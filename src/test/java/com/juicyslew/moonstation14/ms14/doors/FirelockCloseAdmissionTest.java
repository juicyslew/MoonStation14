package com.juicyslew.moonstation14.ms14.doors;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.doors.prototype.FirelockData;
import com.juicyslew.moonstation14.ms14.doors.prototype.ModFirelocks;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FirelockCloseAdmissionTest {
    @Test void blockedPartialCloseReopensForResolvedOpenTimeTwoAndCrushReopenUsesNormalOpeningPhase(){
        FirelockData data=productionData();
        var closing=new FirelockState(AirlockState.DoorPhase.CLOSING_PASSABLE,false,false,false,false,0,0,2);
        var aborted=closing.partialCloseObstructed(10,data);
        assertEquals(AirlockState.DoorPhase.OPEN,aborted.phase());
        assertEquals(22,aborted.transitionAt(),"partial-close abort waits resolved OpenTimeTwo (0.6 s), not retrySeconds");
        var crushed=new FirelockState(AirlockState.DoorPhase.CLOSED,false,false,false,false,0,0,3);
        var reopening=crushed.reopenAfterCrush(data,50);
        assertEquals(AirlockState.DoorPhase.OPENING,reopening.phase());
        assertEquals(52,reopening.transitionAt(),"post-crush recovery uses normal first opening phase even while unpowered");
        assertEquals(2.1,data.door().crushStunSeconds()+data.door().openTimeOneSeconds(),1e-9,"victim stun is base DoorStunTime plus OpenTimeOne");
        assertEquals(90,50+(long)Math.ceil(data.door().crushStunSeconds()*20),"reopen wait is 2.0-second base DoorStunTime");
        assertEquals(AirlockState.DoorPhase.OPENING_PASSABLE,reopening.tick(data,52).phase());
    }

    private static FirelockData productionData(){try{var raw=new LinkedHashMap<ResourceLocation,JsonObject>();for(String id:new String[]{"base_structure","base_firelock","firelock"})try(var in=FirelockCloseAdmissionTest.class.getResourceAsStream("/data/moonstation14/moonstation14/firelock/"+id+".json")){raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14",id),JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject());}var manager=new PrototypeManager();manager.register(ModFirelocks.TYPE);manager.reload(ModFirelocks.TYPE,raw);return manager.snapshot(ModFirelocks.TYPE).get(ModFirelocks.STANDARD);}catch(Exception e){throw new RuntimeException(e);}}
}
