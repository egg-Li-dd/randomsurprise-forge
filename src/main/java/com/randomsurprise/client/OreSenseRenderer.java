package com.randomsurprise.client;

import com.randomsurprise.RandomSurpriseMod;
import com.randomsurprise.superpower.SuperPower;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * ORE_SENSE 超能力已移除，此渲染器已禁用，不再注册到事件总线。
 * 保留文件以备将来需要时参考。
 */
public class OreSenseRenderer {

	private static final int RANGE = 16;
	private static final float[] HIGHLIGHT_COLOR = {0.0F, 1.0F, 0.0F, 0.3F};

	// ORE_SENSE 超能力已移除，渲染逻辑已禁用
	// @SubscribeEvent
	public static void onRenderLevelStage(RenderLevelStageEvent event) {
		// ORE_SENSE 已移除，不再渲染
	}

	private static List<BlockPos> scanForOres(Minecraft client) {
		List<BlockPos> ores = new ArrayList<>();
		BlockPos playerPos = client.player.blockPosition();

		for (int dx = -RANGE; dx <= RANGE; dx++) {
			for (int dy = -RANGE; dy <= RANGE; dy++) {
				for (int dz = -RANGE; dz <= RANGE; dz++) {
					BlockPos pos = playerPos.offset(dx, dy, dz);
					if (pos.distSqr(playerPos) > RANGE * RANGE) continue;

					BlockState state = client.level.getBlockState(pos);
					if (isOreBlock(state.getBlock())) {
						ores.add(pos);
					}
				}
			}
		}

		return ores;
	}

	private static void renderOreHighlight(PoseStack poseStack, VertexConsumer consumer, BlockPos pos) {
		double x = pos.getX();
		double y = pos.getY();
		double z = pos.getZ();

		float r = HIGHLIGHT_COLOR[0];
		float g = HIGHLIGHT_COLOR[1];
		float b = HIGHLIGHT_COLOR[2];
		float a = HIGHLIGHT_COLOR[3];

		renderLine(consumer, x, y, z, x + 1, y, z, r, g, b, a);
		renderLine(consumer, x + 1, y, z, x + 1, y, z + 1, r, g, b, a);
		renderLine(consumer, x + 1, y, z + 1, x, y, z + 1, r, g, b, a);
		renderLine(consumer, x, y, z + 1, x, y, z, r, g, b, a);

		renderLine(consumer, x, y + 1, z, x + 1, y + 1, z, r, g, b, a);
		renderLine(consumer, x + 1, y + 1, z, x + 1, y + 1, z + 1, r, g, b, a);
		renderLine(consumer, x + 1, y + 1, z + 1, x, y + 1, z + 1, r, g, b, a);
		renderLine(consumer, x, y + 1, z + 1, x, y + 1, z, r, g, b, a);

		renderLine(consumer, x, y, z, x, y + 1, z, r, g, b, a);
		renderLine(consumer, x + 1, y, z, x + 1, y + 1, z, r, g, b, a);
		renderLine(consumer, x + 1, y, z + 1, x + 1, y + 1, z + 1, r, g, b, a);
		renderLine(consumer, x, y, z + 1, x, y + 1, z + 1, r, g, b, a);
	}

	private static void renderLine(VertexConsumer consumer, double x1, double y1, double z1,
			double x2, double y2, double z2, float r, float g, float b, float a) {
		consumer.vertex(x1, y1, z1).color(r, g, b, a).endVertex();
		consumer.vertex(x2, y2, z2).color(r, g, b, a).endVertex();
	}

	private static boolean isOreBlock(Block block) {
		BlockState state = block.defaultBlockState();
		if (state.is(net.minecraft.tags.BlockTags.DIAMOND_ORES)) return true;
		if (state.is(net.minecraft.tags.BlockTags.EMERALD_ORES)) return true;
		if (state.is(net.minecraft.tags.BlockTags.GOLD_ORES)) return true;
		if (state.is(net.minecraft.tags.BlockTags.IRON_ORES)) return true;
		if (state.is(net.minecraft.tags.BlockTags.LAPIS_ORES)) return true;
		if (state.is(net.minecraft.tags.BlockTags.REDSTONE_ORES)) return true;
		if (state.is(net.minecraft.tags.BlockTags.COPPER_ORES)) return true;
		if (state.is(net.minecraft.tags.BlockTags.COAL_ORES)) return true;
		return false;
	}
}