package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Function;

/** LIFO ballistic supply. Manual cycle ejects the last loaded live round; it is not a firing gate. */
public final class BallisticProviderKernel {
    private BallisticProviderKernel() {}
    public record Ejection(BallisticState state, Optional<ItemStack> round) {
        public Ejection { round=round.map(ItemStack::copy); }
        @Override public Optional<ItemStack> round(){return round.map(ItemStack::copy);}
    }
    public static GunAmmoProviderKernel.TakeResult take(ItemStack gun, BallisticState state,
            Map<ResourceLocation,CartridgeProfile> cartridges, Map<ResourceLocation,ProjectileProfile> projectiles,
            Function<ResourceLocation,ItemStack> materializer) {
        var rounds=new ArrayList<>(state.rounds()); ItemStack fired;
        int unspawned=state.unspawnedCount(); boolean spent=Boolean.TRUE.equals(state.rounds().isEmpty()?null:state.rounds().get(state.rounds().size()-1).get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get()));
        if(!rounds.isEmpty()) { ItemStack top=rounds.get(rounds.size()-1); fired=top.copyWithCount(1); if(top.getCount()==1)rounds.remove(rounds.size()-1);else {top.shrink(1);rounds.set(rounds.size()-1,top);} }
        else if(unspawned>0) { fired=materializer.apply(state.fillPrototype()); if(fired==null||fired.isEmpty())return invalid(gun); fired=fired.copyWithCount(1); unspawned--; }
        else return new GunAmmoProviderKernel.TakeResult(GunAmmoProviderKernel.Status.EMPTY,gun,null,Optional.empty(),Optional.empty());
        ResourceLocation cartridgeId=GunAmmoProviderKernel.itemId(fired); CartridgeProfile c=cartridges.get(cartridgeId);
        if(c==null||!projectiles.containsKey(c.projectile()))return invalid(gun);
        ItemStack updated=gun.copy(); updated.set(com.juicyslew.moonstation14.component.ModDataComponents.BALLISTIC_STATE.get(),new BallisticState(rounds,unspawned,state.fillPrototype()));
        if(!c.deleteOnSpawn()) fired.set(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get(),true);
        if(spent) return new GunAmmoProviderKernel.TakeResult(GunAmmoProviderKernel.Status.DRY_FIRE,updated,null,Optional.empty(),Optional.of(fired));
        return new GunAmmoProviderKernel.TakeResult(GunAmmoProviderKernel.Status.READY,updated,c.projectile(),Optional.empty(),c.deleteOnSpawn()?Optional.empty():Optional.of(fired));
    }
    private static GunAmmoProviderKernel.TakeResult invalid(ItemStack gun){return new GunAmmoProviderKernel.TakeResult(GunAmmoProviderKernel.Status.INVALID,gun,null,Optional.empty(),Optional.empty());}
    public static Ejection cycle(BallisticState state, Function<ResourceLocation,ItemStack> materializer) {
        var rounds=new ArrayList<>(state.rounds()); int unspawned=state.unspawnedCount(); ItemStack ejected=ItemStack.EMPTY;
        if(!rounds.isEmpty()){int last=rounds.size()-1;ItemStack top=rounds.get(last);ejected=top.copyWithCount(1);if(top.getCount()==1)rounds.remove(last);else{top.shrink(1);rounds.set(last,top);}}
        else if(unspawned>0){ejected=materializer.apply(state.fillPrototype());if(ejected==null||ejected.isEmpty())return new Ejection(state,Optional.empty());ejected=ejected.copyWithCount(1);unspawned--;}
        return new Ejection(new BallisticState(rounds,unspawned,state.fillPrototype()),ejected.isEmpty()?Optional.empty():Optional.of(ejected));
    }
    public static BallisticState insert(BallisticState state,ItemStack round,int capacity){if(round.isEmpty()||state.count()>=capacity)return state;var list=new ArrayList<>(state.rounds());list.add(round.copy());return new BallisticState(list,state.unspawnedCount(),state.fillPrototype());}
}
