package com.visualcrafting.fluid;

import com.visualcrafting.VisualCraftingTable;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@EventBusSubscriber(modid = "visualcrafting", bus = EventBusSubscriber.Bus.MOD)
public class CreativeFluidHandler {
    @SubscribeEvent
    public static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        String path = event.getTabKey().location().getPath();
        if (path.equals("tools_and_utilities") || path.equals("ingredients") || path.equals("search")) {
            ItemStack stack = new ItemStack(VisualCraftingTable.LIQUID_XP_BUCKET.get());
            try {
                event.accept(stack);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
