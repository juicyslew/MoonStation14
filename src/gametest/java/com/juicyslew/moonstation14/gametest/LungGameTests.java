package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.atmos.core.GasMixture;
import com.juicyslew.moonstation14.ms14.atmos.core.GasType;
import com.juicyslew.moonstation14.ms14.atmos.world.AtmosphereService;
import com.juicyslew.moonstation14.component.codec.json.EffectCommonData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.ms14.effect.*;
import com.juicyslew.moonstation14.ms14.lung.*;
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

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LungGameTests {
    private LungGameTests() {}
    @GameTest(template="empty",timeoutTicks=20)
    public static void pigBloodstreamOxygenMetabolizesIntoSaturation(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1,1,1));
        pig.setNoAi(true);
        helper.runAfterDelay(1, () -> {
             if (!LungSystem.reconcile(pig)) throw new GameTestAssertException("pig must have configured lungs");
             pig.setData(ModDataAttachments.LUNG.get(), new LungAttachment(LungComponent.from(GasMixture.vacuum(), 1, true)));
             double before = MS14Provider.getDetached(pig, MS14Bridges.LUNG).component().saturation();
            var gasBefore = MS14Provider.getDetached(pig, MS14Bridges.LUNG).component().gasMoles();
            var oxygen = ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY,
                    ResourceLocation.fromNamespaceAndPath("moonstation14", "oxygen"));
            if (pig.getExistingDataOrNull(ModDataAttachments.BLOODSTREAM.get()) != null
                    && pig.getData(ModDataAttachments.BLOODSTREAM.get()).getMap().getOrDefault(oxygen, 0f) != 0f)
                throw new GameTestAssertException("breathing must not manufacture bloodstream oxygen");
            pig.setData(ModDataAttachments.BLOODSTREAM.get(), new ReagentAttachment(Map.of(oxygen, 1f)));
            TickHooks.runDueActivities(pig, (net.minecraft.server.level.ServerLevel) helper.getLevel(),
                    Set.of(EntityActivity.REAGENT_METABOLISM));
            double after = MS14Provider.getDetached(pig, MS14Bridges.LUNG).component().saturation();
            if (after <= before) throw new GameTestAssertException("actual oxygen bloodstream metabolism must raise pig saturation");
            var gasAfter = MS14Provider.getDetached(pig, MS14Bridges.LUNG).component().mixture();
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
            pig.setData(ModDataAttachments.LUNG.get(), new LungAttachment(initial));
            var effects = EffectSystem.withDefaults();
            var context = new EffectContext(helper.getLevel(), pig, 0.25f, RandomSource.create(1),
                    ConditionContext.builder().build(), EffectCause.MANUAL);
            var roomBefore = AtmosphereService.INSTANCE.sample(helper.getLevel(), BlockPos.containing(pig.getEyePosition()));
            if (effects.apply(new EffectData.Oxygenate(EffectCommonData.DEFAULT, 2f), context) != EffectResult.APPLIED
                     || MS14Provider.getDetached(pig, MS14Bridges.LUNG).component().saturation() != 2.5)
                throw new GameTestAssertException("oxygenate must apply factor times scale exactly once");
            if (effects.apply(new EffectData.ModifyLungGas(EffectCommonData.DEFAULT,
                    Map.of("oxygen", -1f, "carbon_dioxide", 1f)), context) != EffectResult.APPLIED)
                throw new GameTestAssertException("gas adjustment must work independently of world atmosphere");
            var changed = MS14Provider.getDetached(pig, MS14Bridges.LUNG).component();
            if (Math.abs(changed.mixture().moles(GasType.OXYGEN) - 0.15) > 1e-9
                    || Math.abs(changed.mixture().moles(GasType.CARBON_DIOXIDE) - 0.25) > 1e-9
                     || changed.saturation() != 2.5)
                throw new GameTestAssertException("scaled gas delta must affect only the lung inventory once");
            if (!AtmosphereService.INSTANCE.sample(helper.getLevel(), BlockPos.containing(pig.getEyePosition())).equals(roomBefore))
                throw new GameTestAssertException("lung gas effect must not mutate room atmosphere");
            unconfigured.setData(ModDataAttachments.LUNG.get(), new LungAttachment(initial));
            if (LungSystem.oxygenate(unconfigured, 2) != LungSystem.EffectAdjustmentResult.SKIPPED_UNSUPPORTED
                    || LungSystem.modifyLungGas(unconfigured, Map.of("oxygen", 1f), 1)
                    != LungSystem.EffectAdjustmentResult.SKIPPED_UNSUPPORTED
                    || !MS14Provider.getDetached(unconfigured, MS14Bridges.LUNG).component().equals(initial))
                throw new GameTestAssertException("unconfigured host must remain inert even with a lung attachment");
            pig.removeData(ModDataAttachments.LUNG.get());
            if (LungSystem.oxygenate(pig, 1) != LungSystem.EffectAdjustmentResult.SKIPPED_UNSUPPORTED
                    || pig.hasData(ModDataAttachments.LUNG.get()))
                throw new GameTestAssertException("absent lung must not initialize through an effect");
             pig.setData(ModDataAttachments.LUNG.get(), new LungAttachment(LungComponent.from(GasMixture.vacuum(), 5, false)));
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
            if(!LungSystem.reconcile(pig)||!pig.hasData(ModDataAttachments.LUNG.get())) throw new GameTestAssertException("configured pig lung should initialize");
            var initial=MS14Provider.getDetached(pig,MS14Bridges.LUNG).component();
             if(!initial.initialized()||!initial.gasMoles().isEmpty()||initial.saturation()!=5) throw new GameTestAssertException("initial lung must be empty at prototype saturation");
             if(!LungSystem.reconcile(human) || MS14Provider.getDetached(human,MS14Bridges.LUNG).component().saturation()!=5)
                 throw new GameTestAssertException("human-hosted villager must use its distinct prototype lung policy");
             pig.setData(ModDataAttachments.LUNG.get(),new LungAttachment(LungComponent.from(GasMixture.vacuum(),92,true)));
             if(LungSystem.oxygenate(pig,1)!=LungSystem.EffectAdjustmentResult.FAILED || !LungSystem.reconcile(pig)
                     || MS14Provider.getDetached(pig,MS14Bridges.LUNG).component().saturation()!=5)
                 throw new GameTestAssertException("legacy 92 saturation must fail closed until explicit reconciliation clamps to five");
             var encoded=LungComponent.CODEC.encodeStart(JsonOps.INSTANCE,initial).getOrThrow();
             var restored=LungComponent.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow();
            pig.setData(ModDataAttachments.LUNG.get(),new LungAttachment(restored));
            LungSystem.reconcile(pig);
             if(!MS14Provider.getDetached(pig,MS14Bridges.LUNG).component().equals(initial)) throw new GameTestAssertException("retry must preserve persisted initialized empty state");
             var exhaling=LungComponent.from(GasMixture.vacuum(),initial.saturation(),true,LungComponent.Phase.EXHALING);
             pig.setData(ModDataAttachments.LUNG.get(),new LungAttachment(exhaling));
             Pig clone=helper.spawn(EntityType.PIG,new BlockPos(5,1,1));
             if(!LungSystem.copyToClone(pig,clone)
                     || MS14Provider.getDetached(clone,MS14Bridges.LUNG).component().phase()!=LungComponent.Phase.EXHALING)
                 throw new GameTestAssertException("clone must preserve the persisted respiratory phase");
             helper.succeed();
        });
    }
}
