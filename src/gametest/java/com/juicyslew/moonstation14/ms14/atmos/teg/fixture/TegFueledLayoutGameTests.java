package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.ShutdownDiagnostics;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.piping.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.cable.CableTier;
import com.juicyslew.moonstation14.ms14.power.topology.CableFaceNode;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;

/** Native combined TEG fixture proof: exact gas topology, finite combustion, thermal conversion and real load delivery. */
@PrefixGameTestTemplate(false)
@GameTestTags({"atmosphere","teg","teg-fueled-layout","teg-fueled-power-layout"})
public final class TegFueledLayoutGameTests {
    private static TegFixtureRegistryScope playerBuildRegistryScope;
    private static TegFixtureRegistryScope productionBuildRegistryScope;
    @BeforeBatch(batch="teg_fueled_player_command")
    public static void setupPlayerBuildBatch(ServerLevel level) { playerBuildRegistryScope=TegFixtureRegistryScope.acquire(level); }
    @AfterBatch(batch="teg_fueled_player_command")
    public static void closePlayerBuildBatch(ServerLevel level) { if(playerBuildRegistryScope!=null){playerBuildRegistryScope.close();playerBuildRegistryScope=null;} }
    @BeforeBatch(batch="teg_fueled_production_build")
    public static void setupProductionBuildBatch(ServerLevel level) { productionBuildRegistryScope=TegFixtureRegistryScope.acquire(level); }
    @AfterBatch(batch="teg_fueled_production_build")
    public static void closeProductionBuildBatch(ServerLevel level) { if(productionBuildRegistryScope!=null){productionBuildRegistryScope.close();productionBuildRegistryScope=null;} }
    private TegFueledLayoutGameTests() { }

    @GameTest(template="disposal_power_clear",templateNamespace="moonstation14",timeoutTicks=200)
    @GameTestTags({"teg-fueled-build","teg-command-ux"})
    public static void publicFueledBuildCommandCombinesSetupAndRefusesReset(GameTestHelper h) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level=(ServerLevel)h.getLevel();BlockPos origin=h.absolutePos(BlockPos.ZERO);
        for(int x=0;x<48;x++)for(int y=0;y<8;y++)for(int z=0;z<48;z++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3); for(int x=0;x<48;x++)for(int z=0;z<48;z++)level.setBlock(origin.offset(x,1,z),Blocks.STONE.defaultBlockState(),3);
        var dispatcher=level.getServer().getCommands().getDispatcher();var console=level.getServer().createCommandSourceStack().withLevel(level).withPermission(2);
        String coords=origin.getX()+" "+origin.getY()+" "+origin.getZ();
        try{dispatcher.execute("ms14 teg_fixture finite build "+coords,console.withPermission(0));throw new GameTestAssertException("permission 0 accepted");}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException expected) { }
        if(dispatcher.execute("ms14 teg_fixture finite build",console)!=0)throw new GameTestAssertException("console without coordinates must be refused");
        int built=dispatcher.execute("ms14 teg_fixture finite build "+coords,console);
        var entry=TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow(()->new GameTestAssertException("public build did not persist fixture"));
        if(built!=1||entry.mode()!=TegFixtureSavedData.Mode.FUELED||!entry.initialized())throw new GameTestAssertException("public build must construct and initialize in one command");
        String status=TegFueledFixtureService.status(level,origin);
        if(!status.toLowerCase(Locale.ROOT).contains("initialized / finite"))throw failDetailed("TEG-COMMAND-INITIALIZED-STATUS",status);
        var core=TegFueledFixturePlan.layout(origin.offset(22,2,18)).electrical().devices().core();
        var gasBefore=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();double startupBefore=((PowerDeviceBlockEntity)level.getBlockEntity(TegFueledFixturePlan.layout(origin.offset(22,2,18)).electrical().devices().startupApc())).energyJoules();
        int repeat=dispatcher.execute("ms14 teg_fixture finite build "+coords,console);
        if(repeat!=0||!TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow().equals(entry))throw new GameTestAssertException("repeat public build must refuse without changing saved identity");
        if(!gasBefore.equals(PipeGasInventorySavedData.existing(level).orElseThrow().snapshot())||startupBefore!=1_000_000)throw new GameTestAssertException("repeat build refilled or changed finite resources");
        h.succeed();
    }

    @GameTest(template="disposal_power_clear",templateNamespace="moonstation14",timeoutTicks=200,batch="teg_fueled_player_command")
    @GameTestTags({"teg-fueled-build","teg-command-ux"})
    public static void playerNoArgsBuildAndStatusUseBodyRelativeSavedFixture(GameTestHelper h) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level=(ServerLevel)h.getLevel();var body=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"teg-command-player"));
        BlockPos bodyPos=h.absolutePos(BlockPos.ZERO).offset(-4,2,-4);body.setPos(bodyPos.getX()+.5,bodyPos.getY(),bodyPos.getZ()+.5);
        if(!level.addFreshEntity(body))throw new GameTestAssertException("fake player could not be spawned");
        try {
        BlockPos origin=body.blockPosition().offset(4,-2,4);
        for(int x=0;x<48;x++)for(int y=0;y<8;y++)for(int z=0;z<48;z++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3); for(int x=0;x<48;x++)for(int z=0;z<48;z++)level.setBlock(origin.offset(x,1,z),Blocks.STONE.defaultBlockState(),3);
        var source=level.getServer().createCommandSourceStack().withLevel(level).withEntity(body).withPermission(2);var dispatcher=level.getServer().getCommands().getDispatcher();
        var beforeEntries=TegFixtureSavedData.existing(level).map(TegFixtureSavedData::entries).orElse(List.of());
        BlockPos nearestBefore=TegFixtureCommands.nearestFueledOrigin(beforeEntries,body.blockPosition());
        var beforeCapture=captureOutput(source);
        int beforeStatus=dispatcher.execute("ms14 teg_fixture finite status",beforeCapture.source());
        String expectedBefore=nearestBefore==null?null:TegFueledFixtureService.status(level,nearestBefore);
        int expectedCode=expectedBefore==null||expectedBefore.startsWith("NOT READY")?0:1;
        if(beforeStatus!=expectedCode)throw new GameTestAssertException("status command readiness mismatch; nearest="+nearestBefore+" service="+expectedBefore+" actual="+beforeCapture.messages());
        if(expectedBefore!=null&&!beforeCapture.messages().stream().anyMatch(m->m.equals(expectedBefore)))
            throw new GameTestAssertException("status output did not match shared service result; nearest="+nearestBefore+" service="+expectedBefore+" actual="+beforeCapture.messages());
        if(dispatcher.execute("ms14 teg_fixture finite build",source)!=1)throw new GameTestAssertException("player no-argument finite build failed");
        var entry=TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow(()->new GameTestAssertException("player-relative origin was not saved"));
        if(!entry.initialized())throw new GameTestAssertException("player build did not initialize fixture");
        BlockPos core=origin.offset(22,2,18);
        if(!core.equals(body.blockPosition().offset(26,0,22))||origin.getY()!=body.blockPosition().getY()-2)
            throw new GameTestAssertException("no-argument fixture core/foundation must use the player's feet plane; origin="+origin+" body="+body.blockPosition()+" core="+core);
        BlockPos expectedNearest=TegFixtureCommands.nearestFueledOrigin(TegFixtureSavedData.existing(level).orElseThrow().entries(),body.blockPosition());
        var afterCapture=captureOutput(source);
        if(!origin.equals(expectedNearest)||dispatcher.execute("ms14 teg_fixture finite status",afterCapture.source())!=1)throw new GameTestAssertException("player-owned fixture must be the nearest loaded persisted origin; expected="+origin+" actual="+expectedNearest);
        if(!afterCapture.messages().stream().anyMatch(m->m.contains(core.toString())))
            throw new GameTestAssertException("status output did not identify nearest persisted fixture core "+expectedNearest);
        h.succeed();
        } finally { body.discard(); }
    }

    @GameTest(template="disposal_power_clear",templateNamespace="moonstation14",timeoutTicks=200)
    @GameTestTags({"teg-command-ux","teg-nearest-unloaded-status"})
    public static void statusRefusesNearestPersistedFueledFixtureWithoutLoadingChunks(GameTestHelper h) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerLevel level=(ServerLevel)h.getLevel();
        BlockPos origin=h.absolutePos(BlockPos.ZERO).offset(1024,0,0);
        var body=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"teg-unloaded-status-player"));
        body.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5);
        int chunkX=origin.getX()>>4,chunkZ=origin.getZ()>>4;
        if(level.getChunkSource().getChunk(chunkX,chunkZ,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)!=null)
            throw new GameTestAssertException("remote saved fixture origin was unexpectedly loaded");
        var ids=new HashSet<UUID>();while(ids.size()<4)ids.add(UUID.randomUUID());var iterator=ids.iterator();
        var entry=new TegFixtureSavedData.Entry(origin,iterator.next(),iterator.next(),iterator.next(),iterator.next(),false,TegFixtureSavedData.Mode.FUELED);
        var data=TegFixtureSavedData.forMutation(level);
        if(!data.recordBuilt(entry))throw new GameTestAssertException("could not save unloaded descriptor fixture");
        var source=level.getServer().createCommandSourceStack().withLevel(level).withEntity(body).withPermission(2);
        var nearest=TegFixtureCommands.nearestFueledOrigin(data.entries(),body.blockPosition());
        if(!origin.equals(nearest))throw new GameTestAssertException("saved unloaded fueled descriptor was not selected as nearest: "+nearest);
        String expected=TegFueledFixtureService.status(level,origin);
        if(!expected.startsWith("NOT READY: fueled 48x8x48 footprint is not FULL-loaded"))throw new GameTestAssertException("unloaded footprint did not refuse before hardware inspection: "+expected);
        var capture=captureOutput(source);
        int result=level.getServer().getCommands().getDispatcher().execute("ms14 teg_fixture finite status",capture.source());
        if(result!=0||capture.messages().stream().noneMatch(expected::equals))throw new GameTestAssertException("status command must report nearest unloaded saved descriptor as not ready; code="+result+" output="+capture.messages());
        if(level.getChunkSource().getChunk(chunkX,chunkZ,net.minecraft.world.level.chunk.status.ChunkStatus.FULL,false)!=null)
            throw new GameTestAssertException("status command loaded the saved fixture chunk");
        if(!data.entry(origin).filter(entry::equals).isPresent())throw new GameTestAssertException("status changed saved descriptor ownership");
        h.succeed();
    }

    private static CapturedOutput captureOutput(net.minecraft.commands.CommandSourceStack original) {
        var messages=new ArrayList<String>();
        var output=new net.minecraft.commands.CommandSource() {
            @Override public void sendSystemMessage(net.minecraft.network.chat.Component message) { messages.add(message.getString()); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        return new CapturedOutput(original.withSource(output),messages);
    }
    private record CapturedOutput(net.minecraft.commands.CommandSourceStack source,List<String> messages) { }
    // The global power graph fails closed during any topology rebuild; isolate this long fueled proof from unrelated cable teardown.
    @GameTest(template="disposal_power_clear",templateNamespace="moonstation14",timeoutTicks=6000,batch="teg_fueled_production_build")
    @GameTestTags({"teg-fueled-build"})
    public static void productionFueledBuildPersistsUninitializedToken(GameTestHelper h) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ShutdownDiagnostics.arm(h.getLevel().getServer());
        ServerLevel level=(ServerLevel)h.getLevel();long fixtureStartTick=level.getGameTime();BlockPos origin=h.absolutePos(BlockPos.ZERO);
        // disposal_power_clear is a zero-block 48x8x48 template, not an air-filled volume.
        // Explicitly prepare only that allocated test area; production admission remains strict.
        for(int x=0;x<48;x++)for(int y=0;y<8;y++)for(int z=0;z<48;z++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3); for(int x=0;x<48;x++)for(int z=0;z<48;z++)level.setBlock(origin.offset(x,1,z),Blocks.STONE.defaultBlockState(),3);
        var dispatcher=level.getServer().getCommands().getDispatcher();var console=level.getServer().createCommandSourceStack().withLevel(level).withPermission(2);
        String coords=origin.getX()+" "+origin.getY()+" "+origin.getZ();
        try{dispatcher.execute("ms14 teg_fixture finite build "+coords,console.withPermission(0));throw new GameTestAssertException("permission-0 fueled build unexpectedly executed");}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException denied){if(TegFixtureSavedData.existing(level).flatMap(data->data.entry(origin)).isPresent())throw new GameTestAssertException("denied command created saved ownership");}
        int buildResult=dispatcher.execute("ms14 teg_fixture finite build "+coords,console);
        if(buildResult!=1)throw new GameTestAssertException("public fueled build command returned "+buildResult);
        var entry=TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElseThrow(()->new GameTestAssertException("built token missing"));
        if(entry.mode()!=TegFixtureSavedData.Mode.FUELED||!entry.initialized())throw new GameTestAssertException("public build must persist an initialized fueled fixture");
        String builtStatus=TegFueledFixtureService.status(level,origin);String builtStatusFolded=builtStatus.toLowerCase(Locale.ROOT);if(!builtStatusFolded.contains("initialized / finite")||!builtStatusFolded.contains("hot room:"))throw failDetailed("TEG-FUELED-BUILT-STATUS",builtStatus);
        var p=TegFueledFixturePlan.layout(origin.offset(22,2,18));var d=p.electrical().devices();
        var core=(AtmosMachineBlockEntity)level.getBlockEntity(d.core());if(core==null||com.juicyslew.moonstation14.ms14.atmos.teg.TegAssembly.resolve(level,core).isEmpty())throw new GameTestAssertException("actual TEG assembly resolution failed");
        var live=new ArrayList<Map<String,PipeTopology.NodeKey>>();var roots=new ArrayList<PipeTopology.NodeKey>();List<LoadedPipeGasWork.Port> capturePorts=new ArrayList<>();
        for(var m:p.machines()){var machine=(AtmosMachineBlockEntity)level.getBlockEntity(m.position());if(machine==null)throw new GameTestAssertException("planned machine missing");machine.reconcileLoadedPorts(level);Map<String,PipeTopology.NodeKey> ports=new HashMap<>();for(var gp:machine.gasPorts())ports.put(gp.id(),gp.key(machine.getBlockPos()));live.add(ports);}
        int[][] pairs={{0,1},{1,2},{2,0},{4,5},{5,6},{6,4}};String[][] ids={{"outlet","inlet"},{"outlet","inlet"},{"outlet","inlet"},{"outlet","inlet"},{"outlet","inlet"},{"outlet","inlet"}};
        for(int i=0;i<pairs.length;i++){var a=live.get(pairs[i][0]).get(ids[i][0]);var b=live.get(pairs[i][1]).get(ids[i][1]);if(a==null||b==null)throw new GameTestAssertException("named planned port missing");}
        var topology=TegFueledFixtureTopology.plan(p,live);roots.addAll(topology.representatives());for(var root:roots)capturePorts.add(new LoadedPipeGasWork.Port(root));
        var heaterPort=live.get(3).get("pipe");if(heaterPort==null)throw new GameTestAssertException("heater pipe port missing");
        var captured=LoadedPipeGasWork.capture(level,capturePorts).orElseThrow(()->new GameTestAssertException("production gas capture unavailable"));
        if(captured.aliases()||captured.components().size()!=6)throw new GameTestAssertException("production capture must be six distinct components");
        Set<PipeTopology.NodeKey> union=new HashSet<>();
        Map<PipeTopology.NodeKey,UUID> actualOwners=new HashMap<>();
        for(int i=0;i<6;i++){var component=captured.componentFor(roots.get(i)).orElseThrow(()->new GameTestAssertException("planned component missing"));Set<PipeTopology.NodeKey> keys=new HashSet<>();component.nodes().forEach(n->{keys.add(n.key());actualOwners.put(n.key(),n.incarnation());});if(!keys.equals(topology.components().get(i))||!Collections.disjoint(union,keys))throw new GameTestAssertException("production component membership mismatch");union.addAll(keys);}
        if(!entry.ownedGas().equals(actualOwners))throw new GameTestAssertException("persisted gas fingerprint differs from actual six-component incarnation union");
        Map<BlockPos,UUID> actualMachines=new HashMap<>();for(var m:p.machines())actualMachines.put(m.position(),((AtmosMachineBlockEntity)level.getBlockEntity(m.position())).placementIdentity());actualMachines.put(d.core(),core.placementIdentity());
        if(!entry.ownedMachines().equals(actualMachines))throw failDetailed("TEG-FUELED-MACHINE-OWNERSHIP","persisted vs live machine maps; missing="+difference(actualMachines.keySet(),entry.ownedMachines().keySet())+", extra="+difference(entry.ownedMachines().keySet(),actualMachines.keySet()));
        var reloaded=TegFixtureSavedData.load(TegFixtureSavedData.existing(level).orElseThrow().save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()),level.registryAccess()).entry(origin).orElseThrow();
        if(!reloaded.ownedGas().equals(actualOwners)||!reloaded.ownedMachines().equals(actualMachines))throw new GameTestAssertException("persisted ownership fingerprint failed NBT round-trip");
        var atmosphere=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE;
         for(var cell:p.roomInterior())if(atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).isEmpty())throw new GameTestAssertException("hot room endpoint missing at "+cell);
         for(var cell:p.coldRoomInterior())if(atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).isEmpty())throw new GameTestAssertException("cold room endpoint missing at "+cell);
         for(var crossing:concat(p.sealedCrossings(),p.coldSealedCrossings()))if(atmosphere.finiteRoomEndpointForTrustedSimulation(level,crossing).isPresent())throw new GameTestAssertException("sealed crossing unexpectedly exposes a room endpoint at "+crossing);
          for(var cable:p.combinedCables())if(!CableStorage.has(level,cable.host(),cable.face(),cable.tier()))throw new GameTestAssertException("production build did not preserve planned cable face "+cable);
         if(!(level.getBlockEntity(d.startupApc()) instanceof PowerDeviceBlockEntity startupBuilt)||startupBuilt.energyJoules()!=1_000_000||!(level.getBlockEntity(d.outputApc()) instanceof PowerDeviceBlockEntity outputBuilt)||outputBuilt.energyJoules()!=0)throw new GameTestAssertException("public command initialization APC energy mismatch");
         var identity=List.of(entry.core(),entry.north(),entry.south(),entry.token());
         String wrongLegacy=TegFixtureService.initialize(level,origin);
         if(!wrongLegacy.startsWith("Initialization refused"))throw new GameTestAssertException("legacy finite initializer must reject fueled mode");
         entry=TegFixtureSavedData.existing(level).flatMap(data->data.entry(origin)).orElseThrow();
        if(!entry.initialized()||!entry.ownedGas().equals(actualOwners)||!entry.ownedMachines().equals(actualMachines))throw new GameTestAssertException("initialization token/fingerprint not retained");
        var gasInventory=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
        double hotTotal=0,coldTotal=0;
        for(var component:topology.components()){
            double loop=0,actualEnergy=0,energyBound=0;List<GasMixture> componentGas=new ArrayList<>();
             for(var key:component){var gas=gasInventory.get(key).orElseThrow(()->new GameTestAssertException("seeded node missing: "+key));var mixture=gasInventory.nodeShare(key);componentGas.add(mixture);loop+=mixture.totalMoles();actualEnergy+=mixture.thermalEnergy();
                if(mixture.totalMoles()==0){if(mixture.thermalEnergy()!=0)throw new GameTestAssertException("empty gas owner has thermal energy key="+key+" T="+mixture.temperatureKelvin()+" E="+mixture.thermalEnergy());}
                else {if(mixture.gasMoles().size()!=1||mixture.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN)!=mixture.totalMoles())throw new GameTestAssertException("wrong seeded species key="+key+" moles="+mixture.gasMoles());double tolerance=Math.ulp((float)mixture.temperatureKelvin())*2;if(Math.abs(mixture.temperatureKelvin()-293.15)>tolerance)throw new GameTestAssertException("seeded gas temperature mismatch key="+key+" moles="+mixture.totalMoles()+" T="+mixture.temperatureKelvin()+" E="+mixture.thermalEnergy()+" tolerance="+tolerance);energyBound+=Math.ulp((float)mixture.thermalEnergy())*2;}
            }
            var aggregate=com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasLedger.merge(componentGas);
            if(Math.abs(aggregate.temperatureKelvin()-293.15)>Math.ulp((float)aggregate.temperatureKelvin())*2)throw new GameTestAssertException("component aggregate temperature mismatch root="+topology.representatives().get(topology.components().indexOf(component))+" T="+aggregate.temperatureKelvin()+" moles="+aggregate.totalMoles()+" energy="+aggregate.thermalEnergy());
            double expectedEnergy=new GasMixture(Map.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN,loop),293.15).thermalEnergy();energyBound+=Math.ulp((float)expectedEnergy)*2;
            if(Math.abs(actualEnergy-expectedEnergy)>energyBound)throw new GameTestAssertException("component thermal inventory mismatch total="+loop+" actualE="+actualEnergy+" expectedE="+expectedEnergy+" ulpBound="+energyBound);
            if(component==topology.components().get(0)||component==topology.components().get(1)||component==topology.components().get(2))hotTotal+=loop;else coldTotal+=loop;
        }
        if(Math.abs(hotTotal-43)>.01||Math.abs(coldTotal-43)>.01)throw new GameTestAssertException("loop seed totals mismatch hot="+hotTotal+" cold="+coldTotal);
        double hotRoom=0,coldRoom=0;
        for(var cell:p.roomInterior()){var gas=atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).orElseThrow().snapshot();hotRoom+=gas.totalMoles();if(Math.abs(gas.temperatureKelvin()-293.15)>.002)throw new GameTestAssertException("hot room seed temperature mismatch");}
        for(var cell:p.coldRoomInterior()){var gas=atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).orElseThrow().snapshot();coldRoom+=gas.totalMoles();if(Math.abs(gas.temperatureKelvin()-293.15)>.002)throw new GameTestAssertException("cold room seed temperature mismatch");}
        if(Math.abs(hotRoom-29.7)>.02||Math.abs(coldRoom-270)>.02)throw new GameTestAssertException("room seed species totals mismatch");
        if(!(level.getBlockEntity(d.startupApc()) instanceof PowerDeviceBlockEntity startupEnergy)||startupEnergy.energyJoules()!=1_000_000||!(level.getBlockEntity(d.outputApc()) instanceof PowerDeviceBlockEntity outputEnergy)||outputEnergy.energyJoules()!=0)throw new GameTestAssertException("initial APC energy mismatch");
        var statusGasBefore=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
        var statusCapture=LoadedPipeGasWork.capture(level,topology.representatives().stream().map(LoadedPipeGasWork.Port::new).toList()).orElseThrow(()->new GameTestAssertException("post-init loop measurement capture unavailable"));
        var hotMeasurement=TegFueledLoopMeasurement.measure(topology.representatives().subList(0,3).stream().map(root->statusCapture.componentFor(root).orElseThrow().aggregate()).toList()).mixture();var coldMeasurement=TegFueledLoopMeasurement.measure(topology.representatives().subList(3,6).stream().map(root->statusCapture.componentFor(root).orElseThrow().aggregate()).toList()).mixture();
         double hotInventoryN2=topology.components().subList(0,3).stream().flatMap(Set::stream).mapToDouble(k->statusGasBefore.nodeShare(k).moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN)).sum();double coldInventoryN2=topology.components().subList(3,6).stream().flatMap(Set::stream).mapToDouble(k->statusGasBefore.nodeShare(k).moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN)).sum();
        if(Math.abs(hotMeasurement.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN)-hotInventoryN2)>.01||Math.abs(coldMeasurement.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN)-coldInventoryN2)>.01)throw failDetailed("TEG-FUELED-LOOP-MEASUREMENT","measurement vs actual component inventory hot="+hotMeasurement+"/"+hotInventoryN2+" cold="+coldMeasurement+"/"+coldInventoryN2);
         double statusStartupBefore=startupEnergy.energyJoules(),statusOutputBefore=outputEnergy.energyJoules();String liveStatus=TegFueledFixtureService.status(level,origin);String repeatedStatus=TegFueledFixtureService.status(level,origin);
         String liveStatusFolded=liveStatus.toLowerCase(Locale.ROOT);if(!liveStatusFolded.contains("initialized / finite")||!liveStatusFolded.contains("plasma")||!liveStatusFolded.contains("cold room: n2")||!liveStatusFolded.contains("hot loop n2 43.0 mol")||!liveStatusFolded.contains("cold loop n2 43.0 mol")||!liveStatusFolded.contains("offered hv")||!liveStatusFolded.contains("output apc allocation")||!liveStatusFolded.contains("delivered"))throw failDetailed("TEG-FUELED-INITIALIZED-STATUS",liveStatus);
        if(!liveStatus.equals(repeatedStatus)||startupEnergy.energyJoules()!=statusStartupBefore||outputEnergy.energyJoules()!=statusOutputBefore)throw failDetailed("TEG-FUELED-STATUS-READ-MUTATION",liveStatus+"\nrepeat="+repeatedStatus);
        var statusGasAfter=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();for(var component:topology.components())for(var key:component)if(!statusGasBefore.get(key).equals(statusGasAfter.get(key)))throw new GameTestAssertException("status mutated gas at "+key);
         String retry=TegFueledFixtureService.initialize(level,origin);
         if(!retry.startsWith("Initialization refused"))throw new GameTestAssertException("service must refuse reinitialization, got "+retry);
        var gasAfter=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();for(var component:topology.components())for(var key:component)if(!gasAfter.get(key).equals(gasInventory.get(key)))throw new GameTestAssertException("retry changed gas inventory at "+key);
        if(!(level.getBlockEntity(d.startupApc()) instanceof PowerDeviceBlockEntity startupAfter)||startupAfter.energyJoules()!=1_000_000||!(level.getBlockEntity(d.outputApc()) instanceof PowerDeviceBlockEntity outputAfter)||outputAfter.energyJoules()!=0)throw new GameTestAssertException("retry changed APC energy");
        var initializedEntry=TegFixtureSavedData.existing(level).flatMap(data->data.entry(origin)).orElseThrow();
        if(!initializedEntry.initialized()||!identity.equals(List.of(initializedEntry.core(),initializedEntry.north(),initializedEntry.south(),initializedEntry.token()))||!initializedEntry.ownedGas().equals(actualOwners)||!initializedEntry.ownedMachines().equals(actualMachines))throw new GameTestAssertException("initialized token/fingerprint checkpoint mismatch");
        Map<BlockPos,GasMixture> roomCheckpoint=new HashMap<>();for(var cell:p.roomInterior())roomCheckpoint.put(cell,atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).orElseThrow().snapshot());for(var cell:p.coldRoomInterior())roomCheckpoint.put(cell,atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).orElseThrow().snapshot());
        var gasCheckpoint=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();
         String repeat=TegFueledFixtureService.build(level,origin);
         if(!repeat.startsWith("Build refused"))throw new GameTestAssertException("service must refuse repeat build, got "+repeat);
        var after=TegFixtureSavedData.existing(level).flatMap(data->data.entry(origin)).orElseThrow();if(!after.equals(initializedEntry)||!after.initialized()||after.mode()!=TegFixtureSavedData.Mode.FUELED||!after.ownedGas().equals(actualOwners)||!after.ownedMachines().equals(actualMachines))throw new GameTestAssertException("repeat build changed initialized saved ownership");
        var gasAfterBuild=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();for(var component:topology.components())for(var key:component)if(!gasAfterBuild.get(key).equals(gasCheckpoint.get(key)))throw new GameTestAssertException("repeat build changed seeded gas at "+key);
        for(var cell:roomCheckpoint.keySet())if(!atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).orElseThrow().snapshot().gasMoles().equals(roomCheckpoint.get(cell).gasMoles()))throw new GameTestAssertException("repeat build changed room species at "+cell);
        if(!(level.getBlockEntity(d.startupApc()) instanceof PowerDeviceBlockEntity startupAfterBuild)||startupAfterBuild.energyJoules()!=1_000_000||!(level.getBlockEntity(d.outputApc()) instanceof PowerDeviceBlockEntity outputAfterBuild)||outputAfterBuild.energyJoules()!=0)throw new GameTestAssertException("repeat build changed initialized APC energy");
        var hotSets=topology.components().subList(0,3);var coldSets=topology.components().subList(3,6);
        var devices=p.electrical().devices();var coreMachine=machine(level,devices.core());var pumpNorth=machine(level,p.core().offset(-8,0,-1));var pumpSouth=machine(level,p.core().offset(8,0,1));var heater=machine(level,p.core().offset(-11,0,-2));
         // Keep the finite chemical-energy proof explicitly isolated from ordinary MV self-supply.
         var startupMvPort=new com.juicyslew.moonstation14.ms14.power.topology.DevicePort(devices.startupApc(),Direction.UP,CableTier.MV);
         var feedbackContacts=com.juicyslew.moonstation14.ms14.power.topology.PowerTopology.deviceContacts(startupMvPort);
         var feedbackNode=p.combinedCables().stream().filter(c->feedbackContacts.contains(new CableFaceNode(c.host(),c.face(),c.tier()))&&c.tier()==CableTier.MV).findFirst().orElseThrow(()->new GameTestAssertException("planned startup APC MV feedback contact missing"));
         if(!CableStorage.has(level,feedbackNode.host(),feedbackNode.face(),CableTier.MV)||CableStorage.remove(level,feedbackNode.host(),feedbackNode.face(),CableTier.MV)!=CableTier.MV)throw new GameTestAssertException("could not isolate exact planned startup APC MV feedback node");
         for(var contact:feedbackContacts)if(CableStorage.has(level,contact.host(),contact.face(),contact.tier()))throw new GameTestAssertException("startup APC still has physical MV feedback contact "+contact);
         MoonStation14.LOGGER.info("TEG_FUELED_PROOF_ISOLATED reason=finite-chemical-energy window excludes startup APC MV feedback; this is not ordinary net-power/self-supply evidence node={}",feedbackNode);
         observeInitializedFuelledFixture(h,level,p,devices,coreMachine,pumpNorth,pumpSouth,heater,(PowerDeviceBlockEntity)level.getBlockEntity(devices.startupApc()),(PowerDeviceBlockEntity)level.getBlockEntity(devices.outputApc()),hotSets,coldSets,1_000_000,fixtureStartTick,()->restoreFeedbackAndAssertDelivery(h,level,p,coreMachine,heater,feedbackNode,fixtureStartTick));
    }
    private static Set<BlockPos> difference(Set<BlockPos> a,Set<BlockPos> b){Set<BlockPos> r=new HashSet<>(a);r.removeAll(b);return r;}
    @GameTest(template="disposal_power_clear",templateNamespace="moonstation14",timeoutTicks=200)
    @GameTestTags({"teg-fueled-build"})
    public static void fueledInitializerRejectsChangedMachineFacingBeforeToken(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();BlockPos origin=h.absolutePos(BlockPos.ZERO);
        for(int x=0;x<48;x++)for(int y=0;y<8;y++)for(int z=0;z<48;z++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3); for(int x=0;x<48;x++)for(int z=0;z<48;z++)level.setBlock(origin.offset(x,1,z),Blocks.STONE.defaultBlockState(),3);
        String built=TegFueledFixtureService.build(level,origin);if(!built.startsWith("Built fueled"))throw new GameTestAssertException(built);
        var entry=TegFixtureSavedData.existing(level).flatMap(data->data.entry(origin)).orElseThrow();var p=TegFueledFixturePlan.layout(origin.offset(22,2,18));var pump=p.machines().get(1);
        var state=level.getBlockState(pump.position());if(!state.hasProperty(AtmosMachineBlock.FACING))throw new GameTestAssertException("pump facing property missing");
        level.setBlock(pump.position(),state.setValue(AtmosMachineBlock.FACING,Direction.SOUTH),3);
        String result=TegFueledFixtureService.initialize(level,origin);if(!result.startsWith("Initialization refused")||TegFixtureSavedData.existing(level).flatMap(data->data.entry(origin)).orElseThrow().initialized())throw new GameTestAssertException("changed machine facing must reject before token consumption: "+result);
        var d=p.electrical().devices();for(BlockPos apc:List.of(d.startupApc(),d.outputApc()))if(!(level.getBlockEntity(apc) instanceof PowerDeviceBlockEntity device)||device.energyJoules()!=0)throw new GameTestAssertException("facing refusal charged APC");
        h.succeed();
    }
    private static void restoreFeedbackAndAssertDelivery(GameTestHelper h,ServerLevel level,TegFueledFixturePlan.Layout p,AtmosMachineBlockEntity core,AtmosMachineBlockEntity heater,CableFaceNode feedbackNode,long fixtureStartTick){
        long remaining=fixtureStartTick+5500-level.getGameTime();if(remaining<=0)fail("overall fueled native proof deadline reached before feedback restoration");
        if(!CableStorage.place(level,feedbackNode.host(),feedbackNode.face(),CableTier.MV))fail("could not restore exact planned startup APC MV feedback node");
        var devices=p.electrical().devices();var output=(PowerDeviceBlockEntity)level.getBlockEntity(devices.outputApc());var load=(PowerDeviceBlockEntity)level.getBlockEntity(devices.load());
        com.juicyslew.moonstation14.gametest.support.PowerFixtureReadiness.await(h,level,Math.min(200,remaining),()->{
            var observation=PowerRuntime.apcOutputObservation(level,output);return com.juicyslew.moonstation14.ms14.power.graph.PowerGraphService.topologyReady(level)&&observation.known()&&observation.sampleTick()>=PowerRuntime.currentSampleTick(level)-PowerRuntime.TICKS_PER_SOLVE&&observation.outputWatts()>=100&&core.powerSupplierOffer()!=null&&core.powerSupplierOffer().currentWatts()>0&&level.getBlockState(devices.load()).getValue(PowerDeviceBlock.LIT);
        },()->"restored MV feedback not ready; remaining="+(fixtureStartTick+5500-level.getGameTime()),()->{
            if(level.getGameTime()>=fixtureStartTick+5500)fail("overall fueled native proof deadline reached after feedback restoration");
            assertProductionDeliveryStatus(level,p,core,heater,(PowerDeviceBlockEntity)level.getBlockEntity(devices.startupApc()),output);h.succeed();
        });
    }
    @GameTest(template="disposal_power_clear",templateNamespace="moonstation14",timeoutTicks=200)
    @GameTestTags({"teg-fueled-build","teg-fueled-ground","teg-fueled-windows"})
    public static void fueledBuildRejectsUnbreakableHostBeforeWritesAndGlassWindowsSealRooms(GameTestHelper h) {
        ServerLevel level=(ServerLevel)h.getLevel();BlockPos origin=h.absolutePos(BlockPos.ZERO);
        for(int x=0;x<48;x++)for(int y=0;y<8;y++)for(int z=0;z<48;z++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        for(int x=0;x<48;x++)for(int z=0;z<48;z++)level.setBlock(origin.offset(x,1,z),Blocks.STONE.defaultBlockState(),3);
        var p=TegFueledFixturePlan.layout(origin.offset(22,2,18));BlockPos planned=p.combinedCables().get(0).host();
        level.setBlock(planned,Blocks.BEDROCK.defaultBlockState(),3);BlockState before=level.getBlockState(planned);
        String refused=TegFueledFixtureService.build(level,origin);
        if(!refused.startsWith("Build refused")||!level.getBlockState(planned).equals(before)||TegFixtureSavedData.existing(level).flatMap(data->data.entry(origin)).isPresent())throw new GameTestAssertException("unbreakable planned host must refuse before writes/token: "+refused);
        level.setBlock(planned,Blocks.AIR.defaultBlockState(),3);
        String built=TegFueledFixtureService.build(level,origin);if(!built.startsWith("Built fueled"))throw new GameTestAssertException("breakable footprint failed build: "+built);
        for(BlockPos window:TegFueledFixturePlan.observationWindows(p.core())){var state=level.getBlockState(window);if(!state.is(Blocks.GLASS)||com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereTopology.isPassable(level,window)||!state.isCollisionShapeFullBlock(level,window))throw new GameTestAssertException("glass observation window must be visually glass but full-collision and gas-impassable at "+window);}
        var atmosphere=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE;
        if(p.roomInterior().size()!=27||p.coldRoomInterior().size()!=27)throw new GameTestAssertException("hot/cold room interior volume must remain 27 cells");
        for(BlockPos cell:p.roomInterior())if(atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).isEmpty())throw new GameTestAssertException("hot room endpoint missing at "+cell);
        for(BlockPos cell:p.coldRoomInterior())if(atmosphere.finiteRoomEndpointForTrustedSimulation(level,cell).isEmpty())throw new GameTestAssertException("cold room endpoint missing at "+cell);
        for(BlockPos window:TegFueledFixturePlan.observationWindows(p.core()))if(atmosphere.finiteRoomEndpointForTrustedSimulation(level,window).isPresent())throw new GameTestAssertException("window cell must not have finite room endpoint at "+window);
        h.succeed();
    }
    private record PipeMeasure(double nitrogen,double energy,Map<PipeTopology.NodeKey,Long> versions,Map<UUID,FinitePipeSpeciesAudit.NetworkCounter> networks) { }
    private record RoomMeasure(double plasma,double oxygen,double co2,double tritium,double nitrogen,double energy,double kelvin) { }
    private record TrackedNode(UUID incarnation,double volume) { }
    private static final class FuelRun {
        final List<BlockPos> hotRoom,coldRoom;final List<Set<PipeTopology.NodeKey>> hot,cold;final Map<PipeTopology.NodeKey,TrackedNode> allNodes;
        final RoomMeasure room0,cold0;final PipeMeasure hot0,coldPipe0;final double startup0,generated0;final TegFixtureEnergyBalance.ConversionModel conversionModel;double previousStartup;long lastOutputSample=-1;double lastOutputWatts;double deliveredJoules;double hotEnergyAtOff,generatedAtOff;long offPowerSample,heaterOffCommandSample;boolean paidPreheatSeen;Runnable onDelivered;
        final ArrayDeque<String> heaterTrace=new ArrayDeque<>();Object lastTracedState;String lastTraceKey;long traceFirstWorldTick=-1;long lastTraceGeneration=Long.MIN_VALUE;boolean lastTraceReady;String lastGraphSnapshot="";
        FuelRun(List<BlockPos> hotRoom,List<BlockPos> coldRoom,List<Set<PipeTopology.NodeKey>> hot,List<Set<PipeTopology.NodeKey>> cold,Map<PipeTopology.NodeKey,TrackedNode> allNodes,RoomMeasure room0,RoomMeasure cold0,PipeMeasure hot0,PipeMeasure coldPipe0,double startup0,double currentStartup,double generated0,TegFixtureEnergyBalance.ConversionModel conversionModel){this.hotRoom=hotRoom;this.coldRoom=coldRoom;this.hot=hot;this.cold=cold;this.allNodes=allNodes;this.room0=room0;this.cold0=cold0;this.hot0=hot0;this.coldPipe0=coldPipe0;this.startup0=startup0;this.previousStartup=currentStartup;this.generated0=generated0;this.conversionModel=conversionModel;}
    }
    private static void observeInitializedFuelledFixture(GameTestHelper h,ServerLevel level,TegFueledFixturePlan.Layout p,TegFixturePlan.Devices d,AtmosMachineBlockEntity core,AtmosMachineBlockEntity pumpA,AtmosMachineBlockEntity pumpB,AtmosMachineBlockEntity heater,PowerDeviceBlockEntity startup,PowerDeviceBlockEntity output,List<Set<PipeTopology.NodeKey>> hot,List<Set<PipeTopology.NodeKey>> cold,double originalStartupJ,long fixtureStartTick,Runnable onDelivered){
            var all=new LinkedHashSet<PipeTopology.NodeKey>();hot.forEach(all::addAll);cold.forEach(all::addAll);
            var hotRoom=List.copyOf(p.roomInterior());var coldRoom=List.copyOf(p.coldRoomInterior());
            var tracked=new LinkedHashMap<PipeTopology.NodeKey,TrackedNode>();var inventory=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();for(var key:all){var en=inventory.get(key).orElseThrow();tracked.put(key,new TrackedNode(en.incarnation(),en.volume()));}
            var hotMeasure=measurePipe(level,hot,tracked);var coldMeasure=measurePipe(level,cold,tracked);
              RoomMeasure room0=measureRoom(level,hotRoom),cold0=measureRoom(level,coldRoom);double generated0=core.tegRuntimeState().cumulativeGeneratedJoules();FuelRun run=new FuelRun(hotRoom,coldRoom,hot,cold,tracked,room0,cold0,hotMeasure,coldMeasure,originalStartupJ,startup.energyJoules(),generated0,conversionModel(core));run.onDelivered=onDelivered;
            MoonStation14.LOGGER.info("TEG_FUELED_PHASE=OBSERVE_INITIALIZED roomFuel={} roomO2={} hotN2={} coldN2={} startupBaselineJ={} startupCurrentJ={} K={}",room0.plasma(),room0.oxygen(),hotMeasure.nitrogen(),coldMeasure.nitrogen(),originalStartupJ,startup.energyJoules(),room0.kelvin());
            awaitFuel(h,level,p,d,core,pumpA,pumpB,heater,startup,output,run,fixtureStartTick,200,0,null,-1,false);
    }
    private static void assertProductionDeliveryStatus(ServerLevel level,TegFueledFixturePlan.Layout p,AtmosMachineBlockEntity core,AtmosMachineBlockEntity heater,PowerDeviceBlockEntity startup,PowerDeviceBlockEntity output){
        BlockPos origin=p.core().offset(-22,-2,-18);String status=TegFueledFixtureService.status(level,origin);String folded=status.toLowerCase(Locale.ROOT);
        var meter=TegFixtureService.deliveryState(level,origin);var meterView=PowerRuntime.apcOutputObservation(level,output);long now=PowerRuntime.currentSampleTick(level);var entry=TegFixtureSavedData.existing(level).flatMap(data->data.entry(origin)).orElseThrow();var gasBefore=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();double startupBefore=startup.energyJoules(),outputBefore=output.energyJoules();
        if(folded.startsWith("not ready")||!folded.contains("initialized / finite")||!folded.contains("hot loop n2 43.0 mol")||!folded.contains("cold loop n2 43.0 mol")||!folded.contains("heater ")||!folded.contains("off")||!folded.contains("offered hv")||!folded.contains("generated")||!folded.contains("lamp lit")||heater.settingsSnapshot().enabled().orElse(true)||heater.apcPowerDemandWatts()==null||heater.apcPowerDemandWatts()!=0||heater.apcAllocatedWatts()!=0||heater.apcAllocationKnown())failDetailed("TEG-FUELED-LIVE-STATUS",status);
        if(meter==null||meter.sampleTick()<0||meter.totalJoules()<=0||meter.sampleTick()>now||now-meter.sampleTick()>PowerRuntime.TICKS_PER_SOLVE||!meterView.known()||meterView.sampleTick()!=meter.sampleTick()||meterView.topologyGeneration()!=PowerGraphService.topologyGeneration(level)||meterView.outputWatts()<100||!status.contains("delivered ")||status.contains("delivered UNKNOWN")||status.contains("offered HV UNKNOWN"))failDetailed("TEG-FUELED-DELIVERY-OBSERVER",status+" meter="+meter+" actual="+meterView);
        var gasAfter=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();String repeated=TegFueledFixtureService.status(level,origin);var meterAfter=TegFixtureService.deliveryState(level,origin);
        if(!status.equals(repeated)||!entry.equals(TegFixtureSavedData.existing(level).flatMap(data->data.entry(origin)).orElseThrow())||!meter.equals(meterAfter)||startup.energyJoules()!=startupBefore||output.energyJoules()!=outputBefore||!gasBefore.equals(gasAfter))failDetailed("TEG-FUELED-STATUS-READ-MUTATION",status+" repeat="+repeated);
        if(core.tegRuntimeState().cumulativeGeneratedJoules()<=0||core.powerSupplierOffer()==null||core.powerSupplierOffer().currentWatts()<=0||!level.getBlockState(p.electrical().devices().load()).getValue(PowerDeviceBlock.LIT))failDetailed("TEG-FUELED-LIVE-CAUSALITY",status);
    }
    private static AtmosMachineBlockEntity machine(ServerLevel level,BlockPos pos){return level.getBlockEntity(pos) instanceof AtmosMachineBlockEntity machine?machine:null;}
    private static TegFixtureEnergyBalance.ConversionModel conversionModel(AtmosMachineBlockEntity core){var profile=java.util.Objects.requireNonNull(core.resolvedProfile(),"resolved TEG core profile missing");if(profile.teg()==null)throw new IllegalStateException("resolved core profile has no TEG conversion coefficients");return new TegFixtureEnergyBalance.ConversionModel(profile.teg().efficiency(),profile.teg().powerFactor());}
    private static <T> List<T> concat(Collection<T> a,Collection<T> b){var result=new ArrayList<T>(a);result.addAll(b);return result;}
    private static RoomMeasure measureRoom(ServerLevel level,List<BlockPos> cells){double plasma=0,oxygen=0,co2=0,tritium=0,nitrogen=0,energy=0,temp=0;for(var cell:cells){var m=com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService.INSTANCE.sample(level,cell).orElseThrow();plasma+=m.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.PLASMA);oxygen+=m.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN);co2+=m.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.CARBON_DIOXIDE);tritium+=m.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.TRITIUM);nitrogen+=m.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN);energy+=m.thermalEnergy();temp+=m.temperatureKelvin();}return new RoomMeasure(plasma,oxygen,co2,tritium,nitrogen,energy,temp/cells.size());}
     private static PipeMeasure measurePipe(ServerLevel level,List<Set<PipeTopology.NodeKey>> sets,Map<PipeTopology.NodeKey,TrackedNode> tracked){var inv=PipeGasInventorySavedData.existing(level).orElseThrow().snapshot();var keys=sets.stream().flatMap(Set::stream).collect(java.util.stream.Collectors.toSet());double nitrogen=0,energy=0;var versions=new LinkedHashMap<PipeTopology.NodeKey,Long>();var networks=new LinkedHashMap<UUID,FinitePipeSpeciesAudit.NetworkCounter>();var seen=new HashSet<UUID>();for(var key:keys){var e=inv.get(key).orElseThrow();var t=tracked.get(key);if(t!=null&&(!e.active()||!e.incarnation().equals(t.incarnation())||Math.abs(e.volume()-t.volume())>1e-9))fail("tracked pipe identity changed at "+key);versions.put(key,e.version());var network=inv.network(key).orElseThrow();if(seen.add(network.id())){if(!keys.containsAll(network.members()))fail("working loop contains only part of pipe network "+network.id());var gas=network.mixture();if(gas.gasMoles().keySet().stream().anyMatch(g->g!=com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN))fail("hot/cold working loop acquired non-nitrogen species at network "+network.id());nitrogen+=gas.moles(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN);energy+=gas.thermalEnergy();networks.put(network.id(),new FinitePipeSpeciesAudit.NetworkCounter(network.topologyGeneration(),network.members(),network.gasRevision()));}}return new PipeMeasure(nitrogen,energy,Map.copyOf(versions),Map.copyOf(networks));}
     private static void auditWorkingFluid(ServerLevel level,FuelRun run){var hot=measurePipe(level,run.hot,run.allNodes);var cold=measurePipe(level,run.cold,run.allNodes);var ha=FinitePipeSpeciesAudit.assessNetworks(run.hot0.networks(),hot.networks(),run.hot0.nitrogen(),hot.nitrogen(),Math.max(run.hot0.nitrogen(),hot.nitrogen()));var ca=FinitePipeSpeciesAudit.assessNetworks(run.coldPipe0.networks(),cold.networks(),run.coldPipe0.nitrogen(),cold.nitrogen(),Math.max(run.coldPipe0.nitrogen(),cold.nitrogen()));if(!ha.conserved()||!ca.conserved())fail("working fluid species conservation failed hot="+ha+" cold="+ca);}
    private static void awaitFuel(GameTestHelper h,ServerLevel level,TegFueledFixturePlan.Layout p,TegFixturePlan.Devices d,AtmosMachineBlockEntity core,AtmosMachineBlockEntity pumpA,AtmosMachineBlockEntity pumpB,AtmosMachineBlockEntity heater,PowerDeviceBlockEntity startup,PowerDeviceBlockEntity output,FuelRun run,long fixtureStart,int budget,int waited,Long offStamp,long offSample,boolean preheated){
        h.runAfterDelay(1,()->{try{long now=level.getGameTime();if(now-fixtureStart>=5500)fail("overall fueled native proof deadline reached before GameTest timeout");double startupEnergy=startup.energyJoules();if(startupEnergy>run.previousStartup)fail("finite startup APC energy increased during fueled phase");run.previousStartup=startupEnergy;var room=measureRoom(level,run.hotRoom);var cold=measureRoom(level,run.coldRoom);var hot=measurePipe(level,run.hot,run.allNodes);var coldPipe=measurePipe(level,run.cold,run.allNodes);auditWorkingFluid(level,run);if(room.nitrogen()!=0||cold.plasma()!=0||cold.oxygen()!=0||cold.co2()!=0||cold.tritium()!=0||Math.abs(cold.nitrogen()-run.cold0.nitrogen())>1e-4)fail("finite hot fuel/cold sink rooms mixed species or lost cooling mass");
              double batteryLoss=run.startup0-startup.energyJoules();var event=machine(level,p.core().offset(-16,0,-1)).deviceRuntimeState();var runtime=core.tegRuntimeState();var heaterState=heater.deviceRuntimeState();var heaterDemand=heater.apcPowerDemandWatts();boolean heaterOff=heater.settingsSnapshot().enabled().map(v->!v).orElse(false)&&heaterDemand!=null&&heaterDemand==0&&heater.apcAllocatedWatts()==0&&!heater.apcAllocationKnown();
              var currentModel=conversionModel(core);var balance=TegFixtureEnergyBalance.compare(new TegFixtureEnergyBalance.Observation(run.room0.energy(),run.hot0.energy(),run.cold0.energy(),run.coldPipe0.energy(),run.startup0,run.generated0,run.conversionModel),new TegFixtureEnergyBalance.Observation(room.energy(),hot.energy(),cold.energy(),coldPipe.energy(),startupEnergy,runtime.cumulativeGeneratedJoules(),currentModel));
             boolean chemistryProof=run.room0.plasma()-room.plasma()>.01&&run.room0.oxygen()-room.oxygen()>.01&&(room.co2()>run.room0.co2()+.005||room.tritium()>run.room0.tritium()+.005);
             boolean thermalProof=balance.provesChemicalExcess()&&room.kelvin()>run.room0.kelvin()+20;
              boolean paidEvent=heaterState.lastActualPaidInputJoules()>0;run.paidPreheatSeen|=paidEvent;
              if(preheated&&offStamp==null&&waited<300)traceHeaterPoll(run,level,heater,heaterState,now);
             boolean preheatPaid=core.apcAllocationKnown()&&pumpA.apcAllocationKnown()&&pumpB.apcAllocationKnown()&&heater.apcAllocationKnown()&&run.paidPreheatSeen&&startup.energyJoules()>0;
             if(offStamp==null){boolean chemistry=chemistryProof;boolean thermal=thermalProof;
                if(!preheated&&preheatPaid){MoonStation14.LOGGER.info("TEG_FUELED_PHASE=PAID_PREHEAT heaterAlloc={} actualPaidJ={} batteryLoss={} hotLoopN2={} roomK={} startupJ={}",heater.apcAllocatedWatts(),heater.deviceRuntimeState().lastActualPaidInputJoules(),batteryLoss,hot.nitrogen(),room.kelvin(),startup.energyJoules());awaitFuel(h,level,p,d,core,pumpA,pumpB,heater,startup,output,run,fixtureStart,2400,0,null,-1,true);return;}
                if(preheated&&chemistry&&thermal&&preheatPaid){if(!heater.applySettings(AtmosMachineSettings.Operation.SET_ENABLED,new AtmosMachineSettings.BooleanValue(false)))fail("heater OFF command rejected after finite chemistry proof");run.heaterOffCommandSample=PowerRuntime.currentSampleTick(level);MoonStation14.LOGGER.info("TEG_FUELED_PHASE=HEATER_OFF_COMMAND fuelRemaining={} fuelLoss={} O2Loss={} hotPipeE={} batteryLoss={} startupJ={} commandSample={}",room.plasma(),run.room0.plasma()-room.plasma(),run.room0.oxygen()-room.oxygen(),hot.energy(),batteryLoss,startup.energyJoules(),run.heaterOffCommandSample);awaitFuel(h,level,p,d,core,pumpA,pumpB,heater,startup,output,run,fixtureStart,1000,0,Long.MIN_VALUE,-1,true);return;}
            }else if(offStamp==Long.MIN_VALUE){
                if(heaterOff&&heater.apcSampleTick()>run.heaterOffCommandSample&&heater.apcSampleTick()<=PowerRuntime.currentSampleTick(level)){run.hotEnergyAtOff=hot.energy();run.generatedAtOff=runtime.cumulativeGeneratedJoules();run.offPowerSample=PowerRuntime.currentSampleTick(level);long stamp=event.lastUnpoweredProcessWorldTick();MoonStation14.LOGGER.info("TEG_FUELED_PHASE=HEATER_OFF_CONFIRMED freshHeaterSample={} exchangerBaseline={} hotPipeAtOff={} generatedAtOff={} outputBaselineSample={} startupJ={}",heater.apcSampleTick(),stamp,run.hotEnergyAtOff,run.generatedAtOff,run.offPowerSample,startup.energyJoules());awaitFuel(h,level,p,d,core,pumpA,pumpB,heater,startup,output,run,fixtureStart,budget,waited,stamp,run.offPowerSample,true);return;}
            }else if(heaterOff&&heater.apcSampleTick()>offSample&&heater.apcSampleTick()<=PowerRuntime.currentSampleTick(level)&&!heater.apcAllocationKnown()&&event.lastUnpoweredProcessWorldTick()>offStamp&&event.lastThermalJoules()>0&&hot.energy()>run.hotEnergyAtOff+1&&startup.energyJoules()>0){
                if(event.lastWorkStatus()==com.juicyslew.moonstation14.ms14.atmos.machine.AtmosDeviceRuntime.WorkStatus.COMMITTED&&runtime.cumulativeGeneratedJoules()>run.generatedAtOff&&runtime.lastGeneratedJoules()>0&&runtime.lastPaidSampleTick()>run.offPowerSample&&runtime.supply().maximumSupplyWatts()>0){var obs=PowerRuntime.apcOutputObservation(level,output);if(obs.known()&&obs.sampleTick()>run.offPowerSample){if(run.lastOutputSample>=0&&obs.sampleTick()>run.lastOutputSample)run.deliveredJoules+=run.lastOutputWatts*(obs.sampleTick()-run.lastOutputSample)/20d;run.lastOutputSample=obs.sampleTick();run.lastOutputWatts=obs.outputWatts();}
                    boolean delivery=obs.known()&&obs.sampleTick()>run.offPowerSample&&obs.outputWatts()>=100&&obs.topologyGeneration()==PowerGraphService.topologyGeneration(level)&&obs.sampleTick()<=PowerRuntime.currentSampleTick(level)&&level.getBlockState(d.load()).getValue(PowerDeviceBlock.LIT)&&run.deliveredJoules>0;
                     if(delivery&&cold.energy()>run.cold0.energy()&&cold.kelvin()>run.cold0.kelvin()&&Math.abs(cold.nitrogen()-run.cold0.nitrogen())<=1e-4&&cold.plasma()==0&&cold.oxygen()==0&&cold.co2()==0&&cold.tritium()==0&&room.nitrogen()==0){if(!balance.provesChemicalExcess())fail("finite chemical thermal excess does not exceed startup input net of modeled TEG thermal sink and unchanged 1000J margin");MoonStation14.LOGGER.info("TEG_FUELED_PHASE=DELIVERED fuelRemaining={} lampW={} deliveredJ={} reserveJ={} outputStoredJ={}",room.plasma(),obs.outputWatts(),run.deliveredJoules,startup.energyJoules(),output.energyJoules());MoonStation14.LOGGER.info("TEG_FUELED_BALANCE model=SS14_MODELED_THERMAL_SINK efficiency={} powerFactor={} {} modeledThermalSinkJ={} generatedActualJ={} generatedSinceOff={} offeredW={} outSample={} exchangerStamp={}",run.conversionModel.efficiency(),run.conversionModel.powerFactor(),balance,balance.modeledThermalSinkDelta(),balance.generatedDelta(),runtime.cumulativeGeneratedJoules()-run.generatedAtOff,runtime.supply().maximumSupplyWatts(),obs.sampleTick(),event.lastUnpoweredProcessWorldTick());MoonStation14.LOGGER.info("TEG_FUELED_THERMAL hotPipeGain={} coldRoomGain={} coldK={} hotN2={} coldN2={}",hot.energy()-run.hot0.energy(),cold.energy()-run.cold0.energy(),cold.kelvin(),hot.nitrogen(),coldPipe.nitrogen());if(run.onDelivered!=null){run.onDelivered.run();}else h.succeed();return;}
                }
            }
            if(waited%100==0)MoonStation14.LOGGER.info("TEG_FUELED_WAIT phase={} left={} chemistry={} chemicalExcess={} thermal={} thermalDelta={} startupConsumed={} generatedDelta={} preheatPaid={} heaterOff={} deliveryProgress={} baselineThermal={} currentThermal={} startupBaseline={} startupCurrent={} generatedBaseline={} generatedCurrent={} materialMargin={} fuelRemaining={} fuelLoss={} O2Loss={} products={},{} hotRoomE={} hotPipeE={} coldRoomE={} coldPipeE={} coldK={} hotVol={} coldVol={} hotN2={} coldN2={} battery={} loss={} heaterDemand={} heaterAlloc={} heaterSample={} exchStamp={} exchDelta={} tegGenerated={} generatedAtOff={} offer={} output={} deliveredJ={}",offStamp==null?(preheated?"COMBUSTION":"PAID_PREHEAT"):"POST_HEATER_OFF_TEG",budget-waited,chemistryProof,balance.chemicalExcess(),thermalProof,balance.thermalDelta(),balance.startupConsumed(),balance.generatedDelta(),preheatPaid,heaterOff,run.deliveredJoules>0,run.room0.energy()+run.hot0.energy()+run.cold0.energy()+run.coldPipe0.energy(),room.energy()+hot.energy()+cold.energy()+coldPipe.energy(),run.startup0,startupEnergy,run.generated0,runtime.cumulativeGeneratedJoules(),TegFixtureEnergyBalance.MATERIAL_MARGIN_JOULES,room.plasma(),run.room0.plasma()-room.plasma(),run.room0.oxygen()-room.oxygen(),room.co2(),room.tritium(),room.energy(),hot.energy(),cold.energy(),coldPipe.energy(),cold.kelvin(),totalVolume(run.hot,run.allNodes),totalVolume(run.cold,run.allNodes),hot.nitrogen(),coldPipe.nitrogen(),startup.energyJoules(),batteryLoss,heaterDemand,heater.apcAllocatedWatts(),heater.apcSampleTick(),event.lastUnpoweredProcessWorldTick(),event.lastThermalJoules(),runtime.cumulativeGeneratedJoules(),run.generatedAtOff,runtime.supply().maximumSupplyWatts(),run.lastOutputWatts,run.deliveredJoules);
             if(startup.energyJoules()<=0)fail("finite startup APC depleted before fueled TEG/lamp proof");if(waited>=budget)throw failDetailed("TEG-FUELED-PHASE-TIMEOUT","phase="+(offStamp==null?"combustion":"post-off TEG/output")+" room="+room+" hotPipe="+hot+" coldRoom="+cold+" battery="+startup.energyJoules()+" event="+event+" teg="+runtime+(preheated&&offStamp==null?" heaterTrace="+run.heaterTrace:""));
            awaitFuel(h,level,p,d,core,pumpA,pumpB,heater,startup,output,run,fixtureStart,budget,waited+1,offStamp,offSample,preheated);
        }catch(Throwable t){MoonStation14.LOGGER.error("TEG_FUELED_RETAINED bounded phase failed; test world disposal required",t);h.fail("TEG-FUELED-PHASE-FAILED");}});
    }
    private static void traceHeaterPoll(FuelRun run,ServerLevel level,AtmosMachineBlockEntity heater,com.juicyslew.moonstation14.ms14.atmos.machine.AtmosDeviceRuntime.State state,long worldTick){
        if(run.traceFirstWorldTick<0)run.traceFirstWorldTick=worldTick;
        if(worldTick-run.traceFirstWorldTick>=300)return;
        long sample=PowerRuntime.currentSampleTick(level),generation=PowerGraphService.topologyGeneration(level);
        boolean ready=PowerGraphService.topologyReady(level);
        String key="allocationSample="+heater.apcSampleTick()+",generation="+generation+",ready="+ready+",alloc="+heater.apcAllocatedWatts()+",known="+heater.apcAllocationKnown()+",demand="+heater.apcPowerDemandWatts();
        if(state==run.lastTracedState&&key.equals(run.lastTraceKey))return;
        run.lastTracedState=state;run.lastTraceKey=key;
        if(run.heaterTrace.size()==128)run.heaterTrace.removeFirst();
        String graph="";if(generation!=run.lastTraceGeneration||ready!=run.lastTraceReady){graph=PowerGraphService.diagnosticSnapshot(level);run.lastTraceGeneration=generation;run.lastTraceReady=ready;run.lastGraphSnapshot=graph;}
        String pipe="unavailable";try{var port=heater.gasPorts().stream().filter(p->p.id().equals("pipe")).findFirst().orElse(null);if(port!=null){var snapshot=LoadedPipeGasWork.capture(level,List.of(new LoadedPipeGasWork.Port(port.key(heater.getBlockPos())))).orElse(null);if(snapshot!=null){var c=snapshot.componentFor(port.key(heater.getBlockPos())).orElse(null);if(c!=null)pipe="K="+c.aggregate().temperatureKelvin()+",moles="+c.aggregate().totalMoles();}}}catch(RuntimeException ignored){pipe="capture-unavailable";}
        run.heaterTrace.addLast("worldTick="+worldTick+" powerCurrentSampleTick="+sample+" allocationSampleTick="+heater.apcSampleTick()+" (clock units distinct) "+key+" runtime="+state+" heaterPipe="+pipe+(graph.isEmpty()?"":" graph="+graph));
    }
    private static double totalVolume(List<Set<PipeTopology.NodeKey>> components,Map<PipeTopology.NodeKey,TrackedNode> nodes){return components.stream().flatMap(Set::stream).distinct().mapToDouble(k->nodes.get(k).volume()).sum();}
    private static void fail(String s){throw new GameTestAssertException(s);}
    private static GameTestAssertException failDetailed(String code,String detail){MoonStation14.LOGGER.error("{} diagnostics: {}",code,detail);return new GameTestAssertException(code+" (details in server log)");}
}
