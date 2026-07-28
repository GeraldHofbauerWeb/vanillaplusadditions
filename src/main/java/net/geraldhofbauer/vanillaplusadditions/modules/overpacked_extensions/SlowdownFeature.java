package net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions;

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
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Slowdown-override feature of {@link OverpackedExtensionsModule}.
 *
 * <p>Overpacked applies a movement-speed penalty based on how many items are stored in its backpack
 * items. This handler runs at {@link EventPriority#LOW} — after Overpacked's own NORMAL-priority
 * handler — and re-applies the speed modifier scaled by the configured multiplier (0.0 = removed).
 * It references no Overpacked classes (it reads the vanilla {@code CUSTOM_DATA} "Count" tag), so it
 * runs whether or not Overpacked is installed.
 */
public final class SlowdownFeature {

    private static final ResourceLocation OVERPACKED_SPEED =
            ResourceLocation.fromNamespaceAndPath("overpacked", "speed");

    private final OverpackedExtensionsModule module;

    public SlowdownFeature(OverpackedExtensionsModule module) {
        this.module = module;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!module.isModuleEnabled()) {
            return;
        }

        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }

        double multiplier = module.getConfig().getSlowdownMultiplierValue();

        // Recalculate the slowdown the same way Overpacked does.
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

        // Apply the configured multiplier.
        slowdown *= multiplier;

        // Replace Overpacked's modifier with our adjusted one.
        AttributeInstance attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null) {
            if (attribute.getModifier(OVERPACKED_SPEED) != null) {
                attribute.removeModifier(OVERPACKED_SPEED);
            }
            attribute.addTransientModifier(new AttributeModifier(
                    OVERPACKED_SPEED, -slowdown, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
