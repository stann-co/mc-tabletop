package co.stann.tabletop;

import co.stann.tabletop.api.TabletopApi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;

/**
 * The 52 standard playing cards (each its own item, e.g. tabletop:queen_of_hearts) and sorting of cards in general.
 * Any item in {@link TabletopApi#CARDS} is a card; these are just the ones a new deck is made of.
 */
public final class Card {
    public static final int COUNT = 52;
    /** Jokers aren't in a new deck; they're cut from card stock like any other card. */
    public static final int JOKERS = 4;
    private static final String[] RANKS = {"ace", "2", "3", "4", "5", "6", "7", "8", "9", "10", "jack", "queen", "king"};
    private static final String[] SUITS = {"spades", "hearts", "diamonds", "clubs"};
    private static List<Item> fullDeck;

    private Card() {}

    /** The registry name of standard card {@code index} (suit * 13 + rank), e.g. "queen_of_hearts". */
    public static String id(int index) {
        return RANKS[index % 13] + "_of_" + SUITS[index / 13];
    }

    /** The registry name of joker {@code index} (from 0), e.g. "joker_1". */
    public static String jokerId(int index) {
        return "joker_" + (index + 1);
    }

    /** The standard cards in fresh-deck order, ace of spades first, the contents of a new deck. */
    public static List<Item> fullDeck() {
        if (fullDeck == null) {
            fullDeck = Tabletop.STANDARD_CARDS.stream().map(card -> (Item) card.get()).toList();
        }
        return fullDeck;
    }

    /**
     * Cards in sorted order, first card first, by their {@link TabletopApi#CARD_SORT} codes: rank-0 cards (jokers and
     * the like) first in random order, then by suit letter and rank, then cards without a code.
     */
    public static List<Item> sorted(List<Item> cards, RandomSource random) {
        List<Item> front = new ArrayList<>();
        List<Item> rest = new ArrayList<>();
        for (Item card : cards) {
            (rank(card) == 0 ? front : rest).add(card);
        }
        Util.shuffle(front, random);
        rest.sort(Comparator.comparing((Item card) -> code(card) == null)
                .thenComparing(card -> suit(card))
                .thenComparingInt(Card::rank)
                .thenComparingInt(card -> BuiltInRegistries.ITEM.getId(card)));
        front.addAll(rest);
        return front;
    }

    private static String code(Item card) {
        TabletopApi.CardSort sort = BuiltInRegistries.ITEM.wrapAsHolder(card).getData(TabletopApi.CARD_SORT);
        return sort != null ? sort.code() : null;
    }

    /** The number at the start of a card's code; -1 without one. */
    private static int rank(Item card) {
        String code = code(card);
        if (code == null) {
            return -1;
        }
        int end = 0;
        while (end < code.length() && Character.isDigit(code.charAt(end))) {
            end++;
        }
        return end == 0 ? -1 : Integer.parseInt(code.substring(0, end));
    }

    /** The letters after the number in a card's code; empty without one. */
    private static String suit(Item card) {
        String code = code(card);
        return code == null ? "" : code.replaceFirst("^\\d+", "");
    }
}
