package net.geraldhofbauer.vanillaplusadditions.modules.mo_arrows.item;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * An arrow that leaves the bow already burning.
 *
 * <p>The entity is vanilla's own {@link Arrow}; the only change is that it is lit for the same
 * hundred seconds a Flame-enchanted bow gives its arrows, which is far longer than any arrow stays
 * in the air. Everything that follows from a burning arrow is therefore vanilla behaviour and needs
 * no code here: it ignites what it hits for five seconds ({@code AbstractArrow.onHitEntity}), it
 * lights TNT, campfires and candles through their own {@code onProjectileHit}, and it goes out in
 * water.
 *
 * <p>What vanilla does <em>not</em> do is set the ground alight — that half lives in
 * {@code MoArrowsModule.onProjectileImpact}, which recognises this arrow by the item it would drop.
 */
public class FireArrowItem extends ArrowItem {

    /** What a Flame-enchanted bow gives its arrows; an arrow never flies anywhere near that long. */
    private static final float BURN_SECONDS = 100.0F;

    public FireArrowItem(Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @Nullable ItemStack weapon) {
        AbstractArrow arrow = super.createArrow(level, ammo, shooter, weapon);
        arrow.igniteForSeconds(BURN_SECONDS);
        return arrow;
    }

    @Override
    public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
        Projectile projectile = super.asProjectile(level, position, stack, direction);
        projectile.igniteForSeconds(BURN_SECONDS);
        return projectile;
    }
}
