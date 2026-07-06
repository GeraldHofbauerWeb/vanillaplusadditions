package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.sable;

import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.block.AxolotlBowlBlock;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.block.AxolotlFeedingStationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Factory for the Sable-aware block variants, keeping every Sable class reference out of
 * {@link net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.AxolotlGuardianModule}.
 * <p>
 * Without this indirection the module class itself fails to <em>link</em> when Sable is absent:
 * its registration lambdas join {@code SableAxolotlBowlBlock} with {@code AxolotlBowlBlock},
 * forcing the bytecode verifier to load the Sable subclass (which implements a Sable API
 * interface) at class load — an {@code isLoaded("sable")} runtime check cannot prevent that.
 * Here the signatures only mention module-own types, so callers link cleanly; this class is only
 * classloaded when invoked, which callers must guard with {@code ModList.get().isLoaded("sable")}.
 */
public final class SableAxolotlBlocks {

    private SableAxolotlBlocks() {
    }

    public static AxolotlBowlBlock createBowl(BlockBehaviour.Properties props) {
        return new SableAxolotlBowlBlock(props);
    }

    public static AxolotlFeedingStationBlock createFeedingStation(BlockBehaviour.Properties props) {
        return new SableAxolotlFeedingStationBlock(props);
    }
}
