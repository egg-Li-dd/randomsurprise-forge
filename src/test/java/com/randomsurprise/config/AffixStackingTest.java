package com.randomsurprise.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 词条叠加与属性封顶测试
 * 验证坏词条叠加为加法（非指数），属性有封顶
 */
class AffixStackingTest {

    // ========== 坏词条叠加测试 ==========

    @Test
    @DisplayName("单玩家坏词条血量倍率正常生效")
    void singlePlayerHostileHealth() {
        double[] multipliers = {1.2};
        double result = BalanceMath.calculateCappedHostileHealth(multipliers);
        assertEquals(1.2, result, 0.001, "单玩家 1.2x 应正常生效");
    }

    @Test
    @DisplayName("10个玩家坏词条血量叠加封顶2.0x（非指数6.19x）")
    void tenPlayersHostileHealthCapped() {
        double[] multipliers = {1.2, 1.2, 1.2, 1.2, 1.2, 1.2, 1.2, 1.2, 1.2, 1.2};
        double result = BalanceMath.calculateCappedHostileHealth(multipliers);
        assertTrue(result <= BalanceConfig.MAX_HOSTILE_HEALTH_MULT,
            "10人叠加超限: " + result + " > " + BalanceConfig.MAX_HOSTILE_HEALTH_MULT);
        assertTrue(result > 1.0, "叠加结果应 > 1.0");
    }

    @Test
    @DisplayName("坏词条伤害叠加封顶1.5x")
    void hostileDamageCapped() {
        double[] multipliers = {1.3, 1.3, 1.3, 1.3, 1.3};
        double result = BalanceMath.calculateCappedHostileDamage(multipliers);
        assertTrue(result <= BalanceConfig.MAX_HOSTILE_DAMAGE_MULT,
            "伤害叠加超限: " + result);
    }

    @Test
    @DisplayName("坏词条叠加为加法而非乘法: 2人1.2x应=1.4x非1.44x")
    void additiveNotMultiplicative() {
        double[] multipliers = {1.2, 1.2};
        double result = BalanceMath.calculateCappedHostileHealth(multipliers);
        // 加法: 1.0 + 0.2 + 0.2 = 1.4
        // 乘法: 1.2 * 1.2 = 1.44
        assertEquals(1.4, result, 0.001, "叠加应为加法(1.4)非乘法(1.44)");
    }

    // ========== 属性封顶测试 ==========

    @Test
    @DisplayName("生命加成封顶180")
    void healthBonusCapped() {
        assertEquals(20, BalanceMath.capHealthBonus(20), "20 < 180 应不变");
        assertEquals(180, BalanceMath.capHealthBonus(180), "180 = 上限 应不变");
        assertEquals(180, BalanceMath.capHealthBonus(300), "300 > 180 应封顶为180");
    }

    @Test
    @DisplayName("吸血封顶300%")
    void lifestealCapped() {
        assertEquals(0.30, BalanceMath.capLifesteal(0.30), 0.001, "30% < 300% 应不变");
        assertEquals(3.0, BalanceMath.capLifesteal(5.0), 0.001, "500% > 300% 应封顶为300%");
    }

    @Test
    @DisplayName("每秒回血封顶30.0")
    void hpPerSecondCapped() {
        assertEquals(3.0, BalanceMath.capHpPerSecond(3.0), 0.001, "3.0 < 30.0 应不变");
        assertEquals(30.0, BalanceMath.capHpPerSecond(50.0), 0.001, "50.0 > 30.0 应封顶为30.0");
    }

    @Test
    @DisplayName("移速百分比封顶0.07")
    void moveSpeedPercentCapped() {
        assertEquals(0.04, BalanceMath.capMoveSpeedPercent(0.04), 0.001);
        assertEquals(0.07, BalanceMath.capMoveSpeedPercent(1.0), 0.001);
    }

    @Test
    @DisplayName("基础攻击力封顶90.0")
    void baseAttackDamageCapped() {
        assertEquals(10.0, BalanceMath.capBaseAttackDamage(10.0), 0.001);
        assertEquals(90.0, BalanceMath.capBaseAttackDamage(120.0), 0.001);
    }

    // ========== Boss HP 缩放测试 ==========

    @Test
    @DisplayName("单人 Boss HP 无额外缩放")
    void soloBossHpNoScale() {
        double scale = BalanceMath.calculateBossPlayerScale(1);
        assertEquals(1.0, scale, 0.001, "单人应无缩放");
    }

    @Test
    @DisplayName("4人 Boss HP 缩放1.6x")
    void quadBossHpScale() {
        double scale = BalanceMath.calculateBossPlayerScale(4);
        // 1.0 + 3 * 0.2 = 1.6
        assertEquals(1.6, scale, 0.001, "4人应缩放1.6x");
    }

    @Test
    @DisplayName("10人 Boss HP 封顶2.0x")
    void tenPlayersBossHpCapped() {
        double scale = BalanceMath.calculateBossPlayerScale(10);
        assertTrue(scale <= BalanceConfig.BOSS_HP_PLAYER_SCALE_MAX,
            "10人缩放超限: " + scale);
    }

    @Test
    @DisplayName("Boss 总HP倍率 = 难度倍率 * 玩家数缩放")
    void bossTotalHpMultiplier() {
        // 难度5: 1 + 5*0.25 = 2.25, 玩家4人: 1.6x, 总: 3.6x
        double mult = BalanceMath.calculateBossHpMultiplier(5, 4);
        assertEquals(3.6, mult, 0.01, "难度5+4人应=3.6x");
        // 单人应 < 多人
        double solo = BalanceMath.calculateBossHpMultiplier(5, 1);
        assertTrue(mult > solo, "多人HP应 > 单人HP");
    }
}
