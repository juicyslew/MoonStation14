package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.FixtureChunkTickets;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock;
import com.juicyslew.moonstation14.ms14.atmos.piping.VentilationDeviceBlock;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class TegContinuousExteriorSiteGameTests {
    private static TegFixtureRegistryScope commandRegistryScope;
    @BeforeBatch(batch="teg_continuous_public_command")
    public static void setupCommandBatch(ServerLevel level) { commandRegistryScope=TegFixtureRegistryScope.acquire(level); }
    @AfterBatch(batch="teg_continuous_public_command")
    public static void closeCommandBatch(ServerLevel level) { if(commandRegistryScope!=null){commandRegistryScope.close();commandRegistryScope=null;} }
    private TegContinuousExteriorSiteGameTests() { }

    @GameTest(template = "disposal_power_clear", templateNamespace = "moonstation14", timeoutTicks = 200)
    @GameTestTags("teg-fixture-construction")
    public static void exteriorSiteIsRecheckedAgainstLiveRadiatorAndWasteBoundaries(GameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos templateOrigin = helper.absolutePos(BlockPos.ZERO);
        BlockPos far = templateOrigin.offset(48, 8, 48);
        var lease = FixtureChunkTickets.acquire(helper, new AABB(templateOrigin.getX(), templateOrigin.getY(), templateOrigin.getZ(), far.getX(), far.getY(), far.getZ()).inflate(2));
        FixtureChunkTickets.awaitEntityTicking(helper, lease, 40, () -> true,
                "continuous TEG exterior site chunks", () -> {
                     BlockPos anchor=TegContinuousFixtureTestSupport.prepareOutdoorFootprint(level,templateOrigin).origin();
                     int x = anchor.getX(), z = anchor.getZ();
                    level.getChunk(x >> 4, z >> 4);
                    int radiatorY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + 22, z + 19) + 2;
                    int ventY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + 7, z + 22) + 2;
                    int originY = Math.max(radiatorY - 7, ventY - 2);
                    BlockPos origin = new BlockPos(x, originY, z);
                    TegContinuousFuelPlan.Layout layout = TegContinuousFuelPlan.layout(origin);
                    BlockPos radiator = layout.radiatorPositions().iterator().next();
                    BlockPos ventBody = layout.wasteVentBody();
                    BlockPos wasteFront = ventBody.relative(layout.wasteVentFacing());
                    require(level.hasChunkAt(radiator) && level.hasChunkAt(wasteFront), "all boundary columns remain loaded");
                    require(level.getBlockState(radiator).isAir() && level.getBlockState(wasteFront).isAir(), "candidate cells start clear");
                     require(AtmosphereService.INSTANCE.exteriorBoundaryForImmediateTransfer(level, radiator).isPresent(), "radiator candidate is actual exposed exterior");
                     require(AtmosphereService.INSTANCE.finiteCellEndpointForImmediateTransfer(level, radiator).isEmpty(), "radiator candidate is not also an owned finite-room heat reservoir");
                    require(AtmosphereService.INSTANCE.exteriorBoundaryForImmediateTransfer(level, wasteFront).isPresent(), "waste-front candidate is actual exposed exterior");
                     String failure=TegFixturePlacement.continuousExteriorFailure(level, layout);
                     require(failure==null,TegContinuousFixtureTestSupport.exteriorDiagnostic(level,layout,"air candidates refused: "+failure));

                    level.setBlock(radiator, ModBlocks.PASSIVE_HEAT_EXCHANGER.get().defaultBlockState()
                            .setValue(AtmosMachineBlock.FACING, Direction.WEST), 3);
                    level.setBlock(ventBody, ModBlocks.GAS_VENT.get().defaultBlockState().setValue(VentilationDeviceBlock.FACING, Direction.SOUTH), 3);
                     failure=TegFixturePlacement.continuousExteriorFailure(level, layout);
                     require(failure==null,TegContinuousFixtureTestSupport.exteriorDiagnostic(level,layout,
                             "ordinary radiator and vent placement refused: "+failure));
                    level.setBlock(wasteFront, Blocks.STONE.defaultBlockState(), 3);
                    require(TegFixturePlacement.continuousExteriorFailure(level, layout) != null, "blocked waste front refuses site");
                    level.setBlock(wasteFront, Blocks.AIR.defaultBlockState(), 3);
                     failure=TegFixturePlacement.continuousExteriorFailure(level, layout);
                     require(failure==null,TegContinuousFixtureTestSupport.exteriorDiagnostic(level,layout,
                             "freshly restored waste boundary refused: "+failure));
                    level.setBlock(radiator, Blocks.STONE.defaultBlockState(), 3);
                    require(TegFixturePlacement.continuousExteriorFailure(level, layout) != null, "blocked radiator host refuses site");
                    helper.succeed();
                 }, templateOrigin);
    }

    @GameTest(template = "disposal_power_clear", templateNamespace = "moonstation14", timeoutTicks = 500, batch="teg_continuous_public_command")
    @GameTestTags({"teg-command-ux", "teg-fixture-construction"})
    public static void publicDefaultBuildUsesContinuousModeAndOperatorStartupConsumesOnce(GameTestHelper helper) {
        ServerLevel level=(ServerLevel)helper.getLevel();BlockPos templateOrigin=helper.absolutePos(BlockPos.ZERO);
        BlockPos far=templateOrigin.offset(48,8,48);
        var ownedOrigin=new java.util.concurrent.atomic.AtomicReference<BlockPos>();
        var lease=FixtureChunkTickets.acquire(helper,new AABB(templateOrigin.getX(),templateOrigin.getY(),templateOrigin.getZ(),far.getX(),far.getY(),far.getZ()).inflate(2),()->{
            BlockPos cleanupOrigin=ownedOrigin.get();
            if(cleanupOrigin==null)return;
            var entry=TegFixtureSavedData.existing(level).flatMap(data->data.entry(cleanupOrigin)).orElse(null);
            if(entry!=null&&cleanupOrigin.equals(entry.origin()))
                TegContinuousFixtureTestSupport.quiesceOperatingHardware(TegContinuousFixtureService.observeOwnedHardware(level,entry));
        });
        FixtureChunkTickets.awaitEntityTicking(helper,lease,40,()->true,"default continuous command fixture chunks",()->{
            final BlockPos origin;
            try { origin=TegContinuousFixtureTestSupport.prepareOutdoorFootprint(level,templateOrigin).origin(); }
            catch(RuntimeException failure){lease.close();throw failure;}
            ownedOrigin.set(origin);
            var body=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"teg-default-command-player"));
            body.setPos(origin.getX()-3.5,origin.getY()+2,origin.getZ()-3.5);
            if(!level.addFreshEntity(body)){lease.close();throw new GameTestAssertException("fake player could not be spawned");}
            var dispatcher=level.getServer().getCommands().getDispatcher();
            var source=level.getServer().createCommandSourceStack().withLevel(level).withEntity(body).withPermission(2);
            try {
                if(dispatcher.execute("ms14 teg_fixture build",source)!=1)throw new GameTestAssertException("public default player build did not construct continuous hardware");
                var entry=TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow(()->new GameTestAssertException("default build ownership missing"));
                if(entry.mode()!=TegFixtureSavedData.Mode.CONTINUOUS||entry.phase()!=TegFixtureSavedData.ContinuousPhase.BUILT||entry.initialized())
                    throw new GameTestAssertException("default build must persist an uninitialized continuous BUILT token: "+entry);
                var inventory=com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
                for(var owner:entry.ownedGas().keySet())if(inventory.get(owner).map(g->inventory.nodeShare(owner).totalMoles()!=0).orElse(true))
                    throw new GameTestAssertException("construction seeded gas before operator start at "+owner);
                var data=TegFixtureSavedData.forMutation(level);
                var finiteOrigin=body.blockPosition();var ids=new java.util.ArrayList<java.util.UUID>();for(int i=0;i<4;i++)ids.add(java.util.UUID.randomUUID());
                var finite=new TegFixtureSavedData.Entry(finiteOrigin,ids.get(0),ids.get(1),ids.get(2),ids.get(3),false,TegFixtureSavedData.Mode.FUELED);
                if(!data.recordBuilt(finite))throw new GameTestAssertException("could not add nearby finite status decoy");
                if(!origin.equals(TegFixtureCommands.nearestContinuousOrigin(data.entries(),body.blockPosition())))
                    throw new GameTestAssertException("default continuous status selector chose finite fixture");
                var messages=new java.util.ArrayList<String>();
                var captured=source.withSource(new net.minecraft.commands.CommandSource(){
                    @Override public void sendSystemMessage(net.minecraft.network.chat.Component message){messages.add(message.getString());}
                    @Override public boolean acceptsSuccess(){return true;}
                    @Override public boolean acceptsFailure(){return true;}
                    @Override public boolean shouldInformAdmins(){return false;}
                });
                dispatcher.execute("ms14 teg_fixture status",captured);
                if(messages.isEmpty()||!messages.get(0).toLowerCase(java.util.Locale.ROOT).contains("continuous"))
                    throw new GameTestAssertException("default status did not select continuous fixture over nearer finite descriptor: "+messages);
                int startResult=dispatcher.execute("ms14 teg_fixture start",source);
                if(startResult!=1)throw new GameTestAssertException("public automatic startup command was refused");
                var armed=data.automaticStartup(origin).orElseThrow(()->new GameTestAssertException("public start did not save automatic arm"));
                if(armed.status()!=TegFixtureSavedData.AutomaticStartupStatus.WAITING_START||armed.armTick()!=level.getGameTime())
                    throw new GameTestAssertException("public start did not save its one-shot arm tick: "+armed);
                var admittedEntry=data.entry(origin).orElseThrow();
                awaitDefaultStartup(helper,level,body,origin,dispatcher,source,lease,0,armed, startupSettings(TegContinuousFixtureService.observeOwnedHardware(level,admittedEntry)));
            } catch(Throwable failure){quiesceDefaultHardware(level,body,origin,lease);helper.fail("default continuous build/start command: "+failure.getMessage());}
        },new BlockPos[]{templateOrigin,far});
    }

    private static void awaitDefaultStartup(GameTestHelper helper,ServerLevel level,net.minecraft.world.entity.player.Player body,
            BlockPos origin,com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
            net.minecraft.commands.CommandSourceStack source,FixtureChunkTickets.Lease lease,int waited,
            TegFixtureSavedData.AutomaticStartup originalArm,java.util.Map<BlockPos,Object> builtSettings) {
        try {
            var currentEntry=TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow();
            var currentObserved=TegContinuousFixtureService.observeOwnedHardware(level,currentEntry);
            var currentSettings=startupSettings(currentObserved);
            var data=TegFixtureSavedData.existing(level).orElseThrow();var started=data.entry(origin).orElseThrow();
            var receipt=data.automaticStartup(origin).orElseThrow();
            if(receipt.armTick()!=originalArm.armTick())throw new GameTestAssertException("automatic arm tick changed");
            if(receipt.status()!=TegFixtureSavedData.AutomaticStartupStatus.PREPARING_COLD
                    ||started.phase()!=TegFixtureSavedData.ContinuousPhase.STARTUP||!started.initialized()) {
                if(waited>=200)throw new GameTestAssertException("ordinary controller did not reach cold preparation after one public start");
                helper.runAfterDelay(1,()->awaitDefaultStartup(helper,level,body,origin,dispatcher,source,lease,waited+1,originalArm,builtSettings));return;
            }
            if(currentSettings.equals(builtSettings))throw new GameTestAssertException("cold preparation did not apply its stage-plan controls");
            assertCanonicalColdPreparation(currentObserved);
            assertAdmittedCarrier(currentObserved);
            var inventoryData=com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData.existing(level).orElseThrow();
            var gas=inventoryData.snapshot();
             var plan=TegFueledFixturePlan.layout(origin.offset(22,2,18));
             var output=(com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity)level.getBlockEntity(plan.electrical().devices().outputApc());
              var auxiliary=(com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity)level.getBlockEntity(TegContinuousPowerPlan.equipment(currentObserved.layout()).secondaryStartupApc());
             double apcBefore=output==null?-1:output.energyJoules();
             double auxiliaryBefore=auxiliary==null?-1:auxiliary.energyJoules();
            var second=dispatcher.execute("ms14 teg_fixture start",source);
            var afterEntry=data.entry(origin).orElseThrow();var afterReceipt=data.automaticStartup(origin).orElseThrow();
            if(second!=0||!started.equals(afterEntry)||!receipt.equals(afterReceipt))throw new GameTestAssertException("repeat start must refuse without resetting token or automatic arm");
            if(!currentSettings.equals(startupSettings(TegContinuousFixtureService.observeOwnedHardware(level,afterEntry))))throw new GameTestAssertException("repeat start reconfigured cold-preparation controls");
            for(var owner:started.ownedGas().keySet())if(!gas.get(owner).equals(inventoryData.snapshot().get(owner)))
                throw new GameTestAssertException("repeat start changed gas resources at "+owner);
             if(output==null||output.energyJoules()!=apcBefore)throw new GameTestAssertException("repeat start changed APC energy");
             if(auxiliary==null||auxiliary.energyJoules()!=auxiliaryBefore)throw new GameTestAssertException("repeat start changed the isolated auxiliary APC store");
             TegContinuousFixtureTestSupport.quiesceOperatingHardware(currentObserved);
             stopAutomaticForScopeCleanup(level,origin);
             body.discard();lease.close();helper.succeed();
        } catch(Throwable failure){quiesceDefaultHardware(level,body,origin,lease);helper.fail("public continuous startup: "+failure.getMessage());}
    }

    private static void quiesceDefaultHardware(ServerLevel level,net.minecraft.world.entity.player.Player body,BlockPos origin,FixtureChunkTickets.Lease lease) {
        boolean quiesced=false;
        try {
            var entry=TegFixtureSavedData.existing(level).flatMap(data->data.entry(origin)).orElse(null);
            if(entry!=null) { TegContinuousFixtureTestSupport.quiesceOperatingHardware(TegContinuousFixtureService.observeOwnedHardware(level,entry)); quiesced=true; }
        } catch(RuntimeException cleanupFailure) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error("TEG public-start fixture quiescence failed before scope unload origin={}",origin,cleanupFailure);
        }
        if(quiesced)stopAutomaticForScopeCleanup(level,origin);
        body.discard();lease.close();
    }

    private static void stopAutomaticForScopeCleanup(ServerLevel level,BlockPos origin) {
        var data=TegFixtureSavedData.existing(level).orElse(null);
        var receipt=data==null?null:data.automaticStartup(origin).orElse(null);
        if(receipt!=null&&receipt.status()!=TegFixtureSavedData.AutomaticStartupStatus.COMPLETE
                &&receipt.status()!=TegFixtureSavedData.AutomaticStartupStatus.FAILED)
            data.updateAutomaticStartup(origin,receipt.status(),TegFixtureSavedData.AutomaticStartupStatus.FAILED,
                    "test scope cleanup after verified public-start admission");
    }

    private static java.util.Map<BlockPos,Object> startupSettings(TegContinuousFixtureService.ObservedHardware observed) {
        var result=new java.util.LinkedHashMap<BlockPos,Object>();
        var machines=new java.util.ArrayList<com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity>();
        machines.add(observed.placement().core());machines.addAll(observed.placement().baseMachines());
        machines.addAll(observed.placement().additional().atmosMachines());
        for(var machine:machines)result.put(machine.getBlockPos(),machine.settingsSnapshot());
        for(var vent:observed.placement().additional().ventilation())result.put(vent.getBlockPos(),vent.config().settings());
        return java.util.Map.copyOf(result);
    }

    private static void assertCanonicalColdPreparation(TegContinuousFixtureService.ObservedHardware observed) {
        var layout=observed.layout().fueled();
        for(var machine:observed.placement().baseMachines()) {
            var profile=machine.resolvedProfile();
            var kind=profile.kind();
            TegContinuousOperatingPlan.Role role=null;
            if(kind==com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.VOLUME_PUMP
                    ||kind==com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.PRESSURE_PUMP)
                role=TegContinuousOperatingPlan.pumpRole(layout,machine.getBlockPos());
            else if(kind==com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.HEATER)
                role=TegContinuousOperatingPlan.Role.IGNITION_HEATER;
            if(role!=null) {
                var plan=TegContinuousOperatingPlan.create(role,profile,machine.settingsSnapshot(),TegContinuousOperatingPlan.Stage.COLD_PREPARATION);
                if(!plan.finalValues().equals(machine.settingsSnapshot()))
                    throw new GameTestAssertException("cold-preparation controls differ from canonical resolved-profile plan for "+role);
            }
        }
        for(var machine:observed.placement().additional().atmosMachines())
            if(machine.resolvedProfile().kind()==com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.MIXER
                    &&!TegContinuousOperatingPlan.create(TegContinuousOperatingPlan.Role.MIXER,machine.resolvedProfile(),machine.settingsSnapshot(),TegContinuousOperatingPlan.Stage.COLD_PREPARATION).finalValues().equals(machine.settingsSnapshot()))
                throw new GameTestAssertException("cold-preparation mixer differs from canonical resolved-profile plan");
    }

    private static void assertAdmittedCarrier(TegContinuousFixtureService.ObservedHardware observed) {
        for(int loop=0;loop<2;loop++) {
            var expected=loop==0?TegContinuousFuelPlan.HOT_CARRIER_GAS:TegContinuousFuelPlan.COLD_CARRIER_GAS;
            double total=0;
            for(int i=loop==0?0:3;i<(loop==0?3:7);i++) {
                var component=observed.capture().componentFor(observed.topology().representatives().get(i)).orElseThrow();
                total+=component.aggregate().moles(expected);
                if(!component.aggregate().gasMoles().keySet().equals(java.util.Set.of(expected)))
                    throw new GameTestAssertException("admitted carrier component has unexpected species: "+i);
            }
            if(Math.abs(total-TegContinuousFuelPlan.CARRIER_MOLES)>1e-5)
                throw new GameTestAssertException("one-shot admission must seed 43 mol "+expected+", observed "+total);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
