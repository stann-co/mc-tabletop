package co.stann.tabletop.api;

import co.stann.tabletop.Tabletop;
import co.stann.tabletop.DeckItem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

/**
 * Hooks for putting your own things on Tabletop's play surfaces (poker tables and playmats).
 *
 * <p>No code is needed: add an item to one of the tags below (with a datapack, KubeJS, or your mod's data) and
 * players can lay it on a table. The table draws the item's own model lying flat, so a texture is all it needs.
 * ADDING_ITEMS.md has ready-to-copy examples.
 *
 * <p>To add a new kind of surface, extend {@link co.stann.tabletop.table.PlaySurfaceBlock}.
 */
public final class TabletopApi {
    public static final String MODID = "tabletop";

    /** Cards: they pile up, lie face-up or face-down, go into decks, and only their holder sees their face. */
    public static final TagKey<Item> CARDS = TagKey.create(Registries.ITEM, id("cards"));
    /** Chips: they stack up to 20 high, and players can place several at once. Give them a {@link #CHIP_VALUE}. */
    public static final TagKey<Item> CHIPS = TagKey.create(Registries.ITEM, id("chips"));
    /** Tokens: single markers such as the dealer button, placed and taken one at a time; they never stack. */
    public static final TagKey<Item> TOKENS = TagKey.create(Registries.ITEM, id("tokens"));

    /** What a chip is worth, added up on chip stack labels. File: data/tabletop/data_maps/item/chip_value.json. */
    public static final DataMapType<Item, ChipValue> CHIP_VALUE = DataMapType.builder(id("chip_value"), Registries.ITEM, ChipValue.CODEC)
            .synced(ChipValue.CODEC, false)
            .build();
    /** Where a card goes when a deck is sorted. File: data/tabletop/data_maps/item/card_sort.json. */
    public static final DataMapType<Item, CardSort> CARD_SORT = DataMapType.builder(id("card_sort"), Registries.ITEM, CardSort.CODEC)
            .synced(CardSort.CODEC, false)
            .build();
    /**
     * Items that give a card, card stock or deck a back theme when crafted together, like the dyes.
     * File: data/tabletop/data_maps/item/card_back.json. The theme's look is a model at models/item/card_back/{name}.json
     * in its namespace, found automatically; its name is the lang key card_back.{namespace}.{name}.
     */
    public static final DataMapType<Item, CardBack> CARD_BACK = DataMapType.builder(id("card_back"), Registries.ITEM, CardBack.CODEC)
            .synced(CardBack.CODEC, false)
            .build();
    /** The back cards have unless something gave them another. */
    public static final ResourceLocation DEFAULT_BACK = id("white");

    /** How something behaves on a play surface. */
    public enum Kind { CARDS, CHIPS, TOKENS }

    public record ChipValue(int value) {
        public static final Codec<ChipValue> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(Codec.INT.fieldOf("value").forGetter(ChipValue::value))
                .apply(instance, ChipValue::new));
    }

    /**
     * A card's sort code: a rank number then a suit letter, e.g. "1a" for the ace of spades. Ranks run 1 (ace) to
     * 13 (king); suits are a = spades, b = hearts, c = diamonds, d = clubs. Rank 0 sorts in front of everything (in
     * random order), suit letters after d sort after the standard suits, and cards without a code go last.
     */
    public record CardSort(String code) {
        public static final Codec<CardSort> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(Codec.STRING.fieldOf("code").forGetter(CardSort::code))
                .apply(instance, CardSort::new));
    }

    /** A back theme an item applies, e.g. {@code tabletop:blue} for blue dye. */
    public record CardBack(ResourceLocation back) {
        public static final Codec<CardBack> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(ResourceLocation.CODEC.fieldOf("back").forGetter(CardBack::back))
                .apply(instance, CardBack::new));
    }

    private TabletopApi() {}

    /** How an item behaves on a play surface, or null if it can't go on one. */
    @Nullable
    public static Kind kindOf(ItemStack stack) {
        if (stack.is(CARDS) || stack.getItem() instanceof DeckItem) {
            return Kind.CARDS;
        }
        if (stack.is(CHIPS)) {
            return Kind.CHIPS;
        }
        return stack.is(TOKENS) ? Kind.TOKENS : null;
    }

    public static boolean isCard(ItemStack stack) {
        return stack.is(CARDS);
    }

    /** The back theme of a card or deck. */
    public static ResourceLocation backOf(ItemStack stack) {
        return stack.getOrDefault(Tabletop.BACK, DEFAULT_BACK);
    }

    /** Gives a card or deck a back theme. The default isn't stored, so plain cards stay plain stacks. */
    public static void setBack(ItemStack stack, ResourceLocation back) {
        if (back.equals(DEFAULT_BACK)) {
            stack.remove(Tabletop.BACK);
        } else {
            stack.set(Tabletop.BACK, back);
        }
    }

    /** The label a card, deck or chip was given in an anvil, or "" for none. Cards only go together with the same label. */
    public static String labelOf(ItemStack stack) {
        return stack.getOrDefault(Tabletop.LABEL, "");
    }

    /** Labels a card, deck or chip; "" removes the label. */
    public static void setLabel(ItemStack stack, String label) {
        if (label.isEmpty()) {
            stack.remove(Tabletop.LABEL);
        } else {
            stack.set(Tabletop.LABEL, label);
        }
    }

    /** The back theme an item applies when crafted with a card, from {@link #CARD_BACK}, or null. */
    @Nullable
    public static ResourceLocation backApplied(Item item) {
        CardBack back = BuiltInRegistries.ITEM.wrapAsHolder(item).getData(CARD_BACK);
        return back != null ? back.back() : null;
    }

    /** A chip's value from {@link #CHIP_VALUE}, or 0 if it has none. */
    public static int chipValue(Item chip) {
        ChipValue value = BuiltInRegistries.ITEM.wrapAsHolder(chip).getData(CHIP_VALUE);
        return value != null ? value.value() : 0;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
