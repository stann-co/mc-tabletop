package co.stann.tabletop;

import co.stann.tabletop.api.TabletopApi;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;

/**
 * Inventory clicks with cards and decks, for every card in {@link TabletopApi#CARDS}. Cards and decks with the same back
 * and label act like a stack of items, where each card is one item:
 * <ul>
 *   <li>carrying a card or deck, left-click a card or deck: put everything carried on top of it, as much as fits;
 *   <li>right-click a deck: cut it, picking up the top half;
 *   <li>carrying a deck, right-click an empty slot: deal its top card there;
 *   <li>carrying a deck, right-click a card or deck: gather it into the carried deck, on top;
 *   <li>carrying a card, right-click a card or deck: put the card on top, so two cards make a deck;
 *   <li>carrying a deck, left-drag over empty slots: split it evenly between them, dealing round the slots;
 *   <li>carrying a card or deck, double-click: collect every card and deck with its back into it;
 *   <li>Q on a deck: drop its top card (Ctrl+Q drops the whole deck).
 * </ul>
 * Right-dragging a deck across slots does the same to each (see {@code client.DeckDrag}). With different backs or labels,
 * a left-click swaps them as with any two items. A pile of one card left anywhere is a plain card, never a deck. Runs on
 * both sides, like every inventory click; the drag, Q and double-click come from {@code mixin.AbstractContainerMenuMixin}.
 */
public final class InventoryClicks {
    private InventoryClicks() {}

    static void onStackedOnOther(ItemStackedOnOtherEvent event) {
        Player player = event.getPlayer();
        Slot slot = event.getSlot();
        if (!slot.allowModification(player)) {
            return;
        }
        boolean handled = event.getClickAction() == ClickAction.PRIMARY
                ? putAllOnTop(event.getCarriedItem(), event.getStackedOnItem(), slot, event.getCarriedSlotAccess(), player)
                : handle(event.getCarriedItem(), event.getStackedOnItem(), slot, event.getCarriedSlotAccess(), player);
        if (handled) {
            event.setCanceled(true);
        }
    }

    /**
     * Puts everything carried on top of the card or deck in the slot, as items join a stack. If not all of it fits,
     * the carried deck's top cards stay in hand. Different backs are left to vanilla, which swaps the two.
     */
    private static boolean putAllOnTop(ItemStack carried, ItemStack target, Slot slot, SlotAccess cursor, Player player) {
        if (!isPile(carried) || !isPile(target) || stacks(carried, target)) {
            return false;
        }
        Backing back = Backing.of(target);
        if (!back.matches(carried)) {
            return false;
        }
        List<Item> combined = new ArrayList<>(cards(target));
        List<Item> added = cards(carried);
        int moved = Math.min(DeckItem.MAX_CARDS - combined.size(), added.size());
        if (moved <= 0) {
            message(player, "message.tabletop.deck_full");
            return true;
        }
        combined.addAll(added.subList(0, moved));
        ItemStack result = DeckItem.ofCards(combined, back);
        if (!slot.mayPlace(result)) {
            return false;
        }
        slot.set(result);
        cursor.set(withoutBottom(carried, moved));
        playSound(player);
        return true;
    }

    /** Whether a deck can be split by left-dragging: there must be at least two cards to share out. */
    public static boolean canSplit(ItemStack carried) {
        return carried.is(Tabletop.DECK) && carried.getCount() == 1 && DeckItem.cards(carried).size() >= 2;
    }

    /** Whether a left-drag splitting {@code deck} may add {@code slot}, with {@code marked} slots already chosen: one card each at least. */
    public static boolean canSplitInto(AbstractContainerMenu menu, Slot slot, ItemStack deck, int marked) {
        return !slot.hasItem() && slot.mayPlace(deck) && menu.canDragTo(slot) && marked < DeckItem.cards(deck).size();
    }

    /** Deals the carried deck round the slots, one card at a time, until each has an equal share; the rest stays in hand. */
    public static void split(AbstractContainerMenu menu, Collection<Slot> slots) {
        ItemStack deck = menu.getCarried();
        List<Item> cards = new ArrayList<>(DeckItem.cards(deck));
        Backing back = Backing.of(deck);
        // In slot order, so the client's guess matches what the server does.
        List<Slot> ordered = slots.stream().sorted(Comparator.comparingInt(slot -> slot.index)).toList();
        int each = cards.size() / ordered.size();
        List<List<Item>> hands = new ArrayList<>();
        ordered.forEach(slot -> hands.add(new ArrayList<>()));
        for (int round = 0; round < each; round++) {
            hands.forEach(hand -> hand.add(cards.removeLast()));
        }
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setByPlayer(DeckItem.ofCards(hands.get(i), back));
        }
        menu.setCarried(DeckItem.ofCards(cards, back));
    }

    /** Q in an inventory on a deck drops only its top card. Returns false to leave the click to vanilla. */
    public static boolean throwTopCard(AbstractContainerMenu menu, int slotId, int button, Player player) {
        if (button != 0 || slotId < 0 || !menu.getCarried().isEmpty()) {
            return false;
        }
        Slot slot = menu.slots.get(slotId);
        ItemStack deck = slot.getItem();
        if (!deck.is(Tabletop.DECK) || deck.getCount() != 1 || !slot.mayPickup(player) || !slot.allowModification(player)) {
            return false;
        }
        ItemStack rest = deck.copy();
        ItemStack top = DeckItem.takeTop(rest);
        if (top.isEmpty()) {
            return false;
        }
        slot.setByPlayer(DeckItem.settle(rest));
        player.drop(top, true);
        return true;
    }

    /**
     * A double-click while carrying a card or deck collects every card and deck with its back from the screen into it,
     * the top cards of each first, until it is full. Stackable cards (card stock) carried are left to vanilla. Not in
     * the deck screen, where a quick double-click on a card would empty the whole deck onto the cursor.
     */
    public static boolean collectCards(AbstractContainerMenu menu, int slotId, int button, Player player) {
        ItemStack carried = menu.getCarried();
        if (slotId < 0 || menu instanceof DeckMenu || !isPile(carried) || carried.getMaxStackSize() > 1) {
            return false;
        }
        Slot clicked = menu.slots.get(slotId);
        if (clicked.hasItem() && clicked.mayPickup(player)) {
            return false;
        }
        Backing back = Backing.of(carried);
        List<Item> cards = new ArrayList<>(cards(carried));
        int before = cards.size();
        int start = button == 0 ? 0 : menu.slots.size() - 1;
        int step = button == 0 ? 1 : -1;
        for (int i = start; i >= 0 && i < menu.slots.size() && cards.size() < DeckItem.MAX_CARDS; i += step) {
            Slot slot = menu.slots.get(i);
            ItemStack pile = slot.getItem();
            if (!isPile(pile) || !back.matches(pile) || !slot.mayPickup(player) || !slot.allowModification(player)
                    || !menu.canTakeItemForPickAll(carried, slot)) {
                continue;
            }
            List<Item> inPile = cards(pile);
            int taken = Math.min(inPile.size(), DeckItem.MAX_CARDS - cards.size());
            cards.addAll(inPile.subList(inPile.size() - taken, inPile.size()));
            slot.setByPlayer(withoutTop(pile, taken));
        }
        if (cards.size() > before) {
            menu.setCarried(DeckItem.ofCards(cards, back));
            playSound(player);
        }
        return true;
    }

    private static boolean handle(ItemStack carried, ItemStack target, Slot slot, SlotAccess cursor, Player player) {
        if (carried.isEmpty()) {
            return target.is(Tabletop.DECK) && cut(target, slot, cursor, player);
        }
        if (carried.is(Tabletop.DECK)) {
            return target.isEmpty() ? deal(carried, slot, cursor, player) : isPile(target) && gather(carried, target, slot, cursor, player);
        }
        return TabletopApi.isCard(carried) && isPile(target) && !stacks(carried, target) && putOnTop(carried, target, slot, player);
    }

    /** Picks up the top half of the deck in the slot, the bigger half if it doesn't split evenly. */
    private static boolean cut(ItemStack deck, Slot slot, SlotAccess cursor, Player player) {
        List<Item> cards = DeckItem.cards(deck);
        if (deck.getCount() != 1 || cards.size() < 2) {
            return false;
        }
        Backing back = Backing.of(deck);
        int split = cards.size() / 2;
        ItemStack bottom = DeckItem.ofCards(cards.subList(0, split), back);
        if (!slot.mayPlace(bottom)) {
            return false;
        }
        slot.set(bottom);
        cursor.set(DeckItem.ofCards(cards.subList(split, cards.size()), back));
        playSound(player);
        return true;
    }

    /** Puts the carried deck's top card into the empty slot. */
    private static boolean deal(ItemStack deck, Slot slot, SlotAccess cursor, Player player) {
        List<Item> cards = DeckItem.cards(deck);
        if (deck.getCount() != 1 || cards.isEmpty()) {
            return false;
        }
        ItemStack top = CardItem.stack(cards.getLast(), true, Backing.of(deck));
        if (!slot.mayPlace(top)) {
            return false;
        }
        slot.set(top);
        cursor.set(withoutTop(deck, 1));
        playSound(player);
        return true;
    }

    /** Takes the card or deck in the slot onto the carried deck. If not all of a deck fits, its bottom cards stay behind. */
    private static boolean gather(ItemStack deck, ItemStack target, Slot slot, SlotAccess cursor, Player player) {
        List<Item> taken = cards(target);
        if (deck.getCount() != 1 || target.getCount() != 1) {
            return false;
        }
        Backing back = Backing.of(deck);
        if (!back.matches(target)) {
            message(player, "message.tabletop.other_back");
            return true;
        }
        List<Item> cards = new ArrayList<>(DeckItem.cards(deck));
        int moved = Math.min(taken.size(), DeckItem.MAX_CARDS - cards.size());
        if (moved <= 0) {
            message(player, "message.tabletop.deck_full");
            return true;
        }
        int left = taken.size() - moved;
        cards.addAll(taken.subList(left, taken.size()));
        slot.set(DeckItem.ofCards(taken.subList(0, left), back));
        cursor.set(DeckItem.ofCards(cards, back));
        playSound(player);
        return true;
    }

    /** Puts the carried card on top of the card or deck in the slot. */
    private static boolean putOnTop(ItemStack carried, ItemStack target, Slot slot, Player player) {
        if (target.getCount() != 1) {
            return false;
        }
        Backing back = Backing.of(target);
        if (!back.matches(carried)) {
            message(player, "message.tabletop.other_back");
            return true;
        }
        List<Item> combined = new ArrayList<>(cards(target));
        if (combined.size() >= DeckItem.MAX_CARDS) {
            message(player, "message.tabletop.deck_full");
            return true;
        }
        combined.add(carried.getItem());
        ItemStack result = DeckItem.ofCards(combined, back);
        if (!slot.mayPlace(result)) {
            return false;
        }
        slot.set(result);
        carried.shrink(1);
        playSound(player);
        return true;
    }

    /** Whether a stack is cards in an inventory: a deck or any card. */
    public static boolean isPile(ItemStack stack) {
        return stack.is(Tabletop.DECK) || TabletopApi.isCard(stack);
    }

    /** Card stock joins a stack of the same card stock, as items do, rather than making a deck. */
    private static boolean stacks(ItemStack carried, ItemStack target) {
        return ItemStack.isSameItemSameComponents(carried, target) && target.getCount() < target.getMaxStackSize();
    }

    /** The cards in a deck, or a card for each item in a card stack, bottom to top. */
    private static List<Item> cards(ItemStack pile) {
        return pile.is(Tabletop.DECK) ? DeckItem.cards(pile) : Collections.nCopies(pile.getCount(), pile.getItem());
    }

    /** The pile with its top {@code count} cards taken off. A card stack just gets smaller, keeping its facing. */
    private static ItemStack withoutTop(ItemStack pile, int count) {
        if (!pile.is(Tabletop.DECK)) {
            return pile.copyWithCount(pile.getCount() - count);
        }
        List<Item> cards = DeckItem.cards(pile);
        return DeckItem.ofCards(cards.subList(0, cards.size() - count), Backing.of(pile));
    }

    /** The pile with its bottom {@code count} cards taken off. */
    private static ItemStack withoutBottom(ItemStack pile, int count) {
        if (!pile.is(Tabletop.DECK)) {
            return pile.copyWithCount(pile.getCount() - count);
        }
        List<Item> cards = DeckItem.cards(pile);
        return DeckItem.ofCards(cards.subList(count, cards.size()), Backing.of(pile));
    }

    private static void message(Player player, String key) {
        if (!player.level().isClientSide) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }

    private static void playSound(Player player) {
        player.playSound(SoundEvents.BOOK_PAGE_TURN, 0.8F, 1.3F);
    }
}
