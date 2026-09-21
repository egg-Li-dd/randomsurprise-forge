package com.randomsurprise.affix;

import com.randomsurprise.menu.ModBagMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 模组袋物品：右键打开容器界面，可存放物品并快捷使用净化/清除药水与抽奖券。
 * 内容物以 SimpleContainer 形式存储于物品 NBT 的 "BagItems" 字段。
 */
public class ModBagItem extends Item {

	/** NBT 中存储内容物的键名 */
	public static final String ITEMS_KEY = "BagItems";

	/** 袋子容量（1 行 9 格） */
	public static final int BAG_SIZE = 9;

	public ModBagItem(Properties properties) {
		super(properties);
	}

	/** 从物品 NBT 读取容器（不绑定监听器），用于服务端读取/操作内容物 */
	public static SimpleContainer loadContainer(ItemStack stack) {
		SimpleContainer container = new SimpleContainer(BAG_SIZE);
		CompoundTag tag = stack.getOrCreateTag();
		if (tag.contains(ITEMS_KEY, Tag.TAG_LIST)) {
			container.fromTag(tag.getList(ITEMS_KEY, Tag.TAG_COMPOUND));
		}
		return container;
	}

	/** 从物品 NBT 读取容器并绑定回写监听器，用于打开菜单时实时持久化 */
	public static SimpleContainer getContainer(ItemStack stack) {
		SimpleContainer container = loadContainer(stack);
		container.addListener(c -> saveContainer(stack, container));
		return container;
	}

	/** 将容器内容物写入物品 NBT */
	public static void saveContainer(ItemStack stack, SimpleContainer container) {
		stack.getOrCreateTag().put(ITEMS_KEY, container.createTag());
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		ItemStack stack = player.getItemInHand(usedHand);
		if (!level.isClientSide()) {
			SimpleContainer container = getContainer(stack);
			player.openMenu(new MenuProvider() {
				@Override
				public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
					return new ModBagMenu(containerId, inventory, container);
				}

				@Override
				public Component getDisplayName() {
					return Component.translatable("modbag.randomsurprise.title");
				}
			});
		}
		return InteractionResultHolder.success(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("modbag.randomsurprise.tooltip"));
		super.appendHoverText(stack, level, tooltip, flag);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}
}
