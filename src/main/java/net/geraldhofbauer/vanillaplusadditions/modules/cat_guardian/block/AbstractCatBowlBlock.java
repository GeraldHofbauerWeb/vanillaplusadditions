package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.block;

import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.AbstractCatBowlBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Shared logic for CatBowlBlock and CatFeedingStationBlock: cat association on shift+right-click.
 */
public abstract class AbstractCatBowlBlock extends net.minecraft.world.level.block.Block
        implements net.minecraft.world.level.block.EntityBlock {

    protected AbstractCatBowlBlock(Properties properties) {
        super(properties);
    }

    /**
     * Associates all owned, tamed cats within the configured radius with the bowl at bowlPos.
     * Returns the number of cats associated.
     */
    protected int associateCatsWithBowl(Level level, BlockPos bowlPos, Player player) {
        if (level.isClientSide()) return 0;
        if (!(level.getBlockEntity(bowlPos) instanceof AbstractCatBowlBlockEntity bowl)) return 0;

        double radius = CatGuardianModule.getAssociationRadius();
        AABB searchBox = new AABB(bowlPos).inflate(radius);

        List<Cat> cats = level.getEntitiesOfClass(Cat.class, searchBox, cat ->
                cat.isTame() && Objects.equals(cat.getOwnerUUID(), player.getUUID())
        );

        if (cats.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.vanillaplusadditions.cat_guardian.no_cats"));
            return 0;
        }

        long newBowlLong = bowlPos.asLong();

        for (Cat cat : cats) {
            // Detach cat from its previous bowl
            long oldBowlLong = cat.getData(CatGuardianModule.CAT_BOWL_POS.get());
            if (oldBowlLong != Long.MIN_VALUE) {
                BlockPos oldPos = BlockPos.of(oldBowlLong);
                if (!oldPos.equals(bowlPos)
                        && level.getBlockEntity(oldPos) instanceof AbstractCatBowlBlockEntity oldBowl) {
                    oldBowl.removeCat(cat.getUUID());
                }
            }
            cat.setData(CatGuardianModule.CAT_BOWL_POS.get(), newBowlLong);
            bowl.addCat(cat.getUUID());
        }

        player.sendSystemMessage(Component.translatable(
                "message.vanillaplusadditions.cat_guardian.associated", cats.size()));
        return cats.size();
    }

    /**
     * Removes all cat associations from this bowl and clears bowl-pos on each associated cat.
     * Called when the block is about to be destroyed.
     */
    public static void clearAssociationsOnBreak(Level level, BlockPos pos,
                                                AbstractCatBowlBlockEntity bowl) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) return;
        List<UUID> cats = new ArrayList<>(bowl.getAssociatedCats());
        for (UUID catUUID : cats) {
            // Search in the same dimension as the bowl (cats move between dimensions rarely,
            // but this is still correct behaviour)
            var entity = serverLevel.getEntities().get(catUUID);
            if (entity instanceof Cat cat) {
                cat.setData(CatGuardianModule.CAT_BOWL_POS.get(), Long.MIN_VALUE);
            }
        }
    }
}
