package com.randomsurprise.superpower;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.randomsurprise.RandomSurpriseMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家超能力数据管理
 * - 持久化存储每位玩家的超能力ID（JSON文件）
 * - 每个玩家只能拥有一个超能力
 * - 管理冷却时间（内存中，不持久化）
 * - 管理被动技能开关状态（内存中）
 * - 管理濒死回溯的位置历史（内存中，3秒滚动窗口）
 * - 管理二段跳状态（内存中）
 * - 管理飞行/毒素清除等临时状态
 *
 * 数据文件：world/data/randomsurprise_superpowers.json
 * 格式：
 * {
 *   "playerUUID": "ore_sense",
 *   ...
 * }
 */
public class PlayerSuperPowerManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	/** 玩家 UUID → 超能力 ID */
	private static final Map<UUID, String> PLAYER_SUPER_POWERS = new HashMap<>();
	/** 玩家 UUID → 当前冷却剩余秒数（每秒 -1） */
	private static final Map<UUID, Map<SuperPower, Integer>> COOLDOWNS = new HashMap<>();
	/** 玩家 UUID → 被动技能开关状态（true=启用） */
	private static final Map<UUID, Boolean> TOGGLE_STATES = new HashMap<>();
	/** 玩家 UUID → 超能力状态数据（飞行/毒素清除剩余时间等） */
	private static final Map<UUID, Map<String, Integer>> STATE_TICKS = new HashMap<>();
	/** 玩家 UUID → 位置历史（用于濒死回溯，存储最近3秒的位置和时间戳） */
	private static final Map<UUID, java.util.Deque<PositionRecord>> POSITION_HISTORY = new HashMap<>();
	/** 玩家 UUID → 上次跳跃状态（用于二段跳检测） */
	private static final Map<UUID, Boolean> WAS_ON_GROUND = new HashMap<>();
	/** 玩家 UUID → 二段跳是否已使用（每次落地重置） */
	private static final Map<UUID, Boolean> DOUBLE_JUMP_USED = new HashMap<>();

	private static Path dataPath;

	/** 位置记录（用于濒死回溯，含维度信息防止跨维度传送） */
	public record PositionRecord(double x, double y, double z, float yaw, float pitch, long timestampMs,
		net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {}

	/** 初始化（服务器启动时调用） */
	public static void init(MinecraftServer server) {
		dataPath = com.randomsurprise.WorldDataPath.getWorldDataPath(server, "randomsurprise_superpowers.json");
		PLAYER_SUPER_POWERS.clear();
		load();
	}

	/** 加载数据 */
	public static void load() {
		try {
			if (dataPath != null && Files.exists(dataPath)) {
				String content = Files.readString(dataPath);
				Map<UUID, String> loaded = GSON.fromJson(content,
						new TypeToken<Map<UUID, String>>() {}.getType());
				if (loaded != null) PLAYER_SUPER_POWERS.putAll(loaded);
			}
		} catch (Exception e) {
			RandomSurpriseMod.LOGGER.warn("加载超能力数据失败: {}", e.getMessage());
		}
	}

	/** 保存数据 */
	public static void save() {
		if (dataPath == null) return;
		try {
			Files.createDirectories(dataPath.getParent());
			Files.writeString(dataPath, GSON.toJson(PLAYER_SUPER_POWERS));
		} catch (IOException e) {
			RandomSurpriseMod.LOGGER.error("保存超能力数据失败: {}", e.getMessage());
		}
	}

	/** 设置玩家的超能力（永久，每个玩家只能有一个） */
	public static void setSuperPower(UUID playerId, SuperPower power) {
		PLAYER_SUPER_POWERS.put(playerId, power.getId());
		save();
		// 初始化被动技能开关为开启状态
		if (power.isToggleable()) {
			TOGGLE_STATES.put(playerId, true);
		}
	}

	/** 获取玩家的超能力，未拥有返回 null */
	public static SuperPower getSuperPower(UUID playerId) {
		String id = PLAYER_SUPER_POWERS.get(playerId);
		return id != null ? SuperPower.getById(id) : null;
	}

	/** 玩家是否已拥有超能力 */
	public static boolean hasSuperPower(UUID playerId) {
		return PLAYER_SUPER_POWERS.containsKey(playerId);
	}

	/** 清除玩家的超能力（用于无效ID修复） */
	public static void removeSuperPower(UUID playerId) {
		PLAYER_SUPER_POWERS.remove(playerId);
		save();
	}

	/** 被动技能开关切换 */
	public static boolean togglePassive(UUID playerId) {
		SuperPower sp = getSuperPower(playerId);
		if (sp == null || !sp.isToggleable()) return false;
		boolean current = TOGGLE_STATES.getOrDefault(playerId, true);
		boolean next = !current;
		TOGGLE_STATES.put(playerId, next);
		return next;
	}

	/** 获取被动技能开关状态（默认开启） */
	public static boolean isPassiveEnabled(UUID playerId) {
		return TOGGLE_STATES.getOrDefault(playerId, true);
	}

	// ========== 冷却管理 ==========

	/** 设置冷却（秒） */
	public static void setCooldown(UUID playerId, SuperPower power, int seconds) {
		COOLDOWNS.computeIfAbsent(playerId, k -> new HashMap<>()).put(power, seconds);
	}

	/** 获取冷却剩余秒数（0 = 可用） */
	public static int getCooldown(UUID playerId, SuperPower power) {
		return COOLDOWNS.getOrDefault(playerId, new HashMap<>()).getOrDefault(power, 0);
	}

	/** 是否在冷却中 */
	public static boolean isOnCooldown(UUID playerId, SuperPower power) {
		return getCooldown(playerId, power) > 0;
	}

	/** 每秒减少所有冷却（由 SuperPowerHandler 调用） */
	public static void tickCooldowns() {
		for (Map<SuperPower, Integer> cooldowns : COOLDOWNS.values()) {
			cooldowns.entrySet().removeIf(e -> {
				int v = e.getValue() - 1;
				if (v <= 0) return true;
				e.setValue(v);
				return false;
			});
		}
	}

	// ========== 临时状态管理（飞行/毒素清除等） ==========

	/** 设置状态剩余 ticks */
	public static void setStateTicks(UUID playerId, String key, int ticks) {
		STATE_TICKS.computeIfAbsent(playerId, k -> new HashMap<>()).put(key, ticks);
	}

	/** 获取状态剩余 ticks */
	public static int getStateTicks(UUID playerId, String key) {
		return STATE_TICKS.getOrDefault(playerId, new HashMap<>()).getOrDefault(key, 0);
	}

	/** 每tick减少所有状态ticks */
	public static void tickStates() {
		for (Map<String, Integer> states : STATE_TICKS.values()) {
			states.entrySet().removeIf(e -> {
				int v = e.getValue() - 1;
				if (v <= 0) return true;
				e.setValue(v);
				return false;
			});
		}
	}

	// ========== 位置历史（濒死回溯） ==========

	/** 记录玩家位置（每 200ms 调用一次，保留最近 3 秒） */
	public static void recordPosition(ServerPlayer player) {
		java.util.Deque<PositionRecord> history = POSITION_HISTORY.computeIfAbsent(
				player.getUUID(), k -> new java.util.ArrayDeque<>());
		long now = System.currentTimeMillis();
		PositionRecord record = new PositionRecord(
				player.getX(), player.getY(), player.getZ(),
				player.getYRot(), player.getXRot(), now,
				player.level().dimension());
		history.addLast(record);
		// 移除超过 3 秒的记录
		while (!history.isEmpty() && now - history.peekFirst().timestampMs() > 3000) {
			history.pollFirst();
		}
	}

	/** 获取玩家 3 秒前的位置记录（用于濒死回溯） */
	public static PositionRecord getPositionThreeSecondsAgo(UUID playerId) {
		java.util.Deque<PositionRecord> history = POSITION_HISTORY.get(playerId);
		if (history == null || history.isEmpty()) return null;
		// 返回最早的记录（约3秒前）
		return history.peekFirst();
	}

	// ========== 二段跳状态 ==========

	/** 上次是否在地面 */
	public static boolean wasOnGround(UUID playerId) {
		return WAS_ON_GROUND.getOrDefault(playerId, true);
	}

	public static void setWasOnGround(UUID playerId, boolean onGround) {
		WAS_ON_GROUND.put(playerId, onGround);
	}

	/** 二段跳是否已使用 */
	public static boolean isDoubleJumpUsed(UUID playerId) {
		return DOUBLE_JUMP_USED.getOrDefault(playerId, false);
	}

	public static void setDoubleJumpUsed(UUID playerId, boolean used) {
		DOUBLE_JUMP_USED.put(playerId, used);
	}

	// ========== 玩家退出清理 ==========

	public static void onPlayerQuit(UUID playerId) {
		// 保留超能力ID和开关状态（持久化），仅清理临时数据
		COOLDOWNS.remove(playerId);
		STATE_TICKS.remove(playerId);
		POSITION_HISTORY.remove(playerId);
		WAS_ON_GROUND.remove(playerId);
		DOUBLE_JUMP_USED.remove(playerId);
	}
}
