package co.stann.tabletop.table;

import co.stann.tabletop.client.Controls;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One block of poker table. Tables placed side by side join into one surface; only open sides get a wooden rail,
 * and only outer corners get legs. The felt height and the rail's height and width must match models/block/poker_table_*.json.
 */
public class PokerTableBlock extends PlaySurfaceBlock {
    public static final MapCodec<PokerTableBlock> CODEC = simpleCodec(PokerTableBlock::new);
    /** Shapes for every combination of open sides, indexed like {@link #openSides}. */
    private static final VoxelShape[] SHAPES = new VoxelShape[16];

    static {
        VoxelShape top = box(0, 9, 0, 16, 12, 16);
        VoxelShape[] rails = {box(0, 9, 0, 16, 13, 1), box(15, 9, 0, 16, 13, 16), box(0, 9, 15, 16, 13, 16), box(0, 9, 0, 1, 13, 16)};
        // Legs sit in the corner between a side and the next one clockwise: NE, SE, SW, NW.
        VoxelShape[] legs = {box(12, 0, 1, 15, 9, 4), box(12, 0, 12, 15, 9, 15), box(1, 0, 12, 4, 9, 15), box(1, 0, 1, 4, 9, 4)};
        for (int open = 0; open < 16; open++) {
            VoxelShape shape = top;
            for (int side = 0; side < 4; side++) {
                if ((open & 1 << side) != 0) {
                    shape = Shapes.or(shape, rails[side]);
                    if ((open & 1 << (side + 1) % 4) != 0) {
                        shape = Shapes.or(shape, legs[side]);
                    }
                }
            }
            SHAPES[open] = shape;
        }
    }

    public PokerTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<PokerTableBlock> codec() {
        return CODEC;
    }

    @Override
    public double surfaceHeight() {
        return 12 / 16.0;
    }

    @Override
    public int edgeWidth() {
        return 1;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[openSides(state)];
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Controls.append(tooltip, "table");
    }
}
