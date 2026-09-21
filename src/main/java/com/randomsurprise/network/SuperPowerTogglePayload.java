package com.randomsurprise.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Client->Server: superpower passive toggle (V key) */
public class SuperPowerTogglePayload {
public SuperPowerTogglePayload() {}
public SuperPowerTogglePayload(FriendlyByteBuf buf) {}
public void encode(FriendlyByteBuf buf) {}
public static SuperPowerTogglePayload decode(FriendlyByteBuf buf) { return new SuperPowerTogglePayload(); }
public void handle(Supplier<NetworkEvent.Context> ctx) {
ctx.get().enqueueWork(() -> {
var player = ctx.get().getSender();
if (player != null) com.randomsurprise.superpower.SuperPowerHandler.onToggleKeyPressed(player);
});
ctx.get().setPacketHandled(true);
}
}