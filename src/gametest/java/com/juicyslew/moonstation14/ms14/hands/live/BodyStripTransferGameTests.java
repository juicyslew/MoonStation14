package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.item.ModEquipmentItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.stripping.StripSlotRef;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"stripping", "equipment", "storage"})
@PrefixGameTestTemplate(false)
public final class BodyStripTransferGameTests {
    private BodyStripTransferGameTests() { }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=50)
    public static void bodyHandsAndEquipmentRoundTrip(GameTestHelper h) {
        Villager actor=body(h,new BlockPos(1,1,1)); Villager target=body(h,new BlockPos(2,1,1));
        h.runAfterDelay(1,()->{
            LiveHands.initialize(actor); LiveHands.initialize(target);
            String active=actorHands(actor).activeHand(); put(actor,active,"exact",new ItemStack(Items.APPLE,3));
            require(BodyStripTransfer.canTransferOwned(actor,target,new StripSlotRef(StripSlotRef.Kind.HAND,"left"),true,"exact",()->true),"read-only timed-action preflight accepts source");
            require(transfer(actor,target,new StripSlotRef(StripSlotRef.Kind.HAND,"left"),true,"exact")==BodyStripTransfer.Result.SUCCESS,"hand insertion");
            require(targetHands(target).stackCopy("left").orElseThrow().getCount()==3,"whole stack conserved on target");
            require(transfer(actor,target,new StripSlotRef(StripSlotRef.Kind.HAND,"left"),false,"exact")==BodyStripTransfer.Result.SUCCESS,"hand removal");
            require(hasToken(actorHands(actor),"exact"),"hand returns exact identity");
            require(transfer(actor,target,new StripSlotRef(StripSlotRef.Kind.HAND,"left"),true,"exact")==BodyStripTransfer.Result.SUCCESS,
                    "return round-trip stack to target so active actor hand is free");
            active=actorHands(actor).activeHand(); put(actor,active,"cap",new ItemStack(ModEquipmentItems.GREY_CAP.get()));
            LiveHands actorBeforeCap=actorHands(actor), targetBeforeCap=targetHands(target);
            var capPolicy=com.juicyslew.moonstation14.ms14.stripping.StripCapability.resolve(actor,target);
            var targetEquipment=LiveHands.equipmentSnapshot(target).orElseThrow();
            var targetTemplate=BodyEquipmentTransfer.templateForTransfer(target);
            var targetHead=targetEquipment.slots().stream().filter(s->s.id().equals("head")).findFirst().orElseThrow();
            var cap=actorHands(actor).stackCopy(active).orElseThrow();
            var occupied=targetEquipment.slots().stream().filter(s->s.stack().isPresent()).map(LiveHands.EquipmentSlot::id).collect(java.util.stream.Collectors.toSet());
            boolean capFits=BodyEquipmentTransfer.fitsForTransfer((net.minecraft.server.level.ServerLevel) actor.level(),targetTemplate,targetEquipment,"head",cap,occupied);
            boolean candidateFits=targetBeforeCap.insertEquipment(targetBeforeCap.revision(),targetEquipment.slots().stream().map(LiveHands.EquipmentSlot::id).toList(),"head","cap",cap).isPresent();
            boolean preflight=BodyStripTransfer.canTransferOwned(actor,target,new StripSlotRef(StripSlotRef.Kind.EQUIPMENT,"head"),true,"cap",()->true);
            require(preflight,"equipment preflight diagnostics: actorCanOpen="+capPolicy.actorCanOpen()+" inventory="+capPolicy.targetInventoryCanBeStripped()+" active="+actorBeforeCap.activeHand()+" source="+actorBeforeCap.token(active)+" targetHeadOccupied="+targetHead.token().isPresent()+" fits="+capFits+" candidate="+candidateFits+" template="+(targetTemplate!=null));
            BodyStripTransfer.Result capResult=transfer(actor,target,new StripSlotRef(StripSlotRef.Kind.EQUIPMENT,"head"),true,"cap");
            require(capResult==BodyStripTransfer.Result.SUCCESS,"fitting equipment insert result="+capResult+" active="+actorHands(actor).activeHand()+" source="+actorHands(actor).token(active)+" targetHead="+LiveHands.equipmentSnapshot(target).orElseThrow().slots().stream().filter(s->s.id().equals("head")).findFirst().orElseThrow().token());
            put(actor,active,"cap2",new ItemStack(ModEquipmentItems.GREY_CAP.get()));
            LiveHands occupiedSource=actorHands(actor);
            require(transfer(actor,target,new StripSlotRef(StripSlotRef.Kind.EQUIPMENT,"head"),true,"cap2")==BodyStripTransfer.Result.DENIED
                    && actorHands(actor)==occupiedSource,"occupied equipment slot denies without taking source");
            require(transfer(actor,target,new StripSlotRef(StripSlotRef.Kind.EQUIPMENT,"head"),false,"cap")==BodyStripTransfer.Result.SUCCESS,"equipment remove");
            require(hasToken(actorHands(actor),"cap"),"equipment whole item returned");
            h.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=50)
    public static void denyStaleOrOccupiedAndPreserveDependentDropAndBagContents(GameTestHelper h) {
        Villager actor=body(h,new BlockPos(1,1,1)); Villager target=body(h,new BlockPos(2,1,1));
        h.runAfterDelay(1,()->{
            LiveHands.initialize(actor); LiveHands.initialize(target);
            put(actor,"left","wrong",new ItemStack(Items.APPLE));
            LiveHands before=actorHands(actor);
            require(transfer(actor,target,new StripSlotRef(StripSlotRef.Kind.HAND,"left"),true,"other")==BodyStripTransfer.Result.DENIED
                    && actorHands(actor)==before,"exact actor source token required");
            put(target,"left","occupant",new ItemStack(Items.BREAD));
            before=actorHands(actor);
            require(transfer(actor,target,new StripSlotRef(StripSlotRef.Kind.HAND,"left"),true,"wrong")==BodyStripTransfer.Result.DENIED
                    && actorHands(actor)==before,"occupied destination denied");
            equipment(target,"uniform",new ItemStack(ModEquipmentItems.GREY_JUMPSUIT.get()),"jumpsuit");
            equipment(target,"med",new ItemStack(ModItems.BRUTEPACK.get(),10),"pocket1");
            equipment(target,"id",new ItemStack(ModEquipmentItems.BLANK_ID_CARD.get()),"id");
            ItemStack bag=new ItemStack(ModItems.BAG.get()); bag.set(com.juicyslew.moonstation14.component.ModDataComponents.GRID_CONTENTS.get(),GridContents.of(java.util.List.of(new GridContents.Entry("child",new ItemStack(Items.DIAMOND,4),0,0,GridGeometry.Rotation.DEG_0))));
            equipment(target,"bag",bag,"back");
            require(transfer(actor,target,new StripSlotRef(StripSlotRef.Kind.EQUIPMENT,"jumpsuit"),false,"uniform")==BodyStripTransfer.Result.SUCCESS,"uniform removal");
            var drops=h.getLevel().getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,target.getBoundingBox().inflate(8));
            require(drops.stream().anyMatch(e->e.getItem().is(ModItems.BRUTEPACK.get())&&e.getItem().getCount()==10),"medical stack dropped intact");
            require(drops.stream().anyMatch(e->e.getItem().is(ModEquipmentItems.BLANK_ID_CARD.get())),"dependent ID dropped");
            var back=LiveHands.equipmentSnapshot(target).orElseThrow().slots().stream().filter(s->s.id().equals("back")).findFirst().orElseThrow().stack().orElseThrow();
            require(GridContents.read(back).orElseThrow().entries().getFirst().stack().getCount()==4,"bag child unchanged");
            h.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=50)
    public static void fullActorHandsDropsAndWrongFitDenied(GameTestHelper h) {
        Villager actor=body(h,new BlockPos(1,1,1)); Villager target=body(h,new BlockPos(2,1,1));
        h.runAfterDelay(1,()->{
            LiveHands.initialize(actor); LiveHands.initialize(target);
            put(actor,"left","a",new ItemStack(Items.APPLE)); put(actor,"right","b",new ItemStack(Items.BREAD));
            put(target,"left","pull",new ItemStack(Items.DIAMOND));
            require(transfer(actor,target,new StripSlotRef(StripSlotRef.Kind.HAND,"left"),false,"pull")==BodyStripTransfer.Result.SUCCESS,"full hands permits pull by dropping");
            require(h.getLevel().getEntitiesOfClass(com.juicyslew.moonstation14.entities.LooseItemEntity.class,actor.getBoundingBox().inflate(8)).stream().anyMatch(e->e.getItem().is(Items.DIAMOND)),"full-hand main item world drop");
            put(actor,"left","bad",new ItemStack(Items.DIAMOND));
            LiveHands before=actorHands(actor);
            require(transfer(actor,target,new StripSlotRef(StripSlotRef.Kind.EQUIPMENT,"head"),true,"bad")==BodyStripTransfer.Result.DENIED
                    && actorHands(actor)==before,"wrong-fit item denied with source conserved");
            h.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=40)
    public static void failedSecondWriteConditionallyRestoresFirstOwner(GameTestHelper h) {
        Villager actor=body(h,new BlockPos(1,1,1)); Villager target=body(h,new BlockPos(2,1,1));
        h.runAfterDelay(1,()->{
            LiveHands.initialize(actor); LiveHands.initialize(target); put(actor,"left","atomic",new ItemStack(Items.APPLE,2));
            LiveHands original=actorHands(actor), targetBefore=actorHands(target);
            BodyStripTransfer.Result result=BodyStripTransfer.transferOwned(actor,target,
                    new StripSlotRef(StripSlotRef.Kind.HAND,"left"),true,"atomic",()->true,
                    (body,expected,candidate)->{
                        if(body==target) return false;
                        return LiveHandsCommitGateway.commitTrusted(body,expected,candidate);
                    });
            LiveHands after=actorHands(actor);
            require(result==BodyStripTransfer.Result.DENIED,"failed second write compensated as denied");
            require(after.stackCopy("left").filter(s->s.getCount()==2).isPresent()&&after.revision()>original.revision(),"first owner restored without revision rewind");
            require(actorHands(target)==targetBefore&&targetBefore.stackCopy("left").isEmpty(),"target retains no duplicate or lost item");
            h.succeed();
        });
    }

    @GameTest(templateNamespace="moonstation14", template="empty", timeoutTicks=50)
    @GameTestTags({"stripping", "equipment"})
    public static void falseAfterWriteAndThrowPublicationOutcomesAreProven(GameTestHelper h) {
        Villager a=h.spawn(EntityType.VILLAGER,new BlockPos(1,1,1)); Villager t=h.spawn(EntityType.VILLAGER,new BlockPos(2,1,1));
        h.runAfterDelay(1,()->{
            LiveHands.initialize(a); LiveHands.initialize(t); String hand=actorHands(a).activeHand();
            put(a,hand,"write-through",new ItemStack(Items.APPLE,2));
            int[] calls={0};
            BodyStripTransfer.Result committed=BodyStripTransfer.transferOwned(a,t,
                    new StripSlotRef(StripSlotRef.Kind.HAND,"left"),true,"write-through",()->true,
                    (body,expected,candidate)->{ calls[0]++; boolean wrote=LiveHandsCommitGateway.commitTrusted(body,expected,candidate); return false; });
            require(committed==BodyStripTransfer.Result.SUCCESS,"false returned after both writes is recognized as committed");
            require(actorHands(a).stackCopy(hand).isEmpty()&&actorHands(t).token("left").filter("write-through"::equals).isPresent(),"both owners reflect one transfer");
            Villager a2=body(h,new BlockPos(3,1,1)); Villager t2=body(h,new BlockPos(4,1,1));
            LiveHands.initialize(a2); LiveHands.initialize(t2); String h2=actorHands(a2).activeHand(); put(a2,h2,"throw-before",new ItemStack(Items.BREAD));
            LiveHands original=actorHands(a2);
            BodyStripTransfer.Result before=BodyStripTransfer.transferOwned(a2,t2,
                    new StripSlotRef(StripSlotRef.Kind.HAND,"left"),true,"throw-before",()->true,
                    (body,expected,candidate)->{ if(body==a2) throw new IllegalStateException("before write"); return LiveHandsCommitGateway.commitTrusted(body,expected,candidate); });
            require(before==BodyStripTransfer.Result.DENIED&&actorHands(a2)==original&&actorHands(t2).stackCopy("left").isEmpty(),"throw before first write proves no mutation");
            Villager a3=body(h,new BlockPos(5,1,1)); Villager t3=body(h,new BlockPos(6,1,1));
            LiveHands.initialize(a3); LiveHands.initialize(t3); String h3=actorHands(a3).activeHand(); put(a3,h3,"throw-after",new ItemStack(Items.CARROT));
            BodyStripTransfer.Result after=BodyStripTransfer.transferOwned(a3,t3,
                    new StripSlotRef(StripSlotRef.Kind.HAND,"left"),true,"throw-after",()->true,
                    (body,expected,candidate)->{ boolean wrote=LiveHandsCommitGateway.commitTrusted(body,expected,candidate); if(body==t3) throw new IllegalStateException("after target write"); return wrote; });
            require(after==BodyStripTransfer.Result.SUCCESS&&actorHands(a3).stackCopy(h3).isEmpty()
                    &&actorHands(t3).token("left").filter("throw-after"::equals).isPresent(),"throw after both authoritative writes is success");
            h.succeed();
        });
    }

    private static BodyStripTransfer.Result transfer(Villager a,Villager t,StripSlotRef slot,boolean insert,String token) { return BodyStripTransfer.transferOwned(a,t,slot,insert,token,()->true); }
    private static Villager body(GameTestHelper h,BlockPos pos) {
        Villager body=h.spawn(EntityType.VILLAGER,pos);
        require(CharacterIdentitySystem.enroll(body,h.getLevel(),ModCharacters.HUMAN_ID),"resolved stripping-capable character enrolled");
        require(BodySystem.reconcile(body),"resolved body composition initialized");
        body.setNoAi(true);body.setNoGravity(true);
        return body;
    }
    private static LiveHands actorHands(Villager e) { return e.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()); }
    private static LiveHands targetHands(Villager e) { return actorHands(e); }
    private static boolean hasToken(LiveHands state,String token) { return state.handIds().stream().anyMatch(id->state.token(id).filter(token::equals).isPresent()); }
    private static void put(Villager body,String hand,String token,ItemStack stack) { LiveHands b=actorHands(body); LiveHands n=b.putWhole(b.revision(),hand,new ItemToken(token),stack).state(); require(LiveHandsCommitGateway.commitTrusted(body,b,n),"seed item"); }
    private static void equipment(Villager b,String token,ItemStack stack,String slot) { put(b,"right",token,stack); LiveHands s=actorHands(b); require(BodyEquipmentTransfer.transferOwned(b,"right",slot,token,s.revision(),true,()->true)==BodyEquipmentTransfer.Result.SUCCESS,"seed equipment"); }
    private static void require(boolean ok,String msg) { if(!ok) throw new GameTestAssertException(msg); }
}
