package com.randomsurprise.battlefield;

import com.randomsurprise.superpower.PlayerSuperPowerManager;
import com.randomsurprise.superpower.SuperPower;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 征召战场倒地状态管理器
 *
 * 功能：
 * 1. 玩家血量清零时不立即死亡，进入倒地状态
 * 2. 倒地玩家免疫所有伤害，不被怪物攻击
 * 3. 倒地后无法自救，只能由队友救助
 * 4. 队友靠近倒地玩家5格内持续5秒即可救助复活（无需右键）
 * 5. 所有玩家倒地时宣布失败
 *
 * 倒地动画：
 * - 倒地瞬间红色粒子爆发
 * - 倒地期间持续红色尘埃粒子（受伤视觉反馈）
 * - 复活时爱心粒子效果
 */
public class DownedStateManager {

	private static final Map<UUID, DownedState> DOWNED_PLAYERS = new HashMap<>();

	/** 复活需要长按右键的tick数 - 5秒 = 100 tick */
	private static final int REVIVE_HOLD_TICKS = 100;
	/** 急速救援超能力缩短后的救助时间 - 2秒 = 40 tick */
	private static final int RAPID_RESCUE_REVIVE_TICKS = 40;
	/** 怪物目标清除检查间隔（tick） */
	private static final int MOB_TARGET_CLEAR_INTERVAL = 20;
	/** 倒地持续粒子效果间隔（tick） */
	private static final int DOWNED_PARTICLE_INTERVAL = 40;
	/** 队友救助最大距离（格） */
	private static final double ALLY_REVIVE_MAX_DISTANCE = 5.0;

	/** 倒地状态数据 */
	private static class DownedState {
		long downedTime;            // 倒地时刻（gameTime），用于单人自救窗口
		long reviveProgress;        // 自复活进度
		boolean rightClickHeld;     // 自复活右键状态
		long allyReviveProgress;    // 队友救助进度
		UUID allyReviverId;         // 当前救助者ID（同一时间仅一人可救助）

		DownedState() {
			this.downedTime = -1;
			this.reviveProgress = 0;
			this.rightClickHeld = false;
			this.allyReviveProgress = 0;
			this.allyReviverId = null;
		}
	}

	/** 怪物目标清除计数器 */
	private static int mobTargetClearCounter = 0;
	/** 倒地粒子效果计数器 */
	private static int downedParticleCounter = 0;

	/**
	 * 尝试使玩家进入倒地状态
	 * @return true 如果进入倒地状态，false 如果直接死亡
	 */
	public static boolean tryDown(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)) return false;
		if (!BattlefieldDimension.isInBattlefield(serverPlayer)) return false;
		if (isDowned(serverPlayer.getUUID())) return false;

		DownedState state = new DownedState();
		state.downedTime = serverPlayer.level().getGameTime();
		DOWNED_PLAYERS.put(serverPlayer.getUUID(), state);

		// 应用倒地效果
		applyDownedEffects(serverPlayer);

		// 设置极低血量（防止真正死亡）
		serverPlayer.setHealth(0.5F);

		// 倒地粒子爆发动画
		spawnDownedParticles(serverPlayer);

		// 广播消息
		BattlefieldManager.broadcast(serverPlayer.level().getServer(),
				Component.translatable("battlefield.randomsurprise.downed", player.getDisplayName().getString()));

		// 提示玩家需要队友靠近救助
		serverPlayer.sendSystemMessage(Component.translatable("battlefield.randomsurprise.downed_hint"));

		return true;
	}

	/**
	 * 检查玩家是否处于倒地状态
	 */
	public static boolean isDowned(UUID playerId) {
		return DOWNED_PLAYERS.containsKey(playerId);
	}

	/**
	 * 清除玩家倒地状态（复活或真正死亡）
	 */
	public static void clearDowned(UUID playerId) {
		DOWNED_PLAYERS.remove(playerId);
	}

	/**
	 * 设置玩家右键按住状态（客户端通过网络包通知服务端）- 自复活用
	 */
	public static void setRightClickHeld(UUID playerId, boolean held) {
		DownedState state = DOWNED_PLAYERS.get(playerId);
		if (state != null) {
			state.rightClickHeld = held;
			if (!held) {
				state.reviveProgress = 0;
			}
		}
	}

	/**
	 * 设置队友救助状态（救助者通过网络包通知服务端）
	 * @param targetId 倒地玩家ID
	 * @param reviverId 救助者ID
	 * @param start true=开始救助, false=停止救助
	 */
	public static void setAllyReviving(UUID targetId, UUID reviverId, boolean start) {
		// 清除该救助者对其他倒地玩家的救助关系（防止同时救助多人）
		for (var entry : DOWNED_PLAYERS.entrySet()) {
			DownedState other = entry.getValue();
			if (other.allyReviverId != null && other.allyReviverId.equals(reviverId)
					&& !entry.getKey().equals(targetId)) {
				other.allyReviverId = null;
				other.allyReviveProgress = 0;
			}
		}

		DownedState state = DOWNED_PLAYERS.get(targetId);
		if (state == null) return;

		if (start) {
			// 同一时间仅允许一名救助者
			if (state.allyReviverId == null || state.allyReviverId.equals(reviverId)) {
				state.allyReviverId = reviverId;
			}
		} else {
			// 仅取消自己的救助
			if (state.allyReviverId != null && state.allyReviverId.equals(reviverId)) {
				state.allyReviverId = null;
				state.allyReviveProgress = 0;
			}
		}
	}

	/**
	 * 对倒地玩家应用效果
	 */
	private static void applyDownedEffects(ServerPlayer player) {
		// 缓慢IV（无法移动）
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Integer.MAX_VALUE, 3, false, false, false));
		// 虚弱IV（无法攻击）
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, Integer.MAX_VALUE, 3, false, false, false));
		// 致盲（轻微视野模糊，模拟趴下视角）
		player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, Integer.MAX_VALUE, 0, false, false, false));
		// 发光（便于队友发现）
		player.addEffect(new MobEffectInstance(MobEffects.GLOWING, Integer.MAX_VALUE, 0, false, false, false));
		// 隐身（优化倒地视觉：隐藏玩家本体，仅保留发光轮廓和粒子效果）
		// 注意：发光+隐身会显示轮廓线但不显示玩家模型，营造"倒下"视觉效果
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
	}

	/**
	 * 清除倒地效果
	 */
	private static void removeDownedEffects(ServerPlayer player) {
		player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		player.removeEffect(MobEffects.WEAKNESS);
		player.removeEffect(MobEffects.BLINDNESS);
		player.removeEffect(MobEffects.GLOWING);
		player.removeEffect(MobEffects.INVISIBILITY);
	}

	/**
	 * 倒地粒子爆发（倒地瞬间触发）
	 */
	private static void spawnDownedParticles(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 pos = player.position();
		// 红色伤害粒子爆发（20个，范围1.5格）
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
				pos.x, pos.y + 1, pos.z, 20,
				1.0, 0.8, 1.0, 0.1);
		// 红石粒子（更密集的红色效果）
		level.sendParticles(ParticleTypes.CRIMSON_SPORE,
				pos.x, pos.y + 1, pos.z, 30,
				1.5, 1.0, 1.5, 0.05);
	}

	/**
	 * 倒地持续粒子效果（每2秒触发一次，表示受伤状态）
	 */
	private static void spawnDownedIdleParticles(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 pos = player.position();
		// 少量红色粒子（持续流血视觉反馈）
		level.sendParticles(ParticleTypes.CRIMSON_SPORE,
				pos.x, pos.y + 0.5, pos.z, 8,
				0.6, 0.5, 0.6, 0.02);
	}

	/**
	 * 复活粒子效果（复活成功时触发）
	 */
	private static void spawnReviveParticles(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Vec3 pos = player.position();
		// 爱心粒子（复活庆祝）
		level.sendParticles(ParticleTypes.HEART,
				pos.x, pos.y + 1.5, pos.z, 12,
				1.0, 0.8, 1.0, 0.1);
		// 快乐村民粒子（绿色闪光，恢复感）
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
				pos.x, pos.y + 1, pos.z, 10,
				0.8, 0.6, 0.8, 0.1);
	}

	/**
	 * 自复活（满血复活）
	 */
	public static void revivePlayer(ServerPlayer player) {
		if (!isDowned(player.getUUID())) return;

		clearDowned(player.getUUID());
		removeDownedEffects(player);

		player.setHealth(player.getMaxHealth());

		// 给予短暂无敌（3秒）
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false, false));
		// 短暂生命恢复
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1, false, false, false));

		// 复活粒子动画
		spawnReviveParticles(player);

		BattlefieldManager.broadcast(player.level().getServer(),
				Component.translatable("battlefield.randomsurprise.revived_self", player.getDisplayName().getString()));
	}

	/**
	 * 队友救助复活（满血复活）
	 * @param target 被救助的倒地玩家
	 * @param reviver 救助者
	 */
	private static void reviveByAlly(ServerPlayer target, ServerPlayer reviver) {
		if (!isDowned(target.getUUID())) return;

		clearDowned(target.getUUID());
		removeDownedEffects(target);

		target.setHealth(target.getMaxHealth());

		// 给予短暂无敌（3秒）
		target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false, false));
		// 短暂生命恢复
		target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1, false, false, false));

		// 复活粒子动画
		spawnReviveParticles(target);

		BattlefieldManager.broadcast(target.level().getServer(),
				Component.translatable("battlefield.randomsurprise.revived",
						target.getDisplayName().getString(), reviver.getDisplayName().getString()));
	}

	/**
	 * 倒地状态tick更新
	 */
	public static void tick(MinecraftServer server) {
		if (DOWNED_PLAYERS.isEmpty()) return;

		for (UUID playerId : new java.util.ArrayList<>(DOWNED_PLAYERS.keySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player == null) {
				clearDowned(playerId);
				continue;
			}

			// 如果玩家已不在战场维度，清除倒地状态
			if (!BattlefieldDimension.isInBattlefield(player)) {
				clearDowned(playerId);
				removeDownedEffects(player);
				continue;
			}

			DownedState state = DOWNED_PLAYERS.get(playerId);

			// 检查是否有队友在附近（靠近即可救助，无需右键）
			boolean allyNearby = false;
			ServerPlayer allyReviver = null;
			boolean hasRapidRescue = false;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (p.getUUID().equals(playerId)) continue;
				// 救助者必须在战场、未倒地、距离<=5格
				if (BattlefieldDimension.isInBattlefield(p) && !isDowned(p.getUUID())
						&& p.distanceTo(player) <= ALLY_REVIVE_MAX_DISTANCE) {
					allyNearby = true;
					allyReviver = p;
					// 检查救助者是否拥有急速救援超能力
					SuperPower sp = PlayerSuperPowerManager.getSuperPower(p.getUUID());
					if (sp == SuperPower.RAPID_RESCUE) {
						hasRapidRescue = true;
					}
				}
			}
			if (allyNearby) {
				// 急速救援超能力将救助时间从100tick降为40tick
				int effectiveTicks = hasRapidRescue ? RAPID_RESCUE_REVIVE_TICKS : REVIVE_HOLD_TICKS;
				state.allyReviveProgress++;
				if (state.allyReviveProgress >= effectiveTicks) {
					reviveByAlly(player, allyReviver);
					continue;
				}
			} else {
				// 没有队友在附近，进度衰减（每tick -2，比积累快一倍，防止来回蹭进度）
				state.allyReviveProgress = Math.max(0, state.allyReviveProgress - 2);
			}
		}

		// 倒地持续粒子效果（每2秒）
		downedParticleCounter++;
		if (downedParticleCounter >= DOWNED_PARTICLE_INTERVAL) {
			downedParticleCounter = 0;
			for (UUID playerId : DOWNED_PLAYERS.keySet()) {
				ServerPlayer player = server.getPlayerList().getPlayer(playerId);
				if (player != null && BattlefieldDimension.isInBattlefield(player)) {
					spawnDownedIdleParticles(player);
				}
			}
		}

		// 每tick清除以倒地玩家为目标的怪物仇恨
		clearMobTargetsForDownedPlayers(server);
	}

	/**
	 * 清除所有以倒地玩家为目标的怪物仇恨
	 */
	private static void clearMobTargetsForDownedPlayers(MinecraftServer server) {
		if (DOWNED_PLAYERS.isEmpty()) return;

		ServerLevel battlefield = BattlefieldDimension.getBattlefieldLevel(server);
		if (battlefield == null) return;

		for (var entity : battlefield.getAllEntities()) {
			if (entity instanceof Mob mob) {
				LivingEntity target = mob.getTarget();
				if (target instanceof ServerPlayer p && isDowned(p.getUUID())) {
					mob.setTarget(null);
				}
			}
		}
	}

	/**
	 * 获取倒地玩家数量
	 */
	public static int getDownedCount() {
		return DOWNED_PLAYERS.size();
	}

	/**
	 * 获取指定玩家的复活进度（0-1，取自复活和队友救助的最大值）
	 */
	public static double getReviveProgress(UUID playerId) {
		DownedState state = DOWNED_PLAYERS.get(playerId);
		if (state == null) return 0;
		double selfProgress = (double) state.reviveProgress / REVIVE_HOLD_TICKS;
		double allyProgress = (double) state.allyReviveProgress / REVIVE_HOLD_TICKS;
		return Math.max(selfProgress, allyProgress);
	}

	/**
	 * 检查所有玩家是否都倒地了
	 * v16: 单人模式给予30秒自救窗口
	 */
	public static boolean allPlayersDowned(MinecraftServer server) {
		int battlefieldPlayers = 0;
		int downedPlayers = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (BattlefieldDimension.isInBattlefield(player)) {
				battlefieldPlayers++;
				if (isDowned(player.getUUID()) || !player.isAlive()) {
					downedPlayers++;
				}
			}
		}
		if (battlefieldPlayers == 0) return false;

		// v16: 单人模式倒地后给予30秒自救窗口
		if (battlefieldPlayers == 1 && downedPlayers == 1) {
			DownedState state = DOWNED_PLAYERS.values().iterator().next();
			if (state != null && state.downedTime >= 0) {
				long elapsed = server.overworld().getGameTime() - state.downedTime;
				if (elapsed < com.randomsurprise.config.BalanceConfig.SOLO_DOWNED_GRACE_TICKS) {
					return false; // 自救窗口内不算全灭
				}
			}
		}

		return downedPlayers == battlefieldPlayers && downedPlayers > 0;
	}

	/**
	 * 战场结束时清理所有倒地状态
	 */
	public static void clearAll(MinecraftServer server) {
		for (UUID playerId : DOWNED_PLAYERS.keySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player != null) {
				removeDownedEffects(player);
			}
		}
		DOWNED_PLAYERS.clear();
	}
}
