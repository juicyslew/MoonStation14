package com.juicyslew.moonstation14.ms14.loose_objects;

import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.slip.ItemStepContactSystem;

/** Applies the supported target-user damage/delete subset of step triggers. */
public final class ItemStepDamageSystem {
    private ItemStepDamageSystem() { }

    public static void onContact(ItemStepContactSystem.ContactContext context) {
        LooseObjectProfile.StepDamageComposition damage = context.sourceProfile().stepDamage();
        if (damage == null) return;
        DamageSystem.applyHealthChange(context.target(), damage.damage(), 1f, false, DamageSystem.Cause.COMBAT);
        if (damage.deleteSource() && !context.source().isRemoved()) context.source().discard();
    }
}
