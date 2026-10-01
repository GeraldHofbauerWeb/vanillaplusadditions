package net.geraldhofbauer.vanillaplusadditions.mixin.trial_spawner_glow;

import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;
import java.util.UUID;

/**
 * Exposes {@link TrialSpawnerData}'s {@code currentMobs} - the set of mobs a trial spawner is
 * currently keeping track of.
 *
 * <p>The public API only aggregates it: {@code haveAllCurrentMobsDied()} asks whether the set is
 * empty and {@code isReadyToSpawnNextMob} asks for its size. Neither hands the ids over.
 *
 * <p>Convenience and cost, not necessity — worth writing down so nobody later reads this as "there
 * was no other way". {@code BlockEntity.saveCustomOnly(...)} is {@code public final} and writes the
 * full {@code TrialSpawner.codec()}, which includes {@code current_mobs}, and
 * {@code UUIDUtil.CODEC_SET} could read it back out. That route allocates a whole
 * {@code TrialSpawnerConfig} tree per call and couples us to an NBT key name instead of a field
 * name. This accessor is a single field read.
 *
 * <p>Careful: the returned set is the spawner's <b>live</b> set, not a copy. Copy it before
 * iterating if anything in the loop could touch the spawner.
 */
@Mixin(TrialSpawnerData.class)
public interface TrialSpawnerCurrentMobsAccessor {

    /**
     * {@return the live set of mob ids this spawner is tracking}
     */
    @Accessor("currentMobs")
    Set<UUID> getCurrentMobs();
}
