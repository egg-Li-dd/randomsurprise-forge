package com.randomsurprise.affix;

/**
 * 词条定义
 * good=true: 增强玩家（好词条）
 * good=false: 增强敌对生物（坏词条）
 *
 * 新增效果字段（v2）：
 * - 吸血 lifestealPercent：攻击时按伤害百分比回血
 * - 护盾 absorptionBonus：吸收点数（用 Absorption 效果模拟）
 * - 免疫 fireImmunity/drownImmunity/fallImmunity：对应抗性药水
 * - 武器伤害 weaponDamageBonus：额外力量等级
 * - 召唤 summonChance/summonMobId：受击时概率召唤友方生物
 * - 敌对吸血/召唤/免疫/护甲
 */
public class Affix {
	private final String id;
	private final AffixRarity rarity;
	private final boolean good;
	private final String nameKey;
	private final String descKey;

	// 好词条效果（玩家增强）
	private final int healthBonus;       // 最大生命+
	private final int speedAmplifier;     // 速度药水等级（定期刷新）
	private final int strengthAmplifier; // 力量药水等级
	private final int resistanceAmplifier; // 抗性药水等级
	private final int expBonusPercent;   // 经验获取+%

	// 坏词条效果（敌对生物增强，全局影响）
	private final double hostileHealthMult;  // 怪物血量倍率
	private final double hostileDamageMult;   // 怪物攻击倍率
	private final double hostileSpeedMult;    // 怪物速度倍率
	private final int hostileEffectAmplifier; // 怪物额外药水效果等级

	// ========== 新增效果字段（v2）==========
	// 好词条：特殊效果
	private final double lifestealPercent;  // 吸血百分比（0=无）
	private final int absorptionBonus;      // 吸收护盾点数（0=无）
	private final boolean fireImmunity;      // 火焰免疫
	private final boolean drownImmunity;     // 溺水免疫
	private final boolean fallImmunity;      // 摔落免疫
	private final int weaponDamageBonus;    // 武器额外伤害等级（叠加到力量）
	private final double summonChance;       // 受击时召唤友方生物概率（0~1）
	private final String summonMobId;        // 召唤的生物ID（如 "minecraft:iron_golem"）

	// 坏词条：特殊效果
	private final double hostileLifestealPercent;  // 敌对生物吸血
	private final double hostileSummonChance;       // 敌对生物召唤同伴概率
	private final boolean hostileFireImmunity;      // 敌对生物火焰免疫
	private final int hostileArmorLevel;           // 敌对生物护甲（抗性等级）

	// ========== 新增效果字段（v3）==========
	// 好词条：特殊效果
	private final int hasteAmplifier;          // 急迫药水等级（0=无）
	private final int regenAmplifier;          // 生命恢复药水等级（0=无）
	private final double bossDamageBonus;      // 对Boss伤害加成（0=无，0.15=+15%）

	// 坏词条：特殊效果
	private final int hostileRegenLevel;       // 敌对生物生命恢复等级（0=无）
	private final double hostileThornsReflect; // 敌对生物反伤百分比（0=无，0.10=反伤10%）

	// ========== 新增效果字段（v4）==========
	// 好词条：特殊效果
	private final int absorptionCooldownSeconds; // 护盾冷却时间（秒，0=无冷却）
	private final double lightningDamage;         // 攻击时闪电伤害（0=无，0.5=0.5心）
	private final String weaponTypeBonus;         // 武器类型被动（sword/bow/axe/pickaxe/trident，null=无）
	private final double weaponDamagePercent;    // 武器类型伤害加成（0=无，0.10=+10%）

	// 坏词条：特殊效果
	private final double hostileArmorPercent;    // 敌对生物护甲百分比减免（每级1%，0=无）

	// ========== 新增效果字段（v5）==========
	// 好词条：火焰伤害减免百分比（0=无，0.5=减50%火焰伤害，1.0=免疫）
	private final double fireResistancePercent;
	// 坏词条：敌对生物火焰伤害减免百分比
	private final double hostileFireResistancePercent;

	// ========== 新增效果字段（v6）==========
	// 元素伤害体系统一：火焰/冰霜/闪电三种元素，每种含伤害+减免+免疫+攻击附加状态
	private final double fireDamage;                  // 火焰伤害（攻击时触发，0=无）
	private final double frostDamage;                 // 冰霜伤害（攻击时触发，0=无）
	private final boolean frostImmunity;              // 冰霜免疫（免疫减速效果）
	private final double frostResistancePercent;      // 冰霜伤害减免（0~1）
	private final double lightningResistancePercent;  // 闪电伤害减免（0~1）
	private final double fireChanceOnAttack;          // 攻击时点燃概率（0~1）
	private final double frostChanceOnAttack;         // 攻击时减速概率（0~1）
	private final double fireBurnStackBonus;          // 火焰燃烧堆叠加成（每次攻击增加的燃烧伤害，0=无，0.5=每次+0.5）

	// ========== 新增效果字段（v7）==========
	// 好词条：独立属性加成（不依赖原版药水效果，用 AttributeModifier/事件实现）
	private final double miningSpeedBonus;            // 挖掘速度加成（0=无，0.15=+15%）
	private final double attackSpeedBonus;            // 攻击速度加成（0=无，0.20=+20%）
	private final double moveSpeedBonus;              // 移动速度加成（0=无，0.10=+10%，区别于速度药水）
	private final double critChance;                  // 暴击概率（0=无，0.08=8%，触发时造成2倍伤害）
	private final double healBonus;                   // 治疗加成（0=无，0.30=+30%所有治疗量）

	// ========== 新增效果字段（v10）==========
	// 好词条：独立属性（替代原版药水效果的新版本）
	private final double moveSpeedPercent;            // 移速百分比加成（0=无，0.10=+10%）
	private final double baseAttackDamage;            // 基础攻击力加成（0=无，1.0=+1心）
	private final double hpPerSecond;                 // 每秒回血量（0=无，0.5=每秒回0.5心）
	// 坏词条：元素抗性扩展
	private final double hostileFrostResistancePercent;       // 敌对冰霜减免%（0~1）
	private final double hostileLightningResistancePercent;   // 敌对闪电减免%（0~1）

	// ========== 新增效果字段（v8）==========
	// 好词条：自然回血加成（0=无，0.5=+50%饱食自然回血，不显示药水图标，不受牛奶清除）
	private final double naturalRegenBonus;

	// ========== 新增效果字段（v9）==========
	// 好词条：失明免疫率（0=无，0.30=30%概率免疫失明效果）
	private final double blindnessResistancePercent;

	// ========== 新增效果字段（v11）==========
	// 好词条：元素百分比伤害（基于目标最大生命值）
	private final double lightningPercentDamage;        // 闪电百分比伤害（0=无，0.05=对最大生命值5%伤害）
	private final double frostFreezeChance;             // 冰霜冻结概率（0=无，0.30=30%概率冻结目标）
	private final double firePercentDamagePerSecond;   // 火焰每秒百分比伤害（0=无，0.02=每秒对最大生命值2%伤害）

	// ========== 新增效果字段（v12）==========
	/** v12: 抑制回血（坏词条：减少目标回血量百分比） */
	private final int healReductionPercent;             // 抑制回血百分比（0=无，15=减少15%回血量）
	/** v12: 攻击附带抑制回血（好词条） */
	private final int antiHealOnHitPercent;             // 攻击附带抑制回血百分比（0=无，50=减少50%回血量，持续3秒）
	/** v12: 狂战士触发血量阈值（0=不触发） */
	private final int berserkerThreshold;               // 狂战士触发血量阈值（0=不触发，50=血量<50%时触发）
	/** v12: 狂战士攻击力加成 */
	private final double berserkerDamageBonus;           // 狂战士攻击力加成（0=无，0.50=+50%伤害）

	// ========== 新增效果字段（v13）==========
	/** v13: Boss专属词条标记（true=仅Boss可获得的词条，不影响普通怪物） */
	private final boolean bossOnly;                      // Boss专属词条（true=仅Boss获得，普通怪物roll词条时跳过）

	/**
	 * 完整构造器（v12，包含所有效果）
	 */
	public Affix(String id, AffixRarity rarity, boolean good, String nameKey, String descKey,
			int healthBonus, int speedAmplifier, int strengthAmplifier, int resistanceAmplifier, int expBonusPercent,
			double hostileHealthMult, double hostileDamageMult, double hostileSpeedMult, int hostileEffectAmplifier,
			double lifestealPercent, int absorptionBonus, boolean fireImmunity, boolean drownImmunity,
			boolean fallImmunity, int weaponDamageBonus, double summonChance, String summonMobId,
			double hostileLifestealPercent, double hostileSummonChance, boolean hostileFireImmunity,
			int hostileArmorLevel,
			int hasteAmplifier, int regenAmplifier, double bossDamageBonus,
			int hostileRegenLevel, double hostileThornsReflect,
			int absorptionCooldownSeconds, double lightningDamage, String weaponTypeBonus, double weaponDamagePercent,
			double hostileArmorPercent, double fireResistancePercent, double hostileFireResistancePercent,
			double fireDamage, double frostDamage, boolean frostImmunity,
			double frostResistancePercent, double lightningResistancePercent,
			double fireChanceOnAttack, double frostChanceOnAttack,
			double fireBurnStackBonus,
			double miningSpeedBonus, double attackSpeedBonus, double moveSpeedBonus,
			double critChance, double healBonus,
			double hostileFrostResistancePercent, double hostileLightningResistancePercent,
			double naturalRegenBonus,
			double blindnessResistancePercent,
			double moveSpeedPercent, double baseAttackDamage, double hpPerSecond,
			double lightningPercentDamage, double frostFreezeChance, double firePercentDamagePerSecond,
			int healReductionPercent, int antiHealOnHitPercent, int berserkerThreshold, double berserkerDamageBonus) {
		this.id = id;
		this.rarity = rarity;
		this.good = good;
		this.nameKey = nameKey;
		this.descKey = descKey;
		this.healthBonus = healthBonus;
		this.speedAmplifier = speedAmplifier;
		this.strengthAmplifier = strengthAmplifier;
		this.resistanceAmplifier = resistanceAmplifier;
		this.expBonusPercent = expBonusPercent;
		this.hostileHealthMult = hostileHealthMult;
		this.hostileDamageMult = hostileDamageMult;
		this.hostileSpeedMult = hostileSpeedMult;
		this.hostileEffectAmplifier = hostileEffectAmplifier;
		this.lifestealPercent = lifestealPercent;
		this.absorptionBonus = absorptionBonus;
		this.fireImmunity = fireImmunity;
		this.drownImmunity = drownImmunity;
		this.fallImmunity = fallImmunity;
		this.weaponDamageBonus = weaponDamageBonus;
		this.summonChance = summonChance;
		this.summonMobId = summonMobId;
		this.hostileLifestealPercent = hostileLifestealPercent;
		this.hostileSummonChance = hostileSummonChance;
		this.hostileFireImmunity = hostileFireImmunity;
		this.hostileArmorLevel = hostileArmorLevel;
		this.hasteAmplifier = hasteAmplifier;
		this.regenAmplifier = regenAmplifier;
		this.bossDamageBonus = bossDamageBonus;
		this.hostileRegenLevel = hostileRegenLevel;
		this.hostileThornsReflect = hostileThornsReflect;
		this.absorptionCooldownSeconds = absorptionCooldownSeconds;
		this.lightningDamage = lightningDamage;
		this.weaponTypeBonus = weaponTypeBonus;
		this.weaponDamagePercent = weaponDamagePercent;
		this.hostileArmorPercent = hostileArmorPercent;
		this.fireResistancePercent = fireResistancePercent;
		this.hostileFireResistancePercent = hostileFireResistancePercent;
		this.fireDamage = fireDamage;
		this.frostDamage = frostDamage;
		this.frostImmunity = frostImmunity;
		this.frostResistancePercent = frostResistancePercent;
		this.lightningResistancePercent = lightningResistancePercent;
		this.fireChanceOnAttack = fireChanceOnAttack;
		this.frostChanceOnAttack = frostChanceOnAttack;
		this.fireBurnStackBonus = fireBurnStackBonus;
		this.miningSpeedBonus = miningSpeedBonus;
		this.attackSpeedBonus = attackSpeedBonus;
		this.moveSpeedBonus = moveSpeedBonus;
		this.critChance = critChance;
		this.healBonus = healBonus;
		this.hostileFrostResistancePercent = hostileFrostResistancePercent;
		this.hostileLightningResistancePercent = hostileLightningResistancePercent;
		this.naturalRegenBonus = naturalRegenBonus;
		this.blindnessResistancePercent = blindnessResistancePercent;
		this.moveSpeedPercent = moveSpeedPercent;
		this.baseAttackDamage = baseAttackDamage;
		this.hpPerSecond = hpPerSecond;
		this.lightningPercentDamage = lightningPercentDamage;
		this.frostFreezeChance = frostFreezeChance;
		this.firePercentDamagePerSecond = firePercentDamagePerSecond;
		this.healReductionPercent = healReductionPercent;
		this.antiHealOnHitPercent = antiHealOnHitPercent;
		this.berserkerThreshold = berserkerThreshold;
		this.berserkerDamageBonus = berserkerDamageBonus;
		this.bossOnly = false;
	}

	/**
	 * v13 构造器（v12 + bossOnly 参数）
	 */
	public Affix(String id, AffixRarity rarity, boolean good, String nameKey, String descKey,
			int healthBonus, int speedAmplifier, int strengthAmplifier, int resistanceAmplifier, int expBonusPercent,
			double hostileHealthMult, double hostileDamageMult, double hostileSpeedMult, int hostileEffectAmplifier,
			double lifestealPercent, int absorptionBonus, boolean fireImmunity, boolean drownImmunity,
			boolean fallImmunity, int weaponDamageBonus, double summonChance, String summonMobId,
			double hostileLifestealPercent, double hostileSummonChance, boolean hostileFireImmunity,
			int hostileArmorLevel,
			int hasteAmplifier, int regenAmplifier, double bossDamageBonus,
			int hostileRegenLevel, double hostileThornsReflect,
			int absorptionCooldownSeconds, double lightningDamage, String weaponTypeBonus, double weaponDamagePercent,
			double hostileArmorPercent, double fireResistancePercent, double hostileFireResistancePercent,
			double fireDamage, double frostDamage, boolean frostImmunity,
			double frostResistancePercent, double lightningResistancePercent,
			double fireChanceOnAttack, double frostChanceOnAttack,
			double fireBurnStackBonus,
			double miningSpeedBonus, double attackSpeedBonus, double moveSpeedBonus,
			double critChance, double healBonus,
			double hostileFrostResistancePercent, double hostileLightningResistancePercent,
			double naturalRegenBonus,
			double blindnessResistancePercent,
			double moveSpeedPercent, double baseAttackDamage, double hpPerSecond,
			double lightningPercentDamage, double frostFreezeChance, double firePercentDamagePerSecond,
			int healReductionPercent, int antiHealOnHitPercent, int berserkerThreshold, double berserkerDamageBonus,
			boolean bossOnly) {
		this.id = id;
		this.rarity = rarity;
		this.good = good;
		this.nameKey = nameKey;
		this.descKey = descKey;
		this.healthBonus = healthBonus;
		this.speedAmplifier = speedAmplifier;
		this.strengthAmplifier = strengthAmplifier;
		this.resistanceAmplifier = resistanceAmplifier;
		this.expBonusPercent = expBonusPercent;
		this.hostileHealthMult = hostileHealthMult;
		this.hostileDamageMult = hostileDamageMult;
		this.hostileSpeedMult = hostileSpeedMult;
		this.hostileEffectAmplifier = hostileEffectAmplifier;
		this.lifestealPercent = lifestealPercent;
		this.absorptionBonus = absorptionBonus;
		this.fireImmunity = fireImmunity;
		this.drownImmunity = drownImmunity;
		this.fallImmunity = fallImmunity;
		this.weaponDamageBonus = weaponDamageBonus;
		this.summonChance = summonChance;
		this.summonMobId = summonMobId;
		this.hostileLifestealPercent = hostileLifestealPercent;
		this.hostileSummonChance = hostileSummonChance;
		this.hostileFireImmunity = hostileFireImmunity;
		this.hostileArmorLevel = hostileArmorLevel;
		this.hasteAmplifier = hasteAmplifier;
		this.regenAmplifier = regenAmplifier;
		this.bossDamageBonus = bossDamageBonus;
		this.hostileRegenLevel = hostileRegenLevel;
		this.hostileThornsReflect = hostileThornsReflect;
		this.absorptionCooldownSeconds = absorptionCooldownSeconds;
		this.lightningDamage = lightningDamage;
		this.weaponTypeBonus = weaponTypeBonus;
		this.weaponDamagePercent = weaponDamagePercent;
		this.hostileArmorPercent = hostileArmorPercent;
		this.fireResistancePercent = fireResistancePercent;
		this.hostileFireResistancePercent = hostileFireResistancePercent;
		this.fireDamage = fireDamage;
		this.frostDamage = frostDamage;
		this.frostImmunity = frostImmunity;
		this.frostResistancePercent = frostResistancePercent;
		this.lightningResistancePercent = lightningResistancePercent;
		this.fireChanceOnAttack = fireChanceOnAttack;
		this.frostChanceOnAttack = frostChanceOnAttack;
		this.fireBurnStackBonus = fireBurnStackBonus;
		this.miningSpeedBonus = miningSpeedBonus;
		this.attackSpeedBonus = attackSpeedBonus;
		this.moveSpeedBonus = moveSpeedBonus;
		this.critChance = critChance;
		this.healBonus = healBonus;
		this.hostileFrostResistancePercent = hostileFrostResistancePercent;
		this.hostileLightningResistancePercent = hostileLightningResistancePercent;
		this.naturalRegenBonus = naturalRegenBonus;
		this.blindnessResistancePercent = blindnessResistancePercent;
		this.moveSpeedPercent = moveSpeedPercent;
		this.baseAttackDamage = baseAttackDamage;
		this.hpPerSecond = hpPerSecond;
		this.lightningPercentDamage = lightningPercentDamage;
		this.frostFreezeChance = frostFreezeChance;
		this.firePercentDamagePerSecond = firePercentDamagePerSecond;
		this.healReductionPercent = healReductionPercent;
		this.antiHealOnHitPercent = antiHealOnHitPercent;
		this.berserkerThreshold = berserkerThreshold;
		this.berserkerDamageBonus = berserkerDamageBonus;
		this.bossOnly = bossOnly;
	}

	/**
	 * v13 兼容构造器（v12 兼容参数 + bossOnly）
	 * 与 v12 兼容构造器相同的参数顺序（缺少 v9/v10/v7 fireBurnStackBonus 字段），末尾加 bossOnly
	 * 用于 AffixRegistry 中 Boss 词条注册
	 */
	public Affix(String id, AffixRarity rarity, boolean good, String nameKey, String descKey,
			int healthBonus, int speedAmplifier, int strengthAmplifier, int resistanceAmplifier, int expBonusPercent,
			double hostileHealthMult, double hostileDamageMult, double hostileSpeedMult, int hostileEffectAmplifier,
			double lifestealPercent, int absorptionBonus, boolean fireImmunity, boolean drownImmunity,
			boolean fallImmunity, int weaponDamageBonus, double summonChance, String summonMobId,
			double hostileLifestealPercent, double hostileSummonChance, boolean hostileFireImmunity,
			int hostileArmorLevel,
			int hasteAmplifier, int regenAmplifier, double bossDamageBonus,
			int hostileRegenLevel, double hostileThornsReflect,
			int absorptionCooldownSeconds, double lightningDamage, String weaponTypeBonus, double weaponDamagePercent,
			double hostileArmorPercent, double fireResistancePercent, double hostileFireResistancePercent,
			double fireDamage, double frostDamage, boolean frostImmunity,
			double frostResistancePercent, double lightningResistancePercent,
			double fireChanceOnAttack, double frostChanceOnAttack,
			double miningSpeedBonus, double attackSpeedBonus, double moveSpeedBonus,
			double critChance, double healBonus,
			double hostileFrostResistancePercent, double hostileLightningResistancePercent,
			double naturalRegenBonus,
			double lightningPercentDamage, double frostFreezeChance, double firePercentDamagePerSecond,
			int healReductionPercent, int antiHealOnHitPercent, int berserkerThreshold, double berserkerDamageBonus,
			boolean bossOnly) {
		this(id, rarity, good, nameKey, descKey,
				healthBonus, speedAmplifier, strengthAmplifier, resistanceAmplifier, expBonusPercent,
				hostileHealthMult, hostileDamageMult, hostileSpeedMult, hostileEffectAmplifier,
				lifestealPercent, absorptionBonus, fireImmunity, drownImmunity,
				fallImmunity, weaponDamageBonus, summonChance, summonMobId,
				hostileLifestealPercent, hostileSummonChance, hostileFireImmunity, hostileArmorLevel,
				hasteAmplifier, regenAmplifier, bossDamageBonus,
				hostileRegenLevel, hostileThornsReflect,
				absorptionCooldownSeconds, lightningDamage, weaponTypeBonus, weaponDamagePercent,
				hostileArmorPercent, fireResistancePercent, hostileFireResistancePercent,
				fireDamage, frostDamage, frostImmunity,
				frostResistancePercent, lightningResistancePercent,
				fireChanceOnAttack, frostChanceOnAttack,
				0,
				miningSpeedBonus, attackSpeedBonus, moveSpeedBonus,
				critChance, healBonus,
				hostileFrostResistancePercent, hostileLightningResistancePercent,
				naturalRegenBonus, 0, 0, 0, 0,
				lightningPercentDamage, frostFreezeChance, firePercentDamagePerSecond,
				healReductionPercent, antiHealOnHitPercent, berserkerThreshold, berserkerDamageBonus,
				bossOnly);
	}

	/**
	 * v8 兼容构造器（含 v2~v8 字段，无 v9/v10 字段）
	 * fireBurnStackBonus 和 blindnessResistancePercent 默认为 0
	 */
	public Affix(String id, AffixRarity rarity, boolean good, String nameKey, String descKey,
			int healthBonus, int speedAmplifier, int strengthAmplifier, int resistanceAmplifier, int expBonusPercent,
			double hostileHealthMult, double hostileDamageMult, double hostileSpeedMult, int hostileEffectAmplifier,
			double lifestealPercent, int absorptionBonus, boolean fireImmunity, boolean drownImmunity,
			boolean fallImmunity, int weaponDamageBonus, double summonChance, String summonMobId,
			double hostileLifestealPercent, double hostileSummonChance, boolean hostileFireImmunity,
			int hostileArmorLevel,
			int hasteAmplifier, int regenAmplifier, double bossDamageBonus,
			int hostileRegenLevel, double hostileThornsReflect,
			int absorptionCooldownSeconds, double lightningDamage, String weaponTypeBonus, double weaponDamagePercent,
			double hostileArmorPercent, double fireResistancePercent, double hostileFireResistancePercent,
			double fireDamage, double frostDamage, boolean frostImmunity,
			double frostResistancePercent, double lightningResistancePercent,
			double fireChanceOnAttack, double frostChanceOnAttack,
			double miningSpeedBonus, double attackSpeedBonus, double moveSpeedBonus,
			double critChance, double healBonus,
			double hostileFrostResistancePercent, double hostileLightningResistancePercent,
			double naturalRegenBonus) {
		this(id, rarity, good, nameKey, descKey,
				healthBonus, speedAmplifier, strengthAmplifier, resistanceAmplifier, expBonusPercent,
				hostileHealthMult, hostileDamageMult, hostileSpeedMult, hostileEffectAmplifier,
				lifestealPercent, absorptionBonus, fireImmunity, drownImmunity,
				fallImmunity, weaponDamageBonus, summonChance, summonMobId,
				hostileLifestealPercent, hostileSummonChance, hostileFireImmunity, hostileArmorLevel,
				hasteAmplifier, regenAmplifier, bossDamageBonus,
				hostileRegenLevel, hostileThornsReflect,
				absorptionCooldownSeconds, lightningDamage, weaponTypeBonus, weaponDamagePercent,
				hostileArmorPercent, fireResistancePercent, hostileFireResistancePercent,
				fireDamage, frostDamage, frostImmunity,
				frostResistancePercent, lightningResistancePercent,
				fireChanceOnAttack, frostChanceOnAttack,
				0,
				miningSpeedBonus, attackSpeedBonus, moveSpeedBonus,
				critChance, healBonus,
				hostileFrostResistancePercent, hostileLightningResistancePercent,
				naturalRegenBonus, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 0);
}

/**
 * v11 兼容构造器（含 v2~v8 字段 + v11 元素百分比伤害字段，无 v9/v10 字段）
 * fireBurnStackBonus 和 blindnessResistancePercent 默认为 0
 */
public Affix(String id, AffixRarity rarity, boolean good, String nameKey, String descKey,
		int healthBonus, int speedAmplifier, int strengthAmplifier, int resistanceAmplifier, int expBonusPercent,
		double hostileHealthMult, double hostileDamageMult, double hostileSpeedMult, int hostileEffectAmplifier,
		double lifestealPercent, int absorptionBonus, boolean fireImmunity, boolean drownImmunity,
		boolean fallImmunity, int weaponDamageBonus, double summonChance, String summonMobId,
		double hostileLifestealPercent, double hostileSummonChance, boolean hostileFireImmunity,
		int hostileArmorLevel,
		int hasteAmplifier, int regenAmplifier, double bossDamageBonus,
		int hostileRegenLevel, double hostileThornsReflect,
		int absorptionCooldownSeconds, double lightningDamage, String weaponTypeBonus, double weaponDamagePercent,
		double hostileArmorPercent, double fireResistancePercent, double hostileFireResistancePercent,
		double fireDamage, double frostDamage, boolean frostImmunity,
		double frostResistancePercent, double lightningResistancePercent,
		double fireChanceOnAttack, double frostChanceOnAttack,
		double miningSpeedBonus, double attackSpeedBonus, double moveSpeedBonus,
		double critChance, double healBonus,
		double hostileFrostResistancePercent, double hostileLightningResistancePercent,
		double naturalRegenBonus,
		double lightningPercentDamage, double frostFreezeChance, double firePercentDamagePerSecond) {
	this(id, rarity, good, nameKey, descKey,
			healthBonus, speedAmplifier, strengthAmplifier, resistanceAmplifier, expBonusPercent,
			hostileHealthMult, hostileDamageMult, hostileSpeedMult, hostileEffectAmplifier,
			lifestealPercent, absorptionBonus, fireImmunity, drownImmunity,
			fallImmunity, weaponDamageBonus, summonChance, summonMobId,
			hostileLifestealPercent, hostileSummonChance, hostileFireImmunity, hostileArmorLevel,
			hasteAmplifier, regenAmplifier, bossDamageBonus,
			hostileRegenLevel, hostileThornsReflect,
			absorptionCooldownSeconds, lightningDamage, weaponTypeBonus, weaponDamagePercent,
			hostileArmorPercent, fireResistancePercent, hostileFireResistancePercent,
			fireDamage, frostDamage, frostImmunity,
			frostResistancePercent, lightningResistancePercent,
			fireChanceOnAttack, frostChanceOnAttack,
			0,
			miningSpeedBonus, attackSpeedBonus, moveSpeedBonus,
			critChance, healBonus,
			hostileFrostResistancePercent, hostileLightningResistancePercent,
			naturalRegenBonus, 0, 0, 0, 0,
			lightningPercentDamage, frostFreezeChance, firePercentDamagePerSecond,
			0, 0, 0, 0);
}

/**
 * v12 兼容构造器（含 v2~v11 字段 + v12 抑制回血/狂战士/攻击附带抑制回血字段）
 * fireBurnStackBonus 和 blindnessResistancePercent/moveSpeedPercent/baseAttackDamage/hpPerSecond 默认为 0
 */
public Affix(String id, AffixRarity rarity, boolean good, String nameKey, String descKey,
		int healthBonus, int speedAmplifier, int strengthAmplifier, int resistanceAmplifier, int expBonusPercent,
		double hostileHealthMult, double hostileDamageMult, double hostileSpeedMult, int hostileEffectAmplifier,
		double lifestealPercent, int absorptionBonus, boolean fireImmunity, boolean drownImmunity,
		boolean fallImmunity, int weaponDamageBonus, double summonChance, String summonMobId,
		double hostileLifestealPercent, double hostileSummonChance, boolean hostileFireImmunity,
		int hostileArmorLevel,
		int hasteAmplifier, int regenAmplifier, double bossDamageBonus,
		int hostileRegenLevel, double hostileThornsReflect,
		int absorptionCooldownSeconds, double lightningDamage, String weaponTypeBonus, double weaponDamagePercent,
		double hostileArmorPercent, double fireResistancePercent, double hostileFireResistancePercent,
		double fireDamage, double frostDamage, boolean frostImmunity,
		double frostResistancePercent, double lightningResistancePercent,
		double fireChanceOnAttack, double frostChanceOnAttack,
		double miningSpeedBonus, double attackSpeedBonus, double moveSpeedBonus,
		double critChance, double healBonus,
		double hostileFrostResistancePercent, double hostileLightningResistancePercent,
		double naturalRegenBonus,
		double lightningPercentDamage, double frostFreezeChance, double firePercentDamagePerSecond,
		int healReductionPercent, int antiHealOnHitPercent, int berserkerThreshold, double berserkerDamageBonus) {
	this(id, rarity, good, nameKey, descKey,
			healthBonus, speedAmplifier, strengthAmplifier, resistanceAmplifier, expBonusPercent,
			hostileHealthMult, hostileDamageMult, hostileSpeedMult, hostileEffectAmplifier,
			lifestealPercent, absorptionBonus, fireImmunity, drownImmunity,
			fallImmunity, weaponDamageBonus, summonChance, summonMobId,
			hostileLifestealPercent, hostileSummonChance, hostileFireImmunity, hostileArmorLevel,
			hasteAmplifier, regenAmplifier, bossDamageBonus,
			hostileRegenLevel, hostileThornsReflect,
			absorptionCooldownSeconds, lightningDamage, weaponTypeBonus, weaponDamagePercent,
			hostileArmorPercent, fireResistancePercent, hostileFireResistancePercent,
			fireDamage, frostDamage, frostImmunity,
			frostResistancePercent, lightningResistancePercent,
			fireChanceOnAttack, frostChanceOnAttack,
			0,
			miningSpeedBonus, attackSpeedBonus, moveSpeedBonus,
			critChance, healBonus,
			hostileFrostResistancePercent, hostileLightningResistancePercent,
			naturalRegenBonus, 0, 0, 0, 0,
			lightningPercentDamage, frostFreezeChance, firePercentDamagePerSecond,
			healReductionPercent, antiHealOnHitPercent, berserkerThreshold, berserkerDamageBonus);
}

/**
 * v7 兼容构造器（含 v2~v7 字段，无 v8/v9/v10/v11 字段）
	 * naturalRegenBonus、fireBurnStackBonus、blindnessResistancePercent 默认为 0
	 */
	public Affix(String id, AffixRarity rarity, boolean good, String nameKey, String descKey,
			int healthBonus, int speedAmplifier, int strengthAmplifier, int resistanceAmplifier, int expBonusPercent,
			double hostileHealthMult, double hostileDamageMult, double hostileSpeedMult, int hostileEffectAmplifier,
			double lifestealPercent, int absorptionBonus, boolean fireImmunity, boolean drownImmunity,
			boolean fallImmunity, int weaponDamageBonus, double summonChance, String summonMobId,
			double hostileLifestealPercent, double hostileSummonChance, boolean hostileFireImmunity,
			int hostileArmorLevel,
			int hasteAmplifier, int regenAmplifier, double bossDamageBonus,
			int hostileRegenLevel, double hostileThornsReflect,
			int absorptionCooldownSeconds, double lightningDamage, String weaponTypeBonus, double weaponDamagePercent,
			double hostileArmorPercent, double fireResistancePercent, double hostileFireResistancePercent,
			double fireDamage, double frostDamage, boolean frostImmunity,
			double frostResistancePercent, double lightningResistancePercent,
			double fireChanceOnAttack, double frostChanceOnAttack,
			double miningSpeedBonus, double attackSpeedBonus, double moveSpeedBonus,
			double critChance, double healBonus,
			double hostileFrostResistancePercent, double hostileLightningResistancePercent) {
		this(id, rarity, good, nameKey, descKey,
				healthBonus, speedAmplifier, strengthAmplifier, resistanceAmplifier, expBonusPercent,
				hostileHealthMult, hostileDamageMult, hostileSpeedMult, hostileEffectAmplifier,
				lifestealPercent, absorptionBonus, fireImmunity, drownImmunity,
				fallImmunity, weaponDamageBonus, summonChance, summonMobId,
				hostileLifestealPercent, hostileSummonChance, hostileFireImmunity, hostileArmorLevel,
				hasteAmplifier, regenAmplifier, bossDamageBonus,
				hostileRegenLevel, hostileThornsReflect,
				absorptionCooldownSeconds, lightningDamage, weaponTypeBonus, weaponDamagePercent,
				hostileArmorPercent, fireResistancePercent, hostileFireResistancePercent,
				fireDamage, frostDamage, frostImmunity,
				frostResistancePercent, lightningResistancePercent,
				fireChanceOnAttack, frostChanceOnAttack,
				0,
				miningSpeedBonus, attackSpeedBonus, moveSpeedBonus,
				critChance, healBonus,
				hostileFrostResistancePercent, hostileLightningResistancePercent,
			0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0);
}

/**
 * v6 兼容构造器（含 v2~v6 字段，无 v7 新属性字段）
	 * 用于 AffixRegistry 中 v6 时期的词条注册（35 个效果参数）
	 * fireBurnStackBonus、v7/v8/v9/v10 字段默认为 0
	 */
	public Affix(String id, AffixRarity rarity, boolean good, String nameKey, String descKey,
			int healthBonus, int speedAmplifier, int strengthAmplifier, int resistanceAmplifier, int expBonusPercent,
			double hostileHealthMult, double hostileDamageMult, double hostileSpeedMult, int hostileEffectAmplifier,
			double lifestealPercent, int absorptionBonus, boolean fireImmunity, boolean drownImmunity,
			boolean fallImmunity, int weaponDamageBonus, double summonChance, String summonMobId,
			double hostileLifestealPercent, double hostileSummonChance, boolean hostileFireImmunity,
			int hostileArmorLevel,
			int hasteAmplifier, int regenAmplifier, double bossDamageBonus,
			int hostileRegenLevel, double hostileThornsReflect,
			int absorptionCooldownSeconds, double lightningDamage, String weaponTypeBonus, double weaponDamagePercent,
			double hostileArmorPercent, double fireResistancePercent, double hostileFireResistancePercent,
			double fireDamage, double frostDamage, boolean frostImmunity,
			double frostResistancePercent, double lightningResistancePercent,
			double fireChanceOnAttack, double frostChanceOnAttack) {
		this(id, rarity, good, nameKey, descKey,
				healthBonus, speedAmplifier, strengthAmplifier, resistanceAmplifier, expBonusPercent,
				hostileHealthMult, hostileDamageMult, hostileSpeedMult, hostileEffectAmplifier,
				lifestealPercent, absorptionBonus, fireImmunity, drownImmunity,
				fallImmunity, weaponDamageBonus, summonChance, summonMobId,
				hostileLifestealPercent, hostileSummonChance, hostileFireImmunity, hostileArmorLevel,
				hasteAmplifier, regenAmplifier, bossDamageBonus,
				hostileRegenLevel, hostileThornsReflect,
				absorptionCooldownSeconds, lightningDamage, weaponTypeBonus, weaponDamagePercent,
				hostileArmorPercent, fireResistancePercent, hostileFireResistancePercent,
				fireDamage, frostDamage, frostImmunity,
				frostResistancePercent, lightningResistancePercent,
				fireChanceOnAttack, frostChanceOnAttack,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0);
}

	/**
	 * 旧版兼容构造器（无特殊效果，用于原12个基础词条）
	 */
	public Affix(String id, AffixRarity rarity, boolean good, String nameKey, String descKey,
			int healthBonus, int speedAmplifier, int strengthAmplifier, int resistanceAmplifier, int expBonusPercent,
			double hostileHealthMult, double hostileDamageMult, double hostileSpeedMult, int hostileEffectAmplifier) {
		this(id, rarity, good, nameKey, descKey,
				healthBonus, speedAmplifier, strengthAmplifier, resistanceAmplifier, expBonusPercent,
				hostileHealthMult, hostileDamageMult, hostileSpeedMult, hostileEffectAmplifier,
				0, 0, false, false, false, 0, 0, null,
				0, 0, false, 0,
				0, 0, 0, 0, 0,
				0, 0, null, 0, 0, 0, 0,
			0, 0, false, 0, 0, 0, 0);
}

/**
 * v5 兼容构造器（含 v2~v5 字段，无 v6 元素伤害字段）
	 * 用于 AffixRegistry 中 v5 时期的词条注册（38 个效果参数）
	 * v6 的 7 个元素字段默认为 0/false
	 */
	public Affix(String id, AffixRarity rarity, boolean good, String nameKey, String descKey,
			int healthBonus, int speedAmplifier, int strengthAmplifier, int resistanceAmplifier, int expBonusPercent,
			double hostileHealthMult, double hostileDamageMult, double hostileSpeedMult, int hostileEffectAmplifier,
			double lifestealPercent, int absorptionBonus, boolean fireImmunity, boolean drownImmunity,
			boolean fallImmunity, int weaponDamageBonus, double summonChance, String summonMobId,
			double hostileLifestealPercent, double hostileSummonChance, boolean hostileFireImmunity,
			int hostileArmorLevel,
			int hasteAmplifier, int regenAmplifier, double bossDamageBonus,
			int hostileRegenLevel, double hostileThornsReflect,
			int absorptionCooldownSeconds, double lightningDamage, String weaponTypeBonus, double weaponDamagePercent,
			double hostileArmorPercent, double fireResistancePercent, double hostileFireResistancePercent) {
		this(id, rarity, good, nameKey, descKey,
				healthBonus, speedAmplifier, strengthAmplifier, resistanceAmplifier, expBonusPercent,
				hostileHealthMult, hostileDamageMult, hostileSpeedMult, hostileEffectAmplifier,
				lifestealPercent, absorptionBonus, fireImmunity, drownImmunity,
				fallImmunity, weaponDamageBonus, summonChance, summonMobId,
				hostileLifestealPercent, hostileSummonChance, hostileFireImmunity, hostileArmorLevel,
				hasteAmplifier, regenAmplifier, bossDamageBonus,
				hostileRegenLevel, hostileThornsReflect,
				absorptionCooldownSeconds, lightningDamage, weaponTypeBonus, weaponDamagePercent,
				hostileArmorPercent, fireResistancePercent, hostileFireResistancePercent,
			0, 0, false, 0, 0, 0, 0);
	}

	// 基础访问器
	public String getId() { return id; }
	public AffixRarity getRarity() { return rarity; }
	public boolean isGood() { return good; }
	public String getNameKey() { return nameKey; }
	public String getDescKey() { return descKey; }
	public int getHealthBonus() { return healthBonus; }
	public int getSpeedAmplifier() { return speedAmplifier; }
	public int getStrengthAmplifier() { return strengthAmplifier; }
	public int getResistanceAmplifier() { return resistanceAmplifier; }
	public int getExpBonusPercent() { return expBonusPercent; }
	public double getHostileHealthMult() { return hostileHealthMult; }
	public double getHostileDamageMult() { return hostileDamageMult; }
	public double getHostileSpeedMult() { return hostileSpeedMult; }
	public int getHostileEffectAmplifier() { return hostileEffectAmplifier; }

	// 新效果访问器（好词条）
	public double getLifestealPercent() { return lifestealPercent; }
	public int getAbsorptionBonus() { return absorptionBonus; }
	public boolean isFireImmunity() { return fireImmunity; }
	public boolean isDrownImmunity() { return drownImmunity; }
	public boolean isFallImmunity() { return fallImmunity; }
	public int getWeaponDamageBonus() { return weaponDamageBonus; }
	public double getSummonChance() { return summonChance; }
	public String getSummonMobId() { return summonMobId; }

	// 新效果访问器（坏词条）
	public double getHostileLifestealPercent() { return hostileLifestealPercent; }
	public double getHostileSummonChance() { return hostileSummonChance; }
	public boolean isHostileFireImmunity() { return hostileFireImmunity; }
	public int getHostileArmorLevel() { return hostileArmorLevel; }

	// v3 新效果访问器（好词条）
	public int getHasteAmplifier() { return hasteAmplifier; }
	public int getRegenAmplifier() { return regenAmplifier; }
	public double getBossDamageBonus() { return bossDamageBonus; }

	// v3 新效果访问器（坏词条）
	public int getHostileRegenLevel() { return hostileRegenLevel; }
	public double getHostileThornsReflect() { return hostileThornsReflect; }

	// v4 新效果访问器（好词条）
	public int getAbsorptionCooldownSeconds() { return absorptionCooldownSeconds; }
	public double getLightningDamage() { return lightningDamage; }
	public String getWeaponTypeBonus() { return weaponTypeBonus; }
	public double getWeaponDamagePercent() { return weaponDamagePercent; }

	// v4 新效果访问器（坏词条）
	public double getHostileArmorPercent() { return hostileArmorPercent; }
	public double getFireResistancePercent() { return fireResistancePercent; }
	public double getHostileFireResistancePercent() { return hostileFireResistancePercent; }

	// v6 新效果访问器（元素伤害体系统一）
	public double getFireDamage() { return fireDamage; }
	public double getFrostDamage() { return frostDamage; }
	public boolean isFrostImmunity() { return frostImmunity; }
	public double getFrostResistancePercent() { return frostResistancePercent; }
	public double getLightningResistancePercent() { return lightningResistancePercent; }
	public double getFireChanceOnAttack() { return fireChanceOnAttack; }
	public double getFrostChanceOnAttack() { return frostChanceOnAttack; }
	public double getFireBurnStackBonus() { return fireBurnStackBonus; }

	// v7 新效果访问器（独立属性加成，不依赖原版药水效果）
	public double getMiningSpeedBonus() { return miningSpeedBonus; }
	public double getAttackSpeedBonus() { return attackSpeedBonus; }
	public double getMoveSpeedBonus() { return moveSpeedBonus; }
	public double getCritChance() { return critChance; }
	public double getHealBonus() { return healBonus; }
	public double getHostileFrostResistancePercent() { return hostileFrostResistancePercent; }
	public double getHostileLightningResistancePercent() { return hostileLightningResistancePercent; }

	// v8 新效果访问器
	public double getNaturalRegenBonus() { return naturalRegenBonus; }

	// v9 新效果访问器（好词条：失明免疫率）
	public double getBlindnessResistancePercent() { return blindnessResistancePercent; }

	public double getMoveSpeedPercent() { return moveSpeedPercent; }

	public double getBaseAttackDamage() { return baseAttackDamage; }

	public double getHpPerSecond() { return hpPerSecond; }

	// v11 新效果访问器（元素百分比伤害）
	public double getLightningPercentDamage() { return lightningPercentDamage; }
	public double getFrostFreezeChance() { return frostFreezeChance; }
	public double getFirePercentDamagePerSecond() { return firePercentDamagePerSecond; }

	// v12 新效果访问器
	/** v12: 抑制回血百分比（坏词条） */
	public int getHealReductionPercent() { return healReductionPercent; }
	/** v12: 攻击附带抑制回血百分比（好词条） */
	public int getAntiHealOnHitPercent() { return antiHealOnHitPercent; }
	/** v12: 狂战士触发血量阈值 */
	public int getBerserkerThreshold() { return berserkerThreshold; }
	/** v12: 狂战士攻击力加成 */
	public double getBerserkerDamageBonus() { return berserkerDamageBonus; }

	// v13 新效果访问器
	/** v13: 是否为Boss专属词条 */
	public boolean isBossOnly() { return bossOnly; }

	/** 是否包含任何特殊效果（用于判断是否需要特殊处理） */
	public boolean hasSpecialEffect() {
		if (good) {
			return lifestealPercent > 0 || absorptionBonus > 0 || fireImmunity
					|| drownImmunity || fallImmunity || weaponDamageBonus > 0
					|| summonChance > 0
					|| hasteAmplifier > 0 || regenAmplifier > 0 || bossDamageBonus > 0
					|| absorptionCooldownSeconds > 0 || lightningDamage > 0
					|| weaponTypeBonus != null || weaponDamagePercent > 0
				|| fireResistancePercent > 0
				|| fireDamage > 0 || frostDamage > 0 || frostImmunity
				|| frostResistancePercent > 0 || lightningResistancePercent > 0
				|| fireChanceOnAttack > 0 || frostChanceOnAttack > 0
				|| miningSpeedBonus > 0 || attackSpeedBonus > 0 || moveSpeedBonus > 0
				|| critChance > 0 || healBonus > 0
				|| naturalRegenBonus > 0 || blindnessResistancePercent > 0
			|| lightningPercentDamage > 0 || frostFreezeChance > 0 || firePercentDamagePerSecond > 0
			|| antiHealOnHitPercent > 0 || berserkerThreshold > 0;
	} else {
		return hostileLifestealPercent > 0 || hostileSummonChance > 0
				|| hostileFireImmunity || hostileArmorLevel > 0
				|| hostileRegenLevel > 0 || hostileThornsReflect > 0
				|| hostileArmorPercent > 0 || hostileFireResistancePercent > 0
				|| hostileFrostResistancePercent > 0 || hostileLightningResistancePercent > 0
				|| healReductionPercent > 0;
		}
	}

	@Override
	public String toString() {
		return rarity.getDisplayName() + "-" + id;
	}
}
