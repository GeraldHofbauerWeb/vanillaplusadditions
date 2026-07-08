package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games;

import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.GameSession;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.MiniGame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * The Shooting Stars: glowing floating targets appear one at a time around the spot; shoot each with
 * a bow before time runs out. The cat gifts a bow and arrows. Targets are invulnerable floaters
 * detected by projectile proximity, and all are removed when the trial ends.
 */
public class TargetRangeGame implements MiniGame {

    private static final int TARGET_COUNT = 5;

    private static final class State {
        private int hit;
        private UUID current;
    }

    @Override
    public String id() {
        return "target_range";
    }

    @Override
    public void start(GameSession ctx) {
        giveIfMissing(ctx, new ItemStack(Items.BOW));
        ctx.player().getInventory().add(new ItemStack(Items.ARROW, 16));
        ctx.setState(new State());
        ctx.gameMessage("start");
        ctx.message("gift");
        spawnTarget(ctx);
    }

    @Override
    public void tick(GameSession ctx) {
        State state = ctx.state();
        if (state == null || state.current == null) {
            return;
        }
        Entity target = ctx.level().getEntity(state.current);
        if (target != null && ctx.elapsedTicks() % 4 == 0) {
            ctx.particles(ParticleTypes.END_ROD, target.position().add(0, 0.3, 0), 3, 0.2, 0.0);
        }
    }

    @Override
    public void onProjectileImpact(GameSession ctx, Projectile projectile, Vec3 hitPos) {
        State state = ctx.state();
        if (state == null || state.current == null) {
            return;
        }
        Entity target = ctx.level().getEntity(state.current);
        if (target == null || !target.position().closerThan(hitPos, 2.0)) {
            return;
        }
        target.discard();
        state.current = null;
        state.hit++;
        ctx.soundAt(hitPos, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
        ctx.particles(ParticleTypes.FIREWORK, hitPos, 20, 0.3, 0.1);
        if (state.hit >= TARGET_COUNT) {
            ctx.win();
        } else {
            ctx.actionBar(net.minecraft.network.chat.Component.translatable(
                    GameSession.lang("progress"), state.hit, TARGET_COUNT));
            spawnTarget(ctx);
        }
    }

    private void spawnTarget(GameSession ctx) {
        State state = ctx.state();
        Entity entity = EntityType.BAT.create(ctx.level());
        if (!(entity instanceof Mob mob)) {
            return;
        }
        BlockPos center = ctx.center();
        double angle = ctx.random().nextDouble() * Math.PI * 2;
        double dist = 6 + ctx.random().nextDouble() * 5;
        double x = center.getX() + 0.5 + Math.cos(angle) * dist;
        double z = center.getZ() + 0.5 + Math.sin(angle) * dist;
        double y = ctx.groundY((int) x, (int) z) + 2 + ctx.random().nextDouble() * 3;
        mob.moveTo(x, y, z, 0, 0);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setInvulnerable(true);
        mob.setPersistenceRequired();
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        ctx.level().addFreshEntity(mob);
        ctx.trackEntity(mob);
        state.current = mob.getUUID();
    }

    private void giveIfMissing(GameSession ctx, ItemStack stack) {
        if (!ctx.player().getInventory().contains(stack)) {
            ctx.player().getInventory().add(stack.copy());
        }
    }
}
