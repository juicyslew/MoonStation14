package com.juicyslew.moonstation14.ms14.stripping.client;

import static org.junit.jupiter.api.Assertions.*;
import com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData;
import com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.equipment.client.EquipmentPanelLayout;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.LinkedHashMap;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

class StripPanelLayoutTest {
    private static List<InventoryTemplateData.Slot> resolvedHuman() throws Exception {
        try(var stream=StripPanelLayoutTest.class.getResourceAsStream("/data/moonstation14/moonstation14/inventory_template/human.json")) {
            assertNotNull(stream);
            JsonObject raw=JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var manager=new PrototypeManager(); manager.register(ModInventoryTemplates.TYPE);
            manager.reload(ModInventoryTemplates.TYPE,new LinkedHashMap<>(java.util.Map.of(ModInventoryTemplates.HUMAN_ID,raw)));
            return manager.snapshot(ModInventoryTemplates.TYPE).get(ModInventoryTemplates.HUMAN_ID).slots();
        }
    }
    @Test void preservesResolvedClothingCoordinatesAndAddsOnlyActualHandsAboveIt() throws Exception {
        var template=resolvedHuman();
        var layout=StripPanelLayout.calculate(20,300,960,540,template,List.of("left","right"));
        var hud=EquipmentPanelLayout.calculate(960,540,template,true);
        assertEquals(17,layout.slots().size());
        assertEquals(hud.cellSize(),layout.cellSize());
        for(var declared:template.stream().filter(s->s.group().equals("Default")).toList()) {
            var clothing=hud.slots().stream().filter(s->s.slotId().equals(declared.id())).findFirst().orElseThrow();
            var strip=layout.slots().stream().filter(s->s.kind()==StripPanelLayout.Kind.EQUIPMENT&&s.id().equals(clothing.slotId())).findFirst().orElseThrow();
            assertEquals(20+clothing.rect().x()-hud.toggleRect().x(),strip.rect().x());
            assertEquals(300+clothing.rect().y()-hud.toggleRect().y(),strip.rect().y());
        }
        var byId=layout.slots().stream().collect(java.util.stream.Collectors.toMap(StripPanelLayout.SlotRect::id,s->s));
        int topY=byId.get("id").rect().y(), handY=byId.get("left").rect().y(), pocketY=byId.get("pocket1").rect().y();
        assertEquals(topY,byId.get("back").rect().y());
        assertEquals(topY,byId.get("belt").rect().y());
        assertTrue(handY>topY);
        assertTrue(pocketY>handY);
        assertTrue(layout.slots().stream().filter(s->s.kind()==StripPanelLayout.Kind.EQUIPMENT&&!s.id().matches("shoes|jumpsuit|outerClothing|gloves|neck|mask|ears|eyes|head"))
                .allMatch(s->s.rect().y()<300-3*(layout.cellSize()+EquipmentPanelLayout.GRID_GAP)));
        assertEquals(17,byId.size()); // blank third hand position is not a target
        assertTrue(layout.slots().stream().filter(s->s.kind()==StripPanelLayout.Kind.HAND).allMatch(s->s.rect().width()==layout.cellSize()));
    }
    @Test void wrapsAtThreeColumnsAndUsesSharedNarrowViewportMetrics() throws Exception {
        var layout=StripPanelLayout.calculate(0,200,320,240,resolvedHuman(),List.of("one","two","three","four"));
        assertEquals(EquipmentPanelLayout.cellSizeForViewport(320,240),layout.cellSize());
        assertTrue(layout.slots().stream().allMatch(s->s.rect().width()>0&&s.rect().height()>0));
        assertTrue(layout.slots().stream().map(s->s.rect().x()).distinct().count()<=3);
        assertTrue(layout.slots().stream().filter(s->s.kind()==StripPanelLayout.Kind.HAND).count()==4);
    }
    @Test void productionWindowFitContainsHumanAndHandsOnlySlotRectsAtNormalAndNarrowWidths() throws Exception {
        for(var viewport:List.of(new int[]{960,540},new int[]{320,240})) {
            var human=StripPanelLayout.calculate(0,0,viewport[0],viewport[1],resolvedHuman(),List.of("left","right"));
            var size=StripPanelLayout.windowSize(human,viewport[0],viewport[1]);
            assertTrue(size.gridFits());
            int baseline=22-human.bounds().y();
            var placed=StripPanelLayout.calculate(4,baseline,viewport[0],viewport[1],resolvedHuman(),List.of("left","right"));
            assertTrue(placed.slots().stream().allMatch(s->{var r=s.rect();return r.x()>=4&&r.x()+r.width()<=size.width()-4
                    &&r.y()>=22&&r.y()+r.height()<=size.height()-6;}));
            assertEquals(human.cellSize(),placed.slots().stream().filter(s->s.kind()==StripPanelLayout.Kind.HAND)
                    .findFirst().orElseThrow().rect().width());
            var hands=StripPanelLayout.calculate(0,0,viewport[0],viewport[1],List.of(),List.of("left","right"));
            var handSize=StripPanelLayout.windowSize(hands,viewport[0],viewport[1]);
            assertTrue(handSize.gridFits());
            int handBaseline=22-hands.bounds().y();
            var handPlaced=StripPanelLayout.calculate(4,handBaseline,viewport[0],viewport[1],List.of(),List.of("left","right"));
            assertTrue(handPlaced.slots().stream().allMatch(s->{var r=s.rect();return r.x()>=4&&r.x()+r.width()<=handSize.width()-4
                    &&r.y()>=22&&r.y()+r.height()<=handSize.height()-6;}));
        }
    }
}
