package com.visualcrafting.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.visualcrafting.block.VisualCraftingBlockEntity;
import com.visualcrafting.recipe.RecipeRegistrar;
import com.visualcrafting.screen.VisualCraftingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ModMessages {

    // Packet type IDs
    public static final ResourceLocation ADD_RECIPE_ID =
            ResourceLocation.fromNamespaceAndPath("visualcrafting", "add_recipe");
    public static final ResourceLocation REMOVE_RECIPE_ID =
            ResourceLocation.fromNamespaceAndPath("visualcrafting", "remove_recipe");
    public static final ResourceLocation SYNC_RECIPES_ID =
            ResourceLocation.fromNamespaceAndPath("visualcrafting", "sync_recipes");
    public static final ResourceLocation DELETE_BY_OUTPUT_ID =
            ResourceLocation.fromNamespaceAndPath("visualcrafting", "delete_by_output");
    public static final ResourceLocation TIER_UPDATE_ID =
            ResourceLocation.fromNamespaceAndPath("visualcrafting", "tier_update");
    public static final ResourceLocation FORMAT_UPDATE_ID =
            ResourceLocation.fromNamespaceAndPath("visualcrafting", "format_update");
    public static final ResourceLocation MODE_UPDATE_ID =
            ResourceLocation.fromNamespaceAndPath("visualcrafting", "mode_update");
    public static final ResourceLocation ADD_INFUSING_RECIPE_ID =
            ResourceLocation.fromNamespaceAndPath("visualcrafting", "add_infusing_recipe");
    public static final ResourceLocation REMOVE_INFUSING_RECIPE_ID =
            ResourceLocation.fromNamespaceAndPath("visualcrafting", "remove_infusing_recipe");
    public static final ResourceLocation DELETE_INFUSING_BY_OUTPUT_ID =
            ResourceLocation.fromNamespaceAndPath("visualcrafting", "delete_infusing_by_output");
    public static final ResourceLocation SYNC_INFUSING_RECIPES_ID =
            ResourceLocation.fromNamespaceAndPath("visualcrafting", "sync_infusing_recipes");
    public static final ResourceLocation REQUEST_DIM_BIOMES_ID =
            ResourceLocation.fromNamespaceAndPath("visualcrafting", "request_dim_biomes");
    public static final ResourceLocation SYNC_DIM_BIOMES_ID =
            ResourceLocation.fromNamespaceAndPath("visualcrafting", "sync_dim_biomes");

    private static long lastReloadTime = 0L;
    private static final long RELOAD_DEBOUNCE_MS = 3000L;
    private static DimensionBiomesData cachedDimBiomesData;

    // ===== Registration =====

    public static void register(IEventBus modBus) {
        modBus.addListener(ModMessages::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(AddRecipePacket.TYPE, AddRecipePacket.STREAM_CODEC, ModMessages::handleAddRecipe);
        registrar.playToServer(RemoveRecipePacket.TYPE, RemoveRecipePacket.STREAM_CODEC, ModMessages::handleRemoveRecipe);
        registrar.playToServer(DeleteByOutputPacket.TYPE, DeleteByOutputPacket.STREAM_CODEC, ModMessages::handleDeleteByOutput);
        registrar.playToClient(SyncRecipesPacket.TYPE, SyncRecipesPacket.STREAM_CODEC, ModMessages::handleSyncRecipes);
        registrar.playToServer(TierUpdatePacket.TYPE, TierUpdatePacket.STREAM_CODEC, ModMessages::handleTierUpdate);
        registrar.playToServer(FormatUpdatePacket.TYPE, FormatUpdatePacket.STREAM_CODEC, ModMessages::handleFormatUpdate);
        registrar.playToServer(ModeUpdatePacket.TYPE, ModeUpdatePacket.STREAM_CODEC, ModMessages::handleModeUpdate);
        registrar.playToServer(AddInfusingRecipePacket.TYPE, AddInfusingRecipePacket.STREAM_CODEC,
                ModMessages::handleAddInfusingRecipe);
        registrar.playToServer(RemoveInfusingRecipePacket.TYPE, RemoveInfusingRecipePacket.STREAM_CODEC,
                ModMessages::handleRemoveInfusingRecipe);
        registrar.playToServer(DeleteInfusingByOutputPacket.TYPE, DeleteInfusingByOutputPacket.STREAM_CODEC,
                ModMessages::handleDeleteInfusingByOutput);
        registrar.playToClient(SyncInfusingRecipesPacket.TYPE, SyncInfusingRecipesPacket.STREAM_CODEC,
                ModMessages::handleSyncInfusingRecipes);
        registrar.playToServer(RequestDimBiomesPacket.TYPE, RequestDimBiomesPacket.STREAM_CODEC,
                ModMessages::handleRequestDimBiomes);
        registrar.playToClient(SyncDimBiomesPacket.TYPE, SyncDimBiomesPacket.STREAM_CODEC,
                ModMessages::handleSyncDimBiomes);
    }

    // ===== Utility =====

    private static void scheduleReload(ServerPlayer player) {
        MinecraftServer server = player.server;
        long now = System.currentTimeMillis();
        if (now - lastReloadTime >= RELOAD_DEBOUNCE_MS) {
            lastReloadTime = now;
            server.getCommands().performPrefixedCommand(
                    player.createCommandSourceStack().withSuppressedOutput(), "kubejs reload");
        }
    }

    private static void syncToWatching(Level level, BlockPos pos, VisualCraftingBlockEntity be) {
        if (level instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersTrackingChunk(serverLevel, new ChunkPos(pos),
                    new SyncRecipesPacket(pos, new ArrayList<>(be.getRecipes())));
        }
    }

    private static void syncInfusingToWatching(Level level, BlockPos pos, VisualCraftingBlockEntity be) {
        if (level instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersTrackingChunk(serverLevel, new ChunkPos(pos),
                    new SyncInfusingRecipesPacket(pos, new ArrayList<>(be.getInfusingRecipes())));
        }
    }

    public static DimensionBiomesData getCachedDimBiomesData() {
        DimensionBiomesData data = cachedDimBiomesData;
        cachedDimBiomesData = null;
        return data;
    }

    // ===== Crafting recipe handlers =====

    private static void handleAddRecipe(AddRecipePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            BlockEntity be = serverPlayer.level().getBlockEntity(packet.pos);
            if (!(be instanceof VisualCraftingBlockEntity vcBe)) return;

            vcBe.addRecipe(new VisualCraftingBlockEntity.SavedRecipe(
                    packet.shaped, packet.result, packet.ingredients));

            String outputId = BuiltInRegistries.ITEM.getKey(packet.result.getItem()).toString();
            List<VisualCraftingBlockEntity.SavedRecipe> recipes = vcBe.getRecipes();
            for (int i = recipes.size() - 1; i >= 0; i--) {
                VisualCraftingBlockEntity.SavedRecipe r = recipes.get(i);
                if (!r.banned) continue;
                String rId = BuiltInRegistries.ITEM.getKey(r.result.getItem()).toString();
                if (rId.equals(outputId)) {
                    vcBe.removeRecipe(i);
                }
            }

            RecipeRegistrar.updateTableRecipes(packet.pos, vcBe.getRecipes(), vcBe.getFormat());
            RecipeRegistrar.regenerateScript(vcBe.getRecipes(), vcBe.getTier(), vcBe.getFormat());
            syncToWatching(serverPlayer.level(), packet.pos, vcBe);

            String type = packet.shaped ? "有序合成" : "无序合成";
            serverPlayer.displayClientMessage(
                    Component.literal(type + "已添加: " + packet.result.getHoverName().getString()), false);
            scheduleReload(serverPlayer);
        });
    }

    private static void handleRemoveRecipe(RemoveRecipePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            BlockEntity be = serverPlayer.level().getBlockEntity(packet.pos);
            if (!(be instanceof VisualCraftingBlockEntity vcBe)) return;

            vcBe.removeRecipe(packet.index);
            RecipeRegistrar.updateTableRecipes(packet.pos, vcBe.getRecipes(), vcBe.getFormat());
            syncToWatching(serverPlayer.level(), packet.pos, vcBe);

            serverPlayer.displayClientMessage(Component.literal("已删除已保存配方"), false);
            scheduleReload(serverPlayer);
        });
    }

    private static void handleDeleteByOutput(DeleteByOutputPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            BlockEntity be = serverPlayer.level().getBlockEntity(packet.pos);
            if (!(be instanceof VisualCraftingBlockEntity vcBe)) return;

            String outputId = BuiltInRegistries.ITEM.getKey(packet.output.getItem()).toString();
            List<VisualCraftingBlockEntity.SavedRecipe> recipes = vcBe.getRecipes();
            for (int i = recipes.size() - 1; i >= 0; i--) {
                VisualCraftingBlockEntity.SavedRecipe r = recipes.get(i);
                if (r.banned) continue;
                String rId = BuiltInRegistries.ITEM.getKey(r.result.getItem()).toString();
                if (rId.equals(outputId)) {
                    vcBe.removeRecipe(i);
                }
            }

            RecipeRegistrar.updateTableRecipes(packet.pos, vcBe.getRecipes(), vcBe.getFormat());
            RecipeRegistrar.banOutput(outputId, vcBe.getRecipes(), vcBe.getTier(), vcBe.getFormat());
            syncToWatching(serverPlayer.level(), packet.pos, vcBe);

            serverPlayer.displayClientMessage(
                    Component.literal("已删除配方: " + packet.output.getHoverName().getString()), false);
            scheduleReload(serverPlayer);
        });
    }

    private static void handleSyncRecipes(SyncRecipesPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof VisualCraftingScreen vcScreen) {
                vcScreen.updateRecipes(packet.recipes);
            }
        });
    }

    // ===== Tier / Format / Mode handlers =====

    private static void handleTierUpdate(TierUpdatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            BlockEntity be = serverPlayer.level().getBlockEntity(packet.pos);
            if (be instanceof VisualCraftingBlockEntity vcBe) {
                vcBe.setTier(packet.tier);
            }
        });
    }

    private static void handleFormatUpdate(FormatUpdatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            BlockEntity be = serverPlayer.level().getBlockEntity(packet.pos);
            if (!(be instanceof VisualCraftingBlockEntity vcBe)) return;

            vcBe.setFormat(packet.format);
            RecipeRegistrar.updateTableRecipes(packet.pos, vcBe.getRecipes(), vcBe.getFormat());
            RecipeRegistrar.regenerateScript(vcBe.getRecipes(), vcBe.getTier(), vcBe.getFormat());

            serverPlayer.displayClientMessage(Component.literal(
                    packet.format == 0 ? "Switched to KubeJS mode" : "Switched to CraftTweaker mode"), false);
        });
    }

    private static void handleModeUpdate(ModeUpdatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            BlockEntity be = serverPlayer.level().getBlockEntity(packet.pos);
            if (be instanceof VisualCraftingBlockEntity vcBe) {
                vcBe.setMode(packet.mode);
            }
        });
    }

    // ===== Infusing recipe handlers =====

    private static void handleAddInfusingRecipe(AddInfusingRecipePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            BlockEntity be = serverPlayer.level().getBlockEntity(packet.pos);
            if (!(be instanceof VisualCraftingBlockEntity vcBe)) return;

            vcBe.addInfusingRecipe(new VisualCraftingBlockEntity.InfusingRecipe(
                    packet.inputA, packet.inputB, packet.output, packet.infusionAmount));

            String outputId = BuiltInRegistries.ITEM.getKey(packet.output.getItem()).toString();
            List<VisualCraftingBlockEntity.InfusingRecipe> recipes = vcBe.getInfusingRecipes();
            for (int i = recipes.size() - 1; i >= 0; i--) {
                VisualCraftingBlockEntity.InfusingRecipe r = recipes.get(i);
                if (!r.banned) continue;
                String rId = BuiltInRegistries.ITEM.getKey(r.output.getItem()).toString();
                if (rId.equals(outputId)) {
                    vcBe.removeInfusingRecipe(i);
                }
            }

            RecipeRegistrar.updateInfusingTableRecipes(packet.pos, vcBe.getInfusingRecipes(), vcBe.getFormat());
            RecipeRegistrar.regenerateInfusingScript(vcBe.getInfusingRecipes(), vcBe.getFormat());
            syncInfusingToWatching(serverPlayer.level(), packet.pos, vcBe);

            serverPlayer.displayClientMessage(
                    Component.literal("灌注配方已添加: " + packet.output.getHoverName().getString()), false);
            scheduleReload(serverPlayer);
        });
    }

    private static void handleRemoveInfusingRecipe(RemoveInfusingRecipePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            BlockEntity be = serverPlayer.level().getBlockEntity(packet.pos);
            if (!(be instanceof VisualCraftingBlockEntity vcBe)) return;

            vcBe.removeInfusingRecipe(packet.index);
            RecipeRegistrar.updateInfusingTableRecipes(packet.pos, vcBe.getInfusingRecipes(), vcBe.getFormat());
            syncInfusingToWatching(serverPlayer.level(), packet.pos, vcBe);

            serverPlayer.displayClientMessage(Component.literal("已删除已保存灌注配方"), false);
            scheduleReload(serverPlayer);
        });
    }

    private static void handleDeleteInfusingByOutput(DeleteInfusingByOutputPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            BlockEntity be = serverPlayer.level().getBlockEntity(packet.pos);
            if (!(be instanceof VisualCraftingBlockEntity vcBe)) return;

            String outputId = BuiltInRegistries.ITEM.getKey(packet.output.getItem()).toString();

            // Create a banned entry from the output item
            vcBe.addInfusingRecipe(new VisualCraftingBlockEntity.InfusingRecipe(packet.output.copy()));

            List<VisualCraftingBlockEntity.InfusingRecipe> recipes = vcBe.getInfusingRecipes();
            for (int i = recipes.size() - 1; i >= 0; i--) {
                VisualCraftingBlockEntity.InfusingRecipe r = recipes.get(i);
                if (r.banned) continue;
                String rId = BuiltInRegistries.ITEM.getKey(r.output.getItem()).toString();
                if (rId.equals(outputId)) {
                    vcBe.removeInfusingRecipe(i);
                }
            }

            RecipeRegistrar.updateInfusingTableRecipes(packet.pos, vcBe.getInfusingRecipes(), vcBe.getFormat());
            RecipeRegistrar.banInfusingOutput(outputId, vcBe.getInfusingRecipes(), vcBe.getFormat());
            syncInfusingToWatching(serverPlayer.level(), packet.pos, vcBe);

            serverPlayer.displayClientMessage(
                    Component.literal("已删除灌注配方: " + packet.output.getHoverName().getString()), false);
            scheduleReload(serverPlayer);
        });
    }

    private static void handleSyncInfusingRecipes(SyncInfusingRecipesPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof VisualCraftingScreen vcScreen) {
                vcScreen.updateInfusingRecipes(packet.recipes);
            }
        });
    }


    // ===== Dimension / Biomes handlers =====

    private static void handleRequestDimBiomes(RequestDimBiomesPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = ctx.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            List<String> dimIds = new ArrayList<>();
            LinkedHashMap<String, List<String>> biomesByDim = new LinkedHashMap<>();
            List<String> allBiomes = new ArrayList<>();

            try {
                var registryAccess = serverPlayer.server.registryAccess();
                var dims = registryAccess.registryOrThrow(Registries.LEVEL_STEM);

                for (Map.Entry<ResourceKey<LevelStem>, LevelStem> entry : dims.entrySet()) {
                    String dimId = entry.getKey().location().toString();
                    dimIds.add(dimId);

                    try {
                        var biomeSource = entry.getValue().generator().getBiomeSource();
                        Set<Holder<?>> biomes = biomeSource.possibleBiomes();
                        List<String> biomeList = new ArrayList<>();
                        for (Holder<?> holder : biomes) {
                            holder.unwrapKey().ifPresent(key -> {
                                String id = key.location().toString();
                                biomeList.add(id);
                                if (!allBiomes.contains(id)) {
                                    allBiomes.add(id);
                                }
                            });
                        }
                        biomesByDim.put(dimId, biomeList);
                    } catch (Exception ignored) {
                        biomesByDim.put(dimId, new ArrayList<>());
                    }
                }

                // Include all registered biomes not already covered
                var biomeRegistry = registryAccess.registryOrThrow(Registries.BIOME);
                for (Map.Entry<ResourceKey<?>, ?> biomeEntry : biomeRegistry.entrySet()) {
                    String id = biomeEntry.getKey().location().toString();
                    if (!allBiomes.contains(id)) {
                        allBiomes.add(id);
                    }
                }
            } catch (Exception e) {
                System.err.println("[VisualCrafting] Failed to load dim/biome data on server: " + e.getMessage());
            }

            DimensionBiomesData data = new DimensionBiomesData(dimIds, biomesByDim, allBiomes);

            // Save index on server
            try {
                File worldDir = serverPlayer.server.getWorldPath(LevelResource.ROOT).toFile();
                File vcDir = new File(worldDir, "visualcrafting");
                vcDir.mkdirs();
                File indexFile = new File(vcDir, "dim_biomes_index.json");
                Files.writeString(indexFile.toPath(), data.toJson(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.err.println("[VisualCrafting] Failed to save dim/biome index: " + e.getMessage());
            }

            PacketDistributor.sendToPlayer(serverPlayer, new SyncDimBiomesPacket(data));
        });
    }

    private static void handleSyncDimBiomes(SyncDimBiomesPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof VisualCraftingScreen vcScreen) {
                vcScreen.applyDimBiomesData(packet.data);
            } else {
                cachedDimBiomesData = packet.data;
            }

            try {
                File vcDir = new File(Minecraft.getInstance().gameDirectory, "visualcrafting");
                vcDir.mkdirs();
                File cacheFile = new File(vcDir, "dim_biomes_cache.json");
                Files.writeString(cacheFile.toPath(), packet.data.toJson(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.err.println("[VisualCrafting] Failed to save dim/biome client cache: " + e.getMessage());
            }
        });
    }

    // ========================================================================
    // Packet records
    // ========================================================================

    public record AddRecipePacket(BlockPos pos, boolean shaped, ItemStack result,
                                  List<ItemStack> ingredients) implements CustomPacketPayload {
        public static final Type<AddRecipePacket> TYPE = new Type<>(ADD_RECIPE_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, AddRecipePacket> STREAM_CODEC =
                StreamCodec.of(AddRecipePacket::encode, AddRecipePacket::decode);

        @Override
        public Type<AddRecipePacket> type() { return TYPE; }

        private static void encode(RegistryFriendlyByteBuf buf, AddRecipePacket pkt) {
            buf.writeBlockPos(pkt.pos);
            buf.writeBoolean(pkt.shaped);
            ItemStack.STREAM_CODEC.encode(buf, pkt.result);
            buf.writeVarInt(pkt.ingredients.size());
            for (ItemStack stack : pkt.ingredients) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
            }
        }

        private static AddRecipePacket decode(RegistryFriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            boolean shaped = buf.readBoolean();
            ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
            int count = buf.readVarInt();
            List<ItemStack> ingredients = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                ingredients.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
            }
            return new AddRecipePacket(pos, shaped, result, ingredients);
        }
    }

    public record RemoveRecipePacket(BlockPos pos, int index) implements CustomPacketPayload {
        public static final Type<RemoveRecipePacket> TYPE = new Type<>(REMOVE_RECIPE_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, RemoveRecipePacket> STREAM_CODEC =
                StreamCodec.of(RemoveRecipePacket::encode, RemoveRecipePacket::decode);

        @Override
        public Type<RemoveRecipePacket> type() { return TYPE; }

        private static void encode(RegistryFriendlyByteBuf buf, RemoveRecipePacket pkt) {
            buf.writeBlockPos(pkt.pos);
            buf.writeVarInt(pkt.index);
        }

        private static RemoveRecipePacket decode(RegistryFriendlyByteBuf buf) {
            return new RemoveRecipePacket(buf.readBlockPos(), buf.readVarInt());
        }
    }

    public record DeleteByOutputPacket(BlockPos pos, ItemStack output) implements CustomPacketPayload {
        public static final Type<DeleteByOutputPacket> TYPE = new Type<>(DELETE_BY_OUTPUT_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, DeleteByOutputPacket> STREAM_CODEC =
                StreamCodec.of(DeleteByOutputPacket::encode, DeleteByOutputPacket::decode);

        @Override
        public Type<DeleteByOutputPacket> type() { return TYPE; }

        private static void encode(RegistryFriendlyByteBuf buf, DeleteByOutputPacket pkt) {
            buf.writeBlockPos(pkt.pos);
            ItemStack.STREAM_CODEC.encode(buf, pkt.output);
        }

        private static DeleteByOutputPacket decode(RegistryFriendlyByteBuf buf) {
            return new DeleteByOutputPacket(buf.readBlockPos(), ItemStack.STREAM_CODEC.decode(buf));
        }
    }

    public record SyncRecipesPacket(BlockPos pos,
                                    List<VisualCraftingBlockEntity.SavedRecipe> recipes)
            implements CustomPacketPayload {
        public static final Type<SyncRecipesPacket> TYPE = new Type<>(SYNC_RECIPES_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncRecipesPacket> STREAM_CODEC =
                StreamCodec.of(SyncRecipesPacket::encode, SyncRecipesPacket::decode);

        @Override
        public Type<SyncRecipesPacket> type() { return TYPE; }

        private static void encode(RegistryFriendlyByteBuf buf, SyncRecipesPacket pkt) {
            buf.writeBlockPos(pkt.pos);
            buf.writeVarInt(pkt.recipes.size());
            for (VisualCraftingBlockEntity.SavedRecipe r : pkt.recipes) {
                buf.writeBoolean(r.shaped);
                buf.writeBoolean(r.banned);
                ItemStack.STREAM_CODEC.encode(buf, r.result);
                buf.writeVarInt(r.ingredients.size());
                for (ItemStack stack : r.ingredients) {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
                }
            }
        }

        private static SyncRecipesPacket decode(RegistryFriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            int total = buf.readVarInt();
            List<VisualCraftingBlockEntity.SavedRecipe> recipes = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                boolean shaped = buf.readBoolean();
                boolean banned = buf.readBoolean();
                ItemStack result = ItemStack.STREAM_CODEC.decode(buf);
                int ingCount = buf.readVarInt();
                List<ItemStack> ingredients = new ArrayList<>();
                for (int j = 0; j < ingCount; j++) {
                    ingredients.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                }
                recipes.add(new VisualCraftingBlockEntity.SavedRecipe(shaped, banned, result, ingredients));
            }
            return new SyncRecipesPacket(pos, recipes);
        }
    }

    public record TierUpdatePacket(BlockPos pos, int tier) implements CustomPacketPayload {
        public static final Type<TierUpdatePacket> TYPE = new Type<>(TIER_UPDATE_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, TierUpdatePacket> STREAM_CODEC =
                StreamCodec.of(TierUpdatePacket::encode, TierUpdatePacket::decode);

        @Override
        public Type<TierUpdatePacket> type() { return TYPE; }

        private static void encode(RegistryFriendlyByteBuf buf, TierUpdatePacket pkt) {
            buf.writeBlockPos(pkt.pos);
            buf.writeVarInt(pkt.tier);
        }

        private static TierUpdatePacket decode(RegistryFriendlyByteBuf buf) {
            return new TierUpdatePacket(buf.readBlockPos(), buf.readVarInt());
        }
    }

    public record FormatUpdatePacket(BlockPos pos, int format) implements CustomPacketPayload {
        public static final Type<FormatUpdatePacket> TYPE = new Type<>(FORMAT_UPDATE_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, FormatUpdatePacket> STREAM_CODEC =
                StreamCodec.of(FormatUpdatePacket::encode, FormatUpdatePacket::decode);

        @Override
        public Type<FormatUpdatePacket> type() { return TYPE; }

        private static void encode(RegistryFriendlyByteBuf buf, FormatUpdatePacket pkt) {
            buf.writeBlockPos(pkt.pos);
            buf.writeVarInt(pkt.format);
        }

        private static FormatUpdatePacket decode(RegistryFriendlyByteBuf buf) {
            return new FormatUpdatePacket(buf.readBlockPos(), buf.readVarInt());
        }
    }

    public record ModeUpdatePacket(BlockPos pos, int mode) implements CustomPacketPayload {
        public static final Type<ModeUpdatePacket> TYPE = new Type<>(MODE_UPDATE_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, ModeUpdatePacket> STREAM_CODEC =
                StreamCodec.of(ModeUpdatePacket::encode, ModeUpdatePacket::decode);

        @Override
        public Type<ModeUpdatePacket> type() { return TYPE; }

        private static void encode(RegistryFriendlyByteBuf buf, ModeUpdatePacket pkt) {
            buf.writeBlockPos(pkt.pos);
            buf.writeVarInt(pkt.mode);
        }

        private static ModeUpdatePacket decode(RegistryFriendlyByteBuf buf) {
            return new ModeUpdatePacket(buf.readBlockPos(), buf.readVarInt());
        }
    }

    public record AddInfusingRecipePacket(BlockPos pos, ItemStack inputA, ItemStack inputB,
                                          ItemStack output, int infusionAmount) implements CustomPacketPayload {
        public static final Type<AddInfusingRecipePacket> TYPE = new Type<>(ADD_INFUSING_RECIPE_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, AddInfusingRecipePacket> STREAM_CODEC =
                StreamCodec.of(AddInfusingRecipePacket::encode, AddInfusingRecipePacket::decode);

        @Override
        public Type<AddInfusingRecipePacket> type() { return TYPE; }

        private static void encode(RegistryFriendlyByteBuf buf, AddInfusingRecipePacket pkt) {
            buf.writeBlockPos(pkt.pos);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, pkt.inputA);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, pkt.inputB);
            ItemStack.STREAM_CODEC.encode(buf, pkt.output);
            buf.writeVarInt(pkt.infusionAmount);
        }

        private static AddInfusingRecipePacket decode(RegistryFriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            ItemStack inputA = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            ItemStack inputB = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            ItemStack output = ItemStack.STREAM_CODEC.decode(buf);
            int amount = buf.readVarInt();
            return new AddInfusingRecipePacket(pos, inputA, inputB, output, amount);
        }
    }

    public record RemoveInfusingRecipePacket(BlockPos pos, int index) implements CustomPacketPayload {
        public static final Type<RemoveInfusingRecipePacket> TYPE = new Type<>(REMOVE_INFUSING_RECIPE_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, RemoveInfusingRecipePacket> STREAM_CODEC =
                StreamCodec.of(RemoveInfusingRecipePacket::encode, RemoveInfusingRecipePacket::decode);

        @Override
        public Type<RemoveInfusingRecipePacket> type() { return TYPE; }

        private static void encode(RegistryFriendlyByteBuf buf, RemoveInfusingRecipePacket pkt) {
            buf.writeBlockPos(pkt.pos);
            buf.writeVarInt(pkt.index);
        }

        private static RemoveInfusingRecipePacket decode(RegistryFriendlyByteBuf buf) {
            return new RemoveInfusingRecipePacket(buf.readBlockPos(), buf.readVarInt());
        }
    }

    public record DeleteInfusingByOutputPacket(BlockPos pos, ItemStack output) implements CustomPacketPayload {
        public static final Type<DeleteInfusingByOutputPacket> TYPE = new Type<>(DELETE_INFUSING_BY_OUTPUT_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, DeleteInfusingByOutputPacket> STREAM_CODEC =
                StreamCodec.of(DeleteInfusingByOutputPacket::encode, DeleteInfusingByOutputPacket::decode);

        @Override
        public Type<DeleteInfusingByOutputPacket> type() { return TYPE; }

        private static void encode(RegistryFriendlyByteBuf buf, DeleteInfusingByOutputPacket pkt) {
            buf.writeBlockPos(pkt.pos);
            ItemStack.STREAM_CODEC.encode(buf, pkt.output);
        }

        private static DeleteInfusingByOutputPacket decode(RegistryFriendlyByteBuf buf) {
            return new DeleteInfusingByOutputPacket(buf.readBlockPos(), ItemStack.STREAM_CODEC.decode(buf));
        }
    }

    public record SyncInfusingRecipesPacket(BlockPos pos,
                                            List<VisualCraftingBlockEntity.InfusingRecipe> recipes)
            implements CustomPacketPayload {
        public static final Type<SyncInfusingRecipesPacket> TYPE = new Type<>(SYNC_INFUSING_RECIPES_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncInfusingRecipesPacket> STREAM_CODEC =
                StreamCodec.of(SyncInfusingRecipesPacket::encode, SyncInfusingRecipesPacket::decode);

        @Override
        public Type<SyncInfusingRecipesPacket> type() { return TYPE; }

        private static void encode(RegistryFriendlyByteBuf buf, SyncInfusingRecipesPacket pkt) {
            buf.writeBlockPos(pkt.pos);
            buf.writeVarInt(pkt.recipes.size());
            for (VisualCraftingBlockEntity.InfusingRecipe r : pkt.recipes) {
                buf.writeBoolean(r.banned);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, r.inputA);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, r.inputB);
                ItemStack.STREAM_CODEC.encode(buf, r.output);
                buf.writeVarInt(r.infusionAmount);
            }
        }

        private static SyncInfusingRecipesPacket decode(RegistryFriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            int total = buf.readVarInt();
            List<VisualCraftingBlockEntity.InfusingRecipe> recipes = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                boolean banned = buf.readBoolean();
                ItemStack inputA = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                ItemStack inputB = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                ItemStack output = ItemStack.STREAM_CODEC.decode(buf);
                int amount = buf.readVarInt();
                if (banned) {
                    VisualCraftingBlockEntity.InfusingRecipe r =
                            new VisualCraftingBlockEntity.InfusingRecipe(output);
                    r.infusionAmount = amount;
                    recipes.add(r);
                } else {
                    recipes.add(new VisualCraftingBlockEntity.InfusingRecipe(
                            inputA, inputB, output, amount));
                }
            }
            return new SyncInfusingRecipesPacket(pos, recipes);
        }
    }

    public record RequestDimBiomesPacket() implements CustomPacketPayload {
        public static final Type<RequestDimBiomesPacket> TYPE = new Type<>(REQUEST_DIM_BIOMES_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestDimBiomesPacket> STREAM_CODEC =
                StreamCodec.of((buf, pkt) -> {}, buf -> new RequestDimBiomesPacket());

        @Override
        public Type<RequestDimBiomesPacket> type() { return TYPE; }
    }

    public record SyncDimBiomesPacket(DimensionBiomesData data) implements CustomPacketPayload {
        public static final Type<SyncDimBiomesPacket> TYPE = new Type<>(SYNC_DIM_BIOMES_ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncDimBiomesPacket> STREAM_CODEC =
                StreamCodec.of(SyncDimBiomesPacket::encode, SyncDimBiomesPacket::decode);

        @Override
        public Type<SyncDimBiomesPacket> type() { return TYPE; }

        private static void encode(RegistryFriendlyByteBuf buf, SyncDimBiomesPacket pkt) {
            DimensionBiomesData data = pkt.data;
            buf.writeVarInt(data.dimIds.size());
            for (String dimId : data.dimIds) {
                buf.writeUtf(dimId);
            }
            buf.writeVarInt(data.biomesByDim.size());
            for (Map.Entry<String, List<String>> entry : data.biomesByDim.entrySet()) {
                buf.writeUtf(entry.getKey());
                List<String> biomes = entry.getValue();
                buf.writeVarInt(biomes.size());
                for (String biome : biomes) {
                    buf.writeUtf(biome);
                }
            }
            buf.writeVarInt(data.allBiomes.size());
            for (String biome : data.allBiomes) {
                buf.writeUtf(biome);
            }
        }

        private static SyncDimBiomesPacket decode(RegistryFriendlyByteBuf buf) {
            int dimCount = buf.readVarInt();
            List<String> dimIds = new ArrayList<>();
            for (int i = 0; i < dimCount; i++) {
                dimIds.add(buf.readUtf());
            }

            int mapSize = buf.readVarInt();
            LinkedHashMap<String, List<String>> biomesByDim = new LinkedHashMap<>();
            for (int i = 0; i < mapSize; i++) {
                String dimId = buf.readUtf();
                int biomeCount = buf.readVarInt();
                List<String> biomes = new ArrayList<>();
                for (int j = 0; j < biomeCount; j++) {
                    biomes.add(buf.readUtf());
                }
                biomesByDim.put(dimId, biomes);
            }

            int allCount = buf.readVarInt();
            List<String> allBiomes = new ArrayList<>();
            for (int i = 0; i < allCount; i++) {
                allBiomes.add(buf.readUtf());
            }

            return new SyncDimBiomesPacket(new DimensionBiomesData(dimIds, biomesByDim, allBiomes));
        }
    }
}
