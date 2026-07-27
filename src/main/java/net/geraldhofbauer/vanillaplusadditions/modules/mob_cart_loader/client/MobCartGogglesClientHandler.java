package net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.client;

import net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client.GogglesUtil;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.blockentity.AbstractMobCartBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Draws a small stats panel next to the crosshair when the player wears Create's Engineer's Goggles
 * (or a tagged goggles item) and looks at a mob loader/unloader block: the tracked mob's icon + name,
 * and — while sneaking — its health. No mob → a compact "empty" line. Gated purely on
 * {@link GogglesUtil}, so without Create nothing shows and nothing breaks.
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class MobCartGogglesClientHandler {

    private static final ResourceLocation HEART_SPRITE =
            ResourceLocation.parse("minecraft:hud/heart/full");

    private MobCartGogglesClientHandler() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        if (!GogglesUtil.isWearingGoggles(mc.player)) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult blockHit)) {
            return;
        }
        BlockEntity be = mc.level.getBlockEntity(blockHit.getBlockPos());
        if (!(be instanceof AbstractMobCartBlockEntity mobCart)) {
            return;
        }

        renderPanel(event.getGuiGraphics(), mc, mobCart, mc.player.isShiftKeyDown());
    }

    private static void renderPanel(GuiGraphics g, Minecraft mc, AbstractMobCartBlockEntity be,
                                    boolean sneaking) {
        Font font = mc.font;
        EntityType<?> type = be.getDisplayType();

        Component nameText = type != null
                ? type.getDescription()
                : Component.translatable("gui.vanillaplusadditions.mob_cart_loader.empty");
        ItemStack icon = type != null ? spawnEggFor(type) : ItemStack.EMPTY;
        boolean showHealth = sneaking && type != null;
        String healthStr = showHealth
                ? Math.round(be.getMobHealth()) + "/" + Math.round(be.getMobMaxHealth())
                : null;

        int iconSize = 14;
        float itemScale = iconSize / 16f;
        int iconGap = iconSize + 3;
        int rowH = iconSize + 2;
        int textOff = (rowH - font.lineHeight) / 2;

        int nameWidth = iconGap + font.width(nameText);
        int healthWidth = showHealth ? iconGap + font.width(healthStr) : 0;
        int panelW = Math.max(nameWidth, healthWidth);
        int rows = showHealth ? 2 : 1;
        int contentH = rowH * rows;
        int pad = 4;

        int x = g.guiWidth() / 2 + 10;
        int y = g.guiHeight() / 2 + 10;

        // Background + border (tooltip style, green-tinted for this module).
        g.fillGradient(x - pad - 1, y - pad - 1, x + panelW + pad + 1, y + contentH + pad + 1,
                0x80100010, 0x80100010);
        g.fillGradient(x - pad - 1, y - pad - 1, x + panelW + pad + 1, y - pad, 0xA030C050, 0xA030C050);
        g.fillGradient(x - pad - 1, y + contentH + pad, x + panelW + pad + 1, y + contentH + pad + 1,
                0xA0186028, 0xA0186028);
        g.fillGradient(x - pad - 1, y - pad, x - pad, y + contentH + pad, 0xA030C050, 0xA0186028);
        g.fillGradient(x + panelW + pad, y - pad, x + panelW + pad + 1, y + contentH + pad,
                0xA030C050, 0xA0186028);

        // Row 0: mob icon + name.
        if (!icon.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(itemScale, itemScale, 1f);
            g.renderItem(icon, 0, 0);
            g.pose().popPose();
        }
        g.drawString(font, nameText, x + iconGap, y + textOff, 0xFFFFFFFF, false);

        // Row 1 (sneaking only): heart + health.
        if (showHealth) {
            int row1Y = y + rowH;
            g.blitSprite(HEART_SPRITE, x, row1Y, iconSize, iconSize);
            g.drawString(font, healthStr, x + iconGap, row1Y + textOff, 0xFFCC2222, false);
        }
    }

    private static ItemStack spawnEggFor(EntityType<?> type) {
        SpawnEggItem egg = SpawnEggItem.byId(type);
        return egg != null ? new ItemStack(egg) : ItemStack.EMPTY;
    }
}
