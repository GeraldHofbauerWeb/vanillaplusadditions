package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games;

import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.MysticalCatModule;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.entity.MysticalCatEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.GameSession;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.MiniGame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Ghost Walk: a spectral second cat walks a winding path; stay within six blocks of it the whole
 * way. Fall behind once and it warns you — fall behind twice and the trial fails. Reach the end of
 * its walk to win. The ghost is a tracked, invulnerable Mystical Cat that fades at the end.
 */
public class GhostEscortGame implements MiniGame {

    private static final double LEASH = 6.0;

    private static final class State {
        private final UUID ghostId;
        private final List<Vec3> waypoints;
        private int wpIndex;
        private boolean warned;

        private State(UUID ghostId, List<Vec3> waypoints) {
            this.ghostId = ghostId;
            this.waypoints = waypoints;
        }
    }

    @Override
    public String id() {
        return "ghost_escort";
    }

    @Override
    public void start(GameSession ctx) {
        Entity entity = MysticalCatModule.MYSTICAL_CAT.get().create(ctx.level());
        if (!(entity instanceof MysticalCatEntity ghost)) {
            ctx.lose();
            return;
        }
        BlockPos center = ctx.center();
        ghost.setGhost(true);
        ghost.moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0, 0);
        ghost.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        ctx.level().addFreshEntity(ghost);
        ctx.trackEntity(ghost);
        ghost.setSittingPose();

        int count = 5 + ctx.random().nextInt(4); // 5..8
        double baseAngle = ctx.random().nextDouble() * Math.PI * 2;
        double curve = (ctx.random().nextDouble() - 0.5) * 0.6;
        List<Vec3> waypoints = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            double angle = baseAngle + curve * i;
            double dist = i * 5.0;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
            int y = ctx.groundY(x, z);
            waypoints.add(new Vec3(x + 0.5, y, z + 0.5));
        }
        ctx.setState(new State(ghost.getUUID(), waypoints));
        ctx.gameMessage("start");
    }

    @Override
    public void tick(GameSession ctx) {
        State state = ctx.state();
        if (state == null) {
            return;
        }
        if (!(ctx.level().getEntity(state.ghostId) instanceof MysticalCatEntity ghost)) {
            ctx.lose();
            return;
        }
        ghost.setLying(false);

        Vec3 target = state.waypoints.get(state.wpIndex);
        ctx.particles(ParticleTypes.SOUL, ghost.position().add(0, 0.4, 0), 2, 0.15, 0.01);

        if (ghost.position().closerThan(target, 1.8)) {
            state.wpIndex++;
            if (state.wpIndex >= state.waypoints.size()) {
                ctx.particles(ParticleTypes.PORTAL, ghost.position().add(0, 0.5, 0), 40, 0.3, 0.1);
                ctx.soundAt(ghost.position(), SoundEvents.PLAYER_LEVELUP, 1.0f, 1.4f);
                ctx.win();
                return;
            }
            target = state.waypoints.get(state.wpIndex);
        }
        if (ghost.getNavigation().isDone()) {
            ghost.getNavigation().moveTo(target.x, target.y, target.z, 1.0);
        }

        if (ctx.elapsedTicks() % 20 == 0) {
            double dist = ctx.player().position().distanceTo(ghost.position());
            if (dist > LEASH) {
                if (state.warned) {
                    ctx.lose();
                } else {
                    state.warned = true;
                    ctx.gameMessage("warn");
                    ctx.sound(SoundEvents.CAT_HISS, 0.8f, 1.2f);
                }
            } else {
                state.warned = false;
            }
        }
    }
}
