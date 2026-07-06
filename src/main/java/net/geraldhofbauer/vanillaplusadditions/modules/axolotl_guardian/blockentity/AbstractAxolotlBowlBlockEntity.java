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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

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
     * Removes stale associations (missing/dead axolotls or axolotls bound to another bowl/station).
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
