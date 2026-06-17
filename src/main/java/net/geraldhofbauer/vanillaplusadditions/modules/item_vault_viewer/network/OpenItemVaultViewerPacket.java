package net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;

public record OpenItemVaultViewerPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<OpenItemVaultViewerPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "open_item_vault_viewer"));

    public static final StreamCodec<FriendlyByteBuf, OpenItemVaultViewerPacket> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, OpenItemVaultViewerPacket::pos, OpenItemVaultViewerPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
