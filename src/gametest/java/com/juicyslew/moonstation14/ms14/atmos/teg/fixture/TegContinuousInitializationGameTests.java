package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceRules;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

import java.util.*;

@net.neoforged.neoforge.gametest.PrefixGameTestTemplate(false)
public final class TegContinuousInitializationGameTests {
    private static TegFixtureRegistryScope resourceAdmissionRegistryScope;
    @net.minecraft.gametest.framework.BeforeBatch(batch="teg_continuous_resource_admission")
    public static void setupResourceAdmissionBatch(ServerLevel level) { resourceAdmissionRegistryScope=TegFixtureRegistryScope.acquire(level); }
    @net.minecraft.gametest.framework.AfterBatch(batch="teg_continuous_resource_admission")
    public static void closeResourceAdmissionBatch(ServerLevel level) { if(resourceAdmissionRegistryScope!=null){resourceAdmissionRegistryScope.close();resourceAdmissionRegistryScope=null;} }
    private TegContinuousInitializationGameTests() { }

    @GameTest(template="disposal_power_clear",templateNamespace="moonstation14",timeoutTicks=300,batch="teg_continuous_resource_admission")
    @GameTestTags({"teg-fixture-construction","teg-continuous-startup"})
    public static void resourcesAdmitOnceAfterNormalPowerTopologySettles(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel();
        BlockPos template=helper.absolutePos(BlockPos.ZERO),far=template.offset(48,8,48);
        var lease=FixtureChunkTickets.acquire(helper,new AABB(template.getX(),template.getY(),template.getZ(),far.getX(),far.getY(),far.getZ()));
        FixtureChunkTickets.awaitEntityTicking(helper,lease,40,()->true,"continuous resource footprint entity-ticking",()->{
            try {
                BlockPos origin=TegContinuousFixtureTestSupport.prepareOutdoorFootprint(level,template).origin();
                String built=TegContinuousFixtureService.build(level,origin);
                require(built.startsWith("Built continuous TEG hardware"),"continuous build: "+built);
                 var entry=TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                 var plan=layout(level,origin);var output=(PowerDeviceBlockEntity)level.getBlockEntity(plan.fueled().electrical().devices().outputApc());
                 var initial=TegContinuousFixtureService.observeOwnedHardware(level,entry);
                 TegContinuousFixtureTestSupport.assertThreeHotContacts(level,initial);
                require(initial.topology().representatives().size()==10,"continuous fixture has exactly ten gas components");
                for(int i=0;i<initial.topology().representatives().size();i++) {
                    var component=initial.capture().componentFor(initial.topology().representatives().get(i)).orElseThrow();
                    require(component.aggregate().gasMoles().isEmpty()&&component.aggregate().thermalEnergy()==0,
                            "all ten gas components start vacuum before admission at "+i);
                }
                var pipeData=PipeGasInventorySavedData.existing(level).orElseThrow();
                var before=pipeData.snapshot();
                String early=TegContinuousFixtureService.initializeResources(level,origin);
                if(!PowerGraphService.topologyReady(level)) {
                    require(early.startsWith(TegContinuousFixtureService.RESOURCE_POWER_WAIT_PREFIX),"pending graph must return retryable power-topology wait: "+early);
                    require(!TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow().initialized(),"pending query preserves token");
                    require(output.energyJoules()==0&&pipeData.snapshot().equals(before),"pending query writes no APC/gas");
                }
                awaitReady(helper,level,origin,plan,lease,0,false);
            } catch(Throwable failure) { lease.close();helper.fail("continuous resource setup failed: "+failure.getMessage()); }
        },template);
    }

    @GameTest(template="disposal_power_clear",templateNamespace="moonstation14",timeoutTicks=500)
    @GameTestTags("teg-automatic-startup")
    public static void publicAutomaticStartAdmitsResourcesFromLevelTicks(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel();
        BlockPos template=helper.absolutePos(BlockPos.ZERO),far=template.offset(48,8,48);
        var lease=FixtureChunkTickets.acquire(helper,new AABB(template.getX(),template.getY(),template.getZ(),far.getX(),far.getY(),far.getZ()));
        FixtureChunkTickets.awaitEntityTicking(helper,lease,40,()->true,"automatic startup footprint entity-ticking",()->{
            try {
                BlockPos origin=TegContinuousFixtureTestSupport.prepareOutdoorFootprint(level,template).origin();
                require(TegContinuousFixtureService.build(level,origin).startsWith("Built continuous TEG hardware"),"continuous fixture built");
                var entry=TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                var plan=layout(level,origin);
                awaitAutomaticPower(helper,level,origin,plan,lease,0);
            } catch(Throwable failure) { lease.close();helper.fail("automatic startup setup failed: "+failure.getMessage()); }
        },template);
    }

    private static void awaitAutomaticPower(GameTestHelper helper,ServerLevel level,BlockPos origin,TegContinuousFuelPlan.Layout plan,
                                            FixtureChunkTickets.Lease lease,int ticks) {
        helper.runAfterDelay(1,()->{
            try {
                var entry=TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                var observed=TegContinuousFixtureService.observeOwnedHardware(level,entry);
                if(!PowerGraphService.topologyReady(level)||TegContinuousFixtureService.startupPowerFailure(level,observed)!=null) {
                    if(ticks>=200)throw new GameTestAssertException("ordinary power graph did not settle for automatic start");
                    awaitAutomaticPower(helper,level,origin,plan,lease,ticks+1);return;
                }
                var saved=TegFixtureSavedData.existing(level).orElseThrow();
                var output=(PowerDeviceBlockEntity)level.getBlockEntity(plan.fueled().electrical().devices().outputApc());
                String armed=TegContinuousFixtureService.startAutomatic(level,origin,UUID.randomUUID());
                require(armed.startsWith("Automatic startup armed once"),"public automatic start arms: "+armed);
                var receipt=saved.automaticStartup(origin).orElseThrow();
                require(receipt.status()==TegFixtureSavedData.AutomaticStartupStatus.WAITING_START,"start waits for the real level-tick consumer");
                require(TegContinuousFixtureService.startAutomatic(level,origin,UUID.randomUUID()).startsWith("Automatic startup refused"),"repeat start rejected");
                awaitAutomaticAdmission(helper,level,origin,plan,lease,receipt.armTick(),output,0);
            } catch(Throwable failure) { cleanupAutomatic(level,origin,lease);helper.fail("automatic startup arm failed: "+failure.getMessage()); }
        });
    }

    private static void awaitAutomaticAdmission(GameTestHelper helper,ServerLevel level,BlockPos origin,TegContinuousFuelPlan.Layout plan,
                                               FixtureChunkTickets.Lease lease,long armTick,PowerDeviceBlockEntity output,int ticks) {
        helper.runAfterDelay(1,()->{
            try {
                var saved=TegFixtureSavedData.existing(level).orElseThrow();
                var entry=saved.entry(origin).orElseThrow();
                var receipt=saved.automaticStartup(origin).orElseThrow();
                if(!entry.initialized()) {
                    require(receipt.status()==TegFixtureSavedData.AutomaticStartupStatus.WAITING_START,"automatic receipt remains armed while consumer waits");
                    require(output.energyJoules()==0,"no APC bootstrap before consumer admission");
                    assertCarrierVacuum(level,entry);
                    if(ticks>=300)throw new GameTestAssertException("ordinary level ticks did not admit automatic startup resources");
                    awaitAutomaticAdmission(helper,level,origin,plan,lease,armTick,output,ticks+1);return;
                }
                require(entry.phase()==TegFixtureSavedData.ContinuousPhase.STARTUP,"automatic consumer reaches STARTUP");
                require(receipt.armTick()==armTick&&receipt.status()==TegFixtureSavedData.AutomaticStartupStatus.PREPARING_COLD,
                        "automatic admission starts cold preparation without manual phase actions");
                var admitted=TegContinuousFixtureService.observeOwnedHardware(level,entry);
                assertCarrierSeeds(admitted);
                assertColdPreparationSettings(admitted);
                var settings=settingsSnapshot(admitted);
                var after=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                require(TegContinuousFixtureService.startAutomatic(level,origin,UUID.randomUUID()).startsWith("Automatic startup refused"),"terminal duplicate arm rejected");
                require(settings.equals(settingsSnapshot(TegContinuousFixtureService.observeOwnedHardware(level,entry)))
                                &&after.equals(PipeGasInventorySavedData.existing(level).orElseThrow().snapshot()),
                        "repeat start does not reconfigure or refill");
                cleanupAutomatic(level,origin,lease);
                helper.succeed();
            } catch(Throwable failure) { cleanupAutomatic(level,origin,lease);helper.fail("automatic startup admission failed: "+failure.getMessage()); }
        });
    }

    private static void assertColdPreparationSettings(TegContinuousFixtureService.ObservedHardware observed) {
        var layout=observed.layout();
        for(var host:observed.placement().baseMachines()) {
            var profile=host.resolvedProfile(); var values=host.settingsSnapshot();
            switch(profile.kind()) {
                       case VOLUME_PUMP, PRESSURE_PUMP -> {
                    var role=TegContinuousOperatingPlan.pumpRole(layout.fueled(),host.getBlockPos());
                    assertPlannedSettings(profile,values,values,role,TegContinuousOperatingPlan.Stage.COLD_PREPARATION,host.getBlockPos().toString());
                     require(host.apcPowerDemandWatts()!=null&&host.apcPowerDemandWatts()==200d,"carrier pump retains ordinary 200 W load");
                }
                case HEATER -> require(!values.enabled().orElse(true),"cold preparation heater remains off");
                default -> { }
            }
        }
        var mixer=observed.placement().additional().atmosMachines().stream().filter(m->m.resolvedProfile().kind()==com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.MIXER).findFirst().orElseThrow();
        require(Math.abs(mixer.settingsSnapshot().mixerFractionB()-(1d-TegContinuousOperatingPlan.FUEL_OXYGEN_FRACTION))<=1e-12,
                "mixer B is complementary to configured oxygen fraction");
    }

    /** Shared read-only native/JVM assertion: live snapshot must equal the plan produced from its own current values. */
    static void assertPlannedSettings(com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile profile,
                                      com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings.Values current,
                                      com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings.Values actual,
                                      TegContinuousOperatingPlan.Role role,TegContinuousOperatingPlan.Stage stage,String context) {
        var original=current;
        var expected=TegContinuousOperatingPlan.create(role,profile,current,stage).finalValues();
        require(expected.equals(actual),"operating settings mismatch role="+role+" stage="+stage+" profile="+profile.kind()+" context="+context+" expected="+expected+" actual="+actual);
        require(current.equals(original),"read-only operating assertion mutated input role="+role+" stage="+stage+" context="+context);
    }

    private static TegContinuousOperatingPlan.Role pressurePumpRole(TegContinuousFuelPlan.Layout layout,BlockPos position) {
        if(position.equals(TegContinuousOperatingPlan.carrierPumpPositions(layout.fueled()).getFirst()))
            return TegContinuousOperatingPlan.Role.HOT_CARRIER_PUMP;
        if(position.equals(layout.devicePosition(TegContinuousFuelPlan.DeviceRole.COLD_PRESSURE_PUMP)))
            return TegContinuousOperatingPlan.Role.COLD_RETURN_PRESSURE_PUMP;
        throw new GameTestAssertException("unplanned pressure pump at "+position);
    }

    private static void assertCarrierVacuum(ServerLevel level,TegFixtureSavedData.Entry entry) {
        var observed=TegContinuousFixtureService.observeOwnedHardware(level,entry);
        for(int i=0;i<7;i++) {
            var component=observed.capture().componentFor(observed.topology().representatives().get(i)).orElseThrow();
            require(component.aggregate().gasMoles().isEmpty()&&component.aggregate().thermalEnergy()==0,
                    "unadmitted carrier component remains vacuum: "+i);
        }
    }

    private static void assertCarrierSeeds(TegContinuousFixtureService.ObservedHardware observed) {
        for(int loop=0;loop<2;loop++) {
            double total=0; GasType expected=loop==0?TegContinuousFuelPlan.HOT_CARRIER_GAS:TegContinuousFuelPlan.COLD_CARRIER_GAS;
            for(int i=loop==0?0:3;i<(loop==0?3:7);i++) {
                var component=observed.capture().componentFor(observed.topology().representatives().get(i)).orElseThrow();
                total+=component.aggregate().moles(expected);
                require(component.aggregate().gasMoles().keySet().equals(Set.of(expected)),"admitted carrier has only expected species "+expected+": "+i);
            }
            require(Math.abs(total-TegContinuousFuelPlan.CARRIER_MOLES)<=1e-5,"automatic admission seeds carrier loop with 43 mol "+expected+": "+total);
        }
    }

    private static void cleanupAutomatic(ServerLevel level,BlockPos origin,FixtureChunkTickets.Lease lease) {
        try {
            var data=TegFixtureSavedData.existing(level).orElse(null);
            if(data!=null) {
                var armed=data.automaticStartup(origin).orElse(null);
                if(armed!=null&&(armed.status()==TegFixtureSavedData.AutomaticStartupStatus.ACTIVE
                        ||armed.status()==TegFixtureSavedData.AutomaticStartupStatus.WAITING_START))
                    data.updateAutomaticStartup(origin,armed.status(),TegFixtureSavedData.AutomaticStartupStatus.FAILED,"native test cleanup after startup-admission proof");
                data.entry(origin).ifPresent(entry->TegContinuousFixtureService.quiesceOwned(level,entry));
            }
        } finally { lease.close(); }
    }

    private static void awaitReady(GameTestHelper helper,ServerLevel level,BlockPos origin,TegContinuousFuelPlan.Layout plan,
                                   FixtureChunkTickets.Lease lease,int ticks,boolean unknownLogged) {
        helper.runAfterDelay(1,()->{
            try {
                boolean loggedUnknown=unknownLogged;
                var entry=TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                var observed=TegContinuousFixtureService.observeOwnedHardware(level,entry);
                if(!PowerGraphService.topologyReady(level)||TegContinuousFixtureService.startupPowerFailure(level,observed)!=null) {
                    if(!loggedUnknown) {
                        logUnknownMainComponent(level,origin,plan,lease);
                        loggedUnknown=true;
                    }
                    if(ticks>=200)throw new GameTestAssertException("normal power graph did not settle: "+TegContinuousFixtureService.startupPowerFailure(level,observed)
                            +" receivers="+failedReceiverDiagnostics(level,observed)+" graph="+PowerGraphService.diagnosticSnapshot(level)
                            +" mainComponent="+mainComponentSnapshot(level,plan));
                    awaitReady(helper,level,origin,plan,lease,ticks+1,loggedUnknown);return;
                }
                var beforeInventory=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                var rooms=roomSnapshots(level,plan);
                var gas=AtmosphereService.INSTANCE;
                require(nonempty(level,gas,plan.roomA())||nonempty(level,gas,plan.roomB()),"ordinary source miner production is visible before admission");
                long admissionCommandTick=level.getGameTime();
                String result=TegContinuousFixtureService.start(level,origin);
                var afterAttempt=TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
                if(!afterAttempt.initialized()) {
                    var actualInventory=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                     var actualRooms=roomSnapshots(level,plan);
                     var output=(PowerDeviceBlockEntity)level.getBlockEntity(plan.fueled().electrical().devices().outputApc());
                     var auxiliary=(PowerDeviceBlockEntity)level.getBlockEntity(TegContinuousPowerPlan.equipment(plan).secondaryStartupApc());
                     require(output.energyJoules()==0,"refused operating attempt leaves APC at zero");
                     require(auxiliary!=null&&auxiliary.energyJoules()==0,"refused operating attempt leaves auxiliary APC at zero");
                    require(actualInventory.equals(beforeInventory),"refused operating attempt leaves pipe inventory unchanged");
                     require(sameRooms(rooms,actualRooms),"refused operating attempt leaves finite source/burn rooms unchanged");
                    var retryObservation=TegContinuousFixtureService.observeOwnedHardware(level,afterAttempt);
                    String pending=TegContinuousFixtureService.startupPowerFailure(level,retryObservation);
                    if(pending!=null) {
                        if(ticks>=200)throw new GameTestAssertException("normal power graph did not settle after operating refusal: "+pending);
                        awaitReady(helper,level,origin,plan,lease,ticks+1,loggedUnknown);return;
                    }
                    throw new GameTestAssertException("operating startup failed with settled power and unused token: "+result);
                }
                require(result.startsWith("Operating startup admitted once:"),"operating success: "+result);
                require(TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow().initialized(),"one-shot token consumed");
                assertOperatingSettings(level, TegContinuousFixtureService.observeOwnedHardware(level,
                        TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow()));
                var live=TegContinuousFixtureService.observeOwnedHardware(level,
                        TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow());
                var carrierPositions=TegContinuousOperatingPlan.carrierPumpPositions(live.layout().fueled());
                var coldPump=live.placement().baseMachines().stream().filter(m->m.getBlockPos().equals(carrierPositions.get(1))
                        &&m.resolvedProfile()!=null&&m.resolvedProfile().kind()==com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.VOLUME_PUMP).toList();
                require(coldPump.size()==1,"typed cold carrier position resolves to exactly one owned live volume pump");
                require(TegContinuousFixtureService.operatingStatus(level,origin).contains("coldCarrierPumpFlow="),
                        "read-only operating status reports live cold carrier pump flow");
                 var output=(PowerDeviceBlockEntity)level.getBlockEntity(plan.fueled().electrical().devices().outputApc());
                 var auxiliary=(PowerDeviceBlockEntity)level.getBlockEntity(TegContinuousPowerPlan.equipment(plan).secondaryStartupApc());
                 var bootstrap=TegContinuousFixtureService.startupBootstrapSplit(live);
                 require(level.getGameTime()==admissionCommandTick,"both APC stores are seeded within the original admission command tick");
                 require(output.energyJoules()==bootstrap.operatingJoules()&&auxiliary.energyJoules()==bootstrap.secondaryJoules(),
                         "both APCs immediately match the resolved proportional bootstrap allocation");
                 require(Math.abs(output.energyJoules()+auxiliary.energyJoules()-TegContinuousPowerPlan.TOTAL_BOOTSTRAP_JOULES)<=1e-6,
                         "the two actual APC stores sum to exactly the original 1 MJ total");
                 require(output.energyJoules()<=PowerDeviceRules.APC_CAPACITY_JOULES&&auxiliary.energyJoules()<=PowerDeviceRules.APC_CAPACITY_JOULES,
                         "neither bootstrap share exceeds its APC capacity");
                var after=TegContinuousFixtureService.observeOwnedHardware(level,TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow());
                double hotSeedMoles=0,coldSeedMoles=0;
                 require(after.topology().representatives().size()==10,"continuous fixture has exactly ten gas components after admission");
                 for(int i=0;i<after.topology().representatives().size();i++) {
                    var component=after.capture().componentFor(after.topology().representatives().get(i)).orElseThrow();
                     GasType expectedSpecies=i<3?TegContinuousFuelPlan.HOT_CARRIER_GAS:TegContinuousFuelPlan.COLD_CARRIER_GAS;
                     double carrierMoles=component.aggregate().moles(expectedSpecies);
                     if(i<7) {
                         double componentVolume=component.aggregate().volumeCubicMeters();
                        int loopStart=i<3?0:3,loopEnd=i<3?3:7;
                        double loopVolume=0;
                        for(int j=loopStart;j<loopEnd;j++) {
                            var loopComponent=after.capture().componentFor(after.topology().representatives().get(j)).orElseThrow();
                             loopVolume+=loopComponent.aggregate().volumeCubicMeters();
                        }
                        double expected=43*componentVolume/loopVolume;
                         require(Math.abs(carrierMoles-expected)<=1e-5,"carrier component "+i+" has volume-apportioned "+expectedSpecies+" "+expected+": "+carrierMoles);
                        require(Math.abs(component.aggregate().temperatureKelvin()-293.15)<=1e-5,"carrier component "+i+" remains at 293.15 K");
                         require(component.aggregate().gasMoles().keySet().equals(Set.of(expectedSpecies)),"carrier component "+i+" contains only "+expectedSpecies);
                         if(i<3)hotSeedMoles+=carrierMoles;else coldSeedMoles+=carrierMoles;
                     } else require(component.aggregate().gasMoles().isEmpty()&&component.aggregate().thermalEnergy()==0,"source/waste pipes remain vacuum at "+i);
                }
                 require(Math.abs(hotSeedMoles-TegContinuousFuelPlan.CARRIER_MOLES)<=1e-5,"hot carrier loop totals 43 mol N2: "+hotSeedMoles);
                 require(Math.abs(coldSeedMoles-TegContinuousFuelPlan.CARRIER_MOLES)<=1e-5,"cold carrier loop totals 43 mol plasma coolant: "+coldSeedMoles);
                 require(sameRooms(rooms,roomSnapshots(level,plan)),"all finite source/burn room mixtures unchanged");
                require(after.machineOwners().equals(entry.ownedMachines())&&after.gasOwners().equals(entry.ownedGas()),"all machine/pipe owner identities unchanged");
                 var postInventory=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                 double mainStoreAfterInit=output.energyJoules(),auxStoreAfterInit=auxiliary.energyJoules();
                var settingsBefore=settingsSnapshot(after);
                String repeatedOperating=TegContinuousFixtureService.start(level,origin);
                require(repeatedOperating.startsWith("Operating startup refused"),"repeat operating admission refused: "+repeatedOperating);
                var repeatedObserved=TegContinuousFixtureService.observeOwnedHardware(level,TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow());
                require(settingsBefore.equals(settingsSnapshot(repeatedObserved)),"repeat operating admission preserves exact settings revisions and values");
                 require(PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().equals(postInventory)
                         &&output.energyJoules()==mainStoreAfterInit&&auxiliary.energyJoules()==auxStoreAfterInit,"repeat operating admission preserves gas and both APC stores");
                 require(sameRooms(rooms,roomSnapshots(level,plan)),"repeat operating admission preserves finite source/burn room mixtures");
                // Test-only quiescence after every startup/repeat assertion: prevent this newly enabled fixture engine
                // from perturbing unrelated finite-batch tests; this is not initializer behavior or runtime proof.
                TegContinuousFixtureTestSupport.quiesceOperatingHardware(after);
                String repeated=TegContinuousFixtureService.initializeResources(level,origin);
                 require(repeated.startsWith("Resource initialization refused"),"repeat admission refused: "+repeated);
                 require(PipeGasInventorySavedData.existing(level).orElseThrow().snapshot().equals(postInventory)
                         &&output.energyJoules()==mainStoreAfterInit&&auxiliary.energyJoules()==auxStoreAfterInit,"repeat admission does not refill either APC");
                require(sameRooms(rooms,roomSnapshots(level,plan)),"repeat preserves source and finite-room mixtures");
                helper.succeed();
            } catch(Throwable failure) {
                com.juicyslew.moonstation14.MoonStation14.LOGGER.error("TEG continuous resource admission proof failed", failure);
                helper.fail(com.juicyslew.moonstation14.gametest.support.GameTestFailureMessage.concise("continuous resource admission proof failed: "+failure.getMessage()));
            }
        });
    }

    /** Bounded failure-only detail for the at-most eleven paid receivers; each graph query is indexed/read-only. */
    private static void logUnknownMainComponent(ServerLevel level,BlockPos origin,TegContinuousFuelPlan.Layout layout,
                                                FixtureChunkTickets.Lease lease) {
        var cables=TegContinuousPowerPlan.cables(origin);
        var plannedHosts=new java.util.ArrayList<BlockPos>();
        cables.forEach(node->plannedHosts.add(node.host()));plannedHosts.add(layout.core());
        layout.devices().forEach(device->plannedHosts.add(device.position()));
        int minX=plannedHosts.stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int maxX=plannedHosts.stream().mapToInt(BlockPos::getX).max().orElseThrow();
        int minZ=plannedHosts.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int maxZ=plannedHosts.stream().mapToInt(BlockPos::getZ).max().orElseThrow();
        var main=layout.fueled().electrical().devices().outputApc();
        var auxiliary=TegContinuousPowerPlan.equipment(layout).secondaryStartupApc();
        var source=mainOutputNode(level,layout,cables);
        String component=source==null?"unresolved-main-output-contact":PowerGraphService.componentDiagnosticSnapshot(level,source);
        var leased=lease.chunkPositions().stream().sorted(java.util.Comparator.comparingInt((net.minecraft.world.level.ChunkPos chunk)->chunk.x)
                .thenComparingInt(chunk->chunk.z)).toList();
        com.juicyslew.moonstation14.MoonStation14.LOGGER.info("TEG_RESOURCE_UNKNOWN_COMPONENT origin={} mainApc={} auxiliaryApc={} plannedHostBounds=x[{},{}]z[{},{}] cableNodes={} leaseChunks={} mainContact={} {}",
                origin,main,auxiliary,minX,maxX,minZ,maxZ,cables.size(),leased,source,component);
    }

    private static com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode mainOutputNode(ServerLevel level,
            TegContinuousFuelPlan.Layout layout,List<com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode> cables) {
        BlockPos apc=layout.fueled().electrical().devices().outputApc();
        var state=level.getBlockState(apc);
        var facing=state.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)
                ?state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING):net.minecraft.core.Direction.NORTH;
        var port=com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind.APC.ports(apc,facing).stream()
                .filter(p->p.tier()==com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC).findFirst().orElse(null);
        if(port==null)return null;
        return cables.stream().filter(node->com.juicyslew.moonstation14.ms14.power.topology.PowerTopology.deviceAdjacent(port,node))
                .findFirst().orElse(null);
    }

    private static String mainComponentSnapshot(ServerLevel level,TegContinuousFuelPlan.Layout layout) {
        var node=mainOutputNode(level,layout,TegContinuousPowerPlan.cables(layout.origin()));
        return node==null?"main output contact not found":PowerGraphService.componentDiagnosticSnapshot(level,node);
    }

    private static String failedReceiverDiagnostics(ServerLevel level,TegContinuousFixtureService.ObservedHardware observed) {
        var hosts=new java.util.ArrayList<net.minecraft.world.level.block.entity.BlockEntity>();
        hosts.add(observed.placement().core());
        for(var machine:observed.placement().baseMachines()) if(machine.apcPowerDemandWatts()!=null&&machine.apcPowerDemandWatts()>0)hosts.add(machine);
        for(var machine:observed.placement().additional().atmosMachines()) if(machine.apcPowerDemandWatts()!=null&&machine.apcPowerDemandWatts()>0)hosts.add(machine);
        hosts.addAll(observed.placement().additional().ventilation());
        var failures=new java.util.ArrayList<String>();
        for(var host:hosts) {
            int reach=host instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity v
                    ?v.prototype().extensionCableReceiver().localReachBlocks():0;
            String before=PowerGraphService.diagnosticSnapshot(level,host.getBlockPos(),reach,
                    com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC);
            var connection=com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.apcConnection(level,host);
            if(connection.known()&&observed.layout().fueled().electrical().devices().outputApc().equals(connection.provider()))continue;
            String after=PowerGraphService.diagnosticSnapshot(level,host.getBlockPos(),reach,
                    com.juicyslew.moonstation14.ms14.power.cable.CableTier.APC);
            String profile=host instanceof com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity m
                    ?m.resolvedProfile().kind().toString():host instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity v
                    ?v.prototype().kind().toString():host.getClass().getSimpleName();
            Object identity=host instanceof com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity m?m.placementIdentity():"n/a";
            String meter=host instanceof com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlockEntity v
                    ?com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.ventilationMeterDiagnostic(level,v)
                    :com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.apcPowerMeter(level,host).toString();
            failures.add(host.getBlockPos().toShortString()+"/"+profile+" identity="+identity+" sampleTick="
                    +com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime.currentSampleTick(level)+" topologyGeneration="
                    +connection.topologyGeneration()+" reason="+connection.reason()+" reach="+reach+" meter="+meter
                    +" graphBefore="+before+" graphAfter="+after);
        }
        return failures.stream().limit(11).toList().toString();
    }

    private static void assertOperatingSettings(ServerLevel level,TegContinuousFixtureService.ObservedHardware observed) {
        var hardware=observed.placement();
        var before=new java.util.ArrayList<com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity>();
        before.add(hardware.core());before.addAll(hardware.baseMachines());before.addAll(hardware.additional().atmosMachines());
        var snapshots=before.stream().collect(java.util.stream.Collectors.toMap(machine->machine,machine->machine.settingsSnapshot()));
         var bound=TegContinuousFixtureService.bindOperatingSettings(level,observed,TegContinuousOperatingPlan.Stage.STARTUP);
         var ignition=TegContinuousFixtureService.bindOperatingSettings(level,observed,TegContinuousOperatingPlan.Stage.IGNITION);
         var running=TegContinuousFixtureService.bindOperatingSettings(level,observed,TegContinuousOperatingPlan.Stage.RUNNING);
         var hotCarrierPosition=TegContinuousOperatingPlan.carrierPumpPositions(observed.layout().fueled()).getFirst();
         var hotCarrier=hardware.baseMachines().stream().filter(machine->machine.getBlockPos().equals(hotCarrierPosition)).findFirst().orElseThrow();
         var hotProfile=hotCarrier.resolvedProfile();
          var hotStartup=bound.atmosphere().stream().filter(host->host.blockEntity()==hotCarrier).findFirst().orElseThrow();
            require(hotProfile.kind()==com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.PRESSURE_PUMP
                           &&hotProfile.pump().mode()==com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.PumpMode.PRESSURE
                           &&hotProfile.power().watts()==200d&&hotProfile.ports().size()==2
                           &&hotProfile.pump().maxPressureKpa().orElseThrow()==4500d
                           &&hotStartup.plan().finalValues().enabled().orElse(false)
                           &&hotStartup.plan().finalValues().targetPressureKpa().orElse(-1d)==4500d
                           &&hotCarrier.settingsSnapshot().targetPressureKpa().orElse(-1d)==4500d,
                   "live continuous hot carrier consumes resolved 200 W two-port pressure-pump defaults at 4500 kPa during STARTUP");
          for(var stage:TegContinuousOperatingPlan.Stage.values()) {
              var binding=TegContinuousFixtureService.bindOperatingSettings(level,observed,stage);
              var hot=binding.atmosphere().stream().filter(host->host.blockEntity()==hotCarrier).findFirst().orElseThrow();
              boolean hotEnabled=stage!=TegContinuousOperatingPlan.Stage.COLD_PREPARATION;
              require(hot.plan().finalValues().enabled().orElse(false)==hotEnabled
                               &&hot.plan().finalValues().targetPressureKpa().orElse(-1d)==4500d,
                       "live hot pressure carrier stage gate/target: "+stage);
          }
         var planLayout=observed.layout();
         long expectedHosts=1L+planLayout.fueled().machines().size()+planLayout.devices().stream().filter(d->d.role().isAtmosMachine()).count();
         require(bound.atmosphere().size()==expectedHosts,"all typed atmospheric machine settings are bound: expected="+expectedHosts+" actual="+bound.atmosphere().size());
          Set<BlockPos> passiveHosts=new HashSet<>(planLayout.fueled().machines().stream().filter(m->m.kind()==TegFueledFixturePlan.MachineKind.EXCHANGER).map(TegFueledFixturePlan.Machine::position).toList());
          passiveHosts.addAll(planLayout.devices().stream().filter(d->d.role()==TegContinuousFuelPlan.DeviceRole.PASSIVE_HEAT_EXCHANGER
                   ||d.role()==TegContinuousFuelPlan.DeviceRole.HOT_HEAT_EXCHANGER).map(TegContinuousFuelPlan.Device::position).toList());
          long expectedPassive=passiveHosts.size();
          require(expectedPassive==8&&bound.atmosphere().stream().filter(host->passiveHosts.contains(host.blockEntity().getBlockPos())
                          &&host.blockEntity().resolvedProfile().kind()==com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.PASSIVE_HEAT_EXCHANGER).count()==expectedPassive,
                 "all array radiators and hot contacts remain passive resolved hosts");
         var actualPumpProfiles=new java.util.HashMap<BlockPos,com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile>();
         int actualPumpCount=0;
        var layout=observed.layout();
        for(var host:bound.atmosphere()) {
            var machine=host.blockEntity(); var profile=machine.resolvedProfile(); var values=machine.settingsSnapshot();
            require(machine.settingsValid()&&values!=null,"valid settings for "+profile.kind());
            switch(profile.kind()) {
                case TEG_CENTER -> require(values.enabled().orElse(false),"TEG core enabled");
                     case VOLUME_PUMP -> {
                       actualPumpCount++;
                       require(actualPumpProfiles.put(machine.getBlockPos(),profile)==null,"duplicate actual pump position");
                       var role=TegContinuousOperatingPlan.pumpRole(layout.fueled(),machine.getBlockPos());
                         assertPlannedSettings(profile,values,values,role,TegContinuousOperatingPlan.Stage.STARTUP,machine.getBlockPos().toString());
                        require(machine.apcPowerDemandWatts()!=null&&machine.apcPowerDemandWatts()==200d,"carrier pump retains its ordinary 200 W APC receiver");
                  }
                 case PRESSURE_PUMP -> {
                        actualPumpCount++;
                        require(actualPumpProfiles.put(machine.getBlockPos(),profile)==null,"duplicate actual pump position");
                         var role=pressurePumpRole(layout,machine.getBlockPos());
                         assertPlannedSettings(profile,values,values,role,TegContinuousOperatingPlan.Stage.STARTUP,machine.getBlockPos().toString());
                        require(machine.apcPowerDemandWatts()!=null&&machine.apcPowerDemandWatts()==200d,"pressure carrier retains ordinary 200 W APC receiver");
                 }
                 case HEATER -> assertPlannedSettings(profile,values,values,TegContinuousOperatingPlan.Role.IGNITION_HEATER,TegContinuousOperatingPlan.Stage.STARTUP,machine.getBlockPos().toString());
                case MIXER -> require(values.enabled().orElse(false)
                                &&Math.abs(values.targetPressureKpa().orElseThrow()-TegContinuousOperatingPlan.STARTUP_MIXER_TARGET_KPA)<=1e-12
                                &&Math.abs(values.mixerFractionA().orElseThrow()-TegContinuousOperatingPlan.FUEL_OXYGEN_FRACTION)<=1e-12
                                 &&Math.abs(values.mixerFractionB()-(1d-TegContinuousOperatingPlan.FUEL_OXYGEN_FRACTION))<=1e-12,
                        "mixer enabled at fixture-selected 12 kPa with configured rate-balanced oxygen/plasma ratio");
                case TEG_CIRCULATOR, PASSIVE_HEAT_EXCHANGER -> require(values.enabled().isEmpty(),"passive atmosphere device has no invented enable setting");
                default -> throw new GameTestAssertException("unexpected operating atmosphere kind "+profile.kind());
            }
        }
            require(actualPumpCount==actualPumpProfiles.size(),"actual pump roster positions are unique");
            try { TegContinuousOperatingPlan.validatePumpRoster(layout,actualPumpProfiles); }
            catch(IllegalArgumentException invalidRoster) { throw new GameTestAssertException("typed pump roster mismatch: "+invalidRoster.getMessage()); }
         var coldPosition=TegContinuousOperatingPlan.carrierPumpPositions(layout.fueled()).get(1);
        var coldIgnition=ignition.atmosphere().stream().filter(host->host.blockEntity().getBlockPos().equals(coldPosition)).findFirst()
                .orElseThrow(()->new GameTestAssertException("cold carrier pump missing from ignition binding"));
           require(coldIgnition.plan().finalValues().enabled().orElse(false)
                         &&coldIgnition.plan().finalValues().transferRateM3PerSecond().equals(
                          java.util.Optional.of(0.04d)),
                    "live ignition binding enables cold circulation at canonical 0.04 configured rate");
         require(coldIgnition.blockEntity().apcPowerDemandWatts()!=null&&coldIgnition.blockEntity().apcPowerDemandWatts()==200d,
                  "live ignition binding keeps the ordinary paid 200 W cold-pump receiver");
        var coldStartup=bound.atmosphere().stream().filter(host->host.blockEntity().getBlockPos().equals(coldPosition)).findFirst()
                .orElseThrow(()->new GameTestAssertException("cold carrier pump missing from startup binding"));
        require(!coldStartup.plan().finalValues().enabled().orElse(true),"live startup binding keeps cold carrier flow off");
        var coldRunning=running.atmosphere().stream().filter(host->host.blockEntity().getBlockPos().equals(coldPosition)).findFirst()
                .orElseThrow(()->new GameTestAssertException("cold carrier pump missing from running binding"));
        require(coldRunning.plan().finalValues().enabled().orElse(false)
                        &&coldRunning.plan().finalValues().transferRateM3PerSecond().equals(
                         java.util.Optional.of(.2d/TegContinuousCoolingPlan.RADIATOR_COUNT)),
                 "live running binding keeps cold carrier flow enabled at canonical 0.04 stage rate");
        for(var entry:snapshots.entrySet()) require(entry.getKey().settingsSnapshot().equals(entry.getValue())
                        &&entry.getKey().settingsRevision()==entry.getValue().revision(),
                "read-only startup/ignition/running bindings preserve settings snapshot/revision at "+entry.getKey().getBlockPos());
        int checked=0;
        for(var device:layout.devices()) {
            if(device.role()!=TegContinuousFuelPlan.DeviceRole.SCRUBBER
                    &&device.role()!=TegContinuousFuelPlan.DeviceRole.WASTE_PUMP
                    &&device.role()!=TegContinuousFuelPlan.DeviceRole.INJECTOR
                    &&device.role()!=TegContinuousFuelPlan.DeviceRole.VENT) continue;
            var vent=observed.placement().additional().ventilation().stream()
                    .filter(candidate->candidate.getBlockPos().equals(device.position())).findFirst()
                    .orElseThrow(()->new GameTestAssertException("ventilation host missing at "+device.position()));
            var settings=vent.config().settings();
            require(vent.settingsValid(),"valid configured ventilation at "+device.position());
            switch(device.role()) {
                case SCRUBBER -> {
                    require(settings.configuredEnabled(),"source scrubber configured enabled");
                    var expectedGases=layout.roomA().contains(device.position())?Set.of(GasType.OXYGEN):Set.of(GasType.PLASMA);
                    require(settings.scrubberMode()==com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.ScrubberMode.FILTER
                                    &&settings.selectedLayer()==com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Layer.PRIMARY
                                    &&settings.filteredGases().equals(expectedGases),"source scrubber filter configuration at "+device.position());
                    var defaults=new com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig(
                            com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Kind.SCRUBBER,vent.prototype()).settings();
                    require(settings.volumeRateM3PerSecond()==defaults.volumeRateM3PerSecond(),"scrubber resolved volume rate");
                }
                case WASTE_PUMP -> {
                    var wasteRole=TegContinuousOperatingPlan.VentilationRole.BURNER_WASTE;
                    var startupWaste=TegContinuousOperatingPlan.proposeVentilation(wasteRole,vent.prototype(),settings,0d,
                            TegContinuousOperatingPlan.Stage.STARTUP).settings();
                    var ignitionWaste=TegContinuousOperatingPlan.proposeVentilation(wasteRole,vent.prototype(),settings,0d,
                            TegContinuousOperatingPlan.Stage.IGNITION).settings();
                    var runningWaste=TegContinuousOperatingPlan.proposeVentilation(wasteRole,vent.prototype(),settings,0d,
                            TegContinuousOperatingPlan.Stage.RUNNING).settings();
                    require(settings.configuredEnabled()==startupWaste.configuredEnabled()&&!startupWaste.configuredEnabled()
                                    &&ignitionWaste.configuredEnabled()&&runningWaste.configuredEnabled(),
                            "waste scrubber follows cold/startup-off and ignition/running-on operating proposals");
                    var gases=EnumSet.allOf(GasType.class);gases.remove(GasType.OXYGEN);gases.remove(GasType.PLASMA);
                     require(settings.scrubberMode()==com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.ScrubberMode.SIPHON
                                      &&settings.selectedLayer()==com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Layer.PRIMARY
                                     &&settings.filteredGases().equals(gases)
                                     &&settings.gasesToScrub().equals(Set.of(GasType.values())),"waste pump siphons all species including residual O2/plasma");
                    var defaults=new com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig(
                            com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Kind.SCRUBBER,vent.prototype()).settings();
                    require(settings.volumeRateM3PerSecond()==defaults.volumeRateM3PerSecond(),"waste scrubber resolved volume rate");
                    var backnode=new com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.NodeKey(
                            vent.pipePortPos(),com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY);
                    require(observed.topology().components().get(2).contains(backnode),
                            "live waste scrubber backnode belongs to hot inlet component 2 on PRIMARY");
                }
                case INJECTOR -> {
                    require(!settings.configuredEnabled(),"burner supply remains disabled during cold preheat");
                    var defaults=new com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig(
                            com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Kind.VENT,vent.prototype()).settings();
                    require(settings.selectedLayer()==com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Layer.PRIMARY
                                     &&Math.abs(settings.targetPressureKPa()-TegContinuousOperatingPlan.IGNITION_BURNER_SUPPLY_TARGET_KPA)<=1e-12
                                      &&settings.targetPressureChangeKPaPerSecond()==defaults.targetPressureChangeKPaPerSecond(),"burner injector remains disabled at canonical 5 kPa target during startup");
                }
                case VENT -> {
                    var startupDischarge=TegContinuousOperatingPlan.proposeVentilation(
                            TegContinuousOperatingPlan.VentilationRole.EXTERIOR_DISCHARGE,vent.prototype(),settings,
                            TegContinuousOperatingPlan.exteriorAmbientPressure(
                                    AtmosphereService.INSTANCE.exteriorBoundaryForImmediateTransfer(level,
                                            layout.wasteVentBody().relative(layout.wasteVentFacing()))
                                            .orElseThrow(()->new GameTestAssertException("exterior boundary missing at "+layout.wasteVentBody()))
                                            .kind(),0d),TegContinuousOperatingPlan.Stage.STARTUP).settings();
                    require(settings.configuredEnabled()==startupDischarge.configuredEnabled()&&!startupDischarge.configuredEnabled(),
                            "exterior discharge remains closed during startup per operating proposal");
                    var backnode=new com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.NodeKey(
                            vent.pipePortPos(),com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology.Layer.PRIMARY);
                    require(vent.getBlockPos().equals(layout.wasteVentBody())
                                    &&observed.topology().components().get(0).contains(backnode),
                            "live exterior vent backnode belongs to hot outlet component 0 on PRIMARY");
                    var front=layout.wasteVentBody().relative(layout.wasteVentFacing());
                    var boundary=AtmosphereService.INSTANCE.exteriorBoundaryForImmediateTransfer(level,front)
                            .orElseThrow(()->new GameTestAssertException("exterior boundary missing at "+front));
                    require(boundary.validate(),"exterior boundary remains valid");
                     double ambient=TegContinuousOperatingPlan.exteriorAmbientPressure(boundary.kind(),boundary.ambient()==null?0d:boundary.ambient().pressureKpa());
                     require(settings.selectedLayer()==com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Layer.PRIMARY
                                    &&settings.targetPressureKPa()>ambient&&settings.externalPressureBoundKPa()>ambient,
                            "exterior vent pressures exceed ambient");
                    var defaults=new com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig(
                            com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceConfig.Kind.VENT,vent.prototype()).settings();
                    require(settings.targetPressureChangeKPaPerSecond()==defaults.targetPressureChangeKPaPerSecond(),"exterior vent resolved pressure rate");
                }
                default -> { }
            }
            checked++;
        }
        require(checked==5,"five ventilation roles checked: "+checked);
        for(var phase:List.of(bound,ignition,running)) {
            var injector=phase.ventilation().stream().filter(b->b.proposal().settings().targetPressureKPa()==TegContinuousOperatingPlan.IGNITION_BURNER_SUPPLY_TARGET_KPA).findFirst()
                    .orElseThrow(()->new GameTestAssertException("canonical burner supply proposal missing from phase binding"));
            boolean enabled=phase==bound?false:true;
            require(injector.proposal().settings().configuredEnabled()==enabled,"burner supply phase enablement matches startup/ignition/running plan");
        }
    }

    private static Map<BlockPos,Object> settingsSnapshot(TegContinuousFixtureService.ObservedHardware observed) {
        var result=new HashMap<BlockPos,Object>();
        var machines=new ArrayList<com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity>();
        machines.add(observed.placement().core()); machines.addAll(observed.placement().baseMachines());
        machines.addAll(observed.placement().additional().atmosMachines());
        for(var machine:machines) result.put(machine.getBlockPos(),machine.settingsSnapshot());
        for(var vent:observed.placement().additional().ventilation()) result.put(vent.getBlockPos(),vent.config().settings());
         require(result.size()==machines(observed).size()+observed.placement().additional().ventilation().size(),"snapshot captures every typed atmosphere and ventilation owner");
        return Map.copyOf(result);
    }

    private static List<com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity> machines(TegContinuousFixtureService.ObservedHardware observed) {
        var result=new ArrayList<com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity>();
        result.add(observed.placement().core()); result.addAll(observed.placement().baseMachines());
        result.addAll(observed.placement().additional().atmosMachines()); return result;
    }

    private static TegContinuousFuelPlan.Layout layout(ServerLevel level,BlockPos origin) {
        var catalog=com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.catalog(level);
        var mixer=catalog.get(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.id(ModBlocks.GAS_MIXER.get().profileIdentity()));
        var radiator=catalog.get(com.juicyslew.moonstation14.ms14.atmos.machine.ModAtmosMachines.id(ModBlocks.PASSIVE_HEAT_EXCHANGER.get().profileIdentity()));
        if(mixer==null||radiator==null)throw new GameTestAssertException("continuous resolved profiles missing");
        return TegContinuousFuelPlan.layout(origin,mixer,radiator);
    }
    private static List<List<com.juicyslew.moonstation14.ms14.atmos.core.GasMixture>> roomSnapshots(ServerLevel level,TegContinuousFuelPlan.Layout p) {
        var a=AtmosphereService.INSTANCE;var result=new ArrayList<List<com.juicyslew.moonstation14.ms14.atmos.core.GasMixture>>();
         require(p.fueled().coldRoomInterior().isEmpty()&&p.fueled().coldWallShell().isEmpty(),"external cooling plan has no sealed cold room");
         for(var room:List.of(p.fueled().roomInterior(),p.roomA(),p.roomB())) {
            var values=new ArrayList<com.juicyslew.moonstation14.ms14.atmos.core.GasMixture>();
            for(BlockPos cell:room)values.add(a.finiteRoomEndpointForTrustedSimulation(level,cell).orElseThrow().snapshot());
            result.add(List.copyOf(values));
        }
        return List.copyOf(result);
    }
    private static boolean sameRooms(List<List<com.juicyslew.moonstation14.ms14.atmos.core.GasMixture>> a,List<List<com.juicyslew.moonstation14.ms14.atmos.core.GasMixture>> b) {
        for(int r=0;r<a.size();r++)for(int i=0;i<a.get(r).size();i++) {
            var x=a.get(r).get(i);var y=b.get(r).get(i);
            if(!x.gasMoles().equals(y.gasMoles())||x.thermalEnergy()!=y.thermalEnergy()||x.temperatureKelvin()!=y.temperatureKelvin())return false;
        }
        return true;
    }
    private static boolean nonempty(ServerLevel level,AtmosphereService atmosphere,Set<BlockPos> room) {
        return room.stream().map(p->atmosphere.finiteRoomEndpointForTrustedSimulation(level,p).orElseThrow().snapshot())
                .anyMatch(gas->!gas.gasMoles().isEmpty()||gas.thermalEnergy()!=0);
    }
    private static void require(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
}
