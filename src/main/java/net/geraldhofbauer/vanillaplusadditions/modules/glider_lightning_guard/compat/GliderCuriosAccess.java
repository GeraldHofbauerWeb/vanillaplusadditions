package net.geraldhofbauer.vanillaplusadditions.modules.glider_lightning_guard.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.List;
import java.util.function.Predicate;

/**
 * Reads worn gliders out of Curios slots.
 *
 * <p>The Gliders mod looks for an equipped glider in the chest armor slot first and in the Curios
 * back/cape slots after that, so covering only the armor slot would miss the usual case. Every
 * Curios type in this module lives in this class, and nothing calls into it without
 * {@link CuriosGate#isLoaded()} being true first — otherwise a pack without Curios would fail to
 * link it.
 */
public final class GliderCuriosAccess {

    private GliderCuriosAccess() {
    }

    /**
     * All worn Curios stacks matching the predicate.
     *
     * @param entity the wearer
     * @param filter which stacks count
     * @return the matching stacks, in slot order
     */
    public static List<ItemStack> findWorn(LivingEntity entity, Predicate<ItemStack> filter) {
        return CuriosApi.getCuriosInventory(entity)
                .map(inventory -> inventory.findCurios(filter).stream().map(SlotResult::stack).toList())
                .orElse(List.of());
    }
}
