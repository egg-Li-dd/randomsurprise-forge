package com.randomsurprise.battlefield;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * 玩家原始位置记录（用于战场结束后传送回原位置）
 * 持久化存储维度Key（非ServerLevel引用），服务器重启后可还原
 */
public record LocationData(
		String dimensionId,  // 维度ID字符串（如 "minecraft:overworld"），用于持久化
		double x, double y, double z,
		float yRot, float xRot
) {

	/** 从ServerLevel当前位置创建 */
	public static LocationData of(ServerLevel level, double x, double y, double z, float yRot, float xRot) {
		String dimId = level.dimension().location().toString();
		return new LocationData(dimId, x, y, z, yRot, xRot);
	}

	/** 根据维度ID字符串解析为 ResourceKey<Level> */
	public ResourceKey<Level> dimensionKey() {
		return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dimensionId));
	}

	/** 获取对应的ServerLevel（通过服务器实例解析） */
	public ServerLevel getServerLevel(MinecraftServer server) {
		return server.getLevel(dimensionKey());
	}

	/** 是否为主世界 */
	public boolean isOverworld() {
		return Level.OVERWORLD.location().toString().equals(dimensionId);
	}
}
