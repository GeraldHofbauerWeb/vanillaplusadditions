package net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.compat.ContraptionVaultAccess;
import net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.config.ItemVaultViewerConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.menu.ItemVaultViewerMenu;
import net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.network.OpenContraptionVaultViewerPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.network.OpenItemVaultViewerPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

public class ItemVaultViewerModule extends AbstractModule<ItemVaultViewerModule, ItemVaultViewerConfig> {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, VanillaPlusAdditions.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ItemVaultViewerMenu>> ITEM_VAULT_VIEWER_MENU =
            MENUS.register("item_vault_viewer", () -> IMenuTypeExtension.create(ItemVaultViewerMenu::new));

    public ItemVaultViewerModule() {
        super(
                "item_vault_viewer",
                "Item Vault Viewer",
                "Lets players view Create Item Vault contents with Engineering Goggles.",
                ItemVaultViewerConfig::new
        );
    }

    public static boolean isCreateLoaded() {
        return ModList.get().isLoaded("create");
    }

    @Override
    protected boolean shouldInitialize() {
        return isCreateLoaded();
    }

    @Override
    protected void onInitialize() {
        MENUS.register(getModEventBus());
        getModEventBus().addListener(this::onRegisterPayloadHandlers);
        getLogger().info("Item Vault Viewer module initialized");
    }

    private void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(OpenItemVaultViewerPacket.TYPE, OpenItemVaultViewerPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> {
                    if (!isModuleEnabled() || !isCreateLoaded()) {
                        return;
                    }

                    ServerPlayer player = (ServerPlayer) ctx.player();
                    BlockPos pos = packet.pos();
                    if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0 * 64.0) {
                        return;
                    }

                    BlockEntity blockEntity = player.level().getBlockEntity(pos);
                    if (blockEntity == null) {
                        return;
                    }
                    if (!ItemVaultBlock.isVault(player.level().getBlockState(pos))) {
                        return;
                    }
                    if (!GogglesItem.isWearingGoggles(player)) {
                        return;
                    }
                    if (player.isShiftKeyDown()) {
                        return;
                    }

                    BlockEntity controller = getController(blockEntity);
                    if (controller == null) {
                        return;
                    }
                    IItemHandler inventory = getInventory(controller);
                    if (inventory == null) {
                        return;
                    }

                    BlockPos controllerPos = controller.getBlockPos();
                    List<ItemStack> stacks = aggregateStacks(inventory);
                    openViewer(player, new ItemVaultViewerMenu.BlockAnchor(controllerPos), stacks);
                }));

        event.registrar("1").playToServer(OpenContraptionVaultViewerPacket.TYPE,
                OpenContraptionVaultViewerPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> {
                    if (!isModuleEnabled() || !isCreateLoaded()) {
                        return;
                    }

                    ServerPlayer player = (ServerPlayer) ctx.player();
                    getLogger().debug("[IVV/contraption] packet from {}: entity #{} local {}",
                            player.getGameProfile().getName(), packet.entityId(), packet.localPos());
                    if (!GogglesItem.isWearingGoggles(player) || player.isShiftKeyDown()) {
                        getLogger().debug("[IVV/contraption] rejected: goggles/sneak gate");
                        return;
                    }
                    if (!(player.level().getEntity(packet.entityId())
                            instanceof AbstractContraptionEntity contraption) || !contraption.isAlive()) {
                        getLogger().debug("[IVV/contraption] rejected: entity #{} not a live contraption ({})",
                                packet.entityId(), player.level().getEntity(packet.entityId()));
                        return;
                    }
                    BlockPos localPos = packet.localPos();
                    StructureTemplate.StructureBlockInfo info =
                            contraption.getContraption().getBlocks().get(localPos);
                    if (info == null || !ItemVaultBlock.isVault(info.state())) {
                        getLogger().debug("[IVV/contraption] rejected: block at local {} is {}",
                                localPos, info == null ? "<absent>" : info.state().getBlock());
                        return;
                    }
                    Vec3 global = contraption.toGlobalVector(Vec3.atCenterOf(localPos), 1.0f);
                    if (player.distanceToSqr(global) > 64.0 * 64.0) {
                        getLogger().debug("[IVV/contraption] rejected: too far ({} blocks)",
                                Math.sqrt(player.distanceToSqr(global)));
                        return;
                    }

                    List<IItemHandler> inventories =
                            ContraptionVaultAccess.findVaultStorages(contraption, localPos);
                    if (inventories.isEmpty()) {
                        getLogger().debug("[IVV/contraption] rejected: no mounted storage for local {} "
                                        + "(known storages: {})", localPos,
                                contraption.getContraption().getStorage().getAllItemStorages().keySet());
                        return;
                    }

                    List<ItemStack> stacks = aggregateStacks(inventories);
                    getLogger().debug("[IVV/contraption] opening viewer with {} stacks "
                            + "({} vault blocks)", stacks.size(), inventories.size());
                    openViewer(player,
                            new ItemVaultViewerMenu.ContraptionAnchor(contraption.getId(), localPos), stacks);
                }));
    }

    private void openViewer(ServerPlayer player, ItemVaultViewerMenu.Anchor anchor, List<ItemStack> stacks) {
        player.openMenu(
                new SimpleMenuProvider(
                        (id, inventory, ignored) -> new ItemVaultViewerMenu(id, inventory, anchor, stacks),
                        Component.literal("Item Vault Viewer")
                ),
                buf -> {
                    anchor.write(buf);
                    buf.writeVarInt(stacks.size());
                    for (ItemStack stack : stacks) {
                        ItemStack.STREAM_CODEC.encode(buf, stack);
                    }
                }
        );
    }

    private static List<ItemStack> aggregateStacks(IItemHandler handler) {
        return aggregateStacks(List.of(handler));
    }

    /**
     * Merges equal stacks across one or more handlers. A multiblock vault on a contraption is
     * mounted as one storage per block, so its contents only add up when all of them are read.
     */
    private static List<ItemStack> aggregateStacks(List<IItemHandler> handlers) {
        List<ItemStack> stacks = new ArrayList<>();
        for (IItemHandler handler : handlers) {
            collectStacks(handler, stacks);
        }
        return stacks;
    }

    private static void collectStacks(IItemHandler handler, List<ItemStack> stacks) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            boolean merged = false;
            for (ItemStack existing : stacks) {
                if (ItemStack.isSameItemSameComponents(existing, stack)) {
                    existing.grow(stack.getCount());
                    merged = true;
                    break;
                }
            }

            if (!merged) {
                stacks.add(stack.copy());
            }
        }
    }

    private static BlockEntity getController(BlockEntity blockEntity) {
        try {
            Object controller = blockEntity.getClass().getMethod("getControllerBE").invoke(blockEntity);
            return controller instanceof BlockEntity controllerBe ? controllerBe : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static IItemHandler getInventory(BlockEntity blockEntity) {
        try {
            var field = blockEntity.getClass().getDeclaredField("itemCapability");
            field.setAccessible(true);
            Object capabilityProvider = field.get(blockEntity);
            if (capabilityProvider != null) {
                Object capability = capabilityProvider.getClass().getMethod("getCapability").invoke(capabilityProvider);
                if (capability instanceof IItemHandler itemHandler) {
                    return itemHandler;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // Fallback below
        }
        try {
            Object inventory = blockEntity.getClass().getMethod("getInventoryOfBlock").invoke(blockEntity);
            return inventory instanceof IItemHandler handler ? handler : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
