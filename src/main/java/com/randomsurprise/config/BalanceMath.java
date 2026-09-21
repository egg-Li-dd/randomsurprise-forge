package com.randomsurprise.config;

/**
 * 平衡性计算工具
 * 纯计算逻辑，不依赖 Minecraft 运行时，可独立单元测试
 */
public final class BalanceMath {

    private BalanceMath() {}

    // ========== 经济套利检测 ==========

    /**
     * 计算资源兑换的等价金币单价（每单位资源 = 多少金币）
     * @param coinAmount 金币数量
     * @param resourceAmount 资源数量
     * @return 每单位资源对应的金币价值
     */
    public static double coinPerResource(int coinAmount, int resourceAmount) {
        if (resourceAmount <= 0) return Double.MAX_VALUE;
        return (double) coinAmount / resourceAmount;
    }

    /**
     * 检查资源兑换对是否存在套利
     * 套利条件: 买入单价 < 卖出单价（用金币买资源比用资源换金币更便宜）
     * @param buyCoinCost 用金币买资源时消耗的金币
     * @param buyResourceAmount 用金币买资源时获得的资源数量
     * @param sellResourceCost 用资源换金币时消耗的资源数量
     * @param sellCoinAmount 用资源换金币时获得的金币数量
     * @return true 如果存在套利
     */
    public static boolean hasArbitrage(int buyCoinCost, int buyResourceAmount,
                                        int sellResourceCost, int sellCoinAmount) {
        double buyPrice = coinPerResource(buyCoinCost, buyResourceAmount);
        double sellPrice = coinPerResource(sellCoinAmount, sellResourceCost);
        return buyPrice < sellPrice;
    }

    /**
     * 检查商店->回收套利
     * 套利条件: 商店售价 < 回收价
     * @param shopPrice 商店售价
     * @param recyclePrice 回收价
     * @return true 如果存在套利
     */
    public static boolean hasShopRecycleArbitrage(int shopPrice, int recyclePrice) {
        return shopPrice < recyclePrice;
    }

    // ========== 坏词条叠加计算 ==========

    /**
     * 计算坏词条叠加后的血量倍率（加法叠加 + 封顶）
     * @param multipliers 各坏词条的血量倍率数组
     * @return 封顶后的总血量倍率
     */
    public static double calculateCappedHostileHealth(double[] multipliers) {
        double bonusTotal = 0;
        for (double mult : multipliers) {
            bonusTotal += (mult - 1.0);
        }
        return 1.0 + Math.min(bonusTotal, BalanceConfig.MAX_HOSTILE_HEALTH_MULT - 1.0);
    }

    /**
     * 计算坏词条叠加后的伤害倍率（加法叠加 + 封顶）
     */
    public static double calculateCappedHostileDamage(double[] multipliers) {
        double bonusTotal = 0;
        for (double mult : multipliers) {
            bonusTotal += (mult - 1.0);
        }
        return 1.0 + Math.min(bonusTotal, BalanceConfig.MAX_HOSTILE_DAMAGE_MULT - 1.0);
    }

    // ========== 属性封顶计算 ==========

    /**
     * 封顶生命加成
     */
    public static int capHealthBonus(int raw) {
        return Math.min(raw, BalanceConfig.MAX_HEALTH_BONUS);
    }

    /**
     * 封顶吸血百分比
     */
    public static double capLifesteal(double raw) {
        return Math.min(raw, BalanceConfig.MAX_LIFESTEAL_PERCENT);
    }

    /**
     * 封顶每秒回血
     */
    public static double capHpPerSecond(double raw) {
        return Math.min(raw, BalanceConfig.MAX_HP_PER_SECOND);
    }

    /**
     * 封顶移速百分比
     */
    public static double capMoveSpeedPercent(double raw) {
        return Math.min(raw, BalanceConfig.MAX_MOVE_SPEED_PERCENT);
    }

    /**
     * 封顶基础攻击力
     */
    public static double capBaseAttackDamage(double raw) {
        return Math.min(raw, BalanceConfig.MAX_BASE_ATTACK_DAMAGE);
    }

    // ========== Boss HP 缩放计算 ==========

    /**
     * 计算 Boss HP 玩家数缩放倍率
     * @param playerCount 战场内玩家数
     * @return 玩家数缩放倍率（1.0 ~ BOSS_HP_PLAYER_SCALE_MAX）
     */
    public static double calculateBossPlayerScale(int playerCount) {
        if (playerCount <= 1) return 1.0;
        double scale = 1.0 + (playerCount - 1) * BalanceConfig.BOSS_HP_PER_PLAYER_BONUS;
        return Math.min(scale, BalanceConfig.BOSS_HP_PLAYER_SCALE_MAX);
    }

    /**
     * 计算 Boss 总 HP 倍率（难度 * 玩家数）
     * @param difficulty 战场难度
     * @param playerCount 玩家数
     * @return 总 HP 倍率
     */
    public static double calculateBossHpMultiplier(int difficulty, int playerCount) {
        double difficultyMult = Math.min(1.0 + difficulty * 0.25, 4.0);
        double playerScale = calculateBossPlayerScale(playerCount);
        return difficultyMult * playerScale;
    }
}
