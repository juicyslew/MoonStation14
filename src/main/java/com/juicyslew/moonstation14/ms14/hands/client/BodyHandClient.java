package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateQuery;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridClose;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridSnapshot;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridAction;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridActionResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldCrossActionResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldWorldCrossAction;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldWorldCrossActionResult;
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
    private static final net.minecraft.client.KeyMapping DROP = new net.minecraft.client.KeyMapping("key.moonstation14.body_drop",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q, "key.categories.moonstation14");
    private static final net.minecraft.client.KeyMapping SELECT_HAND = new net.minecraft.client.KeyMapping("key.moonstation14.select_hand",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.moonstation14");
    private static final net.minecraft.client.KeyMapping STORAGE = new net.minecraft.client.KeyMapping("key.moonstation14.storage",
             com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.moonstation14");
    private static final net.minecraft.client.KeyMapping WORLD_STORAGE = new net.minecraft.client.KeyMapping("key.moonstation14.world_storage",
             com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_E, "key.categories.moonstation14");
    private static final net.minecraft.client.KeyMapping INVENTORY = new net.minecraft.client.KeyMapping("key.moonstation14.body_inventory",
             com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "key.categories.moonstation14");
    // NeoForge's modifier-aware lookup prefers SHIFT-specific mappings to vanilla's NONE-modifier E mapping.
    // Keeping these as real mappings also makes the SS14 defaults visible and rebindable in Controls.
    private static final net.minecraft.client.KeyMapping EQUIP_BAG = new net.minecraft.client.KeyMapping(
            "key.moonstation14.equip_bag", net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME,
            net.neoforged.neoforge.client.settings.KeyModifier.SHIFT, com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B, "key.categories.moonstation14");
    private static final net.minecraft.client.KeyMapping EQUIP_BELT = new net.minecraft.client.KeyMapping(
            "key.moonstation14.equip_belt", net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME,
            net.neoforged.neoforge.client.settings.KeyModifier.SHIFT, com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_E, "key.categories.moonstation14");
    private static final net.minecraft.client.KeyMapping BLOCK_USE = new net.minecraft.client.KeyMapping("key.moonstation14.body_block_use",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.moonstation14");
    private static final BodyHandClientPolicy POLICY = new BodyHandClientPolicy();
    private static final WorldGridClientPolicy WORLD = new WorldGridClientPolicy();
    private static final StoragePopupWorkspace POPUPS = new StoragePopupWorkspace(320, 240);
    private static Object connection, level, player;
    private static Mob cameraBody;
    private static long cameraEpoch;
    private static long clock;
    private static boolean worldResultHandRefreshPending;
    private static UUID worldCrossPendingTarget;
    private static final java.util.Set<UUID> pendingWorldObservations = new java.util.HashSet<>();
    private static final java.util.Map<UUID, Long> worldObservationRetryAt = new java.util.HashMap<>();
    private static boolean contextualWorldKey;
    private static boolean contextualWorldHandled;
    private static final PointerModePolicy POINTER = new PointerModePolicy();

    private BodyHandClient() { }

    public static void install() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(BodyHandClient::contextualKey);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(BodyHandClient::contextualInventoryOpening);
        BodyHandStateSnapshot.setClientHandler(BodyHandClient::onSnapshot);
        BodyHandActionResult.setClientHandler(BodyHandClient::onResult);
        BodyBlockUseResult.setClientHandler(BodyHandClient::onBlockUseResult);
        BodyWorldGridSnapshot.setClientHandler(BodyHandClient::onWorldSnapshot);
        BodyWorldGridActionResult.setClientHandler(BodyHandClient::onWorldResult);
        BodyWorldCrossActionResult.setClientHandler(BodyHandClient::onWorldCrossResult);
        BodyWorldWorldCrossActionResult.setClientHandler(BodyHandClient::onWorldWorldCrossResult);
    }

    public static void registerKeys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        event.register(DROP);
        event.register(SELECT_HAND);
        event.register(STORAGE);
        event.register(WORLD_STORAGE);
        event.register(INVENTORY);
        event.register(BLOCK_USE);
        event.register(EQUIP_BAG);
        event.register(EQUIP_BELT);
    }

    public static void reset() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        closeWorldWindows();
        if (mc.screen instanceof StoragePopupScreen || mc.screen instanceof BodyInventoryScreen) mc.setScreen(null);
        POPUPS.clear();
        POLICY.reset();
        WORLD.clear();
        worldResultHandRefreshPending = false;
        worldCrossPendingTarget = null;
        pendingWorldObservations.clear();
        worldObservationRetryAt.clear();
        contextualWorldKey = false;
        contextualWorldHandled = false;
        POINTER.authorityClear();
        connection = level = player = null;
        cameraBody = null;
        cameraEpoch = 0;
        while (DROP.consumeClick()) { }
        while (SELECT_HAND.consumeClick()) { }
        while (STORAGE.consumeClick()) { }
        while (WORLD_STORAGE.consumeClick()) { }
        while (INVENTORY.consumeClick()) { }
        while (BLOCK_USE.consumeClick()) { }
        while (EQUIP_BAG.consumeClick()) { }
        while (EQUIP_BELT.consumeClick()) { }
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
            pendingWorldObservations.clear();
            worldObservationRetryAt.clear();
            closeWorldWindows();
            WORLD.clear();
            worldCrossPendingTarget = null;
            POLICY.suspend();
            if (mc.screen instanceof StoragePopupScreen || mc.screen instanceof BodyInventoryScreen) mc.setScreen(null);
            POPUPS.clear();
            POINTER.authorityClear();
            level = mc.level;
            player = mc.player;
            cameraBody = null;
            cameraEpoch = 0;
        }
        Mob body = currentBody();
        long epoch = GhostControlClient.committedCharacterEpoch();
        if (cameraBody != body || cameraEpoch != epoch || body == null || epoch == 0) {
            pendingWorldObservations.clear();
            worldObservationRetryAt.clear();
            closeWorldWindows();
            WORLD.clear();
            worldCrossPendingTarget = null;
            POLICY.suspend();
            if (mc.screen instanceof StoragePopupScreen || mc.screen instanceof BodyInventoryScreen) mc.setScreen(null);
            POPUPS.clear();
            POINTER.authorityClear();
            cameraBody = body;
            cameraEpoch = epoch;
            if (body == null || epoch == 0) return;
        }
        POLICY.bind(mc.player.getUUID(), body.getUUID(), epoch);
        WORLD.bind(mc.player.getUUID(), body.getUUID(), epoch);
        POPUPS.bind(body.getUUID(), epoch);
        if (mc.screen != null && !(mc.screen instanceof StoragePopupScreen)) POLICY.suspend();
    }

    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        clock++;
        reconcile();
        if (cameraBody == null) {
            while (DROP.consumeClick()) { }
            while (SELECT_HAND.consumeClick()) { }
            while (STORAGE.consumeClick()) { }
            while (WORLD_STORAGE.consumeClick()) { }
            while (INVENTORY.consumeClick()) { }
            while (BLOCK_USE.consumeClick()) { }
            while (EQUIP_BAG.consumeClick()) { }
            while (EQUIP_BELT.consumeClick()) { }
            return;
        }
        boolean inventory = INVENTORY.consumeClick();
        boolean equipBag = EQUIP_BAG.consumeClick();
        boolean equipBelt = EQUIP_BELT.consumeClick();
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (inventory && !shiftDown() && !controlDown() && !altComboDown()) {
            if (POPUPS.pointerMode()) dismissPointerMode();
            else { POINTER.manualOpen(); pointerMode(true); }
        }
        // Ctrl+V retains keyboard hand switching; V / Shift+V toggle bag / belt.
        POLICY.timeout(clock);
        WORLD.advance(clock);
        if (worldCrossPendingTarget != null && (WORLD.view(worldCrossPendingTarget) == null
                || WORLD.canAct(worldCrossPendingTarget))) worldCrossPendingTarget = null;
        if (worldResultHandRefreshPending) sendWorldResultHandRefresh();
        var proof = POLICY.inventoryView() == null ? null : POLICY.snapshot();
        WORLD.settledHand(proof);
        if (proof != null) for (UUID target : java.util.List.copyOf(pendingWorldObservations)) {
            if (clock >= worldObservationRetryAt.getOrDefault(target, 0L) && !WORLD.hasPendingOpen(target)
                    && WORLD.view(target) == null) WORLD.query(target, proof.revision(), nonWorldWindowCount(), clock)
                    .ifPresent(query -> { PacketDistributor.sendToServer(query); worldObservationRetryAt.put(target, clock + 40); });
        }
        if (proof != null) for (var window : POPUPS.windows()) if (window.visible() && window.provisional()
                && window.key().kind() == StoragePopupWorkspace.HostKind.WORLD) {
            UUID target = window.key().worldId();
            if (clock >= worldObservationRetryAt.getOrDefault(target, 0L) && !WORLD.hasPendingOpen(target))
                WORLD.query(target, proof.revision(), nonWorldWindowCount(), clock).ifPresent(query -> {
                    PacketDistributor.sendToServer(query); worldObservationRetryAt.put(target, clock + 40);
                });
        }
        for (var window : POPUPS.windows()) if (window.key().kind() == StoragePopupWorkspace.HostKind.WORLD
                && WORLD.view(window.key().worldId()) == null && !window.provisional()
                && !pendingWorldObservations.contains(window.key().worldId())) {
            POPUPS.close(window.key()); closePointerIfEmpty();
        }
        if (proof != null) for (var world : WORLD.views()) {
            if (clock - world.refreshedAt() >= 40) {
                var query = WORLD.query(world.target(), proof.revision(), nonWorldWindowCount(), clock);
                query.ifPresent(PacketDistributor::sendToServer);
            }
        }
        long sequence = POLICY.query(clock);
        if (sequence != 0) {
            WORLD.handQueryIssued();
            PacketDistributor.sendToServer(new BodyHandStateQuery(POLICY.queryEpoch(), sequence));
        }
        boolean drop = DROP.consumeClick();
        boolean select = SELECT_HAND.consumeClick();
        boolean storage = STORAGE.consumeClick();
        boolean worldStorage = WORLD_STORAGE.consumeClick();
        if (contextualWorldHandled) { worldStorage = false; contextualWorldHandled = false; }
        boolean blockUse = BLOCK_USE.consumeClick();
        if (mc.screen != null) {
            POLICY.cancelThrowIntent();
            return;
        }
        if (worldStorage && !shiftDown()) {
            if (!openAimedWorldActivation()) openAimedWorld();
        }
        if (equipBag) smartStorage(false);
        if (equipBelt) smartStorage(true);
        boolean ctrlV = storage && controlDown() && !shiftDown() && !altDown();
        if (storage && !controlDown() && !altDown())
            togglePopup(shiftDown() ? StoragePopupWorkspace.HostKind.BELT : StoragePopupWorkspace.HostKind.BAG);
        var dropAction = BodyHandInputPolicy.dropAction(drop, controlDown());
        if (dropAction != null) request(dropAction);
        else if (ctrlV || select && !storage) selectHand();
        if (blockUse) requestHeldActivation();
    }

    private static void requestBlockUse(BodyBlockUseRequest.Intent intent) {
        var snapshot = POLICY.snapshot();
        if (snapshot == null || cameraBody == null) return;
        var hand = snapshot.hands().stream().filter(slot -> slot.id().equals(snapshot.activeHand())).findFirst().orElse(null);
        if (hand == null) return;
        var start = cameraBody.getEyePosition();
        var end = start.add(cameraBody.getLookAngle().scale(6.0));
        var hit = cameraBody.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, cameraBody));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return;
        long sequence = POLICY.blockUseSequence(clock);
        if (sequence == 0) return;
        PacketDistributor.sendToServer(new BodyBlockUseRequest(snapshot.epoch(), sequence, snapshot.revision(),
                hand.id(), hand.token(), hit.getBlockPos(), hit.getDirection(),
                net.minecraft.client.Minecraft.getInstance().player.input.shiftKeyDown, intent));
    }

    private static boolean openAimedWorldActivation() {
        reconcile();
        if (cameraBody == null) return false;
        var start = cameraBody.getEyePosition();
        var end = start.add(cameraBody.getLookAngle().scale(6.0));
        var hit = cameraBody.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, cameraBody));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return false;
        requestBlockUse(BodyBlockUseRequest.Intent.ACTIVATE_WORLD);
        return true;
    }

    private static void requestHeldActivation() {
        var snapshot = POLICY.snapshot();
        if (snapshot == null) return;
        var hand = snapshot.hands().stream().filter(slot -> slot.id().equals(snapshot.activeHand())).findFirst().orElse(null);
        if (hand == null || hand.token() == null) return;
        long sequence = POLICY.action(clock);
        if (sequence == 0) return;
        PacketDistributor.sendToServer(new BodyHandActionRequest(BodyHandActionRequest.Action.ACTIVATE_HELD,
                hand.id(), snapshot.epoch(), sequence, snapshot.revision(), null, hand.token()));
    }

    private static boolean shiftDown() {
        long window = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private static boolean controlDown() {
        long window = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private static boolean altDown() {
        long window = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    private static boolean altComboDown() {
        long window = net.minecraft.client.Minecraft.getInstance().getWindow().getWindow();
        var options = net.minecraft.client.Minecraft.getInstance().options;
        for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_LAST; key++)
            if (key != GLFW.GLFW_KEY_LEFT_ALT && key != GLFW.GLFW_KEY_RIGHT_ALT
                    && !movementKey(key, options)
                    && GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS) return true;
        return false;
    }

    private static boolean movementKey(int key, net.minecraft.client.Options options) {
        return java.util.stream.Stream.of(options.keyUp, options.keyDown, options.keyLeft, options.keyRight,
                        options.keyJump, options.keyShift, options.keySprint).anyMatch(mapping ->
                mapping.getKey().getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM
                        && mapping.getKey().getValue() == key);
    }

    private static boolean aimedWorldEligible() {
        if (currentBody() == null || POLICY.inventoryView() == null) return false;
        UUID target = lookTarget(cameraBody);
        return target != null && cameraBody.level().getEntitiesOfClass(ItemEntity.class,
                cameraBody.getBoundingBox().inflate(6), item -> item.getUUID().equals(target)
                        && (item.getItem().is(com.juicyslew.moonstation14.item.ModItems.BAG)
                        || item.getItem().is(com.juicyslew.moonstation14.item.ModItems.BELT))).size() == 1;
    }

    private static void contextualKey(net.neoforged.neoforge.client.event.InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_RELEASE) {
            if (WORLD_STORAGE.matches(event.getKey(), event.getScanCode()))
                contextualWorldKey = false;
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (net.minecraft.client.Minecraft.getInstance().screen == null && !shiftDown()
                && matches(WORLD_STORAGE, event.getKey(), event.getScanCode(), event.getModifiers())) {
            reconcile();
            contextualWorldKey = aimedWorldEligible();
            if (contextualWorldKey) { contextualWorldHandled = true; openAimedWorld(); }
        } else contextualWorldKey = false;
    }

    private static void contextualInventoryOpening(net.neoforged.neoforge.client.event.ScreenEvent.Opening event) {
        if (contextualWorldKey && event.getCurrentScreen() == null
                && event.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) {
            contextualWorldKey = false;
            event.setCanceled(true);
        }
    }

    public static void mouseButton(net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre event) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        Mob ownedBody = currentBody();
        long ownedEpoch = GhostControlClient.committedCharacterEpoch();
        if (!BodyHandInputPolicy.ownsPrimary(event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && event.getAction() == GLFW.GLFW_PRESS, mc.screen == null, mc.mouseHandler.isMouseGrabbed(),
                ownedBody != null, ownedEpoch)) return;
        reconcile();
        var view = POLICY.inventoryView();
        if (view == null) return;
        var active = view.first().id().equals(view.activeHand()) ? view.first() : view.second();
        if (cameraBody == null) return;
        var start = cameraBody.getEyePosition();
        var end = start.add(cameraBody.getLookAngle().scale(6));
        var block = cameraBody.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, cameraBody));
        var route = PrimaryWorldGesture.route(true,
                block.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK, active.token() != null,
                lookTarget(cameraBody) != null);
        if (route == PrimaryWorldGesture.Route.BLOCK_USE) {
            requestBlockUse(BodyBlockUseRequest.Intent.USE);
            event.setCanceled(true);
        } else {
            var action = PrimaryWorldGesture.action(route);
            if (action != null) request(action);
            if (BodyHandInputPolicy.cancelsVanillaAttack(route)) event.setCanceled(true);
        }
    }

    private static boolean smartStorage(boolean belt) {
        reconcile();
        var view = POLICY.inventoryView();
        var snapshot = POLICY.snapshot();
        var plan = SmartStorageGesture.plan(belt, view, snapshot);
        if (plan == null) return false;
        long sequence = POLICY.action(clock);
        if (sequence == 0) return false;
        PacketDistributor.sendToServer(new BodyHandActionRequest(plan.action(), plan.handId(),
                snapshot.epoch(), sequence, snapshot.revision(), null, plan.hostOrHandToken(),
                plan.childOrSourceToken(), -1, -1, -1));
        return true;
    }

    static void selectHand() {
        BodyHandStateSnapshot snapshot = POLICY.snapshot();
        if (snapshot == null || snapshot.hands().size() != 2) return;
        var other = snapshot.hands().stream().filter(hand -> !hand.id().equals(snapshot.activeHand())).toList();
        if (other.size() != 1) return;
        long sequence = POLICY.selectionAction(clock);
        if (sequence == 0) return;
        PacketDistributor.sendToServer(new BodyHandActionRequest(BodyHandActionRequest.Action.SELECT_HAND,
                other.getFirst().id(), snapshot.epoch(), sequence, snapshot.revision(), null, null));
    }

    static BodyHandClientPolicy.InventoryView inventoryView() {
        reconcile();
        return cameraBody == null ? null : POLICY.inventoryView();
    }

    /** Whether the synchronized active hand for this exact controlled body/epoch is steel. */
    public static boolean activeSteelFor(Mob body, long epoch) {
        reconcile();
        if (body == null || body != cameraBody || epoch <= 0 || epoch != GhostControlClient.committedCharacterEpoch()) return false;
        var snapshot = POLICY.snapshot();
        if (snapshot == null || snapshot.epoch() != epoch || !snapshot.bodyId().equals(body.getUUID())) return false;
        var hand = snapshot.hands().stream().filter(slot -> slot.id().equals(snapshot.activeHand())).findFirst().orElse(null);
        return hand != null && com.juicyslew.moonstation14.item.ModItems.STEEL.getId().toString().equals(hand.itemId()) && hand.count() > 0;
    }

    /** Confirmed active-hand observation for this exact controlled body session; never reads carrier inventory. */
    public static java.util.Optional<net.minecraft.world.item.ItemStack> activeHeldStackFor(UUID bodyId, long epoch) {
        reconcile();
        if (bodyId == null || bodyId.equals(cameraBody == null ? null : cameraBody.getUUID()) == false
                || epoch <= 0 || epoch != cameraEpoch || epoch != GhostControlClient.committedCharacterEpoch())
            return java.util.Optional.empty();
        var snapshot = POLICY.snapshot();
        if (snapshot == null || snapshot.epoch() != epoch || !bodyId.equals(snapshot.bodyId())) return java.util.Optional.empty();
        var hand = snapshot.hands().stream().filter(slot -> slot.id().equals(snapshot.activeHand())).findFirst().orElse(null);
        if (hand == null || hand.itemId() == null || hand.count() <= 0) return java.util.Optional.empty();
        var id = net.minecraft.resources.ResourceLocation.tryParse(hand.itemId());
        if (id == null || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) return java.util.Optional.empty();
        return java.util.Optional.of(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id), hand.count()));
    }

    static BodyHandClientPolicy.InventoryView inventoryPresentation() {
        reconcile();
        return cameraBody == null ? null : POLICY.presentationView();
    }

    static boolean inventoryPresentationPending() {
        reconcile();
        return POLICY.presentationPending();
    }

    static BodyHandStateSnapshot inventoryProof() {
        return POLICY.inventoryView() == null ? null : POLICY.snapshot();
    }

    static boolean inventoryKey(int keyCode, int scanCode) {
        return INVENTORY.matches(keyCode, scanCode);
    }

    static int modifierMask(net.minecraft.client.KeyMapping mapping) {
        return switch (mapping.getKeyModifier()) {
            case SHIFT -> GLFW.GLFW_MOD_SHIFT;
            case CONTROL -> GLFW.GLFW_MOD_CONTROL;
            case ALT -> GLFW.GLFW_MOD_ALT;
            default -> 0;
        };
    }

    static boolean matches(net.minecraft.client.KeyMapping mapping, int key, int scan, int modifiers) {
        int effective = modifiers;
        if (key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT) effective &= ~GLFW.GLFW_MOD_ALT;
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT) effective &= ~GLFW.GLFW_MOD_SHIFT;
        return mapping.matches(key, scan) && PopupMovementIntent.modifiersMatch(modifierMask(mapping), effective);
    }

    /** Screen keyboard routing calls the same snapshot/sequence guarded actions as the world bindings. */
    static boolean popupAction(int key, int scan, int modifiers, boolean firstPress) {
        if (key == GLFW.GLFW_KEY_ESCAPE) return false;
        boolean pointer = matches(INVENTORY, key, scan, modifiers);
        boolean drop = matches(DROP, key, scan, modifiers);
        boolean select = matches(SELECT_HAND, key, scan, modifiers);
        boolean storage = STORAGE.matches(key, scan);
        boolean bag = matches(EQUIP_BAG, key, scan, modifiers);
        boolean belt = matches(EQUIP_BELT, key, scan, modifiers);
        if (bag || belt) {
            if (firstPress) smartStorage(belt);
            return true;
        }
        if (!pointer && !drop && !select && !storage) return false;
        var action = PopupActionPlan.plan(firstPress, pointer, drop, select, storage,
                (modifiers & GLFW.GLFW_MOD_SHIFT) != 0, (modifiers & GLFW.GLFW_MOD_CONTROL) != 0,
                (modifiers & (GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) != 0, altComboDown());
        switch (action) {
            case POINTER -> dismissPointerMode();
            case DROP -> { reconcile(); if (POLICY.inventoryView() != null) request(BodyHandActionRequest.Action.DROP); }
            case SELECT -> { reconcile(); if (POLICY.inventoryView() != null) selectHand(); }
            case BAG -> togglePopup(StoragePopupWorkspace.HostKind.BAG);
            case BELT -> togglePopup(StoragePopupWorkspace.HostKind.BELT);
            default -> { }
        }
        return true;
    }

    static boolean hasCommittedBody() {
        return currentBody() != null && GhostControlClient.committedCharacterEpoch() > 0;
    }

    static void pointerMode(boolean enabled) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (enabled && mc.screen == null && cameraBody != null) {
            POPUPS.pointerMode(true);
            mc.setScreen(new StoragePopupScreen(POPUPS));
        } else if (!enabled && mc.screen instanceof StoragePopupScreen) {
            POPUPS.pointerMode(false);
            mc.setScreen(null);
        }
    }

    private static void dismissPointerMode() {
        POINTER.dismiss();
        pointerMode(false);
    }

    static void dismissPopupPointerMode() { dismissPointerMode(); }

    static void togglePopup(StoragePopupWorkspace.HostKind kind) {
        reconcile();
        if (kind == StoragePopupWorkspace.HostKind.WORLD || kind == StoragePopupWorkspace.HostKind.HELD) return;
        var view = POLICY.presentationView();
        var grid = view == null ? null : kind == StoragePopupWorkspace.HostKind.BAG ? view.backGrid() : view.beltGrid();
        if (grid == null) return;
        var key = StoragePopupWorkspace.HostKey.worn(kind, grid.host().hostToken());
        if ((POPUPS.window(key) == null || !POPUPS.window(key).visible())
                && visibleWindowCount() + WORLD.pendingOpens() >= StoragePopupWorkspace.MAX_WINDOWS) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        int width = StoragePopupLayout.outerWidth(grid.host().width()), height = StoragePopupLayout.outerHeight(grid.host().height());
        var position = PopupDefaultLayout.calculate(kind, width, height, mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight(), defaultPopupBounds());
        POPUPS.toggle(key, POLICY.inventoryView() != null,
                position.x(), position.y(), width, height);
        if (POPUPS.window(key) != null && POPUPS.window(key).visible() && !POPUPS.pointerMode()) {
            POINTER.popupOpen(); pointerMode(true);
        }
        closePointerIfEmpty();
    }

    static void toggleHeldPopup(String token) {
        reconcile();
        var grid = POLICY.heldGrid(token, true);
        if (grid == null) return;
        var key = StoragePopupWorkspace.HostKey.held(token);
        if ((POPUPS.window(key) == null || !POPUPS.window(key).visible())
                && visibleWindowCount() + WORLD.pendingOpens() >= StoragePopupWorkspace.MAX_WINDOWS) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        int width = StoragePopupLayout.outerWidth(grid.host().width()), height = StoragePopupLayout.outerHeight(grid.host().height());
        var position = PopupDefaultLayout.calculate(StoragePopupWorkspace.HostKind.HELD, width, height,
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), defaultPopupBounds());
        POPUPS.toggle(key, POLICY.heldGrid(token, false) != null, position.x(), position.y(), width, height);
        if (POPUPS.window(key) != null && POPUPS.window(key).visible() && !POPUPS.pointerMode()) {
            POINTER.popupOpen(); pointerMode(true);
        }
        closePointerIfEmpty();
    }




    static void closePopup(StoragePopupWorkspace.HostKey key) {
        if (key.kind() == StoragePopupWorkspace.HostKind.WORLD) closeWorld(key.worldId());
        else { POPUPS.close(key); closePointerIfEmpty(); }
    }

    private static void closePointerIfEmpty() {
        if (POPUPS.windows().stream().noneMatch(StoragePopupWorkspace.Window::visible)) {
            if (POINTER.closeWhenEmpty()) pointerMode(false);
        }
    }

    static BodyHandClientPolicy.GridView popupGrid(BodyHandClientPolicy.InventoryView view,
                                                    StoragePopupWorkspace.HostKey key, boolean presentation) {
        if (key == null) return null;
        return switch (key.kind()) {
            case BAG -> view == null ? null : view.backGrid();
            case BELT -> view == null ? null : view.beltGrid();
            case HELD -> POLICY.heldGrid(key.token(), presentation);
            case WORLD -> null;
        };
    }

    static WorldGridClientPolicy.GridView worldGrid(UUID id) { return WORLD.view(id); }

    static boolean worldCrossPending() { return worldCrossPendingTarget != null; }

    static boolean worldActionable(UUID id) {
        var proof = inventoryProof();
        var world = WORLD.view(id);
        return world != null && WORLD.canAct(id) && proof != null && proof.revision() == world.handRevision()
                && proof.accountId().equals(net.minecraft.client.Minecraft.getInstance().player.getUUID())
                && proof.bodyId().equals(cameraBody.getUUID()) && proof.epoch() == cameraEpoch;
    }

    /** Conservative client projection of the world STORE checks; never treat unknown hand data as eligible. */
    static boolean worldStoreEligible(UUID target, String token) {
        var proof = inventoryProof();
        var view = POLICY.inventoryView();
        if (proof == null || view == null || token == null) return false;
        var hand = handForToken(view, token);
        if (hand == null) return false;
        var itemId = net.minecraft.resources.ResourceLocation.tryParse(hand.itemId() == null ? "" : hand.itemId());
        var item = itemId == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        return token.equals(hand.token()) && hand.itemId() != null && hand.count() > 0 && hand.count() <= 64
                && item != null && item != net.minecraft.world.item.Items.AIR
                && !hand.pouch() && !"moonstation14:bag".equals(hand.itemId())
                && !"moonstation14:belt".equals(hand.itemId())
                && worldActionable(target) && proof.revision() == WORLD.view(target).handRevision();
    }

    static void worldAction(StoragePopupWorkspace.HostKey key, BodyWorldGridAction.Mode mode, String token,
                            StoragePopupWorkspace.Drag drag) {
        worldAction(key, mode, token, drag, null);
    }

    static void worldAction(StoragePopupWorkspace.HostKey key, BodyWorldGridAction.Mode mode, String token,
                            StoragePopupWorkspace.Drag drag, String nominatedHandId) {
        reconcile();
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof StoragePopupScreen)
                || key == null || key.kind() != StoragePopupWorkspace.HostKind.WORLD || drag == null
                || !worldActionable(key.worldId())) return;
        var world = WORLD.view(key.worldId());
        var handView = POLICY.inventoryView();
        var proof = POLICY.snapshot();
        var hand = handView.first().id().equals(handView.activeHand()) ? handView.first() : handView.second();
        if (nominatedHandId != null) hand = handView.first().id().equals(nominatedHandId) ? handView.first()
                : handView.second().id().equals(nominatedHandId) ? handView.second() : null;
        if (hand == null) return;
        if (mode == BodyWorldGridAction.Mode.STORE) {
            var nominated = handView.first().token() != null && handView.first().token().equals(token) ? handView.first()
                    : handView.second().token() != null && handView.second().token().equals(token) ? handView.second() : null;
            if (nominated == null) return;
            hand = nominated;
        }
        var entry = mode == BodyWorldGridAction.Mode.STORE ? null : world.entries().stream()
                .filter(e -> e.token().equals(token)).findFirst().orElse(null);
        if (mode == BodyWorldGridAction.Mode.STORE && (hand.token() == null || !hand.token().equals(token)
                || hand.count() > 64 || hand.pouch() || "moonstation14:bag".equals(hand.itemId())
                || "moonstation14:belt".equals(hand.itemId())) || mode != BodyWorldGridAction.Mode.STORE
                && (entry == null || entry.x() != drag.oldAnchor().x() || entry.y() != drag.oldAnchor().y()
                    || entry.rotation() != drag.oldRotation().ordinal() * 90)
                || mode == BodyWorldGridAction.Mode.TAKE && hand.token() != null) return;
        if (mode != BodyWorldGridAction.Mode.TAKE) {
            var id = net.minecraft.resources.ResourceLocation.tryParse(entry == null ? hand.itemId() : entry.itemId());
            if (id == null || !StoragePopupWorkspace.preview(world.geometry(), entry == null ? null : token,
                    com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints.forId(id),
                    drag.proposedAnchor(), drag.proposedRotation(), true).maySendAction()) return;
        }
        WORLD.settledHand(proof);
        WORLD.action(key.worldId(), proof, mode, mode == BodyWorldGridAction.Mode.MOVE ? null : hand.id(), token,
                entry == null ? -1 : entry.x(), entry == null ? -1 : entry.y(), entry == null ? -1 : entry.rotation(),
                mode == BodyWorldGridAction.Mode.TAKE ? -1 : drag.proposedAnchor().x(),
                mode == BodyWorldGridAction.Mode.TAKE ? -1 : drag.proposedAnchor().y(),
                mode == BodyWorldGridAction.Mode.TAKE ? -1 : drag.proposedRotation().ordinal() * 90, clock)
                .ifPresent(PacketDistributor::sendToServer);
    }

    private static void onWorldResult(BodyWorldGridActionResult result) {
        reconcile();
        if (!WORLD.result(result)) return;
        if (WORLD.view(result.target()) == null) {
            POPUPS.close(StoragePopupWorkspace.HostKey.world(result.target()));
            return;
        }
        worldResultHandRefreshPending = true;
        sendWorldResultHandRefresh();
    }

    private static void onWorldCrossResult(BodyWorldCrossActionResult result) {
        reconcile();
        if (!WORLD.crossResult(result)) return;
        worldResultHandRefreshPending = true;
        sendWorldResultHandRefresh();
        if (WORLD.view(result.target()) == null) {
            worldCrossPendingTarget = null;
            POPUPS.close(StoragePopupWorkspace.HostKey.world(result.target()));
            closePointerIfEmpty();
            return;
        }
    }

    private static void onWorldWorldCrossResult(BodyWorldWorldCrossActionResult result) {
        reconcile();
        var sourceBefore = WORLD.view(result.sourceTarget());
        var destinationBefore = WORLD.view(result.destinationTarget());
        if (!WORLD.worldCrossResult(result)) return;
        worldResultHandRefreshPending = true;
        sendWorldResultHandRefresh();
        if (result.reason() != BodyWorldWorldCrossActionResult.Reason.OK) {
            closeInvalidWorldAfterCross(result.sourceTarget(), sourceBefore);
            closeInvalidWorldAfterCross(result.destinationTarget(), destinationBefore);
            closePointerIfEmpty();
            return;
        }
    }

    private static void closeInvalidWorldAfterCross(UUID target, WorldGridClientPolicy.GridView known) {
        if (WORLD.view(target) != null || known == null) return;
        if (connection == net.minecraft.client.Minecraft.getInstance().getConnection())
            PacketDistributor.sendToServer(new BodyWorldGridClose(cameraEpoch, target, known.session()));
        POPUPS.close(StoragePopupWorkspace.HostKey.world(target));
    }

    static boolean popupCrossWorld(PopupActionPlan.WorldCrossSource source, StoragePopupWorkspace.HostKey destination,
                                   com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Cell anchor,
                                   com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Rotation rotation) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof StoragePopupScreen)) return false;
        reconcile();
        var proof = inventoryProof();
        var world = source == null ? null : WORLD.view(source.worldTarget());
        var bodyKey = source == null ? null : source.host().kind() == StoragePopupWorkspace.HostKind.WORLD
                ? destination : source.host();
        var sourceWindow = source == null ? null : POPUPS.window(source.host());
        var destinationWindow = destination == null ? null : POPUPS.window(destination);
        if (proof == null || world == null || sourceWindow == null || destinationWindow == null
                || !sourceWindow.visible() || !destinationWindow.visible() || sourceWindow.provisional()
                || destinationWindow.provisional() || bodyKey == null
                || popupGrid(POLICY.inventoryView(), bodyKey, false) == null) return false;
        var plan = PopupActionPlan.worldCrossPlan(source, destination, proof, world,
                worldActionable(world.target()), anchor, rotation, 0);
        if (plan == null) return false;
        return WORLD.crossAction(plan.target(), proof, plan.direction(), plan.worn(), plan.hostId(),
                plan.hostToken(), plan.child(), plan.oldAnchor().x(), plan.oldAnchor().y(),
                plan.oldRotation().degrees(), plan.newAnchor().x(), plan.newAnchor().y(),
                plan.newRotation().degrees(), clock).map(action -> {
                    worldCrossPendingTarget = plan.target();
                    PacketDistributor.sendToServer(action);
                    return true;
                }).orElse(false);
    }

    static boolean popupCrossWorldWorld(PopupActionPlan.WorldWorldPlan plan) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof StoragePopupScreen) || plan == null) return false;
        reconcile();
        var proof = inventoryProof();
        var sourceWindow = POPUPS.window(StoragePopupWorkspace.HostKey.world(plan.source()));
        var destinationWindow = POPUPS.window(StoragePopupWorkspace.HostKey.world(plan.destination()));
        var sourceWorld = WORLD.view(plan.source());
        var destinationWorld = WORLD.view(plan.destination());
        if (sourceWorld == null || destinationWorld == null || proof == null) return false;
        var entry = sourceWorld.entries().stream().filter(e -> e.token().equals(plan.child())).findFirst().orElse(null);
        var actionPlan = PopupActionPlan.worldWorldPlan(
                PopupActionPlan.worldCrossSource(proof, sourceWorld, entry),
                sourceWorld, destinationWorld, proof, plan.anchor(), plan.rotation(),
                worldActionable(plan.source()) && worldActionable(plan.destination())
                        && sourceWindow != null && destinationWindow != null && sourceWindow.visible() && destinationWindow.visible()
                        && !sourceWindow.provisional() && !destinationWindow.provisional(), 0);
        if (actionPlan == null) return false;
        return WORLD.worldCrossAction(actionPlan.source(), actionPlan.destination(), proof, actionPlan.child(),
                actionPlan.oldAnchor().x(), actionPlan.oldAnchor().y(), actionPlan.oldRotation().degrees(),
                actionPlan.anchor().x(), actionPlan.anchor().y(), actionPlan.rotation().degrees(), clock)
                .map(action -> { PacketDistributor.sendToServer(action); return true; }).orElse(false);
    }

    private static void sendWorldResultHandRefresh() {
        long handQuery = POLICY.forceFreshQuery(clock);
        if (handQuery == 0) return;
        worldResultHandRefreshPending = false;
        WORLD.handQueryIssued();
        PacketDistributor.sendToServer(new BodyHandStateQuery(POLICY.queryEpoch(), handQuery));
    }

    private static int nonWorldWindowCount() {
        return (int) POPUPS.windows().stream().filter(w -> w.visible()
                && w.key().kind() != StoragePopupWorkspace.HostKind.WORLD).count();
    }

    private static int visibleWindowCount() {
        return (int) POPUPS.windows().stream().filter(StoragePopupWorkspace.Window::visible).count();
    }

    private static java.util.List<HandBarLayout.Rect> defaultPopupBounds() {
        return POPUPS.windows().stream().filter(w -> w.visible()).map(w ->
                new HandBarLayout.Rect(w.x(), w.y(), w.width(), w.height())).toList();
    }

    private static void openAimedWorld() {
        var proof = POLICY.inventoryView() == null ? null : POLICY.snapshot();
        if (proof == null || cameraBody == null) return;
        UUID target = lookTarget(cameraBody);
        if (target == null) return;
        if (!aimedWorldEligible()) return;
        var key = StoragePopupWorkspace.HostKey.world(target);
        if (POPUPS.window(key) != null) { closeWorld(target); return; }
        if (WORLD.hasPendingOpen(target)) { closeWorld(target); return; }
        WORLD.query(target, proof.revision(), nonWorldWindowCount(), clock).ifPresent(PacketDistributor::sendToServer);
    }

    static void closeWorld(UUID target) {
        if (target.equals(worldCrossPendingTarget)) worldCrossPendingTarget = null;
        var view = WORLD.view(target);
        if (view != null && connection == net.minecraft.client.Minecraft.getInstance().getConnection())
            PacketDistributor.sendToServer(new BodyWorldGridClose(cameraEpoch, target, view.session()));
        WORLD.close(target);
        POPUPS.close(StoragePopupWorkspace.HostKey.world(target));
        closePointerIfEmpty();
    }

    private static void closeWorldWindows() {
        WORLD.cancelPendingOpens();
        for (var view : WORLD.views()) closeWorld(view.target());
        for (var window : POPUPS.windows()) if (window.key().kind() == StoragePopupWorkspace.HostKind.WORLD)
            closeWorld(window.key().worldId());
    }

    private static void onWorldSnapshot(BodyWorldGridSnapshot snapshot) {
        reconcile();
        WORLD.lateClose(snapshot).ifPresent(PacketDistributor::sendToServer);
        if (!WORLD.accept(snapshot, clock)) {
            WORLD.rejectedGrantClose().ifPresent(PacketDistributor::sendToServer);
            refreshStaleWorldsFromFreshHand();
            return;
        }
        boolean rehosting = pendingWorldObservations.contains(snapshot.entityId());
        if (snapshot.reason() != BodyWorldGridSnapshot.Reason.OK) {
            if (WorldGridClientPolicy.materiallyUnavailable(snapshot.reason())) {
                pendingWorldObservations.remove(snapshot.entityId());
                worldObservationRetryAt.remove(snapshot.entityId());
                POPUPS.close(StoragePopupWorkspace.HostKey.world(snapshot.entityId()));
            }
            refreshStaleWorldsFromFreshHand();
            return;
        }
        for (var window : POPUPS.windows()) if (window.key().kind() == StoragePopupWorkspace.HostKind.WORLD
                && WORLD.view(window.key().worldId()) == null && !pendingWorldObservations.contains(window.key().worldId())) {
            POPUPS.close(window.key());
            closePointerIfEmpty();
        }
        var view = WORLD.view(snapshot.entityId());
        if (view == null) return;
        var mc = net.minecraft.client.Minecraft.getInstance();
        var key = StoragePopupWorkspace.HostKey.world(view.target());
        boolean newWindow = POPUPS.window(key) == null;
        if (newWindow) {
            int width = StoragePopupLayout.outerWidth(view.width()), height = StoragePopupLayout.outerHeight(view.height());
            int screenW = mc.getWindow().getGuiScaledWidth(), screenH = mc.getWindow().getGuiScaledHeight();
            var position = PopupDefaultLayout.calculate(StoragePopupWorkspace.HostKind.WORLD, width, height,
                    screenW, screenH, defaultPopupBounds());
            if (POPUPS.open(key, true, position.x(), position.y(), width,
                    StoragePopupLayout.outerHeight(view.height())) == StoragePopupWorkspace.OpenResult.DENIED) {
                closeWorld(view.target()); return;
            }
        }
        POPUPS.resizeWindow(key, StoragePopupLayout.outerWidth(view.width()), StoragePopupLayout.outerHeight(view.height()));
        POPUPS.snapshot(key, true, true, view.entries().stream().map(BodyWorldGridSnapshot.Entry::token).toList());
        if (rehosting) { pendingWorldObservations.remove(snapshot.entityId()); worldObservationRetryAt.remove(snapshot.entityId()); }
        if (newWindow && mc.screen == null && !rehosting && !POPUPS.pointerMode()) { POINTER.popupOpen(); pointerMode(true); }
        refreshStaleWorldsFromFreshHand();
    }

    private static void refreshStaleWorldsFromFreshHand() {
        var proof = POLICY.inventoryView() == null ? null : POLICY.snapshot();
        if (proof == null) return;
        for (var world : WORLD.views()) if (world.stale())
            WORLD.query(world.target(), proof.revision(), nonWorldWindowCount(), clock).ifPresent(PacketDistributor::sendToServer);
    }

    private static void finishWorldObservation(UUID target, boolean closePlaceholder) {
        if (!pendingWorldObservations.remove(target)) return;
        worldObservationRetryAt.remove(target);
        var key = StoragePopupWorkspace.HostKey.world(target);
        if (closePlaceholder) {
            var view = WORLD.view(target);
            if (view != null && connection == net.minecraft.client.Minecraft.getInstance().getConnection())
                PacketDistributor.sendToServer(new BodyWorldGridClose(cameraEpoch, target, view.session()));
            WORLD.close(target);
            POPUPS.close(key);
            closePointerIfEmpty();
        }
    }

    static BodyHandStateSnapshot.Hand popupSourceHand(BodyHandClientPolicy.InventoryView view,
                                                       StoragePopupWorkspace.HostKey key) {
        if (view == null) return null;
        if (key.kind() == StoragePopupWorkspace.HostKind.HELD) {
            if (key.token().equals(view.first().token())) return view.second();
            if (key.token().equals(view.second().token())) return view.first();
            return null;
        }
        return view.activeHand().equals(view.first().id()) ? view.first() : view.second();
    }

    static void popupGridTake(StoragePopupWorkspace.HostKey key, BodyHandStateSnapshot.GridEntry entry) {
        var view = POLICY.inventoryView();
        popupGridTake(key, entry, view == null ? null : view.activeHand());
    }

    static void popupGridTake(StoragePopupWorkspace.HostKey key, BodyHandStateSnapshot.GridEntry entry, String destinationHandId) {
        var view = POLICY.inventoryView();
        var grid = popupGrid(view, key, false);
        var destination = view == null || destinationHandId == null ? null
                : view.first().id().equals(destinationHandId) ? view.first()
                : view.second().id().equals(destinationHandId) ? view.second() : null;
        if (grid == null || !grid.host().hostToken().equals(key.token())
                || destination == null || destination.token() != null || !grid.host().entries().contains(entry)) return;
        sendGrid(key, entry.token(), entry.x(), entry.y(), -1, null, destinationHandId);
    }

    static void popupGridDrop(StoragePopupWorkspace.HostKey key, String token,
                              StoragePopupWorkspace.Drag drag, boolean fromHand) {
        var view = POLICY.inventoryView();
        var grid = popupGrid(view, key, false);
        if (grid == null || !grid.host().hostToken().equals(key.token()) || token == null) return;
        var hand = fromHand ? handForToken(view, token) : popupSourceHand(view, key);
        if (hand == null) return;
        var entry = fromHand ? null : grid.host().entries().stream().filter(e -> e.token().equals(token)).findFirst().orElse(null);
        String itemId = fromHand && token.equals(hand.token()) && grid.canStore() ? hand.itemId()
                : entry == null ? null : entry.itemId();
        if (itemId == null || drag == null) return;
        var id = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (id == null || drag.proposedAnchor().x() < 0 || drag.proposedAnchor().y() < 0) return;
        var preview = StoragePopupWorkspace.preview(grid.occupancy(), fromHand ? null : token,
                com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints.forId(id),
                drag.proposedAnchor(), drag.proposedRotation(), true);
        if (!preview.maySendAction()) return;
        if (entry != null && (entry.x() != drag.oldAnchor().x() || entry.y() != drag.oldAnchor().y()
                || entry.rotation() != drag.oldRotation().ordinal() * 90)) return;
        sendGrid(key, token, drag.proposedAnchor().x(), drag.proposedAnchor().y(),
                drag.proposedRotation().ordinal() * 90, entry, fromHand ? hand.id() : null);
    }

    static boolean popupCrossBody(PopupActionPlan.CrossSource source, StoragePopupWorkspace.HostKey target,
                                  com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Cell anchor,
                                  com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Rotation rotation) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof StoragePopupScreen)) return false;
        reconcile();
        var view = POLICY.inventoryView();
        var proof = POLICY.snapshot();
        if (view == null || proof == null || source == null || target == null || POPUPS.window(source.host()) == null
                || POPUPS.window(target) == null || !POPUPS.window(source.host()).visible()
                || !POPUPS.window(target).visible() || POPUPS.window(source.host()).provisional()
                || POPUPS.window(target).provisional()
                || popupGrid(view, source.host(), false) == null || popupGrid(view, target, false) == null) return false;
        // Validate before consuming the sole action sequence; never clear or alter either local host.
        if (PopupActionPlan.crossRequest(source, target, proof, anchor, rotation, 0, 1) == null) return false;
        long sequence = POLICY.action(clock);
        if (sequence == 0) return false;
        PacketDistributor.sendToServer(PopupActionPlan.crossRequest(source, target, proof, anchor, rotation, 0, sequence));
        return true;
    }

    static void popupHandGridDrop(StoragePopupWorkspace.HostKey key, HandEquipmentGesture.Drag source,
                                  com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Cell anchor,
                                  com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Rotation rotation) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof StoragePopupScreen)
                || key == null || source == null || anchor == null || rotation == null) return;
        reconcile();
        var view = POLICY.inventoryView();
        var proof = POLICY.snapshot();
        if (view == null || proof == null || !proof.accountId().equals(source.accountId()) || !proof.bodyId().equals(source.bodyId())
                || proof.epoch() != source.epoch() || proof.revision() != source.revision()) return;
        var hand = source.handId().equals(view.first().id()) ? view.first()
                : source.handId().equals(view.second().id()) ? view.second() : null;
        var grid = popupGrid(view, key, false);
        var other = popupSourceHand(view, key);
        if (!BodyHandClientPolicy.nominatedGridStoreEligible(hand, source.token(), source.itemId(), source.count())
                || key.kind() == StoragePopupWorkspace.HostKind.HELD && other != hand
                || grid == null || key.kind() == StoragePopupWorkspace.HostKind.HELD && !grid.canStore()
                || !grid.host().hostToken().equals(key.token())) return;
        var item = net.minecraft.resources.ResourceLocation.tryParse(source.itemId());
        if (item == null || !StoragePopupWorkspace.preview(grid.occupancy(), null,
                com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints.forId(item), anchor, rotation, true).maySendAction()) return;
        sendGrid(key, source.token(), anchor.x(), anchor.y(), rotation.ordinal() * 90, null, source.handId());
    }

    private static BodyHandStateSnapshot.Hand handForToken(BodyHandClientPolicy.InventoryView view, String token) {
        if (view == null || token == null) return null;
        if (token.equals(view.first().token())) return view.first();
        if (token.equals(view.second().token())) return view.second();
        return null;
    }

    private static void sendGrid(StoragePopupWorkspace.HostKey key, String token, int x, int y, int rotation,
                                 BodyHandStateSnapshot.GridEntry moved) {
        sendGrid(key, token, x, y, rotation, moved, null);
    }

    private static void sendGrid(StoragePopupWorkspace.HostKey key, String token, int x, int y, int rotation,
                                 BodyHandStateSnapshot.GridEntry moved, String nominatedHandId) {
        var view = POLICY.inventoryView();
        var snapshot = POLICY.snapshot();
        var grid = popupGrid(view, key, false);
        if (snapshot == null || view == null || grid == null || !key.token().equals(grid.host().hostToken())) return;
        String handId = nominatedHandId == null ? view.activeHand() : nominatedHandId;
        if (nominatedHandId != null) {
            if (rotation < 0) {
                var destination = nominatedHandId.equals(view.first().id()) ? view.first()
                        : nominatedHandId.equals(view.second().id()) ? view.second() : null;
                var child = grid.host().entries().stream().filter(e -> e.token().equals(token)
                        && e.x() == x && e.y() == y).findFirst().orElse(null);
                String heldHostHandId = key.kind() == StoragePopupWorkspace.HostKind.HELD
                        ? key.token().equals(view.first().token()) ? view.first().id()
                        : key.token().equals(view.second().token()) ? view.second().id() : "" : null;
                if (!BodyHandClientPolicy.nominatedGridTakeEligible(destination,
                        grid.host().entries().stream().filter(e -> e.token().equals(token)).findFirst().orElse(null),
                        child, heldHostHandId)) return;
            } else {
                var nominatedSource = handForToken(view, token);
                if (nominatedSource == null || !nominatedSource.id().equals(nominatedHandId)
                        || !BodyHandClientPolicy.nominatedGridStoreEligible(nominatedSource, token,
                        nominatedSource.itemId(), nominatedSource.count())) return;
            }
        }
        if (key.kind() == StoragePopupWorkspace.HostKind.HELD) {
            var host = key.token().equals(view.first().token()) ? view.first()
                    : key.token().equals(view.second().token()) ? view.second() : null;
            var other = popupSourceHand(view, key);
            if (host == null || other == null || host.count() != 1
                     || rotation < 0 && (other.token() != null || !grid.canTake()
                     || nominatedHandId != null && !other.id().equals(nominatedHandId))
                    || rotation >= 0 && moved == null && (!token.equals(other.token()) || !grid.canStore())
                    || moved != null && !grid.host().entries().contains(moved)) return;
            handId = host.id();
        }
        long sequence = POLICY.action(clock);
        if (sequence == 0) return;
        var action = key.kind() == StoragePopupWorkspace.HostKind.HELD
                ? rotation < 0 ? BodyHandActionRequest.Action.HELD_GRID_TAKE
                        : moved == null ? BodyHandActionRequest.Action.HELD_GRID_STORE : BodyHandActionRequest.Action.HELD_GRID_MOVE
                : key.kind() == StoragePopupWorkspace.HostKind.BELT
                ? rotation < 0 ? BodyHandActionRequest.Action.GRID_TAKE_BELT
                        : moved == null ? BodyHandActionRequest.Action.GRID_STORE_BELT : BodyHandActionRequest.Action.GRID_MOVE_BELT
                : rotation < 0 ? BodyHandActionRequest.Action.GRID_TAKE_BACK
                        : moved == null ? BodyHandActionRequest.Action.GRID_STORE_BACK : BodyHandActionRequest.Action.GRID_MOVE_BACK;
        PacketDistributor.sendToServer(new BodyHandActionRequest(action, handId, snapshot.epoch(), sequence,
                snapshot.revision(), null, key.token(), token, x, y, rotation,
                moved == null ? -1 : moved.x(), moved == null ? -1 : moved.y(), moved == null ? -1 : moved.rotation()));
    }

    static void selectInventoryHand(String id) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof BodyInventoryScreen
                || net.minecraft.client.Minecraft.getInstance().screen instanceof StoragePopupScreen)) return;
        reconcile();
        var view = POLICY.inventoryView();
        if (view == null || view.activeHand().equals(id)
                || !view.first().id().equals(id) && !view.second().id().equals(id)) return;
        var snapshot = POLICY.snapshot();
        long sequence = POLICY.selectionAction(clock);
        if (sequence != 0) PacketDistributor.sendToServer(new BodyHandActionRequest(
                BodyHandActionRequest.Action.SELECT_HAND, id, snapshot.epoch(), sequence, snapshot.revision(), null, null));
    }


    static void inventoryEquipment(BodyHandActionRequest.Action action) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof BodyInventoryScreen
                || net.minecraft.client.Minecraft.getInstance().screen instanceof StoragePopupScreen)) return;
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

    /** Pointer gestures cannot silently substitute the currently selected hand for their source. */
    static void popupEquipment(HandEquipmentGesture.Drag drag, HandBarLayout.Layout.Tile target) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof StoragePopupScreen)) return;
        reconcile();
        var view = POLICY.inventoryView();
        var proof = POLICY.snapshot();
        if (view == null || proof == null) return;
        if (target == HandBarLayout.Layout.Tile.FIRST || target == HandBarLayout.Layout.Tile.SECOND) {
            var host = target == HandBarLayout.Layout.Tile.FIRST ? view.first() : view.second();
            var grid = host == null || host.token() == null ? null : POLICY.heldGrid(host.token(), false);
            var heldPlan = HandEquipmentGesture.heldStorePlan(drag, target, view, proof, grid);
            if (heldPlan != null) {
                long sequence = POLICY.action(clock);
                if (sequence != 0) PacketDistributor.sendToServer(new BodyHandActionRequest(
                        BodyHandActionRequest.Action.HELD_GRID_STORE, heldPlan.hostHandId(), proof.epoch(), sequence,
                        proof.revision(), null, heldPlan.hostToken(), heldPlan.childToken(), heldPlan.x(), heldPlan.y(), heldPlan.rotation()));
                return;
            }
        }
        var plan = HandEquipmentGesture.plan(drag, target, view, proof);
        if (plan == null) return;
        long sequence = POLICY.action(clock);
        if (sequence != 0) PacketDistributor.sendToServer(plan.action() == BodyHandActionRequest.Action.TRANSFER_HAND
                ? new BodyHandActionRequest(plan.action(), drag.handId(), proof.epoch(), sequence,
                proof.revision(), null, drag.token(), null, -1, -1, -1, -1, -1, -1, null, null, plan.handId())
                : new BodyHandActionRequest(plan.action(), plan.handId(),
                proof.epoch(), sequence, proof.revision(), null, drag.token()));
    }

    static void popupSmartStore(HandEquipmentGesture.Drag drag, HandBarLayout.Layout.Tile target) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof StoragePopupScreen)) return;
        reconcile();
        var view = POLICY.inventoryView();
        var proof = POLICY.snapshot();
        var plan = HandEquipmentGesture.wornStorePlan(drag, target, view, proof);
        if (plan == null) return;
        long sequence = POLICY.action(clock);
        if (sequence != 0) PacketDistributor.sendToServer(new BodyHandActionRequest(plan.action(), plan.handId(),
                proof.epoch(), sequence, proof.revision(), null, plan.hostToken(), plan.childToken(),
                -1, -1, -1));
    }

    static void inventoryGrid(boolean belt, boolean store, String selectedToken, int x, int y, int rotation) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof BodyInventoryScreen)) return;
        reconcile();
        var view = POLICY.inventoryView();
        if (view == null) return;
        var grid = belt ? view.beltGrid() : view.backGrid();
        if (grid == null) return;
        var active = view.first().id().equals(view.activeHand()) ? view.first() : view.second();
        String child;
        if (store) {
            child = active.token();
            if (child == null || !child.equals(selectedToken)
                    || grid.proposed(active.itemId(), child, rotation, x, y) == null) return;
        } else {
            var entry = grid.entryAt(x, y);
            if (!grid.canTake() || entry == null || !entry.token().equals(selectedToken)) return;
            child = entry.token();
        }
        var snapshot = POLICY.snapshot();
        long sequence = POLICY.action(clock);
        if (sequence == 0) return;
        var action = belt ? (store ? BodyHandActionRequest.Action.GRID_STORE_BELT : BodyHandActionRequest.Action.GRID_TAKE_BELT)
                : (store ? BodyHandActionRequest.Action.GRID_STORE_BACK : BodyHandActionRequest.Action.GRID_TAKE_BACK);
        PacketDistributor.sendToServer(new BodyHandActionRequest(action, active.id(), snapshot.epoch(), sequence,
                snapshot.revision(), null, grid.host().hostToken(), child, x, y, store ? rotation : -1));
    }


    private static boolean request(BodyHandActionRequest.Action action) {
        BodyHandStateSnapshot snapshot = POLICY.snapshot();
        if (snapshot == null) return false;
        BodyHandStateSnapshot.Hand active = snapshot.hands().stream()
                .filter(hand -> hand.id().equals(snapshot.activeHand())).findFirst().orElse(null);
        if (active == null) return false;
        UUID target = null;
        if (action == BodyHandActionRequest.Action.PICKUP) {
            if (active.token() != null) return false;
            target = lookTarget(cameraBody);
            if (target == null) { POLICY.noItemUnderCursor(clock); return false; }
        } else if (active.token() == null) return false;
        long sequence = POLICY.action(clock);
        if (sequence == 0) return false;
        PacketDistributor.sendToServer(new BodyHandActionRequest(action, active.id(), snapshot.epoch(),
                sequence, snapshot.revision(), target, action == BodyHandActionRequest.Action.PICKUP ? null : active.token()));
        return true;
    }

    /** Only a nomination: server ray, reach, block occlusion, and item identity are authoritative. */
    private static UUID lookTarget(Mob body) {
        if (body == null) return null;
        Vec3 start = body.getEyePosition();
        Vec3 end = start.add(body.getLookAngle().scale(5));
        var block = body.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, body));
        double blockerDistance = start.distanceTo(block.getLocation());
        double maxDistance = blockerDistance * blockerDistance;
        UUID candidate = null;
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
            if (BodyPickupTargetOrder.precedes(distance, item.getUUID(), maxDistance, candidate)) {
                maxDistance = distance;
                candidate = item.getUUID();
            }
        }
        return candidate;
    }

    private static void onSnapshot(BodyHandStateSnapshot snapshot) {
        reconcile();
        if (POLICY.accept(snapshot)) {
            worldResultHandRefreshPending = false;
            WORLD.settledHand(POLICY.inventoryView() == null ? null : POLICY.snapshot());
            var settled = POLICY.inventoryView() == null ? null : POLICY.snapshot();
            if (settled != null) for (UUID target : java.util.List.copyOf(pendingWorldObservations))
                WORLD.query(target, settled.revision(), nonWorldWindowCount(), clock).ifPresent(PacketDistributor::sendToServer);
            if (settled != null) for (var world : WORLD.views())
                if (!WORLD.canAct(world.target())) WORLD.query(world.target(), settled.revision(),
                        nonWorldWindowCount(), clock).ifPresent(PacketDistributor::sendToServer);
            var view = POLICY.presentationView();
            if (settled != null) POPUPS.validateHandSources(settled.hands().stream()
                    .map(BodyHandStateSnapshot.Hand::token).filter(java.util.Objects::nonNull).toList());
            boolean hadWindows = !POPUPS.windows().isEmpty();
            StoragePopupLifecycle.reconcile(POPUPS, view != null && !POLICY.presentationPending(),
                    token -> StoragePopupLifecycle.resolveBody(token, view, key -> popupGrid(view, key, true)),
                    key -> popupGrid(view, key, true));
            if (hadWindows) closePointerIfEmpty();
        }
    }

    private static void onResult(BodyHandActionResult result) {
        reconcile();
        boolean correlated = POLICY.result(result, clock);
        var transition = StoragePopupLifecycle.transition(correlated, result);
        if (transition == null) return;
        var sourceWindow = POPUPS.window(transition.source());
        if (sourceWindow == null || !sourceWindow.visible()) return;
        if (transition.direction() == BodyHandActionResult.HostTransition.Direction.PICKUP) {
            var world = WORLD.view(transition.source().worldId());
            if (world != null && connection == net.minecraft.client.Minecraft.getInstance().getConnection())
                PacketDistributor.sendToServer(new BodyWorldGridClose(cameraEpoch, world.target(), world.session()));
            WORLD.close(transition.source().worldId());
            POPUPS.rekey(transition.source(), transition.destination());
        } else {
            UUID target = transition.destination().worldId();
            pendingWorldObservations.add(target);
            worldObservationRetryAt.put(target, clock);
            POPUPS.rekey(transition.source(), transition.destination());
        }
    }

    private static void onBlockUseResult(BodyBlockUseResult result) {
        reconcile();
        if (!POLICY.blockUseResult(result)) return;
        if (result.outcome() == BodyBlockUseResult.Outcome.PLACED
                || result.outcome() == BodyBlockUseResult.Outcome.RECOVERY_REQUIRED
                || result.outcome() == BodyBlockUseResult.Outcome.DENIED) {
            long sequence = POLICY.query(clock);
            if (sequence != 0) PacketDistributor.sendToServer(new BodyHandStateQuery(POLICY.queryEpoch(), sequence));
        }
    }

    public static void render(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        boolean storagePopup = mc.screen instanceof StoragePopupScreen;
        boolean gameplay = mc.screen == null;
        if (gameplay && currentBody() != null && !mc.options.hideGui)
            StoragePopupScreen.drawWindows(event.getGuiGraphics(), POPUPS,
                    mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        Mob body = currentBody();
        if (!BodyHandClientPolicy.showAimCue(body != null && GhostControlClient.committedCharacterEpoch() > 0,
                !mc.options.hideGui, gameplay)) {
            if (!storagePopup || body == null || mc.options.hideGui) return;
        }
        reconcile();
        var graphics = event.getGuiGraphics();
        if (gameplay) {
            int centerX = mc.getWindow().getGuiScaledWidth() / 2;
            int centerY = mc.getWindow().getGuiScaledHeight() / 2;
            // Visual only; never use the carrier's hit result as body/item authority.
            graphics.fill(centerX - 3, centerY, centerX + 4, centerY + 1, 0xff202020);
            graphics.fill(centerX, centerY - 3, centerX + 1, centerY + 4, 0xff202020);
            graphics.fill(centerX - 2, centerY, centerX + 3, centerY + 1, 0xffeeeeee);
            graphics.fill(centerX, centerY - 2, centerX + 1, centerY + 3, 0xffeeeeee);
        }
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        var snapshot = POLICY.presentationSnapshot();
        var bar = HandBarLayout.calculate(width, height);
        if (bar != null && snapshot != null && snapshot.hands().size() == 2) {
            boolean enabled = !POLICY.presentationPending();
            drawHandTile(event.getGuiGraphics(), mc, bar.leftHand(), snapshot.hands().get(0),
                    snapshot.activeHand().equals(snapshot.hands().get(0).id()), enabled);
            drawHandTile(event.getGuiGraphics(), mc, bar.rightHand(), snapshot.hands().get(1),
                    snapshot.activeHand().equals(snapshot.hands().get(1).id()), enabled);
            drawEquipmentTile(event.getGuiGraphics(), mc, bar.back(), snapshot.equipment(), "back", enabled);
            drawEquipmentTile(event.getGuiGraphics(), mc, bar.belt(), snapshot.equipment(), "belt", enabled);
            var info = HandInfoLayout.calculate(width, height, bar);
            if (info != null) {
                drawHandInfo(graphics, mc, info.left(), snapshot.hands().get(0), enabled, info.textBudget());
                drawHandInfo(graphics, mc, info.right(), snapshot.hands().get(1), enabled, info.textBudget());
            }
        }
        String feedback = POLICY.feedback();
        if (feedback != null && !feedback.toLowerCase(java.util.Locale.ROOT).contains("waiting"))
            drawHudLine(graphics, mc, feedback.replaceFirst("^Hands: ", ""), 8, 54, width);
    }

    private static void drawHandTile(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.Minecraft mc,
                                     HandBarLayout.Rect r, BodyHandStateSnapshot.Hand hand, boolean active, boolean enabled) {
        drawInsetPanel(g, r);
        if (active) g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + 1, 0xffc7a85b);
        if (hand.token() == null) drawPlaceholder(g, r, false);
        else drawStack(g, hand.itemId(), hand.count(), r, mc);
        if (!enabled) g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), 0x66090f12);
    }

    private static void drawHandInfo(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.Minecraft mc,
                                     HandBarLayout.Rect rect, BodyHandStateSnapshot.Hand hand, boolean enabled,
                                     int textBudget) {
        String name;
        if (hand.token() == null) name = net.minecraft.network.chat.Component.translatable("gui.moonstation14.hands.empty").getString();
        else {
            var id = net.minecraft.resources.ResourceLocation.tryParse(hand.itemId() == null ? "" : hand.itemId());
            var item = id == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            name = item == null ? net.minecraft.network.chat.Component.translatable("gui.moonstation14.hands.empty").getString()
                    : item.getDescription().getString();
            if (hand.count() > 1) name += " ×" + hand.count();
        }
        float scale = 0.76f;
        int available = Math.max(0, Math.min(textBudget, rect.width() - 6));
        name = mc.font.plainSubstrByWidth(name, (int) (available / scale));
        g.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), 0xcc11191d);
        g.fill(rect.x(), rect.y() + rect.height() - 1, rect.x() + rect.width(), rect.y() + rect.height(), 0xff60747c);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(rect.x() + 3, rect.y() + 2, 0);
        pose.scale(scale, scale, 1);
        g.drawString(mc.font, name, 0, 0, enabled ? 0xffe2e8e9 : 0xff858b8d, false);
        pose.popPose();
    }

    private static void drawEquipmentTile(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.Minecraft mc,
            HandBarLayout.Rect r, java.util.List<BodyHandStateSnapshot.EquipmentSlot> slots, String id, boolean enabled) {
        drawInsetPanel(g, r);
        var slot = slots.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
        if (slot == null || slot.token() == null) drawPlaceholder(g, r, true);
        else drawStack(g, slot.itemId(), slot.count(), r, mc);
        if (!enabled) g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), 0x66090f12);
    }

    private static void drawPlaceholder(net.minecraft.client.gui.GuiGraphics g, HandBarLayout.Rect r, boolean equipment) {
        int cx = r.x() + r.width() / 2, cy = r.y() + r.height() / 2;
        int color = equipment ? 0x557d9299 : 0xff60747c;
        if (equipment) {
            g.fill(cx - 5, cy - 4, cx + 5, cy + 5, color);
            g.fill(cx - 3, cy - 6, cx + 3, cy - 4, color);
        } else {
            g.fill(cx - 1, cy - 5, cx + 2, cy + 2, color);
            g.fill(cx - 4, cy - 1, cx + 5, cy + 2, color);
            g.fill(cx - 3, cy + 2, cx + 4, cy + 4, color);
        }
    }

    private static void drawStack(net.minecraft.client.gui.GuiGraphics g, String itemId, int count,
                                  HandBarLayout.Rect r, net.minecraft.client.Minecraft mc) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(itemId == null ? "" : itemId);
        var item = id == null ? net.minecraft.world.item.Items.BARRIER
                : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).orElse(net.minecraft.world.item.Items.BARRIER);
        var stack = new net.minecraft.world.item.ItemStack(item);
        if (count > 1) stack.setCount(Math.min(count, stack.getMaxStackSize()));
        float scale = Math.min(1f, Math.min((r.width() - 2) / 16f, (r.height() - 2) / 16f));
        if (scale > 0) {
            var pose = g.pose();
            pose.pushPose();
            pose.translate(r.x() + r.width() / 2f, r.y() + r.height() / 2f, 0);
            pose.scale(scale, scale, 1);
            g.renderItem(stack, -8, -8);
            pose.popPose();
        }
        if (count > 1) {
            String label = Integer.toString(count);
            var pose = g.pose();
            pose.pushPose();
            pose.translate(r.x() + r.width() - 2, r.y() + r.height() - mc.font.lineHeight * 0.62f, 0);
            pose.scale(0.62f, 0.62f, 1);
            g.drawString(mc.font, label, -mc.font.width(label), 0, 0xffffffff, true);
            pose.popPose();
        }
    }

    /** Recessed SS14-style hand-bar tile, kept local to client-owned presentation code. */
    private static void drawInsetPanel(net.minecraft.client.gui.GuiGraphics g, HandBarLayout.Rect r) {
        if (r.width() < 3 || r.height() < 3) return;
        g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), 0xff141f24);
        g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + 1, 0xff090f12);
        g.fill(r.x(), r.y() + r.height() - 1, r.x() + r.width(), r.y() + r.height(), 0xff60747c);
        g.fill(r.x(), r.y(), r.x() + 1, r.y() + r.height(), 0xff11191d);
        g.fill(r.x() + r.width() - 1, r.y(), r.x() + r.width(), r.y() + r.height(), 0xff11191d);
    }

    public static void renderBlockOutline(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.options.hideGui || mc.screen != null || mc.player == null || !mc.player.isSpectator()) return;
        reconcile();
        long epoch = GhostControlClient.committedCharacterEpoch();
        Mob body = currentBody();
        if (body == null || body != cameraBody || epoch == 0 || epoch != cameraEpoch
                || body.level() != mc.level || POLICY.snapshot() == null) return;
        BodyBlockOutlineRenderer.render(event, body);
    }

    private static int drawHudLine(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.Minecraft mc,
                                   String line, int x, int y, int width) {
        line = mc.font.plainSubstrByWidth(line, Math.max(0, width / 2 - 12));
        graphics.fill(x - 3, y - 2, x + mc.font.width(line) + 3, y + mc.font.lineHeight + 2, 0x99000000);
        graphics.drawString(mc.font, line, x, y, 0xffffffff, false);
        return y + mc.font.lineHeight + 4;
    }
}
