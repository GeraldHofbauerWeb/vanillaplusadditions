package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncAxolotlStatsPacket(int axolotlId, int xp, int xpCap) implements CustomPacketPayload {

    public static final Type<SyncAxolotlStatsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "sync_axolotl_stats"));

    public static final StreamCodec<FriendlyByteBuf, SyncAxolotlStatsPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SyncAxolotlStatsPacket::axolotlId,
                    ByteBufCodecs.INT, SyncAxolotlStatsPacket::xp,
                    ByteBufCodecs.INT, SyncAxolotlStatsPacket::xpCap,
                    SyncAxolotlStatsPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
