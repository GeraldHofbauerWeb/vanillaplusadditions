package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.blockentity;

import com.mojang.serialization.Codec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

public class AxolotlInventoryData {

    public static final int ARMOR_SLOT  = 0;
    public static final int LOOT_START  = 1;
    public static final int LOOT_SLOTS  = 5;
    public static final int TOTAL_SLOTS = 6; // 1 armor + 5 loot

    private final ItemStackHandler inventory = new ItemStackHandler(TOTAL_SLOTS);

    public AxolotlInventoryData() {
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public ItemStack getArmor() {
        return inventory.getStackInSlot(ARMOR_SLOT);
    }

    public void setArmor(ItemStack stack) {
        inventory.setStackInSlot(ARMOR_SLOT, stack);
    }

    // ---- Codec for AttachmentType serialization ----

    public static final Codec<AxolotlInventoryData> CODEC =
            Codec.list(ItemStack.OPTIONAL_CODEC)
                 .xmap(AxolotlInventoryData::fromList, AxolotlInventoryData::toList);

    private static AxolotlInventoryData fromList(List<ItemStack> stacks) {
        AxolotlInventoryData data = new AxolotlInventoryData();
        for (int i = 0; i < Math.min(stacks.size(), TOTAL_SLOTS); i++) {
            data.inventory.setStackInSlot(i, stacks.get(i));
        }
        return data;
    }

    private List<ItemStack> toList() {
        List<ItemStack> stacks = new ArrayList<>(TOTAL_SLOTS);
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            stacks.add(inventory.getStackInSlot(i));
        }
        return stacks;
    }
}
