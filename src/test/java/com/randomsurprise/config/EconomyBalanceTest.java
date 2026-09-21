package com.randomsurprise.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 经济平衡性测试
 * 验证所有资源兑换和商店/回收价格不存在套利漏洞
 */
class EconomyBalanceTest {

    // ========== 资源兑换套利检测 ==========

    @Test
    @DisplayName("绿宝石兑换无套利: coin_from_emerald vs res_diamond_to_emerald")
    void noEmeraldArbitrage() {
        // 买入: 用金币买绿宝石 (res_diamond_to_emerald)
        // 卖出: 用绿宝石换金币 (coin_from_emerald)
        boolean arbitrage = BalanceMath.hasArbitrage(
            BalanceConfig.RES_EMERALD_COST,       // 买: 消耗 2 金币
            BalanceConfig.RES_EMERALD_AMOUNT,     // 买: 获得 2 绿宝石
            BalanceConfig.COIN_FROM_EMERALD_COST, // 卖: 消耗 1 绿宝石
            BalanceConfig.COIN_FROM_EMERALD_RATE  // 卖: 获得 2 金币
        );
        assertFalse(arbitrage, "绿宝石存在套利: 买入单价 < 卖出单价");
    }

    @Test
    @DisplayName("铁锭兑换无套利: coin_from_iron vs res_gold_to_iron")
    void noIronArbitrage() {
        boolean arbitrage = BalanceMath.hasArbitrage(
            BalanceConfig.RES_IRON_COST,          // 买: 消耗 1 金币
            BalanceConfig.RES_IRON_AMOUNT,        // 买: 获得 8 铁锭
            BalanceConfig.COIN_FROM_IRON_COST,    // 卖: 消耗 4 铁锭
            BalanceConfig.COIN_FROM_IRON_RATE     // 卖: 获得 1 金币
        );
        assertFalse(arbitrage, "铁锭存在套利");
    }

    @Test
    @DisplayName("金锭兑换无套利: coin_from_gold vs res_iron_to_gold")
    void noGoldArbitrage() {
        boolean arbitrage = BalanceMath.hasArbitrage(
            BalanceConfig.RES_GOLD_COST,          // 买: 消耗 2 金币
            BalanceConfig.RES_GOLD_AMOUNT,        // 买: 获得 1 金锭
            BalanceConfig.COIN_FROM_GOLD_COST,    // 卖: 消耗 4 金锭
            BalanceConfig.COIN_FROM_GOLD_RATE     // 卖: 获得 2 金币
        );
        assertFalse(arbitrage, "金锭存在套利");
    }

    @Test
    @DisplayName("钻石兑换无套利: coin_from_diamond vs res_emerald_to_diamond")
    void noDiamondArbitrage() {
        boolean arbitrage = BalanceMath.hasArbitrage(
            BalanceConfig.RES_DIAMOND_COST,       // 买: 消耗 4 金币
            BalanceConfig.RES_DIAMOND_AMOUNT,     // 买: 获得 1 钻石
            BalanceConfig.COIN_FROM_DIAMOND_COST, // 卖: 消耗 1 钻石
            BalanceConfig.COIN_FROM_DIAMOND_RATE  // 卖: 获得 3 金币
        );
        assertFalse(arbitrage, "钻石存在套利");
    }

    // ========== 商店->回收套利检测 ==========

    @Test
    @DisplayName("下界之星: 商店售价 >= 回收价")
    void netherStarNoArbitrage() {
        boolean arbitrage = BalanceMath.hasShopRecycleArbitrage(
            BalanceConfig.SHOP_NETHER_STAR_PRICE,
            BalanceConfig.RECYCLE_NETHER_STAR_PRICE
        );
        assertFalse(arbitrage, "下界之星套利: 商店价 < 回收价");
    }

    @Test
    @DisplayName("龙蛋: 商店售价 >= 回收价")
    void dragonEggNoArbitrage() {
        boolean arbitrage = BalanceMath.hasShopRecycleArbitrage(
            BalanceConfig.SHOP_DRAGON_EGG_PRICE,
            BalanceConfig.RECYCLE_DRAGON_EGG_PRICE
        );
        assertFalse(arbitrage, "龙蛋套利: 商店价 < 回收价");
    }

    @Test
    @DisplayName("鞘翅: 商店售价 >= 回收价")
    void elytraNoArbitrage() {
        boolean arbitrage = BalanceMath.hasShopRecycleArbitrage(
            BalanceConfig.SHOP_ELYTRA_PRICE,
            BalanceConfig.RECYCLE_ELYTRA_PRICE
        );
        assertFalse(arbitrage, "鞘翅套利: 商店价 < 回收价");
    }
}
