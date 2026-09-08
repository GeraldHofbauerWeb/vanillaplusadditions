package net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.network;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: which way a wolf just bit.
 *
 * <p>The bite lunge needs a direction, and the client cannot work one out on its own. The obvious
 * source, {@code Mob.getTarget()}, is server-side AI state that is never synced — on the client it
 * is always null. The next best guess, the wolf's head yaw, is right for a loose wolf and wrong for
 * the one case that matters: while a player rides it, {@code tickRidden} overwrites both head and
 * body rotation from the <em>rider's</em> look every tick, so the head points wherever the rider is
 * looking and never at the victim.
 *
 * <p>So the server, which is the only side that knows, says so — once per landed bite, to the
 * players tracking that wolf. A yaw rather than the victim's entity id: it is one float instead of
 * a lookup that can fail, it stays correct if the victim dies from the same hit, and the animation
 * needs nothing else.
 *
 * @param wolfId entity id of the biting wolf
 * @param yaw    yaw in degrees from the wolf towards what it bit, in the same convention as
 *               {@code Entity.getYRot()}
 */
public record WolfBiteDirectionPacket(int wolfId, float yaw) implements CustomPacketPayload {

    public static final Type<WolfBiteDirectionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "wolf_bite_direction"));

    public static final StreamCodec<FriendlyByteBuf, WolfBiteDirectionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, WolfBiteDirectionPacket::wolfId,
                    ByteBufCodecs.FLOAT, WolfBiteDirectionPacket::yaw,
                    WolfBiteDirectionPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
