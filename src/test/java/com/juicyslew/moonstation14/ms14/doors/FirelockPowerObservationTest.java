package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.power.runtime.PowerRuntime;
import com.juicyslew.moonstation14.ms14.power.runtime.FirelockPowerFreshness;
import com.juicyslew.moonstation14.ms14.doors.prototype.FirelockData;
import com.juicyslew.moonstation14.ms14.doors.prototype.ModFirelocks;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FirelockPowerObservationTest {
    @Test
    void absentLevelOrReceiverIsUnknownRatherThanPowered() {
        assertFalse(PowerRuntime.firelockPower(null, null).known());
    }

    @Test void freshnessAcceptsKnownDeliveryIndependentOfPaidPowerAndRejectsStaleEvidence(){
        FirelockData data=productionData();UUID root=UUID.randomUUID();
        assertFresh(data,root,root,data,5,5,100,100,true,true,7,7,true);
        assertFresh(data,root,root,data,4.99,5,100,100,true,true,7,7,true);
        assertFresh(data,root,root,data,0,5,100,100,true,true,7,7,true);
        assertFresh(data,root,root,data,0,5,100,100,false,false,-1,-1,true);
        assertFresh(data,root,root,data,0,5,100,100,true,false,7,7,true);
        assertFresh(data,root,root,data,0,5,100,100,true,false,8,8,true);
        assertFresh(data,root,root,data,5,5,100,100,false,true,-1,-1,false);
        assertFresh(data,root,root,data,5,5,100,100,true,false,7,7,false);
        assertFresh(data,root,root,data,5,5,79,100,true,true,7,7,false);
        assertFresh(data,root,UUID.randomUUID(),data,5,5,100,100,true,true,7,7,false);
        assertFresh(data,root,root,productionData(),5,5,100,100,true,true,7,7,false);
        assertFresh(data,root,root,data,5,4,100,100,true,true,7,7,false);
        assertFresh(data,root,root,data,5,5,100,100,false,true,-1,-1,false);
        assertFresh(data,root,root,data,5,5,100,100,true,false,7,8,false);
        assertFresh(data,root,root,data,5,5,100,100,true,true,7,8,false);
        assertFresh(data,root,root,data,Double.NaN,5,100,100,true,true,7,7,false);
        assertFresh(data,root,root,data,5.01,5,100,100,true,true,7,7,false);
        assertFresh(data,root,root,data,5,5,100,121,true,true,7,7,false);
    }

    @Test void receiverKnowledgeDistinguishesResolvedEmptyFromUnresolvedCandidates(){
        assertEquals(true,FirelockPowerFreshness.receiverKnowledge(true,false,false),"empty, reconciled receiver radius is known-off");
        assertEquals(false,FirelockPowerFreshness.receiverKnowledge(false,false,false),"unready topology is never known-off");
        assertEquals(false,FirelockPowerFreshness.receiverKnowledge(true,true,false),"all-unknown candidates cannot be filtered into known-off");
        assertEquals(true,FirelockPowerFreshness.receiverKnowledge(true,true,true),"shared nearest-known policy can select a known candidate among unknown candidates");
    }

    private static void assertFresh(FirelockData expected,UUID expectedId,UUID sampledId,FirelockData sample,
            double delivered,long generation,long tick,long now,boolean provider,boolean breaker,
            long breakerAtSample,long breakerNow,boolean accepted){
        String evidence="delivery="+delivered+" provider="+provider+" breakerClosed="+breaker+" generation="+generation+" tick="+tick+" now="+now+" breakerRevision="+breakerAtSample+"/"+breakerNow;
        assertEquals(accepted,FirelockPowerFreshness.accepts(expectedId,sampledId,expected,sample,generation,
                5,tick,now,PowerRuntime.TICKS_PER_SOLVE,provider,breaker,breakerAtSample,breakerNow,delivered),evidence);
    }
    private static FirelockData productionData(){try{var raw=new LinkedHashMap<ResourceLocation,JsonObject>();for(String id:new String[]{"base_structure","base_firelock","firelock"})try(var in=FirelockPowerObservationTest.class.getResourceAsStream("/data/moonstation14/moonstation14/firelock/"+id+".json")){raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14",id),JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject());}var manager=new PrototypeManager();manager.register(ModFirelocks.TYPE);manager.reload(ModFirelocks.TYPE,raw);return manager.snapshot(ModFirelocks.TYPE).get(ModFirelocks.STANDARD);}catch(Exception e){throw new RuntimeException(e);}}
}
