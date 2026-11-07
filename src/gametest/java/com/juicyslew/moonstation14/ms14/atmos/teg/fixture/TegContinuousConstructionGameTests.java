package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachinePortDirections;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines;
import com.juicyslew.moonstation14.ms14.atmos.piping.LoadedPipeGasWork;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasPortHost;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.topology.DevicePort;
import com.juicyslew.moonstation14.ms14.power.topology.PowerTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.stream.Collectors;

@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class TegContinuousConstructionGameTests {
    private TegContinuousConstructionGameTests() { }

    @GameTest(template="disposal_power_clear", templateNamespace="moonstation14", timeoutTicks=200)
    @GameTestTags({"teg-fixture-construction","teg-outdoor-site-surface"})
    public static void publicBuildPersistsExactUninitializedContinuousOwnership(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel();
        BlockPos templateOrigin=helper.absolutePos(BlockPos.ZERO), far=templateOrigin.offset(48,8,48);
        var lease=FixtureChunkTickets.acquire(helper,new AABB(templateOrigin.getX(),templateOrigin.getY(),templateOrigin.getZ(),far.getX(),far.getY(),far.getZ()));
         FixtureChunkTickets.awaitEntityTicking(helper,lease,40,()->true,"continuous construction footprint entity-ticking",()->{
             int motionTop=Integer.MIN_VALUE;
             for(int x=0;x<48;x++)for(int z=0;z<48;z++)motionTop=Math.max(motionTop,level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,templateOrigin.getX()+x,templateOrigin.getZ()+z)-1);
             BlockPos plantPos=new BlockPos(templateOrigin.getX()+10,motionTop+4,templateOrigin.getZ()+10);
             require(level.getBlockState(plantPos).isAir()&&level.getBlockEntity(plantPos)==null&&level.getFluidState(plantPos).isEmpty(),"reserved tall-plant probe cell is empty before fixture setup");
             level.setBlock(plantPos,Blocks.CHORUS_PLANT.defaultBlockState(),3);
             var site=TegContinuousFixtureTestSupport.prepareOutdoorFootprint(level,templateOrigin);
             BlockPos origin=site.origin();
             require(site.highestMotionBlockingY()<=origin.getY()-2&&site.highestOccupiedY()<origin.getY(),"site is above preserved non-motion-blocking vegetation and motion-blocking terrain");
             require(level.getBlockState(plantPos).is(Blocks.CHORUS_PLANT),"site selection preserves the tall chorus plant rather than clearing it");
            var catalog=ModAtmosMachines.catalog(level);
            AtmosMachineProfile mixer=catalog.get(ModAtmosMachines.id(ModBlocks.GAS_MIXER.get().profileIdentity()));
            AtmosMachineProfile radiator=catalog.get(ModAtmosMachines.id(ModBlocks.PASSIVE_HEAT_EXCHANGER.get().profileIdentity()));
            require(mixer!=null&&radiator!=null,"resolved mixer and radiator profiles available");
             var layout=TegContinuousFuelPlan.layout(origin,mixer,radiator);
             require(layout.fueled().roomInterior().size()==27,"raised site preserves the normal 27-cell burner layout");
            for(int x=0;x<48;x++)for(int z=0;z<48;z++)level.setBlock(origin.offset(x,1,z),Blocks.STONE.defaultBlockState(),3);
            Set<BlockPos> planned=new LinkedHashSet<>(layout.fueled().allOwnedCells());
            planned.addAll(layout.roomA());planned.addAll(layout.roomB());planned.addAll(layout.shellA());planned.addAll(layout.shellB());
            planned.addAll(layout.machines());planned.addAll(layout.outsideVent());layout.routes().values().forEach(planned::addAll);
            TegContinuousPowerPlan.cables(origin).forEach(c->planned.add(c.host()));
            for(int x=0;x<48;x++)for(int y=2;y<8;y++)for(int z=0;z<48;z++)planned.add(origin.offset(x,y,z));
            String refusal=TegFixturePlacement.preflight(level,origin,planned,TegFueledFixturePlan.foundation(origin),TegContinuousFuelPlan.HEIGHT);
            require(refusal==null,"site preflight refused: "+refusal);
            require(TegFixturePlacement.continuousExteriorFailure(level,layout)==null,"fresh exterior preflight succeeds");

            String result=TegContinuousFixtureService.build(level,origin);
            require(result!=null&&result.startsWith("Built continuous TEG hardware"),"public build failed: "+result);
             var entry=TegFixtureSavedData.existing(level).flatMap(data->data.entry(origin)).orElseThrow(
                     ()->new GameTestAssertException("public build did not persist ownership: "+result));
            require(entry.mode()==TegFixtureSavedData.Mode.CONTINUOUS&&!entry.initialized(),"saved mode is uninitialized CONTINUOUS");
             var auxiliaryEquipment=TegContinuousPowerPlan.equipment(layout);
             BlockPos auxiliaryApcPos=auxiliaryEquipment.secondaryStartupApc();
             require(level.getBlockEntity(auxiliaryApcPos) instanceof PowerDeviceBlockEntity,"dedicated auxiliary APC has live block entity");
             var actualAuxiliaryFacing=level.getBlockState(auxiliaryApcPos).getValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.FACING);
             require(actualAuxiliaryFacing==auxiliaryEquipment.secondaryStartupApcFacing(),"placed auxiliary APC facing is sourced from the typed continuous power plan");
             var actualAuxiliaryPorts=PowerDeviceKind.APC.ports(auxiliaryApcPos,actualAuxiliaryFacing);
             var actualAuxiliaryOutput=actualAuxiliaryPorts.stream().filter(port->port.tier()==CableTier.APC).findFirst().orElseThrow();
             var actualAuxiliaryMv=actualAuxiliaryPorts.stream().filter(port->port.tier()==CableTier.MV).findFirst().orElseThrow();
             require(actualAuxiliaryOutput.face()==Direction.EAST&&actualAuxiliaryMv.face()==Direction.WEST,
                     "actual placed APC kind/facing maps to its east APC output and isolated west MV input");
             assertPlannedCableContact(level,actualAuxiliaryOutput);
             assertPlannedCableContact(level,actualAuxiliaryMv);
             BlockPos mainApcPos=layout.fueled().electrical().devices().outputApc();
             var actualMainFacing=level.getBlockState(mainApcPos).getValue(com.juicyslew.moonstation14.block.custom.PowerDeviceBlock.FACING);
             require(actualMainFacing==Direction.WEST,"existing main operating APC facing remains unchanged");
             var actualMainPorts=PowerDeviceKind.APC.ports(mainApcPos,actualMainFacing);
             require(actualMainPorts.stream().anyMatch(port->port.tier()==CableTier.MV&&port.face()==Direction.WEST)
                     &&actualMainPorts.stream().anyMatch(port->port.tier()==CableTier.APC&&port.face()==Direction.EAST),
                     "main operating APC retains its original west MV/east APC ports");
              Set<BlockPos> expectedOwners=new LinkedHashSet<>();expectedOwners.add(layout.core());
              layout.fueled().machines().forEach(machine->expectedOwners.add(machine.position()));
              layout.devices().forEach(device->expectedOwners.add(device.position()));
              expectedOwners.add(auxiliaryApcPos);
              expectedOwners.add(layout.fueled().electrical().devices().outputApc());
              require(entry.ownedMachines().keySet().equals(expectedOwners),"saved ownership exactly matches all planned bodies including both APCs: expected="+expectedOwners+" actual="+entry.ownedMachines().keySet());
              require(entry.ownedMachines().values().stream().allMatch(Objects::nonNull)
                              &&entry.ownedMachines().values().stream().distinct().count()==expectedOwners.size(),
                      "every planned owner has one unique persisted placement identity");
              require(entry.ownedMachines().get(auxiliaryApcPos).equals(((PowerDeviceBlockEntity)level.getBlockEntity(auxiliaryApcPos)).placementIdentity()),
                      "saved owner identity matches the actual auxiliary APC incarnation");
               var labels=layout.presentationSigns().values().stream().map(TegFueledFixturePlan.PresentationLabel::text).collect(java.util.stream.Collectors.toSet());
                require(labels.stream().anyMatch(text->text.contains("BURN CHAMBER")),"burner room retains its burn chamber label without the redundant inherited label");
               require(labels.stream().anyMatch(text->text.contains("OXYGEN SOURCE")&&text.contains("O2 MINER")),"oxygen source label identifies source and miner");
               require(labels.stream().anyMatch(text->text.contains("PLASMA SOURCE")&&text.contains("FUEL MINER")),"plasma source label identifies source and miner");
               require(labels.stream().anyMatch(text->text.contains("COOLANT ARRAY")&&text.contains("5 RADIATORS")),"standard five-radiator coolant array label remains planned");
               require(labels.contains("TEG"),"core control label remains planned");
               require(labels.stream().anyMatch(text->text.contains("STARTUP HEATER")&&text.contains("PAID PREHEAT")),"heater/startup control label remains planned");
               require(labels.stream().anyMatch(text->text.contains("CLOSED PLASMA")),"cold-loop coolant label remains planned");
               require(labels.stream().noneMatch(text->text.contains("COOLANT CHAMBER")),"removed legacy coolant chamber label is absent");
              for(var plannedSign:layout.presentationSigns().entrySet()) {
                 BlockPos signPos=plannedSign.getKey();var label=plannedSign.getValue();
                 var signState=level.getBlockState(signPos);
                 require(signState.getBlock() instanceof SignBlock&&signState.hasProperty(net.minecraft.world.level.block.StandingSignBlock.ROTATION),"planned label is a standing sign at "+signPos);
                 int expectedRotation=switch(label.readerFacing()){case SOUTH->0;case WEST->4;case NORTH->8;case EAST->12;default->throw new GameTestAssertException("planned sign facing is not horizontal at "+signPos);};
                 require(signState.getValue(net.minecraft.world.level.block.StandingSignBlock.ROTATION)==expectedRotation,"live standing-sign rotation matches planned reader-facing "+label.readerFacing()+" at "+signPos);
                 require(level.getBlockEntity(signPos) instanceof SignBlockEntity,"planned label has a live sign entity at "+signPos);
                 var sign=(SignBlockEntity)level.getBlockEntity(signPos);
                  String[] lines=label.text().split("\\n",-1);
                  for(int i=0;i<lines.length;i++)require(sign.getFrontText().getMessage(i,false).getString().equals(lines[i]),"front sign row "+i+" matches planned label at "+signPos);
             }
            require(entry.ownedMachines().get(layout.core())!=null,"saved owner includes core");
            require(layout.core().getY()==origin.getY()+2,"core uses planned body-relative Y");
               require(layout.radiatorPositions().size()==TegContinuousCoolingPlan.RADIATOR_COUNT,"typed cooling plan declares the full parallel array");
               AtmosphereService.ExteriorBoundary.Kind expectedExteriorKind=AtmosphereService.INSTANCE.isSpaceDimension(level.dimension())
                       ? AtmosphereService.ExteriorBoundary.Kind.SPACE : AtmosphereService.ExteriorBoundary.Kind.PLANETARY_AMBIENT;
               for(BlockPos radiatorBody:layout.radiatorPositions()) {
                  require(level.getBlockState(radiatorBody).is(ModBlocks.PASSIVE_HEAT_EXCHANGER.get()),"radiator occupies its machine host, not a pipe host at "+radiatorBody);
                  var boundary=AtmosphereService.INSTANCE.exteriorBoundaryForImmediateTransfer(level,radiatorBody)
                          .orElseThrow(()->new GameTestAssertException("radiator boundary missing at "+radiatorBody));
                   boolean valid=boundary.validate();
                   require(boundary.kind()==expectedExteriorKind&&valid,"each array radiator faces valid world-policy exterior: position="+radiatorBody
                           +" expectedKind="+expectedExteriorKind+" actualKind="+boundary.kind()+" valid="+valid);
              }
              BlockPos mount=layout.wasteMountHost(),vent=layout.wasteVentBody(),ventFront=vent.relative(layout.wasteVentFacing());
             require(level.getBlockState(mount).is(ModBlocks.STEEL_WALL_BLOCK.get()),"public build places actual steel at the waste vent mount");
               var wasteMountSegment=layout.sealedRouteSegments().stream().filter(segment->segment.position().equals(mount)).findFirst().orElseThrow();
              var mountOwner=com.juicyslew.moonstation14.ms14.atmos.piping.LoadedSealedWallSegment.matchingLive(level,mount,wasteMountSegment.layer()).orElse(null);
              var mountGas=com.juicyslew.moonstation14.ms14.atmos.piping.LoadedSealedWallGasOwner.activeSnapshot(level,mount,wasteMountSegment.layer()).orElse(null);
             require(mountOwner!=null&&mountGas!=null&&mountOwner.id().equals(mountGas.incarnation()),"waste vent mount has matching live active sealed-wall ownership");
              require(level.getBlockState(ventFront).isAir(),"waste vent has an unobstructed actual air cell in front");
              require(layout.wasteVentFacing()==Direction.SOUTH&&layout.outsideVent().contains(vent),"relocated exterior vent is south-facing and included as the actual outside body");
              require(layout.wasteMountWall().stream().allMatch(pos->level.getBlockState(pos).is(ModBlocks.STEEL_WALL_BLOCK.get())),"sealed waste vent host is surrounded by the planned steel wall mount panel");
             require(layout.wasteMountWall().stream().allMatch(pos->pos.getZ()==mount.getZ())&&!layout.wasteMountWall().contains(vent)
                     &&layout.wasteMountWall().contains(mount.above())&&layout.wasteMountWall().contains(mount.east())&&layout.wasteMountWall().contains(mount.west()),"mount panel stays in the back wall plane around the sealed pipe aperture, not over the vent");
              require(level.getBlockEntity(mount)==null&&layout.wasteSegments().get(2).kind()==PipeTopology.Kind.STRAIGHT_SEALED_WALL,"back pipe is an actual primary sealed wall segment, not an exposed lane");
             require(level.getBlockState(vent.below()).isFaceSturdy(level,vent.below(),Direction.UP),"floating exterior vent has solid floor support");
             require(level.getBlockState(ventFront).isAir()&&AtmosphereService.INSTANCE.exteriorBoundaryForImmediateTransfer(level,ventFront).isPresent(),"vent south-facing discharge remains unobstructed at a real exterior boundary");
             for(BlockPos window:TegContinuousFuelPlan.sourceObservationWindows(layout.origin()))require(level.getBlockState(window).is(Blocks.GLASS),"source viewing panel is actual glass at "+window);
             for(BlockPos crossing:layout.sealedCrossings())require(level.getBlockState(crossing).is(ModBlocks.STEEL_WALL_BLOCK.get()),"sealed source crossing remains steel at "+crossing);
            require(TegFixturePlacement.continuousExteriorFailure(level,layout)==null,"fresh post-build exterior boundary remains valid");
            var hardware=readLiveHardware(level,layout,entry);
             require(entry.ownedGas().equals(hardware.gasOwners()),"saved gas owners match the live ten-component capture");
              require(hardware.capture().components().size()==layout.hotPipeComponents().size()+layout.coldPipeComponents().size()+3 && hardware.capture().components().size()==10,"actual typed-plan gas components include independent oxygen, plasma, and fuel circuits");
             var heaterPlan=layout.fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.HEATER).toList();
             require(heaterPlan.size()==2,"continuous fueled layout has the canonical and supplemental startup heaters");
             var heater=heaterPlan.getFirst();var auxiliaryHeater=heaterPlan.get(1);
             require(heater.position().equals(layout.core().offset(-11,0,-2))&&heater.facing()==Direction.NORTH,"continuous heater occupies planned north-facing carrier-loop branch");
             require(auxiliaryHeater.position().equals(layout.core().offset(-9,0,-6))&&auxiliaryHeater.facing()==Direction.SOUTH,
                     "supplemental heater occupies the planned south-facing long-return branch");
             require(level.getBlockState(heater.position()).is(ModBlocks.GAS_THERMO_HEATER.get())
                     &&level.getBlockEntity(heater.position()) instanceof AtmosMachineBlockEntity,"live heater block and machine entity match plan");
              require(layout.hotPipeComponents().get(1).contains(heater.position().relative(Direction.SOUTH)),"heater's south pipe is on the planned first hot carrier component");
             require(level.getBlockState(auxiliaryHeater.position()).is(ModBlocks.GAS_THERMO_HEATER.get())
                             &&level.getBlockEntity(auxiliaryHeater.position()) instanceof AtmosMachineBlockEntity,
                     "live supplemental heater block and machine entity match plan");
             require(layout.hotPipeComponents().get(2).contains(auxiliaryHeater.position().relative(Direction.NORTH)),
                     "supplemental heater's north pipe is on the existing long-return component 2");
             require(!auxiliaryHeater.position().equals(heater.position())
                             &&!layout.fueled().roomInterior().contains(auxiliaryHeater.position())
                             &&!layout.fueled().wallShell().contains(auxiliaryHeater.position())
                             &&layout.fueled().hotComponents().get(2).contains(auxiliaryHeater.position().relative(Direction.NORTH)),
                     "supplemental heater body stays outside the burner room while its port serves component 2");
            require(!layout.fueled().roomInterior().contains(heater.position())&&!layout.fueled().wallShell().contains(heater.position())
                    &&!layout.fueled().coldRoomInterior().contains(heater.position())&&!layout.fueled().coldWallShell().contains(heater.position())
                    &&!layout.roomA().contains(heater.position())&&!layout.roomB().contains(heater.position())
                    &&!layout.shellA().contains(heater.position())&&!layout.shellB().contains(heater.position()),"heater body remains outside all room interiors and shells");
               BlockPos heaterFront=heater.position().relative(Direction.NORTH);
             require(level.getBlockState(heaterFront).isAir()&&level.getBlockState(heaterFront.below()).is(Blocks.STONE),"heater control front is reachable over foundation");
              BlockPos mainHeaterLabel=layout.core().offset(-12,0,-3),auxiliaryHeaterLabel=layout.core().offset(-9,0,-8);
              var expectedHeaterText="STARTUP HEATER\nPAID PREHEAT\nTARGET 590 K";
               require(layout.presentationSigns().get(mainHeaterLabel).equals(
                               new TegFueledFixturePlan.PresentationLabel(expectedHeaterText,Direction.NORTH)),
                      "main heater label identifies startup/preheat purpose and target at its independent planned position");
               require(layout.presentationSigns().get(auxiliaryHeaterLabel).equals(
                               new TegFueledFixturePlan.PresentationLabel(expectedHeaterText,Direction.SOUTH)),
                      "auxiliary heater label independently identifies startup/preheat purpose and target at its distinct planned position");
              require(level.getBlockState(mainHeaterLabel).getBlock() instanceof SignBlock&&level.getBlockEntity(mainHeaterLabel) instanceof SignBlockEntity,
                      "main heater label is a live sign");
              require(level.getBlockState(auxiliaryHeaterLabel).getBlock() instanceof SignBlock&&level.getBlockEntity(auxiliaryHeaterLabel) instanceof SignBlockEntity,
                      "auxiliary heater label is a live sign");
             BlockPos oldHeater=origin.offset(6,2,8),oldLabel=origin.offset(7,2,9);
             if(layout.fueled().machines().stream().noneMatch(m->m.position().equals(oldHeater)))
                 require(!level.getBlockState(oldHeater).is(ModBlocks.GAS_THERMO_HEATER.get()),"legacy fuel-heater site has no extra heater");
              if(!layout.presentationSigns().containsKey(oldLabel))
                 require(!(level.getBlockState(oldLabel).getBlock() instanceof SignBlock),"legacy fuel-heater label site has no extra sign");
             var heaterProfile=ModAtmosMachines.catalog(level).get(ModAtmosMachines.id(ModBlocks.GAS_THERMO_HEATER.get().profileIdentity()));
             require(heaterProfile!=null,"resolved heater prototype available");
             var heaterMachine=(AtmosMachineBlockEntity)level.getBlockEntity(heater.position());
             var auxiliaryHeaterMachine=(AtmosMachineBlockEntity)level.getBlockEntity(auxiliaryHeater.position());
             var heaterPort=heaterMachine.gasPorts().stream().filter(p->p.id().equals("pipe")).findFirst().orElseThrow();
             var auxiliaryHeaterPort=auxiliaryHeaterMachine.gasPorts().stream().filter(p->p.id().equals("pipe")).findFirst().orElseThrow();
             var auxiliaryPowerPorts=auxiliaryHeaterMachine.powerRuntimePorts().stream().filter(p->p.tier()==CableTier.APC).toList();
             require(auxiliaryPowerPorts.size()==1&&auxiliaryPowerPorts.getFirst().face()==Direction.EAST,
                     "resolved south-facing auxiliary heater exposes its actual east APC receiver port");
             assertPlannedCableContact(level,auxiliaryPowerPorts.getFirst());
            var heaterKey=heaterPort.key(heater.position());
             require(heaterPort.directions().equals(Set.of(Direction.SOUTH)),"resolved heater gas port faces south into the carrier loop");
             require(heaterProfile.ports().stream().anyMatch(p->p.name()==AtmosMachineProfile.PortName.PIPE
                     &&AtmosMachinePortDirections.worldDirection(p.direction(),heater.facing())==Direction.SOUTH
                     &&Math.abs(p.volumeM3()-heaterPort.volume())<1e-9),"heater gas port direction and volume match resolved prototype");
             require(auxiliaryHeaterPort.directions().equals(Set.of(Direction.NORTH))
                             &&heaterProfile.ports().stream().anyMatch(p->p.name()==AtmosMachineProfile.PortName.PIPE
                             &&AtmosMachinePortDirections.worldDirection(p.direction(),auxiliaryHeater.facing())==Direction.NORTH
                             &&Math.abs(p.volumeM3()-auxiliaryHeaterPort.volume())<1e-9),
                     "supplemental heater's north gas port and volume match the resolved profile");
             require(layout.fueled().coldRoomInterior().isEmpty()&&layout.fueled().coldWallShell().isEmpty(),"external coolant array has no legacy finite cold-room chamber or shell");
             for(Set<BlockPos> room:List.of(layout.fueled().roomInterior(),layout.roomA(),layout.roomB()))
                for(BlockPos cell:room)require(AtmosphereService.INSTANCE.finiteRoomEndpointForTrustedSimulation(level,cell).isPresent(),
                        "synchronously owned vacuum room cell "+cell);
            var devices=layout.fueled().electrical().devices();
            require(level.getBlockEntity(devices.outputApc()) instanceof PowerDeviceBlockEntity output&&output.energyJoules()==0,
                    "operating APC has zero bootstrap energy");
            require(level.getBlockState(devices.startupApc()).isAir(),"startup APC was not constructed");
            Map<BlockPos,UUID> beforeOwners=entry.ownedMachines();
            var gasInventoryBeforeRepeat=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
            String repeated=TegContinuousFixtureService.build(level,origin);
            require(repeated!=null&&repeated.startsWith("Build refused:"),"repeat build refused: "+repeated);
            require(TegFixtureSavedData.existing(level).flatMap(data->data.entry(origin)).orElseThrow().equals(entry),
                    "repeat refusal preserves original saved entry");
            var afterHardware=readLiveHardware(level,layout,entry);
            require(afterHardware.machineOwners().equals(beforeOwners),"repeat refusal preserves live owner identities");
            require(afterHardware.gasOwners().equals(hardware.gasOwners()),"repeat refusal preserves pipe ownership inventories");
            require(PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().equals(gasInventoryBeforeRepeat),
                    "repeat refusal preserves the exact pipe gas inventory snapshot");
            require(level.getBlockEntity(devices.outputApc()) instanceof PowerDeviceBlockEntity after&&after.energyJoules()==0,
                    "repeat refusal leaves operating APC unseeded");
            var gasBeforeObserve=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
             var observed=TegContinuousFixtureService.observeOwnedHardware(level,entry);
             TegContinuousFixtureTestSupport.assertThreeHotContacts(level,observed);
            require(observed.machineOwners().equals(entry.ownedMachines())&&observed.gasOwners().equals(entry.ownedGas()),
                    "owned observer exactly validates saved identities");
             var heaterComponent=TegContinuousFixtureService.capturedComponentForPort(observed.topology(),hardware.capture(),heaterKey).orElseThrow();
             var heaterNodes=heaterComponent.nodes().stream().map(n->n.key()).collect(Collectors.toSet());
             require(heaterNodes.contains(heaterKey),"actual captured heater pipe component contains heater port");
             Set<PipeTopology.NodeKey> expectedHeaterComponent=hardware.expected().components().get(1);
              require(heaterNodes.equals(expectedHeaterComponent),"heater port joins exactly hot carrier component 1, including distributed contact branches, and excludes fuel/waste/cold components");
              var wasteComponent=hardware.expected().components().get(2);
              for(BlockPos route:layout.routes().get("waste"))require(wasteComponent.contains(new PipeTopology.NodeKey(route,PipeTopology.Layer.PRIMARY)),
                      "actual captured waste line belongs to hot inlet component 2 at "+route);
              var exhaustComponent=hardware.expected().components().get(0);
              for(BlockPos route:layout.routes().get("vent"))require(exhaustComponent.contains(new PipeTopology.NodeKey(route,PipeTopology.Layer.PRIMARY)),
                      "actual captured exterior exhaust belongs to hot outlet component 0 at "+route);
             require(observed.topology().components().stream().filter(component->component.contains(heaterKey)).count()==1,
                     "heater port has one unambiguous captured component owner");
            require(PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().equals(gasBeforeObserve),
                    "owned observation leaves exact pipe gas inventory unchanged");
            BlockPos oxygenMiner=layout.devices().stream().filter(d->d.role()==TegContinuousFuelPlan.DeviceRole.GAS_MINER_OXYGEN)
                    .map(TegContinuousFuelPlan.Device::position).findFirst().orElseThrow();
            UUID originalMiner=entry.ownedMachines().get(oxygenMiner);
            level.setBlock(oxygenMiner,Blocks.AIR.defaultBlockState(),3);
            level.setBlock(oxygenMiner,ModBlocks.GAS_MINER_OXYGEN.get().defaultBlockState(),3);
            var replacement=(com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity)level.getBlockEntity(oxygenMiner);
            require(replacement!=null&&replacement.placementIdentity()!=null&&!replacement.placementIdentity().equals(originalMiner),
                    "ordinary replacement miner receives a new live UUID");
            try {
                TegContinuousFixtureService.observeOwnedHardware(level,entry);
                throw new GameTestAssertException("owned observation accepted a replaced miner incarnation");
            } catch(IllegalStateException expected) {
                require(expected.getMessage().contains("saved continuous ownership"),
                        "replacement rejection reports saved identity mismatch: "+expected.getMessage());
            }
            helper.succeed();
        },templateOrigin);
    }

    private static void assertPlannedCableContact(ServerLevel level,DevicePort port) {
        var contact=PowerTopology.deviceContacts(port).stream().filter(candidate->candidate.face()==port.face().getOpposite()).findFirst().orElseThrow();
        require(com.juicyslew.moonstation14.ms14.power.cable.CableStorage.has(level,contact.host(),contact.face(),contact.tier()),
                "placed cable ledger contains the actual resolved device contact "+contact);
    }

    private static LiveHardware readLiveHardware(ServerLevel level,TegContinuousFuelPlan.Layout layout,TegFixtureSavedData.Entry entry) {
        List<AtmosMachineBlockEntity> machines=new ArrayList<>();
        layout.fueled().machines().forEach(m->{if(level.getBlockEntity(m.position()) instanceof AtmosMachineBlockEntity machine)machines.add(machine);});
         layout.devices().stream().filter(d->d.role().isAtmosMachine())
                .forEach(d->{if(level.getBlockEntity(d.position()) instanceof AtmosMachineBlockEntity machine)machines.add(machine);});
        if(!(level.getBlockEntity(layout.core()) instanceof AtmosMachineBlockEntity core))throw new GameTestAssertException("live core missing");
         Map<BlockPos,UUID> owners=liveOwnerIdentities(level,layout);
         require(owners.equals(entry.ownedMachines()),"all saved machine owner UUIDs match exact live hosts; missing="+difference(entry.ownedMachines().keySet(),owners.keySet())
                 +" unexpected="+difference(owners.keySet(),entry.ownedMachines().keySet())+" mismatched="+owners.entrySet().stream()
                 .filter(e->!Objects.equals(entry.ownedMachines().get(e.getKey()),e.getValue())).toList());
        Map<BlockPos,List<PipeGasPortHost.Port>> ports=new LinkedHashMap<>();
        machines.forEach(machine->ports.put(machine.getBlockPos(),machine.gasPorts().stream().map(p->new PipeGasPortHost.Port(p.id(),p.layer(),p.directions(),p.volume(),p.incarnation())).toList()));
        var expected=TegContinuousFixtureTopology.plan(layout,ports);
        var capture=LoadedPipeGasWork.capture(level,expected.representatives().stream().map(LoadedPipeGasWork.Port::new).toList()).orElseThrow();
        require(!capture.aliases()&&capture.components().size()==10,"exact live continuous gas capture");
        Map<PipeTopology.NodeKey,Set<PipeTopology.NodeKey>> actual=new HashMap<>();
        for(var representative:expected.representatives())actual.put(representative,capture.componentFor(representative).orElseThrow().nodes().stream().map(n->n.key()).collect(Collectors.toSet()));
         require(TegFueledFixtureTopology.exact(expected,actual),"captured gas membership equals three independent fuel circuits and seven other planned components");
         return new LiveHardware(owners,TegFixturePlacement.gasOwners(capture),expected,capture);
     }
      private record LiveHardware(Map<BlockPos,UUID> machineOwners,Map<PipeTopology.NodeKey,UUID> gasOwners,
                                  TegFueledFixtureTopology.Plan expected,LoadedPipeGasWork.Snapshot capture) { }
     /** Independent native owner audit dispatches through every live BE owner identity kind. */
     private static Map<BlockPos,UUID> liveOwnerIdentities(ServerLevel level,TegContinuousFuelPlan.Layout layout) {
         Map<BlockPos,UUID> owners=new LinkedHashMap<>();Set<UUID> identities=new HashSet<>();
         for(var machine:layout.fueled().machines()) {
             var live=level.getBlockEntity(machine.position());
             if(!(live instanceof AtmosMachineBlockEntity be)||be.isRemoved()||be.placementIdentity()==null)
                 throw new GameTestAssertException("planned atmosphere-machine owner missing at "+machine.position());
             putLiveOwner(owners,identities,machine.position(),be.placementIdentity());
         }
         var core=level.getBlockEntity(layout.core());
         if(!(core instanceof AtmosMachineBlockEntity be)||be.isRemoved()||be.placementIdentity()==null)throw new GameTestAssertException("planned TEG core owner missing at "+layout.core());
         putLiveOwner(owners,identities,layout.core(),be.placementIdentity());
         for(var device:layout.devices())switch(device.role()) {
             case GAS_MINER_OXYGEN,GAS_MINER_PLASMA -> {
                 var live=level.getBlockEntity(device.position());
                 if(!(live instanceof com.juicyslew.moonstation14.ms14.atmos.world.GasMinerBlockEntity miner)
                         ||!miner.hasLivePlacementIdentity())throw new GameTestAssertException("planned gas-miner owner missing at "+device.position());
                 putLiveOwner(owners,identities,device.position(),miner.placementIdentity());
             }
             case SCRUBBER,INJECTOR,WASTE_PUMP,VENT -> {
                 var live=level.getBlockEntity(device.position());
                 if(!(live instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity vent)
                         ||vent.isRemoved())throw new GameTestAssertException("planned ventilation owner missing at "+device.position());
                 putLiveOwner(owners,identities,device.position(),vent.deviceId());
             }
             case MIXER,PASSIVE_HEAT_EXCHANGER,HOT_HEAT_EXCHANGER,COLD_PRESSURE_PUMP -> {
                 var live=level.getBlockEntity(device.position());
                 if(!(live instanceof AtmosMachineBlockEntity machine)||machine.isRemoved()||machine.placementIdentity()==null)
                     throw new GameTestAssertException("planned additional atmosphere-machine owner missing at "+device.position());
                 putLiveOwner(owners,identities,device.position(),machine.placementIdentity());
             }
         }
          BlockPos auxiliaryApc=TegContinuousPowerPlan.equipment(layout).secondaryStartupApc();
          var apc=level.getBlockEntity(auxiliaryApc);
          if(!(apc instanceof PowerDeviceBlockEntity power)||power.isRemoved()||!level.getBlockState(auxiliaryApc).is(ModBlocks.APC.get()))
              throw new GameTestAssertException("planned auxiliary APC owner missing at "+auxiliaryApc);
          putLiveOwner(owners,identities,auxiliaryApc,power.placementIdentity());
          BlockPos mainApc=layout.fueled().electrical().devices().outputApc();var mainApcEntity=level.getBlockEntity(mainApc);
          if(!(mainApcEntity instanceof PowerDeviceBlockEntity mainPower)||mainPower.isRemoved()||!level.getBlockState(mainApc).is(ModBlocks.APC.get()))
              throw new GameTestAssertException("planned main operating APC owner missing at "+mainApc);
          putLiveOwner(owners,identities,mainApc,mainPower.placementIdentity());
          Set<BlockPos> expected=new HashSet<>(Set.of(layout.core()));layout.fueled().machines().forEach(m->expected.add(m.position()));
          layout.devices().forEach(d->expected.add(d.position()));expected.add(auxiliaryApc);expected.add(mainApc);
         if(!owners.keySet().equals(expected))throw new GameTestAssertException("typed owner dispatch differs from planned body positions: expected="+expected+" actual="+owners.keySet());
         return Map.copyOf(owners);
     }
     private static Set<BlockPos> difference(Collection<BlockPos> left,Collection<BlockPos> right) {
         Set<BlockPos> result=new HashSet<>(left);result.removeAll(right);return result;
     }
     private static void putLiveOwner(Map<BlockPos,UUID> owners,Set<UUID> identities,BlockPos pos,UUID identity) {
         if(identity==null||owners.putIfAbsent(pos,identity)!=null||!identities.add(identity))
             throw new GameTestAssertException("duplicate/missing typed owner identity at "+pos+" identity="+identity);
     }
    private static void require(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
}
