package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games;

import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.GameSession;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.MiniGame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

/**
 * Shadow Wardens: three escalating waves of glowing shadow creatures (2, then 3, then 4) close in
 * on the player. Defeat every wave to win. The wardens drop no loot or XP (suppressed centrally by
 * their game tag) and any survivors are removed when the trial ends.
 */
public class ShadowWavesGame implements MiniGame {

    private static final int[] WAVE_SIZES = {2, 3, 4};

    private static final class State {
        private int wave = -1;
    }

    @Override
    public String id() {
        return "shadow_waves";
    }

    @Override
    public void start(GameSession ctx) {
        ctx.setState(new State());
        ctx.gameMessage("start");
        spawnWave(ctx, 0);
    }

    @Override
    public void tick(GameSession ctx) {
        State state = ctx.state();
        if (state == null) {
            return;
        }
        long alive = ctx.trackedEntities().stream()
                .map(id -> ctx.level().getEntity(id))
                .filter(e -> e != null && e.isAlive() && !e.isRemoved())
                .count();
        if (alive > 0) {
            return;
        }
        if (state.wave >= WAVE_SIZES.length - 1) {
            ctx.win();
        } else {
            spawnWave(ctx, state.wave + 1);
        }
    }

    private void spawnWave(GameSession ctx, int wave) {
        State state = ctx.state();
        state.wave = wave;
        ctx.actionBar(net.minecraft.network.chat.Component.translatable(
                GameSession.lang("progress"), wave + 1, WAVE_SIZES.length));
        for (int i = 0; i < WAVE_SIZES[wave]; i++) {
            spawnShadow(ctx, (wave + i) % 2 == 0 ? EntityType.ZOMBIE : EntityType.SPIDER);
        }
    }

    private void spawnShadow(GameSession ctx, EntityType<?> type) {
        Entity entity = type.create(ctx.level());
        if (!(entity instanceof Mob mob)) {
            return;
        }
        BlockPos center = ctx.center();
        double angle = ctx.random().nextDouble() * Math.PI * 2;
        double dist = 5 + ctx.random().nextDouble() * 5;
        int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
        int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
        int y = ctx.groundY(x, z);
        mob.moveTo(x + 0.5, y, z + 0.5, ctx.random().nextFloat() * 360, 0);
        mob.finalizeSpawn(ctx.level(), ctx.level().getCurrentDifficultyAt(mob.blockPosition()),
                MobSpawnType.EVENT, null);
        mob.setPersistenceRequired();
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        mob.setTarget(ctx.player());
        ctx.level().addFreshEntity(mob);
        ctx.trackEntity(mob);
        ctx.particles(ParticleTypes.LARGE_SMOKE, new Vec3(x + 0.5, y + 1, z + 0.5), 15, 0.3, 0.02);
    }
}
