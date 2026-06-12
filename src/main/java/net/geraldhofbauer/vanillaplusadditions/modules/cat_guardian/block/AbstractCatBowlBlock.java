package net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.block;

import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.CatGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.cat_guardian.blockentity.AbstractCatBowlBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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

    public static final BooleanProperty FILLED = BooleanProperty.create("filled");

    protected AbstractCatBowlBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FILLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FILLED);
    }

    /**
     * Associates all owned, tamed cats within the configured radius with the bowl at bowlPos.
     * Returns the number of cats associated.
     */
    protected int associateCatsWithBowl(Level level, BlockPos bowlPos, Player player) {
        if (level.isClientSide()) {
            return 0;
        }
        if (!(level.getBlockEntity(bowlPos) instanceof AbstractCatBowlBlockEntity bowl)) {
            return 0;
        }

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
        int added = 0;
        int skipped = 0;

        for (Cat cat : cats) {
            if (!bowl.canAddCat(cat.getUUID())) {
                skipped++;
                continue;
            }
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
            added++;
        }

        if (added > 0) {
            player.sendSystemMessage(Component.translatable(
                    "message.vanillaplusadditions.cat_guardian.associated", added));
        }
        if (skipped > 0) {
            player.sendSystemMessage(Component.translatable(
                    "message.vanillaplusadditions.cat_guardian.station_full", skipped));
        }

        return added;
    }

    /**
     * Removes all cat associations from this bowl and clears bowl-pos on each associated cat.
     * Called when the block is about to be destroyed.
     */
    public static void clearAssociationsOnBreak(Level level, BlockPos pos,
                                                AbstractCatBowlBlockEntity bowl) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        List<UUID> cats = new ArrayList<>(bowl.getAssociatedCats());
        for (UUID catUUID : cats) {
            var entity = serverLevel.getEntity(catUUID);
            if (entity instanceof Cat cat) {
                cat.setData(CatGuardianModule.CAT_BOWL_POS.get(), Long.MIN_VALUE);
            }
        }
        // Unloaded cats self-heal: getBowlEntity() in the tick handler clears CAT_BOWL_POS
        // the next time the cat's chunk loads and the bowl is gone.
    }
}
