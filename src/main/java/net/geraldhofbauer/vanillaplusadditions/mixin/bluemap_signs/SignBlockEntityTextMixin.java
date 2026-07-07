package net.geraldhofbauer.vanillaplusadditions.mixin.bluemap_signs;

import net.geraldhofbauer.vanillaplusadditions.modules.bluemap_signs.BluemapSignsModule;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Detects live sign edits so the BlueMap Signs module can (re)evaluate the {@code [bm]} marker.
 * Fires at the end of {@code setText} (a player finishing the edit / placement), not on NBT load
 * (which assigns the text fields directly). The hook itself does all side/thread/enabled guarding.
 */
@Mixin(SignBlockEntity.class)
public class SignBlockEntityTextMixin {

    @Inject(method = "setText(Lnet/minecraft/world/level/block/entity/SignText;Z)Z", at = @At("RETURN"))
    private void vpaOnSetText(SignText text, boolean isFrontText, CallbackInfoReturnable<Boolean> cir) {
        BluemapSignsModule.onSignChanged((SignBlockEntity) (Object) this);
    }
}
