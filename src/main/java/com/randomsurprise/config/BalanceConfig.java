package com.randomsurprise.config;

/**
 * 平衡性配置中心
 * 所有经济汇率和属性封顶值集中管理，便于单元测试验证无套利
 */
public final class BalanceConfig {

    private BalanceConfig() {}

    // ========== 资源兑换汇率（金币 <-> 资源）==========
    // coin_from_xxx: 用资源买金币（玩家给资源，收金币）
    // res_xxx_to_xxx: 用金币买资源（玩家给金币，收资源）

    // --- 金币兑换（资源 -> 金币）---
    public static final int COIN_FROM_EMERALD_RATE = 2;       // 1 绿宝石 -> 2 金币
    public static final int COIN_FROM_EMERALD_COST = 1;       // 消耗 1 绿宝石
    public static final int COIN_FROM_IRON_RATE = 1;          // 1 金币
    public static final int COIN_FROM_IRON_COST = 4;          // 消耗 4 铁锭
    public static final int COIN_FROM_GOLD_RATE = 2;          // 2 金币
    public static final int COIN_FROM_GOLD_COST = 4;          // 消耗 4 金锭
    public static final int COIN_FROM_DIAMOND_RATE = 3;       // 3 金币
    public static final int COIN_FROM_DIAMOND_COST = 1;       // 消耗 1 钻石
    public static final int COIN_FROM_NETHERITE_RATE = 15;    // 15 金币
    public static final int COIN_FROM_NETHERITE_COST = 1;     // 消耗 1 下界合金锭
    public static final int COIN_FROM_TICKET_RATE = 5;        // 5 金币
    public static final int COIN_FROM_TICKET_COST = 1;        // 消耗 1 彩票

    // --- 资源兑换（金币 -> 资源）---
    // 修复: 2 金币 -> 1 绿宝石（与 coin_from_emerald 1 绿宝石 -> 2 金币 平衡）
    public static final int RES_EMERALD_COST = 2;             // 消耗 2 金币
    public static final int RES_EMERALD_AMOUNT = 1;           // 获得 1 绿宝石

    // v19: 铁购买价格翻三倍（3 金币 -> 4 铁锭）
    public static final int RES_IRON_COST = 3;                // 消耗 3 金币
    public static final int RES_IRON_AMOUNT = 4;              // 获得 4 铁锭

    public static final int RES_DIAMOND_COST = 4;             // 消耗 4 金币
    public static final int RES_DIAMOND_AMOUNT = 1;           // 获得 1 钻石
    // v19: 金购买价格翻三倍（6 金币 -> 1 金锭）
    public static final int RES_GOLD_COST = 6;                // 消耗 6 金币
    public static final int RES_GOLD_AMOUNT = 1;              // 获得 1 金锭

    // --- 商店稀有物品售价（金币）---
    public static final int SHOP_NETHER_STAR_PRICE = 40;
    public static final int SHOP_DRAGON_EGG_PRICE = 80;
    public static final int SHOP_ELYTRA_PRICE = 50;
    public static final int SHOP_NETHERITE_INGOT_PRICE = 20;

    // --- 回收白名单价格（金币）---
    // 修复: 回收价 < 商店售价的 75%，杜绝套利
    public static final int RECYCLE_NETHER_STAR_PRICE = 30;   // 商店40, 回收30
    public static final int RECYCLE_DRAGON_EGG_PRICE = 60;    // 商店80, 回收60
    public static final int RECYCLE_ELYTRA_PRICE = 40;
    public static final int RECYCLE_DRAGON_HEAD_PRICE = 60;

    // ========== 属性封顶值 ==========
    public static final int MAX_HEALTH_BONUS = 180;            // 生命加成封顶 +180（原30，调高6倍）
    public static final double MAX_LIFESTEAL_PERCENT = 3.0;   // 吸血封顶 300%（原50%，调高6倍）
    public static final double MAX_HP_PER_SECOND = 30.0;      // 每秒回血封顶 30 HP（原5，调高6倍）
    public static final double MAX_MOVE_SPEED_PERCENT = 0.07; // 移速百分比封顶 +7%（联动模组时限制增益）
    public static final double MAX_BASE_ATTACK_DAMAGE = 90.0; // 基础攻击力封顶 +90（原15，调高6倍）

    // ========== 坏词条全局叠加封顶 ==========
    public static final double MAX_HOSTILE_HEALTH_MULT = 2.0; // 怪物血量倍率封顶 2.0x
    public static final double MAX_HOSTILE_DAMAGE_MULT = 1.5; // 怪物伤害倍率封顶 1.5x
    public static final double MAX_HOSTILE_SPEED_MULT = 1.3;  // 怪物速度倍率封顶 1.3x

    // ========== Boss HP 玩家数缩放 ==========
    public static final double BOSS_HP_PER_PLAYER_BONUS = 0.2; // 每多一名玩家 +20% HP
    public static final double BOSS_HP_PLAYER_SCALE_MAX = 2.0; // 玩家数缩放封顶 2.0x

    // ========== 超能力冷却 ==========
    public static final int NEAR_DEATH_RECALL_COOLDOWN = 180;  // 濒死回溯冷却 180 秒（原 120）

    // ========== 单人倒地自救窗口 ==========
    public static final int SOLO_DOWNED_GRACE_TICKS = 600;     // 30 秒自救窗口（600 ticks）
}
