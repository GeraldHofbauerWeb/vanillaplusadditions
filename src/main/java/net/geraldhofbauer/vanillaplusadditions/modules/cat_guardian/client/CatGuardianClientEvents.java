package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.client;

import net.geraldhofbauer.vanillaplusadditions.core.Module;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.AbstractCatBowlBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class CatGuardianClientEvents {

    private static BlockPos lastGlowPos = null;
    private static long lastGlowSentTick = Long.MIN_VALUE;

    // catEntityId → targetEntityId; populated by SyncCatTargetPacket
    static final Map<Integer, Integer> CAT_TARGET_MAP = new HashMap<>();

    // catEntityId → [xp, xpCap]; populated by SyncCatStatsPacket
    static final Map<Integer, int[]> CAT_XP_MAP = new HashMap<>();

    // catEntityId → path packet; populated by SyncCatPathPacket; empty arrays = no path
    static final Map<Integer,
            net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatPathPacket>
            CAT_PATH_MAP = new HashMap<>();

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

        if (!mc.player.getUUID().equals(cat.getOwnerUUID())) {
            return;
        }

        // Plain right-click → open cat inventory. Shift+right-click is left to Carry On.
        // Ctrl+right-click is left uncancelled too, so vanilla sit/stand stays reachable
        // (armor is removed by dragging it out of the inventory GUI, not via a click gesture).
        if (!Screen.hasShiftDown() && !Screen.hasControlDown()) {
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

        // Overlay toggle + keybind are now owned by the shared debug_overlay framework
        // (CatGuardianGogglesClientHandler reads DebugOverlayState.isEnabled()).
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

    public static void handleSyncCatStats(SyncCatStatsPacket packet) {
        CAT_XP_MAP.put(packet.catId(), new int[]{packet.xp(), packet.xpCap()});
    }

    public static void handleSyncCatTarget(SyncCatTargetPacket packet) {
        if (packet.targetEntityId() == SyncCatTargetPacket.NO_TARGET) {
            CAT_TARGET_MAP.remove(packet.catEntityId());
        } else {
            CAT_TARGET_MAP.put(packet.catEntityId(), packet.targetEntityId());
        }
    }

    public static void handleSyncCatPath(
            net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatPathPacket packet) {
        if (packet.nodeX().length == 0) {
            CAT_PATH_MAP.remove(packet.catEntityId());
        } else {
            CAT_PATH_MAP.put(packet.catEntityId(), packet);
        }
    }

    private static CatGuardianModule getModule() {
        Module module = ModuleManager.getInstance().getModule("cat_guardian");
        return module instanceof CatGuardianModule m ? m : null;
    }
}
