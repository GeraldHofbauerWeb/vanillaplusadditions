package net.geraldhofbauer.vanillaplusadditions.modules.static_fov.client;

import net.geraldhofbauer.vanillaplusadditions.core.Module;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.static_fov.StaticFovModule;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;

/**
 * Clamps the player FOV modifier to at most 1.0: anything that would widen the FOV
 * (sprinting, Speed effect, creative flight) is neutralized, while modifiers below 1.0
 * (bow draw zoom) pass through unchanged.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class StaticFovClientEvents {

    private StaticFovClientEvents() {
    }

    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        StaticFovModule module = getModule();
        if (module == null || !module.isModuleEnabled()) {
            return;
        }
        if (event.getNewFovModifier() > 1.0f) {
            event.setNewFovModifier(1.0f);
        }
    }

    private static StaticFovModule getModule() {
        Module module = ModuleManager.getInstance().getModule("static_fov");
        if (module instanceof StaticFovModule staticFovModule) {
            return staticFovModule;
        }
        return null;
    }
}
