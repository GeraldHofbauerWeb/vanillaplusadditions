package net.geraldhofbauer.vanillaplusadditions.mixin.mystical_cat;

import net.minecraft.world.entity.animal.Cat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@link Cat}'s package-private {@code setRelaxStateOne} so the Mystical Cat can adopt the
 * curled-up "relaxed" sleeping pose that Fresh Animations / vanilla render for a cat lying on its
 * owner's bed.
 *
 * <p>{@code Cat.setLying(true)} alone only flattens the cat; the tightly curled sleep pose needs
 * {@code relaxStateOne} set as well (see {@code Cat.CatRelaxOnOwnerGoal}). That setter is
 * package-private, hence this invoker mixin (same pattern as {@code EntitySharedFlagInvoker}).
 */
@Mixin(Cat.class)
public interface CatRelaxStateInvoker {

    @Invoker("setRelaxStateOne")
    void callSetRelaxStateOne(boolean relaxStateOne);
}
