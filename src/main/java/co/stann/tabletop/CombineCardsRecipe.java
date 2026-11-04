package co.stann.tabletop;

import co.stann.tabletop.api.TabletopApi;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Any mix of two or more cards (anything in {@link TabletopApi#CARDS}) and decks with the same back and label in a crafting grid
 * makes one deck holding all of them, stacked in grid order (the first slot ends up at the bottom), as long as they
 * fit in one deck.
 */
public class CombineCardsRecipe extends CustomRecipe {
    public CombineCardsRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        int items = 0;
        int cards = 0;
        Backing backing = null;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            if (backing != null && !backing.matches(stack)) {
                return false;
            }
            backing = Backing.of(stack);
            if (stack.getItem() instanceof DeckItem) {
                cards += DeckItem.cards(stack).size();
            } else if (TabletopApi.isCard(stack)) {
                cards++;
            } else {
                return false;
            }
            items++;
        }
        // A deck holds at least two cards, so an empty deck and one card make nothing.
        return items >= 2 && cards >= 2 && cards <= DeckItem.MAX_CARDS;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        List<Item> cards = new ArrayList<>();
        Backing backing = Backing.DEFAULT;
        for (ItemStack stack : input.items()) {
            if (!stack.isEmpty()) {
                backing = Backing.of(stack);
            }
            if (stack.getItem() instanceof DeckItem) {
                cards.addAll(DeckItem.cards(stack));
            } else if (TabletopApi.isCard(stack)) {
                cards.add(stack.getItem());
            }
        }
        return DeckItem.stack(cards, backing);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Tabletop.COMBINE_CARDS.get();
    }
}
