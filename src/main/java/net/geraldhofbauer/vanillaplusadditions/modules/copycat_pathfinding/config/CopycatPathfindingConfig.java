package net.geraldhofbauer.vanillaplusadditions.modules.copycat_pathfinding.config;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModuleConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.copycat_pathfinding.CopycatPathfindingModule;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Configuration for the Copycat Pathfinding module. Every switch can be turned off individually so
 * a single hook can be ruled out while testing in-game.
 */
public class CopycatPathfindingConfig
        extends AbstractModuleConfig<CopycatPathfindingModule, CopycatPathfindingConfig> {

    private ModConfigSpec.BooleanValue directionAware;
    private ModConfigSpec.BooleanValue floorPanelsWalkable;
    private ModConfigSpec.BooleanValue waterPathfinding;

    public CopycatPathfindingConfig(CopycatPathfindingModule module) {
        super(module);
    }

    @Override
    protected void buildModuleSpecificConfig(ModConfigSpec.Builder builder) {
        directionAware = builder
                .comment("Block only those movements that actually cross a panel's plate. "
                        + "false = wall and ceiling panels become passable from every side, which "
                        + "lets mobs walk into a panel wall and get stuck against it.")
                .define("direction_aware", true);

        floorPanelsWalkable = builder
                .comment("Treat a floor panel (FACING=UP) that rests on something solid as walkable "
                        + "ground instead of a wall. Fixes passages only one block high. A floor "
                        + "panel with air below stays as it is, so mobs keep walking across "
                        + "free-standing panel bridges.")
                .define("floor_panels_walkable", true);

        waterPathfinding = builder
                .comment("Let waterlogged panels and steps count as water for swimming mobs, like "
                        + "vanilla slabs do. Create blocks them outright today.")
                .define("water_pathfinding", true);
    }

    /**
     * Whether the plate-crossing check is applied.
     *
     * @return true if movements should be judged per direction (default true)
     */
    public boolean isDirectionAwareValue() {
        return directionAware == null || directionAware.get();
    }

    /**
     * Whether a supported floor panel is upgraded to walkable ground.
     *
     * @return true if floor panels should be walkable (default true)
     */
    public boolean isFloorPanelsWalkableValue() {
        return floorPanelsWalkable == null || floorPanelsWalkable.get();
    }

    /**
     * Whether waterlogged copycat blocks count as water for swimming mobs.
     *
     * @return true if water pathfinding should be fixed (default true)
     */
    public boolean isWaterPathfindingValue() {
        return waterPathfinding == null || waterPathfinding.get();
    }
}
