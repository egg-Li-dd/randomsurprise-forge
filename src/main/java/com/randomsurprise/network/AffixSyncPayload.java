package com.randomsurprise.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server->Client: affix data sync */
public class AffixSyncPayload {
private final List<String> ownAffixes;
private final List<PlayerBadAffixes> allPlayerBadAffixes;
public AffixSyncPayload(List<String> ownAffixes, List<PlayerBadAffixes> allPlayerBadAffixes) {
this.ownAffixes = ownAffixes; this.allPlayerBadAffixes = allPlayerBadAffixes;
}
public AffixSyncPayload(FriendlyByteBuf buf) {
int ownSize = buf.readVarInt();
this.ownAffixes = new ArrayList<>(ownSize);
for (int i = 0; i < ownSize; i++) this.ownAffixes.add(buf.readUtf(64));
int groupSize = buf.readVarInt();
this.allPlayerBadAffixes = new ArrayList<>(groupSize);
for (int i = 0; i < groupSize; i++) {
String name = buf.readUtf(64);
int affixCount = buf.readVarInt();
List<String> ids = new ArrayList<>(affixCount);
for (int j = 0; j < affixCount; j++) ids.add(buf.readUtf(64));
this.allPlayerBadAffixes.add(new PlayerBadAffixes(name, ids));
}
}
public List<String> getOwnAffixes() { return ownAffixes; }
public List<PlayerBadAffixes> getAllPlayerBadAffixes() { return allPlayerBadAffixes; }
public void encode(FriendlyByteBuf buf) {
buf.writeVarInt(ownAffixes.size());
for (String id : ownAffixes) buf.writeUtf(id, 64);
buf.writeVarInt(allPlayerBadAffixes.size());
for (PlayerBadAffixes pba : allPlayerBadAffixes) {
buf.writeUtf(pba.playerName(), 64);
buf.writeVarInt(pba.affixIds().size());
for (String id : pba.affixIds()) buf.writeUtf(id, 64);
}
}
public static AffixSyncPayload decode(FriendlyByteBuf buf) { return new AffixSyncPayload(buf); }
public void handle(Supplier<NetworkEvent.Context> ctx) {
ctx.get().enqueueWork(() -> {
List<com.randomsurprise.client.ClientAffixData.PlayerBadAffixes> allBad = new ArrayList<>();
for (PlayerBadAffixes pba : allPlayerBadAffixes) {
allBad.add(new com.randomsurprise.client.ClientAffixData.PlayerBadAffixes(pba.playerName(), new ArrayList<>(pba.affixIds())));
}
com.randomsurprise.client.ClientAffixData.update(new ArrayList<>(ownAffixes), allBad);
});
ctx.get().setPacketHandled(true);
}
public record PlayerBadAffixes(String playerName, List<String> affixIds) {}
}