package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;

import java.util.Map;
import java.util.Optional;

/** Read-only projection of the source gun's ammo counter, suitable for a client HUD. */
public final class GunAmmoCounterProjection {
    private GunAmmoCounterProjection() {}

    public enum Chamber { LIVE, SPENT, EMPTY }
    public enum Kind { BALLISTIC, MAGAZINE, REVOLVER, BATTERY, CELL_CHARGE }
    public record Result(boolean visible, int count, int capacity, String text,
                         java.util.List<Chamber> chambers, int currentIndex, boolean noMagazine, Kind kind) {
        public Result { chambers = java.util.List.copyOf(chambers); }
        static Result absent() { return new Result(false, 0, 0, "", java.util.List.of(), -1, false, null); }
    }

    /** Catalogs are injected by the caller; invalid or mixed provider composition is never guessed. */
    public static Result project(ItemStack item, GunProfile profile,
            Map<ResourceLocation, AmmoContainerProfile> containers,
            Map<ResourceLocation, BatteryProfile> batteries) {
        return project(item, profile, null, containers, batteries);
    }

    public static Result project(ItemStack item, GunProfile profile, GunRuntimeState runtime,
            Map<ResourceLocation, AmmoContainerProfile> containers,
            Map<ResourceLocation, BatteryProfile> batteries) {
        if (item == null || item.isEmpty()) return Result.absent();
        if (profile == null) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.getItem());
            AmmoContainerProfile container = containers.get(id);
            if (container == null) return Result.absent();
            try {
                ItemStack owned = new GunItemFactory(new PrototypeCatalog<>(Map.of()),
                        new PrototypeCatalog<>(containers), new PrototypeCatalog<>(batteries)).initializeOwnedCopy(item);
                if (owned.isEmpty()) return Result.absent();
                var state = owned.get(ModDataComponents.AMMO_CONTAINER_STATE.get());
                int count = state == null ? 0 : state.rounds().stream().mapToInt(ItemStack::getCount).sum();
                return new Result(true, count, container.capacity(), count+"/"+container.capacity(),
                        java.util.List.of(), -1, false, Kind.MAGAZINE);
            } catch (IllegalArgumentException invalidComposition) { return Result.absent(); }
        }
        if (!profile.ammoCounter()) return Result.absent();
        ItemStack owned;
        try {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.getItem());
            owned = new GunItemFactory(new PrototypeCatalog<>(Map.of(id, profile)),
                    new PrototypeCatalog<>(containers), new PrototypeCatalog<>(batteries)).initializeOwnedCopy(item);
        } catch (IllegalArgumentException invalidComposition) {
            return Result.absent();
        }
        if (owned.isEmpty()) return Result.absent();
        if (runtime == null) runtime = owned.get(ModDataComponents.GUN_RUNTIME_STATE.get());
        item = owned;
        int providers = (profile.revolverAmmoProvider().isPresent()?1:0)
                +(profile.magazineChamberProvider().isPresent()?1:0)
                +(profile.ballisticProvider().isPresent()?1:0)+(profile.batteryAmmoProvider().isPresent()?1:0);
        if (providers != 1) return Result.absent();
        if (profile.ballisticProvider().isPresent()) {
            var p=profile.ballisticProvider().orElseThrow(); var s=item.get(ModDataComponents.BALLISTIC_STATE.get());
            int count=s==null?p.capacity():s.count();
            return new Result(true,count,p.capacity(),count+"/"+p.capacity(),java.util.List.of(),-1,false,Kind.BALLISTIC);
        }
        if (profile.revolverAmmoProvider().isPresent()) {
            var s=item.get(ModDataComponents.REVOLVER_STATE.get());
            var presentation=RevolverChamberPresentation.project(profile,item,s);
            if(presentation.isEmpty()) return Result.absent();
            var projection=presentation.orElseThrow();
            var chambers=projection.chambers().stream().map(c -> switch(c.status()) {
                case LOADED -> Chamber.LIVE;
                case SPENT -> Chamber.SPENT;
                case MISSING -> Chamber.EMPTY;
            }).toList();
            long live=chambers.stream().filter(c->c==Chamber.LIVE).count();
            return new Result(true,(int)live,chambers.size(),live+"/"+chambers.size(),chambers,projection.currentIndex(),false,Kind.REVOLVER);
        }
        if (profile.magazineChamberProvider().isPresent()) {
            var p=profile.magazineChamberProvider().orElseThrow(); var s=item.get(ModDataComponents.MAGAZINE_CHAMBER_STATE.get());
            if(s==null)s=MagazineChamberState.empty(p.boltClosed());
            ItemStack mag=s.magazine(); int count=0,capacity=0;
             if(!mag.isEmpty()) {
                 var cp=containers.get(GunAmmoProviderKernel.itemId(mag)); if(cp==null)return Result.absent();
                 // The gun copy initializer recursively initializes a genuinely fresh nested
                 // magazine from its resolved container profile. Existing state is preserved,
                 // including an initialized empty/depleted magazine.
                 var initializedMag = new GunItemFactory(new PrototypeCatalog<>(Map.of()),
                         new PrototypeCatalog<>(containers), new PrototypeCatalog<>(batteries)).initializeOwnedCopy(mag);
                 var cs=initializedMag.get(ModDataComponents.AMMO_CONTAINER_STATE.get()); capacity=cp.capacity();
                 count=cs==null?0:cs.rounds().stream().mapToInt(ItemStack::getCount).sum();
             }
            boolean noMagazine=mag.isEmpty();
            Chamber chamber=s.chamber().isEmpty()?Chamber.EMPTY:Boolean.TRUE.equals(s.chamber().get(ModDataComponents.CARTRIDGE_SPENT.get()))?Chamber.SPENT:Chamber.LIVE;
            return new Result(true,count,capacity,count+"/"+capacity,java.util.List.of(chamber),-1,noMagazine,Kind.MAGAZINE);
        }
        var p=profile.batteryAmmoProvider().orElseThrow();
        float cost=p.cost();
        if(profile.batteryFireModes().isPresent()) {
            int selected=GunModePolicy.selectedBatteryMode(runtime,profile);
            cost=profile.batteryFireModes().orElseThrow().get(selected).cost();
        }
         ItemStack power=item; boolean missingCell=false; BatteryProfile bp=null; if(profile.cellSlot().isPresent()) { var slot=item.get(ModDataComponents.POWER_CELL_SLOT_STATE.get()); if(slot==null||slot.cell().isEmpty()) { missingCell=true; var starting=profile.cellSlot().orElseThrow().startingCell(); if(starting.isEmpty())return Result.absent(); bp=batteries.get(starting.orElseThrow()); } else power=slot.cell().orElseThrow(); }
         if(bp==null)bp=batteries.get(GunAmmoProviderKernel.itemId(power)); if(bp==null)return Result.absent();
         var state=power.get(ModDataComponents.BATTERY_STATE.get()); double charge=missingCell?0:state==null?bp.startingCharge():state.charge();
        int count=(int)Math.floor(charge/cost), capacity=(int)Math.floor(bp.capacity()/cost);
        return new Result(true,count,capacity,"x"+String.format(java.util.Locale.ROOT,"%02d",count),java.util.List.of(),-1,false,Kind.BATTERY);
    }
}
