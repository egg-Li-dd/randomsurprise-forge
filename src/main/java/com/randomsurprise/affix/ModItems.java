package com.randomsurprise.affix;

import com.randomsurprise.RandomSurpriseMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 模组物品注册（Forge 1.20.1 版本）
 * 使用 DeferredRegister + RegistryObject 注册物品
 */
public class ModItems {
	public static final DeferredRegister<Item> ITEMS =
			DeferredRegister.create(ForgeRegistries.ITEMS, RandomSurpriseMod.MOD_ID);

	public static final RegistryObject<Item> LOTTERY_TICKET = ITEMS.register("lottery_ticket",
			() -> new LotteryTicketItem(new Item.Properties()
					.rarity(Rarity.UNCOMMON)
					.stacksTo(16)));

	public static final RegistryObject<Item> PURIFY_POTION = ITEMS.register("purify_potion",
			() -> new PurifyPotionItem(new Item.Properties()
					.rarity(Rarity.RARE)
					.stacksTo(4)));

	public static final RegistryObject<Item> GUARDIAN_CHARM = ITEMS.register("guardian_charm",
			() -> new GuardianCharmItem(new Item.Properties()
					.rarity(Rarity.EPIC)
					.stacksTo(1)));

	public static final RegistryObject<Item> MOB_CLEAR_POTION = ITEMS.register("mob_clear_potion",
			() -> new MobClearPotionItem(new Item.Properties()
					.rarity(Rarity.RARE)
					.stacksTo(4)));

	/** 通用金币 - 商店唯一货币，无限堆叠仅占一格 */
	public static final RegistryObject<Item> UNIVERSAL_COIN = ITEMS.register("universal_coin",
			() -> new Item(new Item.Properties()
					.rarity(Rarity.UNCOMMON)
					.stacksTo(Integer.MAX_VALUE)));

	/** 钱袋子 - 可存储金币，右键打开UI存取 */
	public static final RegistryObject<Item> MONEY_BAG = ITEMS.register("money_bag",
			() -> new MoneyBagItem(new Item.Properties()
					.rarity(Rarity.RARE)
					.stacksTo(1)));

	/** 模组袋 - 可存放物品并快捷使用净化/清除药水与抽奖券 */
	public static final RegistryObject<Item> MOD_BAG = ITEMS.register("mod_bag",
			() -> new ModBagItem(new Item.Properties()
					.rarity(Rarity.EPIC)
					.stacksTo(1)));

	public static void register(IEventBus eventBus) {
		ITEMS.register(eventBus);
		RandomSurpriseMod.LOGGER.info("注册模组物品...");
	}
}
