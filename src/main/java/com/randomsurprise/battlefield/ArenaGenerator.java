package com.randomsurprise.battlefield;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 征召维度竞技场结构生成器
 *
 * 生成 100×100 封闭竞技场：
 *  - 石砖地面（y=64，半径50）
 *  - 石砖基底（y=63，防虚空）
 *  - 四面围墙（y=65~74，高10格）
 *  - 四角塔（5×5，高20）
 *  - 战斗区上方清空（y=65~90）
 *
 * 首次传送时自动生成，使用标记方块避免重复生成。
 */
public class ArenaGenerator {

	private static final int RADIUS = BattlefieldDimension.ARENA_RADIUS;       // 50
	private static final int FLOOR_Y = BattlefieldDimension.ARENA_CENTER_Y;   // 64
	private static final int WALL_HEIGHT = 10;
	private static final int TOWER_HEIGHT = 20;
	/** 生成标记位置（中心点下方，bedrock 标记已生成） */
	private static final BlockPos MARKER_POS = new BlockPos(0, 58, 0);

	/** 地面方块 */
	private static final BlockState STONE_BRICK = Blocks.STONE_BRICKS.defaultBlockState();
	/** 围墙方块（深板岩砖，更坚固视觉效果） */
	private static final BlockState DEEPSLATE_BRICK = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
	/** 标记方块 */
	private static final BlockState MARKER = Blocks.BEDROCK.defaultBlockState();
	/** 基岩（最底层，防挖穿掉虚空） */
	private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
	/** 空气（清空用） */
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	/**
	 * 若竞技场未生成则生成（首次传送时调用）
	 */
	public static void generateIfNotExist(ServerLevel level) {
		if (isGenerated(level)) return;
		long start = System.currentTimeMillis();
		generate(level);
		com.randomsurprise.RandomSurpriseMod.LOGGER.info(
				"[征召战场] 竞技场生成完成，耗时 {}ms", System.currentTimeMillis() - start);
	}

	/** 是否已生成（检查标记方块） */
	private static boolean isGenerated(ServerLevel level) {
		return level.getBlockState(MARKER_POS).is(Blocks.BEDROCK);
	}

	/** 生成完整竞技场 */
	private static void generate(ServerLevel level) {
		// flags=2：仅更新渲染，不触发方块更新（性能优化）
		int flags = 2;

		// 1. 地面 + 基底 + 基岩层（y=62~64，半径100，共3层）
		for (int x = -RADIUS; x <= RADIUS; x++) {
			for (int z = -RADIUS; z <= RADIUS; z++) {
				level.setBlock(new BlockPos(x, FLOOR_Y, z), STONE_BRICK, flags);      // 地面 y=64
				level.setBlock(new BlockPos(x, FLOOR_Y - 1, z), STONE_BRICK, flags);  // 基底 y=63
				level.setBlock(new BlockPos(x, FLOOR_Y - 2, z), BEDROCK, flags);      // 基岩层 y=62（防挖穿掉虚空）
			}
		}

		// 2. 四面围墙（y=65~74，深板岩砖）
		for (int y = FLOOR_Y + 1; y <= FLOOR_Y + WALL_HEIGHT; y++) {
			for (int i = -RADIUS; i <= RADIUS; i++) {
				// 北墙、南墙
				level.setBlock(new BlockPos(i, y, -RADIUS), DEEPSLATE_BRICK, flags);
				level.setBlock(new BlockPos(i, y, RADIUS), DEEPSLATE_BRICK, flags);
				// 东墙、西墙
				level.setBlock(new BlockPos(-RADIUS, y, i), DEEPSLATE_BRICK, flags);
				level.setBlock(new BlockPos(RADIUS, y, i), DEEPSLATE_BRICK, flags);
			}
		}

		// 3. 四角塔（5×5，高20，从y=64到84）
		int[][] corners = {{-RADIUS, -RADIUS}, {RADIUS, -RADIUS}, {-RADIUS, RADIUS}, {RADIUS, RADIUS}};
		for (int[] corner : corners) {
			int cx = corner[0], cz = corner[1];
			// 确定塔的5×5范围（向内延伸）
			int xStart = cx < 0 ? cx : cx - 4;
			int zStart = cz < 0 ? cz : cz - 4;
			for (int y = FLOOR_Y; y <= FLOOR_Y + TOWER_HEIGHT; y++) {
				for (int dx = 0; dx < 5; dx++) {
					for (int dz = 0; dz < 5; dz++) {
						// 仅外圈填充（塔身空心）
						if (dx == 0 || dx == 4 || dz == 0 || dz == 4 || y == FLOOR_Y || y == FLOOR_Y + TOWER_HEIGHT) {
							level.setBlock(new BlockPos(xStart + dx, y, zStart + dz), DEEPSLATE_BRICK, flags);
						}
					}
				}
			}
		}

		// 4. 清空战斗区上方（y=65~90，半径99，避免清到围墙）
		for (int x = -RADIUS + 1; x < RADIUS; x++) {
			for (int z = -RADIUS + 1; z < RADIUS; z++) {
				for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 26; y++) {
					level.setBlock(new BlockPos(x, y, z), AIR, flags);
				}
			}
		}

		// 5. 放置生成标记（bedrock at y=58）
		level.setBlock(MARKER_POS, MARKER, flags);

		// 6. 围墙顶部加一圈平滑石半砖（装饰，可选）
		int topY = FLOOR_Y + WALL_HEIGHT + 1;
		for (int i = -RADIUS; i <= RADIUS; i++) {
			level.setBlock(new BlockPos(i, topY, -RADIUS),
					Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), flags);
			level.setBlock(new BlockPos(i, topY, RADIUS),
					Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), flags);
			level.setBlock(new BlockPos(-RADIUS, topY, i),
					Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), flags);
			level.setBlock(new BlockPos(RADIUS, topY, i),
					Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), flags);
		}

		// 7. 天花板（防止飞行 Boss 如 ghast/phantom/blaze 飞出竞技场）
		// 7a. 围墙上方延伸玻璃墙（y=76~90，外圈用玻璃保持采光，与围墙视觉一致）
		for (int y = FLOOR_Y + WALL_HEIGHT + 2; y <= FLOOR_Y + 26; y++) {
			for (int i = -RADIUS; i <= RADIUS; i++) {
				// 北墙、南墙
				level.setBlock(new BlockPos(i, y, -RADIUS),
						Blocks.GLASS.defaultBlockState(), flags);
				level.setBlock(new BlockPos(i, y, RADIUS),
						Blocks.GLASS.defaultBlockState(), flags);
				// 东墙、西墙
				level.setBlock(new BlockPos(-RADIUS, y, i),
						Blocks.GLASS.defaultBlockState(), flags);
				level.setBlock(new BlockPos(RADIUS, y, i),
						Blocks.GLASS.defaultBlockState(), flags);
			}
		}

		// 7b. 屏障天花板（y=91，不可见但不可穿过，覆盖整个竞技场防止飞越玻璃墙）
		int ceilingY = FLOOR_Y + 27;
		for (int x = -RADIUS; x <= RADIUS; x++) {
			for (int z = -RADIUS; z <= RADIUS; z++) {
				level.setBlock(new BlockPos(x, ceilingY, z),
						Blocks.BARRIER.defaultBlockState(), flags);
			}
		}
	}
}
