package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.actions.BodyActionRequest;
import com.juicyslew.moonstation14.ms14.actions.BodyActionResult;
import com.juicyslew.moonstation14.ms14.actions.BodyActionService;
import com.juicyslew.moonstation14.ms14.actions.CombatModeSystem;
import com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Native coverage starts from an enrolled player and the normal Ready/camera/body lifecycle. */
@PrefixGameTestTemplate(false)
@GameTestTags({"active_capabilities", "interaction", "gameplay"})
public final class BodyActionGameTests {
    private BodyActionGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 100)
    public static void committedHumanQueriesResolvedCapabilities(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(level, "bodytest");
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var snapshot = BodyActionService.query(fixture.player()).orElseThrow(
                        () -> new GameTestAssertException("committed Ready body has no authoritative action snapshot"));
                var combat = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "combat_mode_toggle");
                var scream = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "scream");
                require(snapshot.bodyId().equals(active.body().getUUID()), "snapshot is for lifecycle-owned body");
                require(snapshot.bodyEpoch() == active.epoch(), "snapshot carries current body epoch");
                require(snapshot.actions().stream().anyMatch(entry -> entry.actionId().equals(combat)),
                        "human composed body exposes its combat action");
                require(snapshot.actions().stream().anyMatch(entry -> entry.actionId().equals(scream)),
                        "human composed body exposes its scream action");
                require(snapshot.actions().stream().noneMatch(entry -> entry.actionId().getPath().contains("debug")),
                        "debug capability is not granted by the committed human composition");
                helper.succeed();
            } finally {
                fixture.close();
            }
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 100)
    public static void combatToggleUsesFreshSnapshotsAndRejectsStaleOrReplayedRequests(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(level, "combatbody");
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = active.body();
                var action = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "combat_mode_toggle");
                var before = BodyActionService.query(fixture.player()).orElseThrow();
                var entry = before.actions().stream().filter(e -> e.actionId().equals(action)).findFirst().orElseThrow();
                var request = new BodyActionRequest(before.bodyId(), before.bodyEpoch(), before.revision(), action, entry.providerId(), 1);
                var first = BodyActionService.activate(fixture.player(), request);
                require(first.status() == BodyActionResult.Status.ACCEPTED, "combat first toggle: " + describe(first));
                require(CombatModeSystem.isInCombatMode(body), "combat state enabled after accepted action: " + describe(first));

                var secondSnapshot = BodyActionService.query(fixture.player()).orElseThrow();
                var secondEntry = secondSnapshot.actions().stream().filter(e -> e.actionId().equals(action)).findFirst().orElseThrow();
                var secondRequest = new BodyActionRequest(secondSnapshot.bodyId(), secondSnapshot.bodyEpoch(),
                        secondSnapshot.revision(), action, secondEntry.providerId(), 2);
                var second = BodyActionService.activate(fixture.player(), secondRequest);
                require(second.status() == BodyActionResult.Status.ACCEPTED, "combat second toggle: " + describe(second));
                require(!CombatModeSystem.isInCombatMode(body), "combat state disabled after second accepted action: " + describe(second));
                var replay = BodyActionService.activate(fixture.player(), secondRequest);
                require(replay.status() == BodyActionResult.Status.DENIED && !CombatModeSystem.isInCombatMode(body),
                        "used sequence replay denied without toggle: " + describe(replay));

                var current = BodyActionService.query(fixture.player()).orElseThrow();
                var currentEntry = current.actions().stream().filter(e -> e.actionId().equals(action)).findFirst().orElseThrow();
                var badBody = BodyActionService.activate(fixture.player(), new BodyActionRequest(UUID.randomUUID(), current.bodyEpoch(), current.revision(), action, currentEntry.providerId(), 3));
                var badEpoch = BodyActionService.activate(fixture.player(), new BodyActionRequest(current.bodyId(), current.bodyEpoch() + 1, current.revision(), action, currentEntry.providerId(), 4));
                var badProvider = BodyActionService.activate(fixture.player(), new BodyActionRequest(current.bodyId(), current.bodyEpoch(), current.revision(), action, "wrong-provider", 5));
                var staleRevision = BodyActionService.activate(fixture.player(), new BodyActionRequest(current.bodyId(), current.bodyEpoch(), current.revision() + 1, action, currentEntry.providerId(), 6));
                for (var denied : java.util.List.of(badBody, badEpoch, badProvider, staleRevision))
                    require(denied.status() == BodyActionResult.Status.DENIED && !CombatModeSystem.isInCombatMode(body),
                            "invalid combat request denied without toggle: " + describe(denied));
                helper.succeed();
            } finally { fixture.close(); }
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 100)
    @GameTestTags({"active_capabilities", "combat", "melee"})
    public static void canonicalCombatModeAndActionSnapshotShareOneBodyState(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(level, "combatinterop");
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = active.body();
                var action = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "combat_mode_toggle");
                require(com.juicyslew.moonstation14.ms14.combat.CombatModeSystem.setMode(body, true),
                        "canonical combat owner enables mode");
                var enabled = BodyActionService.query(fixture.player()).orElseThrow();
                var enabledEntry = enabled.actions().stream().filter(e -> e.actionId().equals(action)).findFirst().orElseThrow();
                require(enabledEntry.active(), "canonical mode is reflected in body-action snapshot active flag");
                var req = new BodyActionRequest(enabled.bodyId(), enabled.bodyEpoch(), enabled.revision(), action,
                        enabledEntry.providerId(), 101);
                var toggled = BodyActionService.activate(fixture.player(), req);
                require(toggled.status() == BodyActionResult.Status.ACCEPTED,
                        "action toggles state after canonical setter: " + describe(toggled));
                require(!com.juicyslew.moonstation14.ms14.combat.CombatModeSystem.isInCombatMode(body),
                        "incoming melee combat owner observes action toggle disabled");
                var disabled = BodyActionService.query(fixture.player()).orElseThrow();
                var disabledEntry = disabled.actions().stream().filter(e -> e.actionId().equals(action)).findFirst().orElseThrow();
                require(!disabledEntry.active() && disabled.revision() != enabled.revision(),
                        "action snapshot fingerprint revises when canonical state changes");
                require(com.juicyslew.moonstation14.ms14.combat.CombatModeSystem.setMode(body, true),
                        "canonical owner can enable again after action toggle");
                var enabledAgain = BodyActionService.query(fixture.player()).orElseThrow();
                require(enabledAgain.actions().stream().filter(e -> e.actionId().equals(action)).findFirst().orElseThrow().active(),
                        "subsequent canonical state change is visible without action-local cache");
                helper.succeed();
            } finally { fixture.close(); }
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 100)
    public static void screamSetsResolvedCooldownAndImmediateRepeatDoesNotExtendIt(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(level, "screambody");
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var binding = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.character
                        .PlayerCharacterHarnessBinder.binding(active.body());
                var identity = com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentitySavedData
                        .existing(level.getServer().overworld()).orElseThrow()
                        .character(new com.juicyslew.moonstation14.ms14.chat.identity.ChatIdentityRegistry
                                .CharacterKey(binding.accountId(), binding.profileKey())).orElseThrow();
                String expectedName = identity.name();
                String nativeName = active.body().getDisplayName().getString();
                require(!expectedName.equals("Human") && !expectedName.equals(fixture.player().getGameProfile().getName())
                                && !expectedName.equals(nativeName),
                        "registered harness uses durable personal name distinct from prototype/account/native name: identity="
                                + expectedName + " native=" + nativeName);
                require(com.juicyslew.moonstation14.ms14.character.CharacterPublicName.component(active.body())
                                .getString().equals(expectedName), "shared public-name policy uses saved identity");
                var screamPlan = com.juicyslew.moonstation14.ms14.effect.EmoteEffect
                        .plan("scream", true, active.body());
                require(screamPlan != null && screamPlan.chat() != null
                                && screamPlan.chat().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents line
                                && line.getKey().equals("moonstation14.emote.scream")
                                && line.getArgs().length > 0
                                && line.getArgs()[0] instanceof net.minecraft.network.chat.Component name
                                && name.getString().equals(expectedName)
                                && !name.getString().equals(nativeName),
                        "registered human scream plan carries saved personal name, not prototype/native entity display name");
                var action = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "scream");
                var before = BodyActionService.query(fixture.player()).orElseThrow();
                var entry = before.actions().stream().filter(e -> e.actionId().equals(action)).findFirst().orElseThrow();
                long expectedEnd = level.getGameTime() + entry.useDelayTicks();
                var request = new BodyActionRequest(before.bodyId(), before.bodyEpoch(), before.revision(), action, entry.providerId(), 1);
                var result = BodyActionService.activate(fixture.player(), request);
                require(result.status() == BodyActionResult.Status.ACCEPTED, "scream action: " + describe(result));
                var after = BodyActionService.query(fixture.player()).orElseThrow();
                var afterEntry = after.actions().stream().filter(e -> e.actionId().equals(action)).findFirst().orElseThrow();
                require(afterEntry.cooldownEndTick() == expectedEnd, "scream cooldown end expected=" + expectedEnd + " actual=" + afterEntry.cooldownEndTick());
                var repeatRequest = new BodyActionRequest(after.bodyId(), after.bodyEpoch(), after.revision(), action, afterEntry.providerId(), 2);
                var repeat = BodyActionService.activate(fixture.player(), repeatRequest);
                require(repeat.status() == BodyActionResult.Status.DENIED, "immediate scream repeat denied: " + describe(repeat));
                var finalEntry = BodyActionService.query(fixture.player()).orElseThrow().actions().stream()
                        .filter(e -> e.actionId().equals(action)).findFirst().orElseThrow();
                require(finalEntry.cooldownEndTick() == expectedEnd, "repeat does not extend cooldown expected=" + expectedEnd + " actual=" + finalEntry.cooldownEndTick());
                helper.succeed();
            } finally { fixture.close(); }
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 100)
    @GameTestTags({"active_capabilities", "gameplay", "medical"})
    public static void criticalHumanRetainsCombatButLosesConsciousScream(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(level, "criticalaction");
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var player = fixture.player();
                var body = active.body();
                var snapshot = BodyActionService.query(player).orElseThrow();
                var combat = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "combat_mode_toggle");
                var scream = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "scream");
                var combatEntry = snapshot.actions().stream().filter(e -> e.actionId().equals(combat)).findFirst().orElseThrow();
                var screamEntry = snapshot.actions().stream().filter(e -> e.actionId().equals(scream)).findFirst().orElseThrow();
                var policy = com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.policy(body);
                double critical = policy.thresholds().thresholds().entrySet().stream()
                        .filter(e -> e.getValue() == com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.CRITICAL)
                        .mapToDouble(java.util.Map.Entry::getKey).min().orElseThrow();
                double dead = policy.thresholds().thresholds().entrySet().stream()
                        .filter(e -> e.getValue() == com.juicyslew.moonstation14.ms14.character.components.MobStateComponent.State.DEAD)
                        .mapToDouble(java.util.Map.Entry::getKey).min().orElseThrow();
                float damage = (float) Math.min(dead - 1, critical);
                require(damage >= critical && damage < dead, "resolved thresholds yield critical damage amount=" + damage + " critical=" + critical + " dead=" + dead);
                require(com.juicyslew.moonstation14.ms14.damage.DamageSystem.applyHealthChange(body,
                        java.util.Map.of(com.juicyslew.moonstation14.ms14.damage.DamageKeys.BLUNT, damage), 1f, true)
                        == com.juicyslew.moonstation14.ms14.damage.DamageSystem.Result.APPLIED,
                        "threshold-derived typed blunt damage applied amount=" + damage);
                require(com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.isCritical(body)
                                && body.isAlive() && body.getHealth() > 0,
                        "body is medical=" + com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.currentState(body)
                                + " nativeAlive=" + body.isAlive() + " health=" + body.getHealth());
                require(com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority.resolve(player).isPresent(),
                        "critical human retains resolved body-action authority");
                var criticalSnapshot = BodyActionService.query(player).orElseThrow();
                var criticalCombat = criticalSnapshot.actions().stream().filter(e -> e.actionId().equals(combat)).findFirst().orElseThrow();
                var criticalScream = criticalSnapshot.actions().stream().filter(e -> e.actionId().equals(scream)).findFirst().orElseThrow();
                require(criticalCombat.enabled(), "critical combat enabled action=" + combat + " enabled=" + criticalCombat.enabled()
                        + " reason=" + criticalCombat.disabledReason());
                require(!criticalScream.enabled(), "critical scream disabled action=" + scream + " enabled=" + criticalScream.enabled()
                        + " reason=" + criticalScream.disabledReason());
                var denied = BodyActionService.activate(player, new BodyActionRequest(criticalSnapshot.bodyId(),
                        criticalSnapshot.bodyEpoch(), criticalSnapshot.revision(), scream, screamEntry.providerId(), 1));
                require(denied.status() == BodyActionResult.Status.DENIED,
                        "critical scream denied status=" + describe(denied) + " bodyState=" + com.juicyslew.moonstation14.ms14.mobs.MobStateSystem.currentState(body));
                var current = BodyActionService.query(player).orElseThrow();
                var currentCombat = current.actions().stream().filter(e -> e.actionId().equals(combat)).findFirst().orElseThrow();
                var accepted = BodyActionService.activate(player, new BodyActionRequest(current.bodyId(), current.bodyEpoch(),
                        current.revision(), combat, currentCombat.providerId(), 2));
                require(accepted.status() == BodyActionResult.Status.ACCEPTED && CombatModeSystem.isInCombatMode(body),
                        "critical combat toggle accepted and changes state result=" + describe(accepted));
                helper.succeed();
            } finally { fixture.close(); }
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "empty", timeoutTicks = 100)
    @GameTestTags("active_capabilities")
    public static void debugCapabilityRequiresWornCapAndRevokesOldProvider(GameTestHelper helper) {
        var level = (net.minecraft.server.level.ServerLevel) helper.getLevel();
        RegisteredServerPlayerFixture fixture = RegisteredServerPlayerFixture.register(level, "debugcapbody");
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var player = fixture.player();
                var body = active.body();
                var authority = com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                        .resolve(player).orElseThrow();
                var capId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "debug_capability_cap");
                var cap = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(capId);
                require(cap != net.minecraft.world.item.Items.AIR, "registered debug cap exists");
                var starterHands = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.initialize(body).orElseThrow();
                require(starterHands.handIds().stream().allMatch(h -> starterHands.stackCopy(h).isEmpty()),
                        "normal Ready starter equipment leaves an empty hand for trusted cap give; hands=" + starterHands.handIds());
                require(com.juicyslew.moonstation14.ms14.hands.live.BodyGiveService.giveTrustedBody(body,
                        new net.minecraft.world.item.ItemStack(cap),
                        () -> com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                                .revalidate(player, authority)), "trusted authoritative body give succeeds");
                var held = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.initialize(body).orElseThrow();
                String hand = held.handIds().stream().filter(h -> held.stackCopy(h)
                        .filter(s -> s.is(cap)).isPresent()).findFirst().orElseThrow();
                String token = held.token(hand).orElseThrow();
                var debug = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "debug_pulse");
                var query = BodyActionService.query(player).orElseThrow();
                require(query.actions().stream().noneMatch(e -> e.actionId().equals(debug)), "held cap grants no action");
                var beforeHealth = body.getHealth();
                var beforeVelocity = body.getDeltaMovement();
                require(com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer.quickEquipOwned(
                        body, hand, token, held.revision(), () -> com.juicyslew.moonstation14.ms14.player_body_control.action
                                .BodyActionAuthority.revalidate(player, authority))
                        == com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer.Result.SUCCESS,
                        "cap quick-equips through live equipment owner");
                var equipment = com.juicyslew.moonstation14.ms14.hands.live.LiveHands.equipmentSnapshot(body).orElseThrow();
                var slot = equipment.slots().stream().filter(s -> s.token().filter(token::equals).isPresent()).findFirst().orElseThrow();
                require(slot.stack().filter(s -> s.is(cap)).isPresent(), "snapshot owns cap and token in equipment");
                query = BodyActionService.query(player).orElseThrow();
                var entry = query.actions().stream().filter(e -> e.actionId().equals(debug)).findFirst().orElseThrow();
                String provider = "equipment:" + slot.id() + ":" + token;
                require(entry.providerId().equals(provider), "provider identifies actual equipment ownership");
                var request = new BodyActionRequest(query.bodyId(), query.bodyEpoch(), query.revision(), debug, provider, 1);
                require(BodyActionService.activate(player, request).status() == BodyActionResult.Status.ACCEPTED,
                        "worn cap debug pulse accepted");
                require(body.getHealth() == beforeHealth && body.getDeltaMovement().equals(beforeVelocity),
                        "pulse has no synchronous health or velocity effect");
                var current = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                var emptyHand = current.handIds().stream().filter(h -> current.stackCopy(h).isEmpty()).findFirst().orElseThrow();
                require(com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer.transferOwned(body,
                        emptyHand, slot.id(), token, current.revision(), false,
                        () -> com.juicyslew.moonstation14.ms14.player_body_control.action.BodyActionAuthority
                                .revalidate(player, authority))
                        == com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer.Result.SUCCESS,
                        "cap unequips through live equipment owner");
                var after = BodyActionService.query(player).orElseThrow();
                require(after.actions().stream().noneMatch(e -> e.actionId().equals(debug)), "unequipped cap revokes action");
                var denied = BodyActionService.activate(player, new BodyActionRequest(query.bodyId(), query.bodyEpoch(),
                        after.revision(), debug, provider, 2));
                require(denied.status() == BodyActionResult.Status.DENIED && body.getHealth() == beforeHealth
                        && body.getDeltaMovement().equals(beforeVelocity), "old provider denied without effect");
                helper.succeed();
            } finally { fixture.close(); }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static String describe(BodyActionResult result) {
        var details = new StringBuilder("status=").append(result.status())
                .append(" reason=").append(result.reason()).append(" sequence=").append(result.sequence());
        result.snapshot().ifPresent(snapshot -> details.append(" snapshot=").append(snapshot.bodyId())
                .append(" epoch=").append(snapshot.bodyEpoch()).append(" revision=").append(snapshot.revision())
                .append(" actions=").append(snapshot.actions().size()));
        return details.toString();
    }
}
