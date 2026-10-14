package com.juicyslew.moonstation14.ms14.singularity.fixture;

import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Direction;
import com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity;
import com.juicyslew.moonstation14.block.block_entity.SingularityGeneratorBlockEntity;
import com.juicyslew.moonstation14.block.block_entity.EmitterBlockEntity;
import com.juicyslew.moonstation14.block.block_entity.ParticleAcceleratorPartBlockEntity;
import com.juicyslew.moonstation14.block.custom.EmitterBlock;
import com.juicyslew.moonstation14.ms14.radiation.RadiationCollectorBlockEntity;
import com.juicyslew.moonstation14.ms14.singularity.pa.ParticleAcceleratorStrength;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.item.custom.GasTankItem;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness;
import com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime;
import com.juicyslew.moonstation14.ms14.singularity.containment.ClosedFieldVolume;
import com.juicyslew.moonstation14.ms14.singularity.containment.ModFieldGenerators;
import com.juicyslew.moonstation14.ms14.singularity.ModSingularityGenerators;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.logging.Logger;
import java.util.UUID;
import java.util.HashSet;
import java.util.function.Consumer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import com.juicyslew.moonstation14.entities.EmitterBoltEntity;
import com.juicyslew.moonstation14.entities.ProjectileLoadedSweep;
import com.juicyslew.moonstation14.entities.ParticleAcceleratorParticleEntity;
import com.juicyslew.moonstation14.entities.SingularityEntity;
import com.juicyslew.moonstation14.entities.ModEntities;

@GameTestTags("singularity-fixture")
@PrefixGameTestTemplate(false)
public final class SingularityFixtureLayoutGameTests {
    private static final Logger LOGGER=Logger.getLogger(SingularityFixtureLayoutGameTests.class.getName());
    private SingularityFixtureLayoutGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="singularity_fixture_clear", batch="singularity_fixture_layout", timeoutTicks=100)
    public static void buildsOnClearTemplate(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(
                origin.getX()-1,origin.getY(),origin.getZ()-1,origin.getX()+28,origin.getY()+12,origin.getZ()+20));
        var owned=new java.util.ArrayList<BlockPos>();
        cleanupOnEnd(h,level,owned);
        FixtureChunkTickets.awaitEntityTicking(h,lease,80,()->{
         var result=SingularityFixtureService.build(level,origin);
         if(!result.successful()) throw new GameTestAssertException("clear fixture refused: "+result.error());
         var record=result.record(); var layout=record.placed();
         owned.addAll(layout.occupiedPositions()); owned.addAll(record.bootstrap().devices()); record.bootstrap().nodes().forEach(n->owned.add(n.host()));
         owned.addAll(record.export().devices()); record.export().nodes().forEach(n->owned.add(n.host())); owned.addAll(record.kit().accessBlocks());
        check(layout.fieldGenerators().size()==8&&layout.emitters().size()==8,"expected eight field generators and emitters");
         check(layout.fieldGenerators().contains(origin.offset(5,3,7))&&layout.fieldGenerators().contains(origin.offset(9,7,11)),"default field corners retain canonical centered compact geometry");
        check(layout.startupGenerator().equals(origin.offset(7,5,9))&&layout.acceleratorControl().equals(origin.offset(19,5,8)),"generator and PA control retain canonical positions");
        for(BlockPos p:layout.fieldGenerators()) {
            check(level.getBlockEntity(p) instanceof FieldGeneratorBlockEntity field
                    &&field.snapshot().charge()==0&&!field.snapshot().enabled(),"field generator starts empty and disabled at "+p);
        }
        for(BlockPos p:layout.emitters()) {
            Direction facing=level.getBlockState(p).getValue(EmitterBlock.FACING);
            check(facing==(p.getX()<origin.getX()+7?Direction.EAST:Direction.WEST),"emitter faces inward at "+p);
            check(level.getBlockEntity(p) instanceof EmitterBlockEntity emitter&&!emitter.requestedOn(),"emitter starts requested off at "+p);
        }
        check(level.getBlockEntity(layout.startupGenerator()) instanceof SingularityGeneratorBlockEntity generator
                &&generator.startupCharge()==0&&!generator.startupInert(),"startup generator has zero charge and is not inert");
         check(level.getBlockEntity(layout.acceleratorControl()) instanceof ParticleAcceleratorPartBlockEntity control,
                "accelerator control exists");
        var control=(ParticleAcceleratorPartBlockEntity)level.getBlockEntity(layout.acceleratorControl());
        control.requestAssemblyRescan();
        check(control.assemblyValid()&&control.assemblySnapshot().orElseThrow().parts().size()==6,"six derived accelerator parts resolve; control is separate hardware");
         check(control.strength()==ParticleAcceleratorStrength.STANDBY&&!control.enabled(),"accelerator begins in standby");
         assertEmitterRays(level,origin,layout,record.bootstrap(),record.export());
        for(BlockPos p:layout.collectors()) {
            check(level.getBlockEntity(p) instanceof RadiationCollectorBlockEntity collector
                &&collector.controlStateReady()&&!collector.enabled()&&collector.tankSlot().snapshot().stack().is(ModItems.PLASMA_TANK.get()),
                "collector is initialized with its resolved full tank and disabled at "+p);
            var tank=((RadiationCollectorBlockEntity)level.getBlockEntity(p)).tankSlot().snapshot().stack();
            check(GasTankItem.contents(tank,level).orElseThrow().mixture().moles(GasType.PLASMA)>0,"collector tank contains finite plasma");
        }
          record.kit().provisionedEntities().forEach(id->{var entity=level.getEntity(id);if(entity!=null)entity.discard();});
          SingularityFixtureService.forget(level,origin); h.succeed();
        },origin,origin.offset(26,11,18));
    }

    @GameTestTags({"singularity-containment", "reactor-containment"})
    @GameTest(templateNamespace="moonstation14", template="singularity_fixture_clear", batch="singularity_fixture_containment", timeoutTicks=140)
    public static void explicitWideFixtureContainsStageThreeFourAndAllowsStageFiveEscape(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(
                origin.getX()-1,origin.getY(),origin.getZ()-1,origin.getX()+28,origin.getY()+12,origin.getZ()+20));
        var owned=new java.util.LinkedHashSet<BlockPos>();
        var provisioned=new java.util.ArrayList<UUID>();
        var singularities=new java.util.ArrayList<SingularityEntity>();
        h.testInfo.addListener(new GameTestListener() {
            private void clean() {
                singularities.forEach(SingularityEntity::discard);
                provisioned.forEach(id->{var entity=level.getEntity(id);if(entity!=null)entity.discard();});
                owned.forEach(pos->level.setBlock(pos,Blocks.AIR.defaultBlockState(),3));
                SingularityFixtureService.forget(level,origin);
            }
            public void testStructureLoaded(GameTestInfo i){}
            public void testPassed(GameTestInfo i,GameTestRunner r){clean();}
            public void testFailed(GameTestInfo i,GameTestRunner r){clean();}
            public void testAddedForRerun(GameTestInfo a,GameTestInfo b,GameTestRunner r){clean();}
        });
        FixtureChunkTickets.awaitEntityTicking(h,lease,80,()->{
             var built=SingularityFixtureService.buildWideInactive(level,origin);
            if(!built.successful())throw new GameTestAssertException("canonical fixture refused: "+built.error());
            var record=built.record(); var layout=record.placed();
            owned.addAll(layout.occupiedPositions()); owned.addAll(record.bootstrap().devices()); owned.addAll(record.export().devices());
            record.bootstrap().nodes().forEach(n->owned.add(n.host())); record.export().nodes().forEach(n->owned.add(n.host()));
            owned.addAll(record.kit().accessBlocks()); provisioned.addAll(record.kit().provisionedEntities());
             check(layout.fieldGenerators().contains(origin.offset(3,1,5))&&layout.fieldGenerators().contains(origin.offset(11,9,13)),
                     "explicit wide plan uses canonical centered eight-block field cube");
            Set<BlockPos> corners=Set.copyOf(layout.fieldGenerators());
            Set<BlockPos> cableHosts=new HashSet<>();
            record.bootstrap().nodes().forEach(n->cableHosts.add(n.host())); record.export().nodes().forEach(n->cableHosts.add(n.host()));
            assertPlaneClear(level,origin,Axis.X,3,2,8,6,12,cableHosts);
            assertPlaneClear(level,origin,Axis.X,11,2,8,6,12,cableHosts);
            assertPlaneClear(level,origin,Axis.Y,1,4,10,6,12,cableHosts);
            assertPlaneClear(level,origin,Axis.Y,9,4,10,6,12,cableHosts);
            assertPlaneClear(level,origin,Axis.Z,5,4,10,2,8,cableHosts);
            assertPlaneClear(level,origin,Axis.Z,13,4,10,2,8,cableHosts);
            check(layout.emitters().size()==8,"one external emitter per canonical corner lane");
            for(BlockPos emitterPos:layout.emitters()) {
                var emitter=(EmitterBlockEntity)level.getBlockEntity(emitterPos);
                Direction direction=level.getBlockState(emitterPos).getValue(EmitterBlock.FACING);
                check(emitter.powerRuntimePorts().stream().allMatch(port->record.bootstrap().nodes().stream()
                                .anyMatch(node->PowerTopology.deviceAdjacent(port,node))),
                        "repositioned emitter retains a connected resolved power port at "+emitterPos);
                BlockPos target=layout.fieldGenerators().stream()
                        .filter(p->p.getY()==emitterPos.getY()&&p.getZ()==emitterPos.getZ())
                        .filter(p->direction==Direction.EAST?p.getX()>emitterPos.getX():p.getX()<emitterPos.getX())
                        .min(java.util.Comparator.comparingInt(p->Math.abs(p.getX()-emitterPos.getX()))).orElseThrow();
                var profile=emitter.resolvedProfile();
                check(profile!=null,"emitter resolves its projectile profile");
                Vec3 start=Vec3.atCenterOf(emitterPos).add(0,.25,0)
                        .add(direction.getStepX()*.8,direction.getStepY()*.8,direction.getStepZ()*.8);
                var bolt=new EmitterBoltEntity(level,emitter,profile);
                Vec3 end=new Vec3(target.getX()+.5,start.y,target.getZ()+.5);
                var ray=ProjectileLoadedSweep.clip(level,bolt,start,end,profile.boltHalfExtent());
                check(ray instanceof net.minecraft.world.phys.BlockHitResult hit&&hit.getBlockPos().equals(target),
                        "actual resolved emitter ray reaches its matching registered field-generator collider: "+emitterPos+" -> "+target+" hit="+ray);
                for(int x=Math.min(emitterPos.getX(),target.getX())+1;x<Math.max(emitterPos.getX(),target.getX());x++)
                    check(!cableHosts.contains(origin.offset(x,emitterPos.getY(),emitterPos.getZ())),
                            "power cable support does not occlude emitter-to-field ray");
                BlockPos rearWall=new BlockPos(origin.getX()+(direction==Direction.EAST?0:14),emitterPos.getY(),emitterPos.getZ());
                check(level.getBlockState(rearWall).is(Blocks.GLASS),"transparent beam-permitting rear wall remains behind emitter; emitter="
                        +emitterPos+" facing="+direction+" wall="+rearWall+" state="+level.getBlockState(rearWall));
            }
            check(record.bootstrap().topologyVerified()&&record.export().topologyVerified(),"normal power routes remain connected");
            check(layout.collectors().stream().allMatch(p->level.getBlockEntity(p) instanceof RadiationCollectorBlockEntity c
                            &&GasTankItem.contents(c.tankSlot().snapshot().stack(),level).orElseThrow().mixture().moles(GasType.PLASMA)>0),
                    "finite resolved collector fuel remains provisioned");
            for(BlockPos corner:layout.fieldGenerators()) {
                var field=(FieldGeneratorBlockEntity)level.getBlockEntity(corner);
                field.receiveEmitterBolt(); field.receiveEmitterBolt();
                check(field.toggleEnabled(),"charged corner field generator enables");
            }
            FieldContainmentRuntime.refresh(level);
            check(FieldContainmentRuntime.currentFaces(level).stream().filter(f->corners.containsAll(f.corners())).count()==6,
                    "canonical loaded plan derives all six clear field faces");
            check(FieldContainmentRuntime.closedVolumes(level).stream().anyMatch(v->v.min().equals(origin.offset(3,1,5))
                            &&v.max().equals(origin.offset(11,9,13))),"registered corner placements derive the intended closed box");

            SingularityEntity singularity=ModEntities.SINGULARITY.get().create(level);
            check(singularity!=null,"registered singularity created"); singularities.add(singularity);
            singularity.setPos(Vec3.atCenterOf(layout.startupGenerator()));
            check(level.addFreshEntity(singularity),"singularity admitted at preserved startup center");
            singularity.tick();
            singularity.feed(500);
            check(singularity.singularityLevel()==3&&singularity.gravityProfile().eventHorizonRadiusAtLevel(3)==2.5,
                    "actual resolved stage-3 contact radius is used");
            singularity.setDeltaMovement(2,0,0); Vec3 stage3Start=singularity.position();
            h.runAfterDelay(4,()->{
                check(singularity.position().x>stage3Start.x,"stage 3 naturally moves within the registered six-face box");
                check(insideRadiusBox(singularity.position(),origin,2.5),"stage-3 growing-radius sphere stays within field planes");
                singularity.feed(500);
                check(singularity.singularityLevel()==4&&singularity.gravityProfile().eventHorizonRadiusAtLevel(4)==3.5,
                        "actual resolved stage-4 contact radius is used");
                singularity.setDeltaMovement(-2,0,0); Vec3 stage4Start=singularity.position();
                h.runAfterDelay(4,()->{
                    check(singularity.position().x<stage4Start.x,"stage 4 naturally moves within the registered six-face box");
                    check(insideRadiusBox(singularity.position(),origin,3.5),"stage-4 growing-radius sphere stays within field planes");
                    singularity.feed(1000);
                    check(singularity.singularityLevel()==5,"stage 5 resolves through the live energy profile");
                    singularity.setDeltaMovement(20,0,0);
                    h.runAfterDelay(10,()->{
                        check(singularity.position().x>origin.getX()+11.5,"stage 5 naturally escapes beyond the canonical east field plane");
                        h.succeed();
                    });
                });
            });
        },origin,origin.offset(26,11,18));
    }

    private enum Axis { X, Y, Z }
    private static void assertPlaneClear(ServerLevel level,BlockPos origin,Axis axis,int plane,int a0,int a1,int b0,int b1,Set<BlockPos> cables) {
        for(int a=a0;a<=a1;a++) for(int b=b0;b<=b1;b++) {
            BlockPos p=switch(axis) { case X -> origin.offset(plane,a,b); case Y -> origin.offset(a,plane,b); case Z -> origin.offset(a,b,plane); };
            check(level.getBlockState(p).getCollisionShape(level,p).isEmpty()&&!cables.contains(p),
                    "operator deck/power support leaves field-face interior clear at "+p);
        }
    }
    private static void assertEmitterRays(ServerLevel level,BlockPos origin,SingularityFixtureLayout.Placed layout,SingularityFixturePower.Circuit... circuits) {
        Set<BlockPos> cables=new HashSet<>(); for(var circuit:circuits)circuit.nodes().forEach(n->cables.add(n.host()));
        for(BlockPos emitterPos:layout.emitters()) {
            var emitter=(EmitterBlockEntity)level.getBlockEntity(emitterPos);
            Direction direction=level.getBlockState(emitterPos).getValue(EmitterBlock.FACING);
            BlockPos target=layout.fieldGenerators().stream().filter(p->p.getY()==emitterPos.getY()&&p.getZ()==emitterPos.getZ())
                    .filter(p->direction==Direction.EAST?p.getX()>emitterPos.getX():p.getX()<emitterPos.getX())
                    .min(java.util.Comparator.comparingInt(p->Math.abs(p.getX()-emitterPos.getX()))).orElseThrow();
            check(emitter.powerRuntimePorts().stream().allMatch(port->java.util.Arrays.stream(circuits).flatMap(c->c.nodes().stream()).anyMatch(node->PowerTopology.deviceAdjacent(port,node))),
                    "emitter support connects to normal power route at "+emitterPos);
            var profile=emitter.resolvedProfile(); check(profile!=null,"emitter resolves profile at "+emitterPos);
            Vec3 start=Vec3.atCenterOf(emitterPos).add(0,.25,0).add(direction.getStepX()*.8,direction.getStepY()*.8,direction.getStepZ()*.8);
            Vec3 end=new Vec3(target.getX()+.5,start.y,target.getZ()+.5);
            var ray=ProjectileLoadedSweep.clip(level,new EmitterBoltEntity(level,emitter,profile),start,end,profile.boltHalfExtent());
            check(ray instanceof net.minecraft.world.phys.BlockHitResult hit&&hit.getBlockPos().equals(target),
                    "full built-plan bolt sweep must hit matching generator; closest BlockHit before target: emitter="
                            +emitterPos+" target="+target+" hit="+ray);
            for(int x=Math.min(emitterPos.getX(),target.getX())+1;x<Math.max(emitterPos.getX(),target.getX());x++)
                check(!cables.contains(origin.offset(x,emitterPos.getY(),emitterPos.getZ())),"cable does not block emitter beam lane");
        }
    }
    private static boolean insideRadiusBox(Vec3 p,BlockPos origin,double radius) {
        return p.x-(origin.getX()+3.5)>radius && origin.getX()+11.5-p.x>radius
                &&p.y-(origin.getY()+1.5)>radius && origin.getY()+9.5-p.y>radius
                &&p.z-(origin.getZ()+5.5)>radius && origin.getZ()+13.5-p.z>radius;
    }

    private static void check(boolean ok,String why){if(!ok)throw new GameTestAssertException(why);}

    @GameTest(templateNamespace="moonstation14", template="singularity_fixture_clear", batch="singularity_fixture_layout", timeoutTicks=100)
    public static void refusesOccupiedReservedCellWithoutMutation(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(
                origin.getX()-1,origin.getY(),origin.getZ()-1,origin.getX()+28,origin.getY()+12,origin.getZ()+20));
        FixtureChunkTickets.awaitEntityTicking(h,lease,80,()->{
        BlockPos obstruction=origin.offset(24,7,17);
        level.setBlock(obstruction,Blocks.STONE.defaultBlockState(),3);
        var result=SingularityFixtureLayout.build(level,origin);
        if(result.successful()) throw new GameTestAssertException("occupied reservation unexpectedly placed fixture");
        // This valid solid-host obstruction exercises ordinary reserved-envelope occupancy. Cable records
        // hosted on valid solid blocks are already rejected by this same occupancy preflight; a synthetic
        // record hosted in AIR would violate CableStorage's production invariant and is not fabricated here.
         for(BlockPos pos:new BlockPos[]{origin.offset(5,3,7),origin.offset(9,7,11),origin.offset(7,5,9),origin.offset(1,3,7)})
            if(!level.getBlockState(pos).isAir()) throw new GameTestAssertException("refusal mutated clear fixture position "+pos);
        if(!level.getBlockState(obstruction).is(Blocks.STONE)) throw new GameTestAssertException("refusal mutated obstruction");
        h.succeed();
        },origin,origin.offset(26,11,18));
    }

    @GameTestTags({"singularity-fixture", "wide-fixture-command"})
    @GameTest(templateNamespace="moonstation14", template="singularity_fixture_clear", batch="singularity_fixture_layout", timeoutTicks=120)
    public static void inactiveWideCommandBuildsUnchargedUnstartedFixture(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel(); BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin.getX()-1,origin.getY(),origin.getZ()-1,
                origin.getX()+28,origin.getY()+12,origin.getZ()+20));
        var owned=new java.util.ArrayList<BlockPos>(); cleanupOnEnd(h,level,owned);
        FixtureChunkTickets.awaitEntityTicking(h,lease,80,()->{
            var dispatcher=level.getServer().getCommands().getDispatcher();
            var source=level.getServer().createCommandSourceStack().withLevel(level).withPermission(2);
            try {
                check(dispatcher.execute("ms14 singularity_fixture build inactive_wide "+origin.getX()+" "+origin.getY()+" "+origin.getZ(),source)==1,
                        "inactive_wide command succeeds");
            } catch(com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new GameTestAssertException("wide build command syntax failed: "+e.getMessage()); }
            var record=SingularityFixtureService.registered(level,origin); check(record!=null&&record.buildError()==null,"wide command registers complete fixture");
            var layout=record.placed(); owned.addAll(layout.occupiedPositions()); owned.addAll(record.bootstrap().devices()); record.bootstrap().nodes().forEach(n->owned.add(n.host()));
            owned.addAll(record.export().devices()); record.export().nodes().forEach(n->owned.add(n.host())); owned.addAll(record.kit().accessBlocks());
            check(layout.fieldGenerators().size()==8&&layout.fieldGenerators().contains(origin.offset(3,1,5))
                            &&layout.fieldGenerators().contains(origin.offset(11,9,13)),"wide command selects eight-block enclosure with all corner generators");
            check(layout.fieldGenerators().stream().allMatch(p->((FieldGeneratorBlockEntity)level.getBlockEntity(p)).snapshot().charge()==0
                            &&!((FieldGeneratorBlockEntity)level.getBlockEntity(p)).snapshot().enabled())
                            &&layout.emitters().stream().allMatch(p->!((EmitterBlockEntity)level.getBlockEntity(p)).requestedOn())
                            &&((ParticleAcceleratorPartBlockEntity)level.getBlockEntity(layout.acceleratorControl())).strength()==ParticleAcceleratorStrength.STANDBY,
                    "wide command leaves fields uncharged/off, emitters off, PA in standby");
            check(SingularityFixtureService.readinessSnapshot(level,origin).jobState()==SingularityFixtureService.JobState.BUILT_INACTIVE,
                    "wide command does not auto-start or commission");
            String startRefusal=SingularityFixtureService.startRunning(level,origin);
            check(startRefusal.startsWith("Refused: wide fixtures are inactive-only"),"public running service refuses wide fixture start: "+startRefusal);
            check(SingularityFixtureService.readinessSnapshot(level,origin).jobState()==SingularityFixtureService.JobState.BUILT_INACTIVE
                            &&layout.fieldGenerators().stream().allMatch(p->((FieldGeneratorBlockEntity)level.getBlockEntity(p)).snapshot().charge()==0
                                    &&!((FieldGeneratorBlockEntity)level.getBlockEntity(p)).snapshot().enabled())
                            &&layout.emitters().stream().allMatch(p->!((EmitterBlockEntity)level.getBlockEntity(p)).requestedOn())
                            &&layout.collectors().stream().allMatch(p->!((RadiationCollectorBlockEntity)level.getBlockEntity(p)).enabled())
                            &&((ParticleAcceleratorPartBlockEntity)level.getBlockEntity(layout.acceleratorControl())).strength()==ParticleAcceleratorStrength.STANDBY,
                    "refused wide start creates no job and changes no charge, emitter, collector, or PA state");
            assertEmitterRays(level,origin,layout,record.bootstrap(),record.export()); SingularityFixtureService.forget(level,origin); h.succeed();
        },origin,origin.offset(26,11,18));
    }

    @GameTestTags({"reactor-bootstrap", "reactor-containment"})
    @GameTest(templateNamespace="moonstation14", template="singularity_fixture_clear", batch="singularity_fixture_containment", timeoutTicks=2000)
    public static void bootstrapsAndDeliversFixturePower(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(
                origin.getX()-1,origin.getY(),origin.getZ()-1,origin.getX()+28,origin.getY()+12,origin.getZ()+20));
        var owned=new java.util.ArrayList<BlockPos>();
        cleanupOnEnd(h,level,owned);
        FixtureChunkTickets.awaitEntityTicking(h,lease,80,()->{
            var result=SingularityFixtureLayout.build(level,origin);
            if(!result.successful()) throw new GameTestAssertException("clear fixture refused: "+result.refusal());
            var layout=result.layout();
            owned.addAll(layout.occupiedPositions());
            var circuit=SingularityFixturePower.bootstrap(level,layout);
            owned.addAll(circuit.devices());
            for(var node:circuit.nodes()) owned.add(node.host());
            if(!circuit.topologyVerified()) throw new GameTestAssertException("fixture circuit topology not verified");
            for(BlockPos pos:layout.emitters()) ((EmitterBlockEntity)level.getBlockEntity(pos)).setRequestedOn(true);
            var control=(ParticleAcceleratorPartBlockEntity)level.getBlockEntity(layout.acceleratorControl());
            var power=(ParticleAcceleratorPartBlockEntity)level.getBlockEntity(layout.acceleratorParts().stream()
                    .filter(p->level.getBlockEntity(p) instanceof ParticleAcceleratorPartBlockEntity be&&be.role().equals("power"))
                    .findFirst().orElseThrow());
            PowerFixtureReadiness.await(h,level,600,
                    ()->layout.emitters().stream().allMatch(p->level.getBlockEntity(p) instanceof EmitterBlockEntity e&&e.firingPowered())
                            && PowerRuntime.apcPowerMeter(level,control).known()
                            && PowerRuntime.mvPowerMeter(level,power).known()
                            && PowerRuntime.mvPowerMeter(level,power).deliveredWatts()>=500,
                    ()->"control="+PowerRuntime.apcPowerMeter(level,control)+" power="+PowerRuntime.mvPowerMeter(level,power),
                    ()->{
                        for(BlockPos pos:layout.fieldGenerators()) {
                            var field=(FieldGeneratorBlockEntity)level.getBlockEntity(pos);
                            if(!field.snapshot().enabled()) field.toggleEnabled();
                        }
                        waitForFieldCharge(h,level,layout,control,power,500,()->waitForStableContainment(h,level,layout,control,power,0));
                    });
        },origin,origin.offset(26,11,18));
    }

    @GameTestTags({"reactor-generation", "singularity-fixture", "singularity_fixture", "emitter-targeting", "reactor-containment"})
     @GameTest(templateNamespace="moonstation14", template="singularity_fixture_clear", batch="singularity_fixture_generation", timeoutTicks=14000)
    public static void naturalStartupGeneratesSustainedExportPower(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(
                origin.getX()-1,origin.getY(),origin.getZ()-1,origin.getX()+28,origin.getY()+12,origin.getZ()+20));
        var owned=new java.util.ArrayList<BlockPos>();
        cleanupOnEnd(h,level,owned);
        FixtureChunkTickets.awaitEntityTicking(h,lease,80,()->{
            var result=SingularityFixtureService.build(level,origin);
            if(!result.successful()) throw new GameTestAssertException("full fixture refused: "+result.error());
            var record=result.record(); var layout=record.placed(); owned.addAll(layout.occupiedPositions());
            owned.addAll(record.bootstrap().devices()); for(var n:record.bootstrap().nodes())owned.add(n.host());
            owned.addAll(record.export().devices()); for(var n:record.export().nodes())owned.add(n.host());
            owned.addAll(record.kit().accessBlocks());
            // Trace actual post-bootstrap/post-kit collision before startup can mask a bad lane.
            assertEmitterRays(level,origin,layout,record.bootstrap(),record.export());
            check(layout.fieldGenerators().stream().allMatch(p->((FieldGeneratorBlockEntity)level.getBlockEntity(p)).snapshot().charge()==0)
                            &&layout.emitters().stream().allMatch(p->!((EmitterBlockEntity)level.getBlockEntity(p)).requestedOn()),
                    "native startup begins with no injected field charge and all ordinary emitters off");
            double[] initial=new double[layout.collectors().size()];
            for(int i=0;i<initial.length;i++) {
                var collector=(RadiationCollectorBlockEntity)level.getBlockEntity(layout.collectors().get(i));
                initial[i]=GasTankItem.contents(collector.tankSlot().snapshot().stack(),level).orElseThrow().mixture().moles(GasType.PLASMA);
            }
            String started=SingularityFixtureService.startRunning(level,origin);
            if(!started.startsWith("STARTING:")) throw new GameTestAssertException("running job refused: "+started);
            new ReactorGenerationRun(h,level,layout,origin.offset(24,3,12),origin.offset(24,3,14),initial,owned,origin,record.kit().provisionedEntities()).tick();
        },origin,origin.offset(26,11,18));
    }

    @GameTestTags({"reactor-inactive", "batchsingularity_fixture_inactive"})
    @GameTest(templateNamespace="moonstation14", template="singularity_fixture_clear", batch="singularity_fixture_inactive", timeoutTicks=100)
    public static void inactiveConsoleCommandBuildsCompleteUnstartedReactor(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos origin=h.absolutePos(new BlockPos(1,1,1));
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(
                origin.getX()-1,origin.getY(),origin.getZ()-1,origin.getX()+28,origin.getY()+12,origin.getZ()+20));
        var owned=new java.util.ArrayList<BlockPos>();
        var provisioned=new java.util.ArrayList<UUID>();
        h.testInfo.addListener(new GameTestListener() {
            private void clean(){
                provisioned.forEach(id->{var entity=level.getEntity(id);if(entity!=null)entity.discard();});
                for(BlockPos pos:owned) level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
                SingularityFixtureService.forget(level,origin);
            }
            public void testStructureLoaded(GameTestInfo i){}
            public void testPassed(GameTestInfo i,GameTestRunner r){clean();}
            public void testFailed(GameTestInfo i,GameTestRunner r){clean();}
            public void testAddedForRerun(GameTestInfo a,GameTestInfo b,GameTestRunner r){clean();}
        });
        FixtureChunkTickets.awaitEntityTicking(h,lease,80,()->{
            var dispatcher=level.getServer().getCommands().getDispatcher();
            var source=level.getServer().createCommandSourceStack().withLevel(level).withPermission(2);
             int result;
             try { result=dispatcher.execute("ms14 singularity_fixture build inactive "+origin.getX()+" "+origin.getY()+" "+origin.getZ(),source); }
             catch(com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new GameTestAssertException("inactive build command syntax failed: "+e.getMessage()); }
            check(result==1,"inactive console build command failed");
            var record=SingularityFixtureService.registered(level,origin);
            check(record!=null&&record.buildError()==null&&record.placed()!=null&&record.bootstrap()!=null&&record.export()!=null&&record.kit()!=null,
                    "command must register the complete fixture, circuits and operator kit");
            var layout=record.placed(); owned.addAll(layout.occupiedPositions());
            owned.addAll(record.bootstrap().devices()); record.bootstrap().nodes().forEach(n->owned.add(n.host()));
             owned.addAll(record.export().devices()); record.export().nodes().forEach(n->owned.add(n.host()));
             owned.addAll(record.kit().accessBlocks()); provisioned.addAll(record.kit().provisionedEntities());
             // These are actual planned rear-wall nodes (bootstrap y=1 cable runs), not arbitrary wall cells.
             boolean[] rearWallHosts={false,false};
             for(var node:record.bootstrap().nodes()) {
                 BlockPos host=node.host();
                 if((host.getX()==origin.getX()||host.getX()==origin.getX()+14)
                         &&host.getY()==origin.getY()+1&&host.getZ()>=origin.getZ()+4&&host.getZ()<=origin.getZ()+13) {
                     rearWallHosts[host.getX()==origin.getX()?0:1]=true;
                     check(level.getBlockState(host).is(Blocks.GLASS),"planned rear-wall cable host remains glass after operator kit at "+host);
                     check(CableStorage.has(level,host,node.face(),node.tier()),"planned rear-wall cable attachment retained after operator kit at "+node);
                 }
             }
             check(rearWallHosts[0]&&rearWallHosts[1],"planned MV cable hosts exist on both rear walls");
             check(record.bootstrap().ports().stream().allMatch(port->record.bootstrap().nodes().stream().anyMatch(node->PowerTopology.deviceAdjacent(port,node))),
                     "all fixture power port supports remain adjacent to planned cable nodes");
            var inactive=SingularityFixtureService.readinessSnapshot(level,origin);
            check(inactive!=null&&inactive.jobState()==SingularityFixtureService.JobState.BUILT_INACTIVE,"inactive build readiness snapshot changed");
            var control=(ParticleAcceleratorPartBlockEntity)level.getBlockEntity(layout.acceleratorControl());
            check(control!=null&&control.strength()==ParticleAcceleratorStrength.STANDBY&&!control.enabled(),"PA must remain off/standby");
            var gen=(SingularityGeneratorBlockEntity)level.getBlockEntity(layout.startupGenerator());
            check(gen!=null&&gen.startupCharge()==0&&!gen.startupInert(),"GEN must have zero charge and remain non-inert");
            check(layout.emitters().size()==8&&layout.emitters().stream().allMatch(p->level.getBlockEntity(p) instanceof EmitterBlockEntity e&&!e.requestedOn()),"all eight emitters must be off");
            check(layout.fieldGenerators().size()==8&&layout.fieldGenerators().stream().allMatch(p->level.getBlockEntity(p) instanceof FieldGeneratorBlockEntity f&&!f.snapshot().enabled()&&f.snapshot().charge()==0),"all eight fields must be off and uncharged");
            check(layout.collectors().size()==4&&layout.collectors().stream().allMatch(p->level.getBlockEntity(p) instanceof RadiationCollectorBlockEntity c&&c.controlStateReady()
                    &&Double.isFinite(GasTankItem.contents(c.tankSlot().snapshot().stack(),level).orElseThrow().mixture().moles(GasType.PLASMA))
                    &&GasTankItem.contents(c.tankSlot().snapshot().stack(),level).orElseThrow().mixture().moles(GasType.PLASMA)>0),"four finite, nonempty collector tanks must be provisioned");
            check(record.kit().provisionedEntities().size()==7,"kit must provision tools, PPE, spare tanks and canister");
            check(level.getEntitiesOfClass(SingularityEntity.class,new net.minecraft.world.phys.AABB(layout.startupGenerator()).inflate(32)).isEmpty(),"inactive fixture must contain no singularity");
            h.succeed();
        },origin,origin.offset(26,11,18));
    }

    private static final class ReactorGenerationRun {
        private final GameTestHelper h; private final ServerLevel level; private final SingularityFixtureLayout.Placed layout;
        private final BlockPos apcPos,lamp,origin; private final double[] fuel;
        private int elapsed,measurementTicks,readyElapsed,positiveEnergyJumps; private boolean measuring;
        private double previousEnergy;
        private double minOutput=Double.POSITIVE_INFINITY,maxOutput,minOffer=Double.POSITIVE_INFINITY,maxOffer,minEnergy=Double.POSITIVE_INFINITY,maxEnergy,energyGains;
        private int minLevel=Integer.MAX_VALUE,maxLevel;
        private final java.util.List<BlockPos> owned;
        private final java.util.Set<UUID> emitterIds=new HashSet<>(), paIds=new HashSet<>();
        private final java.util.Set<net.minecraft.world.entity.Entity> captured=new java.util.HashSet<>();
        private final Consumer<EntityJoinLevelEvent> observer;
        ReactorGenerationRun(GameTestHelper h,ServerLevel level,SingularityFixtureLayout.Placed layout,BlockPos apcPos,BlockPos lamp,double[] fuel,java.util.List<BlockPos> owned,BlockPos origin,java.util.List<UUID> provisionedEntities) {
            this.h=h;this.level=level;this.layout=layout;this.apcPos=apcPos;this.lamp=lamp;this.fuel=fuel;this.owned=owned;this.origin=origin;
            layout.emitters().forEach(p->{if(level.getBlockEntity(p) instanceof EmitterBlockEntity be)emitterIds.add(be.placementIdentity());});
            layout.acceleratorParts().forEach(p->{if(level.getBlockEntity(p) instanceof ParticleAcceleratorPartBlockEntity be)paIds.add(be.placementIdentity());});
            Vec3 center=Vec3.atCenterOf(layout.startupGenerator());
            observer=event->{if(event.getLevel()!=level)return;var entity=event.getEntity();
                if(entity instanceof EmitterBoltEntity bolt&&emitterIds.contains(bolt.emitterIdentity())
                        ||entity instanceof ParticleAcceleratorParticleEntity particle&&paIds.contains(particle.sourceEmitterIdentity())
                        ||entity instanceof SingularityEntity&&entity.position().distanceToSqr(center)<=4)captured.add(entity);};
            NeoForge.EVENT_BUS.addListener(observer);
            h.testInfo.addListener(new GameTestListener(){private void clean(){NeoForge.EVENT_BUS.unregister(observer);captured.forEach(net.minecraft.world.entity.Entity::discard);captured.clear();
                    provisionedEntities.forEach(id->{var entity=level.getEntity(id);if(entity!=null)entity.discard();});
                    SingularityFixtureService.forget(level,origin);}
                public void testStructureLoaded(GameTestInfo i){} public void testPassed(GameTestInfo i,GameTestRunner r){clean();}
                public void testFailed(GameTestInfo i,GameTestRunner r){clean();} public void testAddedForRerun(GameTestInfo a,GameTestInfo b,GameTestRunner r){clean();}});
        }
        void tick() {
            try {
                elapsed++;
                var apc=level.getBlockEntity(apcPos);
                var observation=apc instanceof com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity p
                        ?PowerRuntime.apcOutputObservation(level,p):PowerRuntime.ApcOutputObservation.UNKNOWN;
                double offers=0; boolean consumed=false;
                for(int i=0;i<layout.collectors().size();i++) {
                    var collector=(RadiationCollectorBlockEntity)level.getBlockEntity(layout.collectors().get(i));
                    offers+=collector.powerSupplierOffer().currentWatts();
                    double remaining=GasTankItem.contents(collector.tankSlot().snapshot().stack(),level).orElseThrow().mixture().moles(GasType.PLASMA);
                    consumed|=remaining<fuel[i]-1e-9;
                }
                var capturedSingularityIds=captured.stream().filter(SingularityEntity.class::isInstance)
                        .map(e->((SingularityEntity)e).getUUID())
                        .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
                var singularities=capturedSingularityIds.stream().map(level::getEntity)
                        .filter(SingularityEntity.class::isInstance).map(SingularityEntity.class::cast).toList();
                boolean sing=singularities.size()==1; var singularity=sing?singularities.get(0):null;
                var generator=(SingularityGeneratorBlockEntity)level.getBlockEntity(layout.startupGenerator());
                boolean lit=level.getBlockState(lamp).hasProperty(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.LIT)
                        &&level.getBlockState(lamp).getValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.LIT);
                var readiness=SingularityFixtureService.readinessSnapshot(level,origin);
                boolean ready=readiness!=null && readiness.jobState()==SingularityFixtureService.JobState.READY && generator.startupInert() && sing && singularity.energy()>0 && singularity.singularityLevel()>=1 && closed(level,layout)
                         &&layout.fieldGenerators().stream().allMatch(p->((FieldGeneratorBlockEntity)level.getBlockEntity(p)).connectionEligible())
                         &&fixtureFaces(level,layout)==6
                        &&layout.emitters().stream().allMatch(p->level.getBlockEntity(p) instanceof EmitterBlockEntity e&&e.firingPowered())
                        &&observation.known()&&observation.outputWatts()>=12000&&lit&&offers>0&&consumed;
                if(!measuring) {
                    if(ready){measuring=true;readyElapsed=elapsed;minOutput=maxOutput=observation.outputWatts();minOffer=maxOffer=offers;minEnergy=maxEnergy=singularity.energy();previousEnergy=singularity.energy();minLevel=maxLevel=singularity.singularityLevel(); LOGGER.info("reactor generation started elapsed="+elapsed+" energy="+singularity.energy()+" level="+singularity.singularityLevel()+" position="+singularity.position());}
                } else {
                    if(!ready) throw new GameTestAssertException("generation condition interrupted at tick "+elapsed
                            +" capturedSingularityUUIDs="+capturedSingularityIds+" capturedRefs="+captured.stream()
                            .filter(SingularityEntity.class::isInstance).map(e->{var s=(SingularityEntity)e;
                                return "uuid="+s.getUUID()+",energy="+s.energy()+",level="+s.singularityLevel()+",position="+s.position()
                                        +",removed="+s.isRemoved()+",alive="+s.isAlive()+",reason="+s.getRemovalReason();}).toList()
                            +" currentByUUID="+capturedSingularityIds.stream().map(id->{var e=level.getEntity(id);
                                return "uuid="+id+",present="+(e!=null)+(e instanceof SingularityEntity s?",energy="+s.energy()+",level="+s.singularityLevel()+",position="+s.position()+",removed="+s.isRemoved()+",alive="+s.isAlive():"");}).toList()
                            +" readinessUUID="+(readiness==null?null:readiness.singularityId())+" sing="+sing+" APC="+observation+" lit="+lit+" offers="+offers+" consumed="+consumed+" closed="+closed(level,layout));
                    double energyJump=singularity.energy()-previousEnergy;
                    previousEnergy=singularity.energy();
                    if(energyJump>0){positiveEnergyJumps++;energyGains+=energyJump;}
                    measurementTicks++; minOutput=Math.min(minOutput,observation.outputWatts());maxOutput=Math.max(maxOutput,observation.outputWatts());minOffer=Math.min(minOffer,offers);maxOffer=Math.max(maxOffer,offers);
                    minEnergy=Math.min(minEnergy,singularity.energy());maxEnergy=Math.max(maxEnergy,singularity.energy());minLevel=Math.min(minLevel,singularity.singularityLevel());maxLevel=Math.max(maxLevel,singularity.singularityLevel());
                    if(measurementTicks>=6000){double delta=0;for(int i=0;i<fuel.length;i++){var c=(RadiationCollectorBlockEntity)level.getBlockEntity(layout.collectors().get(i));delta+=fuel[i]-GasTankItem.contents(c.tankSlot().snapshot().stack(),level).orElseThrow().mixture().moles(GasType.PLASMA);}
                        LOGGER.info("reactor generation ended startupElapsed="+readyElapsed+" window="+measurementTicks+" energy="+minEnergy+".."+maxEnergy+" endEnergy="+singularity.energy()+" level="+minLevel+".."+maxLevel+" endLevel="+singularity.singularityLevel()+" position="+singularity.position()+" removed="+singularity.isRemoved()+" positiveJumps="+positiveEnergyJumps+" energyGains="+energyGains+" output="+minOutput+".."+maxOutput+" offers="+minOffer+".."+maxOffer+" fuelDelta="+delta);
                        if(positiveEnergyJumps==0||energyGains<=0)throw new GameTestAssertException("no positive natural capture-energy increments during sustained run");
                        var control=(ParticleAcceleratorPartBlockEntity)level.getBlockEntity(layout.acceleratorControl());
                        check(control.strength()==ParticleAcceleratorStrength.LEVEL_0,
                                "compact running fixture returns to the accepted one-time Level 0 upkeep preset");
                        control.setStrength(ParticleAcceleratorStrength.LEVEL_2);
                        SingularityFixtureService.tick(level);
                        check(control.strength()==ParticleAcceleratorStrength.LEVEL_2,
                                "READY fixture does not overwrite an operator's later PA strength choice");
                        h.succeed();return;}
                }
                if(elapsed>=13990) {
                    String startup=SingularityFixtureService.status(level,origin);
                    if(startup.length()>500)startup=startup.substring(0,500);
                    throw new GameTestAssertException("reactor generation timeout job="+readiness+" startup="
                        +startup+" measuring="+measuring+" measured="+measurementTicks
                        +" capturedSingularity="+singularities.stream().map(s->"energy="+s.energy()+",level="+s.singularityLevel()
                        +",position="+s.position()+",removed="+s.isRemoved()+",alive="+s.isAlive()).toList()+" genCharge="
                        +fieldDiagnostic(level,layout)+" APC="+observation+" offers="+offers);
                }
                BoundedGameTestWait.safeDelay(h,1,this::tick);
            } catch(RuntimeException e) { throw e; }
        }
    }

    private static boolean closed(ServerLevel level,SingularityFixtureLayout.Placed layout) {
        var generator=(SingularityGeneratorBlockEntity)level.getBlockEntity(layout.startupGenerator());
        var profile=ModSingularityGenerators.catalog(level).get(generator.profileId());
        Vec3 center=Vec3.atCenterOf(layout.startupGenerator());
        return profile!=null && (!profile.failsafeEnabled() || FieldContainmentRuntime.closedVolumes(level).stream()
                .anyMatch(bounds->ClosedFieldVolume.containsWithinFaceDistance(bounds,center,profile.failsafeDistance())));
    }
    private static long fixtureFaces(ServerLevel level,SingularityFixtureLayout.Placed layout) {
        Set<BlockPos> owned=Set.copyOf(layout.fieldGenerators());
        return FieldContainmentRuntime.currentFaces(level).stream().filter(f->owned.containsAll(f.corners())).count();
    }
    private static void waitForFieldCharge(GameTestHelper h,ServerLevel level,SingularityFixtureLayout.Placed layout,
                                           ParticleAcceleratorPartBlockEntity control,ParticleAcceleratorPartBlockEntity power,
                                           int remaining,Runnable ready) {
        if(layout.fieldGenerators().stream().allMatch(pos->{var f=(FieldGeneratorBlockEntity)level.getBlockEntity(pos);
            var profile=ModFieldGenerators.catalog(level).get(f.profileId()); return profile!=null&&f.snapshot().charge()>=profile.connectionThreshold();})) { ready.run(); return; }
        if(remaining<=0) throw new GameTestAssertException(fieldDiagnostic(level,layout)+" field charge timeout");
        BoundedGameTestWait.safeDelay(h,1,()->waitForFieldCharge(h,level,layout,control,power,remaining-1,ready));
    }
    private static void waitForStableContainment(GameTestHelper h,ServerLevel level,SingularityFixtureLayout.Placed layout,
                                                   ParticleAcceleratorPartBlockEntity control,ParticleAcceleratorPartBlockEntity power,int elapsed) {
        FieldContainmentRuntime.refresh(level);
        boolean eligible=layout.fieldGenerators().stream().allMatch(pos->((FieldGeneratorBlockEntity)level.getBlockEntity(pos)).connectionEligible());
        boolean powered=layout.emitters().stream().allMatch(pos->level.getBlockEntity(pos) instanceof EmitterBlockEntity emitter&&emitter.firingPowered());
        var owned=Set.copyOf(layout.fieldGenerators());
        long edges=FieldContainmentRuntime.currentEdges(level).stream().filter(e->owned.contains(e.first())&&owned.contains(e.second())).count();
        long faces=FieldContainmentRuntime.currentFaces(level).stream().filter(f->owned.containsAll(f.corners())).count();
        boolean acceleratorOff=control.strength()==ParticleAcceleratorStrength.STANDBY&&!control.enabled();
        boolean noSingularity=level.getEntitiesOfClass(com.juicyslew.moonstation14.entities.SingularityEntity.class,
                new net.minecraft.world.phys.AABB(layout.startupGenerator()).inflate(32)).isEmpty();
        if(!closed(level,layout)||!eligible||!powered||edges!=12||faces!=6||!acceleratorOff||!noSingularity)
            throw new GameTestAssertException(fieldDiagnostic(level,layout)+" containment lost at " +elapsed+" ownedEdges="+edges+" ownedFaces="+faces+" control="+PowerRuntime.apcPowerMeter(level,control)+" power="+PowerRuntime.mvPowerMeter(level,power));
        if(elapsed>=600){h.succeed();return;}
        BoundedGameTestWait.safeDelay(h,1,()->waitForStableContainment(h,level,layout,control,power,elapsed+1));
    }
    private static String fieldDiagnostic(ServerLevel level,SingularityFixtureLayout.Placed layout) {
        var charges=layout.fieldGenerators().stream().map(pos->{var f=(FieldGeneratorBlockEntity)level.getBlockEntity(pos);return f.snapshot().charge()+"/"+f.connectionEligible();}).toList();
        var owned=java.util.Set.copyOf(layout.fieldGenerators());
        long edges=FieldContainmentRuntime.currentEdges(level).stream().filter(e->owned.contains(e.first())&&owned.contains(e.second())).count();
        long faces=FieldContainmentRuntime.currentFaces(level).stream().filter(f->owned.containsAll(f.corners())).count();
        return "charges/eligible="+charges+" ownedEdges="+edges+" ownedFaces="+faces+" volumes="+FieldContainmentRuntime.closedVolumes(level).size();
    }

    private static void cleanupOnEnd(GameTestHelper h,ServerLevel level,java.util.List<BlockPos> owned) {
        h.testInfo.addListener(new GameTestListener() {
            private void clean(){ for(BlockPos pos:owned) level.setBlock(pos,Blocks.AIR.defaultBlockState(),3); }
            public void testStructureLoaded(GameTestInfo i){}
            public void testPassed(GameTestInfo i,GameTestRunner r){clean();}
            public void testFailed(GameTestInfo i,GameTestRunner r){clean();}
            public void testAddedForRerun(GameTestInfo a,GameTestInfo b,GameTestRunner r){clean();}
        });
    }
}
