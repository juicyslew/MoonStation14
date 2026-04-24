package com.juicyslew.moonstation14.eventhooks;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.attachment.DamageData;
import com.juicyslew.moonstation14.enums.DamageEnum;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import static com.juicyslew.moonstation14.util.MapOperations.getTotal;
import static com.juicyslew.moonstation14.util.NetworkingUtils.markDirty;


public class DamageHooks {
    // Could've used "@SubscribeEvent" here, but keeping things explicit helps me be intentional about understanding how everything
    public static void onLivingEntityHurt(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        DamageSource src = event.getSource();

        // Ignore armor / shield. Will have to reimplement.
        float original_damage = event.getOriginalDamage();

        DamageData damage = target.getData(ModDataAttachments.DAMAGE.get());

        // Apply Damage
        // TODO: Translate Vanilla DamageSource into new Damage Type.
        // Start with everything just being blunt.
        damage.specificAdd(DamageEnum.BLUNT.getId(), original_damage * 5f, 1000f); // 100 == 1 damage.

        // Cancel vanilla application so it doesn't subtract vanilla health
        event.setNewDamage(0f);
        target.setHealth(20f - getTotal(damage.getMap())/5f);
        markDirty(target, ModDataAttachments.DAMAGE);
    }
}
