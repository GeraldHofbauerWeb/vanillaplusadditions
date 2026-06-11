package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity;

import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public class CatBowlBlockEntity extends AbstractCatBowlBlockEntity {

    private final ItemStackHandler fishSlot = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(ItemTags.FISHES);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            syncToClient();
            updateFilledState();
        }
    };

    public CatBowlBlockEntity(BlockPos pos, BlockState state) {
        super(CatGuardianModule.CAT_BOWL_BE.get(), pos, state);
    }

    @Override
    public boolean hasFish() {
        return !fishSlot.getStackInSlot(0).isEmpty();
    }

    @Override
    public ItemStack takeFish() {
        return fishSlot.extractItem(0, 1, false);
    }

    @Override
    public boolean insertFish(ItemStack stack, boolean simulate) {
        if (!stack.is(ItemTags.FISHES)) {
            return false;
        }
        ItemStack remainder = fishSlot.insertItem(0, stack.copy(), simulate);
        return remainder.getCount() < stack.getCount();
    }

    public ItemStackHandler getFishSlot() {
        return fishSlot;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("fish", fishSlot.serializeNBT(registries));
        saveCats(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fish")) {
            fishSlot.deserializeNBT(registries, tag.getCompound("fish"));
        }
        loadCats(tag);
    }
}
