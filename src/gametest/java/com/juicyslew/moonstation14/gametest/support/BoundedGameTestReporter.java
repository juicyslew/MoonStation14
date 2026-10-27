package com.juicyslew.moonstation14.gametest.support;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.LogTestReporter;
import net.minecraft.gametest.framework.TestReporter;
import org.apache.logging.log4j.Logger;

/** Preserves native test reporting while adding a small, tail-friendly failure summary. */
final class BoundedGameTestReporter implements TestReporter {
    private static final int MAX_FAILURES = 64;
    private static final int MAX_ENTRY_BYTES = 384;
    private static final int MAX_TOTAL_BYTES = 12 * 1024;
    private final TestReporter delegate;
    private final Consumer<String> sink;
    private final List<String> failures = new ArrayList<>();
    private int bytes;
    private int omitted;

    BoundedGameTestReporter(TestReporter delegate, Logger logger) {
        this(delegate, line -> logger.info("{}", line));
    }

    BoundedGameTestReporter(TestReporter delegate, Consumer<String> sink) {
        this.delegate = delegate;
        this.sink = sink;
    }

    @Override public void onTestSuccess(GameTestInfo info) { delegate.onTestSuccess(info); }

    @Override public void onTestFailed(GameTestInfo info) {
        delegate.onTestFailed(info);
        if (failures.size() >= MAX_FAILURES) { omitted++; return; }
        Throwable error = info.getError();
        String entry = clean(info.getTestName()) + ": " + describe(error);
        entry = truncateUtf8(entry, MAX_ENTRY_BYTES);
        int size = entry.getBytes(StandardCharsets.UTF_8).length;
        if (bytes + size > MAX_TOTAL_BYTES) { omitted++; return; }
        failures.add(entry);
        bytes += size;
    }

    @Override public void finish() {
        delegate.finish();
        if (failures.isEmpty()) return;
        sink.accept("MS14_NATIVE_FAILURE_SUMMARY_BEGIN (" + (failures.size() + omitted) + " failures)");
        for (String failure : failures) sink.accept("MS14_NATIVE_FAILURE_SUMMARY " + failure);
        if (omitted > 0) sink.accept("MS14_NATIVE_FAILURE_SUMMARY omitted " + omitted + " failure details (bounded output)");
        sink.accept("MS14_NATIVE_FAILURE_SUMMARY_END");
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) return "<no message>";
        return value.replaceAll("[\\r\\n\\t\\p{Cntrl}]", " ").trim();
    }

    private static String describe(Throwable error) {
        if (error == null) return "<no error detail>";
        Throwable outer = error;
        Throwable leaf = error;
        for (int depth = 1; depth < 4 && leaf.getCause() != null && leaf.getCause() != leaf; depth++) {
            leaf = leaf.getCause();
        }
        String leafMessage = clean(leaf.getMessage() == null ? leaf.getClass().getSimpleName() : leaf.getMessage());
        String outerMessage = clean(outer.getMessage());
        StringBuilder result = new StringBuilder(leafMessage);
        if (outer != leaf && !outerMessage.equals("<no message>") && !outerMessage.equals(leafMessage)) {
            result.append(" [context: ").append(outerMessage).append(']');
        }
        for (StackTraceElement frame : error.getStackTrace()) {
            if (frame.getClassName().startsWith("com.juicyslew.moonstation14.")) {
                result.append(" [at ").append(frame.getClassName()).append('#').append(frame.getMethodName())
                        .append('(').append(frame.getFileName()).append(':').append(frame.getLineNumber()).append(")]");
                break;
            }
        }
        return result.toString();
    }

    private static String truncateUtf8(String value, int maxBytes) {
        if (value.getBytes(StandardCharsets.UTF_8).length <= maxBytes) return value;
        String suffix = "…";
        int end = 0;
        int budget = maxBytes - suffix.getBytes(StandardCharsets.UTF_8).length;
        int used = 0;
        while (end < value.length()) {
            int cp = value.codePointAt(end);
            int width = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (used + width > budget) break;
            used += width;
            end += Character.charCount(cp);
        }
        return value.substring(0, end) + suffix;
    }
}
