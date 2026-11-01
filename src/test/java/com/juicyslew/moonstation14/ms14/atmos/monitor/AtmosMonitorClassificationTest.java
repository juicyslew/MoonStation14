package com.juicyslew.moonstation14.ms14.atmos.monitor;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeReloadListener;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class AtmosMonitorClassificationTest {
    @Test void canonicalResourcesResolveThroughProductionReloadAndClassifyWithInheritedMonitor(@TempDir Path temp) throws Exception {
        Path pack = Files.createDirectories(temp.resolve("pack"));
        copyResources(pack, "alarm_threshold", List.of("stationtemperature", "stationpressure", "stationoxygen", "stationnitrogen", "stationco2", "stationplasma", "stationtritium", "stationwatervapor", "stationno", "stationammonia", "danger", "ignore"));
        copyResources(pack, "ventilation_device", List.of("gas_unary_base", "gas_vent", "gas_scrubber"));
        PrototypeManager manager = new PrototypeManager();
        manager.register(ModAtmosAlarmThresholds.TYPE); manager.register(ModVentilationDevices.VENTILATION_DEVICE_TYPE);
        try (var resources = resources(pack)) {
            var listener = new PrototypeReloadListener(manager);
            var prepare = PrototypeReloadListener.class.getDeclaredMethod("prepare",
                    net.minecraft.server.packs.resources.ResourceManager.class,
                    net.minecraft.util.profiling.ProfilerFiller.class);
            var apply = PrototypeReloadListener.class.getDeclaredMethod("apply", Map.class,
                    net.minecraft.server.packs.resources.ResourceManager.class,
                    net.minecraft.util.profiling.ProfilerFiller.class);
            prepare.setAccessible(true); apply.setAccessible(true);
            var prepared = prepare.invoke(listener, resources, InactiveProfiler.INSTANCE);
            apply.invoke(listener, prepared, resources, InactiveProfiler.INSTANCE);
            assertTrue(manager.commitStagedReload());
        }
        assertTrue(PrototypeRuntime.serverManager().registeredTypes().contains(ModAtmosAlarmThresholds.TYPE));
        var thresholds = manager.snapshot(ModAtmosAlarmThresholds.TYPE);
        assertEquals(12, thresholds.asMap().size());
        var vent = manager.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE).get(ModVentilationDevices.VENT);
        var scrubber = manager.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE).get(ModVentilationDevices.SCRUBBER);
        assertNotNull(vent.atmosMonitor()); assertEquals(vent.atmosMonitor(), scrubber.atmosMonitor());
        assertEquals(java.util.Set.of("GasVent"), vent.atmosAlarmable().sourceTags());
        assertEquals(java.util.Set.of("GasScrubber"), scrubber.atmosAlarmable().sourceTags());
        assertEquals(AtmosAlarmSeverity.DANGER, AtmosMonitorClassification.classify(thresholds, vent.atmosMonitor(), GasMixture.vacuum()));
        assertTrue(AtmosMonitorSampler.observe(thresholds, vent, Optional.of(GasMixture.vacuum()), 0).orElseThrow().severity() == AtmosAlarmSeverity.DANGER);
        assertTrue(AtmosMonitorSampler.observe(thresholds, vent, Optional.empty(), 0).isEmpty(), "unknown sample cannot become Normal");
        assertEquals(AtmosAlarmSeverity.DANGER, AtmosMonitorSampler.observe(thresholds, vent,
                Optional.of(GasMixture.breathableAir()), 1).orElseThrow().severity(), "committed hotspot is authoritative danger evidence");
        assertTrue(AtmosMonitorSampler.observe(thresholds, vent, Optional.of(GasMixture.breathableAir()), 1)
                .orElseThrow().fireEvidence());
        double total = 101325.0 / (8.3144626 * 293.15);
        var oxygenRich = new GasMixture(Map.of(GasType.OXYGEN, total * .31, GasType.NITROGEN, total * .69), 293.15);
        assertEquals(AtmosAlarmSeverity.DANGER, AtmosMonitorClassification.classify(thresholds, vent.atmosMonitor(), oxygenRich));
        var plasmaWarning = new GasMixture(Map.of(GasType.OXYGEN, total * .21, GasType.NITROGEN, total * .787,
                GasType.PLASMA, total * .003), 293.15);
        assertEquals(AtmosAlarmSeverity.WARNING, AtmosMonitorClassification.classify(thresholds, scrubber.atmosMonitor(), plasmaWarning));
    }

    @Test void boundsAreInclusiveAndMissingResolvedThresholdFailsClosed() {
        var threshold = new AtmosAlarmThresholdData(.1, .3, 1.5, .8, false);
        assertEquals(AtmosAlarmSeverity.DANGER, threshold.classify(.1));
        assertEquals(AtmosAlarmSeverity.DANGER, threshold.classify(.3));
        assertEquals(AtmosAlarmSeverity.WARNING, threshold.classify(.15));
        assertEquals(AtmosAlarmSeverity.WARNING, threshold.classify(.24));
        assertThrows(IllegalArgumentException.class, () -> AtmosMonitorClassification.classify(
                PrototypeRuntime.serverAtmosAlarmThresholds(), null, GasMixture.breathableAir()));
    }

    @Test void receiverAggregatesSourcesHonorsIgnoreAndPowerResetClearsForcedReport() {
        var receiver = new AtmosAlarmReceiverState();
        var first = java.util.UUID.randomUUID(); var second = java.util.UUID.randomUUID();
        assertTrue(receiver.report(first, AtmosAlarmSeverity.WARNING));
        receiver.report(second, AtmosAlarmSeverity.DANGER);
        assertEquals(AtmosAlarmSeverity.DANGER, receiver.severity());
        receiver.remove(second);
        assertEquals(AtmosAlarmSeverity.WARNING, receiver.severity());
        var owner = java.util.UUID.randomUUID();
        receiver.force(owner, AtmosAlarmSeverity.DANGER);
        receiver.powerDown();
        assertEquals(AtmosAlarmSeverity.NORMAL, receiver.severity());
        receiver.resetForced(owner);
        assertEquals(AtmosAlarmSeverity.NORMAL, receiver.severity());
        receiver.setIgnoreAlarms(true);
        assertFalse(receiver.report(first, AtmosAlarmSeverity.DANGER));
        assertEquals(AtmosAlarmSeverity.NORMAL, receiver.severity());
    }

    @Test void receiverReportPolicyFiltersResolvedSyncTagsAndResetsExactSource() {
        var state = new AtmosAlarmReceiverState();
        var one = java.util.UUID.randomUUID();
        var two = java.util.UUID.randomUUID();
        var report = new com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosAlarmPayload.Measurement(
                AtmosAlarmSeverity.WARNING, 100, 293, 1, java.util.Set.of("GasVent"), true, false);
        assertFalse(AtmosAlarmReportPolicy.apply(state, one, java.util.Set.of("AirSensor"),
                java.util.Set.of("GasVent"), report));
        assertTrue(AtmosAlarmReportPolicy.apply(state, one, java.util.Set.of("GasVent"),
                java.util.Set.of("GasVent"), report));
        assertTrue(AtmosAlarmReportPolicy.apply(state, two, java.util.Set.of("GasVent"),
                java.util.Set.of("GasVent"), report));
        assertEquals(AtmosAlarmSeverity.WARNING, state.severity());
        assertTrue(AtmosAlarmReportPolicy.apply(state, one, java.util.Set.of("GasVent"), java.util.Set.of("GasVent"),
                new com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosAlarmPayload.Measurement(
                        AtmosAlarmSeverity.NORMAL, Double.NaN, Double.NaN, Double.NaN, java.util.Set.of("GasVent"), false, false)));
        assertEquals(AtmosAlarmSeverity.WARNING, state.severity(), "unknown observation does not clear report or become Normal");
        assertTrue(AtmosAlarmReportPolicy.apply(state, one, java.util.Set.of("GasVent"),
                java.util.Set.of("GasVent"), new com.juicyslew.moonstation14.ms14.devicenet.atmos.AtmosAlarmPayload.Reset()));
        assertEquals(AtmosAlarmSeverity.WARNING, state.severity(), "second source remains aggregated");
        state.remove(two);
        assertEquals(AtmosAlarmSeverity.NORMAL, state.severity());
    }

    private static void copyResources(Path root, String type, List<String> names) throws IOException {
        String resourceRoot = "data/moonstation14/moonstation14/" + type + "/";
        for (String name : names) {
            String relative = resourceRoot + name + ".json";
            Path destination = root.resolve(relative);
            Files.createDirectories(destination.getParent());
            try (var input = AtmosMonitorClassificationTest.class.getClassLoader().getResourceAsStream(relative)) {
                assertNotNull(input, relative);
                Files.copy(input, destination);
            }
        }
    }

    private static MultiPackResourceManager resources(Path root) {
        PackResources pack = new PathPackResources(new PackLocationInfo("atmos", Component.literal("test"),
                PackSource.DEFAULT, Optional.empty()), root);
        return new MultiPackResourceManager(PackType.SERVER_DATA, List.of(pack));
    }
}
