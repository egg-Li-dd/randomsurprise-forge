package com.randomsurprise.affix;

import com.randomsurprise.RandomSurpriseMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 敌对词条清除药水
 * 右键使用：随机清除自己抽中的 1 个敌对词条
 * 由 Boss 掉落（凋零/末影龙/监守者/远古守卫者）
 */
public class PurifyPotionItem extends Item {

	public PurifyPotionItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		ItemStack stack = player.getItemInHand(usedHand);
		if (level.isClientSide()) {
			return InteractionResultHolder.success(stack);
		}
		ServerPlayer serverPlayer = (ServerPlayer) player;

		// 检查玩家是否有敌对词条
		var removed = PlayerAffixManager.removeRandomBadAffix(serverPlayer.getUUID());
		if (removed == null) {
			serverPlayer.sendSystemMessage(Component.translatable(
					"purify.randomsurprise.no_bad_affix"));
			return InteractionResultHolder.fail(stack);
		}

		// 消耗药水（创造模式不消耗）
		if (!serverPlayer.getAbilities().instabuild) {
			stack.shrink(1);
		}

		// 通知玩家
		serverPlayer.sendSystemMessage(Component.translatable(
				"purify.randomsurprise.success",
				Component.translatable(removed.getNameKey())));

		// 同步词条数据到所有玩家（敌对词条变化全局可见）
		PlayerAffixManager.syncAllToClients(serverPlayer.level().getServer());

		RandomSurpriseMod.LOGGER.info("玩家 {} 使用清除药水移除敌对词条: {}",
				serverPlayer.getName().getString(), removed.getId());
		return InteractionResultHolder.success(stack);
	}
}
