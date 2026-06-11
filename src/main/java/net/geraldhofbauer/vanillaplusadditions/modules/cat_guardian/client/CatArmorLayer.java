package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.CatInventoryData;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.item.CatArmorItem;
import net.minecraft.client.model.CatModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.EnumMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class CatArmorLayer extends RenderLayer<Cat, CatModel<Cat>> {

    private static final Map<CatArmorItem.Tier, ResourceLocation> TEXTURES;

    static {
        TEXTURES = new EnumMap<>(CatArmorItem.Tier.class);
        for (CatArmorItem.Tier tier : CatArmorItem.Tier.values()) {
            TEXTURES.put(tier, ResourceLocation.fromNamespaceAndPath(
                    VanillaPlusAdditions.MODID,
                    "textures/entity/cat_armor_" + tier.name().toLowerCase() + ".png"));
        }
    }

    public CatArmorLayer(RenderLayerParent<Cat, CatModel<Cat>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       Cat cat, float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (cat.isInvisible()) {
            return;
        }

        CatInventoryData invData = cat.getData(CatGuardianModule.CAT_INVENTORY.get());
        ItemStack armorStack = invData.getArmor();
        if (!(armorStack.getItem() instanceof CatArmorItem catArmor)) {
            return;
        }

        ResourceLocation texture = TEXTURES.get(catArmor.getTier());
        coloredCutoutModelCopyLayerRender(getParentModel(), getParentModel(), texture,
                poseStack, buffer, packedLight, cat,
                limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, partialTick,
                -1);
    }
}
