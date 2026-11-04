package co.stann.tabletop.table;

import co.stann.tabletop.Backing;
import co.stann.tabletop.CardItem;
import co.stann.tabletop.DeckItem;
import co.stann.tabletop.Tells;
import co.stann.tabletop.api.TabletopApi;
import co.stann.tabletop.api.TabletopApi.Kind;
import co.stann.tabletop.net.FlipHeldPayload;
import co.stann.tabletop.net.PeekPayload;
import co.stann.tabletop.net.ShuffleDeckPayload;
import co.stann.tabletop.net.TableActionPayload;
import co.stann.tabletop.net.TableActionPayload.Action;
import co.stann.tabletop.table.TableObject.Chips;
import co.stann.tabletop.table.TableObject.Pile;
import co.stann.tabletop.table.TableObject.PlacedCard;
import co.stann.tabletop.table.TableObject.PlacedChip;
import co.stann.tabletop.table.TableObject.Token;
import co.stann.tabletop.table.TableSurface.Placed;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Carries out moves on play surfaces on the server. The client only says what it wants; everything is checked again
 * here (reach, the held item, whether the spot is free) before anything changes.
 */
public final class TableActions {
    /** Ticks between flips or shuffles of a held card or deck: quicker than anyone clicks, slower than a macro. */
    private static final int HELD_ACTION_COOLDOWN = 3;
    private static final Map<ServerPlayer, Long> lastHeldAction = new WeakHashMap<>();

    private TableActions() {}

    public static void handle(TableActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        Level level = player.level();
        Action action = payload.action();
        if (action == Action.PLACE || action == Action.PLACE_FLIPPED) {
            place(player, level, payload, action == Action.PLACE_FLIPPED);
            return;
        }
        // Check reach before looking anything up, so a client can't make the server load far-away chunks.
        int y = payload.pos().getY();
        if (!canReach(player, TableSurface.ownerPos(payload.gx(), y, payload.gz()))) {
            return;
        }
        Placed placed = TableSurface.objectAt(level, y, payload.gx(), payload.gz());
        if (placed == null) {
            return;
        }
        switch (action) {
            case TAKE_ONE -> {
                if (!takeOntoHeldCard(player, placed, false)) {
                    takeOne(player, placed);
                }
            }
            case TAKE_ALL -> {
                if (!takeOntoHeldCard(player, placed, true)) {
                    takeAll(player, placed);
                }
            }
            case FLIP -> flip(placed);
            case ROTATE -> rotate(placed, payload.rot());
            case SWEEP_TOP -> sweepPile(player, placed, false);
            case SWEEP_PILE -> sweepPile(player, placed, true);
            default -> {}
        }
    }

    public static void flipHeld(FlipHeldPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || tooSoon(player)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (TabletopApi.isCard(held)) {
            boolean faceUp = !CardItem.faceUp(held);
            CardItem.setFaceUp(held, faceUp);
            if (payload.all()) {
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    ItemStack card = player.getInventory().getItem(slot);
                    if (TabletopApi.isCard(card)) {
                        CardItem.setFaceUp(card, faceUp);
                    }
                }
                player.getInventory().setChanged();
            }
            playSound(player, SoundEvents.BOOK_PAGE_TURN, 1.2F);
            Tells.peek(player);
        }
    }

    public static void shuffleHeld(ShuffleDeckPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || tooSoon(player)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof DeckItem) {
            DeckItem.shuffle(held, player.getRandom());
            player.displayClientMessage(Component.translatable("message.tabletop.shuffled"), true);
            playSound(player, SoundEvents.BOOK_PAGE_TURN, 0.8F);
            playSound(player, SoundEvents.BOOK_PUT, 1.2F);
            Tells.shuffle(player);
        }
    }

    /** The player is looking at hidden cards. It only shows a puff, so there's nothing to check. */
    public static void peek(PeekPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && !player.isSpectator()) {
            if (payload.faint()) {
                Tells.glimpse(player);
            } else {
                Tells.peek(player);
            }
        }
    }

    /**
     * Whether the player flipped or shuffled what they hold only a moment ago. Each one plays sounds and a puff for
     * everyone around, so a macro or a modified client mustn't be able to fire them every tick.
     */
    private static boolean tooSoon(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        Long last = lastHeldAction.get(player);
        if (last != null && now - last < HELD_ACTION_COOLDOWN && now >= last) {
            return true;
        }
        lastHeldAction.put(player, now);
        return false;
    }

    private static boolean canReach(ServerPlayer player, BlockPos pos) {
        return player.canInteractWithBlock(pos, 1.5);
    }

    /** Puts down what the player holds; {@code flipped} lays cards the other way up from how they'd land by default. */
    private static void place(ServerPlayer player, Level level, TableActionPayload payload, boolean flipped) {
        ItemStack held = player.getMainHandItem();
        Kind kind = TabletopApi.kindOf(held);
        int y = payload.pos().getY();
        BlockPos owner = TableSurface.ownerPos(payload.gx(), y, payload.gz());
        if (kind == null || !canReach(player, owner)) {
            return;
        }
        // Remember the item before it's used up: placing your last card leaves an empty hand.
        Item item = held.getItem();
        Placed target = TableSurface.objectAt(level, y, payload.gx(), payload.gz());
        if (target != null && target.object().kind() == kind && kind != Kind.TOKENS) {
            if (target.object().accepts(held) && fill(target.object(), held, payload.amount(), flipped)) {
                target.table().changed();
                playPlaceSound(target.table(), target.object(), item);
            }
            return;
        }
        int rot = kind == Kind.CHIPS ? 0 : Math.floorMod(payload.rot(), TableSurface.ROTATIONS);
        if (!TableSurface.fits(level, y, kind, payload.gx(), payload.gz(), rot, null)
                || !(level.getBlockEntity(owner) instanceof PlaySurfaceBlockEntity table)) {
            return;
        }
        TableObject object = switch (kind) {
            case CARDS -> new Pile(payload.gx(), payload.gz(), rot);
            case CHIPS -> new Chips(payload.gx(), payload.gz());
            case TOKENS -> new Token(payload.gx(), payload.gz(), rot, item);
        };
        if (fill(object, held, payload.amount(), flipped)) {
            table.objects().add(object);
            table.changed();
            playPlaceSound(table, object, item);
        }
    }

    /** Moves what the player holds onto an object. Returns false if nothing could be added. */
    private static boolean fill(TableObject object, ItemStack held, int amount, boolean flipped) {
        if (object instanceof Pile pile) {
            // Cards landing on a pile turn the same way as the card they land on.
            Boolean landsFaceUp = pile.size() > 0 ? pile.top().faceUp() : null;
            if (held.getItem() instanceof DeckItem) {
                // The deck goes down in its order, so a deck nobody shuffled deals in order. On bare felt it's face-down.
                List<Item> cards = DeckItem.cards(held);
                if (cards.isEmpty()) {
                    return false;
                }
                boolean faceUp = (landsFaceUp != null && landsFaceUp) != flipped;
                Backing backing = Backing.of(held);
                cards.forEach(card -> pile.cards.add(new PlacedCard(card, faceUp, backing)));
            } else {
                boolean faceUp = (landsFaceUp != null ? landsFaceUp : CardItem.faceUp(held)) != flipped;
                pile.cards.add(new PlacedCard(held.getItem(), faceUp, Backing.of(held)));
            }
            held.shrink(1);
            return true;
        }
        if (object instanceof Chips chips) {
            int count = Math.min(Math.min(amount, held.getCount()), TableSurface.MAX_CHIPS - chips.size());
            if (count <= 0) {
                return false;
            }
            PlacedChip chip = PlacedChip.of(held);
            for (int i = 0; i < count; i++) {
                chips.chips.add(chip);
            }
            chips.sort();
            held.shrink(count);
            return true;
        }
        held.shrink(1); // a token already holds its item
        return true;
    }

    private static void takeOne(ServerPlayer player, Placed placed) {
        TableObject object = placed.object();
        if (object instanceof Token) {
            takeAll(player, placed);
            return;
        }
        ItemStack taken = object instanceof Pile pile ? pile.cards.removeLast().toItem() : ((Chips) object).chips.removeLast().toItem(1);
        tidy(placed);
        give(player, taken);
        playTakeSound(placed.table(), object);
    }

    /**
     * Holding a single card, taking from a pile picks the top card (or the whole pile) up onto it, so the card in hand
     * becomes a deck with the taken cards on top. Returns false if that can't be done (another back or label, or more
     * than a deck holds), and the cards are then taken the usual way.
     */
    private static boolean takeOntoHeldCard(ServerPlayer player, Placed placed, boolean wholePile) {
        ItemStack held = player.getMainHandItem();
        if (!(placed.object() instanceof Pile pile) || !TabletopApi.isCard(held) || held.getCount() != 1) {
            return false;
        }
        Backing backing = Backing.of(held);
        List<PlacedCard> taken = topCards(pile, wholePile);
        if (1 + taken.size() > DeckItem.MAX_CARDS || taken.stream().anyMatch(card -> !card.backing().equals(backing))) {
            return false;
        }
        List<Item> cards = new ArrayList<>(List.of(held.getItem()));
        taken.forEach(card -> cards.add(card.card()));
        taken.clear();
        tidy(placed);
        player.setItemInHand(InteractionHand.MAIN_HAND, DeckItem.stack(cards, backing));
        playTakeSound(placed.table(), pile);
        return true;
    }

    private static void takeAll(ServerPlayer player, Placed placed) {
        placed.table().objects().remove(placed.object());
        placed.table().changed();
        placed.object().toItems().forEach(stack -> give(player, stack));
        playTakeSound(placed.table(), placed.object());
    }

    private static void flip(Placed placed) {
        if (placed.object() instanceof Pile pile) {
            pile.flip();
            placed.table().changed();
            playSound(placed.table(), pile, SoundEvents.BOOK_PAGE_TURN, 1.0F);
        }
    }

    private static void rotate(Placed placed, int rot) {
        TableObject object = placed.object();
        int turned = Math.floorMod(rot, TableSurface.ROTATIONS);
        int y = placed.table().getBlockPos().getY();
        if (object.kind() != Kind.CHIPS && TableSurface.fits(placed.table().getLevel(), y, object.kind(), object.gx, object.gz, turned, object)) {
            object.rot = turned;
            placed.table().changed();
        }
    }

    /** Puts the top card of a pile, or the whole pile, on top of the held deck. */
    private static void sweepPile(ServerPlayer player, Placed placed, boolean wholePile) {
        ItemStack deck = player.getMainHandItem();
        if (!(deck.getItem() instanceof DeckItem) || !(placed.object() instanceof Pile pile)) {
            return;
        }
        List<PlacedCard> taken = topCards(pile, wholePile);
        Backing backing = Backing.of(deck);
        if (taken.stream().anyMatch(card -> !card.backing().equals(backing))) {
            player.displayClientMessage(Component.translatable("message.tabletop.other_back"), true);
            return;
        }
        if (DeckItem.cards(deck).size() + taken.size() > DeckItem.MAX_CARDS) {
            player.displayClientMessage(Component.translatable("message.tabletop.deck_full"), true);
            return;
        }
        DeckItem.add(deck, taken.stream().map(PlacedCard::card).toList());
        taken.clear();
        tidy(placed);
        playSound(placed.table(), pile, wholePile ? SoundEvents.BOOK_PUT : SoundEvents.BOOK_PAGE_TURN, 1.2F);
    }

    /** The top card of a pile, or the whole pile, as a live view: clearing it takes those cards off the pile. */
    private static List<PlacedCard> topCards(Pile pile, boolean wholePile) {
        return pile.cards.subList(wholePile ? 0 : pile.size() - 1, pile.size());
    }

    /** After taking from an object: an emptied object goes, and the table is saved and sent. */
    private static void tidy(Placed placed) {
        if (placed.object().size() == 0) {
            placed.table().objects().remove(placed.object());
        }
        placed.table().changed();
    }

    /** Puts a card in an empty main hand so it can be placed right away; chips join matching stacks first. */
    private static void give(ServerPlayer player, ItemStack stack) {
        boolean stacksWithSomething = player.getInventory().getSlotWithRemainingSpace(stack) >= 0;
        if (!stacksWithSomething && player.getMainHandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        } else if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void playPlaceSound(PlaySurfaceBlockEntity table, TableObject object, Item held) {
        switch (object.kind()) {
            case CHIPS -> playSound(table, object, SoundEvents.CHAIN_PLACE, 1.8F);
            case TOKENS -> playSound(table, object, SoundEvents.CHAIN_PLACE, 1.3F);
            case CARDS -> playSound(table, object, held instanceof DeckItem ? SoundEvents.BOOK_PUT : SoundEvents.BOOK_PAGE_TURN, 1.4F);
        }
    }

    private static void playTakeSound(PlaySurfaceBlockEntity table, TableObject object) {
        playSound(table, object, object.kind() == Kind.CARDS ? SoundEvents.BOOK_PAGE_TURN : SoundEvents.CHAIN_HIT, 1.6F);
    }

    /** Plays a sound at an object lying on {@code table}. */
    private static void playSound(PlaySurfaceBlockEntity table, TableObject object, SoundEvent sound, float pitch) {
        playSound(table.getLevel(), TableSurface.toWorld(object.gx), table.surfaceY(), TableSurface.toWorld(object.gz), sound, pitch);
    }

    /** Plays a sound at a player's hands. */
    private static void playSound(ServerPlayer player, SoundEvent sound, float pitch) {
        playSound(player.level(), player.getX(), player.getEyeY(), player.getZ(), sound, pitch);
    }

    private static void playSound(Level level, double x, double y, double z, SoundEvent sound, float pitch) {
        level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, 0.7F, pitch);
    }
}
