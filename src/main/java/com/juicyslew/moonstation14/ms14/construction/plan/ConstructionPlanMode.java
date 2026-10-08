package com.juicyslew.moonstation14.ms14.construction.plan;

import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.construction.prototype.ConstructionRecipeData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Server-owned selected structure intent. It is deliberately independent of hands and inventory. */
public final class ConstructionPlanMode {
    public record Plan(UUID body, long epoch, String recipe, int rotation, long revision, long sequence) {}
    private record Key(UUID actor, UUID body, long epoch) {}
    record Entry(String recipe, int rotation, long revision, long sequence) {}
    static Entry transition(Entry old, ConstructionPlanRequest request, java.util.function.Predicate<String> supported) {
        Objects.requireNonNull(request); Objects.requireNonNull(supported);
        long seq=old==null?0:old.sequence(), rev=old==null?0:old.revision();
        if(request.sequence()<=seq||request.sequence()<=0)return old;
        String recipe=old==null?null:old.recipe();int rotation=old==null?0:old.rotation();
        switch(request.action()) {
            case SELECT, FAMILY_VARIANT -> { if(!supported.test(request.recipe()))return old;recipe=request.recipe();rotation=0;rev++; }
            case CLEAR -> {recipe=null;rotation=0;rev++;}
            case ROTATE -> {if(recipe==null)return old;rotation++;rev++;}
        }
        return new Entry(recipe,rotation,rev,request.sequence());
    }
    private static final Map<Key, Entry> PLANS = new HashMap<>();
    private ConstructionPlanMode() {}

    public static synchronized Optional<Plan> handle(ServerPlayer actor, ConstructionPlanRequest request) {
        var a = HandActorAuthority.resolve(actor).orElse(null);
        if (a == null || request == null || !matchesAuthority(a.body().getUUID(),a.epoch(),request.body(),request.epoch())) return Optional.empty();
        UUID actorId = actor.getUUID();
        PLANS.keySet().removeIf(k -> k.actor().equals(actorId) && (!k.body().equals(request.body()) || k.epoch() != request.epoch()));
        Key key = new Key(actorId, request.body(), request.epoch());
        Entry old = PLANS.get(key);
        if(request.action()==ConstructionPlanRequest.Action.FAMILY_VARIANT) {
            var prior=old==null||old.recipe()==null?null:PrototypeRuntime.serverConstructionCatalog().recipe(ResourceLocation.tryParse(old.recipe()));
            var candidate=PrototypeRuntime.serverConstructionCatalog().recipe(ResourceLocation.tryParse(request.recipe()));
            if(prior==null||candidate==null||!prior.family().equals(candidate.family()))return Optional.empty();
        }
        Entry next=transition(old,request,ConstructionPlanMode::supported);if(next==old)return Optional.empty();
        String selected=next.recipe();int rotation=next.rotation();long revision=next.revision();
        if (!bridgeDisposal(actor, a, request, selected, rotation)) return Optional.empty();
        PLANS.put(key, next);
        return Optional.of(new Plan(request.body(), request.epoch(), selected, rotation, revision, request.sequence()));
    }
    static boolean matchesAuthority(UUID body,long epoch,UUID requestedBody,long requestedEpoch) {
        return body!=null&&body.equals(requestedBody)&&epoch>0&&epoch==requestedEpoch;
    }
    private static boolean bridgeDisposal(ServerPlayer actor, HandActorAuthority.Snapshot authority, ConstructionPlanRequest request,
                                          String selected, int rotation) {
        var legacy = com.juicyslew.moonstation14.ms14.disposal.ui.DisposalConstructionMode.query(actor).orElse(null);
        if (legacy == null) return false;
        long legacySeq = Math.max(request.sequence(), legacy.sequence() + 1);
        var action = request.action();
        if (action == ConstructionPlanRequest.Action.CLEAR || selected == null || !isDisposal(selected)) {
            if (legacy.active()) return com.juicyslew.moonstation14.ms14.disposal.ui.DisposalConstructionMode.handle(actor,
                    new com.juicyslew.moonstation14.ms14.disposal.ui.DisposalConstructionRequest(authority.body().getUUID(),authority.epoch(),legacySeq,
                            com.juicyslew.moonstation14.ms14.disposal.ui.DisposalConstructionRequest.Action.EXIT,"")).isPresent();
            return true;
        }
        if (action == ConstructionPlanRequest.Action.ROTATE) {
            if (!legacy.active()) return false;
            return com.juicyslew.moonstation14.ms14.disposal.ui.DisposalConstructionMode.handle(actor,
                    new com.juicyslew.moonstation14.ms14.disposal.ui.DisposalConstructionRequest(authority.body().getUUID(),authority.epoch(),legacySeq,
                            com.juicyslew.moonstation14.ms14.disposal.ui.DisposalConstructionRequest.Action.ROTATE,"")).isPresent();
        }
        String physical = disposalPhysicalPrototype(selected);
        if (physical == null) return false;
        // Legacy mode is activated only for a selected disposal structure; its selection is the resolved goal entity.
        if (!legacy.active() && com.juicyslew.moonstation14.ms14.disposal.ui.DisposalConstructionMode.handle(actor,
                new com.juicyslew.moonstation14.ms14.disposal.ui.DisposalConstructionRequest(authority.body().getUUID(),authority.epoch(),legacySeq,
                        com.juicyslew.moonstation14.ms14.disposal.ui.DisposalConstructionRequest.Action.ENTER,"")).isEmpty()) return false;
        return com.juicyslew.moonstation14.ms14.disposal.ui.DisposalConstructionMode.handle(actor,
                new com.juicyslew.moonstation14.ms14.disposal.ui.DisposalConstructionRequest(authority.body().getUUID(),authority.epoch(),legacySeq+1,
                        com.juicyslew.moonstation14.ms14.disposal.ui.DisposalConstructionRequest.Action.SELECT,physical)).isPresent();
    }
    private static boolean isDisposal(String id) {
        var recipe=PrototypeRuntime.serverConstructionCatalog().recipe(ResourceLocation.tryParse(id));
        return recipe!=null&&recipe.family().equalsIgnoreCase("disposal_pipes");
    }
    private static String disposalPhysicalPrototype(String id) {
        var catalog=PrototypeRuntime.serverConstructionCatalog();var recipe=catalog.recipe(ResourceLocation.tryParse(id));if(recipe==null)return null;
        var graphId=recipe.graph().contains(":")?ResourceLocation.tryParse(recipe.graph()):com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction.id(recipe.graph());
        var graph=graphId==null?null:catalog.graph(graphId);
        return resolvedEntityPrototype(graph,recipe.targetNode());
    }
    static String resolvedEntityPrototype(com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph graph,String goal) {
        var node=graph==null?null:graph.nodes().get(goal);
        if(node==null||node.entity().isEmpty())return null;
        var entity=ResourceLocation.tryParse(node.entity().get());
        return entity!=null&&"moonstation14".equals(entity.getNamespace())?entity.toString():null;
    }
    private static boolean supported(String text) {
        ResourceLocation id = ResourceLocation.tryParse(text); if (id == null) return false;
        var recipe = PrototypeRuntime.serverConstructionCatalog().recipe(id);
        return recipe != null && recipe.outputKind() == ConstructionRecipeData.OutputKind.STRUCTURE;
    }
    public static synchronized Optional<String> selectedRecipe(ServerPlayer actor, net.minecraft.world.entity.LivingEntity body) {
        var auth = HandActorAuthority.resolve(actor).orElse(null);
        if (auth == null || auth.body() != body) return Optional.empty();
        Entry e = PLANS.get(new Key(actor.getUUID(), body.getUUID(), auth.epoch()));
        return e == null || e.recipe() == null ? Optional.empty() : Optional.of(e.recipe());
    }
    public static synchronized void clearActor(UUID actor) { PLANS.keySet().removeIf(k -> k.actor().equals(actor)); }
}
