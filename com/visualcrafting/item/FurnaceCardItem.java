package com.visualcrafting.item;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.helpers.InterfaceLogic;
import appeng.items.materials.UpgradeCardItem;
import appeng.util.ConfigInventory;
import com.visualcrafting.fluid.ExperienceFluidHelper;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FurnaceCardItem extends UpgradeCardItem {
    public static FurnaceCardItem FURNACE_CARD_TIER1;
    public static FurnaceCardItem FURNACE_CARD_TIER2;
    public static FurnaceCardItem FURNACE_CARD_TIER3;

    private static final long[] MAX_EXP_STORAGE = {0L, 255532000L, 1098668000L, Long.MAX_VALUE};
    private static final double[] ENERGY_COST = {0.0, 45000.0, 85000.0, 165000.0};
    private static final double[] PROCESS_RATIO = {0.0, 0.3, 0.6, 1.0};

    private final int tier;

    public FurnaceCardItem(Item.Properties properties, int tier) {
        super(properties);
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }

    public int getSmeltSpeed() {
        return switch (tier) {
            case 1 -> 15;
            case 2 -> 10;
            case 3 -> 2;
            default -> 20;
        };
    }

    public long getMaxExpStorage() {
        return MAX_EXP_STORAGE[tier];
    }

    public double getEnergyCost() {
        return ENERGY_COST[tier];
    }

    public double getProcessRatio() {
        return PROCESS_RATIO[tier];
    }

    public static long getStoredExpMilli(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getLong("vc_stored_exp");
    }

    public static void setStoredExpMilli(ItemStack stack, long value) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (value <= 0L) {
            tag.remove("vc_stored_exp");
        } else {
            tag.putLong("vc_stored_exp", value);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int getMaxExperienceLevels(int tier) {
        return switch (tier) {
            case 1 -> 256;
            case 2 -> 512;
            case 3 -> 1024;
            default -> 256;
        };
    }

    public float getBatchPercent() {
        return switch (tier) {
            case 1 -> 0.3f;
            case 2 -> 0.6f;
            case 3 -> 1.0f;
            default -> 0.3f;
        };
    }

    public static int getExperiencePointsForLevel(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        }
        if (level <= 31) {
            return (int) (2.5 * level * level - 40.5 * level + 360.0);
        }
        return (int) (4.5 * level * level - 162.5 * level + 2220.0);
    }

    public int getMaxExperiencePoints() {
        return getExperiencePointsForLevel(getMaxExperienceLevels(tier));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.visualcrafting.furnace_card.tooltip.speed", getSmeltSpeed()));
        tooltip.add(Component.translatable("item.visualcrafting.furnace_card.tooltip.tier", tier));
    }

    @EventBusSubscriber(modid = "visualcrafting")
    public static class TickHandler {
        private static final Map<BlockPos, CooldownData> COOLDOWNS = new HashMap<>();
        private static final int CLEANUP_INTERVAL = 1200;
        private static final int STORAGE_FULL_INTERVAL = 60;
        private static final Logger LOGGER = LoggerFactory.getLogger(FurnaceCardItem.class);

        private static boolean debugFirstTick = true;
        private static long lastChunkMapErrorLogTime = 0L;
        private static final Map<ResourceLocation, Set<BlockPos>> knownInterfaces = new HashMap<>();
        private static long lastRescanTick = -1L;
        private static final long RESCAN_INTERVAL = 100L;

        private static List<RecipeHolder<SmeltingRecipe>> cachedSmeltingRecipes = null;
        private static List<RecipeHolder<BlastingRecipe>> cachedBlastingRecipes = null;
        private static long lastRecipeRefreshTick = -1L;
        private static final long RECIPE_REFRESH_INTERVAL = 100L;

        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post event) {
            MinecraftServer server = event.getServer();
            long tick = server.getTickCount();

            if (debugFirstTick) {
                debugFirstTick = false;
                int levelCount = 0;
                for (ServerLevel ignored : server.getAllLevels()) {
                    levelCount++;
                }
                LOGGER.debug("[VC:FurnaceCard] onServerTick.Post FIRED. Server tick={}, levels={}", tick, levelCount);
            }

            for (ServerLevel level : server.getAllLevels()) {
                processLevel(level, tick);
            }

            if (tick % CLEANUP_INTERVAL == 0L) {
                COOLDOWNS.entrySet().removeIf(entry -> entry.getValue().lastSeen < tick - CLEANUP_INTERVAL);
            }
        }

        private static void processLevel(ServerLevel level, long tick) {
            try {
                ResourceLocation dimId = level.dimension().location();
                Set<BlockPos> interfaces = knownInterfaces.get(dimId);
                boolean needsRescan = interfaces == null || tick - lastRescanTick >= RESCAN_INTERVAL;

                if (needsRescan) {
                    Set<BlockPos> found = new HashSet<>();
                    ChunkMap chunkMap = level.getChunkSource().chunkMap;
                    if (chunkMap == null) {
                        long now = System.currentTimeMillis();
                        if (now - lastChunkMapErrorLogTime > 10000L) {
                            LOGGER.debug("[VC:FurnaceCard] processLevel: chunkMap is null {}", dimId);
                            lastChunkMapErrorLogTime = now;
                        }
                        return;
                    }

                    try {
                        Field field = ChunkMap.class.getDeclaredField("updatingChunkMap");
                        field.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap =
                                (Long2ObjectLinkedOpenHashMap<ChunkHolder>) field.get(chunkMap);

                        for (ChunkHolder holder : updatingChunkMap.values()) {
                            LevelChunk chunk = holder.getTickingChunk();
                            if (chunk == null) continue;
                            for (BlockEntity be : chunk.getBlockEntities().values()) {
                                if (be instanceof InterfaceBlockEntity iface && !be.isRemoved()) {
                                    found.add(be.getBlockPos());
                                    tickInterface(iface, level, tick);
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.debug("[VC:FurnaceCard] processLevel: reflection failed for {}", dimId, e);
                        return;
                    }

                    knownInterfaces.put(dimId, found);
                    lastRescanTick = tick;
                    refreshRecipeCache(level);
                } else {
                    Iterator<BlockPos> it = interfaces.iterator();
                    while (it.hasNext()) {
                        BlockPos pos = it.next();
                        BlockEntity be = level.getBlockEntity(pos);
                        if (be instanceof InterfaceBlockEntity iface && !be.isRemoved()) {
                            tickInterface(iface, level, tick);
                        } else if (be == null || !(be instanceof InterfaceBlockEntity)) {
                            it.remove();
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("[VC:FurnaceCard] processLevel error", e);
            }
        }

        private static void tickInterface(InterfaceBlockEntity iface, ServerLevel level, long tick) {
            BlockPos pos = iface.getBlockPos();
            IUpgradeInventory upgrades = iface.getUpgrades();

            FurnaceCardItem bestCard = null;
            int bestSlot = -1;
            int upgradeSize = upgrades.size();

            for (int i = 0; i < upgradeSize; i++) {
                ItemStack stack = upgrades.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                if (stack.getItem() instanceof FurnaceCardItem card) {
                    if (bestCard == null || card.tier > bestCard.tier) {
                        bestCard = card;
                        bestSlot = i;
                    }
                    if (tick % 200L == 0L) {
                        LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} slot[{}] furnace card tier={}",
                                pos, i, card.tier);
                    }
                } else if (tick % 200L == 0L) {
                    LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} slot[{}] item={} (NOT furnace card)",
                            pos, i, stack.getItem().getClass().getName());
                }
            }

            if (bestCard == null) {
                if (tick % 200L == 0L) {
                    LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} NO FURNACE CARD in upgrades (upgrades.size={}), returning",
                            pos, upgradeSize);
                }
                COOLDOWNS.remove(pos);
                return;
            }

            if (tick % 200L == 0L) {
                LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} best card tier={} slot={}",
                        pos, bestCard.tier, bestSlot);
            }

            CooldownData cd = COOLDOWNS.computeIfAbsent(pos, bp -> new CooldownData());
            cd.lastSeen = tick;

            int smeltSpeed = bestCard.getSmeltSpeed();
            if (tick - cd.lastProcessed < smeltSpeed) {
                if (tick % 200L == 0L) {
                    LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} COOLDOWN skip: tick={} lastProcessed={} interval={}",
                            pos, tick, cd.lastProcessed, smeltSpeed);
                }
                return;
            }

            if (iface.getMainNode() == null || !iface.getMainNode().isActive()) {
                if (tick % 200L == 0L) {
                    LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} node==null or NOT ACTIVE", pos);
                }
                return;
            }

            IGrid grid = iface.getMainNode().getGrid();
            if (grid == null) {
                if (tick % 200L == 0L) {
                    LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} grid==null", pos);
                }
                return;
            }

            IStorageService storageService = grid.getStorageService();
            MEStorage networkStorage = storageService.getInventory();
            if (networkStorage == null) {
                if (tick % 200L == 0L) {
                    LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} networkStorage==null, skipping", pos);
                }
                return;
            }

            IEnergyService energyService = grid.getEnergyService();
            double energyCost = bestCard.getEnergyCost();

            InterfaceLogic interfaceLogic = iface.getInterfaceLogic();
            ConfigInventory config = interfaceLogic.getConfig();
            ConfigInventory storage = interfaceLogic.getStorage();

            if (tick % 200L == 0L) {
                long nonEmpty = 0L;
                for (int i = 0; i < storage.size(); i++) {
                    if (storage.getKey(i) != null) nonEmpty++;
                }
                LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} config slots={} internalInv slots={} internalInv nonempty={} PASSED all checks",
                        pos, config.size(), storage.size(), nonEmpty);
            }

            List<RecipeHolder<SmeltingRecipe>> smeltingRecipes = cachedSmeltingRecipes;
            List<RecipeHolder<BlastingRecipe>> blastingRecipes = cachedBlastingRecipes;
            if (smeltingRecipes == null || blastingRecipes == null) {
                RecipeManager recipeManager = level.getServer().getRecipeManager();
                smeltingRecipes = recipeManager.getAllRecipesFor(RecipeType.SMELTING);
                blastingRecipes = recipeManager.getAllRecipesFor(RecipeType.BLASTING);
            }

            if (tick % 200L == 0L) {
                LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} smelting recipes={} blasting recipes={}",
                        pos, smeltingRecipes.size(), blastingRecipes.size());
            }

            Map<AEItemKey, RecipeMatch> recipeMatches = new HashMap<>();
            for (int i = 0; i < config.size(); i++) {
                AEKey key = config.getKey(i);
                if (!(key instanceof AEItemKey itemKey)) {
                    if (tick % 200L == 0L && key != null) {
                        LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} config[{}] not AEItemKey: {}",
                                pos, i, key.getClass().getName());
                    }
                    continue;
                }

                SmeltingRecipe smeltRecipe = findSmelting(itemKey, smeltingRecipes);
                if (smeltRecipe == null) {
                    smeltRecipe = findBlasting(itemKey, blastingRecipes);
                }
                if (smeltRecipe == null) {
                    if (tick % 200L == 0L) {
                        LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} config[{}] no recipe for {}",
                                pos, i, itemKey);
                    }
                    continue;
                }

                ItemStack resultStack = smeltRecipe.getResultItem(level.registryAccess());
                AEItemKey resultKey = AEItemKey.of(resultStack);
                long resultCount = resultStack.getCount();
                float exp = smeltRecipe.getExperience();
                recipeMatches.put(itemKey, new RecipeMatch(resultKey, resultCount, exp));

                if (tick % 200L == 0L) {
                    LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} config[{}] recipe found: input={} resultKey={} resultCount={} exp={}",
                            pos, i, itemKey, resultKey, resultCount, exp);
                }
            }

            if (recipeMatches.isEmpty()) {
                if (tick % 200L == 0L) {
                    LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} no valid recipes from config, returning", pos);
                }
                return;
            }

            ItemStack cardStack = upgrades.getStackInSlot(bestSlot);
            long storedExp = getStoredExpMilli(cardStack);
            long maxExp = bestCard.getMaxExpStorage();
            double processRatio = bestCard.getProcessRatio();

            int totalProcessed = 0;
            boolean didWork = false;
            float totalExp = 0.0f;
            IActionSource actionSource = IActionSource.ofMachine((IActionHost) iface);

            for (int i = 0; i < config.size(); i++) {
                AEKey key = config.getKey(i);
                if (!(key instanceof AEItemKey itemKey)) continue;
                RecipeMatch match = recipeMatches.get(itemKey);
                if (match == null) continue;

                long totalInput = 0L;
                List<int[]> extractSlots = new ArrayList<>();
                for (int j = 0; j < storage.size(); j++) {
                    AEKey storageKey = storage.getKey(j);
                    if (!itemKey.equals(storageKey)) continue;
                    long amount = storage.getAmount(j);
                    if (amount <= 0L) continue;
                    totalInput += amount;
                    extractSlots.add(new int[]{j, (int) Math.min(amount, Integer.MAX_VALUE)});
                }
                if (totalInput == 0L) continue;

                int batchSize = Math.max(1, (int) Math.ceil(totalInput * processRatio));
                if (batchSize > totalInput) batchSize = (int) totalInput;
                if (batchSize <= 0) continue;

                if (tick % 200L == 0L) {
                    LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} config[{}] {} total={} ratio={} batch={} -> {}",
                            pos, i, itemKey, totalInput, processRatio, batchSize, match.resultKey);
                }

                double available = energyService.extractAEPower(energyCost, Actionable.SIMULATE, PowerMultiplier.CONFIG);
                if (available < energyCost) {
                    if (tick % 200L == 0L) {
                        LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} ENERGY insufficient, stopping", pos);
                    }
                    break;
                }

                long remaining = batchSize;
                List<long[]> extractions = new ArrayList<>();
                for (int[] slotInfo : extractSlots) {
                    if (remaining <= 0L) break;
                    int slotIdx = slotInfo[0];
                    long maxExtract = Math.min(remaining, slotInfo[1]);
                    long extracted = storage.extract(slotIdx, itemKey, maxExtract, Actionable.SIMULATE);
                    if (extracted <= 0L) continue;
                    extractions.add(new long[]{slotIdx, extracted});
                    remaining -= extracted;
                }

                if (remaining > 0L) {
                    if (tick % 200L == 0L) {
                        LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} config[{}] SIMULATE extract short by {}, skipping",
                                pos, i, remaining);
                    }
                    continue;
                }

                long canInsert = networkStorage.insert(match.resultKey, batchSize, Actionable.SIMULATE, actionSource);
                if (canInsert < batchSize) {
                    if (tick % 200L == 0L) {
                        LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} config[{}] network full (need={} got={}), skipping",
                                pos, i, batchSize, canInsert);
                    }
                    continue;
                }

                // Commit
                energyService.extractAEPower(energyCost, Actionable.MODULATE, PowerMultiplier.CONFIG);
                for (long[] ext : extractions) {
                    storage.extract((int) ext[0], itemKey, ext[1], Actionable.MODULATE);
                }
                networkStorage.insert(match.resultKey, batchSize, Actionable.MODULATE, actionSource);

                totalProcessed += batchSize;
                didWork = true;
                if (match.experience > 0.0f) {
                    totalExp += match.experience * batchSize;
                }

                if (tick % 200L == 0L) {
                    LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} SMELTED batch: {} x{} -> {}",
                            pos, itemKey, batchSize, match.resultKey);
                }
            }

            if (totalExp > 0.0f) {
                int expMilli = (int) (totalExp * 1000.0f);
                long newStored = storedExp + expMilli;
                if (newStored <= maxExp) {
                    setStoredExpMilli(cardStack, newStored);
                    if (tick % 200L == 0L) {
                        LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} exp +{} -> card ({}/{})",
                                pos, expMilli, newStored, maxExp);
                    }
                } else {
                    long overflow = newStored - maxExp;
                    setStoredExpMilli(cardStack, maxExp);
                    boolean networkOk = ExperienceFluidHelper.insertExpFluidToNetwork(grid, actionSource, (int) overflow);
                    if (!networkOk && tick - cd.lastExpWarningTick >= 100L) {
                        cd.lastExpWarningTick = tick;
                        String msg = String.format(
                                "\u00a7e[VisualCrafting] \u00a7c\u7194\u70bc\u5361\u7ecf\u9a8c\u6ea2\u51fa %d mB\uff0c\u65e0\u6cd5\u5b58\u5165AE\u7f51\u7edc\u3002\u53ef\u80fd\u539f\u56e0\uff1a\u7f51\u7edc\u65e0\u6d41\u4f53\u5b58\u50a8\u5355\u5143\u3001\u5b58\u50a8\u5df2\u6ee1\u3001\u672a\u8fde\u63a5\u6d41\u4f53\u6a21\u7ec4",
                                overflow);
                        level.getServer().getPlayerList().getPlayers().forEach(p ->
                                p.displayClientMessage(Component.literal(msg), false));
                    }
                    if (tick % 200L == 0L) {
                        LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} exp +{} -> card full ({}/{}) overflow={} network={}",
                                pos, expMilli, maxExp, maxExp, overflow, networkOk ? "OK" : "DISCARDED");
                    }
                }
            }

            if (didWork) {
                cd.lastProcessed = tick;
                if (tick % 200L == 0L) {
                    LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} batch processed={} items, updating cooldown",
                            pos, totalProcessed);
                }
            } else if (tick - cd.lastProcessed >= STORAGE_FULL_INTERVAL) {
                cd.lastProcessed = tick;
                if (tick % 200L == 0L) {
                    LOGGER.debug("[VC:FurnaceCard] tickInterface pos={} idle, advancing cooldown", pos);
                }
            }
        }

        private static SmeltingRecipe findSmelting(AEItemKey itemKey, List<RecipeHolder<SmeltingRecipe>> recipes) {
            ItemStack stack = itemKey.toStack();
            for (RecipeHolder<SmeltingRecipe> holder : recipes) {
                SmeltingRecipe recipe = holder.value();
                if (recipe.getIngredients().isEmpty()) continue;
                if (recipe.getIngredients().get(0).test(stack)) {
                    return recipe;
                }
            }
            return null;
        }

        private static BlastingRecipe findBlasting(AEItemKey itemKey, List<RecipeHolder<BlastingRecipe>> recipes) {
            ItemStack stack = itemKey.toStack();
            for (RecipeHolder<BlastingRecipe> holder : recipes) {
                BlastingRecipe recipe = holder.value();
                if (recipe.getIngredients().isEmpty()) continue;
                if (recipe.getIngredients().get(0).test(stack)) {
                    return recipe;
                }
            }
            return null;
        }

        private static void refreshRecipeCache(ServerLevel level) {
            try {
                RecipeManager recipeManager = level.getServer().getRecipeManager();
                cachedSmeltingRecipes = recipeManager.getAllRecipesFor(RecipeType.SMELTING);
                cachedBlastingRecipes = recipeManager.getAllRecipesFor(RecipeType.BLASTING);
                lastRecipeRefreshTick = level.getServer().getTickCount();
                LOGGER.debug("[VC:FurnaceCard] Recipe cache refreshed: smelting={}, blasting={}",
                        cachedSmeltingRecipes.size(), cachedBlastingRecipes.size());
            } catch (Exception e) {
                LOGGER.debug("[VC:FurnaceCard] refreshRecipeCache failed", e);
            }
        }

        private static class CooldownData {
            long lastProcessed;
            long lastSeen;
            long lastExpWarningTick;
        }

        private record RecipeMatch(AEItemKey resultKey, long resultCount, float experience) {
        }
    }
}
