package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncCatStatsPacket(int catId, int xp, int xpCap) implements CustomPacketPayload {

    public static final Type<SyncCatStatsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "sync_cat_stats"));

    public static final StreamCodec<FriendlyByteBuf, SyncCatStatsPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SyncCatStatsPacket::catId,
                    ByteBufCodecs.INT, SyncCatStatsPacket::xp,
                    ByteBufCodecs.INT, SyncCatStatsPacket::xpCap,
                    SyncCatStatsPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
