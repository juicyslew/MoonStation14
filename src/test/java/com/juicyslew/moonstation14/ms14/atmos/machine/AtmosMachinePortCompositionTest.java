package com.juicyslew.moonstation14.ms14.atmos.machine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AtmosMachinePortCompositionTest {
    private static JsonObject profile(String parent, String ports) {
        var json = new JsonObject();
        if (parent != null) json.addProperty("parent", parent);
        json.addProperty("kind", "manual_valve");
        json.add("ports", JsonParser.parseString(ports));
        json.add("networkNodes", JsonParser.parseString("[]"));
        json.add("valve", JsonParser.parseString("{\"open\":true}"));
        return json;
    }

    private static PrototypeManager resolve(JsonObject base, JsonObject child) {
        var manager = new PrototypeManager();
        manager.register(ModAtmosMachines.TYPE);
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        raw.put(ModAtmosMachines.id("synthetic_base"), base);
        raw.put(ModAtmosMachines.id("synthetic_child"), child);
        manager.reload(ModAtmosMachines.TYPE, raw);
        return manager;
    }

    @Test void absentFieldsDefaultAndInheritedResolvedPortsRetainLayerPeers() {
        var absent = AtmosMachineProfile.Port.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"name\":\"north0\",\"direction\":\"north\"}")).getOrThrow();
        assertEquals(Optional.empty(), absent.fixedLayer());
        assertEquals(List.of(), absent.alwaysReachablePeers());
        assertEquals(com.juicyslew.moonstation14.ms14.atmos.piping.Ss14VentilationDefaults.PIPE_NODE_VOLUME_M3, absent.volumeM3());

        var base = profile(null, "[{\"name\":\"inlet\",\"direction\":\"north\",\"fixedLayer\":\"secondary\",\"alwaysReachablePeers\":[\"outlet\"]},{\"name\":\"outlet\",\"direction\":\"south\",\"fixedLayer\":\"secondary\",\"alwaysReachablePeers\":[\"inlet\"]}]");
        var child = new JsonObject();
        child.addProperty("parent", "synthetic_base");
        child.add("networkNodes", JsonParser.parseString("[]"));
        var resolved = resolve(base, child).snapshot(ModAtmosMachines.TYPE).get(ModAtmosMachines.id("synthetic_child"));
        assertEquals(Optional.of(com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.SECONDARY), resolved.ports().get(0).fixedLayer());
        assertEquals(List.of(AtmosMachineProfile.PortName.OUTLET), resolved.ports().get(0).alwaysReachablePeers());
        assertEquals(List.of(AtmosMachineProfile.PortName.INLET), resolved.ports().get(1).alwaysReachablePeers());
        assertEquals(.4, resolved.ports().stream().mapToDouble(AtmosMachineProfile.Port::volumeM3).sum(), 1e-12);
    }

    @Test void rejectsUnknownSelfAndDuplicatePeerNames() {
        assertThrows(IllegalArgumentException.class, () -> new AtmosMachineProfile.Port(AtmosMachineProfile.PortName.NORTH0,
                AtmosMachineProfile.PortDirection.NORTH, .05, Optional.empty(), List.of(AtmosMachineProfile.PortName.NORTH0)));
        for (String peers : List.of("[\"unknown\"]", "[\"outlet\",\"outlet\"]")) {
            var base = profile(null, "[{\"name\":\"inlet\",\"direction\":\"north\",\"alwaysReachablePeers\":" + peers + "},{\"name\":\"outlet\",\"direction\":\"south\"}]");
            var child = profile("synthetic_base", "[]");
            assertThrows(RuntimeException.class, () -> resolve(base, child));
        }
    }

    @Test void sixFiveHundredthsPortsTotalThreeTenths() {
        var portsJson = JsonParser.parseString("[{\"name\":\"north0\",\"direction\":\"north\",\"volumeM3\":0.05},{\"name\":\"north1\",\"direction\":\"north\",\"volumeM3\":0.05},{\"name\":\"north2\",\"direction\":\"north\",\"volumeM3\":0.05},{\"name\":\"south0\",\"direction\":\"south\",\"volumeM3\":0.05},{\"name\":\"south1\",\"direction\":\"south\",\"volumeM3\":0.05},{\"name\":\"south2\",\"direction\":\"south\",\"volumeM3\":0.05}]");
        // Codec-focused volume coverage: .05 is below the canonical machine-port volume;
        // do not pass this intentionally synthetic profile through the Mod validator.
        var ports = AtmosMachineProfile.Port.CODEC.listOf().parse(JsonOps.INSTANCE, portsJson).getOrThrow();
        assertEquals(6, ports.size());
        assertEquals(.3, ports.stream().mapToDouble(AtmosMachineProfile.Port::volumeM3).sum(), 1e-12);
    }

    @Test void bundledManifoldResolvesFullParentComposition() {
        var manager = new PrototypeManager();
        manager.register(ModAtmosMachines.TYPE);
        var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
        for (String id : List.of("atmos_machine_base", "atmos_machine_base_powered", "constructible_machine_base", "gas_pipe_sans_layers", "gas_pipe_base", "gas_trinary_base", "gas_pipe_manifold")) {
            String path = "/data/moonstation14/moonstation14/atmos_machine/" + id + ".json";
            try (var in = getClass().getResourceAsStream(path)) {
                assertNotNull(in, path);
                raw.put(ModAtmosMachines.id(id), JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (IOException e) { throw new RuntimeException(e); }
        }
        manager.reload(ModAtmosMachines.TYPE, raw);
        var profile = manager.snapshot(ModAtmosMachines.TYPE).get(ModAtmosMachines.id("gas_pipe_manifold"));
        assertEquals(AtmosMachineProfile.Kind.GAS_PIPE_MANIFOLD, profile.kind());
        assertEquals(6, profile.ports().size());
        assertEquals(.3, profile.ports().stream().mapToDouble(AtmosMachineProfile.Port::volumeM3).sum(), 1e-12);
        assertEquals(3, profile.pipeLayers().orElseThrow().count());
        assertTrue(profile.pipeLayers().orElseThrow().locked());
        assertEquals(6, profile.ports().stream().filter(p -> p.fixedLayer().isPresent()).count());
        assertEquals(18, profile.ports().stream().mapToInt(p -> p.alwaysReachablePeers().size()).sum());
        for (var port : profile.ports()) for (var peerName : port.alwaysReachablePeers()) {
            var peer = profile.ports().stream().filter(p -> p.name() == peerName).findFirst().orElseThrow();
            assertTrue(peer.alwaysReachablePeers().contains(port.name()));
            assertNotEquals(port.direction(), peer.direction());
        }
        assertNull(profile.power());
        assertNull(profile.pump());
        assertNull(profile.thermostat());
        assertNull(profile.mixer());
        assertTrue(profile.ports().stream().allMatch(p -> p.direction() == AtmosMachineProfile.PortDirection.NORTH || p.direction() == AtmosMachineProfile.PortDirection.SOUTH));
    }
}
