package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data component on the Inventory Linker item: the first block the player selected, waiting for
 * the second click. Network-synchronized, so the client can render the selection outline straight
 * off the held stack without an extra packet.
 *
 * @param dimension the dimension the selection was made in, as {@code Level.dimension().location()}
 * @param pos       the selected block (may be a Sable plot position at ~20.48M coordinates)
 */
public record PendingSelection(String dimension, BlockPos pos) {

    public static final Codec<PendingSelection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("dimension").forGetter(PendingSelection::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(PendingSelection::pos)
    ).apply(instance, PendingSelection::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PendingSelection> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PendingSelection::dimension,
            BlockPos.STREAM_CODEC, PendingSelection::pos,
            PendingSelection::new);
}
