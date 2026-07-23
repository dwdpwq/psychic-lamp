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
        if (stack.getItem() instanceof FurnaceCardItem card) {
            return card.getMaxExpStorage();
        }
        return 10000;
    }
}
