package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.ingestion.BodyTimedIngestionService;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionResult;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperationState;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.loose_objects.interaction.LiquidInteractionCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Map;

@GameTestTags({"timed-interaction", "ingestion", "hands", "chemistry"})
@PrefixGameTestTemplate(false)
public final class BodyTimedIngestionGameTests {
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> WATER =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));

    private BodyTimedIngestionGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 160)
    public static void puddleConsumerWaitsForResolvedProfileDeadlineAndRepeats(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos bodyPos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(bodyPos.below(), Blocks.STONE.defaultBlockState(), 3);
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "body created");
        body.setNoAi(true);
        body.setNoGravity(true);
        body.setPos(bodyPos.getX() + .5, bodyPos.getY(), bodyPos.getZ() + .5);
        body.setYRot(-90f);
        body.setXRot(0f);
        require(level.addFreshEntity(body), "body spawned");
        require(CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "body enrolled");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body), "eligible body and hands initialized");
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        BlockPos pos = bodyPos.offset(1, 0, 0);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
        require(level.getBlockEntity(pos) instanceof PuddleBlockEntity, "puddle entity created");
        PuddleBlockEntity puddle = (PuddleBlockEntity) level.getBlockEntity(pos);
        MS14Provider.update(puddle, MS14Bridges.REAGENT, new ReagentAttachment(Map.of(WATER, 10f)));
        aimAtPuddle(body, pos);
        require(PuddleTargetProbe.selected(level, body, pos), probeDiagnostic(level, body, pos));
        long start = level.getGameTime();
        require(BodyTimedIngestionService.startPuddleOwned(body, pos, hands.activeHand(), hands.revision(), 77, () -> true)
                .name().equals("OK"), "production timed ingestion consumer accepts request");
        require(water(puddle) == 1000 && water(body) == 0, "starting operation performs no immediate transfer");
        helper.runAfterDelay(30, () -> {
            require(level.getGameTime() - start >= 30 && water(puddle) == 1000 && water(body) == 0,
                    "puddle profile does not complete before its 60 tick deadline");
        });
        helper.runAfterDelay(63, () -> {
            require(water(puddle) == 900, "first timed 100-cent dose committed");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 60, () -> {
                require(water(puddle) == 800, "repeat waits one full resolved interval and commits once");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void duplicateGestureCancelsAndStaleRevisionCannotCancel(GameTestHelper helper) {
        var level = helper.getLevel();
        Villager body = makeBody(helper, new BlockPos(2, 1, 2));
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        BlockPos pos = puddle(level, helper.absolutePos(new BlockPos(3, 1, 2)), 10f);
        aimAtPuddle(body,pos);
        require(PuddleTargetProbe.selected(level,body,pos),probeDiagnostic(level,body,pos));
        require(BodyTimedIngestionService.startPuddleOwned(body,pos,hands.activeHand(),hands.revision(),1,()->true).name().equals("OK"), "first request starts");
        TimedOperationState state = body.getData(ModDataAttachments.TIMED_OPERATIONS.get());
        long id = state.operations().get(0).id();
        require(BodyTimedIngestionService.startPuddleOwned(body,pos,hands.activeHand(),hands.revision()-1,2,()->true)==BodyHandActionResult.Reason.DENIED,
                "stale hand revision denied");
        require(active(body,id), "stale duplicate leaves original operation active");
        MS14Provider.update(body, MS14Bridges.STOMACH,
                new ReagentAttachment(Map.of(WATER, LiquidInteractionCapability.STOMACH_CAPACITY_CENTS / 100f)));
        require(BodyTimedIngestionService.startPuddleOwned(body,pos,hands.activeHand(),hands.revision(),3,()->true).name().equals("OK"), "authorized duplicate cancels");
        require(water((PuddleBlockEntity)level.getBlockEntity(pos))==1000
                        && water(body)==LiquidInteractionCapability.STOMACH_CAPACITY_CENTS && cancelled(body,id),
                "authorized duplicate cancels despite full stomach without transferring anything");
        MS14Provider.update(body, MS14Bridges.STOMACH, new ReagentAttachment(Map.of()));
        helper.runAfterDelay(1, () -> {
            require(BodyTimedIngestionService.startPuddleOwned(body,pos,hands.activeHand(),hands.revision(),4,()->true).name().equals("OK"), "fresh gesture starts new operation");
            TimedOperation repeat = body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                    .filter(o -> o.phase() == TimedOperation.Phase.ACTIVE).findFirst().orElseThrow();
            require(repeat.id() != id && repeat.startTick() > state.operations().get(0).startTick(), "restart creates a fresh repeat operation");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 30, () -> {
                require(water((PuddleBlockEntity)level.getBlockEntity(pos))==1000 && active(body, repeat.id()), "fresh repeat remains active before its full interval");
                com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(helper, 31, () -> {
                    TimedOperation current = body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream()
                            .filter(o -> o.id() == repeat.id()).findFirst().orElseThrow();
                    require(water((PuddleBlockEntity)level.getBlockEntity(pos))==900 && current.phase() == TimedOperation.Phase.ACTIVE
                                    && current.startTick() > repeat.startTick(), "first repeat dose commits and next interval starts");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 70)
    public static void twoActorsSharePuddleAndSecondCommitsRemainder(GameTestHelper helper) {
        var level=helper.getLevel();
        Villager first=makeBody(helper,new BlockPos(2,1,2));
        Villager second=makeBody(helper,new BlockPos(2,1,4));
        BlockPos pos=puddle(level,helper.absolutePos(new BlockPos(3,1,2)),1.5f);
        aimAtPuddle(first,pos); aimAtPuddle(second,pos);
        require(PuddleTargetProbe.selected(level,first,pos),probeDiagnostic(level,first,pos));
        require(PuddleTargetProbe.selected(level,second,pos),probeDiagnostic(level,second,pos));
        LiveHands h1=first.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()), h2=second.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(BodyTimedIngestionService.startPuddleOwned(first,pos,h1.activeHand(),h1.revision(),11,()->true).name().equals("OK"), "first actor starts");
        require(BodyTimedIngestionService.startPuddleOwned(second,pos,h2.activeHand(),h2.revision(),12,()->true).name().equals("OK"), "second actor starts against same source");
        require(water((PuddleBlockEntity)level.getBlockEntity(pos))==150, "both operations begin before source changes");
        helper.runAfterDelay(62,()->{
            require(level.getBlockState(pos).isAir(), "remaining puddle is removed only after second admission");
            int completed = (int) java.util.stream.Stream.of(first, second).filter(b -> hasTerminal(b, TimedOperation.Phase.COMPLETED)).count();
            int cancelled = (int) java.util.stream.Stream.of(first, second).filter(b -> hasTerminal(b, TimedOperation.Phase.CANCELLED)).count();
            require(completed == 1 && cancelled == 1, "one actor commits the final 50-cent slice; the missing-source repeat cancels");
            require(!hasTerminal(first, TimedOperation.Phase.ACTIVE) && !hasTerminal(second, TimedOperation.Phase.ACTIVE), "neither operation remains active after source depletion");
            require(h1.compatible(first)&&h2.compatible(second), "hands remain valid after commits");
            helper.succeed();
        });
    }

    private static Villager makeBody(GameTestHelper helper, BlockPos pos) {
        pos = helper.absolutePos(pos);
        var level=helper.getLevel(); level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        Villager body=EntityType.VILLAGER.create(level); require(body!=null,"body created"); body.setNoAi(true); body.setNoGravity(true);
        body.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5); body.setYRot(-90f); body.setXRot(0f);
        require(level.addFreshEntity(body)&&CharacterIdentitySystem.enroll(body,level,ModCharacters.HUMAN_ID),"body enrolled");
        require(LiveHands.initialize(body).isPresent()&&BodySystem.reconcile(body),"body initialized"); return body;
    }

    private static BlockPos puddle(net.minecraft.world.level.Level level,BlockPos pos,float units) {
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3); level.setBlock(pos,ModBlocks.PUDDLE.get().defaultBlockState(),3);
        require(level.getBlockEntity(pos) instanceof PuddleBlockEntity,"puddle created");
        MS14Provider.update(level.getBlockEntity(pos),MS14Bridges.REAGENT,new ReagentAttachment(Map.of(WATER,units)));
        return pos;
    }

    private static void aimAtPuddle(Villager body, BlockPos pos) {
        var level=body.level();
        var bounds=level.getBlockState(pos).getShape(level,pos,CollisionContext.of(body)).bounds();
        Vec3 target=new Vec3(pos.getX()+(bounds.minX+bounds.maxX)/2,
                pos.getY()+(bounds.minY+bounds.maxY)/2,pos.getZ()+(bounds.minZ+bounds.maxZ)/2);
        Vec3 delta=target.subtract(body.getEyePosition());
        body.setYRot((float)(Math.toDegrees(Math.atan2(delta.z,delta.x))-90));
        body.setXRot((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
        body.setYHeadRot(body.getYRot()); body.setYBodyRot(body.getYRot());
    }

    private static String probeDiagnostic(net.minecraft.world.level.Level level,Villager body,BlockPos pos) {
        return "puddle target not selected: eyes="+body.getEyePosition()+" look="+body.getLookAngle()+" pos="+pos;
    }

    private static boolean active(Villager b,long id) { return b.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().anyMatch(o->o.id()==id&&o.phase()==TimedOperation.Phase.ACTIVE); }
    private static boolean cancelled(Villager b,long id) { return b.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().anyMatch(o->o.id()==id&&o.phase()==TimedOperation.Phase.CANCELLED); }
    private static boolean hasTerminal(Villager b,TimedOperation.Phase p) { return b.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().anyMatch(o->o.phase()==p); }
    private static boolean hasPhase(Villager b,long id,TimedOperation.Phase p) { return b.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().anyMatch(o->o.id()==id&&o.phase()==p); }

    private static long water(PuddleBlockEntity entity) {
        return ReagentUnits.fromFloat(MS14Provider.get(entity, MS14Bridges.REAGENT).getMap().getOrDefault(WATER, 0f));
    }

    private static long water(Villager entity) {
        return ReagentUnits.fromFloat(MS14Provider.get(entity, MS14Bridges.STOMACH).getMap().getOrDefault(WATER, 0f));
    }

    private static void require(boolean value, String message) {
        if (!value) throw new GameTestAssertException(message);
    }
}
