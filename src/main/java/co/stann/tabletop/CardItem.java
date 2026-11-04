package co.stann.tabletop;

import co.stann.tabletop.api.TabletopApi;
import co.stann.tabletop.client.Controls;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Tabletop's own cards: card stock and the 52 standard cards. Card behaviour itself comes from the
 * {@link TabletopApi#CARDS} tag, so cards from other mods or KubeJS work the same.
 *
 * <p>Other players only ever see a held card's back, which is the card's back theme ({@link Tabletop#BACK}). Whether a
 * card is face-up is part of the stack ({@link Tabletop#FACE_UP}): it decides which way up it lands when placed, and a
 * face-down card hides its face from the holder too (it's just "Card"), so cards can be dealt and drawn unseen.
 */
public class CardItem extends Item {
    public CardItem(Properties properties) {
        super(properties);
    }

    public static ItemStack stack(Item card, boolean faceUp, Backing backing) {
        ItemStack stack = new ItemStack(card);
        setFaceUp(stack, faceUp);
        backing.applyTo(stack);
        return stack;
    }

    public static boolean faceUp(ItemStack stack) {
        return stack.getOrDefault(Tabletop.FACE_UP, true);
    }

    /** Face-up is the default, so only face-down is stored; a face-up card stays a plain stack. */
    public static void setFaceUp(ItemStack stack, boolean faceUp) {
        if (faceUp) {
            stack.remove(Tabletop.FACE_UP);
        } else {
            stack.set(Tabletop.FACE_UP, false);
        }
    }

    /** What a face-down card is called, so its name doesn't give it away. */
    public static Component hiddenName() {
        return Component.translatable("item.tabletop.card.hidden");
    }

    /** Adds the back theme line to a card's or deck's tooltip. */
    public static void appendBack(ItemStack stack, List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.tabletop.card.back", backName(TabletopApi.backOf(stack))).withStyle(ChatFormatting.GRAY));
    }

    /** Adds the label line to a card's, deck's or chip's tooltip, if it has a label. Everyone can see it, even face-down. */
    public static void appendLabel(ItemStack stack, List<Component> tooltip) {
        String label = TabletopApi.labelOf(stack);
        if (!label.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.tabletop.label", label).withStyle(ChatFormatting.GOLD));
        }
    }

    /** A back theme's name, from lang key card_back.{namespace}.{name}, e.g. "Blue". */
    public static Component backName(ResourceLocation back) {
        return Component.translatable("card_back." + back.getNamespace() + "." + back.getPath());
    }

    @Override
    public Component getName(ItemStack stack) {
        // Card stock is all back, so it's named after it: "Blue Card Stock".
        if (stack.is(Tabletop.CARD_STOCK)) {
            return Component.translatable("item.tabletop.card_stock.themed", backName(TabletopApi.backOf(stack)));
        }
        return faceUp(stack) ? super.getName(stack) : hiddenName();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        // Card stock's back is already in its name.
        boolean stock = stack.is(Tabletop.CARD_STOCK);
        if (!stock) {
            appendBack(stack, tooltip);
        }
        appendLabel(stack, tooltip);
        if (!stock && !faceUp(stack)) {
            tooltip.add(Component.translatable("tooltip.tabletop.card.face_down", Controls.modifierName()).withStyle(ChatFormatting.GRAY));
        }
        Controls.append(tooltip, "card");
    }
}
