package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the authoritative trusted NPC melee commit, not an isolated damage helper. */
@GameTestTags({"melee-npc", "shared-melee-runtime-fixture"})
@PrefixGameTestTemplate(false)
public final class TrustedNpcMeleeGameTests {
    private TrustedNpcMeleeGameTests() { }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="trusted_npc_melee", timeoutTicks=40)
    public static void trustedNpcCommitUsesResolvedProfileAndPublishesFeedback(GameTestHelper h) {
        var level = h.getLevel();
        var attacker = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "kobold")).create(level);
        var victim = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (!(attacker instanceof net.minecraft.world.entity.Mob mob) || !(victim instanceof net.minecraft.world.entity.LivingEntity target)) throw new GameTestAssertException("NPC fixture creation failed");
        mob.setNoAi(true); mob.setNoGravity(true); target.setNoGravity(true);
        var p = h.absolutePos(new net.minecraft.core.BlockPos(3, 2, 3));
        for (int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) level.setBlock(p.offset(x,-1,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        mob.setPos(p.getX()+.5,p.getY(),p.getZ()+.5); target.setPos(p.getX()+.5,p.getY(),p.getZ()+.9);
        level.addFreshEntity(mob); level.addFreshEntity(target);
        var profile = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(mob).orElseThrow();
        var attack = profile.component(com.juicyslew.moonstation14.ms14.character.components.MeleeAttackComponent.class).orElseThrow();
        if (attack.damage().types().getOrDefault("slash",0f)!=5f || attack.damage().types().getOrDefault("piercing",0f)!=4f) throw new GameTestAssertException("resolved Kobold damage mismatch");
        float before = typed(target,"slash"), beforePiercing=typed(target,"piercing");
        var feedback = new java.util.ArrayList<MeleeAttackEvents.TrustedNpcFeedbackPublishedEvent>();
        var listener = new Object() { @net.neoforged.bus.api.SubscribeEvent public void published(MeleeAttackEvents.TrustedNpcFeedbackPublishedEvent event) { if (event.attacker() == mob) feedback.add(event); } };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(listener);
        var listenerRegistered = new java.util.concurrent.atomic.AtomicBoolean(true);
        Runnable cleanup = () -> {
            if (listenerRegistered.compareAndSet(true, false)) net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
            com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.forget(mob);
            if (!mob.isRemoved()) mob.discard();
            if (!target.isRemoved()) target.discard();
        };
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void clean() { cleanup.run(); }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { clean(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { clean(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) { clean(); }
        });
        if (!com.juicyslew.moonstation14.ms14.mobs.NpcBehaviorSystem.autonomousNpcEligible(mob, (net.minecraft.server.level.ServerLevel) level, profile)) throw new GameTestAssertException("resolved kobold not autonomously eligible");
        var now = level.getGameTime();
        if (!com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.provoke(mob, target, now)) throw new GameTestAssertException("NPC retaliation memory was not recorded");
        boolean committed = com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.attack(mob,target,now);
        if (!committed || Math.abs(typed(target,"slash")-before-5f)>.02f || Math.abs(typed(target,"piercing")-beforePiercing-4f)>.02f) throw new GameTestAssertException("trusted NPC typed damage did not commit once");
        if (feedback.size()!=1 || feedback.get(0).arc()==null || feedback.get(0).arc().weapon().arc()==null || !feedback.get(0).arc().weapon().arc().equals(attack.arc()) || feedback.get(0).cue()==null || !feedback.get(0).cue().event().equals(attack.audio().hit().event())) throw new GameTestAssertException("trusted NPC published feedback missing resolved Kobold sound/arc IDs");
        if (!feedback.get(0).attacker().getUUID().equals(mob.getUUID()) || !feedback.get(0).victim().getUUID().equals(target.getUUID())) throw new GameTestAssertException("feedback actor/victim identity mismatch");
        if (com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.attack(mob,target,now)) throw new GameTestAssertException("cooldown repeat unexpectedly committed");
        if (feedback.size()!=1) throw new GameTestAssertException("rejected repeat published feedback");
        if (Math.abs(typed(target,"slash")-before-5f)>.02f) throw new GameTestAssertException("repeat added damage");
        target.setPos(mob.getX()+1, mob.getY(), mob.getZ());
        level.setBlock(net.minecraft.core.BlockPos.containing(mob.getX()+.5, mob.getY()+1, mob.getZ()), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        mob.getSensing().tick(); // Refresh the LOS cache after changing fixture geometry synchronously.
        com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.forget(mob);
        com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.provoke(mob,target,now+100);
        float blockedBefore = typed(target,"slash");
        if (com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.attack(mob,target,now+100)) throw new GameTestAssertException("blocked-LOS strike committed");
        if (typed(target,"slash") != blockedBefore) throw new GameTestAssertException("blocked-LOS strike added damage");
        level.setBlock(net.minecraft.core.BlockPos.containing(mob.getX()+.5, mob.getY()+1, mob.getZ()), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        target.setPos(mob.getX()+20, mob.getY(), mob.getZ());
        com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.forget(mob);
        com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.provoke(mob,target,now+200);
        if (com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.attack(mob,target,now+200)) throw new GameTestAssertException("out-of-range strike committed");
        cleanup.run(); h.succeed();
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="trusted_npc_zero_damage", timeoutTicks=40)
    public static void zeroEffectiveDamageConsumesNpcStrikeCooldown(GameTestHelper h) {
        var catalog = com.juicyslew.moonstation14.gametest.support.ScopedPrototypeCatalogFixture.replace(h,
                com.juicyslew.moonstation14.ms14.character.ModCharacters.CHARACTER_TYPE, entries -> {
                    var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "kobold");
                    var json = com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, entries.get(id)).getOrThrow().getAsJsonObject();
                    var components = json.getAsJsonArray("components");
                    for (int i=0;i<components.size();i++) if (components.get(i).getAsJsonObject().get("type").getAsString().equals("MeleeAttack")) {
                        var component = components.get(i).getAsJsonObject();
                        component.getAsJsonObject("damage").add("types", new com.google.gson.JsonObject());
                        break;
                    }
                    entries.put(id, com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow());
                    return entries;
                });
        var level = h.getLevel();
        var attacker = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "kobold")).create(level);
        var victim = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (!(attacker instanceof net.minecraft.world.entity.Mob mob) || !(victim instanceof net.minecraft.world.entity.LivingEntity target)) throw new GameTestAssertException("NPC fixture creation failed");
        mob.setNoAi(true); mob.setNoGravity(true); target.setNoGravity(true);
        var p = h.absolutePos(new net.minecraft.core.BlockPos(3, 2, 3));
        for (int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) level.setBlock(p.offset(x,-1,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        mob.setPos(p.getX()+.5,p.getY(),p.getZ()+.5); target.setPos(p.getX()+.5,p.getY(),p.getZ()+.9);
        level.addFreshEntity(mob); level.addFreshEntity(target);
        var profile = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(mob).orElseThrow();
        var attack = profile.component(com.juicyslew.moonstation14.ms14.character.components.MeleeAttackComponent.class).orElseThrow();
        var before = target.getHealth();
        var feedback = new java.util.ArrayList<MeleeAttackEvents.TrustedNpcFeedbackPublishedEvent>();
        var listener = new Object() { @net.neoforged.bus.api.SubscribeEvent public void published(MeleeAttackEvents.TrustedNpcFeedbackPublishedEvent event) { if (event.attacker() == mob) feedback.add(event); } };
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(listener);
        try {
            var now = level.getGameTime();
            if (!com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.provoke(mob, target, now)) throw new GameTestAssertException("NPC retaliation memory was not recorded");
            if (!com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.attack(mob,target,now)) throw new GameTestAssertException("zero-effective-damage strike did not consume/commit attack");
            var expectedCue = MeleeSoundPolicy.outcome(attack.audio(), null, false);
            if (target.getHealth()!=before || feedback.size()!=1 || feedback.get(0).cue()==null || expectedCue==null || !feedback.get(0).cue().event().equals(expectedCue.event()) || feedback.get(0).arc()==null || !feedback.get(0).arc().weapon().arc().equals(attack.arc())) throw new GameTestAssertException("zero-damage strike feedback/health mismatch");
            if (com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.attack(mob,target,now) || feedback.size()!=1) throw new GameTestAssertException("zero-damage strike did not consume cooldown");
            if (!com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.attack(mob,target,now+attack.cooldownTicks())) throw new GameTestAssertException("strike did not become available after cooldown");
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);
            catalog.restore();
            com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.forget(mob);
            mob.discard(); target.discard();
        }
        h.succeed();
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="trusted_npc_moving_miss", timeoutTicks=40)
    public static void movingTargetCanConsumeNpcStrikeAsMiss(GameTestHelper h) {
        assertNpcChanceCase(h, 1.0, true, true);
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="trusted_npc_stationary_hit", timeoutTicks=40)
    public static void stationaryTargetHitsDespiteMaximumMissChance(GameTestHelper h) {
        assertNpcChanceCase(h, 1.0, false, false);
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="trusted_npc_registered_body_velocity", timeoutTicks=100)
    public static void registeredControlledHumanVelocityCausesNpcMiss(GameTestHelper h) {
        var catalog = com.juicyslew.moonstation14.gametest.support.ScopedPrototypeCatalogFixture.replace(h,
                com.juicyslew.moonstation14.ms14.character.ModCharacters.CHARACTER_TYPE, entries -> {
                    var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "kobold");
                    var json = com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, entries.get(id)).getOrThrow().getAsJsonObject();
                    for (var component : json.getAsJsonArray("components"))
                        if (component.getAsJsonObject().get("type").getAsString().equals("NpcRetaliation"))
                            component.getAsJsonObject().addProperty("meleeMissChance", 1.0);
                    entries.put(id, com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow());
                    return entries;
                });
        var fixture = com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture.register(h.getLevel(), "npc-real-human-velocity");
        var refs = new net.minecraft.world.entity.Entity[2];
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void clean(net.minecraft.gametest.framework.GameTestInfo i) {
                try { for (var e : refs) if (e != null && !e.isRemoved()) e.discard(); fixture.close(); catalog.restore(); }
                catch (Throwable t) { if (i.getError() != null) i.getError().addSuppressed(t); else i.fail(t); }
            }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i) { }
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(i); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i, net.minecraft.gametest.framework.GameTestRunner r) { clean(i); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o, net.minecraft.gametest.framework.GameTestInfo n, net.minecraft.gametest.framework.GameTestRunner r) { clean(o); }
        });
        h.runAtTickTime(2, () -> {
            try {
                var active = com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver.readyAndQuery(fixture);
                var body = active.body(); refs[0] = body;
                var origin = h.absolutePos(new net.minecraft.core.BlockPos(3, 2, 3));
                for (int x=-2;x<=2;x++) for(int z=-2;z<=2;z++) h.getLevel().setBlock(origin.offset(x,-1,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
                body.teleportTo(origin.getX()+.5, origin.getY(), origin.getZ()+.5);
                body.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                var start = body.position(); body.tick();
                submitRealMovement(h, fixture, active, 0, 8);
                h.runAfterDelay(8, () -> {
                    var velocity = body.getDeltaMovement();
                    if (body.getUUID().equals(fixture.player().getUUID()) || body.position().distanceToSqr(start) < .01 || velocity.lengthSqr() == 0)
                        throw new GameTestAssertException("registered human body did not retain real movement velocity at attack boundary: pos="+body.position()+" delta="+velocity);
                    var attacker = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","kobold")).create(h.getLevel());
                    if (!(attacker instanceof net.minecraft.world.entity.Mob mob)) throw new GameTestAssertException("kobold creation failed");
                    refs[1] = mob; mob.setNoAi(true); mob.setNoGravity(true);
                    mob.setPos(body.getX()+.5, body.getY(), body.getZ()+.6);
                    if (!h.getLevel().addFreshEntity(mob)) throw new GameTestAssertException("kobold insertion failed");
                    var melee = com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(mob).orElseThrow()
                            .component(com.juicyslew.moonstation14.ms14.character.components.MeleeAttackComponent.class).orElseThrow();
                    float health = body.getHealth(), slash = typed(body,"slash"), piercing = typed(body,"piercing");
                    var feedback = new java.util.ArrayList<MeleeAttackEvents.TrustedNpcFeedbackPublishedEvent>();
                    var listener = new Object() { @net.neoforged.bus.api.SubscribeEvent public void published(MeleeAttackEvents.TrustedNpcFeedbackPublishedEvent e) { if (e.attacker()==mob) feedback.add(e); } };
                    net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(listener);
                    try {
                        long now=h.getLevel().getGameTime();
                        if (!com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.provoke(mob,body,now)
                                || !com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.attack(mob,body,now)) throw new GameTestAssertException("real moving-body miss did not consume strike");
                        if (body.getHealth()!=health || typed(body,"slash")!=slash || typed(body,"piercing")!=piercing || feedback.size()!=1
                                || feedback.getFirst().victim()!=null || feedback.getFirst().cue()==null
                                || !feedback.getFirst().cue().event().equals(melee.audio().swing().event()))
                            throw new GameTestAssertException("registered moving human body did not produce undamaged swing miss feedback");
                    } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener); com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.forget(mob); }
                    h.succeed();
                });
            } catch (RuntimeException | Error e) { fixture.close(); catalog.restore(); throw e; }
        });
    }

    private static void submitRealMovement(GameTestHelper h,
            com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture fixture,
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver.Active active, int frame, int frames) {
        if (frame >= frames) return;
        h.runAfterDelay(frame == 0 ? 0 : 1, () -> {
            com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver.submitAcceptedIntent(fixture,
                    new com.juicyslew.moonstation14.ms14.player_body_control.network.GhostControlPayloads.Intent(active.epoch(), frame+1L,
                            (short)0, (short)1000, (byte)0, 0, active.body().getYRot(), active.body().getXRot()));
            if (frame+1 < frames) submitRealMovement(h,fixture,active,frame+1,frames);
        });
    }

    @GameTest(template="atmos_large_empty", templateNamespace="moonstation14", batch="trusted_npc_zero_miss_chance", timeoutTicks=40)
    public static void movingTargetHitsWithZeroMissChance(GameTestHelper h) {
        assertNpcChanceCase(h, 0.0, true, false);
    }

    private static void assertNpcChanceCase(GameTestHelper h, double chance, boolean movingTarget, boolean expectMiss) {
        var catalog = com.juicyslew.moonstation14.gametest.support.ScopedPrototypeCatalogFixture.replace(h,
                com.juicyslew.moonstation14.ms14.character.ModCharacters.CHARACTER_TYPE, entries -> {
                    var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", "kobold");
                    var json = com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, entries.get(id)).getOrThrow().getAsJsonObject();
                    var components = json.getAsJsonArray("components");
                    for (int i=0;i<components.size();i++) if (components.get(i).getAsJsonObject().get("type").getAsString().equals("NpcRetaliation"))
                        components.get(i).getAsJsonObject().addProperty("meleeMissChance", chance);
                    entries.put(id, com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow());
                    return entries;
                });
        var level=h.getLevel(); var attacker=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","kobold")).create(level);
        var victim=net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (!(attacker instanceof net.minecraft.world.entity.Mob mob) || !(victim instanceof net.minecraft.world.entity.LivingEntity target)) throw new GameTestAssertException("NPC fixture creation failed");
        mob.setNoAi(true); mob.setNoGravity(true); target.setNoGravity(true);
        var p=h.absolutePos(new net.minecraft.core.BlockPos(3,2,3)); for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)level.setBlock(p.offset(x,-1,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        mob.setPos(p.getX()+.5,p.getY(),p.getZ()+.5); target.setPos(p.getX()+.5,p.getY(),p.getZ()+.9);
        level.addFreshEntity(mob);level.addFreshEntity(target);
        var feedback=new java.util.ArrayList<MeleeAttackEvents.TrustedNpcFeedbackPublishedEvent>(); var listener=new Object(){@net.neoforged.bus.api.SubscribeEvent public void published(MeleeAttackEvents.TrustedNpcFeedbackPublishedEvent e){if(e.attacker()==mob)feedback.add(e);}};
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(listener);
        try {
            var profile=com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem.resolveForActor(mob).orElseThrow(); var melee=profile.component(com.juicyslew.moonstation14.ms14.character.components.MeleeAttackComponent.class).orElseThrow();
            float hp=target.getHealth();
            long now=level.getGameTime(); com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.provoke(mob,target,now); target.setDeltaMovement(movingTarget?.1:0,0,0); mob.setDeltaMovement(movingTarget?0:.1,0,0);
            if(!com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.attack(mob,target,now))throw new GameTestAssertException("moving miss did not consume strike");
            if(expectMiss) {
                if(target.getHealth()!=hp||feedback.size()!=1||feedback.get(0).cue()==null||!feedback.get(0).cue().event().equals(melee.audio().swing().event())||feedback.get(0).arc()==null||feedback.get(0).victim()!=null)throw new GameTestAssertException("moving miss damaged or omitted swing feedback");
            } else if(target.getHealth()>=hp||feedback.size()!=1||feedback.get(0).cue()==null||!feedback.get(0).cue().event().equals(melee.audio().hit().event())) throw new GameTestAssertException("expected typed hit did not occur");
            if(com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.attack(mob,target,now)||feedback.size()!=1)throw new GameTestAssertException("miss failed to consume cooldown");
        } finally { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener); catalog.restore(); com.juicyslew.moonstation14.ms14.npc.NpcRetaliationSystem.forget(mob); mob.discard();target.discard(); }
        h.succeed();
    }

    private static float typed(net.minecraft.world.entity.LivingEntity entity,String type) {
        var damage=entity.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get());
        return damage==null?0f:damage.getMap().getOrDefault(type,0f);
    }
}
