package com.juicyslew.moonstation14.ms14.animals.diagnostics;

import jdk.jfr.Recording;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Dumps the already-running population recording from within its owning JVM. */
public final class AnimalPopulationRecording {
    public static final String RECORDING_NAME = "ms14-population";

    private AnimalPopulationRecording() {
    }

    public static Path dumpCurrentRecording(Path destination) throws IOException {
        var recordings = jdk.jfr.FlightRecorder.getFlightRecorder().getRecordings();
        Recording recording = recordings.stream()
                .filter(candidate -> RECORDING_NAME.equals(candidate.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "JFR recording '" + RECORDING_NAME + "' not found; available recordings: "
                                + recordings.stream().map(Recording::getName).toList()));

        Path parent = destination.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        recording.dump(destination);
        if (!Files.isRegularFile(destination) || Files.size(destination) == 0) {
            throw new IOException("JFR recording '" + RECORDING_NAME
                    + "' dump did not produce a nonempty regular file: " + destination);
        }
        return destination;
    }
}
