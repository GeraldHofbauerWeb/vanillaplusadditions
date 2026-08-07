package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server answer to {@link RequestLinkOverlayPacket}. An empty list clears the client's cache. */
public record LinkOverlayPacket(BlockPos queried, List<LinkDisplay> links) implements CustomPacketPayload {

    public static final Type<LinkOverlayPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "link_overlay"));

    public static final StreamCodec<FriendlyByteBuf, LinkOverlayPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, LinkOverlayPacket::queried,
                    LinkDisplay.LIST_STREAM_CODEC, LinkOverlayPacket::links,
                    LinkOverlayPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
