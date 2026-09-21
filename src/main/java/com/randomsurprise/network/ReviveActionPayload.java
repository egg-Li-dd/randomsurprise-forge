package com.randomsurprise.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 复活动作网络包
 * - targetPlayerId == 自己 → 自复活（倒地玩家按住右键）
 * - targetPlayerId != 自己 → 队友救助（对准倒地玩家按住右键）
 */
public class ReviveActionPayload {
	private final UUID targetPlayerId;
	private final boolean start;

	public ReviveActionPayload(UUID targetPlayerId, boolean start) {
		this.targetPlayerId = targetPlayerId;
		this.start = start;
	}

	public ReviveActionPayload(FriendlyByteBuf buf) {
		this.targetPlayerId = buf.readUUID();
		this.start = buf.readBoolean();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeUUID(targetPlayerId);
		buf.writeBoolean(start);
	}

	public static ReviveActionPayload decode(FriendlyByteBuf buf) {
		return new ReviveActionPayload(buf);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			var player = ctx.get().getSender();
			if (player == null) return;

			if (targetPlayerId.equals(player.getUUID())) {
				// 自救已取消：倒地后只能由队友靠近救助，忽略自救请求
				return;
			}

			// 队友救助：设置对目标的救助状态（服务端会验证距离/战场/倒地等条件）
			com.randomsurprise.battlefield.DownedStateManager.setAllyReviving(
					targetPlayerId, player.getUUID(), start);
		});
		ctx.get().setPacketHandled(true);
	}
}
