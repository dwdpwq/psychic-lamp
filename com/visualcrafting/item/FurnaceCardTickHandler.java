package com.visualcrafting.item;

import appeng.blockentity.misc.InterfaceBlockEntity;
import com.visualcrafting.event.FurnaceCardExpHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/**
 * Per-server-tick handler that scans all loaded ME Interfaces for installed Furnace Cards
 * and processes furnace recipes through them (auto-smelt/blast).
 * The heavy lifting of recipe matching/processing is delegated to FurnaceCardExpHandler.
 */
public class FurnaceCardTickHandler {

    private static final Map<BlockPos, CooldownData> COOLDOWNS = new HashMap<>();
    private static final int CLEANUP_INTERVAL = 1200;
    private static final int STORAGE_FULL_INTERVAL = 600;

    /** Holds per-interface cooldown tracking. */
    public static class CooldownData {
        long lastProcessTime;
        long storageFullTime;

        CooldownData(long time) {
            this.lastProcessTime = time;
            this.storageFullTime = 0;
        }
    }

    /** Holds a cached recipe match result. */
    public static class RecipeMatch {
        final Object recipe;     // SmeltingRecipe or BlastingRecipe
        final int inputSlotIndex;

        RecipeMatch(Object recipe, int inputSlotIndex) {
            this.recipe = recipe;
            this.inputSlotIndex = inputSlotIndex;
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        long tick = event.getServer().getTickCount();

        for (ServerLevel level : event.getServer().getAllLevels()) {
            processLevel(level, tick);
        }

        if (tick % CLEANUP_INTERVAL == 0) {
            COOLDOWNS.entrySet().removeIf(e -> (tick - e.getValue().lastProcessTime) > CLEANUP_INTERVAL * 2);
        }
    }

    private static void processLevel(ServerLevel level, long tick) {
        for (InterfaceBlockEntity iface : getInterfaceBlockEntities(level)) {
            if (iface.isRemoved()) continue;
            BlockPos pos = iface.getBlockPos();

            // Delegate all processing to FurnaceCardExpHandler
            FurnaceCardExpHandler.processInterface(iface, level, tick);
        }
    }

    /**
     * Finds all ME Interface block entities in the given ServerLevel.
     * Uses ChunkMap to efficiently iterate loaded chunks.
     */
    @SuppressWarnings("unchecked")
    private static List<InterfaceBlockEntity> getInterfaceBlockEntities(ServerLevel level) {
        List<InterfaceBlockEntity> result = new ArrayList<>();
        try {
            var chunkSource = level.getChunkSource();
            var chunkMapField = chunkSource.getClass().getDeclaredField("chunkMap");
            chunkMapField.setAccessible(true);
            var chunkMap = chunkMapField.get(chunkSource);

            var visibleChunkMapField = chunkMap.getClass().getDeclaredField("visibleChunkMap");
            visibleChunkMapField.setAccessible(true);
            var visibleChunkMap = visibleChunkMapField.get(chunkMap);

            if (visibleChunkMap instanceof Map<?, ?> chunkMap2) {
                // visibleChunkMap is a Long2ObjectLinkedOpenHashMap; values() are ChunkHolder
                for (Object holder : chunkMap2.values()) {
                    var getTickingChunk = holder.getClass().getMethod("getTickingChunk");
                    var chunk = getTickingChunk.invoke(holder);
                    if (chunk == null) continue;

                    var getBlockEntities = chunk.getClass().getMethod("getBlockEntities");
                    var beMap = (Map<BlockPos, ?>) getBlockEntities.invoke(chunk);
                    for (Object be : beMap.values()) {
                        if (be instanceof InterfaceBlockEntity iface) {
                            result.add(iface);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fallback: use reflection on chunkMap if direct field access fails
            try {
                var cmField = level.getChunkSource().getClass().getDeclaredField("chunkMap");
                cmField.setAccessible(true);
                var chunkMap = cmField.get(level.getChunkSource());
                var getChunks = chunkMap.getClass().getDeclaredMethod("getChunks");
                getChunks.setAccessible(true);
                for (var chunkHolder : (Iterable<?>) getChunks.invoke(chunkMap)) {
                    var getTickingChunk = chunkHolder.getClass().getMethod("getTickingChunk");
                    var chunk = getTickingChunk.invoke(chunkHolder);
                    if (chunk == null) continue;
                    var getBlockEntities = chunk.getClass().getMethod("getBlockEntities");
                    var beMap = (Map<BlockPos, ?>) getBlockEntities.invoke(chunk);
                    for (Object be : beMap.values()) {
                        if (be instanceof InterfaceBlockEntity iface) {
                            result.add(iface);
                        }
                    }
                }
            } catch (Exception ex) {
                // Both approaches failed, return empty
            }
        }
        return result;
    }

    /** Gets or creates cooldown data for a position. */
    public static CooldownData getCooldown(BlockPos pos, long tick) {
        return COOLDOWNS.computeIfAbsent(pos, k -> new CooldownData(tick));
    }

    /** Marks storage full notification for a position. */
    public static void markStorageFull(BlockPos pos, long tick, boolean full) {
        CooldownData cd = COOLDOWNS.get(pos);
        if (cd != null) {
            cd.storageFullTime = full ? tick : 0;
        }
    }

    public static boolean isStorageFull(BlockPos pos, long tick) {
        CooldownData cd = COOLDOWNS.get(pos);
        return cd != null && cd.storageFullTime > 0 &&
                (tick - cd.storageFullTime) < STORAGE_FULL_INTERVAL;
    }
}
