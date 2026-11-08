package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.gametest.support.ScopedPrototypeCatalogFixture;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.combat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"shared-melee-runtime-fixture"})
@PrefixGameTestTemplate(false)
public final class RegisteredMeleeGameTests {
    private RegisteredMeleeGameTests() { }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="shared_melee_runtime_fixture", timeoutTicks=100)
    public static void admittedRequestsUseResolvedUnarmedMelee(GameTestHelper helper) {
        var characterType = ModCharacters.CHARACTER_TYPE;
        ScopedPrototypeCatalogFixture.replace(helper, characterType, catalog -> {
            var id = ModCharacters.HUMAN_ID;
            var json = com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC.encodeStart(
                    com.mojang.serialization.JsonOps.INSTANCE, catalog.get(id)).getOrThrow().getAsJsonObject();
            var components = json.has("components") ? json.getAsJsonArray("components") : new com.google.gson.JsonArray();
            json.add("components", components);
            com.juicyslew.moonstation14.ms14.character.CharacterPrototypeComponentUpsert.upsert(components,com.google.gson.JsonParser.parseString("{\"type\":\"CombatMode\",\"can_disarm\":true}").getAsJsonObject());
            com.juicyslew.moonstation14.ms14.character.CharacterPrototypeComponentUpsert.upsert(components,com.google.gson.JsonParser.parseString("{\"type\":\"MeleeWeapon\",\"weapon\":{\"damage\":{\"blunt\":5}}}").getAsJsonObject());
            catalog.put(id, com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC
                    .parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow());
            return catalog;
        });

        var fixture = RegisteredServerPlayerFixture.register(helper.getLevel(), "melee-fixture");
        var bodyRef = new net.minecraft.world.entity.LivingEntity[1];
        var targetRef = new net.minecraft.world.entity.LivingEntity[1];
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void clean(net.minecraft.gametest.framework.GameTestInfo info) {
                try {
                    if (targetRef[0] != null && !targetRef[0].isRemoved()) targetRef[0].discard();
                    if (bodyRef[0] != null && !bodyRef[0].isRemoved()) bodyRef[0].discard();
                    fixture.close();
                } catch (Throwable t) { if (info.getError() != null) info.getError().addSuppressed(t); else info.fail(t); }
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
                    var body = active.body(); bodyRef[0] = body;
                    BlockPos p = helper.absolutePos(new BlockPos(3, 2, 3));
                    for (int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) helper.getLevel().setBlock(p.offset(x,-1,z), Blocks.STONE.defaultBlockState(),3);
                    body.teleportTo(p.getX()+.5,p.getY(),p.getZ()+.5); body.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); body.tick();
                    var selection = MeleeWeaponResolver.resolve(body).orElseThrow(() -> new GameTestAssertException("unarmed selection absent"));
                    if (selection.kind() != MeleeWeaponResolver.Kind.UNARMED) throw new GameTestAssertException("expected actual UNARMED selection");
                    var victim = EntityType.VILLAGER.create(helper.getLevel());
                    if (victim == null) throw new GameTestAssertException("villager spawn failed"); targetRef[0] = victim;
                    victim.setNoAi(true); victim.setPos(p.getX()+.5,p.getY(),p.getZ()+.9); helper.getLevel().addFreshEntity(victim);
                    victim.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    var damage = victim.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get());
                    float before = damage == null ? 0f : damage.getMap().getOrDefault("blunt",0f);
                    float healthBefore = victim.getHealth();
                    var off = CombatModeRequestService.handle(fixture.player(), new CombatModeRequest(active.epoch(),1,false));
                    var hitRequest = new MeleeAttackRequest(active.epoch(),2,selection.kind(),selection.handRevision(),selection.activeHand(),selection.itemToken().orElse(""),victim.getUUID());
                    var rejected = MeleeAttackRequestService.handle(fixture.player(),hitRequest);
                    if (rejected.status()!=MeleeAttackReply.Status.REJECTED) throw new GameTestAssertException("mode-off attack not rejected: "+rejected.status());
                    if (Math.abs(blunt(victim)-before) > .011f || Math.abs(victim.getHealth()-healthBefore) > .011f)
                        throw new GameTestAssertException("mode-off rejection changed target damage or native health");
                    var on = CombatModeRequestService.handle(fixture.player(),new CombatModeRequest(active.epoch(),2,true));
                    var hit = MeleeAttackRequestService.handle(fixture.player(),new MeleeAttackRequest(active.epoch(),3,selection.kind(),selection.handRevision(),selection.activeHand(),selection.itemToken().orElse(""),victim.getUUID()));
                    var current = victim.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get());
                    float after = current==null?0f:current.getMap().getOrDefault("blunt",0f);
                    float healthAfterHit = victim.getHealth();
                    var cooldown = MeleeAttackRequestService.handle(fixture.player(),new MeleeAttackRequest(active.epoch(),4,selection.kind(),selection.handRevision(),selection.activeHand(),selection.itemToken().orElse(""),victim.getUUID()));
                    var replay = MeleeAttackRequestService.handle(fixture.player(),hitRequest);
                    float healthAfterRejected = victim.getHealth();
                    float bluntAfterRejected = blunt(victim);
                    if (off.status()!=CombatModeRequestResult.Status.ACCEPTED || on.status()!=CombatModeRequestResult.Status.ACCEPTED
                            || hit.status()!=MeleeAttackReply.Status.HIT || !hit.cooldownConsumed()
                            || Math.abs((after-before)-5f) > .011f
                            || Math.abs((healthBefore-healthAfterHit)-5f/com.juicyslew.moonstation14.ms14.damage.DamageSystem.TYPED_PER_HEALTH) > .011f
                            || cooldown.status()!=MeleeAttackReply.Status.REJECTED || replay.status()!=MeleeAttackReply.Status.REPLAY)
                        throw new GameTestAssertException("melee request flow failed: off="+off.status()+" on="+on.status()+" hit="+hit.status()+" cd="+cooldown.status()+" replay="+replay.status()+" damage="+(after-before));
                    if (Math.abs(bluntAfterRejected-after) > .011f || Math.abs(healthAfterRejected-healthAfterHit) > .011f)
                        throw new GameTestAssertException("cooldown rejection/replay changed target damage or health");
                    helper.succeed();
                } catch (RuntimeException | Error e) { fixture.close(); throw e; }
            });
        } catch (RuntimeException | Error e) { fixture.close(); throw e; }
    }

    private static float blunt(net.minecraft.world.entity.LivingEntity body) {
        var damage = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get());
        return damage == null ? 0f : damage.getMap().getOrDefault("blunt", 0f);
    }
}
