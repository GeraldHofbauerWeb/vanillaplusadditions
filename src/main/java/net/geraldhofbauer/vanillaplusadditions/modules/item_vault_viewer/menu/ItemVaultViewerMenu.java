package net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.menu;

import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.ItemVaultViewerModule;

public class ItemVaultViewerMenu extends AbstractContainerMenu {
    private final BlockPos vaultPos;
    private final List<ItemStack> stacks;
    private final int totalRows;
    private final int visibleRows;

    public ItemVaultViewerMenu(int id, Inventory playerInventory, BlockPos vaultPos, List<ItemStack> stacks) {
        super(ItemVaultViewerModule.ITEM_VAULT_VIEWER_MENU.get(), id);
        this.vaultPos = vaultPos;
        this.stacks = List.copyOf(stacks.stream().map(ItemStack::copy).toList());
        this.totalRows = Math.max(1, (this.stacks.size() + 8) / 9);
        this.visibleRows = Math.min(totalRows, 6);
    }

    public ItemVaultViewerMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(id, playerInventory, buf.readBlockPos(), readStacks(buf));
    }

    private static List<ItemStack> readStacks(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<ItemStack> stacks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            stacks.add(ItemStack.STREAM_CODEC.decode(buf));
        }
        return stacks;
    }

    public BlockPos getVaultPos() {
        return vaultPos;
    }

    public List<ItemStack> getStacks() {
        return stacks;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getVisibleRows() {
        return visibleRows;
    }

    public int getScrollMax() {
        return Math.max(0, totalRows - visibleRows);
    }

    @Override
    public boolean stillValid(Player player) {
        return ItemVaultBlock.isVault(player.level().getBlockState(vaultPos))
                && player.distanceToSqr(vaultPos.getX() + 0.5, vaultPos.getY() + 0.5, vaultPos.getZ() + 0.5) <= 64.0 * 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
