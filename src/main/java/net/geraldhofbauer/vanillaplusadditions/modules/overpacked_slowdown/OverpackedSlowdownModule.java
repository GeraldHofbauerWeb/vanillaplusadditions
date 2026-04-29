package net.geraldhofbauer.vanillaplusadditions.modules.overpacked_slowdown;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_slowdown.config.OverpackedSlowdownConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Overpacked Slowdown Module
 * <p>
 * Overrides the slowdown effect applied by the Overpacked mod by listening to the same
 * PlayerTickEvent and re-applying the speed modifier with a configurable multiplier.
 * <p>
 * The Overpacked mod applies a movement speed penalty based on how many items are stored
 * in custom backpack items. This module intercepts that by running after Overpacked's
 * event handler and adjusting the modifier value using the configured multiplier.
 * <p>
 * Features:
 * - Configurable slowdown multiplier (0.0 = no slowdown, 1.0 = original, etc.)
 * - Runs at LOW priority to execute after Overpacked's NORMAL priority handler
 */
public class OverpackedSlowdownModule extends AbstractModule<OverpackedSlowdownModule, OverpackedSlowdownConfig> {

    private static final ResourceLocation OVERPACKED_SPEED = ResourceLocation.fromNamespaceAndPath("overpacked", "speed");

    public OverpackedSlowdownModule() {
        super("overpacked_slowdown",
                "Overpacked Slowdown Override",
                "Overrides the slowdown effect from the Overpacked mod with a configurable multiplier",
                OverpackedSlowdownConfig::new
        );
    }

    @Override
    protected void onInitialize() {
        NeoForge.EVENT_BUS.register(this);

        getLogger().info("Overpacked Slowdown Override module initialized - multiplier: {}",
                getConfig().getSlowdownMultiplierValue());
    }

    @Override
    protected void onCommonSetup() {
        if (getConfig().shouldDebugLog()) {
            getLogger().debug("Overpacked Slowdown Override module common setup complete");
        }
    }

    /**
     * Listens for PlayerTickEvent.Pre at LOW priority so it runs after Overpacked's
     * handler (which uses default NORMAL priority). Recalculates the slowdown using
     * the configured multiplier and re-applies the attribute modifier.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!isModuleEnabled()) {
            return;
        }

        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }

        double multiplier = getConfig().getSlowdownMultiplierValue();

        // Recalculate the slowdown the same way Overpacked does
        double slowdown = 0.0;
        List<ItemStack> items = new ArrayList<>(player.getInventory().items);
        if (!player.getOffhandItem().isEmpty()) {
            items.add(player.getOffhandItem());
        }

        for (ItemStack stack : items) {
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data != null && data.copyTag().contains("Count")) {
                int count = data.copyTag().getInt("Count");
                if (count >= 27) {
                    slowdown += (1.0 - slowdown) * (count < 54 ? 0.1 : (count < 81 ? 0.2 : 0.3));
                }
            }
        }

        // Apply the configured multiplier
        slowdown *= multiplier;

        // Replace Overpacked's modifier with our adjusted one
        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null) {
            if (attribute.getModifier(OVERPACKED_SPEED) != null) {
                attribute.removeModifier(OVERPACKED_SPEED);
            }

            attribute.addTransientModifier(new AttributeModifier(
                    OVERPACKED_SPEED, -slowdown, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }

        if (getConfig().shouldDebugLog() && slowdown > 0) {
            getLogger().debug("Applied adjusted slowdown: {} (multiplier: {})", slowdown, multiplier);
        }
    }
}
