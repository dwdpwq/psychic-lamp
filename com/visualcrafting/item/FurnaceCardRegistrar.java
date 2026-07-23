package com.visualcrafting.item;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import com.visualcrafting.Config;
import com.visualcrafting.VisualCraftingTable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;

public class FurnaceCardRegistrar {
    public static DeferredItem<Item> FURNACE_CARD_TIER1;
    public static DeferredItem<Item> FURNACE_CARD_TIER2;
    public static DeferredItem<Item> FURNACE_CARD_TIER3;

    @EventBusSubscriber(modid = "visualcrafting", bus = EventBusSubscriber.Bus.GAME)
    public static class CreativeTabHandler {
        private static final ResourceKey<CreativeModeTab> FUNCTIONAL_BLOCKS = ResourceKey.create(
                Registries.CREATIVE_MODE_TAB, ResourceLocation.withDefaultNamespace("functional_blocks"));

        @SubscribeEvent
        static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
            if (!Config.isFurnaceCardEnabled()) return;
            if (event.getTabKey().equals(FUNCTIONAL_BLOCKS) && FURNACE_CARD_TIER1 != null) {
                event.accept(FURNACE_CARD_TIER1.get());
                event.accept(FURNACE_CARD_TIER2.get());
                event.accept(FURNACE_CARD_TIER3.get());
            }
        }
    }

    @EventBusSubscriber(modid = "visualcrafting", bus = EventBusSubscriber.Bus.MOD)
    public static class RegistrationHandler {
        @SubscribeEvent
        static void onCommonSetup(FMLCommonSetupEvent event) {
            if (!Config.isFurnaceCardEnabled()) return;
            if (FURNACE_CARD_TIER1 != null) {
                event.enqueueWork(() -> {
                    System.err.println("[VisualCrafting] Registering furnace cards as ME Interface upgrades...");
                    System.err.println("[VisualCrafting] FURNACE_CARD_TIER1.get() = "
                            + FURNACE_CARD_TIER1.get() + " (class: "
                            + FURNACE_CARD_TIER1.get().getClass().getName() + ", identity: "
                            + System.identityHashCode(FURNACE_CARD_TIER1.get()) + ")");
                    System.err.println("[VisualCrafting] AEBlocks.INTERFACE = " + AEBlocks.INTERFACE
                            + " (class: " + AEBlocks.INTERFACE.getClass().getName() + ")");
                    System.err.println("[VisualCrafting] AEBlocks.INTERFACE.asItem() = "
                            + AEBlocks.INTERFACE.asItem() + " (class: "
                            + AEBlocks.INTERFACE.asItem().getClass().getName() + ", identity: "
                            + System.identityHashCode(AEBlocks.INTERFACE.asItem()) + ")");
                    System.err.println("[VisualCrafting] AEParts.INTERFACE = " + AEParts.INTERFACE
                            + " (class: " + AEParts.INTERFACE.getClass().getName() + ")");
                    System.err.println("[VisualCrafting] AEParts.INTERFACE.asItem() = "
                            + AEParts.INTERFACE.asItem() + " (class: "
                            + AEParts.INTERFACE.asItem().getClass().getName() + ", identity: "
                            + System.identityHashCode(AEParts.INTERFACE.asItem()) + ")");

                    String blockAe2Interface = "block.ae2.interface";
                    Upgrades.add(FURNACE_CARD_TIER1.get(), AEBlocks.INTERFACE, 1, blockAe2Interface);
                    System.err.println("[VisualCrafting] Upgrades.add(TIER1, AEBlocks.INTERFACE) done");
                    Upgrades.add(FURNACE_CARD_TIER1.get(), AEParts.INTERFACE, 1, blockAe2Interface);
                    Upgrades.add(FURNACE_CARD_TIER2.get(), AEBlocks.INTERFACE, 1, blockAe2Interface);
                    Upgrades.add(FURNACE_CARD_TIER2.get(), AEParts.INTERFACE, 1, blockAe2Interface);
                    Upgrades.add(FURNACE_CARD_TIER3.get(), AEBlocks.INTERFACE, 1, blockAe2Interface);
                    Upgrades.add(FURNACE_CARD_TIER3.get(), AEParts.INTERFACE, 1, blockAe2Interface);

                    System.err.println("[VisualCrafting] Verify: getMaxInstallable(TIER1, AEBlocks.INTERFACE) = "
                            + Upgrades.getMaxInstallable(FURNACE_CARD_TIER1.get(), AEBlocks.INTERFACE));
                    System.err.println("[VisualCrafting] Verify: getMaxInstallable(TIER1, AEParts.INTERFACE) = "
                            + Upgrades.getMaxInstallable(FURNACE_CARD_TIER1.get(), AEParts.INTERFACE));

                    if (ModList.get().isLoaded("megacells")) {
                        String megaInterface = "block.megacells.mega_interface";
                        BuiltInRegistries.ITEM.getOptional(
                                ResourceLocation.fromNamespaceAndPath("megacells", "mega_interface"))
                                .ifPresent(item -> {
                                    Upgrades.add(FURNACE_CARD_TIER1.get(), item, 1, megaInterface);
                                    Upgrades.add(FURNACE_CARD_TIER2.get(), item, 1, megaInterface);
                                    Upgrades.add(FURNACE_CARD_TIER3.get(), item, 1, megaInterface);
                                });
                        BuiltInRegistries.ITEM.getOptional(
                                ResourceLocation.fromNamespaceAndPath("megacells", "cable_mega_interface"))
                                .ifPresent(item -> {
                                    Upgrades.add(FURNACE_CARD_TIER1.get(), item, 1, megaInterface);
                                    Upgrades.add(FURNACE_CARD_TIER2.get(), item, 1, megaInterface);
                                    Upgrades.add(FURNACE_CARD_TIER3.get(), item, 1, megaInterface);
                                });
                    }
                });
            }
        }

        static {
            if (ModList.get().isLoaded("ae2")) {
                FURNACE_CARD_TIER1 = VisualCraftingTable.ITEMS.register("furnace_card_tier1", () -> {
                    FurnaceCardItem item = new FurnaceCardItem(new Item.Properties().stacksTo(1), 1);
                    FurnaceCardItem.FURNACE_CARD_TIER1 = item;
                    return item;
                });
                FURNACE_CARD_TIER2 = VisualCraftingTable.ITEMS.register("furnace_card_tier2", () -> {
                    FurnaceCardItem item = new FurnaceCardItem(new Item.Properties().stacksTo(1), 2);
                    FurnaceCardItem.FURNACE_CARD_TIER2 = item;
                    return item;
                });
                FURNACE_CARD_TIER3 = VisualCraftingTable.ITEMS.register("furnace_card_tier3", () -> {
                    FurnaceCardItem item = new FurnaceCardItem(new Item.Properties().stacksTo(1), 3);
                    FurnaceCardItem.FURNACE_CARD_TIER3 = item;
                    return item;
                });
            }
        }
    }
}
