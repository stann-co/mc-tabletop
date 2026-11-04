package co.stann.tabletop.table;

import co.stann.tabletop.api.TabletopApi.Kind;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Geometry of play surfaces, shared by the client (ghost preview, aiming) and the server (checking moves).
 *
 * <p>Distances are in "table pixels" (1/16 block) unless noted. Objects sit on a grid of {@link #GRID} pixels in
 * world coordinates, so they can lie across the border between two blocks of the same surface. Whether an object is
 * on open surface is checked on the whole pixel cells its footprint covers; whether two objects overlap is checked on
 * their true shapes, so cards can lie edge to edge.
 */
public final class TableSurface {
    /** Grid step: objects snap to every pixel, a sixteenth of a block. */
    public static final int GRID = 1;
    public static final int GRID_PER_BLOCK = 16 / GRID;
    /** Rotation steps in a full turn, 15 degrees each. */
    public static final int ROTATIONS = 24;

    /** Cards are drawn at a quarter: the 12x16 card in a 16x16 texture lies 3 by 4 pixels on the table. */
    public static final float CARD_SCALE = 0.25F;
    public static final float CARD_WIDTH = 3;
    public static final float CARD_LENGTH = 4;
    public static final float CARD_THICKNESS = 0.025F;
    /** Chips are drawn at a sixth: the 12 pixel wide disc in a 16x16 texture is 2 pixels across on the table. */
    public static final float CHIP_SCALE = 1 / 6F;
    public static final float CHIP_RADIUS = 1;
    public static final float CHIP_THICKNESS = CHIP_SCALE;
    public static final int MAX_CHIPS = 20;
    /** Tokens are drawn at a quarter: the 12 pixel wide disc in a 16x16 texture is 3 pixels across on the table. */
    public static final float TOKEN_SCALE = 0.25F;
    public static final float TOKEN_RADIUS = 1.5F;
    public static final float TOKEN_THICKNESS = TOKEN_SCALE;

    private TableSurface() {}

    /** The block that stores an object centred at a grid point. */
    public static BlockPos ownerPos(int gx, int y, int gz) {
        return new BlockPos(Math.floorDiv(gx, GRID_PER_BLOCK), y, Math.floorDiv(gz, GRID_PER_BLOCK));
    }

    /** A grid coordinate as a world coordinate in blocks. */
    public static double toWorld(int g) {
        return g / (double) GRID_PER_BLOCK;
    }

    /** A rotation in radians, clockwise seen from above. */
    public static double angle(int rot) {
        return Math.PI * 2 * rot / ROTATIONS;
    }

    /** How far a point at {@code dx, dz} from a card's centre lies across the card. */
    private static double across(int rot, double dx, double dz) {
        return dx * Math.cos(angle(rot)) + dz * Math.sin(angle(rot));
    }

    /** How far a point at {@code dx, dz} from a card's centre lies along the card. */
    private static double along(int rot, double dx, double dz) {
        return -dx * Math.sin(angle(rot)) + dz * Math.cos(angle(rot));
    }

    /** Distance from an object's centre to the furthest point of its footprint. */
    static float radius(Kind kind) {
        return switch (kind) {
            case CARDS -> 2.5F; // half the diagonal of a 3x4 card
            case CHIPS -> CHIP_RADIUS;
            case TOKENS -> TOKEN_RADIUS;
        };
    }

    /**
     * Whether the pixel cell centred at {@code dx, dz} from an object's centre is part of its footprint. A disc claims
     * every cell it covers any part of; a card claims the cells whose centre is on it or right on its edge, so a card
     * 3 pixels wide claims every cell its edges cut through.
     */
    private static boolean contains(Kind kind, int rot, double dx, double dz) {
        if (kind != Kind.CARDS) {
            // The point of the cell nearest the disc's centre.
            double nearX = Math.max(Math.abs(dx) - 0.5, 0);
            double nearZ = Math.max(Math.abs(dz) - 0.5, 0);
            return nearX * nearX + nearZ * nearZ < radius(kind) * radius(kind);
        }
        return Math.abs(across(rot, dx, dz)) <= CARD_WIDTH / 2 + 1e-6 && Math.abs(along(rot, dx, dz)) < CARD_LENGTH / 2 - 1e-6;
    }

    /** The pixel cells an object covers: those whose centre lies on its footprint. */
    private static LongSet cells(Kind kind, int gx, int gz, int rot) {
        int cx = gx * GRID;
        int cz = gz * GRID;
        int reach = (int) Math.ceil(radius(kind));
        LongSet cells = new LongOpenHashSet();
        for (int px = cx - reach; px < cx + reach; px++) {
            for (int pz = cz - reach; pz < cz + reach; pz++) {
                if (contains(kind, rot, px + 0.5 - cx, pz + 0.5 - cz)) {
                    cells.add((long) px << 32 | (pz & 0xFFFFFFFFL));
                }
            }
        }
        return cells;
    }

    /** The play surface block at a position, or null if there is none. */
    @Nullable
    public static PlaySurfaceBlock surface(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof PlaySurfaceBlock surface ? surface : null;
    }

    /** World height of the playing surface of the block at {@code pos}, which must be a play surface. */
    public static double surfaceY(BlockGetter level, BlockPos pos) {
        PlaySurfaceBlock surface = surface(level, pos);
        return pos.getY() + (surface != null ? surface.surfaceHeight() : 1);
    }

    /**
     * Whether a pixel cell is open surface of type {@code type}: over a block of that type, and not on the edge band
     * (a table's rail) of a side with no block of the same type beyond it.
     */
    public static boolean isFelt(BlockGetter level, PlaySurfaceBlock type, int y, int px, int pz) {
        BlockPos pos = new BlockPos(Math.floorDiv(px, 16), y, Math.floorDiv(pz, 16));
        if (surface(level, pos) != type) {
            return false;
        }
        int edge = type.edgeWidth();
        if (edge == 0) {
            return true;
        }
        int x = Math.floorMod(px, 16);
        int z = Math.floorMod(pz, 16);
        return (z >= edge || surface(level, pos.north()) == type)
                && (z < 16 - edge || surface(level, pos.south()) == type)
                && (x >= edge || surface(level, pos.west()) == type)
                && (x < 16 - edge || surface(level, pos.east()) == type);
    }

    /** An object together with the block that stores it. */
    public record Placed(PlaySurfaceBlockEntity table, TableObject object) {}

    /** Every object stored in play surfaces up to {@code radius} blocks from {@code center} at the same height. */
    public static List<Placed> objectsAround(BlockGetter level, BlockPos center, int radius) {
        List<Placed> found = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (level.getBlockEntity(center.offset(dx, 0, dz)) instanceof PlaySurfaceBlockEntity table) {
                    table.objects().forEach(object -> found.add(new Placed(table, object)));
                }
            }
        }
        return found;
    }

    /** The object centred exactly on a grid point, if there is one. */
    @Nullable
    public static Placed objectAt(BlockGetter level, int y, int gx, int gz) {
        if (level.getBlockEntity(ownerPos(gx, y, gz)) instanceof PlaySurfaceBlockEntity table) {
            for (TableObject object : table.objects()) {
                if (object.gx == gx && object.gz == gz) {
                    return new Placed(table, object);
                }
            }
        }
        return null;
    }

    /** Whether an object at a spot lies entirely on open surface of the same type as the block under its centre. */
    private static boolean onFelt(BlockGetter level, int y, Kind kind, int gx, int gz, int rot) {
        PlaySurfaceBlock type = surface(level, ownerPos(gx, y, gz));
        if (type == null) {
            return false;
        }
        for (long cell : cells(kind, gx, gz, rot)) {
            if (!isFelt(level, type, y, (int) (cell >> 32), (int) cell)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether an object fits at a spot: entirely on open surface of the block under its centre, and not overlapping
     * anything other than {@code ignore}.
     */
    public static boolean fits(BlockGetter level, int y, Kind kind, int gx, int gz, int rot, @Nullable TableObject ignore) {
        if (!onFelt(level, y, kind, gx, gz, rot)) {
            return false;
        }
        // Footprints reach at most a few pixels from their centre, so only blocks right next door can hold an overlap.
        for (Placed placed : objectsAround(level, ownerPos(gx, y, gz), 1)) {
            TableObject other = placed.object();
            if (other != ignore && overlaps(kind, gx, gz, rot, other.kind(), other.gx, other.gz, other.rot)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether two objects overlap, taken as their true shapes (a turned rectangle for a card, a disc otherwise).
     * Shapes that only touch don't overlap, so cards can lie edge to edge.
     */
    static boolean overlaps(Kind kindA, int ax, int az, int rotA, Kind kindB, int bx, int bz, int rotB) {
        double dx = (bx - ax) * GRID;
        double dz = (bz - az) * GRID;
        if (Math.hypot(dx, dz) >= radius(kindA) + radius(kindB)) {
            return false;
        }
        boolean cardA = kindA == Kind.CARDS;
        boolean cardB = kindB == Kind.CARDS;
        if (!cardA && !cardB) {
            return true; // two discs, closer than their radii add up to
        }
        if (cardA != cardB) {
            // A disc against a card: the point of the card nearest the disc's centre must lie inside the disc.
            int rot = cardA ? rotA : rotB;
            double nearAcross = Math.max(Math.abs(across(rot, dx, dz)) - CARD_WIDTH / 2, 0);
            double nearAlong = Math.max(Math.abs(along(rot, dx, dz)) - CARD_LENGTH / 2, 0);
            double r = radius(cardA ? kindB : kindA);
            return nearAcross * nearAcross + nearAlong * nearAlong < r * r - 1e-6;
        }
        // Two cards: separating axis test on the sides of both.
        double[][] axes = {cardAxis(rotA, false), cardAxis(rotA, true), cardAxis(rotB, false), cardAxis(rotB, true)};
        for (double[] axis : axes) {
            double gap = Math.abs(dx * axis[0] + dz * axis[1]);
            if (gap >= projectedHalf(rotA, axis) + projectedHalf(rotB, axis) - 1e-6) {
                return false;
            }
        }
        return true;
    }

    /** A unit vector across a card at {@code rot}, or along it. */
    private static double[] cardAxis(int rot, boolean along) {
        double angle = angle(rot);
        return along ? new double[] {-Math.sin(angle), Math.cos(angle)} : new double[] {Math.cos(angle), Math.sin(angle)};
    }

    /** Half the length of a card's shadow on a unit axis. */
    private static double projectedHalf(int rot, double[] axis) {
        return CARD_WIDTH / 2 * Math.abs(across(rot, axis[0], axis[1])) + CARD_LENGTH / 2 * Math.abs(along(rot, axis[0], axis[1]));
    }

    /**
     * Whether an object stored at {@code owner} still lies entirely on its surface (it doesn't once a block under it
     * goes). An object is always stored by the block under its centre.
     */
    public static boolean supported(BlockGetter level, BlockPos owner, TableObject object) {
        return onFelt(level, owner.getY(), object.kind(), object.gx, object.gz, object.rot);
    }

    /**
     * The nearest object a ray hits within {@code reach} blocks, among surfaces around {@code table}.
     * Each object is tested as a box turned with it, so aiming at a rotated card is exact.
     */
    @Nullable
    public static Placed raycast(BlockGetter level, BlockPos table, Vec3 from, Vec3 direction, double reach) {
        Vec3 to = from.add(direction.scale(reach));
        Placed best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Placed placed : objectsAround(level, table, 2)) {
            TableObject object = placed.object();
            Vec3 centre = new Vec3(toWorld(object.gx), placed.table().surfaceY(), toWorld(object.gz));
            double halfWidth = (object.kind() == Kind.CARDS ? CARD_WIDTH / 2 : radius(object.kind())) / 16;
            double halfLength = (object.kind() == Kind.CARDS ? CARD_LENGTH / 2 : radius(object.kind())) / 16;
            double height = Math.max(object.height(), 0.25) / 16;
            AABB box = new AABB(-halfWidth, 0, -halfLength, halfWidth, height, halfLength);
            Optional<Vec3> hit = box.clip(toLocal(from, centre, object.rot), toLocal(to, centre, object.rot));
            if (hit.isPresent()) {
                // Rotating about the centre keeps distances, so compare in the object's own frame.
                double distance = hit.get().distanceToSqr(toLocal(from, centre, object.rot));
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = placed;
                }
            }
        }
        return best;
    }

    /** A world point in an object's frame: centred on it, x across the card and z along it. */
    private static Vec3 toLocal(Vec3 point, Vec3 centre, int rot) {
        double dx = point.x - centre.x;
        double dz = point.z - centre.z;
        return new Vec3(across(rot, dx, dz), point.y - centre.y, along(rot, dx, dz));
    }
}
