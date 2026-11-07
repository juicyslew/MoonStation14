package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.atmos.piping.PipeGasPortHost;
import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import net.minecraft.core.BlockPos;
import java.util.*;

/** Pure expected gas-component membership for the continuous fixture. */
public final class TegContinuousFixtureTopology {
    private TegContinuousFixtureTopology() { }

    public static TegFueledFixtureTopology.Plan plan(TegContinuousFuelPlan.Layout layout,
            Map<BlockPos,List<PipeGasPortHost.Port>> actualPorts) {
        Objects.requireNonNull(layout); Objects.requireNonNull(actualPorts);
        var base=layout.fueled(); Map<BlockPos,Map<String,PipeTopology.NodeKey>> byBody=new HashMap<>();
         actualPorts.forEach((body,ports)->{if(ports==null||ports.isEmpty())throw new IllegalArgumentException("machine body must expose resolved gas ports: "+body);Map<String,PipeTopology.NodeKey> m=new HashMap<>();for(var p:ports)if(m.put(p.id(),p.key(body))!=null)throw new IllegalArgumentException("duplicate port "+p.id());byBody.put(body,m);});
        Set<BlockPos> expectedBodies=new HashSet<>();base.machines().forEach(m->expectedBodies.add(m.position()));
        expectedBodies.add(layout.origin().offset(6,2,4)); expectedBodies.add(layout.devicePosition(TegContinuousFuelPlan.DeviceRole.COLD_PRESSURE_PUMP));
        expectedBodies.addAll(layout.radiatorPositions());
        expectedBodies.addAll(layout.hotExchangers().subList(1,3));
        if(!actualPorts.keySet().equals(expectedBodies))throw new IllegalArgumentException("continuous gas-port body mismatch: expected "+expectedBodies.size()+" got "+actualPorts.keySet().size());
        List<Map<String,PipeTopology.NodeKey>> basePorts=new ArrayList<>();for(var m:base.machines())basePorts.add(byBody.get(m.position()));
        for(int i=0;i<base.machines().size();i++) {
            var kind=base.machines().get(i).kind();Set<String> required=switch(kind) {
                case CIRCULATOR,PUMP,PRESSURE_PUMP,EXCHANGER -> Set.of("inlet","outlet");
                case HEATER -> Set.of("pipe");
            };
            if(!byBody.get(base.machines().get(i).position()).keySet().equals(required))throw new IllegalArgumentException("base machine has missing or extra resolved gas ports at "+base.machines().get(i).position());
        }
        requireNames(byBody,layout.origin().offset(6,2,4),Set.of("inlet","filter","outlet"));
        for(BlockPos body:layout.radiatorPositions())requireNames(byBody,body,Set.of("inlet","outlet"));
        requireNames(byBody,layout.devicePosition(TegContinuousFuelPlan.DeviceRole.COLD_PRESSURE_PUMP),Set.of("inlet","outlet"));
        for(BlockPos body:layout.hotExchangers().subList(1,3))requireNames(byBody,body,Set.of("inlet","outlet"));
        List<Set<PipeTopology.NodeKey>> sets=new ArrayList<>();
        var baseHot=base.hotComponents();for(var c:baseHot)sets.add(pipeSet(c,PipeTopology.Layer.PRIMARY));
        var hotPipeComponents=layout.hotPipeComponents();
        for(int i=0;i<3;i++)for(BlockPos position:hotPipeComponents.get(i))sets.get(i).add(new PipeTopology.NodeKey(position,PipeTopology.Layer.PRIMARY));
        for(var c:layout.coldPipeComponents())sets.add(pipeSet(c,PipeTopology.Layer.PRIMARY));
         for(String route:List.of("oxygen","plasma","fuel"))sets.add(pipeSet(layout.routes().get(route),PipeTopology.Layer.PRIMARY));
        Set<PipeTopology.NodeKey> deviceKeys=new HashSet<>();
        for(var entry:actualPorts.entrySet())for(var p:entry.getValue()) {
            if(p.directions().size()!=1)throw new IllegalArgumentException("machine port must have one direction");
            var key=p.key(entry.getKey()); if(!deviceKeys.add(key))throw new IllegalArgumentException("duplicate device port key");
            var neighbor=new PipeTopology.NodeKey(entry.getKey().relative(p.directions().iterator().next()),p.layer()); int match=-1;
            for(int i=0;i<sets.size();i++)if(sets.get(i).contains(neighbor)){if(match!=-1)throw new IllegalArgumentException("ambiguous machine attachment");match=i;}
            if(match<0)throw new IllegalArgumentException("machine port has no expected pipe neighbor: "+key);
             if(p.layer()!=PipeTopology.Layer.PRIMARY)throw new IllegalArgumentException("machine port on wrong component layer");
            if(layout.radiatorPositions().contains(entry.getKey())) {
                int n="inlet".equals(p.id())?4:"outlet".equals(p.id())?5:-1;
                if(n<0||match!=n)throw new IllegalArgumentException("radiator port attached to wrong cold component: "+key);
            }
            if(entry.getKey().equals(layout.devicePosition(TegContinuousFuelPlan.DeviceRole.COLD_PRESSURE_PUMP))) {
                int n="inlet".equals(p.id())?5:"outlet".equals(p.id())?6:-1;
                if(n<0||match!=n)throw new IllegalArgumentException("pressure pump port attached to wrong cold component: "+key);
            }
            if(layout.hotExchangers().subList(1,3).contains(entry.getKey())) {
                int expected= "inlet".equals(p.id())?1:"outlet".equals(p.id())?2:-1;
                if(expected<0||match!=expected)throw new IllegalArgumentException("hot exchanger port attached to wrong parallel hot component: "+key);
            }
            sets.get(match).add(key);
        }
        for(int i=0;i<3;i++) {
            Set<PipeTopology.NodeKey> expected=new LinkedHashSet<>();
            if(i<3)expected.addAll(pipeSet(base.hotComponents().get(i),PipeTopology.Layer.PRIMARY));
            for(BlockPos position:hotPipeComponents.get(i))expected.add(new PipeTopology.NodeKey(position,PipeTopology.Layer.PRIMARY));
            for(var entry:actualPorts.entrySet())for(var port:entry.getValue()) {
                var endpoint=entry.getKey().relative(port.directions().iterator().next());
                if(hotPipeComponents.get(i).contains(endpoint))expected.add(port.key(entry.getKey()));
            }
            if(!sets.get(i).equals(expected))throw new IllegalArgumentException("hot topology differs from declared parallel-contact membership at component "+i);
        }
        Set<PipeTopology.NodeKey> union=new HashSet<>();List<PipeTopology.NodeKey> reps=new ArrayList<>();
        for(var s:sets){if(s.isEmpty()||!Collections.disjoint(union,s))throw new IllegalArgumentException("empty or aliased expected component");union.addAll(s);reps.add(s.iterator().next());}
         if(sets.size()!=10||!union.containsAll(deviceKeys)||deviceKeys.size()!=actualPorts.values().stream().mapToInt(List::size).sum())throw new IllegalArgumentException("incomplete continuous topology");
        return new TegFueledFixtureTopology.Plan(sets.stream().map(Set::copyOf).toList(),List.copyOf(reps));
    }
    private static void requireNames(Map<BlockPos,Map<String,PipeTopology.NodeKey>> ports,BlockPos body,Set<String> names) {
        if(!ports.get(body).keySet().equals(names))throw new IllegalArgumentException("resolved body ports mismatch at "+body+": expected "+names+" got "+ports.get(body).keySet());
    }
    private static Set<PipeTopology.NodeKey> pipeSet(Collection<BlockPos> positions,PipeTopology.Layer layer){Set<PipeTopology.NodeKey>s=new LinkedHashSet<>();positions.forEach(p->s.add(new PipeTopology.NodeKey(p,layer)));return s;}
}
