package com.randomsurprise.network;

import com.randomsurprise.affix.LotteryTicketItem;
import com.randomsurprise.affix.ModBagItem;
import com.randomsurprise.affix.ModItems;
import com.randomsurprise.menu.ModBagMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client->Server: 模组袋快捷使用请求
 * actionType: 0=使用净化药水, 1=使用清除药水, 2=使用抽奖券
 *
 * 实现方式：从袋子 NBT 取出对应物品 1 个，临时放入玩家空闲手，
 * 调用原始物品的 use / serverRoll 逻辑，结束后归还剩余。
 */
public class ModBagActionPayload {
	public static final int ACTION_PURIFY = 0;
	public static final int ACTION_CLEAR = 1;
	public static final int ACTION_LOTTERY = 2;

	private final int actionType;

	public ModBagActionPayload(int actionType) {
		this.actionType = actionType;
	}

	public ModBagActionPayload(FriendlyByteBuf buf) {
		this.actionType = buf.readVarInt();
	}

	public void encode(FriendlyByteBuf buf) {
		buf.writeVarInt(actionType);
	}

	public static ModBagActionPayload decode(FriendlyByteBuf buf) {
		return new ModBagActionPayload(buf);
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			ServerPlayer player = ctx.get().getSender();
			if (player == null) return;

			// 若模组袋菜单仍开启，先关闭以确保容器最新状态写入 NBT
			if (player.containerMenu instanceof ModBagMenu) {
				player.closeContainer();
			}

			ItemStack bag = findBag(player);
			if (bag.isEmpty()) return;

			switch (actionType) {
				case ACTION_PURIFY -> handlePurify(player, bag);
				case ACTION_CLEAR -> handleClear(player, bag);
				case ACTION_LOTTERY -> handleLottery(player, bag);
			}

			player.inventoryMenu.broadcastChanges();
		});
		ctx.get().setPacketHandled(true);
	}

	/** 在主/副手查找模组袋 */
	private ItemStack findBag(ServerPlayer player) {
		ItemStack main = player.getMainHandItem();
		if (main.getItem() instanceof ModBagItem) return main;
		ItemStack off = player.getOffhandItem();
		if (off.getItem() instanceof ModBagItem) return off;
		return ItemStack.EMPTY;
	}

	/** 返回未持有模组袋的那只手，用于临时放置要使用的物品 */
	private InteractionHand freeHand(ServerPlayer player) {
		if (player.getMainHandItem().getItem() instanceof ModBagItem) {
			return InteractionHand.OFF_HAND;
		}
		return InteractionHand.MAIN_HAND;
	}

	/** 在袋子容器中查找指定物品槽位 */
	private int findSlot(SimpleContainer container, Item item) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).is(item)) return i;
		}
		return -1;
	}

	/** 使用净化药水 */
	private void handlePurify(ServerPlayer player, ItemStack bag) {
		SimpleContainer container = ModBagItem.loadContainer(bag);
		int slot = findSlot(container, ModItems.PURIFY_POTION.get());
		if (slot < 0) {
			player.sendSystemMessage(Component.translatable("modbag.randomsurprise.no_item"));
			return;
		}
		ItemStack potion = container.removeItem(slot, 1);
		useTempItem(player, potion, () -> ModItems.PURIFY_POTION.get().use(player.level(), player, freeHand(player)));
		if (!potion.isEmpty()) {
			container.addItem(potion);
		}
		ModBagItem.saveContainer(bag, container);
	}

	/** 使用清除药水 */
	private void handleClear(ServerPlayer player, ItemStack bag) {
		SimpleContainer container = ModBagItem.loadContainer(bag);
		int slot = findSlot(container, ModItems.MOB_CLEAR_POTION.get());
		if (slot < 0) {
			player.sendSystemMessage(Component.translatable("modbag.randomsurprise.no_item"));
			return;
		}
		ItemStack potion = container.removeItem(slot, 1);
		useTempItem(player, potion, () -> ModItems.MOB_CLEAR_POTION.get().use(player.level(), player, freeHand(player)));
		if (!potion.isEmpty()) {
			container.addItem(potion);
		}
		ModBagItem.saveContainer(bag, container);
	}

	/** 使用抽奖券（单抽） */
	private void handleLottery(ServerPlayer player, ItemStack bag) {
		SimpleContainer container = ModBagItem.loadContainer(bag);
		int slot = findSlot(container, ModItems.LOTTERY_TICKET.get());
		if (slot < 0) {
			player.sendSystemMessage(Component.translatable("modbag.randomsurprise.no_item"));
			return;
		}
		ItemStack ticket = container.removeItem(slot, 1);
		useTempItem(player, ticket, () -> LotteryTicketItem.serverRoll(player, false));
		if (!ticket.isEmpty()) {
			container.addItem(ticket);
		}
		ModBagItem.saveContainer(bag, container);
	}

	/**
	 * 临时把物品放入玩家空闲手，执行动作，再恢复原手持物品。
	 * 执行后若物品未被消耗（创造模式或失败），由调用方归还到袋子。
	 */
	private void useTempItem(ServerPlayer player, ItemStack tempItem, Runnable action) {
		InteractionHand hand = freeHand(player);
		ItemStack saved = player.getItemInHand(hand);
		player.setItemInHand(hand, tempItem);
		try {
			action.run();
		} finally {
			player.setItemInHand(hand, saved);
		}
	}
}
