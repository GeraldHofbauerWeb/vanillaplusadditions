package net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server request to mount a wolf, sent when the player modifier-right-clicks it
 * (default Ctrl, see {@code WolfMountKeybinds}).
 *
 * <p>Why a packet rather than handling it purely in the server-side interact event: the modifier
 * key is client-only state. The server sees an ordinary right-click and cannot tell a mount attempt
 * from a plain one.
 *
 * <p>Ordering matters and is guaranteed: {@code MultiPlayerGameMode.interact} sends the vanilla
 * {@code ServerboundInteractPacket} <em>before</em> it runs the client-side interact event where
 * this packet is dispatched. Both travel the same connection in order, so the server always
 * processes the vanilla interaction first and this packet second — which is why the handler can
 * clear the sit state that vanilla's right-click just toggled.
 *
 * <p>Everything in here is untrusted: the handler re-checks module state, entity type, ownership,
 * eligibility and distance.
 *
 * @param wolfId entity id of the wolf the player wants to mount
 */
public record MountWolfPacket(int wolfId) implements CustomPacketPayload {

    public static final Type<MountWolfPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "mount_wolf"));

    public static final StreamCodec<FriendlyByteBuf, MountWolfPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MountWolfPacket::wolfId,
                    MountWolfPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
