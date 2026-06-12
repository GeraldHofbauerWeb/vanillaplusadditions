package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity;

import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.block.AbstractCatBowlBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public abstract class AbstractCatBowlBlockEntity extends BlockEntity {

    private final List<UUID> associatedCats = new ArrayList<>();

    protected AbstractCatBowlBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // --- Fish interface ---

    public abstract boolean hasFish();

    /** Removes and returns one fish item from this bowl/station. Returns EMPTY if none available. */
    public abstract ItemStack takeFish();

    /** Tries to insert the given fish stack. Returns true if at least one item was inserted. */
    public abstract boolean insertFish(ItemStack stack, boolean simulate);

    // --- Cat association ---

    public List<UUID> getAssociatedCats() {
        return Collections.unmodifiableList(associatedCats);
    }

    public void addCat(UUID catUUID) {
        if (!associatedCats.contains(catUUID)) {
            associatedCats.add(catUUID);
            setChanged();
            syncToClient();
        }
    }

    public boolean canAddCat(UUID catUUID) {
        if (associatedCats.contains(catUUID)) {
            return true;
        }
        return associatedCats.size() < net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule.getMaxCatsPerStation();
    }

    public void removeCat(UUID catUUID) {
        if (associatedCats.remove(catUUID)) {
            setChanged();
            syncToClient();
        }
    }

    public void clearCats() {
        associatedCats.clear();
        setChanged();
        syncToClient();
    }

    protected void syncToClient() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    protected void updateFilledState() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = level.getBlockState(worldPosition);
        if (!state.hasProperty(AbstractCatBowlBlock.FILLED)) {
            return;
        }
        boolean filled = hasFish();
        if (state.getValue(AbstractCatBowlBlock.FILLED) != filled) {
            level.setBlock(worldPosition, state.setValue(AbstractCatBowlBlock.FILLED, filled),
                    Block.UPDATE_CLIENTS);
        }
    }

    // --- NBT helpers shared by both bowl types ---

    protected void saveCats(CompoundTag tag) {
        ListTag list = new ListTag();
        for (UUID uuid : associatedCats) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", uuid);
            list.add(entry);
        }
        tag.put("cats", list);
    }

    protected void loadCats(CompoundTag tag) {
        associatedCats.clear();
        ListTag list = tag.getList("cats", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("uuid")) {
                associatedCats.add(entry.getUUID("uuid"));
            }
        }
    }

    // --- Sync packet ---

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
