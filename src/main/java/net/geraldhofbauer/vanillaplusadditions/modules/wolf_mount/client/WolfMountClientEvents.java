package net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount.client;

import net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount.WolfMountModule;
import net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount.WolfMountRules;
import net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount.network.MountWolfPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Wolf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side mount gesture: modifier (default Ctrl) + right-click on a rideable wolf.
 *
 * <p>The modifier is client-only state, so the decision has to start here and be relayed to the
 * server with {@link MountWolfPacket}. Cancelling the event stops the client's own local
 * interaction, but not the vanilla {@code ServerboundInteractPacket} — that has already been sent
 * one line earlier in {@code MultiPlayerGameMode.interact}. The server therefore still runs the
 * vanilla right-click (which on a tamed wolf toggles sit/stand); the mount packet arrives right
 * after and undoes it. See {@link MountWolfPacket} for why that ordering is guaranteed.
 *
 * <p>Eligibility is checked here only to avoid sending pointless packets and to keep a plain
 * right-click working on a wolf that cannot be ridden — the server re-checks everything.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class WolfMountClientEvents {

    private WolfMountClientEvents() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        if (!WolfMountModule.isModuleActive()) {
            return;
        }
        if (!(event.getTarget() instanceof Wolf wolf)) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!WolfMountKeybinds.isModifierDown()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator() || mc.player.isPassenger()) {
            return;
        }
        if (!WolfMountRules.canMount(wolf, mc.player)) {
            return;
        }

        event.setCanceled(true);
        PacketDistributor.sendToServer(new MountWolfPacket(wolf.getId()));
    }
}
