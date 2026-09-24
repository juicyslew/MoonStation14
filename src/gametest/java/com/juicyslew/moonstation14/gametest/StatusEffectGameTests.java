package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.eventhooks.ModEventHooks;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.component.codec.json.EffectCommonData;
import com.juicyslew.moonstation14.component.codec.json.EffectData;
import com.juicyslew.moonstation14.component.codec.json.StatusEffectDuration;
import com.juicyslew.moonstation14.eventhooks.TickHooks;
import com.juicyslew.moonstation14.ms14.effect.ConditionContext;
import com.juicyslew.moonstation14.ms14.effect.EffectCause;
import com.juicyslew.moonstation14.ms14.effect.EffectContext;
import com.juicyslew.moonstation14.ms14.effect.EffectResult;
import com.juicyslew.moonstation14.ms14.effect.EffectSystem;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.activity.EntityActivity;
import com.juicyslew.moonstation14.ms14.activity.EntityActivityAttachment;
import com.juicyslew.moonstation14.ms14.activity.EntityActivitySystem;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.status_effect.IStatusEffectTrait;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.juicyslew.moonstation14.ms14.status_effect.MovementSpeedProjection;
import com.juicyslew.moonstation14.ms14.status_effect.LivingEntityStatusEffectLifecycle;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectAttachment;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectChangeKind;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectClearReport;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectInstance;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectOperation;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectPayload;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectReduction;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectSystem;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import com.mojang.authlib.GameProfile;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.Map;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StatusEffectGameTests {
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.StatusEffectData> JITTER =
            ModStatusEffects.createKey("jitter");
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.StatusEffectData> DRUNK =
            ModStatusEffects.createKey("statuseffectdrunk");
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.StatusEffectData> KNOCKDOWN =
            ModStatusEffects.createKey("knockdown");
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.StatusEffectData> STUNNED =
            ModStatusEffects.createKey("statuseffectstunned");

    private StatusEffectGameTests() {
    }

    // Status lifecycle tests invoke the authoritative scheduler seam explicitly.
    // GameTest clock advancement does not guarantee that a spawned ArmorStand is
    // ticked, especially when tests are activated in parallel placements.

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void unaffectedEntity(GameTestHelper helper) {
        ArmorStand stand = spawn(helper);
        assertNoAttachment(stand);
        assertNoActivity(stand);
        long initialGameTime = stand.level().getGameTime();

        helper.startSequence()
                .thenWaitUntil(() -> {
                    require(stand.level().getGameTime() > initialGameTime,
                            "server must reach a subsequent game tick");
                    assertNoAttachment(stand);
                    assertNoActivity(stand);
                    StatusEffectReduction removed = apply(
                            stand, StatusEffectOperation.REMOVE, OptionalInt.of(1), 0);
                    require(removed.kind() == StatusEffectChangeKind.UNCHANGED,
                            "removing an absent status must be unchanged");
                    assertNoAttachment(stand);
                })
                .thenExecute(helper::succeed);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void delayedFiniteLifecycle(GameTestHelper helper) {
        ArmorStand stand = spawn(helper);
        helper.startSequence()
                .thenExecute(() -> {
                    apply(stand, StatusEffectOperation.ADD, OptionalInt.of(2), 2);
                    assertStatus(stand, StatusEffectInstance.pendingFinite(2, 2));
                })
                .thenExecute(() -> {
                    runStatusTick(stand, helper.getLevel());
                    assertStatus(stand, StatusEffectInstance.pendingFinite(1, 2));
                })
                .thenExecute(() -> {
                    runStatusTick(stand, helper.getLevel());
                    assertStatus(stand, StatusEffectInstance.activeFinite(2));
                })
                .thenExecute(() -> {
                    runStatusTick(stand, helper.getLevel());
                    assertStatus(stand, StatusEffectInstance.activeFinite(1));
                })
                .thenExecute(() -> {
                    runStatusTick(stand, helper.getLevel());
                    assertAbsent(stand);
                })
                .thenExecute(() -> {
                    runStatusTick(stand, helper.getLevel());
                    assertAbsent(stand);
                })
                .thenExecute(helper::succeed);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void activityGateControlsStatusTick(GameTestHelper helper) {
        ArmorStand stand = spawn(helper);
        helper.startSequence()
                .thenExecute(() -> {
                    StatusEffectAttachment attachment = new StatusEffectAttachment(
                            Map.of(JITTER, StatusEffectInstance.activeFinite(2)));
                    // Deliberately bypass the provider so the derived activity index is absent.
                    stand.setData(ModDataAttachments.STATUS_EFFECT.get(), attachment);
                    assertNoActivity(stand);
                })
                .thenExecute(() -> {
                    EnumSet<EntityActivity> due = TickHooks.dueActivities(new EntityActivityAttachment(),
                            helper.getLevel().getGameTime(), stand.getId());
                    require(due.isEmpty(), "absent activity must not schedule status work");
                    TickHooks.runDueActivities(stand, helper.getLevel(), due);
                    assertStatus(stand, StatusEffectInstance.activeFinite(2));
                    assertNoActivity(stand);
                })
                .thenExecute(() -> {
                    EntityActivitySystem.reconcile(stand);
                    assertActivity(stand, EntityActivity.STATUS_EFFECT);
                })
                .thenExecute(() -> {
                    long dueTime = nextDueTime(EntityActivity.STATUS_EFFECT, stand.getId(),
                            helper.getLevel().getGameTime());
                    EnumSet<EntityActivity> due = TickHooks.dueActivities(
                            stand.getData(ModDataAttachments.ACTIVE_SYSTEMS.get()), dueTime, stand.getId());
                    require(due.equals(EnumSet.of(EntityActivity.STATUS_EFFECT)),
                            "reconciled status activity must be due in its controlled bucket");
                    TickHooks.runDueActivities(stand, helper.getLevel(), due);
                    assertStatus(stand, StatusEffectInstance.activeFinite(1));
                })
                .thenExecute(helper::succeed);
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void staggeredReagentDispatch(GameTestHelper helper) {
        ArmorStand bodyOnly = spawn(helper);
        ArmorStand stomachOnly = spawn(helper);
        var milk = ModReagents.createKey("milk");
        long[] bodyDueTime = {-1L};
        long[] stomachDueTime = {-1L};
        helper.startSequence()
                .thenExecute(() -> {
                    ReagentAttachment reagent = MS14Provider.getDetached(bodyOnly, MS14Bridges.REAGENT);
                    var before = MS14Provider.snapshot(reagent);
                    reagent.specificAdd(milk, 1f, 200_000f);
                    require(MS14Provider.updateIfChanged(
                                    bodyOnly, MS14Bridges.REAGENT, before, reagent),
                            "adding body milk must be a change-only update");
                    assertActivity(bodyOnly, EntityActivity.REAGENT_METABOLISM);

                    ReagentAttachment stomach = new ReagentAttachment(Map.of(milk, 1f));
                    MS14Provider.update(stomachOnly, MS14Bridges.STOMACH, stomach);
                    EntityActivitySystem.update(stomachOnly, EntityActivity.REAGENT_METABOLISM, true);
                    assertActivity(stomachOnly, EntityActivity.REAGENT_METABOLISM);

                    bodyDueTime[0] = nextDueTime(EntityActivity.REAGENT_METABOLISM, bodyOnly.getId(),
                            bodyOnly.level().getGameTime());
                    stomachDueTime[0] = nextDueTime(EntityActivity.REAGENT_METABOLISM, stomachOnly.getId(),
                            stomachOnly.level().getGameTime());
                    require(reagentAmount(bodyOnly, milk) == 1f,
                            "body milk must start at exactly one unit");
                    require(MS14Provider.get(stomachOnly, MS14Bridges.STOMACH).getMap().get(milk) == 1f,
                            "stomach milk must start at exactly one unit");

                    bodyDueTime[0] = nextDueTime(EntityActivity.REAGENT_METABOLISM, bodyOnly.getId(),
                            bodyOnly.level().getGameTime());
                    EnumSet<EntityActivity> bodyDueBefore = TickHooks.dueActivities(
                            bodyOnly.getData(ModDataAttachments.ACTIVE_SYSTEMS.get()), bodyDueTime[0] - 1,
                            bodyOnly.getId());
                    require(bodyDueBefore.isEmpty(), "body reagent activity must not be due before its bucket");
                    TickHooks.runDueActivities(bodyOnly, helper.getLevel(), bodyDueBefore);
                    require(reagentAmount(bodyOnly, milk) == 1f,
                            "body milk must remain unchanged before its due tick");

                    EnumSet<EntityActivity> stomachDueBefore = TickHooks.dueActivities(
                            stomachOnly.getData(ModDataAttachments.ACTIVE_SYSTEMS.get()), stomachDueTime[0] - 1,
                            stomachOnly.getId());
                    require(stomachDueBefore.isEmpty(), "stomach reagent activity must not be due before its bucket");
                    TickHooks.runDueActivities(stomachOnly, helper.getLevel(), stomachDueBefore);
                    require(MS14Provider.get(stomachOnly, MS14Bridges.STOMACH).getMap().get(milk) == 1f,
                            "stomach milk must remain unchanged before its due tick");

                    EnumSet<EntityActivity> bodyDue = TickHooks.dueActivities(
                            bodyOnly.getData(ModDataAttachments.ACTIVE_SYSTEMS.get()), bodyDueTime[0], bodyOnly.getId());
                    require(bodyDue.equals(EnumSet.of(EntityActivity.REAGENT_METABOLISM)),
                            "body reagent activity must be due in its controlled bucket");
                    TickHooks.runDueActivities(bodyOnly, helper.getLevel(), bodyDue);
                    require(reagentAmount(bodyOnly, milk) == 1f,
                            "shared body milk must not run the digestion-only metabolism stage");

                    EnumSet<EntityActivity> stomachDue = TickHooks.dueActivities(
                            stomachOnly.getData(ModDataAttachments.ACTIVE_SYSTEMS.get()), stomachDueTime[0],
                            stomachOnly.getId());
                    require(stomachDue.equals(EnumSet.of(EntityActivity.REAGENT_METABOLISM)),
                            "stomach reagent activity must be due in its controlled bucket");
                    TickHooks.runDueActivities(stomachOnly, helper.getLevel(), stomachDue);
                    float stomachAmount = MS14Provider.get(stomachOnly, MS14Bridges.STOMACH)
                            .getMap().getOrDefault(milk, 0f);
                    require(Math.abs(stomachAmount - .5f) < .0001f,
                            "stomach digestion must consume exactly the configured half-unit rate at its due tick");
                    require(reagentAmount(stomachOnly, milk) == 0f,
                            "stomach digestion must not route milk through shared body metabolism");
                    assertActivity(stomachOnly, EntityActivity.REAGENT_METABOLISM);
                    helper.succeed();
                });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void permanentExplicitRemove(GameTestHelper helper) {
        ArmorStand stand = spawn(helper);
        helper.startSequence()
                .thenExecute(() -> {
                    StatusEffectReduction applied = apply(
                            stand, StatusEffectOperation.ADD, OptionalInt.empty(), 0);
                    require(applied.kind() == StatusEffectChangeKind.CREATED,
                            "permanent application must create the status");
                    assertStatus(stand, StatusEffectInstance.activePermanent());

                    StatusEffectReduction removed = apply(
                            stand, StatusEffectOperation.REMOVE, OptionalInt.of(1), 0);
                    require(removed.kind() == StatusEffectChangeKind.REMOVED,
                            "explicit permanent removal must be a REMOVED reduction");
                    assertAbsent(stand);
                })
                .thenExecute(() -> {
                    runStatusTick(stand, helper.getLevel());
                    assertAbsent(stand);
                })
                .thenExecute(helper::succeed);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void playerDeathPolicyClearsAllStatusesAndProjection(GameTestHelper helper) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "death-policy-test"), ClientInformation.createDefault());
        player.setPos(1, 1, 1);
        StatusEffectAttachment seeded = new StatusEffectAttachment();
        LivingEntityStatusEffectLifecycle lifecycle =
                new LivingEntityStatusEffectLifecycle(player, helper.getLevel());
        ResourceKey<com.juicyslew.moonstation14.component.codec.json.StatusEffectData> movement =
                ModStatusEffects.createKey("ReagentSpeedStatusEffect");
        helper.startSequence()
                .thenExecute(() -> {
                    StatusEffectSystem.apply(seeded, PrototypeRuntime.serverStatusEffects(), lifecycle, movement,
                            StatusEffectOperation.SET, OptionalInt.empty(), 0,
                            new StatusEffectPayload.MovementSpeedModifier(.65f));
                    StatusEffectSystem.apply(seeded, PrototypeRuntime.serverStatusEffects(), lifecycle, JITTER,
                            StatusEffectOperation.SET, OptionalInt.of(4), 2);
                    StatusEffectSystem.apply(seeded, PrototypeRuntime.serverStatusEffects(), lifecycle, DRUNK,
                            StatusEffectOperation.SET, OptionalInt.empty(), 0);

                    EntityActivitySystem.update(player, MS14Bridges.STATUS_EFFECT, seeded);
                    require(!seeded.isEmpty(), "death-policy fixture must seed statuses");
                    require(player.getAttribute(Attributes.MOVEMENT_SPEED)
                                    .getModifier(MovementSpeedProjection.modifierId(movement)) != null,
                            "death-policy fixture must seed a transient movement projection");
                    assertActivity(player, EntityActivity.STATUS_EFFECT);

                    StatusEffectClearReport report = ModEventHooks.applyPlayerDeathStatusPolicy(player, seeded);
                    require(report.changes().size() == 3,
                            "death policy must clear active, pending, and permanent statuses");
                    require(seeded.isEmpty(), "respawned player must not retain seeded statuses");
                    require(player.getAttribute(Attributes.MOVEMENT_SPEED)
                                    .getModifier(MovementSpeedProjection.modifierId(movement)) == null,
                            "respawned player must not retain a transient movement projection");
                    assertNoActivity(player);
                    helper.succeed();
                });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void serverEffectHandlersUseAuthoritativeStatusAttachment(GameTestHelper helper) {
        ArmorStand stand = spawn(helper);
        EffectSystem effects = EffectSystem.withDefaults();
        EffectContext context = new EffectContext(helper.getLevel(), stand, 1f,
                RandomSource.create(11L), ConditionContext.unavailable(), EffectCause.MANUAL);

        EffectResult modified = effects.apply(new EffectData.ModifyStatusEffect(
                EffectCommonData.DEFAULT, "statuseffectdrowsiness", StatusEffectDuration.finite(.1f),
                StatusEffectOperation.SET, 0f), context);
        EffectResult drunk = effects.apply(new EffectData.Drunk(EffectCommonData.DEFAULT, 1f), context);
        EffectResult jitter = effects.apply(new EffectData.Jitter(
                EffectCommonData.DEFAULT, 301f, .5f, 1f, true), context);

        require(modified == EffectResult.APPLIED, "ModifyStatusEffect must apply on the server");
        require(drunk == EffectResult.APPLIED, "Drunk must apply on the server");
        require(jitter == EffectResult.APPLIED, "Jitter must apply on the server");
        StatusEffectAttachment status = MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT);
        require(status.get(ModStatusEffects.createKey("statuseffectdrowsiness")).orElseThrow().isActive(),
                "ModifyStatusEffect must attach its resolved status");
        require(status.get(ModStatusEffects.createKey("statuseffectdrunk")).orElseThrow()
                        .remainingDurationTicks().orElseThrow() == 20,
                "Drunk must use the canonical accumulated status duration");
        StatusEffectInstance jitterInstance = status.get(JITTER).orElseThrow();
        require(jitterInstance.isActive(), "Jitter must activate immediately");
        require(jitterInstance.payload().equals(new StatusEffectPayload.Jitter(300f, 1f)),
                "Jitter payload must be clamped before attachment");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void popupMessageRunsServerSideWithoutCreatingAttachments(GameTestHelper helper) {
        ArmorStand stand = spawn(helper);
        EffectContext context = new EffectContext(helper.getLevel(), stand, 1f,
                RandomSource.create(17L), ConditionContext.unavailable(), EffectCause.MANUAL);

        EffectResult result = EffectSystem.withDefaults().apply(new EffectData.PopupMessage(
                EffectCommonData.DEFAULT, EffectData.PopupRecipients.Local,
                EffectData.PopupMethod.PopupEntity, EffectData.PopupVisualType.Small,
                java.util.List.of("effect-sleepy")), context);

        require(result == EffectResult.APPLIED, "PopupMessage must apply on the server");
        require(!stand.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                "PopupMessage must not create a status attachment");
        require(!stand.hasData(ModDataAttachments.REAGENT.get()),
                "PopupMessage must not create a reagent attachment");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void effectContextRejectsEntityFromAnotherLevel(GameTestHelper helper) {
        ArmorStand stand = spawn(helper);
        var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        require(nether != null, "the nether test level must exist");
        try {
            new EffectContext(nether, stand, 1f, RandomSource.create(23L),
                    ConditionContext.unavailable(), EffectCause.MANUAL);
            throw new GameTestAssertException("cross-level EffectContext must be rejected");
        } catch (IllegalArgumentException expected) {
            helper.succeed();
        }
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void statusDerivedHandlersCoverOperationsAndQuietNoOps(GameTestHelper helper) {
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(1, 1, 1));
        EffectSystem effects = EffectSystem.withDefaults();
        EffectContext context = new EffectContext(helper.getLevel(), stand, .5f,
                RandomSource.create(17L), ConditionContext.unavailable(), EffectCause.MANUAL);
        ResourceKey<com.juicyslew.moonstation14.component.codec.json.StatusEffectData> drowsiness =
                ModStatusEffects.createKey("statuseffectdrowsiness");

        require(effects.apply(new EffectData.ModifyStatusEffect(EffectCommonData.DEFAULT,
                        "statuseffectdrowsiness", StatusEffectDuration.finite(2f),
                        StatusEffectOperation.UPDATE, .25f), context) == EffectResult.APPLIED,
                "Modify UPDATE must apply");
                require(MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT).get(drowsiness).orElseThrow()
                        .equals(StatusEffectInstance.pendingFinite(5, 20)),
                "Modify UPDATE must scale duration while preserving unscaled delay");

        require(effects.apply(new EffectData.ModifyStatusEffect(EffectCommonData.DEFAULT,
                        "statuseffectdrowsiness", StatusEffectDuration.finite(.5f),
                        StatusEffectOperation.ADD, .1f), context) == EffectResult.APPLIED,
                "Modify ADD must apply");
                require(MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT).get(drowsiness).orElseThrow()
                        .equals(StatusEffectInstance.pendingFinite(2, 25)),
                "Modify ADD must scale and accumulate duration while choosing the earliest pending delay");

        require(effects.apply(new EffectData.ModifyStatusEffect(EffectCommonData.DEFAULT,
                        "statuseffectdrowsiness", StatusEffectDuration.permanent(),
                        StatusEffectOperation.SET, .5f), context) == EffectResult.APPLIED,
                "Modify SET permanent must apply");
                require(MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT).get(drowsiness).orElseThrow()
                        .equals(StatusEffectInstance.pendingPermanent(2)),
                "Modify SET must preserve permanent duration");

        require(effects.apply(new EffectData.ModifyStatusEffect(EffectCommonData.DEFAULT,
                        "statuseffectdrowsiness", StatusEffectDuration.finite(.1f),
                        StatusEffectOperation.REMOVE, 0f), context) == EffectResult.APPLIED,
                "Modify REMOVE must apply");
        require(MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT).get(drowsiness).isEmpty(),
                "Modify REMOVE must route through the reducer");

                require(effects.apply(new EffectData.ModifyStatusEffect(EffectCommonData.DEFAULT,
                        "statuseffectdrowsiness", StatusEffectDuration.finite(.1f),
                        StatusEffectOperation.SET, 0f), context) == EffectResult.APPLIED,
                "Modify SET must create when absent");
        require(MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT).get(drowsiness).orElseThrow()
                        .equals(StatusEffectInstance.activeFinite(1)),
                "Modify SET must create an absent status with scaled duration");

        require(effects.apply(new EffectData.ModifyStatusEffect(EffectCommonData.DEFAULT,
                        "status_missing_for_effect_test", StatusEffectDuration.finite(1f),
                        StatusEffectOperation.UPDATE, 0f), context) == EffectResult.FAILED,
                "missing Modify prototype must fail through EffectSystem");
        require(MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT)
                        .get(ModStatusEffects.createKey("status_missing_for_effect_test")).isEmpty(),
                "missing Modify prototype must not create dangling state");

        require(effects.apply(new EffectData.Drunk(EffectCommonData.DEFAULT, 1f), context)
                        == EffectResult.APPLIED, "first Drunk application must apply");
        require(effects.apply(new EffectData.Drunk(EffectCommonData.DEFAULT, 1f), context)
                        == EffectResult.APPLIED, "second Drunk application must apply");
        require(MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT)
                        .get(ModStatusEffects.createKey("statuseffectdrunk")).orElseThrow()
                        .remainingDurationTicks().orElseThrow() == 20,
                "Drunk applications must scale and accumulate");

        require(effects.apply(new EffectData.Jitter(EffectCommonData.DEFAULT, 10f, 4f, 1f, true), context)
                        == EffectResult.APPLIED, "initial Jitter must apply");
        require(effects.apply(new EffectData.Jitter(EffectCommonData.DEFAULT, 12f, 2f, .5f, true), context)
                        == EffectResult.APPLIED, "refreshing Jitter must apply");
        StatusEffectInstance refreshed = MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT)
                .get(JITTER).orElseThrow();
        require(refreshed.remainingDurationTicks().orElseThrow() == 10,
                "Jitter refresh must scale duration and take the maximum remaining duration");
        require(refreshed.payload().equals(new StatusEffectPayload.Jitter(12f, 4f)),
                "Jitter refresh must merge payload coordinates by maximum");
        require(effects.apply(new EffectData.Jitter(EffectCommonData.DEFAULT, 2f, 8f, .5f, false), context)
                        == EffectResult.APPLIED, "accumulating Jitter must apply");
        StatusEffectInstance accumulated = MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT)
                .get(JITTER).orElseThrow();
        require(accumulated.remainingDurationTicks().orElseThrow() == 15,
                "non-refreshing Jitter must scale and accumulate duration");
        require(accumulated.payload().equals(new StatusEffectPayload.Jitter(12f, 8f)),
                "accumulating Jitter must merge payload maxima");

        ArmorStand zeroTarget = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(4, 1, 1));
        EffectContext zeroContext = new EffectContext(helper.getLevel(), zeroTarget, 1f,
                RandomSource.create(18L), ConditionContext.unavailable(), EffectCause.MANUAL);
        require(effects.apply(new EffectData.Drunk(EffectCommonData.DEFAULT, 0f), zeroContext)
                        == EffectResult.APPLIED, "zero duration must be an applied no-op");
        require(!zeroTarget.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                "zero duration must not create an attachment");

        require(effects.apply(new EffectData.ModifyBleed(EffectCommonData.DEFAULT, 1f), zeroContext)
                        == EffectResult.SKIPPED_UNSUPPORTED,
                "an unregistered effect must be explicitly unsupported");
        require(!zeroTarget.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                "an unsupported effect must not materialize or mutate status state");

        var unsupported = helper.spawn(EntityType.ITEM, new BlockPos(7, 1, 1));
        EffectContext unsupportedContext = new EffectContext(helper.getLevel(), unsupported, 1f,
                RandomSource.create(19L), ConditionContext.unavailable(), EffectCause.MANUAL);
        require(effects.apply(new EffectData.Drunk(EffectCommonData.DEFAULT, 1f), unsupportedContext)
                        == EffectResult.SKIPPED_UNSUPPORTED,
                "non-status-capable targets must be quietly unsupported");
        require(!unsupported.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                "unsupported targets must not receive an attachment");

        ArmorStand genericTarget = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(10, 1, 1));
        EffectContext genericContext = new EffectContext(helper.getLevel(), genericTarget, 1f,
                RandomSource.create(20L), ConditionContext.unavailable(), EffectCause.MANUAL);
        require(effects.apply(new EffectData.GenericStatusEffect(EffectCommonData.DEFAULT,
                        "jitter", "", StatusEffectOperation.SET, 2f), genericContext)
                        == EffectResult.APPLIED, "legacy SET absent must be a quiet success");
        require(!genericTarget.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                "legacy SET absent must not materialize an attachment");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void knockdownUsesDedicatedStatusAndRejectsPhysicalProjections(GameTestHelper helper) {
        EffectSystem effects = EffectSystem.withDefaults();
        ArmorStand stand = spawn(helper);
        EffectContext context = new EffectContext(helper.getLevel(), stand, .5f,
                RandomSource.create(24L), ConditionContext.unavailable(), EffectCause.MANUAL);

        require(effects.apply(new EffectData.ModifyKnockdown(EffectCommonData.DEFAULT,
                        StatusEffectDuration.finite(2f), StatusEffectOperation.UPDATE, .251f, false, false), context)
                        == EffectResult.APPLIED, "knockdown UPDATE must apply");
        require(MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT).get(KNOCKDOWN).orElseThrow()
                        .equals(StatusEffectInstance.pendingFinite(6, 20)),
                "knockdown must use scaled duration and unscaled delay");
        require(MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT).get(STUNNED).isEmpty(),
                "knockdown must not alias the stunned status");

        require(effects.apply(new EffectData.ModifyKnockdown(EffectCommonData.DEFAULT,
                        StatusEffectDuration.finite(.5f), StatusEffectOperation.ADD, .1f, false, false), context)
                        == EffectResult.APPLIED, "knockdown ADD must use the reducer");
        require(MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT).get(KNOCKDOWN).orElseThrow()
                        .equals(StatusEffectInstance.pendingFinite(2, 25)),
                "knockdown ADD must accumulate and choose the earliest pending delay");

        require(effects.apply(new EffectData.ModifyKnockdown(EffectCommonData.DEFAULT,
                        StatusEffectDuration.permanent(), StatusEffectOperation.SET, .5f, false, false), context)
                        == EffectResult.APPLIED, "knockdown SET permanent must apply");
        require(MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT).get(KNOCKDOWN).orElseThrow()
                        .equals(StatusEffectInstance.pendingPermanent(2)),
                "knockdown SET must preserve local pending/permanent reducer semantics");
        require(effects.apply(new EffectData.ModifyKnockdown(EffectCommonData.DEFAULT,
                        StatusEffectDuration.finite(.1f), StatusEffectOperation.REMOVE, 0f, true, true), context)
                        == EffectResult.APPLIED, "knockdown REMOVE must ignore physical flags");
        require(MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT).get(KNOCKDOWN).isEmpty(),
                "knockdown REMOVE must remove the dedicated status");

        ArmorStand finite = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(4, 1, 1));
        EffectContext finiteContext = contextFor(helper, finite);
        require(effects.apply(new EffectData.ModifyKnockdown(EffectCommonData.DEFAULT,
                        StatusEffectDuration.finite(2f), StatusEffectOperation.UPDATE, 0f, false, false), finiteContext)
                        == EffectResult.APPLIED, "finite knockdown must apply");
        require(effects.apply(new EffectData.ModifyKnockdown(EffectCommonData.DEFAULT,
                        StatusEffectDuration.finite(1f), StatusEffectOperation.REMOVE, 0f, true, true), finiteContext)
                        == EffectResult.APPLIED, "finite knockdown REMOVE must apply with both flags");
        require(MS14Provider.get(finite, MS14Bridges.STATUS_EFFECT).get(KNOCKDOWN).orElseThrow()
                        .remainingDurationTicks().orElseThrow() == 20,
                "finite knockdown REMOVE must reduce only its own timer");

        ArmorStand rejected = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(7, 1, 1));
        EffectContext rejectedContext = contextFor(helper, rejected);
        for (StatusEffectOperation operation : List.of(StatusEffectOperation.UPDATE,
                StatusEffectOperation.ADD, StatusEffectOperation.SET)) {
            require(effects.apply(new EffectData.ModifyKnockdown(EffectCommonData.DEFAULT,
                            StatusEffectDuration.finite(1f), operation, 0f,
                            operation != StatusEffectOperation.ADD, operation != StatusEffectOperation.UPDATE),
                    rejectedContext) == EffectResult.SKIPPED_UNSUPPORTED,
                    "crawling/drop must reject non-removal knockdown operations");
        }
        require(!rejected.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                "physical projection rejection must not mutate status state");

        ArmorStand zero = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(10, 1, 1));
        EffectContext zeroContext = contextFor(helper, zero);
        require(effects.apply(new EffectData.ModifyKnockdown(EffectCommonData.DEFAULT,
                        StatusEffectDuration.finite(1f), StatusEffectOperation.UPDATE, 0f, true, true),
                zeroContext.withScale(0f)) == EffectResult.APPLIED,
                "scaled-zero knockdown must be an applied no-op for a supported target");
        require(!zero.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                "scaled-zero knockdown must not create an attachment");

        ArmorStand paired = helper.spawn(EntityType.ARMOR_STAND, new BlockPos(13, 1, 1));
        EffectContext pairedContext = contextFor(helper, paired);
        require(effects.apply(new EffectData.ModifyStatusEffect(EffectCommonData.DEFAULT,
                        "statuseffectstunned", StatusEffectDuration.finite(1f),
                        StatusEffectOperation.UPDATE, 0f), pairedContext) == EffectResult.APPLIED,
                "paired stunned status must apply");
        require(effects.apply(new EffectData.ModifyKnockdown(EffectCommonData.DEFAULT,
                        StatusEffectDuration.finite(1f), StatusEffectOperation.UPDATE, 0f, false, false),
                pairedContext) == EffectResult.APPLIED, "paired knockdown must apply");
        require(effects.apply(new EffectData.ModifyStatusEffect(EffectCommonData.DEFAULT,
                        "statuseffectstunned", StatusEffectDuration.finite(1f),
                        StatusEffectOperation.REMOVE, 0f), pairedContext) == EffectResult.APPLIED,
                "stunned removal must apply");
        require(MS14Provider.get(paired, MS14Bridges.STATUS_EFFECT).get(STUNNED).isEmpty()
                        && MS14Provider.get(paired, MS14Bridges.STATUS_EFFECT).get(KNOCKDOWN).isPresent(),
                "stunned removal must not remove knockdown");
        require(effects.apply(new EffectData.ModifyKnockdown(EffectCommonData.DEFAULT,
                        StatusEffectDuration.finite(1f), StatusEffectOperation.REMOVE, 0f, false, false),
                pairedContext) == EffectResult.APPLIED,
                "knockdown removal must apply");
        require(MS14Provider.get(paired, MS14Bridges.STATUS_EFFECT).get(KNOCKDOWN).isEmpty(),
                "knockdown removal must affect only knockdown");

        var unsupported = helper.spawn(EntityType.ITEM, new BlockPos(16, 1, 1));
        EffectContext unsupportedContext = new EffectContext(helper.getLevel(), unsupported, 1f,
                RandomSource.create(25L), ConditionContext.unavailable(), EffectCause.MANUAL);
        require(effects.apply(new EffectData.ModifyKnockdown(EffectCommonData.DEFAULT,
                        StatusEffectDuration.finite(1f), StatusEffectOperation.UPDATE, 0f, false, false),
                unsupportedContext) == EffectResult.SKIPPED_UNSUPPORTED,
                "unsupported knockdown targets must be quiet no-ops");
        require(!unsupported.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                "unsupported knockdown targets must not receive an attachment");
        require(effects.apply(new EffectData.ModifyKnockdown(EffectCommonData.DEFAULT,
                        StatusEffectDuration.finite(1f), StatusEffectOperation.UPDATE, 0f, false, false),
                unsupportedContext.withScale(0f)) == EffectResult.SKIPPED_UNSUPPORTED,
                "zero-scale knockdown on an unsupported target must remain unsupported");
        require(!unsupported.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                "zero-scale unsupported knockdown must not create an attachment");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void movementSpeedModifierProjectsAndCleansIdempotently(GameTestHelper helper) {
        ArmorStand stand = spawn(helper);
        EffectSystem effects = EffectSystem.withDefaults();
        EffectContext context = new EffectContext(helper.getLevel(), stand, 1f,
                RandomSource.create(21L), ConditionContext.unavailable(), EffectCause.MANUAL);
        ResourceKey<com.juicyslew.moonstation14.component.codec.json.StatusEffectData> movement =
                ModStatusEffects.createKey("ReagentSpeedStatusEffect");
        AttributeInstance attribute = stand.getAttribute(Attributes.MOVEMENT_SPEED);
        require(attribute != null, "armor stand must expose movement speed");

        EffectData.MovementSpeedModifier delayed = new EffectData.MovementSpeedModifier(
                EffectCommonData.DEFAULT, .65f, .65f, "ReagentSpeedStatusEffect",
                StatusEffectDuration.finite(.1f), StatusEffectOperation.UPDATE, .1f);
        EffectData.MovementSpeedModifier scaled = new EffectData.MovementSpeedModifier(
                EffectCommonData.DEFAULT, .65f, .65f, "ReagentSpeedStatusEffect",
                StatusEffectDuration.finite(.1f), StatusEffectOperation.UPDATE, .1f);
        EffectData.MovementSpeedModifier removed = new EffectData.MovementSpeedModifier(
                EffectCommonData.DEFAULT, .0f, .0f, "ReagentSpeedStatusEffect",
                StatusEffectDuration.finite(.1f), StatusEffectOperation.REMOVE, 0f);
        EffectData.MovementSpeedModifier removeAll = new EffectData.MovementSpeedModifier(
                EffectCommonData.DEFAULT, .0f, .0f, "ReagentSpeedStatusEffect",
                StatusEffectDuration.permanent(), StatusEffectOperation.REMOVE, 0f);
        EffectData.MovementSpeedModifier absentFiniteSet = new EffectData.MovementSpeedModifier(
                EffectCommonData.DEFAULT, 1f, 1f, "ReagentSpeedStatusEffect",
                StatusEffectDuration.finite(.1f), StatusEffectOperation.SET, 0f);
        EffectData.MovementSpeedModifier absentPermanentSet = new EffectData.MovementSpeedModifier(
                EffectCommonData.DEFAULT, 1f, 1f, "ReagentSpeedStatusEffect",
                StatusEffectDuration.permanent(), StatusEffectOperation.SET, 0f);
        EffectData.ModifyStatusEffect modifyAbsentFiniteSet = new EffectData.ModifyStatusEffect(
                EffectCommonData.DEFAULT, "ReagentSpeedStatusEffect", StatusEffectDuration.finite(.1f),
                StatusEffectOperation.SET, 0f);
        EffectData.ModifyStatusEffect modifyAbsentPermanentSet = new EffectData.ModifyStatusEffect(
                EffectCommonData.DEFAULT, "ReagentSpeedStatusEffect", StatusEffectDuration.permanent(),
                StatusEffectOperation.SET, 0f);
        helper.startSequence()
                .thenExecute(() -> {
                    require(effects.apply(scaled, context.withScale(2f)) == EffectResult.APPLIED,
                            "scaled movement effect must apply");
                    StatusEffectInstance scaledInstance = MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT)
                            .get(movement).orElseThrow();
                    require(scaledInstance.isPending()
                                    && scaledInstance.delayTicks() == 2
                                    && scaledInstance.remainingDurationTicks().orElseThrow() == 4,
                            "movement duration must scale while delay remains unscaled");
                    require(effects.apply(removeAll, context) == EffectResult.APPLIED,
                            "scaled movement status must be removable before activation");
                    require(effects.apply(absentFiniteSet, context) == EffectResult.APPLIED,
                            "absent finite SET must create a movement status");
                    require(Math.abs(attribute.getModifier(MovementSpeedProjection.modifierId(movement)).amount()
                            + .5d) < 0.000001d,
                            "absent finite SET must project the typed definition default");
                    require(effects.apply(removed, context) == EffectResult.APPLIED,
                            "finite default movement status must be removable");
                    require(effects.apply(modifyAbsentFiniteSet, context) == EffectResult.APPLIED,
                            "ModifyStatusEffect absent finite SET must create a movement status");
                    require(Math.abs(attribute.getModifier(MovementSpeedProjection.modifierId(movement)).amount()
                            + .5d) < 0.000001d,
                            "ModifyStatusEffect absent finite SET must project the typed default");
                    require(effects.apply(removed, context) == EffectResult.APPLIED,
                            "ModifyStatusEffect finite default movement status must be removable");
                    require(effects.apply(modifyAbsentPermanentSet, context) == EffectResult.APPLIED,
                            "ModifyStatusEffect absent permanent SET must create a movement status");
                    require(Math.abs(attribute.getModifier(MovementSpeedProjection.modifierId(movement)).amount()
                            + .5d) < 0.000001d,
                            "ModifyStatusEffect absent permanent SET must project the typed default");
                    require(effects.apply(removed, context) == EffectResult.APPLIED,
                            "ModifyStatusEffect permanent default movement status must be removable");
                    require(effects.apply(absentPermanentSet, context) == EffectResult.APPLIED,
                            "absent permanent SET must create a movement status");
                    require(Math.abs(attribute.getModifier(MovementSpeedProjection.modifierId(movement)).amount()
                            + .5d) < 0.000001d,
                            "absent permanent SET must project the typed definition default");
                    require(effects.apply(removed, context) == EffectResult.APPLIED,
                            "permanent default movement status must be removable");
                    require(effects.apply(delayed, context) == EffectResult.APPLIED,
                            "equal movement modifiers must apply");
                    require(MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT).get(movement)
                                    .orElseThrow().isPending(), "delayed movement status must be pending");
                    require(attribute.getModifier(MovementSpeedProjection.modifierId(movement)) == null,
                            "pending movement status must not project a modifier");
                })
                .thenExecute(() -> {
                    runStatusTick(stand, helper.getLevel());
                    require(attribute.getModifier(MovementSpeedProjection.modifierId(movement)) == null,
                            "pending movement status must remain unprojected");
                })
                .thenExecute(() -> {
                    runStatusTick(stand, helper.getLevel());
                    var modifier = attribute.getModifier(MovementSpeedProjection.modifierId(movement));
                    require(modifier != null, "activation must project movement speed");
                    require(Math.abs(modifier.amount() + .35d) < 0.000001d,
                            "movement modifier must use multiplier minus one");
                    require(modifier.operation().name().equals("ADD_MULTIPLIED_TOTAL"),
                            "movement modifier must use multiplicative-total semantics");
                })
                .thenExecute(() -> {
                    runStatusTick(stand, helper.getLevel());
                    require(attribute.getModifier(MovementSpeedProjection.modifierId(movement)) != null,
                            "projected movement modifier must remain through the final active tick");
                })
                .thenExecute(() -> {
                    runStatusTick(stand, helper.getLevel());
                    require(MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT).get(movement).isEmpty(),
                            "finite movement status must expire at the exact boundary");
                    require(attribute.getModifier(MovementSpeedProjection.modifierId(movement)) == null,
                            "natural expiry must clean the transient modifier at the boundary");
                })
                .thenExecute(() -> {
                    EffectData.MovementSpeedModifier changed = new EffectData.MovementSpeedModifier(
                            EffectCommonData.DEFAULT, 1.25f, 1.25f, "ReagentSpeedStatusEffect",
                            StatusEffectDuration.finite(.1f), StatusEffectOperation.UPDATE, 0f);
                    require(effects.apply(changed, context) == EffectResult.APPLIED,
                            "movement payload update must apply");
                    var modifier = attribute.getModifier(MovementSpeedProjection.modifierId(movement));
                    require(modifier != null && Math.abs(modifier.amount() - .25d) < 0.000001d,
                            "latest movement payload must replace the projection");
                    var identity = modifier;
                    require(effects.apply(changed, context) == EffectResult.APPLIED,
                            "repeated equal movement update must be a no-op application");
                    require(attribute.getModifier(MovementSpeedProjection.modifierId(movement)) == identity,
                            "idempotent reconciliation must not remove and re-add a correct modifier");
                })
                .thenExecute(() -> {
                    EffectData.MovementSpeedModifier added = new EffectData.MovementSpeedModifier(
                            EffectCommonData.DEFAULT, .65f, .65f, "ReagentSpeedStatusEffect",
                            StatusEffectDuration.finite(.1f), StatusEffectOperation.ADD, 0f);
                    require(effects.apply(added, context) == EffectResult.APPLIED,
                            "movement ADD must apply");
                    require(Math.abs(attribute.getModifier(MovementSpeedProjection.modifierId(movement)).amount()
                            + .35d) < 0.000001d, "movement ADD must update its payload");
                })
                .thenExecute(() -> {
                    require(effects.apply(removed, context) == EffectResult.APPLIED,
                            "movement REMOVE must apply");
                    require(attribute.getModifier(MovementSpeedProjection.modifierId(movement)) != null,
                            "REMOVE must preserve a currently active movement payload");
                })
                .thenExecute(() -> {
                    EffectData.MovementSpeedModifier set = new EffectData.MovementSpeedModifier(
                            EffectCommonData.DEFAULT, 0f, 0f, "ReagentSpeedStatusEffect",
                            StatusEffectDuration.finite(.1f), StatusEffectOperation.SET, 0f);
                    require(effects.apply(set, context) == EffectResult.APPLIED,
                            "movement SET must apply");
                    require(attribute.getModifier(MovementSpeedProjection.modifierId(movement)) != null,
                            "SET must preserve a currently active movement payload");
                })
                .thenExecute(() -> {
                    require(effects.apply(removed, context) == EffectResult.APPLIED,
                            "final movement REMOVE must apply");
                    require(attribute.getModifier(MovementSpeedProjection.modifierId(movement)) == null,
                            "explicit removal must clean the transient modifier exactly");
                    helper.succeed();
                });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void persistedMovementProjectionRehydrates(GameTestHelper helper) {
        ArmorStand stand = spawn(helper);
        ResourceKey<com.juicyslew.moonstation14.component.codec.json.StatusEffectData> movement =
                ModStatusEffects.createKey("ReagentSpeedStatusEffect");
        MovementSpeedProjection.remove(stand, movement);
        StatusEffectSystem.apply(((IStatusEffectTrait) stand).toHandleSelf(), stand.level(), movement,
                StatusEffectOperation.UPDATE, OptionalInt.empty(), 0,
                new StatusEffectPayload.MovementSpeedModifier(.8f));
        require(stand.getAttribute(Attributes.MOVEMENT_SPEED)
                        .getModifier(MovementSpeedProjection.modifierId(movement)) != null,
                "initial active movement status must project");

        CompoundTag saved = new CompoundTag();
        stand.save(saved);
        stand.discard();
        ArmorStand reloaded = (ArmorStand) EntityType.loadEntityRecursive(saved, helper.getLevel(), entity -> entity);
        require(reloaded != null, "movement entity must reload");
        if (helper.getLevel().getEntity(reloaded.getUUID()) != null) {
            reloaded.setUUID(UUID.randomUUID());
        }
        require(helper.getLevel().addFreshEntity(reloaded), "reloaded movement entity must join the level");
        require(reloaded.getAttribute(Attributes.MOVEMENT_SPEED)
                        .getModifier(MovementSpeedProjection.modifierId(movement)) == null,
                "transient projection must not be persisted as competing state");
        helper.startSequence()
                .thenExecute(() -> {
                    runStatusTick(reloaded, helper.getLevel());
                    var modifier = reloaded.getAttribute(Attributes.MOVEMENT_SPEED)
                            .getModifier(MovementSpeedProjection.modifierId(movement));
                    require(modifier != null && Math.abs(modifier.amount() + .2d) < 0.000001d,
                            "active persisted movement state must rehydrate its projection");
                    helper.succeed();
                });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void distinctMovementStatusKeysStackWithoutCollision(GameTestHelper helper) {
        ArmorStand stand = spawn(helper);
        ResourceKey<com.juicyslew.moonstation14.component.codec.json.StatusEffectData> first =
                ModStatusEffects.createKey("test_movement_projection_first");
        ResourceKey<com.juicyslew.moonstation14.component.codec.json.StatusEffectData> second =
                ModStatusEffects.createKey("test_movement_projection_second");
        AttributeInstance attribute = stand.getAttribute(Attributes.MOVEMENT_SPEED);
        require(attribute != null, "armor stand must expose movement speed");
        double base = attribute.getBaseValue();
        MovementSpeedProjection.reconcile(stand, first, new StatusEffectPayload.MovementSpeedModifier(.8f));
        MovementSpeedProjection.reconcile(stand, second, new StatusEffectPayload.MovementSpeedModifier(1.25f));
        require(!MovementSpeedProjection.modifierId(first).equals(MovementSpeedProjection.modifierId(second)),
                "distinct status keys must have distinct deterministic IDs");
        require(attribute.getModifier(MovementSpeedProjection.modifierId(first)) != null
                        && attribute.getModifier(MovementSpeedProjection.modifierId(second)) != null,
                "both movement projections must coexist");
        require(Math.abs(attribute.getValue() - base) < 0.000001d,
                "separate movement projections must multiply rather than collide");
        MovementSpeedProjection.remove(stand, first);
        MovementSpeedProjection.remove(stand, second);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void missingDefinitionInvalidatesOnce(GameTestHelper helper) {
        ArmorStand stand = spawn(helper);
        ResourceKey<com.juicyslew.moonstation14.component.codec.json.StatusEffectData> missing =
                ModStatusEffects.createKey("deliberately_missing_game_test_status");
        helper.startSequence()
                .thenExecute(() -> {
                    MovementSpeedProjection.reconcile(stand, missing,
                            new StatusEffectPayload.MovementSpeedModifier(.65f));
                    require(stand.getAttribute(Attributes.MOVEMENT_SPEED)
                                    .getModifier(MovementSpeedProjection.modifierId(missing)) != null,
                            "test invalidation must begin with an established projection");
                    StatusEffectAttachment attachment = new StatusEffectAttachment();
                    attachment.apply(missing, StatusEffectOperation.ADD, OptionalInt.of(2), 0);
                    MS14Provider.update(stand, MS14Bridges.STATUS_EFFECT, attachment);
                })
                .thenExecute(() -> {
                    runStatusTick(stand, helper.getLevel());
                    assertAbsent(stand);
                    require(stand.getAttribute(Attributes.MOVEMENT_SPEED)
                                    .getModifier(MovementSpeedProjection.modifierId(missing)) == null,
                            "definition invalidation must clean an established movement projection");
                })
                .thenExecute(() -> {
                    runStatusTick(stand, helper.getLevel());
                    assertAbsent(stand);
                })
                .thenExecute(helper::succeed);
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void entityNbtPersistence(GameTestHelper helper) {
        ArmorStand stand = spawn(helper);
        helper.startSequence()
                .thenExecute(() -> {
                    apply(stand, StatusEffectOperation.ADD, OptionalInt.of(2), 2);
                    StatusEffectInstance expected = StatusEffectInstance.pendingFinite(2, 2);
                    assertStatus(stand, expected);

                    CompoundTag saved = new CompoundTag();
                    stand.save(saved);
                    ArmorStand reloaded = (ArmorStand) EntityType.loadEntityRecursive(
                            saved, helper.getLevel(), entity -> entity);
                    require(reloaded != null, "saved armor stand must reload");
                    require(reloaded.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                            "reloaded entity must have its status attachment before getData");
                    require(MS14Provider.get(reloaded, MS14Bridges.STATUS_EFFECT)
                                    .get(JITTER).orElseThrow(() -> new GameTestAssertException(
                                            "reloaded jitter must be present")).equals(expected),
                            "pending status instance must survive entity NBT serialization");
                    helper.succeed();
                });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void activityLifecycleAndEmptyPersistence(GameTestHelper helper) {
        ArmorStand stand = spawn(helper);
        helper.startSequence()
                .thenExecute(() -> {
                    require(!stand.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                            "untouched entity must not have status data");
                    require(!stand.hasData(ModDataAttachments.REAGENT.get()),
                            "untouched entity must not have reagent data");
                    assertNoActivity(stand);

                    apply(stand, StatusEffectOperation.ADD, OptionalInt.of(2), 0);
                    assertActivity(stand, EntityActivity.STATUS_EFFECT);

                    StatusEffectAttachment status = MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT);
                    var statusBefore = MS14Provider.snapshot(status);
                    status.remove(JITTER);
                    require(MS14Provider.updateIfChanged(
                                    stand, MS14Bridges.STATUS_EFFECT, statusBefore, status),
                            "clearing status must be a change-only update");
                    require(stand.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                            "empty status state is retained at runtime");
                    assertNoActivity(stand);

                    ReagentAttachment reagent = MS14Provider.getDetached(stand, MS14Bridges.REAGENT);
                    reagent.specificAdd(ModReagents.createKey("game_test_reagent"), 1f, 200_000f);
                    require(MS14Provider.updateIfChanged(
                                    stand, MS14Bridges.REAGENT,
                                    MS14Provider.snapshot(new ReagentAttachment()), reagent),
                            "adding reagent must be a change-only update");
                    assertActivity(stand, EntityActivity.REAGENT_METABOLISM);

                    var reagentBefore = MS14Provider.snapshot(reagent);
                    reagent.clear();
                    require(MS14Provider.updateIfChanged(
                                    stand, MS14Bridges.REAGENT, reagentBefore, reagent),
                            "emptying reagent must be a change-only update");
                    require(stand.hasData(ModDataAttachments.REAGENT.get()),
                            "empty reagent state is retained at runtime");
                    assertNoActivity(stand);

                    CompoundTag saved = new CompoundTag();
                    stand.save(saved);
                    ArmorStand reloaded = (ArmorStand) EntityType.loadEntityRecursive(
                            saved, helper.getLevel(), entity -> entity);
                    require(reloaded != null, "empty-state entity must reload");
                    require(!reloaded.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                            "empty status state must be omitted from persistence");
                    require(!reloaded.hasData(ModDataAttachments.REAGENT.get()),
                            "empty reagent state must be omitted from persistence");
                    require(!reloaded.hasData(ModDataAttachments.ACTIVE_SYSTEMS.get()),
                            "derived activity must not be persisted");
                    helper.succeed();
                });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void persistedStateReconcilesActivity(GameTestHelper helper) {
        ArmorStand stand = spawn(helper);
        ArmorStand[] reloaded = {null};
        helper.startSequence()
                .thenExecute(() -> {
                    apply(stand, StatusEffectOperation.ADD, OptionalInt.of(4), 0);
                    CompoundTag saved = new CompoundTag();
                    stand.save(saved);
                    stand.discard();
                    reloaded[0] = (ArmorStand) EntityType.loadEntityRecursive(
                            saved, helper.getLevel(), entity -> entity);
                    require(reloaded[0] != null, "nonempty entity must reload");
                    require(!reloaded[0].hasData(ModDataAttachments.ACTIVE_SYSTEMS.get()),
                            "derived activity must not load from entity data");

                    if (helper.getLevel().getEntity(reloaded[0].getUUID()) != null) {
                        reloaded[0].setUUID(UUID.randomUUID());
                    }
                    require(helper.getLevel().addFreshEntity(reloaded[0]),
                            "reloaded entity must be added to the server level");
                    assertActivity(reloaded[0], EntityActivity.STATUS_EFFECT);
                })
                .thenExecute(() -> {
                    runStatusTick(reloaded[0], helper.getLevel());
                    assertStatus(reloaded[0], StatusEffectInstance.activeFinite(3));
                })
                .thenExecute(helper::succeed);
    }

    private static ArmorStand spawn(GameTestHelper helper) {
        return helper.spawn(EntityType.ARMOR_STAND, new BlockPos(1, 1, 1));
    }

    private static void runStatusTick(LivingEntity livingEntity, ServerLevel serverLevel) {
        TickHooks.runDueActivities(livingEntity, serverLevel,
                EnumSet.of(EntityActivity.STATUS_EFFECT));
    }

    private static EffectContext contextFor(GameTestHelper helper, ArmorStand stand) {
        return new EffectContext(helper.getLevel(), stand, 1f,
                RandomSource.create(26L), ConditionContext.unavailable(), EffectCause.MANUAL);
    }

    private static long nextDueTime(EntityActivity activity, int entityId, long currentTime) {
        long candidate = currentTime + 1;
        while (!activity.isDue(candidate, entityId)) {
            candidate++;
        }
        return candidate;
    }

    private static StatusEffectReduction apply(ArmorStand stand,
                                               StatusEffectOperation operation,
                                               OptionalInt duration,
                                               int delay) {
        return StatusEffectSystem.apply(
                ((IStatusEffectTrait) stand).toHandleSelf(),
                stand.level(), JITTER, operation, duration, delay);
    }

    private static void assertNoAttachment(ArmorStand stand) {
        require(!stand.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                "status attachment must be absent");
    }

    private static void assertAbsent(ArmorStand stand) {
        require(stand.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                "empty status attachment must be retained at runtime");
        require(MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT).get(JITTER).isEmpty(),
                "status must be absent from the attachment");
        assertNoActivity(stand);
    }

    private static void assertNoActivity(LivingEntity entity) {
        require(!entity.hasData(ModDataAttachments.ACTIVE_SYSTEMS.get()),
                "entity must have no active derived systems");
    }

    private static void assertActivity(LivingEntity entity, EntityActivity activity) {
        require(entity.hasData(ModDataAttachments.ACTIVE_SYSTEMS.get()),
                "entity must have active derived systems");
        require(entity.getData(ModDataAttachments.ACTIVE_SYSTEMS.get()).isActive(activity),
                "expected activity " + activity + " to be active");
    }

    private static void assertStatus(ArmorStand stand, StatusEffectInstance expected) {
        require(stand.hasData(ModDataAttachments.STATUS_EFFECT.get()),
                "status attachment must be present");
        StatusEffectInstance actual = MS14Provider.get(stand, MS14Bridges.STATUS_EFFECT)
                .get(JITTER).orElseThrow(() -> new GameTestAssertException("jitter must be present"));
        require(actual.equals(expected), "expected status " + expected + " but got " + actual);
    }

    private static float reagentAmount(ArmorStand stand, ResourceKey<ReagentData> key) {
        return MS14Provider.get(stand, MS14Bridges.REAGENT).toComponent().contents()
                .getOrDefault(key, 0f);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
