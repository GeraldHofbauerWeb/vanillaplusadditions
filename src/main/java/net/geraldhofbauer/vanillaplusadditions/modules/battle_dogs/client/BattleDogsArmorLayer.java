package net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.geraldhofbauer.vanillaplusadditions.modules.battle_dogs.item.WolfArmorItem;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Crackiness;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class BattleDogsArmorLayer extends RenderLayer<Wolf, WolfModel<Wolf>> {

    private static final Map<Crackiness.Level, ResourceLocation> CRACK_TEXTURES = Map.of(
            Crackiness.Level.LOW,
            ResourceLocation.withDefaultNamespace("textures/entity/wolf/wolf_armor_crackiness_low.png"),
            Crackiness.Level.MEDIUM,
            ResourceLocation.withDefaultNamespace("textures/entity/wolf/wolf_armor_crackiness_medium.png"),
            Crackiness.Level.HIGH,
            ResourceLocation.withDefaultNamespace("textures/entity/wolf/wolf_armor_crackiness_high.png")
    );

    private final WolfModel<Wolf> model;

    public BattleDogsArmorLayer(RenderLayerParent<Wolf, WolfModel<Wolf>> parent, EntityModelSet models) {
        super(parent);
        this.model = new WolfModel<>(models.bakeLayer(ModelLayers.WOLF_ARMOR));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       Wolf wolf, float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (wolf.isInvisible()) {
            return;
        }

        ItemStack stack = wolf.getBodyArmorItem();
        if (!(stack.getItem() instanceof WolfArmorItem wolfArmor)) {
            return;
        }

        this.getParentModel().copyPropertiesTo(this.model);
        this.model.prepareMobModel(wolf, limbSwing, limbSwingAmount, partialTick);
        this.model.setupAnim(wolf, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(wolfArmor.getTexture()));
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY);

        Crackiness.Level crackLevel = Crackiness.WOLF_ARMOR.byDamage(stack);
        if (crackLevel != Crackiness.Level.NONE) {
            ResourceLocation crackTexture = CRACK_TEXTURES.get(crackLevel);
            VertexConsumer crackConsumer = buffer.getBuffer(RenderType.entityTranslucent(crackTexture));
            this.model.renderToBuffer(poseStack, crackConsumer, packedLight, OverlayTexture.NO_OVERLAY);
        }
    }
}
