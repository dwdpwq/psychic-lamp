package com.visualcrafting;

import com.visualcrafting.block.ModBlocks;
import com.visualcrafting.block.VisualCraftingBlockEntity;
import com.visualcrafting.block.VisualCraftingBlockItem;
import com.visualcrafting.fluid.LiquidXpFluidType;
import com.visualcrafting.network.ModMessages;
import com.visualcrafting.screen.VisualCraftingMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

@Mod("visualcrafting")
public class VisualCraftingTable {
    public static final String MOD_ID = "visualcrafting";

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("visualcrafting");
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("visualcrafting");
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "visualcrafting");
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, "visualcrafting");
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, "visualcrafting");
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, "visualcrafting");

    public static final DeferredBlock<Block> VISUAL_CRAFTING_BLOCK =
            BLOCKS.register("visual_crafting_table", ModBlocks::createVisualCraftingBlock);

    public static final DeferredItem<Item> VISUAL_CRAFTING_ITEM =
            ITEMS.register("visual_crafting_table",
                    () -> new VisualCraftingBlockItem(VISUAL_CRAFTING_BLOCK.get(), new Item.Properties()));

    public static final Supplier<BlockEntityType<VisualCraftingBlockEntity>> VISUAL_CRAFTING_BE =
            BLOCK_ENTITIES.register("visual_crafting_table",
                    () -> BlockEntityType.Builder.of(VisualCraftingBlockEntity::new, VISUAL_CRAFTING_BLOCK.get()).build(null));

    public static final Supplier<MenuType<VisualCraftingMenu>> VISUAL_CRAFTING_MENU =
            MENU_TYPES.register("visual_crafting_table",
                    () -> IMenuTypeExtension.create(VisualCraftingMenu::new));

    public static final Supplier<FluidType> LIQUID_XP_FLUID_TYPE =
            FLUID_TYPES.register("liquid_xp", LiquidXpFluidType::new);

    public static final Supplier<Fluid> LIQUID_XP =
            FLUIDS.register("liquid_xp",
                    () -> new BaseFlowingFluid.Source(makeLiquidXpProperties()));

    public static final Supplier<Fluid> LIQUID_XP_FLOWING =
            FLUIDS.register("liquid_xp_flowing",
                    () -> new BaseFlowingFluid.Flowing(makeLiquidXpProperties()));

    public static final DeferredItem<Item> LIQUID_XP_BUCKET =
            ITEMS.register("liquid_xp_bucket",
                    () -> new BucketItem(LIQUID_XP.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    private static BaseFlowingFluid.Properties makeLiquidXpProperties() {
        return new BaseFlowingFluid.Properties(LIQUID_XP_FLUID_TYPE, LIQUID_XP, LIQUID_XP_FLOWING);
    }

    public VisualCraftingTable(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);

        ModMessages.register(modEventBus);

        modEventBus.addListener(this::addCreative);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().getPath().equals("functional_blocks")) {
            event.accept(VISUAL_CRAFTING_ITEM.get());
        }
    }
}
