package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.AxolotlGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.blockentity.AxolotlInventoryData;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.item.AxolotlArmorItem;
import net.minecraft.client.model.AxolotlModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.EnumMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class AxolotlArmorLayer extends RenderLayer<Axolotl, AxolotlModel<Axolotl>> {

    private static final Map<AxolotlArmorItem.Tier, ResourceLocation> TEXTURES;

    static {
        TEXTURES = new EnumMap<>(AxolotlArmorItem.Tier.class);
        for (AxolotlArmorItem.Tier tier : AxolotlArmorItem.Tier.values()) {
            TEXTURES.put(tier, ResourceLocation.fromNamespaceAndPath(
                    VanillaPlusAdditions.MODID,
                    "textures/entity/axolotl_armor/axolotl_armor_" + tier.name().toLowerCase() + ".png"));
        }
    }

    public AxolotlArmorLayer(RenderLayerParent<Axolotl, AxolotlModel<Axolotl>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       Axolotl axolotl, float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (axolotl.isInvisible()) {
            return;
        }

        AxolotlInventoryData invData = axolotl.getData(AxolotlGuardianModule.AXOLOTL_INVENTORY.get());
        ItemStack armorStack = invData.getArmor();
        if (!(armorStack.getItem() instanceof AxolotlArmorItem axolotlArmor)) {
            return;
        }

        ResourceLocation texture = TEXTURES.get(axolotlArmor.getTier());
        coloredCutoutModelCopyLayerRender(getParentModel(), getParentModel(), texture,
                poseStack, buffer, packedLight, axolotl,
                limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, partialTick,
                -1);
    }
}
