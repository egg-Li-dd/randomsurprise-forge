package com.randomsurprise.battlefield;

import com.randomsurprise.RandomSurpriseMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * 征召维度注册与传送工具
 *
 * 维度通过数据包 JSON 自动注册：
 *   data/randomsurprise/dimension_type/battlefield.json  —— 维度类型
 *   data/randomsurprise/dimension/battlefield.json        —— 维度定义（flat生成器）
 * 代码中仅定义 RegistryKey<Level> 用于获取 ServerLevel。
 *
 * 维度特性：正午固定时间、无怪物自然生成、平坦地形
 */
public class BattlefieldDimension {

	/** 征召维度 Key（对应 randomsurprise:battlefield） */
	public static final ResourceKey<Level> BATTLEFIELD_DIM_KEY =
			ResourceKey.create(Registries.DIMENSION,
					new ResourceLocation(RandomSurpriseMod.MOD_ID, "battlefield"));

	/** 竞技场中心坐标 */
	public static final int ARENA_CENTER_X = 0;
	public static final int ARENA_CENTER_Y = 64;
	public static final int ARENA_CENTER_Z = 0;
	/** 竞技场半径（70×70 总尺寸） */
	public static final int ARENA_RADIUS = 35;

	/**
	 * 获取征召维度的 ServerLevel（维度未加载时返回null）
	 */
	public static ServerLevel getBattlefieldLevel(MinecraftServer server) {
		return server.getLevel(BATTLEFIELD_DIM_KEY);
	}

	/**
	 * 传送玩家到征召维度竞技场中心
	 * 使用 teleportTo 跨维度传送（26.1 API，第一参数为 ServerLevel）
	 * @return true 传送成功，false 维度未加载
	 */
	public static boolean teleportToBattlefield(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return false;
		ServerLevel battlefield = getBattlefieldLevel(server);
		if (battlefield == null) {
			RandomSurpriseMod.LOGGER.warn("[征召战场] 维度未加载，传送失败");
			return false;
		}
		// 强制加载中心区块，确保竞技场已生成
		battlefield.getChunk(ARENA_CENTER_X >> 4, ARENA_CENTER_Z >> 4);
		// 生成竞技场结构（若未生成）
		ArenaGenerator.generateIfNotExist(battlefield);
		// 跨维度传送（26.1: teleportTo 接受 ServerLevel 第一参数）
		player.teleportTo(battlefield,
				ARENA_CENTER_X + 0.5, ARENA_CENTER_Y, ARENA_CENTER_Z + 0.5,
				java.util.Collections.emptySet(), 0.0F, 0.0F);
		return true;
	}

	/**
	 * 判断玩家是否在征召维度内
	 */
	public static boolean isInBattlefield(ServerPlayer player) {
		return player.level().dimension().equals(BATTLEFIELD_DIM_KEY);
	}
}
