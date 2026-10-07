package com.juicyslew.moonstation14.ms14.chat.client;

import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

class LocalSpeechReviewOverlayTest {
    @Test
    void labelledSpeechHighlightsOnlyWholeLocalNameRangesWithModeStyles() {
        var say = speech("hello Ann and Ann, goodbye", LocalSpeechPayload.Mode.SAY);
        MutableComponent rendered = (MutableComponent) LocalSpeechReviewOverlay.labelledSpeech(say,
                String::length, "Ann", 245);
        List<Component> leaves = leaves(rendered);
        assertEquals("Ann", leaves.get(0).getString());
        assertEquals(0x123456, leaves.get(0).getStyle().getColor().getValue());
        assertEquals("hello ", leaves.get(2).getString());
        assertEquals(0xeeeeee, leaves.get(2).getStyle().getColor().getValue());
        assertEquals("Ann", leaves.get(3).getString());
        assertEquals(0xffdc5f, leaves.get(3).getStyle().getColor().getValue());
        assertEquals(" and ", leaves.get(4).getString());
        assertEquals(0xeeeeee, leaves.get(4).getStyle().getColor().getValue());
        assertEquals("Ann", leaves.get(5).getString());
        assertEquals(0xffdc5f, leaves.get(5).getStyle().getColor().getValue());
        assertEquals(", goodbye", leaves.get(6).getString());
        assertEquals(0xeeeeee, leaves.get(6).getStyle().getColor().getValue());

        leaves = leaves(LocalSpeechReviewOverlay.labelledSpeech(speech("hello Ann", LocalSpeechPayload.Mode.WHISPER),
                String::length, "Ann", 245));
        assertEquals(0xaaaaaa, leaves.get(2).getStyle().getColor().getValue());
        assertEquals(0xffdc5f, leaves.get(3).getStyle().getColor().getValue());

        leaves = leaves(LocalSpeechReviewOverlay.labelledSpeech(speech("hello Ann", LocalSpeechPayload.Mode.SHOUT),
                String::length, "Ann", 245));
        assertFalse(leaves.get(0).getStyle().isBold());
        assertFalse(leaves.get(1).getStyle().isBold());
        for (int i = 2; i < leaves.size(); i++) assertTrue(leaves.get(i).getStyle().isBold());
        assertEquals(0x123456, leaves.get(0).getStyle().getColor().getValue());
        assertEquals(0xffdc5f, leaves.get(3).getStyle().getColor().getValue());

        leaves = leaves(LocalSpeechReviewOverlay.labelledSpeech(speech("hello Ann", LocalSpeechPayload.Mode.W_MUFFLED),
                String::length, "Ann", 245));
        assertFalse(leaves.stream().anyMatch(leaf -> leaf.getStyle().getColor() != null
                && leaf.getStyle().getColor().getValue() == 0xffdc5f));
        leaves = leaves(LocalSpeechReviewOverlay.labelledSpeech(say, String::length, "Joanna", 245));
        assertTrue(leaves.stream().noneMatch(leaf -> leaf.getStyle().getColor() != null
                && leaf.getStyle().getColor().getValue() == 0xffdc5f));
        assertTrue(leaves(LocalSpeechReviewOverlay.labelledSpeech(say, String::length, "", 245)).stream()
                .noneMatch(leaf -> leaf.getStyle().getColor() != null
                        && leaf.getStyle().getColor().getValue() == 0xffdc5f));
    }

    private static LocalSpeechTranscript.Line speech(String text, LocalSpeechPayload.Mode mode) {
        return new LocalSpeechTranscript.Line(1, "Ann", 0x123456, text, 0, 0, 0, 0, mode);
    }

    private static List<Component> leaves(Component component) {
        List<Component> result = new ArrayList<>();
        for (Component child : component.getSiblings()) {
            if (child.getSiblings().isEmpty()) result.add(child);
            else result.addAll(leaves(child));
        }
        return result;
    }

    @Test
    void rightEdgeLayoutStaysTopAnchoredInGameplayAndChatAtSupportedSizes() {
        for (int height : List.of(140, 180, 240, 450, 480)) {
            for (int width : List.of(180, 320, 390, 480, 640, 800, 854)) {
                LocalSpeechReviewOverlay.Layout playing = LocalSpeechReviewOverlay.layout(width, height, false);
                LocalSpeechReviewOverlay.Layout typing = LocalSpeechReviewOverlay.layout(width, height, true);
                assertEquals(playing, typing);
                assertEquals(width - 6, playing.right());
                assertTrue(playing.bottom() <= height - TopRightHudLayout.BOTTOM_RESERVE);
                assertEquals(6, playing.top());
                assertEquals(Math.min(155, width - 12), playing.width());
                assertEquals(Math.min(78, Math.max(0, height - 6 - 34 - 5 - 30)), playing.height());
                assertEquals(0.5f, playing.fontScale());
                assertEquals(Math.max(1, Math.round((playing.width() - 14) / 0.5f)), playing.textWidth());
                assertEquals(Math.max(1, (playing.height() - 10) / 6), playing.capacity(false));
                assertEquals(Math.max(1, (playing.height() - 16) / 6), playing.capacity(true));
            }
        }
    }

    @Test
    void resizingAcrossFormerSideColumnThresholdKeepsTopOffsetAndTextCapacity() {
        for (boolean chatOpen : List.of(false, true)) {
            LocalSpeechReviewOverlay.Layout wide = LocalSpeechReviewOverlay.layout(480, 180, chatOpen);
            LocalSpeechReviewOverlay.Layout narrow = LocalSpeechReviewOverlay.layout(390, 180, chatOpen);
            assertEquals(new LocalSpeechReviewOverlay.Layout(319, 6, 474, 84, 0.5f), wide);
            assertEquals(new LocalSpeechReviewOverlay.Layout(229, 6, 384, 84, 0.5f), narrow);
            assertEquals(wide.bottom(), narrow.bottom());
            assertEquals(wide.textWidth(), narrow.textWidth());
            assertEquals(wide.capacity(false), narrow.capacity(false));
            assertEquals(wide.capacity(true), narrow.capacity(true));
        }
    }

    @Test
    void shortGuiReservesSpaceForStatusesAndInput() {
        LocalSpeechReviewOverlay.Layout shortScreen = LocalSpeechReviewOverlay.layout(180, 140, true);
        assertEquals(new LocalSpeechReviewOverlay.Layout(19, 6, 174, 71, 0.5f), shortScreen);
        assertEquals(9, shortScreen.capacity(false));
        assertEquals(8, shortScreen.capacity(true));
        assertEquals(new LocalSpeechReviewOverlay.Layout(159, 6, 314, 6, 0.5f),
                LocalSpeechReviewOverlay.layout(320, 20, false));
    }

    @Test
    void scaledRowsDetermineWrappingAndPageCapacity() {
        LocalSpeechReviewOverlay.Layout layout = LocalSpeechReviewOverlay.layout(640, 240, false);
        assertEquals(282, layout.textWidth()); // 141 GUI pixels at 0.5 scale.
        assertEquals(11, layout.capacity(false));
        assertEquals(10, layout.capacity(true));
        List<Integer> rows = Collections.nCopies(30, 1);
        assertEquals(new LocalSpeechReviewOverlay.Window(19, 30, 11),
                LocalSpeechReviewOverlay.window(rows, 0, layout.capacity(false)));
        assertEquals(11, LocalSpeechReviewOverlay.pageOffset(rows, 0, 1, layout.capacity(false)));
        assertEquals(10, LocalSpeechReviewOverlay.pageOffset(rows, 0, 1, layout.capacity(true)));
        LocalSpeechReviewOverlay.Layout shortScreen = LocalSpeechReviewOverlay.layout(180, 140, true);
        assertEquals(9, shortScreen.capacity(false));
        assertEquals(8, shortScreen.capacity(true));
    }

    @Test
    void compactViewerKeepsPagingAndChatInputClearAtGuiZooms() {
        for (int[] size : new int[][] {{180, 140}, {320, 240}, {800, 450}}) {
            var playing = LocalSpeechReviewOverlay.layout(size[0], size[1], false);
            var typing = LocalSpeechReviewOverlay.layout(size[0], size[1], true);
            assertEquals(playing, typing);
            assertEquals(155, typing.width());
            assertEquals(Math.min(78, size[1] - 75), typing.height());
            assertTrue(typing.bottom() < size[1] - 14); // Vanilla chat input remains unobscured.
            assertEquals(typing.height() < 78 ? 9 : 11, typing.capacity(false));
            assertEquals(typing.height() < 78 ? 8 : 10, typing.capacity(true));
            assertEquals(new LocalSpeechReviewOverlay.Window(20 - typing.capacity(false), 20,
                            typing.capacity(false)),
                    LocalSpeechReviewOverlay.window(Collections.nCopies(20, 1), 0, typing.capacity(false)));
        }
    }

    @Test
    void chatDrawsOnlyAfterScreenAndOtherMenusNeverDraw() {
        Screen chat = new ChatScreen("");
        Screen menu = new Screen(Component.literal("menu")) { };
        assertTrue(LocalSpeechReviewOverlay.renderInPhase(null, false));
        assertFalse(LocalSpeechReviewOverlay.renderInPhase(null, true));
        assertFalse(LocalSpeechReviewOverlay.renderInPhase(chat, false));
        assertTrue(LocalSpeechReviewOverlay.renderInPhase(chat, true));
        assertFalse(LocalSpeechReviewOverlay.renderInPhase(menu, false));
        assertFalse(LocalSpeechReviewOverlay.renderInPhase(menu, true));
        Screen storage = new com.juicyslew.moonstation14.ms14.hands.client.StoragePopupScreen(null);
        assertFalse(LocalSpeechReviewOverlay.renderInPhase(storage, false));
        assertTrue(LocalSpeechReviewOverlay.renderInPhase(storage, true));
    }

    @Test
    void changingRenderPhasesDoesNotToggleViewer() throws Exception {
        LocalSpeechReviewOverlay.clear();
        try {
            assertTrue(visible());
            LocalSpeechReviewOverlay.renderInPhase(new ChatScreen(""), true);
            LocalSpeechReviewOverlay.renderInPhase(null, false);
            assertTrue(visible());
        } finally {
            LocalSpeechReviewOverlay.clear();
        }
    }

    @Test
    void sessionClearShowsEmptyViewerAndToggleOnlyChangesVisibility() throws Exception {
        LocalSpeechReviewOverlay.clear();
        assertTrue(visible());
        LocalSpeechReviewOverlay.onSystemNotice(Component.literal("welcome"));
        assertEquals(1, LocalSpeechReviewOverlay.historySize());
        LocalSpeechReviewOverlay.toggle();
        assertFalse(visible());
        assertEquals(1, LocalSpeechReviewOverlay.historySize());
        LocalSpeechReviewOverlay.toggle();
        assertTrue(visible());
        LocalSpeechReviewOverlay.clear();
        assertTrue(visible());
        assertEquals(0, LocalSpeechReviewOverlay.historySize());
    }

    @Test
    void interleavedNoticesRetainComponentsAndCountTowardsPaging() throws Exception {
        LocalSpeechReviewOverlay.clear();
        try {
            var speech = new LocalSpeechTranscript.Line(5, "Character", 0x123456, "hello",
                    4, 8, 12, 20, LocalSpeechPayload.Mode.SHOUT);
            var selector = Component.selector("@p", Optional.empty());
            var notice = Component.translatable("commands.example.success", selector)
                    .withStyle(ChatFormatting.GREEN);
            LocalSpeechReviewOverlay.onSpeech(speech);
            LocalSpeechReviewOverlay.onSystemNotice(notice);
            LocalSpeechReviewOverlay.onSpeech(speech);
            assertEquals(3, LocalSpeechReviewOverlay.historySize());
            assertEquals(List.of(new LocalChatFeed.Speech(speech), new LocalChatFeed.Notice(notice),
                    new LocalChatFeed.Speech(speech)), feed().entries());
            var renderedNotice = LocalSpeechReviewOverlay.displayText(new LocalChatFeed.Notice(notice), null, 245);
            assertSame(notice, renderedNotice.getSiblings().get(1));
            assertSame(selector, ((net.minecraft.network.chat.contents.TranslatableContents) notice.getContents()).getArgs()[0]);
            assertEquals("› ", renderedNotice.getSiblings().getFirst().getString());
            assertEquals(ChatFormatting.GREEN.getColor(), notice.getStyle().getColor().getValue());
            LocalSpeechReviewOverlay.scroll(1, LocalSpeechReviewOverlay.historySize());
            assertEquals(1, state("fromNewest"));
            LocalSpeechReviewOverlay.onSystemNotice(Component.literal("another notice"));
            assertEquals(4, LocalSpeechReviewOverlay.historySize());
            assertEquals(2, state("fromNewest"));
            assertEquals(1, state("unseen"));
            LocalSpeechReviewOverlay.scroll(-1, LocalSpeechReviewOverlay.historySize());
            assertEquals(1, state("fromNewest"));
            LocalSpeechReviewOverlay.scroll(-1, LocalSpeechReviewOverlay.historySize());
            assertEquals(0, state("fromNewest"));
            assertEquals(0, state("unseen"));
        } finally {
            LocalSpeechReviewOverlay.clear();
        }
    }

    @Test
    void shortHistoryStaysAtBottomAndDenseLinesFit() {
        assertEquals(new LocalSpeechReviewOverlay.Window(0, 2, 2),
                LocalSpeechReviewOverlay.window(List.of(1, 1), 0, 13));
        assertEquals(new LocalSpeechReviewOverlay.Window(7, 20, 13),
                LocalSpeechReviewOverlay.window(java.util.Collections.nCopies(20, 1), 0, 13));
    }

    @Test
    void variableHeightMessagesStayWholeWhenPossible() {
        assertEquals(new LocalSpeechReviewOverlay.Window(2, 5, 6),
                LocalSpeechReviewOverlay.window(List.of(2, 2, 3, 1, 2), 0, 6));
        assertEquals(new LocalSpeechReviewOverlay.Window(2, 3, 4),
                LocalSpeechReviewOverlay.window(List.of(2, 2, 9, 1), 1, 4));
    }

    @Test
    void pagingJumpsSeveralEntriesAndClampsToHistory() {
        assertEquals(8, LocalSpeechReviewOverlay.pageOffset(0, 1, 100, 8));
        assertEquals(10, LocalSpeechReviewOverlay.pageOffset(8, 1, 100, 2));
        assertEquals(0, LocalSpeechReviewOverlay.pageOffset(3, -1, 100, 8));
        assertEquals(99, LocalSpeechReviewOverlay.pageOffset(97, 1, 100, 8));
        assertEquals(0, LocalSpeechReviewOverlay.pageOffset(0, 1, 0, 8));
    }

    @Test
    void tallEntriesPageOneAtATimeInBothDirections() {
        List<Integer> heights = Collections.nCopies(20, 8);
        int offset = 0;
        for (int index = 19; index >= 0; index--) {
            LocalSpeechReviewOverlay.Window page = LocalSpeechReviewOverlay.window(heights, offset, 13);
            assertEquals(index, page.start());
            assertEquals(index + 1, page.end());
            offset = LocalSpeechReviewOverlay.pageOffset(heights, offset, 1, 13);
        }
        assertEquals(19, offset); // Oldest retained page is still reachable.
        for (int index = 0; index < 19; index++) {
            offset = LocalSpeechReviewOverlay.pageOffset(heights, offset, -1, 13);
            assertEquals(index + 1, LocalSpeechReviewOverlay.window(heights, offset, 13).start());
        }
        assertEquals(0, offset);
    }

    @Test
    void mixedHeightsNeverSkipAnEntryInEitherDirection() {
        List<Integer> heights = List.of(8, 1, 1, 8, 1, 1, 8, 1, 1, 8,
                1, 1, 8, 1, 1, 8, 1, 1, 8, 1);
        int offset = 0;
        boolean[] seenOlder = new boolean[heights.size()];
        boolean[] seenNewer = new boolean[heights.size()];
        for (int i = 0; i < heights.size(); i++) {
            LocalSpeechReviewOverlay.Window page = LocalSpeechReviewOverlay.window(heights, offset, 13);
            for (int j = page.start(); j < page.end(); j++) seenOlder[j] = true;
            int next = LocalSpeechReviewOverlay.pageOffset(heights, offset, 1, 13);
            if (next == offset) break;
            offset = next;
        }
        for (boolean seen : seenOlder) assertEquals(true, seen);
        for (int i = 0; i < heights.size(); i++) {
            LocalSpeechReviewOverlay.Window page = LocalSpeechReviewOverlay.window(heights, offset, 13);
            for (int j = page.start(); j < page.end(); j++) seenNewer[j] = true;
            int next = LocalSpeechReviewOverlay.pageOffset(heights, offset, -1, 13);
            if (next == offset) break;
            offset = next;
        }
        for (boolean seen : seenNewer) assertEquals(true, seen);
        assertEquals(0, offset);
    }

    @Test
    void shortMessagesKeepFastFullPageJumps() {
        List<Integer> heights = Collections.nCopies(30, 1);
        assertEquals(13, LocalSpeechReviewOverlay.pageOffset(heights, 0, 1, 13));
        assertEquals(26, LocalSpeechReviewOverlay.pageOffset(heights, 13, 1, 13));
        assertEquals(29, LocalSpeechReviewOverlay.pageOffset(heights, 26, 1, 13));
        assertEquals(16, LocalSpeechReviewOverlay.pageOffset(heights, 29, -1, 13));
        assertEquals(13, LocalSpeechReviewOverlay.pageOffset(heights, 26, -1, 13));
    }

    @Test
    void wrappedRowsOfOneLongMessageAreReachableAcrossPagesInBothDirections() {
        int capacity = 8;
        int offset = 0;
        boolean[] seen = new boolean[37];
        while (true) {
            var range = LocalSpeechReviewOverlay.visibleRows(37, offset, capacity);
            for (int row = range.start(); row < range.end(); row++) seen[row] = true;
            int older = LocalSpeechReviewOverlay.rowPageOffset(offset, 1, 37, capacity);
            if (older == offset) break;
            offset = older;
        }
        for (boolean row : seen) assertTrue(row);
        assertEquals(29, offset); // Clamp at the oldest full viewport, not one arbitrary row.
        while (offset > 0) offset = LocalSpeechReviewOverlay.rowPageOffset(offset, -1, 37, capacity);
        assertEquals(new LocalSpeechReviewOverlay.RowRange(29, 37),
                LocalSpeechReviewOverlay.visibleRows(37, offset, capacity));
        assertEquals(29, LocalSpeechReviewOverlay.rowPageOffset(24, 1, 37, capacity));
        assertEquals(new LocalSpeechReviewOverlay.RowRange(0, 8),
                LocalSpeechReviewOverlay.visibleRows(37, 29, capacity));
    }

    @Test
    void rowOffsetsAnchorToEntriesIncludingPartialTallEntry() {
        List<Integer> rows = List.of(3, 37, 2);
        assertEquals(1, LocalSpeechReviewOverlay.entriesBehindRows(rows, 1));
        assertEquals(2, LocalSpeechReviewOverlay.entriesBehindRows(rows, 3));
        assertEquals(3, LocalSpeechReviewOverlay.entriesBehindRows(rows, 42));
        assertEquals(3, LocalSpeechReviewOverlay.entriesBehindRows(rows, 100));
    }

    @Test
    void evictionClampsToOldestRetainedAndNewerCountIsInformational() throws Exception {
        LocalSpeechReviewOverlay.clear();
        try {
            for (int i = 0; i < 100; i++) LocalSpeechReviewOverlay.onSystemNotice(Component.literal("notice " + i));
            for (int i = 0; i < 99; i++) LocalSpeechReviewOverlay.scroll(1, LocalSpeechReviewOverlay.historySize());
            assertEquals(99, state("fromNewest"));

            LocalSpeechReviewOverlay.onSystemNotice(Component.literal("notice 100"));
            assertEquals(100, LocalSpeechReviewOverlay.historySize());
            assertEquals("notice 1", ((LocalChatFeed.Notice) feed().entries().getFirst()).component().getString());
            assertEquals(100, state("fromNewest"));
            assertEquals(1, state("unseen"));
            LocalSpeechReviewOverlay.scroll(1, LocalSpeechReviewOverlay.historySize());
            assertEquals(99, state("fromNewest"));
            assertEquals(new LocalSpeechReviewOverlay.Window(0, 1, 1),
                    LocalSpeechReviewOverlay.window(Collections.nCopies(100, 1), state("fromNewest"), 13));
            assertEquals(1, state("unseen"));
            for (int i = 0; i < 99; i++) LocalSpeechReviewOverlay.scroll(-1, LocalSpeechReviewOverlay.historySize());
            assertEquals(0, state("fromNewest"));
            assertEquals(0, state("unseen"));
        } finally {
            LocalSpeechReviewOverlay.clear();
        }
    }

    private static int state(String name) throws Exception {
        Field field = LocalSpeechReviewOverlay.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static LocalChatFeed feed() throws Exception {
        Field field = LocalSpeechReviewOverlay.class.getDeclaredField("FEED");
        field.setAccessible(true);
        return (LocalChatFeed) field.get(null);
    }

    private static boolean visible() throws Exception {
        Field field = LocalSpeechReviewOverlay.class.getDeclaredField("visible");
        field.setAccessible(true);
        return field.getBoolean(null);
    }
}
