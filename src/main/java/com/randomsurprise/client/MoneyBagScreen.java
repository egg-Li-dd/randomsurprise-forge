package com.randomsurprise.client;

import com.randomsurprise.affix.MoneyBagItem;
import com.randomsurprise.affix.ModItems;
import com.randomsurprise.network.ModNetworking;
import com.randomsurprise.network.MoneyBagPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * 钱袋子UI界面
 * 显示存储的金币数量，支持存入/取出操作
 */
public class MoneyBagScreen extends Screen {
	private ItemStack bagStack;
	private final InteractionHand hand;
	private EditBox amountInput;
	private int panelX, panelY, panelW, panelH;

	// 按钮布局常量
	private static final int BTN_W = 80;
	private static final int BTN_H = 18;
	private static final int BTN_GAP = 4;

	public MoneyBagScreen(ItemStack bagStack, InteractionHand hand) {
		super(Component.translatable("moneybag.randomsurprise.title"));
		this.bagStack = bagStack;
		this.hand = hand;
	}

	public static void open(ItemStack stack, InteractionHand hand) {
		com.randomsurprise.client.ClientRandomSurpriseMod.openMoneyBagScreen(stack, hand);
	}

	@Override
	protected void init() {
		super.init();
		this.panelW = 200;
		this.panelH = 140;
		this.panelX = (this.width - panelW) / 2;
		this.panelY = (this.height - panelH) / 2;

		// 数量输入框
		this.amountInput = new EditBox(this.font, panelX + 60, panelY + 55, 80, 16,
				Component.translatable("moneybag.randomsurprise.amount"));
		this.amountInput.setValue("100");
		this.amountInput.setFilter(s -> s.isEmpty() || s.matches("\\d{1,8}"));
		this.addRenderableWidget(this.amountInput);

		int handId = hand == InteractionHand.OFF_HAND ? 1 : 0;
		int btnY1 = panelY + 80;
		int btnY2 = panelY + 102;

		// 第一行：存入 / 取出
		this.addRenderableWidget(Button.builder(Component.translatable("moneybag.randomsurprise.deposit"),
				b -> sendAction(MoneyBagPayload.ACTION_DEPOSIT, handId))
				.bounds(panelX + 20, btnY1, BTN_W, BTN_H).build());
		this.addRenderableWidget(Button.builder(Component.translatable("moneybag.randomsurprise.withdraw"),
				b -> sendAction(MoneyBagPayload.ACTION_WITHDRAW, handId))
				.bounds(panelX + 20 + BTN_W + BTN_GAP, btnY1, BTN_W, BTN_H).build());

		// 第二行：存入全部 / 取出全部
		this.addRenderableWidget(Button.builder(Component.translatable("moneybag.randomsurprise.deposit_all"),
				b -> sendAction(MoneyBagPayload.ACTION_DEPOSIT_ALL, handId))
				.bounds(panelX + 20, btnY2, BTN_W, BTN_H).build());
		this.addRenderableWidget(Button.builder(Component.translatable("moneybag.randomsurprise.withdraw_all"),
				b -> sendAction(MoneyBagPayload.ACTION_WITHDRAW_ALL, handId))
				.bounds(panelX + 20 + BTN_W + BTN_GAP, btnY2, BTN_W, BTN_H).build());
	}

	private void sendAction(int action, int handId) {
		int amount = 0;
		if (action == MoneyBagPayload.ACTION_DEPOSIT || action == MoneyBagPayload.ACTION_WITHDRAW) {
			try {
				amount = Integer.parseInt(amountInput.getValue());
			} catch (NumberFormatException e) {
				amount = 0;
			}
		}
		ModNetworking.sendToServer(new MoneyBagPayload(action, amount, handId));
		// 关闭界面让服务端处理
		this.onClose();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// 半透明背景
		graphics.fill(0, 0, this.width, this.height, 0xAA000000);
		super.render(graphics, mouseX, mouseY, partialTick);

		// 主面板
		graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF51A1A2E);
		// 金色边框
		int border = 0xFFFFD700;
		graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, border);
		graphics.fill(panelX, panelY + panelH - 2, panelX + panelW, panelY + panelH, border);
		graphics.fill(panelX, panelY, panelX + 2, panelY + panelH, border);
		graphics.fill(panelX + panelW - 2, panelY, panelX + panelW, panelY + panelH, border);

		// 标题
		graphics.drawCenteredString(this.font,
				Component.translatable("moneybag.randomsurprise.title"),
				this.width / 2, panelY + 12, 0xFFFFD700);

		// 钱袋子存储的金币数量
		int bagCoins = MoneyBagItem.getCoinValue(bagStack);
		graphics.drawCenteredString(this.font,
				Component.translatable("moneybag.randomsurprise.bag_coins", bagCoins),
				this.width / 2, panelY + 28, 0xFFAAFFAA);

		// 背包中的金币数量
		int invCoins = countInventoryCoins();
		graphics.drawCenteredString(this.font,
				Component.translatable("moneybag.randomsurprise.inv_coins", invCoins),
				this.width / 2, panelY + 42, 0xFFCCCCCC);

		// 数量标签
		graphics.drawString(this.font,
				Component.translatable("moneybag.randomsurprise.amount"),
				panelX + 20, panelY + 59, 0xFFAAAAAA);
	}

	private int countInventoryCoins() {
		if (this.minecraft == null || this.minecraft.player == null) return 0;
		int count = 0;
		var inv = this.minecraft.player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(ModItems.UNIVERSAL_COIN.get())) count += s.getCount();
		}
		return count;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
