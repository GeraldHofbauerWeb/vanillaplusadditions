package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.sable;

import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.block.CatBowlBlock;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.block.CatFeedingStationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Factory for the Sable-aware block variants, keeping every Sable class reference out of
 * {@link net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule}.
 * <p>
 * Without this indirection the module class itself fails to <em>link</em> when Sable is absent:
 * its registration lambdas join {@code SableCatBowlBlock} with {@code CatBowlBlock}, forcing the
 * bytecode verifier to load the Sable subclass (which implements a Sable API interface) at class
 * load — an {@code isLoaded("sable")} runtime check cannot prevent that. Here the signatures only
 * mention module-own types, so callers link cleanly; this class is only classloaded when invoked,
 * which callers must guard with {@code ModList.get().isLoaded("sable")}.
 */
public final class SableCatBlocks {

    private SableCatBlocks() {
    }

    public static CatBowlBlock createBowl(BlockBehaviour.Properties props) {
        return new SableCatBowlBlock(props);
    }

    public static CatFeedingStationBlock createFeedingStation(BlockBehaviour.Properties props) {
        return new SableCatFeedingStationBlock(props);
    }
}
