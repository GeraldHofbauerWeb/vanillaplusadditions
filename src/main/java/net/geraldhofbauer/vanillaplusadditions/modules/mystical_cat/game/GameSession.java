package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.entity.MysticalCatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Live context for one running mini-game: the cat, the player, timing, and every helper a game
 * needs — temporary block placement with guaranteed restore, entity tracking, particles/sound/text
 * FX, and win/lose signalling.
 *
 * <p>Per-play game state is stored via {@link #setState}/{@link #state()} so {@link MiniGame}
 * implementations can stay stateless singletons.
 */
public class GameSession {

    /** Persistent-data key marking an entity as owned by a mystical mini-game (for orphan cleanup). */
    public static final String GAME_TAG = "vpa_mystical_game";

    private final MysticalCatEntity cat;
    private final ServerPlayer player;
    private final ServerLevel level;
    private final MiniGame game;
    private final String snapshotId;
    private final long startTick;
    private final long deadlineTick;

    private final List<BlockSnapshot> snapshots = new ArrayList<>();
    private final List<java.util.UUID> trackedEntities = new ArrayList<>();

    private GameOutcome outcome = GameOutcome.RUNNING;
    private Object state;

    private record BlockSnapshot(BlockPos pos, BlockState original) {
    }

    public GameSession(MysticalCatEntity cat, ServerPlayer player, ServerLevel level, MiniGame game,
                       String snapshotId, long deadlineTick) {
        this.cat = cat;
        this.player = player;
        this.level = level;
        this.game = game;
        this.snapshotId = snapshotId;
        this.startTick = level.getGameTime();
        this.deadlineTick = deadlineTick;
    }

    // ---- accessors ----

    public MysticalCatEntity cat() {
        return cat;
    }

    public ServerPlayer player() {
        return player;
    }

    public ServerLevel level() {
        return level;
    }

    public MiniGame game() {
        return game;
    }

    public String snapshotId() {
        return snapshotId;
    }

    public long startTick() {
        return startTick;
    }

    public long deadlineTick() {
        return deadlineTick;
    }

    public long timeLeftTicks() {
        return deadlineTick - level.getGameTime();
    }

    public long elapsedTicks() {
        return level.getGameTime() - startTick;
    }

    public RandomSource random() {
        return level.getRandom();
    }

    /** The anchor for game geometry: the cat's spot center if it has one, else its position. */
    public BlockPos center() {
        return cat.getSpotCenter() != null ? cat.getSpotCenter() : cat.blockPosition();
    }

    @SuppressWarnings("unchecked")
    public <T> T state() {
        return (T) state;
    }

    public void setState(Object state) {
        this.state = state;
    }

    // ---- outcome signalling ----

    public GameOutcome outcome() {
        return outcome;
    }

    public void win() {
        if (outcome == GameOutcome.RUNNING) {
            outcome = GameOutcome.WON;
        }
    }

    public void lose() {
        if (outcome == GameOutcome.RUNNING) {
            outcome = GameOutcome.LOST;
        }
    }

    // ---- temporary blocks (recorded for guaranteed restore + crash safety) ----

    /**
     * Places {@code newState} at {@code pos}, remembering the original state so it is restored when
     * the game ends (or after a crash). Returns false if the position could not be recorded.
     */
    public boolean placeTempBlock(BlockPos pos, BlockState newState) {
        BlockPos immutable = pos.immutable();
        BlockState original = level.getBlockState(immutable);
        snapshots.add(new BlockSnapshot(immutable, original));
        GameCrashData.get(level).recordBlock(snapshotId, immutable, original);
        level.setBlock(immutable, newState, 3);
        return true;
    }

    /** Whether {@code pos} is one of the blocks this game placed. */
    public boolean ownsBlock(BlockPos pos) {
        BlockPos immutable = pos.immutable();
        for (BlockSnapshot s : snapshots) {
            if (s.pos().equals(immutable)) {
                return true;
            }
        }
        return false;
    }

    /** Restores every placed block in reverse order and drops the crash mirror. */
    public void restoreBlocks() {
        for (int i = snapshots.size() - 1; i >= 0; i--) {
            BlockSnapshot s = snapshots.get(i);
            level.setBlock(s.pos(), s.original(), 3);
        }
        snapshots.clear();
        GameCrashData.get(level).clearSession(snapshotId);
    }

    // ---- tracked entities ----

    /** Spawns/registers an entity as game-owned so it is cleaned up (and orphan-collected on crash). */
    public void trackEntity(Entity entity) {
        entity.getPersistentData().putString(GAME_TAG, snapshotId);
        trackedEntities.add(entity.getUUID());
    }

    public List<java.util.UUID> trackedEntities() {
        return trackedEntities;
    }

    /** Removes all tracked entities from the world. */
    public void discardTrackedEntities() {
        for (java.util.UUID id : trackedEntities) {
            Entity e = level.getEntity(id);
            if (e != null && !e.isRemoved()) {
                e.discard();
            }
        }
        trackedEntities.clear();
    }

    // ---- FX / messaging helpers ----

    public void message(String key, Object... args) {
        player.sendSystemMessage(Component.translatable(key, args));
    }

    public void actionBar(String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    public void actionBar(Component component) {
        player.displayClientMessage(component, true);
    }

    public void sound(SoundEvent sound, float volume, float pitch) {
        level.playSound(null, cat.getX(), cat.getY(), cat.getZ(), sound, SoundSource.NEUTRAL, volume, pitch);
    }

    public void soundAt(Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.NEUTRAL, volume, pitch);
    }

    public void particles(ParticleOptions particle, Vec3 pos, int count, double spread, double speed) {
        level.sendParticles(particle, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
    }

    public static String lang(String suffix) {
        return "message." + VanillaPlusAdditions.MODID + ".mystical_cat." + suffix;
    }

    public static String gameLang(String id, String suffix) {
        return "game." + VanillaPlusAdditions.MODID + ".mystical_cat." + id + "." + suffix;
    }

    /** Sends this game's start/instruction line (translatable {@code game.….<id>.<suffix>}). */
    public void gameMessage(String suffix, Object... args) {
        player.sendSystemMessage(Component.translatable(gameLang(game.id(), suffix), args));
    }

    /** Action-bar variant of {@link #gameMessage}. */
    public void gameActionBar(String suffix, Object... args) {
        player.displayClientMessage(Component.translatable(gameLang(game.id(), suffix), args), true);
    }
}
