package co.stann.tabletop.net;

import co.stann.tabletop.Tabletop;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Shuffles the deck in the player's main hand. */
public record ShuffleDeckPayload() implements CustomPacketPayload {
    public static final ShuffleDeckPayload INSTANCE = new ShuffleDeckPayload();
    public static final Type<ShuffleDeckPayload> TYPE = new Type<>(Tabletop.id("shuffle_deck"));
    public static final StreamCodec<ByteBuf, ShuffleDeckPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<ShuffleDeckPayload> type() {
        return TYPE;
    }
}
