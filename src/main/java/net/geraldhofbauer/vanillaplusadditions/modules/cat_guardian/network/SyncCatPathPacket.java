package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Syncs a guardian cat's current navigation path nodes to nearby clients for overlay rendering. */
public record SyncCatPathPacket(int catEntityId, int[] nodeX, int[] nodeY, int[] nodeZ, int nextNodeIndex)
        implements CustomPacketPayload {

    public static final int[] EMPTY = new int[0];

    public static final Type<SyncCatPathPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "sync_cat_path"));

    public static final StreamCodec<FriendlyByteBuf, SyncCatPathPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeInt(pkt.catEntityId());
                int n = pkt.nodeX().length;
                buf.writeInt(n);
                for (int i = 0; i < n; i++) {
                    buf.writeInt(pkt.nodeX()[i]);
                    buf.writeInt(pkt.nodeY()[i]);
                    buf.writeInt(pkt.nodeZ()[i]);
                }
                buf.writeInt(pkt.nextNodeIndex());
            },
            buf -> {
                int catId = buf.readInt();
                int n = buf.readInt();
                int[] xs = new int[n], ys = new int[n], zs = new int[n];
                for (int i = 0; i < n; i++) {
                    xs[i] = buf.readInt();
                    ys[i] = buf.readInt();
                    zs[i] = buf.readInt();
                }
                return new SyncCatPathPacket(catId, xs, ys, zs, buf.readInt());
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
