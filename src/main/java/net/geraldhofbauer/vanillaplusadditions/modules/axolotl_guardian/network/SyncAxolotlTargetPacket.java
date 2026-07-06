package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Syncs a guardian axolotl's current attack target entity ID to nearby clients for goggle overlay rendering. */
public record SyncAxolotlTargetPacket(int axolotlEntityId, int targetEntityId) implements CustomPacketPayload {

    /** targetEntityId = -1 means the axolotl has no target. */
    public static final int NO_TARGET = -1;

    public static final Type<SyncAxolotlTargetPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "sync_axolotl_target"));

    public static final StreamCodec<FriendlyByteBuf, SyncAxolotlTargetPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SyncAxolotlTargetPacket::axolotlEntityId,
                    ByteBufCodecs.INT, SyncAxolotlTargetPacket::targetEntityId,
                    SyncAxolotlTargetPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
