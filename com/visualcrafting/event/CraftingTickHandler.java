package com.visualcrafting.event;

import com.visualcrafting.block.VisualCraftingBlockEntity;
import com.visualcrafting.screen.VisualCraftingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = "visualcrafting")
public class CraftingTickHandler {

    @SubscribeEvent
    public static void handleServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof VisualCraftingMenu vcMenu) {
                craftItem(vcMenu, player);
            }
        }
    }

    private static void craftItem(VisualCraftingMenu menu, ServerPlayer player) {
        // 通过 blockPos 获取 BlockEntity，不再依赖 menu.blockEntity 字段
        Level level = player.level();
        BlockPos pos = menu.blockPos;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof VisualCraftingBlockEntity vcbe)) {
            return;
        }
        if (vcbe.getMode() != 0) {
            return;
        }
        if (level.isClientSide) {
            return;
        }

        int craftSlotCount = menu.getCraftSlotCount();
        int gridSize = menu.getGridSize();

        CraftingInput input = CraftingInput.of(gridSize, gridSize, craftSlotsToList(menu, craftSlotCount));
        RecipeManager recipeManager = level.getRecipeManager();
        Optional<RecipeHolder<CraftingRecipe>> optional =
                recipeManager.getRecipeFor(RecipeType.CRAFTING, input, level);

        ItemStack result = optional.isPresent()
                ? optional.get().value().assemble(input, level.registryAccess())
                : ItemStack.EMPTY;

        Slot slot = menu.slots.get(81);
        ItemStack current = slot.getItem();
        if (current.isEmpty() && !result.isEmpty()) {
            slot.setByPlayer(result);
        }
    }

    private static List<ItemStack> craftSlotsToList(VisualCraftingMenu menu, int count) {
        List<ItemStack> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(menu.craftSlots.getItem(i));
        }
        return list;
    }
}