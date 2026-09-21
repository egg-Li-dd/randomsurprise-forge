package com.randomsurprise.battlefield;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.randomsurprise.RandomSurpriseMod;
import com.randomsurprise.affix.AffixRarity;
import com.randomsurprise.affix.PlayerAffixManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 征召战场系统核心管理器
 *
 * 职责：
 *  - 全局状态机管理（GameState）
 *  - 计数器（事件次数/战场总次数/连续失败/末日险境征召次数/末日死亡数）
 *  - 持久化（状态+计数器+原始位置，服务器重启可恢复）
 *  - 触发与结算入口（startBattlefield/endBattlefield 具体实现在后续阶段补全）
 *  - tick 分发（各阶段的周期性逻辑）
 *
 * 数据持久化路径：<存档>/world/data/randomsurprise_battlefield.json
 */
public class BattlefieldManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Path dataPath;

	// ===== 核心常量 =====
	/** 每 N 次随机事件触发一次征召战场（第一次进入后使用此阈值） */
	public static final int EVENTS_PER_BATTLEFIELD = 30;
	/** 第一次进入征召战场所需随机事件次数（仅 totalBattlefieldCount==0 时生效） */
	public static final int FIRST_BATTLEFIELD_EVENT_THRESHOLD = 40;
	/** 末日倒计时持续时长（tick）—— 5分钟 = 6000 */
	public static final int DOOMSDAY_DURATION_TICKS = 6000;
	/** 末日倒计时死亡上限基础值（随难度递减，最低3） */
	public static final int DOOMSDAY_DEATH_BASE = 10;
	/** 末日倒计时死亡上限下限 */
	public static final int DOOMSDAY_DEATH_MIN = 3;
	/** 随机世界入侵负面事件间隔（tick）—— 120秒 = 2400 */
	public static final int INVASION_INTERVAL_TICKS = 2400;
	/** 进入决战的征召总次数阈值 */
	public static final int FINAL_BATTLE_TOTAL_THRESHOLD = 10;
	/** 末日险境下进入决战的征召次数 */
	public static final int DOOMSDAY_DANGER_BATTLE_THRESHOLD = 4;
	/** 进入末日倒计时的连续失败次数 */
	public static final int DOOMSDAY_FAIL_THRESHOLD = 4;
	/** 决战 Boss 场上数量倍数（玩家人数 × 此值） */
	public static final double FINAL_BOSS_PLAYER_RATIO = 1.5;
	/** 准备房间持续时长（tick）—— 30秒 = 600 */
	private static final int PREP_ROOM_DURATION = 600;
	/** 波次间隔（tick）—— 60秒 = 1200，场上敌对生物全清时立即触发下一波 */
	private static final int WAVE_INTERVAL_TICKS = 1200;
	/** 准备房间坐标（在征召世界中，距离竞技场较远，避免与战斗区域重叠） */
	private static final int PREP_ROOM_X = 200;
	private static final int PREP_ROOM_Y = 64;
	private static final int PREP_ROOM_Z = 0;
	/** 批次生成：基础波次数（难度5+为4波，10+为5波） */
	private static final int BASE_WAVES = 3;

	// ===== 持久化状态 =====
	private static GameState state = GameState.NORMAL;
	private static int totalEventCount = 0;          // 随机事件累计次数
	private static int totalBattlefieldCount = 0;    // 征召战场总进入次数
	private static int consecutiveFailures = 0;      // 连续失败次数
	private static int doomsdayDangerBattleCount = 0;// 末日险境下的征召次数
	private static int doomsdayDeathCount = 0;       // 末日倒计时期间玩家总死亡次数
	private static int doomsdayCountdownTicks = 0;   // 末日倒计时剩余 tick
	private static int battlefieldCountdownTicks = 0; // 战场进入倒计时剩余 tick
	private static final Map<UUID, LocationData> originalLocations = new ConcurrentHashMap<>();

	// ===== 临时状态（不持久化）=====
	private static final Set<UUID> aliveBosses = ConcurrentHashMap.newKeySet(); // 当前战场存活Boss
	private static int battlefieldDifficulty = 1;    // 本场难度等级
	private static int invasionTickCounter = 0;      // 入侵负面事件计数器
	private static int blockRemoveCounter = 0;       // 末日方块消除计数器
	private static int prepRoomTicks = 0;            // 准备房间剩余 tick（30秒倒计时）
	private static int currentWave = 0;              // 当前波次（从1开始）
	private static int totalWaves = 0;               // 本场总波次数
	private static int nextWaveCountdown = 0;         // 下一波生成倒计时（tick）
	/** 玩家进入战场前的游戏模式（用于死亡复活后恢复） */
	private static final Map<UUID, net.minecraft.world.level.GameType> originalGameModes = new ConcurrentHashMap<>();
	/** 记录本场战场是否由末日倒计时触发（用于状态机断裂修复） */
	private static boolean lastBattleFromDoomsday = false;

	// ===== v20: 胜利停留阶段 =====
	/** 胜利停留倒计时（tick），30秒=600 */
	private static int victoryCountdownTicks = 0;
	private static final int VICTORY_STAY_DURATION = 600;
	/** 已退出胜利停留阶段的玩家UUID */
	private static final Set<UUID> exitedPlayers = ConcurrentHashMap.newKeySet();
	/** 胜利时保存的战斗上下文（用于最终状态转移） */
	private static boolean victoryWasFinal = false;
	private static boolean victoryWasDoomsdayDanger = false;
	private static boolean victoryFromDoomsday = false;
	/** 胜利时保存的傀儡数据（按玩家UUID过滤后传送回主世界） */
	private static final List<GolemSaveData> savedVictoryGolems = new ArrayList<>();

	/** 判断实体是否为战场Boss（用于排除双重增强） */
	public static boolean isBattlefieldBoss(java.util.UUID entityUUID) {
		return aliveBosses.contains(entityUUID);
	}

	// ===== 初始化与持久化 =====

	/** 服务器启动时初始化 */
	public static void init(MinecraftServer server) {
		dataPath = com.randomsurprise.WorldDataPath.getWorldDataPath(server, "randomsurprise_battlefield.json");
		state = GameState.NORMAL;
		totalEventCount = 0;
		totalBattlefieldCount = 0;
		consecutiveFailures = 0;
		doomsdayDangerBattleCount = 0;
		doomsdayDeathCount = 0;
		doomsdayCountdownTicks = 0;
		battlefieldCountdownTicks = 0;
		aliveBosses.clear();
		originalLocations.clear();
		originalGameModes.clear();
		load();
		if (state.isBattlefieldActive()) {
			RandomSurpriseMod.LOGGER.warn("[征召战场] 检测到服务器重启时处于战场态 {}，强制回常规态", state);
			state = GameState.NORMAL;
			aliveBosses.clear();
			save();
		}
	}

	/** 加载持久化数据 */
	public static void load() {
		try {
			if (dataPath != null && Files.exists(dataPath)) {
				String content = Files.readString(dataPath);
				JsonObject json = GSON.fromJson(content, JsonObject.class);
				if (json != null) {
					state = GameState.valueOf(json.get("state").getAsString());
					totalEventCount = json.get("totalEventCount").getAsInt();
					totalBattlefieldCount = json.get("totalBattlefieldCount").getAsInt();
					consecutiveFailures = json.get("consecutiveFailures").getAsInt();
					doomsdayDangerBattleCount = json.get("doomsdayDangerBattleCount").getAsInt();
					doomsdayDeathCount = json.get("doomsdayDeathCount").getAsInt();
					doomsdayCountdownTicks = json.has("doomsdayCountdownTicks")
							? json.get("doomsdayCountdownTicks").getAsInt() : 0;
					// 加载原始位置
					originalLocations.clear();
					if (json.has("originalLocations")) {
						JsonObject locObj = json.getAsJsonObject("originalLocations");
						for (Map.Entry<String, com.google.gson.JsonElement> entry : locObj.entrySet()) {
							UUID id = UUID.fromString(entry.getKey());
							JsonObject l = entry.getValue().getAsJsonObject();
							originalLocations.put(id, new LocationData(
									l.get("dimensionId").getAsString(),
									l.get("x").getAsDouble(),
									l.get("y").getAsDouble(),
									l.get("z").getAsDouble(),
									l.get("yRot").getAsFloat(),
									l.get("xRot").getAsFloat()));
						}
					}
					// Bug3: 加载 originalGameModes（玩家进入战场前的游戏模式）
					originalGameModes.clear();
					if (json.has("originalGameModes")) {
						JsonObject modesJson = json.getAsJsonObject("originalGameModes");
						for (var entry : modesJson.entrySet()) {
							try {
								originalGameModes.put(UUID.fromString(entry.getKey()),
									net.minecraft.world.level.GameType.byName(entry.getValue().getAsString()));
							} catch (Exception ignored) {}
						}
					}
				}
			}
		} catch (Exception e) {
			RandomSurpriseMod.LOGGER.warn("[征召战场] 加载数据失败: {}", e.getMessage());
		}
	}

	/** 保存持久化数据 */
	public static void save() {
		if (dataPath == null) return;
		try {
			Files.createDirectories(dataPath.getParent());
			JsonObject json = new JsonObject();
			json.addProperty("state", state.name());
			json.addProperty("totalEventCount", totalEventCount);
			json.addProperty("totalBattlefieldCount", totalBattlefieldCount);
			json.addProperty("consecutiveFailures", consecutiveFailures);
			json.addProperty("doomsdayDangerBattleCount", doomsdayDangerBattleCount);
			json.addProperty("doomsdayDeathCount", doomsdayDeathCount);
			json.addProperty("doomsdayCountdownTicks", doomsdayCountdownTicks);
			// 保存原始位置
			JsonObject locObj = new JsonObject();
			for (Map.Entry<UUID, LocationData> entry : originalLocations.entrySet()) {
				JsonObject l = new JsonObject();
				l.addProperty("dimensionId", entry.getValue().dimensionId());
				l.addProperty("x", entry.getValue().x());
				l.addProperty("y", entry.getValue().y());
				l.addProperty("z", entry.getValue().z());
				l.addProperty("yRot", entry.getValue().yRot());
				l.addProperty("xRot", entry.getValue().xRot());
				locObj.add(entry.getKey().toString(), l);
			}
			json.add("originalLocations", locObj);
			// Bug3: 持久化 originalGameModes（玩家进入战场前的游戏模式）
			JsonObject modesJson = new JsonObject();
			for (var entry : originalGameModes.entrySet()) {
				modesJson.addProperty(entry.getKey().toString(), entry.getValue().getName());
			}
			json.add("originalGameModes", modesJson);
			Files.writeString(dataPath, GSON.toJson(json));
		} catch (Exception e) {
			RandomSurpriseMod.LOGGER.error("[征召战场] 保存数据失败: {}", e.getMessage());
		}
	}

	// ===== 事件入口（由其他系统调用）=====

	/**
	 * 随机事件执行后调用（由 SurpriseManager 触发）
	 * 计数+1，NORMAL 状态下达阈值自动触发征召战场
	 * 第一次进入需 FIRST_BATTLEFIELD_EVENT_THRESHOLD(30) 次，之后恢复 EVENTS_PER_BATTLEFIELD(20) 次
	 */
	public static void onEventExecuted(MinecraftServer server) {
		if (state != GameState.NORMAL) return;  // 非常规态不计数
		totalEventCount++;
		int threshold = (totalBattlefieldCount == 0)
				? FIRST_BATTLEFIELD_EVENT_THRESHOLD
				: EVENTS_PER_BATTLEFIELD;
		if (totalEventCount >= threshold) {
			totalEventCount = 0;
			// 进入10秒倒计时，而非直接传送
			enterBattlefieldCountdown(server);
		}
		save();
	}

	/** 进入战场倒计时（10秒准备时间） */
	private static void enterBattlefieldCountdown(MinecraftServer server) {
		state = GameState.BATTLEFIELD_COUNTDOWN;
		battlefieldCountdownTicks = 200; // 10秒 = 200 tick
		broadcast(server, Component.translatable("battlefield.randomsurprise.countdown_start"));
		RandomSurpriseMod.LOGGER.info("[征召战场] 进入10秒倒计时");
		save();
	}

	/** 战场倒计时 tick：显示倒计时，结束后开始战场 */
	private static void tickBattlefieldCountdown(MinecraftServer server) {
		// 缺陷10: 空战场检查 —— 无在线玩家则取消战场回到 NORMAL
		if (server.getPlayerList().getPlayers().isEmpty()) {
			state = GameState.NORMAL;
			save();
			return;
		}
		battlefieldCountdownTicks--;
		int secondsLeft = battlefieldCountdownTicks / 20;

		// 每秒通知所有在线玩家
		if (battlefieldCountdownTicks % 20 == 0 && secondsLeft > 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.displayClientMessage(
						Component.translatable("battlefield.randomsurprise.countdown", secondsLeft), true);
			}
		}

		// 倒计时结束 → 开始战场
		if (battlefieldCountdownTicks <= 0) {
			startBattlefield(server);
		}
	}

	/**
	 * 服务器 tick 回调（由 SurpriseManager.onServerTick 调用）
	 * 分发到各状态的周期性逻辑
	 */
	public static void onServerTick(MinecraftServer server) {
		switch (state) {
			case BATTLEFIELD_COUNTDOWN -> tickBattlefieldCountdown(server);
			case DOOMSDAY_COUNTDOWN -> tickDoomsdayCountdown(server);
			case WORLD_INVASION -> tickWorldInvasion(server);
			case BATTLEFIELD_PREP, BATTLEFIELD_ACTIVE, FINAL_BATTLE -> tickBattlefield(server);
		case BATTLEFIELD_VICTORY -> tickVictory(server);
			default -> { /* NORMAL/DOOMSDAY_DANGER/WORLD_PEACE 无 tick 逻辑 */ }
		}
	}

	/** 玩家加入服务器（处理战场维度卡住的兜底传送） */
	public static void onPlayerJoin(ServerPlayer player) {
		// 若玩家登录在战场维度但当前不在战场态，传送回原位置
		if (!state.isBattlefieldActive()) {
			var dim = player.level().dimension();
			if (dim.location().toString().equals("randomsurprise:battlefield")) {
				LocationData loc = originalLocations.remove(player.getUUID());
				LocationData target = (loc != null) ? loc
						: new LocationData("minecraft:overworld", 0, 64, 0, 0, 0);
				teleportBack(player, target);
				// 恢复游戏模式
				net.minecraft.world.level.GameType mode = originalGameModes.remove(player.getUUID());
				if (mode != null) {
					player.setGameMode(mode);
				}
				save();
			}
		}
	}

	/**
	 * 玩家复活事件：恢复进入战场前的游戏模式，保留玩家权限
	 * 在战场内死亡后复活时调用
	 */
	public static void onPlayerRespawn(ServerPlayer player) {
		net.minecraft.world.level.GameType mode = originalGameModes.get(player.getUUID());
		if (mode != null) {
			player.setGameMode(mode);
			RandomSurpriseMod.LOGGER.info("[征召战场] 玩家 {} 复活，恢复游戏模式: {}",
					player.getName().getString(), mode.getName());
		}
	}

	// ===== 状态查询方法 =====

	public static GameState getState() { return state; }
	public static int getTotalEventCount() { return totalEventCount; }
	public static int getTotalBattlefieldCount() { return totalBattlefieldCount; }
	public static int getConsecutiveFailures() { return consecutiveFailures; }
	public static int getDoomsdayDangerBattleCount() { return doomsdayDangerBattleCount; }
	public static int getDoomsdayDeathCount() { return doomsdayDeathCount; }
	public static int getDoomsdayCountdownTicks() { return doomsdayCountdownTicks; }
	public static Set<UUID> getAliveBosses() { return aliveBosses; }
	public static int getBattlefieldDifficulty() { return battlefieldDifficulty; }

	/** 末日倒计时剩余秒数 */
	public static int getDoomsdayRemainingSeconds() {
		return Math.max(0, doomsdayCountdownTicks / 20);
	}

	/** 末日倒计时死亡上限（随难度递减） */
	public static int getDoomsdayDeathLimit() {
		int limit = DOOMSDAY_DEATH_BASE - battlefieldDifficulty;
		return Math.max(DOOMSDAY_DEATH_MIN, limit);
	}

	/** 当前是否禁用抽奖（入侵态禁用） */
	public static boolean isLotteryDisabled() {
		return state == GameState.WORLD_INVASION;
	}

	/** 当前是否禁用好词条效果（入侵态禁用） */
	public static boolean isGoodAffixDisabled() {
		return state == GameState.WORLD_INVASION;
	}

	/** 当前是否禁用坏词条增强（平静态禁用） */
	public static boolean isBadAffixDisabled() {
		return state == GameState.WORLD_PEACE;
	}

	/** 当前是否仅触发负面事件（入侵态） */
	public static boolean isNegativeOnly() {
		return state == GameState.WORLD_INVASION;
	}

	// ===== 核心流程（具体实现由后续阶段补全）=====

	/**
	 * 开始征召战场（记录位置→传送→生成Boss）
	 * 自动判断是否进入决战（总次数≥10 或 末日险境后4次）
	 */
	public static void startBattlefield(MinecraftServer server) {
		// 缺陷10: 空战场检查 —— 无在线玩家则取消战场回到 NORMAL
		if (server.getPlayerList().getPlayers().isEmpty()) {
			state = GameState.NORMAL;
			save();
			return;
		}
		// Bug2: 记录本场战场是否来自末日倒计时（状态机断裂修复）
		lastBattleFromDoomsday = (state == GameState.DOOMSDAY_COUNTDOWN);
		// 判断是否进入决战
		boolean isFinal = totalBattlefieldCount >= FINAL_BATTLE_TOTAL_THRESHOLD
				|| (state == GameState.DOOMSDAY_DANGER
						&& doomsdayDangerBattleCount >= DOOMSDAY_DANGER_BATTLE_THRESHOLD);

		battlefieldDifficulty = Math.max(1, totalBattlefieldCount + 1);
		state = isFinal ? GameState.FINAL_BATTLE : GameState.BATTLEFIELD_PREP;
		// 缺陷11: 重置 tickCounter，避免上一场残留计数影响本场胜负检测节奏
		tickCounter = 0;
		// 重置波次与准备房间计数器
		currentWave = 0;
		totalWaves = 0;
		nextWaveCountdown = 0;
		prepRoomTicks = 0;
		aliveBosses.clear();
		save();

		// 广播
		String msgKey = isFinal ? "battlefield.randomsurprise.final_start" : "battlefield.randomsurprise.start";
		broadcast(server, Component.translatable(msgKey, battlefieldDifficulty));

		// 记录所有在线玩家原位置
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		for (ServerPlayer player : players) {
			recordOriginalLocation(player);
			// 保存玩家游戏模式，死亡复活后恢复
			originalGameModes.put(player.getUUID(), player.gameMode.getGameModeForPlayer());
		}

		// 获取战场维度
		ServerLevel battlefield = BattlefieldDimension.getBattlefieldLevel(server);
		if (battlefield == null) {
			RandomSurpriseMod.LOGGER.error("[征召战场] 维度未加载，无法开始战场，回退状态");
			state = GameState.NORMAL;
			save();
			return;
		}

		if (isFinal) {
			// 决战：直接传送到竞技场中心并生成Boss（保持原逻辑）
			for (ServerPlayer player : players) {
				BattlefieldDimension.teleportToBattlefield(player);
			}
			spawnFinalBattleBosses(battlefield, players.size());
			broadcast(server, Component.translatable("battlefield.randomsurprise.boss_count", aliveBosses.size()));
			RandomSurpriseMod.LOGGER.info("[征召战场] 决战开始 isFinal=true difficulty={} bossCount={}",
					battlefieldDifficulty, aliveBosses.size());
		} else {
			// 普通战场：先传送到准备房间，30秒后再进入竞技场
			totalWaves = BASE_WAVES + Math.min(2, battlefieldDifficulty / 5); // 难度5+为4波，10+为5波
			// 强制加载准备房间区块并生成结构
			battlefield.getChunk(PREP_ROOM_X >> 4, PREP_ROOM_Z >> 4);
			generatePrepRoom(battlefield);
			// 传送所有玩家到准备房间
			for (ServerPlayer player : players) {
				player.teleportTo(battlefield,
						PREP_ROOM_X + 0.5, PREP_ROOM_Y, PREP_ROOM_Z + 0.5,
						java.util.Collections.emptySet(), 0.0F, 0.0F);
			}
			prepRoomTicks = PREP_ROOM_DURATION;
			broadcast(server, Component.translatable("battlefield.randomsurprise.prep_start", totalWaves));
			RandomSurpriseMod.LOGGER.info("[征召战场] 普通战场开始，进入准备房间30秒 difficulty={} totalWaves={}",
					battlefieldDifficulty, totalWaves);
		}
		save();
	}

	/**
	 * 结束战场结算（成功/失败 → 奖惩 → 传送回 → 状态流转）
	 */
	public static void endBattlefield(MinecraftServer server, boolean success) {
		GameState battleState = state;  // 保存战斗时的状态
		boolean wasFinal = (battleState == GameState.FINAL_BATTLE);
		boolean wasDoomsdayDanger = (battleState == GameState.DOOMSDAY_DANGER);

		// 1. 清理战场维度所有Boss和掉落物
		clearBattlefield(server);
		// 重置波次与准备房间计数器
		currentWave = 0;
		totalWaves = 0;
		nextWaveCountdown = 0;
		prepRoomTicks = 0;

		// 1.5 清理所有玩家倒地状态（移除效果）
		DownedStateManager.clearAll(server);

		// 1.6 清空所有客户端战场HUD（发送0,0）
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			com.randomsurprise.network.ModNetworking.sendBattlefieldMobCount(player, 0, 0);
		}
		glowingCounter = 0;

		// v19: 保存玩家召唤的傀儡数据（用于战后跟随回主世界）
		List<GolemSaveData> savedGolems = new ArrayList<>();
		ServerLevel bfLevel = BattlefieldDimension.getBattlefieldLevel(server);
		if (bfLevel != null) {
			for (net.minecraft.world.entity.Entity entity : bfLevel.getAllEntities()) {
				if (entity instanceof net.minecraft.world.entity.Mob mob) {
					UUID ownerUUID = com.randomsurprise.affix.PlayerAffixManager.getGolemOwnerUUID(mob);
					if (ownerUUID != null) {
						var nbt = new net.minecraft.nbt.CompoundTag();
						mob.saveAsPassenger(nbt);
						savedGolems.add(new GolemSaveData(ownerUUID, nbt, mob.getX(), mob.getY(), mob.getZ()));
					}
				}
			}
		}
		RandomSurpriseMod.LOGGER.info("[征召战场] 保存 {} 个玩家傀儡数据", savedGolems.size());

		// 清除所有在线玩家的负面药水效果（防止boss施加的负面效果带回主世界）
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			// 清除所有有害效果
			for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects())) {
				if (!effect.getEffect().isBeneficial()) {
					player.removeEffect(effect.getEffect());
				}
			}
		}

		// 2. 传送所有玩家回原位置并恢复游戏模式
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			LocationData loc = consumeOriginalLocation(player.getUUID());
			// 缺陷6: 中途加入的玩家无原位置记录，跳过传送（避免误传到主世界0,0）
			if (loc == null) continue;
			LocationData target = (loc != null) ? loc
					: new LocationData("minecraft:overworld", 0, 64, 0, 0, 0);
			teleportBack(player, target);
			// 恢复玩家进入战场前的游戏模式
			net.minecraft.world.level.GameType mode = originalGameModes.remove(player.getUUID());
			if (mode != null) {
				player.setGameMode(mode);
			}
		}

		// v19: 传送傀儡到主人身边（跟随回主世界）
		for (GolemSaveData data : savedGolems) {
			ServerPlayer owner = server.getPlayerList().getPlayer(data.ownerUUID);
			if (owner != null && owner.level() instanceof ServerLevel ownerLevel) {
				try {
					var entity = net.minecraft.world.entity.EntityType.loadEntityRecursive(
							data.nbt, ownerLevel, e -> e);
					if (entity != null) {
						double offsetX = (java.util.concurrent.ThreadLocalRandom.current().nextDouble() - 0.5) * 3;
						double offsetZ = (java.util.concurrent.ThreadLocalRandom.current().nextDouble() - 0.5) * 3;
						entity.setPos(owner.getX() + offsetX, owner.getY(), owner.getZ() + offsetZ);
						ownerLevel.addFreshEntity(entity);
						RandomSurpriseMod.LOGGER.info("[征召战场] 傀儡 {} 已跟随主人 {} 回到主世界",
								entity.getType().toShortString(), owner.getName().getString());
					}
				} catch (Throwable t) {
					RandomSurpriseMod.LOGGER.warn("[征召战场] 傀儡传送失败: {}", t.getMessage());
				}
			}
		}

		// 3. 应用奖惩
		AffixRarity rarity = getRarityForDifficulty(battlefieldDifficulty);
		if (success) {
			// 成功：每位玩家+1好词条
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				PlayerAffixManager.addRandomGoodAffix(player.getUUID(), rarity);
			}
			consecutiveFailures = 0;
			broadcast(server, Component.translatable("battlefield.randomsurprise.victory", rarity.getDisplayName()));
		} else {
			// 失败：每位玩家+1坏词条
			List<com.randomsurprise.affix.Affix> badPool = com.randomsurprise.affix.AffixRegistry.getByRarity(false, rarity);
			if (!badPool.isEmpty()) {
				com.randomsurprise.affix.Affix bad = badPool.get(
						java.util.concurrent.ThreadLocalRandom.current().nextInt(badPool.size()));
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					PlayerAffixManager.addAffix(player.getUUID(), bad.getId());
				}
			}
			consecutiveFailures++;
			broadcast(server, Component.translatable("battlefield.randomsurprise.defeat", rarity.getDisplayName()));
		}
		totalBattlefieldCount++;

		// v19: 发送结算画面到所有在线玩家
		AffixRarity resultRarity = getRarityForDifficulty(battlefieldDifficulty);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			com.randomsurprise.network.ModNetworking.sendToClient(player,
					new com.randomsurprise.network.BattlefieldResultPayload(
							success, resultRarity.getDisplayName(),
							battlefieldDifficulty, totalBattlefieldCount));
		}

		// 4. 状态流转
		if (wasFinal) {
			// 决战结束
			if (success) {
				enterWorldPeace(server);
			} else {
				enterWorldInvasion(server);
			}
		} else if (wasDoomsdayDanger) {
			// 末日险境下的征召
			if (success) {
				doomsdayDangerBattleCount++;
				state = GameState.DOOMSDAY_DANGER;  // 保持末日险境，等下次征召
				broadcast(server, Component.translatable("battlefield.randomsurprise.danger_progress",
						doomsdayDangerBattleCount, DOOMSDAY_DANGER_BATTLE_THRESHOLD));
			} else {
				enterWorldInvasion(server);
			}
		} else if (success && lastBattleFromDoomsday) {
			// Bug2: 来自末日倒计时的战场成功 → 进入末日险境（修复状态机断裂）
			enterDoomsdayDanger(server);
		} else if (success) {
			state = GameState.NORMAL;
		} else {
			// 普通失败：连续4次 → 末日倒计时
			if (consecutiveFailures >= DOOMSDAY_FAIL_THRESHOLD) {
				enterDoomsdayCountdown(server);
			} else {
				state = GameState.NORMAL;
			}
		}
		aliveBosses.clear();
		RandomSurpriseMod.LOGGER.info("[征召战场] 战场结束 success={} newState={} total={}",
				success, state, totalBattlefieldCount);
		save();
	}

	// ===== v20: 胜利停留阶段 =====

	/**
	 * 胜利触发：不立即退出，而是杀死所有敌对生物（保留掉落物），进入30秒停留阶段
	 * 玩家可拾取掉落物，30秒后自动退出或长按空格5秒提前退出
	 */
	private static void onVictory(MinecraftServer server) {
		// 保存战斗上下文
		victoryWasFinal = (state == GameState.FINAL_BATTLE);
		victoryWasDoomsdayDanger = (state == GameState.DOOMSDAY_DANGER);
		victoryFromDoomsday = lastBattleFromDoomsday;

		// 杀死所有敌对生物（保留掉落物）——使用kill()触发死亡掉落
		ServerLevel bfLevel = BattlefieldDimension.getBattlefieldLevel(server);
		if (bfLevel != null) {
			List<net.minecraft.world.entity.Mob> toKill = new ArrayList<>();
			for (net.minecraft.world.entity.Entity entity : bfLevel.getAllEntities()) {
				if (entity instanceof net.minecraft.world.entity.Mob mob
						&& entity instanceof Enemy) {
					toKill.add(mob);
				}
			}
			for (net.minecraft.world.entity.Mob mob : toKill) {
				mob.kill();
			}
			RandomSurpriseMod.LOGGER.info("[征召战场] 胜利停留：杀死 {} 个敌对生物（保留掉落物）", toKill.size());
		}

		// 保存傀儡数据（稍后逐玩家传送回主世界）
		savedVictoryGolems.clear();
		if (bfLevel != null) {
			for (net.minecraft.world.entity.Entity entity : bfLevel.getAllEntities()) {
				if (entity instanceof net.minecraft.world.entity.Mob mob) {
					UUID ownerUUID = com.randomsurprise.affix.PlayerAffixManager.getGolemOwnerUUID(mob);
					if (ownerUUID != null) {
						var nbt = new net.minecraft.nbt.CompoundTag();
						mob.saveAsPassenger(nbt);
						savedVictoryGolems.add(new GolemSaveData(ownerUUID, nbt, mob.getX(), mob.getY(), mob.getZ()));
					}
				}
			}
		}

		// 清理所有玩家倒地状态
		DownedStateManager.clearAll(server);

		// 清空客户端战场HUD
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			com.randomsurprise.network.ModNetworking.sendBattlefieldMobCount(player, 0, 0);
		}
		glowingCounter = 0;

		// 应用奖励（好词条）
		AffixRarity rarity = getRarityForDifficulty(battlefieldDifficulty);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			PlayerAffixManager.addRandomGoodAffix(player.getUUID(), rarity);
		}
		consecutiveFailures = 0;
		broadcast(server, Component.translatable("battlefield.randomsurprise.victory", rarity.getDisplayName()));

		// 设置状态
		state = GameState.BATTLEFIELD_VICTORY;
		victoryCountdownTicks = VICTORY_STAY_DURATION;
		exitedPlayers.clear();

		// 广播停留提示
		broadcast(server, Component.translatable("battlefield.randomsurprise.victory_stay", 30));

		// 同步到客户端
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (BattlefieldDimension.isInBattlefield(player)) {
				com.randomsurprise.network.ModNetworking.sendToClient(player,
						new com.randomsurprise.network.BattlefieldVictoryPayload(true, 30));
			}
		}

		RandomSurpriseMod.LOGGER.info("[征召战场] 进入胜利停留阶段，30秒后自动退出，傀儡 {} 个待传送", savedVictoryGolems.size());
		save();
	}

	/** 胜利停留阶段 tick：倒计时 + 检查玩家退出 */
	private static void tickVictory(MinecraftServer server) {
		victoryCountdownTicks--;

		// 每秒同步倒计时到客户端
		int secondsLeft = victoryCountdownTicks / 20;
		if (victoryCountdownTicks % 20 == 0 && secondsLeft > 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (BattlefieldDimension.isInBattlefield(player)) {
					com.randomsurprise.network.ModNetworking.sendToClient(player,
							new com.randomsurprise.network.BattlefieldVictoryPayload(true, secondsLeft));
				}
			}
		}

		// 倒计时结束：传送所有剩余玩家
		if (victoryCountdownTicks <= 0) {
			for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
				if (BattlefieldDimension.isInBattlefield(player)) {
					teleportPlayerFromVictory(player);
				}
			}
		}

		// 检查是否所有玩家都已退出
		checkAllPlayersExited(server);
	}

	/** 玩家请求提前退出（长按空格5秒后由客户端发送） */
	public static void requestEarlyExit(ServerPlayer player) {
		if (state != GameState.BATTLEFIELD_VICTORY) return;
		if (!BattlefieldDimension.isInBattlefield(player)) return;
		if (exitedPlayers.contains(player.getUUID())) return;
		teleportPlayerFromVictory(player);
		checkAllPlayersExited(player.server);
	}

	/** 传送单个玩家回原位置（胜利停留阶段退出） */
	private static void teleportPlayerFromVictory(ServerPlayer player) {
		if (exitedPlayers.contains(player.getUUID())) return;

		// 清除负面效果
		for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects())) {
			if (!effect.getEffect().isBeneficial()) {
				player.removeEffect(effect.getEffect());
			}
		}

		// 传送回原位置
		LocationData loc = consumeOriginalLocation(player.getUUID());
		if (loc != null) {
			teleportBack(player, loc);
		} else {
			// 兜底：传送到主世界出生点
			var overworld = player.server.overworld();
			player.teleportTo(overworld, 0.5, 64, 0.5, java.util.Collections.emptySet(), 0, 0);
		}

		// 恢复游戏模式
		net.minecraft.world.level.GameType mode = originalGameModes.remove(player.getUUID());
		if (mode != null) {
			player.setGameMode(mode);
		}

		// 传送该玩家的傀儡回主世界
		Iterator<GolemSaveData> it = savedVictoryGolems.iterator();
		while (it.hasNext()) {
			GolemSaveData data = it.next();
			if (data.ownerUUID.equals(player.getUUID()) && player.level() instanceof ServerLevel ownerLevel) {
				try {
					var entity = net.minecraft.world.entity.EntityType.loadEntityRecursive(
							data.nbt, ownerLevel, e -> e);
					if (entity != null) {
						double offsetX = (java.util.concurrent.ThreadLocalRandom.current().nextDouble() - 0.5) * 3;
						double offsetZ = (java.util.concurrent.ThreadLocalRandom.current().nextDouble() - 0.5) * 3;
						entity.setPos(player.getX() + offsetX, player.getY(), player.getZ() + offsetZ);
						ownerLevel.addFreshEntity(entity);
						RandomSurpriseMod.LOGGER.info("[征召战场] 傀儡跟随 {} 回到主世界", player.getName().getString());
					}
					it.remove();
				} catch (Throwable t) {
					RandomSurpriseMod.LOGGER.warn("[征召战场] 傀儡传送失败: {}", t.getMessage());
				}
			}
		}

		// 通知客户端退出胜利阶段
		com.randomsurprise.network.ModNetworking.sendToClient(player,
				new com.randomsurprise.network.BattlefieldVictoryPayload(false, 0));

		exitedPlayers.add(player.getUUID());
		RandomSurpriseMod.LOGGER.info("[征召战场] 玩家 {} 已退出胜利停留阶段", player.getName().getString());
	}

	/** 检查是否所有玩家都已退出战场维度，若是则重置战场 */
	private static void checkAllPlayersExited(MinecraftServer server) {
		if (state != GameState.BATTLEFIELD_VICTORY) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (BattlefieldDimension.isInBattlefield(player)) {
				return; // 还有人在战场
			}
		}
		// 所有玩家已退出 → 重置战场
		resetBattlefieldAfterVictory(server);
	}

	/**
	 * 所有玩家退出后重置战场：清除掉落物 + 恢复场景 + 状态转移
	 */
	private static void resetBattlefieldAfterVictory(MinecraftServer server) {
		RandomSurpriseMod.LOGGER.info("[征召战场] 所有玩家已退出，开始重置战场");

		// 清除战场维度所有非玩家实体（掉落物、残留生物、弹射物、经验球等）
		ServerLevel battlefield = BattlefieldDimension.getBattlefieldLevel(server);
		if (battlefield != null) {
			List<net.minecraft.world.entity.Entity> toRemove = new ArrayList<>();
			for (net.minecraft.world.entity.Entity entity : battlefield.getAllEntities()) {
				if (entity instanceof net.minecraft.world.entity.item.ItemEntity
						|| entity instanceof net.minecraft.world.entity.projectile.Projectile
						|| entity instanceof net.minecraft.world.entity.ExperienceOrb
						|| entity instanceof net.minecraft.world.entity.Mob
						|| entity instanceof net.minecraft.world.entity.item.PrimedTnt) {
					toRemove.add(entity);
				}
			}
			for (net.minecraft.world.entity.Entity e : toRemove) {
				e.discard();
			}
			// 重置竞技场标记（下次进入时重新生成）
			resetArenaMarker(battlefield);
			RandomSurpriseMod.LOGGER.info("[征召战场] 清除 {} 个残留实体，竞技场标记已重置", toRemove.size());
		}

		// 重置计数器
		currentWave = 0;
		totalWaves = 0;
		nextWaveCountdown = 0;
		prepRoomTicks = 0;
		aliveBosses.clear();
		exitedPlayers.clear();
		savedVictoryGolems.clear();
		victoryCountdownTicks = 0;

		// 发送结算画面
		AffixRarity resultRarity = getRarityForDifficulty(battlefieldDifficulty);
		totalBattlefieldCount++;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			com.randomsurprise.network.ModNetworking.sendToClient(player,
					new com.randomsurprise.network.BattlefieldResultPayload(
							true, resultRarity.getDisplayName(),
							battlefieldDifficulty, totalBattlefieldCount));
		}

		// 状态流转（与原endBattlefield成功路径一致）
		if (victoryWasFinal) {
			enterWorldPeace(server);
		} else if (victoryWasDoomsdayDanger) {
			doomsdayDangerBattleCount++;
			state = GameState.DOOMSDAY_DANGER;
			broadcast(server, Component.translatable("battlefield.randomsurprise.danger_progress",
					doomsdayDangerBattleCount, DOOMSDAY_DANGER_BATTLE_THRESHOLD));
		} else if (victoryFromDoomsday) {
			enterDoomsdayDanger(server);
		} else {
			state = GameState.NORMAL;
		}

		RandomSurpriseMod.LOGGER.info("[征召战场] 战场重置完成 newState={} total={}", state, totalBattlefieldCount);
		save();
	}

	/** Boss死亡回调：仅从存活Boss列表中移除（波次由固定倒计时控制，不依赖Boss死亡） */
	public static void onBossDeath(UUID bossUuid) {
		// 仅处理被追踪的战场Boss（非Boss实体直接忽略）
		aliveBosses.remove(bossUuid);
		// 波次由固定倒计时（WAVE_INTERVAL_TICKS）控制，Boss死亡不再触发下一波
	}

	/** 玩家死亡回调（用于末日倒计时死亡计数） */
	public static void onPlayerDeath(ServerPlayer player) {
		if (state == GameState.DOOMSDAY_COUNTDOWN) {
			doomsdayDeathCount++;
			save();
			if (doomsdayDeathCount > getDoomsdayDeathLimit()) {
				// 死亡超限 → 进入随机世界入侵
				enterWorldInvasion(player.level().getServer());
			}
		}
	}

	// ===== 各阶段 tick 处理 =====

	/** 末日倒计时 tick：倒计时 + 周期性方块消除 + 主世界生物Boss化 */
	private static void tickDoomsdayCountdown(MinecraftServer server) {
		doomsdayCountdownTicks--;
		// 每100tick（5秒）执行一次方块消除
		blockRemoveCounter++;
		if (blockRemoveCounter >= 100) {
			blockRemoveCounter = 0;
			removeRandomBlocksNearPlayers(server);
		}
		// 倒计时结束 → 自动触发征召战场
		if (doomsdayCountdownTicks <= 0) {
			startBattlefield(server);
		}
	}

	/** 随机世界入侵 tick：每120s触发全服负面事件 */
	private static void tickWorldInvasion(MinecraftServer server) {
		invasionTickCounter++;
		if (invasionTickCounter >= INVASION_INTERVAL_TICKS) {
			invasionTickCounter = 0;
			// 触发全服负面事件
			try {
				com.randomsurprise.SurpriseActions.triggerGlobalNegativeEvent(server);
				broadcast(server, Component.translatable("battlefield.randomsurprise.invasion_wave"));
				RandomSurpriseMod.LOGGER.info("[征召战场] 入侵态：触发全服负面事件");
			} catch (Throwable t) {
				RandomSurpriseMod.LOGGER.error("[征召战场] 入侵负面事件失败: {}", t.getMessage());
			}
		}
	}

	/**
	 * 末日倒计时期间：在玩家周边随机消除方块（增加紧迫感）
	 * 每5秒在每位玩家半径10-20范围内随机消除1个非基岩方块
	 */
	private static void removeRandomBlocksNearPlayers(MinecraftServer server) {
		java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			try {
				if (!(player.level() instanceof ServerLevel sl)) continue;
				// 在玩家半径10-20范围随机选位置
				double angle = rnd.nextDouble(Math.PI * 2);
				double dist = rnd.nextDouble(10, 20);
				int bx = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
				int by = (int) Math.floor(player.getY() + rnd.nextDouble(-3, 3));
				int bz = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
				var pos = new net.minecraft.core.BlockPos(bx, by, bz);
				var state = sl.getBlockState(pos);
				// 排除基岩/空气/液体
				if (state.isAir()) continue;
				if (state.getBlock() == net.minecraft.world.level.block.Blocks.BEDROCK) continue;
				if (state.liquid()) continue;
				// 缺陷7: 跳过玩家放置的方块（不可破坏的方块跳过）
				if (state.getBlock().defaultBlockState().getDestroySpeed(null, null) < 0) continue;  // 不可破坏的跳过
				// 缺陷7: 只破坏自然方块（白名单方式），避免破坏玩家放置的方块
				if (!isNaturalBlock(state)) continue;
				// 移除方块（不掉落）
				sl.removeBlock(pos, false);
				// 在原位置生成少量火焰增加紧张感（5%概率）
				if (rnd.nextDouble() < 0.05) {
					sl.setBlock(pos, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 3);
				}
			} catch (Throwable ignored) {
				// 单个玩家失败不影响其他玩家
			}
		}
	}

	/**
	 * 缺陷7: 判断方块是否为自然生成方块（白名单方式）
	 * 仅破坏自然方块，避免破坏玩家放置的方块
	 */
	private static boolean isNaturalBlock(net.minecraft.world.level.block.state.BlockState state) {
		var block = state.getBlock();
		// 自然方块：石头、泥土、沙子、沙砾、花岗岩、闪长岩、安山岩、煤矿石、铁矿石、金矿石等
		return block == net.minecraft.world.level.block.Blocks.STONE
			|| block == net.minecraft.world.level.block.Blocks.DIRT
			|| block == net.minecraft.world.level.block.Blocks.SAND
			|| block == net.minecraft.world.level.block.Blocks.GRAVEL
			|| block == net.minecraft.world.level.block.Blocks.GRANITE
			|| block == net.minecraft.world.level.block.Blocks.DIORITE
			|| block == net.minecraft.world.level.block.Blocks.ANDESITE
			|| block == net.minecraft.world.level.block.Blocks.COBBLESTONE
			|| block == net.minecraft.world.level.block.Blocks.COAL_ORE
			|| block == net.minecraft.world.level.block.Blocks.IRON_ORE
			|| block == net.minecraft.world.level.block.Blocks.GOLD_ORE
			|| block == net.minecraft.world.level.block.Blocks.GRASS_BLOCK
			|| block == net.minecraft.world.level.block.Blocks.PODZOL
			|| block == net.minecraft.world.level.block.Blocks.NETHERRACK
			|| block == net.minecraft.world.level.block.Blocks.BASALT
			|| block == net.minecraft.world.level.block.Blocks.BLACKSTONE
			|| block == net.minecraft.world.level.block.Blocks.END_STONE;
	}

	/** 战场进行中 tick：检测胜负条件（每20tick检测一次以节省开销） */
private static int tickCounter = 0;
private static int glowingCounter = 0;  // 高光效果应用计数器
private static void tickBattlefield(MinecraftServer server) {
	// 准备房间阶段：30秒倒计时，结束后传送至竞技场并生成第一波
	if (state == GameState.BATTLEFIELD_PREP) {
		tickPrepRoom(server);
		return;
	}

	// 下一波生成倒计时（每tick递减，到0时若有剩余波次则生成下一波）
	if (nextWaveCountdown > 0) {
		nextWaveCountdown--;
		if (nextWaveCountdown == 0) {
			if (currentWave < totalWaves) {
				currentWave++;
				spawnWave(server, currentWave);
			}
		}
	}

	// 更新倒地状态（每tick）
	DownedStateManager.tick(server);

	// 每20tick检测一次（1秒），降低开销
	tickCounter++;
	if (tickCounter < 20) return;
	tickCounter = 0;

	// v3: 计数剩余敌对生物并同步到客户端HUD + 高光显示
	syncBattlefieldMobCount(server);

	// v4: 战场生物越界检测 —— 距中心过远拉回，掉入虚空移除
	clampBattlefieldMobs(server);

	// 场上敌对生物全清时立即触发下一波（跳过60秒倒计时）
	if (currentWave < totalWaves && nextWaveCountdown > 0 && areAllHostilesCleared(server)) {
		RandomSurpriseMod.LOGGER.info("[征召战场] 场上敌对生物全清，提前触发第{}波", currentWave + 1);
		nextWaveCountdown = 0;
		currentWave++;
		spawnWave(server, currentWave);
	}

	// 胜利条件：所有波次已生成完 + 所有Boss被清
	if (currentWave >= totalWaves && aliveBosses.isEmpty()) {
		RandomSurpriseMod.LOGGER.info("[征召战场] 所有波次Boss全清，进入胜利停留阶段");
		onVictory(server);
		return;
	}

	// 失败条件：所有玩家都进入倒地状态
	if (DownedStateManager.allPlayersDowned(server)) {
		RandomSurpriseMod.LOGGER.info("[征召战场] 所有玩家倒地，结算失败");
		endBattlefield(server, false);
	}
}

/**
 * 准备房间阶段 tick：30秒倒计时，结束后传送至竞技场中心并生成第一波怪物
 */
private static void tickPrepRoom(MinecraftServer server) {
	prepRoomTicks--;
	int secondsLeft = prepRoomTicks / 20;
	// 每秒通知剩余时间
	if (prepRoomTicks % 20 == 0 && secondsLeft > 0) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.displayClientMessage(
					Component.translatable("battlefield.randomsurprise.prep_countdown", secondsLeft), true);
		}
	}
	// 倒计时结束 → 传送到竞技场中心 + 生成第一波
	if (prepRoomTicks <= 0) {
		ServerLevel battlefield = BattlefieldDimension.getBattlefieldLevel(server);
		if (battlefield == null) {
			RandomSurpriseMod.LOGGER.error("[征召战场] 准备房间结束但维度未加载，无法传送");
			return;
		}
		// 强制加载中心区块并生成竞技场结构（若未生成）
		battlefield.getChunk(BattlefieldDimension.ARENA_CENTER_X >> 4, BattlefieldDimension.ARENA_CENTER_Z >> 4);
		ArenaGenerator.generateIfNotExist(battlefield);
		// 将战场维度内的玩家传送到竞技场中心
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (BattlefieldDimension.isInBattlefield(player)) {
				player.teleportTo(battlefield,
						BattlefieldDimension.ARENA_CENTER_X + 0.5, BattlefieldDimension.ARENA_CENTER_Y,
						BattlefieldDimension.ARENA_CENTER_Z + 0.5,
						java.util.Collections.emptySet(), 0.0F, 0.0F);
			}
		}
		// 生成第一波怪物
		currentWave = 1;
		spawnWave(server, currentWave);
		state = GameState.BATTLEFIELD_ACTIVE;
		RandomSurpriseMod.LOGGER.info("[征召战场] 准备房间结束，进入竞技场，生成第{}波/共{}波", currentWave, totalWaves);
		save();
	}
}

/**
 * v4: 战场生物越界检测（每秒一次，与 tickBattlefield 同节奏）
 * v20: 超出战场边界1格时，强制传送至距离边界最近的战场边缘点
 *  - Y < 40（掉入虚空）直接 discard 移除
 *  - 距竞技场中心水平距离 > ARENA_RADIUS+1（超出1格）传至最近边缘点
 */
private static void clampBattlefieldMobs(MinecraftServer server) {
	ServerLevel battlefield = BattlefieldDimension.getBattlefieldLevel(server);
	if (battlefield == null) return;
	int searchRange = BattlefieldDimension.ARENA_RADIUS + 10;
	net.minecraft.world.phys.AABB arenaBox = new net.minecraft.world.phys.AABB(
			BattlefieldDimension.ARENA_CENTER_X - searchRange, 0, BattlefieldDimension.ARENA_CENTER_Z - searchRange,
			BattlefieldDimension.ARENA_CENTER_X + searchRange, 320, BattlefieldDimension.ARENA_CENTER_Z + searchRange);
	double boundaryRadius = BattlefieldDimension.ARENA_RADIUS + 1;
	double edgeRadius = BattlefieldDimension.ARENA_RADIUS;
	for (net.minecraft.world.entity.Entity entity : battlefield.getEntities(null, arenaBox)) {
		if (!(entity instanceof Mob mob)) continue;
		if (mob.getY() < 40) {
			mob.discard();
			continue;
		}
		double dx = mob.getX() - BattlefieldDimension.ARENA_CENTER_X;
		double dz = mob.getZ() - BattlefieldDimension.ARENA_CENTER_Z;
		double distSq = dx * dx + dz * dz;
		if (distSq > boundaryRadius * boundaryRadius) {
			double dist = Math.sqrt(distSq);
			double ratio = edgeRadius / dist;
			double newX = BattlefieldDimension.ARENA_CENTER_X + dx * ratio;
			double newZ = BattlefieldDimension.ARENA_CENTER_Z + dz * ratio;
			mob.teleportTo(newX, mob.getY(), newZ);
		}
	}
}

/**
 * 统计战场维度内剩余敌对生物数量并同步到所有玩家客户端
 * 同时周期性（每5秒）对所有敌对生物应用发光效果以便高光显示
 */
private static void syncBattlefieldMobCount(MinecraftServer server) {
	ServerLevel battlefield = BattlefieldDimension.getBattlefieldLevel(server);
	if (battlefield == null) return;

	int totalMobs = 0;
	int totalBosses = aliveBosses.size();
	List<Mob> hostileMobs = new ArrayList<>();
	List<Mob> bossMobs = new ArrayList<>();

	for (net.minecraft.world.entity.Entity entity : battlefield.getAllEntities()) {
		if (!(entity instanceof Mob mob)) continue;
		if (aliveBosses.contains(mob.getUUID())) {
			bossMobs.add(mob);
			continue;
		}
		// 仅计数敌对生物（Enemy接口或类型名包含敌对关键字）
		if (mob instanceof Enemy || isHostileMobType(mob)) {
			totalMobs++;
			hostileMobs.add(mob);
		}
	}

	// 只向战场维度内的玩家发送同步包
	for (ServerPlayer player : server.getPlayerList().getPlayers()) {
		if (player.serverLevel().dimension().equals(battlefield.dimension())) {
			com.randomsurprise.network.ModNetworking.sendBattlefieldMobCount(player, totalMobs, totalBosses);
		}
	}

	// 每5秒（5次20tick循环）应用发光效果，持续6秒避免间隙
	glowingCounter++;
	if (glowingCounter >= 5) {
		glowingCounter = 0;
		for (Mob mob : hostileMobs) {
			mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, false, false));
		}
		for (Mob boss : bossMobs) {
			boss.addEffect(new MobEffectInstance(MobEffects.GLOWING, 120, 0, false, false));
		}
	}
}

/**
 * 检查战场上是否所有敌对生物（Boss + 小怪）都已清除
 * 用于提前触发下一波：当场上无敌对生物时跳过倒计时直接进入下一波
 */
private static boolean areAllHostilesCleared(MinecraftServer server) {
	if (!aliveBosses.isEmpty()) return false;  // 还有Boss存活
	ServerLevel battlefield = BattlefieldDimension.getBattlefieldLevel(server);
	if (battlefield == null) return false;
	// 检查是否还有非Boss的敌对生物（小怪/精英怪）
	for (net.minecraft.world.entity.Entity entity : battlefield.getAllEntities()) {
		if (!(entity instanceof Mob mob)) continue;
		if (aliveBosses.contains(mob.getUUID())) continue;  // 跳过Boss
		if (mob instanceof Enemy || isHostileMobType(mob)) return false;  // 还有小怪
	}
	return true;
}

/**
 * 判断实体是否为敌对生物类型（兜底，Enemy接口未覆盖的模组生物）
 */
private static boolean isHostileMobType(Mob mob) {
	String typeName = mob.getType().toShortString();
	return typeName.contains("zombie") || typeName.contains("skeleton")
			|| typeName.contains("creeper") || typeName.contains("spider")
			|| typeName.contains("enderman") || typeName.contains("witch")
			|| typeName.contains("phantom") || typeName.contains("pillager")
			|| typeName.contains("ravager") || typeName.contains("vindicator")
			|| typeName.contains("evoker") || typeName.contains("blaze")
			|| typeName.contains("ghast") || typeName.contains("wither")
			|| typeName.contains("slime") || typeName.contains("magma");
}

	// ===== 状态流转方法 =====

	/** 进入末日倒计时 */
	public static void enterDoomsdayCountdown(MinecraftServer server) {
		state = GameState.DOOMSDAY_COUNTDOWN;
		doomsdayCountdownTicks = DOOMSDAY_DURATION_TICKS;
		doomsdayDeathCount = 0;
		save();
		RandomSurpriseMod.LOGGER.info("[征召战场] 进入末日倒计时，持续 {} 秒", DOOMSDAY_DURATION_TICKS / 20);
	}

	/** 进入末日险境 */
	public static void enterDoomsdayDanger(MinecraftServer server) {
		state = GameState.DOOMSDAY_DANGER;
		doomsdayDangerBattleCount = 0;
		save();
		RandomSurpriseMod.LOGGER.info("[征召战场] 进入末日险境，增益词条大增");
	}

	/** 进入随机世界入侵（终局失败） */
	public static void enterWorldInvasion(MinecraftServer server) {
		state = GameState.WORLD_INVASION;
		invasionTickCounter = 0;
		save();
		RandomSurpriseMod.LOGGER.warn("[征召战场] 进入随机世界入侵！增益禁用、抽奖停止、负面事件每120s");
	}

	/** 进入世界平静（终局成功） */
	public static void enterWorldPeace(MinecraftServer server) {
		state = GameState.WORLD_PEACE;
		save();
		RandomSurpriseMod.LOGGER.info("[征召战场] 世界归于平静，坏词条全部禁用");
	}

	/**
	 * 代价重置：清空全服所有玩家词条 + 计数器归零，回到 NORMAL
	 * 仅在 WORLD_INVASION 状态可用（命令调用）
	 */
	public static boolean resetWithCost(MinecraftServer server) {
		if (state != GameState.WORLD_INVASION) return false;
		// 清空全服词条（代价）
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			com.randomsurprise.affix.PlayerAffixManager.clearAllAffixes(player.getUUID());
		}
		// 计数器归零
		state = GameState.NORMAL;
		totalEventCount = 0;
		totalBattlefieldCount = 0;
		consecutiveFailures = 0;
		doomsdayDangerBattleCount = 0;
		doomsdayDeathCount = 0;
		doomsdayCountdownTicks = 0;
		originalLocations.clear();
		aliveBosses.clear();
		save();
		RandomSurpriseMod.LOGGER.info("[征召战场] 已执行代价重置，回到常规态");
		return true;
	}

	// ===== 战场Boss生成与清理 =====

	/**
	 * 在准备房间位置生成一个简单的石砖房间结构（7×7，高5，带石砖地板）
	 * 房间中心为 (PREP_ROOM_X, PREP_ROOM_Y, PREP_ROOM_Z)
	 */
	private static void generatePrepRoom(ServerLevel level) {
		BlockPos base = new BlockPos(PREP_ROOM_X - 3, PREP_ROOM_Y, PREP_ROOM_Z - 3);
		// 墙壁（外围一圈，高度5）
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				for (int y = 0; y < 5; y++) {
					if (x == 0 || x == 6 || z == 0 || z == 6) {
						level.setBlock(new BlockPos(base.getX() + x, base.getY() + y, base.getZ() + z),
								Blocks.STONE_BRICKS.defaultBlockState(), 3);
					}
				}
			}
		}
		// 地板
		for (int x = 0; x < 7; x++) {
			for (int z = 0; z < 7; z++) {
				level.setBlock(new BlockPos(base.getX() + x, PREP_ROOM_Y - 1, base.getZ() + z),
						Blocks.STONE_BRICKS.defaultBlockState(), 3);
			}
		}
	}

	/**
	 * 批次生成：按波次生成怪物（每波1个Boss + 若干小怪，第2波起加入精英怪）
	 * @param server 服务器实例
	 * @param wave 当前波次（从1开始）
	 */
	private static void spawnWave(MinecraftServer server, int wave) {
		ServerLevel battlefieldLevel = BattlefieldDimension.getBattlefieldLevel(server);
		if (battlefieldLevel == null) return;

		int playerCount = server.getPlayerList().getPlayers().size();
		int difficulty = battlefieldDifficulty;

		// 生成1个Boss（追踪到 aliveBosses 以便波次推进）
		String bossId = BossPool.randomBoss(difficulty);
		if (bossId != null) {
			Mob boss = spawnBossAt(battlefieldLevel, bossId, difficulty);
			if (boss != null) {
				aliveBosses.add(boss.getUUID());
			}
		}

		// 生成小怪：玩家数 × 2 + 难度
		int mobCount = playerCount * 2 + difficulty;
		for (int i = 0; i < mobCount; i++) {
			String mobId = BossPool.randomHostileMob();
			if (mobId != null) {
				spawnMobAt(battlefieldLevel, mobId, difficulty);
			}
		}

		// 第2波及之后生成精英怪（更强的普通怪物，难度+1增强）
		if (wave >= 2) {
			int eliteCount = Math.min(3, 1 + wave / 2);
			for (int i = 0; i < eliteCount; i++) {
				String eliteId = BossPool.randomHostileMob();
				if (eliteId != null) {
					spawnMobAt(battlefieldLevel, eliteId, difficulty + 1);
				}
			}
		}

		broadcast(server, Component.translatable("battlefield.randomsurprise.wave_start", wave, totalWaves));
		RandomSurpriseMod.LOGGER.info("[征召战场] 生成第{}波/共{}波 boss+{}小怪",
				wave, totalWaves, mobCount);
		// 设置下一波固定倒计时（30秒后生成下一波，不依赖Boss死亡）
		nextWaveCountdown = WAVE_INTERVAL_TICKS;
	}
	
	/**
	 * 在战场竞技场内随机位置生成一个普通怪物
	 */
	private static Mob spawnMobAt(ServerLevel level, String entityId, int difficulty) {
		EntityType<?> type = BossPool.resolveEntityType(entityId);
		if (type == null) {
			return null;
		}
		java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
		double angle = rnd.nextDouble(Math.PI * 2);
		double dist = rnd.nextDouble(10, 55);
		double x = BattlefieldDimension.ARENA_CENTER_X + Math.cos(angle) * dist;
		double z = BattlefieldDimension.ARENA_CENTER_Z + Math.sin(angle) * dist;
		double y = BattlefieldDimension.ARENA_CENTER_Y + 1;
		
		try {
			net.minecraft.world.entity.Entity entity = type.create(level);
			if (!(entity instanceof Mob mob)) {
				return null;
			}
			mob.setPos(x, y, z);
			mob.setYRot(rnd.nextFloat() * 360F);
			mob.setXRot(0F);
			mob.setPersistenceRequired();
			// 小怪也应用难度增强（较弱）
			enhanceMob(mob, difficulty);
			level.addFreshEntity(mob);
			return mob;
		} catch (Throwable t) {
			RandomSurpriseMod.LOGGER.error("[征召战场] 生成小怪失败 {}: {}", entityId, t.getMessage());
			return null;
		}
	}
	
	/**
	 * 小怪属性增强（HP/力量/速度/护甲随难度递增，比Boss弱）
	 */
	private static void enhanceMob(Mob mob, int difficulty) {
		try {
			// HP倍率：1.0 + difficulty * 0.15，上限3.0
			double hpMultiplier = Math.min(1.0 + difficulty * 0.15, 3.0);
			var maxAttr = mob.getAttribute(Attributes.MAX_HEALTH);
			if (maxAttr != null) {
				double base = maxAttr.getBaseValue();
				maxAttr.setBaseValue(base * hpMultiplier);
				mob.setHealth((float) maxAttr.getBaseValue());
			}
			// 力量等级：difficulty/2，上限4
			int strengthLevel = Math.min(difficulty / 2, 4);
			if (strengthLevel > 0) {
				mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, strengthLevel - 1, false, false, false));
			}
			// 速度提升（难度3+）
			if (difficulty >= 3) {
				mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 0, false, false, false));
			}
			// 抗性提升（难度5+）
			if (difficulty >= 5) {
				int resistLevel = Math.min(2, (difficulty - 5) / 2);
				mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, resistLevel, false, false, false));
			}
		} catch (Throwable t) {
			// ignore
		}
	}

	/**
	 * 决战Boss生成：从全部Boss池选Boss，分波次入场
	 * 总Boss数 = 玩家数 × 1.5（向下取整，至少1）
	 * 当前一次性生成全部，后续可由 tick 分批入场
	 */
	private static void spawnFinalBattleBosses(ServerLevel battlefield, int playerCount) {
		// 缺陷12: Boss数量基于战场维度内实际人数（而非全服在线人数）
		playerCount = (int) battlefield.players().stream()
				.filter(p -> p instanceof net.minecraft.server.level.ServerPlayer).count();
		int totalBosses = Math.max(1, (int) Math.floor(playerCount * FINAL_BOSS_PLAYER_RATIO));
		List<String> finalPool = BossPool.getFinalBattleBosses();
		if (finalPool.isEmpty()) {
			RandomSurpriseMod.LOGGER.warn("[征召战场] 决战Boss池为空，使用默认Boss");
			finalPool = List.of("minecraft:warden", "minecraft:wither", "minecraft:ravager");
		}
		java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
		for (int i = 0; i < totalBosses; i++) {
			String bossId = finalPool.get(rnd.nextInt(finalPool.size()));
			Mob boss = spawnBossAt(battlefield, bossId, battlefieldDifficulty);
			if (boss != null) {
				aliveBosses.add(boss.getUUID());
			}
		}
	}

	/**
	 * 在战场竞技场内随机位置生成一个Boss并应用难度增强
	 * @return 生成的Mob实例，失败返回null
	 */
	private static Mob spawnBossAt(ServerLevel level, String entityId, int difficulty) {
		EntityType<?> type = BossPool.resolveEntityType(entityId);
		if (type == null) {
			RandomSurpriseMod.LOGGER.warn("[征召战场] 无法解析实体类型: {}", entityId);
			return null;
		}
		// 在竞技场内随机位置（半径60以内）
		java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
		double angle = rnd.nextDouble(Math.PI * 2);
		double dist = rnd.nextDouble(15, 60);
		double x = BattlefieldDimension.ARENA_CENTER_X + Math.cos(angle) * dist;
		double z = BattlefieldDimension.ARENA_CENTER_Z + Math.sin(angle) * dist;
		double y = BattlefieldDimension.ARENA_CENTER_Y + 1;

		try {
			// 1.20.1: 使用 EntityType.create(level) 静态生成
			net.minecraft.world.entity.Entity entity = type.create(level);
			if (!(entity instanceof Mob mob)) {
				RandomSurpriseMod.LOGGER.warn("[征召战场] 实体 {} 不是 Mob，无法作为Boss", entityId);
				return null;
			}
			mob.setPos(x, y, z);
			mob.setYRot(rnd.nextFloat() * 360F);
			mob.setXRot(0F);
			// 设置PersistenceRequired以避免AI自然消失
			mob.setPersistenceRequired();
			// 应用难度增强
			enhanceBoss(mob, difficulty);
			// 添加到世界
			level.addFreshEntity(mob);
			return mob;
		} catch (Throwable t) {
			RandomSurpriseMod.LOGGER.error("[征召战场] 生成Boss失败 {}: {}", entityId, t.getMessage());
			return null;
		}
	}

	/**
	 * Boss属性增强（HP/ATK倍率随难度递增）
	 * HP倍率 = min(1 + difficulty * 0.25, 4.0) * 玩家数缩放
	 * ATK通过力量药水等级 = difficulty/3
	 */
	private static void enhanceBoss(Mob boss, int difficulty) {
		try {
			// v16: HP倍率 = 难度倍率 * 玩家数缩放
			int playerCount = boss.level() instanceof net.minecraft.server.level.ServerLevel sl
				? sl.players().size() : 1;
			double hpMultiplier = com.randomsurprise.config.BalanceMath.calculateBossHpMultiplier(difficulty, playerCount);
			var maxAttr = boss.getAttribute(Attributes.MAX_HEALTH);
			if (maxAttr != null) {
				// 基础值已包含原版HP，叠加 modifier
				double base = maxAttr.getBaseValue();
				maxAttr.setBaseValue(base * hpMultiplier);
				boss.setHealth(boss.getMaxHealth());
			}
			// 攻击力倍率（通过力量药水，duration=Integer.MAX_VALUE 永久生效）
		int strengthLevel = Math.max(1, difficulty / 3);
		boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, strengthLevel - 1, false, false));
		// 抗性提升（中难度以上）
		if (difficulty >= 5) {
			int resistLevel = Math.min(1, (difficulty - 5) / 4);
			boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, resistLevel, false, false));
		}
		// 速度提升（高难度）
		if (difficulty >= 7) {
			boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 0, false, false));
		}
		} catch (Throwable t) {
			RandomSurpriseMod.LOGGER.warn("[征召战场] 增强Boss失败: {}", t.getMessage());
		}
	}

	/**
	 * 清理战场维度：移除所有Boss类实体和掉落物
	 * 用于战场结算时清场
	 */
	private static void clearBattlefield(MinecraftServer server) {
		ServerLevel battlefield = BattlefieldDimension.getBattlefieldLevel(server);
		if (battlefield == null) {
			aliveBosses.clear();
			return;
		}
		// 移除所有Mob类实体（Boss和小怪都清）
		List<net.minecraft.world.entity.Entity> toRemove = new ArrayList<>();
		for (net.minecraft.world.entity.Entity entity : battlefield.getAllEntities()) {
			if (entity instanceof Mob) {
				toRemove.add(entity);
			} else if (entity instanceof net.minecraft.world.entity.item.ItemEntity) {
				toRemove.add(entity);
			} else if (entity instanceof net.minecraft.world.entity.projectile.Projectile
					|| entity instanceof net.minecraft.world.entity.ExperienceOrb
					|| entity instanceof net.minecraft.world.entity.item.PrimedTnt) {
				// 优化18: 额外清理弹射物、经验球、TNT等非玩家实体
				toRemove.add(entity);
			}
		}
		for (net.minecraft.world.entity.Entity e : toRemove) {
			e.discard();
		}
		aliveBosses.clear();
		// 重置波次与准备房间计数器
		currentWave = 0;
		totalWaves = 0;
		nextWaveCountdown = 0;
		prepRoomTicks = 0;
		// 重置竞技场结构标记，下次进入时重新生成（确保干净环境）
		resetArenaMarker(battlefield);
		RandomSurpriseMod.LOGGER.info("[征召战场] 战场维度清理完成，移除 {} 个实体", toRemove.size());
	}

	/**
	 * 重置竞技场生成标记（移除bedrock标记方块），下次进入时重新生成竞技场
	 * 确保每次战斗都有干净的环境
	 */
	private static void resetArenaMarker(ServerLevel battlefield) {
		if (battlefield == null) return;
		net.minecraft.core.BlockPos markerPos = new net.minecraft.core.BlockPos(0, 58, 0);
		if (battlefield.getBlockState(markerPos).is(net.minecraft.world.level.block.Blocks.BEDROCK)) {
			battlefield.setBlock(markerPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
			RandomSurpriseMod.LOGGER.info("[征召战场] 竞技场标记已重置，下次进入将重新生成");
		}
	}

	// ===== 通用工具方法 =====

	/** 全服广播消息（发送给所有在线玩家） */
	public static void broadcast(MinecraftServer server, Component message) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.sendSystemMessage(message);
		}
	}

	/**
	 * 难度 → 稀有度映射（决定奖惩词条的稀有度）
	 * 1-2: COMMON, 3-4: UNCOMMON, 5-6: RARE, 7-8: EPIC, 9-10: LEGENDARY, 11+: MYTHIC
	 */
	private static AffixRarity getRarityForDifficulty(int difficulty) {
		if (difficulty <= 2) return AffixRarity.COMMON;
		if (difficulty <= 4) return AffixRarity.UNCOMMON;
		if (difficulty <= 6) return AffixRarity.RARE;
		if (difficulty <= 8) return AffixRarity.EPIC;
		if (difficulty <= 10) return AffixRarity.LEGENDARY;
		return AffixRarity.MYTHIC;
	}

	/** 传送玩家回原位置（跨维度安全传送） */
	private static void teleportBack(ServerPlayer player, LocationData loc) {
		var server = player.level().getServer();
		if (server == null) return;
		var targetLevel = loc.getServerLevel(server);
		if (targetLevel == null) {
			// 维度解析失败，兜底主世界出生点
			targetLevel = server.overworld();
			player.teleportTo(targetLevel, 0.5, 64, 0.5,
					java.util.Collections.emptySet(), 0.0F, 0.0F);
		} else {
			player.teleportTo(targetLevel, loc.x(), loc.y(), loc.z(),
					java.util.Collections.emptySet(), loc.yRot(), loc.xRot());
		}
	}

	/** 记录玩家原位置（进入战场时调用） */
	public static void recordOriginalLocation(ServerPlayer player) {
		originalLocations.put(player.getUUID(), LocationData.of(
				(ServerLevel) player.level(), player.getX(), player.getY(), player.getZ(),
				player.getYRot(), player.getXRot()));
	}

	/** 获取并移除玩家原位置（结算回传时调用） */
	public static LocationData consumeOriginalLocation(UUID playerId) {
		return originalLocations.remove(playerId);
	}

	/** v19: 傀儡保存数据（用于战后跟随回主世界） */
	private static class GolemSaveData {
		final UUID ownerUUID;
		final net.minecraft.nbt.CompoundTag nbt;
		final double x, y, z;

		GolemSaveData(UUID ownerUUID, net.minecraft.nbt.CompoundTag nbt, double x, double y, double z) {
			this.ownerUUID = ownerUUID;
			this.nbt = nbt;
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}
}
