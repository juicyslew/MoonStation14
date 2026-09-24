package com.juicyslew.moonstation14.eventhooks;

import com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem;
import com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait;
import com.juicyslew.moonstation14.ms14.status_effect.LivingEntityStatusEffectLifecycle;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectAttachment;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectClearReport;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

public class ModEventHooks {

    public static void register(IEventBus eventBus){
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, DamageHooks::onLivingDamagePost);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, DamageHooks::onLivingIncomingDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, DamageHooks::onLivingHeal);
        NeoForge.EVENT_BUS.addListener(TickHooks::onLivingEntityTick);
        NeoForge.EVENT_BUS.addListener(ModEventHooks::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(ModEventHooks::onPlayerClone);
    }

    private static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel && event.getEntity() instanceof LivingEntity livingEntity) {
            com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.initializeIfEligible(livingEntity,
                    (ServerLevel) event.getLevel());
            com.juicyslew.moonstation14.ms14.hunger.HungerSystem.initializeIfEligible(livingEntity,
                    (ServerLevel) event.getLevel());
            com.juicyslew.moonstation14.ms14.hunger.HungerSystem.reconcile(livingEntity,
                    (ServerLevel) event.getLevel());
            com.juicyslew.moonstation14.ms14.thirst.ThirstSystem.reconcile(livingEntity,
                    (ServerLevel) event.getLevel());
            com.juicyslew.moonstation14.ms14.damage.DamageSystem.reconcile(livingEntity);
            EntityActivitySystem.reconcile(livingEntity);
        }
    }

    /** Clears custom status state only for a death-created player clone. */
    private static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath() && event.getEntity() instanceof ServerPlayer player) {
            applyPlayerDeathStatusPolicy(player);
        }
    }

    /**
     * Narrow policy seam for server-side death/respawn tests.  NeoForge's
     * {@link PlayerEvent.Clone} is the production entry point; this method
     * contains no ghost or existing-entity respawn behavior.
     */
    public static StatusEffectClearReport applyPlayerDeathStatusPolicy(ServerPlayer player) {
        if (!(player instanceof IStatusEffectTrait statusEffectTrait)) {
            return new StatusEffectClearReport(List.of(), false);
        }
        return StatusEffectSystem.clearAll(statusEffectTrait.toHandleSelf(), player.level());
    }

    /**
     * Test/harness seam for a server player whose attachment synchronization
     * transport is unavailable. It still uses the player lifecycle and
     * performs the same single derived-activity reconciliation as the event
     * path, but leaves persistence of the detached fixture to the caller.
     */
    public static StatusEffectClearReport applyPlayerDeathStatusPolicy(
            ServerPlayer player, StatusEffectAttachment detachedStatus) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return new StatusEffectClearReport(List.of(), false);
        }
        StatusEffectClearReport report = StatusEffectSystem.clearAll(
                detachedStatus,
                PrototypeRuntime.serverStatusEffects(),
                new LivingEntityStatusEffectLifecycle(player, serverLevel));
        EntityActivitySystem.reconcile(player);
        return report;
    }
}
