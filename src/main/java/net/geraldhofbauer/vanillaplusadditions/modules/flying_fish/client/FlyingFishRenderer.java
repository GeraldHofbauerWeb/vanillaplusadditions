package net.geraldhofbauer.vanillaplusadditions.modules.flying_fish.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.geraldhofbauer.vanillaplusadditions.modules.flying_fish.entity.FlyingFishEntity;
import net.minecraft.client.model.CodModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class FlyingFishRenderer extends MobRenderer<FlyingFishEntity, CodModel<FlyingFishEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "vanillaplusadditions",
            "textures/entity/fish/flying_fish.png"
    );

    public FlyingFishRenderer(EntityRendererProvider.Context context) {
        super(context, new CodModel<>(context.bakeLayer(ModelLayers.COD)), 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(FlyingFishEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void setupRotations(
            FlyingFishEntity entity,
            PoseStack poseStack,
            float bob,
            float yBodyRot,
            float partialTick,
            float scale
    ) {
        super.setupRotations(entity, poseStack, bob, yBodyRot, partialTick, scale);
        float wiggle = 4.3F * Mth.sin(0.6F * bob);
        poseStack.mulPose(Axis.YP.rotationDegrees(wiggle));
        if (!entity.isInWater()) {
            poseStack.translate(0.1F, 0.1F, -0.1F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        }
    }
}

