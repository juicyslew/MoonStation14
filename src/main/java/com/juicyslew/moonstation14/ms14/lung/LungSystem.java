package com.juicyslew.moonstation14.ms14.lung;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
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
                         ResourceLocation identity, Optional<CharacterData.LungsData> policy) {}
    private LungSystem() {}

    /** Applies a persisted-state-only saturation adjustment; this never initializes lung state. */
    public static EffectAdjustmentResult oxygenate(LivingEntity entity, double amount) {
        Optional<CharacterData.LungsData> policy = effectPolicy(entity);
        if (policy.isEmpty()) return EffectAdjustmentResult.SKIPPED_UNSUPPORTED;
        if (!Double.isFinite(amount)) return EffectAdjustmentResult.FAILED;
        try {
            LungComponent old = effectComponent(entity, policy.get());
            double adjusted = old.saturation() + amount;
            if (!Double.isFinite(adjusted)) return EffectAdjustmentResult.FAILED;
             double next = Math.max(policy.get().minSaturation(), Math.min(policy.get().maxSaturation(), adjusted));
            if (next != old.saturation()) MS14Provider.update(entity, MS14Bridges.LUNG,
                     new LungAttachment(new LungComponent(old.gasMoles(), old.temperatureKelvin(), next, true, old.phase())));
            return EffectAdjustmentResult.APPLIED;
        } catch (RuntimeException invalid) { return EffectAdjustmentResult.FAILED; }
    }

    /** Applies explicit signed gas deltas to the lung inventory, not to room atmosphere. */
    public static EffectAdjustmentResult modifyLungGas(LivingEntity entity, Map<String, Float> ratios,
                                                        double scale) {
        Optional<CharacterData.LungsData> policy = effectPolicy(entity);
        if (policy.isEmpty()) return EffectAdjustmentResult.SKIPPED_UNSUPPORTED;
        if (ratios == null || !Double.isFinite(scale)) return EffectAdjustmentResult.FAILED;
        try {
            LungComponent old = effectComponent(entity, policy.get());
            GasMixture changed = old.mixture();
            for (var entry : ratios.entrySet()) {
                String name = Objects.requireNonNull(entry.getKey());
                GasType gas = GasType.fromId(name);
                if (!gas.id().equals(name)) return EffectAdjustmentResult.FAILED;
                double delta = Objects.requireNonNull(entry.getValue()) * scale;
                if (!Double.isFinite(delta)) return EffectAdjustmentResult.FAILED;
                if (delta < 0d) delta = -Math.min(changed.moles(gas), -delta);
                else delta = Math.min(delta, Math.max(0d, policy.get().maxLungMoles() - changed.totalMoles()));
                if (delta != 0d) changed = changed.withGasDelta(gas, delta, changed.temperatureKelvin());
                if (!Double.isFinite(changed.totalMoles()) || changed.totalMoles() > policy.get().maxLungMoles())
                    return EffectAdjustmentResult.FAILED;
            }
            if (!changed.gasMoles().equals(old.gasMoles())
                    || changed.temperatureKelvin() != old.temperatureKelvin()) MS14Provider.update(entity, MS14Bridges.LUNG,
                     new LungAttachment(LungComponent.from(changed, old.saturation(), true, old.phase())));
            return EffectAdjustmentResult.APPLIED;
        } catch (RuntimeException invalid) { return EffectAdjustmentResult.FAILED; }
    }

    private static Optional<CharacterData.LungsData> effectPolicy(LivingEntity entity) {
        // Reagent effects act only on an existing persisted lung, independently of world gas simulation.
        if (entity == null || entity.level().isClientSide || !(entity.level() instanceof ServerLevel)
                || !entity.hasData(ModDataAttachments.LUNG.get()))
            return Optional.empty();
        return resolvePolicy(entity);
    }

    private static LungComponent effectComponent(LivingEntity entity, CharacterData.LungsData policy) {
        LungComponent component = MS14Provider.getDetached(entity, MS14Bridges.LUNG).component();
        GasMixture mixture = component.mixture();
         if (!component.initialized() || !Double.isFinite(component.saturation()) || component.saturation() < policy.minSaturation()
                || component.saturation() > policy.maxSaturation() || mixture.totalMoles() > policy.maxLungMoles())
            throw new IllegalArgumentException("invalid persisted lung state");
        return component;
    }

    public static Optional<CharacterData.LungsData> resolvePolicy(LivingEntity entity) {
        if(entity==null || entity.level().isClientSide || !(entity.level() instanceof ServerLevel level)) return Optional.empty();
        Optional<CharacterData> character=CharacterIdentitySystem.resolve(entity); if(character.isEmpty()) return Optional.empty();
        ResourceLocation host=BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        var identity=entity.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        return identity==null || !identity.isBound()?Optional.empty():resolvePolicy(character.get(),identity.characterId(),host,ModCharacters.characterForHost(level,host));
    }
    public static Optional<CharacterData.LungsData> resolvePolicy(CharacterData data, ResourceLocation identity, ResourceLocation host, Optional<ResourceLocation> owner) {
        if(data==null||identity==null||host==null||owner==null) return Optional.empty();
        if(!data.hostEntityTypes().isEmpty()&&!data.hostEntityTypes().contains(host)) return Optional.empty();
        if(owner.isPresent()&&!owner.get().equals(identity)) return Optional.empty();
        if(owner.isEmpty()&&!data.hostEntityTypes().isEmpty()) return Optional.empty();
        return data.lungs();
    }
    public static boolean reconcile(LivingEntity entity) {
        var policy=resolvePolicy(entity); if(policy.isEmpty()) return false;
        try {
            var p=policy.get(); int interval=intervalTicks(p.breathIntervalSeconds());
            if(!entity.hasData(ModDataAttachments.LUNG.get())) {
                if(!entity.isAlive()) return false;
                MS14Provider.update(entity,MS14Bridges.LUNG,new LungAttachment(LungComponent.from(GasMixture.vacuum(),p.initialSaturation(),true)));
                return true;
            }
            LungAttachment old=MS14Provider.getDetached(entity,MS14Bridges.LUNG); LungComponent c=old.component();
            c.mixture(); if(c.gasMoles().values().stream().mapToDouble(Double::doubleValue).sum()>p.maxLungMoles()) return false;
             // Old 98/92 saves are explicitly migrated to the new maximum on reconciliation.
             // Strict scheduled ticks and effects fail closed until this reconciliation occurs.
             double sat=Math.min(p.maxSaturation(),c.saturation());
             if(sat<p.minSaturation()) return false;
             if(!c.initialized() || sat!=c.saturation()) MS14Provider.update(entity,MS14Bridges.LUNG,new LungAttachment(new LungComponent(c.gasMoles(),c.temperatureKelvin(),sat,true,c.phase())));
            return true;
        } catch(RuntimeException invalid) { return false; }
    }
     public static boolean tickIfDue(LivingEntity entity,long gameTime) {
         return tickIfDue(entity,gameTime,AtmosphereService.INSTANCE);
     }
      /** Isolated service seam for enabled world tests; production uses the global service. */
      public static boolean tickIfDue(LivingEntity entity,long gameTime,AtmosphereService atmosphere) {
          Optional<CharacterData.LungsData> policy=policyAtCadence(entity,gameTime); if(policy.isEmpty()) return false;
          // A prototype can become available after the spawn/load join hook ran. Reconcile
          // only the attachment here; breathing still requires a due, strict world sample.
          if(!entity.isAlive() || !validPolicy(policy.get()) || !reconcile(entity)) return false;
          int interval=intervalTicks(policy.get().breathIntervalSeconds()); if(!isDue(gameTime,entity.getId(),interval)||!entity.isAlive()||atmosphere==null||!atmosphere.isEnabled()) return false;
         LungComponent old=MS14Provider.getDetached(entity,MS14Bridges.LUNG).component();
        var p=policy.get();
        // Validate and reduce persisted state before the atmosphere's atomic exchange. In
        // particular an uninitialized/migrating attachment and a newly over-cap policy fail shut.
        if(!old.initialized() || !validPolicy(p)) return false;
        GasMixture oldGas;
        try { oldGas=old.mixture(); if(oldGas.totalMoles()>p.maxLungMoles() || old.saturation()>p.maxSaturation()) return false; }
        catch(RuntimeException invalid){return false;}
        BlockPos eye=BlockPos.containing(entity.getEyePosition());
         // A missing strict sample is unknown, not vacuum. It cannot drain saturation or
         // advance phase, even while incapacitated.
         Optional<GasMixture> strictSample=atmosphere.sample((ServerLevel)entity.level(),eye);
         if(strictSample.isEmpty()) return false;
         boolean incapacitated=entity.isSleeping();
          GasMixture[] inhaled = new GasMixture[1];
          LungComponent[] validated = new LungComponent[1];
          Optional<LungComponent> candidate;
          if (incapacitated) {
               candidate = advance(old,p,strictSample.get(),true,(exhaled,requested) -> Optional.empty());
          } else {
              boolean inhale = old.phase() == LungComponent.Phase.INHALING;
              GasMixture exhaled = inhale ? GasMixture.vacuum() : oldGas;
               double requested = inhale ? requestedMoles(strictSample.get(),p.breathVolumeLiters(),
                       p.maxLungMoles()-oldGas.totalMoles()) : 0d;
               var exchanged = atmosphere.exchangeBreath((ServerLevel)entity.level(),eye,requested,exhaled, portion -> {
                   Optional<LungComponent> next = advance(old,p,strictSample.get(),false,(gas,amount) -> Optional.of(portion));
                  if (next.isEmpty()) return false;
                  validated[0] = next.get();
                  if (inhale) inhaled[0] = portion;
                  return true;
              });
              candidate = exchanged.isPresent() ? Optional.ofNullable(validated[0]) : Optional.empty();
          }
         if(candidate.isEmpty()) return false;
         MS14Provider.update(entity,MS14Bridges.LUNG,new LungAttachment(candidate.get()));
         if (old.phase() == LungComponent.Phase.INHALING && !incapacitated && inhaled[0] != null) {
             LungToxicity.perInhale(inhaled[0], p.toxicGasDamagePerMole(), p.toxicGasDamageCapPerInhale())
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

     /** One due update, after an authoritative sample. Callback commits one directional exchange. */
      static Optional<LungComponent> advance(LungComponent current, CharacterData.LungsData policy, GasMixture room, boolean incapacitated,
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
     private static boolean validPolicy(CharacterData.LungsData p) {
         return Double.isFinite(p.breathVolumeLiters()) && p.breathVolumeLiters()>0
                 && Double.isFinite(p.maxLungMoles()) && p.maxLungMoles()>0
                 && Double.isFinite(p.breathMolesToSaturationMultiplier()) && p.breathMolesToSaturationMultiplier()>0
                 && Double.isFinite(p.maxSaturation()) && p.maxSaturation()>0
                 && Double.isFinite(p.minSaturation()) && p.minSaturation()>=-2 && p.minSaturation()<=0
                && Double.isFinite(p.saturationLossPerUpdate()) && p.saturationLossPerUpdate()>=0;
    }
    private static Optional<CharacterData.LungsData> policyAtCadence(LivingEntity entity,long time) {
        if(entity==null||entity.level().isClientSide||!(entity.level() instanceof ServerLevel level)) return Optional.empty();
        ResourceLocation host=BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()); PrototypeCatalog<CharacterData> catalog=ModCharacters.catalog(level);
        var attachment=entity.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get()); ResourceLocation id=attachment!=null&&attachment.isBound()?attachment.characterId():null;
        synchronized(CACHE) { Cache c=CACHE.get(entity); if(c!=null&&c.catalog()==catalog&&c.host().equals(host)&&Objects.equals(c.identity(),id)&&time>=c.checked()&&time-c.checked()<RETRY_TICKS) return c.policy(); }
        if(id==null&&attachment==null) { CharacterIdentitySystem.enrollSupportedActor(entity,level); catalog=ModCharacters.catalog(level); }
        attachment=entity.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get()); id=attachment!=null&&attachment.isBound()?attachment.characterId():null;
        CharacterData data=id==null?null:catalog.get(id); Optional<CharacterData.LungsData> policy=data==null?Optional.empty():resolvePolicy(data,id,host,ModCharacters.characterForHost(catalog,host));
         // Policy lookup does not initialize lungs; tickIfDue reconciles the attachment
         // separately from the strict-sample-gated physical update.
        synchronized(CACHE){CACHE.put(entity,new Cache(time,catalog,host,id,policy));} return policy;
    }
    public static int intervalTicks(double seconds){long t=Math.round(seconds*20); if(t<1||t>Integer.MAX_VALUE)throw new IllegalArgumentException("lung interval out of range"); return (int)t;}
    public static boolean isDue(long time,int entityId,int interval){return interval>0&&Math.floorMod(Math.floorMod(time,(long)interval)+entityId,interval)==0;}
    public static boolean copyToClone(LivingEntity original,LivingEntity clone){
        if(original==null||clone==null||clone.level().isClientSide||!original.hasData(ModDataAttachments.LUNG.get())) return false;
        LungAttachment source=MS14Provider.getDetached(original,MS14Bridges.LUNG);
        if(clone.hasData(ModDataAttachments.LUNG.get())&&source.equals(MS14Provider.getDetached(clone,MS14Bridges.LUNG))) return false;
        MS14Provider.update(clone,MS14Bridges.LUNG,source); return true;
    }
}
