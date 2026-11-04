package co.stann.tabletop;

import co.stann.tabletop.api.TabletopApi;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The deck screen: the deck's cards in an 8x8 grid, top card first, above the player's inventory. Cards can be
 * taken out, put in and moved around, and every change is written straight back to the deck item.
 */
public class DeckMenu extends AbstractContainerMenu {
    public static final int COLUMNS = 8;
    public static final int ROWS = DeckItem.MAX_CARDS / COLUMNS;
    /** The card grid is one column narrower than the inventory, so it sits this far in to be centred. */
    public static final int GRID_X = 8 + 9;
    public static final int BUTTON_SORT = 0;
    public static final int BUTTON_SHUFFLE = 1;

    private final Player player;
    /** Inventory index of the deck being edited. It stays locked in place while the screen is open. */
    private final int deckSlot;
    private final SimpleContainer cards;
    private boolean loading;

    public DeckMenu(int id, Inventory inventory, int deckSlot) {
        super(Tabletop.DECK_MENU.get(), id);
        this.player = inventory.player;
        this.deckSlot = deckSlot;
        this.cards = new SimpleContainer(DeckItem.MAX_CARDS) {
            @Override
            public void setChanged() {
                super.setChanged();
                save();
            }
        };
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                addSlot(new Slot(cards, col + row * COLUMNS, GRID_X + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        // Only cards with the deck's back and label belong in it.
                        return TabletopApi.isCard(stack) && Backing.of(deck()).matches(stack);
                    }

                    /** One card per slot, even for stackable cards like card stock: each slot is one card in the deck. */
                    @Override
                    public int getMaxStackSize() {
                        return 1;
                    }
                });
            }
        }
        // Laid out like a large chest below the cards, so the vanilla background fits.
        int inventoryTop = 103 + (ROWS - 4) * 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, inventoryTop + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(col == deckSlot ? new LockedSlot(inventory, col, 8 + col * 18, inventoryTop + 58) : new Slot(inventory, col, 8 + col * 18, inventoryTop + 58));
        }
        if (!player.level().isClientSide) {
            load();
        }
    }

    public static DeckMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new DeckMenu(id, inventory, buf.readVarInt());
    }

    private ItemStack deck() {
        return player.getInventory().getItem(deckSlot);
    }

    private void load() {
        loading = true;
        List<Item> list = DeckItem.cards(deck());
        Backing backing = Backing.of(deck());
        for (int i = 0; i < cards.getContainerSize(); i++) {
            // The deck's list runs bottom to top; the grid shows the top card first.
            cards.setItem(i, i < list.size() ? CardItem.stack(list.get(list.size() - 1 - i), true, backing) : ItemStack.EMPTY);
        }
        loading = false;
    }

    private void save() {
        if (loading || player.level().isClientSide || !(deck().getItem() instanceof DeckItem)) {
            return;
        }
        List<Item> list = new ArrayList<>();
        for (int i = cards.getContainerSize() - 1; i >= 0; i--) {
            ItemStack card = cards.getItem(i);
            if (!card.isEmpty()) {
                list.add(card.getItem());
            }
        }
        DeckItem.setCards(deck(), list);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_SORT) {
            // The deck's list runs bottom to top, so reverse the sorted order to put the first card on top.
            DeckItem.setCards(deck(), Card.sorted(DeckItem.cards(deck()), player.getRandom()).reversed());
        } else if (id == BUTTON_SHUFFLE) {
            DeckItem.shuffle(deck(), player.getRandom());
        } else {
            return false;
        }
        load();
        player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, id == BUTTON_SORT ? 1.2F : 0.8F);
        if (id == BUTTON_SHUFFLE && player instanceof ServerPlayer serverPlayer) {
            Tells.shuffle(serverPlayer);
        }
        return true;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Number keys and F swap items with the hotbar or offhand: never let that move the deck being edited.
        if (clickType == ClickType.SWAP && button == deckSlot) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack moved = stack.copy();
        boolean fromDeck = index < DeckItem.MAX_CARDS;
        if (fromDeck ? !moveItemStackTo(stack, DeckItem.MAX_CARDS, slots.size(), true) : !moveItemStackTo(stack, 0, DeckItem.MAX_CARDS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return moved;
    }

    /** Closing the screen on a deck that's down to one card leaves that card in its place; an emptied deck is used up. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide && deck().getItem() instanceof DeckItem) {
            player.getInventory().setItem(deckSlot, DeckItem.settle(deck()));
        }
    }

    /** How many cards the deck holds right now, as the screen shows it. */
    public int cardCount() {
        int count = 0;
        for (int i = 0; i < cards.getContainerSize(); i++) {
            if (!cards.getItem(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** Runs every tick while the screen is open: all the cards are on show, so the others see a steady peek. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player instanceof ServerPlayer serverPlayer) {
            Tells.peek(serverPlayer);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return deck().getItem() instanceof DeckItem;
    }

    /** The hotbar slot holding the deck itself, which can't be moved while it is open. */
    private static class LockedSlot extends Slot {
        LockedSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
