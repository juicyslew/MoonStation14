package com.juicyslew.moonstation14.ms14.examine;

import java.util.Objects;
import net.minecraft.network.chat.Component;

/** A bounded, ordered contributor result. Detail rows are omitted unless authorized server-side. */
public record ExamineEntry(Visibility visibility, Component text, String group, int priority) {
    public enum Visibility { PUBLIC, DETAIL }
    public ExamineEntry {
        Objects.requireNonNull(visibility);
        Objects.requireNonNull(text);
        group = Objects.requireNonNullElse(group, "");
    }
    public static ExamineEntry publicText(Component text, String group, int priority) {
        return new ExamineEntry(Visibility.PUBLIC, text, group, priority);
    }
    public static ExamineEntry detail(Component text, String group, int priority) {
        return new ExamineEntry(Visibility.DETAIL, text, group, priority);
    }
}

