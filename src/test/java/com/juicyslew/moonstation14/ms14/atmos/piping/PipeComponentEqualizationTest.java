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
}
