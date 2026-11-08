package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import com.juicyslew.moonstation14.ms14.atmos.piping.PipeTopology;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TegFueledFixtureTopologyTest {
    @Test void choosesOneRootPerComponentWhileExpectedMembershipRetainsPairedPorts() {
        var plan=TegFueledFixturePlan.layout(new BlockPos(0,64,0));List<Map<String,PipeTopology.NodeKey>> ports=new ArrayList<>();
        for(int i=0;i<7;i++){Map<String,PipeTopology.NodeKey> m=new HashMap<>();for(String id:List.of("inlet","outlet","pipe"))m.put(id,new PipeTopology.NodeKey(new BlockPos(i,70,0),PipeTopology.Layer.PRIMARY,id));ports.add(m);}
        var topology=TegFueledFixtureTopology.plan(plan,ports);
        assertEquals(6,topology.representatives().size());assertEquals(6,new HashSet<>(topology.representatives()).size());
        assertEquals(ports.get(0).get("outlet"),topology.representatives().get(0));
        assertTrue(topology.components().get(0).contains(ports.get(0).get("outlet")));
        assertTrue(topology.components().get(0).contains(ports.get(1).get("inlet")));
        assertTrue(topology.components().get(1).contains(ports.get(3).get("pipe")));
        var carrier=TegFueledFixtureTopology.carrierPlan(plan,ports);
        assertFalse(carrier.components().stream().anyMatch(c->c.contains(ports.get(3).get("pipe"))));
        assertEquals(topology.representatives(),carrier.representatives());
        assertEquals(ports.get(4).get("outlet"),topology.representatives().get(3));
        ports.add(Map.of("power-role-only",new PipeTopology.NodeKey(new BlockPos(8,70,0),PipeTopology.Layer.PRIMARY,"power-role-only")));
        var wrongArity=assertThrows(IllegalArgumentException.class,()->TegFueledFixtureTopology.plan(plan,ports));
        assertTrue(wrongArity.getMessage().contains("seven reconciled machines"),"TEG core is owned separately and must not be supplied as an eighth gas machine");
    }
    @Test void missingRequiredNamedPortFailsWithMachineAndPortContext() {
        var plan=TegFueledFixturePlan.layout(new BlockPos(0,64,0));List<Map<String,PipeTopology.NodeKey>> ports=new ArrayList<>();
        for(int i=0;i<7;i++){Map<String,PipeTopology.NodeKey> m=new HashMap<>();m.put("outlet",new PipeTopology.NodeKey(new BlockPos(i,70,0),PipeTopology.Layer.PRIMARY,"outlet"));if(i!=1)m.put("inlet",new PipeTopology.NodeKey(new BlockPos(i,71,0),PipeTopology.Layer.PRIMARY,"inlet"));if(i==3)m.put("pipe",new PipeTopology.NodeKey(new BlockPos(i,72,0),PipeTopology.Layer.PRIMARY,"pipe"));ports.add(m);}
        var failure=assertThrows(IllegalArgumentException.class,()->TegFueledFixtureTopology.plan(plan,ports));
        assertTrue(failure.getMessage().contains("machineIndex=1"));assertTrue(failure.getMessage().contains("port=inlet"));assertTrue(failure.getMessage().contains("available="));
    }
}
