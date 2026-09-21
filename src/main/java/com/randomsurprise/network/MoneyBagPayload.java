package com.randomsurprise.network;

import com.randomsurprise.affix.MoneyBagItem;
import com.randomsurprise.affix.ModItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client->Server: 钱袋子操作请求
 * action: 0=存入, 1=取出, 2=存入全部, 3=取出全部
 */
public class MoneyBagPayload {
	public static final int ACTION_DEPOSIT = 0;
	public static final int ACTION_WITHDRAW = 1;
	public static final int ACTION_DEPOSIT_ALL = 2;
	public static final int ACTION_WITHDRAW_ALL = 3;

	private final int action;
	private final int amount;
	private final int hand; // 0=main hand, 1=off hand

	public MoneyBagPayload(int action, int amount, int hand) {
		this.action = action;
		this.amount = amount;
		this.hand = hand;
	}

	public MoneyBagPayload(FriendlyByteBuf buf) {
		this.action = buf.readVarInt();
		this.amount = buf.readVarInt();
		this.hand = buf.readVarInt();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeVarInt(action);
		buf.writeVarInt(amount);
		buf.writeVarInt(hand);
	}

	public static MoneyBagPayload decode(FriendlyByteBuf buf) {
		return new MoneyBagPayload(buf);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			ServerPlayer player = ctx.get().getSender();
			if (player == null) return;

			InteractionHand usedHand = hand == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			ItemStack bagStack = player.getItemInHand(usedHand);

			if (!(bagStack.getItem() instanceof MoneyBagItem)) return;

			switch (action) {
				case ACTION_DEPOSIT -> handleDeposit(player, bagStack, amount);
				case ACTION_WITHDRAW -> handleWithdraw(player, bagStack, amount);
				case ACTION_DEPOSIT_ALL -> handleDepositAll(player, bagStack);
				case ACTION_WITHDRAW_ALL -> handleWithdrawAll(player, bagStack);
			}

			bagStack.getOrCreateTag().putInt("coin_value", MoneyBagItem.getCoinValue(bagStack));
		});
		ctx.get().setPacketHandled(true);
	}

	/** 存入指定数量金币 */
	private void handleDeposit(ServerPlayer player, ItemStack bag, int amount) {
		if (amount <= 0) return;
		int coinsInInv = countCoinsInInventory(player);
		int actual = Math.min(amount, coinsInInv);
		if (actual <= 0) {
			player.sendSystemMessage(Component.translatable("moneybag.randomsurprise.no_coins"));
			return;
		}
		removeCoinsFromInventory(player, actual);
		MoneyBagItem.addCoins(bag, actual);
		player.sendSystemMessage(Component.translatable("moneybag.randomsurprise.deposited", actual));
		player.inventoryMenu.broadcastChanges();
	}

	/** 取出指定数量金币 */
	private void handleWithdraw(ServerPlayer player, ItemStack bag, int amount) {
		if (amount <= 0) return;
		int bagCoins = MoneyBagItem.getCoinValue(bag);
		int actual = Math.min(amount, bagCoins);
		if (actual <= 0) {
			player.sendSystemMessage(Component.translatable("moneybag.randomsurprise.bag_empty"));
			return;
		}
		MoneyBagItem.removeCoins(bag, actual);
		addCoinsToInventory(player, actual);
		player.sendSystemMessage(Component.translatable("moneybag.randomsurprise.withdrew", actual));
		player.inventoryMenu.broadcastChanges();
	}

	/** 存入全部 */
	private void handleDepositAll(ServerPlayer player, ItemStack bag) {
		int coinsInInv = countCoinsInInventory(player);
		if (coinsInInv <= 0) {
			player.sendSystemMessage(Component.translatable("moneybag.randomsurprise.no_coins"));
			return;
		}
		removeCoinsFromInventory(player, coinsInInv);
		MoneyBagItem.addCoins(bag, coinsInInv);
		player.sendSystemMessage(Component.translatable("moneybag.randomsurprise.deposited", coinsInInv));
		player.inventoryMenu.broadcastChanges();
	}

	/** 取出全部 */
	private void handleWithdrawAll(ServerPlayer player, ItemStack bag) {
		int bagCoins = MoneyBagItem.getCoinValue(bag);
		if (bagCoins <= 0) {
			player.sendSystemMessage(Component.translatable("moneybag.randomsurprise.bag_empty"));
			return;
		}
		MoneyBagItem.removeCoins(bag, bagCoins);
		addCoinsToInventory(player, bagCoins);
		player.sendSystemMessage(Component.translatable("moneybag.randomsurprise.withdrew", bagCoins));
		player.inventoryMenu.broadcastChanges();
	}

	private int countCoinsInInventory(ServerPlayer player) {
		int count = 0;
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(ModItems.UNIVERSAL_COIN.get())) count += s.getCount();
		}
		return count;
	}

	private void removeCoinsFromInventory(ServerPlayer player, int amount) {
		Inventory inv = player.getInventory();
		int remaining = amount;
		for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(ModItems.UNIVERSAL_COIN.get())) {
				int shrink = Math.min(s.getCount(), remaining);
				s.shrink(shrink);
				remaining -= shrink;
				if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
			}
		}
		inv.setChanged();
	}

	private void addCoinsToInventory(ServerPlayer player, int amount) {
		ItemStack coinStack = new ItemStack(ModItems.UNIVERSAL_COIN.get(), amount);
		if (!player.getInventory().add(coinStack)) {
			player.drop(coinStack, false);
		}
		player.getInventory().setChanged();
	}
}
