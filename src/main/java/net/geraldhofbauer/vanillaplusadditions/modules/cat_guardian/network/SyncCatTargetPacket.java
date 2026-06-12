package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Syncs a guardian cat's current target entity ID to nearby clients for goggle overlay rendering. */
public record SyncCatTargetPacket(int catEntityId, int targetEntityId) implements CustomPacketPayload {

    /** targetEntityId = -1 means the cat has no target. */
    public static final int NO_TARGET = -1;

    public static final Type<SyncCatTargetPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "sync_cat_target"));

    public static final StreamCodec<FriendlyByteBuf, SyncCatTargetPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SyncCatTargetPacket::catEntityId,
                    ByteBufCodecs.INT, SyncCatTargetPacket::targetEntityId,
                    SyncCatTargetPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
