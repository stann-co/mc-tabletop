package co.stann.tabletop.table;

import co.stann.tabletop.Backing;
import co.stann.tabletop.CardItem;
import co.stann.tabletop.DeckItem;
import co.stann.tabletop.api.TabletopApi;
import co.stann.tabletop.api.TabletopApi.Kind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Something lying on a play surface: a pile of cards (a single card is a pile of one), a stack of chips, or a token.
 * Objects remember items by id, so anything in the {@link TabletopApi} tags can lie on a table.
 */
public abstract sealed class TableObject permits TableObject.Pile, TableObject.Chips, TableObject.Token {
    /** Centre, in grid steps (sixteenths of a block) in world coordinates. */
    public final int gx, gz;
    /** Turn in 15 degree steps, clockwise seen from above. Rotation 0 has a card's top pointing north. */
    public int rot;

    TableObject(int gx, int gz, int rot) {
        this.gx = gx;
        this.gz = gz;
        this.rot = rot;
    }

    public abstract Kind kind();

    public abstract int size();

    /** How far it stands above the surface, in table pixels (1/16 block). */
    public abstract float height();

    /** What it turns back into when picked up whole or knocked off the table. */
    public abstract List<ItemStack> toItems();

    /**
     * Whether {@code held} can be put on top of it: a pile takes cards up to as many as a deck holds, and a chip stack
     * takes chips while it has room, only with its own label. Tokens never stack.
     */
    public abstract boolean accepts(ItemStack held);

    abstract void write(CompoundTag tag, boolean censored);

    /**
     * Saves the object. A censored copy is what gets sent to players: it leaves out every card except a face-up
     * top card, so face-down cards and deck contents never reach other clients.
     */
    public CompoundTag save(boolean censored) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("gx", gx);
        tag.putInt("gz", gz);
        tag.putByte("rot", (byte) rot);
        write(tag, censored);
        return tag;
    }

    /** Loads an object, or returns null if none of its items exist any more (e.g. their mod was removed). */
    @Nullable
    public static TableObject load(CompoundTag tag) {
        int gx = tag.getInt("gx");
        int gz = tag.getInt("gz");
        int rot = tag.getByte("rot");
        TableObject object;
        if (tag.contains("chips")) {
            Chips chips = new Chips(gx, gz);
            ListTag list = tag.getList("chips", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag chip = list.getCompound(i);
                item(chip.getString("id")).ifPresent(item -> chips.chips.add(new PlacedChip(item, chip.getString("label"))));
            }
            object = chips;
        } else if (tag.contains("token")) {
            object = item(tag.getString("token")).map(item -> (TableObject) new Token(gx, gz, rot, item)).orElse(null);
        } else {
            Pile pile = new Pile(gx, gz, rot);
            ListTag cards = tag.getList("cards", Tag.TAG_COMPOUND);
            for (int i = 0; i < cards.size(); i++) {
                CompoundTag card = cards.getCompound(i);
                boolean faceUp = card.getBoolean("up");
                ResourceLocation back = card.contains("back") ? ResourceLocation.tryParse(card.getString("back")) : null;
                Backing backing = new Backing(back != null ? back : TabletopApi.DEFAULT_BACK, card.getString("label"));
                if (!card.contains("id")) {
                    pile.cards.add(new PlacedCard(null, faceUp, backing)); // hidden from this client
                } else {
                    item(card.getString("id")).ifPresent(item -> pile.cards.add(new PlacedCard(item, faceUp, backing)));
                }
            }
            object = pile;
        }
        return object != null && object.size() > 0 ? object : null;
    }

    private static Optional<Item> item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(location);
    }

    private static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** A card lying on the table, with its back theme and label. On clients, a card that isn't showing has a null item. */
    public record PlacedCard(@Nullable Item card, boolean faceUp, Backing backing) {
        /** The card as an item stack, lying the way it lay on the table, so it goes back down the same way. */
        public ItemStack toItem() {
            return CardItem.stack(card, faceUp, backing);
        }
    }

    /** A chip lying on the table, with the label it was given in an anvil ("" for none). */
    public record PlacedChip(Item chip, String label) {
        public static PlacedChip of(ItemStack stack) {
            return new PlacedChip(stack.getItem(), TabletopApi.labelOf(stack));
        }

        public ItemStack toItem(int count) {
            ItemStack stack = new ItemStack(chip, count);
            TabletopApi.setLabel(stack, label);
            return stack;
        }
    }

    public static final class Pile extends TableObject {
        /** Bottom to top. */
        public final List<PlacedCard> cards = new ArrayList<>();

        public Pile(int gx, int gz, int rot) {
            super(gx, gz, rot);
        }

        public PlacedCard top() {
            return cards.getLast();
        }

        /** Turns the whole pile over, the way you would flip a pile of real cards. */
        public void flip() {
            Collections.reverse(cards);
            cards.replaceAll(card -> new PlacedCard(card.card(), !card.faceUp(), card.backing()));
        }

        @Override
        public Kind kind() {
            return Kind.CARDS;
        }

        @Override
        public int size() {
            return cards.size();
        }

        @Override
        public float height() {
            return Math.max(1, cards.size()) * TableSurface.CARD_THICKNESS;
        }

        @Override
        public boolean accepts(ItemStack held) {
            int adding = held.getItem() instanceof DeckItem ? DeckItem.cards(held).size() : 1;
            return cards.size() + adding <= DeckItem.MAX_CARDS;
        }

        @Override
        public List<ItemStack> toItems() {
            if (cards.size() == 1) {
                return List.of(top().toItem());
            }
            // A deck has one back and label, so a mixed pile comes back as a deck per backing (a pile never holds more
            // than a deck does, see accepts). A lone card left over comes back as a card.
            Map<Backing, List<Item>> byBacking = new LinkedHashMap<>();
            cards.forEach(card -> byBacking.computeIfAbsent(card.backing(), backing -> new ArrayList<>()).add(card.card()));
            List<ItemStack> decks = new ArrayList<>();
            byBacking.forEach((backing, ids) -> {
                for (int from = 0; from < ids.size(); from += DeckItem.MAX_CARDS) {
                    decks.add(DeckItem.ofCards(ids.subList(from, Math.min(ids.size(), from + DeckItem.MAX_CARDS)), backing));
                }
            });
            return decks;
        }

        @Override
        void write(CompoundTag tag, boolean censored) {
            ListTag list = new ListTag();
            for (int i = 0; i < cards.size(); i++) {
                PlacedCard card = cards.get(i);
                CompoundTag entry = new CompoundTag();
                entry.putBoolean("up", card.faceUp());
                // The back and label are what everyone sees anyway, so clients always get them.
                if (!card.backing().theme().equals(TabletopApi.DEFAULT_BACK)) {
                    entry.putString("back", card.backing().theme().toString());
                }
                if (!card.backing().label().isEmpty()) {
                    entry.putString("label", card.backing().label());
                }
                boolean showing = i == cards.size() - 1 && card.faceUp();
                if (!censored || showing) {
                    entry.putString("id", id(card.card()));
                }
                list.add(entry);
            }
            tag.put("cards", list);
        }
    }

    public static final class Chips extends TableObject {
        /** Bottom to top. */
        public final List<PlacedChip> chips = new ArrayList<>();

        public Chips(int gx, int gz) {
            super(gx, gz, 0);
        }

        public List<Item> items() {
            return chips.stream().map(PlacedChip::chip).toList();
        }

        public int value() {
            return chips.stream().mapToInt(chip -> TabletopApi.chipValue(chip.chip())).sum();
        }

        /**
         * Puts the highest values at the bottom, like chips in a rack, and like chips together. A stack is then at
         * most one run per kind of chip, which is what keeps it cheap to draw however it was built.
         */
        public void sort() {
            chips.sort(Comparator.comparingInt((PlacedChip chip) -> -TabletopApi.chipValue(chip.chip()))
                    .thenComparing(chip -> BuiltInRegistries.ITEM.getKey(chip.chip())));
        }

        @Override
        public Kind kind() {
            return Kind.CHIPS;
        }

        @Override
        public int size() {
            return chips.size();
        }

        @Override
        public float height() {
            return chips.size() * TableSurface.CHIP_THICKNESS;
        }

        @Override
        public boolean accepts(ItemStack held) {
            // Every chip in a stack has the same label, so the bottom one speaks for them all.
            return chips.size() < TableSurface.MAX_CHIPS && (chips.isEmpty() || chips.getFirst().label().equals(TabletopApi.labelOf(held)));
        }

        @Override
        public List<ItemStack> toItems() {
            // Chips with different labels don't stack in an inventory, so each kind and label comes back on its own.
            Map<PlacedChip, Integer> counts = new LinkedHashMap<>();
            chips.forEach(chip -> counts.merge(chip, 1, Integer::sum));
            List<ItemStack> items = new ArrayList<>();
            counts.forEach((chip, count) -> items.add(chip.toItem(count)));
            return items;
        }

        @Override
        void write(CompoundTag tag, boolean censored) {
            ListTag list = new ListTag();
            for (PlacedChip chip : chips) {
                CompoundTag entry = new CompoundTag();
                entry.putString("id", id(chip.chip()));
                if (!chip.label().isEmpty()) {
                    entry.putString("label", chip.label());
                }
                list.add(entry);
            }
            tag.put("chips", list);
        }
    }

    /** A single marker, like the dealer button: it never stacks. */
    public static final class Token extends TableObject {
        public final Item item;

        public Token(int gx, int gz, int rot, Item item) {
            super(gx, gz, rot);
            this.item = item;
        }

        @Override
        public Kind kind() {
            return Kind.TOKENS;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public float height() {
            return TableSurface.TOKEN_THICKNESS;
        }

        @Override
        public boolean accepts(ItemStack held) {
            return false;
        }

        @Override
        public List<ItemStack> toItems() {
            return List.of(new ItemStack(item));
        }

        @Override
        void write(CompoundTag tag, boolean censored) {
            tag.putString("token", id(item));
        }
    }
}
