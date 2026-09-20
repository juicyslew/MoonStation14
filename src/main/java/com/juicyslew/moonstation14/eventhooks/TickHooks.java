package com.juicyslew.moonstation14.eventhooks;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.effect.EffectContext;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectAttachment;
import com.juicyslew.moonstation14.util.enums.DamageEnum;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.Map;

import static com.juicyslew.moonstation14.util.MapOperations.getTotal;
import static com.juicyslew.moonstation14.util.NetworkingUtils.markDirty;

public class TickHooks {
    private static final Map<String, Float> passiveHealing = Map.of(
            DamageEnum.BLUNT.getId(), -.02f, // Make this a "shared group" passive heal, so people heal brute damage efficiently.
            DamageEnum.PIERCE.getId(), -.02f,
            DamageEnum.SLASH.getId(), -.02f,
            DamageEnum.HEAT.getId(), -.01f
            // Eventually these will use registers.
            // And after that, passive healing will be loaded from a JSON File.
            // At some point next to no actual values should be in this code, only logic.
    );

    // TODO: Implement max reagents processable
    private static final int MAX_REAGENTS_PROCESSABLE = 2;

    public static void onLivingEntityTick(EntityTickEvent.Pre event) {
        ReagentUpdate(event);
        StatusEffectUpdate(event);
    }

    static void ReagentUpdate(EntityTickEvent.Pre event){
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) return; // run server-side only unless needed
        if(!(entity instanceof LivingEntity livingEntity)) return;
        if (livingEntity.tickCount % 20 == 0){ // Metabolize once a second.
            // METABOLIZE
            var reg = ModReagents.getRegistry(entity.level());
            ReagentAttachment reagentContainer = entity.getData(ModDataAttachments.REAGENT.get());
            if (reagentContainer.isEmpty()){
                return;
            }
            var reagentEntries = new HashMap<>(reagentContainer.getMap()); // Gotta copy so I can iterate while mutating.

            for (var reagentKey : reagentEntries.keySet()) {
                var reagent = reg.get(reagentKey);
                if (reagent == null) continue;

                reagent.metabolisms().forEach((metabolism, metabolismData) -> {
                    // Handle the effects
                    if (metabolismData.rate() == 0){
                        return;
                    }
                    float scale = Math.min(reagentEntries.get(reagentKey) / metabolismData.rate(), 1f);

                    // TODO: Add organs? could abstract this away for MVP maybe.
                    metabolismData.Digest(livingEntity); // DIGEST! (moves reagent from stomach to bloodstream (although rn, stomach and bloodstream are a shared container, so just moves reagents from bloodstream to bloodstream lol.

                    metabolismData.effects().forEach(effect -> {
                        EffectContext effectContext = new EffectContext.Builder(livingEntity).scale(scale).build();
                        if (effect.shouldApply(effectContext)) {
                            effect.apply(effectContext);
                        }
                    });
                    reagentContainer.specificRemove(reagentKey, metabolismData.rate());
                });
            }

            var damageMap = entity.getData(ModDataAttachments.DAMAGE.get());
            livingEntity.setHealth(20f - getTotal(damageMap.getMap())/5f);

            // Allegedly, I can update the data attachments without re-setting them, hence no need for these.
            markDirty(livingEntity, ModDataAttachments.DAMAGE);
            MS14Provider.update(livingEntity, MS14Bridges.REAGENT, reagentContainer);
        }
    }

    static void StatusEffectUpdate(EntityTickEvent.Pre event){
        int statusEffectUpdateWindow = 20; // IN TICKS.
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) return; // run server-side only unless needed
        if(!(entity instanceof LivingEntity livingEntity)) return;
        if (livingEntity.tickCount % statusEffectUpdateWindow == 0){
            var reg = ModStatusEffects.getRegistry(entity.level());
            StatusEffectAttachment statusEffectContainer = entity.getData(ModDataAttachments.STATUS_EFFECT.get());
            var statusEffectEntries = new HashMap<>(statusEffectContainer.getMap()); // Gotta copy so I can iterate while mutating.

            for (var statusEffectKey : statusEffectEntries.keySet()) {
                var statusEffect = reg.get(statusEffectKey);
                if (statusEffect == null) continue;

                // decrement
                float EffectTimeRemaining = Math.max(0f, statusEffectEntries.getOrDefault(statusEffectKey, 0f) - statusEffectUpdateWindow / 20f); // do math in seconds
                statusEffectEntries.put(statusEffectKey, EffectTimeRemaining);
            }

            // Allegedly, I can update the data attachments without re-setting them, hence no need for these.
            MS14Provider.update(livingEntity, MS14Bridges.STATUS_EFFECT, statusEffectContainer);
        }
    }
}
