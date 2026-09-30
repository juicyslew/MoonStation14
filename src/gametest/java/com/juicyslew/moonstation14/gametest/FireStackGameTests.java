package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.EffectCommonData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.ms14.activity.EntityActivity;
import com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem;
import com.juicyslew.moonstation14.ms14.effect.ConditionContext;
import com.juicyslew.moonstation14.ms14.effect.EffectCause;
import com.juicyslew.moonstation14.ms14.effect.EffectContext;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import com.juicyslew.moonstation14.ms14.effect.EffectSystem;
import com.juicyslew.moonstation14.eventhooks.TickHooks;
import com.juicyslew.moonstation14.ms14.activity.EntityActivityAttachment;
import com.juicyslew.moonstation14.ms14.fire.FireStackAttachment;
import com.juicyslew.moonstation14.ms14.fire.FireStackComponent;
import com.juicyslew.moonstation14.ms14.fire.FireStackSystem;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.monster.Blaze;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FireStackGameTests {
    private FireStackGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void flammableUsesScaleAndExistingMultiplier(GameTestHelper helper) {
        Villager zombie = spawn(helper, 1);
        EffectSystem effects = EffectSystem.withDefaults();

        require(apply(effects, helper, zombie,
                new EffectData.Flammable(EffectCommonData.DEFAULT, 2f, 5f), .5f)
                        == EffectResult.APPLIED, "first Flammable must apply");
        assertState(zombie, 1f, false);

        require(apply(effects, helper, zombie,
                new EffectData.Flammable(EffectCommonData.DEFAULT, 2f, 5f), 1f)
                        == EffectResult.APPLIED, "second Flammable must apply");
        assertState(zombie, 6f, false);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void igniteOnlyChangesAuthoritativeState(GameTestHelper helper) {
        Villager zombie = spawn(helper, 1);
        EffectSystem effects = EffectSystem.withDefaults();
        require(apply(effects, helper, zombie,
                new EffectData.Flammable(EffectCommonData.DEFAULT, 2f, null), 1f)
                == EffectResult.APPLIED, "flammable setup must apply");

        float healthBefore = zombie.getHealth();
        boolean onFireBefore = zombie.isOnFire();
        int ticksBefore = zombie.getRemainingFireTicks();
        require(apply(effects, helper, zombie, new EffectData.Ignite(EffectCommonData.DEFAULT), 99f)
                == EffectResult.APPLIED, "Ignite must apply");
        assertState(zombie, 2f, true);
        require(zombie.getHealth() == healthBefore,
                "Ignite must not deal immediate damage: " + fixtureDiagnostics(zombie, healthBefore));
        require(zombie.isOnFire() == onFireBefore
                        && zombie.getRemainingFireTicks() == ticksBefore,
                "Ignite must not touch vanilla fire state: " + fixtureDiagnostics(zombie, healthBefore));

        require(apply(effects, helper, zombie, new EffectData.Ignite(EffectCommonData.DEFAULT), .01f)
                == EffectResult.APPLIED, "repeated Ignite must be applied idempotently");
        assertState(zombie, 2f, true);
        require(zombie.getHealth() == healthBefore,
                "repeated Ignite must not deal immediate damage: " + fixtureDiagnostics(zombie, healthBefore));

        long start = zombie.level().getGameTime();
        monitorIgnite(helper, effects, zombie, healthBefore, onFireBefore, ticksBefore,
                start + 40, healthBefore);
    }

    private static void monitorIgnite(GameTestHelper helper, EffectSystem effects, Villager zombie,
                                      float healthBefore, boolean onFireBefore, int ticksBefore,
                                      long deadline, float previousHealth) {
        helper.runAfterDelay(1, () -> {
            require(zombie.isOnFire() == onFireBefore
                            && zombie.getRemainingFireTicks() == ticksBefore,
                    "Ignite must leave vanilla fire state unchanged over time: "
                            + fixtureDiagnostics(zombie, healthBefore));
            // Ambient damage (including vacuum) is allowed; only a fire-attributed health loss fails.
            DamageSource lastDamage = zombie.getLastDamageSource();
            float health = zombie.getHealth();
            require(health >= previousHealth || lastDamage == null
                            || !(lastDamage.is(DamageTypes.ON_FIRE) || lastDamage.is(DamageTypes.IN_FIRE)),
                    "Ignite must not cause vanilla burn damage: "
                            + fixtureDiagnostics(zombie, healthBefore));
            if (zombie.level().getGameTime() < deadline) {
                monitorIgnite(helper, effects, zombie, healthBefore, onFireBefore, ticksBefore,
                        deadline, health);
                return;
            }
            require(apply(effects, helper, zombie,
                    new EffectData.Extinguish(EffectCommonData.DEFAULT, -1.5f), 0f)
                            == EffectResult.APPLIED,
                    "Extinguish boundary must apply");
            require(!zombie.hasData(ModDataAttachments.FIRE_STACK.get())
                            && zombie.isOnFire() == onFireBefore
                            && zombie.getRemainingFireTicks() == ticksBefore,
                    "state-only Extinguish must not alter vanilla fire state");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void igniteWithoutPositiveStacksIsQuietAndDoesNotMaterialize(GameTestHelper helper) {
        EffectSystem effects = EffectSystem.withDefaults();
        Villager absent = spawn(helper, 1);
        Villager zero = spawn(helper, 3);
        Villager negative = spawn(helper, 5);

        require(apply(effects, helper, absent, new EffectData.Ignite(EffectCommonData.DEFAULT), 1f)
                == EffectResult.APPLIED, "absent Ignite must be applied no-op");
        require(!absent.hasData(ModDataAttachments.FIRE_STACK.get()),
                "absent Ignite must not materialize fire state");

        require(apply(effects, helper, zero,
                new EffectData.Flammable(EffectCommonData.DEFAULT, 0f, null), 1f)
                == EffectResult.APPLIED, "zero Flammable must be applied no-op");
        require(apply(effects, helper, zero, new EffectData.Ignite(EffectCommonData.DEFAULT), 1f)
                == EffectResult.APPLIED, "zero Ignite must be applied no-op");
        require(!zero.hasData(ModDataAttachments.FIRE_STACK.get()),
                "zero Ignite must not materialize fire state");

        require(apply(effects, helper, negative,
                new EffectData.Flammable(EffectCommonData.DEFAULT, -2f, null), 1f)
                == EffectResult.APPLIED, "negative setup must apply");
        FireStackAttachment before = negative.getExistingDataOrNull(ModDataAttachments.FIRE_STACK.get());
        require(apply(effects, helper, negative, new EffectData.Ignite(EffectCommonData.DEFAULT), 1f)
                == EffectResult.APPLIED, "negative Ignite must be applied no-op");
        require(negative.getExistingDataOrNull(ModDataAttachments.FIRE_STACK.get()) == before,
                "negative Ignite must not replace state");
        assertState(negative, -2f, false);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 30)
    public static void zeroScaleExtinguishClearsIgnitedBaseline(GameTestHelper helper) {
        Villager zombie = spawn(helper, 1);
        EffectSystem effects = EffectSystem.withDefaults();
        require(apply(effects, helper, zombie,
                new EffectData.Flammable(EffectCommonData.DEFAULT, 4f, null), 1f)
                == EffectResult.APPLIED, "flammable setup must apply");
        require(apply(effects, helper, zombie, new EffectData.Ignite(EffectCommonData.DEFAULT), 1f)
                == EffectResult.APPLIED, "Ignite setup must apply");
        require(apply(effects, helper, zombie,
                new EffectData.Extinguish(EffectCommonData.DEFAULT, -1.5f), 0f)
                == EffectResult.APPLIED, "zero-scale Extinguish must apply");
        require(!zombie.hasData(ModDataAttachments.FIRE_STACK.get()),
                "extinguishing an ignited baseline at scale zero must remove state");
        require(!hasActivity(zombie, EntityActivity.FIRE_DRYING),
                "empty extinguish state must disable drying");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void extinguishAdjustsOffFireStacks(GameTestHelper helper) {
        Villager zombie = spawn(helper, 1);
        EffectSystem effects = EffectSystem.withDefaults();
        require(apply(effects, helper, zombie,
                new EffectData.Flammable(EffectCommonData.DEFAULT, 4f, null), 1f)
                == EffectResult.APPLIED, "flammable setup must apply");
        require(apply(effects, helper, zombie,
                new EffectData.Extinguish(EffectCommonData.DEFAULT, -1.5f), 1f)
                == EffectResult.APPLIED, "off-fire Extinguish must apply");
        assertState(zombie, 2.5f, false);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void newDryingActivityWaitsForNextTickSnapshot(GameTestHelper helper) {
        Villager stand = spawn(helper, 1);
        EffectSystem effects = EffectSystem.withDefaults();
        long gameTime = stand.level().getGameTime();
        var due = TickHooks.dueActivities(new EntityActivityAttachment(), gameTime, stand.getId());

        require(apply(effects, helper, stand,
                new EffectData.Extinguish(EffectCommonData.DEFAULT, -1.5f), 1f)
                == EffectResult.APPLIED, "Extinguish must create wetness state");
        TickHooks.runDueActivities(stand, helper.getLevel(), due);

        assertState(stand, -1.5f, false);
        require(hasActivity(stand, EntityActivity.FIRE_DRYING),
                "negative stacks must enable drying after the snapshot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fireCapabilityRejectsUnsupportedTargets(GameTestHelper helper) {
        EffectSystem effects = EffectSystem.withDefaults();
        Entity projectile = helper.spawn(EntityType.ARROW, new BlockPos(1, 1, 1));
        Blaze blaze = helper.spawn(EntityType.BLAZE, new BlockPos(3, 1, 1));
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(5, 1, 1));
        blaze.setNoAi(true);

        EffectData.Flammable flammable =
                new EffectData.Flammable(EffectCommonData.DEFAULT, 1f, null);
        require(apply(effects, helper, projectile, flammable, 1f)
                        == EffectResult.SKIPPED_UNSUPPORTED,
                "nonliving targets must be unsupported");
        require(apply(effects, helper, blaze, flammable, 1f)
                        == EffectResult.SKIPPED_UNSUPPORTED,
                "fire-immune living targets must be unsupported");
        require(apply(effects, helper, stand, flammable, 1f) == EffectResult.SKIPPED_UNSUPPORTED,
                "unbound living target must be unsupported");
        require(!projectile.hasData(ModDataAttachments.FIRE_STACK.get())
                        && !blaze.hasData(ModDataAttachments.FIRE_STACK.get())
                        && !stand.hasData(ModDataAttachments.FIRE_STACK.get()),
                "unsupported targets must not materialize fire state");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void negativeStacksDryOnePerDueIntervalAndCleanUp(GameTestHelper helper) {
        Villager zombie = spawn(helper, 1);
        EffectSystem effects = EffectSystem.withDefaults();
        require(apply(effects, helper, zombie,
                new EffectData.Flammable(EffectCommonData.DEFAULT, -2f, null), 1f)
                == EffectResult.APPLIED, "negative Flammable must apply");
        assertState(zombie, -2f, false);
        require(hasActivity(zombie, EntityActivity.FIRE_DRYING),
                "negative state must enable drying");

        long start = zombie.level().getGameTime();
        long[] firstDry = {-1L};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    FireStackComponent state = state(zombie);
                    if (state != null && state.stacks() == -1f) {
                        firstDry[0] = zombie.level().getGameTime();
                    }
                    require(firstDry[0] >= 0L, "first due drying interval must add exactly one stack");
                    require(firstDry[0] >= start && firstDry[0] - start <= 20,
                            "first drying update must occur within one 20-tick interval");
                })
                .thenWaitUntil(() -> {
                    require(!zombie.hasData(ModDataAttachments.FIRE_STACK.get()),
                            "second due interval must clean up at zero");
                    require(!hasActivity(zombie, EntityActivity.FIRE_DRYING),
                            "drying activity must be removed at zero");
                    require(zombie.level().getGameTime() > firstDry[0],
                            "cleanup must occur on a later due interval");
                })
                .thenExecute(helper::succeed);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void reconcileRehydratesPersistedDryingActivity(GameTestHelper helper) {
        Villager zombie = spawn(helper, 1);
        zombie.setData(ModDataAttachments.FIRE_STACK.get(),
                new FireStackAttachment(new FireStackComponent(-1f, false)));
        require(!hasActivity(zombie, EntityActivity.FIRE_DRYING),
                "fixture must begin without derived activity");

        long dueTime = helper.getLevel().getGameTime();
        while (!EntityActivity.FIRE_DRYING.isDue(dueTime, zombie.getId())) {
            dueTime++;
        }
        long targetDueTime = dueTime;
        var dueBeforeReconcile = TickHooks.dueActivities(new EntityActivityAttachment(),
                targetDueTime, zombie.getId());
        require(!dueBeforeReconcile.contains(EntityActivity.FIRE_DRYING),
                "persisted state without derived activity must not schedule drying");

        EntityActivitySystem.reconcile(zombie);
        require(hasActivity(zombie, EntityActivity.FIRE_DRYING),
                "reconcile must rehydrate persisted negative fire state");
        var dueAfterReconcile = TickHooks.dueActivities(
                zombie.getData(ModDataAttachments.ACTIVE_SYSTEMS.get()), targetDueTime, zombie.getId());
        require(dueAfterReconcile.contains(EntityActivity.FIRE_DRYING),
                "rehydrated drying activity must be present in the due snapshot");

        helper.startSequence()
                .thenWaitUntil(() -> require(helper.getLevel().getGameTime() >= targetDueTime,
                        "server must reach the controlled drying tick"))
                .thenExecute(() -> {
                    TickHooks.runDueActivities(zombie, helper.getLevel(), dueAfterReconcile);
                    require(!zombie.hasData(ModDataAttachments.FIRE_STACK.get()),
                            "rehydrated negative state must dry to zero and clean up");
                    require(!hasActivity(zombie, EntityActivity.FIRE_DRYING),
                            "rehydrated drying activity must clean up");
                })
                .thenExecute(helper::succeed);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void unaffectedLivingEntityNeverMaterializesFireWork(GameTestHelper helper) {
        Villager zombie = spawn(helper, 1);
        long start = zombie.level().getGameTime();
        require(!zombie.hasData(ModDataAttachments.FIRE_STACK.get()),
                "unaffected entity must begin without fire state");
        require(!hasActivity(zombie, EntityActivity.FIRE_DRYING),
                "unaffected entity must begin without fire activity");

        helper.startSequence()
                .thenWaitUntil(() -> {
                    require(zombie.level().getGameTime() >= start + 20,
                            "server must advance through a drying interval");
                    require(!zombie.hasData(ModDataAttachments.FIRE_STACK.get())
                                    && !hasActivity(zombie, EntityActivity.FIRE_DRYING),
                            "unaffected entity must remain unmaterialized");
                })
                .thenExecute(helper::succeed);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void dormantSavedFireSurvivesStaleDueAndReconciliation(GameTestHelper helper) {
        Villager host = spawn(helper, 1);
        FireStackAttachment saved = new FireStackAttachment(new FireStackComponent(-2f, false));
        host.setData(ModDataAttachments.FIRE_STACK.get(), saved);
        var wrong = new CharacterIdentityAttachment();
        wrong.bind(net.minecraft.resources.ResourceLocation.parse("moonstation14:pig"));
        host.setData(ModDataAttachments.CHARACTER_IDENTITY.get(), wrong);
        EntityActivitySystem.update(host, EntityActivity.FIRE_DRYING, true);
        require(!FireStackSystem.supports(host), "mismatched host must fail closed");
        require(FireStackSystem.ignite(host, 1f) == EffectResult.SKIPPED_UNSUPPORTED,
                "stale state cannot grant ignition");
        TickHooks.runDueActivities(host, helper.getLevel(), java.util.Set.of(EntityActivity.FIRE_DRYING));
        require(host.getExistingDataOrNull(ModDataAttachments.FIRE_STACK.get()) == saved,
                "stale due work must preserve persisted fire stacks");
        EntityActivitySystem.reconcile(host);
        require(!hasActivity(host, EntityActivity.FIRE_DRYING), "reconcile removes dormant work");
        require(host.getExistingDataOrNull(ModDataAttachments.FIRE_STACK.get()) == saved,
                "reconcile must not erase dormant state");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void reconciliationRestoresOnlyIgnitedFireActivity(GameTestHelper helper) {
        Villager ignited = spawn(helper, 1);
        Villager unlit = spawn(helper, 3);
        require(FireStackSystem.flammable(ignited, 2f, null, 1f) == EffectResult.APPLIED,
                "ignited fixture setup must apply");
        require(FireStackSystem.ignite(ignited, 1f) == EffectResult.APPLIED,
                "ignited fixture must ignite");
        FireStackComponent savedIgnited = state(ignited);
        require(savedIgnited != null && savedIgnited.stacks() > 0f && savedIgnited.ignited(),
                "fixture must hold positive ignited saved stacks");

        // Simulate loading the persisted attachment before derived activity is rebuilt.
        EntityActivitySystem.update(ignited, EntityActivity.FIRE_DRYING, false);
        require(!hasActivity(ignited, EntityActivity.FIRE_DRYING),
                "reload fixture must begin without derived fire activity");
        EntityActivitySystem.reconcile(ignited);
        require(hasActivity(ignited, EntityActivity.FIRE_DRYING),
                "reconcile must restore activity for positive ignited fire stacks");
        require(state(ignited).equals(savedIgnited),
                "reconciliation must not rerun the fire hotspot or mutate saved stacks");

        require(FireStackSystem.flammable(unlit, 2f, null, 1f) == EffectResult.APPLIED,
                "unlit fixture setup must apply");
        FireStackComponent savedUnlit = state(unlit);
        require(savedUnlit != null && savedUnlit.stacks() > 0f && !savedUnlit.ignited(),
                "unlit fixture must hold positive unlit stacks");
        EntityActivitySystem.reconcile(unlit);
        require(!hasActivity(unlit, EntityActivity.FIRE_DRYING),
                "positive unlit stacks must not reconcile to drying activity");

        require(FireStackSystem.extinguish(ignited, 0f, 1f) == EffectResult.APPLIED,
                "explicit Extinguish must apply");
        require(!ignited.hasData(ModDataAttachments.FIRE_STACK.get())
                        && !hasActivity(ignited, EntityActivity.FIRE_DRYING),
                "explicit Extinguish must remove fire attachment and activity");
        helper.succeed();
    }

    private static Villager spawn(GameTestHelper helper, int x) {
        Villager stand = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 1, 1));
        stand.setNoAi(true);
        // The 1x1x1 empty template has no floor at this position; keep the fixture in place.
        stand.setNoGravity(true);
        CharacterIdentitySystem.enroll(stand, helper.getLevel(), ModCharacters.HUMAN_ID);
        return stand;
    }

    private static String fixtureDiagnostics(Villager villager, float healthBefore) {
        DamageSource lastDamage = villager.getLastDamageSource();
        return "health=" + villager.getHealth() + " (before=" + healthBefore + ")"
                + ", position=" + villager.position()
                + ", lastDamage=" + (lastDamage == null ? "none/unknown" : lastDamage.getMsgId())
                + ", onFire=" + villager.isOnFire()
                + ", fireTicks=" + villager.getRemainingFireTicks();
    }

    private static EffectResult apply(EffectSystem effects, GameTestHelper helper,
                                      Entity zombie, EffectData effect, float scale) {
        return effects.apply(effect, new EffectContext(helper.getLevel(), zombie, scale,
                RandomSource.create(7L), ConditionContext.unavailable(), EffectCause.MANUAL));
    }

    private static FireStackComponent state(Villager zombie) {
        FireStackAttachment attachment =
                zombie.getExistingDataOrNull(ModDataAttachments.FIRE_STACK.get());
        return attachment == null ? null : attachment.toComponent();
    }

    private static void assertState(Villager zombie, float stacks, boolean ignited) {
        FireStackComponent state = state(zombie);
        require(state != null && state.stacks() == stacks && state.ignited() == ignited,
                "expected fire state stacks=" + stacks + ", ignited=" + ignited + " but got " + state);
    }

    private static boolean hasActivity(Villager zombie, EntityActivity activity) {
        var active = zombie.getExistingDataOrNull(ModDataAttachments.ACTIVE_SYSTEMS.get());
        return active != null && active.isActive(activity);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
