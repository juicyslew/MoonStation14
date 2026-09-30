package com.juicyslew.moonstation14.ms14.chat.client;

import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LocalChatFeedTest {
    private static LocalSpeechTranscript.Line line(int id) {
        return new LocalSpeechTranscript.Line(id, "Alice Smith", 0x123456, "hello " + id,
                1, 2, 3, id, LocalSpeechPayload.Mode.SAY);
    }

    @Test
    void speechAndNoticesKeepTheirArrivalOrderAndTypes() {
        var feed = new LocalChatFeed();
        var first = line(1);
        var second = line(2);
        var notice = Component.literal("Command response").withStyle(ChatFormatting.GREEN);
        feed.appendSpeech(first);
        feed.appendNotice(notice);
        feed.appendSpeech(second);

        assertEquals(List.of(new LocalChatFeed.Speech(first), new LocalChatFeed.Notice(notice),
                new LocalChatFeed.Speech(second)), feed.entries());
        assertSame(first, ((LocalChatFeed.Speech) feed.entries().getFirst()).line());
        assertSame(notice, ((LocalChatFeed.Notice) feed.entries().get(1)).component());
        assertEquals(0x55FF55, ((LocalChatFeed.Notice) feed.entries().get(1))
                .component().getStyle().getColor().getValue());
        assertEquals(notice.getStyle(), ((LocalChatFeed.Notice) feed.entries().get(1)).component().getStyle());
    }

    @Test
    void combinedCapacityDropsOldestRegardlessOfType() {
        var feed = new LocalChatFeed();
        feed.appendSpeech(line(1));
        feed.appendNotice(Component.literal("old notice"));
        for (int i = 2; i <= 99; i++) feed.appendSpeech(line(i));
        assertEquals(LocalChatFeed.MAX_ENTRIES, feed.entries().size());
        assertEquals(new LocalChatFeed.Speech(line(1)), feed.entries().getFirst());
        feed.appendNotice(Component.literal("new notice"));
        assertEquals(LocalChatFeed.MAX_ENTRIES, feed.entries().size());
        assertEquals("old notice", ((LocalChatFeed.Notice) feed.entries().getFirst()).component().getString());
        feed.appendSpeech(line(100));
        assertEquals(new LocalChatFeed.Speech(line(2)), feed.entries().getFirst());
        assertEquals(LocalChatFeed.MAX_ENTRIES, feed.entries().size());
        assertInstanceOf(LocalChatFeed.Speech.class, feed.entries().getLast());
    }

    @Test
    void clearSeparatesSessionsAndSnapshotsCannotChange() {
        var feed = new LocalChatFeed();
        feed.appendNotice(Component.literal("old session"));
        var old = feed.entries();
        feed.clear();
        assertTrue(feed.entries().isEmpty());
        feed.appendSpeech(line(4));
        assertEquals(1, old.size());
        assertEquals(List.of(new LocalChatFeed.Speech(line(4))), feed.entries());
        assertThrows(UnsupportedOperationException.class, () -> feed.entries().clear());
    }

    @Test
    void muffledSpeechKeepsNameAndBearingButNotPreciseMetadata() {
        var feed = new LocalChatFeed();
        feed.appendSpeech(new LocalSpeechTranscript.Line(91, "Account Name", 0x123456, "muffled",
                42, 64, 80, 123, LocalSpeechPayload.Mode.W_MUFFLED, 4, 2));
        var anonymous = ((LocalChatFeed.Speech) feed.entries().getFirst()).line();
        assertEquals(0, anonymous.speakerId());
        assertEquals("Account Name", anonymous.name());
        assertEquals(LocalSpeechPayload.ANONYMOUS_RGB, anonymous.rgb());
        assertEquals(0, anonymous.x());
        assertEquals(0, anonymous.y());
        assertEquals(0, anonymous.z());
        assertEquals(4, anonymous.azimuthSector());
        assertEquals(2, anonymous.verticalBand());
        assertEquals("muffled", anonymous.text());
        assertEquals(123, anonymous.receivedAt());
        assertNull(anonymous.speakerUuid());
    }

    @Test
    void muffledFeedDropsEvenExplicitUuidAndPreciseLocation() {
        var feed = new LocalChatFeed();
        feed.appendSpeech(new LocalSpeechTranscript.Line(91, "Alice Smith", 0x123456, "quiet",
                42, 64, 80, 123, LocalSpeechPayload.Mode.W_MUFFLED, 4, 2, UUID.randomUUID()));
        var line = ((LocalChatFeed.Speech) feed.entries().getFirst()).line();
        assertNull(line.speakerUuid());
        assertEquals(0, line.speakerId());
        assertEquals(0, line.x());
        assertEquals(0, line.y());
        assertEquals(0, line.z());
    }

    @Test
    void nullInputsAreRejectedWithoutChangingFeed() {
        var feed = new LocalChatFeed();
        assertThrows(NullPointerException.class, () -> feed.appendSpeech(null));
        assertThrows(NullPointerException.class, () -> feed.appendNotice(null));
        assertTrue(feed.entries().isEmpty());
    }
}
