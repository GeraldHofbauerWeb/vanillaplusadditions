package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Server tells the client to open the link settings screen for a block. */
public record OpenLinkScreenPacket(BlockPos pos, List<LinkDisplay> links) implements CustomPacketPayload {

    public static final Type<OpenLinkScreenPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "open_link_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenLinkScreenPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, OpenLinkScreenPacket::pos,
                    LinkDisplay.LIST_STREAM_CODEC, OpenLinkScreenPacket::links,
                    OpenLinkScreenPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
