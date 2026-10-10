package net.geraldhofbauer.vanillaplusadditions.mixin.quark_fresh_animations;

import java.util.EnumSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps Quark's Toretoise from crashing once Entity Model Features has rebuilt its model.
 *
 * <p>{@code ToretoiseModel.renderToBuffer} walks the legs itself, while drawing: for each leg it
 * picks a cube with {@code ModelPart.getRandomCube}, uses that cube's corner as the pivot, and lifts
 * and swings the leg around it on the pose stack. As soon as any {@code .jem} exists for the
 * Toretoise, EMF rebuilds the model and moves every cube one level down into its own child parts -
 * the leg part itself keeps none, {@code getRandomCube} calls {@code nextInt(0)} and the game crashes
 * with "Bound must be positive".
 *
 * <p>An empty part now hands back a cube from its descendants, which is the same box EMF moved
 * there, so Quark's step keeps its real pivot. Only if no descendant has one either (a part the
 * {@code .jem} left empty) a zero-sized stand-in at the origin keeps the game alive. Parts that still
 * have cubes are untouched.
 *
 * <p>Quark is not a compile dependency, hence the string target; without Quark, Mixin skips this
 * class (the mixin config is {@code "required": false}). The Toretoise CEM files live in their own
 * built-in pack ({@code resourcepacks/vpa_quark_toretoise}) that only the mod adds, so the
 * standalone resource pack never ships a model that would crash without this mixin.
 */
@Mixin(targets = "org.violetmoon.quark.content.mobs.client.model.ToretoiseModel", remap = false)
public abstract class ToretoiseRandomCubeMixin {

    @Unique
    private static final ModelPart.Cube VPA_EMPTY_CUBE = new ModelPart.Cube(
            0, 0, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, 0F, false, 1F, 1F, EnumSet.noneOf(Direction.class));

    @Redirect(method = "lambda$renderToBuffer$0", remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraft/client/model/geom/ModelPart;getRandomCube"
                            + "(Lnet/minecraft/util/RandomSource;)Lnet/minecraft/client/model/geom/ModelPart$Cube;"))
    private ModelPart.Cube vpaSafeRandomCube(ModelPart part, RandomSource random) {
        if (!part.isEmpty()) {
            return part.getRandomCube(random);
        }
        return part.getAllParts()
                .filter(child -> !child.isEmpty())
                .findFirst()
                .map(child -> child.getRandomCube(random))
                .orElse(VPA_EMPTY_CUBE);
    }
}
