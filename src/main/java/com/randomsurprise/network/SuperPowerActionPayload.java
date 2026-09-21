package com.randomsurprise.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Client->Server: superpower active skill trigger (G key) */
public class SuperPowerActionPayload {
public SuperPowerActionPayload() {}
public SuperPowerActionPayload(FriendlyByteBuf buf) {}
public void encode(FriendlyByteBuf buf) {}
public static SuperPowerActionPayload decode(FriendlyByteBuf buf) { return new SuperPowerActionPayload(); }
public void handle(Supplier<NetworkEvent.Context> ctx) {
ctx.get().enqueueWork(() -> {
var player = ctx.get().getSender();
if (player != null) com.randomsurprise.superpower.SuperPowerHandler.onActiveKeyPressed(player);
});
ctx.get().setPacketHandled(true);
}
}