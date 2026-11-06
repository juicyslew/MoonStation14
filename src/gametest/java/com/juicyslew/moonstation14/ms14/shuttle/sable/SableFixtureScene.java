package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import org.joml.Vector3d;
import java.util.*;

/** Owned, bounded world scene setup shared by Sable GameTests; never changes world spawn or global physics. */
public final class SableFixtureScene {
    private static final Map<ServerLevel, Set<Region>> ACTIVE = new IdentityHashMap<>();
    /** Bounded chunk-promotion allowance for startup scenes under concurrent native fixtures. */
    public static final int READINESS_TICKS = 120;

    private SableFixtureScene() { }

    static int supportSurfaceY(BlockPos origin) { return origin.getY(); }

    static int supportBottomY(BlockPos origin,int supportThickness) {
        if(supportThickness<1)throw new IllegalArgumentException("support thickness must be positive");
        return origin.getY()-supportThickness;
    }

    static BlockPos supportCell(BlockPos origin,int x,int layer,int z) {
        if(layer<1)throw new IllegalArgumentException("support layer must be positive");
        return origin.offset(x,-layer,z);
    }

    public static Region acquire(GameTestHelper helper, BlockPos origin, int width, int height, int depth) {
        return acquire(helper,origin,width,height,depth,1);
    }

    static Region acquire(GameTestHelper helper, BlockPos origin, int width, int height, int depth,
                          int supportThickness) {
        if (width < 1 || height < 1 || depth < 1) throw new IllegalArgumentException("scene dimensions must be positive");
        if (supportThickness < 1) throw new IllegalArgumentException("support thickness must be positive");
        BlockPos min = origin.below(supportThickness);
        BlockPos max = origin.offset(width - 1, height - 1, depth - 1);
        ServerLevel level=helper.getLevel();
        Region region = new Region(origin.immutable(), min.immutable(), max.immutable(), width, height, depth,
                supportThickness);
        region.owner=helper.testInfo.toString();
        admit(level,region);
        try { region.tickets=FixtureChunkTickets.around(helper, min, max, 1); }
        catch(RuntimeException|Error failure) { release(level,region); throw failure; }
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo ignored) { }
            @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { cleanup(level,region); }
            @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { cleanup(level,region); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
                cleanup(level,region);
            }
        });
        return region;
    }

    static void admit(ServerLevel level,Region region) {
        synchronized (ACTIVE) {
            Set<Region> regions=ACTIVE.computeIfAbsent(level, ignored -> Collections.newSetFromMap(new IdentityHashMap<>()));
            for(Region active:regions) if(overlaps(region.min,region.max,active.min,active.max))
                throw new IllegalStateException("Overlapping active Sable fixture scenes: requested="+region.min+".."+region.max
                        +", owner="+active.owner+" region="+active.min+".."+active.max);
            regions.add(region);
        }
    }

    static boolean overlaps(BlockPos a,BlockPos b,BlockPos c,BlockPos d) {
        return a.getX()<=d.getX()&&b.getX()>=c.getX()&&a.getY()<=d.getY()&&b.getY()>=c.getY()
                &&a.getZ()<=d.getZ()&&b.getZ()>=c.getZ();
    }
    static void release(ServerLevel level,Region region) {
        synchronized(ACTIVE) { Set<Region> set=ACTIVE.get(level); if(set!=null){set.remove(region);if(set.isEmpty())ACTIVE.remove(level);} }
    }
    private static void cleanup(ServerLevel level,Region region) {
        try { clearStoneSupportPad(level,region); } finally { release(level,region); }
    }

    public static void awaitEmpty(GameTestHelper helper, Region region, int maxWaitTicks, Runnable ready) {
        String testName=helper.testInfo.toString().toLowerCase(Locale.ROOT);
        boolean diagnosticTarget=testName.contains("supportedinteriorvanillapigremainscarriedundercontrolimpulse")
                || testName.contains("puddleoverflowfindsoffsetyawedlowerframebesidelocalneighbor");
        long startTick=helper.getTick();
        boolean[] readyDispatched={false};
        if(diagnosticTarget) helper.runAfterDelay(10,()->{
            if(readyDispatched[0]||region.tickets.isClosed()) return;
            String failure=firstReadinessFailure(helper.getLevel(),region);
            long elapsed=helper.getTick()-startTick;
            System.out.println("CHUNK_READINESS_DIAGNOSTIC phase=PENDING testTick="+helper.getTick()
                    +" elapsed="+elapsed+" owner="+region.owner+" region="+region.min+".."+region.max
                    +" "+(failure==null?"WAITING_DISPATCH":failure)
                    +" leaseMin={"+region.tickets.diagnostic(region.min)+"}"
                    +" leaseMax={"+region.tickets.diagnostic(region.max)+"}");
        });
        FixtureChunkTickets.awaitEntityTicking(helper, region.tickets, maxWaitTicks,
                () -> firstReadinessFailure(helper.getLevel(),region)==null,
                () -> "bounded empty Sable world scene; "+readinessDescription(helper.getLevel(),region),
                () -> { readyDispatched[0]=true; ready.run(); }, region.min, region.max);
    }

    static boolean emptyAndLoaded(ServerLevel level, Region region) {
        return firstReadinessFailure(level,region)==null;
    }

    private static String readinessDescription(ServerLevel level,Region region) {
        String failure=firstReadinessFailure(level,region);
        return failure==null?"all cells clear and loaded; region="+region.min+".."+region.max:failure;
    }

    /** Returns the first failing cell with one bounded cell's observed state, or null when fully ready. */
    private static String firstReadinessFailure(ServerLevel level,Region region) {
        for (int x = region.min.getX(); x <= region.max.getX(); x++)
            for (int y = region.min.getY(); y <= region.max.getY(); y++)
                for (int z = region.min.getZ(); z <= region.max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    boolean ticking=region.tickets.entityTicking(pos);
                    boolean loaded=level.getChunkSource().getChunkNow(x>>4,z>>4)!=null;
                    boolean full=level.getChunkSource().getChunk(x >> 4,z >> 4,ChunkStatus.FULL,false)!=null;
                    String reason=!ticking?"entity-ticking":!full?"full-chunk":null;
                    if(reason!=null) return "firstFailure="+reason+", position="+pos+", region="+region.min+".."+region.max
                            +", observed={ticketLoaded="+loaded+", ticketEntityTicking="+ticking+", "+region.tickets.diagnostic(pos)+"}";
                    boolean border=level.getWorldBorder().isWithinBounds(pos);
                    boolean height=pos.getY()>=level.getMinBuildHeight()&&pos.getY()<level.getMaxBuildHeight();
                    var state=level.getBlockState(pos);
                    var fluid=level.getFluidState(pos);
                    var blockEntity=level.getBlockEntity(pos);
                    reason=!border?"world-border":!height?"build-height":
                            !state.isAir()?"occupied":!fluid.isEmpty()?"fluid":blockEntity!=null?"block-entity":null;
                    if(reason!=null) return "firstFailure="+reason+", position="+pos+", region="+region.min+".."+region.max
                            +", observed={block="+state+", fluid="+fluid+", blockEntity="+(blockEntity==null?"none":blockEntity.getType())
                            +", ticketLoaded="+loaded+", ticketEntityTicking="+ticking+"}";
                }
        return null;
    }

    static void authorStoneSupportPad(ServerLevel level, Region region) {
        String failure=firstReadinessFailure(level,region);
        if (failure!=null) throw new IllegalStateException("Sable support-pad scene is not clear/loaded; "+failure);
        for (int x = 0; x < region.width; x++) for (int z = 0; z < region.depth; z++)
            for (int layer=1;layer<=region.supportThickness;layer++) {
            BlockPos pad = region.supportCell(x,layer,z);
            if (!level.setBlock(pad, Blocks.STONE.defaultBlockState(), 3)) {
                clearStoneSupportPad(level,region);
                throw new IllegalStateException("Sable authored support-pad block placement failed at " + pad);
            }
            region.authored.add(pad.immutable());
        }
    }

    /** Verify actual terrain supports every world cell under an authored frame floor footprint. */
    static void requireStoneSupportUnderProjectedFootprint(ServerLevel level, Region region,
            SableFixturePlot plot, BlockPos storage, int width, int depth) {
        if (width < 1 || depth < 1) throw new IllegalArgumentException("projected support footprint dimensions must be positive");
        var pose = plot.subLevel().logicalPose();
        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        for (int x : new int[]{0, width}) for (int z : new int[]{0, depth}) {
            Vector3d corner = pose.transformPosition(new Vector3d(storage.getX() + x, storage.getY(), storage.getZ() + z));
            minX = Math.min(minX, corner.x); maxX = Math.max(maxX, corner.x);
            minY = Math.min(minY, corner.y); minZ = Math.min(minZ, corner.z); maxZ = Math.max(maxZ, corner.z);
        }
        int padY = region.supportBottomY();
        double supportTop = region.supportSurfaceY();
        int minBlockX = net.minecraft.core.BlockPos.containing(minX, 0, minZ).getX();
        int maxBlockX = net.minecraft.core.BlockPos.containing(Math.nextDown(maxX), 0, minZ).getX();
        int minBlockZ = net.minecraft.core.BlockPos.containing(0, 0, minZ).getZ();
        int maxBlockZ = net.minecraft.core.BlockPos.containing(0, 0, Math.nextDown(maxZ)).getZ();
        if (minY < supportTop - 1.0e-4 || minBlockX < region.origin.getX()
                || maxBlockX >= region.origin.getX() + region.width
                || minBlockZ < region.origin.getZ() || maxBlockZ >= region.origin.getZ() + region.depth)
            throw new GameTestAssertException("projected frame floor starts penetrating or outside its support pad; "
                    + "frame=" + plot.engineId() + ", storage=" + storage + ", worldBounds=["
                    + minX + "," + minY + "," + minZ + "] -> [" + maxX + ",...," + maxZ
                    + "], supportTop=" + supportTop + ", scene=" + region.origin + ".."
                    + region.origin.offset(region.width - 1, 0, region.depth - 1));
        for (int x = minBlockX; x <= maxBlockX; x++) for (int z = minBlockZ; z <= maxBlockZ; z++) {
            BlockPos pad = new BlockPos(x,padY,z);
            if (!region.tickets.entityTicking(pad) || !level.getBlockState(pad).is(Blocks.STONE))
                throw new GameTestAssertException("projected frame floor has missing/non-owned support terrain at "
                        + pad + "; frame=" + plot.engineId() + ", worldBounds=[" + minX + "," + minY + ","
                        + minZ + "] -> [" + maxX + ",...," + maxZ + "], scene=" + region.origin);
        }
    }

    static void clearStoneSupportPad(ServerLevel level, Region region) {
        for (int x = 0; x < region.width; x++) for (int z = 0; z < region.depth; z++)
            for (int layer=1;layer<=region.supportThickness;layer++) {
            BlockPos pad = region.supportCell(x,layer,z);
            if (forgetAuthoredCell(region.authored,pad)) clearOwnedCell(level,pad);
        }
    }

    private static void clearOwnedCell(ServerLevel level,BlockPos pad) {
        if(level.getBlockState(pad).is(Blocks.STONE)) level.setBlock(pad,Blocks.AIR.defaultBlockState(),3);
    }

    static boolean forgetAuthoredCell(Set<BlockPos> authored,BlockPos cell) { return authored.remove(cell); }

    static void trackPlot(GameTestHelper helper, SableFixturePlot plot) {
        helper.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo ignored) { }
            @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { plot.close(); }
            @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { plot.close(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
                plot.close();
            }
        });
    }

    static void awaitStationaryFrame(GameTestHelper helper, SableFixturePlot plot, int ticks, Runnable settled) {
        RigidBodyHandle handle = RigidBodyHandle.of(plot.subLevel());
        Vector3d position = plot.subLevel().logicalPose().position().get(new Vector3d());
        Vector3d linear = handle.getLinearVelocity(new Vector3d());
        Vector3d angular = handle.getAngularVelocity(new Vector3d());
        awaitStationaryFrame(helper, plot, ticks, position, 0, settled);
    }

    private static void awaitStationaryFrame(GameTestHelper helper, SableFixturePlot plot, int ticksLeft,
            Vector3d previous, int stableSamples, Runnable settled) {
        RigidBodyHandle handle = RigidBodyHandle.of(plot.subLevel());
        Vector3d position = plot.subLevel().logicalPose().position().get(new Vector3d());
        Vector3d linear = handle.getLinearVelocity(new Vector3d());
        Vector3d angular = handle.getAngularVelocity(new Vector3d());
        boolean still = linear.lengthSquared() < 1.0e-5 && angular.lengthSquared() < 1.0e-6
                && position.distance(previous) < .005;
        int samples = still ? stableSamples + 1 : 0;
        if (samples >= 3) {
            settled.run();
            return;
        }
        if (ticksLeft <= 0)
            throw new GameTestAssertException("Sable frame failed bounded natural settling; pose="
                    + plot.subLevel().logicalPose() + ", linear=" + linear + ", angular=" + angular);
        helper.runAfterDelay(1, () -> awaitStationaryFrame(helper, plot, ticksLeft - 1,
                position, samples, settled));
    }

    public static final class Region {
        final BlockPos origin;
        final BlockPos min;
        final BlockPos max;
        final int width;
        final int height;
        final int depth;
        final int supportThickness;
        FixtureChunkTickets.Lease tickets;
        final Set<BlockPos> authored=new HashSet<>();
        String owner="starting";

        public BlockPos origin() { return origin; }

        int supportSurfaceY() { return SableFixtureScene.supportSurfaceY(origin); }
        int supportBottomY() { return SableFixtureScene.supportBottomY(origin,supportThickness); }
        BlockPos supportCell(int x,int layer,int z) { return SableFixtureScene.supportCell(origin,x,layer,z); }

        Region(BlockPos origin, BlockPos min, BlockPos max, int width, int height, int depth,
                       int supportThickness) {
            this.origin = origin;
            this.min = min;
            this.max = max;
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.supportThickness = supportThickness;
        }
    }
}
