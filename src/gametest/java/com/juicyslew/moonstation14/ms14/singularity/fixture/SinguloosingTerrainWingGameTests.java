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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;

@GameTestTags({"singuloosing", "singularity-fixture"})
@PrefixGameTestTemplate(false)
public final class SinguloosingTerrainWingGameTests {
    private SinguloosingTerrainWingGameTests() { }
    @GameTest(templateNamespace="moonstation14",template="singularity_fixture_clear",batch="singuloosing_fixture",timeoutTicks=100)
    public static void boundedWingUsesOnlyClearOwnedCells(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel(); BlockPos origin=h.absolutePos(new BlockPos(1,120,1));
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin.getX()-1,origin.getY(),origin.getZ()-1,
                origin.getX()+42,origin.getY()+6,origin.getZ()+16));
        var owned=new ArrayList<BlockPos>();
        var cables=new ArrayList<com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode>();
        var entities=new ArrayList<java.util.UUID>();
        h.testInfo.addListener(new GameTestListener(){private void clean(){SingularityFixtureService.forget(level,origin);cables.forEach(n->com.juicyslew.moonstation14.ms14.power.cable.CableStorage.remove(level,n.host(),n.face(),n.tier()));
            entities.forEach(id->{var entity=level.getEntity(id);if(entity!=null)entity.discard();});owned.forEach(p->level.setBlock(p,Blocks.AIR.defaultBlockState(),3));}
            public void testStructureLoaded(GameTestInfo i){} public void testPassed(GameTestInfo i,GameTestRunner r){clean();}
            public void testFailed(GameTestInfo i,GameTestRunner r){clean();} public void testAddedForRerun(GameTestInfo a,GameTestInfo b,GameTestRunner r){clean();}});
        FixtureChunkTickets.awaitEntityTicking(h,lease,80,()->{
            var source=level.getServer().createCommandSourceStack().withLevel(level).withPermission(2);
            try { check(level.getServer().getCommands().getDispatcher().execute("ms14 singularity_fixture build singuloosing "+origin.getX()+" "+origin.getY()+" "+origin.getZ(),source)==1,"real singuloosing fixture command succeeds"); }
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new GameTestAssertException("singuloosing command syntax: "+e.getMessage());}
            var record=SingularityFixtureService.registered(level,origin);check(record!=null&&record.buildError()==null,"command registers completed singuloosing fixture");
            var layout=record.placed();owned.addAll(layout.occupiedPositions());
            owned.addAll(record.bootstrap().devices());owned.addAll(record.export().devices());owned.addAll(record.kit().accessBlocks());
            record.bootstrap().nodes().forEach(n->{cables.add(n);owned.add(n.host());});record.export().nodes().forEach(n->{cables.add(n);owned.add(n.host());});
            entities.addAll(record.kit().provisionedEntities());
            var wing=SingularityFixtureService.terrainWing(level,origin);check(wing!=null,"command installed owned wing and beacon");
            owned.addAll(wing.owned());cables.addAll(wing.beaconNodes());
            check(level.getBlockState(wing.soil()).is(Blocks.DIRT),"sacrificial strip has ordinary dirt");
            check(level.getBlockState(wing.grass()).is(Blocks.GRASS_BLOCK),"sacrificial strip has ordinary grass");
            check(level.getBlockState(origin.offset(34,2,3)).is(Blocks.STONE_BRICKS),"bounded station wall built from ordinary blocks");
            check(layout.fieldGenerators().contains(wing.breachCorner())
                     &&wing.breachCorner().getX()==origin.getX()+9&&wing.breachCorner().getY()==origin.getY()+4
                    &&wing.breachCorner().getZ()==origin.getZ()+8,
                    "breach target is the actual low-profile fixture's selected east corner");
            check(level.getBlockEntity(wing.beacon()) instanceof com.juicyslew.moonstation14.block.block_entity.SingularityBeaconBlockEntity beacon
                    &&beacon.resolvedProfile()!=null&&beacon.resolvedProfile().powerWatts()==5000,"typed-profile beacon is placed as a real power consumer");
            check(record.export().devices().stream().anyMatch(pos->level.getBlockState(pos).getBlock() instanceof com.juicyslew.moonstation14.block.custom.PowerDeviceBlock block
                            &&block.kind()==com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind.LAMP),"singuloosing preset selects the ordinary export lamp, preserving the regular fixture's high-load default");
            check(com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind.LAMP.lampDemandWatts()+5000<=10000,
                    "ordinary export lamp plus resolved beacon demand fits the APC's 10kW supply ceiling");
            check(wing.beaconNodes().stream().allMatch(n->com.juicyslew.moonstation14.ms14.power.cable.CableStorage.has(level,n.host(),n.face(),n.tier())),"all real APC branch cable records exist");
            check(record.export().nodes().stream().anyMatch(sourceNode->wing.beaconNodes().stream().anyMatch(branch->com.juicyslew.moonstation14.ms14.power.topology.PowerTopology.cableEdge(sourceNode,branch).isPresent())),"beacon APC branch physically joins the existing export network");
            var beaconPort=((com.juicyslew.moonstation14.block.block_entity.SingularityBeaconBlockEntity)level.getBlockEntity(wing.beacon())).powerRuntimePorts().get(0);
            check(wing.beaconNodes().stream().anyMatch(n->com.juicyslew.moonstation14.ms14.power.topology.PowerTopology.deviceAdjacent(beaconPort,n)),"APC branch terminates on beacon's actual north port");
             try { check(level.getServer().getCommands().getDispatcher().execute("ms14 singularity_fixture breach "+wing.breachCorner().getX()+" "+wing.breachCorner().getY()+" "+wing.breachCorner().getZ(),source)==1,"derived singuloosing-fixture corner is accepted by the real breach command"); }
            catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new GameTestAssertException("breach command syntax: "+e.getMessage());}
            check(level.getBlockState(wing.breachCorner()).isAir(),"command removes selected corner only");
            check(layout.fieldGenerators().stream().filter(pos->!pos.equals(wing.breachCorner())).allMatch(pos->level.getBlockEntity(pos) instanceof com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity),"other seven fixture corners remain intact");
            h.succeed();
        },origin.offset(29,0,3),origin.offset(39,4,13));
    }
    @GameTest(template="empty",templateNamespace="moonstation14",batch="singuloosing_fixture",timeoutTicks=100)
    public static void existingWingCableRecordRefusesBuildWithoutMutation(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel(); BlockPos origin=h.absolutePos(new BlockPos(1,120,1));
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin.getX()-1,origin.getY(),origin.getZ()-1,origin.getX()+42,origin.getY()+6,origin.getZ()+16));
        FixtureChunkTickets.awaitEntityTicking(h,lease,80,()->{
        BlockPos root=origin.offset(25,3,13); level.setBlock(root,Blocks.STONE.defaultBlockState(),3);
        var node=new com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode(root,net.minecraft.core.Direction.SOUTH,
                com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC);
        if(!com.juicyslew.moonstation14.ms14.power.cable.CableStorage.place(level,root,node.face(),node.tier()))
            throw new GameTestAssertException("test-owned obstruction cable failed to place");
        try {
            var source=level.getServer().createCommandSourceStack().withLevel(level).withPermission(2);
            int result=level.getServer().getCommands().getDispatcher().execute("ms14 singularity_fixture build singuloosing "+origin.getX()+" "+origin.getY()+" "+origin.getZ(),source);
            check(result==0&&SingularityFixtureService.registered(level,origin)==null,"unrelated wing cable record safely refuses build");
            check(level.getBlockState(root).is(Blocks.STONE)&&com.juicyslew.moonstation14.ms14.power.cable.CableStorage.has(level,root,node.face(),node.tier()),"refusal preserves unrelated cable host and record");
            check(level.getBlockState(origin.offset(11,1,5)).isAir(),"refusal places no containment generators");
            h.succeed();
        } catch(com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new GameTestAssertException("singuloosing refusal command syntax: "+e.getMessage()); }
        finally { com.juicyslew.moonstation14.ms14.power.cable.CableStorage.remove(level,root,node.face(),node.tier()); level.setBlock(root,Blocks.AIR.defaultBlockState(),3); }
        },origin,origin.offset(26,11,18),origin.offset(39,4,14));
    }
    @GameTest(templateNamespace="moonstation14",template="singularity_fixture_clear",batch="singuloosing_escape",timeoutTicks=180)
    public static void stageThreeNaturallyEscapesBreachedSinguloosingFaceAndDamagesSacrificialWing(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();BlockPos origin=h.absolutePos(new BlockPos(1,120,1));
        var lease=FixtureChunkTickets.acquire(h,new net.minecraft.world.phys.AABB(origin.getX()-1,origin.getY(),origin.getZ()-1,
                origin.getX()+42,origin.getY()+12,origin.getZ()+20));
        var owned=new java.util.LinkedHashSet<BlockPos>();var cables=new java.util.ArrayList<com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode>();
        var entities=new java.util.ArrayList<java.util.UUID>();var sourceRef=new com.juicyslew.moonstation14.entities.SingularityEntity[1];
        h.testInfo.addListener(new GameTestListener(){private void clean(){if(sourceRef[0]!=null)sourceRef[0].discard();SingularityFixtureService.forget(level,origin);
            cables.forEach(n->com.juicyslew.moonstation14.ms14.power.cable.CableStorage.remove(level,n.host(),n.face(),n.tier()));
            entities.forEach(id->{var e=level.getEntity(id);if(e!=null)e.discard();});owned.forEach(p->level.setBlock(p,Blocks.AIR.defaultBlockState(),3));}
            public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){clean();}
            public void testFailed(GameTestInfo i,GameTestRunner r){clean();}public void testAddedForRerun(GameTestInfo a,GameTestInfo b,GameTestRunner r){clean();}});
        FixtureChunkTickets.awaitEntityTicking(h,lease,80,()->{
             var built=SingularityFixtureService.buildSinguloosing(level,origin);check(built.successful(),"complete singuloosing damage fixture builds: "+built.error());
            var record=built.record();var layout=record.placed();owned.addAll(layout.occupiedPositions());owned.addAll(record.bootstrap().devices());owned.addAll(record.export().devices());owned.addAll(record.kit().accessBlocks());
            record.bootstrap().nodes().forEach(n->{cables.add(n);owned.add(n.host());});record.export().nodes().forEach(n->{cables.add(n);owned.add(n.host());});
            entities.addAll(record.kit().provisionedEntities());var wing=SingularityFixtureService.terrainWing(level,origin);check(wing!=null,"owned sacrificial wing exists");
            owned.addAll(wing.owned());cables.addAll(wing.beaconNodes());
            var corners=new java.util.HashSet<>(layout.fieldGenerators());
            for(BlockPos p:layout.fieldGenerators()) {var field=(com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity)level.getBlockEntity(p);
                field.receiveEmitterBolt();field.receiveEmitterBolt();check(field.toggleEnabled(),"real charged corner generator enables");}
            com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.refresh(level);
            check(com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.currentFaces(level).stream().filter(f->corners.containsAll(f.corners())).count()==6,
                    "all six faces hold before the authorized corner removal");
            var source=com.juicyslew.moonstation14.entities.ModEntities.SINGULARITY.get().create(level);check(source!=null,"registered source created");sourceRef[0]=source;
            source.setPos(Vec3.atCenterOf(origin.offset(8,5,9)));check(level.addFreshEntity(source),"source admitted inside intact compact containment");source.feed(620);
            check(source.singularityLevel()==3,"resolved profile feeds source to stage three");
            int minWalkInterval=source.gravityProfile().randomWalk().minimumIntervalTicks();
            check(minWalkInterval>32,"resolved random-walk interval leaves the test-only initial impulse intact until wing impact");
            double wallBefore=wing.owned().stream().filter(p->wing.ownedStates().get(p).is(Blocks.STONE_BRICKS)).count();
            h.runAfterDelay(1,()->{
                check(com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.closedVolumes(level).size()==1,
                        "natural world tick leaves intact source contained before breach");
                check(level.getBlockEntity(wing.breachCorner()) instanceof com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity,
                        "all eight actual field corner blocks remain before the selected breach");
                 String result=SingularityFixtureService.breachOwnedCorner(level,wing.breachCorner());check(result.startsWith("Removed"),"derived singuloosing corner accepted: "+result);
                com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.refresh(level);
                check(com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.closedVolumes(level).isEmpty(),"removed corner opens actual containment volume");
                check(com.juicyslew.moonstation14.ms14.singularity.containment.FieldContainmentRuntime.currentFaces(level).stream().filter(f->corners.containsAll(f.corners())).count()<6,
                        "broken corner removes connected enclosure faces");
                source.setDeltaMovement(20,0,0); // Test-only initial condition; all movement/destruction below is normal world ticking.
                h.runAfterDelay(31,()->{
                    int eastPlane=layout.fieldGenerators().stream().mapToInt(BlockPos::getX).max().orElseThrow();
                    check(source.position().x>eastPlane+.5,"stage-three source crossed the former east containment plane on native ticks: "+source.position());
                    long remainingWalls=wing.owned().stream().filter(pos->wing.ownedStates().get(pos).is(Blocks.STONE_BRICKS)&&level.getBlockState(pos).is(Blocks.STONE_BRICKS)).count();
                    check(wallBefore-remainingWalls>=8,"natural event-horizon waves destroy a bounded mass of station wing blocks: "+wallBefore+" -> "+remainingWalls);
                    h.succeed();
                });
            });
        },origin,origin.offset(26,11,18),origin.offset(39,4,14));
    }
    private static void check(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
}
