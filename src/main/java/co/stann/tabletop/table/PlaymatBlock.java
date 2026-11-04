package co.stann.tabletop.table;

import co.stann.tabletop.client.Controls;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A thin felt mat that turns the top of any block into a play surface. Like carpet it needs something under it,
 * and pops off (dropping what lies on it) when that block goes.
 */
public class PlaymatBlock extends PlaySurfaceBlock {
    public static final MapCodec<PlaymatBlock> CODEC = simpleCodec(PlaymatBlock::new);
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 1, 16);

    public PlaymatBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<PlaymatBlock> codec() {
        return CODEC;
    }

    @Override
    public double surfaceHeight() {
        return 1 / 16.0;
    }

    @Override
    public int edgeWidth() {
        return 0;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return !level.isEmptyBlock(pos.below());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (side == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, side, neighbour, level, pos, neighbourPos);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Controls.append(tooltip, "playmat");
    }
}
