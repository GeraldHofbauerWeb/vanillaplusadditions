package net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.MenuProvider;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * How a player's current menu was opened - enough to open it a second time.
 *
 * @param containerId the id the server handed that menu, used to check it is still the one open
 * @param provider    the provider that built it
 * @param extraData   NeoForge's extra client data writer it was opened with, or null
 */
public record RememberedMenu(int containerId, MenuProvider provider,
                             @Nullable Consumer<RegistryFriendlyByteBuf> extraData) {
}
