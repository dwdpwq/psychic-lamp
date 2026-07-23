package com.visualcrafting.screen;

import com.visualcrafting.VisualCraftingTable;
import com.visualcrafting.block.VisualCraftingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.lang.reflect.Field;
import java.util.*;

public class VisualCraftingMenu extends AbstractContainerMenu {

    public static final int MAX_GRID = 81;
    public static final int GRID_START = 0;
    public static final int OUTPUT_SLOT = MAX_GRID;
    public static final int PLAYER_START = OUTPUT_SLOT + 1;
    public static final int PLAYER_END = PLAYER_START + 36;

    public final BlockPos blockPos;
    public VisualCraftingBlockEntity blockEntity;
    public final Container craftSlots;
    public final Container resultSlot;
    public ChemSlotData chemSlotData;
    public int chemAmount;

    private int currentTier = 1;

    // === Constructors ===

    public VisualCraftingMenu(int id, Inventory playerInv) {
        this(id, playerInv, BlockPos.ZERO);
    }

    public VisualCraftingMenu(int id, Inventory playerInv, VisualCraftingBlockEntity blockEntity) {
        this(id, playerInv, blockEntity.getBlockPos());
        this.blockEntity = blockEntity;
        this.currentTier = blockEntity.getTier();
        updateSlotPositions(currentTier);
    }

    public VisualCraftingMenu(int id, Inventory playerInv, FriendlyByteBuf extra) {
        this(id, playerInv, extra != null ? extra.readBlockPos() : BlockPos.ZERO);
    }

    public VisualCraftingMenu(int id, Inventory playerInv, ContainerLevelAccess access, BlockPos pos) {
        super(VisualCraftingTable.VISUAL_CRAFTING_MENU.get(), id);
        this.blockPos = pos;
        this.craftSlots = new SimpleContainer(MAX_GRID);
        this.resultSlot = new SimpleContainer(1);

        buildSlots(playerInv);

        access.execute((level, blockPos) -> {
            if (level.getBlockEntity(blockPos) instanceof VisualCraftingBlockEntity be) {
                this.currentTier = be.getTier();
            }
        });

        updateSlotPositions(currentTier);
    }

    public VisualCraftingMenu(int id, Inventory playerInv, BlockPos pos) {
        this(id, playerInv, ContainerLevelAccess.NULL, pos);
        if (playerInv.player.level().getBlockEntity(pos) instanceof VisualCraftingBlockEntity be) {
            this.currentTier = be.getTier();
        }
        updateSlotPositions(currentTier);
    }

    // === Slot building (matches backup jar's layout) ===

    private void buildSlots(Inventory playerInv) {
        // Crafting grid slots (0 to MAX_GRID-1)
        for (int i = 0; i < MAX_GRID; i++) {
            this.addSlot(new Slot(craftSlots, i, 0, 0));
        }
        // Output slot (index MAX_GRID)
        this.addSlot(new Slot(resultSlot, 0, 145, 16));

        // Player inventory
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 198));
        }
    }

    // === updateSlotPositions (exact backup jar layout) ===

    public void updateSlotPositions(int tier) {
        this.currentTier = tier;
        // Backup's grid size parameter: 1→5, 2→7, 3→9, default→3
        int gridParam = switch (tier) { case 1 -> 5; case 2 -> 7; case 3 -> 9; default -> 3; };
        int baseX = 52;
        int baseY = 13;

        try {
            Field fx = Slot.class.getDeclaredField("x");
            Field fy = Slot.class.getDeclaredField("y");
            fx.setAccessible(true);
            fy.setAccessible(true);

            // Position active grid slots
            int idx = 0;
            for (int row = 0; row < gridParam; row++) {
                for (int col = 0; col < gridParam; col++) {
                    Slot slot = this.slots.get(idx);
                    fx.setInt(slot, baseX + col * 18);
                    fy.setInt(slot, baseY + row * 18);
                    idx++;
                }
            }
            // Hide unused grid slots
            for (int i = gridParam * gridParam; i < MAX_GRID; i++) {
                Slot slot = this.slots.get(i);
                fx.setInt(slot, -2000);
                fy.setInt(slot, -2000);
            }

            // Output slot position
            Slot outSlot = this.slots.get(OUTPUT_SLOT);
            fx.setInt(outSlot, baseX + gridParam * 18 + 20);
            fy.setInt(outSlot, baseY + (baseY + gridParam * 18 - 18) / 2);

            // Player inventory X (columns 0-8)
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    Slot slot = this.slots.get(PLAYER_START + row * 9 + col);
                    fx.setInt(slot, 8 + col * 18);
                }
            }
            // Player inventory Y
            int invBaseY = baseY + gridParam * 18 + 8;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    Slot slot = this.slots.get(PLAYER_START + row * 9 + col);
                    fy.setInt(slot, invBaseY + row * 18);
                }
            }
            // Hotbar
            int hotbarY = invBaseY + 54 + 4;
            for (int col = 0; col < 9; col++) {
                Slot slot = this.slots.get(PLAYER_START + 27 + col);
                fx.setInt(slot, 8 + col * 18);
                fy.setInt(slot, hotbarY);
            }
        } catch (Exception ignored) {}
    }

    // === Standard container methods ===

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < PLAYER_START) {
                if (!this.moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(stack, 0, MAX_GRID, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < MAX_GRID && clickType == ClickType.PICKUP) {
            Slot slot = this.slots.get(slotId);
            ItemStack carried = getCarried();
            if (!carried.isEmpty()) {
                slot.setByPlayer(carried.copyWithCount(1));
            } else {
                slot.setByPlayer(ItemStack.EMPTY);
            }
            return;
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    // === Utility ===

    public Optional<VisualCraftingBlockEntity> getBlockEntity() {
        return Optional.empty();
    }

    public int getCraftSlotCount() {
        return MAX_GRID;
    }

    public static int tierToGridSize(int tier) {
        if (tier <= 3) return 3;
        if (tier <= 5) return 5;
        if (tier <= 7) return 7;
        return 9;
    }

    public int getActiveGridSize() {
        return tierToGridSize(currentTier);
    }

    public int getGridSize() {
        return getActiveGridSize();
    }

    public void setCurrentTier(int tier) {
        this.currentTier = tier;
    }

    // === Chem ghost data (for infusing tab) ===

    private final Map<Integer, CompoundTag> chemGhostData = new HashMap<>();

    public CompoundTag getChemGhost(int slot) {
        return chemGhostData.getOrDefault(slot, new CompoundTag());
    }

    public void setChemGhost(int slot, CompoundTag tag) {
        chemGhostData.put(slot, tag);
    }
}
