package net.geraldhofbauer.vanillaplusadditions.mixin.pocket_crafting;

import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.menu.MenuMemory;
import net.geraldhofbauer.vanillaplusadditions.modules.pocket_crafting.menu.RememberedMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * Remembers, per player, how the menu that is currently open was opened.
 *
 * <p>Pocket Crafting needs it to send a player back into the chest they opened the crafting grid
 * from. Opening the grid closes the chest on the server - a player has exactly one open menu - and
 * an open menu does not know the provider that built it. So the provider is recorded on the way in.
 *
 * <p>One hook covers every menu. NeoForge patches {@code ServerPlayer} so that vanilla's
 * {@code openMenu(MenuProvider)} delegates to {@code openMenu(MenuProvider, Consumer)}, and its
 * {@code openMenu(MenuProvider, BlockPos)} extension funnels into the same method - every container,
 * vanilla or modded, passes through here. The extra-data writer is kept as well, because a modded
 * menu whose client factory reads that data would get an empty buffer on a reopen without it.
 *
 * <p>Recording is unconditional and costs one field write per opened menu. Whether a recorded menu
 * is ever <i>reopened</i> is decided elsewhere, against an allow-list - see the module.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMenuMemoryMixin implements MenuMemory {

    @Unique
    private RememberedMenu vpaLastMenu;

    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;Ljava/util/function/Consumer;)Ljava/util/OptionalInt;",
            at = @At("RETURN"))
    private void vpaRememberOpenedMenu(MenuProvider provider, Consumer<RegistryFriendlyByteBuf> extraData,
                                       CallbackInfoReturnable<OptionalInt> cir) {
        OptionalInt containerId = cir.getReturnValue();
        if (provider != null && containerId != null && containerId.isPresent()) {
            vpaLastMenu = new RememberedMenu(containerId.getAsInt(), provider, extraData);
        }
    }

    @Override
    public RememberedMenu vpaGetLastMenu() {
        return vpaLastMenu;
    }
}
