package com.randomsurprise.client;

import com.randomsurprise.affix.Affix;
import com.randomsurprise.affix.AffixRarity;
import com.randomsurprise.affix.AffixRegistry;
import com.randomsurprise.network.LotteryRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 抽奖准备界面（v2）
 * 主界面：标题、消耗信息、问号按钮(查看概率)、开始抽奖、取消
 * 点击问号按钮弹出概率详情浮层，显示所有24个词条及对应概率
 * 右键抽奖券时打开此界面，点击开始后才真正消耗资源并抽奖
 */
public class LotteryStartScreen extends Screen {

	private static final int PANEL_WIDTH = 400;
	private static final int ROW_HEIGHT = 18;

	private int panelX, panelY, panelHeight;
	private int startBtnX, startBtnY, startBtnW, startBtnH;
	private int tenBtnX, tenBtnY, tenBtnW, tenBtnH;
	private int cancelBtnX, cancelBtnY, cancelBtnW, cancelBtnH;
	private int questionBtnX, questionBtnY, questionBtnSize;

	/** 是否满足抽奖条件（手持抽奖券） */
	private boolean canAfford = true;
	/** 是否显示概率详情浮层 */
	private boolean showProbabilities = false;

	// 概率浮层滚动
	private int probScrollOffset = 0;
	private int probMaxScroll = 0;

	// 概率浮层布局
	private int probPanelX, probPanelY, probPanelW, probPanelH;

	public LotteryStartScreen() {
		super(Component.translatable("lottery.randomsurprise.start_title"));
	}

	/** 客户端调用：打开抽奖准备界面 */
	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.setScreen(new LotteryStartScreen());
		}
	}

	@Override
	protected void init() {
		super.init();
		this.panelX = (this.width - PANEL_WIDTH) / 2;
		this.panelY = 20;
		this.panelHeight = this.height - 40;

		// 主界面按钮布局（三按钮：单抽 | 十连抽 | 取消）
		int btnY = panelY + panelHeight - 30;
		this.startBtnW = 100;
		this.startBtnH = 20;
		this.startBtnX = this.width / 2 - startBtnW - 62;
		this.startBtnY = btnY;

		this.tenBtnW = 100;
		this.tenBtnH = 20;
		this.tenBtnX = this.width / 2 - tenBtnW / 2;
		this.tenBtnY = btnY;

		this.cancelBtnW = 100;
		this.cancelBtnH = 20;
		this.cancelBtnX = this.width / 2 + 62;
		this.cancelBtnY = btnY;

		// 问号按钮（右上角）
		this.questionBtnSize = 18;
		this.questionBtnX = panelX + PANEL_WIDTH - questionBtnSize - 8;
		this.questionBtnY = panelY + 8;

		// 概率浮层布局（覆盖大部分屏幕）
		this.probPanelW = Math.min(560, this.width - 20);
		this.probPanelH = Math.min(380, this.height - 40);
		this.probPanelX = (this.width - probPanelW) / 2;
		this.probPanelY = 20;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// 半透明背景遮罩
		graphics.fill(0, 0, this.width, this.height, 0xAA000000);
		super.render(graphics, mouseX, mouseY, partialTick);

		// 主面板背景
		renderMainPanel(graphics, mouseX, mouseY);

		// 概率浮层（覆盖在主面板之上）
		if (showProbabilities) {
			renderProbabilitiesPanel(graphics, mouseX, mouseY);
		}
	}

	/** 渲染主面板 */
	private void renderMainPanel(GuiGraphics graphics, int mouseX, int mouseY) {
		graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, 0xCC1A1A2E);
		int border = 0xFFFFD700;
		graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, border);
		graphics.fill(panelX, panelY + panelHeight - 1, panelX + PANEL_WIDTH, panelY + panelHeight, border);
		graphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, border);
		graphics.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, border);

		// 标题
		graphics.drawCenteredString(this.font,
				Component.translatable("lottery.randomsurprise.start_title"),
				this.width / 2, panelY + 12, 0xFFFFD700);

		// 问号按钮（右上角）
		boolean qHovered = mouseX >= questionBtnX && mouseX <= questionBtnX + questionBtnSize
				&& mouseY >= questionBtnY && mouseY <= questionBtnY + questionBtnSize;
		int qColor = qHovered ? 0xFF4CAF50 : 0xFF2E7D32;
		graphics.fill(questionBtnX, questionBtnY, questionBtnX + questionBtnSize, questionBtnY + questionBtnSize, qColor);
		graphics.fill(questionBtnX, questionBtnY, questionBtnX + questionBtnSize, questionBtnY + 1, 0xFF66BB6A);
		graphics.fill(questionBtnX, questionBtnY + questionBtnSize - 1, questionBtnX + questionBtnSize, questionBtnY + questionBtnSize, 0xFF1B5E20);
		graphics.fill(questionBtnX, questionBtnY, questionBtnX + 1, questionBtnY + questionBtnSize, 0xFF66BB6A);
		graphics.fill(questionBtnX + questionBtnSize - 1, questionBtnY, questionBtnX + questionBtnSize, questionBtnY + questionBtnSize, 0xFF1B5E20);
		graphics.drawCenteredString(this.font, "?", questionBtnX + questionBtnSize / 2, questionBtnY + 5, 0xFFFFFFFF);

		// 消耗信息（双行：单抽 + 十连抽）
		graphics.drawCenteredString(this.font, "§a单抽消耗: 1张抽奖券   §6十连抽消耗: 10张抽奖券",
				this.width / 2, panelY + 40, 0xFFAAFFAA);

		// 简要说明（引导玩家点击问号查看详情）
		graphics.drawCenteredString(this.font, "§7点击右上角 §e? §7查看词条概率详情",
				this.width / 2, panelY + 64, 0xFFAAAAAA);

		// 抽奖说明
		int totalAffixes = AffixRegistry.getAll().size();
		graphics.drawCenteredString(this.font,
				String.format("§f共 %d 种词条 §7| §a好词条 70%% §7| §c坏词条 30%%", totalAffixes),
				this.width / 2, panelY + 90, 0xFFCCCCCC);
		graphics.drawCenteredString(this.font, "§7坏词条越多，稀有度越高（补偿机制）",
				this.width / 2, panelY + 104, 0xFF888888);

		// 按钮
		renderButtons(graphics, mouseX, mouseY);

		// 底部提示
		graphics.drawCenteredString(this.font,
				Component.translatable("lottery.randomsurprise.start_hint"),
				this.width / 2, panelY + panelHeight - 50, 0xFF888888);
	}

	/** 渲染概率详情浮层 */
	private void renderProbabilitiesPanel(GuiGraphics graphics, int mouseX, int mouseY) {
		// 浮层背景遮罩（高不透明，遮挡背景界面避免文字透出干扰阅读）
		graphics.fill(0, 0, this.width, this.height, 0xDD000000);

		// 浮层面板
		graphics.fill(probPanelX, probPanelY, probPanelX + probPanelW, probPanelY + probPanelH, 0xEE1A1A2E);
		int border = 0xFFFFD700;
		graphics.fill(probPanelX, probPanelY, probPanelX + probPanelW, probPanelY + 1, border);
		graphics.fill(probPanelX, probPanelY + probPanelH - 1, probPanelX + probPanelW, probPanelY + probPanelH, border);
		graphics.fill(probPanelX, probPanelY, probPanelX + 1, probPanelY + probPanelH, border);
		graphics.fill(probPanelX + probPanelW - 1, probPanelY, probPanelX + probPanelW, probPanelY + probPanelH, border);

		// 标题
		graphics.drawCenteredString(this.font,
				Component.translatable("lottery.randomsurprise.probabilities_title"),
				this.width / 2, probPanelY + 8, 0xFFFFD700);

		// 分隔线
		int dividerY = probPanelY + 24;
		graphics.fill(probPanelX + 8, dividerY, probPanelX + probPanelW - 8, dividerY + 1, 0xFF6B6B8E);

		// 两列布局：左增益，右敌对
		int colWidth = (probPanelW - 24) / 2;
		int leftX = probPanelX + 8;
		int rightX = probPanelX + 16 + colWidth;
		int listStartY = dividerY + 8;

		graphics.drawString(this.font, "§a§l增益词条（70%）", leftX, listStartY, 0xFFAAFFAA);
		graphics.drawString(this.font, "§c§l敌对词条（30%）", rightX, listStartY, 0xFFFFAAAA);

		// 获取所有词条并按稀有度排序
		List<Affix> goodAffixes = new ArrayList<>();
		List<Affix> badAffixes = new ArrayList<>();
		for (Affix affix : AffixRegistry.getAll()) {
			if (affix.isGood()) goodAffixes.add(affix);
			else badAffixes.add(affix);
		}

		// 计算概率（按稀有度）
		double[] goodProbs = calculateRarityProbabilities(true);
		double[] badProbs = calculateRarityProbabilities(false);

		// ===== 滚动内容区（启用 scissor 防止溢出） =====
		int contentTop = listStartY + 14;
		int contentBottom = probPanelY + probPanelH - 24;

		// 计算总内容高度 → probMaxScroll
		int goodHeight = calculateColumnHeight(goodAffixes);
		int badHeight = calculateColumnHeight(badAffixes);
		int maxContentHeight = Math.max(goodHeight, badHeight);
		probMaxScroll = Math.max(0, maxContentHeight - (contentBottom - contentTop));
		probScrollOffset = Math.min(probScrollOffset, probMaxScroll);

		// 启用裁切
		graphics.enableScissor(probPanelX + 1, contentTop, probPanelX + probPanelW - 1, contentBottom);

		// 渲染好词条列表
		renderAffixColumn(graphics, leftX, contentTop, colWidth, goodAffixes, goodProbs, true);

		// 渲染坏词条列表
		renderAffixColumn(graphics, rightX, contentTop, colWidth, badAffixes, badProbs, false);

		graphics.disableScissor();

		// 关闭提示
		graphics.drawCenteredString(this.font,
				Component.translatable("lottery.randomsurprise.probabilities_close"),
				this.width / 2, probPanelY + probPanelH - 16, 0xFF888888);
	}

	/** 渲染一列词条（按稀有度分组） */
	private void renderAffixColumn(GuiGraphics graphics, int x, int startY, int width,
			List<Affix> affixes, double[] rarityProbs, boolean good) {
		// 按稀有度排序
		List<Affix> sorted = new ArrayList<>(affixes);
		sorted.sort((a, b) -> Integer.compare(a.getRarity().getTier(), b.getRarity().getTier()));

		int y = startY - probScrollOffset;
		AffixRarity prevRarity = null;
		for (Affix affix : sorted) {
			// 稀有度分组标题
			if (prevRarity != affix.getRarity()) {
				int tier = affix.getRarity().getTier();
				double rarityProb = rarityProbs[tier];
				// 该稀有度单个词条概率 = 稀有度概率 / 词条数
				int countInRarity = countAffixesInRarity(sorted, affix.getRarity());
				double singleProb = countInRarity > 0 ? rarityProb / countInRarity : 0;

				String header = String.format("[%s] %.1f%% | 单个: %.1f%%",
					affix.getRarity().getDisplayName(),
					rarityProb * 100, singleProb * 100);
				graphics.drawString(this.font, header, x, y, 0xFF000000 | affix.getRarity().getColor());
				y += 12;
				prevRarity = affix.getRarity();
			}

			// 词条行
			renderAffixRow(graphics, x + 4, y, width - 4, affix);
			y += ROW_HEIGHT;
		}
	}

	/** 计算指定列表中某稀有度的词条数 */
	private int countAffixesInRarity(List<Affix> list, AffixRarity rarity) {
		int count = 0;
		for (Affix a : list) {
			if (a.getRarity() == rarity) count++;
		}
		return count;
	}

	/** 计算一列词条的总内容高度（用于 probMaxScroll，不渲染） */
	private int calculateColumnHeight(List<Affix> affixes) {
		List<Affix> sorted = new ArrayList<>(affixes);
		sorted.sort((a, b) -> Integer.compare(a.getRarity().getTier(), b.getRarity().getTier()));
		int height = 0;
		AffixRarity prevRarity = null;
		for (Affix affix : sorted) {
			if (prevRarity != affix.getRarity()) {
				height += 12; // 稀有度分组标题行
				prevRarity = affix.getRarity();
			}
			height += ROW_HEIGHT; // 词条行
		}
		return height;
	}

	/** 渲染单行词条 */
	private void renderAffixRow(GuiGraphics graphics, int x, int y, int width, Affix affix) {
		AffixRarity rarity = affix.getRarity();
		int color = rarity.getColor();

		// 稀有度颜色方块
		graphics.fill(x, y + 2, x + 8, y + 12, 0xFF000000 | color);
		graphics.fill(x, y + 2, x + 8, y + 3, 0x66FFFFFF);
		graphics.fill(x, y + 11, x + 8, y + 12, 0x66000000);

		// 词条名称
		String name = Component.translatable(affix.getNameKey()).getString();
		// 截断过长名称
		int maxNameWidth = width - 8 - 50;
		if (this.font.width(name) > maxNameWidth) {
			name = this.font.plainSubstrByWidth(name, maxNameWidth - 3) + "...";
		}
		graphics.drawString(this.font, name, x + 12, y + 3, 0xFFCCCCCC);
	}

	/**
	 * 计算每个稀有度的总概率（好或坏）
	 * 返回6个稀有度的概率数组
	 */
	private double[] calculateRarityProbabilities(boolean good) {
		double goodChance = 0.70;
		double badChance = 0.30;
		double baseChance = good ? goodChance : badChance;

		int globalBadCount = ClientAffixData.getGlobalBadCount();
		double rarityBonus = Math.min(0.15, (globalBadCount / 5.0) * 0.01);

		double[] weights = new double[6];
		weights[0] = 0.40 - rarityBonus;        // 白
		weights[1] = 0.25;                      // 绿
		weights[2] = 0.15;                      // 蓝
		weights[3] = 0.10 + rarityBonus * 0.4;  // 紫
		weights[4] = 0.06 + rarityBonus * 0.3;  // 红
		weights[5] = 0.04 + rarityBonus * 0.3;  // 金

		double[] result = new double[6];
		for (int i = 0; i < 6; i++) {
			result[i] = baseChance * weights[i];
		}
		return result;
	}

	/** 渲染底部按钮 */
	private void renderButtons(GuiGraphics graphics, int mouseX, int mouseY) {
		// 开始抽奖按钮（单抽 - 绿色）
		boolean startHovered = mouseX >= startBtnX && mouseX <= startBtnX + startBtnW
				&& mouseY >= startBtnY && mouseY <= startBtnY + startBtnH;
		int startColor = !canAfford ? 0xFF555555 : (startHovered ? 0xFF4CAF50 : 0xFF2E7D32);
		graphics.fill(startBtnX, startBtnY, startBtnX + startBtnW, startBtnY + startBtnH, startColor);
		graphics.fill(startBtnX, startBtnY, startBtnX + startBtnW, startBtnY + 1, 0xFF66BB6A);
		graphics.fill(startBtnX, startBtnY + startBtnH - 1, startBtnX + startBtnW, startBtnY + startBtnH, 0xFF1B5E20);
		graphics.fill(startBtnX, startBtnY, startBtnX + 1, startBtnY + startBtnH, 0xFF66BB6A);
		graphics.fill(startBtnX + startBtnW - 1, startBtnY, startBtnX + startBtnW, startBtnY + startBtnH, 0xFF1B5E20);
		String startText = canAfford ? "单抽 (1券)" : "无法抽奖";
		int startTextWidth = this.font.width(startText);
		graphics.drawString(this.font, startText, startBtnX + (startBtnW - startTextWidth) / 2, startBtnY + 6, 0xFFFFFFFF);

		// 十连抽按钮（金色）
		boolean tenHovered = mouseX >= tenBtnX && mouseX <= tenBtnX + tenBtnW
				&& mouseY >= tenBtnY && mouseY <= tenBtnY + tenBtnH;
		int tenColor = !canAfford ? 0xFF555555 : (tenHovered ? 0xFFFFC107 : 0xFFFF8F00);
		graphics.fill(tenBtnX, tenBtnY, tenBtnX + tenBtnW, tenBtnY + tenBtnH, tenColor);
		graphics.fill(tenBtnX, tenBtnY, tenBtnX + tenBtnW, tenBtnY + 1, 0xFFFFD54F);
		graphics.fill(tenBtnX, tenBtnY + tenBtnH - 1, tenBtnX + tenBtnW, tenBtnY + tenBtnH, 0xFFE65100);
		graphics.fill(tenBtnX, tenBtnY, tenBtnX + 1, tenBtnY + tenBtnH, 0xFFFFD54F);
		graphics.fill(tenBtnX + tenBtnW - 1, tenBtnY, tenBtnX + tenBtnW, tenBtnY + tenBtnH, 0xFFE65100);
		String tenText = canAfford ? "十连抽 (10券)" : "无法抽奖";
		int tenTextWidth = this.font.width(tenText);
		graphics.drawString(this.font, tenText, tenBtnX + (tenBtnW - tenTextWidth) / 2, tenBtnY + 6, 0xFF000000);

		// 取消按钮
		boolean cancelHovered = mouseX >= cancelBtnX && mouseX <= cancelBtnX + cancelBtnW
				&& mouseY >= cancelBtnY && mouseY <= cancelBtnY + cancelBtnH;
		int cancelColor = cancelHovered ? 0xFF666666 : 0xFF333333;
		graphics.fill(cancelBtnX, cancelBtnY, cancelBtnX + cancelBtnW, cancelBtnY + cancelBtnH, cancelColor);
		graphics.fill(cancelBtnX, cancelBtnY, cancelBtnX + cancelBtnW, cancelBtnY + 1, 0xFF777777);
		graphics.fill(cancelBtnX, cancelBtnY + cancelBtnH - 1, cancelBtnX + cancelBtnW, cancelBtnY + cancelBtnH, 0xFF222222);
		graphics.fill(cancelBtnX, cancelBtnY, cancelBtnX + 1, cancelBtnY + cancelBtnH, 0xFF777777);
		graphics.fill(cancelBtnX + cancelBtnW - 1, cancelBtnY, cancelBtnX + cancelBtnW, cancelBtnY + cancelBtnH, 0xFF222222);
		String cancelText = "取消";
		int cancelTextWidth = this.font.width(cancelText);
		graphics.drawString(this.font, cancelText, cancelBtnX + (cancelBtnW - cancelTextWidth) / 2, cancelBtnY + 6, 0xFFCCCCCC);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (showProbabilities) {
			if (amount > 0) probScrollOffset -= 36;
			else probScrollOffset += 36;
			probScrollOffset = Math.max(0, Math.min(probScrollOffset, probMaxScroll));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) return super.mouseClicked(mouseX, mouseY, button);


		// 如果显示概率浮层，点击任意位置关闭浮层
		if (showProbabilities) {
			showProbabilities = false;
			return true;
		}

		// 问号按钮 - 打开概率详情
		if (mouseX >= questionBtnX && mouseX <= questionBtnX + questionBtnSize
				&& mouseY >= questionBtnY && mouseY <= questionBtnY + questionBtnSize) {
			showProbabilities = true;
			probScrollOffset = 0;
			return true;
		}

		// 开始抽奖按钮（单抽）
		if (mouseX >= startBtnX && mouseX <= startBtnX + startBtnW
				&& mouseY >= startBtnY && mouseY <= startBtnY + startBtnH) {
			if (canAfford) {
				com.randomsurprise.network.ModNetworking.sendToServer(new LotteryRequestPayload(false));
				this.onClose();
			}
			return true;
		}

		// 十连抽按钮
		if (mouseX >= tenBtnX && mouseX <= tenBtnX + tenBtnW
				&& mouseY >= tenBtnY && mouseY <= tenBtnY + tenBtnH) {
			if (canAfford) {
				com.randomsurprise.network.ModNetworking.sendToServer(new LotteryRequestPayload(true));
				this.onClose();
			}
			return true;
		}

		// 取消按钮
		if (mouseX >= cancelBtnX && mouseX <= cancelBtnX + cancelBtnW
				&& mouseY >= cancelBtnY && mouseY <= cancelBtnY + cancelBtnH) {
			this.onClose();
			return true;
		}

		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
