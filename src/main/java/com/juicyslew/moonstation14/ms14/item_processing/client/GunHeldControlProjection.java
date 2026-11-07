package com.juicyslew.moonstation14.ms14.item_processing.client;

import com.juicyslew.moonstation14.ms14.combat.GunControlAvailability;
import com.juicyslew.moonstation14.ms14.combat.GunProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Client adaptation of the shared source-agnostic gun control rows. */
public final class GunHeldControlProjection {
    private GunHeldControlProjection() { }
    public static java.util.List<BodyTargetActionClient.HeldRow> rows(GunProfile profile,ItemStack stack){
        return GunControlAvailability.rows(profile,stack,GunControlAvailability.SourceKind.HELD).stream()
                .map(r->new BodyTargetActionClient.HeldRow(r.action(),Component.translatable(r.labelKey()),r.enabled(),
                        r.reasonKey()==null?null:Component.translatable(r.reasonKey()))).toList();
    }
}
