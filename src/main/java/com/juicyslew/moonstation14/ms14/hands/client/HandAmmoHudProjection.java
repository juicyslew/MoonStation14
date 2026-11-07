package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.combat.*;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Read-only per-presented-hand ammo HUD projection shared by the live HUD and tests. */
public final class HandAmmoHudProjection {
    public record Entry(int handIndex, String handId, ItemStack stack, GunProfile profile,
                        GunAmmoCounterProjection.Result counter, RevolverChamberPresentation.Projection revolver) { }
    private HandAmmoHudProjection() { }

    public static List<Entry> project(BodyHandStateSnapshot snapshot, GunWeaponResolver.Selection selection,
            ItemStack preferredGunStack, Map<ResourceLocation,AmmoContainerProfile> containers,
            Map<ResourceLocation,BatteryProfile> batteries) {
        if (snapshot==null || snapshot.reason()!=BodyHandStateSnapshot.Reason.OK) return List.of();
        var entries=new ArrayList<Entry>();
        for(int i=0;i<snapshot.hands().size();i++) {
            var hand=snapshot.hands().get(i);
            if(hand.presentation()==null||hand.token()==null||hand.itemId()==null) continue;
            ResourceLocation id=ResourceLocation.tryParse(hand.itemId()); if(id==null) continue;
            ItemStack presented=hand.presentation().stack();
            if(presented.isEmpty()||!BuiltInRegistries.ITEM.containsKey(id)
                    ||!BuiltInRegistries.ITEM.get(id).equals(presented.getItem())) continue;
            boolean bound=selection!=null&&selection.body().equals(snapshot.bodyId())
                    &&selection.activeHand().equals(snapshot.activeHand())&&selection.activeHand().equals(hand.id())
                    &&Objects.equals(hand.token(),selection.itemToken());
            boolean preferredMatches=preferredGunStack!=null&&!preferredGunStack.isEmpty()
                    &&preferredGunStack.getItem().equals(presented.getItem());
            GunProfile profile=bound&&selection.profile().ammoCounter()&&preferredMatches?selection.profile():null;
            ItemStack stack=profile!=null?preferredGunStack:presented;
            if(profile==null&&!containers.containsKey(id)) {
                var charge=BatteryChargeProjection.project(presented,batteries);
                if(charge==null) continue;
                var counter=new GunAmmoCounterProjection.Result(true,charge.percent(),100,charge.text(),List.of(),-1,false,GunAmmoCounterProjection.Kind.CELL_CHARGE);
                entries.add(new Entry(i,hand.id(),presented,null,counter,null));
                continue;
            }
            var counter=GunAmmoCounterProjection.project(stack,profile,containers,batteries);
            if(!counter.visible())continue;
            RevolverChamberPresentation.Projection revolver=null;
            if(counter.kind()==GunAmmoCounterProjection.Kind.REVOLVER&&bound)
                revolver=RevolverChamberPresentation.project(profile,stack,stack.get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get())).orElse(null);
            entries.add(new Entry(i,hand.id(),stack,profile,counter,revolver));
        }
        return List.copyOf(entries);
    }
}
