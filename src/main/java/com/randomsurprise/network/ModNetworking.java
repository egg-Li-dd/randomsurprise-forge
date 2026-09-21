package com.randomsurprise.network;

import com.randomsurprise.RandomSurpriseMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;

/**
 * 网络通信注册（Forge 1.20.1 版本）
 * 使用 SimpleChannel 替代 Fabric 的 PayloadTypeRegistry
 */
public class ModNetworking {
private static final String PROTOCOL_VERSION = "1";
public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
new ResourceLocation(RandomSurpriseMod.MOD_ID, "main"),
() -> PROTOCOL_VERSION,
PROTOCOL_VERSION::equals,
PROTOCOL_VERSION::equals
);

private static int packetId = 0;

public static void register() {
		// C2S packets (client -> server)
		INSTANCE.registerMessage(packetId++, LotteryRequestPayload.class,
				LotteryRequestPayload::encode, LotteryRequestPayload::decode, LotteryRequestPayload::handle);
		INSTANCE.registerMessage(packetId++, ShopPurchasePayload.class,
				ShopPurchasePayload::encode, ShopPurchasePayload::decode, ShopPurchasePayload::handle);
		INSTANCE.registerMessage(packetId++, SuperPowerActionPayload.class,
				SuperPowerActionPayload::encode, SuperPowerActionPayload::decode, SuperPowerActionPayload::handle);
		INSTANCE.registerMessage(packetId++, SuperPowerTogglePayload.class,
				SuperPowerTogglePayload::encode, SuperPowerTogglePayload::decode, SuperPowerTogglePayload::handle);
		INSTANCE.registerMessage(packetId++, ReviveActionPayload.class,
			ReviveActionPayload::encode, ReviveActionPayload::decode, ReviveActionPayload::handle);
	INSTANCE.registerMessage(packetId++, ExchangePayload.class,
		ExchangePayload::encode, ExchangePayload::decode, ExchangePayload::handle);
	INSTANCE.registerMessage(packetId++, MoneyBagPayload.class,
		MoneyBagPayload::encode, MoneyBagPayload::decode, MoneyBagPayload::handle);
	INSTANCE.registerMessage(packetId++, EnchantedBookApplyPayload.class,
		EnchantedBookApplyPayload::encode, EnchantedBookApplyPayload::decode, EnchantedBookApplyPayload::handle);
	INSTANCE.registerMessage(packetId++, LotteryConfirmPayload.class,
		LotteryConfirmPayload::encode, LotteryConfirmPayload::decode, LotteryConfirmPayload::handle);
	INSTANCE.registerMessage(packetId++, ModBagActionPayload.class,
		ModBagActionPayload::encode, ModBagActionPayload::decode, ModBagActionPayload::handle);
	INSTANCE.registerMessage(packetId++, BattlefieldEarlyExitPayload.class,
		BattlefieldEarlyExitPayload::encode, BattlefieldEarlyExitPayload::decode, BattlefieldEarlyExitPayload::handle);

// S2C packets (server -> client)
INSTANCE.registerMessage(packetId++, LotteryResultPayload.class,
LotteryResultPayload::encode, LotteryResultPayload::decode, LotteryResultPayload::handle);
INSTANCE.registerMessage(packetId++, LotteryTenResultPayload.class,
LotteryTenResultPayload::encode, LotteryTenResultPayload::decode, LotteryTenResultPayload::handle);
INSTANCE.registerMessage(packetId++, AffixSyncPayload.class,
AffixSyncPayload::encode, AffixSyncPayload::decode, AffixSyncPayload::handle);
INSTANCE.registerMessage(packetId++, TimerSyncPayload.class,
TimerSyncPayload::encode, TimerSyncPayload::decode, TimerSyncPayload::handle);
INSTANCE.registerMessage(packetId++, ShopPurchaseResultPayload.class,
ShopPurchaseResultPayload::encode, ShopPurchaseResultPayload::decode, ShopPurchaseResultPayload::handle);
INSTANCE.registerMessage(packetId++, SuperPowerRollPayload.class,
SuperPowerRollPayload::encode, SuperPowerRollPayload::decode, SuperPowerRollPayload::handle);
INSTANCE.registerMessage(packetId++, SuperPowerSyncPayload.class,
				SuperPowerSyncPayload::encode, SuperPowerSyncPayload::decode, SuperPowerSyncPayload::handle);
INSTANCE.registerMessage(packetId++, BattlefieldMobCountPayload.class,
		BattlefieldMobCountPayload::encode, BattlefieldMobCountPayload::decode, BattlefieldMobCountPayload::handle);
INSTANCE.registerMessage(packetId++, ExchangeListSyncPayload.class,
		ExchangeListSyncPayload::encode, ExchangeListSyncPayload::decode, ExchangeListSyncPayload::handle);
INSTANCE.registerMessage(packetId++, BattlefieldResultPayload.class,
		BattlefieldResultPayload::encode, BattlefieldResultPayload::decode, BattlefieldResultPayload::handle);
INSTANCE.registerMessage(packetId++, BattlefieldVictoryPayload.class,
		BattlefieldVictoryPayload::encode, BattlefieldVictoryPayload::decode, BattlefieldVictoryPayload::handle);
}

/** 发送包到服务端（客户端调用） */
public static <MSG> void sendToServer(MSG message) {
INSTANCE.sendToServer(message);
}

/** 发送包到指定客户端（服务端调用） */
public static <MSG> void sendToClient(ServerPlayer player, MSG message) {
INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
}

/** 发送抽奖结果到客户端 */
public static void sendLotteryResult(ServerPlayer player, String affixId) {
sendToClient(player, new LotteryResultPayload(affixId));
}

/** 发送十连抽结果到客户端 */
public static void sendLotteryTenResult(ServerPlayer player, List<String> affixIds) {
sendToClient(player, new LotteryTenResultPayload(affixIds));
}

/** 发送词条同步数据到客户端 */
public static void sendAffixSync(ServerPlayer player, AffixSyncPayload payload) {
sendToClient(player, payload);
}

/** 发送计时器同步到客户端 */
public static void sendTimerSync(ServerPlayer player, int remainingSeconds) {
sendToClient(player, new TimerSyncPayload(remainingSeconds));
}

/** 发送超能力转盘结果到客户端 */
public static void sendSuperPowerRoll(ServerPlayer player, String superPowerId) {
sendToClient(player, new SuperPowerRollPayload(superPowerId));
}

/** 发送超能力数据同步到客户端 */
public static void sendSuperPowerSync(ServerPlayer player, SuperPowerSyncPayload payload) {
sendToClient(player, payload);
}

/** 发送战场剩余怪物数量到指定客户端 */
public static void sendBattlefieldMobCount(ServerPlayer player, int mobCount, int bossCount) {
sendToClient(player, new BattlefieldMobCountPayload(mobCount, bossCount));
}

/** 发送稀有回收物品列表到客户端 */
public static void sendExchangeList(ServerPlayer player) {
var entries = com.randomsurprise.shop.ExchangeRegistry.getEntriesForSync();
sendToClient(player, new ExchangeListSyncPayload(entries));
}
}