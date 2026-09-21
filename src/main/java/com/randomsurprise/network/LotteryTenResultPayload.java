package com.randomsurprise.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server->Client: ten-fold lottery result with 10 affixIds */
public class LotteryTenResultPayload {
private final List<String> affixIds;
public LotteryTenResultPayload(List<String> affixIds) { this.affixIds = affixIds; }
public LotteryTenResultPayload(FriendlyByteBuf buf) {
	int size = buf.readInt();
	this.affixIds = new ArrayList<>(size);
	for (int i = 0; i < size; i++) {
		affixIds.add(buf.readUtf(64));
	}
}
public List<String> getAffixIds() { return affixIds; }
public void encode(FriendlyByteBuf buf) {
	buf.writeInt(affixIds.size());
	for (String id : affixIds) {
		buf.writeUtf(id);
	}
}
public static LotteryTenResultPayload decode(FriendlyByteBuf buf) { return new LotteryTenResultPayload(buf); }
public void handle(Supplier<NetworkEvent.Context> ctx) {
ctx.get().enqueueWork(() -> {
	var mc = net.minecraft.client.Minecraft.getInstance();
	if (mc.player != null) mc.setScreen(new com.randomsurprise.client.LotteryTenScreen(affixIds));
});
ctx.get().setPacketHandled(true);
}
}
