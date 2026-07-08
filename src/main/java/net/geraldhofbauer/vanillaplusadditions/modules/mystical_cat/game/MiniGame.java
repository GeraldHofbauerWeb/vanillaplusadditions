package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game;

import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.entity.MysticalCatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * A single Mystical Cat mini-game. Implementations are stateless singletons; all per-play state
 * lives in the {@link GameSession} passed to every hook (via {@link GameSession#state()}).
 *
 * <p>A game signals completion by calling {@link GameSession#win()} or {@link GameSession#lose()}
 * from any hook. Timeouts, logout and straying too far are handled centrally by the
 * {@link GameManager}; games never need to check them.
 */
public interface MiniGame {

    /** Stable id; matches the config keys and the {@code game.…} lang keys. */
    String id();

    /**
     * Whether this game can run for the given cat at its current location. Called before a game is
     * picked; a game that returns false is removed from the random pool for that cat (e.g. games
     * that need a prepared cat spot, or open sky for a parkour tower).
     */
    default boolean canRunAt(ServerLevel level, MysticalCatEntity cat) {
        return true;
    }

    /** Sets up blocks/entities and sends the instruction line. */
    void start(GameSession ctx);

    /** Per-tick logic. Default: nothing (purely event-driven games). */
    default void tick(GameSession ctx) {
    }

    /** The player right-clicked the cat again while the game is running (e.g. Fetch Riddle). */
    default void onCatInteract(GameSession ctx, ServerPlayer player, InteractionHand hand) {
    }

    /** The player right-clicked a block (Simon Says taps, Candle Keeper lighting). */
    default void onBlockRightClick(GameSession ctx, ServerPlayer player, BlockPos pos) {
    }

    /** The player broke a block that belongs to this game (Wool Hunt). */
    default void onBlockBreak(GameSession ctx, ServerPlayer player, BlockPos pos) {
    }

    /** A game-tracked entity died (Shadow Waves, Target Range). */
    default void onTrackedEntityDeath(GameSession ctx, Entity entity) {
    }

    /** A projectile hit something while the game is running (Target Range, Snowball Targets). */
    default void onProjectileImpact(GameSession ctx, Projectile projectile, Vec3 hitPos) {
    }
}
