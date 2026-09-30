package com.juicyslew.moonstation14.ms14.chat.client;

import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiPredicate;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

class LocalSpeechDrawTest {
    private static final ResourceLocation MOON = ResourceLocation.parse("moonstation14:moon");
    private static final Matrix4f CUE_PROJECTION = new Matrix4f().perspective(
            (float) Math.toRadians(70), 16f / 9, .05f, 100f);
    private static final Vec3 CAMERA = new Vec3(10, 20, 30);
    private static final long RECEIVED = 1_000;
    private final LocalSpeechTranscript transcript = new LocalSpeechTranscript();
    private final List<Submission> submissions = new ArrayList<>();
    private final List<Panel> panels = new ArrayList<>();
    private int flushes;

    private record Submission(Component styled, Vector3f worldRelativePosition,
                                Vector3f glyphBottomLeft, Vector3f glyphBottomRight) { }
    private record Panel(Vector3f topLeft, Vector3f bottomLeft, Vector3f bottomRight,
                         Vector3f bottomCenter, float width, float height, float depth, int color) { }

    private static LocalSpeechClient.Speaker speaker(UUID uuid, double height) {
        return new LocalSpeechClient.Speaker(uuid, new Vec3(12, 20, 31), new Vec3(12, 21.6, 31), height);
    }

    private static float scaleAt(Vec3 eye) {
        return (float) Math.max(.009, Math.min(.024, .009 + Math.max(0, eye.distanceTo(CAMERA) - 3) * .00085));
    }

    private static int width(Component text) {
        return text.getString().length() * (text.getStyle().isBold() ? 7 : 6);
    }

    private static String text(Submission submission) {
        return submission.styled().getString();
    }

    private void accept(LocalSpeechPayload.Mode mode, UUID uuid) {
        LocalSpeechPayload packet = mode == LocalSpeechPayload.Mode.W_MUFFLED
                ? new LocalSpeechPayload(7, UUID.randomUUID(), "Unknown Person", 0xffffff,
                        "private speech", 0, 0, 0, MOON, mode, 3, 1, LocalSpeechPayload.DistanceTier.NEAR)
                : new LocalSpeechPayload(7, uuid == null ? LocalSpeechPayload.NO_UUID : uuid,
                        "Alice Smith", 0xabcdef, "peer speech", 12, 20, 31, MOON, mode,
                        LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.NO_BEARING,
                        LocalSpeechPayload.DistanceTier.NONE);
        LocalSpeechClient.acceptSpeech(transcript, packet, MOON, uuid, false, RECEIVED);
    }

    private void render(long now, Object dimension, IntFunction<LocalSpeechClient.Speaker> resolver,
                         boolean self, BiPredicate<Vec3, Vec3> loaded,
                         BiPredicate<Vec3, Vec3> unobstructed) {
        render(now, dimension, resolver, self, loaded, unobstructed, new Quaternionf());
    }

    private void render(long now, Object dimension, IntFunction<LocalSpeechClient.Speaker> resolver,
                         boolean self, BiPredicate<Vec3, Vec3> loaded,
                         BiPredicate<Vec3, Vec3> unobstructed, Quaternionf rotation) {
        render(now, dimension, resolver, self, loaded, unobstructed, rotation, CAMERA);
    }

    private void render(long now, Object dimension, IntFunction<LocalSpeechClient.Speaker> resolver,
                        boolean self, BiPredicate<Vec3, Vec3> loaded,
                        BiPredicate<Vec3, Vec3> unobstructed, Quaternionf rotation, Vec3 camera) {
        LocalSpeechClient.renderBubbles(transcript, dimension, now, camera, rotation, new PoseStack(),
                   resolver, (id, uuid) -> self && id == 7 && uuid != null, loaded, unobstructed,
                   LocalSpeechDrawTest::width,
                  (left, top, right, bottom, depth, color, matrix) -> panels.add(new Panel(
                          new Vector3f(left, top, depth).mulPosition(matrix),
                          new Vector3f(left, bottom, depth).mulPosition(matrix),
                          new Vector3f(right, bottom, depth).mulPosition(matrix),
                          new Vector3f(0, bottom, 0).mulPosition(matrix),
                          right - left, bottom - top, depth, color)),
                  (text, x, y, matrix) -> submissions.add(new Submission(text,
                         new Vector3f(x, y, 0).mulPosition(matrix),
                         new Vector3f(x, y + 8, 0).mulPosition(matrix),
                          new Vector3f(x + 6, y + 8, 0).mulPosition(matrix))), () -> flushes++);
    }

    private void render(IntFunction<LocalSpeechClient.Speaker> resolver) {
        render(RECEIVED + 1, MOON, resolver, false, (from, to) -> true, (from, to) -> true);
    }

    @Test
    void trackedPeerSubmitsTextAtCameraRelativeEyeAndFlushes() {
        UUID uuid = UUID.randomUUID();
        accept(LocalSpeechPayload.Mode.SAY, uuid);
        render(id -> speaker(uuid, 1.8));

        assertEquals(2, submissions.size());
        assertEquals("Alice Smith", text(submissions.getFirst()));
        assertEquals("peer speech", text(submissions.get(1)));
        // The panel bottom clears the 1.8m bounding box by .22m; glyphs sit inside it.
        Vector3f location = submissions.get(1).worldRelativePosition();
        float scale = scaleAt(new Vec3(12, 21.6, 31));
        assertEquals(2 - "peer speech".length() * 6 * scale / 2, location.x(), 1e-5);
        assertEquals(2.02 + 14 * scale, location.y(), 1e-5);
        assertEquals(1, location.z(), 1e-5);
        assertEquals(2.02, panels.getFirst().bottomCenter().y(), 1e-5);
        assertEquals(1, flushes);
    }

    @Test
    void hiddenGuiDisablesWorldBubblePhase() {
        assertFalse(LocalSpeechClient.worldBubblesAllowed(true));
        assertTrue(LocalSpeechClient.worldBubblesAllowed(false));
    }

    @Test
    void nearCalloutStateIsPrunedToTranscriptSpeakersAndBounded() {
        UUID active = UUID.randomUUID();
        transcript.accept(7, "Alice", 0xffffff, "hello", 12, 20, 31, MOON, active, RECEIVED);
        for (int i = 0; i < 40; i++) LocalSpeechClient.nearCalloutState.put(UUID.randomUUID(), true);
        LocalSpeechClient.nearCalloutState.put(active, true);
        LocalSpeechClient.pruneNearCalloutState(transcript);
        assertEquals(java.util.Set.of(active), LocalSpeechClient.nearCalloutState.keySet());
    }

    @Test
    void bubblesStyleHeaderAndUtterancesByModeWithoutChangingPanelContrast() {
        UUID uuid = UUID.randomUUID();
        for (LocalSpeechPayload.Mode mode : List.of(LocalSpeechPayload.Mode.SAY,
                LocalSpeechPayload.Mode.WHISPER, LocalSpeechPayload.Mode.SHOUT)) {
            transcript.clear();
            submissions.clear();
            panels.clear();
            accept(mode, uuid);
            render(id -> speaker(uuid, 1.8));
            assertEquals(2, submissions.size());
            Component header = submissions.getFirst().styled();
            assertEquals("Alice Smith", header.getString());
            assertEquals(0xabcdef, header.getStyle().getColor().getValue());
            assertTrue(header.getStyle().isBold());
            Component styled = submissions.get(1).styled();
            assertEquals("peer speech", styled.getString(), "no verb in bubble");
            assertEquals(mode == LocalSpeechPayload.Mode.WHISPER ? 0xaaaaaa : 0xffffff,
                    styled.getStyle().getColor().getValue());
            assertEquals(mode == LocalSpeechPayload.Mode.SHOUT, styled.getStyle().isBold());
            Panel panel = panels.getFirst();
            assertEquals(0xf2111319, panel.color());
            Panel tab = panels.get(1);
            assertEquals(0xff090c13, tab.color());
            assertTrue(tab.topLeft().y() > panel.topLeft().y());
            assertTrue(tab.bottomLeft().y() < panel.topLeft().y());
            assertTrue(tab.depth() > panel.depth(), "name tab must be in front of body");
            assertTrue(width(styled) <= panel.width() - 12);
            assertTrue(width(header) <= panel.width() - 12);
            assertTrue(submissions.getFirst().worldRelativePosition().y()
                    > submissions.get(1).worldRelativePosition().y());
            Vector3f front = new Vector3f(panel.bottomLeft()).sub(panel.topLeft())
                    .cross(new Vector3f(panel.bottomRight()).sub(panel.topLeft()));
            assertTrue(front.z() > 0, "every mode retains camera-facing panel winding");
            Vector3f tabFront = new Vector3f(tab.bottomLeft()).sub(tab.topLeft())
                    .cross(new Vector3f(tab.bottomRight()).sub(tab.topLeft()));
            assertTrue(tabFront.z() > 0, "name tab must be camera-facing too");
        }
    }

    @Test
    void shoutWrapUsesBoldWidthForRowsEllipsisAndPanelBounds() {
        UUID uuid = UUID.randomUUID();
        String utterance = "abcdefghijklmno abcdefghijklmno abcdefghijklmno abcdefghijklmno "
                + "abcdefghijklmno abcdefghijklmno";
        transcript.accept(7, "Alice Smith", 0xffffff, utterance, 12, 20, 31,
                MOON, uuid, RECEIVED, LocalSpeechPayload.Mode.SHOUT);
        render(id -> speaker(uuid, 1.8));
        assertEquals(4, submissions.size());
        assertTrue(text(submissions.getLast()).endsWith("…"));
        int maxWidth = 0;
        for (Submission row : submissions.subList(1, submissions.size())) {
            assertTrue(row.styled().getStyle().isBold());
            assertTrue(width(row.styled()) <= 100, "bold glyph width must fit row including ellipsis");
            maxWidth = Math.max(maxWidth, width(row.styled()));
        }
        assertEquals(Math.max(maxWidth, width(submissions.getFirst().styled())) + 12, panels.getFirst().width());
        assertEquals(51, panels.getFirst().height());
        float scale = scaleAt(new Vec3(12, 21.6, 31));
        for (Submission row : submissions) {
            float left = row.worldRelativePosition().x();
            assertEquals(2 - width(row.styled()) * scale / 2, left, 1e-5);
            assertTrue(left >= panels.getFirst().bottomLeft().x() + 5 * scale);
            assertTrue(left + width(row.styled()) * scale <= panels.getFirst().bottomRight().x() - 5 * scale);
        }
        assertEquals(1, flushes);
    }

    @Test
    void mentionColorSurvivesHardWrapForFullAuthoredNameOnly() throws Exception {
        var field = LocalSpeechClient.class.getDeclaredField("localName");
        field.setAccessible(true);
        field.set(null, "Morgan Alexandra");
        try {
            UUID uuid = UUID.randomUUID();
            transcript.accept(7, "Morgan Alexandra", 0xffffff,
                    "Morgan Alexandra! Morgan Alexandrian Morgan Alexandra", 12, 20, 31, MOON, uuid, RECEIVED);
            render(id -> speaker(uuid, 1.8));
            List<Component> rows = submissions.subList(1, submissions.size()).stream().map(Submission::styled).toList();
            assertTrue(rows.size() <= 3);
            assertTrue(rows.stream().anyMatch(row -> row.getString().contains("Morgan")
                    && row.getSiblings().stream().anyMatch(part -> part.getStyle().getColor() != null
                            && part.getStyle().getColor().getValue() == DirectionalCueTintMesh.MENTION_RGB)),
                    "visible matched fragments retain cue orange across row boundaries");
            assertTrue(rows.stream().flatMap(row -> row.getSiblings().stream())
                    .filter(part -> part.getString().contains("Alexandrian"))
                    .noneMatch(part -> part.getStyle().getColor() != null
                            && part.getStyle().getColor().getValue() == DirectionalCueTintMesh.MENTION_RGB));
        } finally {
            field.set(null, null);
        }
    }

    @Test
    void absentEntityUsesPacketPositionForTextOnlyFallbackAndFlushes() {
        accept(LocalSpeechPayload.Mode.SAY, null);
        render(id -> null);
        assertEquals(2, submissions.size());
        assertEquals("Alice Smith", text(submissions.getFirst()));
        assertEquals("peer speech", text(submissions.get(1)));
        float scale = scaleAt(new Vec3(12, 21.5, 31));
        assertEquals(2 - "peer speech".length() * 6 * scale / 2,
                 submissions.get(1).worldRelativePosition().x(), 1e-5);
        assertEquals(2.02 + 14 * scale, submissions.get(1).worldRelativePosition().y(), 1e-5);
        assertEquals(2.02, panels.getFirst().bottomCenter().y(), 1e-5);
        assertEquals(1, submissions.get(1).worldRelativePosition().z(), 1e-5);
        assertEquals(1, flushes);
    }

    @Test
    void trackedAndFallbackGlyphQuadsFaceCameraAfterRotation() {
        Quaternionf rotation = new Quaternionf().rotateY(.7f).rotateX(-.35f);
        Vector3f towardCamera = new Vector3f(0, 0, 1).rotate(rotation);
        for (boolean tracked : new boolean[] {true, false}) {
            transcript.clear();
            submissions.clear();
            panels.clear();
            flushes = 0;
            UUID uuid = tracked ? UUID.randomUUID() : null;
            accept(LocalSpeechPayload.Mode.SAY, uuid);
            render(RECEIVED + 1, MOON,
                     id -> tracked ? speaker(uuid, 1.8) : null,
                    false, (from, to) -> true, (from, to) -> true, rotation);

            assertEquals(2, submissions.size());
            assertEquals("Alice Smith", text(submissions.getFirst()));
            assertEquals("peer speech", text(submissions.get(1)));
            assertEquals(1, flushes);
            for (Submission glyph : submissions) {
                // BakedGlyph's first three vertices are top-left, bottom-left, bottom-right.
                Vector3f front = new Vector3f(glyph.glyphBottomLeft()).sub(glyph.worldRelativePosition())
                        .cross(new Vector3f(glyph.glyphBottomRight()).sub(glyph.worldRelativePosition()));
                assertTrue(front.dot(towardCamera) > 0, "glyph must be front-facing for "
                         + (tracked ? "tracked" : "fallback") + " speech with text culling enabled");
            }
            Panel panel = panels.getFirst();
            Vector3f panelFront = new Vector3f(panel.bottomLeft()).sub(panel.topLeft())
                    .cross(new Vector3f(panel.bottomRight()).sub(panel.topLeft()));
            assertTrue(panelFront.dot(towardCamera) > 0);
            Panel tab = panels.get(1);
            Vector3f tabFront = new Vector3f(tab.bottomLeft()).sub(tab.topLeft())
                    .cross(new Vector3f(tab.bottomRight()).sub(tab.topLeft()));
            assertTrue(tabFront.dot(towardCamera) > 0);
            assertTrue(panel.depth() < 0);
            assertTrue((panel.color() >>> 24) >= 0xe0);
            assertTrue((panel.color() & 0xffffff) < 0x303030);
        }
    }

    @Test
    void selfNeverSubmitsOrFlushes() {
        UUID uuid = UUID.randomUUID();
        accept(LocalSpeechPayload.Mode.SAY, uuid);
        render(RECEIVED + 1, MOON,
                 id -> speaker(uuid, 1.8),
                true, (from, to) -> true, (from, to) -> true);
        assertTrue(submissions.isEmpty());
        assertEquals(0, flushes);
    }

    @Test
    void anonymousMuffledNeverSubmitsEvenWithResolvableEntityId() {
        accept(LocalSpeechPayload.Mode.W_MUFFLED, null);
        render(id -> speaker(UUID.randomUUID(), 1.8));
        assertTrue(submissions.isEmpty());
        assertTrue(panels.isEmpty());
        assertEquals(0, flushes);
    }

    @Test
    void headerUsesAuthoredNameAndColorAndEllipsizesWithinPanel() {
        UUID uuid = UUID.randomUUID();
        String name = "ABCDEFGHIJKLMNOPQRSTUVWX ABCDEFGHIJKLMNOPQRSTUVWX";
        transcript.accept(7, name, 0x000000, "hello", 12, 20, 31, MOON, uuid, RECEIVED);
        render(id -> speaker(uuid, 1.8));
        assertEquals(2, submissions.size());
        Component header = submissions.getFirst().styled();
        assertTrue(header.getString().endsWith("…"));
        assertTrue(name.startsWith(header.getString().substring(0, header.getString().length() - 1)));
        assertTrue(width(header) <= 100);
        assertTrue(header.getStyle().isBold());
        assertEquals(width(header) + 12, panels.getFirst().width());
        assertEquals(width(header) + 12, panels.get(1).width());
        assertEquals(0xff090c13, panels.get(1).color());
        assertTrue(header.getStyle().getColor().getValue() >= 0x969696,
                "dark authored colors must remain legible on the dark panel");
        assertEquals(name, transcript.history().getLast().name());
        assertEquals("hello", text(submissions.get(1)));
    }

    @Test
    void blockedOrUnloadedSightlineAndWrongWorldNeverSubmit() {
        accept(LocalSpeechPayload.Mode.SAY, null);
        render(RECEIVED + 1, MOON, id -> null, false,
                (from, to) -> true, (from, to) -> false);
        render(RECEIVED + 1, MOON, id -> null, false,
                (from, to) -> false, (from, to) -> fail("must not raytrace unloaded chunks"));
        render(RECEIVED + 1, ResourceLocation.parse("minecraft:overworld"), id -> null,
                false, (from, to) -> true, (from, to) -> true);
        assertTrue(submissions.isEmpty());
        assertEquals(0, flushes);
    }

    @Test
    void changedUuidIsDiscardedInsteadOfTurningIntoFallback() {
        UUID uuid = UUID.randomUUID();
        accept(LocalSpeechPayload.Mode.SAY, uuid);
        render(id -> speaker(UUID.randomUUID(), 1.8));
        render(id -> null);
        assertTrue(submissions.isEmpty());
        assertTrue(transcript.candidates().isEmpty());
        assertEquals(0, flushes);
    }

    @Test
    void authoredUuidRejectsNearbyReusedIdBeforeFirstBinding() {
        UUID authored = UUID.randomUUID();
        var packet = new LocalSpeechPayload(7, authored, "Alice Smith", 0xabcdef, "peer speech",
                12, 20, 31, MOON, LocalSpeechPayload.Mode.SAY,
                LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.NO_BEARING,
                LocalSpeechPayload.DistanceTier.NONE);
        LocalSpeechClient.acceptSpeech(transcript, packet, MOON, null, false, RECEIVED);
        assertEquals(authored, transcript.history().getFirst().speakerUuid());
        render(id -> speaker(UUID.randomUUID(), 1.8));
        assertTrue(submissions.isEmpty());
        assertTrue(transcript.candidates().isEmpty());
    }

    @Test
    void authoredUuidBindsWhenMatchingEntityAppearsAndPausesWhileAbsent() {
        UUID authored = UUID.randomUUID();
        var packet = new LocalSpeechPayload(7, authored, "Alice Smith", 0xabcdef, "peer speech",
                12, 20, 31, MOON, LocalSpeechPayload.Mode.SAY,
                LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.NO_BEARING,
                LocalSpeechPayload.DistanceTier.NONE);
        LocalSpeechClient.acceptSpeech(transcript, packet, MOON, null, false, RECEIVED);
        assertEquals(LocalSpeechTranscript.Placement.FALLBACK, LocalSpeechTranscript.placement(
                transcript.candidates().getFirst(), MOON, RECEIVED + 1, null, false, false, 1, true));
        render(id -> speaker(authored, 1.8));
        assertEquals(authored, transcript.candidates().getFirst().entityUuid());
        submissions.clear();
        panels.clear();
        render(id -> null);
        assertTrue(submissions.isEmpty(), "bound bubbles must never float at packet coordinates");
        assertEquals(1, transcript.candidates().size());
        render(id -> speaker(authored, 1.8));
        assertEquals(2, submissions.size());
    }

    @Test
    void identityComparisonRejectsNearbyIdReuseAndSelfIdReuse() {
        UUID authored = UUID.randomUUID();
        Vec3 packetPosition = new Vec3(12, 20, 31);
        assertFalse(LocalSpeechClient.matchesSpeaker(authored, UUID.randomUUID(), packetPosition,
                12, 20, 31), "a reused ID directly on the packet coordinate cannot resolve at receipt");
        assertTrue(LocalSpeechClient.matchesSpeaker(authored, authored, packetPosition, 12, 20, 31));
        assertFalse(LocalSpeechClient.matchesSpeaker(LocalSpeechPayload.NO_UUID, authored, packetPosition,
                12, 20, 31), "legacy clear packets cannot resolve arbitrary real entities");
        assertFalse(LocalSpeechClient.sameSpeaker(7, authored, 7, UUID.randomUUID()));
        assertTrue(LocalSpeechClient.sameSpeaker(7, authored, 7, authored));
        assertFalse(LocalSpeechClient.sameSpeaker(7, LocalSpeechPayload.NO_UUID, 7, authored));
        assertFalse(LocalSpeechClient.sameSpeaker(7, authored, 8, authored));
    }

    @Test
    void interpolatedFeetEyeAndCameraMovePanelsContinuouslyWithinATick() {
        UUID uuid = UUID.randomUUID();
        accept(LocalSpeechPayload.Mode.SAY, uuid);
        Vec3 oldFeet = new Vec3(12, 20, 31);
        Vec3 newFeet = new Vec3(13, 20.4, 31);
        Vec3 oldEye = new Vec3(12, 21.6, 31);
        Vec3 newEye = new Vec3(13, 22, 31);
        for (double partial : new double[] {0, .5, 1}) {
            panels.clear();
            submissions.clear();
            // Both feet and eye follow the same render partial, while the camera moves independently.
            Vec3 camera = CAMERA.add(partial * .2, 0, 0);
            LocalSpeechClient.Speaker peer = new LocalSpeechClient.Speaker(uuid,
                    oldFeet.lerp(newFeet, partial), newFeet, oldEye.lerp(newEye, partial), 1.8);
            render(RECEIVED + 1, MOON, id -> peer, false,
                    (from, to) -> {
                        assertEquals(camera, from);
                        assertEquals(oldEye.lerp(newEye, partial), to);
                        return true;
                    }, (from, to) -> true, new Quaternionf(), camera);

            assertEquals(2, panels.size());
            assertEquals(2, submissions.size());
            float expectedX = (float) (oldFeet.x + partial - camera.x);
            float expectedHeadY = (float) (oldFeet.y + partial * .4 + 1.8 + .22 - camera.y);
            float scale = (float) Math.max(.009, Math.min(.024,
                    .009 + Math.max(0, peer.eye().distanceTo(camera) - 3) * .00085));
            assertEquals(expectedX, panels.getFirst().bottomCenter().x(), 1e-5);
            assertEquals(expectedHeadY, panels.getFirst().bottomCenter().y(), 1e-5);
            assertEquals(expectedHeadY + 14 * scale, submissions.get(1).worldRelativePosition().y(), 1e-5);
            assertEquals(expectedX - "peer speech".length() * 6 * scale / 2,
                    submissions.get(1).worldRelativePosition().x(), 1e-5);
        }
        assertEquals(3, flushes);
    }

    @Test
    void interpolatedFeetCannotBindAnIdWhoseTickPositionDisagreesWithPacket() {
        accept(LocalSpeechPayload.Mode.SAY, null);
        UUID uuid = UUID.randomUUID();
        render(id -> new LocalSpeechClient.Speaker(uuid, new Vec3(12, 20, 31),
                new Vec3(20, 20, 31), new Vec3(12, 21.6, 31), 1.8));
        assertTrue(submissions.isEmpty());
        assertTrue(transcript.candidates().isEmpty(), "conflicting ID must not turn into a packet fallback");
        assertEquals(0, flushes);
    }

    @Test
    void fourSecondLifetimePreventsSubmission() {
        accept(LocalSpeechPayload.Mode.SAY, null);
        render(RECEIVED + 4_000, MOON, id -> null, false,
                (from, to) -> true, (from, to) -> true);
        assertTrue(submissions.isEmpty());
        assertEquals(0, flushes);
    }

    @Test
    void shortAndTallHeadsClearPanelBottomWithoutUsingEyeHeight() {
        UUID uuid = UUID.randomUUID();
        for (double height : new double[] {.9, 2.6}) {
            transcript.clear();
            panels.clear();
            submissions.clear();
            accept(LocalSpeechPayload.Mode.SAY, uuid);
            render(id -> speaker(uuid, height));
            assertEquals(height + .22, panels.getFirst().bottomCenter().y(), 1e-5);
        }
    }

    @Test
    void nearAndFarPanelsStayBounded() {
        UUID uuid = UUID.randomUUID();
        accept(LocalSpeechPayload.Mode.SAY, uuid);
        render(id -> speaker(uuid, 1.8));
        float nearWidth = panels.getFirst().bottomRight().distance(panels.getFirst().bottomLeft());
        assertTrue(nearWidth < 1.2);
        panels.clear();
        submissions.clear();
        render(RECEIVED + 1, MOON, id -> new LocalSpeechClient.Speaker(uuid,
                new Vec3(10, 20, 49), new Vec3(10, 21.6, 49), 1.8), false,
                (from, to) -> true, (from, to) -> true);
        assertEquals(2, panels.size());
        float farWidth = panels.getFirst().bottomRight().distance(panels.getFirst().bottomLeft());
        assertTrue(farWidth > nearWidth);
        assertTrue(farWidth < 2.7);
    }

    @Test
    void wordWrappingEllipsisAndLongTokensStayInsideThreeShortRows() {
        UUID uuid = UUID.randomUUID();
        String text = "one two three four five six seven eight nine ten eleven twelve "
                + "supercalifragilisticexpialidocious thirteen fourteen fifteen sixteen";
        transcript.accept(7, "Alice", 0xffffff, text, 12, 20, 31, MOON, uuid, RECEIVED);
        render(id -> speaker(uuid, 1.8));
        assertEquals(4, submissions.size());
        assertTrue(text(submissions.getLast()).endsWith("…"));
        for (Submission row : submissions.subList(1, submissions.size())) assertTrue(width(row.styled()) <= 100);
        assertEquals(2, panels.size());
        assertEquals(51, panels.getFirst().height());
        assertEquals(text, transcript.history().getLast().text(), "ellipsis never changes U/transcript text");
    }

    @Test
    void unbrokenLongWordSplitsOnCodePointBoundary() {
        UUID uuid = UUID.randomUUID();
        transcript.accept(7, "Alice", 0xffffff,
                "supercalifragilisticexpialidocioussupercalifragilisticexpialidocious",
                12, 20, 31, MOON, uuid, RECEIVED);
        render(id -> speaker(uuid, 1.8));
        assertEquals(4, submissions.size());
        assertTrue(text(submissions.getLast()).endsWith("…"));
        for (Submission row : submissions.subList(1, submissions.size())) assertTrue(width(row.styled()) <= 100);
    }

    @Test
    void fourRetainedPanelsStackNewestNearestHeadWithoutOverlap() {
        UUID uuid = UUID.randomUUID();
        for (int i = 0; i < 4; i++)
            transcript.accept(7, "Alice", 0xffffff, "line " + i, 12, 20, 31, MOON, uuid, RECEIVED + i);
        render(id -> speaker(uuid, 1.8));
        assertEquals(8, panels.size());
        assertEquals("Alice", text(submissions.getFirst()));
        assertEquals("line 3", text(submissions.get(1)));
        for (int i = 1; i < 4; i++)
            assertTrue(panels.get(i * 2).bottomCenter().y() > panels.get((i - 1) * 2 + 1).topLeft().y());
        assertEquals(4, transcript.candidates().size());
        assertEquals(1, flushes);
    }

    @Test
    void fourFullHeightPanelsFitAtNearAndFarWithSteepCameraPitch() {
        UUID uuid = UUID.randomUUID();
        String longText = "one two three four five six seven eight nine ten eleven twelve "
                + "thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty";
        Quaternionf rotation = new Quaternionf().rotateY(.45f).rotateX(-1.3f);
        Vector3f screenUp = new Vector3f(0, 1, 0).rotate(rotation);
        Vector3f towardCamera = new Vector3f(0, 0, 1).rotate(rotation);
        for (Vec3 position : List.of(new Vec3(12, 20, 31), new Vec3(10, 20, 53))) {
            transcript.clear();
            panels.clear();
            submissions.clear();
            flushes = 0;
            for (int i = 0; i < 5; i++)
                transcript.accept(7, "Alice", 0xffffff, "entry " + i + " " + longText,
                        position.x, position.y, position.z, MOON, uuid, RECEIVED + i);
            assertEquals(4, transcript.candidates().size(), "fifth packet evicts the oldest bubble");
            assertTrue(transcript.candidates().stream().noneMatch(b -> b.line().text().startsWith("entry 0")));
            LocalSpeechClient.Speaker peer = new LocalSpeechClient.Speaker(uuid, position,
                    position.add(0, 1.6, 0), 1.8);
            render(RECEIVED + 5, MOON, id -> peer, false,
                    (from, to) -> true, (from, to) -> true, rotation);

            assertEquals(8, panels.size());
            assertEquals(16, submissions.size());
            assertEquals("Alice", text(submissions.getFirst()));
            assertTrue(text(submissions.get(1)).startsWith("entry 4"), "newest nearest head");
            for (Panel panel : panels) {
                Vector3f front = new Vector3f(panel.bottomLeft()).sub(panel.topLeft())
                        .cross(new Vector3f(panel.bottomRight()).sub(panel.topLeft()));
                assertTrue(front.dot(towardCamera) > 0, "pitched panel winding must face camera");
            }
            for (int i = 0; i < 4; i++) {
                Panel body = panels.get(i * 2), tab = panels.get(i * 2 + 1);
                assertTrue(tab.topLeft().dot(screenUp) > body.topLeft().dot(screenUp));
                assertTrue(tab.bottomLeft().dot(screenUp) < body.topLeft().dot(screenUp));
            }
            float head = new Vector3f((float) (position.x - CAMERA.x),
                    (float) (position.y + 1.8 - CAMERA.y), (float) (position.z - CAMERA.z)).dot(screenUp);
            assertEquals(.22, panels.getFirst().bottomCenter().dot(screenUp) - head, 1e-4);
            for (int i = 1; i < 4; i++) {
                float previousTop = panels.get((i - 1) * 2 + 1).topLeft().dot(screenUp);
                float nextBottom = panels.get(i * 2).bottomCenter().dot(screenUp);
                assertTrue(nextBottom - previousTop >= .059, "billboard-projected panels must not overlap");
            }
            float highestTop = panels.getLast().topLeft().dot(screenUp);
            assertTrue(highestTop - head < 5.8, "four panels and tabs remain world-space bounded");
            float panelWidth = panels.getFirst().bottomRight().distance(panels.getFirst().bottomLeft());
            assertTrue(panelWidth < 2.7);
            assertEquals(1, flushes);
        }
    }

    @Test
    void pitchedFallbackStackUsesOnlyPacketAnchor() {
        for (int i = 0; i < 2; i++)
            transcript.accept(7, "Alice", 0xffffff, "fallback " + i,
                    12, 20, 31, MOON, null, RECEIVED + i);
        Quaternionf rotation = new Quaternionf().rotateX(-1.3f);
        Vector3f up = new Vector3f(0, 1, 0).rotate(rotation);
        render(RECEIVED + 2, MOON, id -> null, false,
                (from, to) -> true, (from, to) -> true, rotation);
        assertEquals(4, panels.size());
        assertEquals("Alice", text(submissions.getFirst()));
        assertEquals("fallback 1", text(submissions.get(1)));
        Vector3f packetHead = new Vector3f(2, 1.8f, 1);
        assertEquals(.22, panels.getFirst().bottomCenter().dot(up) - packetHead.dot(up), 1e-4);
        assertTrue(panels.get(2).bottomCenter().dot(up) - panels.get(1).topLeft().dot(up) >= .059);
        assertEquals(1, flushes);
    }

    @Test
    void chunkTraversalRejectsThinDiagonalUnloadedCellsAndNeverRaytracesThem() {
        Vec3 from = new Vec3(15.9, 20, 15.8);
        Vec3 to = new Vec3(31.7, 20, 31.8);
        assertTrue(LocalSpeechClient.loadedSightline(from, to, (x, z) -> true));
        assertFalse(LocalSpeechClient.loadedSightline(from, to, (x, z) -> x != 1 || z != 0),
                "the 0.1m-wide crossed chunk is missed by 1m sampling");
        assertFalse(LocalSpeechClient.loadedSightline(new Vec3(15, 0, 15), new Vec3(17, 0, 17),
                (x, z) -> x != 0 || z != 1), "corner-touching chunks fail closed");
        assertFalse(LocalSpeechClient.loadedSightline(new Vec3(1, 0, 16), new Vec3(17, 0, 16),
                (x, z) -> z != 0), "rays along chunk edges check both adjacent rows");
        assertTrue(LocalSpeechClient.loadedSightline(new Vec3(-17, 0, -17), new Vec3(-1, 0, -1),
                (x, z) -> true), "negative chunk coordinates must traverse correctly");

        accept(LocalSpeechPayload.Mode.SAY, null);
        render(RECEIVED + 1, MOON, id -> null, false,
                (a, b) -> LocalSpeechClient.loadedSightline(from, to, (x, z) -> x != 1 || z != 0),
                (a, b) -> fail("must not raytrace across an unloaded cell"));
        assertTrue(panels.isEmpty());
        assertEquals(0, flushes);
    }

    private static void assertMissingChunk(Vec3 from, Vec3 to, int missingX, int missingZ) {
        assertTrue(LocalSpeechClient.loadedSightline(from, to, (x, z) -> true));
        assertFalse(LocalSpeechClient.loadedSightline(from, to,
                (x, z) -> x != missingX || z != missingZ),
                "ray from " + from + " to " + to + " touches chunk (" + missingX + ", " + missingZ + ")");
    }

    @Test
    void exactAlignedEdgesIncludeBothRowsAndColumnsInBothDirections() {
        for (Vec3[] ray : List.of(
                new Vec3[] {new Vec3(1, 0, 16), new Vec3(15, 0, 16)},
                new Vec3[] {new Vec3(16, 0, 1), new Vec3(16, 0, 15)},
                new Vec3[] {new Vec3(-31, 0, -16), new Vec3(-17, 0, -16)},
                new Vec3[] {new Vec3(-16, 0, -31), new Vec3(-16, 0, -17)})) {
            int x = (int) Math.floor(ray[0].x / 16);
            int z = (int) Math.floor(ray[0].z / 16);
            if (ray[0].x == x * 16.0) {
                assertMissingChunk(ray[0], ray[1], x, z);
                assertMissingChunk(ray[0], ray[1], x - 1, z);
                assertMissingChunk(ray[1], ray[0], x - 1, z);
            } else {
                assertMissingChunk(ray[0], ray[1], x, z);
                assertMissingChunk(ray[0], ray[1], x, z - 1);
                assertMissingChunk(ray[1], ray[0], x, z - 1);
            }
        }
    }

    @Test
    void boundaryEndpointsAndStationaryCornerRequireEveryTouchedChunk() {
        for (Vec3[] ray : List.of(
                new Vec3[] {new Vec3(17, 0, 5), new Vec3(16, 0, 5)},
                new Vec3[] {new Vec3(5, 0, 17), new Vec3(5, 0, 16)},
                new Vec3[] {new Vec3(-15, 0, 5), new Vec3(-16, 0, 5)},
                new Vec3[] {new Vec3(17, 0, 17), new Vec3(16, 0, 16)},
                new Vec3[] {new Vec3(16, 0, 16), new Vec3(16, 0, 16)},
                new Vec3[] {new Vec3(-16, 0, -16), new Vec3(-16, 0, -16)})) {
            int x = (int) Math.floor(ray[1].x / 16);
            int z = (int) Math.floor(ray[1].z / 16);
            for (int cx = x - (ray[1].x == x * 16.0 ? 1 : 0); cx <= x; cx++)
                for (int cz = z - (ray[1].z == z * 16.0 ? 1 : 0); cz <= z; cz++) {
                    assertMissingChunk(ray[0], ray[1], cx, cz);
                    assertMissingChunk(ray[1], ray[0], cx, cz);
                }
        }
    }

    @Test
    void unloadedBoundaryEndpointSkipsClipAndBubbleSubmission() {
        transcript.accept(7, "Alice", 0xffffff, "edge", 12, 20, 16, MOON, null, RECEIVED);
        render(RECEIVED + 1, MOON, id -> null, false,
                (from, to) -> LocalSpeechClient.loadedSightline(from, to, (x, z) -> z != 0),
                (from, to) -> fail("must not clip a sightline touching an unloaded endpoint chunk"));
        assertTrue(panels.isEmpty());
        assertTrue(submissions.isEmpty());
        assertEquals(0, flushes);
    }

    @Test
    void sightlineRemainsBoundedAndRejectsNonfiniteCoordinates() {
        assertFalse(LocalSpeechClient.loadedSightline(new Vec3(0, 0, 0), new Vec3(25, 0, 0),
                (x, z) -> true));
        assertFalse(LocalSpeechClient.loadedSightline(new Vec3(0, Double.NaN, 0), new Vec3(0, 0, 0),
                (x, z) -> true));
    }

    @Test
    void visibleMentionKeepsAuthoredFormattingButDoesNotCueWhileOccludedMentionDoes() {
        DirectionalCueOverlay.clear();
        try {
            var packet = new LocalSpeechPayload(7, "Alice Smith", 0xffffff, "Morgan, listen",
                    0, 0, -5, MOON, LocalSpeechPayload.Mode.SAY);
            var line = new LocalSpeechTranscript.Line(7, packet.actualName(), packet.rgb(), packet.text(),
                    packet.x(), packet.y(), packet.z(), RECEIVED, packet.mode());
            var authored = new LocalSpeechTranscript();
            LocalSpeechClient.acceptSpeech(authored, packet, MOON, null, false, RECEIVED);
            assertEquals("Morgan, listen", authored.history().getLast().text());
            assertTrue(authored.history().getLast().mentions("Morgan Hale"));
            LocalSpeechClient.admitCue(packet, line, null, null, false, true, RECEIVED);
            assertEquals(0, DirectionalCueOverlay.selected(CUE_PROJECTION, new Quaternionf(), 854, 480, RECEIVED));
            assertEquals(1, DirectionalCueOverlay.queued());
            DirectionalCueOverlay.clear();
            LocalSpeechClient.admitCue(packet, line, null, "Morgan Hale", false, true, RECEIVED);
            assertFalse(DirectionalCueOverlay.selectedMention(CUE_PROJECTION, new Quaternionf(), 854, 480, RECEIVED),
                    "a clear, on-screen visible bubble suppresses even its mention cue");
            assertEquals(1, DirectionalCueOverlay.queued());
            DirectionalCueOverlay.clear();
            LocalSpeechClient.admitCue(packet, line, null, "Morgan Hale", false, false, RECEIVED);
            assertTrue(DirectionalCueOverlay.selectedMention(CUE_PROJECTION, new Quaternionf(), 854, 480, RECEIVED),
                    "receipt-blocked speech remains eligible for a mention cue");
            assertEquals(1, DirectionalCueOverlay.queued());
        } finally { DirectionalCueOverlay.clear(); }
    }

    @Test
    void muffledPacketNeedsNoPositionOrMentionAndSelfClearSpeechNeverEnqueues() {
        DirectionalCueOverlay.clear();
        try {
            UUID uuid = UUID.randomUUID();
            var packet = new LocalSpeechPayload(7, uuid, "Alice Smith", LocalSpeechPayload.ANONYMOUS_RGB,
                    "Morgan", 0, 0, 0, MOON, LocalSpeechPayload.Mode.W_MUFFLED,
                    0, LocalSpeechPayload.BAND_LEVEL, LocalSpeechPayload.DistanceTier.NEAR);
            var muffled = new LocalSpeechTranscript.Line(0, packet.actualName(), packet.rgb(), packet.text(),
                    0, 0, 0, RECEIVED, LocalSpeechPayload.Mode.W_MUFFLED,
                    packet.azimuthSector(), packet.verticalBand());
            LocalSpeechClient.admitCue(packet, muffled, null, "Morgan Hale", false, false, RECEIVED);
            assertEquals(1, DirectionalCueOverlay.queued());
            assertFalse(DirectionalCueOverlay.selectedMention(CUE_PROJECTION, new Quaternionf(), 854, 480, RECEIVED));
            var selfPacket = new LocalSpeechPayload(7, "Morgan Hale", 0xffffff, "hello",
                    0, 0, -5, MOON, LocalSpeechPayload.Mode.SAY);
            var self = new LocalSpeechTranscript.Line(7, selfPacket.actualName(), selfPacket.rgb(),
                    selfPacket.text(), 0, 0, -5, RECEIVED, selfPacket.mode());
            LocalSpeechClient.admitCue(selfPacket, self, uuid, "Morgan Hale", true, false, RECEIVED);
            assertEquals(1, DirectionalCueOverlay.queued());
        } finally { DirectionalCueOverlay.clear(); }
    }

    @Test
    void cueReceiptRetainsVerifiedUuidForMovementAndUsesBubbleLifetime() {
        DirectionalCueOverlay.clear();
        try {
            UUID verified = UUID.randomUUID();
            String text = "x".repeat(40) + "😀";
            var packet = new LocalSpeechPayload(7, "Alice Smith", 0xffffff, text,
                    0, 0, -5, MOON, LocalSpeechPayload.Mode.SHOUT);
            var line = new LocalSpeechTranscript.Line(7, packet.actualName(), packet.rgb(), text,
                    packet.x(), packet.y(), packet.z(), RECEIVED, packet.mode());
            LocalSpeechClient.admitCue(packet, line, verified, null, false, false, RECEIVED);
            assertEquals(new Vec3(1, 2, 3), DirectionalCueOverlay.matchingPosition(verified, verified,
                    new Vec3(1, 2, 3)));
            assertEquals(new Vec3(20, 2, 3), DirectionalCueOverlay.matchingPosition(verified, verified,
                    new Vec3(20, 2, 3)), "the same entity can move after receipt");
            assertNull(DirectionalCueOverlay.matchingPosition(verified, UUID.randomUUID(), new Vec3(20, 2, 3)));
            assertEquals(1, DirectionalCueOverlay.queued());
            assertEquals(1, DirectionalCueOverlay.selected(CUE_PROJECTION, new Quaternionf(), 854, 480,
                    RECEIVED + LocalSpeechTranscript.bubbleLifetime(text) - 1));
            assertEquals(0, DirectionalCueOverlay.selected(CUE_PROJECTION, new Quaternionf(), 854, 480,
                    RECEIVED + LocalSpeechTranscript.bubbleLifetime(text)));
        } finally { DirectionalCueOverlay.clear(); }
    }

    @Test
    void unresolvedClearCueRetainsAuthoredUuidWithoutFollowingReusedId() {
        DirectionalCueOverlay.clear();
        try {
            UUID authored = UUID.randomUUID();
            var packet = new LocalSpeechPayload(7, authored, "Alice Smith", 0xffffff, "hello",
                    0, 0, -5, MOON, LocalSpeechPayload.Mode.SAY,
                    LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.NO_BEARING,
                    LocalSpeechPayload.DistanceTier.NONE);
            var transcript = new LocalSpeechTranscript();
            LocalSpeechClient.acceptSpeech(transcript, packet, MOON, null, false, RECEIVED);
            var line = transcript.history().getFirst();
            LocalSpeechClient.admitCue(packet, line, line.speakerUuid(), null, false, false, RECEIVED);
            assertEquals(1, DirectionalCueOverlay.queued());
            assertNull(DirectionalCueOverlay.matchingPosition(line.speakerUuid(), UUID.randomUUID(), Vec3.ZERO));
            assertEquals(Vec3.ZERO, DirectionalCueOverlay.matchingPosition(line.speakerUuid(), authored, Vec3.ZERO));
        } finally { DirectionalCueOverlay.clear(); }
    }

    @Test
    void receiptCooldownDoesNotDisplaceMentionAndMuffledPacketDoesNotDisclosePosition() {
        DirectionalCueOverlay.clear();
        try {
            UUID uuid = UUID.randomUUID();
            var mentionPacket = new LocalSpeechPayload(7, "Alice Smith", 0xffffff, "Morgan!",
                    0, 0, -5, MOON, LocalSpeechPayload.Mode.SAY);
            var mention = new LocalSpeechTranscript.Line(7, mentionPacket.actualName(), mentionPacket.rgb(),
                    mentionPacket.text(), 0, 0, -5, RECEIVED, mentionPacket.mode());
            LocalSpeechClient.admitCue(mentionPacket, mention, uuid, "Morgan Hale", false, false, RECEIVED);
            var ordinaryPacket = new LocalSpeechPayload(7, "Alice Smith", 0xffffff, "hello",
                    0, 0, -5, MOON, LocalSpeechPayload.Mode.SAY);
            var ordinary = new LocalSpeechTranscript.Line(7, ordinaryPacket.actualName(), ordinaryPacket.rgb(),
                    ordinaryPacket.text(), 0, 0, -5, RECEIVED + 100, ordinaryPacket.mode());
            LocalSpeechClient.admitCue(ordinaryPacket, ordinary, uuid, "Morgan Hale", false, false, RECEIVED + 100);
            assertEquals(1, DirectionalCueOverlay.queued());
            assertTrue(DirectionalCueOverlay.selectedMention(CUE_PROJECTION, new Quaternionf(), 854, 480,
                    RECEIVED + 100));

            DirectionalCueOverlay.clear();
            var muffledPacket = new LocalSpeechPayload(7, uuid, "Alice Smith", LocalSpeechPayload.ANONYMOUS_RGB,
                    "Morgan!", 0, 0, 0, MOON, LocalSpeechPayload.Mode.W_MUFFLED,
                    4, LocalSpeechPayload.BAND_LEVEL, LocalSpeechPayload.DistanceTier.FAR);
            var masked = new LocalSpeechTranscript();
            LocalSpeechClient.acceptSpeech(masked, muffledPacket, MOON, null, false, RECEIVED);
            var line = masked.history().getFirst();
            assertEquals(0, line.speakerId());
            assertEquals(Vec3.ZERO, new Vec3(line.x(), line.y(), line.z()));
            assertEquals(LocalSpeechPayload.ANONYMOUS_RGB, line.rgb());
            assertFalse(line.mentions("Morgan Hale"));
            assertTrue(masked.candidates().isEmpty());
            LocalSpeechClient.admitCue(muffledPacket, line, null, "Morgan Hale", false, false, RECEIVED);
            assertEquals(1, DirectionalCueOverlay.queued(), "one speech packet creates one cue");
            assertFalse(DirectionalCueOverlay.selectedMention(CUE_PROJECTION, new Quaternionf(), 854, 480,
                    RECEIVED));
        } finally { DirectionalCueOverlay.clear(); }
    }

    @Test
    void worldOrSessionClearForgetsBearingsAndNoClipTouchesUnloadedChunks() {
        DirectionalCueOverlay.clear();
        var packet = new LocalSpeechPayload(7, "Alice Smith", 0xffffff, "hello",
                0, 0, -5, MOON, LocalSpeechPayload.Mode.SAY);
        var line = new LocalSpeechTranscript.Line(7, packet.actualName(), packet.rgb(), packet.text(),
                0, 0, -5, RECEIVED, packet.mode());
        LocalSpeechClient.admitCue(packet, line, null, null, false, false, RECEIVED);
        assertEquals(1, DirectionalCueOverlay.queued());
        LocalSpeechClient.clear(); // Called for world transitions and login/logout.
        assertEquals(0, DirectionalCueOverlay.queued());
        assertFalse(LocalSpeechClient.bubbleVisibleAtReceipt(Vec3.ZERO, new Vec3(0, 0, -5), false,
                LocalSpeechPayload.Mode.SAY, (from, to) -> false,
                (from, to) -> fail("never clip an unloaded sightline")));
        assertFalse(LocalSpeechClient.bubbleVisibleAtReceipt(Vec3.ZERO, new Vec3(0, 0, -5), false,
                LocalSpeechPayload.Mode.SAY, (from, to) -> true, (from, to) -> false));
        assertFalse(LocalSpeechClient.bubbleVisibleAtReceipt(Vec3.ZERO, new Vec3(0, 0, -20), false,
                LocalSpeechPayload.Mode.SAY, (from, to) -> fail("out of bubble range"),
                (from, to) -> fail("out of bubble range")));
    }

    @Test
    void oldGlobalActionbarAndLastCueThrottleStayRemoved() throws Exception {
        Path relative = Path.of("src/main/java/com/juicyslew/moonstation14/ms14/chat/client/LocalSpeechClient.java");
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (directory != null && !Files.isRegularFile(directory.resolve(relative))) directory = directory.getParent();
        assertNotNull(directory, "test must find the checked-out speech client source");
        String source = Files.readString(directory.resolve(relative));
        assertFalse(source.contains("Speech nearby"));
        assertFalse(source.contains("setOverlayMessage("));
        assertFalse(source.contains("lastCue"));
    }
}
