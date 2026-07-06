package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestAxolotlStatsPacket(int axolotlId) implements CustomPacketPayload {

    public static final Type<RequestAxolotlStatsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "request_axolotl_stats"));

    public static final StreamCodec<FriendlyByteBuf, RequestAxolotlStatsPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, RequestAxolotlStatsPacket::axolotlId,
                    RequestAxolotlStatsPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
