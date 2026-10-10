package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.entities.LooseItemEntity;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import com.juicyslew.moonstation14.ms14.machine.itemslots.MachineItemSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Trusted-body transaction-core fixtures; these do not claim authenticated transport coverage. */
@PrefixGameTestTemplate(false)
@GameTestTags({"machine-slots", "hands"})
public final class BodyMachineItemSlotTransferGameTests {
    private BodyMachineItemSlotTransferGameTests() { }

    public static void insertTakePreserveWholeStackAndReinsertAfterOrdinaryOwnershipRelease(GameTestHelper helper) {
        Villager body = body(helper);
        body.setYRot(-90); body.setYHeadRot(-90);
        ItemLocationLedger ledger = new ItemLocationLedger();
        MachineItemSlot slot = slot(body, ledger);
        LiveHands initial = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        ItemStack source = new ItemStack(Items.DIAMOND, 3);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("machine slot exact payload"));
        LiveHands held = initial.putWhole(initial.revision(), "left", new ItemToken("slot-exact"), source).state();
        body.setData(ModDataAttachments.LIVE_HANDS.get(), held);

        require(BodyMachineItemSlotTransfer.insertOwnedForFixture(body, "left", "slot-exact", held.revision(),
                slot, 0, ledger.snapshot().revision(), ledger, () -> true) == BodyMachineItemSlotTransfer.Result.SUCCESS,
                "valid body hand inserts into machine slot");
        require(body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()).token("left").isEmpty(), "source hand cleared");
        require(slot.snapshot().token().equals(new ItemToken("slot-exact"))
                && ItemStack.matches(source, slot.snapshot().stack()), "whole count/components/token retained");

        LiveHands empty = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(BodyMachineItemSlotTransfer.takeOwnedForFixture(body, "left", empty.revision(), slot, 1,
                ledger.snapshot().revision(), ledger, () -> true) == BodyMachineItemSlotTransfer.Result.SUCCESS,
                "slot returns whole item to empty hand");
        require(ledger.snapshot().locations().get(new ItemToken("slot-exact")) == null,
                "ordinary hand becomes untracked after successful release");

        LiveHands returned = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(BodyMachineItemSlotTransfer.insertOwnedForFixture(body, "left", "slot-exact", returned.revision(),
                slot, 2, ledger.snapshot().revision(), ledger, () -> true) == BodyMachineItemSlotTransfer.Result.SUCCESS,
                "same item can return after ordinary untracked hand ownership");
        LiveHands after = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(BodyMachineItemSlotTransfer.takeOwnedForFixture(body, "left", after.revision(), slot, 3,
                ledger.snapshot().revision(), ledger, () -> true) == BodyMachineItemSlotTransfer.Result.SUCCESS,
                "repeated release succeeds");
        require(!ledger.snapshot().locations().containsKey(new ItemToken("slot-exact")), "no stale hand claim remains");
        cleanup(helper, body, ledger);
        helper.succeed();
    }

    public static void fullHandsFallBackToCanonicalWorldAndRetireLedgerClaim(GameTestHelper helper) {
        Villager body = body(helper);
        body.setYRot(-90); body.setYHeadRot(-90);
        ItemLocationLedger ledger = new ItemLocationLedger();
        MachineItemSlot slot = slot(body, ledger);
        ItemToken token = new ItemToken("world-fallback");
        ItemStack payload = new ItemStack(Items.DIAMOND, 3);
        payload.set(DataComponents.CUSTOM_NAME, Component.literal("world fallback payload"));
        ItemLocation source = new ItemLocation.BodyHand(body.getUUID(), "left");
        require(ledger.register(token, source).accepted(), "ledger fixture source");
        require(slot.insert(0, ledger.snapshot().revision(), token, source, payload), "seed slot payload");
        LiveHands state = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        LiveHands both = state.putWhole(state.revision(), "left", new ItemToken("left-full"), new ItemStack(Items.STICK))
                .state().putWhole(state.revision() + 1, "right", new ItemToken("right-full"), new ItemStack(Items.APPLE)).state();
        body.setData(ModDataAttachments.LIVE_HANDS.get(), both);
        long ledgerRevision = ledger.snapshot().revision();
        require(BodyMachineItemSlotTransfer.takeOwnedForFixture(body, "left", both.revision(), slot, 1,
                ledgerRevision, ledger, () -> true) == BodyMachineItemSlotTransfer.Result.DENIED,
                "occupied hand rejects without changing slot or ledger");
        require(ItemStack.matches(payload, slot.snapshot().stack())
                        && ledger.snapshot().locations().get(token).equals(new ItemLocation.MachineSlot(body.getUUID(), "input")),
                "full-hand denial compensates to exact machine owner");
        require(BodyMachineItemSlotTransfer.ejectToWorldOwnedForFixture(body, slot, slot.snapshot().revision(),
                ledger.snapshot().revision(), ledger,
                () -> true) == BodyMachineItemSlotTransfer.Result.SUCCESS, "full hands permit canonical world fallback");
        var worldItems = helper.getLevel().getEntitiesOfClass(LooseItemEntity.class, body.getBoundingBox().inflate(5),
                entity -> entity.getItem().is(Items.DIAMOND));
        require(worldItems.size() == 1 && ItemStack.matches(payload, worldItems.getFirst().getItem()),
                "one actual canonical world entity owns exact payload");
        assertInsideScene(helper, worldItems.getFirst().blockPosition());
        require(!ledger.snapshot().locations().containsKey(token), "successful world release retires opt-in claim");
        LiveHands fullState = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(BodyHandItemTransfer.dropOwned(body, "left", "left-full", fullState.revision(), () -> true)
                        == BodyHandItemTransfer.Result.SUCCESS,
                "ordinary hand drop after machine ejection succeeds");
        var worldPayload = worldItems.getFirst();
        LiveHands afterDrop = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        awaitWorldPickup(helper, body, worldPayload, slot, ledger, token, payload, afterDrop.revision(), 0);
    }

    public static void rejectedWorldSpawnRestoresExactMachineOwner(GameTestHelper helper) {
        Villager body = body(helper);
        ItemLocationLedger ledger = new ItemLocationLedger();
        MachineItemSlot slot = slot(body, ledger);
        ItemToken token = new ItemToken("spawn-rejected");
        ItemStack payload = new ItemStack(Items.DIAMOND, 2);
        ItemLocation source = new ItemLocation.BodyHand(body.getUUID(), "left");
        require(ledger.register(token, source).accepted() && slot.insert(0, ledger.snapshot().revision(), token,
                source, payload), "seed payload");
        require(BodyMachineItemSlotTransfer.ejectToWorldOwnedForFixture(body, slot, 1,
                ledger.snapshot().revision(), ledger, () -> false) == BodyMachineItemSlotTransfer.Result.DENIED
                && slot.snapshot().token().equals(token), "stale machine context denies without changing owner");
        AtomicInteger canceled = new AtomicInteger();
        Consumer<EntityJoinLevelEvent> listener = event -> {
            if (event.getEntity() instanceof LooseItemEntity item && item.getItem().is(Items.DIAMOND)
                    && event.getLevel() == helper.getLevel()) { canceled.incrementAndGet(); event.setCanceled(true); }
        };
        NeoForge.EVENT_BUS.addListener(listener);
        BodyMachineItemSlotTransfer.Result result;
        try {
            result = BodyMachineItemSlotTransfer.ejectToWorldOwnedForFixture(body, slot, 1,
                    ledger.snapshot().revision(), ledger, () -> true);
        } finally { NeoForge.EVENT_BUS.unregister(listener); }
        require(result == BodyMachineItemSlotTransfer.Result.DENIED && canceled.get() == 1,
                "canceled world admission denies and compensates");
        require(slot.snapshot().token().equals(token) && ItemStack.matches(payload, slot.snapshot().stack())
                && ledger.snapshot().locations().get(token).equals(new ItemLocation.MachineSlot(body.getUUID(), "input")),
                "failed spawn restores exact machine token, stack and ledger source");
        require(helper.getLevel().getEntitiesOfClass(LooseItemEntity.class, body.getBoundingBox().inflate(5),
                item -> item.getItem().is(Items.DIAMOND)).isEmpty(), "failed admission leaves no world duplicate");
        body.discard();
        require(BodyMachineItemSlotTransfer.ejectToWorldOwnedForFixture(body, slot, slot.snapshot().revision(),
                ledger.snapshot().revision(), ledger, () -> true) == BodyMachineItemSlotTransfer.Result.DENIED,
                "removed body cannot release machine payload");
        helper.succeed();
    }

    public static void acceptedWorldSpawnRetiresClaimWhenContextInvalidates(GameTestHelper helper) {
        Villager body = body(helper);
        ItemLocationLedger ledger = new ItemLocationLedger();
        MachineItemSlot slot = slot(body, ledger);
        ItemToken token = new ItemToken("context-changed-after-spawn");
        ItemStack payload = new ItemStack(Items.DIAMOND, 2);
        ItemLocation source = new ItemLocation.BodyHand(body.getUUID(), "left");
        require(ledger.register(token, source).accepted() && slot.insert(0, ledger.snapshot().revision(), token,
                source, payload), "seed payload");
        AtomicInteger joins = new AtomicInteger();
        Consumer<EntityJoinLevelEvent> listener = event -> {
            if (event.getEntity() instanceof LooseItemEntity item && item.getItem().is(Items.DIAMOND)
                    && event.getLevel() == helper.getLevel()) joins.incrementAndGet();
        };
        NeoForge.EVENT_BUS.addListener(listener);
        AtomicInteger checks = new AtomicInteger();
        BodyMachineItemSlotTransfer.Result result;
        try {
            result = BodyMachineItemSlotTransfer.ejectToWorldOwnedForFixture(body, slot, 1,
                    ledger.snapshot().revision(), ledger, () -> checks.incrementAndGet() < 3);
        } finally { NeoForge.EVENT_BUS.unregister(listener); }
        require(result == BodyMachineItemSlotTransfer.Result.RECOVERY_REQUIRED && joins.get() == 1,
                "accepted exact world payload reports changed context as recovery");
        var items = helper.getLevel().getEntitiesOfClass(LooseItemEntity.class, body.getBoundingBox().inflate(5),
                entity -> entity.getItem().is(Items.DIAMOND));
        require(items.size() == 1 && ItemStack.matches(payload, items.getFirst().getItem())
                        && !ledger.snapshot().locations().containsKey(token),
                "confirmed world payload retires opt-in claim despite later context invalidation");
        items.forEach(LooseItemEntity::discard);
        body.discard();
        helper.succeed();
    }

    private static Villager body(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        body.setNoAi(true);
        var readiness = BodyHandBootstrap.ensure(body);
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var prototypeHands = com.juicyslew.moonstation14.ms14.hands.HandCapability.resolve(body);
        require(helper.getBounds().contains(body.position()) && insideScene(helper, body.blockPosition())
                        && (readiness == BodyHandBootstrap.Result.INITIALIZED
                        || readiness == BodyHandBootstrap.Result.PRESERVED)
                        && hands != null && hands.compatible(body)
                        && prototypeHands.filter(hands.handIds()::equals).isPresent(),
                "in-bounds body has compatible hands at readiness boundary (result=" + readiness
                        + ", resolved=" + prototypeHands.orElse(List.of()) + ", live="
                        + (hands == null ? "absent" : hands.handIds()) + ")");
        return body;
    }
    private static void awaitWorldPickup(GameTestHelper helper, Villager body, LooseItemEntity worldPayload,
                                         MachineItemSlot slot, ItemLocationLedger ledger, ItemToken oldToken,
                                         ItemStack payload, long handRevision, int waited) {
        if (worldPayload.hasPickUpDelay()) {
            require(waited < 30, "normal world drop pickup delay expires within bounded fixture wait");
            helper.runAfterDelay(1, () -> awaitWorldPickup(helper, body, worldPayload, slot, ledger,
                    oldToken, payload, handRevision, waited + 1));
            return;
        }
        require(body.distanceToSqr(worldPayload) <= 25, "stable body remains within ordinary world pickup reach");
        body.lookAt(EntityAnchorArgument.Anchor.EYES, worldPayload.position());
        require(com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe.probeSelected(
                        (net.minecraft.server.level.ServerLevel) helper.getLevel(), body, worldPayload.getUUID(), 5.0) != null,
                "ordinary world drop is visible to the body-eye pickup ray");
        require(BodyHandItemTransfer.pickupSelectedOwned(body, worldPayload.getUUID(), "left", handRevision, () -> true)
                        == BodyHandItemTransfer.Result.SUCCESS,
                "ordinary world pickup succeeds after its native pickup delay");
        LiveHands picked = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        require(ItemStack.matches(payload, picked.stackCopy("left").orElseThrow())
                        && !ledger.snapshot().locations().containsKey(oldToken),
                "ordinary pickup owns unchanged stack and old opt-in machine claim remains retired");
        String pickedToken = picked.token("left").orElseThrow();
        require(BodyMachineItemSlotTransfer.insertOwnedForFixture(body, "left", pickedToken, picked.revision(),
                slot, slot.snapshot().revision(), ledger.snapshot().revision(), ledger, () -> true)
                        == BodyMachineItemSlotTransfer.Result.SUCCESS,
                "new canonical pickup token can be reinserted");
        require(ItemStack.matches(payload, slot.snapshot().stack()) && slot.snapshot().stack().getCount() == 3,
                "reinsert preserves exact components and count");
        slot.eject(slot.snapshot().revision(), ledger.snapshot().revision(),
                new ItemLocation.WorldEntity(com.juicyslew.moonstation14.ms14.disposal.item.DisposalWorldPayloadStage.worldIdentity(helper.getLevel()), UUID.randomUUID()));
        worldPayload.discard();
        body.discard();
        helper.succeed();
    }
    private static void assertInsideScene(GameTestHelper helper, BlockPos position) {
        require(insideScene(helper, position), "position inside declared 16x10x16 transaction fixture: " + position);
    }
    private static boolean insideScene(GameTestHelper helper, BlockPos position) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int x = position.getX() - origin.getX(), y = position.getY() - origin.getY(), z = position.getZ() - origin.getZ();
        return x >= 0 && x < 16 && y >= 0 && y < 10 && z >= 0 && z < 16;
    }
    private static MachineItemSlot slot(Villager body, ItemLocationLedger ledger) {
        return new MachineItemSlot(body.getUUID(), "input", stack -> stack.is(Items.DIAMOND), ledger);
    }
    private static void cleanup(GameTestHelper helper, Villager body, ItemLocationLedger ledger) {
        body.discard();
        helper.getLevel().getEntitiesOfClass(LooseItemEntity.class, helper.getBounds(), ignored -> true)
                .forEach(LooseItemEntity::discard);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
