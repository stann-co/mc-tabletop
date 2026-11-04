package co.stann.tabletop.client;

import co.stann.tabletop.Tabletop;
import co.stann.tabletop.DeckItem;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = Tabletop.MODID, dist = Dist.CLIENT)
public class TabletopClient {
    /** Held to rotate, flip, or take a whole pile at a table. Never sneak: that would stand a seated player up. */
    public static final KeyMapping MODIFIER = new KeyMapping("key.tabletop.modifier", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, "key.categories.tabletop");

    public TabletopClient(IEventBus modBus) {
        modBus.addListener(TabletopClient::registerRenderers);
        modBus.addListener(TabletopClient::registerKeys);
        modBus.addListener(TabletopClient::registerScreens);
        modBus.addListener(TabletopClient::registerTooltips);
        modBus.addListener(TabletopClient::registerModels);
        NeoForge.EVENT_BUS.addListener(Controls::hideCardId);
        TableInteraction.register(NeoForge.EVENT_BUS);
        TableOverlay.register(NeoForge.EVENT_BUS);
        DeckDrag.register(NeoForge.EVENT_BUS);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Tabletop.PLAY_SURFACE_ENTITY.get(), PlaySurfaceRenderer::new);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(MODIFIER);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Tabletop.DECK_MENU.get(), DeckScreen::new);
    }

    private static void registerTooltips(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(DeckItem.DeckContents.class, DeckTooltip::new);
    }

    private static void registerModels(ModelEvent.RegisterAdditional event) {
        CardFaces.registerThemes(event);
    }

    /**
     * Whether the modifier (Left Ctrl unless rebound) is held. Left Ctrl is also the sprint key, and a key can only
     * drive one mapping reliably, so a keyboard binding is read straight from the keyboard; that also works with a
     * screen open.
     */
    public static boolean modifierDown() {
        return keyDown(MODIFIER);
    }

    /** Whether a mapping's key is held: a keyboard binding is read straight from the keyboard. */
    private static boolean keyDown(KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        if (key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue()) {
            return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key.getValue());
        }
        return mapping.isDown();
    }

    /** The modifier key by name, for tooltips: "Left Control". */
    public static Component modifierName() {
        return MODIFIER.getTranslatedKeyMessage();
    }
}
