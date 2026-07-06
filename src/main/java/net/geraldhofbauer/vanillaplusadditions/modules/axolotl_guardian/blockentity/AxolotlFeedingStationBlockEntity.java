package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.blockentity;

import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.AxolotlGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.menu.AxolotlFeedingStationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public class AxolotlFeedingStationBlockEntity extends AbstractAxolotlBowlBlockEntity implements MenuProvider {

    private static final int SLOTS = 9;

    private int storedXp = 0;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return AxolotlGuardianModule.isAxolotlFood(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            syncToClient();
            updateFilledState();
        }
    };

    private final ItemStackHandler lootInventory = new ItemStackHandler(15) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            syncToClient();
        }
    };

    public AxolotlFeedingStationBlockEntity(BlockPos pos, BlockState state) {
        super(AxolotlGuardianModule.AXOLOTL_FEEDING_STATION_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AxolotlFeedingStationBlockEntity station) {
        if (level.isClientSide() || level.getGameTime() % 200L != 0L) {
            return;
        }
        station.pruneStaleAssociations();
    }

    /** Returns the index of the first non-empty slot, or -1 if all slots are empty. */
    public int getActiveSlot() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean hasFish() {
        return getActiveSlot() >= 0;
    }

    @Override
    public ItemStack takeFish() {
        int active = getActiveSlot();
        if (active < 0) {
            return ItemStack.EMPTY;
        }
        return inventory.extractItem(active, 1, false);
    }

    @Override
    public boolean insertFish(ItemStack stack, boolean simulate) {
        if (!AxolotlGuardianModule.isAxolotlFood(stack)) {
            return false;
        }
        ItemStack remaining = stack.copy();
        for (int i = 0; i < inventory.getSlots() && !remaining.isEmpty(); i++) {
            remaining = inventory.insertItem(i, remaining, simulate);
        }
        return remaining.getCount() < stack.getCount();
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public ItemStackHandler getLootInventory() {
        return lootInventory;
    }

    public int getStoredXp() {
        return storedXp;
    }

    public void addStoredXp(int delta) {
        storedXp = Math.max(0, storedXp + delta);
        setChanged();
        syncToClient();
    }

    // ---- MenuProvider ----

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.vanillaplusadditions.axolotl_feeding_station");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new AxolotlFeedingStationMenu(id, playerInventory, this);
    }

    // ---- NBT ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.put("loot_inventory", lootInventory.serializeNBT(registries));
        tag.putInt("stored_xp", storedXp);
        saveAxolotls(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inventory")) {
            inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        }
        if (tag.contains("loot_inventory")) {
            lootInventory.deserializeNBT(registries, tag.getCompound("loot_inventory"));
        }
        storedXp = tag.getInt("stored_xp");
        loadAxolotls(tag);
    }
}
