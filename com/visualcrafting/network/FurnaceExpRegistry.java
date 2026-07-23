package com.visualcrafting.network;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.blockentity.misc.InterfaceBlockEntity;
import com.visualcrafting.item.FurnaceCardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = "visualcrafting", bus = EventBusSubscriber.Bus.MOD)
public class FurnaceExpRegistry {
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
                ExtractFurnaceExpPacket.TYPE,
                ExtractFurnaceExpPacket.STREAM_CODEC,
                FurnaceExpRegistry::handleExtractFurnaceExp);
    }

    private static void handleExtractFurnaceExp(ExtractFurnaceExpPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                ServerPlayer player = (ServerPlayer) context.player();
                BlockPos pos = packet.pos();
                BlockEntity blockEntity = player.level().getBlockEntity(pos);
                if (!(blockEntity instanceof InterfaceBlockEntity iface)) {
                    return;
                }
                if (iface.getMainNode() == null || iface.getMainNode().getGrid() == null) {
                    return;
                }
                IUpgradeInventory upgrades = iface.getUpgrades();

                int totalExpMilli = 0;
                for (int i = 0; i < upgrades.size(); i++) {
                    ItemStack stack = upgrades.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    Item item = stack.getItem();
                    if (!(item instanceof FurnaceCardItem)) continue;
                    int stored = FurnaceCardItem.getStoredExpMilli(stack);
                    if (stored <= 0) continue;
                    totalExpMilli += stored;
                    FurnaceCardItem.setStoredExpMilli(stack, 0);
                }

                if (totalExpMilli <= 0) {
                    return;
                }

                int points = totalExpMilli / 1000;
                if (points > 0) {
                    player.giveExperiencePoints(points);
                    player.displayClientMessage(
                            Component.literal("Gave " + points + " experience points to player"), false);
                }
            } catch (Exception e) {
                System.err.println("[VC] Failed to extract furnace exp: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}
