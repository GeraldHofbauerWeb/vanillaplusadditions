package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.client;

import net.geraldhofbauer.vanillaplusadditions.core.Module;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.AbstractCatBowlBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.network.*;
import net.minecraft.client.Minecraft;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class CatGuardianClientEvents {

    // Client-side-only glow: catEntityId → game tick when the local glow expires. The glow flag
    // is re-asserted every tick (server entity-data resyncs would otherwise clear it) and is
    // never sent to the server — other players see nothing.
    private static final Map<Integer, Long> GLOW_EXPIRY = new HashMap<>();

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

        // Modifier (default Ctrl) + right-click → open cat inventory. A plain right-click falls
        // through to vanilla (sit/stand toggle); Shift+right-click stays with Carry On. The
        // modifier is a rebindable keybind (see CatGuardianKeybinds). Armor is removed by dragging
        // it out of the inventory GUI, not via a click gesture.
        if (CatGuardianKeybinds.isModifierDown()) {
            event.setCanceled(true);
            PacketDistributor.sendToServer(new OpenCatInventoryPacket(cat.getId()));
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            GLOW_EXPIRY.clear();
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

        // Looking at a bowl/station refreshes the local glow on the player's OWN associated
        // cats. Purely client-side — replaces the old server-side GLOWING effect that lit the
        // cats up for every player nearby. Associated UUIDs come from the block entity (BE data
        // is synced to clients); ownership from the vanilla-synched owner UUID.
        if (gameTime % 20 == 0
                && mc.hitResult instanceof BlockHitResult blockHit
                && mc.level.getBlockEntity(blockHit.getBlockPos()) instanceof AbstractCatBowlBlockEntity bowl) {
            BlockPos bowlPos = blockHit.getBlockPos();
            long expiry = gameTime + CatGuardianModule.getGlowDurationTicks();
            Set<java.util.UUID> associated = new HashSet<>(bowl.getAssociatedCats());
            double range = CatGuardianModule.getGuardRadius() + 16.0;
            for (Cat cat : mc.level.getEntitiesOfClass(Cat.class,
                    new net.minecraft.world.phys.AABB(bowlPos).inflate(range),
                    c -> associated.contains(c.getUUID())
                            && c.isTame() && mc.player.getUUID().equals(c.getOwnerUUID()))) {
                GLOW_EXPIRY.put(cat.getId(), expiry);
            }
        }

        tickLocalGlow(mc, gameTime);
    }

    private static void tickLocalGlow(Minecraft mc, long gameTime) {
        if (GLOW_EXPIRY.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Integer, Long>> iter = GLOW_EXPIRY.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<Integer, Long> entry = iter.next();
            net.minecraft.world.entity.Entity entity = mc.level.getEntity(entry.getKey());
            if (entity == null || !entity.isAlive() || entry.getValue() < gameTime) {
                // Only clear the flag if no real (server-side) glowing effect is active.
                if (entity instanceof Cat cat && !cat.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)) {
                    setLocalGlow(cat, false);
                }
                iter.remove();
                continue;
            }
            setLocalGlow(entity, true);
        }
    }

    /**
     * Sets the glowing shared flag (bit 6) directly. {@code Entity.setGlowingTag(true)} is a
     * client-side no-op: it writes {@code setSharedFlag(6, isCurrentlyGlowing())}, and
     * {@code isCurrentlyGlowing()} reads exactly that flag on the client — so the outline never
     * appears. Going through the invoker mixin flips the bit the renderer actually checks.
     */
    private static void setLocalGlow(net.minecraft.world.entity.Entity entity, boolean glow) {
        ((net.geraldhofbauer.vanillaplusadditions.mixin.core.EntitySharedFlagInvoker) entity)
                .callSetSharedFlag(6, glow);
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
