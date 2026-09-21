package com.randomsurprise.compat;

import com.randomsurprise.RandomSurpriseMod;
import com.randomsurprise.affix.PlayerAffixManager;
import com.randomsurprise.battlefield.BattlefieldManager;
import com.randomsurprise.battlefield.GameState;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

/**
 * AtomicStryker's Infernal Mobs 模组兼容层
 *
 * <p>功能：动态调节精英怪生成率
 * <ul>
 *   <li>检测 Infernal Mobs 模组是否加载</li>
 *   <li>通过反射获取并修改 eliteRarity / ultraRarity / infernoRarity 配置值</li>
 *   <li>根据游戏状态动态计算生成率：
 *     <ul>
 *       <li>玩家数量：每多一名玩家，稀有度值降低5%（生成率提高）</li>
 *       <li>征召战场：战场期间大幅提高生成率，随难度递增</li>
 *       <li>坏词条数量：全服坏词条越多，生成率越高</li>
 *       <li>游戏进度：征召战场总次数越多，生成率越高</li>
 *     </ul>
 *   </li>
 *   <li>每5秒（100 tick）更新一次配置</li>
 * </ul>
 *
 * <p>rarity 值含义：1-in-N 概率，值越低 = 生成率越高
 */
public class InfernalMobsCompat {

	private static final String MOD_ID = "infernalmobs";
	private static boolean checked = false;
	private static boolean loaded = false;

	/** 缓存的原始配置值（首次访问时保存） */
	private static int baseEliteRarity = -1;
	private static int baseUltraRarity = -1;
	private static int baseInfernoRarity = -1;

	/** 更新间隔（tick）—— 5秒 = 100 tick */
	private static final int UPDATE_INTERVAL = 100;
	private static int tickCounter = 0;

	/** 反射缓存 */
	private static Object configInstance;
	private static Method setEliteRarityMethod;
	private static Method setUltraRarityMethod;
	private static Method setInfernoRarityMethod;
	private static Method getEliteRarityMethod;
	private static Method getUltraRarityMethod;
	private static Method getInfernoRarityMethod;
	private static boolean reflectionFailed = false;

	/**
	 * 检测 Infernal Mobs 是否已加载
	 */
	public static boolean isLoaded() {
		if (!checked) {
			checked = true;
			try {
				loaded = ModList.get() != null && ModList.get().isLoaded(MOD_ID);
				if (loaded) {
					RandomSurpriseMod.LOGGER.info("[兼容] 检测到 Infernal Mobs，动态精英怪生成率调节已启用");
				}
			} catch (Throwable t) {
				loaded = false;
			}
		}
		return loaded;
	}

	/**
	 * 初始化反射引用（首次调用时缓存）
	 */
	private static boolean initReflection() {
		if (reflectionFailed) return false;
		if (configInstance != null) return true;

		try {
			// 获取 InfernalMobsCore.instance()
			Class<?> coreClass = Class.forName("atomicstryker.infernalmobs.common.InfernalMobsCore");
			Method instanceMethod = coreClass.getDeclaredMethod("instance");
			instanceMethod.setAccessible(true);
			Object coreInstance = instanceMethod.invoke(null);

			// 获取 getConfig()
			Method getConfigMethod = coreClass.getDeclaredMethod("getConfig");
			getConfigMethod.setAccessible(true);
			configInstance = getConfigMethod.invoke(coreInstance);
			if (configInstance == null) {
				RandomSurpriseMod.LOGGER.warn("[兼容] Infernal Mobs config 为空，跳过动态调节");
				reflectionFailed = true;
				return false;
			}

			// 缓存 config 的 getter/setter 方法
			Class<?> configClass = configInstance.getClass();
			getEliteRarityMethod = configClass.getDeclaredMethod("getEliteRarity");
			setEliteRarityMethod = configClass.getDeclaredMethod("setEliteRarity", int.class);
			getUltraRarityMethod = configClass.getDeclaredMethod("getUltraRarity");
			setUltraRarityMethod = configClass.getDeclaredMethod("setUltraRarity", int.class);
			getInfernoRarityMethod = configClass.getDeclaredMethod("getInfernoRarity");
			setInfernoRarityMethod = configClass.getDeclaredMethod("setInfernoRarity", int.class);

			// 保存原始值
			baseEliteRarity = (int) getEliteRarityMethod.invoke(configInstance);
			baseUltraRarity = (int) getUltraRarityMethod.invoke(configInstance);
			baseInfernoRarity = (int) getInfernoRarityMethod.invoke(configInstance);

			RandomSurpriseMod.LOGGER.info("[兼容] Infernal Mobs 原始配置 - Elite: 1/{}, Ultra: 1/{}, Inferno: 1/{}",
					baseEliteRarity, baseUltraRarity, baseInfernoRarity);
			return true;
		} catch (Throwable t) {
			RandomSurpriseMod.LOGGER.warn("[兼容] Infernal Mobs 反射初始化失败: {}", t.getMessage());
			reflectionFailed = true;
			return false;
		}
	}

	/**
	 * 服务器 tick 回调（由 RandomSurpriseMod.onServerTick 调用）
	 * 每5秒更新一次生成率
	 */
	public static void onServerTick(MinecraftServer server) {
		if (!isLoaded()) return;

		tickCounter++;
		if (tickCounter < UPDATE_INTERVAL) return;
		tickCounter = 0;

		updateSpawnRates(server);
	}

	/**
	 * 根据游戏状态动态计算并应用生成率
	 */
	private static void updateSpawnRates(MinecraftServer server) {
		if (!initReflection()) return;

		try {
			// ===== 计算各因子 =====

			// 1. 玩家数量因子：每多一名玩家降低5%稀有度值（最低0.7x）
			int playerCount = server.getPlayerList().getPlayers().size();
			double playerFactor = Math.max(0.7, 1.0 - (playerCount - 1) * 0.05);

			// 2. 征召战场因子
			double battlefieldFactor = 1.0;
			GameState state = BattlefieldManager.getState();
			if (state.isBattlefieldActive()) {
				// 战场期间：基础0.5x（生成率翻倍），随难度进一步降低
				int difficulty = BattlefieldManager.getBattlefieldDifficulty();
				battlefieldFactor = Math.max(0.3, 0.5 - difficulty * 0.03);
			}

			// 3. 坏词条因子：全服坏词条越多，生成率越高（最低0.7x）
			int badAffixCount = PlayerAffixManager.getGlobalBadAffixCount();
			double affixFactor = Math.max(0.7, 1.0 - badAffixCount * 0.02);

			// 4. 游戏进度因子：征召总次数越多，生成率越高（最低0.8x）
			int totalBattles = BattlefieldManager.getTotalBattlefieldCount();
			double progressionFactor = Math.max(0.8, 1.0 - totalBattles * 0.01);

			// ===== 综合计算 =====
			double combinedFactor = playerFactor * battlefieldFactor * affixFactor * progressionFactor;

			int newElite = Math.max(1, (int) Math.round(baseEliteRarity * combinedFactor));
			int newUltra = Math.max(1, (int) Math.round(baseUltraRarity * combinedFactor));
			int newInferno = Math.max(1, (int) Math.round(baseInfernoRarity * combinedFactor));

			// 应用新值
			setEliteRarityMethod.invoke(configInstance, newElite);
			setUltraRarityMethod.invoke(configInstance, newUltra);
			setInfernoRarityMethod.invoke(configInstance, newInferno);

			RandomSurpriseMod.LOGGER.debug("[兼容] Infernal Mobs 动态生成率 - Elite: 1/{} (base 1/{}), Ultra: 1/{} (base 1/{}), Inferno: 1/{} (base 1/{}) | factor: {} (players={} bf={} affix={} prog={})",
					newElite, baseEliteRarity,
					newUltra, baseUltraRarity,
					newInferno, baseInfernoRarity,
					String.format("%.2f", combinedFactor),
					String.format("%.2f", playerFactor),
					String.format("%.2f", battlefieldFactor),
					String.format("%.2f", affixFactor),
					String.format("%.2f", progressionFactor));

		} catch (Throwable t) {
			RandomSurpriseMod.LOGGER.warn("[兼容] Infernal Mobs 生成率更新失败: {}", t.getMessage());
		}
	}

	/**
	 * 服务器关闭时恢复原始配置值
	 */
	public static void onServerStopping() {
		if (!isLoaded() || !initReflection()) return;

		try {
			setEliteRarityMethod.invoke(configInstance, baseEliteRarity);
			setUltraRarityMethod.invoke(configInstance, baseUltraRarity);
			setInfernoRarityMethod.invoke(configInstance, baseInfernoRarity);
			RandomSurpriseMod.LOGGER.info("[兼容] Infernal Mobs 配置已恢复原始值 - Elite: 1/{}, Ultra: 1/{}, Inferno: 1/{}",
					baseEliteRarity, baseUltraRarity, baseInfernoRarity);
		} catch (Throwable t) {
			RandomSurpriseMod.LOGGER.warn("[兼容] 恢复 Infernal Mobs 配置失败: {}", t.getMessage());
		}
	}

	/** 防止实例化 */
	private InfernalMobsCompat() {}
}
