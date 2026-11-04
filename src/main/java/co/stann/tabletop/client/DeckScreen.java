package co.stann.tabletop.client;

import co.stann.tabletop.DeckItem;
import co.stann.tabletop.DeckMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The deck screen: a large-chest style grid of the deck's cards, with Sort and Shuffle buttons in the title bar and
 * the number of cards on the inventory label's row.
 */
public class DeckScreen extends AbstractContainerScreen<DeckMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int LABEL_COLOR = 0x404040;
    /** The grey of the vanilla container background, to fill beside the narrower card grid. */
    private static final int PANEL_COLOR = 0xFFC6C6C6;
    /** Where the Sort button starts, from the screen's left edge; the title has to end before it. */
    private static final int BUTTONS_X = 176 - 7 - 44 - 2 - 32;

    public DeckScreen(DeckMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 114 + DeckMenu.ROWS * 18;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("container.tabletop.deck.sort"), button -> press(DeckMenu.BUTTON_SORT))
                .bounds(leftPos + BUTTONS_X, topPos + 4, 32, 12).build());
        addRenderableWidget(Button.builder(Component.translatable("container.tabletop.deck.shuffle"), button -> press(DeckMenu.BUTTON_SHUFFLE))
                .bounds(leftPos + BUTTONS_X + 32 + 2, topPos + 4, 44, 12).build());
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // A long title (a deck renamed in an anvil) is cut short so it doesn't run under the buttons.
        String title = this.title.getString();
        int room = BUTTONS_X - titleLabelX - 3;
        if (font.width(title) > room) {
            title = font.plainSubstrByWidth(title, room - font.width("...")) + "...";
        }
        graphics.drawString(font, title, titleLabelX, titleLabelY, LABEL_COLOR, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL_COLOR, false);
        Component count = Component.translatable("container.tabletop.deck.count", menu.cardCount(), DeckItem.MAX_CARDS);
        graphics.drawString(font, count, imageWidth - 8 - font.width(count), inventoryLabelY, LABEL_COLOR, false);
    }

    private void press(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * The large chest's background, rebuilt for the card grid: its title bar, then one row strip per row of cards with
     * the slots narrowed to the grid's columns, then its inventory half.
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, 17);
        int gridLeft = DeckMenu.GRID_X - 1;
        int gridRight = gridLeft + DeckMenu.COLUMNS * 18;
        for (int row = 0; row < DeckMenu.ROWS; row++) {
            int y = topPos + 17 + row * 18;
            graphics.blit(BACKGROUND, leftPos, y, 0, 17, 7, 18);
            graphics.fill(leftPos + 7, y, leftPos + gridLeft, y + 18, PANEL_COLOR);
            graphics.blit(BACKGROUND, leftPos + gridLeft, y, 7, 17, DeckMenu.COLUMNS * 18, 18);
            graphics.fill(leftPos + gridRight, y, leftPos + imageWidth - 7, y + 18, PANEL_COLOR);
            graphics.blit(BACKGROUND, leftPos + imageWidth - 7, y, imageWidth - 7, 17, 7, 18);
        }
        graphics.blit(BACKGROUND, leftPos, topPos + DeckMenu.ROWS * 18 + 17, 0, 126, imageWidth, 96);
    }
}
