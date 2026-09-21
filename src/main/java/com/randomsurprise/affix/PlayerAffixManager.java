package com.randomsurprise.affix;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.randomsurprise.battlefield.BossPool;
import com.randomsurprise.config.BalanceConfig;
import com.randomsurprise.config.BalanceMath;
import com.randomsurprise.network.AffixSyncPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家词条数据管理
 * - 持久化存储每位玩家的词条（JSON文件）
 * - 定期应用好词条效果（药水效果刷新）
 * - 计算坏词条全局效果（用于怪物增强）
 * - 同步词条数据到客户端（R键查看面板可见）
 */
public class PlayerAffixManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<UUID, List<String>> PLAYER_AFFIXES = new HashMap<>();
	private static Path dataPath;
	// v19: 抽奖次数计数（所有抽取，好+坏）
	private static final Map<UUID, Integer> DRAW_COUNTS = new HashMap<>();
	private static Path drawCountsPath;
	// v19: 不可移除的敌对词条（每50次抽取后第一个敌对词条锁定）
	private static final Map<UUID, List<String>> LOCKED_AFFIXES = new HashMap<>();
	// v19: 待锁定的里程碑（达到50/100/150...次抽取时记录，下一个敌对词条触发锁定）
	private static final Map<UUID, List<Integer>> PENDING_LOCK_MILESTONES = new HashMap<>();
	private static Path lockedAffixesPath;
	// v19: 当前服务器引用（用于判断玩家在线状态）
	private static MinecraftServer currentServer;
	/** 玩家最大生命加成的 AttributeModifier ID（来自好词条 healthBonus 累计） */
	private static final java.util.UUID HEALTH_BONUS_ID = java.util.UUID.fromString("d3a7f2b1-4e5c-6a8b-9c0d-1e2f3a4b5c6d");
	/** v7: 玩家攻击速度加成 AttributeModifier ID（来自好词条 attackSpeedBonus） */
	private static final java.util.UUID ATTACK_SPEED_BONUS_ID = java.util.UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d");
	/** v7: 玩家移动速度加成 AttributeModifier ID（来自好词条 moveSpeedBonus） */
	private static final java.util.UUID MOVE_SPEED_BONUS_ID = java.util.UUID.fromString("b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e");
	/** v10: 玩家移速百分比加成 AttributeModifier ID（来自好词条 moveSpeedPercent） */
	private static final java.util.UUID MOVE_SPEED_PERCENT_ID = java.util.UUID.fromString("c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f");
	/** v10: 玩家基础攻击力加成 AttributeModifier ID（来自好词条 baseAttackDamage） */
	private static final java.util.UUID BASE_ATTACK_DAMAGE_ID = java.util.UUID.fromString("d4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f8a");
	// ========== v15: 傀儡词条效果专用 AttributeModifier UUID（避免与玩家冲突） ==========
	private static final java.util.UUID GOLEM_HEALTH_ID = java.util.UUID.fromString("e1f2a3b4-c5d6-4e7f-8a9b-0c1d2e3f4a5b");
	private static final java.util.UUID GOLEM_ATTACK_ID = java.util.UUID.fromString("f2a3b4c5-d6e7-4f8a-9b0c-1d2e3f4a5b6c");
	private static final java.util.UUID GOLEM_SPEED_ID = java.util.UUID.fromString("a3b4c5d6-e7f8-4a9b-0c1d-2e3f4a5b6c7d");
	private static final java.util.UUID GOLEM_ARMOR_ID = java.util.UUID.fromString("b4c5d6e7-f8a9-4b0c-1d2e-3f4a5b6c7d8e");
	private static final java.util.UUID GOLEM_ARMOR_TOUGH_ID = java.util.UUID.fromString("c5d6e7f8-a9b0-4c1d-2e3f-4a5b6c7d8e9f");
	private static final java.util.UUID GOLEM_ATTACK_SPEED_ID = java.util.UUID.fromString("d6e7f8a9-b0c1-4d2e-3f4a-5b6c7d8e9f0a");
	/** 玩家召唤友方生物的冷却时间（毫秒），防止无限召唤 */
	private static final Map<UUID, Long> SUMMON_COOLDOWNS = new HashMap<>();
	private static final long SUMMON_COOLDOWN_MS = 10000; // 10秒冷却
	/** 玩家召唤友方生物的总数上限 */
	private static final int MAX_PLAYER_SUMMONS = 3;
	/** 敌对生物召唤同伴的冷却（按生物UUID） */
	private static final Map<UUID, Long> HOSTILE_SUMMON_COOLDOWNS = new HashMap<>();
	private static final long HOSTILE_SUMMON_COOLDOWN_MS = 15000; // 15秒冷却
	/** 防止 Boss 伤害加成递归调用（v3 新增） */
	private static final ThreadLocal<Boolean> IN_BOSS_BONUS = ThreadLocal.withInitial(() -> false);
	/** 玩家护盾冷却时间（毫秒），防止护盾无限刷新（v4 新增） */
	private static final Map<UUID, Long> ABSORPTION_COOLDOWNS = new HashMap<>();
	/** 全局已分配好词条ID集合（好词条独占：一旦被任何玩家获得，其他玩家不可再抽到） */
	private static final Set<String> ASSIGNED_GOOD_AFFIXES = ConcurrentHashMap.newKeySet();
	/** 召唤铁傀儡的所有者 UUID 标记 key（PersistentData，CompoundTag 以 String 为 key） */
	public static final String SUMMON_OWNER_KEY = "randomsurprise.summon_owner";
	/** v10: 火焰燃烧堆叠数据（攻击者UUID -> 目标UUID -> 堆叠层数），用于实现连续攻击增加燃烧伤害 */
	private static final Map<UUID, Map<UUID, Integer>> FIRE_BURN_STACKS = new ConcurrentHashMap<>();
	/** v10: 火焰堆叠衰减时间（tick），每隔 60 tick(3秒) 减少一层 */
	private static final long FIRE_STACK_DECAY_TICKS = 60;
	/** v10: 火焰堆叠最大层数 */
	private static final int MAX_FIRE_STACKS = 10;
	/** v12: 攻击附带抑制回血追踪（目标UUID → 过期时间戳），3秒内减少目标回血 */
	private static final Map<UUID, Long> ANTI_HEAL_EXPIRY = new ConcurrentHashMap<>();
	/** v12: 攻击附带抑制回血追踪（目标UUID → 抑制百分比） */
	private static final Map<UUID, Integer> ANTI_HEAL_PERCENT = new ConcurrentHashMap<>();
	/** v12: 抑制回血持续时间（毫秒）= 3秒 */
	private static final long ANTI_HEAL_DURATION_MS = 3000;

	// ========== v13: 16个好词条机制型状态追踪 ==========
	/** 痛楚循环：玩家UUID -> 层数 */
	private static final Map<UUID, Integer> PAIN_CYCLE_STACKS = new ConcurrentHashMap<>();
	/** 虚空侵蚀：目标UUID -> 侵蚀数据[层数, 上次攻击tick] */
	private static final Map<UUID, long[]> VOID_EROSION_STACKS = new ConcurrentHashMap<>();
	/** 动能蓄势：玩家UUID -> 是否在疾跑/跳跃后 */
	private static final Map<UUID, Boolean> MOMENTUM_READY = new ConcurrentHashMap<>();
	/** 灵魂虹吸：玩家UUID -> 灵魂层数 */
	private static final Map<UUID, Integer> SOUL_SIPHON_STACKS = new ConcurrentHashMap<>();
	/** 守卫击杀：玩家UUID -> 护盾剩余tick */
	private static final Map<UUID, Integer> GUARD_KILL_SHIELD_TICKS = new ConcurrentHashMap<>();
	/** v14: 玩家急迫等级（替代急迫药水，供 BreakSpeed 事件使用） */
	private static final Map<UUID, Integer> HASTE_LEVELS = new ConcurrentHashMap<>();
	/** v14: 玩家吸收护盾值（替代吸收药水，供 onServerTick 手动管理） */
	private static final Map<UUID, Float> ABSORPTION_LEVELS = new ConcurrentHashMap<>();
	/** 虚空侵蚀持续时间（tick）= 100 tick（5秒） */
	private static final long VOID_EROSION_DURATION_TICKS = 100;
	/** 灵魂虹吸最大层数 */
	private static final int MAX_SOUL_STACKS = 10;

	// ========== v15: 傀儡词条效果状态追踪 ==========
	/** v15: 傀儡词条效果追踪 - 傀儡UUID → 主人UUID */
	private static final Map<UUID, UUID> GOLEM_OWNER_MAP = new ConcurrentHashMap<>();
	/** v15: 傀儡火焰免疫（好词条或坏词条）- 傀儡UUID → true */
	private static final Set<UUID> GOLEM_FIRE_IMMUNE = ConcurrentHashMap.newKeySet();
	/** v15: 傀儡溺水免疫 - 傀儡UUID → true */
	private static final Set<UUID> GOLEM_DROWN_IMMUNE = ConcurrentHashMap.newKeySet();
	/** v15: 傀儡摔落免疫 - 傀儡UUID → true */
	private static final Set<UUID> GOLEM_FALL_IMMUNE = ConcurrentHashMap.newKeySet();
	/** v15: 傀儡反伤百分比 - 傀儡UUID → 反伤百分比 */
	private static final Map<UUID, Double> GOLEM_THORNS = new ConcurrentHashMap<>();
	/** v15: 傀儡吸血百分比 - 傀儡UUID → 吸血百分比 */
	private static final Map<UUID, Double> GOLEM_LIFESTEAL = new ConcurrentHashMap<>();
	/** v15: 傀儡每秒回血 - 傀儡UUID → 每秒回血量 */
	private static final Map<UUID, Float> GOLEM_REGEN = new ConcurrentHashMap<>();

	/** 初始化（服务器启动时调用） */
	public static void init(MinecraftServer server) {
		dataPath = com.randomsurprise.WorldDataPath.getWorldDataPath(server, "randomsurprise_affixes.json");
		drawCountsPath = com.randomsurprise.WorldDataPath.getWorldDataPath(server, "randomsurprise_draw_counts.json");
		lockedAffixesPath = com.randomsurprise.WorldDataPath.getWorldDataPath(server, "randomsurprise_locked_affixes.json");
		currentServer = server;
		PLAYER_AFFIXES.clear();
		DRAW_COUNTS.clear();
		LOCKED_AFFIXES.clear();
		PENDING_LOCK_MILESTONES.clear();
		load();
		loadDrawCounts();
		loadLockedAffixes();
		rebuildAssignedGoodAffixes();  // 重建全局已分配好词条集合
	}

	/** 加载数据 */
	public static void load() {
		try {
			if (dataPath != null && Files.exists(dataPath)) {
				String content = Files.readString(dataPath);
				Map<UUID, List<String>> loaded = GSON.fromJson(content,
						new TypeToken<Map<UUID, List<String>>>() {}.getType());
				if (loaded != null) PLAYER_AFFIXES.putAll(loaded);
			}
		} catch (Exception e) {
			com.randomsurprise.RandomSurpriseMod.LOGGER.warn("加载词条数据失败: {}", e.getMessage());
		}
	}

	/** 保存数据 */
	public static void save() {
		if (dataPath == null) return;
		try {
			Files.createDirectories(dataPath.getParent());
			Files.writeString(dataPath, GSON.toJson(PLAYER_AFFIXES));
		} catch (IOException e) {
			com.randomsurprise.RandomSurpriseMod.LOGGER.error("保存词条数据失败: {}", e.getMessage());
		}
		saveDrawCounts();
		saveLockedAffixes();
	}

	/** v19: 加载抽奖次数 */
	private static void loadDrawCounts() {
		try {
			if (drawCountsPath != null && Files.exists(drawCountsPath)) {
				String content = Files.readString(drawCountsPath);
				Map<UUID, Integer> loaded = GSON.fromJson(content,
						new TypeToken<Map<UUID, Integer>>() {}.getType());
				if (loaded != null) DRAW_COUNTS.putAll(loaded);
			}
		} catch (Exception e) {
			com.randomsurprise.RandomSurpriseMod.LOGGER.warn("加载抽奖次数失败: {}", e.getMessage());
		}
	}

	/** v19: 保存抽奖次数 */
	private static void saveDrawCounts() {
		if (drawCountsPath == null) return;
		try {
			Files.createDirectories(drawCountsPath.getParent());
			Files.writeString(drawCountsPath, GSON.toJson(DRAW_COUNTS));
		} catch (Exception e) {
			com.randomsurprise.RandomSurpriseMod.LOGGER.warn("保存抽奖次数失败: {}", e.getMessage());
		}
	}

	/** v19: 加载锁定词条数据 */
	@SuppressWarnings("unchecked")
	private static void loadLockedAffixes() {
		try {
			if (lockedAffixesPath != null && Files.exists(lockedAffixesPath)) {
				String content = Files.readString(lockedAffixesPath);
				var root = GSON.fromJson(content, Map.class);
				if (root != null) {
					Object locked = root.get("lockedAffixes");
					if (locked != null) {
						Map<UUID, List<String>> loadedLocked = GSON.fromJson(GSON.toJson(locked),
								new TypeToken<Map<UUID, List<String>>>() {}.getType());
						if (loadedLocked != null) LOCKED_AFFIXES.putAll(loadedLocked);
					}
					Object pending = root.get("pendingMilestones");
					if (pending != null) {
						Map<UUID, List<Integer>> loadedPending = GSON.fromJson(GSON.toJson(pending),
								new TypeToken<Map<UUID, List<Integer>>>() {}.getType());
						if (loadedPending != null) PENDING_LOCK_MILESTONES.putAll(loadedPending);
					}
				}
			}
		} catch (Exception e) {
			com.randomsurprise.RandomSurpriseMod.LOGGER.warn("加载锁定词条数据失败: {}", e.getMessage());
		}
	}

	/** v19: 保存锁定词条数据 */
	private static void saveLockedAffixes() {
		if (lockedAffixesPath == null) return;
		try {
			Files.createDirectories(lockedAffixesPath.getParent());
			Map<String, Object> root = new java.util.LinkedHashMap<>();
			root.put("lockedAffixes", LOCKED_AFFIXES);
			root.put("pendingMilestones", PENDING_LOCK_MILESTONES);
			Files.writeString(lockedAffixesPath, GSON.toJson(root));
		} catch (Exception e) {
			com.randomsurprise.RandomSurpriseMod.LOGGER.warn("保存锁定词条数据失败: {}", e.getMessage());
		}
	}

	/** v19: 检查词条是否被锁定（不可移除） */
	public static boolean isAffixLocked(UUID playerId, String affixId) {
		List<String> locked = LOCKED_AFFIXES.get(playerId);
		return locked != null && locked.contains(affixId);
	}

	/** v19: 获取玩家抽奖次数 */
	public static int getDrawCount(UUID playerId) {
		return DRAW_COUNTS.getOrDefault(playerId, 0);
	}

	/** 添加词条到玩家 */
	public static void addAffix(UUID playerId, String affixId) {
		PLAYER_AFFIXES.computeIfAbsent(playerId, k -> new ArrayList<>()).add(affixId);

		// v19: 抽奖次数计数
		int newCount = DRAW_COUNTS.getOrDefault(playerId, 0) + 1;
		DRAW_COUNTS.put(playerId, newCount);

		// v19: 每累计50次抽取后的第一个敌对词条不可移除
		Affix affix = AffixRegistry.getById(affixId);
		if (affix != null && !affix.isGood()) {
			List<Integer> pending = PENDING_LOCK_MILESTONES.computeIfAbsent(playerId, k -> new ArrayList<>());
			if (!pending.isEmpty()) {
				// 有待锁定的里程碑 → 锁定此敌对词条
				LOCKED_AFFIXES.computeIfAbsent(playerId, k -> new ArrayList<>()).add(affixId);
				pending.remove(0);
				com.randomsurprise.RandomSurpriseMod.LOGGER.info(
						"玩家 {} 的敌对词条 {} 已锁定（不可移除），触发里程碑: 50次抽取",
						playerId, affixId);
			}
		}

		// v19: 达到50的倍数时记录待锁定里程碑
		if (newCount % 50 == 0 && newCount >= 50) {
			PENDING_LOCK_MILESTONES.computeIfAbsent(playerId, k -> new ArrayList<>()).add(newCount);
			com.randomsurprise.RandomSurpriseMod.LOGGER.info(
					"玩家 {} 累计抽取 {} 次，下一个敌对词条将不可移除", playerId, newCount);
		}

		save();
	}

	/** 获取玩家所有词条 */
	public static List<Affix> getAffixes(UUID playerId) {
		List<String> ids = PLAYER_AFFIXES.getOrDefault(playerId, Collections.emptyList());
		List<Affix> result = new ArrayList<>();
		for (String id : ids) {
			Affix affix = AffixRegistry.getById(id);
			if (affix != null) result.add(affix);
		}
		return result;
	}

	/** 玩家是否有指定词条 */
	public static boolean hasAffix(UUID playerId, String affixId) {
		return PLAYER_AFFIXES.getOrDefault(playerId, Collections.emptyList()).contains(affixId);
	}

	/** 玩家退出时移除内存中的冷却数据（词条数据保留以便重连恢复） */
	public static void onPlayerQuit(UUID playerId) {
		SUMMON_COOLDOWNS.remove(playerId);
		ABSORPTION_COOLDOWNS.remove(playerId);
		HASTE_LEVELS.remove(playerId);
		ABSORPTION_LEVELS.remove(playerId);
		// v13: 清理机制型状态数据（仅内存，非持久化）
		PAIN_CYCLE_STACKS.remove(playerId);
		SOUL_SIPHON_STACKS.remove(playerId);
		GUARD_KILL_SHIELD_TICKS.remove(playerId);
	}

	/** v14: 获取玩家急迫等级（供 BreakSpeed 事件使用） */
	public static int getHasteLevel(UUID playerId) {
		return HASTE_LEVELS.getOrDefault(playerId, 0);
	}

	/** v13: 检查任意玩家是否拥有指定词条（用于坏词条全局机制触发） */
	public static boolean isAnyPlayerHasAffix(String affixId) {
		for (var entry : PLAYER_AFFIXES.entrySet()) {
			// v19: 离线玩家的词条不起作用
			if (currentServer != null && currentServer.getPlayerList().getPlayer(entry.getKey()) == null) continue;
			if (entry.getValue().contains(affixId)) return true;
		}
		return false;
	}

	/** 查询某个好词条是否已被任何玩家获得（好词条独占机制） */
	public static boolean isGoodAffixTaken(String affixId) {
		return ASSIGNED_GOOD_AFFIXES.contains(affixId);
	}

	/** 从所有玩家词条数据重建全局已分配好词条集合（服务器启动/清空时调用） */
	private static void rebuildAssignedGoodAffixes() {
		ASSIGNED_GOOD_AFFIXES.clear();
		for (List<String> ids : PLAYER_AFFIXES.values()) {
			for (String id : ids) {
				Affix affix = AffixRegistry.getById(id);
				if (affix != null && affix.isGood()) {
					ASSIGNED_GOOD_AFFIXES.add(id);
				}
			}
		}
	}

	/**
	 * 清空玩家所有词条（征召战场代价重置用）
	 * @return 被清除的词条数量
	 */
	public static int clearAllAffixes(UUID playerId) {
		List<String> removed = PLAYER_AFFIXES.remove(playerId);
		rebuildAssignedGoodAffixes();  // 重建全局已分配集合（可能有好词条被释放）
		save();
		return removed == null ? 0 : removed.size();
	}

	/**
	 * 清空玩家所有坏词条（世界平静终局用）
	 * @return 被清除的坏词条数量
	 */
	public static int clearAllBadAffixes(UUID playerId) {
		List<String> ids = PLAYER_AFFIXES.get(playerId);
		if (ids == null || ids.isEmpty()) return 0;
		int before = ids.size();
		ids.removeIf(id -> {
			Affix a = AffixRegistry.getById(id);
			if (a == null || a.isGood()) return false;
			// v19: 保留锁定的词条
			return !isAffixLocked(playerId, id);
		});
		int removed = before - ids.size();
		if (removed > 0) save();
		return removed;
	}

	/** 为玩家随机添加一个好词条（征召战场奖励用），避免同一玩家重复获得 */
	public static boolean addRandomGoodAffix(UUID playerId, com.randomsurprise.affix.AffixRarity rarity) {
		List<String> ids = PLAYER_AFFIXES.computeIfAbsent(playerId, k -> new ArrayList<>());
		// 收集该稀有度所有好词条，仅过滤玩家已拥有的（不再全局独占）
		List<Affix> candidates = new ArrayList<>();
		for (Affix a : AffixRegistry.getByRarity(true, rarity)) {
			if (!ids.contains(a.getId())) candidates.add(a);
		}
		if (candidates.isEmpty()) return false;  // 该稀有度好词条已集齐
		Affix pick = candidates.get(new Random().nextInt(candidates.size()));
		ids.add(pick.getId());
		save();
		return true;
	}

	/**
	 * 随机移除玩家持有的一个敌对词条
	 * @return 被移除的词条，若无则 null
	 */
	public static Affix removeRandomBadAffix(UUID playerId) {
		List<String> ids = PLAYER_AFFIXES.get(playerId);
		if (ids == null || ids.isEmpty()) return null;
		// 收集所有可移除的敌对词条索引（跳过锁定的）
		List<Integer> badIndices = new ArrayList<>();
		for (int i = 0; i < ids.size(); i++) {
			Affix a = AffixRegistry.getById(ids.get(i));
			if (a != null && !a.isGood()) {
				// v19: 跳过锁定的词条
				if (isAffixLocked(playerId, ids.get(i))) continue;
				badIndices.add(i);
			}
		}
		if (badIndices.isEmpty()) return null;
		// 随机选一个
		int pickIdx = new Random().nextInt(badIndices.size());
		int targetIdx = badIndices.get(pickIdx);
		String removedId = ids.remove(targetIdx);
		save();
		return AffixRegistry.getById(removedId);
	}

	// ========== 好词条效果应用（定期刷新药水效果）==========

	/**
	 * 应用好词条效果（每5秒调用一次）
	 * v14: 全面改用 AttributeModifier，不再使用任何原版药水效果
	 * - 速度 → MOVEMENT_SPEED AttributeModifier（MULTIPLY_TOTAL）
	 * - 力量/武器伤害 → ATTACK_DAMAGE AttributeModifier（ADDITION）
	 * - 生命恢复 → hpPerSecond 每秒回血（onServerTick中执行）
	 * - 护盾 → MAX_ABSORPTION AttributeModifier
	 * - 急迫 → DIG_SPEED AttributeModifier（MULTIPLY_TOTAL）
	 * - 火焰/溺水/摔落免疫 → 在 LivingHurtEvent 中取消对应伤害
	 * - 最大生命加成 → MAX_HEALTH AttributeModifier
	 */
	public static void applyGoodEffects(ServerPlayer player) {
		boolean disabled = com.randomsurprise.battlefield.BattlefieldManager.isGoodAffixDisabled();
		List<Affix> affixes = getAffixes(player.getUUID());
		int healthBonus = 0;
		int speedAmplifier = 0;       // 旧字段，转换为 moveSpeedPercent
		int strengthAmplifier = 0;   // 旧字段，转换为 baseAttackDamage
		int weaponDamageBonus = 0;   // 旧字段，转换为 baseAttackDamage
		int absorptionBonus = 0;
		boolean fireImmunity = false;
		double fireResistancePercent = 0.0;
		boolean drownImmunity = false;
		boolean fallImmunity = false;
		int hasteAmplifier = 0;       // 旧字段，转换为 DIG_SPEED AttributeModifier
		int regenAmplifier = 0;       // 旧字段，转换为 hpPerSecond
		double attackSpeedBonus = 0;
		double moveSpeedBonus = 0;
		double moveSpeedPercent = 0;
		double baseAttackDamage = 0;
		double hpPerSecond = 0;

		if (!disabled) {
			for (Affix affix : affixes) {
				if (!affix.isGood()) continue;
				speedAmplifier = Math.max(speedAmplifier, affix.getSpeedAmplifier());
				strengthAmplifier = Math.max(strengthAmplifier, affix.getStrengthAmplifier());
				healthBonus += affix.getHealthBonus();
				absorptionBonus = Math.max(absorptionBonus, affix.getAbsorptionBonus());
				weaponDamageBonus = Math.max(weaponDamageBonus, affix.getWeaponDamageBonus());
				fireImmunity |= affix.isFireImmunity();
				fireResistancePercent = Math.max(fireResistancePercent, affix.getFireResistancePercent());
				drownImmunity |= affix.isDrownImmunity();
				fallImmunity |= affix.isFallImmunity();
				hasteAmplifier = Math.max(hasteAmplifier, affix.getHasteAmplifier());
				regenAmplifier = Math.max(regenAmplifier, affix.getRegenAmplifier());
				attackSpeedBonus = Math.max(attackSpeedBonus, affix.getAttackSpeedBonus());
				moveSpeedBonus = Math.max(moveSpeedBonus, affix.getMoveSpeedBonus());
				moveSpeedPercent += affix.getMoveSpeedPercent();
				baseAttackDamage += affix.getBaseAttackDamage();
				hpPerSecond += affix.getHpPerSecond();
			}
		}

		// v14: 将旧药水等级转换为 AttributeModifier 值
		// 速度药水等级 → 移速百分比（每级+20%）
		moveSpeedPercent += speedAmplifier * 0.2;
		// 力量药水等级 → 基础攻击力（每级+3伤害）
		baseAttackDamage += strengthAmplifier * 3.0;
		// 武器伤害等级 → 基础攻击力（每级+2伤害）
		baseAttackDamage += weaponDamageBonus * 2.0;
		// 生命恢复药水等级 → 每秒回血（每级+0.5 HP/s）
		hpPerSecond += regenAmplifier * 0.5;
		// 生命加成也提供微量回血（每5点生命加成+0.1 HP/s）
		hpPerSecond += healthBonus * 0.02;

		// v16: 属性封顶，防止叠加后过于强大
		healthBonus = BalanceMath.capHealthBonus(healthBonus);
		hpPerSecond = BalanceMath.capHpPerSecond(hpPerSecond);
		moveSpeedPercent = BalanceMath.capMoveSpeedPercent(moveSpeedPercent);
		baseAttackDamage = BalanceMath.capBaseAttackDamage(baseAttackDamage);

		// 护盾冷却检查
		long now = System.currentTimeMillis();
		Long lastAbsorption = ABSORPTION_COOLDOWNS.get(player.getUUID());
		int maxCooldown = 0;
		for (Affix affix : affixes) {
			if (affix.isGood()) {
				maxCooldown = Math.max(maxCooldown, affix.getAbsorptionCooldownSeconds());
			}
		}
		boolean canApplyAbsorption = (lastAbsorption == null || now - lastAbsorption >= maxCooldown * 1000L);
		if (canApplyAbsorption && absorptionBonus > 0) {
			ABSORPTION_COOLDOWNS.put(player.getUUID(), now);
		} else if (!canApplyAbsorption) {
			absorptionBonus = 0;
		}

		// 1. 最大生命加成
		try {
			var healthAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
			if (healthAttr != null) {
				healthAttr.removeModifier(HEALTH_BONUS_ID);
				if (healthBonus > 0) {
					healthAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(HEALTH_BONUS_ID, "health_bonus", (double) healthBonus, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
				}
			}
		} catch (Throwable t) {
			com.randomsurprise.RandomSurpriseMod.LOGGER.warn("应用生命加成失败: {}", t.getMessage());
		}

		// 2. 攻击速度加成
		try {
			var atkSpeedAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
			if (atkSpeedAttr != null) {
				atkSpeedAttr.removeModifier(ATTACK_SPEED_BONUS_ID);
				if (attackSpeedBonus > 0) {
					atkSpeedAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
							ATTACK_SPEED_BONUS_ID, "affix_attack_speed_bonus",
							attackSpeedBonus, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
				}
			}
		} catch (Throwable t) {
			com.randomsurprise.RandomSurpriseMod.LOGGER.warn("应用攻击速度加成失败: {}", t.getMessage());
		}

		// 3. 移动速度加成（固定值）
		try {
			var moveSpeedAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
			if (moveSpeedAttr != null) {
				moveSpeedAttr.removeModifier(MOVE_SPEED_BONUS_ID);
				if (moveSpeedBonus > 0) {
					moveSpeedAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
							MOVE_SPEED_BONUS_ID, "affix_move_speed_bonus",
							moveSpeedBonus, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
				}
			}
		} catch (Throwable t) {
			com.randomsurprise.RandomSurpriseMod.LOGGER.warn("应用移动速度加成失败: {}", t.getMessage());
		}

		// 4. 移速百分比加成（上限0.07，含旧速度药水转换）
		try {
			var moveSpeedAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
			if (moveSpeedAttr != null) {
				moveSpeedAttr.removeModifier(MOVE_SPEED_PERCENT_ID);
				moveSpeedPercent = Math.min(moveSpeedPercent, com.randomsurprise.config.BalanceConfig.MAX_MOVE_SPEED_PERCENT);
				if (moveSpeedPercent > 0) {
					moveSpeedAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
							MOVE_SPEED_PERCENT_ID, "affix_move_speed_percent",
							moveSpeedPercent, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
				}
			}
		} catch (Throwable t) {
			com.randomsurprise.RandomSurpriseMod.LOGGER.warn("应用移速百分比加成失败: {}", t.getMessage());
		}

		// 5. 基础攻击力加成（含旧力量/武器伤害药水转换，上限20）
		try {
			var attackAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
			if (attackAttr != null) {
				attackAttr.removeModifier(BASE_ATTACK_DAMAGE_ID);
				baseAttackDamage = Math.min(baseAttackDamage, 20.0);
				if (baseAttackDamage > 0) {
					attackAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
							BASE_ATTACK_DAMAGE_ID, "affix_base_attack_damage",
							baseAttackDamage, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
				}
			}
		} catch (Throwable t) {
			com.randomsurprise.RandomSurpriseMod.LOGGER.warn("应用基础攻击力加成失败: {}", t.getMessage());
		}

		// 6. v14: 挖掘速度加成（替代急迫药水，1.20.1无DIG_SPEED属性，用BreakSpeed事件处理）
		// 存储到 HASTE_LEVELS 供 PlayerEvent.BreakSpeed 使用
		if (hasteAmplifier > 0) {
			HASTE_LEVELS.put(player.getUUID(), hasteAmplifier);
		} else {
			HASTE_LEVELS.remove(player.getUUID());
		}

		// 7. v14: 吸收护盾加成（替代吸收药水，1.20.1无MAX_ABSORPTION属性，手动管理）
		// 存储到 ABSORPTION_LEVELS 供 onServerTick 使用
		if (absorptionBonus > 0) {
			ABSORPTION_LEVELS.put(player.getUUID(), absorptionBonus * 2.0F);
		} else {
			ABSORPTION_LEVELS.remove(player.getUUID());
		}

		// v14: 火焰/溺水/摔落免疫改为在 LivingHurtEvent 中取消伤害（不再用药水）
		// hpPerSecond 回血已在 onServerTick 中处理
	}

	/**
	 * 获取玩家总吸血百分比（v2 新增）
	 * 用于攻击事件中按伤害回血
	 */
	public static double getTotalLifestealPercent(UUID playerId) {
		double total = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) total += affix.getLifestealPercent();
		}
		// v16: 吸血封顶50%，防止超过100%导致无敌
		return BalanceMath.capLifesteal(total);
	}

	/**
	 * v12: 获取玩家攻击附带抑制回血百分比（取最大值）
	 * @return 0~100 的整数百分比
	 */
	public static int getTotalAntiHealOnHitPercent(UUID playerId) {
		int max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) max = Math.max(max, affix.getAntiHealOnHitPercent());
		}
		return max;
	}

	/**
	 * v12: 获取玩家狂战士伤害加成
	 * 检查玩家当前血量是否低于词条设定的阈值，若低于则返回伤害加成倍率
	 * @param playerId 玩家UUID
	 * @param currentHealth 玩家当前血量
	 * @param maxHealth 玩家最大血量
	 * @return 伤害加成倍率（0=不触发，0.50=+50%伤害）
	 */
	public static double getBerserkerDamageBonus(UUID playerId, float currentHealth, float maxHealth) {
		if (maxHealth <= 0) return 0;
		double healthPercent = (currentHealth / maxHealth) * 100.0;
		double maxBonus = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood() && affix.getBerserkerThreshold() > 0) {
				if (healthPercent < affix.getBerserkerThreshold()) {
					maxBonus = Math.max(maxBonus, affix.getBerserkerDamageBonus());
				}
			}
		}
		return maxBonus;
	}

	/**
	 * 获取玩家伤害免疫率（抗性词条替换，最多90%）
	 * 每点 resistanceAmplifier = 10% 伤害免疫，累加计算
	 */
	public static double getTotalDamageImmunity(UUID playerId) {
		int resistanceSum = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) resistanceSum += affix.getResistanceAmplifier();
		}
		return Math.min(resistanceSum * 0.10, 0.90);
	}

	/**
	 * 获取全服敌对生物伤害免疫率（最多60%）
	 * armorLevel 每级5% + armorPercent 百分比 + effectAmplifier 高等级5%
	 */
	public static double getTotalHostileDamageImmunity() {
		HostileBoost boost = calculateGlobalHostileBoost();
		double immunity = boost.armorLevel() * 0.05 + boost.armorPercent();
		if (boost.effectAmplifier() >= 2) {
			immunity += (boost.effectAmplifier() - 1) * 0.05;
		}
		return Math.min(immunity, 0.60);
	}

	/**
	 * 获取玩家火焰伤害减免百分比（v5 新增，非金词条）
	 * 取所有好词条中 fireResistancePercent 的最大值，最高100%
	 */
	public static double getTotalFireResistance(UUID playerId) {
		double max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) max = Math.max(max, affix.getFireResistancePercent());
		}
		return Math.min(max, 1.0);
	}

	/**
	 * 获取全服敌对生物火焰伤害减免百分比（v5 新增，非金词条）
	 */
	public static double getTotalHostileFireResistance() {
		double max = 0;
		for (var entry : PLAYER_AFFIXES.entrySet()) {
			// v19: 离线玩家的敌对词条不起作用
			if (currentServer != null && currentServer.getPlayerList().getPlayer(entry.getKey()) == null) continue;
			List<String> affixIds = entry.getValue();
			for (String id : affixIds) {
				Affix affix = AffixRegistry.getById(id);
				if (affix != null && !affix.isGood()) {
					max = Math.max(max, affix.getHostileFireResistancePercent());
				}
			}
		}
		return Math.min(max, 1.0);
	}

	/**
	 * 获取玩家对Boss的伤害加成百分比（v3 新增）
	 * 取所有好词条中 bossDamageBonus 的最大值
	 */
	public static double getTotalBossDamageBonus(UUID playerId) {
		double max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) max = Math.max(max, affix.getBossDamageBonus());
		}
		return max;
	}

	/**
	 * 获取玩家闪电伤害值（v4 新增）
	 * 取所有好词条中 lightningDamage 的最大值
	 */
	public static double getTotalLightningDamage(UUID playerId) {
		double max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) max = Math.max(max, affix.getLightningDamage());
		}
		return max;
	}

	// ========== v6 元素伤害体系统一计算方法 ==========

	/** 获取玩家火焰伤害（取最大值） */
	public static double getTotalFireDamage(UUID playerId) {
		double max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) max = Math.max(max, affix.getFireDamage());
		}
		return max;
	}

	/** 获取玩家每秒回血量（累加） */
	public static double getTotalHpPerSecond(UUID playerId) {
		double total = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) total += affix.getHpPerSecond();
		}
		return total;
	}

	/** 获取玩家冰霜伤害（取最大值） */
	public static double getTotalFrostDamage(UUID playerId) {
		double max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) max = Math.max(max, affix.getFrostDamage());
		}
		return max;
	}

	/** 获取玩家攻击时点燃概率（取最大值） */
	public static double getFireChanceOnAttack(UUID playerId) {
		double max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) max = Math.max(max, affix.getFireChanceOnAttack());
		}
		return max;
	}

	/** 获取玩家攻击时减速概率（取最大值） */
	public static double getFrostChanceOnAttack(UUID playerId) {
		double max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) max = Math.max(max, affix.getFrostChanceOnAttack());
		}
		return max;
	}

	/** v10: 获取玩家火焰燃烧堆叠加成（取最大值） */
	public static double getTotalFireBurnStackBonus(UUID playerId) {
		double max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) max = Math.max(max, affix.getFireBurnStackBonus());
		}
		return max;
	}

	// ========== v11 元素百分比伤害计算方法 ==========

	/** v11: 获取玩家闪电百分比伤害（取最大值，0.05=5%最大生命值） */
	public static double getTotalLightningPercentDamage(UUID playerId) {
		double max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) max = Math.max(max, affix.getLightningPercentDamage());
		}
		return max;
	}

	/** v11: 获取玩家冰霜冻结概率（取最大值） */
	public static double getTotalFrostFreezeChance(UUID playerId) {
		double max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) max = Math.max(max, affix.getFrostFreezeChance());
		}
		return max;
	}

	/** v11: 获取玩家火焰每秒百分比伤害（取最大值，0.02=每秒2%最大生命值） */
	public static double getTotalFirePercentDamagePerSecond(UUID playerId) {
		double max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) max = Math.max(max, affix.getFirePercentDamagePerSecond());
		}
		return max;
	}

	/** v11: 火焰百分比持续伤害数据（目标UUID -> FireDotData） */
	private static final Map<UUID, FireDotData> FIRE_DOT_TRACKER = new ConcurrentHashMap<>();

	/** v11: 火焰持续伤害数据结构 */
	private static class FireDotData {
		UUID attackerId;
		double percentPerSecond;
		int remainingTicks;
		int tickCounter;
	}

	/** v11: 处理火焰百分比持续伤害 */
	private static void processFireDotDamage(MinecraftServer server) {
		if (FIRE_DOT_TRACKER.isEmpty()) return;
		var iterator = FIRE_DOT_TRACKER.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			UUID targetUuid = entry.getKey();
			FireDotData data = entry.getValue();
			data.remainingTicks--;
			data.tickCounter++;
			// 每20tick（1秒）造成一次伤害
			if (data.tickCounter >= 20) {
				data.tickCounter = 0;
				// 查找目标实体（遍历所有维度）
				net.minecraft.world.entity.Entity found = null;
				for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
					found = level.getEntity(targetUuid);
					if (found != null) break;
				}
				if (found instanceof net.minecraft.world.entity.LivingEntity living) {
					float maxHp = living.getMaxHealth();
					float dotDamage = (float) (maxHp * data.percentPerSecond);
					if (dotDamage > 0) {
						var attacker = server.getPlayerList().getPlayer(data.attackerId);
						if (attacker != null) {
							living.hurt(attacker.damageSources().onFire(), dotDamage);
						} else {
							living.hurt(living.damageSources().onFire(), dotDamage);
						}
					}
				}
			}
			if (data.remainingTicks <= 0) {
				iterator.remove();
			}
		}
	}

	/** v10: 获取火焰燃烧堆叠层数 */
	public static int getFireBurnStack(UUID attackerId, UUID targetId) {
		Map<UUID, Integer> targets = FIRE_BURN_STACKS.get(attackerId);
		return targets != null ? targets.getOrDefault(targetId, 0) : 0;
	}

	/** v10: 设置火焰燃烧堆叠层数 */
	public static void setFireBurnStack(UUID attackerId, UUID targetId, int stacks) {
		FIRE_BURN_STACKS.computeIfAbsent(attackerId, k -> new ConcurrentHashMap<>()).put(targetId, stacks);
	}

	/** v10: 移除火焰燃烧堆叠 */
	public static void removeFireBurnStack(UUID attackerId, UUID targetId) {
		Map<UUID, Integer> targets = FIRE_BURN_STACKS.get(attackerId);
		if (targets != null) {
			targets.remove(targetId);
			if (targets.isEmpty()) {
				FIRE_BURN_STACKS.remove(attackerId);
			}
		}
	}

	/** v10: 服务器Tick处理 */
	public static void onServerTick(MinecraftServer server) {
		long tick = server.getTickCount();

		// v11: 处理火焰百分比持续伤害（每tick检查）
		processFireDotDamage(server);

		// 每20tick(1秒)处理每秒回血 + 吸收护盾管理
		if (tick % 20 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				double hpPerSecond = getTotalHpPerSecond(player.getUUID());
				if (hpPerSecond > 0 && player.getHealth() < player.getMaxHealth()) {
					player.heal((float) hpPerSecond);
				}
				// v14: 吸收护盾手动管理（替代吸收药水）
				Float absorptionAmount = ABSORPTION_LEVELS.get(player.getUUID());
				if (absorptionAmount != null && absorptionAmount > 0) {
					float current = player.getAbsorptionAmount();
					if (current < absorptionAmount) {
						player.setAbsorptionAmount(absorptionAmount);
					}
				} else if (absorptionAmount == null) {
					// 词条不再提供护盾时，如果玩家还有残留的护盾，清除它
					if (player.getAbsorptionAmount() > 0 && player.getPersistentData().getBoolean("randomsurprise.absorption_active")) {
						player.setAbsorptionAmount(0);
						player.getPersistentData().remove("randomsurprise.absorption_active");
					}
				}
				if (absorptionAmount != null && absorptionAmount > 0) {
					player.getPersistentData().putBoolean("randomsurprise.absorption_active", true);
				}
			}

			// v15: 傀儡每秒回血（好词条 hpPerSecond）
			if (!GOLEM_REGEN.isEmpty()) {
				for (net.minecraft.server.level.ServerLevel serverLevel : server.getAllLevels()) {
					for (net.minecraft.world.entity.Entity entity : serverLevel.getAllEntities()) {
						if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) continue;
						if (!living.isAlive()) continue;
						Float regenAmount = GOLEM_REGEN.get(living.getUUID());
						if (regenAmount != null && regenAmount > 0 && living.getHealth() < living.getMaxHealth()) {
							living.heal(regenAmount);
						}
					}
				}
			}
		}

		// v15: 每100tick(5秒)应用傀儡词条效果（好词条 + 坏词条）
		if (tick % 100 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				applyAffixesToGolems(player);
			}
		}

		// v13: 守卫击杀护盾tick递减
		if (!GUARD_KILL_SHIELD_TICKS.isEmpty()) {
			var iterator = GUARD_KILL_SHIELD_TICKS.entrySet().iterator();
			while (iterator.hasNext()) {
				var entry = iterator.next();
				int remaining = entry.getValue() - 1;
				if (remaining <= 0) {
					iterator.remove();
				} else {
					entry.setValue(remaining);
				}
			}
		}

		// v13: 净化光环 - 每10秒（200 tick）清除5格内所有玩家1个负面效果
		if (tick % 200 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (!hasAffix(player.getUUID(), "good_purify_aura_legendary")) continue;
				try {
					for (ServerPlayer nearby : player.level().getEntitiesOfClass(
							ServerPlayer.class, player.getBoundingBox().inflate(5.0))) {
						removeOneNegativeEffect(nearby);
					}
				} catch (Throwable t) {}
			}
		}

		// 每60tick(3秒)处理火焰堆叠衰减
		if (tick % FIRE_STACK_DECAY_TICKS != 0) return;

		List<UUID> emptyAttackers = new ArrayList<>();
		for (Map.Entry<UUID, Map<UUID, Integer>> entry : FIRE_BURN_STACKS.entrySet()) {
			UUID attackerId = entry.getKey();
			Map<UUID, Integer> targets = entry.getValue();
			List<UUID> zeroStackTargets = new ArrayList<>();
			for (Map.Entry<UUID, Integer> targetEntry : targets.entrySet()) {
				int newStack = targetEntry.getValue() - 1;
				if (newStack <= 0) {
					zeroStackTargets.add(targetEntry.getKey());
				} else {
					targetEntry.setValue(newStack);
				}
			}
			zeroStackTargets.forEach(targets::remove);
			if (targets.isEmpty()) {
				emptyAttackers.add(attackerId);
			}
		}
		emptyAttackers.forEach(FIRE_BURN_STACKS::remove);
	}

	/** 获取玩家冰霜伤害减免（取最大值，上限90%） */
	public static double getTotalFrostResistance(UUID playerId) {
		double max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) max = Math.max(max, affix.getFrostResistancePercent());
		}
		return Math.min(max, 0.90);
	}

	/** 获取玩家闪电伤害减免（取最大值，上限90%） */
	public static double getTotalLightningResistance(UUID playerId) {
		double max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) max = Math.max(max, affix.getLightningResistancePercent());
		}
		return Math.min(max, 0.90);
	}

	/** 玩家是否拥有冰霜免疫（免疫减速） */
	public static boolean hasFrostImmunity(UUID playerId) {
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood() && affix.isFrostImmunity()) return true;
		}
		return false;
	}

	// ========== v7 新增属性 getter ==========

	/** 获取玩家挖掘速度加成（取最大值，用于 PlayerEvent.BreakSpeed） */
	public static double getTotalMiningSpeedBonus(UUID playerId) {
		double max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) max = Math.max(max, affix.getMiningSpeedBonus());
		}
		return max;
	}

	/** 获取玩家攻击速度加成（取最大值，用于 AttributeModifier） */
	public static double getTotalAttackSpeedBonus(UUID playerId) {
		double max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) max = Math.max(max, affix.getAttackSpeedBonus());
		}
		return max;
	}

	/** 获取玩家移动速度加成（取最大值，用于 AttributeModifier，区别于速度药水） */
	public static double getTotalMoveSpeedBonus(UUID playerId) {
		double max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) max = Math.max(max, affix.getMoveSpeedBonus());
		}
		return max;
	}

	/** 获取玩家暴击概率（取最大值，触发时造成 2 倍伤害） */
	public static double getTotalCritChance(UUID playerId) {
		double max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) max = Math.max(max, affix.getCritChance());
		}
		return max;
	}

	/** 获取玩家治疗加成（取最大值，用于 LivingHealEvent） */
	public static double getTotalHealBonus(UUID playerId) {
		double max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) max = Math.max(max, affix.getHealBonus());
		}
		return max;
	}

	/** 获取全服敌对生物冰霜伤害减免百分比（取最大值，上限 1.0） */
	public static double getTotalHostileFrostResistance() {
		double max = 0;
		for (var entry : PLAYER_AFFIXES.entrySet()) {
			if (currentServer != null && currentServer.getPlayerList().getPlayer(entry.getKey()) == null) continue;
			List<String> affixIds = entry.getValue();
			for (String id : affixIds) {
				Affix affix = AffixRegistry.getById(id);
				if (affix != null && !affix.isGood()) {
					max = Math.max(max, affix.getHostileFrostResistancePercent());
				}
			}
		}
		return Math.min(max, 1.0);
	}

	/** 获取全服敌对生物闪电伤害减免百分比（取最大值，上限 1.0） */
	public static double getTotalHostileLightningResistance() {
		double max = 0;
		for (var entry : PLAYER_AFFIXES.entrySet()) {
			if (currentServer != null && currentServer.getPlayerList().getPlayer(entry.getKey()) == null) continue;
			List<String> affixIds = entry.getValue();
			for (String id : affixIds) {
				Affix affix = AffixRegistry.getById(id);
				if (affix != null && !affix.isGood()) {
					max = Math.max(max, affix.getHostileLightningResistancePercent());
				}
			}
		}
		return Math.min(max, 1.0);
	}

	/** 获取玩家失明免疫率（取最大值，上限 1.0） */
	public static double getTotalBlindnessResistance(UUID playerId) {
		double max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) max = Math.max(max, affix.getBlindnessResistancePercent());
		}
		return Math.min(max, 1.0);
	}

	/**
	 * v7+v14: 玩家挖掘速度事件处理（PlayerEvent.BreakSpeed）
	 * v7: 将挖掘速度加成应用到方块破坏速度
	 * v14: 合并急迫等级（替代急迫药水）
	 */
	public static void onBreakSpeed(net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed event) {
		var entity = event.getEntity();
		if (!(entity instanceof ServerPlayer player)) return;
		// 入侵态禁用好词条效果
		if (com.randomsurprise.battlefield.BattlefieldManager.isGoodAffixDisabled()) return;
		float speedMultiplier = 1.0F;
		// v7: 挖掘速度加成
		double bonus = getTotalMiningSpeedBonus(player.getUUID());
		if (bonus > 0) {
			speedMultiplier += (float) bonus;
		}
		// v14: 急迫等级（替代急迫药水，每级+30%）
		int hasteLvl = getHasteLevel(player.getUUID());
		if (hasteLvl > 0) {
			speedMultiplier += hasteLvl * 0.3F;
		}
		if (speedMultiplier > 1.0F) {
			event.setNewSpeed(event.getNewSpeed() * speedMultiplier);
		}
	}

	/**
	 * v7: 玩家治疗加成事件处理（LivingHealEvent）
	 * 将治疗加成应用到所有治疗量
	 */
	public static void onLivingHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
		var entity = event.getEntity();
		// 玩家治疗加成
		if (entity instanceof ServerPlayer player) {
			// 入侵态禁用好词条效果
			if (com.randomsurprise.battlefield.BattlefieldManager.isGoodAffixDisabled()) return;
			double bonus = getTotalHealBonus(player.getUUID());
			if (bonus > 0) {
				float newAmount = event.getAmount() * (float) (1.0 + bonus);
				event.setAmount(newAmount);
			}
			return;
		}
		// v12: 怪物回血减少（坏词条全局抑制回血 + 好词条攻击附带抑制回血）
		if (entity instanceof net.minecraft.world.entity.Mob mob) {
			float reductionMultiplier = 1.0F;
			// 坏词条全局抑制回血（取最大值，避免100%抑制）
			HostileBoost boost = calculateGlobalHostileBoost();
			if (boost.healReductionPercent() > 0) {
				reductionMultiplier *= (1.0F - boost.healReductionPercent() / 100.0F);
			}
			// 好词条攻击附带抑制回血（3秒内有效）
			long now = System.currentTimeMillis();
			Long expiry = ANTI_HEAL_EXPIRY.get(mob.getUUID());
			if (expiry != null && now < expiry) {
				Integer percent = ANTI_HEAL_PERCENT.get(mob.getUUID());
				if (percent != null && percent > 0) {
					reductionMultiplier *= (1.0F - percent / 100.0F);
				}
			} else if (expiry != null) {
				// 过期则清理
				ANTI_HEAL_EXPIRY.remove(mob.getUUID());
				ANTI_HEAL_PERCENT.remove(mob.getUUID());
			}
			if (reductionMultiplier < 1.0F) {
				event.setAmount(event.getAmount() * reductionMultiplier);
			}
		}
	}

	/**
	 * v13: 玩家受击事件处理（LivingHurtEvent）
	 * 处理好词条：
	 * - good_vengeance_rare: 反弹30%伤害给攻击者
	 * - good_guard_kill_rare: 减伤50%（击杀后3秒内）
	 */
	public static void onLivingHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
		var entity = event.getEntity();
		if (!(entity instanceof ServerPlayer player)) return;
		float amount = event.getAmount();
		var source = event.getSource();
		// v14: 火焰免疫（原为FIRE_RESISTANCE药水，现改为直接取消火焰伤害）
		if (hasFireImmunity(player.getUUID()) && source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
			event.setCanceled(true);
			return;
		}
		// v14: 溺水免疫（原为WATER_BREATHING药水，现改为直接取消溺水伤害）
		if (hasDrownImmunity(player.getUUID()) && source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)) {
			event.setCanceled(true);
			return;
		}
		// v14: 摔落免疫（原为SLOW_FALLING药水，现改为直接取消摔落伤害）
		if (hasFallImmunity(player.getUUID()) && source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
			event.setCanceled(true);
			return;
		}
		// good_vengeance_rare: 反弹30%伤害
		if (hasAffix(player.getUUID(), "good_vengeance_rare")) {
			var attacker = source.getEntity();
			if (attacker instanceof net.minecraft.world.entity.LivingEntity livingAttacker
					&& !livingAttacker.level().isClientSide()) {
				float reflectAmount = amount * 0.3F;
				if (reflectAmount > 0) {
					livingAttacker.hurt(player.damageSources().thorns(livingAttacker), reflectAmount);
				}
			}
		}
		// good_guard_kill_rare: 减伤50%（击杀后3秒内）
		if (hasAffix(player.getUUID(), "good_guard_kill_rare")) {
			Integer shieldTicks = GUARD_KILL_SHIELD_TICKS.get(player.getUUID());
			if (shieldTicks != null && shieldTicks > 0) {
				event.setAmount(amount * 0.5F);
			}
		}
	}

	/** v14: 检查玩家是否拥有火焰免疫（来自好词条） */
	public static boolean hasFireImmunity(UUID playerId) {
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood() && affix.isFireImmunity()) return true;
		}
		return false;
	}

	/** v14: 检查玩家是否拥有溺水免疫（来自好词条） */
	public static boolean hasDrownImmunity(UUID playerId) {
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood() && affix.isDrownImmunity()) return true;
		}
		return false;
	}

	/** v14: 检查玩家是否拥有摔落免疫（来自好词条） */
	public static boolean hasFallImmunity(UUID playerId) {
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood() && affix.isFallImmunity()) return true;
		}
		return false;
	}

	/**
	 * v13: 生物死亡事件处理（LivingDeathEvent）
	 * 处理好词条：
	 * - good_death_detonate_epic: 在死亡实体位置3格内造成爆炸
	 * - good_guard_kill_rare: 击杀获得3秒减伤50%
	 * - good_potion_thirst_uncommon: 击杀清除自身1个负面效果
	 * - good_soul_siphon_legendary: 击杀获得1层灵魂（每层+2%暴击率，上限10层）
	 */
	public static void onLivingDeath(net.minecraft.world.entity.LivingEntity entity,
			net.minecraft.world.damagesource.DamageSource source) {
		var killer = source.getEntity();
		// v13: 玩家死亡时清除灵魂虹吸层数
		if (entity instanceof ServerPlayer deadPlayer) {
			SOUL_SIPHON_STACKS.remove(deadPlayer.getUUID());
			PAIN_CYCLE_STACKS.remove(deadPlayer.getUUID());
			GUARD_KILL_SHIELD_TICKS.remove(deadPlayer.getUUID());
		}
		if (!(killer instanceof ServerPlayer player)) return;
		if (!(entity instanceof net.minecraft.world.entity.Mob mob)) return;
		if (mob.level().isClientSide()) return;

		// good_death_detonate_epic: 爆炸
		if (hasAffix(player.getUUID(), "good_death_detonate_epic")) {
			float maxHp = mob.getMaxHealth();
			float explosionDamage = maxHp * 0.1F;
			if (explosionDamage > 0) {
				try {
					var level = mob.level();
					for (net.minecraft.world.entity.LivingEntity nearby : level.getEntitiesOfClass(
							net.minecraft.world.entity.LivingEntity.class,
							mob.getBoundingBox().inflate(3.0))) {
						if (nearby == mob || nearby == player) continue;
						if (nearby instanceof net.minecraft.world.entity.monster.Enemy
								|| isHostileTypeForDetonate(nearby)) {
							nearby.hurt(player.damageSources().mobAttack(player), explosionDamage);
						}
					}
					level.explode(null, mob.getX(), mob.getY(), mob.getZ(), 0.0F,
						net.minecraft.world.level.Level.ExplosionInteraction.NONE);
				} catch (Throwable t) {
					com.randomsurprise.RandomSurpriseMod.LOGGER.warn("死亡引爆异常: {}", t.getMessage());
				}
			}
		}
		// good_guard_kill_rare: 获得护盾（60 tick = 3秒）
		if (hasAffix(player.getUUID(), "good_guard_kill_rare")) {
			GUARD_KILL_SHIELD_TICKS.put(player.getUUID(), 60);
		}
		// good_potion_thirst_uncommon: 清除自身1个负面效果
		if (hasAffix(player.getUUID(), "good_potion_thirst_uncommon")) {
			removeOneNegativeEffect(player);
		}
		// good_soul_siphon_legendary: 获得灵魂层
		if (hasAffix(player.getUUID(), "good_soul_siphon_legendary")) {
			int stacks = SOUL_SIPHON_STACKS.getOrDefault(player.getUUID(), 0);
			stacks = Math.min(stacks + 1, MAX_SOUL_STACKS);
			SOUL_SIPHON_STACKS.put(player.getUUID(), stacks);
		}
	}

	/** 判断实体是否为可被引爆伤害的敌对类型 */
	private static boolean isHostileTypeForDetonate(net.minecraft.world.entity.LivingEntity entity) {
		if (entity instanceof net.minecraft.world.entity.Mob mob) {
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
		return false;
	}

	/** 清除玩家1个负面效果 */
	private static void removeOneNegativeEffect(ServerPlayer player) {
		net.minecraft.world.effect.MobEffect[] negativeEffects = {
				MobEffects.WEAKNESS, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SLOWDOWN,
				MobEffects.CONFUSION, MobEffects.BLINDNESS, MobEffects.HUNGER,
				MobEffects.POISON, MobEffects.WITHER, MobEffects.LEVITATION,
				MobEffects.UNLUCK
		};
		for (net.minecraft.world.effect.MobEffect effect : negativeEffects) {
			if (player.hasEffect(effect)) {
				player.removeEffect(effect);
				return;
			}
		}
	}

	/**
	 * 获取玩家武器类型伤害加成（v4 新增）
	 * 根据手持武器类型匹配词条中的 weaponTypeBonus
	 * weaponTypeBonus 为 "all" 时对所有武器类型生效
	 */
	public static double getTotalWeaponDamageBonus(UUID playerId, net.minecraft.world.item.Item heldItem) {
		if (heldItem == null) return 0;
		String weaponType = getWeaponType(heldItem);
		double max = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (!affix.isGood()) continue;
			String bonusType = affix.getWeaponTypeBonus();
			if (bonusType == null) continue;
			if ("all".equals(bonusType)) {
				max = Math.max(max, affix.getWeaponDamagePercent());
			} else if (weaponType != null && weaponType.equals(bonusType)) {
				max = Math.max(max, affix.getWeaponDamagePercent());
			}
		}
		return max;
	}

	/**
	 * 判断物品的武器类型
	 */
	private static String getWeaponType(net.minecraft.world.item.Item item) {
		if (item instanceof net.minecraft.world.item.SwordItem) return "sword";
		if (item instanceof net.minecraft.world.item.BowItem) return "bow";
		if (item instanceof net.minecraft.world.item.CrossbowItem) return "bow";
		if (item instanceof net.minecraft.world.item.AxeItem) return "axe";
		if (item instanceof net.minecraft.world.item.PickaxeItem) return "pickaxe";
		if (item instanceof net.minecraft.world.item.HoeItem) return "axe";
		if (item instanceof net.minecraft.world.item.TridentItem) return "trident";
		return null;
	}

	/**
	 * 尝试触发召唤友方生物（v2 新增）
	 * 在玩家受击时调用，按词条概率召唤
	 * 含冷却限制（10秒）和数量上限（最多3只同时存在）
	 * @return true 如果触发了召唤
	 */
	public static boolean trySummonAlly(ServerPlayer player) {
		// 冷却检查
		long now = System.currentTimeMillis();
		Long lastSummon = SUMMON_COOLDOWNS.get(player.getUUID());
		if (lastSummon != null && now - lastSummon < SUMMON_COOLDOWN_MS) return false;

		// 数量上限检查：统计玩家附近32格内的铁傀儡数量
		var level = player.level();
		long currentSummons = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
				player.getBoundingBox().inflate(32)).stream()
				.filter(g -> g.isAlive() && g.getType() == net.minecraft.world.entity.EntityType.IRON_GOLEM)
				.count();
		if (currentSummons >= MAX_PLAYER_SUMMONS) return false;

		List<Affix> affixes = getAffixes(player.getUUID());
		for (Affix affix : affixes) {
			if (!affix.isGood()) continue;
			if (affix.getSummonChance() <= 0 || affix.getSummonMobId() == null) continue;
			if (new Random().nextDouble() < affix.getSummonChance()) {
				// 解析召唤实体ID
				var typeOpt = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
						.getOptional(new net.minecraft.resources.ResourceLocation(affix.getSummonMobId()));
				if (typeOpt.isPresent()) {
					var entity = typeOpt.get().create(level);
					if (entity != null) {
						double dx = player.getX() + (new Random().nextDouble() - 0.5) * 4;
						double dz = player.getZ() + (new Random().nextDouble() - 0.5) * 4;
						entity.setPos(dx, player.getY(), dz);
						// 标记召唤者 UUID（仅铁傀儡），用于保护机制判定
						if (entity.getType() == net.minecraft.world.entity.EntityType.IRON_GOLEM) {
							bindGolemOwner(entity, player.getUUID());
						}
						level.addFreshEntity(entity);
						SUMMON_COOLDOWNS.put(player.getUUID(), now);
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * 绑定傀儡的召唤者 UUID（PersistentData 持久化，仅服务端使用）
	 * v15: 改为 public static，支持所有 LivingEntity 类型（包括 Modular Golems）
	 */
	public static void bindGolemOwner(net.minecraft.world.entity.Entity golem, UUID ownerUUID) {
		try {
			var data = golem.getPersistentData();
			data.putUUID(SUMMON_OWNER_KEY, ownerUUID);
		} catch (Throwable ignored) {}
	}

	/**
	 * 获取傀儡的召唤者 UUID（无标记则返回 null，表示非玩家召唤）
	 * v15: 不限制实体类型，检查 PersistentData（支持所有 LivingEntity）
	 */
	public static UUID getGolemOwnerUUID(net.minecraft.world.entity.LivingEntity entity) {
		if (entity == null) return null;
		try {
			var data = entity.getPersistentData();
			if (data.hasUUID(SUMMON_OWNER_KEY)) return data.getUUID(SUMMON_OWNER_KEY);
		} catch (Throwable ignored) {}
		return null;
	}

	/**
	 * 获取傀儡的召唤者 UUID（Entity 重载，委托给 LivingEntity 版本）
	 * v15: 保留旧接口，移除 IRON_GOLEM 类型限制
	 */
	public static UUID getGolemOwnerUUID(net.minecraft.world.entity.Entity golem) {
		if (golem == null) return null;
		if (golem instanceof net.minecraft.world.entity.LivingEntity living) {
			return getGolemOwnerUUID(living);
		}
		return null;
	}

	/**
	 * 判断傀儡是否由指定玩家召唤
	 * v15: 移除对 IronGolem 类型的隐式依赖（getGolemOwnerUUID 已扩展）
	 */
	public static boolean isPlayerSummonedGolem(net.minecraft.world.entity.Entity golem, net.minecraft.world.entity.player.Player player) {
		if (player == null) return false;
		UUID owner = getGolemOwnerUUID(golem);
		return owner != null && owner.equals(player.getUUID());
	}

	/**
	 * 强制傀儡立即切换目标反击攻击者（保护行为最高优先级）
	 * v15: 参数从 IronGolem 改为 Mob，支持所有傀儡类型
	 * 实现：清空当前目标后立即 setTarget 攻击者，触发原版 HurtByTargetGoal 接管攻击
	 */
	public static void forceGolemRetaliate(net.minecraft.world.entity.Mob golem, net.minecraft.world.entity.LivingEntity attacker) {
		if (golem == null || attacker == null || !golem.isAlive() || !attacker.isAlive()) return;
		// 不反击自己的召唤者
		if (attacker instanceof net.minecraft.world.entity.player.Player player
				&& isPlayerSummonedGolem(golem, player)) return;
		try {
			// 清空当前目标，强制 AI 重新选择，避免锁定旧目标
			golem.setTarget(null);
			golem.setTarget(attacker);
		} catch (Throwable ignored) {}
	}

	/**
	 * 战斗事件处理（v2 新增）
	 * 在实体受到伤害后调用，处理：
	 * - 玩家攻击敌对生物：玩家吸血（基于实际伤害）
	 * - 玩家受击：尝试召唤友方生物（含冷却和数量上限）
	 * - 敌对生物攻击玩家：敌对生物吸血（全局坏词条效果，含召唤冷却）
	 * @param appliedAmount 实际结算后的伤害值（已扣除护甲/抗性减免）
	 */
	public static void onEntityDamaged(net.minecraft.world.entity.LivingEntity entity,
			net.minecraft.world.damagesource.DamageSource source, float appliedAmount) {
		if (appliedAmount <= 0) return;
		var attacker = source.getEntity();
		// 玩家造成伤害 → 玩家吸血（基于实际伤害，不再有最低1点保底）
		if (attacker instanceof ServerPlayer player && entity instanceof net.minecraft.world.entity.Mob mob) {
			double lifesteal = getTotalLifestealPercent(player.getUUID());
			if (lifesteal > 0) {
				float heal = appliedAmount * (float) lifesteal;
				if (heal > 0) player.heal(heal);
			}
			// v12: 攻击附带抑制回血（好词条：被攻击目标3秒内回血减少指定百分比）
			int antiHealPercent = getTotalAntiHealOnHitPercent(player.getUUID());
			if (antiHealPercent > 0) {
				long now = System.currentTimeMillis();
				ANTI_HEAL_EXPIRY.put(mob.getUUID(), now + ANTI_HEAL_DURATION_MS);
				ANTI_HEAL_PERCENT.put(mob.getUUID(), antiHealPercent);
			}
			// v12: 狂战士（好词条：血量低于阈值时攻击力增加，用 ThreadLocal 防止递归）
			if (!IN_BOSS_BONUS.get()) {
				double berserkerBonus = getBerserkerDamageBonus(player.getUUID(),
						player.getHealth(), player.getMaxHealth());
				if (berserkerBonus > 0) {
					float bonusDamage = appliedAmount * (float) berserkerBonus;
					if (bonusDamage > 0) {
						IN_BOSS_BONUS.set(true);
						try {
							mob.hurt(source, bonusDamage);
						} finally {
							IN_BOSS_BONUS.set(false);
						}
					}
				}
			}
			// v4: 闪电伤害（攻击时有概率召唤闪电造成额外伤害）
			double lightningDmg = getTotalLightningDamage(player.getUUID());
			if (lightningDmg > 0 && new Random().nextDouble() < 0.3) {
				try {
					net.minecraft.world.entity.LightningBolt lightning = new net.minecraft.world.entity.LightningBolt(
							net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, player.level());
					lightning.setPos(mob.getX(), mob.getY(), mob.getZ());
					lightning.setVisualOnly(true); // 仅视觉，不点燃方块、不产生巨大雷声
					player.level().addFreshEntity(lightning);
					mob.hurt(player.damageSources().lightningBolt(), 5.0F + (float) lightningDmg);
					// 播放较小的电击音效
					player.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
							net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT,
							net.minecraft.sounds.SoundSource.PLAYERS, 0.3F, 1.5F);
				} catch (Throwable t) {}
				// v11: 闪电百分比伤害（对最大生命值的5%伤害）
				double lightningPctDmg = getTotalLightningPercentDamage(player.getUUID());
				if (lightningPctDmg > 0) {
					float pctDmg = mob.getMaxHealth() * (float) lightningPctDmg;
					IN_BOSS_BONUS.set(true);
					try {
						mob.hurt(player.damageSources().lightningBolt(), pctDmg);
					} finally {
						IN_BOSS_BONUS.set(false);
					}
				}
			}
			// v6/v10: 火焰伤害（攻击时按概率点燃目标并造成额外火焰伤害，支持燃烧堆叠）
			double fireDmg = getTotalFireDamage(player.getUUID());
			if (fireDmg > 0) {
				double fireChance = getFireChanceOnAttack(player.getUUID());
				if (fireChance > 0 && new Random().nextDouble() < fireChance) {
					double stackBonus = getTotalFireBurnStackBonus(player.getUUID());
					int currentStack = getFireBurnStack(player.getUUID(), mob.getUUID());
					currentStack++;
					currentStack = Math.min(currentStack, MAX_FIRE_STACKS);
					setFireBurnStack(player.getUUID(), mob.getUUID(), currentStack);
					double totalFireDmg = fireDmg + (stackBonus * (currentStack - 1));
					mob.hurt(player.damageSources().onFire(), (float) totalFireDmg);
					int fireTicks = 60 + (currentStack * 20);
					mob.setRemainingFireTicks(fireTicks);
				}
				// v11: 火焰每秒百分比伤害（每秒2%最大生命值，持续5秒）
				double firePctPerSec = getTotalFirePercentDamagePerSecond(player.getUUID());
				if (firePctPerSec > 0) {
					FireDotData dotData = new FireDotData();
					dotData.attackerId = player.getUUID();
					dotData.percentPerSecond = firePctPerSec;
					dotData.remainingTicks = 100; // 5秒 = 100 ticks
					dotData.tickCounter = 0;
					FIRE_DOT_TRACKER.put(mob.getUUID(), dotData);
				}
			}
			// v6: 冰霜伤害（攻击时按概率造成额外冰霜伤害并减速目标）
			double frostDmg = getTotalFrostDamage(player.getUUID());
			if (frostDmg > 0) {
				double frostChance = getFrostChanceOnAttack(player.getUUID());
				if (frostChance > 0 && new Random().nextDouble() < frostChance) {
					mob.hurt(player.damageSources().freeze(), (float) frostDmg);
					mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false, false));
				}
				// v11: 冰霜冻结概率（冻结目标，类似细雪效果）
				double frostFreezeChance = getTotalFrostFreezeChance(player.getUUID());
				if (frostFreezeChance > 0 && new Random().nextDouble() < frostFreezeChance) {
					// 设置冻结ticks（使目标受到冻结伤害）
					mob.setTicksFrozen(Math.min(mob.getTicksFrozen() + 140, mob.getTicksRequiredToFreeze() + 40));
					// 额外强减速效果
					mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2, false, true, true));
				}
			}
			// v4: 武器类型伤害加成（根据手持武器类型）
			double weaponBonus = getTotalWeaponDamageBonus(player.getUUID(), player.getMainHandItem().getItem());
			if (weaponBonus > 0) {
				float bonusDamage = appliedAmount * (float) weaponBonus;
				if (bonusDamage > 0) {
					IN_BOSS_BONUS.set(true);
					try {
						mob.hurt(source, bonusDamage);
					} finally {
						IN_BOSS_BONUS.set(false);
					}
				}
			}
			// v7: 暴击概率（取最大值，触发时造成 2 倍原始伤害，仅对原始攻击触发，防止递归）
			if (!IN_BOSS_BONUS.get()) {
				double critChance = getTotalCritChance(player.getUUID());
				// v13: 灵魂虹吸增加暴击率（每层+2%，上限10层=20%）
				if (hasAffix(player.getUUID(), "good_soul_siphon_legendary")) {
					int soulStacks = SOUL_SIPHON_STACKS.getOrDefault(player.getUUID(), 0);
					critChance += soulStacks * 0.02;
				}
				if (critChance > 0 && new Random().nextDouble() < critChance) {
					float critDamage = appliedAmount; // 额外造成 1 倍伤害 = 总计 2 倍
					IN_BOSS_BONUS.set(true);
					try {
						mob.hurt(source, critDamage);
						// 暴击视觉反馈：在目标位置播放暴击粒子
						player.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
								net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
								net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.0F);
					} finally {
						IN_BOSS_BONUS.set(false);
					}
				}
			}
			// v3: Boss 伤害加成（用 ThreadLocal 防止 mob.hurt 递归触发）
			if (!IN_BOSS_BONUS.get()) {
				double bossBonus = getTotalBossDamageBonus(player.getUUID());
				if (bossBonus > 0) {
					var loc = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
					if (loc != null && BossPool.isBoss(loc.toString())) {
						float bonusDamage = appliedAmount * (float) bossBonus;
						if (bonusDamage > 0) {
							IN_BOSS_BONUS.set(true);
							try {
								mob.hurt(source, bonusDamage);
							} finally {
								IN_BOSS_BONUS.set(false);
							}
						}
					}
				}
			}
			// v3: 敌对生物反伤（仅对原始攻击触发，不在 Boss 加成递归中触发）
			if (!IN_BOSS_BONUS.get()) {
				// v13: 痛楚循环 - 每次攻击消耗1HP，叠加1层，满5层时额外造成5倍伤害并清零
				if (hasAffix(player.getUUID(), "good_pain_cycle_epic")) {
					if (player.getHealth() > 2.0F) {
						player.hurt(player.damageSources().generic(), 2.0F);
					}
					int stacks = PAIN_CYCLE_STACKS.getOrDefault(player.getUUID(), 0) + 1;
					if (stacks >= 5) {
						float bonusDamage = appliedAmount * 5.0F;
						IN_BOSS_BONUS.set(true);
						try {
							mob.hurt(source, bonusDamage);
						} finally {
							IN_BOSS_BONUS.set(false);
						}
						PAIN_CYCLE_STACKS.put(player.getUUID(), 0);
					} else {
						PAIN_CYCLE_STACKS.put(player.getUUID(), stacks);
					}
				}
				// v13: 虚空侵蚀 - 命中目标叠加1层侵蚀，每层+5%伤害
				if (hasAffix(player.getUUID(), "good_void_erosion_legendary")) {
					long currentTick = mob.level().getGameTime();
					long[] erosionData = VOID_EROSION_STACKS.getOrDefault(mob.getUUID(), new long[]{0, 0});
					if (currentTick - erosionData[1] > VOID_EROSION_DURATION_TICKS) {
						erosionData[0] = 0; // 超过5秒重置
					}
					erosionData[0] = Math.min(erosionData[0] + 1, 10);
					erosionData[1] = currentTick;
					VOID_EROSION_STACKS.put(mob.getUUID(), erosionData);
					float erosionBonus = appliedAmount * 0.05F * erosionData[0];
					if (erosionBonus > 0) {
						IN_BOSS_BONUS.set(true);
						try {
							mob.hurt(source, erosionBonus);
						} finally {
							IN_BOSS_BONUS.set(false);
						}
					}
				}
				// v13: 震波 - 30%概率对目标身后5格直线范围敌人造成50%伤害
				if (hasAffix(player.getUUID(), "good_shockwave_rare") && new Random().nextDouble() < 0.3) {
					try {
						var level = mob.level();
						double dx = mob.getX() - player.getX();
						double dz = mob.getZ() - player.getZ();
						double len = Math.sqrt(dx * dx + dz * dz);
						if (len > 0.001) {
							dx /= len; dz /= len;
							for (net.minecraft.world.entity.LivingEntity nearby : level.getEntitiesOfClass(
									net.minecraft.world.entity.LivingEntity.class,
									mob.getBoundingBox().inflate(5.0))) {
								if (nearby == mob || nearby == player) continue;
								double nDx = nearby.getX() - mob.getX();
								double nDz = nearby.getZ() - mob.getZ();
								double nLen = Math.sqrt(nDx * nDx + nDz * nDz);
								if (nLen > 0.001) {
									nDx /= nLen; nDz /= nLen;
									if (nDx * dx + nDz * dz > 0.5) {
										nearby.hurt(player.damageSources().mobAttack(player), appliedAmount * 0.5F);
									}
								}
							}
						}
					} catch (Throwable t) {}
				}
				// v13: 旋风斩 - 30%概率对3格内所有敌对生物造成40%伤害
				if (hasAffix(player.getUUID(), "good_whirlwind_rare") && new Random().nextDouble() < 0.3) {
					try {
						var level = mob.level();
						for (net.minecraft.world.entity.LivingEntity nearby : level.getEntitiesOfClass(
								net.minecraft.world.entity.LivingEntity.class,
								mob.getBoundingBox().inflate(3.0))) {
							if (nearby == mob || nearby == player) continue;
							if (nearby instanceof net.minecraft.world.entity.monster.Enemy
									|| isHostileTypeForDetonate(nearby)) {
								nearby.hurt(player.damageSources().mobAttack(player), appliedAmount * 0.4F);
							}
						}
					} catch (Throwable t) {}
				}
				// v13: 暗杀 - 目标的目标不是玩家时伤害+30%
				if (hasAffix(player.getUUID(), "good_ambush_uncommon")) {
					net.minecraft.world.entity.LivingEntity mobTarget = mob.getTarget();
					if (mobTarget != null && !(mobTarget instanceof net.minecraft.world.entity.player.Player)) {
						IN_BOSS_BONUS.set(true);
						try {
							mob.hurt(source, appliedAmount * 0.3F);
						} finally {
							IN_BOSS_BONUS.set(false);
						}
					}
				}
				// v13: 动能蓄势 - 疾跑或跳跃后附加4伤害
				if (hasAffix(player.getUUID(), "good_momentum_rare")) {
					if (player.isSprinting() || player.fallDistance > 0) {
						IN_BOSS_BONUS.set(true);
						try {
							mob.hurt(source, 4.0F);
						} finally {
							IN_BOSS_BONUS.set(false);
						}
					}
				}
				// v13: 破甲 - 20%概率给目标施加虚弱
				if (hasAffix(player.getUUID(), "good_armor_break_epic") && new Random().nextDouble() < 0.2) {
					mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, false, false, true));
				}
				// v13: 削弱 - 攻击降低目标攻击力（虚弱II）
				if (hasAffix(player.getUUID(), "good_weaken_uncommon")) {
					mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1, false, false, true));
				}
				// v13: 额外射击 - 弓箭射击时对附近敌人造成额外伤害
				if (hasAffix(player.getUUID(), "good_extra_shot_rare")) {
					net.minecraft.world.item.Item heldItem = player.getMainHandItem().getItem();
					if (heldItem instanceof net.minecraft.world.item.BowItem
							|| heldItem instanceof net.minecraft.world.item.CrossbowItem) {
						try {
							var level = mob.level();
							net.minecraft.world.entity.LivingEntity nearest = null;
							double nearestDist = Double.MAX_VALUE;
							for (net.minecraft.world.entity.LivingEntity nearby : level.getEntitiesOfClass(
									net.minecraft.world.entity.LivingEntity.class,
									mob.getBoundingBox().inflate(10.0))) {
								if (nearby == mob || nearby == player) continue;
								if (!(nearby instanceof net.minecraft.world.entity.monster.Enemy)
										&& !isHostileTypeForDetonate(nearby)) continue;
								double dist = nearby.distanceToSqr(mob);
								if (dist < nearestDist) {
									nearestDist = dist;
									nearest = nearby;
								}
							}
							if (nearest != null) {
								nearest.hurt(player.damageSources().mobAttack(player), appliedAmount * 0.5F);
							}
						} catch (Throwable t) {}
					}
				}
				// v13: 连锁束缚 - 20%概率给3格内最多3个敌人施加缓慢IV 2秒
				if (hasAffix(player.getUUID(), "good_chain_bind_epic") && new Random().nextDouble() < 0.2) {
					try {
						var level = mob.level();
						int count = 0;
						for (net.minecraft.world.entity.LivingEntity nearby : level.getEntitiesOfClass(
								net.minecraft.world.entity.LivingEntity.class,
								mob.getBoundingBox().inflate(3.0))) {
							if (nearby == mob || nearby == player) continue;
							if (nearby instanceof net.minecraft.world.entity.monster.Enemy
									|| isHostileTypeForDetonate(nearby)) {
								nearby.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3, false, false, true));
								count++;
								if (count >= 3) break;
							}
						}
					} catch (Throwable t) {}
				}
				HostileBoost boost = calculateGlobalHostileBoost();
				if (boost.thornsReflect() > 0) {
					float reflectAmount = appliedAmount * (float) boost.thornsReflect();
					if (reflectAmount > 0) {
						player.hurt(player.damageSources().thorns(mob), reflectAmount);
					}
				}
			}
		}
		// 玩家受击 → 尝试召唤友方生物
		if (entity instanceof ServerPlayer player) {
			trySummonAlly(player);
		}
		// 敌对生物造成伤害 → 敌对生物吸血（全局坏词条效果）
		if (attacker instanceof net.minecraft.world.entity.Mob mob && entity instanceof ServerPlayer) {
			HostileBoost boost = calculateGlobalHostileBoost();
			if (boost.lifestealPercent() > 0) {
				float heal = appliedAmount * (float) boost.lifestealPercent();
				if (heal > 0) mob.heal(heal);
			}
			// 敌对生物召唤同伴（受击方触发，含冷却限制）
			if (boost.summonChance() > 0 && new Random().nextDouble() < boost.summonChance()) {
				long now = System.currentTimeMillis();
				Long lastHostileSummon = HOSTILE_SUMMON_COOLDOWNS.get(mob.getUUID());
				if (lastHostileSummon == null || now - lastHostileSummon >= HOSTILE_SUMMON_COOLDOWN_MS) {
					var type = mob.getType();
					var level = mob.level();
					var ally = type.create(level);
					if (ally != null) {
						ally.setPos(mob.getX() + (new Random().nextDouble() - 0.5) * 3,
								mob.getY(), mob.getZ() + (new Random().nextDouble() - 0.5) * 3);
						level.addFreshEntity(ally);
						HOSTILE_SUMMON_COOLDOWNS.put(mob.getUUID(), now);
					}
				}
			}
		}
	}

	/**
	 * 获取玩家经验加成百分比（用于经验获取时）
	 */
	public static int getExpBonusPercent(UUID playerId) {
		int total = 0;
		for (Affix affix : getAffixes(playerId)) {
			if (affix.isGood()) total += affix.getExpBonusPercent();
		}
		return total;
	}

	// ========== 坏词条全局效果（用于怪物增强）==========

	/**
	 * 计算所有玩家坏词条的累计全局效果
	 * 用于增强新生成的敌对生物
	 * v2: 新增吸血/召唤概率/火焰免疫/护甲等级
	 * v5: armorLevel/armorPercent 改为累加（用于伤害免疫率），速度倍率封顶1.3x
	 */
	public static HostileBoost calculateGlobalHostileBoost() {
		double healthBonusTotal = 0, damageBonusTotal = 0, speedBonusTotal = 0;
		double healthMult = 1.0, damageMult = 1.0, speedMult = 1.0;
		int maxEffectAmplifier = 0;
		// v2 新增
		double lifestealPercent = 0;
		double summonChance = 0;
		boolean fireImmunity = false;
		int armorLevel = 0;
		// v3 新增
		int regenLevel = 0;
		double thornsReflect = 0;
		// v4 新增
		double armorPercent = 0;
		// v12 新增
		int healReduction = 0;

		for (var entry : PLAYER_AFFIXES.entrySet()) {
			// v19: 离线玩家的敌对词条不起作用
			if (currentServer != null && currentServer.getPlayerList().getPlayer(entry.getKey()) == null) continue;
			List<String> affixIds = entry.getValue();
			for (String id : affixIds) {
				Affix affix = AffixRegistry.getById(id);
				if (affix == null || affix.isGood()) continue;
				// v16: 改为加法叠加（非乘法），避免多人指数级增长
				healthBonusTotal += (affix.getHostileHealthMult() - 1.0);
				damageBonusTotal += (affix.getHostileDamageMult() - 1.0);
				speedBonusTotal += (affix.getHostileSpeedMult() - 1.0);
				maxEffectAmplifier = Math.max(maxEffectAmplifier, affix.getHostileEffectAmplifier());
				// v2 新增效果累加
				lifestealPercent = Math.max(lifestealPercent, affix.getHostileLifestealPercent());
				summonChance = Math.max(summonChance, affix.getHostileSummonChance());
				fireImmunity |= affix.isHostileFireImmunity();
				// v5: armorLevel 改为累加（用于伤害免疫率计算）
				armorLevel += affix.getHostileArmorLevel();
				// v3 新增效果累加
				regenLevel = Math.max(regenLevel, affix.getHostileRegenLevel());
				thornsReflect = Math.max(thornsReflect, affix.getHostileThornsReflect());
				// v5: armorPercent 改为累加（用于伤害免疫率计算）
				armorPercent += affix.getHostileArmorPercent();
				// v12: 抑制回血（取最大值，而非累加，避免100%抑制）
				healReduction = Math.max(healReduction, affix.getHealReductionPercent());
			}
		}
		// v16: 加法叠加后封顶，防止多人游戏怪物过强
		healthMult = 1.0 + Math.min(healthBonusTotal, BalanceConfig.MAX_HOSTILE_HEALTH_MULT - 1.0);
		damageMult = 1.0 + Math.min(damageBonusTotal, BalanceConfig.MAX_HOSTILE_DAMAGE_MULT - 1.0);
		speedMult = 1.0 + Math.min(speedBonusTotal, BalanceConfig.MAX_HOSTILE_SPEED_MULT - 1.0);
		return new HostileBoost(healthMult, damageMult, speedMult, maxEffectAmplifier,
				lifestealPercent, summonChance, fireImmunity, armorLevel,
				regenLevel, thornsReflect, armorPercent, healReduction);
	}

	// ========== v15: 傀儡词条效果应用 ==========

	/**
	 * v15: 将玩家的好词条和坏词条效果应用到玩家制作的傀儡上
	 * - 好词条：生命加成、攻击力、移速、攻击速度、火焰/溺水/摔落免疫、每秒回血
	 * - 坏词条：生命倍率、伤害倍率、速度倍率、护甲、护甲韧性、火焰免疫、反伤、吸血
	 * 每 100 tick（5秒）调用一次，属性加成需要定期重新应用（傀儡可能被清除或重生）
	 */
	public static void applyAffixesToGolems(ServerPlayer player) {
		if (player == null) return;
		UUID playerId = player.getUUID();
		MinecraftServer server = player.getServer();
		if (server == null) return;

		// ===== 收集好词条效果 =====
		List<Affix> affixes = getAffixes(playerId);
		int healthBonus = 0;
		double baseAttackDamage = 0;
		double moveSpeedBonus = 0;
		double moveSpeedPercent = 0;
		double attackSpeedBonus = 0;
		boolean fireImmunity = false;
		boolean drownImmunity = false;
		boolean fallImmunity = false;
		double hpPerSecond = 0;

		boolean goodDisabled = com.randomsurprise.battlefield.BattlefieldManager.isGoodAffixDisabled();
		if (!goodDisabled) {
			for (Affix affix : affixes) {
				if (!affix.isGood()) continue;
				healthBonus += affix.getHealthBonus();
				baseAttackDamage += affix.getBaseAttackDamage();
				moveSpeedBonus = Math.max(moveSpeedBonus, affix.getMoveSpeedBonus());
				moveSpeedPercent += affix.getMoveSpeedPercent();
				attackSpeedBonus = Math.max(attackSpeedBonus, affix.getAttackSpeedBonus());
				fireImmunity |= affix.isFireImmunity();
				drownImmunity |= affix.isDrownImmunity();
				fallImmunity |= affix.isFallImmunity();
				hpPerSecond += affix.getHpPerSecond();
			}
			// v16: 属性封顶，与玩家效果一致
			healthBonus = BalanceMath.capHealthBonus(healthBonus);
			baseAttackDamage = BalanceMath.capBaseAttackDamage(baseAttackDamage);
			moveSpeedPercent = BalanceMath.capMoveSpeedPercent(moveSpeedPercent);
			hpPerSecond = BalanceMath.capHpPerSecond(hpPerSecond);
		}

		// ===== 收集坏词条效果 =====
		HostileBoost boost = calculatePlayerHostileBoost(playerId);

		// ===== 遍历所有服务器维度，找到该玩家的傀儡 =====
		Set<UUID> foundGolems = new HashSet<>();
		for (net.minecraft.server.level.ServerLevel serverLevel : server.getAllLevels()) {
			for (net.minecraft.world.entity.Entity entity : serverLevel.getAllEntities()) {
				if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) continue;
				if (!living.isAlive()) continue;
				UUID ownerUUID = getGolemOwnerUUID(living);
				if (ownerUUID == null || !ownerUUID.equals(playerId)) continue;

				UUID golemUUID = living.getUUID();
				foundGolems.add(golemUUID);
				GOLEM_OWNER_MAP.put(golemUUID, playerId);

				// ===== 应用好词条 AttributeModifier =====
				// 1. MAX_HEALTH（好词条 ADDITION + 坏词条倍率转为 ADDITION）
				try {
					var healthAttr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
					if (healthAttr != null) {
						healthAttr.removeModifier(GOLEM_HEALTH_ID);
						double baseHealth = healthAttr.getBaseValue();
						double totalHealthBonus = healthBonus;
						if (boost.healthMult() != 1.0) {
							totalHealthBonus += baseHealth * (boost.healthMult() - 1.0);
						}
						if (totalHealthBonus > 0) {
							healthAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
									GOLEM_HEALTH_ID, "golem_health", totalHealthBonus,
									net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
						}
					}
				} catch (Throwable t) {
					com.randomsurprise.RandomSurpriseMod.LOGGER.warn("v15: 应用傀儡生命加成失败: {}", t.getMessage());
				}

				// 2. ATTACK_DAMAGE（好词条 ADDITION + 坏词条倍率转为 ADDITION）
				try {
					var attackAttr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
					if (attackAttr != null) {
						attackAttr.removeModifier(GOLEM_ATTACK_ID);
						double baseAttack = attackAttr.getBaseValue();
						double totalAttackBonus = baseAttackDamage;
						if (boost.damageMult() != 1.0) {
							totalAttackBonus += baseAttack * (boost.damageMult() - 1.0);
						}
						if (totalAttackBonus > 0) {
							attackAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
									GOLEM_ATTACK_ID, "golem_attack", totalAttackBonus,
									net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
						}
					}
				} catch (Throwable t) {
					com.randomsurprise.RandomSurpriseMod.LOGGER.warn("v15: 应用傀儡攻击力加成失败: {}", t.getMessage());
				}

				// 3. MOVEMENT_SPEED（好词条 + 坏词条都用 MULTIPLY_TOTAL）
				try {
					var speedAttr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
					if (speedAttr != null) {
						speedAttr.removeModifier(GOLEM_SPEED_ID);
						double totalSpeedMult = moveSpeedBonus + moveSpeedPercent;
						if (boost.speedMult() != 1.0) {
							totalSpeedMult += (boost.speedMult() - 1.0);
						}
						if (totalSpeedMult > 0) {
							speedAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
									GOLEM_SPEED_ID, "golem_speed", totalSpeedMult,
									net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
						}
					}
				} catch (Throwable t) {
					com.randomsurprise.RandomSurpriseMod.LOGGER.warn("v15: 应用傀儡移速加成失败: {}", t.getMessage());
				}

				// 4. ATTACK_SPEED（好词条 MULTIPLY_TOTAL）
				try {
					var atkSpeedAttr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
					if (atkSpeedAttr != null) {
						atkSpeedAttr.removeModifier(GOLEM_ATTACK_SPEED_ID);
						if (attackSpeedBonus > 0) {
							atkSpeedAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
									GOLEM_ATTACK_SPEED_ID, "golem_attack_speed", attackSpeedBonus,
									net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
						}
					}
				} catch (Throwable t) {
					com.randomsurprise.RandomSurpriseMod.LOGGER.warn("v15: 应用傀儡攻击速度加成失败: {}", t.getMessage());
				}

				// 5. ARMOR（坏词条 ADDITION，每级2点护甲）
				try {
					var armorAttr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
					if (armorAttr != null) {
						armorAttr.removeModifier(GOLEM_ARMOR_ID);
						int armorBonus = boost.armorLevel() * 2;
						if (armorBonus > 0) {
							armorAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
									GOLEM_ARMOR_ID, "golem_armor", (double) armorBonus,
									net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
						}
					}
				} catch (Throwable t) {
					com.randomsurprise.RandomSurpriseMod.LOGGER.warn("v15: 应用傀儡护甲加成失败: {}", t.getMessage());
				}

				// 6. ARMOR_TOUGHNESS（坏词条 ADDITION，每个百分点1点韧性）
				try {
					var toughnessAttr = living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
					if (toughnessAttr != null) {
						toughnessAttr.removeModifier(GOLEM_ARMOR_TOUGH_ID);
						double toughnessBonus = boost.armorPercent();
						if (toughnessBonus > 0) {
							toughnessAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
									GOLEM_ARMOR_TOUGH_ID, "golem_armor_toughness", toughnessBonus,
									net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
						}
					}
				} catch (Throwable t) {
					com.randomsurprise.RandomSurpriseMod.LOGGER.warn("v15: 应用傀儡护甲韧性加成失败: {}", t.getMessage());
				}

				// ===== 存储免疫/反伤/吸血/回血到 Map（供 LivingHurtEvent 使用） =====
				// 火焰免疫（好词条或坏词条）
				if (fireImmunity || boost.fireImmunity()) {
					GOLEM_FIRE_IMMUNE.add(golemUUID);
				} else {
					GOLEM_FIRE_IMMUNE.remove(golemUUID);
				}
				// 溺水免疫（好词条）
				if (drownImmunity) {
					GOLEM_DROWN_IMMUNE.add(golemUUID);
				} else {
					GOLEM_DROWN_IMMUNE.remove(golemUUID);
				}
				// 摔落免疫（好词条）
				if (fallImmunity) {
					GOLEM_FALL_IMMUNE.add(golemUUID);
				} else {
					GOLEM_FALL_IMMUNE.remove(golemUUID);
				}
				// 反伤（坏词条）
				if (boost.thornsReflect() > 0) {
					GOLEM_THORNS.put(golemUUID, boost.thornsReflect());
				} else {
					GOLEM_THORNS.remove(golemUUID);
				}
				// 吸血（坏词条）
				if (boost.lifestealPercent() > 0) {
					GOLEM_LIFESTEAL.put(golemUUID, boost.lifestealPercent());
				} else {
					GOLEM_LIFESTEAL.remove(golemUUID);
				}
				// 每秒回血（好词条）
				if (hpPerSecond > 0) {
					GOLEM_REGEN.put(golemUUID, (float) hpPerSecond);
				} else {
					GOLEM_REGEN.remove(golemUUID);
				}
			}
		}

		// ===== 清理已不存在的傀儡的状态条目 =====
		var mapIterator = GOLEM_OWNER_MAP.entrySet().iterator();
		while (mapIterator.hasNext()) {
			var entry = mapIterator.next();
			if (playerId.equals(entry.getValue()) && !foundGolems.contains(entry.getKey())) {
				UUID staleUUID = entry.getKey();
				mapIterator.remove();
				GOLEM_FIRE_IMMUNE.remove(staleUUID);
				GOLEM_DROWN_IMMUNE.remove(staleUUID);
				GOLEM_FALL_IMMUNE.remove(staleUUID);
				GOLEM_THORNS.remove(staleUUID);
				GOLEM_LIFESTEAL.remove(staleUUID);
				GOLEM_REGEN.remove(staleUUID);
			}
		}
	}

	/**
	 * v15: 傀儡词条伤害效果处理（在 LivingHurtEvent 中调用）
	 * 处理：
	 * - 傀儡火焰/溺水/摔落免疫：取消对应伤害
	 * - 傀儡反伤：对攻击者反射伤害
	 * - 傀儡吸血：傀儡造成伤害时回复生命
	 * @param entity 受伤实体
	 * @param source 伤害来源
	 * @param amount 伤害值
	 * @return true 表示伤害应被取消（免疫），false 表示正常处理
	 */
	public static boolean applyGolemAffixHurtEffects(net.minecraft.world.entity.LivingEntity entity,
			net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (entity == null || entity.level().isClientSide()) return false;
		UUID entityUUID = entity.getUUID();

		// Case 1: 受伤实体是傀儡 → 免疫 + 反伤
		if (GOLEM_OWNER_MAP.containsKey(entityUUID)) {
			// 火焰免疫
			if (GOLEM_FIRE_IMMUNE.contains(entityUUID)
					&& source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
				return true;
			}
			// 溺水免疫
			if (GOLEM_DROWN_IMMUNE.contains(entityUUID)
					&& source.is(net.minecraft.world.damagesource.DamageTypes.DROWN)) {
				return true;
			}
			// 摔落免疫
			if (GOLEM_FALL_IMMUNE.contains(entityUUID)
					&& source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
				return true;
			}
			// 反伤
			Double thornsPercent = GOLEM_THORNS.get(entityUUID);
			if (thornsPercent != null && thornsPercent > 0) {
				var attacker = source.getEntity();
				if (attacker instanceof net.minecraft.world.entity.LivingEntity livingAttacker
						&& !livingAttacker.level().isClientSide()
						&& !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
					float reflectAmount = amount * thornsPercent.floatValue();
					if (reflectAmount > 0) {
						livingAttacker.hurt(entity.damageSources().thorns(livingAttacker), reflectAmount);
					}
				}
			}
		}

		// Case 2: 攻击者是傀儡 → 吸血（傀儡造成伤害时回复自身生命）
		var attacker = source.getEntity();
		if (attacker instanceof net.minecraft.world.entity.LivingEntity attackerLiving) {
			UUID attackerUUID = attackerLiving.getUUID();
			if (GOLEM_OWNER_MAP.containsKey(attackerUUID)) {
				Double lifestealPercent = GOLEM_LIFESTEAL.get(attackerUUID);
				if (lifestealPercent != null && lifestealPercent > 0
						&& attackerLiving.getHealth() < attackerLiving.getMaxHealth()) {
					float heal = amount * lifestealPercent.floatValue();
					if (heal > 0) {
						attackerLiving.heal(heal);
					}
				}
			}
		}
		return false;
	}

	/**
	 * v15: 清除死亡傀儡的所有状态条目
	 * 在 LivingDeathEvent 中调用
	 */
	public static void onGolemDeath(UUID golemUUID) {
		GOLEM_OWNER_MAP.remove(golemUUID);
		GOLEM_FIRE_IMMUNE.remove(golemUUID);
		GOLEM_DROWN_IMMUNE.remove(golemUUID);
		GOLEM_FALL_IMMUNE.remove(golemUUID);
		GOLEM_THORNS.remove(golemUUID);
		GOLEM_LIFESTEAL.remove(golemUUID);
		GOLEM_REGEN.remove(golemUUID);
	}

	/**
	 * v15: 计算单个玩家持有的坏词条效果（用于傀儡增强）
	 * 与 calculateGlobalHostileBoost 类似，但只计算指定玩家的坏词条
	 * @param playerId 玩家UUID
	 * @return 该玩家的坏词条增强效果
	 */
	public static HostileBoost calculatePlayerHostileBoost(UUID playerId) {
		double healthBonusTotal = 0, damageBonusTotal = 0, speedBonusTotal = 0;
		double healthMult = 1.0, damageMult = 1.0, speedMult = 1.0;
		int maxEffectAmplifier = 0;
		double lifestealPercent = 0;
		double summonChance = 0;
		boolean fireImmunity = false;
		int armorLevel = 0;
		int regenLevel = 0;
		double thornsReflect = 0;
		double armorPercent = 0;
		int healReduction = 0;

		List<String> affixIds = PLAYER_AFFIXES.getOrDefault(playerId, Collections.emptyList());
		for (String id : affixIds) {
			Affix affix = AffixRegistry.getById(id);
			if (affix == null || affix.isGood()) continue;
			// v16: 加法叠加+封顶，与全局方法保持一致
			healthBonusTotal += (affix.getHostileHealthMult() - 1.0);
			damageBonusTotal += (affix.getHostileDamageMult() - 1.0);
			speedBonusTotal += (affix.getHostileSpeedMult() - 1.0);
			maxEffectAmplifier = Math.max(maxEffectAmplifier, affix.getHostileEffectAmplifier());
			lifestealPercent = Math.max(lifestealPercent, affix.getHostileLifestealPercent());
			summonChance = Math.max(summonChance, affix.getHostileSummonChance());
			fireImmunity |= affix.isHostileFireImmunity();
			armorLevel += affix.getHostileArmorLevel();
			regenLevel = Math.max(regenLevel, affix.getHostileRegenLevel());
			thornsReflect = Math.max(thornsReflect, affix.getHostileThornsReflect());
			armorPercent += affix.getHostileArmorPercent();
			healReduction = Math.max(healReduction, affix.getHealReductionPercent());
		}
		healthMult = 1.0 + Math.min(healthBonusTotal, BalanceConfig.MAX_HOSTILE_HEALTH_MULT - 1.0);
		damageMult = 1.0 + Math.min(damageBonusTotal, BalanceConfig.MAX_HOSTILE_DAMAGE_MULT - 1.0);
		speedMult = 1.0 + Math.min(speedBonusTotal, BalanceConfig.MAX_HOSTILE_SPEED_MULT - 1.0);
		return new HostileBoost(healthMult, damageMult, speedMult, maxEffectAmplifier,
				lifestealPercent, summonChance, fireImmunity, armorLevel,
				regenLevel, thornsReflect, armorPercent, healReduction);
	}

	/**
	 * 计算玩家自己持有的敌对词条数量（用于稀有奖励补偿机制）
	 */
	public static int getBadAffixCount(UUID playerId) {
		List<String> ids = PLAYER_AFFIXES.getOrDefault(playerId, Collections.emptyList());
		int count = 0;
		for (String id : ids) {
			Affix a = AffixRegistry.getById(id);
			if (a != null && !a.isGood()) count++;
		}
		return count;
	}

	/**
	 * 计算全服敌对词条总数（用于稀有奖励补偿机制）
	 */
	public static int getGlobalBadAffixCount() {
		int count = 0;
		for (var entry : PLAYER_AFFIXES.entrySet()) {
			// v19: 离线玩家的词条不起作用
			if (currentServer != null && currentServer.getPlayerList().getPlayer(entry.getKey()) == null) continue;
			for (String id : entry.getValue()) {
				Affix a = AffixRegistry.getById(id);
				if (a != null && !a.isGood()) count++;
			}
		}
		return count;
	}

	// ========== 同步词条数据到客户端 ==========

	/**
	 * 将当前玩家的词条数据同步给指定玩家
	 * （用于玩家加入服务器时）
	 */
	public static void syncToClient(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) return;
		List<String> own = PLAYER_AFFIXES.getOrDefault(player.getUUID(), Collections.emptyList());
		List<AffixSyncPayload.PlayerBadAffixes> allBad = buildAllBadAffixesForPayload(server);
		AffixSyncPayload payload = new AffixSyncPayload(
				new ArrayList<>(own), allBad);
		com.randomsurprise.network.ModNetworking.sendAffixSync(player, payload);
	}

	/**
	 * 将所有玩家的词条数据同步给所有在线玩家
	 * （用于词条变更、清除药水使用后）
	 */
	public static void syncAllToClients(MinecraftServer server) {
		if (server == null) return;
		List<AffixSyncPayload.PlayerBadAffixes> allBad = buildAllBadAffixesForPayload(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			List<String> own = PLAYER_AFFIXES.getOrDefault(player.getUUID(), Collections.emptyList());
			AffixSyncPayload payload = new AffixSyncPayload(
					new ArrayList<>(own), allBad);
			com.randomsurprise.network.ModNetworking.sendAffixSync(player, payload);
		}
	}

	/** 构建所有玩家的敌对词条分组（玩家名 → 敌对词条列表） */
	private static List<AffixSyncPayload.PlayerBadAffixes> buildAllBadAffixesForPayload(MinecraftServer server) {
		List<AffixSyncPayload.PlayerBadAffixes> result = new ArrayList<>();
		for (Map.Entry<UUID, List<String>> entry : PLAYER_AFFIXES.entrySet()) {
			UUID uuid = entry.getKey();
			List<String> badIds = new ArrayList<>();
			for (String id : entry.getValue()) {
				Affix a = AffixRegistry.getById(id);
				if (a != null && !a.isGood()) badIds.add(id);
			}
			if (badIds.isEmpty()) continue;
			// 查找玩家名
			ServerPlayer p = server.getPlayerList().getPlayer(uuid);
			String name = (p != null) ? p.getName().getString() : uuid.toString().substring(0, 8);
			result.add(new AffixSyncPayload.PlayerBadAffixes(name, badIds));
		}
		return result;
	}

	/** 敌对生物增强数据（v2 扩展：新增吸血/召唤/火焰免疫/护甲；v3 新增再生/反伤；v4 新增护甲百分比；v12 新增抑制回血） */
	public static record HostileBoost(double healthMult, double damageMult, double speedMult, int effectAmplifier,
			double lifestealPercent, double summonChance, boolean fireImmunity, int armorLevel,
			int regenLevel, double thornsReflect, double armorPercent,
			int healReductionPercent) {
		public boolean hasAnyBoost() {
			return healthMult > 1.0 || damageMult > 1.0 || speedMult > 1.0 || effectAmplifier > 0
					|| lifestealPercent > 0 || summonChance > 0 || fireImmunity || armorLevel > 0
					|| regenLevel > 0 || thornsReflect > 0 || armorPercent > 0
					|| healReductionPercent > 0;
		}
	}
}
