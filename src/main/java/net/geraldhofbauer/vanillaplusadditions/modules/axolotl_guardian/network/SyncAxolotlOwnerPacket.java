package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Syncs an axolotl's owner + bowl assignment to clients. Axolotls are not {@code TamableAnimal}s,
 * so unlike cats there is no vanilla synched owner data — and entity attachments never sync on
 * their own. The client handler writes the values into the client-side attachments so ownership
 * checks work identically on both sides (GUI gating, menu stillValid, goggles overlay, glow).
 *
 * <p>{@code owner} is the owner UUID as string, or {@code ""} for unowned.
 * {@code bowlPos} is the packed bowl position, or {@code Long.MIN_VALUE} for unassigned.
 */
public record SyncAxolotlOwnerPacket(int entityId, String owner, long bowlPos) implements CustomPacketPayload {

    public static final Type<SyncAxolotlOwnerPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "sync_axolotl_owner"));

    public static final StreamCodec<FriendlyByteBuf, SyncAxolotlOwnerPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SyncAxolotlOwnerPacket::entityId,
                    ByteBufCodecs.STRING_UTF8, SyncAxolotlOwnerPacket::owner,
                    ByteBufCodecs.VAR_LONG, SyncAxolotlOwnerPacket::bowlPos,
                    SyncAxolotlOwnerPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
