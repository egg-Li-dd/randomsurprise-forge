package com.randomsurprise.battlefield;

/**
 * 征召战场系统全局状态机
 *
 * 状态流转：
 *   NORMAL → (20次事件) → BATTLEFIELD_PREP → BATTLEFIELD_ACTIVE → 结算
 *   结算(成功) → NORMAL（计数+1）
 *   结算(失败) → consecutiveFailures++ → 若达4 → DOOMSDAY_COUNTDOWN，否则 NORMAL
 *   DOOMSDAY_COUNTDOWN → (5分钟+征召成功) → DOOMSDAY_DANGER
 *   DOOMSDAY_COUNTDOWN → (死亡超限/征召失败) → WORLD_INVASION
 *   DOOMSDAY_DANGER → (4次征召) → FINAL_BATTLE
 *   DOOMSDAY_DANGER → (任一次失败) → WORLD_INVASION
 *   总进入次数达10 或 末日险境后4次 → FINAL_BATTLE
 *   FINAL_BATTLE(成功) → WORLD_PEACE
 *   FINAL_BATTLE(失败) → WORLD_INVASION
 *   WORLD_INVASION → (代价重置) → NORMAL
 */
public enum GameState {
	/** 常规：随机事件正常进行，计数达20触发征召 */
	NORMAL,
	/** 战场倒计时：10秒准备倒计时，结束后传送 */
	BATTLEFIELD_COUNTDOWN,
	/** 战场准备：记录位置、传送中（过渡态） */
	BATTLEFIELD_PREP,
	/** 战场进行中：玩家在战场维度战斗 */
	BATTLEFIELD_ACTIVE,
	/** 末日倒计时：全维度生物Boss化+方块消除，持续5分钟后自动征召 */
	DOOMSDAY_COUNTDOWN,
	/** 末日险境：增益词条大增，4次征召后进入决战 */
	DOOMSDAY_DANGER,
	/** 征召决战：所有Boss依次入场（分波次，上限1.5×玩家数） */
	FINAL_BATTLE,
	/** 胜利停留：所有Boss清完，玩家可拾取掉落物，30秒后或长按空格5秒退出 */
	BATTLEFIELD_VICTORY,
	/** 随机世界入侵（终局失败）：增益禁用、抽奖停止、负面事件每120s */
	WORLD_INVASION,
	/** 世界平静（终局成功）：坏词条全禁用，世界归于平静 */
	WORLD_PEACE;

	/** 是否处于任意战场进行态（PREP/ACTIVE/FINAL） */
	public boolean isBattlefieldActive() {
		return this == BATTLEFIELD_PREP || this == BATTLEFIELD_ACTIVE || this == FINAL_BATTLE || this == BATTLEFIELD_VICTORY;
	}

	/** 是否处于终局状态（不可逆） */
	public boolean isTerminal() {
		return this == WORLD_PEACE;
	}

	/** 是否处于入侵状态 */
	public boolean isInvasion() {
		return this == WORLD_INVASION;
	}

	/** 是否处于末日系列状态 */
	public boolean isDoomsday() {
		return this == DOOMSDAY_COUNTDOWN || this == DOOMSDAY_DANGER;
	}
}
