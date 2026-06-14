package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestCatStatsPacket(int catId) implements CustomPacketPayload {

    public static final Type<RequestCatStatsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "request_cat_stats"));

    public static final StreamCodec<FriendlyByteBuf, RequestCatStatsPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, RequestCatStatsPacket::catId,
                    RequestCatStatsPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
