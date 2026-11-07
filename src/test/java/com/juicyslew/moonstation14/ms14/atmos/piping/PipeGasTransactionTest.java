package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.prototype.ModVentilationDevices;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.lang.reflect.Modifier;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PipeGasTransactionTest {
    @Test void planCannotBeConstructedByCallers() {
        assertTrue(java.util.Arrays.stream(PipeGasTransaction.Plan.class.getDeclaredConstructors())
                .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())));
        var source = source(); var destination = destination();
        var plan = plan(source, destination);
        for (GasType gas : GasType.values())
            assertEquals(source.gas.moles(gas) + destination.gas.moles(gas),
                    plan.transfer().sourceAfter().moles(gas) + plan.transfer().destinationAfter().moles(gas), 1e-12);
        assertEquals(source.gas.thermalEnergy() + destination.gas.thermalEnergy(),
                plan.transfer().sourceAfter().thermalEnergy() + plan.transfer().destinationAfter().thermalEnergy(), 1e-8);
    }

    private static class MemoryEndpoint implements PipeGasTransaction.Endpoint {
        final Object id = new Object();
        GasMixture gas;
        long version = 1;
        int writes, publications;
        int failWrite;
        boolean throwsOnFailure;
        MemoryEndpoint(GasMixture gas) { this.gas = gas; }
        public Object identity() { return id; }
        public long version() { return version; }
        public GasMixture snapshot() { return gas; }
        public boolean eligible() { return true; }
        public boolean writeRaw(GasMixture before, GasMixture next) {
            writes++;
            if (next != null && before != null && !same(gas, before)) return false;
            gas = next;
            if (writes == failWrite) {
                if (throwsOnFailure) throw new IllegalStateException("injected");
                return false;
            }
            return true;
        }
        public void publish() { publications++; }
    }

    @Test void rollbackIncludesRejectingAndThrowingWriterEndpoints() {
        for (int failedEnd = 1; failedEnd <= 2; failedEnd++) for (boolean throwing : new boolean[]{false, true}) {
            var source = source(); var destination = destination();
            var plan = plan(source, destination);
            MemoryEndpoint failing = failedEnd == 1 ? source : destination;
            failing.failWrite = failedEnd == 1 ? 1 : 1;
            failing.throwsOnFailure = throwing;
            GasMixture originalSource = source.gas, originalDestination = destination.gas;
            if (failedEnd == 1) assertThrows(IllegalStateException.class, () -> PipeGasTransaction.commit(source, destination, plan));
            else assertThrows(IllegalStateException.class, () -> PipeGasTransaction.commit(source, destination, plan));
            assertTrue(same(source.gas, originalSource)); assertTrue(same(destination.gas, originalDestination));
            assertEquals(0, source.publications + destination.publications);
        }
    }

    @Test void destinationRejectAfterMutationRestoresBothEnds() {
        var source = source(); var destination = destination(); var plan = plan(source, destination);
        GasMixture sb = source.gas, db = destination.gas;
        destination.failWrite = 1;
        assertThrows(IllegalStateException.class, () -> PipeGasTransaction.commit(source, destination, plan));
        assertTrue(same(source.gas, sb)); assertTrue(same(destination.gas, db));
        assertEquals(0, source.publications + destination.publications);
    }

    @Test void staleInputsAreRejectedBeforeWrites() {
        var source = source(); var destination = destination(); var plan = plan(source, destination);
        source.version++;
        assertThrows(IllegalStateException.class, () -> PipeGasTransaction.commit(source, destination, plan));
        assertEquals(0, source.writes + destination.writes);
        source.version--;
        destination.gas = new GasMixture(Map.of(GasType.NITROGEN, 1.0), 300);
        assertThrows(IllegalStateException.class, () -> PipeGasTransaction.commit(source, destination, plan));
        assertEquals(0, source.writes + destination.writes);
    }

    @Test void selectedGasCommitConservesSpeciesAndEnergyAndPublishesOnce() {
        var source = source(); var destination = destination();
        var plan = PipeGasTransaction.plan(source, destination, 1, 1, 1, 10000, Set.of(GasType.OXYGEN)).orElseThrow();
        double energy = source.gas.thermalEnergy() + destination.gas.thermalEnergy();
        PipeGasTransaction.commit(source, destination, plan);
        assertEquals(1, source.publications); assertEquals(1, destination.publications);
        assertEquals(2, source.gas.moles(GasType.PLASMA), 0);
        assertEquals(2, source.gas.moles(GasType.PLASMA) + destination.gas.moles(GasType.PLASMA), 1e-12);
        assertEquals(energy, source.gas.thermalEnergy() + destination.gas.thermalEnergy(), 1e-8);
    }

    @Test void preparedPairValidatesBothBeforeWritingAndCompensatesBeforePublishing() {
        var a=source(); var b=destination();
        var left=PipeGasTransaction.prepare(a,a.gas,new GasMixture(Map.of(GasType.OXYGEN,3.0,GasType.PLASMA,2.0),800));
        var right=PipeGasTransaction.prepare(b,b.gas,new GasMixture(Map.of(GasType.NITROGEN,4.0),200));
        GasMixture beforeA=a.gas, beforeB=b.gas;
        b.version++;
        assertThrows(IllegalStateException.class,()->PipeGasTransaction.commit(left,right));
        assertEquals(0,a.writes+b.writes);
        b.version--;
        b.failWrite=1;
        assertThrows(IllegalStateException.class,()->PipeGasTransaction.commit(left,right));
        assertTrue(same(beforeA,a.gas)); assertTrue(same(beforeB,b.gas));
        assertEquals(0,a.publications+b.publications);
    }

    @Test void ordinaryResolvedVentAndScrubberPacketsSurviveTransactionalCap() {
        // These are the authored default gas inventories used by the paid finite-vent fixtures.
        var definitions = resolvedVentilationDefinitions();
        var ventDefinition = definitions.get(ModVentilationDevices.VENT);
        var scrubberDefinition = definitions.get(ModVentilationDevices.SCRUBBER);
        assertNotNull(ventDefinition, "resolved vent prototype");
        assertNotNull(scrubberDefinition, "resolved scrubber prototype");
        var ventSettings = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT, ventDefinition).settings();
        var ventPipe = new GasMixture(Map.of(GasType.OXYGEN, 12.0, GasType.NITROGEN, 45.0), 310.0);
        var vacuumRoom = GasMixture.vacuum();
        for (double elapsedSeconds : new double[]{0.05, 1.0}) {
            // Fresh endpoints model each independent paid interval, including the full 20-tick solve.
            var ventReference = VentilationGasFlow.planVentRelease(ventPipe, vacuumRoom, 0.2, 1.0,
                    elapsedSeconds, ventSettings, ventDefinition.gasVentPump()).orElseThrow();
            var ventSource = new MemoryEndpoint(ventPipe);
            var ventDestination = new MemoryEndpoint(vacuumRoom);
            var ventTransaction = PipeGasTransaction.plan(ventSource, ventDestination, 0.2, 1.0,
                    ventReference.packet().totalMoles(),
                    VentilationGasFlow.releaseDestinationPressureLimit(ventSettings, ventDefinition.gasVentPump()),
                    Set.of(GasType.values())).orElseThrow();
            assertPacketNoGreaterThanReference("vent elapsedSeconds=" + elapsedSeconds + " targetKPa="
                    + ventSettings.targetPressureKPa() + " pressureChangeKPaPerSecond="
                    + ventSettings.targetPressureChangeKPaPerSecond(), ventReference, ventTransaction.transfer());
        }

        var scrubberSettings = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.SCRUBBER, scrubberDefinition).settings();
        var roomGas = new GasMixture(Map.of(GasType.OXYGEN, 100.0, GasType.NITROGEN, 300.0,
                GasType.CARBON_DIOXIDE, 20.0), 300.0);
        var wastePipe = GasMixture.vacuum();
        var scrubberReference = VentilationGasFlow.planScrubberCapture(roomGas, wastePipe, 1.0, 0.2, 1.0,
                scrubberSettings).orElseThrow();
        var scrubberSource = new MemoryEndpoint(roomGas);
        var scrubberDestination = new MemoryEndpoint(wastePipe);
        var scrubberTransaction = PipeGasTransaction.plan(scrubberSource, scrubberDestination, 1.0, 0.2,
                scrubberReference.packet().totalMoles(), 5_066.25, scrubberSettings.gasesToScrub()).orElseThrow();
        assertPacketNoGreaterThanReference("scrubber elapsedSeconds=1.0 volumeRateM3PerSecond="
                + scrubberSettings.volumeRateM3PerSecond(), scrubberReference, scrubberTransaction.transfer());
    }

    @Test void r17WasteMixturesReproduceOrRuleOutSecondVentPlanRejection() {
        var definitions = resolvedVentilationDefinitions();
        var ventSettings = new VentilationDeviceConfig(VentilationDeviceConfig.Kind.VENT,
                definitions.get(ModVentilationDevices.VENT)).settings();
        assertTrue(ventSettings.configuredEnabled());
        var waste = Map.of(GasType.OXYGEN, 3.797210872, GasType.PLASMA, .082425514,
                GasType.CARBON_DIOXIDE, .422419949, GasType.TRITIUM, .024867844,
                GasType.WATER_VAPOR, .008497368);
        int validReferenceCases = 0;
        for (double scale : new double[]{.001,.002,.005,.01,.02,.05,.1,.2,1})
            for (double temperature : new double[]{293.15,600,700,767})
                for (double elapsed : new double[]{.6,1.0}) {
                    var source = new GasMixture(waste.entrySet().stream().collect(
                            java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue()*scale)), temperature);
                    var vacuum = GasMixture.vacuum();
                    var reference = VentilationGasFlow.planVentRelease(source, vacuum, .2, 1.0, elapsed,
                            ventSettings, definitions.get(ModVentilationDevices.VENT).gasVentPump()).orElse(null);
                    if (reference == null) continue;
                    validReferenceCases++;
                    var actual = PipeGasTransfer.plan(source, vacuum, .2, 1.0,
                            reference.packet().totalMoles(), VentilationGasFlow.releaseDestinationPressureLimit(ventSettings,
                                    definitions.get(ModVentilationDevices.VENT).gasVentPump()), Set.of(GasType.values())).orElse(null);
                    boolean rejected = actual == null;
                    if (actual != null) {
                        for (GasType gas : GasType.values())
                            rejected |= actual.perGasMoved().getOrDefault(gas, 0.0)
                                    > reference.perGasMoved().getOrDefault(gas, 0.0);
                        rejected |= actual.packet().totalMoles() > reference.packet().totalMoles()
                                || actual.energyMoved() > reference.energyMoved();
                    }
                    assertFalse(rejected, "r17 reference unexpectedly rejected/exceeded: scale=" + scale
                            + " T=" + temperature + " elapsed=" + elapsed + " reference="
                            + transferValues(reference) + " actual=" + (actual == null ? "EMPTY" : transferValues(actual)));
                }
        assertEquals(72, validReferenceCases, "every specified sweep input should produce a valid reference");
    }

    @Test void exportReceiptConservationUsesWholeInventoryPrecisionDomain() {
        double temperature = 310.0;
        double sourceVolume = 0.2;
        double roomVolume = 1.0;
        double ambientKpa = 0.0;
        double boundKpa = ambientKpa + 0.05;
        double sourcePressureKpa = boundKpa / 2.0 + 10.0;
        double sourceMoles = sourcePressureKpa * sourceVolume * 1000.0
                / (8.31446261815324 * temperature);
        var before = new GasMixture(Map.of(GasType.OXYGEN, sourceMoles), temperature);
        var exterior = GasMixture.vacuum();
        double independentBudget = (boundKpa - ambientKpa) * roomVolume * 1000.0
                / (8.31446261815324 * temperature);
        var plan = PipeGasTransfer.plan(before, exterior, sourceVolume, roomVolume,
                independentBudget * 2.0, boundKpa, Set.of(GasType.OXYGEN)).orElseThrow();
        var after = plan.sourceAfter();
        var packet = plan.packet();

        assertTrue(after.totalMoles() < before.totalMoles());
        assertTrue(packet.totalMoles() <= independentBudget + 8.0 * Math.max(
                Math.ulp((float) packet.totalMoles()), Math.ulp((float) independentBudget)));
        assertFalse(PipeGasTransfer.close(before.thermalEnergy() - after.thermalEnergy(), plan.energyMoved()),
                "small subtraction loses float-backed inventory precision");
        for (GasType gas : GasType.values())
            assertTrue(PipeGasTransfer.close(before.moles(gas), after.moles(gas) + packet.moles(gas)),
                    "whole-inventory species conservation: " + gas);
        assertTrue(PipeGasTransfer.close(before.thermalEnergy(), after.thermalEnergy() + plan.energyMoved()),
                "whole-inventory enthalpy conservation uses shared predicate");
    }

    static com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<
            com.juicyslew.moonstation14.ms14.atmos.piping.prototype.VentilationDeviceData> resolvedVentilationDefinitions() {
        var manager = new PrototypeManager();
        manager.register(ModVentilationDevices.VENTILATION_DEVICE_TYPE);
        Map<ResourceLocation, JsonObject> raw = new java.util.LinkedHashMap<>();
        raw.put(ModVentilationDevices.BASE, ventilationResource("gas_unary_base"));
        raw.put(ModVentilationDevices.VENT, ventilationResource("gas_vent"));
        raw.put(ModVentilationDevices.SCRUBBER, ventilationResource("gas_scrubber"));
        manager.reload(ModVentilationDevices.VENTILATION_DEVICE_TYPE, raw);
        return manager.snapshot(ModVentilationDevices.VENTILATION_DEVICE_TYPE);
    }

    private static JsonObject ventilationResource(String name) {
        String path = "/data/moonstation14/moonstation14/ventilation_device/" + name + ".json";
        try (var reader = new InputStreamReader(java.util.Objects.requireNonNull(
                PipeGasTransactionTest.class.getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException failure) {
            throw new RuntimeException(failure);
        }
    }

    private static void assertPacketNoGreaterThanReference(String label, PipeGasTransfer.Plan reference,
                                                           PipeGasTransfer.Plan actual) {
        String values = transferValues(reference) + " actual{" + transferValues(actual) + "}";
        assertTrue(actual.packet().totalMoles() <= reference.packet().totalMoles(), label + " total moles actual="
                + actual.packet().totalMoles() + " reference=" + reference.packet().totalMoles() + " " + values);
        for (GasType gas : GasType.values()) {
            double actualMoles = actual.packet().moles(gas);
            double referenceMoles = reference.packet().moles(gas);
            assertTrue(actualMoles <= referenceMoles, label + " " + gas + " actual=" + actualMoles
                    + " reference=" + referenceMoles + " " + values);
        }
        assertTrue(actual.energyMoved() <= reference.energyMoved(), label + " energy actual=" + actual.energyMoved()
                + " reference=" + reference.energyMoved() + " " + values);
    }

    private static String transferValues(PipeGasTransfer.Plan plan) {
        StringBuilder result = new StringBuilder("total=").append(plan.packet().totalMoles()).append(",perGas={");
        for (GasType gas : GasType.values()) {
            if (result.charAt(result.length() - 1) != '{') result.append(',');
            result.append(gas).append('=').append(plan.packet().moles(gas));
        }
        return result.append("},energy=").append(plan.energyMoved()).toString();
    }

    private static MemoryEndpoint source() { return new MemoryEndpoint(new GasMixture(Map.of(GasType.OXYGEN, 4.0, GasType.PLASMA, 2.0), 800)); }
    private static MemoryEndpoint destination() { return new MemoryEndpoint(new GasMixture(Map.of(GasType.NITROGEN, 3.0), 200)); }
    private static PipeGasTransaction.Plan plan(MemoryEndpoint s, MemoryEndpoint d) {
        return PipeGasTransaction.plan(s, d, 1, 1, 1, 10000, Set.of(GasType.OXYGEN)).orElseThrow();
    }
    private static boolean same(GasMixture a, GasMixture b) {
        if (Double.compare(a.temperatureKelvin(), b.temperatureKelvin()) != 0) return false;
        for (GasType type : GasType.values()) if (Double.compare(a.moles(type), b.moles(type)) != 0) return false;
        return true;
    }
}
