package com.randomsurprise.compat.jade;

import com.randomsurprise.RandomSurpriseMod;
import com.randomsurprise.affix.Affix;
import com.randomsurprise.affix.AffixRegistry;
import com.randomsurprise.client.ClientAffixData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Jade 模组集成插件（Forge 1.20.1 / Jade 11.x）。
 * <p>
 * 通过 {@link WailaPlugin} 注解由 Jade 自动发现，无需手动注册到 Forge 事件总线。
 * 当 Jade 未安装时，本类不会被加载，因此不会影响其他功能。
 * <p>
 * v20修复：
 * - 对准敌对生物（Enemy）时：显示当前生效的全局敌对词条摘要（所有玩家坏词条汇总去重，前4个）
 * - 对准友好生物（牛/猪/羊等）：不显示任何词条信息
 * - 不再错误地显示玩家自己拥有的词条
 */
@WailaPlugin
public class JadePlugin implements IWailaPlugin {

	/** 词条显示组件的唯一标识（同时作为 Jade 配置键） */
	private static final ResourceLocation AFFIX_UID =
			new ResourceLocation(RandomSurpriseMod.MOD_ID + ":mob_affixes");

	@Override
	public void registerClient(IWailaClientRegistration registration) {
		try {
			registration.registerEntityComponent(AffixEntityProvider.INSTANCE, LivingEntity.class);
			RandomSurpriseMod.LOGGER.info("[Jade 兼容] 词条悬浮提示组件已注册");
		} catch (Throwable t) {
			RandomSurpriseMod.LOGGER.warn("[Jade 兼容] 注册失败，已降级: {}", t.getMessage());
		}
	}

	/**
	 * 实体词条信息组件提供者。
	 * 仅对敌对生物（Enemy接口）显示全局生效的敌对词条摘要。
	 */
	public static class AffixEntityProvider implements IEntityComponentProvider {
		public static final AffixEntityProvider INSTANCE = new AffixEntityProvider();

		/** 最多显示的敌对词条数量 */
		private static final int MAX_DISPLAY = 4;

		@Override
		public ResourceLocation getUid() {
			return AFFIX_UID;
		}

		@Override
		public boolean enabledByDefault() {
			return true;
		}

		@Override
		public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
			try {
				net.minecraft.world.entity.Entity entity = accessor.getEntity();
				if (!(entity instanceof LivingEntity target)) return;

				// 只对敌对生物显示词条信息
				// 友好生物（牛/猪/羊/鸡等）和玩家不显示
				if (!(target instanceof Enemy)) return;

				// 收集全局所有玩家的敌对词条（去重）
				Set<String> badAffixIds = new LinkedHashSet<>();
				var allBad = ClientAffixData.getAllPlayerBadAffixes();
				if (allBad != null) {
					for (var pba : allBad) {
						if (pba.affixIds() != null) {
							badAffixIds.addAll(pba.affixIds());
						}
					}
				}

				if (badAffixIds.isEmpty()) return;

				// 显示敌对词条标题
				tooltip.add(Component.literal("\u00A7c\u00A7l敌对词条:")
						.withStyle(style -> style.withColor(0xFF5555)));

				int count = 0;
				for (String affixId : badAffixIds) {
					if (count >= MAX_DISPLAY) break;
					Affix affix = AffixRegistry.getById(affixId);
					if (affix != null && !affix.isGood()) {
						MutableComponent text = Component.translatable(affix.getNameKey())
								.withStyle(style -> style.withColor(affix.getRarity().getColor()));
						tooltip.add(text);
						count++;
					}
				}
				if (badAffixIds.size() > MAX_DISPLAY) {
					int more = badAffixIds.size() - MAX_DISPLAY;
					tooltip.add(Component.literal("\u00A77还有 " + more + " 个词条...")
							.withStyle(style -> style.withColor(0xFFAAAAAA)));
				}
			} catch (Throwable t) {
				RandomSurpriseMod.LOGGER.warn("[Jade 兼容] 追加词条文本异常: {}", t.getMessage());
			}
		}
	}
}
