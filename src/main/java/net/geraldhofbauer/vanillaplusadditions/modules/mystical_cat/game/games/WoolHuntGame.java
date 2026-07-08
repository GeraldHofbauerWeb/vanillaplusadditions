package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games;

import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.GameSession;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.MiniGame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Yarn Hunt: five balls of yarn (wool) are tucked into nooks around the spot, shimmering faintly.
 * Find and break them all before time runs out. Broken yarn drops nothing and is restored to its
 * original block; any yarn left at the end is restored too.
 */
public class WoolHuntGame implements MiniGame {

    private static final int WOOL_COUNT = 5;
    private static final int RADIUS = 25;
    private static final Block[] WOOLS = {
            Blocks.WHITE_WOOL, Blocks.PINK_WOOL, Blocks.LIGHT_BLUE_WOOL,
            Blocks.YELLOW_WOOL, Blocks.LIME_WOOL, Blocks.MAGENTA_WOOL
    };

    private record State(Set<BlockPos> remaining, int total) {
    }

    @Override
    public String id() {
        return "wool_hunt";
    }

    @Override
    public void start(GameSession ctx) {
        BlockPos center = ctx.center();
        Set<BlockPos> placed = new LinkedHashSet<>();
        int attempts = 0;
        while (placed.size() < WOOL_COUNT && attempts++ < 200) {
            double angle = ctx.random().nextDouble() * Math.PI * 2;
            double dist = 8 + ctx.random().nextDouble() * (RADIUS - 8);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
            int y = ctx.groundY(x, z) + ctx.random().nextInt(3); // ground..+2 so some hang in nooks
            BlockPos pos = new BlockPos(x, y, z);
            if (placed.contains(pos) || !ctx.level().getBlockState(pos).canBeReplaced()) {
                continue;
            }
            if (!hasSolidNeighbour(ctx, pos)) {
                continue;
            }
            Block wool = WOOLS[ctx.random().nextInt(WOOLS.length)];
            ctx.placeTempBlock(pos, wool.defaultBlockState());
            placed.add(pos);
        }
        if (placed.isEmpty()) {
            ctx.lose();
            return;
        }
        ctx.setState(new State(placed, placed.size()));
        ctx.gameMessage("start", placed.size());
    }

    @Override
    public void tick(GameSession ctx) {
        State state = ctx.state();
        if (state == null || ctx.elapsedTicks() % 40 != 0) {
            return;
        }
        for (BlockPos pos : state.remaining()) {
            ctx.particles(ParticleTypes.END_ROD, new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                    2, 0.35, 0.0);
        }
    }

    @Override
    public void onBlockBreak(GameSession ctx, ServerPlayer player, BlockPos pos) {
        State state = ctx.state();
        if (state == null || !state.remaining().remove(pos)) {
            return;
        }
        ctx.restoreSingleBlock(pos);
        ctx.particles(ParticleTypes.CLOUD, new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                8, 0.2, 0.02);
        ctx.soundAt(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                SoundEvents.WOOL_BREAK, 0.9f, 1.2f);
        int found = state.total() - state.remaining().size();
        if (state.remaining().isEmpty()) {
            ctx.win();
        } else {
            ctx.actionBar(net.minecraft.network.chat.Component.translatable(
                    GameSession.lang("progress"), found, state.total()));
        }
    }

    private boolean hasSolidNeighbour(GameSession ctx, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (ctx.level().getBlockState(pos.relative(dir)).isSolidRender(ctx.level(), pos.relative(dir))) {
                return true;
            }
        }
        return false;
    }
}
