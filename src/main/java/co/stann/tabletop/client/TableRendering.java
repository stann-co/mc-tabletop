package co.stann.tabletop.client;

import co.stann.tabletop.CardItem;
import co.stann.tabletop.Tabletop;
import co.stann.tabletop.table.TableObject;
import co.stann.tabletop.table.TableObject.Chips;
import co.stann.tabletop.table.TableObject.Pile;
import co.stann.tabletop.table.TableObject.PlacedCard;
import co.stann.tabletop.table.TableObject.Token;
import co.stann.tabletop.table.TableSurface;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Draws cards, chips and tokens lying on a play surface using their item models, so whatever art an item has is
 * also what shows on the table. All methods expect the pose at the object's centre, level with the surface.
 */
public final class TableRendering {
    private TableRendering() {}

    public static void render(TableObject object, PoseStack pose, MultiBufferSource buffers, Level level, int light) {
        switch (object) {
            case Pile pile -> {
                PlacedCard top = pile.top();
                // Clients don't know face-down cards; any card drawn face-down shows its back anyway.
                Item shown = top.card() != null ? top.card() : Tabletop.CARD_STOCK.get();
                renderCards(CardItem.stack(shown, true, top.backing()), top.faceUp(), pile.size(), pile.rot, pose, buffers, level, light);
            }
            case Chips chips -> renderChips(chips.items(), pose, buffers, level, light);
            case Token token -> renderToken(new ItemStack(token.item), token.rot, pose, buffers, level, light);
        }
    }

    /** Draws a card flat on the table; a pile of {@code count} cards is drawn as one card as thick as the pile. */
    public static void renderCards(ItemStack card, boolean faceUp, int count, int rot, PoseStack pose, MultiBufferSource buffers, Level level, int light) {
        float height = Math.max(1, count) * TableSurface.CARD_THICKNESS / 16;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation((float) -TableSurface.angle(rot)));
        pose.translate(0, height / 2, 0);
        // Item models stand facing south: tip them back so the face looks up with the card's top to the north.
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        // They are a block across and 1/16 block thick: shrink to card size and stretch to the pile's height.
        pose.scale(TableSurface.CARD_SCALE, TableSurface.CARD_SCALE, height * 16);
        if (faceUp && !CardItem.faceUp(card)) {
            card = card.copy(); // a face-down card in hand shows its back even to you, so turn the drawn copy up
            CardItem.setFaceUp(card, true);
        }
        // Drawn as held by you, a card shows its face; drawn by nobody, it shows the back (see CardFaces).
        Minecraft.getInstance().getItemRenderer().renderStatic(faceUp ? Minecraft.getInstance().player : null, card,
                ItemDisplayContext.NONE, false, pose, buffers, level, light, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    /**
     * Draws a stack of chips, bottom to top. Each run of like chips is drawn as a single chip as thick as the run, since
     * every chip is a whole item model; stacks on tables sort themselves so like chips are together.
     */
    public static void renderChips(List<Item> chips, PoseStack pose, MultiBufferSource buffers, Level level, int light) {
        for (int start = 0, end; start < chips.size(); start = end) {
            Item chip = chips.get(start);
            end = start + 1;
            while (end < chips.size() && chips.get(end) == chip) {
                end++;
            }
            pose.pushPose();
            pose.translate(0, (start + end) / 2F * TableSurface.CHIP_THICKNESS / 16, 0);
            flat(new ItemStack(chip), TableSurface.CHIP_SCALE, end - start, pose, buffers, level, light);
            pose.popPose();
        }
    }

    public static void renderToken(ItemStack token, int rot, PoseStack pose, MultiBufferSource buffers, Level level, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation((float) -TableSurface.angle(rot)));
        pose.translate(0, TableSurface.TOKEN_THICKNESS / 32, 0);
        flat(token, TableSurface.TOKEN_SCALE, 1, pose, buffers, level, light);
        pose.popPose();
    }

    /**
     * Draws an item model lying flat, top of the texture to the north, at {@code scale} of a block across and
     * {@code thickness} times as thick as that makes it.
     */
    private static void flat(ItemStack stack, float scale, int thickness, PoseStack pose, MultiBufferSource buffers, Level level, int light) {
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.scale(scale, scale, scale * thickness);
        Minecraft.getInstance().getItemRenderer().renderStatic(null, stack, ItemDisplayContext.NONE, false,
                pose, buffers, level, light, OverlayTexture.NO_OVERLAY, 0);
    }
}
