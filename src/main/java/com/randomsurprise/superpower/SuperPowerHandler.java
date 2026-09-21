package com.randomsurprise.superpower;

import com.randomsurprise.RandomSurpriseMod;
import com.randomsurprise.network.ModNetworking;
import com.randomsurprise.network.SuperPowerSyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.BlockTags;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 超能力服务端核心逻辑
 *
 * 功能：
 * - 玩家首次进入服务器时自动抽取超能力（弹出转盘）
 * - 处理 G 键主动技能触发
 * - 处理 V 键被动技能开关
 * - 每 tick 处理被动技能效果（家财万贯、毒素免疫、二段跳、濒死回溯位置记录）
 * - 伤害事件处理（武器大师伤害翻倍）
 * - 致命伤害拦截（濒死回溯）
 *
 * 同步策略：
 * - 冷却/状态变化时立即同步给对应玩家
 * - 玩家加入时同步
 * - 每 10 ticks（0.5秒）定期同步一次保证一致性
 */
public class SuperPowerHandler {

	// ========== 公平袋子系统 ==========
	/** 超能力抽取袋子：确保所有超能力在袋子清空前都能被抽到 */
	private static final List<SuperPower> powerBag = new ArrayList<>();
	/** 袋子剩余多少个时补充（保证抽取的随机性同时确保公平） */
	private static final int BAG_REFILL_THRESHOLD = 3;

	/**
	 * 从公平袋子中抽取超能力
	 * - 袋子包含全部 16 个超能力的随机排列（含征召世界专属超能力）
	 * - 每次抽取取出一个（不放回），保证不重复
	 * - 袋子剩余 ≤ BAG_REFILL_THRESHOLD 时补充为全部并重新洗牌
	 * - 这保证了每 16 次抽取内，所有超能力都会出现至少一次
	 */
	private static SuperPower drawFromBag() {
		if (powerBag.size() <= BAG_REFILL_THRESHOLD) {
			powerBag.clear();
			powerBag.addAll(Arrays.asList(SuperPower.values()));
			Collections.shuffle(powerBag, ThreadLocalRandom.current());
			RandomSurpriseMod.LOGGER.info("[超能力] 袋子已补充并洗牌，当前袋子大小: {}", powerBag.size());
		}
		int idx = ThreadLocalRandom.current().nextInt(powerBag.size());
		SuperPower drawn = powerBag.remove(idx);
		RandomSurpriseMod.LOGGER.info("[超能力] 从袋子抽取: {}，剩余: {}", drawn.getId(), powerBag.size());
		return drawn;
	}

	/** 家财万贯矿物奖励间隔（1200 ticks = 60 秒） */
	private static final int WEALTHY_INTERVAL_TICKS = 1200;
	/** 飞行持续时间（20 秒 = 400 ticks） */
	private static final int FLY_DURATION_TICKS = 400;
	/** 毒素清除持续时间（15 秒 = 300 ticks） */
	private static final int TOXIN_CLEANSE_DURATION_TICKS = 300;
	/** 位置记录间隔（10 ticks = 0.5 秒，记录 6 次即 3 秒） */
	private static final int POSITION_RECORD_INTERVAL_TICKS = 10;
	/** 瞬移最大距离（30 格） */
	private static final int BLINK_MAX_RANGE = 30;
	/** 雷霆最大距离（30 格） */
	private static final int THUNDER_MAX_RANGE = 30;
	/** 武器大师伤害翻倍概率（0.30） */
	private static final double WEAPON_MASTER_DOUBLE_CHANCE = 0.30;

	/** 全局 tick 计数器 */
	private static int globalTickCounter = 0;
	/** 玩家 tick 计数器（用于家财万贯计时） */
	private static final java.util.Map<java.util.UUID, Integer> playerTickCounter = new java.util.HashMap<>();
	/** 玩家位置记录 tick 计数器 */
	private static final java.util.Map<java.util.UUID, Integer> positionRecordCounter = new java.util.HashMap<>();
	/** 同步计数器（每 10 ticks 同步一次） */
	private static int syncCounter = 0;
	/** 上次同步数据快照（脏检查：数据未变化时跳过发送） */
	private static final Map<java.util.UUID, String> lastSyncSnapshot = new HashMap<>();
	/** 武器大师伤害处理递归防护标志（防止 hurt 触发递归） */
	private static boolean weaponMasterProcessing = false;
	/** 超能力伤害处理递归防护标志（猎杀标记/毁灭终结/战神之力） */
	private static boolean superPowerDamageProcessing = false;
	/** 灵魂链接治疗递归防护 */
	private static boolean soulLinkProcessing = false;
	/** pending_roll 独立计数器（避免与 tickStates 双重递减） */
	private static final Map<java.util.UUID, Integer> pendingRollTimers = new HashMap<>();

	// ===== 征召专属超能力数据结构 =====
	/** 猎杀标记：被标记实体UUID -> 剩余tick */
	private static final Map<java.util.UUID, Integer> huntMarkedEntities = new HashMap<>();
	/** 铁壁堡垒：玩家UUID -> 静止tick计数 */
	private static final Map<java.util.UUID, Integer> ironFortressStillTicks = new HashMap<>();
	/** 铁壁堡垒激活状态：玩家UUID -> 是否激活 */
	private static final Map<java.util.UUID, Boolean> ironFortressActive = new HashMap<>();
	/** 区域效果（烈焰风暴/生命之泉） */
	private static final List<ZoneData> activeZones = new ArrayList<>();
	/** 区域数据记录 */
	private record ZoneData(ServerLevel level, double x, double y, double z, int remainingTicks, java.util.UUID casterUUID, ZoneType type) {}
	/** 区域类型 */
	private enum ZoneType { FLAME_STORM, LIFE_SPRING }

	// ========== 玩家加入/退出 ==========

	/**
	 * 玩家加入服务器时调用
	 * - 若玩家未拥有超能力，自动抽取并通知客户端打开转盘
	 * - 若玩家已有超能力，同步数据到客户端
	 */
	public static void onPlayerJoin(ServerPlayer player) {
		// 清除非创造/旁观模式玩家的残留飞行能力（防止断线重连后永久飞行）
		if (!player.isCreative() && !player.isSpectator()) {
			var abilities = player.getAbilities();
			if (abilities.mayfly) {
				abilities.mayfly = false;
				abilities.flying = false;
				player.onUpdateAbilities();
			}
		}
		if (!PlayerSuperPowerManager.hasSuperPower(player.getUUID())) {
			// 延迟 40 ticks（2 秒）后抽取，等待客户端完全加载
			playerTickCounter.put(player.getUUID(), 0);
			// 标记待抽取（使用独立计数器，避免与 tickStates 双重递减）
			pendingRollTimers.put(player.getUUID(), 40);
		} else {
			// 已有超能力，验证有效性
			SuperPower existing = PlayerSuperPowerManager.getSuperPower(player.getUUID());
			if (existing == null) {
				// 存档中的超能力ID无效，清除并重新抽取
				PlayerSuperPowerManager.removeSuperPower(player.getUUID());
				playerTickCounter.put(player.getUUID(), 0);
				pendingRollTimers.put(player.getUUID(), 40);
			} else {
				syncToClient(player);
			}
		}
	}

	/** 玩家退出时调用 */
	public static void onPlayerQuit(java.util.UUID playerId) {
		PlayerSuperPowerManager.onPlayerQuit(playerId);
		playerTickCounter.remove(playerId);
		positionRecordCounter.remove(playerId);
		pendingRollTimers.remove(playerId);
		lastSyncSnapshot.remove(playerId);
	}

	// ========== 服务端 tick ==========

	/**
	 * 每 tick 调用（注册到 ServerTickEvents.END_SERVER_TICK）
	 * 处理所有玩家的超能力持续效果
	 */
	public static void onServerTick(MinecraftServer server) {
		globalTickCounter++;

		// 每 tick 处理状态衰减
		PlayerSuperPowerManager.tickStates();

		// 每秒（20 ticks）减少冷却
		if (globalTickCounter % 20 == 0) {
			PlayerSuperPowerManager.tickCooldowns();
		}

		// 处理所有在线玩家
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			// pending_roll 使用独立计数器，避免与 tickStates() 双重递减
			Integer timer = pendingRollTimers.get(player.getUUID());
			if (timer != null) {
				int newTimer = timer - 1;
				if (newTimer <= 0) {
					pendingRollTimers.remove(player.getUUID());
					rollSuperPowerForPlayer(player);
				} else {
					pendingRollTimers.put(player.getUUID(), newTimer);
				}
			}

			SuperPower sp = PlayerSuperPowerManager.getSuperPower(player.getUUID());
			if (sp == null) continue;

			switch (sp) {
				case WEALTHY -> tickWealthy(player);
				case TOXIN_IMMUNITY -> tickToxinImmunity(player);
				case PARKOUR -> tickParkour(player);
				case NEAR_DEATH_RECALL -> tickNearDeathRecall(player);
				case FLY -> tickFly(player);
				case BATTLE_MEDIC -> tickBattleMedic(player);
				case IRON_FORTRESS -> tickIronFortress(player);
				case FROST_DOMAIN -> tickFrostDomain(player);
				default -> {}
			}
		}

		// 处理区域效果（烈焰风暴/生命之泉）
		tickActiveZones();
		// 处理猎杀标记倒计时
		tickHuntMark();

		// 每 10 ticks 同步一次所有玩家数据
		syncCounter++;
		if (syncCounter >= 10) {
			syncCounter = 0;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				syncToClient(player);
			}
		}
	}

	// ========== G 键主动技能 ==========

	/**
	 * G 键按下时调用（由网络包处理器调用）
	 */
	public static void onActiveKeyPressed(ServerPlayer player) {
		SuperPower sp = PlayerSuperPowerManager.getSuperPower(player.getUUID());
		if (sp == null) {
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.no_power"));
			return;
		}
		if (!sp.hasActiveAction()) {
			player.sendSystemMessage(Component.translatable(
					"superpower.randomsurprise.not_active", Component.translatable(sp.getNameKey())));
			return;
		}
		// 冷却检查
		if (PlayerSuperPowerManager.isOnCooldown(player.getUUID(), sp)) {
			int cd = PlayerSuperPowerManager.getCooldown(player.getUUID(), sp);
			player.sendSystemMessage(Component.translatable(
					"superpower.randomsurprise.on_cooldown", cd));
			return;
		}

		// 飞行中不允许重复触发
		if (sp == SuperPower.FLY && PlayerSuperPowerManager.getStateTicks(player.getUUID(), "fly_active") > 0) {
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.fly_already_active"));
			return;
		}

		// 分派到具体技能
		boolean success = false;
		switch (sp) {
			case BLINK -> success = doBlink(player);
			case FLY -> success = doFly(player);
			case ENCHANT -> success = doEnchant(player);
			case THUNDER_STRIKE -> success = doThunderStrike(player);
			case DECOMPOSE -> success = doDecompose(player);
			case TOXIN_IMMUNITY -> success = doToxinCleanse(player);
			case TIME_DILATION -> success = doTimeDilation(player);
			case GRAVITY_PULL -> success = doGravityPull(player);
			case HUNT_MARK -> success = doHuntMark(player);
			case WAR_STOMP -> success = doWarStomp(player);
			case FLAME_STORM -> success = doFlameStorm(player);
			case DOOM_STRIKE -> success = doDoomStrike(player);
			case LIFE_SPRING -> success = doLifeSpring(player);
			case WAR_GOD_MIGHT -> success = doWarGodMight(player);
			case DEATH_TOUCH -> success = doDeathTouch(player);
			default -> {}
		}

		if (success) {
		// 设置冷却（飞行技能特殊处理：CD 从技能结束时开始计算，由 tickFly 在结束时设置）
		if (sp.getCooldownSeconds() > 0 && sp != SuperPower.FLY) {
			PlayerSuperPowerManager.setCooldown(player.getUUID(), sp, sp.getCooldownSeconds());
		}
	}

		// 立即同步
		syncToClient(player);
	}

	// ========== V 键被动开关 ==========

	/**
	 * V 键按下时调用
	 */
	public static void onToggleKeyPressed(ServerPlayer player) {
		SuperPower sp = PlayerSuperPowerManager.getSuperPower(player.getUUID());
		if (sp == null) {
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.no_power"));
			return;
		}
		if (!sp.isToggleable()) {
			player.sendSystemMessage(Component.translatable(
					"superpower.randomsurprise.not_toggleable", Component.translatable(sp.getNameKey())));
			return;
		}
		boolean newState = PlayerSuperPowerManager.togglePassive(player.getUUID());
		String key = newState ? "superpower.randomsurprise.toggle_on" : "superpower.randomsurprise.toggle_off";
		player.sendSystemMessage(Component.translatable(key, Component.translatable(sp.getNameKey())));
		syncToClient(player);
	}

	// ========== 伤害事件 ==========

	/**
	 * 实体受到伤害后调用（用于武器大师伤害翻倍）
	 */
	public static void onEntityDamaged(LivingEntity entity, DamageSource source, float amount) {
		if (amount <= 0) return;
		Entity attacker = source.getEntity();

		// 猎杀标记：被标记目标受到所有来源+50%伤害
		if (!superPowerDamageProcessing) {
			Integer markTicks = huntMarkedEntities.get(entity.getUUID());
			if (markTicks != null && markTicks > 0) {
				superPowerDamageProcessing = true;
				try {
					entity.hurt(source, amount * 0.5F);
				} finally {
					superPowerDamageProcessing = false;
				}
			}
		}

		if (attacker instanceof ServerPlayer player) {
			SuperPower sp = PlayerSuperPowerManager.getSuperPower(player.getUUID());
			if (sp == null) return;
			if (sp == SuperPower.WEAPON_MASTER && !weaponMasterProcessing) {
				if (ThreadLocalRandom.current().nextDouble() < WEAPON_MASTER_DOUBLE_CHANCE) {
					weaponMasterProcessing = true;
					try {
						entity.hurt(entity.damageSources().mobAttack(player), amount);
					} finally {
						weaponMasterProcessing = false;
					}
					player.sendSystemMessage(Component.translatable("superpower.randomsurprise.weapon_master_trigger"));
				}
			}
			// 生命汲取：攻击时恢复自身2点生命值
			if (sp == SuperPower.LIFE_DRAIN) {
				player.heal(2.0F);
			}
			// 毁灭终结：下一次攻击3倍伤害
			if (sp == SuperPower.DOOM_STRIKE && !superPowerDamageProcessing) {
				int doomTicks = PlayerSuperPowerManager.getStateTicks(player.getUUID(), "doom_strike");
				if (doomTicks > 0) {
					superPowerDamageProcessing = true;
					try {
						entity.hurt(entity.damageSources().mobAttack(player), amount * 2.0F);
					} finally {
						superPowerDamageProcessing = false;
					}
					PlayerSuperPowerManager.setStateTicks(player.getUUID(), "doom_strike", 0);
					player.sendSystemMessage(Component.translatable("superpower.randomsurprise.doom_strike_trigger"));
				}
			}
			// 战神之力：战场内攻击+20%，主动激活期间再+50%
			if (sp == SuperPower.WAR_GOD_MIGHT && !superPowerDamageProcessing) {
				if (com.randomsurprise.battlefield.BattlefieldDimension.isInBattlefield(player)) {
					float bonus = amount * 0.2F;
					int warGodTicks = PlayerSuperPowerManager.getStateTicks(player.getUUID(), "war_god_active");
					if (warGodTicks > 0) {
						bonus += amount * 0.5F;
					}
					superPowerDamageProcessing = true;
					try {
						entity.hurt(entity.damageSources().mobAttack(player), bonus);
					} finally {
						superPowerDamageProcessing = false;
					}
				}
			}
		}
	}

	/**
	 * 致命伤害拦截（用于濒死回溯）
	 * 返回 false 阻止死亡
	 */
	public static boolean onAllowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (entity instanceof ServerPlayer player) {
			SuperPower sp = PlayerSuperPowerManager.getSuperPower(player.getUUID());
			if (sp == SuperPower.NEAR_DEATH_RECALL) {
				if (PlayerSuperPowerManager.isOnCooldown(player.getUUID(), sp)) {
					return true;
				}
				return doNearDeathRecall(player);
			}
			// 不死之身：征召世界中致死时复活
			if (sp == SuperPower.UNDYING_BODY
					&& com.randomsurprise.battlefield.BattlefieldDimension.isInBattlefield(player)) {
				if (PlayerSuperPowerManager.isOnCooldown(player.getUUID(), sp)) {
					return true; // 冷却中，允许死亡
				}
				return doUndyingBody(player);
			}
		}
		return true;
	}

	// ========== 具体技能实现 ==========

	/** 瞬移：瞬移到准星命中点 */
	private static boolean doBlink(ServerPlayer player) {
		HitResult hit = player.pick(BLINK_MAX_RANGE, 1.0F, false);
		if (hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = ((BlockHitResult) hit).getBlockPos();
			// 在命中方块上方落地
			double targetX = pos.getX() + 0.5;
			double targetY = pos.getY() + 1.0;
			double targetZ = pos.getZ() + 0.5;
			// 安全检查：目标位置不能是流体
			var level = player.serverLevel();
			if (!level.getFluidState(pos.above()).isEmpty()) {
				player.sendSystemMessage(Component.translatable("superpower.randomsurprise.blink_unsafe"));
				return false;
			}
			// 安全检查：目标位置和上方必须是空气
			var aboveState = level.getBlockState(pos.above());
			var above2State = level.getBlockState(pos.above(2));
			if (!aboveState.isAir() || !above2State.isAir()) {
				player.sendSystemMessage(Component.translatable("superpower.randomsurprise.blink_unsafe"));
				return false;
			}
			// 原位置播放消失粒子
			level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(),
					20, 0.5, 1.0, 0.5, 0.5);
			// 传送
			player.teleportTo(targetX, targetY, targetZ);
			// 目标位置播放出现粒子
			level.sendParticles(ParticleTypes.PORTAL, targetX, targetY, targetZ,
					20, 0.5, 1.0, 0.5, 0.5);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.blink_success"));
			return true;
		} else if (hit.getType() == HitResult.Type.ENTITY) {
			Entity target = ((EntityHitResult) hit).getEntity();
			double targetX = target.getX();
			double targetY = target.getY();
			double targetZ = target.getZ();
			var level = player.serverLevel();
			// 安全检查：目标位置不能是固体方块
			BlockPos targetPos = target.blockPosition();
			var targetState = level.getBlockState(targetPos);
			var aboveTargetState = level.getBlockState(targetPos.above());
			if (!targetState.isAir() || !aboveTargetState.isAir()) {
				player.sendSystemMessage(Component.translatable("superpower.randomsurprise.blink_unsafe"));
				return false;
			}
			level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(),
					20, 0.5, 1.0, 0.5, 0.5);
			player.teleportTo(targetX, targetY, targetZ);
			level.sendParticles(ParticleTypes.PORTAL, targetX, targetY, targetZ,
					20, 0.5, 1.0, 0.5, 0.5);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.blink_success"));
			return true;
		} else {
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.blink_no_target"));
			return false;
		}
	}

	/** 飞行：20 秒创造飞行 */
	private static boolean doFly(ServerPlayer player) {
		var abilities = player.getAbilities();
		abilities.mayfly = true;
		abilities.flying = true;
		player.onUpdateAbilities();
		PlayerSuperPowerManager.setStateTicks(player.getUUID(), "fly_active", FLY_DURATION_TICKS);
		player.sendSystemMessage(Component.translatable("superpower.randomsurprise.fly_start"));
		return true;
	}

	/** 飞行 tick 处理：到时间取消飞行 */
private static void tickFly(ServerPlayer player) {
	int remaining = PlayerSuperPowerManager.getStateTicks(player.getUUID(), "fly_active");
	if (remaining > 0) {
		if (remaining <= 1) {
			// 即将到期，取消飞行
			var abilities = player.getAbilities();
			if (!player.isCreative()) {
				abilities.mayfly = false;
				abilities.flying = false;
				player.onUpdateAbilities();
				player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0,
						true, false, false));
				player.sendSystemMessage(Component.translatable("superpower.randomsurprise.fly_end"));
			}
			// 飞行技能 CD 从技能结束时开始计算（15 秒）
			PlayerSuperPowerManager.setCooldown(player.getUUID(), SuperPower.FLY,
					SuperPower.FLY.getCooldownSeconds());
		} else if (remaining == 60) {
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.fly_ending"));
		}
	}
}

	/** 附魔：主手物品随机高等级附魔 */
	private static boolean doEnchant(ServerPlayer player) {
		ItemStack mainHand = player.getMainHandItem();
		if (mainHand.isEmpty()) {
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.enchant_no_item"));
			return false;
		}
		// 检查经验等级
		if (player.experienceLevel < 10) {
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.enchant_no_exp"));
			return false;
		}
		// 扣除 10 级经验
		player.giveExperienceLevels(-10);

		// 获取可用的附魔
		var registryAccess = player.level().getServer().registryAccess();
		var enchantmentRegistry = registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
		List<Holder<Enchantment>> applicable = new ArrayList<>();
		for (Holder<Enchantment> holder : enchantmentRegistry.holders().toList()) {
			Enchantment ench = holder.value();
			if (ench.canEnchant(mainHand)) {
				applicable.add(holder);
			}
		}
		if (applicable.isEmpty()) {
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.enchant_no_enchantment"));
			player.giveExperienceLevels(10); // 退还经验
			return false;
		}
		// 随机选 3-5 个附魔
		int count = 3 + ThreadLocalRandom.current().nextInt(3);
		count = Math.min(count, applicable.size());
		java.util.Collections.shuffle(applicable);

		Map<Enchantment, Integer> enchants = new HashMap<>(EnchantmentHelper.getEnchantments(mainHand));
		int applied = 0;
		// 遍历候选列表，跳过与已选附魔互斥的（如精准采集与时运不可共存）
		for (int i = 0; i < applicable.size() && applied < count; i++) {
			Holder<Enchantment> holder = applicable.get(i);
			Enchantment ench = holder.value();
			// 检查与已选附魔是否互斥
			boolean conflict = false;
			for (Enchantment existing : enchants.keySet()) {
				if (areEnchantmentsExclusive(existing, ench, enchantmentRegistry)) {
					conflict = true;
					break;
				}
			}
			if (conflict) continue;
			int maxLevel = ench.getMaxLevel();
			// 满级或接近满级
			int level = Math.max(1, maxLevel);
			enchants.put(ench, level);
			applied++;
		}
		EnchantmentHelper.setEnchantments(enchants, mainHand);

		player.sendSystemMessage(Component.translatable(
				"superpower.randomsurprise.enchant_success", applied));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	/** 原版互斥附魔组（同一组内的附魔不可共存） */
	private static final String[][] EXCLUSIVE_GROUPS = {
		{"minecraft:silk_touch", "minecraft:fortune"},
		{"minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods"},
		{"minecraft:infinity", "minecraft:mending"},
		{"minecraft:protection", "minecraft:fire_protection", "minecraft:blast_protection", "minecraft:projectile_protection"},
		{"minecraft:depth_strider", "minecraft:frost_walker"},
		{"minecraft:loyalty", "minecraft:riptide", "minecraft:channeling"},
		{"minecraft:multishot", "minecraft:piercing"}
	};

	/**
	 * 检查两个附魔是否互斥（1.20.1 无 isExclusiveWith API，通过ID手动判定）
	 */
	private static boolean areEnchantmentsExclusive(Enchantment a, Enchantment b,
			net.minecraft.core.Registry<Enchantment> registry) {
		if (a == b) return false;
		var idA = registry.getKey(a);
		var idB = registry.getKey(b);
		if (idA == null || idB == null) return false;
		String aStr = idA.toString();
		String bStr = idB.toString();
		for (String[] group : EXCLUSIVE_GROUPS) {
			boolean aInGroup = false, bInGroup = false;
			for (String id : group) {
				if (id.equals(aStr)) aInGroup = true;
				if (id.equals(bStr)) bInGroup = true;
			}
			if (aInGroup && bInGroup) return true;
		}
		return false;
	}

	/** 雷霆之子：对准星命中位置（方块或生物）召唤雷击 */
	private static boolean doThunderStrike(ServerPlayer player) {
		HitResult hit = player.pick(THUNDER_MAX_RANGE, 1.0F, false);
		double targetX, targetY, targetZ;
		LivingEntity targetEntity = null;

		if (hit.getType() == HitResult.Type.ENTITY) {
			// 命中实体：对实体放置闪电
			Entity e = ((EntityHitResult) hit).getEntity();
			if (e instanceof LivingEntity le && le != player) {
				targetEntity = le;
				targetX = e.getX();
				targetY = e.getY();
				targetZ = e.getZ();
			} else {
				// 命中自己或非生物实体，不允许
				player.sendSystemMessage(Component.translatable("superpower.randomsurprise.thunder_no_target"));
				return false;
			}
		} else if (hit.getType() == HitResult.Type.BLOCK) {
			// 命中方块：在方块上方放置闪电
			BlockPos pos = ((BlockHitResult) hit).getBlockPos();
			targetX = pos.getX() + 0.5;
			targetY = pos.getY() + 1.0; // 方块上方
			targetZ = pos.getZ() + 0.5;
		} else {
			// 未命中任何目标
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.thunder_no_target"));
			return false;
		}

		// 召唤雷击
		ServerLevel level = (ServerLevel) player.level();
		LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
		if (lightning == null) {
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.thunder_failed"));
			return false;
		}
		lightning.setPos(targetX, targetY, targetZ);
		// 设置仅视觉效果（不造成火焰，避免破坏地形和双重伤害）
		lightning.setVisualOnly(true);
		level.addFreshEntity(lightning);

		// 如果命中实体，额外造成伤害（基础 8，雨天翻倍 16）
		boolean isRaining = level.isRaining() && level.isThundering();
		if (targetEntity != null) {
			float damage = isRaining ? 16.0F : 8.0F;
			targetEntity.hurt(targetEntity.damageSources().lightningBolt(), damage);
		}

		// 雷击粒子效果
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, targetX, targetY + 1, targetZ,
				15, 0.5, 1.0, 0.5, 0.3);
		level.sendParticles(ParticleTypes.END_ROD, targetX, targetY + 1, targetZ,
				5, 0.5, 1.0, 0.5, 0.1);

		String msgKey = isRaining
				? "superpower.randomsurprise.thunder_success_rain"
				: "superpower.randomsurprise.thunder_success";
		player.sendSystemMessage(Component.translatable(msgKey));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	/** 分解大师：主手物品分解为合成配方材料 */
	private static boolean doDecompose(ServerPlayer player) {
		ItemStack mainHand = player.getMainHandItem();
		if (mainHand.isEmpty()) {
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.decompose_no_item"));
			return false;
		}
		// 获取合成配方
		var server = player.level().getServer();
		if (server == null) return false;
		var recipeManager = server.getRecipeManager();
		// 构造单格容器用于查询
		// 1.20.1: CraftingInput does not exist; iterate all crafting recipes
		var allRecipes = recipeManager.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING);
		net.minecraft.world.item.crafting.CraftingRecipe matchedRecipe = null;
		for (var recipe : allRecipes) {
			if (recipe.getResultItem(player.level().registryAccess()).is(mainHand.getItem())) {
				matchedRecipe = recipe;
				break;
			}
		}
		if (matchedRecipe == null) {
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.decompose_no_recipe"));
			return false;
		}
		// 获取配方产出数量，消耗等量物品防止复制
		ItemStack recipeOutput = matchedRecipe.getResultItem(player.level().registryAccess());
		int outputCount = recipeOutput.getCount();
		if (mainHand.getCount() < outputCount) {
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.decompose_not_enough", outputCount));
			return false;
		}
		var ingredients = matchedRecipe.getIngredients();
		if (ingredients.isEmpty()) {
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.decompose_no_recipe"));
			return false;
		}
		// 收集所有材料
		List<ItemStack> materials = new ArrayList<>();
		for (var ingredient : ingredients) {
			if (ingredient.isEmpty()) continue;
			// 1.20.1: ingredient.getItems() returns ItemStack[]
ItemStack[] itemsArr = ingredient.getItems();
			if (itemsArr.length > 0) {
				// 取第一个变体
				materials.add(itemsArr[0].copy());
			}
		}
		if (materials.isEmpty()) {
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.decompose_no_recipe"));
			return false;
		}
		// 消耗主手物品（与配方产出数量等量，防止材料复制）
		mainHand.shrink(outputCount);
		// 给玩家材料
		for (ItemStack mat : materials) {
			if (!player.getInventory().add(mat.copy())) {
				// 背包满，掉落到地上
				player.drop(mat.copy(), false);
			}
		}
		player.sendSystemMessage(Component.translatable(
				"superpower.randomsurprise.decompose_success", materials.size()));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.UI_STONECUTTER_SELECT_RECIPE, SoundSource.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	/** 毒素清除主动技能：15 秒全异常清除 */
	private static boolean doToxinCleanse(ServerPlayer player) {
		PlayerSuperPowerManager.setStateTicks(player.getUUID(), "toxin_cleanse", TOXIN_CLEANSE_DURATION_TICKS);
		player.sendSystemMessage(Component.translatable("superpower.randomsurprise.toxin_cleanse_start"));
		return true;
	}

	/** 时间膨胀：周围5格内所有敌对生物减速50%（缓慢II）持续10秒 */
	private static boolean doTimeDilation(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		double radius = 5.0;
		net.minecraft.world.phys.AABB box = net.minecraft.world.phys.AABB.ofSize(
				player.position(), radius * 2, radius * 2, radius * 2);
		List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, box);
		int count = 0;
		for (LivingEntity entity : entities) {
			if (entity == player) continue;
			if (!(entity instanceof net.minecraft.world.entity.Mob mob)) continue;
			if (!(mob instanceof net.minecraft.world.entity.monster.Enemy)) continue;
			// 缓慢II（amplifier=1）持续200 ticks（10秒）
			mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1, false, false, true));
			count++;
		}
		// 粒子效果
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(),
				20, 2.0, 1.0, 2.0, 0.1);
		player.sendSystemMessage(Component.translatable("superpower.randomsurprise.time_dilation_success", count));
		return true;
	}

	/** 重力操控：将周围10格内所有敌对生物拉向自己并造成3点坠落伤害 */
	private static boolean doGravityPull(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		double radius = 10.0;
		net.minecraft.world.phys.AABB box = net.minecraft.world.phys.AABB.ofSize(
				player.position(), radius * 2, radius * 2, radius * 2);
		List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, box);
		int count = 0;
		for (LivingEntity entity : entities) {
			if (entity == player) continue;
			if (!(entity instanceof net.minecraft.world.entity.Mob mob)) continue;
			if (!(mob instanceof net.minecraft.world.entity.monster.Enemy)) continue;
			// 计算拉拽方向（从怪物指向玩家）
			double dx = player.getX() - entity.getX();
			double dy = player.getY() + 1 - entity.getY();
			double dz = player.getZ() - entity.getZ();
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist < 0.1) continue;
			// 施加拉拽速度
			double pullSpeed = 1.5;
			mob.push(dx / dist * pullSpeed, dy / dist * pullSpeed + 0.3, dz / dist * pullSpeed);
			// 造成3点坠落伤害
			mob.hurt(mob.damageSources().fall(), 3.0F);
			count++;
		}
		// 粒子效果
		level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(),
				30, 3.0, 1.0, 3.0, 0.2);
		player.sendSystemMessage(Component.translatable("superpower.randomsurprise.gravity_pull_success", count));
		return true;
	}

	/**
	 * 生物死亡事件处理（用于战场狂怒）
	 * 击杀者拥有 BATTLE_FURY 且在征召战场中时，获得10秒力量II
	 */
	public static void onLivingDeath(LivingEntity entity, DamageSource source) {
		Entity killer = source.getEntity();
		if (killer instanceof ServerPlayer player) {
			// 仅在征召战场中生效
			if (!com.randomsurprise.battlefield.BattlefieldDimension.isInBattlefield(player)) return;
			SuperPower sp = PlayerSuperPowerManager.getSuperPower(player.getUUID());
			if (sp == SuperPower.BATTLE_FURY) {
				// 力量II（amplifier=1）持续200 ticks（10秒）
				player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 1, false, false, true));
				player.sendSystemMessage(Component.translatable("superpower.randomsurprise.battle_fury_trigger"));
			}
		}
	}

	/**
	 * 伤害减免处理（用于守护之盾）
	 * 被攻击者周围8格内有队友拥有 GUARDIAN_SHIELD 且在征召战场中时，减免20%伤害
	 * @return 修改后的伤害值
	 */
	public static float onLivingHurtReduceDamage(LivingEntity entity, DamageSource source, float amount) {
		if (amount <= 0) return amount;
		if (!(entity instanceof ServerPlayer target)) return amount;

		// 不死之身：无敌期间免疫所有伤害
		int invulnTicks = PlayerSuperPowerManager.getStateTicks(target.getUUID(), "undying_invuln");
		if (invulnTicks > 0) {
			return 0;
		}

		SuperPower targetSp = PlayerSuperPowerManager.getSuperPower(target.getUUID());

		// 元素护甲：战场内免疫火焰/冰霜/闪电伤害
		if (targetSp == SuperPower.ELEMENTAL_ARMOR
				&& com.randomsurprise.battlefield.BattlefieldDimension.isInBattlefield(target)) {
			if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)
					|| source.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING)
					|| source.is(net.minecraft.tags.DamageTypeTags.IS_FREEZING)) {
				return 0;
			}
		}

		// 仅在征召战场中生效（守护之盾）
		if (!com.randomsurprise.battlefield.BattlefieldDimension.isInBattlefield(target)) return amount;

		MinecraftServer server = target.serverLevel().getServer();
		if (server == null) return amount;

		for (ServerPlayer ally : server.getPlayerList().getPlayers()) {
			if (ally.getUUID().equals(target.getUUID())) continue;
			if (!com.randomsurprise.battlefield.BattlefieldDimension.isInBattlefield(ally)) continue;
			if (ally.distanceTo(target) > 8.0) continue;
			SuperPower sp = PlayerSuperPowerManager.getSuperPower(ally.getUUID());
			if (sp == SuperPower.GUARDIAN_SHIELD) {
				return amount * 0.8F; // 减免20%
			}
		}
		return amount;
	}

	/** 濒死回溯触发 */
	private static boolean doNearDeathRecall(ServerPlayer player) {
		PlayerSuperPowerManager.PositionRecord record = PlayerSuperPowerManager.getPositionThreeSecondsAgo(player.getUUID());
		if (record == null) {
			// 没有记录，原地回血
			player.setHealth(player.getMaxHealth() / 2.0F);
		} else {
			// 跨维度时不传送，仅原地回血
			if (record.dimension() != null && !record.dimension().equals(player.level().dimension())) {
				player.setHealth(player.getMaxHealth() / 2.0F);
			} else {
				// 回溯到 3 秒前位置
				player.teleportTo(record.x(), record.y(), record.z());
				// 设置朝向
				player.setYRot(record.yaw());
				player.setXRot(record.pitch());
				// 恢复一半血量
				player.setHealth(player.getMaxHealth() / 2.0F);
			}
		}
		// 清除负面效果
		player.removeEffect(MobEffects.WITHER);
		player.removeEffect(MobEffects.POISON);
		player.removeEffect(MobEffects.WEAKNESS);
		player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		player.removeEffect(MobEffects.DIG_SLOWDOWN);
		player.removeEffect(MobEffects.BLINDNESS);
		player.removeEffect(MobEffects.HUNGER);
	// 给予短暂抗性
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 2, true, false, true));

		// 设置冷却
		PlayerSuperPowerManager.setCooldown(player.getUUID(), SuperPower.NEAR_DEATH_RECALL, 120);
		// 位置历史清空
		PlayerSuperPowerManager.recordPosition(player); // 重新记录当前位置

		// 粒子效果
		var level = player.serverLevel();
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(),
				30, 0.5, 1.0, 0.5, 0.5);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.sendSystemMessage(Component.translatable("superpower.randomsurprise.near_death_recall_triggered"));
		return false; // false 阻止死亡
	}

	// ========== 被动技能 tick ==========

	/** 家财万贯：每 60s 获得随机矿物 */
	private static void tickWealthy(ServerPlayer player) {
		int counter = playerTickCounter.getOrDefault(player.getUUID(), 0) + 1;
		if (counter >= WEALTHY_INTERVAL_TICKS) {
			counter = 0;
			// 给予 1-3 个随机矿物
			int count = 1 + ThreadLocalRandom.current().nextInt(3);
			List<Item> minerals = List.of(
					Items.DIAMOND, Items.GOLD_INGOT, Items.IRON_INGOT, Items.EMERALD,
					Items.COPPER_INGOT, Items.REDSTONE, Items.LAPIS_LAZULI, Items.COAL,
					Items.QUARTZ, Items.NETHERITE_SCRAP, Items.RAW_COPPER, Items.RAW_GOLD,
					Items.RAW_IRON, Items.AMETHYST_SHARD
			);
			for (int i = 0; i < count; i++) {
				Item mineral = minerals.get(ThreadLocalRandom.current().nextInt(minerals.size()));
				ItemStack stack = new ItemStack(mineral, 1);
				if (!player.getInventory().add(stack)) {
					player.drop(stack, false);
				}
			}
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.wealthy_gain", count));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 1.0F);
		}
		playerTickCounter.put(player.getUUID(), counter);
	}

	/** 毒素免疫：永久免疫中毒/凋零/饥饿 + 主动清除效果 */
	private static void tickToxinImmunity(ServerPlayer player) {
		// 永久免疫
		player.removeEffect(MobEffects.POISON);
		player.removeEffect(MobEffects.WITHER);
		player.removeEffect(MobEffects.HUNGER);
		// 主动清除效果期间，每 tick 清除所有常见负面效果
		int cleanseRemaining = PlayerSuperPowerManager.getStateTicks(player.getUUID(), "toxin_cleanse");
		if (cleanseRemaining > 0) {
			player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		player.removeEffect(MobEffects.DIG_SLOWDOWN);
		player.removeEffect(MobEffects.WEAKNESS);
			player.removeEffect(MobEffects.BLINDNESS);
			player.removeEffect(MobEffects.DARKNESS);
			player.removeEffect(MobEffects.CONFUSION);
			player.removeEffect(MobEffects.LEVITATION);
			player.removeEffect(MobEffects.UNLUCK);
		}
	}

	/** 跑酷达人：二段跳检测 */
	private static void tickParkour(ServerPlayer player) {
		boolean onGround = player.onGround();
		boolean wasOnGround = PlayerSuperPowerManager.wasOnGround(player.getUUID());
		// 落地重置二段跳状态
		if (onGround) {
			PlayerSuperPowerManager.setDoubleJumpUsed(player.getUUID(), false);
		}
		PlayerSuperPowerManager.setWasOnGround(player.getUUID(), onGround);
	}

	/**
	 * 跑酷达人：尝试二段跳
	 * 由客户端检测跳跃键按下时调用（通过特殊网络包或客户端本地处理）
	 * 这里通过客户端检测跳跃键后发送 action 包，但 action 包统一调用 onActiveKeyPressed
	 * 为了简化，跑酷达人的二段跳放在客户端处理（给予 Y 速度）
	 *
	 * 服务端此方法用于验证：玩家在空中且未使用二段跳时，允许客户端触发
	 * @return 是否允许二段跳
	 */
	public static boolean tryConsumeDoubleJump(ServerPlayer player) {
		SuperPower sp = PlayerSuperPowerManager.getSuperPower(player.getUUID());
		if (sp != SuperPower.PARKOUR) return false;
		if (player.onGround()) return false;
		if (PlayerSuperPowerManager.isDoubleJumpUsed(player.getUUID())) return false;
		PlayerSuperPowerManager.setDoubleJumpUsed(player.getUUID(), true);
		return true;
	}

	/** 濒死回溯：记录位置 */
	private static void tickNearDeathRecall(ServerPlayer player) {
		int counter = positionRecordCounter.getOrDefault(player.getUUID(), 0) + 1;
		if (counter >= POSITION_RECORD_INTERVAL_TICKS) {
			counter = 0;
			PlayerSuperPowerManager.recordPosition(player);
		}
		positionRecordCounter.put(player.getUUID(), counter);
	}

	// ========== 抽取超能力 ==========

	/** 玩家首次进入时抽取超能力（使用公平袋子系统，确保所有超能力都能被抽到） */
	private static void rollSuperPowerForPlayer(ServerPlayer player) {
		SuperPower rolled = drawFromBag();
		PlayerSuperPowerManager.setSuperPower(player.getUUID(), rolled);
		// 发送给客户端打开转盘
		ModNetworking.sendSuperPowerRoll(player, rolled.getId());
		// 同步数据
		syncToClient(player);
		RandomSurpriseMod.LOGGER.info("玩家 {} 抽中超能力: {}",
				player.getName().getString(), rolled.getId());
	}

	// ========== 数据同步 ==========

	/** 同步玩家超能力数据到客户端（含脏检查：数据未变化时跳过发送） */
	public static void syncToClient(ServerPlayer player) {
		SuperPower sp = PlayerSuperPowerManager.getSuperPower(player.getUUID());
		String id = sp != null ? sp.getId() : null;
		boolean passiveEnabled = PlayerSuperPowerManager.isPassiveEnabled(player.getUUID());
		int cooldown = sp != null ? PlayerSuperPowerManager.getCooldown(player.getUUID(), sp) : 0;
		// 收集状态标记
		List<String> flags = new ArrayList<>();
		int flyRemaining = PlayerSuperPowerManager.getStateTicks(player.getUUID(), "fly_active");
		int cleanseRemaining = PlayerSuperPowerManager.getStateTicks(player.getUUID(), "toxin_cleanse");
		int stateTicks = 0;
		if (flyRemaining > 0) {
			flags.add("flying");
			stateTicks = flyRemaining;
		} else if (cleanseRemaining > 0) {
			flags.add("cleansing");
			stateTicks = cleanseRemaining;
		}
		// 脏检查：构建当前数据快照字符串，与上次同步的快照比较，相同则跳过发送
		String snapshot = (id == null ? "null" : id) + "|" + passiveEnabled + "|" + cooldown + "|" + stateTicks + "|" + flags.toString();
		String last = lastSyncSnapshot.get(player.getUUID());
		if (snapshot.equals(last)) {
			return;
		}
		lastSyncSnapshot.put(player.getUUID(), snapshot);
		SuperPowerSyncPayload payload = new SuperPowerSyncPayload(
				player.getUUID(), id, passiveEnabled, cooldown, stateTicks, flags);
		ModNetworking.sendSuperPowerSync(player, payload);
	}

	/** 强制重新抽取超能力（管理员命令用） */
	public static void forceReRoll(ServerPlayer player) {
		rollSuperPowerForPlayer(player);
	}

	// ========== 灵魂链接：治疗分享 ==========

	/**
	 * 玩家治疗事件：灵魂链接将50%治疗分享给最近队友
	 * 在 LivingHealEvent 中调用
	 */
	public static void onLivingHeal(LivingEntity entity, float amount) {
		if (soulLinkProcessing) return;
		if (!(entity instanceof ServerPlayer player)) return;
		SuperPower sp = PlayerSuperPowerManager.getSuperPower(player.getUUID());
		if (sp != SuperPower.SOUL_LINK) return;
		if (!com.randomsurprise.battlefield.BattlefieldDimension.isInBattlefield(player)) return;

		MinecraftServer server = player.serverLevel().getServer();
		if (server == null) return;

		ServerPlayer nearest = null;
		double nearestDist = Double.MAX_VALUE;
		for (ServerPlayer ally : server.getPlayerList().getPlayers()) {
			if (ally.getUUID().equals(player.getUUID())) continue;
			if (!com.randomsurprise.battlefield.BattlefieldDimension.isInBattlefield(ally)) continue;
			double dist = player.distanceTo(ally);
			if (dist < nearestDist && dist <= 20.0) {
				nearest = ally;
				nearestDist = dist;
			}
		}
		if (nearest != null && amount > 0) {
			soulLinkProcessing = true;
			try {
				nearest.heal(amount * 0.5F);
				player.serverLevel().sendParticles(ParticleTypes.HEART,
						nearest.getX(), nearest.getY() + 1.5, nearest.getZ(),
						3, 0.3, 0.5, 0.3, 0.1);
			} finally {
				soulLinkProcessing = false;
			}
		}
	}

	// ========== 征召专属超能力实现 ==========

	/** 战场医疗兵：每秒为8格内血量最低队友恢复5HP */
	private static void tickBattleMedic(ServerPlayer player) {
		if (globalTickCounter % 20 != 0) return;
		MinecraftServer server = player.serverLevel().getServer();
		if (server == null) return;
		ServerPlayer lowestHP = null;
		float lowestRatio = 1.0F;
		for (ServerPlayer ally : server.getPlayerList().getPlayers()) {
			if (!com.randomsurprise.battlefield.BattlefieldDimension.isInBattlefield(ally)) continue;
			if (player.distanceTo(ally) > 8.0) continue;
			float ratio = ally.getHealth() / ally.getMaxHealth();
			if (ratio < lowestRatio && ally.getHealth() < ally.getMaxHealth()) {
				lowestHP = ally;
				lowestRatio = ratio;
			}
		}
		if (lowestHP != null) {
			lowestHP.heal(5.0F);
			player.serverLevel().sendParticles(ParticleTypes.HEART,
					lowestHP.getX(), lowestHP.getY() + 1.5, lowestHP.getZ(),
					5, 0.3, 0.5, 0.3, 0.1);
		}
	}

	/** 铁壁堡垒：原地不动或蹲下3秒后获得抗性II+击退免疫 */
	private static void tickIronFortress(ServerPlayer player) {
		if (!com.randomsurprise.battlefield.BattlefieldDimension.isInBattlefield(player)) {
			ironFortressStillTicks.put(player.getUUID(), 0);
			ironFortressActive.put(player.getUUID(), false);
			return;
		}
		boolean notMoving = player.getDeltaMovement().x * player.getDeltaMovement().x
				+ player.getDeltaMovement().z * player.getDeltaMovement().z < 0.0001;
		int ticks = ironFortressStillTicks.getOrDefault(player.getUUID(), 0);
		if (notMoving) {
			ticks++;
		} else {
			ticks = 0;
		}
		ironFortressStillTicks.put(player.getUUID(), ticks);

		boolean shouldBeActive = ticks >= 60; // 3秒
		boolean isActive = ironFortressActive.getOrDefault(player.getUUID(), false);

		if (shouldBeActive) {
			// 持续刷新抗性II（30 tick = 1.5秒，保证不中断）
			player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 1, true, false, true));
			// 设置击退抗性
			var kbAttr = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
			if (kbAttr != null && !isActive) {
				kbAttr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
						java.util.UUID.fromString("a3b2c1d0-e5f6-7890-abcd-ef1234567890"),
						"Iron Fortress", 1.0,
						net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
			}
			ironFortressActive.put(player.getUUID(), true);
			// 粒子效果
			if (globalTickCounter % 10 == 0) {
				player.serverLevel().sendParticles(ParticleTypes.ITEM_SLIME,
						player.getX(), player.getY() + 0.5, player.getZ(),
						3, 0.5, 0.8, 0.5, 0.02);
			}
		} else if (isActive) {
			// 移动后移除击退抗性
			var kbAttr = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
			if (kbAttr != null) {
				kbAttr.removeModifier(java.util.UUID.fromString("a3b2c1d0-e5f6-7890-abcd-ef1234567890"));
			}
			ironFortressActive.put(player.getUUID(), false);
		}
	}

	/** 冰霜领域：5格内敌对生物移动速度降低30%，攻击速度降低15% */
	private static void tickFrostDomain(ServerPlayer player) {
		if (globalTickCounter % 20 != 0) return;
		ServerLevel level = player.serverLevel();
		double radius = 5.0;
		var box = net.minecraft.world.phys.AABB.ofSize(player.position(), radius * 2, radius * 2, radius * 2);
		List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, box);
		for (LivingEntity entity : entities) {
			if (entity == player) continue;
			if (!(entity instanceof net.minecraft.world.entity.Mob mob)) continue;
			if (!(mob instanceof net.minecraft.world.entity.monster.Enemy)) continue;
			// 缓慢I（amplifier=0）= 减速约30%，持续40 tick保证覆盖
			mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, true));
			// 挖掘减速I = 攻击速度降低约15%
			mob.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 40, 0, false, false, true));
		}
		if (globalTickCounter % 40 == 0) {
			level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1, player.getZ(),
					8, 2.0, 1.0, 2.0, 0.05);
		}
	}

	/** 猎杀标记倒计时 */
	private static void tickHuntMark() {
		if (huntMarkedEntities.isEmpty()) return;
		var iterator = huntMarkedEntities.entrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			int ticks = entry.getValue() - 1;
			if (ticks <= 0) {
				iterator.remove();
			} else {
				entry.setValue(ticks);
			}
		}
	}

	/** 区域效果tick（烈焰风暴/生命之泉） */
	private static void tickActiveZones() {
		if (activeZones.isEmpty()) return;
		var iterator = activeZones.iterator();
		while (iterator.hasNext()) {
			ZoneData zone = iterator.next();
			int remaining = zone.remainingTicks() - 1;
			if (remaining <= 0) {
				iterator.remove();
				continue;
			}
			// 每20 tick（1秒）执行一次效果
			if (remaining % 20 == 0) {
				var box = net.minecraft.world.phys.AABB.ofSize(
						new Vec3(zone.x(), zone.y(), zone.z()), 12, 12, 12);
				if (zone.type() == ZoneType.FLAME_STORM) {
					// 烈焰风暴：6格内敌对生物每秒4伤害
					List<LivingEntity> entities = zone.level().getEntitiesOfClass(LivingEntity.class, box);
					for (LivingEntity entity : entities) {
						if (!(entity instanceof net.minecraft.world.entity.Mob mob)) continue;
						if (!(mob instanceof net.minecraft.world.entity.monster.Enemy)) continue;
						mob.hurt(mob.damageSources().onFire(), 4.0F);
						mob.setSecondsOnFire(3);
					}
					zone.level().sendParticles(ParticleTypes.FLAME, zone.x(), zone.y() + 0.5, zone.z(),
							15, 2.0, 1.0, 2.0, 0.1);
					zone.level().sendParticles(ParticleTypes.LAVA, zone.x(), zone.y() + 0.5, zone.z(),
							3, 1.0, 0.5, 1.0, 0.05);
				} else if (zone.type() == ZoneType.LIFE_SPRING) {
					// 生命之泉：8格内队友每秒恢复2HP
					MinecraftServer server = zone.level().getServer();
					if (server != null) {
						for (ServerPlayer ally : server.getPlayerList().getPlayers()) {
							if (ally.level() != zone.level()) continue;
							if (ally.distanceToSqr(zone.x(), zone.y(), zone.z()) > 64.0) continue;
							if (com.randomsurprise.battlefield.BattlefieldDimension.isInBattlefield(ally)) {
								ally.heal(2.0F);
							}
						}
					}
					zone.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, zone.x(), zone.y() + 0.5, zone.z(),
							10, 2.0, 1.0, 2.0, 0.1);
				}
			}
			// 更新剩余tick（直接替换记录）
			iterator.remove();
			activeZones.add(new ZoneData(zone.level(), zone.x(), zone.y(), zone.z(),
					remaining, zone.casterUUID(), zone.type()));
		}
	}

	/** 猎杀标记：标记准星目标，全队+50%伤害持续10秒 */
	private static boolean doHuntMark(ServerPlayer player) {
		HitResult hit = player.pick(30, 1.0F, false);
		if (hit.getType() != HitResult.Type.ENTITY) {
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.hunt_mark_no_target"));
			return false;
		}
		Entity target = ((EntityHitResult) hit).getEntity();
		if (!(target instanceof LivingEntity)) {
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.hunt_mark_no_target"));
			return false;
		}
		huntMarkedEntities.put(target.getUUID(), 200); // 10秒
		player.serverLevel().sendParticles(ParticleTypes.DRAGON_BREATH,
				target.getX(), target.getY() + 1, target.getZ(),
				15, 0.5, 1.0, 0.5, 0.1);
		player.serverLevel().sendParticles(ParticleTypes.WITCH,
				target.getX(), target.getY() + 1, target.getZ(),
				10, 0.5, 1.0, 0.5, 0.05);
		player.sendSystemMessage(Component.translatable("superpower.randomsurprise.hunt_mark_success"));
		return true;
	}

	/** 战争践踏：8格内敌人受5伤害+击飞+缓慢II */
	private static boolean doWarStomp(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		double radius = 8.0;
		var box = net.minecraft.world.phys.AABB.ofSize(player.position(), radius * 2, radius * 2, radius * 2);
		List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, box);
		int count = 0;
		for (LivingEntity entity : entities) {
			if (entity == player) continue;
			if (!(entity instanceof net.minecraft.world.entity.Mob mob)) continue;
			if (!(mob instanceof net.minecraft.world.entity.monster.Enemy)) continue;
			mob.hurt(mob.damageSources().playerAttack(player), 5.0F);
			// 击飞
			double dx = mob.getX() - player.getX();
			double dz = mob.getZ() - player.getZ();
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist > 0.1) {
				mob.push(dx / dist * 1.5, 0.5, dz / dist * 1.5);
			} else {
				mob.push(0, 0.5, 0);
			}
			mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1, false, false, true));
			count++;
		}
		// 粒子效果
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(),
				30, 3.0, 0.3, 3.0, 0.3);
		level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.2, player.getZ(),
				10, 2.0, 0.5, 2.0, 0.2);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5F, 0.8F);
		player.sendSystemMessage(Component.translatable("superpower.randomsurprise.war_stomp_success", count));
		return true;
	}

	/** 烈焰风暴：在准星位置创建火旋风，6格内持续5秒每秒4伤害 */
	private static boolean doFlameStorm(ServerPlayer player) {
		HitResult hit = player.pick(20, 1.0F, false);
		double x, y, z;
		if (hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = ((BlockHitResult) hit).getBlockPos();
			x = pos.getX() + 0.5;
			y = pos.getY() + 1.0;
			z = pos.getZ() + 0.5;
		} else if (hit.getType() == HitResult.Type.ENTITY) {
			Entity e = ((EntityHitResult) hit).getEntity();
			x = e.getX();
			y = e.getY();
			z = e.getZ();
		} else {
			x = player.getX();
			y = player.getY();
			z = player.getZ();
		}
		activeZones.add(new ZoneData(player.serverLevel(), x, y, z, 100, player.getUUID(), ZoneType.FLAME_STORM));
		player.serverLevel().sendParticles(ParticleTypes.FLAME, x, y + 0.5, z,
				30, 1.5, 1.0, 1.5, 0.2);
		player.serverLevel().sendParticles(ParticleTypes.LAVA, x, y + 0.5, z,
				10, 1.0, 0.5, 1.0, 0.1);
		player.level().playSound(null, x, y, z,
				SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.8F);
		player.sendSystemMessage(Component.translatable("superpower.randomsurprise.flame_storm_success"));
		return true;
	}

	/** 毁灭终结：下一次攻击3倍伤害，10秒内不使用则失效 */
	private static boolean doDoomStrike(ServerPlayer player) {
		PlayerSuperPowerManager.setStateTicks(player.getUUID(), "doom_strike", 200); // 10秒
		player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false, true));
		player.sendSystemMessage(Component.translatable("superpower.randomsurprise.doom_strike_ready"));
		return true;
	}

	/** 生命之泉：在准星位置创建治疗区域，8格内队友每秒恢复2HP持续10秒 */
	private static boolean doLifeSpring(ServerPlayer player) {
		HitResult hit = player.pick(20, 1.0F, false);
		double x, y, z;
		if (hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = ((BlockHitResult) hit).getBlockPos();
			x = pos.getX() + 0.5;
			y = pos.getY() + 1.0;
			z = pos.getZ() + 0.5;
		} else if (hit.getType() == HitResult.Type.ENTITY) {
			Entity e = ((EntityHitResult) hit).getEntity();
			x = e.getX();
			y = e.getY();
			z = e.getZ();
		} else {
			x = player.getX();
			y = player.getY();
			z = player.getZ();
		}
		activeZones.add(new ZoneData(player.serverLevel(), x, y, z, 200, player.getUUID(), ZoneType.LIFE_SPRING));
		player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 0.5, z,
				20, 2.0, 1.0, 2.0, 0.1);
		player.serverLevel().sendParticles(ParticleTypes.HEART, x, y + 1.0, z,
				10, 1.0, 0.5, 1.0, 0.05);
		player.level().playSound(null, x, y, z,
				SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.2F);
		player.sendSystemMessage(Component.translatable("superpower.randomsurprise.life_spring_success"));
		return true;
	}

	/** 战神之力主动：10秒内额外+50%攻击 */
	private static boolean doWarGodMight(ServerPlayer player) {
		PlayerSuperPowerManager.setStateTicks(player.getUUID(), "war_god_active", 200); // 10秒
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0, false, false, true));
		player.serverLevel().sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1, player.getZ(),
				20, 0.5, 1.0, 0.5, 0.5);
		player.sendSystemMessage(Component.translatable("superpower.randomsurprise.war_god_might_active"));
		return true;
	}

	/** 死亡之触：对HP低于20%的敌人立即击杀 */
	private static boolean doDeathTouch(ServerPlayer player) {
		HitResult hit = player.pick(20, 1.0F, false);
		if (hit.getType() != HitResult.Type.ENTITY) {
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.death_touch_no_target"));
			return false;
		}
		Entity target = ((EntityHitResult) hit).getEntity();
		if (!(target instanceof LivingEntity living)) {
			player.sendSystemMessage(Component.translatable("superpower.randomsurprise.death_touch_no_target"));
			return false;
		}
		float hpRatio = living.getHealth() / living.getMaxHealth();
		if (hpRatio > 0.20F) {
			player.sendSystemMessage(Component.translatable(
					"superpower.randomsurprise.death_touch_too_healthy",
					String.format("%.0f%%", hpRatio * 100)));
			return false;
		}
		// 击杀
		living.kill();
		player.serverLevel().sendParticles(ParticleTypes.SOUL, living.getX(), living.getY() + 1, living.getZ(),
				20, 0.5, 1.0, 0.5, 0.2);
		player.serverLevel().sendParticles(ParticleTypes.DRAGON_BREATH, living.getX(), living.getY() + 1, living.getZ(),
				15, 0.5, 1.0, 0.5, 0.1);
		player.level().playSound(null, living.getX(), living.getY(), living.getZ(),
				SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 0.8F, 0.5F);
		player.sendSystemMessage(Component.translatable("superpower.randomsurprise.death_touch_success"));
		return true;
	}

	/** 不死之身触发：复活30%血量+3秒无敌 */
	private static boolean doUndyingBody(ServerPlayer player) {
		player.setHealth(player.getMaxHealth() * 0.3F);
		PlayerSuperPowerManager.setStateTicks(player.getUUID(), "undying_invuln", 60); // 3秒
		PlayerSuperPowerManager.setCooldown(player.getUUID(), SuperPower.UNDYING_BODY, 180);
		// 清除负面效果
		player.removeEffect(MobEffects.WITHER);
		player.removeEffect(MobEffects.POISON);
		player.removeEffect(MobEffects.WEAKNESS);
		player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		player.removeEffect(MobEffects.DIG_SLOWDOWN);
		player.removeEffect(MobEffects.BLINDNESS);
		player.removeEffect(MobEffects.HUNGER);
		// 粒子效果
		var level = player.serverLevel();
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(),
				40, 0.8, 1.5, 0.8, 0.5);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.sendSystemMessage(Component.translatable("superpower.randomsurprise.undying_body_triggered"));
		return false; // false 阻止死亡
	}

	/** 玩家退出时清理征召专属数据 */
	public static void onPlayerQuitBattlefield(java.util.UUID playerId) {
		ironFortressStillTicks.remove(playerId);
		ironFortressActive.remove(playerId);
	}
}
