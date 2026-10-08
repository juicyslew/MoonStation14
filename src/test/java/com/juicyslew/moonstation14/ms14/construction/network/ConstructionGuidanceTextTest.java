package com.juicyslew.moonstation14.ms14.construction.network;

import com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph;
import com.juicyslew.moonstation14.ms14.construction.tool.ToolPrototypeData;
import com.juicyslew.moonstation14.ms14.construction.world.ConstructionWorldData;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConstructionGuidanceTextTest {
    private static ConstructionWorldData.Record record(String current,String goal,int edge,int step,boolean anchored) {
        return new ConstructionWorldData.Record(new net.minecraft.core.BlockPos(1,2,3),new UUID(0,1),"moonstation14:steel_wall_girder", "steel_wall",goal,current,edge,step,anchored);
    }
    private static com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionCatalog catalog() throws Exception {
        return com.juicyslew.moonstation14.ms14.construction.ConstructionGraphTest.resolvedCatalogForGuidance();
    }
    @Test void catalogueProducerReturnsOnlyRememberedGoalAndImmediateRequirement() throws Exception {
        var catalog=catalog();
        var steel=ConstructionGuidanceText.content(catalog,record("girder","wall",0,0,true));
        assertEquals(new ConstructionGuidanceText.Content("Steel Wall","2 Steel"),steel);
        var plastic=ConstructionGuidanceText.content(catalog,record("girder","plastic_wall",0,0,true));
        assertEquals("Plastic Wall",plastic.goal());assertEquals("2 Plastic",plastic.requirement());
        var unanchored=ConstructionGuidanceText.content(catalog,record("girder","wall",0,0,false));
        assertEquals("Steel Wall",unanchored.goal());assertEquals("Use Anchoring tool",unanchored.requirement());
        var bare=ConstructionGuidanceText.content(catalog,record("girder","girder",0,0,true));
        assertEquals("Girder",bare.goal());assertTrue(Set.of("2 Steel","2 Plastic").contains(bare.requirement()));
        assertFalse(bare.requirement().contains("start"));
        assertEquals("2 Steel",ConstructionGuidanceText.content(catalog,record("girder","wall",0,1,true)).requirement());
        assertNull(ConstructionGuidanceText.content(catalog,record("wall","wall",0,0,true)));
        assertNull(ConstructionGuidanceText.content(catalog,record("plastic_wall","wall",0,0,true)));
        for(String shape:List.of("straight","bend","trunk","junction","junction_flipped","y_junction","x_junction","router","router_flipped")) {
            var pipe=new ConstructionWorldData.Record(new net.minecraft.core.BlockPos(1,2,3),new UUID(0,1),"moonstation14:disposal_pipe","moonstation14:disposal_"+shape,shape,shape,0,0);
            assertNull(ConstructionGuidanceText.content(catalog,pipe),shape);
        }
    }
    @Test void requirementTracksCurrentStepAndLegacyApiDelegatesToStructuredProducer() {
        var edge=new ConstructionGraph.Edge("finish",Optional.empty(),List.of(new ConstructionGraph.MaterialStep(ConstructionGraph.MaterialId.Steel,2,1),
                new ConstructionGraph.ToolStep(ToolPrototypeData.Quality.Prying,10,10),new ConstructionGraph.MaterialStep(ConstructionGraph.MaterialId.Plastic,1,1)),List.of());
        var graph=new ConstructionGraph("base",Map.of("base",new ConstructionGraph.Node(Optional.empty(),List.of(edge)),"finish",new ConstructionGraph.Node(Optional.of("finish"),List.of())));
        var content=ConstructionGuidanceText.content(graph,record("base","finish",0,1,true),Set.of(),ConstructionGuidanceText::humanize);
        assertEquals("Use Prying tool",content.requirement());
        assertEquals(content.goal()+"\n"+content.requirement(),ConstructionGuidanceText.generate(graph,record("base","finish",0,1,true),Set.of()));
    }
}
