package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client requests the link settings screen for a block. Sent from the client-side Ctrl+right-click
 * intercept — the modifier key is never transmitted by the vanilla interaction packet, so the
 * decision has to be made client-side and reported explicitly.
 */
public record RequestOpenLinkScreenPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<RequestOpenLinkScreenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "request_open_link_screen"));

    public static final StreamCodec<FriendlyByteBuf, RequestOpenLinkScreenPacket> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, RequestOpenLinkScreenPacket::pos,
                    RequestOpenLinkScreenPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
