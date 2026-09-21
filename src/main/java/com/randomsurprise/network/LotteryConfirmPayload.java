package com.randomsurprise.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client->Server: 抽奖确认数据包
 * <p>
 * 配合「词条延迟生效」机制：服务端 serverRoll 只抽奖不应用，客户端播放完动画后
 * 通过本包通知服务端真正应用词条（addAffix → applyGoodEffects → 广播 → 同步）。
 * <p>
 * 字段：
 * <ul>
 *   <li>tenFold —— 是否十连抽</li>
 *   <li>affixIds —— 抽中的词条 id 列表（单抽为 1 个元素）。服务端会优先使用
 *       自己暂存的结果，客户端回传仅作兜底，防止作弊篡改。</li>
 * </ul>
 */
public class LotteryConfirmPayload {
	private final boolean tenFold;
	private final List<String> affixIds;

	public LotteryConfirmPayload(boolean tenFold, List<String> affixIds) {
		this.tenFold = tenFold;
		this.affixIds = affixIds;
	}

	public LotteryConfirmPayload(FriendlyByteBuf buf) {
		this.tenFold = buf.readBoolean();
		int size = buf.readInt();
		this.affixIds = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			this.affixIds.add(buf.readUtf(64));
		}
	}

	public boolean isTenFold() {
		return tenFold;
	}

	public List<String> getAffixIds() {
		return affixIds;
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeBoolean(tenFold);
		buf.writeInt(affixIds.size());
		for (String id : affixIds) {
			buf.writeUtf(id);
		}
	}

	public static LotteryConfirmPayload decode(FriendlyByteBuf buf) {
		return new LotteryConfirmPayload(buf);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			var player = ctx.get().getSender();
			if (player != null) {
				com.randomsurprise.affix.LotteryTicketItem.serverApply(player, tenFold, affixIds);
			}
		});
		ctx.get().setPacketHandled(true);
	}
}
