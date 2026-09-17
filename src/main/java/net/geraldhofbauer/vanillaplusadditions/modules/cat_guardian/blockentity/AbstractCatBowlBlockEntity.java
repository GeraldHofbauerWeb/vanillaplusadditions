package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity;

import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.block.AbstractCatBowlBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
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
        return associatedCats.size() < CatGuardianModule.getMaxCatsPerStation();
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

    /**
     * Removes stale associations — but only on positive evidence.
     * <p>
     * {@code ServerLevel.getEntity(UUID)} finds loaded entities only, so a cat in an unloaded
     * chunk is indistinguishable from a cat that no longer exists. Treating that as "gone" used
     * to delete the association permanently while the cat itself kept pointing at this bowl, and
     * nothing ever restored it: the station silently lost guardians, showing e.g. 1/8 with two
     * cats sitting on it. A cat we cannot see is therefore left alone; only a cat we can actually
     * inspect — and that is dead or bound elsewhere — is dropped. The reverse direction is
     * repaired by {@code CatGuardianModule.reconcileBowlAssociation}.
     */
    public void pruneStaleAssociations() {
        if (!(level instanceof ServerLevel serverLevel) || associatedCats.isEmpty()) {
            return;
        }
        long thisBowl = worldPosition.asLong();
        boolean changed = false;
        Iterator<UUID> iter = associatedCats.iterator();
        while (iter.hasNext()) {
            UUID catUUID = iter.next();
            Entity entity = serverLevel.getEntity(catUUID);
            if (entity == null) {
                continue; // not loaded right now — no evidence either way, keep the association
            }
            if (!(entity instanceof Cat cat)
                    || !cat.isAlive()
                    || cat.getData(CatGuardianModule.CAT_BOWL_POS.get()) != thisBowl) {
                iter.remove();
                changed = true;
            }
        }
        if (changed) {
            setChanged();
            syncToClient();
        }
    }

    /**
     * Re-adds cats in range that still point at this bowl but are missing from its list.
     * <p>
     * Counterpart to {@link #pruneStaleAssociations()}, which can only ever remove entries. The
     * association is stored twice — as a UUID list here and as {@code CAT_BOWL_POS} on the cat —
     * and only this side used to be repaired. A cat that was unloaded during a prune was dropped
     * for good while it went on pointing at this bowl: it kept guarding, but the station no
     * longer counted it and it no longer glowed. The cat's own pointer is the authority.
     * <p>
     * If the station is meanwhile full, the cat's pointer is cleared instead, because an
     * association one side refuses is not an association; the cat is then free to bind elsewhere.
     */
    public void reclaimOwnCats() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long thisBowl = worldPosition.asLong();
        double range = CatGuardianModule.getGuardRadius() + 16.0;
        AABB box = new AABB(worldPosition).inflate(range);
        for (Cat cat : serverLevel.getEntitiesOfClass(Cat.class, box,
                c -> c.isAlive() && c.getData(CatGuardianModule.CAT_BOWL_POS.get()) == thisBowl)) {
            if (associatedCats.contains(cat.getUUID())) {
                continue;
            }
            if (associatedCats.size() < CatGuardianModule.getMaxCatsPerStation()) {
                addCat(cat.getUUID());
            } else {
                cat.setData(CatGuardianModule.CAT_BOWL_POS.get(), Long.MIN_VALUE);
            }
        }
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
