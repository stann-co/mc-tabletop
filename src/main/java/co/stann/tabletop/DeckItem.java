package co.stann.tabletop;

import co.stann.tabletop.api.TabletopApi;
import co.stann.tabletop.client.Controls;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A deck of cards, in order: the last card in the list is the top of the deck. It holds any cards (items in
 * {@link TabletopApi#CARDS}), all with the deck's back theme and label ({@link Backing}); other cards can't go in.
 * A new deck holds the 52 standard cards sorted, so it wants shuffling (modifier + right-click while holding it, or
 * the Shuffle button in the deck screen) before a game.
 *
 * <p>In an inventory it acts like a stack of its cards: it cuts, deals, splits and combines (see {@link InventoryClicks}).
 * Hovering it with the modifier held shows every card inside, top card first.
 */
public class DeckItem extends Item {
    /** The most cards a deck holds, as many as a stack of items: a full deck with room for extra jokers or cards. */
    public static final int MAX_CARDS = 64;

    public DeckItem(Properties properties) {
        super(properties);
    }

    public static List<Item> cards(ItemStack deck) {
        List<Item> cards = deck.get(Tabletop.DECK_CARDS);
        return cards != null ? cards : Card.fullDeck();
    }

    public static void setCards(ItemStack deck, List<Item> cards) {
        deck.set(Tabletop.DECK_CARDS, List.copyOf(cards));
    }

    public static void shuffle(ItemStack deck, RandomSource random) {
        List<Item> cards = new ArrayList<>(cards(deck));
        Util.shuffle(cards, random);
        setCards(deck, cards);
    }

    public static ItemStack stack(Collection<Item> cards, Backing backing) {
        ItemStack deck = new ItemStack(Tabletop.DECK.get());
        deck.set(Tabletop.DECK_CARDS, List.copyOf(cards));
        backing.applyTo(deck);
        return deck;
    }

    /** Cards as an item: nothing, a single face-up card, or a deck. A deck always holds at least two cards. */
    public static ItemStack ofCards(List<Item> cards, Backing backing) {
        return switch (cards.size()) {
            case 0 -> ItemStack.EMPTY;
            case 1 -> CardItem.stack(cards.getFirst(), true, backing);
            default -> stack(cards, backing);
        };
    }

    /** The deck as it should be held: down to one card it becomes that card, and an empty deck is used up. */
    public static ItemStack settle(ItemStack deck) {
        List<Item> cards = cards(deck);
        return cards.size() >= 2 ? deck : ofCards(cards, Backing.of(deck));
    }

    /** Takes the top card off the deck as a face-up card with the deck's backing, or returns an empty stack. */
    public static ItemStack takeTop(ItemStack deck) {
        List<Item> cards = new ArrayList<>(cards(deck));
        if (cards.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Item card = cards.removeLast();
        setCards(deck, cards);
        return CardItem.stack(card, true, Backing.of(deck));
    }

    /** Puts cards on top of the deck, as many as fit. */
    public static void add(ItemStack deck, Collection<Item> added) {
        List<Item> cards = new ArrayList<>(cards(deck));
        added.stream().limit(Math.max(0, MAX_CARDS - cards.size())).forEach(cards::add);
        setCards(deck, cards);
    }

    /** Opens the deck screen, where the cards can be rearranged, sorted or shuffled. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack deck = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
            // The screen shows a live card count of its own, so its title is the name without one, or the deck's label.
            String label = TabletopApi.labelOf(deck);
            Component title = deck.has(DataComponents.CUSTOM_NAME) ? deck.getHoverName()
                    : label.isEmpty() ? super.getName(deck) : Component.literal(label);
            serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, p) -> new DeckMenu(id, inventory, slot), title),
                    buf -> buf.writeVarInt(slot));
        }
        return InteractionResultHolder.sidedSuccess(deck, level.isClientSide);
    }

    /** The name with the number of cards, e.g. "Deck of Cards x52". */
    @Override
    public Component getName(ItemStack deck) {
        return Component.translatable("item.tabletop.deck.count", super.getName(deck), cards(deck).size());
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack deck) {
        // The cards only show while the modifier is held, so nobody sees them by just hovering over the deck.
        List<Item> cards = cards(deck);
        if (cards.isEmpty() || !Controls.showDeckCards()) {
            return Optional.empty();
        }
        Controls.peeking(false);
        return Optional.of(new DeckContents(cards, Backing.of(deck)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CardItem.appendBack(stack, tooltip);
        CardItem.appendLabel(stack, tooltip);
        Controls.appendDeckHint(tooltip);
        Controls.append(tooltip, "deck");
    }

    /** The cards shown when hovering a deck, drawn by the client's deck tooltip. Bottom to top, like the deck. */
    public record DeckContents(List<Item> cards, Backing backing) implements TooltipComponent {}
}
