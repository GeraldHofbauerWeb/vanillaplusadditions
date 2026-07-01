package net.geraldhofbauer.vanillaplusadditions.modules.end_oxygen.client;

import com.simibubi.create.content.equipment.armor.BacktankUtil;
import net.geraldhofbauer.vanillaplusadditions.core.Module;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.end_oxygen.EndOxygenModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

import java.util.List;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class EndOxygenClientEvents {
    private static final ResourceLocation AIR_LEVEL_LAYER = ResourceLocation.withDefaultNamespace("air_level");

    private EndOxygenClientEvents() {
    }

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        EndOxygenModule module = getModule();
        if (module == null || !module.isModuleEnabled()) {
            return;
        }

        if (event.getName().equals(AIR_LEVEL_LAYER)) {
            Player player = Minecraft.getInstance().player;
            if (player != null && player.level().dimension() == Level.END && !player.isCreative() && !player.isSpectator()) {
                // Force the air bubbles layer to render by making it think air is not full.
                if (player.getAirSupply() >= player.getMaxAirSupply()) {
                    player.setAirSupply(player.getMaxAirSupply() - 1);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGuiLayerPost(RenderGuiLayerEvent.Post event) {
        EndOxygenModule module = getModule();
        if (module == null || !module.isModuleEnabled()) {
            return;
        }

        // After vanilla air bubbles, render Create's backtank overlay next to the hotbar.
        if (event.getName().equals(AIR_LEVEL_LAYER)) {
            renderBacktankOverlay(module, event.getGuiGraphics(), event.getPartialTick());
        }
    }

    private static void renderBacktankOverlay(EndOxygenModule module, GuiGraphics guiGraphics, DeltaTracker partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || player.level().dimension() != Level.END || player.isCreative() || player.isSpectator()) {
            return;
        }

        List<ItemStack> backtanks = BacktankUtil.getAllWithAir(player);
        if (backtanks.isEmpty()) {
            return;
        }

        ItemStack backtank = backtanks.get(0);
        int air = BacktankUtil.getAir(backtank);

        int x = guiGraphics.guiWidth() / 2 + 90;
        int y = guiGraphics.guiHeight() - 53;
        if (backtank.has(DataComponents.FIRE_RESISTANT)) {
            y += 9;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.renderItem(backtank, 0, 0);

        int ticksLeft = Math.max(0, air - 1);
        int depletionRate = module.getConfig().getBacktankDepletionRate();
        int totalSeconds;
        if (depletionRate > 0) {
            float tickRate = player.level().tickRateManager().tickrate();
            totalSeconds = (int) (((long) ticksLeft * depletionRate) / tickRate);
        } else {
            totalSeconds = 5999;
        }

        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        if (minutes > 99) {
            minutes = 99;
            seconds = 59;
        }

        String timeLeft = String.format("%02d:%02d", minutes, seconds);
        int color = (air < 60 && air % 2 == 0) ? 0xFF0000 : 0xFFFFFF;
        guiGraphics.drawString(mc.font, timeLeft, 16, 5, color);
        guiGraphics.pose().popPose();
    }

    private static EndOxygenModule getModule() {
        Module module = ModuleManager.getInstance().getModule("end_oxygen");
        if (module instanceof EndOxygenModule endOxygenModule) {
            return endOxygenModule;
        }
        return null;
    }
}

