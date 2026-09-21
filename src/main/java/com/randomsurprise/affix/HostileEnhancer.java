package com.randomsurprise.affix;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.randomsurprise.RandomSurpriseMod;
import com.randomsurprise.battlefield.BattlefieldDimension;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 敌对生物增强器
 *
 * <p>v13: 新增20个坏词条机制型效果
 * <ul>
 *   <li>死亡事件：死亡爆炸、死亡分裂、尸体连锁、1UP复活</li>
 *   <li>受击事件：濒死狂暴、末影闪现、击退炮、燃烧光环、凋零之触、锈蚀攻击、护盾充能、黑暗光环、仇恨连锁、蛛网陷阱</li>
 *   <li>服务器tick：狂暴计时、再生怪物、隐身、雷暴召唤、火球射手、治疗友军</li>
 * </ul>
 * 所有坏词条机制仅在征召世界中生效。
 */
public class HostileEnhancer {

	// ========== v13: 坏词条机制型状态追踪 ==========
	/** 1UP复活标记（防止重复复活） */
	private static final Set<UUID> REVIVED_MOBS = new HashSet<>();
	/** 狂暴标记 */
	private static final Set<UUID> ENRAGED_MOBS = new HashSet<>();
	/** 狂暴战斗计时：怪物UUID -> 战斗计时秒数 */
	private static final Map<UUID, Integer> ENRAGE_BATTLE_TIMERS = new HashMap<>();
	/** 护盾充能计时：怪物UUID -> 剩余cd秒 */
	private static final Map<UUID, Integer> SHIELD_CHARGE_TIMERS = new HashMap<>();
	/** 隐身计时：怪物UUID -> 剩余cd秒 */
	private static final Map<UUID, Integer> CLOAK_TIMERS = new HashMap<>();
	/** 雷暴计时：怪物UUID -> 剩余cd秒 */
	private static final Map<UUID, Integer> STORM_TIMERS = new HashMap<>();
	/** 火球计时：怪物UUID -> 剩余cd秒 */
	private static final Map<UUID, Integer> FIREBALL_TIMERS = new HashMap<>();
	/** 治疗友军计时：怪物UUID -> 剩余cd秒 */
	private static final Map<UUID, Integer> HEAL_ALLY_TIMERS = new HashMap<>();
	/** Boss闪电计时：怪物UUID -> 剩余cd秒 */
	private static final Map<UUID, Integer> BOSS_LIGHTNING_TIMERS = new HashMap<>();
	/** 死亡处理标记（防止递归） */
	private static final Set<UUID> DEATH_PROCESSED = new HashSet<>();
	/** 递归保护标志 */
	private static final ThreadLocal<Boolean> IN_DEATH_PROCESS = ThreadLocal.withInitial(() -> false);

	private static final Random RNG = new Random();

	/** 检查实体是否在征召世界 */
	private static boolean isInBattlefield(Entity entity) {
		return entity.level().dimension().equals(BattlefieldDimension.BATTLEFIELD_DIM_KEY);
	}

	/**
	 * 增强敌对生物（在实体生成时调用）
	 */
	public static void enhanceHostile(Mob mob) {
		// 跳过战场Boss（已通过 enhanceBoss() 增强过，避免双重增强导致属性叠加过高）
		if (com.randomsurprise.battlefield.BattlefieldManager.isBattlefieldBoss(mob.getUUID())) {
			return;
		}
		// 只增强敌对生物
		if (!(mob instanceof Enemy) && !isHostileMob(mob)) return;
		// 平静态：坏词条全部禁用
		if (com.randomsurprise.battlefield.BattlefieldManager.isBadAffixDisabled()) return;

		PlayerAffixManager.HostileBoost boost = PlayerAffixManager.calculateGlobalHostileBoost();
		if (!boost.hasAnyBoost()) return;

		// 血量增强：用属性修改器（如果可用），否则用药水效果
		try {
			var healthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
			if (healthAttr != null && boost.healthMult() > 1.0) {
				double bonus = boost.healthMult() - 1.0;
				healthAttr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
					"hostile_health_boost",
					bonus,
					net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
				mob.setHealth(mob.getMaxHealth());
			}
		} catch (Throwable ignored) {
			// API 不兼容时用Resistance模拟
			if (boost.healthMult() > 1.1) {
				mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,
						Integer.MAX_VALUE, (int) ((boost.healthMult() - 1) * 10),
						false, false, false));
			}
		}

		// 攻击增强：用Strength药水
		if (boost.damageMult() > 1.05) {
			int strLvl = Math.max(0, (int) Math.round((boost.damageMult() - 1) * 10));
			strLvl = Math.max(strLvl, boost.effectAmplifier());
			if (strLvl > 0) {
				mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,
						Integer.MAX_VALUE, strLvl, false, false, false));
			}
		}

		// 速度增强：用Speed药水（已封顶1.3x）
		if (boost.speedMult() > 1.05) {
			int spdLvl = (int) Math.round((boost.speedMult() - 1) * 10);
			if (spdLvl > 0) {
				mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
						Integer.MAX_VALUE, spdLvl, false, false, false));
			}
		}

		// v5: 抗性/护甲相关效果已替换为伤害免疫率（在 LivingHurtEvent 中处理）
		// 原 effectAmplifier>=2 / armorLevel / armorPercent 的 DAMAGE_RESISTANCE 药水已移除

		// v2 新增：火焰免疫
		if (boost.fireImmunity()) {
			mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,
					Integer.MAX_VALUE, 0, false, false, false));
		}

		// v3 新增：生命恢复
		if (boost.regenLevel() > 0) {
			mob.addEffect(new MobEffectInstance(MobEffects.REGENERATION,
					Integer.MAX_VALUE, boost.regenLevel() - 1, false, false, false));
		}
	}

	/**
	 * v13: 生物死亡事件处理（LivingDeathEvent）
	 */
	public static void onLivingDeath(net.minecraft.world.entity.LivingEntity entity,
			net.minecraft.world.damagesource.DamageSource source) {
		if (!(entity instanceof Mob mob)) return;
		if (mob.level().isClientSide()) return;
		if (!isInBattlefield(mob)) return;
		if (IN_DEATH_PROCESS.get()) return;
		if (DEATH_PROCESSED.contains(mob.getUUID())) return;

		IN_DEATH_PROCESS.set(true);
		try {
			// 1. bad_1up_legendary: 死亡后复活一次满血
			if (!REVIVED_MOBS.contains(mob.getUUID())
					&& PlayerAffixManager.isAnyPlayerHasAffix("bad_1up_legendary")) {
				REVIVED_MOBS.add(mob.getUUID());
				mob.setHealth(mob.getMaxHealth());
			mob.setAbsorptionAmount(0.0F);
			return; // 复活后不处理其他死亡效果
			}

			// 2. bad_death_explode_rare: 死亡时在位置3格内爆炸(6伤害)
			if (PlayerAffixManager.isAnyPlayerHasAffix("bad_death_explode_rare")) {
				try {
					var level = mob.level();
					for (LivingEntity nearby : level.getEntitiesOfClass(
							LivingEntity.class,
							mob.getBoundingBox().inflate(3.0))) {
						if (nearby == mob) continue;
						if (nearby instanceof ServerPlayer) {
							nearby.hurt(nearby.damageSources().mobAttack(mob), 6.0F);
						}
					}
					level.explode(null, mob.getX(), mob.getY(), mob.getZ(), 0.0F,
						net.minecraft.world.level.Level.ExplosionInteraction.NONE);
				} catch (Throwable t) {
					RandomSurpriseMod.LOGGER.warn("死亡爆炸异常: {}", t.getMessage());
				}
			}

			// 3. bad_corpse_chain_legendary: 死亡爆炸会引爆4格内其他死亡怪物
			if (PlayerAffixManager.isAnyPlayerHasAffix("bad_corpse_chain_legendary")) {
				try {
					var level = mob.level();
					for (LivingEntity nearby : level.getEntitiesOfClass(
							LivingEntity.class,
							mob.getBoundingBox().inflate(4.0))) {
						if (nearby == mob) continue;
						if (nearby instanceof Mob nearbyMob && nearbyMob.getHealth() <= 0) {
							for (LivingEntity victim : level.getEntitiesOfClass(
									LivingEntity.class,
									nearbyMob.getBoundingBox().inflate(3.0))) {
								if (victim instanceof ServerPlayer) {
									victim.hurt(victim.damageSources().mobAttack(nearbyMob), 4.0F);
								}
							}
						}
					}
				} catch (Throwable t) {}
			}

			// 4. bad_death_split_epic: 死亡时生成2只同类型50%HP的怪物
			if (PlayerAffixManager.isAnyPlayerHasAffix("bad_death_split_epic")) {
				try {
					var level = mob.level();
					EntityType<?> type = mob.getType();
					for (int i = 0; i < 2; i++) {
						try {
							Entity spawned = type.create(level);
							if (spawned instanceof Mob splitMob) {
								double offsetX = (i == 0 ? -1 : 1) * 1.5;
								splitMob.moveTo(mob.getX() + offsetX, mob.getY(), mob.getZ(),
										mob.getYRot(), mob.getXRot());
								if (level instanceof ServerLevel serverLevel) {
									serverLevel.addFreshEntity(splitMob);
								}
								splitMob.setHealth(splitMob.getMaxHealth() * 0.5F);
								REVIVED_MOBS.add(splitMob.getUUID());
							}
						} catch (Throwable t2) {}
					}
				} catch (Throwable t) {}
			}
		} finally {
			DEATH_PROCESSED.add(mob.getUUID());
			IN_DEATH_PROCESS.set(false);
		}
	}

	/**
	 * v13: 生物受击事件处理（LivingHurtEvent）
	 * 处理怪物被攻击时的坏词条效果
	 * 处理怪物攻击玩家时的坏词条效果
	 */
	public static void onLivingHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
		var entity = event.getEntity();
		float amount = event.getAmount();
		var source = event.getSource();
		var attacker = source.getEntity();

		// === 怪物被攻击时 ===
		if (entity instanceof Mob mob && !mob.level().isClientSide() && isInBattlefield(mob)) {
			// bad_ender_blink_rare: 受击20%概率瞬移到玩家身后3格
			if (PlayerAffixManager.isAnyPlayerHasAffix("bad_ender_blink_rare")
					&& attacker instanceof ServerPlayer player && RNG.nextDouble() < 0.2) {
				try {
					double dx = mob.getX() - player.getX();
					double dz = mob.getZ() - player.getZ();
					double len = Math.sqrt(dx * dx + dz * dz);
					if (len > 0.001) {
						dx /= len; dz /= len;
						double newX = player.getX() + dx * 3.0;
						double newZ = player.getZ() + dz * 3.0;
						mob.teleportTo(newX, mob.getY(), newZ);
					}
				} catch (Throwable t) {}
			}

			// bad_hate_chain_uncommon: 受击时5格内同类增援
			if (PlayerAffixManager.isAnyPlayerHasAffix("bad_hate_chain_uncommon") && RNG.nextDouble() < 0.3) {
				try {
					var level = mob.level();
					EntityType<?> type = mob.getType();
					int sameTypeCount = 0;
					for (LivingEntity nearby : level.getEntitiesOfClass(
							LivingEntity.class, mob.getBoundingBox().inflate(5.0))) {
						if (nearby == mob) continue;
						if (nearby.getType() == type) sameTypeCount++;
					}
					if (sameTypeCount < 3) {
						Entity spawned = type.create(level);
						if (spawned instanceof Mob reinforceMob) {
							reinforceMob.moveTo(mob.getX() + (RNG.nextDouble() - 0.5) * 4,
									mob.getY(), mob.getZ() + (RNG.nextDouble() - 0.5) * 4,
									mob.getYRot(), mob.getXRot());
							if (level instanceof ServerLevel serverLevel) {
								serverLevel.addFreshEntity(reinforceMob);
							}
							reinforceMob.setHealth(reinforceMob.getMaxHealth() * 0.7F);
						}
					}
				} catch (Throwable t) {}
			}

			// bad_burning_aura_rare: 2格内近战玩家被点燃2秒
			if (PlayerAffixManager.isAnyPlayerHasAffix("bad_burning_aura_rare")
					&& attacker instanceof ServerPlayer player) {
				double dx = player.getX() - mob.getX();
				double dz = player.getZ() - mob.getZ();
				double distSq = dx * dx + dz * dz;
				if (distSq <= 4.0) { // 2格内
					player.setSecondsOnFire(2);
				}
			}

			// boss_teleport_rare: Boss受击时25%概率闪现到玩家身后（仅Boss）
			if (PlayerAffixManager.isAnyPlayerHasAffix("boss_teleport_rare")
					&& com.randomsurprise.battlefield.BattlefieldManager.isBattlefieldBoss(mob.getUUID())
					&& attacker instanceof ServerPlayer player && RNG.nextDouble() < 0.25) {
				try {
					double dx = mob.getX() - player.getX();
					double dz = mob.getZ() - player.getZ();
					double len = Math.sqrt(dx * dx + dz * dz);
					if (len > 0.001) {
						dx /= len; dz /= len;
						double newX = player.getX() + dx * 4.0;
						double newZ = player.getZ() + dz * 4.0;
						mob.teleportTo(newX, mob.getY(), newZ);
					}
				} catch (Throwable t) {}
			}
		}

		// === 怪物攻击玩家时 ===
		if (entity instanceof ServerPlayer player && attacker instanceof Mob mob
				&& !player.level().isClientSide() && isInBattlefield(mob)) {
			// bad_near_death_rage_uncommon: HP<30%时攻击+40%
			if (PlayerAffixManager.isAnyPlayerHasAffix("bad_near_death_rage_uncommon")) {
				if (mob.getHealth() < mob.getMaxHealth() * 0.3F) {
					event.setAmount(amount * 1.4F);
					// 速度+20%（仅当尚未有速度加成时）
					if (!mob.hasEffect(MobEffects.MOVEMENT_SPEED)) {
						mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, false, false, true));
					}
				}
			}
			// bad_knockback_cannon_rare: 攻击时强力击退玩家(额外3格)
			if (PlayerAffixManager.isAnyPlayerHasAffix("bad_knockback_cannon_rare")) {
				try {
					double dx = player.getX() - mob.getX();
					double dz = player.getZ() - mob.getZ();
					double len = Math.sqrt(dx * dx + dz * dz);
					if (len > 0.001) {
						dx /= len; dz /= len;
						player.push(dx * 0.6, 0.4, dz * 0.6);
					}
				} catch (Throwable t) {}
			}
			// bad_wither_touch_epic: 攻击施加凋零II 3秒
			if (PlayerAffixManager.isAnyPlayerHasAffix("bad_wither_touch_epic")) {
				player.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1, false, false, true));
			}
			// bad_rust_attack_uncommon: 攻击时损耗玩家头盔5点耐久
			if (PlayerAffixManager.isAnyPlayerHasAffix("bad_rust_attack_uncommon")) {
				try {
					var helmet = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
					if (!helmet.isEmpty()) {
						helmet.hurtAndBreak(5, player, p -> p.broadcastBreakEvent(net.minecraft.world.entity.EquipmentSlot.HEAD));
					}
				} catch (Throwable t) {}
			}
			// bad_web_trap_uncommon: 攻击时在玩家脚下放置蛛网
			if (PlayerAffixManager.isAnyPlayerHasAffix("bad_web_trap_uncommon") && RNG.nextDouble() < 0.3) {
				try {
					var level = player.level();
					BlockPos pos = player.blockPosition();
					if (level.getBlockState(pos).isAir() && level instanceof ServerLevel serverLevel) {
						serverLevel.setBlock(pos, Blocks.COBWEB.defaultBlockState(), 3);
					}
				} catch (Throwable t) {}
			}
		}
	}

	/**
	 * v13: 服务器tick事件处理
	 * 处理周期性坏词条机制
	 */
	public static void onServerTick(MinecraftServer server) {
		long tick = server.getTickCount();
		// 每20 tick（1秒）处理周期性效果
		if (tick % 20 != 0) return;

		for (ServerLevel level : server.getAllLevels()) {
			if (!level.dimension().equals(BattlefieldDimension.BATTLEFIELD_DIM_KEY)) continue;

			// 遍历所有敌对生物（竞技场范围足够大）
			net.minecraft.world.phys.AABB arenaBox = new net.minecraft.world.phys.AABB(-200, -64, -200, 200, 320, 200);
			for (Mob mob : level.getEntitiesOfClass(Mob.class, arenaBox)) {
				if (!(mob instanceof Enemy) && !isHostileMob(mob)) continue;
				processMobTick(mob, level);
			}
		}
	}

	/** 处理单个怪物的周期性坏词条效果 */
	private static void processMobTick(Mob mob, ServerLevel level) {
		UUID mobId = mob.getUUID();

		// bad_enrage_timer_epic: 战斗10秒后狂暴
		if (PlayerAffixManager.isAnyPlayerHasAffix("bad_enrage_timer_epic")) {
			int battleTimer = ENRAGE_BATTLE_TIMERS.getOrDefault(mobId, 0) + 1;
			ENRAGE_BATTLE_TIMERS.put(mobId, battleTimer);
			if (battleTimer >= 10 && !ENRAGED_MOBS.contains(mobId)) {
				ENRAGED_MOBS.add(mobId);
				try {
					var attr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
					if (attr != null) {
						attr.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
								"bad_enrage_bonus", 0.5,
								net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
					}
				} catch (Throwable t) {}
				// 给予力量效果
				mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, Integer.MAX_VALUE, 1, false, false, true));
			}
		}

		// bad_regen_monster_rare: 每秒恢复1%HP
		if (PlayerAffixManager.isAnyPlayerHasAffix("bad_regen_monster_rare")) {
			if (mob.getHealth() < mob.getMaxHealth()) {
				mob.heal(mob.getMaxHealth() * 0.01F);
			}
		}

		// bad_cloak_epic: 每10秒隐身3秒
		if (PlayerAffixManager.isAnyPlayerHasAffix("bad_cloak_epic")) {
			int cloakCd = CLOAK_TIMERS.getOrDefault(mobId, 0);
			if (cloakCd <= 0) {
				mob.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60, 0, false, false, true));
				CLOAK_TIMERS.put(mobId, 10);
			} else {
				CLOAK_TIMERS.put(mobId, cloakCd - 1);
			}
		}

		// bad_storm_call_epic: 每10秒在玩家位置召唤闪电
		if (PlayerAffixManager.isAnyPlayerHasAffix("bad_storm_call_epic")) {
			int stormCd = STORM_TIMERS.getOrDefault(mobId, 0);
			if (stormCd <= 0) {
				try {
					ServerPlayer target = findNearestPlayer(level, mob, 15.0);
					if (target != null) {
						LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
						if (bolt != null) {
							bolt.moveTo(target.getX(), target.getY(), target.getZ());
							bolt.setVisualOnly(false);
							level.addFreshEntity(bolt);
						}
					}
				} catch (Throwable t) {}
				STORM_TIMERS.put(mobId, 10);
			} else {
				STORM_TIMERS.put(mobId, stormCd - 1);
			}
		}

		// bad_fireball_shooter_rare: 每5秒向玩家发射火球
		if (PlayerAffixManager.isAnyPlayerHasAffix("bad_fireball_shooter_rare")) {
			int fbCd = FIREBALL_TIMERS.getOrDefault(mobId, 0);
			if (fbCd <= 0) {
				try {
					ServerPlayer target = findNearestPlayer(level, mob, 15.0);
					if (target != null) {
						Vec3 direction = new Vec3(target.getX() - mob.getX(),
								target.getEyeY() - mob.getEyeY(),
								target.getZ() - mob.getZ()).normalize();
						SmallFireball fireball = new SmallFireball(level, mob,
							direction.x * 0.5, direction.y * 0.5, direction.z * 0.5);
						fireball.setPos(mob.getX(), mob.getEyeY(), mob.getZ());
						level.addFreshEntity(fireball);
					}
				} catch (Throwable t) {}
				FIREBALL_TIMERS.put(mobId, 5);
			} else {
				FIREBALL_TIMERS.put(mobId, fbCd - 1);
			}
		}

		// bad_heal_ally_epic: 每5秒为5格内友方怪物恢复5%HP
		if (PlayerAffixManager.isAnyPlayerHasAffix("bad_heal_ally_epic")) {
			int healCd = HEAL_ALLY_TIMERS.getOrDefault(mobId, 0);
			if (healCd <= 0) {
				try {
					for (LivingEntity nearby : level.getEntitiesOfClass(
							LivingEntity.class, mob.getBoundingBox().inflate(5.0))) {
						if (nearby == mob) continue;
						if (nearby instanceof Mob allyMob && isFriendlyMob(allyMob, mob)) {
							if (allyMob.getHealth() < allyMob.getMaxHealth()) {
								allyMob.heal(allyMob.getMaxHealth() * 0.05F);
							}
						}
					}
				} catch (Throwable t) {}
				HEAL_ALLY_TIMERS.put(mobId, 5);
			} else {
				HEAL_ALLY_TIMERS.put(mobId, healCd - 1);
			}
		}

		// bad_shield_charge_epic: 每15秒3秒免疫护盾
		if (PlayerAffixManager.isAnyPlayerHasAffix("bad_shield_charge_epic")) {
			int shieldCd = SHIELD_CHARGE_TIMERS.getOrDefault(mobId, 0);
			if (shieldCd <= 0) {
				mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false, true));
				SHIELD_CHARGE_TIMERS.put(mobId, 15);
			} else {
				SHIELD_CHARGE_TIMERS.put(mobId, shieldCd - 1);
			}
		}

		// bad_darkness_aura_rare: 3格内玩家获得失明3秒
		if (PlayerAffixManager.isAnyPlayerHasAffix("bad_darkness_aura_rare")) {
			try {
				for (ServerPlayer nearby : level.getEntitiesOfClass(
						ServerPlayer.class, mob.getBoundingBox().inflate(3.0))) {
					nearby.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0, false, false, true));
				}
			} catch (Throwable t) {}
		}

		// ========== v13: Boss 专属机制（仅Boss实体生效） ==========
		boolean isBoss = com.randomsurprise.battlefield.BattlefieldManager.isBattlefieldBoss(mobId);

		// boss_fire_trail_uncommon: Boss每秒在脚下放置火焰（仅Boss）
		if (isBoss && PlayerAffixManager.isAnyPlayerHasAffix("boss_fire_trail_uncommon")) {
			try {
				BlockPos firePos = mob.blockPosition().below();
				if (level.getBlockState(firePos).isAir() || level.getBlockState(firePos).is(Blocks.FIRE)) {
					level.setBlock(firePos, Blocks.FIRE.defaultBlockState(), 3);
				}
			} catch (Throwable t) {}
		}

		// boss_lightning_strike_epic: Boss每8秒在自身周围3格召唤闪电（仅Boss）
		if (isBoss && PlayerAffixManager.isAnyPlayerHasAffix("boss_lightning_strike_epic")) {
			int bossLtCd = BOSS_LIGHTNING_TIMERS.getOrDefault(mobId, 0);
			if (bossLtCd <= 0) {
				try {
					// 在Boss周围随机位置召唤3道闪电
					for (int i = 0; i < 3; i++) {
						double offsetX = (RNG.nextDouble() - 0.5) * 6.0;
						double offsetZ = (RNG.nextDouble() - 0.5) * 6.0;
						LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
						if (bolt != null) {
							bolt.moveTo(mob.getX() + offsetX, mob.getY(), mob.getZ() + offsetZ);
							bolt.setVisualOnly(false);
							level.addFreshEntity(bolt);
						}
					}
				} catch (Throwable t) {}
				BOSS_LIGHTNING_TIMERS.put(mobId, 8);
			} else {
				BOSS_LIGHTNING_TIMERS.put(mobId, bossLtCd - 1);
			}
		}

		// boss_frost_aura_uncommon: Boss 5格内玩家获得缓慢II 2秒（仅Boss）
		if (isBoss && PlayerAffixManager.isAnyPlayerHasAffix("boss_frost_aura_uncommon")) {
			try {
				for (ServerPlayer nearby : level.getEntitiesOfClass(
						ServerPlayer.class, mob.getBoundingBox().inflate(5.0))) {
					nearby.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false, true));
				}
			} catch (Throwable t) {}
		}

		// boss_wither_aura_rare: Boss 4格内玩家获得凋零I 3秒（仅Boss）
		if (isBoss && PlayerAffixManager.isAnyPlayerHasAffix("boss_wither_aura_rare")) {
			try {
				for (ServerPlayer nearby : level.getEntitiesOfClass(
						ServerPlayer.class, mob.getBoundingBox().inflate(4.0))) {
					nearby.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0, false, false, true));
				}
			} catch (Throwable t) {}
		}
	}

	/** 查找最近的玩家 */
	private static ServerPlayer findNearestPlayer(ServerLevel level, Mob mob, double maxDist) {
		ServerPlayer nearest = null;
		double nearestDist = maxDist * maxDist;
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
				mob.getBoundingBox().inflate(maxDist))) {
			double dist = player.distanceToSqr(mob);
			if (dist < nearestDist) {
				nearestDist = dist;
				nearest = player;
			}
		}
		return nearest;
	}

	/** 判断两个怪物是否为友方 */
	private static boolean isFriendlyMob(Mob a, Mob b) {
		return a.getType() == b.getType()
				|| (a instanceof Enemy && b instanceof Enemy);
	}

	/**
	 * 判断是否为敌对生物（兜底，Enemy接口未覆盖的类型）
	 */
	private static boolean isHostileMob(Mob mob) {
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
}
