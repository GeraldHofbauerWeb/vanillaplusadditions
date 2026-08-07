package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client edits an existing link from the settings screen.
 *
 * @param anchor     the block whose screen is open — not necessarily a link endpoint, since any
 *                   block of a vault opens the vault's links; the server answers for this block so
 *                   the screen recognises the reply as its own
 * @param action     {@link #ACTION_SET_MODES} or {@link #ACTION_REMOVE}
 * @param firstMode  new {@code LinkMode} id of {@code first} (ignored when removing)
 * @param secondMode new {@code LinkMode} id of {@code second} (ignored when removing)
 */
public record UpdateLinkPacket(BlockPos anchor, BlockPos first, BlockPos second, byte action,
                               byte firstMode, byte secondMode) implements CustomPacketPayload {

    public static final byte ACTION_SET_MODES = 0;
    public static final byte ACTION_REMOVE = 1;

    public static final Type<UpdateLinkPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "update_link"));

    public static final StreamCodec<FriendlyByteBuf, UpdateLinkPacket> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public UpdateLinkPacket decode(FriendlyByteBuf buf) {
                    return new UpdateLinkPacket(buf.readBlockPos(), buf.readBlockPos(), buf.readBlockPos(),
                            buf.readByte(), buf.readByte(), buf.readByte());
                }

                @Override
                public void encode(FriendlyByteBuf buf, UpdateLinkPacket value) {
                    buf.writeBlockPos(value.anchor());
                    buf.writeBlockPos(value.first());
                    buf.writeBlockPos(value.second());
                    buf.writeByte(value.action());
                    buf.writeByte(value.firstMode());
                    buf.writeByte(value.secondMode());
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
