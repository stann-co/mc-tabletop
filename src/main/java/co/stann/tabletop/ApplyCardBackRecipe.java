package co.stann.tabletop;

import co.stann.tabletop.api.TabletopApi;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * A card, card stock or deck crafted with an item from {@link TabletopApi#CARD_BACK} (a dye, or a theme item another
 * mod adds) gets that item's back theme. A deck changes the back of every card in it.
 */
public class ApplyCardBackRecipe extends CustomRecipe {
    public ApplyCardBackRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return find(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Found found = find(input);
        if (found == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = found.card().copyWithCount(1);
        TabletopApi.setBack(result, found.back());
        return result;
    }

    private record Found(ItemStack card, ResourceLocation back) {}

    /** Exactly one card or deck plus exactly one back-theme item, whose theme it doesn't already have. */
    private static Found find(CraftingInput input) {
        ItemStack card = null;
        ResourceLocation back = null;
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation applied = TabletopApi.backApplied(stack.getItem());
            if (TabletopApi.isCard(stack) || stack.getItem() instanceof DeckItem) {
                if (card != null) {
                    return null;
                }
                card = stack;
            } else if (applied != null && back == null) {
                back = applied;
            } else {
                return null;
            }
        }
        return card != null && back != null && !back.equals(TabletopApi.backOf(card)) ? new Found(card, back) : null;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Tabletop.APPLY_CARD_BACK.get();
    }
}
