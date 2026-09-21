package com.randomsurprise.affix;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 钱袋子物品 - 用于存储金币
 * - 右键打开UI界面存取金币
 * - NBT存储金币数量（key: "coin_value"）
 * - 不可堆叠（stacksTo(1)）
 * - Shift+右键快速存入背包内所有金币
 */
public class MoneyBagItem extends Item {
	public static final String COIN_VALUE_KEY = "coin_value";

	public MoneyBagItem(Properties properties) {
		super(properties);
	}

	/** 获取钱袋子中存储的金币数量 */
	public static int getCoinValue(ItemStack stack) {
		if (stack.isEmpty() || !(stack.getItem() instanceof MoneyBagItem)) return 0;
		CompoundTag tag = stack.getOrCreateTag();
		return tag.getInt(COIN_VALUE_KEY);
	}

	/** 设置钱袋子中的金币数量 */
	public static void setCoinValue(ItemStack stack, int value) {
		if (stack.isEmpty() || !(stack.getItem() instanceof MoneyBagItem)) return;
		CompoundTag tag = stack.getOrCreateTag();
		tag.putInt(COIN_VALUE_KEY, Math.max(0, value));
	}

	/** 向钱袋子添加金币 */
	public static void addCoins(ItemStack stack, int amount) {
		int current = getCoinValue(stack);
		setCoinValue(stack, current + amount);
	}

	/** 从钱袋子扣除金币，返回实际扣除数量 */
	public static int removeCoins(ItemStack stack, int amount) {
		int current = getCoinValue(stack);
		int removed = Math.min(current, amount);
		setCoinValue(stack, current - removed);
		return removed;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		ItemStack stack = player.getItemInHand(usedHand);

		if (level.isClientSide()) {
			// 客户端打开UI
			com.randomsurprise.client.MoneyBagScreen.open(stack, usedHand);
			return InteractionResultHolder.success(stack);
		}

		// 服务端：Shift+右键快速存入所有金币
		if (player.isShiftKeyDown()) {
			ServerPlayer serverPlayer = (ServerPlayer) player;
			int coinsInInventory = countCoinsInInventory(serverPlayer);
			if (coinsInInventory > 0) {
				addCoins(stack, coinsInInventory);
				removeCoinsFromInventory(serverPlayer, coinsInInventory);
				serverPlayer.sendSystemMessage(Component.translatable(
						"moneybag.randomsurprise.deposited", coinsInInventory));
			} else {
				serverPlayer.sendSystemMessage(Component.translatable(
						"moneybag.randomsurprise.no_coins"));
			}
		}

		return InteractionResultHolder.success(stack);
	}

	/** 统计玩家背包中的金币数量 */
	private static int countCoinsInInventory(Player player) {
		int count = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack slotStack = inventory.getItem(i);
			if (slotStack.is(ModItems.UNIVERSAL_COIN.get())) {
				count += slotStack.getCount();
			}
		}
		return count;
	}

	/** 从玩家背包移除指定数量金币 */
	private static void removeCoinsFromInventory(Player player, int amount) {
		var inventory = player.getInventory();
		int remaining = amount;
		for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
			ItemStack slotStack = inventory.getItem(i);
			if (slotStack.is(ModItems.UNIVERSAL_COIN.get())) {
				int shrink = Math.min(slotStack.getCount(), remaining);
				slotStack.shrink(shrink);
				remaining -= shrink;
				if (slotStack.isEmpty()) {
					inventory.setItem(i, ItemStack.EMPTY);
				}
			}
		}
		inventory.setChanged();
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return getCoinValue(stack) > 0;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		int coins = getCoinValue(stack);
		tooltip.add(Component.translatable("moneybag.randomsurprise.stored", coins));
		tooltip.add(Component.translatable("moneybag.randomsurprise.tooltip"));
		super.appendHoverText(stack, level, tooltip, flag);
	}

	@Override
	public boolean isEnchantable(ItemStack stack) {
		return false;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		int coins = getCoinValue(stack);
		return coins > 0;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		int coins = getCoinValue(stack);
		// 每1000金币显示满条
		return Math.min(13, coins * 13 / 1000);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return 0xFFFFD700; // 金色
	}
}
