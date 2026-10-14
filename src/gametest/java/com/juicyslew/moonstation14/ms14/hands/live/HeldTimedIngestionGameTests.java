package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.ingestion.BodyTimedIngestionService;
import com.juicyslew.moonstation14.ms14.organ.BodySystem;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperation;
import com.juicyslew.moonstation14.ms14.timed_interaction.TimedOperationState;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@GameTestTags({"timed-interaction", "ingestion", "hands", "chemistry"})
@PrefixGameTestTemplate(false)
public final class HeldTimedIngestionGameTests {
    private static final ResourceKey<com.juicyslew.moonstation14.component.codec.json.ReagentData> WATER =
            ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, ResourceLocation.fromNamespaceAndPath("moonstation14", "water"));
    private HeldTimedIngestionGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 110)
    public static void heldDoseWaitsRepeatsAndStopsAtEmpty(GameTestHelper h) {
        Fixture f = fixture(h, 10); start(f, 1, () -> true);
        h.runAfterDelay(10, () -> { require(source(f) == 1000, "no dose before profile deadline"); });
        h.runAfterDelay(23, () -> {
            require(source(f) == 500, "first 500-cent dose");
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(h, 15, () -> { require(source(f) == 500, "repeat waits complete interval"); });
            com.juicyslew.moonstation14.gametest.support.BoundedGameTestWait.safeDelay(h, 23, () -> {
                require(source(f) == 0, "second exact source dose");
                LiveHands now = hands(f); require(now.revision() == f.revision + 2, "one owner revision per dose");
                require(now.token("left").filter(f.token::equals).isPresent(), "empty container remains held");
                require(!active(f), "empty source operation completed without another empty cycle"); h.succeed();
            });
        });
    }

    @GameTestTags({"hands-liquid", "beaker"})
    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 40)
    public static void resolvedBeakerDrinkConsumesFiveUnitsAfterTwentyTicks(GameTestHelper h) {
        Fixture f = fixtureWithItem(h, new ItemStack(ModItems.BEAKER.get()), 6);
        AtomicReference<com.juicyslew.moonstation14.ms14.ingestion.IngestionReceiptObserver.Receipt> receipt = new AtomicReference<>();
        AutoCloseable observer = com.juicyslew.moonstation14.ms14.ingestion.IngestionReceiptObserver.observe(r -> {
            if (r.actorId() == null && r.bodyId().equals(f.body.getUUID()) && r.sourceKey().equals("held:" + f.token)) receipt.set(r);
        });
        AtomicBoolean closed = new AtomicBoolean();
        Runnable closeObserver = () -> {
            if (closed.compareAndSet(false, true)) try { observer.close(); } catch (Exception ignored) { }
        };
        h.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo ignored) { }
            @Override public void testPassed(GameTestInfo ignored, GameTestRunner runner) { closeObserver.run(); }
            @Override public void testFailed(GameTestInfo ignored, GameTestRunner runner) { closeObserver.run(); }
            @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { closeObserver.run(); }
        });
        require(BodyTimedIngestionService.startHeldOwned(f.body, "left", f.token, f.revision, 90, () -> true).name().equals("OK"),
                "resolved drink policy starts the existing timed ingestion operation");
        AtomicReference<String> trace = new AtomicReference<>("");
        for (int tick = 19; tick <= 23; tick++) {
            int at = tick;
            h.runAfterDelay(tick, () -> trace.set(trace.get() + (trace.get().isEmpty() ? "" : ";") + at + ":" + ingestionSample(f)));
        }
        h.runAfterDelay(10, () -> require(source(f) == 600, "beaker remains unchanged before its 20-tick deadline"));
        h.runAfterDelay(23, () -> {
            var committed = receipt.get();
            require(committed != null && committed.acceptedCents() == 500,
                    "actual canonical transaction committed exact 5u; receipt=" + committed + " " + ingestionSample(f) + " trace=" + trace.get());
            require(source(f) == 100, "resolved 5u serving leaves 1u from 6u source; " + ingestionSample(f) + " trace=" + trace.get());
            closeObserver.run();
            h.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void heldTokenMayMoveHandsAndBodyWithoutCancelling(GameTestHelper h) {
        Fixture f = fixture(h, 10); start(f, 2, () -> true);
        LiveHands before = hands(f);
        var moved = before.transferWhole(before.revision(), "left", "right", new ItemToken(f.token));
        require(moved.succeeded(), "whole item moved to other hand"); f.body.setData(ModDataAttachments.LIVE_HANDS.get(), moved.state());
        var selectRight = moved.state().selectActive(moved.state().revision(), "right");
        require(selectRight.isPresent(), "different active hand selected"); f.body.setData(ModDataAttachments.LIVE_HANDS.get(), selectRight.orElseThrow());
        var selectLeft = selectRight.orElseThrow().selectActive(selectRight.orElseThrow().revision(), "left");
        require(selectLeft.isPresent(), "original active hand selected again"); f.body.setData(ModDataAttachments.LIVE_HANDS.get(), selectLeft.orElseThrow());
        require(hands(f).token("right").filter(f.token::equals).isPresent() && hands(f).activeHand().equals("left"),
                "original token remains in right while active hand returns left");
        h.runAfterDelay(8, () -> f.body.setPos(f.body.getX() + .2, f.body.getY(), f.body.getZ() + .5));
        h.runAfterDelay(22, () -> { require(source(f) == 500, "original token consumed after transfer, selection change, and movement"); h.succeed(); });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void leavingHandsCancelsHeldIngestion(GameTestHelper h) {
        Fixture f = fixture(h, 10); start(f, 3, () -> true);
        ItemStack droppedStack = hands(f).stackCopy("left").orElseThrow().copy();
        AtomicReference<java.util.UUID> droppedId = new AtomicReference<>();
        require(BodyLooseThrow.throwOwned(f.body, "left", f.token, hands(f).revision(), () -> true, droppedId::set).name().equals("SUCCESS"), "whole held item dropped through owner transfer seam");
        require(hands(f).stackCopy("left").isEmpty(), "owner hand emptied by throw");
        h.runAfterDelay(2, () -> {
            var dropped = h.getLevel().getEntity(droppedId.get());
            require(dropped instanceof net.minecraft.world.entity.item.ItemEntity item && !item.isRemoved()
                    && item.getUUID().equals(droppedId.get()) && ItemStack.matches(droppedStack, item.getItem())
                    && ReagentUnits.fromFloat(item.getItem().get(ModDataComponents.REAGENT.get()).contents().getOrDefault(WATER, 0f)) == 1000,
                    "actual dropped item retains exact source contents");
            require(source(f) == 0 && cancelled(f), "dropped source cancels without consuming held source"); h.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void authorityLossCancelsAndStaleDuplicateCannotCancel(GameTestHelper h) {
        Fixture f = fixture(h, 10); AtomicBoolean authority = new AtomicBoolean(true); start(f, 4, authority::get);
        require(BodyTimedIngestionService.startHeldOwned(f.body, "left", f.token, f.revision - 1, 4, authority::get).name().equals("DENIED"), "stale revision duplicate denied");
        require(active(f), "denial preserves original active operation");
        require(BodyTimedIngestionService.startHeldOwned(f.body, "left", f.token, f.revision, 5, authority::get).name().equals("OK"), "fresh same-gesture request cancels operation");
        require(cancelled(f), "duplicate toggle cancellation recorded");
        Fixture lost = fixture(h, 10); AtomicBoolean live = new AtomicBoolean(true); start(lost, 6, live::get); live.set(false);
        h.runAfterDelay(2, () -> { require(source(lost) == 1000 && cancelled(lost), "authority loss cancels without dose"); h.succeed(); });
    }

    private static Fixture fixture(GameTestHelper h, float units) {
        return fixtureWithItem(h, new ItemStack(ModItems.BOTTLE.get()), units);
    }
    private static Fixture fixtureWithItem(GameTestHelper h, ItemStack source, float units) {
        var level = h.getLevel(); BlockPos p = h.absolutePos(new BlockPos(2, 1, 2));
        Villager body = EntityType.VILLAGER.create(level); require(body != null, "body created"); body.setNoAi(true); body.setNoGravity(true); body.setPos(p.getX()+.5,p.getY(),p.getZ()+.5);
        require(level.addFreshEntity(body) && CharacterIdentitySystem.enroll(body, level, ModCharacters.HUMAN_ID), "human body enrolled");
        require(LiveHands.initialize(body).isPresent() && BodySystem.reconcile(body), "eligible body initialized");
        LiveHands initial = hands(body);
        source.set(ModDataComponents.REAGENT.get(), new ReagentComponent(Map.of(WATER, units)));
        var put = initial.putWhole(initial.revision(), "left", new ItemToken("bottle"), source); require(put.succeeded(), "filled source inserted");
        body.setData(ModDataAttachments.LIVE_HANDS.get(), put.state());
        return new Fixture(body, put.state().token("left").orElseThrow(), put.state().revision());
    }
    private static void start(Fixture f, long seq, java.util.function.BooleanSupplier auth) {
        require(BodyTimedIngestionService.startHeldOwned(f.body,"left",f.token,f.revision,seq,auth).name().equals("OK"), "held ingestion started");
        require(source(f)==1000,"start does not transfer immediately");
    }
    private static LiveHands hands(Fixture f) { return hands(f.body); }
    private static LiveHands hands(Villager b) { return b.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()); }
    private static long source(Fixture f) { var r=f.body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).stackCopy("left"); if(r.isEmpty()) r=hands(f).stackCopy("right"); return r.map(s -> { var c=s.get(ModDataComponents.REAGENT.get()); return c==null?0L:ReagentUnits.fromFloat(c.contents().getOrDefault(WATER,0f)); }).orElse(0L); }
    private static String ingestionSample(Fixture f) {
        var stomach = com.juicyslew.moonstation14.ms14.MS14Provider.get(f.body, com.juicyslew.moonstation14.ms14.MS14Bridges.STOMACH).getMap();
        return "source=" + source(f) + "u token=" + f.token + " stomach=" + ReagentUnits.fromFloat(stomach.getOrDefault(WATER, 0f))
                + "u reagents=" + stomach + " alive=" + f.body.isAlive() + " health=" + f.body.getHealth()
                + " body=" + f.body.getUUID() + " op=" + state(f).operations();
    }
    private static TimedOperationState state(Fixture f) { return f.body.getExistingDataOrNull(ModDataAttachments.TIMED_OPERATIONS.get()); }
    private static boolean active(Fixture f) { return state(f).operations().stream().anyMatch(o -> o.phase()==TimedOperation.Phase.ACTIVE); }
    private static boolean cancelled(Fixture f) { return state(f).operations().stream().anyMatch(o -> o.phase()==TimedOperation.Phase.CANCELLED); }
    private record Fixture(Villager body, String token, long revision) { }
    private static void require(boolean b,String m) { if(!b) throw new GameTestAssertException(m); }
}
