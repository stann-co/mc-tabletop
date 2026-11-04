package co.stann.tabletop;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Small puffs of coloured dust at a player's hands that everyone nearby can see, so it's plain at the table when
 * someone looks at cards (amber; a fainter speck for a glimpse) or really shuffles a deck (aqua).
 */
public final class Tells {
    private static final Vector3f AMBER = new Vector3f(1.0F, 0.7F, 0.15F);
    private static final DustParticleOptions PEEK = new DustParticleOptions(AMBER, 0.6F);
    private static final DustParticleOptions GLIMPSE = new DustParticleOptions(AMBER, 0.35F);
    private static final DustParticleOptions SHUFFLE = new DustParticleOptions(new Vector3f(0.25F, 0.85F, 1.0F), 0.7F);
    /** Ticks between puffs while someone keeps looking, so a long look is a steady trickle rather than a cloud. */
    private static final int PEEK_INTERVAL = 8;
    private static final Map<ServerPlayer, Long> lastPeek = new WeakHashMap<>();

    private Tells() {}

    /** The player looked at cards: a deck's contents, or a card they turned over. */
    public static void peek(ServerPlayer player) {
        if (due(player)) {
            puff(player, PEEK, 3);
        }
    }

    /** The player glimpsed a card's face by previewing it the other way up at a table: a fainter amber speck. */
    public static void glimpse(ServerPlayer player) {
        if (due(player)) {
            puff(player, GLIMPSE, 1);
        }
    }

    /** Whether it's time for another peek puff (see {@link #PEEK_INTERVAL}). */

    private static boolean due(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        Long last = lastPeek.get(player);
        if (last != null && now - last < PEEK_INTERVAL && now >= last) {
            return false;
        }
        lastPeek.put(player, now);
        return true;
    }

    /** The player shuffled a deck. */
    public static void shuffle(ServerPlayer player) {
        puff(player, SHUFFLE, 8);
    }

    private static void puff(ServerPlayer player, DustParticleOptions dust, int count) {
        // Just in front of the chest, where the cards are held, which still works for a player sitting in a chair.
        Vec3 at = player.getEyePosition().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(0.4)).subtract(0, 0.45, 0);
        ServerLevel level = player.serverLevel();
        level.sendParticles(dust, at.x, at.y, at.z, count, 0.12, 0.08, 0.12, 0);
    }
}
