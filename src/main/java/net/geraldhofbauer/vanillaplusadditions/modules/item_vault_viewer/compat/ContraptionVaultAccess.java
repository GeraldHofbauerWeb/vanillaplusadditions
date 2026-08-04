package net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.compat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Resolves the mounted item storages backing a clicked vault block inside a Create contraption.
 *
 * <p>A multiblock vault has <em>no</em> shared inventory: every {@code ItemVaultBlockEntity} keeps
 * its own 20-slot handler and the controller merely exposes a {@code SameSizeCombinedInvWrapper}
 * over all members at runtime. Create's assembly mounts each block separately
 * ({@code ItemVaultMountedStorageType.mount} copies {@code getInventoryOfBlock()}), so the contents
 * end up spread across one storage per block. Reading a single storage — the clicked one or the
 * controller's — therefore shows at most a twentieth of the vault. We rebuild the controller's
 * combined view instead: collect every member of the same multiblock and read them all, in Create's
 * own member order so slots line up with what the block-side GUI shows.
 */
public final class ContraptionVaultAccess {

    /** Max Chebyshev distance for the nearest-storage fallback (max vault dimension is 3). */
    private static final int FALLBACK_RADIUS = 3;

    private static volatile boolean warnedReflection = false;

    private ContraptionVaultAccess() {
    }

    /**
     * All mounted storages that together form the vault the player clicked.
     *
     * @param entity   the contraption entity that was hit
     * @param localPos contraption-local position of the clicked vault block
     * @return the vault's storages in Create's member order; empty if nothing could be resolved
     */
    public static List<IItemHandler> findVaultStorages(AbstractContraptionEntity entity, BlockPos localPos) {
        Contraption contraption = entity.getContraption();
        ImmutableMap<BlockPos, MountedItemStorage> storages = contraption.getStorage().getAllItemStorages();

        BlockPos controllerPos = readControllerPos(contraption, localPos);
        if (controllerPos == null) {
            controllerPos = findMultiblockControllerKey(contraption, localPos);
        }
        if (controllerPos == null) {
            controllerPos = localPos;
        }

        List<BlockPos> members = collectMembers(contraption, controllerPos);
        members.sort(memberOrder(vaultAxis(contraption, controllerPos)));

        List<IItemHandler> handlers = new ArrayList<>(members.size());
        for (BlockPos member : members) {
            MountedItemStorage storage = storages.get(member);
            if (storage != null) {
                handlers.add(storage);
            }
        }
        if (!handlers.isEmpty()) {
            return handlers;
        }

        // Fallback: nearest storage within the largest possible vault dimension.
        MountedItemStorage nearest = null;
        int nearestDist = Integer.MAX_VALUE;
        for (Map.Entry<BlockPos, MountedItemStorage> entry : storages.entrySet()) {
            BlockPos pos = entry.getKey();
            int dist = Math.max(Math.abs(pos.getX() - localPos.getX()),
                    Math.max(Math.abs(pos.getY() - localPos.getY()), Math.abs(pos.getZ() - localPos.getZ())));
            if (dist <= FALLBACK_RADIUS && dist < nearestDist) {
                nearest = entry.getValue();
                nearestDist = dist;
            }
        }
        return nearest == null ? List.of() : List.of(nearest);
    }

    /**
     * Every vault block of the multiblock owned by {@code controllerPos}, the controller included.
     * Membership is read straight off the captured block entity NBT, so it works for single blocks
     * (which are their own controller) just as well as for a full 3×3×9 vault.
     */
    private static List<BlockPos> collectMembers(Contraption contraption, BlockPos controllerPos) {
        List<BlockPos> members = new ArrayList<>();
        for (Map.Entry<BlockPos, StructureTemplate.StructureBlockInfo> entry : contraption.getBlocks().entrySet()) {
            if (!ItemVaultBlock.isVault(entry.getValue().state())) {
                continue;
            }
            BlockPos owner = readControllerPos(contraption, entry.getKey());
            if (controllerPos.equals(owner == null ? entry.getKey() : owner)) {
                members.add(entry.getKey());
            }
        }
        if (members.isEmpty()) {
            members.add(controllerPos);
        }
        return members;
    }

    /**
     * Create walks a vault's members along its horizontal axis first, then the two remaining axes
     * ({@code ItemVaultBlockEntity.initCapability}). Matching that keeps our slot order identical
     * to the vault's own GUI.
     */
    private static Comparator<BlockPos> memberOrder(Direction.Axis axis) {
        return switch (axis) {
            case Z -> Comparator.<BlockPos>comparingInt(BlockPos::getZ)
                    .thenComparingInt(BlockPos::getX)
                    .thenComparingInt(BlockPos::getY);
            case Y -> Comparator.<BlockPos>comparingInt(BlockPos::getY)
                    .thenComparingInt(BlockPos::getX)
                    .thenComparingInt(BlockPos::getZ);
            default -> Comparator.<BlockPos>comparingInt(BlockPos::getX)
                    .thenComparingInt(BlockPos::getY)
                    .thenComparingInt(BlockPos::getZ);
        };
    }

    private static Direction.Axis vaultAxis(Contraption contraption, BlockPos controllerPos) {
        StructureTemplate.StructureBlockInfo info = contraption.getBlocks().get(controllerPos);
        if (info == null) {
            return Direction.Axis.X;
        }
        Direction.Axis axis = ItemVaultBlock.getVaultBlockAxis(info.state());
        return axis == null ? Direction.Axis.X : axis;
    }

    /**
     * Segment→controller mapping: when Create captures a multiblock it stamps the controller's
     * <em>contraption-local</em> position into every part's block entity NBT
     * ({@code Contraption.captureMultiblock}), so the clicked block itself tells us which vault it
     * belongs to — no reflection needed.
     *
     * @return the controller's local position, or null if this block is not a captured multiblock
     */
    private static BlockPos readControllerPos(Contraption contraption, BlockPos localPos) {
        StructureTemplate.StructureBlockInfo info = contraption.getBlocks().get(localPos);
        if (info == null || info.nbt() == null || !info.nbt().contains("Controller")) {
            return null;
        }
        return NBTHelper.readBlockPos(info.nbt(), "Controller");
    }

    /**
     * Fallback segment→controller mapping via {@code Contraption.capturedMultiblocks}
     * ({@code Multimap<controllerLocalPos, StructureBlockInfo>}).
     */
    private static BlockPos findMultiblockControllerKey(Contraption contraption, BlockPos localPos) {
        try {
            Field field = Contraption.class.getDeclaredField("capturedMultiblocks");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Multimap<BlockPos, StructureTemplate.StructureBlockInfo> captured =
                    (Multimap<BlockPos, StructureTemplate.StructureBlockInfo>) field.get(contraption);
            for (Map.Entry<BlockPos, StructureTemplate.StructureBlockInfo> entry : captured.entries()) {
                if (entry.getValue().pos().equals(localPos)) {
                    return entry.getKey();
                }
            }
        } catch (ReflectiveOperationException | ClassCastException e) {
            if (!warnedReflection) {
                warnedReflection = true;
                org.slf4j.LoggerFactory.getLogger(ContraptionVaultAccess.class)
                        .warn("Could not read Contraption.capturedMultiblocks — falling back to "
                                + "nearest-storage lookup for contraption vaults.", e);
            }
        }
        return null;
    }
}
