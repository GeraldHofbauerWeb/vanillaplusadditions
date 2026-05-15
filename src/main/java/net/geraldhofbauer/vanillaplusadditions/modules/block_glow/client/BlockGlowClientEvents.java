package net.geraldhofbauer.vanillaplusadditions.modules.block_glow.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.geraldhofbauer.vanillaplusadditions.VanillaPlusAdditions;
import net.geraldhofbauer.vanillaplusadditions.core.Module;
import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.geraldhofbauer.vanillaplusadditions.modules.block_glow.BlockGlowModule;
import net.geraldhofbauer.vanillaplusadditions.modules.block_glow.BlockGlowModule.BlockGlowState;
import net.geraldhofbauer.vanillaplusadditions.modules.block_glow.compat.BlockGlowSableIntegration;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
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
        Vec3 playerPos = minecraft.player.position();
        AABB searchBox = new AABB(
                playerPos.x - radius,
                playerPos.y - radius,
                playerPos.z - radius,
                playerPos.x + radius,
                playerPos.y + radius,
                playerPos.z + radius
        );

        List<BlockGlowHighlight> candidates = new ArrayList<>();
        collectMainWorldHighlights(minecraft.level, targetBlock, minecraft.player.blockPosition(), playerPos, radius,
                candidates);
        if (BlockGlowSableIntegration.isLoaded()) {
            BlockGlowSableIntegration.collectHighlights(minecraft.level, targetBlock, searchBox, playerPos,
                    candidates::add);
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        VertexConsumer consumer = minecraft.renderBuffers().bufferSource().getBuffer(XRAY_LINES);

        String selectionMode = module.getConfig().getSelectionMode();
        if ("scan_order".equals(selectionMode)) {
            renderScanOrderHighlights(candidates, maxHighlights, poseStack, cameraPos, consumer, module);
        } else {
            renderNearestHighlights(candidates, maxHighlights, poseStack, cameraPos, consumer, module);
        }

        minecraft.renderBuffers().bufferSource().endBatch(XRAY_LINES);
    }

    private static void collectMainWorldHighlights(ClientLevel level, Block targetBlock, BlockPos center,
            Vec3 playerPos, int radius, List<BlockGlowHighlight> candidates) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int y = center.getY() - radius; y <= center.getY() + radius; y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).is(targetBlock)) {
                        continue;
                    }

                    AABB worldAabb = new AABB(cursor);
                    candidates.add(new BlockGlowHighlight(worldAabb, worldAabb.getCenter().distanceToSqr(playerPos)));
                }
            }
        }
    }

    private static void renderNearestHighlights(List<BlockGlowHighlight> candidates, int maxHighlights,
            PoseStack poseStack, Vec3 cameraPos, VertexConsumer consumer, BlockGlowModule module) {
        PriorityQueue<BlockGlowHighlight> nearestCandidates = new PriorityQueue<>(
                Comparator.comparingDouble(BlockGlowHighlight::distanceSquared).reversed()
        );

        for (BlockGlowHighlight candidate : candidates) {
            if (nearestCandidates.size() < maxHighlights) {
                nearestCandidates.offer(candidate);
                continue;
            }

            BlockGlowHighlight farthest = nearestCandidates.peek();
            if (farthest != null && candidate.distanceSquared() < farthest.distanceSquared()) {
                nearestCandidates.poll();
                nearestCandidates.offer(candidate);
            }
        }

        List<BlockGlowHighlight> sortedCandidates = new ArrayList<>(nearestCandidates);
        sortedCandidates.sort(Comparator.comparingDouble(BlockGlowHighlight::distanceSquared));
        renderCandidates(sortedCandidates, poseStack, cameraPos, consumer, module);
    }

    private static void renderScanOrderHighlights(List<BlockGlowHighlight> candidates, int maxHighlights,
            PoseStack poseStack, Vec3 cameraPos, VertexConsumer consumer, BlockGlowModule module) {
        int limit = Math.min(maxHighlights, candidates.size());
        renderCandidates(candidates.subList(0, limit), poseStack, cameraPos, consumer, module);
    }

    private static void renderCandidates(List<BlockGlowHighlight> candidates, PoseStack poseStack, Vec3 cameraPos,
            VertexConsumer consumer, BlockGlowModule module) {
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        for (BlockGlowHighlight candidate : candidates) {
            LevelRenderer.renderLineBox(
                    poseStack,
                    consumer,
                    candidate.worldAabb(),
                    module.getConfig().getOutlineRed(),
                    module.getConfig().getOutlineGreen(),
                    module.getConfig().getOutlineBlue(),
                    module.getConfig().getOutlineAlpha()
            );
        }

        poseStack.popPose();
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

