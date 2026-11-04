package co.stann.tabletop.client;

import co.stann.tabletop.CardItem;
import co.stann.tabletop.Tabletop;
import co.stann.tabletop.api.TabletopApi;
import co.stann.tabletop.net.PeekPayload;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tooltip lines explaining how an item is used at a poker table. Items and blocks call these from common code, and
 * some mods build tooltips on a dedicated server, so every entry point does nothing there: the keyboard and the
 * modifier key mapping only exist on the client.
 */
public final class Controls {
    /** Ticks between peek messages while a deck's cards stay on show. */
    private static final int PEEK_RESEND = 8;
    private static long lastPeekSent = Long.MIN_VALUE / 2;

    private Controls() {}

    private static boolean onClient() {
        return FMLEnvironment.dist.isClient();
    }

    /** The modifier key by name, for tooltips: "Left Control". */
    public static Component modifierName() {
        return onClient() ? TabletopClient.modifierName() : Component.translatable("key.tabletop.modifier");
    }

    /** Adds lang keys tooltip.tabletop.{item}.controls.1, .2, ... while Shift is held, or a hint to hold Shift. */
    public static void append(List<Component> tooltip, String item) {
        if (!onClient()) {
            return;
        }
        if (!Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.tabletop.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        // Lines name the modifier keys by their current bindings, so rebinding them keeps the text accurate.
        Component modifier = modifierName();
        String prefix = "tooltip.tabletop." + item + ".controls.";
        for (int line = 1; Language.getInstance().has(prefix + line); line++) {
            tooltip.add(Component.translatable(prefix + line, modifier).withStyle(ChatFormatting.GRAY));
        }
    }

    /** Whether a deck's tooltip shows its cards: only while the modifier is held, so a glance doesn't. */
    public static boolean showDeckCards() {
        return onClient() && TabletopClient.modifierDown();
    }

    /**
     * Tells the server the player is looking at hidden cards, a few times a second at most. A {@code faint} peek is a
     * glimpse, like previewing a card the other way up at a table.
     */
    public static void peeking(boolean faint) {
        if (!onClient()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        if (now >= lastPeekSent && now - lastPeekSent < PEEK_RESEND) {
            return;
        }
        lastPeekSent = now;
        PacketDistributor.sendToServer(new PeekPayload(faint));
    }

    /** Adds a hint to hold the modifier to see a deck's cards, while they're hidden. */
    public static void appendDeckHint(List<Component> tooltip) {
        if (onClient() && !showDeckCards()) {
            tooltip.add(Component.translatable("tooltip.tabletop.deck.hold_to_see", modifierName()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * A face-down card's tooltip mustn't give it away: advanced tooltips (F3+H) would show its item id, so that line is
     * left out, and cards from other mods are named "Card" here as Tabletop's own cards name themselves.
     */
    static void hideCardId(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!TabletopApi.isCard(stack) || CardItem.faceUp(stack) || stack.is(Tabletop.CARD_STOCK)) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        tooltip.removeIf(line -> line.getString().equals(id));
        if (!(stack.getItem() instanceof CardItem) && !tooltip.isEmpty()) {
            tooltip.set(0, CardItem.hiddenName());
        }
    }
}
