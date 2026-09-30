package com.juicyslew.moonstation14.ms14.chat.client;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.chat.network.LocalCharacterIdentityPayload;
import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload;
import com.juicyslew.moonstation14.ms14.player_body_control.client.GhostControlClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Collections;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Presents only received server speech; never derives character names from a game profile. */
public final class LocalSpeechClient {
    private static final LocalSpeechTranscript TRANSCRIPT = new LocalSpeechTranscript();
    private static ClientLevel world;
    private static String localName;
    private static final boolean DEBUG_BUBBLES = Boolean.getBoolean("moonstation14.debugSpeechBubbles");
    private static long lastBubbleDiagnostic;
    static final java.util.Map<UUID, Boolean> nearCalloutState = new HashMap<>();
    private static final List<QueuedCallout> callouts = new ArrayList<>();
    private static long calloutFrameAt;
    private static final List<CloseSpeechCallout.Rect> calloutRects = new ArrayList<>();
    private record QueuedCallout(LocalSpeechTranscript.Line line, CloseSpeechCallout.Point anchor,
                                 CloseSpeechCallout.Rect rect, long captured, int width, int height,
                                 int panelWidth, int panelHeight,
                                 boolean uVisible) { }

    private LocalSpeechClient() { }

    public static void clear() {
        LocalSpeechReviewOverlay.clear();
        DirectionalCueOverlay.clear();
        TRANSCRIPT.clear();
        world = null;
        localName = null;
        lastBubbleDiagnostic = 0;
        nearCalloutState.clear();
        callouts.clear();
        calloutRects.clear();
        calloutFrameAt = 0;
    }

    public static void tick() {
        ClientLevel current = Minecraft.getInstance().level;
        if (current != world) {
            clear();
            world = current;
        }
        if (current != null) TRANSCRIPT.expire(current.dimension(), System.currentTimeMillis());
    }

    public static void receive(CustomPacketPayload packet) {
        if (packet instanceof LocalCharacterIdentityPayload identity) {
            Minecraft minecraft = Minecraft.getInstance();
            ClientLevel level = minecraft.level;
            // Ready is sent only with a bound world. Never stage an identity across worlds/logins.
            if (level == null || minecraft.player == null || !identity.inDimension(level.dimension().location())) return;
            if (level != world) {
                clear();
                world = level;
            }
            localName = identity.name();
            return;
        }
        if (!(packet instanceof LocalSpeechPayload payload)) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || !payload.inDimension(level.dimension().location())) return;
        if (level != world) {
            clear();
            world = level;
        }
        long now = System.currentTimeMillis();
        boolean muffled = payload.mode() == LocalSpeechPayload.Mode.W_MUFFLED;
        Entity speaker = muffled ? null : level.getEntity(payload.speakerEntityId());
        // Controlled bodies may be camera-owned but absent from the normal tracked-entity lookup.
        if (!muffled && speaker == null) {
            Entity owned = GhostControlClient.ownedCharacterForHud();
            if (owned != null && owned.getId() == payload.speakerEntityId()) speaker = owned;
        }
        // Only associate a bubble with an actual entity in this world, at the
        // packet's authored position (not a reused entity ID).
        boolean resolved = matches(speaker, level, payload.speakerUuid(), payload.x(), payload.y(), payload.z());
        if (!muffled && resolved && speaker == GhostControlClient.ownedCharacterForHud()) localName = payload.actualName();
        acceptSpeech(TRANSCRIPT, payload, level.dimension(), resolved ? speaker.getUUID() : null,
                !muffled && speaker != null && !resolved, now);
        LocalSpeechTranscript.Line line = TRANSCRIPT.history().getLast();
        LocalSpeechReviewOverlay.onSpeech(line);

        if (muffled) {
            admitCue(payload, line, null, localName, false, false, now);
            return;
        }
        boolean self = minecraft.getCameraEntity() != null
                && sameSpeaker(payload.speakerEntityId(), payload.speakerUuid(),
                        minecraft.getCameraEntity().getId(), minecraft.getCameraEntity().getUUID())
                || resolved && speaker == GhostControlClient.ownedCharacterForHud();
        if (self || speaker != null && !resolved) return;
        // Use the authored position, as with fallback bubbles. Clip only when every
        // crossed chunk is loaded and the target is in the bubble's range.
        Vec3 targetEye = new Vec3(line.x(), line.y() + 1.5, line.z());
        Vec3 cameraEye = minecraft.gameRenderer.getMainCamera().getPosition();
        boolean bubbleVisible = bubbleVisibleAtReceipt(cameraEye, targetEye, resolved, line.mode(),
                (from, to) -> loadedSightline(level, from, to),
                (from, to) -> level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, minecraft.player)).getType() == net.minecraft.world.phys.HitResult.Type.MISS);
        admitCue(payload, line, resolved ? speaker.getUUID() : line.speakerUuid(), localName,
                false, bubbleVisible, now);
    }

    static boolean bubbleVisibleAtReceipt(Vec3 cameraEye, Vec3 targetEye, boolean resolved,
                                          LocalSpeechPayload.Mode mode, BiPredicate<Vec3, Vec3> loaded,
                                          BiPredicate<Vec3, Vec3> unobstructed) {
        return cameraEye.distanceToSqr(targetEye) <= (resolved || mode == LocalSpeechPayload.Mode.SHOUT
                ? 24 * 24 : 15 * 15) && loaded.test(cameraEye, targetEye)
                && unobstructed.test(cameraEye, targetEye);
    }

    /** Admission is based only on received, server-authored speech; no profile-name fallback. */
    static void admitCue(LocalSpeechPayload payload, LocalSpeechTranscript.Line line, UUID resolvedUuid,
                         String localName, boolean self, boolean bubbleVisibleAtReceipt, long now) {
        long lifetime = LocalSpeechTranscript.bubbleLifetime(payload.text());
        if (line.mode() == LocalSpeechPayload.Mode.W_MUFFLED) {
            DirectionalCueOverlay.enqueueMuffled(payload.speakerEntityId(), payload.speakerUuid(),
                    payload.azimuthSector(), payload.verticalBand(), payload.distanceTier(), lifetime, now);
        } else if (!self) {
            DirectionalCueOverlay.enqueueClear(line.speakerId(), resolvedUuid,
                    new Vec3(line.x(), line.y() + 1.5, line.z()), line.mentions(localName),
                    bubbleVisibleAtReceipt, payload.mode(), lifetime, now);
        }
    }

    static MutableComponent chatMessage(LocalSpeechTranscript.Line line, String localName) {
        boolean mention = line.mentions(localName);
        boolean shout = line.mode() == LocalSpeechPayload.Mode.SHOUT;
        boolean whisper = line.mode() != LocalSpeechPayload.Mode.SAY && !shout;
        MutableComponent name = Component.literal(line.name()).withColor(line.rgb());
        MutableComponent verb = Component.literal(line.speechLabel().substring(line.name().length()) + " ")
                .withColor(whisper ? 0xaaaaaa : 0xffffff);
        MutableComponent body = styledSpeech(line.text(), localName, whisper ? 0xaaaaaa : 0xffffff,
                mention ? DirectionalCueTintMesh.MENTION_RGB : -1, shout);
        return name.append(mention ? Component.literal(" [mention]") : Component.empty()).append(verb).append(body);
    }

    static MutableComponent styledSpeech(String text, String localName, int defaultColor, int mentionColor,
                                         boolean bold) {
        MutableComponent result = Component.empty();
        List<int[]> ranges = mentionColor < 0 ? List.of() : LocalSpeechTranscript.mentionRanges(text, localName);
        int at = 0;
        for (int[] range : ranges) {
            if (range[0] < at) continue;
            result.append(styledPart(text.substring(at, range[0]), defaultColor, bold));
            result.append(styledPart(text.substring(range[0], range[1]), mentionColor, bold));
            at = range[1];
        }
        result.append(styledPart(text.substring(at), defaultColor, bold));
        result.withColor(defaultColor);
        if (bold) result.withStyle(ChatFormatting.BOLD);
        return result;
    }

    private static MutableComponent styledPart(String text, int color, boolean bold) {
        MutableComponent part = Component.literal(text).withColor(color);
        if (bold) part.withStyle(ChatFormatting.BOLD);
        return part;
    }

    static boolean sameSpeaker(int expectedId, UUID expectedUuid, int actualId, UUID actualUuid) {
        return expectedId == actualId && expectedUuid != null
                && !LocalSpeechPayload.NO_UUID.equals(expectedUuid) && expectedUuid.equals(actualUuid);
    }

    private static boolean matches(Entity speaker, ClientLevel level, UUID expectedUuid,
                                   double x, double y, double z) {
        return speaker != null && speaker.level() == level && !speaker.isRemoved()
                && matchesSpeaker(expectedUuid, speaker.getUUID(), speaker.position(), x, y, z);
    }

    static boolean matchesSpeaker(UUID expectedUuid, UUID observedUuid, Vec3 position,
                                  double x, double y, double z) {
        return expectedUuid != null && !LocalSpeechPayload.NO_UUID.equals(expectedUuid)
                && expectedUuid.equals(observedUuid) && position.distanceToSqr(x, y, z) < 64;
    }

    static void acceptSpeech(LocalSpeechTranscript transcript, LocalSpeechPayload payload, Object dimension,
                             UUID resolvedUuid, boolean conflictingEntity, long now) {
        transcript.accept(payload.speakerEntityId(), payload.actualName(), payload.rgb(), payload.text(),
                payload.x(), payload.y(), payload.z(), dimension, payload.speakerUuid(), resolvedUuid,
                now, payload.mode(),
                payload.azimuthSector(), payload.verticalBand());
        // A present but inconsistent ID must not become a floating fallback if it later despawns.
        if (conflictingEntity && payload.mode() != LocalSpeechPayload.Mode.W_MUFFLED) {
            transcript.discard(transcript.candidates().getLast());
            diagnose(LocalSpeechTranscript.Placement.MISMATCH, now, false);
        }
    }

    public static List<LocalSpeechTranscript.Line> history() {
        return TRANSCRIPT.history();
    }

    public static String localName() {
        return localName;
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || world != level) return;
        boolean hudCallouts = !minecraft.options.hideGui && (minecraft.screen == null || minecraft.screen instanceof ChatScreen);
        if (!worldBubblesAllowed(minecraft.options.hideGui)) {
            clearCalloutFrame();
            return;
        }
        long now = System.currentTimeMillis();
        TRANSCRIPT.expire(level.dimension(), now);
        PoseStack poses = event.getPoseStack();
        if (poses == null) return;
        Vec3 camera = event.getCamera().getPosition();
        callouts.clear();
        calloutRects.clear();
        calloutFrameAt = now;
        pruneNearCalloutState(TRANSCRIPT);
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        renderBubbles(TRANSCRIPT, level.dimension(), now, camera, event.getCamera().rotation(), poses,
                id -> {
                    Entity entity = level.getEntity(id);
                    if (entity != null && (entity.level() != level || entity.isRemoved())) entity = null;
                    Entity owned = GhostControlClient.ownedCharacterForHud();
                    if (entity == null && owned != null && owned.level() == level && !owned.isRemoved()
                            && owned.getId() == id) entity = owned;
                    return entity == null ? null : new Speaker(entity.getUUID(), entity.getPosition(partialTick),
                            entity.position(), entity.getEyePosition(partialTick), entity.getBbHeight());
                 }, (id, uuid) -> minecraft.getCameraEntity() != null && sameSpeaker(id, uuid,
                         minecraft.getCameraEntity().getId(), minecraft.getCameraEntity().getUUID()),
                (from, to) -> loadedSightline(level, from, to),
                (from, to) -> level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, minecraft.player)).getType() == net.minecraft.world.phys.HitResult.Type.MISS,
                  minecraft.font::width,
                 (left, top, right, bottom, depth, color, matrix) -> {
                     var vertices = buffers.getBuffer(RenderType.textBackground());
                     vertices.addVertex(matrix, left, top, depth).setColor(color).setLight(LightTexture.FULL_BRIGHT);
                     vertices.addVertex(matrix, left, bottom, depth).setColor(color).setLight(LightTexture.FULL_BRIGHT);
                     vertices.addVertex(matrix, right, bottom, depth).setColor(color).setLight(LightTexture.FULL_BRIGHT);
                     vertices.addVertex(matrix, right, top, depth).setColor(color).setLight(LightTexture.FULL_BRIGHT);
                 },
                  (text, x, y, matrix) -> minecraft.font.drawInBatch(text, x, y,
                         0xffffffff, false, matrix, buffers, Font.DisplayMode.NORMAL,
                          0, LightTexture.FULL_BRIGHT), buffers::endBatch,
                (bubble, speaker, placement) -> {
                     if (!hudCallouts || placement != LocalSpeechTranscript.Placement.TRACKED || speaker == null
                            || bubble.line().mode() == LocalSpeechPayload.Mode.W_MUFFLED) return false;
                    UUID uuid = bubble.line().speakerUuid();
                    if (uuid == null || !uuid.equals(speaker.uuid()) || minecraft.getCameraEntity() != null
                            && sameSpeaker(bubble.line().speakerId(), uuid, minecraft.getCameraEntity().getId(),
                                    minecraft.getCameraEntity().getUUID())) return false;
                    double distance = Math.sqrt(speaker.authoritativePosition().distanceToSqr(camera));
                    boolean wasNear = nearCalloutState.getOrDefault(uuid, false);
                    boolean near = distance <= (wasNear ? 3.0 : 2.5);
                    nearCalloutState.put(uuid, near);
                    if (!near) return false;
                    if (callouts.stream().anyMatch(existing -> existing.line().speakerId() == bubble.line().speakerId()))
                        return true;
                    if (callouts.size() >= 2) return false;
                    Vector3f view = new Vector3f();
                    var projection = new Matrix4f(event.getProjectionMatrix());
                    Quaternionf inverseCamera = new Quaternionf(event.getCamera().rotation()).conjugate();
                    int pixelWidth = minecraft.getWindow().getWidth(), pixelHeight = minecraft.getWindow().getHeight();
                    Vec3 feet = speaker.position();
                    Vec3[] bodyPoints = {feet.add(0, .08, 0), feet.add(0, speaker.height() * .55, 0),
                            speaker.eye(), feet.add(0, speaker.height(), 0)};
                    CloseSpeechCallout.Point highestVisibleBody = null;
                    for (Vec3 bodyPoint : bodyPoints) {
                        Vec3 relative = bodyPoint.subtract(camera);
                        view.set((float) relative.x, (float) relative.y, (float) relative.z).rotate(inverseCamera);
                        var projected = CloseSpeechCallout.project(projection, view.x, view.y, view.z,
                                pixelWidth, pixelHeight);
                        if (CloseSpeechCallout.onScreen(projected, pixelWidth, pixelHeight)
                                && loadedSightline(level, camera, bodyPoint)
                                && level.clip(new ClipContext(camera, bodyPoint, ClipContext.Block.COLLIDER,
                                        ClipContext.Fluid.NONE, minecraft.player)).getType()
                                == net.minecraft.world.phys.HitResult.Type.MISS
                                && (highestVisibleBody == null || projected.y() < highestVisibleBody.y()))
                            highestVisibleBody = projected;
                    }
                    if (highestVisibleBody == null) {
                        Vec3 head = feet.add(0, speaker.height(), 0);
                        Vec3 from = feet.subtract(camera), to = head.subtract(camera);
                        view.set((float) from.x, (float) from.y, (float) from.z).rotate(inverseCamera);
                        float x0 = view.x, y0 = view.y, z0 = view.z;
                        view.set((float) to.x, (float) to.y, (float) to.z).rotate(inverseCamera);
                        var intersection = CloseSpeechCallout.intersectSegment(projection, x0, y0, z0,
                                view.x, view.y, view.z, pixelWidth, pixelHeight);
                        if (intersection != null) {
                            Vec3 candidate = feet.add(0, speaker.height() * intersection.t(), 0);
                            if (loadedSightline(level, camera, candidate)
                                    && level.clip(new ClipContext(camera, candidate, ClipContext.Block.COLLIDER,
                                    ClipContext.Fluid.NONE, minecraft.player)).getType()
                                    == net.minecraft.world.phys.HitResult.Type.MISS)
                                highestVisibleBody = intersection.point();
                        }
                    }
                    if (highestVisibleBody == null) return false;
                    Vec3 headAnchor = feet.add(0, speaker.height() + .22, 0).subtract(camera);
                    view.set((float) headAnchor.x, (float) headAnchor.y, (float) headAnchor.z).rotate(inverseCamera);
                    var point = CloseSpeechCallout.projectInFront(projection, view.x, view.y, view.z,
                            pixelWidth, pixelHeight);
                    float guiScaleX = (float) minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getWidth();
                    float guiScaleY = (float) minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getHeight();
                    var guiHead = point == null ? null : new CloseSpeechCallout.Point(point.x() * guiScaleX,
                            point.y() * guiScaleY);
                    var guiBody = new CloseSpeechCallout.Point(highestVisibleBody.x() * guiScaleX,
                            highestVisibleBody.y() * guiScaleY);
                    var guiAnchor = CloseSpeechCallout.chooseAnchor(guiHead, guiBody,
                            minecraft.getWindow().getGuiScaledHeight());
                    int textWidth = minecraft.font.width(bubbleHeader(minecraft.font::width,
                            bubble.line().name(), bubble.line().rgb()));
                    for (Component row : wrap(minecraft.font::width, bubble.line().text(), bubble.line().mode(), localName))
                        textWidth = Math.max(textWidth, minecraft.font.width(row));
                     int guiWidth = minecraft.getWindow().getGuiScaledWidth(), guiHeight = minecraft.getWindow().getGuiScaledHeight();
                     var reserved = new ArrayList<CloseSpeechCallout.Rect>();
                     boolean uVisible = LocalSpeechReviewOverlay.isVisible();
                     if (uVisible) {
                         var u = LocalSpeechReviewOverlay.layout(guiWidth, guiHeight, minecraft.screen instanceof ChatScreen);
                         if (u.width() >= 24 && u.height() >= 20) reserved.add(new CloseSpeechCallout.Rect(u.left(), u.top(), u.width(), u.height()));
                     }
                     reserved.addAll(calloutRects);
                     int panelWidth = Math.min(Math.max(0, guiWidth - 12), Math.max(72, textWidth + 12));
                     int panelHeight = 18 + wrap(minecraft.font::width, bubble.line().text(), bubble.line().mode(), localName).size() * 10 + 7;
                     var feasible = CloseSpeechCallout.layout(guiAnchor, panelWidth,
                             panelHeight, guiWidth, guiHeight, 24, reserved);
                     if (feasible == null) return false;
                      callouts.add(new QueuedCallout(bubble.line(), guiAnchor, feasible, now, guiWidth, guiHeight,
                              panelWidth, panelHeight, uVisible));
                     calloutRects.add(feasible);
                    return true;
                });
    }

    public static void renderCallouts(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event == null || minecraft.screen != null) return;
        drawCallouts(event.getGuiGraphics(), minecraft);
    }

    public static void renderCallouts(ScreenEvent.Render.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event == null || minecraft.screen != event.getScreen() || !(event.getScreen() instanceof ChatScreen)) return;
        drawCallouts(event.getGuiGraphics(), minecraft);
    }

    private static void drawCallouts(GuiGraphics graphics, Minecraft minecraft) {
        if (minecraft.options.hideGui) {
            clearCalloutFrame();
            return;
        }
        if (graphics == null || minecraft.level == null || minecraft.player == null) return;
        long now = System.currentTimeMillis();
        if (now < calloutFrameAt || now - calloutFrameAt > 500) return;
        int viewportWidth = graphics.guiWidth(), viewportHeight = graphics.guiHeight();
        Font font = minecraft.font;
        calloutRects.clear();
        CloseSpeechCallout.Rect reserved = null;
        if (LocalSpeechReviewOverlay.isVisible()) {
            var u = LocalSpeechReviewOverlay.layout(viewportWidth, viewportHeight, minecraft.screen instanceof ChatScreen);
            if (u.width() >= 24 && u.height() >= 20)
                reserved = new CloseSpeechCallout.Rect(u.left(), u.top(), u.width(), u.height());
        }
        for (QueuedCallout queued : callouts) {
            if (now < queued.captured() || now - queued.captured() > 500) continue;
            Component header = bubbleHeader(font::width, queued.line().name(), queued.line().rgb());
            List<Component> lines = wrap(font::width, queued.line().text(), queued.line().mode(), localName);
            int textWidth = Math.max(font.width(header), lines.stream().mapToInt(font::width).max().orElse(0));
            int panelWidth = Math.min(Math.max(0, viewportWidth - 12), Math.max(72, textWidth + 12));
            int panelHeight = 18 + lines.size() * 10 + 7;
            boolean uVisible = LocalSpeechReviewOverlay.isVisible();
            var rect = queued.width() == viewportWidth && queued.height() == viewportHeight
                    && queued.panelWidth() == panelWidth && queued.panelHeight() == panelHeight
                    && queued.uVisible() == uVisible
                    ? (queued.rect().overlapsAny(reserved, calloutRects) ? null : queued.rect())
                    : CloseSpeechCallout.layout(queued.anchor(), panelWidth, panelHeight,
                    viewportWidth, viewportHeight, 24, withReserved(reserved, calloutRects));
            if (rect == null) continue;
            if (!calloutRects.contains(rect)) calloutRects.add(rect);
            int x = Math.round(rect.x()), y = Math.round(rect.y());
            graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xf2111319);
            graphics.fill(x, y, x + panelWidth, y + 15, 0xff090c13);
            graphics.drawString(font, header, CloseSpeechCallout.centeredTextX(x, panelWidth, font.width(header)),
                    y + 3, 0xffffffff, false);
            for (int i = 0; i < lines.size(); i++)
                graphics.drawString(font, lines.get(i), CloseSpeechCallout.centeredTextX(
                        x, panelWidth, font.width(lines.get(i))), y + 18 + i * 10, 0xffffffff, false);
        }
    }

    static boolean worldBubblesAllowed(boolean hideGui) {
        return !hideGui;
    }

    private static void clearCalloutFrame() {
        callouts.clear();
        calloutRects.clear();
        calloutFrameAt = 0;
    }

    static void pruneNearCalloutState(LocalSpeechTranscript transcript) {
        var active = new java.util.LinkedHashSet<UUID>();
        for (var bubble : transcript.candidates()) {
            UUID uuid = bubble.line().speakerUuid();
            if (uuid != null) active.add(uuid);
        }
        nearCalloutState.keySet().removeIf(uuid -> !active.contains(uuid));
        while (nearCalloutState.size() > 24)
            nearCalloutState.remove(nearCalloutState.keySet().iterator().next());
    }

    private static List<CloseSpeechCallout.Rect> withReserved(CloseSpeechCallout.Rect reserved,
                                                               List<CloseSpeechCallout.Rect> previous) {
        List<CloseSpeechCallout.Rect> result = new ArrayList<>();
        if (reserved != null) result.add(reserved);
        result.addAll(previous);
        return result;
    }

    /** Visual feet/eye share the render partial tick; proximity uses tick-authoritative feet. */
    record Speaker(UUID uuid, Vec3 position, Vec3 authoritativePosition, Vec3 eye, double height) {
        Speaker(UUID uuid, Vec3 position, Vec3 eye, double height) {
            this(uuid, position, position, eye, height);
        }
    }

    @FunctionalInterface
    interface PanelDraw {
        void draw(float left, float top, float right, float bottom, float depth, int color, Matrix4f matrix);
    }

    @FunctionalInterface
    interface TextDraw { void draw(Component text, float x, float y, Matrix4f matrix); }

    /** Same submission path in-game and in headless draw tests; callbacks only supply world and font I/O. */
    static void renderBubbles(LocalSpeechTranscript transcript, Object dimension, long now, Vec3 camera,
                              Quaternionf rotation, PoseStack poses, IntFunction<Speaker> resolve,
                                BiPredicate<Integer, UUID> self, BiPredicate<Vec3, Vec3> loaded,
                                BiPredicate<Vec3, Vec3> unobstructed, ToIntFunction<Component> width,
                                PanelDraw panel, TextDraw draw, Runnable flush) {
        renderBubbles(transcript, dimension, now, camera, rotation, poses, resolve, self, loaded, unobstructed,
                width, panel, draw, flush, (bubble, speaker, placement) -> false);
    }

    @FunctionalInterface
    interface CalloutSelect { boolean select(LocalSpeechTranscript.Bubble bubble, Speaker speaker,
                                             LocalSpeechTranscript.Placement placement); }

    static void renderBubbles(LocalSpeechTranscript transcript, Object dimension, long now, Vec3 camera,
                              Quaternionf rotation, PoseStack poses, IntFunction<Speaker> resolve,
                              BiPredicate<Integer, UUID> self, BiPredicate<Vec3, Vec3> loaded,
                              BiPredicate<Vec3, Vec3> unobstructed, ToIntFunction<Component> width,
                              PanelDraw panel, TextDraw draw, Runnable flush, CalloutSelect calloutSelect) {
        transcript.expire(dimension, now);
        var speakerHeights = new HashMap<Integer, Double>();
        var speakerCounts = new HashMap<Integer, Integer>();
        Vector3f screenUp = new Vector3f(0, 1, 0).rotate(rotation);
        boolean drawn = false;
        LocalSpeechTranscript.Placement submitted = null;
        LocalSpeechTranscript.Placement skipped = null;
        List<LocalSpeechTranscript.Bubble> candidates = new ArrayList<>(transcript.candidates());
        Collections.reverse(candidates); // Newest sits nearest the head.
        for (LocalSpeechTranscript.Bubble bubble : candidates) {
            LocalSpeechTranscript.Line line = bubble.line();
            Speaker entity = resolve.apply(line.speakerId());
            boolean isSelf = self.test(line.speakerId(), line.speakerUuid());
            // The transient panel uses only the authored line, never an entity/profile name.
            Vec3 eye = entity != null
                    ? entity.eye()
                    : new Vec3(line.x(), line.y() + 1.5, line.z());
            boolean near = entity != null && entity.authoritativePosition().distanceToSqr(line.x(), line.y(), line.z()) < 4;
            boolean withinRange = eye.distanceToSqr(camera) <= 24 * 24;
            // Production sightline checks loaded chunks before raytracing; never loads a speaker chunk.
            boolean visible = withinRange && loaded.test(camera, eye) && unobstructed.test(camera, eye);
            LocalSpeechTranscript.Placement placement = LocalSpeechTranscript.placement(bubble, dimension, now,
                    entity == null ? null : entity.uuid(), near, isSelf, eye.distanceToSqr(camera), visible);
            if (placement == LocalSpeechTranscript.Placement.MISMATCH) transcript.discard(bubble);
            if (placement != LocalSpeechTranscript.Placement.TRACKED
                    && placement != LocalSpeechTranscript.Placement.FALLBACK) {
                if (skipped == null) skipped = placement;
                continue;
            }
            if (placement == LocalSpeechTranscript.Placement.TRACKED && bubble.entityUuid() == null) {
                // Bind once, while close to the authored position; later movement follows this UUID.
                UUID trackedUuid = entity.uuid();
                transcript.bindPending(dimension, now, pending -> pending == line ? trackedUuid : null);
            }
            if (calloutSelect.select(bubble, entity, placement)) continue;
            double distance = Math.sqrt(eye.distanceToSqr(camera));
            float scale = (float) Math.max(.009, Math.min(.024, .009 + Math.max(0, distance - 3) * .00085));
            List<Component> lines = wrap(width, line.text(), line.mode(), localName);
            Component header = bubbleHeader(width, line.name(), line.rgb());
            int headerWidth = width.applyAsInt(header);
            int textWidth = Math.max(headerWidth, lines.stream().mapToInt(width).max().orElse(0));
            float panelWidth = textWidth + 12;
            float panelHeight = lines.size() * 10 + 21;
            float tabOverhang = 5;
            int count = speakerCounts.getOrDefault(line.speakerId(), 0);
            if (count >= 4) continue; // Never pin an unbounded history to the edge of the view.
            double offset = speakerHeights.getOrDefault(line.speakerId(), 0.0);
            double nextOffset = offset + (panelHeight + tabOverhang) * scale + .06;
            if (nextOffset > 5.8) continue; // Four full panels and protruding tabs at maximum scale.
            speakerHeights.put(line.speakerId(), nextOffset);
            speakerCounts.put(line.speakerId(), count + 1);
            // Preserve .22m projected head clearance even when pitched; stack along
            // billboard up so each panel retains its full screen-space height.
            Vec3 headTop = entity != null
                    ? entity.position().add(0, entity.height(), 0)
                    : new Vec3(line.x(), line.y() + 1.8, line.z());
            Vec3 anchor = headTop.add(0, .22, 0).add(screenUp.x * (offset + .22 * (1 - screenUp.y)),
                    screenUp.y * (offset + .22 * (1 - screenUp.y)),
                    screenUp.z * (offset + .22 * (1 - screenUp.y)));
            poses.pushPose();
            poses.translate(anchor.x - camera.x, anchor.y - camera.y, anchor.z - camera.z);
            poses.mulPose(rotation);
            poses.scale(scale, -scale, scale);
            panel.draw(-panelWidth / 2, -panelHeight, panelWidth / 2, 0, -.1f,
                    0xf2111319, poses.last().pose());
            // Opaque name tab crosses the border and remains readable against sky or terrain.
            panel.draw(-headerWidth / 2f - 6, -panelHeight - tabOverhang,
                    headerWidth / 2f + 6, -panelHeight + 9, -.08f,
                    0xff090c13, poses.last().pose());
            draw.draw(header, -headerWidth / 2f, -panelHeight - 2, poses.last().pose());
            float startY = -panelHeight + 17;
            for (int i = 0; i < lines.size(); i++) {
                Component text = lines.get(i);
                draw.draw(text, -width.applyAsInt(text) / 2f, startY + i * 10, poses.last().pose());
                submitted = placement;
            }
            poses.popPose();
            drawn = true;
        }
        if (drawn) flush.run();
        if (submitted != null) diagnose(submitted, now, true);
        else if (skipped != null) diagnose(skipped, now, false);
    }

    private static void diagnose(LocalSpeechTranscript.Placement placement, long now, boolean submitted) {
        if (!DEBUG_BUBBLES || now - lastBubbleDiagnostic < 5_000) return;
        lastBubbleDiagnostic = now;
        MoonStation14.LOGGER.info("Local speech bubble {}: {} (diagnostics limited to once per 5s)",
                submitted ? "submitted" : "placement", placement);
    }

    private static boolean loadedSightline(ClientLevel level, Vec3 from, Vec3 to) {
        return loadedSightline(from, to, level::hasChunk);
    }

    /** Supercover traversal in the horizontal chunk grid, including corner-touching cells. */
    static boolean loadedSightline(Vec3 from, Vec3 to, BiPredicate<Integer, Integer> hasChunk) {
        if (!Double.isFinite(from.x) || !Double.isFinite(from.y) || !Double.isFinite(from.z)
                || !Double.isFinite(to.x) || !Double.isFinite(to.y) || !Double.isFinite(to.z)
                || from.distanceToSqr(to) > 24 * 24) return false;
        int x = (int) Math.floor(from.x / 16), z = (int) Math.floor(from.z / 16);
        int endX = (int) Math.floor(to.x / 16), endZ = (int) Math.floor(to.z / 16);
        if (!hasChunk.test(x, z)) return false;
        double dx = to.x - from.x, dz = to.z - from.z;
        int stepX = Integer.compare(endX, x), stepZ = Integer.compare(endZ, z);
        // A ray lying exactly on a chunk edge touches the cells on both sides.
        boolean onXEdge = dx == 0 && from.x == x * 16.0;
        boolean onZEdge = dz == 0 && from.z == z * 16.0;
        // The starting point itself may touch the lower-indexed side of either edge,
        // even if the ray immediately travels toward the higher-indexed side.
        if (!edgeNeighborsLoaded(x, z, from.x == x * 16.0, from.z == z * 16.0, hasChunk)) return false;
        double tX = stepX == 0 ? Double.POSITIVE_INFINITY
                : ((stepX > 0 ? (x + 1) * 16.0 : x * 16.0) - from.x) / dx;
        double tZ = stepZ == 0 ? Double.POSITIVE_INFINITY
                : ((stepZ > 0 ? (z + 1) * 16.0 : z * 16.0) - from.z) / dz;
        double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(dx);
        double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 16 / Math.abs(dz);
        for (int crossings = 0; x != endX || z != endZ; crossings++) {
            if (crossings >= 8) return false; // A <=24m ray cannot cross more than four boundaries per axis.
            if (tX == tZ) {
                if (!hasChunk.test(x + stepX, z) || !hasChunk.test(x, z + stepZ)) return false;
                x += stepX;
                z += stepZ;
                tX += deltaX;
                tZ += deltaZ;
            } else if (tX < tZ) {
                x += stepX;
                tX += deltaX;
            } else {
                z += stepZ;
                tZ += deltaZ;
            }
            if (!hasChunk.test(x, z)) return false;
            if (!edgeNeighborsLoaded(x, z, onXEdge, onZEdge, hasChunk)) return false;
        }
        // A boundary endpoint approached from the positive side still touches the
        // lower-indexed chunk (and all four chunks when it is a corner).
        return edgeNeighborsLoaded(x, z, to.x == x * 16.0, to.z == z * 16.0, hasChunk);
    }

    private static boolean edgeNeighborsLoaded(int x, int z, boolean onXEdge, boolean onZEdge,
                                               BiPredicate<Integer, Integer> hasChunk) {
        return (!onXEdge || hasChunk.test(x - 1, z))
                && (!onZEdge || hasChunk.test(x, z - 1))
                && (!onXEdge || !onZEdge || hasChunk.test(x - 1, z - 1));
    }

    private static Component bubbleText(String text, LocalSpeechPayload.Mode mode, String localName) {
        int base = mode == LocalSpeechPayload.Mode.WHISPER ? 0xaaaaaa : 0xffffff;
        boolean shout = mode == LocalSpeechPayload.Mode.SHOUT;
        return styledSpeech(text, localName, base,
                mode == LocalSpeechPayload.Mode.W_MUFFLED ? -1 : DirectionalCueTintMesh.MENTION_RGB, shout);
    }

    /** Keep the authored character name on the same short-lived bubble as the utterance. */
    private static Component bubbleHeader(ToIntFunction<Component> width, String name, int rgb) {
        // Lift dark character colors against the nearly black panel without changing the authored hue.
        int red = rgb >> 16 & 255, green = rgb >> 8 & 255, blue = rgb & 255;
        double luminance = .2126 * red + .7152 * green + .0722 * blue;
        if (luminance < 150) {
            double blend = (150 - luminance) / (255 - luminance);
            red += (int) Math.ceil((255 - red) * blend);
            green += (int) Math.ceil((255 - green) * blend);
            blue += (int) Math.ceil((255 - blue) * blend);
        }
        int color = red << 16 | green << 8 | blue;
        Component full = Component.literal(name).withColor(color).withStyle(ChatFormatting.BOLD);
        if (width.applyAsInt(full) <= 100) return full;
        String shortened = name;
        do {
            shortened = shortened.substring(0, shortened.offsetByCodePoints(shortened.length(), -1));
        } while (!shortened.isEmpty() && width.applyAsInt(Component.literal(shortened + "…")
                .withColor(color).withStyle(ChatFormatting.BOLD)) > 100);
        return Component.literal(shortened + "…").withColor(color).withStyle(ChatFormatting.BOLD);
    }

    private static List<Component> wrap(ToIntFunction<Component> width, String text, LocalSpeechPayload.Mode mode,
                                         String localName) {
        List<Component> result = new ArrayList<>(3);
        String remaining = text.strip().replaceAll("\\s+", " ");
        List<int[]> mentionRanges = mode == LocalSpeechPayload.Mode.W_MUFFLED
                ? List.of() : LocalSpeechTranscript.mentionRanges(remaining, localName);
        int sourceOffset = 0;
        while (!remaining.isEmpty() && result.size() < 3) {
            int fit = 0;
            for (int end = 0; end < remaining.length();) {
                end += Character.charCount(remaining.codePointAt(end));
                if (width.applyAsInt(bubbleText(remaining.substring(0, end), mode, localName)) > 100) break;
                fit = end;
            }
            if (fit == 0) fit = Character.charCount(remaining.codePointAt(0));
            if (fit < remaining.length()) {
                int space = remaining.lastIndexOf(' ', fit);
                if (space > 0) fit = space;
            }
            String row = remaining.substring(0, fit).stripTrailing();
            String tail = remaining.substring(fit);
            int skippedWhitespace = tail.length() - tail.stripLeading().length();
            remaining = tail.stripLeading();
            if (result.size() == 2 && !remaining.isEmpty()) {
                while (!row.isEmpty() && width.applyAsInt(bubbleText(row + "…", mode, localName)) > 100)
                    row = row.substring(0, row.offsetByCodePoints(row.length(), -1)).stripTrailing();
                row += "…";
            }
            result.add(bubbleText(row, mode, localName, mentionRanges, sourceOffset));
            sourceOffset += fit + skippedWhitespace;
        }
        if (result.isEmpty()) result.add(bubbleText("", mode, localName));
        return result;
    }

    private static Component bubbleText(String text, LocalSpeechPayload.Mode mode, String localName,
                                        List<int[]> ranges, int sourceOffset) {
        int base = mode == LocalSpeechPayload.Mode.WHISPER ? 0xaaaaaa : 0xffffff;
        return styledSpeech(text, localName, base,
                mode == LocalSpeechPayload.Mode.W_MUFFLED ? -1 : DirectionalCueTintMesh.MENTION_RGB,
                mode == LocalSpeechPayload.Mode.SHOUT, ranges, sourceOffset);
    }

    private static MutableComponent styledSpeech(String text, String localName, int defaultColor, int mentionColor,
                                                  boolean bold, List<int[]> ranges, int sourceOffset) {
        MutableComponent result = Component.empty();
        int at = 0;
        int sourceEnd = sourceOffset + text.length();
        for (int[] range : ranges) {
            int start = Math.max(sourceOffset, range[0]);
            int end = Math.min(sourceEnd, range[1]);
            if (start >= end) continue;
            int rowStart = start - sourceOffset, rowEnd = end - sourceOffset;
            if (rowStart > at) result.append(styledPart(text.substring(at, rowStart), defaultColor, bold));
            result.append(styledPart(text.substring(rowStart, rowEnd), mentionColor, bold));
            at = rowEnd;
        }
        result.append(styledPart(text.substring(at), defaultColor, bold));
        result.withColor(defaultColor);
        if (bold) result.withStyle(ChatFormatting.BOLD);
        return result;
    }
}
