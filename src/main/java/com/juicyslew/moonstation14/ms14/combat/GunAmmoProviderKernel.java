package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** Pure staged stack transitions. Caller publishes changed stack and handles deferred physical ejections. */
public final class GunAmmoProviderKernel {
    private GunAmmoProviderKernel(){}
    public enum Status { READY, DRY_FIRE, EMPTY, INVALID }
    public record TakeResult(Status status, ItemStack gun, ResourceLocation projectile, Optional<ItemStack> retainedRound, Optional<ItemStack> ejectedRound) {
        public TakeResult { gun=gun.copy(); retainedRound=retainedRound.map(ItemStack::copy); ejectedRound=ejectedRound.map(ItemStack::copy); }
        @Override public ItemStack gun(){return gun.copy();}
    }
    public static TakeResult takeOne(ItemStack staged, GunProfile profile, Map<ResourceLocation,CartridgeProfile> cartridges, Map<ResourceLocation,ProjectileProfile> projectiles){
        return takeOne(staged, profile, cartridges, projectiles, id -> { var item=BuiltInRegistries.ITEM.get(id); return item==null?ItemStack.EMPTY:new ItemStack(item); });
    }
    public static TakeResult takeOne(ItemStack staged, GunProfile profile, Map<ResourceLocation,CartridgeProfile> cartridges, Map<ResourceLocation,ProjectileProfile> projectiles, Function<ResourceLocation,ItemStack> materializer){
        if(profile.revolverAmmoProvider().isPresent()) { var s=staged.get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get()); if(s==null)s=RevolverProviderKernel.initialize(profile); boolean spent=s.chambers().get(s.index()).physical().map(x->Boolean.TRUE.equals(x.get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get()))).orElse(false); var r=RevolverProviderKernel.takeOne(s,profile,cartridges,projectiles); ItemStack out=staged.copy(); out.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(),r.state()); if(r.status()==RevolverProviderKernel.TakeStatus.READY){var shot=r.shot().orElseThrow();return new TakeResult(Status.READY,out,shot.projectilePrototype(),shot.physical(),Optional.empty());} return new TakeResult(r.status()==RevolverProviderKernel.TakeStatus.EMPTY?(spent?Status.DRY_FIRE:Status.EMPTY):Status.INVALID,out,null,Optional.empty(),Optional.empty()); }
        if(profile.magazineChamberProvider().isPresent()) { var s=staged.get(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get()); if(s==null)s=MagazineChamberProviderKernel.initialize(profile,materializer); return MagazineChamberProviderKernel.take(staged,s,profile,cartridges,projectiles); }
        if(profile.ballisticProvider().isPresent()) { var p=profile.ballisticProvider().orElseThrow(); var state=staged.get(com.juicyslew.moonstation14.component.ModDataComponents.BALLISTIC_STATE.get()); if(state==null)state=BallisticState.full(p.capacity(),p.fillCartridgePrototype()); return BallisticProviderKernel.take(staged,state,cartridges,projectiles,materializer); }
        return new TakeResult(Status.INVALID,staged,null,Optional.empty(),Optional.empty());
    }
    public static ResourceLocation itemId(ItemStack stack){return BuiltInRegistries.ITEM.getKey(stack.getItem());}
}
