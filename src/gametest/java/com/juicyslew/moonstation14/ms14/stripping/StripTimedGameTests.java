package com.juicyslew.moonstation14.ms14.stripping;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.hands.live.BodyGiveService;
import com.juicyslew.moonstation14.ms14.hands.live.BodyStripTransfer;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems;
import com.juicyslew.moonstation14.ms14.mobs.MobStateAttachment;
import com.juicyslew.moonstation14.ms14.mobs.MobStateData;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.Map;
import java.util.UUID;
import com.mojang.authlib.GameProfile;

@GameTestTags({"stripping","equipment","timed-interaction"})
@PrefixGameTestTemplate(false)
public final class StripTimedGameTests {
    private StripTimedGameTests() { }

    @GameTest(template="empty",templateNamespace="moonstation14",timeoutTicks=95)
    public static void productionStripAdmitsTwoPointFiveRootsAndCompletesButRejectsBeyondThree(GameTestHelper h) {
        Villager actor=body(h,new BlockPos(2,1,2)),target=body(h,new BlockPos(3,1,2));
        give(actor,Items.STICK);
        LiveHands source=hands(actor); String token=source.token(source.activeHand()).orElseThrow();
        String slot=hands(target).handIds().stream().filter(id->hands(target).token(id).isEmpty()).findFirst().orElseThrow();
        var origin=actor.position();
        target.setPos(origin.x+3.05,origin.y,origin.z);
        require(!start(actor,null,target,slot,true,1)&&hands(actor).token(source.activeHand()).filter(token::equals).isPresent(),
                "production strip start rejects beyond three blocks without source mutation");
        target.setPos(origin.x+2.5,origin.y,origin.z);
        require(start(actor,null,target,slot,true,2),"production strip start admits a 2.5-block root distance");
        h.runAfterDelay(82,()->{
            require(hands(target).token(slot).isPresent()&&hands(actor).token(source.activeHand()).isEmpty(),
                    "admitted strip reaches authoritative completion and conserves source");
            h.succeed();
        });
    }

    @GameTest(template="empty",templateNamespace="moonstation14",timeoutTicks=105)
    public static void insertionWaitsFullResolvedHandDelayThenCommits(GameTestHelper h) {
        Villager actor=body(h,new BlockPos(2,1,2)),target=body(h,new BlockPos(3,1,2));
        give(actor,Items.STICK); LiveHands a=hands(actor),t=hands(target); String slot=t.handIds().stream().filter(id->t.token(id).isEmpty()).findFirst().orElseThrow();
        long start=h.getLevel().getGameTime();
        require(start(actor,null,target,slot,true,1),"production shared strip consumer starts insertion");
        require(t.token(slot).isEmpty(),"start has no predicted ownership change");
        h.runAfterDelay(79,()->require(hands(target).token(slot).isEmpty(),"four-second hand delay has not completed early"));
        h.runAfterDelay(82,()->{
            require(h.getLevel().getGameTime()-start>=80,"waited 80 ticks");
            require(hands(target).token(slot).isPresent()&&hands(actor).token(a.activeHand()).isEmpty(),"whole stack transfers at deadline");h.succeed();
        });
    }

    @GameTest(template="empty",templateNamespace="moonstation14",timeoutTicks=130)
    public static void resolvedInventorySlotInsertionAndRemovalWaitThreeSeconds(GameTestHelper h) {
        Villager actor=body(h,new BlockPos(2,1,2)),target=body(h,new BlockPos(3,1,2));
        give(actor,com.juicyslew.moonstation14.item.ModEquipmentItems.BLACK_SHOES.get());
        require(start(actor,null,target,"shoes",true,1,StripSlotRef.Kind.EQUIPMENT),"resolved shoes slot accepts authored shoes");
        h.runAfterDelay(59,()->require(hands(target).equipmentStack("shoes").isEmpty(),"three-second slot does not complete early"));
        h.runAfterDelay(62,()->{
            require(hands(target).equipmentStack("shoes").isPresent(),"shoes insertion commits after resolved three-second deadline");
            require(start(actor,null,target,"shoes",false,2,StripSlotRef.Kind.EQUIPMENT),"occupied equipment slot removal starts");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(h,62,()->{
                require(hands(target).equipmentStack("shoes").isEmpty(),"equipment removal commits whole item");
                require(hands(actor).handIds().stream().anyMatch(id->hands(actor).token(id).isPresent()),"removed equipment reaches actor hand");h.succeed();
            });
        });
    }

    @GameTest(template="empty",templateNamespace="moonstation14",timeoutTicks=105)
    public static void removalAllowsActiveHandChangeAndConcurrentDistinctSources(GameTestHelper h) {
        Villager actor=body(h,new BlockPos(2,1,2)),target=body(h,new BlockPos(3,1,2));
        give(target,Items.STICK); give(target,Items.APPLE);
        LiveHands before=hands(target); var occupied=before.handIds().stream().filter(id->before.token(id).isPresent()).toList();
        String left=occupied.get(0),right=occupied.get(1);
        require(before.token(left).isPresent()&&before.token(right).isPresent(),"target holds two independent sources");
        require(start(actor,null,target,left,false,1),"first removal starts");
        require(start(actor,null,target,right,false,2),"distinct source may run concurrently");
        String leftToken=before.token(left).orElseThrow(),rightToken=before.token(right).orElseThrow();
        LiveHands own=hands(actor); String other=own.handIds().stream().filter(id->!id.equals(own.activeHand())).findFirst().orElseThrow();
        require(LiveHandsCommitGateway.commitTrusted(actor,own,own.selectActive(own.revision(),other).orElseThrow()),"active hand switched while removal proceeds");
        require(start(actor,null,target,left,false,3),"same-tool gesture cancels duplicate");
        require(activeCount(actor)==1,"duplicate cancellation does not replace the other source operation");
        h.runAfterDelay(82,()->{
            require(hands(actor).token(right).filter(rightToken::equals).isPresent()
                            &&hands(target).token(left).filter(leftToken::equals).isPresent(),
                    "distinct source completes while same-tool duplicate leaves its source untouched");
            h.succeed();
        });
    }

    @GameTest(template="empty",templateNamespace="moonstation14",timeoutTicks=12)
    public static void insertionCancelsOnActiveSourceChangeAndMovementOrTargetDrift(GameTestHelper h) {
        Villager actor=body(h,new BlockPos(2,1,2)),target=body(h,new BlockPos(3,1,2)); give(actor,Items.STICK);
        String slot=hands(target).handIds().stream().filter(id->hands(target).token(id).isEmpty()).findFirst().orElseThrow();
        require(start(actor,null,target,slot,true,1),"insertion started");
        LiveHands own=hands(actor); String other=own.handIds().stream().filter(id->!id.equals(own.activeHand())).findFirst().orElseThrow();
        require(LiveHandsCommitGateway.commitTrusted(actor,own,own.selectActive(own.revision(),other).orElseThrow()),"switch source active hand");
        h.runAfterDelay(1,()->{
            require(cancelled(actor),"active source change cancels insertion");
            // Fresh removal then radial target drift while both bodies remain within reach.
            give(target,Items.APPLE); String targetSlot=hands(target).handIds().stream().filter(id->hands(target).token(id).isPresent()).findFirst().orElseThrow();
            require(start(actor,null,target,targetSlot,false,2),"removal starts for target drift check");
            target.setPos(target.getX()+.31,target.getY(),target.getZ());
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(h,1,()->{require(cancelled(actor),"target distance change greater than .3 cancels");h.succeed();});
        });
    }

    @GameTest(template="empty",templateNamespace="moonstation14",timeoutTicks=12)
    public static void actorDisplacementAndActualDamageCancelOperation(GameTestHelper h) {
        Villager actor=body(h,new BlockPos(2,1,2)),target=body(h,new BlockPos(3,1,2)); give(target,Items.STICK);
        String slot=hands(target).handIds().stream().filter(id->hands(target).token(id).isPresent()).findFirst().orElseThrow();require(start(actor,null,target,slot,false,1),"removal started");
        actor.setPos(actor.getX()+.31,actor.getY(),actor.getZ());
        h.runAfterDelay(1,()->{
            require(cancelled(actor),"actor displacement cancels timed operation");
            actor.setPos(h.absolutePos(new BlockPos(2,1,2)).getX()+.5,actor.getY(),actor.getZ());
            require(start(actor,null,target,slot,false,2),"second removal starts");
            require(DamageSystem.applyHealthChange(actor,Map.of(DamageKeys.BLUNT,1f),1f,true,DamageSystem.Cause.COMBAT)==DamageSystem.Result.APPLIED,"one typed damage applied");
            require(cancelled(actor),"actual damage event cancels before completion");h.succeed();
        });
    }

    @GameTest(template="empty",templateNamespace="moonstation14",timeoutTicks=10)
    public static void replacingSourceTokenAfterStartCancelsWithoutConsumingReplacement(GameTestHelper h) {
        Villager actor=body(h,new BlockPos(2,1,2)),target=body(h,new BlockPos(3,1,2)),recipient=body(h,new BlockPos(4,1,2));
        give(actor,Items.STICK);LiveHands initial=hands(actor);String sourceHand=initial.activeHand();String sourceToken=initial.token(sourceHand).orElseThrow();
        String destination=hands(target).handIds().stream().filter(id->hands(target).token(id).isEmpty()).findFirst().orElseThrow();
        require(start(actor,null,target,destination,true,1),"timed insertion started on exact source token");
        String receiver=hands(recipient).handIds().stream().filter(id->hands(recipient).token(id).isEmpty()).findFirst().orElseThrow();
        require(BodyStripTransfer.transferOwned(actor,recipient,new StripSlotRef(StripSlotRef.Kind.HAND,receiver),true,sourceToken,()->true)==BodyStripTransfer.Result.SUCCESS,
                "independent authorized transfer removes source from actor");
        give(actor,Items.APPLE);
        h.runAfterDelay(1,()->{
            require(cancelled(actor),"replacement token does not satisfy captured source proof");
            require(hands(actor).token(sourceHand).isPresent()&&!hands(actor).token(sourceHand).orElseThrow().equals(sourceToken),
                    "replacement remains untouched after cancellation");h.succeed();
        });
    }

    @GameTest(template="empty",templateNamespace="moonstation14",timeoutTicks=8)
    public static void criticalAndDeadTargetsUseResolvedModifiersWithoutTargetCanActGate(GameTestHelper h) {
        Villager actor=body(h,new BlockPos(2,1,2)),target=body(h,new BlockPos(3,1,2));give(target,Items.STICK);
        String slot=hands(target).handIds().stream().filter(id->hands(target).token(id).isPresent()).findFirst().orElseThrow();
        target.setData(ModDataAttachments.MOB_STATE.get(),new MobStateAttachment(new MobStateData(MobStateComponent.State.CRITICAL,1)));
        require(start(actor,null,target,slot,false,1),"critical target remains eligible");
        require(duration(actor)==40,"critical state halves resolved four-second hand delay");
        com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem.cancelDuplicate(actor,"body-stripping",hands(target).token(slot).orElseThrow());
        target.setData(ModDataAttachments.MOB_STATE.get(),new MobStateAttachment(new MobStateData(MobStateComponent.State.DEAD,2)));
        require(start(actor,null,target,slot,false,2),"dead target remains eligible");
        require(duration(actor)==27,"dead state applies one-third delay");h.succeed();
    }

    @GameTest(template="empty",templateNamespace="moonstation14",timeoutTicks=10)
    public static void unauthenticatedPublicRequestsDoNotOpenOrMutateStripTargets(GameTestHelper h) {
        Villager untracked=EntityType.VILLAGER.create(h.getLevel());
        require(untracked!=null,"detached untracked fixture created");
        Villager filled=body(h,new BlockPos(3,1,2));give(filled,Items.STICK);
        LiveHands original=hands(filled);String token=original.token(original.handIds().getFirst()).orElseThrow();
        FakePlayer outsider=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"strip-outsider"));
        require(h.getLevel().addFreshEntity(outsider),"unbound FakePlayer fixture added");
        try {
            StripService.request(outsider,new StripViewRequest(StripViewRequest.Operation.OPEN,1,1,untracked.getUUID(),0));
            require(untracked.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())==null,
                    "unauthenticated OPEN does not initialize arbitrary target stock");
            StripService.action(outsider,new StripSlotAction(1,2,0,filled.getUUID(),original.revision(),
                    StripSlotRef.Kind.HAND,original.handIds().getFirst()));
            require(hands(filled)==original&&hands(filled).token(original.handIds().getFirst()).filter(token::equals).isPresent(),
                    "unauthenticated action cannot mutate an occupied target hand");
            h.succeed();
        } finally {
            outsider.discard();
        }
    }

    private static boolean start(Villager actor,net.minecraft.server.level.ServerPlayer owner,Villager target,String slot,boolean insert,long seq) {
        return start(actor,owner,target,slot,insert,seq,StripSlotRef.Kind.HAND);
    }
    private static boolean start(Villager actor,net.minecraft.server.level.ServerPlayer owner,Villager target,String slot,boolean insert,long seq,StripSlotRef.Kind kind) {
        LiveHands a=hands(actor),t=hands(target); return StripService.startOwned(actor,owner,target,
                new StripSlotAction(1,seq,a.revision(),target.getUUID(),t.revision(),kind,slot),()->true);
    }
    private static Villager body(GameTestHelper h,BlockPos relative) {
        BlockPos p=h.absolutePos(relative);var level=h.getLevel();level.setBlock(p.below(),Blocks.STONE.defaultBlockState(),3);
        Villager v=EntityType.VILLAGER.create(level);require(v!=null,"entity created");v.setNoAi(true);v.setNoGravity(true);v.setPos(p.getX()+.5,p.getY(),p.getZ()+.5);
        require(level.addFreshEntity(v),"naturally joined ordinary villager");
        require(CharacterIdentitySystem.resolveForActor(v).isPresent()
                && com.juicyslew.moonstation14.ms14.hands.HandCapability.resolve(v).isPresent(),
                "join lifecycle enrolls resolved human composition");
        require(hands(v)!=null&&hands(v).compatible(v),"join lifecycle bootstraps compatible live hands");
        require(BodySystem.reconcile(v),"resolved body state initialized");return v;
    }
    private static void give(Villager body,net.minecraft.world.item.Item item) { require(BodyGiveService.giveTrustedBody(body,new ItemStack(item),()->true),"fixture item granted to live hands"); }
    private static LiveHands hands(Villager body){return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());}
    private static long duration(Villager body){return body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().filter(o->o.phase()==TimedOperation.Phase.ACTIVE).findFirst().orElseThrow().durationTicks();}
    private static int activeCount(Villager body){return (int)body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().filter(o->o.phase()==TimedOperation.Phase.ACTIVE).count();}
    private static boolean cancelled(Villager body){return body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().anyMatch(o->o.phase()==TimedOperation.Phase.CANCELLED);}
    private static void require(boolean ok,String message){if(!ok)throw new GameTestAssertException(message);}
}
