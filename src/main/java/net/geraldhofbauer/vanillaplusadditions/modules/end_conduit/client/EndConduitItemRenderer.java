package net.geraldhofbauer.vanillaplusadditions.modules.end_conduit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders the End Conduit <em>item</em> as the tinted 3D conduit shell — the same way vanilla renders
 * the conduit item (via {@code builtin/entity}), but with our {@link EndConduitTextures#SHELL} sprite,
 * so the inventory item matches the placed block. Wired up through
 * {@code IClientItemExtensions.getCustomRenderer()} in {@link EndConduitClientSetup}.
 */
public class EndConduitItemRenderer extends BlockEntityWithoutLevelRenderer {

    private ModelPart shell;

    public EndConduitItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (this.shell == null) {
            // Bake lazily: the entity model set is only populated after resource load.
            this.shell = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.CONDUIT_SHELL);
        }
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        VertexConsumer consumer = EndConduitTextures.SHELL.buffer(buffer, RenderType::entitySolid);
        this.shell.render(poseStack, consumer, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
