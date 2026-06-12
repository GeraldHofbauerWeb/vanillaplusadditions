package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity;

import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.menu.CatFeedingStationMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public class CatFeedingStationBlockEntity extends AbstractCatBowlBlockEntity implements MenuProvider {

    private static final int SLOTS = 9;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isValidFishType(stack);
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

    public CatFeedingStationBlockEntity(BlockPos pos, BlockState state) {
        super(CatGuardianModule.CAT_FEEDING_STATION_BE.get(), pos, state);
    }

    private boolean isValidFishType(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ItemTags.FISHES);
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
        if (!isValidFishType(stack)) {
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

    // ---- MenuProvider ----

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.vanillaplusadditions.cat_feeding_station");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new CatFeedingStationMenu(id, playerInventory, this);
    }

    // ---- NBT ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.put("loot_inventory", lootInventory.serializeNBT(registries));
        saveCats(tag);
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
        loadCats(tag);
    }
}
