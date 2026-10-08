package com.juicyslew.moonstation14.ms14.medical;

import java.util.function.Consumer;

/** Common-side client receiver hook; server classes never reference client implementations. */
public final class HealthAnalyzerClientReceiver {
    private static volatile Consumer<HealthAnalyzerWindowState> receiver = ignored -> { };
    private HealthAnalyzerClientReceiver() { }
    public static void install(Consumer<HealthAnalyzerWindowState> consumer) { receiver = java.util.Objects.requireNonNull(consumer); }
    public static void receive(HealthAnalyzerWindowUpdate update) { receiver.accept(update.state()); }
}
