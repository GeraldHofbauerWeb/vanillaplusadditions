package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.block;

import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.AxolotlGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.blockentity.AbstractAxolotlBowlBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shared logic for AxolotlBowlBlock and AxolotlFeedingStationBlock: axolotl association on
 * shift+right-click. Both blocks are waterloggable — an underwater base is their natural habitat.
 */
public abstract class AbstractAxolotlBowlBlock extends net.minecraft.world.level.block.Block
        implements net.minecraft.world.level.block.EntityBlock, SimpleWaterloggedBlock {

    public static final BooleanProperty FILLED = BooleanProperty.create("filled");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    protected AbstractAxolotlBowlBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FILLED, false)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FILLED, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return this.defaultBlockState().setValue(WATERLOGGED, water);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    /**
     * Drops the block itself when broken. This mod's datapack loot-table JSONs don't load
     * reliably, so block drops are provided in code (see project convention) — without this the
     * bowl/station break but yield no item. Container contents are dropped separately in each
     * subclass's {@code onRemove}.
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(this));
    }

    /**
     * Associates all owned axolotls within the configured radius with the bowl at bowlPos.
     * Returns the number of axolotls associated.
     */
    protected int associateAxolotlsWithBowl(Level level, BlockPos bowlPos, Player player) {
        if (level.isClientSide()) {
            return 0;
        }
        if (!(level.getBlockEntity(bowlPos) instanceof AbstractAxolotlBowlBlockEntity bowl)) {
            return 0;
        }

        double radius = AxolotlGuardianModule.getAssociationRadius();
        AABB searchBox = new AABB(bowlPos).inflate(radius);

        List<Axolotl> axolotls = level.getEntitiesOfClass(Axolotl.class, searchBox, axolotl ->
                AxolotlGuardianModule.isOwnedBy(axolotl, player.getUUID())
        );

        if (axolotls.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.vanillaplusadditions.axolotl_guardian.no_axolotls"));
            return 0;
        }

        long newBowlLong = bowlPos.asLong();
        int added = 0;
        int skipped = 0;

        for (Axolotl axolotl : axolotls) {
            if (!bowl.canAddAxolotl(axolotl.getUUID())) {
                skipped++;
                continue;
            }
            // Detach axolotl from its previous bowl
            long oldBowlLong = axolotl.getData(AxolotlGuardianModule.AXOLOTL_BOWL_POS.get());
            if (oldBowlLong != Long.MIN_VALUE) {
                BlockPos oldPos = BlockPos.of(oldBowlLong);
                if (!oldPos.equals(bowlPos)
                        && level.getBlockEntity(oldPos) instanceof AbstractAxolotlBowlBlockEntity oldBowl) {
                    oldBowl.removeAxolotl(axolotl.getUUID());
                }
            }
            axolotl.setData(AxolotlGuardianModule.AXOLOTL_BOWL_POS.get(), newBowlLong);
            bowl.addAxolotl(axolotl.getUUID());
            AxolotlGuardianModule.broadcastOwnerSync(axolotl);
            added++;
        }

        if (added > 0) {
            player.sendSystemMessage(Component.translatable(
                    "message.vanillaplusadditions.axolotl_guardian.associated", added));
        }
        if (skipped > 0) {
            player.sendSystemMessage(Component.translatable(
                    "message.vanillaplusadditions.axolotl_guardian.station_full", skipped));
        }

        return added;
    }

    /**
     * Removes all axolotl associations from this bowl and clears bowl-pos on each associated
     * axolotl. Called when the block is about to be destroyed.
     */
    public static void clearAssociationsOnBreak(Level level, BlockPos pos,
                                                AbstractAxolotlBowlBlockEntity bowl) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        List<UUID> axolotls = new ArrayList<>(bowl.getAssociatedAxolotls());
        for (UUID axolotlUUID : axolotls) {
            var entity = serverLevel.getEntity(axolotlUUID);
            if (entity instanceof Axolotl axolotl) {
                axolotl.setData(AxolotlGuardianModule.AXOLOTL_BOWL_POS.get(), Long.MIN_VALUE);
                AxolotlGuardianModule.broadcastOwnerSync(axolotl);
            }
        }
        // Unloaded axolotls self-heal: getBowlEntity() in the tick handler clears the bowl pos
        // the next time the axolotl's chunk loads and the bowl is gone.
    }
}
