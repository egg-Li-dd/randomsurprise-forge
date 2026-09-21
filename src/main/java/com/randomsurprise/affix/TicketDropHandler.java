package com.randomsurprise.affix;

import com.randomsurprise.SurpriseConfig;
import com.randomsurprise.battlefield.BossPool;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.Set;

/**
 * 敌对生物死亡掉落
 *
 * 兼容性设计：
 * - 主判定：instanceof Enemy（自动兼容所有模组的敌对生物）
 * - 命名空间识别：识别常见生物模组的命名空间前缀
 * - 关键词匹配：实体名包含 boss/hostile/monster/demon 等关键词
 * - 配置项：用户可在 config/randomsurprise.json 自定义额外ID
 *
 * 掉落规则：
 * - 普通敌对 20% 掉落 1 张抽奖券（坏词条越多概率越高，上限+20%）
 * - Boss 必掉 3 张抽奖券 + 1 个清除药水（原版 Boss + 模组 Boss 均掉落）
 */
public class TicketDropHandler {
	private static final double BASE_NORMAL_CHANCE = 0.20;
	private static final double BASE_BOSS_CHANCE = 1.0;
	private static final int NORMAL_DROP_COUNT = 1;
	private static final double BONUS_PER_BAD_AFFIX = 0.02;
	private static final double MAX_BONUS = 0.20;

	/** Boss按Tier的抽奖券掉落数量 */
	private static final Map<BossPool.Tier, Integer> BOSS_TICKET_DROPS = Map.of(
			BossPool.Tier.TIER_1, 3,
			BossPool.Tier.TIER_2, 3,
			BossPool.Tier.TIER_3, 4,
			BossPool.Tier.TIER_4, 5,
			BossPool.Tier.TIER_5, 8
	);

	/** 模组敌对生物特色掉落表（命名空间 → 特色物品ID列表），5%概率掉一个 */
	private static final Map<String, String[]> MODDED_SPECIAL_DROPS = Map.of(
			"alexsmobs", new String[]{"alexsmobs:bear_dust", "alexsmobs:spike_leather", "alexsmobs:siderite"},
			"twilightforest", new String[]{"twilightforest:steeleaf_ingot", "twilightforest:fiery_ingot", "twilightforest:knightmetal_ingot"},
			"mowziesmobs", new String[]{"mowziesmobs:solar_dust", "mowziesmobs:naga_fang"},
			"enderzoology", new String[]{"enderzoology:ender_fragment", "enderzoology:wither_dust"},
			"born_in_chaos", new String[]{"born_in_chaos:soul_piece", "born_in_chaos:dark_magic"},
			"mutantmonsters", new String[]{"mutantmonsters:endersoul_fragment", "mutantmonsters:chemical_x"},
			"aether", new String[]{"aether:ambrosium_shard", "aether:zanite_gemstone"},
			"cataclysm", new String[]{"cataclysm:soul_mass"},
			"blue_skies", new String[]{"blue_skies:diopside_gem"},
			"friendsandfoes", new String[]{"friendsandfoes:crab_claw"}
	);
	private static final double SPECIAL_DROP_CHANCE = 0.05;

	/**
	 * 已知生物模组的命名空间前缀（这些模组的生物会触发兜底判定）
	 * 这些模组实现了 Enemy 接口的生物会自动支持，无需在此列出
	 * 此列表仅用于不实现 Enemy 接口但属于敌对生物的兜底识别
	 */
	private static final Set<String> KNOWN_MOB_MOD_NAMESPACES = Set.of(
			"alexsmobs",              // Alex's Mobs
			"mowziesmobs",            // Mowzie's Mobs
			"born_in_chaos",          // Born in Chaos
			"cataclysm",              // L_Ender's Cataclysm
			"le_enders_cataclysm",    // L_Ender's Cataclysm (旧ID)
			"bosses_of_mass_destruction", // Bosses of Mass Destruction
			"blue_skies",             // Blue Skies
			"twilightforest",         // Twilight Forest
			"aether",                 // The Aether
			"betteranimalsplus",      // Better Animals Plus
			"naturalist",             // Naturalist
			"crittersandcompanions",  // Critters and Companions
			"decorative_blocks",      // 装饰方块（含部分生物）
			"illage_and_spillage",    // Illage and Spillage
			"mutantmonsters",        // Mutant Monsters
			"cave_dweller",          // Cave Dweller (Fabric Port)
			"multigolem",            // MultiGolem
			"enderzoology",          // Ender Zoology
			"friendsandfoes"         // Friends&Foes
	);

	/** 实体名包含这些关键词的视为敌对（仅对已知模组的生物生效） */
	private static final Set<String> HOSTILE_KEYWORDS = Set.of(
			"boss", "hostile", "monster", "demon", "dragon", "evil",
			"ghost", "spirit", "wraith", "phantom", "shadow", "dark",
			"warrior", "knight", "mage", "witch", "necromancer",
			"horror", "beast", "abomination", "construct", "golem",
			"raider", "pillager", "villain", "enemy", "aggressive",
			"dweller", "giant", "wildfire", "illusioner",
			"mosco", "maw", "wroughtnaut"
	);

	/**
	 * 由主类 ServerLivingEntityEvents.AFTER_DEATH 调用
	 */
	public static void onEntityDeath(net.minecraft.world.entity.LivingEntity entity) {
		if (!(entity instanceof Mob mob)) return;
		if (mob.level().isClientSide()) return;

		// 获取实体ID（命名空间:路径）
		String entityId = getEntityId(mob);
		String namespace = getNamespace(entityId);
		String path = getPath(entityId);

		// 判定是否为敌对生物
		boolean isHostile = isHostile(mob, entityId, namespace, path);
		if (!isHostile) return;

		// 判定是否为Boss（统一使用 BossPool 判定）
		BossPool.Tier bossTier = BossPool.getBossTier(entityId);
		// 用户自定义Boss列表兜底
		if (bossTier == null && SurpriseConfig.getExtraBossEntityIds().contains(entityId)) {
			bossTier = BossPool.Tier.TIER_2;
		}
		boolean isBoss = bossTier != null;

		// 计算补偿后的掉落概率
		double bonusChance = computeBadAffixBonus();
		double chance;
		int count;
		if (isBoss) {
			chance = BASE_BOSS_CHANCE;
			count = BOSS_TICKET_DROPS.getOrDefault(bossTier, 3);
		} else {
			chance = Math.min(0.95, BASE_NORMAL_CHANCE + bonusChance);
			count = NORMAL_DROP_COUNT;
		}

		// 掉落抽奖券
		if (mob.getRandom().nextDouble() < chance) {
			ItemStack ticket = new ItemStack(ModItems.LOTTERY_TICKET.get(), count);
			dropItem(mob, ticket);
		}

		// 所有Boss（原版 + 模组）都掉落清除药水
		if (isBoss) {
			ItemStack potion = new ItemStack(ModItems.PURIFY_POTION.get(), 1);
			dropItem(mob, potion);
			// T4+ Boss 额外掉落稀有物品
			dropBossRareItem(mob, bossTier);
		}

		// 模组敌对生物特色掉落（5%概率）
		if (!isBoss) {
			dropModdedSpecialItem(mob, namespace);
		}
	}

	/** T4+Boss额外掉落稀有物品 */
	private static void dropBossRareItem(Mob mob, BossPool.Tier tier) {
		if (tier == BossPool.Tier.TIER_4) {
			// T4: 50% 下界之星, 30% 龙息, 20% 附魔金苹果
			double roll = mob.getRandom().nextDouble();
			if (roll < 0.50) dropItem(mob, new ItemStack(Items.NETHER_STAR));
			else if (roll < 0.80) dropItem(mob, new ItemStack(Items.DRAGON_BREATH));
			else dropItem(mob, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
		} else if (tier == BossPool.Tier.TIER_5) {
			// T5: 必掉下界之星 + 50% 附魔金苹果
			dropItem(mob, new ItemStack(Items.NETHER_STAR));
			if (mob.getRandom().nextDouble() < 0.50) {
				dropItem(mob, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
			}
		}
	}

	/** 模组敌对生物特色掉落（5%概率掉该模组的特色物品） */
	private static void dropModdedSpecialItem(Mob mob, String namespace) {
		String[] items = MODDED_SPECIAL_DROPS.get(namespace);
		if (items == null || items.length == 0) return;
		if (mob.getRandom().nextDouble() >= SPECIAL_DROP_CHANCE) return;
		// 随机选一个特色物品，动态解析
		String itemId = items[mob.getRandom().nextInt(items.length)];
		try {
			net.minecraft.resources.ResourceLocation rl = new net.minecraft.resources.ResourceLocation(itemId);
			net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
			if (item != null) {
				dropItem(mob, new ItemStack(item));
			}
		} catch (Throwable ignored) {}
	}

	/** 综合判定是否为敌对生物 */
	private static boolean isHostile(Mob mob, String entityId, String namespace, String path) {
		// 1. 主判定：实现 Enemy 接口（自动兼容所有模组）
		if (mob instanceof Enemy) return true;

		// 2. 用户自定义列表
		if (SurpriseConfig.getExtraHostileEntityIds().contains(entityId)) return true;

		// 3. 原版兜底判定（按路径匹配）
		if (namespace.equals("minecraft") && isVanillaHostile(path)) return true;

		// 4. 已知生物模组的兜底判定：命名空间在已知列表中 + 路径包含敌对关键词
		if (KNOWN_MOB_MOD_NAMESPACES.contains(namespace)) {
			for (String keyword : HOSTILE_KEYWORDS) {
				if (path.contains(keyword)) return true;
			}
		}
		return false;
	}

	/** 原版敌对生物路径判定 */
	private static boolean isVanillaHostile(String path) {
		return path.contains("zombie") || path.contains("skeleton")
				|| path.contains("creeper") || path.contains("spider")
				|| path.contains("enderman") || path.contains("witch")
				|| path.contains("phantom") || path.contains("pillager")
				|| path.contains("vindicator") || path.contains("evoker")
				|| path.contains("blaze") || path.contains("ghast")
				|| path.contains("wither") || path.contains("stray")
				|| path.contains("husk") || path.contains("drowned")
				|| path.contains("zombified_piglin") || path.contains("hoglin")
				|| path.contains("piglin") || path.contains("shulker")
				|| path.contains("silverfish") || path.contains("endermite")
				|| path.contains("guardian") || path.contains("ravager")
				|| path.contains("warden") || path.contains("ender_dragon");
	}

	/** 判定是否为Boss（原版 + 用户自定义 + 已知模组Boss关键词） */
	private static boolean isBossEntity(String entityId, Mob mob) {
		// 1. 原版Boss
		if (isVanillaBoss(entityId)) return true;

		// 2. 用户自定义Boss列表
		if (SurpriseConfig.getExtraBossEntityIds().contains(entityId)) return true;

		// 3. 已知模组Boss关键词判定（命名空间在已知列表中 + 路径包含 Boss 关键词）
		String namespace = getNamespace(entityId);
		String path = getPath(entityId);
		if (KNOWN_MOB_MOD_NAMESPACES.contains(namespace)) {
			return path.contains("boss") || path.contains("king")
					|| path.contains("queen") || path.contains("lord")
					|| path.contains("overlord") || path.contains("titan")
					|| path.contains("leviathan") || path.contains("dragon")
					|| path.contains("golem")     // MultiGolem 金属傀儡
					|| path.contains("dweller")   // Cave Dweller
					|| path.contains("giant")     // Giant Spawn 巨型僵尸
					|| path.contains("wildfire")  // Friends&Foes Wildfire
					|| path.contains("illusioner")// Friends&Foes Illusioner
					|| path.contains("mosco")     // Alex's Mobs Warped Mosco
					|| path.contains("maw")       // Mowzie's Mobs Frostmaw
					|| path.contains("wroughtnaut"); // Mowzie's Mobs Ferrous Wroughtnaut
		}
		return false;
	}

	/** 原版Boss判定（用于 isBossEntity 第一层判定） */
	private static boolean isVanillaBoss(String entityId) {
		return entityId.equals("minecraft:wither")
				|| entityId.equals("minecraft:ender_dragon")
				|| entityId.equals("minecraft:warden")
				|| entityId.equals("minecraft:elder_guardian")
				|| entityId.equals("minecraft:ravager");
	}

	/** 获取实体ID（命名空间:路径） */
	private static String getEntityId(Mob mob) {
		try {
			return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
					.getKey(mob.getType()).toString();
		} catch (Throwable t) {
			return mob.getType().toString();
		}
	}

	private static String getNamespace(String entityId) {
		int idx = entityId.indexOf(':');
		return idx > 0 ? entityId.substring(0, idx) : "minecraft";
	}

	private static String getPath(String entityId) {
		int idx = entityId.indexOf(':');
		return idx > 0 ? entityId.substring(idx + 1) : entityId;
	}

	/** 在死亡位置生成掉落物 */
	private static void dropItem(Mob mob, ItemStack stack) {
		try {
			if (mob.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
				mob.spawnAtLocation(stack, 0.0F);
			}
		} catch (Throwable e) {
			com.randomsurprise.RandomSurpriseMod.LOGGER.warn(
					"掉落物生成失败: {}", e.getMessage());
		}
	}

	/** 计算全服敌对词条带来的掉落概率补偿 */
	private static double computeBadAffixBonus() {
		int badCount = PlayerAffixManager.getGlobalBadAffixCount();
		return Math.min(MAX_BONUS, badCount * BONUS_PER_BAD_AFFIX);
	}
}
