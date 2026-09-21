package com.randomsurprise.client;

import com.randomsurprise.affix.Affix;
import com.randomsurprise.affix.AffixRarity;
import com.randomsurprise.affix.AffixRegistry;
import com.randomsurprise.network.LotteryConfirmPayload;
import com.randomsurprise.network.ModNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 十连抽结果展示界面
 * 以 5×2 网格展示 10 个抽中词条，点击或按 ESC 关闭
 * - 自适应屏幕大小，小屏幕自动缩小格子
 * - 渐入动画 + 悬停高亮效果
 */
public class LotteryTenScreen extends Screen {

	private static final int COLS = 5;
	private static final int ROWS = 2;
	private static final int BASE_CELL_W = 100;
	private static final int BASE_CELL_H = 110;
	private static final int CELL_GAP = 6;

	private final List<Affix> affixes = new ArrayList<>();
	/** 客户端回传给服务端的原始 affixId 列表（用于确认包，服务端优先用暂存结果） */
	private final List<String> affixIdsRaw;
	private int panelX, panelY, panelW, panelH;
	private int gridX, gridY;
	private int cellW, cellH;

	// 进场动画
	private int animationTicks = 0;
	private static final int ANIM_DURATION = 20;
	private static final int CELL_DELAY = 2; // 每个格子延迟2tick

	// 词条延迟生效：确认包发送状态
	private boolean confirmSent = false;

	public LotteryTenScreen(List<String> affixIds) {
		super(Component.translatable("lottery.randomsurprise.ten_result_title"));
		this.affixIdsRaw = affixIds;
		for (String id : affixIds) {
			Affix a = AffixRegistry.getById(id);
			if (a != null) affixes.add(a);
		}
	}

	@Override
	protected void init() {
		super.init();

		// 自适应计算格子大小
		int maxGridW = this.width - 40;
		int maxGridH = this.height - 100;
		int neededW = COLS * BASE_CELL_W + (COLS - 1) * CELL_GAP;
		int neededH = ROWS * BASE_CELL_H + (ROWS - 1) * CELL_GAP;

		double scaleX = (double) maxGridW / neededW;
		double scaleY = (double) maxGridH / neededH;
		double scale = Math.min(1.0, Math.min(scaleX, scaleY));

		this.cellW = (int) (BASE_CELL_W * scale);
		this.cellH = (int) (BASE_CELL_H * scale);
		// 最小尺寸保证可读
		this.cellW = Math.max(70, cellW);
		this.cellH = Math.max(80, cellH);

		this.panelW = COLS * cellW + (COLS - 1) * CELL_GAP + 24;
		this.panelH = ROWS * cellH + (ROWS - 1) * CELL_GAP + 80;
		this.panelX = (this.width - panelW) / 2;
		this.panelY = (this.height - panelH) / 2;
		this.gridX = panelX + 12;
		this.gridY = panelY + 50;
		this.animationTicks = 0;
	}

	@Override
	public void tick() {
		super.tick();
		if (animationTicks < ANIM_DURATION + COLS * ROWS * CELL_DELAY) {
			animationTicks++;
		} else {
			// 进场动画结束，发送确认包到服务端 —— 词条此时才真正生效
			sendConfirm();
		}
	}

	/**
	 * 发送抽奖确认包到服务端（幂等：仅发送一次）。
	 * 十连抽无旋转动画，进场渐变结束后即应用词条。
	 */
	private void sendConfirm() {
		if (confirmSent) return;
		confirmSent = true;
		ModNetworking.sendToServer(new LotteryConfirmPayload(true, affixIdsRaw));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// 半透明背景遮罩
		graphics.fill(0, 0, this.width, this.height, 0xAA000000);
		super.render(graphics, mouseX, mouseY, partialTick);

		// 主面板渐变背景
		graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xF51A1A2E);

		// 金色边框带渐变
		int border = 0xFFFFD700;
		int borderDark = 0xFFB8860B;
		graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, border);
		graphics.fill(panelX, panelY + panelH - 2, panelX + panelW, panelY + panelH, borderDark);
		graphics.fill(panelX, panelY, panelX + 2, panelY + panelH, border);
		graphics.fill(panelX + panelW - 2, panelY, panelX + panelW, panelY + panelH, borderDark);

		// 面板内部装饰角
		int cornerSize = 8;
		graphics.fill(panelX + 2, panelY + 2, panelX + 2 + cornerSize, panelY + 3, 0xFFFFD700);
		graphics.fill(panelX + 2, panelY + 2, panelX + 3, panelY + 2 + cornerSize, 0xFFFFD700);
		graphics.fill(panelX + panelW - 2 - cornerSize, panelY + 2, panelX + panelW - 2, panelY + 3, 0xFFFFD700);
		graphics.fill(panelX + panelW - 3, panelY + 2, panelX + panelW - 2, panelY + 2 + cornerSize, 0xFFFFD700);

		// 标题
		graphics.drawCenteredString(this.font,
				Component.translatable("lottery.randomsurprise.ten_result_title"),
				this.width / 2, panelY + 12, 0xFFFFD700);
		graphics.drawCenteredString(this.font,
				Component.translatable("lottery.randomsurprise.ten_result_subtitle"),
				this.width / 2, panelY + 26, 0xFFCCCCCC);

		// 渲染 5×2 网格
		for (int i = 0; i < affixes.size() && i < COLS * ROWS; i++) {
			int col = i % COLS;
			int row = i / COLS;
			int cellX = gridX + col * (cellW + CELL_GAP);
			int cellY = gridY + row * (cellH + CELL_GAP);

			// 进场动画：每个格子按顺序延迟出现
			int cellDelay = i * CELL_DELAY;
			int cellProgress = Math.max(0, Math.min(ANIM_DURATION, animationTicks - cellDelay));
			if (cellProgress == 0) continue;

			renderCell(graphics, cellX, cellY, affixes.get(i),
					mouseX >= cellX && mouseX <= cellX + cellW
							&& mouseY >= cellY && mouseY <= cellY + cellH,
					cellProgress);
		}

		// 关闭提示（闪烁效果）
		int blinkAlpha = (int) (128 + 127 * Math.sin(animationTicks * 0.1));
		int hintColor = (blinkAlpha << 24) | 0x888888;
		graphics.drawCenteredString(this.font,
				Component.translatable("lottery.randomsurprise.ten_close_hint"),
				this.width / 2, panelY + panelH - 12, hintColor);
	}

	/** 渲染单个词条格子 */
	private void renderCell(GuiGraphics graphics, int x, int y, Affix affix, boolean hovered, int progress) {
		AffixRarity rarity = affix.getRarity();
		int rarityColor = rarity.getColor();

		// 渐入透明度（0~255）
		float progressF = progress / (float) ANIM_DURATION;
		int alpha = (int) (0xFF * progressF);
		if (alpha > 0xFF) alpha = 0xFF;

		// 悬停时向上偏移2px
		int offsetY = hovered ? -2 : 0;
		y += offsetY;

		// 主背景（悬停时变亮）
		int bgBase = hovered ? 0xFF252540 : 0xE0202030;
		graphics.fill(x, y, x + cellW, y + cellH, bgBase);

		// 顶部稀有度渐变色条
		int barHeight = Math.max(8, cellH / 12);
		int barColor = (alpha << 24) | rarityColor;
		graphics.fill(x, y, x + cellW, y + barHeight, barColor);
		// 高光
		graphics.fill(x, y, x + cellW, y + 2, 0x88FFFFFF);
		// 底部渐变
		graphics.fill(x, y + barHeight - 2, x + cellW, y + barHeight, 0x44000000);

		// 边框：使用稀有度颜色
		int borderCol = hovered ? 0xFFFFFFFF : ((alpha << 24) | rarityColor);
		graphics.fill(x, y, x + cellW, y + 1, borderCol);
		graphics.fill(x, y + cellH - 1, x + cellW, y + cellH, borderCol);
		graphics.fill(x, y, x + 1, y + cellH, borderCol);
		graphics.fill(x + cellW - 1, y, x + cellW, y + cellH, borderCol);

		// 稀有度名称
		String rarityName = rarity.getDisplayName();
		int rarityTextWidth = this.font.width(rarityName);
		graphics.drawString(this.font, rarityName,
				x + (cellW - rarityTextWidth) / 2, y + barHeight + 3, 0xFFFFFFFF);

		// 类型标识
		boolean good = affix.isGood();
		String typeText = good ? "✦ 增益" : "⚔ 敌对";
		int typeColor = good ? 0xFFAAFFAA : 0xFFFFAAAA;
		int typeWidth = this.font.width(typeText);
		graphics.drawString(this.font, typeText,
				x + (cellW - typeWidth) / 2, y + barHeight + 15, typeColor);

		// 分隔线
		int dividerY = y + barHeight + 28;
		graphics.fill(x + 8, dividerY, x + cellW - 8, dividerY + 1, 0x55FFFFFF);

		// 词条名称（截断显示）
		String name = Component.translatable(affix.getNameKey()).getString();
		int maxNameWidth = cellW - 10;
		if (this.font.width(name) > maxNameWidth) {
			name = this.font.plainSubstrByWidth(name, maxNameWidth - 3) + "...";
		}
		int nameWidth = this.font.width(name);
		graphics.drawString(this.font, name,
				x + (cellW - nameWidth) / 2, dividerY + 4, 0xFFCCCCCC);

		// 简短描述（多行截断）
		String desc = Component.translatable(affix.getDescKey()).getString();
		int maxDescWidth = cellW - 10;
		List<String> lines = wrapText(desc, maxDescWidth);
		int descY = dividerY + 16;
		// 根据格子高度动态决定显示行数
		int maxLines = Math.max(2, (cellH - barHeight - 50) / 11);
		for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
			String line = lines.get(i);
			if (i == maxLines - 1 && lines.size() > maxLines) {
				line = this.font.plainSubstrByWidth(line, maxDescWidth - 3) + "...";
			}
			graphics.drawString(this.font, line, x + 5, descY + i * 11, 0xFFAAAAAA);
		}

		// 悬停发光效果
		if (hovered) {
			int glowAlpha = (int) (100 + 50 * Math.sin(animationTicks * 0.15));
			int glowColor = (glowAlpha << 24) | 0xFFFF00;
			graphics.fill(x - 1, y - 1, x + cellW + 1, y, glowColor);
			graphics.fill(x - 1, y + cellH, x + cellW + 1, y + cellH + 1, glowColor);
			graphics.fill(x - 1, y, x, y + cellH, glowColor);
			graphics.fill(x + cellW, y, x + cellW + 1, y + cellH, glowColor);
		}

		// 渐入时的缩放效果（简单模拟：从中心扩展）
		if (progress < ANIM_DURATION) {
			float scaleFactor = progressF;
			int margin = (int) ((1 - scaleFactor) * cellW / 4);
			if (margin > 0) {
				// 用背景色覆盖边缘模拟缩放
				graphics.fill(x, y, x + margin, y + cellH, 0xF51A1A2E);
				graphics.fill(x + cellW - margin, y, x + cellW, y + cellH, 0xF51A1A2E);
				int marginY = (int) ((1 - scaleFactor) * cellH / 4);
				if (marginY > 0) {
					graphics.fill(x, y, x + cellW, y + marginY, 0xF51A1A2E);
					graphics.fill(x, y + cellH - marginY, x + cellW, y + cellH, 0xF51A1A2E);
				}
			}
		}
	}

	/** 简单文字换行 */
	private List<String> wrapText(String text, int maxWidth) {
		List<String> result = new ArrayList<>();
		if (text == null || text.isEmpty()) return result;
		StringBuilder current = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			String test = current.toString() + c;
			if (this.font.width(test) > maxWidth) {
				if (current.length() > 0) result.add(current.toString());
				current = new StringBuilder().append(c);
			} else {
				current.append(c);
			}
		}
		if (current.length() > 0) result.add(current.toString());
		return result;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			this.onClose();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public void onClose() {
		// 安全兜底：关闭前发送确认包应用词条（券已消耗，避免白白损失）
		sendConfirm();
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
