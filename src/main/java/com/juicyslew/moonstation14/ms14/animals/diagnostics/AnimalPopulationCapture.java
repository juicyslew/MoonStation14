package com.juicyslew.moonstation14.ms14.animals.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.juicyslew.moonstation14.MoonStation14;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Small fixture-only filesystem handshake for in-process JFR captures. */
final class AnimalPopulationCapture {
    private static final Gson JSON = new GsonBuilder().create();
    private static final String PROPERTY = "ms14.animalPopulationCaptureDir";
    private AnimalPopulationCapture() { }

    static Path directory() {
        if (!AnimalPopulationDiagnostic.enabled()) return null;
        String value = System.getProperty(PROPERTY, "");
        return value.isBlank() ? null : Path.of(value);
    }

    static void captureServer(int count) {
        Path dir = directory();
        if (dir == null) return;
        Path file = dir.resolve("server.jfr");
        try {
            AnimalPopulationRecording.dumpCurrentRecording(file);
            writeReady(dir.resolve("server-ready.json"), new Ready("server", scenario(), count,
                    ProcessHandle.current().pid(), file.getFileName().toString(), Files.size(file)));
            MoonStation14.LOGGER.info("MS14_ANIMAL_POPULATION_RECORDING_READY role=server scenario={}", scenario());
        } catch (Exception e) {
            MoonStation14.LOGGER.error("MS14_ANIMAL_POPULATION_RECORDING_FAILED role=server path={}", file, e);
        }
    }

    static Ready readServerReady(Path dir) throws Exception {
        Path marker = dir.resolve("server-ready.json");
        if (!Files.isRegularFile(marker)) return null;
        Ready ready = JSON.fromJson(Files.readString(marker), Ready.class);
        return ready != null && "server".equals(ready.role) && scenario().equals(ready.scenario)
                && "server.jfr".equals(ready.filename) ? ready : null;
    }

    static void captureClient(Path dir) {
        Path file = dir.resolve("client.jfr");
        try {
            AnimalPopulationRecording.dumpCurrentRecording(file);
            writeReady(dir.resolve("client-ready.json"), new Ready("client", scenario(), 0,
                    ProcessHandle.current().pid(), file.getFileName().toString(), Files.size(file)));
            MoonStation14.LOGGER.info("MS14_ANIMAL_POPULATION_RECORDING_READY role=client scenario={}", scenario());
        } catch (Exception e) {
            MoonStation14.LOGGER.error("MS14_ANIMAL_POPULATION_RECORDING_FAILED role=client path={}", file, e);
        }
    }

    static void writePhaseFailure(String scenario, String phase, int ticks, int expected, int actual,
                                  PopulationCaptureProtocol metrics, String reason) {
        Path dir = directory();
        String summary = "phase="+phase+" ticks="+ticks+" expectedPopulation="+expected+" actualPopulation="+actual+
                " nearSamples="+metrics.nearSamples()+" farSamples="+metrics.farSamples()+" acceptedInputSamples="+metrics.acceptedInputSamples()+
                " acceptedHorizontalDistance="+metrics.acceptedDistance()+" groundedJumps="+metrics.groundedJumps()+
                " upwardSamples="+metrics.upwardSamples()+" maxDistance="+metrics.maxDistance()+" unmet="+metrics.failureReasons(phase,actual,expected);
        MoonStation14.LOGGER.warn("MS14_ANIMAL_POPULATION_PHASE_FAILURE scenario={} {} reason={}", scenario, summary, reason);
        if (dir == null) return;
        try { writeJson(dir.resolve("phase-failure.json"), new PhaseFailure(scenario, phase, ticks, expected, actual,
                metrics.nearSamples(), metrics.farSamples(), metrics.acceptedInputSamples(), metrics.acceptedDistance(),
                metrics.groundedJumps(), metrics.upwardSamples(), metrics.maxDistance(), metrics.failureReasons(phase,actual,expected).toString(), reason)); }
        catch (Exception e) { MoonStation14.LOGGER.error("MS14_ANIMAL_POPULATION_PHASE_FAILURE_WRITE_FAILED path={}", dir.resolve("phase-failure.json"), e); }
    }
    private record PhaseFailure(String scenario, String phase, int ticks, int expectedPopulation, int actualPopulation,
                                int nearSamples, int farSamples, int acceptedInputSamples, double acceptedHorizontalDistance,
                                int groundedJumps, int upwardSamples, double maxDistance, String unmetConditions, String reason) { }

    private static String scenario() { return AnimalPopulationDiagnostic.scenario().name().toLowerCase(java.util.Locale.ROOT); }
    private static void writeReady(Path path, Ready ready) throws Exception {
        writeJson(path, ready);
    }
    private static void writeJson(Path path, Object value) throws Exception {
        Path parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(value));
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
    static final class Ready {
        String role, scenario; int actualCount; long pid, bytes; String filename;
        Ready(String role, String scenario, int count, long pid, String filename, long bytes) {
            this.role=role; this.scenario=scenario; this.actualCount=count; this.pid=pid; this.filename=filename; this.bytes=bytes;
        }
    }
}
