package com.randomsurprise.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Server->Client: superpower data sync */
public class SuperPowerSyncPayload {
private final UUID playerUUID;
private final String superPowerId;
private final boolean passiveEnabled;
private final int cooldownSeconds;
private final int stateTicks;
private final List<String> stateFlags;
public SuperPowerSyncPayload(UUID playerUUID, String superPowerId, boolean passiveEnabled, int cooldownSeconds, int stateTicks, List<String> stateFlags) {
this.playerUUID = playerUUID;
this.superPowerId = superPowerId;
this.passiveEnabled = passiveEnabled;
this.cooldownSeconds = cooldownSeconds;
this.stateTicks = stateTicks;
this.stateFlags = stateFlags;
}
public SuperPowerSyncPayload(FriendlyByteBuf buf) {
this.playerUUID = buf.readUUID();
String id = buf.readUtf(64);
this.superPowerId = id.isEmpty() ? null : id;
this.passiveEnabled = buf.readBoolean();
this.cooldownSeconds = buf.readVarInt();
this.stateTicks = buf.readVarInt();
int size = buf.readVarInt();
this.stateFlags = new ArrayList<>(size);
for (int i = 0; i < size; i++) this.stateFlags.add(buf.readUtf(64));
}
public UUID getPlayerUUID() { return playerUUID; }
public String getSuperPowerId() { return superPowerId; }
public boolean isPassiveEnabled() { return passiveEnabled; }
public int getCooldownSeconds() { return cooldownSeconds; }
public int getStateTicks() { return stateTicks; }
public List<String> getStateFlags() { return stateFlags; }
public void encode(FriendlyByteBuf buf) {
buf.writeUUID(playerUUID);
buf.writeUtf(superPowerId == null ? "" : superPowerId, 64);
buf.writeBoolean(passiveEnabled);
buf.writeVarInt(cooldownSeconds);
buf.writeVarInt(stateTicks);
buf.writeVarInt(stateFlags.size());
for (String f : stateFlags) buf.writeUtf(f, 64);
}
public static SuperPowerSyncPayload decode(FriendlyByteBuf buf) { return new SuperPowerSyncPayload(buf); }
public void handle(Supplier<NetworkEvent.Context> ctx) {
ctx.get().enqueueWork(() -> com.randomsurprise.client.ClientSuperPowerData.update(
playerUUID, superPowerId, passiveEnabled, cooldownSeconds, stateTicks, new ArrayList<>(stateFlags)));
ctx.get().setPacketHandled(true);
}
}