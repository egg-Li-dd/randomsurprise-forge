package com.randomsurprise;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/**
 * 存档数据路径工具类
 * 提供世界特定的数据路径，确保每个存档的数据隔离存储
 */
public class WorldDataPath {

	/**
	 * 获取当前世界的 data 目录路径
	 * 使用 server.getWorldPath(LevelResource.ROOT) 获取实际的存档目录
	 * 而不是硬编码的 "world" 文件夹
	 */
	public static Path getWorldDataPath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("data");
	}

	/**
	 * 获取指定文件名的完整路径
	 */
	public static Path getWorldDataPath(MinecraftServer server, String filename) {
		return getWorldDataPath(server).resolve(filename);
	}
}
