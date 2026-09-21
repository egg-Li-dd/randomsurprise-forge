package com.randomsurprise.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * v19: 征召战场结算画面
 * 显示征召成功/失败、难度、奖励词条稀有度
 */
public class BattlefieldResultScreen extends Screen {
    private final boolean success;
    private final String rarityName;
    private final int difficulty;
    private final int totalBattles;
    private int displayTicks = 0;

    public BattlefieldResultScreen(boolean success, String rarityName, int difficulty, int totalBattles) {
        super(Component.literal(""));
        this.success = success;
        this.rarityName = rarityName;
        this.difficulty = difficulty;
        this.totalBattles = totalBattles;
    }

    @Override
    protected void init() {
        super.init();
        // 关闭按钮
        this.addRenderableWidget(Button.builder(
                Component.translatable("battlefield.randomsurprise.result_close"),
                button -> this.onClose()
        ).bounds(this.width / 2 - 60, this.height / 2 + 60, 120, 20).build());
    }

    @Override
    public void tick() {
        super.tick();
        displayTicks++;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 半透明背景
        graphics.fill(0, 0, this.width, this.height, 0xC0202020);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 主标题
        String titleKey = success ? "battlefield.randomsurprise.victory_title" : "battlefield.randomsurprise.defeat_title";
        int titleColor = success ? 0xFF55FF55 : 0xFFFF5555;
        Component title = Component.translatable(titleKey);
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title, centerX - titleWidth / 2, centerY - 50, titleColor, true);

        // 副标题：难度信息
        Component subtitle = Component.translatable("battlefield.randomsurprise.result_difficulty", difficulty);
        int subWidth = this.font.width(subtitle);
        graphics.drawString(this.font, subtitle, centerX - subWidth / 2, centerY - 30, 0xFFAAAAAA, false);

        // 奖励信息
        if (success) {
            Component reward = Component.translatable("battlefield.randomsurprise.result_reward", rarityName);
            int rewardWidth = this.font.width(reward);
            graphics.drawString(this.font, reward, centerX - rewardWidth / 2, centerY - 10, 0xFFFFFF55, false);
        } else {
            Component penalty = Component.translatable("battlefield.randomsurprise.result_penalty", rarityName);
            int penaltyWidth = this.font.width(penalty);
            graphics.drawString(this.font, penalty, centerX - penaltyWidth / 2, centerY - 10, 0xFFFFAA55, false);
        }

        // 总场次
        Component totalInfo = Component.translatable("battlefield.randomsurprise.result_total", totalBattles);
        int totalWidth = this.font.width(totalInfo);
        graphics.drawString(this.font, totalInfo, centerX - totalWidth / 2, centerY + 15, 0xFF888888, false);

        // 装饰线
        int lineY = centerY + 30;
        graphics.fill(centerX - 80, lineY, centerX + 80, lineY + 1, 0xFF444444);

        // 提示文字（淡入效果）
        if (displayTicks > 20) {
            String hintKey = success ? "battlefield.randomsurprise.result_victory_hint" : "battlefield.randomsurprise.result_defeat_hint";
            Component hint = Component.translatable(hintKey);
            int hintWidth = this.font.width(hint);
            int alpha = Math.min(255, (displayTicks - 20) * 8);
            graphics.drawString(this.font, hint, centerX - hintWidth / 2, centerY + 40, 0xFF666666, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
    }
}
