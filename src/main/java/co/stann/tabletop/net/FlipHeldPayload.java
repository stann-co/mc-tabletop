package co.stann.tabletop.net;

import co.stann.tabletop.Tabletop;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Turns over the card in the player's main hand; with {@code all}, every other card they carry turns to match it. */
public record FlipHeldPayload(boolean all) implements CustomPacketPayload {
    public static final Type<FlipHeldPayload> TYPE = new Type<>(Tabletop.id("flip_held"));
    public static final StreamCodec<ByteBuf, FlipHeldPayload> STREAM_CODEC = ByteBufCodecs.BOOL.map(FlipHeldPayload::new, FlipHeldPayload::all);

    @Override
    public Type<FlipHeldPayload> type() {
        return TYPE;
    }
}
