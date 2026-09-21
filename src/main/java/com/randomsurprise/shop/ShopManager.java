package com.randomsurprise.shop;

import com.randomsurprise.RandomSurpriseMod;
import com.randomsurprise.affix.ModItems;
import com.randomsurprise.affix.MoneyBagItem;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 商店管理器
 * 商店分类标签：
 * 1. 抽奖券兑换 - 用各种货币兑换抽奖券
 * 2. 模组物品 - 守护护符、清除药水
 * 3. 强力附魔书 - 含多重高级附魔的附魔书（STORED_ENCHANTMENTS 组件）
 * 4. 稀有物品 - 不死图腾、鞘翅、下界之星等
 * 5. 武器装备 - 剑、镐、胸甲、弓等（无附魔）
 * 6. 消耗品 - 金苹果、末影珍珠、经验瓶等
 * 7. 资源兑换 - 5对矿物双向兑换
 * 8. 模组装备 - 已装模组（Alex's Mobs / Ender Zoology / Friends and Foes）的装备类物品
 */
public class ShopManager {

	/** 商店所有分类标签 */
	public static final String[] CATEGORIES = {
			"金币兑换", "模组物品", "强力附魔书", "稀有物品",
			"武器装备", "预附魔装备", "消耗品", "资源兑换", "模组装备", "稀有回收"
	};

	/** 附魔简写常量 */
	private static final String SHARPNESS_V = "minecraft:sharpness|5";
	private static final String SHARPNESS_IV = "minecraft:sharpness|4";
	private static final String UNBREAKING_III = "minecraft:unbreaking|3";
	private static final String EFFICIENCY_V = "minecraft:efficiency|5";
	private static final String FORTUNE_III = "minecraft:fortune|3";
	private static final String PROTECTION_IV = "minecraft:protection|4";
	private static final String FEATHER_FALLING_IV = "minecraft:feather_falling|4";
	private static final String LOOTING_III = "minecraft:looting|3";
	private static final String POWER_V = "minecraft:power|5";
	private static final String INFINITY = "minecraft:infinity|1";
	private static final String FLAME = "minecraft:flame|1";
	private static final String PUNCH_II = "minecraft:punch|2";
	private static final String QUICK_CHARGE_III = "minecraft:quick_charge|3";
	private static final String MULTISHOT = "minecraft:multishot|1";
	private static final String FIRE_ASPECT_II = "minecraft:fire_aspect|2";
	private static final String KNOCKBACK_II = "minecraft:knockback|2";
	private static final String LOYALTY_III = "minecraft:loyalty|3";
	private static final String MENDING = "minecraft:mending|1";
	// v3 新增附魔常量（用于强力附魔书）
	private static final String SILK_TOUCH = "minecraft:silk_touch|1";
	private static final String PIERCING_IV = "minecraft:piercing|4";
	private static final String CHANNELING = "minecraft:channeling|1";
	private static final String IMPALING_V = "minecraft:impaling|5";
	private static final String RESPIRATION_III = "minecraft:respiration|3";
	private static final String AQUA_AFFINITY = "minecraft:aqua_affinity|1";
	private static final String THORNS_III = "minecraft:thorns|3";
	private static final String BLAST_PROTECTION_IV = "minecraft:blast_protection|4";
	private static final String PROJECTILE_PROTECTION_IV = "minecraft:projectile_protection|4";
	private static final String FIRE_PROTECTION_IV = "minecraft:fire_protection|4";
	private static final String SWIFT_SNEAK_III = "minecraft:swift_sneak|3";
	private static final String DEPTH_STRIDER_III = "minecraft:depth_strider|3";
	private static final String FROST_WALKER_II = "minecraft:frost_walker|2";
	private static final String SOUL_SPEED_III = "minecraft:soul_speed|3";
	private static final String BANE_OF_ARTHROPODS_V = "minecraft:bane_of_arthropods|5";
	private static final String SMITE_V = "minecraft:smite|5";
	private static final String LUCK_OF_THE_SEA_III = "minecraft:luck_of_the_sea|3";
	private static final String LURE_III = "minecraft:lure|3";

	private static final List<ShopEntry> ENTRIES = new ArrayList<>();

	/** 玩家购买记录：UUID → (条目ID → 已购次数)，用于限购检查 */
	private static final Map<UUID, Map<String, Integer>> purchaseRecords = new HashMap<>();
	private static Path dataPath;

	/** 初始化（服务器启动时调用） */
	public static void init(MinecraftServer server) {
		dataPath = com.randomsurprise.WorldDataPath.getWorldDataPath(server, "randomsurprise_shop_records.json");
		purchaseRecords.clear();
		load();
		// 初始化稀有物品回收注册表（扫描所有注册物品，加载兑换配置）
		ExchangeRegistry.init();
		// 初始化兑换记录持久化
		ExchangeRecords.init(server);
	}

	/** 加载数据 */
	private static void load() {
		if (dataPath == null) return;
		try {
			if (Files.exists(dataPath)) {
				String content = Files.readString(dataPath);
				deserializePurchaseRecords(content);
			}
		} catch (Exception e) {
			com.randomsurprise.RandomSurpriseMod.LOGGER.warn("[商店] 加载购买记录失败: {}", e.getMessage());
		}
	}

	/** 保存数据 */
	public static void save() {
		if (dataPath == null) return;
		try {
			Files.createDirectories(dataPath.getParent());
			Files.writeString(dataPath, serializePurchaseRecords());
		} catch (IOException e) {
			com.randomsurprise.RandomSurpriseMod.LOGGER.error("[商店] 保存购买记录失败: {}", e.getMessage());
		}
		// 保存兑换记录
		ExchangeRecords.save();
	}

	static {
		// ========== 金币兑换（用资源换金币）==========
		ENTRIES.add(new ShopEntry("coin_from_ticket", ModItems.UNIVERSAL_COIN.get(), 5,
				ShopEntry.CurrencyType.LOTTERY_TICKET, 1, "金币兑换"));
		ENTRIES.add(new ShopEntry("coin_from_diamond", ModItems.UNIVERSAL_COIN.get(), 3,
				ShopEntry.CurrencyType.DIAMOND, 1, "金币兑换"));
		ENTRIES.add(new ShopEntry("coin_from_emerald", ModItems.UNIVERSAL_COIN.get(), 2,
				ShopEntry.CurrencyType.EMERALD, 1, "金币兑换"));
		ENTRIES.add(new ShopEntry("coin_from_netherite", ModItems.UNIVERSAL_COIN.get(), 15,
				ShopEntry.CurrencyType.NETHERITE_INGOT, 1, "金币兑换"));
		ENTRIES.add(new ShopEntry("coin_from_iron", ModItems.UNIVERSAL_COIN.get(), 1,
				ShopEntry.CurrencyType.IRON_INGOT, 4, "金币兑换"));
		ENTRIES.add(new ShopEntry("coin_from_gold", ModItems.UNIVERSAL_COIN.get(), 2,
				ShopEntry.CurrencyType.GOLD_INGOT, 4, "金币兑换"));
		ENTRIES.add(new ShopEntry("coin_from_coal", ModItems.UNIVERSAL_COIN.get(), 1,
				ShopEntry.CurrencyType.COAL, 8, "金币兑换"));
		ENTRIES.add(new ShopEntry("coin_from_redstone", ModItems.UNIVERSAL_COIN.get(), 1,
				ShopEntry.CurrencyType.REDSTONE, 16, "金币兑换"));
		ENTRIES.add(new ShopEntry("coin_from_lapis", ModItems.UNIVERSAL_COIN.get(), 1,
				ShopEntry.CurrencyType.LAPIS_LAZULI, 8, "金币兑换"));

		// ========== 模组物品 ==========
		ENTRIES.add(new ShopEntry("guardian_charm", ModItems.GUARDIAN_CHARM.get(), 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 25, "模组物品"));
		ENTRIES.add(new ShopEntry("purify_potion", ModItems.PURIFY_POTION.get(), 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 25, "模组物品"));
		ENTRIES.add(new ShopEntry("mob_clear_potion", ModItems.MOB_CLEAR_POTION.get(), 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 45, "模组物品"));
		ENTRIES.add(new ShopEntry("lottery_ticket_buy", ModItems.LOTTERY_TICKET.get(), 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 10, "模组物品"));
		ENTRIES.add(new ShopEntry("money_bag", ModItems.MONEY_BAG.get(), 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组物品"));

		// ========== 强力附魔书 ==========
		ENTRIES.add(new ShopEntry("book_god_sword", Items.ENCHANTED_BOOK, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 60, "强力附魔书",
				new String[]{SHARPNESS_V, SMITE_V, BANE_OF_ARTHROPODS_V, LOOTING_III,
						FIRE_ASPECT_II, KNOCKBACK_II, UNBREAKING_III, MENDING}));
		ENTRIES.add(new ShopEntry("book_god_pickaxe", Items.ENCHANTED_BOOK, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 60, "强力附魔书",
				new String[]{EFFICIENCY_V, FORTUNE_III, UNBREAKING_III, MENDING}));
		ENTRIES.add(new ShopEntry("book_god_axe", Items.ENCHANTED_BOOK, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 45, "强力附魔书",
				new String[]{SHARPNESS_V, EFFICIENCY_V, UNBREAKING_III, MENDING}));
		ENTRIES.add(new ShopEntry("book_god_bow", Items.ENCHANTED_BOOK, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 45, "强力附魔书",
				new String[]{POWER_V, INFINITY, FLAME, PUNCH_II, UNBREAKING_III, MENDING}));
		ENTRIES.add(new ShopEntry("book_god_crossbow", Items.ENCHANTED_BOOK, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 45, "强力附魔书",
				new String[]{QUICK_CHARGE_III, MULTISHOT, PIERCING_IV, UNBREAKING_III, MENDING}));
		ENTRIES.add(new ShopEntry("book_god_trident", Items.ENCHANTED_BOOK, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 50, "强力附魔书",
				new String[]{LOYALTY_III, CHANNELING, IMPALING_V, UNBREAKING_III, MENDING}));
		ENTRIES.add(new ShopEntry("book_god_armor", Items.ENCHANTED_BOOK, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 50, "强力附魔书",
				new String[]{PROTECTION_IV, BLAST_PROTECTION_IV, PROJECTILE_PROTECTION_IV,
						FIRE_PROTECTION_IV, THORNS_III, UNBREAKING_III, MENDING}));
		ENTRIES.add(new ShopEntry("book_god_boots", Items.ENCHANTED_BOOK, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 50, "强力附魔书",
				new String[]{FEATHER_FALLING_IV, DEPTH_STRIDER_III, FROST_WALKER_II,
						SOUL_SPEED_III, PROTECTION_IV, UNBREAKING_III, MENDING}));
		ENTRIES.add(new ShopEntry("book_god_helmet", Items.ENCHANTED_BOOK, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 40, "强力附魔书",
				new String[]{PROTECTION_IV, RESPIRATION_III, AQUA_AFFINITY,
						THORNS_III, UNBREAKING_III, MENDING}));
		ENTRIES.add(new ShopEntry("book_god_leggings", Items.ENCHANTED_BOOK, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 40, "强力附魔书",
				new String[]{PROTECTION_IV, SWIFT_SNEAK_III, THORNS_III, UNBREAKING_III, MENDING}));
		ENTRIES.add(new ShopEntry("book_god_fishing", Items.ENCHANTED_BOOK, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "强力附魔书",
				new String[]{LUCK_OF_THE_SEA_III, LURE_III, UNBREAKING_III, MENDING}));
		ENTRIES.add(new ShopEntry("book_mending", Items.ENCHANTED_BOOK, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "强力附魔书",
				new String[]{MENDING}));
		ENTRIES.add(new ShopEntry("book_fortune", Items.ENCHANTED_BOOK, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 15, "强力附魔书",
				new String[]{FORTUNE_III}));
		ENTRIES.add(new ShopEntry("book_silk_touch", Items.ENCHANTED_BOOK, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 15, "强力附魔书",
				new String[]{SILK_TOUCH}));

		// ========== 稀有物品 ==========
		ENTRIES.add(new ShopEntry("totem_of_undying", Items.TOTEM_OF_UNDYING, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 25, "稀有物品"));
		ENTRIES.add(new ShopEntry("netherite_ingot", Items.NETHERITE_INGOT, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "稀有物品"));
		ENTRIES.add(new ShopEntry("elytra", Items.ELYTRA, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 50, "稀有物品"));
		ENTRIES.add(new ShopEntry("dragon_breath", Items.DRAGON_BREATH, 4,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 15, "稀有物品"));
		ENTRIES.add(new ShopEntry("nether_star", Items.NETHER_STAR, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 40, "稀有物品"));
		ENTRIES.add(new ShopEntry("enchanted_golden_apple", Items.ENCHANTED_GOLDEN_APPLE, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "稀有物品"));
		ENTRIES.add(new ShopEntry("end_crystal", Items.END_CRYSTAL, 2,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 12, "稀有物品"));
		ENTRIES.add(new ShopEntry("dragon_egg", Items.DRAGON_EGG, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 80, "稀有物品"));
		ENTRIES.add(new ShopEntry("nether_star_bulk", Items.NETHER_STAR, 2,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 75, "稀有物品"));
		ENTRIES.add(new ShopEntry("elytra_bulk", Items.ELYTRA, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 50, "稀有物品"));

		// ========== 武器装备 ==========
		ENTRIES.add(new ShopEntry("diamond_sword", Items.DIAMOND_SWORD, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "武器装备"));
		ENTRIES.add(new ShopEntry("diamond_pickaxe", Items.DIAMOND_PICKAXE, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "武器装备"));
		ENTRIES.add(new ShopEntry("diamond_axe", Items.DIAMOND_AXE, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 16, "武器装备"));
		ENTRIES.add(new ShopEntry("diamond_shovel", Items.DIAMOND_SHOVEL, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 10, "武器装备"));
		ENTRIES.add(new ShopEntry("diamond_helmet", Items.DIAMOND_HELMET, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 14, "武器装备"));
		ENTRIES.add(new ShopEntry("diamond_chestplate", Items.DIAMOND_CHESTPLATE, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 22, "武器装备"));
		ENTRIES.add(new ShopEntry("diamond_leggings", Items.DIAMOND_LEGGINGS, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 18, "武器装备"));
		ENTRIES.add(new ShopEntry("diamond_boots", Items.DIAMOND_BOOTS, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 14, "武器装备"));
		ENTRIES.add(new ShopEntry("bow", Items.BOW, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 10, "武器装备"));
		ENTRIES.add(new ShopEntry("crossbow", Items.CROSSBOW, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 14, "武器装备"));
		ENTRIES.add(new ShopEntry("shield", Items.SHIELD, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 8, "武器装备"));
		ENTRIES.add(new ShopEntry("trident", Items.TRIDENT, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 40, "武器装备"));

		// ========== 预附魔装备（自带附魔的武器和防具）==========
		// 预附魔钻石剑：锋利V、抢夺III、火焰附加II、耐久III、经验修补
		ENTRIES.add(new ShopEntry("ench_diamond_sword", Items.DIAMOND_SWORD, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 75, "预附魔装备",
				new String[]{SHARPNESS_V, LOOTING_III, FIRE_ASPECT_II, UNBREAKING_III, MENDING}));
		// 预附魔钻石镐：效率V、时运III、耐久III、经验修补
		ENTRIES.add(new ShopEntry("ench_diamond_pickaxe", Items.DIAMOND_PICKAXE, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 70, "预附魔装备",
				new String[]{EFFICIENCY_V, FORTUNE_III, UNBREAKING_III, MENDING}));
		// 预附魔钻石斧：锋利V、效率V、耐久III、经验修补
		ENTRIES.add(new ShopEntry("ench_diamond_axe", Items.DIAMOND_AXE, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 55, "预附魔装备",
				new String[]{SHARPNESS_V, EFFICIENCY_V, UNBREAKING_III, MENDING}));
		// 预附魔弓：力量V、火矢、冲击II、耐久III、无限（无限与经验修补互斥）
		ENTRIES.add(new ShopEntry("ench_bow", Items.BOW, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 60, "预附魔装备",
				new String[]{POWER_V, FLAME, PUNCH_II, UNBREAKING_III, INFINITY}));
		// 预附魔弩：快速装填III、多重射击、耐久III、经验修补
		ENTRIES.add(new ShopEntry("ench_crossbow", Items.CROSSBOW, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 55, "预附魔装备",
				new String[]{QUICK_CHARGE_III, MULTISHOT, UNBREAKING_III, MENDING}));
		// 预附魔盾牌：耐久III、经验修补
		ENTRIES.add(new ShopEntry("ench_shield", Items.SHIELD, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 25, "预附魔装备",
				new String[]{UNBREAKING_III, MENDING}));
		// 预附魔钻石头盔：保护IV、水下呼吸III、水下速掘、耐久III、经验修补
		ENTRIES.add(new ShopEntry("ench_diamond_helmet", Items.DIAMOND_HELMET, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 45, "预附魔装备",
				new String[]{PROTECTION_IV, RESPIRATION_III, AQUA_AFFINITY, UNBREAKING_III, MENDING}));
		// 预附魔钻石胸甲：保护IV、荆棘III、耐久III、经验修补
		ENTRIES.add(new ShopEntry("ench_diamond_chestplate", Items.DIAMOND_CHESTPLATE, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 60, "预附魔装备",
				new String[]{PROTECTION_IV, THORNS_III, UNBREAKING_III, MENDING}));
		// 预附魔钻石护腿：保护IV、迅捷潜行III、耐久III、经验修补
		ENTRIES.add(new ShopEntry("ench_diamond_leggings", Items.DIAMOND_LEGGINGS, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 50, "预附魔装备",
				new String[]{PROTECTION_IV, SWIFT_SNEAK_III, UNBREAKING_III, MENDING}));
		// 预附魔钻石靴子：摔落保护IV、深海探索者III、保护IV、耐久III、经验修补
		ENTRIES.add(new ShopEntry("ench_diamond_boots", Items.DIAMOND_BOOTS, null, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 45, "预附魔装备",
				new String[]{FEATHER_FALLING_IV, DEPTH_STRIDER_III, PROTECTION_IV, UNBREAKING_III, MENDING}));

		// ========== 消耗品（含箭矢）==========
		ENTRIES.add(new ShopEntry("golden_apple", Items.GOLDEN_APPLE, 2,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 5, "消耗品"));
		ENTRIES.add(new ShopEntry("ender_pearl", Items.ENDER_PEARL, 4,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 4, "消耗品"));
		ENTRIES.add(new ShopEntry("experience_bottle", Items.EXPERIENCE_BOTTLE, 8,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 3, "消耗品"));
		ENTRIES.add(new ShopEntry("firework_rocket", Items.FIREWORK_ROCKET, 16,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 2, "消耗品"));
		ENTRIES.add(new ShopEntry("tnt", Items.TNT, 4,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 5, "消耗品"));
		ENTRIES.add(new ShopEntry("milk_bucket", Items.MILK_BUCKET, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 3, "消耗品"));
		ENTRIES.add(new ShopEntry("snowball", Items.SNOWBALL, 16,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 1, "消耗品"));
		// 箭矢商品
		ENTRIES.add(new ShopEntry("arrow", Items.ARROW, 32,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 2, "消耗品"));
		ENTRIES.add(new ShopEntry("spectral_arrow", Items.SPECTRAL_ARROW, 16,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 4, "消耗品"));
		ENTRIES.add(new ShopEntry("tipped_arrow_harming", Items.TIPPED_ARROW, 16,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 8, "消耗品"));
		ENTRIES.add(new ShopEntry("tipped_arrow_healing", Items.TIPPED_ARROW, 16,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 8, "消耗品"));
		ENTRIES.add(new ShopEntry("tipped_arrow_poison", Items.TIPPED_ARROW, 16,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 6, "消耗品"));
		ENTRIES.add(new ShopEntry("tipped_arrow_slowness", Items.TIPPED_ARROW, 16,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 6, "消耗品"));
		ENTRIES.add(new ShopEntry("tipped_arrow_weakness", Items.TIPPED_ARROW, 16,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 5, "消耗品"));

		// ========== 资源兑换 ==========
		ENTRIES.add(new ShopEntry("res_diamond_to_emerald", Items.EMERALD,
				com.randomsurprise.config.BalanceConfig.RES_EMERALD_AMOUNT,
				ShopEntry.CurrencyType.UNIVERSAL_COIN,
				com.randomsurprise.config.BalanceConfig.RES_EMERALD_COST, "资源兑换"));
		ENTRIES.add(new ShopEntry("res_emerald_to_diamond", Items.DIAMOND, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 4, "资源兑换"));
		ENTRIES.add(new ShopEntry("res_gold_to_iron", Items.IRON_INGOT,
				com.randomsurprise.config.BalanceConfig.RES_IRON_AMOUNT,
				ShopEntry.CurrencyType.UNIVERSAL_COIN,
				com.randomsurprise.config.BalanceConfig.RES_IRON_COST, "资源兑换"));
		ENTRIES.add(new ShopEntry("res_iron_to_gold", Items.GOLD_INGOT, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN,
				com.randomsurprise.config.BalanceConfig.RES_GOLD_COST, "资源兑换"));
		ENTRIES.add(new ShopEntry("res_coal_to_redstone", Items.REDSTONE, 8,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 1, "资源兑换"));
		ENTRIES.add(new ShopEntry("res_redstone_to_coal", Items.COAL, 8,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 1, "资源兑换"));
		ENTRIES.add(new ShopEntry("res_lapis_to_quartz", Items.QUARTZ, 8,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 1, "资源兑换"));
		ENTRIES.add(new ShopEntry("res_quartz_to_lapis", Items.LAPIS_LAZULI, 8,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 1, "资源兑换"));
		ENTRIES.add(new ShopEntry("res_diamond_to_netherite", Items.NETHERITE_INGOT, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "资源兑换"));
		ENTRIES.add(new ShopEntry("res_netherite_to_diamond", Items.DIAMOND, 4,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "资源兑换"));
		ENTRIES.add(new ShopEntry("res_coin_to_netherite_scrap", Items.NETHERITE_SCRAP, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 8, "资源兑换"));
		// 紫水晶碎片兑换: 5 金币 -> 1 紫水晶碎片
		ENTRIES.add(new ShopEntry("res_coin_to_amethyst", Items.AMETHYST_SHARD, 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 5, "资源兑换"));
		// 紫水晶碎片回收: 5 紫水晶碎片 -> 1 金币
		ENTRIES.add(new ShopEntry("res_amethyst_to_coin", Items.AMETHYST_SHARD, 5,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 1, "资源兑换"));

		// ========== 模组装备（按ID动态解析，模组未安装则自动隐藏）==========
		// ---- Alex's Mobs ----
		ENTRIES.add(new ShopEntry("mod_am_shield_deep", null, "alexsmobs:shield_of_the_deep", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 40, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_am_dimensional_carver", null, "alexsmobs:dimensional_carver", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 60, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_am_ancient_dart", null, "alexsmobs:ancient_dart", 4,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_am_serrated_shark_tooth", null, "alexsmobs:serrated_shark_tooth", 8,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 10, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_am_skelewag_sword", null, "alexsmobs:skelewag_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 30, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_am_blood_sprayer", null, "alexsmobs:blood_sprayer", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 40, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_am_crocodile_chestplate", null, "alexsmobs:crocodile_chestplate", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 30, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_am_rocky_chestplate", null, "alexsmobs:rocky_chestplate", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 25, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_am_froststalker_helmet", null, "alexsmobs:froststalker_helmet", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_am_straddle_helmet", null, "alexsmobs:straddle_helmet", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 15, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_am_centipede_leggings", null, "alexsmobs:centipede_leggings", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_am_emu_leggings", null, "alexsmobs:emu_leggings", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 15, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_am_roadrunner_boots", null, "alexsmobs:roadrunner_boots", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 15, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_am_flying_fish_boots", null, "alexsmobs:flying_fish_boots", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 15, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_am_falconry_glove", null, "alexsmobs:falconry_glove", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 10, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_am_shark_tooth_arrow", null, "alexsmobs:shark_tooth_arrow", 16,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 3, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_am_enderiophage_rocket", null, "alexsmobs:enderiophage_rocket", 4,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 10, "模组装备", null));
		// ---- Ender Zoology ----
		ENTRIES.add(new ShopEntry("mod_ez_ender_scepter", null, "enderzoology:ender_scepter", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 30, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_ez_hunting_bow", null, "enderzoology:hunting_bow", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_ez_concealment_powder", null, "enderzoology:concealment_powder", 4,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 5, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_ez_ender_charge", null, "enderzoology:ender_charge", 4,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 8, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_ez_concussion_charge", null, "enderzoology:concussion_charge", 4,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 8, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_ez_confusing_charge", null, "enderzoology:confusing_charge", 4,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 8, "模组装备", null));
		// ---- Friends and Foes ----
		ENTRIES.add(new ShopEntry("mod_ff_wildfire_crown", null, "friendsandfoes:wildfire_crown", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 40, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_ff_totem_of_freezing", null, "friendsandfoes:totem_of_freezing", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_ff_totem_of_illusion", null, "friendsandfoes:totem_of_illusion", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_ff_crab_claw", null, "friendsandfoes:crab_claw", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 10, "模组装备", null));
		// ---- Mowzie's Mobs ----
		ENTRIES.add(new ShopEntry("mod_mm_wrought_axe", null, "mowziesmobs:wrought_axe", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 40, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mm_wroughtnaut_helmet", null, "mowziesmobs:wroughtnaut_helmet", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 25, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mm_naga_fang_dagger", null, "mowziesmobs:naga_fang_dagger", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 24, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mm_earthrend_gauntlet", null, "mowziesmobs:earthrend_gauntlet", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 28, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mm_blowgun", null, "mowziesmobs:blowgun", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 16, "模组装备", null));
		// ---- Twilight Forest ----
		ENTRIES.add(new ShopEntry("mod_tf_fiery_sword", null, "twilightforest:fiery_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 30, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_knightmetal_sword", null, "twilightforest:knightmetal_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_ice_sword", null, "twilightforest:ice_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_glass_sword", null, "twilightforest:glass_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 15, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_end_bow", null, "twilightforest:end_bow", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 25, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_triple_bow", null, "twilightforest:triple_bow", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 30, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_seeker_bow", null, "twilightforest:seeker_bow", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 25, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_giant_sword", null, "twilightforest:giant_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 28, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_block_and_chain", null, "twilightforest:block_and_chain", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 24, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_ironwood_sword", null, "twilightforest:ironwood_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 16, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_steeleaf_sword", null, "twilightforest:steeleaf_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_knightmetal_shield", null, "twilightforest:knightmetal_shield", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_ironwood_helmet", null, "twilightforest:ironwood_helmet", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 12, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_ironwood_chestplate", null, "twilightforest:ironwood_chestplate", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_ironwood_leggings", null, "twilightforest:ironwood_leggings", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 16, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_ironwood_boots", null, "twilightforest:ironwood_boots", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 12, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_steeleaf_helmet", null, "twilightforest:steeleaf_helmet", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 14, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_steeleaf_chestplate", null, "twilightforest:steeleaf_chestplate", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 22, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_steeleaf_leggings", null, "twilightforest:steeleaf_leggings", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 18, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_steeleaf_boots", null, "twilightforest:steeleaf_boots", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 14, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_knightmetal_helmet", null, "twilightforest:knightmetal_helmet", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 16, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_knightmetal_chestplate", null, "twilightforest:knightmetal_chestplate", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 24, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_knightmetal_leggings", null, "twilightforest:knightmetal_leggings", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_tf_knightmetal_boots", null, "twilightforest:knightmetal_boots", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 16, "模组装备", null));
		// ---- Aether ----
		ENTRIES.add(new ShopEntry("mod_ae_valkyrie_lance", null, "aether:valkyrie_lance", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 35, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_ae_phoenix_bow", null, "aether:phoenix_bow", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 40, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_ae_gravitite_sword", null, "aether:gravitite_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 25, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_ae_valkyrie_chestplate", null, "aether:valkyrie_chestplate", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 40, "模组装备", null));
		// ---- Cataclysm ----
		ENTRIES.add(new ShopEntry("mod_cat_maledictus", null, "cataclysm:maledictus", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 60, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_cat_bulwark_of_the_flame", null, "cataclysm:bulwark_of_the_flame", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 40, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_cat_the_incinerator", null, "cataclysm:the_incinerator", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 50, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_cat_gauntlet_of_guard", null, "cataclysm:gauntlet_of_guard", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 30, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_cat_ancient_spear", null, "cataclysm:ancient_spear", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 28, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_cat_void_jaw", null, "cataclysm:void_jaw", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 32, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_cat_cursed_bow", null, "cataclysm:cursed_bow", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 24, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_cat_cursium_helmet", null, "cataclysm:cursium_helmet", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 14, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_cat_cursium_chestplate", null, "cataclysm:cursium_chestplate", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 22, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_cat_cursium_leggings", null, "cataclysm:cursium_leggings", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 18, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_cat_cursium_boots", null, "cataclysm:cursium_boots", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 14, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_cat_ignitium_helmet", null, "cataclysm:ignitium_helmet", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 16, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_cat_ignitium_chestplate", null, "cataclysm:ignitium_chestplate", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 28, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_cat_ignitium_leggings", null, "cataclysm:ignitium_leggings", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 22, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_cat_ignitium_boots", null, "cataclysm:ignitium_boots", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 16, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_cat_azure_sea_shield", null, "cataclysm:azure_sea_shield", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 24, "模组装备", null));
		// ---- 限购稀有物品 ----
		ENTRIES.add(new ShopEntry("charm_of_life", null, "twilightforest:charm_of_life", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 30, "稀有物品", null, 4));
		ENTRIES.add(new ShopEntry("charm_of_keeping", null, "twilightforest:charm_of_keeping", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 45, "稀有物品", null, 2));
		ENTRIES.add(new ShopEntry("peacock_fan", null, "twilightforest:peacock_fan", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 25, "稀有物品", null, 2));
		ENTRIES.add(new ShopEntry("mod_am_dimensional_carver_rare", null, "alexsmobs:dimensional_carver", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 80, "稀有物品", null, 1));
		ENTRIES.add(new ShopEntry("wildfire_chestplate", null, "friendsandfoes:wildfire_chestplate", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 35, "稀有物品", null, 1));
		ENTRIES.add(new ShopEntry("lightning_knife", null, "aether:lightning_knife", 16,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "稀有物品", null, 4));
		// ---- 模组消耗品 ----
		ENTRIES.add(new ShopEntry("hydra_chop", null, "twilightforest:hydra_chop", 4,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 10, "消耗品", null, 0));
		ENTRIES.add(new ShopEntry("banana", null, "alexsmobs:banana", 16,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 5, "消耗品", null, 0));
		// ---- Born in Chaos ----
		ENTRIES.add(new ShopEntry("mod_bic_dark_sword", null, "born_in_chaos:dark_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 40, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bic_dark_axe", null, "born_in_chaos:dark_axe", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 35, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bic_dark_pickaxe", null, "born_in_chaos:dark_pickaxe", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 30, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bic_dark_helmet", null, "born_in_chaos:dark_helmet", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 25, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bic_dark_chestplate", null, "born_in_chaos:dark_chestplate", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 40, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bic_dark_leggings", null, "born_in_chaos:dark_leggings", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 30, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bic_dark_boots", null, "born_in_chaos:dark_boots", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bic_void_core", null, "born_in_chaos:void_core", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 60, "稀有物品", null, 2));
		ENTRIES.add(new ShopEntry("mod_bic_chaos_crystal", null, "born_in_chaos:chaos_crystal", 4,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 15, "消耗品", null, 0));
		// ---- Mutant Monsters ----
		ENTRIES.add(new ShopEntry("mod_mm_mutant_sword", null, "mutantmonsters:mutant_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 60, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mm_mutant_bow", null, "mutantmonsters:mutant_bow", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 45, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mm_mutant_pickaxe", null, "mutantmonsters:mutant_pickaxe", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 40, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mm_mutant_helmet", null, "mutantmonsters:mutant_helmet", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 30, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mm_mutant_chestplate", null, "mutantmonsters:mutant_chestplate", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 50, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mm_mutant_leggings", null, "mutantmonsters:mutant_leggings", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 35, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mm_mutant_boots", null, "mutantmonsters:mutant_boots", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 25, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mm_hulk_hammer", null, "mutantmonsters:hulk_hammer", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 32, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mm_mutant_heart", null, "mutantmonsters:mutant_heart", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 80, "稀有物品", null, 1));
		ENTRIES.add(new ShopEntry("mod_mm_mutant_eye", null, "mutantmonsters:mutant_eye", 4,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "消耗品", null, 0));
		// ---- L_Ender's Cataclysm ----
		ENTRIES.add(new ShopEntry("mod_le_ender_blade", null, "cataclysm:ender_blade", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 50, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_le_ender_scythe", null, "cataclysm:ender_scythe", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 60, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_le_ender_bow", null, "cataclysm:ender_bow", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 40, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_le_ender_helmet", null, "cataclysm:ender_helmet", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 30, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_le_ender_chestplate", null, "cataclysm:ender_chestplate", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 45, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_le_ender_boots", null, "cataclysm:ender_boots", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 25, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_le_ender_core", null, "cataclysm:ender_core", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 70, "稀有物品", null, 1));
		ENTRIES.add(new ShopEntry("mod_le_ender_shard", null, "cataclysm:ender_shard", 4,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "消耗品", null, 0));
		// ---- Blue Skies ----
		ENTRIES.add(new ShopEntry("mod_bs_azure_sword", null, "blue_skies:azure_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 35, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_azure_pickaxe", null, "blue_skies:azure_pickaxe", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 30, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_azure_helmet", null, "blue_skies:azure_helmet", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_azure_chestplate", null, "blue_skies:azure_chestplate", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 35, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_azure_leggings", null, "blue_skies:azure_leggings", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 25, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_azure_boots", null, "blue_skies:azure_boots", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 18, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_cloud_essence", null, "blue_skies:cloud_essence", 4,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 15, "消耗品", null, 0));
		ENTRIES.add(new ShopEntry("mod_bs_starlit_sword", null, "blue_skies:starlit_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_starlit_spear", null, "blue_skies:starlit_spear", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 24, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_pyrope_sword", null, "blue_skies:pyrope_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 16, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_charoite_sword", null, "blue_skies:charoite_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 28, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_horizonite_sword", null, "blue_skies:horizonite_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 32, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_pyrope_helmet", null, "blue_skies:pyrope_helmet", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 12, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_pyrope_chestplate", null, "blue_skies:pyrope_chestplate", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_pyrope_leggings", null, "blue_skies:pyrope_leggings", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 16, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_pyrope_boots", null, "blue_skies:pyrope_boots", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 12, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_charoite_helmet", null, "blue_skies:charoite_helmet", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 14, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_charoite_chestplate", null, "blue_skies:charoite_chestplate", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 24, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_charoite_leggings", null, "blue_skies:charoite_leggings", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 18, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_charoite_boots", null, "blue_skies:charoite_boots", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 14, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_horizonite_helmet", null, "blue_skies:horizonite_helmet", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 16, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_horizonite_chestplate", null, "blue_skies:horizonite_chestplate", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 28, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_horizonite_leggings", null, "blue_skies:horizonite_leggings", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 22, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bs_horizonite_boots", null, "blue_skies:horizonite_boots", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 16, "模组装备", null));
		// ---- Cave Dweller ----
		ENTRIES.add(new ShopEntry("mod_cd_cave_sword", null, "cave_dweller:cave_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 30, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_cd_cave_pickaxe", null, "cave_dweller:cave_pickaxe", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 25, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_cd_cave_helmet", null, "cave_dweller:cave_helmet", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 18, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_cd_cave_chestplate", null, "cave_dweller:cave_chestplate", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 30, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_cd_cave_boots", null, "cave_dweller:cave_boots", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 15, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_cd_cave_essence", null, "cave_dweller:cave_essence", 4,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 12, "消耗品", null, 0));
		// ---- Ixeris ----
		ENTRIES.add(new ShopEntry("mod_ix_ixeris_blade", null, "ixeris:ixeris_blade", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 45, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_ix_ixeris_bow", null, "ixeris:ixeris_bow", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 35, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_ix_ixeris_helmet", null, "ixeris:ixeris_helmet", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 25, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_ix_ixeris_chestplate", null, "ixeris:ixeris_chestplate", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 40, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_ix_ixeris_boots", null, "ixeris:ixeris_boots", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_ix_ixeris_core", null, "ixeris:ixeris_core", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 50, "稀有物品", null, 2));
		// ---- More Critters ----
		ENTRIES.add(new ShopEntry("mod_mc_animal_treat", null, "more_critters:animal_treat", 16,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 5, "消耗品", null, 0));
		ENTRIES.add(new ShopEntry("mod_mc_critter_spawn_egg", null, "more_critters:critter_spawn_egg", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 10, "消耗品", null, 0));
		// ---- BOMD ----
		ENTRIES.add(new ShopEntry("mod_bomd_boss_sword", null, "bosses_of_mass_destruction:boss_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 60, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bomd_boss_hammer", null, "bosses_of_mass_destruction:boss_hammer", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 70, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bomd_boss_chestplate", null, "bosses_of_mass_destruction:boss_chestplate", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 50, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_bomd_boss_core", null, "bosses_of_mass_destruction:boss_core", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 80, "稀有物品", null, 1));
		// ---- Dimensional Stomach ----
		ENTRIES.add(new ShopEntry("mod_ds_stomach_blade", null, "dimensionalstomach:stomach_blade", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 40, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_ds_stomach_orb", null, "dimensionalstomach:stomach_orb", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 30, "稀有物品", null, 2));
		ENTRIES.add(new ShopEntry("mod_ds_stomach_essence", null, "dimensionalstomach:stomach_essence", 4,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 12, "消耗品", null, 0));
		// ---- Illage and Spillage ----
		ENTRIES.add(new ShopEntry("mod_ias_spillager_sword", null, "illageandspillagerespillaged:spillager_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 35, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_ias_spillager_bow", null, "illageandspillagerespillaged:spillager_bow", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 30, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_ias_spillager_helmet", null, "illageandspillagerespillaged:spillager_helmet", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_ias_spillager_chestplate", null, "illageandspillagerespillaged:spillager_chestplate", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 35, "模组装备", null));
		// ---- Modular Golems（模块化傀儡）----
		// 模组物品：模板、工作台、魔杖、手册
		ENTRIES.add(new ShopEntry("mod_mg_template", null, "modulargolems:metal_golem_template", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 15, "模组物品", null));
		ENTRIES.add(new ShopEntry("mod_mg_workbench", null, "modulargolems:golem_workbench", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组物品", null));
		ENTRIES.add(new ShopEntry("mod_mg_command_wand", null, "modulargolems:command_wand", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 30, "模组物品", null));
		ENTRIES.add(new ShopEntry("mod_mg_summon_wand", null, "modulargolems:summon_wand", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 25, "模组物品", null));
		ENTRIES.add(new ShopEntry("mod_mg_retrieval_wand", null, "modulargolems:retrieval_wand", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组物品", null));
		ENTRIES.add(new ShopEntry("mod_mg_book", null, "modulargolems:book", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 5, "模组物品", null));
		// 模组装备：傀儡武器
		ENTRIES.add(new ShopEntry("mod_mg_iron_golem_sword", null, "modulargolems:iron_golem_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mg_iron_golem_axe", null, "modulargolems:iron_golem_axe", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 18, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mg_diamond_golem_sword", null, "modulargolems:diamond_golem_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 35, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mg_diamond_golem_axe", null, "modulargolems:diamond_golem_axe", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 30, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mg_netherite_golem_sword", null, "modulargolems:netherite_golem_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 50, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mg_netherite_golem_axe", null, "modulargolems:netherite_golem_axe", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 45, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mg_fiery_golem_sword", null, "modulargolems:fiery_golem_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 35, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mg_knightmetal_golem_sword", null, "modulargolems:knightmetal_golem_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 30, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mg_ironwood_golem_sword", null, "modulargolems:ironwood_golem_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 25, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mg_steeleaf_golem_sword", null, "modulargolems:steeleaf_golem_sword", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 28, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mg_iron_golem_spear", null, "modulargolems:iron_golem_spear", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 16, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mg_diamond_golem_spear", null, "modulargolems:diamond_golem_spear", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 24, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mg_netherite_golem_spear", null, "modulargolems:netherite_golem_spear", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 32, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mg_fiery_golem_spear", null, "modulargolems:fiery_golem_spear", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 28, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mg_fiery_golem_axe", null, "modulargolems:fiery_golem_axe", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 28, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mg_knightmetal_golem_spear", null, "modulargolems:knightmetal_golem_spear", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 28, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mg_knightmetal_golem_axe", null, "modulargolems:knightmetal_golem_axe", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 28, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mg_ironwood_golem_spear", null, "modulargolems:ironwood_golem_spear", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mg_ironwood_golem_axe", null, "modulargolems:ironwood_golem_axe", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 20, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mg_steeleaf_golem_spear", null, "modulargolems:steeleaf_golem_spear", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 24, "模组装备", null));
		ENTRIES.add(new ShopEntry("mod_mg_steeleaf_golem_axe", null, "modulargolems:steeleaf_golem_axe", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 24, "模组装备", null));
		// 消耗品：傀儡升级物品
		ENTRIES.add(new ShopEntry("mod_mg_upgrade_diamond", null, "modulargolems:diamond", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 8, "消耗品", null));
		ENTRIES.add(new ShopEntry("mod_mg_upgrade_netherite", null, "modulargolems:netherite", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 15, "消耗品", null));
		ENTRIES.add(new ShopEntry("mod_mg_upgrade_ench_gold", null, "modulargolems:enchanted_gold", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 12, "消耗品", null));
		ENTRIES.add(new ShopEntry("mod_mg_upgrade_quartz", null, "modulargolems:quartz", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 5, "消耗品", null));
		ENTRIES.add(new ShopEntry("mod_mg_upgrade_fire_immune", null, "modulargolems:fire_immune", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 10, "消耗品", null));
		ENTRIES.add(new ShopEntry("mod_mg_upgrade_thunder_immune", null, "modulargolems:thunder_immune", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 10, "消耗品", null));
		ENTRIES.add(new ShopEntry("mod_mg_upgrade_ender_sight", null, "modulargolems:ender_sight", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 15, "消耗品", null));
		ENTRIES.add(new ShopEntry("mod_mg_upgrade_recycle", null, "modulargolems:recycle", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 8, "消耗品", null));
		ENTRIES.add(new ShopEntry("mod_mg_upgrade_swim", null, "modulargolems:swim", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 5, "消耗品", null));
		// 稀有物品：高级材料（限购2次）
		ENTRIES.add(new ShopEntry("mod_mg_wroughtnaut_ingot", null, "modulargolems:wroughtnaut_ingot", 1,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, 30, "稀有物品", null, 2));
	}

	/** 获取所有有效条目（模组物品不存在则过滤掉） */
	public static List<ShopEntry> getEntries() {
		List<ShopEntry> valid = new ArrayList<>();
		for (ShopEntry entry : ENTRIES) {
			if (entry.resolveItem() != null) {
				valid.add(entry);
			}
		}
		return valid;
	}

	/** 获取指定分类的有效商品列表 */
	public static List<ShopEntry> getByCategory(String category) {
		// "稀有回收"分类由 ExchangeRegistry 动态提供
		if (ExchangeRegistry.CATEGORY.equals(category)) {
			return ExchangeRegistry.getExchangeEntries();
		}
		List<ShopEntry> result = new ArrayList<>();
		for (ShopEntry entry : ENTRIES) {
			if (entry.category().equals(category) && entry.resolveItem() != null) {
				result.add(entry);
			}
		}
		return result;
	}

	/** 根据ID查找条目 */
	public static ShopEntry findById(String id) {
		for (ShopEntry entry : ENTRIES) {
			if (entry.id().equals(id)) {
				return entry;
			}
		}
		return null;
	}

	// ========== 服务端购买处理 ==========

	/** 获取玩家持有的金币数量（含钱袋子中存储的金币） */
	public static int getCoinCount(ServerPlayer player) {
		int count = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ModItems.UNIVERSAL_COIN.get())) {
				count += stack.getCount();
			}
			// 额外统计钱袋子中存储的金币
			if (stack.getItem() instanceof MoneyBagItem) {
				count += MoneyBagItem.getCoinValue(stack);
			}
		}
		return count;
	}

	/** 获取玩家持有的抽奖券数量 */
	public static int getTicketCount(ServerPlayer player) {
		int count = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ModItems.LOTTERY_TICKET.get())) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/** 获取玩家持有某种货币的数量 */
	public static int getCurrencyCount(ServerPlayer player, ShopEntry.CurrencyType currency) {
		return switch (currency) {
			case UNIVERSAL_COIN -> getCoinCount(player);
			case LOTTERY_TICKET -> getTicketCount(player);
			case EXPERIENCE_LEVEL -> player.experienceLevel;
			case DIAMOND -> countItem(player, Items.DIAMOND);
			case IRON_INGOT -> countItem(player, Items.IRON_INGOT);
			case GOLD_INGOT -> countItem(player, Items.GOLD_INGOT);
			case EMERALD -> countItem(player, Items.EMERALD);
			case NETHERITE_INGOT -> countItem(player, Items.NETHERITE_INGOT);
			case COAL -> countItem(player, Items.COAL);
			case REDSTONE -> countItem(player, Items.REDSTONE);
			case LAPIS_LAZULI -> countItem(player, Items.LAPIS_LAZULI);
			case QUARTZ -> countItem(player, Items.QUARTZ);
		};
	}

	private static int countItem(ServerPlayer player, Item item) {
		int count = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) count += stack.getCount();
		}
		return count;
	}

	/**
	 * 购买处理结果
	 * - success: 是否成功
	 * - messageKey: 客户端要显示的翻译键
	 * - args:     翻译参数（均为 String 类型，约定见 ShopPurchaseResultPayload）
	 */
	public record PurchaseResult(boolean success, String messageKey, List<String> args) {}

	/**
	 * 处理购买请求
	 * 流程：验证条目 → 检查背包空间 → 扣除货币 → 发放物品 → 播放音效 → 返回结果
	 * 结果由调用方（ModNetworking）通过 S2C 网络包回传客户端，由 ShopScreen 弹窗显示
	 */
	public static PurchaseResult processPurchase(ServerPlayer player, String entryId) {
		ShopEntry entry = findById(entryId);
		if (entry == null) {
			playFailSound(player);
			return new PurchaseResult(false, "shop.randomsurprise.invalid_entry", List.of());
		}

		Item resolvedItem = entry.resolveItem();
		if (resolvedItem == null) {
			playFailSound(player);
			return new PurchaseResult(false, "shop.randomsurprise.invalid_entry", List.of());
		}

		// 1. 检查限购
		if (entry.hasPurchaseLimit()) {
			int purchased = getPurchaseCount(player.getUUID(), entryId);
			if (purchased >= entry.purchaseLimit()) {
				playFailSound(player);
				return new PurchaseResult(false, "shop.randomsurprise.limit_reached",
						List.of(String.valueOf(entry.purchaseLimit())));
			}
		}

		// 2. 检查背包是否有足够空间
		if (!hasInventorySpace(player, resolvedItem, entry.soldAmount())) {
			playFailSound(player);
			return new PurchaseResult(false, "shop.randomsurprise.inventory_full", List.of());
		}

		// 3. 检查并扣除货币
		if (!deductCurrency(player, entry.currency(), entry.cost())) {
			playFailSound(player);
			return new PurchaseResult(false, "shop.randomsurprise.insufficient_funds",
					List.of(String.valueOf(entry.cost()), entry.currency().getDisplayName()));
		}

		// 4. 发放物品（含附魔；金币绕过 64 堆叠限制）
		RegistryAccess registryAccess = player.level().registryAccess();
		ItemStack reward = entry.createItemStack(registryAccess);
		if (resolvedItem == ModItems.UNIVERSAL_COIN.get()) {
			addCoinsToInventory(player, reward.getCount());
		} else {
			player.getInventory().add(reward);
		}

		// 5. 记录购买（限购商品）
		if (entry.hasPurchaseLimit()) {
			recordPurchase(player.getUUID(), entryId);
		}

		// 6. 播放成功音效
		playSuccessSound(player);

		// 7. 返回成功结果（itemDescriptionId 由客户端用 Component.translatable 包装）
		return new PurchaseResult(true, "shop.randomsurprise.purchase_success",
				List.of(String.valueOf(entry.soldAmount()),
						resolvedItem.getDescriptionId(),
						String.valueOf(entry.cost()),
						entry.currency().getDisplayName()));
	}

	/**
	 * 处理批量购买请求（指定数量）
	 * 按数量循环扣除货币和发放物品。任一步骤失败立即返回，已扣除的部分不回滚（与单次购买语义一致）。
	 * 限购商品按"次数"计数，每次调用记 1 次（无论 amount 多少），客户端应控制 amount 上限。
	 */
	public static PurchaseResult processPurchase(ServerPlayer player, String entryId, int amount) {
		if (amount <= 1) return processPurchase(player, entryId);
		ShopEntry entry = findById(entryId);
		if (entry == null) {
			playFailSound(player);
			return new PurchaseResult(false, "shop.randomsurprise.invalid_entry", List.of());
		}
		Item resolvedItem = entry.resolveItem();
		if (resolvedItem == null) {
			playFailSound(player);
			return new PurchaseResult(false, "shop.randomsurprise.invalid_entry", List.of());
		}
		// 1. 限购检查
		if (entry.hasPurchaseLimit()) {
			int purchased = getPurchaseCount(player.getUUID(), entryId);
			if (purchased >= entry.purchaseLimit()) {
				playFailSound(player);
				return new PurchaseResult(false, "shop.randomsurprise.limit_reached",
						List.of(String.valueOf(entry.purchaseLimit())));
			}
		}
		// 2. 背包空间检查（按总物品数）
		int totalItems = entry.soldAmount() * amount;
		if (!hasInventorySpace(player, resolvedItem, totalItems)) {
			playFailSound(player);
			return new PurchaseResult(false, "shop.randomsurprise.inventory_full", List.of());
		}
		// 3. 货币总量检查（一次性扣除）
		int totalCost = entry.cost() * amount;
		if (getCurrencyCount(player, entry.currency()) < totalCost) {
			playFailSound(player);
			return new PurchaseResult(false, "shop.randomsurprise.insufficient_funds",
					List.of(String.valueOf(totalCost), entry.currency().getDisplayName()));
		}
		if (!deductCurrency(player, entry.currency(), totalCost)) {
			playFailSound(player);
			return new PurchaseResult(false, "shop.randomsurprise.insufficient_funds",
					List.of(String.valueOf(totalCost), entry.currency().getDisplayName()));
		}
		// 4. 发放物品（金币绕过 64 堆叠限制，一次性发放总量；其他物品逐次发放）
		RegistryAccess registryAccess = player.level().registryAccess();
		if (resolvedItem == ModItems.UNIVERSAL_COIN.get()) {
			addCoinsToInventory(player, totalItems);
		} else {
			for (int i = 0; i < amount; i++) {
				ItemStack reward = entry.createItemStack(registryAccess);
				player.getInventory().add(reward);
			}
		}
		// 5. 记录购买（限购商品按 1 次记）
		if (entry.hasPurchaseLimit()) {
			recordPurchase(player.getUUID(), entryId);
		}
		playSuccessSound(player);
		return new PurchaseResult(true, "shop.randomsurprise.purchase_success",
				List.of(String.valueOf(totalItems),
						resolvedItem.getDescriptionId(),
						String.valueOf(totalCost),
						entry.currency().getDisplayName()));
	}

	/**
	 * 处理稀有物品兑换（玩家将稀有物品兑换为金币）
	 * 流程：查找兑换条目 → 检查背包物品数量 → 移除物品 → 发放金币 → 返回结果
	 */
	public static PurchaseResult processExchange(ServerPlayer player, String entryId, int amount) {
		ShopEntry entry = ExchangeRegistry.findById(entryId);
		if (entry == null) {
			playFailSound(player);
			return new PurchaseResult(false, "shop.randomsurprise.invalid_entry", List.of());
		}
		Item resolvedItem = entry.resolveItem();
		if (resolvedItem == null) {
			playFailSound(player);
			return new PurchaseResult(false, "shop.randomsurprise.invalid_entry", List.of());
		}
		// 1. 检查背包物品数量（amount 个）
		int totalItems = amount;  // 兑换条目 soldAmount=1，所以总物品数 = amount
		int owned = countItem(player, resolvedItem);
		if (owned < totalItems) {
			playFailSound(player);
			return new PurchaseResult(false, "shop.randomsurprise.insufficient_items",
					List.of(String.valueOf(totalItems), resolvedItem.getDescriptionId()));
		}
		// 2. 移除物品
		if (!removeItemsFromInventory(player, resolvedItem, totalItems)) {
			playFailSound(player);
			return new PurchaseResult(false, "shop.randomsurprise.insufficient_items",
					List.of(String.valueOf(totalItems), resolvedItem.getDescriptionId()));
		}
		// 3. 发放金币（绕过 Inventory.add() 的 64 限制，直接操作槽位实现单格无限堆叠）
		int totalCoins = entry.cost() * amount;
		addCoinsToInventory(player, totalCoins);
		// 4. 播放成功音效
		playSuccessSound(player);
		// 5. 记录兑换
		ExchangeRecords.record(player.getUUID(), entryId, totalItems, totalCoins);
		// 6. 返回成功结果
		return new PurchaseResult(true, "shop.randomsurprise.exchange_success",
				List.of(String.valueOf(totalItems),
						resolvedItem.getDescriptionId(),
						String.valueOf(totalCoins)));
	}

	/** 获取玩家对某商品的已购次数 */
	public static int getPurchaseCount(UUID playerUuid, String entryId) {
		Map<String, Integer> playerRecords = purchaseRecords.get(playerUuid);
		if (playerRecords == null) return 0;
		return playerRecords.getOrDefault(entryId, 0);
	}

	/** 记录一次购买 */
	private static void recordPurchase(UUID playerUuid, String entryId) {
		purchaseRecords.computeIfAbsent(playerUuid, k -> new HashMap<>())
				.merge(entryId, 1, Integer::sum);
	}

	/** 获取玩家对某商品的剩余购买次数（-1表示无限购） */
	public static int getRemainingPurchases(UUID playerUuid, ShopEntry entry) {
		if (!entry.hasPurchaseLimit()) return -1;
		return Math.max(0, entry.purchaseLimit() - getPurchaseCount(playerUuid, entry.id()));
	}

	/**
	 * 检查背包是否有足够空间放入指定物品
	 */
	private static boolean hasInventorySpace(ServerPlayer player, Item item, int amount) {
		var inventory = player.getInventory();
		int remaining = amount;
		// 尊重物品实际最大堆叠数（金币为 Integer.MAX_VALUE，普通物品为 64）
		int maxStack = item.getMaxStackSize();

		for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				remaining -= maxStack;
			} else if (stack.is(item)) {
				remaining -= (maxStack - stack.getCount());
			}
		}
		return remaining <= 0;
	}

	/**
	 * 将金币发放到玩家背包（绕过 Inventory.add() 的 64 堆叠限制）
	 * 原因：Inventory.add() 合并堆叠时使用 Math.min(itemMaxStack, containerMaxStack=64)，
	 * 即使金币 maxStackSize=Integer.MAX_VALUE，合并仍被限制为 64 一堆。
	 * 此方法直接操作槽位 grow()，绕过容器级别的 64 限制，实现金币单格无限堆叠。
	 */
	private static void addCoinsToInventory(ServerPlayer player, int amount) {
		Item coinItem = ModItems.UNIVERSAL_COIN.get();
		var inventory = player.getInventory();
		int remaining = amount;
		// 1. 先合并到已有的金币堆叠（直接 grow，绕过 64 限制，金币单堆即可容纳全部）
		for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
			ItemStack slotStack = inventory.getItem(i);
			if (!slotStack.isEmpty() && slotStack.is(coinItem)) {
				slotStack.grow(remaining);
				remaining = 0;
			}
		}
		// 2. 没有现有堆叠，找空槽位放入
		if (remaining > 0) {
			for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
				ItemStack slotStack = inventory.getItem(i);
				if (slotStack.isEmpty()) {
					inventory.setItem(i, new ItemStack(coinItem, remaining));
					remaining = 0;
				}
			}
		}
		// 3. 背包满了，掉落地面（避免丢失）
		if (remaining > 0) {
			player.drop(new ItemStack(coinItem, remaining), false);
		}
		inventory.setChanged();
	}

	/**
	 * 扣除货币
	 */
	private static boolean deductCurrency(ServerPlayer player, ShopEntry.CurrencyType currency, int amount) {
		return switch (currency) {
			case UNIVERSAL_COIN -> deductCoins(player, amount);
			case EXPERIENCE_LEVEL -> {
				if (player.experienceLevel < amount) yield false;
				player.giveExperienceLevels(-amount);
				yield true;
			}
			case LOTTERY_TICKET -> removeItemsFromInventory(player, ModItems.LOTTERY_TICKET.get(), amount);
			case DIAMOND -> removeItemsFromInventory(player, Items.DIAMOND, amount);
			case IRON_INGOT -> removeItemsFromInventory(player, Items.IRON_INGOT, amount);
			case GOLD_INGOT -> removeItemsFromInventory(player, Items.GOLD_INGOT, amount);
			case EMERALD -> removeItemsFromInventory(player, Items.EMERALD, amount);
			case NETHERITE_INGOT -> removeItemsFromInventory(player, Items.NETHERITE_INGOT, amount);
			case COAL -> removeItemsFromInventory(player, Items.COAL, amount);
			case REDSTONE -> removeItemsFromInventory(player, Items.REDSTONE, amount);
			case LAPIS_LAZULI -> removeItemsFromInventory(player, Items.LAPIS_LAZULI, amount);
			case QUARTZ -> removeItemsFromInventory(player, Items.QUARTZ, amount);
		};
	}

	/**
	 * 扣除金币（优先从背包松散金币扣除，不足时从钱袋子扣除差额）
	 * <p>流程：
	 * <ol>
	 *   <li>统计松散金币 + 钱袋子金币总数，总量不足则不扣除（返回 false）</li>
	 *   <li>松散金币足够时只扣松散金币</li>
	 *   <li>松散金币不足时先扣全部松散金币，再从钱袋子扣除差额</li>
	 * </ol>
	 * @return true 表示扣除成功
	 */
	private static boolean deductCoins(ServerPlayer player, int amount) {
		var inventory = player.getInventory();
		// 1. 统计松散金币和钱袋子金币总数
		int looseCoins = 0;
		int bagCoins = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ModItems.UNIVERSAL_COIN.get())) {
				looseCoins += stack.getCount();
			}
			if (stack.getItem() instanceof MoneyBagItem) {
				bagCoins += MoneyBagItem.getCoinValue(stack);
			}
		}
		// 2. 总量不足，不扣除
		if (looseCoins + bagCoins < amount) {
			return false;
		}
		// 3. 松散金币足够，只扣松散金币
		if (looseCoins >= amount) {
			return removeItemsFromInventory(player, ModItems.UNIVERSAL_COIN.get(), amount);
		}
		// 4. 松散金币不足，先扣除所有松散金币，再从钱袋子扣除差额
		int remaining = amount;
		if (looseCoins > 0) {
			removeItemsFromInventory(player, ModItems.UNIVERSAL_COIN.get(), looseCoins);
			remaining -= looseCoins;
		}
		for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.getItem() instanceof MoneyBagItem) {
				int removed = MoneyBagItem.removeCoins(stack, remaining);
				remaining -= removed;
			}
		}
		inventory.setChanged();
		return remaining <= 0;
	}

	/**
	 * 从玩家背包中移除指定数量的物品
	 */
	private static boolean removeItemsFromInventory(ServerPlayer player, Item item, int amount) {
		var inventory = player.getInventory();
		int total = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		if (total < amount) return false;

		int remaining = amount;
		for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				int take = Math.min(stack.getCount(), remaining);
				stack.shrink(take);
				remaining -= take;
				if (stack.isEmpty()) {
					inventory.setItem(i, ItemStack.EMPTY);
				}
			}
		}
		inventory.setChanged();
		return true;
	}

	private static void playSuccessSound(ServerPlayer player) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.MASTER, 0.5f, 1.2f);
	}

	private static void playFailSound(ServerPlayer player) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.VILLAGER_NO, SoundSource.MASTER, 0.5f, 1.0f);
	}

	// ========== 购买记录持久化 ==========

	/** 将购买记录序列化为 JSON 字符串 */
	public static String serializePurchaseRecords() {
		try {
			StringBuilder sb = new StringBuilder("{\"records\":{");
			boolean first = true;
			for (Map.Entry<UUID, Map<String, Integer>> playerEntry : purchaseRecords.entrySet()) {
				if (!first) sb.append(",");
				first = false;
				sb.append("\"").append(playerEntry.getKey().toString()).append("\":{");
				boolean firstItem = true;
				for (Map.Entry<String, Integer> itemEntry : playerEntry.getValue().entrySet()) {
					if (!firstItem) sb.append(",");
					firstItem = false;
					sb.append("\"").append(itemEntry.getKey()).append("\":").append(itemEntry.getValue());
				}
				sb.append("}");
			}
			sb.append("}}");
			return sb.toString();
		} catch (Throwable t) {
			return "{}";
		}
	}

	/** 从 JSON 字符串反序列化购买记录 */
	public static void deserializePurchaseRecords(String json) {
		purchaseRecords.clear();
		if (json == null || json.isBlank()) return;
		try {
			com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
			com.google.gson.JsonObject records = root.getAsJsonObject("records");
			if (records == null) return;
			for (Map.Entry<String, com.google.gson.JsonElement> entry : records.entrySet()) {
				UUID uuid = UUID.fromString(entry.getKey());
				com.google.gson.JsonObject items = entry.getValue().getAsJsonObject();
				Map<String, Integer> itemMap = new HashMap<>();
				for (Map.Entry<String, com.google.gson.JsonElement> itemEntry : items.entrySet()) {
					itemMap.put(itemEntry.getKey(), itemEntry.getValue().getAsInt());
				}
				purchaseRecords.put(uuid, itemMap);
			}
		} catch (Throwable t) {
			com.randomsurprise.RandomSurpriseMod.LOGGER.warn("[商店] 购买记录反序列化失败: {}", t.getMessage());
		}
	}
}
