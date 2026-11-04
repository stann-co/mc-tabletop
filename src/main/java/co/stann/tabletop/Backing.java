package co.stann.tabletop;

import co.stann.tabletop.api.TabletopApi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * What everyone can see of a card whichever way up it is: its back theme ({@link Tabletop#BACK}) and the label its
 * owner gave it in an anvil ({@link Tabletop#LABEL}, empty for none). Cards and decks only go together with the same
 * backing, so a labelled deck can't take in cards slipped in from another one.
 */
public record Backing(ResourceLocation theme, String label) {
    public static final Backing DEFAULT = new Backing(TabletopApi.DEFAULT_BACK, "");

    public static Backing of(ItemStack stack) {
        return new Backing(TabletopApi.backOf(stack), TabletopApi.labelOf(stack));
    }

    public static Backing of(ResourceLocation theme) {
        return new Backing(theme, "");
    }

    public void applyTo(ItemStack stack) {
        TabletopApi.setBack(stack, theme);
        TabletopApi.setLabel(stack, label);
    }

    public boolean matches(ItemStack stack) {
        return equals(of(stack));
    }
}
