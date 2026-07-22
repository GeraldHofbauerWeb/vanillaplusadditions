package net.geraldhofbauer.vanillaplusadditions.modules.end_conduit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.geraldhofbauer.vanillaplusadditions.modules.end_conduit.blockentity.EndConduitBlockEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ConduitRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Block entity renderer for the {@link EndConduitBlockEntity}. A near-verbatim copy of the vanilla
 * {@link ConduitRenderer#render}, retyped to our block entity — it bakes the vanilla-registered
 * {@code ModelLayers.CONDUIT_*} layers and reuses {@link ConduitRenderer}'s public sprite materials,
 * so the placed End Conduit looks and animates pixel-identically to a vanilla conduit.
 */
public class EndConduitBER implements BlockEntityRenderer<EndConduitBlockEntity> {

    private final ModelPart eye;
    private final ModelPart wind;
    private final ModelPart shell;
    private final ModelPart cage;
    private final BlockEntityRenderDispatcher renderer;

    public EndConduitBER(BlockEntityRendererProvider.Context context) {
        this.renderer = context.getBlockEntityRenderDispatcher();
        this.eye = context.bakeLayer(ModelLayers.CONDUIT_EYE);
        this.wind = context.bakeLayer(ModelLayers.CONDUIT_WIND);
        this.shell = context.bakeLayer(ModelLayers.CONDUIT_SHELL);
        this.cage = context.bakeLayer(ModelLayers.CONDUIT_CAGE);
    }

    @Override
    public void render(EndConduitBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        float time = (float) blockEntity.getTickCount() + partialTick;
        if (!blockEntity.isActive()) {
            float rot = blockEntity.getActiveRotation(0.0F);
            VertexConsumer shellBuffer = ConduitRenderer.SHELL_TEXTURE.buffer(bufferSource, RenderType::entitySolid);
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.mulPose(new Quaternionf().rotationY(rot * (float) (Math.PI / 180.0)));
            this.shell.render(poseStack, shellBuffer, packedLight, packedOverlay);
            poseStack.popPose();
        } else {
            float rot = blockEntity.getActiveRotation(partialTick) * (180.0F / (float) Math.PI);
            float bob = Mth.sin(time * 0.1F) / 2.0F + 0.5F;
            bob = bob * bob + bob;
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.3F + bob * 0.2F, 0.5F);
            Vector3f axis = new Vector3f(0.5F, 1.0F, 0.5F).normalize();
            poseStack.mulPose(new Quaternionf().rotationAxis(rot * (float) (Math.PI / 180.0), axis));
            this.cage.render(poseStack,
                    ConduitRenderer.ACTIVE_SHELL_TEXTURE.buffer(bufferSource, RenderType::entityCutoutNoCull),
                    packedLight, packedOverlay);
            poseStack.popPose();

            int windFrame = blockEntity.getTickCount() / 66 % 3;
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            if (windFrame == 1) {
                poseStack.mulPose(new Quaternionf().rotationX((float) (Math.PI / 2)));
            } else if (windFrame == 2) {
                poseStack.mulPose(new Quaternionf().rotationZ((float) (Math.PI / 2)));
            }
            VertexConsumer windBuffer =
                    (windFrame == 1 ? ConduitRenderer.VERTICAL_WIND_TEXTURE : ConduitRenderer.WIND_TEXTURE)
                            .buffer(bufferSource, RenderType::entityCutoutNoCull);
            this.wind.render(poseStack, windBuffer, packedLight, packedOverlay);
            poseStack.popPose();

            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.scale(0.875F, 0.875F, 0.875F);
            poseStack.mulPose(new Quaternionf().rotationXYZ((float) Math.PI, 0.0F, (float) Math.PI));
            this.wind.render(poseStack, windBuffer, packedLight, packedOverlay);
            poseStack.popPose();

            Camera camera = this.renderer.camera;
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.3F + bob * 0.2F, 0.5F);
            poseStack.scale(0.5F, 0.5F, 0.5F);
            float yaw = -camera.getYRot();
            poseStack.mulPose(new Quaternionf().rotationYXZ(yaw * (float) (Math.PI / 180.0),
                    camera.getXRot() * (float) (Math.PI / 180.0), (float) Math.PI));
            poseStack.scale(1.3333334F, 1.3333334F, 1.3333334F);
            this.eye.render(poseStack,
                    (blockEntity.isHunting() ? ConduitRenderer.OPEN_EYE_TEXTURE : ConduitRenderer.CLOSED_EYE_TEXTURE)
                            .buffer(bufferSource, RenderType::entityCutoutNoCull),
                    packedLight, packedOverlay);
            poseStack.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(EndConduitBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        return new AABB(pos.getX(), pos.getY() - 0.25, pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.25, pos.getZ() + 1.0);
    }
}
