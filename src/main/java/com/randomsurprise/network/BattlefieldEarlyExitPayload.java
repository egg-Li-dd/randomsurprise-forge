package com.randomsurprise.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S 数据包：玩家请求提前退出胜利停留阶段（长按空格5秒后发送）
 */
public class BattlefieldEarlyExitPayload {

	public BattlefieldEarlyExitPayload() {}

	public static void encode(BattlefieldEarlyExitPayload msg, FriendlyByteBuf buf) {}

	public static BattlefieldEarlyExitPayload decode(FriendlyByteBuf buf) {
		return new BattlefieldEarlyExitPayload();
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			ServerPlayer player = ctx.get().getSender();
			if (player == null) return;
			com.randomsurprise.battlefield.BattlefieldManager.requestEarlyExit(player);
		});
		ctx.get().setPacketHandled(true);
	}
}
