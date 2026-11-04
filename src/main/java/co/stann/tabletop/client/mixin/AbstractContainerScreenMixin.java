package co.stann.tabletop.client.mixin;

import co.stann.tabletop.InventoryClicks;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a left-drag with a deck mark more than one slot, one per card at most, so it can be split evenly. Vanilla stops
 * at one slot because a deck is a stack of one; the menu side does the split (see {@code AbstractContainerMenuMixin}).
 */
@Mixin(AbstractContainerScreen.class)
abstract class AbstractContainerScreenMixin {
    @Shadow
    @Final
    protected AbstractContainerMenu menu;
    @Shadow
    @Final
    protected Set<Slot> quickCraftSlots;
    @Shadow
    protected boolean isQuickCrafting;
    @Shadow
    private int quickCraftingType;

    @Shadow
    protected abstract Slot findSlot(double mouseX, double mouseY);

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void tabletop$markSplitSlots(double mouseX, double mouseY, int button, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
        if (!isQuickCrafting || quickCraftingType != 0 || Minecraft.getInstance().options.touchscreen().get()
                || !InventoryClicks.canSplit(menu.getCarried())) {
            return;
        }
        Slot slot = findSlot(mouseX, mouseY);
        if (slot != null && InventoryClicks.canSplitInto(menu, slot, menu.getCarried(), quickCraftSlots.size())) {
            quickCraftSlots.add(slot);
        }
        cir.setReturnValue(true);
    }
}
