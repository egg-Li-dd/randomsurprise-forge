# RandomSurprise · 随机惊喜

> Minecraft **Forge 1.20.1** 的硬核生存玩法扩展。每 60 秒给每位在线玩家随机发放物品 / 增益 / 敌对效果 / 随机事件 —— 并围绕这套核心循环长出词条、超能力、征召战场、商店经济与随机建筑一整套系统。

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-3C8527?style=flat-square)](#)
[![Forge](https://img.shields.io/badge/Forge-47.3.33-E04E14?style=flat-square)](#)
[![Java](https://img.shields.io/badge/Java-17-007396?style=flat-square)](#)
[![License](https://img.shields.io/badge/License-All_Rights_Reserved-inactive?style=flat-square)](LICENSE)

---

## ✨ 功能总览

| 系统 | 说明 |
| :--- | :--- |
| 🎲 **随机惊喜** | 每 60 秒（可配置，最低 10 秒）随机触发物品 / 增益 / 敌对效果 / 事件。入侵态与征召战场进行期间自动停发随机事件，避免与战场叠加 |
| 🏷️ **词条系统** | 100+ 条增益 / 敌对词条，6 级稀有度。抽奖券抽取，专用老虎机转盘界面；词条采用「先看结果、确认后才生效」的两步机制 |
| ⚡ **超能力** | 16 个主动 / 被动超能力，公平袋子抽取（不放回，剩 ≤3 时重洗）。含瞬移、飞行、时间膨胀、重力操控、生命汲取等，其中 3 个为征召世界专属 |
| ⚔️ **征召战场** | 状态机驱动的多波次战斗，独立维度竞技场（70×70）。Boss 池按难度抽取，倒地后需队友靠近救助 |
| 🛒 **商店与经济** | 金币 / 通用券双货币，覆盖模组物品、强力附魔书、稀有回收。含套利检测与价格平衡配置 |
| 🏗️ **随机建筑** | 世界内随机生成建筑与五级稀有度战利品箱；优先使用已装模组自带的结构文件 |
| 🔌 **兼容层** | 可选集成 Jade 悬浮提示、ToroHealth 血条 HUD、Infernal Mobs 精英怪生成率动态调节 |

### 玩法要点

- **计时器 HUD** — 屏幕常驻倒计时，每秒同步剩余时间
- **倒地与救助** — 玩家倒地后免疫伤害、被怪物忽略；仅队友靠近可持续救助复活
- **平衡性护栏** — 坏词条为加法叠加而非乘法，并按属性分别封顶（血量 2.0×、伤害 1.5×、速度 1.3×）
- **经济防套利** — 资源兑换与商店回收价内置套利检测，并由单元测试持续校验

---

## 📦 安装

1. 安装 **Minecraft 1.20.1** 与 **Forge 47.3.33**（47.x 系列均可）
2. 把 `randomsurprise-1.0.0.jar` 放进 `.minecraft/mods/`
3. 启动游戏，选择 Forge 1.20.1 配置进入

> **多人游戏**：模组逻辑主要在服务端运行，但包含客户端界面（HUD / 转盘 / 商店）。建议**服务端与客户端都安装**，否则 Forge 的 mod 列表校验会导致连接被拒。

### 可选依赖

| 模组 | 版本 | 作用 | 必需 |
| :--- | :--- | :--- | :--- |
| [Jade](https://www.curseforge.com/minecraft/mc-mods/jade) | ≥ 11.13.0 | 方块 / 实体悬浮信息集成 | ❌ 仅客户端 |
| [ToroHealth](https://www.curseforge.com/minecraft/mc-mods/torohealth-damage-indicators) | ≥ 1.0.0 | 生物血条 HUD 集成 | ❌ 仅客户端 |
| [Infernal Mobs](https://www.curseforge.com/minecraft/mc-mods/atomicstrykers-infernal-mobs) | — | 精英怪生成率动态调节 | ❌ 服务端 |

以上均通过反射 / 软依赖方式接入，**不装也不影响主模组加载**。

---

## 🔗 与第三方模组的关系

本模组**不依赖任何特定模组**，也不附带第三方模组的代码或资源。

物品池、生物池、结构战利品均在运行时扫描游戏注册表（`ForgeRegistries` / `BuiltInRegistries`）动态生成：

- 整合包中装了什么物品 / 生物模组，它们的内容就会**自动进入随机池**；
- 另有部分精选条目（Boss 池、商店商品、战利品表）显式引用了一些知名模组（如 Alex's Mobs、Twilight Forest、L_Ender's Cataclysm 等）—— 未安装时这些条目静默跳过，不影响加载。

> 换句话说：**装得多，惊喜多；一个不装，也能完整游玩**。

---

## 🛠 从源码构建

需要 **JDK 17**（Forge 1.20.1 强制要求）。

```bash
# Windows 下需先设置 JAVA_HOME 指向 JDK 17
export JAVA_HOME=/path/to/jdk-17
./gradlew build

# 产物
build/libs/randomsurprise-1.0.0.jar
```

运行单元测试（经济平衡与词条叠加校验）：

```bash
./gradlew test
```

---

## 📂 项目结构

```
src/main/java/com/randomsurprise/
├── RandomSurpriseMod.java      # 主类，事件总线装配
├── SurpriseManager.java        # 计时器 + 随机事件触发门控
├── SurpriseActions.java        # 随机事件执行逻辑
├── SurpriseConfig.java         # 间隔等配置（JSON 持久化）
├── BuildingGenerator.java      # 随机建筑生成
├── affix/                      # 词条系统
├── superpower/                 # 超能力系统
├── battlefield/                # 征召战场（状态机 / Boss 池 / 倒地）
├── shop/                       # 商店与稀有回收
├── building/                   # 模组结构战利品
├── network/                    # 网络通道与数据包
├── config/                     # 平衡性配置与计算
├── compat/                     # Jade / ToroHealth / Infernal Mobs 集成
├── client/                     # 客户端界面与 HUD
└── menu/                       # 容器菜单

src/test/java/com/randomsurprise/config/
├── EconomyBalanceTest.java     # 套利检测
└── AffixStackingTest.java      # 词条叠加与属性封顶
```

- `文档分布.md` — 全项目文件功能索引，改动前建议先读
- `.trae/skills/randomsurprise-data/SKILL.md` — 词条 / 事件 / 超能力 / 战场 / 商店的完整数据手册

---

## 📄 许可

**All Rights Reserved**（保留所有权利）— 详见 [LICENSE](LICENSE)。

- 本仓库不附带、不分发任何第三方模组
- 开发期引用的 Jade / ToroHealth jar 仅作 `compileOnly` 依赖，已排除在仓库与产物之外
- Minecraft Forge 的 LGPL 2.1 许可与致谢文本见 `docs/forge/`
