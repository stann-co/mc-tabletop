package co.stann.tabletop.table;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A block players lay cards, chips and tokens on, such as the poker table and the playmat. Placing, taking and
 * drawing what lies on it works the same for every subclass. A subclass says where its playing surface is
 * ({@link #surfaceHeight}) and how wide its edge is ({@link #edgeWidth}), and provides its shape and model.
 *
 * <p>Blocks of the same type side by side join into one surface. Each knows which sides have a matching neighbour
 * (the north/east/south/west properties), and objects can lie across the border between two of them.
 */
public abstract class PlaySurfaceBlock extends BaseEntityBlock {
    protected static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    protected PlaySurfaceBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (Direction side : SIDES) {
            state = state.setValue(connection(side), false);
        }
        registerDefaultState(state);
    }

    /** Height of the playing surface within the block, in blocks. */
    public abstract double surfaceHeight();

    /** Width of the band along edges with no matching neighbour that nothing can be placed on, in pixels. */
    public abstract int edgeWidth();

    /** The property saying whether a matching block is next to this one on {@code side}. */
    public static BooleanProperty connection(Direction side) {
        return PipeBlock.PROPERTY_BY_DIRECTION.get(side);
    }

    /** The sides with no matching neighbour, as one bit per side in north, east, south, west order. */
    protected static int openSides(BlockState state) {
        int open = 0;
        for (int side = 0; side < 4; side++) {
            if (!state.getValue(connection(SIDES[side]))) {
                open |= 1 << side;
            }
        }
        return open;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        for (Direction side : SIDES) {
            builder.add(connection(side));
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction side : SIDES) {
            state = state.setValue(connection(side), context.getLevel().getBlockState(context.getClickedPos().relative(side)).is(this));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (side.getAxis().isHorizontal()) {
            return state.setValue(connection(side), neighbour.is(this));
        }
        return state;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlaySurfaceBlockEntity(pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean removed = !state.is(newState.getBlock());
        if (removed && level.getBlockEntity(pos) instanceof PlaySurfaceBlockEntity surface) {
            surface.dropAll();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!removed) {
            return;
        }
        // Things stored by the blocks around this one may have been lying partly on it.

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (level.getBlockEntity(pos.offset(dx, 0, dz)) instanceof PlaySurfaceBlockEntity neighbour) {
                    neighbour.dropUnsupported();
                }
            }
        }
    }
}
