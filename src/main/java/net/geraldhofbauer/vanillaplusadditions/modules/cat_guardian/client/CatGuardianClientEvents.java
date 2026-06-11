package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.Module;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.AbstractCatBowlBlockEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.config.CatGuardianConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

@EventBusSubscriber(modid = VanillaPlusAdditions.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class CatGuardianClientEvents {

    private static final RenderType XRAY_LINES = RenderType.create(
            "cat_guardian_xray_lines",
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

    private CatGuardianClientEvents() { }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        CatGuardianModule module = getModule();
        if (module == null || !module.isModuleEnabled()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        // Only render when the player is looking at a cat bowl or feeding station
        if (!(mc.hitResult instanceof BlockHitResult blockHit)) {
            return;
        }
        BlockPos hitPos = blockHit.getBlockPos();
        if (!(mc.level.getBlockEntity(hitPos) instanceof AbstractCatBowlBlockEntity bowl)) {
            return;
        }

        List<UUID> catUUIDs = bowl.getAssociatedCats();
        if (catUUIDs.isEmpty()) {
            return;
        }

        CatGuardianConfig config = module.getConfig();
        float r = config.getOutlineRed();
        float g = config.getOutlineGreen();
        float b = config.getOutlineBlue();
        float a = config.getOutlineAlpha();

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(XRAY_LINES);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        // Search for cats within a large area around the bowl; UUIDs filter which ones to outline.
        // ClientLevel has no direct UUID→Entity map, so we query by class + predicate.
        List<UUID> uuidList = catUUIDs;
        AABB searchBox = new AABB(hitPos).inflate(192);
        for (Cat cat : mc.level.getEntitiesOfClass(Cat.class, searchBox,
                c -> uuidList.contains(c.getUUID()) && c.isAlive())) {
            LevelRenderer.renderLineBox(poseStack, consumer, cat.getBoundingBox(), r, g, b, a);
        }

        poseStack.popPose();
        mc.renderBuffers().bufferSource().endBatch(XRAY_LINES);
    }

    private static CatGuardianModule getModule() {
        Module module = ModuleManager.getInstance().getModule("cat_guardian");
        return module instanceof CatGuardianModule m ? m : null;
    }
}
