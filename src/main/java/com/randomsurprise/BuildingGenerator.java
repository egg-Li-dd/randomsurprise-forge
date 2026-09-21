package com.randomsurprise;

import com.randomsurprise.affix.ModItems;
import com.randomsurprise.building.ModStructureLoot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.tags.BlockTags;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class BuildingGenerator {
	private static final Random RANDOM = new Random();

	// 活跃信标列表：玩家靠近后自动移除
	private static final List<ActiveBeacon> activeBeacons = new CopyOnWriteArrayList<>();

	private static class ActiveBeacon {
		final BlockPos beaconPos;
		final List<BlockPos> baseBlocks;
		final long createdTick;
		final ResourceKey<Level> dimension;

		ActiveBeacon(BlockPos beaconPos, List<BlockPos> baseBlocks, long createdTick, ResourceKey<Level> dimension) {
			this.beaconPos = beaconPos;
			this.baseBlocks = baseBlocks;
			this.createdTick = createdTick;
			this.dimension = dimension;
		}
	}

	/**
	 * 为空投物资生成交贴心标标记
	 * 在箱子旁边放置信标+铁块基座，玩家靠近后自动移除
	 * @param level 世界
	 * @param chestPos 箱子位置
	 */
	public static void spawnAirdropBeacon(ServerLevel level, BlockPos chestPos) {
		// 信标放在箱子旁边1格的地面上
		BlockPos beaconPos = new BlockPos(chestPos.getX() + 1, chestPos.getY(), chestPos.getZ());
		// 放置 3x3 铁块基座（信标下方），激活信标光柱
		List<BlockPos> baseBlocks = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos basePos = new BlockPos(beaconPos.getX() + dx, beaconPos.getY() - 1, beaconPos.getZ() + dz);
				level.setBlockAndUpdate(basePos, Blocks.IRON_BLOCK.defaultBlockState());
				baseBlocks.add(basePos);
			}
		}
		// 清空信标上方方块（确保光柱直通天空），继续向上清空直到天空或达到高度上限
		for (int dy = 1; dy <= 200; dy++) {
			BlockPos above = beaconPos.above(dy);
			if (above.getY() > level.getMaxBuildHeight()) break;
			level.setBlockAndUpdate(above, Blocks.AIR.defaultBlockState());
		}
		// 放置信标
		level.setBlockAndUpdate(beaconPos, Blocks.BEACON.defaultBlockState());
		// 记录到活跃信标列表，待玩家靠近后移除
		activeBeacons.add(new ActiveBeacon(beaconPos, baseBlocks, level.getServer().getTickCount(), level.dimension()));
	}

	// 由 ServerTickEvents.END_SERVER_TICK 调用，每秒检查玩家是否靠近信标
	public static void onServerTick(MinecraftServer server) {
		if (activeBeacons.isEmpty()) return;
		if (server.getTickCount() % 20 != 0) return;
		long currentTime = server.getTickCount();
		List<ActiveBeacon> toRemove = new ArrayList<>();
		for (ActiveBeacon beacon : activeBeacons) {
			boolean shouldRemove = false;
			if (currentTime - beacon.createdTick > 12000) {
				shouldRemove = true;
			} else {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (player.level().dimension() == beacon.dimension) {
						double distSq = player.distanceToSqr(
								beacon.beaconPos.getX() + 0.5,
								beacon.beaconPos.getY(),
								beacon.beaconPos.getZ() + 0.5);
						if (distSq < 64.0) {
							shouldRemove = true;
							break;
						}
					}
				}
			}
			if (shouldRemove) {
				ServerLevel lvl = server.getLevel(beacon.dimension);
				if (lvl != null) {
					lvl.setBlockAndUpdate(beacon.beaconPos, Blocks.AIR.defaultBlockState());
					for (BlockPos basePos : beacon.baseBlocks) {
						lvl.setBlockAndUpdate(basePos, Blocks.AIR.defaultBlockState());
					}
				}
				toRemove.add(beacon);
			}
		}
		activeBeacons.removeAll(toRemove);
	}

	public enum BuildingType {
		TRAP("神秘洞穴"),
		REWARD_HOUSE("神秘小屋"),
		BATTLE_TOWER("神秘高塔"),
		MOD_STRUCTURE("神秘遗迹");

		private final String name;

		BuildingType(String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	public static void generateBuilding(ServerPlayer player, BuildingType type) {
		try {
			ServerLevel serverLevel = (ServerLevel) player.level();
			int distance = 40 + RANDOM.nextInt(31);
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			double x = player.getX() + Math.cos(angle) * distance;
			double z = player.getZ() + Math.sin(angle) * distance;

			int y = findSuitableY(serverLevel, (int) x, (int) z);
			BlockPos centerPos = new BlockPos((int) x, y, (int) z);

			int beaconTopOffset = getBuildingTopOffset(type);
			boolean usedModStructure = false;

			// v17: 优先尝试使用 mod 结构文件（.nbt），原建筑作为保底
			if (type == BuildingType.MOD_STRUCTURE || RANDOM.nextBoolean()) {
				usedModStructure = tryGenerateModStructure(serverLevel, centerPos);
			}

			if (!usedModStructure) {
				// 保底：使用原代码生成建筑
				switch (type) {
					case TRAP -> generateTrapCave(serverLevel, centerPos);
					case REWARD_HOUSE -> generateRewardHouse(serverLevel, centerPos);
					case BATTLE_TOWER -> {
						int towerHeight = generateBattleTower(serverLevel, centerPos);
						beaconTopOffset = towerHeight + 2;
					}
					case MOD_STRUCTURE -> generateRewardHouse(serverLevel, centerPos); // 最终保底
				}
			} else {
				// mod 建筑生成成功，估算高度用于信标放置
				beaconTopOffset = 10; // 默认高度
			}

			clearObstructionsAbove(serverLevel, centerPos, 7, 0, 20);

			String direction = getDirectionHint(angle);
			player.sendSystemMessage(
					net.minecraft.network.chat.Component.translatable("randomsurprise.event.building", type.getName(), distance, direction),
					false);
			player.sendSystemMessage(
					net.minecraft.network.chat.Component.literal("§a坐标: §f" + centerPos.getX() + ", " + centerPos.getY() + ", " + centerPos.getZ()),
					false);

			spawnBeacon(serverLevel, centerPos, beaconTopOffset);
		} catch (Exception e) {
			RandomSurpriseMod.LOGGER.error("[RandomSurprise] generateBuilding 生成建筑异常: {}", e.getMessage(), e);
		}
	}

	/**
	 * 尝试使用 mod 结构文件生成建筑
	 * 支持:
	 *   1. 旧版 schematic 格式 (Width/Height/Length/Blocks/Data)
	 *   2. 现代 .nbt 结构格式 (palette/blocks/size)
	 * @return true 如果成功生成 mod 建筑
	 */
	private static boolean tryGenerateModStructure(ServerLevel level, BlockPos center) {
		File schematicsDir = new File("schematics");
		if (!schematicsDir.exists()) {
			schematicsDir = new File(new File(".").getAbsolutePath(), "schematics");
		}

		File[] nbtFiles = schematicsDir.listFiles((dir, name) -> name.endsWith(".nbt"));
		if (nbtFiles == null || nbtFiles.length == 0) {
			return false; // 无结构文件，使用保底
		}

		File selectedFile = nbtFiles[RANDOM.nextInt(nbtFiles.length)];
		try {
			CompoundTag tag = NbtIo.readCompressed(selectedFile);
			if (tag == null) return false;

			// 检测格式：现代 .nbt 有 palette 字段，旧版有 Blocks 字段
			if (tag.contains("palette")) {
				// 现代 .nbt 结构格式（MC 1.20+）
				placeModernNbt(level, center, tag);
			} else if (tag.contains("Blocks")) {
				// 旧版 schematic 格式
				placeSchematic(level, center, tag);
			} else {
				return false;
			}

			// 在生成的建筑中放置战利品箱子
			ModStructureLoot.ChestTier tier = switch (RANDOM.nextInt(4)) {
				case 0 -> ModStructureLoot.ChestTier.COMMON;
				case 1 -> ModStructureLoot.ChestTier.UNCOMMON;
				case 2 -> ModStructureLoot.ChestTier.RARE;
				default -> ModStructureLoot.ChestTier.LEGENDARY;
			};
			int chestCount = 1 + RANDOM.nextInt(3); // 1-3 个箱子
			ModStructureLoot.placeLootChestsInArea(level, center, 8, chestCount, tier);

			return true;
		} catch (IOException e) {
			RandomSurpriseMod.LOGGER.warn("[RandomSurprise] 加载结构文件失败: {} - {}", selectedFile.getName(), e.getMessage());
			return false;
		}
	}

	/**
	 * 放置现代 .nbt 结构文件（MC 1.20+ palette-based 格式）
	 * 格式:
	 *   size: {x, y, z}
	 *   palette: [{Name: "minecraft:stone", Properties: {...}}, ...]
	 *   blocks: [{pos: {x, y, z}, state: index, nbt: {...}}, ...]
	 *   entities: [...]
	 */
	private static void placeModernNbt(ServerLevel level, BlockPos origin, CompoundTag tag) {
		if (!tag.contains("size") || !tag.contains("palette") || !tag.contains("blocks")) {
			// 格式不完整，回退到旧版
			placeSchematic(level, origin, tag);
			return;
		}

		// 读取尺寸
		net.minecraft.nbt.ListTag sizeList = tag.getList("size", net.minecraft.nbt.Tag.TAG_INT);
		int sizeX = sizeList.getInt(0);
		int sizeY = sizeList.getInt(1);
		int sizeZ = sizeList.getInt(2);

		// 读取 palette（方块状态调色板）
		net.minecraft.nbt.ListTag paletteList = tag.getList("palette", net.minecraft.nbt.Tag.TAG_COMPOUND);
		BlockState[] palette = new BlockState[paletteList.size()];
		for (int i = 0; i < paletteList.size(); i++) {
			CompoundTag entry = paletteList.getCompound(i);
			String blockName = entry.getString("Name");
			var blockOpt = BuiltInRegistries.BLOCK.getOptional(new ResourceLocation(blockName));
			if (blockOpt.isEmpty()) {
				palette[i] = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
				continue;
			}
			BlockState state = blockOpt.get().defaultBlockState();
			// 应用 Properties
			if (entry.contains("Properties")) {
				CompoundTag props = entry.getCompound("Properties");
				for (String key : props.getAllKeys()) {
					String value = props.getString(key);
					try {
						var property = state.getBlock().getStateDefinition().getProperty(key);
						if (property != null) {
							applyProperty(state, property, value);
						}
					} catch (Exception ignored) {}
				}
			}
			palette[i] = state;
		}

		// 读取并放置方块
		net.minecraft.nbt.ListTag blocksList = tag.getList("blocks", net.minecraft.nbt.Tag.TAG_COMPOUND);
		for (int i = 0; i < blocksList.size(); i++) {
			CompoundTag blockEntry = blocksList.getCompound(i);

			// 读取相对位置
			net.minecraft.nbt.ListTag posList = blockEntry.getList("pos", net.minecraft.nbt.Tag.TAG_INT);
			int relX = posList.getInt(0);
			int relY = posList.getInt(1);
			int relZ = posList.getInt(2);

			// 转换为世界坐标（居中放置）
			BlockPos pos = origin.offset(relX - sizeX / 2, relY, relZ - sizeZ / 2);

			// 边界检查
			if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) continue;

			// 读取方块状态索引
			int stateIndex = blockEntry.getInt("state");
			if (stateIndex < 0 || stateIndex >= palette.length) continue;

			BlockState state = palette[stateIndex];
			if (state.isAir()) continue;

			// 放置方块
			level.setBlockAndUpdate(pos, state);

			// 处理方块实体（箱子内容等）
			if (blockEntry.contains("nbt")) {
				CompoundTag nbt = blockEntry.getCompound("nbt");
				var blockEntity = level.getBlockEntity(pos);
				if (blockEntity != null) {
					// 清除原箱子内容，稍后由 ModStructureLoot 填充
					if (blockEntity instanceof ChestBlockEntity) {
						// 不加载原 nbt 中的物品，保持空箱子待 ModStructureLoot 填充
					} else {
						// 非箱子方块实体（如刷怪笼、告示牌等），加载 nbt
						nbt.putInt("x", pos.getX());
						nbt.putInt("y", pos.getY());
						nbt.putInt("z", pos.getZ());
						blockEntity.load(nbt);
					}
				}
			}
		}

		RandomSurpriseMod.LOGGER.info("[RandomSurprise] 成功放置现代 .nbt 结构: {}x{}x{}, 方块数: {}",
				sizeX, sizeY, sizeZ, blocksList.size());
	}

	/** 辅助方法：应用方块状态属性 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static BlockState applyProperty(BlockState state, net.minecraft.world.level.block.state.properties.Property property, String value) {
		var optional = property.getValue(value);
		if (optional.isPresent()) {
			return state.setValue(property, (Comparable) optional.get());
		}
		return state;
	}

	private static int getBuildingTopOffset(BuildingType type) {
		return switch (type) {
			case TRAP -> 3;
			case REWARD_HOUSE -> 6;
			case BATTLE_TOWER -> 28;
			case MOD_STRUCTURE -> 15;
		};
	}

	private static String getDirectionHint(double angle) {
		String[] directions = {"东方", "东南方", "南方", "西南方", "西方", "西北方", "北方", "东北方"};
		int index = (int) ((angle + Math.PI / 8) / (Math.PI / 4)) % 8;
		return directions[index];
	}

	private static void spawnBeacon(ServerLevel level, BlockPos pos, int topOffset) {
		int beaconY = pos.getY() + topOffset + 1;
		BlockPos beaconPos = new BlockPos(pos.getX(), beaconY, pos.getZ());
		// 放置 3x3 铁块基座（信标下方），激活信标光柱
		List<BlockPos> baseBlocks = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos basePos = new BlockPos(pos.getX() + dx, beaconY - 1, pos.getZ() + dz);
				level.setBlockAndUpdate(basePos, Blocks.IRON_BLOCK.defaultBlockState());
				baseBlocks.add(basePos);
			}
		}
		// 清空信标上方方块（确保光柱直通天空），继续向上清空直到天空或达到高度上限
		for (int dy = 1; dy <= 200; dy++) {
			BlockPos above = beaconPos.above(dy);
			if (above.getY() > level.getMaxBuildHeight()) break;
			level.setBlockAndUpdate(above, Blocks.AIR.defaultBlockState());
		}
		// 放置信标
		level.setBlockAndUpdate(beaconPos, Blocks.BEACON.defaultBlockState());
		// 记录到活跃信标列表，待玩家靠近后移除
		activeBeacons.add(new ActiveBeacon(beaconPos, baseBlocks, level.getServer().getTickCount(), level.dimension()));
	}

	private static void clearObstructionsAbove(ServerLevel level, BlockPos center, int radius, int fromY, int toY) {
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				for (int dy = fromY; dy <= toY; dy++) {
					BlockPos pos = center.offset(dx, dy, dz);
					BlockState state = level.getBlockState(pos);
					if (state.is(BlockTags.LEAVES) || state.is(Blocks.SNOW)) {
						level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
	}

	private static int findSuitableY(ServerLevel level, int x, int z) {
		// 使用 heightmap 找到该列最高非空气方块的位置（含流体/树叶），返回 Y 是首个空气方块
		BlockPos heightmapPos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));
		int topY = heightmapPos.getY();
		// 从 heightmap 高度向下查找合适的固体地表（排除流体、树叶、雪等）
		for (int i = topY; i > level.getSeaLevel(); i--) {
			BlockPos pos = new BlockPos(x, i, z);
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) continue;
			// 跳过流体（水、岩浆），避免建筑生成在水底/岩浆湖
			if (!state.getFluidState().isEmpty()) continue;
			// 跳过树叶和雪层，避免建筑生成在树冠/雪堆上
			if (state.is(BlockTags.LEAVES) || state.is(Blocks.SNOW)) continue;
			// 找到固体方块，检查上方两格是否空气
			if (state.isSolid() &&
					level.getBlockState(pos.above()).isAir() &&
					level.getBlockState(pos.above(2)).isAir()) {
				return i + 1;
			}
		}
		return level.getSeaLevel() + 1;
	}

	/**
	 * 生成神秘洞穴（陷阱建筑）- 系统性优化版
	 *
	 * 空间布局（7x7，深6格）：
	 *   入口通道(南) → 缓冲区(苔石) → 陷阱区(5种陷阱随机分布) → 宝箱区(深处平台)
	 *
	 * 5种陷阱类型：
	 *   1. TNT爆炸陷阱（物理触发，压力板→TNT，4秒引信延时）
	 *   2. 箭矢发射陷阱（物理触发，绊线钩→发射器射箭，可盾牌格挡）
	 *   3. 岩浆陷阱（物理触发，活板门下方岩浆，可用水桶灭）
	 *   4. 蜘蛛网困陷（物理触发，压力板+蜘蛛网+TNT组合）
	 *   5. 洞穴蜘蛛刷怪笼（感应触发，玩家靠近自动生成怪物）
	 *
	 * 应对策略：宝箱含水桶(灭岩浆)、剪刀(拆绊线/蜘蛛网)、盾牌(挡箭矢)、打火石(提前引爆TNT)、铁靴子(防护)
	 * 平衡性：每种陷阱都有延时或应对方式，不会秒杀玩家
	 */
	private static void generateTrapCave(ServerLevel level, BlockPos center) {
		int size = 6;
		int floorY = -3;
		int ceilY = 2;

		// 1. 挖掘洞穴空间 + 地板/墙壁铺设
		for (int dx = -size; dx <= size; dx++) {
			for (int dz = -size; dz <= size; dz++) {
				for (int dy = floorY; dy <= ceilY; dy++) {
					BlockPos pos = center.offset(dx, dy, dz);
					if (dy == floorY) {
						level.setBlockAndUpdate(pos, (Math.abs(dx + dz) % 3 == 0)
								? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
								: Blocks.COBBLESTONE.defaultBlockState());
					} else if (dy == ceilY) {
						if (dx == 0 && dz == 0) {
							level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
						} else {
							level.setBlockAndUpdate(pos, Blocks.COBBLESTONE.defaultBlockState());
						}
					} else {
						if (dx == -size || dx == size || dz == -size || dz == size) {
							level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
						} else {
							level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}

		// 2. 入口通道（向南延伸3格，2宽3高）
		for (int dz = size + 1; dz <= size + 3; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = floorY; dy <= floorY + 2; dy++) {
					BlockPos pos = center.offset(dx, dy, dz);
					if (dy == floorY) {
						level.setBlockAndUpdate(pos, Blocks.COBBLESTONE.defaultBlockState());
					} else if (dx == -1 || dx == 1) {
						level.setBlockAndUpdate(pos, Blocks.STONE_BRICKS.defaultBlockState());
					} else {
						level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}

		// 3. 陷阱区（5种陷阱分布在玩家路径上，入口在南+z，宝箱在北-z）
		placeTntTrap(level, center.offset(-3, floorY, 3));
		placeArrowTrap(level, center.offset(3, floorY, 3), Direction.NORTH);
		placeLavaTrap(level, center.offset(-3, floorY, 0));
		placeWebTrap(level, center.offset(3, floorY, 0));
		placeSpawnerTrap(level, center.offset(0, floorY, -3));

		// 4. 宝箱区（深处平台，含应对工具+奖励）
		BlockPos chestPos = center.offset(0, floorY, -5);
		level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
		var chest = level.getBlockEntity(chestPos);
		if (chest instanceof ChestBlockEntity cb) {
			cb.setItem(0, new ItemStack(Items.WATER_BUCKET, 1));
			cb.setItem(1, new ItemStack(Items.SHEARS, 1));
			cb.setItem(2, new ItemStack(Items.FLINT_AND_STEEL, 1));
			cb.setItem(3, new ItemStack(Items.SHIELD, 1));
			cb.setItem(4, new ItemStack(Items.IRON_BOOTS, 1));
			cb.setItem(5, new ItemStack(Items.TORCH, 8));
			cb.setItem(6, new ItemStack(Items.DIAMOND, 1 + RANDOM.nextInt(2)));
			cb.setItem(7, new ItemStack(Items.GOLD_INGOT, 2 + RANDOM.nextInt(3)));
			if (RANDOM.nextBoolean()) {
				cb.setItem(8, new ItemStack(Items.ENDER_PEARL, 1));
			}
		}

		// 5. 装饰火把照明
		level.setBlockAndUpdate(center.offset(-size + 1, floorY + 1, size - 1), Blocks.WALL_TORCH.defaultBlockState()
				.setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.WEST));
		level.setBlockAndUpdate(center.offset(size - 1, floorY + 1, size - 1), Blocks.WALL_TORCH.defaultBlockState()
				.setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.EAST));
	}

	/**
	 * 陷阱1：TNT爆炸陷阱（物理触发型+延时）
	 * 机制：石质压力板→TNT（踩上即激活，4秒原生引信延时）
	 * 伤害：8-12心（爆炸伤害，可被方块阻挡）
	 * 应对：打火石提前引爆后逃跑 / 快速跑开
	 */
	private static void placeTntTrap(ServerLevel level, BlockPos pos) {
		level.setBlockAndUpdate(pos.below(), Blocks.TNT.defaultBlockState());
		level.setBlockAndUpdate(pos, Blocks.STONE_PRESSURE_PLATE.defaultBlockState());
		level.setBlockAndUpdate(pos.east(), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
		level.setBlockAndUpdate(pos.west(), Blocks.MOSSY_COBBLESTONE.defaultBlockState());
	}

	/**
	 * 陷阱2：箭矢发射陷阱（物理触发型）
	 * 机制：绊线钩→发射器（玩家触碰绊线，发射器射出箭矢）
	 * 伤害：2-4心/支（可被盾牌格挡）
	 * 应对：盾牌格挡 / 拆除绊线（剪刀）
	 */
	private static void placeArrowTrap(ServerLevel level, BlockPos pos, Direction facing) {
		BlockPos dispenserPos = pos.offset(facing.getStepX() * 2, 1, facing.getStepZ() * 2);
		level.setBlockAndUpdate(dispenserPos, Blocks.DISPENSER.defaultBlockState()
				.setValue(DispenserBlock.FACING, facing.getOpposite()));
		var be = level.getBlockEntity(dispenserPos);
		if (be instanceof DispenserBlockEntity dbe) {
			for (int i = 0; i < 9; i++) {
				dbe.setItem(i, new ItemStack(Items.ARROW, 64));
			}
		}
		BlockPos hookPos = pos.offset(facing.getStepX(), 1, facing.getStepZ());
		level.setBlockAndUpdate(hookPos, Blocks.TRIPWIRE_HOOK.defaultBlockState()
				.setValue(net.minecraft.world.level.block.TripWireHookBlock.FACING, facing));
		for (int i = 1; i <= 3; i++) {
			BlockPos wirePos = pos.offset(facing.getStepX() * (1 - i), 1, facing.getStepZ() * (1 - i));
			level.setBlockAndUpdate(wirePos, Blocks.TRIPWIRE.defaultBlockState());
		}
		BlockPos hookPos2 = pos.offset(facing.getStepX() * -3, 1, facing.getStepZ() * -3);
		level.setBlockAndUpdate(hookPos2, Blocks.TRIPWIRE_HOOK.defaultBlockState()
				.setValue(net.minecraft.world.level.block.TripWireHookBlock.FACING, facing.getOpposite()));
	}

	/**
	 * 陷阱3：岩浆陷阱（物理触发型）
	 * 机制：木质压力板→活板门（踩上压力板，活板门打开，下方岩浆）
	 * 伤害：持续燃烧伤害（可逃出，可用水桶灭）
	 * 应对：水桶灭火/冲走岩浆 / 快速跳出
	 */
	private static void placeLavaTrap(ServerLevel level, BlockPos pos) {
		for (int dy = 1; dy <= 3; dy++) {
			level.setBlockAndUpdate(pos.below(dy), Blocks.LAVA.defaultBlockState());
		}
		level.setBlockAndUpdate(pos, Blocks.OAK_TRAPDOOR.defaultBlockState());
		BlockPos platePos = pos.east();
		level.setBlockAndUpdate(platePos, Blocks.OAK_PRESSURE_PLATE.defaultBlockState());
		level.setBlockAndUpdate(platePos.below(), Blocks.REDSTONE_WIRE.defaultBlockState());
	}

	/**
	 * 陷阱4：蜘蛛网困陷（物理触发型+组合陷阱）
	 * 机制：压力板+蜘蛛网+TNT（踩上压力板，蜘蛛网困住玩家，上方TNT延时爆炸）
	 * 伤害：TNT爆炸8-12心（蜘蛛网可用剪刀快速拆除逃脱）
	 * 应对：剪刀拆蜘蛛网 / 快速破坏压力板
	 */
	private static void placeWebTrap(ServerLevel level, BlockPos pos) {
		level.setBlockAndUpdate(pos, Blocks.STONE_PRESSURE_PLATE.defaultBlockState());
		level.setBlockAndUpdate(pos.north(), Blocks.COBWEB.defaultBlockState());
		level.setBlockAndUpdate(pos.south(), Blocks.COBWEB.defaultBlockState());
		level.setBlockAndUpdate(pos.east(), Blocks.COBWEB.defaultBlockState());
		level.setBlockAndUpdate(pos.west(), Blocks.COBWEB.defaultBlockState());
		level.setBlockAndUpdate(pos.above(2), Blocks.TNT.defaultBlockState());
		level.setBlockAndUpdate(pos.above(), Blocks.REDSTONE_WIRE.defaultBlockState());
	}

	/**
	 * 陷阱5：洞穴蜘蛛刷怪笼（感应触发型）
	 * 机制：刷怪笼（玩家靠近自动生成洞穴蜘蛛，无需手动触发）
	 * 伤害：洞穴蜘蛛中毒效果（2-4心+持续中毒）
	 * 应对：击杀怪物 / 快速通过 / 铁盔甲防中毒
	 */
	private static void placeSpawnerTrap(ServerLevel level, BlockPos pos) {
		level.setBlockAndUpdate(pos, Blocks.SPAWNER.defaultBlockState());
		var spawner = level.getBlockEntity(pos);
		if (spawner instanceof SpawnerBlockEntity sb) {
			sb.getSpawner().setEntityId(EntityType.CAVE_SPIDER, level, RandomSource.create(), pos);
		}
		level.setBlockAndUpdate(pos.north().above(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
		level.setBlockAndUpdate(pos.south().above(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
		level.setBlockAndUpdate(pos.east().above(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
		level.setBlockAndUpdate(pos.west().above(), Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
	}

	private static void generateRewardHouse(ServerLevel level, BlockPos center) {
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				level.setBlockAndUpdate(center.offset(dx, -1, dz), Blocks.OAK_PLANKS.defaultBlockState());

				if ((dx == -4 || dx == 4 || dz == -4 || dz == 4) && (dx != 0 || dz != 0)) {
					for (int dy = 0; dy <= 3; dy++) {
						level.setBlockAndUpdate(center.offset(dx, dy, dz), Blocks.OAK_LOG.defaultBlockState());
					}
				} else if (dx == -4 || dx == 4 || dz == -4 || dz == 4) {
					for (int dy = 0; dy <= 3; dy++) {
						if (dy == 3) {
							level.setBlockAndUpdate(center.offset(dx, dy, dz), Blocks.OAK_LOG.defaultBlockState());
						} else {
							level.setBlockAndUpdate(center.offset(dx, dy, dz), Blocks.GLASS.defaultBlockState());
						}
					}
				}
			}
		}

		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				level.setBlockAndUpdate(center.offset(dx, 4, dz), Blocks.OAK_PLANKS.defaultBlockState());
			}
		}

		for (int dx = -3; dx <= 3; dx++) {
			level.setBlockAndUpdate(center.offset(dx, 4, -3), Blocks.OAK_STAIRS.defaultBlockState());
			level.setBlockAndUpdate(center.offset(dx, 4, 3), Blocks.OAK_STAIRS.defaultBlockState());
		}
		for (int dz = -3; dz <= 3; dz++) {
			level.setBlockAndUpdate(center.offset(-3, 4, dz), Blocks.OAK_STAIRS.defaultBlockState());
			level.setBlockAndUpdate(center.offset(3, 4, dz), Blocks.OAK_STAIRS.defaultBlockState());
		}

		level.setBlockAndUpdate(center.offset(0, 4, 0), Blocks.OAK_FENCE.defaultBlockState());
		level.setBlockAndUpdate(center.offset(0, 5, 0), Blocks.OAK_FENCE.defaultBlockState());

		level.setBlockAndUpdate(center.offset(0, 0, -4), Blocks.OAK_DOOR.defaultBlockState());
		level.setBlockAndUpdate(center.offset(0, 1, -4), Blocks.OAK_DOOR.defaultBlockState());

		for (int i = 0; i < 4; i++) {
			int dx = -2 + RANDOM.nextInt(5);
			int dz = -2 + RANDOM.nextInt(5);
			int dy = RANDOM.nextInt(3);
			BlockPos torchPos = center.offset(dx, dy, dz);
			if (level.getBlockState(torchPos).isAir()) {
				level.setBlockAndUpdate(torchPos, Blocks.TORCH.defaultBlockState());
			}
		}

		BlockPos chestPos1 = center.offset(2, 0, 2);
		level.setBlockAndUpdate(chestPos1, Blocks.CHEST.defaultBlockState());
		var chest1 = level.getBlockEntity(chestPos1);
		if (chest1 instanceof ChestBlockEntity cb) {
			cb.setItem(0, new ItemStack(Items.DIAMOND, 2 + RANDOM.nextInt(3)));
			cb.setItem(1, new ItemStack(Items.EMERALD, 3 + RANDOM.nextInt(5)));
			cb.setItem(2, new ItemStack(Items.GOLD_INGOT, 8 + RANDOM.nextInt(8)));
			cb.setItem(3, new ItemStack(Items.IRON_INGOT, 16 + RANDOM.nextInt(16)));
			cb.setItem(4, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1));
		}

		BlockPos chestPos2 = center.offset(-2, 0, -2);
		level.setBlockAndUpdate(chestPos2, Blocks.CHEST.defaultBlockState());
		var chest2 = level.getBlockEntity(chestPos2);
		if (chest2 instanceof ChestBlockEntity cb) {
			cb.setItem(0, new ItemStack(Items.NETHERITE_INGOT, 1));
			cb.setItem(1, new ItemStack(Items.ELYTRA, 1));
			cb.setItem(2, new ItemStack(Items.TOTEM_OF_UNDYING, 1));
			cb.setItem(3, new ItemStack(Items.NETHER_STAR, 1));
			cb.setItem(4, new ItemStack(Items.DRAGON_EGG, 1));
			cb.setItem(5, new ItemStack(Items.EXPERIENCE_BOTTLE, 8 + RANDOM.nextInt(8)));
		}

		BlockPos chestPos3 = center.offset(0, 2, 0);
		level.setBlockAndUpdate(chestPos3, Blocks.TRAPPED_CHEST.defaultBlockState());
		var chest3 = level.getBlockEntity(chestPos3);
		if (chest3 instanceof ChestBlockEntity cb) {
			cb.setItem(0, new ItemStack(ModItems.LOTTERY_TICKET.get(), 3 + RANDOM.nextInt(3)));
			cb.setItem(1, new ItemStack(Items.ENCHANTED_BOOK, 2));
			cb.setItem(2, new ItemStack(Items.PURPLE_DYE, 4));
			cb.setItem(3, new ItemStack(Items.BLAZE_ROD, 4));
			cb.setItem(4, new ItemStack(Items.GHAST_TEAR, 2));
		}

		placeEnchantedItem(level, center.offset(0, 1, 2), "minecraft:diamond_sword",
				new String[]{"minecraft:sharpness|5", "minecraft:unbreaking|3", "minecraft:mending|1"});
		placeEnchantedItem(level, center.offset(0, 1, -2), "minecraft:diamond_pickaxe",
				new String[]{"minecraft:efficiency|5", "minecraft:unbreaking|3", "minecraft:fortune|3"});
	}

	private static int generateBattleTower(ServerLevel level, BlockPos center) {
		int towerHeight = 15 + RANDOM.nextInt(10);

		// 基座：5x5圆石，地下1层+地面1层
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = -1; dy <= 0; dy++) {
					level.setBlockAndUpdate(center.offset(dx, dy, dz), Blocks.COBBLESTONE.defaultBlockState());
				}
				// 四面外墙
				if (dx == -2 || dx == 2 || dz == -2 || dz == 2) {
					for (int dy = 1; dy <= towerHeight; dy++) {
						level.setBlockAndUpdate(center.offset(dx, dy, dz), Blocks.COBBLESTONE.defaultBlockState());
					}
				}
			}
		}

		// 清空内部3x3空间（保留中心柱给梯子用）
		for (int dy = 1; dy <= towerHeight; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					// 清空非中心柱的位置
					if (!(dx == 0 && dz == 0)) {
						level.setBlockAndUpdate(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
					}
				}
			}
		}

		// v19: 中心柱作为攀爬通道，从y=1到塔顶全部放梯子
		// 玩家在中心柱内部向上攀爬，每个楼层有开口可以进出
		for (int dy = 1; dy <= towerHeight; dy++) {
			level.setBlockAndUpdate(center.offset(0, dy, 0),
					Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
		}

		// 随机选择一个开口方向（东西南北）
		Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
		Direction exitDir = dirs[RANDOM.nextInt(dirs.length)];
		int exitDx = exitDir.getStepX();
		int exitDz = exitDir.getStepZ();

		// 每隔4层一个楼层：用圆石半砖(上层)作楼板（方便跳跃上去），开口供出入
		for (int floor = 3; floor <= towerHeight; floor += 4) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					// 外墙不替换
					if (dx == -2 || dx == 2 || dz == -2 || dz == 2) continue;
					// 梯子所在中心柱不替换
					if (dx == 0 && dz == 0) continue;

					// v19: 开口方向留空供玩家出入（2格高：floor和floor+1）
					if (dx == exitDx && dz == exitDz) continue;

					// v19: 使用圆石台阶（上半部分）而非下半砖，玩家更容易跳上去
					level.setBlockAndUpdate(center.offset(dx, floor, dz),
							Blocks.COBBLESTONE_SLAB.defaultBlockState()
									.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.SLAB_TYPE,
											net.minecraft.world.level.block.state.properties.SlabType.TOP));
				}
			}

			// 在开口方向留空（不放活板门，避免兼容问题）
			// 开口已通过上面的 continue 跳过楼板放置实现

			// 楼层刷怪笼
			if (RANDOM.nextBoolean()) {
				BlockPos spawnerPos = center.offset(0, floor + 1, 0);
				level.setBlockAndUpdate(spawnerPos, Blocks.SPAWNER.defaultBlockState());
				var spawner = level.getBlockEntity(spawnerPos);
				if (spawner instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity sb) {
					EntityType<?>[] types = {EntityType.SKELETON, EntityType.ZOMBIE, EntityType.SPIDER, EntityType.BLAZE};
					sb.getSpawner().setEntityId(types[RANDOM.nextInt(types.length)], level, RandomSource.create(), spawnerPos);
				}
			}

			// 火把照明
			for (int i = 0; i < 2; i++) {
				int dx = -1 + RANDOM.nextInt(3);
				int dz = -1 + RANDOM.nextInt(3);
				if (dx == 0 && dz == 0) continue; // 避开梯子
				BlockPos torchPos = center.offset(dx, floor + 1, dz);
				if (level.getBlockState(torchPos).isAir()) {
					level.setBlockAndUpdate(torchPos, Blocks.TORCH.defaultBlockState());
				}
			}

			// v19: 每个楼层都放一个箱子（奖励必须在箱子内，不散落）
			BlockPos chestPos = findEmptyPosInFloor(level, center, floor + 1, exitDx, exitDz);
			if (chestPos != null) {
				level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
				var floorChest = level.getBlockEntity(chestPos);
				if (floorChest instanceof ChestBlockEntity cb) {
					cb.setItem(0, new ItemStack(Items.IRON_INGOT, 4 + RANDOM.nextInt(8)));
					cb.setItem(1, new ItemStack(Items.GOLD_INGOT, 2 + RANDOM.nextInt(4)));
					cb.setItem(2, new ItemStack(Items.DIAMOND, 1 + RANDOM.nextInt(2)));
					cb.setItem(3, new ItemStack(Items.POTION, 2));
					if (RANDOM.nextBoolean()) {
						cb.setItem(4, new ItemStack(ModItems.LOTTERY_TICKET.get(), 1 + RANDOM.nextInt(2)));
					}
				}
			}
		}

		// 塔顶封顶
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				level.setBlockAndUpdate(center.offset(dx, towerHeight + 1, dz), Blocks.COBBLESTONE.defaultBlockState());
			}
		}

		// 塔顶奖励箱子（在封顶上方，靠近出口方向）
		BlockPos topChest = center.offset(exitDx, towerHeight + 2, exitDz);
		level.setBlockAndUpdate(topChest, Blocks.CHEST.defaultBlockState());
		var chest = level.getBlockEntity(topChest);
		if (chest instanceof ChestBlockEntity cb) {
			cb.setItem(0, new ItemStack(Items.NETHERITE_SWORD, 1));
			cb.setItem(1, new ItemStack(Items.NETHERITE_PICKAXE, 1));
			cb.setItem(2, new ItemStack(Items.DIAMOND, 5 + RANDOM.nextInt(5)));
			cb.setItem(3, new ItemStack(Items.EMERALD, 8 + RANDOM.nextInt(8)));
			cb.setItem(4, new ItemStack(Items.GOLD_INGOT, 16 + RANDOM.nextInt(16)));
			cb.setItem(5, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 2));
			cb.setItem(6, new ItemStack(Items.TOTEM_OF_UNDYING, 1));
			cb.setItem(7, new ItemStack(Items.EXPERIENCE_BOTTLE, 16 + RANDOM.nextInt(16)));
			cb.setItem(8, new ItemStack(ModItems.LOTTERY_TICKET.get(), 5 + RANDOM.nextInt(5)));
		}

		// 楼层怪物（骷髅）
		for (int i = 0; i < 3; i++) {
			int floor = 3 + RANDOM.nextInt(towerHeight - 3);
			var entity = EntityType.SKELETON.create(level);
			if (entity != null) {
				entity.setPos(center.getX() + RANDOM.nextDouble() * 2 - 1, center.getY() + floor + 1, center.getZ() + RANDOM.nextDouble() * 2 - 1);
				level.addFreshEntity(entity);
			}
		}

		// 顶层Boss：凋灵骷髅
		var boss = EntityType.WITHER_SKELETON.create(level);
		if (boss != null) {
			boss.setPos(center.getX() + exitDx, center.getY() + towerHeight + 2, center.getZ() + exitDz);
			level.addFreshEntity(boss);
		}
		return towerHeight;
	}

	/**
	 * 在楼层内找一个空气位置放箱子（避开梯子和出口方向）
	 */
	private static BlockPos findEmptyPosInFloor(ServerLevel level, BlockPos center, int y, int exitDx, int exitDz) {
		// 优先尝试非出口方向的内圈位置
		int[][] candidates = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
		for (int[] c : candidates) {
			int dx = c[0], dz = c[1];
			if (dx == 0 && dz == 0) continue; // 避开梯子
			BlockPos pos = center.offset(dx, y, dz);
			if (level.getBlockState(pos).isAir()) {
				return pos;
			}
		}
		return null;
	}

	private static void generateModStructure(ServerLevel level, BlockPos center) {
		File schematicsDir = new File("schematics");
		if (!schematicsDir.exists()) {
			schematicsDir = new File(new File(".").getAbsolutePath(), "schematics");
		}

		File[] nbtFiles = schematicsDir.listFiles((dir, name) -> name.endsWith(".nbt"));
		if (nbtFiles == null || nbtFiles.length == 0) {
			generateRewardHouse(level, center);
			return;
		}

		File selectedFile = nbtFiles[RANDOM.nextInt(nbtFiles.length)];
		try {
			Path path = selectedFile.toPath();
			CompoundTag tag = NbtIo.readCompressed(selectedFile);
			if (tag != null) {
				placeSchematic(level, center, tag);
			} else {
				generateRewardHouse(level, center);
			}
		} catch (IOException e) {
			generateRewardHouse(level, center);
		}
	}

	private static void placeSchematic(ServerLevel level, BlockPos origin, CompoundTag tag) {
		if (!tag.contains("Width") || !tag.contains("Height") || !tag.contains("Length") || !tag.contains("Blocks")) return;

		int width = tag.getInt("Width");
		int height = tag.getInt("Height");
		int length = tag.getInt("Length");

		byte[] blocks = tag.getByteArray("Blocks");
		if (blocks.length == 0) return;
		byte[] metadata = tag.getByteArray("Data");
		if (metadata.length == 0) metadata = new byte[blocks.length];

		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				for (int z = 0; z < length; z++) {
					int index = y * width * length + z * width + x;
					if (index >= blocks.length) continue;

					int blockId = blocks[index] & 0xFF;
					if (blockId == 0) continue;

					BlockPos pos = origin.offset(x - width / 2, y, z - length / 2);
					// Bug 7: 放置方块前检查Y坐标边界
					if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) continue;

					var block = BuiltInRegistries.BLOCK.byId(blockId);
					// Bug 6: byId在无效ID时返回AIR而非null
					if (block != Blocks.AIR) {
						// Bug 5: 读取metadata。旧版schematic的metadata在Forge 1.20.1扁平化后无法直接映射，
						// 方向属性需按方块类型分别处理，此处尝试用FACING属性应用方向
						int meta = index < metadata.length ? (metadata[index] & 0xFF) : 0;
						BlockState state = block.defaultBlockState();
						if (meta != 0) {
							try {
								var facingProp = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
								if (state.hasProperty(facingProp)) {
									Direction facing = Direction.from3DDataValue(meta & 3);
									state = state.setValue(facingProp, facing);
								}
							} catch (Exception ignored) {
								// metadata方向映射失败，使用默认状态
							}
						}
						level.setBlockAndUpdate(pos, state);
					}
				}
			}
		}

		net.minecraft.nbt.ListTag tileEntities = tag.getList("TileEntities", net.minecraft.nbt.Tag.TAG_COMPOUND);
		for (int i = 0; i < tileEntities.size(); i++) {
			CompoundTag tileTag = tileEntities.getCompound(i);

			int x = tileTag.getInt("x");
		int y = tileTag.getInt("y");
		int z = tileTag.getInt("z");
			BlockPos pos = origin.offset(x - width / 2, y, z - length / 2);

			net.minecraft.nbt.ListTag items = tileTag.getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND);
			var tileEntity = level.getBlockEntity(pos);
			if (tileEntity instanceof ChestBlockEntity cb) {
				for (int j = 0; j < items.size(); j++) {
					CompoundTag itemTag = items.getCompound(j);

					int slot = itemTag.getInt("Slot");
					String idStr = itemTag.getString("id");
					int count = itemTag.getInt("Count");
					var itemOpt2 = BuiltInRegistries.ITEM.getOptional(new ResourceLocation(idStr));
					if (itemOpt2.isPresent()) {
						cb.setItem(slot, new ItemStack(itemOpt2.get(), Math.max(1, count)));
					}
				}
			}
		}
	}

	private static void placeEnchantedItem(ServerLevel level, BlockPos pos, String itemId, String[] enchantments) {
		var itemOpt = BuiltInRegistries.ITEM.getOptional(new ResourceLocation(itemId));
		if (itemOpt.isEmpty()) return;

		var stack = new ItemStack(itemOpt.get(), 1);

		RegistryAccess registryAccess = level.registryAccess();
		Registry<Enchantment> enchantmentRegistry = registryAccess.registryOrThrow(Registries.ENCHANTMENT);
		{
			for (String enchant : enchantments) {
				String[] parts = enchant.split("\\|");
				if (parts.length == 2) {
					var holderOpt = enchantmentRegistry.getHolder(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, new ResourceLocation(parts[0])));
					if (holderOpt.isPresent()) {
						int enchantLevel = Integer.parseInt(parts[1]);
						stack.enchant(holderOpt.get().value(), enchantLevel);
					}
				}
			}
		}

		level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, stack));
	}
}