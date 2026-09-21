package com.randomsurprise.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Client->Server: shop purchase request (with amount) */
public class ShopPurchasePayload {
private final String entryId;
private final int amount;
public ShopPurchasePayload(String entryId, int amount) { this.entryId = entryId; this.amount = Math.max(1, amount); }
public ShopPurchasePayload(String entryId) { this(entryId, 1); }
public ShopPurchasePayload(FriendlyByteBuf buf) { this.entryId = buf.readUtf(64); this.amount = buf.readVarInt(); }
public String getEntryId() { return entryId; }
public int getAmount() { return amount; }
public void encode(FriendlyByteBuf buf) { buf.writeUtf(entryId); buf.writeVarInt(amount); }
public static ShopPurchasePayload decode(FriendlyByteBuf buf) { return new ShopPurchasePayload(buf); }
public void handle(Supplier<NetworkEvent.Context> ctx) {
ctx.get().enqueueWork(() -> {
var player = ctx.get().getSender();
if (player != null) {
var result = com.randomsurprise.shop.ShopManager.processPurchase(player, entryId, amount);
com.randomsurprise.network.ModNetworking.sendToClient(player,
new ShopPurchaseResultPayload(result.success(), result.messageKey(), result.args()));
}
});
ctx.get().setPacketHandled(true);
}
}
