package com.visualcrafting.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ExtractFurnaceExpPacket(BlockPos pos) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ExtractFurnaceExpPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("visualcrafting", "extract_furnace_exp"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExtractFurnaceExpPacket> STREAM_CODEC =
            StreamCodec.of(ExtractFurnaceExpPacket::encode, ExtractFurnaceExpPacket::decode);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ExtractFurnaceExpPacket packet) {
        buf.writeBlockPos(packet.pos);
    }

    private static ExtractFurnaceExpPacket decode(RegistryFriendlyByteBuf buf) {
        return new ExtractFurnaceExpPacket(buf.readBlockPos());
    }
}
