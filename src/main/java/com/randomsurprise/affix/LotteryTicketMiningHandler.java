package com.randomsurprise.affix;

import com.randomsurprise.RandomSurpriseMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/**
 * 挖矿掉落抽奖券处理器
 *
 * 当玩家挖掘高价值矿物时有概率额外掉落抽奖券：
 * - 钻石矿：15% 掉落 1 张
 * - 深板岩钻石矿：15% 掉落 1 张
 * - 绿宝石矿：12% 掉落 1 张
 * - 深板岩绿宝石矿：12% 掉落 1 张
 * - 远古残骸：25% 掉落 2 张（最稀有，奖励最高）
 * - 金矿/深板岩金矿：5% 掉落 1 张（增加获取途径）
 *
 * 使用精准采集时也触发（鼓励玩家挖掘而非囤积）
 */
public class LotteryTicketMiningHandler {
	private static final Random RANDOM = new Random();

	/**
	 * 由主类 PlayerBlockBreakEvents.AFTER 调用
	 */
	public static void onBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		Block block = state.getBlock();

		// 判断矿石类型并计算掉落概率与数量
		double chance = 0;
		int count = 1;

		if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) {
			chance = 0.15;
			count = 1;
		} else if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) {
			chance = 0.12;
			count = 1;
		} else if (block == Blocks.ANCIENT_DEBRIS) {
			chance = 0.25;
			count = 2;
		} else if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE
				|| block == Blocks.NETHER_GOLD_ORE) {
			chance = 0.05;
			count = 1;
		} else {
			return; // 非目标矿石，不触发
		}

		// 概率掉落
		if (RANDOM.nextDouble() < chance) {
			ItemStack ticket = new ItemStack(ModItems.LOTTERY_TICKET.get(), count);
			// 在玩家位置掉落（避免方块位置被破坏后掉入虚空）
			Block.popResource(level, pos, ticket);

			// 通知玩家（仅当不是创造模式破坏时）
			if (!player.getAbilities().instabuild) {
				RandomSurpriseMod.LOGGER.debug("玩家 {} 挖掘 {} 时获得 {} 张抽奖券",
						player.getName().getString(),
						block.toString(), count);
			}
		}
	}
}
