package com.randomsurprise;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 模组配置管理
 * 管理随机惊喜的间隔时间、开关、额外敌对实体ID等
 */
public class SurpriseConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH =
			FMLPaths.CONFIGDIR.get().resolve("randomsurprise.json");

	private static boolean enabled = true;
	private static int intervalSeconds = 60;
	private static boolean allowNegativeBuffs = true;
	private static boolean allowDangerousEvents = true;

	/** 用户自定义的额外敌对实体ID（如 ["alexsmobs:crocodile"]） */
	private static List<String> extraHostileEntityIds = new ArrayList<>();
	/** 用户自定义的额外Boss实体ID（用于清除药水掉落） */
	private static List<String> extraBossEntityIds = new ArrayList<>();

	/** 白天敌对生物生成开关 */
	private static boolean daytimeSpawningEnabled = true;
	/** 白天初始生成数量与夜晚的比例（0.0~1.0，建议0.25~0.33即1:4~1:3） */
	private static double daytimeInitialSpawnRatio = 0.25;
	/** 白天生成数量达到夜晚水平所需的天数（游戏内天数） */
	private static int daytimeSpawnRampUpDays = 180;
	/** 白天生成数量增长是否启用（关闭则保持初始比例） */
	private static boolean daytimeSpawnRampUpEnabled = true;

	// ===== 白天生成 v2 优化配置 =====
	/** 每个玩家生成冷却（tick），同一玩家周围两次生成之间最小间隔 */
	private static int daytimeSpawnCooldownTicks = 100; // 5秒
	/** 玩家附近（64格）敌对生物硬上限 */
	private static int daytimeMaxHostilesNearPlayer = 30;
	/** 局部区域（16格半径）内同类型敌对生物上限，防止聚集 */
	private static int daytimeMaxPerTypeInLocalArea = 6;
	/** 局部区域半径（格） */
	private static int daytimeLocalAreaRadius = 16;
	/** 全世界敌对生物软上限（超过则降低生成概率） */
	private static int daytimeGlobalSoftCap = 200;
	/** 是否排除Boss级生物（强烈建议开启，避免生成凋零/末影龙等） */
	private static boolean daytimeExcludeBosses = true;
	/** 是否启用动态规则（玩家等级/进度/天气/游戏状态影响生成） */
	private static boolean daytimeDynamicRulesEnabled = true;
	/** 是否启用天气影响（雨天+10%/雷暴+25%生成率） */
	private static boolean daytimeWeatherEffectEnabled = true;
	/** 是否启用倒地玩家保护（倒地玩家周围降低生成） */
	private static boolean daytimeDownedProtectionEnabled = true;
	/** 生成位置最小光照等级（0=不检查，7=原版夜晚标准，建议4允许阴影生成） */
	private static int daytimeMaxLightLevel = 4;
	/** 生成位置最小坡度要求（避免在悬崖/陡坡生成），0=不检查 */
	private static int daytimeMaxSlopeBlocks = 3;
	/** 最小生成距离（格），避免太近惊吓玩家 */
	private static int daytimeMinSpawnDistance = 24;
	/** 最大生成距离（格） */
	private static int daytimeMaxSpawnDistance = 48;

	public static void load() {
		try {
			if (Files.exists(CONFIG_PATH)) {
				String content = Files.readString(CONFIG_PATH);
				JsonObject json = GSON.fromJson(content, JsonObject.class);
				if (json != null) {
					if (json.has("enabled")) enabled = json.get("enabled").getAsBoolean();
					if (json.has("intervalSeconds"))
						intervalSeconds = Math.max(10, json.get("intervalSeconds").getAsInt());
					if (json.has("allowNegativeBuffs"))
						allowNegativeBuffs = json.get("allowNegativeBuffs").getAsBoolean();
					if (json.has("allowDangerousEvents"))
						allowDangerousEvents = json.get("allowDangerousEvents").getAsBoolean();
					if (json.has("extraHostileEntityIds")) {
						extraHostileEntityIds = jsonToStringList(json.getAsJsonArray("extraHostileEntityIds"));
					}
					if (json.has("extraBossEntityIds")) {
						extraBossEntityIds = jsonToStringList(json.getAsJsonArray("extraBossEntityIds"));
					}
					if (json.has("daytimeSpawningEnabled"))
						daytimeSpawningEnabled = json.get("daytimeSpawningEnabled").getAsBoolean();
					if (json.has("daytimeInitialSpawnRatio"))
						daytimeInitialSpawnRatio = Math.max(0.0, Math.min(1.0,
								json.get("daytimeInitialSpawnRatio").getAsDouble()));
					if (json.has("daytimeSpawnRampUpDays"))
						daytimeSpawnRampUpDays = Math.max(1, json.get("daytimeSpawnRampUpDays").getAsInt());
					if (json.has("daytimeSpawnRampUpEnabled"))
					daytimeSpawnRampUpEnabled = json.get("daytimeSpawnRampUpEnabled").getAsBoolean();
				// v2 优化配置
				if (json.has("daytimeSpawnCooldownTicks"))
					daytimeSpawnCooldownTicks = Math.max(0, json.get("daytimeSpawnCooldownTicks").getAsInt());
				if (json.has("daytimeMaxHostilesNearPlayer"))
					daytimeMaxHostilesNearPlayer = Math.max(1, json.get("daytimeMaxHostilesNearPlayer").getAsInt());
				if (json.has("daytimeMaxPerTypeInLocalArea"))
					daytimeMaxPerTypeInLocalArea = Math.max(1, json.get("daytimeMaxPerTypeInLocalArea").getAsInt());
				if (json.has("daytimeLocalAreaRadius"))
					daytimeLocalAreaRadius = Math.max(4, json.get("daytimeLocalAreaRadius").getAsInt());
				if (json.has("daytimeGlobalSoftCap"))
					daytimeGlobalSoftCap = Math.max(10, json.get("daytimeGlobalSoftCap").getAsInt());
				if (json.has("daytimeExcludeBosses"))
					daytimeExcludeBosses = json.get("daytimeExcludeBosses").getAsBoolean();
				if (json.has("daytimeDynamicRulesEnabled"))
					daytimeDynamicRulesEnabled = json.get("daytimeDynamicRulesEnabled").getAsBoolean();
				if (json.has("daytimeWeatherEffectEnabled"))
					daytimeWeatherEffectEnabled = json.get("daytimeWeatherEffectEnabled").getAsBoolean();
				if (json.has("daytimeDownedProtectionEnabled"))
					daytimeDownedProtectionEnabled = json.get("daytimeDownedProtectionEnabled").getAsBoolean();
				if (json.has("daytimeMaxLightLevel"))
					daytimeMaxLightLevel = Math.max(0, Math.min(15, json.get("daytimeMaxLightLevel").getAsInt()));
				if (json.has("daytimeMaxSlopeBlocks"))
					daytimeMaxSlopeBlocks = Math.max(0, json.get("daytimeMaxSlopeBlocks").getAsInt());
				if (json.has("daytimeMinSpawnDistance"))
					daytimeMinSpawnDistance = Math.max(8, json.get("daytimeMinSpawnDistance").getAsInt());
				if (json.has("daytimeMaxSpawnDistance"))
					daytimeMaxSpawnDistance = Math.max(daytimeMinSpawnDistance + 4,
							json.get("daytimeMaxSpawnDistance").getAsInt());
			}
		}
		} catch (Exception e) {
			RandomSurpriseMod.LOGGER.warn("读取配置失败，使用默认值: {}", e.getMessage());
		}
		save();
	}

	public static void save() {
		JsonObject json = new JsonObject();
		json.addProperty("enabled", enabled);
		json.addProperty("intervalSeconds", intervalSeconds);
		json.addProperty("allowNegativeBuffs", allowNegativeBuffs);
		json.addProperty("allowDangerousEvents", allowDangerousEvents);
		JsonArray hostileArr = new JsonArray();
		for (String id : extraHostileEntityIds) hostileArr.add(id);
		json.add("extraHostileEntityIds", hostileArr);
		JsonArray bossArr = new JsonArray();
		for (String id : extraBossEntityIds) bossArr.add(id);
		json.add("extraBossEntityIds", bossArr);
		json.addProperty("daytimeSpawningEnabled", daytimeSpawningEnabled);
		json.addProperty("daytimeInitialSpawnRatio", daytimeInitialSpawnRatio);
		json.addProperty("daytimeSpawnRampUpDays", daytimeSpawnRampUpDays);
		json.addProperty("daytimeSpawnRampUpEnabled", daytimeSpawnRampUpEnabled);
		// v2 优化配置
		json.addProperty("daytimeSpawnCooldownTicks", daytimeSpawnCooldownTicks);
		json.addProperty("daytimeMaxHostilesNearPlayer", daytimeMaxHostilesNearPlayer);
		json.addProperty("daytimeMaxPerTypeInLocalArea", daytimeMaxPerTypeInLocalArea);
		json.addProperty("daytimeLocalAreaRadius", daytimeLocalAreaRadius);
		json.addProperty("daytimeGlobalSoftCap", daytimeGlobalSoftCap);
		json.addProperty("daytimeExcludeBosses", daytimeExcludeBosses);
		json.addProperty("daytimeDynamicRulesEnabled", daytimeDynamicRulesEnabled);
		json.addProperty("daytimeWeatherEffectEnabled", daytimeWeatherEffectEnabled);
		json.addProperty("daytimeDownedProtectionEnabled", daytimeDownedProtectionEnabled);
		json.addProperty("daytimeMaxLightLevel", daytimeMaxLightLevel);
		json.addProperty("daytimeMaxSlopeBlocks", daytimeMaxSlopeBlocks);
		json.addProperty("daytimeMinSpawnDistance", daytimeMinSpawnDistance);
		json.addProperty("daytimeMaxSpawnDistance", daytimeMaxSpawnDistance);
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(CONFIG_PATH, GSON.toJson(json));
		} catch (IOException e) {
			RandomSurpriseMod.LOGGER.error("保存配置失败: {}", e.getMessage());
		}
	}

	private static List<String> jsonToStringList(JsonArray arr) {
		List<String> result = new ArrayList<>();
		if (arr == null) return result;
		arr.forEach(e -> {
			if (e.isJsonPrimitive()) result.add(e.getAsString());
		});
		return result;
	}

	public static boolean isEnabled() { return enabled; }
	public static void setEnabled(boolean e) { enabled = e; }
	public static int getIntervalSeconds() { return intervalSeconds; }
	public static void setIntervalSeconds(int s) { intervalSeconds = Math.max(10, s); }
	public static int getIntervalTicks() { return intervalSeconds * 20; }
	public static boolean isAllowNegativeBuffs() { return allowNegativeBuffs; }
	public static boolean isAllowDangerousEvents() { return allowDangerousEvents; }
	public static void setAllowDangerousEvents(boolean v) { allowDangerousEvents = v; }

	public static List<String> getExtraHostileEntityIds() {
		return Collections.unmodifiableList(extraHostileEntityIds);
	}

	public static List<String> getExtraBossEntityIds() {
		return Collections.unmodifiableList(extraBossEntityIds);
	}

	public static boolean isDaytimeSpawningEnabled() { return daytimeSpawningEnabled; }
	public static void setDaytimeSpawningEnabled(boolean v) { daytimeSpawningEnabled = v; }
	public static double getDaytimeInitialSpawnRatio() { return daytimeInitialSpawnRatio; }
	public static void setDaytimeInitialSpawnRatio(double v) {
		daytimeInitialSpawnRatio = Math.max(0.0, Math.min(1.0, v));
	}
	public static int getDaytimeSpawnRampUpDays() { return daytimeSpawnRampUpDays; }
	public static void setDaytimeSpawnRampUpDays(int v) {
		daytimeSpawnRampUpDays = Math.max(1, v);
	}
	public static boolean isDaytimeSpawnRampUpEnabled() { return daytimeSpawnRampUpEnabled; }
	public static void setDaytimeSpawnRampUpEnabled(boolean v) { daytimeSpawnRampUpEnabled = v; }

	// ===== v2 优化配置 getter/setter =====
	public static int getDaytimeSpawnCooldownTicks() { return daytimeSpawnCooldownTicks; }
	public static void setDaytimeSpawnCooldownTicks(int v) { daytimeSpawnCooldownTicks = Math.max(0, v); }
	public static int getDaytimeMaxHostilesNearPlayer() { return daytimeMaxHostilesNearPlayer; }
	public static void setDaytimeMaxHostilesNearPlayer(int v) { daytimeMaxHostilesNearPlayer = Math.max(1, v); }
	public static int getDaytimeMaxPerTypeInLocalArea() { return daytimeMaxPerTypeInLocalArea; }
	public static void setDaytimeMaxPerTypeInLocalArea(int v) { daytimeMaxPerTypeInLocalArea = Math.max(1, v); }
	public static int getDaytimeLocalAreaRadius() { return daytimeLocalAreaRadius; }
	public static void setDaytimeLocalAreaRadius(int v) { daytimeLocalAreaRadius = Math.max(4, v); }
	public static int getDaytimeGlobalSoftCap() { return daytimeGlobalSoftCap; }
	public static void setDaytimeGlobalSoftCap(int v) { daytimeGlobalSoftCap = Math.max(10, v); }
	public static boolean isDaytimeExcludeBosses() { return daytimeExcludeBosses; }
	public static void setDaytimeExcludeBosses(boolean v) { daytimeExcludeBosses = v; }
	public static boolean isDaytimeDynamicRulesEnabled() { return daytimeDynamicRulesEnabled; }
	public static void setDaytimeDynamicRulesEnabled(boolean v) { daytimeDynamicRulesEnabled = v; }
	public static boolean isDaytimeWeatherEffectEnabled() { return daytimeWeatherEffectEnabled; }
	public static void setDaytimeWeatherEffectEnabled(boolean v) { daytimeWeatherEffectEnabled = v; }
	public static boolean isDaytimeDownedProtectionEnabled() { return daytimeDownedProtectionEnabled; }
	public static void setDaytimeDownedProtectionEnabled(boolean v) { daytimeDownedProtectionEnabled = v; }
	public static int getDaytimeMaxLightLevel() { return daytimeMaxLightLevel; }
	public static void setDaytimeMaxLightLevel(int v) { daytimeMaxLightLevel = Math.max(0, Math.min(15, v)); }
	public static int getDaytimeMaxSlopeBlocks() { return daytimeMaxSlopeBlocks; }
	public static void setDaytimeMaxSlopeBlocks(int v) { daytimeMaxSlopeBlocks = Math.max(0, v); }
	public static int getDaytimeMinSpawnDistance() { return daytimeMinSpawnDistance; }
	public static void setDaytimeMinSpawnDistance(int v) { daytimeMinSpawnDistance = Math.max(8, v); }
	public static int getDaytimeMaxSpawnDistance() { return daytimeMaxSpawnDistance; }
	public static void setDaytimeMaxSpawnDistance(int v) { daytimeMaxSpawnDistance = Math.max(getDaytimeMinSpawnDistance() + 4, v); }
}
