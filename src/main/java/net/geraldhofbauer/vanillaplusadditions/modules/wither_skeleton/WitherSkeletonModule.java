package net.geraldhofbauer.vanillaplusadditions.modules.wither_skeleton;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.wither_skeleton.config.WitherSkeletonConfig;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressStructure;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

import java.util.Map;

/**
 * Wither Skeleton Module
 * <p>
 * Keeps plain skeletons out of <strong>Nether fortresses</strong> and puts a Wither Skeleton in
 * their place, so a fortress spawner yields what a fortress is supposed to yield.
 * <p>
 * This is deliberately <em>not</em> a Nether-wide ban: the handler returns early for any skeleton
 * whose position carries no structure reference, so plain skeletons keep spawning in the wastes,
 * the soul sand valleys and the basalt deltas exactly as vanilla intends.
 * <p>
 * Features:
 * - Blocks plain skeleton spawns inside a Nether fortress (vanilla's, and Better Fortresses')
 * - Replaces every blocked spawn with a Wither Skeleton - always, there is no switch for it
 * - Announces blocked spawns in chat, but only while debug logging is on; the module has no
 *   config keys of its own
 */
public class WitherSkeletonModule
        extends AbstractModule<WitherSkeletonModule, WitherSkeletonConfig> {

    /** Permission level {@code /tp} requires, and therefore the one the click-to-teleport needs. */
    private static final int TELEPORT_PERMISSION_LEVEL = 2;


    public WitherSkeletonModule() {
        super("wither_skeleton",
                "Wither Skeleton Enforcer",
                "Keeps plain skeletons out of Nether fortresses and replaces them with Wither "
                        + "Skeletons (blocked spawns are announced only with debug logging on)",
                WitherSkeletonConfig::new
        );
    }

    @Override
    protected void onInitialize() {
        // Register event listeners for this module
        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Wither Skeleton module initialized - plain skeletons are now banned from Nether fortresses");
    }

    @Override
    protected void onCommonSetup() {
        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Wither Skeleton module common setup complete");
        }
    }


    /**
     * Event handler that prevents normal skeleton spawns in the Nether and broadcasts messages
     * Uses HIGH priority to ensure we can cancel the spawn before other mods process it
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onEntitySpawn(FinalizeSpawnEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        // Only process on server side
        if (event.getLevel().isClientSide()) {
            return;
        }

        // Check if the entity is a normal skeleton
        if (!(event.getEntity() instanceof Skeleton skeleton)) {
            return;
        }

        // Check if we're in the Nether (cast to ServerLevel to access dimension())
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        ResourceKey<Level> dimension = serverLevel.dimension();
        if (dimension != Level.NETHER) {
            return;
        }

        // A fortress is what this module is about: outside one, a plain skeleton is left alone.
        Map<Structure, LongSet> allStructures = serverLevel.structureManager().getAllStructuresAt(
                event.getEntity().blockPosition());
        if (allStructures.isEmpty()) {
            return;
        }
        boolean insideFortress = false;
        Registry<Structure> structureRegistry = serverLevel.registryAccess()
                .registryOrThrow(Registries.STRUCTURE);
        for (Structure structure : allStructures.keySet()) {
            if (structure instanceof NetherFortressStructure
                    || ResourceLocation.fromNamespaceAndPath("betterfortresses", "fortress")
                    .equals(structureRegistry.getKey(structure))) {
                // Inside a fortress - this is the spawn that gets replaced below.
                if (getConfig().shouldDebugLog()) {
                    getLogger().debug("Plain skeleton spawn inside a Nether fortress at {} - replacing it",
                            event.getEntity().blockPosition());
                }
                insideFortress = true;
                break;
            }
        }

        if (!insideFortress) {
            return;
        }

        // A plain skeleton trying to spawn inside a fortress - block it.
        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Blocked normal skeleton spawn in Nether at {}", event.getEntity().blockPosition());
        }

        // Cancel the spawn
        event.setSpawnCancelled(true);

        // Broadcast message to all players
        broadcastSkeletonBlockedMessage(serverLevel, event.getEntity().blockPosition());

        // Optionally spawn a Wither Skeleton in its place
        replaceWithWitherSkeleton(serverLevel, skeleton, event.getSpawnType());
    }

    /**
     * Announces a blocked skeleton spawn in chat.
     *
     * <p>Debug output, and gated as such: with debug logging off - the default - nothing is ever
     * sent. The text is an English literal on purpose; it is a developer aid, not a player-facing
     * message, and the module ships no lang keys.</p>
     *
     * @param level    the level the spawn was blocked in
     * @param position where it was blocked
     */
    private void broadcastSkeletonBlockedMessage(ServerLevel level, BlockPos position) {
        if (!getConfig().shouldDebugLog()) {
            return;
        }
        Component headline = Component
                .literal("🔥 A normal skeleton tried to spawn in a Fortress but was blocked! 🔥")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        Component coordinates = Component
                .literal("\nLocation: %d, %d, %d".formatted(position.getX(), position.getY(), position.getZ()))
                .withStyle(ChatFormatting.YELLOW);
        // The teleport is offered per recipient: /tp needs permission level 2, so handing the click
        // to everyone only produces an error message for the players who cannot run it.
        Component clickable = coordinates.copy().withStyle(style -> style.withClickEvent(
                new net.minecraft.network.chat.ClickEvent(
                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                        "/tp @s %d %d %d".formatted(position.getX(), position.getY(), position.getZ())
                )
        ));

        // Send to all players on the server
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(headline.copy()
                    .append(player.hasPermissions(TELEPORT_PERMISSION_LEVEL) ? clickable : coordinates));
        }

        getLogger().debug("Broadcasted skeleton block message for spawn at {}", position);
    }

    /**
     * Replaces the blocked skeleton with a Wither Skeleton
     */
    private void replaceWithWitherSkeleton(ServerLevel level, Skeleton originalSkeleton, MobSpawnType spawnType) {
        try {
            // Create a new Wither Skeleton at the same position
            WitherSkeleton witherSkeleton = EntityType.WITHER_SKELETON.create(level);
            if (witherSkeleton == null) {
                getLogger().warn("Failed to create Wither Skeleton replacement");
                return;
            }

            // Copy position and rotation from the original skeleton
            witherSkeleton.moveTo(originalSkeleton.getX(), originalSkeleton.getY(), originalSkeleton.getZ(),
                    originalSkeleton.getYRot(), originalSkeleton.getXRot());

            // Through EventHooks, not Mob#finalizeSpawn directly: that method is @ApiStatus.
            // OverrideOnly in NeoForge and calling it here would hide the replacement from every
            // other mod's FinalizeSpawnEvent handler. No recursion into this handler either - a
            // WitherSkeleton is not a Skeleton.
            EventHooks.finalizeMobSpawn(witherSkeleton, level,
                    level.getCurrentDifficultyAt(witherSkeleton.blockPosition()), spawnType, null);

            // Add the Wither Skeleton to the world
            level.addFreshEntity(witherSkeleton);

            if (getConfig().shouldDebugLog()) {
                getLogger().debug("Replaced blocked skeleton with Wither Skeleton at {}",
                        witherSkeleton.blockPosition());
            }

        } catch (Exception e) {
            getLogger().error("Failed to replace skeleton with Wither Skeleton", e);
        }
    }
}