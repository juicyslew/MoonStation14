package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.damage.DamageKeys;
import com.juicyslew.moonstation14.ms14.damage.DamageSystem;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.ingestion.BodyTimedIngestionService;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedInteractionSystem;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestTags({"timed-interaction", "ingestion", "damage", "hands"})
@PrefixGameTestTemplate(false)
public final class TimedInteractionDamageGameTests {
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> WATER =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
    private TimedInteractionDamageGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 45)
    public static void actualDamageCancelsBeforeIngestionCompletes(GameTestHelper helper) {
        Villager body = body(helper, new BlockPos(2, 1, 2));
        LiveHands hands = body.getData(ModDataAttachments.LIVE_HANDS.get());
        var source = bottle(body, hands, 10f);
        final LiveHands liveHands = body.getData(ModDataAttachments.LIVE_HANDS.get());
        require(BodyTimedIngestionService.startHeldOwned(body, liveHands.activeHand(), source, liveHands.revision(), 10, () -> true).name().equals("OK"), "held ingestion starts");
        float initial = body.getHealth();
        helper.runAfterDelay(10, () -> {
            require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, 1f), 1f, true) == DamageSystem.Result.APPLIED, "actual typed damage applied");
            float afterDamage = body.getHealth();
            body.heal(initial - afterDamage);
            require(Math.abs(body.getHealth() - initial) < .001f, "immediate healing restores actual health");
            require(Math.abs(afterDamage - (initial - .2f)) < .001f, "one typed damage projects to .2 health");
            TimedOperation op = operation(body);
            require(op.phase() == TimedOperation.Phase.CANCELLED, "damage event cancels operation despite same-frame healing");
            require(body.getData(ModDataAttachments.LIVE_HANDS.get()).token(liveHands.activeHand()).orElseThrow().equals(source), "source remains unchanged");
            helper.runAfterDelay(5, () -> {
                require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().anyMatch(o -> o.id() == op.id() && o.phase() == TimedOperation.Phase.CANCELLED), "terminal cancellation retained for ten ticks");
                require(body.getData(ModDataAttachments.LIVE_HANDS.get()).token(liveHands.activeHand()).orElseThrow().equals(source), "cancelled operation produces no dose");
                helper.runAfterDelay(6, () -> {
                    require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().noneMatch(o -> o.id() == op.id()), "terminal record pruned after retention window");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 50)
    public static void subthresholdRejectedDamageAndHealingDoNotCancel(GameTestHelper helper) {
        Villager body = body(helper, new BlockPos(2, 1, 2));
        LiveHands hands = body.getData(ModDataAttachments.LIVE_HANDS.get());
        String source = bottle(body, hands, 5f);
        final LiveHands liveHands = body.getData(ModDataAttachments.LIVE_HANDS.get());
        BodyTimedIngestionService.startHeldOwned(body, liveHands.activeHand(), source, liveHands.revision(), 11, () -> true);
        helper.runAfterDelay(2, () -> {
            body.invulnerableTime = 0;
            body.hurt(helper.getLevel().damageSources().generic(), 0f);
            require(Math.abs(body.getHealth() - body.getMaxHealth()) < .001f, "zero native damage leaves health unchanged");
            require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().anyMatch(o -> o.phase() == TimedOperation.Phase.ACTIVE), "zero native damage does not cancel operation");
            require(DamageSystem.applyHealthChange(body, Map.of(DamageKeys.BLUNT, .5f), 1f, true) == DamageSystem.Result.APPLIED, "half typed damage applied");
            require(Math.abs(body.getHealth() - (body.getMaxHealth() - .1f)) < .001f, "actual final health confirms .1 health / .5 typed damage");
            body.heal(1f);
            require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().anyMatch(o -> o.phase() == TimedOperation.Phase.ACTIVE), "subthreshold damage, rejection, and healing leave timer active");
            helper.runAfterDelay(19, () -> {
                var currentHands = body.getData(ModDataAttachments.LIVE_HANDS.get());
                var remaining = currentHands.stackCopy(liveHands.activeHand()).orElseThrow().get(ModDataComponents.REAGENT.get());
                require(remaining != null && remaining.contents().isEmpty(), "dose leaves the retained bottle empty");
                require(currentHands.token(liveHands.activeHand()).orElseThrow().equals(source), "empty bottle remains in the hand");
                require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().anyMatch(o -> o.phase() == TimedOperation.Phase.COMPLETED), "operation completed, not cancelled");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 35)
    public static void moveCancellationAndGenericCallbackCompletion(GameTestHelper helper) {
        Villager body = body(helper, new BlockPos(2, 1, 2));
        AtomicInteger breakMoveCalls = new AtomicInteger();
        require(TimedInteractionSystem.start(body, null, 12, "generic", "move", 20, false, true,
                () -> true, () -> { breakMoveCalls.incrementAndGet(); return TimedInteractionSystem.Completion.STOP; }).name().equals("STARTED"), "movement-sensitive generic operation starts");
        require(TimedInteractionSystem.start(body, null, 13, "generic", "still", 20, false, false,
                () -> true, () -> { breakMoveCalls.incrementAndGet(); return TimedInteractionSystem.Completion.STOP; }).name().equals("STARTED"), "movement-insensitive generic operation starts");
        long moveId = body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().get(0).id();
        long stillId = body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().get(1).id();
        helper.runAfterDelay(1, () -> body.setPos(body.getX() + 1, body.getY(), body.getZ()));
        helper.runAfterDelay(3, () -> {
            var operations = body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations();
            require(operations.stream().anyMatch(o -> o.id() == moveId && o.phase() == TimedOperation.Phase.CANCELLED), "move-enabled operation cancels after movement");
            require(breakMoveCalls.get() == 0, "cancelled movement callback does not run");
            helper.runAfterDelay(18, () -> {
                var laterOperations = body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations();
                require(laterOperations.stream().noneMatch(o -> o.id() == moveId), "cancelled movement record prunes after retention");
                require(laterOperations.stream().anyMatch(o -> o.id() == stillId && o.phase() == TimedOperation.Phase.COMPLETED), "generic callback completes despite movement when breakMove is false");
                require(breakMoveCalls.get() == 1, "only the still-valid callback runs once");
                helper.runAfterDelay(11, () -> {
                    require(body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().stream().noneMatch(o -> o.id() == moveId || o.id() == stillId), "both terminal records prune");
                    helper.succeed();
                });
            });
        });
    }

    private static Villager body(GameTestHelper helper, BlockPos pos) {
        var level = helper.getLevel();
        level.setBlock(helper.absolutePos(pos).below(), Blocks.STONE.defaultBlockState(), 3);
        Villager body = EntityType.VILLAGER.create(level);
        require(body != null, "body created");
        body.setNoAi(true); body.setNoGravity(true);
        BlockPos absolute = helper.absolutePos(pos);
        body.setPos(absolute.getX() + .5, absolute.getY(), absolute.getZ() + .5);
        require(level.addFreshEntity(body) && CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "human body enrolled");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body), "body and hands initialized");
        return body;
    }

    private static String bottle(Villager body, LiveHands hands, float units) {
        ItemStack filled = new ItemStack(ModItems.BOTTLE.get());
        filled.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, units)));
        var inserted = hands.putWhole(hands.revision(), hands.activeHand(),
                new com.juicyslew.moonstation14.ms14.hands.ItemToken("timed-damage-water-bottle"), filled);
        require(inserted.succeeded(), "positive-water bottle placed in active hand");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), inserted.state());
        return inserted.state().token(inserted.state().activeHand()).orElseThrow();
    }

    private static TimedOperation operation(Villager body) {
        return body.getData(ModDataAttachments.TIMED_OPERATIONS.get()).operations().get(0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
