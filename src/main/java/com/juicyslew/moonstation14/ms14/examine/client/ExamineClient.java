package com.juicyslew.moonstation14.ms14.examine.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.juicyslew.moonstation14.ms14.examine.*;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

import java.util.*;

/** Client intent, local visual selection and non-modal authoritative result presentation. */
public final class ExamineClient {
    public record Authority(UUID actor, long epoch, Entity body) { }
    private static final ExamineCandidateCycle CYCLE = new ExamineCandidateCycle();
    private static final ExamineLatchSession LATCH = new ExamineLatchSession();
    private static final ExamineClientPolicy.Popup POPUP = new ExamineClientPolicy.Popup();
    private static final ExamineRefreshPolicy REFRESH = new ExamineRefreshPolicy();
    private static List<ExamineSelector.Candidate> visible = List.of();
    private static ExamineSelector.Candidate selected;
    private static long requestSequence, lastRequestTick = Long.MIN_VALUE;
    private static ExamineTarget requestedTarget;
    private static Authority requestedAuthority;
    private static ExamineResponse response;
    private static ExamineControllerIdentity.Binding pendingIdentity, acceptedIdentity;
    private static UUID acceptedControllerIncarnation;
    private static UUID pendingControllerIncarnation;
    private static boolean pendingAllowsInitialIncarnation;
    private static int pointerX, pointerY;
    private static int popupX, popupY, popupWidth, popupHeight;
    private static int popupScrollOffset, popupVisibleLines;
    private static List<Component> popupScrollContent = List.of();
    private static List<net.minecraft.util.FormattedCharSequence> popupWrappedLines = List.of();
    private static int popupScrollWidth = -1, popupScrollAvailable = -1;
    private static ExamineTarget popupScrollTarget;
    private static boolean popupScrollDetailsAllowed;
    private static boolean popupDismissed;
    private static boolean intent;
    private static boolean shiftWasDown;
    private static boolean lastLocalDetailsAllowed;
    private static boolean detailGateInitialized;
    private static long detailRetryAt;
    private static org.joml.Matrix4f frameProjection;

    private ExamineClient() { }

    public static void install() { ExamineNetworking.setClientHandler(ExamineClient::accept); }

    public static Authority authority() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return null;
        var observer = com.juicyslew.moonstation14.ms14.medical_observer.client.ObserverClient.current();
        if (observer != null && observer.mode() != com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.END
                && observer.epoch() > 0) {
            Entity role = observer.mode() == com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.GHOST
                    ? mc.level.getEntity(observer.ghostEntity())
                    : com.juicyslew.moonstation14.ms14.medical_observer.client.ObserverClient.statusBody();
            UUID identity = observer.mode() == com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.GHOST
                    ? observer.ghost() : observer.body();
            if (role != null && !role.isRemoved() && role.level() == mc.level && identity != null
                    && role.getUUID().equals(identity)) return new Authority(identity, observer.epoch(), role);
        }
        Mob body = GhostControlClient.ownedCharacterForPresentation();
        long epoch = GhostControlClient.presentationCharacterEpoch();
        if (body != null && epoch > 0 && body.level() == mc.level) return new Authority(body.getUUID(), epoch, body);
        if (GhostControlClient.isLocalGhostControl()) {
            Entity ghost = GhostControlClient.ownedGhostForPresentation();
            var context = GhostControlClient.committedGhostContext();
            if (ghost != null && context != null && ghost.level() == mc.level
                    && ghost.getUUID().equals(context.ghost())) return new Authority(context.ghost(), context.epoch(), ghost);
        }
        return null;
    }

    public static boolean activeIntent() { return intent && selected != null; }
    public static boolean hasMultipleCandidates() { refreshSelection(false); return visible.size() > 1; }

    /** Returns true only when the exact Shift+primary gesture should be consumed. */
    public static boolean examineWorldClick() {
        if (!worldIntentAllowed() || authority() == null) return false;
        refreshSelection(false);
        intent = selected != null;
        if (selected != null) {
            popupDismissed = false; POPUP.open(selected.target(), authority().epoch());
            popupScrollOffset = 0; popupVisibleLines = 0; popupScrollContent = List.of(); popupWrappedLines = List.of();
            popupScrollTarget = null; popupScrollWidth = -1; popupScrollAvailable = -1;
            request(selected.target(), true);
        }
        return true;
    }

    /** Explicitly examine one captured loose entity, independent of the release-time gaze target. */
    public static boolean examineEntityClick(UUID entityId) {
        if (entityId == null || !worldIntentAllowed()) return false;
        Authority actor = authority();
        Minecraft mc = Minecraft.getInstance();
        Entity entity = null;
        if (mc.level != null && actor != null) {
            for (Entity candidate : mc.level.entitiesForRendering()) {
                if (entityId.equals(candidate.getUUID()) && !candidate.isRemoved() && candidate.level() == mc.level) {
                    entity = candidate;
                    break;
                }
            }
        }
        if (actor == null || entity == null || entity.isRemoved() || entity.level() != mc.level) return false;
        ExamineTarget target = ExamineTarget.entity(entity);
        ExamineSelector.Candidate candidate = new ExamineSelector.Candidate(target, entity.position(), 0,
                entity.getName().getString(), entity.getBoundingBox(), false);
        if (!candidateEligible(candidate)) return false;
        selected = candidate; intent = true; popupDismissed = false;
        POPUP.open(target, actor.epoch());
        popupScrollOffset = 0; popupVisibleLines = 0; popupScrollContent = List.of(); popupWrappedLines = List.of();
        popupScrollTarget = null; popupScrollWidth = -1; popupScrollAvailable = -1;
        request(target, true);
        return true;
    }

    public static boolean cycle(int delta) {
        if (delta == 0 || !worldIntentAllowed()) return false;
        refreshSelection(false);
        if (visible.size() < 2) return false;
        ExamineTarget target = CYCLE.update(visible, transparentAnchor(), selected == null ? null : selected.target(), delta);
        selected = visible.stream().filter(c -> c.target().equals(target)).findFirst().orElse(null);
        intent = selected != null;
        return intent;
    }

    private static boolean worldIntentAllowed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return false;
        boolean storage = mc.screen instanceof com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen;
        return ExamineClientPolicy.worldClickAllowed(mc.screen == null, storage,
                storage && com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen.pointerOverInteractiveWindow());
    }

    private static boolean localDetailsAllowed(Authority actor) {
        if (actor == null || requestedTarget == null || requestedTarget.kind() == ExamineTarget.Kind.SCENE) return false;
        return ExamineDetailPolicy.locallyAllowed(examiner(actor), state(actor.body()), isActualGhost(actor),
                actor.body().getEyePosition().distanceTo(responsePoint(requestedTarget)));
    }

    private static ExamineTarget transparentAnchor(List<ExamineSelector.Candidate> candidates) {
        return candidates.stream().filter(ExamineSelector.Candidate::transparent).map(ExamineSelector.Candidate::target)
                .findFirst().orElse(null);
    }
    private static ExamineTarget transparentAnchor() { return transparentAnchor(visible); }

    private static boolean candidateEligible(ExamineSelector.Candidate candidate) {
        Authority actor = authority();
        if (actor == null) return false;
        if (candidate.target().kind() == ExamineTarget.Kind.SCENE) {
            var level = actor.body().level();
            var data = com.juicyslew.moonstation14.ms14.examine.scene.ModCelestialScenes.catalog(level).get(candidate.target().sceneId());
            return com.juicyslew.moonstation14.ms14.examine.scene.CelestialSceneExamination.visible(level,
                    actor.body(), data, level.getTimeOfDay(1), state(actor.body()) == ExaminationPolicy.State.CRITICAL);
        }
        return actor != null && ExamineClientPolicy.eligible(examiner(actor), state(actor.body()), false, 0,
                actor.body().getEyePosition().distanceTo(ExamineTargetResolver.closestPoint(candidate.box(), actor.body().getEyePosition())));
    }

    private static ExaminationPolicy.Examiner examiner(Authority actor) {
        var observer = com.juicyslew.moonstation14.ms14.medical_observer.client.ObserverClient.current();
        boolean canonicalGhost = com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.isLocalGhostControl()
                && com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedGhostForPresentation() == actor.body();
        boolean actualGhost = canonicalGhost || ExamineClientPolicy.actualObserverGhost(observer != null
                        && observer.mode() == com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.GHOST,
                observer != null && actor.actor().equals(observer.ghost()), observer != null && observer.epoch() == actor.epoch());
        if (actualGhost) {
            var data = com.juicyslew.moonstation14.ms14.character.ModCharacters.catalog(actor.body().level())
                    .get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "observer"));
            var component = data == null ? null : data.component(com.juicyslew.moonstation14.ms14.character.components.ExaminerComponent.class).orElse(null);
            return component == null ? ExaminationPolicy.Examiner.DEFAULT
                    : new ExaminationPolicy.Examiner(component.skipChecks(), component.checkInRangeUnOccluded());
        }
        var data = actor.body() instanceof net.minecraft.world.entity.LivingEntity living
                ? com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.projectForActor(living).orElse(null) : null;
        var component = data == null ? null : data.component(com.juicyslew.moonstation14.ms14.character.components.ExaminerComponent.class).orElse(null);
        return component == null ? ExaminationPolicy.Examiner.DEFAULT
                : new ExaminationPolicy.Examiner(component.skipChecks(), component.checkInRangeUnOccluded());
    }
    private static boolean isActualGhost(Authority actor) {
        var observer = com.juicyslew.moonstation14.ms14.medical_observer.client.ObserverClient.current();
        return com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.isLocalGhostControl()
                && com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient.ownedGhostForPresentation() == actor.body()
                || ExamineClientPolicy.actualObserverGhost(observer != null
                        && observer.mode() == com.juicyslew.moonstation14.ms14.medical_observer.ObserverPayloads.Mode.GHOST,
                observer != null && actor.actor().equals(observer.ghost()), observer != null && observer.epoch() == actor.epoch());
    }
    private static ExaminationPolicy.State state(Entity entity) {
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) return ExaminationPolicy.State.ALIVE;
        var attachment = living.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.MOB_STATE.get());
        var value = attachment == null ? com.juicyslew.moonstation14.ms14.mobs.MobStateData.DEFAULT.state() : attachment.data().state();
        return switch (value) {
            case ALIVE -> ExaminationPolicy.State.ALIVE;
            case CRITICAL -> ExaminationPolicy.State.CRITICAL;
            case DEAD -> ExaminationPolicy.State.DEAD;
        };
    }
    private static boolean candidateInRange(Authority actor, ExamineTarget target) {
        if (target.kind() == ExamineTarget.Kind.SCENE) {
            var level = actor.body().level();
            var profile = com.juicyslew.moonstation14.ms14.examine.scene.ModCelestialScenes.catalog(level).get(target.sceneId());
            return com.juicyslew.moonstation14.ms14.examine.scene.CelestialSceneExamination.visible(level, actor.body(),
                    profile, level.getTimeOfDay(1), state(actor.body()) == ExaminationPolicy.State.CRITICAL);
        }
        Vec3 point = responsePoint(target);
        double distance = actor.body().getEyePosition().distanceTo(point);
        return ExamineClientPolicy.eligible(examiner(actor), state(actor.body()), false, 0, distance);
    }

    public static void refreshSelection(boolean updatePointer) {
        Minecraft mc = Minecraft.getInstance();
        Authority actor = authority();
        if (mc.level == null || actor == null) { clear(); return; }
        if (mc.screen != null && !(mc.screen instanceof com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen)) {
            visible = List.of(); selected = null; CYCLE.clear(); intent = false; return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        boolean pointerRay = mc.screen instanceof com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen
                && !mc.mouseHandler.isMouseGrabbed();
        if (pointerRay) {
            pointerX = (int) (mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth());
            pointerY = (int) (mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight());
        } else if (updatePointer) {
            pointerX = mc.getWindow().getGuiScaledWidth() / 2;
            pointerY = mc.getWindow().getGuiScaledHeight() / 2;
        }
        Vec3 origin = camera.getPosition();
        Vector3f look = camera.getLookVector();
        Vec3 direction = new Vec3(look.x, look.y, look.z);
        if (pointerRay) direction = pointerDirection(camera, pointerX, pointerY, mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight());
        Set<Integer> ignored = new HashSet<>();
        ignored.add(actor.body().getId()); ignored.add(mc.player.getId());
        Entity cameraEntity = mc.getCameraEntity(); if (cameraEntity != null) ignored.add(cameraEntity.getId());
        List<ExamineSelector.Candidate> next = ExamineSelector.select(mc.level, origin, direction,
                ExamineSelector.MAX_VISIBLE_RANGE, ignored).stream().filter(ExamineClient::candidateEligible).toList();
        // Scene fallback is deliberately considered only after the eligible world query has no result.
        if (ExamineClientPolicy.sceneFallbackAllowed(next)) {
            var catalog = com.juicyslew.moonstation14.ms14.examine.scene.ModCelestialScenes.catalog(mc.level);
            double time = mc.level.getTimeOfDay(1);
            for (var id : List.of(com.juicyslew.moonstation14.ms14.examine.scene.ModCelestialScenes.SUN,
                    com.juicyslew.moonstation14.ms14.examine.scene.ModCelestialScenes.EARTH)) {
                var data = catalog.get(id);
                if (data == null || !com.juicyslew.moonstation14.ms14.examine.scene.CelestialSceneGeometry.available(
                        mc.level, data, state(actor.body()) == ExaminationPolicy.State.CRITICAL)) continue;
                Vec3 hit = com.juicyslew.moonstation14.ms14.examine.scene.CelestialSceneGeometry.intersectCameraRay(
                        data, time, origin, direction);
                if (hit == null) continue;
                if (!com.juicyslew.moonstation14.ms14.examine.scene.CelestialSceneExamination.rayVisibility(
                        mc.level, origin, direction).visible()) continue;
                if (!com.juicyslew.moonstation14.ms14.examine.scene.CelestialSceneExamination.visible(mc.level,
                        actor.body(), data, time, state(actor.body()) == ExaminationPolicy.State.CRITICAL)) continue;
                next = List.of(new ExamineSelector.Candidate(ExamineTarget.scene(id), null, Double.NaN,
                        data.metadata().name(), null, false));
                break;
            }
        }
        long window = mc.getWindow().getWindow();
        boolean shift = com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean control = com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean alt = com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT);
        if (shift && !shiftWasDown) CYCLE.resetAutomatic();
        shiftWasDown = shift;
        ExamineTarget cycled = CYCLE.update(next, transparentAnchor(next), selected == null ? null : selected.target(), 0);
        selected = cycled == null ? null : next.stream().filter(c -> c.target().equals(cycled)).findFirst().orElse(null);
        visible = next;
        intent = selected != null && shift && !control && !alt && worldIntentAllowed();
        if (POPUP.target() != null) {
            boolean valid = POPUP.valid(requestedTarget, actor.epoch(), requestedTarget != null
                    && candidateInRange(actor, requestedTarget));
            if (!valid) { popupDismissed = true; clearResponseState(); }
        }
        if (requestedTarget != null && (!sameAuthority(actor, requestedAuthority)
                || !sameLocalIdentity(response != null ? acceptedIdentity : pendingIdentity, captureIdentity(requestedTarget))
                || !candidateInRange(actor, requestedTarget))) {
            clearResponseState();
        }
    }

    private static Vec3 pointerDirection(Camera camera, int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        double nx = 2.0 * x / Math.max(1, width) - 1.0;
        double ny = 1.0 - 2.0 * y / Math.max(1, height);
        Vector3f vector;
        if (frameProjection != null) {
            var inverse = new org.joml.Matrix4f(frameProjection).invert();
            var view = inverse.transform(new org.joml.Vector4f((float) nx, (float) ny, -1, 1));
            vector = new Vector3f(view.x / view.w, view.y / view.w, view.z / view.w).normalize();
        } else {
            double fov = Math.toRadians(mc.options.fov().get());
            double scale = Math.tan(fov / 2.0);
            vector = new Vector3f((float) (nx * width / (double) Math.max(1, height) * scale),
                    (float) (ny * scale), -1.0f).normalize();
        }
        vector.rotate(camera.rotation());
        return new Vec3(vector.x, vector.y, vector.z).normalize();
    }

    private static void request(ExamineSelector.Candidate candidate) { request(candidate.target(), false); }

    private static void request(ExamineTarget target) { request(target, false); }
    private static void request(ExamineTarget target, boolean explicitClick) {
        if (explicitClick) REFRESH.explicitRequest();
        Authority authority = authority();
        if (authority == null || requestSequence == Long.MAX_VALUE || !candidateInRange(authority, target)) return;
        if (!explicitClick && !REFRESH.request()) return;
        ExamineControllerIdentity.Binding identity = captureIdentity(target);
        if (identity == null) { clearResponseState(); return; }
        if (!sameLocalIdentity(acceptedIdentity, identity)) {
            response = null;
            acceptedIdentity = null;
            acceptedControllerIncarnation = null;
        }
        boolean newLatch = requestedTarget == null || !requestedTarget.equals(target)
                || !sameAuthority(requestedAuthority, authority);
        if (newLatch) response = null;
        pendingIdentity = identity;
        lastRequestTick = Minecraft.getInstance().level.getGameTime();
        UUID controllerStamp = acceptedIdentity != null && sameLocalIdentity(acceptedIdentity, identity)
                ? acceptedControllerIncarnation : null;
        pendingControllerIncarnation = controllerStamp;
        pendingAllowsInitialIncarnation = identity.controllerReference() != null && controllerStamp == null;
        long id = ++requestSequence;
        if (newLatch || LATCH.target() == null) LATCH.begin(target, authority.actor(), authority.epoch(), id);
        else LATCH.refreshed(id);
        requestedTarget = target; requestedAuthority = authority;
        if (explicitClick) {
            lastLocalDetailsAllowed = localDetailsAllowed(authority);
            detailGateInitialized = true;
            detailRetryAt = 0;
        }
        ExamineNetworking.sendRequest(new ExamineRequest(id, authority.actor(), authority.epoch(),
                target, controllerStamp));
        REFRESH.sent();
        var logger = com.juicyslew.moonstation14.MoonStation14.LOGGER;
        if (explicitClick) logger.info("Examine client requestId={} actor={} epoch={} target={} action=CLICK", id, authority.actor(), authority.epoch(), target);
        else logger.debug("Examine client requestId={} actor={} epoch={} target={} action=REFRESH", id, authority.actor(), authority.epoch(), target);
    }

    public static void tick() {
        refreshSelection(false);
        if (requestedTarget != null && !popupDismissed) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || (mc.screen != null && !(mc.screen instanceof com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen))) return;
            long now = mc.level.getGameTime();
            Authority actor = authority();
            boolean allowed = localDetailsAllowed(actor);
            if (!detailGateInitialized || allowed != lastLocalDetailsAllowed) {
                lastLocalDetailsAllowed = allowed;
                detailGateInitialized = true;
                if (allowed != (response != null && response.detailsAllowed())) {
                    request(requestedTarget);
                    detailRetryAt = now + 4;
                    return;
                }
            }
            boolean serverAllows = response != null && response.detailsAllowed();
            if (ExamineClientPolicy.disagreementRefreshDue(allowed, serverAllows, REFRESH.inFlight(), now, detailRetryAt)) {
                request(requestedTarget);
                detailRetryAt = now + 4;
            } else if (ExamineClientPolicy.periodicRefreshDue(allowed, serverAllows, REFRESH.inFlight(), now, lastRequestTick)) request(requestedTarget);
        }
    }

    private static void accept(ExamineResponse incoming) {
        Minecraft mc = Minecraft.getInstance();
        String rejection = null;
        if (mc.level == null || mc.player == null || requestedAuthority == null
                || !LATCH.accepts(incoming.target(), incoming.actorUuid(), incoming.epoch(), incoming.requestId())
                || !ExamineResponseStamp.correlates(incoming, requestSequence, requestedTarget,
                requestedTarget,
                requestedAuthority.actor(), requestedAuthority.epoch())) rejection = "correlation";
        if (rejection != null) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.debug("Examine client rejection requestId={} actor={} epoch={} target={} reason={}", incoming.requestId(), incoming.actorUuid(), incoming.epoch(), incoming.target(), rejection);
            return;
        }
        // Only a reply correlated to the latest request may release the in-flight slot.
        boolean followUp = REFRESH.correlatedResponse(true);
        // A correlated denial grants no presentation content; clear the request and keep diagnostics local.
        if (!incoming.accepted()) {
            Authority actor = authority();
            if (ExamineResponseStamp.acceptsBoundary(incoming, requestSequence, requestedTarget, requestedTarget,
                    requestedAuthority.actor(), requestedAuthority.epoch(), pendingControllerIncarnation,
                    pendingAllowsInitialIncarnation)
                    && sameAuthority(actor, requestedAuthority) && sameLocalIdentity(pendingIdentity, captureIdentity(incoming.target()))) {
                acceptedIdentity = pendingIdentity;
                response = null;
                popupDismissed = true; POPUP.clear(); clearResponseState();
                com.juicyslew.moonstation14.MoonStation14.LOGGER.debug("Examine denied requestId={} target={} accepted=false", incoming.requestId(), incoming.target());
            } else {
                com.juicyslew.moonstation14.MoonStation14.LOGGER.debug("Examine client rejection requestId={} actor={} epoch={} target={} reason=stale-actor-or-target", incoming.requestId(), incoming.actorUuid(), incoming.epoch(), incoming.target());
                return;
            }
            return;
        }
        if (!ExamineResponseStamp.acceptsBoundary(incoming, requestSequence, requestedTarget, requestedTarget,
                requestedAuthority.actor(), requestedAuthority.epoch(), pendingControllerIncarnation,
                pendingAllowsInitialIncarnation)) {
            clearResponseState();
            com.juicyslew.moonstation14.MoonStation14.LOGGER.info("Examine client rejection requestId={} actor={} epoch={} target={} reason=incarnation-mismatch", incoming.requestId(), incoming.actorUuid(), incoming.epoch(), incoming.target());
            return;
        }
        Authority actor = authority();
        if (!sameAuthority(actor, requestedAuthority) || !sameLocalIdentity(pendingIdentity, captureIdentity(incoming.target()))
                || !candidateInRange(actor, incoming.target())) {
            clearResponseState();
            return;
        }
        if (pendingIdentity.controllerReference() != null) {
            if (incoming.controllerIncarnation() == null || (acceptedControllerIncarnation != null
                    && !acceptedControllerIncarnation.equals(incoming.controllerIncarnation()))) {
                clearResponseState();
                acceptedIdentity = null;
                acceptedControllerIncarnation = null;
                return;
            }
            acceptedControllerIncarnation = incoming.controllerIncarnation();
        } else if (incoming.controllerIncarnation() != null) {
            clearResponseState();
            return;
        }
        acceptedIdentity = pendingIdentity;
        response = incoming;
        if (followUp && requestedTarget != null && !popupDismissed) request(requestedTarget, false);
    }

    public static void renderGui(RenderGuiEvent.Post event) {
        refreshSelection(true);
        if (Minecraft.getInstance().options.hideGui) return;
        Minecraft mc = Minecraft.getInstance();
        var g = event.getGuiGraphics();
        if (intent && selected != null) {
            Component name = Component.literal(ExamineClientPolicy.publicPreviewName(selected));
            int nameTextWidth = mc.font.width(name);
            float previewScale = ExamineClientPolicy.previewScale(nameTextWidth, g.guiWidth());
            int previewWidth = ExamineClientPolicy.previewWidth((int)Math.ceil(nameTextWidth * previewScale), g.guiWidth());
            int previewX = ExamineClientPolicy.previewLeft(pointerX, previewWidth, g.guiWidth());
            int previewHeight = 6 + Math.max(1, (int)Math.ceil(mc.font.lineHeight * previewScale));
            int previewY = ExamineClientPolicy.previewTop(pointerY, previewHeight);
            g.fill(previewX, previewY, previewX + previewWidth, previewY + previewHeight, 0xe820252c);
            PoseStack namePose = g.pose(); namePose.pushPose(); namePose.translate(previewX + 4, previewY + 3, 0); namePose.scale(previewScale, previewScale, 1);
            g.drawString(mc.font, name, 0, 0, 0xfff2e6c5, false);
            namePose.popPose();
        }
        if (response == null || popupDismissed) return;
        List<Component> lines = new ArrayList<>();
        lines.add(response.name());
        if (!response.description().getString().isBlank()) lines.add(response.description());
        if (intent && visible.size() > 1 && selected != null) {
            int index = visible.indexOf(selected) + 1;
            lines.add(Component.literal(index + "/" + visible.size() + " · Shift+Scroll"));
        }
        Authority actor = authority();
        boolean showDetails = response != null && response.detailsAllowed() && localDetailsAllowed(actor);
        lines.addAll(ExamineClientPolicy.visibleEntryTexts(response, showDetails));
        int measuredWidth = lines.stream().mapToInt(mc.font::width).max().orElse(0);
        int width = ExamineClientPolicy.popupWidth(measuredWidth, g.guiWidth());
        int x = com.juicyslew.moonstation14.ms14.chat.client.TopRightHudLayout.examineLeft(g.guiWidth(), width);
        float scale = 0.75f;
        int availableTextWidth = Math.max(1, (width - 8) * 4 / 3);
        List<net.minecraft.util.FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) wrapped.addAll(mc.font.split(line, availableTextWidth));
        popupWrappedLines = List.copyOf(wrapped);
        int lineHeight = Math.max(1, (int) Math.ceil(mc.font.lineHeight * scale));
        int availableHeight = com.juicyslew.moonstation14.ms14.chat.client.TopRightHudLayout.examineAvailableHeight(g.guiWidth(), g.guiHeight());
        if (availableHeight <= 0) return;
        int maxLines = ExamineClientPolicy.popupVisibleRows(availableHeight, lineHeight, wrapped.size());
        int height = ExamineClientPolicy.popupHeight(availableHeight, lineHeight, maxLines);
        int y = com.juicyslew.moonstation14.ms14.chat.client.TopRightHudLayout.examineTop(g.guiWidth(), g.guiHeight());
        if (!lines.equals(popupScrollContent) || width != popupScrollWidth || availableHeight != popupScrollAvailable
                || !Objects.equals(response.target(), popupScrollTarget) || showDetails != popupScrollDetailsAllowed) {
            popupScrollOffset = 0;
            popupScrollContent = List.copyOf(lines);
            popupScrollWidth = width;
            popupScrollAvailable = availableHeight;
            popupScrollTarget = response.target();
            popupScrollDetailsAllowed = showDetails;
        }
        popupVisibleLines = Math.min(maxLines, wrapped.size());
        popupScrollOffset = ExamineClientPolicy.clampScrollOffset(popupScrollOffset, wrapped.size(), popupVisibleLines);
        popupX=x; popupY=y; popupWidth=width; popupHeight=height;
        g.fill(x, y, x + width, y + height, 0xe820252c);
        g.fill(x, y, x + width, y + 1, 0xff91b5c1);
        int pinColor = POPUP.pinned() ? 0xffffc45c : 0xffd3d7d8;
        int px = x + width - 25;
        g.fill(px + 3, y + 2, px + 8, y + 5, pinColor); // head
        g.fill(px + 4, y + 5, px + 7, y + 9, pinColor); // neck
        g.fill(px + 5, y + 9, px + 6, y + 12, pinColor); // point
        g.fill(px + 2, y + 5, px + 9, y + 6, pinColor); // crossbar
        g.drawString(mc.font, Component.literal("×"), x + width - 11, y + 3, 0xfff2e6c5, false);
        if (wrapped.size() > popupVisibleLines) {
            String cue = (popupScrollOffset + 1) + "/" + wrapped.size() + " ↕";
            cue = mc.font.plainSubstrByWidth(cue, ExamineClientPolicy.popupCueMaxWidth(width));
            g.drawString(mc.font, cue, x + 4, y + 4, 0xffb9cbd0, false);
        }
        int mouseX = (int) (mc.mouseHandler.xpos() * g.guiWidth() / mc.getWindow().getScreenWidth());
        int mouseY = (int) (mc.mouseHandler.ypos() * g.guiHeight() / mc.getWindow().getScreenHeight());
        if (ExamineClientPolicy.pinHit(mouseX, x, width) && mouseY >= y && mouseY < y + 16) {
            Component tip = Component.translatable(POPUP.pinned() ? "moonstation14.examine.unpin" : "moonstation14.examine.pin");
            int tw = mc.font.width(tip);
            int tx = Math.max(2, Math.min(mouseX + 8, g.guiWidth() - tw - 8));
            int ty = Math.max(2, mouseY - 22);
            g.fill(tx, ty, tx + tw + 8, ty + mc.font.lineHeight + 4, 0xf0101010);
            g.drawString(mc.font, tip, tx + 4, ty + 2, 0xffffffff, false);
        }
        PoseStack pose = g.pose();
        g.enableScissor(x + 2, y + 17, x + width - 2, y + height - 3);
        pose.pushPose(); pose.translate(x + 4, y + 18, 0); pose.scale(scale, scale, 1);
        int yy = 0;
        for (int i = popupScrollOffset; i < wrapped.size() && i < popupScrollOffset + popupVisibleLines; i++, yy += mc.font.lineHeight)
            g.drawString(mc.font, wrapped.get(i), 0, yy, i == 0 ? 0xfff2e6c5 : 0xffd3d7d8, false);
        pose.popPose();
        g.disableScissor();
    }

    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (event.getProjectionMatrix() != null) frameProjection = new org.joml.Matrix4f(event.getProjectionMatrix());
        refreshSelection(false);
        ExamineTarget outlineTarget = !popupDismissed ? POPUP.target() : null;
        ExamineTarget previewTarget = selected != null && intent ? selected.target() : null;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        if (outlineTarget != null) drawOutline(event, mc, outlineTarget, 0.25f, 0.85f, 0.95f);
        if (ExamineClientPolicy.drawSeparatePreview(outlineTarget, previewTarget))
            drawOutline(event, mc, previewTarget, 0.95f, 0.75f, 0.3f);
    }
    private static void drawOutline(RenderLevelStageEvent event, Minecraft mc, ExamineTarget target, float red, float green, float blue) {
        if (target.kind() == ExamineTarget.Kind.SCENE) return; // Sky silhouettes have no world-space outline.
        net.minecraft.world.phys.AABB outlineBox = targetBounds(target);
        if (outlineBox == null) return;
        if (target.kind() == ExamineTarget.Kind.ENTITY) {
            Entity entity = mc.level.getEntity(target.entityId());
            if (entity == null || entity.isRemoved() || !entity.getUUID().equals(target.entityUuid())) return;
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            outlineBox = outlineBox.move(entity.getPosition(partialTick).subtract(entity.position()));
        }
        var buffers = mc.renderBuffers().bufferSource();
        var lines = buffers.getBuffer(net.minecraft.client.renderer.RenderType.lines());
        // Shape box is already a clipped candidate's visible shape; retain depth testing to avoid through-wall outlines.
        PoseStack pose = event.getPoseStack();
        var box = outlineBox.move(-event.getCamera().getPosition().x, -event.getCamera().getPosition().y,
                -event.getCamera().getPosition().z).inflate(0.01);
        net.minecraft.client.renderer.LevelRenderer.renderLineBox(pose, lines, box, red, green, blue, 0.9f);
        buffers.endBatch(net.minecraft.client.renderer.RenderType.lines());
    }

    private static Vec3 responsePoint(ExamineTarget target) {
        var mc = Minecraft.getInstance();
        Authority actor = authority();
        var bounds = targetBounds(target);
        if (actor == null || bounds == null) return new Vec3(Double.MAX_VALUE, 0, 0);
        return ExamineTargetResolver.closestPoint(bounds, actor.body().getEyePosition());
    }
    private static net.minecraft.world.phys.AABB targetBounds(ExamineTarget target) {
        var mc = Minecraft.getInstance();
        return com.juicyslew.moonstation14.ms14.examine.ExamineWorldSelector.currentBounds(mc.level, target);
    }
    private static ExamineControllerIdentity.Binding captureIdentity(ExamineTarget target) {
        Minecraft mc = Minecraft.getInstance();
        if (target.kind() == ExamineTarget.Kind.SCENE)
            return mc.level == null ? null : new ExamineControllerIdentity.Binding(target, null, null,
                    mc.level.dimension().location() + "/" + target.sceneId());
        if (target.kind() == ExamineTarget.Kind.ENTITY) {
            var entity = mc.level == null ? null : mc.level.getEntity(target.entityId());
            return entity != null && !entity.isRemoved() && entity.level() == mc.level && entity.getUUID().equals(target.entityUuid())
                    ? new ExamineControllerIdentity.Binding(target, null, null, null) : null;
        }
        var pos = target.blockPos();
        if (mc.level == null || !mc.level.hasChunkAt(pos)) return null;
        var state = mc.level.getBlockState(pos);
        if (state.isAir()) return null;
        var blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        var blockEntity = mc.level.getBlockEntity(pos);
        String stateSignature = blockEntity == null ? state.toString() : null;
        return new ExamineControllerIdentity.Binding(target, blockEntity, blockId, stateSignature);
    }
    private static boolean sameLocalIdentity(ExamineControllerIdentity.Binding a, ExamineControllerIdentity.Binding b) {
        return ExamineControllerIdentity.same(a, b);
    }
    private static void clearResponseState() {
        POPUP.clear();
        response = null;
        requestedTarget = null;
        requestedAuthority = null;
        LATCH.clear();
        pendingIdentity = null;
        acceptedIdentity = null;
        acceptedControllerIncarnation = null;
        pendingControllerIncarnation = null;
        pendingAllowsInitialIncarnation = false;
        REFRESH.reset();
        detailGateInitialized = false;
        detailRetryAt = 0;
        popupScrollOffset = 0; popupVisibleLines = 0; popupScrollContent = List.of(); popupWrappedLines = List.of();
        popupScrollWidth = -1; popupScrollAvailable = -1; popupScrollTarget = null; popupScrollDetailsAllowed = false;
    }
    private static boolean sameAuthority(Authority a, Authority b) {
        return a != null && b != null && a.epoch() == b.epoch() && a.actor().equals(b.actor()) && a.body() == b.body();
    }
    public static void clear() {
        visible = List.of(); selected = null; clearResponseState();
        CYCLE.clear(); intent = false; popupDismissed = false;
    }

    public static boolean hasPopup() { return POPUP.target() != null && !popupDismissed; }
    /** A correlated authoritative world mutation may invalidate the currently latched block's cached details. */
    public static void worldMutationConfirmed(net.minecraft.core.BlockPos position) {
        if (position == null || requestedTarget == null || popupDismissed
                || requestedTarget.kind() != ExamineTarget.Kind.BLOCK || !requestedTarget.blockPos().equals(position)) return;
        request(requestedTarget, false);
    }
    public static boolean mouseClick(double x, double y, int button) {
        Minecraft mc = Minecraft.getInstance();
        boolean storageHost = mc.screen instanceof com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen;
        if (!ExamineClientPolicy.popupClickAllowed(mc.screen == null, storageHost)) return false;
        if (!hasPopup() || button != 0) return false;
        boolean inside = x >= popupX && x < popupX + popupWidth && y >= popupY && y < popupY + popupHeight;
        if (!inside) {
            if (POPUP.outsideClick()) { dismiss(); return true; }
            return false;
        }
        if (ExamineClientPolicy.closeHit(x, popupX, popupWidth) && y < popupY + 16) dismiss();
        else if (ExamineClientPolicy.pinHit(x, popupX, popupWidth) && y < popupY + 16)
            POPUP.pin(!POPUP.pinned());
        return true;
    }
    public static boolean mouseScroll(double x, double y, double delta) {
        if (delta == 0 || !hasPopup() || popupVisibleLines <= 0 || popupWrappedLines.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        boolean storageHost = mc.screen instanceof com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen;
        if (!ExamineClientPolicy.popupClickAllowed(mc.screen == null, storageHost)) return false;
        if (storageHost && com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen.pointerOverInteractiveWindow()) return false;
        if (x < popupX || x >= popupX + popupWidth || y < popupY || y >= popupY + popupHeight) return false;
        int next = ExamineClientPolicy.scrollOffset(popupScrollOffset, delta > 0 ? -1 : 1,
                popupWrappedLines.size(), popupVisibleLines);
        if (next == popupScrollOffset) return false;
        popupScrollOffset = next;
        return true;
    }
    public static boolean escape() { if (!hasPopup() || !POPUP.escape()) return false; dismiss(); return true; }
    public static void dismiss() { popupDismissed = true; detailGateInitialized = false; detailRetryAt = 0; POPUP.clear(); clearResponseState(); }
}

