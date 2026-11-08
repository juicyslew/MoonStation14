package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasPortHost;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasWork;
import com.juicyslew.moonstation14.ms14.atmos.world.ModGasMiners;
import com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class TegContinuousDevicePlacementGameTests {
    private TegContinuousDevicePlacementGameTests() { }

    @GameTest(template="disposal_power_clear", templateNamespace="moonstation14", timeoutTicks=200)
    @GameTestTags("teg-fixture-construction")
    public static void placesOnlyPlannedContinuousDevicesFromResolvedProfiles(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel();
        BlockPos origin=helper.absolutePos(BlockPos.ZERO);
        BlockPos far=origin.offset(48,8,48);
        var lease=FixtureChunkTickets.acquire(helper,new AABB(origin.getX(),origin.getY(),origin.getZ(),far.getX(),far.getY(),far.getZ()));
        FixtureChunkTickets.awaitEntityTicking(helper,lease,40,()->true,"continuous devices are entity-ticking",()->{
            for(int x=0;x<48;x++)for(int y=0;y<8;y++)for(int z=0;z<48;z++)
                level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
            var profiles=ModAtmosMachines.catalog(level);
            AtmosMachineProfile mixer=profiles.get(ModAtmosMachines.id("gas_mixer"));
            AtmosMachineProfile radiator=profiles.get(ModAtmosMachines.id("passive_heat_exchanger"));
            require(mixer!=null&&radiator!=null,"resolved mixer and passive exchanger profiles available");
            var layout=TegContinuousFuelPlan.layout(origin,mixer,radiator);
            var placed=TegFixturePlacement.placeContinuousDevices(level,layout);
            long plannedAtmos=layout.devices().stream().filter(d->d.role().isAtmosMachine()).count();
            long plannedMiners=layout.devices().stream().filter(d->d.role()==TegContinuousFuelPlan.DeviceRole.GAS_MINER_OXYGEN
                    ||d.role()==TegContinuousFuelPlan.DeviceRole.GAS_MINER_PLASMA).count();
            long plannedVentilation=layout.devices().stream().filter(d->d.role()==TegContinuousFuelPlan.DeviceRole.SCRUBBER
                    ||d.role()==TegContinuousFuelPlan.DeviceRole.INJECTOR||d.role()==TegContinuousFuelPlan.DeviceRole.WASTE_PUMP
                    ||d.role()==TegContinuousFuelPlan.DeviceRole.VENT).count();
            require(placed.atmosMachines().size()==plannedAtmos&&placed.miners().size()==plannedMiners&&placed.ventilation().size()==plannedVentilation,
                    "typed return covers every planned machine, miner, and ventilation role");
            Set<BlockPos> expected=new HashSet<>(); layout.devices().forEach(d->expected.add(d.position()));
            Set<BlockPos> actual=new HashSet<>();
            for(var machine:placed.atmosMachines()) { actual.add(machine.getBlockPos()); require(level.getBlockEntity(machine.getBlockPos())==machine,"exact atmos host"); }
            for(var miner:placed.miners()) { actual.add(miner.getBlockPos()); require(level.getBlockEntity(miner.getBlockPos())==miner,"exact miner host"); }
            for(var vent:placed.ventilation()) { actual.add(vent.getBlockPos()); require(level.getBlockEntity(vent.getBlockPos())==vent,"exact ventilation host"); }
            require(actual.equals(expected)&&actual.size()==expected.size(),"exactly the typed plan device positions placed, with no missing or extra hosts");
            for(BlockPos hot:layout.hotExchangers().subList(1,3)) {
                var machine=placed.atmosMachines().stream().filter(candidate->candidate.getBlockPos().equals(hot)).findFirst().orElseThrow();
                require(machine.resolvedProfile()==radiator&&level.getBlockState(hot).getValue(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock.FACING)==net.minecraft.core.Direction.EAST,
                        "inner contact reuses resolved east-facing ordinary passive exchanger at "+hot);
            }

            var minerProfiles=ModGasMiners.catalog(level);
            for(var miner:placed.miners()) {
                var profile=miner.resolvedProfile();
                require(profile!=null,"resolved gas miner profile");
                var block=level.getBlockState(miner.getBlockPos()).getBlock();
                if(block==ModBlocks.GAS_MINER_OXYGEN.get()) {
                    require(minerProfiles.get(ModGasMiners.id("oxygen_station"))==profile&&profile.gas()==GasType.OXYGEN
                                    &&profile.maxExternalPressureKpa()==1000,
                            "oxygen miner uses resolved oxygen-station profile and cutoff");
                } else {
                    require(block==ModBlocks.GAS_MINER_PLASMA.get()&&minerProfiles.get(ModGasMiners.id("plasma_station"))==profile
                                    &&profile.gas()==GasType.PLASMA&&profile.maxExternalPressureKpa()==300,
                            "plasma miner uses resolved plasma-station profile and cutoff");
                }
                require(miner.placementIdentity()!=null&&miner.hasLivePlacementIdentity(),"miner has live placement identity");
            }
            var mixerMachine=placed.atmosMachines().stream().filter(m->m.getBlockState().is(ModBlocks.GAS_MIXER.get())).findFirst().orElseThrow();
            require(mixerMachine.settingsValid()&&mixerMachine.resolvedProfile()!=null
                            &&!mixerMachine.settingsSnapshot().enabled().orElse(true),"mixer is profile-backed and disabled");
            for(VentilationDeviceBlockEntity vent:placed.ventilation()) {
                require(vent.prototype()!=null&&vent.settingsValid()&&!vent.config().settings().configuredEnabled(),
                        "ventilation prototype/settings valid and disabled");
                require(vent.deviceId()!=null,"ventilation device has live identity");
            }
            helper.succeed();
        },origin);
    }

    @GameTest(template="disposal_power_clear", templateNamespace="moonstation14", timeoutTicks=200)
    @GameTestTags("teg-fixture-construction")
    public static void continuousHardwareComposesAdmittedHardwareAndVacuumRooms(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel();
        BlockPos templateOrigin=helper.absolutePos(BlockPos.ZERO), far=templateOrigin.offset(48,8,48);
        var lease=FixtureChunkTickets.acquire(helper,new AABB(templateOrigin.getX(),templateOrigin.getY(),templateOrigin.getZ(),far.getX(),far.getY(),far.getZ()));
        FixtureChunkTickets.awaitEntityTicking(helper,lease,40,()->true,"continuous hardware footprint entity-ticking",()->{
            var catalog=ModAtmosMachines.catalog(level);
            AtmosMachineProfile mixer=catalog.get(ModAtmosMachines.id("gas_mixer"));
            AtmosMachineProfile radiator=catalog.get(ModAtmosMachines.id("passive_heat_exchanger"));
            require(mixer!=null&&radiator!=null,"resolved continuous machine profiles available");
            var layout=TegContinuousFixtureTestSupport.prepareOutdoorContinuousSite(level,templateOrigin,mixer,radiator);
            BlockPos origin=layout.origin();
            var built=TegFixturePlacement.placeContinuousHardware(level,layout);
            require(built.baseMachines().size()==layout.fueled().machines().size()
                            &&built.additional().atmosMachines().size()==layout.devices().stream().filter(d->d.role().isAtmosMachine()).count(),
                    "typed plan finite-base and additional atmos machine hosts returned");
            require(built.core()!=null&&level.getBlockEntity(layout.core())==built.core(),"exact live TEG core returned");
            require(built.additional().miners().size()==2&&built.additional().ventilation().size()==5,
                    "additional miners and ventilation devices returned");
            Map<BlockPos,java.util.List<PipeGasPortHost.Port>> actualGasPorts=new java.util.LinkedHashMap<>();
            java.util.stream.Stream.concat(built.baseMachines().stream(),built.additional().atmosMachines().stream()).forEach(machine->{
                actualGasPorts.put(machine.getBlockPos(),machine.gasPorts().stream().map(port->new PipeGasPortHost.Port(
                        port.id(),port.layer(),port.directions(),port.volume(),port.incarnation())).toList());
            });
            var expectedTopology=TegContinuousFixtureTopology.plan(layout,actualGasPorts);
            var captured=LoadedPipeGasWork.capture(level,expectedTopology.representatives().stream()
                    .map(LoadedPipeGasWork.Port::new).toList());
            require(captured.isPresent()&&!captured.orElseThrow().aliases()
                            &&captured.orElseThrow().components().size()==10,
                    "all eleven expected pipe components are captured independently without aliases");
            Map<PipeTopology.NodeKey,Set<PipeTopology.NodeKey>> actualTopology=new java.util.HashMap<>();
            for(var representative:expectedTopology.representatives()) {
                var component=captured.orElseThrow().componentFor(representative).orElseThrow();
                actualTopology.put(representative,component.nodes().stream().map(node->node.key())
                        .collect(java.util.stream.Collectors.toSet()));
            }
            require(TegFueledFixtureTopology.exact(expectedTopology,actualTopology),
                    "captured live topology exactly matches all eleven expected components");
            for(int side:new int[]{-15,-17})for(int y=0;y<=2;y++) {
                var host=layout.core().offset(side,y,-1);var state=level.getBlockState(host);
                require(state.getValue(com.juicyslew.moonstation14.ms14.atmos.piping.ExposedPipeVisualBlock.UP)==(y<2)
                                &&state.getValue(com.juicyslew.moonstation14.ms14.atmos.piping.ExposedPipeVisualBlock.DOWN)==(y>0),
                        "reciprocal branch visual masks at "+host);
            }
            TegContinuousFixtureTestSupport.assertThreeHotContacts(level,TegContinuousFixtureService.observeHardware(level,layout));
            require(TegFixtureSavedData.existing(level).flatMap(data->data.entry(origin)).isEmpty(),
                    "internal hardware placement does not save a fixture token");
            require(layout.fueled().coldRoomInterior().isEmpty()&&layout.fueled().coldWallShell().isEmpty(),"external cooling does not build a legacy cold-room chamber");
            for(Set<BlockPos> room:java.util.List.of(layout.fueled().roomInterior(),layout.roomA(),layout.roomB()))
                for(BlockPos cell:room) require(com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE
                                .finiteRoomEndpointForTrustedSimulation(level,cell).isPresent(),"owned vacuum endpoint at "+cell);
            require(!level.getBlockState(layout.fueled().electrical().devices().startupApc()).is(ModBlocks.APC.get()),
                    "startup APC is not placed by continuous hardware composition");
            helper.succeed();
        },templateOrigin);
    }

    private static void require(boolean value,String message) { if(!value) throw new GameTestAssertException(message); }
}
