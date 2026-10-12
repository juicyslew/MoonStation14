package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseRequest;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseResult;
import com.juicyslew.moonstation14.ms14.hands.network.BodyBlockUseTargetProbe;
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
import com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction.ChemMasterDumpRequest;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/** Client presentation and intent only; all hand occupancy and transfer remain server-owned. */
public final class BodyHandClient {
    private static final org.slf4j.Logger DUMP_LOGGER = com.mojang.logging.LogUtils.getLogger();
    /** Existing normal E activation ray ceiling, shared with client-only body nomination. */
    public static final double NORMAL_WORLD_AIM_REACH = 6.0;
    private static final com.juicyslew.moonstation14.ms14.equipment.client.EquipmentPanelState EQUIPMENT_PANEL_STATE = new com.juicyslew.moonstation14.ms14.equipment.client.EquipmentPanelState();
    private static int suppressExaminePauseTicks;
    private static final net.minecraft.client.KeyMapping DROP = new net.minecraft.client.KeyMapping("key.moonstation14.body_drop",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q, "key.categories.moonstation14");
    private static final net.minecraft.client.KeyMapping SELECT_HAND = new net.minecraft.client.KeyMapping("key.moonstation14.select_hand",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.moonstation14");
    private static final net.minecraft.client.KeyMapping STORAGE = new net.minecraft.client.KeyMapping("key.moonstation14.storage",
             com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.moonstation14");
    private static final net.minecraft.client.KeyMapping WORLD_STORAGE = new net.minecraft.client.KeyMapping("key.moonstation14.world_storage",
             com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_E, "key.categories.moonstation14");
    private static final net.minecraft.client.KeyMapping ALTERNATE_WORLD_USE = new net.minecraft.client.KeyMapping(
            "key.moonstation14.alternate_world_use", net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME,
            net.neoforged.neoforge.client.settings.KeyModifier.ALT,
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_E, "key.categories.moonstation14");
    // A modifier-aware physical mouse mapping keeps this gesture rebindable and mouse-only.
    private static final net.minecraft.client.KeyMapping AIM_RELEASE_DUMP = new net.minecraft.client.KeyMapping(
            "key.moonstation14.aim_release_dump", net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME,
            net.neoforged.neoforge.client.settings.KeyModifier.SHIFT,
            com.mojang.blaze3d.platform.InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_LEFT,
            "key.categories.moonstation14");
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
    // Temporary grenade-specific controls; replace with item-driven use dispatch in a later sprint.
    private static final net.minecraft.client.KeyMapping PRIME_GRENADE = new net.minecraft.client.KeyMapping("key.moonstation14.prime_grenade",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.moonstation14");
    private static final net.minecraft.client.KeyMapping THROW_GRENADE = new net.minecraft.client.KeyMapping("key.moonstation14.throw_grenade",
            net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME,
            net.neoforged.neoforge.client.settings.KeyModifier.CONTROL,
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Q, "key.categories.moonstation14");
    private static final net.minecraft.client.KeyMapping USE_IN_HAND = new net.minecraft.client.KeyMapping("key.moonstation14.use_in_hand",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.moonstation14");
    private static final net.minecraft.client.KeyMapping DIRECT_CLIMB = new net.minecraft.client.KeyMapping("key.moonstation14.disposal_climb",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.moonstation14");
    // NeoForge modifier-aware mouse mappings are not consistently routed through InputEvent.MouseButton.Pre.
    // Keep pull rebindable with the explicit SS14-style P adaptation instead.
    private static final net.minecraft.client.KeyMapping TOGGLE_PULL = new net.minecraft.client.KeyMapping("key.moonstation14.toggle_pull",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, "key.categories.moonstation14");
    private static final net.minecraft.client.KeyMapping CYCLE_PILE = new net.minecraft.client.KeyMapping("key.moonstation14.cycle_pile",
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_BRACKET, "key.categories.moonstation14");
    private static final BodyHandClientPolicy POLICY = new BodyHandClientPolicy();
    private static final WorldGridClientPolicy WORLD = new WorldGridClientPolicy();
    private static final StoragePopupWorkspace POPUPS = new StoragePopupWorkspace(320, 240);
    private static Object connection, level, player;
    private static Mob cameraBody;
    private static boolean wasContained;
    private static long cameraEpoch;
    private static long clock;
    private static long pullActionSequence;
    private static boolean pullProjectionRefreshPending;
    private static boolean worldResultHandRefreshPending;
    private static boolean altWorldStoragePressed;
    private static boolean altSyringeDosePressed;
    private static UUID worldCrossPendingTarget;
    private static final java.util.Set<UUID> pendingWorldObservations = new java.util.HashSet<>();
    private static final java.util.Map<UUID, Long> worldObservationRetryAt = new java.util.HashMap<>();
    private static final PointerModePolicy POINTER = new PointerModePolicy();
    private record PlacementProof(UUID body, long epoch, long handsRevision, String hand, String token, String itemId, int heldCount, int requiredCount,
                                  long planRevision, String prototype, int rotation) { }
    private record ConstructionUsePlan(UUID body, long epoch, long revision, String prototype, int rotation, int required) { }
    private static PlacementProof placementProof;
    private static final DeferredConstructionUse deferredPlacement = new DeferredConstructionUse();
    private static final AltTapGesture ALT_TAP = new AltTapGesture();
    private static final AimReleaseWorldDump DUMP_GESTURE = new AimReleaseWorldDump();
    private static long dumpSequence;
    private static UUID dumpBody;
    private static UUID dumpSource, dumpGestureId;
    private static net.minecraft.resources.ResourceLocation dumpDimension;
    private static boolean suppressDumpEscapeScreenKey;
    private static final AimReleaseWorldDumpFeedback DUMP_FEEDBACK = new AimReleaseWorldDumpFeedback();
    private static net.minecraft.core.BlockPos queuedDumpTarget;
    private static final AimReleasePhysicalInput DUMP_PHYSICAL_INPUT = new AimReleasePhysicalInput();
    private static final PileSelectionPolicy PILE = new PileSelectionPolicy();
    private static java.util.List<ItemEntity> pileItems = java.util.List.of();
    private static long pileFrame = -1;
    private static long pileEpoch;

    private BodyHandClient() { }
    public static Component worldContextKeyMessage() { return WORLD_STORAGE.getTranslatedKeyMessage(); }

    /** Refresh hand proof after a timed operation commits outside the ordinary action-result path. */
    public static void refreshAfterTimedCompletion() {
        reconcile();
        POLICY.externalTimedCompletion();
        long sequence = POLICY.query(clock);
        if (sequence == 0) return;
        WORLD.handQueryIssued();
        PacketDistributor.sendToServer(new BodyHandStateQuery(POLICY.queryEpoch(), sequence));
    }

    public static void ingestionCommitted(com.juicyslew.moonstation14.ms14.ingestion.IngestionCommitted hint) {
        if (hint == null) return;
        reconcile();
        Mob body = currentBody();
        if (body == null || !hint.matchesAuthority(body.getUUID(), GhostControlClient.committedCharacterEpoch())) return;
        refreshAfterTimedCompletion();
    }

    public static void install() {
        com.juicyslew.moonstation14.ms14.examine.client.ExamineClient.install();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(BodyHandClient::contextualKey);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(BodyHandClient::dumpEscapeScreenKey);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(BodyHandClient::mouseScroll);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Opening e) -> {
            if (e.getNewScreen() != null) deferredPlacement.clear();
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(BodyHandClient::suppressNativeInventoryScreen);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(BodyHandClient::renderChatScreenHud);
        BodyHandStateSnapshot.setClientHandler(BodyHandClient::onSnapshot);
        com.juicyslew.moonstation14.ms14.ingestion.IngestionCommitted.setClientHandler(BodyHandClient::ingestionCommitted);
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
        event.register(ALTERNATE_WORLD_USE);
        event.register(AIM_RELEASE_DUMP);
        event.register(INVENTORY);
        event.register(DIRECT_CLIMB);
        event.register(EQUIP_BAG);
        event.register(EQUIP_BELT);
        event.register(PRIME_GRENADE);
        event.register(THROW_GRENADE);
        event.register(USE_IN_HAND);
        event.register(CYCLE_PILE);
    }

    public static void reset() {
        com.juicyslew.moonstation14.ms14.stripping.client.StripClient.instance().contextReset();
        EQUIPMENT_PANEL_STATE.beginBody(Long.MIN_VALUE);
        com.juicyslew.moonstation14.ms14.examine.client.ExamineClient.clear();
        var mc = net.minecraft.client.Minecraft.getInstance();
        com.juicyslew.moonstation14.ms14.doors.ui.client.AirlockWireClient.authorityLost();
        closeWorldWindows();
        com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.resetOwnedContext();
        if (mc.screen instanceof StoragePopupScreen || mc.screen instanceof BodyInventoryScreen) mc.setScreen(null);
        POPUPS.clear();
        com.juicyslew.moonstation14.ms14.medical.client.HealthAnalyzerWorkspace.INSTANCE.clear();
        com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client.ChemMasterWorkspace.INSTANCE.clear();
        com.juicyslew.moonstation14.ms14.devicenet.item.client.NetworkConfiguratorClientState.clear();
        POLICY.reset();
        placementProof = null; deferredPlacement.clear();
        WORLD.clear();
        worldResultHandRefreshPending = false;
        altWorldStoragePressed = false;
        altSyringeDosePressed = false;
        worldCrossPendingTarget = null;
        pendingWorldObservations.clear();
        worldObservationRetryAt.clear();
        POINTER.authorityClear();
        ALT_TAP.clear();
        if (DUMP_GESTURE.active()) sendDumpCancel();
        DUMP_GESTURE.clear(); dumpBody = dumpSource = dumpGestureId = null; dumpDimension = null; DUMP_FEEDBACK.clear(); queuedDumpTarget = null; clearDumpPhysicalInput();
        suppressDumpEscapeScreenKey = false;
        connection = level = player = null;
        cameraBody = null;
        cameraEpoch = 0;
        wasContained = false;
        PILE.clear(); pileItems = java.util.List.of(); pileFrame = -1; pileEpoch = 0;
        while (DROP.consumeClick()) { }
        while (SELECT_HAND.consumeClick()) { }
        while (STORAGE.consumeClick()) { }
        while (WORLD_STORAGE.consumeClick()) { }
        while (ALTERNATE_WORLD_USE.consumeClick()) { }
        while (AIM_RELEASE_DUMP.consumeClick()) { }
        while (INVENTORY.consumeClick()) { }
        while (DIRECT_CLIMB.consumeClick()) { }
        while (EQUIP_BAG.consumeClick()) { }
        while (EQUIP_BELT.consumeClick()) { }
        while (PRIME_GRENADE.consumeClick()) { }
        while (THROW_GRENADE.consumeClick()) { }
        while (USE_IN_HAND.consumeClick()) { }
        while (CYCLE_PILE.consumeClick()) { }
    }

    public static boolean isCurrentMedicalBody(java.util.UUID actorId) {
        Mob body = currentBody();
        return actorId != null && body != null && actorId.equals(body.getUUID());
    }

    private static Mob currentBody() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        return mc.player != null && mc.player.isSpectator() && mc.getConnection() != null
                ? GhostControlClient.ownedCharacterForPresentation() : null;
    }

    private static void reconcile() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (connection != mc.getConnection()) {
            reset();
            connection = mc.getConnection();
        }
        if (level != mc.level || player != mc.player) {
            com.juicyslew.moonstation14.ms14.stripping.client.StripClient.instance().contextReset();
            com.juicyslew.moonstation14.ms14.doors.ui.client.AirlockWireClient.authorityLost();
            com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.resetOwnedContext();
            pendingWorldObservations.clear();
            worldObservationRetryAt.clear();
            closeWorldWindows();
            WORLD.clear();
            worldCrossPendingTarget = null;
            POLICY.suspend();
            com.juicyslew.moonstation14.ms14.timed_interaction.client.TimedInteractionClient.clearPredictions();
            if (mc.screen instanceof StoragePopupScreen || mc.screen instanceof BodyInventoryScreen) mc.setScreen(null);
            POPUPS.clear();
            com.juicyslew.moonstation14.ms14.medical.client.HealthAnalyzerWorkspace.INSTANCE.clear();
            com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client.ChemMasterWorkspace.INSTANCE.clear();
            com.juicyslew.moonstation14.ms14.devicenet.item.client.NetworkConfiguratorClientState.clear();
            POINTER.authorityClear();
            level = mc.level;
            player = mc.player;
            cameraBody = null;
            cameraEpoch = 0;
            PILE.clear(); pileItems = java.util.List.of(); pileFrame = -1; pileEpoch = 0;
        }
        Mob body = currentBody();
        boolean contained = com.juicyslew.moonstation14.ms14.player_body_control.client.ContainedCharacterClientView.isContained();
        if (contained && !wasContained) { closeWorldWindows(); POLICY.containedEntry(); deferredPlacement.clear(); }
        wasContained = contained;
        long epoch = GhostControlClient.committedCharacterEpoch();
        if (cameraBody != body || cameraEpoch != epoch) {
            if (DUMP_GESTURE.active()) { sendDumpCancel(); DUMP_GESTURE.contextLost(); }
            com.juicyslew.moonstation14.ms14.stripping.client.StripClient.instance().contextReset();
            com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.resetOwnedContext();
        }
        if (deferredPlacement.pending()) {
            var queued = deferredPlacement.intent();
            var plan = constructionUsePlan(queued.item());
            if (body == null || !body.getUUID().equals(queued.body()) || epoch != queued.epoch() || plan == null
                    || plan.revision() != queued.planRevision() || !queued.recipe().equals(plan.prototype())
                    || queued.rotation() != plan.rotation()) deferredPlacement.clear();
        }
        if (cameraBody != body || cameraEpoch != epoch || body == null || epoch == 0) {
            com.juicyslew.moonstation14.ms14.doors.ui.client.AirlockWireClient.authorityLost();
            pendingWorldObservations.clear();
            worldObservationRetryAt.clear();
            closeWorldWindows();
            WORLD.clear();
            worldCrossPendingTarget = null;
            POLICY.suspend();
            com.juicyslew.moonstation14.ms14.timed_interaction.client.TimedInteractionClient.clearPredictions();
            if (mc.screen instanceof StoragePopupScreen || mc.screen instanceof BodyInventoryScreen) mc.setScreen(null);
            POPUPS.clear();
            com.juicyslew.moonstation14.ms14.medical.client.HealthAnalyzerWorkspace.INSTANCE.clear();
            com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client.ChemMasterWorkspace.INSTANCE.clear();
            com.juicyslew.moonstation14.ms14.devicenet.item.client.NetworkConfiguratorClientState.clear();
            POINTER.authorityClear();
            cameraBody = body;
            cameraEpoch = epoch;
            if (body == null || epoch == 0) return;
        }
        POLICY.bind(mc.player.getUUID(), body.getUUID(), epoch);
        WORLD.bind(mc.player.getUUID(), body.getUUID(), epoch);
        POPUPS.bind(body.getUUID(), epoch);
        if (mc.screen != null && !(mc.screen instanceof StoragePopupScreen)
                && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) POLICY.suspend();
    }

    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if (suppressExaminePauseTicks > 0) suppressExaminePauseTicks--;
        clock++;
        reconcile();
        if (DUMP_GESTURE.active() && looseEntity(DUMP_GESTURE.source()) == null) {
            sendDumpCancel();
            DUMP_GESTURE.contextLost();
        }
        if (queuedDumpTarget != null && (!DUMP_FEEDBACK.ownsContext(dumpGestureId, dumpSource,
                currentBody() == null ? null : currentBody().getUUID(), GhostControlClient.committedCharacterEpoch())
                || looseEntity(dumpSource) == null)) sendDumpCancel();
        while (AIM_RELEASE_DUMP.consumeClick()) { }
        com.juicyslew.moonstation14.ms14.stripping.client.StripClient.instance().tick(clock);
        com.juicyslew.moonstation14.ms14.examine.client.ExamineClient.tick();
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (PointerModePolicy.shouldRestoreHost(POPUPS.pointerMode(), mc.screen == null, currentBody() != null))
            mc.setScreen(new StoragePopupScreen(POPUPS));
        var activeWire = com.juicyslew.moonstation14.ms14.doors.ui.client.AirlockWireClient.active();
        if (activeWire != null) activeWire.tick();
        if (cameraBody == null) {
            pullProjectionRefreshPending = false;
            altWorldStoragePressed = false;
            altSyringeDosePressed = false;
            while (DROP.consumeClick()) { }
            while (SELECT_HAND.consumeClick()) { }
            while (STORAGE.consumeClick()) { }
            while (WORLD_STORAGE.consumeClick()) { }
            while (ALTERNATE_WORLD_USE.consumeClick()) { }
            while (INVENTORY.consumeClick()) { }
            while (DIRECT_CLIMB.consumeClick()) { }
            while (EQUIP_BAG.consumeClick()) { }
            while (EQUIP_BELT.consumeClick()) { }
            while (PRIME_GRENADE.consumeClick()) { }
            while (THROW_GRENADE.consumeClick()) { }
            while (USE_IN_HAND.consumeClick()) { }
            while (CYCLE_PILE.consumeClick()) { }
            return;
        }
        boolean inventory = INVENTORY.consumeClick();
        boolean alternateWorldUse = ALTERNATE_WORLD_USE.consumeClick();
        boolean worldStorage = WORLD_STORAGE.consumeClick();
        boolean altWorldStorage = altWorldStoragePressed;
        altWorldStoragePressed = false;
        boolean equipBag = EQUIP_BAG.consumeClick();
        boolean equipBelt = EQUIP_BELT.consumeClick();
        if (inventory && !inventoryUsesAlt() && !shiftDown() && !controlDown() && !altComboDown()) {
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
        if (proof != null) {
            var posture = cameraBody.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.POSTURE.get());
            boolean posturePulling = posture != null && posture.data().pulling();
            boolean snapshotPulling = proof.hands().stream().anyMatch(hand -> hand.pullTarget() != null);
            if (posturePulling == snapshotPulling) pullProjectionRefreshPending = false;
            else if (!pullProjectionRefreshPending) {
                pullProjectionRefreshPending = true;
                refreshAfterTimedCompletion();
            }
        }
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
        boolean directClimb = DIRECT_CLIMB.consumeClick();
        boolean contained = com.juicyslew.moonstation14.ms14.player_body_control.client.ContainedCharacterClientView.isContained();
        boolean ownerMenu = contained && mc.screen instanceof com.juicyslew.moonstation14.ms14.disposal.unit.ui.client.DisposalUnitScreen;
        if (mc.screen != null && !ownerMenu) {
            POLICY.cancelThrowIntent();
            return;
        }
        long window = mc.getWindow().getWindow();
        boolean shiftDown = org.lwjgl.glfw.GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean controlDown = org.lwjgl.glfw.GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean altDown = org.lwjgl.glfw.GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
        boolean down = com.juicyslew.moonstation14.ms14.standing.PostureSystem.isDown(cameraBody);
        boolean sharesRoll = com.juicyslew.moonstation14.ms14.standing.client.PostureClient.sharesRollKey(DIRECT_CLIMB);
        if (!ownerMenu && directClimb && DirectClimbInputPolicy.mayClimb(
                sharesRoll, down, shiftDown, controlDown, altDown))
            requestBlockUse(BodyBlockUseRequest.Intent.CLIMB_INSIDE);
        boolean prime = PRIME_GRENADE.consumeClick();
        boolean throwGrenade = THROW_GRENADE.consumeClick();
        boolean useInHand = USE_IN_HAND.consumeClick();
        // Alt+Z is captured here because modifier-aware mappings can drop the queued Z click.
        boolean altUseInHand = altSyringeDosePressed;
        altSyringeDosePressed = false;
        boolean cyclePile = false;
        while (CYCLE_PILE.consumeClick()) cyclePile = true;
        refreshPile(cameraBody);
        if (cyclePile) PILE.cycle(pileItems.stream().map(ItemEntity::getUUID).toList());
        if (!com.juicyslew.moonstation14.ms14.ui.client.GameplayWindow.allowsBodyInput(
                mc.screen,mc.mouseHandler.isMouseGrabbed())) {
            POLICY.cancelThrowIntent();
            return;
        }
        boolean handUseRequested = false;
        var worldGesture = BodyInputGesturePolicy.dispatch(false, true, equipBag, equipBelt,
                alternateWorldUse, worldStorage, altWorldStorage);
        if (worldGesture == BodyInputGesturePolicy.Route.SMART_BAG) smartStorage(false);
        else if (worldGesture == BodyInputGesturePolicy.Route.SMART_BELT) smartStorage(true);
        else if (worldGesture == BodyInputGesturePolicy.Route.ALTERNATE_WORLD_USE) alternateWorldUse();
        else if (worldGesture == BodyInputGesturePolicy.Route.NORMAL_WORLD_USE) {
            if (!openAimedWorldActivation()) ordinaryWorldUse();
        }
        if (com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.textFocused()) {
            while (DROP.consumeClick()) { } while (SELECT_HAND.consumeClick()) { }
            while (STORAGE.consumeClick()) { } while (WORLD_STORAGE.consumeClick()) { }
            while (ALTERNATE_WORLD_USE.consumeClick()) { } while (INVENTORY.consumeClick()) { }
            while (DIRECT_CLIMB.consumeClick()) { } while (EQUIP_BAG.consumeClick()) { }
            while (EQUIP_BELT.consumeClick()) { } while (PRIME_GRENADE.consumeClick()) { }
            while (THROW_GRENADE.consumeClick()) { } while (USE_IN_HAND.consumeClick()) { }
            while (TOGGLE_PULL.consumeClick()) { } while (CYCLE_PILE.consumeClick()) { }
            altWorldStoragePressed=false;
            return;
        }
        boolean ctrlV = storage && controlDown() && !shiftDown() && !altDown();
        if (storage && !controlDown() && !altDown())
            togglePopup(shiftDown() ? StoragePopupWorkspace.HostKind.BELT : StoragePopupWorkspace.HostKind.BAG);
        if (drop && !throwGrenade && !controlDown()) request(BodyHandActionRequest.Action.DROP);
        else if (ctrlV || select && !storage) selectHand();
        if (prime) handUseRequested = request(BodyHandActionRequest.Action.PRIME_GRENADE);
        else handUseRequested = HeldUseRequestDispatch.routePressed(useInHand, controlDown, altDown,
                () -> true, () -> HeldUseRequestDispatch.routeActivation(BodyHandClient::quickEquipInHand,
                        BodyHandClient::toggleActiveHeldStorage, () -> {
                    var view = POLICY.inventoryView();
                    var active = view == null ? null : (view.first().id().equals(view.activeHand()) ? view.first() : view.second());
                    var kind = active == null ? MedicalUseGesturePolicy.Kind.NONE : medicalKind(active.itemId());
                    var inHand = MedicalUseGesturePolicy.inHand(kind, shiftDown(), true);
                    if (inHand == MedicalUseGesturePolicy.InHand.CYCLE_SYRINGE_MODE)
                        return request(BodyHandActionRequest.Action.USE_IN_HAND);
                    else if (inHand == MedicalUseGesturePolicy.InHand.SYRINGE_SELF_USE)
                        return request(BodyHandActionRequest.Action.USE_BODY, cameraBody == null ? null : cameraBody.getUUID());
                    else if (inHand == MedicalUseGesturePolicy.InHand.SELF_SCAN)
                        return request(BodyHandActionRequest.Action.USE_BODY, cameraBody == null ? null : cameraBody.getUUID());
                    return request(BodyHandActionRequest.Action.USE_IN_HAND);
                }));
        var proofHand = POLICY.inventoryView();
        var proofActive = proofHand == null ? null : proofHand.first().id().equals(proofHand.activeHand()) ? proofHand.first() : proofHand.second();
        if (altUseInHand && proofActive != null && medicalKind(proofActive.itemId()) == MedicalUseGesturePolicy.Kind.SYRINGE)
            request(BodyHandActionRequest.Action.CYCLE_SYRINGE_AMOUNT);
        if (throwGrenade && !POLICY.requestThrowAfterPrime(clock)) {
            if (!handUseRequested) request(BodyHandActionRequest.Action.THROW_ITEM);
        }
        if (POLICY.takeReadyThrow(clock) && request(BodyHandActionRequest.Action.THROW_ITEM)) POLICY.throwSent();
    }

    private static ConstructionUsePlan constructionUsePlan(String sourceItem) {
        var shared = com.juicyslew.moonstation14.ms14.disposal.ui.client.DisposalConstructionClient.confirmedSharedPlan();
        if (shared != null) {
            var id = net.minecraft.resources.ResourceLocation.tryParse(shared.recipe());
            var stage = id == null ? null : com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime
                    .clientConstructionCatalog().initialStage(id).orElse(null);
            if (stage == null) return null;
            int needed = stage.steps().stream().filter(s -> s instanceof com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph.MaterialStep)
                    .map(s -> (com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph.MaterialStep)s)
                    .filter(s -> s.material() == com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph.MaterialId.Steel
                            ? "moonstation14:steel".equals(sourceItem) : "moonstation14:plastic".equals(sourceItem))
                    .mapToInt(com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph.MaterialStep::amount).sum();
            if (needed <= 0) return null;
            return new ConstructionUsePlan(shared.body(), shared.epoch(), shared.revision(), shared.recipe(), shared.rotation(), needed);
        }
        var legacy = com.juicyslew.moonstation14.ms14.disposal.ui.client.DisposalConstructionClient.state();
        if (legacy == null || !legacy.active()) return null;
        var data = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.clientDisposals()
                .get(net.minecraft.resources.ResourceLocation.tryParse(legacy.prototype()));
        var step = data == null ? null : data.construction();
        if (step == null || !step.material().equals(sourceItem)) return null;
        return new ConstructionUsePlan(legacy.bodyUUID(), legacy.epoch(), legacy.revision(), legacy.prototype(),
                legacy.rotationOffset(), step.steelCount());
    }

    /** Presentation-cache hint only: re-query owned hand proof after an owned world job terminates. */
    public static void externalTimedWorldCompletion(UUID bodyId, net.minecraft.resources.ResourceLocation dimension) {
        reconcile();
        Mob body = currentBody();
        if (body == null || bodyId == null || dimension == null || !body.getUUID().equals(bodyId)
                || !body.level().dimension().location().equals(dimension)
                || cameraBody != body || cameraEpoch != GhostControlClient.committedCharacterEpoch()) return;
        refreshAfterTimedCompletion();
    }

    private static boolean requestBlockUse(BodyBlockUseRequest.Intent intent) {
        var snapshot = POLICY.snapshot();
        if (snapshot == null || cameraBody == null
                || com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isClientActionBlocked(cameraBody)) return false;
        var hand = snapshot.hands().stream().filter(slot -> slot.id().equals(snapshot.activeHand())).findFirst().orElse(null);
        if (hand == null) return false;
        var plan = hand.itemId() == null ? null : constructionUsePlan(hand.itemId());
        placementProof = intent == BodyBlockUseRequest.Intent.USE && plan != null && hand.itemId() != null
                && plan.body().equals(snapshot.bodyId()) && plan.epoch() == snapshot.epoch()
                ? new PlacementProof(snapshot.bodyId(), snapshot.epoch(), snapshot.revision(), hand.id(), hand.token(), hand.itemId(),
                hand.count(), plan.required(), plan.revision(), plan.prototype(), plan.rotation()) : null;
        boolean contained = com.juicyslew.moonstation14.ms14.player_body_control.client.ContainedCharacterClientView.isContained();
        var ownerTarget = containedWorldTarget();
        if (contained && ownerTarget.isEmpty()) return false;
        var start = cameraBody.getEyePosition();
        var end = start.add(cameraBody.getLookAngle().scale(NORMAL_WORLD_AIM_REACH));
        var hit = cameraBody.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, cameraBody));
        if (contained) hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(ownerTarget.orElseThrow()).add(0, .5, 0), net.minecraft.core.Direction.UP, ownerTarget.orElseThrow(), true);
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return false;
        long sequence = POLICY.blockUseSequence(clock);
        if (sequence == 0) return false;
        PacketDistributor.sendToServer(new BodyBlockUseRequest(snapshot.epoch(), sequence, snapshot.revision(),
                hand.id(), hand.token(), hit.getBlockPos(), hit.getDirection(),
                net.minecraft.client.Minecraft.getInstance().player.input.shiftKeyDown, intent));
        return true;
    }

    private static boolean openAimedWorldActivation() {
        reconcile();
        if (cameraBody == null) return false;
        var ownerTarget = containedWorldTarget();
        if (com.juicyslew.moonstation14.ms14.player_body_control.client.ContainedCharacterClientView.isContained()) {
            if (ownerTarget.isEmpty() || !isWorldActivationTarget(cameraBody, ownerTarget.get())) return false;
            requestBlockUse(BodyBlockUseRequest.Intent.ACTIVATE_WORLD);
            return true;
        }
        if (com.juicyslew.moonstation14.ms14.stripping.client.StripClient.instance().openAimed()) return true;
        var start = cameraBody.getEyePosition();
        var end = start.add(cameraBody.getLookAngle().scale(NORMAL_WORLD_AIM_REACH));
        var hit = cameraBody.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, cameraBody));
        if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return false;
        if (!isWorldActivationTarget(cameraBody, hit.getBlockPos())) return false;
        requestBlockUse(BodyBlockUseRequest.Intent.ACTIVATE_WORLD);
        return true;
    }

    private static java.util.Optional<net.minecraft.core.BlockPos> containedWorldTarget() {
        return com.juicyslew.moonstation14.ms14.player_body_control.client.ContainedCharacterClientView.worldTarget();
    }

    private static net.minecraft.world.phys.BlockHitResult aimedWorldBlock() {
        if (com.juicyslew.moonstation14.ms14.player_body_control.client.ContainedCharacterClientView.isContained())
            return containedWorldTarget().map(pos -> new net.minecraft.world.phys.BlockHitResult(
                    Vec3.atCenterOf(pos).add(0, .5, 0), net.minecraft.core.Direction.UP, pos, true)).orElse(null);
        return aimedBlock();
    }

    private static boolean isWorldActivationTarget(Mob body, net.minecraft.core.BlockPos pos) {
        var target = body.level().getBlockEntity(pos);
        return target instanceof com.juicyslew.moonstation14.ms14.disposal.unit.DisposalUnitBlockEntity
                || target instanceof com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity
                || target instanceof com.juicyslew.moonstation14.ms14.atmos.piping.alarm.AirAlarmBlockEntity;
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
        if (com.juicyslew.moonstation14.ms14.player_body_control.client.ContainedCharacterClientView.isContained()) return false;
        if (currentBody() == null || POLICY.inventoryView() == null) return false;
        var start = cameraBody.getEyePosition();
        var end = start.add(cameraBody.getLookAngle().scale(6.0));
        var block = cameraBody.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, cameraBody));
        if (block.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            if (isWorldActivationTarget(cameraBody, block.getBlockPos())) return true;
        }
        UUID target = lookTarget(cameraBody);
        return target != null && cameraBody.level().getEntitiesOfClass(ItemEntity.class,
                cameraBody.getBoundingBox().inflate(6), item -> item.getUUID().equals(target)
                        && (item.getItem().is(com.juicyslew.moonstation14.item.ModItems.BAG)
                        || item.getItem().is(com.juicyslew.moonstation14.item.ModItems.BELT)
                        || item.getItem().is(com.juicyslew.moonstation14.item.ModItems.PILL_CANISTER))).size() == 1;
    }

    private static void contextualKey(net.neoforged.neoforge.client.event.InputEvent.Key event) {
        var screen = net.minecraft.client.Minecraft.getInstance().screen;
        if (event.getAction() == GLFW.GLFW_PRESS && event.getKey() == GLFW.GLFW_KEY_ESCAPE && DUMP_GESTURE.active()) {
            sendDumpCancel(); DUMP_GESTURE.escape(); suppressExaminePauseTicks = 1; suppressDumpEscapeScreenKey = screen != null; return;
        }
        if (screen == null && event.getAction() == GLFW.GLFW_PRESS && event.getKey() == GLFW.GLFW_KEY_ESCAPE
                && com.juicyslew.moonstation14.ms14.examine.client.ExamineClient.hasPopup()) {
            suppressExaminePauseTicks = 1;
            com.juicyslew.moonstation14.ms14.examine.client.ExamineClient.escape();
            return;
        }
        if (constructionSearchFocused()) return;
        boolean altBinding = inventoryUsesAlt() && INVENTORY.matches(event.getKey(), event.getScanCode());
        if (event.getAction() == GLFW.GLFW_PRESS && !altBinding
                && screen instanceof com.juicyslew.moonstation14.ms14.ui.client.GameplayWindow
                && popupAction(event.getKey(), event.getScanCode(), event.getModifiers(), true)) return;
        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (altBinding) ALT_TAP.pressAlt();
            else {
                ALT_TAP.otherPress();
                if (DUMP_GESTURE.active() && (event.getKey() == GLFW.GLFW_KEY_LEFT_SHIFT || event.getKey() == GLFW.GLFW_KEY_RIGHT_SHIFT
                        || event.getKey() == GLFW.GLFW_KEY_LEFT_CONTROL || event.getKey() == GLFW.GLFW_KEY_RIGHT_CONTROL
                        || event.getKey() == GLFW.GLFW_KEY_LEFT_ALT || event.getKey() == GLFW.GLFW_KEY_RIGHT_ALT))
                    DUMP_GESTURE.modifierPressed();
            }
        } else if (event.getAction() == GLFW.GLFW_RELEASE && altBinding) {
            if (ALT_TAP.releaseAlt()) {
                reconcile();
                if (cameraBody != null) {
                    boolean open = POPUPS.pointerMode();
                    if (open) dismissPointerMode();
                    else { POINTER.manualOpen(); pointerMode(true); }
                }
            }
            return;
        }
        if (event.getAction() == GLFW.GLFW_RELEASE) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        // KeyMapping's modifier-aware lookup may reject this E press before queuing the
        // ALT+E mapping. Capture the modifier at the press event (not next client tick).
        if (WORLD_STORAGE.matches(event.getKey(), event.getScanCode()) && altDown()) altWorldStoragePressed = true;
        if (USE_IN_HAND.matches(event.getKey(), event.getScanCode()) && altDown()) altSyringeDosePressed = true;
        // Key mappings queue modifier-validated clicks; tick dispatches them after UI gating.
    }

    private static void suppressNativeInventoryScreen(net.neoforged.neoforge.client.event.ScreenEvent.Opening event) {
        if (suppressExaminePauseTicks > 0 && event.getNewScreen() instanceof net.minecraft.client.gui.screens.PauseScreen) {
            suppressExaminePauseTicks = 0;
            event.setCanceled(true);
            return;
        }
        suppressExaminePauseTicks = 0;
        boolean nativeInventory = event.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
                || event.getNewScreen() instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
        if (BodyInputGesturePolicy.suppressNativeInventory(nativeInventory, hasCommittedBody())) {
            event.setCanceled(true);
        }
    }

    private static void ordinaryWorldUse() {
        reconcile();
        if (aimedWorldEligible()) {
            openAimedWorld();
            return;
        }
        var view = POLICY.inventoryView();
        var active = view == null ? null : view.first().id().equals(view.activeHand()) ? view.first() : view.second();
        UUID target = lookTarget(cameraBody);
        var route = BodyInputGesturePolicy.normalWorldRoute(false, target != null,
                active != null && active.token() != null);
        if (route == BodyInputGesturePolicy.NormalWorldRoute.ACTIVATE_TARGET) {
            var loose = findAimedLoose(target);
            if (loose != null
                    && com.juicyslew.moonstation14.ms14.devicenet.ModNetworkDeviceItems.resolve(loose.getItem(), true)
                    .filter(profile -> profile.signallerPort().isPresent()).isPresent())
                request(BodyHandActionRequest.Action.ACTIVATE_TARGET);
            return;
        }
        if (route == BodyInputGesturePolicy.NormalWorldRoute.TARGET_USE) {
            request(BodyHandActionRequest.Action.USE_TARGET);
        } else if (route == BodyInputGesturePolicy.NormalWorldRoute.BLOCK_ACTIVATE) {
            requestBlockUse(BodyBlockUseRequest.Intent.ACTIVATE_WORLD);
        }
    }

    private static boolean alternateWorldUse() {
        reconcile();
        var view = POLICY.inventoryView();
        if (cameraBody == null || view == null) return false;
        var active = view.first().id().equals(view.activeHand()) ? view.first() : view.second();
        UUID aimed = lookTarget(cameraBody);
        var puddle = aimedPuddle(cameraBody);
        if (active.token() == null && puddle != null) return requestPuddleSip(puddle);
        if ((active.token() != null && liquidItem(active.itemId())) || aimedLooseLiquid(aimed))
            return request(BodyHandActionRequest.Action.ALTERNATE_TARGET);
        return false;
    }

    private static void onBlockUseResult(BodyBlockUseResult result) {
        reconcile();
        if (!POLICY.blockUseResult(result)) return;
        if (result.outcome() == BodyBlockUseResult.Outcome.RECOVERY_REQUIRED) deferredPlacement.clear();
        if (result.outcome() == BodyBlockUseResult.Outcome.PLACED
                || result.outcome() == BodyBlockUseResult.Outcome.HAND_CHANGED
                || result.outcome() == BodyBlockUseResult.Outcome.RECOVERY_REQUIRED
                || result.outcome() == BodyBlockUseResult.Outcome.DENIED
                || result.outcome() == BodyBlockUseResult.Outcome.WORK_STARTED) {
            long sequence = POLICY.query(clock);
            if (sequence != 0) {
                WORLD.handQueryIssued();
                PacketDistributor.sendToServer(new BodyHandStateQuery(POLICY.queryEpoch(), sequence));
            }
        }
    }

    public static void mouseButton(net.neoforged.neoforge.client.event.InputEvent.MouseButton.Pre event) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        boolean consumedDumpMouseUp = event.getAction() == GLFW.GLFW_RELEASE
                && DUMP_PHYSICAL_INPUT.matchesMouseRelease(event.getButton());
        if (consumedDumpMouseUp) {
            event.setCanceled(true);
            finishDumpRelease(releaseOverUi(mc));
            clearDumpPhysicalInput();
            return;
        }
        if (event.getAction() == GLFW.GLFW_PRESS) ALT_TAP.otherPress();
        boolean pointerCanvas = mc.screen instanceof StoragePopupScreen popup;
        double guiX=mc.mouseHandler.xpos()*mc.getWindow().getGuiScaledWidth()/mc.getWindow().getScreenWidth();
        double guiY=mc.mouseHandler.ypos()*mc.getWindow().getGuiScaledHeight()/mc.getWindow().getScreenHeight();
        boolean overUi=pointerCanvas&&((StoragePopupScreen)mc.screen).pointerUiHit(guiX,guiY)
                || com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.hit(guiX,guiY);
        // Screen owns pointer-canvas input; its routed outside-window action handles Ctrl-pull.
        if (pointerCanvas) return;
        if (mc.screen == null && currentBody()!=null && event.getAction()==GLFW.GLFW_PRESS
                && equipmentPanelHit((int)guiX,(int)guiY,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight())
                && !com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.hit(guiX,guiY)
                && POPUPS.topmost(guiX,guiY)==null
                && com.juicyslew.moonstation14.ms14.medical.client.HealthAnalyzerWorkspace.INSTANCE.topmost(guiX,guiY)==null
                && (com.juicyslew.moonstation14.ms14.doors.ui.client.AirlockWireClient.active()==null
                    || !com.juicyslew.moonstation14.ms14.doors.ui.client.AirlockWireClient.active().contains(guiX,guiY))) {
            if (!toggleEquipmentPanel((int)guiX,(int)guiY,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight())) {
                var panel=equipmentPanelLayout(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
                if(panel!=null) for(var slot:panel.slots()) if(slot.rect().contains((int)guiX,(int)guiY)) {
                    if(event.getButton()==GLFW.GLFW_MOUSE_BUTTON_LEFT) equipmentSlotClick(slot.slotId());
                    event.setCanceled(true); return;
                }
            }
            event.setCanceled(true); return;
        }
        // The transparent Screen owns every pointer-canvas click (including empty-space dismissal).
        // Never let the global body action listener race it and issue a world action first.
        if (!BodyHandClientPolicy.routeGlobalMouseActions(pointerCanvas)) return;
        boolean routeCanvas=BodyHandClientPolicy.allowPointerWorldAction(pointerCanvas,overUi,constructionSearchFocused());
        if (overUi) return;
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getAction() == GLFW.GLFW_PRESS
                && controlDown() && !shiftDown() && !altDown() && mc.screen == null && mc.mouseHandler.isMouseGrabbed()
                && mc.player != null && (currentBody() != null || !mc.player.isSpectator())) {
            var ownedBody = currentBody();
            if (ownedBody != null) {
                // Owned bodies use the authenticated LiveHands lease/result path so reservation
                // state reaches the HUD immediately; the native request has no hand acknowledgement.
                reconcile();
                togglePull();
            } else {
                var nativeActor = mc.player;
                var posture = nativeActor.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.POSTURE.get());
                boolean pulling = posture != null && posture.data().pulling();
                UUID target = pulling ? null : lookPullTarget(nativeActor);
                if (pulling || target != null) PacketDistributor.sendToServer(
                        new com.juicyslew.moonstation14.ms14.loose_objects.pulling.NativeBodyPullRequest(target));
            }
            event.setCanceled(true);
            return;
        }
        if ((event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT && !AIM_RELEASE_DUMP.matchesMouse(event.getButton()))
                || event.getAction() != GLFW.GLFW_PRESS
                || (mc.screen != null && !routeCanvas) || (!mc.mouseHandler.isMouseGrabbed() && !routeCanvas) || currentBody() == null
                || GhostControlClient.committedCharacterEpoch() == 0) return;
        if (AimReleaseWorldDump.configuredBindingMatches(AIM_RELEASE_DUMP.matchesMouse(event.getButton()),
                PopupMovementIntent.modifiersMatch(modifierMask(AIM_RELEASE_DUMP), event.getModifiers()), false)) {
            UUID source = lookTarget(currentBody());
            var entity = looseEntity(source);
            var policy = entity == null || !(entity.getItem().getItem() instanceof com.juicyslew.moonstation14.ms14.reagent.IReagentTrait trait)
                    ? null : trait.drainableSolution(entity.getItem(), entity.level());
            long epoch = GhostControlClient.committedCharacterEpoch();
            var body = currentBody();
            var capture = DUMP_GESTURE.press(true, false, policy != null && policy.enabled(), source,
                    body == null ? null : body.getUUID(), epoch);
            if (AimReleaseWorldDump.consumesWorldPress(capture)) {
                DUMP_PHYSICAL_INPUT.beginMouse(event.getButton(), event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                        && AIM_RELEASE_DUMP.getKeyModifier() == net.neoforged.neoforge.client.settings.KeyModifier.ALT);
                beginDumpRequest(body, source);
                event.setCanceled(true);
            } else if (capture == AimReleaseWorldDump.Press.ROUTE_ORIGINAL
                    && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && altDown() && !shiftDown() && !controlDown()) {
                alternateWorldUse(); event.setCanceled(true);
            } else if (capture == AimReleaseWorldDump.Press.ROUTE_ORIGINAL
                    && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && shiftDown() && !controlDown() && !altDown()
                    && mc.screen == null && mc.mouseHandler.isMouseGrabbed()
                    && com.juicyslew.moonstation14.ms14.examine.client.ExamineClient.examineWorldClick()) event.setCanceled(true);
            return;
        }
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getAction() == GLFW.GLFW_PRESS) {
            double examineX=mc.mouseHandler.xpos()*mc.getWindow().getGuiScaledWidth()/mc.getWindow().getScreenWidth();
            double examineY=mc.mouseHandler.ypos()*mc.getWindow().getGuiScaledHeight()/mc.getWindow().getScreenHeight();
            if (mc.screen == null && !mc.mouseHandler.isMouseGrabbed()
                    && com.juicyslew.moonstation14.ms14.examine.client.ExamineClient.mouseClick(examineX,examineY,event.getButton())) {
                event.setCanceled(true); return;
            }
        }
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && event.getAction() == GLFW.GLFW_PRESS
                && shiftDown() && !controlDown() && !altDown() && mc.screen == null
                && mc.mouseHandler.isMouseGrabbed() && com.juicyslew.moonstation14.ms14.examine.client.ExamineClient.examineWorldClick()) {
            event.setCanceled(true); return;
        }
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT && altDown() && !shiftDown() && !controlDown()) {
            alternateWorldUse(); event.setCanceled(true); return;
        }
        if (controlDown() && !shiftDown() && !altDown()) {
            reconcile(); togglePull(); event.setCanceled(true); return;
        }
        if (POPUPS.pointerMode()) return;
        reconcile();
        if (POLICY.blockUseBarrier() && deferPlacementPress()) {
            event.setCanceled(true);
            return;
        }
        var view = POLICY.inventoryView();
        if (view == null) { event.setCanceled(true); return; }
        var active = view.first().id().equals(view.activeHand()) ? view.first() : view.second();
        if (cameraBody == null) return;
        var block = aimedWorldBlock();
        if (com.juicyslew.moonstation14.ms14.player_body_control.client.ContainedCharacterClientView.isContained()) event.setCanceled(true);
        var medicalKind = active.token() == null ? MedicalUseGesturePolicy.Kind.NONE : medicalKind(active.itemId());
        UUID bodyTarget = medicalKind == MedicalUseGesturePolicy.Kind.NONE || controlDown()
                ? null : aimedMedicalBody(cameraBody, medicalKind == MedicalUseGesturePolicy.Kind.HEALTH_ANALYZER
                        ? resolvedAnalyzerRange(active.itemId()) : 2.0);
        boolean medicalTarget = MedicalUseGesturePolicy.primaryBodyUse(medicalKind, bodyTarget != null,
                controlDown(), POPUPS.pointerMode());
        UUID target = medicalTarget ? null : lookTarget(cameraBody);
        var route = PrimaryWorldGesture.route(true,
                block != null && block.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK, active.token() != null,
                target != null, medicalTarget);
        if (route == PrimaryWorldGesture.Route.BLOCK_USE) {
            requestBlockUse(BodyBlockUseRequest.Intent.USE);
        } else {
            var action = PrimaryWorldGesture.action(route);
            if (action == BodyHandActionRequest.Action.PICKUP && target != null)
                action = BodyHandActionRequest.Action.PICKUP_SELECTED;
            if (action == BodyHandActionRequest.Action.USE_BODY) request(action, bodyTarget);
            else if (action != null) request(action);
        }
        event.setCanceled(true);
    }

    public static void mouseScroll(net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent event) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        double x=mc.mouseHandler.xpos()*mc.getWindow().getGuiScaledWidth()/mc.getWindow().getScreenWidth();
        double y=mc.mouseHandler.ypos()*mc.getWindow().getGuiScaledHeight()/mc.getWindow().getScreenHeight();
        if (!shiftDown() && !controlDown() && !altDown() && mc.screen == null
                && com.juicyslew.moonstation14.ms14.examine.client.ExamineClient.mouseScroll(x,y,event.getScrollDeltaY())) {
            event.setCanceled(true);
            return;
        }
        if (!shiftDown() || controlDown() || altDown() || mc.screen != null
                || equipmentPanelHit((int)x,(int)y,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight())
                || POPUPS.topmost(x,y)!=null
                || com.juicyslew.moonstation14.ms14.medical.client.HealthAnalyzerWorkspace.INSTANCE.topmost(x,y)!=null
                || com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.hit(x,y)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_G)) return;
        if (event.getScrollDeltaY()!=0 && com.juicyslew.moonstation14.ms14.examine.client.ExamineClient.cycle(event.getScrollDeltaY()>0?1:-1)) event.setCanceled(true);
    }

    /** Route an outside-window left click through the committed body's existing typed use actions. */
    public static void gameplayWindowMouseUse(int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || currentBody() == null
                || GhostControlClient.committedCharacterEpoch() == 0) return;
        reconcile();
        if (altDown() && !shiftDown() && !controlDown()) { alternateWorldUse(); return; }
        if (controlDown() && !shiftDown() && !altDown()) { togglePull(); return; }
        var view = POLICY.inventoryView();
        if (view == null) return;
        var active = view.first().id().equals(view.activeHand()) ? view.first() : view.second();
        UUID target = lookTarget(cameraBody);
        var start = cameraBody.getEyePosition();
        var end = start.add(cameraBody.getLookAngle().scale(BodyBlockUseTargetProbe.MAX_REACH));
        var block = cameraBody.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, cameraBody));
        var route = PrimaryWorldGesture.route(true, block.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK,
                active.token() != null, target != null);
        if (route == PrimaryWorldGesture.Route.BLOCK_USE) requestBlockUse(BodyBlockUseRequest.Intent.USE);
        else {
            var action = PrimaryWorldGesture.action(route);
            if (action == BodyHandActionRequest.Action.PICKUP && target != null)
                action = BodyHandActionRequest.Action.PICKUP_SELECTED;
            if (action != null) request(action);
        }
    }

    /** Applies ordinary mouse-look through the committed body while a non-modal gameplay window is open. */
    public static void gameplayWindowLook(double mouseX, double mouseY) {
        var body = currentBody();
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (body == null || mc.player == null || GhostControlClient.committedCharacterEpoch() == 0
                || !Double.isFinite(mouseX) || !Double.isFinite(mouseY)) return;
        double sensitivity = mc.options.sensitivity().get() * 0.6 + 0.2;
        double scale = sensitivity * sensitivity * sensitivity * 8.0 * 0.15;
        double yaw = mouseX * scale;
        double pitch = mouseY * scale * (mc.options.invertYMouse().get() ? -1.0 : 1.0);
        mc.player.turn(yaw, pitch);
        GhostControlClient.projectOwnedGhostLookForRenderFrame();
    }

    private static void togglePull() {
        var snapshot = POLICY.snapshot();
        if (snapshot == null) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null || player.isSpectator() || GhostControlClient.ownedCharacterForHud() != null) return;
            var posture = player.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.POSTURE.get());
            if (posture != null && posture.data().pulling()) return;
            UUID target = lookPullTarget(player);
            if (target != null)
                PacketDistributor.sendToServer(new com.juicyslew.moonstation14.ms14.loose_objects.pulling.NativeBodyPullRequest(target));
            return;
        }
        var hand = snapshot.hands().stream().filter(h -> h.id().equals(snapshot.activeHand())).findFirst().orElse(null);
        if (hand == null) return;
        if (snapshot.hands().stream().anyMatch(h -> h.pullTarget() != null)) request(BodyHandActionRequest.Action.STOP_PULL);
        else requestPull();
    }

    private static boolean requestPull() {
        BodyHandStateSnapshot snapshot = POLICY.snapshot();
        if (snapshot == null) return false;
        BodyHandStateSnapshot.Hand hand = BodyPullHandSelector.select(snapshot.hands(), snapshot.activeHand());
        if (hand == null) return false;
        UUID target = lookPullTarget(cameraBody);
        if (target == null) { POLICY.noItemUnderCursor(clock); return false; }
        long sequence = POLICY.action(clock);
        if (sequence == 0) return false;
        pullActionSequence = sequence;
        PacketDistributor.sendToServer(new BodyHandActionRequest(BodyHandActionRequest.Action.TOGGLE_PULL,
                hand.id(), snapshot.epoch(), sequence, snapshot.revision(), target, null));
        return true;
    }

    private static boolean smartStorage(boolean belt) {
        reconcile();
        if (cameraBody != null && com.juicyslew.moonstation14.ms14.character.CharacterControlSystem
                .isClientActionBlocked(cameraBody)) return false;
        var view = POLICY.inventoryView();
        var snapshot = POLICY.snapshot();
        var plan = SmartStorageGesture.plan(belt, view, snapshot);
        if (plan == null) return false;
        if (plan.action() != BodyHandActionRequest.Action.EQUIP_BELT
                && plan.action() != BodyHandActionRequest.Action.EQUIP_BACK && !ItemFootprints.clientCatalogIsLoaded()) return false;
        long sequence = POLICY.action(clock);
        if (sequence == 0) return false;
        PacketDistributor.sendToServer(new BodyHandActionRequest(plan.action(), plan.handId(),
                snapshot.epoch(), sequence, snapshot.revision(), null, plan.hostOrHandToken(),
                plan.childOrSourceToken(), -1, -1, -1));
        return true;
    }

    static void selectHand() {
        if (cameraBody != null && com.juicyslew.moonstation14.ms14.character.CharacterControlSystem
                .isClientActionBlocked(cameraBody)) return;
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

    /** Settled authenticated owner proof; deliberately exposes no mutable inventory authority. */
    public record OwnerProof(Mob body, long epoch, long revision) { }
    public static OwnerProof ownerProof() {
        reconcile(); var proof=POLICY.snapshot();
        return cameraBody==null||proof==null||proof.reason()!=BodyHandStateSnapshot.Reason.OK
                ||!cameraBody.getUUID().equals(proof.bodyId())||proof.epoch()!=GhostControlClient.committedCharacterEpoch()
                ?null:new OwnerProof(cameraBody,proof.epoch(),proof.revision());
    }
    public static Mob currentControlledBody() { reconcile(); return cameraBody; }

    private static boolean deferPlacementPress() {
        if (com.juicyslew.moonstation14.ms14.player_body_control.client.ContainedCharacterClientView.isContained()) return false;
        if (deferredPlacement.pending()) return true;
        var proof = placementProof;
        var plan = proof == null ? null : constructionUsePlan(proof.itemId());
        if (proof == null || plan == null || !proof.body().equals(plan.body())
                || proof.epoch() != plan.epoch() || proof.planRevision() != plan.revision()
                || !proof.prototype().equals(plan.prototype()) || proof.rotation() != plan.rotation()
                || cameraBody == null || !BodyHandClientPolicy.mayDeferConstructionUse(POLICY.blockUseBarrier(), true,
                proof.itemId(), proof.requiredCount(), proof.itemId(), proof.heldCount())) return false;
        var hit = aimedBlock();
        if (hit == null || cameraBody.level().getBlockState(hit.getBlockPos()).is(com.juicyslew.moonstation14.block.ModBlocks.DISPOSAL_UNIT.get())) return false;
        return deferredPlacement.capture(new DeferredConstructionUse.Intent(proof.body(), proof.epoch(), proof.handsRevision(),
                proof.hand(), proof.token(), proof.itemId(), proof.requiredCount(), proof.planRevision(), proof.prototype(),
                proof.rotation(), hit.getBlockPos().immutable(), hit.getDirection()));
    }

    private static net.minecraft.world.phys.BlockHitResult aimedBlock() {
        if (cameraBody == null) return null;
        if (com.juicyslew.moonstation14.ms14.player_body_control.client.ContainedCharacterClientView.isContained()) return null;
        var start = cameraBody.getEyePosition();
        var hit = cameraBody.level().clip(new ClipContext(start, start.add(cameraBody.getLookAngle().scale(6.0)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, cameraBody));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? hit : null;
    }

    private static void replayDeferredPlacement() {
        var queued = deferredPlacement.intent();
        if (queued == null) return;
        if (com.juicyslew.moonstation14.ms14.player_body_control.client.ContainedCharacterClientView.isContained()) return;
        var proofItem = queued.item();
        var plan = constructionUsePlan(proofItem);
        var view = POLICY.inventoryView();
        if (view == null || plan == null || cameraBody == null) return;
        var active = view.first().id().equals(view.activeHand()) ? view.first() : view.second();
        var freshSnapshot = POLICY.snapshot();
        if (freshSnapshot == null) return;
        var hit = aimedBlock();
        var valid = deferredPlacement.takeForReplay(new DeferredConstructionUse.Current(cameraBody.getUUID(),
                GhostControlClient.committedCharacterEpoch(), freshSnapshot.revision(), active.id(), active.token(),
                active.itemId(), active.count(), plan.revision(), plan.prototype(), plan.rotation(),
                hit == null ? null : hit.getBlockPos(), hit == null ? null : hit.getDirection(),
                plan.body().equals(queued.body()) && plan.epoch() == queued.epoch(),
                net.minecraft.client.Minecraft.getInstance().screen == null));
        if (valid == null) return;
        requestBlockUse(BodyBlockUseRequest.Intent.USE);
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

    /** True when a current body/epoch snapshot exists, including authoritative empty hands. */
    public static boolean hasConfirmedHandsFor(UUID bodyId, long epoch) {
        reconcile();
        if (bodyId == null || cameraBody == null || !bodyId.equals(cameraBody.getUUID())
                || epoch <= 0 || epoch != cameraEpoch || epoch != GhostControlClient.committedCharacterEpoch()) return false;
        var snapshot = POLICY.snapshot();
        return snapshot != null && snapshot.epoch() == epoch && bodyId.equals(snapshot.bodyId());
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

    /** Read-only latest confirmed body-hand proof for authenticated tool workspaces. */
    public static BodyHandStateSnapshot networkConfiguratorProof() {
        reconcile();
        return cameraBody == null ? null : POLICY.confirmedSnapshot();
    }

    /** Browser craft intent uses the same private current-body proof as other hand actions. */
    public static void requestConstructionCraft(String recipe, int quantity, boolean cancel) {
        var proof = inventoryProof();
        if (proof == null) return;
        long sequence = CRAFT_SEQUENCE.incrementAndGet();
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.juicyslew.moonstation14.ms14.construction.network.ConstructionCraftRequest(
                        proof.epoch(), sequence, proof.revision(), recipe, cancel ? 0 : quantity, cancel));
    }
    public static boolean constructionStateMatches(long epoch, java.util.UUID body) {
        var proof=inventoryProof(); return proof!=null&&proof.epoch()==epoch&&proof.bodyId().equals(body);
    }
    private static final java.util.concurrent.atomic.AtomicLong CRAFT_SEQUENCE = new java.util.concurrent.atomic.AtomicLong();

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

    public static boolean matches(net.minecraft.client.KeyMapping mapping, int key, int scan, int modifiers) {
        int effective = modifiers;
        if (key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT) effective &= ~GLFW.GLFW_MOD_ALT;
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT) effective &= ~GLFW.GLFW_MOD_SHIFT;
        return mapping.matches(key, scan) && PopupMovementIntent.modifiersMatch(modifierMask(mapping), effective);
    }

    /** Screen keyboard routing calls the same snapshot/sequence guarded actions as the world bindings. */
    public static boolean popupAction(int key, int scan, int modifiers, boolean firstPress) {
        if (key == GLFW.GLFW_KEY_ESCAPE) return false;
        // Alt tap is handled centrally on release; never turn its press into an
        // immediate pointer action from either the host event route or screen key path.
        if (inventoryUsesAlt() && INVENTORY.matches(key, scan)) return true;
        boolean use = matches(USE_IN_HAND, key, scan, modifiers);
        boolean prime = matches(PRIME_GRENADE, key, scan, modifiers);
        boolean throwItem = matches(THROW_GRENADE, key, scan, modifiers);
        boolean worldUse = matches(WORLD_STORAGE, key, scan, modifiers);
        boolean alternateUse = matches(ALTERNATE_WORLD_USE, key, scan, modifiers);
        boolean pull = matches(TOGGLE_PULL, key, scan, modifiers);
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
        if (use || prime || throwItem || worldUse || alternateUse || pull) {
            if (firstPress) {
                if (prime) request(BodyHandActionRequest.Action.PRIME_GRENADE);
                else if (use) popupUseInHand(key, scan, modifiers);
                else if (throwItem) request(BodyHandActionRequest.Action.THROW_ITEM);
                else if (worldUse) ordinaryWorldUse();
                else if (alternateUse) alternateWorldUse();
                else togglePull();
            }
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

    public static boolean hasCommittedBody() {
        return currentBody() != null && GhostControlClient.committedCharacterEpoch() > 0;
    }

    static void pointerMode(boolean enabled) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (enabled && mc.screen == null && cameraBody != null) {
            POPUPS.pointerMode(true);
            mc.setScreen(new StoragePopupScreen(POPUPS));
        } else if (!enabled && mc.screen instanceof StoragePopupScreen) {
            com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.clearTextFocus();
            POPUPS.pointerMode(false);
            mc.setScreen(null);
        }
    }

    /** Routes the existing Z gesture through the unlocked popup screen exactly once. */
    static boolean popupUseInHand(int key,int scan,int modifiers) {
        if(!USE_IN_HAND.matches(key,scan) || (modifiers & (GLFW.GLFW_MOD_CONTROL|GLFW.GLFW_MOD_SUPER)) != 0) return false;
        if ((modifiers & GLFW.GLFW_MOD_ALT) == 0)
            return HeldUseRequestDispatch.routeActivation(BodyHandClient::quickEquipInHand,
                    BodyHandClient::toggleActiveHeldStorage, () -> ordinaryPopupHeldUse(modifiers));
        var view=POLICY.inventoryView(); if(view==null) return true;
        var active=view.first().id().equals(view.activeHand())?view.first():view.second();
        var kind=medicalKind(active.itemId());
        if(kind==MedicalUseGesturePolicy.Kind.SYRINGE) request(BodyHandActionRequest.Action.CYCLE_SYRINGE_AMOUNT);
        return true;
    }

    private static boolean ordinaryPopupHeldUse(int modifiers) {
        var view=POLICY.inventoryView(); if(view==null) return true;
        var active=view.first().id().equals(view.activeHand())?view.first():view.second();
        var kind=medicalKind(active.itemId());
        var inHand=MedicalUseGesturePolicy.inHand(kind,(modifiers&GLFW.GLFW_MOD_SHIFT)!=0,true);
        if(inHand==MedicalUseGesturePolicy.InHand.SYRINGE_SELF_USE || inHand==MedicalUseGesturePolicy.InHand.SELF_SCAN)
            request(BodyHandActionRequest.Action.USE_BODY,cameraBody==null?null:cameraBody.getUUID());
        else request(BodyHandActionRequest.Action.USE_IN_HAND);
        return true;
    }

    private static boolean toggleActiveHeldStorage() {
        var view=POLICY.inventoryView();
        if(view==null) return false;
        var active=view.first().id().equals(view.activeHand())?view.first():view.second();
        if(active.token()==null || active.count()!=1 || active.pullTarget()!=null
                || POLICY.heldGrid(active.token(),true)==null) return false;
        toggleHeldPopup(active.token());
        return true;
    }

    /** Shares the existing storage pointer layer; analyzer visibility is independent of capture. */
    public static void medicalPointerMode() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen == null && !POPUPS.pointerMode()) { POINTER.popupOpen(); pointerMode(true); }
    }

    private static void dumpEscapeScreenKey(net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre event) {
        if (!suppressDumpEscapeScreenKey || event.getKeyCode() != GLFW.GLFW_KEY_ESCAPE) return;
        suppressDumpEscapeScreenKey = false;
        event.setCanceled(true);
    }

    private static ItemEntity looseEntity(UUID id) {
        Mob body = currentBody();
        if (body == null || id == null) return null;
        return body.level().getEntitiesOfClass(ItemEntity.class, body.getBoundingBox().inflate(6), e -> e.getUUID().equals(id))
                .stream().findFirst().orElse(null);
    }

    private static net.minecraft.world.phys.BlockHitResult dumpReleaseTarget(Mob body) {
        if (body == null || body.level() != net.minecraft.client.Minecraft.getInstance().level
                || !body.level().dimension().location().equals(dumpDimension)) return null;
        Vec3 start = body.getEyePosition();
        var hit = body.level().clip(new ClipContext(start, start.add(body.getLookAngle().scale(6)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, body));
        return hit instanceof net.minecraft.world.phys.BlockHitResult block && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                ? block : null;
    }

    private static void sendDump(ChemMasterDumpRequest.Phase phase, net.minecraft.core.BlockPos target) {
        if (dumpBody == null || dumpDimension == null || dumpSource == null || dumpGestureId == null) {
            DUMP_LOGGER.debug("ChemMaster dump client phase={} send=skipped reason=context_missing", phase); return;
        }
        long epoch = GhostControlClient.committedCharacterEpoch();
        if (epoch <= 0 || currentBody() == null || !dumpBody.equals(currentBody().getUUID())) {
            DUMP_LOGGER.debug("ChemMaster dump client phase={} gesture={} send=skipped reason=body_context_lost", phase, dumpToken(dumpGestureId)); return;
        }
        if (dumpSequence == Long.MAX_VALUE) {
            DUMP_LOGGER.debug("ChemMaster dump client phase={} gesture={} send=skipped reason=sequence_exhausted", phase, dumpToken(dumpGestureId)); return;
        }
        long sequence = ++dumpSequence;
        DUMP_LOGGER.debug("ChemMaster dump client phase={} gesture={} seq={} event=SEND", phase, dumpToken(dumpGestureId), sequence);
        PacketDistributor.sendToServer(new com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction.ChemMasterDumpRequest(dumpGestureId, phase,
                epoch, sequence, dumpSource, dumpDimension,
                phase == ChemMasterDumpRequest.Phase.RELEASE ? java.util.Optional.of(target) : java.util.Optional.empty()));
    }

    private static void sendDumpCancel() {
        boolean shouldSend = DUMP_GESTURE.active() || DUMP_FEEDBACK.pending();
        if (dumpGestureId != null) {
            DUMP_LOGGER.debug("ChemMaster dump client phase=CANCEL gesture={} seq={} event=LOCAL_CANCEL reason=input_cancel", dumpToken(dumpGestureId), dumpSequence);
            DUMP_FEEDBACK.cancelQueued(dumpGestureId);
        }
        queuedDumpTarget = null;
        if (shouldSend) sendDump(ChemMasterDumpRequest.Phase.CANCEL, null);
    }

    private static void beginDumpRequest(Mob body, UUID source) {
        if (body == null || source == null) return;
        dumpBody = body.getUUID(); dumpSource = source; dumpGestureId = DUMP_GESTURE.gestureId();
        dumpDimension = body.level().dimension().location();
        queuedDumpTarget = null;
        DUMP_FEEDBACK.begin(dumpGestureId, dumpSource, dumpBody, GhostControlClient.committedCharacterEpoch());
        sendDump(ChemMasterDumpRequest.Phase.BEGIN, null);
    }

    private static void clearDumpPhysicalInput() {
        DUMP_PHYSICAL_INPUT.clear();
    }

    private static boolean releaseOverUi(net.minecraft.client.Minecraft mc) {
        double x = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double y = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        return (mc.screen != null && !(mc.screen instanceof com.juicyslew.moonstation14.ms14.ui.client.GameplayWindow))
                || (mc.screen instanceof StoragePopupScreen popup && popup.pointerUiHit(x, y))
                || com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.hit(x, y);
    }

    private static void finishDumpRelease(boolean overUi) {
        DUMP_GESTURE.consumeMouseUp();
        if (!DUMP_GESTURE.active()) return;
        var body = currentBody();
        var target = overUi ? null : dumpReleaseTarget(body);
        UUID aimedLoose = lookTarget(body);
        boolean aimedMachine = target != null && body != null
                && body.level().getBlockEntity(target.getBlockPos()) instanceof com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlockEntity;
        UUID targetIdentity = overUi ? null : aimedMachine
                ? UUID.nameUUIDFromBytes((dumpDimension + ":" + target.getBlockPos().asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                : aimedLoose != null ? aimedLoose : target == null ? DUMP_GESTURE.source()
                : UUID.nameUUIDFromBytes((dumpDimension + ":" + target.getBlockPos().asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        DUMP_GESTURE.aim(targetIdentity);
        boolean machine = target != null && body != null
                && body.level().getBlockEntity(target.getBlockPos()) instanceof com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlockEntity be
                && be.prototype() != null && be.prototype().dumpableSolution().enabled();
        ItemEntity captured = looseEntity(DUMP_GESTURE.source());
        UUID capturedSourceId = captured == null ? null : captured.getUUID();
        var decision = DUMP_GESTURE.release(targetIdentity, machine, aimedLoose, capturedSourceId,
                body == null ? null : body.getUUID(), GhostControlClient.committedCharacterEpoch());
        if (decision == AimReleaseWorldDump.Release.DUMP) {
            if (DUMP_FEEDBACK.beginAccepted()) sendDump(ChemMasterDumpRequest.Phase.RELEASE, target.getBlockPos());
            else if (DUMP_FEEDBACK.queueRelease(dumpSource, dumpBody, GhostControlClient.committedCharacterEpoch())) {
                queuedDumpTarget = target.getBlockPos();
                DUMP_LOGGER.debug("ChemMaster dump client phase=RELEASE gesture={} seq={} event=QUEUED reason=awaiting_begin_ack", dumpToken(dumpGestureId), dumpSequence);
            } else DUMP_LOGGER.debug("ChemMaster dump client phase=RELEASE gesture={} seq={} event=NOT_QUEUED reason=begin_not_accepted_or_context_changed", dumpToken(dumpGestureId), dumpSequence);
        }
        else if (decision == AimReleaseWorldDump.Release.ORIGINAL_ALTERNATE_USE) {
            DUMP_LOGGER.debug("ChemMaster dump client phase=CANCEL gesture={} seq={} event=LOCAL_CANCEL reason=short_examine", dumpToken(dumpGestureId), dumpSequence);
            com.juicyslew.moonstation14.ms14.examine.client.ExamineClient.examineEntityClick(dumpSource);
            sendDump(ChemMasterDumpRequest.Phase.CANCEL, null);
        } else {
            DUMP_LOGGER.debug("ChemMaster dump client phase=CANCEL gesture={} seq={} event=LOCAL_CANCEL reason=release_rejected decision={}", dumpToken(dumpGestureId), dumpSequence, decision);
            sendDump(ChemMasterDumpRequest.Phase.CANCEL, null);
        }
    }

    public static void dumpResult(com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction.ChemMasterDumpResult result) {
        if (result != null) DUMP_LOGGER.debug("ChemMaster dump client phase={} gesture={} event=ACK_RECEIVED accepted={} reason={}",
                result.phase(), dumpToken(result.gestureId()), result.accepted(), result.reason().map(Component::getString).orElse("none"));
        if (!DUMP_FEEDBACK.accept(result)) return;
        if (!result.accepted() && result.reason().isPresent()) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) mc.player.displayClientMessage(result.reason().get(), true);
        }
        if (result.phase() == ChemMasterDumpRequest.Phase.BEGIN && result.accepted() && DUMP_FEEDBACK.hasQueuedRelease()) {
            boolean contextValid = DUMP_FEEDBACK.ownsContext(dumpGestureId, dumpSource, dumpBody,
                    GhostControlClient.committedCharacterEpoch()) && looseEntity(dumpSource) != null;
            if (contextValid && DUMP_FEEDBACK.takeQueuedRelease() && queuedDumpTarget != null) {
                var target = queuedDumpTarget;
                queuedDumpTarget = null;
                DUMP_LOGGER.debug("ChemMaster dump client phase=RELEASE gesture={} seq={} event=QUEUED_FLUSH reason=begin_ack_accepted", dumpToken(dumpGestureId), dumpSequence);
                sendDump(ChemMasterDumpRequest.Phase.RELEASE, target);
            } else {
                DUMP_LOGGER.debug("ChemMaster dump client phase=CANCEL gesture={} seq={} event=QUEUED_CLEAR reason={}", dumpToken(result.gestureId()), dumpSequence,
                        contextValid ? "queued_state_invalid" : "body_or_source_context_lost");
                DUMP_FEEDBACK.cancelQueued(result.gestureId());
                queuedDumpTarget = null;
            }
        } else if (result.phase() == ChemMasterDumpRequest.Phase.BEGIN && !result.accepted()) {
            queuedDumpTarget = null;
        }
    }

    private static String dumpToken(UUID gesture) { return gesture.toString().substring(0, 8); }

    /** Server-opened machine windows join, but never replace, the current managed pointer workspace. */
    public static void openChemMasterPointerWorkspace() {
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(cameraBody==null)reconcile();
        if(cameraBody==null)return;
        if(!POPUPS.pointerMode()){POINTER.popupOpen();POPUPS.pointerMode(true);}
        if(mc.screen==null)mc.setScreen(new StoragePopupScreen(POPUPS));
    }

    /** Join the existing pointer workspace without replacing any unrelated screen. */
    public static void openConstructionPointerWorkspace() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen != null && !(mc.screen instanceof StoragePopupScreen)) return;
        if (cameraBody == null) reconcile();
        if (cameraBody != null && !POPUPS.pointerMode()) { POINTER.manualOpen(); pointerMode(true); }
    }
    public static void openSharedPointerWorkspace() { openConstructionPointerWorkspace(); }
    public static boolean constructionSearchFocused() {
        return com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.textFocused();
    }
    public static void pointerOverlayClosed() { closePointerIfEmpty(); }
    /** Give the shared storage workspace the pointer while preserving the wire window model. */
    public static void openWireWorkspace() {
        reconcile();
        if (cameraBody == null) return;
        POINTER.popupOpen();
        POPUPS.pointerMode(true);
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (!(mc.screen instanceof StoragePopupScreen)) mc.setScreen(new StoragePopupScreen(POPUPS));
    }

    public static void wireClosed() { closePointerIfEmpty(); }

    private static void dismissPointerMode() {
        POINTER.dismiss();
        pointerMode(false);
    }

    public static void dismissPopupPointerMode() { dismissPointerMode(); }

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
        boolean analyzerOpen = com.juicyslew.moonstation14.ms14.medical.client.HealthAnalyzerWorkspace.INSTANCE
                .windows().stream().anyMatch(w -> w.visible());
        if (POPUPS.windows().stream().noneMatch(StoragePopupWorkspace.Window::visible)
                && !analyzerOpen
                && !com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.anyVisible()
                && !com.juicyslew.moonstation14.ms14.devicenet.item.client.NetworkConfiguratorWindows.anyVisible()) {
            if (POINTER.closeWhenEmpty()) pointerMode(false);
        }
        boolean wireOpen = com.juicyslew.moonstation14.ms14.doors.ui.client.AirlockWireClient.active() != null;
        boolean storageOpen = POPUPS.windows().stream().anyMatch(StoragePopupWorkspace.Window::visible) || analyzerOpen;
        if (!PointerModePolicy.keepOpenForWindow(wireOpen, storageOpen) && POINTER.closeWhenEmpty()) pointerMode(false);
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
        if (com.juicyslew.moonstation14.ms14.player_body_control.client.ContainedCharacterClientView.isContained()) return false;
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
        if (!ItemFootprints.clientCatalogIsLoaded()) return;
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
                    com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints.clientForId(id),
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
        if (!ItemFootprints.clientCatalogIsLoaded()) return false;
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
        if (!ItemFootprints.clientCatalogIsLoaded()) return false;
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
        if (!ItemFootprints.clientCatalogIsLoaded()) return;
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
        if (!ItemFootprints.clientCatalogIsLoaded()) return;
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
                com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints.clientForId(id),
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
        if (!ItemFootprints.clientCatalogIsLoaded()) return false;
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
        if (!ItemFootprints.clientCatalogIsLoaded()) return;
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
                com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints.clientForId(item), anchor, rotation, true).maySendAction()) return;
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
        if (!ItemFootprints.clientCatalogIsLoaded()) return;
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
        if (!ItemFootprints.clientCatalogIsLoaded()) return;
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
        if (!ItemFootprints.clientCatalogIsLoaded()) return;
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


    private static boolean request(BodyHandActionRequest.Action action) { return request(action, null); }

    private static boolean request(BodyHandActionRequest.Action action, UUID nominatedBody) {
        if (cameraBody != null && com.juicyslew.moonstation14.ms14.character.CharacterControlSystem
                .isClientActionBlocked(cameraBody)) return false;
        BodyHandStateSnapshot snapshot = POLICY.snapshot();
        if (snapshot == null) return false;
        BodyHandStateSnapshot.Hand active = snapshot.hands().stream()
                .filter(hand -> hand.id().equals(snapshot.activeHand())).findFirst().orElse(null);
        if (active == null) return false;
        UUID target = null;
        String expectedToken = active.token();
        switch (action) {
            case PICKUP, PICKUP_SELECTED -> {
                if (active.token() != null || !BodyHandClientPolicy.handAvailableForItemAction(active)) return false;
                target = lookTarget(cameraBody);
                expectedToken = null;
            }
            case USE_TARGET -> {
                if (active.token() == null || !BodyHandClientPolicy.handAvailableForItemAction(active)) return false;
                target = lookTarget(cameraBody);
            }
            case ACTIVATE_TARGET -> {
                if (active.token() != null || !BodyHandClientPolicy.handAvailableForItemAction(active)) return false;
                target = lookTarget(cameraBody);
                expectedToken = null;
            }
            case USE_BODY -> {
                if (active.token() == null || !BodyHandClientPolicy.handAvailableForItemAction(active)
                        || nominatedBody == null) return false;
                target = nominatedBody;
            }
            case ALTERNATE_TARGET -> {
                if (!BodyHandClientPolicy.handAvailableForItemAction(active)) return false;
                target = lookTarget(cameraBody);
            }
            case TOGGLE_PULL -> {
                target = active.pullTarget() != null ? UUID.fromString(active.pullTarget()) : lookPullTarget(cameraBody);
                if (target == null) { POLICY.noItemUnderCursor(clock); return false; }
            }
            case STOP_PULL -> { }
            case DROP, THROW_GRENADE, THROW_ITEM, USE_IN_HAND, USE_HELD, PRIME_GRENADE, CYCLE_SYRINGE_AMOUNT -> {
                if (active.token() == null) return false;
                if (action == BodyHandActionRequest.Action.USE_IN_HAND
                        && !BodyHandClientPolicy.handAvailableForItemAction(active)) return false;
            }
            default -> { return false; }
        }
        if ((action == BodyHandActionRequest.Action.PICKUP || action == BodyHandActionRequest.Action.PICKUP_SELECTED
                || action == BodyHandActionRequest.Action.USE_TARGET || action == BodyHandActionRequest.Action.ALTERNATE_TARGET
                || action == BodyHandActionRequest.Action.USE_BODY || action == BodyHandActionRequest.Action.ACTIVATE_TARGET)
                && target == null) { POLICY.noItemUnderCursor(clock); return false; }
        if (action == BodyHandActionRequest.Action.USE_IN_HAND) {
            int duration = predictionDuration(action, active, target);
            long heldSequence = HeldUseRequestDispatch.dispatch(POLICY, snapshot, active, clock, duration,
                    PacketDistributor::sendToServer, sequence -> {
                        if (cameraBody != null) com.juicyslew.moonstation14.ms14.timed_interaction.client.TimedInteractionClient
                                .predictStart(sequence, cameraBody.getUUID(), duration);
                    });
            return heldSequence != 0;
        }
        long sequence = POLICY.action(clock);
        if (sequence == 0) return false;
        var ingestionDuration = predictionDuration(action, active, target);
        if (ingestionDuration > 0 && cameraBody != null)
            com.juicyslew.moonstation14.ms14.timed_interaction.client.TimedInteractionClient.predictStart(sequence,cameraBody.getUUID(),ingestionDuration);
        if (action == BodyHandActionRequest.Action.STOP_PULL) pullActionSequence = sequence;
        PacketDistributor.sendToServer(new BodyHandActionRequest(action, active.id(), snapshot.epoch(),
                sequence, snapshot.revision(), target,
                expectedToken));
        if (action == BodyHandActionRequest.Action.PRIME_GRENADE)
            POLICY.markUse(sequence);
        return true;
    }

    private static int predictionDuration(BodyHandActionRequest.Action action, BodyHandStateSnapshot.Hand active, UUID target) {
        net.minecraft.resources.ResourceLocation itemId=null;
        if(action==BodyHandActionRequest.Action.USE_IN_HAND && active.itemId()!=null)
            itemId=net.minecraft.resources.ResourceLocation.tryParse(active.itemId());
        else if(action==BodyHandActionRequest.Action.ALTERNATE_TARGET && active.token()==null && target!=null && cameraBody!=null) {
            var item=cameraBody.level().getEntitiesOfClass(ItemEntity.class,cameraBody.getBoundingBox().inflate(6),e->e.getUUID().equals(target)).stream().findFirst().orElse(null);
            if(item!=null) itemId=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem().getItem());
        }
        var profile=itemId==null?null:com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.clientLookup(itemId);
        return profile==null?0:profile.delayTicks();
    }

    /** Only a nomination: server ray, reach, block occlusion, and item identity are authoritative. */
    private static UUID lookTarget(Mob body) {
        if (com.juicyslew.moonstation14.ms14.player_body_control.client.ContainedCharacterClientView.isContained()) return null;
        if (body == null) return null;
        refreshPile(body);
        return PILE.update(pileItems.stream().map(ItemEntity::getUUID).toList());
    }

    /** Resolve the UUID only from the existing bounded client pile nomination. */
    private static com.juicyslew.moonstation14.entities.LooseItemEntity findAimedLoose(UUID target) {
        if (target == null || cameraBody == null) return null;
        refreshPile(cameraBody);
        return pileItems.stream().filter(item -> target.equals(item.getUUID()))
                .filter(com.juicyslew.moonstation14.entities.LooseItemEntity.class::isInstance)
                .map(com.juicyslew.moonstation14.entities.LooseItemEntity.class::cast)
                .findFirst().orElse(null);
    }
    public static void analyzerWindowClosed() {
        if (POPUPS.windows().stream().anyMatch(StoragePopupWorkspace.Window::visible)
                || com.juicyslew.moonstation14.ms14.medical.client.HealthAnalyzerWorkspace.INSTANCE.windows().stream().anyMatch(w -> w.visible())) return;
        if (!com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client.ChemMasterWorkspace.INSTANCE.windows().isEmpty()) return;
        var mc=net.minecraft.client.Minecraft.getInstance();
        if (mc == null) return;
        boolean ownsPointer=mc.screen instanceof StoragePopupScreen;
        if(POINTER.lastAnalyzerClosed(false,ownsPointer)) pointerMode(false);
    }

    private static MedicalUseGesturePolicy.Kind medicalKind(String itemId) {
        if (cameraBody == null || itemId == null) return MedicalUseGesturePolicy.Kind.NONE;
        var id = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (id == null) return MedicalUseGesturePolicy.Kind.NONE;
        try {
            var data = com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.require(
                    com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.catalog(cameraBody.level()), id);
            if (data.healthAnalyzer().isPresent()) return MedicalUseGesturePolicy.Kind.HEALTH_ANALYZER;
            if (data.healing().isPresent()) return MedicalUseGesturePolicy.Kind.HEALING;
            if (data.syringePolicy().isPresent()) return MedicalUseGesturePolicy.Kind.SYRINGE;
        } catch (RuntimeException missingOrUnresolved) { }
        return MedicalUseGesturePolicy.Kind.NONE;
    }

    private static double resolvedAnalyzerRange(String itemId) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (cameraBody == null || id == null) return 0;
        try {
            return com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.require(
                    com.juicyslew.moonstation14.ms14.medical.ModMedicalItems.catalog(cameraBody.level()), id)
                    .healthAnalyzer().orElseThrow().scanRange();
        } catch (RuntimeException unresolved) { return 0; }
    }

    /** Medical targeting has its own living-body ray; it never borrows the loose-item selector. */
    private static UUID aimedMedicalBody(Mob body, double reach) {
        if (body == null || body.level() != net.minecraft.client.Minecraft.getInstance().level
                || com.juicyslew.moonstation14.ms14.player_body_control.client.ContainedCharacterClientView.isContained()
                || !Double.isFinite(reach) || reach <= 0) return null;
        Vec3 start = body.getEyePosition();
        Vec3 end = start.add(body.getLookAngle().scale(reach));
        var block = body.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, body));
        double blocker = block.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                ? start.distanceTo(block.getLocation()) : reach;
        return body.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        new AABB(start, end).inflate(1.0), entity -> entity != body && !(entity instanceof net.minecraft.world.entity.player.Player)
                                && !entity.isRemoved() && entity.level() == body.level()
                                && com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.projectForActor(entity)
                                .flatMap(com.juicyslew.moonstation14.ms14.mobs.MobStatePolicy::from).isPresent())
                .stream().filter(entity -> com.juicyslew.moonstation14.ms14.standing.BodyInteractionRay.hit(
                        com.juicyslew.moonstation14.ms14.standing.BodyInteractionBounds.of(entity), start, end, reach, blocker).isPresent())
                .min(java.util.Comparator.comparingDouble(entity -> com.juicyslew.moonstation14.ms14.standing.BodyInteractionBounds.of(entity)
                        .clip(start, end).map(start::distanceToSqr).orElse(Double.MAX_VALUE)))
                .map(net.minecraft.world.entity.Entity::getUUID).orElse(null);
    }

    /** Pull nomination prefers the existing selected loose-item target, then resolved pullable characters. */
    private static UUID lookPullTarget(net.minecraft.world.entity.LivingEntity body) {
        if (body == null || body.level() != net.minecraft.client.Minecraft.getInstance().level) return null;
        UUID loose = body instanceof Mob mob ? lookTarget(mob) : null;
        if (loose != null) return loose;
        Vec3 start = body.getEyePosition();
        Vec3 end = start.add(body.getLookAngle().scale(5));
        var block = body.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, body));
        double blocker = block.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                ? start.distanceTo(block.getLocation()) : 5;
        return body.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        new AABB(start, end).inflate(2.0), entity -> entity != body
                                && !entity.isRemoved() && entity.level() == body.level()
                                && com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem
                                .projectForActor(entity).filter(data -> data.component(
                                        com.juicyslew.moonstation14.ms14.character.components.PullableComponent.class).isPresent()).isPresent())
                .stream().filter(entity -> {
                    var box = com.juicyslew.moonstation14.ms14.standing.BodyInteractionBounds.of(entity);
                    return com.juicyslew.moonstation14.ms14.standing.BodyInteractionRay
                            .hit(box, start, end, 5, blocker).isPresent();
                }).min(java.util.Comparator.comparingDouble(entity -> com.juicyslew.moonstation14.ms14.standing.BodyInteractionBounds.of(entity)
                        .clip(start, end).map(start::distanceToSqr).orElse(Double.MAX_VALUE)))
                .map(net.minecraft.world.entity.Entity::getUUID).orElse(null);
    }

    private static boolean inventoryUsesAlt() {
        int key = INVENTORY.getKey().getValue();
        return INVENTORY.getKey().getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM
                && (key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT);
    }

    private static boolean liquidItem(String itemId) {
        if (itemId == null) return false;
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.tryParse(itemId));
        return item instanceof com.juicyslew.moonstation14.ms14.reagent.IReagentTrait;
    }

    private static boolean aimedLooseLiquid(UUID target) {
        if (target == null || cameraBody == null) return false;
        return cameraBody.level().getEntitiesOfClass(ItemEntity.class, cameraBody.getBoundingBox().inflate(6),
                item -> item.getUUID().equals(target) && (item.getItem().getItem() instanceof com.juicyslew.moonstation14.ms14.reagent.IReagentTrait
                        || item.getItem().is(com.juicyslew.moonstation14.item.ModItems.JUG))).size() == 1;
    }

    private static net.minecraft.core.BlockPos aimedPuddle(Mob body) {
        return body == null ? null : com.juicyslew.moonstation14.ms14.hands.live.PuddleTargetProbe.find(body.level(), body);
    }

    private static boolean requestPuddleSip(net.minecraft.core.BlockPos pos) {
        var snapshot = POLICY.snapshot();
        if (pos == null || snapshot == null) return false;
        var active = snapshot.hands().stream().filter(hand -> hand.id().equals(snapshot.activeHand())).findFirst().orElse(null);
        if (active == null || active.token() != null) return false;
        long sequence = POLICY.action(clock);
        if (sequence == 0) return false;
        PacketDistributor.sendToServer(BodyHandActionRequest.sipPuddle(active.id(), snapshot.epoch(), sequence,
                snapshot.revision(), pos));
        var puddle=com.juicyslew.moonstation14.ms14.ingestion.ModIngestionProfiles.clientLookup(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","puddle"));
        if(puddle!=null && cameraBody!=null) com.juicyslew.moonstation14.ms14.timed_interaction.client.TimedInteractionClient.predictStart(sequence,cameraBody.getUUID(),puddle.delayTicks());
        return true;
    }

    private static void refreshPile(Mob body) {
        if (body == null || body != cameraBody || GhostControlClient.committedCharacterEpoch() != cameraEpoch
                || cameraEpoch == 0) {
            PILE.clear(); pileItems = java.util.List.of(); pileFrame = -1; return;
        }
        Vec3 start = body.getEyePosition();
        Vec3 end = start.add(body.getLookAngle().scale(5));
        var block = body.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, body));
        double blockerDistance = start.distanceTo(block.getLocation());
        double maxDistance = blockerDistance * blockerDistance;
        var candidates = new java.util.ArrayList<ItemEntity>();
        var distances = new java.util.HashMap<UUID, Double>();
        for (ItemEntity item : body.level().getEntitiesOfClass(ItemEntity.class,
                new AABB(start, end).inflate(BodyItemTargetProbe.TARGET_MARGIN),
                entity -> entity.isAlive() && !entity.getItem().isEmpty())) {
            var box = item.getBoundingBox();
            Vec3 hit = LooseItemRayGeometry.entry(box, start, end);
            if (hit == null) continue;
            Vec3 look = body.getLookAngle();
            double nearFace = LooseItemRayGeometry.physicalNearFace(box, start, look);
            if (nearFace >= blockerDistance) continue;
            double distance = hit.distanceToSqr(start);
            if (distance <= maxDistance) { candidates.add(item); distances.put(item.getUUID(), distance); }
        }
        candidates.sort(java.util.Comparator.comparingDouble((ItemEntity i) -> distances.get(i.getUUID()))
                .thenComparing(ItemEntity::getUUID));
        if (candidates.size() > BodyItemTargetProbe.MAX_VISIBLE_CANDIDATES)
            candidates.subList(BodyItemTargetProbe.MAX_VISIBLE_CANDIDATES, candidates.size()).clear();
        pileItems = java.util.List.copyOf(candidates);
        PILE.update(pileItems.stream().map(ItemEntity::getUUID).toList());
        pileFrame = clock;
        pileEpoch = cameraEpoch;
    }

    /** Fresh client-only outline nomination; action eligibility and pending policy never affect observation. */
    public static ItemEntity selectedItemForOutline() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        Mob body = currentBody();
        if (DUMP_GESTURE.active() && body != null && body.getUUID().equals(dumpBody)
                && cameraEpoch == GhostControlClient.committedCharacterEpoch() && body.level() == mc.level) {
            ItemEntity captured = looseEntity(DUMP_GESTURE.source());
            if (captured != null && captured.isAlive()) return captured;
        }
        if (mc.screen != null || !mc.mouseHandler.isMouseGrabbed() || body == null
                || body != cameraBody || GhostControlClient.committedCharacterEpoch() != cameraEpoch
                || cameraEpoch == 0 || body.level() != mc.level) return null;
        refreshPile(body);
        var ids = pileItems.stream().map(ItemEntity::getUUID).toList();
        UUID selected = PILE.update(ids);
        if (selected == null) return null;
        for (ItemEntity item : pileItems)
            if (item.getUUID().equals(selected) && item.isAlive()) return item;
        return null;
    }

    private static void onSnapshot(BodyHandStateSnapshot snapshot) {
        reconcile();
        if (POLICY.accept(snapshot)) {
            replayDeferredPlacement();
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
        boolean pullCompletion = correlated && result.sequence() == pullActionSequence;
        if (pullCompletion) pullActionSequence = 0;
        // Pull reservations are part of the authoritative hand snapshot. Refresh immediately
        // after the server result instead of waiting for the periodic recovery query.
        if (pullCompletion) {
            long sequence = POLICY.query(clock);
            if (sequence != 0) {
                WORLD.handQueryIssued();
                PacketDistributor.sendToServer(new BodyHandStateQuery(POLICY.queryEpoch(), sequence));
            }
        }
        if(correlated) com.juicyslew.moonstation14.ms14.timed_interaction.client.TimedInteractionClient.actionResult(
                result.sequence(), result.reason() == BodyHandActionResult.Reason.OK);
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

    public static void render(net.neoforged.neoforge.client.event.RenderGuiEvent.Post event) {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.ChatScreen) return;
        renderHud(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    private static void renderChatScreenHud(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.ChatScreen && mc.screen == event.getScreen())
            renderHud(event.getGuiGraphics(), event.getPartialTick());
    }

    private static void renderHud(net.minecraft.client.gui.GuiGraphics graphics, float partialTick) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        renderDumpAimFeedback(graphics, mc);
        var wire = com.juicyslew.moonstation14.ms14.doors.ui.client.AirlockWireClient.active();
        if (wire != null && com.juicyslew.moonstation14.ms14.ui.client.GameplayHudPolicy.hudPaintsWire(mc.screen)
                && mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen
                && currentBody() != null && !mc.options.hideGui)
            wire.render(graphics, -1, -1, partialTick);
        boolean gameplay = mc.screen == null;
        Mob body = currentBody();
        boolean aimCue = BodyHandClientPolicy.showAimCue(body != null && GhostControlClient.committedCharacterEpoch() > 0,
                !mc.options.hideGui, gameplay);
        boolean hudVisible = com.juicyslew.moonstation14.ms14.ui.client.GameplayHudPolicy.visible(mc.screen);
        if (!hudVisible || body == null || mc.options.hideGui) {
            if (gameplay && com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.shouldPaintWorkspace(
                    true,false,true,true)) renderPointerWorkspace(graphics, partialTick);
            return;
        }
        reconcile();
        if (aimCue) {
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
            drawHandTile(graphics, mc, bar.leftHand(), snapshot.hands().get(0),
                    snapshot.activeHand().equals(snapshot.hands().get(0).id()), enabled);
            drawHandTile(graphics, mc, bar.rightHand(), snapshot.hands().get(1),
                    snapshot.activeHand().equals(snapshot.hands().get(1).id()), enabled);
            var info = HandInfoLayout.calculate(width, height, bar);
            if (info != null) {
                drawHandInfo(graphics, mc, info.left(), snapshot.hands().get(0), enabled, info.textBudget(), true);
                drawHandInfo(graphics, mc, info.right(), snapshot.hands().get(1), enabled, info.textBudget(), false);
            }
        }
        String feedback = POLICY.feedback();
        if (feedback != null && !feedback.toLowerCase(java.util.Locale.ROOT).contains("waiting"))
            drawHudLine(graphics, mc, feedback.replaceFirst("^Hands: ", ""), 8, 54, width);
        if (snapshot != null) drawEquipmentPanel(graphics, mc, snapshot, width, height, !POLICY.presentationPending());
        // Gameplay HUD phase is after the HUD only; Screen hosts paint in ScreenEvent.Render.Post.
        if (gameplay && com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.shouldPaintWorkspace(
                true,false,true,true)) renderPointerWorkspace(graphics, partialTick);
    }

    private static void renderDumpAimFeedback(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.Minecraft mc) {
        if (!DUMP_GESTURE.active() || mc.player == null || mc.options.hideGui) return;
        var source = looseEntity(DUMP_GESTURE.source());
        if (source == null || !source.isAlive()) return;
        String keyLabel = AIM_RELEASE_DUMP.getTranslatedKeyMessage().getString();
        String label = "Aiming with " + keyLabel + ": " + source.getItem().getHoverName().getString();
        var body = currentBody();
        var hit = dumpReleaseTarget(body);
        var sourcePolicy = source.getItem().getItem() instanceof com.juicyslew.moonstation14.ms14.reagent.IReagentTrait trait
                ? trait.drainableSolution(source.getItem(), source.level()) : null;
        if (body != null && hit != null && body.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(hit.getBlockPos()))
                <= com.juicyslew.moonstation14.ms14.chemistry.chemmaster.interaction.ChemMasterWorldDump.DUMP_INTERACTION_RANGE
                && body.level().getBlockEntity(hit.getBlockPos())
                instanceof com.juicyslew.moonstation14.ms14.chemistry.chemmaster.ChemMasterBlockEntity machine
                && machine.prototype() != null && machine.prototype().dumpableSolution().enabled()
                && machine.prototype().dumpableSolution().unlimited() && sourcePolicy != null && sourcePolicy.enabled()
                && sourcePolicy.solutionName().equals(machine.prototype().dumpableSolution().solutionName()))
            label = "Release " + keyLabel + " to pour entire container";
        graphics.drawCenteredString(mc.font, label, graphics.guiWidth() / 2, graphics.guiHeight() / 2 + 20, 0xffe7f5ff);
    }

    static boolean popupWorldToEquipment(PopupActionPlan.WorldCrossSource source, String slotId) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof StoragePopupScreen)
                || source == null || slotId == null || !ItemFootprints.clientCatalogIsLoaded()) return false;
        reconcile();
        var proof = inventoryProof();
        var world = WORLD.view(source.worldTarget());
        var window = POPUPS.window(source.host());
        if (proof == null || world == null || window == null || !window.visible() || window.provisional()
                || !equipmentPanelDependencyEnabled(slotId)
                || !BodyHandClientPolicy.equipmentFitsSlot(proof, slotId, source.itemId())) return false;
        var plan = PopupActionPlan.worldToEquipmentPlan(source, slotId, proof, world, true,
                worldActionable(source.worldTarget()), 0);
        if (plan == null) return false;
        return WORLD.crossAction(plan.target(), proof, plan.direction(), true, plan.hostId(), null,
                plan.child(), plan.oldAnchor().x(), plan.oldAnchor().y(), plan.oldRotation().degrees(),
                plan.newAnchor().x(), plan.newAnchor().y(), plan.newRotation().degrees(), clock).map(action -> {
            worldCrossPendingTarget = plan.target(); PacketDistributor.sendToServer(action); return true;
        }).orElse(false);
    }

    static boolean popupGridToEquipment(PopupActionPlan.CrossSource source, String slotId) {
        if (!(net.minecraft.client.Minecraft.getInstance().screen instanceof StoragePopupScreen)
                || source == null || slotId == null) return false;
        reconcile(); var proof = POLICY.snapshot(); var view = POLICY.inventoryView();
        var window = POPUPS.window(source.host());
        if (proof == null || view == null || window == null || !window.visible() || window.provisional()
                || popupGrid(view, source.host(), false) == null || !equipmentPanelDependencyEnabled(slotId)) return false;
        if (!BodyHandClientPolicy.equipmentFitsSlot(proof, slotId, source.itemId())) return false;
        if (PopupActionPlan.gridToEquipmentRequest(source, slotId, proof, true, 0, 1) == null) return false;
        long sequence = POLICY.action(clock); if (sequence == 0) return false;
        var request = PopupActionPlan.gridToEquipmentRequest(source, slotId, proof, true, 0, sequence);
        if (request == null) return false;
        PacketDistributor.sendToServer(request); return true;
    }

    static boolean popupEquipmentToGrid(String slotId, StoragePopupWorkspace.HostKey destination,
            com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Cell anchor,
            com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Rotation rotation) {
        reconcile(); var proof = POLICY.snapshot();
        var window = destination == null ? null : POPUPS.window(destination);
        if (proof == null || POLICY.inventoryView() == null || window == null || !window.visible() || window.provisional()) return false;
        var source = proof.equipment().stream().filter(s -> s.id().equals(slotId) && s.token() != null).findFirst().orElse(null);
        if (source == null || popupGrid(POLICY.inventoryView(), destination, false) == null
                || PopupActionPlan.equipmentToGridRequest(slotId, source.token(), source.itemId(), source.count(), proof,
                destination, anchor, rotation, 0, 1) == null) return false;
        long sequence = POLICY.action(clock); if (sequence == 0) return false;
        var request = PopupActionPlan.equipmentToGridRequest(slotId, source.token(), source.itemId(), source.count(), proof,
                destination, anchor, rotation, 0, sequence);
        if (request == null) return false; PacketDistributor.sendToServer(request); return true;
    }

    static boolean popupEquipmentToWorld(HandEquipmentGesture.Drag drag, StoragePopupWorkspace.Window target,
            com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Cell anchor,
            com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Rotation rotation) {
        if (drag == null || drag.sourceSlotId() == null || target == null
                || target.key().kind() != StoragePopupWorkspace.HostKind.WORLD) return false;
        reconcile(); var proof = POLICY.snapshot(); var world = WORLD.view(target.key().worldId());
        var sourceWindow = POPUPS.window(target.key());
        if (proof == null || POLICY.inventoryView() == null || POLICY.presentationPending()
                || proof.revision() != drag.revision() || proof.epoch() != drag.epoch()
                || !proof.accountId().equals(drag.accountId()) || !proof.bodyId().equals(drag.bodyId())
                || sourceWindow == null || !sourceWindow.visible() || sourceWindow.provisional()
                || !equipmentPanelDependencyEnabled(drag.sourceSlotId())) return false;
        boolean fit = BodyHandClientPolicy.equipmentFitsSlot(proof, drag.sourceSlotId(), drag.itemId());
        var plan = PopupActionPlan.equipmentToWorldPlan(drag.sourceSlotId(), drag.token(), drag.itemId(), drag.count(),
                proof, proof, world, anchor, rotation, fit, worldActionable(target.key().worldId()), 0);
        if (plan == null) return false;
        return WORLD.crossAction(plan.target(), proof, plan.direction(), true, plan.hostId(), plan.hostToken(),
                plan.child(), 0, 0, 0, plan.newAnchor().x(), plan.newAnchor().y(), plan.newRotation().degrees(), clock)
                .map(action -> { worldCrossPendingTarget = plan.target(); PacketDistributor.sendToServer(action); return true; })
                .orElse(false);
    }

    static com.juicyslew.moonstation14.ms14.equipment.client.EquipmentPanelLayout.Layout equipmentPanelLayout(int width,int height) {
        var snapshot=POLICY.presentationSnapshot();
        if(snapshot==null||snapshot.equipment().isEmpty())return null;
        var definitions=equipmentDefinitions(snapshot);
        return definitions.isEmpty()?null:com.juicyslew.moonstation14.ms14.equipment.client.EquipmentPanelLayout.calculate(width,height,
                definitions,EQUIPMENT_PANEL_STATE.expanded(snapshot.epoch()));
    }
    private static java.util.List<com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData.Slot> equipmentDefinitions(BodyHandStateSnapshot snapshot) {
        var catalog=com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.clientCatalog();
        var id=snapshot.templateId()==null?com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.id("human"):snapshot.templateId();
        var template=catalog.get(id);
        if(template==null)template=catalog.get(com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates.id("human"));
        if(template==null)return java.util.List.of();
        if(snapshot.templateId()!=null&&snapshot.templateId().equals(id))return template.slots();
        var observed=snapshot.equipment().stream().map(BodyHandStateSnapshot.EquipmentSlot::id).collect(java.util.stream.Collectors.toSet());
        return template.slots().stream().filter(slot->observed.contains(slot.id())).toList();
    }
    static boolean toggleEquipmentPanel(int x,int y,int width,int height) {
        var panel=equipmentPanelLayout(width,height); var snapshot=POLICY.presentationSnapshot();
        if(panel==null||snapshot==null||!panel.toggleRect().contains(x,y))return false;
        EQUIPMENT_PANEL_STATE.toggle(snapshot.epoch()); return true;
    }
    static boolean equipmentPanelHit(int x,int y,int width,int height) {
        var panel=equipmentPanelLayout(width,height);
        return panel!=null&&(panel.toggleRect().contains(x,y)||panel.slots().stream().anyMatch(s->s.rect().contains(x,y)));
    }
    static String equipmentPanelSlotAt(int x,int y,int width,int height) {
        var panel=equipmentPanelLayout(width,height); if(panel==null)return null;
        return panel.slots().stream().filter(s->s.rect().contains(x,y))
                .filter(s->panel.expanded()||!equipmentDefinitions(POLICY.presentationSnapshot()).stream().filter(d->d.id().equals(s.slotId())).findFirst().map(d->d.group().equals("Default")).orElse(false))
                .map(com.juicyslew.moonstation14.ms14.equipment.client.EquipmentPanelLayout.SlotRect::slotId).findFirst().orElse(null);
    }
    static boolean equipmentPanelDependencyEnabled(String slotId) {
        var snapshot=POLICY.presentationSnapshot();
        if(snapshot==null)return false;
        var def=equipmentDefinitions(snapshot).stream().filter(s->s.id().equals(slotId)).findFirst().orElse(null);
        if(def==null)return false;
        var occupied=snapshot.equipment().stream().filter(s->s.token()!=null).map(s->s.id())
                .collect(java.util.stream.Collectors.toSet());
        boolean outerAllows=false;
        if(slotId.equals("suitstorage")) {
            var coat=snapshot.equipment().stream().filter(s->s.id().equals("outerClothing")&&s.token()!=null).findFirst().orElse(null);
            var itemId=coat==null?null:net.minecraft.resources.ResourceLocation.tryParse(coat.itemId());
            var composition=itemId==null?null:com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.clientCatalog().get(itemId);
            outerAllows=composition!=null&&composition.allowSuitStorage().map(com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData.AllowSuitStorage::enabled).orElse(false);
        }
        return com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.dependenciesSatisfied(def,occupied,outerAllows);
    }

    /** Z quick-equip is a single server-authoritative nomination; ordinary items keep existing use behavior. */
    private static boolean quickEquipInHand() {
        reconcile();
        var proof = POLICY.snapshot();
        var view = POLICY.inventoryView();
        if (proof == null || view == null || POLICY.presentationPending()) return true;
        var hand = view.first().id().equals(view.activeHand()) ? view.first() : view.second();
        if (hand.itemId() == null) return false;
        var item = net.minecraft.resources.ResourceLocation.tryParse(hand.itemId());
        var stack = item == null ? net.minecraft.world.item.ItemStack.EMPTY : new net.minecraft.world.item.ItemStack(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(item).orElse(net.minecraft.world.item.Items.AIR));
        boolean eligible = cameraBody != null && com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy
                .quickEquip(cameraBody.level(), stack);
        if (!eligible) return false;
        var plan = HandEquipmentGesture.quickEquipPlan(proof, hand, true, false);
        if (plan == null) return true;
        long sequence = POLICY.action(clock);
        if (sequence == 0) return true;
        PacketDistributor.sendToServer(new BodyHandActionRequest(BodyHandActionRequest.Action.QUICK_EQUIP,
                plan.handId(), proof.epoch(), sequence, plan.revision(), null, plan.token()));
        return true;
    }
    static boolean pendingSnapshot() { return POLICY.presentationPending(); }
    static void popupEquipmentSlot(HandEquipmentGesture.Drag drag,String slotId) {
        if(drag==null||slotId==null)return;
        reconcile(); var proof=POLICY.snapshot(); var view=POLICY.inventoryView();
        if(proof==null||view==null||POLICY.presentationPending()||!equipmentPanelDependencyEnabled(slotId)
                ||!proof.accountId().equals(drag.accountId())||!proof.bodyId().equals(drag.bodyId())
                ||proof.epoch()!=drag.epoch()||proof.revision()!=drag.revision())return;
        var slot=proof.equipment().stream().filter(s->s.id().equals(slotId)).findFirst().orElse(null);
        var hand=drag.handId().equals(view.first().id())?view.first():drag.handId().equals(view.second().id())?view.second():null;
        if(hand==null||hand.token()==null||!drag.token().equals(hand.token())||!drag.itemId().equals(hand.itemId())||drag.count()!=hand.count())return;
        if(!com.juicyslew.moonstation14.ms14.hands.client.BodyHandClientPolicy.equipmentFitsSlot(proof,slotId,hand.itemId()))return;
        var plan=HandEquipmentGesture.slotPlan(slotId,slot,hand,false,POLICY.presentationPending(),true);
        if(plan==null)return;
        long sequence=POLICY.action(clock); if(sequence==0)return;
        PacketDistributor.sendToServer(new BodyHandActionRequest(plan.action(),plan.handId(),proof.epoch(),sequence,
                proof.revision(),null,plan.expectedToken(),null,-1,-1,-1,-1,-1,-1,null,plan.slotId(),null));
    }
    static void equipmentSlotClick(String slotId) {
        equipmentSlotClick(slotId,null);
    }
    static void equipmentSlotClick(String slotId,String nominatedHandId) {
        reconcile(); var proof=POLICY.snapshot(); var view=POLICY.inventoryView();
        if(proof==null||view==null||POLICY.presentationPending()||!equipmentPanelDependencyEnabled(slotId))return;
        var slot=proof.equipment().stream().filter(s->s.id().equals(slotId)).findFirst().orElse(null);
        if(slot!=null&&slot.token()!=null&&(slotId.equals("back")||slotId.equals("belt"))&&nominatedHandId==null) {
            togglePopup(slotId.equals("back")?StoragePopupWorkspace.HostKind.BAG:StoragePopupWorkspace.HostKind.BELT); return;
        }
        var hand=nominatedHandId==null?(view.first().id().equals(view.activeHand())?view.first():view.second())
                :view.first().id().equals(nominatedHandId)?view.first():view.second().id().equals(nominatedHandId)?view.second():null;
        var plan=HandEquipmentGesture.slotPlan(slotId,slot,hand,slot!=null&&slot.token()!=null);
        if(plan==null)return;
        if(plan.action()==BodyHandActionRequest.Action.EQUIP_SLOT
                &&!BodyHandClientPolicy.equipmentFitsSlot(proof,slotId,hand.itemId()))return;
        long sequence=POLICY.action(clock); if(sequence==0)return;
        PacketDistributor.sendToServer(new BodyHandActionRequest(plan.action(),plan.handId(),proof.epoch(),sequence,
                proof.revision(),null,plan.expectedToken(),null,-1,-1,-1,-1,-1,-1,null,plan.slotId(),null));
    }

    static void moveEquipment(HandEquipmentGesture.GearMovePlan plan) {
        reconcile();
        var proof = POLICY.snapshot();
        if (plan == null || proof == null || proof.epoch() != plan.epoch() || proof.revision() != plan.revision()) return;
        long sequence = POLICY.action(clock);
        if (sequence == 0) return;
        PacketDistributor.sendToServer(new BodyHandActionRequest(BodyHandActionRequest.Action.MOVE_EQUIPMENT,
                plan.handId(), proof.epoch(), sequence, proof.revision(), null, plan.expectedToken(), null,
                0, 0, 0, 0, 0, 0, null, plan.sourceSlotId(), plan.destinationSlotId()));
    }

    static void drawEquipmentPanel(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.Minecraft mc,
                                           BodyHandStateSnapshot snapshot, int width, int height, boolean enabled) {
        if (snapshot == null || snapshot.equipment().isEmpty()) return;
        EQUIPMENT_PANEL_STATE.beginBody(snapshot.epoch());
        var definitions=equipmentDefinitions(snapshot);
        if (definitions.isEmpty()) return;
        var panel = com.juicyslew.moonstation14.ms14.equipment.client.EquipmentPanelLayout.calculate(width,height,definitions,
                EQUIPMENT_PANEL_STATE.expanded(snapshot.epoch()));
        com.juicyslew.moonstation14.ms14.equipment.client.EquipmentPanelRenderer.drawToggle(graphics,panel,panel.expanded());
        for (var rect : panel.slots()) {
            var def = definitions.stream().filter(s -> s.id().equals(rect.slotId())).findFirst().orElse(null);
            if (def != null && def.group().equals("Default") && !panel.expanded()) continue;
            var observed = snapshot.equipment().stream().filter(s -> s.id().equals(rect.slotId())).findFirst().orElse(null);
            if (def == null || observed == null) continue;
            boolean slotEnabled = enabled && equipmentPanelDependencyEnabled(def.id());
            com.juicyslew.moonstation14.ms14.equipment.client.EquipmentPanelRenderer.drawSlot(graphics,rect,def,slotEnabled,POLICY.presentationPending());
            if (observed.token() != null) drawStack(graphics,observed.itemId(),observed.count(),observed.presentation(),rect.rect(),mc);
            if (rect.rect().contains((int)(mc.mouseHandler.xpos()*width/mc.getWindow().getScreenWidth()),
                    (int)(mc.mouseHandler.ypos()*height/mc.getWindow().getScreenHeight())))
                graphics.renderTooltip(mc.font,net.minecraft.network.chat.Component.literal(def.displayName()),
                        (int)(mc.mouseHandler.xpos()*width/mc.getWindow().getScreenWidth()),
                        (int)(mc.mouseHandler.ypos()*height/mc.getWindow().getScreenHeight()));
    }
    }

    public static void renderPointerWorkspace(net.minecraft.client.gui.GuiGraphics graphics, float partialTick) {
        var mc=net.minecraft.client.Minecraft.getInstance();
        if (mc.options.hideGui || !com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.allowsHost(mc.screen)) return;
        int width=mc.getWindow().getGuiScaledWidth(),height=mc.getWindow().getGuiScaledHeight();
        int storageSiblingCount=(int) POPUPS.windows().stream().filter(StoragePopupWorkspace.Window::visible).count()
                +(int) com.juicyslew.moonstation14.ms14.medical.client.HealthAnalyzerWorkspace.INSTANCE.windows().stream()
                .filter(com.juicyslew.moonstation14.ms14.medical.client.HealthAnalyzerWorkspace.Window::visible).count()
                +(int) com.juicyslew.moonstation14.ms14.chemistry.chemmaster.client.ChemMasterWorkspace.INSTANCE.windows().size();
        com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.renderWorkspace(
                () -> {
                    if ((mc.screen instanceof StoragePopupScreen || mc.screen == null && currentBody() != null)
                            && !mc.options.hideGui)
                        StoragePopupScreen.drawWindows(graphics, POPUPS, width, height, partialTick);
                },
                () -> com.juicyslew.moonstation14.ms14.ui.client.pointer.PointerOverlayRegistry.render(graphics,
                        (int)(mc.mouseHandler.xpos()*width/mc.getWindow().getScreenWidth()),
                        (int)(mc.mouseHandler.ypos()*height/mc.getWindow().getScreenHeight()),
                        partialTick,width,height,storageSiblingCount));
    }

    private static void drawHandTile(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.Minecraft mc,
                                     HandBarLayout.Rect r, BodyHandStateSnapshot.Hand hand, boolean active, boolean enabled) {
        drawInsetPanel(g, r);
        if (active) g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + 1, 0xffc7a85b);
        if (hand.pullTarget() != null) {
            int cx = r.x() + r.width() / 2, cy = r.y() + r.height() / 2;
            // A reserved hand represents an ongoing pull, never an item stack.
            g.fill(cx - 5, cy - 1, cx + 5, cy + 2, 0xffe0b85a);
            g.fill(cx + 2, cy - 4, cx + 5, cy + 5, 0xffe0b85a);
        } else if (hand.token() == null) drawPlaceholder(g, r, false);
        else drawStack(g, hand.itemId(), hand.count(), hand.presentation(), r, mc);
        if (!enabled) g.fill(r.x(), r.y(), r.x() + r.width(), r.y() + r.height(), 0x66090f12);
    }

    private static void drawHandInfo(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.Minecraft mc,
                                     HandBarLayout.Rect rect, BodyHandStateSnapshot.Hand hand, boolean enabled,
                                     int textBudget, boolean rightAligned) {
        String name;
        if (hand.pullTarget() != null) name = net.minecraft.network.chat.Component.translatable("gui.moonstation14.hands.pulling").getString();
        else if (hand.token() == null) name = net.minecraft.network.chat.Component.translatable("gui.moonstation14.hands.empty").getString();
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
        int textX = HandInfoLayout.textX(rect.x(), rect.width(), mc.font.width(name), scale, rightAligned);
        g.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), 0xcc11191d);
        g.fill(rect.x(), rect.y() + rect.height() - 1, rect.x() + rect.width(), rect.y() + rect.height(), 0xff60747c);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(textX, rect.y() + 2, 0);
        pose.scale(scale, scale, 1);
        g.drawString(mc.font, name, 0, 0, enabled ? 0xffe2e8e9 : 0xff858b8d, false);
        pose.popPose();
    }

    private static void drawEquipmentTile(net.minecraft.client.gui.GuiGraphics g, net.minecraft.client.Minecraft mc,
            HandBarLayout.Rect r, java.util.List<BodyHandStateSnapshot.EquipmentSlot> slots, String id, boolean enabled) {
        drawInsetPanel(g, r);
        var slot = slots.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
        if (slot == null || slot.token() == null) drawPlaceholder(g, r, true);
        else drawStack(g, slot.itemId(), slot.count(), slot.presentation(), r, mc);
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

    /** Reuse the existing hand glyph for remote empty hand cells; identity stays presentation-only. */
    public static void drawStripHandPlaceholder(net.minecraft.client.gui.GuiGraphics g, HandBarLayout.Rect r, String handId) {
        drawPlaceholder(g,r,false);
        String label=handId!=null&&handId.toLowerCase(java.util.Locale.ROOT).contains("left")?"L":
                handId!=null&&handId.toLowerCase(java.util.Locale.ROOT).contains("right")?"R":"";
        if(!label.isEmpty()){var pose=g.pose();pose.pushPose();pose.translate(r.x()+r.width()-4,r.y()+2,0);pose.scale(.45f,.45f,1);g.drawString(net.minecraft.client.Minecraft.getInstance().font,label,0,0,0xffd5e0e3,false);pose.popPose();}
    }

    private static void drawStack(net.minecraft.client.gui.GuiGraphics g, String itemId, int count,
                                  com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation presentation,
                                  HandBarLayout.Rect r, net.minecraft.client.Minecraft mc) {
        ItemUiRenderer.renderIntoSlot(g,ItemUiRenderer.stack(itemId,count,presentation),count,r.x(),r.y(),r.width(),r.height(),mc.font);
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

    private static int drawHudLine(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.Minecraft mc,
                                   String line, int x, int y, int width) {
        line = mc.font.plainSubstrByWidth(line, Math.max(0, width / 2 - 12));
        graphics.fill(x - 3, y - 2, x + mc.font.width(line) + 3, y + mc.font.lineHeight + 2, 0x99000000);
        graphics.drawString(mc.font, line, x, y, 0xffffffff, false);
        return y + mc.font.lineHeight + 4;
    }
}
