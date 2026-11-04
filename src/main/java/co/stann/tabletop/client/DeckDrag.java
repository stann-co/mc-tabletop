package co.stann.tabletop.client;

import co.stann.tabletop.InventoryClicks;
import co.stann.tabletop.Tabletop;
import co.stann.tabletop.client.mixin.AbstractContainerScreenAccessor;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Right-dragging a carried deck across an inventory gathers every card and deck it passes over into it, the way a
 * right-click on each would (see {@code InventoryClicks}). Empty slots are left alone: Mouse Tweaks' right-drag
 * deals a card into each of those.
 *
 * <p>Mouse Tweaks acts on the same drag events, so which slots to gather is decided before it runs (a slot it is
 * about to deal into was empty) and the gathering happens after it (so it never deals into a slot just emptied).
 */
final class DeckDrag {
    private static boolean dragging;
    private static boolean leftStart;
    @Nullable
    private static Slot lastSlot;
    private static final List<Slot> pending = new ArrayList<>();

    private DeckDrag() {}

    static void register(IEventBus bus) {
        bus.addListener(DeckDrag::onPressed);
        bus.addListener(EventPriority.HIGHEST, DeckDrag::beforeDrag);
        bus.addListener(EventPriority.LOWEST, true, DeckDrag::afterDrag);
    }

    private static void onPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        dragging = event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getScreen() instanceof AbstractContainerScreen<?> screen
                && screen.getMenu().getCarried().is(Tabletop.DECK);
        leftStart = false;
        lastSlot = dragging ? ((AbstractContainerScreenAccessor) event.getScreen()).tabletop$findSlot(event.getMouseX(), event.getMouseY()) : null;
    }

    private static void beforeDrag(ScreenEvent.MouseDragged.Pre event) {
        pending.clear();
        if (!dragging || event.getMouseButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT || !(event.getScreen() instanceof AbstractContainerScreenAccessor screen)) {
            return;
        }
        Slot slot = screen.tabletop$findSlot(event.getMouseX(), event.getMouseY());
        if (slot == lastSlot) {
            return;
        }
        // Leaving the slot the drag started on counts as clicking it, so it is gathered too.
        if (!leftStart && holdsCards(lastSlot)) {
            pending.add(lastSlot);
        }
        leftStart = true;
        if (holdsCards(slot)) {
            pending.add(slot);
        }
        lastSlot = slot;
    }

    private static void afterDrag(ScreenEvent.MouseDragged.Pre event) {
        if (pending.isEmpty() || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        AbstractContainerScreenAccessor access = (AbstractContainerScreenAccessor) screen;
        for (Slot slot : pending) {
            if (screen.getMenu().getCarried().is(Tabletop.DECK) && holdsCards(slot) && slot.allowModification(Minecraft.getInstance().player)) {
                access.tabletop$slotClicked(slot, slot.index, GLFW.GLFW_MOUSE_BUTTON_RIGHT, ClickType.PICKUP);
                // The drag did the clicking, so letting go mustn't click the last slot again.
                access.tabletop$setQuickCrafting(false);
                access.tabletop$setSkipNextRelease(true);
            }
        }
        pending.clear();
    }

    private static boolean holdsCards(@Nullable Slot slot) {
        return slot != null && InventoryClicks.isPile(slot.getItem());
    }
}
