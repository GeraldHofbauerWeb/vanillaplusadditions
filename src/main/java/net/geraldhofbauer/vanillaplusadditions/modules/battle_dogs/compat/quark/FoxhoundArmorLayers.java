package net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.compat.quark;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.violetmoon.quark.content.mobs.client.model.FoxhoundModel;
import org.violetmoon.quark.content.mobs.entity.Foxhound;
import org.violetmoon.quark.content.mobs.module.FoxhoundModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hangs {@link FoxhoundArmorLayer} on Quark's Foxhound renderer.
 *
 * <p>Only ever entered behind {@link QuarkFoxhoundCompat#isAvailable()} - this class names Quark
 * types in its own method bodies and must not be linked without Quark present.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class FoxhoundArmorLayers {

    private static final Logger LOGGER = LoggerFactory.getLogger("VanillaPlusAdditions/battle_dogs");

    private FoxhoundArmorLayers() {
    }

    /**
     * Adds the armour layer to the Foxhound renderer, if there is one to add it to.
     *
     * <p>Quark being installed is not enough: its Foxhound is a module of its own that an operator
     * can switch off, and then no entity type is registered and no model layer is baked. Both are
     * checked rather than assumed, and the whole thing is wrapped - a missing cosmetic layer is not
     * worth taking a client down for.</p>
     *
     * @param event the layer-registration event
     */
    @SuppressWarnings("unchecked")
    public static void addTo(EntityRenderersEvent.AddLayers event) {
        if (FoxhoundModule.foxhoundType == null) {
            return; // Quark is here, its Foxhound module is not
        }
        try {
            if (!(event.getRenderer(FoxhoundModule.foxhoundType) instanceof LivingEntityRenderer<?, ?> renderer)) {
                return;
            }
            LivingEntityRenderer<Foxhound, FoxhoundModel> foxhoundRenderer =
                    (LivingEntityRenderer<Foxhound, FoxhoundModel>) renderer;
            foxhoundRenderer.addLayer(new FoxhoundArmorLayer(foxhoundRenderer, event.getEntityModels()));
        } catch (RuntimeException e) {
            LOGGER.warn("Could not add the Foxhound armour layer - foxhounds will wear our armour "
                    + "without showing it: {}", e.toString());
        }
    }
}
