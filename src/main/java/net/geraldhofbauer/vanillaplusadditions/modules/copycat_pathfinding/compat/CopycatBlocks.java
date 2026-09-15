package net.geraldhofbauer.vanillaplusadditions.modules.copycat_pathfinding.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Link-safe lookup of Create's two copycat blocks. Resolves {@code create:copycat_panel} and
 * {@code create:copycat_step} from the block registry and hands out the plain {@link Block}
 * instances — no Create type is ever named here, so the class links fine on a pack without Create
 * (both getters simply stay {@code null}).
 *
 * <p>That separation is the rule from CLAUDE.md: a gate must never live in the same class as the
 * optional-mod code it guards. Here it is trivially satisfied because there is no optional-mod
 * code at all — the whole module talks to Create through vanilla types only
 * ({@link BlockStateProperties#FACING} is the very same property instance Create's
 * {@code CopycatPanelBlock} registers).
 */
public final class CopycatBlocks {

    private static final Logger LOGGER = LoggerFactory.getLogger(CopycatBlocks.class);
    private static final String CREATE = "create";

    private static volatile boolean resolved;
    private static Block panel;
    private static Block step;

    private CopycatBlocks() {
    }

    /**
     * Create's {@code copycat_panel}, or {@code null} without Create.
     *
     * @return the panel block instance, or null if unavailable
     */
    @Nullable
    public static Block panel() {
        ensureResolved();
        return panel;
    }

    /**
     * Create's {@code copycat_step}, or {@code null} without Create.
     *
     * @return the step block instance, or null if unavailable
     */
    @Nullable
    public static Block step() {
        ensureResolved();
        return step;
    }

    /**
     * Resolves both blocks from the registry once. Idempotent and safe to call from the hot
     * pathfinding hooks — after the first call it is a single volatile read.
     *
     * <p>Deliberately lazy instead of resolve-on-load-complete: {@code ModuleManager.loadComplete()}
     * only calls back into modules that were enabled at startup, so a module switched on at runtime
     * would otherwise never see its blocks.
     */
    public static void ensureResolved() {
        if (resolved) {
            return;
        }
        synchronized (CopycatBlocks.class) {
            if (resolved) {
                return;
            }
            resolved = true;
            if (!ModList.get().isLoaded(CREATE)) {
                return;
            }
            panel = lookup("copycat_panel");
            step = lookup("copycat_step");
            if (panel != null && !panelFacingIsInverted(panel)) {
                LOGGER.warn("create:copycat_panel does not match the expected FACING convention "
                        + "(plate sits at FACING.getOpposite()) - disabling copycat pathfinding to "
                        + "avoid trapping mobs the wrong way round");
                panel = null;
            }
        }
    }

    @Nullable
    private static Block lookup(String path) {
        // getOptional, not get: the block registry is defaulted and would hand back AIR.
        return BuiltInRegistries.BLOCK
                .getOptional(ResourceLocation.fromNamespaceAndPath(CREATE, path))
                .orElse(null);
    }

    /**
     * Sanity-checks the orientation convention this module depends on: Create builds the panel
     * shape from {@code AllShapes.CASING_3PX}, whose base form is a <em>floor</em> plate anchored
     * at {@link Direction#UP}. So {@code FACING = UP} must yield a plate in the lowest 3 pixels,
     * i.e. the plate sticks to {@code FACING.getOpposite()}. Should Create ever flip that, the
     * module disables itself instead of blocking exactly the moves it means to allow.
     *
     * <p>{@code CopycatPanelBlock.getShape} does not touch level or position (verified in the
     * bytecode), so {@link EmptyBlockGetter} is safe here.
     */
    private static boolean panelFacingIsInverted(Block block) {
        BlockState up = block.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
        VoxelShape shape = up.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        if (shape.isEmpty()) {
            return false;
        }
        return shape.min(Direction.Axis.Y) < 1.0E-6 && shape.max(Direction.Axis.Y) < 0.5;
    }
}
