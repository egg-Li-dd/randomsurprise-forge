package com.randomsurprise.client;

import com.randomsurprise.affix.Affix;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 背包界面左侧词条属性汇总面板
 * 一列紧凑布局，显示玩家从词条获得的所有属性加成汇总
 * - 优先放在背包 GUI 左侧；空间不足时放右侧；两侧都不够则不显示
 * - 只显示非零属性，避免空行
 * - 颜色区分：金色标题 / 绿色增益 / 青色免疫 / 元素色伤害减免 / 橙红敌对词条数
 * - 支持鼠标滚轮滚动查看更多属性
 */
public class AffixStatsPanel {
	private static final int PANEL_WIDTH = 100;
	private static final int LINE_HEIGHT = 10;
	private static final int PADDING = 4;
	private static final int GUI_HEIGHT = 166; // 生存背包 GUI 高度
	private static final int SCROLL_STEP = LINE_HEIGHT; // 每次滚动一行

	// 颜色
	private static final int TITLE_COLOR = 0xFFFFD700;     // 金色
	private static final int GOOD_COLOR = 0xFF55FF55;      // 绿色
	private static final int IMMUNE_COLOR = 0xFF55FFFF;    // 青色
	private static final int FIRE_COLOR = 0xFFFF7755;      // 橙红（火焰）
	private static final int FROST_COLOR = 0xFF88AAFF;     // 浅蓝（冰霜）
	private static final int LIGHTNING_COLOR = 0xFFFFEE55; // 黄色（闪电）
	private static final int BAD_COLOR = 0xFFFF5577;       // 红粉（敌对词条）
	private static final int DIVIDER_COLOR = 0xFF444466;   // 分隔线
	private static final int BG_COLOR = 0xCC1A1A2E;        // 深紫背景
	private static final int BORDER_COLOR = 0xFF6B6B8E;    // 紫灰边框
	private static final int EMPTY_COLOR = 0xFF888888;     // 灰色（无词条提示）
	private static final int SCROLLBAR_COLOR = 0xFF8888AA;  // 滚动条颜色
	private static final int SCROLLBAR_BG = 0xFF333355;     // 滚动条背景

	/** 滚动偏移量（像素） */
	private static int scrollOffset = 0;
	/** 当前面板位置（用于鼠标坐标判断） */
	private static int lastPanelX = 0;
	private static int lastPanelY = 0;
	private static int lastPanelW = 0;
	private static int lastPanelH = 0;
	private static boolean panelVisible = false;

	/** 1~8 转罗马数字 */
	private static String toRoman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			case 6 -> "VI";
			case 7 -> "VII";
			case 8 -> "VIII";
			default -> n <= 0 ? "0" : String.valueOf(n);
		};
	}

	/** 百分比格式化：0.15 -> "15" */
	private static String pct(double v) {
		return String.valueOf((int) Math.round(v * 100));
	}

	/**
	 * 在背包界面渲染词条属性面板
	 * 由 ClientRandomSurpriseMod 的 ScreenEvent.Render.Post 调用
	 */
	public static void render(GuiGraphics graphics, InventoryScreen screen) {
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;

		int guiLeft = screen.getGuiLeft();
		int guiTop = screen.getGuiTop();
		int screenWidth = screen.width;

		// 选择面板位置：优先左侧，空间不足时放右侧
		int panelX;
		boolean leftSide;
		if (guiLeft >= PANEL_WIDTH + 8) {
			panelX = guiLeft - PANEL_WIDTH - 4;
			leftSide = true;
		} else if (screenWidth - guiLeft - 176 >= PANEL_WIDTH + 8) {
			panelX = guiLeft + 176 + 4;
			leftSide = false;
		} else {
			// 两侧空间都不够，不显示
			panelVisible = false;
			return;
		}

		// 收集属性行
		List<StatLine> lines = collectStats();
		if (lines.isEmpty()) {
			panelVisible = false;
			return;
		}

		// 计算面板高度（不超过 GUI 高度）
		int contentHeight = lines.size() * LINE_HEIGHT + PADDING * 2;
		int panelHeight = Math.min(contentHeight, GUI_HEIGHT);
		int panelY = guiTop;

		// 计算最大滚动距离
		int maxScroll = Math.max(0, contentHeight - panelHeight);
		scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

		// 记录面板位置
		lastPanelX = panelX;
		lastPanelY = panelY;
		lastPanelW = PANEL_WIDTH;
		lastPanelH = panelHeight;
		panelVisible = true;

		// 绘制背景
		graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, BG_COLOR);
		// 边框
		graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 1, BORDER_COLOR);
		graphics.fill(panelX, panelY + panelHeight - 1, panelX + PANEL_WIDTH, panelY + panelHeight, BORDER_COLOR);
		graphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, BORDER_COLOR);
		graphics.fill(panelX + PANEL_WIDTH - 1, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, BORDER_COLOR);

		// 启用裁剪（超出面板的内容不显示）
		graphics.enableScissor(panelX + 1, panelY + 1, panelX + PANEL_WIDTH - 1, panelY + panelHeight - 1);

		// 绘制内容（根据滚动偏移调整起始位置）
		int y = panelY + PADDING - scrollOffset;
		for (StatLine line : lines) {
			// 跳过完全在面板上方的行
			if (y + LINE_HEIGHT < panelY + PADDING) {
				y += LINE_HEIGHT;
				continue;
			}
			// 超出面板下方则停止
			if (y > panelY + panelHeight - PADDING - LINE_HEIGHT) {
				// 如果有更多内容，显示省略号提示
				if (y <= panelY + panelHeight - PADDING) {
					graphics.drawString(font, "▼", panelX + PANEL_WIDTH / 2 - 2, y, SCROLLBAR_COLOR, false);
				}
				break;
			}
			if (line.divider) {
				// 分隔线
				graphics.fill(panelX + PADDING, y + LINE_HEIGHT / 2,
						panelX + PANEL_WIDTH - PADDING, y + LINE_HEIGHT / 2 + 1, DIVIDER_COLOR);
			} else {
				// 文本（带阴影）
				graphics.drawString(font, line.text, panelX + PADDING, y, line.color, false);
			}
			y += LINE_HEIGHT;
		}

		graphics.disableScissor();

		// 绘制滚动条（如果内容超出面板）
		if (maxScroll > 0) {
			int scrollbarX = panelX + PANEL_WIDTH - 3;
			int scrollbarTrackY = panelY + 2;
			int scrollbarTrackH = panelHeight - 4;
			// 滚动条背景
			graphics.fill(scrollbarX, scrollbarTrackY, scrollbarX + 2, scrollbarTrackY + scrollbarTrackH, SCROLLBAR_BG);
			// 滚动条滑块
			int thumbH = Math.max(10, scrollbarTrackH * panelHeight / contentHeight);
			int thumbY = scrollbarTrackY + (scrollbarTrackH - thumbH) * scrollOffset / maxScroll;
			graphics.fill(scrollbarX, thumbY, scrollbarX + 2, thumbY + thumbH, SCROLLBAR_COLOR);
		}
	}

	/**
	 * 处理鼠标滚轮事件
	 * 返回 true 表示已处理（拦截事件）
	 */
	public static boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (!panelVisible) return false;
		// 检查鼠标是否在面板区域内
		if (mouseX >= lastPanelX && mouseX <= lastPanelX + lastPanelW
				&& mouseY >= lastPanelY && mouseY <= lastPanelY + lastPanelH) {
			if (delta > 0) {
				// 向上滚动
				scrollOffset = Math.max(0, scrollOffset - SCROLL_STEP * 2);
			} else {
				// 向下滚动
				scrollOffset += SCROLL_STEP * 2;
			}
			return true;
		}
		return false;
	}

	/** 收集所有需要显示的属性行 */
	private static List<StatLine> collectStats() {
		List<Affix> affixes = ClientAffixData.getOwnAffixes();
		List<StatLine> lines = new ArrayList<>();

		// 标题
		lines.add(new StatLine(
				Component.translatable("affix_panel.randomsurprise.title").getString(), TITLE_COLOR, false));

		if (affixes.isEmpty()) {
			lines.add(new StatLine(
					Component.translatable("affix_panel.randomsurprise.none").getString(), EMPTY_COLOR, false));
			return lines;
		}

		// 汇总好词条属性
		int healthBonus = 0;
		int speedLevel = 0;
		int strengthLevel = 0;
		int resistanceLevel = 0;
		int expBonus = 0;
		int hasteLevel = 0;
		int regenLevel = 0;
		double bossDamage = 0;
		double lifesteal = 0;
		int absorption = 0;
		int weaponDamage = 0;
		double lightningDmg = 0;
		double fireDmg = 0;
		double frostDmg = 0;
		double fireResist = 0;
		double frostResist = 0;
		double lightningResist = 0;
		double weaponDmgPct = 0;
		boolean fireImmune = false, drownImmune = false, fallImmune = false, frostImmune = false;
		int badCount = 0;
		// v7 新增：独立属性加成
		double miningSpeed = 0;
		double attackSpeed = 0;
		double moveSpeed = 0;
		double critChance = 0;
		double healBonus = 0;
		// v9 新增：失明免疫率
		double blindnessResist = 0;
		// v11 新增：元素百分比伤害
		double lightningPctDmg = 0;
		double frostFreeze = 0;
		double firePctPerSec = 0;
		// v10 新增
		double moveSpeedPct = 0;
		double baseAtkDmg = 0;
		double hpPerSec = 0;

		for (Affix a : affixes) {
			if (a.isGood()) {
				healthBonus += a.getHealthBonus();
				speedLevel = Math.max(speedLevel, a.getSpeedAmplifier());
				strengthLevel = Math.max(strengthLevel, a.getStrengthAmplifier());
				resistanceLevel += a.getResistanceAmplifier();
				expBonus += a.getExpBonusPercent();
				hasteLevel = Math.max(hasteLevel, a.getHasteAmplifier());
				regenLevel = Math.max(regenLevel, a.getRegenAmplifier());
				bossDamage = Math.max(bossDamage, a.getBossDamageBonus());
				lifesteal += a.getLifestealPercent();
				absorption += a.getAbsorptionBonus();
				weaponDamage += a.getWeaponDamageBonus();
				lightningDmg += a.getLightningDamage();
				fireDmg += a.getFireDamage();
				frostDmg += a.getFrostDamage();
				fireResist += a.getFireResistancePercent();
				frostResist += a.getFrostResistancePercent();
				lightningResist += a.getLightningResistancePercent();
				weaponDmgPct += a.getWeaponDamagePercent();
				if (a.isFireImmunity()) fireImmune = true;
				if (a.isDrownImmunity()) drownImmune = true;
				if (a.isFallImmunity()) fallImmune = true;
				if (a.isFrostImmunity()) frostImmune = true;
				// v7 新增属性（取最大值）
				miningSpeed = Math.max(miningSpeed, a.getMiningSpeedBonus());
				attackSpeed = Math.max(attackSpeed, a.getAttackSpeedBonus());
				moveSpeed = Math.max(moveSpeed, a.getMoveSpeedBonus());
				critChance = Math.max(critChance, a.getCritChance());
				healBonus = Math.max(healBonus, a.getHealBonus());
				// v9 新增：失明免疫率（取最大值）
				blindnessResist = Math.max(blindnessResist, a.getBlindnessResistancePercent());
				// v11 新增：元素百分比伤害（取最大值）
				lightningPctDmg = Math.max(lightningPctDmg, a.getLightningPercentDamage());
				frostFreeze = Math.max(frostFreeze, a.getFrostFreezeChance());
				firePctPerSec = Math.max(firePctPerSec, a.getFirePercentDamagePerSecond());
				// v10 新增
				moveSpeedPct = Math.max(moveSpeedPct, a.getMoveSpeedPercent());
				baseAtkDmg = Math.max(baseAtkDmg, a.getBaseAttackDamage());
				hpPerSec = Math.max(hpPerSec, a.getHpPerSecond());
			} else {
				badCount++;
			}
		}

		// 伤害免疫率上限 90%（与 PlayerAffixManager.getTotalDamageImmunity 一致）
		int immunityPercent = Math.min(90, resistanceLevel * 10);

		// 分隔线（标题下方）
		lines.add(StatLine.divider());

		// 基础属性（绿色）
		if (healthBonus > 0)
			lines.add(new StatLine("生命 +" + healthBonus, GOOD_COLOR));
		if (speedLevel > 0)
			lines.add(new StatLine("速度 " + toRoman(speedLevel), GOOD_COLOR));
		if (strengthLevel > 0)
			lines.add(new StatLine("力量 " + toRoman(strengthLevel), GOOD_COLOR));
		if (immunityPercent > 0)
			lines.add(new StatLine("免疫 " + immunityPercent + "%", GOOD_COLOR));
		if (hasteLevel > 0)
			lines.add(new StatLine("急迫 " + toRoman(hasteLevel), GOOD_COLOR));
		if (regenLevel > 0)
			lines.add(new StatLine("再生 " + toRoman(regenLevel), GOOD_COLOR));
		if (absorption > 0)
			lines.add(new StatLine("护盾 +" + absorption, GOOD_COLOR));
		if (expBonus > 0)
			lines.add(new StatLine("经验 +" + expBonus + "%", GOOD_COLOR));

		// 战斗属性
		if (bossDamage > 0)
			lines.add(new StatLine("Boss伤 +" + pct(bossDamage) + "%", GOOD_COLOR));
		if (lifesteal > 0)
			lines.add(new StatLine("吸血 " + pct(lifesteal) + "%", GOOD_COLOR));
		if (weaponDamage > 0)
			lines.add(new StatLine("武器伤 +" + weaponDamage, GOOD_COLOR));
		if (weaponDmgPct > 0)
			lines.add(new StatLine("武器 +" + pct(weaponDmgPct) + "%", GOOD_COLOR));
		if (baseAtkDmg > 0)
			lines.add(new StatLine("基础攻击 +" + (int)baseAtkDmg, GOOD_COLOR));

		// v7 新增：独立属性加成
		if (miningSpeed > 0)
			lines.add(new StatLine("挖掘 +" + pct(miningSpeed) + "%", GOOD_COLOR));
		if (attackSpeed > 0)
			lines.add(new StatLine("攻速 +" + pct(attackSpeed) + "%", GOOD_COLOR));
		if (moveSpeed > 0)
			lines.add(new StatLine("移速 +" + pct(moveSpeed) + "%", GOOD_COLOR));
		if (moveSpeedPct > 0)
			lines.add(new StatLine("移速% +" + pct(moveSpeedPct) + "%", GOOD_COLOR));
		if (critChance > 0)
			lines.add(new StatLine("暴击 " + pct(critChance) + "%", GOOD_COLOR));
		if (healBonus > 0)
			lines.add(new StatLine("治疗 +" + pct(healBonus) + "%", GOOD_COLOR));
		if (hpPerSec > 0)
			lines.add(new StatLine("回血/秒 +" + (int)hpPerSec, GOOD_COLOR));

		// v9 新增：失明免疫率
		if (blindnessResist > 0)
			lines.add(new StatLine("失明免疫 " + pct(blindnessResist) + "%", GOOD_COLOR));

		// 元素伤害
		if (lightningDmg > 0)
			lines.add(new StatLine("闪电 +" + lightningDmg, LIGHTNING_COLOR));
		if (fireDmg > 0)
			lines.add(new StatLine("火焰 +" + fireDmg, FIRE_COLOR));
		if (frostDmg > 0)
			lines.add(new StatLine("冰霜 +" + frostDmg, FROST_COLOR));

		// v11 新增：元素百分比伤害
		if (lightningPctDmg > 0)
			lines.add(new StatLine("雷maxHP " + pct(lightningPctDmg) + "%", LIGHTNING_COLOR));
		if (frostFreeze > 0)
			lines.add(new StatLine("冰冻概率 " + pct(frostFreeze) + "%", FROST_COLOR));
		if (firePctPerSec > 0)
			lines.add(new StatLine("火/秒 " + pct(firePctPerSec) + "%", FIRE_COLOR));

		// 元素减免
		if (fireResist > 0)
			lines.add(new StatLine("火减 " + pct(fireResist) + "%", FIRE_COLOR));
		if (frostResist > 0)
			lines.add(new StatLine("冰减 " + pct(frostResist) + "%", FROST_COLOR));
		if (lightningResist > 0)
			lines.add(new StatLine("雷减 " + pct(lightningResist) + "%", LIGHTNING_COLOR));

		// 免疫列表（青色）
		List<String> immunes = new ArrayList<>();
		if (fireImmune) immunes.add("火");
		if (drownImmune) immunes.add("水");
		if (fallImmune) immunes.add("摔");
		if (frostImmune) immunes.add("冰");
		if (!immunes.isEmpty()) {
			lines.add(new StatLine("免疫:" + String.join("", immunes), IMMUNE_COLOR));
		}

		// 坏词条数量（橙红色）
		if (badCount > 0) {
			lines.add(StatLine.divider());
			lines.add(new StatLine("敌对词条 " + badCount, BAD_COLOR));
		}

		return lines;
	}

	/** 一行显示数据 */
	private static class StatLine {
		final String text;
		final int color;
		final boolean divider;

		StatLine(String text, int color) {
			this.text = text;
			this.color = color;
			this.divider = false;
		}

		StatLine(String text, int color, boolean divider) {
			this.text = text;
			this.color = color;
			this.divider = divider;
		}

		static StatLine divider() {
			return new StatLine("", 0, true);
		}
	}
}
