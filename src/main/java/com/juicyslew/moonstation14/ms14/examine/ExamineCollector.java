package com.juicyslew.moonstation14.ms14.examine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Deterministic bounded contributor collection. */
public final class ExamineCollector {
    private final ArrayList<ExamineEntry> entries = new ArrayList<>();
    private Component name;
    private Component description = Component.empty();
    private boolean nameLocked;
    public ExamineCollector(boolean detailsAllowed, Component fallbackName) {
        this.detailsAllowed = detailsAllowed;
        this.name = ExamineNumbers.normalize(java.util.Objects.requireNonNull(fallbackName));
    }
    /** Apply prototype name only when present; native Minecraft identity remains the fallback. */
    public void metadata(com.juicyslew.moonstation14.ms14.examine.prototype.ExamineMetadata metadata) {
        if (metadata == null) return;
        if (metadata.hasName() && !nameLocked) name = Component.literal(metadata.name());
        if (metadata.hasDescription()) description = Component.literal(metadata.description());
    }
    public Component name() { return ExamineNumbers.normalize(name); }
    public Component description() { return ExamineNumbers.normalize(description); }
    public void description(Component value) { if (value != null && !value.getString().isBlank()) description = ExamineNumbers.normalize(value); }
    public void preserveName() { nameLocked = true; }
    public void publicEntry(Component text, String group, int priority) {
        add(ExamineEntry.publicText(text, group, priority));
    }
    public void detailEntry(Component text, String group, int priority) {
        add(ExamineEntry.detail(text, group, priority));
    }
    private void add(ExamineEntry entry) {
        if (entry.visibility() == ExamineEntry.Visibility.DETAIL && !detailsAllowed) return;
        if (entry.group().length() > 128 || entry.text().getString().length() > 2048) return;
        if (entries.size() >= ExamineResponse.MAX_ENTRIES) return;
        entries.add(new ExamineEntry(entry.visibility(), ExamineNumbers.normalize(entry.text()), entry.group(), entry.priority()));
    }
    private final boolean detailsAllowed;
    public ExamineCollector(boolean detailsAllowed) { this(detailsAllowed, Component.empty()); }
    public List<ExamineEntry> sortedEntries() {
        return entries.stream().sorted(Comparator.comparingInt(ExamineEntry::priority).reversed()
                .thenComparing(ExamineEntry::group).thenComparing(entry -> entry.text().getString())).toList();
    }
}

