package com.juicyslew.moonstation14.ms14.atmos.machine.ui.client;

import org.junit.jupiter.api.Test;
import com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings;
import com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineControlRequest;
import com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineControlSnapshot;
import net.minecraft.client.gui.Font;
import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AtmosMachineOverlayPointerTest {
    @Test void gasPickerSearchSelectsMatchingGasInsteadOfCycling() {
        UUID session=UUID.randomUUID();var sent=new java.util.ArrayList<AtmosMachineControlRequest>();var search=mock(net.minecraft.client.gui.components.EditBox.class);
        org.mockito.Mockito.when(search.isFocused()).thenReturn(true);org.mockito.Mockito.when(search.charTyped(org.mockito.ArgumentMatchers.anyChar(),org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);org.mockito.Mockito.when(search.getValue()).thenReturn("oxygen");
        var overlay=new AtmosMachineOverlay(null,session,400,300,()->true,sent::add,id->{},mock(Font.class),bounds->search);
        overlay.snapshot(filterSnapshot(session,7,Optional.of(com.juicyslew.moonstation14.ms14.atmos.core.GasType.NITROGEN)));
        overlay.openGasPickerForTest();assertTrue(overlay.charTyped('o',0));assertEquals(java.util.List.of("oxygen"),overlay.visibleGasLabelsForTest());
         var layout=AtmosMachineLayout.forControls(216,224,overlayStateControls(),false,false,"filterGas");var row=new AtmosMachineLayout.Bounds(10,layout.edit().y()+22,layout.width()-20,18);
        assertTrue(overlay.mouseClicked(92+row.x()+1,38+row.y()+1,0));
        var selected=sent.get(0);assertEquals(AtmosMachineSettings.Operation.SET_FILTER_GAS,selected.operation());assertEquals(com.juicyslew.moonstation14.ms14.atmos.core.GasType.OXYGEN.ordinal(),selected.gasIndex());assertEquals(7,selected.expectedRevision());
    }
    private static AtmosMachineControlSnapshot.Controls overlayStateControls(){return new AtmosMachineControlSnapshot.Controls("gas_filter",com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.FILTER,true,false,true,false,true,false,0,1,0,0);}
    @Test void numericEditorFactoryReceivesFullProductionEditWidth() {
        UUID session=UUID.randomUUID();var received=new AtomicReference<AtmosMachineLayout.Bounds>();
        var overlay=new AtmosMachineOverlay(null,session,400,300,()->true,r->{},id->{},mock(Font.class),bounds->{received.set(bounds);return mock(net.minecraft.client.gui.components.EditBox.class);});
        var controls=new AtmosMachineControlSnapshot.Controls("gas_pressure_pump",com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.PRESSURE_PUMP,true,true,false,false,false,false,5000,0,0,0);
        var settings=new com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings.Values(5,Optional.of(true),Optional.of(100d),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty());
        overlay.snapshot(new AtmosMachineControlSnapshot(3,session,-1,true,settings,controls,0,false));
        var layout=AtmosMachineLayout.forControls(216,224,controls,false,false);var pressure=layout.pressure();
        assertTrue(overlay.mouseClicked(92+pressure.x()+pressure.width()/2d,38+pressure.y()+pressure.height()/2d,0));
        assertNotNull(received.get());assertEquals(layout.edit().width(),received.get().width());
    }
    @Test void focusedOverlayRoutesTypedCharactersAndEnterSubmitsInlineFieldOnce(){
        UUID session=UUID.randomUUID();var sent=new java.util.ArrayList<AtmosMachineControlRequest>();var edit=mock(net.minecraft.client.gui.components.EditBox.class);
        org.mockito.Mockito.when(edit.isFocused()).thenReturn(true);org.mockito.Mockito.when(edit.charTyped(org.mockito.ArgumentMatchers.anyChar(),org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);org.mockito.Mockito.when(edit.getValue()).thenReturn("321");
        var overlay=new AtmosMachineOverlay(null,session,400,300,()->true,sent::add,id->{},mock(Font.class),bounds->edit);
        var controls=new AtmosMachineControlSnapshot.Controls("gas_pressure_pump",com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.PRESSURE_PUMP,true,true,false,false,false,false,5000,0,0,0);
        var settings=new com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings.Values(5,Optional.of(true),Optional.of(100d),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty());overlay.snapshot(new AtmosMachineControlSnapshot(3,session,-1,true,settings,controls,0,false));
        var row=AtmosMachineLayout.forControls(216,224,controls,false,false).pressure();assertTrue(overlay.mouseClicked(92+row.x()+4,38+row.y()+4,0));
        assertTrue(overlay.charTyped('3',0));assertTrue(overlay.keyPressed(257,0,0));assertEquals(1,sent.size());assertEquals(321d,sent.get(0).numericValue());
        overlay.keyPressed(257,0,0);assertEquals(1,sent.size());
    }
    @Test void enabledTogglePresentsIntentImmediatelyAndRejectedSnapshotReconcilesAuthority(){
        UUID session=UUID.randomUUID();var sent=new java.util.ArrayList<AtmosMachineControlRequest>();var overlay=new AtmosMachineOverlay(null,session,400,300,()->true,sent::add,id->{},mock(Font.class));
        var controls=new AtmosMachineControlSnapshot.Controls("gas_pressure_pump",com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.PRESSURE_PUMP,true,true,false,false,false,false,5000,0,0,0);
        var settings=new com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings.Values(5,Optional.of(false),Optional.of(100d),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty());overlay.snapshot(new AtmosMachineControlSnapshot(3,session,-1,true,settings,controls,0,false));
        var row=AtmosMachineLayout.forControls(216,224,controls,false,false).enabled();assertTrue(overlay.mouseClicked(92+row.x()+4,38+row.y()+4,0));assertEquals(Boolean.TRUE,overlay.enabledIntentForTest());
        overlay.snapshot(new AtmosMachineControlSnapshot(3,session,sent.get(0).requestId(),false,settings,controls,0,false));assertNull(overlay.enabledIntentForTest());
    }
    private static AtmosMachineControlSnapshot filterSnapshot(UUID session,long revision,Optional<com.juicyslew.moonstation14.ms14.atmos.core.GasType> gas){
        var controls=new AtmosMachineControlSnapshot.Controls("gas_filter",com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.FILTER,true,false,true,false,true,false,0,1,0,0);
        var settings=new com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineSettings.Values(revision,Optional.of(true),Optional.empty(),Optional.of(.2),Optional.empty(),gas,Optional.empty(),Optional.empty());
        return new AtmosMachineControlSnapshot(3,session,-1,true,settings,controls,0,false);
    }
    private static void clickFilter(AtmosMachineOverlay overlay){
        var l=AtmosMachineLayout.forControls(216,224,new AtmosMachineControlSnapshot.Controls("gas_filter",com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.FILTER,true,false,true,false,true,false,0,1,0,0),false,false);
        var b=l.filter();int originX=(400-216)/2,originY=(300-224)/2;
        assertTrue(overlay.mouseClicked(originX+b.x()+b.width()/2d,originY+b.y()+b.height()/2d,0));
    }
    @Test void titleDragClampsAndReleaseClearsCapture() {
        var overlay=new AtmosMachineOverlay(null,UUID.randomUUID(),400,300,()->true,r->{},id->{},mock(Font.class));
        assertTrue(overlay.mouseClicked(104,40,0));
        assertTrue(overlay.ownsPointerCapture());
        assertTrue(overlay.mouseDragged(900,900));
        assertFalse(overlay.contains(900,900));
        assertTrue(overlay.mouseReleased(0));
        assertFalse(overlay.ownsPointerCapture());
    }
    @Test void unavailablePointerIgnoresClickAndCloseUsesSession() {
        UUID session=UUID.randomUUID();var closed=new AtomicReference<UUID>();
        var overlay=new AtmosMachineOverlay(null,session,400,300,()->false,r->{},closed::set,mock(Font.class));
        assertFalse(overlay.mouseClicked(134,96,0));
        assertNull(closed.get());
        var close=AtmosMachineLayout.forControls(216,224,null,false,false).close();
        var bounds=com.juicyslew.moonstation14.ms14.ui.client.window.WindowChromeButtons.closeBounds(close.x(),close.y(),close.width(),close.height());
        double x=92+bounds.left()+1,y=38+bounds.top()+1;
        // Enable pointer for the actual close hit path.
        var active=new AtmosMachineOverlay(null,session,400,300,()->true,r->{},closed::set,mock(Font.class));
        assertTrue(active.mouseClicked(x,y,0));assertEquals(session,closed.get());
    }
}

