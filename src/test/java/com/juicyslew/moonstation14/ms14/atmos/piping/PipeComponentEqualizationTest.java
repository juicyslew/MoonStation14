package com.juicyslew.moonstation14.ms14.atmos.piping;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PipeComponentEqualizationTest {
    private static PipeTopology.NodeKey key(int x, PipeTopology.Layer layer) {
        return new PipeTopology.NodeKey(new BlockPos(x, 2, 3), layer);
    }

    @Test void branchEqualizesByVolumeAndKeepsCoLocatedOtherLaneIndependent() {
        var a = key(0, PipeTopology.Layer.PRIMARY);
        var b = key(1, PipeTopology.Layer.PRIMARY);
        var c = key(2, PipeTopology.Layer.PRIMARY);
        var isolated = key(1, PipeTopology.Layer.SECONDARY);
        var hot = new GasMixture(Map.of(GasType.OXYGEN, 4.0, GasType.NITROGEN, 2.0), 500);
        var cold = new GasMixture(Map.of(GasType.OXYGEN, 1.0), 100);
        var untouched = new GasMixture(Map.of(GasType.NITROGEN, 3.0), 240);
        var nodes = List.of(new PipeComponentEqualization.Node(a, UUID.randomUUID(), 1, .2, hot),
                new PipeComponentEqualization.Node(b, UUID.randomUUID(), 2, .2, cold),
                new PipeComponentEqualization.Node(c, UUID.randomUUID(), 3, .4, GasMixture.vacuum()));
        var plan = PipeComponentEqualization.plan(nodes);
        var results = plan.values().stream().map(PipeGasInventory.ExpectedIdentityVersionAndGasAfter::gasAfter).toList();
        assertEquals(1.25, results.get(0).moles(GasType.OXYGEN), 1e-12);
        assertEquals(1.25, results.get(1).moles(GasType.OXYGEN), 1e-12);
        assertEquals(2.5, results.get(2).moles(GasType.OXYGEN), 1e-12);
        assertEquals(5, results.stream().mapToDouble(m -> m.moles(GasType.OXYGEN)).sum(), 1e-12);
        assertEquals(2, results.stream().mapToDouble(m -> m.moles(GasType.NITROGEN)).sum(), 1e-12);
        double energy = nodes.stream().mapToDouble(n -> n.mixture().thermalEnergy()).sum();
        assertEquals(energy, results.stream().mapToDouble(GasMixture::thermalEnergy).sum(), Math.max(1e-9, energy * 1e-12));
        assertFalse(plan.containsKey(isolated));
        assertEquals(untouched.moles(GasType.NITROGEN), 3.0);
    }

    @Test void lowMolePlasmaFromOneConcealedNodeSplitsAcrossElevenPointTwoCubicMeterNodes() {
        var inventory = new PipeGasInventory();
        var owners = new java.util.ArrayList<UUID>();
        var keys = new java.util.ArrayList<PipeTopology.NodeKey>();
        for (int i = 0; i < 11; i++) {
            var id = UUID.randomUUID();
            var nodeKey = key(i, PipeTopology.Layer.PRIMARY);
            owners.add(id);
            keys.add(nodeKey);
            inventory = inventory.admit(nodeKey, id, 0.2);
        }
        var source = new GasMixture(Map.of(GasType.PLASMA, 0.4), 300.0, 0.2);
        inventory = inventory.withMixture(keys.getFirst(), owners.getFirst(), 1, source);
        var nodes = new java.util.ArrayList<PipeComponentEqualization.Node>();
        for (int i = 0; i < 11; i++) {
            var entry = inventory.get(keys.get(i)).orElseThrow();
            nodes.add(new PipeComponentEqualization.Node(entry.node(), entry.incarnation(), entry.version(),
                    entry.volume(), entry.mixture()));
        }
        var plan = PipeComponentEqualization.plan(nodes);
        assertEquals(11, plan.size());
        PipeGasInventory after = inventory.withBatchMixtures(plan);
        double totalPlasma = 0, totalEnergy = 0;
        for (var node : nodes) {
            var committed = after.get(node.key()).orElseThrow();
            assertTrue(committed.mixture().moles(GasType.PLASMA) > 0.0,
                    "every owner receives representable Plasma: " + committed.mixture());
            assertEquals(node.incarnation(), committed.incarnation());
            assertEquals(node.version() + 1, committed.version());
            totalPlasma += committed.mixture().moles(GasType.PLASMA);
            totalEnergy += committed.mixture().thermalEnergy();
        }
        assertEquals(source.moles(GasType.PLASMA), totalPlasma,
                Math.ulp((float) source.moles(GasType.PLASMA)) * 8.0);
        assertEquals(source.thermalEnergy(), totalEnergy,
                Math.max(Math.ulp((float) source.thermalEnergy()) * 8.0, Math.ulp(source.thermalEnergy()) * 8.0));
    }
}
