package net.geraldhofbauer.vanillaplusadditions.modules.block_glow.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.Module;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.block_glow.BlockGlowModule;
import net.geraldhofbauer.vanillaplusadditions.modules.block_glow.BlockGlowModule.BlockGlowState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.OptionalDouble;

@EventBusSubscriber(modid = VanillaPlusAdditions.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class BlockGlowClientEvents {
    private static final RenderType XRAY_LINES = RenderType.create(
            "blockglow_xray_lines",
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

    private static final SuggestionProvider<CommandSourceStack> BLOCK_TYPE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(
                    BuiltInRegistries.BLOCK.keySet().stream()
                            .filter(blockId -> BuiltInRegistries.BLOCK.get(blockId) != Blocks.AIR),
                    builder
            );

    private BlockGlowClientEvents() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        BlockGlowModule module = getModule();
        if (module == null || !module.isModuleEnabled()) {
            return;
        }

        event.getDispatcher().register(
                Commands.literal("blockglow")
                        .then(Commands.literal("clear")
                                .executes(BlockGlowClientEvents::executeClear)
                        )
                        .then(Commands.argument("block_id", ResourceLocationArgument.id())
                                .suggests(BLOCK_TYPE_SUGGESTIONS)
                                .executes(context -> executeEnable(context,
                                        module.getConfig().getDefaultRadius(),
                                        module.getConfig().getDefaultDurationSeconds()))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                                        .executes(context -> executeEnable(context,
                                                IntegerArgumentType.getInteger(context, "radius"),
                                                module.getConfig().getDefaultDurationSeconds()))
                                        .then(Commands.argument("duration_seconds", IntegerArgumentType.integer(0))
                                                .executes(context -> executeEnable(context,
                                                        IntegerArgumentType.getInteger(context, "radius"),
                                                        IntegerArgumentType.getInteger(context, "duration_seconds")))
                                        )
                                )
                        )
        );
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        BlockGlowModule module = getModule();
        if (module == null || !module.isModuleEnabled()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        BlockGlowState state = module.getClientState();
        if (!state.enabled()) {
            return;
        }

        long gameTime = minecraft.level.getGameTime();
        if (state.isExpired(gameTime)) {
            module.clearClientState();
            return;
        }

        Block targetBlock = BuiltInRegistries.BLOCK.get(state.blockId());
        if (targetBlock == Blocks.AIR) {
            return;
        }

        int radius = Math.min(state.radius(), module.getConfig().getMaxRadius());
        int maxHighlights = module.getConfig().getMaxHighlightsPerFrame();

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        VertexConsumer consumer = minecraft.renderBuffers().bufferSource().getBuffer(XRAY_LINES);

        BlockPos center = minecraft.player.blockPosition();
        MutableBlockPos cursor = new MutableBlockPos();
        String selectionMode = module.getConfig().getSelectionMode();
        if ("scan_order".equals(selectionMode)) {
            renderScanOrderHighlights(minecraft, module, targetBlock, center, cursor, radius, maxHighlights,
                    poseStack, cameraPos, consumer);
        } else {
            renderNearestHighlights(minecraft, module, targetBlock, center, cursor, radius, maxHighlights,
                    poseStack, cameraPos, consumer);
        }

        minecraft.renderBuffers().bufferSource().endBatch(XRAY_LINES);
    }

    private static void renderNearestHighlights(Minecraft minecraft, BlockGlowModule module, Block targetBlock,
            BlockPos center, MutableBlockPos cursor, int radius, int maxHighlights, PoseStack poseStack,
            Vec3 cameraPos, VertexConsumer consumer) {
        PriorityQueue<HighlightCandidate> nearestCandidates = new PriorityQueue<>(
                Comparator.comparingDouble(HighlightCandidate::distanceSquared).reversed()
        );

        scanForCandidates(minecraft, targetBlock, center, cursor, radius, pos -> {
            double distanceSquared = pos.distSqr(center);
            if (nearestCandidates.size() < maxHighlights) {
                nearestCandidates.offer(new HighlightCandidate(pos.immutable(), distanceSquared));
            } else {
                HighlightCandidate farthest = nearestCandidates.peek();
                if (farthest != null && distanceSquared < farthest.distanceSquared()) {
                    nearestCandidates.poll();
                    nearestCandidates.offer(new HighlightCandidate(pos.immutable(), distanceSquared));
                }
            }
        });

        List<HighlightCandidate> sortedCandidates = new ArrayList<>(nearestCandidates);
        sortedCandidates.sort(Comparator.comparingDouble(HighlightCandidate::distanceSquared));
        renderCandidates(module, poseStack, cameraPos, consumer, sortedCandidates);
    }

    private static void renderScanOrderHighlights(Minecraft minecraft, BlockGlowModule module, Block targetBlock,
            BlockPos center, MutableBlockPos cursor, int radius, int maxHighlights, PoseStack poseStack,
            Vec3 cameraPos, VertexConsumer consumer) {
        List<HighlightCandidate> candidates = new ArrayList<>(maxHighlights);

        scanForCandidates(minecraft, targetBlock, center, cursor, radius, pos -> {
            if (candidates.size() >= maxHighlights) {
                return;
            }

            candidates.add(new HighlightCandidate(pos.immutable(), pos.distSqr(center)));
        });

        renderCandidates(module, poseStack, cameraPos, consumer, candidates);
    }

    private interface BlockCandidateConsumer {
        void accept(MutableBlockPos pos);
    }

    private static void scanForCandidates(Minecraft minecraft, Block targetBlock, BlockPos center,
            MutableBlockPos cursor, int radius, BlockCandidateConsumer consumer) {
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int y = center.getY() - radius; y <= center.getY() + radius; y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    cursor.set(x, y, z);
                    if (minecraft.level != null && minecraft.level.getBlockState(cursor).is(targetBlock)) {
                        consumer.accept(cursor);
                    }
                }
            }
        }
    }

    private static void renderCandidates(BlockGlowModule module, PoseStack poseStack, Vec3 cameraPos,
            VertexConsumer consumer, List<HighlightCandidate> candidates) {
        for (HighlightCandidate candidate : candidates) {
            BlockPos pos = candidate.pos();
            poseStack.pushPose();
            poseStack.translate(pos.getX() - cameraPos.x, pos.getY() - cameraPos.y, pos.getZ() - cameraPos.z);
            LevelRenderer.renderLineBox(
                    poseStack,
                    consumer,
                    AABB.unitCubeFromLowerCorner(Vec3.ZERO),
                    module.getConfig().getOutlineRed(),
                    module.getConfig().getOutlineGreen(),
                    module.getConfig().getOutlineBlue(),
                    module.getConfig().getOutlineAlpha()
            );
            poseStack.popPose();
        }
    }

    private record HighlightCandidate(BlockPos pos, double distanceSquared) {
    }

    private static int executeEnable(CommandContext<CommandSourceStack> context, int radius,
            int durationSeconds) {
        BlockGlowModule module = getModule();
        if (module == null || !module.isModuleEnabled()) {
            return 0;
        }

        CommandSourceStack source = context.getSource();
        ResourceLocation blockId = ResourceLocationArgument.getId(context, "block_id");
        if (!BuiltInRegistries.BLOCK.containsKey(blockId)) {
            source.sendFailure(Component.literal("Unknown block type: " + blockId)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        Block block = BuiltInRegistries.BLOCK.get(blockId);
        if (block == Blocks.AIR) {
            source.sendFailure(Component.literal("Cannot glow air blocks")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        int maxRadius = module.getConfig().getMaxRadius();
        if (radius > maxRadius) {
            source.sendFailure(Component.literal("Radius cannot exceed " + maxRadius)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            source.sendFailure(Component.literal("No active world on client")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        module.setClientState(blockId, radius, durationSeconds, minecraft.level.getGameTime());

        String durationText = durationSeconds <= 0 ? "indefinitely" : durationSeconds + " seconds";
        source.sendSuccess(() -> Component.literal("Block glow enabled for " + blockId
                        + " (radius=" + radius + ", duration=" + durationText + ")")
                .withStyle(ChatFormatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeClear(CommandContext<CommandSourceStack> context) {
        BlockGlowModule module = getModule();
        if (module == null || !module.isModuleEnabled()) {
            return 0;
        }

        module.clearClientState();
        context.getSource().sendSuccess(() -> Component.literal("Block glow cleared")
                .withStyle(ChatFormatting.YELLOW), false);
        return Command.SINGLE_SUCCESS;
    }

    private static BlockGlowModule getModule() {
        Module module = ModuleManager.getInstance().getModule("block_glow");
        if (module instanceof BlockGlowModule blockGlowModule) {
            return blockGlowModule;
        }
        return null;
    }
}

