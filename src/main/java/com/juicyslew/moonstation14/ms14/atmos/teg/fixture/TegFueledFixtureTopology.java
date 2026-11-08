package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import java.util.*;

/** Exact planned gas-component membership shared by fixture admission and native proofs. */
public final class TegFueledFixtureTopology {
    private TegFueledFixtureTopology() { }
    public record Plan(List<Set<PipeTopology.NodeKey>> components,List<PipeTopology.NodeKey> representatives) { }
    public static Plan plan(TegFueledFixturePlan.Layout p, List<? extends Map<String, PipeTopology.NodeKey>> ports) {
        Plan carrier=carrierPlan(p,ports);
        List<Set<PipeTopology.NodeKey>> result=new ArrayList<>(carrier.components());
        Set<PipeTopology.NodeKey> hot1=new LinkedHashSet<>(result.get(1));
        hot1.add(require(ports,3,"pipe")); result.set(1,Set.copyOf(hot1));
        return new Plan(List.copyOf(result),carrier.representatives());
    }
    public static Plan carrierPlan(TegFueledFixturePlan.Layout p, List<? extends Map<String, PipeTopology.NodeKey>> ports) {
        if (ports.size()!=7) throw new IllegalArgumentException("seven reconciled machines required");
        List<Set<PipeTopology.NodeKey>> result=new ArrayList<>();
        for(int i=0;i<6;i++) {
            int route=i%3; boolean cold=i>=3;
            Set<PipeTopology.NodeKey> keys=new LinkedHashSet<>();
            for(var pos:(cold?p.coldComponents():p.hotComponents()).get(route))keys.add(new PipeTopology.NodeKey(pos,PipeTopology.Layer.PRIMARY));
            if(!cold) for(var pos:p.sealedCrossings()) if(route==1&&pos.equals(p.core().offset(-14,0,-1))||route==2&&pos.equals(p.core().offset(-18,0,-1))) keys.add(new PipeTopology.NodeKey(pos,PipeTopology.Layer.PRIMARY));
            int a,b; String pa,pb;
            if(!cold){a=route==0?0:route==1?1:2;b=route==0?1:route==1?2:0;pa="outlet";pb="inlet";}
            else {a=route==0?4:route==1?5:6;b=route==0?5:route==1?6:4;pa="outlet";pb="inlet";}
            PipeTopology.NodeKey representative=require(ports,a,pa),paired=require(ports,b,pb);
            keys.add(representative); keys.add(paired);
            result.add(Set.copyOf(keys));
        }
        List<PipeTopology.NodeKey> representatives=new ArrayList<>();
        for(int i=0;i<6;i++){int machine=i<3?i:(i==3?4:i==4?5:6);representatives.add(require(ports,machine,"outlet"));}
        return new Plan(List.copyOf(result),List.copyOf(representatives));
    }
    private static PipeTopology.NodeKey require(List<? extends Map<String,PipeTopology.NodeKey>> ports,int machine,String id) {
        var map=ports.get(machine);var key=map.get(id);
        if(key==null)throw new IllegalArgumentException("missing named gas port machineIndex="+machine+" port="+id+" available="+map.keySet());
        return key;
    }
    public static boolean exact(Plan expected, Map<PipeTopology.NodeKey, ? extends Collection<PipeTopology.NodeKey>> actual) {
        if(expected.components().isEmpty()||actual.size()!=expected.components().size())return false;
        Set<PipeTopology.NodeKey> union=new HashSet<>();
        for(var component:expected.components())if(!Collections.disjoint(union,component))return false;else union.addAll(component);
        Set<PipeTopology.NodeKey> actualUnion=new HashSet<>();
        for(var e:actual.entrySet())if(!e.getValue().equals(expected.components().stream().filter(x->x.contains(e.getKey())).findFirst().orElse(Set.of()))||!actualUnion.addAll(e.getValue()))return false;
        return actualUnion.equals(union);
    }
}
