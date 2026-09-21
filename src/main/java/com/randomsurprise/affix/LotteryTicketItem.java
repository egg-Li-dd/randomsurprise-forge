package com.randomsurprise.affix;

import com.randomsurprise.RandomSurpriseMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 抽奖券物品
 * 右键使用：打开抽奖准备界面，玩家确认后消耗1张券+10级经验随机获得词条
 * 实际抽奖逻辑在 LotteryTicketItem.serverRoll() 中，由 ModNetworking 的 C2S 处理器调用
 * 支持十连抽：消耗10张券，连续roll 10次，结果通过 LotteryTenResultPayload 发送
 */
public class LotteryTicketItem extends Item {

	public static final int REQUIRED_EXP_LEVEL = 10;

	/**
	 * 服务端暂存的「待应用抽奖结果」：key=玩家UUID，value=本次抽中的词条 id 列表。
	 * <p>
	 * 配合「词条延迟生效」机制：serverRoll 只抽奖并暂存结果，等待客户端动画结束后
	 * 通过 LotteryConfirmPayload 回调 serverApply 真正应用。serverApply 优先消费此
	 * 暂存结果（防止客户端篡改 affixId 作弊），客户端回传的 id 仅作兜底。
	 */
	private static final Map<UUID, List<String>> PENDING_ROLLS = new HashMap<>();

	public LotteryTicketItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		ItemStack itemstack = player.getItemInHand(usedHand);
		if (level.isClientSide()) {
			// 客户端：打开抽奖准备界面（显示概率，由玩家点击开始）
			com.randomsurprise.client.LotteryStartScreen.open();
			return InteractionResultHolder.success(itemstack);
		}
		// 服务端：不在此处理，等待客户端发送 LotteryRequestPayload
		return InteractionResultHolder.success(itemstack);
	}

	/**
	 * 服务端执行抽奖逻辑（由 ModNetworking 的 C2S 处理器调用）—— 第一步：只抽奖不应用。
	 * <p>
	 * 流程：验证手持抽奖券 → 消耗券 → roll 词条 → 暂存结果 → 发送结果包给客户端。
	 * 词条此时<b>尚未生效</b>，需等待客户端动画结束后回传 {@link LotteryConfirmPayload}，
	 * 由 {@link #serverApply} 真正应用。
	 *
	 * @param tenFold true=十连抽（消耗10张券，roll 10次）；false=单抽
	 */
	public static void serverRoll(ServerPlayer serverPlayer, boolean tenFold) {
		// 入侵态禁用抽奖（征召战场失败惩罚）
		if (com.randomsurprise.battlefield.BattlefieldManager.isLotteryDisabled()) {
			serverPlayer.sendSystemMessage(Component.translatable(
					"lottery.randomsurprise.disabled_invasion"));
			return;
		}
		// 检查主手/副手是否持有抽奖券
		ItemStack mainHand = serverPlayer.getMainHandItem();
		ItemStack offHand = serverPlayer.getOffhandItem();
		ItemStack ticketStack = null;

		if (mainHand.getItem() instanceof LotteryTicketItem) {
			ticketStack = mainHand;
		} else if (offHand.getItem() instanceof LotteryTicketItem) {
			ticketStack = offHand;
		}

		if (ticketStack == null) {
			serverPlayer.sendSystemMessage(Component.translatable(
					"lottery.randomsurprise.no_ticket"));
			return;
		}

		int rollCount = tenFold ? 10 : 1;
		// 创造模式不消耗券；生存模式检查数量
		if (!serverPlayer.getAbilities().instabuild) {
			if (ticketStack.getCount() < rollCount) {
				serverPlayer.sendSystemMessage(Component.translatable(
						"lottery.randomsurprise.insufficient_tickets",
						rollCount, ticketStack.getCount()));
				return;
			}
			ticketStack.shrink(rollCount);
		}

		// 仅抽奖，不应用词条
		List<String> affixIds = new ArrayList<>();
		for (int i = 0; i < rollCount; i++) {
			Affix affix = AffixRegistry.rollAffix();
			affixIds.add(affix.getId());
		}

		// 服务端暂存待应用结果（防作弊：apply 时优先使用此结果而非客户端回传）
		PENDING_ROLLS.put(serverPlayer.getUUID(), affixIds);

		// 发送抽奖结果到客户端（词条尚未生效，等待动画结束回传确认）
		if (tenFold) {
			com.randomsurprise.network.ModNetworking.sendLotteryTenResult(serverPlayer, affixIds);
			RandomSurpriseMod.LOGGER.info("玩家 {} 十连抽获得（待确认）: {}",
					serverPlayer.getName().getString(), affixIds);
		} else {
			com.randomsurprise.network.ModNetworking.sendLotteryResult(serverPlayer, affixIds.get(0));
			RandomSurpriseMod.LOGGER.info("玩家 {} 抽中词条（待确认）: {}",
					serverPlayer.getName().getString(), affixIds.get(0));
		}
	}

	/**
	 * 服务端应用抽奖词条 —— 第二步：由客户端 {@link LotteryConfirmPayload} 回调触发。
	 * <p>
	 * 流程：取出暂存结果 → addAffix → applyGoodEffects → 广播坏词条 → syncAllToClients。
	 * 优先使用服务端暂存的结果，客户端回传的 affixIds 仅作兜底（防作弊）。
	 *
	 * @param tenFold   是否十连抽（仅用于日志）
	 * @param affixIds  客户端回传的词条 id 列表（兜底用）
	 */
	public static void serverApply(ServerPlayer serverPlayer, boolean tenFold, List<String> affixIds) {
		// 优先使用服务端暂存的结果（防作弊），客户端回传仅作兜底
		List<String> toApply = PENDING_ROLLS.remove(serverPlayer.getUUID());
		if (toApply == null || toApply.isEmpty()) {
			toApply = affixIds;
		}
		if (toApply == null || toApply.isEmpty()) return;

		List<Affix> badAffixes = new ArrayList<>();
		for (String id : toApply) {
			Affix affix = AffixRegistry.getById(id);
			if (affix == null) continue;
			PlayerAffixManager.addAffix(serverPlayer.getUUID(), affix.getId());
			if (!affix.isGood()) {
				badAffixes.add(affix);
			}
		}
		// 立即应用好词条效果
		PlayerAffixManager.applyGoodEffects(serverPlayer);
		// 广播所有坏词条
		for (Affix bad : badAffixes) {
			broadcastBadAffix(serverPlayer, bad);
		}
		// 同步词条数据到所有玩家
		MinecraftServer server = serverPlayer.level().getServer();
		if (server != null) {
			PlayerAffixManager.syncAllToClients(server);
		}
		RandomSurpriseMod.LOGGER.info("玩家 {} 确认应用{}词条: {}",
				serverPlayer.getName().getString(), tenFold ? "十连抽" : "单抽", toApply);
	}

	/**
	 * 全服广播：玩家抽中敌对词条
	 * 让所有在线玩家看到警告
	 */
	private static void broadcastBadAffix(ServerPlayer roller, Affix affix) {
		MinecraftServer server = roller.level().getServer();
		if (server == null) return;
		String rollerName = roller.getName().getString();
		String rarityName = affix.getRarity().getDisplayName();
		Component affixName = Component.translatable(affix.getNameKey());
		Component message = Component.translatable(
				"lottery.randomsurprise.broadcast_bad",
				rollerName, rarityName, affixName);
		// 发送给所有在线玩家
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			p.sendSystemMessage(message, false);
		}
	}
}
