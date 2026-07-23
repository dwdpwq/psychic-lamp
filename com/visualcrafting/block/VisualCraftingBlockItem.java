package com.visualcrafting.block;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

public class VisualCraftingBlockItem extends BlockItem {
    public VisualCraftingBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (context.getPlayer() != null && !context.getPlayer().isCreative()) {
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }
}
