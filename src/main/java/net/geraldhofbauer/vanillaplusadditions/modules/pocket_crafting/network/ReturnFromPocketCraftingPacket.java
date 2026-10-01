package net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the pocket grid was closed with Escape, please reopen the container it was
 * opened from.
 *
 * <p>Carries nothing. Which container that is, and whether reopening it is allowed at all, is known
 * only to the server - a client cannot name a container here, so it cannot ask for one it never had
 * open.</p>
 */
public record ReturnFromPocketCraftingPacket() implements CustomPacketPayload {

    public static final ReturnFromPocketCraftingPacket INSTANCE = new ReturnFromPocketCraftingPacket();

    public static final Type<ReturnFromPocketCraftingPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "return_from_pocket_crafting"));

    public static final StreamCodec<FriendlyByteBuf, ReturnFromPocketCraftingPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
