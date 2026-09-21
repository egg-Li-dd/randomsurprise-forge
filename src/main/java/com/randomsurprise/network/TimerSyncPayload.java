package com.randomsurprise.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Server->Client: timer sync */
public class TimerSyncPayload {
private final int remainingSeconds;
public TimerSyncPayload(int remainingSeconds) { this.remainingSeconds = remainingSeconds; }
public TimerSyncPayload(FriendlyByteBuf buf) { this.remainingSeconds = buf.readVarInt(); }
public int getRemainingSeconds() { return remainingSeconds; }
public void encode(FriendlyByteBuf buf) { buf.writeVarInt(remainingSeconds); }
public static TimerSyncPayload decode(FriendlyByteBuf buf) { return new TimerSyncPayload(buf); }
public void handle(Supplier<NetworkEvent.Context> ctx) {
ctx.get().enqueueWork(() -> com.randomsurprise.client.TimerHud.update(remainingSeconds));
ctx.get().setPacketHandled(true);
}
}