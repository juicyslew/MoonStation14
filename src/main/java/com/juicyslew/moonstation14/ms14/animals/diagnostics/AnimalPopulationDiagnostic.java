package com.juicyslew.moonstation14.ms14.animals.diagnostics;

import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/** Opt-in, bounded JFR measurements for an isolated animal population run. */
public final class AnimalPopulationDiagnostic {
    public static final int FLYER_POSE_SAMPLE_INTERVAL_TICKS = 5;
    public static final int FLYER_POSE_SAMPLE_CAP = 16;
    public static final int CLIENT_FLYER_FRAME_CAP = 4;
    private AnimalPopulationDiagnostic() { }

    public enum Scenario { PARROTS, KOBOLDS, MIXED }

    public static boolean enabled() {
        return Boolean.getBoolean("ms14.animalPopulationDiagnostic");
    }

    /** Independently opt-in geometry-heavy flyer pose telemetry within population diagnostics. */
    public static boolean flyerPosesEnabled() {
        return enabled() && Boolean.getBoolean("ms14.animalFlyerPoseDiagnostic");
    }

    static boolean allowFlyerPoseSample(long gameTick, int sampled) {
        return gameTick % FLYER_POSE_SAMPLE_INTERVAL_TICKS == 0 && sampled < FLYER_POSE_SAMPLE_CAP;
    }

    public static double estimateMeshBottomY(double interpolatedY, double bottomPixels, double scale) {
        return interpolatedY + (24.0 - bottomPixels) * scale / 16.0;
    }

    public static void commitClientFlyerPose(ClientFlyerPose event) {
        if (flyerPosesEnabled()) event.commit();
    }

    @Name("ms14.ClientFlyerPose") @Label("MS14 client flyer pose") @StackTrace(false)
    public static final class ClientFlyerPose extends Event {
        @Label("Entity UUID") public String entityId;
        @Label("Dimension / host id") public String dimension;
        @Label("Host entity type id") public String hostEntityType;
        @Label("Client game tick") public long gameTick;
        @Label("Logical entity Y") public double entityY;
        @Label("Interpolated render Y") public double renderY;
        @Label("Raw flight state") public String flightState;
        @Label("Powered flight active") public boolean poweredFlight;
        @Label("Resolved flight capability present") public boolean resolvedFlightCapability;
        @Label("On ground") public boolean onGround;
        @Label("Source clip") public String sourceClip;
        @Label("Destination clip") public String destinationClip;
        @Label("Blend weight") public float blendWeight;
        @Label("Has visible geometry") public boolean hasVisibleGeometry;
        @Label("Model bottom pixels") public double modelBottomPixels;
        @Label("Presentation scale") public float presentationScale;
        @Label("Estimated mesh bottom Y (not camera-matrix proof)") public double estimatedMeshBottomY;
    }

    public static Scenario scenario() {
        if (!enabled()) return null;
        String value = System.getProperty("ms14.animalPopulationScenario", "");
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "parrots" -> Scenario.PARROTS;
            case "kobolds" -> Scenario.KOBOLDS;
            case "mixed" -> Scenario.MIXED;
            default -> throw new IllegalArgumentException("ms14.animalPopulationScenario must be Parrots, Kobolds, or Mixed");
        };
    }

    /** Longer operator-driven capture; requires the ordinary diagnostic opt-in. */
    public static boolean sustained() {
        return PopulationCaptureProtocol.enabled(enabled(), Boolean.getBoolean("ms14.animalPopulationSustained"),
                System.getProperty("ms14.animalPopulationDiagnosticPlayer", ""), AnimalPopulationCapture.directory() != null);
    }

    /** Called by the animal renderer; deliberately a primitive thread-local-per-render-thread counter. */
    public static void animalRenderCall() {
        if (enabled()) AnimalPopulationClientSampler.recordAnimalRenderCall();
    }

    /** Emits an explicit fixture phase boundary into every active JFR recording. */
    public static void markPhase(String phase, int animalCount) {
        markPhase(phase, animalCount, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public static void markPhase(String phase, int animalCount, int ticks, double acceptedDistance,
                                 int groundedJumpRequests, int upwardSamples, double maxDistance) {
        markPhase(phase, animalCount, ticks, acceptedDistance, groundedJumpRequests, upwardSamples, maxDistance, 0, 0, 0);
    }

    public static void recordFlyerPose(java.util.UUID entityId, String dimension, String hostEntityType, long gameTick,
                                       double entityY, boolean onGround, String flightState,
                                       boolean poweredFlight, int expectedFixtureFloorY) {
        if (!flyerPosesEnabled()) return;
        var event = new FlyerPose();
        event.entityId = entityId.toString(); event.dimension = dimension; event.hostEntityType = hostEntityType; event.gameTick = gameTick;
        event.entityY = entityY; event.onGround = onGround; event.flightState = flightState;
        event.poweredFlight = poweredFlight; event.expectedFixtureFloorY = expectedFixtureFloorY;
        event.commit();
    }

    public static void markPhase(String phase, int animalCount, int ticks, double acceptedDistance,
                                 int groundedJumpRequests, int upwardSamples, double maxDistance,
                                 int acceptedInputSamples, int nearBodySamples, int farBodySamples) {
        if (!enabled()) return;
        var event = new Phase();
        event.scenario = scenario().name().toLowerCase(java.util.Locale.ROOT);
        event.phase = phase;
        event.animalCount = animalCount;
        event.phaseTicks = ticks;
        event.acceptedHorizontalDistance = acceptedDistance;
        event.acceptedGroundedJumpRequests = groundedJumpRequests;
        event.positiveAscentSamples = upwardSamples;
        event.maxBodyDistanceFromCenter = maxDistance;
        event.acceptedInputSamples = acceptedInputSamples;
        event.nearBodySamples = nearBodySamples;
        event.farBodySamples = farBodySamples;
        event.commit();
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info("MS14_ANIMAL_POPULATION_PHASE scenario={} phase={} animalCount={}", event.scenario, phase, animalCount);
    }

    @Name("ms14.ServerTickDuration") @Label("MS14 server tick duration") @StackTrace(false)
    public static final class ServerTick extends Event {
        @Label("Phase") public String phase;
        @Label("Loaded fixture animals") public int animalCount;
        @Label("Server game tick") public long gameTick;
    }

    @Name("ms14.ClientFrameDuration") @Label("MS14 client render frame interval") @StackTrace(false)
    public static final class ClientFrame extends Event {
        @Label("Frame interval nanoseconds") public long intervalNanos;
        @Label("Animal renderer calls (not unique visible animals)") public int renderedAnimalCalls;
    }

    @Name("ms14.PopulationPhase") @Label("MS14 population diagnostic phase") @StackTrace(false)
    public static final class Phase extends Event {
        @Label("Scenario") public String scenario;
        @Label("Phase") public String phase;
        @Label("Actual fixture animal count") public int animalCount;
        @Label("Server phase ticks") public int phaseTicks;
        @Label("Accepted horizontal body displacement, accumulated") public double acceptedHorizontalDistance;
        @Label("Accepted jump requests while previously grounded; not successful launches") public int acceptedGroundedJumpRequests;
        @Label("Observed positive vertical displacement samples") public int positiveAscentSamples;
        @Label("Maximum horizontal owned-body distance from population center") public double maxBodyDistanceFromCenter;
        @Label("Committed accepted input samples") public int acceptedInputSamples;
        @Label("Owned-body samples within 32 blocks") public int nearBodySamples;
        @Label("Owned-body samples at least 64 blocks away") public int farBodySamples;
    }

    @Name("ms14.ServerFlyerPose") @Label("MS14 server flyer pose") @StackTrace(false)
    public static final class FlyerPose extends Event {
        @Label("Entity UUID") public String entityId;
        @Label("Dimension / host id") public String dimension;
        @Label("Host entity type id") public String hostEntityType;
        @Label("Server game tick") public long gameTick;
        @Label("Entity Y") public double entityY;
        @Label("On ground") public boolean onGround;
        @Label("Raw flight state") public String flightState;
        @Label("Powered flight active") public boolean poweredFlight;
        @Label("Expected fixture floor Y (not measured support)") public int expectedFixtureFloorY;
    }
}
