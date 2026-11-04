package co.stann.tabletop.client;

import co.stann.tabletop.CardItem;
import co.stann.tabletop.DeckItem;
import co.stann.tabletop.api.TabletopApi;
import co.stann.tabletop.api.TabletopApi.Kind;
import co.stann.tabletop.net.FlipHeldPayload;
import co.stann.tabletop.net.ShuffleDeckPayload;
import co.stann.tabletop.net.TableActionPayload;
import co.stann.tabletop.net.TableActionPayload.Action;
import co.stann.tabletop.table.PlaySurfaceBlock;
import co.stann.tabletop.table.TableObject;
import co.stann.tabletop.table.TableObject.Chips;
import co.stann.tabletop.table.TableObject.Pile;
import co.stann.tabletop.table.TableSurface;
import co.stann.tabletop.table.TableSurface.Placed;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client side of playing at a table or playmat: works out what the player is aiming at, keeps the ghost preview's
 * rotation and chip count, and turns clicks and Ctrl+scroll into {@link TableActionPayload}s.
 *
 * <p>Right-click puts things down, left-click picks them up, and the modifier acts on the whole thing. The modifier
 * is Ctrl, which works while sitting in a chair (sneaking would stand the player up).
 */
public final class TableInteraction {
    private static int rotationOffset;
    private static int chipAmount = 1;

    // Clicks act once per press. NeoForge also reports a held attack every tick and a held use every few ticks.
    private static int tick;
    private static boolean attackWasDown;
    private static boolean useWasDown;
    private static int attackActedTick = -1;
    private static int useActedTick = -1;

    /** Longest press of the modifier, in ticks, that still counts as a tap. */
    private static final int TAP_TICKS = 10;
    private static boolean modifierWasDown;
    private static boolean modifierUsed;
    private static int modifierPressedTick;

    private TableInteraction() {}

    /**
     * Where a held item would go. {@code base} is how high it starts above the surface, in table pixels, and
     * {@code faceUp} which way up a card would land.
     */
    public record Ghost(Kind kind, int gx, int gz, int rot, int amount, float base, boolean valid, boolean faceUp) {}

    /** What the crosshair is on: the table block, an object on it if any, and a ghost if holding something placeable. */
    public record Aim(BlockPos table, @Nullable Placed hovered, @Nullable Ghost ghost) {}

    static void register(IEventBus bus) {
        bus.addListener(TableInteraction::onClick);
        bus.addListener(TableInteraction::onScroll);
        bus.addListener(TableInteraction::onTick);
    }

    @Nullable
    public static Aim aim() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isSpectator()
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !(mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof PlaySurfaceBlock)) {
            return null;
        }
        ItemStack held = player.getMainHandItem();
        Kind kind = TabletopApi.kindOf(held);
        if (kind == null && !held.isEmpty()) {
            return null;
        }
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);
        double reach = player.blockInteractionRange();
        BlockPos table = hit.getBlockPos();
        Placed hovered = TableSurface.raycast(mc.level, table, eye, look, reach);
        Ghost ghost = kind == null ? null : ghost(player, held, kind, table, hovered, eye, look, reach);
        return new Aim(table, hovered, ghost);
    }

    @Nullable
    private static Ghost ghost(LocalPlayer player, ItemStack held, Kind kind, BlockPos table, @Nullable Placed hovered, Vec3 eye, Vec3 look, double reach) {
        // Holding the modifier lays cards the other way up from how they'd land by default.
        boolean flipped = kind == Kind.CARDS && TabletopClient.modifierDown();
        // Aiming at a matching pile or stack puts the item on top of it; a card lands the way the card under it lies.
        // A full pile, or a chip stack with another label, shows the ghost as blocked.
        if (hovered != null && hovered.object().kind() == kind && kind != Kind.TOKENS) {
            TableObject target = hovered.object();
            int amount = target instanceof Chips ? Math.max(1, Math.min(Math.min(chipAmount, held.getCount()), TableSurface.MAX_CHIPS - target.size())) : 1;
            boolean faceUp = (target instanceof Pile pile && pile.top().faceUp()) != flipped;
            return new Ghost(kind, target.gx, target.gz, target.rot, amount, target.height(), target.accepts(held), faceUp);
        }
        double feltY = TableSurface.surfaceY(player.level(), table);
        if (look.y >= 0) {
            return null;
        }
        double distance = (feltY - eye.y) / look.y;
        if (distance <= 0 || distance > reach) {
            return null;
        }
        int gx = (int) Math.round((eye.x + look.x * distance) * TableSurface.GRID_PER_BLOCK);
        int gz = (int) Math.round((eye.z + look.z * distance) * TableSurface.GRID_PER_BLOCK);
        int rot = kind == Kind.CHIPS ? 0 : Math.floorMod(facingRotation(player) + rotationOffset, TableSurface.ROTATIONS);
        int amount = kind == Kind.CHIPS ? Math.min(chipAmount, held.getCount()) : 1;
        boolean valid = TableSurface.fits(player.level(), table.getY(), kind, gx, gz, rot, null);
        // A card keeps the way up it has in hand (the way it lay when picked up); a deck goes down face-down.
        boolean faceUp = (TabletopApi.isCard(held) && CardItem.faceUp(held)) != flipped;
        return new Ghost(kind, gx, gz, rot, amount, 0, valid, faceUp);
    }

    /** The rotation that lays a card the right way up for the player, so the offset is relative to where they face. */
    private static int facingRotation(LocalPlayer player) {
        return Math.floorMod(Math.round(player.getDirection().toYRot() / 15) + TableSurface.ROTATIONS / 2, TableSurface.ROTATIONS);
    }

    private static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        tick++;
        attackWasDown = mc.options.keyAttack.isDown();
        useWasDown = mc.options.keyUse.isDown();
        watchModifierTap(mc);
        watchFlippedPreview(mc);
    }

    /**
     * Holding the modifier at a table previews a card or deck landing the other way up. When that shows a face the
     * player couldn't otherwise see (a face-down card, or a deck's top card), the others see a faint peek at their hands.
     */
    private static void watchFlippedPreview(Minecraft mc) {
        if (mc.screen != null || mc.player == null || !TabletopClient.modifierDown()) {
            return;
        }
        Aim aim = aim();
        if (aim == null || aim.ghost() == null || aim.ghost().kind() != Kind.CARDS || !aim.ghost().faceUp()) {
            return;
        }
        ItemStack held = mc.player.getMainHandItem();
        if (held.getItem() instanceof DeckItem || !CardItem.faceUp(held)) {
            Controls.peeking(true);
        }
    }

    /**
     * Tapping the modifier on its own while holding a card turns it over, and every other card you carry to match. Only
     * a short tap with nothing else done counts, so Ctrl+scroll, Ctrl+click, sprinting (Ctrl is also the sprint key)
     * and walking never do it. A deck is shuffled with modifier + right-click instead, so a stray tap never shuffles.
     */
    private static void watchModifierTap(Minecraft mc) {
        boolean down = TabletopClient.modifierDown();
        if (mc.screen != null || mc.options.keyUp.isDown()) {
            modifierUsed = true;
        }
        if (down && !modifierWasDown) {
            modifierPressedTick = tick;
            modifierUsed = false;
        } else if (!down && modifierWasDown && !modifierUsed && tick - modifierPressedTick <= TAP_TICKS && mc.player != null) {
            if (TabletopApi.isCard(mc.player.getMainHandItem())) {
                PacketDistributor.sendToServer(new FlipHeldPayload(true));
            }
        }
        modifierWasDown = down;
    }

    private static boolean freshPress(boolean wasDown, int actedTick) {
        return !wasDown && actedTick != tick;
    }

    private static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        boolean modifier = TabletopClient.modifierDown();
        modifierUsed |= modifier;
        if (event.isUseItem()) {
            boolean fresh = freshPress(useWasDown, useActedTick);
            Aim aim = aim();
            Ghost ghost = aim != null ? aim.ghost() : null;
            if (ghost != null) {
                // With the modifier, cards land the other way up (the ghost shows it).
                Action action = modifier && ghost.kind() == Kind.CARDS ? Action.PLACE_FLIPPED : Action.PLACE;
                use(event, fresh && ghost.valid(), () -> send(action, aim.table(), ghost.gx(), ghost.gz(), ghost.rot(), ghost.amount()));
            } else if (modifier && TabletopApi.isCard(held)) {
                // Away from a table, the modifier turns over the card in your hand, or shuffles the deck.
                use(event, fresh, () -> PacketDistributor.sendToServer(new FlipHeldPayload(false)));
            } else if (modifier && held.getItem() instanceof DeckItem) {
                use(event, fresh, () -> PacketDistributor.sendToServer(ShuffleDeckPayload.INSTANCE));
            } else if (modifier && held.isEmpty() && aim != null && aim.hovered() != null) {
                use(event, fresh, () -> send(Action.FLIP, aim.table(), aim.hovered().object(), 0));
            }
        } else if (event.isAttack()) {
            Aim aim = aim();
            if (aim == null) {
                return;
            }
            Placed hovered = aim.hovered();
            boolean holdingTabletopItem = TabletopApi.kindOf(held) != null;
            if (!holdingTabletopItem && hovered == null) {
                return; // an empty hand on bare felt breaks the table as usual
            }
            boolean fresh = freshPress(attackWasDown, attackActedTick);
            Action action = null;
            if (held.getItem() instanceof DeckItem) {
                // A pile gives up its top card, or with the modifier the whole pile.
                if (hovered != null && hovered.object() instanceof Pile) {
                    action = modifier ? Action.SWEEP_PILE : Action.SWEEP_TOP;
                }
            } else if (hovered != null) {
                action = modifier ? Action.TAKE_ALL : Action.TAKE_ONE;
            }
            // Never start breaking the table while handling cards and chips.
            consume(event, fresh && action != null);
            if (fresh && action != null) {
                attackActedTick = tick;
                send(action, aim.table(), hovered.object(), 0);
            }
        }
    }

    private static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (Minecraft.getInstance().screen != null || event.getScrollDeltaY() == 0 || !TabletopClient.modifierDown()) {
            return;
        }
        modifierUsed = true;
        Aim aim = aim();
        if (aim == null) {
            return;
        }
        int step = event.getScrollDeltaY() > 0 ? 1 : -1;
        Ghost ghost = aim.ghost();
        if (ghost != null) {
            if (ghost.kind() == Kind.CHIPS) {
                chipAmount = Mth.clamp(chipAmount + step, 1, TableSurface.MAX_CHIPS);
            } else {
                rotationOffset = Math.floorMod(rotationOffset + step, TableSurface.ROTATIONS);
            }
            event.setCanceled(true);
        } else if (aim.hovered() != null && aim.hovered().object().kind() != Kind.CHIPS) {
            TableObject object = aim.hovered().object();
            send(Action.ROTATE, aim.table(), object, object.rot + step);
            event.setCanceled(true);
        }
    }

    /** Stops the vanilla click, swinging the arm only if something actually happened. */
    private static void consume(InputEvent.InteractionKeyMappingTriggered event, boolean acted) {
        event.setCanceled(true);
        event.setSwingHand(acted);
    }

    /** Takes over a right-click, doing {@code action} if it should act (once per press). */
    private static void use(InputEvent.InteractionKeyMappingTriggered event, boolean acts, Runnable action) {
        consume(event, acts);
        if (acts) {
            useActedTick = tick;
            action.run();
        }
    }

    private static void send(Action action, BlockPos table, TableObject object, int rot) {
        send(action, table, object.gx, object.gz, rot, 0);
    }

    private static void send(Action action, BlockPos table, int gx, int gz, int rot, int amount) {
        PacketDistributor.sendToServer(new TableActionPayload(action, table, gx, gz, rot, amount));
    }
}
