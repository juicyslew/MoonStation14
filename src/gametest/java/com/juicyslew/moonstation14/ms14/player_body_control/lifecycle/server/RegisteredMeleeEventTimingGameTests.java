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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"shared-melee-runtime-fixture", "melee-event-timing"})
@PrefixGameTestTemplate(false)
public final class RegisteredMeleeEventTimingGameTests {
    private RegisteredMeleeEventTimingGameTests() { }

    @GameTest(template="atmos_large_empty",templateNamespace="moonstation14",batch="shared_melee_event_timing",timeoutTicks=160)
    public static void eventCancellationTracksCooldownBoundary(GameTestHelper helper) {
        ScopedPrototypeCatalogFixture.replace(helper, ModCharacters.CHARACTER_TYPE, catalog -> {
            var id=ModCharacters.HUMAN_ID;
            var json=com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,catalog.get(id)).getOrThrow().getAsJsonObject();
            var components=json.has("components")?json.getAsJsonArray("components"):new com.google.gson.JsonArray(); json.add("components",components);
            com.juicyslew.moonstation14.ms14.character.CharacterPrototypeComponentUpsert.upsert(components,com.google.gson.JsonParser.parseString("{\"type\":\"CombatMode\",\"can_disarm\":true}").getAsJsonObject());
            com.juicyslew.moonstation14.ms14.character.CharacterPrototypeComponentUpsert.upsert(components,com.google.gson.JsonParser.parseString("{\"type\":\"MeleeWeapon\",\"weapon\":{\"damage\":{\"blunt\":5}}}").getAsJsonObject());
            catalog.put(id,com.juicyslew.moonstation14.component.codec.json.CharacterData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow()); return catalog;
        });
        var fixture=RegisteredServerPlayerFixture.register(helper.getLevel(),"melee-timing");
        var bodyRef=new net.minecraft.world.entity.LivingEntity[1]; var targetRef=new net.minecraft.world.entity.LivingEntity[1];
         Object preListener=new Object(){@net.neoforged.bus.api.SubscribeEvent public void onAttempt(MeleeAttackEvents.SharedMeleeAttackAttemptEvent e){if(bodyRef[0]!=null&&e.attacker().getUUID().equals(bodyRef[0].getUUID()))e.setCanceled(true);}};
         Object postListener=new Object(){@net.neoforged.bus.api.SubscribeEvent public void onAttempt(MeleeAttackEvents.AttemptMeleeEvent e){if(bodyRef[0]!=null&&e.attacker().getUUID().equals(bodyRef[0].getUUID()))e.setCanceled(true);}};
        var hitCount=new java.util.concurrent.atomic.AtomicInteger();
         Object hitListener=new Object(){@net.neoforged.bus.api.SubscribeEvent public void onHit(MeleeAttackEvents.MeleeHitEvent e){if(bodyRef[0]!=null&&e.attacker().getUUID().equals(bodyRef[0].getUUID())){if(!e.targets().isEmpty())throw new GameTestAssertException("null nomination unexpectedly resolved target");hitCount.incrementAndGet();}}};
        var preInstalled=new java.util.concurrent.atomic.AtomicBoolean(); var postInstalled=new java.util.concurrent.atomic.AtomicBoolean(); var hitInstalled=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable remove=()->{if(preInstalled.compareAndSet(true,false))NeoForge.EVENT_BUS.unregister(preListener);if(postInstalled.compareAndSet(true,false))NeoForge.EVENT_BUS.unregister(postListener);if(hitInstalled.compareAndSet(true,false))NeoForge.EVENT_BUS.unregister(hitListener);};
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener(){private void clean(net.minecraft.gametest.framework.GameTestInfo i){remove.run();if(targetRef[0]!=null&&!targetRef[0].isRemoved())targetRef[0].discard();if(bodyRef[0]!=null&&!bodyRef[0].isRemoved())bodyRef[0].discard();fixture.close();}
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){} @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean(i);}
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean(i);}@Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo o,net.minecraft.gametest.framework.GameTestInfo n,net.minecraft.gametest.framework.GameTestRunner r){clean(o);}});
        try { helper.runAtTickTime(2,()->{try{
            var active=RegisteredPlayerReadyDriver.readyAndQuery(fixture);var body=active.body();bodyRef[0]=body;BlockPos p=helper.absolutePos(new BlockPos(3,2,3));
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)helper.getLevel().setBlock(p.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
            body.teleportTo(p.getX()+.5,p.getY(),p.getZ()+.5);body.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);body.tick();
            var selection=MeleeWeaponResolver.resolve(body).orElseThrow();if(selection.kind()!=MeleeWeaponResolver.Kind.UNARMED)throw new GameTestAssertException("expected unarmed selection");
            var target=EntityType.VILLAGER.create(helper.getLevel());if(target==null)throw new GameTestAssertException("target creation failed");targetRef[0]=target;target.setNoAi(true);target.setPos(p.getX()+.5,p.getY(),p.getZ()+.9);helper.getLevel().addFreshEntity(target);
            CombatModeRequestService.handle(fixture.player(),new CombatModeRequest(active.epoch(),1,true));
            long initialDeadline=MeleeCooldownSystem.nextAttackTick(body,selection).orElseThrow();
            NeoForge.EVENT_BUS.register(preListener);preInstalled.set(true);
            var pre=MeleeAttackRequestService.handle(fixture.player(),request(active.epoch(),1,selection,target.getUUID()));remove.run();
            float targetBefore=blunt(target);if(pre.status()!=MeleeAttackReply.Status.PRE_ATTEMPT_CANCELLED||pre.cooldownConsumed()||MeleeCooldownSystem.nextAttackTick(body,selection).orElseThrow()!=initialDeadline||blunt(target)!=targetBefore)throw new GameTestAssertException("pre-cooldown cancellation changed attack state");
            NeoForge.EVENT_BUS.register(hitListener);hitInstalled.set(true);
            var miss=MeleeAttackRequestService.handle(fixture.player(),request(active.epoch(),2,selection,null));
            if(miss.status()!=MeleeAttackReply.Status.MISS||!miss.cooldownConsumed()||hitCount.get()!=1||blunt(target)!=targetBefore)throw new GameTestAssertException("valid miss failed: "+miss.status());
            var blocked=MeleeAttackRequestService.handle(fixture.player(),request(active.epoch(),3,selection,target.getUUID()));if(blocked.status()!=MeleeAttackReply.Status.REJECTED)throw new GameTestAssertException("cooldown did not block immediate attack");
            helper.runAfterDelay(21,()->{try{remove.run();NeoForge.EVENT_BUS.register(postListener);postInstalled.set(true);var consumed=MeleeAttackRequestService.handle(fixture.player(),request(active.epoch(),4,selection,target.getUUID()));
                if(consumed.status()!=MeleeAttackReply.Status.CONSUMED_CANCELLED||!consumed.cooldownConsumed()||blunt(target)!=targetBefore||hitCount.get()!=1)throw new GameTestAssertException("post-cooldown cancellation failed: "+consumed.status());helper.succeed();
            }catch(RuntimeException|Error e){remove.run();throw e;}});
        }catch(RuntimeException|Error e){remove.run();fixture.close();throw e;}});}catch(RuntimeException|Error e){remove.run();fixture.close();throw e;}
    }
    private static MeleeAttackRequest request(long epoch,long seq,MeleeWeaponResolver.Selection s,java.util.UUID target){return new MeleeAttackRequest(epoch,seq,s.kind(),s.handRevision(),s.activeHand(),s.itemToken().orElse(""),target);}
    private static float blunt(net.minecraft.world.entity.LivingEntity e){var d=e.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.DAMAGE.get());return d==null?0f:d.getMap().getOrDefault("blunt",0f);}
}
