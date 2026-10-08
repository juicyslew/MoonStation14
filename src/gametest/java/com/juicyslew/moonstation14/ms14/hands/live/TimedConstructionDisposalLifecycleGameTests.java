package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.construction.domain.ConstructionGraph;
import com.juicyslew.moonstation14.ms14.construction.prototype.ModConstruction;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeWorldData;
import com.juicyslew.moonstation14.ms14.disposal.world.DisposalTubeRecord;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/** Trusted-body LiveHands reservation, timer, and world-install integration only; not authenticated WorldService admission or its reservation settlement. */
@GameTestTags({"construction", "shared-construction", "timed-interaction", "disposals"})
@PrefixGameTestTemplate(false)
public final class TimedConstructionDisposalLifecycleGameTests {
    private TimedConstructionDisposalLifecycleGameTests() { }

    @GameTest(templateNamespace = "moonstation14", template = "disposal_clear", timeoutTicks = 10)
    public static void fourWorldConstructionJobsArePublishedInRuntimeStateAtStart(GameTestHelper helper) {
        ServerLevel level=helper.getLevel(); Villager body=body(helper); body.setInvulnerable(true);
        long startTick=level.getServer().overworld().getGameTime();
        var targets=java.util.stream.IntStream.range(0,4)
                .mapToObj(i->helper.absolutePos(new BlockPos(5+i*2,2,7))).toList();
        for(int i=0;i<targets.size();i++) {
            BlockPos target=targets.get(i); String key=target.asLong()+":material:"+i;
            require(TimedInteractionSystem.start(body,null,0,"construction_material",key,200,true,false,
                    ()->true,()->TimedInteractionSystem.Completion.STOP,Optional.of(target),()->{})
                    ==com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionEngine.StartResult.STARTED,
                    "each distinct graph/material work target starts through the trusted runtime");
        }
        var state=body.getData(ModDataAttachments.TIMED_OPERATIONS.get());
        var active=state.operations().stream().filter(op->op.phase()==com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE).toList();
        require(active.size()==4,"runtime retains all four active jobs immediately after starts");
        require(active.stream().allMatch(op->op.startTick()==startTick&&op.dueTick()>level.getServer().overworld().getGameTime()),
                "all jobs retain current server start tick and are observed before any deadline");
        require(active.stream().map(op->op.kind()+"/"+op.sourceKey()).distinct().count()==4,
                "all four runtime job identities remain independently active");
        require(level.getServer().overworld().getGameTime()==startTick,"inspection did not advance the server clock");
        helper.succeed();
    }

    @GameTest(templateNamespace = "moonstation14", template = "disposal_clear", timeoutTicks = 55)
    public static void concurrentGraphTimedDisposalInstallsAtRecordedOneSecondDeadline(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager body = body(helper);
        var stage = PrototypeRuntime.serverConstructionCatalog().initialStage(ModConstruction.id("disposal_straight")).orElseThrow();
        require(stage.durationSeconds() == 1.0 && stage.steps().getFirst() instanceof ConstructionGraph.MaterialStep m
                && m.material() == ConstructionGraph.MaterialId.Steel && m.amount() == 2, "resolved stage is one second and two steel");
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        String hand = hands.activeHand();
        var placed = hands.putWhole(hands.revision(), hand, new ItemToken("trusted-steel"), new ItemStack(ModItems.STEEL.get(), 4));
        require(placed.succeeded(), "trusted reservation seam seeds body-owned steel");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), placed.state());
        reserveTrusted(body, stage.steps().getFirst() instanceof ConstructionGraph.MaterialStep cost ? cost.amount() : -1);
        reserveTrusted(body, stage.steps().getFirst() instanceof ConstructionGraph.MaterialStep cost ? cost.amount() : -1);
        BlockPos first = helper.absolutePos(new BlockPos(6, 2, 6));
        BlockPos second = helper.absolutePos(new BlockPos(10, 2, 6));
        level.setBlock(first, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(second, Blocks.AIR.defaultBlockState(), 3);
        AtomicLong firstInstall = new AtomicLong(-1), secondInstall = new AtomicLong(-1);
        long startTick = level.getServer().overworld().getGameTime();
        int ticks = (int) Math.round(stage.durationSeconds() * 20);
        for (var entry : java.util.List.of(new Object[]{first, firstInstall}, new Object[]{second, secondInstall})) {
            BlockPos target = (BlockPos) entry[0]; AtomicLong completionTick = (AtomicLong) entry[1];
            require(TimedInteractionSystem.start(body, null, 0, "construction_foundation", target.asLong() + "", ticks,
                    true, false, () -> true, () -> {
                        DisposalTubeRecord record = new DisposalTubeRecord(UUID.randomUUID(), target,
                                "moonstation14:disposal_pipe", Direction.NORTH, false, "", "", 0);
                        require(DisposalTubeWorldData.forMutation(level).installExposed(level, record), "exposed tube installed");
                        completionTick.set(level.getServer().overworld().getGameTime());
                        return TimedInteractionSystem.Completion.STOP;
                    }, Optional.of(target), () -> { }).name().equals("STARTED"), "parallel world-target job starts");
        }
        helper.runAfterDelay(19, () -> {
            require(level.getBlockState(first).isAir() && level.getBlockState(second).isAir(), "no physical install at tick 19");
        });
        helper.succeedWhen(() -> {
            require(firstInstall.get() >= 0 && secondInstall.get() >= 0, "both concurrent callbacks completed");
            require(firstInstall.get() == startTick + ticks && secondInstall.get() == startTick + ticks,
                    "physical installs occur at exact graph-derived deadline");
            require(DisposalTubeWorldData.forMutation(level).lookup(level, first).kind() == DisposalTubeWorldData.LookupKind.PRESENT
                    && DisposalTubeWorldData.forMutation(level).lookup(level, second).kind() == DisposalTubeWorldData.LookupKind.PRESENT,
                    "both installed world records validate");
            require(totalSteel(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())) == 0,
                    "two trusted reservations consumed exactly the graph-derived four steel");
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "disposal_clear", timeoutTicks = 40)
    public static void timedDeniedOperationRefundsBodyMaterialsOnceWhilePeerInstalls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel(); Villager body = body(helper);
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()); String hand = hands.activeHand();
        var inserted = hands.putWhole(hands.revision(), hand, new ItemToken("refund-steel"), new ItemStack(ModItems.STEEL.get(), 4));
        require(inserted.succeeded(), "seed body-owned reservation material"); body.setData(ModDataAttachments.LIVE_HANDS.get(), inserted.state());
        ItemStack refundStack = reserveTrusted(body, 2);
        reserveTrusted(body, 2);
        AtomicInteger refunds = new AtomicInteger(); BlockPos a = helper.absolutePos(new BlockPos(6, 2, 6));
        BlockPos b = helper.absolutePos(new BlockPos(10, 2, 6)); level.setBlock(a, Blocks.AIR.defaultBlockState(), 3); level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
        require(TimedInteractionSystem.start(body, null, 0, "construction", "denied", 20, true, false, () -> true,
                () -> TimedInteractionSystem.Completion.DENIED, Optional.of(a), () -> {
                    refunds.incrementAndGet();
                    LiveHands current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                    var refund = current.placeCraftOutput(current.revision(), refundStack, "refund-output");
                    require(refund.isPresent(), "reserved stack refunded into body-owned inventory");
                    body.setData(ModDataAttachments.LIVE_HANDS.get(), refund.orElseThrow());
                }).name().equals("STARTED"), "denied timed operation starts");
        require(TimedInteractionSystem.start(body, null, 0, "construction", "peer", 20, true, false, () -> true,
                () -> {
                    var record = new DisposalTubeRecord(UUID.randomUUID(), b, "moonstation14:disposal_pipe", Direction.NORTH, false, "", "", 0);
                    require(DisposalTubeWorldData.forMutation(level).installExposed(level, record), "peer installs tube");
                    return TimedInteractionSystem.Completion.STOP;
                }, Optional.of(b), () -> { }).name().equals("STARTED"), "peer operation starts concurrently");
        helper.succeedWhen(() -> {
            require(refunds.get() == 1, "denied completion refunded exactly once");
            require(totalSteel(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get())) == 2,
                    "refund restored reserved steel to actual body-owned inventory");
            require(DisposalTubeWorldData.forMutation(level).lookup(level, b).kind() == DisposalTubeWorldData.LookupKind.PRESENT,
                    "independent operation completes world installation");
        });
    }

    @GameTest(templateNamespace = "moonstation14", template = "disposal_clear", timeoutTicks = 40)
    public static void sameTargetCompletionInvalidationRefundsSecondReservation(GameTestHelper helper) {
        ServerLevel level=helper.getLevel(); Villager body=body(helper);
        // This fixture proves the same-target completion/refund race, not damage interruption policy.
        // The unfiltered cross-domain run damaged the isolated villager before either deadline.
        body.setInvulnerable(true);
        float initialHealth=body.getHealth();
        LiveHands hands=body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        String hand=hands.activeHand();
        var seeded=hands.putWhole(hands.revision(),hand,new ItemToken("same-cell-steel"),new ItemStack(ModItems.STEEL.get(),4));
        require(seeded.succeeded(),"seed four body-owned steel"); body.setData(ModDataAttachments.LIVE_HANDS.get(),seeded.state());
        ItemStack firstReserved=reserveTrusted(body,2), secondReserved=reserveTrusted(body,2);
        BlockPos target=helper.absolutePos(new BlockPos(8,2,8)); level.setBlock(target,Blocks.AIR.defaultBlockState(),3);
        var data=DisposalTubeWorldData.forMutation(level); AtomicInteger refunds=new AtomicInteger(), installs=new AtomicInteger();
        for(int i=0;i<2;i++) {
            final boolean first=i==0; ItemStack reserved=first?firstReserved:secondReserved;
            // Mirror the construction reservation identity without widening its package-private production helper.
            String key=target.asLong()+":reservation:"+new UUID(8,i+1);
            require(TimedInteractionSystem.start(body,null,0,"construction_foundation",key,20,true,true,
                    ()->data.lookup(level,target).kind()==DisposalTubeWorldData.LookupKind.ABSENT,
                    ()->{
                        var record=new DisposalTubeRecord(UUID.randomUUID(),target,"moonstation14:disposal_pipe",Direction.NORTH,false,"","",0);
                        require(data.installExposed(level,record),"first same-cell job installs physical tube"); installs.incrementAndGet();
                        return TimedInteractionSystem.Completion.STOP;
                    },Optional.of(target),()->{
                        refunds.incrementAndGet();
                        LiveHands current=body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
                        var placed=current.placeCraftOutput(current.revision(),reserved,"same-cell-refund-"+(first?"first":"second"));
                        require(placed.isPresent(),"canceled reservation returns to body-owned hands");
                        body.setData(ModDataAttachments.LIVE_HANDS.get(),placed.orElseThrow());
                    }).name().equals("STARTED"),"same target admits independent initial job keys");
        }
        helper.succeedWhen(()->{
            require(installs.get()==1,"only first completion installs at occupied target; installs="+installs.get()
                    +", refunds="+refunds.get()+", alive="+body.isAlive()+", health="+body.getHealth()
                    +"/"+initialHealth+", bodyPos="+body.position()
                    +", targetState="+level.getBlockState(target)+", lookup="+data.lookup(level,target).kind()
                    +", activeOperations="+Optional.ofNullable(body.getExistingDataOrNull(
                    com.juicyslew.moonstation14.component.ModDataAttachments.TIMED_OPERATIONS.get()))
                    .map(state->state.operations().stream().filter(op->op.phase()==com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation.Phase.ACTIVE).count()).orElse(0L));
            require(refunds.get()==1,"late-invalidated second operation refunds exactly once");
            require(totalSteel(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()))==2,
                    "four reserved steel conserved as two installed and two refunded");
            require(data.lookup(level,target).kind()==DisposalTubeWorldData.LookupKind.PRESENT,"one valid target owner remains");
        });
    }

    private static Villager body(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
        body.setNoAi(true); body.setNoGravity(true);
        require(BodyHandBootstrap.ensure(body) == BodyHandBootstrap.Result.INITIALIZED, "LIVE_HANDS bootstrap");
        return body;
    }
    /** Fixture-only trusted reservation seam: apply the immutable stack replacement before timer admission. */
    private static ItemStack reserveTrusted(Villager body, int amount) {
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        String hand = hands.activeHand(); String token = hands.token(hand).orElseThrow();
        ItemStack before = hands.stackCopy(hand).orElseThrow();
        require(before.is(ModItems.STEEL.get()) && before.getCount() >= amount, "trusted body inventory has adequate steel");
        ItemStack reserved = before.copy(); reserved.setCount(amount);
        ItemStack remainder = before.copy(); remainder.shrink(amount);
        var changed = hands.replaceActiveUseStack(hands.revision(), hand, token, before, remainder);
        require(changed.isPresent(), "LiveHands immutable material reservation replacement succeeds");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), changed.orElseThrow());
        return reserved;
    }
    private static int totalSteel(LiveHands hands) {
        int total = 0;
        for (String hand : hands.handIds()) total += hands.stackCopy(hand).filter(stack -> stack.is(ModItems.STEEL.get()))
                .map(ItemStack::getCount).orElse(0);
        return total;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new GameTestAssertException(message); }
}
