package com.juicyslew.moonstation14.eventhooks;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;


public class DamageHooks {
    private DamageHooks() {
    }

    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        DamageSystem.ensureBaseline(event.getEntity());
    }

    /** Mirror only the final damage that vanilla actually applied, exactly once. */
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        DamageSystem.observePost(event.getEntity(), event.getSource(), event.getNewDamage());
    }

    /** Replace vanilla generic healing with authoritative even typed healing. */
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        DamageSystem.ensureBaseline(target);
        float acceptedAmount = Math.min(event.getAmount(),
                Math.max(0f, target.getMaxHealth() - target.getHealth()));
        if (target.getExistingDataOrNull(ModDataAttachments.DAMAGE.get()) == null
                || acceptedAmount <= 0f) {
            return;
        }
        event.setCanceled(true);
        DamageSystem.applyVanillaHealing(target, acceptedAmount);
    }
}
