package co.stann.tabletop.client.mixin;

import co.stann.tabletop.client.CardFaces;
import com.llamalad7.mixinextras.sugar.Local;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shows the card back for cards that aren't yours to see, and each deck's theme icon, see {@link CardFaces}. Every
 * item is drawn by {@code render}, which knows where it is drawn (a screen, a hand, the ground); the holder comes from
 * the {@code getModel} lookup just before it.
 */
@Mixin(ItemRenderer.class)
abstract class ItemRendererMixin {
    @Inject(method = "getModel", at = @At("HEAD"))
    private void tabletop$noteHolder(ItemStack stack, @Nullable Level level, @Nullable LivingEntity entity, int seed, CallbackInfoReturnable<BakedModel> cir) {
        CardFaces.noteHolder(entity);
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private BakedModel tabletop$themedModel(BakedModel model, @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) ItemDisplayContext context) {
        return CardFaces.model(stack, context, model);
    }
}
