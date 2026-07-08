package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games;

import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.GameSession;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.MiniGame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Frostfall Marks: six glowing target rings appear one at a time; hit each with a snowball (or egg).
 * The cat gifts a stack of snowballs. Purely position-based — no entities or blocks to clean up.
 */
public class SnowballTargetsGame implements MiniGame {

    private static final int TARGET_COUNT = 6;
    private static final Vector3f RING_COLOR = new Vector3f(0.6f, 0.9f, 1.0f);

    private static final class State {
        private final List<Vec3> targets;
        private int index;

        private State(List<Vec3> targets) {
            this.targets = targets;
        }
    }

    @Override
    public String id() {
        return "snowball_targets";
    }

    @Override
    public void start(GameSession ctx) {
        ctx.player().getInventory().add(new ItemStack(Items.SNOWBALL, 16));
        BlockPos center = ctx.center();
        List<Vec3> targets = new ArrayList<>();
        for (int i = 0; i < TARGET_COUNT; i++) {
            double angle = ctx.random().nextDouble() * Math.PI * 2;
            double dist = 5 + ctx.random().nextDouble() * 4;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
            double y = ctx.groundY(x, z) + 1 + ctx.random().nextDouble() * 3;
            targets.add(new Vec3(x + 0.5, y, z + 0.5));
        }
        ctx.setState(new State(targets));
        ctx.gameMessage("start");
        ctx.message("gift");
    }

    @Override
    public void tick(GameSession ctx) {
        State state = ctx.state();
        if (state == null || state.index >= state.targets.size()) {
            return;
        }
        Vec3 target = state.targets.get(state.index);
        // Draw a small ring of dust so the mark reads clearly at range.
        for (int i = 0; i < 8; i++) {
            double a = (Math.PI * 2 / 8) * i;
            Vec3 p = target.add(Math.cos(a) * 0.6, 0, Math.sin(a) * 0.6);
            ctx.level().sendParticles(new DustParticleOptions(RING_COLOR, 1.4f), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    @Override
    public void onProjectileImpact(GameSession ctx, Projectile projectile, Vec3 hitPos) {
        State state = ctx.state();
        if (state == null || state.index >= state.targets.size()) {
            return;
        }
        if (!(projectile instanceof Snowball) && !(projectile instanceof ThrownEgg)) {
            return;
        }
        Vec3 target = state.targets.get(state.index);
        if (!target.closerThan(hitPos, 1.4)) {
            return;
        }
        state.index++;
        ctx.soundAt(target, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.3f);
        ctx.particles(ParticleTypes.SNOWFLAKE, target, 20, 0.3, 0.05);
        if (state.index >= state.targets.size()) {
            ctx.win();
        } else {
            ctx.actionBar(net.minecraft.network.chat.Component.translatable(
                    GameSession.lang("progress"), state.index, TARGET_COUNT));
        }
    }
}
