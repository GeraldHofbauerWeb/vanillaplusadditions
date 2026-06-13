package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.menu;

import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.CatInventoryData;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.item.CatArmorItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.Objects;

public class CatInventoryMenu extends AbstractContainerMenu {

    public static final int DATA_FED_TICKS  = 0;
    public static final int DATA_MAX_TICKS  = 1;
    public static final int DATA_CAT_XP     = 2;
    public static final int DATA_CAT_XP_CAP = 3;
    public static final int DATA_COUNT      = 4;

    private static final int ARMOR_END    = 1;  // slot 0
    private static final int LOOT_END     = 6;  // slots 1–5
    private static final int PLAYER_START = 6;
    private static final int HOTBAR_END   = 42;

    private final Cat cat;
    private int fedTicks;
    private int maxFedTicks = 6000;
    private int catXp;
    private int catXpCap = 500;

    private final ContainerData syncedData = new ContainerData() {
        @Override
        public int get(int index) {
            if (index == DATA_FED_TICKS) {
                return cat != null ? cat.getData(CatGuardianModule.CAT_FED_TICKS.get()) : 0;
            }
            if (index == DATA_MAX_TICKS) {
                return CatGuardianModule.getFedDurationTicks();
            }
            if (index == DATA_CAT_XP) {
                return cat != null ? cat.getData(CatGuardianModule.CAT_XP.get()) : 0;
            }
            if (index == DATA_CAT_XP_CAP) {
                return CatGuardianModule.getCatXpCapacity();
            }
            return 0;
        }

        @Override
        public void set(int index, int value) {
            if (index == DATA_FED_TICKS) {
                fedTicks = value;
            } else if (index == DATA_MAX_TICKS) {
                maxFedTicks = value;
            } else if (index == DATA_CAT_XP) {
                catXp = value;
            } else if (index == DATA_CAT_XP_CAP) {
                catXpCap = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public CatInventoryMenu(int id, Inventory playerInventory, Cat cat) {
        super(CatGuardianModule.CAT_INVENTORY_MENU.get(), id);
        this.cat = cat;

        ItemStackHandler handler = cat != null
                ? cat.getData(CatGuardianModule.CAT_INVENTORY.get()).getInventory()
                : new ItemStackHandler(CatInventoryData.TOTAL_SLOTS);

        // Armor slot — only accepts CatArmorItem
        addSlot(new SlotItemHandler(handler, CatInventoryData.ARMOR_SLOT, 8, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof CatArmorItem;
            }
        });

        // Loot slots — output-only (cat fills them)
        for (int i = 0; i < CatInventoryData.LOOT_SLOTS; i++) {
            addSlot(new SlotItemHandler(handler, CatInventoryData.LOOT_START + i, 35 + i * 18, 17) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        // Player inventory (3 × 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 53 + row * 18));
            }
        }

        // Hotbar (1 × 9)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 111));
        }

        addDataSlots(syncedData);
    }

    public CatInventoryMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(id, playerInventory, getCat(playerInventory.player.level(), buf.readInt()));
    }

    private static Cat getCat(Level level, int entityId) {
        Entity e = level.getEntity(entityId);
        return e instanceof Cat c ? c : null;
    }

    public int getFedTicks() {
        return fedTicks;
    }

    public int getMaxFedTicks() {
        return maxFedTicks;
    }

    public int getCatXp() {
        return catXp;
    }

    public int getCatXpCap() {
        return catXpCap;
    }

    @Override
    public boolean stillValid(Player player) {
        if (cat == null || !cat.isAlive()) {
            return false;
        }
        if (!Objects.equals(cat.getOwnerUUID(), player.getUUID())) {
            return false;
        }
        return cat.distanceToSqr(player) < 64.0 * 64.0;
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

        if (index < LOOT_END) {
            // Armor or loot → move to player inventory
            if (!moveItemStackTo(stack, PLAYER_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player/hotbar → try armor slot if CatArmorItem, otherwise no valid target
            if (stack.getItem() instanceof CatArmorItem) {
                if (!moveItemStackTo(stack, 0, ARMOR_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
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
