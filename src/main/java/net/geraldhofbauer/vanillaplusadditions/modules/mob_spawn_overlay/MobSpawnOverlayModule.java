package net.geraldhofbauer.vanillaplusadditions.modules.mob_spawn_overlay;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_spawn_overlay.config.MobSpawnOverlayConfig;

/**
 * Mob Spawn Overlay Module
 *
 * <p>Adds a debug overlay on {@code F3 + M} (rebindable via config) that marks every block
 * position where hostile mobs can spawn — the feature OptiFine's {@code F7} used to provide and
 * that Sodium/Iris do not bring along. Two colors: one for "spawns right now", one for
 * "spawns once it gets dark"; positions roomy enough for a spider get an extra outline.</p>
 *
 * <p>The check mirrors vanilla's own spawn code
 * ({@code SpawnPlacementTypes.ON_GROUND} + {@code Monster.isDarkEnoughToSpawn}) and runs purely
 * client-side. That is possible because block states, fluids and both light layers are available
 * on the client — but note that {@code Biome.NETWORK_CODEC} strips {@code MobSpawnSettings}, so
 * the client cannot know which mobs a biome actually lists. The overlay therefore answers
 * "is this a valid ground-spawn position for monsters", not "what spawns here".</p>
 *
 * <p>All logic lives in {@code client/} classes loaded only on {@code Dist.CLIENT}; the module
 * itself has no server-side behaviour.</p>
 */
public class MobSpawnOverlayModule
        extends AbstractModule<MobSpawnOverlayModule, MobSpawnOverlayConfig> {

    private static MobSpawnOverlayModule instance;

    public MobSpawnOverlayModule() {
        super("mob_spawn_overlay",
                "Mob Spawn Overlay",
                "F3+M overlay marking every position where hostile mobs can spawn",
                MobSpawnOverlayConfig::new
        );
        instance = this;
    }

    @Override
    protected void onInitialize() {
        // Client keybind/render handlers register themselves via @EventBusSubscriber(Dist.CLIENT).
        getLogger().info("Mob Spawn Overlay module initialized");
    }

    /** The module instance, or {@code null} before construction. */
    public static MobSpawnOverlayModule getInstance() {
        return instance;
    }

    /**
     * Mixin hook for {@code KeyboardHandlerDebugKeyMixin}: whether the F3 combo should be
     * consumed at all. False while the module is disabled, so vanilla keeps the key free.
     *
     * @return true if the overlay may react to its debug key
     */
    public static boolean isActiveClientSide() {
        MobSpawnOverlayModule module = instance;
        return module != null && module.isModuleEnabled();
    }

    /**
     * The GLFW key code that toggles the overlay while F3 is held (default {@code M}).
     *
     * @return the configured key code
     */
    public static int getToggleKey() {
        MobSpawnOverlayModule module = instance;
        return module == null ? MobSpawnOverlayConfig.DEFAULT_TOGGLE_KEY : module.getConfig().getToggleKey();
    }
}
