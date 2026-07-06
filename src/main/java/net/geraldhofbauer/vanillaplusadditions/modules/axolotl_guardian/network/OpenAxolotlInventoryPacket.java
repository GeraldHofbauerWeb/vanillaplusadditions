package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenAxolotlInventoryPacket(int entityId) implements CustomPacketPayload {

    public static final Type<OpenAxolotlInventoryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    VanillaPlusAdditions.MODID, "open_axolotl_inventory"));

    public static final StreamCodec<FriendlyByteBuf, OpenAxolotlInventoryPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    OpenAxolotlInventoryPacket::entityId,
                    OpenAxolotlInventoryPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
