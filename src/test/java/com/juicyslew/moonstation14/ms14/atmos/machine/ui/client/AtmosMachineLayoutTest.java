package com.juicyslew.moonstation14.ms14.atmos.machine.ui.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AtmosMachineLayoutTest {
    @Test void capabilityLayoutCompactsRowsAndEditor(){
        var heater=new com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineControlSnapshot.Controls("heater",com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.HEATER,true,false,false,false,false,true,0,0,0,1000);
        var two=AtmosMachineLayout.forControls(640,360,heater,false,false);
         assertEquals(74,two.height());assertEquals(30,two.enabled().y());assertEquals(52,two.thermostat().y());assertEquals(20,two.enabled().height());
        var three=AtmosMachineLayout.forControls(640,360,new com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineControlSnapshot.Controls("filter",com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.FILTER,true,false,true,false,true,false,0,1,0,0),true,false);
         assertEquals(120,three.height());assertEquals(30,three.enabled().y());assertEquals(74,three.filter().y());assertTrue(three.edit().y()+three.edit().height()<=three.height());
    }
    @Test void viewportFallbackAndWindowHitGeometryAreBounded() {
        var tiny=AtmosMachineLayout.forViewport(160,120);
         assertTrue(tiny.tooSmall()); assertEquals(160,tiny.width()); assertEquals(120,tiny.height());
        var l=AtmosMachineLayout.forViewport(640,480);
          assertFalse(l.tooSmall()); assertEquals(216,l.width()); assertEquals(224,l.height());
        var close=l.close(); assertTrue(close.x()>=0&&close.x()+close.width()<=l.width());
        var first=l.row(0);var second=l.row(1);assertTrue(first.y()+first.height()<=second.y());
         var edit=l.edit();var submit=l.submit();assertEquals(edit,submit);
        var label0=l.numericLabel(0);var field0=l.numericEdit(0);var label1=l.numericLabel(1);var field1=l.numericEdit(1);var telemetry=l.telemetry();var message=l.message();
        assertTrue(label0.y()+label0.height()<=field0.y());assertTrue(field0.y()+field0.height()<=label1.y());assertTrue(label1.y()+label1.height()<=field1.y());assertTrue(field1.y()+field1.height()<=telemetry.y());assertTrue(telemetry.y()+telemetry.height()<=message.y());assertTrue(message.y()+message.height()<=l.height());
    }
    @Test void responsiveGuiPixelGeometryFitsSmallViewports(){
         for(int[] size:new int[][]{{320,240},{640,360}}){var l=AtmosMachineLayout.forViewport(size[0],size[1]);assertTrue(l.width()<=size[0]);assertTrue(l.height()<=size[1]);}
         var overflow=AtmosMachineLayout.forViewport(100,80);assertTrue(overflow.tooSmall());assertEquals(100,overflow.width());assertEquals(80,overflow.height());
    }
    @Test void expandedEditorIsDirectlyBelowItsOwnRowAndShiftsFollowingRows(){
        var controls=new com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineControlSnapshot.Controls("heater",com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.HEATER,true,false,true,false,false,true,0,0,0,1000);
        var l=AtmosMachineLayout.forControls(160,120,controls,true,false,"rate");
         assertEquals(l.rate().y()+22,l.edit().y());assertEquals(l.rate().y()+22+24,l.thermostat().y());
        assertTrue(l.edit().x()+l.edit().width()<=l.width());assertTrue(l.edit().y()+l.edit().height()<=l.height());
        var filterControls=new com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineControlSnapshot.Controls("filter",com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.FILTER,true,false,true,false,true,false,0,0,0,0);
         var picker=AtmosMachineLayout.forControls(160,224,filterControls,false,false,"filterGas");
         assertTrue(picker.edit().y()>picker.filter().y());assertTrue(picker.height()>=picker.edit().y()+22+5*18);
    }
    @Test void everyPickerRowFitsBelowSearchWithoutOverlap(){
        var c=new com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineControlSnapshot.Controls("filter",com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.FILTER,true,false,true,false,true,false,0,1,0,0);
        var l=AtmosMachineLayout.forControls(320,240,c,false,false,"filterGas");
        assertEquals(200,l.height());
        var search=l.edit();var previous=search;
        for(int i=0;i<5;i++){var row=l.gasRow(i);assertTrue(row.y()>=previous.y()+previous.height());assertTrue(row.y()+row.height()<=l.height());assertTrue(row.x()+row.width()<=l.width());previous=row;}
    }
    @Test void pickerHeightTracksVisibleGasRows(){
        var c=new com.juicyslew.moonstation14.ms14.atmos.machine.ui.AtmosMachineControlSnapshot.Controls("filter",com.juicyslew.moonstation14.ms14.atmos.machine.AtmosMachineProfile.Kind.FILTER,true,false,true,false,true,false,0,1,0,0);
        for(int count:new int[]{0,1,5}){var l=AtmosMachineLayout.forControls(320,240,c,false,false,"filterGas",count);var edit=l.edit();int end=count==0?edit.y()+22+18: l.gasRow(count-1).y()+l.gasRow(count-1).height();assertEquals(end+4,l.height());for(int i=0;i<count;i++)assertTrue(l.gasRow(i).y()+l.gasRow(i).height()<=l.height()-4);}
        assertEquals(200,AtmosMachineLayout.forControls(320,240,c,false,false,"filterGas").height());
    }
}
