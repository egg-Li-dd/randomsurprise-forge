package com.randomsurprise;

import com.randomsurprise.affix.PlayerAffixManager;
import com.randomsurprise.battlefield.BattlefieldManager;
import com.randomsurprise.battlefield.BossPool;
import com.randomsurprise.battlefield.DownedStateManager;
import com.randomsurprise.battlefield.GameState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 白天敌对生物生成管理器 v2
 *
 * 优化要点：
 * 1. Boss 排除：避免生成凋零/末影龙/监守者等 Boss 级生物
 * 2. 类型分层：基础/中级/高级三层，按游戏进度动态调整权重分布
 * 3. 位置算法优化：光照/坡度/聚集检测/地表精准定位
 * 4. 生成冷却：每玩家独立冷却，防止短时间内大量生成
 * 5. 多级上限：玩家附近/局部区域同类型/全局软上限
 * 6. 动态规则：玩家等级/游戏进度/战场难度/游戏状态/天气/倒地状态
 * 7. 系统协同：征召战场进行中暂停主世界生成、入侵态大幅增加、倒地玩家保护
 */
public class DaytimeSpawnManager {

	/** 生成检查间隔（tick），每 20 tick (1秒) 检查一次 */
	private static final int SPAWN_INTERVAL_TICKS = 20;
	private static int tickCounter = 0;

	/** 已缓存的敌对生物类型列表（分层缓存，延迟初始化） */
	private static List<EntityType<? extends Mob>> tier1Cache;   // 基础（原版普通）
	private static List<EntityType<? extends Mob>> tier2Cache;   // 中级（原版进阶）
	private static List<EntityType<? extends Mob>> tier3Cache;   // 高级（模组/特殊）
	private static boolean cacheResolved = false;

	/** 每玩家最后生成时间（tick），用于冷却机制 */
	private static final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();

	/** 已知生物模组命名空间（用于识别模组敌对生物） */
	private static final Set<String> KNOWN_MOD_NAMESPACES = Set.of(
			"alexsmobs", "mowziesmobs", "born_in_chaos", "cataclysm",
			"le_enders_cataclysm", "bosses_of_mass_destruction", "blue_skies",
			"twilightforest", "aether", "betteranimalsplus", "naturalist",
			"crittersandcompanions", "decorative_blocks", "illage_and_spillage",
			"mutantmonsters", "cave_dweller", "multigolem", "enderzoology",
			"friendsandfoes"
	);

	/** 敌对关键词 */
	private static final Set<String> HOSTILE_KEYWORDS = Set.of(
			"boss", "hostile", "monster", "demon", "dragon", "evil",
			"ghost", "spirit", "wraith", "phantom", "shadow", "dark",
			"warrior", "knight", "mage", "witch", "necromancer",
			"horror", "beast", "abomination", "construct", "golem",
			"raider", "pillager", "villain", "enemy", "aggressive",
			"dweller", "giant", "wildfire", "illusioner", "mosco",
			"maw", "wroughtnaut", "zombie", "skeleton", "spider",
			"creeper", "enderman", "husk", "stray",
			"crocodile", "mosquito", "serpent", "stalker", "mantis",
			"redcap", "kobold", "beetle", "hedge", "minotaur", "slime",
			"concussion", "fallen", "wizard", "scarlet", "corpse",
			"magispeller", "blastfinder", "tremozzarella",
			"ancient", "specter", "fiend", "devil",
			"hunter", "predator", "viper", "scorpion", "mimic"
	);

	/** 排除的命名空间（被动/友好生物模组） */
	private static final Set<String> EXCLUDED_NAMESPACES = Set.of(
			"modulargolems", "happyghastmod", "more_critters", "morecritters"
	);

	/** Tier1 基础原版敌对生物（早期游戏主要生成） */
	private static final Set<String> TIER1_IDS = Set.of(
			"minecraft:zombie", "minecraft:skeleton", "minecraft:creeper",
			"minecraft:spider", "minecraft:husk", "minecraft:stray",
			"minecraft:zombie_villager", "minecraft:drowned"
	);

	/** Tier2 中级原版敌对生物（中期开始增多） */
	private static final Set<String> TIER2_IDS = Set.of(
			"minecraft:cave_spider", "minecraft:silverfish", "minecraft:endermite",
			"minecraft:witch", "minecraft:slime", "minecraft:phantom",
			"minecraft:pillager", "minecraft:vindicator", "minecraft:evoker",
			"minecraft:blaze", "minecraft:ghast", "minecraft:magma_cube",
			"minecraft:wither_skeleton", "minecraft:zombified_piglin",
			"minecraft:hoglin", "minecraft:piglin", "minecraft:piglin_brute",
			"minecraft:shulker", "minecraft:guardian"
	);

	// ===== 核心入口 =====

	/**
	 * 服务端 Tick 处理 - 定期触发生成
	 */
	public static void onServerTick(MinecraftServer server) {
		if (!SurpriseConfig.isDaytimeSpawningEnabled()) return;

		tickCounter++;
		if (tickCounter < SPAWN_INTERVAL_TICKS) return;
		tickCounter = 0;

		for (ServerLevel level : server.getAllLevels()) {
			// 固定时间维度（如下界）跳过
			if (level.dimensionType().hasFixedTime()) continue;
			if (!level.isDay()) continue;
			if (level.players().isEmpty()) continue;

			// 战场进行中：跳过主世界生成（玩家在战场维度，避免双重压力）
			if (BattlefieldManager.getState().isBattlefieldActive()) continue;

			spawnHostileMobs(level);
		}
	}

	/**
	 * 在一个世界中尝试生成敌对生物
	 */
	private static void spawnHostileMobs(ServerLevel level) {
		double ratio = getCurrentDaytimeSpawnRatio(level);
		if (ratio <= 0.0) return;

		List<ServerPlayer> players = level.players();
		if (players.isEmpty()) return;

		// 解析分层生物列表
		List<EntityType<? extends Mob>> tier1 = getTier1Mobs();
		List<EntityType<? extends Mob>> tier2 = getTier2Mobs();
		List<EntityType<? extends Mob>> tier3 = getTier3Mobs();
		if (tier1.isEmpty() && tier2.isEmpty() && tier3.isEmpty()) return;

		long currentTick = level.getGameTime();
		int maxNear = SurpriseConfig.getDaytimeMaxHostilesNearPlayer();
		int globalCap = SurpriseConfig.getDaytimeGlobalSoftCap();

		// 全局软上限：超过则按概率跳过整个世界的生成
		long globalCount = countGlobalHostiles(level);
		if (globalCount >= globalCap) {
			double skipChance = Math.min(0.9, (globalCount - globalCap) / (double) globalCap);
			if (ThreadLocalRandom.current().nextDouble() < skipChance) return;
		}

		for (ServerPlayer player : players) {
			// 冷却检查
			Long lastSpawn = playerCooldowns.get(player.getUUID());
			int cooldown = SurpriseConfig.getDaytimeSpawnCooldownTicks();
			if (lastSpawn != null && (currentTick - lastSpawn) < cooldown) continue;

			// 倒地玩家保护：减少附近生成
			boolean playerDowned = DownedStateManager.isDowned(player.getUUID());
			if (playerDowned && SurpriseConfig.isDaytimeDownedProtectionEnabled()) {
				// 倒地玩家周围仅 20% 概率继续生成，且数量减半
				if (ThreadLocalRandom.current().nextDouble() > 0.2) continue;
			}

			// 玩家附近已有数量
			long nearby = countNearbyHostiles(level, player);
			if (nearby >= maxNear) continue;

			// 计算动态生成倍率
			double dynamicMult = computeDynamicMultiplier(level, player);

			// 基础生成尝试次数（每1秒0.5次 × 比例 × 动态倍率）
			double attempts = 0.5 * ratio * dynamicMult;

			int wholeAttempts = (int) Math.floor(attempts);
			double fractional = attempts - wholeAttempts;
			int totalToSpawn = wholeAttempts;
			if (ThreadLocalRandom.current().nextDouble() < fractional) totalToSpawn++;

			// 倒地保护：数量减半
			if (playerDowned && SurpriseConfig.isDaytimeDownedProtectionEnabled()) {
				totalToSpawn = totalToSpawn / 2;
			}

			// 容量限制
			int remaining = (int) (maxNear - nearby);
			totalToSpawn = Math.min(totalToSpawn, remaining);
			if (totalToSpawn <= 0) continue;

			// 根据游戏进度获取分层权重
			long day = level.getDayTime() / 24000;
			double[] weights = getTierWeights(day);

			int spawned = 0;
			for (int i = 0; i < totalToSpawn; i++) {
				EntityType<? extends Mob> type = pickMobByTier(tier1, tier2, tier3, weights);
				if (type == null) continue;
				if (trySpawnOneMob(level, player, type, tier1, tier2, tier3)) {
					spawned++;
				}
			}

			// 仅在实际生成后更新冷却
			if (spawned > 0) {
				playerCooldowns.put(player.getUUID(), currentTick);
			}
		}
	}

	// ===== 动态规则 =====

	/**
	 * 计算动态生成倍率（基于游戏状态/天气/玩家等级/进度等）
	 */
	private static double computeDynamicMultiplier(ServerLevel level, ServerPlayer player) {
		if (!SurpriseConfig.isDaytimeDynamicRulesEnabled()) return 1.0;

		double mult = 1.0;

		// 1. 游戏状态影响
		GameState state = BattlefieldManager.getState();
		if (state == GameState.WORLD_INVASION) {
			mult *= 1.5; // 入侵态：白天也疯狂
		} else if (state.isDoomsday()) {
			mult *= 1.3; // 末日态：增加压力
		} else if (state == GameState.FINAL_BATTLE) {
			mult *= 1.2;
		}

		// 2. 天气影响
		if (SurpriseConfig.isDaytimeWeatherEffectEnabled()) {
			if (level.isThundering()) {
				mult *= 1.25;
			} else if (level.isRaining()) {
				mult *= 1.10;
			}
		}

		// 3. 玩家等级影响（高等级玩家面对更多挑战，上限 +30%）
		int playerLevel = player.experienceLevel;
		mult *= 1.0 + Math.min(0.3, playerLevel / 100.0);

		// 4. 征召战场难度影响（完成的战场越多，世界越危险，上限 +50%）
		int bfDifficulty = BattlefieldManager.getBattlefieldDifficulty();
		mult *= 1.0 + Math.min(0.5, bfDifficulty / 20.0);

		// 5. 全局坏词条数量（世界越凶险，白天怪物越多，上限 +30%）
		int globalBadAffix = PlayerAffixManager.getGlobalBadAffixCount();
		mult *= 1.0 + Math.min(0.3, globalBadAffix / 20.0);

		// 6. 玩家好词条数量（玩家越强，挑战越多，上限 +20%）
		try {
			int goodAffix = (int) PlayerAffixManager.getAffixes(player.getUUID()).stream()
					.filter(a -> a != null && a.isGood()).count();
			mult *= 1.0 + Math.min(0.2, goodAffix / 10.0);
		} catch (Exception ignored) {}

		// 上下限保护
		return Math.max(0.1, Math.min(3.0, mult));
	}

	/**
	 * 根据游戏天数获取分层权重 [tier1, tier2, tier3]
	 * 早期：基础多，高级少
	 * 中期：均衡
	 * 后期：高级增多
	 * v10: 随时间变化更明显，高等级生物更早出现
	 */
	private static double[] getTierWeights(long day) {
		if (day < 3) {
			return new double[]{0.90, 0.10, 0.00};
		} else if (day < 7) {
			return new double[]{0.70, 0.25, 0.05};
		} else if (day < 14) {
			return new double[]{0.55, 0.35, 0.10};
		} else if (day < 21) {
			return new double[]{0.45, 0.38, 0.17};
		} else if (day < 30) {
			return new double[]{0.38, 0.40, 0.22};
		} else if (day < 45) {
			return new double[]{0.32, 0.40, 0.28};
		} else if (day < 60) {
			return new double[]{0.28, 0.40, 0.32};
		} else if (day < 90) {
			return new double[]{0.24, 0.38, 0.38};
		} else {
			return new double[]{0.20, 0.35, 0.45};
		}
	}

	/**
	 * 按分层权重随机选取一种生物
	 */
	private static EntityType<? extends Mob> pickMobByTier(
			List<EntityType<? extends Mob>> tier1,
			List<EntityType<? extends Mob>> tier2,
			List<EntityType<? extends Mob>> tier3,
			double[] weights) {
		// 过滤空分层，重新归一化权重
		double w1 = tier1.isEmpty() ? 0 : weights[0];
		double w2 = tier2.isEmpty() ? 0 : weights[1];
		double w3 = tier3.isEmpty() ? 0 : weights[2];
		double total = w1 + w2 + w3;
		if (total <= 0) return null;

		double r = ThreadLocalRandom.current().nextDouble() * total;
		if (r < w1) return tier1.get(ThreadLocalRandom.current().nextInt(tier1.size()));
		r -= w1;
		if (r < w2) return tier2.get(ThreadLocalRandom.current().nextInt(tier2.size()));
		return tier3.get(ThreadLocalRandom.current().nextInt(tier3.size()));
	}

	// ===== 生成位置算法 =====

	/**
	 * 在玩家周围尝试生成一只敌对生物
	 */
	private static boolean trySpawnOneMob(ServerLevel level, ServerPlayer player,
											EntityType<? extends Mob> entityType,
											List<EntityType<? extends Mob>> tier1,
											List<EntityType<? extends Mob>> tier2,
											List<EntityType<? extends Mob>> tier3) {
		int minDist = SurpriseConfig.getDaytimeMinSpawnDistance();
		int maxDist = SurpriseConfig.getDaytimeMaxSpawnDistance();
		if (maxDist <= minDist) maxDist = minDist + 8;

		// 尝试 3 次找到合适位置
		for (int attempt = 0; attempt < 3; attempt++) {
			double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
			double distance = minDist + ThreadLocalRandom.current().nextDouble() * (maxDist - minDist);

			int x = (int) Math.round(player.getX() + Math.cos(angle) * distance);
			int z = (int) Math.round(player.getZ() + Math.sin(angle) * distance);

			BlockPos pos = findSpawnPos(level, x, z, entityType);
			if (pos == null) continue;

			if (!isValidSpawnPosition(level, pos, entityType)) continue;

			// 局部区域同类型上限检查
			if (exceedsLocalTypeCap(level, pos, entityType)) continue;

			try {
				Mob mob = entityType.create(level);
				if (mob == null) return false;

				mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

				// 限制瞬时移动到生成点附近（避免卡在方块里）
				mob.yRotO = ThreadLocalRandom.current().nextFloat() * 360F;
				mob.setYRot(mob.yRotO);

				level.addFreshEntityWithPassengers(mob);
				return true;
			} catch (Exception e) {
				return false;
			}
		}
		return false;
	}

	/**
	 * 精准定位地表 Y 坐标（使用 MOTION_BLOCKING 高度图）
	 */
	private static BlockPos findSpawnPos(ServerLevel level, int x, int z, EntityType<?> type) {
		try {
			int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			if (height < level.getMinBuildHeight() + 1 || height > level.getMaxBuildHeight() - 1) return null;
			return new BlockPos(x, height, z);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * 检查位置是否适合生成（光照/坡度/空间/流体/难度）
	 */
	private static boolean isValidSpawnPosition(ServerLevel level, BlockPos pos,
												EntityType<? extends Mob> entityType) {
		try {
			if (pos.getY() < level.getMinBuildHeight() + 1 || pos.getY() > level.getMaxBuildHeight() - 1) {
				return false;
			}

			String typeName = getEntityTypeName(entityType);
			boolean canFly = isFlyingMob(typeName);
			boolean isSmall = isSmallMob(typeName);
			boolean isWater = isWaterMob(typeName);

			// 下方实心方块（飞行生物除外）
			if (!canFly) {
				BlockState below = level.getBlockState(pos.below());
				if (!below.entityCanStandOn(level, pos.below(), null)) return false;
			}

			// 生成位置无碰撞
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;

			// 上方空间（小生物除外）
			if (!isSmall) {
				if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) return false;
			}

			// 流体检查（水生生物除外）
			if (!isWater && !level.getFluidState(pos).isEmpty()) return false;

			// 光照检查：白天需在阴影/洞穴中生成
			int maxLight = SurpriseConfig.getDaytimeMaxLightLevel();
			if (maxLight < 15) {
				int brightness = level.getMaxLocalRawBrightness(pos);
				if (brightness > maxLight) return false;
			}

			// 坡度检查：避免在陡坡/悬崖生成
			int maxSlope = SurpriseConfig.getDaytimeMaxSlopeBlocks();
			if (maxSlope > 0 && !canFly) {
				if (isTooSteep(level, pos, maxSlope)) return false;
			}

			// 和平模式不生成
			if (level.getDifficulty().getId() == 0) return false;

			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * 检查 3x3 区域内高度差是否过大（陡坡/悬崖）
	 */
	private static boolean isTooSteep(ServerLevel level, BlockPos pos, int maxSlope) {
		int baseY = pos.getY();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) continue;
				int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX() + dx, pos.getZ() + dz);
				if (Math.abs(h - baseY) > maxSlope) return true;
			}
		}
		return false;
	}

	/**
	 * 检查局部区域内同类型生物是否超过上限（防聚集）
	 */
	private static boolean exceedsLocalTypeCap(ServerLevel level, BlockPos pos, EntityType<?> type) {
		int radius = SurpriseConfig.getDaytimeLocalAreaRadius();
		int cap = SurpriseConfig.getDaytimeMaxPerTypeInLocalArea();
		try {
			long count = level.getEntitiesOfClass(Mob.class,
					new net.minecraft.world.phys.AABB(pos).inflate(radius),
					m -> m.getType() == type).size();
			return count >= cap;
		} catch (Exception e) {
			return false;
		}
	}

	// ===== 计数辅助 =====

	private static long countNearbyHostiles(ServerLevel level, ServerPlayer player) {
		return level.getEntitiesOfClass(Mob.class,
				player.getBoundingBox().inflate(64.0),
				mob -> isHostileMob(mob.getType())).size();
	}

	private static long countGlobalHostiles(ServerLevel level) {
		// 使用整体实体计数（近似），避免遍历所有实体导致性能问题
		try {
			return level.getEntitiesOfClass(Mob.class,
					new net.minecraft.world.phys.AABB(level.getMinBuildHeight(), level.getMinBuildHeight(), level.getMinBuildHeight(),
							level.getMaxBuildHeight(), level.getMaxBuildHeight(), level.getMaxBuildHeight()),
					mob -> isHostileMob(mob.getType())).size();
		} catch (Exception e) {
			return 0;
		}
	}

	// ===== 类型识别辅助 =====

	private static String getEntityTypeName(EntityType<?> type) {
		ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		return id != null ? id.toString() : "";
	}

	private static boolean isFlyingMob(String typeName) {
		return typeName.contains("phantom") || typeName.contains("ghast")
				|| typeName.contains("blaze") || typeName.contains("vex")
				|| typeName.contains("wither") || typeName.contains("dragon")
				|| typeName.contains("bee");
	}

	private static boolean isSmallMob(String typeName) {
		return typeName.contains("spider") || typeName.contains("cave_spider")
				|| typeName.contains("silverfish") || typeName.contains("endermite")
				|| typeName.contains("bee") || typeName.contains("baby");
	}

	private static boolean isWaterMob(String typeName) {
		return typeName.contains("drowned") || typeName.contains("guardian")
				|| typeName.contains("elder_guardian") || typeName.contains("squid");
	}

	// ===== 比例与白天判定 =====

	/**
	 * 获取当前白天生成比例（0.0 ~ 1.0），1.0 表示与夜晚相同数量水平
	 */
	public static double getCurrentDaytimeSpawnRatio(ServerLevel level) {
		if (!SurpriseConfig.isDaytimeSpawningEnabled()) return 0.0;

		double initialRatio = SurpriseConfig.getDaytimeInitialSpawnRatio();
		if (!SurpriseConfig.isDaytimeSpawnRampUpEnabled()) return initialRatio;

		long dayTime = level.getDayTime();
		long currentDay = dayTime / 24000;
		int rampUpDays = SurpriseConfig.getDaytimeSpawnRampUpDays();

		if (currentDay >= rampUpDays) return 1.0;

		double progress = (double) currentDay / rampUpDays;
		return initialRatio + (1.0 - initialRatio) * progress;
	}

	/** 判断是否为白天（考虑维度） */
	public static boolean isDaytime(Level level) {
		if (level.dimensionType().hasFixedTime()) return false;
		return level.isDay();
	}

	// ===== 敌对生物识别 =====

	/**
	 * 判断一个生物是否为敌对生物（排除 Boss）
	 */
	public static boolean isHostileMob(EntityType<?> type) {
		if (type.getCategory() == MobCategory.MONSTER) return true;

		ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		if (id == null) return false;
		String namespace = id.getNamespace();
		String path = id.getPath();

		if (EXCLUDED_NAMESPACES.contains(namespace)) return false;
		if (KNOWN_MOD_NAMESPACES.contains(namespace)) {
			for (String kw : HOSTILE_KEYWORDS) {
				if (path.contains(kw)) return true;
			}
		}
		return false;
	}

	/**
	 * 判断是否为 Boss（用于排除）
	 */
	private static boolean isBossType(EntityType<?> type) {
		ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
		if (id == null) return false;
		return BossPool.isBoss(id.toString());
	}

	// ===== 分层缓存 =====

	public static List<EntityType<? extends Mob>> getTier1Mobs() {
		ensureCache();
		return tier1Cache;
	}

	public static List<EntityType<? extends Mob>> getTier2Mobs() {
		ensureCache();
		return tier2Cache;
	}

	public static List<EntityType<? extends Mob>> getTier3Mobs() {
		ensureCache();
		return tier3Cache;
	}

	/**
	 * 兼容旧 API：返回所有可生成的敌对生物
	 */
	public static List<EntityType<? extends Mob>> getHostileEntityTypes() {
		ensureCache();
		List<EntityType<? extends Mob>> all = new ArrayList<>();
		all.addAll(tier1Cache);
		all.addAll(tier2Cache);
		all.addAll(tier3Cache);
		return all;
	}

	private static void ensureCache() {
		if (cacheResolved) return;
		resolveCache();
	}

	private static synchronized void resolveCache() {
		if (cacheResolved) return;

		List<EntityType<? extends Mob>> t1 = new ArrayList<>();
		List<EntityType<? extends Mob>> t2 = new ArrayList<>();
		List<EntityType<? extends Mob>> t3 = new ArrayList<>();

		boolean excludeBosses = SurpriseConfig.isDaytimeExcludeBosses();

		for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
			if (id == null) continue;
			String fullId = id.toString();
			String namespace = id.getNamespace();
			String path = id.getPath();

			// 排除 Boss
			if (excludeBosses && isBossType(type)) continue;

			boolean isHostile = false;
			if (type.getCategory() == MobCategory.MONSTER) {
				isHostile = true;
			} else if (!EXCLUDED_NAMESPACES.contains(namespace)
					&& KNOWN_MOD_NAMESPACES.contains(namespace)) {
				for (String kw : HOSTILE_KEYWORDS) {
					if (path.contains(kw)) { isHostile = true; break; }
				}
			}
			if (!isHostile) continue;

			try {
				EntityType<? extends Mob> mobType = (EntityType<? extends Mob>) type;
				if (!mobType.canSummon()) continue;

				if (TIER1_IDS.contains(fullId)) {
					t1.add(mobType);
				} else if (TIER2_IDS.contains(fullId)) {
					t2.add(mobType);
				} else {
					// 模组生物或未分类的原版敌对生物归入 Tier3
					t3.add(mobType);
				}
			} catch (Exception ignored) {}
		}

		tier1Cache = t1;
		tier2Cache = t2;
		tier3Cache = t3;
		cacheResolved = true;
		RandomSurpriseMod.LOGGER.info("[白天生成v2] 已解析敌对生物分层：基础{} 中级{} 高级{}",
				t1.size(), t2.size(), t3.size());
	}

	/** 强制刷新缓存 */
	public static void invalidateCache() {
		cacheResolved = false;
		tier1Cache = null;
		tier2Cache = null;
		tier3Cache = null;
	}

	/** 玩家退出时清理冷却记录 */
	public static void onPlayerQuit(UUID playerId) {
		playerCooldowns.remove(playerId);
	}

	// ===== 状态查询（供命令系统使用） =====

	public static int getTier1Count() { ensureCache(); return tier1Cache.size(); }
	public static int getTier2Count() { ensureCache(); return tier2Cache.size(); }
	public static int getTier3Count() { ensureCache(); return tier3Cache.size(); }

	/**
	 * 获取指定玩家的冷却剩余 tick
	 */
	public static long getPlayerCooldownRemaining(ServerLevel level, UUID playerId) {
		Long last = playerCooldowns.get(playerId);
		if (last == null) return 0;
		long elapsed = level.getGameTime() - last;
		int cooldown = SurpriseConfig.getDaytimeSpawnCooldownTicks();
		return Math.max(0, cooldown - elapsed);
	}
}
