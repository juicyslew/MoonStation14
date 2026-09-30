package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/** Client presentation and intent only; all hand occupancy and transfer remain server-owned. */
public final class BodyHandClient {
    private static final net.minecraft.client.KeyMapping PICKUP = new net.minecraft.client.KeyMapping("key.moonstation14.body_pickup",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.moonstation14");
    private static final net.minecraft.client.KeyMapping DROP = new net.minecraft.client.KeyMapping("key.moonstation14.body_drop",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.moonstation14");
    private static final net.minecraft.client.KeyMapping SELECT_HAND = new net.minecraft.client.KeyMapping("key.moonstation14.select_hand",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.moonstation14");
    private static final net.minecraft.client.KeyMapping INSERT_POUCH = new net.minecraft.client.KeyMapping("key.moonstation14.pouch_insert",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.moonstation14");
    private static final net.minecraft.client.KeyMapping EXTRACT_POUCH = new net.minecraft.client.KeyMapping("key.moonstation14.pouch_extract",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.moonstation14");
    private static final net.minecraft.client.KeyMapping INVENTORY = new net.minecraft.client.KeyMapping("key.moonstation14.body_inventory",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "key.categories.moonstation14");
    private static final BodyHandClientPolicy POLICY = new BodyHandClientPolicy();
    private static Object connection, level, player;
    private static Mob cameraBody;
    private static long cameraEpoch;
    private static long clock;

    private BodyHandClient() { }

    public static void install() {
        BodyHandStateSnapshot.setClientHandler(BodyHandClient::onSnapshot);
        BodyHandActionResult.setClientHandler(BodyHandClient::onResult);
    }

    public static void registerKeys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(PICKUP);
        event.register(DROP);
        event.register(SELECT_HAND);
        event.register(INSERT_POUCH);
        event.register(EXTRACT_POUCH);
        event.register(INVENTORY);
    }

    public static void reset() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof BodyInventoryScreen) mc.setScreen(null);
        POLICY.reset();
        connection = level = player = null;
        cameraBody = null;
        cameraEpoch = 0;
        while (PICKUP.consumeClick()) { }
        while (DROP.consumeClick()) { }
        while (SELECT_HAND.consumeClick()) { }
        while (INSERT_POUCH.consumeClick()) { }
        while (EXTRACT_POUCH.consumeClick()) { }
        while (INVENTORY.consumeClick()) { }
    }

    private static Mob currentBody() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.player != null && mc.player.isSpectator() && mc.getConnection() != null
                ? GhostControlClient.ownedCharacterForHud() : null;
    }

    private static void reconcile() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (connection != mc.getConnection()) {
            reset();
            connection = mc.getConnection();
        }
        if (level != mc.level || player != mc.player) {
            POLICY.suspend();
            if (mc.screen instanceof BodyInventoryScreen) mc.setScreen(null);
            level = mc.level;
            player = mc.player;
            cameraBody = null;
            cameraEpoch = 0;
        }
        Mob body = currentBody();
        long epoch = GhostControlClient.committedCharacterEpoch();
        if (cameraBody != body || cameraEpoch != epoch || body == null || epoch == 0) {
            POLICY.suspend();
            if (mc.screen instanceof BodyInventoryScreen) mc.setScreen(null);
            cameraBody = body;
            cameraEpoch = epoch;
            if (body == null || epoch == 0) return;
        }
        POLICY.bind(mc.player.getUUID(), body.getUUID(), epoch);
        if (mc.screen != null && !(mc.screen instanceof BodyInventoryScreen)) POLICY.suspend();
    }

    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        clock++;
        reconcile();
        if (cameraBody == null) {
            while (PICKUP.consumeClick()) { }
            while (DROP.consumeClick()) { }
            while (SELECT_HAND.consumeClick()) { }
            while (INSERT_POUCH.consumeClick()) { }
            while (EXTRACT_POUCH.consumeClick()) { }
            while (INVENTORY.consumeClick()) { }
            return;
        }
        boolean inventory = INVENTORY.consumeClick();
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof BodyInventoryScreen) {
            if (inventory) mc.setScreen(null);
        } else if (inventory && mc.screen == null) {
            mc.setScreen(new BodyInventoryScreen());
        }
        POLICY.timeout(clock);
        long sequence = POLICY.query(clock);
        if (sequence != 0) PacketDistributor.sendToServer(new BodyHandStateQuery(POLICY.queryEpoch(), sequence));
        boolean pickup = PICKUP.consumeClick();
        boolean drop = DROP.consumeClick();
        boolean select = SELECT_HAND.consumeClick();
        boolean insert = INSERT_POUCH.consumeClick();
        boolean extract = EXTRACT_POUCH.consumeClick();
        if (mc.screen != null) return;
        if (pickup) request(BodyHandActionRequest.Action.PICKUP);
        else if (drop) request(BodyHandActionRequest.Action.DROP);
        else if (select) selectHand();
        else if (insert) requestPouch(BodyHandActionRequest.Action.INSERT_POUCH);
        else if (extract) requestPouch(BodyHandActionRequest.Action.EXTRACT_POUCH);
    }

    private static void selectHand() {
        BodyHandStateSnapshot snapshot = POLICY.snapshot();
        if (snapshot == null || snapshot.hands().size() != 2) return;
        var other = snapshot.hands().stream().filter(hand -> !hand.id().equals(snapshot.activeHand())).toList();
        if (other.size() != 1) return;
        long sequence = POLICY.action(clock);
        if (sequence == 0) return;
        PacketDistributor.sendToServer(new BodyHandActionRequest(BodyHandActionRequest.Action.SELECT_HAND,
                other.getFirst().id(), snapshot.epoch(), sequence, snapshot.revision(), null, null));
    }

    static BodyHandClientPolicy.InventoryView inventoryView() {
        reconcile();
        return cameraBody == null ? null : POLICY.inventoryView();
    }

    static boolean inventoryKey(int keyCode, int scanCode) {
        return INVENTORY.matches(keyCode, scanCode);
    }

    static void selectInventoryHand(String id) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof BodyInventoryScreen)) return;
        reconcile();
        var view = POLICY.inventoryView();
        if (view == null || view.activeHand().equals(id)
                || !view.first().id().equals(id) && !view.second().id().equals(id)) return;
        var snapshot = POLICY.snapshot();
        long sequence = POLICY.action(clock);
        if (sequence != 0) PacketDistributor.sendToServer(new BodyHandActionRequest(
                BodyHandActionRequest.Action.SELECT_HAND, id, snapshot.epoch(), sequence, snapshot.revision(), null, null));
    }

    static void inventoryPouch(BodyHandActionRequest.Action action) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof BodyInventoryScreen)) return;
        reconcile();
        if (POLICY.inventoryView() != null) requestPouch(action);
    }

    static void inventoryEquipment(BodyHandActionRequest.Action action) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof BodyInventoryScreen)) return;
        reconcile();
        var view = POLICY.inventoryView();
        if (view == null) return;
        String slotId = switch (action) {
            case EQUIP_BELT, UNEQUIP_BELT, STORE_BELT, TAKE_BELT -> "belt";
            case EQUIP_BACK, UNEQUIP_BACK, STORE_BACK, TAKE_BACK -> "back";
            default -> null;
        };
        if (slotId == null) return;
        var equipment = "belt".equals(slotId) ? view.belt() : view.back();
        if (equipment == null || !equipment.allows(action)) return;
        var snapshot = POLICY.snapshot();
        var active = view.first().id().equals(view.activeHand()) ? view.first() : view.second();
        boolean equip = action == BodyHandActionRequest.Action.EQUIP_BELT
                || action == BodyHandActionRequest.Action.EQUIP_BACK;
        String token = equip ? active.token() : equipment.slot().token();
        if (token == null) return;
        long sequence = POLICY.action(clock);
        if (sequence != 0) PacketDistributor.sendToServer(new BodyHandActionRequest(action, active.id(),
                snapshot.epoch(), sequence, snapshot.revision(), null, token));
    }

    private static void requestPouch(BodyHandActionRequest.Action action) {
        var view = POLICY.pouchView();
        if (view == null || action == BodyHandActionRequest.Action.INSERT_POUCH && !view.canInsert()
                || action == BodyHandActionRequest.Action.EXTRACT_POUCH && !view.canExtract()) return;
        BodyHandStateSnapshot snapshot = POLICY.snapshot();
        long sequence = POLICY.action(clock);
        if (sequence == 0) return;
        PacketDistributor.sendToServer(new BodyHandActionRequest(action, view.pouch().id(), snapshot.epoch(),
                sequence, snapshot.revision(), null, view.pouch().token()));
    }

    private static void request(BodyHandActionRequest.Action action) {
        BodyHandStateSnapshot snapshot = POLICY.snapshot();
        if (snapshot == null) return;
        BodyHandStateSnapshot.Hand active = snapshot.hands().stream()
                .filter(hand -> hand.id().equals(snapshot.activeHand())).findFirst().orElse(null);
        if (active == null) return;
        UUID target = null;
        if (action == BodyHandActionRequest.Action.PICKUP) {
            if (active.token() != null) return;
            target = lookTarget(cameraBody);
            if (target == null) { POLICY.noItemUnderCursor(clock); return; }
        } else if (active.token() == null) return;
        long sequence = POLICY.action(clock);
        if (sequence == 0) return;
        PacketDistributor.sendToServer(new BodyHandActionRequest(action, active.id(), snapshot.epoch(),
                sequence, snapshot.revision(), target, action == BodyHandActionRequest.Action.DROP ? active.token() : null));
    }

    /** Only a nomination: server ray, reach, block occlusion, and item identity are authoritative. */
    private static UUID lookTarget(Mob body) {
        Vec3 start = body.getEyePosition();
        Vec3 end = start.add(body.getLookAngle().scale(5));
        var block = body.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, body));
        double blockerDistance = start.distanceTo(block.getLocation());
        double maxDistance = blockerDistance * blockerDistance;
        UUID candidate = null;
        boolean ambiguous = false;
        for (ItemEntity item : body.level().getEntitiesOfClass(ItemEntity.class,
                new AABB(start, end).inflate(BodyItemTargetProbe.TARGET_MARGIN),
                entity -> entity.isAlive() && !entity.getItem().isEmpty())) {
            var box = item.getBoundingBox();
            var expanded = box.inflate(BodyItemTargetProbe.TARGET_MARGIN);
            var hit = expanded.contains(start) ? java.util.Optional.of(start) : expanded.clip(start, end);
            if (hit.isEmpty()) continue;
            Vec3 look = body.getLookAngle();
            double nearFace = (look.x >= 0 ? box.minX : box.maxX) * look.x
                    + (look.y >= 0 ? box.minY : box.maxY) * look.y
                    + (look.z >= 0 ? box.minZ : box.maxZ) * look.z - start.dot(look);
            if (nearFace >= blockerDistance) continue;
            double distance = hit.orElseThrow().distanceToSqr(start);
            if (distance == maxDistance && candidate != null) ambiguous = true;
            if (distance < maxDistance) { maxDistance = distance; candidate = item.getUUID(); ambiguous = false; }
        }
        return ambiguous ? null : candidate;
    }

    private static void onSnapshot(BodyHandStateSnapshot snapshot) {
        reconcile();
        POLICY.accept(snapshot);
    }

    private static void onResult(BodyHandActionResult result) {
        reconcile();
        POLICY.result(result, clock);
    }

    public static void render(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        Mob body = currentBody();
        if (!BodyHandClientPolicy.showAimCue(body != null && GhostControlClient.committedCharacterEpoch() > 0,
                !mc.options.hideGui, mc.screen == null)) return;
        reconcile();
        int centerX = mc.getWindow().getGuiScaledWidth() / 2;
        int centerY = mc.getWindow().getGuiScaledHeight() / 2;
        // Spectator carrier suppresses vanilla's crosshair and block outline. This cue is visual only;
        // never use the carrier's hit result as body/item authority.
        var graphics = event.getGuiGraphics();
        graphics.fill(centerX - 3, centerY, centerX + 4, centerY + 1, 0xff202020);
        graphics.fill(centerX, centerY - 3, centerX + 1, centerY + 4, 0xff202020);
        graphics.fill(centerX - 2, centerY, centerX + 3, centerY + 1, 0xffeeeeee);
        graphics.fill(centerX, centerY - 2, centerX + 1, centerY + 3, 0xffeeeeee);
        BodyHandStateSnapshot snapshot = POLICY.hudSnapshot();
        String text = null;
        if (snapshot != null) {
            var hand = snapshot.hands().stream().filter(slot -> slot.id().equals(snapshot.activeHand()))
                    .findFirst().orElse(null);
            if (hand != null) text = "Hand " + hand.id() + ": "
                    + (hand.token() == null ? "empty" : hand.itemId() + " x" + hand.count());
        }
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        String feedback = POLICY.feedback();
        if (text == null && feedback == null) return;
        // Never let long item IDs spill off the scaled window or into right-side alerts.
        if (text != null) text = mc.font.plainSubstrByWidth(text, Math.max(0, width / 2 - 12));
        var position = BodyHandClientPolicy.handHudPosition(width, height,
                mc.font.width(text == null ? feedback : text), mc.font.lineHeight);
        if (position == null) return;
        int x = position.x(), y = position.y();
        if (text != null) {
            graphics.fill(x - 3, y - 2, x + mc.font.width(text) + 3, y + mc.font.lineHeight + 2, 0x99000000);
            graphics.drawString(mc.font, text, x, y, 0xffffffff, false);
        }
        int nextY = text == null ? y : y + mc.font.lineHeight + 4;
        var pouch = POLICY.pouchView();
        if (pouch != null && text != null) {
            String contents = pouch.pouch().childToken() == null ? "empty"
                    : pouch.pouch().childItemId() + " x" + pouch.pouch().childCount();
            String line = "Pouch " + pouch.pouch().id() + ": " + contents;
            if (nextY + mc.font.lineHeight + 2 <= height - 36) {
                nextY = drawHudLine(graphics, mc, line, x, nextY, width);
                String hint = pouch.canInsert() ? "J: insert other hand"
                        : pouch.canExtract() ? "K: extract to other hand" : null;
                if (hint != null && nextY + mc.font.lineHeight + 2 <= height - 36)
                    nextY = drawHudLine(graphics, mc, hint, x, nextY, width);
            }
        }
        if (feedback != null && nextY + mc.font.lineHeight + 2 <= height - 36) {
            feedback = feedback.replaceFirst("^Hands: ", "");
            drawHudLine(graphics, mc, feedback, x, nextY, width);
        }
    }

    private static int drawHudLine(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.Minecraft mc,
                                   String line, int x, int y, int width) {
        line = mc.font.plainSubstrByWidth(line, Math.max(0, width / 2 - 12));
        graphics.fill(x - 3, y - 2, x + mc.font.width(line) + 3, y + mc.font.lineHeight + 2, 0x99000000);
        graphics.drawString(mc.font, line, x, y, 0xffffffff, false);
        return y + mc.font.lineHeight + 4;
    }
}
