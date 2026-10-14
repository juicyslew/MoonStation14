package com.juicyslew.moonstation14.ms14.singularity.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SingularitySourceVisibilityTest {
    private static final Vec3 CAMERA=new Vec3(.5,.5,-3), CENTER=new Vec3(.5,.5,3);

    @Test void fullStoneWallBlocksAllProbesAndGlassDoesNot() {
        World world=new World();
        assertEquals(1, fraction(world),"an empty world leaves every visibility ray clear");
        for(int x=-2;x<=2;x++) for(int y=-2;y<=2;y++) world.put(new BlockPos(x,y,0),Blocks.STONE.defaultBlockState());
        assertEquals(0, fraction(world));
        world.blocks.clear(); world.put(new BlockPos(0,0,0),Blocks.GLASS.defaultBlockState());
        assertEquals(1, fraction(world));
    }

    @Test void pillarLeavesSomeBillboardProbesVisibleAndUnloadedCellsAreNotRead() {
        World world=new World(); world.put(new BlockPos(0,0,0),Blocks.STONE.defaultBlockState());
        assertEquals(0,fraction(world,1),"a voxel halfway to source covers a half-extent-1 billboard");
        float pillarFraction=fraction(world,2);
        assertTrue(pillarFraction>0 && pillarFraction<1,"pillar visibility fraction="+pillarFraction);
        world.reads=0;
        assertEquals(1, SingularitySourceVisibility.fraction(world,pos->false,CAMERA,CENTER,1,SingularityBillboardBasis.between(CENTER,CAMERA)));
        assertEquals(0,world.reads);
    }

    @Test void opaqueSourceContainingCubeBlocksLevelOneBillboardAtCardinalAndPitchViews() {
        World world=new World();
        Vec3 center=new Vec3(.5,.5,.5);
        for (Vec3 forward : java.util.List.of(new Vec3(0,0,1),new Vec3(0,0,-1),new Vec3(1,0,0),new Vec3(-1,0,0),
                new Vec3(0,1,1).normalize(),new Vec3(0,-1,-1).normalize())) {
            Vec3 camera=center.subtract(forward.scale(3));
            var basis=SingularityBillboardBasis.between(center,camera);
            world.blocks.clear();
            world.put(new BlockPos(0,0,0),Blocks.STONE.defaultBlockState());
            assertEquals(0,SingularitySourceVisibility.fraction(world,pos->true,camera,center,.5f,basis),
                    "original full-cube opacity blocks all nine source-centered probes");
            world.blocks.clear();
            world.put(new BlockPos(0,0,0),Blocks.GLASS.defaultBlockState());
            assertEquals(1,SingularitySourceVisibility.fraction(world,pos->true,camera,center,.5f,basis),
                    "transparent block remains ignored by the generic VISUAL occlusion policy");
        }
    }

    @Test void basisUsesCameraPositionNotLookAndRemainsOrthonormalAtDegeneracies() {
        Vec3 source=Vec3.ZERO, camera=new Vec3(4,3,8);
        var basis=SingularityBillboardBasis.between(source,camera);
        var otherLook=SingularityBillboardBasis.between(source,camera);
        assertEquals(basis,otherLook);
        for (Vec3 point : java.util.List.of(new Vec3(0,1,0),new Vec3(0,-1,0),source)) {
            var b=SingularityBillboardBasis.between(source,point);
            assertTrue(Double.isFinite(b.normal().x+b.right().y+b.up().z));
            assertEquals(1,b.normal().length(),1e-6); assertEquals(1,b.right().length(),1e-6); assertEquals(1,b.up().length(),1e-6);
            assertEquals(0,b.right().dot(b.up()),1e-6); assertEquals(0,b.right().dot(b.normal()),1e-6);
            var rotated=b.rotation().transform(new org.joml.Vector3f(0,0,1));
            assertEquals(b.normal().x,rotated.x,1e-6); assertEquals(b.normal().y,rotated.y,1e-6); assertEquals(b.normal().z,rotated.z,1e-6);
        }
    }

    @Test void cacheSmoothsByStableIdentityAndClearsPrunesAndReachesExactZero() {
        var cache=new SingularitySourceVisibility.Cache(); var id=UUID.randomUUID(); Object world=new Object();
        assertEquals(1,cache.update(id,world,1,0));
        assertTrue(cache.update(id,world,0,60_000_000)>0);
        assertEquals(0,cache.update(id,world,0,2_000_000_000L));
        assertEquals(1,cache.update(id,new Object(),1,3_000_000_000L));
        assertEquals(1,cache.size()); cache.prune(Set.of()); assertEquals(0,cache.size());
        cache.update(id,world,1,4_000_000_000L); cache.clear(); assertEquals(0,cache.size());
    }

    @Test void worldFrameSourceCollectionReusesVisibilityAndDoesNotCaptureSceneColor() throws Exception {
        Path sourcePath=Path.of("src/main/java/com/juicyslew/moonstation14/ms14/singularity/client/SingularityVisualPreview.java");
        for(Path base=Path.of("").toAbsolutePath();!Files.exists(sourcePath)&&base.getParent()!=null;base=base.getParent())
            sourcePath=base.resolve("src/main/java/com/juicyslew/moonstation14/ms14/singularity/client/SingularityVisualPreview.java");
        String source=Files.readString(sourcePath);
        String renderMethod = source.substring(source.indexOf("public static void render("), source.indexOf("/** Frame collector"));
        assertFalse(renderMethod.contains("captureDepth("));
        assertFalse(renderMethod.contains("distort("));
        assertTrue(source.contains("selectWarpSources(liveSources(mc, partialTick), List.copyOf(ANCHORS))"));
        assertTrue(source.contains("VISIBILITY.update(anchor.id"));
        assertTrue(source.contains("profile.cutoffWorldUnits() * com.juicyslew.moonstation14.ms14.singularity.SingularityVisualProfile.PIXELS_PER_METER"));
        assertTrue(source.contains("float effectiveStrength = profile.lensStrength() * visibility"));
    }

    private static float fraction(World world) {
        return fraction(world,1);
    }
    private static float fraction(World world,float halfExtent) {
        return SingularitySourceVisibility.fraction(world,pos->true,CAMERA,CENTER,halfExtent,SingularityBillboardBasis.between(CENTER,CAMERA));
    }

    private static final class World implements BlockGetter {
        final Map<BlockPos,BlockState> blocks=new HashMap<>(); int reads;
        void put(BlockPos pos,BlockState state){blocks.put(pos.immutable(),state);}
        @Override public BlockState getBlockState(BlockPos pos){reads++;return blocks.getOrDefault(pos,Blocks.AIR.defaultBlockState());}
        @Override public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
        @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos){return null;}
        @Override public int getHeight(){return 256;}
        @Override public int getMinBuildHeight(){return 0;}
    }
}
