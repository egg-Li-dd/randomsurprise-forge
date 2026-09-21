package com.randomsurprise.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server->Client: 稀有回收物品列表同步
 * 服务端 ExchangeRegistry 扫描完成后，将可回收物品列表发送给客户端
 * 客户端缓存后供 ShopScreen 读取，解决"稀有回收仅房主可见"问题
 */
public class ExchangeListSyncPayload {
	/** 单个兑换条目数据 */
	public static class EntryData {
		public final String entryId;
		public final String itemIdString;
		public final int cost;
		public final int soldAmount;

		public EntryData(String entryId, String itemIdString, int cost, int soldAmount) {
			this.entryId = entryId;
			this.itemIdString = itemIdString;
			this.cost = cost;
			this.soldAmount = soldAmount;
		}

		public EntryData(FriendlyByteBuf buf) {
			this.entryId = buf.readUtf(128);
			this.itemIdString = buf.readUtf(128);
			this.cost = buf.readVarInt();
			this.soldAmount = buf.readVarInt();
		}

		public void encode(FriendlyByteBuf buf) {
			buf.writeUtf(entryId, 128);
			buf.writeUtf(itemIdString, 128);
			buf.writeVarInt(cost);
			buf.writeVarInt(soldAmount);
		}
	}

	private final List<EntryData> entries;

	public ExchangeListSyncPayload(List<EntryData> entries) {
		this.entries = entries;
	}

	public ExchangeListSyncPayload(FriendlyByteBuf buf) {
		int size = buf.readVarInt();
		this.entries = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			entries.add(new EntryData(buf));
		}
	}

	public List<EntryData> getEntries() { return entries; }

	public void encode(FriendlyByteBuf buf) {
		buf.writeVarInt(entries.size());
		for (EntryData e : entries) {
			e.encode(buf);
		}
	}

	public static ExchangeListSyncPayload decode(FriendlyByteBuf buf) {
		return new ExchangeListSyncPayload(buf);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			// 客户端收到数据后，更新 ExchangeRegistry 的客户端缓存
			com.randomsurprise.shop.ExchangeRegistry.updateClientCache(entries);
		});
		ctx.get().setPacketHandled(true);
	}
}
