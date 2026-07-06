package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Syncs a guardian axolotl's current navigation path nodes to nearby clients for overlay rendering. */
public record SyncAxolotlPathPacket(int axolotlEntityId, int[] nodeX, int[] nodeY, int[] nodeZ, int nextNodeIndex)
        implements CustomPacketPayload {

    public static final int[] EMPTY = new int[0];

    public static final Type<SyncAxolotlPathPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "sync_axolotl_path"));

    public static final StreamCodec<FriendlyByteBuf, SyncAxolotlPathPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeInt(pkt.axolotlEntityId());
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
                int axolotlId = buf.readInt();
                int n = buf.readInt();
                int[] xs = new int[n], ys = new int[n], zs = new int[n];
                for (int i = 0; i < n; i++) {
                    xs[i] = buf.readInt();
                    ys[i] = buf.readInt();
                    zs[i] = buf.readInt();
                }
                return new SyncAxolotlPathPacket(axolotlId, xs, ys, zs, buf.readInt());
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
