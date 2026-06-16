package net.geraldhofbauer.vanillaplusadditions.mixin;

import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.ChestBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cats must not block chest opening when CatGuardianModule is active.
 * Vanilla detects cats in the AABB (y+1, y+2) above the chest; a cat sitting on
 * the chest surface (y+0.875) still reaches into that zone. We override this
 * to allow chests to be opened regardless of cats sitting on top.
 */
@Mixin(ChestBlock.class)
public class ChestBlockCatMixin {

    @Inject(method = "isCatSittingOnChest", at = @At("HEAD"), cancellable = true)
    private static void allowChestsWithCats(LevelAccessor level, BlockPos pos,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (CatGuardianModule.isModuleActive()) {
            cir.setReturnValue(false);
        }
    }
}
