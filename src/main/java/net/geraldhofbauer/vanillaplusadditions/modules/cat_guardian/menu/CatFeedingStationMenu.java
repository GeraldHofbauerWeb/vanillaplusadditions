package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.menu;

import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.CatFeedingStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

public class CatFeedingStationMenu extends AbstractContainerMenu {

    // Slot ranges (absolute indices in this.slots)
    private static final int FISH_SLOTS   = 9;
    private static final int LOOT_SLOTS   = 15;
    private static final int FISH_END     = FISH_SLOTS;              // 0–8
    private static final int LOOT_END     = FISH_SLOTS + LOOT_SLOTS; // 9–23
    private static final int PLAYER_START = LOOT_END;                // 24–50
    private static final int PLAYER_END   = PLAYER_START + 27;       // 51
    private static final int HOTBAR_END   = PLAYER_END + 9;          // 60

    private final CatFeedingStationBlockEntity blockEntity;

    // Server-side constructor (called directly)
    public CatFeedingStationMenu(int id, Inventory playerInventory, CatFeedingStationBlockEntity be) {
        super(CatGuardianModule.CAT_FEEDING_STATION_MENU.get(), id);
        this.blockEntity = be;

        // Fish slots (3 × 3) — top section
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new SlotItemHandler(be.getInventory(), row * 3 + col,
                        62 + col * 18, 20 + row * 18));
            }
        }

        // Loot slots (5 × 3) — middle section
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 5; col++) {
                addSlot(new SlotItemHandler(be.getLootInventory(), row * 5 + col,
                        8 + col * 18, 88 + row * 18));
            }
        }

        // Player inventory (3 × 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 154 + row * 18));
            }
        }

        // Hotbar (1 × 9)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 212));
        }
    }

    // Client-side constructor (called by MenuType factory via network)
    public CatFeedingStationMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(id, playerInventory, getBlockEntity(playerInventory.player.level(), buf.readBlockPos()));
    }

    private static CatFeedingStationBlockEntity getBlockEntity(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof CatFeedingStationBlockEntity station) {
            return station;
        }
        throw new IllegalStateException("No CatFeedingStationBlockEntity at " + pos);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, CatGuardianModule.CAT_FEEDING_STATION.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return result;
        }

        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index < FISH_END) {
            if (!moveItemStackTo(stack, PLAYER_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < LOOT_END) {
            if (!moveItemStackTo(stack, PLAYER_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (stack.is(ItemTags.FISHES)) {
                if (!moveItemStackTo(stack, 0, FISH_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!moveItemStackTo(stack, FISH_END, LOOT_END, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (stack.getCount() == result.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return result;
    }
}
