package net.geraldhofbauer.vanillaplusadditions.modules.wither_skeleton;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.wither_skeleton.config.WitherSkeletonConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.NetherFortressStructure;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wither Skeleton Module
 * <p>
 * Prevents normal skeletons from spawning in the Nether and broadcasts a message
 * when this happens. Optionally replaces them with Wither Skeletons to maintain
 * the intended Nether difficulty.
 * <p>
 * Features:
 * - Prevents normal skeleton spawns in the Nether
 * - Broadcasts configurable messages to all players
 * - Option to replace blocked skeletons with Wither Skeletons
 * - Configurable message format and replacement behavior
 */
public class WitherSkeletonModule
        extends AbstractModule<WitherSkeletonModule, WitherSkeletonConfig> {

    private final Map<Item, Float> additionalDropsCache = new HashMap<>();

    public WitherSkeletonModule() {
        super("wither_skeleton",
                "Wither Skeleton Enforcer",
                "Prevents normal skeletons from spawning in the Nether and broadcasts messages "
                        + "about blocked spawns",
                WitherSkeletonConfig::new
        );
    }

    @Override
    protected void onInitialize() {
        // Register event listeners for this module
        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Wither Skeleton module initialized - Normal skeletons are now banned from the Nether!");
    }

    @Override
    protected void onCommonSetup() {
        reloadAdditionalDropsCache();
        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Wither Skeleton module common setup complete");
        }
    }

    public void reloadAdditionalDropsCache() {
        additionalDropsCache.clear();
        if (!isModuleEnabled()) {
            return;
        }

        List<String> entries = getConfig().getAdditionalDrops();
        for (String entry : entries) {
            String[] parts = entry.split(";");
            if (parts.length != 2) {
                continue;
            }

            try {
                ResourceLocation itemRl = ResourceLocation.parse(parts[0]);
                float chance = Float.parseFloat(parts[1]);

                Item item = BuiltInRegistries.ITEM.get(itemRl);

                if (item != Items.AIR) {
                    additionalDropsCache.put(item, chance);
                } else {
                    if (getConfig().shouldDebugLog()) {
                        getLogger().warn("Additional drop config: Item not found: {}", parts[0]);
                    }
                }
            } catch (Exception e) {
                getLogger().error("Failed to parse additional drop entry: {}", entry, e);
            }
        }

        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Additional drops cache reloaded. {} items configured.", additionalDropsCache.size());
        }
    }

    /**
     * Event handler that increases the drop rate of wither skeleton skulls
     */
    @SubscribeEvent
    public void onEntityKill(LivingDropsEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        if (event.getEntity() instanceof WitherSkeleton witherSkeleton) {
            // Additional configurable drops (including Wither Skulls)
            for (Map.Entry<Item, Float> entry : additionalDropsCache.entrySet()) {
                if (witherSkeleton.getRandom().nextFloat() < entry.getValue()) {
                    event.getDrops().add(new ItemEntity(witherSkeleton.level(),
                            witherSkeleton.getX(), witherSkeleton.getY(), witherSkeleton.getZ(),
                            new ItemStack(entry.getKey())));

                    if (getConfig().shouldDebugLog()) {
                        getLogger().debug("Added additional drop {} at {} (Chance: {})",
                                entry.getKey().toString(), witherSkeleton.blockPosition(), entry.getValue());
                    }
                }
            }
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

        // Check if the skeleton is inside a fortress - allow it if so
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
                // Allow normal skeleton spawn inside Nether Fortress
                if (getConfig().shouldDebugLog()) {
                    getLogger().debug("Allowed normal skeleton spawn inside Nether Fortress at {}",
                            event.getEntity().blockPosition());
                }
                insideFortress = true;
                break;
            }
        }

        if (!insideFortress) {
            return;
        }

        // This is a normal skeleton trying to spawn in the Nether - block it!
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
     * Broadcasts a message to all players about the blocked skeleton spawn
     */
    private void broadcastSkeletonBlockedMessage(ServerLevel level, BlockPos position) {
        if (!getConfig().shouldDebugLog()) {
            return;
        }
        Component message = Component
                .literal("🔥 A normal skeleton tried to spawn in a Fortress but was blocked! 🔥")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component
                        .literal("\nLocation: %d, %d, %d".formatted(position.getX(), position.getY(), position.getZ()))
                        .withStyle(ChatFormatting.YELLOW)
                        .withStyle(style -> style.withClickEvent(
                                new net.minecraft.network.chat.ClickEvent(
                                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                        "/tp @p %d %d %d".formatted(position.getX(), position.getY(), position.getZ())
                                )
                        ))
                );

        // Send to all players on the server
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
        }

        getLogger().info("Broadcasted skeleton block message for spawn at {}", position);
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

            // Finalize the spawn with the same spawn type
            witherSkeleton.finalizeSpawn(level, level.getCurrentDifficultyAt(witherSkeleton.blockPosition()),
                    spawnType, null);

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