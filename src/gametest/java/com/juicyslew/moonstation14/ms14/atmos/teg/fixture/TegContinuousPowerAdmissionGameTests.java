package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.ms14.power.graph.LoadedPowerGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.*;

@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class TegContinuousPowerAdmissionGameTests {
    private TegContinuousPowerAdmissionGameTests() { }

    @GameTest(template="disposal_power_clear", templateNamespace="moonstation14", timeoutTicks=300)
    @GameTestTags({"teg-fixture-construction", "apc-connected-overload"})
    public static void topologyOnlyApcAdmissionWorksBeforeBootstrapAndRejectsAmbiguousProviders(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel();
        BlockPos template=helper.absolutePos(BlockPos.ZERO),far=template.offset(48,8,48);
        var lease=FixtureChunkTickets.acquire(helper,new AABB(template.getX(),template.getY(),template.getZ(),far.getX(),far.getY(),far.getZ()));
        FixtureChunkTickets.awaitEntityTicking(helper,lease,40,()->true,"continuous power footprint entity-ticking",()->{
            try {
                AtmosMachineProfile mixer=ModAtmosMachines.catalog(level).get(ModAtmosMachines.id("gas_mixer"));
                AtmosMachineProfile radiator=ModAtmosMachines.catalog(level).get(ModAtmosMachines.id("passive_heat_exchanger"));
                require(mixer!=null&&radiator!=null,"resolved continuous machine profiles");
                var layout=TegContinuousFixtureTestSupport.prepareOutdoorContinuousSite(level,template,mixer,radiator);
                BlockPos origin=layout.origin();
                var placed=TegFixturePlacement.placeContinuousHardware(level,layout);
                require(placed!=null,"strict continuous hardware admission completed");
                require(TegFixtureSavedData.existing(level).flatMap(data->data.entry(origin)).isEmpty(),
                        "power topology probe creates no saved fixture entry");
                var devices=layout.fueled().electrical().devices();
                var output=(PowerDeviceBlockEntity)level.getBlockEntity(devices.outputApc());
                require(output!=null&&output.energyJoules()==0,"output APC starts uncharged");
                var expectedRoster=TegContinuousPowerPlan.startupReceiverPositions(layout);
                var readOnlyHardware=TegContinuousFixtureService.observeHardware(level,layout,placed);
                var actualRoster=TegContinuousFixtureService.startupAdmissionReceivers(level,readOnlyHardware);
                require(TegContinuousPowerPlan.exactReceiverRoster(expectedRoster.mainApc(),TegContinuousPowerPlan.receiverIdentities(actualRoster)),
                        "read-only production admission roster exactly matches all typed main receiver positions/kinds: expected="+expectedRoster.mainApc()+" actual="+actualRoster);
                require(expectedRoster.mainApc().size()==11,"typed plan resolves eleven main APC receivers: "+expectedRoster.mainApc());
                require(expectedRoster.secondaryApc().size()==1,"typed plan resolves one separately powered auxiliary heater");
                BlockPos auxiliaryHeater=expectedRoster.secondaryApc().keySet().iterator().next();
                BlockPos auxiliaryApc=TegContinuousPowerPlan.equipment(layout).secondaryStartupApc();
                require(expectedRoster.mainApc().keySet().stream().noneMatch(auxiliaryHeater::equals),"auxiliary heater is excluded from the main-APC roster");
                require(level.getBlockEntity(auxiliaryApc) instanceof PowerDeviceBlockEntity aux&&aux.energyJoules()==0,
                        "auxiliary APC remains uncharged before one-shot resource admission");
                List<BlockPos> receivers=expectedRoster.mainApc().keySet().stream().sorted(Comparator.comparingLong(BlockPos::asLong)).toList();
                require(layout.hotExchangers().subList(1,3).stream().noneMatch(receivers::contains),"inner parallel hot exchangers add no APC receivers");
                waitForUnique(helper,level,devices,output,layout,placed,lease,receivers,auxiliaryHeater,auxiliaryApc,level.getGameTime()+200);
            } catch(Throwable failure) { lease.close(); helper.fail("continuous APC admission setup failed: "+failure.getMessage()); }
        },template);
    }

    private static void waitForUnique(GameTestHelper helper,ServerLevel level,TegFixturePlan.Devices devices,
                                      PowerDeviceBlockEntity output,TegContinuousFuelPlan.Layout layout,
                                      TegFixturePlacement.ContinuousHardware hardware,FixtureChunkTickets.Lease lease,List<BlockPos> receivers,
                                      BlockPos auxiliaryHeater,BlockPos auxiliaryApc,long deadline) {
        helper.runAfterDelay(1,()->{
            RogueApc rogue=null;
            try {
                var gasBefore=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                var queries=queryAll(level,receivers);
                var auxiliary=PowerRuntime.apcConnection(level,level.getBlockEntity(auxiliaryHeater));
                if(!allKnownAt(queries,devices.outputApc())||!auxiliary.known()||!auxiliaryApc.equals(auxiliary.provider())) {
                    if(level.getGameTime()>=deadline)throw new GameTestAssertException("power topology did not settle uniquely: "+queries);
                    waitForUnique(helper,level,devices,output,layout,hardware,lease,receivers,auxiliaryHeater,auxiliaryApc,deadline);return;
                }
                assertReadOnly(level,output,(PowerDeviceBlockEntity)level.getBlockEntity(auxiliaryApc),gasBefore);
                assertAdmission(level,layout,hardware,TegContinuousFixtureService.PowerAdmissionStatus.READY);
                rogue=placeRogueApcOnMainCableIsland(level,layout,devices,output);
                trace(level,helper,"duplicate-place","AFTER",rogue.position());
                require(level.getBlockEntity(rogue.position()) instanceof PowerDeviceBlockEntity duplicate
                                &&duplicate.energyJoules()==0,"main-island rogue APC starts uncharged at its clear planned contact");
                waitForAmbiguous(helper,level,devices,output,layout,hardware,lease,receivers,auxiliaryHeater,auxiliaryApc,rogue,deadline);
            } catch(Throwable failure) { if(rogue!=null)removeRogueApc(level,rogue);lease.close();helper.fail("continuous APC admission proof failed: "+failure.getMessage()); }
        });
    }

    private static void waitForAmbiguous(GameTestHelper helper,ServerLevel level,TegFixturePlan.Devices devices,
                                          PowerDeviceBlockEntity output,TegContinuousFuelPlan.Layout layout,
                                          TegFixturePlacement.ContinuousHardware hardware,FixtureChunkTickets.Lease lease,List<BlockPos> receivers,
                                          BlockPos auxiliaryHeater,BlockPos auxiliaryApc,RogueApc rogue,long deadline) {
        helper.runAfterDelay(1,()->{
            try {
                require(level.getBlockEntity(rogue.position()) instanceof PowerDeviceBlockEntity duplicate
                                &&duplicate.energyJoules()==0,"duplicate-provider APC remains uncharged before query");
                var gasBefore=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                var queries=queryAll(level,receivers);
                var auxiliary=PowerRuntime.apcConnection(level,level.getBlockEntity(auxiliaryHeater));
                boolean ready=com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyReady(level);
                if(!ready || queries.values().stream().anyMatch(q->!"provider-ambiguous".equals(q.reason()))
                        ||!auxiliary.known()||!auxiliaryApc.equals(auxiliary.provider())) {
                    if(level.getGameTime()>=deadline)throw new GameTestAssertException("second APC topology did not become strictly ambiguous: "+queries);
                    waitForAmbiguous(helper,level,devices,output,layout,hardware,lease,receivers,auxiliaryHeater,auxiliaryApc,rogue,deadline);return;
                }
                assertReadOnly(level,output,(PowerDeviceBlockEntity)level.getBlockEntity(auxiliaryApc),gasBefore);
                var observed=TegContinuousFixtureService.observeHardware(level,layout,hardware);
                var admission=TegContinuousFixtureService.startupPowerAdmission(level,observed);
                require(admission.status()==TegContinuousFixtureService.PowerAdmissionStatus.REFUSED
                                &&admission.reason().contains("provider-ambiguous"),
                        "admission rejects specifically because a main-island receiver has two providers, not a stale layout or auxiliary provider: "+admission);
                require(((PowerDeviceBlockEntity)level.getBlockEntity(rogue.position())).energyJoules()==0,
                        "ambiguous-provider queries do not mutate duplicate APC energy");
                removeRogueApc(level,rogue);
                trace(level,helper,"duplicate-remove","AFTER",rogue.position());
                waitForRestored(helper,level,devices,output,layout,hardware,lease,receivers,auxiliaryHeater,auxiliaryApc,deadline);
            } catch(Throwable failure) { removeRogueApc(level,rogue);lease.close();helper.fail("ambiguous-provider phase failed: "+failure.getMessage()); }
        });
    }

    private static void waitForRestored(GameTestHelper helper,ServerLevel level,TegFixturePlan.Devices devices,
                                        PowerDeviceBlockEntity output,TegContinuousFuelPlan.Layout layout,
                                        TegFixturePlacement.ContinuousHardware hardware,FixtureChunkTickets.Lease lease,List<BlockPos> receivers,
                                        BlockPos auxiliaryHeater,BlockPos auxiliaryApc,long deadline) {
        helper.runAfterDelay(1,()->{
            try {
                var gasBefore=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                var queries=queryAll(level,receivers);
                var auxiliary=PowerRuntime.apcConnection(level,level.getBlockEntity(auxiliaryHeater));
                if(!com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyReady(level)
                        || !allKnownAt(queries,devices.outputApc())||!auxiliary.known()||!auxiliaryApc.equals(auxiliary.provider())) {
                    if(level.getGameTime()>=deadline)throw new GameTestAssertException("provider did not recover after APC removal: "+queries);
                    waitForRestored(helper,level,devices,output,layout,hardware,lease,receivers,auxiliaryHeater,auxiliaryApc,deadline);return;
                }
                assertReadOnly(level,output,(PowerDeviceBlockEntity)level.getBlockEntity(auxiliaryApc),gasBefore);
                assertAdmission(level,layout,hardware,TegContinuousFixtureService.PowerAdmissionStatus.READY);
                  trace(level,helper,"phase-completion","BEFORE",devices.outputApc()); lease.close();
                  if(!lease.wakeAnchorClaimReleased())throw new GameTestAssertException("power-admission fixture wake-anchor claim was not released");
                  helper.succeed();
            } catch(Throwable failure) { lease.close();helper.fail("provider-restoration phase failed: "+failure.getMessage()); }
        });
    }

    private static Map<BlockPos,PowerRuntime.ApcConnectionObservation> queryAll(ServerLevel level,List<BlockPos> positions) {
        Map<BlockPos,PowerRuntime.ApcConnectionObservation> result=new LinkedHashMap<>();
        for(BlockPos pos:positions)result.put(pos,PowerRuntime.apcConnection(level,level.getBlockEntity(pos)));
        return result;
    }
    private static boolean allKnownAt(Map<BlockPos,PowerRuntime.ApcConnectionObservation> queries,BlockPos provider) {
        return !queries.isEmpty()&&queries.values().stream().allMatch(q->q.known()&&provider.equals(q.provider())
                &&queries.values().stream().allMatch(other->other.topologyGeneration()==q.topologyGeneration()));
    }
    private static void assertReadOnly(ServerLevel level,PowerDeviceBlockEntity output,PowerDeviceBlockEntity auxiliary,
                                       com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventory gasBefore) {
        require(output.energyJoules()==0,"connection query did not change APC charge");
        require(auxiliary!=null&&auxiliary.energyJoules()==0,"connection query did not change auxiliary APC charge");
        require(PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().equals(gasBefore),"connection query did not change pipe inventory");
    }
    private static void assertAdmission(ServerLevel level,TegContinuousFuelPlan.Layout layout,
                                        TegFixturePlacement.ContinuousHardware hardware,
                                        TegContinuousFixtureService.PowerAdmissionStatus expected) {
        var observed=TegContinuousFixtureService.observeHardware(level,layout,hardware);
        var split=TegContinuousFixtureService.startupBootstrapSplit(observed);
        if(expected==TegContinuousFixtureService.PowerAdmissionStatus.READY) {
            require(split.operatingDemandWatts()==6_725d,"resolved main circuit roster is 6,725 W including its original heater");
            require(split.secondaryDemandWatts()==5_000d,"resolved auxiliary circuit roster contains only the second 5,000 W heater");
        }
        var admission=TegContinuousFixtureService.startupPowerAdmission(level,observed);
        require(admission.status()==expected,"startup admission has expected split-provider result "+expected+", got "+admission.status()+" ("+admission.reason()+")");
    }
    private record RogueApc(BlockPos position,CableFaceNode outputContact,boolean supportWasAir) { }
    /** Adds one ordinary, supported APC output face adjacent to a live canonical main-APC cable node. */
    private static RogueApc placeRogueApcOnMainCableIsland(ServerLevel level,TegContinuousFuelPlan.Layout layout,
                                                            TegFixturePlan.Devices devices,PowerDeviceBlockEntity mainApc) {
        var ports=PowerDeviceKind.APC.ports(mainApc.getBlockPos(),level.getBlockState(mainApc.getBlockPos())
                .getValue(PowerDeviceBlock.FACING));
        DevicePort mainOutput=ports.stream().filter(port->port.tier()==CableTier.APC).findFirst().orElseThrow();
        var mainState=PowerGraphService.state(level,mainOutput);
        if(mainState.knowledge()!=LoadedPowerGraph.Knowledge.KNOWN)throw new GameTestAssertException("main APC output component is not known before ambiguity mutation");
        var layoutCables=TegContinuousPowerPlan.cables(layout.origin());
        Set<BlockPos> protectedCells=new HashSet<>(layout.fueled().allOwnedCells());
        protectedCells.addAll(layout.roomA());protectedCells.addAll(layout.roomB());protectedCells.addAll(layout.shellA());protectedCells.addAll(layout.shellB());
        protectedCells.addAll(layout.machines());protectedCells.addAll(layout.presentationSigns().keySet());protectedCells.addAll(layout.outsideVent());
        protectedCells.addAll(layout.wasteMountWall());layout.routes().values().forEach(protectedCells::addAll);
        layoutCables.forEach(cable->protectedCells.add(cable.host()));protectedCells.add(devices.startupApc());
        for(CableFaceNode bus:layoutCables) {
            if(bus.tier()!=CableTier.APC||bus.face()!=Direction.UP
                    ||!com.juicyslew.moonstation14.ms14.power.cable.CableStorage.has(level,bus.host(),bus.face(),bus.tier()))continue;
            var busState=PowerGraphService.state(level,bus);
            if(busState.knowledge()!=LoadedPowerGraph.Knowledge.KNOWN||busState.componentId()!=mainState.componentId())continue;
            BlockPos rogueApc=bus.host().above(),contactHost=rogueApc.relative(Direction.EAST);
            if(protectedCells.contains(rogueApc)||protectedCells.contains(contactHost)
                    ||!level.getBlockState(rogueApc).isAir()||level.getBlockEntity(rogueApc)!=null
                    ||!level.getBlockState(contactHost).isAir()||level.getBlockEntity(contactHost)!=null
                    ||!level.getFluidState(contactHost).isEmpty())continue;
            CableFaceNode outputContact=new CableFaceNode(contactHost,Direction.WEST,CableTier.APC);
            if(PowerTopology.cableEdge(bus,outputContact).isEmpty())continue;
            boolean supportWasAir=level.getBlockState(contactHost).isAir();
            if(!PowerDeviceFixturePlacement.ensureApcSupport(level,rogueApc,Direction.WEST))continue;
            if(!com.juicyslew.moonstation14.ms14.power.cable.CableStorage.place(level,contactHost,outputContact.face(),CableTier.APC)) {
                if(supportWasAir)level.setBlock(contactHost,Blocks.AIR.defaultBlockState(),3);
                continue;
            }
            if(!PowerDeviceFixturePlacement.placeApc(level,rogueApc,Direction.WEST)) {
                if(level.getBlockState(rogueApc).is(ModBlocks.APC.get()))level.setBlock(rogueApc,Blocks.AIR.defaultBlockState(),3);
                com.juicyslew.moonstation14.ms14.power.cable.CableStorage.remove(level,contactHost,outputContact.face(),CableTier.APC);
                if(supportWasAir)level.setBlock(contactHost,Blocks.AIR.defaultBlockState(),3);
                continue;
            }
            var entity=(PowerDeviceBlockEntity)level.getBlockEntity(rogueApc);entity.setEnergyJoules(0);
            return new RogueApc(rogueApc,outputContact,supportWasAir);
        }
        throw new GameTestAssertException("no clear, supported side-contact site on the live main APC cable island for the ambiguity probe");
    }
    private static void removeRogueApc(ServerLevel level,RogueApc rogue) {
        if(level.getBlockState(rogue.position()).is(ModBlocks.APC.get()))level.setBlock(rogue.position(),Blocks.AIR.defaultBlockState(),3);
        if(com.juicyslew.moonstation14.ms14.power.cable.CableStorage.has(level,rogue.outputContact().host(),rogue.outputContact().face(),CableTier.APC)) {
            var removed=com.juicyslew.moonstation14.ms14.power.cable.CableStorage.remove(level,rogue.outputContact().host(),rogue.outputContact().face(),CableTier.APC);
            if(removed!=CableTier.APC)throw new GameTestAssertException("rogue cleanup did not remove its exact APC-tier cable");
        }
        if(rogue.supportWasAir()&&java.util.Arrays.stream(Direction.values()).noneMatch(face->
                com.juicyslew.moonstation14.ms14.power.cable.CableStorage.get(level,rogue.outputContact().host(),face)!=null))
            level.setBlock(rogue.outputContact().host(),Blocks.AIR.defaultBlockState(),3);
    }
    private static void require(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
    private static void trace(ServerLevel level,GameTestHelper helper,String op,String edge,BlockPos pos) {
        MoonStation14.LOGGER.info("TEG_POWER_MUTATION case=topologyOnlyApcAdmissionWorksBeforeBootstrapAndRejectsAmbiguousProviders op={} edge={} root={} template={} pos={} worldTick={} sampleTick={} generation={} graph={}",
                op,edge,helper.absolutePos(BlockPos.ZERO),"disposal_power_clear",pos,level.getGameTime(),PowerRuntime.currentSampleTick(level),
                com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyGeneration(level),
                com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.diagnosticSnapshot(level));
    }
}
