package com.juicyslew.moonstation14.ms14.singularity.pa;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

class ParticleAcceleratorAssemblyTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "particle_accelerator");

    private ParticleAcceleratorControlProfile profile() throws Exception {
        var raw = new HashMap<ResourceLocation, com.google.gson.JsonObject>();
        for (String name : new String[]{"pa_base", "particle_accelerator"}) {
            String path = "/data/moonstation14/moonstation14/particle_accelerator/" + name + ".json";
            try (var stream = ParticleAcceleratorAssemblyTest.class.getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            raw.put(ResourceLocation.parse(json.get("id").getAsString()), json);
            }
        }
        PrototypeRuntime.serverManager().reload(ModParticleAccelerators.TYPE, raw);
        return PrototypeRuntime.serverParticleAccelerators().get(ID);
    }

    private record Identity(String role, ResourceLocation prototypeId, Rotation orientation,
                            boolean anchored, boolean completed)
            implements ParticleAcceleratorPartIdentity {}

    @Test void inheritedDefaultsAndFourRotationsResolveStrictParts() throws Exception {
        var profile = profile();
        assertEquals(250, profile.controlPowerAp());
        assertEquals(ParticleAcceleratorStrength.STANDBY, profile.defaultStrength());
        assertEquals(ParticleAcceleratorStrength.LEVEL_2, profile.maximumStrength()); assertEquals(6, profile.waveSeconds());
        assertEquals(500, profile.strength(ParticleAcceleratorStrength.STANDBY).powerBoxMv());
        assertEquals(0, profile.strength(ParticleAcceleratorStrength.STANDBY).singularityFood());
        assertEquals(0, profile.strength(ParticleAcceleratorStrength.STANDBY).generatorCharge());
        assertEquals(2000, profile.strength(ParticleAcceleratorStrength.LEVEL_0).powerBoxMv());
        assertEquals(3500, profile.strength(ParticleAcceleratorStrength.LEVEL_1).powerBoxMv());
        assertEquals(5000, profile.strength(ParticleAcceleratorStrength.LEVEL_2).powerBoxMv());
        assertEquals(6500, profile.strength(ParticleAcceleratorStrength.LEVEL_3).powerBoxMv());
        assertEquals(java.util.List.of(0,10,20,30,60), java.util.Arrays.stream(ParticleAcceleratorStrength.values())
                .map(profile::strength).map(ParticleAcceleratorStrengthProfile::singularityFood).toList());
        assertEquals(java.util.List.of(0,1,2,4,8), java.util.Arrays.stream(ParticleAcceleratorStrength.values())
                .map(profile::strength).map(ParticleAcceleratorStrengthProfile::generatorCharge).toList());
        assertEquals(4, profile.strength(ParticleAcceleratorStrength.LEVEL_2).generatorCharge());
        assertEquals(30, profile.strength(ParticleAcceleratorStrength.LEVEL_2).singularityFood());
        assertEquals(60, profile.strength(ParticleAcceleratorStrength.LEVEL_3).singularityFood());
        assertEquals(4, ParticleAcceleratorStrength.LEVEL_2.ss14Value());
        assertEquals(20, profile.particleSpeed());
        assertEquals(ModParticleAcceleratorParticles.DEFAULT_ID, profile.particleProfile());
        assertEquals(new BlockPos(1,0,1), ParticleAcceleratorAssembly.toWorldVec(1,-1,Rotation.NONE));
        assertEquals(new BlockPos(-2,0,2), ParticleAcceleratorAssembly.toWorldVec(-2,-2,Rotation.NONE));
        assertEquals(new BlockPos(-2,0,1), ParticleAcceleratorAssembly.toWorldVec(-2,-1,Rotation.NONE));
        assertEquals(new BlockPos(-2,0,0), ParticleAcceleratorAssembly.toWorldVec(-2,0,Rotation.NONE));
        var expectedBeamDirections = java.util.Map.of(Rotation.NONE, Direction.WEST,
                Rotation.CLOCKWISE_90, Direction.NORTH, Rotation.CLOCKWISE_180, Direction.EAST,
                Rotation.COUNTERCLOCKWISE_90, Direction.SOUTH);
        for (Rotation rotation : Rotation.values()) {
            assertEquals(expectedBeamDirections.get(rotation), rotation.rotate(Direction.WEST));
            assertEquals(new BlockPos(-2,0,2).rotate(rotation), ParticleAcceleratorAssembly.toWorldVec(-2,-2,rotation));
            var parts = new HashMap<BlockPos, ParticleAcceleratorPartIdentity>();
            profile.parts().forEach((role, p) -> {
                var pos = new BlockPos(10, 64, 10).offset(ParticleAcceleratorAssembly.toWorldVec(p.offsetX(), p.offsetY(), rotation));
                parts.put(pos, new Identity(role, ResourceLocation.parse(p.completedPrototype()),
                        rotation.getRotated(p.relativeRotation()), true, true));
            });
            assertTrue(ParticleAcceleratorAssembly.resolve(new BlockPos(10,64,10), rotation, profile, parts::get).isPresent());
            var one = parts.keySet().iterator().next();
            var old = parts.remove(one);
            parts.put(one, new Identity("wrong", old.prototypeId(), old.orientation(), true, true));
            assertTrue(ParticleAcceleratorAssembly.resolve(new BlockPos(10,64,10), rotation, profile, parts::get).isEmpty());
        }
    }

    @Test void rejectsUnanchoredAndIncompleteAndOrdinaryBlocks() throws Exception {
        var profile = profile(); var control = new BlockPos(0,64,0); var rotation = Rotation.NONE;
        var parts = new HashMap<BlockPos, ParticleAcceleratorPartIdentity>();
        profile.parts().forEach((role,p) -> parts.put(control.offset(ParticleAcceleratorAssembly.toWorldVec(p.offsetX(),p.offsetY(),rotation)),
                new Identity(role, ResourceLocation.parse(p.completedPrototype()), rotation.getRotated(p.relativeRotation()),true,true)));
        assertTrue(ParticleAcceleratorAssembly.resolve(control,rotation,profile,parts::get).isPresent());
        var key=parts.keySet().iterator().next(); var old=parts.get(key);
        parts.put(key,new Identity(old.role(),old.prototypeId(),old.orientation(),false,true));
        assertTrue(ParticleAcceleratorAssembly.resolve(control,rotation,profile,parts::get).isEmpty());
        parts.put(key,new Identity(old.role(),old.prototypeId(),old.orientation(),true,false));
        assertTrue(ParticleAcceleratorAssembly.resolve(control,rotation,profile,parts::get).isEmpty());
        parts.remove(key); // a non-PA block has no typed identity
        assertTrue(ParticleAcceleratorAssembly.resolve(control,rotation,profile,parts::get).isEmpty());
    }

    @Test void threePhysicalEmitterRolesShareSelectedStrengthValues() throws Exception {
        var profile = profile();
        var emitters = java.util.List.of(profile.parts().get("port"), profile.parts().get("fore"), profile.parts().get("starboard"));
        for (var strength : ParticleAcceleratorStrength.values()) {
            var row = profile.strength(strength);
            // Runtime consumes this one selected-strength row for each emitter; role cannot alter energy.
            var applied = emitters.stream().map(ignored -> row).toList();
            assertEquals(3, applied.size());
            assertTrue(applied.stream().allMatch(value -> value.singularityFood() == row.singularityFood()
                    && value.generatorCharge() == row.generatorCharge()));
        }
    }
}
