package com.juicyslew.moonstation14.ms14.stripping.client;

import com.juicyslew.moonstation14.ms14.equipment.*;
import com.juicyslew.moonstation14.ms14.stripping.*;
import java.util.Set;
import java.util.stream.Collectors;

/** Read-only target equipment dependency view over the bounded strip projection. */
public final class StripSlotAvailability {
    private StripSlotAvailability() { }

    public static boolean equipmentEnabled(StripViewSnapshot snapshot,InventoryTemplateData template,
            com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<EquipmentItemData> catalog,String slotId) {
        if(snapshot==null||template==null||catalog==null||slotId==null)return false;
        var projection=snapshot.slots().stream().filter(s->s.kind()==StripSlotRef.Kind.EQUIPMENT&&s.id().equals(slotId)).findFirst().orElse(null);
        if(projection==null)return false;
        // Removal remains available for orphaned contents; dependencies govern insertion only.
        if(projection.occupied())return true;
        Set<String> occupied=snapshot.slots().stream().filter(s->s.kind()==StripSlotRef.Kind.EQUIPMENT&&s.occupied())
                .map(StripViewSnapshot.Slot::id).collect(Collectors.toSet());
        EquipmentItemData outer=null;
        if(slotId.equals("suitstorage")) {
            var projectionOuter=snapshot.slots().stream().filter(s->s.kind()==StripSlotRef.Kind.EQUIPMENT
                    &&s.id().equals("outerClothing")&&s.occupied()).findFirst().orElse(null);
            if(projectionOuter!=null&&projectionOuter.item()!=null) {
                var item=projectionOuter.item().stack().getItem();
                var id=item.builtInRegistryHolder().key().location();
                outer=catalog.get(id);
            }
        }
        return dependenciesSatisfied(template.slot(slotId).orElse(null),occupied,outer);
    }

    /** Shared policy adapter used by runtime projection and focused composition tests. */
    public static boolean dependenciesSatisfied(InventoryTemplateData.Slot slot,Set<String> occupied,
                                                EquipmentItemData outerComposition) {
        if (slot != null && slot.whitelistComponents().contains(InventoryTemplateData.SlotComponent.GAS_TANK))
            return occupied != null && slot.dependsOn().stream().allMatch(occupied::contains);
        boolean allows=outerComposition!=null&&outerComposition.allowSuitStorage()
                .map(EquipmentItemData.AllowSuitStorage::enabled).orElse(false);
        return EquipmentFitPolicy.dependenciesSatisfied(slot,occupied,allows);
    }

    public static boolean actionEnabled(StripViewSnapshot snapshot,StripViewSnapshot.Slot projection,
            InventoryTemplateData template,com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog<EquipmentItemData> catalog) {
        if(snapshot==null||projection==null||!snapshot.canAct())return false;
        return projection.kind()==StripSlotRef.Kind.HAND||projection.occupied()
                ||equipmentEnabled(snapshot,template,catalog,projection.id());
    }
}
