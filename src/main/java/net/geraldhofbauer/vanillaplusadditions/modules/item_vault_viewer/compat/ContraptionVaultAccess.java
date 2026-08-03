package net.geraldhofbauer.vanillaplusadditions.modules.item_vault_viewer.compat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Resolves the mounted item storage backing a clicked vault block inside a Create contraption.
 * Create merges a whole multiblock vault into ONE {@code ItemVaultMountedStorage}, keyed by the
 * vault controller's contraption-local position — so a click on a non-controller segment needs a
 * segment→controller mapping, which lives in the protected {@code Contraption.capturedMultiblocks}
 * multimap (reflection; with a nearest-storage fallback if that ever breaks).
 */
public final class ContraptionVaultAccess {

    /** Max Chebyshev distance for the nearest-storage fallback (max vault dimension is 3). */
    private static final int FALLBACK_RADIUS = 3;

    private static volatile boolean warnedReflection = false;

    private ContraptionVaultAccess() {
    }

    public static IItemHandler findVaultStorage(AbstractContraptionEntity entity, BlockPos localPos) {
        Contraption contraption = entity.getContraption();
        ImmutableMap<BlockPos, MountedItemStorage> storages = contraption.getStorage().getAllItemStorages();

        MountedItemStorage direct = storages.get(localPos);
        if (direct != null) {
            return direct;
        }

        BlockPos controllerKey = findMultiblockControllerKey(contraption, localPos);
        if (controllerKey != null) {
            MountedItemStorage viaController = storages.get(controllerKey);
            if (viaController != null) {
                return viaController;
            }
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
        return nearest;
    }

    /**
     * Exact segment→controller mapping via {@code Contraption.capturedMultiblocks}
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
