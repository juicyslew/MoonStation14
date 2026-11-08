package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.character.components.StaminaPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.stamina.BeforeStaminaDamageEvent;
import com.juicyslew.moonstation14.ms14.character.stamina.StaminaSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import com.juicyslew.moonstation14.ms14.status_effect.ModStatusEffects;
import com.juicyslew.moonstation14.ms14.status_effect.StatusEffectPayload;
import com.juicyslew.moonstation14.ms14.standing.PostureSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Native entity coverage of the resolved Human stamina owner; pure reducer checks are separate JVM tests. */
@PrefixGameTestTemplate(false)
@GameTestTags({"stamina", "character-control"})
public final class StaminaGameTests {
    private StaminaGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 250)
    public static void resolvedHumanVillagerCrossesThresholdsAndRecoversOnLiveTicks(GameTestHelper helper) {
        var bodies = new ArrayList<Villager>();
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        bodies.add(body);
        TreeMap<Long, com.juicyslew.moonstation14.ms14.character.stamina.StaminaState> postTicks = new TreeMap<>();
        java.util.concurrent.atomic.AtomicReference<RecoveryObservation> recovery = new java.util.concurrent.atomic.AtomicReference<>();
        Consumer<EntityTickEvent.Post> postListener = event -> {
            if (event.getEntity() == body)
                postTicks.put(event.getEntity().level().getGameTime(), StaminaSystem.state(body));
        };
        Consumer<BeforeStaminaDamageEvent> damageListener = event -> {
            if (event.target() == body && event.amount() < 0) {
                var during = StaminaSystem.state(body);
                recovery.set(new RecoveryObservation(body.level().getGameTime(), event.amount(), during.damage(), during.nextUpdateTick(), event.amount()));
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, postListener);
        NeoForge.EVENT_BUS.addListener(damageListener);
        helper.runAfterDelay(249, () -> cleanup(bodies, damageListener, postListener));
        try {
            var policy = ActiveCharacterPolicy.resolveActor(body).flatMap(c -> c.component(StaminaPrototypeComponent.class));
            require(policy.isPresent(), "default Villager resolves mapped Human Stamina composition");
            require(!body.hasData(ModDataAttachments.STAMINA.get()), "configured neutral stamina reads do not materialize an attachment");
            float health = body.getHealth();
            helper.runAfterDelay(1, () -> guarded(helper, bodies, damageListener, postListener, () -> {
                long damageAt = helper.getLevel().getGameTime();
                for (int i = 0; i < 3; i++) require(StaminaSystem.takeStaminaDamage(body, 25f)
                        == StaminaSystem.DamageResult.APPLIED, "each admitted stamina dose applies");
                var state = StaminaSystem.state(body);
                requireClose(75, state.damage(), "three doses reach 75 independently of health damage");
                require(state.nextUpdateTick() == damageAt + 60, "positive damage starts three-second recovery deadline");
                requireClose(.7f, lowMultiplier(body), "60 percent slowdown threshold payload is projected");
                require(CharacterControlSystem.applyParalyze(body, 240), "existing shared paralysis owner applies longer stun before stamina crit");
                int priorStun = remainingStatus(body, "statuseffectstunned");
                int priorKnockdown = remainingStatus(body, "knockdown");
                require(StaminaSystem.takeStaminaDamage(body, 25f) == StaminaSystem.DamageResult.APPLIED,
                        "fourth dose enters stamina critical");
                state = StaminaSystem.state(body);
                require(state.critical() && state.damage() == 100, "critical state clamps stamina to its resolved 100 threshold");
                require(state.nextUpdateTick() == damageAt + 180, "critical recovery waits six-second stun plus three-second buffer");
                require(remainingStatus(body, "statuseffectstunned") == priorStun
                                && remainingStatus(body, "knockdown") == priorKnockdown,
                        "six-second stamina paralysis does not shorten a longer shared paralysis");
                requireClose(.5f, lowMultiplier(body), "80 percent slowdown threshold payload is projected");
                require(CharacterControlSystem.isStunned(body) && PostureSystem.isDownPosture(body),
                        "critical projection reuses shared paralysis and posture owners");
                var posture = body.getExistingDataOrNull(ModDataAttachments.POSTURE.get());
                require(posture != null && posture.data().voluntaryAutoStand(), "knockdown projection uses posture owner's auto-stand policy");
                requireClose(health, body.getHealth(), "stamina changes do not modify vanilla health");
                long criticalDeadline = state.nextUpdateTick();
                require(StaminaSystem.takeStaminaDamage(body, 25f) == StaminaSystem.DamageResult.APPLIED,
                        "post-critical damage is admitted through event path");
                require(StaminaSystem.state(body).equals(state), "critical ignores admitted delta and preserves its deadline");
                helper.runAfterDelay(181, () -> guarded(helper, bodies, damageListener, postListener, () -> {
                    var beforeDeadline = postTicks.get(criticalDeadline - 1);
                    require(beforeDeadline != null && beforeDeadline.critical() && beforeDeadline.damage() == 100,
                            "post-entity-tick observer confirms critical persists through deadline minus one");
                    var due = postTicks.get(criticalDeadline);
                    require(due != null, "post-entity-tick observer records the exact critical deadline");
                    stateCheckRecovery(due, 85, true, criticalDeadline + 20);
                    RecoveryObservation observed = recovery.get();
                    require(observed != null && observed.atTick() == criticalDeadline && observed.amount() == -15f
                                    && observed.damageBeforeApply() == 100 && observed.deadlineDuringEvent() == criticalDeadline + 20,
                            "critical recovery event observes requested -15 after advancing deadline by 20 ticks");
                    helper.runAfterDelay(20, () -> guarded(helper, bodies, damageListener, postListener, () -> {
                        stateCheckRecovery(postTicks.get(criticalDeadline + 20), 70, true, criticalDeadline + 40);
                        requireClose(.7f, lowMultiplier(body), "after-critical recovery refreshes slowdown threshold to .7");
                        cleanup(bodies, damageListener, postListener);
                        helper.succeed();
                    }));
                }));
            }));
        } catch (Throwable failure) {
            cleanup(bodies, damageListener, postListener);
            helper.fail("stamina human threshold setup: " + failure);
        }
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 100)
    public static void normalRecoveryUsesResolvedCadenceAndUnsupportedPigStaysInert(GameTestHelper helper) {
        var bodies = new ArrayList<Villager>();
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        bodies.add(body);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(5, 1, 2));
        TreeMap<Long, com.juicyslew.moonstation14.ms14.character.stamina.StaminaState> postTicks = new TreeMap<>();
        AtomicReference<RecoveryObservation> recovery = new AtomicReference<>();
        AtomicInteger negativeEvents = new AtomicInteger();
        Consumer<EntityTickEvent.Post> postListener = event -> {
            if (event.getEntity() == body) postTicks.put(event.getEntity().level().getGameTime(), StaminaSystem.state(body));
        };
        Consumer<BeforeStaminaDamageEvent> damageListener = event -> {
            if (event.target() != body || event.amount() >= 0) return;
            var during = StaminaSystem.state(body);
            float requested = event.amount();
            float accepted = requested;
            if (negativeEvents.incrementAndGet() == 1) {
                event.setAmount(-1.5f);
                accepted = -1.5f;
            }
            recovery.set(new RecoveryObservation(body.level().getGameTime(), requested, during.damage(), during.nextUpdateTick(), accepted));
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, postListener);
        NeoForge.EVENT_BUS.addListener(damageListener);
        helper.runAfterDelay(99, () -> { cleanup(bodies, damageListener, postListener); pig.discard(); });
        try {
            require(ActiveCharacterPolicy.resolveActor(body).flatMap(c -> c.component(StaminaPrototypeComponent.class)).isPresent(),
                    "Villager test actor has resolved Human stamina");
            require(ActiveCharacterPolicy.resolveActor(pig).isPresent()
                            && ActiveCharacterPolicy.resolveActor(pig).orElseThrow().component(StaminaPrototypeComponent.class).isEmpty(),
                    "resolved Pig composition has no Stamina capability");
            require(StaminaSystem.takeStaminaDamage(pig, 25) == StaminaSystem.DamageResult.UNSUPPORTED,
                    "unsupported Pig is inert");
            require(!pig.hasData(ModDataAttachments.STAMINA.get()), "unsupported actor has no stamina attachment");
            require(StaminaSystem.state(body).equals(com.juicyslew.moonstation14.ms14.character.stamina.StaminaState.EMPTY)
                            && !body.hasData(ModDataAttachments.STAMINA.get()), "configured absent state reads neutral without attachment creation");
            helper.runAfterDelay(1, () -> guarded(helper, bodies, damageListener, postListener, () -> {
                require(StaminaSystem.takeStaminaDamage(body, 25) == StaminaSystem.DamageResult.APPLIED, "normal dose applies");
                long firstDeadline = StaminaSystem.state(body).nextUpdateTick();
                helper.runAfterDelay(61, () -> guarded(helper, bodies, damageListener, postListener, () -> {
                    var beforeDeadline = postTicks.get(firstDeadline - 1);
                    require(beforeDeadline != null && beforeDeadline.damage() == 25,
                            "post-entity-tick observer confirms normal damage is unchanged at cooldown minus one");
                    var due = postTicks.get(firstDeadline);
                    require(due != null, "post-entity-tick observer records the exact normal cooldown deadline");
                    stateCheckNormalRecovery(due, 23.5f, firstDeadline + 20);
                    RecoveryObservation observed = recovery.get();
                    require(observed != null && observed.atTick() == firstDeadline && observed.amount() == -3f
                                    && observed.damageBeforeApply() == 25f && observed.deadlineDuringEvent() == firstDeadline + 20
                                    && observed.acceptedDelta() == -1.5f,
                            "normal -3 recovery is modified to -1.5 through the same event after its deadline advances");
                    requireClose(1, lowMultiplier(body), "recovery below 60 percent refreshes neutral multiplier and status remains present");
                    helper.runAfterDelay(20, () -> guarded(helper, bodies, damageListener, postListener, () -> {
                        stateCheckNormalRecovery(postTicks.get(firstDeadline + 20), 20.5f, firstDeadline + 40);
                        cleanup(bodies, damageListener, postListener);
                        pig.discard();
                        helper.succeed();
                    }));
                }));
            }));
        } catch (Throwable failure) {
            cleanup(bodies, damageListener, postListener);
            pig.discard();
            helper.fail("stamina normal recovery setup: " + failure);
        }
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 220)
    public static void staminaEventsCancelModifyBypassResistanceAndObserveCriticalRecovery(GameTestHelper helper) {
        var bodies = new ArrayList<Villager>();
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        bodies.add(body);
        AtomicReference<Mode> mode = new AtomicReference<>(Mode.CANCEL);
        AtomicInteger events = new AtomicInteger();
        AtomicInteger recoveryEvents = new AtomicInteger();
        Consumer<BeforeStaminaDamageEvent> listener = event -> {
            if (event.target() != body) return;
            events.incrementAndGet();
            if (event.amount() < 0) recoveryEvents.incrementAndGet();
            switch (mode.get()) {
                case CANCEL -> event.setCanceled(true);
                case HALF -> event.setAmount(event.amount() * .5f);
                case CANCEL_RECOVERY -> { if (event.amount() < 0) event.setCanceled(true); }
                case PASS -> { }
            }
        };
        helper.runAfterDelay(219, () -> cleanup(bodies, listener, null));
        NeoForge.EVENT_BUS.addListener(listener);
        try {
            require(ActiveCharacterPolicy.resolveActor(body).flatMap(c -> c.component(StaminaPrototypeComponent.class)).isPresent(),
                    "event fixture resolves the Human stamina component");
            helper.runAfterDelay(1, () -> guarded(helper, bodies, listener, null, () -> {
                require(StaminaSystem.takeStaminaDamage(body, 25) == StaminaSystem.DamageResult.CANCELLED,
                        "cancellable event rejects stamina damage");
                require(StaminaSystem.takeStaminaDamage(body, 25, true) == StaminaSystem.DamageResult.CANCELLED,
                        "ignoreResistance does not bypass event cancellation");
                require(events.get() == 2 && !body.hasData(ModDataAttachments.STAMINA.get()),
                        "canceled first deltas do not materialize resource state");
                mode.set(Mode.HALF);
                require(StaminaSystem.takeStaminaDamage(body, 25) == StaminaSystem.DamageResult.APPLIED,
                        "event-modified dose applies");
                requireClose(12.5f, StaminaSystem.state(body).damage(), "normal call consumes event's half-value");
                require(StaminaSystem.takeStaminaDamage(body, 25, true) == StaminaSystem.DamageResult.APPLIED,
                        "ignoreResistance applies original value after event dispatch");
                requireClose(37.5f, StaminaSystem.state(body).damage(), "ignoreResistance ignores modification, not dispatch");
                mode.set(Mode.PASS);
                for (int i = 0; i < 3; i++) StaminaSystem.takeStaminaDamage(body, 25);
                var critical = StaminaSystem.state(body);
                require(critical.critical(), "event path can enter critical");
                int beforeRepeat = events.get();
                StaminaSystem.takeStaminaDamage(body, 25);
                require(events.get() == beforeRepeat + 1 && StaminaSystem.state(body).equals(critical),
                        "critical admission still dispatches event before ignoring delta");
                mode.set(Mode.CANCEL_RECOVERY);
                helper.runAfterDelay(182, () -> guarded(helper, bodies, listener, null, () -> {
                    var afterCancelledRecovery = StaminaSystem.state(body);
                    require(recoveryEvents.get() == 1, "deadline recovery delta traverses cancellable event");
                    require(!afterCancelledRecovery.critical() && afterCancelledRecovery.afterCritical()
                                    && afterCancelledRecovery.damage() == critical.damage(),
                            "critical exits and after-critical flag advances even when recovery is canceled");
                    require(afterCancelledRecovery.nextUpdateTick() == critical.nextUpdateTick() + 20,
                            "canceled recovery retains the next one-second deadline");
                    cleanup(bodies, listener, null);
                    helper.succeed();
                }));
            }));
        } catch (Throwable failure) {
            cleanup(bodies, listener, null);
            helper.fail("stamina event setup: " + failure);
        }
    }

    private enum Mode { CANCEL, HALF, CANCEL_RECOVERY, PASS }

    private static float lowMultiplier(Villager body) {
        var attachment = body.getExistingDataOrNull(ModDataAttachments.STATUS_EFFECT.get());
        var status = attachment == null ? null : attachment.get(ModStatusEffects.createKey("stamina_low")).orElse(null);
        require(status != null && status.payload() instanceof StatusEffectPayload.MovementSpeedModifier,
                "stamina-low status carries the shared movement payload");
        return ((StatusEffectPayload.MovementSpeedModifier) status.payload()).multiplier();
    }

    private record RecoveryObservation(long atTick, float amount, float damageBeforeApply,
                                       long deadlineDuringEvent, float acceptedDelta) { }

    private static int remainingStatus(Villager body, String statusId) {
        var attachment = body.getExistingDataOrNull(ModDataAttachments.STATUS_EFFECT.get());
        var status = attachment == null ? null : attachment.get(ModStatusEffects.createKey(statusId)).orElse(null);
        require(status != null && status.remainingDurationTicks().isPresent(), statusId + " has a finite shared status duration");
        return status.remainingDurationTicks().getAsInt();
    }

    private static void stateCheckRecovery(com.juicyslew.moonstation14.ms14.character.stamina.StaminaState state,
                                           float damage, boolean afterCritical, long nextUpdate) {
        requireClose(damage, state.damage(), "stamina recovery reaches the expected resource value");
        require(!state.critical() && state.afterCritical() == afterCritical,
                "critical recovery transitions to after-critical state");
        require(state.nextUpdateTick() == nextUpdate, "recovery advances the authoritative update tick");
    }

    private static void stateCheckNormalRecovery(com.juicyslew.moonstation14.ms14.character.stamina.StaminaState state,
                                                 float damage, long nextUpdate) {
        require(state != null, "post-entity-tick observer recorded recovery state");
        requireClose(damage, state.damage(), "event-admitted recovery has the expected value");
        require(!state.critical() && !state.afterCritical(), "normal recovery remains outside critical state");
        require(state.nextUpdateTick() == nextUpdate, "normal recovery advances exactly one scheduled tick");
    }

    private static void guarded(GameTestHelper helper, ArrayList<Villager> bodies,
                                Consumer<BeforeStaminaDamageEvent> listener, Runnable action) {
        guarded(helper, bodies, listener, null, action);
    }

    private static void guarded(GameTestHelper helper, ArrayList<Villager> bodies,
                                Consumer<BeforeStaminaDamageEvent> listener,
                                Consumer<EntityTickEvent.Post> postListener, Runnable action) {
        try { action.run(); }
        catch (Throwable failure) {
            cleanup(bodies, listener, postListener);
            helper.fail("stamina fixture callback: " + failure);
        }
    }

    private static void cleanup(ArrayList<Villager> bodies, Consumer<BeforeStaminaDamageEvent> listener) {
        cleanup(bodies, listener, null);
    }

    private static void cleanup(ArrayList<Villager> bodies, Consumer<BeforeStaminaDamageEvent> listener,
                                Consumer<EntityTickEvent.Post> postListener) {
        try { if (listener != null) NeoForge.EVENT_BUS.unregister(listener); }
        catch (Throwable ignored) { /* Continue releasing every fixture entity. */ }
        try { if (postListener != null) NeoForge.EVENT_BUS.unregister(postListener); }
        catch (Throwable ignored) { /* Continue releasing every fixture entity. */ }
        for (Villager body : bodies) {
            try { body.discard(); }
            catch (Throwable ignored) { /* Cleanup remains best-effort for independently removed actors. */ }
        }
    }

    private static void requireClose(float expected, float actual, String message) {
        if (Math.abs(expected - actual) > .001f)
            throw new GameTestAssertException(message + ": expected=" + expected + " actual=" + actual);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
