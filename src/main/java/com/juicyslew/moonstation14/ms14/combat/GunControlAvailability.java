package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest;
import net.minecraft.world.item.ItemStack;

/** Shared value-only menu rows for an already resolved gun source. */
public final class GunControlAvailability {
    public enum SourceKind { HELD, WORLD }
    public record Row(BodyHandActionRequest.Action action, String labelKey, boolean enabled, String reasonKey) { }
    private GunControlAvailability() { }
    private static Row row(BodyHandActionRequest.Action action,String label,boolean enabled,String reason) {
        return new Row(action,"moonstation14.gun.action."+label,enabled,
                reason==null?null:"moonstation14.gun.action.reason."+reason);
    }
    public static java.util.List<Row> rows(GunProfile profile, ItemStack stack, SourceKind source) {
        if(profile==null||stack==null||stack.isEmpty()||source==null)return java.util.List.of();
        var rows=new java.util.ArrayList<Row>();
        if(source==SourceKind.HELD&&profile.wieldable().isPresent())rows.add(row(BodyHandActionRequest.Action.GUN_TOGGLE_WIELD,"toggle_wield",true,null));
        if(profile.gun().availableModes().size()>1)rows.add(row(BodyHandActionRequest.Action.GUN_CYCLE_FIRE_MODE,"cycle_fire_mode",true,null));
        if(profile.magazineChamberProvider().isPresent()){
            var state=stack.get(ModDataComponents.MAGAZINE_CHAMBER_STATE.get());boolean loaded=state!=null&&!state.magazine().isEmpty();
            rows.add(row(BodyHandActionRequest.Action.GUN_EJECT_MAGAZINE,"eject_magazine",loaded,loaded?null:"empty"));
            rows.add(row(BodyHandActionRequest.Action.GUN_TOGGLE_BOLT,state!=null&&state.boltClosed()?"open_bolt":"close_bolt",true,null));
        }
        if(profile.cellSlot().isPresent()){
            var state=stack.get(ModDataComponents.POWER_CELL_SLOT_STATE.get());boolean loaded=state!=null&&state.cell().filter(c->!c.isEmpty()).isPresent();
            rows.add(row(BodyHandActionRequest.Action.GUN_EJECT_CELL,"eject_cell",loaded,loaded?null:"empty"));
        }
        if(profile.revolverAmmoProvider().isPresent()){
            var state=stack.get(ModDataComponents.REVOLVER_STATE.get());var revolver=state==null?RevolverProviderKernel.initialize(profile):state;
            boolean occupied=revolver.chambers().stream().anyMatch(c->c.virtualLive()||c.physical().isPresent()||c.spentCasing().isPresent());
            rows.add(row(BodyHandActionRequest.Action.GUN_EMPTY_CYLINDER,"empty_cylinder",occupied,occupied?null:"empty"));
            rows.add(row(BodyHandActionRequest.Action.GUN_SPIN_CYLINDER,"spin_cylinder",true,null));
        }
        rows.addAll(ordinaryUseRows(profile,source));
        return java.util.List.copyOf(rows);
    }
    /** Ordinary-use actions shared across held/world source projections. */
    public static java.util.List<Row> ordinaryUseRows(GunProfile profile,SourceKind source){
        if(profile==null||source==null)return java.util.List.of();var rows=new java.util.ArrayList<Row>();
        if(profile.ballisticProvider().isPresent())rows.add(row(BodyHandActionRequest.Action.GUN_CYCLE_ACTION,"cycle_action",true,null));
        if(profile.batteryFireModes().filter(modes->modes.size()>1).isPresent())rows.add(row(BodyHandActionRequest.Action.GUN_CYCLE_BATTERY_MODE,"cycle_battery_mode",true,null));
        return java.util.List.copyOf(rows);
    }
}
