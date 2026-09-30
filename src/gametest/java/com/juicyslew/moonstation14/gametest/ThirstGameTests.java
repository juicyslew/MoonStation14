package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.activity.EntityActivity;
import com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem;
import com.juicyslew.moonstation14.ms14.thirst.ThirstSystem;
import com.juicyslew.moonstation14.ms14.thirst.ThirstAttachment;
import com.juicyslew.moonstation14.ms14.thirst.ThirstComponent;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.ms14.alert.AlertSystem;
import com.juicyslew.moonstation14.ms14.alert.AlertAttachment;
import com.juicyslew.moonstation14.ms14.alert.ModAlerts;
import com.juicyslew.moonstation14.component.codec.json.AlertData;
import com.juicyslew.moonstation14.ms14.effect.ConditionContext;
import com.juicyslew.moonstation14.ms14.effect.EffectCause;
import com.juicyslew.moonstation14.ms14.effect.EffectContext;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import com.juicyslew.moonstation14.ms14.effect.EffectSystem;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import com.juicyslew.moonstation14.eventhooks.TickHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThirstGameTests {
    private ThirstGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void staggeredRetryInitializesAbsentNeedsWithoutRerollingSavedState(GameTestHelper helper) {
        Villager missing = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        Villager saved = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 1, 1));
        missing.removeData(ModDataAttachments.HUNGER.get());
        missing.removeData(ModDataAttachments.THIRST.get());
        var savedThirst = new ThirstAttachment(new ThirstComponent(0f));
        saved.setData(ModDataAttachments.THIRST.get(), savedThirst);
        var savedHunger = new com.juicyslew.moonstation14.ms14.hunger.HungerAttachment(
                new com.juicyslew.moonstation14.ms14.hunger.HungerComponent(0f));
        saved.setData(ModDataAttachments.HUNGER.get(), savedHunger);
        helper.runAfterDelay(22, () -> {
            require(missing.hasData(ModDataAttachments.HUNGER.get())
                            && missing.hasData(ModDataAttachments.THIRST.get()),
                    "retry cadence initializes absent eligible needs");
            require(saved.getExistingDataOrNull(ModDataAttachments.HUNGER.get()) == savedHunger
                            && saved.getExistingDataOrNull(ModDataAttachments.THIRST.get()) == savedThirst,
                    "retry must not reroll saved needs");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void zeroThirstWithoutActivityShedsProjectionOnTick(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        zombie.setData(ModDataAttachments.THIRST.get(), new ThirstAttachment(new ThirstComponent(0f)));
        var saved = zombie.getExistingDataOrNull(ModDataAttachments.THIRST.get());
        AlertSystem.apply(zombie, helper.getLevel(), ModAlerts.createKey("parched"), false, 0, false);
        zombie.getAttribute(Attributes.MOVEMENT_SPEED).addTransientModifier(
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(ThirstSystem.PARCHED_MODIFIER_ID,
                        -.25, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        EntityActivitySystem.update(zombie, EntityActivity.THIRST, false);
        TickHooks.onLivingEntityTick(new EntityTickEvent.Pre(zombie));
        require(!has(zombie, "parched"), "stale alert must clear without a THIRST activity");
        require(zombie.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ThirstSystem.PARCHED_MODIFIER_ID) == null,
                "stale slowdown must clear without a THIRST activity");
        require(zombie.getExistingDataOrNull(ModDataAttachments.THIRST.get()) == saved,
                "saved zero scalar must not be replaced");
        TickHooks.onLivingEntityTick(new EntityTickEvent.Pre(zombie));
        require(zombie.getExistingDataOrNull(ModDataAttachments.THIRST.get()) == saved,
                "inert cleanup must not write the scalar on later ticks");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void explicitEligibilityInitializesOnceAndExcludesZombie(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        villager.setNoAi(true);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 1));

        require(ThirstSystem.isEligible(villager), "bound villager must carry Thirst");
        require(!ThirstSystem.isEligible(zombie), "zombie must not be enrolled");
        require(villager.hasData(ModDataAttachments.THIRST.get()), "join should initialize villager thirst");
        float initialized = MS14Provider.get(villager, MS14Bridges.THIRST).thirst();
        require(initialized >= 310f && initialized < 449f, "initial thirst must be in [310, 449)");
        require(!ThirstSystem.initializeIfEligible(villager, helper.getLevel()), "existing thirst must not reroll");
        require(MS14Provider.get(villager, MS14Bridges.THIRST).thirst() == initialized,
                "existing thirst must be preserved exactly");
        require(!zombie.hasData(ModDataAttachments.THIRST.get()), "unrelated zombie must remain without thirst");

        EntityActivitySystem.reconcile(villager);
        var activity = villager.getExistingDataOrNull(ModDataAttachments.ACTIVE_SYSTEMS.get());
        require(activity != null && activity.isActive(EntityActivity.THIRST), "positive eligible thirst needs activity");

        ThirstAttachment nearZero = new ThirstAttachment(new ThirstComponent(.05f));
        MS14Provider.update(villager, MS14Bridges.THIRST, nearZero);
        require(ThirstSystem.decayOneSecond(villager), "one due invocation must apply one decay transition");
        require(MS14Provider.get(villager, MS14Bridges.THIRST).thirst() == 0f,
                "one upstream second must clamp thirst to zero");
        EntityActivitySystem.reconcile(villager);
        var stopped = villager.getExistingDataOrNull(ModDataAttachments.ACTIVE_SYSTEMS.get());
        require(stopped == null || !stopped.isActive(EntityActivity.THIRST), "zero thirst must stop activity");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void thresholdProjectionAndSatiationAreThirstOwned(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        villager.setNoAi(true);
        ServerLevel level = helper.getLevel();
        ResourceKey<AlertData> unrelated = ModAlerts.createKey("toxins");
        require(AlertSystem.apply(villager, level, unrelated, false, 0, false)
                == AlertSystem.Outcome.APPLIED, "preexisting unrelated alert must apply");

        seed(villager, 450f);
        require(!has(villager, "thirsty") && !has(villager, "parched"), "450 is OKAY without thirst alerts");
        require(villager.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ThirstSystem.PARCHED_MODIFIER_ID) == null,
                "OKAY has no thirst slowdown");
        seed(villager, 300f);
        require(has(villager, "thirsty"), "300 is THIRSTY");
        seed(villager, 150f);
        require(!has(villager, "thirsty") && has(villager, "parched"), "150 is PARCHED");
        require(villager.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ThirstSystem.PARCHED_MODIFIER_ID) != null,
                "PARCHED has deterministic speed projection");
        seed(villager, 0f);
        require(has(villager, "parched"), "zero is the DEAD thirst threshold, presented as Parched");
        require(villager.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ThirstSystem.PARCHED_MODIFIER_ID) != null,
                "DEAD thirst level retains deterministic speed projection without implying death");
        seed(villager, 450.5f);
        require(!has(villager, "parched") && !has(villager, "thirsty"), "recovery removes thirst alerts");
        require(villager.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ThirstSystem.PARCHED_MODIFIER_ID) == null,
                "recovery removes only thirst slowdown");
        AlertAttachment alerts = MS14Provider.get(villager, MS14Bridges.ALERT);
        require(alerts.get(unrelated).isPresent(), "recovery preserves unrelated alert");

        // Reconciliation over an already-matching projection must not replace
        // the alert attachment (and therefore performs no component write).
        ThirstSystem.reconcile(villager, level);
        require(villager.getExistingDataOrNull(ModDataAttachments.ALERT.get()) == alerts,
                "unchanged reconciliation preserves alert attachment identity");

        float before = MS14Provider.get(villager, MS14Bridges.THIRST).thirst();
        EffectSystem effects = EffectSystem.withDefaults();
        EffectContext context = new EffectContext(level, villager, 2f, RandomSource.create(4L),
                ConditionContext.builder().build(), EffectCause.MANUAL);
        require(effects.apply(new EffectData.SatiateThirst(java.util.List.of(), 1.5f), context)
                        == EffectResult.APPLIED,
                "actual effect dispatcher applies SatiateThirst to initialized eligible target");
        require(MS14Provider.get(villager, MS14Bridges.THIRST).thirst() == before + 3f,
                "dispatcher applies factor multiplied by admitted scale");
        require(ThirstSystem.satiate(villager, level, Float.NaN, 1f) == ThirstSystem.Outcome.FAILED,
                "invalid effect is a controlled failure");
        require(MS14Provider.get(villager, MS14Bridges.THIRST).thirst() == before + 3f,
                "invalid effect does not mutate thirst");

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 1));
        require(ThirstSystem.satiate(zombie, level, 2f, 1f) == ThirstSystem.Outcome.SKIPPED_UNSUPPORTED,
                "noneligible zombie is unsupported");
        Villager absent = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 1, 1));
        absent.removeData(ModDataAttachments.THIRST.get());
        require(ThirstSystem.satiate(absent, level, 2f, 1f) == ThirstSystem.Outcome.SKIPPED_UNSUPPORTED,
                "missing attachment is not silently materialized");
        require(!absent.hasData(ModDataAttachments.THIRST.get()), "unsupported effect leaves attachment absent");

        EffectContext zombieContext = new EffectContext(level, zombie, 1f, RandomSource.create(5L),
                ConditionContext.builder().build(), EffectCause.MANUAL);
        require(effects.apply(new EffectData.SatiateThirst(java.util.List.of(), 1.5f), zombieContext)
                        == EffectResult.SKIPPED_UNSUPPORTED,
                "dispatcher skips noneligible target");
        EffectContext absentContext = new EffectContext(level, absent, 1f, RandomSource.create(6L),
                ConditionContext.builder().build(), EffectCause.MANUAL);
        require(effects.apply(new EffectData.SatiateThirst(java.util.List.of(), 1.5f), absentContext)
                        == EffectResult.SKIPPED_UNSUPPORTED,
                "dispatcher skips eligible target without authoritative attachment");

        // Persisted scalar remains authoritative; transient projection can be
        // reconstructed after loss without a full save/reload simulation.
        seed(villager, 150f);
        villager.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(ThirstSystem.PARCHED_MODIFIER_ID);
        EntityActivitySystem.update(villager, EntityActivity.THIRST, false);
        require(ThirstSystem.initializeIfEligible(villager, level) == false,
                "re-enrollment does not replace persisted thirst");
        EntityActivitySystem.reconcile(villager);
        require(villager.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(ThirstSystem.PARCHED_MODIFIER_ID) != null,
                "re-enrollment reconciles transient modifier from persisted thirst");
        require(villager.getExistingDataOrNull(ModDataAttachments.ACTIVE_SYSTEMS.get())
                        .isActive(EntityActivity.THIRST),
                "re-enrollment restores activity from persisted positive thirst");

        // Eligibility-loss owner path: derived projection is removed without
        // deleting authoritative thirst or unrelated alerts/modifiers.
        seed(villager, 150f);
        var unrelatedModifierId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "moonstation14", "test/unrelated");
        // A noneligible type with copied test state exercises the same
        // ThirstSystem cleanup routine used by TickHooks on eligibility loss.
        Zombie transformed = helper.spawn(EntityType.ZOMBIE, new BlockPos(10, 1, 1));
        MS14Provider.update(transformed, MS14Bridges.THIRST,
                new ThirstAttachment(new ThirstComponent(150f)));
        transformed.getAttribute(Attributes.MOVEMENT_SPEED).addTransientModifier(
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(unrelatedModifierId, .1,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        require(AlertSystem.apply(transformed, level, ModAlerts.createKey("parched"), false, 0, false)
                == AlertSystem.Outcome.APPLIED, "seed thirst-owned alert on transformation fixture");
        EntityActivitySystem.update(transformed, EntityActivity.THIRST, true);
        transformed.getAttribute(Attributes.MOVEMENT_SPEED).addTransientModifier(
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(ThirstSystem.PARCHED_MODIFIER_ID,
                        -.25, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        ThirstSystem.reconcile(transformed, level);
        EntityActivitySystem.update(transformed, EntityActivity.THIRST, false);
        require(transformed.hasData(ModDataAttachments.THIRST.get()),
                "eligibility loss preserves authoritative thirst attachment");
        require(!has(transformed, "parched") && transformed.getAttribute(Attributes.MOVEMENT_SPEED)
                        .getModifier(ThirstSystem.PARCHED_MODIFIER_ID) == null,
                "eligibility loss removes thirst-owned alert and slowdown");
        require(transformed.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(unrelatedModifierId) != null,
                "eligibility loss preserves unrelated speed modifier");
        helper.succeed();
    }

    private static void seed(Villager entity, float value) {
        MS14Provider.update(entity, MS14Bridges.THIRST, new ThirstAttachment(new ThirstComponent(value)));
        ThirstSystem.reconcile(entity, (ServerLevel) entity.level());
    }

    private static boolean has(LivingEntity entity, String key) {
        AlertAttachment state = MS14Provider.get(entity, MS14Bridges.ALERT);
        return state.get(ModAlerts.createKey(key)).isPresent();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }
}
