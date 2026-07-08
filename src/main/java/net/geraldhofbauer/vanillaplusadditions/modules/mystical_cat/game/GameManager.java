package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game;

import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.MysticalCatModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.config.CatWinBehavior;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.config.MysticalCatConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.entity.MysticalCatEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games.CandleKeeperGame;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games.FetchRiddleGame;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games.GhostEscortGame;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games.HotColdGame;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games.ParkourGame;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games.PedestalOfferingGame;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games.ShadowWavesGame;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games.SimonSaysGame;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games.SnowballTargetsGame;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games.TargetRangeGame;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games.WispChaseGame;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games.WoolHuntGame;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side orchestrator for all Mystical Cat mini-games: picks a weighted-random game on
 * interaction, ticks every running {@link GameSession}, routes gameplay events to the active game,
 * enforces aborts (logout, straying too far, timeout, cat gone), pays out wins, and restores the
 * world after clean shutdown or a crash.
 */
public final class GameManager {

    private static final GameManager INSTANCE = new GameManager();

    /** All mini-game singletons. Extended as games are implemented. */
    private final List<MiniGame> games = new ArrayList<>();

    private final Map<UUID, GameSession> byPlayer = new HashMap<>();
    private final Map<UUID, GameSession> byCat = new HashMap<>();

    private long sessionCounter;

    private GameManager() {
        games.add(new FetchRiddleGame());
        games.add(new WispChaseGame());
        games.add(new HotColdGame());
        games.add(new SimonSaysGame());
        games.add(new ParkourGame());
        games.add(new WoolHuntGame());
        games.add(new CandleKeeperGame());
        games.add(new PedestalOfferingGame());
        games.add(new ShadowWavesGame());
        games.add(new TargetRangeGame());
        games.add(new SnowballTargetsGame());
        games.add(new GhostEscortGame());
    }

    public static GameManager get() {
        return INSTANCE;
    }

    /** Registers the manager on the game event bus. Called once from the module. */
    public static void init() {
        NeoForge.EVENT_BUS.register(INSTANCE);
    }

    public List<MiniGame> games() {
        return games;
    }

    private MysticalCatConfig config() {
        return MysticalCatModule.config();
    }

    // ---- interaction dispatch ----

    /**
     * Central right-click handler for a Mystical Cat (server side). Routes to an active game, or a
     * trade (sneak), or starts a new game; returns a consuming result so vanilla never runs.
     */
    public InteractionResult handleInteract(MysticalCatEntity cat, ServerPlayer player, InteractionHand hand) {
        GameSession active = byCat.get(cat.getUUID());
        if (active != null) {
            if (active.player().getUUID().equals(player.getUUID())) {
                active.game().onCatInteract(active, player, hand);
                settleIfDone(active);
            } else {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        GameSession.lang("busy_other")));
            }
            return InteractionResult.sidedSuccess(false);
        }

        if (byPlayer.containsKey(player.getUUID())) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    GameSession.lang("busy_self")));
            return InteractionResult.sidedSuccess(false);
        }

        if (player.isShiftKeyDown()) {
            net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.trade.TradeHandler.trade(cat, player);
            return InteractionResult.sidedSuccess(false);
        }

        long now = cat.level().getGameTime();
        if (now < cat.getCooldownUntil()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(GameSession.lang("dream")));
            return InteractionResult.sidedSuccess(false);
        }

        startGame(cat, player);
        return InteractionResult.sidedSuccess(false);
    }

    private void startGame(MysticalCatEntity cat, ServerPlayer player) {
        if (!(cat.level() instanceof ServerLevel level)) {
            return;
        }
        MysticalCatConfig cfg = config();
        if (cfg == null) {
            return;
        }
        if (byPlayer.size() >= cfg.getMaxConcurrentSessions()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    GameSession.lang("max_sessions")));
            return;
        }

        Map<String, Integer> weights = parseIntMap(cfg.getGameWeights());
        Map<String, Integer> timeLimits = parseIntMap(cfg.getGameTimeLimits());

        List<MiniGame> pool = new ArrayList<>();
        int totalWeight = 0;
        for (MiniGame g : games) {
            int w = weights.getOrDefault(g.id(), 0);
            if (w > 0 && g.canRunAt(level, cat)) {
                pool.add(g);
                totalWeight += w;
            }
        }
        if (pool.isEmpty() || totalWeight <= 0) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    GameSession.lang("max_sessions")));
            return;
        }

        int roll = cat.getRandom().nextInt(totalWeight);
        MiniGame chosen = pool.get(pool.size() - 1);
        for (MiniGame g : pool) {
            roll -= weights.getOrDefault(g.id(), 0);
            if (roll < 0) {
                chosen = g;
                break;
            }
        }

        int seconds = timeLimits.getOrDefault(chosen.id(), 120);
        long deadline = level.getGameTime() + Math.max(1, seconds) * 20L;
        String id = "s" + (sessionCounter++) + "_" + player.getGameProfile().getName();
        GameSession session = new GameSession(cat, player, level, chosen, id, deadline);

        // Cat wakes and sits up; cryptic meow, then the game's instruction line.
        cat.setSittingPose();
        int meow = 1 + cat.getRandom().nextInt(6);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                GameSession.lang("meow." + meow)));
        session.sound(SoundEvents.CAT_AMBIENT, 0.9f, 0.7f);

        byPlayer.put(player.getUUID(), session);
        byCat.put(cat.getUUID(), session);

        try {
            chosen.start(session);
        } catch (RuntimeException e) {
            abort(session, "fail_generic");
            return;
        }
        settleIfDone(session);
    }

    // ---- per-tick driving ----

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (byPlayer.isEmpty()) {
            return;
        }
        MysticalCatConfig cfg = config();
        double abortDistSq = cfg != null ? Math.pow(cfg.getAbortDistance(), 2) : 48 * 48;

        for (GameSession session : new ArrayList<>(byPlayer.values())) {
            if (session.outcome() != GameOutcome.RUNNING) {
                continue;
            }
            ServerPlayer player = session.player();
            MysticalCatEntity cat = session.cat();
            if (player.isRemoved() || !player.isAlive() || player.hasDisconnected()) {
                abort(session, "fail_generic");
                continue;
            }
            if (cat.isRemoved() || !cat.isAlive() || player.level() != session.level()) {
                abort(session, "fail_generic");
                continue;
            }
            if (player.distanceToSqr(cat) > abortDistSq) {
                abort(session, "abort_distance");
                continue;
            }
            if (session.timeLeftTicks() <= 0) {
                finishLose(session, "fail_timeout");
                continue;
            }
            try {
                session.game().tick(session);
            } catch (RuntimeException e) {
                abort(session, "fail_generic");
                continue;
            }
            settleIfDone(session);
        }
    }

    private void settleIfDone(GameSession session) {
        switch (session.outcome()) {
            case WON -> finishWin(session);
            case LOST -> finishLose(session, "fail_generic");
            default -> { }
        }
    }

    // ---- outcomes ----

    private void finishWin(GameSession session) {
        MysticalCatEntity cat = session.cat();
        ServerPlayer player = session.player();
        session.restoreBlocks();
        session.discardTrackedEntities();

        MysticalCatConfig cfg = config();
        int paws = cfg != null ? cfg.getPawsPerWin() : 1;
        ItemStack reward = new ItemStack(MysticalCatModule.MYSTICAL_PAW.get(), paws);
        if (!player.getInventory().add(reward) || !reward.isEmpty()) {
            player.drop(reward, false);
        }
        int completed = player.getData(MysticalCatModule.GAMES_COMPLETED.get()) + 1;
        player.setData(MysticalCatModule.GAMES_COMPLETED.get(), completed);

        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(GameSession.lang("win")));
        session.message("paw_awarded", paws);
        if (completed % 5 == 0) {
            session.message("milestone", completed);
        }
        session.sound(SoundEvents.CAT_PURR, 1.0f, 1.2f);
        session.particles(ParticleTypes.HAPPY_VILLAGER, cat.position().add(0, 0.6, 0), 16, 0.5, 0.02);

        endSession(session);
        applyPostGameCatBehaviour(cat, true);
    }

    private void finishLose(GameSession session, String messageKey) {
        session.restoreBlocks();
        session.discardTrackedEntities();
        session.message(messageKey);
        session.sound(SoundEvents.CAT_HISS, 0.8f, 0.9f);
        MysticalCatEntity cat = session.cat();
        endSession(session);
        // A loss lets the player retry after a short cooldown (never despawns the cat).
        MysticalCatConfig cfg = config();
        int retry = cfg != null ? cfg.getFailRetryCooldownSeconds() : 30;
        cat.setCooldownUntil(cat.level().getGameTime() + retry * 20L);
        cat.setSleepingPose();
    }

    private void abort(GameSession session, String messageKey) {
        session.restoreBlocks();
        session.discardTrackedEntities();
        if (!session.player().hasDisconnected()) {
            session.message(messageKey);
        }
        MysticalCatEntity cat = session.cat();
        endSession(session);
        cat.setSleepingPose();
    }

    private void applyPostGameCatBehaviour(MysticalCatEntity cat, boolean won) {
        MysticalCatConfig cfg = config();
        CatWinBehavior behaviour = cfg != null ? cfg.getCatWinBehavior() : CatWinBehavior.COOLDOWN;
        if (won && behaviour == CatWinBehavior.DESPAWN) {
            if (cat.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.PORTAL, cat.getX(), cat.getY() + 0.5, cat.getZ(),
                        40, 0.3, 0.5, 0.3, 0.1);
                level.sendParticles(ParticleTypes.END_ROD, cat.getX(), cat.getY() + 0.5, cat.getZ(),
                        20, 0.2, 0.4, 0.2, 0.05);
            }
            cat.discard();
            return;
        }
        int minutes = cfg != null ? cfg.getWinCooldownMinutes() : 60;
        cat.setCooldownUntil(cat.level().getGameTime() + minutes * 60L * 20L);
        cat.setSleepingPose();
    }

    private void endSession(GameSession session) {
        byPlayer.remove(session.player().getUUID());
        byCat.remove(session.cat().getUUID());
    }

    // ---- event routing ----

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        GameSession session = byPlayer.get(event.getEntity().getUUID());
        if (session != null) {
            abort(session, "fail_generic");
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        GameSession session = byPlayer.get(player.getUUID());
        if (session == null || !session.ownsBlock(event.getPos())) {
            return;
        }
        // Suppress the drop and hand the break to the game (it decides win + does the restore).
        event.setCanceled(true);
        session.game().onBlockBreak(session, player, event.getPos().immutable());
        settleIfDone(session);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        GameSession session = byPlayer.get(player.getUUID());
        if (session == null) {
            return;
        }
        session.game().onBlockRightClick(session, player, event.getPos().immutable());
        settleIfDone(session);
    }

    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        Entity owner = event.getProjectile().getOwner();
        if (!(owner instanceof ServerPlayer player)) {
            return;
        }
        GameSession session = byPlayer.get(player.getUUID());
        if (session == null) {
            return;
        }
        session.game().onProjectileImpact(session, event.getProjectile(), event.getRayTraceResult().getLocation());
        settleIfDone(session);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        String tag = entity.getPersistentData().getString(GameSession.GAME_TAG);
        if (tag.isEmpty()) {
            return;
        }
        for (GameSession session : byPlayer.values()) {
            if (session.snapshotId().equals(tag)) {
                session.game().onTrackedEntityDeath(session, entity);
                settleIfDone(session);
                return;
            }
        }
    }

    @SubscribeEvent
    public void onGameMobDrops(net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) {
        if (!event.getEntity().getPersistentData().getString(GameSession.GAME_TAG).isEmpty()) {
            event.getDrops().clear();
        }
    }

    @SubscribeEvent
    public void onGameMobXp(net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent event) {
        if (!event.getEntity().getPersistentData().getString(GameSession.GAME_TAG).isEmpty()) {
            event.setDroppedExperience(0);
        }
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        Entity entity = event.getEntity();
        // Orphan cleanup: any game-tagged entity with no matching live session is crash debris —
        // spawned mobs AND ghost-escort cats (which must never survive a reload).
        String tag = entity.getPersistentData().getString(GameSession.GAME_TAG);
        if (!tag.isEmpty()) {
            boolean owned = byPlayer.values().stream().anyMatch(s -> s.snapshotId().equals(tag));
            if (!owned) {
                entity.discard();
                return;
            }
        }
        // A resting cat that loads while not mid-game must show its sleeping pose (synced flags
        // aren't saved to NBT, so they reset to standing on reload).
        if (entity instanceof MysticalCatEntity cat && !cat.isGhost() && !byCat.containsKey(cat.getUUID())) {
            cat.setSleepingPose();
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            GameCrashData data = GameCrashData.get(level);
            if (!data.isEmpty()) {
                data.restoreInto(level);
            }
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        for (GameSession session : new ArrayList<>(byPlayer.values())) {
            abort(session, "fail_generic");
        }
    }

    // ---- helpers ----

    /** Aborts the player's active game (command hook). Returns true if a game was running. */
    public boolean abortPlayer(ServerPlayer player) {
        GameSession session = byPlayer.get(player.getUUID());
        if (session != null) {
            abort(session, "fail_generic");
            return true;
        }
        return false;
    }

    public GameSession sessionForCat(MysticalCatEntity cat) {
        return byCat.get(cat.getUUID());
    }

    public GameSession sessionForPlayer(ServerPlayer player) {
        return byPlayer.get(player.getUUID());
    }

    private static Map<String, Integer> parseIntMap(List<? extends String> entries) {
        Map<String, Integer> map = new HashMap<>();
        for (String entry : entries) {
            int eq = entry.indexOf('=');
            if (eq <= 0 || eq >= entry.length() - 1) {
                continue;
            }
            try {
                map.put(entry.substring(0, eq).trim(), Integer.parseInt(entry.substring(eq + 1).trim()));
            } catch (NumberFormatException ignored) {
                // skip malformed entry
            }
        }
        return map;
    }
}
