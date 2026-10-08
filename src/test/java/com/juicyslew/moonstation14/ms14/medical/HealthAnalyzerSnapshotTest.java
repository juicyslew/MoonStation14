package com.juicyslew.moonstation14.ms14.medical;

import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@GameTestTags({"health-analyzer", "prototype", "physiology", "medical-state"})
class HealthAnalyzerSnapshotTest {
    @Test void wireRoundTripRebuildsDamageGroupsFromTypedDamage() {
        var scanner = UUID.randomUUID();
        var actor = UUID.randomUUID();
        var reading = HealthAnalyzerSnapshot.readings(scanner, 4, actor, 2, UUID.randomUUID(), 7,
                "Patient", "Human", "Critical", true, true, Optional.empty(), Optional.empty(),
                false, false, Map.of("blunt", 2f, "heat", 3f));
        var state = new HealthAnalyzerWindowState(scanner, 4, actor, 7, true, Optional.of(reading));
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                net.minecraft.core.RegistryAccess.EMPTY);
        HealthAnalyzerPayloadCodec.writeState(buffer, state);
        var decoded = HealthAnalyzerPayloadCodec.readState(buffer).readings().orElseThrow();
        assertEquals(5f, decoded.totalDamage());
        assertEquals(Map.of("blunt", 2f), decoded.groups().stream().filter(g -> g.name().equals("brute"))
                .findFirst().orElseThrow().damage());
        assertEquals(Map.of("heat", 3f), decoded.groups().stream().filter(g -> g.name().equals("burn"))
                .findFirst().orElseThrow().damage());
    }
    @Test void groupsOnlyPositiveDamageAndSortsByTotal() {
        var snapshot = HealthAnalyzerSnapshot.readings(UUID.randomUUID(), 1, UUID.randomUUID(), 4,
                UUID.randomUUID(), 2, "Patient", "Human", "Critical", true, true,
                Optional.empty(), Optional.of(.4f), true, false,
                Map.of("blunt", 2f, "slash", 3f, "heat", 8f, "holy", 0f));
        assertEquals(13f, snapshot.totalDamage());
        assertEquals("burn", snapshot.groups().getFirst().name());
        assertEquals(8f, snapshot.groups().getFirst().total());
        assertEquals(2, snapshot.groups().size());
        assertTrue(snapshot.temperatureKelvin().isEmpty());
    }

    @Test void invalidOptionalPhysiologyIsUnknown() {
        var snapshot = new HealthAnalyzerSnapshot(UUID.randomUUID(), 0, UUID.randomUUID(), 1,
                UUID.randomUUID(), 0, "", "", "Unknown", false, false,
                Optional.of(Float.NaN), Optional.of(4f), false, false, 0, java.util.List.of(), Map.of());
        assertTrue(snapshot.temperatureKelvin().isEmpty());
        assertEquals(4f, snapshot.usableBloodFraction().orElseThrow());
    }

    @Test void bloodFractionMayExceedOneWhenResolvedPolicyAllowsIt() {
        var snapshot = HealthAnalyzerSnapshot.readings(UUID.randomUUID(), 0, UUID.randomUUID(), 1,
                UUID.randomUUID(), 0, "", "", "Alive", true, true,
                Optional.empty(), Optional.of(1.2f), false, false, Map.of());
        assertEquals(1.2f, snapshot.usableBloodFraction().orElseThrow());
    }
}
