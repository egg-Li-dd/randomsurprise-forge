package com.randomsurprise.network;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * C2S 数据包：在背包界面将附魔书拖到物品上直接应用附魔
 * 仅影响背包内该特定物品（不影响其他同类物品）
 */
public class EnchantedBookApplyPayload {
	private final int slotIndex;

	public EnchantedBookApplyPayload(int slotIndex) {
		this.slotIndex = slotIndex;
	}

	public static void encode(EnchantedBookApplyPayload msg, FriendlyByteBuf buf) {
		buf.writeInt(msg.slotIndex);
	}

	public static EnchantedBookApplyPayload decode(FriendlyByteBuf buf) {
		return new EnchantedBookApplyPayload(buf.readInt());
	}

	/** 互斥附魔组（与 SuperPowerHandler 保持一致） */
	private static final String[][] EXCLUSIVE_GROUPS = {
		{"minecraft:silk_touch", "minecraft:fortune"},
		{"minecraft:sharpness", "minecraft:smite", "minecraft:bane_of_arthropods"},
		{"minecraft:infinity", "minecraft:mending"},
		{"minecraft:protection", "minecraft:fire_protection", "minecraft:blast_protection", "minecraft:projectile_protection"},
		{"minecraft:depth_strider", "minecraft:frost_walker"},
		{"minecraft:loyalty", "minecraft:riptide", "minecraft:channeling"},
		{"minecraft:multishot", "minecraft:piercing"}
	};

	private static boolean areEnchantmentsExclusive(Enchantment a, Enchantment b) {
		if (a == b) return false;
		Registry<Enchantment> registry = BuiltInRegistries.ENCHANTMENT;
		ResourceLocation idA = registry.getKey(a);
		ResourceLocation idB = registry.getKey(b);
		if (idA == null || idB == null) return false;
		String aStr = idA.toString();
		String bStr = idB.toString();
		for (String[] group : EXCLUSIVE_GROUPS) {
			boolean aInGroup = false, bInGroup = false;
			for (String id : group) {
				if (id.equals(aStr)) aInGroup = true;
				if (id.equals(bStr)) bInGroup = true;
			}
			if (aInGroup && bInGroup) return true;
		}
		return false;
	}

	/** 从附魔书中读取存储的附魔 */
	private static Map<Enchantment, Integer> getBookEnchantments(ItemStack bookStack) {
		Map<Enchantment, Integer> map = new HashMap<>();
		CompoundTag tag = bookStack.getTag();
		if (tag == null) return map;
		ListTag list = tag.getList("StoredEnchantments", 10);
		Registry<Enchantment> registry = BuiltInRegistries.ENCHANTMENT;
		for (int i = 0; i < list.size(); i++) {
			CompoundTag enchTag = list.getCompound(i);
			ResourceLocation id = new ResourceLocation(enchTag.getString("id"));
			int level = enchTag.getShort("lvl");
			Enchantment ench = registry.get(id);
			if (ench != null) {
				map.put(ench, level);
			}
		}
		return map;
	}

	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			ServerPlayer player = ctx.get().getSender();
			if (player == null) return;

			// 获取光标上的物品（必须是附魔书）
			ItemStack cursorStack = player.containerMenu.getCarried();
			if (!cursorStack.is(Items.ENCHANTED_BOOK)) {
				player.sendSystemMessage(Component.translatable("enchantedbook.randomsurprise.not_book"));
				return;
			}

			// 检查槽位是否有效
			if (slotIndex < 0 || slotIndex >= player.containerMenu.slots.size()) return;
			Slot slot = player.containerMenu.getSlot(slotIndex);
			if (slot == null || !slot.hasItem()) {
				player.sendSystemMessage(Component.translatable("enchantedbook.randomsurprise.no_target"));
				return;
			}

			ItemStack targetStack = slot.getItem();
			if (targetStack.isEmpty() || targetStack.is(Items.ENCHANTED_BOOK)) {
				player.sendSystemMessage(Component.translatable("enchantedbook.randomsurprise.no_target"));
				return;
			}

			// 读取附魔书中的附魔
			Map<Enchantment, Integer> bookEnchants = getBookEnchantments(cursorStack);
			if (bookEnchants.isEmpty()) {
				player.sendSystemMessage(Component.translatable("enchantedbook.randomsurprise.no_enchantment"));
				return;
			}

			// 读取目标物品已有的附魔
			Map<Enchantment, Integer> existingEnchants = EnchantmentHelper.getEnchantments(targetStack);

			// 逐个检查并应用附魔
			boolean applied = false;
			for (Map.Entry<Enchantment, Integer> entry : bookEnchants.entrySet()) {
				Enchantment ench = entry.getKey();
				int level = entry.getValue();

				// 检查附魔是否适用于该物品类型
				if (!ench.canEnchant(targetStack)) continue;

				// 检查与已有附魔的兼容性
				boolean compatible = true;
				for (Enchantment existing : existingEnchants.keySet()) {
					if (areEnchantmentsExclusive(ench, existing)) {
						compatible = false;
						break;
					}
				}
				if (!compatible) continue;

				// 如果已有同附魔，取较高等级
				int existingLevel = existingEnchants.getOrDefault(ench, 0);
				if (level > existingLevel) {
					existingEnchants.put(ench, level);
				}
				applied = true;
			}

			if (applied) {
				// 应用附魔到目标物品
				EnchantmentHelper.setEnchantments(existingEnchants, targetStack);

				// 消耗一本附魔书
				cursorStack.shrink(1);
				if (cursorStack.isEmpty()) {
					player.containerMenu.setCarried(ItemStack.EMPTY);
				}

				// 广播变更
				player.containerMenu.broadcastChanges();
				player.inventoryMenu.broadcastChanges();

				player.sendSystemMessage(Component.translatable("enchantedbook.randomsurprise.applied"));
			} else {
				player.sendSystemMessage(Component.translatable("enchantedbook.randomsurprise.no_effect"));
			}
		});
		ctx.get().setPacketHandled(true);
	}

	/** 客户端调用：发送附魔应用请求 */
	public static void send(SimpleChannel channel, int slotIndex) {
		channel.sendToServer(new EnchantedBookApplyPayload(slotIndex));
	}
}
