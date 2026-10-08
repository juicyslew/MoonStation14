package com.juicyslew.moonstation14.ms14.construction.client;

import com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph;
import com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionCatalog;
import com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionRecipeData;
import com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction;
import com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData;
import com.juicyslew.moonstation14.ms14.disposal.prototype.ModDisposals;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation;
import com.juicyslew.moonstation14.ms14.disposal.domain.DisposalTubeRouting;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.Comparator;

/** Resolves browser icons from the composed recipe graph/entity policy and existing registered items. */
final class ConstructionRecipeIconResolver {
    static final int THUMBNAIL_LOGICAL_SIZE = 12;
    record Icon(ResourceLocation item, DisposalEntityData disposal) {
        Icon { if ((item==null)==(disposal==null)) throw new IllegalArgumentException("icon must be item or disposal geometry"); }
        static Icon item(ResourceLocation id) { return id==null?null:new Icon(id,null); }
        static Icon disposal(DisposalEntityData data) { return data==null?null:new Icon(null,data); }
    }
    private ConstructionRecipeIconResolver() { }

    static Icon recipeIcon(ResourceLocation id, ConstructionCatalog catalog,
            PrototypeCatalog<com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData> blocks,
            PrototypeCatalog<DisposalEntityData> disposal) {
        var recipe=catalog.recipe(id);
        if(recipe==null)return null;
        var graph=catalog.graph(parseGraph(recipe.graph()));
        if(graph==null)return null;
        var target=graph.nodes().get(recipe.targetNode());
        if(recipe.outputKind()==ConstructionRecipeData.OutputKind.ITEM && target!=null && target.entity().isPresent()) {
            ResourceLocation output=parse(target.entity().get());
            if(output!=null)return Icon.item(output);
        }
        Icon fromTarget=entityIcon(target==null?null:target.entity().orElse(null),blocks,disposal);
        if(fromTarget!=null)return fromTarget;
        ResourceLocation declared=parse(recipe.icon());
        if(isItem(declared))return Icon.item(declared);
        return catalog.initialStage(id).map(s->entityIcon(s.entity(),blocks,disposal)).orElse(null);
    }

    static Icon familyIcon(String family, ConstructionCatalog catalog,
            PrototypeCatalog<com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData> blocks,
            PrototypeCatalog<DisposalEntityData> disposal) {
        var representative=familyRepresentative(family,catalog,disposal);
        return representative==null?null:recipeIcon(representative,catalog,blocks,disposal);
    }

    static java.util.List<com.juicyslew.moonstation14.ms14.disposal.domain.DisposalMeshGeometry.Quad> thumbnailMesh(Icon icon) {
        if(icon==null||icon.disposal()==null)return java.util.List.of();
        return icon.disposal().geometry().mesh(icon.disposal().tube(),DisposalOrientation.identity());
    }

    static ResourceLocation familyRepresentative(String family, ConstructionCatalog catalog,
            PrototypeCatalog<DisposalEntityData> disposal) {
        return catalog.recipeIds().stream().filter(id-> {
            var recipe=catalog.recipe(id); return recipe!=null&&recipe.family().equals(family);
        }).min(Comparator.<ResourceLocation>comparingInt(id-> {
            var recipe=catalog.recipe(id);var stage=catalog.initialStage(id).orElse(null);
            var graph=catalog.graph(parseGraph(recipe.graph()));var target=graph.nodes().get(recipe.targetNode());
            var targetData=target==null?null:target.entity().map(entity->disposal.get(ModConstruction.entityId(entity))).orElse(null);
            if(targetData!=null&&sameBaseTube(targetData,disposal))return 0;
            return stage!=null&&target!=null&&target.entity().filter(stage.entity()::equals).isPresent()?0:1;
        }).thenComparingInt(id->geometryRank(catalog,id,disposal))
                .thenComparingInt(id->routeLength(catalog,id)).thenComparing(ResourceLocation::toString)).orElse(null);
    }

    private static boolean sameBaseTube(DisposalEntityData candidate,PrototypeCatalog<DisposalEntityData> disposal) {
        // The abstract DisposalTubeBase resolves geometry defaults but no Tube; disposal_pipe is
        // the composed concrete default Tube (STRAIGHT, rotation zero) used as the local base form.
        var base=disposal.get(ModDisposals.id("disposal_pipe"));
        if(base==null)return false;
        try { return candidate.tube().equals(base.tube())&&Double.compare(candidate.geometry().diameter(),base.geometry().diameter())==0; }
        catch(IllegalStateException ignored) { return false; }
    }

    private static int geometryRank(ConstructionCatalog catalog,ResourceLocation id,PrototypeCatalog<DisposalEntityData> disposal) {
        var recipe=catalog.recipe(id);var graph=catalog.graph(parseGraph(recipe.graph()));var node=graph.nodes().get(recipe.targetNode());
        var data=node==null?null:node.entity().map(e->disposal.get(ModConstruction.entityId(e))).orElse(null);
        if(data==null)return 0;
        try {
            var ports=DisposalTubeRouting.connections(data.tube(),DisposalOrientation.identity());
            int count=ports.size();
            boolean hub=com.juicyslew.moonstation14.ms14.disposal.domain.DisposalMeshGeometry.needsHub(
                    ports.stream().map(com.juicyslew.moonstation14.ms14.disposal.domain.DisposalDirection::toMC)
                            .collect(java.util.stream.Collectors.toSet()));
            return (hub?100:0)+count*10+data.tube().rotationQuarters();
        } catch(IllegalStateException ignored) { return 0; }
    }

    private static int routeLength(ConstructionCatalog catalog,ResourceLocation id) {
        var recipe=catalog.recipe(id);var graph=catalog.graph(parseGraph(recipe.graph()));
        var route=graph.guidance(graph.start(),recipe.targetNode(),true);
        if(route.status()!=ConstructionGraph.GuidanceStatus.ROUTE)route=graph.guidance(graph.start(),recipe.targetNode(),false);
        return route.status()==ConstructionGraph.GuidanceStatus.ROUTE?route.route().size():Integer.MAX_VALUE;
    }

    private static Icon entityIcon(String entity,
            PrototypeCatalog<com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionEntityData> blocks,
            PrototypeCatalog<DisposalEntityData> disposal) {
        if(entity==null)return null;
        var block=blocks.get(ModConstruction.entityId(entity));
        if(block!=null) {
            ResourceLocation blockId=parse(block.block());
            if(blockId!=null)return Icon.item(blockId); // block prototypes use the matching registered BlockItem id
        }
        var disposalEntity=disposal.get(ModConstruction.entityId(entity));
        if(disposalEntity!=null) {
            try { if(disposalEntity.tube()!=null)return Icon.disposal(disposalEntity); }
            catch(IllegalStateException ignored) { }
        }
        ResourceLocation entityId=parse(entity);
        return isItem(entityId)?Icon.item(entityId):null;
    }

    private static boolean isItem(ResourceLocation id) { return id!=null&&BuiltInRegistries.ITEM.containsKey(id); }
    private static ResourceLocation parseGraph(String id) { return id.indexOf(':')<0?ModConstruction.id(id):ResourceLocation.tryParse(id); }
    private static ResourceLocation parse(String id) {
        if(id==null||id.isBlank())return null;
        return ResourceLocation.tryParse(id.indexOf(':')<0?"moonstation14:"+id:id);
    }
}
