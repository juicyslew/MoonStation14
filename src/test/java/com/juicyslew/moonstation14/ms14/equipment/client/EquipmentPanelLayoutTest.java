package com.juicyslew.moonstation14.ms14.equipment.client;

import static org.junit.jupiter.api.Assertions.*;
import com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData;
import com.juicyslew.moonstation14.ms14.hands.client.HandEquipmentGesture;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class EquipmentPanelLayoutTest {
    private static List<InventoryTemplateData.Slot> slots() {
        return List.of(
                slot("shoes","Default",1,0),slot("jumpsuit","Default",0,1),slot("outerClothing","Default",1,1),
                slot("gloves","Default",2,1),slot("neck","Default",0,2),slot("mask","Default",1,2),
                slot("eyes","Default",0,3),slot("ears","Default",2,2),slot("head","Default",1,3),
                slot("pocket1","MainHotbar",0,3),slot("pocket2","MainHotbar",2,3),slot("suitstorage","MainHotbar",2,0),
                slot("id","SecondHotbar",2,1),slot("belt","SecondHotbar",3,1),slot("back","SecondHotbar",3,0));
    }
    private static InventoryTemplateData.Slot slot(String id,String group,int x,int y) {
        return new InventoryTemplateData.Slot(id,id,ResourceLocation.withDefaultNamespace("test"),group,x,y,List.of(),List.of(),List.of(),false);
    }
    @Test void allTemplateSlotsAreUniqueVisibleAndBoundedAtStandardAndNarrowSizes() {
        for(int[] size:new int[][]{{1920,1080},{320,240},{180,150}}) {
            var layout=EquipmentPanelLayout.calculate(size[0],size[1],slots(),true);
            assertEquals(15,layout.slots().size());
            assertEquals(15,layout.slots().stream().map(EquipmentPanelLayout.SlotRect::slotId).distinct().count());
            var all=new ArrayList<com.juicyslew.moonstation14.ms14.hands.client.HandBarLayout.Rect>();
            all.add(layout.toggleRect()); all.addAll(layout.slots().stream().map(EquipmentPanelLayout.SlotRect::rect).toList());
            for(var r:all) { assertTrue(r.x()>=0&&r.y()>=0); assertTrue(r.x()+r.width()<=Math.max(1,size[0])); assertTrue(r.y()+r.height()<=Math.max(1,size[1])); }
            for(int i=1;i<all.size();i++) for(int j=0;j<i;j++) {
                var a=all.get(i); var b=all.get(j);
                assertFalse(a.x()<b.x()+b.width()&&a.x()+a.width()>b.x()&&a.y()<b.y()+b.height()&&a.y()+a.height()>b.y(),
                        "overlap at "+size[0]+"x"+size[1]+" rects "+i+"="+a+" and "+j+"="+b);
            }
        }
    }
    @Test void subsetAndCollapseKeepToggleAvailableAndEpochResetsExpanded() {
        var subset=EquipmentPanelLayout.calculate(640,480,slots().subList(0,3),false);
        assertEquals(3,subset.slots().size()); assertFalse(subset.expanded());
        var state=new EquipmentPanelState(); assertTrue(state.expanded(1)); state.toggle(1); assertFalse(state.expanded(1));
        state.beginBody(2); assertTrue(state.expanded(2));
    }
    @Test void clothingUsesHalfCellTemplateCoordinatesAndCollapsedHotbarPersists() {
        var layout=EquipmentPanelLayout.calculate(960,540,slots(),true);
        assertEquals(15,layout.cellSize());
        var shoes=layout.slots().stream().filter(s->s.slotId().equals("shoes")).findFirst().orElseThrow().rect();
        assertEquals(layout.toggleRect().y(),shoes.y());
        var jumpsuit=layout.slots().stream().filter(s->s.slotId().equals("jumpsuit")).findFirst().orElseThrow().rect();
        assertEquals(shoes.x()-16,jumpsuit.x());
        var collapsed=EquipmentPanelLayout.calculate(960,540,slots(),false);
        assertEquals(6,collapsed.slots().stream().filter(s->!s.slotId().matches("shoes|jumpsuit|outerClothing|gloves|neck|mask|eyes|ears|head")).count());
        var bar=com.juicyslew.moonstation14.ms14.hands.client.HandBarLayout.calculate(960,540);
        var order=List.of(bar.equipmentSlot("id"),bar.back(),bar.belt(),bar.leftHand(),bar.rightHand(),bar.equipmentSlot("pocket1"),bar.equipmentSlot("pocket2"),bar.equipmentSlot("suitstorage"));
        for(int i=1;i<order.size();i++) assertTrue(order.get(i-1).x()+order.get(i-1).width()<=order.get(i).x());
        assertEquals(bar.back().width(),layout.cellSize());
        for(var slot:layout.slots()) if(!slot.slotId().matches("back|belt|id|pocket1|pocket2|suitstorage"))
            assertEquals(bar.back().width(),slot.rect().width());
        for(var slot:layout.slots()) if(slot.slotId().matches("shoes|jumpsuit|outerClothing|gloves|neck|mask|eyes|ears|head"))
            assertEquals(layout.cellSize(),slot.rect().width());
        assertEquals(EquipmentPanelRenderer.ToggleArrow.HIDE,EquipmentPanelRenderer.toggleArrow(true));
        assertEquals(EquipmentPanelRenderer.ToggleArrow.SHOW,EquipmentPanelRenderer.toggleArrow(false));
    }
    @Test void genericSlotPlansRequireObservedSlotAndAppropriateHandState() {
        var empty=new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot.EquipmentSlot("back",null,null,0,null,null,0);
        var occupied=new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot.EquipmentSlot("back","worn","minecraft:stone",1,null,null,0);
        var held=new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot.Hand("left","held","minecraft:stick",1,false,null,null,0,null);
        var free=new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot.Hand("right",null,null,0,false,null,null,0,null);
        assertEquals("back",HandEquipmentGesture.slotPlan("back",empty,held,false).slotId());
        var otherHeld=new com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot.Hand("right","right-token","minecraft:stone",1,false,null,null,0,null);
        assertEquals("right",HandEquipmentGesture.slotPlan("back",empty,otherHeld,false).handId());
        assertEquals("worn",HandEquipmentGesture.slotPlan("back",occupied,free,true).expectedToken());
        assertNull(HandEquipmentGesture.slotPlan("unknown",empty,held,false));
        assertNull(HandEquipmentGesture.slotPlan("back",occupied,held,false));
        assertNull(HandEquipmentGesture.slotPlan("back",empty,free,false));
        assertNull(HandEquipmentGesture.slotPlan("back",empty,held,false,true,false));
        assertNull(HandEquipmentGesture.slotPlan("back",empty,held,false,true,true));
        assertNull(HandEquipmentGesture.slotPlan("back",empty,held,false,false,false));
        assertNull(HandEquipmentGesture.slotPlan("back",empty,held,false,false,true,false));
        assertTrue(EquipmentPanelLayout.receivesInput(true,false));
        assertFalse(EquipmentPanelLayout.receivesInput(true,true));
        assertFalse(EquipmentPanelLayout.receivesInput(false,false));
        assertEquals(HandEquipmentGesture.SlotDropKind.STORE_BACK,HandEquipmentGesture.slotDropKind("back",true,true,false,true));
        assertEquals(HandEquipmentGesture.SlotDropKind.STORE_BELT,HandEquipmentGesture.slotDropKind("belt",true,true,false,true));
        assertEquals(HandEquipmentGesture.SlotDropKind.DENY,HandEquipmentGesture.slotDropKind("head",true,true,false,true));
        assertEquals(HandEquipmentGesture.SlotDropKind.DENY,HandEquipmentGesture.slotDropKind("head",false,true,true,true));
    }
}
