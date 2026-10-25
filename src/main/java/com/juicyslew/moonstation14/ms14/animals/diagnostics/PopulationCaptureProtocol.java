package com.juicyslew.moonstation14.ms14.animals.diagnostics;

import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** Small pure policy for the opt-in operator capture; the server driver owns all observations. */
final class PopulationCaptureProtocol {
    static final int PHASE_TICKS = 1200;
    static final List<String> PHASES = List.of("population_idle", "population_move", "population_jump", "population_away", "population_return");
    private double acceptedDistance, maxDistance;
    private int groundedJumps, upwardSamples, nearSamples, farSamples, acceptedInputSamples;

    static boolean enabled(boolean diagnostic, boolean sustained, String configuredPlayer, boolean capturePath) {
        return diagnostic && sustained && configuredPlayer != null && !configuredPlayer.isBlank() && capturePath;
    }
    static boolean phaseCompleteAt(int ticks) { return ticks >= PHASE_TICKS; }
    static boolean validCompletion(String scenario, int actualAnimals, int expectedAnimals) {
        return List.of("parrots", "kobolds", "mixed").contains(scenario)
                && actualAnimals == expectedAnimals
                && expectedAnimals == ("mixed".equals(scenario) ? 40 : 100);
    }

    static boolean sameOwner(UUID configuredPlayer, UUID observedPlayer, UUID configuredBody,
                             UUID observedBody, long configuredEpoch, long observedEpoch) {
        return configuredPlayer != null && configuredPlayer.equals(observedPlayer)
                && configuredBody != null && configuredBody.equals(observedBody)
                && configuredEpoch == observedEpoch;
    }

    static String next(String phase) {
        int index = PHASES.indexOf(phase);
        return index >= 0 && index + 1 < PHASES.size() ? PHASES.get(index + 1) : "complete";
    }

    void reset() { acceptedDistance=0; maxDistance=0; groundedJumps=0; upwardSamples=0; nearSamples=0; farSamples=0; acceptedInputSamples=0; }

    void acceptedStep(boolean jumpRequest, boolean groundedBefore, Vec3 before, Vec3 after) {
        acceptedInputSamples++;
        if (jumpRequest && groundedBefore) groundedJumps++;
        if (after.y > before.y + 0.02) upwardSamples++;
        acceptedDistance += Math.hypot(after.x-before.x, after.z-before.z);
    }

    void bodySample(double horizontalDistanceFromCenter) {
        maxDistance = Math.max(maxDistance, horizontalDistanceFromCenter);
        if (horizontalDistanceFromCenter <= 32) nearSamples++;
        if (horizontalDistanceFromCenter >= 64) farSamples++;
    }

    boolean covered(String phase, int actualAnimals, int expectedAnimals) {
        return failureReasons(phase, actualAnimals, expectedAnimals).isEmpty();
    }

    List<String> failureReasons(String phase, int actualAnimals, int expectedAnimals) {
        List<String> failures = new java.util.ArrayList<>();
        if (actualAnimals != expectedAnimals) failures.add("population expected=" + expectedAnimals + " actual=" + actualAnimals);
        return switch (phase) {
            case "population_idle" -> criteria(failures, nearSamples >= 1000, "nearSamples>=1000", acceptedInputSamples > 0, "acceptedInputSamples>0", acceptedDistance <= 2, "acceptedDistance<=2");
            case "population_move" -> criteria(failures, acceptedDistance >= 16, "acceptedDistance>=16", nearSamples >= 100, "nearSamples>=100");
            case "population_jump" -> criteria(failures, groundedJumps >= 3, "groundedJumps>=3", upwardSamples > 0, "upwardSamples>0", nearSamples >= 100, "nearSamples>=100");
            case "population_away" -> criteria(failures, farSamples >= 100, "farSamples>=100");
            case "population_return" -> criteria(failures, nearSamples >= 100, "nearSamples>=100");
            default -> { failures.add("unknown phase"); yield failures; }
        };
    }
    private static List<String> criteria(List<String> failures, Object... pairs) {
        for (int i=0;i<pairs.length;i+=2) if (!(boolean)pairs[i]) failures.add((String)pairs[i+1]);
        return failures;
    }

    double acceptedDistance() { return acceptedDistance; }
    double maxDistance() { return maxDistance; }
    int groundedJumps() { return groundedJumps; }
    int upwardSamples() { return upwardSamples; }
    int nearSamples() { return nearSamples; }
    int farSamples() { return farSamples; }
    int acceptedInputSamples() { return acceptedInputSamples; }
}
