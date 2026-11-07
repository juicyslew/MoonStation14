package com.juicyslew.moonstation14.ms14.atmos.analyzer;

import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.*;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer;
import com.juicyslew.moonstation14.ms14.hands.network.*;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"atmos-analyzer", "interaction"})
public final class GasAnalyzerPipeUseGameTests {
    private GasAnalyzerPipeUseGameTests() { }
    @GameTest(template="empty",templateNamespace="moonstation14",timeoutTicks=60)
    @GameTestTags({"atmos-analyzer-pipe-use","network"})
    public static void registeredOrdinaryHeldUsePinsVisibleGasPipeWithoutMutatingInventory(GameTestHelper helper) {
        var fixture=RegisteredServerPlayerFixture.register(helper.getLevel(),"analyzer-pipe-world-use-v1");
        helper.runAtTickTime(2,()->{
            try{
                var level=(net.minecraft.server.level.ServerLevel)helper.getLevel();var body=RegisteredPlayerReadyDriver.readyAndQuery(fixture).body();
                var authority=HandActorAuthority.resolve(fixture.player()).orElseThrow();
                var item=LooseItemEntity.create(level,body.position().add(1,0,0),new ItemStack(ModItems.ATMOSPHERE_ANALYZER.get()));item.setNoGravity(true);
                require(level.addFreshEntity(item)&&BodyHandItemTransfer.pickupTrustedCreated(authority,item,item.getItem(),()->true)==BodyHandItemTransfer.Result.SUCCESS,"registered held analyzer");
                var hands=body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                var hand=hands.activeHand();var token=hands.token(hand).orElseThrow();
                require(BodyHandRequestService.apply(fixture.player(),new BodyHandActionRequest(
                        BodyHandActionRequest.Action.USE_IN_HAND,hand,authority.epoch(),1,hands.revision(),null,token)).reason()
                        ==BodyHandActionResult.Reason.OK,"normal hand activation opens air analyzer");
                BlockPos target=helper.absolutePos(new BlockPos(5,2,4));
                level.setBlock(target,com.juicyslew.moonstation14.block.ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(),3);
                var pipe=(VisualPipeBlockEntity)level.getBlockEntity(target);
                require(pipe!=null&&pipe.setLayersForPlacement(VisualPipeBlockEntity.PRIMARY),"visible pipe target");
                var id=LoadedPipeGasOwner.createVacuumLane(level,target,VisualPipeBlockEntity.PRIMARY).orElseThrow();
                var key=new PipeTopology.NodeKey(target,PipeTopology.Layer.PRIMARY);
                var initial=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().get(key).orElseThrow();
                var initialInventory=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                var initialNetwork=initialInventory.network(key).orElseThrow();
                var mixture=new GasMixture(java.util.Map.of(GasType.OXYGEN,.5,GasType.NITROGEN,.2),293.15,initial.volume());
                require(PipeGasFixtureTestSupport.seedLane(level,key,id,initial.version(),mixture),"trusted test-only pipe seed");
                body.setPos(target.getX()-2,target.getY(),target.getZ()+.5);
                var hit=RegisteredPlayerReadyDriver.aimAt(body,level,target);
                BlockPos wall=target.west();BlockPos upperWall=wall.above();
                level.setBlock(wall,com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(),3);
                level.setBlock(upperWall,com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(),3);
                var eye=body.getEyePosition();var rayEnd=eye.add(body.getLookAngle().scale(BodyBlockUseTargetProbe.MAX_REACH));
                var actualRay=level.clip(new net.minecraft.world.level.ClipContext(eye,rayEnd,
                        net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,body));
                var firstHit=actualRay instanceof net.minecraft.world.phys.BlockHitResult blockHit?blockHit.getBlockPos():null;
                require(actualRay instanceof net.minecraft.world.phys.BlockHitResult&&actualRay.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK
                                &&(wall.equals(firstHit)||upperWall.equals(firstHit)),
                        "occlusion ray must first hit wall; eye="+eye+" pipe="+target+" first="+firstHit);
                var blockedRequest=new BodyBlockUseRequest(authority.epoch(),1,hands.revision(),hand,token,target,hit.getDirection(),false,BodyBlockUseRequest.Intent.USE);
                 var blockedOutcome=AuthenticatedBlockUseDriver.use(fixture.player(),blockedRequest);
                 require(blockedOutcome==BodyBlockUseResult.Outcome.TARGET_NOT_ADMITTED,
                         "ordinary target nomination behind opaque wall outcome="+blockedOutcome);
                require(GasAnalyzerSessionService.latestSnapshot(fixture.player()).source()==null,"opaque nomination did not pin or reveal pipe gas");
                level.setBlock(wall,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                level.setBlock(upperWall,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
                var restoredHit=RegisteredPlayerReadyDriver.aimAt(body,level,target);
                require(restoredHit.getBlockPos().equals(target),"pipe remains ordinarily aimable after occluders are removed");
                String otherHand=hands.handIds().stream().filter(h->!h.equals(hand)).findFirst().orElseThrow();
                require(AuthenticatedBlockUseDriver.use(fixture.player(),new BodyBlockUseRequest(authority.epoch(),2,hands.revision(),hand,"wrong-token",target,hit.getDirection(),false,BodyBlockUseRequest.Intent.USE))==BodyBlockUseResult.Outcome.DENIED,
                        "wrong source token cannot scan");
                require(AuthenticatedBlockUseDriver.use(fixture.player(),new BodyBlockUseRequest(authority.epoch(),3,hands.revision()+1,hand,token,target,hit.getDirection(),false,BodyBlockUseRequest.Intent.USE))==BodyBlockUseResult.Outcome.DENIED,
                        "stale source revision cannot scan");
                require(AuthenticatedBlockUseDriver.use(fixture.player(),new BodyBlockUseRequest(authority.epoch(),4,hands.revision(),otherHand,null,target,hit.getDirection(),false,BodyBlockUseRequest.Intent.USE))==BodyBlockUseResult.Outcome.DENIED,
                        "inactive/wrong hand cannot scan");
                var request=new BodyBlockUseRequest(authority.epoch(),5,hands.revision(),hand,token,target,hit.getDirection(),false,BodyBlockUseRequest.Intent.USE);
                if(AuthenticatedBlockUseDriver.use(fixture.player(),request)!=BodyBlockUseResult.Outcome.NO_CHANGE)throw new AssertionError("ordinary authenticated USE admitted the analyzer scan");
                var snapshot=GasAnalyzerSessionService.latestSnapshot(fixture.player());
                require(snapshot.source()==GasAnalyzerPipeSamples.Source.VISIBLE_PIPE&&snapshot.nodeEntries().size()==1
                        &&snapshot.local()==null&&snapshot.target()==null,"wire payload carries only named node samples");
                var node=snapshot.nodeEntries().getFirst();require(id.equals(node.stamp().incarnation()),"exact pipe incarnation returned");
                require(Math.abs(node.sample().volumeLitres()-200)<.001&&Math.abs(node.sample().gases().getFirst().moles()-.5)<.001,"sample uses actual stored node mixture and volume");
                var after=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().get(key).orElseThrow();
                var afterInventory=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                var afterNetwork=afterInventory.network(key).orElseThrow();
                require(initial.version()==after.version(),"gas seeding leaves owner metadata version unchanged");
                require(afterNetwork.id().equals(initialNetwork.id())&&afterNetwork.members().equals(initialNetwork.members())
                        &&afterNetwork.gasRevision()==initialNetwork.gasRevision()+1,"fixture seed advances only shared gas revision");
                BlockPos mixerPos=target.east(2);
                level.setBlock(mixerPos,com.juicyslew.moonstation14.block.ModBlocks.GAS_MIXER.get().defaultBlockState(),3);
                var mixerMachine=(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity)level.getBlockEntity(mixerPos);
                require(mixerMachine!=null,"placed mixer must have its atmosphere-machine block entity");
                mixerMachine.reconcileLoadedPorts(level);
                var host=LoadedDevicePortGasOwner.host(level,mixerPos).orElseThrow();
                var descriptors=host.gasPorts();require(descriptors.size()==3,"three independent mixer ports after canonical loaded-port reconciliation: count="+descriptors.size()+", kind="+mixerMachine.getClass().getSimpleName());
                var seeds=new java.util.ArrayList<TrustedPipeFixtureInitialization.PortSeed>();
                for(int i=0;i<descriptors.size();i++){
                    var port=descriptors.get(i);var composition=switch(i){case 0->java.util.Map.of(GasType.OXYGEN,2d);case 1->java.util.Map.of(GasType.NITROGEN,3d);default->java.util.Map.of(GasType.PLASMA,4d);};
                    seeds.add(new TrustedPipeFixtureInitialization.PortSeed(mixerPos,port.id(),port.incarnation(),new GasMixture(composition,293.15,port.volume())));
                }
                require(TrustedPipeFixtureInitialization.initializeVacuumPortsOnce(level,seeds).successful(),"seed distinct named mixer port mixtures");
                body.setPos(mixerPos.getX()-2,mixerPos.getY(),mixerPos.getZ()+.5);
                var mixerHit=RegisteredPlayerReadyDriver.aimAt(body,level,mixerPos);
                var mixerRequest=new BodyBlockUseRequest(authority.epoch(),6,hands.revision(),hand,token,mixerPos,mixerHit.getDirection(),false,BodyBlockUseRequest.Intent.USE);
                require(AuthenticatedBlockUseDriver.use(fixture.player(),mixerRequest)==BodyBlockUseResult.Outcome.NO_CHANGE,"ordinary held USE scans visible mixer");
                var mixerSnapshot=GasAnalyzerSessionService.latestSnapshot(fixture.player());
                require(mixerSnapshot.source()==GasAnalyzerPipeSamples.Source.DEVICE&&mixerSnapshot.nodeEntries().size()==3,"all named mixer nodes included");
                require(mixerSnapshot.nodeEntries().stream().map(e->e.stamp().node().portId()).distinct().count()==3,"no combined component aggregate");
                require(mixerSnapshot.nodeEntries().stream().map(e->e.sample().gases().getFirst().gas()).distinct().count()==3,"each mixer port preserves its own mixture");
                var mixerSaved=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                require(mixerSnapshot.nodeEntries().stream().allMatch(e->mixerSaved.get(e.stamp().node()).orElseThrow().version()==e.stamp().version()),
                        "read-only scan leaves every port inventory version unchanged");
                body.setPos(mixerPos.getX()-7,mixerPos.getY(),mixerPos.getZ()+.5);
                GasAnalyzerSessionService.serverTick(null);
                var outOfReach=GasAnalyzerSessionService.latestSnapshot(fixture.player());
                require(!outOfReach.closed()&&outOfReach.source()==null,"ordinary reach loss falls back to current air mode instead of stale device samples");
                body.setPos(mixerPos.getX()-2,mixerPos.getY(),mixerPos.getZ()+.5);
                var reachableHit=RegisteredPlayerReadyDriver.aimAt(body,level,mixerPos);
                require(AuthenticatedBlockUseDriver.use(fixture.player(),new BodyBlockUseRequest(authority.epoch(),7,hands.revision(),hand,token,mixerPos,reachableHit.getDirection(),false,BodyBlockUseRequest.Intent.USE))==BodyBlockUseResult.Outcome.NO_CHANGE,
                        "reachable mixer can be rebound after air fallback");
                require(GasAnalyzerSessionService.latestSnapshot(fixture.player()).source()==GasAnalyzerPipeSamples.Source.DEVICE,
                        "ordinary reachable use restores named-device view");
                level.setBlock(mixerPos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                GasAnalyzerSessionService.serverTick(null);
                require(GasAnalyzerSessionService.latestSnapshot(fixture.player()).closed(),"replaced target invalidates exact scan session");

                var ventPos=mixerPos;
                level.setBlock(ventPos,com.juicyslew.moonstation14.block.ModBlocks.GAS_VENT.get().defaultBlockState()
                        .setValue(VentilationDeviceBlock.FACING,Direction.WEST),3);
                var vent=(VentilationDeviceBlockEntity)level.getBlockEntity(ventPos);
                require(vent!=null&&vent.settingsValid(),"resolved vent prototype/settings are valid");
                var connectedPos=ventPos.east();
                level.setBlock(connectedPos,com.juicyslew.moonstation14.block.ModBlocks.GAS_PIPE_VISUAL.get().defaultBlockState(),3);
                var connectedPipe=(VisualPipeBlockEntity)level.getBlockEntity(connectedPos);
                require(connectedPipe!=null&&connectedPipe.setLayersForPlacement(VisualPipeBlockEntity.PRIMARY),"vent connected visual lane");
                var connectedId=LoadedPipeGasOwner.createVacuumLane(level,connectedPos,VisualPipeBlockEntity.PRIMARY).orElseThrow();
                var connectedKey=new PipeTopology.NodeKey(connectedPos,PipeTopology.Layer.PRIMARY);
                var connectedBefore=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().get(connectedKey).orElseThrow();
                var connectedNetworkBefore=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().network(connectedKey).orElseThrow();
                require(PipeGasFixtureTestSupport.seedLane(level,connectedKey,connectedId,connectedBefore.version(),
                        new GasMixture(java.util.Map.of(GasType.OXYGEN,.4),293.15,connectedBefore.volume())),"seed actual vent pipe node");
                body.setPos(ventPos.getX()-2,ventPos.getY(),ventPos.getZ()+.5);
                var ventHit=RegisteredPlayerReadyDriver.aimAt(body,level,ventPos);
                require(AuthenticatedBlockUseDriver.use(fixture.player(),new BodyBlockUseRequest(authority.epoch(),8,hands.revision(),hand,token,
                        ventPos,ventHit.getDirection(),false,BodyBlockUseRequest.Intent.USE))==BodyBlockUseResult.Outcome.NO_CHANGE,"ordinary use scans visible vent");
                var ventScan=GasAnalyzerSessionService.latestSnapshot(fixture.player());
                require(ventScan.source()==GasAnalyzerPipeSamples.Source.UNARY_DEVICE&&ventScan.targetCell().equals(ventPos)
                        &&ventScan.nodeEntries().size()==1&&ventScan.nodeEntries().getFirst().stamp().node().position().equals(connectedPos),
                        "vent reports exactly its adjacent connected node while retaining visible-host target identity");
                require(Math.abs(ventScan.nodeEntries().getFirst().sample().volumeLitres()-200)<.001
                        &&Math.abs(ventScan.nodeEntries().getFirst().sample().gases().getFirst().moles()-.4)<.001,"vent projects own connected node volume and mixture");
                var ventSettings=vent.config().settings();
                var revisedSettings=new VentilationDeviceConfig.Settings(ventSettings.revision(),ventSettings.configuredEnabled(),
                        ventSettings.externalPressureBoundKPa(),ventSettings.targetPressureChangeKPaPerSecond(),
                        ventSettings.targetPressureKPa()+1,ventSettings.volumeRateM3PerSecond(),ventSettings.scrubberMode(),
                        ventSettings.filteredGases(),ventSettings.selectedLayer());
                require(vent.compareAndSetSettings(ventSettings.revision(),revisedSettings),"change a non-structural vent pressure target");
                require(BodyHeldItemActions.activate(fixture.player(),new BodyHandActionRequest(BodyHandActionRequest.Action.USE_IN_HAND,
                        hand,authority.epoch(),100,hands.revision(),null,token))==BodyHandActionResult.Reason.OK,
                        "same held analyzer refreshes the already pinned unary session after settings edit");
                var retained=GasAnalyzerSessionService.latestSnapshot(fixture.player());
                require(retained.session().equals(ventScan.session())&&retained.generation()==ventScan.generation()
                        &&retained.source()==GasAnalyzerPipeSamples.Source.UNARY_DEVICE
                        &&retained.nodeEntries().getFirst().stamp().incarnation().equals(ventScan.nodeEntries().getFirst().stamp().incarnation()),
                        "non-structural config revision retains exact unary session and pipe owner identity");
                var ventSnapshot=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                var ventNetwork=ventSnapshot.network(connectedKey).orElseThrow();
                require(ventSnapshot.get(connectedKey).orElseThrow().version()==connectedBefore.version()
                        &&ventNetwork.gasRevision()==connectedNetworkBefore.gasRevision()+1
                        &&ventNetwork.id().equals(connectedNetworkBefore.id()),"vent seed leaves metadata unchanged and advances shared gas revision");
                helper.runAtTickTime(3,()->continueRegisteredUse(helper,fixture,level,body,authority,hands,hand,token,
                        ventPos,connectedPos));
            }catch(Throwable failure){helper.fail("registered visible-pipe analyzer use: "+failure);fixture.close();}
        });
    }

    private static void continueRegisteredUse(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
            net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.LivingEntity body,
            HandActorAuthority.Snapshot authority, com.juicyslew.moonstation14.ms14.hands.live.LiveHands hands,
            String hand, String token, BlockPos ventPos, BlockPos connectedPos) {
        try {
                level.setBlock(connectedPos,com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(),3);
                require(LoadedSealedWallSegment.prepareOne(level,connectedPos,Direction.WEST,PipeTopology.Layer.PRIMARY,java.util.UUID.randomUUID()).isPresent(),
                        "trusted sealed-wall lane connects toward visible vent");
                require(LoadedSealedWallGasOwner.createVacuumOwnerForTrustedAdmission(level,connectedPos,PipeTopology.Layer.PRIMARY),
                        "create exact sealed-wall gas owner");
                var sealedKey=new PipeTopology.NodeKey(connectedPos,PipeTopology.Layer.PRIMARY);
                var sealedBefore=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().get(sealedKey).orElseThrow();
                var sealedNetworkBefore=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().network(sealedKey).orElseThrow();
                require(PipeGasFixtureTestSupport.seedLane(level,sealedKey,sealedBefore.incarnation(),sealedBefore.version(),
                        new GasMixture(java.util.Map.of(GasType.NITROGEN,.6),293.15,sealedBefore.volume())),"seed sealed connected node");
                body.setPos(ventPos.getX()-2,ventPos.getY(),ventPos.getZ()+.5);
                 var ventHit=RegisteredPlayerReadyDriver.aimAt(body,level,ventPos);
                require(AuthenticatedBlockUseDriver.use(fixture.player(),new BodyBlockUseRequest(authority.epoch(),9,hands.revision(),hand,token,
                        ventPos,ventHit.getDirection(),false,BodyBlockUseRequest.Intent.USE))==BodyBlockUseResult.Outcome.NO_CHANGE,"vent scans connected sealed-wall node");
                var sealedScan=GasAnalyzerSessionService.latestSnapshot(fixture.player());
                require(sealedScan.source()==GasAnalyzerPipeSamples.Source.UNARY_DEVICE
                        &&sealedScan.nodeEntries().getFirst().stamp().node().equals(sealedKey)
                        &&Math.abs(sealedScan.nodeEntries().getFirst().sample().gases().getFirst().moles()-.6)<.001,
                        "concealed segment must match the configured vent-facing port and retain its own node identity");
                var sealedSnapshot=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                var sealedNetwork=sealedSnapshot.network(sealedKey).orElseThrow();
                require(sealedSnapshot.get(sealedKey).orElseThrow().version()==sealedBefore.version()
                        &&sealedNetwork.gasRevision()==sealedNetworkBefore.gasRevision()+1
                        &&sealedNetwork.id().equals(sealedNetworkBefore.id()),"sealed fixture seed leaves metadata unchanged and advances shared gas revision");
                level.setBlock(ventPos,com.juicyslew.moonstation14.block.ModBlocks.GAS_SCRUBBER.get().defaultBlockState()
                        .setValue(VentilationDeviceBlock.FACING,Direction.WEST),3);
                body.setPos(ventPos.getX()-2,ventPos.getY(),ventPos.getZ()+.5);
                var scrubberHit=RegisteredPlayerReadyDriver.aimAt(body,level,ventPos);
                require(AuthenticatedBlockUseDriver.use(fixture.player(),new BodyBlockUseRequest(authority.epoch(),10,hands.revision(),hand,token,
                        ventPos,scrubberHit.getDirection(),false,BodyBlockUseRequest.Intent.USE))==BodyBlockUseResult.Outcome.NO_CHANGE,"ordinary use scans visible scrubber");
                require(GasAnalyzerSessionService.latestSnapshot(fixture.player()).source()==GasAnalyzerPipeSamples.Source.UNARY_DEVICE,
                        "scrubber uses the same real connected-node adapter");
                var noPipePos=ventPos.west(4);
                level.setBlock(noPipePos,com.juicyslew.moonstation14.block.ModBlocks.GAS_VENT.get().defaultBlockState()
                        .setValue(VentilationDeviceBlock.FACING,Direction.WEST),3);
                body.setPos(noPipePos.getX()-2,noPipePos.getY(),noPipePos.getZ()+.5);
                var missingHit=RegisteredPlayerReadyDriver.aimAt(body,level,noPipePos);
                require(AuthenticatedBlockUseDriver.use(fixture.player(),new BodyBlockUseRequest(authority.epoch(),11,hands.revision(),hand,token,
                        noPipePos,missingHit.getDirection(),false,BodyBlockUseRequest.Intent.USE))==BodyBlockUseResult.Outcome.NO_CHANGE,"unconnected unary target takes air fallback");
                require(GasAnalyzerSessionService.latestSnapshot(fixture.player()).source()==null,"missing pipe connection never fabricates node gas");
                var sealedAfterScan=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                require(sealedAfterScan.get(sealedKey).orElseThrow().version()==sealedBefore.version()
                                &&sealedAfterScan.network(sealedKey).orElseThrow().gasRevision()==sealedNetwork.gasRevision(),
                        "vent/scrubber scans leave seeded sealed-pipe metadata and gas revision unchanged");
                var steelTarget=connectedPos.north();level.setBlock(steelTarget,com.juicyslew.moonstation14.block.ModBlocks.STEEL_WALL_BLOCK.get().defaultBlockState(),3);
                body.setPos(steelTarget.getX()-2,steelTarget.getY(),steelTarget.getZ()+.5);
                var steelHit=RegisteredPlayerReadyDriver.aimAt(body,level,steelTarget);
                require(AuthenticatedBlockUseDriver.use(fixture.player(),new BodyBlockUseRequest(authority.epoch(),12,hands.revision(),hand,token,
                        steelTarget,steelHit.getDirection(),false,BodyBlockUseRequest.Intent.USE))==BodyBlockUseResult.Outcome.NO_CHANGE,"bare steel wall is ordinary air fallback target");
                require(GasAnalyzerSessionService.latestSnapshot(fixture.player()).source()==null,"bare steel wall cannot expose a neighboring concealed gas identity");
                helper.succeed();
            }catch(Throwable failure){helper.fail("registered visible-pipe analyzer continuation: "+failure);}
            finally{fixture.close();}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
