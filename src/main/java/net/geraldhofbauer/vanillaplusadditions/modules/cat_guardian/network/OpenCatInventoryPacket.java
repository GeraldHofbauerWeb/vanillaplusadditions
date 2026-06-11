package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenCatInventoryPacket(int entityId) implements CustomPacketPayload {

    public static final Type<OpenCatInventoryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    VanillaPlusAdditions.MODID, "open_cat_inventory"));

    public static final StreamCodec<FriendlyByteBuf, OpenCatInventoryPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    OpenCatInventoryPacket::entityId,
                    OpenCatInventoryPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
