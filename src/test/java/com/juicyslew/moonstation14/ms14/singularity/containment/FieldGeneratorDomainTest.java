package com.juicyslew.moonstation14.ms14.singularity.containment;

import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FieldGeneratorDomainTest {
    @Test void generatorIndicesConvertToSharedCenterGeometry() {
        for (int index : new int[]{-9, 0, 14}) assertEquals(index + .5, FieldGeometry.center(index));
        var bounds = new ClosedFieldVolume.Bounds(new BlockPos(0, 0, 0), new BlockPos(4, 4, 4));
        assertTrue(ClosedFieldVolume.containsWithinFaceDistance(bounds, new net.minecraft.world.phys.Vec3(2.5, 2.5, 2.5), 2));
        assertFalse(ClosedFieldVolume.containsWithinFaceDistance(bounds, new net.minecraft.world.phys.Vec3(2.5, 2.5, 2.5), 1.99));
    }
    private static PrototypeManager manager() throws Exception {
        var manager = new PrototypeManager(); manager.register(ModFieldGenerators.TYPE);
        var json = new LinkedHashMap<net.minecraft.resources.ResourceLocation, JsonObject>();
        for (String name : java.util.List.of("field_generator_base", "containment_field_generator")) {
            String path = "/data/moonstation14/moonstation14/field_generator/" + name + ".json";
            try (var stream = FieldGeneratorDomainTest.class.getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var value = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                json.put(net.minecraft.resources.ResourceLocation.parse(value.get("id").getAsString()), value);
            }
        }
        manager.reload(ModFieldGenerators.TYPE, json);
        return manager;
    }
    @Test void resolvedProfileDrivesChargeDecayAndConnectionTransitions() {
        FieldGeneratorProfile p;
        try { p=manager().snapshot(ModFieldGenerators.TYPE).get(ModFieldGenerators.CONTAINMENT_ID); }
        catch (Exception e) { throw new AssertionError(e); }
        assertEquals(new FieldGeneratorProfile(25,3,6,2,400,8),p);
        FieldGeneratorState a=FieldGeneratorState.empty().setAnchored(true,false).receiveValidBolt(p).receiveValidBolt(p);
        FieldGeneratorState b=FieldGeneratorState.empty().setAnchored(true,false).receiveValidBolt(p);
        assertTrue(FieldGeneratorState.connectionValid(a,b,p));
        assertEquals(4,a.tick(p,400).charge(), "two valid bolts add 3 charge each (6 total), then one 400-tick decay removes 2");
        assertEquals(0,a.receiveValidBolt(p).decayElapsedTicks());
        assertFalse(FieldGeneratorState.connectionValid(a.tick(p,800),b.tick(p,800),p));
    }
    @Test void upstreamActivationDoesNotRequireConnectionAndConnectedGeneratorCannotTurnOff() throws Exception {
        FieldGeneratorState anchored=FieldGeneratorState.empty().setAnchored(true,false);
        FieldGeneratorState on=anchored.setEnabled(true,false);
        assertTrue(on.enabled());
        assertEquals(on,on.setEnabled(false,true));
        assertTrue(on.setAnchored(false,false).anchored());
        FieldGeneratorState activated=anchored.setEnabled(true,false);
        assertTrue(activated.setAnchored(false,false).enabled());
        assertTrue(activated.setAnchored(false,false).anchored());
        assertEquals(FieldGeneratorState.empty(),FieldGeneratorState.empty().tick(
                manager().snapshot(ModFieldGenerators.TYPE).get(ModFieldGenerators.CONTAINMENT_ID),200));
    }
    @Test void provesAllTwelveLoadedBoxEdgesRatherThanGraphOnly() {
        Set<BlockPos> corners=new HashSet<>();
        for(int x:new int[]{0,4}) for(int y:new int[]{0,4}) for(int z:new int[]{0,4}) corners.add(new BlockPos(x,y,z));
        Set<ClosedFieldVolume.Edge> edges=new HashSet<>();
        for(BlockPos a:corners) for(BlockPos b:corners) if((a.getX()!=b.getX()?1:0)+(a.getY()!=b.getY()?1:0)+(a.getZ()!=b.getZ()?1:0)==1) edges.add(new ClosedFieldVolume.Edge(a,b));
        assertEquals(12,edges.size());
        assertTrue(ClosedFieldVolume.derive(corners,edges,p->true).isPresent());
        edges.remove(edges.iterator().next());
        assertTrue(ClosedFieldVolume.derive(corners,edges,p->true).isEmpty());
        assertTrue(ClosedFieldVolume.derive(corners,edges,p->false).isEmpty());
    }
}
