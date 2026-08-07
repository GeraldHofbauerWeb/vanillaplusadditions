package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client asks which links the block it is looking at takes part in. */
public record RequestLinkOverlayPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<RequestLinkOverlayPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "request_link_overlay"));

    public static final StreamCodec<FriendlyByteBuf, RequestLinkOverlayPacket> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, RequestLinkOverlayPacket::pos,
                    RequestLinkOverlayPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
