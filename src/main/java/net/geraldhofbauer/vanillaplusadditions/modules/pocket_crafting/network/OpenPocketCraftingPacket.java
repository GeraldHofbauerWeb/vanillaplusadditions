package net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server request to open the pocket crafting grid.
 *
 * <p>The slot index is not a convenience: without it the packet would be a free "open me a crafting
 * grid anywhere" for any client. The server re-reads that slot from the player's own inventory menu
 * and only opens the grid if a crafting table is actually sitting there.</p>
 *
 * @param slot index of the clicked slot in the player's inventory menu
 */
public record OpenPocketCraftingPacket(int slot) implements CustomPacketPayload {

    public static final Type<OpenPocketCraftingPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "open_pocket_crafting"));

    public static final StreamCodec<FriendlyByteBuf, OpenPocketCraftingPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, OpenPocketCraftingPacket::slot, OpenPocketCraftingPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
