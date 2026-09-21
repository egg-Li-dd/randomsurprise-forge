package com.randomsurprise.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 稀有物品回收注册表
 *
 * 职责：
 * 1. 扫描 BuiltInRegistries.ITEM 中所有物品，基于 Rarity.EPIC/LEGENDARY 识别稀有物品
 * 2. 加载 config/randomsurprise_exchange.json 配置，支持白名单/黑名单/自定义价格
 * 3. 提供可兑换物品列表（ShopEntry 形式，category="稀有回收"）
 * 4. 提供物品兑换价格查询
 *
 * 物品识别规则（优先级从高到低）：
 * - blacklist 中的物品：不回收
 * - whitelist 中的物品：按白名单价格回收
 * - Rarity.LEGENDARY 物品：按 legendaryPrice 回收
 * - Rarity.EPIC 物品：按 epicPrice 回收
 * - 其他：不回收
 */
public class ExchangeRegistry {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("randomsurprise_exchange.json");

	/** 兑换分类名（与 ShopManager.CATEGORIES 中的"稀有回收"对应） */
	public static final String CATEGORY = "稀有回收";

	/** 默认价格（金币绝对值） */
	private static int epicPrice = 12;
	private static int rarePrice = 6;

	/** 白名单：itemId -> 兑换价格（金币） */
	private static final Map<String, Integer> WHITELIST = new HashMap<>();
	/** 黑名单：itemId 列表（不回收） */
	private static final List<String> BLACKLIST = new ArrayList<>();
	/** 模组命名空间白名单（强制包含该命名空间下所有物品） */
	private static final List<String> MOD_NAMESPACES = new ArrayList<>();

	/** 已生成的可兑换 ShopEntry 列表（按 itemId 索引） */
	private static final Map<String, ShopEntry> EXCHANGE_ENTRIES = new HashMap<>();
	/** entryId → ShopEntry 索引（用于 findById 快速查找，避免遍历） */
	private static final Map<String, ShopEntry> ENTRY_BY_ID = new HashMap<>();

	/** 客户端缓存的兑换条目列表（由服务端通过网络包同步） */
	private static List<ShopEntry> clientCachedEntries = new ArrayList<>();
	/** 客户端缓存是否已就绪 */
	private static boolean clientCacheReady = false;

	/** 是否已初始化（扫描过物品注册表） */
	private static boolean initialized = false;

	// 配方价值缓存 (itemId -> 配方材料总价)
	private static final Map<String, Double> RECIPE_PRICE_CACHE = new ConcurrentHashMap<>();
	// 材料基础价值表
	private static final Map<String, Integer> MATERIAL_VALUES = new java.util.HashMap<>();

	static {
		// 矿物材料
		MATERIAL_VALUES.put("minecraft:coal", 1);
		MATERIAL_VALUES.put("minecraft:iron_ingot", 2);
		MATERIAL_VALUES.put("minecraft:gold_ingot", 3);
		MATERIAL_VALUES.put("minecraft:copper_ingot", 1);
		MATERIAL_VALUES.put("minecraft:diamond", 8);
		MATERIAL_VALUES.put("minecraft:emerald", 6);
		MATERIAL_VALUES.put("minecraft:netherite_ingot", 16);
		MATERIAL_VALUES.put("minecraft:lapis_lazuli", 2);
		MATERIAL_VALUES.put("minecraft:redstone", 1);
		MATERIAL_VALUES.put("minecraft:quartz", 1);
		MATERIAL_VALUES.put("minecraft:amethyst_shard", 3);
		MATERIAL_VALUES.put("minecraft:prismarine_shard", 2);
		MATERIAL_VALUES.put("minecraft:end_crystal", 20);
		MATERIAL_VALUES.put("minecraft:ender_pearl", 4);
		MATERIAL_VALUES.put("minecraft:blaze_rod", 5);
		MATERIAL_VALUES.put("minecraft:ghast_tear", 8);
		MATERIAL_VALUES.put("minecraft:nether_star", 32);
		MATERIAL_VALUES.put("minecraft:leather", 2);
		MATERIAL_VALUES.put("minecraft:string", 1);
		MATERIAL_VALUES.put("minecraft:feather", 1);
		MATERIAL_VALUES.put("minecraft:flint", 1);
		MATERIAL_VALUES.put("minecraft:bone", 1);
		MATERIAL_VALUES.put("minecraft:gunpowder", 2);
		MATERIAL_VALUES.put("minecraft:rotten_flesh", 1);
		MATERIAL_VALUES.put("minecraft:spider_eye", 1);
		MATERIAL_VALUES.put("minecraft:slime_ball", 2);
		MATERIAL_VALUES.put("minecraft:magma_cream", 3);
		MATERIAL_VALUES.put("minecraft:rabbit_hide", 1);
		MATERIAL_VALUES.put("minecraft:phantom_membrane", 4);
		MATERIAL_VALUES.put("minecraft:turtle_scute", 5);
		MATERIAL_VALUES.put("minecraft:nautilus_shell", 6);
		MATERIAL_VALUES.put("minecraft:heart_of_the_sea", 20);
		MATERIAL_VALUES.put("minecraft:echo_shard", 5);
		MATERIAL_VALUES.put("minecraft:sculk_catalyst", 8);
		MATERIAL_VALUES.put("minecraft:disc_fragment_5", 10);
		// 木头/石头/基本材料
		MATERIAL_VALUES.put("minecraft:oak_planks", 1);
		MATERIAL_VALUES.put("minecraft:stone", 1);
		MATERIAL_VALUES.put("minecraft:cobblestone", 1);
		MATERIAL_VALUES.put("minecraft:sand", 1);
		MATERIAL_VALUES.put("minecraft:glass", 1);
		MATERIAL_VALUES.put("minecraft:brick", 1);
		MATERIAL_VALUES.put("minecraft:nether_brick", 2);
		MATERIAL_VALUES.put("minecraft:obsidian", 4);
		MATERIAL_VALUES.put("minecraft:glowstone", 3);
		MATERIAL_VALUES.put("minecraft:prismarine", 3);
		MATERIAL_VALUES.put("minecraft:purpur_block", 2);
		// 模组材料（高价值）
		MATERIAL_VALUES.put("alexsmobs:blood", 8);
		MATERIAL_VALUES.put("alexsmobs:serrated_tooth", 10);
		MATERIAL_VALUES.put("blue_skies:charoite", 6);
		MATERIAL_VALUES.put("cataclysm:bone_remnants", 8);
		MATERIAL_VALUES.put("cataclysm:void_residue", 10);
		MATERIAL_VALUES.put("born_in_chaos:essential_fragment", 8);
		MATERIAL_VALUES.put("mowziesmobs:bronze_token", 6);
		MATERIAL_VALUES.put("twilightforest:twilight_scepter", 10);
		MATERIAL_VALUES.put("friendsandfoes:cup_of_tea", 3);
	}

	/**
	 * 热重载：重新加载配置 + 重新扫描物品注册表
	 * 供 /randomsurprise exchange reload 命令调用
	 * @return 重载后的可回收物品数量
	 */
	public static int reload() {
		try {
			loadConfig();
			scanRegistry();
		} catch (Exception e) {
			System.err.println("[RandomSurprise] ExchangeRegistry 重载异常: " + e.getMessage());
			if (EXCHANGE_ENTRIES.isEmpty()) {
				addFallbackEntries();
			}
		}
		return EXCHANGE_ENTRIES.size();
	}

	/**
	 * 初始化：加载配置 + 扫描物品注册表
	 * 在 ShopManager.init() 中调用，服务器启动时执行
	 * 使用 try-catch 保护，确保即使扫描异常也不会阻止服务器启动
	 */
	public static void init() {
		try {
			RECIPE_PRICE_CACHE.clear();
			loadConfig();
			scanRegistry();
		} catch (Exception e) {
			System.err.println("[RandomSurprise] ExchangeRegistry 初始化异常: " + e.getMessage());
			// 即使异常也确保有保底物品
			if (EXCHANGE_ENTRIES.isEmpty()) {
				addFallbackEntries();
			}
		}
		initialized = true;
	}

	/** 加载配置文件，不存在则生成默认配置 */
	private static void loadConfig() {
		try {
			if (!Files.exists(CONFIG_PATH)) {
				generateDefaultConfig();
			}
			String content = Files.readString(CONFIG_PATH);
			JsonObject json = JsonParser.parseString(content).getAsJsonObject();

			if (json.has("epicPrice")) epicPrice = json.get("epicPrice").getAsInt();
			if (json.has("rarePrice")) rarePrice = json.get("rarePrice").getAsInt();

			WHITELIST.clear();
			BLACKLIST.clear();
			MOD_NAMESPACES.clear();

			if (json.has("whitelist") && json.get("whitelist").isJsonObject()) {
				JsonObject wl = json.getAsJsonObject("whitelist");
				wl.entrySet().forEach(e -> WHITELIST.put(e.getKey(), e.getValue().getAsInt()));
			}
			if (json.has("blacklist") && json.get("blacklist").isJsonArray()) {
				json.getAsJsonArray("blacklist").forEach(e -> BLACKLIST.add(e.getAsString()));
			}
			if (json.has("modNamespaces") && json.get("modNamespaces").isJsonArray()) {
				json.getAsJsonArray("modNamespaces").forEach(e -> MOD_NAMESPACES.add(e.getAsString()));
			}
		} catch (Exception e) {
			System.err.println("[RandomSurprise] ExchangeRegistry 配置加载失败，使用默认值: " + e.getMessage());
			// 加载失败时设置默认白名单
			if (WHITELIST.isEmpty()) {
				WHITELIST.put("minecraft:dragon_head", 60);
				WHITELIST.put("minecraft:dragon_egg", com.randomsurprise.config.BalanceConfig.RECYCLE_DRAGON_EGG_PRICE);
			}
		}
	}

	/** 生成默认配置文件（含示例说明） */
	private static void generateDefaultConfig() {
		try {
			JsonObject json = new JsonObject();
			json.addProperty("epicPrice", 12);
			json.addProperty("rarePrice", 6);

			JsonObject whitelist = new JsonObject();
			whitelist.addProperty("minecraft:dragon_head", 60);
			whitelist.addProperty("minecraft:dragon_egg", com.randomsurprise.config.BalanceConfig.RECYCLE_DRAGON_EGG_PRICE);
			whitelist.addProperty("minecraft:nether_star", com.randomsurprise.config.BalanceConfig.RECYCLE_NETHER_STAR_PRICE);
			whitelist.addProperty("minecraft:elytra", 40);
			whitelist.addProperty("minecraft:enchanted_golden_apple", 20);
			json.add("whitelist", whitelist);

			json.add("blacklist", GSON.toJsonTree(new String[]{
					"minecraft:diamond", "minecraft:emerald", "minecraft:netherite_ingot",
					"minecraft:iron_ingot", "minecraft:gold_ingot", "minecraft:coal",
					"minecraft:redstone", "minecraft:lapis_lazuli", "minecraft:quartz"
			}));

			json.add("modNamespaces", GSON.toJsonTree(new String[]{}));

			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(CONFIG_PATH, GSON.toJson(json));
		} catch (Exception e) {
			System.err.println("[RandomSurprise] 生成默认兑换配置失败: " + e.getMessage());
		}
	}

	/**
	 * 扫描物品注册表，识别可回收的稀有物品
	 * 规则：白名单 > 黑名单 > Rarity.LEGENDARY > Rarity.EPIC > 模组命名空间白名单
	 * 每个物品的解析均包裹在 try-catch 中，单个物品失败不会导致整个列表为空
	 */
	private static void scanRegistry() {
		EXCHANGE_ENTRIES.clear();
		ENTRY_BY_ID.clear();

		// 1. 先添加白名单物品（强制包含，按白名单价格）
		for (Map.Entry<String, Integer> entry : WHITELIST.entrySet()) {
			try {
				String itemId = entry.getKey();
				int price = entry.getValue();
				if (BLACKLIST.contains(itemId)) continue;
				Item item = resolveItem(itemId);
				if (item != null) {
					ShopEntry se = createExchangeEntry(itemId, price);
					EXCHANGE_ENTRIES.put(itemId, se);
					ENTRY_BY_ID.put(se.id(), se);
				}
			} catch (Exception e) {
				System.err.println("[RandomSurprise] ExchangeRegistry 白名单物品解析失败: " + entry.getKey() + " - " + e.getMessage());
			}
		}

		// 2. 扫描所有注册物品，按稀有度识别
		try {
			BuiltInRegistries.ITEM.forEach(item -> {
				try {
					ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
					if (key == null) return;
					String itemId = key.toString();

					// 已在白名单中的跳过
					if (EXCHANGE_ENTRIES.containsKey(itemId)) return;
					// 黑名单跳过
					if (BLACKLIST.contains(itemId)) return;

					// 检查稀有度（1.20.1 仅 COMMON/UNCOMMON/RARE/EPIC，无 LEGENDARY）
					Rarity rarity = getitemRarity(item);
					double recipeValue = estimateRecipeValue(itemId);
					double rarityMultiplier = getRarityMultiplier(rarity);
					int price = 0;
					if (rarity == Rarity.EPIC) {
						price = (int) Math.floor(Math.max(epicPrice, recipeValue * 0.3) * rarityMultiplier);
					} else if (rarity == Rarity.RARE) {
						price = (int) Math.floor(Math.max(rarePrice, recipeValue * 0.3) * rarityMultiplier);
					} else {
						// 模组命名空间白名单：强制包含该模组所有物品
						if (MOD_NAMESPACES.contains(key.getNamespace())) {
							double modMultiplier = getRarityMultiplier(rarity);
							price = (int) Math.floor(Math.max(5, recipeValue * 0.3) * modMultiplier);
						} else {
							return;  // 不符合任何条件，跳过
						}
					}

					ShopEntry se = createExchangeEntry(itemId, price);
					EXCHANGE_ENTRIES.put(itemId, se);
					ENTRY_BY_ID.put(se.id(), se);
				} catch (Exception e) {
					// 单个物品解析失败不影响其他物品的扫描
				}
			});
		} catch (Exception e) {
			System.err.println("[RandomSurprise] ExchangeRegistry 注册表扫描异常: " + e.getMessage());
		}

		// 3. 保底：如果扫描后列表仍为空，添加核心保底物品
		if (EXCHANGE_ENTRIES.isEmpty()) {
			System.err.println("[RandomSurprise] ExchangeRegistry 扫描结果为空，启用保底物品列表");
			addFallbackEntries();
		}

		System.out.println("[RandomSurprise] ExchangeRegistry 扫描完成，可回收物品数: " + EXCHANGE_ENTRIES.size());
	}

	/**
	 * 添加保底兑换条目（当注册表扫描无结果时使用）
	 * 确保即使某些物品无法解析，商店稀有回收分类也不会完全为空
	 */
	private static void addFallbackEntries() {
		String[][] fallbacks = {
				{"minecraft:dragon_head", "60"},
				{"minecraft:dragon_egg", String.valueOf(com.randomsurprise.config.BalanceConfig.RECYCLE_DRAGON_EGG_PRICE)},
				{"minecraft:nether_star", String.valueOf(com.randomsurprise.config.BalanceConfig.RECYCLE_NETHER_STAR_PRICE)},
				{"minecraft:elytra", "40"},
				{"minecraft:enchanted_golden_apple", "20"},
				{"minecraft:totem_of_undying", "12"},
		};
		for (String[] fb : fallbacks) {
			try {
				String itemId = fb[0];
				int price = Integer.parseInt(fb[1]);
				if (BLACKLIST.contains(itemId)) continue;
				Item item = resolveItem(itemId);
				if (item != null) {
					ShopEntry se = createExchangeEntry(itemId, price);
					EXCHANGE_ENTRIES.put(itemId, se);
					ENTRY_BY_ID.put(se.id(), se);
				}
			} catch (Exception e) {
				// 保底物品解析失败，跳过
			}
		}
	}

	/** 获取物品的稀有度（通过创建临时 ItemStack） */
	private static Rarity getitemRarity(Item item) {
		try {
			ItemStack stack = new ItemStack(item);
			return stack.getRarity();
		} catch (Exception e) {
			return Rarity.COMMON;
		}
	}

	/** 通过 itemId 解析 Item */
	private static Item resolveItem(String itemId) {
		try {
			return BuiltInRegistries.ITEM.getOptional(new ResourceLocation(itemId)).orElse(null);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * 基于物品类型估算配方材料价值（服务端缓存）
	 * 不直接查询配方（避免客户端调用），而是基于物品ID模式估算
	 */
	private static double estimateRecipeValue(String itemId) {
		if (RECIPE_PRICE_CACHE.containsKey(itemId)) return RECIPE_PRICE_CACHE.get(itemId);

		double value = 0;
		String id = itemId.toLowerCase();

		// 钻石装备 = 钻石价值
		if (id.contains("diamond_") && (id.contains("sword") || id.contains("pickaxe") || id.contains("axe") ||
				id.contains("shovel") || id.contains("helmet") || id.contains("chestplate") ||
				id.contains("leggings") || id.contains("boots") || id.contains("hoe"))) {
			value = 32; // 多个钻石
		}
		// 下界合金装备 = 下界合金价值
		else if (id.contains("netherite_") && (id.contains("sword") || id.contains("pickaxe") || id.contains("axe") ||
				id.contains("shovel") || id.contains("helmet") || id.contains("chestplate") ||
				id.contains("leggings") || id.contains("boots") || id.contains("hoe"))) {
			value = 64; // 下界合金锭 + 钻石 + 金锭
		}
		// 铁装备
		else if (id.contains("iron_") && (id.contains("sword") || id.contains("pickaxe") || id.contains("axe") ||
				id.contains("shovel") || id.contains("helmet") || id.contains("chestplate") ||
				id.contains("leggings") || id.contains("boots") || id.contains("hoe"))) {
			value = 8;
		}
		// 金装备
		else if (id.contains("golden_") && (id.contains("sword") || id.contains("pickaxe") || id.contains("axe") ||
				id.contains("shovel") || id.contains("helmet") || id.contains("chestplate") ||
				id.contains("leggings") || id.contains("boots") || id.contains("hoe") || id.contains("apple") || id.contains("carrot"))) {
			value = 12;
		}
		// 弓/弩/三叉戟/盾牌
		else if (id.contains("bow") || id.contains("crossbow") || id.contains("trident") || id.contains("shield")) {
			value = id.contains("crossbow") ? 10 : id.contains("trident") ? 16 : 6;
		}
		// 附魔书
		else if (id.contains("enchanted_book")) {
			value = 16;
		}
		// 鞘翅
		else if (id.contains("elytra")) {
			value = 48;
		}
		// 不死图腾
		else if (id.contains("totem")) {
			value = 20;
		}
		// 末影之眼
		else if (id.contains("ender_eye")) {
			value = 6;
		}
		// 末影珍珠
		else if (id.contains("ender_pearl")) {
			value = 4;
		}
		// TNT
		else if (id.contains("tnt")) {
			value = 8;
		}
		// 下界之星
		else if (id.contains("nether_star")) {
			value = 32;
		}
		// 龙息/龙蛋
		else if (id.contains("dragon_breath") || id.contains("dragon_egg")) {
			value = 24;
		}
		// 末地水晶
		else if (id.contains("end_crystal")) {
			value = 20;
		}
		// 经验瓶
		else if (id.contains("experience_bottle") || id.contains("bottle_o'_enchanting")) {
			value = 4;
		}
		// 唱片
		else if (id.contains("music_disc") || id.contains("record")) {
			value = 8;
		}
		// 模组装备（基于命名空间推断较高价值）
		else if (id.contains("alexsmobs") || id.contains("cataclysm") || id.contains("mowziesmobs") ||
				id.contains("twilightforest") || id.contains("blue_skies") || id.contains("born_in_chaos") ||
				id.contains("friendsandfoes") || id.contains("mutantmonsters") || id.contains("enderzoology") ||
				id.contains("illageandspillagerespillaged") || id.contains("bomd") || id.contains("cave_dweller")) {
			// 模组装备通常比原版更有价值
			if (id.contains("sword") || id.contains("axe") || id.contains("spear") || id.contains("greatsword") ||
				id.contains("halberd") || id.contains("cannon") || id.contains("staff")) {
				value = 24; // 武器
			} else if (id.contains("helmet") || id.contains("chestplate") || id.contains("leggings") ||
					id.contains("boots") || id.contains("armor")) {
				value = 20; // 防具
			} else {
				value = 12; // 其他模组物品
			}
		}

		RECIPE_PRICE_CACHE.put(itemId, value);
		return value;
	}

	/** 稀有度系数 */
	private static double getRarityMultiplier(Rarity rarity) {
		return switch (rarity) {
			case COMMON -> 1.0;
			case UNCOMMON -> 1.2;
			case RARE -> 1.5;
			case EPIC -> 2.0;
			default -> 1.0;
		};
	}

	/** 创建一个兑换用的 ShopEntry（soldItem=null, itemIdString=itemId, 货币=金币） */
	private static ShopEntry createExchangeEntry(String itemId, int price) {
		return new ShopEntry(
				"exchange_" + itemId.replace(":", "_"),  // entryId，加前缀避免与购买条目冲突
				null,                                     // soldItem=null（模组物品延迟解析）
				itemId,                                   // itemIdString
				1,                                        // soldAmount=1（回收1个）
				ShopEntry.CurrencyType.UNIVERSAL_COIN,    // 货币=金币
				price,                                    // cost=兑换价格
				CATEGORY,                                 // 分类=稀有回收
				null,                                     // 无附魔
				0                                         // 无限购
		);
	}

	/**
	 * 获取所有可兑换物品的 ShopEntry 列表（按模组命名空间排序，相同模组物品聚集）
	 * 供 ShopManager.getByCategory("稀有回收") 调用
	 */
	public static List<ShopEntry> getExchangeEntries() {
		// 客户端侧：返回服务端同步的缓存数据
		if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
			if (!initialized && clientCacheReady) {
				return new ArrayList<>(clientCachedEntries);
			}
			// 客户端且未收到同步数据时返回空列表（不是null）
			if (!initialized) {
				return new ArrayList<>();
			}
		}
		// 服务端侧：返回实际扫描结果
		List<ShopEntry> list = new ArrayList<>(EXCHANGE_ENTRIES.values());
		list.sort((a, b) -> {
			String nsA = a.itemIdString() != null ? a.itemIdString().split(":")[0] : "zzz";
			String nsB = b.itemIdString() != null ? b.itemIdString().split(":")[0] : "zzz";
			// minecraft 原版物品排在最前，其他模组按命名空间字母序
			int cmp = nsA.equals("minecraft") ? -1 : (nsB.equals("minecraft") ? 1 : nsA.compareTo(nsB));
			if (cmp != 0) return cmp;
			// 同命名空间内按 itemId 排序
			return a.itemIdString().compareTo(b.itemIdString());
		});
		return list;
	}

	/**
	 * 根据 entryId 获取兑换条目（O(1) 直接索引）
	 */
	public static ShopEntry findById(String entryId) {
		return ENTRY_BY_ID.get(entryId);
	}

	/** 是否已初始化 */
	public static boolean isInitialized() {
		return initialized;
	}

	/**
	 * 客户端专用：接收服务端同步的兑换条目数据并更新缓存
	 * 由 ExchangeListSyncPayload.handle() 调用
	 */
	public static void updateClientCache(List<com.randomsurprise.network.ExchangeListSyncPayload.EntryData> entries) {
		clientCachedEntries.clear();
		for (var data : entries) {
			ShopEntry entry = new ShopEntry(
				data.entryId, null, data.itemIdString, data.soldAmount,
				ShopEntry.CurrencyType.UNIVERSAL_COIN, data.cost,
				CATEGORY, null, 0);
			clientCachedEntries.add(entry);
		}
		clientCacheReady = true;
		System.out.println("[RandomSurprise] 客户端稀有回收缓存已更新，条目数: " + clientCachedEntries.size());
	}

	/** 客户端缓存是否已就绪 */
	public static boolean isClientCacheReady() { return clientCacheReady; }

	/**
	 * 服务端专用：获取当前兑换条目的序列化数据（用于网络同步）
	 * @return EntryData 列表
	 */
	public static List<com.randomsurprise.network.ExchangeListSyncPayload.EntryData> getEntriesForSync() {
		List<com.randomsurprise.network.ExchangeListSyncPayload.EntryData> result = new ArrayList<>();
		for (ShopEntry entry : EXCHANGE_ENTRIES.values()) {
			result.add(new com.randomsurprise.network.ExchangeListSyncPayload.EntryData(
				entry.id(), entry.itemIdString(), entry.cost(), entry.soldAmount()));
		}
		return result;
	}
}
