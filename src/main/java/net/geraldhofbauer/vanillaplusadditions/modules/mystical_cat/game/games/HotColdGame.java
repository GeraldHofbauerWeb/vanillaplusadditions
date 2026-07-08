package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games;

import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.GameSession;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.MiniGame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * Warmer / Colder: an invisible point is hidden nearby. Once a second the cat "sings" — the pitch
 * and particle density rise the closer the player gets. Stand within 2 blocks of the point to win.
 */
public class HotColdGame implements MiniGame {

    private static final double MAX_RANGE = 30.0;

    private record State(Vec3 target) {
    }

    @Override
    public String id() {
        return "hot_cold";
    }

    @Override
    public void start(GameSession ctx) {
        BlockPos center = ctx.center();
        double angle = ctx.random().nextDouble() * Math.PI * 2;
        double dist = 15 + ctx.random().nextDouble() * 15;
        int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
        int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
        int y = ctx.groundY(x, z);
        ctx.setState(new State(new Vec3(x + 0.5, y + 0.5, z + 0.5)));
        ctx.gameMessage("start");
    }

    @Override
    public void tick(GameSession ctx) {
        State state = ctx.state();
        if (state == null) {
            return;
        }
        double dist = ctx.player().position().distanceTo(state.target());
        if (dist < 2.0) {
            ctx.particles(ParticleTypes.END_ROD, state.target().add(0, 0.5, 0), 30, 0.4, 0.05);
            ctx.soundAt(state.target(), SoundEvents.PLAYER_LEVELUP, 1.0f, 1.5f);
            ctx.win();
            return;
        }
        if (ctx.elapsedTicks() % 20 == 0) {
            double proximity = Math.max(0.0, 1.0 - dist / MAX_RANGE);
            float pitch = 0.5f + (float) proximity * 1.5f;
            ctx.soundAt(ctx.player().position(), SoundEvents.CAT_AMBIENT, 0.7f, pitch);
            int count = 1 + (int) (proximity * 12);
            ctx.particles(ParticleTypes.SOUL_FIRE_FLAME, ctx.player().position().add(0, 1.0, 0),
                    count, 0.4, 0.01);
        }
    }
}
