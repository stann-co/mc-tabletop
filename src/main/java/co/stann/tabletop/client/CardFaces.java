package co.stann.tabletop.client;

import co.stann.tabletop.CardItem;
import co.stann.tabletop.Tabletop;
import co.stann.tabletop.api.TabletopApi;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.slf4j.Logger;

/**
 * Decides when a card shows its back instead of its face, and which back, for every card in {@link TabletopApi#CARDS}.
 * A face-up card shows its face only to whoever may see it: in screens (your inventory, JEI, the deck screen), in your
 * own hands, and lying face-up on a table. Everywhere else in the world (other players' hands, dropped, in item frames
 * or on other mods' displays) it shows its back. A face-down card (say, taken from a face-down pile) shows its back
 * even to you, until you turn it over. Card stock is all back. Applied by {@code ItemRendererMixin}.
 *
 * <p>The back drawn is the card's back theme ({@link TabletopApi#backOf}): theme {@code ns:name} uses the model
 * {@code ns:models/item/card_back/name.json}, and a deck with that theme uses {@code ns:models/item/deck/name.json} as
 * its icon. Every model in those folders is found when resources load, so a theme needs no code.
 */
public final class CardFaces {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile Map<ResourceLocation, ModelResourceLocation> backs = Map.of();
    private static volatile Map<ResourceLocation, ModelResourceLocation> decks = Map.of();
    /** Who holds the item whose model was just looked up, for the draw that follows; only the render thread uses it. */
    @Nullable
    private static LivingEntity holder;

    private CardFaces() {}

    /** Finds every back theme's card back and deck icon models and has them loaded. Runs each time resources (re)load. */
    static void registerThemes(ModelEvent.RegisterAdditional event) {
        backs = findThemes(event, "card_back");
        decks = findThemes(event, "deck");
        backs.keySet().stream()
                .filter(theme -> !decks.containsKey(theme))
                .forEach(theme -> LOGGER.warn("Card back theme {} has no deck icon (models/item/deck/{}.json in {}), so its decks show the default one",
                        theme, theme.getPath(), theme.getNamespace()));
    }

    /** Every model in {@code models/item/<folder>/} of any namespace, by theme: {@code ns:models/item/<folder>/name.json} is theme {@code ns:name}. */
    private static Map<ResourceLocation, ModelResourceLocation> findThemes(ModelEvent.RegisterAdditional event, String folder) {
        String prefix = "models/item/" + folder + "/";
        Map<ResourceLocation, ModelResourceLocation> found = new HashMap<>();
        Minecraft.getInstance().getResourceManager().listResources("models/item/" + folder, file -> file.getPath().endsWith(".json"))
                .keySet()
                .forEach(file -> {
                    String name = file.getPath().substring(prefix.length(), file.getPath().length() - ".json".length());
                    ModelResourceLocation model = ModelResourceLocation.standalone(
                            ResourceLocation.fromNamespaceAndPath(file.getNamespace(), "item/" + folder + "/" + name));
                    found.put(ResourceLocation.fromNamespaceAndPath(file.getNamespace(), name), model);
                    event.register(model);
                });
        return Map.copyOf(found);
    }

    /** Remembers who holds the item whose model is being looked up (null if nobody), for {@link #model}. */
    public static void noteHolder(@Nullable LivingEntity entity) {
        holder = entity;
    }

    /** The model to draw an item with, in place of {@code model}: the right back for hidden cards and card stock, the theme's icon for decks. */
    public static BakedModel model(ItemStack stack, ItemDisplayContext context, BakedModel model) {
        LivingEntity heldBy = holder;
        holder = null;
        if (stack.is(Tabletop.DECK)) {
            return themed(decks, stack);
        }
        if (stack.is(Tabletop.CARD_STOCK) || stack.is(TabletopApi.CARDS) && !(CardItem.faceUp(stack) && faceVisible(context, heldBy))) {
            return themed(backs, stack);
        }
        return model;
    }

    /** Whether you may see a card's face: in screens and in your own hands. A table draws face-up cards as held by you. */
    private static boolean faceVisible(ItemDisplayContext context, @Nullable LivingEntity heldBy) {
        return context == ItemDisplayContext.GUI || context.firstPerson() || heldBy != null && heldBy == Minecraft.getInstance().player;
    }

    /** The model for a stack's back theme, or the default theme's if it has none. */
    private static BakedModel themed(Map<ResourceLocation, ModelResourceLocation> themes, ItemStack stack) {
        ModelResourceLocation model = themes.getOrDefault(TabletopApi.backOf(stack), themes.get(TabletopApi.DEFAULT_BACK));
        ModelManager models = Minecraft.getInstance().getModelManager();
        return model != null ? models.getModel(model) : models.getMissingModel();
    }
}
