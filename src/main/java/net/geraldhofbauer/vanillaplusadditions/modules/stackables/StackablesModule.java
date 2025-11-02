package net.geraldhofbauer.vanillaplusadditions.modules.stackables;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.stackables.config.StackablesConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class StackablesModule extends AbstractModule<StackablesModule, StackablesConfig> {
    public StackablesModule() {
        super("stackables",
                "Stackables",
                "Allows certain items to be stacked beyond their normal stack size.",
                StackablesConfig::new
        );
    }

    @Override
    protected void onInitialize() {
        // Register event listeners for this module on the module's event bus (preferred).
        // Fall back to the global NeoForge event bus if the mod event bus isn't available.
        try {
            getModEventBus().register(this);
        } catch (IllegalStateException e) {
            getLogger().warn(
                "Mod event bus not available during module initialization, "
                + "falling back to global event bus: {}",
                e.getMessage()
            );
            NeoForge.EVENT_BUS.register(this);
        }
    }

    @Override
    protected void onCommonSetup() {
        if (getConfig().shouldDebugLog()) {
            getLogger().debug("StackablesModule common setup complete.");

            // Debug helper: list possible tough-as-nails items from registry for verification
            try {
                var keys = BuiltInRegistries.ITEM.keySet();
                for (ResourceLocation rl : keys) {
                    String ns = rl.getNamespace();
                    String path = rl.getPath();
                    if (ns.contains("tough") || path.contains("juice") || path.contains("ice") || path.contains("charc")) {
                        getLogger().info("Candidate registry item: {}", rl);
                    }
                }
            } catch (Throwable t) {
                getLogger().debug("Unable to dump item registry keys for StackablesModule: {}", t.getMessage());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRegisterItems(RegisterEvent event) {
        // This event is NOT used anymore - all patching happens in ModifyDefaultComponentsEvent
        // Keeping this method for potential future use
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        getLogger().info("=== STACKABLES MODULE: Starting component patching ===");

        // Patch vanilla potions
        int desired = Math.max(2, getConfig().getDefaultPotionStackSize());
        try {
            event.modify(Items.POTION, builder -> builder.set(DataComponents.MAX_STACK_SIZE, desired));
            event.modify(Items.SPLASH_POTION, builder -> builder.set(DataComponents.MAX_STACK_SIZE, desired));
            event.modify(Items.LINGERING_POTION, builder -> builder.set(DataComponents.MAX_STACK_SIZE, desired));
            event.modify(Items.TIPPED_ARROW, builder -> builder.set(DataComponents.MAX_STACK_SIZE, desired));
            getLogger().info("Patched potion-related items default MAX_STACK_SIZE to {}", desired);
        } catch (Throwable t) {
            getLogger().warn("Failed to patch potion default components: {}", t.getMessage());
        }

        // Build a set of items already handled by config to avoid duplicates
        Set<ResourceLocation> handledByConfig = new HashSet<>();

        // Patch items from config list
        getLogger().info("=== Processing config items ===");
        for (String entry : getConfig().getStackableItems()) {
            int lastColon = entry.lastIndexOf(':');
            if (lastColon <= 0 || lastColon == entry.length() - 1) {
                continue;
            }

            String resourceStr = entry.substring(0, lastColon);
            String stackPart = entry.substring(lastColon + 1);
            int desiredStackSize;
            try {
                desiredStackSize = Integer.parseInt(stackPart);
            } catch (NumberFormatException e) {
                continue;
            }

            ResourceLocation resourceLocation;
            try {
                resourceLocation = ResourceLocation.parse(resourceStr);
            } catch (Exception e) {
                continue;
            }

            handledByConfig.add(resourceLocation);

            Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(resourceLocation);
            if (itemOpt.isPresent()) {
                Item item = itemOpt.get();
                int currentMax = item.getDefaultMaxStackSize();
                getLogger().info("Item {} current stack: {}, target: {}", resourceLocation, currentMax, desiredStackSize);

                try {
                    event.modify(item, builder -> {
                        builder.set(DataComponents.MAX_STACK_SIZE, desiredStackSize);
                        getLogger().debug("Set MAX_STACK_SIZE component for {} to {}", resourceLocation, desiredStackSize);
                    });
                    getLogger().info("✓ Successfully patched {} to stack size {}", resourceLocation, desiredStackSize);
                } catch (Throwable t) {
                    getLogger().error("✗ Failed to patch {}: {}", resourceLocation, t.getMessage(), t);
                }
            } else {
                getLogger().warn("Item not found in registry: {}", resourceLocation);
            }
        }

        // Auto-detect and patch tough as nails items
        getLogger().info("=== Starting auto-detection for Tough as Nails items ===");
        int patchedCount = 0;
        int skippedDurability = 0;
        try {
            for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) {
                if (!rl.getNamespace().contains("tough")) {
                    continue;
                }

                String path = rl.getPath();

                // Skip if already handled by config
                if (handledByConfig.contains(rl)) {
                    getLogger().debug("Skipping {} - already handled by config", rl);
                    continue;
                }

                // Check if it matches our patterns
                boolean matches = path.contains("juice")
                                || path.contains("ice_cream")
                                || path.contains("charc_os")
                                || path.equals("dirty_water_bottle")
                                || path.equals("purified_water_bottle")
                                || path.startsWith("empty_");

                if (!matches) {
                    continue;
                }

                Optional<Item> itemOpt = BuiltInRegistries.ITEM.getOptional(rl);
                if (itemOpt.isPresent()) {
                    Item item = itemOpt.get();
                    int currentMax = item.getDefaultMaxStackSize();

                    // Check if item has durability
                    if (currentMax == 1) {
                        getLogger().debug("Skipping {} - appears to have durability (max stack = 1)", rl);
                        skippedDurability++;
                        continue;
                    }

                    int stackSize = 64;
                    getLogger().info("Auto-detect: {} current stack: {}, target: {}", rl, currentMax, stackSize);

                    try {
                        event.modify(item, builder -> {
                            builder.set(DataComponents.MAX_STACK_SIZE, stackSize);
                            getLogger().debug("Set MAX_STACK_SIZE component for {} to {}", rl, stackSize);
                        });
                        getLogger().info("✓ AUTO-PATCHED: {} to stack size {}", rl, stackSize);
                        patchedCount++;
                    } catch (Throwable t) {
                        if (t.getMessage() != null && t.getMessage().contains("durability")) {
                            getLogger().debug("Skipping {} - has durability", rl);
                            skippedDurability++;
                        } else {
                            getLogger().error("✗ Failed to auto-patch {}: {}", rl, t.getMessage(), t);
                        }
                    }
                }
            }
            getLogger().info("=== Auto-detection complete. Patched {} items, skipped {} items with durability ===",
                           patchedCount, skippedDurability);
        } catch (Throwable t) {
            getLogger().error("Auto-detection of toughasnails items failed: {}", t.getMessage(), t);
        }

        getLogger().info("=== STACKABLES MODULE: Component patching complete ===");
    }

    // Diese Methode wird nicht mehr verwendet
    private void makeItemStackable(RegisterEvent event, ResourceLocation resourceLocation, Item oldItem, int desiredStack) {
        // Deprecated - not used anymore
    }
}
