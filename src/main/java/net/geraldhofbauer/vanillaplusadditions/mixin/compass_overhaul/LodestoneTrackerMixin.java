package net.geraldhofbauer.vanillaplusadditions.mixin.compass_overhaul;

import net.geraldhofbauer.vanillaplusadditions.modules.compass_overhaul.CompassOverhaulModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A lodestone compass must only lose its binding when the lodestone is provably gone.
 *
 * <p>Vanilla asks {@code PoiManager.existsAtPosition} every tick and never checks whether the
 * target chunk is loaded. {@code PoiManager.exists} collapses "no data" into {@code false} via
 * {@code orElse(false)}, and {@code SectionStorage} caches an empty result for a missing,
 * unreadable or unparseable POI column — permanently, because 1.21.1 never evicts it. Any of
 * those turns into {@code new LodestoneTracker(Optional.empty(), true)}, which
 * {@code CompassItem.inventoryTick} writes straight back into the stack. The coordinates are then
 * gone, while the item keeps its glint and its "Lodestone Compass" name and the needle spins.
 *
 * <p>This decides the same question from the block itself: binding kept while the chunk is
 * unloaded, kept while a lodestone still stands there, and only handed back to vanilla — which
 * then clears it — once the chunk is loaded and the block is provably something else.
 */
@Mixin(LodestoneTracker.class)
public class LodestoneTrackerMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void keepBindingWithoutProof(ServerLevel level, CallbackInfoReturnable<LodestoneTracker> cir) {
        if (!CompassOverhaulModule.guardsLodestone()) {
            return;
        }
        LodestoneTracker self = (LodestoneTracker) (Object) this;
        if (!self.tracked() || self.target().isEmpty()) {
            return;
        }
        GlobalPos target = self.target().get();
        if (target.dimension() != level.dimension()) {
            return; // like vanilla: a target in another dimension is not ours to judge
        }
        BlockPos pos = target.pos();
        if (!level.isInWorldBounds(pos)) {
            return; // nothing can stand there, let vanilla clear it
        }
        if (!level.hasChunkAt(pos) || level.getBlockState(pos).is(Blocks.LODESTONE)) {
            cir.setReturnValue(self);
        }
    }
}
