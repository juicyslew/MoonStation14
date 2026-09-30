package com.juicyslew.moonstation14.ms14.chat.client;

import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload.DistanceTier;
import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload.Mode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Text-free, session-local edge illumination; entity IDs are never trusted without their UUID. */
public final class DirectionalCueOverlay {
    private static final long COOLDOWN_MS = 750;
    private static final int CAPACITY = 12;
    private static final int VISIBLE = 3;
    private static final int MARK_SEPARATION = 34;
    private static final List<Cue> cues = new ArrayList<>();
    private static Snapshot snapshot;

    private DirectionalCueOverlay() { }

    private record Cue(int speakerId, @Nullable UUID uuid, @Nullable Vec3 position, int sector, int band,
                       DistanceTier tier, Mode mode, boolean mention, boolean bubbleVisibleAtReceipt,
                       long received, long lifetime) {
        boolean muffled() { return position == null; }
        boolean sameKey(Cue other) {
            if (muffled() != other.muffled()) return false;
            if (uuid != null || other.uuid != null) return uuid != null && uuid.equals(other.uuid);
            return speakerId == other.speakerId && !muffled();
        }
    }

    private record Snapshot(Vec3 camera, Quaternionf rotation, Matrix4f projection, float partial, long captured) { }

    public static void clear() { cues.clear(); snapshot = null; }

    public static void enqueueClear(int id, @Nullable UUID resolvedUuid, Vec3 authoredPosition,
                                    boolean mention, boolean bubbleVisibleAtReceipt, Mode mode,
                                    long lifetimeMillis, long now) {
        if (id <= 0 || authoredPosition == null || !finite(authoredPosition) || mode == null
                || mode == Mode.W_MUFFLED) return;
        enqueue(new Cue(id, resolvedUuid, authoredPosition, -1, -1, DistanceTier.NONE, mode,
                mention, bubbleVisibleAtReceipt, now, clampLifetime(lifetimeMillis)), now);
    }

    public static void enqueueMuffled(int id, UUID uuid, int sector, int band, DistanceTier tier,
                                      long lifetimeMillis, long now) {
        if (id <= 0 || uuid == null || DirectionalCueGeometry.bearing(sector, band) == null
                || tier == null || tier == DistanceTier.NONE) return;
        enqueue(new Cue(id, uuid, null, sector, band, tier, Mode.W_MUFFLED,
                false, false, now, clampLifetime(lifetimeMillis)), now);
    }

    // Compatibility for the pre-handoff caller and focused tests. No bound UUID means no entity lookup.
    public static void enqueueClear(int id, Vec3 position, boolean mention, long now) {
        enqueueClear(id, position, mention, true, now);
    }
    public static void enqueueClear(int id, Vec3 position, boolean mention, boolean visible, long now) {
        if (id <= 0 || position == null || !finite(position)) return;
        enqueue(new Cue(id, null, position, -1, -1, DistanceTier.NONE, Mode.SAY,
                mention, visible, now, 3_600), now);
    }
    public static void enqueueMuffled(int sector, int band, long now) {
        // Legacy anonymous input has no identity to follow; isolate distinct bearings.
        if (DirectionalCueGeometry.bearing(sector, band) == null) return;
        enqueue(new Cue(0, null, null, sector, band, DistanceTier.FAR, Mode.W_MUFFLED,
                false, false, now, 3_600), now);
    }

    private static long clampLifetime(long duration) { return Math.max(4_000, Math.min(10_000, duration)); }
    private static boolean finite(Vec3 p) {
        return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z);
    }

    private static void enqueue(Cue incoming, long now) {
        expire(now);
        for (int i = 0; i < cues.size(); i++) {
            Cue previous = cues.get(i);
            if (!incoming.sameKey(previous)) continue;
            if (previous.mention && !incoming.mention) return;
            if (!incoming.mention && now >= previous.received && now - previous.received < COOLDOWN_MS
                    && (previous.bubbleVisibleAtReceipt == incoming.bubbleVisibleAtReceipt
                    || !previous.bubbleVisibleAtReceipt)) return;
            cues.remove(i);
            break;
        }
        if (cues.size() >= CAPACITY) {
            Cue oldest = cues.stream().filter(c -> !c.mention)
                    .min(Comparator.comparingLong(Cue::received)).orElse(null);
            if (oldest == null && !incoming.mention) return;
            cues.remove(oldest != null ? oldest : cues.stream()
                    .min(Comparator.comparingLong(Cue::received)).orElseThrow());
        }
        cues.add(incoming);
    }

    private static void expire(long now) {
        cues.removeIf(c -> now < c.received || now - c.received >= c.lifetime);
    }

    /** Copy camera matrices and partial tick during the world render; reproject at GUI render time. */
    public static void capture(RenderLevelStageEvent event) {
        if (event == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (event.getCamera() == null || event.getProjectionMatrix() == null) { snapshot = null; return; }
        snapshot = new Snapshot(event.getCamera().getPosition(), new Quaternionf(event.getCamera().rotation()),
                new Matrix4f(event.getProjectionMatrix()),
                event.getPartialTick().getGameTimeDeltaPartialTick(false), System.currentTimeMillis());
    }

    public static void render(RenderGuiEvent.Post event) {
        if (event == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && renderInPhase(minecraft.screen, false)) draw(event.getGuiGraphics(), minecraft);
    }

    public static void render(ScreenEvent.Render.Post event) {
        if (event == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.screen == event.getScreen()
                && renderInPhase(event.getScreen(), true)) draw(event.getGuiGraphics(), minecraft);
    }

    static boolean renderInPhase(Screen screen, boolean screenPhase) {
        return screenPhase ? screen instanceof ChatScreen : screen == null;
    }

    private static void draw(GuiGraphics graphics, Minecraft minecraft) {
        if (graphics == null || minecraft.options == null || minecraft.options.hideGui
                || minecraft.level == null || minecraft.player == null) return;
        long now = System.currentTimeMillis();
        expire(now);
        Snapshot frame = snapshot;
        if (frame == null || now < frame.captured || now - frame.captured > 500) return;
        var panel = panel(graphics.guiWidth(), graphics.guiHeight(), minecraft.screen instanceof ChatScreen);
        var selected = select(frame, minecraft, graphics.guiWidth(), graphics.guiHeight(), now);
        if (selected.isEmpty()) return;
        for (Placed placed : selected) {
            double fade = lifetimeFade(placed.cue.received, placed.cue.lifetime, now);
            DirectionalCueTintMesh.emit(graphics, placed.point, placed.intensity,
                    fade / Math.sqrt(selected.size()), placed.cue.mode, placed.cue.mention, panel);
        }
        graphics.flush();
    }

    private record Placed(Cue cue, DirectionalCueGeometry.Point point, double intensity) { }

    @Nullable
    static LocalSpeechReviewOverlay.Layout panel(int width, int height, boolean chatOpen) {
        return LocalSpeechReviewOverlay.isVisible()
                ? LocalSpeechReviewOverlay.layout(width, height, chatOpen) : null;
    }

    static double lifetimeFade(long received, long lifetime, long now) {
        if (lifetime <= 0 || now < received || now - received >= lifetime) return 0;
        double age = now - received;
        return Math.max(0, Math.min(1, Math.min(age / 220.0, (lifetime - age) * 4.0 / lifetime)));
    }

    private static Vec3 trackedPosition(Cue cue, Minecraft minecraft, float partial) {
        if (cue.uuid == null || minecraft == null || minecraft.level == null) return null;
        Entity entity = minecraft.level.getEntity(cue.speakerId);
        if (entity == null) return null;
        return matchingPosition(cue.uuid, entity.getUUID(), new Vec3(Mth.lerp(partial, entity.xOld, entity.getX()),
                Mth.lerp(partial, entity.yOld, entity.getY()) + entity.getBbHeight() * .85,
                Mth.lerp(partial, entity.zOld, entity.getZ())));
    }

    private static boolean trackedBodyVisible(Cue cue, Minecraft minecraft, Snapshot frame) {
        if (cue.uuid == null || minecraft == null || minecraft.level == null) return false;
        Entity entity = minecraft.level.getEntity(cue.speakerId);
        if (entity == null || matchingPosition(cue.uuid, entity.getUUID(), Vec3.ZERO) == null) return false;
        Vec3 feet = new Vec3(Mth.lerp(frame.partial, entity.xOld, entity.getX()),
                Mth.lerp(frame.partial, entity.yOld, entity.getY()),
                Mth.lerp(frame.partial, entity.zOld, entity.getZ()));
        return DirectionalCueGeometry.bodyInViewport(feet, entity.getBbHeight(), frame.camera,
                frame.rotation, frame.projection);
    }

    static Vec3 matchingPosition(UUID bound, UUID observed, Vec3 interpolated) {
        return bound != null && bound.equals(observed) ? interpolated : null;
    }

    static boolean suppressVisibleClear(boolean muffled, boolean bubbleVisibleAtReceipt,
                                        boolean trackedSource, boolean currentBodyVisible,
                                        boolean packetPointVisible) {
        return !muffled && bubbleVisibleAtReceipt
                && (trackedSource ? currentBodyVisible : packetPointVisible);
    }

    private static Vec3 direction(Cue cue, Snapshot frame, Minecraft minecraft) {
        Vec3 tracked = trackedPosition(cue, minecraft, frame.partial);
        if (tracked != null) return tracked.subtract(frame.camera);
        if (cue.muffled()) return DirectionalCueGeometry.bearing(cue.sector, cue.band);
        // A UUID mismatch must not permit a new occupant of the same entity ID to move the tint.
        return cue.position.subtract(frame.camera);
    }

    private static double intensity(Cue cue, Vec3 direction, boolean tracked) {
        if (cue.muffled() && !tracked) return fallbackIntensity(cue.tier);
        return distanceIntensity(cue.mode, direction.length());
    }

    static double distanceIntensity(double distance) {
        return distanceIntensity(Mode.SAY, distance);
    }

    static double distanceIntensity(Mode mode, double distance) {
        double range = mode == Mode.WHISPER || mode == Mode.W_MUFFLED ? 6 : 15;
        // Whisper fades with a fifth-order curve; ordinary speech uses cubic smoothstep.
        // Both remain continuous across the 3m clear/muffled handoff.
        double bounded = Double.isNaN(distance) ? range : Math.max(0, Math.min(range, distance));
        double proximity = 1 - bounded / range;
        double eased = range == 6
                ? proximity * proximity * proximity * (10 + proximity * (-15 + 6 * proximity))
                : proximity * proximity * (3 - 2 * proximity);
        return .08 + .92 * eased;
    }

    static double fallbackIntensity(DistanceTier tier) {
        return distanceIntensity(Mode.W_MUFFLED, tier == DistanceTier.NEAR ? 3.75 : 5.25);
    }

    private static List<Placed> select(Snapshot frame, Minecraft minecraft, int width, int height, long now) {
        List<Placed> result = new ArrayList<>(VISIBLE);
        cues.stream().filter(c -> now >= c.received && now - c.received < c.lifetime)
                .sorted(Comparator.comparing(Cue::mention).reversed()
                        .thenComparing(Comparator.comparingLong(Cue::received).reversed()))
                .forEach(c -> {
                    if (result.size() >= VISIBLE) return;
                    Vec3 direction = direction(c, frame, minecraft);
                    var point = DirectionalCueGeometry.project(direction, frame.rotation,
                            frame.projection, width, height, c.muffled() && c.band >= 3
                                    && trackedPosition(c, minecraft, frame.partial) == null);
                    boolean tracked = trackedPosition(c, minecraft, frame.partial) != null;
                    boolean bodyVisible = tracked && trackedBodyVisible(c, minecraft, frame);
                    boolean receiptVisible = tracked ? bodyVisible
                            : DirectionalCueGeometry.inViewport(direction, frame.rotation, frame.projection);
                    if (point == null || suppressVisibleClear(c.muffled(), c.bubbleVisibleAtReceipt,
                            tracked, bodyVisible, receiptVisible)) return;
                    for (Placed previous : result) {
                        if (Math.hypot(previous.point.x() - point.x(), previous.point.y() - point.y())
                                < MARK_SEPARATION) return;
                    }
                    result.add(new Placed(c, point, intensity(c, direction,
                            trackedPosition(c, minecraft, frame.partial) != null)));
                });
        return result;
    }

    static int queued() { return cues.size(); }
    static int selected(Matrix4f projection, Quaternionf rotation, int width, int height, long now) {
        return testSelection(projection, rotation, width, height, now).size();
    }
    static boolean selectedMention(Matrix4f projection, Quaternionf rotation, int width, int height, long now) {
        return testSelection(projection, rotation, width, height, now).stream().anyMatch(p -> p.cue.mention);
    }
    static List<DirectionalCueGeometry.Point> selectedPoints(Matrix4f projection, Quaternionf rotation,
                                                               int width, int height, long now) {
        return testSelection(projection, rotation, width, height, now).stream().map(Placed::point).toList();
    }
    private static List<Placed> testSelection(Matrix4f projection, Quaternionf rotation,
                                              int width, int height, long now) {
        return select(new Snapshot(Vec3.ZERO, rotation, projection, 0, now), null, width, height, now);
    }
}
