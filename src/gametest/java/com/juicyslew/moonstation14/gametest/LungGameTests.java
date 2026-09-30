package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.organ.*;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.component.codec.json.EffectCommonData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.ms14.effect.*;
import com.juicyslew.moonstation14.ms14.lung.*;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character.PlayerCharacterHarnessRegistration;
import com.juicyslew.moonstation14.eventhooks.TickHooks;
import com.juicyslew.moonstation14.ms14.activity.EntityActivity;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LungGameTests {
    private LungGameTests() {}
    @GameTest(template="empty",timeoutTicks=20)
    public static void explicitBodyRetainsPersistedOrganAndRevokesCachedPolicy(GameTestHelper helper) {
        var body = helper.spawn(PlayerCharacterHarnessRegistration.getEntityType(), new BlockPos(1,1,1));
        Zombie unmapped = helper.spawn(EntityType.ZOMBIE, new BlockPos(3,1,1));
        CompoundTag saved = new CompoundTag();
        CompoundTag binding = new CompoundTag();
        binding.putUUID("Account", UUID.randomUUID());
        binding.putString("Profile", "main");
        binding.putUUID("Mind", UUID.randomUUID());
        saved.put("Moonstation14PlayerCharacterBinding", binding);
        body.readAdditionalSaveData(saved);
        if (!CharacterIdentitySystem.enroll(body, helper.getLevel(), ModCharacters.HUMAN_ID))
            throw new GameTestAssertException("explicit HUMAN fixture must enroll");
        helper.runAfterDelay(1, () -> {
            if (LungSystem.resolvePolicy(body).isEmpty() || !LungSystem.reconcile(body)
                    || LungSystem.resolvePolicy(unmapped).isPresent())
                throw new GameTestAssertException("saved harness has respiration; unmapped vanilla does not");
            BodyState persisted = BodySystem.current(body).orElseThrow();
            var organ = persisted.find(OrganCategory.LUNGS).orElseThrow();
            if (!LungSystem.reconcile(body) || !BodySystem.current(body).orElseThrow().equals(persisted))
                throw new GameTestAssertException("late lung policy must not duplicate saved organ");
            body.setData(ModDataAttachments.BODY.get(), new BodyAttachment(BodyState.EMPTY));
            if (!LungSystem.reconcile(body) || !BodySystem.current(body).orElseThrow().organs().isEmpty())
                throw new GameTestAssertException("saved empty BODY must not regenerate organs");
            body.setData(ModDataAttachments.BODY.get(), new BodyAttachment(persisted));
            LungSystem.tickIfDue(body, helper.getLevel().getGameTime(), null); // populate cadence cache
            CompoundTag invalid = new CompoundTag();
            invalid.putString("Moonstation14PlayerCharacterBinding", "invalid");
            body.readAdditionalSaveData(invalid);
            if (LungSystem.resolvePolicy(body).isPresent()
                    || LungSystem.tickIfDue(body, helper.getLevel().getGameTime(), null)
                    || LungSystem.oxygenate(body, 1) != LungSystem.EffectAdjustmentResult.SKIPPED_UNSUPPORTED
                    || !BodySystem.current(body).orElseThrow().find(OrganCategory.LUNGS).orElseThrow().id().equals(organ.id()))
                throw new GameTestAssertException("invalid binding immediately revokes lung policy without mutating saved BODY");
            helper.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void pigBloodstreamOxygenMetabolizesIntoSaturation(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1,1,1));
        pig.setNoAi(true);
        helper.runAfterDelay(1, () -> {
             if (!LungSystem.reconcile(pig)) throw new GameTestAssertException("pig must have configured lungs");
             store(pig, LungComponent.from(GasMixture.vacuum(), 1, true));
             double before = snapshot(pig).saturation();
            var gasBefore = snapshot(pig).gasMoles();
            var oxygen = ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,
                    ResourceLocation.fromNamespaceAndPath("moonstation14", "oxygen"));
            if (pig.getExistingDataOrNull(ModDataAttachments.BLOODSTREAM.get()) != null
                    && pig.getData(ModDataAttachments.BLOODSTREAM.get()).getMap().getOrDefault(oxygen, 0f) != 0f)
                throw new GameTestAssertException("breathing must not manufacture bloodstream oxygen");
            pig.setData(ModDataAttachments.BLOODSTREAM.get(), new ReagentAttachment(Map.of(oxygen, 1f)));
            TickHooks.runDueActivities(pig, (net.minecraft.server.level.ServerLevel) helper.getLevel(),
                    Set.of(EntityActivity.REAGENT_METABOLISM));
            double after = snapshot(pig).saturation();
            if (after <= before) throw new GameTestAssertException("actual oxygen bloodstream metabolism must raise pig saturation");
            var gasAfter = snapshot(pig).mixture();
            if (gasAfter.moles(GasType.OXYGEN) > gasBefore.getOrDefault("oxygen", 0d)
                    || gasAfter.moles(GasType.CARBON_DIOXIDE) < gasBefore.getOrDefault("carbon_dioxide", 0d))
                throw new GameTestAssertException("oxygen metabolism may convert lung gas, not invent inhaled oxygen");
            if (pig.getData(ModDataAttachments.BLOODSTREAM.get()).getMap().getOrDefault(oxygen, 0f) >= 1f)
                throw new GameTestAssertException("oxygen must be consumed by real metabolism");
            helper.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void lungEffectsScaleOnceAndStayInPersistedInventory(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1,1,1));
        pig.setNoAi(true);
        Zombie unconfigured = helper.spawn(EntityType.ZOMBIE, new BlockPos(4,1,1));
        unconfigured.setNoAi(true);
        helper.runAfterDelay(1, () -> {
            if (!LungSystem.reconcile(pig)) throw new GameTestAssertException("pig must have bound lungs");
            var initial = LungComponent.from(new GasMixture(Map.of(GasType.OXYGEN, 0.4), 300), 2, true);
            store(pig, initial);
            var effects = EffectSystem.withDefaults();
            var context = new EffectContext(helper.getLevel(), pig, 0.25f, RandomSource.create(1),
                    ConditionContext.builder().build(), EffectCause.MANUAL);
            var roomBefore = AtmosphereService.INSTANCE.sample(helper.getLevel(), BlockPos.containing(pig.getEyePosition()));
            if (effects.apply(new EffectData.Oxygenate(EffectCommonData.DEFAULT, 2f), context) != EffectResult.APPLIED
                     || snapshot(pig).saturation() != 2.5)
                throw new GameTestAssertException("oxygenate must apply factor times scale exactly once");
            if (effects.apply(new EffectData.ModifyLungGas(EffectCommonData.DEFAULT,
                    Map.of("oxygen", -1f, "carbon_dioxide", 1f)), context) != EffectResult.APPLIED)
                throw new GameTestAssertException("gas adjustment must work independently of world atmosphere");
            var changed = snapshot(pig);
            if (Math.abs(changed.mixture().moles(GasType.OXYGEN) - 0.15) > 1e-9
                    || Math.abs(changed.mixture().moles(GasType.CARBON_DIOXIDE) - 0.25) > 1e-9
                     || changed.saturation() != 2.5)
                throw new GameTestAssertException("scaled gas delta must affect only the lung inventory once");
            if (!AtmosphereService.INSTANCE.sample(helper.getLevel(), BlockPos.containing(pig.getEyePosition())).equals(roomBefore))
                throw new GameTestAssertException("lung gas effect must not mutate room atmosphere");
             unconfigured.setData(ModDataAttachments.BODY.get(), new BodyAttachment(BodyState.EMPTY));
            if (LungSystem.oxygenate(unconfigured, 2) != LungSystem.EffectAdjustmentResult.SKIPPED_UNSUPPORTED
                    || LungSystem.modifyLungGas(unconfigured, Map.of("oxygen", 1f), 1)
                    != LungSystem.EffectAdjustmentResult.SKIPPED_UNSUPPORTED
                     || !BodySystem.current(unconfigured).orElseThrow().equals(BodyState.EMPTY))
                throw new GameTestAssertException("unconfigured host must remain inert even with a body attachment");
             pig.removeData(ModDataAttachments.BODY.get());
            if (LungSystem.oxygenate(pig, 1) != LungSystem.EffectAdjustmentResult.SKIPPED_UNSUPPORTED
                    || pig.hasData(ModDataAttachments.BODY.get()))
                 throw new GameTestAssertException("absent body must not initialize through an effect");
             if (!LungSystem.reconcile(pig)) throw new GameTestAssertException("restore body fixture");
              store(pig, LungComponent.from(GasMixture.vacuum(), 5, false));
            if (LungSystem.oxygenate(pig, 1) != LungSystem.EffectAdjustmentResult.FAILED)
                throw new GameTestAssertException("invalid persisted lung must fail closed");
            helper.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=20)
    public static void pigLungInitializesConfiguredSaturationAndPersistsEmptyState(GameTestHelper helper) {
        Pig pig=helper.spawn(EntityType.PIG,new BlockPos(1,1,1)); pig.setNoAi(true);
        Villager human=helper.spawn(EntityType.VILLAGER,new BlockPos(3,1,1)); human.setNoAi(true);
        helper.runAfterDelay(1,()->{
            if(!LungSystem.reconcile(pig)||!pig.hasData(ModDataAttachments.BODY.get())) throw new GameTestAssertException("configured pig lung should initialize");
            var initial=snapshot(pig);
             if(!initial.initialized()||!initial.gasMoles().isEmpty()||initial.saturation()!=5) throw new GameTestAssertException("initial lung must be empty at prototype saturation");
             if(!LungSystem.reconcile(human) || snapshot(human).saturation()!=5)
                 throw new GameTestAssertException("human-hosted villager must use its distinct prototype lung policy");
             store(pig, LungComponent.from(GasMixture.vacuum(),92,true));
             if(LungSystem.oxygenate(pig,1)!=LungSystem.EffectAdjustmentResult.FAILED || !LungSystem.reconcile(pig)
                     || snapshot(pig).saturation()!=5)
                  throw new GameTestAssertException("saved BODY saturation 92 must fail closed until explicit reconciliation clamps to five");
              store(pig, initial);
              BodyState saved = BodySystem.current(pig).orElseThrow();
              var encoded=BodyAttachment.CODEC.encodeStart(JsonOps.INSTANCE,new BodyAttachment(saved)).getOrThrow();
              var restored=BodyAttachment.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow().state();
              pig.setData(ModDataAttachments.BODY.get(),new BodyAttachment(restored));
             LungSystem.reconcile(pig);
              if(!snapshot(pig).equals(initial) || !BodySystem.current(pig).orElseThrow().equals(saved))
                  throw new GameTestAssertException("retry must preserve persisted initialized empty body and organ identity");
             var exhaling=LungComponent.from(GasMixture.vacuum(),initial.saturation(),true,LungComponent.Phase.EXHALING);
              store(pig, exhaling);
              Pig clone=helper.spawn(EntityType.PIG,new BlockPos(5,1,1));
              clone.removeData(ModDataAttachments.BODY.get());
               if(!BodySystem.copyToClone(pig,clone)
                      || !BodySystem.current(clone).orElseThrow().equals(BodySystem.current(pig).orElseThrow())
                      || snapshot(clone).phase()!=LungComponent.Phase.EXHALING)
                  throw new GameTestAssertException("clone must preserve the persisted respiratory phase");
              OrganInstance detached = BodySystem.current(pig).orElseThrow().find(OrganCategory.LUNGS).orElseThrow();
              if(BodySystem.detach(pig, detached.id()).isEmpty())
                  throw new GameTestAssertException("saved lung must detach");
              BodyState withoutLung = BodySystem.current(pig).orElseThrow();
              if(!LungSystem.reconcile(pig) || !BodySystem.current(pig).orElseThrow().equals(withoutLung))
                  throw new GameTestAssertException("saved BODY without organs must not recreate a lung");
              pig.setData(ModDataAttachments.BODY.get(), new BodyAttachment(BodyState.EMPTY));
              if(!LungSystem.reconcile(pig) || !BodySystem.current(pig).orElseThrow().organs().isEmpty()
                      || BodySystem.current(pig).orElseThrow().respiration().saturation()!=5)
                  throw new GameTestAssertException("saved EMPTY BODY must not regenerate organs");
             helper.succeed();
        });
    }
    private static LungComponent snapshot(net.minecraft.world.entity.LivingEntity entity) {
        BodyState body = BodySystem.current(entity).orElseThrow();
        LungOrganState lung = body.find(OrganCategory.LUNGS).orElseThrow().lung();
        RespirationState respiration = body.respiration();
        return new LungComponent(lung.gasMoles(), lung.temperatureKelvin(), respiration.saturation(),
                respiration.initialized(), respiration.phase());
    }
    private static void store(net.minecraft.world.entity.LivingEntity entity, LungComponent state) {
        BodyState body = BodySystem.current(entity).orElseThrow();
        OrganInstance lung = body.find(OrganCategory.LUNGS).orElseThrow();
        body = body.updateLung(lung.id(), LungOrganState.from(state), ModOrgans.catalog(entity.level()))
                .updateRespiration(RespirationState.from(state));
        entity.setData(ModDataAttachments.BODY.get(), new BodyAttachment(body));
    }

}
