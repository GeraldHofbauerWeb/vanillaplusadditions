package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record SyncCatInventoryPacket(int entityId, ItemStack armorStack) implements CustomPacketPayload {

    public static final Type<SyncCatInventoryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "sync_cat_inventory"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCatInventoryPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SyncCatInventoryPacket::entityId,
                    ItemStack.OPTIONAL_STREAM_CODEC, SyncCatInventoryPacket::armorStack,
                    SyncCatInventoryPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
