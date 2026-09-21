package com.randomsurprise.shop;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 商店条目：定义可购买的物品、数量、货币类型和价格
 * 支持原版物品、模组物品（按ID查找）和附魔装备
 */
public class ShopEntry {

	private final String id;
	private final Item soldItem;           // 原版物品（模组物品为null）
	private final String itemIdString;     // 模组物品ID（如 "alexsmobs:shield_of_the_deep"），原版为null
	private final int soldAmount;
	private final CurrencyType currency;
	private final int cost;
	private final String category;
	private final String[] enchantments;   // 附魔列表，格式 "minecraft:sharpness|5"，null表示无附魔
	private final int purchaseLimit;       // 限购次数（0=无限，>0=限购次数）

	/**
	 * 原版物品构造器（向后兼容）
	 */
	public ShopEntry(String id, Item soldItem, int soldAmount,
					 CurrencyType currency, int cost, String category) {
		this(id, soldItem, null, soldAmount, currency, cost, category, null, 0);
	}

	/**
	 * 完整构造器（向后兼容，无限购）
	 */
	public ShopEntry(String id, Item soldItem, String itemIdString, int soldAmount,
					 CurrencyType currency, int cost, String category, String[] enchantments) {
		this(id, soldItem, itemIdString, soldAmount, currency, cost, category, enchantments, 0);
	}

	/**
	 * 完整构造器（含限购）
	 * @param id            条目唯一ID
	 * @param soldItem      原版物品（模组物品传null）
	 * @param itemIdString  模组物品ID字符串（原版传null）
	 * @param soldAmount    售出数量
	 * @param currency      货币类型
	 * @param cost          花费数量
	 * @param category      分类
	 * @param enchantments  附魔列表 ["minecraft:sharpness|5", ...]，null表示无附魔
	 * @param purchaseLimit 限购次数（0=无限）
	 */
	public ShopEntry(String id, Item soldItem, String itemIdString, int soldAmount,
					 CurrencyType currency, int cost, String category, String[] enchantments, int purchaseLimit) {
		this.id = id;
		this.soldItem = soldItem;
		this.itemIdString = itemIdString;
		this.soldAmount = soldAmount;
		this.currency = currency;
		this.cost = cost;
		this.category = category;
		this.enchantments = enchantments;
		this.purchaseLimit = purchaseLimit;
	}

	// 访问器方法（与 record 版本同名，保持兼容）
	public String id() { return id; }
	public Item soldItem() { return soldItem; }
	public int soldAmount() { return soldAmount; }
	public CurrencyType currency() { return currency; }
	public int cost() { return cost; }
	public String category() { return category; }

	public String itemIdString() { return itemIdString; }
	public String[] enchantments() { return enchantments; }
	public int purchaseLimit() { return purchaseLimit; }

	/** 是否为模组物品 */
	public boolean isModded() { return itemIdString != null; }

	/** 是否有附魔 */
	public boolean hasEnchantments() { return enchantments != null && enchantments.length > 0; }

	/** 是否有限购 */
	public boolean hasPurchaseLimit() { return purchaseLimit > 0; }

	/**
	 * 解析实际物品（原版直接返回，模组按ID查找）
	 * @return Item，若模组物品不存在返回null
	 */
	public Item resolveItem() {
		if (soldItem != null) return soldItem;
		if (itemIdString != null) {
			return net.minecraft.core.registries.BuiltInRegistries.ITEM
					.getOptional(new ResourceLocation(itemIdString)).orElse(null);
		}
		return null;
	}

	/**
	 * 创建购买奖励ItemStack（含附魔）
	 * @param registryAccess 注册表访问（从level.registryAccess()获取）
	 * @return 含附魔的ItemStack，物品不存在返回EMPTY
	 */
	public ItemStack createItemStack(RegistryAccess registryAccess) {
		Item item = resolveItem();
		if (item == null) return ItemStack.EMPTY;
		ItemStack stack = new ItemStack(item, soldAmount);
		applyEnchantments(stack, registryAccess);
		return stack;
	}

	/**
	 * 仅创建展示用ItemStack（含附魔，用于GUI渲染）
	 * 与 createItemStack 相同，但数量限制为1（用于图标显示）
	 */
	public ItemStack createDisplayStack(RegistryAccess registryAccess) {
		Item item = resolveItem();
		if (item == null) return ItemStack.EMPTY;
		ItemStack stack = new ItemStack(item, 1);
		applyEnchantments(stack, registryAccess);
		return stack;
	}

	/**
	 * 为ItemStack应用附魔
	 * 附魔格式: "minecraft:sharpness|5"
	 * 对于附魔书（ENCHANTED_BOOK），使用 STORED_ENCHANTMENTS 组件存储
	 * 其他物品使用 ENCHANTMENTS 组件（stack.enchant）
	 */
	private void applyEnchantments(ItemStack stack, RegistryAccess registryAccess) {
		if (!hasEnchantments()) return;
		Registry<Enchantment> enchantmentRegistry = registryAccess.registryOrThrow(Registries.ENCHANTMENT);

		boolean isEnchantedBook = stack.is(Items.ENCHANTED_BOOK);
		Map<Enchantment, Integer> enchantMap = new HashMap<>();

		for (String ench : enchantments) {
			String[] parts = ench.split("\\|");
			if (parts.length != 2) continue;
			String enchId = parts[0];
			int level;
			try {
				level = Integer.parseInt(parts[1]);
			} catch (NumberFormatException e) {
				continue;
			}
			Optional<Holder.Reference<Enchantment>> holderOpt =
					enchantmentRegistry.getHolder(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, new ResourceLocation(enchId)));
			holderOpt.ifPresent(holder -> enchantMap.put(holder.value(), level));
		}

		if (!enchantMap.isEmpty()) {
			if (isEnchantedBook) {
				EnchantmentHelper.setEnchantments(enchantMap, stack);
			} else {
				EnchantmentHelper.setEnchantments(enchantMap, stack);
			}
		}
	}

	/**
	 * 货币类型
	 */
	public enum CurrencyType {
		UNIVERSAL_COIN("金币"),
		EXPERIENCE_LEVEL("经验等级"),
		DIAMOND("钻石"),
		IRON_INGOT("铁锭"),
		GOLD_INGOT("金锭"),
		EMERALD("绿宝石"),
		NETHERITE_INGOT("下界合金锭"),
		COAL("煤炭"),
		REDSTONE("红石"),
		LAPIS_LAZULI("青金石"),
		QUARTZ("石英"),
		LOTTERY_TICKET("抽奖券");

		private final String displayName;

		CurrencyType(String displayName) {
			this.displayName = displayName;
		}

		public String getDisplayName() {
			return displayName;
		}
	}
}
