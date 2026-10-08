package com.juicyslew.moonstation14.ms14.construction;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionGraphData;
import com.juicyslew.moonstation14.ms14.construction.domain.*;
import com.juicyslew.moonstation14.ms14.construction.prototype.*;
import com.juicyslew.moonstation14.ms14.prototype.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class ConstructionGraphTest {
    /** Shared resolved-catalog fixture for consumers that need actual inherited prototypes. */
    public static ConstructionCatalog resolvedCatalogForGuidance() throws Exception {
        var test=new ConstructionGraphTest();var manager=new PrototypeManager();
        manager.register(ModConstruction.GRAPH_TYPE);manager.register(ModConstruction.RECIPE_TYPE);
        manager.reload(Map.of(ModConstruction.GRAPH_TYPE,test.readDirectory("data/moonstation14/moonstation14/construction_graph"),
                ModConstruction.RECIPE_TYPE,test.readDirectory("data/moonstation14/moonstation14/construction_recipe")));
        return ModConstruction.catalog(manager.snapshot(ModConstruction.GRAPH_TYPE),manager.snapshot(ModConstruction.RECIPE_TYPE));
    }
    private ConstructionGraph graph() throws Exception {
        var url=Objects.requireNonNull(getClass().getClassLoader().getResource("data/moonstation14/moonstation14/construction_graph/girder.json"));
        var json=JsonParser.parseString(Files.readString(Path.of(url.toURI()))).getAsJsonObject();
        json.remove("id"); return ConstructionGraphData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow().resolve();
    }
    @Test void branchRequirementsAndRememberedGoalDoNotFilterEligibility() throws Exception {
        var g=graph(); var plastic=g.eligibleEdges("girder",true).stream().filter(e->e.to().equals("plastic_wall")).findFirst().orElseThrow();
        assertInstanceOf(ConstructionGraph.MaterialStep.class,plastic.steps().getFirst());
        assertEquals(ConstructionGraph.MaterialId.Plastic,((ConstructionGraph.MaterialStep)plastic.steps().getFirst()).material());
        assertTrue(g.eligibleEdges("girder",true).stream().anyMatch(e->e.to().equals("wall")));
        assertTrue(g.eligibleEdges("girder",false).stream().noneMatch(e->e.to().equals("wall")));
    }
    @Test void stepOrderAndReturnsAreExact() throws Exception {
        var g=graph(); var e=g.nodes().get("plastic_wall").edges().getFirst();
        assertEquals(new ConstructionGraph.ToolStep(com.juicyslew.moonstation14.ms14.construction.tool.ToolPrototypeData.Quality.Prying,10,10),e.steps().getFirst());
        var result=new ConstructionGraphProgress("plastic_wall",0,0).completeStep(g,true);
        assertEquals("girder",result.progress().node()); assertEquals(2,result.returns().getFirst().amount());
        assertEquals(ConstructionGraph.MaterialId.Plastic,result.returns().getFirst().material());
    }
    @Test void guidanceIsExplicitAndGoalIndependent() throws Exception {
        var g=graph(); assertEquals(ConstructionGraph.GuidanceStatus.ROUTE,g.guidance("girder","plastic_wall",true).status());
        assertEquals(ConstructionGraph.GuidanceStatus.AT_GOAL,g.guidance("plastic_wall","plastic_wall",true).status());
        assertEquals(ConstructionGraph.GuidanceStatus.UNREACHABLE,g.guidance("wall","plastic_wall",false).status());
        assertEquals(ConstructionGraph.GuidanceStatus.INVALID_NODE,g.guidance("bad","wall",true).status());
    }

    @Test void prototypeResolverInheritsRecipeDefaultsAndCatalogDerivesRequirements() throws Exception {
        var manager=new PrototypeManager(); manager.register(ModConstruction.GRAPH_TYPE); manager.register(ModConstruction.RECIPE_TYPE);
        var graphs=readDirectory("data/moonstation14/moonstation14/construction_graph");
        var recipes=readDirectory("data/moonstation14/moonstation14/construction_recipe");
        manager.reload(Map.of(ModConstruction.GRAPH_TYPE,graphs,ModConstruction.RECIPE_TYPE,recipes));
        var catalog=ModConstruction.catalog(manager.snapshot(ModConstruction.GRAPH_TYPE),manager.snapshot(ModConstruction.RECIPE_TYPE));
        var wall=ModConstruction.id("steel_wall"); var recipe=catalog.recipe(wall);
        assertEquals("Structures",recipe.category()); assertEquals("girder",recipe.graph());
        assertEquals("girder",recipe.startingNode()); assertEquals("wall",recipe.targetNode());
        var first=catalog.startingRequirements(wall).getFirst();
        assertEquals(new ConstructionGraph.MaterialStep(ConstructionGraph.MaterialId.Steel,2,2),first);
        var base=ConstructionRecipeData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,recipes.get(ModConstruction.id("base_recipe"))).getOrThrow();
        assertEquals(ConstructionRecipeData.OutputKind.STRUCTURE,base.outputKind());
    }
    @Test void disposalFamilyAndMetalRodResolveFromCatalog() throws Exception {
        var manager=new PrototypeManager(); manager.register(ModConstruction.GRAPH_TYPE); manager.register(ModConstruction.RECIPE_TYPE);
        manager.reload(Map.of(ModConstruction.GRAPH_TYPE,readDirectory("data/moonstation14/moonstation14/construction_graph"),ModConstruction.RECIPE_TYPE,readDirectory("data/moonstation14/moonstation14/construction_recipe")));
        var catalog=ModConstruction.catalog(manager.snapshot(ModConstruction.GRAPH_TYPE),manager.snapshot(ModConstruction.RECIPE_TYPE));
        for(var shape:List.of("straight","bend","trunk","junction","junction_flipped","y_junction","x_junction","router","router_flipped")) {
            var id=ModConstruction.id("disposal_"+shape); var recipe=catalog.recipe(id);
            assertEquals("disposal_pipes",recipe.family());
            assertEquals(List.of(new ConstructionGraph.MaterialStep(ConstructionGraph.MaterialId.Steel,2,1)),catalog.startingRequirements(id));
            assertEquals(1,catalog.outputStackCount(id));
        }
        var rod=ModConstruction.id("metal_rod");
        assertEquals(ConstructionRecipeData.OutputKind.ITEM,catalog.recipe(rod).outputKind());
        assertEquals(List.of(new ConstructionGraph.MaterialStep(ConstructionGraph.MaterialId.Steel,1,0)),catalog.startingRequirements(rod));
        assertEquals(2,catalog.outputStackCount(rod));
        assertEquals("moonstation14:metal_rod",catalog.graph(ModConstruction.id("metal_rod")).nodes().get("metal_rod").entity().orElseThrow());
    }

    @Test void selectedGoalsResolveTheirFirstPhysicalStageAndPipeProvider() throws Exception {
        var manager=new PrototypeManager();manager.register(ModConstruction.GRAPH_TYPE);manager.register(ModConstruction.RECIPE_TYPE);
        manager.register(ModConstruction.ENTITY_TYPE);manager.register(com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals.DISPOSAL_TYPE);
        manager.reload(Map.of(ModConstruction.GRAPH_TYPE,readDirectory("data/moonstation14/moonstation14/construction_graph"),
                ModConstruction.RECIPE_TYPE,readDirectory("data/moonstation14/moonstation14/construction_recipe"),
                ModConstruction.ENTITY_TYPE,readDirectory("data/moonstation14/moonstation14/construction_entity"),
                com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals.DISPOSAL_TYPE,readDirectory("data/moonstation14/moonstation14/disposal_entity")));
        var catalog=ModConstruction.catalog(manager.snapshot(ModConstruction.GRAPH_TYPE),manager.snapshot(ModConstruction.RECIPE_TYPE));
        var girder=catalog.initialStage(ModConstruction.id("steel_wall")).orElseThrow();
        assertEquals("girder",girder.node());assertEquals("Girder",girder.entity());assertEquals(1,girder.durationSeconds());
        int girderSteel=stageSteel(girder);
        assertTrue(com.juicyslew.moonstation14.ms14.hands.client.BodyHandClientPolicy.mayDeferConstructionUse(
                true, true, "moonstation14:steel", girderSteel, "moonstation14:steel", girderSteel), "resolved shared girder stage admits deferred steel intent");
        assertEquals("Girder",catalog.initialStage(ModConstruction.id("plastic_wall")).orElseThrow().entity());
        assertEquals("WallSolid",catalog.graph(ModConstruction.id("girder")).nodes().get("wall").entity().orElseThrow());
        var blockCatalog=manager.snapshot(ModConstruction.ENTITY_TYPE);var pipeCatalog=manager.snapshot(com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals.DISPOSAL_TYPE);
        var pipeEntities=Map.of("straight","disposal_pipe","bend","disposal_bend","trunk","disposal_trunk","junction","disposal_junction",
                "junction_flipped","disposal_junction_flipped","y_junction","disposal_y_junction","x_junction","disposal_x_junction","router","disposal_router","router_flipped","disposal_router_flipped");
        for(var shape:pipeEntities.keySet()){
            var stage=catalog.initialStage(ModConstruction.id("disposal_"+shape)).orElseThrow();
            assertEquals("moonstation14:"+pipeEntities.get(shape),stage.entity());
            assertEquals(2,((ConstructionGraph.MaterialStep)stage.steps().getFirst()).amount());assertEquals(1,stage.durationSeconds());
            int pipeSteel=stageSteel(stage);
            assertTrue(com.juicyslew.moonstation14.ms14.hands.client.BodyHandClientPolicy.mayDeferConstructionUse(
                    true, true, "moonstation14:steel", pipeSteel, "moonstation14:steel", pipeSteel), shape);
            assertEquals(com.juicyslew.moonstation14.ms14.construction.client.ConstructionStructurePreview.Provider.DISPOSAL,
                    com.juicyslew.moonstation14.ms14.construction.client.ConstructionStructurePreview.provider(stage.entity(),blockCatalog,pipeCatalog));
        }
        assertTrue(catalog.initialStage(ModConstruction.id("metal_rod")).isEmpty());
        assertEquals(com.juicyslew.moonstation14.ms14.construction.client.ConstructionStructurePreview.Provider.BLOCK,
                com.juicyslew.moonstation14.ms14.construction.client.ConstructionStructurePreview.provider(girder.entity(),blockCatalog,pipeCatalog));
    }

    private static int stageSteel(com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionCatalog.InitialStage stage) {
        return stage.steps().stream().filter(s -> s instanceof ConstructionGraph.MaterialStep)
                .map(s -> (ConstructionGraph.MaterialStep)s)
                .filter(s -> s.material() == ConstructionGraph.MaterialId.Steel)
                .mapToInt(ConstructionGraph.MaterialStep::amount).sum();
    }

    @Test void futureStructureGetsGenericBlockProviderFromResolvedEntityMapping() {
        var material=new ConstructionGraphData.Step("material","Steel",2,10,3);
        var edge=new ConstructionGraphData.Edge("future",Optional.empty(),List.of(material),List.of());
        var next=new ConstructionGraphData.Edge("finished",Optional.empty(),List.of(material),List.of());
        var graphData=new ConstructionGraphData("start",Map.of("start",new ConstructionGraphData.Node(Optional.empty(),List.of(edge)),
                "future",new ConstructionGraphData.Node(Optional.of("FutureFrame"),List.of(next)),
                "finished",new ConstructionGraphData.Node(Optional.of("FutureFinished"),List.of())));
        var graphCatalog=new PrototypeCatalog<>(Map.of(ModConstruction.id("future_graph"),graphData));
        var recipe=new ConstructionRecipeData("future","","Structures","future","","future_graph","future","finished",ConstructionRecipeData.OutputKind.STRUCTURE);
        var recipes=new PrototypeCatalog<>(Map.of(ModConstruction.id("future_recipe"),recipe));
        var catalog=new ConstructionCatalog(graphCatalog,recipes);var first=catalog.initialStage(ModConstruction.id("future_recipe")).orElseThrow();
        assertEquals("FutureFrame",first.entity());assertEquals(3,first.durationSeconds());
        var blockData=new com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData("minecraft:stone",true,false,false,1,true);
        var blocks=new PrototypeCatalog<>(Map.of(ModConstruction.entityId("FutureFrame"),blockData));
        assertEquals(com.juicyslew.moonstation14.ms14.construction.client.ConstructionStructurePreview.Provider.BLOCK,
                com.juicyslew.moonstation14.ms14.construction.client.ConstructionStructurePreview.provider(first.entity(),blocks,new PrototypeCatalog<>(Map.of())));
        assertEquals(com.juicyslew.moonstation14.ms14.construction.client.ConstructionStructurePreview.Provider.UNSUPPORTED,
                com.juicyslew.moonstation14.ms14.construction.client.ConstructionStructurePreview.provider(first.entity(),new PrototypeCatalog<>(Map.of()),new PrototypeCatalog<>(Map.of())));
    }
    @Test void missingGraphReferenceRejectedByComposedCatalog() throws Exception {
        var graphCatalog=new PrototypeCatalog<ConstructionGraphData>(Map.of());
        var recipe=new ConstructionRecipeData("bad","","","","","missing","a","b",ConstructionRecipeData.OutputKind.STRUCTURE);
        assertThrows(IllegalArgumentException.class,()->new ConstructionCatalog(graphCatalog,new PrototypeCatalog<>(Map.of(ModConstruction.id("bad"),recipe))));
    }
    private Map<net.minecraft.resources.ResourceLocation,com.google.gson.JsonObject> readDirectory(String path) throws Exception {
        var url=Objects.requireNonNull(getClass().getClassLoader().getResource(path)); var result=new LinkedHashMap<net.minecraft.resources.ResourceLocation,com.google.gson.JsonObject>();
        try(var files=Files.list(Path.of(url.toURI()))){for(var file:files.filter(p->p.toString().endsWith(".json")).toList()){var id=ModConstruction.id(file.getFileName().toString().replaceFirst("\\.json$",""));result.put(id,JsonParser.parseString(Files.readString(file)).getAsJsonObject());}}
        return result;
    }
}
