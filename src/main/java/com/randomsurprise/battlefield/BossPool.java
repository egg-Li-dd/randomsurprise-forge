package com.randomsurprise.battlefield;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 征召战场 Boss 池
 *
 * 分级设计（5个Tier，对应难度1-10）：
 *   Tier 1 (难度1-2)：入门级 - 掠夺兽、远古守卫者、变异小怪
 *   Tier 2 (难度3-4)：中级   - 凋灵、小型模组Boss
 *   Tier 3 (难度5-6)：高级   - 监守者、中型模组Boss
 *   Tier 4 (难度7-8)：史诗   - 末影龙、大型模组Boss
 *   Tier 5 (难度9-10+决战)：决战级 - 全部Boss混合
 *
 * 原版Boss按固定Tier分级；模组Boss动态解析（遍历实体注册表）并按关键词粗分3档。
 */
public class BossPool {

	/** Boss Tier 分级 */
	public enum Tier {
		TIER_1(1, 2),
		TIER_2(3, 4),
		TIER_3(5, 6),
		TIER_4(7, 8),
		TIER_5(9, Integer.MAX_VALUE);

		public final int minDifficulty;
		public final int maxDifficulty;

		Tier(int min, int max) { this.minDifficulty = min; this.maxDifficulty = max; }

		/** 根据难度返回对应Tier */
		public static Tier forDifficulty(int difficulty) {
			for (Tier t : values()) {
				if (difficulty >= t.minDifficulty && difficulty <= t.maxDifficulty) return t;
			}
			return TIER_5;
		}
	}

	// ===== 原版Boss按Tier分级（硬编码）=====
	private static final Map<Tier, List<String>> VANILLA_BOSSES = new HashMap<>();
	static {
		VANILLA_BOSSES.put(Tier.TIER_1, List.of("minecraft:ravager", "minecraft:elder_guardian"));
		VANILLA_BOSSES.put(Tier.TIER_2, List.of("minecraft:wither"));
		VANILLA_BOSSES.put(Tier.TIER_3, List.of("minecraft:warden"));
		VANILLA_BOSSES.put(Tier.TIER_4, List.of("minecraft:ender_dragon"));
		VANILLA_BOSSES.put(Tier.TIER_5, List.of(
				"minecraft:warden", "minecraft:wither", "minecraft:ender_dragon", "minecraft:ravager"));
	}

	// ===== 模组Boss识别（复用 TicketDropHandler 的命名空间与关键词）=====
	/** 已知生物模组命名空间 */
	private static final Set<String> KNOWN_MOD_NAMESPACES = Set.of(
			"alexsmobs", "mowziesmobs", "born_in_chaos", "cataclysm", "le_enders_cataclysm",
			"bosses_of_mass_destruction", "blue_skies", "twilightforest", "aether",
			"betteranimalsplus", "naturalist", "crittersandcompanions", "decorative_blocks",
			"illage_and_spillage", "mutantmonsters", "cave_dweller", "multigolem",
			"enderzoology", "friendsandfoes", "infernalmobs", "ixeris", "more_critters",
			"morecritters", "dimensionalstomach", "modulargolems", "curios", "citadel"
	);

	/** Boss关键词（路径包含这些视为Boss） */
	private static final Set<String> BOSS_KEYWORDS = Set.of(
			"boss", "king", "queen", "lord", "overlord", "titan", "leviathan", "dragon",
			"golem", "dweller", "giant", "wildfire", "illusioner", "mosco", "maw", "wroughtnaut",
			"spirit", "revenant", "monstrosity", "harbinger", "symbiont", "monarch",
			"hydra", "lich", "knight", "yeti", "slayer", "stomper", "charger", "crusher",
			"hunter", "prowler", "stalker", "basilisk", "serpent", "beast", "crawler",
			"demon", "devil", "imp", "ghoul", "wraith", "specter", "phantom", "poltergeist",
			"troll", "orc", "goblin"
	);

	/** 高难度Boss关键词（映射到Tier 5） */
	private static final Set<String> HARD_KEYWORDS = Set.of(
			"dragon", "titan", "leviathan", "overlord", "monstrosity", "monarch"
	);
	/** 中难度Boss关键词（映射到Tier 3-4） */
	private static final Set<String> MEDIUM_KEYWORDS = Set.of(
			"king", "queen", "lord", "golem", "dweller", "giant", "maw", "wroughtnaut", "boss",
			"symbiont", "harbinger", "revenant", "spirit"
	);
	/** 低难度Boss关键词（映射到Tier 2） */
	private static final Set<String> EASY_KEYWORDS = Set.of(
			"wildfire", "illusioner", "mosco", "mutant"
	);

	/** 排除的命名空间（这些模组的实体永不被判定为Boss） */
	private static final Set<String> EXCLUDED_NAMESPACES = Set.of(
			"modulargolems",   // 玩家自建傀儡，非Boss
			"happyghastmod",   // 友好恶魂
			"naturalist",      // 真实动物，无Boss
			"more_critters",   // 环境小生物
			"morecritters"     // 同上（modid 可能拼写不同）
	);

	/** 排除的特定实体ID（即使含Boss关键词也不视为Boss） */
	private static final Set<String> EXCLUDED_ENTITY_IDS = Set.of(
			"cave_dweller:cave_dweller"  // 追逐型精英，非真正Boss
	);

	/** 需要特殊方式击败的Boss（从Boss池中排除，避免战场中无法正常击杀） */
	private static final Set<String> SPECIAL_DEFEAT_BOSSES = Set.of(
			"minecraft:ender_dragon",      // 需要先摧毁末影水晶
			"minecraft:elder_guardian",   // 施加永久挖掘疲劳，战场中极难击败
			"twilightforest:hydra",       // 需要特殊策略（嘴巴张开时攻击）
			"twilightforest:snow_queen",  // 需要特殊阶段机制
			"twilightforest:ur_ghast",    // 需要特殊机制（飞行+炮塔）
			"aether:slider",              // 只在移动时可被攻击
			"aether:valkyrie_queen",      // 需要特殊对话触发
			"aether:sun_spirit",          // 需要特殊机制（冰火阶段）
			"bosses_of_mass_destruction:gaia",  // 需要特殊机制（地形交互）
			"cataclysm:the_leviathan",    // 需要特殊机制（水下战斗）
			"cataclysm:netherite_monstrosity"   // 需要特殊机制（岩浆免疫）
	);

	/** 硬编码Boss精确分级表（实体ID → Tier），优先于关键词逻辑 */
	private static final Map<String, Tier> HARDCODED_BOSS_TIERS = Map.ofEntries(
			Map.entry("mowziesmobs:naga", Tier.TIER_2),
			Map.entry("friendsandfoes:wildfire", Tier.TIER_2),
			Map.entry("friendsandfoes:iceologer", Tier.TIER_2),
			Map.entry("friendsandfoes:illusioner", Tier.TIER_2),
			Map.entry("enderzoology:ender_golem", Tier.TIER_2),
			Map.entry("twilightforest:naga", Tier.TIER_2),
			Map.entry("mutantmonsters:mutant_zombie", Tier.TIER_2),
			Map.entry("mutantmonsters:mutant_skeleton", Tier.TIER_2),
			Map.entry("mutantmonsters:mutant_creeper", Tier.TIER_2),
			Map.entry("mowziesmobs:frostmaw", Tier.TIER_3),
			Map.entry("mowziesmobs:ferrous_wroughtnaut", Tier.TIER_3),
			Map.entry("mowziesmobs:barako", Tier.TIER_3),
			Map.entry("twilightforest:lich", Tier.TIER_3),
			Map.entry("twilightforest:minoshroom", Tier.TIER_3),
			Map.entry("twilightforest:knight_phantom", Tier.TIER_3),
			Map.entry("twilightforest:alpha_yeti", Tier.TIER_3),
			Map.entry("aether:slider", Tier.TIER_3),
			Map.entry("bosses_of_mass_destruction:lich", Tier.TIER_3),
			Map.entry("mutantmonsters:mutant_enderman", Tier.TIER_3),
			Map.entry("illageandspillage:magispeller", Tier.TIER_3),
			Map.entry("illageandspillage:blastfinder", Tier.TIER_3),
			Map.entry("illageandspillage:tremozzarella", Tier.TIER_3),
			Map.entry("alexsmobs:void_worm", Tier.TIER_4),
			Map.entry("twilightforest:hydra", Tier.TIER_4),
			Map.entry("twilightforest:ur_ghast", Tier.TIER_4),
			Map.entry("twilightforest:snow_queen", Tier.TIER_4),
			Map.entry("aether:valkyrie_queen", Tier.TIER_4),
			Map.entry("bosses_of_mass_destruction:obsidilith", Tier.TIER_4),
			Map.entry("bosses_of_mass_destruction:gaia", Tier.TIER_4),
			Map.entry("bosses_of_mass_destruction:chorus_horror", Tier.TIER_4),
			Map.entry("cataclysm:ignited_revenant", Tier.TIER_4),
			Map.entry("cataclysm:kobolediator", Tier.TIER_4),
			Map.entry("cataclysm:the_watcher", Tier.TIER_4),
			Map.entry("cataclysm:withered_symbiont", Tier.TIER_4),
			Map.entry("cataclysm:obsidilith", Tier.TIER_4),
			Map.entry("cataclysm:the_harbinger", Tier.TIER_4),
			Map.entry("cataclysm:netherite_monstrosity", Tier.TIER_5),
			Map.entry("cataclysm:the_leviathan", Tier.TIER_5),
			Map.entry("cataclysm:withered_monarch", Tier.TIER_5),
			Map.entry("aether:sun_spirit", Tier.TIER_5)
	);

	/** 模组Boss缓存：Tier → 实体ID列表（首次调用时解析） */
	private static volatile Map<Tier, List<String>> moddedBossCache;
	private static volatile boolean resolved = false;

	/** 普通敌对生物缓存（用于战场小怪生成） */
	private static volatile List<String> hostileMobCache;
	private static volatile boolean hostileResolved = false;

	/** 原版敌对生物列表（用于战场小怪生成） */
	private static final List<String> VANILLA_HOSTILE_MOBS = List.of(
			"minecraft:zombie", "minecraft:skeleton", "minecraft:creeper", "minecraft:spider",
			"minecraft:enderman", "minecraft:blaze", "minecraft:ghast", "minecraft:magma_cube",
			"minecraft:slime", "minecraft:witch", "minecraft:stray", "minecraft:husk",
			"minecraft:drowned", "minecraft:phantom", "minecraft:wither_skeleton",
			"minecraft:silverfish", "minecraft:cave_spider", "minecraft:vex", "minecraft:vindicator",
			"minecraft:evoker", "minecraft:pillager", "minecraft:ravager", "minecraft:elder_guardian"
	);

	/**
	 * 解析所有已安装模组的Boss实体，按Tier分级缓存
	 * 遍历 BuiltInRegistries.ENTITY_TYPE，匹配命名空间+关键词
	 */
	private static synchronized void resolveModdedBosses() {
		if (resolved) return;
		Map<Tier, List<String>> cache = new HashMap<>();
		for (Tier t : Tier.values()) cache.put(t, new ArrayList<>());

		try {
			for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
				ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
				if (id == null) continue;
				String namespace = id.getNamespace();
				String path = id.getPath();
				String fullId = id.toString();
				// 排除黑名单命名空间（玩家自建傀儡/友好生物等）
				if (EXCLUDED_NAMESPACES.contains(namespace)) continue;
				// 排除黑名单实体ID
				if (EXCLUDED_ENTITY_IDS.contains(fullId)) continue;
				// 仅处理已知模组（排除原版 minecraft）
				if (!KNOWN_MOD_NAMESPACES.contains(namespace)) continue;
				// 路径需包含Boss关键词
				if (!isBossPath(path)) continue;
				// 分级（优先硬编码表，其次关键词）
				Tier t = classifyModdedBoss(namespace, path, fullId);
				cache.get(t).add(fullId);
			}
		} catch (Throwable t) {
			com.randomsurprise.RandomSurpriseMod.LOGGER.warn("[征召战场] 解析模组Boss失败: {}", t.getMessage());
		}

		moddedBossCache = cache;
		resolved = true;
		com.randomsurprise.RandomSurpriseMod.LOGGER.info("[征召战场] 模组Boss解析完成：T1={} T2={} T3={} T4={} T5={}",
				cache.get(Tier.TIER_1).size(), cache.get(Tier.TIER_2).size(),
				cache.get(Tier.TIER_3).size(), cache.get(Tier.TIER_4).size(),
				cache.get(Tier.TIER_5).size());
	}

	/** 判断路径是否含Boss关键词 */
	private static boolean isBossPath(String path) {
		for (String kw : BOSS_KEYWORDS) {
			if (path.contains(kw)) return true;
		}
		return false;
	}

	/** 模组Boss分级：优先硬编码表，其次关键词 */
	private static Tier classifyModdedBoss(String namespace, String path, String fullId) {
		// 1. 优先查硬编码表
		Tier hardcoded = HARDCODED_BOSS_TIERS.get(fullId);
		if (hardcoded != null) return hardcoded;
		// 2. 高难度关键词 → Tier 5
		for (String kw : HARD_KEYWORDS) {
			if (path.contains(kw)) return Tier.TIER_5;
		}
		// 3. 中难度关键词 → Tier 3
		for (String kw : MEDIUM_KEYWORDS) {
			if (path.contains(kw)) return Tier.TIER_3;
		}
		// 4. 低难度关键词 → Tier 2
		for (String kw : EASY_KEYWORDS) {
			if (path.contains(kw)) return Tier.TIER_2;
		}
		// 默认 Tier 2
		return Tier.TIER_2;
	}

	/**
	 * 获取Boss的Tier分级（公开接口）
	 * @param entityId 实体ID字符串（namespace:path）
	 * @return Tier，非Boss返回null
	 */
	public static Tier getBossTier(String entityId) {
		if (entityId == null) return null;
		// 原版Boss
		for (Map.Entry<Tier, List<String>> entry : VANILLA_BOSSES.entrySet()) {
			if (entry.getValue().contains(entityId)) return entry.getKey();
		}
		// 排除黑名单
		if (EXCLUDED_ENTITY_IDS.contains(entityId)) return null;
		String namespace = getNs(entityId);
		if (EXCLUDED_NAMESPACES.contains(namespace)) return null;
		// 硬编码表
		Tier hardcoded = HARDCODED_BOSS_TIERS.get(entityId);
		if (hardcoded != null) return hardcoded;
		// 模组Boss关键词
		String path = getPathStr(entityId);
		if (KNOWN_MOD_NAMESPACES.contains(namespace) && isBossPath(path)) {
			return classifyModdedBoss(namespace, path, entityId);
		}
		return null;
	}

	/** 从实体ID提取命名空间 */
	private static String getNs(String entityId) {
		int idx = entityId.indexOf(':');
		return idx > 0 ? entityId.substring(0, idx) : "minecraft";
	}

	/** 从实体ID提取路径 */
	private static String getPathStr(String entityId) {
		int idx = entityId.indexOf(':');
		return idx > 0 ? entityId.substring(idx + 1) : entityId;
	}

	// ===== 公共查询方法 =====

	/**
	 * 获取指定难度的所有Boss实体ID（原版+模组）
	 * 难度越高，从越多Tier中选取（向下兼容包含低Tier）
	 */
	public static List<String> getBossesForDifficulty(int difficulty) {
		if (!resolved) resolveModdedBosses();
		Tier target = Tier.forDifficulty(difficulty);
		List<String> result = new ArrayList<>();
		// 原版Boss：取目标Tier及更低Tier（增加多样性）
		for (Tier t : Tier.values()) {
			if (t.minDifficulty <= target.minDifficulty) {
				result.addAll(VANILLA_BOSSES.getOrDefault(t, List.of()));
			}
		}
		// 模组Boss：取目标Tier及更低Tier
		for (Tier t : Tier.values()) {
			if (t.minDifficulty <= target.minDifficulty) {
				result.addAll(moddedBossCache.getOrDefault(t, List.of()));
			}
		}
		return result;
	}

	/**
	 * 获取决战Boss池（全部Boss，用于FINAL_BATTLE）
	 */
	public static List<String> getFinalBattleBosses() {
		if (!resolved) resolveModdedBosses();
		List<String> result = new ArrayList<>();
		for (Tier t : Tier.values()) {
			result.addAll(VANILLA_BOSSES.getOrDefault(t, List.of()));
			result.addAll(moddedBossCache.getOrDefault(t, List.of()));
		}
		// 过滤掉需要特殊方式击败的Boss
		result.removeIf(SPECIAL_DEFEAT_BOSSES::contains);
		return result;
	}

	/**
	 * 随机选一个Boss实体ID（指定难度）
	 * @return 实体ID字符串，若池为空返回 "minecraft:ravager" 兜底
	 */
	public static String randomBoss(int difficulty) {
		List<String> pool = getBossesForDifficulty(difficulty);
		// 过滤掉需要特殊方式击败的Boss（战场中无法正常击杀）
		pool.removeIf(SPECIAL_DEFEAT_BOSSES::contains);
		if (pool.isEmpty()) return "minecraft:ravager";  // 兜底
		return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
	}

	/**
	 * 根据实体ID字符串解析为 EntityType
	 * @return EntityType，解析失败返回null
	 */
	public static EntityType<?> resolveEntityType(String entityId) {
		try {
			ResourceLocation id = new ResourceLocation(entityId);
			return BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
		} catch (Throwable t) {
			return null;
		}
	}

	/**
	 * 判断实体ID是否为Boss（原版Boss 或 已知模组Boss关键词）
	 * 供战场系统判定Boss死亡用
	 */
	public static boolean isBoss(String entityId) {
		if (entityId == null) return false;
		// 排除黑名单实体ID
		if (EXCLUDED_ENTITY_IDS.contains(entityId)) return false;
		// 原版Boss
		for (List<String> list : VANILLA_BOSSES.values()) {
			if (list.contains(entityId)) return true;
		}
		// 模组Boss
		int idx = entityId.indexOf(':');
		String namespace = idx > 0 ? entityId.substring(0, idx) : "minecraft";
		String path = idx > 0 ? entityId.substring(idx + 1) : entityId;
		// 排除黑名单命名空间
		if (EXCLUDED_NAMESPACES.contains(namespace)) return false;
		if (KNOWN_MOD_NAMESPACES.contains(namespace)) {
			return isBossPath(path);
		}
		return false;
	}

	/** 强制重新解析模组Boss（用于模组动态加载场景，一般不需要） */
	public static void invalidateCache() {
		resolved = false;
		moddedBossCache = null;
		hostileResolved = false;
		hostileMobCache = null;
	}

	/**
	 * 解析所有已安装模组的敌对生物（非Boss），用于战场小怪生成
	 * 遍历 BuiltInRegistries.ENTITY_TYPE，匹配已知模组命名空间
	 */
	private static synchronized void resolveHostileMobs() {
		if (hostileResolved) return;
		List<String> cache = new ArrayList<>(VANILLA_HOSTILE_MOBS);

		try {
			for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
				ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
				if (id == null) continue;
				String namespace = id.getNamespace();
				String path = id.getPath();
				String fullId = id.toString();
				// 排除原版（已在VANILLA_HOSTILE_MOBS中）
				if ("minecraft".equals(namespace)) continue;
				// 排除黑名单命名空间
				if (EXCLUDED_NAMESPACES.contains(namespace)) continue;
				// 仅处理已知模组
				if (!KNOWN_MOD_NAMESPACES.contains(namespace)) continue;
				// 排除Boss类实体（已在Boss池中）
				if (isBoss(fullId)) continue;
				// 排除友好生物关键词
				if (isFriendlyPath(path)) continue;
				// 包含敌对关键词或属于敌对生物类
				if (isHostilePath(path) || isHostileEntityType(type)) {
					cache.add(fullId);
				}
			}
		} catch (Throwable t) {
			com.randomsurprise.RandomSurpriseMod.LOGGER.warn("[征召战场] 解析模组敌对生物失败: {}", t.getMessage());
		}

		hostileMobCache = cache;
		hostileResolved = true;
		com.randomsurprise.RandomSurpriseMod.LOGGER.info("[征召战场] 敌对生物解析完成：总数={}", cache.size());
	}

	/** 判断路径是否含友好生物关键词 */
	private static boolean isFriendlyPath(String path) {
		String lower = path.toLowerCase();
		return lower.contains("animal") || lower.contains("pet") || lower.contains("friend")
				|| lower.contains("rabbit") || lower.contains("chicken") || lower.contains("cow")
				|| lower.contains("pig") || lower.contains("sheep") || lower.contains("horse")
				|| lower.contains("cat") || lower.contains("dog") || lower.contains("bird")
				|| lower.contains("fish") || lower.contains("bee") || lower.contains("turtle")
				|| lower.contains("fox") || lower.contains("panda") || lower.contains("llama")
				|| lower.contains("parrot") || lower.contains("dolphin") || lower.contains("ocelot")
				|| lower.contains("wolf") || lower.contains("iron_golem") || lower.contains("snow_golem")
				|| lower.contains("villager") || lower.contains("wandering_trader") || lower.contains("player");
	}

	/** 判断路径是否含敌对生物关键词 */
	private static boolean isHostilePath(String path) {
		String lower = path.toLowerCase();
		return lower.contains("zombie") || lower.contains("skeleton") || lower.contains("creeper")
				|| lower.contains("spider") || lower.contains("enderman") || lower.contains("blaze")
				|| lower.contains("ghast") || lower.contains("slime") || lower.contains("witch")
				|| lower.contains("phantom") || lower.contains("wither") || lower.contains("ender")
				|| lower.contains("warden") || lower.contains("ravager") || lower.contains("guardian")
				|| lower.contains("vex") || lower.contains("vindicator") || lower.contains("evoker")
				|| lower.contains("pillager") || lower.contains("stray") || lower.contains("husk")
				|| lower.contains("drowned") || lower.contains("silverfish") || lower.contains("magma")
				|| lower.contains("beast") || lower.contains("demon") || lower.contains("devil")
				|| lower.contains("imp") || lower.contains("ghoul") || lower.contains("wraith")
				|| lower.contains("specter") || lower.contains("troll") || lower.contains("orc")
				|| lower.contains("goblin") || lower.contains("monster") || lower.contains("infernal")
				|| lower.contains("mutant") || lower.contains("chaos") || lower.contains("ender")
				|| lower.contains("cave") || lower.contains("dark") || lower.contains("shadow");
	}

	/** 判断实体类型是否为敌对生物（不创建实体实例，避免传入 null Level 导致 NPE） */
	private static boolean isHostileEntityType(EntityType<?> type) {
		try {
			return type.getCategory() == net.minecraft.world.entity.MobCategory.MONSTER;
		} catch (Throwable t) {
			return false;
		}
	}

	/**
	 * 获取所有敌对生物列表（原版+模组）
	 * @return 实体ID列表
	 */
	public static List<String> getHostileMobs() {
		if (!hostileResolved) resolveHostileMobs();
		return hostileMobCache;
	}

	/**
	 * 随机选一个普通敌对生物
	 * @return 实体ID字符串
	 */
	public static String randomHostileMob() {
		List<String> pool = getHostileMobs();
		if (pool.isEmpty()) return "minecraft:zombie";
		return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
	}
}
