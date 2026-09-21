package com.randomsurprise.client;

import com.randomsurprise.RandomSurpriseMod;
import com.randomsurprise.affix.ModItems;
import com.randomsurprise.menu.ModBagMenu;
import com.randomsurprise.network.ModBagActionPayload;
import com.randomsurprise.network.ModNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 模组袋客户端界面：9 格容器槽位 + 4 个快捷使用按钮。
 */
public class ModBagScreen extends AbstractContainerScreen<ModBagMenu> {

	private static final int BTN_W = 75;
	private static final int BTN_H = 18;

	private Button purifyBtn;
	private Button clearBtn;
	private Button lotteryBtn;
	private Button closeBtn;

	public ModBagScreen(ModBagMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = 176;
		this.imageHeight = 180;
	}

	@Override
	protected void init() {
		super.init();
		this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
		this.titleLabelY = 6;

		int btnY1 = this.topPos + 128;
		int btnY2 = this.topPos + 150;
		int x1 = this.leftPos + 11;
		int x2 = this.leftPos + 90;

		purifyBtn = Button.builder(Component.translatable("modbag.randomsurprise.use_purify"),
				b -> sendAction(ModBagActionPayload.ACTION_PURIFY)).bounds(x1, btnY1, BTN_W, BTN_H).build();
		clearBtn = Button.builder(Component.translatable("modbag.randomsurprise.use_clear"),
				b -> sendAction(ModBagActionPayload.ACTION_CLEAR)).bounds(x2, btnY1, BTN_W, BTN_H).build();
		lotteryBtn = Button.builder(Component.translatable("modbag.randomsurprise.use_lottery"),
				b -> sendAction(ModBagActionPayload.ACTION_LOTTERY)).bounds(x1, btnY2, BTN_W, BTN_H).build();
		closeBtn = Button.builder(Component.translatable("modbag.randomsurprise.close"),
				b -> this.onClose()).bounds(x2, btnY2, BTN_W, BTN_H).build();

		addRenderableWidget(purifyBtn);
		addRenderableWidget(clearBtn);
		addRenderableWidget(lotteryBtn);
		addRenderableWidget(closeBtn);

		updateButtonStates();
	}

	/** 发送快捷使用请求并关闭界面 */
	private void sendAction(int actionType) {
		ModNetworking.sendToServer(new ModBagActionPayload(actionType));
		this.onClose();
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		updateButtonStates();
	}

	/** 根据袋子内容物动态启用/禁用按钮 */
	private void updateButtonStates() {
		purifyBtn.active = hasItem(ModItems.PURIFY_POTION.get());
		clearBtn.active = hasItem(ModItems.MOB_CLEAR_POTION.get());
		lotteryBtn.active = hasItem(ModItems.LOTTERY_TICKET.get());
	}

	private boolean hasItem(Item item) {
		var container = this.menu.getBagContainer();
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).is(item)) return true;
		}
		return false;
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		int x = this.leftPos;
		int y = this.topPos;
		int w = this.imageWidth;
		int h = this.imageHeight;

		// 主面板（深色半透明）
		graphics.fill(x, y, x + w, y + h, 0xF51A1A2E);
		// 紫色史诗边框
		int border = 0xFFA020F0;
		graphics.fill(x, y, x + w, y + 2, border);
		graphics.fill(x, y + h - 2, x + w, y + h, border);
		graphics.fill(x, y, x + 2, y + h, border);
		graphics.fill(x + w - 2, y, x + w, y + h, border);

		// 槽位凹陷：袋子 9 格（y=20）
		drawSlotRow(graphics, x, y + 20, 9);
		// 玩家背包 3 行（y=44）
		for (int row = 0; row < 3; row++) {
			drawSlotRow(graphics, x, y + 44 + row * 18, 9);
		}
		// 快捷栏（y=102）
		drawSlotRow(graphics, x, y + 102, 9);
	}

	private void drawSlotRow(GuiGraphics g, int baseX, int baseY, int count) {
		for (int i = 0; i < count; i++) {
			int sx = baseX + 7 + i * 18;
			int sy = baseY - 1;
			g.fill(sx, sy, sx + 18, sy + 18, 0xFF0D0D1A);
			g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF2A2A3E);
		}
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFA020F0);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/**
	 * 客户端注册：将 ModBagMenu 与 ModBagScreen 绑定。
	 * 仅在客户端加载（Dist.CLIENT），不会在专用服务端触发 ModBagScreen 的类加载。
	 */
	@Mod.EventBusSubscriber(modid = RandomSurpriseMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
	public static class Registration {
		@SubscribeEvent
		public static void onClientSetup(FMLClientSetupEvent event) {
			event.enqueueWork(() -> MenuScreens.register(ModBagMenu.TYPE.get(), ModBagScreen::new));
		}
	}
}
