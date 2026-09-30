package com.juicyslew.moonstation14.ms14.lung;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.organ.*;
import com.juicyslew.moonstation14.ms14.character.components.BodyComponent;
import com.juicyslew.moonstation14.ms14.character.components.RespiratorComponent;
import com.juicyslew.moonstation14.ms14.character.components.RespiratorPolicy;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Server-authoritative prototype-enrolled respiration. No host/species rules are implicit. */
public final class LungSystem {
    public enum EffectAdjustmentResult { APPLIED, SKIPPED_UNSUPPORTED, FAILED }
    private static final long RETRY_TICKS=20;
    private static final Map<LivingEntity,Cache> CACHE=new WeakHashMap<>();
    private record Cache(long checked, PrototypeCatalog<CharacterData> catalog, ResourceLocation host,
                          ResourceLocation identity, Optional<RespiratorPolicy> policy) {}
    private LungSystem() {}

    /** Applies a persisted-state-only saturation adjustment; this never initializes lung state. */
    public static EffectAdjustmentResult oxygenate(LivingEntity entity, double amount) {
        Optional<RespiratorPolicy> policy = effectPolicy(entity);
        if (policy.isEmpty()) return EffectAdjustmentResult.SKIPPED_UNSUPPORTED;
        if (!Double.isFinite(amount)) return EffectAdjustmentResult.FAILED;
        try {
            BodyState body = effectBody(entity, policy.get());
            RespirationState old = body.respiration();
            double adjusted = old.saturation() + amount;
            if (!Double.isFinite(adjusted)) return EffectAdjustmentResult.FAILED;
            double next = Math.max(policy.get().minSaturation(), Math.min(policy.get().maxSaturation(), adjusted));
            if (next != old.saturation()) writeBody(entity, body.updateRespiration(new RespirationState(next, true, old.phase())));
            return EffectAdjustmentResult.APPLIED;
        } catch (RuntimeException invalid) { return EffectAdjustmentResult.FAILED; }
    }

    /** Applies explicit signed gas deltas to the lung inventory, not to room atmosphere. */
    public static EffectAdjustmentResult modifyLungGas(LivingEntity entity, Map<String, Float> ratios,
                                                        double scale) {
        Optional<RespiratorPolicy> policy = effectPolicy(entity);
        if (policy.isEmpty()) return EffectAdjustmentResult.SKIPPED_UNSUPPORTED;
        if (ratios == null || !Double.isFinite(scale)) return EffectAdjustmentResult.FAILED;
        try {
            BodyState body = BodySystem.current(entity).orElseThrow();
            OrganInstance organ = activeLung(body, ModOrgans.catalog(entity.level())).orElse(null);
            if (organ == null) return EffectAdjustmentResult.SKIPPED_UNSUPPORTED;
            OrganData.Lung lung = currentLung(organ, ModOrgans.catalog(entity.level())).orElseThrow();
            body = effectBody(entity, policy.get());
            if (organ.lung().mixture().totalMoles() > lung.maxLungMoles()) return EffectAdjustmentResult.FAILED;
            GasMixture old = organ.lung().mixture();
            GasMixture changed = old;
            for (var entry : ratios.entrySet()) {
                String name = Objects.requireNonNull(entry.getKey());
                GasType gas = GasType.fromId(name);
                if (!gas.id().equals(name)) return EffectAdjustmentResult.FAILED;
                double delta = Objects.requireNonNull(entry.getValue()) * scale;
                if (!Double.isFinite(delta)) return EffectAdjustmentResult.FAILED;
                if (delta < 0d) delta = -Math.min(changed.moles(gas), -delta);
                else delta = Math.min(delta, Math.max(0d, lung.maxLungMoles() - changed.totalMoles()));
                if (delta != 0d) changed = changed.withGasDelta(gas, delta, changed.temperatureKelvin());
                if (!Double.isFinite(changed.totalMoles()) || changed.totalMoles() > lung.maxLungMoles())
                    return EffectAdjustmentResult.FAILED;
            }
            if (!changed.equals(old)) writeBody(entity, body.updateLung(organ.id(), fromGas(changed), ModOrgans.catalog(entity.level())));
            return EffectAdjustmentResult.APPLIED;
        } catch (RuntimeException invalid) { return EffectAdjustmentResult.FAILED; }
    }

    private static Optional<RespiratorPolicy> effectPolicy(LivingEntity entity) {
        // Reagent effects act only on persisted, eligible bodies, independently of world gas simulation.
        if (entity == null || entity.level().isClientSide || !(entity.level() instanceof ServerLevel)
                || BodySystem.current(entity).isEmpty() || !bodyEligible(entity))
            return Optional.empty();
        return resolvePolicy(entity);
    }

    private static BodyState effectBody(LivingEntity entity, RespiratorPolicy policy) {
        BodyState body = BodySystem.current(entity).orElseThrow();
        RespirationState state = body.respiration();
        if (!state.initialized() || state.saturation() < policy.minSaturation()
                || state.saturation() > policy.maxSaturation())
            throw new IllegalArgumentException("invalid persisted body respiration");
        return body;
    }

    private static boolean bodyEligible(LivingEntity entity) {
         return CharacterIdentitySystem.resolveForActor(entity)
                .filter(c -> c.component(BodyComponent.class).isPresent()).isPresent();
    }

    private static void writeBody(LivingEntity entity, BodyState state) {
        entity.setData(ModDataAttachments.BODY.get(), new BodyAttachment(state));
    }

    private static LungOrganState fromGas(GasMixture gas) {
        return LungOrganState.from(LungComponent.from(gas, 0, true));
    }

    /** A saved orphan stays in the body, but cannot take part in current gas exchange. */
    static Optional<OrganInstance> activeLung(BodyState body, PrototypeCatalog<OrganData> catalog) {
        if (body == null || catalog == null) return Optional.empty();
        return body.find(OrganCategory.LUNGS).filter(organ -> {
            OrganData current = catalog.get(organ.prototype());
            return current != null && current.category() == OrganCategory.LUNGS && current.lung().isPresent();
        });
    }

    static Optional<OrganData.Lung> currentLung(OrganInstance organ, PrototypeCatalog<OrganData> catalog) {
        OrganData data = organ == null || catalog == null ? null : catalog.get(organ.prototype());
        return data != null && data.category() == OrganCategory.LUNGS ? data.lung() : Optional.empty();
    }

    public static Optional<RespiratorPolicy> resolvePolicy(LivingEntity entity) {
        return CharacterIdentitySystem.resolveForActor(entity)
                .flatMap(data -> data.component(RespiratorComponent.class).map(RespiratorComponent::policy));
    }
    /** Legacy pure host-only fixture seam; runtime uses the entity-aware server authority. */
    public static Optional<RespiratorPolicy> resolvePolicy(CharacterData data, ResourceLocation identity, ResourceLocation host, Optional<ResourceLocation> owner) {
        if(data==null||identity==null||host==null||owner==null) return Optional.empty();
        if(!data.hostEntityTypes().isEmpty()&&!data.hostEntityTypes().contains(host)) return Optional.empty();
        if(owner.isPresent()&&!owner.get().equals(identity)) return Optional.empty();
        if(owner.isEmpty()&&!data.hostEntityTypes().isEmpty()) return Optional.empty();
        return data.component(RespiratorComponent.class).map(RespiratorComponent::policy);
    }
    public static boolean reconcile(LivingEntity entity) {
        var policy=resolvePolicy(entity); if(policy.isEmpty() || !bodyEligible(entity)) return false;
        try {
            var p=policy.get(); intervalTicks(p.breathIntervalSeconds());
            if (!validPolicy(p) || !BodySystem.reconcile(entity)) return false;
            BodyState body = BodySystem.current(entity).orElseThrow();
            var organ = activeLung(body, ModOrgans.catalog(entity.level()));
            if (organ.isPresent() && organ.get().lung().mixture().totalMoles() >
                    currentLung(organ.get(), ModOrgans.catalog(entity.level())).orElseThrow().maxLungMoles()) return false;
            RespirationState respiration = body.respiration();
            double sat = respiration.initialized() ? Math.min(p.maxSaturation(), respiration.saturation()) : p.initialSaturation();
            if (sat < p.minSaturation() || sat > p.maxSaturation()) return false;
            if (!respiration.initialized() || sat != respiration.saturation())
                writeBody(entity, body.updateRespiration(new RespirationState(sat, true, respiration.phase())));
            return true;
        } catch(RuntimeException invalid) { return false; }
    }
     public static boolean tickIfDue(LivingEntity entity,long gameTime) {
         return tickIfDue(entity,gameTime,AtmosphereService.INSTANCE);
     }
      /** Isolated service seam for enabled world tests; production uses the global service. */
      public static boolean tickIfDue(LivingEntity entity,long gameTime,AtmosphereService atmosphere) {
           Optional<RespiratorPolicy> policy=policyAtCadence(entity,gameTime); if(policy.isEmpty()) return false;
          if (!entity.isAlive() || !validPolicy(policy.get())) return false;
          int interval = intervalTicks(policy.get().breathIntervalSeconds());
          boolean due = isDue(gameTime, entity.getId(), interval);
          // A known but unusable sample must not even trigger late BODY reconciliation.
          // An unknown sample remains unknown; the existing join-equivalent reconciliation
          // below may still run, but it must never turn the missing sample into vacuum.
          Optional<GasMixture> strictSample = Optional.empty();
          if (due && atmosphere != null && atmosphere.isEnabled()) {
              try {
                  strictSample = atmosphere.sample((ServerLevel) entity.level(), BlockPos.containing(entity.getEyePosition()));
                  if (strictSample.isPresent() && safeRequestedMoles(strictSample.get(),
                          policy.get().breathVolumeLiters(), 0d).isEmpty()) return false;
              } catch (RuntimeException invalid) { return false; }
          }
          // A prototype can become available after the spawn/load join hook ran. Reconcile
          // only the attachment here; breathing still requires a due, strict world sample.
          if(!reconcile(entity)) return false;
          if(!due || !entity.isAlive() || atmosphere==null || !atmosphere.isEnabled()) return false;
          BodyState body = BodySystem.current(entity).orElseThrow();
          RespirationState respiration = body.respiration();
            PrototypeCatalog<OrganData> organs = ModOrgans.catalog(entity.level());
            OrganInstance organ = activeLung(body, organs).orElse(null);
           var p=policy.get();
        // Validate and reduce persisted state before the atmosphere's atomic exchange. In
        // particular an uninitialized/migrating attachment and a newly over-cap policy fail shut.
         if(!respiration.initialized() || !validPolicy(p)) return false;
         GasMixture oldGas;
          OrganData.Lung lung = currentLung(organ, organs).orElse(null);
          try { oldGas=organ == null ? GasMixture.vacuum() : organ.lung().mixture(); if((lung != null && oldGas.totalMoles()>lung.maxLungMoles()) || respiration.saturation()>p.maxSaturation()) return false; }
         catch(RuntimeException invalid){return false;}
         LungComponent old = LungComponent.from(oldGas, respiration.saturation(), true, respiration.phase());
         BlockPos eye=BlockPos.containing(entity.getEyePosition());
         // A missing strict sample is unknown, not vacuum. It cannot drain saturation or
         // advance phase, even while incapacitated.
         if(strictSample.isEmpty()) return false;
         GasMixture room = strictSample.get();
         boolean incapacitated=entity.isSleeping();
           GasMixture[] inhaled = new GasMixture[1];
           LungComponent[] validated = new LungComponent[1];
           BodyState[] validatedBody = new BodyState[1];
          Optional<LungComponent> candidate;
            if (organ == null) {
                 double depleted = LungReducer.deplete(old.saturation(), p.saturationLossPerUpdate(), p.minSaturation());
                 candidate = Optional.of(LungComponent.from(oldGas, depleted, true, old.phase()));
            } else if (incapacitated) {
                  candidate = advance(old,new LungRuntimePolicy(p, lung),room,true,(exhaled,requested) -> Optional.empty());
            } else {
               LungRuntimePolicy runtime = new LungRuntimePolicy(p, lung);
              boolean inhale = old.phase() == LungComponent.Phase.INHALING;
              GasMixture exhaled = inhale ? GasMixture.vacuum() : oldGas;
               OptionalDouble request = inhale ? safeRequestedMoles(room, p.breathVolumeLiters(),
                       lung.maxLungMoles()-oldGas.totalMoles()) : OptionalDouble.of(0d);
               if (request.isEmpty()) return false;
               double requested = request.getAsDouble();
               var exchanged = atmosphere.exchangeBreath((ServerLevel)entity.level(),eye,requested,exhaled, portion -> {
                    Optional<LungComponent> next = advance(old,runtime,room,false,(gas,amount) -> Optional.of(portion));
                  if (next.isEmpty()) return false;
                   try { validatedBody[0] = updatedBody(body, organ, next.get(), entity); }
                   catch (RuntimeException invalid) { return false; }
                   validated[0] = next.get();
                  if (inhale) inhaled[0] = portion;
                  return true;
              });
              candidate = exchanged.isPresent() ? Optional.ofNullable(validated[0]) : Optional.empty();
          }
         if(candidate.isEmpty()) return false;
          LungComponent result = candidate.get();
          BodyState next;
          try { next = incapacitated || organ == null ? updatedBody(body, organ, result, entity) : validatedBody[0]; }
          catch (RuntimeException invalid) { return false; }
          if (next == null) return false;
          writeBody(entity, next);
         if (old.phase() == LungComponent.Phase.INHALING && !incapacitated && inhaled[0] != null) {
              LungToxicity.perInhale(inhaled[0], lung.toxicGasDamagePerMole(), lung.toxicGasDamageCapPerInhale())
                     .filter(damage -> !damage.isEmpty())
                     .ifPresent(damage -> DamageSystem.applyHealthChange(entity, damage, 1f, false));
         }
        if(candidate.get().saturation()<p.suffocationThreshold()) {
            if(p.suffocationDamagePerUpdate()>0) DamageSystem.applyHealthChange(entity,Map.of(DamageKeys.ASPHYXIATION,(float)p.suffocationDamagePerUpdate()),1f,p.suffocationIgnoreResistances());
        } else if(p.suffocationRecoveryPerUpdate()>0) {
            DamageSystem.applyHealthChange(entity,Map.of(DamageKeys.ASPHYXIATION,(float)-p.suffocationRecoveryPerUpdate()),1f,p.suffocationIgnoreResistances());
        }
        return true;
     }

     private static BodyState updatedBody(BodyState body, OrganInstance organ, LungComponent result, LivingEntity entity) {
         BodyState next = body.updateRespiration(RespirationState.from(result));
         return organ == null ? next : next.updateLung(organ.id(), LungOrganState.from(result), ModOrgans.catalog(entity.level()));
     }

     /** One due update, after an authoritative sample. Callback commits one directional exchange. */
       static Optional<LungComponent> advance(LungComponent current, LungRuntimePolicy policy, GasMixture room, boolean incapacitated,
             java.util.function.BiFunction<GasMixture,Double,Optional<GasMixture>> exchange) {
         if(current==null || policy==null || room==null || exchange==null || !current.initialized() || !validPolicy(policy)) return Optional.empty();
        try {
            GasMixture old=current.mixture();
             if(old.totalMoles()>policy.maxLungMoles() || current.saturation()>policy.maxSaturation()
                     || current.saturation()<policy.minSaturation()) return Optional.empty();
              double depleted=LungReducer.deplete(current.saturation(),policy.saturationLossPerUpdate(),policy.minSaturation());
             if(incapacitated) return Optional.of(LungComponent.from(old,depleted,true,current.phase()));
             if(current.phase()==LungComponent.Phase.INHALING) {
                  double requested=requestedMoles(room,policy.breathVolumeLiters(),policy.maxLungMoles()-old.totalMoles());
                 Optional<GasMixture> response=exchange.apply(GasMixture.vacuum(),requested);
                 if(response==null || response.isEmpty()) return Optional.empty();
                 GasMixture inhaled=response.get();
                  // Proportional species scaling can sum a few ulps above the request.
                  // Do not reject an otherwise conserving room exchange for that rounding.
                  if(!Double.isFinite(inhaled.totalMoles())
                          || inhaled.totalMoles()>requested+4*Math.ulp(requested)
                         || !Double.isFinite(inhaled.thermalEnergy())) return Optional.empty();
                 GasMixture combined=combine(old,inhaled);
                 // Metabolism is local to the lung store; nothing is released to the room until exhale.
                  var reduced=LungReducer.uptake(combined,depleted,policy.breathMolesToSaturationMultiplier(),policy.maxSaturation());
                 return Optional.of(LungComponent.from(reduced.exhaled(),reduced.saturation(),true,LungComponent.Phase.EXHALING));
             }
             if(!Double.isFinite(old.thermalEnergy())) return Optional.empty();
             Optional<GasMixture> response=exchange.apply(old,0d);
             if(response==null || response.isEmpty() || response.get().totalMoles()!=0) return Optional.empty();
             return Optional.of(LungComponent.from(GasMixture.vacuum(),depleted,true,LungComponent.Phase.INHALING));
         } catch(RuntimeException invalid) { return Optional.empty(); }
     }
     private static GasMixture combine(GasMixture a,GasMixture b) {
         GasMixture mixed=a;
         for(var entry:b.gasMoles().entrySet()) mixed=mixed.withGasDelta(entry.getKey(),entry.getValue(),b.temperatureKelvin());
         return mixed;
     }

     /** Cell volume is 1 m³; use the strict sample's actual pressure and temperature. */
     static double requestedMoles(GasMixture room,double liters,double capacity) {
         if(room==null || !Double.isFinite(liters) || liters<=0 || !Double.isFinite(capacity) || capacity<0)
             throw new IllegalArgumentException("invalid breath request");
         if(room.totalMoles()==0) return 0;
         double temperature=room.temperatureKelvin();
         if(temperature<=0) throw new IllegalArgumentException("nonzero gas at zero kelvin");
         double pressurePa=room.pressureKpa(1d)*1000d;
         double moles=(pressurePa/(8.31446261815324d*temperature))*(liters/1000d);
         if(!Double.isFinite(moles)) throw new IllegalArgumentException("non-finite breath request");
         return Math.min(capacity,moles);
     }
     /** Invalid known gas is not a vacuum breath and must never reach exchange. */
     static OptionalDouble safeRequestedMoles(GasMixture room, double liters, double capacity) {
         try { return OptionalDouble.of(requestedMoles(room, liters, capacity)); }
         catch (RuntimeException invalid) { return OptionalDouble.empty(); }
     }
      private static boolean validPolicy(RespiratorPolicy p) {
         return Double.isFinite(p.breathVolumeLiters()) && p.breathVolumeLiters()>0
                 && Double.isFinite(p.maxSaturation()) && p.maxSaturation()>0
                 && Double.isFinite(p.minSaturation()) && p.minSaturation()>=-2 && p.minSaturation()<=0
                && Double.isFinite(p.saturationLossPerUpdate()) && p.saturationLossPerUpdate()>=0;
     }
     private static boolean validPolicy(LungRuntimePolicy p) {
         return validPolicy(p.respirator()) && p.lung() != null;
     }
     private static Optional<RespiratorPolicy> policyAtCadence(LivingEntity entity,long time) {
        if(entity==null||entity.level().isClientSide||!(entity.level() instanceof ServerLevel level)) return Optional.empty();
        ResourceLocation host=BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()); PrototypeCatalog<CharacterData> catalog=ModCharacters.catalog(level);
        var attachment=entity.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get()); ResourceLocation id=attachment!=null&&attachment.isBound()?attachment.characterId():null;
         synchronized(CACHE) { Cache c=CACHE.get(entity); if(c!=null&&c.catalog()==catalog&&c.host().equals(host)&&Objects.equals(c.identity(),id)&&time>=c.checked()&&time-c.checked()<RETRY_TICKS)
             return CharacterIdentitySystem.resolveForActor(entity, catalog)
                     .flatMap(data -> data.component(RespiratorComponent.class).map(RespiratorComponent::policy)); }
        if(id==null&&attachment==null) { CharacterIdentitySystem.enrollSupportedActor(entity,level); catalog=ModCharacters.catalog(level); }
        attachment=entity.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get()); id=attachment!=null&&attachment.isBound()?attachment.characterId():null;
          Optional<RespiratorPolicy> policy=CharacterIdentitySystem.resolveForActor(entity,catalog)
                  .flatMap(data -> data.component(RespiratorComponent.class).map(RespiratorComponent::policy));
         // Policy lookup does not initialize lungs; tickIfDue reconciles the attachment
         // separately from the strict-sample-gated physical update.
        synchronized(CACHE){CACHE.put(entity,new Cache(time,catalog,host,id,policy));} return policy;
    }
    public static int intervalTicks(double seconds){long t=Math.round(seconds*20); if(t<1||t>Integer.MAX_VALUE)throw new IllegalArgumentException("lung interval out of range"); return (int)t;}
    public static boolean isDue(long time,int entityId,int interval){return interval>0&&Math.floorMod(Math.floorMod(time,(long)interval)+entityId,interval)==0;}
}
