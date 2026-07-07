package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian;

import net.minecraft.world.entity.ai.goal.CatLieOnBedGoal;
import net.minecraft.world.entity.ai.goal.CatSitOnBlockGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Cat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Removes the vanilla chest/bed idle goals from cats on guard duty and restores them when duty
 * ends. CatSitOnBlockGoal actively navigates a cat onto chests and pins it there in sitting
 * pose — directly fighting the module's home/guard navigation (the classic "guardian cat stuck
 * on a chest"). The removed {@link WrappedGoal} instances (goal + priority) are stored per cat
 * UUID so restoration re-adds them unchanged, without guessing vanilla constructor arguments.
 */
final class CatSitGoalSuppressor {

    private final Map<UUID, List<WrappedGoal>> removedSitGoals = new HashMap<>();

    private static boolean isSitGoal(Goal goal) {
        return goal instanceof CatSitOnBlockGoal || goal instanceof CatLieOnBedGoal;
    }

    /**
     * Strips the sit/lie goals from a cat on duty. Re-checked every tickCat pass: a reloaded
     * entity re-registers fresh goals, which then replace any stale stored instances.
     */
    void suppress(Cat cat) {
        List<WrappedGoal> present = cat.goalSelector.getAvailableGoals().stream()
                .filter(w -> isSitGoal(w.getGoal()))
                .toList();
        if (present.isEmpty()) {
            return;
        }
        List<WrappedGoal> stored = removedSitGoals.computeIfAbsent(cat.getUUID(), k -> new ArrayList<>());
        stored.clear(); // stale instances from a previous entity instance are garbage
        stored.addAll(present);
        // removeGoal stops a running goal, so a cat currently parked on a chest gets up immediately
        present.forEach(w -> cat.goalSelector.removeGoal(w.getGoal()));
    }

    /**
     * Restores the goals removed by {@link #suppress} once a cat is no longer on duty (bowl
     * gone). No-op if a fresh entity instance already carries its own copies.
     */
    void restore(Cat cat) {
        List<WrappedGoal> stored = removedSitGoals.remove(cat.getUUID());
        if (stored == null || stored.isEmpty()) {
            return;
        }
        boolean anyPresent = cat.goalSelector.getAvailableGoals().stream()
                .anyMatch(w -> isSitGoal(w.getGoal()));
        if (anyPresent) {
            return; // reloaded entity re-registered its own goals; stored instances are stale
        }
        stored.forEach(w -> cat.goalSelector.addGoal(w.getPriority(), w.getGoal()));
    }

    /** Drops the stored state for a cat that left the world. */
    void forget(UUID uid) {
        removedSitGoals.remove(uid);
    }
}
