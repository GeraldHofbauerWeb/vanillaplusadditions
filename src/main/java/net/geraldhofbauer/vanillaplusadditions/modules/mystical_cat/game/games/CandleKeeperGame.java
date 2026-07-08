package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games;

import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.GameSession;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.MiniGame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Candle Keeper: five candles ring the spot. Light them all (bring flint &amp; steel) at the same
 * time to win — but every few seconds the cat puffs a random lit candle back out. Candles are
 * temporary and restored at the end.
 */
public class CandleKeeperGame implements MiniGame {

    private static final int CANDLE_COUNT = 5;

    private static final class State {
        private final List<BlockPos> candles;
        private long nextBlowTick;

        private State(List<BlockPos> candles, long nextBlowTick) {
            this.candles = candles;
            this.nextBlowTick = nextBlowTick;
        }
    }

    @Override
    public String id() {
        return "candle_keeper";
    }

    @Override
    public void start(GameSession ctx) {
        BlockPos center = ctx.center();
        List<BlockPos> candles = new ArrayList<>();
        for (int i = 0; i < CANDLE_COUNT; i++) {
            double angle = (Math.PI * 2 / CANDLE_COUNT) * i;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * 2.5);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * 2.5);
            int y = ctx.groundY(x, z);
            BlockPos pos = new BlockPos(x, y, z);
            ctx.placeTempBlock(pos, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, false));
            candles.add(pos);
        }
        ctx.setState(new State(candles, ctx.level().getGameTime() + 80));
        ctx.gameMessage("start");
    }

    @Override
    public void tick(GameSession ctx) {
        State state = ctx.state();
        if (state == null) {
            return;
        }
        long now = ctx.level().getGameTime();

        if (now >= state.nextBlowTick) {
            List<BlockPos> lit = new ArrayList<>();
            for (BlockPos pos : state.candles) {
                if (isLitCandle(ctx, pos)) {
                    lit.add(pos);
                }
            }
            if (!lit.isEmpty()) {
                BlockPos blow = lit.get(ctx.random().nextInt(lit.size()));
                BlockState s = ctx.level().getBlockState(blow);
                ctx.level().setBlock(blow, s.setValue(CandleBlock.LIT, false), 3);
                Vec3 at = new Vec3(blow.getX() + 0.5, blow.getY() + 0.6, blow.getZ() + 0.5);
                ctx.particles(ParticleTypes.CLOUD, at, 8, 0.15, 0.02);
                ctx.soundAt(at, SoundEvents.CANDLE_EXTINGUISH, 1.0f, 1.0f);
            }
            state.nextBlowTick = now + 60 + ctx.random().nextInt(60);
        }

        int litCount = 0;
        for (BlockPos pos : state.candles) {
            if (isLitCandle(ctx, pos)) {
                litCount++;
            }
        }
        if (litCount == state.candles.size()) {
            ctx.win();
        }
    }

    private boolean isLitCandle(GameSession ctx, BlockPos pos) {
        BlockState s = ctx.level().getBlockState(pos);
        return s.is(Blocks.CANDLE) && s.getValue(CandleBlock.LIT);
    }
}
