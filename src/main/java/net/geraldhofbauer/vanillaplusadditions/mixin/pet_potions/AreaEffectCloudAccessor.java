package net.geraldhofbauer.vanillaplusadditions.mixin.pet_potions;

import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link AreaEffectCloud}'s private {@code potionContents} field.
 *
 * <p>Vanilla only offers a setter, so there is no way to ask a lingering cloud which effects it
 * carries. The Pet Potions module needs exactly that to decide whether a cloud is a calming one
 * (healing/regeneration) worth tracking. Read-only accessor — nothing here modifies the cloud.
 */
@Mixin(AreaEffectCloud.class)
public interface AreaEffectCloudAccessor {

    /**
     * {@return the potion contents the cloud applies to entities inside it}
     */
    @Accessor("potionContents")
    PotionContents getPotionContents();
}
