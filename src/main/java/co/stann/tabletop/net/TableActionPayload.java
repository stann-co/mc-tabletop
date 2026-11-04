package co.stann.tabletop.net;

import co.stann.tabletop.Tabletop;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Something a player does at a poker table, sent from client to server. {@code pos} is the table block they
 * aim at; {@code gx}/{@code gz} the grid point of the object (or of the new object, for PLACE).
 */
public record TableActionPayload(Action action, BlockPos pos, int gx, int gz, int rot, int amount) implements CustomPacketPayload {
    public enum Action {
        /** Put down the held item, or as many chips as {@code amount}, at {@code rot}. Merges into a matching pile. */
        PLACE,
        /** Like PLACE, but cards land the other way up from how they would by default. */
        PLACE_FLIPPED,
        TAKE_ONE,
        TAKE_ALL,
        FLIP,
        /** Turn an object to {@code rot}. */
        ROTATE,
        /** Put the top card of a pile on top of the held deck. */
        SWEEP_TOP,
        /** Sweep one pile of cards into the held deck. */
        SWEEP_PILE
    }

    public static final Type<TableActionPayload> TYPE = new Type<>(Tabletop.id("table_action"));
    public static final StreamCodec<FriendlyByteBuf, TableActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(TableActionPayload::write, TableActionPayload::new);

    private TableActionPayload(FriendlyByteBuf buf) {
        this(buf.readEnum(Action.class), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeBlockPos(pos);
        buf.writeVarInt(gx);
        buf.writeVarInt(gz);
        buf.writeVarInt(rot);
        buf.writeVarInt(amount);
    }

    @Override
    public Type<TableActionPayload> type() {
        return TYPE;
    }
}
