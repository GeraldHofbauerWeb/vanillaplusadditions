package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.blockentity;

import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.AxolotlGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.block.AbstractAxolotlBowlBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

public abstract class AbstractAxolotlBowlBlockEntity extends BlockEntity {

    private final List<UUID> associatedAxolotls = new ArrayList<>();

    protected AbstractAxolotlBowlBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // --- Fish interface ---

    public abstract boolean hasFish();

    /** Removes and returns one fish item from this bowl/station. Returns EMPTY if none available. */
    public abstract ItemStack takeFish();

    /** Tries to insert the given fish stack. Returns true if at least one item was inserted. */
    public abstract boolean insertFish(ItemStack stack, boolean simulate);

    /**
     * Hands the empty bucket back after an axolotl ate a tropical fish bucket out of this
     * bowl/station — stored if the container has room, dropped on top of the block otherwise.
     * A bucket is worth more than the fish inside it, so it is never silently voided.
     */
    public void returnEmptyBucket() {
        ItemStack leftover = storeLeftover(new ItemStack(Items.BUCKET));
        if (!leftover.isEmpty() && level != null) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                    worldPosition.getZ() + 0.5, leftover);
        }
    }

    /**
     * Tries to keep a by-product (currently only the empty bucket) inside this container.
     * The plain bowl has a single slot reserved for fish, so it stores nothing by default.
     *
     * @param stack the stack to store
     * @return whatever could not be stored
     */
    protected ItemStack storeLeftover(ItemStack stack) {
        return stack;
    }

    // --- Axolotl association ---

    public List<UUID> getAssociatedAxolotls() {
        return Collections.unmodifiableList(associatedAxolotls);
    }

    public void addAxolotl(UUID axolotlUUID) {
        if (!associatedAxolotls.contains(axolotlUUID)) {
            associatedAxolotls.add(axolotlUUID);
            setChanged();
            syncToClient();
        }
    }

    public boolean canAddAxolotl(UUID axolotlUUID) {
        if (associatedAxolotls.contains(axolotlUUID)) {
            return true;
        }
        return associatedAxolotls.size() < AxolotlGuardianModule.getMaxAxolotlsPerStation();
    }

    public void removeAxolotl(UUID axolotlUUID) {
        if (associatedAxolotls.remove(axolotlUUID)) {
            setChanged();
            syncToClient();
        }
    }

    public void clearAxolotls() {
        associatedAxolotls.clear();
        setChanged();
        syncToClient();
    }

    /**
     * Removes stale associations — but only on positive evidence.
     * <p>
     * {@code ServerLevel.getEntity(UUID)} finds loaded entities only, so an axolotl in an
     * unloaded chunk is indistinguishable from one that no longer exists. Treating that as "gone"
     * deleted the association permanently while the axolotl kept pointing at this bowl, and
     * nothing ever restored it. An axolotl we cannot see is therefore left alone; the reverse
     * direction is repaired by {@code AxolotlGuardianModule.reconcileBowlAssociation}.
     */
    public void pruneStaleAssociations() {
        if (!(level instanceof ServerLevel serverLevel) || associatedAxolotls.isEmpty()) {
            return;
        }
        long thisBowl = worldPosition.asLong();
        boolean changed = false;
        Iterator<UUID> iter = associatedAxolotls.iterator();
        while (iter.hasNext()) {
            UUID axolotlUUID = iter.next();
            Entity entity = serverLevel.getEntity(axolotlUUID);
            if (entity == null) {
                continue; // not loaded right now — no evidence either way, keep the association
            }
            if (!(entity instanceof Axolotl axolotl)
                    || !axolotl.isAlive()
                    || axolotl.getData(AxolotlGuardianModule.AXOLOTL_BOWL_POS.get()) != thisBowl) {
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
     * Re-adds axolotls in range that still point at this bowl but are missing from its list.
     * Counterpart to {@link #pruneStaleAssociations()}, which can only ever remove entries — an
     * axolotl unloaded during a prune used to be dropped for good while it went on pointing here.
     * If the station is meanwhile full, the axolotl's pointer is cleared instead, so both sides
     * always agree.
     */
    public void reclaimOwnAxolotls() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        long thisBowl = worldPosition.asLong();
        double range = AxolotlGuardianModule.getGuardRadius() + 16.0;
        AABB box = new AABB(worldPosition).inflate(range);
        for (Axolotl axolotl : serverLevel.getEntitiesOfClass(Axolotl.class, box,
                a -> a.isAlive()
                        && a.getData(AxolotlGuardianModule.AXOLOTL_BOWL_POS.get()) == thisBowl)) {
            if (associatedAxolotls.contains(axolotl.getUUID())) {
                continue;
            }
            if (associatedAxolotls.size() < AxolotlGuardianModule.getMaxAxolotlsPerStation()) {
                addAxolotl(axolotl.getUUID());
            } else {
                axolotl.setData(AxolotlGuardianModule.AXOLOTL_BOWL_POS.get(), Long.MIN_VALUE);
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
        if (!state.hasProperty(AbstractAxolotlBowlBlock.FILLED)) {
            return;
        }
        boolean filled = hasFish();
        if (state.getValue(AbstractAxolotlBowlBlock.FILLED) != filled) {
            level.setBlock(worldPosition, state.setValue(AbstractAxolotlBowlBlock.FILLED, filled),
                    Block.UPDATE_CLIENTS);
        }
    }

    // --- NBT helpers shared by both bowl types ---

    protected void saveAxolotls(CompoundTag tag) {
        ListTag list = new ListTag();
        for (UUID uuid : associatedAxolotls) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", uuid);
            list.add(entry);
        }
        tag.put("axolotls", list);
    }

    protected void loadAxolotls(CompoundTag tag) {
        associatedAxolotls.clear();
        ListTag list = tag.getList("axolotls", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("uuid")) {
                associatedAxolotls.add(entry.getUUID("uuid"));
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
