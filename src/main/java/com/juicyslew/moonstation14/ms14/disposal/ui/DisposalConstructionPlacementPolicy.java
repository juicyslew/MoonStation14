package com.juicyslew.moonstation14.ms14.disposal.ui;

import com.juicyslew.moonstation14.ms14.disposal.prototype.DisposalEntityData;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.Optional;

/** Pure checks shared by server placement and focused tests. */
public final class DisposalConstructionPlacementPolicy {
    private DisposalConstructionPlacementPolicy(){}
    public static long requiredTicks(DisposalEntityData.ConstructionStep step){return step==null?Long.MAX_VALUE:(long)Math.ceil(step.timeSeconds()*20.0);}
    /** Resolve the typed material identifier through the real item registry; invalid IDs admit nothing. */
    public static Optional<Item> requiredMaterial(DisposalEntityData.ConstructionStep step){
        if(step==null)return Optional.empty();
        ResourceLocation id=ResourceLocation.tryParse(step.material());
        return id==null||!BuiltInRegistries.ITEM.containsKey(id)?Optional.empty():Optional.of(BuiltInRegistries.ITEM.get(id));
    }
    public static boolean matchesMaterial(DisposalEntityData.ConstructionStep step,Item held){
        return held!=null&&requiredMaterial(step).filter(held::equals).isPresent();
    }
    public static boolean enoughMaterial(DisposalEntityData.ConstructionStep step,Item held,int count){
        return matchesMaterial(step,held)&&count>=step.steelCount();
    }
    public static boolean samePlan(DisposalConstructionMode.Plan plan,String prototype,long revision){
        return plan!=null&&plan.prototype().equals(prototype)&&plan.revision()==revision;
    }
}
