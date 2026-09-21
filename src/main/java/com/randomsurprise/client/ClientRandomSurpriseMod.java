package com.randomsurprise.client;

import com.randomsurprise.RandomSurpriseMod;
import com.randomsurprise.network.EnchantedBookApplyPayload;
import com.randomsurprise.network.ModNetworking;
import com.randomsurprise.network.ReviveActionPayload;
import com.randomsurprise.network.SuperPowerActionPayload;
import com.randomsurprise.network.SuperPowerTogglePayload;
import com.randomsurprise.superpower.SuperPower;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端入口（Forge 1.20.1 版本）
 * - 监听 R 键打开词条面板
 * - 监听 P 键打开属性面板
 * - 监听 G 键释放超能力主动技能
 * - 监听 V 键切换被动开关
 * - 跑酷达人二段跳：客户端本地检测跳跃键按下
 * - 商店入口：在生存背包界面左上角添加商店按钮
 *
 * 网络接收器已在各 Payload 的 handle() 方法中处理，HUD 注册已在 TimerHud/SuperPowerHud
 * 的 RegisterGuiOverlaysEvent 中处理，本类仅保留按键监听和背包按钮。
 */
@Mod.EventBusSubscriber(modid = RandomSurpriseMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientRandomSurpriseMod {

/** 上次 R 键状态（用于检测按下事件，避免长按重复触发） */
private static boolean rKeyWasDown = false;
/** 上次 G 键状态 */
private static boolean gKeyWasDown = false;
/** 上次 V 键状态 */
private static boolean vKeyWasDown = false;
/** 上次跳跃键状态（用于二段跳检测） */
private static boolean jumpKeyWasDown = false;
/** 上次右键状态（用于倒地自复活） */
private static boolean rightClickWasDown = false;
/** 上次 P 键状态（用于属性面板） */
private static boolean pKeyWasDown = false;
/** 上次中键状态（用于蹲下+中键查看生物词条） */
private static boolean sneakMButtonWasDown = false;
/** 缓存反射获取的 hoveredSlot Field（避免每次点击都调用 getDeclaredField + setAccessible） */
private static java.lang.reflect.Field hoveredSlotField = null;
/** v20: 胜利停留阶段空格长按计数（5秒=100tick） */
private static int victorySpaceHoldTicks = 0;
private static final int VICTORY_SPACE_HOLD_REQUIRED = 100;

@SubscribeEvent
public static void onClientTick(TickEvent.ClientTickEvent event) {
if (event.phase != TickEvent.Phase.END) return;
Minecraft client = Minecraft.getInstance();
if (client.player == null) return;
if (client.screen != null) return;

long window = client.getWindow().getWindow();

// R 键：打开词条面板
boolean rDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
if (rDown && !rKeyWasDown) {
client.setScreen(new AffixListScreen());
}
rKeyWasDown = rDown;

// P 键：打开属性面板
boolean pDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_P) == GLFW.GLFW_PRESS;
if (pDown && !pKeyWasDown) {
client.setScreen(new AttributePanelScreen());
}
pKeyWasDown = pDown;

// G 键：释放超能力主动技能
boolean gDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;
if (gDown && !gKeyWasDown) {
SuperPower sp = ClientSuperPowerData.getSuperPower();
if (sp != null && sp.hasActiveAction()) {
com.randomsurprise.network.ModNetworking.sendToServer(new SuperPowerActionPayload());
}
}
gKeyWasDown = gDown;

// V 键：切换被动技能开关
		boolean vDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_V) == GLFW.GLFW_PRESS;
		if (vDown && !vKeyWasDown) {
			SuperPower sp = ClientSuperPowerData.getSuperPower();
			if (sp != null && sp.isToggleable()) {
				com.randomsurprise.network.ModNetworking.sendToServer(new SuperPowerTogglePayload());
			}
		}
		vKeyWasDown = vDown;

	// v20: 胜利停留阶段 — 长按空格5秒提前退出
	if (BattlefieldHud.isVictoryActive()) {
		boolean spaceDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
		if (spaceDown) {
			victorySpaceHoldTicks++;
			BattlefieldHud.setSpaceHoldProgress((float) victorySpaceHoldTicks / VICTORY_SPACE_HOLD_REQUIRED);
			if (victorySpaceHoldTicks >= VICTORY_SPACE_HOLD_REQUIRED) {
				com.randomsurprise.network.ModNetworking.sendToServer(
						new com.randomsurprise.network.BattlefieldEarlyExitPayload());
				victorySpaceHoldTicks = 0;
				BattlefieldHud.setSpaceHoldProgress(0f);
			}
		} else {
			victorySpaceHoldTicks = 0;
			BattlefieldHud.setSpaceHoldProgress(0f);
		}
	} else {
		victorySpaceHoldTicks = 0;
	}

	// 跑酷达人二段跳
		handleParkourDoubleJump(client, window);

		// 右键长按：倒地自复活 / 队友救助
		handleReviveRightClick(client, window);

		// 蹲下 + 中键：查看生物敌对词条
		boolean sneakDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
		boolean mButtonDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
		if (sneakDown && mButtonDown && !sneakMButtonWasDown) {
			if (client.hitResult != null && client.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
				net.minecraft.world.entity.Entity target = ((net.minecraft.world.phys.EntityHitResult)client.hitResult).getEntity();
				showMobAffixInfo(target);
			}
		}
		sneakMButtonWasDown = mButtonDown;
	}

@SubscribeEvent
public static void onScreenInit(ScreenEvent.Init.Post event) {
var screen = event.getScreen();
if (screen instanceof InventoryScreen inventory) {
Button shopButton = Button.builder(
Component.translatable("shop.randomsurprise.button"),
button -> Minecraft.getInstance().setScreen(new ShopScreen())
).bounds(45, 4, 60, 16).build();
event.addListener(shopButton);
}
}

/** 打开钱袋子界面 */
public static void openMoneyBagScreen(net.minecraft.world.item.ItemStack stack, net.minecraft.world.InteractionHand hand) {
Minecraft.getInstance().setScreen(new MoneyBagScreen(stack, hand));
}

/**
 * 背包界面渲染后事件：在背包界面左侧绘制词条属性汇总面板
 * 仅在生存/创造背包界面（InventoryScreen）触发，一列紧凑布局
 */
@SubscribeEvent
public static void onScreenRender(ScreenEvent.Render.Post event) {
if (event.getScreen() instanceof InventoryScreen inventory) {
AffixStatsPanel.render(event.getGuiGraphics(), inventory);
}
}

/**
 * 背包界面鼠标滚轮事件：处理词条属性面板滚动
 */
@SubscribeEvent
public static void onMouseScrolled(ScreenEvent.MouseScrolled event) {
if (event.getScreen() instanceof InventoryScreen) {
if (AffixStatsPanel.mouseScrolled(event.getMouseX(), event.getMouseY(), event.getScrollDelta())) {
event.setCanceled(true);
}
}
}

/**
 * 背包/容器界面鼠标左键点击事件：附魔书拖到物品上直接应用附魔
 * 当玩家光标持有附魔书并左键点击一个可附魔物品时，取消原版交换行为，
 * 发送自定义数据包到服务端应用附魔并消耗附魔书
 */
@SubscribeEvent
public static void onScreenMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
if (!(event.getScreen() instanceof AbstractContainerScreen<?> containerScreen)) return;
if (event.getButton() != 0) return; // 仅左键

Minecraft mc = Minecraft.getInstance();
if (mc.player == null) return;

// 检查光标上是否是附魔书
ItemStack cursorStack = mc.player.containerMenu.getCarried();
if (!cursorStack.is(Items.ENCHANTED_BOOK)) return;

// 通过反射获取鼠标悬停的槽位（hoveredSlot 是 protected 字段），缓存Field避免重复查找
if (hoveredSlotField == null) {
try {
hoveredSlotField = AbstractContainerScreen.class.getDeclaredField("hoveredSlot");
hoveredSlotField.setAccessible(true);
} catch (Exception e) {
return;
}
}
Slot hoveredSlot;
try {
hoveredSlot = (Slot) hoveredSlotField.get(containerScreen);
} catch (Exception e) {
return;
}
if (hoveredSlot == null || !hoveredSlot.hasItem()) return;

ItemStack targetStack = hoveredSlot.getItem();
if (targetStack.isEmpty() || targetStack.is(Items.ENCHANTED_BOOK)) return;

// 取消原版点击行为（防止物品交换），发送自定义数据包
event.setCanceled(true);
ModNetworking.sendToServer(new EnchantedBookApplyPayload(hoveredSlot.index));
}

/**
 * 跑酷达人二段跳处理
 * - 落地后重置二段跳次数
 * - 第一次起跳后在空中再按一次空格触发二段跳（仅边沿触发，需松开再按）
 */
private static void handleParkourDoubleJump(Minecraft client, long window) {
	SuperPower sp = ClientSuperPowerData.getSuperPower();
	if (sp != SuperPower.PARKOUR) return;
	if (!ClientSuperPowerData.isPassiveEnabled()) return;
	if (client.player == null) return;
	// 飞行/骑乘/在水中时不触发二段跳
	if (client.player.getAbilities().flying) return;
	if (client.player.isPassenger()) return;
	if (client.player.isInWater() || client.player.isInLava()) return;

	boolean jumpDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;

	// 落地重置二段跳次数
	if (client.player.onGround()) {
		ClientSuperPowerData.setDoubleJumpUsed(false);
	}

	// 仅边沿触发（松开再按）：在空中 + 未使用二段跳
	if (jumpDown && !jumpKeyWasDown
			&& !client.player.onGround() && !ClientSuperPowerData.isDoubleJumpUsed()) {
		client.player.jumpFromGround();
		ClientSuperPowerData.setDoubleJumpUsed(true);
		client.player.playSound(net.minecraft.sounds.SoundEvents.SLIME_BLOCK_FALL,
				0.5F, 1.5F);
	}
	jumpKeyWasDown = jumpDown;
}

/**
 * 右键长按复活处理：
 * - 自己倒地时：自救已取消，右键无效（只能由队友靠近救助）
 * - 自己未倒地时：对准倒地队友长按右键可辅助触发救助（服务端已改为靠近即可）
 */
private static void handleReviveRightClick(Minecraft client, long window) {
	if (client.player == null) return;

	boolean rightDown = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

	if (isLocalPlayerDowned(client.player)) {
		// === 自救已取消：倒地后只能由队友靠近救助，自己右键无效 ===
		// 不发送自救包，等待队友靠近救助
	} else {
		// === 队友救助模式 ===
		// 仅在右键按下或刚松开时才搜索倒地队友，避免每tick无条件遍历实体
		if (rightDown || rightClickWasDown) {
			Player target = findDownedPlayerInCrosshair(client);
			if (target != null) {
				if (rightDown && !rightClickWasDown) {
					// 开始救助队友
					com.randomsurprise.network.ModNetworking.sendToServer(
							new ReviveActionPayload(target.getUUID(), true));
					client.player.sendSystemMessage(Component.translatable(
							"battlefield.randomsurprise.reviving", target.getDisplayName().getString()));
				} else if (!rightDown && rightClickWasDown) {
					// 停止救助队友
					com.randomsurprise.network.ModNetworking.sendToServer(
							new ReviveActionPayload(target.getUUID(), false));
				}
			} else if (!rightDown && rightClickWasDown) {
				// 右键松开但当前未对准倒地队友 → 发送停止救助（防止进度卡住）
				// 无法确定之前的targetId，服务端会在tick中自动清理无效救助者
			}
		}
	}

	rightClickWasDown = rightDown;
}

/**
 * 判断本地玩家是否处于倒地状态（通过发光+缓慢+虚弱效果判断）
 */
private static boolean isLocalPlayerDowned(Player player) {
	return player.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)
			&& player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)
			&& player.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
}

/**
 * 检测附近的倒地队友（用于队友救助）
 * 遍历5格内的实体找最近的倒地玩家（与服务端 ALLY_REVIVE_MAX_DISTANCE=5 一致）
 */
private static Player findDownedPlayerInCrosshair(Minecraft client) {
	if (client.cameraEntity == null || client.player == null || client.level == null) return null;
	// 遍历5格内的实体找倒地玩家（与服务端 ALLY_REVIVE_MAX_DISTANCE=5 一致）
	double maxDist = 5.0;
	Player closest = null;
	double closestDist = maxDist * maxDist;
	net.minecraft.world.phys.AABB searchBox = client.player.getBoundingBox().inflate(maxDist);
	for (Player other : client.level.getEntitiesOfClass(Player.class, searchBox)) {
		if (other == client.player) continue;
		// 检测目标是否处于倒地状态（药水效果判断，客户端可用）
		if (!isLocalPlayerDowned(other)) continue;
		double d = client.player.distanceToSqr(other);
		if (d < closestDist) {
			closestDist = d;
			closest = other;
		}
	}
	return closest;
}

/**
 * 当玩家蹲下+中键点击生物时调用
 * v18: 修复显示全局坏词条的问题，改为显示该生物自身的词条
 * 通过读取生物的 PersistentData 中存储的词条信息
 */
private static void showMobAffixInfo(net.minecraft.world.entity.Entity target) {
	var mc = Minecraft.getInstance();
	if (mc.player == null) return;

	// 检查目标是否为敌对生物
	if (!(target instanceof net.minecraft.world.entity.Mob mob)) {
		mc.player.displayClientMessage(Component.literal("§7该目标不是生物"), false);
		return;
	}

	mc.player.displayClientMessage(Component.literal("§6§l【" + target.getName().getString() + " 的词条信息】"), false);

	// 读取该生物自身的词条数据（通过 PersistentData）
	var data = target.getPersistentData();
	boolean hasAffix = false;

	// 检查生物是否有个体词条标记
	if (data.contains("randomsurprise.mob_affixes", 9)) { // 9 = TAG_LIST
		var affixList = data.getList("randomsurprise.mob_affixes", 8); // 8 = TAG_STRING
		int count = 0;
		for (int i = 0; i < affixList.size(); i++) {
			String affixId = affixList.getString(i);
			com.randomsurprise.affix.Affix affix = com.randomsurprise.affix.AffixRegistry.getById(affixId);
			if (affix == null) continue;
			Component name = Component.translatable(affix.getNameKey());
			String typeStr = affix.isGood() ? "§a[好词条]" : "§c[坏词条]";
			String bossStr = affix.isBossOnly() ? " §6[Boss专属]" : "";
			mc.player.displayClientMessage(
				Component.literal("  §f" + (count+1) + ". ").append(name)
					.append(Component.literal(" " + typeStr + bossStr + " §8[" + affix.getRarity().name() + "]")), false);
			count++;
			hasAffix = true;
		}
		if (count > 0) {
			mc.player.displayClientMessage(Component.literal("§8该生物持有 " + count + " 个词条"), false);
		}
	}

	// 同时显示全局坏词条对该生物的影响
	var allBad = ClientAffixData.getAllPlayerBadAffixes();
	int globalCount = 0;
	for (var entry : allBad) {
		for (String affixId : entry.affixIds()) {
			com.randomsurprise.affix.Affix affix = com.randomsurprise.affix.AffixRegistry.getById(affixId);
			if (affix == null || affix.isBossOnly()) continue;
			globalCount++;
		}
	}

	if (globalCount > 0) {
		mc.player.displayClientMessage(Component.literal("§7--- 全局坏词条影响 ---"), false);
		int count = 0;
		for (var entry : allBad) {
			for (String affixId : entry.affixIds()) {
				com.randomsurprise.affix.Affix affix = com.randomsurprise.affix.AffixRegistry.getById(affixId);
				if (affix == null || affix.isBossOnly()) continue;
				Component name = Component.translatable(affix.getNameKey());
				mc.player.displayClientMessage(
					Component.literal("  §c" + (count+1) + ". ").append(name)
						.append(Component.literal(" §8[" + affix.getRarity().name() + "]")), false);
				count++;
			}
		}
		mc.player.displayClientMessage(Component.literal("§8全局坏词条共 " + count + " 个，影响所有敌对生物"), false);
		hasAffix = true;
	}

	if (!hasAffix) {
		mc.player.displayClientMessage(Component.literal("§7该生物没有词条"), false);
	}
}

}