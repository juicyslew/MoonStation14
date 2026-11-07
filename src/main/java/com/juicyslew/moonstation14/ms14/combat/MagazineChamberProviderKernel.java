package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.Optional;

/** Immutable state transitions for magazine-fed firearms; caller performs inventory writes. */
public final class MagazineChamberProviderKernel {
    private MagazineChamberProviderKernel() {}
    public record RackResult(MagazineChamberState state, java.util.Optional<ItemStack> ejected) {
        public RackResult { ejected=ejected.map(ItemStack::copy); }
        @Override public java.util.Optional<ItemStack> ejected(){return ejected.map(ItemStack::copy);}
    }
    public static MagazineChamberState initialize(GunProfile profile, java.util.function.Function<ResourceLocation, ItemStack> materializer) {
        var p = profile.magazineChamberProvider().orElseThrow();
        ItemStack magazine = p.startingMagazine().map(materializer).orElse(ItemStack.EMPTY);
        ItemStack chamber = p.startingChamber().map(materializer).orElse(ItemStack.EMPTY);
        return new MagazineChamberState(magazine, chamber, p.boltClosed());
    }
    public static MagazineChamberState insertMagazine(MagazineChamberState s, ItemStack magazine, GunProfile gun, Map<ResourceLocation, AmmoContainerProfile> profiles) {
        if (magazine.isEmpty() || !s.magazine().isEmpty()) return s;
        var p = profiles.get(GunAmmoProviderKernel.itemId(magazine));
        return p == null || java.util.Collections.disjoint(p.magazineTags(), gun.magazineChamberProvider().orElseThrow().magazineTags()) ? s : new MagazineChamberState(magazine, s.chamber(), s.boltClosed());
    }
    public static MagazineChamberState ejectMagazine(MagazineChamberState s) { return new MagazineChamberState(ItemStack.EMPTY, s.chamber(), s.boltClosed()); }
    /** Insert one physical round into an open, empty chamber without touching the magazine. */
    public static Optional<MagazineChamberState> insertChamber(MagazineChamberState s, ItemStack round, GunProfile profile, Map<ResourceLocation,CartridgeProfile> cartridges) {
        if (s == null || round == null || round.isEmpty() || round.getCount()!=1 || s.boltClosed() || !s.chamber().isEmpty()) return Optional.empty();
        var cp=cartridges.get(GunAmmoProviderKernel.itemId(round));
        if(cp==null || java.util.Collections.disjoint(cp.tags(),profile.magazineChamberProvider().orElseThrow().cartridgeTags())) return Optional.empty();
        return Optional.of(new MagazineChamberState(s.magazine(),round.copy(),false));
    }
    /** Toggle bolt: opening ejects only the chambered round; closing feeds only an empty chamber. */
    public static RackResult toggleBolt(MagazineChamberState s, GunProfile profile) {
        if(s.boltClosed()) return new RackResult(new MagazineChamberState(s.magazine(),ItemStack.EMPTY,false),s.chamber().isEmpty()?Optional.empty():Optional.of(s.chamber()));
        return rack(s,profile);
    }
    public static RackResult rack(MagazineChamberState s, GunProfile profile) {
        var p = profile.magazineChamberProvider().orElseThrow();
        if (s.boltClosed() && !s.chamber().isEmpty()) return feed(new MagazineChamberState(s.magazine(), ItemStack.EMPTY, true), profile, Optional.of(s.chamber()));
        if (!s.boltClosed()) return feed(new MagazineChamberState(s.magazine(), s.chamber(), true), profile, Optional.empty());
        return feed(new MagazineChamberState(s.magazine(), ItemStack.EMPTY, true), profile, Optional.empty());
    }
    private static RackResult feed(MagazineChamberState s, GunProfile profile, java.util.Optional<ItemStack> ejected) {
        var p=profile.magazineChamberProvider().orElseThrow();
        ItemStack mag = s.magazine();
        if (!s.chamber().isEmpty()) return new RackResult(s,ejected);
        if (mag.isEmpty()) return new RackResult(new MagazineChamberState(mag, ItemStack.EMPTY, s.boltClosed()),ejected);
        var contents = mag.get(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get());
        if (contents == null || contents.rounds().isEmpty()) return new RackResult(s,ejected);
        var rounds = new java.util.ArrayList<>(contents.rounds()); ItemStack chamber = rounds.remove(rounds.size()-1).copy();
        mag.set(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get(), new AmmoContainerState(rounds));
        return new RackResult(new MagazineChamberState(mag, chamber, s.boltClosed()),ejected);
    }
    public static GunAmmoProviderKernel.TakeResult take(ItemStack gun, MagazineChamberState state, GunProfile profile, Map<ResourceLocation,CartridgeProfile> cartridges, Map<ResourceLocation,ProjectileProfile> projectiles) {
        if(state.chamber().isEmpty() || !state.boltClosed())return new GunAmmoProviderKernel.TakeResult(GunAmmoProviderKernel.Status.EMPTY,gun,null,Optional.empty(),Optional.empty());
        if(Boolean.TRUE.equals(state.chamber().get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get()))) {
            if(!profile.magazineChamberProvider().orElseThrow().autoCycle()) return new GunAmmoProviderKernel.TakeResult(GunAmmoProviderKernel.Status.DRY_FIRE,gun,null,Optional.empty(),Optional.empty());
            ItemStack spent=state.chamber(); var cycled=autoCycle(new MagazineChamberState(state.magazine(),ItemStack.EMPTY,true),profile);
            ItemStack out=gun.copy(); out.set(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get(),cycled.state());
            return new GunAmmoProviderKernel.TakeResult(GunAmmoProviderKernel.Status.DRY_FIRE,out,null,Optional.empty(),Optional.of(spent));
        }
        ItemStack round=state.chamber(); ResourceLocation id=GunAmmoProviderKernel.itemId(round); CartridgeProfile c=cartridges.get(id);
        if(c==null||java.util.Collections.disjoint(c.tags(),profile.magazineChamberProvider().orElseThrow().cartridgeTags())||!projectiles.containsKey(c.projectile()))return new GunAmmoProviderKernel.TakeResult(GunAmmoProviderKernel.Status.INVALID,gun,null,java.util.Optional.empty(),java.util.Optional.empty());
        boolean auto=profile.magazineChamberProvider().orElseThrow().autoCycle();
        if (!c.deleteOnSpawn()) round.set(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get(),true);
        MagazineChamberState changed=new MagazineChamberState(state.magazine(),c.deleteOnSpawn()||auto?ItemStack.EMPTY:round,state.boltClosed());
        Optional<ItemStack> ejected=Optional.empty();
        if(auto){ if(!c.deleteOnSpawn())ejected=Optional.of(round); var cycled=autoCycle(changed,profile);changed=cycled.state();if(ejected.isEmpty())ejected=cycled.ejected(); }
        ItemStack out=gun.copy();out.set(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get(),changed);
        return new GunAmmoProviderKernel.TakeResult(GunAmmoProviderKernel.Status.READY,out,c.projectile(),!auto&&!c.deleteOnSpawn()?Optional.of(round):Optional.empty(),ejected);
    }
    private static RackResult autoCycle(MagazineChamberState s, GunProfile profile) {
        var result=feed(s,profile,Optional.empty());
        if(!result.state().magazine().isEmpty() && result.state().chamber().isEmpty())
            return new RackResult(new MagazineChamberState(result.state().magazine(),ItemStack.EMPTY,false),result.ejected());
        return result;
    }
}
