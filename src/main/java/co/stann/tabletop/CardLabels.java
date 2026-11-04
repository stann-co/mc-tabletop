package co.stann.tabletop;

import co.stann.tabletop.api.TabletopApi;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * Renaming a card or deck in an anvil labels it instead of naming it: every card in a labelled deck carries the label,
 * and cards only combine with cards of the same label, so nobody can quietly slip a card in from another deck. The
 * label shows on every tooltip, face-down or not. Clearing the name removes the label. Chips are labelled the same
 * way, so a table's own chips can be told from ones brought in; the label stays on them when they lie on a table.
 */
public final class CardLabels {
    /** Levels it costs to label or unlabel cards or chips, however many are in the stack. */
    private static final int COST = 1;

    private CardLabels() {}

    static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        if (!event.getRight().isEmpty() || !(left.is(Tabletop.DECK) || TabletopApi.isCard(left) || left.is(TabletopApi.CHIPS)) || event.getName() == null) {
            return;
        }
        String label = event.getName().trim();
        String current = TabletopApi.labelOf(left);
        // The name field starts out holding the item's name, which is no label; vanilla must never name a card either,
        // as a custom name would show in place of a face-down card's "Card".
        if (label.equals(current) || label.equals(left.getHoverName().getString())) {
            event.setCanceled(true);
            return;
        }
        ItemStack labelled = left.copy();
        TabletopApi.setLabel(labelled, label);
        event.setOutput(labelled);
        event.setCost(COST);
    }
}
