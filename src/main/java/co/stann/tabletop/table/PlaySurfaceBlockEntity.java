package co.stann.tabletop.table;

import co.stann.tabletop.Tabletop;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds the objects whose centre lies over this play surface block (a poker table, a playmat, ...). */
public class PlaySurfaceBlockEntity extends BlockEntity {
    private final List<TableObject> objects = new ArrayList<>();

    public PlaySurfaceBlockEntity(BlockPos pos, BlockState state) {
        super(Tabletop.PLAY_SURFACE_ENTITY.get(), pos, state);
    }

    /** The objects stored here. Call {@link #changed()} after modifying them. */
    public List<TableObject> objects() {
        return objects;
    }

    /** World height of this block's playing surface. */
    public double surfaceY() {
        return worldPosition.getY() + ((PlaySurfaceBlock) getBlockState().getBlock()).surfaceHeight();
    }

    /** Saves and sends the new state to nearby players. */
    public void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Knocks off anything that no longer lies entirely on the surface, e.g. after a block under it was removed. */
    public void dropUnsupported() {
        boolean dropped = false;
        for (Iterator<TableObject> it = objects.iterator(); it.hasNext(); ) {
            TableObject object = it.next();
            if (!TableSurface.supported(level, worldPosition, object)) {
                it.remove();
                drop(object);
                dropped = true;
            }
        }
        if (dropped) {
            changed();
        }
    }

    public void dropAll() {
        objects.forEach(this::drop);
        objects.clear();
    }

    private void drop(TableObject object) {
        double x = TableSurface.toWorld(object.gx);
        double z = TableSurface.toWorld(object.gz);
        object.toItems().forEach(stack -> Containers.dropItemStack(level, x, surfaceY() + 0.1, z, stack));
    }

    private ListTag write(boolean censored) {
        ListTag list = new ListTag();
        objects.forEach(object -> list.add(object.save(censored)));
        return list;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("objects", write(false));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        objects.clear();
        ListTag list = tag.getList("objects", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            TableObject object = TableObject.load(list.getCompound(i));
            if (object != null) {
                objects.add(object);
            }
        }
    }

    /** What clients get: hidden cards are left out, see {@link TableObject#save}. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("objects", write(true));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
