package co.stann.tabletop.mixin;

import co.stann.tabletop.InventoryClicks;
import java.util.Set;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inventory clicks that make cards and decks act like stacks of items where NeoForge has no event for it: left-dragging
 * a deck splits it, Q drops its top card, and double-clicking collects cards (see {@link InventoryClicks}).
 */
@Mixin(AbstractContainerMenu.class)
abstract class AbstractContainerMenuMixin {
    @Shadow
    private int quickcraftType;
    @Shadow
    private int quickcraftStatus;
    @Shadow
    @Final
    private Set<Slot> quickcraftSlots;

    @Shadow
    protected abstract void resetQuickCraft();

    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void tabletop$cardClicks(int slotId, int button, ClickType type, Player player, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        boolean handled = switch (type) {
            case QUICK_CRAFT -> tabletop$splitDrag(menu, slotId, button);
            case THROW -> InventoryClicks.throwTopCard(menu, slotId, button, player);
            case PICKUP_ALL -> InventoryClicks.collectCards(menu, slotId, button, player);
            default -> false;
        };
        if (handled) {
            ci.cancel();
        }
    }

    /** An even left-drag with a deck: each slot dragged over gets an equal share, which a stack of one never could. */
    private boolean tabletop$splitDrag(AbstractContainerMenu menu, int slotId, int button) {
        int header = AbstractContainerMenu.getQuickcraftHeader(button);
        if (quickcraftStatus != 1 || quickcraftType != 0 || !InventoryClicks.canSplit(menu.getCarried())) {
            return false;
        }
        if (header == 1) {
            Slot slot = menu.slots.get(slotId);
            if (InventoryClicks.canSplitInto(menu, slot, menu.getCarried(), quickcraftSlots.size())) {
                quickcraftSlots.add(slot);
            }
            return true;
        }
        if (header == 2 && quickcraftSlots.size() > 1) {
            InventoryClicks.split(menu, quickcraftSlots);
            resetQuickCraft();
            return true;
        }
        return false;
    }
}
