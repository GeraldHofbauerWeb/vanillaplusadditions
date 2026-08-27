package net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/**
 * Link-safe Create gate for the Mob Cart Loader, plus the track test — both need only vanilla types,
 * so the block entities can ask "is Create here?" and "is this a track?" without ever touching
 * {@link CreateTrainAccess}.
 *
 * <p>That separation is load-bearing. {@code CreateTrainAccess} has methods whose bodies handle
 * {@code CarriageContraptionEntity} values, and resolving <em>any</em> of its static members links
 * and verifies the whole class — the JVM verifier then eagerly loads those Create types and throws
 * {@code NoClassDefFoundError} on a pack without Create. A gate must never live in the same class as
 * the optional-mod code it guards (see {@code overpacked_extensions}, where exactly that took the
 * whole mod down during construction).
 */
public final class CreateCompat {

    private static final boolean CREATE_LOADED = ModList.get().isLoaded("create");

    /** Create's block tag covering every track material (vanilla lookup — no Create classes). */
    private static final TagKey<Block> TRACKS =
            BlockTags.create(ResourceLocation.fromNamespaceAndPath("create", "tracks"));

    private CreateCompat() {
    }

    /** Whether Create is present at all. */
    public static boolean isLoaded() {
        return CREATE_LOADED;
    }

    /**
     * Whether the given block is one of Create's train tracks. Safe (and simply {@code false})
     * without Create, because the tag then does not exist.
     *
     * @param state the block state to test
     * @return true if the block is tagged {@code create:tracks}
     */
    public static boolean isTrack(BlockState state) {
        return state.is(TRACKS);
    }
}
