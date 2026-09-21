package com.randomsurprise.client;

import com.randomsurprise.affix.Affix;
import com.randomsurprise.affix.AffixRegistry;
import com.randomsurprise.client.ClientAffixData.PlayerBadAffixes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
// MouseButtonEvent import removed for 1.20.1
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 玩家词条查看界面
 * - 上半部分：自己的词条（增益 + 敌对）
 * - 下半部分：全局所有玩家的敌对词条（可点击玩家名折叠/展开）
 * - 支持鼠标滚轮滚动，内容超出面板时自动裁切
 */
public class AffixListScreen extends Screen {
	private static final int LINE_HEIGHT = 12;
	private static final int PADDING = 12;
	private static final int SCROLL_STEP = LINE_HEIGHT * 3;

	/** 已展开的玩家名（跨界面持久，避免每次重新打开要点开） */
	private static final Set<String> EXPANDED_PLAYERS = new HashSet<>();

	private int scrollOffset = 0;
	private int maxScroll = 0;

	/** 缓存本帧玩家名行坐标，用于点击切换展开 */
	private final List<PlayerRowBounds> playerRowBounds = new ArrayList<>();

	private record PlayerRowBounds(String name, int x, int y, int width, int height) {}

	public AffixListScreen() {
		super(Component.translatable("affix_list.randomsurprise.title"));
	}

	@Override
	protected void init() {
		super.init();
		this.scrollOffset = 0;
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// 半透明背景
		graphics.fill(0, 0, this.width, this.height, 0xAA000000);
		super.render(graphics, mouseX, mouseY, partialTick);

		int topY = 20;
		int boxWidth = Math.min(320, this.width - 60);
		int boxX = this.width - boxWidth - 10;
		int centerX = boxX + boxWidth / 2;
		int boxHeight = this.height - 60;
		int bottomY = topY + boxHeight;

		// 主面板背景
		graphics.fill(boxX, topY, boxX + boxWidth, bottomY, 0xCC1A1A2E);
		// 边框
		int border = 0xFF6B6B8E;
		graphics.fill(boxX, topY, boxX + boxWidth, topY + 1, border);
		graphics.fill(boxX, bottomY - 1, boxX + boxWidth, bottomY, border);
		graphics.fill(boxX, topY, boxX + 1, bottomY, border);
		graphics.fill(boxX + boxWidth - 1, topY, boxX + boxWidth, bottomY, border);

		// ===== 标题（不裁切） =====
		int y = topY + PADDING;
		graphics.drawCenteredString(this.font,
				Component.translatable("affix_list.randomsurprise.title"),
				centerX, y, 0xFFFFD700);
		y += LINE_HEIGHT + 6;

		// ===== 内容区（启用 scissor 防止溢出） =====
		int contentTop = y;
		int contentBottom = bottomY - PADDING - LINE_HEIGHT;

		// 计算总内容高度 → maxScroll
		int totalHeight = calculateContentHeight(boxWidth);
		maxScroll = Math.max(0, totalHeight - (contentBottom - contentTop));
		scrollOffset = Math.min(scrollOffset, maxScroll);

		// 启用裁切
		try {
			graphics.enableScissor(boxX + 1, contentTop, boxX + boxWidth - 1, contentBottom);
		} catch (Throwable ignored) {
			// API 不支持时降级（不裁切，依赖滚动控制）
		}

		int contentY = contentTop - scrollOffset;
		renderContent(graphics, boxX, boxWidth, contentY, centerX);

		try {
			graphics.disableScissor();
		} catch (Throwable ignored) {}

		// ===== 底部关闭提示（不裁切） =====
		graphics.drawCenteredString(this.font,
				Component.translatable("lottery.randomsurprise.close_hint"),
				centerX, bottomY - PADDING - LINE_HEIGHT / 2, 0xFF888888);
	}

	/** 计算内容总高度（不渲染，用于 maxScroll） */
	private int calculateContentHeight(int boxWidth) {
		int y = 0;
		List<Affix> ownGood = ClientAffixData.getOwnGoodAffixes();
		List<Affix> ownBad = ClientAffixData.getOwnBadAffixes();

		// "我的词条" 标题
		y += LINE_HEIGHT + 4;
		// 增益区
		y += LINE_HEIGHT; // section 标题
		y += ownGood.isEmpty() ? LINE_HEIGHT : ownGood.size() * LINE_HEIGHT * 2;
		y += 4;
		// 自己的敌对词条区
		y += LINE_HEIGHT;
		y += ownBad.isEmpty() ? LINE_HEIGHT : ownBad.size() * LINE_HEIGHT * 2;
		y += 8;
		// 分割线
		y += 4;
		// 全局敌对词条区
		y += LINE_HEIGHT; // global 标题
		y += LINE_HEIGHT; // hint
		y += LINE_HEIGHT + 2; // count
		// 各玩家
		var allBad = ClientAffixData.getAllPlayerBadAffixes();
		for (var pba : allBad) {
			if (pba.affixIds().isEmpty()) continue;
			y += LINE_HEIGHT;
			if (EXPANDED_PLAYERS.contains(pba.playerName())) {
				y += pba.affixIds().size() * LINE_HEIGHT * 2;
			}
			y += 2;
		}
		return y;
	}

	/** 渲染内容主体 */
	private void renderContent(GuiGraphics graphics, int boxX, int boxWidth, int y, int centerX) {
		int innerX = boxX + PADDING;

		// ===== 我的词条 =====
		graphics.drawCenteredString(this.font,
				Component.translatable("affix_list.randomsurprise.my_affixes"),
				centerX, y, 0xFFAAFFAA);
		y += LINE_HEIGHT + 4;

		List<Affix> ownGood = ClientAffixData.getOwnGoodAffixes();
		List<Affix> ownBad = ClientAffixData.getOwnBadAffixes();

		graphics.drawString(this.font,
				Component.translatable("affix_list.randomsurprise.good_section", ownGood.size()),
				innerX, y, 0xFF66FF66);
		y += LINE_HEIGHT;
		y = renderAffixList(graphics, ownGood, innerX + 8, y, boxWidth);
		y += 4;

		graphics.drawString(this.font,
				Component.translatable("affix_list.randomsurprise.bad_section_own", ownBad.size()),
				innerX, y, 0xFFFF6666);
		y += LINE_HEIGHT;
		y = renderAffixList(graphics, ownBad, innerX + 8, y, boxWidth);
		y += 8;

		// ===== 全局敌对词条区 =====
		graphics.fill(innerX, y - 2, boxX + boxWidth - PADDING, y - 1, 0xFF6B6B8E);
		y += 4;
		graphics.drawCenteredString(this.font,
				Component.translatable("affix_list.randomsurprise.global_bad"),
				centerX, y, 0xFFFF5555);
		y += LINE_HEIGHT;
		graphics.drawCenteredString(this.font,
				Component.translatable("affix_list.randomsurprise.global_bad_hint"),
				centerX, y, 0xFFAAAAAA);
		y += LINE_HEIGHT;

		int globalCount = ClientAffixData.getGlobalBadCount();
		graphics.drawCenteredString(this.font,
				Component.translatable("affix_list.randomsurprise.global_bad_count", globalCount),
				centerX, y, 0xFFFFFFAA);
		y += LINE_HEIGHT + 2;

		// 清空并重新记录玩家名行的点击区域
		playerRowBounds.clear();
		var allBad = ClientAffixData.getAllPlayerBadAffixes();
		for (PlayerBadAffixes pba : allBad) {
			if (pba.affixIds().isEmpty()) continue;
			boolean expanded = EXPANDED_PLAYERS.contains(pba.playerName());
			String arrow = expanded ? "▼" : "▶";
			String label = arrow + " [" + pba.playerName() + "] " + pba.affixIds().size() + "个";
			graphics.drawString(this.font, Component.literal(label), innerX, y, 0xFFFFAA00);
			// 记录点击区域（整行宽度）
			playerRowBounds.add(new PlayerRowBounds(
					pba.playerName(), innerX, y, boxWidth - PADDING * 2, LINE_HEIGHT));
			y += LINE_HEIGHT;

			if (expanded) {
				for (String affixId : pba.affixIds()) {
					Affix a = AffixRegistry.getById(affixId);
					if (a == null) continue;
					y = renderSingleAffix(graphics, a, innerX + 8, y, boxWidth);
				}
				y += 2;
			}
		}
	}

	/** 渲染词条列表（自己持有） */
	private int renderAffixList(GuiGraphics graphics, List<Affix> affixes, int x, int y, int boxWidth) {
		if (affixes.isEmpty()) {
			graphics.drawString(this.font,
					Component.translatable("affix_list.randomsurprise.empty"),
					x, y, 0xFF888888);
			return y + LINE_HEIGHT;
		}
		for (Affix a : affixes) {
			y = renderSingleAffix(graphics, a, x, y, boxWidth);
		}
		return y;
	}

	/**
	 * 渲染单条词条（按稀有度上色，含效果描述数值）
	 * 名称和描述分两行显示，提高可读性
	 */
	private int renderSingleAffix(GuiGraphics graphics, Affix a, int x, int y, int boxWidth) {
		int rarityColor = a.getRarity().getColor() | 0xFF000000;
		int grayColor = 0xFF888888;
		int maxWidth = boxWidth - PADDING * 2 - 20;

		String name = Component.translatable(a.getNameKey()).getString();
		String desc = Component.translatable(a.getDescKey()).getString();

		String tag = "[" + a.getRarity().getDisplayName() + "]";
		String nameLine = tag + " " + name;

		if (this.font.width(nameLine) > maxWidth) {
			nameLine = truncateText(nameLine, maxWidth);
		}

		var tagComp = Component.literal(tag).withStyle(s -> s.withColor(rarityColor).withBold(true));
		var nameComp = Component.literal(" " + nameLine.substring(tag.length() + 1)).withStyle(s -> s.withColor(rarityColor));
		var nameFinal = Component.empty().append(tagComp).append(nameComp);
		graphics.drawString(this.font, nameFinal, x, y, rarityColor);
		y += LINE_HEIGHT;

		if (!desc.isEmpty()) {
			String descLine = "  " + desc;
			if (this.font.width(descLine) > maxWidth) {
				descLine = truncateText(descLine, maxWidth);
			}
			graphics.drawString(this.font, descLine, x, y, grayColor);
			y += LINE_HEIGHT;
		}

		return y;
	}

	/** 按像素宽度截断文本，保留格式码 */
	private String truncateText(String text, int maxWidth) {
		if (this.font.width(text) <= maxWidth) return text;
		StringBuilder sb = new StringBuilder();
		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (c == '§' && i + 1 < text.length()) {
				sb.append(c).append(text.charAt(i + 1));
				i += 2;
			} else {
				sb.append(c);
				i++;
			}
			if (this.font.width(sb + "...") > maxWidth) {
				while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '§') {
					sb.deleteCharAt(sb.length() - 1);
				}
				break;
			}
		}
		return sb + "...";
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (amount > 0) {
			scrollOffset = Math.max(0, scrollOffset - SCROLL_STEP);
		} else if (amount < 0) {
			scrollOffset = Math.min(maxScroll, scrollOffset + SCROLL_STEP);
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			for (PlayerRowBounds b : playerRowBounds) {
				if (mouseY >= b.y() && mouseY < b.y() + b.height()
						&& mouseX >= b.x() && mouseX < b.x() + b.width()) {
					if (EXPANDED_PLAYERS.contains(b.name())) {
						EXPANDED_PLAYERS.remove(b.name());
					} else {
						EXPANDED_PLAYERS.add(b.name());
					}
					return true;
				}
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
