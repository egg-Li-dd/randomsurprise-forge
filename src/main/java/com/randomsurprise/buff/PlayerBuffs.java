package com.randomsurprise.buff;

import com.randomsurprise.RandomSurpriseMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 临时增益管理器（随机事件触发，30~60 秒持续）
 *
 * 10 种 Buff：
 * - TAUNT 仇恨吸引（60s）- 每 20t 强制周围 32 格内所有敌对生物以玩家为目标
 * - FIRE_TRAIL 过境火焰（30s）- 每 5t 在玩家脚下放置火焰（可造成伤害）+ 火焰粒子
 * - MIDAS_TOUCH 点石成金（60s）- 破坏石类方块时额外掉落随机矿物
 * - MAGNET 磁铁吸引（60s）- 每 5t 将 16 格内的物品实体/经验球拉向玩家
 * - FLOWER_TRAIL 百花足迹（60s）- 每 10t 在脚下草地/泥土种花 + 花瓣粒子
 * - STATIC_FIELD 雷霆体质（30s）- 玩家攻击生物 30% 概率召唤闪电（造成伤害）
 * - BOUNCY 跳跳人体质（30s）- 每 t 玩家在地面时自动弹起（持续弹跳）
 * - GLOWING 荧光显现（30s）- 每 5s 刷新发光效果
 * - FROST_AURA 冰霜光环（30s）- 每 20t 给 8 格内生物缓慢 II + 雪花粒子
 * - MIGHTY_SWING 重击（60s）- 玩家攻击时对目标 4 格内其他生物造成 50% 溅射伤害
 *
 * 与 Affix 永久词条系统不同：Buff 是临时的、由随机事件触发的。
 */
public class PlayerBuffs {
	private static final Random RANDOM = new Random();
	private static final Map<UUID, Map<BuffType, Integer>> REMAINING_TICKS = new HashMap<>();

	/** Buff 类型枚举 */
	public enum BuffType {
		TAUNT(60),
		FIRE_TRAIL(30),
		MIDAS_TOUCH(60),
		MAGNET(60),
		FLOWER_TRAIL(60),
		STATIC_FIELD(30),
		BOUNCY(30),
		GLOWING(30),
		FROST_AURA(30),
		MIGHTY_SWING(60);

		private final int durationSeconds;

		BuffType(int seconds) {
			this.durationSeconds = seconds;
		}

		public int getDurationSeconds() {
			return durationSeconds;
		}

		public int getDurationTicks() {
			return durationSeconds * 20;
		}
	}

	/** 赋予玩家一个 Buff */
	public static void grantBuff(ServerPlayer player, BuffType type) {
		REMAINING_TICKS.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
				.put(type, type.getDurationTicks());
		// GLOWING 立即施加一次发光效果
		if (type == BuffType.GLOWING) {
			player.addEffect(new MobEffectInstance(MobEffects.GLOWING,
					type.getDurationTicks(), 0, true, false, true));
		}
		// BOUNCY 给予跳跃提升效果作为视觉提示
		if (type == BuffType.BOUNCY) {
			player.addEffect(new MobEffectInstance(MobEffects.JUMP,
					type.getDurationTicks(), 5, true, true, true));
		}
	}

	/** 玩家是否拥有指定 Buff */
	public static boolean hasBuff(UUID playerId, BuffType type) {
		Map<BuffType, Integer> map = REMAINING_TICKS.get(playerId);
		return map != null && map.getOrDefault(type, 0) > 0;
	}

	/** 玩家剩余秒数（用于 HUD/聊天显示，0 表示无） */
	public static int getRemainingSeconds(UUID playerId, BuffType type) {
		Map<BuffType, Integer> map = REMAINING_TICKS.get(playerId);
		if (map == null) return 0;
		return map.getOrDefault(type, 0) / 20;
	}

	/** 玩家退出时清理 */
	public static void onPlayerQuit(UUID playerId) {
		REMAINING_TICKS.remove(playerId);
	}

	// ========== Tick 处理（每 tick 调用一次）==========

	/**
	 * 服务器 tick 回调，由 RandomSurpriseMod 注册的
	 * ServerTickEvents.END_SERVER_TICK 调用
	 */
	public static void onServerTick(MinecraftServer server) {
		if (REMAINING_TICKS.isEmpty()) return;

		var playerIter = REMAINING_TICKS.entrySet().iterator();
		while (playerIter.hasNext()) {
			var entry = playerIter.next();
			UUID uuid = entry.getKey();
			Map<BuffType, Integer> buffs = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(uuid);
			if (player == null || !player.isAlive()) {
				// 玩家离线或死亡：清理 Buff
				playerIter.remove();
				continue;
			}

			// 自增式迭代并减少剩余 tick
			var buffIter = buffs.entrySet().iterator();
			while (buffIter.hasNext()) {
				var buffEntry = buffIter.next();
				BuffType type = buffEntry.getKey();
				int remaining = buffEntry.getValue() - 1;
				if (remaining <= 0) {
					buffIter.remove();
					continue;
				}
				buffEntry.setValue(remaining);

				// 按 cadence 触发各 Buff 的 tick 处理
				try {
					switch (type) {
						case TAUNT -> {
							if (remaining % 20 == 0) handleTaunt(player);
						}
						case FIRE_TRAIL -> {
							if (remaining % 5 == 0) handleFireTrail(player);
						}
						case MAGNET -> {
							if (remaining % 5 == 0) handleMagnet(player);
						}
						case FLOWER_TRAIL -> {
							if (remaining % 10 == 0) handleFlowerTrail(player);
						}
						case FROST_AURA -> {
							if (remaining % 20 == 0) handleFrostAura(player);
						}
						case BOUNCY -> handleBouncy(player);
						case GLOWING -> {
							if (remaining % 100 == 0) handleGlowing(player, remaining);
						}
						// MIDAS_TOUCH / STATIC_FIELD / MIGHTY_SWING：事件驱动，无 tick 处理
						case MIDAS_TOUCH, STATIC_FIELD, MIGHTY_SWING -> {
						}
					}
				} catch (Throwable t) {
					RandomSurpriseMod.LOGGER.warn("处理增益 {} 时出错: {}", type, t.getMessage());
				}
			}

			if (buffs.isEmpty()) {
				playerIter.remove();
			}
		}
	}

	// ========== 事件驱动处理 ==========

	/**
	 * 玩家破坏方块时调用（由 PlayerBlockBreakEvents.AFTER 转发）
	 * 处理 MIDAS_TOUCH：破坏石类方块时额外掉落随机矿物
	 */
	public static void onBlockBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		if (!hasBuff(player.getUUID(), BuffType.MIDAS_TOUCH)) return;

		Block block = state.getBlock();
		// 仅对石类方块生效
		boolean isStoneFamily = block == Blocks.STONE || block == Blocks.COBBLESTONE
				|| block == Blocks.DEEPSLATE || block == Blocks.COBBLED_DEEPSLATE
				|| block == Blocks.GRANITE || block == Blocks.DIORITE || block == Blocks.ANDESITE
				|| block == Blocks.TUFF || block == Blocks.DRIPSTONE_BLOCK
				|| block == Blocks.NETHERRACK || block == Blocks.BLACKSTONE
				|| block == Blocks.BASALT || block == Blocks.END_STONE;
		if (!isStoneFamily) return;

		// 在原位置额外掉落随机矿物（不影响原方块掉落）
		ItemStack oreStack = createRandomOreStack();
		Block.popResource(level, pos, oreStack);
	}

	/**
	 * 实体受到伤害后调用（由 ServerLivingEntityEvents.AFTER_DAMAGE 转发）
	 * 处理 STATIC_FIELD（雷霆体质）+ MIGHTY_SWING（重击溅射）
	 */
	public static void onEntityDamaged(LivingEntity entity, DamageSource source, float amount) {
		if (amount <= 0) return;
		Entity attacker = source.getEntity();
		if (!(attacker instanceof ServerPlayer player)) return;

		// STATIC_FIELD：30% 概率在目标处召唤闪电（仅视觉+直接伤害，不点燃）
		if (hasBuff(player.getUUID(), BuffType.STATIC_FIELD) && entity instanceof Mob) {
			if (RANDOM.nextDouble() < 0.30) {
				var level = entity.level();
				var lightning = EntityType.LIGHTNING_BOLT.create(level);
				if (lightning != null) {
					lightning.setPos(entity.getX(), entity.getY(), entity.getZ());
					lightning.setVisualOnly(true); // 仅视觉，不点燃方块、不产生巨大雷声
					level.addFreshEntity(lightning);
					((Mob) entity).hurt(player.damageSources().lightningBolt(), 3.0F);
					// 播放较小的电击音效
					level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
							net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT,
							net.minecraft.sounds.SoundSource.PLAYERS, 0.3F, 1.5F);
				}
			}
		}

		// MIGHTY_SWING：对目标 4 格内其他敌对生物造成 50% 溅射伤害
		if (hasBuff(player.getUUID(), BuffType.MIGHTY_SWING) && entity instanceof Mob) {
			var level = entity.level();
			if (level instanceof ServerLevel serverLevel) {
				AABB aabb = AABB.ofSize(entity.position(), 8, 4, 8);
				for (Mob nearby : serverLevel.getEntitiesOfClass(Mob.class, aabb)) {
					if (nearby == entity || (Entity) nearby == player) continue;
					if (nearby.distanceToSqr(entity) > 16) continue; // 4 格半径
					nearby.hurt(level.damageSources().playerAttack(player), amount * 0.5f);
				}
				// 重击粒子效果
				serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
						entity.getX(), entity.getY() + 1, entity.getZ(),
						3, 1.0, 0.5, 1.0, 0.0);
			}
		}
	}

	// ========== 单 Buff 内部处理 ==========

	/** 仇恨吸引：将 32 格内所有敌对生物的目标设为玩家 */
	private static void handleTaunt(ServerPlayer player) {
		var level = player.level();
		if (!(level instanceof ServerLevel serverLevel)) return;
		AABB aabb = AABB.ofSize(player.position(), 64, 32, 64);
		for (Mob mob : serverLevel.getEntitiesOfClass(Mob.class, aabb)) {
			if (mob.distanceToSqr(player) > 32 * 32) continue;
			mob.setTarget(player);
		}
		// 怒气粒子
		serverLevel.sendParticles(ParticleTypes.ANGRY_VILLAGER,
				player.getX(), player.getY() + 2, player.getZ(),
				3, 0.5, 0.3, 0.5, 0.0);
	}

	/** 过境火焰：在脚下放置火焰 + 火焰粒子 */
	private static void handleFireTrail(ServerPlayer player) {
		var level = player.level();
		BlockPos pos = player.blockPosition();
		BlockState belowState = level.getBlockState(pos.below());
		BlockState atState = level.getBlockState(pos);
		// 仅当下方方块稳固且脚部为空气时放置火焰
		if (atState.isAir() && belowState.isFaceSturdy(level, pos.below(), Direction.UP)) {
			level.setBlock(pos, Blocks.FIRE.defaultBlockState(), 3);
		}
		// 火焰粒子
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.FLAME,
					player.getX(), player.getY() + 0.1, player.getZ(),
					8, 0.3, 0.1, 0.3, 0.02);
			serverLevel.sendParticles(ParticleTypes.LAVA,
					player.getX(), player.getY() + 0.1, player.getZ(),
					1, 0.2, 0.1, 0.2, 0.0);
		}
	}

	/** 磁铁吸引：将 16 格内物品/经验球拉向玩家 */
	private static void handleMagnet(ServerPlayer player) {
		var level = player.level();
		if (!(level instanceof ServerLevel serverLevel)) return;
		AABB aabb = AABB.ofSize(player.position(), 32, 16, 32);
		// 拉取掉落物
		for (ItemEntity item : serverLevel.getEntitiesOfClass(ItemEntity.class, aabb)) {
			if (item.distanceToSqr(player) > 16 * 16) continue;
			pullToward(item, player);
		}
		// 拉取经验球
		for (ExperienceOrb orb : serverLevel.getEntitiesOfClass(ExperienceOrb.class, aabb)) {
			if (orb.distanceToSqr(player) > 16 * 16) continue;
			pullToward(orb, player);
		}
	}

	/** 拉取实体朝向玩家 */
	private static void pullToward(Entity entity, ServerPlayer player) {
		double dx = player.getX() - entity.getX();
		double dy = (player.getY() + 0.5) - entity.getY();
		double dz = player.getZ() - entity.getZ();
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dist < 0.5) return;
		double speed = 0.55;
		entity.setDeltaMovement(
				(dx / dist) * speed,
				(dy / dist) * speed,
				(dz / dist) * speed);
	}

	/** 百花足迹：在脚下草地/泥土种花 + 花瓣粒子 */
	private static void handleFlowerTrail(ServerPlayer player) {
		var level = player.level();
		BlockPos pos = player.blockPosition();
		BlockState belowState = level.getBlockState(pos.below());
		// 仅在草地/泥土/苔藓上种花
		if ((belowState.is(Blocks.GRASS_BLOCK) || belowState.is(Blocks.DIRT)
				|| belowState.is(Blocks.MOSS_BLOCK))
				&& level.getBlockState(pos).isAir()) {
			Block flower = randomFlower();
			level.setBlock(pos, flower.defaultBlockState(), 3);
		}
		// 花瓣粒子
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
					player.getX(), player.getY() + 0.1, player.getZ(),
					5, 0.4, 0.2, 0.4, 0.1);
		}
	}

	/** 随机返回一种小花 */
	private static Block randomFlower() {
		Block[] flowers = {
				Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM,
				Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP,
				Blocks.PINK_TULIP, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY
		};
		return flowers[RANDOM.nextInt(flowers.length)];
	}

	/** 冰霜光环：8 格内生物获得缓慢 II + 雪花粒子 */
	private static void handleFrostAura(ServerPlayer player) {
		var level = player.level();
		if (!(level instanceof ServerLevel serverLevel)) return;
		AABB aabb = AABB.ofSize(player.position(), 16, 8, 16);
		for (Mob mob : serverLevel.getEntitiesOfClass(Mob.class, aabb)) {
			if ((Entity) mob == player) continue;
			if (mob.distanceToSqr(player) > 8 * 8) continue;
			mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, true, true, true));
		}
		// 雪花粒子
		serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
				player.getX(), player.getY() + 1, player.getZ(),
				10, 1.5, 0.5, 1.5, 0.05);
	}

	/** 跳跳人体质：每 tick 玩家在地面时弹起 */
	private static void handleBouncy(ServerPlayer player) {
		if (player.onGround()) {
			player.setDeltaMovement(
					player.getDeltaMovement().x,
					0.6, // 弹跳速度
					player.getDeltaMovement().z);
		}
	}

	/** 荧光显现：刷新发光效果 */
	private static void handleGlowing(ServerPlayer player, int remainingTicks) {
		int duration = Math.min(120, remainingTicks); // 至多 6 秒，5 秒一刷新
		player.addEffect(new MobEffectInstance(MobEffects.GLOWING,
				duration, 0, true, false, true));
	}

	// ========== MIDAS_TOUCH 辅助 ==========

	/** 创建一个随机矿物物品堆（加权，普通矿物权重高） */
	private static ItemStack createRandomOreStack() {
		// 加权随机：权重 = 数组中重复次数
		Item[] ores = {
				Items.COAL, Items.COAL, Items.COAL, Items.COAL,        // 权重 4
				Items.RAW_IRON, Items.RAW_IRON, Items.RAW_IRON,        // 3
				Items.RAW_COPPER, Items.RAW_COPPER,                    // 2
				Items.RAW_GOLD, Items.RAW_GOLD,                        // 2
				Items.REDSTONE, Items.REDSTONE,                        // 2
				Items.LAPIS_LAZULI,                                    // 1
				Items.EMERALD,                                         // 1
				Items.DIAMOND,                                         // 1
				Items.QUARTZ                                           // 1
		};
		Item ore = ores[RANDOM.nextInt(ores.length)];
		return new ItemStack(ore, 1 + RANDOM.nextInt(2)); // 1~2 个
	}
}
