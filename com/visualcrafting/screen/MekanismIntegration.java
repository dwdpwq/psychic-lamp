package com.visualcrafting.screen;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.fml.ModList;

/**
 * Centralized Mekanism integration check.
 * All Mekanism-dependent features should gate on this class.
 */
public class MekanismIntegration {

    private static final boolean LOADED = ModList.get().isLoaded("mekanism");

    public static boolean isLoaded() {
        return LOADED;
    }

    /**
     * Stub: returns false when Mekanism is not loaded or ingredient is not a chemical.
     * Override this when implementing full Mekanism chemical support.
     */
    public static <I> boolean isChemicalIngredient(I ingredient) {
        return false;
    }

    /**
     * Stub: converts a chemical ingredient to a CompoundTag (no-op stub).
     */
    public static <I> CompoundTag convertChemicalToTag(I ingredient) {
        return new CompoundTag();
    }

    /**
     * Stub: creates a display ItemStack representing a chemical (no-op stub).
     */
    public static <I> net.minecraft.world.item.ItemStack createChemicalTagItem(CompoundTag tag) {
        return net.minecraft.world.item.Items.BARRIER.getDefaultInstance();
    }

    /**
     * Stub: builds ChemSlotData for a chemical ingredient (no-op stub).
     */
    public static <I> ChemSlotData buildChemSlotData(I ingredient) {
        return null;
    }
}
