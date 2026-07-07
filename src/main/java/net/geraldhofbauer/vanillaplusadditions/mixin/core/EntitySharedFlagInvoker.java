package net.geraldhofbauer.vanillaplusadditions.mixin.core;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes {@link Entity}'s protected {@code setSharedFlag} for the client-only guardian glow
 * (cat_guardian + axolotl_guardian).
 *
 * <p>Needed because {@code Entity.setGlowingTag(true)} is a no-op on the client: it writes
 * {@code setSharedFlag(6, isCurrentlyGlowing())}, and {@code isCurrentlyGlowing()} reads that
 * very shared flag on the client side — the flag therefore never turns on. Setting flag 6
 * directly makes the render path ({@code Minecraft.shouldEntityAppearGlowing}) pick the entity
 * up for the outline without any server involvement.
 */
@Mixin(Entity.class)
public interface EntitySharedFlagInvoker {

    @Invoker("setSharedFlag")
    void callSetSharedFlag(int flag, boolean value);
}
