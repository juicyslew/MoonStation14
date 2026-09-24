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
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
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
        ArmorStand zombie = spawn(helper, 1);
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
        ArmorStand zombie = spawn(helper, 1);
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
        require(zombie.isOnFire() == onFireBefore
                        && zombie.getRemainingFireTicks() == ticksBefore,
                "Ignite must not touch vanilla fire state");

        require(apply(effects, helper, zombie, new EffectData.Ignite(EffectCommonData.DEFAULT), .01f)
                == EffectResult.APPLIED, "repeated Ignite must be applied idempotently");
        assertState(zombie, 2f, true);

        long start = zombie.level().getGameTime();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    require(zombie.level().getGameTime() >= start + 40,
                            "server must advance beyond twenty ticks");
                    require(zombie.getHealth() == healthBefore,
                            "authoritative Ignite must not cause vanilla burn damage");
                    require(zombie.isOnFire() == onFireBefore
                                    && zombie.getRemainingFireTicks() == ticksBefore,
                            "Ignite must leave vanilla fire state unchanged over time");
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
        ArmorStand absent = spawn(helper, 1);
        ArmorStand zero = spawn(helper, 3);
        ArmorStand negative = spawn(helper, 5);

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
        ArmorStand zombie = spawn(helper, 1);
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
        ArmorStand zombie = spawn(helper, 1);
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
        ArmorStand stand = spawn(helper, 1);
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
        blaze.setNoAi(true);

        EffectData.Flammable flammable =
                new EffectData.Flammable(EffectCommonData.DEFAULT, 1f, null);
        require(apply(effects, helper, projectile, flammable, 1f)
                        == EffectResult.SKIPPED_UNSUPPORTED,
                "nonliving targets must be unsupported");
        require(apply(effects, helper, blaze, flammable, 1f)
                        == EffectResult.SKIPPED_UNSUPPORTED,
                "fire-immune living targets must be unsupported");
        require(!projectile.hasData(ModDataAttachments.FIRE_STACK.get())
                        && !blaze.hasData(ModDataAttachments.FIRE_STACK.get()),
                "unsupported targets must not materialize fire state");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void negativeStacksDryOnePerDueIntervalAndCleanUp(GameTestHelper helper) {
        ArmorStand zombie = spawn(helper, 1);
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
        ArmorStand zombie = spawn(helper, 1);
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
        ArmorStand zombie = spawn(helper, 1);
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

    private static ArmorStand spawn(GameTestHelper helper, int x) {
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(x, 1, 1));
        stand.setNoGravity(true);
        return stand;
    }

    private static EffectResult apply(EffectSystem effects, GameTestHelper helper,
                                      Entity zombie, EffectData effect, float scale) {
        return effects.apply(effect, new EffectContext(helper.getLevel(), zombie, scale,
                RandomSource.create(7L), ConditionContext.unavailable(), EffectCause.MANUAL));
    }

    private static FireStackComponent state(ArmorStand zombie) {
        FireStackAttachment attachment =
                zombie.getExistingDataOrNull(ModDataAttachments.FIRE_STACK.get());
        return attachment == null ? null : attachment.toComponent();
    }

    private static void assertState(ArmorStand zombie, float stacks, boolean ignited) {
        FireStackComponent state = state(zombie);
        require(state != null && state.stacks() == stacks && state.ignited() == ignited,
                "expected fire state stacks=" + stacks + ", ignited=" + ignited + " but got " + state);
    }

    private static boolean hasActivity(ArmorStand zombie, EntityActivity activity) {
        var active = zombie.getExistingDataOrNull(ModDataAttachments.ACTIVE_SYSTEMS.get());
        return active != null && active.isActive(activity);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
