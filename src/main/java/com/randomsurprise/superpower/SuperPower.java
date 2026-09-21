package com.randomsurprise.superpower;

/**
 * 超能力定义枚举
 * 每个玩家只能拥有其中一个（首次进入服务器时通过转盘抽取）
 *
 * 类型分类：
 * - ACTIVE：主动技能，按 G 键释放，有冷却
 * - PASSIVE：纯被动技能，永久生效
 * - PASSIVE_WITH_TOGGLE：被动技能，可通过 V 键开关
 * - PASSIVE_WITH_ACTIVE：被动永久效果 + G 键主动释放额外效果
 */
public enum SuperPower {
	/** 瞬移：瞬移到准星命中点，10 秒冷却 */
	BLINK("blink", "superpower.randomsurprise.blink.name", "superpower.randomsurprise.blink.desc",
			Type.ACTIVE, 10, 0xFF55AAFF),

	/** 家财万贯：每 60s 获得随机矿物 1-3 个 */
	WEALTHY("wealthy", "superpower.randomsurprise.wealthy.name", "superpower.randomsurprise.wealthy.desc",
			Type.PASSIVE, 0, 0xFFFFD700),

	/** 飞行：20 秒创造飞行，15 秒冷却 */
	FLY("fly", "superpower.randomsurprise.fly.name", "superpower.randomsurprise.fly.desc",
			Type.ACTIVE, 15, 0xFF55FFFF),

	/** 附魔：主手物品随机高等级附魔（多词条满级），消耗 10 级经验，无冷却 */
	ENCHANT("enchant", "superpower.randomsurprise.enchant.name", "superpower.randomsurprise.enchant.desc",
			Type.ACTIVE, 0, 0xFFAA00FF),

	/** 雷霆之子：对准星命中位置（方块或生物）召唤雷击，雨天伤害翻倍，22 秒冷却，不误伤自己 */
	THUNDER_STRIKE("thunder_strike", "superpower.randomsurprise.thunder_strike.name", "superpower.randomsurprise.thunder_strike.desc",
			Type.ACTIVE, 22, 0xFFFFFF55),

	/** 濒死回溯：生命清零时回溯 3 秒前位置并恢复一半血量，180 秒冷却 */
	NEAR_DEATH_RECALL("near_death_recall", "superpower.randomsurprise.near_death_recall.name", "superpower.randomsurprise.near_death_recall.desc",
			Type.PASSIVE, com.randomsurprise.config.BalanceConfig.NEAR_DEATH_RECALL_COOLDOWN, 0xFFFF5555),

	/** 分解大师：主手物品分解为合成配方材料，30 秒冷却 */
	DECOMPOSE("decompose", "superpower.randomsurprise.decompose.name", "superpower.randomsurprise.decompose.desc",
			Type.ACTIVE, 30, 0xFF55FF55),

	/** 跑酷达人：可二段跳 */
	PARKOUR("parkour", "superpower.randomsurprise.parkour.name", "superpower.randomsurprise.parkour.desc",
			Type.PASSIVE, 0, 0xFFFFAA00),

	/** 武器大师：30% 概率伤害翻倍 */
	WEAPON_MASTER("weapon_master", "superpower.randomsurprise.weapon_master.name", "superpower.randomsurprise.weapon_master.desc",
			Type.PASSIVE, 0, 0xFFFF55FF),

	/** 毒素免疫：永久免疫中毒/凋零/饥饿；G 键释放 15 秒全异常清除，30 秒冷却 */
	TOXIN_IMMUNITY("toxin_immunity", "superpower.randomsurprise.toxin_immunity.name", "superpower.randomsurprise.toxin_immunity.desc",
			Type.PASSIVE_WITH_ACTIVE, 30, 0xFF55FFAA),

	/** 时间膨胀：主动，周围5格内所有敌对生物减速50%持续10秒，20秒冷却 */
	TIME_DILATION("time_dilation", "superpower.randomsurprise.time_dilation.name", "superpower.randomsurprise.time_dilation.desc",
			Type.ACTIVE, 20, 0xFFDDA0DD),

	/** 生命汲取：被动，攻击时恢复自身2点生命值 */
	LIFE_DRAIN("life_drain", "superpower.randomsurprise.life_drain.name", "superpower.randomsurprise.life_drain.desc",
			Type.PASSIVE, 0, 0xFFFF6666),

	/** 重力操控：主动，将周围10格内所有敌对生物拉向自己并造成3点坠落伤害，25秒冷却 */
	GRAVITY_PULL("gravity_pull", "superpower.randomsurprise.gravity_pull.name", "superpower.randomsurprise.gravity_pull.desc",
			Type.ACTIVE, 25, 0xFF9966FF),

	/** 急速救援（征召世界专属）：站在倒地队友旁边5秒救助时间缩短为2秒 */
	RAPID_RESCUE("rapid_rescue", "superpower.randomsurprise.rapid_rescue.name", "superpower.randomsurprise.rapid_rescue.desc",
			Type.PASSIVE, 0, 0xFF66FFB2, true),

	/** 战场狂怒（征召世界专属）：征召世界中击杀怪物后10秒内攻击力+30% */
	BATTLE_FURY("battle_fury", "superpower.randomsurprise.battle_fury.name", "superpower.randomsurprise.battle_fury.desc",
			Type.PASSIVE, 0, 0xFFFF6600, true),

	/** 守护之盾（征召世界专属）：征召世界中为周围8格内队友提供20%伤害减免 */
	GUARDIAN_SHIELD("guardian_shield", "superpower.randomsurprise.guardian_shield.name", "superpower.randomsurprise.guardian_shield.desc",
			Type.PASSIVE, 0, 0xFF66CCFF, true),

	// ===== 征召世界专属超能力（第二批） =====

	/** 战场医疗兵（征召专属）：每秒为8格内血量最低队友恢复5HP */
	BATTLE_MEDIC("battle_medic", "superpower.randomsurprise.battle_medic.name", "superpower.randomsurprise.battle_medic.desc",
			Type.PASSIVE, 0, 0xFF66FF66, true),

	/** 铁壁堡垒（征召专属）：原地不动或蹲下3秒后获得抗性II+击退免疫，移动后消失 */
	IRON_FORTRESS("iron_fortress", "superpower.randomsurprise.iron_fortress.name", "superpower.randomsurprise.iron_fortress.desc",
			Type.PASSIVE, 0, 0xFFAAAAAA, true),

	/** 猎杀标记（征召专属）：标记准星目标，全队对其伤害+50%持续10秒，20秒冷却 */
	HUNT_MARK("hunt_mark", "superpower.randomsurprise.hunt_mark.name", "superpower.randomsurprise.hunt_mark.desc",
			Type.ACTIVE, 20, 0xFFFFAA00, true),

	/** 战争践踏（征召专属）：践踏地面，8格内敌人受5伤害+击飞+缓慢II，15秒冷却 */
	WAR_STOMP("war_stomp", "superpower.randomsurprise.war_stomp.name", "superpower.randomsurprise.war_stomp.desc",
			Type.ACTIVE, 15, 0xFFCC6600, true),

	/** 不死之身（征召专属）：致死时自动复活并恢复30%血量，附带3秒无敌，180秒冷却 */
	UNDYING_BODY("undying_body", "superpower.randomsurprise.undying_body.name", "superpower.randomsurprise.undying_body.desc",
			Type.PASSIVE, 180, 0xFFFF6666, true),

	/** 冰霜领域（征召专属）：5格内敌对生物移动速度降低30%，攻击速度降低15% */
	FROST_DOMAIN("frost_domain", "superpower.randomsurprise.frost_domain.name", "superpower.randomsurprise.frost_domain.desc",
			Type.PASSIVE, 0, 0xFF66CCFF, true),

	/** 烈焰风暴（征召专属）：在准星位置生成火旋风，6格内持续燃烧5秒+每秒4伤害，25秒冷却 */
	FLAME_STORM("flame_storm", "superpower.randomsurprise.flame_storm.name", "superpower.randomsurprise.flame_storm.desc",
			Type.ACTIVE, 25, 0xFFFF6600, true),

	/** 灵魂链接（征召专属）：与最近队友链接，受到治疗的50%分享给该队友 */
	SOUL_LINK("soul_link", "superpower.randomsurprise.soul_link.name", "superpower.randomsurprise.soul_link.desc",
			Type.PASSIVE, 0, 0xFFCC99FF, true),

	/** 毁灭终结（征召专属）：下一次攻击造成3倍伤害，10秒内不使用则失效，30秒冷却 */
	DOOM_STRIKE("doom_strike", "superpower.randomsurprise.doom_strike.name", "superpower.randomsurprise.doom_strike.desc",
			Type.ACTIVE, 30, 0xFFFF0000, true),

	/** 生命之泉（征召专属）：在准星位置创建治疗区域，8格内队友每秒恢复2HP持续10秒，30秒冷却 */
	LIFE_SPRING("life_spring", "superpower.randomsurprise.life_spring.name", "superpower.randomsurprise.life_spring.desc",
			Type.ACTIVE, 30, 0xFF66FF99, true),

	/** 战神之力（征召专属）：被动战场内攻击+20%，G键10秒内再+50%（合计70%），30秒冷却 */
	WAR_GOD_MIGHT("war_god_might", "superpower.randomsurprise.war_god_might.name", "superpower.randomsurprise.war_god_might.desc",
			Type.PASSIVE_WITH_ACTIVE, 30, 0xFFFF3333, true),

	/** 元素护甲（征召专属）：战场内免疫火焰/冰霜/闪电伤害 */
	ELEMENTAL_ARMOR("elemental_armor", "superpower.randomsurprise.elemental_armor.name", "superpower.randomsurprise.elemental_armor.desc",
			Type.PASSIVE, 0, 0xFF99CCFF, true),

	/** 死亡之触（征召专属）：对准星目标使用，HP低于20%的敌人立即击杀，60秒冷却 */
	DEATH_TOUCH("death_touch", "superpower.randomsurprise.death_touch.name", "superpower.randomsurprise.death_touch.desc",
			Type.ACTIVE, 60, 0xFF990000, true);

	public enum Type {
		/** 纯主动：仅按 G 键触发 */
		ACTIVE,
		/** 纯被动：永久生效，无开关 */
		PASSIVE,
		/** 被动+开关：V 键切换开关状态 */
		PASSIVE_WITH_TOGGLE,
		/** 被动+主动：被动永久生效，G 键额外释放主动效果 */
		PASSIVE_WITH_ACTIVE
	}

	private final String id;
	private final String nameKey;
	private final String descKey;
	private final Type type;
	private final int cooldownSeconds;
	private final int color; // ARGB 颜色，用于 UI
	private final boolean battlefieldOnly; // 是否为征召世界专属超能力

	SuperPower(String id, String nameKey, String descKey, Type type, int cooldownSeconds, int color) {
		this(id, nameKey, descKey, type, cooldownSeconds, color, false);
	}

	SuperPower(String id, String nameKey, String descKey, Type type, int cooldownSeconds, int color, boolean battlefieldOnly) {
		this.id = id;
		this.nameKey = nameKey;
		this.descKey = descKey;
		this.type = type;
		this.cooldownSeconds = cooldownSeconds;
		this.color = color;
		this.battlefieldOnly = battlefieldOnly;
	}

	public String getId() { return id; }
	public String getNameKey() { return nameKey; }
	public String getDescKey() { return descKey; }
	public Type getType() { return type; }
	public int getCooldownSeconds() { return cooldownSeconds; }
	public int getColor() { return color; }
	public boolean isBattlefieldOnly() { return battlefieldOnly; }

	/** 是否需要主动按键触发 */
	public boolean hasActiveAction() {
		return type == Type.ACTIVE || type == Type.PASSIVE_WITH_ACTIVE;
	}

	/** 是否可通过 V 键切换开关 */
	public boolean isToggleable() {
		return type == Type.PASSIVE_WITH_TOGGLE;
	}

	/** 根据 ID 查找超能力 */
	public static SuperPower getById(String id) {
		if (id == null) return null;
		for (SuperPower sp : values()) {
			if (sp.id.equals(id)) return sp;
		}
		return null;
	}
}
