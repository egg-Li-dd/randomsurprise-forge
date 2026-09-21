package com.randomsurprise.cleanup;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 掉落物定时清理处理器
 * 每 5 分钟（6000 ticks）自动清理所有维度中的掉落物
 * 仅清理存活超过 30 秒的物品（通过 Entity.tickCount 与物品创建时间对比）
 * 使用 PersistentData 标记物品创建时间
 */
public class ItemCleanupHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("RandomSurprise");
    private static final int CLEANUP_INTERVAL = 6000; // 5 minutes in ticks
    private static final int MIN_AGE_TICKS = 600; // 30 seconds protection
    private static final net.minecraft.resources.ResourceLocation SPAWN_TIME_KEY =
            new net.minecraft.resources.ResourceLocation("randomsurprise", "item_spawn_time");
    private static int cleanupCounter = 0;

    public static void onServerTick(MinecraftServer server) {
        cleanupCounter++;
        if (cleanupCounter < CLEANUP_INTERVAL) return;
        cleanupCounter = 0;

        long currentGameTime = server.overworld().getGameTime();
        int totalRemoved = 0;
        for (ServerLevel level : server.getAllLevels()) {
            List<ItemEntity> toRemove = new ArrayList<>();
            for (var entity : level.getAllEntities()) {
                if (!(entity instanceof ItemEntity item)) continue;

                // 首次遇到时标记创建时间（近似：第一次看到时记录 gameTime）
                if (!item.getPersistentData().contains(SPAWN_TIME_KEY.toString(), 4)) {
                    item.getPersistentData().putLong(SPAWN_TIME_KEY.toString(), currentGameTime);
                    continue; // 刚标记的物品跳过（下次清理时才可能被清理）
                }

                long spawnTime = item.getPersistentData().getLong(SPAWN_TIME_KEY.toString());
                if (currentGameTime - spawnTime >= MIN_AGE_TICKS) {
                    toRemove.add(item);
                }
            }
            for (ItemEntity item : toRemove) {
                item.discard();
            }
            totalRemoved += toRemove.size();
        }

        if (totalRemoved > 0) {
            LOGGER.info("[掉落物清理] 清理了 {} 个掉落物", totalRemoved);
        }
    }
}
