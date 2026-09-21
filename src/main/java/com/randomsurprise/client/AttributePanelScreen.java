package com.randomsurprise.client;

import com.randomsurprise.affix.Affix;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 属性面板界面（P 键打开）
 * 显示玩家所有战斗属性：
 * - 左列：属性名称
 * - 右列：属性值（基础值 + 词条加成 = 总计）
 * 半透明深色背景，金色边框，ESC 关闭
 */
public class AttributePanelScreen extends Screen {

    private static final int LINE_HEIGHT = 18;
    private static final int PADDING = 12;

    public AttributePanelScreen() {
        super(Component.translatable("attribute_panel.randomsurprise.title"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 全屏半透明遮罩
        graphics.fill(0, 0, this.width, this.height, 0xAA000000);

        // 面板尺寸与居中定位
        int panelWidth = Math.min(360, this.width - 40);
        int panelHeight = Math.min(520, this.height - 40);
        int x = (this.width - panelWidth) / 2;
        int y = (this.height - panelHeight) / 2;
        int centerX = x + panelWidth / 2;

        // 面板背景
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xCC1A1A2E);

        // 金色边框（2px 粗）
        int borderColor = 0xFFAA6600;
        graphics.fill(x, y, x + panelWidth, y + 2, borderColor);
        graphics.fill(x, y, x + 2, y + panelHeight, borderColor);
        graphics.fill(x + panelWidth - 2, y, x + panelWidth, y + panelHeight, borderColor);
        graphics.fill(x, y + panelHeight - 2, x + panelWidth, y + panelHeight, borderColor);

        // 标题
        graphics.drawCenteredString(this.font, "\u00A76\u00A7l\u5C5E \u6027 \u9762 \u677F", centerX, y + 8, 0xFFFFFF);

        // 分隔线
        int sepY = y + 26;
        graphics.fill(x + 8, sepY, x + panelWidth - 8, sepY + 1, 0xFF6B6B8E);

        Player player = Minecraft.getInstance().player;
        if (player != null) {
            int startY = y + 32;

            // ========== 属性列表 ==========
            List<StatEntry> stats = new ArrayList<>();

            // --- 原版属性（直接从玩家读取，已包含词条加成） ---
            double maxHealth = player.getMaxHealth();
            double attackDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
            double moveSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
            double armor = player.getAttributeValue(Attributes.ARMOR);
            double knockbackRes = player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);

            stats.add(new StatEntry("\u00A7c\u2665 \u751F\u547D\u503C",
                    String.format("%.1f HP", maxHealth), 0xFFAA0000));
            stats.add(new StatEntry("\u00A7e\u2694 \u653B\u51FB\u4F24\u5BB3",
                    String.format("%.1f", attackDamage), 0xFFFFAA00));
            stats.add(new StatEntry("\u00A7a\u26A1 \u653B\u51FB\u901F\u5EA6",
                    String.format("%.2f", attackSpeed), 0xFF55FF55));
            stats.add(new StatEntry("\u00A7b\u2501 \u79FB\u52A8\u901F\u5EA6",
                    String.format("%.2f", moveSpeed), 0xFF55FFFF));
            stats.add(new StatEntry("\u00A77\u25C6 \u62A4\u7532\u503C",
                    String.format("%.0f", armor), 0xFFAAAAAA));
            stats.add(new StatEntry("\u00A7d\u270A \u51FB\u9000\u6297\u6027",
                    String.format("%.0f%%", knockbackRes * 100), 0xFFFF55FF));

            // --- 词条专属属性（从 ClientAffixData 缓存的 Affix 对象累加） ---
            double totalCritChance = 0;
            double totalLifesteal = 0;
            double totalExpBonus = 0;
            double totalFireDamage = 0;
            double totalFrostDamage = 0;
            double totalLightningDamage = 0;
            double totalBossDmgBonus = 0;
            double totalHealBonus = 0;
            double totalLightningPercentDmg = 0;
            double totalFirePercentDmg = 0;
            int totalAntiHealOnHit = 0;
            // v18: 新增缺失属性
            double totalFireResist = 0;
            double totalFrostResist = 0;
            double totalLightningResist = 0;
            double totalBlindnessResist = 0;
            double totalMiningSpeed = 0;
            double totalAttackSpeedBonus = 0;
            double totalHpPerSecond = 0;
            double totalNaturalRegen = 0;
            double totalMoveSpeedPercent = 0;
            double totalBaseAttackDamage = 0;
            double totalFrostFreezeChance = 0;
            double totalFireBurnStack = 0;
            double totalFireChanceOnAttack = 0;
            double totalFrostChanceOnAttack = 0;
            boolean hasFireImmunity = false;
            boolean hasFrostImmunity = false;
            boolean hasFallImmunity = false;
            boolean hasDrownImmunity = false;
            int totalAbsorptionBonus = 0;
            int totalHasteAmplifier = 0;
            int totalBerserkerThreshold = 0;
            double totalBerserkerDmgBonus = 0;

            for (Affix affix : ClientAffixData.getOwnGoodAffixes()) {
                totalCritChance += affix.getCritChance();
                totalLifesteal += affix.getLifestealPercent();
                totalExpBonus += affix.getExpBonusPercent();
                totalFireDamage += affix.getFireDamage();
                totalFrostDamage += affix.getFrostDamage();
                totalLightningDamage += affix.getLightningDamage();
                totalBossDmgBonus += affix.getBossDamageBonus();
                totalHealBonus += affix.getHealBonus();
                totalLightningPercentDmg += affix.getLightningPercentDamage();
                totalFirePercentDmg += affix.getFirePercentDamagePerSecond();
                totalAntiHealOnHit += affix.getAntiHealOnHitPercent();
                // v18 新增
                totalFireResist += affix.getFireResistancePercent();
                totalFrostResist += affix.getFrostResistancePercent();
                totalLightningResist += affix.getLightningResistancePercent();
                totalBlindnessResist += affix.getBlindnessResistancePercent();
                totalMiningSpeed += affix.getMiningSpeedBonus();
                totalAttackSpeedBonus += affix.getAttackSpeedBonus();
                totalHpPerSecond += affix.getHpPerSecond();
                totalNaturalRegen += affix.getNaturalRegenBonus();
                totalMoveSpeedPercent += affix.getMoveSpeedPercent();
                totalBaseAttackDamage += affix.getBaseAttackDamage();
                totalFrostFreezeChance += affix.getFrostFreezeChance();
                totalFireBurnStack += affix.getFireBurnStackBonus();
                totalFireChanceOnAttack += affix.getFireChanceOnAttack();
                totalFrostChanceOnAttack += affix.getFrostChanceOnAttack();
                hasFireImmunity |= affix.isFireImmunity();
                hasFrostImmunity |= affix.isFrostImmunity();
                hasFallImmunity |= affix.isFallImmunity();
                hasDrownImmunity |= affix.isDrownImmunity();
                totalAbsorptionBonus += affix.getAbsorptionBonus();
                totalHasteAmplifier = Math.max(totalHasteAmplifier, affix.getHasteAmplifier());
                totalBerserkerThreshold = Math.max(totalBerserkerThreshold, affix.getBerserkerThreshold());
                totalBerserkerDmgBonus += affix.getBerserkerDamageBonus();
            }

            // --- 输出属性 ---
            // 原版属性
            stats.add(new StatEntry("\u00A7c\u2665 \u751F\u547D\u503C",
                    String.format("%.1f HP", maxHealth), 0xFFAA0000));
            stats.add(new StatEntry("\u00A7e\u2694 \u653B\u51FB\u4F24\u5BB3",
                    String.format("%.1f", attackDamage), 0xFFFFAA00));
            stats.add(new StatEntry("\u00A7a\u26A1 \u653B\u51FB\u901F\u5EA6",
                    String.format("%.2f", attackSpeed), 0xFF55FF55));
            stats.add(new StatEntry("\u00A7b\u2501 \u79FB\u52A8\u901F\u5EA6",
                    String.format("%.2f", moveSpeed), 0xFF55FFFF));
            stats.add(new StatEntry("\u00A77\u25C6 \u62A4\u7532\u503C",
                    String.format("%.0f", armor), 0xFFAAAAAA));
            stats.add(new StatEntry("\u00A7d\u270A \u51FB\u9000\u6297\u6027",
                    String.format("%.0f%%", knockbackRes * 100), 0xFFFF55FF));

            // 词条属性 - 伤害类
            if (totalBaseAttackDamage > 0) {
                stats.add(new StatEntry("\u00A7e\u2726 \u57FA\u7840\u653B\u51FB\u529B",
                        String.format("+%.1f", totalBaseAttackDamage), 0xFFFFFF55));
            }
            if (totalCritChance > 0) {
                stats.add(new StatEntry("\u00A76\u2022 \u66B4\u51FB\u7387",
                        String.format("+%.1f%%", totalCritChance * 100), 0xFFFFAA00));
            }
            if (totalLifesteal > 0) {
                stats.add(new StatEntry("\u00A74\u2022 \u5438\u8840\u7387",
                        String.format("+%.1f%%", totalLifesteal * 100), 0xFFFF5555));
            }
            if (totalBossDmgBonus > 0) {
                stats.add(new StatEntry("\u00A75\u2022 Boss\u4F24\u5BB3\u52A0\u6210",
                        String.format("+%.0f%%", totalBossDmgBonus * 100), 0xFFFF55FF));
            }
            if (totalBerserkerDmgBonus > 0) {
                stats.add(new StatEntry("\u00A7c\u2022 \u72C2\u6218\u58EB",
                        String.format("+%.0f%% (\u8840<%d%%)", totalBerserkerDmgBonus * 100, totalBerserkerThreshold), 0xFFFF5555));
            }

            // 词条属性 - 元素伤害
            if (totalFireDamage > 0) {
                stats.add(new StatEntry("\u00A76\u2022 \u706B\u7130\u4F24\u5BB3",
                        String.format("+%.1f", totalFireDamage), 0xFFFFAA00));
            }
            if (totalFrostDamage > 0) {
                stats.add(new StatEntry("\u00A7b\u2022 \u51B0\u971C\u4F24\u5BB3",
                        String.format("+%.1f", totalFrostDamage), 0xFF55FFFF));
            }
            if (totalLightningDamage > 0) {
                stats.add(new StatEntry("\u00A7e\u2022 \u95EA\u7535\u4F24\u5BB3",
                        String.format("+%.1f", totalLightningDamage), 0xFFFFFF55));
            }
            if (totalFireChanceOnAttack > 0) {
                stats.add(new StatEntry("\u00A76\u2022 \u70B9\u71C3\u6982\u7387",
                        String.format("+%.0f%%", totalFireChanceOnAttack * 100), 0xFFFFAA00));
            }
            if (totalFrostChanceOnAttack > 0) {
                stats.add(new StatEntry("\u00A7b\u2022 \u51CF\u901F\u6982\u7387",
                        String.format("+%.0f%%", totalFrostChanceOnAttack * 100), 0xFF55FFFF));
            }
            if (totalFireBurnStack > 0) {
                stats.add(new StatEntry("\u00A76\u2022 \u71C3\u70E7\u53E0\u52A0",
                        String.format("+%.1f/\u6B21", totalFireBurnStack), 0xFFFFAA00));
            }
            if (totalLightningPercentDmg > 0) {
                stats.add(new StatEntry("\u00A7e\u2022 \u95EA\u7535\u767E\u5206\u6BD4",
                        String.format("+%.1f%%\u6700\u5927\u751F\u547D", totalLightningPercentDmg * 100), 0xFFFFFF55));
            }
            if (totalFirePercentDmg > 0) {
                stats.add(new StatEntry("\u00A76\u2022 \u706B\u7130\u6BCF\u79D2\u767E\u5206\u6BD4",
                        String.format("+%.1f%%\u6700\u5927\u751F\u547D", totalFirePercentDmg * 100), 0xFFFFAA00));
            }
            if (totalFrostFreezeChance > 0) {
                stats.add(new StatEntry("\u00A7b\u2022 \u51BB\u7ED3\u6982\u7387",
                        String.format("+%.0f%%", totalFrostFreezeChance * 100), 0xFF55FFFF));
            }

            // 词条属性 - 元素减伤
            if (totalFireResist > 0) {
                stats.add(new StatEntry("\u00A76\u25CE \u706B\u7130\u51CF\u4F24",
                        String.format("+%.0f%%", totalFireResist * 100), 0xFFFFAA00));
            }
            if (totalFrostResist > 0) {
                stats.add(new StatEntry("\u00A7b\u25CE \u51B0\u971C\u51CF\u4F24",
                        String.format("+%.0f%%", totalFrostResist * 100), 0xFF55FFFF));
            }
            if (totalLightningResist > 0) {
                stats.add(new StatEntry("\u00A7e\u25CE \u95EA\u7535\u51CF\u4F24",
                        String.format("+%.0f%%", totalLightningResist * 100), 0xFFFFFF55));
            }

            // 词条属性 - 免疫
            if (hasFireImmunity) {
                stats.add(new StatEntry("\u00A76\u2714 \u706B\u7130\u514D\u75AB",
                        "\u2714", 0xFFFFAA00));
            }
            if (hasFrostImmunity) {
                stats.add(new StatEntry("\u00A7b\u2714 \u51B0\u971C\u514D\u75AB",
                        "\u2714", 0xFF55FFFF));
            }
            if (hasFallImmunity) {
                stats.add(new StatEntry("\u00A7a\u2714 \u6454\u843D\u514D\u75AB",
                        "\u2714", 0xFF55FF55));
            }
            if (hasDrownImmunity) {
                stats.add(new StatEntry("\u00A79\u2714 \u6EBA\u6C34\u514D\u75AB",
                        "\u2714", 0xFF5555FF));
            }
            if (totalBlindnessResist > 0) {
                stats.add(new StatEntry("\u00A78\u2714 \u5931\u660E\u6297\u6027",
                        String.format("+%.0f%%", totalBlindnessResist * 100), 0xFF888888));
            }

            // 词条属性 - 生存
            if (totalHpPerSecond > 0) {
                stats.add(new StatEntry("\u00A7a\u2022 \u6BCF\u79D2\u56DE\u8840",
                        String.format("+%.1f HP/s", totalHpPerSecond), 0xFF55FF55));
            }
            if (totalAbsorptionBonus > 0) {
                stats.add(new StatEntry("\u00A7e\u2022 \u5438\u6536\u62A4\u76FE",
                        String.format("+%d", totalAbsorptionBonus), 0xFFFFFF55));
            }
            if (totalHealBonus > 0) {
                stats.add(new StatEntry("\u00A7a\u2022 \u6CBB\u7597\u52A0\u6210",
                        String.format("+%.0f%%", totalHealBonus * 100), 0xFF55FF55));
            }
            if (totalNaturalRegen > 0) {
                stats.add(new StatEntry("\u00A7a\u2022 \u81EA\u7136\u56DE\u8840",
                        String.format("+%.0f%%", totalNaturalRegen * 100), 0xFF55FF55));
            }
            if (totalAntiHealOnHit > 0) {
                stats.add(new StatEntry("\u00A78\u2022 \u653B\u51FB\u6291\u5236\u56DE\u8840",
                        String.format("%d%%", totalAntiHealOnHit), 0xFF888888));
            }

            // 词条属性 - 速度与挖掘
            if (totalMoveSpeedPercent > 0) {
                stats.add(new StatEntry("\u00A7b\u2022 \u79FB\u901F\u52A0\u6210",
                        String.format("+%.0f%%", totalMoveSpeedPercent * 100), 0xFF55FFFF));
            }
            if (totalHasteAmplifier > 0) {
                stats.add(new StatEntry("\u00A7e\u2022 \u6025\u8FEB\u7B49\u7EA7",
                        String.format("+%d", totalHasteAmplifier), 0xFFFFFF55));
            }
            if (totalMiningSpeed > 0) {
                stats.add(new StatEntry("\u00A7e\u2022 \u6316\u6398\u901F\u5EA6",
                        String.format("+%.0f%%", totalMiningSpeed * 100), 0xFFFFFF55));
            }
            if (totalAttackSpeedBonus > 0) {
                stats.add(new StatEntry("\u00A7a\u2022 \u653B\u901F\u52A0\u6210",
                        String.format("+%.0f%%", totalAttackSpeedBonus * 100), 0xFF55FF55));
            }
            if (totalExpBonus > 0) {
                stats.add(new StatEntry("\u00A7a\u2022 \u7ECF\u9A8C\u52A0\u6210",
                        String.format("+%d%%", (int) totalExpBonus), 0xFF55FF55));
            }

            // --- 渲染属性行 ---
            for (int i = 0; i < stats.size(); i++) {
                StatEntry entry = stats.get(i);
                int ly = startY + i * LINE_HEIGHT;
                // 超出面板底部则停止渲染
                if (ly > y + panelHeight - 30) break;

                // 属性名称（左对齐）
                graphics.drawString(this.font, entry.name, x + PADDING, ly, 0xFFFFFF);
                // 属性值（右对齐）
                int valueWidth = this.font.width(entry.value);
                graphics.drawString(this.font, entry.value,
                        x + panelWidth - PADDING - valueWidth, ly, entry.color);
            }

            // 词条数量统计
            int affixCount = ClientAffixData.getOwnGoodAffixes().size();
            int statEndY = startY + stats.size() * LINE_HEIGHT + 4;
            if (statEndY < y + panelHeight - 30) {
                graphics.drawCenteredString(this.font,
                        "\u00A78\u5F53\u524D\u6FC0\u6D3B\u597D\u8BCD\u6761: " + affixCount + " \u4E2A",
                        centerX, statEndY, 0xFFFFFF);
            }
        }

        // 底部关闭提示
        graphics.drawCenteredString(this.font, "\u00A78\u6309 ESC \u5173\u95ED",
                centerX, y + panelHeight - 16, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 属性条目：名称、格式化值、显示颜色 */
    private record StatEntry(String name, String value, int color) {}
}
