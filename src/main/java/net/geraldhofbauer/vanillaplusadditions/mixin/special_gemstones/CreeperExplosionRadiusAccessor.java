package net.geraldhofbauer.vanillaplusadditions.mixin.special_gemstones;

import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link Creeper}'s private {@code explosionRadius} so a gemstone can scale the blast along
 * with the creeper.
 *
 * <p>A creeper's blast is the one "stat" that is not an attribute: {@code explodeCreeper} reads a
 * plain {@code int} field (3 by default, doubled while charged) and hands it to
 * {@code Level.explode}. There is nothing to attach a modifier to, and the {@code Explosion}'s own
 * {@code radius} is {@code private final}, so it cannot be adjusted at detonation time either —
 * intercepting {@code ExplosionEvent.Start} would mean cancelling the explosion and starting a
 * second one, which fires the same event again and needs a recursion guard for no gain.
 *
 * <p>Writing the field instead is both simpler and free to persist: vanilla already saves it as
 * {@code ExplosionRadius} in the creeper's NBT, so a grown creeper is still grown after a restart
 * without this module storing anything of its own.
 */
@Mixin(Creeper.class)
public interface CreeperExplosionRadiusAccessor {

    /**
     * {@return the radius of the explosion this creeper will set off, before the charged doubling}
     */
    @Accessor("explosionRadius")
    int getExplosionRadius();

    /**
     * Sets that radius. Vanilla persists it as {@code ExplosionRadius} in the creeper's NBT.
     *
     * @param radius the new radius, at least 1
     */
    @Accessor("explosionRadius")
    void setExplosionRadius(int radius);
}
