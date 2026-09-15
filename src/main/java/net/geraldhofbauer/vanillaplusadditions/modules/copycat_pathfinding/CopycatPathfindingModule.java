package net.geraldhofbauer.vanillaplusadditions.modules.copycat_pathfinding;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.copycat_pathfinding.compat.CopycatBlocks;
import net.geraldhofbauer.vanillaplusadditions.modules.copycat_pathfinding.config.CopycatPathfindingConfig;
import net.neoforged.fml.ModList;

/**
 * Copycat Pathfinding Module
 *
 * <p>Create's {@code CopycatPanelBlock} and {@code CopycatStepBlock} return a hardcoded
 * {@code false} from {@code isPathfindable}, so vanilla's node evaluators mark the whole block
 * {@code PathType.BLOCKED} — even though a panel is a 3-pixel plate stuck to a single face. Mobs
 * therefore refuse to use a passage whose wall or ceiling merely happens to be lined with a panel.
 *
 * <p>For a floor panel and for a step, Create's {@code false} is in fact right: it matches
 * {@code SlabBlock}, and it is what makes mobs walk <em>on top of</em> them. The module keeps that
 * and repairs the three cases that are genuinely wrong:
 *
 * <ul>
 *   <li>wall and ceiling panels become passable, but only for movements that do not cross the
 *       plate's plane ({@link PanelGeometry});</li>
 *   <li>a floor panel resting on solid ground becomes walkable ground itself, which matters in a
 *       passage only one block high;</li>
 *   <li>waterlogged panels and steps count as water again, like vanilla slabs, so swimming mobs
 *       are no longer locked out.</li>
 * </ul>
 *
 * <p>All behavior lives in the mixins under {@code mixin/copycat_pathfinding}, which call back into
 * the static gates here. Nothing in this module references a Create class — the blocks are looked
 * up from the registry by name (see {@link CopycatBlocks}), so the code links fine without Create.
 */
public class CopycatPathfindingModule
        extends AbstractModule<CopycatPathfindingModule, CopycatPathfindingConfig> {

    private static CopycatPathfindingModule instance;

    public CopycatPathfindingModule() {
        super("copycat_pathfinding",
                "Copycat Pathfinding",
                "Mobs treat Create copycat panels as the thin plate they are instead of a full wall",
                CopycatPathfindingConfig::new
        );
        instance = this;
    }

    @Override
    protected boolean shouldInitialize() {
        return ModList.get().isLoaded("create");
    }

    @Override
    protected void onInitialize() {
        getLogger().info("Copycat Pathfinding module initialized - copycat panels no longer block mob paths");
    }

    @Override
    protected void onLoadComplete() {
        CopycatBlocks.ensureResolved();
    }

    /**
     * Mixin gate: whether copycat pathfinding should be altered at all.
     *
     * @return true if the module is registered and enabled
     */
    public static boolean isActive() {
        CopycatPathfindingModule module = instance;
        return module != null && module.isModuleEnabled();
    }

    /**
     * Whether a movement is judged against the plate's orientation instead of being allowed
     * outright.
     *
     * @return true if the plate-crossing check should run (default true)
     */
    public static boolean isDirectionAware() {
        CopycatPathfindingModule module = instance;
        return module == null || module.getConfig().isDirectionAwareValue();
    }

    /**
     * Whether a floor panel resting on something solid counts as walkable ground.
     *
     * @return true if supported floor panels should be walkable (default true)
     */
    public static boolean floorPanelsWalkable() {
        CopycatPathfindingModule module = instance;
        return module == null || module.getConfig().isFloorPanelsWalkableValue();
    }

    /**
     * Whether waterlogged copycat blocks count as water for swimming mobs.
     *
     * @return true if water pathfinding should be fixed (default true)
     */
    public static boolean waterPathfinding() {
        CopycatPathfindingModule module = instance;
        return module == null || module.getConfig().isWaterPathfindingValue();
    }
}
