package com.randomsurprise;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家计时器管理
 * 每个玩家独立的60秒计时，到时触发随机惊喜
 */
public class SurpriseManager {
	private final Map<UUID, Integer> playerTimers = new HashMap<>();
	private int affixRefreshCounter = 0; // 好词条效果刷新计数器（每5秒刷新）
	private int timerSyncCounter = 0;    // 计时器同步计数器（每1秒同步到客户端）
	private int lastSyncedTimer = -1;    // 上次同步的计时器值（脏检查：只在值变化时发送）

	/**
	 * 服务器 tick 回调（每秒20次）
	 */
	public void onServerTick(MinecraftServer server) {
		// 征召战场 tick 分发（独立于随机惊喜开关，确保战场状态机始终推进）
		try {
			com.randomsurprise.battlefield.BattlefieldManager.onServerTick(server);
		} catch (Exception e) {
			RandomSurpriseMod.LOGGER.error("征召战场 tick 失败: {}", e.getMessage());
		}

		// 好词条效果定期刷新（每100tick=5秒，独立于自动惊喜开关）
		affixRefreshCounter++;
		if (affixRefreshCounter >= 100) {
			affixRefreshCounter = 0;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				try {
					com.randomsurprise.affix.PlayerAffixManager.applyGoodEffects(player);
				} catch (Exception e) {
					RandomSurpriseMod.LOGGER.error("刷新好词条效果失败: {}", e.getMessage());
				}
			}
		}

		if (!SurpriseConfig.isEnabled()) return;

		// 入侵态：禁用常规随机惊喜（仅触发负面事件，由 BattlefieldManager.tickWorldInvasion 处理）
		if (com.randomsurprise.battlefield.BattlefieldManager.isNegativeOnly()) return;

		// 战场进行中（PREP/ACTIVE/FINAL_BATTLE）：停止主世界随机事件，回到常规态后恢复
		// 注意：不影响 DOOMSDAY 系列状态，末日倒计时/险境期间随机事件照常进行
		if (com.randomsurprise.battlefield.BattlefieldManager.getState().isBattlefieldActive()) return;

		Iterator<Map.Entry<UUID, Integer>> it = playerTimers.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Integer> entry = it.next();
			UUID id = entry.getKey();
			int timer = entry.getValue() - 1;

			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null || player.hasDisconnected()) {
				it.remove();
				continue;
			}

			if (timer <= 0) {
				// 触发随机惊喜
				try {
					SurpriseActions.executeRandom(player);
					// 通知征召战场系统计数+1（非常规态会自动忽略）
					com.randomsurprise.battlefield.BattlefieldManager.onEventExecuted(server);
				} catch (Exception e) {
					RandomSurpriseMod.LOGGER.error("触发随机惊喜失败: {}", e.getMessage());
				}
				timer = SurpriseConfig.getIntervalTicks();
			}
			entry.setValue(timer);
		}

		// 每秒同步剩余时间到客户端 HUD（脏检查：只在值变化时发送）
		timerSyncCounter++;
		if (timerSyncCounter >= 20) {
			timerSyncCounter = 0;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				int remaining = getRemainingSeconds(player.getUUID());
				if (remaining != lastSyncedTimer) {
					com.randomsurprise.network.ModNetworking.sendTimerSync(player, remaining);
				}
			}
			// 更新上次同步值（用第一个玩家的值作为参考）
			if (!server.getPlayerList().getPlayers().isEmpty()) {
				lastSyncedTimer = getRemainingSeconds(server.getPlayerList().getPlayers().get(0).getUUID());
			}
		}
	}

	/**
	 * 玩家加入服务器
	 */
	public void onPlayerJoin(ServerPlayer player) {
		// 新玩家给予初始计时（从配置的间隔开始）
		playerTimers.put(player.getUUID(), SurpriseConfig.getIntervalTicks());
	}

	/**
	 * 玩家退出服务器
	 */
	public void onPlayerQuit(ServerPlayer player) {
		playerTimers.remove(player.getUUID());
	}

	/**
	 * 立即为所有玩家触发一次惊喜
	 */
	public int triggerNow(MinecraftServer server) {
		int count = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			try {
				SurpriseActions.executeRandom(player);
				// 重置计时器
				playerTimers.put(player.getUUID(), SurpriseConfig.getIntervalTicks());
				count++;
			} catch (Exception e) {
				RandomSurpriseMod.LOGGER.error("手动触发惊喜失败: {}", e.getMessage());
			}
		}
		return count;
	}

	/**
	 * 获取玩家剩余时间（秒）
	 */
	public int getRemainingSeconds(UUID playerId) {
		int ticks = playerTimers.getOrDefault(playerId, SurpriseConfig.getIntervalTicks());
		return ticks / 20;
	}
}
