package com.randomsurprise.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Server->Client: lottery result with affixId */
public class LotteryResultPayload {
private final String affixId;
public LotteryResultPayload(String affixId) { this.affixId = affixId; }
public LotteryResultPayload(FriendlyByteBuf buf) { this.affixId = buf.readUtf(64); }
public String getAffixId() { return affixId; }
public void encode(FriendlyByteBuf buf) { buf.writeUtf(affixId); }
public static LotteryResultPayload decode(FriendlyByteBuf buf) { return new LotteryResultPayload(buf); }
public void handle(Supplier<NetworkEvent.Context> ctx) {
ctx.get().enqueueWork(() -> {
var mc = net.minecraft.client.Minecraft.getInstance();
if (mc.player != null) mc.setScreen(new com.randomsurprise.client.LotteryScreen(affixId));
});
ctx.get().setPacketHandled(true);
}
}