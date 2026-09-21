package com.randomsurprise;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;

/**
 * 免伤机制处理器
 * 在原版护甲/抗性减免之上，提供额外的伤害减免：
 * 1. 护甲值额外减伤：每点护甲值额外减免 0.5% 伤害（上限 15%）
 * 2. 护甲韧性额外减伤：每点韧性额外减免 1% 伤害（上限 10%）
 * 3. 盾牌格挡加成：手持盾牌时额外减免 5%
 * 4. 经验等级减伤：每10级经验额外减免 1%（上限 5%）
 * 5. 抗性效果封顶：抗性提升最多2级（由 PlayerAffixManager 控制）
 *
 * 实现方式：在 AFTER_DAMAGE 事件中，按减伤比例回复玩家生命值
 */
public class DamageReductionHandler {

	/** 护甲值减伤上限 */
	private static final double MAX_ARMOR_REDUCTION = 0.15;
	/** 韧性减伤上限 */
	private static final double MAX_TOUGHNESS_REDUCTION = 0.10;
	/** 盾牌减伤 */
	private static final double SHIELD_REDUCTION = 0.05;
	/** 经验等级减伤上限 */
	private static final double MAX_EXP_REDUCTION = 0.05;

	/**
	 * 在伤害结算后调用，计算额外减伤比例并回复对应生命值
	 * @param entity 受伤实体
	 * @param source 伤害来源
	 * @param appliedAmount 实际结算后的伤害值
	 */
	public static void onAfterDamage(LivingEntity entity, DamageSource source, float appliedAmount) {
		if (!(entity instanceof ServerPlayer player)) return;
		if (appliedAmount <= 0) return;

		// 忽略虚空/指令伤害
		if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

		// 计算总减伤比例
		double reductionPercent = calculateReduction(player);

		if (reductionPercent <= 0) return;

		// 计算需要回复的生命值
		float healAmount = appliedAmount * (float) reductionPercent;
		if (healAmount > 0 && player.getHealth() > 0) {
			// 不超过实际受到的伤害
			healAmount = Math.min(healAmount, appliedAmount);
			player.heal(healAmount);
		}
	}

	/**
	 * 计算玩家的额外减伤比例（0.0 ~ 0.35）
	 */
	private static double calculateReduction(ServerPlayer player) {
		double totalReduction = 0;

		// 1. 护甲值额外减伤（每点护甲 0.5%，上限 15%）
		double armorValue = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
		totalReduction += Math.min(MAX_ARMOR_REDUCTION, armorValue * 0.005);

		// 2. 护甲韧性额外减伤（每点韧性 1%，上限 10%）
		double toughnessValue = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
		totalReduction += Math.min(MAX_TOUGHNESS_REDUCTION, toughnessValue * 0.01);

		// 3. 盾牌格挡加成（主手或副手持盾时额外 5%）
		ItemStack mainHand = player.getMainHandItem();
		ItemStack offHand = player.getOffhandItem();
		if (mainHand.getItem() instanceof ShieldItem || offHand.getItem() instanceof ShieldItem) {
			totalReduction += SHIELD_REDUCTION;
		}

		// 4. 经验等级减伤（每10级 1%，上限 5%）
		int expLevel = player.experienceLevel;
		totalReduction += Math.min(MAX_EXP_REDUCTION, (expLevel / 10) * 0.01);

		// 总减伤上限 35%
		return Math.min(0.35, totalReduction);
	}
}
