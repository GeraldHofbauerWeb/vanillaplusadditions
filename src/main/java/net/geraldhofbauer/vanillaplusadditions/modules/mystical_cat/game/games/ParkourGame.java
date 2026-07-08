package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games;

import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.entity.MysticalCatEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.GameSession;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.MiniGame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Skyward Steps (parkour): a short tower of floating glass platforms spirals up to a crowned goal.
 * Reach the top platform to win. Platforms are placed only into air and always restored, so the
 * surrounding terrain is never harmed.
 */
public class ParkourGame implements MiniGame {

    private record State(Vec3 goal) {
    }

    @Override
    public String id() {
        return "parkour";
    }

    @Override
    public boolean canRunAt(ServerLevel level, MysticalCatEntity cat) {
        BlockPos c = cat.blockPosition();
        // Needs open sky for the tower.
        return level.getBlockState(c.above(4)).isAir() && level.getBlockState(c.above(9)).isAir();
    }

    @Override
    public void start(GameSession ctx) {
        BlockPos center = ctx.center();
        int steps = 8 + ctx.random().nextInt(5); // 8..12
        double angle = ctx.random().nextDouble() * Math.PI * 2;
        double dirX = Math.cos(angle);
        double dirZ = Math.sin(angle);

        int baseY = ctx.groundY(center.getX(), center.getZ()) + 2;
        int y = baseY;
        int lastX = center.getX();
        int lastZ = center.getZ();
        for (int i = 0; i < steps; i++) {
            int px = center.getX() + (int) Math.round(dirX * (i * 2.5));
            int pz = center.getZ() + (int) Math.round(dirZ * (i * 2.5));
            y += 1 + (i % 2); // rise 1 or 2 each step
            int placeY = clearY(ctx, px, pz, y);
            y = placeY;
            place2x2(ctx, px, placeY, pz, Blocks.PURPLE_STAINED_GLASS);
            lastX = px;
            lastZ = pz;
            if (i == steps - 1) {
                place2x2(ctx, px, placeY, pz, Blocks.CRYING_OBSIDIAN);
                ctx.placeTempBlock(new BlockPos(px, placeY + 1, pz), Blocks.END_ROD.defaultBlockState());
                ctx.placeTempBlock(new BlockPos(px, placeY + 2, pz), Blocks.END_ROD.defaultBlockState());
            }
        }
        ctx.setState(new State(new Vec3(lastX + 0.5, y + 1.0, lastZ + 0.5)));
        ctx.gameMessage("start");
    }

    @Override
    public void tick(GameSession ctx) {
        State state = ctx.state();
        if (state == null) {
            return;
        }
        ctx.particles(ParticleTypes.END_ROD, state.goal().add(0, 1.2, 0), 3, 0.15, 0.01);
        if (ctx.player().position().closerThan(state.goal(), 2.2)) {
            ctx.particles(ParticleTypes.TOTEM_OF_UNDYING, state.goal().add(0, 1.0, 0), 30, 0.4, 0.1);
            ctx.win();
        }
    }

    /** Nudges the platform up until its 2×2 footprint sits in air (bounded search). */
    private int clearY(GameSession ctx, int x, int z, int startY) {
        int y = startY;
        for (int tries = 0; tries < 4; tries++) {
            if (ctx.level().getBlockState(new BlockPos(x, y, z)).canBeReplaced()
                    && ctx.level().getBlockState(new BlockPos(x + 1, y, z)).canBeReplaced()) {
                return y;
            }
            y++;
        }
        return y;
    }

    private void place2x2(GameSession ctx, int x, int y, int z, net.minecraft.world.level.block.Block block) {
        ctx.placeTempBlock(new BlockPos(x, y, z), block.defaultBlockState());
        ctx.placeTempBlock(new BlockPos(x + 1, y, z), block.defaultBlockState());
        ctx.placeTempBlock(new BlockPos(x, y, z + 1), block.defaultBlockState());
        ctx.placeTempBlock(new BlockPos(x + 1, y, z + 1), block.defaultBlockState());
    }
}
