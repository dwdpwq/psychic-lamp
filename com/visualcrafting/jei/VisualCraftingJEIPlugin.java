package com.visualcrafting.jei;

import com.visualcrafting.screen.VisualCraftingScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public class VisualCraftingJEIPlugin implements IModPlugin {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("visualcrafting", "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(VisualCraftingScreen.class, new VisualCraftingGhostHandler());
    }
}
