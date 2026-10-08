package com.juicyslew.moonstation14.ms14.construction.client;

import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction;
import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import com.juicyslew.moonstation14.ms14.ui.client.navigation.MachineSearchList;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConstructionRecipeIconResolverTest {
    private static Map<net.minecraft.resources.ResourceLocation,com.google.gson.JsonObject> directory(String path) throws Exception {
        var url=Objects.requireNonNull(ConstructionRecipeIconResolverTest.class.getClassLoader().getResource(path));
        var result=new LinkedHashMap<net.minecraft.resources.ResourceLocation,com.google.gson.JsonObject>();
        try(var files=Files.list(Path.of(url.toURI()))) {
            for(var file:files.filter(p->p.toString().endsWith(".json")).toList()) {
                var id=ModConstruction.id(file.getFileName().toString().replaceFirst("\\.json$",""));
                result.put(id,JsonParser.parseString(Files.readString(file)).getAsJsonObject());
            }
        }
        return result;
    }

    @Test void allResolvedRecipesHaveRegisteredOutputCompositionIconsAndStableFamilyBase() throws Exception {
        var manager=new PrototypeManager();
        manager.register(ModConstruction.GRAPH_TYPE);manager.register(ModConstruction.RECIPE_TYPE);
        manager.register(ModConstruction.ENTITY_TYPE);manager.register(ModDisposals.DISPOSAL_TYPE);
        manager.reload(Map.of(ModConstruction.GRAPH_TYPE,directory("data/moonstation14/moonstation14/construction_graph"),
                ModConstruction.RECIPE_TYPE,directory("data/moonstation14/moonstation14/construction_recipe"),
                ModConstruction.ENTITY_TYPE,directory("data/moonstation14/moonstation14/construction_entity"),
                ModDisposals.DISPOSAL_TYPE,directory("data/moonstation14/moonstation14/disposal_entity")));
        var catalog=ModConstruction.catalog(manager.snapshot(ModConstruction.GRAPH_TYPE),manager.snapshot(ModConstruction.RECIPE_TYPE));
        var blocks=manager.snapshot(ModConstruction.ENTITY_TYPE);var pipes=manager.snapshot(ModDisposals.DISPOSAL_TYPE);
        assertEquals(13,catalog.recipeIds().size());
        for(var id:catalog.recipeIds()) {
            var icon=ConstructionRecipeIconResolver.recipeIcon(id,catalog,blocks,pipes);
            assertNotNull(icon,id.toString());
            if(icon.item()!=null) {
                String model="assets/"+icon.item().getNamespace()+"/models/item/"+icon.item().getPath()+".json";
                assertNotNull(getClass().getClassLoader().getResource(model),id+" -> "+icon.item()+" missing item model");
            } else assertNotNull(icon.disposal(),id.toString());
        }
        assertEquals(ModConstruction.id("steel_wall_girder_block"),ConstructionRecipeIconResolver.recipeIcon(
                ModConstruction.id("girder"),catalog,blocks,pipes).item());
        assertEquals(ModConstruction.id("steel_wall_block"),ConstructionRecipeIconResolver.recipeIcon(
                ModConstruction.id("steel_wall"),catalog,blocks,pipes).item());
        assertEquals(ModConstruction.id("plastic_wall"),ConstructionRecipeIconResolver.recipeIcon(
                ModConstruction.id("plastic_wall"),catalog,blocks,pipes).item());
        assertEquals(ModConstruction.id("metal_rod"),ConstructionRecipeIconResolver.recipeIcon(
                ModConstruction.id("metal_rod"),catalog,blocks,pipes).item());
        assertNull(ConstructionRecipeIconResolver.recipeIcon(
                ModConstruction.id("disposal_straight"),catalog,blocks,pipes).item());
        assertNotNull(getClass().getClassLoader().getResource("assets/moonstation14/textures/block/disposal/pipe_material.png"));
        var pipeRecipeIds=List.of("straight","bend","trunk","junction","junction_flipped","y_junction","x_junction","router","router_flipped");
        var geometryMeshes=new HashSet<List<com.juicyslew.moonstation14.ms14.disposal.domain.DisposalMeshGeometry.Quad>>();
        for(String shape:pipeRecipeIds) {
            var recipeId=ModConstruction.id("disposal_"+shape);
            var recipe=catalog.recipe(recipeId);var graph=catalog.graph(ModConstruction.id(recipe.graph()));
            String entity=graph.nodes().get(recipe.targetNode()).entity().orElseThrow();
            assertEquals(shape.equals("straight")?"moonstation14:disposal_pipe":"moonstation14:disposal_"+shape,entity);
            var expected=pipes.get(ModConstruction.entityId(entity));
            var icon=ConstructionRecipeIconResolver.recipeIcon(recipeId,catalog,blocks,pipes);
            assertNull(icon.item());
            assertSame(expected,icon.disposal(),shape+" retained resolved shape prototype");
            assertTrue(icon.disposal().tube()!=null);
            assertEquals(expected.tube().kind(),icon.disposal().tube().kind());
            assertEquals(expected.tube().topology(),icon.disposal().tube().topology());
            geometryMeshes.add(ConstructionRecipeIconResolver.thumbnailMesh(icon));
        }
        assertTrue(geometryMeshes.size()>=6,"different resolved disposal topologies retain different reusable mesh geometry");
        int meshMin=Integer.MAX_VALUE,meshMax=Integer.MIN_VALUE;
        for(var shape:pipeRecipeIds) {
            var icon=ConstructionRecipeIconResolver.recipeIcon(ModConstruction.id("disposal_"+shape),catalog,blocks,pipes);
            for(var quad:ConstructionRecipeIconResolver.thumbnailMesh(icon)) for(var vertex:List.of(quad.a(),quad.b(),quad.c(),quad.d())) {
                // Browser's XP-90 pose maps local z to negative GUI y, then anchors at canvas bottom.
                int y=MachineSearchList.rowPlacement(9,true).iconY()+
                        (int)Math.round((ConstructionRecipeIconResolver.THUMBNAIL_LOGICAL_SIZE-vertex.z()*ConstructionRecipeIconResolver.THUMBNAIL_LOGICAL_SIZE));
                meshMin=Math.min(meshMin,y);meshMax=Math.max(meshMax,y);
            }
        }
         assertEquals(1,meshMin);assertEquals(13,meshMax);
         assertEquals(7,(meshMin+meshMax)/2,"resolved disposal mesh remains centred in the row");
        var bendIcon=ConstructionRecipeIconResolver.recipeIcon(ModConstruction.id("disposal_bend"),catalog,blocks,pipes);
        var widget=new com.juicyslew.moonstation14.ms14.ui.client.navigation.MachineSearchList(0,0,120,1,
                net.minecraft.network.chat.Component.literal("recipes"),new com.juicyslew.moonstation14.ms14.ui.model.FilteredListModel(2,1),id->{},q->{},
                new MachineSearchWidgetTest.DeterministicFont());
        var drawn=new java.util.concurrent.atomic.AtomicReference<>();
        widget.snapshotWithIcons(1,List.of(new com.juicyslew.moonstation14.ms14.ui.model.FilteredListModel.Entry(
                        "disposal_bend","Disposal Bend")),id->bendIcon,(graphics,descriptor,x,y)->
                        drawn.set(ConstructionRecipeIconResolver.thumbnailMesh((ConstructionRecipeIconResolver.Icon)descriptor)),16);
        widget.renderIconForRow(null,"disposal_bend",2,3);
        assertEquals(ConstructionRecipeIconResolver.thumbnailMesh(bendIcon),drawn.get());

        var girder=ConstructionRecipeIconResolver.familyRepresentative("Walls",catalog,pipes);
        var base=catalog.recipe(girder);
        var target=catalog.graph(ModConstruction.id(base.graph())).nodes().get(base.targetNode()).entity().orElseThrow();
        assertEquals(catalog.initialStage(girder).orElseThrow().entity(),target);
        assertEquals(ModConstruction.id("disposal_straight"),ConstructionRecipeIconResolver.familyRepresentative("disposal_pipes",catalog,pipes));
    }
}
