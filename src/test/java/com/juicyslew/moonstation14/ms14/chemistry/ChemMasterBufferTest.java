package com.juicyslew.moonstation14.ms14.chemistry;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ChemMasterBufferTest {
    private static ResourceKey<ReagentData> key(String id) {
        return ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", id));
    }

    @Test void defaultBufferInstanceOperationsConserveSelectedSpeciesAndPersistOrder() {
        var a = key("a"); var b = key("b");
        var buffer = new ChemMasterBuffer();
        var beaker = new ReagentAttachment(Map.of(a, 2f, b, 1f));
        assertEquals(200, buffer.extractFrom(beaker, a, ChemMasterBuffer.Amount.ALL));
        assertEquals(Map.of(b, 100L), beaker.snapshotUnits());
        assertEquals(Map.of(a, 200L), buffer.quantities());
        assertEquals(100, buffer.extractFrom(beaker, b, ChemMasterBuffer.Amount.ONE));
        assertEquals(List.of(a, b), buffer.order());
        assertEquals(50, buffer.returnTo(beaker, a, ChemMasterBuffer.Amount.ALL, 50));
        assertEquals(Map.of(a, 150L, b, 100L), buffer.quantities());
        assertEquals(150, buffer.discard(a, ChemMasterBuffer.Amount.ALL));
        beaker.admitUnits(a, 100, ReagentUnits.MAX_CENTS);
        assertEquals(List.of(b), buffer.order());
        assertEquals(150, buffer.extractFrom(beaker, a, ChemMasterBuffer.Amount.ALL));
        assertEquals(List.of(b, a), buffer.order());

        var encoded = ChemMasterBuffer.SNAPSHOT_CODEC.encodeStart(JsonOps.INSTANCE, buffer.snapshot()).getOrThrow();
        var restoredSnapshot = ChemMasterBuffer.SNAPSHOT_CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        var restored = new ChemMasterBuffer(restoredSnapshot);
        assertEquals(buffer.quantities(), restored.quantities());
        assertEquals(buffer.order(), restored.order());
        assertThrows(IllegalArgumentException.class, () -> new ChemMasterBuffer.Snapshot(
                new ReagentComponent(), List.of(a, a)));
    }

    @Test void transfersExactSelectedSpeciesWithFullPartialAndAllClamps() {
        var a = key("a"); var b = key("b");
        var source = new ReagentAttachment(Map.of(a, 3f, b, 2f));
        var target = new ReagentAttachment(Map.of(b, 1f));
        assertEquals(150, ChemMasterBuffer.transfer(source, target, a, ChemMasterBuffer.Amount.FIVE, 250));
        assertEquals(Map.of(a, 150L, b, 200L), source.snapshotUnits());
        assertEquals(Map.of(a, 150L, b, 100L), target.snapshotUnits());
        assertEquals(150, ChemMasterBuffer.transfer(source, target, a, ChemMasterBuffer.Amount.ALL, 1000));
        assertEquals(0, source.snapshotUnits().getOrDefault(a, 0L));
        assertEquals(200, ChemMasterBuffer.transfer(source, target, b, ChemMasterBuffer.Amount.ALL, 600));
        assertEquals(0, source.totalUnits());
        assertEquals(600, target.totalUnits());
    }

    @Test void discardIsExplicitSinkAndOrderPersistsAndReconciles() {
        var a = key("a"); var b = key("b");
        var attachment = new ReagentAttachment(Map.of(a, 1f, b, 2f));
        var buffer = new ChemMasterBuffer(new ChemMasterBuffer.Snapshot(attachment.toComponent(), List.of(b, a)));
        assertEquals(100, buffer.discard(a, ChemMasterBuffer.Amount.ONE));
        assertEquals(List.of(b), buffer.order());
        attachment = new ReagentAttachment(Map.of(a, 1f));
        assertEquals(100, buffer.extractFrom(attachment, a, ChemMasterBuffer.Amount.ALL));
        assertEquals(List.of(b, a), buffer.order());
        var restored = new ChemMasterBuffer(buffer.snapshot());
        assertEquals(buffer.order(), restored.order());
        assertEquals(300, restored.quantities().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(List.of(b, a), restored.sorted(ChemMasterBuffer.Sort.OLDEST, null).stream().map(ChemMasterBuffer.Entry::reagent).toList());
        assertEquals(List.of(a, b), restored.sorted(ChemMasterBuffer.Sort.RECENT, null).stream().map(ChemMasterBuffer.Entry::reagent).toList());
        assertEquals(List.of(b, a), restored.sorted(ChemMasterBuffer.Sort.QUANTITY, null).stream().map(ChemMasterBuffer.Entry::reagent).toList());
        assertEquals(List.of(a, b), restored.sorted(ChemMasterBuffer.Sort.ALPHABETICAL, Comparator.comparing(k -> k.location().toString())).stream().map(ChemMasterBuffer.Entry::reagent).toList());
    }
}
