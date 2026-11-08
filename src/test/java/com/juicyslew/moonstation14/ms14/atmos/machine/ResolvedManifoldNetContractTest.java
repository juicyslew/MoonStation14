package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResolvedManifoldNetContractTest {
    @Test void resolvedSixPortsFormOneAllToAllThreeHundredLiterNetAcrossLayers() {
        var manager = new PrototypeManager();
        manager.register(ModAtmosMachines.TYPE);
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (String id : List.of("atmos_machine_base", "atmos_machine_base_powered", "constructible_machine_base",
                "gas_pipe_sans_layers", "gas_pipe_base", "gas_trinary_base", "gas_pipe_manifold")) {
            var path = "/data/moonstation14/moonstation14/atmos_machine/" + id + ".json";
            try (var input = ResolvedManifoldNetContractTest.class.getResourceAsStream(path)) {
                assertNotNull(input, path);
                raw.put(ModAtmosMachines.id(id), JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
        manager.reload(ModAtmosMachines.TYPE, raw);
        var manifold = manager.snapshot(ModAtmosMachines.TYPE).get(ModAtmosMachines.id("gas_pipe_manifold"));
        assertEquals(6, manifold.ports().size());
        assertEquals(.3, manifold.ports().stream().mapToDouble(AtmosMachineProfile.Port::volumeM3).sum(), 1e-12);
        var inlets = List.of(AtmosMachineProfile.PortName.SOUTH0, AtmosMachineProfile.PortName.SOUTH1, AtmosMachineProfile.PortName.SOUTH2);
        var outlets = List.of(AtmosMachineProfile.PortName.NORTH0, AtmosMachineProfile.PortName.NORTH1, AtmosMachineProfile.PortName.NORTH2);
        for (var inlet : inlets) {
            var port = manifold.ports().stream().filter(p -> p.name() == inlet).findFirst().orElseThrow();
            assertEquals(3, port.alwaysReachablePeers().size());
            assertTrue(port.alwaysReachablePeers().containsAll(outlets));
            assertTrue(port.fixedLayer().isPresent());
        }
        for (var outlet : outlets) {
            var port = manifold.ports().stream().filter(p -> p.name() == outlet).findFirst().orElseThrow();
            assertEquals(3, port.alwaysReachablePeers().size());
            assertTrue(port.alwaysReachablePeers().containsAll(inlets));
            assertTrue(port.fixedLayer().isPresent());
        }
        assertEquals(18, manifold.ports().stream().mapToInt(p -> p.alwaysReachablePeers().size()).sum());
    }
}
