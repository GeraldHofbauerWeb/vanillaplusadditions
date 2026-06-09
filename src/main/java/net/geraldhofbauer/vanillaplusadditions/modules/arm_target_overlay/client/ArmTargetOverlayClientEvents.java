package net.geraldhofbauer.vanillaplusadditions.modules.arm_target_overlay.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.Module;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.arm_target_overlay.ArmTargetOverlayModule;
import net.geraldhofbauer.vanillaplusadditions.modules.arm_target_overlay.config.ArmTargetOverlayConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;
import java.util.OptionalDouble;

@EventBusSubscriber(modid = VanillaPlusAdditions.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ArmTargetOverlayClientEvents {
    static final TagKey<Item> ARM_GOGGLES_TAG = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath(VanillaPlusAdditions.MODID, "arm_goggles"));

    private static final RenderType XRAY_LINES = RenderType.create(
            "arm_target_overlay_xray_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
    );

    private ArmTargetOverlayClientEvents() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        ArmTargetOverlayModule module = getModule();
        if (module == null || !module.isModuleEnabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        if (!isWearingGoggles(minecraft.player)) {
            return;
        }

        if (!(minecraft.hitResult instanceof BlockHitResult blockHit)) {
            return;
        }

        BlockPos hitPos = blockHit.getBlockPos();
        Level level = minecraft.level;
        if (!(level.getBlockEntity(hitPos) instanceof ArmBlockEntity armBe)) {
            return;
        }

        List<ArmInteractionPoint> inputs = ArmBlockEntityReflection.getInputs(armBe);
        List<ArmInteractionPoint> outputs = ArmBlockEntityReflection.getOutputs(armBe);
        if (inputs.isEmpty() && outputs.isEmpty()) {
            return;
        }

        ArmTargetOverlayConfig config = module.getConfig();
        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        VertexConsumer consumer = minecraft.renderBuffers().bufferSource().getBuffer(XRAY_LINES);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        for (ArmInteractionPoint point : inputs) {
            LevelRenderer.renderLineBox(poseStack, consumer, new AABB(point.getPos()),
                    config.getInputRed(), config.getInputGreen(),
                    config.getInputBlue(), config.getInputAlpha());
        }
        for (ArmInteractionPoint point : outputs) {
            LevelRenderer.renderLineBox(poseStack, consumer, new AABB(point.getPos()),
                    config.getOutputRed(), config.getOutputGreen(),
                    config.getOutputBlue(), config.getOutputAlpha());
        }

        poseStack.popPose();
        minecraft.renderBuffers().bufferSource().endBatch(XRAY_LINES);
    }

    private static boolean isWearingGoggles(Player player) {
        // Engineering Goggles in head slot or any registered Curios slot (via Create's predicate system)
        if (GogglesItem.isWearingGoggles(player)) {
            return true;
        }
        // Aviation Goggles or other goggles registered in the arm_goggles item tag (head slot)
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ARM_GOGGLES_TAG);
    }

    private static ArmTargetOverlayModule getModule() {
        Module module = ModuleManager.getInstance().getModule("arm_target_overlay");
        if (module instanceof ArmTargetOverlayModule armModule) {
            return armModule;
        }
        return null;
    }
}
