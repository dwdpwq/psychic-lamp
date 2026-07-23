package com.visualcrafting.fluid;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

@EventBusSubscriber(modid = "visualcrafting")
public class ExperienceFluidFinder {
    private static final ResourceLocation FALLBACK_ID =
            ResourceLocation.fromNamespaceAndPath("visualcrafting", "liquid_xp");

    private static Fluid targetFluid = null;
    private static ResourceKey<Fluid> lockedFluidKey = null;
    private static boolean usingFallback = false;

    public static Fluid getTargetFluid() {
        validateAndFind();
        return targetFluid;
    }

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        targetFluid = null;
        lockedFluidKey = null;
        usingFallback = false;
        validateAndFind();
    }

    private static void validateAndFind() {
        if (lockedFluidKey != null) {
            Fluid fluid = BuiltInRegistries.FLUID.get(lockedFluidKey);
            if (fluid != null) {
                if (usingFallback) {
                    targetFluid = fluid;
                }
                return;
            }
            System.err.println("[VC:ExperienceFluidFinder] Locked fluid "
                    + lockedFluidKey.location() + " disappeared from registry, re-scanning...");
            targetFluid = null;
            lockedFluidKey = null;
            usingFallback = false;
        }

        Fluid fallback = BuiltInRegistries.FLUID.get(FALLBACK_ID);

        for (ResourceKey<Fluid> key : BuiltInRegistries.FLUID.registryKeySet()) {
            String path = key.location().getPath();
            if (!path.contains("experience") && !path.contains("xp")
                    && !path.contains("essence") && !path.contains("juice")
                    && !path.contains("mob_essence")) {
                continue;
            }
            Fluid fluid = BuiltInRegistries.FLUID.get(key);
            if (fluid == null) continue;
            if (fallback != null && fluid.isSame(fallback)) continue;

            lockedFluidKey = key;
            targetFluid = fluid;
            usingFallback = false;
            System.err.println("[VC:ExperienceFluidFinder] Locked external experience fluid: " + key.location());
            return;
        }

        if (fallback != null) {
            lockedFluidKey = ResourceKey.create(BuiltInRegistries.FLUID.key(), FALLBACK_ID);
            targetFluid = fallback;
            usingFallback = true;
            System.err.println("[VC:ExperienceFluidFinder] No external experience fluid, using built-in liquid_xp");
        } else {
            System.err.println("[VC:ExperienceFluidFinder] WARNING: liquid_xp not registered, no fallback available");
        }
    }
}
