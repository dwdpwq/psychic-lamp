package com.visualcrafting.item;

import net.minecraft.world.item.ItemStack;

public class FurnaceCardHelper {
    public static int getTier(ItemStack stack) {
        if (stack.getItem() instanceof FurnaceCardItem card) {
            return card.getTier();
        }
        return 0;
    }

    public static int getCapacity(ItemStack stack) {
        int tier = getTier(stack);
        switch (tier) {
            case 1: return 10000;
            case 2: return 50000;
            case 3: return 200000;
            default: return 10000;
        }
    }
}
