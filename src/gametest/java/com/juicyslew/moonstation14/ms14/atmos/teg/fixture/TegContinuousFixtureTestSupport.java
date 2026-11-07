package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.atmos.piping.VisualPipeBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.ExposedPipeVisualBlock;
import net.minecraft.core.Direction;
import java.util.List;
import java.util.Set;

/** Test-only placement setup for outdoor 48x48 continuous-fixture sites. */
public final class TegContinuousFixtureTestSupport {
    private TegContinuousFixtureTestSupport() { }

    public static String boundedFailureMessage(String message) {
        return com.juicyslew.moonstation14.gametest.support.GameTestFailureMessage.concise(message);
    }

    static void quiesceOperatingHardware(TegContinuousFixtureService.ObservedHardware observed) {
        TegContinuousFixtureService.quiesceObserved(observed);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }

    static void assertThreeHotContacts(ServerLevel level,TegContinuousFixtureService.ObservedHardware observed) {
        var layout=observed.layout();var profile=observed.placement().additional().atmosMachines().stream()
                .filter(machine->layout.hotExchangers().subList(1,3).contains(machine.getBlockPos()))
                .map(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity::resolvedProfile)
                .filter(java.util.Objects::nonNull).findFirst().orElseThrow(()->new net.minecraft.gametest.framework.GameTestAssertException("resolved inner hot exchanger profile unavailable"));
        layout.validateHotExchanger(profile);
        require(layout.hotExchangers().size()==3,"three hot contacts are declared");
        for(int index=0;index<3;index++) {
            BlockPos body=layout.hotExchangers().get(index);
            var machine=java.util.stream.Stream.concat(observed.placement().baseMachines().stream(),observed.placement().additional().atmosMachines().stream())
                    .filter(host->host.getBlockPos().equals(body)).findFirst().orElseThrow(()->new net.minecraft.gametest.framework.GameTestAssertException("hot contact host missing at "+body));
            require(machine.resolvedProfile()!=null&&machine.resolvedProfile().kind()==com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.PASSIVE_HEAT_EXCHANGER,"ordinary resolved passive exchanger at "+body);
            require(level.getBlockState(body).hasProperty(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING)
                    &&level.getBlockState(body).getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING)==Direction.EAST,"east-facing hot contact "+body);
            require(AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level,body).isPresent(),"all three hot contacts face the existing finite burner room");
            for(String name:List.of("inlet","outlet")) {
                var port=machine.gasPorts().stream().filter(candidate->candidate.id().equals(name)).findFirst().orElseThrow(()->new net.minecraft.gametest.framework.GameTestAssertException("resolved "+name+" port missing at "+body));
                Direction expected=name.equals("inlet")?Direction.EAST:Direction.WEST;
                require(port.layer()==PipeTopology.Layer.PRIMARY&&port.directions().equals(Set.of(expected)),"resolved "+name+" port direction/layer at "+body);
                var prototypePort=machine.resolvedProfile().ports().stream().filter(candidate->candidate.name().name().equalsIgnoreCase(name)).findFirst().orElseThrow();
                require(Math.abs(port.volume()-prototypePort.volumeM3())<1e-9,"resolved "+name+" port volume at "+body);
                var component=TegContinuousFixtureService.capturedComponentForPort(observed.topology(),observed.capture(),port.key(body)).orElseThrow();
                int componentIndex=observed.topology().components().indexOf(observed.topology().components().stream().filter(nodes->nodes.contains(port.key(body))).findFirst().orElseThrow());
                require(componentIndex==(name.equals("inlet")?1:2),"hot "+name+" reaches only expected parallel component at "+body);
                require(component.nodes().stream().anyMatch(node->node.key().equals(port.key(body))),"captured exact actual resolved hot port at "+body);
            }
        }
        for(int side:new int[]{-15,-17})for(int y=0;y<=2;y++) {
            BlockPos host=layout.core().offset(side,y,-1);var state=level.getBlockState(host);
            require(level.getBlockEntity(host) instanceof VisualPipeBlockEntity pipe&&pipe.hasLayer(VisualPipeBlockEntity.PRIMARY),"live hot branch host primary lane at "+host);
            boolean up=y<2,down=y>0;
            require(state.getValue(ExposedPipeVisualBlock.UP)==up&&state.getValue(ExposedPipeVisualBlock.DOWN)==down,"reciprocal vertical hot branch masks at "+host);
        }
        require(layout.fueled().roomInterior().size()==27,"parallel contacts preserve 27 burner cells");
    }

    record OutdoorSite(BlockPos origin, int highestMotionBlockingY, int highestOccupiedY) { }

    static OutdoorSite prepareOutdoorFootprint(ServerLevel level, BlockPos templateOrigin) {
        int highestMotion = Integer.MIN_VALUE;
        int highestOccupied = Integer.MIN_VALUE;
        for (int x = 0; x < 48; x++) for (int z = 0; z < 48; z++) {
            int worldX=templateOrigin.getX()+x,worldZ=templateOrigin.getZ()+z;
            highestMotion=Math.max(highestMotion,level.getHeight(Heightmap.Types.MOTION_BLOCKING,worldX,worldZ)-1);
            highestOccupied=Math.max(highestOccupied,level.getHeight(Heightmap.Types.WORLD_SURFACE,worldX,worldZ)-1);
        }
        int highest=Math.max(highestMotion,highestOccupied);
        int y = highest + 2;
        BlockPos origin = new BlockPos(templateOrigin.getX(), y, templateOrigin.getZ());
        if (y < level.getMinBuildHeight() || y + 8 > level.getMaxBuildHeight())
            throw new IllegalStateException("Outdoor continuous fixture site outside build bounds: y=" + y
                    + " maxMotionBlocking=" + highestMotion+" maxOccupied="+highestOccupied);

        // Never clear above template-owned space. The elevated candidate must already be empty.
        for (int x = 0; x < 48; x++) for (int dy = 0; dy < 8; dy++) for (int z = 0; z < 48; z++) {
            BlockPos pos = origin.offset(x, dy, z);
            if (!level.getBlockState(pos).isAir() || level.getBlockEntity(pos) != null
                    || !level.getFluidState(pos).isEmpty())
                throw new IllegalStateException("Outdoor continuous fixture volume obstructed at " + pos
                        + " state=" + level.getBlockState(pos));
        }
        for (int x = 0; x < 48; x++) for (int z = 0; z < 48; z++) {
            BlockPos floor = origin.offset(x, 1, z);
            if (!level.getBlockState(floor).isAir() || level.getBlockEntity(floor) != null
                    || !level.getFluidState(floor).isEmpty())
                throw new IllegalStateException("Outdoor continuous foundation is obstructed at " + floor
                        + " state=" + level.getBlockState(floor));
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
        }
        return new OutdoorSite(origin, highestMotion, highestOccupied);
    }

    static TegContinuousFuelPlan.Layout prepareOutdoorContinuousSite(ServerLevel level, BlockPos templateOrigin,
                                                                      AtmosMachineProfile mixer, AtmosMachineProfile radiator) {
        BlockPos origin=prepareOutdoorFootprint(level,templateOrigin).origin();
        var layout=TegContinuousFuelPlan.layout(origin,mixer,radiator);
        Set<BlockPos> planned=new java.util.LinkedHashSet<>(layout.fueled().allOwnedCells());
        planned.addAll(layout.roomA());planned.addAll(layout.roomB());planned.addAll(layout.shellA());planned.addAll(layout.shellB());
        planned.addAll(layout.machines());planned.addAll(layout.outsideVent());layout.routes().values().forEach(planned::addAll);
        TegContinuousPowerPlan.cables(origin).forEach(c->planned.add(c.host()));
        for(int x=0;x<TegContinuousFuelPlan.WIDTH;x++)for(int y=2;y<TegContinuousFuelPlan.HEIGHT;y++)
            for(int z=0;z<TegContinuousFuelPlan.DEPTH;z++)planned.add(origin.offset(x,y,z));
        String refusal=TegFixturePlacement.preflight(level,origin,planned,TegFueledFixturePlan.foundation(origin),TegContinuousFuelPlan.HEIGHT);
        if(refusal!=null)throw new IllegalStateException("continuous outdoor preflight refused: "+refusal);
        refusal=TegFixturePlacement.continuousExteriorFailure(level,layout);
        if(refusal!=null)throw new IllegalStateException(exteriorDiagnostic(level,layout,"continuous outdoor exterior preflight refused: "+refusal));
        return layout;
    }

    static String exteriorDiagnostic(ServerLevel level, TegContinuousFuelPlan.Layout layout, String failure) {
        BlockPos radiator = layout.radiatorPositions().iterator().next();
        BlockPos wasteFront = layout.wasteVentBody().relative(layout.wasteVentFacing());
        return failure + "; radiator=" + describe(level, radiator) + "; wasteFront=" + describe(level, wasteFront);
    }

    private static String describe(ServerLevel level, BlockPos pos) {
        return pos + " state=" + level.getBlockState(pos) + " air=" + level.getBlockState(pos).isAir()
                + " loaded=" + level.hasChunkAt(pos) + " outsideBuild=" + level.isOutsideBuildHeight(pos)
                + " withinBorder=" + level.getWorldBorder().isWithinBounds(pos)
                + " immediateExterior=" + AtmosphereService.INSTANCE.exteriorBoundaryForImmediateTransfer(level, pos).isPresent();
    }
}
