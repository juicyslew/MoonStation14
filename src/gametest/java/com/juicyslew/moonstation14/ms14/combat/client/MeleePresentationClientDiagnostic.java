package com.juicyslew.moonstation14.ms14.combat.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Bounded client-side visual probe. It observes and drives only ordinary public gameplay paths. */
@EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID, value = Dist.CLIENT)
public final class MeleePresentationClientDiagnostic {
    // The connected strike driver owns the newer three-window capture protocol.
    // Keep this legacy eight-case probe opt-in only when strike mode is absent.
    private static final boolean ENABLED = Boolean.getBoolean("ms14.meleePresentationDiagnostic")
            && !Boolean.getBoolean("ms14.meleeStrikePresentationDiagnostic");
    private static final String[] COMMANDS = {"fist_first", "fist_third", "disarm_first", "fist_hit_near", "fist_hit_edge", "wall_first", "knife_first", "observer_setup"};
    private static final String[] OUTPUTS = {"fist_first", "fist_third", "disarm_first", "fist_hit_near", "fist_hit_edge", "wall_first", "knife_first", "observer"};
    private static final List<JsonObject> records = new ArrayList<>();
    private static Path captureDir;
    private static int phase;
    private static String waitingFor;
    private static JsonObject scene;
    private static boolean started, inputSent, screenshotPending, awaitingReply, done;
    private static long frameWatermark, requestSequence, inputTick, totalTicks;
    private static net.minecraft.world.phys.Vec3 phaseEye, phaseLook;
    private static UUID expectedBody;
    private static boolean expectedFirstPerson;
    private static long disarmQueuedWatermark;
    private static int disarmReplyTicks;
    private static boolean observerCommandSent;
    private static int observerPeerTicks;
    private static int observerReplyTicks;
    private static JsonObject observerReplyScene;
    private static JsonObject pendingObserverRecord;
    private static MeleeArcClient.DiagnosticSnapshot lastSnapshot;
    private static int worldReadyTicks;
    private static long phaseDeadlineNanos;
    private static String lastBlockedStage = "not_started";
    private static JsonObject lastBlockedDetails = new JsonObject();
    private static long lastRefreshNanos;
    private MeleePresentationClientDiagnostic() { }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED || done) return;
        try {
            if (++totalTicks > 3000) throw new IllegalStateException("client diagnostic exceeded 150-second tick budget");
            var mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || mc.getConnection() == null) return;
            if (started) MeleeCaptureFocusPolicy.require(mc);
            if (captureDir == null) {
                String raw = System.getProperty("ms14.meleePresentationCaptureDir");
                if (raw == null || raw.isBlank()) throw new IllegalStateException("missing capture directory property");
                captureDir = Path.of(raw).toAbsolutePath().normalize(); Files.createDirectories(captureDir);
            }
            if (!started) {
                var readyBody = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
                if (readyBody == null || !hasRequiredLeafProfiles()) {
                    if (++worldReadyTicks > 200) throw new IllegalStateException("owned character/catalog did not synchronize within 200 world ticks");
                    return;
                }
                started = true;
                MeleeCaptureFocusPolicy.require(mc);
                issueNextCommand(mc);
            }
            if (started && phase < OUTPUTS.length && phaseDeadlineNanos != 0 && System.nanoTime() >= phaseDeadlineNanos) {
                String deadlinePhase = scene != null && scene.has("phase") ? scene.get("phase").getAsString()
                        : waitingFor != null ? waitingFor : OUTPUTS[phase];
                throw new IllegalStateException("phase deadline expired phase="+deadlinePhase+" stage="+lastBlockedStage+" details="+lastBlockedDetails);
            }
            if (awaitingReply) {
                var reply = MeleeAttackClient.diagnosticLastReply().orElse(null);
                if (reply != null && expectedBody.equals(reply.bodyUUID()) && requestSequence == reply.sequence()) {
                    var phaseName = scene.get("phase").getAsString();
                    var expectedStatus = phaseName.equals("disarm_first") ? null : hitPhase(phaseName) || phaseName.equals("wall_first") ? com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.HIT : com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.MISS;
                    if (reply.epoch() != scene.get("primaryEpoch").getAsLong() || (expectedStatus != null && reply.status() != expectedStatus)
                            || (expectedStatus == null && reply.status() != com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.DISARM_FAILED && reply.status() != com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.DISARM_HANDLED)) throw new IllegalStateException("correlated melee reply unexpected phase="+phaseName+" status=" + reply.status());
                    JsonObject last = records.get(records.size() - 1);
                    last.addProperty("replyStatus", reply.status().name()); last.addProperty("replySequence", reply.sequence());
                    awaitingReply = false;
                    scene = null;
                    if (phase == OUTPUTS.length) complete(); else issueNextCommand(mc);
                } else if (++inputTick > 300) throw new IllegalStateException("no correlated production melee reply for request " + requestSequence);
                return;
            }
            if (scene != null && "observer_setup".equals(scene.get("phase").getAsString())) {
                armObserverSwing(mc);
                if (observerCommandSent && waitingFor != null) {
                    if (++observerReplyTicks > 300) throw new IllegalStateException("observer swing scene reply did not arrive within 300 ticks");
                    Path replyPath = captureDir.resolve("scene.json");
                    if (Files.isRegularFile(replyPath)) {
                        var candidate = JsonParser.parseString(Files.readString(replyPath)).getAsJsonObject();
                        if (candidate.has("ready") && candidate.get("ready").getAsBoolean()
                                && "observer_swing".equals(candidate.get("phase").getAsString())) {
                            observerReplyScene = candidate; waitingFor = null;
                        }
                    }
                }
                if (observerReplyScene != null && pendingObserverRecord != null) {
                    finalizeObserverRecord(observerReplyScene);
                }
                return;
            }
            if (waitingFor != null) {
                Path scenePath = captureDir.resolve("scene.json");
                if (!Files.isRegularFile(scenePath)) return;
                JsonObject candidate = JsonParser.parseString(Files.readString(scenePath)).getAsJsonObject();
                if (!candidate.has("ready") || !candidate.get("ready").getAsBoolean()
                        || !waitingFor.equals(candidate.get("phase").getAsString())) return;
                scene = candidate; waitingFor = null; inputSent = false; inputTick = 0;
                phaseDeadlineNanos = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(18);
                lastBlockedStage = "scene_ready"; lastBlockedDetails = new JsonObject();
                mc.options.setCameraType(scene.get("phase").getAsString().equals("fist_third")
                        ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
                if ("observer_setup".equals(scene.get("phase").getAsString())) {
                    lastSnapshot = MeleeArcClient.diagnosticSnapshot();
                    frameWatermark = lastSnapshot.frameId();
                    observerCommandSent = false; observerPeerTicks = 0;
                    armObserverSwing(mc);
                    return;
                }
                lastSnapshot = MeleeArcClient.diagnosticSnapshot(); frameWatermark = lastSnapshot.frameId();
                preparePhase(mc);
            }
            if (scene != null && !inputSent && !"observer_swing".equals(scene.get("phase").getAsString())
                    && !"observer_setup".equals(scene.get("phase").getAsString())) {
                preparePhase(mc);
            }
            if (scene != null && "observer_setup".equals(scene.get("phase").getAsString()) && !inputSent) {
                var snap = MeleeArcClient.diagnosticSnapshot();
                if (snap.observerQueued() <= lastSnapshot.observerQueued()) {
                    if (++inputTick > 300) throw new IllegalStateException("observer presentation was not delivered to the real client queue");
                    return;
                }
                inputSent = true;
            }
            if (inputSent && !screenshotPending && phase < OUTPUTS.length && phase > 0) {
                var reply = MeleeAttackClient.diagnosticLastReply().orElse(null);
                if ("disarm_first".equals(scene.get("phase").getAsString()) && reply != null
                        && expectedBody.equals(reply.bodyUUID()) && requestSequence == reply.sequence()
                        && (reply.status() == com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.DISARM_FAILED
                        || reply.status() == com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.DISARM_HANDLED)) {
                    var snapshot = MeleeArcClient.diagnosticSnapshot();
                    boolean queued = snapshot.queuedCount() > disarmQueuedWatermark;
                    boolean frame = snapshot.rendered().stream().anyMatch(a -> a.predicted() && a.body().equals(expectedBody)
                            && a.requestSequence() == requestSequence);
                    if (!queued && !frame && ++disarmReplyTicks > 30) {
                        var req = MeleeAttackClient.diagnosticLastRequest().orElse(null);
                        net.minecraft.world.entity.Entity target = req == null || req.targetUUID() == null ? null
                                : mc.level.getEntity(scene.get("peerBodyId").getAsInt());
                        if (target != null && !req.targetUUID().equals(target.getUUID())) target = null;
                        var identity = target instanceof net.minecraft.world.entity.LivingEntity living
                                ? com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.projectForActor(living).orElse(null) : null;
                        var admission = com.juicyslew.moonstation14.ms14.combat.ClientDisarmAdmission.allows(identity,
                                target instanceof net.minecraft.world.entity.LivingEntity, target == mc.player,
                                target instanceof net.minecraft.world.entity.LivingEntity living && com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isIncapacitated(living),
                                target instanceof net.minecraft.world.entity.player.Player player && player.isSpectator());
                        throw new IllegalStateException("correlated disarm reply had no local presentation; mode="+(req==null?"missing":req.attackMode())
                                +" targetUUID="+(req==null?"missing":req.targetUUID())+" targetCharacterIdentity="+identity
                                +" ClientDisarmAdmission="+admission+" replyStatus="+reply.status()+" queued="+queued+" matchingFrame="+frame);
                    }
                }
            if (phase < OUTPUTS.length - 1 && (reply == null || requestSequence <= 0 || !expectedBody.equals(reply.bodyUUID())
                        || requestSequence != reply.sequence())) inputTick++;
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private static void armObserverSwing(Minecraft mc) {
        MeleeCaptureFocusPolicy.require(mc);
        if (observerCommandSent) return;
        var peerId = scene.get("peerBodyId").getAsInt();
        var peer = UUID.fromString(scene.get("peerBodyUUID").getAsString());
        var entity = mc.level.getEntity(peerId);
        if (!(entity instanceof net.minecraft.world.entity.Mob) || !peer.equals(entity.getUUID())) {
            if (++observerPeerTicks > 300) throw new IllegalStateException("observer peer body did not synchronize as expected mob");
            return;
        }
        var pos = entity.position();
        double dx=pos.x-scene.get("peerX").getAsDouble(), dy=pos.y-scene.get("peerY").getAsDouble(), dz=pos.z-scene.get("peerZ").getAsDouble();
        var arcState = MeleeArcClient.diagnosticSnapshot();
        if (dx*dx+dy*dy+dz*dz > .0625 || arcState.queuedCount() != 0) {
            lastBlockedStage = dx*dx+dy*dy+dz*dz > .0625 ? "observer-peer-position" : "previous-arc-drain";
            lastBlockedDetails = new JsonObject(); lastBlockedDetails.addProperty("currentPeer", pos.toString());
            lastBlockedDetails.addProperty("expectedPeer", scene.get("peerX").getAsDouble()+","+scene.get("peerY").getAsDouble()+","+scene.get("peerZ").getAsDouble());
            lastBlockedDetails.addProperty("queuedCount", arcState.queuedCount());
            if (++observerPeerTicks > 300) throw new IllegalStateException("observer readiness timed out stage="+lastBlockedStage+" details="+lastBlockedDetails);
            return;
        }
        lastSnapshot = MeleeArcClient.diagnosticSnapshot(); frameWatermark = lastSnapshot.frameId();
        expectedBody = peer; expectedFirstPerson = false;
        phaseEye = mc.player.getEyePosition(); phaseLook = mc.player.getLookAngle();
        inputSent = true; observerCommandSent = true;
        observerReplyTicks = 0;
        waitingFor = "observer_swing";
        mc.getConnection().sendCommand("ms14_melee_capture observer_swing");
    }

    private static boolean hasRequiredLeafProfiles() {
        var catalog = com.juicyslew.moonstation14.ms14.combat.ModMeleeArcProfiles.clientCatalog();
        return catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_arc_fist")) != null
                && catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_arc_slash")) != null
                && catalog.get(ResourceLocation.fromNamespaceAndPath("moonstation14", "weapon_arc_thrust")) != null;
    }

    private static boolean hitPhase(String name) { return "fist_hit_near".equals(name) || "fist_hit_edge".equals(name); }

    private static void issueNextCommand(Minecraft mc) {
        MeleeCaptureFocusPolicy.require(mc);
        if (phase >= COMMANDS.length) return;
        String command = COMMANDS[phase];
        phaseDeadlineNanos = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(18);
        lastBlockedStage = "awaiting_scene"; lastBlockedDetails = new JsonObject();
        mc.getConnection().sendCommand("ms14_melee_capture " + command);
        waitingFor = command;
    }

    private static void preparePhase(Minecraft mc) {
        String name = scene.get("phase").getAsString();
        if ("observer_swing".equals(name)) return;
        JsonObject details = new JsonObject();
        details.addProperty("phase", name);
        boolean first = !"fist_third".equals(name);
        mc.options.setCameraType("fist_third".equals(name) ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
        var controlled = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedCharacterForPresentation();
        if (controlled == null || controlled.getId() != scene.get("primaryBodyId").getAsInt()
                || !controlled.getUUID().toString().equals(scene.get("primaryBodyUUID").getAsString())) { block("body", details); return; }
        details.addProperty("bodyUUID", controlled.getUUID().toString()); details.addProperty("epoch", scene.get("primaryEpoch").getAsLong());
        if (!com.juicyslew.moonstation14.ms14.combat.CombatModeClient.projectedMode()) {
            if (!com.juicyslew.moonstation14.ms14.combat.CombatModeClient.policy().pending()) com.juicyslew.moonstation14.ms14.combat.CombatModeClient.requestToggle();
            details.addProperty("projectedCombat", false); details.addProperty("combatPending", com.juicyslew.moonstation14.ms14.combat.CombatModeClient.policy().pending()); block("combat_mode", details); return;
        }
        details.addProperty("projectedCombat", true);
        var snapshot = com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.confirmedOwnedHandSnapshot();
        if (snapshot == null || !snapshot.bodyId().equals(controlled.getUUID())
                || snapshot.epoch() != scene.get("primaryEpoch").getAsLong()
                || com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.estimatedConfirmedServerTick().isEmpty()) {
            details.addProperty("handProof", snapshot == null ? "missing" : "body="+snapshot.bodyId()+",epoch="+snapshot.epoch());
            details.addProperty("serverEstimate", com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.estimatedConfirmedServerTick().isPresent());
            requestHandRefresh(); block("hand_proof", details); return;
        }
        details.addProperty("handBody", snapshot.bodyId().toString()); details.addProperty("handEpoch", snapshot.epoch()); details.addProperty("handRevision", snapshot.revision());
        details.addProperty("serverEstimate", com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.estimatedConfirmedServerTick().getAsDouble());
        var selection = com.juicyslew.moonstation14.ms14.combat.ClientMeleeWeaponResolver.resolve().orElse(null);
        if ("knife_first".equals(name)) {
            var hand = snapshot.hands().stream().filter(value -> value.id().equals(snapshot.activeHand())).findFirst().orElse(null);
            if (selection == null || selection.kind() != com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.HELD
                    || !selection.itemToken().isPresent() || hand == null
                    || !"moonstation14:kitchen_knife".equals(hand.itemId())
                    || !"HELD".equals(scene.get("expectedKind").getAsString())) { details.addProperty("selection", String.valueOf(selection)); block("selection", details); return; }
        } else if (selection == null || selection.kind() != com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.UNARMED
                || !"UNARMED".equals(scene.get("expectedKind").getAsString())) { details.addProperty("selection", String.valueOf(selection)); block("selection", details); return; }
        details.addProperty("selectionKind", selection.kind().name());
        if ("disarm_first".equals(name) || hitPhase(name)) {
            var peerUUID = UUID.fromString(scene.get("peerBodyUUID").getAsString());
            var peer = mc.level.getEntity(scene.get("peerBodyId").getAsInt());
            if (!(peer instanceof net.minecraft.world.entity.LivingEntity) || !peerUUID.equals(peer.getUUID())) {
                if (++inputTick > 300) throw new IllegalStateException("melee peer entity did not synchronize within 300 ticks");
                details.addProperty("peerEntity", String.valueOf(peer)); block("peer_tracking", details); return;
            }
            var peerPos=peer.position(); double pdx=peerPos.x-scene.get("peerX").getAsDouble(), pdy=peerPos.y-scene.get("peerY").getAsDouble(), pdz=peerPos.z-scene.get("peerZ").getAsDouble();
            if (pdx*pdx+pdy*pdy+pdz*pdz>.0625) { details.addProperty("currentPeer",peerPos.toString()); details.addProperty("expectedPeer",scene.get("peerX").getAsDouble()+","+scene.get("peerY").getAsDouble()+","+scene.get("peerZ").getAsDouble()); block("peer-position",details); return; }
        }
        var body = controlled;
        var dx = body.getX() - scene.get("bodyX").getAsDouble();
        var dy = body.getY() - scene.get("bodyY").getAsDouble();
        var dz = body.getZ() - scene.get("bodyZ").getAsDouble();
        if (dx*dx + dy*dy + dz*dz > .25) { details.addProperty("positionDelta", dx*dx+dy*dy+dz*dz); block("position", details); return; }
        String focusFailure = MeleeCaptureFocusPolicy.reason(mc.isWindowActive(), mc.screen != null, mc.mouseHandler.isMouseGrabbed());
        details.addProperty("windowActive", mc.isWindowActive()); details.addProperty("screenClass", mc.screen == null ? "none" : mc.screen.getClass().getName()); details.addProperty("mouseGrabbed", mc.mouseHandler.isMouseGrabbed());
        if (focusFailure != null) throw new IllegalStateException("validation interference: " + focusFailure);
        boolean canAttempt = MeleeAttackClient.canAttemptNow(body, scene.get("primaryEpoch").getAsLong(), selection);
        details.addProperty("canAttemptNow", canAttempt); if (!canAttempt) {
            var policy = MeleeAttackClient.policy();
            var estimate = com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.estimatedConfirmedServerTick();
            details.addProperty("pending", policy.pending()); details.addProperty("projectedDeadline", policy.projectedDeadline());
            details.addProperty("sequence", policy.sequence()); details.addProperty("localClock", com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.localClock());
            if (estimate.isPresent()) details.addProperty("serverEstimate", estimate.getAsDouble()); else details.addProperty("serverEstimate", "missing");
            var cooldown = bodyCooldown(body, selection);
            if (cooldown == null) details.addProperty("sourceCooldownDeadline", "missing"); else details.addProperty("sourceCooldownDeadline", cooldown);
            details.addProperty("clientActionBlocked", com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isClientActionBlocked(body));
            details.addProperty("screen", String.valueOf(mc.screen)); details.addProperty("windowActive", mc.isWindowActive());
            details.addProperty("mouseGrabbed", mc.mouseHandler.isMouseGrabbed());
            block("can_attempt", details); return;
        }
        var eyes = body.getEyePosition(); var look = body.getLookAngle();
        phaseEye = eyes; phaseLook = look;
        var before = MeleeAttackClient.policy().sequence();
        MeleeArcClient.DiagnosticSnapshot initial = MeleeArcClient.diagnosticSnapshot();
        frameWatermark = initial.frameId(); lastSnapshot = initial;
        expectedBody = body.getUUID(); expectedFirstPerson = first;
        int button = "disarm_first".equals(name) ? GLFW.GLFW_MOUSE_BUTTON_2 : GLFW.GLFW_MOUSE_BUTTON_1;
        long beforePress = MeleeAttackClient.policy().sequence();
        var press = new InputEvent.MouseButton.Pre(button, GLFW.GLFW_PRESS, 0);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(press);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new InputEvent.MouseButton.Pre(button, GLFW.GLFW_RELEASE, 0));
        requestSequence = MeleeAttackClient.policy().sequence();
        if (requestSequence <= before || !MeleeAttackClient.policy().pending()) {
            JsonObject blocked = new JsonObject(); blocked.addProperty("button", button); blocked.addProperty("canceled", press.isCanceled());
            blocked.addProperty("sequenceBefore", beforePress); blocked.addProperty("sequenceAfter", requestSequence);
            blocked.addProperty("pending", MeleeAttackClient.policy().pending());
            blocked.addProperty("windowActive", mc.isWindowActive()); blocked.addProperty("mouseGrabbed", mc.mouseHandler.isMouseGrabbed());
            blocked.addProperty("screen", String.valueOf(mc.screen)); blocked.addProperty("primaryBody", body.getUUID().toString());
            blocked.addProperty("epoch", scene.get("primaryEpoch").getAsLong()); blocked.addProperty("mode", com.juicyslew.moonstation14.ms14.combat.CombatModeClient.projectedMode());
            var actual = MeleeAttackClient.diagnosticLastRequest().orElse(null); blocked.addProperty("actualRequest", String.valueOf(actual));
            block("input-not-admitted", blocked); return;
        }
        phaseDeadlineNanos = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(18);
        if ("disarm_first".equals(name) || hitPhase(name)) {
            var actual = MeleeAttackClient.diagnosticLastRequest().orElse(null);
            var peerUUID = UUID.fromString(scene.get("peerBodyUUID").getAsString());
            var expectedMode = hitPhase(name) ? com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT : com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.DISARM;
            if (actual == null || actual.attackMode() != expectedMode
                    || !peerUUID.equals(actual.targetUUID())) throw new IllegalStateException("actual request not expected peer body; request="+actual+" scene="+scene);
            if (!hitPhase(name)) { disarmQueuedWatermark = initial.queuedCount(); disarmReplyTicks = 0; }
        }
        if ("wall_first".equals(name)) {
            var actual = MeleeAttackClient.diagnosticLastRequest().orElse(null);
            var nomination = actual == null ? null : actual.typedTarget();
            var pos = new net.minecraft.core.BlockPos(scene.get("targetBlockX").getAsInt(), scene.get("targetBlockY").getAsInt(), scene.get("targetBlockZ").getAsInt());
            if (actual == null || actual.attackMode() != com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.LIGHT
                    || !(nomination instanceof com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination.Block block)
                    || !pos.equals(block.pos()) || block.expectedBlockStateID() != scene.get("targetBlockStateId").getAsInt())
                throw new IllegalStateException("actual request lacked expected wall block nomination; request="+actual+" expectedPos="+pos);
        }
        inputSent = true; inputTick = 0;
        diagnosticLog("input", name, body.getUUID(), requestSequence, first, eyes, look, initial);
    }

    private static void requestHandRefresh() {
        long now = System.nanoTime();
        if (now - lastRefreshNanos >= java.util.concurrent.TimeUnit.SECONDS.toNanos(1)) {
            com.juicyslew.moonstation14.ms14.hands.client.BodyHandClient.requestConfirmedHandRefresh();
            lastRefreshNanos = now;
        }
    }

    private static Long bodyCooldown(net.minecraft.world.entity.Mob body,
            com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Selection selection) {
        if (selection.kind() == com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.Kind.UNARMED) {
            var state = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.MELEE_COOLDOWN.get());
            return state == null ? null : state.data().nextAttackTick();
        }
        var stack = com.juicyslew.moonstation14.ms14.combat.ClientMeleeWeaponResolver.presentationStack(selection).orElse(null);
        var data = stack == null ? null : stack.get(com.juicyslew.moonstation14.component.ModDataComponents.MELEE_COOLDOWN.get());
        return data == null ? null : data.nextAttackTick();
    }

    private static void block(String stage, JsonObject details) {
        if (!stage.equals(lastBlockedStage)) System.out.println("MS14_MELEE_WAIT phase="+(scene == null ? waitingFor : scene.get("phase").getAsString())+" stage="+stage+" details="+details);
        lastBlockedStage = stage; lastBlockedDetails = details.deepCopy();
    }

    @SubscribeEvent public static void postFrame(RenderFrameEvent.Post event) {
        if (!ENABLED || done || !inputSent || screenshotPending || phase >= OUTPUTS.length) return;
        try {
            var snapshot = MeleeArcClient.diagnosticSnapshot();
            if (!hasRequiredLeafProfiles()) throw new IllegalStateException("required melee arc leaf profiles are unavailable");
            boolean observer = scene != null && "observer_setup".equals(scene.get("phase").getAsString()) && observerCommandSent;
            String phaseName = scene == null ? "" : scene.get("phase").getAsString();
            if (hitPhase(phaseName)) {
                var reply = MeleeAttackClient.diagnosticLastReply().orElse(null);
                if (reply == null || !expectedBody.equals(reply.bodyUUID()) || requestSequence != reply.sequence()
                        || reply.status() != com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.HIT) return;
                var target = Minecraft.getInstance().level.getEntity(scene.get("peerBodyId").getAsInt());
                if (!(target instanceof net.minecraft.world.entity.LivingEntity) || !target.getUUID().toString().equals(scene.get("peerBodyUUID").getAsString())) return;
                if (!com.juicyslew.moonstation14.ms14.damage.client.DamageFeedbackClientState.feedbackActive(target)
                        && ((net.minecraft.world.entity.LivingEntity)target).hurtTime <= 0) return;
            }
            if ("wall_first".equals(phaseName)) {
                var reply = MeleeAttackClient.diagnosticLastReply().orElse(null);
                if (reply == null || !expectedBody.equals(reply.bodyUUID()) || requestSequence != reply.sequence()
                        || reply.epoch() != scene.get("primaryEpoch").getAsLong()) return;
            }
            var arc = snapshot.rendered().stream().filter(a -> a.body().equals(expectedBody)
                    && a.firstPerson() == expectedFirstPerson && a.alpha() > 0
                    && (observer ? !a.predicted() : a.predicted() && a.requestSequence() == requestSequence)).findFirst().orElse(null);
            if (snapshot.frameId() <= frameWatermark || arc == null) return;
            screenshotPending = true;
            captureScreenshot(snapshot, arc);
        } catch (Throwable failure) { fail(failure); }
    }

    private static void captureScreenshot(MeleeArcClient.DiagnosticSnapshot snapshot, MeleeArcClient.DiagnosticArcFrame arc) {
        var mc = Minecraft.getInstance();
        final String output = OUTPUTS[phase] + ".png";
        final Path destination = captureDir.resolve(output);
        MeleeDiagnosticScreenshotPublisher.publish(mc, destination, result -> {
            try {
                JsonObject record = new JsonObject(); record.addProperty("phase", OUTPUTS[phase]);
                record.addProperty("scenePhase", scene.get("phase").getAsString()); record.addProperty("body", arc.body().toString());
                record.addProperty("sequence", arc.requestSequence()); record.addProperty("predicted", arc.predicted());
                record.addProperty("firstPerson", arc.firstPerson()); record.addProperty("alpha", arc.alpha());
                record.addProperty("cameraType", mc.options.getCameraType().name());
                record.addProperty("renderFirstPerson", arc.firstPerson());
                record.addProperty("texture", arc.texture().toString()); record.addProperty("renderFrame", snapshot.frameId());
                record.addProperty("queued", snapshot.queuedCount()); record.addProperty("profiles", snapshot.profileCount());
                record.addProperty("timestamp", Instant.now().toString()); record.addProperty("png", output);
                  record.addProperty("sha256", result.sha256());
                   record.add("serverScene", scene.deepCopy());
                   if (observerCommandSent) { var eid=scene.get("peerBodyId").getAsInt(); var e=mc.level.getEntity(eid); if(e!=null) record.addProperty("peerCapturePosition",e.position().toString()); record.addProperty("peerUUID",scene.get("peerBodyUUID").getAsString()); record.addProperty("peerExpectedPosition",scene.get("peerX").getAsDouble()+","+scene.get("peerY").getAsDouble()+","+scene.get("peerZ").getAsDouble()); record.addProperty("cameraEye",mc.gameRenderer.getMainCamera().getPosition().toString()); record.addProperty("observerArmedAt",Instant.now().toString()); }
                    if ("disarm_first".equals(scene.get("phase").getAsString())) { var req=MeleeAttackClient.diagnosticLastRequest().orElse(null); if(req!=null){record.addProperty("requestMode",req.attackMode().name()); record.addProperty("requestTargetUUID",String.valueOf(req.targetUUID()));} }
                    if (hitPhase(scene.get("phase").getAsString())) { var target=mc.level.getEntity(scene.get("peerBodyId").getAsInt()); var req=MeleeAttackClient.diagnosticLastRequest().orElse(null); record.addProperty("requestMode",req==null?"missing":req.attackMode().name()); record.addProperty("requestTargetUUID",req==null?"missing":String.valueOf(req.targetUUID())); record.addProperty("peerUUID",target==null?"missing":target.getUUID().toString()); if(target!=null) record.addProperty("peerCapturePosition",target.position().toString()); if(target instanceof net.minecraft.world.entity.LivingEntity living){record.addProperty("targetHurtTime",living.hurtTime); record.addProperty("targetFeedbackActive",com.juicyslew.moonstation14.ms14.damage.client.DamageFeedbackClientState.feedbackActive(target)); record.addProperty("targetRedFlashProgress",com.juicyslew.moonstation14.ms14.damage.client.DamageFeedbackClientState.redFlashProgress(target));} }
                    if ("wall_first".equals(scene.get("phase").getAsString())) { var req=MeleeAttackClient.diagnosticLastRequest().orElse(null); var nomination=req==null?null:req.typedTarget(); record.addProperty("requestMode",req==null?"missing":req.attackMode().name()); if(nomination instanceof com.juicyslew.moonstation14.ms14.combat.MeleeTargetNomination.Block block){record.addProperty("nominationType","Block"); record.addProperty("blockX",block.pos().getX()); record.addProperty("blockY",block.pos().getY()); record.addProperty("blockZ",block.pos().getZ()); record.addProperty("blockFace",block.face().name()); record.addProperty("hitpoint",block.hitpoint().toString()); record.addProperty("blockStateId",block.expectedBlockStateID());} else record.addProperty("nominationType",String.valueOf(nomination)); }
                  var reply = MeleeAttackClient.diagnosticLastReply().orElse(null);
                  if (reply != null) { record.addProperty("replyStatus", reply.status().name()); record.addProperty("replySequence", reply.sequence()); }
                  if (phaseEye != null) { record.addProperty("eye", phaseEye.toString()); record.addProperty("look", phaseLook.toString()); record.addProperty("cameraPosition", mc.gameRenderer.getMainCamera().getPosition().toString()); record.addProperty("cameraLook", mc.gameRenderer.getMainCamera().getLookVector().toString()); }
                   record.addProperty("screenshotSource", result.sourceName()); record.addProperty("captureRequestedAt", result.requestedAt().toString());
                   record.addProperty("captureCompletedAt", result.completedAt().toString());
                 if (observerCommandSent) {
                     pendingObserverRecord = record;
                     if (observerReplyScene != null) finalizeObserverRecord(observerReplyScene);
                     return;
                }
                 records.add(record); diagnosticLog("captured", OUTPUTS[phase], arc.body(), arc.requestSequence(), arc.firstPerson(), phaseEye, phaseLook, snapshot);
                 screenshotPending = false; inputSent = false; phase++;
                 if (observerCommandSent) complete();
                else { awaitingReply = true; inputTick = 0; }
            } catch (Throwable failure) { fail(failure); }
        }, MeleePresentationClientDiagnostic::fail);
    }

    private static void finalizeObserverRecord(JsonObject replyScene) throws Exception {
        if (!replyScene.has("phase") || !"observer_swing".equals(replyScene.get("phase").getAsString()))
            throw new IllegalStateException("observer swing reply scene was not available after PNG capture");
        if (!replyScene.has("replyStatus") || !"MISS".equals(replyScene.get("replyStatus").getAsString()))
            throw new IllegalStateException("observer swing reply status was not MISS");
        var seqField = replyScene.has("replySeq") ? replyScene.get("replySeq") : replyScene.has("replySequence") ? replyScene.get("replySequence") : replyScene.get("seq");
        if (seqField == null) throw new IllegalStateException("observer scene lacks authoritative reply sequence");
        pendingObserverRecord.addProperty("replyStatus", replyScene.get("replyStatus").getAsString());
        pendingObserverRecord.addProperty("replySequence", seqField.getAsLong());
        records.add(pendingObserverRecord);
        pendingObserverRecord = null;
        screenshotPending = false; inputSent = false; scene = null; phase++;
        complete();
    }

    private static void diagnosticLog(String action, String name, UUID body, long sequence, boolean first,
                                      net.minecraft.world.phys.Vec3 eyes, net.minecraft.world.phys.Vec3 look,
                                      MeleeArcClient.DiagnosticSnapshot snapshot) {
        System.out.println("MS14_MELEE_PHASE action="+action+" phase="+name+" body="+body+" requestSequence="+sequence
                +" camera="+(first ? "FIRST_PERSON" : "THIRD_PERSON_BACK")+" eye="+eyes+" look="+look
                +" frame="+(snapshot==null ? 0 : snapshot.frameId())+" catalog="+(snapshot==null ? 0 : snapshot.profileCount()));
    }
    private static void complete() throws Exception {
        JsonObject manifest = new JsonObject(); manifest.addProperty("protocol", 1); manifest.addProperty("status", "COMPLETE");
        manifest.addProperty("ownerPhysicalInput", false); manifest.addProperty("inputBoundary", "NeoForge MouseButton.Pre posted on production event bus; not OS hardware input");
        manifest.add("allowedDisarmReplyStatuses", new com.google.gson.Gson().toJsonTree(List.of("DISARM_FAILED", "DISARM_HANDLED")));
        manifest.add("phases", new com.google.gson.Gson().toJsonTree(records));
        Path temp = captureDir.resolve("manifest.json.tmp"), output = captureDir.resolve("manifest.json");
        Files.writeString(temp, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(manifest));
        Files.move(temp, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        JsonObject marker = new JsonObject(); marker.addProperty("status", "COMPLETE"); marker.addProperty("phaseCount", records.size());
        Files.writeString(captureDir.resolve("melee-capture-complete.json"), marker.toString());
        System.out.println("MS14_MELEE_CAPTURE_COMPLETE phaseCount="+records.size()+" manifest="+output);
        done = true;
    }
    private static void fail(Throwable failure) {
        if (done) return; done = true;
        var mc = Minecraft.getInstance();
        try { if (mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.releaseMouse(); } catch (Throwable ignored) { }
        try { if (captureDir != null) { JsonObject marker = new JsonObject(); marker.addProperty("status", "FAILED"); marker.addProperty("reason", failure.toString()); marker.addProperty("phaseCount", records.size()); marker.addProperty("windowActive",mc.isWindowActive()); marker.addProperty("screenClass",mc.screen==null?"none":mc.screen.getClass().getName()); marker.addProperty("mouseGrabbed",mc.mouseHandler.isMouseGrabbed()); marker.addProperty("focusReason",MeleeCaptureFocusPolicy.reason(mc)); Files.writeString(captureDir.resolve("melee-capture-failed.json"), marker.toString()); } }
        catch (Exception ignored) { }
        System.err.println("MS14_MELEE_CAPTURE_FAILED reason="+failure);
        failure.printStackTrace();
    }
}
