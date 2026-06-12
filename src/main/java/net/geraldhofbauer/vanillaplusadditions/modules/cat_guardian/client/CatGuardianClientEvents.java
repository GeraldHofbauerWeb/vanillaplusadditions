package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.client;

import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.Module;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.AbstractCatBowlBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.OpenCatInventoryPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.RequestCatGlowPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.SyncCatInventoryPacket;
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

@EventBusSubscriber(modid = VanillaPlusAdditions.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class CatGuardianClientEvents {

    // Glow debounce state
    private static BlockPos lastGlowPos = null;
    private static long lastGlowSentTick = Long.MIN_VALUE;

    private CatGuardianClientEvents() { }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getTarget() instanceof Cat cat)) {
            return;
        }
        if (!Screen.hasControlDown()) {
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
        if (mc.player == null || !mc.player.getUUID().equals(cat.getOwnerUUID())) {
            return;
        }
        event.setCanceled(true);
        PacketDistributor.sendToServer(new OpenCatInventoryPacket(cat.getId()));
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

        // Check every second (20 ticks)
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

        // Resend if the bowl changed or ~⅔ of glow duration has elapsed to keep it refreshed
        boolean bowlChanged = !hitPos.equals(lastGlowPos);
        boolean timerElapsed = gameTime - lastGlowSentTick > 400; // 20 s
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

    private static CatGuardianModule getModule() {
        Module module = ModuleManager.getInstance().getModule("cat_guardian");
        return module instanceof CatGuardianModule m ? m : null;
    }
}
