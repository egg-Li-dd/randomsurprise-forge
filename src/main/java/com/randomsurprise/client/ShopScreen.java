package com.randomsurprise.client;

import com.randomsurprise.affix.ModItems;
import com.randomsurprise.affix.MoneyBagItem;
import com.randomsurprise.network.ShopPurchasePayload;
import com.randomsurprise.shop.ShopEntry;
import com.randomsurprise.shop.ShopManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.List;
import java.util.Optional;

/**
 * 商店界面（多标签页版）
 * 顶部标签栏切换分类，内容区显示该分类的商品列表
 * 每行：物品图标 + 名称 + 数量 + 价格 + 购买按钮
 * 右上角：玩家资源面板（抽奖券/钻石/绿宝石/下界合金锭/经验等级）
 * 中央模态弹窗：购买成功/失败提示
 */
public class ShopScreen extends Screen {

	private static final int MAX_PANEL_WIDTH = 420;
	/** 实际面板宽度（init 中根据屏幕尺寸自适应） */
	private int panelW = 420;
	private static final int ROW_HEIGHT = 32;
	private static final int TAB_HEIGHT = 22;
	private static final int TOP_PADDING = 36 + TAB_HEIGHT + 4; // 标题 + 标签栏 + 间距
	private static final int BOTTOM_PADDING = 28;
	private static final int SCROLL_STEP = ROW_HEIGHT;
	
	private static final int BTN_WIDTH = 60;
	private static final int PRICE_WIDTH = 120;

	/** 当前选中的标签页索引 */
	private int currentTabIndex = 0;
	private int scrollOffset = 0;
	private int maxScroll = 0;
	private int panelX, panelY, panelHeight;
	private int listTopY, listBottomY;
	private int visibleRows;

	/** 中央模态弹窗状态 */
	private boolean popupVisible = false;
	private boolean popupSuccess = false;
	private Component popupMessage = Component.empty();
	/** 弹窗"确定"按钮区域 */
	private int popupBtnX, popupBtnY, popupBtnW = 80, popupBtnH = 18;
	/** 弹窗整体区域 */
	private int popupX, popupY, popupW = 260, popupH = 90;

	/** 数量选择弹窗状态（点击购买按钮后弹出） */
	private boolean qtyVisible = false;
	private ShopEntry qtyEntry = null;
	private int qtyAmount = 1;
	/** 数量选择弹窗整体区域 */
	private int qtyX, qtyY, qtyW = 280, qtyH = 150;
	/** 数量选择弹窗内的按钮区域 */
	private int qtyMinusX, qtyMinusY, qtyMinusW = 22, qtyMinusH = 22;
	private int qtyPlusX, qtyPlusY, qtyPlusW = 22, qtyPlusH = 22;
	private int qtyConfirmX, qtyConfirmY, qtyConfirmW = 80, qtyConfirmH = 20;
	private int qtyCancelX, qtyCancelY, qtyCancelW = 60, qtyCancelH = 20;
	/** 资源不足提示（在数量选择弹窗之上叠加显示，不关闭数量弹窗） */
	private boolean qtyInsufficientVisible = false;
	private Component qtyInsufficientMessage = Component.empty();
	private int qtyInsX, qtyInsY, qtyInsW = 280, qtyInsH = 80;
	private int qtyInsBtnX, qtyInsBtnY, qtyInsBtnW = 80, qtyInsBtnH = 18;

	public ShopScreen() {
		super(Component.translatable("shop.randomsurprise.title"));
	}

	@Override
	protected void init() {
		super.init();
		this.scrollOffset = 0;
		// 面板宽度自适应：窄屏（手机）时收缩到屏幕宽度-16，宽屏时不超过 MAX_PANEL_WIDTH
		this.panelW = Math.min(MAX_PANEL_WIDTH, this.width - 16);
		this.panelX = (this.width - panelW) / 2;
		this.panelY = 16;
		this.panelHeight = this.height - 32;
		this.listTopY = panelY + TOP_PADDING;
		this.listBottomY = panelY + panelHeight - BOTTOM_PADDING;
		this.visibleRows = (listBottomY - listTopY) / ROW_HEIGHT;
		// 购买结果弹窗位置（屏幕中央），宽度自适应
		this.popupW = Math.min(260, this.width - 20);
		this.popupH = Math.min(90, this.height - 20);
		this.popupX = (this.width - popupW) / 2;
		this.popupY = (this.height - popupH) / 2;
		this.popupBtnX = popupX + (popupW - popupBtnW) / 2;
		this.popupBtnY = popupY + popupH - popupBtnH - 8;
		// 数量选择弹窗位置（屏幕中央），宽高自适应
		this.qtyW = Math.min(280, this.width - 20);
		this.qtyH = Math.min(170, this.height - 20);
		this.qtyX = (this.width - qtyW) / 2;
		this.qtyY = (this.height - qtyH) / 2;
		// 减/加按钮位于弹窗中部两侧
		int sidePad = Math.min(60, qtyW / 5);
		this.qtyMinusX = qtyX + sidePad;
		this.qtyMinusY = qtyY + 58;
		this.qtyPlusX = qtyX + qtyW - sidePad - qtyPlusW;
		this.qtyPlusY = qtyY + 58;
		// 确认/取消按钮位于弹窗底部
		this.qtyConfirmX = qtyX + (qtyW - qtyConfirmW - qtyCancelW - 10) / 2;
		this.qtyConfirmY = qtyY + qtyH - qtyConfirmH - 26;
		this.qtyCancelX = qtyConfirmX + qtyConfirmW + 10;
		this.qtyCancelY = qtyConfirmY;
		// 资源不足提示弹窗位置（屏幕中央，叠加在数量选择弹窗之上）
		this.qtyInsW = Math.min(280, this.width - 20);
		this.qtyInsX = (this.width - qtyInsW) / 2;
		this.qtyInsY = (this.height - qtyInsH) / 2;
		this.qtyInsBtnX = qtyInsX + (qtyInsW - qtyInsBtnW) / 2;
		this.qtyInsBtnY = qtyInsY + qtyInsH - qtyInsBtnH - 8;
		recalculateScroll();
	}

	/**
	 * 接收服务端返回的购买结果，显示模态弹窗
	 * 由 ClientRandomSurpriseMod 在收到 ShopPurchaseResultPayload 时调用
	 */
	public void showPurchaseResult(boolean success, String messageKey, List<String> args) {
		this.popupSuccess = success;
		this.popupMessage = buildPopupMessage(messageKey, args);
		this.popupVisible = true;
	}

	/**
	 * 根据消息key和参数构造弹窗消息组件
	 * 对 purchase_success 的第二个参数（物品描述ID）做 Component.translatable 包装
	 */
	private Component buildPopupMessage(String messageKey, List<String> args) {
		if ("shop.randomsurprise.purchase_success".equals(messageKey) && args.size() >= 4) {
			return Component.translatable(messageKey,
					args.get(0),
					Component.translatable(args.get(1)),
					args.get(2),
					args.get(3));
		} else if ("shop.randomsurprise.insufficient_funds".equals(messageKey) && args.size() >= 2) {
			return Component.translatable(messageKey, args.get(0), args.get(1));
		} else if ("shop.randomsurprise.exchange_success".equals(messageKey) && args.size() >= 3) {
			return Component.translatable(messageKey,
					args.get(0),
					Component.translatable(args.get(1)),
					args.get(2));
		} else if ("shop.randomsurprise.insufficient_items".equals(messageKey) && args.size() >= 2) {
			return Component.translatable(messageKey, args.get(0), Component.translatable(args.get(1)));
		}
		return Component.translatable(messageKey);
	}

	/** 关闭弹窗 */
	private void closePopup() {
		this.popupVisible = false;
	}

	/** 打开数量选择弹窗（点击购买按钮时调用） */
	private void openQtySelector(ShopEntry entry) {
		this.qtyEntry = entry;
		this.qtyAmount = 1;
		this.qtyVisible = true;
		this.qtyInsufficientVisible = false;
		this.qtyInsufficientMessage = Component.empty();
	}

	/** 关闭数量选择弹窗 */
	private void closeQtySelector() {
		this.qtyVisible = false;
		this.qtyEntry = null;
		this.qtyInsufficientVisible = false;
		this.qtyInsufficientMessage = Component.empty();
	}

	/** 关闭资源不足提示（保留数量选择弹窗打开） */
	private void closeQtyInsufficient() {
		this.qtyInsufficientVisible = false;
	}

	/**
	 * 获取数量选择弹窗的数量上限
	 * - 兑换场景：min(64, 玩家持有物品数)
	 * - 购买场景：64
	 */
	private int getQtyMaxAmount() {
		if (qtyEntry == null) return 64;
		boolean isExchange = com.randomsurprise.shop.ExchangeRegistry.CATEGORY.equals(qtyEntry.category());
		if (isExchange) {
			Item resolvedItem = qtyEntry.resolveItem();
			if (resolvedItem == null) return 1;
			int owned = countItem(resolvedItem);
			return Math.max(1, Math.min(64, owned));
		}
		return 64;
	}

	/**
	 * 确认数量选择弹窗的操作（购买或兑换）
	 * 根据 qtyEntry 的分类决定发送 ShopPurchasePayload 还是 ExchangePayload
	 */
	private void confirmQtyAction() {
		if (!checkQtyResources()) return;
		ShopEntry entry = qtyEntry;
		int amount = qtyAmount;
		closeQtySelector();
		if (entry == null) return;
		boolean isExchange = com.randomsurprise.shop.ExchangeRegistry.CATEGORY.equals(entry.category());
		if (isExchange) {
			com.randomsurprise.network.ModNetworking.sendToServer(
					new com.randomsurprise.network.ExchangePayload(entry.id(), amount));
		} else {
			com.randomsurprise.network.ModNetworking.sendToServer(
					new ShopPurchasePayload(entry.id(), amount));
		}
	}

	/**
	 * 检查当前数量选择弹窗的资源是否足够，不足时显示提示
	 * - 购买场景：检查货币数量是否足够
	 * - 兑换场景：检查背包物品数量是否足够
	 * @return true 表示资源充足，可以确认
	 */
	private boolean checkQtyResources() {
		if (qtyEntry == null) return false;
		var player = Minecraft.getInstance().player;
		if (player == null) return false;

		boolean isExchange = com.randomsurprise.shop.ExchangeRegistry.CATEGORY.equals(qtyEntry.category());
		if (isExchange) {
			// 兑换场景：检查背包物品数量
			Item resolvedItem = qtyEntry.resolveItem();
			if (resolvedItem == null) return false;
			int owned = countItem(resolvedItem);
			if (owned >= qtyAmount) return true;
			int deficit = qtyAmount - owned;
			this.qtyInsufficientMessage = Component.translatable(
					"shop.randomsurprise.qty_insufficient_items",
					resolvedItem.getDescriptionId(), String.valueOf(deficit));
			this.qtyInsufficientVisible = true;
			return false;
		}

		// 购买场景：检查货币数量
		int totalCost = qtyEntry.cost() * qtyAmount;
		int owned = countCurrency(qtyEntry.currency());
		if (owned >= totalCost) return true;

		// 资源不足，显示提示（缺少种类 + 差额）
		int deficit = totalCost - owned;
		String currencyName = qtyEntry.currency().getDisplayName();
		this.qtyInsufficientMessage = Component.translatable(
				"shop.randomsurprise.qty_insufficient",
				currencyName, String.valueOf(deficit));
		this.qtyInsufficientVisible = true;
		return false;
	}

	/**
	 * 统计玩家持有某种货币的数量（客户端，遍历背包 + 经验等级）
	 */
	private int countCurrency(ShopEntry.CurrencyType currency) {
		var player = Minecraft.getInstance().player;
		if (player == null) return 0;
		return switch (currency) {
			case UNIVERSAL_COIN -> {
				int count = countItem(ModItems.UNIVERSAL_COIN.get());
				// 额外统计钱袋子中存储的金币
				var inv = player.getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					ItemStack stack = inv.getItem(i);
					if (stack.getItem() instanceof MoneyBagItem) {
						count += MoneyBagItem.getCoinValue(stack);
					}
				}
				yield count;
			}
			case LOTTERY_TICKET -> countItem(ModItems.LOTTERY_TICKET.get());
			case EXPERIENCE_LEVEL -> player.experienceLevel;
			case DIAMOND -> countItem(Items.DIAMOND);
			case IRON_INGOT -> countItem(Items.IRON_INGOT);
			case GOLD_INGOT -> countItem(Items.GOLD_INGOT);
			case EMERALD -> countItem(Items.EMERALD);
			case NETHERITE_INGOT -> countItem(Items.NETHERITE_INGOT);
			case COAL -> countItem(Items.COAL);
			case REDSTONE -> countItem(Items.REDSTONE);
			case LAPIS_LAZULI -> countItem(Items.LAPIS_LAZULI);
			case QUARTZ -> countItem(Items.QUARTZ);
		};
	}

	/**
	 * 统计玩家背包中指定物品的总数量
	 */
	private int countItem(Item item) {
		var player = Minecraft.getInstance().player;
		if (player == null) return 0;
		var inv = player.getInventory();
		int count = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(item)) count += stack.getCount();
		}
		return count;
	}

	/** 切换标签页或内容变化时重新计算滚动范围 */
	private void recalculateScroll() {
		List<ShopEntry> entries = getCurrentEntries();
		int totalHeight = entries.size() * ROW_HEIGHT;
		int listHeight = listBottomY - listTopY;
		this.maxScroll = Math.max(0, totalHeight - listHeight);
		this.scrollOffset = Math.min(scrollOffset, maxScroll);
	}

	/** 获取当前标签页的商品列表（可兑换物品排列在前，不可兑换物品排列在后） */
	private List<ShopEntry> getCurrentEntries() {
		String category = ShopManager.CATEGORIES[currentTabIndex];
		List<ShopEntry> raw = ShopManager.getByCategory(category);
		// 创建副本进行排序，避免修改原始列表；可兑换的排前面，不可兑换的排后面
		List<ShopEntry> sorted = new java.util.ArrayList<>(raw);
		sorted.sort((a, b) -> {
			boolean aOk = isAffordable(a);
			boolean bOk = isAffordable(b);
			if (aOk != bOk) return aOk ? -1 : 1;
			return 0; // 同类保持原有相对顺序
		});
		return sorted;
	}

	/** 判断商品当前是否可兑换/购买（资源是否充足） */
	private boolean isAffordable(ShopEntry entry) {
		boolean isExchange = com.randomsurprise.shop.ExchangeRegistry.CATEGORY.equals(entry.category());
		if (isExchange) {
			Item resolvedItem = entry.resolveItem();
			if (resolvedItem == null) return false;
			// 兑换场景：玩家需要持有 >= soldAmount 个稀有物品（cost 是获得的金币数，非所需物品数）
			return countItem(resolvedItem) >= entry.soldAmount();
		}
		return countCurrency(entry.currency()) >= entry.cost();
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// 半透明背景遮罩
		graphics.fill(0, 0, this.width, this.height, 0xAA000000);
		super.render(graphics, mouseX, mouseY, partialTick);

		// 数量选择弹窗或购买结果弹窗显示时，跳过商店主面板渲染，避免背景文字透出干扰阅读
		if (qtyVisible || popupVisible) {
			if (qtyVisible) {
				renderQtySelector(graphics, mouseX, mouseY);
			}
			if (popupVisible) {
				renderPopup(graphics, mouseX, mouseY);
			}
			return;
		}

		// 主面板背景：金属垂直渐变（顶部深蓝紫 0xFF1A1A2E → 底部纯黑 0xFF0A0A0A）
		int gradSteps = Math.max(1, panelHeight / 4);
		for (int i = 0; i < gradSteps; i++) {
			float ratio = (float) i / Math.max(1, gradSteps - 1);
			int r = (int) (0x1A + (0x0A - 0x1A) * ratio);
			int g = (int) (0x1A + (0x0A - 0x1A) * ratio);
			int b = (int) (0x2E + (0x0A - 0x2E) * ratio);
			int color = 0xFF000000 | (r << 16) | (g << 8) | b;
			int y0 = panelY + i * 4;
			int y1 = Math.min(panelY + (i + 1) * 4, panelY + panelHeight);
			graphics.fill(panelX, y0, panelX + panelW, y1, color);
		}
		// 金属质感3层边框：外层暗金 + 中层亮金 + 内层暗金
		int darkGold = 0xFF8B7500;
		int brightGold = 0xFFFFD700;
		graphics.fill(panelX, panelY, panelX + panelW, panelY + 1, darkGold);
		graphics.fill(panelX, panelY + panelHeight - 1, panelX + panelW, panelY + panelHeight, darkGold);
		graphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, darkGold);
		graphics.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelHeight, darkGold);
		graphics.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + 2, brightGold);
		graphics.fill(panelX + 1, panelY + panelHeight - 2, panelX + panelW - 1, panelY + panelHeight - 1, brightGold);
		graphics.fill(panelX + 1, panelY + 1, panelX + 2, panelY + panelHeight - 1, brightGold);
		graphics.fill(panelX + panelW - 2, panelY + 1, panelX + panelW - 1, panelY + panelHeight - 1, brightGold);
		graphics.fill(panelX + 2, panelY + 2, panelX + panelW - 2, panelY + 3, darkGold);
		graphics.fill(panelX + 2, panelY + panelHeight - 3, panelX + panelW - 2, panelY + panelHeight - 2, darkGold);
		graphics.fill(panelX + 2, panelY + 2, panelX + 3, panelY + panelHeight - 2, darkGold);
		graphics.fill(panelX + panelW - 3, panelY + 2, panelX + panelW - 2, panelY + panelHeight - 2, darkGold);
		// 面板四角金属角装饰（L形2x6px亮金线）
		int cornerLen = 6;
		int cornerThick = 2;
		graphics.fill(panelX + 3, panelY + 3, panelX + 3 + cornerLen, panelY + 3 + cornerThick, brightGold);
		graphics.fill(panelX + 3, panelY + 3, panelX + 3 + cornerThick, panelY + 3 + cornerLen, brightGold);
		graphics.fill(panelX + panelW - 3 - cornerLen, panelY + 3, panelX + panelW - 3, panelY + 3 + cornerThick, brightGold);
		graphics.fill(panelX + panelW - 3 - cornerThick, panelY + 3, panelX + panelW - 3, panelY + 3 + cornerLen, brightGold);
		graphics.fill(panelX + 3, panelY + panelHeight - 3 - cornerThick, panelX + 3 + cornerLen, panelY + panelHeight - 3, brightGold);
		graphics.fill(panelX + 3, panelY + panelHeight - 3 - cornerLen, panelX + 3 + cornerThick, panelY + panelHeight - 3, brightGold);
		graphics.fill(panelX + panelW - 3 - cornerLen, panelY + panelHeight - 3 - cornerThick, panelX + panelW - 3, panelY + panelHeight - 3, brightGold);
		graphics.fill(panelX + panelW - 3 - cornerThick, panelY + panelHeight - 3 - cornerLen, panelX + panelW - 3, panelY + panelHeight - 3, brightGold);

		// 标题文字（带发光效果：先绘制暗色阴影偏移1px，再绘制亮金文字）
		Component titleComp = Component.translatable("shop.randomsurprise.title");
		graphics.drawString(this.font, titleComp, panelX + 12, panelY + 11, 0xFF6B5500);
		graphics.drawString(this.font, titleComp, panelX + 12, panelY + 10, 0xFFFFD700);

		// 右上角资源面板
		renderResourcePanel(graphics);

		// 标签栏
		renderTabs(graphics, mouseX, mouseY);

		// 标签栏下分隔线
		int dividerY = panelY + 36 + TAB_HEIGHT;
		graphics.fill(panelX + 8, dividerY, panelX + panelW - 8, dividerY + 1, 0xFF6B6B8E);

		// 商品列表
		renderShopList(graphics, mouseX, mouseY);

		// 底部提示
		graphics.drawCenteredString(this.font,
				Component.translatable("shop.randomsurprise.close_hint"),
				this.width / 2, panelY + panelHeight - 16, 0xFF888888);

		// 滚动条
		renderScrollbar(graphics);

		// 中央模态弹窗（最上层）
		if (popupVisible) {
			renderPopup(graphics, mouseX, mouseY);
		}
		// 数量选择弹窗（在结果弹窗之上叠加，但通常不会同时显示）
		if (qtyVisible) {
			renderQtySelector(graphics, mouseX, mouseY);
			if (qtyInsufficientVisible) {
				renderQtyInsufficient(graphics, mouseX, mouseY);
			}
		}
	}

	/** 渲染数量选择弹窗 */
	private void renderQtySelector(GuiGraphics graphics, int mouseX, int mouseY) {
		boolean isExchange = qtyEntry != null
				&& com.randomsurprise.shop.ExchangeRegistry.CATEGORY.equals(qtyEntry.category());
		// 半透明背景遮罩（加深至 0xAA 提升对比度，避免商店内容透出干扰阅读）
		graphics.fill(0, 0, this.width, this.height, 0xAA000000);
		// 弹窗主体（玻璃拟态：提升至 0xF5 几乎不透明，解决透明度过低导致显示不清的问题）
		graphics.fill(qtyX, qtyY, qtyX + qtyW, qtyY + qtyH, 0xF51A1A2E);
		// 金属质感3层边框（外层暗金 + 中层亮金 + 内层暗金）
		int darkGold = 0xFF8B7500;
		int brightGold = 0xFFFFD700;
		graphics.fill(qtyX, qtyY, qtyX + qtyW, qtyY + 1, darkGold);
		graphics.fill(qtyX, qtyY + qtyH - 1, qtyX + qtyW, qtyY + qtyH, darkGold);
		graphics.fill(qtyX, qtyY, qtyX + 1, qtyY + qtyH, darkGold);
		graphics.fill(qtyX + qtyW - 1, qtyY, qtyX + qtyW, qtyY + qtyH, darkGold);
		graphics.fill(qtyX + 1, qtyY + 1, qtyX + qtyW - 1, qtyY + 2, brightGold);
		graphics.fill(qtyX + 1, qtyY + qtyH - 2, qtyX + qtyW - 1, qtyY + qtyH - 1, brightGold);
		graphics.fill(qtyX + 1, qtyY + 1, qtyX + 2, qtyY + qtyH - 1, brightGold);
		graphics.fill(qtyX + qtyW - 2, qtyY + 1, qtyX + qtyW - 1, qtyY + qtyH - 1, brightGold);
		graphics.fill(qtyX + 2, qtyY + 2, qtyX + qtyW - 2, qtyY + 3, darkGold);
		graphics.fill(qtyX + 2, qtyY + qtyH - 3, qtyX + qtyW - 2, qtyY + qtyH - 2, darkGold);
		graphics.fill(qtyX + 2, qtyY + 2, qtyX + 3, qtyY + qtyH - 2, darkGold);
		graphics.fill(qtyX + qtyW - 3, qtyY + 2, qtyX + qtyW - 2, qtyY + qtyH - 2, darkGold);

		// 标题（购买/兑换区分）
		String titleKey = isExchange ? "shop.randomsurprise.qty_title_exchange" : "shop.randomsurprise.qty_title_buy";
		// 标题带发光阴影
		graphics.drawCenteredString(this.font, Component.translatable(titleKey),
				this.width / 2, qtyY + 13, 0xFF6B5500);
		graphics.drawCenteredString(this.font, Component.translatable(titleKey),
				this.width / 2, qtyY + 12, 0xFFFFD700);

		// 商品名 + 单价（兑换场景显示"回收价"）
		if (qtyEntry != null) {
			Item resolvedItem = qtyEntry.resolveItem();
			String itemName = resolvedItem != null ? resolvedItem.getDescriptionId() : "shop.randomsurprise.invalid_entry";
			Component nameLine = isExchange
					? Component.translatable("shop.randomsurprise.qty_item_info_exchange",
							Component.translatable(itemName), String.valueOf(qtyEntry.cost()))
					: Component.translatable("shop.randomsurprise.qty_item_info",
							Component.translatable(itemName),
							String.valueOf(qtyEntry.cost()),
							qtyEntry.currency().getDisplayName());
			graphics.drawCenteredString(this.font, nameLine, this.width / 2, qtyY + 28, 0xFFCCCCCC);
		}

		// "−" 减少按钮（红色金属质感）
		boolean minusHover = mouseX >= qtyMinusX && mouseX <= qtyMinusX + qtyMinusW
				&& mouseY >= qtyMinusY && mouseY <= qtyMinusY + qtyMinusH;
		boolean minusDisabled = (qtyAmount <= 1);
		if (minusHover && !minusDisabled) {
			graphics.fill(qtyMinusX - 1, qtyMinusY - 1, qtyMinusX + qtyMinusW + 1, qtyMinusY + qtyMinusH + 1, 0x40FF5555);
		}
		drawMetalButtonBg(graphics, qtyMinusX, qtyMinusY, qtyMinusW, qtyMinusH,
				minusDisabled, minusHover && !minusDisabled,
				0xFF8B0000, 0xFF5A0000, 0xFFFF5555, 0xFF8B0000, 0xFFFFAAAA, 0xFF3A0000);
		graphics.drawCenteredString(this.font, Component.literal("§f−"),
				qtyMinusX + qtyMinusW / 2, qtyMinusY + 7, 0xFFFFFFFF);

		// 当前数量显示（居中，大号金色文字 + 发光阴影）
		String amountText = String.valueOf(qtyAmount);
		graphics.drawCenteredString(this.font, Component.literal("§e§l" + amountText),
				this.width / 2, qtyMinusY + 8, 0xFF8B7500);
		graphics.drawCenteredString(this.font, Component.literal("§e§l" + amountText),
				this.width / 2, qtyMinusY + 7, 0xFFFFD700);

		// "+" 增加按钮（绿色金属质感）
		boolean plusHover = mouseX >= qtyPlusX && mouseX <= qtyPlusX + qtyPlusW
				&& mouseY >= qtyPlusY && mouseY <= qtyPlusY + qtyPlusH;
		int qtyMax = getQtyMaxAmount();
		boolean plusDisabled = (qtyAmount >= qtyMax);
		if (plusHover && !plusDisabled) {
			graphics.fill(qtyPlusX - 1, qtyPlusY - 1, qtyPlusX + qtyPlusW + 1, qtyPlusY + qtyPlusH + 1, 0x4066BB6A);
		}
		drawMetalButtonBg(graphics, qtyPlusX, qtyPlusY, qtyPlusW, qtyPlusH,
				plusDisabled, plusHover && !plusDisabled,
				0xFF2E7D32, 0xFF1B5E20, 0xFF4CAF50, 0xFF2E7D32, 0xFFA5D6A7, 0xFF0D3F10);
		graphics.drawCenteredString(this.font, Component.literal("§f+"),
				qtyPlusX + qtyPlusW / 2, qtyPlusY + 7, 0xFFFFFFFF);

		// 总价 + 持有量显示（兑换场景显示"可获得金币"和"持有物品数"）
		if (qtyEntry != null) {
			if (isExchange) {
				int totalCoins = qtyEntry.cost() * qtyAmount;
				Item resolvedItem = qtyEntry.resolveItem();
				int owned = resolvedItem != null ? countItem(resolvedItem) : 0;
				boolean enough = owned >= qtyAmount;
				String totalLine = "§7可获得: §a" + totalCoins + " §7金币";
				graphics.drawCenteredString(this.font, Component.literal(totalLine),
						this.width / 2, qtyY + 92, 0xFFFFFFFF);
				String ownLine = "§7持有物品: " + (enough ? "§a" : "§c") + owned + " §7个";
				graphics.drawCenteredString(this.font, Component.literal(ownLine),
						this.width / 2, qtyY + 104, 0xFF999999);
			} else {
				int totalCost = qtyEntry.cost() * qtyAmount;
				int owned = countCurrency(qtyEntry.currency());
				boolean affordable = owned >= totalCost;
				String totalLine = "§7总价: " + (affordable ? "§a" : "§c") + totalCost + " §7" + qtyEntry.currency().getDisplayName();
				graphics.drawCenteredString(this.font, Component.literal(totalLine),
						this.width / 2, qtyY + 92, 0xFFFFFFFF);
				String ownLine = "§7持有: §e" + owned + " §7" + qtyEntry.currency().getDisplayName();
				graphics.drawCenteredString(this.font, Component.literal(ownLine),
						this.width / 2, qtyY + 104, 0xFF999999);
			}
		}

		// "确认"按钮（绿色金属质感，同购买按钮）
		boolean confirmHover = mouseX >= qtyConfirmX && mouseX <= qtyConfirmX + qtyConfirmW
				&& mouseY >= qtyConfirmY && mouseY <= qtyConfirmY + qtyConfirmH;
		if (confirmHover) {
			graphics.fill(qtyConfirmX - 1, qtyConfirmY - 1, qtyConfirmX + qtyConfirmW + 1, qtyConfirmY + qtyConfirmH + 1, 0x4066BB6A);
		}
		drawMetalButtonBg(graphics, qtyConfirmX, qtyConfirmY, qtyConfirmW, qtyConfirmH,
				false, confirmHover,
				0xFF2E7D32, 0xFF1B5E20, 0xFF4CAF50, 0xFF2E7D32, 0xFF66BB6A, 0xFF0D3F10);
		String confirmText = isExchange ? "§f确认兑换" : "§f确认购买";
		graphics.drawCenteredString(this.font, Component.literal(confirmText),
				qtyConfirmX + qtyConfirmW / 2, qtyConfirmY + 7, 0xFF0D3F10);
		graphics.drawCenteredString(this.font, Component.literal(confirmText),
				qtyConfirmX + qtyConfirmW / 2, qtyConfirmY + 6, 0xFFFFFFFF);

		// "取消"按钮（红色金属质感）
		boolean cancelHover = mouseX >= qtyCancelX && mouseX <= qtyCancelX + qtyCancelW
				&& mouseY >= qtyCancelY && mouseY <= qtyCancelY + qtyCancelH;
		if (cancelHover) {
			graphics.fill(qtyCancelX - 1, qtyCancelY - 1, qtyCancelX + qtyCancelW + 1, qtyCancelY + qtyCancelH + 1, 0x40FF5555);
		}
		drawMetalButtonBg(graphics, qtyCancelX, qtyCancelY, qtyCancelW, qtyCancelH,
				false, cancelHover,
				0xFF8B0000, 0xFF5A0000, 0xFFFF5555, 0xFF8B0000, 0xFFFFAAAA, 0xFF3A0000);
		graphics.drawCenteredString(this.font, Component.literal("§f取消"),
				qtyCancelX + qtyCancelW / 2, qtyCancelY + 7, 0xFF3A0000);
		graphics.drawCenteredString(this.font, Component.literal("§f取消"),
				qtyCancelX + qtyCancelW / 2, qtyCancelY + 6, 0xFFFFFFFF);

		// 操作提示（精简版，适配手机端：键盘快捷键提示对手机无意义，仅保留滚轮和ESC）
		graphics.drawCenteredString(this.font, Component.literal("§7ESC 关闭 | 滚轮调整数量"),
				this.width / 2, qtyY + qtyH - 14, 0xFF888888);
	}

	/**
	 * 绘制金属质感按钮背景（垂直渐变 + 顶部高光 + 底部暗线）
	 * @param disabled 禁用态（灰色）
	 * @param hovered 悬停态
	 * @param normalTop 普通态顶部色
	 * @param normalBot 普通态底部色
	 * @param hoverTop 悬停态顶部色
	 * @param hoverBot 悬停态底部色
	 * @param highlight 顶部1px高光色
	 * @param darkLine 底部1px暗线色
	 */
	private void drawMetalButtonBg(GuiGraphics graphics, int x, int y, int w, int h,
									boolean disabled, boolean hovered,
									int normalTop, int normalBot,
									int hoverTop, int hoverBot,
									int highlight, int darkLine) {
		int topColor, botColor, hl, dl;
		if (disabled) {
			topColor = 0xFF555555;
			botColor = 0xFF333333;
			hl = 0xFF777777;
			dl = 0xFF222222;
		} else if (hovered) {
			topColor = hoverTop;
			botColor = hoverBot;
			hl = highlight;
			dl = darkLine;
		} else {
			topColor = normalTop;
			botColor = normalBot;
			hl = highlight;
			dl = darkLine;
		}
		for (int i = 0; i < h; i++) {
			float ratio = (float) i / Math.max(1, h - 1);
			int br = (int) (((topColor >> 16) & 0xFF) + (((botColor >> 16) & 0xFF) - ((topColor >> 16) & 0xFF)) * ratio);
			int bg = (int) (((topColor >> 8) & 0xFF) + (((botColor >> 8) & 0xFF) - ((topColor >> 8) & 0xFF)) * ratio);
			int bb = (int) ((topColor & 0xFF) + ((botColor & 0xFF) - (topColor & 0xFF)) * ratio);
			graphics.fill(x, y + i, x + w, y + i + 1, 0xFF000000 | (br << 16) | (bg << 8) | bb);
		}
		// 顶部1px高光
		graphics.fill(x, y, x + w, y + 1, hl);
		// 底部1px暗线
		graphics.fill(x, y + h - 1, x + w, y + h, dl);
	}

	/** 渲染资源不足提示弹窗（叠加在数量选择弹窗之上，不关闭数量弹窗） */
	private void renderQtyInsufficient(GuiGraphics graphics, int mouseX, int mouseY) {
		// 半透明背景遮罩（比数量弹窗更深）
		graphics.fill(0, 0, this.width, this.height, 0xAA000000);
		// 弹窗主体（红色边框警示）
		graphics.fill(qtyInsX, qtyInsY, qtyInsX + qtyInsW, qtyInsY + qtyInsH, 0xFF2A1A1A);
		int border = 0xFFFF5555;
		graphics.fill(qtyInsX, qtyInsY, qtyInsX + qtyInsW, qtyInsY + 1, border);
		graphics.fill(qtyInsX, qtyInsY + qtyInsH - 1, qtyInsX + qtyInsW, qtyInsY + qtyInsH, border);
		graphics.fill(qtyInsX, qtyInsY, qtyInsX + 1, qtyInsY + qtyInsH, border);
		graphics.fill(qtyInsX + qtyInsW - 1, qtyInsY, qtyInsX + qtyInsW, qtyInsY + qtyInsH, border);
		graphics.fill(qtyInsX, qtyInsY, qtyInsX + qtyInsW, qtyInsY + 4, border);

		// 标题（红色警示图标 + 文本）
		graphics.drawCenteredString(this.font, Component.literal("§c§l⚠ 资源不足"),
				this.width / 2, qtyInsY + 14, 0xFFFF5555);

		// 消息内容（自动换行）
		String messageText = qtyInsufficientMessage.getString();
		int maxLineWidth = qtyInsW - 16;
		List<String> lines = wrapText(messageText, maxLineWidth);
		int lineY = qtyInsY + 30;
		for (String line : lines) {
			int lineWidth = this.font.width(line);
			graphics.drawString(this.font, line,
					this.width / 2 - lineWidth / 2, lineY, 0xFFFFFFFF);
			lineY += 10;
		}

		// "确定"按钮（红色调）
		boolean btnHovered = mouseX >= qtyInsBtnX && mouseX <= qtyInsBtnX + qtyInsBtnW
				&& mouseY >= qtyInsBtnY && mouseY <= qtyInsBtnY + qtyInsBtnH;
		int btnColor = btnHovered ? 0xFFFF5555 : 0xFF8B0000;
		graphics.fill(qtyInsBtnX, qtyInsBtnY, qtyInsBtnX + qtyInsBtnW, qtyInsBtnY + qtyInsBtnH, btnColor);
		graphics.fill(qtyInsBtnX, qtyInsBtnY, qtyInsBtnX + qtyInsBtnW, qtyInsBtnY + 1, 0xFFFFAAAA);
		graphics.fill(qtyInsBtnX, qtyInsBtnY + qtyInsBtnH - 1, qtyInsBtnX + qtyInsBtnW, qtyInsBtnY + qtyInsBtnH, 0xFF5A0000);
		graphics.drawCenteredString(this.font, Component.literal("§f确定"),
				qtyInsBtnX + qtyInsBtnW / 2, qtyInsBtnY + 5, 0xFFFFFFFF);
	}

	/** 渲染右上角玩家资源面板（金币数量） */
	private void renderResourcePanel(GuiGraphics graphics) {
		var player = Minecraft.getInstance().player;
		if (player == null) return;

		int coinCount = countCurrency(ShopEntry.CurrencyType.UNIVERSAL_COIN);
		int ticketCount = countItem(ModItems.LOTTERY_TICKET.get());

		// 面板尺寸（金币 + 抽奖券两项，含标签行）
		int itemGap = 68;
		int panelWidth = itemGap * 2 - 8;
		int panelHeight = 28;
		int rx = panelX + panelW - panelWidth - 10;
		int ry = panelY + 6;

		// 半透明深色背景 0xA00A0A0A
		graphics.fill(rx - 4, ry - 2, rx + panelWidth + 4, ry + panelHeight + 2, 0xA00A0A0A);
		// 金属质感1px暗金边框 0xFF8B7500
		int darkGold = 0xFF8B7500;
		graphics.fill(rx - 4, ry - 2, rx + panelWidth + 4, ry - 1, darkGold);
		graphics.fill(rx - 4, ry + panelHeight + 1, rx + panelWidth + 4, ry + panelHeight + 2, darkGold);
		graphics.fill(rx - 4, ry - 2, rx - 3, ry + panelHeight + 2, darkGold);
		graphics.fill(rx + panelWidth + 3, ry - 2, rx + panelWidth + 4, ry + panelHeight + 2, darkGold);

		// 金币（图标 + 数字 + 标签，图标与数字间1px间距）
		ItemStack coinIcon = new ItemStack(ModItems.UNIVERSAL_COIN.get(), 1);
		graphics.renderItem(coinIcon, rx, ry + 3);
		graphics.renderItemDecorations(this.font, coinIcon, rx, ry + 3);
		String coinText = formatNumber(coinCount);
		graphics.drawString(this.font, coinText, rx + 19, ry + 5, 0xFFFFD700);
		// 标签文字"金币"（小号灰色）
		graphics.drawString(this.font, "金币", rx + 19, ry + 16, 0xFF999999);

		// 抽奖券
		int tx = rx + itemGap;
		ItemStack ticketIcon = new ItemStack(ModItems.LOTTERY_TICKET.get(), 1);
		graphics.renderItem(ticketIcon, tx, ry + 3);
		graphics.renderItemDecorations(this.font, ticketIcon, tx, ry + 3);
		String ticketText = formatNumber(ticketCount);
		graphics.drawString(this.font, ticketText, tx + 19, ry + 5, 0xFFFFAA00);
		// 标签文字"券"（小号灰色）
		graphics.drawString(this.font, "券", tx + 19, ry + 16, 0xFF999999);
	}
	
	/** 数字格式化：超过1000显示缩写（1.5k） */
	private String formatNumber(int num) {
		if (num >= 1000000) {
			return String.format("%.1fM", num / 1000000.0);
		} else if (num >= 1000) {
			return String.format("%.1fk", num / 1000.0);
		}
		return String.valueOf(num);
	}

	/** 资源面板单项数据 */
	private record ResourceItem(Item item, int count, String label, int color) {}

	/** 获取附魔书的显示名称（基于附魔属性名） */
	private String getEnchantmentDisplayName(ShopEntry entry) {
		String[] enchantments = entry.enchantments();
		if (enchantments == null || enchantments.length == 0) {
			return Component.translatable(Items.ENCHANTED_BOOK.getDescriptionId()).getString();
		}

		var level = Minecraft.getInstance().level;
		if (level == null) {
			return Component.translatable(Items.ENCHANTED_BOOK.getDescriptionId()).getString();
		}

		Registry<Enchantment> registry = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
		StringBuilder sb = new StringBuilder();
		int count = 0;
		for (String ench : enchantments) {
			String[] parts = ench.split("\\|");
			if (parts.length != 2) continue;
			Optional<Holder.Reference<Enchantment>> holder = registry.getHolder(
					ResourceKey.create(Registries.ENCHANTMENT, new ResourceLocation(parts[0])));
			if (holder.isEmpty()) continue;
			if (count > 0) sb.append("+");
			int enchLevel = Integer.parseInt(parts[1]);
			sb.append(Component.translatable(holder.get().value().getDescriptionId()).getString());
			sb.append(romanNumeral(enchLevel));
			count++;
			if (count >= 3) break; // 最多显示3个附魔名
		}
		if (sb.isEmpty()) {
			return Component.translatable(Items.ENCHANTED_BOOK.getDescriptionId()).getString();
		}
		if (enchantments.length > 3) {
			sb.append("+").append(enchantments.length - 3);
		}
		return sb.toString();
	}

	/** 罗马数字转换 */
	private String romanNumeral(int num) {
		return switch (num) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			default -> String.valueOf(num);
		};
	}

	/** 渲染中央模态弹窗 */
	private void renderPopup(GuiGraphics graphics, int mouseX, int mouseY) {
		// 弹窗背景遮罩（高不透明全屏，遮挡商店内容避免文字透出干扰阅读）
		graphics.fill(0, 0, this.width, this.height, 0xDD000000);

		// 弹窗主体（玻璃拟态：半透明深色 0xE01A1A2E）
		graphics.fill(popupX, popupY, popupX + popupW, popupY + popupH, 0xE01A1A2E);

		// 边框发光效果：外层2px半透明发光 + 内层1px实色
		int glowColor = popupSuccess ? 0x3066BB6A : 0x30FF5555;
		int borderColor = popupSuccess ? 0xFF66BB6A : 0xFFFF5555;
		// 外层发光2px
		graphics.fill(popupX - 2, popupY - 2, popupX + popupW + 2, popupY, glowColor);
		graphics.fill(popupX - 2, popupY + popupH, popupX + popupW + 2, popupY + popupH + 2, glowColor);
		graphics.fill(popupX - 2, popupY - 2, popupX, popupY + popupH + 2, glowColor);
		graphics.fill(popupX + popupW, popupY - 2, popupX + popupW + 2, popupY + popupH + 2, glowColor);
		// 内层1px实色边框
		graphics.fill(popupX, popupY, popupX + popupW, popupY + 1, borderColor);
		graphics.fill(popupX, popupY + popupH - 1, popupX + popupW, popupY + popupH, borderColor);
		graphics.fill(popupX, popupY, popupX + 1, popupY + popupH, borderColor);
		graphics.fill(popupX + popupW - 1, popupY, popupX + popupW, popupY + popupH, borderColor);

		// 标题栏（4px颜色条，渐变从深到浅）
		int darkShade = popupSuccess ? 0xFF1B5E20 : 0xFF5A0000;
		int lightShade = popupSuccess ? 0xFF66BB6A : 0xFFFF5555;
		graphics.fill(popupX, popupY, popupX + popupW, popupY + 2, darkShade);
		graphics.fill(popupX, popupY + 2, popupX + popupW, popupY + 4, lightShade);

		// 标题文字
		String title = popupSuccess ? "✓ 购买成功" : "✗ 购买失败";
		int titleColor = popupSuccess ? 0xFF66BB6A : 0xFFFF5555;
		graphics.drawCenteredString(this.font, Component.literal(title), this.width / 2, popupY + 12, titleColor);

		// 消息内容（自动换行）
		String messageText = popupMessage.getString();
		int maxLineWidth = popupW - 16;
		List<String> lines = wrapText(messageText, maxLineWidth);
		int lineY = popupY + 28;
		for (String line : lines) {
			int lineWidth = this.font.width(line);
			graphics.drawString(this.font, line, this.width / 2 - lineWidth / 2, lineY, 0xFFFFFFFF);
			lineY += 10;
		}

		// "确定"按钮（金属质感）
		boolean btnHovered = mouseX >= popupBtnX && mouseX <= popupBtnX + popupBtnW
				&& mouseY >= popupBtnY && mouseY <= popupBtnY + popupBtnH;
		// 悬停外发光
		if (btnHovered) {
			graphics.fill(popupBtnX - 1, popupBtnY - 1, popupBtnX + popupBtnW + 1, popupBtnY + popupBtnH + 1, 0x4066BB6A);
		}
		// 按钮主体垂直渐变
		int topColor = btnHovered ? 0xFF4CAF50 : 0xFF2E7D32;
		int botColor = btnHovered ? 0xFF2E7D32 : 0xFF1B5E20;
		for (int i = 0; i < popupBtnH; i++) {
			float ratio = (float) i / Math.max(1, popupBtnH - 1);
			int br = (int) (((topColor >> 16) & 0xFF) + (((botColor >> 16) & 0xFF) - ((topColor >> 16) & 0xFF)) * ratio);
			int bg = (int) (((topColor >> 8) & 0xFF) + (((botColor >> 8) & 0xFF) - ((topColor >> 8) & 0xFF)) * ratio);
			int bb = (int) ((topColor & 0xFF) + ((botColor & 0xFF) - (topColor & 0xFF)) * ratio);
			graphics.fill(popupBtnX, popupBtnY + i, popupBtnX + popupBtnW, popupBtnY + i + 1, 0xFF000000 | (br << 16) | (bg << 8) | bb);
		}
		// 顶部1px高光
		graphics.fill(popupBtnX, popupBtnY, popupBtnX + popupBtnW, popupBtnY + 1, btnHovered ? 0xFF81C784 : 0xFF66BB6A);
		// 底部1px暗线
		graphics.fill(popupBtnX, popupBtnY + popupBtnH - 1, popupBtnX + popupBtnW, popupBtnY + popupBtnH, 0xFF0D3F10);

		String okText = "确定";
		int okWidth = this.font.width(okText);
		// 文字白色带轻微阴影
		graphics.drawString(this.font, okText,
				popupBtnX + (popupBtnW - okWidth) / 2,
				popupBtnY + (popupBtnH - 8) / 2 + 1, 0xFF0D3F10);
		graphics.drawString(this.font, okText,
				popupBtnX + (popupBtnW - okWidth) / 2,
				popupBtnY + (popupBtnH - 8) / 2, 0xFFFFFFFF);
	}

	/** 简单的中文/英文混合换行（按字符宽度） */
	private List<String> wrapText(String text, int maxWidth) {
		List<String> lines = new java.util.ArrayList<>();
		if (text == null || text.isEmpty()) return lines;
		StringBuilder cur = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			StringBuilder test = new StringBuilder(cur).append(c);
			if (this.font.width(test.toString()) > maxWidth && cur.length() > 0) {
				lines.add(cur.toString());
				cur = new StringBuilder().append(c);
			} else {
				cur = test;
			}
		}
		if (cur.length() > 0) lines.add(cur.toString());
		return lines;
	}

	/** 渲染标签栏 */
	private void renderTabs(GuiGraphics graphics, int mouseX, int mouseY) {
		String[] categories = ShopManager.CATEGORIES;
		int tabWidth = (panelW - 16) / categories.length;
		int tabY = panelY + 36;

		for (int i = 0; i < categories.length; i++) {
			int tabX = panelX + 8 + i * tabWidth;
			boolean selected = (i == currentTabIndex);
			boolean hovered = mouseX >= tabX && mouseX <= tabX + tabWidth
					&& mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT;

			// 标签背景：选中无背景（与面板融合）；悬停稍亮半透明；未选中深色半透明
			if (!selected) {
				int bgColor = hovered ? 0x802A2A4E : 0x8015152E;
				graphics.fill(tabX, tabY, tabX + tabWidth - 1, tabY + TAB_HEIGHT, bgColor);
			}

			// 选中标签底部2px亮金发光线
			if (selected) {
				graphics.fill(tabX, tabY + TAB_HEIGHT - 2, tabX + tabWidth - 1, tabY + TAB_HEIGHT, 0xFFFFD700);
			}

			// 标签之间1px分隔线
			if (i > 0) {
				graphics.fill(tabX - 1, tabY, tabX, tabY + TAB_HEIGHT, 0xFF333344);
			}

			// 标签文字
			String name = categories[i];
			int textWidth = this.font.width(name);
			int textX = tabX + (tabWidth - textWidth) / 2;
			int textY = tabY + (TAB_HEIGHT - 8) / 2;
			int textColor = selected ? 0xFFFFFFFF : (hovered ? 0xFFCCCCCC : 0xFF999999);
			graphics.drawString(this.font, name, textX, textY, textColor);
		}
	}

	/** 渲染商品列表 */
	private void renderShopList(GuiGraphics graphics, int mouseX, int mouseY) {
		List<ShopEntry> entries = getCurrentEntries();

		int listLeft = panelX + 8;
		int listRight = panelX + panelW - 20;

		ShopEntry tooltipEntry = null;
		int tooltipX = 0, tooltipY = 0;

		graphics.enableScissor(listLeft, listTopY, listRight, listBottomY);

		for (int i = 0; i < entries.size(); i++) {
			int rowY = listTopY + i * ROW_HEIGHT - scrollOffset;
			if (rowY + ROW_HEIGHT < listTopY || rowY > listBottomY) continue;

			ShopEntry entry = entries.get(i);
			boolean hovered = mouseX >= listLeft && mouseX <= listRight
					&& mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

			renderRow(graphics, entry, listLeft, rowY, listRight - listLeft, hovered, mouseX, mouseY);

			int iconX = listLeft + 8;
			int iconY = rowY + (ROW_HEIGHT - 16) / 2;
			if (mouseX >= iconX && mouseX <= iconX + 16
					&& mouseY >= iconY && mouseY <= iconY + 16) {
				tooltipEntry = entry;
				tooltipX = iconX + 18;
				tooltipY = iconY;
			}
		}

		graphics.disableScissor();

		if (tooltipEntry != null && !popupVisible) {
			renderItemTooltip(graphics, tooltipEntry, tooltipX, tooltipY);
		}
	}

	/**
	 * 渲染物品tooltip（含附魔详情）
	 * 当鼠标悬停在物品图标上时显示：物品名称、附魔列表、价格信息
	 */
	private void renderItemTooltip(GuiGraphics graphics, ShopEntry entry, int x, int y) {
		var resolvedItem = entry.resolveItem();
		if (resolvedItem == null) return;

		List<Component> tooltipLines = new java.util.ArrayList<>();

		tooltipLines.add(Component.translatable(resolvedItem.getDescriptionId()));

		if (entry.hasEnchantments()) {
			tooltipLines.add(Component.empty());
			tooltipLines.add(Component.translatable("randomsurprise.shop.enchantments").withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x5555FF))));
			
			var registryAccess = Minecraft.getInstance().level.registryAccess();
			Registry<Enchantment> enchRegistry = registryAccess.registryOrThrow(Registries.ENCHANTMENT);
			
			for (String ench : entry.enchantments()) {
				String[] parts = ench.split("\\|");
				if (parts.length != 2) continue;
				int level;
				try {
					level = Integer.parseInt(parts[1]);
				} catch (NumberFormatException e) {
					continue;
				}
				String enchName;
				String enchDesc = "";
				try {
					Optional<Holder.Reference<Enchantment>> holderOpt =
							enchRegistry.getHolder(ResourceKey.create(Registries.ENCHANTMENT, new ResourceLocation(parts[0])));
					if (holderOpt.isPresent()) {
						Enchantment enchantment = holderOpt.get().value();
						enchName = enchantment.getFullname(level).getString();
						enchDesc = Component.translatable(enchantment.getDescriptionId()).getString();
					} else {
						enchName = parts[0];
					}
				} catch (Exception e) {
					enchName = parts[0];
				}
				tooltipLines.add(Component.literal("  " + enchName).withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA))));
				if (!enchDesc.isEmpty()) {
					tooltipLines.add(Component.literal("    " + enchDesc).withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x888888))));
				}
			}
		}

		if (entry.soldAmount() > 1) {
			tooltipLines.add(Component.empty());
			tooltipLines.add(Component.translatable("randomsurprise.shop.amount", entry.soldAmount()).withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x55FF55))));
		}

		tooltipLines.add(Component.empty());
		tooltipLines.add(Component.translatable("randomsurprise.shop.price", entry.cost(), entry.currency().getDisplayName()).withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFFFF55))));

		int maxLineWidth = 0;
		for (Component line : tooltipLines) {
			int w = this.font.width(line);
			if (w > maxLineWidth) maxLineWidth = w;
		}
		int tooltipWidth = maxLineWidth + 12;
		int tooltipHeight = tooltipLines.size() * 11 + 8;

		// 调整位置避免超出屏幕
		if (x + tooltipWidth > this.width - 4) {
			x = this.width - tooltipWidth - 4;
		}
		if (y + tooltipHeight > this.height - 4) {
			y = this.height - tooltipHeight - 4;
		}
		if (x < 4) x = 4;
		if (y < 4) y = 4;

		// 绘制背景
		graphics.fill(x - 2, y - 2, x + tooltipWidth + 2, y + tooltipHeight + 2, 0xE0000000);
		// 边框
		int borderColor = entry.hasEnchantments() ? 0xFFAA55FF : 0xFF6B6B8E;
		graphics.fill(x - 2, y - 2, x + tooltipWidth + 2, y - 1, borderColor);
		graphics.fill(x - 2, y + tooltipHeight + 1, x + tooltipWidth + 2, y + tooltipHeight + 2, borderColor);
		graphics.fill(x - 2, y - 2, x - 1, y + tooltipHeight + 2, borderColor);
		graphics.fill(x + tooltipWidth + 1, y - 2, x + tooltipWidth + 2, y + tooltipHeight + 2, borderColor);

		// 绘制文字
		int lineY = y + 5;
		for (Component line : tooltipLines) {
			graphics.drawString(this.font, line, x + 6, lineY, 0xFFFFFFFF);
			lineY += 11;
		}
	}

	/** 将数字转为罗马数字（1-10） */
	private String toRoman(int num) {
		return switch (num) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			case 6 -> "VI";
			case 7 -> "VII";
			case 8 -> "VIII";
			case 9 -> "IX";
			case 10 -> "X";
			default -> String.valueOf(num);
		};
	}

	/** 渲染单行商品 */
	private void renderRow(GuiGraphics graphics, ShopEntry entry,
						   int x, int y, int width, boolean hovered, int mouseX, int mouseY) {
		// 判断当前是否可兑换/购买（资源是否充足）
		boolean affordable = isAffordable(entry);

		// 行背景：悬停时水平渐变（左侧深 0xFF2A2A4E → 右侧浅 0xFF1A1A3E），非悬停纯色
		// 不可兑换行：背景使用更暗的灰色调（0xFF1A1A1A / 0xFF101010）
		if (hovered) {
			int segs = 8;
			for (int i = 0; i < segs; i++) {
				float ratio = (float) i / Math.max(1, segs - 1);
				int r, g, b;
				if (affordable) {
					r = (int) (0x2A + (0x1A - 0x2A) * ratio);
					g = (int) (0x2A + (0x1A - 0x2A) * ratio);
					b = (int) (0x4E + (0x3E - 0x4E) * ratio);
				} else {
					// 不可兑换：暗灰色渐变
					r = (int) (0x22 + (0x14 - 0x22) * ratio);
					g = (int) (0x22 + (0x14 - 0x22) * ratio);
					b = (int) (0x22 + (0x14 - 0x22) * ratio);
				}
				int color = 0xFF000000 | (r << 16) | (g << 8) | b;
				int x0 = x + (int) (width * ((float) i / segs));
				int x1 = x + (int) (width * ((float) (i + 1) / segs));
				graphics.fill(x0, y, x1, y + ROW_HEIGHT - 1, color);
			}
		} else {
			graphics.fill(x, y, x + width, y + ROW_HEIGHT - 1, affordable ? 0xFF15152E : 0xFF141414);
		}

		// 分类颜色条（左侧），不可兑换时显示为暗红色提醒
		int catColor;
		if (affordable) {
			catColor = getCategoryColor(entry.category());
			if (hovered) {
				int cr = ((catColor >> 16) & 0xFF);
				int cg = ((catColor >> 8) & 0xFF);
				int cb = (catColor & 0xFF);
				catColor = 0xFF000000 | (((cr + 0xFF) / 2) << 16) | (((cg + 0xFF) / 2) << 8) | ((cb + 0xFF) / 2);
			}
		} else {
			// 不可兑换：左侧色条改为暗红色（0xFF5A2020），悬停时稍微变亮
			catColor = hovered ? 0xFF7A3030 : 0xFF5A2020;
		}
		graphics.fill(x, y, x + 3, y + ROW_HEIGHT - 1, catColor);

		// 物品图标（含附魔效果）
		int iconX = x + 8;
		int iconY = y + (ROW_HEIGHT - 16) / 2;
		var resolvedItem = entry.resolveItem();
		if (resolvedItem == null) return;
		ItemStack displayStack = entry.createDisplayStack(
				net.minecraft.client.Minecraft.getInstance().level.registryAccess());
		graphics.renderItem(displayStack, iconX, iconY);
		graphics.renderItemDecorations(this.font, displayStack, iconX, iconY);

		// 物品名称（附魔书显示附魔属性名）
		int nameX = x + 32;
		int nameY = y + 3;
		int nameMaxWidth = width - PRICE_WIDTH - BTN_WIDTH - 48;
		String itemName;
		if (entry.hasEnchantments()) {
			// 附魔书：用附魔属性名作为显示名
			itemName = getEnchantmentDisplayName(entry);
		} else {
			itemName = Component.translatable(resolvedItem.getDescriptionId()).getString();
		}
		// 不可兑换时物品名称变灰
		int nameColor;
		if (!affordable) {
			nameColor = 0xFF888888;
		} else if (entry.hasEnchantments()) {
			nameColor = 0xFFFF55FF;
		} else {
			nameColor = 0xFFFFFFFF;
		}
		if (this.font.width(itemName) > nameMaxWidth) {
			while (this.font.width(itemName + "...") > nameMaxWidth && itemName.length() > 1) {
				itemName = itemName.substring(0, itemName.length() - 1);
			}
			itemName += "...";
		}
		graphics.drawString(this.font, itemName, nameX, nameY, nameColor);

		// 数量（如果 > 1）
		if (entry.soldAmount() > 1) {
			String amountText = (affordable ? "§a" : "§7") + "x" + entry.soldAmount();
			int amountX = nameX + this.font.width(itemName) + 4;
			if (amountX < x + width - PRICE_WIDTH - BTN_WIDTH - 8) {
				graphics.drawString(this.font, amountText, amountX, nameY, affordable ? 0xFFAAFFAA : 0xFF888888);
			}
		}

		// 货币提示行（不可兑换时显示红色"资源不足"标识）
		String currencyHint = "§7" + getCurrencyShortName(entry.currency());
		graphics.drawString(this.font, currencyHint, nameX, y + 15, affordable ? 0xFFAAAAAA : 0xFF777777);
		// 不可兑换时在货币提示右侧显示红色"✗ 资源不足"标识
		if (!affordable) {
			int hintWidth = this.font.width(currencyHint);
			String insufficientText = "§c✗ 资源不足";
			graphics.drawString(this.font, insufficientText, nameX + hintWidth + 6, y + 15, 0xFFFF5555);
		}

		// 价格区域半透明深色背景框（不可兑换时使用更暗的红色调）
		int priceX = x + width - PRICE_WIDTH - BTN_WIDTH;
		graphics.fill(priceX - 2, y + 4, priceX + PRICE_WIDTH + 2, y + ROW_HEIGHT - 5,
				affordable ? 0x40333344 : 0x40442222);

		// 价格（固定右对齐区域，不可兑换时价格变灰红）
		String costText = (affordable ? "§e" : "§7") + entry.cost() + " " + entry.currency().getDisplayName();
		if (this.font.width(costText) > PRICE_WIDTH - 4) {
			costText = (affordable ? "§e" : "§7") + entry.cost() + " " + getCurrencyShortSymbol(entry.currency());
		}
		int costWidth = this.font.width(costText);
		graphics.drawString(this.font, costText, priceX + (PRICE_WIDTH - costWidth) / 2, y + 9,
				affordable ? 0xFFFFD700 : 0xFFAA7777);

		// 购买按钮（金属质感，不可兑换时显示灰色禁用态）
		int btnX = x + width - BTN_WIDTH;
		int btnY = y + (ROW_HEIGHT - 18) / 2;
		int btnH = 18;
		boolean btnHovered = mouseX >= btnX && mouseX <= btnX + BTN_WIDTH
				&& mouseY >= btnY && mouseY <= btnY + btnH;
		// 悬停态外发光（不可兑换时不发光）
		if (btnHovered && affordable) {
			graphics.fill(btnX - 1, btnY - 1, btnX + BTN_WIDTH + 1, btnY + btnH + 1, 0x4066BB6A);
		}
		// 按钮主体垂直渐变：可兑换为绿色，不可兑换为灰色
		int topColor, botColor, hiColor, loColor, txtColor;
		if (affordable) {
			topColor = btnHovered ? 0xFF4CAF50 : 0xFF2E7D32;
			botColor = btnHovered ? 0xFF2E7D32 : 0xFF1B5E20;
			hiColor = btnHovered ? 0xFF81C784 : 0xFF66BB6A;
			loColor = 0xFF0D3F10;
			txtColor = 0xFFFFFFFF;
		} else {
			// 不可兑换：灰色禁用态
			topColor = 0xFF555555;
			botColor = 0xFF333333;
			hiColor = 0xFF666666;
			loColor = 0xFF222222;
			txtColor = 0xFFAAAAAA;
		}
		for (int i = 0; i < btnH; i++) {
			float ratio = (float) i / Math.max(1, btnH - 1);
			int br = (int) (((topColor >> 16) & 0xFF) + (((botColor >> 16) & 0xFF) - ((topColor >> 16) & 0xFF)) * ratio);
			int bg = (int) (((topColor >> 8) & 0xFF) + (((botColor >> 8) & 0xFF) - ((topColor >> 8) & 0xFF)) * ratio);
			int bb = (int) ((topColor & 0xFF) + ((botColor & 0xFF) - (topColor & 0xFF)) * ratio);
			graphics.fill(btnX, btnY + i, btnX + BTN_WIDTH, btnY + i + 1, 0xFF000000 | (br << 16) | (bg << 8) | bb);
		}
		// 顶部1px高光
		graphics.fill(btnX, btnY, btnX + BTN_WIDTH, btnY + 1, hiColor);
		// 底部1px暗线
		graphics.fill(btnX, btnY + btnH - 1, btnX + BTN_WIDTH, btnY + btnH, loColor);

		String buyText = com.randomsurprise.shop.ExchangeRegistry.CATEGORY.equals(entry.category()) ? "兑换" : "购买";
		int buyTextWidth = this.font.width(buyText);
		// 文字带轻微阴影
		graphics.drawString(this.font, buyText, btnX + (BTN_WIDTH - buyTextWidth) / 2, btnY + 6, loColor);
		graphics.drawString(this.font, buyText, btnX + (BTN_WIDTH - buyTextWidth) / 2, btnY + 5, txtColor);
	}
	
	/** 获取货币简称符号 */
	private String getCurrencyShortSymbol(ShopEntry.CurrencyType currency) {
		return switch (currency) {
			case UNIVERSAL_COIN -> "币";
			case EXPERIENCE_LEVEL -> "级";
			case DIAMOND -> "钻";
			case IRON_INGOT -> "铁";
			case GOLD_INGOT -> "金";
			case EMERALD -> "绿";
			case NETHERITE_INGOT -> "合";
			case COAL -> "煤";
			case REDSTONE -> "红";
			case LAPIS_LAZULI -> "青";
			case QUARTZ -> "石";
			case LOTTERY_TICKET -> "券";
		};
	}

	/** 渲染滚动条 */
	private void renderScrollbar(GuiGraphics graphics) {
		if (maxScroll <= 0) return;
		int barX = panelX + panelW - 14;
		int barY = listTopY;
		int barH = listBottomY - listTopY;
		graphics.fill(barX, barY, barX + 6, barY + barH, 0xFF333344);
		float ratio = (float) scrollOffset / maxScroll;
		int thumbH = Math.max(20, (int) ((float) barH * visibleRows / Math.max(1, getCurrentEntries().size())));
		int thumbY = barY + (int) ((barH - thumbH) * ratio);
		graphics.fill(barX, thumbY, barX + 6, thumbY + thumbH, 0xFF888899);
	}

	/** 获取分类颜色 */
	private int getCategoryColor(String category) {
		return switch (category) {
			case "抽奖券兑换" -> 0xFFFFAA00;
			case "模组物品" -> 0xFFFF55FF;
			case "稀有物品" -> 0xFFFFD700;
			case "武器装备" -> 0xFFFF5555;
			case "消耗品" -> 0xFF55FF55;
			case "资源兑换" -> 0xFF55AAFF;
			default -> 0xFFAAAAAA;
		};
	}

	/** 获取货币简称 */
	private String getCurrencyShortName(ShopEntry.CurrencyType currency) {
		return switch (currency) {
			case UNIVERSAL_COIN -> "花费金币";
			case EXPERIENCE_LEVEL -> "花费经验等级";
			case DIAMOND -> "花费钻石";
			case IRON_INGOT -> "花费铁锭";
			case GOLD_INGOT -> "花费金锭";
			case EMERALD -> "花费绿宝石";
			case NETHERITE_INGOT -> "花费下界合金锭";
			case COAL -> "花费煤炭";
			case REDSTONE -> "花费红石";
			case LAPIS_LAZULI -> "花费青金石";
			case QUARTZ -> "花费石英";
			case LOTTERY_TICKET -> "花费抽奖券";
		};
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		// 购买结果弹窗显示时屏蔽滚动
		if (popupVisible) return true;
		// 数量选择弹窗显示时：用滚轮调节数量（资源不足提示显示时仍允许调节，便于快速找到可购买数量）
		if (qtyVisible) {
			if (qtyEntry == null) return true;
			int max = getQtyMaxAmount();
			// 滚动步长：Shift=5（中速），Ctrl=10（快速），默认=1
			int step = 1;
			if (Screen.hasControlDown()) step = 10;
			else if (Screen.hasShiftDown()) step = 5;
			if (amount > 0) {
				// 向上滚：增加数量
				qtyAmount = Math.min(max, qtyAmount + step);
				qtyInsufficientVisible = false;
			} else if (amount < 0) {
				// 向下滚：减少数量
				qtyAmount = Math.max(1, qtyAmount - step);
				qtyInsufficientVisible = false;
			}
			return true;
		}
		if (amount > 0) {
			scrollOffset = Math.max(0, scrollOffset - SCROLL_STEP);
		} else if (amount < 0) {
			scrollOffset = Math.min(maxScroll, scrollOffset + SCROLL_STEP);
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// 购买结果弹窗显示时：ESC 优先关闭弹窗
		if (popupVisible && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
			closePopup();
			return true;
		}
		// 数量选择弹窗显示时：ESC 关闭弹窗（若资源不足提示可见则优先关闭提示）
		if (qtyVisible && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
			if (qtyInsufficientVisible) {
				closeQtyInsufficient();
			} else {
				closeQtySelector();
			}
			return true;
		}
		// 数量选择弹窗显示时：左右方向键调整数量
		if (qtyVisible && !qtyInsufficientVisible) {
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT && qtyAmount > 1) {
				qtyAmount--;
				return true;
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT && qtyAmount < getQtyMaxAmount()) {
				qtyAmount++;
				return true;
			}
			if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
				confirmQtyAction();
				return true;
			}
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

		// 购买结果弹窗显示时：只响应"确定"按钮，屏蔽其他点击
		if (popupVisible) {
			if (mouseX >= popupBtnX && mouseX <= popupBtnX + popupBtnW
					&& mouseY >= popupBtnY && mouseY <= popupBtnY + popupBtnH) {
				closePopup();
			}
			return true;
		}

		// 数量选择弹窗显示时：处理弹窗内交互，屏蔽商店列表点击
		if (qtyVisible) {
			// 资源不足提示弹窗优先（叠加在数量弹窗之上）
			if (qtyInsufficientVisible) {
				if (mouseX >= qtyInsBtnX && mouseX <= qtyInsBtnX + qtyInsBtnW
						&& mouseY >= qtyInsBtnY && mouseY <= qtyInsBtnY + qtyInsBtnH) {
					closeQtyInsufficient();
				}
				return true;
			}
			// "−" 减少数量按钮
			if (mouseX >= qtyMinusX && mouseX <= qtyMinusX + qtyMinusW
					&& mouseY >= qtyMinusY && mouseY <= qtyMinusY + qtyMinusH) {
				if (qtyAmount > 1) {
					qtyAmount--;
					qtyInsufficientVisible = false;
				}
				return true;
			}
			// "+" 增加数量按钮
			if (mouseX >= qtyPlusX && mouseX <= qtyPlusX + qtyPlusW
					&& mouseY >= qtyPlusY && mouseY <= qtyPlusY + qtyPlusH) {
				int max = getQtyMaxAmount();
				if (qtyAmount < max) {
					qtyAmount++;
					qtyInsufficientVisible = false;
				}
				return true;
			}
			// "确认购买"按钮：检查资源是否足够
			if (mouseX >= qtyConfirmX && mouseX <= qtyConfirmX + qtyConfirmW
					&& mouseY >= qtyConfirmY && mouseY <= qtyConfirmY + qtyConfirmH) {
				confirmQtyAction();
				return true;
			}
			// "取消"按钮：关闭数量选择弹窗
			if (mouseX >= qtyCancelX && mouseX <= qtyCancelX + qtyCancelW
					&& mouseY >= qtyCancelY && mouseY <= qtyCancelY + qtyCancelH) {
				closeQtySelector();
				return true;
			}
			return true;  // 数量弹窗其他区域点击屏蔽底层
		}

		// 检查标签栏点击
		String[] categories = ShopManager.CATEGORIES;
		int tabWidth = (panelW - 16) / categories.length;
		int tabY = panelY + 36;
		for (int i = 0; i < categories.length; i++) {
			int tabX = panelX + 8 + i * tabWidth;
			if (mouseX >= tabX && mouseX <= tabX + tabWidth
					&& mouseY >= tabY && mouseY <= tabY + TAB_HEIGHT) {
				if (currentTabIndex != i) {
					currentTabIndex = i;
					scrollOffset = 0;
					recalculateScroll();
				}
				return true;
			}
		}

		// 检查购买按钮点击：改为打开数量选择弹窗（而非直接发送购买请求）
		List<ShopEntry> entries = getCurrentEntries();
		int listLeft = panelX + 8;
		int listRight = panelX + panelW - 20;

		for (int i = 0; i < entries.size(); i++) {
			int rowY = listTopY + i * ROW_HEIGHT - scrollOffset;
			if (rowY + ROW_HEIGHT < listTopY || rowY > listBottomY) continue;

			int btnX = listLeft + (listRight - listLeft) - BTN_WIDTH;
			int btnY = rowY + (ROW_HEIGHT - 18) / 2;
			if (mouseX >= btnX && mouseX <= btnX + BTN_WIDTH
					&& mouseY >= btnY && mouseY <= btnY + 18) {
				ShopEntry entry = entries.get(i);
				openQtySelector(entry);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
