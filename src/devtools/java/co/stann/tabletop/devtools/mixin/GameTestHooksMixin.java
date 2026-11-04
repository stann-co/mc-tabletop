package co.stann.tabletop.devtools.mixin;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import net.neoforged.neoforge.gametest.GameTestHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Outside production NeoForge loads every class marked as holding game tests. Some pack mods (Create: Central
 * Kitchen) ship tests for optional integrations, and loading one whose other mod isn't installed throws and stops
 * the server. Skip such classes with a warning instead; the real pack never loads them at all.
 */
@Mixin(GameTestHooks.class)
abstract class GameTestHooksMixin {
    @Redirect(method = "addGameTestMethods", at = @At(value = "INVOKE",
            target = "Ljava/lang/Class;forName(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;"))
    private static Class<?> tabletop_devtools$loadHolder(String name, boolean initialize, ClassLoader loader) throws ClassNotFoundException {
        try {
            return Class.forName(name, initialize, loader);
        } catch (LinkageError e) {
            skip(name, e);
            return Object.class; // has no game tests, so nothing gets registered
        }
    }

    @Redirect(method = "addGameTestMethods", at = @At(value = "INVOKE", target = "Ljava/lang/Class;getDeclaredMethods()[Ljava/lang/reflect/Method;"))
    private static Method[] tabletop_devtools$listTests(Class<?> holder) {
        try {
            return holder.getDeclaredMethods();
        } catch (LinkageError e) {
            skip(holder.getName(), e);
            return new Method[0];
        }
    }

    private static void skip(String holder, LinkageError e) {
        LogUtils.getLogger().warn("Skipping game tests in {}, they need a mod that isn't installed: {}", holder, e.toString());
    }
}
