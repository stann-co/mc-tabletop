package co.stann.tabletop.compat;

import co.stann.tabletop.ApplyCardBackRecipe;
import co.stann.tabletop.Tabletop;
import co.stann.tabletop.api.TabletopApi;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * JEI support, loaded only when JEI is installed. JEI lists card stock and decks once per back theme, and shows how
 * each theme is applied: that crafting recipe ({@link ApplyCardBackRecipe}) takes any item in the card_back data map,
 * which JEI can't show by itself.
 */
@JeiPlugin
public class TabletopJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return Tabletop.id("jei");
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        ISubtypeInterpreter<ItemStack> byBack = new ISubtypeInterpreter<>() {
            @Override
            public Object getSubtypeData(ItemStack stack, UidContext context) {
                return TabletopApi.backOf(stack);
            }

            @Override
            public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
                return TabletopApi.backOf(stack).toString();
            }
        };
        registration.registerSubtypeInterpreter(Tabletop.CARD_STOCK.get(), byBack);
        registration.registerSubtypeInterpreter(Tabletop.DECK.get(), byBack);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<RecipeHolder<CraftingRecipe>> recipes = new ArrayList<>();
        BuiltInRegistries.ITEM.getDataMap(TabletopApi.CARD_BACK).forEach((applier, back) -> {
            for (Item themed : List.of(Tabletop.CARD_STOCK.get(), Tabletop.DECK.get())) {
                ItemStack result = new ItemStack(themed);
                TabletopApi.setBack(result, back.back());
                ResourceLocation id = Tabletop.id("jei/card_back/%s/%s/%s".formatted(
                        applier.location().getNamespace(), applier.location().getPath(), BuiltInRegistries.ITEM.getKey(themed).getPath()));
                NonNullList<Ingredient> ingredients = NonNullList.of(Ingredient.EMPTY, Ingredient.of(themed), Ingredient.of(BuiltInRegistries.ITEM.get(applier)));
                recipes.add(new RecipeHolder<>(id, new ShapelessRecipe("", CraftingBookCategory.MISC, result, ingredients)));
            }
        });
        registration.addRecipes(RecipeTypes.CRAFTING, recipes);
    }
}
