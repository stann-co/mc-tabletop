package co.stann.tabletop;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;

/**
 * A stonecutter recipe that puts a card design on card stock: the result keeps the back and label of the card stock it
 * was cut from. Recipe type {@code tabletop:card_cutting}, with the same JSON as {@code minecraft:stonecutting}.
 */
public class CardCuttingRecipe extends StonecutterRecipe {
    public CardCuttingRecipe(String group, Ingredient ingredient, ItemStack result) {
        super(group, ingredient, result);
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        ItemStack card = super.assemble(input, registries);
        Backing.of(input.item()).applyTo(card);
        return card;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Tabletop.CARD_CUTTING.get();
    }
}
