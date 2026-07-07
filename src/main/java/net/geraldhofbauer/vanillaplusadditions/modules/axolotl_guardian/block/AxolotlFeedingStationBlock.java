package net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.block;

import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.AxolotlGuardianModule;
import net.geraldhofbauer.vanillaplusadditions.modules.axolotl_guardian.blockentity.AxolotlFeedingStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.Containers;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class AxolotlFeedingStationBlock extends AbstractAxolotlBowlBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<AxolotlStationSkin> SKIN =
            EnumProperty.create("skin", AxolotlStationSkin.class);

    // Hitbox shapes matching the model geometry (glass at north face, solid bowl at south).
    // EAST/SOUTH/WEST are 90°/180°/270° CW rotations of NORTH derived with:
    //   90° CW:  new=[16-maxZ,16-minZ] x [minX,maxX]
    //  180°:     new=[16-maxX,16-minX] x [16-maxZ,16-minZ]
    //  270° CW:  new=[minZ,maxZ] x [16-maxX,16-minX]
    private static final VoxelShape SHAPE_N = Shapes.or(
        Block.box(0, 0, 0, 16, 2, 16),  // floor
        Block.box(0, 5, 0, 16, 16, 2),  // glass front wall (full width)
        Block.box(0, 5, 2, 2, 16, 9),  // glass left pane
        Block.box(14, 5, 2, 16, 16, 9),  // glass right pane
        Block.box(0, 2, 7, 16, 5, 9),  // divider lower
        Block.box(2, 5, 7, 14, 16, 9),  // divider upper
        Block.box(0, 2, 9, 2, 5, 14),  // bowl left wall
        Block.box(14, 2, 9, 16, 5, 14),  // bowl right wall
        Block.box(0, 2, 14, 16, 5, 16),  // bowl back wall
        Block.box(0, 2, 0, 16, 5, 2),  // front bowl base
        Block.box(0, 2, 2, 2, 5, 7),  // left bowl base
        Block.box(14, 2, 2, 16, 5, 7)   // right bowl base
    );
    private static final VoxelShape SHAPE_E = Shapes.or(
        Block.box(0, 0, 0, 16, 2, 16),
        Block.box(14, 5, 0, 16, 16, 16),  // glass front wall → east face
        Block.box(7, 5, 0, 14, 16, 2),  // glass left pane  → north portion
        Block.box(7, 5, 14, 14, 16, 16),  // glass right pane → south portion
        Block.box(7, 2, 0, 9, 5, 16),  // divider lower
        Block.box(7, 5, 2, 9, 16, 14),  // divider upper
        Block.box(2, 2, 0, 7, 5, 2),  // bowl left wall  → north portion
        Block.box(2, 2, 14, 7, 5, 16),  // bowl right wall → south portion
        Block.box(0, 2, 0, 2, 5, 16),  // bowl back wall  → west face
        Block.box(14, 2, 0, 16, 5, 16),  // front bowl base → east face
        Block.box(9, 2, 0, 14, 5, 2),  // left bowl base  → north portion
        Block.box(9, 2, 14, 14, 5, 16)   // right bowl base → south portion
    );
    private static final VoxelShape SHAPE_S = Shapes.or(
        Block.box(0, 0, 0, 16, 2, 16),
        Block.box(0, 5, 14, 16, 16, 16),  // glass front wall → south face
        Block.box(14, 5, 7, 16, 16, 14),  // glass left pane  → east side
        Block.box(0, 5, 7, 2, 16, 14),  // glass right pane → west side
        Block.box(0, 2, 7, 16, 5, 9),  // divider lower (symmetric)
        Block.box(2, 5, 7, 14, 16, 9),  // divider upper (symmetric)
        Block.box(14, 2, 2, 16, 5, 7),  // bowl left wall  → east side
        Block.box(0, 2, 2, 2, 5, 7),  // bowl right wall → west side
        Block.box(0, 2, 0, 16, 5, 2),  // bowl back wall  → north face
        Block.box(0, 2, 14, 16, 5, 16),  // front bowl base → south face
        Block.box(14, 2, 9, 16, 5, 14),  // left bowl base  → east portion
        Block.box(0, 2, 9, 2, 5, 14)   // right bowl base → west portion
    );
    private static final VoxelShape SHAPE_W = Shapes.or(
        Block.box(0, 0, 0, 16, 2, 16),
        Block.box(0, 5, 0, 2, 16, 16),  // glass front wall → west face
        Block.box(2, 5, 14, 9, 16, 16),  // glass left pane  → south portion
        Block.box(2, 5, 0, 9, 16, 2),  // glass right pane → north portion
        Block.box(7, 2, 0, 9, 5, 16),  // divider lower
        Block.box(7, 5, 2, 9, 16, 14),  // divider upper
        Block.box(9, 2, 14, 14, 5, 16),  // bowl left wall  → south portion
        Block.box(9, 2, 0, 14, 5, 2),  // bowl right wall → north portion
        Block.box(14, 2, 0, 16, 5, 16),  // bowl back wall  → east face
        Block.box(0, 2, 0, 2, 5, 16),  // front bowl base → west face
        Block.box(2, 2, 14, 7, 5, 16),  // left bowl base  → south portion
        Block.box(2, 2, 0, 7, 5, 2)   // right bowl base → north portion
    );

    public AxolotlFeedingStationBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FILLED, false)
                .setValue(WATERLOGGED, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(SKIN, AxolotlStationSkin.ORIGINAL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING, SKIN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return this.defaultBlockState()
                .setValue(WATERLOGGED, water)
                .setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case EAST  -> SHAPE_E;
            case SOUTH -> SHAPE_S;
            case WEST  -> SHAPE_W;
            default    -> SHAPE_N;
        };
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AxolotlFeedingStationBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide() || blockEntityType != AxolotlGuardianModule.AXOLOTL_FEEDING_STATION_BE.get()) {
            return null;
        }
        return (lvl, pos, blockState, be) ->
                AxolotlFeedingStationBlockEntity.serverTick(
                        lvl, pos, blockState, (AxolotlFeedingStationBlockEntity) be);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!AxolotlGuardianModule.isAxolotlFood(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level.getBlockEntity(pos) instanceof AxolotlFeedingStationBlockEntity station)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide()) {
            while (!stack.isEmpty() && station.insertFish(stack.copyWithCount(1), false)) {
                if (!player.isCreative()) {
                    stack.shrink(1);
                } else {
                    break; // creative: just insert one to avoid infinite loop
                }
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AxolotlFeedingStationBlockEntity station)) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            associateAxolotlsWithBowl(level, pos, player);
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        // Non-sneaking → open inventory GUI
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            sp.openMenu(station, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof AxolotlFeedingStationBlockEntity station) {
            dropHandler(level, pos, station.getInventory());
            dropHandler(level, pos, station.getLootInventory());
            dropHandler(level, pos, station.getSkinInventory());
            clearAssociationsOnBreak(level, pos, station);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof AxolotlFeedingStationBlockEntity station)) {
            return 0;
        }
        int axolotlCount = station.getAssociatedAxolotls().size();
        int maxAxolotls = AxolotlGuardianModule.getMaxAxolotlsPerStation();
        if (maxAxolotls <= 0) {
            return 0;
        }
        return Math.floorDiv(axolotlCount * 15, maxAxolotls);
    }

    private static void dropHandler(Level level, BlockPos pos, ItemStackHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if (!s.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), s);
            }
        }
    }
}
