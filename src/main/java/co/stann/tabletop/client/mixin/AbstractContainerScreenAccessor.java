package co.stann.tabletop.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets {@code DeckDrag} click slots during a drag and stop the screen's own release click, as Mouse Tweaks does. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Invoker("findSlot")
    Slot tabletop$findSlot(double mouseX, double mouseY);

    @Invoker("slotClicked")
    void tabletop$slotClicked(Slot slot, int slotId, int button, ClickType type);

    @Accessor("isQuickCrafting")
    void tabletop$setQuickCrafting(boolean quickCrafting);

    @Accessor("skipNextRelease")
    void tabletop$setSkipNextRelease(boolean skip);
}
