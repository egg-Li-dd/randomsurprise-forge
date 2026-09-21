package com.randomsurprise.client;

import com.randomsurprise.RandomSurpriseMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 征召世界战场HUD（紧凑版）
 * 位置：屏幕右上角，不影响玩家正常游玩
 * 风格：暗黑邪能 - 深紫黑背景 + 血红边框 + 邪能紫/绿发光
 * 动画：边框脉动光晕 + Boss出现放大动画 + 屏幕边缘红光
 */
@Mod.EventBusSubscriber(modid = RandomSurpriseMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class BattlefieldHud {
	private static int mobCount = 0;
	private static int bossCount = 0;
	private static int lastBossCount = 0;
	private static long bossAppearAnimStart = 0;

	// v20: 胜利停留阶段
	private static boolean victoryActive = false;
	private static int victorySeconds = 0;
	private static float spaceHoldProgress = 0f; // 0.0~1.0

	public static void update(int mobs, int bosses) {
		if (bosses > lastBossCount) {
			bossAppearAnimStart = System.currentTimeMillis();
		}
		lastBossCount = bosses;
		mobCount = mobs;
		bossCount = bosses;
	}

	public static void clear() {
		mobCount = 0;
		bossCount = 0;
		lastBossCount = 0;
	}

	/** 设置胜利停留阶段状态（由 BattlefieldVictoryPayload 调用） */
	public static void setVictoryPhase(boolean active, int seconds) {
		victoryActive = active;
		victorySeconds = seconds;
		if (!active) {
			spaceHoldProgress = 0f;
		}
	}

	public static boolean isVictoryActive() {
		return victoryActive;
	}

	/** 更新空格长按进度（0.0~1.0，由 ClientRandomSurpriseMod 每tick调用） */
	public static void setSpaceHoldProgress(float progress) {
		spaceHoldProgress = Math.max(0f, Math.min(1f, progress));
	}

	@SubscribeEvent
	public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
		event.registerAboveAll("battlefield_hud", BATTLEFIELD_HUD);
	}

	public static final IGuiOverlay BATTLEFIELD_HUD = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.screen != null) return;

		// v20: 胜利停留阶段HUD（居中顶部，独立于战场怪物计数）
		if (victoryActive) {
			renderVictoryHud(graphics, mc, screenWidth, screenHeight);
		}

		if (mobCount <= 0 && bossCount <= 0 && !victoryActive) return;

		long time = System.currentTimeMillis();
		float pulse = (float)(Math.sin(time / 700.0) * 0.5 + 0.5);

		// Boss出现动画（300ms内轻微放大）
		long animElapsed = time - bossAppearAnimStart;
		float scale = 1.0f;
		if (animElapsed < 300) {
			float animProgress = animElapsed / 300.0f;
			scale = 1.0f + 0.08f * (float)Math.sin(animProgress * Math.PI);
		}

		boolean hasBoss = bossCount > 0;

		// 紧凑布局：单行显示 "征召世界  ☠X  ⚔X"
		Component titlePart = Component.literal("\u00A7d\u00A7l\u00A7o征召世界");
		Component bossPart = Component.literal("\u00A7c\u2620\u00A7l" + bossCount);
		Component mobPart = Component.literal("\u00A76\u2694\u00A7l" + mobCount);
		Component fullText = Component.literal("")
				.append(titlePart)
				.append("  ")
				.append(bossPart)
				.append("  ")
				.append(mobPart);

		int textWidth = mc.font.width(fullText);
		int lineHeight = mc.font.lineHeight;
		int padX = 6;
		int padY = 3;
		int boxWidth = (int)((textWidth + padX * 2) * scale);
		int boxHeight = (int)((lineHeight + padY * 2) * scale);

		// 右上角位置（距右边和上边各4px）
		int x = screenWidth - boxWidth - 4;
		int y = 4;

		// === 外层脉动光晕（紧凑，仅1px外扩）===
		int glowAlpha = (int)(30 + pulse * 50);
		int glowColor = hasBoss ? (glowAlpha << 24) | 0xFF0000 : (glowAlpha << 24) | 0x8B00FF;
		graphics.fill(x - 2, y - 2, x + boxWidth + 2, y - 1, glowColor);
		graphics.fill(x - 2, y + boxHeight + 1, x + boxWidth + 2, y + boxHeight + 2, glowColor);
		graphics.fill(x - 2, y - 2, x - 1, y + boxHeight + 2, glowColor);
		graphics.fill(x + boxWidth + 1, y - 2, x + boxWidth + 2, y + boxHeight + 2, glowColor);

		// === 渐变背景（深紫黑→纯黑，紧凑）===
		for (int i = 0; i < boxHeight; i++) {
			float ratio = (float)i / boxHeight;
			int r = (int)(0x1A * (1 - ratio) + 0x05 * ratio);
			int g = (int)(0x05 * (1 - ratio) + 0x00 * ratio);
			int b = (int)(0x1A * (1 - ratio) + 0x08 * ratio);
			graphics.fill(x, y + i, x + boxWidth, y + i + 1, (0xD0 << 24) | (r << 16) | (g << 8) | b);
		}

		// === 血红边框（1px紧凑）===
		int borderColor = hasBoss
				? 0xFF000000 | ((int)(0x8B + pulse * 0x74) << 16)
				: 0xFF6B0000;
		graphics.fill(x, y, x + boxWidth, y + 1, borderColor);
		graphics.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, borderColor);
		graphics.fill(x, y, x + 1, y + boxHeight, borderColor);
		graphics.fill(x + boxWidth - 1, y, x + boxWidth, y + boxHeight, borderColor);

		// === 文字 ===
		graphics.drawString(mc.font, fullText, x + padX, y + padY, 0xFFFFFFFF, false);

		// === Boss存在时屏幕边缘红光（保留，但降低强度）===
		if (hasBoss) {
			int vignetteAlpha = 20 + (int)(pulse * 15);
			int vignetteWidth = 25;
			for (int i = 0; i < vignetteWidth; i++) {
				int a = (int)(vignetteAlpha * (1 - (float)i / vignetteWidth));
				graphics.fill(0, i, screenWidth, i + 1, (a << 24) | 0xFF0000);
			}
			for (int i = 0; i < vignetteWidth; i++) {
				int a = (int)(vignetteAlpha * (1 - (float)i / vignetteWidth));
				graphics.fill(0, screenHeight - i - 1, screenWidth, screenHeight - i, (a << 24) | 0xFF0000);
			}
			for (int i = 0; i < vignetteWidth; i++) {
				int a = (int)(vignetteAlpha * (1 - (float)i / vignetteWidth));
				graphics.fill(i, 0, i + 1, screenHeight, (a << 24) | 0xFF0000);
			}
			for (int i = 0; i < vignetteWidth; i++) {
				int a = (int)(vignetteAlpha * (1 - (float)i / vignetteWidth));
				graphics.fill(screenWidth - i - 1, 0, screenWidth - i, screenHeight, (a << 24) | 0xFF0000);
			}
		}
	};

	/** v20: 胜利停留阶段HUD渲染 */
	private static void renderVictoryHud(GuiGraphics graphics, Minecraft mc, int screenWidth, int screenHeight) {
		long time = System.currentTimeMillis();
		float pulse = (float)(Math.sin(time / 500.0) * 0.5 + 0.5);

		// 主标题
		Component title = Component.literal("\u00A7e\u00A7l\u2728 \u5F81\u53EC\u6210\u529F\uFF01\u2728");
		int titleWidth = mc.font.width(title);

		// 倒计时
		Component countdown = Component.literal("\u00A7a\u00A7l" + victorySeconds + "\u00A7r\u79D2\u540E\u81EA\u52A8\u9000\u51FA");
		int countdownWidth = mc.font.width(countdown);

		// 空格提示
		String barFill = "";
		int barLength = 20;
		int filledBars = (int)(spaceHoldProgress * barLength);
		for (int i = 0; i < barLength; i++) {
			barFill += (i < filledBars) ? "\u25AC" : "_";
		}
		Component spaceHint = Component.literal(
				"\u00A77\u957F\u6309\u7A7A\u683C\u63D0\u524D\u9000\u51FA [" + barFill + "]");

		int boxWidth = Math.max(titleWidth, Math.max(countdownWidth, mc.font.width(spaceHint))) + 24;
		int boxHeight = 52;
		int x = (screenWidth - boxWidth) / 2;
		int y = 35; // 距顶部35px，与其他HUD一致

		// 渐变背景（金色边框 + 深黑背景）
		for (int i = 0; i < boxHeight; i++) {
			float ratio = (float)i / boxHeight;
			int r = (int)(0x1A * (1 - ratio) + 0x00 * ratio);
			int g = (int)(0x14 * (1 - ratio) + 0x00 * ratio);
			int b = (int)(0x00 * (1 - ratio) + 0x00 * ratio);
			graphics.fill(x, y + i, x + boxWidth, y + i + 1, (0xE0 << 24) | (r << 16) | (g << 8) | b);
		}

		// 金色脉动边框
		int borderR = (int)(0xFF + pulse * 0x00);
		int borderG = (int)(0xC8 + pulse * 0x37);
		int borderB = (int)(0x00 + pulse * 0x00);
		int borderColor = 0xFF000000 | (borderR << 16) | (borderG << 8) | borderB;
		graphics.fill(x, y, x + boxWidth, y + 2, borderColor);
		graphics.fill(x, y + boxHeight - 2, x + boxWidth, y + boxHeight, borderColor);
		graphics.fill(x, y, x + 2, y + boxHeight, borderColor);
		graphics.fill(x + boxWidth - 2, y, x + boxWidth, y + boxHeight, borderColor);

		// 文字渲染
		int textY = y + 6;
		graphics.drawCenteredString(mc.font, title, screenWidth / 2, textY, 0xFFFFFFFF);
		textY += 14;
		graphics.drawCenteredString(mc.font, countdown, screenWidth / 2, textY, 0xFFFFFFFF);
		textY += 14;
		graphics.drawCenteredString(mc.font, spaceHint, screenWidth / 2, textY, 0xFFFFFFFF);
	}
}
