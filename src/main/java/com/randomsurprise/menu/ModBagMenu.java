package com.randomsurprise.menu;

import com.randomsurprise.RandomSurpriseMod;
import com.randomsurprise.affix.ModBagItem;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 模组袋容器菜单：9 格物品存储 + 玩家背包。
 * 顶部 9 格为袋子内容物，下方为玩家背包与快捷栏。
 */
public class ModBagMenu extends AbstractContainerMenu {

	/** 菜单类型注册表 */
	public static final DeferredRegister<MenuType<?>> MENUS =
			DeferredRegister.create(ForgeRegistries.MENU_TYPES, RandomSurpriseMod.MOD_ID);

	public static final RegistryObject<MenuType<ModBagMenu>> TYPE = MENUS.register("mod_bag",
			() -> IForgeMenuType.create((windowId, inv, data) -> new ModBagMenu(windowId, inv)));

	private final SimpleContainer bagContainer;

	/** 客户端构造器（由 MenuType 调用，空容器随后由服务端同步） */
	public ModBagMenu(int containerId, Inventory playerInv) {
		this(containerId, playerInv, new SimpleContainer(9));
	}

	/** 服务端构造器（使用袋子真实容器） */
	public ModBagMenu(int containerId, Inventory playerInv, SimpleContainer bagContainer) {
		super(TYPE.get(), containerId);
		this.bagContainer = bagContainer;
		checkContainerSize(bagContainer, 9);

		// 袋子 9 格（1 行），位于 y=20。禁止放入模组袋本身以防递归嵌套。
		for (int i = 0; i < 9; i++) {
			addSlot(new Slot(bagContainer, i, 8 + i * 18, 20) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return !(stack.getItem() instanceof ModBagItem);
				}
			});
		}

		// 玩家背包 3x9，位于 y=44
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 44 + row * 18));
			}
		}

		// 玩家快捷栏 9 格，位于 y=102
		for (int col = 0; col < 9; col++) {
			addSlot(new Slot(playerInv, col, 8 + col * 18, 102));
		}
	}

	public SimpleContainer getBagContainer() {
		return bagContainer;
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack original = slot.getItem();
		ItemStack copy = original.copy();
		if (index < 9) {
			// 袋子 -> 玩家背包
			if (!this.moveItemStackTo(original, 9, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else {
			// 玩家背包 -> 袋子
			if (!this.moveItemStackTo(original, 0, 9, false)) {
				return ItemStack.EMPTY;
			}
		}
		if (original.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return copy;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	/** 在主模组构造时注册菜单类型到事件总线 */
	public static void register(IEventBus bus) {
		MENUS.register(bus);
	}
}
