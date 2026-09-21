package com.randomsurprise.shop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 兑换记录持久化管理
 * 存储玩家回收物品的记录，用于统计和审计
 * 数据文件：world/data/randomsurprise_exchange_records.json
 */
public class ExchangeRecords {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Path dataPath;
	private static boolean initialized = false;

	/** 玩家UUID -> 兑换记录列表 */
	private static final Map<UUID, List<ExchangeRecord>> RECORDS = new HashMap<>();

	/** 单条兑换记录 */
	public static class ExchangeRecord {
		public final String entryId;
		public final String itemId;
		public final int itemCount;
		public final int coinCount;
		public final long timestamp;

		public ExchangeRecord(String entryId, String itemId, int itemCount, int coinCount, long timestamp) {
			this.entryId = entryId;
			this.itemId = itemId;
			this.itemCount = itemCount;
			this.coinCount = coinCount;
			this.timestamp = timestamp;
		}
	}

	/** 初始化（服务器启动时调用） */
	public static void init(MinecraftServer server) {
		dataPath = com.randomsurprise.WorldDataPath.getWorldDataPath(server, "randomsurprise_exchange_records.json");
		RECORDS.clear();
		load();
		initialized = true;
	}

	/** 记录一次兑换 */
	public static void record(UUID playerUuid, String entryId, int itemCount, int coinCount) {
		if (!initialized) return;
		// 通过 entryId 反查 itemId
		ShopEntry entry = ExchangeRegistry.findById(entryId);
		String itemId = entry != null ? entry.itemIdString() : entryId;
		ExchangeRecord rec = new ExchangeRecord(entryId, itemId, itemCount, coinCount, System.currentTimeMillis());
		RECORDS.computeIfAbsent(playerUuid, k -> new ArrayList<>()).add(rec);
		save();
	}

	/** 获取玩家的所有兑换记录 */
	public static List<ExchangeRecord> getRecords(UUID playerUuid) {
		return RECORDS.getOrDefault(playerUuid, new ArrayList<>());
	}

	/** 获取玩家累计兑换统计：[总物品数, 总金币数] */
	public static int[] getTotalStats(UUID playerUuid) {
		List<ExchangeRecord> list = RECORDS.getOrDefault(playerUuid, new ArrayList<>());
		int totalItems = 0, totalCoins = 0;
		for (ExchangeRecord r : list) {
			totalItems += r.itemCount;
			totalCoins += r.coinCount;
		}
		return new int[]{totalItems, totalCoins};
	}

	/** 加载 JSON */
	private static void load() {
		try {
			if (!Files.exists(dataPath)) return;
			String content = Files.readString(dataPath);
			JsonObject json = JsonParser.parseString(content).getAsJsonObject();
			RECORDS.clear();
			for (var entry : json.entrySet()) {
				UUID uuid = UUID.fromString(entry.getKey());
				List<ExchangeRecord> list = new ArrayList<>();
				for (var elem : entry.getValue().getAsJsonArray()) {
					JsonObject obj = elem.getAsJsonObject();
					list.add(new ExchangeRecord(
							obj.get("entryId").getAsString(),
							obj.get("itemId").getAsString(),
							obj.get("itemCount").getAsInt(),
							obj.get("coinCount").getAsInt(),
							obj.get("timestamp").getAsLong()));
				}
				RECORDS.put(uuid, list);
			}
		} catch (Exception e) {
			System.err.println("[RandomSurprise] ExchangeRecords 加载失败: " + e.getMessage());
		}
	}

	/** 保存 JSON */
	public static void save() {
		if (dataPath == null) return;
		try {
			JsonObject json = new JsonObject();
			for (var entry : RECORDS.entrySet()) {
				var arr = GSON.toJsonTree(entry.getValue()).getAsJsonArray();
				json.add(entry.getKey().toString(), arr);
			}
			Files.writeString(dataPath, GSON.toJson(json));
		} catch (Exception e) {
			System.err.println("[RandomSurprise] ExchangeRecords 保存失败: " + e.getMessage());
		}
	}
}
