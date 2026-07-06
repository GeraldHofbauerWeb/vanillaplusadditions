package net.geraldhofbauer.vanillaplusadditions.modules.overpacked_backpack_keys.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server request to open a compartment of the player's worn Overpacked giant backpack.
 *
 * @param compartment 0 = center (main, 55 slots), 1 = right (28), 2 = left (28)
 */
public record OpenBackpackCompartmentPacket(int compartment) implements CustomPacketPayload {

    public static final Type<OpenBackpackCompartmentPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "open_backpack_compartment"));

    public static final StreamCodec<FriendlyByteBuf, OpenBackpackCompartmentPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, OpenBackpackCompartmentPacket::compartment,
                    OpenBackpackCompartmentPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
