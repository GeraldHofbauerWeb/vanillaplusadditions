package net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.geraldhofbauer.vanillaplusadditions.modules.wolf_mount.WolfMountModule;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Shows the mount's body armor durability while riding, and collapses vanilla's mount health bar.
 *
 * <p>Why the armor and not the health: {@code battle_dogs} makes canine body armor absorb
 * <em>100 %</em> of incoming damage and drain one durability point per damage point, so the wolf's
 * health does not move at all until the armor breaks. The durability is the real health pool — and
 * when it runs out the armor breaks, {@code dismount_when_armor_removed} fires and the rider is
 * dropped mid-fight. Without a bar that lands with no warning whatsoever.
 *
 * <p>Vanilla meanwhile draws one heart per 2 HP capped at 30 hearts, so a 350 HP wolf fills three
 * rows that never visibly move — 30 px of screen spent on a constant. {@code compact_mount_health}
 * replaces them with a single ten-heart row showing the health as a fraction.
 *
 * <p>Nothing here needs a packet: body armor reaches the client as a full {@code ItemStack} through
 * {@code ClientboundSetEquipmentPacket}, damage component included.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class WolfMountArmorHud {

    private static final ResourceLocation HEART_CONTAINER =
            ResourceLocation.withDefaultNamespace("hud/heart/vehicle_container");
    private static final ResourceLocation HEART_FULL =
            ResourceLocation.withDefaultNamespace("hud/heart/vehicle_full");
    private static final ResourceLocation HEART_HALF =
            ResourceLocation.withDefaultNamespace("hud/heart/vehicle_half");
    private static final ResourceLocation ARMOR_EMPTY = ResourceLocation.withDefaultNamespace("hud/armor_empty");
    private static final ResourceLocation ARMOR_FULL = ResourceLocation.withDefaultNamespace("hud/armor_full");
    private static final ResourceLocation ARMOR_HALF = ResourceLocation.withDefaultNamespace("hud/armor_half");

    /** Icons per row, and the pixel geometry vanilla uses for both bars. */
    private static final int ICONS = 10;
    private static final int ICON_SIZE = 9;
    private static final int ICON_STEP = 8;
    private static final int ROW_HEIGHT = 10;

    private static final int COLOR_DEFAULT = 0xFFFFFF;
    private static final int COLOR_WARNING = 0xFF4040;
    private static final long PULSE_PERIOD_MS = 900L;

    /** One-shot latch for the low-armor warning, reset once the armor is healthy or the ride ends. */
    private static boolean armorWarningShown;

    private WolfMountArmorHud() {
    }

    /**
     * Replaces vanilla's mount health rows with a compact one plus the armor bar.
     *
     * <p>Only cancels when it actually takes over the health row. With {@code compact_mount_health}
     * off, vanilla keeps drawing its own rows and the armor bar is stacked on top in
     * {@link #onVehicleHealthPost}.
     *
     * @param event the pre-render event for the vehicle health layer
     */
    @SubscribeEvent
    public static void onVehicleHealthPre(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.VEHICLE_HEALTH)) {
            return;
        }
        Wolf mount = getMount();
        if (mount == null || !WolfMountModule.isCompactMountHealth()) {
            return;
        }
        event.setCanceled(true);

        Gui gui = Minecraft.getInstance().gui;
        GuiGraphics graphics = event.getGuiGraphics();
        int right = graphics.guiWidth() / 2 + 91;
        int y = graphics.guiHeight() - gui.rightHeight;

        RenderSystem.enableBlend();
        renderHealthRow(graphics, right, y, mount);
        gui.rightHeight += ROW_HEIGHT;
        renderArmorRow(graphics, right, y - ROW_HEIGHT, mount, gui);
        RenderSystem.disableBlend();
    }

    /**
     * Stacks the armor bar on top of vanilla's untouched mount health rows.
     *
     * @param event the post-render event for the vehicle health layer
     */
    @SubscribeEvent
    public static void onVehicleHealthPost(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.VEHICLE_HEALTH)) {
            return;
        }
        Wolf mount = getMount();
        if (mount == null || WolfMountModule.isCompactMountHealth()) {
            return;
        }
        Gui gui = Minecraft.getInstance().gui;
        GuiGraphics graphics = event.getGuiGraphics();

        RenderSystem.enableBlend();
        renderArmorRow(graphics, graphics.guiWidth() / 2 + 91,
                graphics.guiHeight() - gui.rightHeight, mount, gui);
        RenderSystem.disableBlend();
    }

    /**
     * The wolf the local player is riding, or null if the HUD has no business drawing.
     *
     * <p>Also the place the warning latch is cleared: dismounting has to re-arm it, otherwise a
     * second ride on a still-damaged mount would stay silent.
     */
    private static Wolf getMount() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (!WolfMountModule.isModuleActive() || player == null || mc.gui == null
                || !(player.getVehicle() instanceof Wolf wolf)) {
            armorWarningShown = false;
            return null;
        }
        return wolf;
    }

    /** Ten hearts showing health as a fraction, so a 350 HP mount fits one row. */
    private static void renderHealthRow(GuiGraphics graphics, int right, int y, Wolf mount) {
        int halves = fractionToHalves(mount.getHealth(), mount.getMaxHealth());
        for (int i = 0; i < ICONS; i++) {
            int x = right - i * ICON_STEP - ICON_SIZE;
            graphics.blitSprite(HEART_CONTAINER, x, y, ICON_SIZE, ICON_SIZE);
            if (i * 2 + 1 < halves) {
                graphics.blitSprite(HEART_FULL, x, y, ICON_SIZE, ICON_SIZE);
            } else if (i * 2 + 1 == halves) {
                graphics.blitSprite(HEART_HALF, x, y, ICON_SIZE, ICON_SIZE);
            }
        }
    }

    /**
     * Ten armor icons showing remaining durability, tinted per armor tier, pulsing red once the
     * armor is nearly gone. Draws nothing (and consumes no row) for an undamageable or absent
     * armor, so a wolf ridden with {@code require_body_armor} off gets no empty bar.
     */
    private static void renderArmorRow(GuiGraphics graphics, int right, int y, Wolf mount, Gui gui) {
        if (!WolfMountModule.isShowArmorBar()) {
            return;
        }
        ItemStack armor = mount.getBodyArmorItem();
        if (armor.isEmpty() || !armor.isDamageableItem() || armor.getMaxDamage() <= 0) {
            return;
        }
        float remaining = 1.0F - (float) armor.getDamageValue() / (float) armor.getMaxDamage();
        int halves = fractionToHalves(remaining, 1.0F);
        int color = armorColor(armor);

        double threshold = WolfMountModule.getArmorWarningThreshold();
        boolean low = threshold > 0.0D && remaining < threshold;
        if (low) {
            color = pulseTowardsWarning(color);
            warnOnce(mount);
        } else {
            armorWarningShown = false;
        }

        for (int i = 0; i < ICONS; i++) {
            graphics.blitSprite(ARMOR_EMPTY, armorIconX(right, i), y, ICON_SIZE, ICON_SIZE);
        }
        setColor(color);
        for (int i = 0; i < ICONS; i++) {
            int x = armorIconX(right, i);
            if (i * 2 + 1 < halves) {
                graphics.blitSprite(ARMOR_FULL, x, y, ICON_SIZE, ICON_SIZE);
            } else if (i * 2 + 1 == halves) {
                graphics.blitSprite(ARMOR_HALF, x, y, ICON_SIZE, ICON_SIZE);
            }
        }
        setColor(COLOR_DEFAULT);
        gui.rightHeight += ROW_HEIGHT;
    }

    /**
     * X position of armor icon {@code i}, filling <b>left to right</b>.
     *
     * <p>Deliberately the opposite of the heart row above it. Vanilla's mount hearts run
     * right-to-left ({@code l - i * 8 - 9}) but its armor bar runs left-to-right
     * ({@code x + i * 8}), and the shared {@code armor_half} sprite is filled on its <em>left</em>
     * half. Drawing armor with the heart geometry therefore points the half icon's filled side away
     * from the full icons next to it, which reads as a hole in the bar.
     */
    private static int armorIconX(int right, int i) {
        return right - (ICONS - 1 - i) * ICON_STEP - ICON_SIZE;
    }

    /**
     * Tint for the armor icons: an explicit dye wins, otherwise the tier is read off the item id.
     *
     * <p>Deliberately a name lookup rather than an {@code instanceof WolfArmorItem} test — this
     * module must keep working with {@code battle_dogs} absent, and the id heuristic gives any
     * third-party canine armor a sane colour for free (white, unless it names a known metal).
     */
    private static int armorColor(ItemStack armor) {
        DyedItemColor dyed = armor.get(DataComponents.DYED_COLOR);
        if (dyed != null) {
            return dyed.rgb() & 0xFFFFFF;
        }
        String id = BuiltInRegistries.ITEM.getKey(armor.getItem()).getPath();
        if (id.contains("netherite")) {
            return 0xB0A0AC;
        }
        if (id.contains("diamond")) {
            return 0x5AE8E0;
        }
        if (id.contains("gold")) {
            return 0xFFD24A;
        }
        return COLOR_DEFAULT;
    }

    /** Blends the tier colour towards red on a sine, so a nearly-broken bar catches the eye. */
    private static int pulseTowardsWarning(int color) {
        float phase = (float) (Util.getMillis() % PULSE_PERIOD_MS) / PULSE_PERIOD_MS;
        float amount = (Mth.sin(phase * Mth.TWO_PI) + 1.0F) * 0.5F;
        int r = Mth.lerpInt(amount, color >> 16 & 0xFF, COLOR_WARNING >> 16 & 0xFF);
        int g = Mth.lerpInt(amount, color >> 8 & 0xFF, COLOR_WARNING >> 8 & 0xFF);
        int b = Mth.lerpInt(amount, color & 0xFF, COLOR_WARNING & 0xFF);
        return r << 16 | g << 8 | b;
    }

    /** One action bar line per damage run, not one per frame. */
    private static void warnOnce(Wolf mount) {
        if (armorWarningShown) {
            return;
        }
        armorWarningShown = true;
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable(
                    "message.vanillaplusadditions.wolf_mount.armor_low", mount.getDisplayName()), true);
        }
    }

    /** Maps a value onto the 0–20 half-icon scale vanilla's bars are drawn in. */
    private static int fractionToHalves(float value, float max) {
        if (max <= 0.0F) {
            return 0;
        }
        float fraction = Mth.clamp(value / max, 0.0F, 1.0F);
        int halves = Mth.ceil(fraction * 20.0F);
        return value > 0.0F ? Math.max(halves, 1) : halves;
    }

    private static void setColor(int rgb) {
        RenderSystem.setShaderColor((rgb >> 16 & 0xFF) / 255.0F, (rgb >> 8 & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F, 1.0F);
    }
}
