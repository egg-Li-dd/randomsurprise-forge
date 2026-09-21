package com.randomsurprise.compat;

import com.randomsurprise.RandomSurpriseMod;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;

/**
 * 兼容性注册器。
 * <p>
 * 在主模组构造阶段调用 {@link #register(IEventBus)}，按需检测第三方模组并注册对应的集成：
 * <ul>
 *   <li>Jade：通过 {@code @WailaPlugin} 注解由 Jade 自动发现，此处仅记录日志。</li>
 *   <li>ToroHealth：检测到后向 MOD 事件总线注册 GUI 覆盖层监听器。</li>
 * </ul>
 * 所有检测均包裹在 try-catch 中，确保任一兼容失败都不会影响主模组加载。
 */
public class CompatRegistry {

	private static final String JADE_MODID = "jade";
	private static final String TOROHEALTH_MODID = "torohealth";

	/**
	 * 注册所有兼容性集成。应在主模组构造函数中调用。
	 *
	 * @param modEventBus MOD 事件总线
	 */
	public static void register(IEventBus modEventBus) {
		if (modEventBus == null) {
			RandomSurpriseMod.LOGGER.warn("[兼容] MOD 事件总线为空，跳过兼容性注册");
			return;
		}
		registerJade();
		registerToroHealth(modEventBus);
		registerInfernalMobs();
	}

	/**
	 * Infernal Mobs 集成：检测到后启用动态精英怪生成率调节
	 * 实际调节逻辑在 InfernalMobsCompat.onServerTick 中执行
	 */
	private static void registerInfernalMobs() {
		try {
			if (isLoaded("infernalmobs")) {
				RandomSurpriseMod.LOGGER.info("[兼容] 检测到 Infernal Mobs，动态精英怪生成率调节已就绪");
			}
		} catch (Throwable t) {
			RandomSurpriseMod.LOGGER.warn("[兼容] Infernal Mobs 检测失败，已降级: {}", t.getMessage());
		}
	}

	/**
	 * Jade 集成：Jade 通过类路径扫描 {@code @WailaPlugin} 注解自动发现
	 * {@link com.randomsurprise.compat.jade.JadePlugin}，无需手动注册。
	 */
	private static void registerJade() {
		try {
			if (isLoaded(JADE_MODID)) {
				RandomSurpriseMod.LOGGER.info("[兼容] 检测到 Jade，词条悬浮提示集成已就绪（由 Jade 自动发现）");
			}
		} catch (Throwable t) {
			RandomSurpriseMod.LOGGER.warn("[兼容] Jade 检测失败，已降级: {}", t.getMessage());
		}
	}

	/**
	 * ToroHealth 集成：检测到后注册 GUI 覆盖层。
	 * RegisterGuiOverlaysEvent 仅在客户端触发，因此服务端不会加载客户端类。
	 */
	private static void registerToroHealth(IEventBus modEventBus) {
		try {
			if (isLoaded(TOROHEALTH_MODID)) {
				modEventBus.addListener(com.randomsurprise.compat.torohealth.ToroHealthCompat::registerOverlay);
				RandomSurpriseMod.LOGGER.info("[兼容] 检测到 ToroHealth，词条 HUD 覆盖层监听器已注册");
			}
		} catch (Throwable t) {
			RandomSurpriseMod.LOGGER.warn("[兼容] ToroHealth 检测失败，已降级: {}", t.getMessage());
		}
	}

	/**
	 * 安全检测某模组是否已加载。
	 */
	private static boolean isLoaded(String modId) {
		try {
			ModList modList = ModList.get();
			return modList != null && modList.isLoaded(modId);
		} catch (Throwable t) {
			return false;
		}
	}

	/** 防止实例化 */
	private CompatRegistry() {
	}
}
