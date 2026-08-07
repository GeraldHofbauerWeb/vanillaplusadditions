package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * One link as the client needs to draw and edit it. The endpoint boxes are resolved on the server
 * (a Create vault reports its whole multiblock), so the client needs no Create access at all.
 *
 * @param first         the endpoint clicked first — the output side when both ends allow both
 *                      directions
 * @param second        the endpoint clicked second
 * @param firstMode     {@code LinkMode} id of the first endpoint
 * @param secondMode    {@code LinkMode} id of the second endpoint
 * @param firstBox      bounding box of the first endpoint's whole structure
 * @param secondBox     bounding box of the second endpoint's whole structure
 * @param anchorIsFirst whether {@code first} is the endpoint belonging to the block the player
 *                      queried — the two ends of a link are interchangeable to the player, but not
 *                      to the transfer direction, so the server resolves this
 */
public record LinkDisplay(BlockPos first, BlockPos second, byte firstMode, byte secondMode,
                          AABB firstBox, AABB secondBox, boolean anchorIsFirst) {

    public static final StreamCodec<FriendlyByteBuf, LinkDisplay> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public LinkDisplay decode(FriendlyByteBuf buf) {
                    BlockPos first = buf.readBlockPos();
                    BlockPos second = buf.readBlockPos();
                    byte firstMode = buf.readByte();
                    byte secondMode = buf.readByte();
                    AABB firstBox = readBox(buf);
                    AABB secondBox = readBox(buf);
                    return new LinkDisplay(first, second, firstMode, secondMode, firstBox, secondBox,
                            buf.readBoolean());
                }

                @Override
                public void encode(FriendlyByteBuf buf, LinkDisplay value) {
                    buf.writeBlockPos(value.first());
                    buf.writeBlockPos(value.second());
                    buf.writeByte(value.firstMode());
                    buf.writeByte(value.secondMode());
                    writeBox(buf, value.firstBox());
                    writeBox(buf, value.secondBox());
                    buf.writeBoolean(value.anchorIsFirst());
                }
            };

    public static final StreamCodec<FriendlyByteBuf, List<LinkDisplay>> LIST_STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public List<LinkDisplay> decode(FriendlyByteBuf buf) {
                    int size = buf.readVarInt();
                    List<LinkDisplay> links = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        links.add(LinkDisplay.STREAM_CODEC.decode(buf));
                    }
                    return links;
                }

                @Override
                public void encode(FriendlyByteBuf buf, List<LinkDisplay> value) {
                    buf.writeVarInt(value.size());
                    for (LinkDisplay link : value) {
                        LinkDisplay.STREAM_CODEC.encode(buf, link);
                    }
                }
            };

    /** The endpoint on the structure the player queried. */
    public BlockPos nearPos() {
        return anchorIsFirst ? first : second;
    }

    public BlockPos farPos() {
        return anchorIsFirst ? second : first;
    }

    public byte nearMode() {
        return anchorIsFirst ? firstMode : secondMode;
    }

    public byte farMode() {
        return anchorIsFirst ? secondMode : firstMode;
    }

    public AABB nearBox() {
        return anchorIsFirst ? firstBox : secondBox;
    }

    public AABB farBox() {
        return anchorIsFirst ? secondBox : firstBox;
    }

    private static AABB readBox(FriendlyByteBuf buf) {
        return new AABB(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static void writeBox(FriendlyByteBuf buf, AABB box) {
        buf.writeDouble(box.minX);
        buf.writeDouble(box.minY);
        buf.writeDouble(box.minZ);
        buf.writeDouble(box.maxX);
        buf.writeDouble(box.maxY);
        buf.writeDouble(box.maxZ);
    }
}
