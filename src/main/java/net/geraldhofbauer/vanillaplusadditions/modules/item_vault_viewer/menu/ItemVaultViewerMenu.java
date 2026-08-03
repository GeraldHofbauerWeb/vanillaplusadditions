package net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.menu;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.ItemVaultViewerModule;

public class ItemVaultViewerMenu extends AbstractContainerMenu {

    /**
     * Where the viewed vault lives: either a placed block in the world, or a block inside an
     * assembled Create contraption (identified by entity id + contraption-local position).
     * Serialized with a leading tag byte (0 = block, 1 = contraption).
     */
    public sealed interface Anchor permits BlockAnchor, ContraptionAnchor {
        boolean stillValid(Player player);

        void write(RegistryFriendlyByteBuf buf);
    }

    public record BlockAnchor(BlockPos pos) implements Anchor {
        @Override
        public boolean stillValid(Player player) {
            return ItemVaultBlock.isVault(player.level().getBlockState(pos))
                    && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0 * 64.0;
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeByte(0);
            buf.writeBlockPos(pos);
        }
    }

    public record ContraptionAnchor(int entityId, BlockPos localPos) implements Anchor {
        @Override
        public boolean stillValid(Player player) {
            if (!(player.level().getEntity(entityId) instanceof AbstractContraptionEntity contraption)
                    || !contraption.isAlive()) {
                return false;
            }
            StructureTemplate.StructureBlockInfo info = contraption.getContraption().getBlocks().get(localPos);
            if (info == null || !ItemVaultBlock.isVault(info.state())) {
                return false;
            }
            Vec3 global = contraption.toGlobalVector(Vec3.atCenterOf(localPos), 1.0f);
            return player.distanceToSqr(global) <= 64.0 * 64.0;
        }

        @Override
        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeByte(1);
            buf.writeVarInt(entityId);
            buf.writeBlockPos(localPos);
        }
    }

    private final Anchor anchor;
    private final List<ItemStack> stacks;
    private final int totalRows;
    private final int visibleRows;

    public ItemVaultViewerMenu(int id, Inventory playerInventory, Anchor anchor, List<ItemStack> stacks) {
        super(ItemVaultViewerModule.ITEM_VAULT_VIEWER_MENU.get(), id);
        this.anchor = anchor;
        this.stacks = List.copyOf(stacks.stream().map(ItemStack::copy).toList());
        this.totalRows = Math.max(1, (this.stacks.size() + 8) / 9);
        this.visibleRows = Math.min(totalRows, 6);
    }

    public ItemVaultViewerMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(id, playerInventory, readAnchor(buf), readStacks(buf));
    }

    private static Anchor readAnchor(RegistryFriendlyByteBuf buf) {
        if (buf.readByte() == 1) {
            return new ContraptionAnchor(buf.readVarInt(), buf.readBlockPos());
        }
        return new BlockAnchor(buf.readBlockPos());
    }

    private static List<ItemStack> readStacks(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<ItemStack> stacks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            stacks.add(ItemStack.STREAM_CODEC.decode(buf));
        }
        return stacks;
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
        return anchor.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
