package net.geraldhofbauer.vanillaplusadditions.modules.inventory_link.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * One link between two inventory blocks in the same level. {@code first} is the block that was
 * clicked first when the link was created — its click order is the tie-break for the transfer
 * direction when both endpoints are set to {@link LinkMode#BOTH}.
 *
 * <p>Endpoint positions are stored as explicit x/y/z ints, NOT {@link BlockPos#asLong()}:
 * Sable/Aeronautics physics platforms live at plot coordinates around x/z ≈ 20.48 million,
 * uncomfortably close to the 26-bit ±33.5M limit of the packed representation.</p>
 */
public final class InventoryLink {

    private final BlockPos first;
    private final BlockPos second;
    private LinkMode firstMode;
    private LinkMode secondMode;

    public InventoryLink(BlockPos first, BlockPos second, LinkMode firstMode, LinkMode secondMode) {
        this.first = first.immutable();
        this.second = second.immutable();
        this.firstMode = firstMode;
        this.secondMode = secondMode;
    }

    public BlockPos first() {
        return first;
    }

    public BlockPos second() {
        return second;
    }

    public LinkMode firstMode() {
        return firstMode;
    }

    public LinkMode secondMode() {
        return secondMode;
    }

    public void setModes(LinkMode firstMode, LinkMode secondMode) {
        this.firstMode = firstMode;
        this.secondMode = secondMode;
    }

    public boolean touches(BlockPos pos) {
        return first.equals(pos) || second.equals(pos);
    }

    /** Order-insensitive endpoint match. */
    public boolean matches(BlockPos a, BlockPos b) {
        return (first.equals(a) && second.equals(b)) || (first.equals(b) && second.equals(a));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("first_x", first.getX());
        tag.putInt("first_y", first.getY());
        tag.putInt("first_z", first.getZ());
        tag.putInt("second_x", second.getX());
        tag.putInt("second_y", second.getY());
        tag.putInt("second_z", second.getZ());
        tag.putByte("first_mode", firstMode.id());
        tag.putByte("second_mode", secondMode.id());
        return tag;
    }

    public static InventoryLink load(CompoundTag tag) {
        return new InventoryLink(
                new BlockPos(tag.getInt("first_x"), tag.getInt("first_y"), tag.getInt("first_z")),
                new BlockPos(tag.getInt("second_x"), tag.getInt("second_y"), tag.getInt("second_z")),
                LinkMode.fromId(tag.getByte("first_mode")),
                LinkMode.fromId(tag.getByte("second_mode")));
    }
}
