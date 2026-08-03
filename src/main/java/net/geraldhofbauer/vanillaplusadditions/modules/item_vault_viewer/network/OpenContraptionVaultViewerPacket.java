package net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C2S: open the Item Vault Viewer for a vault that is part of an assembled Create contraption
 * (train carriage, cart contraption, elevator, ...). {@code localPos} is the block position in
 * contraption-local coordinates, as produced by Create's contraption ray trace.
 */
public record OpenContraptionVaultViewerPacket(int entityId, BlockPos localPos) implements CustomPacketPayload {
    public static final Type<OpenContraptionVaultViewerPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "open_contraption_vault_viewer"));

    public static final StreamCodec<FriendlyByteBuf, OpenContraptionVaultViewerPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, OpenContraptionVaultViewerPacket::entityId,
                    BlockPos.STREAM_CODEC, OpenContraptionVaultViewerPacket::localPos,
                    OpenContraptionVaultViewerPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
