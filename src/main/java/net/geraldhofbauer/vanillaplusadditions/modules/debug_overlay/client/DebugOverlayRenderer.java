package net.geraldhofbauer.vanillaplusadditions.modules.debug_overlay.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * A pluggable debug overlay. The framework only invokes these methods while the master toggle
 * is on and the player wears goggles, so implementations don't need to re-check either.
 *
 * <p>For {@link #renderWorld}, the {@link PoseStack} is already translated by {@code -cameraPos},
 * so world coordinates can be used directly; the framework flushes the buffer source afterwards.</p>
 */
public interface DebugOverlayRenderer {

    /** Per-frame client tick (gather what to draw, manage timers). */
    default void clientTick(Minecraft mc) { }

    /** World-space rendering at {@code AFTER_TRANSLUCENT_BLOCKS}. PoseStack is camera-relative. */
    default void renderWorld(RenderLevelStageEvent event, PoseStack pose,
                             MultiBufferSource.BufferSource buffers, Vec3 cameraPos, float partialTick) { }

    /** Screen-space HUD rendering ({@code RenderGuiEvent.Post}). */
    default void renderHud(GuiGraphics graphics, Minecraft mc) { }
}
