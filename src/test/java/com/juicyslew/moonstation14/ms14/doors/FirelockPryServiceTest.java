package com.juicyslew.moonstation14.ms14.doors;

import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FirelockPryServiceTest {
    private static com.google.gson.JsonObject toolResource(String name){
        try(var reader=new java.io.InputStreamReader(java.util.Objects.requireNonNull(FirelockPryServiceTest.class.getResourceAsStream("/data/moonstation14/moonstation14/tool_profile/"+name+".json")),java.nio.charset.StandardCharsets.UTF_8)){return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();}
        catch(java.io.IOException e){throw new RuntimeException(e);}
    }
    @Test void resolvedInheritedCrowbarCompositionFeedsProductionStrongPryCapability(){
        var manager=new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();var type=com.juicyslew.moonstation14.ms14.tools.ModToolProfiles.TYPE;manager.register(type);
        var raw=new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation,com.google.gson.JsonObject>();
        raw.put(com.juicyslew.moonstation14.ms14.tools.ModToolProfiles.BASE_ITEM,toolResource("base_item"));raw.put(com.juicyslew.moonstation14.ms14.tools.ModToolProfiles.BASE_CROWBAR,toolResource("base_crowbar"));raw.put(com.juicyslew.moonstation14.ms14.tools.ModToolProfiles.CROWBAR,toolResource("crowbar"));
        raw.put(com.juicyslew.moonstation14.ms14.tools.ModToolProfiles.WIRECUTTER,toolResource("wirecutter"));raw.put(com.juicyslew.moonstation14.ms14.tools.ModToolProfiles.SCREWDRIVER,toolResource("screwdriver"));raw.put(com.juicyslew.moonstation14.ms14.tools.ModToolProfiles.MULTITOOL,toolResource("multitool"));raw.put(com.juicyslew.moonstation14.ms14.tools.ModToolProfiles.BOTANY_HOE,toolResource("botany_hoe"));raw.put(com.juicyslew.moonstation14.ms14.tools.ModToolProfiles.BOTANY_SPADE,toolResource("botany_spade"));raw.put(com.juicyslew.moonstation14.ms14.tools.ModToolProfiles.KITCHEN_KNIFE,toolResource("kitchen_knife"));
        manager.reload(type,raw);var catalog=manager.snapshot(type);var crowbar=catalog.get(com.juicyslew.moonstation14.ms14.tools.ModToolProfiles.CROWBAR);
        assertEquals(1,crowbar.prying().speedModifier());assertFalse(crowbar.strongPry());assertFalse(crowbar.poweredPry());
        boolean derivedStrong=FirelockPryService.strongPry(crowbar);
        assertTrue(derivedStrong);assertEquals(FirelockState.Pry.ALLOWED,new FirelockState(AirlockState.DoorPhase.CLOSED,true,false,false,false,0,0,0).pryVeto(crowbar.poweredPry(),derivedStrong));
        var wirecutter=catalog.get(com.juicyslew.moonstation14.ms14.tools.ModToolProfiles.WIRECUTTER);
        assertFalse(FirelockPryService.strongPry(wirecutter));assertEquals(FirelockState.Pry.VETOED,new FirelockState(AirlockState.DoorPhase.CLOSED,true,false,false,false,0,0,0).pryVeto(wirecutter.poweredPry(),FirelockPryService.strongPry(wirecutter)));
        assertEquals(FirelockState.Pry.VETOED,new FirelockState(AirlockState.DoorPhase.CLOSED,true,false,false,false,0,0,0).pryVeto(false,false));
    }
    private LiveHands hands(String token,String stack,long revision){
        String occupant=token==null?"":" ,\"occupant\":{\"token\":\""+token+"\",\"stack\":"+stack+"}";
        String json="{\"hands\":[{\"id\":\"left\""+occupant+"},{\"id\":\"right\"}],\"active\":\"left\",\"revision\":"+revision+"}";
        return LiveHands.CODEC.parse(JsonOps.INSTANCE,com.google.gson.JsonParser.parseString(json)).getOrThrow();
    }
    @Test void admittedVacantHandUsesAbsenceProofAndInvalidatesOnOccupancyOrRevision(){
        LiveHands empty=hands(null,null,7);
        assertTrue(empty.token("left").isEmpty());assertTrue(empty.stackCopy("left").isEmpty());
        assertTrue(DoorPrySupport.matchesEmptyHandSnapshot(empty,"left",null,7));
        assertFalse(DoorPrySupport.matchesEmptyHandSnapshot(empty,"left",null,8));
        LiveHands filled=hands("replacement","{\"id\":\"minecraft:stick\",\"count\":1}",8);
        assertFalse(DoorPrySupport.matchesEmptyHandSnapshot(filled,"left",null,7));
        ItemStack held=filled.stackCopy("left").orElseThrow();
        assertTrue(DoorPrySupport.matchesHeldHandSnapshot(filled,"left","replacement",held,8));
        assertFalse(DoorPrySupport.matchesHeldHandSnapshot(filled,"left","replacement",held,9));
        assertFalse(DoorPrySupport.matchesHeldHandSnapshot(filled,"left","replacement",new ItemStack(Items.DIAMOND),8));
    }
    @Test void sourcePryDurationUsesResolvedDoorTimeAndToolModifierAsDenominator(){
        assertEquals(60,FirelockPryService.durationTicks(1.5,1,.5));
        assertEquals(90,FirelockPryService.durationTicks(1.5,1.5,.5));
        assertEquals(30,FirelockPryService.durationTicks(1.5,1,1));
        assertEquals(45,FirelockPryService.durationTicks(1.5,1.5,1));
    }
}
