package com.juicyslew.moonstation14.ms14.atmos.machine.fixture;

import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineBlockEntity;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasInventory;
import net.minecraft.core.BlockPos;
import java.util.*;

/** One strict baseline-relative observation shared by manifold status and fixture acceptance. */
public final class ManifoldFixtureObservation {
    private ManifoldFixtureObservation() {}
    public record Port(String id,String lane,double beforeMoles,double nowMoles,double beforePlasma,double nowPlasma,double beforeNitrogen,
                       double nowNitrogen,double beforePressure,double nowPressure) {}
    public record Result(boolean valid,boolean observed,List<Port> ports,String reason) { public Result { ports=List.copyOf(ports); } }

    public static Result observe(AtmosMachineBlockEntity manifold,BlockPos host,PipeGasInventory inventory,
                                 AtmosMachineryFixtureSavedData.Entry saved) {
        if(manifold==null||inventory==null||saved==null)return new Result(false,false,List.of(),"UNAVAILABLE");
        var baseline=saved.demoBaselines().get("manifold");
        if(baseline==null)return new Result(false,false,List.of(),"UNVERIFIED: baseline missing");
        var ports=new ArrayList<Port>();
        if(manifold.gasPorts().size()!=6)return new Result(false,false,List.of(),"UNAVAILABLE: expected six ports");
        var owners=new HashMap<String,UUID>();
        for(var owner:saved.expectedPortOwners())if(owner.host().equals(host))owners.put(owner.portId(),owner.incarnation());
        for(var machinePort:manifold.gasPorts()) {
            String id=machinePort.id();
            if(!Objects.equals(owners.get(id),machinePort.incarnation()))return new Result(false,false,ports,"UNAVAILABLE: port identity mismatch");
            var entry=inventory.get(machinePort.key(host)).orElse(null);
            if(entry==null||!entry.active()||!entry.incarnation().equals(machinePort.incarnation())
                    ||Double.compare(entry.volume(),machinePort.volume())!=0)
                return new Result(false,false,ports,"UNAVAILABLE: port inventory identity mismatch");
            GasMixture mixture=inventory.nodeShare(entry.node());
            String prefix="port.manifold."+id;
            double bp=baseline.getOrDefault(AtmosMachineryFixtureService.portSpeciesMeasurementKey(AtmosMachineryFixturePlan.Kind.MANIFOLD,id,GasType.PLASMA),Double.NaN);
            double bn=baseline.getOrDefault(AtmosMachineryFixtureService.portSpeciesMeasurementKey(AtmosMachineryFixturePlan.Kind.MANIFOLD,id,GasType.NITROGEN),Double.NaN);
            double bm=baseline.getOrDefault(prefix+".moles",Double.NaN);
            double bpress=baseline.getOrDefault(prefix+".pressureKpa",Double.NaN);
            double cp=mixture.moles(GasType.PLASMA),cn=mixture.moles(GasType.NITROGEN);
            double pressure=mixture.pressureKpa(mixture.volumeCubicMeters());
            double nowMoles=mixture.totalMoles();
            if(!finite(bm,bp,bn,bpress,nowMoles,cp,cn,pressure))return new Result(false,false,ports,"UNVERIFIED: nonfinite port measurement");
            ports.add(new Port(id,machinePort.layer().toString(),bm,nowMoles,bp,cp,bn,cn,bpress,pressure));
        }
        return classify(ports);
    }
    /** Pure canonical classifier used by production collection and focused negative regressions. */
    public static Result classify(List<Port> readings) {
        if(readings==null||readings.size()!=6)return new Result(false,false,List.of(),"UNAVAILABLE: six port readings required");
        var ids=new HashSet<String>();boolean changed=true,common=true;
        for(var p:readings) {
            if(p==null||p.id()==null||p.lane()==null||!ids.add(p.id()))return new Result(false,false,readings,"UNAVAILABLE: duplicate/missing port");
            if(!finite(p.beforeMoles(),p.nowMoles(),p.beforePlasma(),p.nowPlasma(),p.beforeNitrogen(),p.nowNitrogen(),p.beforePressure(),p.nowPressure()))
                return new Result(false,false,readings,"UNVERIFIED: nonfinite port measurement");
            changed&=p.nowPlasma()-p.beforePlasma()>1e-6&&p.nowNitrogen()-p.beforeNitrogen()>1e-6;
            common&=p.nowPlasma()>1e-6&&p.nowNitrogen()>1e-6;
        }
        boolean observed=changed&&common;
        return new Result(true,observed,readings,observed?"MIX OBSERVED":changed?"UNVERIFIED: mixed species absent":"NO NEW MIXING OBSERVED");
    }
    private static boolean finite(double... values){for(double value:values)if(!Double.isFinite(value)||value<0)return false;return true;}
}
