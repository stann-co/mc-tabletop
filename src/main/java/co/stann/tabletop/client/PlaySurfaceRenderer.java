package co.stann.tabletop.client;

import co.stann.tabletop.table.PlaySurfaceBlockEntity;
import co.stann.tabletop.table.TableObject;
import co.stann.tabletop.table.TableSurface;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Draws the cards, chips and tokens lying on a play surface block (a poker table, a playmat, ...). */
public class PlaySurfaceRenderer implements BlockEntityRenderer<PlaySurfaceBlockEntity> {
    /** Cards are a few pixels across, so past this many blocks there's nothing left worth drawing. */
    private static final int VIEW_DISTANCE = 32;

    public PlaySurfaceRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PlaySurfaceBlockEntity surface, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (surface.objects().isEmpty()) {
            return; // most blocks of a big table hold nothing
        }
        BlockPos pos = surface.getBlockPos();
        // Light the objects like the air above the surface rather than the inside of the block.
        int lightAbove = LevelRenderer.getLightColor(surface.getLevel(), pos.above());
        double height = surface.surfaceY() - pos.getY();
        for (TableObject object : surface.objects()) {
            pose.pushPose();
            pose.translate(TableSurface.toWorld(object.gx) - pos.getX(), height, TableSurface.toWorld(object.gz) - pos.getZ());
            TableRendering.render(object, pose, buffers, surface.getLevel(), lightAbove);
            pose.popPose();
        }
    }

    /** Objects can hang over the neighbouring blocks and stand up to 20 chips tall. */
    @Override
    public AABB getRenderBoundingBox(PlaySurfaceBlockEntity surface) {
        return new AABB(surface.getBlockPos()).inflate(0.5, 0, 0.5).expandTowards(0, 1, 0);
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }
}
