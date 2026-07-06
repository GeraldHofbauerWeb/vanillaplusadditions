package net.geraldhofbauer.vanillaplusadditions.modules.overpacked_backpack_keys;

import net.geraldhofbauer.vanillaplusadditions.core.AbstractModule;
import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_backpack_keys.compat.OverpackedGuiBridge;
import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_backpack_keys.config.OverpackedBackpackKeysConfig;
import net.geraldhofbauer.vanillaplusadditions.modules.overpacked_backpack_keys.network.OpenBackpackCompartmentPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Overpacked Backpack Keybinds.
 *
 * <p>Adds three keybinds that open the compartments of the giant backpack the player wears (via
 * Curios): main/center on {@code K} by default, right and left unbound. The GUI is Overpacked's own
 * — see {@link OverpackedGuiBridge}. The whole feature is a no-op when Overpacked/Curios are absent.
 */
public class OverpackedBackpackKeysModule
        extends AbstractModule<OverpackedBackpackKeysModule, OverpackedBackpackKeysConfig> {

    public OverpackedBackpackKeysModule() {
        super("overpacked_backpack_keys",
                "Overpacked Backpack Keybinds",
                "Keybinds to open the compartments of a worn Overpacked giant backpack "
                        + "(main compartment on K by default; right/left unbound).",
                OverpackedBackpackKeysConfig::new);
    }

    @Override
    protected void onInitialize() {
        getModEventBus().addListener(this::onRegisterPayloadHandlers);

        // The bridge references Overpacked types, so only touch it when Overpacked (and Curios) are
        // present. isAvailable() reads cached ModList booleans and links no Overpacked classes.
        if (OverpackedGuiBridge.isAvailable()) {
            NeoForge.EVENT_BUS.register(OverpackedGuiBridge.class);
        }

        getLogger().info("Overpacked Backpack Keybinds module initialized (overpacked+curios present: {})",
                OverpackedGuiBridge.isAvailable());
    }

    private void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(
                OpenBackpackCompartmentPacket.TYPE,
                OpenBackpackCompartmentPacket.STREAM_CODEC,
                (packet, ctx) -> ctx.enqueueWork(() -> {
                    if (!isModuleEnabled()) {
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
                                "message.vanillaplusadditions.overpacked_backpack_keys.unavailable"), true);
                        return;
                    }
                    OverpackedGuiBridge.open(player, compartment);
                }));
    }
}
