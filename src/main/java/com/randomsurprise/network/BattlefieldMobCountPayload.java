package com.randomsurprise.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * Server->Client: 战场剩余怪物数量同步
 * int mobCount: 剩余敌对生物总数
 * int bossCount: 剩余Boss数量
 */
public class BattlefieldMobCountPayload {
	private final int mobCount;
	private final int bossCount;

	public BattlefieldMobCountPayload(int mobCount, int bossCount) {
		this.mobCount = mobCount;
		this.bossCount = bossCount;
	}

	public BattlefieldMobCountPayload(FriendlyByteBuf buf) {
		this.mobCount = buf.readInt();
		this.bossCount = buf.readInt();
	}

	public int getMobCount() { return mobCount; }
	public int getBossCount() { return bossCount; }

	public void encode(FriendlyByteBuf buf) {
		buf.writeInt(mobCount);
		buf.writeInt(bossCount);
	}

	public static BattlefieldMobCountPayload decode(FriendlyByteBuf buf) {
		return new BattlefieldMobCountPayload(buf);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> com.randomsurprise.client.BattlefieldHud.update(mobCount, bossCount));
		ctx.get().setPacketHandled(true);
	}
}
