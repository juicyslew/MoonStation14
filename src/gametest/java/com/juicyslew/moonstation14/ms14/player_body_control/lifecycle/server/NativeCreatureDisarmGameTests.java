package com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import com.juicyslew.moonstation14.gametest.support.ScopedPrototypeCatalogFixture;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.combat.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"native-creature-disarm", "melee-disarm"})
@PrefixGameTestTemplate(false)
public final class NativeCreatureDisarmGameTests {
    private NativeCreatureDisarmGameTests() { }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="native_creature_disarm", timeoutTicks=100)
    public static void emptyHandDisarmDamagesNativeKoboldStamina(GameTestHelper h) {
        ScopedPrototypeCatalogFixture.replace(h, ModCharacters.CHARACTER_TYPE, c -> {
            var id = ModCharacters.HUMAN_ID;
            var json = com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC
                    .encodeStart(com.mojang.serialization.JsonOps.INSTANCE, c.get(id)).getOrThrow().getAsJsonObject();
            var components = json.getAsJsonArray("components");
            com.juicyslew.moonstation14.ms14.character.CharacterPrototypeComponentUpsert.upsert(components,
                    com.google.gson.JsonParser.parseString("{\"type\":\"CombatMode\",\"can_disarm\":true,\"base_disarm_fail_chance\":0}").getAsJsonObject());
            c.put(id, com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC
                    .parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow());
            return c;
        });
        var fixture = RegisteredServerPlayerFixture.register(h.getLevel(), "native-disarm");
        var refs = new net.minecraft.world.entity.Entity[2];
        var cleaned = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable cleanup = () -> { if (!cleaned.compareAndSet(false, true)) return; for (var e : refs) if (e != null && !e.isRemoved()) e.discard(); fixture.close(); };
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void clean() { cleanup.run(); }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o, net.minecraft.gametest.framework.GameTestInfo n, net.minecraft.gametest.framework.GameTestRunner r) { clean(); }
        });
        h.runAtTickTime(2, () -> {
            try {
                var active = RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var attacker = active.body(); refs[0] = attacker;
                var origin = h.absolutePos(new net.minecraft.core.BlockPos(3, 1, 3)).getCenter();
                attacker.teleportTo(origin.x, origin.y, origin.z); attacker.setYRot(0); attacker.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); attacker.tick();
                var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("moonstation14:kobold"));
                if (type == null || !(type.create(h.getLevel()) instanceof Mob target)) throw new GameTestAssertException("native kobold type unavailable");
                refs[1] = target; target.setNoAi(true); target.setPos(origin.x, origin.y, origin.z + .8);
                if (!h.getLevel().addFreshEntity(target)) throw new GameTestAssertException("native kobold spawn failed");
                var host = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
                var character = ModCharacters.characterForHost(h.getLevel(), host).orElseThrow();
                if (!com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.enroll(target, h.getLevel(), character)) throw new GameTestAssertException("kobold enrollment failed");
                if (com.juicyslew.moonstation14.ms14.hands.live.BodyHandBootstrap.ensure(target) == com.juicyslew.moonstation14.ms14.hands.live.BodyHandBootstrap.Result.REJECTED) throw new GameTestAssertException("kobold hand bootstrap rejected");
                var state = com.juicyslew.moonstation14.ms14.character.stamina.StaminaSystem.state(target);
                float hp = target.getHealth();
                var selection = MeleeWeaponResolver.resolve(attacker).orElseThrow();
                CombatModeRequestService.handle(fixture.player(), new CombatModeRequest(active.epoch(), 1, true));
                var reply = MeleeAttackRequestService.handle(fixture.player(), new MeleeAttackRequest(active.epoch(), 1, selection.kind(), selection.handRevision(), selection.activeHand(), selection.itemToken().orElse(""), MeleeAttackRequest.Mode.DISARM, target.getUUID(), java.util.List.of()));
                var after = com.juicyslew.moonstation14.ms14.character.stamina.StaminaSystem.state(target);
                if (reply.status() != MeleeAttackReply.Status.DISARM_HANDLED || after.damage() <= state.damage() || target.getHealth() != hp
                        || !com.juicyslew.moonstation14.ms14.character.CharacterControlSystem.isStunned(target))
                    throw new GameTestAssertException("emptyhand disarm failed to deal stamina-only damage: " + reply.status());
                h.succeed();
            } catch (RuntimeException | Error e) { cleanup.run(); throw e; }
        });
    }
}
