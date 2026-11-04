package co.stann.tabletop.client;

import co.stann.tabletop.Backing;
import co.stann.tabletop.CardItem;
import co.stann.tabletop.Tabletop;
import co.stann.tabletop.DeckItem;
import co.stann.tabletop.api.TabletopApi.Kind;
import co.stann.tabletop.client.TableInteraction.Aim;
import co.stann.tabletop.client.TableInteraction.Ghost;
import co.stann.tabletop.table.TableObject;
import co.stann.tabletop.table.TableObject.Chips;
import co.stann.tabletop.table.TableSurface;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Draws the ghost preview, the outline of the hovered object, and labels on chip stacks, in place of the block outline. */
public final class TableOverlay {
    private static final int VALID = 0x55FF55;
    private static final int BLOCKED = 0xFF5555;
    private static final int HOVERED = 0xFFFFFF;
    private static final float GHOST_ALPHA = 0.6F;
    /** How far above the top of a chip stack its label floats, in blocks. */
    private static final double LABEL_LIFT = 0.3;

    private TableOverlay() {}

    static void register(IEventBus bus) {
        bus.addListener(TableOverlay::onHighlight);
        bus.addListener(TableOverlay::onRenderLevel);
    }

    private static void onHighlight(RenderHighlightEvent.Block event) {
        Aim aim = TableInteraction.aim();
        if (aim != null && (aim.ghost() != null || aim.hovered() != null)) {
            event.setCanceled(true);
        }
    }

    private static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Aim aim = TableInteraction.aim();
        if (aim == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = event.getPoseStack();
        Camera camera = event.getCamera();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        if (aim.hovered() != null) {
            TableObject object = aim.hovered().object();
            double top = aim.hovered().table().surfaceY() + object.height() / 16;
            outline(pose, buffers, camera, object.kind(), object.gx, object.gz, object.rot, top, HOVERED);
            if (object instanceof Chips chips) {
                Component label = chips.value() > 0
                        ? Component.translatable("label.tabletop.chips", chips.size(), chips.value())
                        : Component.translatable("label.tabletop.chip_count", chips.size());
                label(pose, buffers, camera, label, object.gx, object.gz, top);
            }
        }

        Ghost ghost = aim.ghost();
        if (ghost != null) {
            ItemStack held = mc.player.getMainHandItem();
            double bottom = TableSurface.surfaceY(mc.level, aim.table()) + ghost.base() / 16;
            int light = LevelRenderer.getLightColor(mc.level, aim.table().above());
            int tint = ghost.valid() ? 0xFFFFFF : BLOCKED;
            MultiBufferSource translucent = type -> new TintedVertexConsumer(buffers.getBuffer(Sheets.translucentItemSheet()), tint, GHOST_ALPHA);
            Vec3 cam = camera.getPosition();
            pose.pushPose();
            pose.translate(TableSurface.toWorld(ghost.gx()) - cam.x, bottom - cam.y, TableSurface.toWorld(ghost.gz()) - cam.z);
            switch (ghost.kind()) {
                case CHIPS -> TableRendering.renderChips(Collections.nCopies(ghost.amount(), held.getItem()), pose, translucent, mc.level, light);
                case TOKENS -> TableRendering.renderToken(held, ghost.rot(), pose, translucent, mc.level, light);
                case CARDS -> {
                    // The ghost shows which way up it will land: a deck put down face-up shows its top card.
                    if (held.getItem() instanceof DeckItem) {
                        List<Item> cards = DeckItem.cards(held);
                        Item top = cards.isEmpty() ? Tabletop.CARD_STOCK.get() : cards.getLast();
                        ItemStack card = CardItem.stack(top, true, Backing.of(held));
                        TableRendering.renderCards(card, ghost.faceUp(), cards.size(), ghost.rot(), pose, translucent, mc.level, light);
                    } else {
                        TableRendering.renderCards(held, ghost.faceUp(), 1, ghost.rot(), pose, translucent, mc.level, light);
                    }
                }
            }
            pose.popPose();
            outline(pose, buffers, camera, ghost.kind(), ghost.gx(), ghost.gz(), ghost.rot(), bottom, ghost.valid() ? VALID : BLOCKED);
            if (ghost.kind() == Kind.CHIPS) {
                double top = bottom + ghost.amount() * TableSurface.CHIP_THICKNESS / 16;
                label(pose, buffers, camera, Component.translatable("label.tabletop.placing_chips", ghost.amount()), ghost.gx(), ghost.gz(), top);
            }
        }
        buffers.endBatch();
    }

    /** A small name tag floating well above a chip stack or token, always facing the camera. */
    private static void label(PoseStack pose, MultiBufferSource buffers, Camera camera, Component text, int gx, int gz, double top) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = camera.getPosition();
        pose.pushPose();
        pose.translate(TableSurface.toWorld(gx) - cam.x, top + LABEL_LIFT - cam.y, TableSurface.toWorld(gz) - cam.z);
        pose.mulPose(camera.rotation());
        pose.scale(0.0125F, -0.0125F, 0.0125F);
        Font font = mc.font;
        int background = (int) (mc.options.getBackgroundOpacity(0.25F) * 255) << 24;
        font.drawInBatch(text, -font.width(text) / 2F, 0, 0xFFFFFFFF, false, pose.last().pose(), buffers,
                Font.DisplayMode.SEE_THROUGH, background, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /** Traces an object's footprint at height {@code y}: a rectangle for cards, a circle for chips and tokens. */
    private static void outline(PoseStack pose, MultiBufferSource buffers, Camera camera, Kind kind, int gx, int gz, int rot, double y, int rgb) {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        Vec3 cam = camera.getPosition();
        Vec3 centre = new Vec3(TableSurface.toWorld(gx) - cam.x, y + 0.002 - cam.y, TableSurface.toWorld(gz) - cam.z);
        Vec3[] corners;
        if (kind == Kind.CARDS) {
            float w = TableSurface.CARD_WIDTH / 32;
            float l = TableSurface.CARD_LENGTH / 32;
            corners = new Vec3[] {turn(-w, -l, rot), turn(w, -l, rot), turn(w, l, rot), turn(-w, l, rot)};
        } else {
            corners = new Vec3[16];
            float r = (kind == Kind.CHIPS ? TableSurface.CHIP_RADIUS : TableSurface.TOKEN_RADIUS) / 16;
            for (int i = 0; i < corners.length; i++) {
                double angle = Math.PI * 2 * i / corners.length;
                corners[i] = new Vec3(Math.cos(angle) * r, 0, Math.sin(angle) * r);
            }
        }
        PoseStack.Pose last = pose.last();
        for (int i = 0; i < corners.length; i++) {
            Vec3 from = centre.add(corners[i]);
            Vec3 to = centre.add(corners[(i + 1) % corners.length]);
            Vec3 normal = to.subtract(from).normalize();
            for (Vec3 end : new Vec3[] {from, to}) {
                lines.addVertex(last, (float) end.x, (float) end.y, (float) end.z).setColor(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 255)
                        .setNormal(last, (float) normal.x, (float) normal.y, (float) normal.z);
            }
        }
    }

    /** A point across ({@code x}) and along ({@code z}) a card turned by {@code rot}, as a world offset. */
    private static Vec3 turn(double x, double z, int rot) {
        double angle = TableSurface.angle(rot);
        return new Vec3(x * Math.cos(angle) - z * Math.sin(angle), 0, x * Math.sin(angle) + z * Math.cos(angle));
    }

    /** Recolours and fades everything drawn through it, for the ghost preview. */
    private record TintedVertexConsumer(VertexConsumer delegate, int tint, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alphaIn) {
            delegate.setColor(red * (tint >> 16 & 255) / 255, green * (tint >> 8 & 255) / 255, blue * (tint & 255) / 255, (int) (alphaIn * alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
