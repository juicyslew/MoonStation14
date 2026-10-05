package com.juicyslew.moonstation14.ms14.atmos.piping;

import net.minecraft.nbt.CompoundTag;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PipeGasInventorySavedDataTest {
    @Test void saveLoadRoundTripAndCompareAndReplaceRejectsStaleRevision() {
        var node = new PipeTopology.NodeKey(new net.minecraft.core.BlockPos(1, 2, 3), PipeTopology.Layer.PRIMARY);
        java.util.UUID id = java.util.UUID.randomUUID();
        PipeGasInventory initial = new PipeGasInventory().admit(node, id, 2);
        PipeGasInventorySavedData data = new PipeGasInventorySavedData();
        PipeGasInventory before = data.snapshot();
        assertTrue(data.replace(before.revision(), before, initial));
        var admitted = initial.get(node).orElseThrow();
        initial = initial.withMixture(node, id, admitted.version(),
                new GasMixture(java.util.Map.of(GasType.OXYGEN, 2.5, GasType.NITROGEN, 7.5), 312.0));
        PipeGasInventory admittedSnapshot = data.snapshot();
        assertTrue(data.replace(admittedSnapshot.revision(), admittedSnapshot, initial));
        CompoundTag saved = data.save(new CompoundTag(), null);
        PipeGasInventorySavedData loaded = PipeGasInventorySavedData.load(saved, null);
        assertEquals(initial.revision(), loaded.revision());
        assertEquals(initial.size(), loaded.snapshot().size());
        assertEquals(initial.get(node).orElseThrow().version(), loaded.snapshot().get(node).orElseThrow().version());
        assertEquals(initial.get(node).orElseThrow().mixture().gasMoles(),
                loaded.snapshot().get(node).orElseThrow().mixture().gasMoles());
        assertEquals(initial.get(node).orElseThrow().incarnation(), loaded.snapshot().get(node).orElseThrow().incarnation());
        assertEquals(initial.get(node).orElseThrow().mixture().thermalEnergy(),
                loaded.snapshot().get(node).orElseThrow().mixture().thermalEnergy());

        PipeGasInventory loadedBefore = loaded.snapshot();
        assertFalse(loadedBefore.get(node).orElseThrow().active());
        PipeGasInventory reactivated = loadedBefore.admit(node, id, 2);
        assertTrue(loaded.replace(loadedBefore.revision(), loadedBefore, reactivated));

        PipeGasInventory activeSnapshot = loaded.snapshot();
        PipeGasInventory newer = activeSnapshot.unload(node, id);
        assertTrue(loaded.replace(activeSnapshot.revision(), activeSnapshot, newer));
        assertFalse(loaded.replace(activeSnapshot.revision(), activeSnapshot, activeSnapshot));
        assertEquals(newer.revision(), loaded.revision());
        assertFalse(loaded.snapshot().get(node).orElseThrow().active());
        assertEquals(initial.get(node).orElseThrow().mixture().gasMoles(),
                loaded.snapshot().get(node).orElseThrow().mixture().gasMoles());
        assertEquals(initial.get(node).orElseThrow().mixture().thermalEnergy(),
                loaded.snapshot().get(node).orElseThrow().mixture().thermalEnergy());
    }

    @Test void replaceRequiresCurrentSnapshotAndExactlyNextRevision() {
        PipeGasInventorySavedData data = new PipeGasInventorySavedData();
        PipeGasInventory before = data.snapshot();
        PipeGasInventory replacement = before.admit(
                new PipeTopology.NodeKey(new net.minecraft.core.BlockPos(1, 2, 3), PipeTopology.Layer.PRIMARY),
                java.util.UUID.randomUUID(), 2);

        assertFalse(data.replace(before.revision(), replacement, replacement));
        assertFalse(data.isDirty());
        assertSame(before, data.snapshot());

        assertFalse(data.replace(before.revision(), before, before));
        assertFalse(data.isDirty());
        assertFalse(data.replace(before.revision(), before, new PipeGasInventory()));
        assertFalse(data.isDirty());

        assertTrue(data.replace(before.revision(), before, replacement));
        assertTrue(data.isDirty());
        assertSame(replacement, data.snapshot());
        assertFalse(data.replace(before.revision(), before, replacement));
        assertSame(replacement, data.snapshot());
    }

    @Test void replaceRejectsRevisionOverflow() {
        CompoundTag saved = new PipeGasInventorySavedData().save(new CompoundTag(), null);
        saved.getCompound("inventory").putLong("revision", Long.MAX_VALUE);
        PipeGasInventorySavedData data = PipeGasInventorySavedData.load(saved, null);
        PipeGasInventory before = data.snapshot();
        assertFalse(data.replace(Long.MAX_VALUE, before, before));
        assertFalse(data.isDirty());
        assertSame(before, data.snapshot());
    }

    @Test void rawSwapIsUnpublishedUntilPublishAndRollbackRestoresExactSnapshot() {
        PipeGasInventorySavedData data = new PipeGasInventorySavedData();
        PipeGasInventory before = data.snapshot();
        var node = new PipeTopology.NodeKey(new net.minecraft.core.BlockPos(4, 5, 6), PipeTopology.Layer.PRIMARY);
        PipeGasInventory after = before.admit(node, java.util.UUID.randomUUID(), 2);
        var token = data.beginRawSwap(before, after);
        assertNotNull(token);
        assertFalse(data.isDirty());
        assertThrows(IllegalStateException.class, () -> data.save(new CompoundTag(), null));
        assertThrows(IllegalStateException.class, () -> data.replace(after.revision(), after, before));
        assertSame(after, data.snapshot());
        assertTrue(data.rollbackRawSwap(token));
        assertSame(before, data.snapshot());
        assertEquals(before.revision(), data.revision());
        assertFalse(data.isDirty());
        assertNotNull(data.save(new CompoundTag(), null));
    }

    @Test void rawSwapPublishMarksDirtyAndRejectsStaleTokenAndOverflow() {
        PipeGasInventorySavedData data = new PipeGasInventorySavedData();
        PipeGasInventory before = data.snapshot();
        var node = new PipeTopology.NodeKey(new net.minecraft.core.BlockPos(7, 8, 9), PipeTopology.Layer.PRIMARY);
        PipeGasInventory after = before.admit(node, java.util.UUID.randomUUID(), 2);
        var token = data.beginRawSwap(before, after);
        assertNotNull(token);
        data.publishRawSwap(token);
        assertTrue(data.isDirty());
        assertThrows(IllegalStateException.class, () -> data.publishRawSwap(token));
        assertNull(data.beginRawSwap(before, after));

        CompoundTag encoded = new PipeGasInventorySavedData().save(new CompoundTag(), null);
        encoded.getCompound("inventory").putLong("revision", Long.MAX_VALUE);
        PipeGasInventorySavedData overflow = PipeGasInventorySavedData.load(encoded, null);
        PipeGasInventory max = overflow.snapshot();
        assertNull(overflow.beginRawSwap(max, new PipeGasInventory()));
        assertSame(max, overflow.snapshot());
        assertFalse(overflow.isDirty());
    }

    @Test void malformedAndMissingInventoryAreNotDefaulted() {
        CompoundTag missing = new CompoundTag();
        assertThrows(IllegalArgumentException.class, () -> PipeGasInventorySavedData.load(missing, null));
        CompoundTag malformed = new CompoundTag();
        CompoundTag inventory = new CompoundTag();
        inventory.putInt("version", 99);
        inventory.putLong("revision", 0);
        inventory.put("entries", new net.minecraft.nbt.ListTag());
        malformed.put("inventory", inventory);
        assertThrows(IllegalArgumentException.class, () -> PipeGasInventorySavedData.load(malformed, null));
    }
}
