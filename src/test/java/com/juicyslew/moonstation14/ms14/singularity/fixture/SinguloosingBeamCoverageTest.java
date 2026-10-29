package com.juicyslew.moonstation14.ms14.singularity.fixture;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeType;
import com.juicyslew.moonstation14.ms14.singularity.ModSingularityGameplay;
import com.juicyslew.moonstation14.ms14.singularity.containment.FieldGeometry;
import com.juicyslew.moonstation14.ms14.singularity.pa.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SinguloosingBeamCoverageTest {
    private static final ResourceLocation SINGULARITY=ResourceLocation.fromNamespaceAndPath("moonstation14","singularity");
    private static final ResourceLocation PARTICLE=ResourceLocation.fromNamespaceAndPath("moonstation14","particle_accelerator_particle");
    private static final ResourceLocation PA=ResourceLocation.fromNamespaceAndPath("moonstation14","particle_accelerator");

    @Test void fieldBoundsTranslateOnceAtPositiveAndNegativeOrigins() {
        for (var origin : List.of(new BlockPos(120, 64, 900), new BlockPos(-10816881, -60, -10408371))) {
            var bounds=SingularityFixtureLayout.fieldBounds(origin,SingularityFixtureLayout.Enclosure.SINGULOOSING);
            assertEquals(origin.offset(5,4,8),bounds.min());
            assertEquals(origin.offset(9,6,11),bounds.max());
            for (var corner : List.of(bounds.min(), bounds.max())) {
                var emitter=new BlockPos(origin.getX()+1,corner.getY(),corner.getZ());
                assertEquals(origin.getX()+1,emitter.getX());
                assertEquals(corner.getY(),emitter.getY());
                assertEquals(corner.getZ(),emitter.getZ());
            }
        }
    }

    @Test void resolvedL1SphereFitsActualSinguloosingBeamCrossSection() throws Exception {
        var gameplay=load(ModSingularityGameplay.TYPE,Map.of("singularity.json",SINGULARITY)).get(SINGULARITY);
        var particles=load(ModParticleAcceleratorParticles.TYPE,Map.of("base_bullet.json",ResourceLocation.fromNamespaceAndPath("moonstation14","base_bullet"),
                "particle_accelerator_particle.json",PARTICLE));
        var particle=particles.get(PARTICLE);
        assertNotNull(particle);
        double reach=gameplay.eventHorizonRadiusAtLevel(1)+particle.halfExtent();

        var profiles=load(ModParticleAccelerators.TYPE,Map.of("pa_base.json",ResourceLocation.fromNamespaceAndPath("moonstation14","pa_base"),
                "particle_accelerator.json",PA));
        var pa=profiles.get(PA);
        var origin=BlockPos.ZERO;
        var bounds=SingularityFixtureLayout.fieldBounds(origin,SingularityFixtureLayout.Enclosure.SINGULOOSING);
        var control=origin.offset(19,5,8);
        var resolved=ParticleAcceleratorAssembly.resolve(control,Rotation.NONE,pa,pos->{
            for(var entry:pa.parts().entrySet()) {
                var role=entry.getKey(); var part=entry.getValue();
                var partPos=control.offset(ParticleAcceleratorAssembly.toWorldVec(part.offsetX(),part.offsetY(),Rotation.NONE));
                if(partPos.equals(pos))return new TestPart(role,ResourceLocation.parse(part.completedPrototype()),part.relativeRotation(),true,true);
            }
            return null;
        }).orElseThrow();
        var beams=new ArrayList<net.minecraft.world.phys.Vec3>();
        for(var part:resolved.parts()) if(List.of("port","fore","starboard").contains(part.profile().role())) {
            var direction=part.rotation().rotate(Direction.SOUTH);
            beams.add(ParticleAcceleratorPartBlockEntity.emissionOrigin(part.position(),direction));
            assertEquals(Direction.WEST,direction,"actual roles must fire along X through the enclosure");
        }
        assertEquals(3,beams.size());
        assertTrue(beams.stream().allMatch(b->b.x>bounds.max().getX()+.5),"three real source origins lie beyond the cage and fire westward through its X interval");

        double radius=gameplay.eventHorizonRadiusAtLevel(1);
        double planeMinY=FieldGeometry.center(bounds.min().getY()),planeMaxY=FieldGeometry.center(bounds.max().getY());
        double planeMinZ=FieldGeometry.center(bounds.min().getZ()),planeMaxZ=FieldGeometry.center(bounds.max().getZ());
        // Only centers at least one event-horizon radius inside each plane are accessible.
        double minY=planeMinY+radius,maxY=planeMaxY-radius;
        double minZ=planeMinZ+radius,maxZ=planeMaxZ-radius;
        double worst=0;
        // The nearest-lane distance is piecewise convex; check rectangle corners and
        // Voronoi boundaries between the actual emission-origin projections.
        var ys=criticalCoordinates(beams.stream().mapToDouble(net.minecraft.world.phys.Vec3::y).distinct().sorted().toArray(),minY,maxY);
        var zs=criticalCoordinates(beams.stream().mapToDouble(net.minecraft.world.phys.Vec3::z).distinct().sorted().toArray(),minZ,maxZ);
        for(double y:ys)for(double z:zs) {
            double nearest=beams.stream().mapToDouble(b->Math.hypot(y-b.y,z-b.z)).min().orElseThrow();
            worst=Math.max(worst,nearest);
        }
        assertTrue(worst<reach,"full accessible field cross-section worst distance="+worst+" reach="+reach);
        var startup=origin.offset(7,5,9);
        var startupCenter=net.minecraft.world.phys.Vec3.atCenterOf(startup);
        assertTrue(startupCenter.y>minY&&startupCenter.y<maxY&&startupCenter.z>minZ&&startupCenter.z<maxZ,
                "startup/generator center remains inside actual field bounds");
        assertTrue(startup.getY()!=bounds.min().getY()&&startup.getY()!=bounds.max().getY(),"startup block is not on a field plane");
        // Historical field block Y=5..7 had actual planes at center coordinates 5.5..7.5.
        double historicalMinY=FieldGeometry.center(5)+radius,historicalMaxY=FieldGeometry.center(7)-radius;
        double historicalMinZ=FieldGeometry.center(8)+radius,historicalMaxZ=FieldGeometry.center(11)-radius;
        double historicalWorst=0;
        var historicalYs=criticalCoordinates(beams.stream().mapToDouble(net.minecraft.world.phys.Vec3::y).distinct().sorted().toArray(),historicalMinY,historicalMaxY);
        var historicalZs=criticalCoordinates(beams.stream().mapToDouble(net.minecraft.world.phys.Vec3::z).distinct().sorted().toArray(),historicalMinZ,historicalMaxZ);
        for(double y:historicalYs)for(double z:historicalZs)
            historicalWorst=Math.max(historicalWorst,beams.stream().mapToDouble(b->Math.hypot(y-b.y,z-b.z)).min().orElseThrow());
        assertTrue(historicalWorst>reach,"historical bounds fail actual beam coverage");
        assertEquals(5.0,minY,"accessible Y lower bound is horizon-inset");
        assertEquals(6.0,maxY,"accessible Y upper bound is horizon-inset");
        assertEquals(9.0,minZ,"accessible Z lower bound is horizon-inset");
        assertEquals(11.0,maxZ,"accessible Z upper bound is horizon-inset");
    }

    @Test void threeResolvedPaLanesCaptureAtLeastOneParticleAcrossCompactTeslaInterior() throws Exception {
        var pa=load(ModParticleAccelerators.TYPE,Map.of("pa_base.json",ResourceLocation.fromNamespaceAndPath("moonstation14","pa_base"),
                "particle_accelerator.json",PA)).get(PA);
        var control=BlockPos.ZERO.offset(19,5,8);
        var assembly=ParticleAcceleratorAssembly.resolve(control,Rotation.NONE,pa,pos->{
            for(var e:pa.parts().entrySet()) {
                var p=e.getValue();var world=control.offset(ParticleAcceleratorAssembly.toWorldVec(p.offsetX(),p.offsetY(),Rotation.NONE));
                if(world.equals(pos))return new TestPart(e.getKey(),ResourceLocation.parse(p.completedPrototype()),p.relativeRotation(),true,true);
            } return null;
        }).orElseThrow();
        var lanes=assembly.parts().stream().filter(p->List.of("port","fore","starboard").contains(p.profile().role()))
                .map(p->ParticleAcceleratorPartBlockEntity.emissionOrigin(p.position(),p.rotation().rotate(Direction.SOUTH))).toList();
        assertEquals(3,lanes.size());
        var gameplay=load(ModSingularityGameplay.TYPE,Map.of("singularity.json",SINGULARITY)).get(SINGULARITY);
        var particle=load(ModParticleAcceleratorParticles.TYPE,Map.of("base_bullet.json",ResourceLocation.fromNamespaceAndPath("moonstation14","base_bullet"),
                "particle_accelerator_particle.json",PARTICLE)).get(PARTICLE);
        var balls=load(com.juicyslew.moonstation14.ms14.tesla.ModTeslaProfiles.BALL_TYPE,Map.of(
                "base.json",com.juicyslew.moonstation14.ms14.tesla.ModTeslaProfiles.id("base"),
                "tesla_energy_ball.json",com.juicyslew.moonstation14.ms14.tesla.ModTeslaProfiles.MAIN_ID));
        var tesla=balls.get(com.juicyslew.moonstation14.ms14.tesla.ModTeslaProfiles.MAIN_ID);
        assertNotNull(tesla);
        double radius=tesla.contactRadius()+particle.halfExtent();
        // Production compact cage is x=5..9, y=3..7, z=7..11; containment
        // keeps the collider-radius center at least .55 blocks inside each plane.
        var bounds=SingularityFixtureLayout.fieldBounds(BlockPos.ZERO,SingularityFixtureLayout.Enclosure.COMPACT);
        double collider=tesla.colliderRadius();
        double minX=FieldGeometry.center(bounds.min().getX())+collider,maxX=FieldGeometry.center(bounds.max().getX())-collider;
        double minY=FieldGeometry.center(bounds.min().getY())+collider,maxY=FieldGeometry.center(bounds.max().getY())-collider;
        double minZ=FieldGeometry.center(bounds.min().getZ())+collider,maxZ=FieldGeometry.center(bounds.max().getZ())-collider;
        for(double x:sampleCoordinates(minX,maxX))
            for(double y:sampleCoordinates(minY,maxY))
                for(double z:sampleCoordinates(minZ,maxZ)) {
                    Vec3 center=new Vec3(x,y,z);
                    boolean captured=assembly.parts().stream().filter(p->List.of("port","fore","starboard").contains(p.profile().role()))
                            .anyMatch(p->{var direction=p.rotation().rotate(Direction.SOUTH);var from=ParticleAcceleratorPartBlockEntity.emissionOrigin(p.position(),direction);
                                double distance=pa.particleSpeed()*particle.lifetimeTicks()/20.0;
                                var to=from.add(direction.getStepX()*distance,direction.getStepY()*distance,direction.getStepZ()*distance);
                                return ParticleAcceleratorReceiverContactGeometry.entry(from,to,center,radius,particle.energyReceiverVerticalPadding())>=0;});
                    assertTrue(captured,"actual PA lanes miss legal compact cage center x="+x+" y="+y+" z="+z);
                }
    }

    private record TestPart(String role,ResourceLocation prototypeId,Rotation orientation,boolean anchored,boolean completed) implements ParticleAcceleratorPartIdentity { }

    private static double[] criticalCoordinates(double[] lanes,double min,double max) {
        var values=new TreeSet<Double>(); values.add(min);values.add(max);
        for(int i=1;i<lanes.length;i++){double midpoint=(lanes[i-1]+lanes[i])/2;if(midpoint>=min&&midpoint<=max)values.add(midpoint);}
        for(double lane:lanes)if(lane>=min&&lane<=max)values.add(lane);
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }
    private static double[] sampleCoordinates(double min,double max) {
        var values=new ArrayList<Double>();for(double v=min;v<max;v+=.25)values.add(v);values.add(max);
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }
    private static <T> com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<T> load(PrototypeType<T> type,Map<String,ResourceLocation> files)throws Exception {
        var manager=new PrototypeManager();manager.register(type);var raw=new LinkedHashMap<ResourceLocation,JsonObject>();
        for(var entry:files.entrySet()) {
            String path="/data/moonstation14/"+type.resourceDirectory()+"/"+entry.getKey();
            try(var stream=SinguloosingBeamCoverageTest.class.getResourceAsStream(path)) {
                assertNotNull(stream,path);var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
                raw.put(entry.getValue(),json);
            }
        }
        manager.reload(type,raw);return manager.snapshot(type);
    }
}
