package co.stann.tabletop.mixin;

import co.stann.tabletop.DeckItem;
import co.stann.tabletop.Tabletop;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Q with a deck in hand drops its top card, like one item off a stack; Ctrl+Q still drops the whole deck. */
@Mixin(Inventory.class)
abstract class InventoryMixin {
    @Shadow
    public int selected;

    @Shadow
    public abstract ItemStack getSelected();

    @Shadow
    public abstract void setItem(int slot, ItemStack stack);

    @Inject(method = "removeFromSelected", at = @At("HEAD"), cancellable = true)
    private void tabletop$dropTopCard(boolean all, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack deck = getSelected();
        if (all || !deck.is(Tabletop.DECK) || deck.getCount() != 1) {
            return;
        }
        ItemStack rest = deck.copy();
        ItemStack top = DeckItem.takeTop(rest);
        if (!top.isEmpty()) {
            setItem(selected, DeckItem.settle(rest));
            cir.setReturnValue(top);
        }
    }
}
