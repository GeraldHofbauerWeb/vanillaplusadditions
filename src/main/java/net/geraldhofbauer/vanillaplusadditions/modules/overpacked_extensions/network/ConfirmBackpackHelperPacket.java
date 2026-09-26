package net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: "I can see the helper entity, you may open the screen now."
 *
 * <p>The counterpart to {@link BackpackHelperReadyPacket}. The server verifies that the id belongs to
 * a helper it spawned for <em>this</em> player before acting on it, so a forged packet can at worst
 * re-open a backpack the sender already owns.</p>
 *
 * @param entityId    the helper entity's network id, echoed back
 * @param compartment 0 = center (main), 1 = right, 2 = left
 */
public record ConfirmBackpackHelperPacket(int entityId, int compartment) implements CustomPacketPayload {

    public static final Type<ConfirmBackpackHelperPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "confirm_backpack_helper"));

    public static final StreamCodec<FriendlyByteBuf, ConfirmBackpackHelperPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ConfirmBackpackHelperPacket::entityId,
                    ByteBufCodecs.VAR_INT, ConfirmBackpackHelperPacket::compartment,
                    ConfirmBackpackHelperPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
