package com.juicyslew.moonstation14.gametest.support;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Parsing and OR matching rules shared by the registration hook and focused unit tests. */
public final class GameTestTagSelector {
    private static final Pattern VALID = Pattern.compile("[a-z][a-z0-9_-]*");
    private GameTestTagSelector() {}

    public static Set<String> tags(Class<?> type, Method method) {
        TreeSet<String> tags = new TreeSet<>();
        add(tags, type.getAnnotation(GameTestTags.class));
        add(tags, method.getAnnotation(GameTestTags.class));
        return Set.copyOf(tags);
    }

    private static void add(Set<String> into, GameTestTags annotation) {
        if (annotation == null) return;
        for (String raw : annotation.value()) {
            String tag = canonical(raw);
            if (!VALID.matcher(tag).matches()) throw new IllegalArgumentException("Malformed GameTest tag: '" + raw + "'");
            into.add(tag);
        }
    }

    public static Set<String> parse(String value) {
        if (value == null) return Set.of();
        if (value.isBlank()) throw new IllegalArgumentException("GameTest tag filter is present but blank");
        TreeSet<String> result = new TreeSet<>();
        for (String raw : value.split(",", -1)) {
            if (raw.isBlank() || !raw.equals(raw.trim())) throw new IllegalArgumentException("Malformed GameTest tag filter: '" + value + "'");
            String tag = canonical(raw);
            if (!VALID.matcher(tag).matches()) throw new IllegalArgumentException("Malformed GameTest tag: '" + raw + "'");
            result.add(tag);
        }
        return Set.copyOf(result);
    }

    public static Set<String> unknownTags(Collection<String> requested, Collection<String> available) {
        TreeSet<String> unknown = new TreeSet<>(requested);
        unknown.removeAll(available);
        return Set.copyOf(unknown);
    }

    private static String canonical(String raw) {
        return raw.toLowerCase(Locale.ROOT);
    }

    public static Set<Method> select(Collection<Method> discovered, Set<String> requested) {
        LinkedHashSet<Method> selected = new LinkedHashSet<>();
        for (Method method : discovered) {
            Set<String> methodTags = tags(method.getDeclaringClass(), method);
            if (requested.isEmpty() || methodTags.stream().anyMatch(requested::contains)) selected.add(method);
        }
        return Set.copyOf(selected);
    }
}
