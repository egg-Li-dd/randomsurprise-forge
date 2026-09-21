package com.randomsurprise.affix;

import com.randomsurprise.RandomSurpriseMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 怪物清除药水
 * 右键使用：清除玩家附近32格内所有敌对生物
 * 商店售价：15张抽奖券
 */
public class MobClearPotionItem extends Item {

	public MobClearPotionItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		ItemStack stack = player.getItemInHand(usedHand);
		if (level.isClientSide()) {
			return InteractionResultHolder.success(stack);
		}
		ServerPlayer serverPlayer = (ServerPlayer) player;

		AABB area = serverPlayer.getBoundingBox().inflate(32);
		List<Mob> mobs = level.getEntitiesOfClass(Mob.class, area);

		int removed = 0;
		for (Mob mob : mobs) {
			if (!mob.isAlive()) continue;
			if (mob instanceof Enemy || isHostileMob(mob)) {
				if (mob.getType() == EntityType.ENDER_DRAGON || mob.getType() == EntityType.WITHER
						|| mob.getType() == EntityType.ELDER_GUARDIAN) continue;
				mob.discard();
				removed++;
			}
		}

		if (removed == 0) {
			serverPlayer.sendSystemMessage(Component.translatable(
					"mobclear.randomsurprise.no_target"));
			return InteractionResultHolder.fail(stack);
		}

		if (!serverPlayer.getAbilities().instabuild) {
			stack.shrink(1);
		}

		serverPlayer.sendSystemMessage(Component.translatable(
				"mobclear.randomsurprise.success", removed));

		RandomSurpriseMod.LOGGER.info("玩家 {} 使用怪物清除药水，清除了 {} 个敌对生物",
				serverPlayer.getName().getString(), removed);
		return InteractionResultHolder.success(stack);
	}

	private boolean isHostileMob(Mob mob) {
		String typeName = mob.getType().toShortString();
		return typeName.contains("zombie") || typeName.contains("skeleton")
				|| typeName.contains("creeper") || typeName.contains("spider")
				|| typeName.contains("enderman") || typeName.contains("witch")
				|| typeName.contains("phantom") || typeName.contains("pillager")
				|| typeName.contains("ravager") || typeName.contains("vindicator")
				|| typeName.contains("evoker") || typeName.contains("blaze")
				|| typeName.contains("ghast") || typeName.contains("slime")
				|| typeName.contains("magma");
	}
}
