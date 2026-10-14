package com.juicyslew.moonstation14.ms14.mobs;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.attachment.DamageData;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.juicyslew.moonstation14.MoonStation14;

/** Resolved-prototype medical-state authority and compatibility health projection. */
public final class MobStateSystem {
    private MobStateSystem() { }

    public static MobStatePolicy policy(LivingEntity body) {
        return ActiveCharacterPolicy.resolveActor(body).flatMap(MobStatePolicy::from).orElse(null);
    }

    public static boolean ensureProjection(LivingEntity body) {
        if (body == null || body.level().isClientSide()) return false;
        MobStatePolicy policy = policy(body);
        if (policy == null) return false;
        double dead = policy.thresholds().thresholds().entrySet().stream()
                .filter(e -> e.getValue() == MobStateComponent.State.DEAD)
                .mapToDouble(java.util.Map.Entry::getKey).min().orElse(Double.NaN);
        if (!Double.isFinite(dead) || dead <= 0) return false;
        var attribute = body.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (attribute == null) return false;
        MobStateAttachment saved = body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        DamageData ledger = body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        boolean hasLedger = ledger != null && !ledger.isEmpty();
        if (!hasLedger && saved == null && (body.getHealth() <= 0 || body.isDeadOrDying())) {
            body.setData(ModDataAttachments.MOB_STATE.get(), new MobStateAttachment(new MobStateData(MobStateComponent.State.DEAD, 0)));
            logTransition(body, MobStateComponent.State.ALIVE, MobStateComponent.State.DEAD, 0);
            return true;
        }
        // Import old vanilla deficit while its original maximum is still intact.
        if (!hasLedger && saved == null) com.juicyslew.moonstation14.ms14.damage.DamageSystem.ensureBaseline(body);
        ledger = body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        double total = ledger == null ? 0 : ledger.getMap().values().stream().mapToDouble(Float::doubleValue).sum();
        boolean savedDead = saved != null && saved.data().state() == MobStateComponent.State.DEAD;
        var result = MobNativeHealthProjection.project(dead, total,
                policy.states().allowedStates().contains(MobStateComponent.State.DEAD), savedDead,
                body.getHealth() <= 0 || body.isDeadOrDying(), ledger != null && !ledger.isEmpty());
        attribute.setBaseValue(result.maxHealth());
        body.setHealth((float) result.health());
        return true;
    }

    public static void reconcile(LivingEntity body) {
        if (body == null || body.level().isClientSide()) return;
        MobStatePolicy policy = policy(body);
        if (policy == null) return;
        MobStateAttachment attachment = body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        MobStateData previous = attachment == null ? MobStateData.DEFAULT : attachment.data();
        DamageData damage = body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        double total = damage == null ? 0 : damage.getMap().values().stream().mapToDouble(Float::doubleValue).sum();
        MobStateReducer.Transition transition = MobStateReducer.damageOrHealing(
                new MobStateReducer.State(previous.state(), previous.revision()), total,
                policy.states(), policy.thresholds());
        if (!transition.changed()) return;
        var next = transition.state();
        if (previous.state() == MobStateComponent.State.DEAD && next.value() != MobStateComponent.State.DEAD)
            resetNativeDeath(body);
        body.setData(ModDataAttachments.MOB_STATE.get(), new MobStateAttachment(new MobStateData(next.value(), next.revision())));
        logTransition(body, previous.state(), next.value(), total);
        NeoForge.EVENT_BUS.post(new MobStateChangedEvent(body, previous.state(), next.value()));
    }

    /** Native death is the authoritative boundary, independent of how much typed damage caused it. */
    @EventBusSubscriber(modid = MoonStation14.MOD_ID)
    public static final class NativeDeathEvents {
        private NativeDeathEvents() { }

        @SubscribeEvent
        public static void onLivingDeath(LivingDeathEvent event) {
            LivingEntity body = event.getEntity();
            if (body.level().isClientSide()) return;
            MobStatePolicy policy = policy(body);
            if (policy == null) return;
            if (!policy.states().allowedStates().contains(MobStateComponent.State.DEAD)) {
                event.setCanceled(true);
                if (body.getHealth() <= 0) body.setHealth(1.0F);
                return;
            }
            setDeadState(body);
        }
    }

    private static void setDeadState(LivingEntity body) {
        MobStateAttachment attachment = body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        MobStateData previous = attachment == null ? MobStateData.DEFAULT : attachment.data();
        if (previous.state() == MobStateComponent.State.DEAD) return;
        MobStateData next = new MobStateData(MobStateComponent.State.DEAD, previous.revision() + 1);
        body.setData(ModDataAttachments.MOB_STATE.get(), new MobStateAttachment(next));
        DamageData damage = body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        double total = damage == null ? 0 : damage.getMap().values().stream().mapToDouble(Float::doubleValue).sum();
        logTransition(body, previous.state(), next.state(), total);
        NeoForge.EVENT_BUS.post(new MobStateChangedEvent(body, previous.state(), next.state()));
    }

    public static boolean explicitRevive(LivingEntity body) {
        if (body == null || body.isRemoved() || body.level().isClientSide()) return false;
        MobStatePolicy policy = policy(body);
        MobStateAttachment attachment = body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        if (policy == null || attachment == null || attachment.data().state() != MobStateComponent.State.DEAD) return false;
        DamageData damage = body.getExistingDataOrNull(ModDataAttachments.DAMAGE.get());
        double total = damage == null ? 0 : damage.getMap().values().stream().mapToDouble(Float::doubleValue).sum();
        MobStateReducer.Transition transition = MobStateReducer.explicitRevive(
                new MobStateReducer.State(attachment.data().state(), attachment.data().revision()), total,
                policy.states(), policy.thresholds());
        if (!transition.changed() || total >= policy.thresholds().thresholds().entrySet().stream()
                .filter(e -> e.getValue() == MobStateComponent.State.DEAD).mapToDouble(java.util.Map.Entry::getKey).min().orElse(0)) return false;
        resetNativeDeath(body);
        body.setData(ModDataAttachments.MOB_STATE.get(), new MobStateAttachment(new MobStateData(transition.state().value(), transition.state().revision())));
        double dead = policy.thresholds().thresholds().entrySet().stream()
                .filter(e -> e.getValue() == MobStateComponent.State.DEAD).mapToDouble(java.util.Map.Entry::getKey).min().orElse(0);
        body.setHealth((float) Math.max(1, body.getMaxHealth() * (1 - total / dead)));
        if (body instanceof com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessEntity characterBody
                && !characterBody.clearConfirmedDeathForRevival()) return false;
        logTransition(body, MobStateComponent.State.DEAD, transition.state().value(), total);
        NeoForge.EVENT_BUS.post(new MobStateChangedEvent(body, MobStateComponent.State.DEAD, transition.state().value()));
        return true;
    }

    private static void logTransition(LivingEntity body, MobStateComponent.State previous,
                                      MobStateComponent.State next, double typedDamage) {
        MoonStation14.LOGGER.info("[medical state] body={} type={} {}->{} typed={} nativeHealth={}/{}",
                body.getUUID(), net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(body.getType()),
                previous, next, typedDamage, body.getHealth(), body.getMaxHealth());
    }

    private static void resetNativeDeath(LivingEntity body) {
        // A medically retained corpse can have exhausted native deathTime; revive must
        // clear that bookkeeping or vanilla's renderer keeps presenting it as a corpse.
        body.hurtTime = 0;
        if (body instanceof com.juicyslew.moonstation14.mixin.LivingEntityDeathStateAccessor access) {
            access.ms14$setDead(false);
            access.ms14$setDeathTime(0);
        }
    }

    public static boolean isAlive(LivingEntity body) { return query(body) == MobStateComponent.State.ALIVE; }
    public static boolean isCritical(LivingEntity body) { return query(body) == MobStateComponent.State.CRITICAL; }
    public static boolean isDead(LivingEntity body) { return query(body) == MobStateComponent.State.DEAD; }
    public static boolean isIncapacitated(LivingEntity body) { return !isAlive(body); }
    private static MobStateComponent.State query(LivingEntity body) {
        if (body == null) return MobStateComponent.State.ALIVE;
        MobStateAttachment data = body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        if (data != null) return data.data().state();
        if (body.isDeadOrDying()) return MobStateComponent.State.DEAD;
        return MobStateComponent.State.ALIVE;
    }
}

