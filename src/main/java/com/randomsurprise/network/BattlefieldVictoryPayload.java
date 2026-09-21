package com.randomsurprise.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C 数据包：通知客户端进入/退出胜利停留阶段
 * isVictory=true 时 remainingSeconds 为倒计时秒数
 * isVictory=false 时表示玩家已退出胜利阶段
 */
public class BattlefieldVictoryPayload {
	private final boolean isVictory;
	private final int remainingSeconds;

	public BattlefieldVictoryPayload(boolean isVictory, int remainingSeconds) {
		this.isVictory = isVictory;
		this.remainingSeconds = remainingSeconds;
	}

	public static void encode(BattlefieldVictoryPayload msg, FriendlyByteBuf buf) {
		buf.writeBoolean(msg.isVictory);
		buf.writeVarInt(msg.remainingSeconds);
	}

	public static BattlefieldVictoryPayload decode(FriendlyByteBuf buf) {
		return new BattlefieldVictoryPayload(buf.readBoolean(), buf.readVarInt());
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			if (isVictory) {
				com.randomsurprise.client.BattlefieldHud.setVictoryPhase(true, remainingSeconds);
			} else {
				com.randomsurprise.client.BattlefieldHud.setVictoryPhase(false, 0);
			}
		});
		ctx.get().setPacketHandled(true);
	}
}
