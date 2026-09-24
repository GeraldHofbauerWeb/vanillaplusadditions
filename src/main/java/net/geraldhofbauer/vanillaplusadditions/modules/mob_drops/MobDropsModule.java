package net.geraldhofbauer.vanillaplusadditions.modules.mob_drops;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_drops.config.MobDropsConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mob Drops Module
 * <p>
 * Adds custom drops to mobs based on configuration.
 * <p>
 * Features:
 * - Add configurable drops to any mob
 * - Control drop chance and maximum drops
 * - Easy configuration format: mob_id;item_id;chance[;max_drops]
 */
public class MobDropsModule
        extends AbstractModule<MobDropsModule, MobDropsConfig> {

    /**
     * Parsed config, keyed by mob: EntityType -&gt; list of drops.
     *
     * <p>A list, not a map by item, on purpose - the same item may be configured twice for one mob
     * (two rules with different chances and stack sizes), and every matching rule is rolled.</p>
     */
    private final Map<EntityType<?>, List<DropInfo>> mobDropsCache = new HashMap<>();

    public MobDropsModule() {
        super("mob_drops",
                "Mob Drops",
                "Adds custom drops to mobs based on configuration",
                MobDropsConfig::new
        );
    }

    @Override
    protected void onInitialize() {
        // Register event listeners for this module
        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Mob Drops module initialized - Custom mob drops are now active!");
    }

    @Override
    protected void onCommonSetup() {
        reloadMobDropsCache();
        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Mob Drops module common setup complete");
        }
    }

    /**
     * Reloads the mob drops cache from configuration
     */
    public void reloadMobDropsCache() {
        mobDropsCache.clear();
        if (!isModuleEnabled()) {
            return;
        }

        List<String> entries = getConfig().getMobDrops();
        for (String entry : entries) {
            String[] parts = entry.split(";");
            if (parts.length < 3) {
                getLogger().warn("Mob drops config: Invalid format, need at least 3 parts: {}", entry);
                continue;
            }

            try {
                ResourceLocation mobRl = ResourceLocation.parse(parts[0]);
                ResourceLocation itemRl = ResourceLocation.parse(parts[1]);
                float chance = Float.parseFloat(parts[2]);
                int maxDrops = 1;

                if (parts.length >= 4) {
                    try {
                        maxDrops = Integer.parseInt(parts[3]);
                    } catch (NumberFormatException e) {
                        getLogger().warn("Mob drops config: Invalid max_drops value: {}", parts[3]);
                    }
                }

                // containsKey first: ENTITY_TYPE is a DefaultedRegistry whose get() never returns
                // null - an unknown id silently comes back as minecraft:pig, so a typo used to
                // attach the rule to pigs instead of reporting itself.
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(mobRl)) {
                    getLogger().warn("Mob drops config: Mob not found: {} (rule ignored)", parts[0]);
                    continue;
                }
                EntityType<?> mobType = BuiltInRegistries.ENTITY_TYPE.get(mobRl);
                Item item = BuiltInRegistries.ITEM.get(itemRl);

                if (item == Items.AIR) {
                    getLogger().warn("Mob drops config: Item not found: {} (rule ignored)", parts[1]);
                    continue;
                }

                // Add to cache
                mobDropsCache.computeIfAbsent(mobType, k -> new ArrayList<>())
                        .add(new DropInfo(item, chance, maxDrops));

                if (getConfig().shouldDebugLog()) {
                    getLogger().debug("Added drop config: {} drops {} with {}% chance (max: {})",
                            mobRl, itemRl, chance * 100, maxDrops);
                }

            } catch (Exception e) {
                getLogger().error("Failed to parse mob drops entry: {}", entry, e);
            }
        }

        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Mob drops cache reloaded. {} mob types configured.", mobDropsCache.size());
        }
    }

    /**
     * Event handler that adds configured drops to mobs when they die
     */
    @SubscribeEvent
    public void onEntityDrop(LivingDropsEvent event) {
        if (!isModuleEnabled()) {
            return;
        }

        LivingEntity entity = event.getEntity();
        EntityType<?> entityType = entity.getType();

        // Check if this mob has configured drops
        List<DropInfo> drops = mobDropsCache.get(entityType);
        if (drops == null || drops.isEmpty()) {
            return;
        }

        // Process each configured drop
        for (DropInfo dropInfo : drops) {
            // Check if drop should occur
            if (entity.getRandom().nextFloat() < dropInfo.chance) {
                // Determine stack size
                int amount = dropInfo.maxDrops > 1
                        ? entity.getRandom().nextInt(dropInfo.maxDrops) + 1
                        : 1;

                // Create and add the item entity
                event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                        entity.level(),
                        entity.getX(),
                        entity.getY(),
                        entity.getZ(),
                        new ItemStack(dropInfo.item, amount)
                ));

                if (getConfig().shouldDebugLog()) {
                    getLogger().debug("Added drop {} x{} from {} at {}",
                            dropInfo.item.toString(), amount, entityType.toString(), entity.blockPosition());
                }
            }
        }
    }

    /**
     * Helper class to store drop information
     */
    private record DropInfo(Item item, float chance, int maxDrops) {
        private DropInfo(Item item, float chance, int maxDrops) {
            this.item = item;
            this.chance = chance;
            this.maxDrops = Math.max(1, maxDrops);
        }
    }
}
