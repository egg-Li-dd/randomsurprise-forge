---
name: "randomsurprise-data"
description: "随机惊喜模组的完整数据手册：词条列表、随机事件列表、超能力列表、征召战场配置、商店配置。Invoke when user asks to view/modify/add affixes, random events, super powers, battlefield settings, or shop entries."
---

# 随机惊喜模组数据手册

本文件包含模组所有可配置内容的完整列表。修改对应代码后同步更新此文件。

## 相关代码路径

| 系统 | 主文件路径 |
|------|-----------|
| 词条注册 | `src/main/java/com/randomsurprise/affix/AffixRegistry.java` |
| 词条数据结构 | `src/main/java/com/randomsurprise/affix/Affix.java` |
| 词条效果应用 | `src/main/java/com/randomsurprise/affix/PlayerAffixManager.java` |
| 随机事件 | `src/main/java/com/randomsurprise/SurpriseActions.java` |
| 随机事件管理 | `src/main/java/com/randomsurprise/SurpriseManager.java` |
| 超能力定义 | `src/main/java/com/randomsurprise/superpower/SuperPower.java` |
| 超能力逻辑 | `src/main/java/com/randomsurprise/superpower/SuperPowerHandler.java` |
| 超能力数据 | `src/main/java/com/randomsurprise/superpower/PlayerSuperPowerManager.java` |
| 战场管理 | `src/main/java/com/randomsurprise/battlefield/BattlefieldManager.java` |
| Boss池 | `src/main/java/com/randomsurprise/battlefield/BossPool.java` |
| 倒地系统 | `src/main/java/com/randomsurprise/battlefield/DownedStateManager.java` |
| 商店管理 | `src/main/java/com/randomsurprise/shop/ShopManager.java` |
| 兑换注册 | `src/main/java/com/randomsurprise/shop/ExchangeRegistry.java` |
| 中文翻译 | `src/main/resources/assets/randomsurprise/lang/zh_cn.json` |

---

## 一、词条系统

### 抽取概率

| 参数 | 值 |
|------|-----|
| 好词条概率 | 70% |
| 坏词条概率 | 30% |
| 稀有度权重 | 白40% / 绿25% / 蓝15% / 紫10% / 红6% / 金4% |
| 稀有度补偿 | 每5个全服坏词条，高稀有度权重+1%，上限+15% |
| 正面词条抽取后 | 不移除（多个玩家可获得相同好词条） |
| 同稀有度内 | 均匀随机选择 |

### 好词条（83个）

#### 白色 COMMON（9个）

| ID | 名称 | 效果 |
|----|------|------|
| good_common | 坚韧之心 | 最大生命+2 |
| good_vampire_common | 吸血鬼初阶 | 攻击吸血3%, 生命+2 |
| good_haste_common | 急速之手 | 急迫I, 挖掘速度+10%, 经验+5% |
| good_lightning_common | 雷电使者 | 30%概率闪电, 3伤害, 生命+2, 经验+5% |
| good_fire_common | 火焰之力I | 15%概率点燃, 3火焰伤害, 生命+2, 经验+5% |
| good_frost_common | 冰霜之力I | 15%概率减速, 3冰霜伤害, 生命+2, 经验+5% |
| good_miner_common | 矿工之腕 | 挖掘速度+15%, 经验+5% |
| good_tough_common | 坚韧 | 抗性I, 生命+2 |
| good_apprentice_common | 学徒 | 经验+10%, 生命+2 |

#### 绿色 UNCOMMON（14个）

| ID | 名称 | 效果 |
|----|------|------|
| good_uncommon | 风之足迹 | 速度I, 生命+4, 经验+10% |
| good_shield_uncommon | 护盾守卫 | 护盾4(60sCD), 抗性I, 生命+4, 经验+5% |
| good_knockback_uncommon | 击退之力 | 击退增强, 生命+4, 经验+5% |
| good_sword_master_uncommon | 剑士专精 | 持剑伤害+15%, 生命+4, 经验+10% |
| good_fire_uncommon | 火焰之力II | 20%概率点燃, 4火焰伤害, 生命+4, 经验+10% |
| good_frost_uncommon | 冰霜之力II | 20%概率减速, 4冰霜伤害, 速度I, 生命+4, 经验+10% |
| good_lightning_uncommon | 闪电之力II | 30%概率闪电, 4伤害, 闪电减免30%, 速度I, 生命+4, 经验+10% |
| good_scholar_uncommon | 学者 | 经验+25%, 生命+2 |
| good_iron_will_uncommon | 钢铁意志 | 摔落免疫, 生命+4, 经验+5% |
| good_warrior_uncommon | 战士 | 武器伤害I, 生命+4, 经验+5% |
| good_hunter_uncommon | 猎人直觉 | 移速+10%, 暴击8%, 经验+5% |
| good_ambush_uncommon | 伏击 | 对未以你为仇恨目标的敌人伤害+30%, 生命+4, 经验+10% |
| good_weaken_uncommon | 虚弱化 | 攻击降低目标攻击力20%持续5秒, 生命+4, 经验+5% |
| good_potion_thirst_uncommon | 药水渴求 | 每次击杀减少药水冷却5秒, 生命+4, 经验+5% |

#### 蓝色 RARE（17个）

| ID | 名称 | 效果 |
|----|------|------|
| good_rare | 战士之力 | 力量I, 生命+6, 经验+15% |
| good_immune_rare | 免疫大师 | 火焰免疫, 溺水免疫, 生命+4, 经验+10% |
| good_regen_rare | 生命再生 | 生命恢复I(自然回血+100%), 生命+6, 经验+10% |
| good_bow_master_rare | 弓箭大师 | 持弓伤害+20%, 速度I, 生命+6, 经验+10% |
| good_fire_rare | 火焰之力III | 25%概率点燃, 5火焰伤害, 火焰减免50%, 每秒2%最大生命值火焰伤害, 生命+6, 经验+15% |
| good_frost_rare | 冰霜之力III | 25%概率减速, 5冰霜伤害, 冰霜减免50%, 20%冻结, 生命+6, 经验+15% |
| good_lightning_rare | 闪电之力III | 30%概率闪电, 5伤害, 闪电减免50%, 生命+6, 经验+15% |
| good_aqua_rare | 水行 | 溺水免疫, 生命+6, 经验+10% |
| good_berserker_rare | 狂战士 | 暴击12%, 吸血3%, 生命+4, 经验+5% |
| good_archer_rare | 弓手 | 弓伤害+15%, 暴击8%, 经验+5% |
| good_pyro_rare | 烈焰使者 | 火焰伤害+2, 30%点燃, 火焰减免20%, 每秒2%最大生命值火焰伤害, 经验+5% |
| good_shockwave_rare | 震波斩 | 近战30%概率发出前方5格震波,50%伤害, 生命+6, 经验+10% |
| good_whirlwind_rare | 旋风斩 | 近战30%概率对3格内敌人造成40%范围伤害, 生命+6, 经验+10% |
| good_momentum_rare | 动能蓄势 | 疾跑/跳跃后下一击+4伤害, 生命+6, 经验+10% |
| good_vengeance_rare | 复仇 | 受到伤害时反弹30%给攻击者, 生命+6, 经验+10% |
| good_guard_kill_rare | 守卫击杀 | 击杀后3秒减伤50%, 生命+6, 经验+10% |
| good_extra_shot_rare | 追加射击 | 每次射击额外1发箭(50%伤害), 生命+6, 经验+10% |

#### 紫色 EPIC（16个）

| ID | 名称 | 效果 |
|----|------|------|
| good_epic | 守护之盾 | 抗性I, 生命+8, 经验+20% |
| good_weapon_epic | 武器大师 | 力量I, 生命+6, 经验+15% |
| good_slayer_epic | 屠龙者 | 对Boss伤害+12%, 生命+8, 经验+15% |
| good_axe_master_epic | 战斧大师 | 持斧伤害+25%, 力量I, 生命+8, 经验+15% |
| good_fire_epic | 烈焰风暴IV | 30%概率点燃, 6火焰伤害, 火焰减免50%, 每秒2%最大生命值, 力量I, 生命+8, 经验+20% |
| good_frost_epic | 寒冰风暴IV | 30%概率减速, 6冰霜伤害, 冰霜减免50%, 25%冻结, 力量I, 生命+8, 经验+20% |
| good_lightning_epic | 雷霆万钧IV | 30%概率闪电, 6伤害, 闪电减免70%, 3%最大生命值闪电伤害, 力量I, 生命+8, 经验+20% |
| good_paladin_epic | 圣骑士 | 治疗+25%, 护盾4(60sCD), 生命+8, 经验+10% |
| good_arcanist_epic | 奥术师 | 攻速+15%, 暴击10%, 生命+6, 经验+10% |
| good_frost_mage_epic | 冰霜法师 | 冰霜伤害+3, 40%减速, 冰霜免疫, 冰霜减免30%, 25%冻结, 经验+10% |
| good_eagle_eye_epic | 鹰眼 | 失明免疫50%, 生命+6, 经验+10% |
| good_anti_heal_epic | 枯萎之刃 | 攻击附带3秒50%抑制回血, 生命+8, 经验+15% |
| good_pain_cycle_epic | 痛楚循环 | 每次攻击消耗1HP叠加1层，满5层下次攻击5倍伤害, 生命+8, 经验+15% |
| good_armor_break_epic | 破甲打击 | 20%概率施加目标破甲5秒, 生命+8, 经验+15% |
| good_death_detonate_epic | 死亡引爆 | 击杀敌人时爆炸，3格内敌人受被击杀者最大生命10%伤害, 生命+8, 经验+15% |
| good_chain_bind_epic | 连锁锁链 | 20%概率将3格内最多3个敌人束缚2秒, 生命+8, 经验+15% |

#### 红色 LEGENDARY（15个）

| ID | 名称 | 效果 |
|----|------|------|
| good_legendary | 勇者之魂 | 速度I, 力量II, 抗性I, 生命+10, 经验+30% |
| good_summon_legendary | 召唤大师 | 10%召唤铁傀儡, 力量I, 抗性I, 生命+8, 经验+20% |
| good_thorns_legendary | 荆棘反伤 | 护盾6(60sCD), 摔落免疫, 武器伤害II, 抗性I, 生命+10, 经验+20% |
| good_divine_thunder_legendary | 神罚雷霆 | 30%概率闪电, 6伤害, 生命+10, 经验+20% |
| good_fire_legendary | 烈焰风暴V | 35%概率点燃, 8火焰伤害, 火焰免疫, 火焰减免80%, 每秒2%最大生命值, 抗性I, 生命+10, 经验+30% |
| good_frost_legendary | 寒冰风暴V | 35%概率减速, 8冰霜伤害, 冰霜免疫, 摔落免疫, 冰霜减免80%, 30%冻结, 生命+10, 经验+30% |
| good_guardian_legendary | 守护之灵 | 攻速+20%, 治疗+30%, 护盾6(60sCD), 生命+8, 经验+10% |
| good_vampire_lord_legendary | 吸血鬼领主 | 吸血10%, 力量I, 生命+10, 经验+15% |
| good_mountaineer_legendary | 登山者 | 摔落免疫, 移速+15%, 挖掘+20%, 生命+8, 经验+15% |
| good_battle_mage_legendary | 战斗法师 | 武器伤害II, 攻速+15%, 生命+8, 经验+15% |
| good_life_steal_legendary | 血魔之拥 | 吸血8%, 生命+10, 经验+20% |
| good_sky_eye_legendary | 天眼 | 失明免疫80%, 生命+10, 经验+20% |
| good_void_erosion_legendary | 虚空侵蚀 | 命中叠加侵蚀层，每层+5%伤害，5秒不攻击该目标清零, 生命+10, 经验+20% |
| good_soul_siphon_legendary | 灵魂虹吸 | 击杀获1层灵魂，每层+2%暴击率(上限10层), 生命+10, 经验+20% |
| good_purify_aura_legendary | 净化光环 | 5格内友军每10秒清除1个负面效果, 生命+10, 经验+15% |

#### 金色 MYTHIC（12个）

| ID | 名称 | 效果 |
|----|------|------|
| good_mythic | 神之祝福 | 速度I, 力量II, 抗性II, 生命+15, 经验+50% |
| good_divine_mythic | 神之庇护 | 护盾10(60sCD), 火焰免疫, 摔落免疫, 吸血5%, 10%召唤铁傀儡, 速度I, 力量I, 抗性I, 生命+12, 经验+40% |
| good_phoenix_mythic | 不灭凤凰 | 力量II, 抗性II, 吸血5%, 护盾10(60sCD), 火焰免疫, 摔落免疫, 10%召唤铁傀儡, 速度I, 武器伤害II, 生命+15, 经验+40% |
| good_weapon_lord_mythic | 武器至尊 | 全武器+30%, 力量II, 生命+15, 经验+30% |
| good_fire_mythic | 炎神之体 | 40%概率点燃, 10火焰伤害, 火焰免疫, 摔落免疫, 火焰减免100%, 每秒2%最大生命值, 速度I, 力量I, 抗性I, 生命+15, 经验+50% |
| good_frost_mythic | 冰神之体 | 40%概率减速, 10冰霜伤害, 冰霜免疫, 摔落免疫, 冰霜减免100%, 35%冻结, 速度I, 力量I, 抗性I, 生命+15, 经验+50% |
| good_lightning_mythic | 雷神之体 | 30%概率闪电, 8伤害, 闪电减免90%, 5%最大生命值闪电伤害, 摔落免疫, 速度II, 力量I, 抗性I, 生命+15, 经验+50% |
| good_thunder_god_mythic | 雷霆战神 | 闪电伤害+4, 暴击20%, 攻速+25%, 移速+15%, 闪电减免50%, 生命+10, 经验+20% |
| good_eternal_guardian_mythic | 永恒守护者 | 火焰/溺水/摔落免疫, 护盾15, 吸血8%, 抗性I, 生命+15, 经验+30%, 60sCD |
| good_archmage_mythic | 大法师 | 攻速+30%, 暴击25%, 移速+20%, 生命+10, 经验+25% |
| good_berserker_mythic | 狂战士 | 血量<50%时攻击+50%, 吸血5%, 生命+15, 经验+50% |
| good_true_sight_mythic | 真视之眼 | 失明免疫100%, 生命+15, 经验+30% |

### 坏词条（61个）

#### 白色 COMMON（8个）

| ID | 名称 | 效果 |
|----|------|------|
| bad_common | 怪物韧性 | 敌对生物HP×1.05 |
| bad_armor_common | 怪物护甲 | 抗性I, HP×1.03 |
| bad_speed_common | 疾速怪物 | 速度×1.10 |
| bad_armor_percent_common | 铁甲怪物 | 伤害减免3% |
| bad_fire_resist_common | 火焰抗性怪 | 火焰减免20% |
| bad_tough_common | 坚韧怪 | HP×1.03, 攻击×1.03 |
| bad_swift_common | 疾速怪 | 速度×1.12 |
| bad_heal_reduction_common | 抑制回血I | 回血减少15% |

#### 绿色 UNCOMMON（11个）

| ID | 名称 | 效果 |
|----|------|------|
| bad_uncommon | 怪物凶猛 | 攻击×1.05 |
| bad_vampire_uncommon | 怪物吸血 | 吸血3%, 攻击×1.03 |
| bad_thorns_uncommon | 反伤装甲 | 反伤8% |
| bad_heavy_armor_uncommon | 重甲怪物 | 抗性I, HP×1.03 |
| bad_curse_uncommon | 诅咒怪 | 攻击×1.05, 速度×1.05 |
| bad_nimble_uncommon | 灵巧怪 | 速度×1.15, HP×1.03 |
| bad_frost_resist_uncommon | 冰霜抗性怪 | 冰霜减免25% |
| bad_heal_reduction_uncommon | 抑制回血II | 回血减少25% |
| bad_near_death_rage_uncommon | 濒死狂暴怪 | HP<30%时攻击+40%, 速度+20% |
| bad_hate_chain_uncommon | 仇恨连锁怪 | 受击时5格内同类增援, HP×1.03 |
| bad_web_trap_uncommon | 黏网陷阱怪 | 攻击时在玩家脚下放蛛网, HP×1.03 |
| bad_rust_attack_uncommon | 锈蚀攻击怪 | 攻击损耗玩家头盔5耐久, HP×1.03 |

#### 蓝色 RARE（14个）

| ID | 名称 | 效果 |
|----|------|------|
| bad_rare | 怪物敏捷 | 速度×1.10 |
| bad_summon_rare | 怪物召唤 | 3%召唤同伴, 速度×1.05 |
| bad_regen_rare | 回血怪物 | 生命恢复I, HP×1.05 |
| bad_steel_armor_rare | 钢铁怪物 | 抗性II, HP×1.05 |
| bad_warden_rare | 狱卒怪 | HP×1.08, 护甲I |
| bad_corrosive_rare | 腐蚀怪 | 攻击×1.08, 反伤5%, HP×1.05 |
| bad_lightning_resist_rare | 闪电抗性怪 | 闪电减免30%, HP×1.05 |
| bad_heal_reduction_rare | 抑制回血III | 回血减少35% |
| bad_death_explode_rare | 死亡爆炸怪 | 死亡时3格内爆炸6伤害 |
| bad_ender_blink_rare | 末影闪现怪 | 受击20%概率瞬移到玩家身后 |
| bad_darkness_aura_rare | 黑暗笼罩怪 | 3格内玩家获得失明3秒, HP×1.03 |
| bad_fireball_shooter_rare | 火球射手怪 | 每5秒向玩家发射火球4伤害 |
| bad_knockback_cannon_rare | 击退炮怪 | 攻击强力击退玩家+3格, HP×1.03 |
| bad_burning_aura_rare | 炽燃光环怪 | 2格内近战玩家被点燃2秒, HP×1.03 |
| bad_regen_monster_rare | 再生怪物怪 | 每秒恢复1%HP, 脱战翻倍, HP×1.05 |

#### 紫色 EPIC（12个）

| ID | 名称 | 效果 |
|----|------|------|
| bad_epic | 怪物狂暴 | HP×1.10, 攻击×1.05, 力量I |
| bad_berserk_epic | 怪物狂暴 | 攻击×1.06, HP×1.05, 力量I |
| bad_shield_epic | 护盾怪物 | 抗性II, HP×1.06, 速度×1.05 |
| bad_dark_summoner_epic | 暗黑召唤师 | 5%召唤同伴, 攻击×1.08, HP×1.08 |
| bad_void_epic | 虚空怪 | 反伤15%, 护甲II, HP×1.06 |
| bad_element_shield_epic | 元素护盾怪 | 全元素减免30%, 抗性I |
| bad_heal_reduction_epic | 抑制回血IV | 回血减少50% |
| bad_death_split_epic | 死亡分裂怪 | 死亡时分裂为2只同类型(50%HP), HP×1.05 |
| bad_enrage_timer_epic | 狂暴计时怪 | 战斗10秒后狂暴(攻击+50%, 移速+30%), HP×1.05 |
| bad_shield_charge_epic | 护盾充能怪 | 每15秒获得3秒免疫护盾, HP×1.05 |
| bad_cloak_epic | 隐身潜行怪 | 每10秒隐身3秒仅攻击时显形, HP×1.05 |
| bad_storm_call_epic | 雷暴召唤怪 | 每10秒在玩家位置召唤闪电, HP×1.05 |
| bad_wither_touch_epic | 凋零之触怪 | 攻击施加凋零II 3秒, HP×1.05 |
| bad_heal_ally_epic | 治疗友军怪 | 每5秒为5格内友方恢复5%HP, HP×1.05 |

#### 红色 LEGENDARY（7个）

| ID | 名称 | 效果 |
|----|------|------|
| bad_legendary | 怪物嗜血 | HP×1.15, 攻击×1.10, 力量II |
| bad_immune_legendary | 怪物免疫 | 火焰免疫, HP×1.08, 抗性I |
| bad_fire_berserk_legendary | 狂暴火元素 | 火焰免疫, 火焰减免50%, 攻击×1.10, HP×1.10 |
| bad_titan_legendary | 巨像怪 | HP×1.25, 护甲III, 攻击×1.12 |
| bad_heal_reduction_legendary | 抑制回血V | 回血减少70% |
| bad_corpse_chain_legendary | 尸爆连锁怪 | 死亡爆炸引爆4格内其他死亡怪物, HP×1.08 |
| bad_1up_legendary | 1UP复活怪 | 死亡后原地满血复活一次, HP×1.05 |

#### 金色 MYTHIC（5个）

| ID | 名称 | 效果 |
|----|------|------|
| bad_mythic | 怪物梦魇 | HP×1.20, 攻击×1.15, 速度×1.10, 力量II, 护甲II |
| bad_godly_mythic | 怪物神体 | 吸血5%, 攻击×1.10, HP×1.12, 护甲II, 火焰免疫, 5%召唤 |
| bad_element_immune_mythic | 元素免疫怪 | 火焰免疫, 全元素减免50%, HP×1.15, 攻击×1.10, 抗性II |
| bad_doom_mythic | 末日使者 | HP×1.15, 攻击×1.15, 速度×1.10, 护甲III, 反伤20%, 力量II |
| bad_heal_reduction_mythic | 抑制回血VI | 回血减少90% |

---

## 二、随机事件列表（96个）

### 触发机制
- 每位玩家独立计时器，默认每60秒触发
- 随机选择 物品(1/3) / Buff(1/3) / 事件(1/3)
- 征召战场期间停止随机事件
- 危险模式开关控制是否包含危险事件

### 事件分类

| 分类 | 数量 | 事件ID范围 |
|------|------|-----------|
| 天气/时间 | 7 | 0,2,3,4,12,13,59 |
| 友好/奖励 | 18 | 5,6,7,8,9,19,20,21,22,25,26,60,69,70,71,72,73,74 |
| 临时增益Buff | 10 | 14,29,30,31,32,33,34,35,36,37,38 |
| 祝福增益 | 10 | 45,76,77,78,79,80,81,82,83,84,85,86,87,88 |
| 敌对生成(危险) | 25 | 10,11,15,16,17,18,39,40,41,47-58 |
| 模组入侵 | 3 | 42,43,44 |
| 环境/趣味 | 10 | 1,23,24,27,28,61,62,63,64,65,66,67,68,75 |
| 挑战Boss级 | 7 | 89,90,91,92,93,94,95 |

### 完整事件列表

| ID | 名称 | 类型 | 效果 |
|----|------|------|------|
| 0 | 天降雷霆 | 安全 | 召唤仅视觉闪电 |
| 1 | 空间扭曲 | 安全 | 随机传送±50格 |
| 2 | 下雨 | 安全 | 天气设为下雨 |
| 3 | 雷暴 | 安全 | 天气设为雷暴 |
| 4 | 天气放晴 | 安全 | 天气设为晴天 |
| 5 | 友好生物 | 安全 | 召唤1只友好生物 |
| 6 | 烟花 | 安全 | 获得4枚烟花火箭 |
| 7 | 治愈之光 | 安全 | 满血+10秒生命恢复II |
| 8 | 饱腹 | 安全 | 饱食度补满 |
| 9 | 经验雨(基础) | 安全 | 获得20-99经验 |
| 10 | 敌对生物靠近 | 危险 | 召唤1只敌对生物 |
| 11 | TNT警告 | 危险 | 附近生成1个点燃TNT |
| 12 | 夜幕降临 | 安全 | 时间设为13000 |
| 13 | 黎明 | 安全 | 时间设为1000 |
| 14 | 风的加护 | 安全 | 30秒速度IV+跳跃III |
| 15 | 僵尸围攻 | 危险 | 召唤3-5只僵尸 |
| 16 | 骷髅小队 | 危险 | 召唤2-3只骷髅 |
| 17 | 苦力怕群 | 危险 | 召唤2只苦力怕 |
| 18 | 掠夺者袭击 | 危险 | 召唤2-3只掠夺者 |
| 19 | 空投物资 | 趣味 | 30-60格处生成箱子+信标标记 |
| 20 | 神秘商人 | 趣味 | 流浪商人，20分钟停留 |
| 21 | 双倍掉落祝福 | 趣味 | 120秒幸运I |
| 22 | 经验雨 | 趣味 | 5-8个经验瓶掉落 |
| 23 | 流星雨 | 稀有 | 6-10道视觉闪电 |
| 24 | 短暂无敌 | 稀有 | 3秒抗性255 |
| 25 | 双倍经验加成 | 稀有 | 60秒幸运+村庄英雄 |
| 26 | 强力武器 | 稀有 | 下界合金剑+60秒力量IV |
| 27 | 召唤模组生物 | 稀有 | 模组敌对生物1只 |
| 28 | 神秘建筑 | 稀有 | 随机生成建筑 |
| 29 | 仇恨吸引 | 增益 | 60秒强制吸引敌对生物 |
| 30 | 过境火焰 | 增益 | 30秒走过留火焰 |
| 31 | 点石成金 | 增益 | 60秒挖石掉矿物 |
| 32 | 磁铁吸引 | 增益 | 60秒吸引16格内物品 |
| 33 | 百花足迹 | 增益 | 60秒走过种花 |
| 34 | 雷霆体质 | 增益 | 30秒攻击30%闪电 |
| 35 | 跳跳人体质 | 增益 | 30秒持续弹跳 |
| 36 | 荧光显现 | 增益 | 30秒发光 |
| 37 | 冰霜光环 | 增益 | 30秒8格内生物缓慢II |
| 38 | 重击 | 增益 | 60秒攻击溅射50% |
| 39 | 混合袭击 | 危险 | 6-10只混合敌对生物 |
| 40 | 模组生物袭击 | 危险 | 4-7只模组敌对生物 |
| 41 | Boss级袭击 | 危险 | 1-2只Boss+3-8护卫 |
| 42 | 暮色森林入侵 | 危险 | 3-5只暮色森林生物 |
| 43 | 灾变裂缝 | 危险 | 1只灾变精英 |
| 44 | 变异爆发 | 危险 | 2-3只变异怪物 |
| 45 | 凤凰祝福 | 祝福 | 30秒火焰免疫+生命恢复II |
| 46 | 宝藏猎人 | 宝藏 | 30-50格处生成信标+宝箱 |
| 47 | 凋零骷髅头雨 | 危险 | 3-5个凋零头 |
| 48 | 守卫者伏击 | 危险 | 3-5只守卫者 |
| 49 | 幻翼群袭 | 危险 | 4-6只幻翼 |
| 50 | 恼鬼入侵 | 危险 | 3-5只恼鬼 |
| 51 | 末影人暴走 | 危险 | 3-4只末影人 |
| 52 | 凋零骷髅小队 | 危险 | 2-3只凋零骷髅 |
| 53 | 猪灵旅团 | 危险 | 3-5只猪灵 |
| 54 | 劫兽冲锋 | 危险 | 1-2只劫兽 |
| 55 | 蠹虫群涌 | 危险 | 6-10只蠹虫 |
| 56 | 洞穴蜘蛛伏击 | 危险 | 3-5只洞穴蜘蛛 |
| 57 | 僵尸村民群 | 危险 | 3-5只僵尸村民 |
| 58 | 末影螨入侵 | 危险 | 4-6只末影螨 |
| 59 | 时光倒流 | 趣味 | 时间设为黎明 |
| 60 | 幸运方块 | 趣味 | 金块+2-6随机幸运物品 |
| 61 | 花海盛宴 | 趣味 | 10x10范围种花 |
| 62 | 冰封大地 | 趣味 | 5格范围水变冰 |
| 63 | 岩浆涌出 | 危险 | 3x3岩浆池 |
| 64 | 降雪 | 趣味 | 天气设为雪 |
| 65 | 双段跳 | 趣味 | 30秒跳跃IV+缓慢降落 |
| 66 | 夜视祝福 | 趣味 | 60秒夜视+水下呼吸 |
| 67 | 荧光草地 | 趣味 | 8个光源方块 |
| 68 | 海之恩惠 | 趣味 | 60秒水下呼吸+海豚恩惠 |
| 69 | 附魔装备 | 奖励 | 1件附魔钻石装备 |
| 70 | 黄金雨 | 奖励 | 8-12个金锭+30-79经验 |
| 71 | 稀有宝箱 | 奖励 | 5-9格处宝箱+3-6件物品 |
| 72 | 经验瓶雨 | 奖励 | 8-12个经验瓶 |
| 73 | 钻石雨 | 奖励 | 3-5个钻石 |
| 74 | 免费附魔 | 奖励 | 主手物品附魔1个 |
| 75 | 铁砧雨 | 危险 | 3-4个铁砧方块 |
| 76 | 音乐祝福 | 奖励 | 60秒五种正面效果I |
| 77 | 幸运护符 | 奖励 | 30秒幸运+村庄英雄 |
| 78 | 英雄降临 | 奖励 | 60秒村庄英雄II+100-199经验 |
| 79 | 神圣护盾 | 祝福 | 30秒抗性II+吸收IV |
| 80 | 力量涌动 | 祝福 | 30秒力量III+急迫II |
| 81 | 疾风之翼 | 祝福 | 30秒速度III+跳跃II+缓慢降落 |
| 82 | 魔力流动 | 祝福 | 30秒急迫II+水下呼吸+夜视 |
| 83 | 自然祝福 | 祝福 | 30秒生命恢复II+饱和 |
| 84 | 火焰免疫套餐 | 祝福 | 30秒火焰免疫+力量I |
| 85 | 冰霜免疫套餐 | 祝福 | 30秒水下呼吸+海豚恩惠 |
| 86 | 再生加成 | 祝福 | 30秒生命恢复III+抗性I |
| 87 | 隐身斗篷 | 祝福 | 30秒隐身+速度II |
| 88 | 急迫光环 | 祝福 | 30秒急迫III+力量I |
| 89 | 强化头目 | 危险 | 强化掠夺者+劫兽 |
| 90 | 末影龙之仆 | 危险 | 3只强化末影人(60HP) |
| 91 | 下界入侵 | 危险 | 3-5只下界生物 |
| 92 | 女巫集会 | 危险 | 3只女巫 |
| 93 | 唤魔者袭击 | 危险 | 1唤魔者+2卫道士 |
| 94 | 凋零袭击 | 危险 | 2-3只凋零骷髅 |
| 95 | 卫道士冲锋 | 危险 | 3-4只卫道士 |

---

## 三、超能力列表（29个）

### 抽取机制
- 公平袋子系统：29个超能力随机排列，每次抽取不放回
- 袋子剩余≤3时补充为全部29个并洗牌
- 保证每29次抽取内所有超能力都会出现至少一次
- 玩家首次进入服务器时自动抽取

### 通用超能力（13个）

| ID | 名称 | 类型 | 冷却 | 征召专属 | 效果 |
|----|------|------|------|---------|------|
| blink | 瞬移 | 主动 | 10s | 否 | 瞬移到准星命中点，30格范围 |
| wealthy | 家财万贯 | 被动 | 无 | 否 | 每60秒获得1-3个随机矿物 |
| fly | 飞行 | 主动 | 15s | 否 | 20秒创造飞行，CD从结束时算 |
| enchant | 附魔 | 主动 | 无 | 否 | 随机3-5个满级附魔，消耗10级经验 |
| thunder_strike | 雷霆之子 | 主动 | 22s | 否 | 准星位置雷击，8伤害/雨天16伤害 |
| near_death_recall | 濒死回溯 | 被动 | 120s | 否 | 致死时回溯3秒前位置+恢复半血 |
| decompose | 分解大师 | 主动 | 30s | 否 | 主手物品分解为合成材料 |
| parkour | 跑酷达人 | 被动 | 无 | 否 | 可二段跳 |
| weapon_master | 武器大师 | 被动 | 无 | 否 | 30%概率伤害翻倍 |
| toxin_immunity | 毒素免疫 | 被动+主动 | 30s | 否 | 永久免疫中毒/凋零/饥饿；G键15秒全异常清除 |
| time_dilation | 时间膨胀 | 主动 | 20s | 否 | 5格内敌对生物减速50%持续10秒 |
| life_drain | 生命汲取 | 被动 | 无 | 否 | 攻击时恢复2点生命值 |
| gravity_pull | 重力操控 | 主动 | 25s | 否 | 10格内敌对生物拉向自己+3坠落伤害 |

### 征召世界专属超能力（16个）

| ID | 名称 | 类型 | 冷却 | 效果 |
|----|------|------|------|------|
| rapid_rescue | 急速救援 | 被动 | 无 | 站在倒地队友旁救助时间从5秒缩短为2秒 |
| battle_fury | 战场狂怒 | 被动 | 无 | 征召世界中击杀怪物后10秒内攻击力+30% |
| guardian_shield | 守护之盾 | 被动 | 无 | 8格内队友获得20%伤害减免 |
| battle_medic | 战场医疗兵 | 被动 | 无 | 每秒为8格内血量最低队友恢复5HP |
| iron_fortress | 铁壁堡垒 | 被动 | 无 | 原地不动或蹲下3秒后获得抗性II+击退免疫，移动后消失 |
| hunt_mark | 猎杀标记 | 主动 | 20s | 标记准星目标，全队对其伤害+50%持续10秒 |
| war_stomp | 战争践踏 | 主动 | 15s | 践踏地面，8格内敌人受5伤害+击飞+缓慢II |
| undying_body | 不死之身 | 被动 | 180s | 致死时复活30%血量+3秒无敌，清除负面效果 |
| frost_domain | 冰霜领域 | 被动 | 无 | 5格内敌对生物移速-30%，攻击速度-15% |
| flame_storm | 烈焰风暴 | 主动 | 25s | 准星位置生成火旋风，6格内每秒4伤害+燃烧5秒 |
| soul_link | 灵魂链接 | 被动 | 无 | 受到治疗的50%分享给20格内最近队友 |
| doom_strike | 毁灭终结 | 主动 | 30s | 下一次攻击3倍伤害，10秒内不用则失效 |
| life_spring | 生命之泉 | 主动 | 30s | 准星位置创建治疗区域，8格内队友每秒恢复2HP持续10秒 |
| war_god_might | 战神之力 | 被动+主动 | 30s | 被动战场内攻击+20%，G键10秒内再+50% |
| elemental_armor | 元素护甲 | 被动 | 无 | 战场内免疫火焰/冰霜/闪电伤害 |
| death_touch | 死亡之触 | 主动 | 60s | 对HP低于20%的敌人立即击杀 |

---

## 四、征召战场配置

| 参数 | 值 | 代码位置 |
|------|-----|---------|
| 首次触发事件数 | 40次 | BattlefieldManager.FIRST_BATTLEFIELD_EVENT_THRESHOLD |
| 之后触发间隔 | 30次 | BattlefieldManager.EVENTS_PER_BATTLEFIELD |
| 竞技场半径 | 35格 | BattlefieldDimension.ARENA_RADIUS |
| 准备房间时长 | 30秒(600tick) | BattlefieldManager.PREP_ROOM_DURATION |
| 准备房间位置 | (200,64,0) | BattlefieldManager.PREP_ROOM_X/Y/Z |
| 波次间隔 | 30秒(600tick) | BattlefieldManager.WAVE_INTERVAL_TICKS |
| 基础波次数 | 3 | BattlefieldManager.BASE_WAVES |
| 难度5+波次 | 4 | BASE_WAVES + 1 |
| 难度10+波次 | 5 | BASE_WAVES + 2 |
| 每波Boss数 | 1 | spawnWave() |
| 每波小怪数 | 玩家数×2+难度 | spawnWave() |
| 第2波起精英怪 | 1+波次/2 | spawnWave() |
| 倒地救助时间 | 5秒(100tick) | DownedStateManager.REVIVE_HOLD_TICKS |
| 急速救援救助时间 | 2秒(40tick) | DownedStateManager.RAPID_RESCUE_REVIVE_TICKS |
| 救助范围 | 5格 | DownedStateManager.ALLY_REVIVE_MAX_DISTANCE |
| Boss抗性上限 | Resistance I(20%减伤) | enhanceBoss() |
| Boss HP倍率 | min(1+难度×0.25, 4.0) | enhanceBoss() |
| 小怪HP倍率 | min(1+难度×0.15, 3.0) | enhanceMob() |
| 敌对生物伤害免疫上限 | 60% | PlayerAffixManager.getTotalHostileDamageImmunity() |
| 战场Boss不受 | 坏词条伤害免疫加成 | RandomSurpriseMod.onLivingHurt |

### 已排除的特殊Boss（11个）

| Boss ID | 原因 |
|---------|------|
| minecraft:ender_dragon | 需摧毁末影水晶 |
| minecraft:elder_guardian | 永久挖掘疲劳 |
| twilightforest:hydra | 特殊阶段机制 |
| twilightforest:snow_queen | 特殊阶段机制 |
| twilightforest:ur_ghast | 飞行+炮塔机制 |
| aether:slider | 仅移动时可攻击 |
| aether:valkyrie_queen | 需对话触发 |
| aether:sun_spirit | 冰火阶段机制 |
| bosses_of_mass_destruction:gaia | 地形交互 |
| cataclysm:the_leviathan | 水下战斗 |
| cataclysm:netherite_monstrosity | 岩浆免疫 |

---

## 五、商店配置

### 分类标签（10个）
1. 金币兑换 - 用资源换金币
2. 模组物品 - 守护护符、清除药水、钱袋子等
3. 强力附魔书 - 13本预附魔书
4. 稀有物品 - 不死图腾、鞘翅、下界之星等
5. 武器装备 - 无附魔钻石装备
6. 预附魔装备 - 自带附魔的武器防具
7. 消耗品 - 金苹果、末影珍珠等
8. 资源兑换 - 5对矿物双向兑换
9. 模组装备 - 已安装模组的装备
10. 稀有回收 - 动态扫描注册物品，用物品换金币

### 货币类型
- UNIVERSAL_COIN（金币，支持钱袋子扣除）
- DIAMOND（钻石）
- EMERALD（绿宝石）
- NETHERITE_INGOT（下界合金锭）
- LOTTERY_TICKET（抽奖券）
- IRON_INGOT / GOLD_INGOT / COAL / REDSTONE / LAPIS_LAZULI（资源兑换）

### 金币扣除逻辑
1. 先扣除背包中松散金币
2. 松散不足时从钱袋子扣除差额
3. 钱袋子使用 MoneyBagItem.removeCoins(stack, amount)

---

## 修改指南

### 添加新词条
1. 在 `AffixRegistry.java` 的 `static {}` 块中添加 `ALL_AFFIXES.add(new Affix(...))`
2. 在 `zh_cn.json` 中添加 `affix.randomsurprise.<id>.name` 和 `.desc` 翻译
3. 如需新效果类型，在 `Affix.java` 中添加字段并更新构造器
4. 在 `PlayerAffixManager.java` 中添加效果应用逻辑
5. 同步更新此文件

### 添加新随机事件
1. 在 `SurpriseActions.java` 中添加新的事件方法
2. 在 `SurpriseManager.java` 的事件列表中注册
3. 在 `zh_cn.json` 中添加翻译
4. 同步更新此文件

### 添加新超能力
1. 在 `SuperPower.java` 枚举中添加新值
2. 在 `SuperPowerHandler.java` 中实现逻辑（tick/active/damage事件）
3. 在 `zh_cn.json` 中添加翻译
4. 在 `SuperPowerLotteryScreen.java` 中确认转盘显示正常
5. 同步更新此文件

### 修改征召战场参数
1. 修改 `BattlefieldManager.java` 中的常量
2. 同步更新此文件的"征召战场配置"部分
