package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.gametest.support.RegisteredServerPlayerFixture;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises actual hand-owned pour requests into loose native darts. */
@PrefixGameTestTemplate(false)
@GameTestTags({"thrown-combat", "thrown-injection", "liquid-transfer"})
public final class NativeDartRefillGameTests {
    private NativeDartRefillGameTests() { }

    @GameTest(template="empty", templateNamespace="moonstation14", timeoutTicks=180)
    public static void dartReceivesTwoUnitsThroughNormalOwnedPour(GameTestHelper h) {
        exercise(h, com.juicyslew.moonstation14.item.ModItems.DART.get(), 200);
    }

    @GameTest(template="empty", templateNamespace="moonstation14", timeoutTicks=180)
    public static void hypodartReceivesTenUnitsThroughNormalOwnedPour(GameTestHelper h) {
        exercise(h, com.juicyslew.moonstation14.item.ModItems.HYPO_DART.get(), 1000);
    }

    private static void exercise(GameTestHelper h, net.minecraft.world.item.Item targetItem, long capacity) {
        var level=(net.minecraft.server.level.ServerLevel)h.getLevel();
        var fixture=RegisteredServerPlayerFixture.register(level,"NativeDartRefill");
        var refs=new com.juicyslew.moonstation14.entities.LooseItemEntity[2];
        var bodyRef=new net.minecraft.world.entity.LivingEntity[1];
        h.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void clean(net.minecraft.gametest.framework.GameTestInfo i) {
                Throwable failure=null;
                for(var e:refs) try { if(e!=null&&!e.isRemoved())e.discard(); } catch(Throwable t){if(failure==null)failure=t;else failure.addSuppressed(t);}
                try { if(bodyRef[0]!=null&&!bodyRef[0].isRemoved())bodyRef[0].discard(); } catch(Throwable t){if(failure==null)failure=t;else failure.addSuppressed(t);}
                try { fixture.close(); } catch(Throwable t){if(failure==null)failure=t;else failure.addSuppressed(t);}
                if(failure!=null){if(i.getError()!=null)i.getError().addSuppressed(failure);else i.fail(failure);}
            }
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo i){} public void testPassed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean(i);} public void testFailed(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestRunner r){clean(i);} public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo i,net.minecraft.gametest.framework.GameTestInfo n,net.minecraft.gametest.framework.GameTestRunner r){clean(i);}
        });
        com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.RegisteredPlayerReadyDriver.awaitCharacterReady(h, fixture, 40, human->{
            try {
                net.minecraft.world.entity.LivingEntity body=human.body(); bodyRef[0]=body; setNoAi(body); body.setDeltaMovement(Vec3.ZERO);
                var origin=h.absolutePos(new net.minecraft.core.BlockPos(5,1,5));
                 for(int x=0;x<=12;x++)for(int z=0;z<=10;z++)level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
                 body.setPos(origin.getX()+.5,origin.getY(),origin.getZ()+.5); body.setDeltaMovement(Vec3.ZERO);
                 body.setYRot(0); body.setYHeadRot(0); body.setXRot(0);
                var water=net.minecraft.resources.ResourceKey.create(com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_REGISTRY_KEY,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14","water"));
                var sourceStack=new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.BOTTLE.get());
                sourceStack.set(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get(),ReagentComponent.fromCents(java.util.Map.of(water,2000L)));
                var targetStack=new net.minecraft.world.item.ItemStack(targetItem);
                targetStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("target-"+java.util.UUID.randomUUID()));
                refs[0]=com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,body.position().add(0,.3,1),sourceStack); refs[0].setNoGravity(true);
                refs[1]=com.juicyslew.moonstation14.entities.LooseItemEntity.create(level,body.position().add(0,.3,1.4),targetStack); refs[1].setNoGravity(true);
                if(!level.addFreshEntity(refs[0])||!level.addFreshEntity(refs[1]))throw new GameTestAssertException("loose container spawn failed");
                var active=com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl.activeCharacterBody(fixture.player()).orElseThrow();
                var initial=fixture.queryHands(active.epoch()); String hand=initial.activeHand();
                var pickup=com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(fixture.player(),new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.PICKUP_SELECTED,hand,active.epoch(),1,initial.revision(),refs[0].getUUID(),null));
                if(pickup.reason()!=com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK)throw new GameTestAssertException("bottle pickup denied: "+pickup.reason());
                 // Action sequences share one replay clock: pickup consumed 1, so the
                 // first normal USE_TARGET must be the next action sequence.
                 pour(h,fixture,active,refs,water,capacity,hand,2,0,sourceStack.getCount());
            } catch(Throwable t){throw t instanceof RuntimeException r?r:new GameTestAssertException("fixture failed: "+t);}
        });
    }

    private static long cents(net.minecraft.world.item.ItemStack stack) {
        var component=stack.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get());
        return component==null?0:new ReagentAttachment(component).totalUnits();
    }

    private static void pour(GameTestHelper h,RegisteredServerPlayerFixture f,Object activeObj,com.juicyslew.moonstation14.entities.LooseItemEntity[] refs,net.minecraft.resources.ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> water,long capacity,String hand,long seq,int delivered,int sourceCount){
        var active=(com.juicyslew.moonstation14.ms14.player_body_control.lifecycle.server.LifecycleCharacterSessionControl.ActiveCharacterBody)activeObj;
        h.runAfterDelay(5,()->{
            var q=f.queryHands(active.epoch()); var held=q.hands().stream().filter(x->x.id().equals(hand)).findFirst().orElseThrow();
            var r=com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(f.player(),new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.USE_TARGET,hand,active.epoch(),seq,q.revision(),refs[1].getUUID(),held.token()));
            if(r.reason()!=com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK)throw new GameTestAssertException("normal pour denied: "+r.reason());
            var target=refs[1].getItem(); long now=cents(target);
            if(now<=delivered)throw new GameTestAssertException("normal pour did not fill dart");
            if(now>capacity)throw new GameTestAssertException("dart exceeded expected capacity: "+now+"/"+capacity);
            if(now==capacity){
                if(refs[1].getUUID()==null||refs[1].getItem().getCount()!=1||target.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME)==null)throw new GameTestAssertException("target identity/count/marker changed");
                var live=active.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
                net.minecraft.world.item.ItemStack heldStack=live==null?null:live.stackCopy(hand).orElse(null);
                if(heldStack==null||cents(heldStack)!=2000-capacity||cents(target)!=capacity||cents(heldStack)+cents(target)!=2000)throw new GameTestAssertException("refill conservation failed: source="+(heldStack==null?-1:cents(heldStack))+" target="+cents(target));
                var fullBefore=target.copy(); var deniedQuery=f.queryHands(active.epoch()); var current=deniedQuery.hands().stream().filter(x->x.id().equals(hand)).findFirst().orElseThrow();
                var denied=com.juicyslew.moonstation14.ms14.hands.network.BodyHandRequestService.apply(f.player(),new com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.USE_TARGET,hand,active.epoch(),seq+1,deniedQuery.revision(),refs[1].getUUID(),current.token()));
                if(denied.reason()==com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult.Reason.OK||cents(refs[1].getItem())!=capacity||cents(active.body().getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get()).stackCopy(hand).orElseThrow())!=2000-capacity)throw new GameTestAssertException("full dart accepted extra pour or changed contents");
                var afterInit=refs[1].getItem().copy(); ItemFoodSolutions.initializeResolved(afterInit, (net.minecraft.server.level.ServerLevel)h.getLevel());
                if(cents(afterInit)!=capacity||!afterInit.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get()).equals(fullBefore.get(com.juicyslew.moonstation14.component.ModDataComponents.REAGENT.get())))throw new GameTestAssertException("resolved initialization erased or changed full dart");
                h.succeed(); return;
            }
            if(seq>=20)throw new GameTestAssertException("dart failed to reach resolved capacity: "+now+"/"+capacity);
            pour(h,f,active,refs,water,capacity,hand,seq+1,(int)now,sourceCount);
        });
    }
    private static void setNoAi(net.minecraft.world.entity.LivingEntity body) {
        if (body instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
        else throw new GameTestAssertException("fixture character is not a Mob");
    }
}
