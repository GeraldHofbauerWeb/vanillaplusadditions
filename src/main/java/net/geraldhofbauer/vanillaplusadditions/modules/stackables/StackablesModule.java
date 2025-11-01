package net.geraldhofbauer.vanillaplusadditions.modules.stackables;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.stackables.config.StackablesConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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
            getLogger().warn("Mod event bus not available during module initialization, falling back to global event bus: {}", e.getMessage());
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
        // FIXME
        // Skip any configuration for now, as the config has not been loaded yet at this point
//        if (!isModuleEnabled()) {
//            return;
//        }

        // Only handle item registry events
        if (!event.getRegistryKey().equals(Registries.ITEM)) {
            return;
        }

        StackablesConfig config = getConfig();

        // Track which resources we've already handled to avoid duplicates
        Set<ResourceLocation> handled = new HashSet<>();

        // First, process entries explicitly listed in the config
        for (String entry : config.getStackableItems()) {
            // Expect format: namespace:id:stack
            int lastColon = entry.lastIndexOf(':');
            if (lastColon <= 0 || lastColon == entry.length() - 1) {
                getLogger().warn("Invalid stackables config entry (expected namespace:id:stack): {}", entry);
                continue;
            }

            String resourceStr = entry.substring(0, lastColon);
            String stackPart = entry.substring(lastColon + 1);
            int desiredStack;
            try {
                desiredStack = Integer.parseInt(stackPart);
            } catch (NumberFormatException e) {
                getLogger().warn("Invalid stack size in stackables config entry '{}': {}", entry, stackPart);
                continue;
            }

            ResourceLocation resourceLocation;
            try {
                resourceLocation = ResourceLocation.parse(resourceStr);
            } catch (Exception e) {
                getLogger().warn("Invalid resource location in stackables config: {}", resourceStr);
                continue;
            }

            // Mark as handled so auto-detection won't duplicate
            handled.add(resourceLocation);

            Optional<Item> oldItemOpt = BuiltInRegistries.ITEM.getOptional(resourceLocation);
            if (oldItemOpt.isEmpty()) {
                // FIXME: Enable debug logging again once config system is stable
//                if (config.shouldDebugLog()) {
                getLogger().debug("Item not found in registry, skipping: {}", resourceLocation);
//                }
                continue;
            }

            makeItemStackable(event, resourceLocation, oldItemOpt.get(), desiredStack);
        }

        // Second, auto-detect toughasnails items (juices / ice / charc) and make them stackable to 16
        try {
            for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) {
                // accept any namespace that contains "tough" to be more robust
                if (!rl.getNamespace().contains("tough")) {
                    continue;
                }
                String path = rl.getPath();
                // match juice, ice, ice_cream, charc (loose heuristics)
                if (!(path.contains("juice") || path.contains("ice") || path.contains("ice_cream") || path.contains("charc"))) {
                    continue;
                }
                if (handled.contains(rl)) {
                    continue; // skip if user already configured
                }

                Optional<Item> oldItemOpt = BuiltInRegistries.ITEM.getOptional(rl);
                if (oldItemOpt.isEmpty()) {
                    continue;
                }

                // desired stack for these categories = 16
                makeItemStackable(event, rl, oldItemOpt.get(), 16);
                handled.add(rl);
            }
        } catch (Throwable t) {
            getLogger().debug("Auto-detection of toughasnails items failed: {}", t.getMessage());
        }
    }

    private void makeItemStackable(RegisterEvent event, ResourceLocation resourceLocation, Item oldItem, int desiredStack) {
        try {
            // If already stackable to >= desiredStack, skip
            try {
                if (oldItem.getDefaultMaxStackSize() >= desiredStack) {
//                    if (getConfig().shouldDebugLog()) {
                    getLogger().debug("Item already has stack size >={}, skipping: {}", desiredStack, resourceLocation);
//                    }
                    return;
                }
            } catch (Throwable ignored) {
                // ignore - continue with attempt to replace
            }

            Item.Properties newProps = new Item.Properties().stacksTo(desiredStack);

            // Try to preserve food properties (via reflection) if present on the original item
            try {
                Method getFood = oldItem.getClass().getMethod("getFoodProperties");
                Object foodProps = getFood.invoke(oldItem);
                if (foodProps != null) {
                    Method[] methods = Item.Properties.class.getMethods();
                    for (Method m : methods) {
                        if (!m.getName().equals("food")) {
                            continue;
                        }
                        if (m.getParameterCount() != 1) {
                            continue;
                        }
                        try {
                            m.invoke(newProps, foodProps);
                            break;
                        } catch (IllegalArgumentException ignored) {
                            // parameter not compatible, try next
                        }
                    }
                }
            } catch (NoSuchMethodException ignored) {
                // no food properties method - that's fine
            } catch (Exception e) {
                getLogger().debug("Could not copy food properties for {}: {}", resourceLocation, e.getMessage());
            }

            Item newItem = null;
            Class<? extends Item> cls = oldItem.getClass();

            // Try constructor: (Item.Properties)
            try {
                Constructor<? extends Item> ctor = cls.getConstructor(Item.Properties.class);
                newItem = ctor.newInstance(newProps);
            } catch (NoSuchMethodException e1) {
                // fallback to generic Item
                // FIXME: Enable debug logging again once config system is stable
//                if (getConfig().shouldDebugLog()) {
                getLogger().debug("Original item class has no (Item.Properties) constructor, "
                        + "falling back to generic Item for {}", resourceLocation);
//                }
            } catch (Exception ex) {
                getLogger().debug("Failed to instantiate original item class for {}: {}", resourceLocation, ex.getMessage());
            }

            if (newItem == null) {
                // fallback: create a plain Item with same stack size and possibly food properties
                newItem = new Item(newProps);
            }

            Item finalNewItem = newItem;
            try {
                event.register(Registries.ITEM, resourceLocation, () -> finalNewItem);
                getLogger().info("Made item stackable ({}): {}", desiredStack, resourceLocation);
            } catch (Exception e) {
                getLogger().warn("Failed to register replacement item for {}: {}", resourceLocation, e.getMessage());
            }
        } catch (Throwable t) {
            getLogger().warn("Unexpected error while making {} stackable: {}", resourceLocation, t.getMessage());
        }
    }
}
