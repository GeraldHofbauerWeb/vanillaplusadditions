package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.client;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.Module;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.AbstractCatBowlBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.OpenCatInventoryPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.RequestCatGlowPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatInventoryPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatTargetPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.phys.BlockHitResult;
import java.util.HashMap;
import java.util.Map;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = VanillaPlusAdditions.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class CatGuardianClientEvents {

    private static BlockPos lastGlowPos = null;
    private static long lastGlowSentTick = Long.MIN_VALUE;

    // catEntityId → targetEntityId; populated by SyncCatTargetPacket
    static final Map<Integer, Integer> CAT_TARGET_MAP = new HashMap<>();

    private CatGuardianClientEvents() { }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getTarget() instanceof Cat cat)) {
            return;
        }
        if (!cat.isTame()) {
            return;
        }

        CatGuardianModule module = getModule();
        if (module == null || !module.isModuleEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // Ctrl+click → open cat inventory
        if (Screen.hasControlDown() && mc.player.getUUID().equals(cat.getOwnerUUID())) {
            event.setCanceled(true);
            PacketDistributor.sendToServer(new OpenCatInventoryPacket(cat.getId()));
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        CatGuardianModule module = getModule();
        if (module == null || !module.isModuleEnabled()) {
            return;
        }

        // Overlay toggle keybind (rebindable in Controls; default numpad +)
        while (CatGuardianClientSetup.TOGGLE_OVERLAY.consumeClick()) {
            boolean enabled = CatGuardianGogglesClientHandler.toggleOverlays();
            mc.player.displayClientMessage(Component.translatable(enabled
                    ? "message.vanillaplusadditions.cat_guardian.overlay_on"
                    : "message.vanillaplusadditions.cat_guardian.overlay_off"), true);
        }

        CatGuardianGogglesClientHandler.onClientTick(mc);

        long gameTime = mc.level.getGameTime();
        if (gameTime % 20 != 0) {
            return;
        }

        if (!(mc.hitResult instanceof BlockHitResult blockHit)) {
            return;
        }
        BlockPos hitPos = blockHit.getBlockPos();
        if (!(mc.level.getBlockEntity(hitPos) instanceof AbstractCatBowlBlockEntity)) {
            return;
        }

        boolean bowlChanged = !hitPos.equals(lastGlowPos);
        boolean timerElapsed = gameTime - lastGlowSentTick > 400;
        if (!bowlChanged && !timerElapsed) {
            return;
        }

        lastGlowPos = hitPos;
        lastGlowSentTick = gameTime;
        PacketDistributor.sendToServer(new RequestCatGlowPacket(hitPos));
    }

    public static void handleSyncCatInventory(SyncCatInventoryPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        net.minecraft.world.entity.Entity ent = mc.level.getEntity(packet.entityId());
        if (!(ent instanceof Cat cat)) {
            return;
        }
        cat.getData(CatGuardianModule.CAT_INVENTORY.get()).setArmor(packet.armorStack());
    }

    public static void handleSyncCatTarget(SyncCatTargetPacket packet) {
        if (packet.targetEntityId() == SyncCatTargetPacket.NO_TARGET) {
            CAT_TARGET_MAP.remove(packet.catEntityId());
        } else {
            CAT_TARGET_MAP.put(packet.catEntityId(), packet.targetEntityId());
        }
    }

    private static CatGuardianModule getModule() {
        Module module = ModuleManager.getInstance().getModule("cat_guardian");
        return module instanceof CatGuardianModule m ? m : null;
    }
}
