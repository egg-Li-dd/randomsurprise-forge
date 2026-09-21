# RandomSurprise 模组全面更新计划

> 基于 `MODS1.20.1fro/安装的mods` 中 19 个内容模组，对 5 大系统进行全面更新。
> 源码位置：`randomsurprise-forge-new/src/main/java/com/randomsurprise/`

---

## 阶段一：BOSS 系统修复与增强（优先级：高）

### 1.1 修复 BossPool 误判 Bug
**文件**: `battlefield/BossPool.java`

**问题**:
- `modulargolems` 命名空间的玩家自建傀儡被误判为 Boss（含 `golem` 关键词）
- `happyghastmod`、`naturalist`、`more_critters` 友好生物不应进 Boss 池
- BOMD（`bosses_of_mass_destruction`）Boss 被统一分到 Tier 5，实际应为 Tier 3-4

**修改**:
- 新增 `EXCLUDED_NAMESPACES` 集合：`modulargolems`、`happyghastmod`、`naturalist`、`more_critters`、`morecritters`
- 在 `classifyModdedBoss()` 开头检查命名空间排除
- 新增 `EXCLUDED_ENTITY_IDS` 集合：`cave_dweller:cave_dweller`（追逐型精英，非 Boss）

### 1.2 硬编码 Boss 精确分级
**文件**: `battlefield/BossPool.java`

新增 `HARDCODED_BOSS_TIERS` Map，精确定义已知模组 Boss 的 Tier：

| Tier | 硬编码 Boss ID |
|------|----------------|
| T2 | `mowziesmobs:naga`、`friendsandfoes:wildfire`、`friendsandfoes:iceologer`、`friendsandfoes:illusioner`、`enderzoology:ender_golem`、`twilightforest:naga`、`mutantmonsters:mutant_zombie`、`mutantmonsters:mutant_skeleton`、`mutantmonsters:mutant_creeper` |
| T3 | `mowziesmobs:frostmaw`、`mowziesmobs:ferrous_wroughtnaut`、`mowziesmobs:barako`、`twilightforest:lich`、`twilightforest:minoshroom`、`twilightforest:knight_phantom`、`twilightforest:alpha_yeti`、`aether:slider`、`bosses_of_mass_destruction:lich`、`mutantmonsters:mutant_enderman`、`illageandspillage:magispeller`、`illageandspillage:blastfinder`、`illageandspillage:tremozzarella` |
| T4 | `alexsmobs:void_worm`、`twilightforest:hydra`、`twilightforest:ur_ghast`、`twilightforest:snow_queen`、`aether:valkyrie_queen`、`bosses_of_mass_destruction:obsidilith`、`bosses_of_mass_destruction:gaia`、`bosses_of_mass_destruction:chorus_horror`、`cataclysm:ignited_revenant`、`cataclysm:kobolediator`、`cataclysm:the_watcher`、`cataclysm:withered_symbiont`、`cataclysm:obsidilith`、`cataclysm:the_harbinger` |
| T5 | `cataclysm:netherite_monstrosity`、`cataclysm:the_leviathan`、`cataclysm:withered_monarch`、`aether:sun_spirit` |

`classifyModdedBoss()` 优先查硬编码表，未命中再走关键词逻辑。

### 1.3 补充 Boss 关键词
新增关键词：`spirit`、`revenant`、`monstrosity`、`harbinger`、`symbiont`、`monarch`

### 1.4 Boss 奖励增强
**文件**: `affix/TicketDropHandler.java`

- Boss 死亡时根据 Tier 增加掉落：T3→4券、T4→5券、T5→8券（原固定3券）
- T4+ Boss 额外掉落稀有物品（下界之星/龙息/附魔金苹果，按 Tier 概率）
- 调整 `isBossEntity()` 使用 `BossPool.isBoss()` 统一判定，避免逻辑不一致

---

## 阶段二：商店系统扩展（优先级：高）

### 2.1 新增模组装备商品
**文件**: `shop/ShopManager.java`

在「模组装备」分类新增条目（动态解析，模组未装自动隐藏）：

| 物品 ID | 名称 | 价格 | 来源模组 |
|---------|------|------|---------|
| `mowziesmobs:wroughtnaut_sword` | 铁铸巨剑 | 32 钻石 | Mowzie's |
| `mowziesmobs:wroughtnaut_helmet` | 铁铸头盔 | 20 绿宝石 | Mowzie's |
| `mowziesmobs:wroughtnaut_chestplate` | 铁铸胸甲 | 32 绿宝石 | Mowzie's |
| `twilightforest:fiery_sword` | 炽热剑 | 24 钻石 | Twilight |
| `twilightforest:knightmetal_sword` | 骑士金属剑 | 16 钻石 | Twilight |
| `twilightforest:ice_sword` | 寒冰剑 | 16 钻石 | Twilight |
| `twilightforest:glass_sword` | 玻璃剑 | 12 钻石 | Twilight |
| `twilightforest:end_bow` | 末影弓 | 20 钻石 | Twilight |
| `twilightforest:triple_bow` | 三连弓 | 24 钻石 | Twilight |
| `twilightforest:seeker_bow` | 追踪弓 | 20 钻石 | Twilight |
| `aether:valkyrie_lance` | 女武神长矛 | 28 钻石 | Aether |
| `aether:phoenix_bow` | 凤凰弓 | 32 钻石 | Aether |
| `aether:gravitite_sword` | 重力晶石剑 | 20 钻石 | Aether |
| `aether:valkyrie_chestplate` | 女武神胸甲 | 32 绿宝石 | Aether |
| `cataclysm:maledictus` | 厄咒之刃 | 48 钻石 | Cataclysm |
| `cataclysm:bulwark_of_the_flame` | 烈焰壁垒 | 32 绿宝石 | Cataclysm |
| `cataclysm:the_incinerator` | 焚化者 | 40 钻石 | Cataclysm |
| `cataclysm:gauntlet_of_guard` | 守护臂铠 | 24 绿宝石 | Cataclysm |

### 2.2 新增稀有物品分类条目
| 物品 ID | 名称 | 价格 | 来源 |
|---------|------|------|-----|
| `twilightforest:charm_of_life` | 生命护符 | 32 绿宝石 | Twilight |
| `twilightforest:charm_of_keeping` | 守护护符 | 48 绿宝石 | Twilight |
| `twilightforest:peacock_fan` | 孔雀羽扇 | 24 钻石 | Twilight |
| `alexsmobs:shield_of_the_deep` | 深渊之盾 | 32 绿宝石 | Alex's |
| `alexsmobs:dimensional_carver` | 维度凿子 | 64 钻石 | Alex's |
| `friendsandfoes:wildfire_chestplate` | 野火胸甲 | 28 绿宝石 | F&F |
| `aether:lightning_knife` | 闪电飞刀(×16) | 16 绿宝石 | Aether |

### 2.3 新增消耗品
| 物品 ID | 名称 | 价格 | 来源 |
|---------|------|------|-----|
| `twilightforest:hydra_chop` | 九头蛇肉排(×4) | 8 绿宝石 | Twilight |
| `alexsmobs:banana` | 香蕉(×16) | 4 绿宝石 | Alex's |

### 2.4 购买限制机制
**文件**: `shop/ShopEntry.java` + `shop/ShopManager.java`

- ShopEntry 新增 `int purchaseLimit` 字段（0=无限，>0=限购次数）
- ShopManager 新增 `Map<UUID, Map<Integer, Integer>> purchaseRecords` 记录玩家购买次数
- processPurchase 检查限购：已达上限返回失败 "§c已达购买上限"
- 应用限购的商品：维度凿子(1)、龙蛋(1)、野火胸甲(1)、不死图腾(4)、附魔金苹果(8)
- 数据持久化到 `<world>/data/randomsurprise_shop.json`

---

## 阶段三：词条系统扩展（优先级：中）

### 3.1 新增好词条（6 个）
**文件**: `affix/AffixRegistry.java` + `affix/Affix.java`

| ID | 名称 | 稀有度 | 效果 |
|----|------|--------|------|
| good_haste_common | 急速之手 | 白 | 急迫I，挖掘速度+10% |
| good_knockback_uncommon | 击退之力 | 绿 | 击退增强，+4 HP |
| good_regen_rare | 生命再生 | 蓝 | 生命恢复I，+6 HP |
| good_slayer_epic | 屠龙者 | 紫 | 对 Boss 伤害+15%，+8 HP |
| good_thorns_legendary | 荆棘反伤 | 红 | 荆棘III，+10 HP，反伤20% |
| good_phoenix_mythic | 不灭凤凰 | 金 | 濒死时3秒无敌(120s冷却)，+15 HP，力量II，抗性II |

### 3.2 新增坏词条（4 个）
| ID | 名称 | 稀有度 | 效果 |
|----|------|--------|------|
| bad_speed_common | 疾速怪物 | 白 | 速度×1.15 |
| bad_thorns_uncommon | 反伤装甲 | 绿 | 玩家攻击怪物时受到10%反伤 |
| bad_regen_rare | 回血怪物 | 蓝 | 生命恢复I，血量×1.05 |
| bad_shield_epic | 护盾怪物 | 紫 | 抗性II，血量×1.08，速度×1.05 |

### 3.3 数值平衡调整
- 好词条经验加成统一调整为：白+5%、绿+10%、蓝+15%、紫+20%、红+30%、金+45%（原金50%略高）
- 坏词条血量倍率上限：单个词条不超过×1.25（当前最高×1.20，保持）
- 稀有度动态补偿上限从+10%调整为+15%（坏词条过多时提高高稀有度概率）

### 3.4 词条效果实现
- 屠龙者：在 `PlayerAffixManager.onEntityDamaged` 中检查目标是否为 Boss（`BossPool.isBoss`），是则额外+15%伤害
- 不灭凤凰：在 `onAllowDeath` 中检查冷却，触发时取消死亡+3秒无敌+全回血
- 击退之力：用 `Attributes.ATTACK_KNOCKBACK` AttributeModifier
- 荆棘反伤：用 `MobEffects.THORNS` 药水无法实现，改用 `onEntityDamaged` 事件回弹伤害
- 急速之手：`MobEffects.HASTE` 药水

---

## 阶段四：随机事件系统增强（优先级：中）

### 4.1 新增模组入侵事件（3 个）
**文件**: `SurpriseActions.java`

| 编号 | 事件名 | 权重 | 说明 |
|------|--------|------|------|
| 42 | eventTwilightInvasion | 1 | 召唤 3-5 个暮色森林生物（redcap/kobold/fire_beetle/hedge_spider/minotaur） |
| 43 | eventCataclysmRift | 1 | 召唤 1 个 Cataclysm 精英怪（ignited_revenant/ender_guard/enderseer 随机） |
| 44 | eventMutantOutbreak | 1 | 召唤 2-3 个变异怪物（mutant_zombie/skeleton/creeper） |

这些事件在 `allowDangerousEvents=false` 时不触发。实体 ID 动态解析，模组未装则降级为原版生物群。

### 4.2 新增增益事件（2 个）
| 编号 | 事件名 | 权重 | 说明 |
|------|--------|------|------|
| 45 | eventPhoenixBlessing | 1 | 30秒火焰免疫+生命恢复II |
| 46 | eventTreasureHunt | 2 | 在玩家附近生成一个藏宝点（信标+宝箱，含随机稀有物品） |

### 4.3 神秘商人扩展
**文件**: `SurpriseActions.java` 的 `eventMysteryMerchant`

新增 4 种交易：
- 1 绿宝石 → 1 twilightforest:charm_of_life（限1次）
- 2 绿宝石 → 1 aether:ambrosium_shard×8（限3次）
- 3 绿宝石 → 1 cataclysm:soul_mass（限1次）
- 1 钻石 → 1 alexsmobs:siderite×2（限2次）

---

## 阶段五：刷怪系统优化（优先级：中）

### 5.1 模组敌对生物池扩展
**文件**: `SurpriseActions.java` 的 `collectModdedHostileEntities`

补充已知模组敌对实体 ID 到白名单（确保即使名称不含关键词也能被收集）：
- `alexsmobs:crocodile`、`alexsmobs:crimson_mosquito`、`alexsmobs:bone_serpent`、`alexsmobs:froststalker`、`alexsmobs:mantis_shrimp`
- `twilightforest:redcap`、`twilightforest:kobold`、`twilightforest:fire_beetle`、`twilightforest:hedge_spider`、`twilightforest:minotaur`、`twilightforest:maze_slime`、`twilightforest:wraith`
- `enderzoology:concussion_creeper`、`enderzoology:fallen_knight`、`enderzoology:ender_golem`、`enderzoology:ender_wizard`
- `born_in_chaos:scarlet_demon`、`born_in_chaos:fallen_knight`、`born_in_chaos:dried_corpse`
- `mutantmonsters:mutant_zombie`、`mutantmonsters:mutant_skeleton`、`mutantmonsters:mutant_creeper`
- `illageandspillage:magispeller`、`illageandspillage:blastfinder`

### 5.2 BossRaid 事件扩展
**文件**: `SurpriseActions.java` 的 `eventBossRaid`

- Boss 池从硬编码 5 种扩展为从 `BossPool.getBossesForDifficulty(2)` 动态获取（含模组小 Boss）
- 护卫数量随难度增加：3 + difficulty/2（上限 8）
- 模组未装时兜底为原版 Boss

### 5.3 特殊掉落机制
**文件**: `affix/TicketDropHandler.java`

- 击杀模组命名空间的敌对生物时，额外 5% 概率掉落该模组的特色物品（从预设表随机）
- 例如：击杀 alexsmobs 敌对 → 5% 概率掉 `alexsmobs:bear_dust`；击杀 twilightforest 敌对 → 5% 概率掉 `twilightforest:steeleaf_ingot`
- 模组未装则无额外掉落（自然不触发）

---

## 阶段六：本地化与文档

### 6.1 语言文件更新
**文件**: `src/main/resources/assets/randomsurprise/lang/zh_cn.json`

新增所有新词条/事件/商品的翻译键。

### 6.2 蓝图同步更新
**文件**: `.trae/skills/randomsurprise-blueprint/SKILL.md`

按维护规则更新对应章节。

### 6.3 文档分布更新
**文件**: `randomsurprise-mod/文档分布.md`（或 Forge 版新建）

---

## 执行顺序

1. **阶段一**（BOSS 修复）→ 修复 Bug，影响最大
2. **阶段二**（商店扩展）→ 玩家可感知，测试容易
3. **阶段三**（词条扩展）→ 核心玩法
4. **阶段四**（事件增强）→ 内容丰富度
5. **阶段五**（刷怪优化）→ 平衡性
6. **阶段六**（本地化+文档）→ 收尾

## 风险与注意事项

- 所有模组物品使用 `BuiltInRegistries.ITEM.getOptional()` 动态解析，模组未装自动隐藏，不影响兼容性
- 实体 ID 可能在不同版本有拼写差异，建议首次测试时用 `/execute if entity @e[type=modid:xxx]` 验证
- 购买限制数据需要持久化，增加 `<world>/data/randomsurprise_shop.json` 文件
- 新增词条效果（荆棘反伤/屠龙者/不灭凤凰）需要在事件处理中增加逻辑，注意不影响性能
- Aether 为 NeoForge 版本，需确认与 Forge 整合包兼容
