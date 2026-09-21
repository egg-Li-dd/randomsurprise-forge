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
 * 随机事件倒计时HUD（简化版）
 * 纯文字 + 半透明背景，避免与其他模组HUD重叠
 */
@Mod.EventBusSubscriber(modid = RandomSurpriseMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class TimerHud {
	private static int remainingSeconds = 0;
	/** 缓存的文字宽度（不随blink变化，避免每帧调用font.width） */
	private static int cachedTextWidth = 0;

	public static void update(int seconds) {
		remainingSeconds = seconds;
		if (seconds > 0) {
			// 预计算文字宽度（用无样式占位文本，宽度不受颜色影响）
			Component text = Component.literal("")
				.append(Component.literal("\u26A0 随机事件: "))
				.append(Component.literal(seconds + "s"));
			Minecraft mc = Minecraft.getInstance();
			cachedTextWidth = mc.font.width(text);
		}
	}

	@SubscribeEvent
	public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
		event.registerAboveAll("timer_hud", TIMER_HUD);
		// v18: 血量数字显示，渲染在原版血条上方
		event.registerAboveAll("health_number", HEALTH_NUMBER_HUD);
	}

	public static final IGuiOverlay TIMER_HUD = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.screen != null) return;
		if (remainingSeconds <= 0) return;

		long time = System.currentTimeMillis();
		boolean critical = remainingSeconds <= 10;
		boolean blink = critical && (time / 250) % 2 == 0;

		// 秒数颜色：>30 绿、10-30 橙、<10 红闪烁
		int secondsColor;
		if (critical) {
			secondsColor = blink ? 0xFFFF0000 : 0xFFFF5555;
		} else if (remainingSeconds <= 30) {
			secondsColor = 0xFFFFAA00;
		} else {
			secondsColor = 0xFF55FF55;
		}

		Component fullText = Component.literal("")
				.append(Component.literal("\u26A0 随机事件: ").withStyle(s -> s.withColor(0xFFAAAAAA)))
				.append(Component.literal(remainingSeconds + "s").withStyle(s -> s.withColor(secondsColor).withBold(true)));

		// 使用缓存的文字宽度，避免每帧调用font.width（仅当未缓存时回退到实时计算）
		int textWidth = cachedTextWidth > 0 ? cachedTextWidth : mc.font.width(fullText);
		int centerX = screenWidth / 2;
		int x = centerX - textWidth / 2;
		int y = 35;  // 顶部下移35px，避开其他模组HUD
		int padX = 3;

		// 半透明黑色背景
		graphics.fill(x - padX, y - 2, x + textWidth + padX, y + mc.font.lineHeight + 2, 0x80000000);

		// 文字
		graphics.drawString(mc.font, fullText, x, y, 0xFFFFFFFF, false);
	};

	/** v18: 血量数字HUD - 在血条旁显示当前/最大血量数字 */
	public static final IGuiOverlay HEALTH_NUMBER_HUD = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.screen != null) return;

		float health = mc.player.getHealth();
		float maxHealth = mc.player.getMaxHealth();
		float absorption = mc.player.getAbsorptionAmount();

		// 血量数字格式: 当前/最大 (吸收)
		String healthText;
		int color;
		if (absorption > 0) {
			healthText = String.format("%.0f/%.0f+%.0f", health, maxHealth, absorption);
			color = 0xFFFFAA00; // 金色（有吸收护盾）
		} else {
			healthText = String.format("%.0f/%.0f", health, maxHealth);
			// 血量颜色: >50%绿、20-50%黄、<20%红
			float ratio = health / maxHealth;
			if (ratio > 0.5f) {
				color = 0xFF55FF55; // 绿色
			} else if (ratio > 0.2f) {
				color = 0xFFFFFF55; // 黄色
			} else {
				color = 0xFFFF5555; // 红色
			}
		}

		// 绘制位置: 屏幕左下方，原版血条上方
		// 原版血条在 y = screenHeight - 39 附近
		int x = 10;
		int y = screenHeight - 52;
		int textWidth = mc.font.width(healthText);

		// 半透明背景
		graphics.fill(x - 2, y - 1, x + textWidth + 2, y + mc.font.lineHeight + 1, 0x80000000);
		// 血量数字（带阴影）
		graphics.drawString(mc.font, healthText, x, y, color, true);
	};
}
