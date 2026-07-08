package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games;

import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.GameSession;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.MiniGame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Wisp Chase: a sequence of glowing wisps appears one after another around the spot. Walk into each
 * (within ~1.5 blocks) to collect it; gather them all before time runs out. No entities or blocks —
 * purely particles, so nothing can be left behind.
 */
public class WispChaseGame implements MiniGame {

    private static final int WISP_COUNT = 6;

    private static final class State {
        private final List<Vec3> wisps;
        private int index;

        private State(List<Vec3> wisps) {
            this.wisps = wisps;
        }
    }

    @Override
    public String id() {
        return "wisp_chase";
    }

    @Override
    public void start(GameSession ctx) {
        BlockPos center = ctx.center();
        List<Vec3> wisps = new ArrayList<>();
        for (int i = 0; i < WISP_COUNT; i++) {
            double angle = ctx.random().nextDouble() * Math.PI * 2;
            double dist = 6 + ctx.random().nextDouble() * 8;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
            int y = ctx.groundY(x, z);
            wisps.add(new Vec3(x + 0.5, y + 1.1, z + 0.5));
        }
        ctx.setState(new State(wisps));
        ctx.gameMessage("start", WISP_COUNT);
    }

    @Override
    public void tick(GameSession ctx) {
        State state = ctx.state();
        if (state == null || state.index >= state.wisps.size()) {
            return;
        }
        Vec3 wisp = state.wisps.get(state.index);
        ctx.particles(ParticleTypes.END_ROD, wisp, 4, 0.12, 0.01);
        if (ctx.elapsedTicks() % 6 == 0) {
            ctx.particles(ParticleTypes.GLOW, wisp, 2, 0.2, 0.0);
        }
        if (ctx.player().position().closerThan(wisp, 1.6)) {
            state.index++;
            ctx.soundAt(wisp, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
            ctx.particles(ParticleTypes.WAX_ON, wisp, 12, 0.3, 0.05);
            if (state.index >= state.wisps.size()) {
                ctx.win();
            } else {
                ctx.gameActionBar("start", state.wisps.size() - state.index);
            }
        }
    }
}
