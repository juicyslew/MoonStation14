package com.juicyslew.moonstation14.ms14.animals.diagnostics;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.Path;
import java.util.*;

/** Captures actual render-frame intervals; JFR execution samples are not frame timing. */
@EventBusSubscriber(modid = com.juicyslew.moonstation14.MoonStation14.MOD_ID, value = Dist.CLIENT)
public final class AnimalPopulationClientSampler {
    private static long previousNanos;
    private static int renderedAnimalCalls;
    private static long captureTick;
    private static boolean captureDone;
    private static final FlyerPoseBudget flyerBudget = new FlyerPoseBudget();
    private static Object budgetWorld;
    private AnimalPopulationClientSampler() { }

    /** Renderer-thread-only increment, guarded at the caller when diagnostics are disabled. */
    public static void recordAnimalRenderCall() { renderedAnimalCalls++; }

    public static boolean tryFlyerPoseSample(java.util.UUID id, Object world, long tick) {
        if (!AnimalPopulationDiagnostic.flyerPosesEnabled()) return false;
        if (budgetWorld != world) { budgetWorld = world; flyerBudget.clear(); }
        return flyerBudget.trySample(id, tick);
    }
    static final class FlyerPoseBudget {
        private final Map<java.util.UUID, Long> lastTick = new LinkedHashMap<>();
        private int scans;
        void beginFrame() { scans = 0; }
        void clear() { lastTick.clear(); scans = 0; }
        boolean trySample(java.util.UUID id, long tick) {
            if (scans >= AnimalPopulationDiagnostic.CLIENT_FLYER_FRAME_CAP) return false;
            Long prior = lastTick.get(id);
            if (prior != null && tick - prior < AnimalPopulationDiagnostic.FLYER_POSE_SAMPLE_INTERVAL_TICKS) return false;
            if (prior == null && lastTick.size() >= AnimalPopulationDiagnostic.FLYER_POSE_SAMPLE_CAP) return false;
            lastTick.put(id, tick); scans++; return true;
        }
    }

    @SubscribeEvent public static void pre(RenderFrameEvent.Pre event) {
        if (!AnimalPopulationDiagnostic.enabled()) return;
        renderedAnimalCalls = 0;
        flyerBudget.beginFrame();
    }

    @SubscribeEvent public static void post(RenderFrameEvent.Post event) {
        if (!AnimalPopulationDiagnostic.enabled()) return;
        AnimalPopulationDiagnostic.scenario();
        long now = System.nanoTime();
        long previous = previousNanos;
        previousNanos = now;
        if (previous == 0) return;
        var sample = new AnimalPopulationDiagnostic.ClientFrame();
        sample.intervalNanos = now - previous;
        sample.renderedAnimalCalls = renderedAnimalCalls;
        sample.commit();
    }

    @SubscribeEvent public static void captureTick(ClientTickEvent.Post event) {
        if (!AnimalPopulationDiagnostic.enabled() || captureDone) return;
        Path dir = AnimalPopulationCapture.directory();
        if (dir == null) return;
        if (++captureTick % 10 != 0) return;
        try {
            AnimalPopulationCapture.Ready marker = AnimalPopulationCapture.readServerReady(dir);
            if (marker == null) return;
            captureDone = true;
            AnimalPopulationCapture.captureClient(dir);
        } catch (Exception e) {
            captureDone = true;
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error("MS14_ANIMAL_POPULATION_RECORDING_FAILED role=client path={}", dir.resolve("client.jfr"), e);
        }
    }
}
