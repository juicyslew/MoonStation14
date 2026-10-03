package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PipeGasInventoryTest {
    @Test void detachedSnapshotsPersistVacuumAndDormantGasWithoutReplayingUpdates() {
        var node = new PipeTopology.NodeKey(new BlockPos(4, 5, 6), PipeTopology.Layer.SECONDARY);
        UUID id = UUID.randomUUID();
        PipeGasInventory first = new PipeGasInventory().admit(node, id, 2.0);
        GasMixture seeded = new GasMixture(Map.of(GasType.OXYGEN, 2.0), 300.0);
        PipeGasInventory changed = first.withMixture(node, id, first.get(node).orElseThrow().version(), seeded).unload(node, id);
        var json = PipeGasInventory.CODEC.encodeStart(JsonOps.INSTANCE, changed).getOrThrow(IllegalStateException::new);
        PipeGasInventory loaded = PipeGasInventory.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(IllegalStateException::new);
        assertFalse(loaded.get(node).orElseThrow().active());
        assertEquals(seeded.gasMoles(), loaded.get(node).orElseThrow().mixture().gasMoles());
        assertEquals(changed.revision(), loaded.revision());
        assertEquals(changed.get(node).orElseThrow().version(), loaded.get(node).orElseThrow().version());
        PipeGasInventory joined = loaded.admit(node, id, 2.0);
        assertEquals(2.0, joined.get(node).orElseThrow().mixture().moles(GasType.OXYGEN));
        assertThrows(IllegalStateException.class, () -> joined.withMixture(node, id, first.get(node).orElseThrow().version(), GasMixture.vacuum()));
        assertThrows(IllegalStateException.class, () -> joined.admit(node, UUID.randomUUID(), 2.0));
        assertEquals(1, first.size());
    }

    @Test void tombstoneRequiresVacuumAndRevisionOverflowIsRejected() {
        var node = new PipeTopology.NodeKey(new BlockPos(7, 8, 9), PipeTopology.Layer.PRIMARY);
        UUID id = UUID.randomUUID();
        var inventory = new PipeGasInventory().admit(node, id, 1);
        var seeded = inventory.withMixture(node, id, inventory.get(node).orElseThrow().version(),
                new GasMixture(Map.of(GasType.OXYGEN, 1.0), 300));
        var before = seeded.snapshot();
        assertThrows(IllegalStateException.class, () -> seeded.tombstoneEmpty(node, id));
        assertEquals(before, seeded.snapshot());
        var empty = new PipeGasInventory().admit(node, id, 1).tombstoneEmpty(node, id);
        assertEquals(0, empty.size());
        var max = PipeGasInventory.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(
                "{\"version\":1,\"revision\":9223372036854775807,\"entries\":[]}"))
                .getOrThrow(IllegalStateException::new);
        assertThrows(ArithmeticException.class, () -> max.admit(node, id, 1));
    }

    @Test void duplicateUuidAndNegativeEntryVersionAreRejected() {
        String entry = "{\"x\":0,\"y\":0,\"z\":0,\"layer\":\"PRIMARY\",\"uuid\":\"%s\",\"volume\":1,\"entry_version\":%s,\"temperature\":2.7,\"gases\":[]}";
        UUID id = UUID.randomUUID();
        String first = entry.formatted(id, "1");
        String second = entry.replace("\"x\":0", "\"x\":1").formatted(id, "1");
        assertTrue(PipeGasInventory.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(
                "{\"version\":1,\"revision\":2,\"entries\":[" + first + "," + second + "]}")).error().isPresent());
        assertTrue(PipeGasInventory.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(
                "{\"version\":1,\"revision\":0,\"entries\":[" + entry.formatted(id, "-1") + "]}")).error().isPresent());
    }

    @Test void incarnationCannotBeOwnedByAnotherLiveOrDormantNodeButSameNodeReconciles() {
        var a = new PipeTopology.NodeKey(new BlockPos(0, 0, 0), PipeTopology.Layer.PRIMARY);
        var b = new PipeTopology.NodeKey(new BlockPos(1, 0, 0), PipeTopology.Layer.PRIMARY);
        UUID id = UUID.randomUUID();
        var live = new PipeGasInventory().admit(a, id, 1);
        assertThrows(IllegalStateException.class, () -> live.admit(b, id, 1));
        var dormant = live.unload(a, id);
        assertThrows(IllegalStateException.class, () -> dormant.admit(b, id, 1));
        assertTrue(dormant.admit(a, id, 1).get(a).orElseThrow().active());
    }

    @Test void retirementArchivesGasAndFreshAdmissionCannotReuseRetiredIdentity() {
        var node = new PipeTopology.NodeKey(new BlockPos(20, 2, 3), PipeTopology.Layer.PRIMARY);
        UUID oldId = UUID.randomUUID(), newId = UUID.randomUUID();
        var inventory = new PipeGasInventory().admit(node, oldId, 2);
        var gas = new GasMixture(Map.of(GasType.OXYGEN, 3.0), 300);
        inventory = inventory.withMixture(node, oldId, inventory.get(node).orElseThrow().version(), gas);
        var retired = inventory.retire(node, oldId);
        assertTrue(retired.get(node).isEmpty());
        assertEquals(gas, retired.retiredSnapshot().get(oldId).mixture());
        var fresh = retired.admit(node, newId, 2);
        assertTrue(fresh.get(node).orElseThrow().mixture().gasMoles().isEmpty());
        assertEquals(gas.gasMoles(), fresh.retiredSnapshot().get(oldId).mixture().gasMoles());
        assertEquals(retired.revision(), retired.retire(node, oldId).revision());
        assertThrows(IllegalStateException.class, () -> retired.admit(node, oldId, 2));
        var json = PipeGasInventory.CODEC.encodeStart(JsonOps.INSTANCE, fresh).getOrThrow(IllegalStateException::new);
        var loaded = PipeGasInventory.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(IllegalStateException::new);
        assertEquals(gas.gasMoles(), loaded.retiredSnapshot().get(oldId).mixture().gasMoles());
        assertEquals(gas.thermalEnergy(), loaded.retiredSnapshot().get(oldId).mixture().thermalEnergy(), 1e-9);
        assertTrue(loaded.get(node).orElseThrow().mixture().gasMoles().isEmpty());
    }

    @Test void lanesAreIndependentAndMalformedOrUnknownVersionsFail() {
        var pos = new BlockPos(1, 2, 3);
        var a = new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY);
        var b = new PipeTopology.NodeKey(pos, PipeTopology.Layer.TERTIARY);
        PipeGasInventory inventory = new PipeGasInventory().admit(a, UUID.randomUUID(), 1).admit(b, UUID.randomUUID(), 1);
        assertEquals(2, inventory.size());
        assertTrue(PipeGasInventory.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString("{\"version\":99,\"entries\":[]}")).error().isPresent());
        assertTrue(PipeGasInventory.CODEC.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString("{\"version\":1,\"entries\":[{\"x\":0}]} ")).error().isPresent());
        assertThrows(IllegalArgumentException.class, () -> inventory.admit(a, UUID.randomUUID(), Double.POSITIVE_INFINITY));
    }

    @Test void retirementBatchCommitsOncePreservesGasAndRejectsAnyStaleOwnerAtomically() {
        var pos = new BlockPos(8, 9, 10);
        var primary = new PipeTopology.NodeKey(pos, PipeTopology.Layer.PRIMARY);
        var secondary = new PipeTopology.NodeKey(pos, PipeTopology.Layer.SECONDARY);
        var tertiary = new PipeTopology.NodeKey(pos, PipeTopology.Layer.TERTIARY);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        PipeGasInventory source = new PipeGasInventory().admit(primary, a, 1).admit(secondary, b, 1).admit(tertiary, c, 1);
        var gas = new GasMixture(Map.of(GasType.OXYGEN, 4.0), 300);
        source = source.withMixture(secondary, b, source.get(secondary).orElseThrow().version(), gas);
        var owners = Map.of(primary, a, secondary, b, tertiary, c);
        var unchanged = source;
        var retired = unchanged.retireBatch(owners);
        assertEquals(unchanged.revision() + 1, retired.revision());
        assertEquals(0, retired.size());
        assertEquals(gas, retired.retiredSnapshot().get(b).mixture());
        assertFalse(retired.retiredSnapshot().get(b).active());
        assertFalse(retired.retiredSnapshot().containsKey(a));
        assertFalse(retired.retiredSnapshot().containsKey(c));
        assertSame(retired, retired.retireBatch(Map.of()));

        var staleOwners = Map.of(primary, a, secondary, UUID.randomUUID(), tertiary, c);
        assertThrows(IllegalStateException.class, () -> unchanged.retireBatch(staleOwners));
        assertEquals(3, unchanged.size());
        assertTrue(unchanged.retiredSnapshot().isEmpty());
    }

    @Test void batchIsAtomicConservedAndRejectsStaleOrPartialPlans() {
        var a = new PipeTopology.NodeKey(new BlockPos(10, 0, 0), PipeTopology.Layer.PRIMARY);
        var b = new PipeTopology.NodeKey(new BlockPos(11, 0, 0), PipeTopology.Layer.PRIMARY);
        var idA = UUID.randomUUID(); var idB = UUID.randomUUID();
        var inventory = new PipeGasInventory().admit(a, idA, 1).admit(b, idB, 1);
        inventory = inventory.withMixture(a, idA, inventory.get(a).orElseThrow().version(), new GasMixture(Map.of(GasType.OXYGEN, 2.0), 300));
        inventory = inventory.withMixture(b, idB, inventory.get(b).orElseThrow().version(), GasMixture.vacuum());
        var plan = PipeComponentEqualization.plan(java.util.List.of(
                new PipeComponentEqualization.Node(a, idA, inventory.get(a).orElseThrow().version(), 1, inventory.get(a).orElseThrow().mixture()),
                new PipeComponentEqualization.Node(b, idB, inventory.get(b).orElseThrow().version(), 1, inventory.get(b).orElseThrow().mixture())));
        var source = inventory;
        var result = source.withBatchMixtures(plan);
        assertEquals(source.revision() + 1, result.revision());
        assertEquals(source.get(a).orElseThrow().version() + 1, result.get(a).orElseThrow().version());
        var before = result.snapshot();
        assertThrows(IllegalStateException.class, () -> result.withBatchMixtures(plan));
        var partial = Map.of(a, new PipeGasInventory.ExpectedIdentityVersionAndGasAfter(idA,
                source.get(a).orElseThrow().version(), plan.get(a).gasAfter()));
        assertThrows(IllegalArgumentException.class, () -> source.withBatchMixtures(partial));
        assertEquals(before, result.snapshot());
    }
}
