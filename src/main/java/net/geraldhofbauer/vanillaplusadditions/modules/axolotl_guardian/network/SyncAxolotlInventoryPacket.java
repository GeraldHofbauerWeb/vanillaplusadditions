package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record SyncAxolotlInventoryPacket(int entityId, ItemStack armorStack) implements CustomPacketPayload {

    public static final Type<SyncAxolotlInventoryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "sync_axolotl_inventory"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncAxolotlInventoryPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SyncAxolotlInventoryPacket::entityId,
                    ItemStack.OPTIONAL_STREAM_CODEC, SyncAxolotlInventoryPacket::armorStack,
                    SyncAxolotlInventoryPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
