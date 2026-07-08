package net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.games;

import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.entity.MysticalCatEntity;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.GameSession;
import net.geraldhofbauer.vanillaplusadditions.modules.mystical_cat.game.MiniGame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * The Offering (needs Carry On): three glowing baby creatures spawn nearby; pick each up and set it
 * on one of the three deepslate pedestals that ring the spot. When all three pedestals hold an
 * offering, the trial is won. Pedestals restore and the offerings vanish at the end.
 */
public class PedestalOfferingGame implements MiniGame {

    private record State(List<BlockPos> pedestals) {
    }

    @Override
    public String id() {
        return "pedestal_offering";
    }

    @Override
    public boolean canRunAt(ServerLevel level, MysticalCatEntity cat) {
        return ModList.get().isLoaded("carryon");
    }

    @Override
    public void start(GameSession ctx) {
        BlockPos center = ctx.center();
        List<BlockPos> pedestals = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            double angle = (Math.PI * 2 / 3) * i;
            int x = center.getX() + (int) Math.round(Math.cos(angle) * 3);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * 3);
            int y = ctx.groundY(x, z);
            BlockPos pedestal = new BlockPos(x, y, z);
            ctx.placeTempBlock(pedestal, net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            pedestals.add(pedestal);
        }
        spawnOffer(ctx, EntityType.CHICKEN, true, false);
        spawnOffer(ctx, EntityType.FROG, false, false);
        spawnOffer(ctx, EntityType.SLIME, false, true);
        ctx.setState(new State(pedestals));
        ctx.gameMessage("start");
    }

    @Override
    public void tick(GameSession ctx) {
        State state = ctx.state();
        if (state == null) {
            return;
        }
        int satisfied = 0;
        for (BlockPos pedestal : state.pedestals()) {
            Vec3 top = new Vec3(pedestal.getX() + 0.5, pedestal.getY() + 1.0, pedestal.getZ() + 0.5);
            if (ctx.elapsedTicks() % 10 == 0) {
                ctx.particles(ParticleTypes.END_ROD, top.add(0, 0.3, 0), 2, 0.2, 0.0);
            }
            AABB box = new AABB(pedestal.getX(), pedestal.getY() + 1, pedestal.getZ(),
                    pedestal.getX() + 1, pedestal.getY() + 2.2, pedestal.getZ() + 1).inflate(0.35);
            boolean occupied = !ctx.level().getEntitiesOfClass(Mob.class, box,
                    e -> !e.getPersistentData().getString(GameSession.GAME_TAG).isEmpty()).isEmpty();
            if (occupied) {
                satisfied++;
            }
        }
        if (satisfied >= 3) {
            ctx.win();
        }
    }

    private void spawnOffer(GameSession ctx, EntityType<?> type, boolean baby, boolean slime) {
        ServerLevel level = ctx.level();
        Entity entity = type.create(level);
        if (!(entity instanceof Mob mob)) {
            return;
        }
        BlockPos center = ctx.center();
        double angle = ctx.random().nextDouble() * Math.PI * 2;
        double dist = 10 + ctx.random().nextDouble() * 10;
        int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
        int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
        int y = ctx.groundY(x, z);
        mob.moveTo(x + 0.5, y, z + 0.5, ctx.random().nextFloat() * 360, 0);
        if (baby && mob instanceof AgeableMob ageable) {
            ageable.setBaby(true);
        }
        if (slime && mob instanceof Slime slimeMob) {
            slimeMob.setSize(1, true);
        }
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                MobSpawnType.EVENT, null);
        mob.setPersistenceRequired();
        mob.setNoAi(true);
        mob.setInvulnerable(true);
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        level.addFreshEntity(mob);
        ctx.trackEntity(mob);
    }
}
