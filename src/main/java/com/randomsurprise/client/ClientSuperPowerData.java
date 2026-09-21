package com.randomsurprise.client;

import com.randomsurprise.superpower.SuperPower;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 客户端超能力数据缓存
 * 由服务端通过 SuperPowerSyncPayload 同步，HUD 和按键处理读取此数据
 * 
 * 关键修复：添加玩家UUID验证，确保超能力数据只对拥有者生效
 */
public class ClientSuperPowerData {
	/** 玩家的超能力（null = 未拥有） */
	private static SuperPower superPower = null;
	/** 被动技能开关状态 */
	private static boolean passiveEnabled = true;
	/** 主动技能冷却剩余秒数 */
	private static int cooldownSeconds = 0;
	/** 临时状态剩余 ticks（飞行/毒素清除） */
	private static int stateTicks = 0;
	/** 状态标记列表（flying/cleansing） */
	private static List<String> stateFlags = new ArrayList<>();

	/** 本地二段跳状态（仅客户端，跑酷达人用） */
	private static boolean doubleJumpUsed = false;
	/** 本地：上次跳跃键状态（用于检测按下事件） */
	private static boolean jumpKeyWasDown = false;

	/**
	 * 更新超能力数据（带玩家UUID验证）
	 * 只有当数据属于当前玩家时才更新，防止多玩家环境下数据混淆
	 */
	public static void update(UUID playerUUID, String superPowerId, boolean passiveEnabled, int cooldownSeconds,
			int stateTicks, List<String> stateFlags) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			superPower = null;
			return;
		}
		
		UUID currentPlayerUUID = mc.player.getUUID();
		if (!currentPlayerUUID.equals(playerUUID)) {
			return;
		}
		
		superPower = superPowerId != null ? SuperPower.getById(superPowerId) : null;
		ClientSuperPowerData.passiveEnabled = passiveEnabled;
		ClientSuperPowerData.cooldownSeconds = cooldownSeconds;
		ClientSuperPowerData.stateTicks = stateTicks;
		ClientSuperPowerData.stateFlags = new ArrayList<>(stateFlags);
	}

	public static SuperPower getSuperPower() { return superPower; }
	public static boolean isPassiveEnabled() { return passiveEnabled; }
	public static int getCooldownSeconds() { return cooldownSeconds; }
	public static int getStateTicks() { return stateTicks; }
	public static List<String> getStateFlags() { return stateFlags; }

	public static boolean isFlying() { return stateFlags.contains("flying"); }
	public static boolean isCleansing() { return stateFlags.contains("cleansing"); }

	// 二段跳状态（仅客户端）
	public static boolean isDoubleJumpUsed() { return doubleJumpUsed; }
	public static void setDoubleJumpUsed(boolean used) { doubleJumpUsed = used; }
	public static boolean wasJumpKeyDown() { return jumpKeyWasDown; }
	public static void setJumpKeyWasDown(boolean down) { jumpKeyWasDown = down; }

	/** 是否拥有可主动触发的超能力 */
	public static boolean hasActivePower() {
		return superPower != null && superPower.hasActiveAction();
	}

	/** 是否拥有可开关的被动超能力 */
	public static boolean hasToggleablePower() {
		return superPower != null && superPower.isToggleable();
	}
}
