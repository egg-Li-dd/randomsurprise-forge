package com.randomsurprise;

import com.randomsurprise.affix.HostileEnhancer;
import com.randomsurprise.battlefield.BossPool;
import com.randomsurprise.battlefield.BattlefieldManager;
import com.randomsurprise.buff.PlayerBuffs;
import com.randomsurprise.buff.PlayerBuffs.BuffType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Random;

/**
 * 随机惊喜动作执行器
 * 三大类：物品、Buff、事件
 */
public class SurpriseActions {
	private static final Random RANDOM = new Random();

	/**
	 * 惊喜类型
	 */
	private enum SurpriseType {
		ITEM, BUFF, EVENT
	}

	/**
	 * 随机执行一种惊喜
	 * 玩家身上的幸运效果（Luck）会同时影响三类惊喜：
	 *  - 物品：提升稀有物品出现概率
	 *  - Buff：降低负面效果概率，正面效果等级更高
	 *  - 事件：降低负面事件权重，提升正面/稀有事件权重
	 */
	public static void executeRandom(ServerPlayer player) {
		int luckLevel = getLuckLevel(player);
		SurpriseType type = SurpriseType.values()[RANDOM.nextInt(SurpriseType.values().length)];
		switch (type) {
			case ITEM -> giveRandomItem(player, luckLevel);
			case BUFF -> giveRandomBuff(player, luckLevel);
			case EVENT -> triggerRandomEvent(player, luckLevel);
		}
	}

	/**
	 * 获取玩家幸运等级（amplifier+1），无效果返回 0
	 */
	private static int getLuckLevel(ServerPlayer player) {
		var effect = player.getEffect(net.minecraft.world.effect.MobEffects.LUCK);
		return effect != null ? effect.getAmplifier() + 1 : 0;
	}

	// ========== 物品惊喜 ==========

	private static void giveRandomItem(ServerPlayer player, int luckLevel) {
		// 随机物品池（含稀有装备）
		// 抽奖券权重为 5（重复 5 次），让玩家更易获得
		String[] itemKeys = {
				"randomsurprise:lottery_ticket", "randomsurprise:lottery_ticket",
				"randomsurprise:lottery_ticket", "randomsurprise:lottery_ticket",
				"randomsurprise:lottery_ticket",
				"minecraft:diamond", "minecraft:diamond_block", "minecraft:iron_ingot",
				"minecraft:gold_ingot", "minecraft:emerald", "minecraft:netherite_ingot",
				"minecraft:coal", "minecraft:charcoal", "minecraft:redstone",
				"minecraft:lapis_lazuli", "minecraft:quartz", "minecraft:amethyst_shard",
				"minecraft:apple", "minecraft:golden_apple", "minecraft:enchanted_golden_apple",
				"minecraft:bread", "minecraft:cooked_beef", "minecraft:cake",
				"minecraft:golden_carrot", "minecraft:pumpkin_pie",
				"minecraft:diamond_sword", "minecraft:diamond_pickaxe", "minecraft:diamond_axe",
				"minecraft:diamond_shovel", "minecraft:diamond_hoe",
				"minecraft:diamond_helmet", "minecraft:diamond_chestplate",
				"minecraft:diamond_leggings", "minecraft:diamond_boots",
				"minecraft:netherite_sword", "minecraft:netherite_pickaxe",
				"minecraft:bow", "minecraft:crossbow", "minecraft:trident",
				"minecraft:fishing_rod", "minecraft:shears", "minecraft:flint_and_steel",
				"minecraft:shield", "minecraft:elytra",
				"minecraft:tnt", "minecraft:end_crystal", "minecraft:totem_of_undying",
				"minecraft:ender_pearl", "minecraft:eye_of_ender", "minecraft:blaze_rod",
				"minecraft:nether_star", "minecraft:dragon_egg", "minecraft:dragon_breath",
				"minecraft:experience_bottle", "minecraft:writable_book", "minecraft:book",
				"minecraft:saddle", "minecraft:name_tag", "minecraft:music_disc_13",
				"minecraft:music_disc_cat", "minecraft:music_disc_blocks",
				"minecraft:bucket", "minecraft:water_bucket", "minecraft:lava_bucket",
				"minecraft:milk_bucket", "minecraft:powder_snow_bucket"
		};
		// 幸运加成：每级幸运额外加入一份稀有物品池，提高稀有物品出现概率
		String[] rareBoost = {
				"minecraft:netherite_ingot", "minecraft:netherite_sword", "minecraft:netherite_pickaxe",
				"minecraft:enchanted_golden_apple", "minecraft:totem_of_undying",
				"minecraft:elytra", "minecraft:nether_star", "minecraft:dragon_egg",
				"minecraft:dragon_breath", "minecraft:end_crystal", "minecraft:diamond_block"
		};
		java.util.List<String> pool = new java.util.ArrayList<>(java.util.Arrays.asList(itemKeys));
		for (int i = 0; i < luckLevel; i++) {
			pool.addAll(java.util.Arrays.asList(rareBoost));
		}
		String key = pool.get(RANDOM.nextInt(pool.size()));
		var item = net.minecraft.core.registries.BuiltInRegistries.ITEM
				.getOptional(new net.minecraft.resources.ResourceLocation(key));
		if (item.isEmpty()) {
			// 兜底：给一个钻石
			player.getInventory().add(new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.DIAMOND, 1));
		} else {
			// 数量上限提高：基础1~maxStack，并有10%概率额外翻倍
			int maxAmount = Math.min(48, item.get().getMaxStackSize());
			int amount = 1 + RANDOM.nextInt(maxAmount);
			if (RANDOM.nextInt(10) == 0) {
				amount = Math.min(64, amount * 2); // 10%概率翻倍，上限64
			}
			player.getInventory().add(new net.minecraft.world.item.ItemStack(item.get(), amount));
		}
		player.sendSystemMessage(Component.translatable("randomsurprise.message.item",
				Component.translatable(item.map(i -> i.getDescriptionId())
						.orElse("minecraft:diamond"))), false);
	}

	// ========== Buff 惊喜 ==========

	private static void giveRandomBuff(ServerPlayer player, int luckLevel) {
		// 正面效果池
		var positiveEffects = new java.util.ArrayList<net.minecraft.world.effect.MobEffect>();
		positiveEffects.add(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
		positiveEffects.add(net.minecraft.world.effect.MobEffects.DIG_SPEED);
		positiveEffects.add(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST);
		positiveEffects.add(net.minecraft.world.effect.MobEffects.HEAL);
		positiveEffects.add(net.minecraft.world.effect.MobEffects.JUMP);
		positiveEffects.add(net.minecraft.world.effect.MobEffects.REGENERATION);
		positiveEffects.add(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
		positiveEffects.add(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE);
		positiveEffects.add(net.minecraft.world.effect.MobEffects.WATER_BREATHING);
		positiveEffects.add(net.minecraft.world.effect.MobEffects.INVISIBILITY);
		positiveEffects.add(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
		positiveEffects.add(net.minecraft.world.effect.MobEffects.LUCK);
		positiveEffects.add(net.minecraft.world.effect.MobEffects.SLOW_FALLING);
		positiveEffects.add(net.minecraft.world.effect.MobEffects.CONDUIT_POWER);
		positiveEffects.add(net.minecraft.world.effect.MobEffects.DOLPHINS_GRACE);
		positiveEffects.add(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE);

		// 负面效果池
		var negativeEffects = new java.util.ArrayList<net.minecraft.world.effect.MobEffect>();
		negativeEffects.add(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
		negativeEffects.add(net.minecraft.world.effect.MobEffects.DIG_SLOWDOWN);
		negativeEffects.add(net.minecraft.world.effect.MobEffects.HARM);
		negativeEffects.add(net.minecraft.world.effect.MobEffects.CONFUSION);
		negativeEffects.add(net.minecraft.world.effect.MobEffects.BLINDNESS);
		negativeEffects.add(net.minecraft.world.effect.MobEffects.HUNGER);
		negativeEffects.add(net.minecraft.world.effect.MobEffects.WEAKNESS);
		negativeEffects.add(net.minecraft.world.effect.MobEffects.POISON);
		negativeEffects.add(net.minecraft.world.effect.MobEffects.WITHER);
		negativeEffects.add(net.minecraft.world.effect.MobEffects.LEVITATION);
		negativeEffects.add(net.minecraft.world.effect.MobEffects.UNLUCK);

		// 决定正面还是负面
		// 幸运加成：每级幸运降低 0.1 的负面概率（基础0.5，最低0）
		double negativeChance = SurpriseConfig.isAllowNegativeBuffs()
				? Math.max(0.0, 0.5 - luckLevel * 0.1) : 0.0;
		boolean negative = RANDOM.nextDouble() < negativeChance;
		var pool = negative ? negativeEffects : positiveEffects;
		net.minecraft.world.effect.MobEffect effect = pool.get(RANDOM.nextInt(pool.size()));

		// 瞬间效果（HARM/HEAL）只能持续1 tick，否则会每tick重复触发伤害/治疗
		boolean isInstant = effect.isInstantenous();
		int duration = isInstant ? 1 : (20 + RANDOM.nextInt(40)) * 20; // 20~60 秒（增强）
		// 正面效果等级受幸运加成：基础1~4，每级幸运+1
		int amplifier = negative ? RANDOM.nextInt(2) : 1 + RANDOM.nextInt(4) + luckLevel;
		boolean ambient = true;
		boolean showParticles = true;
		boolean showIcon = true;

		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				effect, duration, amplifier, ambient, showParticles, showIcon));

		player.sendSystemMessage(Component.translatable("randomsurprise.message.buff",
				Component.translatable(effect.getDescriptionId())), false);
	}

	// ========== 趣味事件 ==========

	private static void triggerRandomEvent(ServerPlayer player, int luckLevel) {
		// 加权随机事件表（共96个事件 ID 0-95）
		// 索引: 0-9普通, 10生成敌对, 11TNT, 12夜晚, 13白天, 14速度,
		// 15僵尸群, 16骷髅队, 17苦力怕群, 18掠夺者
		// 新增: 19空投, 20神秘商人, 21双倍掉落, 22经验雨(趣味,权重5-6)
		//       23流星雨, 24限时无敌, 25双倍经验(稀有,权重1-2)
		//       26强力武器(权重2), 27召唤模组生物(权重1), 28建筑生成(权重3)
		// 临时增益事件: 29仇恨吸引, 30过境火焰, 31点石成金, 32磁铁吸引,
		//              33百花足迹, 34雷霆体质, 35跳跳人, 36荧光显现,
		//              37冰霜光环, 38重击
		// 袭击事件: 39混合袭击, 40模组生物袭击, 41Boss级袭击
		// 模组入侵事件: 42暮色森林入侵, 43灾变裂缝, 44变异爆发
		// 祝福/宝藏事件: 45凤凰祝福, 46宝藏猎人
		// ===== v2 扩展（47-95 共49个） =====
		// 战斗类危险事件(47-58): 凋零头雨/守卫者伏击/幻翼群/恼鬼/末影人/凋零骷髅/猪灵/劫兽/蠹虫/洞穴蜘蛛/僵尸村民/末影螨
		// 环境趣味事件(59-68): 时光倒流/幸运方块/花海/冰封/岩浆/降雪/双段跳/夜视/荧光草地/水下呼吸
		// 奖励稀有事件(69-78): 附魔装备/黄金雨/稀有宝箱/经验瓶雨/钻石雨/免费附魔/铁砧雨/音乐祝福/幸运护符/英雄降临
		// 祝福增益事件(79-88): 神圣护盾/力量涌动/疾风之翼/魔力流动/自然祝福/火焰免疫/冰霜免疫/再生加成/隐身斗篷/急迫光环
		// 挑战危险事件(89-95): 强化头目/末影龙之仆/下界入侵/女巫集会/唤魔者/凋零袭击/卫道士冲锋
		int[] weightedEvents;
		if (SurpriseConfig.isAllowDangerousEvents()) {
			weightedEvents = new int[]{
					0, 1, 2, 3, 4, 5, 6, 7, 8, 9,  // 普通事件各1次
					12, 13, 14,                      // 白天/夜晚/速度
					10, 10, 10,                       // 生成敌对（权重3）
					11, 11,                            // TNT（权重2）
					15, 15, 16, 16, 17, 17, 18, 18,   // 敌对群攻事件（各权重2）
					// 趣味事件（高权重5-6）
					19, 19, 19, 19, 19, 19,           // 空投物资（权重6）
					20, 20, 20, 20, 20,               // 神秘商人（权重5）
					21, 21, 21, 21, 21,               // 双倍掉落（权重5）
					22, 22, 22, 22, 22, 22,           // 经验雨（权重6）
					// 稀有事件（低权重1-2）
					23,                                // 流星雨（权重1，仅视觉）
					24, 24,                            // 限时无敌（权重2）
					25,                        // 双倍经验（权重1）
					26, 26,                     // 强力武器（权重2）
					27,                         // 召唤模组生物（权重1）
					28, 28, 28,                  // 建筑生成（权重3）
					// 临时增益事件（30~60s 持续）
				29, 29,                     // 仇恨吸引（权重2）
				30, 30,                     // 过境火焰（权重2）
				31, 31,                     // 点石成金（权重2）
				32, 32, 32,                 // 磁铁吸引（权重3，实用）
				33, 33,                     // 百花足迹（权重2）
				34, 34,                     // 雷霆体质（权重2）
				35, 35,                     // 跳跳人体质（权重2）
				36,                          // 荧光显现（权重1）
				37, 37,                     // 冰霜光环（权重2）
				38, 38,                     // 重击（权重2）
				// 袭击事件（危险事件，权重2-3）
			39, 39,                     // 混合袭击（权重2）
			40,                          // 模组生物袭击（权重1）
			41, 41,                     // Boss级袭击（权重2）
			// 模组入侵事件（危险，权重1）
			42,                          // 暮色森林入侵（权重1）
			43,                          // 灾变裂缝（权重1）
			44,                          // 变异爆发（权重1）
			// 祝福/宝藏事件（安全，权重1-2）
			45,                          // 凤凰祝福（权重1）
			46, 46,                      // 宝藏猎人（权重2）
			// ===== v2 新增战斗类危险事件（47-58，权重1-2）=====
			47, 47,                      // 凋零骷髅头雨（权重2）
			48,                           // 守卫者伏击（权重1）
			49, 49,                      // 幻翼群袭（权重2）
			50,                           // 恼鬼入侵（权重1）
			51, 51,                      // 末影人暴走（权重2）
			52,                           // 凋零骷髅小队（权重1）
			53, 53,                      // 猪灵旅团（权重2）
			54,                           // 劫兽冲锋（权重1）
			55, 55,                      // 蠹虫群涌（权重2）
			56,                           // 洞穴蜘蛛伏击（权重1）
			57, 57,                      // 僵尸村民群（权重2）
			58,                           // 末影螨入侵（权重1）
			// ===== v2 新增环境趣味事件（59-68，权重2-3）=====
			59, 59,                      // 时光倒流（权重2）
			60, 60, 60,                  // 幸运方块（权重3）
			61, 61,                      // 花海盛宴（权重2）
			62, 62,                      // 冰封大地（权重2）
			63,                           // 岩浆涌出（权重1，危险）
			64, 64,                      // 降雪（权重2）
			65, 65,                      // 双段跳（权重2）
			66, 66,                      // 夜视祝福（权重2）
			67, 67,                      // 荧光草地（权重2）
			68, 68,                      // 水下呼吸（权重2）
			// ===== v2 新增奖励稀有事件（69-78，权重1-2）=====
			69, 69,                      // 附魔装备（权重2）
			70, 70,                      // 黄金雨（权重2）
			71,                           // 稀有宝箱（权重1）
			72, 72,                      // 经验瓶雨（权重2）
			73,                           // 钻石雨（权重1）
			74, 74,                      // 免费附魔（权重2）
			75,                           // 铁砧雨（权重1，趣味危险）
			76,                           // 音乐祝福（权重1）
			77, 77,                      // 幸运护符（权重2）
			78,                           // 英雄降临（权重1）
			// ===== v2 新增祝福增益事件（79-88，权重2）=====
			79, 79,                      // 神圣护盾（权重2）
			80, 80,                      // 力量涌动（权重2）
			81, 81,                      // 疾风之翼（权重2）
			82, 82,                      // 魔力流动（权重2）
			83, 83,                      // 自然祝福（权重2）
			84, 84,                      // 火焰免疫套餐（权重2）
			85, 85,                      // 冰霜免疫套餐（权重2）
			86, 86,                      // 再生加成（权重2）
			87, 87,                      // 隐身斗篷（权重2）
			88, 88,                      // 急迫光环（权重2）
			// ===== v2 新增挑战危险事件（89-95，权重1）=====
			89,                           // 强化头目（权重1）
			90,                           // 末影龙之仆（权重1）
			91, 91,                      // 下界入侵（权重2）
			92,                           // 女巫集会（权重1）
			93,                           // 唤魔者袭击（权重1）
			94, 94,                      // 凋零袭击（权重2）
			95                            // 卫道士冲锋（权重1）
		};
		} else {
			// 危险事件关闭时仍保留趣味、稀有增益和临时增益事件
			weightedEvents = new int[]{
					0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 13, 14,
					19, 19, 19, 19, 19, 19,   // 空投物资（权重6）
					20, 20, 20, 20, 20,       // 神秘商人（权重5）
					21, 21, 21, 21, 21,       // 双倍掉落（权重5）
					22, 22, 22, 22, 22, 22,   // 经验雨（权重6）
					23,                        // 流星雨（权重1，仅视觉）
					24, 24,                    // 限时无敌（权重2）
					25,                        // 双倍经验（权重1）
					26, 26,                    // 强力武器（权重2）
					28, 28, 28,                // 建筑生成（权重3，安全模式下仅生成奖励小屋）
					// 临时增益事件（安全模式下保留）
				29, 29, 31, 31, 32, 32, 32,
				33, 33, 35, 35, 36, 37, 37,
				// 祝福/宝藏事件（安全模式下保留）
				45,                          // 凤凰祝福（权重1）
				46, 46,                      // 宝藏猎人（权重2）
				// ===== v2 安全模式新增事件（仅安全事件）=====
				59, 59,                      // 时光倒流（权重2）
				60, 60, 60,                  // 幸运方块（权重3）
				61, 61,                      // 花海盛宴（权重2）
				62, 62,                      // 冰封大地（权重2）
				64, 64,                      // 降雪（权重2）
				65, 65,                      // 双段跳（权重2）
				66, 66,                      // 夜视祝福（权重2）
				67, 67,                      // 荧光草地（权重2）
				68, 68,                      // 水下呼吸（权重2）
				69, 69,                      // 附魔装备（权重2）
				70, 70,                      // 黄金雨（权重2）
				71,                           // 稀有宝箱（权重1）
				72, 72,                      // 经验瓶雨（权重2）
				73,                           // 钻石雨（权重1）
				74, 74,                      // 免费附魔（权重2）
				76,                           // 音乐祝福（权重1）
				77, 77,                      // 幸运护符（权重2）
				78,                           // 英雄降临（权重1）
				79, 79,                      // 神圣护盾（权重2）
				80, 80,                      // 力量涌动（权重2）
				81, 81,                      // 疾风之翼（权重2）
				82, 82,                      // 魔力流动（权重2）
				83, 83,                      // 自然祝福（权重2）
				84, 84,                      // 火焰免疫套餐（权重2）
				85, 85,                      // 冰霜免疫套餐（权重2）
				86, 86,                      // 再生加成（权重2）
				87, 87,                      // 隐身斗篷（权重2）
				88, 88                       // 急迫光环（权重2）
		};
		}
		// 幸运加成：每级幸运降低负面事件权重（保留比例 1 - 0.25*luck，最低0.1），
		// 并按等级追加正面/稀有事件条目
		int choice;
		if (luckLevel <= 0) {
			choice = weightedEvents[RANDOM.nextInt(weightedEvents.length)];
		} else {
			java.util.List<Integer> pool = new java.util.ArrayList<>();
			for (int id : weightedEvents) pool.add(id);
			// 1) 降权负面事件（含原版负面 + v2 新增危险事件 47-58/63/75/89-95）
			int[] negativeIds = {
					// 原版负面
					10, 11, 15, 16, 17, 18, 39, 40, 41, 42, 43, 44,
					// v2 新增战斗类危险事件
					47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58,
					// v2 新增环境危险事件
					63,
					// v2 新增趣味危险事件
					75,
					// v2 新增挑战危险事件
					89, 90, 91, 92, 93, 94, 95
			};
			double keepRatio = Math.max(0.1, 1.0 - luckLevel * 0.25);
			for (int negId : negativeIds) {
				int total = 0;
				for (int id : pool) if (id == negId) total++;
				int toRemove = total - (int) Math.round(total * keepRatio);
				var it = pool.iterator();
				while (it.hasNext() && toRemove > 0) {
					if (it.next() == negId) {
						it.remove();
						toRemove--;
					}
				}
			}
			// 2) 加权正面/稀有事件（每级幸运各 +1 条）— 含原版 + v2 安全事件
			int[] positiveBonusIds = {19, 20, 21, 22, 23, 24, 25, 26, 60, 69, 70, 72, 74, 77, 79, 80, 81, 83, 86, 88};
			for (int i = 0; i < luckLevel; i++) {
				for (int posId : positiveBonusIds) pool.add(posId);
			}
			choice = pool.get(RANDOM.nextInt(pool.size()));
		}
		switch (choice) {
			case 0 -> eventLightning(player);
			case 1 -> eventTeleport(player);
			case 2 -> eventRain(player);
			case 3 -> eventThunder(player);
			case 4 -> eventClearWeather(player);
			case 5 -> eventSpawnFriendly(player);
			case 6 -> eventFirework(player);
			case 7 -> eventHeal(player);
			case 8 -> eventFeed(player);
			case 9 -> eventExpDrop(player);
			case 10 -> eventSpawnHostile(player);
			case 11 -> eventTnt(player);
			case 12 -> eventNight(player);
			case 13 -> eventDay(player);
			case 14 -> eventSpeedBoost(player);
			case 15 -> eventZombieHorde(player);
			case 16 -> eventSkeletonSquad(player);
			case 17 -> eventCreeperPack(player);
			case 18 -> eventPillagerRaid(player);
			case 19 -> eventAirdrop(player);
			case 20 -> eventMysteryMerchant(player);
			case 21 -> eventDoubleDrop(player);
			case 22 -> eventExpRain(player);
			case 23 -> eventMeteorShower(player);
			case 24 -> eventInvincibility(player);
			case 25 -> eventDoubleExperience(player);
			case 26 -> eventStrongWeapon(player);
			case 27 -> eventSummonModMob(player);
			case 28 -> eventBuildingGeneration(player);
			case 29 -> eventTaunt(player);
			case 30 -> eventFireTrail(player);
			case 31 -> eventMidasTouch(player);
			case 32 -> eventMagnet(player);
			case 33 -> eventFlowerTrail(player);
			case 34 -> eventStaticField(player);
			case 35 -> eventBouncy(player);
			case 36 -> eventGlowing(player);
			case 37 -> eventFrostAura(player);
			case 38 -> eventMightySwing(player);
			case 39 -> eventMixedRaid(player);
			case 40 -> eventModdedRaid(player);
			case 41 -> eventBossRaid(player);
			case 42 -> eventTwilightInvasion(player);
			case 43 -> eventCataclysmRift(player);
			case 44 -> eventMutantOutbreak(player);
			case 45 -> eventPhoenixBlessing(player);
			case 46 -> eventTreasureHunt(player);
			// ===== v2 新增事件（47-95）=====
			case 47 -> eventWitherSkullRain(player);
			case 48 -> eventGuardianAmbush(player);
			case 49 -> eventPhantomSwarm(player);
			case 50 -> eventVexInvasion(player);
			case 51 -> eventEndermanFrenzy(player);
			case 52 -> eventWitherSkeletonPack(player);
			case 53 -> eventPiglinBrigade(player);
			case 54 -> eventRavagerCharge(player);
			case 55 -> eventSilverfishSwarm(player);
			case 56 -> eventCaveSpiderAmbush(player);
			case 57 -> eventZombieVillagerHorde(player);
			case 58 -> eventEndermiteInvasion(player);
			case 59 -> eventTimeRewind(player);
			case 60 -> eventLuckyBlock(player);
			case 61 -> eventFlowerField(player);
			case 62 -> eventIceField(player);
			case 63 -> eventLavaPool(player);
			case 64 -> eventSnowfall(player);
			case 65 -> eventDoubleJump(player);
			case 66 -> eventNightVision(player);
			case 67 -> eventGlowingField(player);
			case 68 -> eventWaterBreath(player);
			case 69 -> eventEnchantedGear(player);
			case 70 -> eventGoldenShower(player);
			case 71 -> eventRareChest(player);
			case 72 -> eventExpBottleRain(player);
			case 73 -> eventDiamondRain(player);
			case 74 -> eventFreeEnchant(player);
			case 75 -> eventAnvilRain(player);
			case 76 -> eventMusicalBuff(player);
			case 77 -> eventLuckyCharm(player);
			case 78 -> eventHeroVillage(player);
			case 79 -> eventHolyShield(player);
			case 80 -> eventStrengthSurge(player);
			case 81 -> eventSwiftWind(player);
			case 82 -> eventManaFlow(player);
			case 83 -> eventNatureBlessing(player);
			case 84 -> eventFireImmunitySuite(player);
			case 85 -> eventFrostImmunitySuite(player);
			case 86 -> eventRegenerationBoost(player);
			case 87 -> eventInvisibilityCloak(player);
			case 88 -> eventHasteAura(player);
			case 89 -> eventMiniBoss(player);
			case 90 -> eventEnderDragonMinion(player);
			case 91 -> eventNetherInvasion(player);
			case 92 -> eventWitchCoven(player);
			case 93 -> eventEvokerRaid(player);
			case 94 -> eventWitherSkeletonRaid(player);
			case 95 -> eventVindicatorRush(player);
		}
	}

	/**
	 * 触发全服负面事件（征召战场入侵态使用）
	 * 仅触发危险类事件：TNT/敌对群/掠夺者/雷电/夜晚
	 * 对全服每位在线玩家触发一次
	 */
	public static void triggerGlobalNegativeEvent(MinecraftServer server) {
		// 仅触发负面事件：索引 11(TNT)/15(僵尸)/16(骷髅)/17(苦力怕)/18(掠夺者)/10(敌对)/0(雷电)/12(夜晚)
		// 新增: 39(混合袭击)/40(模组袭击)/41(Boss袭击)
		int[] negativeEvents = {0, 10, 11, 12, 15, 16, 17, 18, 39, 39, 41};
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			try {
				int choice = negativeEvents[RANDOM.nextInt(negativeEvents.length)];
				switch (choice) {
					case 0 -> eventLightning(player);
					case 10 -> eventSpawnHostile(player);
					case 11 -> eventTnt(player);
					case 12 -> eventNight(player);
					case 15 -> eventZombieHorde(player);
					case 16 -> eventSkeletonSquad(player);
					case 17 -> eventCreeperPack(player);
					case 18 -> eventPillagerRaid(player);
					case 39 -> eventMixedRaid(player);
					case 40 -> eventModdedRaid(player);
					case 41 -> eventBossRaid(player);
				}
			} catch (Exception e) {
				RandomSurpriseMod.LOGGER.error("入侵态负面事件触发失败 玩家{}: {}",
						player.getName().getString(), e.getMessage());
			}
		}
	}

	/** 建筑生成 - 在玩家附近随机生成陷阱、奖励小屋或战斗高塔 */
	private static void eventBuildingGeneration(ServerPlayer player) {
		BuildingGenerator.BuildingType[] types;
		if (SurpriseConfig.isAllowDangerousEvents()) {
			// v17: 优先使用 MOD_STRUCTURE 类型（mod 建筑优先，原建筑保底）
			types = BuildingGenerator.BuildingType.values();
		} else {
			// 非危险模式：只生成奖励类型 + mod 结构
			types = new BuildingGenerator.BuildingType[]{BuildingGenerator.BuildingType.REWARD_HOUSE, BuildingGenerator.BuildingType.MOD_STRUCTURE};
		}
		BuildingGenerator.BuildingType type = types[RANDOM.nextInt(types.length)];
		BuildingGenerator.generateBuilding(player, type);
	}

	/** 天降雷霆 */
	private static void eventLightning(ServerPlayer player) {
		var level = player.level();
		var lightning = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
		if (lightning != null) {
			lightning.setPos(player.getX(), player.getY(), player.getZ());
			lightning.setVisualOnly(true); // 仅视觉，不造成伤害
			level.addFreshEntity(lightning);
		}
		player.sendSystemMessage(Component.translatable("randomsurprise.event.lightning"), false);
	}

	/** 随机传送 */
	private static void eventTeleport(ServerPlayer player) {
		double dx = player.getX() + (RANDOM.nextDouble() - 0.5) * 100;
		double dz = player.getZ() + (RANDOM.nextDouble() - 0.5) * 100;
		double dy = player.getY() + RANDOM.nextInt(20) - 10;
		player.teleportTo(player.serverLevel(), dx, dy, dz,
				java.util.Collections.emptySet(), player.getYRot(), player.getXRot());
		player.sendSystemMessage(Component.translatable("randomsurprise.event.teleport"), false);
	}

	/** 下雨 */
	private static void eventRain(ServerPlayer player) {
		if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) { serverLevel.setWeatherParameters(0, 6000, true, false); }
		player.sendSystemMessage(Component.translatable("randomsurprise.event.rain"), false);
	}

	/** 雷暴 */
	private static void eventThunder(ServerPlayer player) {
		if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) { serverLevel.setWeatherParameters(0, 6000, true, true); }
		player.sendSystemMessage(Component.translatable("randomsurprise.event.thunder"), false);
	}

	/** 放晴 */
	private static void eventClearWeather(ServerPlayer player) {
		if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) { serverLevel.setWeatherParameters(6000, 0, false, false); }
		player.sendSystemMessage(Component.translatable("randomsurprise.event.clear"), false);
	}

	/** 召唤友好生物 */
	private static void eventSpawnFriendly(ServerPlayer player) {
		var friendly = new net.minecraft.world.entity.EntityType[]{
				net.minecraft.world.entity.EntityType.CAT, net.minecraft.world.entity.EntityType.WOLF,
				net.minecraft.world.entity.EntityType.HORSE, net.minecraft.world.entity.EntityType.COW,
				net.minecraft.world.entity.EntityType.PIG, net.minecraft.world.entity.EntityType.SHEEP,
				net.minecraft.world.entity.EntityType.CHICKEN, net.minecraft.world.entity.EntityType.RABBIT,
				net.minecraft.world.entity.EntityType.PARROT, net.minecraft.world.entity.EntityType.FOX,
				net.minecraft.world.entity.EntityType.PANDA, net.minecraft.world.entity.EntityType.AXOLOTL
		};
		spawnEntityNearPlayer(player, friendly[RANDOM.nextInt(friendly.length)]);
		player.sendSystemMessage(Component.translatable("randomsurprise.event.spawn_animal"), false);
	}

	/** 召唤敌对生物（原版 + 模组） */
	private static void eventSpawnHostile(ServerPlayer player) {
		// 原版敌对生物
		var hostileTypes = new java.util.ArrayList<net.minecraft.world.entity.EntityType<?>>();
		hostileTypes.add(net.minecraft.world.entity.EntityType.ZOMBIE);
		hostileTypes.add(net.minecraft.world.entity.EntityType.SKELETON);
		hostileTypes.add(net.minecraft.world.entity.EntityType.SPIDER);
		hostileTypes.add(net.minecraft.world.entity.EntityType.CREEPER);
		hostileTypes.add(net.minecraft.world.entity.EntityType.ENDERMAN);
		hostileTypes.add(net.minecraft.world.entity.EntityType.WITCH);
		hostileTypes.add(net.minecraft.world.entity.EntityType.HUSK);
		hostileTypes.add(net.minecraft.world.entity.EntityType.STRAY);
		hostileTypes.add(net.minecraft.world.entity.EntityType.PILLAGER);

		// 收集模组敌对生物
		hostileTypes.addAll(collectModdedHostileEntities());

		var type = hostileTypes.get(RANDOM.nextInt(hostileTypes.size()));
		spawnEntityNearPlayer(player, type);
		player.sendSystemMessage(Component.translatable("randomsurprise.event.spawn_hostile"), false);
	}

	/**
	 * 从注册表中收集模组敌对生物类型
	 * 判定逻辑（与 TicketDropHandler 一致）：
	 * - 命名空间在已知模组列表中 + 路径包含敌对关键词
	 * - 或在用户自定义敌对实体ID列表中
	 */
	private static java.util.List<net.minecraft.world.entity.EntityType<?>> collectModdedHostileEntities() {
		var result = new java.util.ArrayList<net.minecraft.world.entity.EntityType<?>>();
		var registry = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;

		// 已知生物模组的命名空间
		var knownNamespaces = java.util.Set.of(
				"alexsmobs", "mowziesmobs", "born_in_chaos", "cataclysm",
				"le_enders_cataclysm", "bosses_of_mass_destruction", "blue_skies",
				"twilightforest", "aether", "betteranimalsplus", "naturalist",
				"crittersandcompanions", "decorative_blocks", "illage_and_spillage",
				"mutantmonsters", "cave_dweller", "multigolem", "enderzoology",
				"friendsandfoes"
		);
		// 敌对关键词
		var hostileKeywords = java.util.Set.of(
				"boss", "hostile", "monster", "demon", "dragon", "evil",
				"ghost", "spirit", "wraith", "phantom", "shadow", "dark",
				"warrior", "knight", "mage", "witch", "necromancer",
				"horror", "beast", "abomination", "construct", "golem",
				"raider", "pillager", "villain", "enemy", "aggressive",
				"dweller", "giant", "wildfire", "illusioner", "mosco",
				"maw", "wroughtnaut", "zombie", "skeleton", "spider",
				"creeper", "enderman", "husk", "stray", "pillager"
		);

		registry.forEach(entityType -> {
			var key = registry.getKey(entityType);
			if (key == null || key.getNamespace().equals("minecraft")) return;
			String namespace = key.getNamespace();
			String path = key.getPath();

			// 命名空间在已知列表 + 路径含敌对关键词
			if (knownNamespaces.contains(namespace)) {
				for (String kw : hostileKeywords) {
					if (path.contains(kw)) {
						result.add(entityType);
						return;
					}
				}
			}
		});

		// 用户自定义敌对实体ID
		for (String id : SurpriseConfig.getExtraHostileEntityIds()) {
			var typeOpt = registry.getOptional(new net.minecraft.resources.ResourceLocation(id));
			typeOpt.ifPresent(result::add);
		}

		// 硬编码已知模组敌对实体白名单（始终加入，即使不匹配关键词）
		String[] KNOWN_HOSTILE_IDS = {
				"alexsmobs:crocodile", "alexsmobs:crimson_mosquito", "alexsmobs:bone_serpent",
				"alexsmobs:froststalker", "alexsmobs:mantis_shrimp",
				"twilightforest:redcap", "twilightforest:kobold", "twilightforest:fire_beetle",
				"twilightforest:hedge_spider", "twilightforest:minotaur", "twilightforest:maze_slime",
				"twilightforest:wraith",
				"enderzoology:concussion_creeper", "enderzoology:fallen_knight",
				"enderzoology:ender_golem", "enderzoology:ender_wizard",
				"born_in_chaos:scarlet_demon", "born_in_chaos:fallen_knight", "born_in_chaos:dried_corpse",
				"mutantmonsters:mutant_zombie", "mutantmonsters:mutant_skeleton", "mutantmonsters:mutant_creeper",
				"illageandspillage:magispeller", "illageandspillage:blastfinder"
		};
		for (String id : KNOWN_HOSTILE_IDS) {
			var typeOpt = registry.getOptional(new net.minecraft.resources.ResourceLocation(id));
			typeOpt.ifPresent(type -> {
				if (!result.contains(type)) result.add(type); // 避免重复
			});
		}

		return result;
	}

	/** 烟花（增强：多种颜色和形状的复杂烟花，数量也增加） */
	private static void eventFirework(ServerPlayer player) {
		// 给玩家 4 枚复杂烟花（原来只有3枚普通烟花）
		for (int i = 0; i < 4; i++) {
			player.getInventory().add(createComplexFireworkStack());
		}
		player.sendSystemMessage(Component.translatable("randomsurprise.event.firework"), false);
	}

	/** 创建一个复杂烟花（多种颜色+多种形状的复合爆炸） */
	private static net.minecraft.world.item.ItemStack createComplexFireworkStack() {
		var stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET, 1);
		try {
			// 多种颜色搭配
			int[][] colorPalettes = {
					{0xFF0000, 0x00FF00, 0x0000FF},     // RGB三色
					{0xFFFF00, 0xFF00FF, 0x00FFFF},     // 青紫黄
					{0xFFA500, 0xFFD700, 0xFFFFFF},     // 金色
					{0xFF1493, 0x9400D3, 0x4B0082},     // 粉紫
					{0x00FF7F, 0xFFFFFF, 0xFFB6C1}      // 翠绿+白+粉
			};
			int[] colors = colorPalettes[RANDOM.nextInt(colorPalettes.length)];
// 2~4 段复合爆炸
int numExplosions = 2 + RANDOM.nextInt(3);
// 1.20.1: 使用 NBT 标签设置烟花
net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
net.minecraft.nbt.ListTag explosions = new net.minecraft.nbt.ListTag();
for (int i = 0; i < numExplosions; i++) {
net.minecraft.nbt.CompoundTag exp = new net.minecraft.nbt.CompoundTag();
// 0=小球, 1=大球, 2=星形, 3=苦力怕, 4=爆裂
exp.putByte("Type", (byte) RANDOM.nextInt(5));
exp.putIntArray("Colors", colors);
exp.putIntArray("FadeColors", new int[]{0xFFFFFF, 0x888888});
exp.putBoolean("Trail", RANDOM.nextBoolean());
exp.putBoolean("Flicker", RANDOM.nextBoolean());
explosions.add(exp);
}
tag.put("Explosions", explosions);
tag.putByte("Flight", (byte) 1);
		} catch (Throwable ignored) {
			// API 变化兜底：返回默认烟花
		}
		return stack;
	}

	/** 治愈 */
	private static void eventHeal(ServerPlayer player) {
		player.heal(player.getMaxHealth());
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.REGENERATION, 200, 1, true, true, true));
		player.sendSystemMessage(Component.translatable("randomsurprise.event.heal"), false);
	}

	/** 饱腹 */
	private static void eventFeed(ServerPlayer player) {
		player.getFoodData().eat(20, 20.0F);
		player.sendSystemMessage(Component.translatable("randomsurprise.event.feed"), false);
	}

	/** 经验雨 */
	private static void eventExpDrop(ServerPlayer player) {
		player.giveExperiencePoints(20 + RANDOM.nextInt(80));
		player.sendSystemMessage(Component.translatable("randomsurprise.event.exp_drop"), false);
	}

	/** TNT 警告（小爆炸） */
	private static void eventTnt(ServerPlayer player) {
		var level = player.level();
		var tnt = net.minecraft.world.entity.EntityType.TNT.create(level);
		if (tnt != null) {
			tnt.setPos(player.getX() + (RANDOM.nextDouble() - 0.5) * 8,
					player.getY(), player.getZ() + (RANDOM.nextDouble() - 0.5) * 8);
			level.addFreshEntity(tnt);
		}
		player.sendSystemMessage(Component.translatable("randomsurprise.event.tnt"), false);
	}

	/** 变为夜晚 */
	private static void eventNight(ServerPlayer player) {
		player.level().getServer().getCommands().performPrefixedCommand(
				player.createCommandSourceStack(), "time set 13000");
		player.sendSystemMessage(Component.translatable("randomsurprise.event.night"), false);
	}

	/** 变为白天 */
	private static void eventDay(ServerPlayer player) {
		player.level().getServer().getCommands().performPrefixedCommand(
				player.createCommandSourceStack(), "time set 1000");
		player.sendSystemMessage(Component.translatable("randomsurprise.event.day"), false);
	}

	/** 速度加成 */
	private static void eventSpeedBoost(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 600, 3, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.JUMP, 600, 2, true, true, true));
		player.sendSystemMessage(Component.translatable("randomsurprise.event.speed_boost"), false);
	}

	/** 僵尸围攻（3~5只） */
	private static void eventZombieHorde(ServerPlayer player) {
		int count = 3 + RANDOM.nextInt(3);
		for (int i = 0; i < count; i++) {
			spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.ZOMBIE);
		}
		player.sendSystemMessage(Component.translatable("randomsurprise.event.zombie_horde"), false);
	}

	/** 骷髅小队（2~3只） */
	private static void eventSkeletonSquad(ServerPlayer player) {
		int count = 2 + RANDOM.nextInt(2);
		for (int i = 0; i < count; i++) {
			spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.SKELETON);
		}
		player.sendSystemMessage(Component.translatable("randomsurprise.event.skeleton_squad"), false);
	}

	/** 苦力怕群（2只） */
	private static void eventCreeperPack(ServerPlayer player) {
		for (int i = 0; i < 2; i++) {
			spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.CREEPER);
		}
		player.sendSystemMessage(Component.translatable("randomsurprise.event.creeper_pack"), false);
	}

	/** 掠夺者袭击（2~3只） */
	private static void eventPillagerRaid(ServerPlayer player) {
		int count = 2 + RANDOM.nextInt(2);
		for (int i = 0; i < count; i++) {
			spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.PILLAGER);
		}
		player.sendSystemMessage(Component.translatable("randomsurprise.event.pillager_raid"), false);
	}

	/** 在玩家附近生成实体 */
	private static void spawnEntityNearPlayer(ServerPlayer player, EntityType<?> type) {
		var level = player.level();
		double x = player.getX() + (RANDOM.nextDouble() - 0.5) * 6;
		double z = player.getZ() + (RANDOM.nextDouble() - 0.5) * 6;
		var entity = type.create(level);
		if (entity != null) {
			entity.setPos(x, player.getY(), z);
			level.addFreshEntity(entity);
			
			if (entity instanceof Mob mob) {
				mob.setTarget(player);
				mob.targetSelector.addGoal(0, new NearestAttackableTargetGoal<>(mob, Player.class, true));
				equipHostileMob(mob);
				HostileEnhancer.enhanceHostile(mob);
			}
		}
	}

	/** 为敌对生物装备武器和护甲 */
	private static void equipHostileMob(Mob mob) {
		String typeName = mob.getType().toShortString().toLowerCase();
		
		boolean isSkeleton = typeName.contains("skeleton") || typeName.contains("stray") || typeName.contains("wither_skeleton");
		boolean isZombie = typeName.contains("zombie") || typeName.contains("husk");
		boolean isHumanoid = isSkeleton || isZombie || typeName.contains("pillager") || 
							typeName.contains("vindicator") || typeName.contains("evoker") || 
							typeName.contains("witch") || typeName.contains("raider");

		if (!isHumanoid) {
			return;
		}

		if (isSkeleton) {
			Item bow = tryGetModItem("minecraft:bow", "twilightforest:iron_bow", "twilightforest:steel_bow");
			if (bow != null) {
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(bow));
			}
		} else {
			Item weapon = getRandomWeapon();
			if (weapon != null) {
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
			}
		}

		if (RANDOM.nextDouble() < 0.4) {
			Item helmet = tryGetModItem("minecraft:iron_helmet", "minecraft:chainmail_helmet");
			if (helmet != null) mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(helmet));
		}
		if (RANDOM.nextDouble() < 0.3) {
			Item chestplate = tryGetModItem("minecraft:iron_chestplate", "minecraft:chainmail_chestplate");
			if (chestplate != null) mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(chestplate));
		}
	}

	/** 获取随机武器（优先模组武器） */
	private static Item getRandomWeapon() {
		String[][] modWeapons = {
				{"cataclysm:void_blade", "cataclysm:tremor_blade", "cataclysm:volcano_blade"},
				{"born_in_chaos_v1:chaos_sword", "born_in_chaos_v1:void_sword"},
				{"twilightforest:iron_sword", "twilightforest:steel_sword", "twilightforest:knight_sword"},
				{"mutantmonsters:mutant_sword"},
				{"alexsmobs:swordfish_sword"},
				{"mowziesmobs:spear"},
				{"minecraft:iron_sword", "minecraft:stone_sword", "minecraft:diamond_sword", 
				 "minecraft:iron_axe", "minecraft:stone_axe", "minecraft:diamond_axe"}
		};

		for (String[] weapons : modWeapons) {
			for (String weapon : weapons) {
				Item item = getItemOrNull(weapon);
				if (item != null) {
					return item;
				}
			}
		}
		return getItemOrNull("minecraft:iron_sword");
	}

	/** 尝试获取模组物品，返回第一个存在的物品 */
	private static Item tryGetModItem(String... candidates) {
		for (String candidate : candidates) {
			Item item = getItemOrNull(candidate);
			if (item != null) {
				return item;
			}
		}
		return null;
	}

	/** 获取物品，不存在返回null */
	private static Item getItemOrNull(String itemId) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM
				.getOptional(new ResourceLocation(itemId))
				.orElse(null);
	}

	// ========== 新增趣味事件 ==========

	/** 空投物资 - 在距离玩家30格以上位置生成箱子实体，包含稀有物品 */
	private static void eventAirdrop(ServerPlayer player) {
		var level = player.level();
		String[] rareItems = {
				"minecraft:diamond", "minecraft:diamond_block", "minecraft:netherite_ingot",
				"minecraft:enchanted_golden_apple", "minecraft:totem_of_undying",
				"minecraft:elytra", "minecraft:nether_star", "minecraft:emerald_block",
				"minecraft:netherite_block", "minecraft:dragon_egg"
		};

		// 在距离玩家30-60格的范围内寻找安全位置
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		double distance = 30 + RANDOM.nextDouble() * 30;
		double dropX = player.getX() + Math.cos(angle) * distance;
		double dropZ = player.getZ() + Math.sin(angle) * distance;
		int dropY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) dropX, (int) dropZ);

		// 生成箱子方块实体
		BlockPos chestPos = new BlockPos((int) dropX, dropY, (int) dropZ);
		net.minecraft.world.level.block.state.BlockState chestState = net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState();
		level.setBlock(chestPos, chestState, 3);

		// 生成信标标记（玩家靠近后自动消失）
		BuildingGenerator.spawnAirdropBeacon((net.minecraft.server.level.ServerLevel) level, chestPos);

		// 填充箱子内容
		var blockEntity = level.getBlockEntity(chestPos);
		if (blockEntity instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
			int count = 1 + RANDOM.nextInt(3);
			for (int i = 0; i < count; i++) {
				String key = rareItems[RANDOM.nextInt(rareItems.length)];
				var item = net.minecraft.core.registries.BuiltInRegistries.ITEM
						.getOptional(new net.minecraft.resources.ResourceLocation(key));
				if (item.isPresent()) {
					int amount = 1 + RANDOM.nextInt(3);
					var stack = new net.minecraft.world.item.ItemStack(item.get(), amount);
					chest.setItem(i, stack);
				}
			}
			chest.setChanged();
		}

		// 计算实际距离并通知玩家坐标
		double actualDistance = Math.sqrt(Math.pow(dropX - player.getX(), 2) + Math.pow(dropZ - player.getZ(), 2));
		player.sendSystemMessage(Component.translatable("randomsurprise.event.airdrop_coords",
				(int) dropX, dropY, (int) dropZ, (int) actualDistance), false);
	}

	/**
	 * 神秘商人 - 生成一个流浪商人，以绿宝石低价出售珍稀资源
	 * 商人自带定制交易：钻石、下界合金、鞘翅、不死图腾等
	 */
	private static void eventMysteryMerchant(ServerPlayer player) {
		var level = player.level();
		double x = player.getX() + (RANDOM.nextDouble() - 0.5) * 4;
		double z = player.getZ() + (RANDOM.nextDouble() - 0.5) * 4;
		var trader = net.minecraft.world.entity.EntityType.WANDERING_TRADER.create(level);
		if (trader != null) {
			trader.setPos(x, player.getY(), z);
			// 设置定制交易：低价出售珍稀资源
			var offers = createRareResourceOffers();
			trader.overrideOffers(offers);
			// 设置商人为不自然消失（停留更久）
			trader.setDespawnDelay(24000); // 20分钟
			trader.setPersistenceRequired();
			level.addFreshEntity(trader);
		}
		player.sendSystemMessage(Component.translatable("randomsurprise.event.mystery_merchant"), false);
	}

	/**
	 * 创建珍稀资源交易列表（绿宝石低价购买稀有资源）
	 */
	private static net.minecraft.world.item.trading.MerchantOffers createRareResourceOffers() {
		var offers = new net.minecraft.world.item.trading.MerchantOffers();
		var emerald = net.minecraft.world.item.Items.EMERALD;
		// 参数: ItemCost(支付物, 数量), 售出ItemStack, 最大使用次数, 经验, 价格倍率
		// 最大使用次数设为1~3（每种限购），让资源有限但便宜

		// v16: 调整价格，价值比控制在1.5-2x（原5-8x过于慷慨）
		// 3绿宝石 → 4钻石
		offers.add(new net.minecraft.world.item.trading.MerchantOffer(
				new net.minecraft.world.item.ItemStack(emerald, 3),
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 4),
				2, 5, 0.05f));
		// 4绿宝石 → 1下界合金锭
		offers.add(new net.minecraft.world.item.trading.MerchantOffer(
				new net.minecraft.world.item.ItemStack(emerald, 4),
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT, 1),
				1, 10, 0.05f));
		// 1绿宝石 → 4末影珍珠
		offers.add(new net.minecraft.world.item.trading.MerchantOffer(
				new net.minecraft.world.item.ItemStack(emerald, 1),
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 4),
				3, 3, 0.05f));
		// 10绿宝石 → 1鞘翅
		offers.add(new net.minecraft.world.item.trading.MerchantOffer(
				new net.minecraft.world.item.ItemStack(emerald, 10),
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ELYTRA, 1),
				1, 20, 0.05f));
		// 1绿宝石 → 2金苹果
		offers.add(new net.minecraft.world.item.trading.MerchantOffer(
				new net.minecraft.world.item.ItemStack(emerald, 1),
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 2),
				3, 3, 0.05f));
		// 8绿宝石 → 1不死图腾
		offers.add(new net.minecraft.world.item.trading.MerchantOffer(
				new net.minecraft.world.item.ItemStack(emerald, 8),
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING, 1),
				1, 15, 0.05f));
		// 12绿宝石 → 1下界之星
		offers.add(new net.minecraft.world.item.trading.MerchantOffer(
				new net.minecraft.world.item.ItemStack(emerald, 12),
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHER_STAR, 1),
				1, 20, 0.05f));
		// 1绿宝石 → 4紫水晶碎片
		offers.add(new net.minecraft.world.item.trading.MerchantOffer(
				new net.minecraft.world.item.ItemStack(emerald, 1),
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD, 4),
				3, 2, 0.05f));
		// 4绿宝石 → 1附魔金苹果
		offers.add(new net.minecraft.world.item.trading.MerchantOffer(
				new net.minecraft.world.item.ItemStack(emerald, 4),
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE, 1),
				1, 10, 0.05f));
		// 1绿宝石 → 4烈焰棒
		offers.add(new net.minecraft.world.item.trading.MerchantOffer(
				new net.minecraft.world.item.ItemStack(emerald, 1),
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BLAZE_ROD, 4),
				3, 3, 0.05f));
		// 2绿宝石 → 8黑曜石
		offers.add(new net.minecraft.world.item.trading.MerchantOffer(
				new net.minecraft.world.item.ItemStack(emerald, 2),
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OBSIDIAN, 8),
				2, 2, 0.05f));
		// 3绿宝石 → 1龙息
		offers.add(new net.minecraft.world.item.trading.MerchantOffer(
				new net.minecraft.world.item.ItemStack(emerald, 3),
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DRAGON_BREATH, 1),
				1, 10, 0.05f));
		// ===== 模组商品（每个独立 try-catch + 存在性检查，模组未安装则跳过）=====
		// 1绿宝石 → 1 twilightforest:charm_of_life（限购1次）
		try {
			var charmOpt = net.minecraft.core.registries.BuiltInRegistries.ITEM
					.getOptional(new net.minecraft.resources.ResourceLocation("twilightforest:charm_of_life"));
			if (charmOpt.isPresent()) {
				offers.add(new net.minecraft.world.item.trading.MerchantOffer(
						new net.minecraft.world.item.ItemStack(emerald, 1),
						new net.minecraft.world.item.ItemStack(charmOpt.get(), 1),
						1, 10, 0.05f));
			}
		} catch (Throwable ignored) { /* 暮色森林未安装，跳过 */ }
		// 2绿宝石 → 8 aether:ambrosium_shard（限购3次）
		try {
			var ambrosiumOpt = net.minecraft.core.registries.BuiltInRegistries.ITEM
					.getOptional(new net.minecraft.resources.ResourceLocation("aether:ambrosium_shard"));
			if (ambrosiumOpt.isPresent()) {
				offers.add(new net.minecraft.world.item.trading.MerchantOffer(
						new net.minecraft.world.item.ItemStack(emerald, 2),
						new net.minecraft.world.item.ItemStack(ambrosiumOpt.get(), 8),
						3, 5, 0.05f));
			}
		} catch (Throwable ignored) { /* 天境未安装，跳过 */ }
		// 3绿宝石 → 1 cataclysm:soul_mass（限购1次）
		try {
			var soulMassOpt = net.minecraft.core.registries.BuiltInRegistries.ITEM
					.getOptional(new net.minecraft.resources.ResourceLocation("cataclysm:soul_mass"));
			if (soulMassOpt.isPresent()) {
				offers.add(new net.minecraft.world.item.trading.MerchantOffer(
						new net.minecraft.world.item.ItemStack(emerald, 3),
						new net.minecraft.world.item.ItemStack(soulMassOpt.get(), 1),
						1, 15, 0.05f));
			}
		} catch (Throwable ignored) { /* 灾变未安装，跳过 */ }
		// 1钻石 → 2 alexsmobs:siderite（限购2次）
		try {
			var sideriteOpt = net.minecraft.core.registries.BuiltInRegistries.ITEM
					.getOptional(new net.minecraft.resources.ResourceLocation("alexsmobs:siderite"));
			if (sideriteOpt.isPresent()) {
				offers.add(new net.minecraft.world.item.trading.MerchantOffer(
						new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 1),
						new net.minecraft.world.item.ItemStack(sideriteOpt.get(), 2),
						2, 10, 0.05f));
			}
		} catch (Throwable ignored) { /* Alex's Mobs 未安装，跳过 */ }
		return offers;
	}

	/** 双倍掉落 - 给玩家120秒的 luck 效果 */
	private static void eventDoubleDrop(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.LUCK, 2400, 0, true, true, true)); // 120秒
		player.sendSystemMessage(Component.translatable("randomsurprise.event.double_drop"), false);
	}

	/** 经验雨 - 在玩家周围生成多个经验瓶掉落 */
	private static void eventExpRain(ServerPlayer player) {
		var level = player.level();
		int count = 5 + RANDOM.nextInt(4); // 5~8 个经验瓶
		for (int i = 0; i < count; i++) {
			var stack = new net.minecraft.world.item.ItemStack(
					net.minecraft.world.item.Items.EXPERIENCE_BOTTLE, 1);
			var itemEntity = new net.minecraft.world.entity.item.ItemEntity(level,
					player.getX() + (RANDOM.nextDouble() - 0.5) * 6,
					player.getY() + 5 + RANDOM.nextDouble() * 3,
					player.getZ() + (RANDOM.nextDouble() - 0.5) * 6,
					stack);
			level.addFreshEntity(itemEntity);
		}
		player.sendSystemMessage(Component.translatable("randomsurprise.event.exp_rain"), false);
	}

	// ========== 新增稀有事件 ==========

	/** 流星雨 - 在玩家上方生成多个视觉闪电（不伤害玩家） */
	private static void eventMeteorShower(ServerPlayer player) {
		var level = player.level();
		int count = 6 + RANDOM.nextInt(5); // 6~10 道闪电
		for (int i = 0; i < count; i++) {
			var lightning = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
			if (lightning != null) {
				lightning.setPos(
						player.getX() + (RANDOM.nextDouble() - 0.5) * 30,
						player.getY() + 10 + RANDOM.nextDouble() * 15,
						player.getZ() + (RANDOM.nextDouble() - 0.5) * 30);
				lightning.setVisualOnly(true); // 仅视觉，不造成伤害
				level.addFreshEntity(lightning);
			}
		}
		player.sendSystemMessage(Component.translatable("randomsurprise.event.meteor_shower"), false);
	}

	/** 限时无敌 - 给玩家3秒的抗性等级255（近似无敌） */
	private static void eventInvincibility(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 60, 254, true, true, true)); // 3秒，等级255
		player.sendSystemMessage(Component.translatable("randomsurprise.event.invincibility"), false);
	}

	/** 双倍经验 - 给玩家60秒的 luck 和 hero_of_village 效果 */
	private static void eventDoubleExperience(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.LUCK, 1200, 0, true, true, true)); // 60秒
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE, 1200, 0, true, true, true)); // 60秒
		player.sendSystemMessage(Component.translatable("randomsurprise.event.double_exp"), false);
	}

	// ========== 强力限时武器 ==========

	/** 强力武器 - 给玩家一把下界合金剑 + 力量IV + 速度III，持续60秒 */
	private static void eventStrongWeapon(ServerPlayer player) {
		var sword = new net.minecraft.world.item.ItemStack(
				net.minecraft.world.item.Items.NETHERITE_SWORD, 1);
		player.getInventory().add(sword);
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 1200, 3, true, true, true)); // 60秒，力量IV
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 1200, 2, true, true, true)); // 60秒，速度III
		player.sendSystemMessage(Component.translatable("randomsurprise.event.strong_weapon"), false);
	}

	// ========== 召唤模组敌对生物 ==========

	/** 召唤模组生物 - 从已安装模组中随机召唤一个敌对生物，失败则安静降级 */
	private static void eventSummonModMob(ServerPlayer player) {
		// 复用敌对生物收集逻辑（只召唤模组敌对生物，不召唤被动生物/物品实体）
		var moddedHostile = collectModdedHostileEntities();
		if (moddedHostile.isEmpty()) {
			// 没有模组敌对生物时，降级为召唤原版敌对生物
			eventSpawnHostile(player);
			return;
		}
		var entityType = moddedHostile.get(RANDOM.nextInt(moddedHostile.size()));
		spawnEntityNearPlayer(player, entityType);
		player.sendSystemMessage(Component.translatable("randomsurprise.event.summon_mod_mob"), false);
	}

	// ========== 临时增益事件（30~60s 持续）==========

	/** 仇恨吸引 - 60s 内周围 32 格敌对生物强制锁定玩家 */
	private static void eventTaunt(ServerPlayer player) {
		PlayerBuffs.grantBuff(player, BuffType.TAUNT);
		player.sendSystemMessage(Component.translatable("randomsurprise.event.taunt",
				BuffType.TAUNT.getDurationSeconds()), false);
	}

	/** 过境火焰 - 30s 内走过的位置留下火焰（可造成伤害） */
	private static void eventFireTrail(ServerPlayer player) {
		PlayerBuffs.grantBuff(player, BuffType.FIRE_TRAIL);
		// 给玩家自身火焰免疫，防止烧到自己
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE,
				BuffType.FIRE_TRAIL.getDurationTicks(), 0, true, false, true));
		player.sendSystemMessage(Component.translatable("randomsurprise.event.fire_trail",
				BuffType.FIRE_TRAIL.getDurationSeconds()), false);
	}

	/** 点石成金 - 60s 内挖石类方块额外掉落随机矿物 */
	private static void eventMidasTouch(ServerPlayer player) {
		PlayerBuffs.grantBuff(player, BuffType.MIDAS_TOUCH);
		player.sendSystemMessage(Component.translatable("randomsurprise.event.midas_touch",
				BuffType.MIDAS_TOUCH.getDurationSeconds()), false);
	}

	/** 磁铁吸引 - 60s 内 16 格内物品/经验球自动飞向玩家 */
	private static void eventMagnet(ServerPlayer player) {
		PlayerBuffs.grantBuff(player, BuffType.MAGNET);
		player.sendSystemMessage(Component.translatable("randomsurprise.event.magnet",
				BuffType.MAGNET.getDurationSeconds()), false);
	}

	/** 百花足迹 - 60s 内走过的草地/泥土上自动种花 */
	private static void eventFlowerTrail(ServerPlayer player) {
		PlayerBuffs.grantBuff(player, BuffType.FLOWER_TRAIL);
		player.sendSystemMessage(Component.translatable("randomsurprise.event.flower_trail",
				BuffType.FLOWER_TRAIL.getDurationSeconds()), false);
	}

	/** 雷霆体质 - 30s 内攻击生物 30% 概率召唤闪电 */
	private static void eventStaticField(ServerPlayer player) {
		PlayerBuffs.grantBuff(player, BuffType.STATIC_FIELD);
		player.sendSystemMessage(Component.translatable("randomsurprise.event.static_field",
				BuffType.STATIC_FIELD.getDurationSeconds()), false);
	}

	/** 跳跳人体质 - 30s 内持续弹跳 */
	private static void eventBouncy(ServerPlayer player) {
		PlayerBuffs.grantBuff(player, BuffType.BOUNCY);
		player.sendSystemMessage(Component.translatable("randomsurprise.event.bouncy",
				BuffType.BOUNCY.getDurationSeconds()), false);
	}

	/** 荧光显现 - 30s 内发光可见轮廓 */
	private static void eventGlowing(ServerPlayer player) {
		PlayerBuffs.grantBuff(player, BuffType.GLOWING);
		player.sendSystemMessage(Component.translatable("randomsurprise.event.glowing",
				BuffType.GLOWING.getDurationSeconds()), false);
	}

	/** 冰霜光环 - 30s 内 8 格内生物获得缓慢 II */
	private static void eventFrostAura(ServerPlayer player) {
		PlayerBuffs.grantBuff(player, BuffType.FROST_AURA);
		player.sendSystemMessage(Component.translatable("randomsurprise.event.frost_aura",
				BuffType.FROST_AURA.getDurationSeconds()), false);
	}

	/** 重击 - 60s 内攻击对 4 格内其他生物造成 50% 溅射伤害 */
	private static void eventMightySwing(ServerPlayer player) {
		PlayerBuffs.grantBuff(player, BuffType.MIGHTY_SWING);
		player.sendSystemMessage(Component.translatable("randomsurprise.event.mighty_swing",
				BuffType.MIGHTY_SWING.getDurationSeconds()), false);
	}

	// ========== 袭击事件 ==========

	/**
	 * 混合袭击 - 生成多种原版敌对生物的混合群体
	 * 包含僵尸、骷髅、苦力怕、蜘蛛、女巫、掠夺者、卫道士等
	 */
	private static void eventMixedRaid(ServerPlayer player) {
		var level = player.level();
		// 袭击生物类型池
		var raidMobs = new net.minecraft.world.entity.EntityType[]{
				net.minecraft.world.entity.EntityType.ZOMBIE,
				net.minecraft.world.entity.EntityType.SKELETON,
				net.minecraft.world.entity.EntityType.CREEPER,
				net.minecraft.world.entity.EntityType.SPIDER,
				net.minecraft.world.entity.EntityType.WITCH,
				net.minecraft.world.entity.EntityType.PILLAGER,
				net.minecraft.world.entity.EntityType.VINDICATOR,
				net.minecraft.world.entity.EntityType.HUSK,
				net.minecraft.world.entity.EntityType.STRAY,
				net.minecraft.world.entity.EntityType.CAVE_SPIDER
		};
		// 生成 6~10 只混合敌对生物
		int count = 6 + RANDOM.nextInt(5);
		for (int i = 0; i < count; i++) {
			var type = raidMobs[RANDOM.nextInt(raidMobs.length)];
			spawnEntityNearPlayer(player, type);
		}
		player.sendSystemMessage(Component.translatable("randomsurprise.event.mixed_raid"), false);
	}

	/**
	 * 模组生物袭击 - 从已安装模组中召唤多个敌对生物
	 * 若无模组敌对生物则降级为混合袭击
	 */
	private static void eventModdedRaid(ServerPlayer player) {
		var moddedHostile = collectModdedHostileEntities();
		if (moddedHostile.isEmpty()) {
			// 无模组生物时降级为混合袭击
			eventMixedRaid(player);
			return;
		}
		// 生成 4~7 只模组敌对生物（混合不同种类）
		int count = 4 + RANDOM.nextInt(4);
		for (int i = 0; i < count; i++) {
			var entityType = moddedHostile.get(RANDOM.nextInt(moddedHostile.size()));
			spawnEntityNearPlayer(player, entityType);
		}
		player.sendSystemMessage(Component.translatable("randomsurprise.event.modded_raid"), false);
	}

	/**
	 * Boss级袭击 - 生成强力Boss生物（原版+模组）+ 普通敌对护卫
	 * 使用 BossPool 动态获取Boss列表（包含模组Boss），仅原版Boss时回退到硬编码列表
	 * 护卫数量 = 3 + difficulty/2（上限8），难度来自 BattlefieldManager
	 */
	private static void eventBossRaid(ServerPlayer player) {
		var level = player.level();
		// 1. 通过 BossPool 动态获取Boss列表（难度2，含模组Boss）
		java.util.List<String> bossIdPool;
		try {
			bossIdPool = BossPool.getBossesForDifficulty(2);
		} catch (Throwable t) {
			bossIdPool = java.util.Collections.emptyList();
		}
		// 2. 检查是否有模组Boss（非 minecraft: 命名空间）
		boolean hasModdedBoss = false;
		if (bossIdPool != null) {
			for (String id : bossIdPool) {
				if (!id.startsWith("minecraft:")) {
					hasModdedBoss = true;
					break;
				}
			}
		}
		// 3. 解析为 EntityType 列表；仅原版Boss或池为空时回退到硬编码列表
		var bossMobs = new java.util.ArrayList<net.minecraft.world.entity.EntityType<?>>();
		if (hasModdedBoss && bossIdPool != null) {
			for (String id : bossIdPool) {
				var type = BossPool.resolveEntityType(id);
				if (type != null) bossMobs.add(type);
			}
		}
		if (bossMobs.isEmpty()) {
			// 回退：使用原硬编码列表（适合随机事件的精英原版生物）
			bossMobs.add(net.minecraft.world.entity.EntityType.RAVAGER);
			bossMobs.add(net.minecraft.world.entity.EntityType.EVOKER);
			bossMobs.add(net.minecraft.world.entity.EntityType.WITHER_SKELETON);
			bossMobs.add(net.minecraft.world.entity.EntityType.VINDICATOR);
			bossMobs.add(net.minecraft.world.entity.EntityType.PILLAGER);
		}
		// 4. 生成 1~2 只Boss级生物
		int bossCount = 1 + RANDOM.nextInt(2);
		for (int i = 0; i < bossCount; i++) {
			spawnEntityNearPlayer(player, bossMobs.get(RANDOM.nextInt(bossMobs.size())));
		}
		// 5. 获取难度（从 BattlefieldManager，默认3）
		int difficulty;
		try {
			difficulty = BattlefieldManager.getBattlefieldDifficulty();
			if (difficulty < 1) difficulty = 3;
		} catch (Throwable t) {
			difficulty = 3;
		}
		// 6. 护卫数量 = 3 + difficulty/2，上限8
		int guardCount = Math.min(8, 3 + difficulty / 2);
		var guardMobs = new net.minecraft.world.entity.EntityType[]{
				net.minecraft.world.entity.EntityType.ZOMBIE,
				net.minecraft.world.entity.EntityType.SKELETON,
				net.minecraft.world.entity.EntityType.PILLAGER
		};
		for (int i = 0; i < guardCount; i++) {
			spawnEntityNearPlayer(player, guardMobs[RANDOM.nextInt(guardMobs.length)]);
		}
		player.sendSystemMessage(Component.translatable("randomsurprise.event.boss_raid"), false);
	}

	// ========== 模组入侵事件（42-44）==========

	/**
	 * 暮色森林入侵 - 召唤3-5只暮色森林模组生物
	 * 模组未安装时降级为原版僵尸/骷髅
	 */
	private static void eventTwilightInvasion(ServerPlayer player) {
		String[] tfIds = {
				"twilightforest:redcap", "twilightforest:kobold", "twilightforest:fire_beetle",
				"twilightforest:hedge_spider", "twilightforest:minotaur"
		};
		var available = new java.util.ArrayList<net.minecraft.world.entity.EntityType<?>>();
		var registry = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;
		for (String id : tfIds) {
			registry.getOptional(new net.minecraft.resources.ResourceLocation(id)).ifPresent(available::add);
		}
		int count = 3 + RANDOM.nextInt(3); // 3~5只
		if (available.isEmpty()) {
			// 暮色森林未安装，降级为原版僵尸/骷髅
			for (int i = 0; i < count; i++) {
				spawnEntityNearPlayer(player, RANDOM.nextBoolean()
						? net.minecraft.world.entity.EntityType.ZOMBIE
						: net.minecraft.world.entity.EntityType.SKELETON);
			}
		} else {
			for (int i = 0; i < count; i++) {
				spawnEntityNearPlayer(player, available.get(RANDOM.nextInt(available.size())));
			}
		}
		player.sendSystemMessage(Component.literal("§5§l暮色森林入侵！§r§d来自异界的生物出现了！"), false);
	}

	/**
	 * 灾变裂缝 - 召唤1只灾变模组精英生物
	 * 模组未安装时降级为原版卫道士
	 */
	private static void eventCataclysmRift(ServerPlayer player) {
		String[] cataclysmIds = {
				"cataclysm:ignited_revenant", "cataclysm:ender_guard", "cataclysm:enderseer"
		};
		var available = new java.util.ArrayList<net.minecraft.world.entity.EntityType<?>>();
		var registry = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;
		for (String id : cataclysmIds) {
			registry.getOptional(new net.minecraft.resources.ResourceLocation(id)).ifPresent(available::add);
		}
		if (available.isEmpty()) {
			// 灾变未安装，降级为原版卫道士
			spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.VINDICATOR);
		} else {
			spawnEntityNearPlayer(player, available.get(RANDOM.nextInt(available.size())));
		}
		player.sendSystemMessage(Component.literal("§4§l灾变裂缝开启！§r§c一只精英灾变生物降临了！"), false);
	}

	/**
	 * 变异爆发 - 召唤2-3只变异怪物
	 * 模组未安装时降级为原版僵尸
	 */
	private static void eventMutantOutbreak(ServerPlayer player) {
		String[] mutantIds = {
				"mutantmonsters:mutant_zombie", "mutantmonsters:mutant_skeleton", "mutantmonsters:mutant_creeper"
		};
		var available = new java.util.ArrayList<net.minecraft.world.entity.EntityType<?>>();
		var registry = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;
		for (String id : mutantIds) {
			registry.getOptional(new net.minecraft.resources.ResourceLocation(id)).ifPresent(available::add);
		}
		int count = 2 + RANDOM.nextInt(2); // 2~3只
		if (available.isEmpty()) {
			// 变异怪物未安装，降级为原版僵尸
			for (int i = 0; i < count; i++) {
				spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.ZOMBIE);
			}
		} else {
			for (int i = 0; i < count; i++) {
				spawnEntityNearPlayer(player, available.get(RANDOM.nextInt(available.size())));
			}
		}
		player.sendSystemMessage(Component.literal("§2§l变异爆发！§r§a变异怪物在四周涌现！"), false);
	}

	// ========== 祝福/宝藏事件（45-46）==========

	/**
	 * 凤凰祝福 - 30秒火焰免疫 + 生命恢复II
	 */
	private static void eventPhoenixBlessing(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 600, 0, true, true, true)); // 30秒火焰免疫
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.REGENERATION, 600, 1, true, true, true)); // 30秒生命恢复II
		player.sendSystemMessage(Component.literal("§6§l凤凰祝福降临！30秒火焰免疫与生命恢复"), false);
	}

	/**
	 * 宝藏猎人 - 在玩家30-50格外生成信标+宝箱（含稀有物品）
	 * 失败时降级为空投事件
	 */
	private static void eventTreasureHunt(ServerPlayer player) {
		var level = player.level();
		if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
			eventAirdrop(player);
			return;
		}
		try {
			// 30-50格外随机方向
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			int distance = 30 + RANDOM.nextInt(21); // 30~50
			int dx = (int) Math.round(player.getX() + Math.cos(angle) * distance);
			int dz = (int) Math.round(player.getZ() + Math.sin(angle) * distance);
			// 查找地表Y坐标
			var surfacePos = serverLevel.getHeightmapPos(
					net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
					new net.minecraft.core.BlockPos(dx, 0, dz));
			int bx = surfacePos.getX();
			int by = surfacePos.getY();
			int bz = surfacePos.getZ();
			// 放置 3x3 铁块基座（信标下方）
			for (int ox = -1; ox <= 1; ox++) {
				for (int oz = -1; oz <= 1; oz++) {
					serverLevel.setBlock(new net.minecraft.core.BlockPos(bx + ox, by - 1, bz + oz),
							net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState(), 3);
				}
			}
			// 放置信标
			serverLevel.setBlock(new net.minecraft.core.BlockPos(bx, by, bz),
					net.minecraft.world.level.block.Blocks.BEACON.defaultBlockState(), 3);
			// 在信标旁放置宝箱
			var chestPos = new net.minecraft.core.BlockPos(bx + 1, by, bz);
			serverLevel.setBlock(chestPos,
					net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
			// 填充宝箱稀有物品
			var blockEntity = serverLevel.getBlockEntity(chestPos);
			if (blockEntity instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
				String[] rareItems = {
						"minecraft:diamond", "minecraft:diamond_block", "minecraft:netherite_ingot",
						"minecraft:enchanted_golden_apple", "minecraft:totem_of_undying",
						"minecraft:elytra", "minecraft:nether_star", "minecraft:emerald_block",
						"minecraft:netherite_block", "minecraft:dragon_egg", "minecraft:ancient_debris"
				};
				int itemCount = 2 + RANDOM.nextInt(3); // 2~4件
				for (int i = 0; i < itemCount; i++) {
					String key = rareItems[RANDOM.nextInt(rareItems.length)];
					var item = net.minecraft.core.registries.BuiltInRegistries.ITEM
							.getOptional(new net.minecraft.resources.ResourceLocation(key));
					if (item.isPresent()) {
						int amount = 1 + RANDOM.nextInt(3);
						chest.setItem(i, new net.minecraft.world.item.ItemStack(item.get(), amount));
					}
				}
			}
			player.sendSystemMessage(Component.literal(
					"§e§l宝藏猎人！§r§6在 " + bx + ", " + by + ", " + bz + " 附近发现了信标宝藏！"), false);
		} catch (Throwable t) {
			// 失败则降级为空投
			RandomSurpriseMod.LOGGER.warn("宝藏猎人事件失败，降级为空投: {}", t.getMessage());
			eventAirdrop(player);
		}
	}

	// ========== v2 新增战斗类危险事件（47-58）==========

	/** 47 凋零骷髅头雨 - 在玩家周围生成3-5个凋零骷髅头弹实体 */
	private static void eventWitherSkullRain(ServerPlayer player) {
		var level = player.level();
		int count = 3 + RANDOM.nextInt(3);
		for (int i = 0; i < count; i++) {
			double ox = player.getX() + (RANDOM.nextDouble() - 0.5) * 12;
			double oz = player.getZ() + (RANDOM.nextDouble() - 0.5) * 12;
			double oy = player.getY() + 8 + RANDOM.nextInt(5);
			var skull = net.minecraft.world.entity.EntityType.WITHER_SKULL.create(level);
			if (skull != null) {
				skull.setPos(ox, oy, oz);
				level.addFreshEntity(skull);
			}
		}
		player.sendSystemMessage(Component.literal("§4§l凋零骷髅头雨！§r§c天空降下致命的凋零之首！"), false);
	}

	/** 48 守卫者伏击 - 召唤3-5只守卫者（水下效果，陆地降级为远古守卫者1只） */
	private static void eventGuardianAmbush(ServerPlayer player) {
		int count = 3 + RANDOM.nextInt(3);
		for (int i = 0; i < count; i++) {
			spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.GUARDIAN);
		}
		player.sendSystemMessage(Component.literal("§9§l守卫者伏击！§r§b海底守卫者从四面八方涌来！"), false);
	}

	/** 49 幻翼群袭 - 在玩家头顶召唤4-6只幻翼 */
	private static void eventPhantomSwarm(ServerPlayer player) {
		int count = 4 + RANDOM.nextInt(3);
		var level = player.level();
		for (int i = 0; i < count; i++) {
			double ox = player.getX() + (RANDOM.nextDouble() - 0.5) * 16;
			double oz = player.getZ() + (RANDOM.nextDouble() - 0.5) * 16;
			double oy = player.getY() + 12 + RANDOM.nextInt(8);
			var phantom = net.minecraft.world.entity.EntityType.PHANTOM.create(level);
			if (phantom != null) {
				phantom.setPos(ox, oy, oz);
				level.addFreshEntity(phantom);
			}
		}
		player.sendSystemMessage(Component.literal("§8§l幻翼群袭！§r§7夜空中布满了幻翼的身影！"), false);
	}

	/** 50 恼鬼入侵 - 召唤3-5只恼鬼 */
	private static void eventVexInvasion(ServerPlayer player) {
		int count = 3 + RANDOM.nextInt(3);
		for (int i = 0; i < count; i++) {
			spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.VEX);
		}
		player.sendSystemMessage(Component.literal("§d§l恼鬼入侵！§r§5看不见的小恶魔围绕着你！"), false);
	}

	/** 51 末影人暴走 - 召唤3-4只末影人 */
	private static void eventEndermanFrenzy(ServerPlayer player) {
		int count = 3 + RANDOM.nextInt(2);
		for (int i = 0; i < count; i++) {
			spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.ENDERMAN);
		}
		player.sendSystemMessage(Component.literal("§5§l末影人暴走！§r§d周围的末影人变得躁动不安！"), false);
	}

	/** 52 凋零骷髅小队 - 召唤2-3只凋零骷髅（仅地狱） */
	private static void eventWitherSkeletonPack(ServerPlayer player) {
		int count = 2 + RANDOM.nextInt(2);
		for (int i = 0; i < count; i++) {
			spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.WITHER_SKELETON);
		}
		player.sendSystemMessage(Component.literal("§8§l凋零骷髅小队！§r§7带着凋零之力的骷髅出现！"), false);
	}

	/** 53 猪灵旅团 - 召唤3-5只猪灵（带金装备） */
	private static void eventPiglinBrigade(ServerPlayer player) {
		int count = 3 + RANDOM.nextInt(3);
		for (int i = 0; i < count; i++) {
			spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.PIGLIN);
		}
		player.sendSystemMessage(Component.literal("§6§l猪灵旅团！§r§e一群全副武装的猪灵袭来！"), false);
	}

	/** 54 劫兽冲锋 - 召唤1-2只劫兽 */
	private static void eventRavagerCharge(ServerPlayer player) {
		int count = 1 + RANDOM.nextInt(2);
		for (int i = 0; i < count; i++) {
			spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.RAVAGER);
		}
		player.sendSystemMessage(Component.literal("§4§l劫兽冲锋！§r§c巨大的劫兽朝你冲来！"), false);
	}

	/** 55 蠹虫群涌 - 召唤6-10只蠹虫 */
	private static void eventSilverfishSwarm(ServerPlayer player) {
		int count = 6 + RANDOM.nextInt(5);
		for (int i = 0; i < count; i++) {
			spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.SILVERFISH);
		}
		player.sendSystemMessage(Component.literal("§7§l蠹虫群涌！§r§f石头中钻出了大量蠹虫！"), false);
	}

	/** 56 洞穴蜘蛛伏击 - 召唤3-5只洞穴蜘蛛 */
	private static void eventCaveSpiderAmbush(ServerPlayer player) {
		int count = 3 + RANDOM.nextInt(3);
		for (int i = 0; i < count; i++) {
			spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.CAVE_SPIDER);
		}
		player.sendSystemMessage(Component.literal("§2§l洞穴蜘蛛伏击！§r§a阴暗处的蜘蛛群起而攻！"), false);
	}

	/** 57 僵尸村民群 - 召唤3-5只僵尸村民 */
	private static void eventZombieVillagerHorde(ServerPlayer player) {
		int count = 3 + RANDOM.nextInt(3);
		for (int i = 0; i < count; i++) {
			spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.ZOMBIE_VILLAGER);
		}
		player.sendSystemMessage(Component.literal("§2§l僵尸村民群！§r§a被感染的村民向你扑来！"), false);
	}

	/** 58 末影螨入侵 - 召唤4-6只末影螨 */
	private static void eventEndermiteInvasion(ServerPlayer player) {
		int count = 4 + RANDOM.nextInt(3);
		for (int i = 0; i < count; i++) {
			spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.ENDERMITE);
		}
		player.sendSystemMessage(Component.literal("§d§l末影螨入侵！§r§5空间裂隙中涌出末影螨！"), false);
	}

	// ========== v2 新增环境趣味事件（59-68）==========

	/** 59 时光倒流 - 时间回退到黎明（1000刻） */
	private static void eventTimeRewind(ServerPlayer player) {
		player.level().getServer().getCommands().performPrefixedCommand(
				player.createCommandSourceStack(), "time set 1000");
		player.sendSystemMessage(Component.literal("§e§l时光倒流！§r§6晨光再次降临大地！"), false);
	}

	/** 60 幸运方块 - 在玩家附近生成1个金块+随机幸运奖励 */
	private static void eventLuckyBlock(ServerPlayer player) {
		var level = player.level();
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		int distance = 4 + RANDOM.nextInt(4);
		int bx = (int) Math.round(player.getX() + Math.cos(angle) * distance);
		int bz = (int) Math.round(player.getZ() + Math.sin(angle) * distance);
		int by = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, bx, bz);
		var pos = new BlockPos(bx, by, bz);
		level.setBlock(pos, net.minecraft.world.level.block.Blocks.GOLD_BLOCK.defaultBlockState(), 3);
		// 在金块上方放置1个随机的幸运物品（直接给玩家）
		String[] luckyItems = {
				"minecraft:diamond", "minecraft:emerald", "minecraft:gold_ingot",
				"minecraft:iron_ingot", "minecraft:netherite_scrap", "minecraft:experience_bottle"
		};
		String key = luckyItems[RANDOM.nextInt(luckyItems.length)];
		var itemOpt = net.minecraft.core.registries.BuiltInRegistries.ITEM
				.getOptional(new ResourceLocation(key));
		if (itemOpt.isPresent()) {
			int amount = 2 + RANDOM.nextInt(5);
			player.getInventory().add(new ItemStack(itemOpt.get(), amount));
		}
		player.sendSystemMessage(Component.literal("§e§l幸运方块！§r§6金块出现并带来了好运！"), false);
	}

	/** 61 花海盛宴 - 在玩家周围10x10范围种植随机花朵 */
	private static void eventFlowerField(ServerPlayer player) {
		var level = player.level();
		var flowers = new net.minecraft.world.level.block.state.BlockState[]{
				net.minecraft.world.level.block.Blocks.DANDELION.defaultBlockState(),
				net.minecraft.world.level.block.Blocks.POPPY.defaultBlockState(),
				net.minecraft.world.level.block.Blocks.BLUE_ORCHID.defaultBlockState(),
				net.minecraft.world.level.block.Blocks.ALLIUM.defaultBlockState(),
				net.minecraft.world.level.block.Blocks.AZURE_BLUET.defaultBlockState(),
				net.minecraft.world.level.block.Blocks.RED_TULIP.defaultBlockState(),
				net.minecraft.world.level.block.Blocks.ORANGE_TULIP.defaultBlockState(),
				net.minecraft.world.level.block.Blocks.WHITE_TULIP.defaultBlockState(),
				net.minecraft.world.level.block.Blocks.PINK_TULIP.defaultBlockState(),
				net.minecraft.world.level.block.Blocks.OXEYE_DAISY.defaultBlockState(),
				net.minecraft.world.level.block.Blocks.CORNFLOWER.defaultBlockState(),
				net.minecraft.world.level.block.Blocks.LILY_OF_THE_VALLEY.defaultBlockState()
		};
		int px = player.blockPosition().getX();
		int pz = player.blockPosition().getZ();
		int placed = 0;
		for (int dx = -5; dx <= 5; dx++) {
			for (int dz = -5; dz <= 5; dz++) {
				if (RANDOM.nextDouble() < 0.6) {
					int bx = px + dx;
					int bz = pz + dz;
					int by = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, bx, bz);
					var pos = new BlockPos(bx, by, bz);
					var below = level.getBlockState(pos.below());
					if (below.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) {
						level.setBlock(pos, flowers[RANDOM.nextInt(flowers.length)], 3);
						placed++;
					}
				}
			}
		}
		player.sendSystemMessage(Component.literal("§a§l花海盛宴！§r§2周围绽放了 §e" + placed + " §a朵鲜花！"), false);
	}

	/** 62 冰封大地 - 玩家周围5格范围的水变成冰 */
	private static void eventIceField(ServerPlayer player) {
		var level = player.level();
		int px = player.blockPosition().getX();
		int py = player.blockPosition().getY();
		int pz = player.blockPosition().getZ();
		int frozen = 0;
		for (int dx = -5; dx <= 5; dx++) {
			for (int dy = -2; dy <= 2; dy++) {
				for (int dz = -5; dz <= 5; dz++) {
					var pos = new BlockPos(px + dx, py + dy, pz + dz);
					var state = level.getBlockState(pos);
					if (state.is(net.minecraft.world.level.block.Blocks.WATER)) {
						level.setBlock(pos, net.minecraft.world.level.block.Blocks.ICE.defaultBlockState(), 3);
						frozen++;
					}
				}
			}
		}
		player.sendSystemMessage(Component.literal("§b§l冰封大地！§r§3周围的水凝结成了 §e" + frozen + " §b块冰！"), false);
	}

	/** 63 岩浆涌出 - 在玩家附近3-5格生成小型岩浆池（危险） */
	private static void eventLavaPool(ServerPlayer player) {
		var level = player.level();
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		int distance = 4 + RANDOM.nextInt(3);
		int cx = (int) Math.round(player.getX() + Math.cos(angle) * distance);
		int cz = (int) Math.round(player.getZ() + Math.sin(angle) * distance);
		int cy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, cx, cz);
		// 3x3 岩浆池
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				var pos = new BlockPos(cx + dx, cy, cz + dz);
				level.setBlock(pos, net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState(), 3);
			}
		}
		player.sendSystemMessage(Component.literal("§6§l岩浆涌出！§r§c小心，附近出现了岩浆池！"), false);
	}

	/** 64 降雪 - 改变天气为雪天 */
	private static void eventSnowfall(ServerPlayer player) {
		if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			serverLevel.setWeatherParameters(0, 6000, true, false);
			// 在雪地生物群系会自动降雪
		}
		player.sendSystemMessage(Component.literal("§f§l降雪了！§r§7雪花从天而降。"), false);
	}

	/** 65 双段跳 - 给玩家30秒跳跃提升+缓慢降落 */
	private static void eventDoubleJump(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.JUMP, 600, 3, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.SLOW_FALLING, 600, 0, true, true, true));
		player.sendSystemMessage(Component.literal("§a§l双段跳！§r§2获得30秒跳跃提升IV+缓慢降落！"), false);
	}

	/** 66 夜视祝福 - 给玩家60秒夜视+水下呼吸 */
	private static void eventNightVision(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.NIGHT_VISION, 1200, 0, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.WATER_BREATHING, 1200, 0, true, true, true));
		player.sendSystemMessage(Component.literal("§9§l夜视祝福！§r§b获得60秒夜视+水下呼吸！"), false);
	}

	/** 67 荧光草地 - 在玩家周围种植菌光体/荧光浆果 */
	private static void eventGlowingField(ServerPlayer player) {
		var level = player.level();
		int px = player.blockPosition().getX();
		int pz = player.blockPosition().getZ();
		int placed = 0;
		var blocks = new net.minecraft.world.level.block.Block[]{
				net.minecraft.world.level.block.Blocks.GLOWSTONE,
				net.minecraft.world.level.block.Blocks.SHROOMLIGHT,
				net.minecraft.world.level.block.Blocks.SEA_LANTERN
		};
		for (int i = 0; i < 8; i++) {
			int dx = (RANDOM.nextInt(11) - 5);
			int dz = (RANDOM.nextInt(11) - 5);
			int bx = px + dx;
			int bz = pz + dz;
			int by = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, bx, bz);
			var pos = new BlockPos(bx, by, bz);
			level.setBlock(pos, blocks[RANDOM.nextInt(blocks.length)].defaultBlockState(), 3);
			placed++;
		}
		player.sendSystemMessage(Component.literal("§e§l荧光草地！§r§6周围放置了 §e" + placed + " §6个光源方块！"), false);
	}

	/** 68 水下呼吸 - 60秒水下呼吸+海豚恩惠 */
	private static void eventWaterBreath(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.WATER_BREATHING, 1200, 0, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.DOLPHINS_GRACE, 1200, 0, true, true, true));
		player.sendSystemMessage(Component.literal("§b§l海之恩惠！§r§3获得60秒水下呼吸+海豚恩惠！"), false);
	}

	// ========== v2 新增奖励稀有事件（69-78）==========

	/** 69 附魔装备 - 给玩家一件附魔的钻石装备（随机部位） */
	private static void eventEnchantedGear(ServerPlayer player) {
		var items = new net.minecraft.world.item.Item[]{
				net.minecraft.world.item.Items.DIAMOND_HELMET,
				net.minecraft.world.item.Items.DIAMOND_CHESTPLATE,
				net.minecraft.world.item.Items.DIAMOND_LEGGINGS,
				net.minecraft.world.item.Items.DIAMOND_BOOTS,
				net.minecraft.world.item.Items.DIAMOND_SWORD
		};
		var item = items[RANDOM.nextInt(items.length)];
		var stack = new ItemStack(item);
		// 简单附魔：使用 enchant() 方法（1.20.1 API）
		try {
			// 选择1-2个常见附魔
			String[][] enchTable = {
					{"minecraft:protection", "minecraft:unbreaking"},
					{"minecraft:sharpness", "minecraft:unbreaking"},
					{"minecraft:efficiency", "minecraft:unbreaking"}
			};
			String[] enchPair = enchTable[RANDOM.nextInt(enchTable.length)];
			for (String enchId : enchPair) {
				var enchOpt = net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT
						.getOptional(new ResourceLocation(enchId));
				if (enchOpt.isPresent()) {
					stack.enchant(enchOpt.get(), 1 + RANDOM.nextInt(3));
				}
			}
		} catch (Throwable ignored) {}
		player.getInventory().add(stack);
		player.sendSystemMessage(Component.literal("§d§l附魔装备！§r§5获得一件附魔钻石装备！"), false);
	}

	/** 70 黄金雨 - 在玩家头顶下落8-12个金锭+经验球 */
	private static void eventGoldenShower(ServerPlayer player) {
		var level = player.level();
		int count = 8 + RANDOM.nextInt(5);
		for (int i = 0; i < count; i++) {
			double ox = player.getX() + (RANDOM.nextDouble() - 0.5) * 6;
			double oz = player.getZ() + (RANDOM.nextDouble() - 0.5) * 6;
			double oy = player.getY() + 6 + RANDOM.nextInt(3);
			var item = new net.minecraft.world.entity.item.ItemEntity(
					level, ox, oy, oz, new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 1));
			item.setDeltaMovement(0, -0.2, 0);
			level.addFreshEntity(item);
		}
		// 给经验
		player.giveExperiencePoints(30 + RANDOM.nextInt(50));
		player.sendSystemMessage(Component.literal("§e§l黄金雨！§r§6金锭从天而降！"), false);
	}

	/** 71 稀有宝箱 - 在玩家附近生成1个宝箱（含稀有物品） */
	private static void eventRareChest(ServerPlayer player) {
		var level = player.level();
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		int distance = 5 + RANDOM.nextInt(5);
		int bx = (int) Math.round(player.getX() + Math.cos(angle) * distance);
		int bz = (int) Math.round(player.getZ() + Math.sin(angle) * distance);
		int by = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, bx, bz);
		var chestPos = new BlockPos(bx, by, bz);
		level.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
		var blockEntity = level.getBlockEntity(chestPos);
		if (blockEntity instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
			String[] rareItems = {
					"minecraft:diamond", "minecraft:emerald", "minecraft:gold_ingot",
					"minecraft:iron_ingot", "minecraft:netherite_scrap", "minecraft:experience_bottle",
					"minecraft:golden_apple", "minecraft:enchanted_book"
			};
			int itemCount = 3 + RANDOM.nextInt(4);
			for (int i = 0; i < itemCount; i++) {
				String key = rareItems[RANDOM.nextInt(rareItems.length)];
				var itemOpt = net.minecraft.core.registries.BuiltInRegistries.ITEM
						.getOptional(new ResourceLocation(key));
				if (itemOpt.isPresent()) {
					int amount = 1 + RANDOM.nextInt(4);
					chest.setItem(i, new ItemStack(itemOpt.get(), amount));
				}
			}
		}
		player.sendSystemMessage(Component.literal("§6§l稀有宝箱！§r§e在 " + bx + ", " + by + ", " + bz + " 出现了宝箱！"), false);
	}

	/** 72 经验瓶雨 - 头顶下落8-12个经验瓶 */
	private static void eventExpBottleRain(ServerPlayer player) {
		var level = player.level();
		int count = 8 + RANDOM.nextInt(5);
		for (int i = 0; i < count; i++) {
			double ox = player.getX() + (RANDOM.nextDouble() - 0.5) * 6;
			double oz = player.getZ() + (RANDOM.nextDouble() - 0.5) * 6;
			double oy = player.getY() + 8 + RANDOM.nextInt(3);
			var item = new net.minecraft.world.entity.item.ItemEntity(
					level, ox, oy, oz, new ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE, 1));
			item.setDeltaMovement(0, -0.2, 0);
			level.addFreshEntity(item);
		}
		player.sendSystemMessage(Component.literal("§a§l经验瓶雨！§r§2经验瓶从天而降！"), false);
	}

	/** 73 钻石雨 - 头顶下落3-5个钻石 */
	private static void eventDiamondRain(ServerPlayer player) {
		var level = player.level();
		int count = 3 + RANDOM.nextInt(3);
		for (int i = 0; i < count; i++) {
			double ox = player.getX() + (RANDOM.nextDouble() - 0.5) * 4;
			double oz = player.getZ() + (RANDOM.nextDouble() - 0.5) * 4;
			double oy = player.getY() + 6 + RANDOM.nextInt(3);
			var item = new net.minecraft.world.entity.item.ItemEntity(
					level, ox, oy, oz, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 1));
			item.setDeltaMovement(0, -0.2, 0);
			level.addFreshEntity(item);
		}
		player.sendSystemMessage(Component.literal("§b§l钻石雨！§r§3钻石从天而降！"), false);
	}

	/** 74 免费附魔 - 给玩家手持物品附魔（随机附魔1-2级） */
	private static void eventFreeEnchant(ServerPlayer player) {
		var stack = player.getMainHandItem();
		String[] enchIds = {
				"minecraft:sharpness", "minecraft:protection", "minecraft:efficiency",
				"minecraft:unbreaking", "minecraft:fortune", "minecraft:looting",
				"minecraft:silk_touch", "minecraft:power", "minecraft:punch"
		};
		String enchId = enchIds[RANDOM.nextInt(enchIds.length)];
		var enchOpt = net.minecraft.core.registries.BuiltInRegistries.ENCHANTMENT
				.getOptional(new ResourceLocation(enchId));
		if (enchOpt.isEmpty()) {
			player.sendSystemMessage(Component.literal("§c附魔失败。"), false);
			return;
		}
		int level = 1 + RANDOM.nextInt(3);
		if (stack.isEmpty()) {
			// 没手持物品，改为给一本附魔书（用 EnchantmentHelper.setEnchantments 兼容 1.20.1）
			try {
				var book = new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK);
				var enchantMap = new java.util.HashMap<net.minecraft.world.item.enchantment.Enchantment, Integer>();
				enchantMap.put(enchOpt.get(), level);
				net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(enchantMap, book);
				player.getInventory().add(book);
				player.sendSystemMessage(Component.literal("§d§l免费附魔书！§r§5手持物品为空，获得附魔书！"), false);
			} catch (Throwable t) {
				player.sendSystemMessage(Component.literal("§c附魔书创建失败。"), false);
			}
		} else {
			try {
				stack.enchant(enchOpt.get(), level);
				player.sendSystemMessage(Component.literal("§d§l免费附魔！§r§5手持物品已附魔！"), false);
			} catch (Throwable t) {
				player.sendSystemMessage(Component.literal("§c该物品无法附魔。"), false);
			}
		}
	}

	/** 75 铁砧雨 - 在玩家附近3-5格下落3-4个铁砧（趣味危险） */
	private static void eventAnvilRain(ServerPlayer player) {
		var level = player.level();
		int count = 3 + RANDOM.nextInt(2);
		var anvils = new net.minecraft.world.level.block.Block[]{
				net.minecraft.world.level.block.Blocks.ANVIL,
				net.minecraft.world.level.block.Blocks.CHIPPED_ANVIL,
				net.minecraft.world.level.block.Blocks.DAMAGED_ANVIL
		};
		for (int i = 0; i < count; i++) {
			double ox = player.getX() + (RANDOM.nextDouble() - 0.5) * 8;
			double oz = player.getZ() + (RANDOM.nextDouble() - 0.5) * 8;
			double oy = player.getY() + 10 + RANDOM.nextInt(5);
			var blockPos = new BlockPos((int) ox, (int) oy, (int) oz);
			level.setBlock(blockPos, anvils[RANDOM.nextInt(anvils.length)].defaultBlockState(), 3);
		}
		player.sendSystemMessage(Component.literal("§7§l铁砧雨！§r§f小心头顶落下的铁砧！"), false);
	}

	/** 76 音乐祝福 - 给玩家60秒所有正面效果I（5种） */
	private static void eventMusicalBuff(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 1200, 0, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.DIG_SPEED, 1200, 0, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 1200, 0, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.JUMP, 1200, 0, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.REGENERATION, 1200, 0, true, true, true));
		player.sendSystemMessage(Component.literal("§d§l音乐祝福！§r§5获得60秒五种正面效果I！"), false);
	}

	/** 77 幸运护符 - 给玩家30秒幸运+村庄英雄 */
	private static void eventLuckyCharm(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.LUCK, 600, 0, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE, 600, 0, true, true, true));
		player.sendSystemMessage(Component.literal("§a§l幸运护符！§r§2获得30秒幸运+村庄英雄！"), false);
	}

	/** 78 英雄降临 - 给玩家村庄英雄效果60秒+大量经验 */
	private static void eventHeroVillage(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE, 1200, 1, true, true, true));
		player.giveExperiencePoints(100 + RANDOM.nextInt(100));
		player.sendSystemMessage(Component.literal("§6§l英雄降临！§r§e获得60秒村庄英雄II+经验奖励！"), false);
	}

	// ========== v2 新增祝福增益事件（79-88）==========

	/** 79 神圣护盾 - 30秒抗性提升II+伤害吸收 */
	private static void eventHolyShield(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 600, 1, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.ABSORPTION, 600, 3, true, true, true));
		player.sendSystemMessage(Component.literal("§b§l神圣护盾！§r§3获得30秒抗性II+伤害吸收IV！"), false);
	}

	/** 80 力量涌动 - 30秒力量III+急迫II */
	private static void eventStrengthSurge(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 600, 2, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.DIG_SPEED, 600, 1, true, true, true));
		player.sendSystemMessage(Component.literal("§c§l力量涌动！§r§4获得30秒力量III+急迫II！"), false);
	}

	/** 81 疾风之翼 - 30秒速度III+跳跃提升II+缓慢降落 */
	private static void eventSwiftWind(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 600, 2, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.JUMP, 600, 1, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.SLOW_FALLING, 600, 0, true, true, true));
		player.sendSystemMessage(Component.literal("§a§l疾风之翼！§r§2获得30秒速度III+跳跃II+缓慢降落！"), false);
	}

	/** 82 魔力流动 - 30秒急迫II+水下呼吸+夜视 */
	private static void eventManaFlow(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.DIG_SPEED, 600, 1, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.WATER_BREATHING, 600, 0, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.NIGHT_VISION, 600, 0, true, true, true));
		player.sendSystemMessage(Component.literal("§d§l魔力流动！§r§5获得30秒急迫II+水下呼吸+夜视！"), false);
	}

	/** 83 自然祝福 - 30秒生命恢复II+饱和 */
	private static void eventNatureBlessing(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.REGENERATION, 600, 1, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.SATURATION, 600, 0, true, true, true));
		player.sendSystemMessage(Component.literal("§a§l自然祝福！§r§2获得30秒生命恢复II+饱和！"), false);
	}

	/** 84 火焰免疫套餐 - 30秒火焰免疫+力量I */
	private static void eventFireImmunitySuite(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 600, 0, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 600, 0, true, true, true));
		player.sendSystemMessage(Component.literal("§6§l火焰免疫套餐！§r§e获得30秒火焰免疫+力量I！"), false);
	}

	/** 85 冰霜免疫套餐 - 30秒水下呼吸+冰霜行者（替换靴子） */
	private static void eventFrostImmunitySuite(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.WATER_BREATHING, 600, 0, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.DOLPHINS_GRACE, 600, 0, true, true, true));
		player.sendSystemMessage(Component.literal("§b§l冰霜免疫套餐！§r§3获得30秒水下呼吸+海豚恩惠！"), false);
	}

	/** 86 再生加成 - 30秒生命恢复III+抗性I */
	private static void eventRegenerationBoost(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.REGENERATION, 600, 2, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 600, 0, true, true, true));
		player.sendSystemMessage(Component.literal("§a§l再生加成！§r§2获得30秒生命恢复III+抗性I！"), false);
	}

	/** 87 隐身斗篷 - 30秒隐身+速度II */
	private static void eventInvisibilityCloak(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.INVISIBILITY, 600, 0, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 600, 1, true, true, true));
		player.sendSystemMessage(Component.literal("§7§l隐身斗篷！§r§f获得30秒隐身+速度II！"), false);
	}

	/** 88 急迫光环 - 30秒急迫III+力量I */
	private static void eventHasteAura(ServerPlayer player) {
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.DIG_SPEED, 600, 2, true, true, true));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 600, 0, true, true, true));
		player.sendSystemMessage(Component.literal("§e§l急迫光环！§r§6获得30秒急迫III+力量I！"), false);
	}

	// ========== v2 新增挑战危险事件（89-95）==========

	/** 89 强化头目 - 召唤1只强化掠夺者+1只劫兽 */
	private static void eventMiniBoss(ServerPlayer player) {
		var level = player.level();
		// 强化掠夺者（带力量+抗性）
		var pillager = net.minecraft.world.entity.EntityType.PILLAGER.create(level);
		if (pillager != null) {
			pillager.setPos(player.getX() + 3, player.getY(), player.getZ());
			pillager.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 99999, 1, false, false));
			pillager.addEffect(new net.minecraft.world.effect.MobEffectInstance(
					net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 99999, 1, false, false));
			level.addFreshEntity(pillager);
		}
		// 劫兽
		var ravager = net.minecraft.world.entity.EntityType.RAVAGER.create(level);
		if (ravager != null) {
			ravager.setPos(player.getX() - 3, player.getY(), player.getZ());
			level.addFreshEntity(ravager);
		}
		player.sendSystemMessage(Component.literal("§4§l强化头目！§r§c强化掠夺者骑劫兽出现！"), false);
	}

	/** 90 末影龙之仆 - 召唤3只末影人（带末影龙息粒子+生命加成） */
	private static void eventEnderDragonMinion(ServerPlayer player) {
		var level = player.level();
		for (int i = 0; i < 3; i++) {
			var enderman = net.minecraft.world.entity.EntityType.ENDERMAN.create(level);
			if (enderman != null) {
				double ox = player.getX() + (RANDOM.nextDouble() - 0.5) * 8;
				double oz = player.getZ() + (RANDOM.nextDouble() - 0.5) * 8;
				enderman.setPos(ox, player.getY(), oz);
				// 强化生命
				try {
					var attr = enderman.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
					if (attr != null) {
						attr.setBaseValue(60.0);
						enderman.setHealth(60.0f);
					}
				} catch (Throwable ignored) {}
				level.addFreshEntity(enderman);
			}
		}
		player.sendSystemMessage(Component.literal("§5§l末影龙之仆！§r§d三只强化末影人出现！"), false);
	}

	/** 91 下界入侵 - 召唤3-5只下界生物（猪灵/僵尸猪灵/凋零骷髅） */
	private static void eventNetherInvasion(ServerPlayer player) {
		var types = new net.minecraft.world.entity.EntityType[]{
				net.minecraft.world.entity.EntityType.PIGLIN,
				net.minecraft.world.entity.EntityType.ZOMBIFIED_PIGLIN,
				net.minecraft.world.entity.EntityType.WITHER_SKELETON,
				net.minecraft.world.entity.EntityType.MAGMA_CUBE,
				net.minecraft.world.entity.EntityType.BLAZE
		};
		int count = 3 + RANDOM.nextInt(3);
		for (int i = 0; i < count; i++) {
			spawnEntityNearPlayer(player, types[RANDOM.nextInt(types.length)]);
		}
		player.sendSystemMessage(Component.literal("§6§l下界入侵！§r§c来自地狱的生物涌入！"), false);
	}

	/** 92 女巫集会 - 召唤3只女巫 */
	private static void eventWitchCoven(ServerPlayer player) {
		int count = 3;
		for (int i = 0; i < count; i++) {
			spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.WITCH);
		}
		player.sendSystemMessage(Component.literal("§d§l女巫集会！§r§5三位女巫正商议如何处置你！"), false);
	}

	/** 93 唤魔者袭击 - 召唤1只唤魔者+2只卫道士 */
	private static void eventEvokerRaid(ServerPlayer player) {
		spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.EVOKER);
		for (int i = 0; i < 2; i++) {
			spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.VINDICATOR);
		}
		player.sendSystemMessage(Component.literal("§d§l唤魔者袭击！§r§5唤魔者带领卫道士前来！"), false);
	}

	/** 94 凋零袭击 - 召唤2-3只凋零骷髅 */
	private static void eventWitherSkeletonRaid(ServerPlayer player) {
		int count = 2 + RANDOM.nextInt(2);
		for (int i = 0; i < count; i++) {
			spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.WITHER_SKELETON);
		}
		player.sendSystemMessage(Component.literal("§8§l凋零袭击！§r§7带着凋零之力的骷髅群出现！"), false);
	}

	/** 95 卫道士冲锋 - 召唤3-4只卫道士 */
	private static void eventVindicatorRush(ServerPlayer player) {
		int count = 3 + RANDOM.nextInt(2);
		for (int i = 0; i < count; i++) {
			spawnEntityNearPlayer(player, net.minecraft.world.entity.EntityType.VINDICATOR);
		}
		player.sendSystemMessage(Component.literal("§c§l卫道士冲锋！§r§4手持斧头的卫道士群涌而上！"), false);
	}
}
