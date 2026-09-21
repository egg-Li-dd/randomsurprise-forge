package com.randomsurprise.compat.torohealth;

import com.randomsurprise.RandomSurpriseMod;
import com.randomsurprise.affix.Affix;
import com.randomsurprise.affix.AffixRarity;
import com.randomsurprise.client.ClientAffixData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ToroHealth 模组集成（Forge 1.20.1）。
 * <p>
 * ToroHealth 未公开稳定的扩展 API（无 IHUDProvider / DisplayConfigurationSynchronizer），
 * 其血条渲染通过 Mixin 实现。为保证编译与运行安全，本类采用独立的 Forge GUI 覆盖层，
 * 在屏幕左上角（ToroHealth 血条下方）渲染当前玩家拥有的词条数量与稀有度分布。
 * <p>
 * 仅在检测到 ToroHealth 已加载时由 {@link com.randomsurprise.compat.CompatRegistry} 注册，
 * 不会影响移动端 / 服务端逻辑。
 */
public class ToroHealthCompat {

	/** HUD 距屏幕左侧的间距 */
	private static final int MARGIN_X = 4;
	/** HUD 起始 Y 坐标（位于 ToroHealth 血条下方） */
	private static final int START_Y = 70;
	/** 每行行高 */
	private static final int LINE_HEIGHT = 11;

	/**
	 * 词条信息覆盖层实例。
	 */
	public static final IGuiOverlay AFFIX_HUD = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.screen != null) return;
		try {
			List<Affix> affixes = ClientAffixData.getOwnAffixes();
			if (affixes == null || affixes.isEmpty()) return;

			// 统计稀有度分布与好坏词条数量
			Map<AffixRarity, Integer> rarityDist = new LinkedHashMap<>();
			int goodCount = 0;
			int badCount = 0;
			for (Affix affix : affixes) {
				rarityDist.merge(affix.getRarity(), 1, Integer::sum);
				if (affix.isGood()) {
					goodCount++;
				} else {
					badCount++;
				}
			}

			int x = MARGIN_X;
			int y = START_Y;
			int lines = 2 + rarityDist.size();
			int boxWidth = 130;

			// 半透明背景
			graphics.fill(x - 2, y - 2, x + boxWidth, y + lines * LINE_HEIGHT + 2, 0x80000000);

			// 标题
			MutableComponent title = Component.translatable("compat.randomsurprise.torohealth.title")
					.withStyle(style -> style.withColor(0xFFFFD700));
			graphics.drawString(mc.font, title, x, y, 0xFFFFFFFF, false);
			y += LINE_HEIGHT;

			// 数量汇总行
			Component countLine = Component.translatable(
					"compat.randomsurprise.torohealth.count", affixes.size(), goodCount, badCount)
					.withStyle(style -> style.withColor(0xFFCCCCCC));
			graphics.drawString(mc.font, countLine, x, y, 0xFFFFFFFF, false);
			y += LINE_HEIGHT;

			// 稀有度分布
			for (Map.Entry<AffixRarity, Integer> entry : rarityDist.entrySet()) {
				AffixRarity rarity = entry.getKey();
				MutableComponent line = Component.literal(rarity.getDisplayName() + " x" + entry.getValue())
						.withStyle(style -> style.withColor(rarity.getColor()));
				graphics.drawString(mc.font, line, x, y, 0xFFFFFFFF, false);
				y += LINE_HEIGHT;
			}
		} catch (Throwable t) {
			RandomSurpriseMod.LOGGER.warn("[ToroHealth 兼容] HUD 渲染异常: {}", t.getMessage());
		}
	};

	/**
	 * 注册 GUI 覆盖层。由 {@link com.randomsurprise.compat.CompatRegistry} 在检测到
	 * ToroHealth 时通过 MOD 事件总线调用。
	 */
	public static void registerOverlay(RegisterGuiOverlaysEvent event) {
		event.registerAboveAll("randomsurprise_affix_torohealth", AFFIX_HUD);
		RandomSurpriseMod.LOGGER.info("[ToroHealth 兼容] 词条 HUD 覆盖层已注册");
	}

	/** 防止实例化 */
	private ToroHealthCompat() {
	}

	/**
	 * 提供给外部使用的 GuiGraphics 类型引用，避免编译期直接依赖被裁剪。
	 * 保留以明确客户端用途。
	 */
	@SuppressWarnings("unused")
	private static GuiGraphics unusedTypeAnchor(GuiGraphics g) {
		return g;
	}
}
