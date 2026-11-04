package co.stann.tabletop.net;

import co.stann.tabletop.Tabletop;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The player is looking at hidden cards, so the others should see them peeking: through a deck's tooltip, or, when
 * {@code faint}, previewing a card or deck the other way up at a table.
 */
public record PeekPayload(boolean faint) implements CustomPacketPayload {
    public static final Type<PeekPayload> TYPE = new Type<>(Tabletop.id("peek"));
    public static final StreamCodec<ByteBuf, PeekPayload> STREAM_CODEC = ByteBufCodecs.BOOL.map(PeekPayload::new, PeekPayload::faint);

    @Override
    public Type<PeekPayload> type() {
        return TYPE;
    }
}
