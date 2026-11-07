package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.component.ModDataComponents;

/** Pure staging policy for revolver world-use transitions. */
public final class GunWorldUsePolicy {
    private GunWorldUsePolicy() { }

    public static ItemStack prepareSpin(GunProfile profile, ItemStack source, int index) {
        if (profile == null || source == null || source.isEmpty() || profile.revolverAmmoProvider().isEmpty()) return ItemStack.EMPTY;
        var provider = profile.revolverAmmoProvider().orElseThrow();
        var state = source.get(ModDataComponents.REVOLVER_STATE.get());
        if (state == null) state = RevolverProviderKernel.initialize(profile);
        if (provider.capacity() != state.chambers().size() || index < 0 || index >= provider.capacity()) return ItemStack.EMPTY;
        ItemStack staged = source.copy();
        staged.set(ModDataComponents.REVOLVER_STATE.get(), new RevolverState(index, state.chambers()));
        return staged;
    }

    /** Detached plan for one supported gun control; callers retain ownership/commit policy. */
    public record ControlPlan(ItemStack gun, java.util.List<ItemStack> outputs,
                              GunManipulationAudioPolicy.Operation audioOperation, boolean beforeBoltClosed) {
        public ControlPlan { gun=gun.copy(); outputs=outputs.stream().map(ItemStack::copy).toList(); }
        @Override public ItemStack gun(){return gun.copy();}
        @Override public java.util.List<ItemStack> outputs(){return outputs.stream().map(ItemStack::copy).toList();}
    }

    public static ControlPlan prepareControl(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action action,
            GunProfile profile, ItemStack source, int spinIndex) {
        return prepareControl(action,profile,source,spinIndex,0L);
    }
    public static ControlPlan prepareControl(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action action,
            GunProfile profile, ItemStack source, int spinIndex,long now) {
        if(action==null||profile==null||source==null||source.isEmpty())return null;
        if(action==com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_EMPTY)
            action=com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_EMPTY_CYLINDER;
        else if(action==com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_SPIN)
            action=com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_SPIN_CYLINDER;
        ItemStack staged=GunItemFactory.initializeCopy(source);
        var outputs=new java.util.ArrayList<ItemStack>();
        GunManipulationAudioPolicy.Operation audio;
        boolean beforeBolt=false;
        switch(action){
            case GUN_CYCLE_FIRE_MODE -> {
                var runtime=staged.getOrDefault(ModDataComponents.GUN_RUNTIME_STATE.get(),GunRuntimeState.INITIAL);
                var next=GunModePolicy.cycleFireMode(runtime,profile);if(next==null)return null;
                staged.set(ModDataComponents.GUN_RUNTIME_STATE.get(),next);audio=GunManipulationAudioPolicy.Operation.MODE;
            }
            case GUN_CYCLE_ACTION -> {
                if(profile.ballisticProvider().isEmpty())return null;
                var provider=profile.ballisticProvider().orElseThrow();
                var state=staged.getOrDefault(ModDataComponents.BALLISTIC_STATE.get(),BallisticState.full(provider.capacity(),provider.fillCartridgePrototype()));
                var cycled=BallisticProviderKernel.cycle(state,itemId->new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId).orElseThrow()));
                staged.set(ModDataComponents.BALLISTIC_STATE.get(),cycled.state());
                var runtime=staged.getOrDefault(ModDataComponents.GUN_RUNTIME_STATE.get(),GunRuntimeState.INITIAL);
                var deadline=GunFirePolicy.nextFireTick(now,profile.gun().fireRate());if(deadline.isEmpty())return null;
                staged.set(ModDataComponents.GUN_RUNTIME_STATE.get(),runtime.withFire(Math.max(now+1,deadline.getAsLong()),runtime.lastFireTick(),runtime.currentSpreadDegrees()));
                cycled.round().ifPresent(outputs::add);audio=GunManipulationAudioPolicy.Operation.BALLISTIC_CYCLE;
            }
            case GUN_CYCLE_BATTERY_MODE -> {
                if(profile.batteryFireModes().filter(modes->modes.size()>1).isEmpty())return null;
                var runtime=staged.getOrDefault(ModDataComponents.GUN_RUNTIME_STATE.get(),GunRuntimeState.INITIAL);
                int count=profile.batteryFireModes().orElseThrow().size();
                staged.set(ModDataComponents.GUN_RUNTIME_STATE.get(),runtime.withBatteryModeIndex((GunModePolicy.selectedBatteryMode(runtime,profile)+1)%count));
                audio=GunManipulationAudioPolicy.Operation.MODE;
            }
            case GUN_TOGGLE_BOLT -> {
                if(profile.magazineChamberProvider().isEmpty())return null;
                var state=staged.getOrDefault(ModDataComponents.MAGAZINE_CHAMBER_STATE.get(),MagazineChamberProviderKernel.initialize(profile,
                        itemId->new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId).orElseThrow())));
                beforeBolt=state.boltClosed();var result=MagazineChamberProviderKernel.toggleBolt(state,profile);
                staged.set(ModDataComponents.MAGAZINE_CHAMBER_STATE.get(),result.state());result.ejected().ifPresent(outputs::add);
                audio=GunManipulationAudioPolicy.Operation.BOLT_TOGGLE;
            }
            case GUN_EJECT_MAGAZINE -> {
                if(profile.magazineChamberProvider().isEmpty())return null;
                var state=staged.getOrDefault(ModDataComponents.MAGAZINE_CHAMBER_STATE.get(),MagazineChamberProviderKernel.initialize(profile,
                        itemId->new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId).orElseThrow())));
                var magazine=state.magazine().copy();if(magazine.isEmpty())return null;
                staged.set(ModDataComponents.MAGAZINE_CHAMBER_STATE.get(),MagazineChamberProviderKernel.ejectMagazine(state));outputs.add(magazine);
                audio=GunManipulationAudioPolicy.Operation.EJECT_MAGAZINE;
            }
            case GUN_EJECT_CELL -> {
                if(profile.cellSlot().isEmpty())return null;var result=BatteryAmmoProviderKernel.eject(staged);
                if(!result.accepted()||result.returnedCell().isEmpty())return null;
                staged=result.gun();outputs.add(result.returnedCell().orElseThrow());audio=GunManipulationAudioPolicy.Operation.EJECT_CELL;
            }
            case GUN_EMPTY_CYLINDER -> {
                var result=prepareEmpty(profile,staged);if(result==null||result.outputs().isEmpty())return null;
                staged=result.gun();outputs.addAll(result.outputs());audio=GunManipulationAudioPolicy.Operation.EMPTY_CYLINDER;
            }
            case GUN_SPIN_CYLINDER -> {
                staged=prepareSpin(profile,staged,spinIndex);if(staged.isEmpty())return null;audio=GunManipulationAudioPolicy.Operation.SPIN_CYLINDER;
            }
            default -> { return null; }
        }
        return new ControlPlan(staged,outputs,audio,beforeBolt);
    }

    public enum InsertStatus { ACCEPTED, WRONG_SOURCE, FULL, INVALID_STATE }
    /** Detached insertion result shared by hand/world callers; cue is data, never played here. */
    public record InsertPlan(boolean accepted, ItemStack gun, ItemStack sourceRemainder,
                             InsertStatus status, PositionalSoundCue cue,
                             RevolverProviderKernel.InsertFailure failure) {
        public InsertPlan { gun = gun == null ? ItemStack.EMPTY : gun.copy(); sourceRemainder=sourceRemainder==null?ItemStack.EMPTY:sourceRemainder.copy(); }
        @Override public ItemStack gun() { return gun.copy(); }
        @Override public ItemStack sourceRemainder(){return sourceRemainder.copy();}
    }

    /** Stage a physical cartridge, magazine, cell, or revolver speedloader into a detached gun copy. */
    public static InsertPlan prepareInsert(GunProfile profile, ItemStack targetGun, ItemStack cartridge) {
        return prepareInsert(profile, targetGun, cartridge, ModCartridgeProfiles.serverCatalog().asMap());
    }

    static InsertPlan prepareInsert(GunProfile profile, ItemStack targetGun, ItemStack cartridge,
                                    java.util.Map<net.minecraft.resources.ResourceLocation, CartridgeProfile> catalog) {
        return prepareInsert(profile,targetGun,cartridge,catalog,ModAmmoContainerProfiles.serverCatalog().asMap(),ModBatteryProfiles.serverCatalog().asMap());
    }
    static InsertPlan prepareInsert(GunProfile profile, ItemStack targetGun, ItemStack cartridge,
                                    java.util.Map<net.minecraft.resources.ResourceLocation, CartridgeProfile> catalog,
                                    java.util.Map<net.minecraft.resources.ResourceLocation, AmmoContainerProfile> containers,
                                    java.util.Map<net.minecraft.resources.ResourceLocation, BatteryProfile> batteries) {
        if (targetGun == null || targetGun.isEmpty() || cartridge == null
                || cartridge.isEmpty() || cartridge.getCount() != 1)
            return new InsertPlan(false, targetGun, cartridge, InsertStatus.WRONG_SOURCE, null, RevolverProviderKernel.InsertFailure.UNKNOWN_CARTRIDGE);
        if (profile == null) {
            var targetId=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(targetGun.getItem());
            var container=targetId==null?null:containers.get(targetId);
            var cp=catalog.get(GunAmmoProviderKernel.itemId(cartridge));
            if(container==null || cp==null || cp.tags().stream().noneMatch(container.acceptedTags()::contains))
                return new InsertPlan(false,targetGun,cartridge,InsertStatus.WRONG_SOURCE,null,RevolverProviderKernel.InsertFailure.UNKNOWN_CARTRIDGE);
            ItemStack initialized=initializeContainerCopy(targetGun,containers,batteries);
            var old=initialized.get(ModDataComponents.AMMO_CONTAINER_STATE.get());
            if(old==null)return new InsertPlan(false,targetGun,cartridge,InsertStatus.INVALID_STATE,null,RevolverProviderKernel.InsertFailure.UNKNOWN_CARTRIDGE);
            var state=new BallisticState(old.rounds(),0,container.fillCartridgePrototype());
            var changed=BallisticProviderKernel.insert(state,cartridge.copyWithCount(1),container.capacity());
            if(changed==state)return new InsertPlan(false,targetGun,cartridge,InsertStatus.FULL,null,RevolverProviderKernel.InsertFailure.FULL);
            initialized.set(ModDataComponents.AMMO_CONTAINER_STATE.get(),new AmmoContainerState(changed.rounds()));
            return new InsertPlan(true,initialized,ItemStack.EMPTY,InsertStatus.ACCEPTED,container.insertSound(),null);
        }
        ItemStack initialized=initializeCopy(targetGun,profile,containers,batteries);
        if(profile.magazineChamberProvider().isPresent()) {
            var provider=profile.magazineChamberProvider().orElseThrow();
            var roundProfile=catalog.get(GunAmmoProviderKernel.itemId(cartridge));
            if(roundProfile!=null) {
                var old=initialized.get(ModDataComponents.MAGAZINE_CHAMBER_STATE.get());
                var changed=MagazineChamberProviderKernel.insertChamber(old,cartridge,profile,catalog);
                if(changed.isEmpty()) return new InsertPlan(false,targetGun,cartridge,old!=null&&(!old.chamber().isEmpty()||old.boltClosed())?InsertStatus.INVALID_STATE:InsertStatus.WRONG_SOURCE,null,null);
                initialized.set(ModDataComponents.MAGAZINE_CHAMBER_STATE.get(),changed.orElseThrow());
                 return new InsertPlan(true,initialized,ItemStack.EMPTY,InsertStatus.ACCEPTED,provider.chamberInsertSound().orElse(null),null);
            }
            var container=containers.get(GunAmmoProviderKernel.itemId(cartridge));
            if(container==null || java.util.Collections.disjoint(container.magazineTags(),provider.magazineTags())) return new InsertPlan(false,targetGun,cartridge,InsertStatus.WRONG_SOURCE,null,RevolverProviderKernel.InsertFailure.UNKNOWN_CARTRIDGE);
             // Materialize the accepted source on a detached copy at transfer time. Creative/raw
             // stacks may not carry their typed default state; the factory preserves existing state.
             var materializedMagazine=initializeContainerCopy(cartridge,containers,batteries);
             var old=initialized.get(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get());
             var changed=MagazineChamberProviderKernel.insertMagazine(old,materializedMagazine,profile,containers);
            if(changed==old)return new InsertPlan(false,targetGun,cartridge,InsertStatus.FULL,null,RevolverProviderKernel.InsertFailure.FULL);
            initialized.set(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get(),changed);
            return new InsertPlan(true,initialized,ItemStack.EMPTY,InsertStatus.ACCEPTED,provider.magazineInsertSound().orElse(null),null);
        }
        if(profile.cellSlot().isPresent()) {
            var result=BatteryAmmoProviderKernel.insert(initialized,profile,cartridge,batteries);
            if(!result.accepted())return new InsertPlan(false,targetGun,cartridge,InsertStatus.WRONG_SOURCE,null,RevolverProviderKernel.InsertFailure.UNKNOWN_CARTRIDGE);
            return new InsertPlan(true,result.gun(),ItemStack.EMPTY,InsertStatus.ACCEPTED,profile.cellSlot().orElseThrow().insertSound().orElse(null),null);
        }
        if(profile.ballisticProvider().isPresent()) {
            var provider=profile.ballisticProvider().orElseThrow();
            var cp=catalog.get(GunAmmoProviderKernel.itemId(cartridge));
            if(cp==null || !cp.tags().contains(provider.cartridgeTag()))
                return new InsertPlan(false,targetGun,cartridge,InsertStatus.WRONG_SOURCE,null,RevolverProviderKernel.InsertFailure.UNKNOWN_CARTRIDGE);
            var state=initialized.get(ModDataComponents.BALLISTIC_STATE.get());
            if(state==null)state=BallisticState.full(provider.capacity(),provider.fillCartridgePrototype());
            if(state.count()>=provider.capacity())return new InsertPlan(false,targetGun,cartridge,InsertStatus.FULL,null,RevolverProviderKernel.InsertFailure.FULL);
            var changed=BallisticProviderKernel.insert(state,cartridge.copyWithCount(1),provider.capacity());
            if(changed==state)return new InsertPlan(false,targetGun,cartridge,InsertStatus.FULL,null,RevolverProviderKernel.InsertFailure.FULL);
            initialized.set(ModDataComponents.BALLISTIC_STATE.get(),changed);
            return new InsertPlan(true,initialized,ItemStack.EMPTY,InsertStatus.ACCEPTED,provider.insertSound().orElse(null),null);
        }
        if(profile.revolverAmmoProvider().isPresent()) {
            var provider=profile.revolverAmmoProvider().orElseThrow();
            var container=containers.get(GunAmmoProviderKernel.itemId(cartridge));
            if(container!=null && !java.util.Collections.disjoint(container.magazineTags(),provider.acceptedSpeedloaderTags())) {
                var state=initialized.get(ModDataComponents.REVOLVER_STATE.get());
                if(state==null)state=RevolverProviderKernel.initialize(profile);
                var remainder=initializeContainerCopy(cartridge,containers,batteries);
                var contents=remainder.get(ModDataComponents.AMMO_CONTAINER_STATE.get());
                if(contents==null)return new InsertPlan(false,targetGun,cartridge,InsertStatus.WRONG_SOURCE,null,RevolverProviderKernel.InsertFailure.UNKNOWN_CARTRIDGE);
                var rounds=new java.util.ArrayList<>(contents.rounds());
                RevolverState changed=state;
                int transferred=0;
                for(int i=rounds.size()-1;i>=0;i--) {
                    ItemStack round=rounds.get(i);
                    var cp=catalog.get(GunAmmoProviderKernel.itemId(round));
                    if(round.isEmpty() || cp==null || cp.tags().stream().noneMatch(provider.acceptedCartridgeTags()::contains))break;
                    var insertion=RevolverProviderKernel.insertOne(changed,profile,round.copyWithCount(1),catalog);
                    if(!insertion.inserted())break;
                    changed=insertion.state(); transferred++; rounds.remove(i);
                }
                if(transferred==0)return new InsertPlan(false,targetGun,cartridge,InsertStatus.FULL,null,RevolverProviderKernel.InsertFailure.FULL);
                initialized.set(ModDataComponents.REVOLVER_STATE.get(),changed);
                remainder.set(ModDataComponents.AMMO_CONTAINER_STATE.get(),new AmmoContainerState(rounds));
                return new InsertPlan(true,initialized,remainder,InsertStatus.ACCEPTED,provider.insertSound().orElse(null),null);
            }
        }
        if(profile.revolverAmmoProvider().isEmpty())return new InsertPlan(false,targetGun,cartridge,InsertStatus.WRONG_SOURCE,null,RevolverProviderKernel.InsertFailure.UNKNOWN_CARTRIDGE);
        var provider = profile.revolverAmmoProvider().orElseThrow();
        var state = initialized.get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get());
        if (state == null) state = RevolverProviderKernel.initialize(profile);
        if (state.chambers().size() != provider.capacity())
            return new InsertPlan(false, targetGun, cartridge, InsertStatus.FULL, null, RevolverProviderKernel.InsertFailure.FULL);
        var result = RevolverProviderKernel.insertOne(state, profile, cartridge.copy(), catalog);
        if (!result.inserted()) return new InsertPlan(false, targetGun, cartridge, result.failure()==RevolverProviderKernel.InsertFailure.FULL?InsertStatus.FULL:InsertStatus.WRONG_SOURCE, null, result.failure());
        ItemStack staged = initialized.copy();
        staged.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(), result.state());
        ItemStack remainder=cartridge.copy(); remainder.shrink(1);
        return new InsertPlan(true, staged, remainder, InsertStatus.ACCEPTED, provider.insertSound().orElse(null), null);
    }

    private static ItemStack initializeCopy(ItemStack source,GunProfile profile,
            java.util.Map<net.minecraft.resources.ResourceLocation,AmmoContainerProfile> containers,
            java.util.Map<net.minecraft.resources.ResourceLocation,BatteryProfile> batteries) {
        var guns=java.util.Map.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(source.getItem()),profile);
        return new GunItemFactory(new com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<>(guns),
                new com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<>(containers),
                new com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<>(batteries)).initializeOwnedCopy(source);
    }
    private static ItemStack initializeContainerCopy(ItemStack source,
            java.util.Map<net.minecraft.resources.ResourceLocation,AmmoContainerProfile> containers,
            java.util.Map<net.minecraft.resources.ResourceLocation,BatteryProfile> batteries) {
        return new GunItemFactory(new com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<>(java.util.Map.of()),
                new com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<>(containers),
                new com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<>(batteries)).initializeOwnedCopy(source);
    }

    public record EjectPlan(ItemStack gun, java.util.List<ItemStack> outputs) {
        public EjectPlan { outputs = java.util.List.copyOf(outputs); }
    }

    public static EjectPlan prepareEmpty(GunProfile profile, ItemStack source) {
        if (profile == null || source == null || source.isEmpty() || profile.revolverAmmoProvider().isEmpty()) return null;
        var state = source.get(ModDataComponents.REVOLVER_STATE.get());
        if (state == null) state = RevolverProviderKernel.initialize(profile);
        if (state.chambers().size() != profile.revolverAmmoProvider().orElseThrow().capacity()) return null;
        var ejected = RevolverProviderKernel.ejectAll(state, profile);
        var outputs = new java.util.ArrayList<ItemStack>();
        for (var round : ejected.ejected()) {
            var id = round.physical().map(s -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()))
                    .orElse(round.deferredPrototype().orElse(null));
            ItemStack output = round.physical().map(ItemStack::copy).orElseGet(() -> id == null ? null : GunFireSystem.materializeCartridge(id, round.spent()));
            if (output == null || output.isEmpty()) return null;
            outputs.add(output);
        }
        ItemStack staged = source.copy();
        staged.set(ModDataComponents.REVOLVER_STATE.get(), ejected.state());
        return new EjectPlan(staged, outputs);
    }
}
