package net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: the transient helper backpack has been spawned, its entity id is {@code entityId}.
 *
 * <p>The client answers with {@link ConfirmBackpackHelperPacket} <em>once it can actually resolve
 * that id</em>. Only then does the server open the screen. That handshake exists because Overpacked's
 * client-side menu factory does {@code level.getEntity(id).inv[…]} without a null check: opening the
 * screen a moment too early throws a {@link NullPointerException} inside NeoForge's advanced-open-screen
 * handler, which answers by <b>disconnecting the client</b>. Guessing at a fixed delay is not good
 * enough — see {@code docs/modules/overpacked_extensions.md}.</p>
 *
 * @param entityId    the helper entity's network id
 * @param compartment 0 = center (main), 1 = right, 2 = left
 */
public record BackpackHelperReadyPacket(int entityId, int compartment) implements CustomPacketPayload {

    public static final Type<BackpackHelperReadyPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "backpack_helper_ready"));

    public static final StreamCodec<FriendlyByteBuf, BackpackHelperReadyPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BackpackHelperReadyPacket::entityId,
                    ByteBufCodecs.VAR_INT, BackpackHelperReadyPacket::compartment,
                    BackpackHelperReadyPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
