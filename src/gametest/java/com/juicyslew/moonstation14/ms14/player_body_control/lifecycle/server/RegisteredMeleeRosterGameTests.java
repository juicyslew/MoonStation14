package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Confirms registered human bodies resolve their production, inherited melee composition. */
@GameTestTags({"melee-roster", "shared-melee-runtime-fixture"})
@PrefixGameTestTemplate(false)
public final class RegisteredMeleeRosterGameTests {
    private RegisteredMeleeRosterGameTests() { }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_melee_ss14_wide_admission", timeoutTicks=100)
    public static void registeredWideAdmissionUsesRangeAndLosNotServerFan(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "melee-wide-server-fan-boundary");
        var refs = new net.minecraft.world.entity.Entity[3];
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void clean(net.minecraft.gametest.framework.GameTestInfo i) {
                try { for (var e : refs) if (e != null && !e.isRemoved()) e.discard(); fixture.close(); }
                catch (Throwable t) { if (i.getError() != null) i.getError().addSuppressed(t); else i.fail(t); }
            }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(i); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(i); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o, net.minecraft.gametest.framework.GameTestInfo n, net.minecraft.gametest.framework.GameTestRunner r) { clean(o); }
        });
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = active.body(); refs[0] = body;
                var origin = positionBody(helper, body);
                body.setYRot(0); body.setYHeadRot(0); body.setXRot(0);
                var forward = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
                var behind = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
                if (forward == null || behind == null) throw new GameTestAssertException("target creation failed");
                refs[1] = forward; refs[2] = behind;
                for (var target : refs) if (target instanceof net.minecraft.world.entity.Mob mob) { mob.setNoAi(true); mob.setNoGravity(true); }
                forward.setPos(origin.getX()+.5, origin.getY(), origin.getZ()+.9);
                behind.setPos(origin.getX()+.5, origin.getY(), origin.getZ()-.9);
                if (!helper.getLevel().addFreshEntity(forward) || !helper.getLevel().addFreshEntity(behind)) throw new GameTestAssertException("target insertion failed");
                var selection = MeleeWeaponResolver.resolve(body).orElseThrow(() -> new GameTestAssertException("production fists unresolved"));
                requireProductionPunchHit(selection);
                var look = body.getLookAngle().normalize();
                var delta = behind.position().subtract(body.position()).normalize();
                if (look.dot(delta) >= 0 || selection.profile().weapon().angleDegrees() >= 180)
                    throw new GameTestAssertException("behind target is not demonstrably outside resolved fan");
                if (com.juicyslew.moonstation14.ms14.combat.CombatMeleeTargetProbe.probe(helper.getLevel(), body, forward.getUUID(), selection.profile().weapon().range()+.1f).status() != com.juicyslew.moonstation14.ms14.combat.CombatMeleeTargetProbe.Status.HIT
                        || com.juicyslew.moonstation14.ms14.combat.CombatMeleeTargetProbe.probe(helper.getLevel(), body, behind.getUUID(), selection.profile().weapon().range()+.1f).status() != com.juicyslew.moonstation14.ms14.combat.CombatMeleeTargetProbe.Status.HIT)
                    throw new GameTestAssertException("forward and behind targets must both satisfy server range/LOS admission");
                float fBefore = typed((net.minecraft.world.entity.LivingEntity) forward, "blunt"), bBefore = typed((net.minecraft.world.entity.LivingEntity) behind, "blunt");
                com.juicyslew.moonstation14.ms14.combat.CombatModeRequestService.handle(fixture.player(), new com.juicyslew.moonstation14.ms14.combat.CombatModeRequest(active.epoch(), 1, true));
                var result = com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(fixture.player(), new com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest(active.epoch(), 1, selection.kind(), selection.handRevision(), selection.activeHand(), selection.itemToken().orElse(""), com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE, null, java.util.List.of(forward.getUUID(), behind.getUUID())));
                if (result.status() != com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.HIT || !result.cooldownConsumed()
                        || Math.abs(typed((net.minecraft.world.entity.LivingEntity) forward, "blunt")-fBefore-5f) > .011f
                        || Math.abs(typed((net.minecraft.world.entity.LivingEntity) behind, "blunt")-bBefore-5f) > .011f)
                    throw new GameTestAssertException("SS14 server wide range/LOS admission did not damage both valid nominations");
                helper.succeed();
            } catch (RuntimeException | Error e) { fixture.close(); throw e; }
        });
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_melee_audio_invalidfirst", timeoutTicks=100)
    public static void wideAttackSkipsOutOfRangeFirstNomination(GameTestHelper helper) { registeredWideSoundCase(helper, 0); }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_melee_audio_occludedfirst", timeoutTicks=100)
    public static void wideAttackSkipsFirstNominationBlockedByWall(GameTestHelper helper) { registeredWideSoundCase(helper, 1); }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_melee_audio_validatedmiss", timeoutTicks=100)
    public static void wideAttackWithNonemptyInvalidNominationsSwingsAtInitiator(GameTestHelper helper) { registeredWideSoundCase(helper, 2); }

    private static void registeredWideSoundCase(GameTestHelper helper, int scenario) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "melee-wide-sound-" + scenario);
        var refs = new net.minecraft.world.entity.Entity[3];
        var sounds = new java.util.ArrayList<net.minecraft.network.protocol.game.ClientboundSoundPacket>();
        fixture.observeOutbound(packet -> {
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket sound) {
                var id = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(sound.getSound().value());
                if (id != null && id.getNamespace().equals("moonstation14") && id.getPath().startsWith("ss14/weapons/punch")) sounds.add(sound);
            }
        });
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void clean(net.minecraft.gametest.framework.GameTestInfo info) {
                try { for (var entity : refs) if (entity != null && !entity.isRemoved()) entity.discard(); fixture.close(); }
                catch (Throwable t) { if (info.getError() != null) info.getError().addSuppressed(t); else info.fail(t); }
            }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(i); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(i); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o, net.minecraft.gametest.framework.GameTestInfo n, net.minecraft.gametest.framework.GameTestRunner r) { clean(o); }
        });
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                refs[0] = active.body(); var origin = positionBody(helper, active.body());
                var first = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
                var valid = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
                if (first == null || valid == null) throw new GameTestAssertException("target creation failed");
                 refs[1] = first; refs[2] = valid; first.setNoAi(true); valid.setNoAi(true);
                 first.setNoGravity(true); valid.setNoGravity(true);
                 first.setPos(origin.getX() + (scenario == 1 ? 2.35 : 3.5), origin.getY(), origin.getZ() + .5);
                 valid.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + 1.7);
                 if (!helper.getLevel().addFreshEntity(first) || !helper.getLevel().addFreshEntity(valid)) throw new GameTestAssertException("target insertion failed");
                 if (scenario == 1) helper.getLevel().setBlock(origin.offset(1, 1, 0), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                  var selection = MeleeWeaponResolver.resolve(active.body()).orElseThrow(() -> new GameTestAssertException("production fists unresolved"));
                  requireProductionPunchHit(selection);
                 float probeRange = selection.profile().weapon().range() + .1f;
                 boolean firstInRange = com.juicyslew.moonstation14.ms14.combat.CombatMeleeTargetProbe.BodyBounds.withinRange(
                         com.juicyslew.moonstation14.ms14.combat.CombatMeleeTargetProbe.BodyBounds.distance(active.body().getBoundingBox(), first.getBoundingBox()), probeRange);
                 if (firstInRange != (scenario == 1)) throw new GameTestAssertException("first nomination range precondition mismatch: " + firstInRange);
                 var probe = com.juicyslew.moonstation14.ms14.combat.CombatMeleeTargetProbe.probe(helper.getLevel(), active.body(), first.getUUID(), probeRange);
                 if (probe.status() != com.juicyslew.moonstation14.ms14.combat.CombatMeleeTargetProbe.Status.MISS)
                     throw new GameTestAssertException("invalid first nomination unexpectedly admitted: " + probe.status());
                 var validProbe = com.juicyslew.moonstation14.ms14.combat.CombatMeleeTargetProbe.probe(helper.getLevel(), active.body(), valid.getUUID(), probeRange);
                 if (validProbe.status() != com.juicyslew.moonstation14.ms14.combat.CombatMeleeTargetProbe.Status.HIT)
                     throw new GameTestAssertException("valid second nomination failed admission: " + validProbe.status());
                 com.juicyslew.moonstation14.ms14.combat.CombatModeRequestService.handle(fixture.player(), new com.juicyslew.moonstation14.ms14.combat.CombatModeRequest(active.epoch(), 1, true));
                 var hitCue = selection.profile().weapon().audio().hit();
                 boolean hitEventRegistered = hitCue != null && net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(hitCue.event());
                var nominations = scenario == 2 ? java.util.List.of(first.getUUID()) : java.util.List.of(first.getUUID(), valid.getUUID());
                var result = com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(fixture.player(),
                        new com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest(active.epoch(), 1, selection.kind(), selection.handRevision(), selection.activeHand(), selection.itemToken().orElse(""), com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.WIDE, null, nominations));
                var punchHit = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "ss14/weapons/punch");
                var punchMiss = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "ss14/weapons/punch_miss");
                var hits = sounds.stream().filter(p -> net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(p.getSound().value()).equals(punchHit)).toList();
                var misses = sounds.stream().filter(p -> net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(p.getSound().value()).equals(punchMiss)).toList();
                if (scenario < 2) {
                    if (result.status() != com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.HIT || hits.size() != 1 || !misses.isEmpty()) {
                        var packetSummary = sounds.stream().limit(8).map(p -> {
                            var key = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(p.getSound().value());
                            return key + "@" + p.getX() + "," + p.getY() + "," + p.getZ();
                        }).toList();
                        var camera = fixture.player().getCamera();
                        throw new GameTestAssertException("wide HIT diagnostic: status=" + result.status() + " cooldownConsumed=" + result.cooldownConsumed()
                                + " hits=" + hits.size() + " misses=" + misses.size() + " packets=" + packetSummary
                                + " configuredHit=" + hitCue + " registered=" + hitEventRegistered
                                + " targetHealth=" + valid.getHealth() + " removed=" + valid.isRemoved()
                                + " levelEntity=" + (helper.getLevel().getEntity(valid.getUUID()) == valid)
                                + " blunt=" + typed((net.minecraft.world.entity.LivingEntity) valid, "blunt")
                                + " camera=" + camera.getUUID() + " cameraPos=" + camera.position() + " bodyPos=" + active.body().position());
                    }
                    var expected = new net.minecraft.network.protocol.game.ClientboundSoundPacket(net.minecraft.core.Holder.direct(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(punchHit)), net.minecraft.sounds.SoundSource.PLAYERS, valid.getX(), valid.getY(), valid.getZ(), 1f, 1f, 0L);
                    if (hits.getFirst().getX() != expected.getX() || hits.getFirst().getY() != expected.getY() || hits.getFirst().getZ() != expected.getZ()) throw new GameTestAssertException("hit cue was not positioned at the valid nominated target");
                } else if (result.status() != com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.MISS || !result.cooldownConsumed() || !hits.isEmpty() || misses.size() != 1) throw new GameTestAssertException("invalid wide nominations did not consume cooldown and emit exactly one swing cue");
                helper.succeed();
            } catch (RuntimeException | Error e) { fixture.close(); throw e; }
        });
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_melee_roster", timeoutTicks=100)
    public static void registeredHumanSelectsResolvedProductionFists(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "melee-roster-human");
        var bodyRef = new net.minecraft.world.entity.LivingEntity[1];
        var targetRef = new net.minecraft.world.entity.Entity[1];
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void clean(net.minecraft.gametest.framework.GameTestInfo info) {
                try {
                     if (bodyRef[0] != null && !bodyRef[0].isRemoved()) bodyRef[0].discard();
                     if (targetRef[0] != null && !targetRef[0].isRemoved()) targetRef[0].discard();
                    fixture.close();
                } catch (Throwable t) {
                    if (info.getError() != null) info.getError().addSuppressed(t); else info.fail(t);
                }
            }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(i); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(i); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o, net.minecraft.gametest.framework.GameTestInfo n, net.minecraft.gametest.framework.GameTestRunner r) { clean(o); }
        });
        try {
            helper.runAtTickTime(2, () -> {
                try {
                    var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                    bodyRef[0] = active.body();
                    positionBody(helper, active.body());
                    var selection = MeleeWeaponResolver.resolve(active.body())
                            .orElseThrow(() -> new GameTestAssertException("resolved human has no melee selection"));
                    if (selection.kind() != MeleeWeaponResolver.Kind.UNARMED)
                        throw new GameTestAssertException("empty human hands did not select production fists: " + selection.kind());
                    requireProductionPunchHit(selection);
                    var profile = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolve(active.body())
                            .orElseThrow(() -> new GameTestAssertException("registered body identity unresolved"));
                    var melee = profile.component(com.juicyslew.moonstation14.ms14.character.components.MeleeWeaponComponent.class)
                            .orElseThrow(() -> new GameTestAssertException("resolved human lacks inherited MeleeWeapon component"));
                    if (melee.weapon().damage().getOrDefault("blunt", 0f) != 5f)
                        throw new GameTestAssertException("resolved human fists do not carry production blunt 5: " + melee.weapon().damage());
                    var target = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
                    if (target == null) throw new GameTestAssertException("target creation failed");
                    targetRef[0] = target;
                    target.setNoAi(true);
                    target.setPos(bodyRef[0].getX() + .5, bodyRef[0].getY(), bodyRef[0].getZ() + .9);
                    if (!helper.getLevel().addFreshEntity(target)) throw new GameTestAssertException("target insertion failed");
                    float typedBefore = typed((net.minecraft.world.entity.LivingEntity) target, "blunt");
                    com.juicyslew.moonstation14.ms14.combat.CombatModeRequestService.handle(fixture.player(),
                            new com.juicyslew.moonstation14.ms14.combat.CombatModeRequest(active.epoch(), 1, true));
                    var hit = com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(fixture.player(),
                            new com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest(active.epoch(), 1, selection.kind(), selection.handRevision(), selection.activeHand(), selection.itemToken().orElse(""), target.getUUID()));
                    if (hit.status() != com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.HIT || !hit.cooldownConsumed()
                            || Math.abs(typed((net.minecraft.world.entity.LivingEntity) target, "blunt") - typedBefore - 5f) > .011f)
                        throw new GameTestAssertException("production fists request/damage/cooldown mismatch: " + hit.status());
                    helper.succeed();
                } catch (RuntimeException | Error e) { fixture.close(); throw e; }
            });
        } catch (RuntimeException | Error e) { fixture.close(); throw e; }
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_melee_roster", timeoutTicks=100)
    public static void registeredCrowbarUsesResolvedProfileForRequestDamage(GameTestHelper helper) {
        registeredHeldWeaponHits(helper, com.juicyslew.moonstation14.item.ModItems.CROWBAR.get(), "blunt", 8f);
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_melee_roster", timeoutTicks=100)
    public static void registeredKitchenKnifeUsesResolvedProfileForRequestDamage(GameTestHelper helper) {
        registeredHeldWeaponHits(helper, com.juicyslew.moonstation14.item.ModItems.KITCHEN_KNIFE.get(), "slash", 10f);
    }

    private static void registeredHeldWeaponHits(GameTestHelper helper, net.minecraft.world.item.Item item,
                                                  String damageType, float expectedDamage) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "melee-roster-" + damageType);
        var refs = new net.minecraft.world.entity.Entity[3];
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void clean(net.minecraft.gametest.framework.GameTestInfo info) {
                try {
                    for (var entity : refs) if (entity != null && !entity.isRemoved()) entity.discard();
                    fixture.close();
                } catch (Throwable t) {
                    if (info.getError() != null) info.getError().addSuppressed(t); else info.fail(t);
                }
            }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(i); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(i); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o, net.minecraft.gametest.framework.GameTestInfo n, net.minecraft.gametest.framework.GameTestRunner r) { clean(o); }
        });
        try {
            helper.runAtTickTime(2, () -> {
                try {
                    var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                    var body = active.body(); refs[0] = body;
                    var bodyPosition = positionBody(helper, body);
                    var hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                    if (hands == null) throw new GameTestAssertException("registered body has no LiveHands");
                    String hand = hands.activeHand();
                    var stack = new net.minecraft.world.item.ItemStack(item);
                    var loose = com.juicyslew.moonstation14.entities.LooseItemEntity.create(helper.getLevel(), body.getEyePosition().add(body.getLookAngle().scale(.7)), stack);
                    loose.setNoGravity(true); refs[1] = loose;
                    if (!helper.getLevel().addFreshEntity(loose)
                            || com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.pickupSelected(fixture.player(), loose.getUUID(), hand, hands.revision(), ignored -> { })
                            != com.juicyslew.moonstation14.ms14.hands.live.BodyHandItemTransfer.Result.SUCCESS)
                        throw new GameTestAssertException("ordinary LiveHands item admission failed");
                    var selection = MeleeWeaponResolver.resolve(body).orElseThrow(() -> new GameTestAssertException("held production profile unresolved"));
                    if (selection.kind() != MeleeWeaponResolver.Kind.HELD || selection.profile().weapon().damage().getOrDefault(damageType, 0f) != expectedDamage)
                        throw new GameTestAssertException("wrong resolved held weapon composition: " + selection);
                    var target = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
                    if (target == null) throw new GameTestAssertException("target creation failed");
                    refs[2] = target;
                    target.setNoAi(true); target.setPos(bodyPosition.getX()+.5, bodyPosition.getY(), bodyPosition.getZ()+.9); helper.getLevel().addFreshEntity(target);
                    helper.runAtTickTime(24, () -> {
                        try {
                            var current = MeleeWeaponResolver.resolve(body).orElseThrow(() -> new GameTestAssertException("held profile unresolved at legal attack deadline"));
                            float healthBefore = target.getHealth();
                            float typedBefore = typed(target, damageType);
                            com.juicyslew.moonstation14.ms14.combat.CombatModeRequestService.handle(fixture.player(), new com.juicyslew.moonstation14.ms14.combat.CombatModeRequest(active.epoch(), 1, true));
                            var beforeDeadline = com.juicyslew.moonstation14.ms14.combat.MeleeCooldownSystem.nextAttackTick(body, current).orElseThrow();
                            var rejected = com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(fixture.player(), new com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest(active.epoch(), 1, current.kind(), current.handRevision(), current.activeHand(), current.itemToken().orElse(""), com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest.Mode.DISARM, target.getUUID(), java.util.List.of()));
                            var afterDeadline = com.juicyslew.moonstation14.ms14.combat.MeleeCooldownSystem.nextAttackTick(body, MeleeWeaponResolver.resolve(body).orElseThrow()).orElseThrow();
                            if (rejected.status() != com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.REJECTED || rejected.cooldownConsumed() || afterDeadline != beforeDeadline)
                                throw new GameTestAssertException("held-weapon DISARM consumed attempt/cooldown: " + rejected.status());
                            var refreshed = MeleeWeaponResolver.resolve(body).orElseThrow(() -> new GameTestAssertException("held profile unresolved before light attack"));
                            var hit = com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(fixture.player(), new com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest(active.epoch(), 2, refreshed.kind(), refreshed.handRevision(), refreshed.activeHand(), refreshed.itemToken().orElse(""), target.getUUID()));
                            var cooldownSelection = MeleeWeaponResolver.resolve(body).orElseThrow(() -> new GameTestAssertException("held profile unresolved after attack commit"));
                            var cooldown = com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(fixture.player(), new com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest(active.epoch(), 3, cooldownSelection.kind(), cooldownSelection.handRevision(), cooldownSelection.activeHand(), cooldownSelection.itemToken().orElse(""), target.getUUID()));
                            if (hit.status() != com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.HIT || !hit.cooldownConsumed()
                                    || cooldown.status() != com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.REJECTED
                                    || Math.abs(typed(target, damageType) - typedBefore - expectedDamage) > .011f
                                    || Math.abs(healthBefore - target.getHealth() - expectedDamage / com.juicyslew.moonstation14.ms14.damage.DamageSystem.TYPED_PER_HEALTH) > .011f)
                                throw new GameTestAssertException("production weapon request/damage/cooldown mismatch: " + hit.status() + ", " + cooldown.status());
                            helper.succeed();
                        } catch (RuntimeException | Error e) { fixture.close(); throw e; }
                    });
                } catch (RuntimeException | Error e) { fixture.close(); throw e; }
            });
        } catch (RuntimeException | Error e) { fixture.close(); throw e; }
    }

    private static float typed(net.minecraft.world.entity.LivingEntity target, String type) {
        var damage = target.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get());
        return damage == null ? 0f : damage.getMap().getOrDefault(type, 0f);
    }

    private static void requireProductionPunchHit(MeleeWeaponResolver.Selection selection) {
        var hit = selection.profile().weapon().audio().hit();
        if (hit == null || !net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.containsKey(hit.event()))
            throw new GameTestAssertException("production melee profile must define a registered hit cue: " + hit);
        var punch = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "ss14/weapons/punch");
        if (!punch.equals(hit.event())) throw new GameTestAssertException("production fists must resolve the registered punch hit cue: " + hit.event());
    }

    private static net.minecraft.core.BlockPos positionBody(GameTestHelper helper, net.minecraft.world.entity.LivingEntity body) {
        var p = helper.absolutePos(new net.minecraft.core.BlockPos(3, 2, 3));
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            helper.getLevel().setBlock(p.offset(x, -1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        body.teleportTo(p.getX() + .5, p.getY(), p.getZ() + .5);
        body.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        body.tick();
        return p;
    }
}
