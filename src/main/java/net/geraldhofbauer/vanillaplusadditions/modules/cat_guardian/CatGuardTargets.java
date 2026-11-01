package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian;

import net.geraldhofbauer.vanillaplusadditions.core.ModuleManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Slime;

/**
 * What a guardian cat hunts: every {@link Monster}, plus slimes while {@code fight_slimes} is on.
 * <p>
 * {@link Slime} (and its subclass, the magma cube) is a plain {@code Mob} that implements
 * {@code Enemy}, so the original {@code Monster}-only search never saw one and a slime could hop
 * through a guarded base untouched (Sebi's request, 2026-11-01). A slime cannot hurt a cat back:
 * its contact damage ({@code Slime.push} / {@code playerTouch}) only reaches iron golems and players.
 */
final class CatGuardTargets {

    private CatGuardTargets() {
    }

    static boolean isGuardTarget(Entity entity) {
        if (entity instanceof Monster) {
            return true;
        }
        return entity instanceof Slime && fightSlimes();
    }

    private static boolean fightSlimes() {
        return !(ModuleManager.getInstance().getModule("cat_guardian") instanceof CatGuardianModule module)
                || module.getConfig().isFightSlimes();
    }
}
