package co.stann.tabletop.devtools.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Registrate (bundled with Create) stops the game in dev environments when an addon registered a callback for an
 * entry that never got registered; in production it only logs a warning. Some of the pack's Create addons trip
 * this, so make dev runs behave like the real pack here.
 */
@Mixin(targets = "com.tterrag.registrate.AbstractRegistrate")
abstract class AbstractRegistrateMixin {
    @Redirect(method = "onRegister", at = @At(value = "INVOKE", target = "Lcom/tterrag/registrate/AbstractRegistrate;isDevEnvironment()Z"))
    private boolean tabletop_devtools$warnLikeProduction() {
        return false;
    }
}
