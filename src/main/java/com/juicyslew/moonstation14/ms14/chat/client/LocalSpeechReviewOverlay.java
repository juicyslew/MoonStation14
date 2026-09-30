package com.juicyslew.moonstation14.ms14.chat.client;

import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Non-interactive HUD: gameplay owns the mouse and keyboard; history stays session-local. */
public final class LocalSpeechReviewOverlay {
    private static final float FONT_SCALE = 0.8f;
    private static final int ROW_HEIGHT = 9;
    private static final int MAX_WIDTH = 210;
    private static final int MAX_HEIGHT = 95;
    private static final int RIGHT_INSET = 6;
    // Chat input starts around screenHeight - 14; leave a gap above it in both phases.
    private static final int BOTTOM_INSET = 22;
    private static final int TOP_INSET = 6;
    private static final LocalChatFeed FEED = new LocalChatFeed();
    private static boolean visible = true;
    private static int fromNewest;
    private static int unseen;
    private static List<Integer> rowCounts = List.of();
    private static int pageCapacity = 9;

    private LocalSpeechReviewOverlay() { }

    public static void clear() {
        FEED.clear();
        visible = true;
        fromNewest = 0;
        unseen = 0;
        rowCounts = List.of();
        pageCapacity = 9;
    }

    public static void toggle() {
        visible = !visible;
        if (visible) {
            fromNewest = 0;
            unseen = 0;
        }
    }

    static boolean isVisible() { return visible; }

    public static int historySize() {
        return FEED.entries().size();
    }

    /** U hides the whole unified viewer, not just speech; suppression must not depend on visibility. */
    public static boolean replacesVanillaChat() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.level != null && minecraft.player != null;
    }

    public static void onSpeech(LocalSpeechTranscript.Line line) {
        FEED.appendSpeech(line);
        onLineReceived();
    }

    public static void onSystemNotice(Component notice) {
        FEED.appendNotice(notice);
        onLineReceived();
    }

    public static void scroll(int direction, int historySize) {
        if (!visible) return;
        // If the history changed before the next render, move one entry rather than
        // trusting stale wrapping data and potentially skipping unseen messages.
        fromNewest = rowCounts.size() == historySize
                ? pageOffset(rowCounts, fromNewest, direction, pageCapacity)
                : pageOffset(fromNewest, direction, historySize, 1);
        if (fromNewest == 0) unseen = 0;
    }

    static int pageOffset(int offset, int direction, int size, int visibleEntries) {
        int step = Math.max(1, visibleEntries);
        return (int) Math.max(0, Math.min(Math.max(0, size - 1), (long) offset + (long) direction * step));
    }

    static int pageOffset(List<Integer> rowCounts, int offset, int direction, int capacity) {
        int size = rowCounts.size();
        if (size == 0 || direction == 0) return Math.min(Math.max(0, offset), Math.max(0, size - 1));
        int current = Math.min(Math.max(0, offset), size - 1);
        Window page = window(rowCounts, current, capacity);
        if (direction > 0) return pageOffset(current, 1, size, page.end() - page.start());

        // Build the next page towards newest from its first unread entry. Using
        // the *current* page's count here can jump over tall intervening entries.
        int end = page.end();
        int rows = 0;
        while (end < size) {
            int next = Math.max(1, rowCounts.get(end));
            if (end > page.end() && rows + next > capacity) break;
            rows += next;
            end++;
            if (rows >= capacity) break;
        }
        return Math.max(0, size - end);
    }

    public static void onLineReceived() {
        // A capped history can keep the same size while its row heights change.
        rowCounts = List.of();
        // Keep the viewed messages in place as new ones arrive (including at the 100-line cap).
        if (visible && fromNewest > 0) {
            fromNewest++;
            unseen++;
        }
    }

    /** Find the newest full entries that fit; an exceptionally long entry occupies one page. */
    static Window window(List<Integer> rowCounts, int offset, int capacity) {
        int end = Math.max(0, rowCounts.size() - Math.max(0, offset));
        int start = end;
        int rows = 0;
        while (start > 0) {
            int next = Math.max(1, rowCounts.get(start - 1));
            if (rows + next > capacity && start < end) break;
            if (rows + next > capacity) {
                rows = capacity;
                start--;
                break;
            }
            rows += next;
            start--;
        }
        return new Window(start, end, rows);
    }

    record Window(int start, int end, int rows) { }

    /** GUI pixel geometry is independent of the render phase; only the drawing phase changes. */
    static Layout layout(int screenWidth, int screenHeight, boolean chatOpen) {
        int width = Math.min(MAX_WIDTH, Math.max(0, screenWidth - 2 * RIGHT_INSET));
        int bottom = Math.max(TOP_INSET, screenHeight - BOTTOM_INSET);
        int height = Math.min(MAX_HEIGHT, bottom - TOP_INSET);
        int left = screenWidth - width - RIGHT_INSET;
        int top = bottom - height;
        return new Layout(left, top, left + width, top + height, FONT_SCALE);
    }

    record Layout(int left, int top, int right, int bottom, float fontScale) {
        int width() { return right - left; }
        int height() { return bottom - top; }
        int textWidth() { return Math.max(1, Math.round((width() - 14) / fontScale)); }
        int capacity(boolean newer) {
            return Math.max(1, (height() - 10 - (newer ? ROW_HEIGHT : 0)) / ROW_HEIGHT);
        }
    }

    public static void render(RenderGuiEvent.Post event) {
        if (event == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !renderInPhase(minecraft.screen, false)) return;
        render(event.getGuiGraphics(), minecraft);
    }

    public static void render(ScreenEvent.Render.Post event) {
        if (event == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.screen != event.getScreen()
                || !renderInPhase(event.getScreen(), true)) return;
        render(event.getGuiGraphics(), minecraft);
    }

    /** Draw in exactly one phase: HUD without a screen, or above vanilla chat's background and messages. */
    static boolean renderInPhase(Screen screen, boolean screenPhase) {
        return screenPhase ? screen instanceof ChatScreen : screen == null;
    }

    private static void render(GuiGraphics graphics, Minecraft minecraft) {
        if (!visible || minecraft == null || minecraft.options == null || minecraft.options.hideGui
                || minecraft.level == null || minecraft.player == null) return;
        Font font = minecraft.font;
        if (graphics == null || font == null) return;
        Layout layout = layout(graphics.guiWidth(), graphics.guiHeight(), minecraft.screen instanceof ChatScreen);
        if (layout.width() < 24 || layout.height() < 20) return;
        int left = layout.left();
        int right = layout.right();
        int top = layout.top();
        int bottom = layout.bottom();
        int textX = left + 7;
        int textWidth = layout.textWidth();
        List<LocalChatFeed.Entry> history = FEED.entries();
        fromNewest = Math.max(0, Math.min(fromNewest, history.size() - 1));
        if (fromNewest == 0) unseen = 0;
        boolean showNewer = fromNewest > 0 && unseen > 0;
        int contentBottom = bottom - 5 - (showNewer ? ROW_HEIGHT : 0);
        int capacity = layout.capacity(showNewer);
        List<List<FormattedCharSequence>> wrapped = new ArrayList<>(history.size());
        List<Integer> counts = new ArrayList<>(history.size());
        for (LocalChatFeed.Entry entry : history) {
            List<FormattedCharSequence> lines = font.split(displayText(entry, font, textWidth), textWidth);
            wrapped.add(lines);
            counts.add(lines.size());
        }
        Window window = window(counts, fromNewest, capacity);
        rowCounts = List.copyOf(counts);
        pageCapacity = capacity;

        graphics.fill(left, top, right, bottom, 0xd9101824);
        graphics.enableScissor(left + 4, top + 3, right - 4, bottom - 3);
        try {
            if (history.isEmpty()) {
                drawScaled(graphics, font, Component.translatable("gui.moonstation14.speech_review.empty"),
                        textX, contentBottom - ROW_HEIGHT, 0xffaaaaaa);
            }
            int rowY = contentBottom - window.rows() * ROW_HEIGHT;
            for (int i = window.start(); i < window.end(); i++) {
                List<FormattedCharSequence> lines = wrapped.get(i);
                int drawn = Math.min(lines.size(), capacity);
                for (int j = 0; j < drawn; j++) {
                    boolean truncated = j == drawn - 1 && lines.size() > drawn;
                    if (truncated) graphics.enableScissor(left + 4, rowY, right - 14, rowY + ROW_HEIGHT);
                    try {
                        drawScaled(graphics, font, lines.get(j), textX, rowY, 0xffeeeeee);
                    } finally {
                        if (truncated) graphics.disableScissor();
                    }
                    rowY += ROW_HEIGHT;
                }
                if (lines.size() > drawn) drawScaled(graphics, font, "…", right - 13,
                        rowY - ROW_HEIGHT, 0xffffd080);
            }
            if (showNewer) drawScaled(graphics, font,
                    Component.translatable("gui.moonstation14.speech_review.newer", unseen),
                    textX, bottom - 5 - ROW_HEIGHT, 0xffffd080);
        } finally {
            graphics.disableScissor();
        }
    }

    private static void drawScaled(GuiGraphics graphics, Font font, Component text, int x, int y, int color) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(FONT_SCALE, FONT_SCALE, 1);
            graphics.drawString(font, text, 0, 0, color, false);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void drawScaled(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(FONT_SCALE, FONT_SCALE, 1);
            graphics.drawString(font, text, 0, 0, color, false);
        } finally {
            graphics.pose().popPose();
        }
    }

    private static void drawScaled(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        drawScaled(graphics, font, Component.literal(text), x, y, color);
    }

    /** Keep notice content (including selectors, translations and styles) intact. */
    static Component displayText(LocalChatFeed.Entry entry, Font font, int width) {
        if (entry instanceof LocalChatFeed.Speech speech)
            return labelledSpeech(speech.line(), font::width, LocalSpeechClient.localName(), width);
        LocalChatFeed.Notice notice = (LocalChatFeed.Notice) entry;
        return Component.empty().append(Component.literal("› ").withColor(0xaaaaaa))
                .append(notice.component());
    }

    static Component labelledSpeech(LocalSpeechTranscript.Line line, ToIntFunction<String> textWidth,
                                    String localName, int width) {
        boolean whisper = line.mode() == LocalSpeechPayload.Mode.WHISPER
                || line.mode() == LocalSpeechPayload.Mode.W_MUFFLED;
        boolean shout = line.mode() == LocalSpeechPayload.Mode.SHOUT;
        int speechColor = whisper ? 0xaaaaaa : 0xeeeeee;
        String suffix = line.speechLabel().substring(line.name().length());
        String name = line.name();
        // Leave room for the verb and at least a little speech on the first row.
        int nameLimit = Math.max(0, width - textWidth.applyAsInt(suffix) - 24);
        if (textWidth.applyAsInt(name) > nameLimit) {
            while (!name.isEmpty() && textWidth.applyAsInt(name + "…") > nameLimit)
                name = name.substring(0, name.length() - 1);
            name += "…";
        }
        Component header = Component.literal(name).withColor(line.rgb());
        Component verb = Component.literal(suffix + " ").withColor(speechColor);
        Component body = LocalSpeechClient.styledSpeech(line.text(), localName, speechColor,
                line.mode() == LocalSpeechPayload.Mode.W_MUFFLED ? -1 : DirectionalCueTintMesh.MENTION_RGB,
                shout);
        return Component.empty().append(header).append(verb).append(body);
    }
}
