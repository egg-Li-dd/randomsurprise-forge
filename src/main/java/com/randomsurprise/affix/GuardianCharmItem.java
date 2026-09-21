package com.randomsurprise.affix;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 守护护符
 * 携带在背包中时，受到致命伤害自动消耗，恢复生命值
 * 1秒冷却（防止连续触发）
 */
public class GuardianCharmItem extends Item {

	/** 冷却记录：玩家UUID → 上次触发的游戏时间 */
	private static final Map<UUID, Long> cooldowns = new HashMap<>();
	/** 1秒冷却 = 20 ticks */
	private static final long COOLDOWN_TICKS = 20L;

	public GuardianCharmItem(Properties properties) {
		super(properties);
	}

	/**
	 * 处理致命伤害事件
	 * 检查玩家背包中是否有守护护符，如果有且不在冷却中，消耗护符并恢复生命值
	 * @return true 允许死亡，false 阻止死亡
	 */
	public static boolean handleFatalDamage(ServerPlayer player) {
		// 检查冷却
		long currentTick = player.level().getGameTime();
		Long lastUsed = cooldowns.get(player.getUUID());
		if (lastUsed != null && currentTick - lastUsed < COOLDOWN_TICKS) {
			return true; // 在冷却中，允许死亡
		}

		// 检查背包中是否有守护护符
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ModItems.GUARDIAN_CHARM.get())) {
				// 消耗护符
				stack.shrink(1);
				// 恢复全部生命值
				player.setHealth(player.getMaxHealth());
				// 清除负面效果
				player.removeEffect(MobEffects.POISON);
				player.removeEffect(MobEffects.WITHER);
				player.removeEffect(MobEffects.WEAKNESS);
				player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
				player.removeEffect(MobEffects.DIG_SLOWDOWN);
				player.removeEffect(MobEffects.BLINDNESS);
				player.removeEffect(MobEffects.HUNGER);
				player.removeEffect(MobEffects.CONFUSION);
				// 给予 3 秒抗性 V（近似无敌但不能永久）
				player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 4));
				// 设置冷却
				cooldowns.put(player.getUUID(), currentTick);
				// 通知玩家
				player.sendSystemMessage(Component.translatable("guardian_charm.randomsurprise.activated"));
				return false; // 阻止死亡
			}
		}
		return true; // 没有护符，允许死亡
	}
}
