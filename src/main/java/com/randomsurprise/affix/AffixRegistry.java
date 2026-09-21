package com.randomsurprise.affix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 词条注册表
 * v1: 6个稀有度 × 好/坏 = 12个基础词条（属性增强）
 * v2: 新增 6个稀有度 × 好/坏 = 12个特殊词条（吸血/护盾/免疫/召唤/武器/护甲）
 * 共 24 个词条
 *
 * 抽奖逻辑：
 * - 先决定好坏（70%好/30%坏）
 * - 再 roll 稀有度（权重随全服坏词条数动态提升）
 * - 在对应 (good, rarity) 的多个词条中随机选一个
 */
public class AffixRegistry {
	private static final List<Affix> ALL_AFFIXES = new ArrayList<>();
	/** id → Affix 索引，getById O(1) 查找 */
	private static final Map<String, Affix> ID_MAP = new HashMap<>();
	/** (good + "_" + rarity) → 该分组不可变列表缓存，getByRarity O(1) 查找 */
	private static final Map<String, List<Affix>> RARITY_CACHE = new HashMap<>();
	private static final Random RANDOM = new Random();
	private static final double GOOD_CHANCE = 0.70; // 好词条70%

	/**
	 * 快速创建Boss专用词条（大部分参数默认为0/false/null，bossOnly固定true）
	 * 仅暴露Boss常用的非零参数，减少注册代码冗余
	 *
	 * @param id            词条ID（同时用作nameKey/descKey的前缀）
	 * @param rarity        稀有度
	 * @param hpMult        hostileHealthMult - Boss血量倍率
	 * @param dmgMult       hostileDamageMult - Boss伤害倍率
	 * @param spdMult       hostileSpeedMult - Boss速度倍率
	 * @param lifeSteal     hostileLifestealPercent - Boss吸血百分比
	 * @param summonChance  hostileSummonChance - Boss召唤概率
	 * @param fireImmune    hostileFireImmunity - Boss火焰免疫
	 * @param armorLevel    hostileArmorLevel - Boss护甲等级
	 * @param regenLevel    hostileRegenLevel - Boss生命恢复等级
	 * @param thorns        hostileThornsReflect - Boss反伤百分比
	 * @param armorPercent  hostileArmorPercent - Boss护甲减免百分比
	 * @param healReduction healReductionPercent - Boss对玩家的治疗削减百分比
	 */
	private static Affix bossAffix(String id, AffixRarity rarity,
			double hpMult, double dmgMult, double spdMult,
			double lifeSteal, double summonChance, boolean fireImmune,
			int armorLevel, int regenLevel, double thorns, double armorPercent,
			int healReduction) {
		String cleanId = id.replaceFirst("_(common|uncommon|rare|epic|legendary|mythic)$", "");
		return new Affix(id, rarity, false,
				"affix.randomsurprise." + cleanId + ".name",
				"affix.randomsurprise." + cleanId + ".desc",
				0, 0, 0, 0, 0,
				hpMult, dmgMult, spdMult, 0,
				0.0, 0, false, false, false, 0, 0.0, (String)null,
				lifeSteal, summonChance, fireImmune,
				armorLevel,
				0, 0, 0,
				regenLevel, thorns,
				0, 0.0, (String)null, 0,
				armorPercent, 0.0, 0.0,
				0.0, 0.0, false,
				0.0, 0.0,
				0.0, 0.0,
				0.0, 0.0, 0.0,
				0.0, 0.0,
				0.0, 0.0, 0.0,
				0.0, 0.0, 0.0,
				healReduction, 0, 0, 0.0,
				true);
	}

	static {
		// ========== 基础好词条（属性增强）==========
		register(new Affix("good_common", AffixRarity.COMMON, true,
				"affix.randomsurprise.good_common.name", "affix.randomsurprise.good_common.desc",
				2, 0, 0, 0, 0,
				0, 0, 0, 0));
		register(new Affix("good_uncommon", AffixRarity.UNCOMMON, true,
				"affix.randomsurprise.good_uncommon.name", "affix.randomsurprise.good_uncommon.desc",
				4, 1, 0, 0, 10,
				0, 0, 0, 0));
		register(new Affix("good_rare", AffixRarity.RARE, true,
				"affix.randomsurprise.good_rare.name", "affix.randomsurprise.good_rare.desc",
				6, 0, 1, 0, 15,
				0, 0, 0, 0));
		register(new Affix("good_epic", AffixRarity.EPIC, true,
				"affix.randomsurprise.good_epic.name", "affix.randomsurprise.good_epic.desc",
				8, 0, 0, 1, 20,
				0, 0, 0, 0));
		register(new Affix("good_legendary", AffixRarity.LEGENDARY, true,
				"affix.randomsurprise.good_legendary.name", "affix.randomsurprise.good_legendary.desc",
				10, 1, 2, 1, 30,
				0, 0, 0, 0));
		register(new Affix("good_mythic", AffixRarity.MYTHIC, true,
				"affix.randomsurprise.good_mythic.name", "affix.randomsurprise.good_mythic.desc",
				15, 1, 2, 2, 50,
				0, 0, 0, 0));

		// ========== 特殊好词条（吸血/护盾/免疫/召唤/武器）==========
		// 白色：吸血鬼初阶 - 攻击吸血5%
		register(new Affix("good_vampire_common", AffixRarity.COMMON, true,
				"affix.randomsurprise.good_vampire_common.name", "affix.randomsurprise.good_vampire_common.desc",
				2, 0, 0, 0, 0,
				0, 0, 0, 0,
				0.03, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 绿色：护盾守卫 - 吸收4点护盾，冷却60秒，抗性I
		register(new Affix("good_shield_uncommon", AffixRarity.UNCOMMON, true,
				"affix.randomsurprise.good_shield_uncommon.name", "affix.randomsurprise.good_shield_uncommon.desc",
				4, 0, 0, 1, 5,
				0, 0, 0, 0,
				0, 4, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				60, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 蓝色：免疫大师 - 火焰免疫+溺水免疫，生命+4
		register(new Affix("good_immune_rare", AffixRarity.RARE, true,
				"affix.randomsurprise.good_immune_rare.name", "affix.randomsurprise.good_immune_rare.desc",
				4, 0, 0, 0, 10,
				0, 0, 0, 0,
				0, 0, false, true, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0.5, 0, 0, 0, false, 0, 0, 0, 0));
		// 紫色：武器大师 - 武器额外伤害+1级（力量I），生命+6
		register(new Affix("good_weapon_epic", AffixRarity.EPIC, true,
				"affix.randomsurprise.good_weapon_epic.name", "affix.randomsurprise.good_weapon_epic.desc",
				6, 0, 0, 0, 15,
				0, 0, 0, 0,
				0, 0, false, false, false, 1, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 红色：召唤大师 - 受击10%概率召唤铁傀儡，生命+8
		register(new Affix("good_summon_legendary", AffixRarity.LEGENDARY, true,
				"affix.randomsurprise.good_summon_legendary.name", "affix.randomsurprise.good_summon_legendary.desc",
				8, 0, 1, 1, 20,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0.10, "minecraft:iron_golem",
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 金色：神之庇护 - 吸收10+火焰免疫+摔落免疫+吸血5%，冷却60秒
		register(new Affix("good_divine_mythic", AffixRarity.MYTHIC, true,
				"affix.randomsurprise.good_divine_mythic.name", "affix.randomsurprise.good_divine_mythic.desc",
				12, 1, 1, 1, 40,
				0, 0, 0, 0,
				0.05, 10, true, false, true, 1, 0.10, "minecraft:iron_golem",
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				60, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));

		// ========== 基础坏词条（敌对生物增强）==========
		register(new Affix("bad_common", AffixRarity.COMMON, false,
				"affix.randomsurprise.bad_common.name", "affix.randomsurprise.bad_common.desc",
				0, 0, 0, 0, 0,
				1.05, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		register(new Affix("bad_uncommon", AffixRarity.UNCOMMON, false,
				"affix.randomsurprise.bad_uncommon.name", "affix.randomsurprise.bad_uncommon.desc",
				0, 0, 0, 0, 0,
				1.0, 1.05, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		register(new Affix("bad_rare", AffixRarity.RARE, false,
				"affix.randomsurprise.bad_rare.name", "affix.randomsurprise.bad_rare.desc",
				0, 0, 0, 0, 0,
				1.0, 1.0, 1.10, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		register(new Affix("bad_epic", AffixRarity.EPIC, false,
				"affix.randomsurprise.bad_epic.name", "affix.randomsurprise.bad_epic.desc",
				0, 0, 0, 0, 0,
				1.10, 1.05, 1.0, 1,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		register(new Affix("bad_legendary", AffixRarity.LEGENDARY, false,
				"affix.randomsurprise.bad_legendary.name", "affix.randomsurprise.bad_legendary.desc",
				0, 0, 0, 0, 0,
				1.15, 1.10, 1.0, 2,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		register(new Affix("bad_mythic", AffixRarity.MYTHIC, false,
				"affix.randomsurprise.bad_mythic.name", "affix.randomsurprise.bad_mythic.desc",
				0, 0, 0, 0, 0,
				1.20, 1.15, 1.10, 2,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));

		// ========== 特殊坏词条（护甲/吸血/召唤/狂暴/免疫/神体）==========
		// 白色：怪物护甲 - 抗性I，血量+3%
		register(new Affix("bad_armor_common", AffixRarity.COMMON, false,
				"affix.randomsurprise.bad_armor_common.name", "affix.randomsurprise.bad_armor_common.desc",
				0, 0, 0, 0, 0,
				1.03, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 1,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 绿色：怪物吸血 - 攻击吸血3%
		register(new Affix("bad_vampire_uncommon", AffixRarity.UNCOMMON, false,
				"affix.randomsurprise.bad_vampire_uncommon.name", "affix.randomsurprise.bad_vampire_uncommon.desc",
				0, 0, 0, 0, 0,
				1.0, 1.03, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0.03, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 蓝色：怪物召唤 - 3%概率召唤同伴，速度+5%
		register(new Affix("bad_summon_rare", AffixRarity.RARE, false,
				"affix.randomsurprise.bad_summon_rare.name", "affix.randomsurprise.bad_summon_rare.desc",
				0, 0, 0, 0, 0,
				1.0, 1.0, 1.05, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0.03, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 紫色：怪物狂暴 - 攻击+6%，血量+5%，力量I
		register(new Affix("bad_berserk_epic", AffixRarity.EPIC, false,
				"affix.randomsurprise.bad_berserk_epic.name", "affix.randomsurprise.bad_berserk_epic.desc",
				0, 0, 0, 0, 0,
				1.05, 1.06, 1.0, 1,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 红色：怪物免疫 - 火焰免疫，血量+8%，护甲I
		register(new Affix("bad_immune_legendary", AffixRarity.LEGENDARY, false,
				"affix.randomsurprise.bad_immune_legendary.name", "affix.randomsurprise.bad_immune_legendary.desc",
				0, 0, 0, 0, 0,
				1.08, 1.0, 1.0, 1,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 1,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0.5, 0, 0, false, 0, 0, 0, 0));
		// 金色：怪物神体 - 吸血5%，攻击+10%，血量+12%，护甲II，火焰免疫
		register(new Affix("bad_godly_mythic", AffixRarity.MYTHIC, false,
				"affix.randomsurprise.bad_godly_mythic.name", "affix.randomsurprise.bad_godly_mythic.desc",
				0, 0, 0, 0, 0,
				1.12, 1.10, 1.05, 2,
				0, 0, false, false, false, 0, 0, null,
				0.05, 0.05, true, 2,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));

		// ========== v3 新增好词条（急速/击退/再生/屠龙/荆棘/凤凰）==========
		// 白色：急速之手 - 急迫I，挖掘速度+10%
		register(new Affix("good_haste_common", AffixRarity.COMMON, true,
				"affix.randomsurprise.good_haste_common.name", "affix.randomsurprise.good_haste_common.desc",
				0, 0, 0, 0, 5,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				1, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 绿色：击退之力 - 击退增强+4HP（用 weaponDamageBonus=1 作为击退代理）
		register(new Affix("good_knockback_uncommon", AffixRarity.UNCOMMON, true,
				"affix.randomsurprise.good_knockback_uncommon.name", "affix.randomsurprise.good_knockback_uncommon.desc",
				4, 0, 0, 0, 5,
				0, 0, 0, 0,
				0, 0, false, false, false, 1, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 蓝色：生命再生 - 自然回血+100%，+6HP（v8: 改用 naturalRegenBonus，不再用药水）
		register(new Affix("good_regen_rare", AffixRarity.RARE, true,
				"affix.randomsurprise.good_regen_rare.name", "affix.randomsurprise.good_regen_rare.desc",
				6, 0, 0, 0, 10,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0, 0,
				1.0));
		// 紫色：屠龙者 - 对Boss伤害+12%，+8HP
		register(new Affix("good_slayer_epic", AffixRarity.EPIC, true,
				"affix.randomsurprise.good_slayer_epic.name", "affix.randomsurprise.good_slayer_epic.desc",
				8, 0, 0, 0, 15,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0.12, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 红色：荆棘反伤 - 护盾6+摔落免疫+武器伤害II，+10HP，冷却60秒
		register(new Affix("good_thorns_legendary", AffixRarity.LEGENDARY, true,
				"affix.randomsurprise.good_thorns_legendary.name", "affix.randomsurprise.good_thorns_legendary.desc",
				10, 0, 0, 1, 20,
				0, 0, 0, 0,
				0, 6, false, false, true, 2, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				60, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 金色：不灭凤凰 - 力量II+抗性II+吸血5%+护盾10+火焰免疫+摔落免疫，冷却60秒
		register(new Affix("good_phoenix_mythic", AffixRarity.MYTHIC, true,
				"affix.randomsurprise.good_phoenix_mythic.name", "affix.randomsurprise.good_phoenix_mythic.desc",
				15, 1, 2, 2, 40,
				0, 0, 0, 0,
				0.05, 10, true, false, true, 2, 0.10, "minecraft:iron_golem",
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				60, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));

		// ========== v3 新增坏词条（疾速/反伤/回血/护盾）==========
		// 白色：疾速怪物 - 速度×1.10
		register(new Affix("bad_speed_common", AffixRarity.COMMON, false,
				"affix.randomsurprise.bad_speed_common.name", "affix.randomsurprise.bad_speed_common.desc",
				0, 0, 0, 0, 0,
				1.0, 1.0, 1.10, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 绿色：反伤装甲 - 玩家攻击时受到8%反伤
		register(new Affix("bad_thorns_uncommon", AffixRarity.UNCOMMON, false,
				"affix.randomsurprise.bad_thorns_uncommon.name", "affix.randomsurprise.bad_thorns_uncommon.desc",
				0, 0, 0, 0, 0,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0.08,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 蓝色：回血怪物 - 生命恢复I，血量×1.05
		register(new Affix("bad_regen_rare", AffixRarity.RARE, false,
				"affix.randomsurprise.bad_regen_rare.name", "affix.randomsurprise.bad_regen_rare.desc",
				0, 0, 0, 0, 0,
				1.05, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 1, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 紫色：护盾怪物 - 抗性II，血量×1.06，速度×1.05
		register(new Affix("bad_shield_epic", AffixRarity.EPIC, false,
				"affix.randomsurprise.bad_shield_epic.name", "affix.randomsurprise.bad_shield_epic.desc",
				0, 0, 0, 0, 0,
				1.06, 1.0, 1.05, 1,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 2,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));

		// ========== v4 新增好词条（闪电伤害/武器专属被动/护甲减免/伤害免疫）==========
		// 白色：雷电使者 - 攻击30%概率触发闪电，造成3点伤害
		register(new Affix("good_lightning_common", AffixRarity.COMMON, true,
				"affix.randomsurprise.good_lightning_common.name", "affix.randomsurprise.good_lightning_common.desc",
				2, 0, 0, 0, 5,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 3, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 绿色：剑士专精 - 手持剑时伤害+15%，+4HP
		register(new Affix("good_sword_master_uncommon", AffixRarity.UNCOMMON, true,
				"affix.randomsurprise.good_sword_master_uncommon.name", "affix.randomsurprise.good_sword_master_uncommon.desc",
				4, 0, 0, 0, 10,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, "sword", 0.15, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 蓝色：弓箭大师 - 手持弓时伤害+20%，速度+1
		register(new Affix("good_bow_master_rare", AffixRarity.RARE, true,
				"affix.randomsurprise.good_bow_master_rare.name", "affix.randomsurprise.good_bow_master_rare.desc",
				6, 0, 1, 0, 10,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, "bow", 0.20, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 紫色：战斧大师 - 手持斧时伤害+25%，力量+1
		register(new Affix("good_axe_master_epic", AffixRarity.EPIC, true,
				"affix.randomsurprise.good_axe_master_epic.name", "affix.randomsurprise.good_axe_master_epic.desc",
				8, 1, 0, 0, 15,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, "axe", 0.25, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 红色：神罚雷霆 - 攻击50%概率触发闪电，造成6点伤害，+10HP
		register(new Affix("good_divine_thunder_legendary", AffixRarity.LEGENDARY, true,
				"affix.randomsurprise.good_divine_thunder_legendary.name", "affix.randomsurprise.good_divine_thunder_legendary.desc",
				10, 0, 0, 0, 20,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 6, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 金色：武器至尊 - 所有武器伤害+30%，力量+2，+15HP
		register(new Affix("good_weapon_lord_mythic", AffixRarity.MYTHIC, true,
				"affix.randomsurprise.good_weapon_lord_mythic.name", "affix.randomsurprise.good_weapon_lord_mythic.desc",
				15, 2, 0, 0, 30,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, "all", 0.30, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));

		// ========== v4 新增坏词条（怪物护甲百分比/伤害免疫/强化攻击）==========
		// 白色：铁甲怪物 - 伤害减免3%
		register(new Affix("bad_armor_percent_common", AffixRarity.COMMON, false,
				"affix.randomsurprise.bad_armor_percent_common.name", "affix.randomsurprise.bad_armor_percent_common.desc",
				0, 0, 0, 0, 0,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0.03, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 绿色：重甲怪物 - 伤害减免5%，抗性I
		register(new Affix("bad_heavy_armor_uncommon", AffixRarity.UNCOMMON, false,
				"affix.randomsurprise.bad_heavy_armor_uncommon.name", "affix.randomsurprise.bad_heavy_armor_uncommon.desc",
				0, 0, 0, 0, 0,
				1.03, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 1,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0.05, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 蓝色：钢铁怪物 - 伤害减免8%，抗性II
		register(new Affix("bad_steel_armor_rare", AffixRarity.RARE, false,
				"affix.randomsurprise.bad_steel_armor_rare.name", "affix.randomsurprise.bad_steel_armor_rare.desc",
				0, 0, 0, 0, 0,
				1.05, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 2,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0.08, 0, 0, 0, 0, false, 0, 0, 0, 0));

		// ========== v6 元素伤害体系统一：火焰词条 ==========
		register(new Affix("good_fire_common", AffixRarity.COMMON, true,
				"affix.randomsurprise.good_fire_common.name", "affix.randomsurprise.good_fire_common.desc",
				2, 0, 0, 0, 5,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				3, 0, false, 0, 0, 0.15, 0));

		register(new Affix("good_fire_uncommon", AffixRarity.UNCOMMON, true,
				"affix.randomsurprise.good_fire_uncommon.name", "affix.randomsurprise.good_fire_uncommon.desc",
				4, 0, 0, 0, 10,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				4, 0, false, 0, 0, 0.20, 0));

		register(new Affix("good_fire_rare", AffixRarity.RARE, true,
				"affix.randomsurprise.good_fire_rare.name", "affix.randomsurprise.good_fire_rare.desc",
				6, 0, 0, 0, 15,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0.50, 0,
				5, 0, false, 0, 0, 0.25, 0.25,
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0.02));

		register(new Affix("good_fire_epic", AffixRarity.EPIC, true,
				"affix.randomsurprise.good_fire_epic.name", "affix.randomsurprise.good_fire_epic.desc",
				8, 0, 1, 0, 20,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0.50, 0,
				6, 0, false, 0, 0, 0.30, 0.40,
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0.02));

		register(new Affix("good_fire_legendary", AffixRarity.LEGENDARY, true,
				"affix.randomsurprise.good_fire_legendary.name", "affix.randomsurprise.good_fire_legendary.desc",
				10, 0, 0, 1, 30,
				1.0, 1.0, 1.0, 0,
				0, 0, true, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0.80, 0,
				8, 0, false, 0, 0, 0.35, 0.55,
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0.02));

		register(new Affix("good_fire_mythic", AffixRarity.MYTHIC, true,
				"affix.randomsurprise.good_fire_mythic.name", "affix.randomsurprise.good_fire_mythic.desc",
				15, 1, 1, 1, 50,
				1.0, 1.0, 1.0, 0,
				0, 0, true, false, true, 1, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 1.0, 0,
				10, 0, false, 0, 0, 0.40, 0.75,
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0, 0.02));

		// ========== v6 元素伤害体系统一：冰霜词条 ==========
		register(new Affix("good_frost_common", AffixRarity.COMMON, true,
				"affix.randomsurprise.good_frost_common.name", "affix.randomsurprise.good_frost_common.desc",
				2, 0, 0, 0, 5,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 3, false, 0, 0, 0, 0.15));

		register(new Affix("good_frost_uncommon", AffixRarity.UNCOMMON, true,
				"affix.randomsurprise.good_frost_uncommon.name", "affix.randomsurprise.good_frost_uncommon.desc",
				4, 1, 0, 0, 10,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 4, false, 0, 0, 0, 0.20));

		register(new Affix("good_frost_rare", AffixRarity.RARE, true,
				"affix.randomsurprise.good_frost_rare.name", "affix.randomsurprise.good_frost_rare.desc",
				6, 0, 0, 0, 15,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 5, false, 0.50, 0, 0, 0.25,
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0.20, 0));

		register(new Affix("good_frost_epic", AffixRarity.EPIC, true,
				"affix.randomsurprise.good_frost_epic.name", "affix.randomsurprise.good_frost_epic.desc",
				8, 0, 1, 0, 20,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 6, false, 0.50, 0, 0, 0.30,
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0.25, 0));

		register(new Affix("good_frost_legendary", AffixRarity.LEGENDARY, true,
				"affix.randomsurprise.good_frost_legendary.name", "affix.randomsurprise.good_frost_legendary.desc",
				10, 0, 0, 1, 30,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, true, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 8, true, 0.80, 0, 0, 0.35,
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0.30, 0));

		register(new Affix("good_frost_mythic", AffixRarity.MYTHIC, true,
				"affix.randomsurprise.good_frost_mythic.name", "affix.randomsurprise.good_frost_mythic.desc",
				15, 1, 1, 1, 50,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, true, 1, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 10, true, 1.0, 0, 0, 0.40,
				0, 0, 0, 0, 0, 0, 0, 0,
				0, 0.35, 0));

		// ========== v6 补齐闪电词条4档（绿/蓝/紫/金） ==========
		register(new Affix("good_lightning_uncommon", AffixRarity.UNCOMMON, true,
				"affix.randomsurprise.good_lightning_uncommon.name", "affix.randomsurprise.good_lightning_uncommon.desc",
				4, 1, 0, 0, 10,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 4, null, 0, 0, 0, 0,
				0, 0, false, 0, 0.30, 0, 0));

		register(new Affix("good_lightning_rare", AffixRarity.RARE, true,
				"affix.randomsurprise.good_lightning_rare.name", "affix.randomsurprise.good_lightning_rare.desc",
				6, 0, 0, 0, 15,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 5, null, 0, 0, 0, 0,
				0, 0, false, 0, 0.50, 0, 0));

		register(new Affix("good_lightning_epic", AffixRarity.EPIC, true,
				"affix.randomsurprise.good_lightning_epic.name", "affix.randomsurprise.good_lightning_epic.desc",
				8, 0, 1, 0, 20,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 6, null, 0, 0, 0, 0,
				0, 0, false, 0, 0.70, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0,
				0.03, 0, 0));

		register(new Affix("good_lightning_mythic", AffixRarity.MYTHIC, true,
				"affix.randomsurprise.good_lightning_mythic.name", "affix.randomsurprise.good_lightning_mythic.desc",
				15, 1, 2, 1, 50,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, true, 1, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 8, null, 0, 0, 0, 0,
				0, 0, false, 0, 0.90, 0, 0,
				0, 0, 0, 0, 0, 0, 0, 0,
				0.05, 0, 0));

		// ========== v7 新增好词条（独立属性加成，不依赖原版药水效果）==========
		// 白色：矿工之腕 - 挖掘速度+15%，经验+5%
		register(new Affix("good_miner_common", AffixRarity.COMMON, true,
				"affix.randomsurprise.good_miner_common.name", "affix.randomsurprise.good_miner_common.desc",
				0, 0, 0, 0, 5,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0.15, 0, 0, 0, 0, 0, 0));
		// 绿色：猎人直觉 - 移动速度+10%，暴击8%
		register(new Affix("good_hunter_uncommon", AffixRarity.UNCOMMON, true,
				"affix.randomsurprise.good_hunter_uncommon.name", "affix.randomsurprise.good_hunter_uncommon.desc",
				0, 0, 0, 0, 5,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0.10, 0.08, 0, 0, 0));
		// 蓝色：烈焰使者 - 火焰伤害+2，30%点燃，火焰减免20%
		register(new Affix("good_pyro_rare", AffixRarity.RARE, true,
				"affix.randomsurprise.good_pyro_rare.name", "affix.randomsurprise.good_pyro_rare.desc",
				0, 0, 0, 0, 5,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0.20, 0,
				2.0, 0, false, 0, 0, 0.30, 0,
				0, 0, 0, 0, 0, 0, 0,
				0, 0, 0, 0.02));
		// 紫色：冰霜法师 - 冰霜伤害+3，40%减速，冰霜免疫，冰霜减免30%
		register(new Affix("good_frost_mage_epic", AffixRarity.EPIC, true,
				"affix.randomsurprise.good_frost_mage_epic.name", "affix.randomsurprise.good_frost_mage_epic.desc",
				0, 0, 0, 0, 10,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 3.0, true, 0.30, 0, 0, 0.40,
				0, 0, 0, 0, 0, 0, 0,
				0, 0, 0.25, 0));
		// 红色：守护之灵 - 攻击速度+20%，治疗+30%，护盾6（60s冷却），生命+8
		register(new Affix("good_guardian_legendary", AffixRarity.LEGENDARY, true,
				"affix.randomsurprise.good_guardian_legendary.name", "affix.randomsurprise.good_guardian_legendary.desc",
				8, 0, 0, 0, 10,
				0, 0, 0, 0,
				0, 6, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				60, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0.20, 0, 0, 0.30, 0, 0));
		// 金色：雷霆战神 - 闪电伤害+4，暴击20%，攻速+25%，移速+15%，闪电减免50%，生命+10
		register(new Affix("good_thunder_god_mythic", AffixRarity.MYTHIC, true,
				"affix.randomsurprise.good_thunder_god_mythic.name", "affix.randomsurprise.good_thunder_god_mythic.desc",
				10, 0, 0, 0, 20,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 4.0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0.50, 0, 0,
				0, 0.25, 0.15, 0.20, 0, 0, 0,
				0, 0.05, 0, 0));

		// ========== v7 新增坏词条（元素抗性扩展）==========
		// 白色：火焰抗性怪 - 火焰减免20%
		register(new Affix("bad_fire_resist_common", AffixRarity.COMMON, false,
				"affix.randomsurprise.bad_fire_resist_common.name", "affix.randomsurprise.bad_fire_resist_common.desc",
				0, 0, 0, 0, 0,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0));
		// 绿色：冰霜抗性怪 - 冰霜减免25%
		register(new Affix("bad_frost_resist_uncommon", AffixRarity.UNCOMMON, false,
				"affix.randomsurprise.bad_frost_resist_uncommon.name", "affix.randomsurprise.bad_frost_resist_uncommon.desc",
				0, 0, 0, 0, 0,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0.25, 0));
		// 蓝色：闪电抗性怪 - 闪电减免30%，血量+5%
		register(new Affix("bad_lightning_resist_rare", AffixRarity.RARE, false,
				"affix.randomsurprise.bad_lightning_resist_rare.name", "affix.randomsurprise.bad_lightning_resist_rare.desc",
				0, 0, 0, 0, 0,
				1.05, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0.30));
		// 紫色：元素护盾怪 - 全元素减免30%，抗性I
		register(new Affix("bad_element_shield_epic", AffixRarity.EPIC, false,
				"affix.randomsurprise.bad_element_shield_epic.name", "affix.randomsurprise.bad_element_shield_epic.desc",
				0, 0, 0, 0, 0,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 1,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0.30, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0.30, 0.30));
		// 红色：狂暴火元素 - 火焰免疫，火焰减免50%，攻击+10%，血量+10%
		register(new Affix("bad_fire_berserk_legendary", AffixRarity.LEGENDARY, false,
				"affix.randomsurprise.bad_fire_berserk_legendary.name", "affix.randomsurprise.bad_fire_berserk_legendary.desc",
				0, 0, 0, 0, 0,
				1.10, 1.10, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, true, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0.50, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0));
		// 金色：元素免疫怪 - 火焰免疫，全元素减免50%，血量+15%，攻击+10%，抗性II
		register(new Affix("bad_element_immune_mythic", AffixRarity.MYTHIC, false,
				"affix.randomsurprise.bad_element_immune_mythic.name", "affix.randomsurprise.bad_element_immune_mythic.desc",
				0, 0, 0, 0, 0,
				1.15, 1.10, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, true, 2,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0.50, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0.50, 0.50));

		// ========== v9 新增好词条（扩充词条池以支持十连抽，共 15 个）==========
		// 白色：坚韧 - 抗性I，+2HP
		register(new Affix("good_tough_common", AffixRarity.COMMON, true,
				"affix.randomsurprise.good_tough_common.name", "affix.randomsurprise.good_tough_common.desc",
				2, 0, 0, 1, 0,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 白色：学徒 - 经验+10%，+2HP
		register(new Affix("good_apprentice_common", AffixRarity.COMMON, true,
				"affix.randomsurprise.good_apprentice_common.name", "affix.randomsurprise.good_apprentice_common.desc",
				2, 0, 0, 0, 10,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 绿色：学者 - 经验+25%，+2HP
		register(new Affix("good_scholar_uncommon", AffixRarity.UNCOMMON, true,
				"affix.randomsurprise.good_scholar_uncommon.name", "affix.randomsurprise.good_scholar_uncommon.desc",
				2, 0, 0, 0, 25,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 绿色：钢铁意志 - 摔落免疫，+4HP
		register(new Affix("good_iron_will_uncommon", AffixRarity.UNCOMMON, true,
				"affix.randomsurprise.good_iron_will_uncommon.name", "affix.randomsurprise.good_iron_will_uncommon.desc",
				4, 0, 0, 0, 5,
				0, 0, 0, 0,
				0, 0, false, false, true, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 绿色：战士 - 武器伤害I，+4HP，5%经验
		register(new Affix("good_warrior_uncommon", AffixRarity.UNCOMMON, true,
				"affix.randomsurprise.good_warrior_uncommon.name", "affix.randomsurprise.good_warrior_uncommon.desc",
				4, 0, 0, 0, 5,
				0, 0, 0, 0,
				0, 0, false, false, false, 1, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 蓝色：水行 - 溺水免疫，+6HP
		register(new Affix("good_aqua_rare", AffixRarity.RARE, true,
				"affix.randomsurprise.good_aqua_rare.name", "affix.randomsurprise.good_aqua_rare.desc",
				6, 0, 0, 0, 10,
				0, 0, 0, 0,
				0, 0, false, true, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 蓝色：狂战士 - 暴击12%，吸血3%，+4HP
		register(new Affix("good_berserker_rare", AffixRarity.RARE, true,
				"affix.randomsurprise.good_berserker_rare.name", "affix.randomsurprise.good_berserker_rare.desc",
				4, 0, 0, 0, 5,
				0, 0, 0, 0,
				0.03, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0.12, 0, 0, 0));
		// 蓝色：弓手 - 弓伤害+15%，暴击8%
		register(new Affix("good_archer_rare", AffixRarity.RARE, true,
				"affix.randomsurprise.good_archer_rare.name", "affix.randomsurprise.good_archer_rare.desc",
				0, 0, 0, 0, 5,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, "bow", 0.15, 0, 0, 0, 0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0.08, 0, 0, 0));
		// 紫色：圣骑士 - 治疗+25%，护盾4（60s冷却），+8HP
		register(new Affix("good_paladin_epic", AffixRarity.EPIC, true,
				"affix.randomsurprise.good_paladin_epic.name", "affix.randomsurprise.good_paladin_epic.desc",
				8, 0, 0, 0, 10,
				0, 0, 0, 0,
				0, 4, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				60, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0.25, 0, 0));
		// 紫色：奥术师 - 攻速+15%，暴击10%，+6HP
		register(new Affix("good_arcanist_epic", AffixRarity.EPIC, true,
				"affix.randomsurprise.good_arcanist_epic.name", "affix.randomsurprise.good_arcanist_epic.desc",
				6, 0, 0, 0, 10,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0,
				0, 0.15, 0, 0.10, 0, 0, 0));
		// 红色：吸血鬼领主 - 吸血10%，+10HP，力量I
		register(new Affix("good_vampire_lord_legendary", AffixRarity.LEGENDARY, true,
				"affix.randomsurprise.good_vampire_lord_legendary.name", "affix.randomsurprise.good_vampire_lord_legendary.desc",
				10, 0, 1, 0, 15,
				0, 0, 0, 0,
				0.10, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 红色：登山者 - 摔落免疫，移速+15%，挖掘+20%，+8HP
		register(new Affix("good_mountaineer_legendary", AffixRarity.LEGENDARY, true,
				"affix.randomsurprise.good_mountaineer_legendary.name", "affix.randomsurprise.good_mountaineer_legendary.desc",
				8, 0, 0, 0, 15,
				0, 0, 0, 0,
				0, 0, false, false, true, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0,
				0.20, 0, 0.15, 0, 0, 0, 0));
		// 红色：战斗法师 - 武器伤害II，攻速+15%，+8HP
		register(new Affix("good_battle_mage_legendary", AffixRarity.LEGENDARY, true,
				"affix.randomsurprise.good_battle_mage_legendary.name", "affix.randomsurprise.good_battle_mage_legendary.desc",
				8, 0, 0, 0, 15,
				0, 0, 0, 0,
				0, 0, false, false, false, 2, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0,
				0, 0.15, 0, 0, 0, 0, 0));
		// 金色：永恒守护者 - 全免疫，护盾15，吸血8%，+15HP，60s冷却
		register(new Affix("good_eternal_guardian_mythic", AffixRarity.MYTHIC, true,
				"affix.randomsurprise.good_eternal_guardian_mythic.name", "affix.randomsurprise.good_eternal_guardian_mythic.desc",
				15, 0, 0, 1, 30,
				0, 0, 0, 0,
				0.08, 15, true, true, true, 1, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				60, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 金色：大法师 - 攻速+30%，暴击25%，移速+20%，+10HP
		register(new Affix("good_archmage_mythic", AffixRarity.MYTHIC, true,
				"affix.randomsurprise.good_archmage_mythic.name", "affix.randomsurprise.good_archmage_mythic.desc",
				10, 0, 0, 0, 25,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0,
				0, 0.30, 0.20, 0.25, 0, 0, 0));

		// ========== v9 新增好词条（失明免疫）==========
		// 紫色：鹰眼 - 失明免疫率50%，+6HP，经验+10%
		register(new Affix("good_eagle_eye_epic", AffixRarity.EPIC, true,
				"affix.randomsurprise.good_eagle_eye_epic.name", "affix.randomsurprise.good_eagle_eye_epic.desc",
				6, 0, 0, 0, 10,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0.50));
		// 红色：天眼 - 失明免疫率80%，+10HP，经验+20%
		register(new Affix("good_sky_eye_legendary", AffixRarity.LEGENDARY, true,
				"affix.randomsurprise.good_sky_eye_legendary.name", "affix.randomsurprise.good_sky_eye_legendary.desc",
				10, 0, 0, 0, 20,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 0.80));
		// 金色：真视之眼 - 失明免疫率100%，+15HP，经验+30%
		register(new Affix("good_true_sight_mythic", AffixRarity.MYTHIC, true,
				"affix.randomsurprise.good_true_sight_mythic.name", "affix.randomsurprise.good_true_sight_mythic.desc",
				15, 0, 0, 0, 30,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0, 0, 1.0));

		// ========== v9 新增坏词条（扩充词条池以支持十连抽，共 10 个）==========
		// 白色：坚韧怪 - 血量+3%，攻击+3%
		register(new Affix("bad_tough_common", AffixRarity.COMMON, false,
				"affix.randomsurprise.bad_tough_common.name", "affix.randomsurprise.bad_tough_common.desc",
				0, 0, 0, 0, 0,
				1.03, 1.03, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 白色：疾速怪 - 速度×1.12
		register(new Affix("bad_swift_common", AffixRarity.COMMON, false,
				"affix.randomsurprise.bad_swift_common.name", "affix.randomsurprise.bad_swift_common.desc",
				0, 0, 0, 0, 0,
				1.0, 1.0, 1.12, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 绿色：诅咒怪 - 攻击+5%，速度+5%
		register(new Affix("bad_curse_uncommon", AffixRarity.UNCOMMON, false,
				"affix.randomsurprise.bad_curse_uncommon.name", "affix.randomsurprise.bad_curse_uncommon.desc",
				0, 0, 0, 0, 0,
				1.0, 1.05, 1.05, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 绿色：灵巧怪 - 速度×1.15，血量+3%
		register(new Affix("bad_nimble_uncommon", AffixRarity.UNCOMMON, false,
				"affix.randomsurprise.bad_nimble_uncommon.name", "affix.randomsurprise.bad_nimble_uncommon.desc",
				0, 0, 0, 0, 0,
				1.03, 1.0, 1.15, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 蓝色：狱卒怪 - 血量×1.08，护甲I
		register(new Affix("bad_warden_rare", AffixRarity.RARE, false,
				"affix.randomsurprise.bad_warden_rare.name", "affix.randomsurprise.bad_warden_rare.desc",
				0, 0, 0, 0, 0,
				1.08, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 1,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 蓝色：腐蚀怪 - 攻击+8%，反伤5%，血量+5%
		register(new Affix("bad_corrosive_rare", AffixRarity.RARE, false,
				"affix.randomsurprise.bad_corrosive_rare.name", "affix.randomsurprise.bad_corrosive_rare.desc",
				0, 0, 0, 0, 0,
				1.05, 1.08, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0.05,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 紫色：暗黑召唤师 - 5%召唤，攻击+8%，血量+8%
		register(new Affix("bad_dark_summoner_epic", AffixRarity.EPIC, false,
				"affix.randomsurprise.bad_dark_summoner_epic.name", "affix.randomsurprise.bad_dark_summoner_epic.desc",
				0, 0, 0, 0, 0,
				1.08, 1.08, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0.05, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 紫色：虚空怪 - 反伤15%，护甲II，血量+6%
		register(new Affix("bad_void_epic", AffixRarity.EPIC, false,
				"affix.randomsurprise.bad_void_epic.name", "affix.randomsurprise.bad_void_epic.desc",
				0, 0, 0, 0, 0,
				1.06, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 2,
				0, 0, 0, 0, 0.15,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 红色：巨像怪 - 血量×1.25，护甲III，攻击+12%
		register(new Affix("bad_titan_legendary", AffixRarity.LEGENDARY, false,
				"affix.randomsurprise.bad_titan_legendary.name", "affix.randomsurprise.bad_titan_legendary.desc",
				0, 0, 0, 0, 0,
				1.25, 1.12, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 3,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));
		// 金色：末日使者 - 血量+15%，攻击+15%，速度+10%，护甲III，反伤20%
		register(new Affix("bad_doom_mythic", AffixRarity.MYTHIC, false,
				"affix.randomsurprise.bad_doom_mythic.name", "affix.randomsurprise.bad_doom_mythic.desc",
				0, 0, 0, 0, 0,
				1.15, 1.15, 1.10, 2,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 3,
				0, 0, 0, 0, 0.20,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0));

		// ========== v12 新增坏词条：抑制回血系列（减少目标回血量百分比，取最大值避免100%抑制）==========
		// 白色：抑制回血I - 减少目标回血15%
		register(new Affix("bad_heal_reduction_common", AffixRarity.COMMON, false,
				"affix.randomsurprise.bad_heal_reduction_common.name", "affix.randomsurprise.bad_heal_reduction_common.desc",
				0, 0, 0, 0, 0,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				15, 0, 0, 0));
		// 绿色：抑制回血II - 减少目标回血25%
		register(new Affix("bad_heal_reduction_uncommon", AffixRarity.UNCOMMON, false,
				"affix.randomsurprise.bad_heal_reduction_uncommon.name", "affix.randomsurprise.bad_heal_reduction_uncommon.desc",
				0, 0, 0, 0, 0,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				25, 0, 0, 0));
		// 蓝色：抑制回血III - 减少目标回血35%
		register(new Affix("bad_heal_reduction_rare", AffixRarity.RARE, false,
				"affix.randomsurprise.bad_heal_reduction_rare.name", "affix.randomsurprise.bad_heal_reduction_rare.desc",
				0, 0, 0, 0, 0,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				35, 0, 0, 0));
		// 紫色：抑制回血IV - 减少目标回血50%
		register(new Affix("bad_heal_reduction_epic", AffixRarity.EPIC, false,
				"affix.randomsurprise.bad_heal_reduction_epic.name", "affix.randomsurprise.bad_heal_reduction_epic.desc",
				0, 0, 0, 0, 0,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				50, 0, 0, 0));
		// 红色：抑制回血V - 减少目标回血70%
		register(new Affix("bad_heal_reduction_legendary", AffixRarity.LEGENDARY, false,
				"affix.randomsurprise.bad_heal_reduction_legendary.name", "affix.randomsurprise.bad_heal_reduction_legendary.desc",
				0, 0, 0, 0, 0,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				70, 0, 0, 0));
		// 金色：抑制回血VI - 减少目标回血90%
		register(new Affix("bad_heal_reduction_mythic", AffixRarity.MYTHIC, false,
				"affix.randomsurprise.bad_heal_reduction_mythic.name", "affix.randomsurprise.bad_heal_reduction_mythic.desc",
				0, 0, 0, 0, 0,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				90, 0, 0, 0));

		// ========== v12 新增好词条（RPG词条思路：吸血/狂战士/攻击附带抑制回血）==========
		// 红色：血魔之拥 - 吸血8%（比v2的5%更高），生命+10，经验+20%
		register(new Affix("good_life_steal_legendary", AffixRarity.LEGENDARY, true,
				"affix.randomsurprise.good_life_steal_legendary.name", "affix.randomsurprise.good_life_steal_legendary.desc",
				10, 0, 0, 0, 20,
				0, 0, 0, 0,
				0.08, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 金色：狂战士 - 血量<50%时攻击力+50%，吸血5%，生命+15，经验+50%
		register(new Affix("good_berserker_mythic", AffixRarity.MYTHIC, true,
				"affix.randomsurprise.good_berserker_mythic.name", "affix.randomsurprise.good_berserker_mythic.desc",
				15, 0, 0, 0, 50,
				0, 0, 0, 0,
				0.05, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 50, 0.50));
		// 紫色：枯萎之刃 - 攻击附带抑制回血效果（被攻击目标3秒内回血减少50%），生命+8，经验+15%
		register(new Affix("good_anti_heal_epic", AffixRarity.EPIC, true,
				"affix.randomsurprise.good_anti_heal_epic.name", "affix.randomsurprise.good_anti_heal_epic.desc",
				8, 0, 0, 0, 15,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 50, 0, 0));

		// ========== 16个好词条（机制型，通过ID在PlayerAffixManager中检查） ==========
		// 1. 痛楚循环：每次攻击消耗1HP，叠加1层，满5层时额外造成5倍伤害并清零
		register(new Affix("good_pain_cycle_epic", AffixRarity.EPIC, true,
				"affix.randomsurprise.good_pain_cycle_epic.name", "affix.randomsurprise.good_pain_cycle_epic.desc",
				8, 0, 0, 0, 15,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 2. 虚空侵蚀：命中目标叠加侵蚀，每层+5%伤害，5秒不攻击清零
		register(new Affix("good_void_erosion_legendary", AffixRarity.LEGENDARY, true,
				"affix.randomsurprise.good_void_erosion_legendary.name", "affix.randomsurprise.good_void_erosion_legendary.desc",
				10, 0, 0, 0, 20,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 3. 震波：近战时30%概率在目标前方发出震波
		register(new Affix("good_shockwave_rare", AffixRarity.RARE, true,
				"affix.randomsurprise.good_shockwave_rare.name", "affix.randomsurprise.good_shockwave_rare.desc",
				6, 0, 0, 0, 10,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 4. 旋风斩：近战时30%概率对3格内所有敌对生物造成40%伤害
		register(new Affix("good_whirlwind_rare", AffixRarity.RARE, true,
				"affix.randomsurprise.good_whirlwind_rare.name", "affix.randomsurprise.good_whirlwind_rare.desc",
				6, 0, 0, 0, 10,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 5. 死亡引爆：击杀时在死亡位置3格内造成爆炸
		register(new Affix("good_death_detonate_epic", AffixRarity.EPIC, true,
				"affix.randomsurprise.good_death_detonate_epic.name", "affix.randomsurprise.good_death_detonate_epic.desc",
				8, 0, 0, 0, 15,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 6. 暗杀：目标的目标不是玩家时伤害+30%
		register(new Affix("good_ambush_uncommon", AffixRarity.UNCOMMON, true,
				"affix.randomsurprise.good_ambush_uncommon.name", "affix.randomsurprise.good_ambush_uncommon.desc",
				4, 0, 0, 0, 10,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 7. 动能蓄势：疾跑或跳跃后附加4伤害
		register(new Affix("good_momentum_rare", AffixRarity.RARE, true,
				"affix.randomsurprise.good_momentum_rare.name", "affix.randomsurprise.good_momentum_rare.desc",
				6, 0, 0, 0, 10,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 8. 破甲：20%概率给目标施加虚弱
		register(new Affix("good_armor_break_epic", AffixRarity.EPIC, true,
				"affix.randomsurprise.good_armor_break_epic.name", "affix.randomsurprise.good_armor_break_epic.desc",
				8, 0, 0, 0, 15,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 9. 削弱：攻击降低目标攻击力（虚弱II）
		register(new Affix("good_weaken_uncommon", AffixRarity.UNCOMMON, true,
				"affix.randomsurprise.good_weaken_uncommon.name", "affix.randomsurprise.good_weaken_uncommon.desc",
				4, 0, 0, 0, 5,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 10. 复仇：受击时反弹30%伤害
		register(new Affix("good_vengeance_rare", AffixRarity.RARE, true,
				"affix.randomsurprise.good_vengeance_rare.name", "affix.randomsurprise.good_vengeance_rare.desc",
				6, 0, 0, 0, 10,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 11. 守卫击杀：击杀获得3秒减伤50%
		register(new Affix("good_guard_kill_rare", AffixRarity.RARE, true,
				"affix.randomsurprise.good_guard_kill_rare.name", "affix.randomsurprise.good_guard_kill_rare.desc",
				6, 0, 0, 0, 10,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 12. 药剂渴望：击杀清除自身1个负面效果
		register(new Affix("good_potion_thirst_uncommon", AffixRarity.UNCOMMON, true,
				"affix.randomsurprise.good_potion_thirst_uncommon.name", "affix.randomsurprise.good_potion_thirst_uncommon.desc",
				4, 0, 0, 0, 5,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 13. 额外射击：弓箭射击时额外向附近敌人射出1发箭
		register(new Affix("good_extra_shot_rare", AffixRarity.RARE, true,
				"affix.randomsurprise.good_extra_shot_rare.name", "affix.randomsurprise.good_extra_shot_rare.desc",
				6, 0, 0, 0, 10,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 14. 灵魂虹吸：击杀获得灵魂，每层+2%暴击率（上限10层）
		register(new Affix("good_soul_siphon_legendary", AffixRarity.LEGENDARY, true,
				"affix.randomsurprise.good_soul_siphon_legendary.name", "affix.randomsurprise.good_soul_siphon_legendary.desc",
				10, 0, 0, 0, 20,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 15. 连锁束缚：20%概率给3格内最多3个敌人施加缓慢IV
		register(new Affix("good_chain_bind_epic", AffixRarity.EPIC, true,
				"affix.randomsurprise.good_chain_bind_epic.name", "affix.randomsurprise.good_chain_bind_epic.desc",
				8, 0, 0, 0, 15,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 16. 净化光环：每10秒清除5格内所有玩家1个负面效果
		register(new Affix("good_purify_aura_legendary", AffixRarity.LEGENDARY, true,
				"affix.randomsurprise.good_purify_aura_legendary.name", "affix.randomsurprise.good_purify_aura_legendary.desc",
				10, 0, 0, 0, 15,
				0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// ========== 20个坏词条（机制型，通过HostileEnhancer中的PersistentData绑定） ==========
		// 1. 死亡爆炸：死亡时在位置3格内爆炸
		register(new Affix("bad_death_explode_rare", AffixRarity.RARE, false,
				"affix.randomsurprise.bad_death_explode_rare.name", "affix.randomsurprise.bad_death_explode_rare.desc",
				0, 0, 0, 0, 0,
				1.0, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 2. 死亡分裂：死亡时生成2只同类型50%HP的怪物
		register(new Affix("bad_death_split_epic", AffixRarity.EPIC, false,
				"affix.randomsurprise.bad_death_split_epic.name", "affix.randomsurprise.bad_death_split_epic.desc",
				0, 0, 0, 0, 0,
				1.05, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 3. 尸体连锁：死亡爆炸会引爆4格内其他死亡怪物
		register(new Affix("bad_corpse_chain_legendary", AffixRarity.LEGENDARY, false,
				"affix.randomsurprise.bad_corpse_chain_legendary.name", "affix.randomsurprise.bad_corpse_chain_legendary.desc",
				0, 0, 0, 0, 0,
				1.08, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 4. 1UP复活：死亡后复活一次满血
		register(new Affix("bad_1up_legendary", AffixRarity.LEGENDARY, false,
				"affix.randomsurprise.bad_1up_legendary.name", "affix.randomsurprise.bad_1up_legendary.desc",
				0, 0, 0, 0, 0,
				1.05, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 5. 狂暴计时：战斗10秒后狂暴
		register(new Affix("bad_enrage_timer_epic", AffixRarity.EPIC, false,
				"affix.randomsurprise.bad_enrage_timer_epic.name", "affix.randomsurprise.bad_enrage_timer_epic.desc",
				0, 0, 0, 0, 0,
				1.05, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 6. 护盾充能：每15秒3秒免疫护盾
		register(new Affix("bad_shield_charge_epic", AffixRarity.EPIC, false,
				"affix.randomsurprise.bad_shield_charge_epic.name", "affix.randomsurprise.bad_shield_charge_epic.desc",
				0, 0, 0, 0, 0,
				1.05, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 7. 再生怪物：每秒恢复1%HP
		register(new Affix("bad_regen_monster_rare", AffixRarity.RARE, false,
				"affix.randomsurprise.bad_regen_monster_rare.name", "affix.randomsurprise.bad_regen_monster_rare.desc",
				0, 0, 0, 0, 0,
				1.05, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 1, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 8. 濒死狂暴：HP<30%时攻击+40%，速度+20%
		register(new Affix("bad_near_death_rage_uncommon", AffixRarity.UNCOMMON, false,
				"affix.randomsurprise.bad_near_death_rage_uncommon.name", "affix.randomsurprise.bad_near_death_rage_uncommon.desc",
				0, 0, 0, 0, 0,
				1.03, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 9. 末影闪现：受击20%概率瞬移到玩家身后3格
		register(new Affix("bad_ender_blink_rare", AffixRarity.RARE, false,
				"affix.randomsurprise.bad_ender_blink_rare.name", "affix.randomsurprise.bad_ender_blink_rare.desc",
				0, 0, 0, 0, 0,
				1.03, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 10. 隐身：每10秒隐身3秒
		register(new Affix("bad_cloak_epic", AffixRarity.EPIC, false,
				"affix.randomsurprise.bad_cloak_epic.name", "affix.randomsurprise.bad_cloak_epic.desc",
				0, 0, 0, 0, 0,
				1.05, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 11. 仇恨连锁：受击时5格内同类增援
		register(new Affix("bad_hate_chain_uncommon", AffixRarity.UNCOMMON, false,
				"affix.randomsurprise.bad_hate_chain_uncommon.name", "affix.randomsurprise.bad_hate_chain_uncommon.desc",
				0, 0, 0, 0, 0,
				1.03, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 12. 黑暗光环：3格内玩家获得失明3秒
		register(new Affix("bad_darkness_aura_rare", AffixRarity.RARE, false,
				"affix.randomsurprise.bad_darkness_aura_rare.name", "affix.randomsurprise.bad_darkness_aura_rare.desc",
				0, 0, 0, 0, 0,
				1.03, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 13. 雷暴召唤：每10秒在玩家位置召唤闪电
		register(new Affix("bad_storm_call_epic", AffixRarity.EPIC, false,
				"affix.randomsurprise.bad_storm_call_epic.name", "affix.randomsurprise.bad_storm_call_epic.desc",
				0, 0, 0, 0, 0,
				1.05, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 14. 火球射手：每5秒向玩家发射火球
		register(new Affix("bad_fireball_shooter_rare", AffixRarity.RARE, false,
				"affix.randomsurprise.bad_fireball_shooter_rare.name", "affix.randomsurprise.bad_fireball_shooter_rare.desc",
				0, 0, 0, 0, 0,
				1.03, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 15. 击退炮：攻击时强力击退玩家
		register(new Affix("bad_knockback_cannon_rare", AffixRarity.RARE, false,
				"affix.randomsurprise.bad_knockback_cannon_rare.name", "affix.randomsurprise.bad_knockback_cannon_rare.desc",
				0, 0, 0, 0, 0,
				1.03, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 16. 蛛网陷阱：攻击时在玩家脚下放置蛛网
		register(new Affix("bad_web_trap_uncommon", AffixRarity.UNCOMMON, false,
				"affix.randomsurprise.bad_web_trap_uncommon.name", "affix.randomsurprise.bad_web_trap_uncommon.desc",
				0, 0, 0, 0, 0,
				1.03, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 17. 燃烧光环：2格内近战玩家被点燃2秒
		register(new Affix("bad_burning_aura_rare", AffixRarity.RARE, false,
				"affix.randomsurprise.bad_burning_aura_rare.name", "affix.randomsurprise.bad_burning_aura_rare.desc",
				0, 0, 0, 0, 0,
				1.03, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 18. 凋零之触：攻击施加凋零II 3秒
		register(new Affix("bad_wither_touch_epic", AffixRarity.EPIC, false,
				"affix.randomsurprise.bad_wither_touch_epic.name", "affix.randomsurprise.bad_wither_touch_epic.desc",
				0, 0, 0, 0, 0,
				1.05, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 19. 治疗友军：每5秒为5格内友方怪物恢复5%HP
		register(new Affix("bad_heal_ally_epic", AffixRarity.EPIC, false,
				"affix.randomsurprise.bad_heal_ally_epic.name", "affix.randomsurprise.bad_heal_ally_epic.desc",
				0, 0, 0, 0, 0,
				1.05, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));
		// 20. 锈蚀攻击：攻击时损耗玩家头盔5点耐久
		register(new Affix("bad_rust_attack_uncommon", AffixRarity.UNCOMMON, false,
				"affix.randomsurprise.bad_rust_attack_uncommon.name", "affix.randomsurprise.bad_rust_attack_uncommon.desc",
				0, 0, 0, 0, 0,
				1.03, 0, 0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
				0, 0, false, 0, 0, 0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// =====================================================================
		// Boss 专用词条（20个，bossOnly=true，仅Boss生成时使用）
		// =====================================================================

		// ===== 战斗增强类 (6个) =====
		// 1. boss_enrage_common: Boss血量+5%
		register(bossAffix("boss_enrage_common", AffixRarity.COMMON, 1.05, 0, 0, 0, 0, false, 0, 0, 0, 0, 0));
		// 2. boss_thorns_legendary: Boss反伤30%
		register(bossAffix("boss_thorns_legendary", AffixRarity.LEGENDARY, 1.0, 0, 0, 0, 0, false, 0, 0, 0.3, 0, 0));
		// 3. boss_lifesteal_epic: Boss吸血15%
		register(bossAffix("boss_lifesteal_epic", AffixRarity.EPIC, 1.0, 0, 0, 0.15, 0, false, 0, 0, 0, 0, 0));
		// 4. boss_speed_rare: Boss速度+15%
		register(bossAffix("boss_speed_rare", AffixRarity.RARE, 1.0, 0, 0.15, 0, 0, false, 0, 0, 0, 0, 0));
		// 5. boss_knockback_immune_uncommon: Boss抗性I（击退免疫近似）
		register(bossAffix("boss_knockback_immune_uncommon", AffixRarity.UNCOMMON, 1.0, 0, 0, 0, 0, false, 4, 0, 0, 0, 0));
		// 6. boss_armor_epic: Boss护甲减免30%
		register(bossAffix("boss_armor_epic", AffixRarity.EPIC, 1.0, 0, 0, 0, 0, false, 0, 0, 0, 0.3, 0));

		// ===== 特殊技能类 (8个) - 机制型词条，通过 HostileEnhancer 实现 =====
		// 7. boss_teleport_rare: Boss受击闪现（HostileEnhancer实现）
		register(bossAffix("boss_teleport_rare", AffixRarity.RARE, 1.03, 0, 0, 0, 0, false, 0, 0, 0, 0, 0));
		// 8. boss_fire_trail_uncommon: Boss留下火焰轨迹（HostileEnhancer实现）
		register(bossAffix("boss_fire_trail_uncommon", AffixRarity.UNCOMMON, 1.03, 0, 0, 0, 0, false, 0, 0, 0, 0, 0));
		// 9. boss_summon_common: Boss召唤同伴10%
		register(bossAffix("boss_summon_common", AffixRarity.COMMON, 1.03, 0, 0, 0, 0.1, false, 0, 0, 0, 0, 0));
		// 10. boss_lightning_strike_epic: Boss周围召唤闪电（HostileEnhancer实现）
		register(bossAffix("boss_lightning_strike_epic", AffixRarity.EPIC, 1.03, 0, 0, 0, 0, false, 0, 0, 0, 0, 0));
		// 11. boss_frost_aura_uncommon: Boss冰霜光环减速附近玩家（HostileEnhancer实现）
		register(bossAffix("boss_frost_aura_uncommon", AffixRarity.UNCOMMON, 1.03, 0, 0, 0, 0, false, 0, 0, 0, 0, 0));
		// 12. boss_wither_aura_rare: Boss凋零光环（HostileEnhancer实现）
		register(bossAffix("boss_wither_aura_rare", AffixRarity.RARE, 1.03, 0, 0, 0, 0, false, 0, 0, 0, 0, 0));
		// 13. boss_reflect_legendary: Boss反伤40%（高级版）
		register(bossAffix("boss_reflect_legendary", AffixRarity.LEGENDARY, 1.0, 0, 0, 0, 0, false, 0, 0, 0.4, 0, 0));
		// 14. boss_rage_mythic: Boss全属性+10%（HP/ATK/SPD）
		register(bossAffix("boss_rage_mythic", AffixRarity.MYTHIC, 1.1, 0.1, 0.1, 0, 0, false, 0, 0, 0, 0, 0));

		// ===== 防御机制类 (6个) =====
		// 15. boss_regen_uncommon: Boss生命恢复I
		register(bossAffix("boss_regen_uncommon", AffixRarity.UNCOMMON, 1.03, 0, 0, 0, 0, false, 0, 1, 0, 0, 0));
		// 16. boss_shield_rare: Boss抗性II（护盾近似）
		register(bossAffix("boss_shield_rare", AffixRarity.RARE, 1.03, 0, 0, 0, 0, false, 2, 0, 0, 0, 0));
		// 17. boss_fire_immune_common: Boss火焰免疫
		register(bossAffix("boss_fire_immune_common", AffixRarity.COMMON, 1.03, 0, 0, 0, 0, true, 0, 0, 0, 0, 0));
		// 18. boss_heal_reduction_epic: Boss抑制回血30%
		register(bossAffix("boss_heal_reduction_epic", AffixRarity.EPIC, 1.03, 0, 0, 0, 0, false, 0, 0, 0, 0, 30));
		// 19. boss_strength_legendary: Boss血量+5%
		register(bossAffix("boss_strength_legendary", AffixRarity.LEGENDARY, 1.05, 0, 0, 0, 0, false, 0, 0, 0, 0, 0));
		// 20. boss_executioner_rare: Boss+5%攻击，+5%速度，+3%HP
		register(bossAffix("boss_executioner_rare", AffixRarity.RARE, 1.03, 0.05, 0.05, 0, 0, false, 0, 0, 0, 0, 0));

		// ========== v19: Infernal Mobs 联动词条 ==========
		// 参考 AtomicStryker's Infernal Mobs 经典能力，用现有属性字段实现

		// --- 白色 (Common) 级 Infernal词条 ---
		// 1. im_quicksand_common - 流沙（缓慢效果近似→速度-10% + 敌方护甲+3%）
		register(new Affix("im_quicksand_common", AffixRarity.COMMON, false,
				"affix.randomsurprise.im_quicksand.name", "affix.randomsurprise.im_quicksand.desc",
				0, 0, 0, 0, 0,
				1.0, 1.0, 0.90, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0.03, 0, 0, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// 2. im_poisonous_common - 有毒（攻击附带中毒→伤害+3% + 护甲+3%）
		register(new Affix("im_poisonous_common", AffixRarity.COMMON, false,
				"affix.randomsurprise.im_poisonous.name", "affix.randomsurprise.im_poisonous.desc",
				0, 0, 0, 0, 0,
				1.0, 1.03, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0.03, 0, 0, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// 3. im_weakness_common - 虚弱（降低玩家攻击→敌方护甲+5%）
		register(new Affix("im_weakness_common", AffixRarity.COMMON, false,
				"affix.randomsurprise.im_weakness.name", "affix.randomsurprise.im_weakness.desc",
				0, 0, 0, 0, 0,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0.05, 0, 0, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// --- 绿色 (Uncommon) 级 Infernal词条 ---
		// 4. im_sprint_uncommon - 冲刺（速度大幅+15% + 伤害+3%）
		register(new Affix("im_sprint_uncommon", AffixRarity.UNCOMMON, false,
				"affix.randomsurprise.im_sprint.name", "affix.randomsurprise.im_sprint.desc",
				0, 0, 0, 0, 0,
				1.0, 1.03, 1.15, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// 5. im_fiery_uncommon - 炽热（攻击点燃→火伤减免+20% + 伤害+5%）
		register(new Affix("im_fiery_uncommon", AffixRarity.UNCOMMON, false,
				"affix.randomsurprise.im_fiery.name", "affix.randomsurprise.im_fiery.desc",
				0, 0, 0, 0, 0,
				1.0, 1.05, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0.20, 0, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// 6. im_choke_uncommon - 窒息（敌方恢复I + 血量+3% + 火焰免疫）
		register(new Affix("im_choke_uncommon", AffixRarity.UNCOMMON, false,
				"affix.randomsurprise.im_choke.name", "affix.randomsurprise.im_choke.desc",
				0, 0, 0, 0, 0,
				1.03, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, true, 0,
				0, 0, 0, 1, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// 7. im_sapper_uncommon - 工兵（饥饿效果近似→伤害+5% + 生命恢复I）
		register(new Affix("im_sapper_uncommon", AffixRarity.UNCOMMON, false,
				"affix.randomsurprise.im_sapper.name", "affix.randomsurprise.im_sapper.desc",
				0, 0, 0, 0, 0,
				1.0, 1.05, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 1, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// --- 蓝色 (Rare) 级 Infernal词条 ---
		// 8. im_bulwark_rare - 堡垒（50%减伤近似→护甲%15% + 血量+5%）
		register(new Affix("im_bulwark_rare", AffixRarity.RARE, false,
				"affix.randomsurprise.im_bulwark.name", "affix.randomsurprise.im_bulwark.desc",
				0, 0, 0, 0, 0,
				1.05, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 2,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0.15, 0, 0, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// 9. im_regen_rare - 再生（生命恢复II + 血量+5%）
		register(new Affix("im_regen_rare", AffixRarity.RARE, false,
				"affix.randomsurprise.im_regen.name", "affix.randomsurprise.im_regen.desc",
				0, 0, 0, 0, 0,
				1.05, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 2, 0,
				0, 0, null, 0, 0.05, 0, 0, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// 10. im_gravity_rare - 重力（击退+反伤10% + 伤害+5%）
		register(new Affix("im_gravity_rare", AffixRarity.RARE, false,
				"affix.randomsurprise.im_gravity.name", "affix.randomsurprise.im_gravity.desc",
				0, 0, 0, 0, 0,
				1.0, 1.05, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0.10,
				0, 0, null, 0, 0.05, 0, 0, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// 11. im_webber_rare - 织网（召唤+5% + 护甲+5%）
		register(new Affix("im_webber_rare", AffixRarity.RARE, false,
				"affix.randomsurprise.im_webber.name", "affix.randomsurprise.im_webber.desc",
				0, 0, 0, 0, 0,
				1.0, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0.05, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0.05, 0, 0, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// --- 紫色 (Epic) 级 Infernal词条 ---
		// 12. im_berserk_epic - 狂暴（伤害翻倍近似→攻击+25% + 自身受伤-5%）
		register(new Affix("im_berserk_epic", AffixRarity.EPIC, false,
				"affix.randomsurprise.im_berserk.name", "affix.randomsurprise.im_berserk.desc",
				0, 0, 0, 0, 0,
				0.95, 1.25, 1.0, 1,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// 13. im_ninja_epic - 忍者（闪避+瞬移近似→反伤15% + 速度+10% + 护甲+5%）
		register(new Affix("im_ninja_epic", AffixRarity.EPIC, false,
				"affix.randomsurprise.im_ninja.name", "affix.randomsurprise.im_ninja.desc",
				0, 0, 0, 0, 0,
				1.0, 1.0, 1.10, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0.15,
				0, 0, null, 0, 0.05, 0, 0, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// 14. im_storm_epic - 闪电风暴（闪电免疫+冰霜减免+血量+8% + 护甲+8%）
		register(new Affix("im_storm_epic", AffixRarity.EPIC, false,
				"affix.randomsurprise.im_storm.name", "affix.randomsurprise.im_storm.desc",
				0, 0, 0, 0, 0,
				1.08, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 2,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0.08, 0, 0, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0.30, 0.20, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// 15. im_darkness_epic - 黑暗（致盲近似→冰霜减免+反伤8% + 速度+10%）
		register(new Affix("im_darkness_epic", AffixRarity.EPIC, false,
				"affix.randomsurprise.im_darkness.name", "affix.randomsurprise.im_darkness.desc",
				0, 0, 0, 0, 0,
				1.0, 1.0, 1.10, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0.08,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// --- 红色 (Legendary) 级 Infernal词条 ---
		// 16. im_vengeance_legendary - 复仇（伤害反射近似→反伤25% + 伤害+10%）
		register(new Affix("im_vengeance_legendary", AffixRarity.LEGENDARY, false,
				"affix.randomsurprise.im_vengeance.name", "affix.randomsurprise.im_vengeance.desc",
				0, 0, 0, 0, 0,
				1.0, 1.10, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0.25,
				0, 0, null, 0, 0.10, 0, 0, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// 17. im_lifesteal_legendary - 吸血（吸血12% + 血量+8% + 伤害+8%）
		register(new Affix("im_lifesteal_legendary", AffixRarity.LEGENDARY, false,
				"affix.randomsurprise.im_lifesteal.name", "affix.randomsurprise.im_lifesteal.desc",
				0, 0, 0, 0, 0,
				1.08, 1.08, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0.12, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// 18. im_1up_legendary - 复活（生命恢复II + 血量+15% + 护甲+10% + 火焰免疫）
		register(new Affix("im_1up_legendary", AffixRarity.LEGENDARY, false,
				"affix.randomsurprise.im_1up.name", "affix.randomsurprise.im_1up.desc",
				0, 0, 0, 0, 0,
				1.15, 1.0, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, true, 2,
				0, 0, 0, 2, 0,
				0, 0, null, 0, 0.10, 0, 0, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// --- 金色 (Mythic) 级 Infernal词条 ---
		// 19. im_alchemist_mythic - 炼金术士（综合debuff→全属性增强 + 火焰免疫 + 护甲+15%）
		register(new Affix("im_alchemist_mythic", AffixRarity.MYTHIC, false,
				"affix.randomsurprise.im_alchemist.name", "affix.randomsurprise.im_alchemist.desc",
				0, 0, 0, 0, 0,
				1.10, 1.10, 1.10, 2,
				0, 0, false, false, false, 0, 0, null,
				0, 0.05, true, 0,
				0, 0, 0, 1, 0,
				0, 0, null, 0, 0.15, 0.20, 0.10, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// 20. im_ender_mythic - 末影瞬移（闪避+反伤+速度→全属性 + 反伤20% + 速度+15% + 护甲+12%）
		register(new Affix("im_ender_mythic", AffixRarity.MYTHIC, false,
				"affix.randomsurprise.im_ender.name", "affix.randomsurprise.im_ender.desc",
				0, 0, 0, 0, 0,
				1.0, 1.05, 1.15, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0.20,
				0, 0, null, 0, 0.12, 0, 0, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				0, 0, 0, 0));

		// 21. im_rust_mythic - 装备腐蚀（反伤+抑制回血→反伤15% + 护甲+15% + 抑制回血20%）
		register(new Affix("im_rust_mythic", AffixRarity.MYTHIC, false,
				"affix.randomsurprise.im_rust.name", "affix.randomsurprise.im_rust.desc",
				0, 0, 0, 0, 0,
				1.05, 1.05, 1.0, 0,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0.15,
				0, 0, null, 0, 0.15, 0, 0, 0, 0, false, 0, 0,
				0, 0,
				0, 0, 0, 0, 0,
				0, 0, 0,
				0, 0, 0,
				20, 0, 0, 0));

		// 所有词条注册完成，将按稀有度分组的缓存列表冻结为不可变，供 getByRarity O(1) 返回
		RARITY_CACHE.replaceAll((key, list) -> Collections.unmodifiableList(list));
	}

	private static void register(Affix affix) {
		ALL_AFFIXES.add(affix);
		ID_MAP.put(affix.getId(), affix);
		RARITY_CACHE.computeIfAbsent(affix.isGood() + "_" + affix.getRarity().name(), k -> new ArrayList<>()).add(affix);
	}

	/**
	 * 随机抽取一个词条
	 * 先决定好坏（70%好/30%坏），再在对应稀有度中roll
	 * 同一稀有度有多个词条时，随机选一个
	 *
	 * 稀有奖励提升：全服坏词条越多，高稀有度权重越高
	 * - 每 5 个坏词条，高稀有度 +1%（上限 +10%）
	 * - 提升量从白色权重中扣除，紫色/红色/金色 按 4:3:3 分配
	 */
	public static Affix rollAffix() {
		boolean good = RANDOM.nextDouble() < GOOD_CHANCE;

		// 计算坏词条对稀有度的提升
		int globalBadCount = PlayerAffixManager.getGlobalBadAffixCount();
		double rarityBonus = Math.min(0.15, (globalBadCount / 5.0) * 0.01);

		// 动态权重
		double whiteW   = 0.40 - rarityBonus;
		double greenW   = 0.25;
		double blueW    = 0.15;
		double purpleW  = 0.10 + rarityBonus * 0.4;
		double redW     = 0.06 + rarityBonus * 0.3;
		double goldW    = 0.04 + rarityBonus * 0.3;

		// 按权重 roll 稀有度
		double roll = RANDOM.nextDouble();
		double cumulative = 0;
		AffixRarity targetRarity;
		cumulative += whiteW;
		if (roll < cumulative) { targetRarity = AffixRarity.COMMON; }
		else {
			cumulative += greenW;
			if (roll < cumulative) { targetRarity = AffixRarity.UNCOMMON; }
			else {
				cumulative += blueW;
				if (roll < cumulative) { targetRarity = AffixRarity.RARE; }
				else {
					cumulative += purpleW;
					if (roll < cumulative) { targetRarity = AffixRarity.EPIC; }
					else {
						cumulative += redW;
						if (roll < cumulative) { targetRarity = AffixRarity.LEGENDARY; }
						else { targetRarity = AffixRarity.MYTHIC; }
					}
				}
			}
		}

		// 在该 (good, rarity) 组合的词条中随机选一个
		if (good) {
			// 好词条不再独占：从目标稀有度的所有好词条中随机选择
			Affix goodAffix = findAffix(true, targetRarity);
			if (goodAffix != null) return goodAffix;
			// 该稀有度没有好词条 → 降级到坏词条
			good = false;
		}
		Affix affix = findAffix(good, targetRarity);
		if (affix == null) affix = ALL_AFFIXES.get(0);  // 兜底
		return affix;
	}

	/**
	 * 查找可用的好词条（排除已被任何玩家获得的好词条）
	 * 若目标稀有度无可用词条，降级到更低稀有度依次尝试
	 * @return 可用的好词条，若所有稀有度均无可用则返回 null
	 */
	private static Affix findAvailableGoodAffix(AffixRarity rarity) {
		// 从目标稀有度向下依次尝试（MYTHIC→...→COMMON）
		for (int ord = rarity.ordinal(); ord >= 0; ord--) {
			AffixRarity r = AffixRarity.values()[ord];
			List<Affix> matches = new ArrayList<>();
			for (Affix affix : ALL_AFFIXES) {
				if (affix.isGood() && affix.getRarity() == r
						&& !PlayerAffixManager.isGoodAffixTaken(affix.getId())) {
					matches.add(affix);
				}
			}
			if (!matches.isEmpty()) {
				return matches.get(RANDOM.nextInt(matches.size()));
			}
		}
		return null;  // 所有稀有度的好词条都已被占用
	}

	/**
	 * 根据好坏和稀有度查找词条（排除Boss专属词条）
	 * 同一组合有多个词条时随机选一个
	 */
	private static Affix findAffix(boolean good, AffixRarity rarity) {
		List<Affix> matches = new ArrayList<>();
		for (Affix affix : ALL_AFFIXES) {
			if (affix.isGood() == good && affix.getRarity() == rarity && !affix.isBossOnly()) {
				matches.add(affix);
			}
		}
		if (matches.isEmpty()) return null;  // 调用方处理降级/兜底
		return matches.get(RANDOM.nextInt(matches.size()));
	}

	/**
	 * 根据稀有度查找Boss专属坏词条（仅返回 bossOnly=true 的坏词条）
	 * 用于Boss生成时roll专属词条
	 */
	private static Affix findBossAffix(AffixRarity rarity) {
		List<Affix> matches = new ArrayList<>();
		for (Affix affix : ALL_AFFIXES) {
			if (!affix.isGood() && affix.getRarity() == rarity && affix.isBossOnly()) {
				matches.add(affix);
			}
		}
		if (matches.isEmpty()) return null;
		return matches.get(RANDOM.nextInt(matches.size()));
	}

	/**
	 * 随机抽取一个Boss专属词条（仅返回坏词条中 bossOnly=true 的词条）
	 * 用于Boss生成时的词条roll
	 *
	 * @return Boss专属词条，若无可用词条则返回null
	 */
	public static Affix rollBossAffix() {
		// Boss词条稀有度权重
		double whiteW   = 0.25;
		double greenW   = 0.30;
		double blueW    = 0.20;
		double purpleW  = 0.12;
		double redW     = 0.08;
		double goldW    = 0.05;

		double roll = RANDOM.nextDouble();
		double cumulative = 0;
		AffixRarity targetRarity;
		cumulative += whiteW;
		if (roll < cumulative) { targetRarity = AffixRarity.COMMON; }
		else {
			cumulative += greenW;
			if (roll < cumulative) { targetRarity = AffixRarity.UNCOMMON; }
			else {
				cumulative += blueW;
				if (roll < cumulative) { targetRarity = AffixRarity.RARE; }
				else {
					cumulative += purpleW;
					if (roll < cumulative) { targetRarity = AffixRarity.EPIC; }
					else {
						cumulative += redW;
						if (roll < cumulative) { targetRarity = AffixRarity.LEGENDARY; }
						else { targetRarity = AffixRarity.MYTHIC; }
					}
				}
			}
		}

		// 在目标稀有度的Boss词条中随机选一个
		Affix affix = findBossAffix(targetRarity);
		if (affix == null) {
			// 该稀有度无Boss词条，降级尝试
			for (int ord = targetRarity.ordinal() - 1; ord >= 0; ord--) {
				affix = findBossAffix(AffixRarity.values()[ord]);
				if (affix != null) break;
			}
		}
		return affix;
	}

	/**
	 * 根据 id 查找词条（O(1) HashMap 索引）
	 */
	public static Affix getById(String id) {
		return ID_MAP.get(id);
	}

	public static List<Affix> getAll() {
		return ALL_AFFIXES;
	}

	/**
	 * 获取指定稀有度的所有词条（按好坏分组）
	 * 直接返回预建缓存的不可变列表，O(1)
	 */
	public static List<Affix> getByRarity(boolean good, AffixRarity rarity) {
		return RARITY_CACHE.getOrDefault(good + "_" + rarity.name(), Collections.emptyList());
	}
}
