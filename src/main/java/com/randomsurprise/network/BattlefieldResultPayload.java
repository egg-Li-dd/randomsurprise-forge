package com.randomsurprise.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * v19: 战场结算画面网络包（S2C）
 * 服务端发送结算信息到客户端，客户端打开结算界面
 */
public class BattlefieldResultPayload {
    private final boolean success;
    private final String rarityName;
    private final int difficulty;
    private final int totalBattles;

    public BattlefieldResultPayload(boolean success, String rarityName, int difficulty, int totalBattles) {
        this.success = success;
        this.rarityName = rarityName;
        this.difficulty = difficulty;
        this.totalBattles = totalBattles;
    }

    public BattlefieldResultPayload(FriendlyByteBuf buf) {
        this.success = buf.readBoolean();
        this.rarityName = buf.readUtf();
        this.difficulty = buf.readInt();
        this.totalBattles = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(success);
        buf.writeUtf(rarityName);
        buf.writeInt(difficulty);
        buf.writeInt(totalBattles);
    }

    public static BattlefieldResultPayload decode(FriendlyByteBuf buf) {
        return new BattlefieldResultPayload(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                mc.setScreen(new com.randomsurprise.client.BattlefieldResultScreen(
                        success, rarityName, difficulty, totalBattles));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public boolean isSuccess() { return success; }
    public String getRarityName() { return rarityName; }
    public int getDifficulty() { return difficulty; }
    public int getTotalBattles() { return totalBattles; }
}
