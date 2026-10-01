package net.geraldhofbauer.vanillaplusadditions.modules.trial_spawner_glow;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.mixin.trial_spawner_glow.TrialSpawnerCurrentMobsAccessor;
import net.geraldhofbauer.vanillaplusadditions.modules.trial_spawner_glow.config.TrialSpawnerGlowConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Right-click a trial spawner and the mobs it has out right now light up for a while.
 *
 * <p>A trial chamber is a dark, cornered room and the spawner gives no hint whether the wave is
 * finished or whether one straggler is standing behind a wall. The spawner already knows - it keeps
 * the ids of the mobs it is tracking, because that is how it decides when to move on - so this
 * module only makes that knowledge visible.
 *
 * <p>Server side by design. The effect is applied to the mob, so every player sees the outline and a
 * vanilla client needs nothing installed. The alternative - flipping the glow flag on one client -
 * would need a packet carrying the ids, because {@code currentMobs} is not part of the spawner's
 * update tag and never reaches the client.
 *
 * <p>The interaction is never cancelled. A trial spawner has no right-click behaviour of its own in
 * vanilla, but it <i>is</i> a {@code Spawner}, so a spawn egg legitimately changes what it spawns -
 * and cancelling on the server only would desync the client's prediction of whatever the player is
 * holding.
 */
public class TrialSpawnerGlowModule extends AbstractModule<TrialSpawnerGlowModule, TrialSpawnerGlowConfig> {

    private static final String LANG_PREFIX = "message.vanillaplusadditions.trial_spawner_glow.";

    private static TrialSpawnerGlowModule instance;

    public TrialSpawnerGlowModule() {
        super("trial_spawner_glow",
                "Trial Spawner Glow",
                "Clicking a trial spawner outlines the mobs it currently has out",
                TrialSpawnerGlowConfig::new);
        instance = this;
    }

    /**
     * The module instance, or {@code null} before {@code onInitialize} has run. Module-local on
     * purpose: {@code ModuleManager} is only populated by the all-in-one bundle, so a lookup there
     * returns null inside a standalone {@code vpa_trial_spawner_glow} jar.
     *
     * @return the live module instance, or null if it has not been initialized (yet)
     */
    public static TrialSpawnerGlowModule getInstance() {
        return instance;
    }

    @Override
    protected void onInitialize() {
        NeoForge.EVENT_BUS.register(this);
        getLogger().info("Trial Spawner Glow module initialized");
    }

    /**
     * Highlights a trial spawner's current mobs when a player clicks it.
     *
     * <p>Two guards against firing twice: the event is posted on both sides, and it is posted once
     * per hand.</p>
     *
     * @param event the right-click on a block
     */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isModuleEnabled() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (getConfig().requiresEmptyHand() && !event.getItemStack().isEmpty()) {
            return;
        }
        if (!(level.getBlockEntity(event.getPos()) instanceof TrialSpawnerBlockEntity spawner)) {
            return;
        }

        // No check on the spawner's state on purpose: outside ACTIVE the tracked set is empty
        // anyway, so a click during the cooldown does nothing of its own accord.
        int highlighted = applyGlow(level, spawner.getTrialSpawner().getData());
        if (highlighted > 0 && getConfig().showsFeedbackMessage()) {
            Player player = event.getEntity();
            player.displayClientMessage(Component.translatable(LANG_PREFIX + "highlighted", highlighted), true);
        }
    }

    /**
     * Applies the glowing effect to every living mob the spawner is tracking.
     *
     * @param level the level the spawner sits in
     * @param data  the spawner's data
     * @return how many mobs were reached
     */
    private int applyGlow(ServerLevel level, TrialSpawnerData data) {
        // A copy: the accessor hands out the spawner's live set.
        Set<UUID> tracked = new HashSet<>(((TrialSpawnerCurrentMobsAccessor) data).getCurrentMobs());
        int duration = getConfig().getGlowDurationSeconds() * 20;
        boolean particles = getConfig().showsParticles();

        int highlighted = 0;
        for (UUID id : tracked) {
            if (level.getEntity(id) instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0, false, particles, false));
                highlighted++;
            }
        }
        return highlighted;
    }
}
