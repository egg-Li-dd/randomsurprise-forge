package com.randomsurprise;

import com.randomsurprise.affix.GuardianCharmItem;
import com.randomsurprise.affix.HostileEnhancer;
import com.randomsurprise.affix.LotteryTicketMiningHandler;
import com.randomsurprise.affix.ModItems;
import com.randomsurprise.affix.PlayerAffixManager;
import com.randomsurprise.affix.TicketDropHandler;
import com.randomsurprise.buff.PlayerBuffs;
import com.randomsurprise.compat.CompatRegistry;
import com.randomsurprise.network.ModNetworking;
import com.randomsurprise.superpower.PlayerSuperPowerManager;
import com.randomsurprise.superpower.SuperPowerHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 随机惊喜模组主入口（Forge 1.20.1 版本）
 * - 每60秒为每位在线玩家随机给予物品、Buff或触发趣味事件
 * - 抽奖券系统：消耗10级经验抽奖，获得永久词条（好坏词条）
 * - 坏词条会增强敌对生物
 */
@Mod(RandomSurpriseMod.MOD_ID)
public class RandomSurpriseMod {
public static final String MOD_ID = "randomsurprise";
public static final Logger LOGGER = LoggerFactory.getLogger("RandomSurprise");

private static SurpriseManager manager;

public RandomSurpriseMod() {
IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

// 注册物品
ModItems.register(modEventBus);

// 注册模组袋菜单类型
com.randomsurprise.menu.ModBagMenu.register(modEventBus);

// 注册网络通信
ModNetworking.register();

// 初始化惊喜管理器
manager = new SurpriseManager();

// 注册 Forge 事件总线（服务端事件）
MinecraftForge.EVENT_BUS.register(this);

// 注册第三方模组兼容性（Jade / ToroHealth，可选）
CompatRegistry.register(modEventBus);

// 加载配置
SurpriseConfig.load();

LOGGER.info("随机惊喜模组已加载！自动惊喜间隔: {}秒 | 抽奖系统已启用",
SurpriseConfig.getIntervalSeconds());
}

// ========== 服务器 Tick 事件 ==========

	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		MinecraftServer server = event.getServer();
		if (server == null) return;

		manager.onServerTick(server);
		PlayerBuffs.onServerTick(server);
		SuperPowerHandler.onServerTick(server);
		BuildingGenerator.onServerTick(server);
		DaytimeSpawnManager.onServerTick(server);
		com.randomsurprise.affix.PlayerAffixManager.onServerTick(server);
		// v13: 敌对生物周期性坏词条机制（狂暴/再生/隐身/雷暴/火球/治疗友军/护盾/黑暗光环）
		com.randomsurprise.affix.HostileEnhancer.onServerTick(server);
		com.randomsurprise.cleanup.ItemCleanupHandler.onServerTick(server);
		// v19: Infernal Mobs 动态精英怪生成率调节
		com.randomsurprise.compat.InfernalMobsCompat.onServerTick(server);
	}

// ========== 玩家登录/退出事件 ==========

@SubscribeEvent
public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
if (event.getEntity() instanceof ServerPlayer player) {
manager.onPlayerJoin(player);
PlayerAffixManager.applyGoodEffects(player);
PlayerAffixManager.syncToClient(player);
com.randomsurprise.battlefield.BattlefieldManager.onPlayerJoin(player);
SuperPowerHandler.onPlayerJoin(player);
// 同步稀有回收物品列表到客户端
com.randomsurprise.network.ModNetworking.sendExchangeList(player);
// 新手礼包：首次进入游戏时发放
giveStarterKit(player);
}
}

/**
 * 新手礼包：玩家首次进入游戏时发放
 * 使用 NBT 持久化数据标记，防止重复领取
 */
private static void giveStarterKit(ServerPlayer player) {
var data = player.getPersistentData();
if (data.getBoolean("randomsurprise.starter_kit_received")) {
return;
}

var inventory = player.getInventory();
// 抽奖券 ×5（用于抽取永久词条）
inventory.add(new ItemStack(ModItems.LOTTERY_TICKET.get(), 5));
// 金币 ×64（商店消费货币）
inventory.add(new ItemStack(ModItems.UNIVERSAL_COIN.get(), 64));
// 钱袋子 ×1（存储金币）
inventory.add(new ItemStack(ModItems.MONEY_BAG.get(), 1));
// 守护护符 ×1（致命伤害保护）
inventory.add(new ItemStack(ModItems.GUARDIAN_CHARM.get(), 1));
// 净化药水 ×1（清除负面效果）
inventory.add(new ItemStack(ModItems.PURIFY_POTION.get(), 1));

// 标记已领取
data.putBoolean("randomsurprise.starter_kit_received", true);

// 发送欢迎消息
player.sendSystemMessage(Component.literal("§6§l✦ 欢迎来到随机惊喜世界！§r"));
player.sendSystemMessage(Component.literal("§a你获得了新手礼包：§r"));
player.sendSystemMessage(Component.literal("§7  • 抽奖券 ×5"));
player.sendSystemMessage(Component.literal("§7  • 金币 ×64"));
player.sendSystemMessage(Component.literal("§7  • 钱袋子 ×1"));
player.sendSystemMessage(Component.literal("§7  • 守护护符 ×1"));
player.sendSystemMessage(Component.literal("§7  • 净化药水 ×1"));
player.sendSystemMessage(Component.literal("§e使用抽奖券可抽取永久词条，金币可在商店购买物品！§r"));

LOGGER.info("玩家 {} 领取了新手礼包", player.getName().getString());
}

@SubscribeEvent
public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
if (event.getEntity() instanceof ServerPlayer player) {
manager.onPlayerQuit(player);
PlayerAffixManager.onPlayerQuit(player.getUUID());
PlayerBuffs.onPlayerQuit(player.getUUID());
SuperPowerHandler.onPlayerQuit(player.getUUID());
SuperPowerHandler.onPlayerQuitBattlefield(player.getUUID());
DaytimeSpawnManager.onPlayerQuit(player.getUUID());
// 清理倒地状态，防止断线重连后状态异常
com.randomsurprise.battlefield.DownedStateManager.clearDowned(player.getUUID());
}
}

// ========== 玩家复活事件（恢复战场死亡前的游戏模式） ==========

@SubscribeEvent
public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
if (event.getEntity() instanceof ServerPlayer player) {
com.randomsurprise.battlefield.BattlefieldManager.onPlayerRespawn(player);
// 清除倒地状态，防止复活后残留
com.randomsurprise.battlefield.DownedStateManager.clearDowned(event.getEntity().getUUID());
}
}

// ========== 服务器启动/停止事件 ==========

@SubscribeEvent
public void onServerStarted(ServerStartedEvent event) {
MinecraftServer server = event.getServer();
PlayerAffixManager.init(server);
com.randomsurprise.battlefield.BattlefieldManager.init(server);
PlayerSuperPowerManager.init(server);
com.randomsurprise.shop.ShopManager.init(server);
}

@SubscribeEvent
public void onServerStopping(ServerStoppingEvent event) {
MinecraftServer server = event.getServer();
SurpriseConfig.save();
PlayerAffixManager.save();
com.randomsurprise.battlefield.BattlefieldManager.save();
PlayerSuperPowerManager.save();
com.randomsurprise.shop.ShopManager.save();
// v19: 恢复 Infernal Mobs 原始配置
com.randomsurprise.compat.InfernalMobsCompat.onServerStopping();
}

// ========== 实体加载事件（敌对生物增强） ==========

@SubscribeEvent
public void onEntityJoinLevel(EntityJoinLevelEvent event) {
if (event.getEntity() instanceof Mob mob) {
HostileEnhancer.enhanceHostile(mob);
}
// v15: 追踪 Modular Golems 傀儡的主人（自动绑定附近玩家为 summon_owner）
if (event.getEntity() instanceof net.minecraft.world.entity.LivingEntity living) {
try {
var entityType = living.getType();
var resourceLocation = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
if (resourceLocation != null && "modulargolems".equals(resourceLocation.getNamespace())) {
// 检查是否已有 summon_owner 标记
if (PlayerAffixManager.getGolemOwnerUUID(living) == null) {
// 查找附近 5 格内的玩家
var level = event.getLevel();
if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
var nearestPlayer = serverLevel.getNearestPlayer(
living.getX(), living.getY(), living.getZ(), 5.0, false);
if (nearestPlayer instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
PlayerAffixManager.bindGolemOwner(living, serverPlayer.getUUID());
}
}
}
}
} catch (Throwable ignored) {}
}
}

// ========== 生物死亡事件 ==========

@SubscribeEvent
public void onLivingDeath(LivingDeathEvent event) {
var entity = event.getEntity();
// 抽奖券掉落 + Boss死亡检测
TicketDropHandler.onEntityDeath(entity);
if (entity instanceof Mob mob && !mob.level().isClientSide()) {
try {
String entityId;
try {
entityId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
.getKey(mob.getType()).toString();
} catch (Throwable t) {
entityId = "";
}
if (com.randomsurprise.battlefield.BossPool.isBoss(entityId)) {
com.randomsurprise.battlefield.BattlefieldManager.onBossDeath(mob.getUUID());
}
} catch (Throwable t) {
LOGGER.warn("Boss死亡检测异常: {}", t.getMessage());
}
}
// 玩家死亡累计
if (entity instanceof ServerPlayer serverPlayer) {
com.randomsurprise.battlefield.BattlefieldManager.onPlayerDeath(serverPlayer);
}
// 超能力：战场狂怒（征召世界击杀怪物后获得力量II）
SuperPowerHandler.onLivingDeath(entity, event.getSource());
// v13: 词条机制 - 玩家击杀效果（死亡引爆/守卫护盾/药剂渴望/灵魂虹吸）
com.randomsurprise.affix.PlayerAffixManager.onLivingDeath(entity, event.getSource());
// v13: 词条机制 - 敌对生物死亡效果（死亡爆炸/死亡分裂/尸体连锁/1UP复活）
com.randomsurprise.affix.HostileEnhancer.onLivingDeath(entity, event.getSource());
// v15: 清除傀儡词条状态（傀儡死亡时清除所有状态 Map 条目）
var golemOwnerUUID = PlayerAffixManager.getGolemOwnerUUID(entity);
if (golemOwnerUUID != null) {
PlayerAffixManager.onGolemDeath(entity.getUUID());
}
// 清除倒地状态，防止复活后残留
com.randomsurprise.battlefield.DownedStateManager.clearDowned(entity.getUUID());
}

// ========== 致命伤害拦截（守护护符/濒死回溯/战场倒地） ==========

@SubscribeEvent(priority = EventPriority.HIGH)
public void onAllowDeath(LivingDeathEvent event) {
if (event.getEntity() instanceof ServerPlayer player) {
	// 战场倒地机制：玩家在战场内血量清零时不立即死亡，而是进入倒地状态
	if (com.randomsurprise.battlefield.DownedStateManager.tryDown(player)) {
		event.setCanceled(true);
		return;
	}
	if (!SuperPowerHandler.onAllowDeath(player, event.getSource(), player.getMaxHealth())) {
		event.setCanceled(true);
		return;
	}
	if (!GuardianCharmItem.handleFatalDamage(player)) {
		event.setCanceled(true);
	}
}
}

// ========== 伤害事件（LivingHurtEvent - 原始伤害值） ==========

@SubscribeEvent
public void onLivingHurt(LivingHurtEvent event) {
float amount = event.getAmount();
var entity = event.getEntity();
var source = event.getSource();
// 倒地玩家免疫所有伤害
if (entity instanceof ServerPlayer player
		&& com.randomsurprise.battlefield.DownedStateManager.isDowned(player.getUUID())) {
	event.setCanceled(true);
	return;
}
// 倒地玩家无法攻击（攻击者为倒地玩家时取消伤害）
if (source.getEntity() instanceof ServerPlayer attacker
		&& com.randomsurprise.battlefield.DownedStateManager.isDowned(attacker.getUUID())) {
	event.setCanceled(true);
	return;
}
// 铁傀儡召唤保护机制（仅对玩家召唤的铁傀儡生效）
if (handleGolemProtection(entity, source)) {
event.setCanceled(true);
return;
}
// v15: 傀儡词条效果（火焰/溺水/摔落免疫、反伤、吸血）
if (PlayerAffixManager.applyGolemAffixHurtEffects(entity, source, amount)) {
event.setCanceled(true);
return;
}
// 超能力：守护之盾（征召世界队友伤害减免20%）
amount = SuperPowerHandler.onLivingHurtReduceDamage(entity, source, amount);
event.setAmount(amount);
// v5: 伤害免疫率（抗性词条替换）
// 玩家伤害免疫（最多90%）
if (entity instanceof ServerPlayer player
		&& !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
	double immunity = com.randomsurprise.affix.PlayerAffixManager.getTotalDamageImmunity(player.getUUID());
	if (immunity > 0) {
		event.setAmount(amount * (float)(1 - immunity));
		amount = event.getAmount();
	}
}
// 敌对生物伤害免疫（最多60%）
if (entity instanceof net.minecraft.world.entity.Mob mob
		&& (mob instanceof net.minecraft.world.entity.monster.Enemy || isHostileMobType(mob))
		&& !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
	// 战场Boss不受敌对生物伤害免疫加成（已在enhanceBoss中增强过，避免叠加导致无法击败）
	if (!com.randomsurprise.battlefield.BattlefieldManager.isBattlefieldBoss(mob.getUUID())) {
		double hostileImmunity = com.randomsurprise.affix.PlayerAffixManager.getTotalHostileDamageImmunity();
		if (hostileImmunity > 0) {
			event.setAmount(amount * (float)(1 - hostileImmunity));
			amount = event.getAmount();
		}
	}
}
// v5: 火焰伤害减免百分比（非金词条，金词条用FIRE_RESISTANCE药水100%免疫）
if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
	// 玩家火焰减免
	if (entity instanceof ServerPlayer player) {
		double fireRes = com.randomsurprise.affix.PlayerAffixManager.getTotalFireResistance(player.getUUID());
		if (fireRes > 0 && fireRes < 1.0) {
			event.setAmount(amount * (float)(1 - fireRes));
			amount = event.getAmount();
		}
	}
	// 敌对生物火焰减免
	if (entity instanceof net.minecraft.world.entity.Mob mob
			&& (mob instanceof net.minecraft.world.entity.monster.Enemy || isHostileMobType(mob))) {
		double hostileFireRes = com.randomsurprise.affix.PlayerAffixManager.getTotalHostileFireResistance();
		if (hostileFireRes > 0 && hostileFireRes < 1.0) {
			event.setAmount(amount * (float)(1 - hostileFireRes));
			amount = event.getAmount();
		}
	}
}
// v6: 闪电伤害减免
if (source.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING)) {
	if (entity instanceof ServerPlayer player) {
		double lightRes = com.randomsurprise.affix.PlayerAffixManager.getTotalLightningResistance(player.getUUID());
		if (lightRes > 0 && lightRes < 1.0) {
			event.setAmount(amount * (float)(1 - lightRes));
			amount = event.getAmount();
		}
	}
	// v7: 敌对生物闪电减免（坏词条扩展）
	if (entity instanceof net.minecraft.world.entity.Mob mob
			&& (mob instanceof net.minecraft.world.entity.monster.Enemy || isHostileMobType(mob))) {
		double hostileLightRes = com.randomsurprise.affix.PlayerAffixManager.getTotalHostileLightningResistance();
		if (hostileLightRes > 0 && hostileLightRes < 1.0) {
			event.setAmount(amount * (float)(1 - hostileLightRes));
			amount = event.getAmount();
		}
	}
}
// v6: 冰霜伤害减免
if (source.is(net.minecraft.tags.DamageTypeTags.IS_FREEZING)) {
	if (entity instanceof ServerPlayer player) {
		double frostRes = com.randomsurprise.affix.PlayerAffixManager.getTotalFrostResistance(player.getUUID());
		if (frostRes > 0 && frostRes < 1.0) {
			event.setAmount(amount * (float)(1 - frostRes));
			amount = event.getAmount();
		}
	}
	// v7: 敌对生物冰霜减免（坏词条扩展）
	if (entity instanceof net.minecraft.world.entity.Mob mob
			&& (mob instanceof net.minecraft.world.entity.monster.Enemy || isHostileMobType(mob))) {
		double hostileFrostRes = com.randomsurprise.affix.PlayerAffixManager.getTotalHostileFrostResistance();
		if (hostileFrostRes > 0 && hostileFrostRes < 1.0) {
			event.setAmount(amount * (float)(1 - hostileFrostRes));
			amount = event.getAmount();
		}
	}
}
// v6: 冰霜免疫（免疫减速效果）
if (entity instanceof ServerPlayer player
		&& com.randomsurprise.affix.PlayerAffixManager.hasFrostImmunity(player.getUUID())) {
	player.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
}
// 临时 Buff：雷霆体质（闪电）+ 重击（溅射）（使用原始伤害值）
PlayerBuffs.onEntityDamaged(entity, source, amount);
// 超能力：武器大师 30% 概率伤害翻倍（使用原始伤害值）
SuperPowerHandler.onEntityDamaged(entity, source, amount);
// v13: 词条机制 - 玩家受击效果（复仇反弹/守卫减伤）+ 敌对生物受击/攻击效果
com.randomsurprise.affix.PlayerAffixManager.onLivingHurt(event);
com.randomsurprise.affix.HostileEnhancer.onLivingHurt(event);
}

/** 判断实体是否为敌对生物类型（用于伤害免疫判定） */
private static boolean isHostileMobType(net.minecraft.world.entity.Mob mob) {
	String typeName = mob.getType().toShortString();
	return typeName.contains("zombie") || typeName.contains("skeleton")
			|| typeName.contains("creeper") || typeName.contains("spider")
			|| typeName.contains("enderman") || typeName.contains("witch")
			|| typeName.contains("phantom") || typeName.contains("pillager")
			|| typeName.contains("ravager") || typeName.contains("vindicator")
			|| typeName.contains("evoker") || typeName.contains("blaze")
			|| typeName.contains("ghast") || typeName.contains("wither")
			|| typeName.contains("slime") || typeName.contains("magma");
}

/**
 * 傀儡召唤保护机制（v15: 扩展支持所有 LivingEntity 类型，包括 Modular Golems）
 * 规则：
 * 1) 召唤者无法攻击自己召唤的傀儡 → 取消伤害
 * 2) 傀儡不得主动/反击其召唤者 → 取消伤害
 * 3) 任何生物攻击召唤者傀儡时，傀儡立即切换目标反击攻击者（最高优先级）
 * @return true 表示伤害被取消（应在 onLivingHurt 中立即 return）
 */
private static boolean handleGolemProtection(net.minecraft.world.entity.LivingEntity entity,
		net.minecraft.world.damagesource.DamageSource source) {
	var attacker = source.getEntity();
	var ownerUUID = PlayerAffixManager.getGolemOwnerUUID(entity);
	if (ownerUUID == null) return false; // 非玩家制作的傀儡

	// 规则1：召唤者攻击自己召唤的傀儡 → 取消伤害
	if (attacker instanceof net.minecraft.world.entity.player.Player player
			&& ownerUUID.equals(player.getUUID())) {
		return true;
	}
	// 规则2：傀儡攻击自己的召唤者 → 取消伤害（entity=玩家, attacker=傀儡）
	if (entity instanceof net.minecraft.world.entity.player.Player player
			&& attacker instanceof net.minecraft.world.entity.LivingEntity attackerLiving) {
		var golemOwner = PlayerAffixManager.getGolemOwnerUUID(attackerLiving);
		if (golemOwner != null && golemOwner.equals(player.getUUID())) {
			return true;
		}
	}
	// 规则3：任何生物攻击召唤者傀儡 → 傀儡立即反击攻击者（保护行为最高优先级）
	if (attacker instanceof net.minecraft.world.entity.LivingEntity livingAttacker
			&& !(attacker instanceof net.minecraft.world.entity.player.Player p
					&& ownerUUID.equals(p.getUUID()))) {
		if (entity instanceof net.minecraft.world.entity.Mob mob) {
			PlayerAffixManager.forceGolemRetaliate(mob, livingAttacker);
		}
	}
	return false;
}

// ========== 伤害事件（LivingDamageEvent - 实际伤害值） ==========

@SubscribeEvent
public void onLivingDamage(LivingDamageEvent event) {
float appliedAmount = event.getAmount();
var entity = event.getEntity();
var source = event.getSource();
// v10: 铁傀儡不被召唤者伤害
if (entity instanceof net.minecraft.world.entity.animal.IronGolem golem && source.getEntity() instanceof ServerPlayer player) {
	if (PlayerAffixManager.isPlayerSummonedGolem(golem, player)) {
		event.setCanceled(true);
		return;
	}
}
// 免伤机制：基于护甲值和装备的额外减伤
DamageReductionHandler.onAfterDamage(entity, source, appliedAmount);
// 词条效果：吸血/召唤
PlayerAffixManager.onEntityDamaged(entity, source, appliedAmount);
}

// ========== 方块破坏事件 ==========

@SubscribeEvent
public void onBlockBreak(BlockEvent.BreakEvent event) {
if (event.getLevel() instanceof ServerLevel serverLevel
&& event.getPlayer() instanceof ServerPlayer serverPlayer) {
BlockPos pos = event.getPos();
BlockState state = event.getState();
LotteryTicketMiningHandler.onBlockBreak(serverLevel, serverPlayer, pos, state);
PlayerBuffs.onBlockBreak(serverLevel, serverPlayer, pos, state);
}
}

// ========== v7: 玩家挖掘速度事件（词条挖掘速度加成） ==========

@SubscribeEvent
public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
PlayerAffixManager.onBreakSpeed(event);
}

// ========== v7: 玩家治疗加成事件（词条治疗加成） ==========

@SubscribeEvent
public void onLivingHeal(LivingHealEvent event) {
PlayerAffixManager.onLivingHeal(event);
// 超能力：灵魂链接（征召世界治疗分享）
SuperPowerHandler.onLivingHeal(event.getEntity(), event.getAmount());
}

// ========== 命令注册事件 ==========

@SubscribeEvent
public void onRegisterCommands(RegisterCommandsEvent event) {
SurpriseCommand.register(event.getDispatcher());
}

// ========== 失明效果免疫（v9）==========

@SubscribeEvent
public void onPlayerTick(TickEvent.PlayerTickEvent event) {
	if (event.phase != TickEvent.Phase.END) return;
	if (event.player instanceof ServerPlayer player) {
		if (player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)) {
			double resistance = PlayerAffixManager.getTotalBlindnessResistance(player.getUUID());
			if (resistance > 0 && Math.random() < resistance) {
				player.removeEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
			}
		}
	}
}

public static SurpriseManager getManager() {
return manager;
}
}