package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.juicyslew.moonstation14.ms14.damage.DamageFeedbackPayload;

import java.util.ArrayList;

/** Live registered-player melee against production loose jug entities. */
@GameTestTags({"melee", "combat"})
@PrefixGameTestTemplate(false)
public final class RegisteredMeleeJugGameTests {
    private RegisteredMeleeJugGameTests() { }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_melee_jug", timeoutTicks=100)
    public static void registeredFistsDamageProductionJug(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "melee-jug");
        var refs = new net.minecraft.world.entity.Entity[2];
        var dispatched = new ArrayList<DamageFeedbackPayload>();
        DamageFeedbackPayload.observeDispatch(dispatched::add);
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void clean(net.minecraft.gametest.framework.GameTestInfo info) {
                try { for (var e : refs) if (e != null && !e.isRemoved()) e.discard(); fixture.close(); }
                catch (Throwable t) { if (info.getError() != null) info.getError().addSuppressed(t); else info.fail(t); }
                finally { DamageFeedbackPayload.observeDispatch(null); }
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
                var spawn = helper.absoluteVec(new net.minecraft.world.phys.Vec3(3, 2, 3));
                var floor = net.minecraft.core.BlockPos.containing(spawn);
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) helper.getLevel().setBlock(floor.offset(x, -1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                body.teleportTo(spawn.x + .5, spawn.y, spawn.z + .5); if (body instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true); body.setYRot(0); body.setYHeadRot(0); body.setXRot(0); body.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); body.tick();
                var jug = com.juicyslew.moonstation14.entities.LooseItemEntity.create(helper.getLevel(), body.position().add(0, 0, .9), new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get()));
                refs[1] = jug; jug.setNoGravity(true);
                if (!helper.getLevel().addFreshEntity(jug)) throw new GameTestAssertException("production jug insertion failed");
                if (!com.juicyslew.moonstation14.ms14.combat.CommonMeleeTargetEligibility.damageable(jug)) throw new GameTestAssertException("production jug is not melee eligible");
                var selection = com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.resolve(body).orElseThrow(() -> new GameTestAssertException("registered fists unresolved"));
                com.juicyslew.moonstation14.ms14.combat.CombatModeRequestService.handle(fixture.player(), new com.juicyslew.moonstation14.ms14.combat.CombatModeRequest(active.epoch(), 1, true));
                var hit = com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(fixture.player(), new com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest(active.epoch(), 1, selection.kind(), selection.handRevision(), selection.activeHand(), selection.itemToken().orElse(""), jug.getUUID()));
                var state = jug.getItem().getOrDefault(com.juicyslew.moonstation14.component.ModDataComponents.ITEM_DAMAGE_STATE.get(), com.juicyslew.moonstation14.ms14.damage.ItemDamageState.EMPTY);
                if (hit.status() != com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.HIT || !hit.cooldownConsumed() || state.damage().values().stream().mapToDouble(Float::doubleValue).sum() != 5d)
                    throw new GameTestAssertException("registered fists request did not apply one production melee damage increment");
                if (dispatched.size() != 1) throw new GameTestAssertException("first registered fist hit must dispatch exactly one damage feedback payload");
                var feedback = dispatched.get(0);
                if (feedback.entityId() != jug.getId() || !feedback.entityUuid().equals(jug.getUUID())
                        || !feedback.redFlash() || feedback.duration() != DamageFeedbackPayload.MELEE_DURATION)
                    throw new GameTestAssertException("registered fist feedback must identify the jug and carry melee red-flash duration");
                hitRemainingRegisteredJugs(helper, fixture, active, body, jug, refs, 1);
            } catch (RuntimeException | Error e) { DamageFeedbackPayload.observeDispatch(null); fixture.close(); throw e; }
        });
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="registered_melee_jug", timeoutTicks=100)
    public static void typedOverkillDestroysProductionJugWithoutNormalBreakEffects(GameTestHelper helper) {
        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "melee-jug-overkill");
        var refs = new net.minecraft.world.entity.Entity[2];
        var sounds = new ArrayList<net.minecraft.network.protocol.game.ClientboundSoundPacket>();
        fixture.observeOutbound(packet -> { if (packet instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket p) sounds.add(p); });
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void clean(net.minecraft.gametest.framework.GameTestInfo i) { try { for (var e : refs) if (e != null && !e.isRemoved()) e.discard(); fixture.close(); } catch (Throwable t) { if (i.getError() != null) i.getError().addSuppressed(t); else i.fail(t); } }
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(i); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(i); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o, net.minecraft.gametest.framework.GameTestInfo n, net.minecraft.gametest.framework.GameTestRunner r) { clean(o); }
        });
        helper.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var spawn = helper.absoluteVec(new net.minecraft.world.phys.Vec3(3, 2, 3));
                var floor = net.minecraft.core.BlockPos.containing(spawn);
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) helper.getLevel().setBlock(floor.offset(x, -1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                var body = active.body(); body.teleportTo(spawn.x + .5, spawn.y, spawn.z + .5); if (body instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true); body.tick();
                var jug = com.juicyslew.moonstation14.entities.LooseItemEntity.create(helper.getLevel(), body.position().add(0, 0, .9), new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.JUG.get()));
                refs[0] = body; refs[1] = jug; jug.setNoGravity(true);
                if (!helper.getLevel().addFreshEntity(jug)) throw new GameTestAssertException("production jug insertion failed");
                long plasticBefore = helper.getLevel().getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class, jug.getBoundingBox().inflate(8), e -> e.getItem().is(com.juicyslew.moonstation14.item.ModItems.PLASTIC.get())).size();
                sounds.clear();
                if (!jug.applyTypedItemDamage(helper.getLevel(), java.util.Map.of("blunt", 200f), false) || !jug.isRemoved()) throw new GameTestAssertException("typed overkill did not remove production jug");
                long plasticAfter = helper.getLevel().getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class, jug.getBoundingBox().inflate(8), e -> e.getItem().is(com.juicyslew.moonstation14.item.ModItems.PLASTIC.get())).size();
                if (plasticAfter > plasticBefore) throw new GameTestAssertException("typed overkill created additional plastic loose items");
                if (!sounds.isEmpty()) throw new GameTestAssertException("typed overkill emitted normal break sound");
                helper.succeed();
            } catch (RuntimeException | Error e) { fixture.close(); throw e; }
        });
    }

    private static void hitRemainingRegisteredJugs(GameTestHelper helper, RegisteredServerPlayerFixture fixture,
            RegisteredPlayerReadyDriver.Active active, net.minecraft.world.entity.LivingEntity body,
            com.juicyslew.moonstation14.entities.LooseItemEntity jug, net.minecraft.world.entity.Entity[] refs, int hitNumber) {
        if (hitNumber == 4) {
            if (!jug.isRemoved()) throw new GameTestAssertException("jug did not break on its fourth light fist hit");
            helper.succeed();
            return;
        }
        helper.runAtTickTime(2 + 22 * hitNumber, () -> {
            try {
                var selection = com.juicyslew.moonstation14.ms14.combat.MeleeWeaponResolver.resolve(body).orElseThrow(() -> new GameTestAssertException("registered fists unresolved at cooldown deadline"));
                var request = new com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequest(active.epoch(), hitNumber + 1, selection.kind(), selection.handRevision(), selection.activeHand(), selection.itemToken().orElse(""), jug.getUUID());
                var result = com.juicyslew.moonstation14.ms14.combat.MeleeAttackRequestService.handle(fixture.player(), request);
                if (result.status() != com.juicyslew.moonstation14.ms14.combat.MeleeAttackReply.Status.HIT || !result.cooldownConsumed()) throw new GameTestAssertException("registered jug fist hit rejected: " + result.status());
                if (hitNumber < 3) {
                    var state = jug.getItem().getOrDefault(com.juicyslew.moonstation14.component.ModDataComponents.ITEM_DAMAGE_STATE.get(), com.juicyslew.moonstation14.ms14.damage.ItemDamageState.EMPTY);
                    double total = state.damage().values().stream().mapToDouble(Float::doubleValue).sum();
                    if (Math.abs(total - (hitNumber + 1) * 5d) > .001 || jug.isRemoved()) throw new GameTestAssertException("jug damage threshold changed before four hits: " + total);
                    hitRemainingRegisteredJugs(helper, fixture, active, body, jug, refs, hitNumber + 1);
                } else hitRemainingRegisteredJugs(helper, fixture, active, body, jug, refs, 4);
            } catch (RuntimeException | Error e) { fixture.close(); throw e; }
        });
    }
}
