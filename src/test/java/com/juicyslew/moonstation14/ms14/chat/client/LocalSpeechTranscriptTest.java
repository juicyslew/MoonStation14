package com.juicyslew.moonstation14.ms14.chat.client;

import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LocalSpeechTranscriptTest {
    @Test
    void boundedHistoryAndBubbles() {
        LocalSpeechTranscript transcript = new LocalSpeechTranscript();
        for (int i = 0; i < 140; i++)
            transcript.accept(i, "Name Test", 0xffffff, "hello", 0, 0, 0, "world", UUID.randomUUID(), i);
        assertEquals(100, transcript.history().size());
        assertEquals(24, transcript.bubbles().size());
        assertEquals(40, transcript.history().getFirst().speakerId());
        transcript.accept(139, "Name Test", 0, "again", 0, 0, 0, "world", UUID.randomUUID(), 141);
        transcript.accept(139, "Name Test", 0, "third", 0, 0, 0, "world", UUID.randomUUID(), 142);
        assertEquals(3, transcript.bubbles().stream().filter(b -> b.line().speakerId() == 139).count());
        assertEquals(24, transcript.bubbles().size());
    }

    @Test
    void fourPerSpeakerAndFifthEvictsOldestWithoutAffectingOtherSpeakers() {
        LocalSpeechTranscript transcript = new LocalSpeechTranscript();
        UUID source = UUID.randomUUID();
        transcript.accept(2, "Other", 0, "other", 0, 0, 0, "moon", UUID.randomUUID(), 0);
        for (int i = 1; i <= 4; i++) {
            transcript.accept(1, "Speaker", 0, "speech " + i, 0, 0, 0, "moon", source, i);
        }
        assertEquals(java.util.List.of("other", "speech 1", "speech 2", "speech 3", "speech 4"),
                transcript.bubbles().stream().map(b -> b.line().text()).toList());
        transcript.accept(1, "Speaker", 0, "speech 5", 0, 0, 0, "moon", source, 5);
        assertEquals(java.util.List.of("other", "speech 2", "speech 3", "speech 4", "speech 5"),
                transcript.bubbles().stream().map(b -> b.line().text()).toList());
    }

    @Test
    void lifetimeScalesByVisibleCodePointsAndExpiresOutOfArrivalOrder() {
        LocalSpeechTranscript transcript = new LocalSpeechTranscript();
        UUID source = UUID.randomUUID();
        transcript.accept(1, "Speaker", 0, "x".repeat(100), 0, 0, 0, "moon", source, 0);
        var medium = transcript.bubbles().getFirst();
        assertEquals(6_400, medium.expiresAt());
        transcript.accept(2, "Speaker", 0, "short", 0, 0, 0, "moon", source, 1);
        var shortBubble = transcript.bubbles().getLast();
        assertEquals(4_001, shortBubble.expiresAt());
        transcript.expire("moon", 4_000);
        assertEquals(2, transcript.bubbles().size());
        transcript.expire("moon", 4_001);
        assertEquals(java.util.List.of(medium), transcript.bubbles());
        transcript.expire("moon", 6_400);
        assertTrue(transcript.bubbles().isEmpty());
    }

    @Test
    void lifetimeIsBoundedForMaxPayloadBlankAndUnicode() {
        LocalSpeechTranscript transcript = new LocalSpeechTranscript();
        UUID source = UUID.randomUUID();
        String[] texts = {"", " \t\n ", "x".repeat(40), "x".repeat(40) + "😀", "x".repeat(190),
                "x".repeat(256), "x".repeat(100_000)};
        long[] lifetimes = {4_000, 4_000, 4_000, 4_040, 10_000, 10_000, 10_000};
        for (int i = 0; i < texts.length; i++) {
            transcript.accept(i, "Speaker", 0, texts[i], 0, 0, 0, "moon", source, 0);
            assertEquals(lifetimes[i], transcript.bubbles().getLast().expiresAt(), "input " + i);
            assertEquals(lifetimes[i], LocalSpeechTranscript.bubbleLifetime(texts[i]), "cue lifetime " + i);
        }
    }

    @Test
    void expiryDimensionAndSessionReset() {
        LocalSpeechTranscript transcript = new LocalSpeechTranscript();
        transcript.accept(1, "Name Test", 0, "text", 0, 0, 0, "moon", UUID.randomUUID(), 100);
        transcript.expire("moon", 4099);
        assertEquals(1, transcript.bubbles().size());
        transcript.expire("moon", 4100);
        assertTrue(transcript.bubbles().isEmpty());
        transcript.accept(2, "Name Test", 0, "text", 0, 0, 0, "moon", UUID.randomUUID(), 5000);
        transcript.expire("other", 5001);
        assertTrue(transcript.bubbles().isEmpty());
        assertEquals(2, transcript.history().size());
        transcript.clear();
        assertTrue(transcript.history().isEmpty());
        transcript.accept(3, "Name Test", 0, "unloaded", 0, 0, 0, "moon", null, 6000);
        assertTrue(transcript.bubbles().isEmpty());
    }

    @Test
    void lateTrackedSourceBindsOnlyWhilePendingInSameWorld() {
        LocalSpeechTranscript transcript = new LocalSpeechTranscript();
        UUID source = UUID.randomUUID();
        transcript.accept(7, "Alice Smith", 0xffffff, "hello", 2, 3, 4, "moon", null, 100);
        assertTrue(transcript.bubbles().isEmpty());
        transcript.bindPending("other", 101, line -> source);
        assertTrue(transcript.bubbles().isEmpty());
        transcript.accept(7, "Alice Smith", 0xffffff, "again", 2, 3, 4, "moon", source, null, 200,
                LocalSpeechPayload.Mode.SAY, LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.NO_BEARING);
        transcript.bindPending("moon", 201, line -> line.speakerId() == 7 ? source : null);
        assertEquals(1, transcript.bubbles().size());
        assertEquals(source, transcript.bubbles().getFirst().entityUuid());
        transcript.accept(8, "Alice Smith", 0xffffff, "too late", 2, 3, 4, "moon", null, 300);
        transcript.bindPending("moon", 4300, line -> source);
        assertTrue(transcript.bubbles().isEmpty());
    }

    @Test
    void pendingBindingRequiresAuthoredUuidNotJustTheReusedEntityId() {
        var transcript = new LocalSpeechTranscript();
        UUID authored = UUID.randomUUID();
        transcript.accept(7, "Alice Smith", 0xffffff, "hello", 2, 3, 4, "moon", authored, null, 100,
                LocalSpeechPayload.Mode.SAY, LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.NO_BEARING);
        transcript.bindPending("moon", 101, line -> UUID.randomUUID());
        assertTrue(transcript.candidates().isEmpty());
        assertEquals(authored, transcript.history().getFirst().speakerUuid());

        transcript.accept(7, "Alice Smith", 0xffffff, "again", 2, 3, 4, "moon", null, 200);
        transcript.bindPending("moon", 201, line -> authored);
        assertTrue(transcript.candidates().isEmpty(), "nil UUID fixtures cannot bind to arbitrary entities");
    }

    @Test
    void namedMuffledWhisperCannotBecomeBubbleThroughLateBinding() {
        LocalSpeechTranscript transcript = new LocalSpeechTranscript();
        transcript.accept(0, "Alice Smith", 0xffffff, "quiet", 0, 0, 0, "moon", null, 100,
                LocalSpeechPayload.Mode.W_MUFFLED, 4, 1);
        transcript.bindPending("moon", 101, line -> UUID.randomUUID());
        assertTrue(transcript.bubbles().isEmpty());
        assertTrue(transcript.candidates().isEmpty());
        var anonymous = new LocalSpeechTranscript.Bubble(transcript.history().getFirst(), "moon", null, 4100);
        assertEquals(LocalSpeechTranscript.Placement.MISMATCH,
                LocalSpeechTranscript.placement(anonymous, "moon", 101, null, false, false, 1, true));
    }

    @Test
    void clearUntrackedUtteranceFallsBackOnlyWithinLifetimeRangeAndSight() {
        LocalSpeechTranscript transcript = new LocalSpeechTranscript();
        transcript.accept(7, "Alice Smith", 0xffffff, "hello", 2, 3, 4, "moon", null, 100);
        var bubble = transcript.candidates().getFirst();
        assertEquals(LocalSpeechTranscript.Placement.FALLBACK,
                LocalSpeechTranscript.placement(bubble, "moon", 101, null, false, false, 15 * 15, true));
        assertEquals(LocalSpeechTranscript.Placement.OUT_OF_RANGE,
                LocalSpeechTranscript.placement(bubble, "moon", 101, null, false, false, 16 * 16, true));
        assertEquals(LocalSpeechTranscript.Placement.OCCLUDED,
                LocalSpeechTranscript.placement(bubble, "moon", 101, null, false, false, 1, false));
        assertEquals(LocalSpeechTranscript.Placement.WRONG_DIMENSION,
                LocalSpeechTranscript.placement(bubble, "other", 101, null, false, false, 1, true));
        assertEquals(LocalSpeechTranscript.Placement.EXPIRED,
                LocalSpeechTranscript.placement(bubble, "moon", 4100, null, false, false, 1, true));
        transcript.accept(8, "Alice Smith", 0xffffff, "shout", 2, 3, 4, "moon", null, 200,
                LocalSpeechPayload.Mode.SHOUT);
        assertEquals(LocalSpeechTranscript.Placement.FALLBACK,
                LocalSpeechTranscript.placement(transcript.candidates().getLast(), "moon", 201,
                        null, false, false, 24 * 24, true));
    }

    @Test
    void trackedBindingMovesButIdReuseAndSelfFailClosed() {
        LocalSpeechTranscript transcript = new LocalSpeechTranscript();
        UUID first = UUID.randomUUID();
        transcript.accept(7, "Alice Smith", 0xffffff, "hello", 2, 3, 4, "moon", first, null, 100,
                LocalSpeechPayload.Mode.SAY, LocalSpeechPayload.NO_BEARING, LocalSpeechPayload.NO_BEARING);
        var pending = transcript.candidates().getFirst();
        assertEquals(LocalSpeechTranscript.Placement.MISMATCH,
                LocalSpeechTranscript.placement(pending, "moon", 101, first, false, false, 1, true));
        assertEquals(LocalSpeechTranscript.Placement.TRACKED,
                LocalSpeechTranscript.placement(pending, "moon", 101, first, true, false, 1, true));
        transcript.bindPending("moon", 101, line -> first);
        var bound = transcript.candidates().getFirst();
        assertEquals(LocalSpeechTranscript.Placement.TRACKED,
                LocalSpeechTranscript.placement(bound, "moon", 102, first, false, false, 20 * 20, true));
        assertEquals(LocalSpeechTranscript.Placement.MISMATCH,
                LocalSpeechTranscript.placement(bound, "moon", 102, UUID.randomUUID(), true, false, 1, true));
        assertEquals(LocalSpeechTranscript.Placement.ABSENT,
                LocalSpeechTranscript.placement(bound, "moon", 102, null, false, false, 1, true));
        assertEquals(LocalSpeechTranscript.Placement.SELF,
                LocalSpeechTranscript.placement(pending, "moon", 102, null, false, true, 1, true));
        transcript.discard(bound);
        assertTrue(transcript.candidates().isEmpty());
    }

    @Test
    void mentionsOnlyWholeNameWordsWithoutAccountFallback() {
        assertTrue(LocalSpeechTranscript.mentions("hello aLiCe!", "Alice Smith"));
        assertTrue(LocalSpeechTranscript.mentions("SMITH?", "Alice Smith"));
        assertFalse(LocalSpeechTranscript.mentions("malice and smithsonian", "Alice Smith"));
        assertFalse(LocalSpeechTranscript.mentions("Alice", null));
    }

    @Test
    void mentionRangesHighlightRepeatedWholeWordsOnly() {
        String text = "Ann saw Joanna; ANN!";
        var ranges = LocalSpeechTranscript.mentionRanges(text, "Ann Hale");
        assertEquals(java.util.List.of("Ann", "ANN"), ranges.stream()
                .map(range -> text.substring(range[0], range[1])).toList());
        assertEquals(DirectionalCueTintMesh.MENTION_RGB,
                DirectionalCueTintMesh.style(LocalSpeechPayload.Mode.SAY, true, 1).red() << 16
                        | DirectionalCueTintMesh.style(LocalSpeechPayload.Mode.SAY, true, 1).green() << 8
                        | DirectionalCueTintMesh.style(LocalSpeechPayload.Mode.SAY, true, 1).blue());
    }

    @Test
    void modesPreserveOrderColorAndBubblePolicy() {
        LocalSpeechTranscript transcript = new LocalSpeechTranscript();
        UUID speaker = UUID.randomUUID();
        transcript.accept(8, "Alice Smith", 0x123456, "say", 1, 2, 3, "moon", speaker, 1,
                LocalSpeechPayload.Mode.SAY);
        transcript.accept(8, "Alice Smith", 0x123456, "quiet", 1, 2, 3, "moon", speaker, 2,
                LocalSpeechPayload.Mode.WHISPER);
        transcript.accept(8, "Alice Smith", 0x123456, "loud", 1, 2, 3, "moon", speaker, 3,
                LocalSpeechPayload.Mode.SHOUT);
        assertEquals(java.util.List.of("say", "quiet", "loud"),
                transcript.history().stream().map(LocalSpeechTranscript.Line::text).toList());
        assertEquals(java.util.List.of("Alice Smith:", "Alice Smith whispers:", "Alice Smith yells:"),
                transcript.history().stream().map(LocalSpeechTranscript.Line::speechLabel).toList());
        assertTrue(transcript.history().stream().allMatch(line -> line.rgb() == 0x123456));
        assertEquals(3, transcript.bubbles().size());
        assertEquals(java.util.List.of("say", "quiet", "loud"), transcript.bubbles().stream()
                .map(bubble -> bubble.line().text()).toList());
    }

    @Test
    void muffledHistoryKeepsNameAndBearingButNeverPreciseMetadataOrBubble() {
        LocalSpeechTranscript transcript = new LocalSpeechTranscript();
        transcript.accept(91, "Alice Smith", 0x123456, "Alice, garbled", 27, 30, -15, "moon",
                UUID.randomUUID(), 42, LocalSpeechPayload.Mode.W_MUFFLED, 4, 2);
        var line = transcript.history().getFirst();
        assertEquals("Alice Smith whispers:", line.speechLabel());
        assertEquals("Alice, garbled", line.text());
        assertEquals(0xffffff, line.rgb());
        assertEquals(0, line.speakerId());
        assertEquals(0, line.x());
        assertEquals(0, line.y());
        assertEquals(0, line.z());
        assertNull(line.speakerUuid());
        assertEquals(4, line.azimuthSector());
        assertEquals(2, line.verticalBand());
        assertFalse(line.mentions("Alice Smith"));
        assertTrue(transcript.bubbles().isEmpty());
    }

    @Test
    void chatPresentationUsesModeAndNeverHighlightsMuffledMentions() {
        LocalSpeechTranscript transcript = new LocalSpeechTranscript();
        for (var mode : LocalSpeechPayload.Mode.values()) {
            transcript.accept(mode == LocalSpeechPayload.Mode.W_MUFFLED ? 0 : 1,
                     "Alice Smith",
                    mode == LocalSpeechPayload.Mode.W_MUFFLED ? 0xffffff : 0x123456,
                    "Bob!", 0, 0, 0, "moon", null, 1, mode);
        }
        var lines = transcript.history();
        assertEquals("Alice Smith [mention]: Bob!", LocalSpeechClient.chatMessage(lines.get(0), "Bob Jones").getString());
        assertEquals("Alice Smith [mention] whispers: Bob!",
                LocalSpeechClient.chatMessage(lines.get(1), "Bob Jones").getString());
        assertEquals("Alice Smith [mention] yells: Bob!",
                LocalSpeechClient.chatMessage(lines.get(2), "Bob Jones").getString());
        assertEquals("Alice Smith whispers: Bob!", LocalSpeechClient.chatMessage(lines.get(3), "Bob Jones").getString());
        assertEquals(0xffffff, LocalSpeechClient.chatMessage(lines.get(3), null)
                .getStyle().getColor().getValue());
        assertEquals(0x123456, LocalSpeechClient.chatMessage(lines.get(1), null)
                .getStyle().getColor().getValue());
        var shout = LocalSpeechClient.chatMessage(lines.get(2), "Bob Jones");
        var shoutLeaves = shout.getSiblings();
        assertFalse(shout.getStyle().isBold());
        assertEquals(0x123456, shout.getStyle().getColor().getValue());
        assertFalse(shoutLeaves.get(0).getStyle().isBold());
        assertFalse(shoutLeaves.get(1).getStyle().isBold());
        assertTrue(shoutLeaves.get(2).getStyle().isBold());
        var bodyLeaves = new java.util.ArrayList<Component>();
        collectLeaves(shoutLeaves.get(2), bodyLeaves);
        assertTrue(bodyLeaves.stream().allMatch(leaf -> leaf.getStyle().isBold()));
        assertTrue(bodyLeaves.stream().anyMatch(leaf -> leaf.getStyle().getColor() != null
                && leaf.getStyle().getColor().getValue() == 0xffdc5f));
    }

    private static void collectLeaves(Component component, List<Component> result) {
        if (component.getSiblings().isEmpty()) result.add(component);
        else component.getSiblings().forEach(child -> collectLeaves(child, result));
    }
}
