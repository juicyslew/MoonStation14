package com.juicyslew.moonstation14.ms14.atmos.machine.ui.client;

import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings;
import com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineControlRequest;
import com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineControlSnapshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AtmosMachineOverlayDraftTest {
    private static AtmosMachineControlSnapshot snap(UUID session,long request,long revision,boolean accepted,double pressure){
        var values=new AtmosMachineSettings.Values(revision,Optional.of(false),Optional.of(pressure),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty());
        var controls=new AtmosMachineControlSnapshot.Controls("profile",AtmosMachineProfile.Kind.PRESSURE_PUMP,true,true,false,false,false,false,4500,0,0,0);
        return new AtmosMachineControlSnapshot(7,session,request,accepted,values,controls,0,false);
    }
    private static AtmosMachineOverlay overlay(UUID session,boolean[] pointer,List<AtmosMachineControlRequest> sent){
        var editor=mock(EditBox.class);when(editor.getValue()).thenReturn("100.000");when(editor.isFocused()).thenReturn(true);
        var result=new AtmosMachineOverlay(null,session,400,300,()->pointer[0],sent::add,id->{},mock(Font.class),bounds->editor);
        result.snapshot(snap(session,-1,1,true,100));return result;
    }
    private static void clickPressure(AtmosMachineOverlay o){
         var r=o.pressureBoundsForTest();
         assertTrue(o.mouseClicked(r.x()+r.width()/2d,r.y()+r.height()/2d,0));
    }
    @Test void correlationIgnoresUnmatchedAndWrongSessionThenDenialPreservesDraftAndAcceptanceResolves(){
        UUID session=UUID.randomUUID();boolean[] pointer={true};var sent=new ArrayList<AtmosMachineControlRequest>();var o=overlay(session,pointer,sent);clickPressure(o);
        assertTrue(o.keyPressed(257,0,0));assertEquals(1,sent.size());var req=sent.get(0);assertEquals(0,req.requestId());
        o.snapshot(snap(UUID.randomUUID(),req.requestId(),2,false,120));assertEquals(1,sent.size());
        o.snapshot(snap(session,-1,2,true,120));
        o.snapshot(snap(session,req.requestId(),2,false,120));assertTrue(o.isSearchFocused());assertEquals("Request denied",o.messageForTest());
        assertTrue(o.keyPressed(257,0,0));assertEquals(2,sent.size());assertEquals(1,sent.get(1).requestId());
        o.snapshot(snap(session,sent.get(1).requestId(),3,true,200));assertFalse(o.isSearchFocused());
    }
    @Test void timeoutKeepsDraftAllowsNewGlobalRequestAndLockedPointerEnterIsIgnored(){
        UUID session=UUID.randomUUID();boolean[] pointer={true};var sent=new ArrayList<AtmosMachineControlRequest>();var o=overlay(session,pointer,sent);clickPressure(o);
        assertTrue(o.keyPressed(257,0,0));assertEquals(0,sent.get(0).requestId());for(int i=0;i<100;i++)o.advanceTick();
        assertTrue(o.isSearchFocused());pointer[0]=false;assertFalse(o.keyPressed(257,0,0));assertEquals(1,sent.size());
        pointer[0]=true;assertTrue(o.keyPressed(257,0,0));assertEquals(2,sent.size());assertEquals(1,sent.get(1).requestId());
    }
}
