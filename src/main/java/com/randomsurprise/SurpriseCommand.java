package com.randomsurprise;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;

/**
 * 模组命令系统
 * /randomsurprise reload - 重载配置
 * /randomsurprise toggle [on|off] - 开关
 * /randomsurprise status - 查看状态
 * /randomsurprise now - 立即为所有玩家触发一次惊喜
 * /randomsurprise setinterval <秒> - 设置间隔（最小10秒）
 */
public class SurpriseCommand {

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("randomsurprise")
				.requires(src -> src.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(SurpriseCommand::showHelp)
				.then(literal("reload")
						.executes(SurpriseCommand::reload))
				.then(literal("toggle")
						.executes(SurpriseCommand::toggleStatus)
						.then(literal("on")
								.executes(ctx -> setEnabled(ctx, true)))
						.then(literal("off")
								.executes(ctx -> setEnabled(ctx, false))))
				.then(literal("status")
						.executes(SurpriseCommand::showStatus))
				.then(literal("now")
						.executes(SurpriseCommand::triggerNow))
				.then(literal("setinterval")
				.then(argument("seconds", integer(10, 3600))
						.executes(SurpriseCommand::setInterval)))
			// 危险事件模式子命令（默认危险模式）
			.then(literal("dangerous")
					.executes(SurpriseCommand::toggleDangerousEvents)
					.then(literal("on")
							.executes(ctx -> setDangerousEvents(ctx, true)))
					.then(literal("off")
							.executes(ctx -> setDangerousEvents(ctx, false))))
			// 征召战场子命令
				.then(literal("battlefield")
						.executes(SurpriseCommand::showBattlefieldStatus)
						.then(literal("start")
								.executes(SurpriseCommand::startBattlefield))
						.then(literal("end")
								.then(literal("success")
										.executes(ctx -> endBattlefield(ctx, true)))
								.then(literal("fail")
										.executes(ctx -> endBattlefield(ctx, false))))
						.then(literal("reset")
								.executes(SurpriseCommand::resetBattlefield)))
				// 白天敌对子命令
				.then(literal("dayspawn")
						.executes(SurpriseCommand::showDaySpawnStatus)
						.then(literal("toggle")
								.executes(SurpriseCommand::toggleDaySpawn)
								.then(literal("on")
										.executes(ctx -> setDaySpawnEnabled(ctx, true)))
								.then(literal("off")
										.executes(ctx -> setDaySpawnEnabled(ctx, false))))
						.then(literal("setratio")
								.then(argument("percent", integer(0, 100))
										.executes(SurpriseCommand::setDaySpawnRatio)))
						.then(literal("setdays")
								.then(argument("days", integer(1, 10000))
										.executes(SurpriseCommand::setDaySpawnDays)))
						.then(literal("rampup")
								.executes(SurpriseCommand::toggleRampUp)
								.then(literal("on")
										.executes(ctx -> setRampUpEnabled(ctx, true)))
								.then(literal("off")
										.executes(ctx -> setRampUpEnabled(ctx, false))))
						// v2 新增子命令
						.then(literal("cooldown")
								.then(argument("ticks", integer(0, 6000))
										.executes(SurpriseCommand::setDaySpawnCooldown)))
						.then(literal("maxnear")
								.then(argument("count", integer(1, 200))
										.executes(SurpriseCommand::setDaySpawnMaxNear)))
						.then(literal("maxlocal")
								.then(argument("count", integer(1, 50))
										.executes(SurpriseCommand::setDaySpawnMaxLocal)))
						.then(literal("globalcap")
								.then(argument("count", integer(10, 5000))
										.executes(SurpriseCommand::setDaySpawnGlobalCap)))
						.then(literal("boss")
								.executes(SurpriseCommand::toggleBossExclude)
								.then(literal("on")
										.executes(ctx -> setBossExclude(ctx, true)))
								.then(literal("off")
										.executes(ctx -> setBossExclude(ctx, false))))
						.then(literal("dynamic")
								.executes(SurpriseCommand::toggleDynamicRules)
								.then(literal("on")
										.executes(ctx -> setDynamicRules(ctx, true)))
								.then(literal("off")
										.executes(ctx -> setDynamicRules(ctx, false))))
						.then(literal("weather")
								.executes(SurpriseCommand::toggleWeatherEffect)
								.then(literal("on")
										.executes(ctx -> setWeatherEffect(ctx, true)))
								.then(literal("off")
										.executes(ctx -> setWeatherEffect(ctx, false))))
						.then(literal("downed")
								.executes(SurpriseCommand::toggleDownedProtect)
								.then(literal("on")
										.executes(ctx -> setDownedProtect(ctx, true)))
								.then(literal("off")
										.executes(ctx -> setDownedProtect(ctx, false)))))
				// 稀有回收子命令
				.then(literal("exchange")
						.executes(SurpriseCommand::showExchangeStats)
						.then(literal("reload")
								.executes(SurpriseCommand::reloadExchange))
						.then(literal("stats")
								.executes(SurpriseCommand::showExchangeStats)))
		);
	}

	/** 显示征召战场当前状态 */
	private static int showBattlefieldStatus(CommandContext<CommandSourceStack> ctx) {
		var state = com.randomsurprise.battlefield.BattlefieldManager.getState();
		int totalEvents = com.randomsurprise.battlefield.BattlefieldManager.getTotalEventCount();
		int totalBattles = com.randomsurprise.battlefield.BattlefieldManager.getTotalBattlefieldCount();
		int fails = com.randomsurprise.battlefield.BattlefieldManager.getConsecutiveFailures();
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§6===== 征召战场状态 ====="), false);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§e当前状态: §b" + state.name()), false);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§e累计事件: §a" + totalEvents + "/" + com.randomsurprise.battlefield.BattlefieldManager.EVENTS_PER_BATTLEFIELD), false);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§e累计战场次数: §a" + totalBattles), false);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§e连续失败: §c" + fails), false);
		return 1;
	}

	/** 手动触发征召战场（管理员命令） */
	private static int startBattlefield(CommandContext<CommandSourceStack> ctx) {
		var server = ctx.getSource().getServer();
		com.randomsurprise.battlefield.BattlefieldManager.startBattlefield(server);
		ctx.getSource().sendSuccess(() -> Component.literal("§a已手动触发征召战场"), false);
		return 1;
	}

	/** 手动结束征召战场（管理员命令） */
	private static int endBattlefield(CommandContext<CommandSourceStack> ctx, boolean success) {
		var server = ctx.getSource().getServer();
		com.randomsurprise.battlefield.BattlefieldManager.endBattlefield(server, success);
		ctx.getSource().sendSuccess(() -> Component.literal(
				success ? "§a已手动结算：胜利" : "§c已手动结算：失败"), false);
		return 1;
	}

	/** 代价重置：仅在入侵态可用 */
	private static int resetBattlefield(CommandContext<CommandSourceStack> ctx) {
		var server = ctx.getSource().getServer();
		boolean ok = com.randomsurprise.battlefield.BattlefieldManager.resetWithCost(server);
		if (ok) {
			ctx.getSource().sendSuccess(() -> Component.literal(
					"§a已执行代价重置：清空全服词条+计数器归零"), false);
		} else {
			ctx.getSource().sendFailure(Component.literal(
					"§c重置失败：仅在随机世界入侵状态下可用"));
		}
		return 1;
	}

	// ===== 白天敌对生成命令 =====

	/** 显示白天敌对生成状态 */
	private static int showDaySpawnStatus(CommandContext<CommandSourceStack> ctx) {
		boolean enabled = SurpriseConfig.isDaytimeSpawningEnabled();
		double initialRatio = SurpriseConfig.getDaytimeInitialSpawnRatio();
		int rampDays = SurpriseConfig.getDaytimeSpawnRampUpDays();
		boolean rampEnabled = SurpriseConfig.isDaytimeSpawnRampUpEnabled();

		double currentRatio = 0;
		long cooldownRemaining = 0;
		try {
			var player = ctx.getSource().getPlayerOrException();
			currentRatio = DaytimeSpawnManager.getCurrentDaytimeSpawnRatio(player.serverLevel());
			cooldownRemaining = DaytimeSpawnManager.getPlayerCooldownRemaining(player.serverLevel(), player.getUUID());
		} catch (Exception e) {
			// 非玩家执行时不显示当前比例
		}

		ctx.getSource().sendSuccess(() -> Component.literal(
				"§6===== 白天敌对生成设置 v2 ====="), false);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§e总开关: " + (enabled ? "§a开启" : "§c关闭")), false);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§e初始比例: §b" + String.format("%.0f%%", initialRatio * 100)
						+ " §7| 增长天数: §b" + rampDays + "天"
						+ " §7| 动态增长: " + (rampEnabled ? "§a开" : "§c关")), false);
		try {
			double finalCurrentRatio = currentRatio;
			long finalCooldown = cooldownRemaining;
			ctx.getSource().sendSuccess(() -> Component.literal(
					"§e当前比例: §d" + String.format("%.0f%%", finalCurrentRatio * 100)
							+ " §7| 个人冷却剩余: §d" + (finalCooldown / 20) + "秒"), false);
		} catch (Exception ignored) {}
		// v2 新增信息
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§e生成冷却: §b" + (SurpriseConfig.getDaytimeSpawnCooldownTicks() / 20) + "秒"
						+ " §7| 玩家附近上限: §b" + SurpriseConfig.getDaytimeMaxHostilesNearPlayer()
						+ " §7| 局部同类型上限: §b" + SurpriseConfig.getDaytimeMaxPerTypeInLocalArea()
						+ " §7| 全局软上限: §b" + SurpriseConfig.getDaytimeGlobalSoftCap()), false);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§eBoss排除: " + (SurpriseConfig.isDaytimeExcludeBosses() ? "§a开" : "§c关")
						+ " §7| 动态规则: " + (SurpriseConfig.isDaytimeDynamicRulesEnabled() ? "§a开" : "§c关")
						+ " §7| 天气影响: " + (SurpriseConfig.isDaytimeWeatherEffectEnabled() ? "§a开" : "§c关")
						+ " §7| 倒地保护: " + (SurpriseConfig.isDaytimeDownedProtectionEnabled() ? "§a开" : "§c关")), false);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§e光照上限: §b" + SurpriseConfig.getDaytimeMaxLightLevel()
						+ " §7| 坡度上限: §b" + SurpriseConfig.getDaytimeMaxSlopeBlocks() + "格"
						+ " §7| 生成距离: §b" + SurpriseConfig.getDaytimeMinSpawnDistance() + "~" + SurpriseConfig.getDaytimeMaxSpawnDistance() + "格"), false);
		// 显示分层生物数量
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§e生物分层: §a基础" + DaytimeSpawnManager.getTier1Count()
						+ " §b中级" + DaytimeSpawnManager.getTier2Count()
						+ " §d高级" + DaytimeSpawnManager.getTier3Count()), false);
		return 1;
	}

	/** 切换白天敌对对开关 */
	private static int toggleDaySpawn(CommandContext<CommandSourceStack> ctx) {
		boolean newState = !SurpriseConfig.isDaytimeSpawningEnabled();
		SurpriseConfig.setDaytimeSpawningEnabled(newState);
		SurpriseConfig.save();
		boolean finalState = newState;
		ctx.getSource().sendSuccess(() -> Component.translatable(
				finalState ? "randomsurprise.daytime_spawning.enabled"
						: "randomsurprise.daytime_spawning.disabled"), false);
		return 1;
	}

	private static int setDaySpawnEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
		SurpriseConfig.setDaytimeSpawningEnabled(enabled);
		SurpriseConfig.save();
		ctx.getSource().sendSuccess(() -> Component.translatable(
				enabled ? "randomsurprise.daytime_spawning.enabled"
						: "randomsurprise.daytime_spawning.disabled"), false);
		return 1;
	}

	/** 设置初始生成比例 */
	private static int setDaySpawnRatio(CommandContext<CommandSourceStack> ctx) {
		int percent = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "percent");
		double ratio = percent / 100.0;
		SurpriseConfig.setDaytimeInitialSpawnRatio(ratio);
		SurpriseConfig.save();
		ctx.getSource().sendSuccess(() -> Component.translatable(
				"randomsurprise.daytime_spawning.ratio_set", percent), false);
		return 1;
	}

	/** 设置增长天数 */
	private static int setDaySpawnDays(CommandContext<CommandSourceStack> ctx) {
		int days = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "days");
		SurpriseConfig.setDaytimeSpawnRampUpDays(days);
		SurpriseConfig.save();
		ctx.getSource().sendSuccess(() -> Component.translatable(
				"randomsurprise.daytime_spawning.days_set", days), false);
		return 1;
	}

	/** 切换动态增长 */
	private static int toggleRampUp(CommandContext<CommandSourceStack> ctx) {
		boolean newState = !SurpriseConfig.isDaytimeSpawnRampUpEnabled();
		SurpriseConfig.setDaytimeSpawnRampUpEnabled(newState);
		SurpriseConfig.save();
		boolean finalState = newState;
		ctx.getSource().sendSuccess(() -> Component.translatable(
				finalState ? "randomsurprise.daytime_spawning.rampup_on"
						: "randomsurprise.daytime_spawning.rampup_off"), false);
		return 1;
	}

	private static int setRampUpEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
		SurpriseConfig.setDaytimeSpawnRampUpEnabled(enabled);
		SurpriseConfig.save();
		ctx.getSource().sendSuccess(() -> Component.translatable(
				enabled ? "randomsurprise.daytime_spawning.rampup_on"
						: "randomsurprise.daytime_spawning.rampup_off"), false);
		return 1;
	}

	// ===== v2 新增命令处理 =====

	/** 设置生成冷却（tick） */
	private static int setDaySpawnCooldown(CommandContext<CommandSourceStack> ctx) {
		int ticks = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "ticks");
		SurpriseConfig.setDaytimeSpawnCooldownTicks(ticks);
		SurpriseConfig.save();
		int seconds = ticks / 20;
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§a[白天生成] 生成冷却已设为: §e" + seconds + "秒 (" + ticks + "tick)"), false);
		return 1;
	}

	/** 设置玩家附近敌对生物上限 */
	private static int setDaySpawnMaxNear(CommandContext<CommandSourceStack> ctx) {
		int count = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count");
		SurpriseConfig.setDaytimeMaxHostilesNearPlayer(count);
		SurpriseConfig.save();
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§a[白天生成] 玩家附近上限已设为: §e" + count + " 只"), false);
		return 1;
	}

	/** 设置局部区域同类型上限 */
	private static int setDaySpawnMaxLocal(CommandContext<CommandSourceStack> ctx) {
		int count = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count");
		SurpriseConfig.setDaytimeMaxPerTypeInLocalArea(count);
		SurpriseConfig.save();
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§a[白天生成] 局部同类型上限已设为: §e" + count + " 只"), false);
		return 1;
	}

	/** 设置全局软上限 */
	private static int setDaySpawnGlobalCap(CommandContext<CommandSourceStack> ctx) {
		int count = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count");
		SurpriseConfig.setDaytimeGlobalSoftCap(count);
		SurpriseConfig.save();
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§a[白天生成] 全局软上限已设为: §e" + count + " 只"), false);
		return 1;
	}

	/** 切换 Boss 排除 */
	private static int toggleBossExclude(CommandContext<CommandSourceStack> ctx) {
		boolean newState = !SurpriseConfig.isDaytimeExcludeBosses();
		SurpriseConfig.setDaytimeExcludeBosses(newState);
		SurpriseConfig.save();
		DaytimeSpawnManager.invalidateCache();
		boolean finalState = newState;
		ctx.getSource().sendSuccess(() -> Component.literal(
				finalState ? "§a[白天生成] Boss排除已开启（不会生成凋零/末影龙等）"
						: "§c[白天生成] Boss排除已关闭（警告：可能生成Boss！）"), false);
		return 1;
	}

	private static int setBossExclude(CommandContext<CommandSourceStack> ctx, boolean enabled) {
		SurpriseConfig.setDaytimeExcludeBosses(enabled);
		SurpriseConfig.save();
		DaytimeSpawnManager.invalidateCache();
		ctx.getSource().sendSuccess(() -> Component.literal(
				enabled ? "§a[白天生成] Boss排除已开启"
						: "§c[白天生成] Boss排除已关闭（警告）"), false);
		return 1;
	}

	/** 切换动态规则 */
	private static int toggleDynamicRules(CommandContext<CommandSourceStack> ctx) {
		boolean newState = !SurpriseConfig.isDaytimeDynamicRulesEnabled();
		SurpriseConfig.setDaytimeDynamicRulesEnabled(newState);
		SurpriseConfig.save();
		boolean finalState = newState;
		ctx.getSource().sendSuccess(() -> Component.literal(
				finalState ? "§a[白天生成] 动态规则已开启（玩家等级/进度/天气/状态影响生成）"
						: "§c[白天生成] 动态规则已关闭（使用固定比例）"), false);
		return 1;
	}

	private static int setDynamicRules(CommandContext<CommandSourceStack> ctx, boolean enabled) {
		SurpriseConfig.setDaytimeDynamicRulesEnabled(enabled);
		SurpriseConfig.save();
		ctx.getSource().sendSuccess(() -> Component.literal(
				enabled ? "§a[白天生成] 动态规则已开启"
						: "§c[白天生成] 动态规则已关闭"), false);
		return 1;
	}

	/** 切换天气影响 */
	private static int toggleWeatherEffect(CommandContext<CommandSourceStack> ctx) {
		boolean newState = !SurpriseConfig.isDaytimeWeatherEffectEnabled();
		SurpriseConfig.setDaytimeWeatherEffectEnabled(newState);
		SurpriseConfig.save();
		boolean finalState = newState;
		ctx.getSource().sendSuccess(() -> Component.literal(
				finalState ? "§a[白天生成] 天气影响已开启（雨天+10%/雷暴+25%）"
						: "§c[白天生成] 天气影响已关闭"), false);
		return 1;
	}

	private static int setWeatherEffect(CommandContext<CommandSourceStack> ctx, boolean enabled) {
		SurpriseConfig.setDaytimeWeatherEffectEnabled(enabled);
		SurpriseConfig.save();
		ctx.getSource().sendSuccess(() -> Component.literal(
				enabled ? "§a[白天生成] 天气影响已开启"
						: "§c[白天生成] 天气影响已关闭"), false);
		return 1;
	}

	/** 切换倒地玩家保护 */
	private static int toggleDownedProtect(CommandContext<CommandSourceStack> ctx) {
		boolean newState = !SurpriseConfig.isDaytimeDownedProtectionEnabled();
		SurpriseConfig.setDaytimeDownedProtectionEnabled(newState);
		SurpriseConfig.save();
		boolean finalState = newState;
		ctx.getSource().sendSuccess(() -> Component.literal(
				finalState ? "§a[白天生成] 倒地玩家保护已开启（倒地时减少生成）"
						: "§c[白天生成] 倒地玩家保护已关闭"), false);
		return 1;
	}

	private static int setDownedProtect(CommandContext<CommandSourceStack> ctx, boolean enabled) {
		SurpriseConfig.setDaytimeDownedProtectionEnabled(enabled);
		SurpriseConfig.save();
		ctx.getSource().sendSuccess(() -> Component.literal(
				enabled ? "§a[白天生成] 倒地玩家保护已开启"
						: "§c[白天生成] 倒地玩家保护已关闭"), false);
		return 1;
	}

	private static int showHelp(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack source = ctx.getSource();
		source.sendSuccess(() -> Component.literal("§6===== 随机惊喜命令 ====="), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise reload §7- 重载配置"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise toggle [on|off] §7- 开关"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise status §7- 查看状态"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise now §7- 立即触发惊喜"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise setinterval <秒> §7- 设置间隔"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise dangerous [on|off] §7- 开关危险事件模式(默认开)"), false);
		source.sendSuccess(() -> Component.literal("§6----- 征召战场 -----"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise battlefield §7- 查看战场状态"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise battlefield start §7- 手动触发战场"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise battlefield end <success|fail> §7- 手动结算"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise battlefield reset §7- 代价重置(仅入侵态)"), false);
		source.sendSuccess(() -> Component.literal("§6----- 白天敌对生成 v2 -----"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise dayspawn §7- 查看白天生成设置"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise dayspawn toggle [on|off] §7- 开关白天生成"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise dayspawn setratio <percent> §7- 设置初始比例(0-100)"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise dayspawn setdays <days> §7- 设置增长天数"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise dayspawn rampup [on|off] §7- 开关动态增长"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise dayspawn cooldown <ticks> §7- 设置生成冷却(0-6000tick)"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise dayspawn maxnear <count> §7- 玩家附近上限(1-200)"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise dayspawn maxlocal <count> §7- 局部同类型上限(1-50)"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise dayspawn globalcap <count> §7- 全局软上限(10-5000)"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise dayspawn boss [on|off] §7- 开关Boss排除"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise dayspawn dynamic [on|off] §7- 开关动态规则"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise dayspawn weather [on|off] §7- 开关天气影响"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise dayspawn downed [on|off] §7- 开关倒地保护"), false);
		source.sendSuccess(() -> Component.literal("§6----- 稀有回收 -----"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise exchange reload §7- 热重载兑换配置"), false);
		source.sendSuccess(() -> Component.literal("§e/randomsurprise exchange stats §7- 查看个人兑换统计"), false);
		return 1;
	}

	/** 热重载兑换配置 */
	private static int reloadExchange(CommandContext<CommandSourceStack> ctx) {
		int count = com.randomsurprise.shop.ExchangeRegistry.reload();
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§a[稀有回收] 配置已重载，可回收物品数: " + count), true);
		return 1;
	}

	/** 查看兑换统计（执行者自己的） */
	private static int showExchangeStats(CommandContext<CommandSourceStack> ctx) {
		try {
			ServerPlayer player = ctx.getSource().getPlayerOrException();
			int[] stats = com.randomsurprise.shop.ExchangeRecords.getTotalStats(player.getUUID());
			int totalItems = stats[0];
			int totalCoins = stats[1];
			int recordCount = com.randomsurprise.shop.ExchangeRecords.getRecords(player.getUUID()).size();
			ctx.getSource().sendSuccess(() -> Component.literal(
					"§e[稀有回收统计] §7兑换次数: §f" + recordCount
							+ " §7| 累计回收物品: §f" + totalItems + "个"
							+ " §7| 累计获得金币: §f" + totalCoins), false);
		} catch (CommandSyntaxException e) {
			ctx.getSource().sendFailure(Component.literal("§c此命令需由玩家执行"));
		}
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		SurpriseConfig.load();
		ctx.getSource().sendSuccess(() -> Component.translatable("randomsurprise.command.reload"), false);
		return 1;
	}

	private static int toggleStatus(CommandContext<CommandSourceStack> ctx) {
		boolean newState = !SurpriseConfig.isEnabled();
		SurpriseConfig.setEnabled(newState);
		SurpriseConfig.save();
		boolean finalState = newState;
		ctx.getSource().sendSuccess(() -> Component.translatable(
				finalState ? "randomsurprise.command.toggle.on" : "randomsurprise.command.toggle.off"), false);
		return 1;
	}

	private static int setEnabled(CommandContext<CommandSourceStack> ctx, boolean enabled) {
		SurpriseConfig.setEnabled(enabled);
		SurpriseConfig.save();
		ctx.getSource().sendSuccess(() -> Component.translatable(
				enabled ? "randomsurprise.command.toggle.on" : "randomsurprise.command.toggle.off"), false);
		return 1;
	}

	private static int showStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		boolean enabled = SurpriseConfig.isEnabled();
		int interval = SurpriseConfig.getIntervalSeconds();
		int remaining = RandomSurpriseMod.getManager().getRemainingSeconds(player.getUUID());
		boolean dangerous = SurpriseConfig.isAllowDangerousEvents();
		ctx.getSource().sendSuccess(() -> Component.literal(
				String.format("§6随机惊喜状态: §%s§6 | 间隔: §e%d秒§6 | 下次: §b%d秒后",
						enabled ? "a开启" : "c关闭", interval, remaining)), false);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§6危险事件模式: " + (dangerous ? "§c开启 §7(47危险+49新危险/共96事件)" : "§a关闭 §7(仅31+28安全事件)")), false);
		return 1;
	}

	/** 切换危险事件模式 */
	private static int toggleDangerousEvents(CommandContext<CommandSourceStack> ctx) {
		boolean newState = !SurpriseConfig.isAllowDangerousEvents();
		SurpriseConfig.setAllowDangerousEvents(newState);
		SurpriseConfig.save();
		ctx.getSource().sendSuccess(() -> Component.literal(
				newState ? "§c危险事件模式已开启（全部 96 个事件参与随机）"
						: "§a危险事件模式已关闭（仅安全事件参与随机）"), false);
		return 1;
	}

	private static int setDangerousEvents(CommandContext<CommandSourceStack> ctx, boolean enabled) {
		SurpriseConfig.setAllowDangerousEvents(enabled);
		SurpriseConfig.save();
		ctx.getSource().sendSuccess(() -> Component.literal(
				enabled ? "§c危险事件模式已开启（全部 96 个事件参与随机）"
						: "§a危险事件模式已关闭（仅安全事件参与随机）"), false);
		return 1;
	}

	private static int triggerNow(CommandContext<CommandSourceStack> ctx) {
		int count = RandomSurpriseMod.getManager().triggerNow(ctx.getSource().getServer());
		ctx.getSource().sendSuccess(() -> Component.translatable("randomsurprise.command.now"), false);
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§a已为 §e" + count + " §a位玩家触发惊喜！"), false);
		return 1;
	}

	private static int setInterval(CommandContext<CommandSourceStack> ctx) {
		int seconds = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "seconds");
		SurpriseConfig.setIntervalSeconds(seconds);
		SurpriseConfig.save();
		ctx.getSource().sendSuccess(() -> Component.literal(
				"§a间隔已设置为 §e" + seconds + " §a秒。"), false);
		return 1;
	}
}
