package com.randomsurprise.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Client->Server: request lottery roll (single or ten-fold) */
public class LotteryRequestPayload {
private final boolean tenFold;
public LotteryRequestPayload() { this.tenFold = false; }
public LotteryRequestPayload(boolean tenFold) { this.tenFold = tenFold; }
public LotteryRequestPayload(FriendlyByteBuf buf) { this.tenFold = buf.readBoolean(); }
public boolean isTenFold() { return tenFold; }
public void encode(FriendlyByteBuf buf) { buf.writeBoolean(tenFold); }
public static LotteryRequestPayload decode(FriendlyByteBuf buf) { return new LotteryRequestPayload(buf); }
public void handle(Supplier<NetworkEvent.Context> ctx) {
ctx.get().enqueueWork(() -> {
var player = ctx.get().getSender();
if (player != null) com.randomsurprise.affix.LotteryTicketItem.serverRoll(player, tenFold);
});
ctx.get().setPacketHandled(true);
}
}
