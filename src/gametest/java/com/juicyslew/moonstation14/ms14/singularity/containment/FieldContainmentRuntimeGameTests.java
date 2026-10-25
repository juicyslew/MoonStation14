package com.juicyslew.moonstation14.ms14.singularity.containment;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.FieldGeneratorBlockEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;

/** Registered block/entity and loaded-world tests for server-derived field connections. */
@GameTestTags("field-generator-adapter-fixture")
@PrefixGameTestTemplate(false)
public final class FieldContainmentRuntimeGameTests {
    private FieldContainmentRuntimeGameTests() { }
    /** Restrict level-wide derived topology to this fixture's registered corner positions. */
    private static final class LocalView {
        private final Set<BlockPos> positions;
        private final net.minecraft.server.level.ServerLevel level;
        LocalView(GameTestHelper h, List<FieldGeneratorBlockEntity> generators) {
            level=h.getLevel(); positions=new HashSet<>();
            for(var generator:generators) positions.add(generator.getBlockPos());
        }
        Set<FieldContainmentRuntime.Face> faces() {
            return FieldContainmentRuntime.currentFaces(level).stream()
                    .filter(f->positions.containsAll(f.corners())).collect(java.util.stream.Collectors.toSet());
        }
        Set<ClosedFieldVolume.Edge> edges() {
            return FieldContainmentRuntime.currentEdges(level).stream()
                    .filter(e->positions.contains(e.first())&&positions.contains(e.second())).collect(java.util.stream.Collectors.toSet());
        }
        List<ClosedFieldVolume.Bounds> volumes() {
            return FieldContainmentRuntime.closedVolumes(level).stream().filter(v->positions.stream().allMatch(p->
                    p.getX()>=v.min().getX()&&p.getX()<=v.max().getX()&&p.getY()>=v.min().getY()&&p.getY()<=v.max().getY()
                            &&p.getZ()>=v.min().getZ()&&p.getZ()<=v.max().getZ())).toList();
        }
    }
    private static void check(GameTestHelper h,boolean pass,String message) { if(!pass)throw new GameTestAssertException(message); }

    @GameTestTags({"reactor-models", "containment"})
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=40)
    public static void resolvedProfileEnabledZeroChargeSelectsVisibleFieldTierIndependently(GameTestHelper h) {
        var pos=new BlockPos(2,2,2);
        h.setBlock(pos,ModBlocks.FIELD_GENERATOR.get());
        var level=h.getLevel();
        var generator=(FieldGeneratorBlockEntity)level.getBlockEntity(h.absolutePos(pos));
        check(h,generator!=null,"registered field generator block entity exists");
        var profile=ModFieldGenerators.catalog(level).get(generator.profileId());
        check(h,profile!=null,"fixture resolves inherited field-generator profile");
        FieldGeneratorBlockEntity.tick(level,generator);
        var state=generator.getBlockState();
        check(h,!generator.snapshot().enabled()&&generator.snapshot().charge()==0,"fresh fixture begins disabled and uncharged");
        check(h,state.getValue(com.juicyslew.moonstation14.block.custom.FieldGeneratorBlock.FIELD)==0,"disabled tier selects dark default field art");
        check(h,state.getValue(com.juicyslew.moonstation14.block.custom.FieldGeneratorBlock.POWER)==0&&!state.getValue(com.juicyslew.moonstation14.block.custom.FieldGeneratorBlock.ACTIVE),"off power and connection channels independent");
        check(h,generator.toggleEnabled(),"fresh unconnected generator enables");
        FieldGeneratorBlockEntity.tick(level,generator);
        state=generator.getBlockState();
        check(h,generator.snapshot().enabled()&&generator.snapshot().charge()==0,"enabled-at-zero-charge uses live resolved state");
        check(h,state.getValue(com.juicyslew.moonstation14.block.custom.FieldGeneratorBlock.FIELD)==1,"enabled zero charge selects R8 a1 field tier");
        check(h,state.getValue(com.juicyslew.moonstation14.block.custom.FieldGeneratorBlock.POWER)==0&&!state.getValue(com.juicyslew.moonstation14.block.custom.FieldGeneratorBlock.ACTIVE),"a1 does not mutate charge or connected channels");
        h.succeed();
    }

    @GameTestTags({"reactor-models", "containment"})
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=40)
    public static void oneActualConnectionSelectsR8TwoThirdsFieldTier(GameTestHelper h) {
        var localA=new BlockPos(2,2,2); var localB=new BlockPos(6,2,2);
        h.setBlock(localA,ModBlocks.FIELD_GENERATOR.get()); h.setBlock(localB,ModBlocks.FIELD_GENERATOR.get());
        var level=h.getLevel();
        var a=(FieldGeneratorBlockEntity)level.getBlockEntity(h.absolutePos(localA));
        var b=(FieldGeneratorBlockEntity)level.getBlockEntity(h.absolutePos(localB));
        check(h,a!=null&&b!=null,"both registered fixture block entities exist");
        for(var generator:List.of(a,b)) { generator.receiveEmitterBolt(); generator.receiveEmitterBolt(); check(h,generator.toggleEnabled(),"fixture generator enabled"); }
        FieldContainmentRuntime.refresh(level); FieldGeneratorBlockEntity.tick(level,a); FieldGeneratorBlockEntity.tick(level,b);
        check(h,FieldContainmentRuntime.currentEdges(level).stream().filter(e->e.first().equals(a.getBlockPos())||e.second().equals(a.getBlockPos())).count()==1,"one genuine graph edge resolved");
        var projected=a.getBlockState();
        check(h,projected.getValue(com.juicyslew.moonstation14.block.custom.FieldGeneratorBlock.FIELD)==2,"one connection selects a2 two-thirds field artwork");
        check(h,projected.getValue(com.juicyslew.moonstation14.block.custom.FieldGeneratorBlock.POWER)==2&&projected.getValue(com.juicyslew.moonstation14.block.custom.FieldGeneratorBlock.ACTIVE),"field tier leaves medium-charge tier and connected lamp independent");
        h.succeed();
    }

    @GameTestTags({"reactor-contact", "reactor-models"})
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=40)
    public static void fieldGeneratorSelectionOutlineIncludesMeshButCollisionUsesMetalOnly(GameTestHelper h) {
        var state=ModBlocks.FIELD_GENERATOR.get().defaultBlockState();
        var pos=h.absolutePos(new BlockPos(2,2,2));
        h.getLevel().setBlock(pos,state,3);
        var context=net.minecraft.world.phys.shapes.CollisionContext.empty();
        var outline=state.getShape(h.getLevel(),pos,context).bounds();
        var collision=state.getCollisionShape(h.getLevel(),pos,context).toAabbs();
        check(h,outline.maxY>=1.25&&outline.minX==0&&outline.maxX==1,
                "interaction outline reaches accepted visible upper elements and spans lobe silhouette");
        check(h,collision.stream().anyMatch(box->box.minX>=.375-1e-6&&box.maxX<=.625+1e-6
                        &&box.maxY>=1.25-1e-6),
                "native collision retains narrow accepted column rather than full-cell placeholder");
        check(h,collision.stream().noneMatch(box->box.minX==0&&box.maxX==1&&box.maxY>=.5),
                "beam-height collision has no artificial full-cell contact face");
        h.succeed();
    }

    @GameTestTags("reactor-models")
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=40)
    public static void paNeighborDoesNotOccludeGroundTopQuad(GameTestHelper h) {
        var level=h.getLevel();
        var pa=h.absolutePos(new BlockPos(2,2,2));
        var ground=pa.below();
        level.setBlock(ground,Blocks.STONE.defaultBlockState(),3);
        level.setBlock(pa,ModBlocks.PA_CONTROL_BOX.get().defaultBlockState(),3);
        boolean shouldRenderPaNeighbor=net.minecraft.world.level.block.Block.shouldRenderFace(
                Blocks.STONE.defaultBlockState(),level,ground,net.minecraft.core.Direction.UP,pa);
        level.setBlock(pa,Blocks.STONE.defaultBlockState(),3);
        boolean shouldRenderOpaqueNeighbor=net.minecraft.world.level.block.Block.shouldRenderFace(
                Blocks.STONE.defaultBlockState(),level,ground,net.minecraft.core.Direction.UP,pa);
        check(h,shouldRenderPaNeighbor,"ground top quad remains rendered under partial PA model");
        check(h,!shouldRenderOpaqueNeighbor,"same ground quad culls against ordinary opaque neighbor control");
        h.succeed();
    }

    @GameTestTags({"reactor-models", "containment"})
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=40)
    public static void emitterAndFieldGeneratorDoNotOccludeGroundTopQuad(GameTestHelper h) {
        var level=h.getLevel();
        var ground=h.absolutePos(new BlockPos(2,1,2));
        var machine=ground.above();
        level.setBlock(ground,Blocks.STONE.defaultBlockState(),3);
        for(var block:List.of(ModBlocks.EMITTER.get(),ModBlocks.FIELD_GENERATOR.get())) {
            var state=block.defaultBlockState();
            level.setBlock(machine,state,3);
            check(h,!state.canOcclude(),block.getDescriptionId()+" actual registered state is non-occluding");
            check(h,net.minecraft.world.level.block.Block.shouldRenderFace(
                    Blocks.STONE.defaultBlockState(),level,ground,net.minecraft.core.Direction.UP,machine),
                    block.getDescriptionId()+" preserves the ground top quad from above");
            var outline=state.getShape(level,machine,net.minecraft.world.phys.shapes.CollisionContext.empty());
            var collision=state.getCollisionShape(level,machine,net.minecraft.world.phys.shapes.CollisionContext.empty());
            check(h,!outline.isEmpty()&&!collision.isEmpty(),block.getDescriptionId()+" retains physical outline and collision geometry");
        }
        level.setBlock(machine,Blocks.STONE.defaultBlockState(),3);
        check(h,!net.minecraft.world.level.block.Block.shouldRenderFace(
                Blocks.STONE.defaultBlockState(),level,ground,net.minecraft.core.Direction.UP,machine),
                "ground top quad culls against opaque stone control above");
        h.succeed();
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=40)
    public static void loadedRegisteredCornersDeriveSixFacesAndPersistLinksUntilBothLow(GameTestHelper h) {
        var generators=ContainmentFixture.placeBox(h); var level=h.getLevel(); var local=new LocalView(h,generators); FieldContainmentRuntime.refresh(level);
        check(h,local.edges().size()==12,"three-dimensional enclosure has all twelve live edge links");
        check(h,local.volumes().size()==1,"eight loaded registered generators derive one closed box");
        check(h,local.faces().size()==6&&local.faces().stream().allMatch(face->face.corners().size()==4&&face.endpointIdentities().size()==4),"six authoritative rectangle records expose corner positions and placement identities");
        var bounds=local.volumes().getFirst();
        double minX=FieldGeometry.center(bounds.min().getX()), maxX=FieldGeometry.center(bounds.max().getX());
        double minY=FieldGeometry.center(bounds.min().getY()), maxY=FieldGeometry.center(bounds.max().getY());
        double minZ=FieldGeometry.center(bounds.min().getZ()), maxZ=FieldGeometry.center(bounds.max().getZ());
        check(h,FieldContainmentRuntime.crossesFace(level,new Vec3(minX-1,minY+2,minZ+2),new Vec3(minX+1,minY+2,minZ+2),0),"fast horizontal segment contacts side face at block-center plane");
        check(h,FieldContainmentRuntime.crossesFace(level,new Vec3(minX+2,minY+1,minZ+2),new Vec3(minX+2,minY-1,minZ+2),0),"segment contacts bottom face");
        check(h,FieldContainmentRuntime.crossesFace(level,new Vec3(minX+2,maxY-1,minZ+2),new Vec3(minX+2,maxY+1,minZ+2),0),"segment contacts top face");
        check(h,FieldContainmentRuntime.crossesFace(level,new Vec3(minX-1,minY+2,minZ+2),new Vec3(minX-0.25,minY+2,minZ+2),0.5),"swept radius contacts side face without center crossing its plane");
        check(h,FieldContainmentRuntime.crossesFace(level,new Vec3(minX+2,maxY+1,minZ+2),new Vec3(minX+2,maxY+0.25,minZ+2),0.5),"swept radius contacts top face without center crossing its plane");
        check(h,!FieldContainmentRuntime.crossesFace(level,new Vec3(minX-0.25,minY+2,minZ+2),new Vec3(minX-0.25,minY+3,minZ+2),0.5),"parallel motion at contact is not frozen");
        check(h,!FieldContainmentRuntime.crossesFace(level,new Vec3(minX-0.25,minY+2,minZ+2),new Vec3(minX-1,minY+2,minZ+2),0.5),"motion away from a face is not reported as collision");
        check(h,!FieldContainmentRuntime.crossesFace(level,new Vec3(minX+1,minY+1,minZ+1),new Vec3(maxX-1,maxY-1,maxZ-1),0),"interior segment does not report a face hit");
        var edge=local.edges().iterator().next();
        var a=(FieldGeneratorBlockEntity)level.getBlockEntity(edge.first()); var b=(FieldGeneratorBlockEntity)level.getBlockEntity(edge.second());
        for(int i=0;i<400;i++) FieldGeneratorBlockEntity.tick(level,a);
        FieldContainmentRuntime.refresh(level);
        check(h,local.edges().contains(edge),"edge remains while endpoints retain valid charge");
        for(var generator:generators) for(int i=0;i<1200;i++) FieldGeneratorBlockEntity.tick(level,generator);
        FieldContainmentRuntime.refresh(level);
        check(h,local.edges().isEmpty(),"links disappear after both endpoints decay below threshold");
        check(h,local.volumes().isEmpty(),"depleted links no longer produce an enclosure");
        h.succeed();
    }

    @GameTestTags("reactor-models")
    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=120)
    public static void nativeFieldIndicatorsProjectIndependentChargeAndActualGraphState(GameTestHelper h) {
        var generators=ContainmentFixture.placeBox(h); var level=h.getLevel();
        FieldContainmentRuntime.refresh(level);
        check(h,FieldContainmentRuntime.currentEdges(level).size()>=12,"fixture produces actual topology edges");
        for(var generator:generators) FieldGeneratorBlockEntity.tick(level,generator);
        var corner=generators.getFirst();
        var state=corner.getBlockState();
        check(h,state.getValue(com.juicyslew.moonstation14.block.custom.FieldGeneratorBlock.ACTIVE),
                "connection light follows actual live graph edge");
        check(h,state.getValue(com.juicyslew.moonstation14.block.custom.FieldGeneratorBlock.FIELD)==3,
                "three actual corner links select a3 independently");
        check(h,state.getValue(com.juicyslew.moonstation14.block.custom.FieldGeneratorBlock.POWER)==2,
                "charge tier is medium at the resolved connection threshold");
        for(var generator:generators) if(generator!=corner) level.setBlock(generator.getBlockPos(),Blocks.AIR.defaultBlockState(),3);
        FieldContainmentRuntime.refresh(level);
        FieldGeneratorBlockEntity.tick(level,corner);
        state=corner.getBlockState();
        check(h,!state.getValue(com.juicyslew.moonstation14.block.custom.FieldGeneratorBlock.ACTIVE),
                "removal drops only the actual-connection light");
        check(h,state.getValue(com.juicyslew.moonstation14.block.custom.FieldGeneratorBlock.FIELD)==1,
                "enabled unconnected generator retains a1 field indicator");
        check(h,state.getValue(com.juicyslew.moonstation14.block.custom.FieldGeneratorBlock.POWER)==2,
                "charge indicator remains visible independent of disconnect");
        var profile=ModFieldGenerators.catalog(level).get(corner.profileId());
        check(h,profile!=null,"decay bound uses the loaded resolved field-generator profile");
        var decay=corner.snapshot();
        int remainingTicks=decay.charge()==0?0:
                ((decay.charge()+profile.decayAmount()-1)/profile.decayAmount())*profile.decayIntervalTicks()
                        -decay.decayElapsedTicks()+1;
        for(int i=0;i<remainingTicks;i++) FieldGeneratorBlockEntity.tick(level,corner);
        check(h,corner.getBlockState().getValue(com.juicyslew.moonstation14.block.custom.FieldGeneratorBlock.POWER)==0,
                "profile-driven charge decay hides the power indicator at zero");
        check(h,corner.getBlockState().getValue(com.juicyslew.moonstation14.block.custom.FieldGeneratorBlock.FIELD)==1,
                "charge decay does not replace independent enabled field state");
        h.succeed();
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=40)
    public static void firstObstructionAndMissingFacesPreventClosureAndRemovalInvalidates(GameTestHelper h) {
        var generators=ContainmentFixture.placeBox(h); var level=h.getLevel(); var local=new LocalView(h,generators);
        BlockPos first=generators.getFirst().getBlockPos();
        h.setBlock(new BlockPos(3,1,1),Blocks.STONE);
        FieldContainmentRuntime.refresh(level);
        check(h,local.edges().size()==11,"first solid obstruction rejects that edge only");
        check(h,local.volumes().isEmpty(),"missing edge prevents box closure");
        check(h,local.faces().size()==4,"four unrelated valid rectangular faces survive one missing edge");
        var survivingFace=local.faces().stream().filter(f->f.axis()==net.minecraft.core.Direction.Axis.Y&&f.plane()==generators.getFirst().getBlockPos().getY()+4).findFirst();
        check(h,survivingFace.isPresent(),"top rectangular face survives bottom-edge loss");
        var f=survivingFace.orElseThrow();
        check(h,FieldContainmentRuntime.crossesFace(level,new Vec3(FieldGeometry.center(f.minV()+2),FieldGeometry.center(f.plane())-1,FieldGeometry.center(f.minU()+2)),new Vec3(FieldGeometry.center(f.minV()+2),FieldGeometry.center(f.plane())+1,FieldGeometry.center(f.minU()+2)),0),"remaining top face remains collision-queryable at generator-center plane");
        h.setBlock(new BlockPos(3,1,1),Blocks.AIR);
        FieldContainmentRuntime.refresh(level);
        check(h,local.volumes().size()==1,"clearing obstruction restores actual loaded-world closure");
        h.setBlock(new BlockPos(3,5,5),Blocks.STONE);
        FieldContainmentRuntime.refresh(level);
        check(h,local.volumes().isEmpty()&&local.faces().size()==4,"missing top edge removes closure and only its adjacent faces");
        h.setBlock(new BlockPos(3,5,5),Blocks.AIR);
        FieldContainmentRuntime.refresh(level);
        // getBlockPos() is already a world/absolute position; helper.setBlock expects fixture-local coordinates.
        level.setBlock(first,Blocks.STONE.defaultBlockState(),3);
        check(h,level.getBlockState(first).is(Blocks.STONE)&&level.getBlockEntity(first)==null,
                "absolute generator coordinate is replaced by a plain solid block");
        FieldContainmentRuntime.refresh(level);
        check(h,local.volumes().isEmpty(),"replacement invalidates removed placement's links and volume");
        check(h,local.edges().stream().noneMatch(e->e.first().equals(first)||e.second().equals(first)),"stale removed identity cannot retain an edge");
        h.succeed();
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=40)
    public static void chunkUnloadForgetsIndexedGeneratorsAndDerivedFaces(GameTestHelper h) {
        var generators=ContainmentFixture.placeBox(h); var level=h.getLevel(); var local=new LocalView(h,generators); FieldContainmentRuntime.refresh(level);
        check(h,!local.faces().isEmpty(),"fixture initially has registered face geometry");
        var chunkPos=new net.minecraft.world.level.ChunkPos(generators.getFirst().getBlockPos());
        var chunkGenerators=local.positions.stream().filter(p->new net.minecraft.world.level.ChunkPos(p).equals(chunkPos)).collect(java.util.stream.Collectors.toSet());
        var retainedEdges=local.edges().stream().filter(e->!chunkGenerators.contains(e.first())&&!chunkGenerators.contains(e.second())).collect(java.util.stream.Collectors.toSet());
        var retainedFaces=local.faces().stream().filter(f->f.corners().stream().noneMatch(chunkGenerators::contains)).collect(java.util.stream.Collectors.toSet());
        try {
            FieldContainmentRuntime.unloadChunk(level,chunkPos);
            FieldContainmentRuntime.refresh(level);
            check(h,local.edges().equals(retainedEdges)&&local.faces().equals(retainedFaces)
                    &&local.volumes().isEmpty(),"chunk lifecycle removal drops only this fixture's topology requiring generators in the unloaded chunk");
        } finally {
            // This test simulates unload without physically unloading the chunk. Restore every
            // still-loaded owner, including any neighboring fixture sharing this chunk.
            var loadedChunk=level.getChunkSource().getChunkNow(chunkPos.x,chunkPos.z);
            if(loadedChunk!=null) loadedChunk.getBlockEntities().values().stream()
                    .filter(FieldGeneratorBlockEntity.class::isInstance).map(FieldGeneratorBlockEntity.class::cast)
                    .filter(generator->level.getBlockEntity(generator.getBlockPos())==generator)
                    .forEach(generator->FieldContainmentRuntime.changed(level,generator.getBlockPos()));
            FieldContainmentRuntime.refresh(level);
        }
        h.succeed();
    }

    @GameTest(templateNamespace="moonstation14",template="atmos_large_empty",timeoutTicks=40)
    public static void planarGraphAndOverlengthGapDoNotMakeLinksOrVolume(GameTestHelper h) {
        BlockPos aLocal=new BlockPos(1,1,1), bLocal=new BlockPos(1,1,10);
        h.setBlock(aLocal,ModBlocks.FIELD_GENERATOR.get()); h.setBlock(bLocal,ModBlocks.FIELD_GENERATOR.get());
        var level=h.getLevel();
        var a=(FieldGeneratorBlockEntity)level.getBlockEntity(h.absolutePos(aLocal));
        var b=(FieldGeneratorBlockEntity)level.getBlockEntity(h.absolutePos(bLocal));
        a.receiveEmitterBolt();a.receiveEmitterBolt();a.toggleEnabled();
        b.receiveEmitterBolt();b.receiveEmitterBolt();b.toggleEnabled();
        FieldContainmentRuntime.refresh(level);
        var local=new LocalView(h,List.of(a,b));
        check(h,local.edges().isEmpty(),"distance greater than resolved maximum does not connect");
        check(h,local.volumes().isEmpty(),"two endpoints cannot claim enclosure");
        h.succeed();
    }
}
