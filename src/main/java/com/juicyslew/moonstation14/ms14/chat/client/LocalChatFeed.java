package com.juicyslew.moonstation14.ms14.chat.client;

import com.juicyslew.moonstation14.ms14.chat.network.LocalSpeechPayload;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

/** Session-only, arrival-ordered presentation feed; speech identity remains owned by the transcript. */
public final class LocalChatFeed {
    public static final int MAX_ENTRIES = 100;

    public sealed interface Entry permits Speech, Notice { }
    public record Speech(LocalSpeechTranscript.Line line) implements Entry {
        public Speech { Objects.requireNonNull(line, "line"); }
    }
    public record Notice(Component component) implements Entry {
        public Notice { Objects.requireNonNull(component, "component"); }
    }

    private final ArrayDeque<Entry> entries = new ArrayDeque<>();

    public void appendSpeech(LocalSpeechTranscript.Line line) {
        Objects.requireNonNull(line, "line");
        if (line.mode() == LocalSpeechPayload.Mode.W_MUFFLED) {
            line = new LocalSpeechTranscript.Line(0, line.name(),
                    LocalSpeechPayload.ANONYMOUS_RGB, line.text(), 0, 0, 0, line.receivedAt(), line.mode(),
                    line.azimuthSector(), line.verticalBand());
        }
        append(new Speech(line));
    }

    public void appendNotice(Component component) {
        append(new Notice(component));
    }

    public List<Entry> entries() { return List.copyOf(entries); }

    /** Call on session change to prevent prior speech and feedback from leaking into the new session. */
    public void clear() { entries.clear(); }

    private void append(Entry entry) {
        entries.addLast(entry);
        if (entries.size() > MAX_ENTRIES) entries.removeFirst();
    }
}
