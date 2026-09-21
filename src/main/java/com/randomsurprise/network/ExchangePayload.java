package com.randomsurprise.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Client->Server: 稀有物品兑换请求（itemId + amount） */
public class ExchangePayload {
	private final String entryId;
	private final int amount;

	public ExchangePayload(String entryId, int amount) {
		this.entryId = entryId;
		this.amount = Math.max(1, amount);
	}

	public ExchangePayload(FriendlyByteBuf buf) {
		this.entryId = buf.readUtf(128);
		this.amount = buf.readVarInt();
	}

	public String getEntryId() { return entryId; }
	public int getAmount() { return amount; }

	public void encode(FriendlyByteBuf buf) {
		buf.writeUtf(entryId);
		buf.writeVarInt(amount);
	}

	public static ExchangePayload decode(FriendlyByteBuf buf) {
		return new ExchangePayload(buf);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			var player = ctx.get().getSender();
			if (player != null) {
				var result = com.randomsurprise.shop.ShopManager.processExchange(player, entryId, amount);
				com.randomsurprise.network.ModNetworking.sendToClient(player,
						new ShopPurchaseResultPayload(result.success(), result.messageKey(), result.args()));
			}
		});
		ctx.get().setPacketHandled(true);
	}
}
