package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.menu;

import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.AxolotlGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.blockentity.AxolotlInventoryData;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.item.AxolotlArmorItem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class AxolotlInventoryMenu extends AbstractContainerMenu {

    public static final int DATA_FED_TICKS      = 0;
    public static final int DATA_MAX_TICKS      = 1;
    public static final int DATA_AXOLOTL_XP     = 2;
    public static final int DATA_AXOLOTL_XP_CAP = 3;
    public static final int DATA_COUNT          = 4;

    private final Axolotl axolotl;
    private int fedTicks;
    private int maxFedTicks = 6000;
    private int axolotlXp;
    private int axolotlXpCap = 500;

    private final ContainerData syncedData = new ContainerData() {
        @Override
        public int get(int index) {
            if (index == DATA_FED_TICKS) {
                return axolotl != null ? axolotl.getData(AxolotlGuardianModule.AXOLOTL_FED_TICKS.get()) : 0;
            }
            if (index == DATA_MAX_TICKS) {
                return AxolotlGuardianModule.getFedDurationTicks();
            }
            if (index == DATA_AXOLOTL_XP) {
                return axolotl != null ? axolotl.getData(AxolotlGuardianModule.AXOLOTL_XP.get()) : 0;
            }
            if (index == DATA_AXOLOTL_XP_CAP) {
                return AxolotlGuardianModule.getAxolotlXpCapacity();
            }
            return 0;
        }

        @Override
        public void set(int index, int value) {
            if (index == DATA_FED_TICKS) {
                fedTicks = value;
            } else if (index == DATA_MAX_TICKS) {
                maxFedTicks = value;
            } else if (index == DATA_AXOLOTL_XP) {
                axolotlXp = value;
            } else if (index == DATA_AXOLOTL_XP_CAP) {
                axolotlXpCap = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public AxolotlInventoryMenu(int id, Inventory playerInventory, Axolotl axolotl) {
        super(AxolotlGuardianModule.AXOLOTL_INVENTORY_MENU.get(), id);
        this.axolotl = axolotl;

        ItemStackHandler handler = axolotl != null
                ? axolotl.getData(AxolotlGuardianModule.AXOLOTL_INVENTORY.get()).getInventory()
                : new ItemStackHandler(AxolotlInventoryData.TOTAL_SLOTS);

        // Armor slot — only accepts AxolotlArmorItem
        addSlot(new SlotItemHandler(handler, AxolotlInventoryData.ARMOR_SLOT, 8, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof AxolotlArmorItem;
            }
        });

        // Loot slots — output-only (the axolotl fills them). Right-aligned so the 5th loot slot
        // lines up with the 9th player-inventory column (x=152), mirroring the station layout.
        for (int i = 0; i < AxolotlInventoryData.LOOT_SLOTS; i++) {
            addSlot(new SlotItemHandler(handler, AxolotlInventoryData.LOOT_START + i, 80 + i * 18, 17) {
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

    public AxolotlInventoryMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(id, playerInventory, getAxolotl(playerInventory.player.level(), buf.readInt()));
    }

    private static Axolotl getAxolotl(Level level, int entityId) {
        Entity e = level.getEntity(entityId);
        return e instanceof Axolotl a ? a : null;
    }

    public int getFedTicks() {
        return fedTicks;
    }

    public int getMaxFedTicks() {
        return maxFedTicks;
    }

    public int getAxolotlXp() {
        return axolotlXp;
    }

    public int getAxolotlXpCap() {
        return axolotlXpCap;
    }

    public Axolotl getAxolotl() {
        return axolotl;
    }

    @Override
    public boolean stillValid(Player player) {
        if (axolotl == null || !axolotl.isAlive()) {
            return false;
        }
        // Works client-side too: the owner attachment is mirrored to clients via SyncAxolotlOwnerPacket.
        if (!AxolotlGuardianModule.isOwnedBy(axolotl, player.getUUID())) {
            return false;
        }
        return axolotl.distanceToSqr(player) < 64.0 * 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
