package com.randomsurprise.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Server->Client: superpower roll result */
public class SuperPowerRollPayload {
private final String superPowerId;
public SuperPowerRollPayload(String superPowerId) { this.superPowerId = superPowerId; }
public SuperPowerRollPayload(FriendlyByteBuf buf) { this.superPowerId = buf.readUtf(64); }
public String getSuperPowerId() { return superPowerId; }
public void encode(FriendlyByteBuf buf) { buf.writeUtf(superPowerId, 64); }
public static SuperPowerRollPayload decode(FriendlyByteBuf buf) { return new SuperPowerRollPayload(buf); }
public void handle(Supplier<NetworkEvent.Context> ctx) {
ctx.get().enqueueWork(() -> {
var mc = net.minecraft.client.Minecraft.getInstance();
if (mc.player != null) mc.setScreen(new com.randomsurprise.client.SuperPowerLotteryScreen(superPowerId));
});
ctx.get().setPacketHandled(true);
}
}