package com.juicyslew.moonstation14.gametest.support;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.TestReporter;
import org.junit.jupiter.api.Test;

class BoundedGameTestReporterTest {
    @Test void forwardsCallbacksAndEmitsFailurePredicateAtFinish() {
        TestReporter delegate = mock(TestReporter.class);
        List<String> output = new ArrayList<>();
        BoundedGameTestReporter reporter = new BoundedGameTestReporter(delegate, output::add);
        GameTestInfo success = info("success", null);
        GameTestInfo failed = info("fixture.test", new IllegalStateException("expected 4 but was 2"));

        reporter.onTestSuccess(success);
        reporter.onTestFailed(failed);
        reporter.finish();

        verify(delegate).onTestSuccess(success);
        verify(delegate).onTestFailed(failed);
        verify(delegate).finish();
        assertTrue(output.get(1).contains("fixture.test: expected 4 but was 2"));
        assertEquals("MS14_NATIVE_FAILURE_SUMMARY_END", output.get(output.size() - 1));
    }

    @Test void successOnlyProducesNoSummaryAndNullErrorHasFallback() {
        List<String> output = new ArrayList<>();
        BoundedGameTestReporter reporter = new BoundedGameTestReporter(mock(TestReporter.class), output::add);
        reporter.onTestSuccess(info("fine", null));
        reporter.finish();
        assertTrue(output.isEmpty());

        reporter.onTestFailed(info("broken", null));
        reporter.finish();
        assertTrue(output.stream().anyMatch(line -> line.contains("broken: <no error detail>")));
    }

    @Test void sanitizesAndBoundsUnicodeEntryAndFailureCount() {
        List<String> output = new ArrayList<>();
        BoundedGameTestReporter reporter = new BoundedGameTestReporter(mock(TestReporter.class), output::add);
        reporter.onTestFailed(info("name\nwith\tcontrols", new IllegalStateException("😀".repeat(500))));
        for (int i = 0; i < 65; i++) reporter.onTestFailed(info("failure" + i, new RuntimeException("predicate")));
        reporter.finish();

        String first = output.stream().filter(line -> line.contains("name with controls:")).findFirst().orElseThrow();
        assertTrue(first.endsWith("…"));
        assertTrue(first.getBytes(StandardCharsets.UTF_8).length <= 384 + "MS14_NATIVE_FAILURE_SUMMARY ".length());
        assertEquals(first, new String(first.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        assertFalse(first.contains("\n"));
        assertTrue(output.stream().anyMatch(line -> line.contains("omitted 2 failure details")));
        for (String line : output) assertTrue(line.getBytes(StandardCharsets.UTF_8).length < 13 * 1024);
    }

    @Test void aggregatePayloadCapOmitsFurtherDetailsButStillForwardsAllFailures() {
        TestReporter delegate = mock(TestReporter.class);
        List<String> output = new ArrayList<>();
        BoundedGameTestReporter reporter = new BoundedGameTestReporter(delegate, output::add);
        for (int i = 0; i < 50; i++) {
            reporter.onTestFailed(info("aggregate" + i, new RuntimeException("x".repeat(500))));
        }
        reporter.finish();

        List<String> entries = output.stream().filter(line -> line.startsWith("MS14_NATIVE_FAILURE_SUMMARY aggregate")).toList();
        int payloadBytes = entries.stream().mapToInt(line -> line.substring("MS14_NATIVE_FAILURE_SUMMARY ".length())
                .getBytes(StandardCharsets.UTF_8).length).sum();
        assertEquals(32, entries.size());
        assertTrue(payloadBytes <= 12 * 1024);
        assertTrue(output.stream().anyMatch(line -> line.contains("omitted 18 failure details")));
        verify(delegate, times(50)).onTestFailed(any());
        verify(delegate).finish();
        assertFalse(output.stream().anyMatch(line -> line.contains("all required tests passed")));
    }

    @Test void wrappedFailureIncludesLeafPredicateContextAndProjectOrigin() {
        List<String> output = new ArrayList<>();
        BoundedGameTestReporter reporter = new BoundedGameTestReporter(mock(TestReporter.class), output::add);
        IllegalStateException leaf = new IllegalStateException("sampling solution must contain water");
        AssertionError outer = new AssertionError("sampling denial admission guards", leaf);
        outer.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("com.juicyslew.moonstation14.ms14.botany.BotanySamplingAdmissionGameTests", "finish", "BotanySamplingAdmissionGameTests.java", 373)
        });

        reporter.onTestFailed(info("sampling", outer));
        reporter.finish();

        String summary = output.stream().filter(line -> line.startsWith("MS14_NATIVE_FAILURE_SUMMARY sampling:")).findFirst().orElseThrow();
        assertTrue(summary.contains("sampling solution must contain water"));
        assertTrue(summary.contains("context: sampling denial admission guards"));
        assertTrue(summary.contains("BotanySamplingAdmissionGameTests#finish(BotanySamplingAdmissionGameTests.java:373)"));
    }

    private static GameTestInfo info(String name, Throwable error) {
        GameTestInfo info = mock(GameTestInfo.class);
        when(info.getTestName()).thenReturn(name);
        when(info.getError()).thenReturn(error);
        return info;
    }
}
