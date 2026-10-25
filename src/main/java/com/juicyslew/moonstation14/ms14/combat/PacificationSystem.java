package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectBehavior;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.entity.LivingEntity;

/** Pacification is an attack-only projection of the resolved active status definition. */
@EventBusSubscriber(modid = MoonStation14.MOD_ID)
public final class PacificationSystem {
    private PacificationSystem() { }
    public static boolean isPacified(LivingEntity entity) {
        return entity != null && StatusEffectSystem.hasActiveBehavior(entity, StatusEffectBehavior.PACIFIED);
    }
    public static boolean mayFire(LivingEntity entity, GunProfile gun) {
        return mayFire(isPacified(entity), gun);
    }
    public static boolean mayFire(boolean pacified, GunProfile gun) {
        return !pacified || gun != null && gun.pacifismAllowed();
    }
    public static boolean mayMelee(boolean pacified, boolean disarm, net.minecraft.world.entity.Entity target,
                                   java.util.Map<String, Float> rawDamage) {
        if (!pacified || disarm || target == null) return true;
        double total = rawDamage == null ? 0d : rawDamage.values().stream().mapToDouble(Float::doubleValue).sum();
        if (total == 0f) return true;
        return !(target instanceof LivingEntity);
    }
    public static boolean mayMeleeTarget(LivingEntity attacker, net.minecraft.world.entity.Entity target,
                                         java.util.Map<String, Float> rawDamage) {
        return mayMelee(isPacified(attacker), false, target, rawDamage);
    }
    @SubscribeEvent
    public static void onShotAttempted(GunAttackEvents.ShotAttemptedEvent event) {
        if (event.scope() == GunAttackEvents.Scope.USER
                && !mayFire(event.user(), event.gun().selection().profile())) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void onMeleeAttempt(MeleeAttackEvents.SharedMeleeAttackAttemptEvent event) {
        if (!mayMelee(isPacified(event.attacker()), event.disarm(), event.target().orElse(null),
                event.weaponSelection().weapon().damage())) event.setCanceled(true);
    }
}
