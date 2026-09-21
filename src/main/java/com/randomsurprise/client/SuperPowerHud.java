package com.randomsurprise.client;

import com.randomsurprise.RandomSurpriseMod;
import com.randomsurprise.superpower.SuperPower;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RandomSurpriseMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SuperPowerHud {

/** 缓存当前超能力名称Component（仅在能力切换时重建） */
private static Component cachedName = null;
/** 缓存当前超能力类型（用于检测是否变化） */
private static SuperPower cachedSp = null;

@SubscribeEvent
public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
event.registerAboveAll("superpower_hud", SUPERPOWER_HUD);
}

public static final IGuiOverlay SUPERPOWER_HUD = (gui, graphics, partialTick, screenWidth, screenHeight) -> {
Minecraft mc = Minecraft.getInstance();
if (mc.player == null || mc.screen != null) return;

SuperPower sp = ClientSuperPowerData.getSuperPower();
if (sp == null) return;

// 缓存超能力名称Component：仅在能力切换时重建
if (cachedSp != sp) {
cachedSp = sp;
cachedName = Component.translatable(sp.getNameKey());
}
Component nameComp = cachedName;

int boxWidth = 120;
int boxHeight = 36;
int x = screenWidth - boxWidth - 4;
int y = screenHeight - boxHeight - 4;

graphics.fill(x, y, x + boxWidth, y + boxHeight, 0x80000000);
graphics.fill(x, y, x + 2, y + boxHeight, sp.getColor() | 0xFF000000);

graphics.drawString(mc.font, nameComp, x + 6, y + 3, 0xFFFFFFFF, true);

int cooldown = ClientSuperPowerData.getCooldownSeconds();
boolean isFlying = ClientSuperPowerData.isFlying();
boolean isCleansing = ClientSuperPowerData.isCleansing();
boolean passiveOff = !ClientSuperPowerData.isPassiveEnabled();

int lineY = y + 14;
if (sp.hasActiveAction() && cooldown > 0) {
Component cdText = Component.literal("CD: " + cooldown + "s").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF5555)));
graphics.drawString(mc.font, cdText, x + 6, lineY, 0xFFFFFFFF, false);
lineY += 9;
} else if (sp.hasActiveAction()) {
Component readyText = Component.literal("[G] 可用").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x55FF55)));
graphics.drawString(mc.font, readyText, x + 6, lineY, 0xFFFFFFFF, false);
lineY += 9;
}

if (isFlying) {
int ticks = ClientSuperPowerData.getStateTicks();
int seconds = Math.max(1, ticks / 20);
Component flyText = Component.literal("飞行: " + seconds + "s").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x55FFFF)));
graphics.drawString(mc.font, flyText, x + 6, lineY, 0xFFFFFFFF, false);
lineY += 9;
} else if (isCleansing) {
int ticks = ClientSuperPowerData.getStateTicks();
int seconds = Math.max(1, ticks / 20);
Component cleanText = Component.literal("清除: " + seconds + "s").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x55FF55)));
graphics.drawString(mc.font, cleanText, x + 6, lineY, 0xFFFFFFFF, false);
lineY += 9;
}

if (sp.isToggleable()) {
Component stateText;
if (passiveOff) {
stateText = Component.literal("[V] 关").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF5555)));
} else {
stateText = Component.literal("[V] 开").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x55FF55)));
}
graphics.drawString(mc.font, stateText, x + 6, lineY, 0xFFFFFFFF, false);
}
};
}