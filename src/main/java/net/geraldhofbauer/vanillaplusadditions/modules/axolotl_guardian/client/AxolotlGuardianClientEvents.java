package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.client;

import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.AxolotlGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.blockentity.AbstractAxolotlBowlBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network.OpenAxolotlInventoryPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network.SyncAxolotlInventoryPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network.SyncAxolotlOwnerPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network.SyncAxolotlPathPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network.SyncAxolotlStatsPacket;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.network.SyncAxolotlTargetPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.phys.AABB;
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
public final class AxolotlGuardianClientEvents {

    // axolotlEntityId → targetEntityId; populated by SyncAxolotlTargetPacket
    static final Map<Integer, Integer> AXOLOTL_TARGET_MAP = new HashMap<>();

    // axolotlEntityId → [xp, xpCap]; populated by SyncAxolotlStatsPacket
    static final Map<Integer, int[]> AXOLOTL_XP_MAP = new HashMap<>();

    // axolotlEntityId → path packet; populated by SyncAxolotlPathPacket; empty arrays = no path
    static final Map<Integer, SyncAxolotlPathPacket> AXOLOTL_PATH_MAP = new HashMap<>();

    // Client-side-only glow: axolotlEntityId → game tick when the local glow expires. The glow
    // flag is re-asserted every tick (server entity-data resyncs would otherwise clear it) and
    // is never sent to the server — other players see nothing.
    private static final Map<Integer, Long> GLOW_EXPIRY = new HashMap<>();

    private AxolotlGuardianClientEvents() { }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getTarget() instanceof Axolotl axolotl)) {
            return;
        }
        if (!AxolotlGuardianModule.isModuleActive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // Ownership is mirrored into the client-side attachment via SyncAxolotlOwnerPacket.
        if (!AxolotlGuardianModule.isOwnedBy(axolotl, mc.player.getUUID())) {
            return;
        }

        // Modifier (default Ctrl) + right-click → open axolotl inventory. A plain right-click
        // falls through to vanilla. Armor is removed by dragging it out of the inventory GUI.
        if (AxolotlGuardianKeybinds.isModifierDown()) {
            event.setCanceled(true);
            PacketDistributor.sendToServer(new OpenAxolotlInventoryPacket(axolotl.getId()));
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            GLOW_EXPIRY.clear();
            return;
        }

        if (!AxolotlGuardianModule.isModuleActive()) {
            return;
        }

        AxolotlGuardianGogglesClientHandler.onClientTick(mc);

        long gameTime = mc.level.getGameTime();

        // Looking at a bowl/station refreshes the local glow on the player's OWN associated
        // axolotls. Purely client-side — replaces the old server-side GLOWING effect that lit
        // the animals up for every player nearby.
        if (gameTime % 20 == 0
                && mc.hitResult instanceof BlockHitResult blockHit
                && mc.level.getBlockEntity(blockHit.getBlockPos()) instanceof AbstractAxolotlBowlBlockEntity bowl) {
            BlockPos bowlPos = blockHit.getBlockPos();
            long expiry = gameTime + AxolotlGuardianModule.getGlowDurationTicks();
            Set<java.util.UUID> associated = new HashSet<>(bowl.getAssociatedAxolotls());
            double range = AxolotlGuardianModule.getGuardRadius() + 16.0;
            for (Axolotl axolotl : mc.level.getEntitiesOfClass(Axolotl.class,
                    new AABB(bowlPos).inflate(range),
                    a -> associated.contains(a.getUUID())
                            && AxolotlGuardianModule.isOwnedBy(a, mc.player.getUUID()))) {
                GLOW_EXPIRY.put(axolotl.getId(), expiry);
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
            Entity entity = mc.level.getEntity(entry.getKey());
            if (entity == null || !entity.isAlive() || entry.getValue() < gameTime) {
                // Only clear the flag if no real (server-side) glowing effect is active.
                if (entity != null && entity instanceof Axolotl axolotl
                        && !axolotl.hasEffect(MobEffects.GLOWING)) {
                    setLocalGlow(axolotl, false);
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
    private static void setLocalGlow(Entity entity, boolean glow) {
        ((net.geraldhofbauer.vanillaplusadditions.mixin.core.EntitySharedFlagInvoker) entity)
                .callSetSharedFlag(6, glow);
    }

    public static void handleSyncAxolotlInventory(SyncAxolotlInventoryPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity ent = mc.level.getEntity(packet.entityId());
        if (!(ent instanceof Axolotl axolotl)) {
            return;
        }
        axolotl.getData(AxolotlGuardianModule.AXOLOTL_INVENTORY.get()).setArmor(packet.armorStack());
    }

    public static void handleSyncAxolotlOwner(SyncAxolotlOwnerPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity ent = mc.level.getEntity(packet.entityId());
        if (!(ent instanceof Axolotl axolotl)) {
            return;
        }
        // Mirror into the client-side attachments so ownership checks work on both sides.
        axolotl.setData(AxolotlGuardianModule.AXOLOTL_OWNER.get(), packet.owner());
        axolotl.setData(AxolotlGuardianModule.AXOLOTL_BOWL_POS.get(), packet.bowlPos());
    }

    public static void handleSyncAxolotlStats(SyncAxolotlStatsPacket packet) {
        AXOLOTL_XP_MAP.put(packet.axolotlId(), new int[]{packet.xp(), packet.xpCap()});
    }

    public static void handleSyncAxolotlTarget(SyncAxolotlTargetPacket packet) {
        if (packet.targetEntityId() == SyncAxolotlTargetPacket.NO_TARGET) {
            AXOLOTL_TARGET_MAP.remove(packet.axolotlEntityId());
        } else {
            AXOLOTL_TARGET_MAP.put(packet.axolotlEntityId(), packet.targetEntityId());
        }
    }

    public static void handleSyncAxolotlPath(SyncAxolotlPathPacket packet) {
        if (packet.nodeX().length == 0) {
            AXOLOTL_PATH_MAP.remove(packet.axolotlEntityId());
        } else {
            AXOLOTL_PATH_MAP.put(packet.axolotlEntityId(), packet);
        }
    }
}
