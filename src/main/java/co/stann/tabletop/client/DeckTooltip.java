package co.stann.tabletop.client;

import co.stann.tabletop.Backing;
import co.stann.tabletop.CardItem;
import co.stann.tabletop.DeckItem;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.Item;

/** The picture in a deck's tooltip: every card in it, top card first, fanned out in rows of 13. */
public record DeckTooltip(List<Item> cards, Backing backing) implements ClientTooltipComponent {
    private static final int PER_ROW = 13;
    // Cards overlap so only their left edge shows, which is where the rank and suit are.
    private static final int STEP_X = 10;
    private static final int STEP_Y = 18;

    public DeckTooltip(DeckItem.DeckContents contents) {
        this(contents.cards(), contents.backing());
    }

    @Override
    public int getHeight() {
        return (cards.size() + PER_ROW - 1) / PER_ROW * STEP_Y + 2;
    }

    @Override
    public int getWidth(Font font) {
        return (Math.min(cards.size(), PER_ROW) - 1) * STEP_X + 16;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        for (int i = 0; i < cards.size(); i++) {
            Item card = cards.get(cards.size() - 1 - i);
            graphics.pose().pushPose();
            // Each card sits a little in front of the one before, so later cards cleanly cover earlier ones.
            graphics.pose().translate(0, 0, i);
            graphics.renderItem(CardItem.stack(card, true, backing), x + i % PER_ROW * STEP_X, y + i / PER_ROW * STEP_Y);
            graphics.pose().popPose();
        }
    }
}
