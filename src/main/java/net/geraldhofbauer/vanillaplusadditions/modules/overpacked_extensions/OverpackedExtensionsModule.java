package net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions.compat.OverpackedGuiBridge;
import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions.config.OverpackedExtensionsConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_extensions.network.OpenBackpackCompartmentPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Overpacked Extensions — a single module bundling three quality-of-life features for the Overpacked
 * giant-backpack mod. All three share this module's {@code enabled} flag but each has its own config
 * toggle (see {@link OverpackedExtensionsConfig}):
 * <ol>
 *   <li><b>Slowdown override</b> ({@link SlowdownFeature}) — re-applies Overpacked's movement penalty
 *       with a configurable multiplier. References no Overpacked classes, so it always runs.</li>
 *   <li><b>Backpack keybinds</b> — open the compartments of the worn backpack via Overpacked's own GUI
 *       (see {@link OverpackedGuiBridge}).</li>
 * </ol>
 *
 * <p>Sorting + searching inside the backpack are intentionally <b>not</b> reimplemented here: Quark
 * already provides both, and its buttons appear on Overpacked's screen once its class is whitelisted
 * in Quark's {@code "Allowed Screens"} config (see {@code docs/overpacked_extensions.md}).
 *
 * <p>The backpack features are no-ops when Overpacked/Curios are absent: all references to those mods
 * live inside {@link OverpackedGuiBridge} / {@code CuriosBackpackAccess}, reached only after an
 * {@code isAvailable()} gate.
 */
public class OverpackedExtensionsModule
        extends AbstractModule<OverpackedExtensionsModule, OverpackedExtensionsConfig> {

    public OverpackedExtensionsModule() {
        super("overpacked_extensions",
                "Overpacked Extensions",
                "Quality-of-life extensions for Overpacked giant backpacks: configurable slowdown "
                        + "override, keybinds to open worn compartments, and a Quark-style sort button.",
                OverpackedExtensionsConfig::new);
    }

    @Override
    protected void onInitialize() {
        // Slowdown override — no Overpacked classes referenced, safe to always register.
        NeoForge.EVENT_BUS.register(new SlowdownFeature(this));

        // Backpack open/sort packets. The registrar itself links no Overpacked types; the handlers
        // gate on availability before touching the bridge.
        getModEventBus().addListener(this::onRegisterPayloadHandlers);

        // The bridge references Overpacked types, so only register its event handlers when Overpacked
        // (and Curios) are present. isAvailable() reads cached ModList booleans and links no Overpacked
        // classes.
        if (OverpackedGuiBridge.isAvailable()) {
            NeoForge.EVENT_BUS.register(OverpackedGuiBridge.class);
        }

        getLogger().info("Overpacked Extensions module initialized (overpacked+curios present: {})",
                OverpackedGuiBridge.isAvailable());
    }

    private void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
                OpenBackpackCompartmentPacket.TYPE,
                OpenBackpackCompartmentPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> {
                    if (!isModuleEnabled() || !getConfig().isBackpackKeysEnabled()) {
                        return;
                    }
                    if (!(ctx.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    int compartment = packet.compartment();
                    if (compartment < 0 || compartment > 2) {
                        return;
                    }
                    if (!OverpackedGuiBridge.isAvailable()) {
                        player.displayClientMessage(Component.translatable(
                                "message.vanillaplusadditions.overpacked_extensions.unavailable"), true);
                        return;
                    }
                    OverpackedGuiBridge.open(player, compartment);
                }));
    }
}
