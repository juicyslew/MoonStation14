package com.juicyslew.moonstation14.ms14.doors;

import org.junit.jupiter.api.Test;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.doors.prototype.*;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

class FirelockPresentationTest {
    @Test void latchMeansFullyClosedAndNeverMeansLeafPassable() {
        for (AirlockState.DoorPhase phase : AirlockState.DoorPhase.values()) {
            FirelockState state = new FirelockState(phase, false, false, false, false, 40, 0, 1);
            assertEquals(phase == AirlockState.DoorPhase.CLOSED, FirelockController.authoritativeLatch(state), phase.name());
        }
    }

    @Test void openingAtQ0IsUnlatchedWhileItsPhysicalLeafIsStillImpassable() {
        FirelockState openingAtQ0 = new FirelockState(AirlockState.DoorPhase.OPENING, false, false, false, false, 40, 0, 1);
        var presentation = new FirelockController.Presentation(openingAtQ0,false,20,40,0,0,net.minecraft.core.Direction.NORTH);
        assertNotNull(presentation);
        assertFalse(presentation.authoritativeLatch());
        assertFalse(presentation.state().passable());
    }

    @Test void immutableSnapshotRoundTripsFacingLatchWarningClocksAndRevision(){
        var state=new FirelockState(AirlockState.DoorPhase.CLOSING_PASSABLE,true,true,true,false,72,88,19);
        var original=new FirelockController.Presentation(state,false,60,72,41,0,net.minecraft.core.Direction.WEST);
        var tag=new net.minecraft.nbt.CompoundTag();FirelockPresentationNbt.write(tag,"View",original);
        assertEquals(original,FirelockPresentationNbt.read(tag,"View").orElseThrow());
        tag.putBoolean("ViewLatch",true);assertTrue(FirelockPresentationNbt.read(tag,"View").isEmpty(),"snapshot cannot invent a non-CLOSED latch");
        var absent=new net.minecraft.nbt.CompoundTag();assertTrue(FirelockPresentationNbt.read(absent,"View").isEmpty());
    }

    private FirelockData data(){var raw=new LinkedHashMap<ResourceLocation,JsonObject>();for(String id:new String[]{"base_structure","base_firelock","firelock"})try(var in=new InputStreamReader(getClass().getResourceAsStream("/data/moonstation14/moonstation14/firelock/"+id+".json"),StandardCharsets.UTF_8)){raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14",id),JsonParser.parseReader(in).getAsJsonObject());}catch(Exception failure){throw new RuntimeException(failure);}var manager=new PrototypeManager();manager.register(ModFirelocks.TYPE);manager.reload(ModFirelocks.TYPE,raw);return manager.snapshot(ModFirelocks.TYPE).get(ModFirelocks.STANDARD);}

    @Test void controllerProducedSnapshotsFeedAttachedRenderPlanAcrossBothStageBoundaries(){
        var data=data();
        for(var pair:new AirlockState.DoorPhase[][]{{AirlockState.DoorPhase.OPENING,AirlockState.DoorPhase.OPENING_PASSABLE},{AirlockState.DoorPhase.CLOSING,AirlockState.DoorPhase.CLOSING_PASSABLE}}){
            var controller=new FirelockController();var tag=new net.minecraft.nbt.CompoundTag();tag.putString("FirelockPhase",pair[0].name());tag.putBoolean("FirelockPowered",true);tag.putBoolean("FirelockLocked",false);tag.putBoolean("FirelockPressureWarning",true);tag.putBoolean("FirelockTemperatureWarning",false);tag.putLong("FirelockTransitionAt",102);tag.putLong("FirelockRevision",3);tag.putLong("FirelockMotionStartedAt",100);tag.putLong("FirelockPressureWarningStartedAt",73);tag.putLong("FirelockTemperatureWarningStartedAt",0);controller.load(tag);
            var before=controller.presentation(net.minecraft.core.Direction.NORTH);var beforeTag=new net.minecraft.nbt.CompoundTag();FirelockPresentationNbt.write(beforeTag,"View",before);before=FirelockPresentationNbt.read(beforeTag,"View").orElseThrow();
            var atBoundary=com.juicyslew.moonstation14.ms14.doors.client.FirelockFramePlanner.plan(before,data,102);assertEquals(100,before.motionStartedAt());
            if(pair[0]==AirlockState.DoorPhase.OPENING){var q0=com.juicyslew.moonstation14.ms14.doors.client.FirelockFramePlanner.plan(before,data,100);assertEquals(0,q0.openness());assertEquals(com.juicyslew.moonstation14.ms14.doors.client.FirelockFramePlanner.Indicator.DARK,q0.indicator());assertFalse(q0.cullBaseAirlock());}
            var next=controller.state().tick(data,102);controller.transitionAutomaticallyTo(next,102);var after=controller.presentation(net.minecraft.core.Direction.NORTH);var afterTag=new net.minecraft.nbt.CompoundTag();FirelockPresentationNbt.write(afterTag,"View",after);after=FirelockPresentationNbt.read(afterTag,"View").orElseThrow();
            var afterPlan=com.juicyslew.moonstation14.ms14.doors.client.FirelockFramePlanner.plan(after,data,102);assertEquals(100,after.motionStartedAt());assertEquals(atBoundary.openness(),afterPlan.openness());assertEquals(atBoundary.travel(),afterPlan.travel());assertEquals(73,after.pressureWarningStartedAt());
        }
        var closed=new FirelockController();var nbt=new net.minecraft.nbt.CompoundTag();nbt.putString("FirelockPhase",AirlockState.DoorPhase.CLOSED.name());nbt.putLong("FirelockRevision",8);closed.load(nbt);var fullyClosed=com.juicyslew.moonstation14.ms14.doors.client.FirelockFramePlanner.plan(closed.presentation(net.minecraft.core.Direction.EAST),data,400);assertTrue(fullyClosed.cullBaseAirlock());
    }
}
