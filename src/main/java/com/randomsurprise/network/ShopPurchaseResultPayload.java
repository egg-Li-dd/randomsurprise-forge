package com.randomsurprise.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server->Client: shop purchase result */
public class ShopPurchaseResultPayload {
private final boolean success;
private final String messageKey;
private final List<String> args;
public ShopPurchaseResultPayload(boolean success, String messageKey, List<String> args) {
this.success = success; this.messageKey = messageKey; this.args = args;
}
public ShopPurchaseResultPayload(FriendlyByteBuf buf) {
this.success = buf.readBoolean();
this.messageKey = buf.readUtf(256);
int size = buf.readVarInt();
this.args = new ArrayList<>(size);
for (int i = 0; i < size; i++) this.args.add(buf.readUtf(256));
}
public boolean isSuccess() { return success; }
public String getMessageKey() { return messageKey; }
public List<String> getArgs() { return args; }
public void encode(FriendlyByteBuf buf) {
buf.writeBoolean(success);
buf.writeUtf(messageKey, 256);
buf.writeVarInt(args.size());
for (String a : args) buf.writeUtf(a, 256);
}
public static ShopPurchaseResultPayload decode(FriendlyByteBuf buf) { return new ShopPurchaseResultPayload(buf); }
public void handle(Supplier<NetworkEvent.Context> ctx) {
ctx.get().enqueueWork(() -> {
var mc = net.minecraft.client.Minecraft.getInstance();
if (mc.screen instanceof com.randomsurprise.client.ShopScreen shopScreen) {
shopScreen.showPurchaseResult(success, messageKey, args);
}
});
ctx.get().setPacketHandled(true);
}
}