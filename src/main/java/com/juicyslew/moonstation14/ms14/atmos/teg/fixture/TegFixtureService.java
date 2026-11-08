package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PowerDeviceBlockEntity;
import com.juicyslew.moonstation14.block.custom.PowerDeviceBlock;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlock;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventory;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventorySavedData;
import com.juicyslew.moonstation14.ms14.atmos.piping.TrustedPipeFixtureInitialization;
import com.juicyslew.moonstation14.ms14.atmos.teg.TegAssembly;
import com.juicyslew.moonstation14.ms14.power.cable.CableStorage;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceFixturePlacement;
import com.juicyslew.moonstation14.ms14.power.device.PowerDeviceKind;
import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

/** Explicitly administered finite thermal-discharge fixture; never refills or supplies hidden heat. */
@EventBusSubscriber(modid="moonstation14")
public final class TegFixtureService {
    private static final double STARTUP_CHARGE_J = 500_000;
    private static final Map<ServerLevel,Map<BlockPos,TegFixtureDeliveryMeter.State>> DELIVERY = new WeakHashMap<>();
    private TegFixtureService() { }

    public static String build(ServerLevel level, BlockPos origin) {
        BlockPos core=core(origin); var plan=TegFixturePlan.layout(core); var devices=plan.devices();
        var saved=TegFixtureSavedData.existing(level).orElse(null);
        if(saved!=null&&saved.entry(origin).isPresent())return "Refused: a saved TEG fixture token already owns this origin; refusing overwrite.";
        if(!TegFixturePlan.powerTopologyValid(plan))return "Build refused: fixture power islands failed their pure topology proof.";
        if(!preflight(level,origin,plan))return "Build refused: footprint must be clear, in bounds, entity-free and already FULL-loaded.";
        for(BlockPos host:plan.cableHosts())level.setBlock(host,Blocks.STONE.defaultBlockState(),3);
        for(BlockPos machine:List.of(devices.core(),devices.northCirc(),devices.southCirc()))
            if(level.getBlockState(machine.below()).isAir())level.setBlock(machine.below(),Blocks.STONE.defaultBlockState(),3);
        TegFixturePlacement.placeAtmos(level,devices.core(),ModBlocks.TEG_CENTER.get(),Direction.NORTH);
        TegFixturePlacement.placeAtmos(level,devices.northCirc(),ModBlocks.TEG_CIRCULATOR.get(),Direction.EAST);
        TegFixturePlacement.placeAtmos(level,devices.southCirc(),ModBlocks.TEG_CIRCULATOR.get(),Direction.WEST);
        TegFixturePlacement.placePower(level,devices.startupApc(),ModBlocks.APC.get(),Direction.EAST);
        TegFixturePlacement.placePower(level,devices.substation(),ModBlocks.HV_MV_SUBSTATION.get(),Direction.EAST);
        TegFixturePlacement.placePower(level,devices.outputApc(),ModBlocks.APC.get(),Direction.WEST);
        TegFixturePlacement.placePower(level,devices.load(),ModBlocks.POWER_LAMP.get(),Direction.NORTH);
        for(var cable:plan.cables())if(!CableStorage.place(level,cable.host(),cable.face(),cable.tier()))
            return "Partial build: cable placement failed at "+cable+"; no initialization or free-power offer was made.";
        AtmosMachineBlockEntity c=machine(level,devices.core()),n=machine(level,devices.northCirc()),s=machine(level,devices.southCirc());
        PowerDeviceBlockEntity startup=apc(level,devices.startupApc()),output=apc(level,devices.outputApc());
        if(c!=null)c.reconcileLoadedPorts(level);if(n!=null)n.reconcileLoadedPorts(level);if(s!=null)s.reconcileLoadedPorts(level);
        if(c==null||n==null||s==null||startup==null||output==null||TegAssembly.resolve(level,c).isEmpty())
            return "Partial build: registered fixture hardware/assembly missing; inspect the bounded footprint before rebuilding.";
        UUID coreIdentity=c.apcPowerIdentity(); UUID northIdentity=port(n,"inlet"); UUID southIdentity=port(s,"inlet");
        if(coreIdentity==null||northIdentity==null||southIdentity==null)return "Partial build: exact machine identities unavailable.";
        var record=new TegFixtureSavedData.Entry(origin,coreIdentity,northIdentity,southIdentity,UUID.randomUUID(),false);
        if(!TegFixtureSavedData.forMutation(level).recordBuilt(record))return "Structure placed, but persisted ownership admission failed; manual recovery required.";
        return "Built finite-charge TEG fixture at "+origin+". Core faces north; north circulator faces east, south west; APC/LV startup is isolated from the east-side HV output. Run initialize once, then status. No fuel loop or radiator is fitted.";
    }

    public static String initialize(ServerLevel level,BlockPos origin) {
        var data=TegFixtureSavedData.existing(level).orElse(null);
        var entry=data==null?Optional.<TegFixtureSavedData.Entry>empty():data.entry(origin);
        if(entry.isEmpty())return "No saved TEG fixture at that origin.";
        var e=entry.get();String refusal=initializationRefusal(e);if(refusal!=null)return refusal;
        var plan=TegFixturePlan.layout(core(origin));if(!loaded(level,origin))return "Initialization refused: fixture footprint is not already fully loaded.";
        var d=plan.devices();var core=machine(level,d.core());var north=machine(level,d.northCirc());var south=machine(level,d.southCirc());
        var startup=apc(level,d.startupApc());var output=apc(level,d.outputApc());
        if(core==null||north==null||south==null||startup==null||output==null
                ||!Objects.equals(e.core(),core.apcPowerIdentity())||!Objects.equals(e.north(),port(north,"inlet"))
                ||!Objects.equals(e.south(),port(south,"inlet"))||TegAssembly.resolve(level,core).isEmpty()
                ||!TegFixturePlan.powerTopologyValid(plan)||!validateCables(level,plan))
            return "Initialization refused: exact machine identities, assembly, loaded port ownership or planned power cables changed.";

        // Equal finite species inventories, distinct temperatures and inlet/outlet pressures.
        Map<String,GasMixture> northGas=Map.of("inlet",mixture(8,900),"outlet",mixture(1,900));
        Map<String,GasMixture> southGas=Map.of("inlet",mixture(8,100),"outlet",mixture(1,100));
        List<TrustedPipeFixtureInitialization.PortSeed> seeds=new ArrayList<>();
        addSeeds(seeds,d.northCirc(),north,northGas);addSeeds(seeds,d.southCirc(),south,southGas);
        if(seeds.size()!=4)return "Initialization refused: all four active circulator ports must be identity-resolved before charging.";
        var readiness=TrustedPipeFixtureInitialization.validateVacuumPorts(level,seeds);
        if(!readiness.successful())return "Initialization refused before consuming its token: gas-port preflight "+readiness.failure()+" ("+readiness.detail()+").";
        if(!data.markInitialized(e))return "Initialization token changed; no resources written.";
        var seeded=TrustedPipeFixtureInitialization.initializeVacuumPortsOnce(level,seeds);
        if(!seeded.successful())return "Initialization token consumed; finite gas-port seed failed after "+seeded.completedPorts()+" completed ports: "+seeded.failure()+" ("+seeded.detail()+"). Resources were not charged or retried; rebuild a fresh disposable fixture.";
        startup.setEnergyJoules(STARTUP_CHARGE_J);output.setEnergyJoules(0);
        core.setControl("enabled",true);
        return "Initialized one finite thermal charge: 8 mol O2 at 900 K + 1 mol O2 at 900 K in hot ports; 8 mol O2 at 100 K + 1 mol O2 at 100 K in cold ports. Startup APC seeded once with 500,000 J; output APC starts at 0 J. This is finite stored heat/cold, not fuel, continuous refill or a radiator-cooled engine.";
    }

    static boolean finiteChargeInitializationAllowed(TegFixtureSavedData.Entry entry) {
        return entry!=null&&entry.mode()==TegFixtureSavedData.Mode.FINITE_CHARGE&&!entry.initialized();
    }

    static String initializationRefusal(TegFixtureSavedData.Entry entry) {
        if(entry==null)return "Initialization refused: fixture entry is unavailable.";
        if(entry.mode()!=TegFixtureSavedData.Mode.FINITE_CHARGE)return "Initialization refused: this fueled fixture requires its dedicated initializer; no finite charge was seeded.";
        if(entry.initialized())return "Fixture is already initialized; finite charge cannot be reseeded.";
        return null;
    }

    public static String status(ServerLevel level,BlockPos origin) {
        if(!loaded(level,origin))return "NOT READY: fixture footprint is not already fully loaded; status never loads chunks.";
        var entry=TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).orElse(null);
        if(entry==null)return "NOT READY: no saved fixture marker at "+origin+".";
        var plan=TegFixturePlan.layout(core(origin));var devices=plan.devices();var c=machine(level,devices.core());
        var startup=apc(level,devices.startupApc());var output=apc(level,devices.outputApc());var load=power(level,devices.load());
        if(c==null||startup==null||output==null||load==null)return "NOT READY: fixture hardware missing or replaced.";
        String hot=portReadout(level,devices.northCirc(),"inlet"),cold=portReadout(level,devices.southCirc(),"inlet");
        var state=c.tegRuntimeState();var actualOffer=c.powerSupplierOffer();var actual=PowerRuntime.apcOutputObservation(level,output);
        double lastOffered=actualOffer==null?state.supply().maximumSupplyWatts():actualOffer.currentWatts();
        var delivery=DELIVERY.getOrDefault(level,Map.of()).get(origin);
        boolean lit=level.getBlockState(devices.load()).hasProperty(PowerDeviceBlock.LIT)
                &&level.getBlockState(devices.load()).getValue(PowerDeviceBlock.LIT);
        return "TEG "+(entry.initialized()?"FINITE THERMAL CHARGE":"BUILT / NOT INITIALIZED")+"; hot inlet "+hot+"; cold inlet "+cold
                +"; LV last allocation="+(c.apcAllocationKnown()?fmt(c.apcAllocatedWatts())+" W":"UNKNOWN")
                +"; last completed paid input="+fmt(state.lastInputPaidJoules())+" J; startup APC remaining="+fmt(startup.energyJoules())+" J"
                +"; last TEG generated="+fmt(state.lastGeneratedJoules())+" J / "+fmt(state.lastGeneratedWatts())+" W; cumulative generated="+fmt(state.cumulativeGeneratedJoules())+" J"
                +"; last TEG offered maximum="+fmt(lastOffered)+" W; actual isolated lamp APC allocation="
                +(actual.known()?fmt(actual.outputWatts())+" W":"UNKNOWN")+"; lamp="+(lit?"ON":"OFF")
                +"; uniquely sampled delivered energy since observer started="+(delivery==null?"UNKNOWN (observer has no continuous fresh interval)":fmt(delivery.totalJoules())+" J")
                +"; finite thermal inventory is depletable; no combustion/fuel refill/cold-sink radiator is present.";
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for(ServerLevel level:event.getServer().getAllLevels()) {
            var fixtures=TegFixtureSavedData.existing(level).orElse(null);if(fixtures==null)continue;
            var map=DELIVERY.computeIfAbsent(level,ignored->new HashMap<>());
            // One observation per completed, unique APC meter sample. Never multiply a repeated status read.
            for(var entry:fixtureEntries(fixtures)) {
                if(!entry.initialized())continue;
                observeDeliveryEntry(level,entry,map);
            }
        }
    }
    static void observeDeliveryEntry(ServerLevel level,TegFixtureSavedData.Entry entry,Map<BlockPos,TegFixtureDeliveryMeter.State> map){
        var outputPos=entry.mode()==TegFixtureSavedData.Mode.FUELED||entry.mode()==TegFixtureSavedData.Mode.CONTINUOUS?TegFueledFixturePlan.layout(entry.origin().offset(22,2,18)).electrical().devices().outputApc():TegFixturePlan.layout(core(entry.origin())).devices().outputApc();
        // Telemetry must not call Level#getBlockEntity: that getter requests the chunk.
        var chunk=level.getChunkSource().getChunk(outputPos.getX()>>4,outputPos.getZ()>>4,ChunkStatus.FULL,false);
        var apc=chunk instanceof LevelChunk full&&full.getBlockEntities().get(outputPos) instanceof PowerDeviceBlockEntity device
                &&full.getBlockState(outputPos).getBlock() instanceof PowerDeviceBlock block&&block.kind()==PowerDeviceKind.APC?device:null;
        var old=map.get(entry.origin());
        var sample=apc==null?PowerRuntime.ApcOutputObservation.UNKNOWN:PowerRuntime.apcOutputObservation(level,apc);
        map.put(entry.origin(),TegFixtureDeliveryMeter.observe(old,sample.known(),sample.sampleTick(),sample.outputWatts(),sample.topologyGeneration(),apc,entry.token()));
    }
    static String deliveryReadout(ServerLevel level,BlockPos origin){var d=DELIVERY.getOrDefault(level,Map.of()).get(origin);return d==null?"UNKNOWN (observer has no continuous fresh interval)":fmt(d.totalJoules())+" J since observer start; current meter "+(d.sampleTick()<0?"UNKNOWN":"sample "+d.sampleTick()+", topology "+d.topologyGeneration());}
    static TegFixtureDeliveryMeter.State deliveryState(ServerLevel level,BlockPos origin){return DELIVERY.getOrDefault(level,Map.of()).get(origin);}
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { if(event.getLevel() instanceof ServerLevel l)DELIVERY.remove(l); }

    private static List<TegFixtureSavedData.Entry> fixtureEntries(TegFixtureSavedData data) {
        return data.entries();
    }
    private static BlockPos core(BlockPos origin){return origin.offset(8,1,8);}
    private static GasMixture mixture(double o2,double temp){return new GasMixture(Map.of(GasType.OXYGEN,o2),temp);}
    private static void addSeeds(List<TrustedPipeFixtureInitialization.PortSeed> out,BlockPos pos,AtmosMachineBlockEntity host,Map<String,GasMixture> gases){
        for(var item:gases.entrySet())host.gasPorts().stream().filter(p->p.id().equals(item.getKey())).findFirst().ifPresent(p->out.add(new TrustedPipeFixtureInitialization.PortSeed(pos,p.id(),p.incarnation(),new GasMixture(item.getValue().gasMoles(),item.getValue().temperatureKelvin(),p.volume()))));
    }
    private static UUID port(AtmosMachineBlockEntity host,String id){return host.gasPorts().stream().filter(p->p.id().equals(id)).map(com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasPortHost.Port::incarnation).findFirst().orElse(null);}
    private static AtmosMachineBlockEntity machine(ServerLevel l,BlockPos p){return l.getBlockEntity(p) instanceof AtmosMachineBlockEntity m?m:null;}
    private static PowerDeviceBlockEntity power(ServerLevel l,BlockPos p){return l.getBlockEntity(p) instanceof PowerDeviceBlockEntity d?d:null;}
    private static PowerDeviceBlockEntity apc(ServerLevel l,BlockPos p){var d=power(l,p);return d!=null&&l.getBlockState(p).getBlock() instanceof PowerDeviceBlock b&&b.kind()==PowerDeviceKind.APC?d:null;}
    private static String portReadout(ServerLevel l,BlockPos host,String id){
        var h=machine(l,host);if(h==null)return "UNKNOWN";var p=h.gasPorts().stream().filter(x->x.id().equals(id)).findFirst().orElse(null);if(p==null)return "UNWIRED";
        var key=p.key(host);var entry=PipeGasInventorySavedData.existing(l).map(s->s.snapshot().get(key).orElse(null)).orElse(null);if(entry==null||!entry.active())return "UNWIRED";
        var gas=PipeGasInventorySavedData.existing(l).orElseThrow().snapshot().nodeShare(key);return fmt(gas.temperatureKelvin())+" K / "+fmt(gas.pressureKpa(entry.volume()))+" kPa / "+fmt(gas.totalMoles())+" mol";
    }
    private static String fmt(double n){return String.format(Locale.ROOT,"%.1f",n);}
    private static boolean validateCables(ServerLevel l,TegFixturePlan.Layout p){return p.cables().stream().allMatch(n->CableStorage.has(l,n.host(),n.face(),n.tier()));}
    private static boolean preflight(ServerLevel l,BlockPos o,TegFixturePlan.Layout p){
        if(TegFixtureSavedData.existing(l).map(d->d.entry(o).isPresent()||d.count()>=TegFixtureSavedData.MAX_FIXTURES).orElse(false))return false;
        Set<BlockPos> check=new HashSet<>(p.cableHosts());check.addAll(List.of(p.devices().core(),p.devices().northCirc(),p.devices().southCirc(),p.devices().startupApc(),p.devices().substation(),p.devices().outputApc(),p.devices().load()));
        for(BlockPos pos:check){if(!inBounds(l,pos)||l.getChunkSource().getChunk(pos.getX()>>4,pos.getZ()>>4,ChunkStatus.FULL,false)==null)return false;
            if(!l.getBlockState(pos).isAir()||l.getBlockEntity(pos)!=null)return false;}
        for(int x=0;x<TegFixturePlan.WIDTH;x++)for(int y=0;y<TegFixturePlan.HEIGHT;y++)for(int z=0;z<TegFixturePlan.DEPTH;z++){
            BlockPos q=o.offset(x,y,z);if(!inBounds(l,q)||l.getChunkSource().getChunk(q.getX()>>4,q.getZ()>>4,ChunkStatus.FULL,false)==null
                    ||!l.getBlockState(q).isAir()||!l.getFluidState(q).isEmpty()||l.getBlockEntity(q)!=null)return false;}
        return l.getEntities((net.minecraft.world.entity.Entity)null,new net.minecraft.world.phys.AABB(o.getX(),o.getY(),o.getZ(),o.getX()+TegFixturePlan.WIDTH,o.getY()+TegFixturePlan.HEIGHT,o.getZ()+TegFixturePlan.DEPTH),net.minecraft.world.entity.Entity::isAlive).isEmpty();
    }
    private static boolean loaded(ServerLevel l,BlockPos o){for(int x=0;x<TegFixturePlan.WIDTH;x++)for(int y=0;y<TegFixturePlan.HEIGHT;y++)for(int z=0;z<TegFixturePlan.DEPTH;z++){BlockPos p=o.offset(x,y,z);if(!inBounds(l,p)||l.getChunkSource().getChunk(p.getX()>>4,p.getZ()>>4,ChunkStatus.FULL,false)==null)return false;}return true;}
    private static boolean inBounds(ServerLevel l,BlockPos p){return p.getY()>=l.getMinBuildHeight()&&p.getY()<l.getMaxBuildHeight()&&l.getWorldBorder().isWithinBounds(p);}
}
