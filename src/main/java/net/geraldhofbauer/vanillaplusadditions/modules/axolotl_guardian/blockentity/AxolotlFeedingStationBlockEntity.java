package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.blockentity;

import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.AxolotlGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.block.AxolotlFeedingStationBlock;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.block.AxolotlStationSkin;
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
            return AxolotlGuardianModule.isStationFood(stack);
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

    /**
     * Single deco slot: a matching material item (coral, prismarine, copper, ...) reskins the
     * station block via the {@link AxolotlFeedingStationBlock#SKIN} blockstate property. The item
     * stays in the slot and comes back when removed or when the station is broken.
     */
    private final ItemStackHandler skinInventory = new ItemStackHandler(1) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return AxolotlStationSkin.isSkinItem(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            syncToClient();
            applySkinFromSlot();
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

    /**
     * Returns the index of the first slot holding actual food, or -1 if there is none. Skips
     * non-food by-products — a returned empty bucket must never be handed to an axolotl as a meal
     * (it would be consumed and lost), nor be shown as the station's content.
     */
    public int getActiveSlot() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (AxolotlGuardianModule.isStationFood(inventory.getStackInSlot(i))) {
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

    /**
     * Keeps the empty bucket in the station's own inventory so a pipe/funnel can pull it back out;
     * only a completely full station falls back to dropping it.
     */
    @Override
    protected ItemStack storeLeftover(ItemStack stack) {
        // setStackInSlot on purpose, NOT insertItem: isItemValid only allows food, and the empty
        // bucket must stay non-insertable from the outside — it is a by-product, not an input.
        // Only ever called with a single item, hence the simple merge.
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack slot = inventory.getStackInSlot(i);
            if (slot.isEmpty()) {
                inventory.setStackInSlot(i, stack);
                return ItemStack.EMPTY;
            }
            if (ItemStack.isSameItemSameComponents(slot, stack)
                    && slot.getCount() + stack.getCount() <= slot.getMaxStackSize()) {
                ItemStack merged = slot.copy();
                merged.grow(stack.getCount());
                inventory.setStackInSlot(i, merged);
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    @Override
    public boolean insertFish(ItemStack stack, boolean simulate) {
        if (!AxolotlGuardianModule.isStationFood(stack)) {
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

    public ItemStackHandler getSkinInventory() {
        return skinInventory;
    }

    /** Mirrors the skin-slot content into the block's SKIN state (server-side only). */
    private void applySkinFromSlot() {
        Level lvl = getLevel();
        if (lvl == null || lvl.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(AxolotlFeedingStationBlock.SKIN)) {
            return;
        }
        AxolotlStationSkin skin = AxolotlStationSkin.forItem(skinInventory.getStackInSlot(0));
        if (state.getValue(AxolotlFeedingStationBlock.SKIN) != skin) {
            lvl.setBlock(worldPosition, state.setValue(AxolotlFeedingStationBlock.SKIN, skin), 3);
        }
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
        tag.put("skin_inventory", skinInventory.serializeNBT(registries));
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
        if (tag.contains("skin_inventory")) {
            skinInventory.deserializeNBT(registries, tag.getCompound("skin_inventory"));
        }
        storedXp = tag.getInt("stored_xp");
        loadAxolotls(tag);
    }
}
