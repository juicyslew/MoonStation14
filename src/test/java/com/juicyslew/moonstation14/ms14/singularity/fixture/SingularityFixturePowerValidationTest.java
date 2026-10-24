package com.juicyslew.moonstation14.ms14.singularity.fixture;

import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class SingularityFixturePowerValidationTest {
    @Test void gravityEnergySelectionUsesResolvedStageThresholdsAndInheritedOverrides() throws Exception {
        var manager=new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();
        manager.register(com.juicyslew.moonstation14.ms14.singularity.ModSingularityGameplay.TYPE);
        var baseId=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","gravity_fixture_base");
        var childId=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","gravity_fixture_child");
        var raw=new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation,com.google.gson.JsonObject>();
        try(var in=getClass().getResourceAsStream("/data/moonstation14/singularity_gameplay/singularity.json")) {
            var parent=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var child=new com.google.gson.JsonObject(); child.addProperty("parent",baseId.toString());
            child.add("levelThresholds",com.google.gson.JsonParser.parseString("[0,0,220,540,1100,2200,5500]"));
            child.add("drainPerSecond",com.google.gson.JsonParser.parseString("[0,1,6,11,21,0,0]"));
            raw.put(baseId,parent); raw.put(childId,child);
        }
        manager.reload(com.juicyslew.moonstation14.ms14.singularity.ModSingularityGameplay.TYPE,raw);
        var resolved=manager.snapshot(com.juicyslew.moonstation14.ms14.singularity.ModSingularityGameplay.TYPE).get(childId);
        assertNotNull(resolved); assertEquals(10,resolved.gravityAcceleration(),"unmodified parent gameplay fields are inherited");
        for(int stage=1;stage<=6;stage++) {
            double energy=SingularityFixtureService.gravityFixtureEnergy(resolved,stage);
            assertEquals(stage,resolved.levelForEnergy(energy),"chosen reserve resolves selected stage");
            if(stage<6)assertTrue(energy<resolved.levelThresholds().get(stage+1),"reserve stays below next stage");
            if(stage<6)
                assertEquals(resolved.levelThresholds().get(stage+1)-Math.max(1,resolved.drainPerSecond().get(stage)*30),energy,1e-9,
                        "fixture starts about thirty seconds of resolved normal drain below the next threshold");
        }
        assertEquals(360,SingularityFixtureService.gravityFixtureEnergy(resolved,2));
        assertEquals(5501,SingularityFixtureService.gravityFixtureEnergy(resolved,6));
        assertThrows(IllegalArgumentException.class,()->SingularityFixtureService.gravityFixtureEnergy(resolved,0));
    }
    @Test void reportsUnmatchedActualPortAndAcceptsAdjacentNode() {
        DevicePort port = port(0);
        CableFaceNode adjacent = new CableFaceNode(new BlockPos(0, 0, -1), Direction.SOUTH, CableTier.HV);
        assertTrue(SingularityFixturePower.validatePlannedComponents(List.of(adjacent), List.of(port)));
        String diagnostic = SingularityFixturePower.plannedComponentDiagnostic(List.of(), List.of(port));
        assertTrue(diagnostic.contains("unmatched port"));
        assertTrue(diagnostic.contains("HV"));
    }

    @Test void reportsRequiredGroupWithDisconnectedComponentIds() {
        DevicePort first = port(0), second = port(5);
        CableFaceNode a = new CableFaceNode(new BlockPos(0, 0, -1), Direction.SOUTH, CableTier.HV);
        CableFaceNode b = new CableFaceNode(new BlockPos(5, 0, -1), Direction.SOUTH, CableTier.HV);
        String diagnostic = SingularityFixturePower.plannedComponentDiagnostic(List.of(a, b), List.of(first, second), List.of(first, second));
        assertTrue(diagnostic.contains("required group 0 split"));
        assertTrue(diagnostic.contains("component0"));
        assertTrue(diagnostic.contains("component1"));
        assertFalse(SingularityFixturePower.validatePlannedComponents(List.of(a, b), List.of(first, second), List.of(first, second)));
    }

    private static DevicePort port(int x) { return new DevicePort(new BlockPos(x, 0, 0), Direction.NORTH, CableTier.HV); }
}
