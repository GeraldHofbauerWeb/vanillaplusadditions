package net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.geraldhofbauer.vanillaplusadditions.modules.mob_cart_loader.blockentity.AbstractMobCartBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Renders a small, slowly spinning model of the block entity's tracked mob hovering above the block —
 * the same idea as the vanilla monster spawner's mini-mob. The displayed mob (pen candidate for the
 * loader, cart passenger for the unloader) is synced from the server; a null mob renders nothing.
 */
public class MobCartBER implements BlockEntityRenderer<AbstractMobCartBlockEntity> {

    /** Degrees of spin per game tick. */
    private static final float SPIN_SPEED = 3.0f;

    public MobCartBER(BlockEntityRendererProvider.Context context) {
        // No baked resources needed; the entity render dispatcher is fetched per-frame.
    }

    @Override
    public void render(AbstractMobCartBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Entity display = blockEntity.getOrCreateDisplayEntity();
        if (display == null) {
            return;
        }

        Level level = blockEntity.getLevel();
        long gameTime = level != null ? level.getGameTime() : 0L;
        float spin = (gameTime + partialTick) * SPIN_SPEED;

        float bbHeight = Math.max(0.1f, display.getBbHeight());
        float bbWidth = Math.max(0.1f, display.getBbWidth());
        float maxDim = Math.max(1.0f, Math.max(bbHeight, bbWidth));
        float scale = 0.5f / maxDim;

        // Render the mob inside the block frame (centred), slowly spinning — visible through the
        // open input/output windows.
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        poseStack.translate(0.0, -bbHeight / 2.0, 0.0);

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        dispatcher.render(display, 0.0, 0.0, 0.0, 0.0f, partialTick, poseStack, bufferSource, packedLight);
        dispatcher.setRenderShadow(true);

        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(AbstractMobCartBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        // Generous box so the hovering mob is never culled.
        return new AABB(pos).inflate(1.0).expandTowards(0.0, 1.5, 0.0);
    }
}
