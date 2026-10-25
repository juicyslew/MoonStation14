package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.combat.MeleeSelectionCooldown;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import com.juicyslew.moonstation14.ms14.machine.itemslots.MachineItemSlot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.entities.LooseItemEntity;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Authenticated server-thread hand/slot ownership transfer. Runtime must supply current machine validity. */
public final class BodyMachineItemSlotTransfer {
    private BodyMachineItemSlotTransfer() { }
    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }

    public static Result insert(HandActorAuthority.Snapshot authority, String hand, String expectedToken,
            long expectedHandRevision, MachineItemSlot slot, long expectedSlotRevision, long expectedLedgerRevision,
            ItemLocationLedger ledger, BooleanSupplier machineValid) {
        if (authority == null || expectedToken == null || slot == null || ledger == null || machineValid == null) return Result.DENIED;
        var actor = authority.actor(); LivingEntity body = authority.body();
        if (!authority.handIds().contains(hand)) return Result.DENIED;
        BooleanSupplier context = () -> valid(actor, authority, body, machineValid);
        return insertCore(body, hand, expectedToken, expectedHandRevision, slot, expectedSlotRevision,
                expectedLedgerRevision, ledger, context, next -> LiveHandsCommitGateway.commit(actor, authority, next));
    }

    /** Exchange one occupied slot with the exact item in the body's active hand. */
    public static Result swapActiveHand(HandActorAuthority.Snapshot authority, String expectedToken,
            long expectedHandRevision, MachineItemSlot slot, long expectedSlotRevision,
            long expectedLedgerRevision, ItemLocationLedger ledger, BooleanSupplier machineValid) {
        if (authority == null || slot == null || ledger == null || machineValid == null) return Result.DENIED;
        LivingEntity body = authority.body();
        BooleanSupplier context = () -> valid(authority.actor(), authority, body, machineValid);
        return swapCore(body, authority.handIds(), expectedToken, expectedHandRevision, slot, expectedSlotRevision,
                expectedLedgerRevision, ledger, context,
                next -> LiveHandsCommitGateway.commit(authority.actor(), authority, next));
    }

    static Result swapActiveHandOwnedForFixture(LivingEntity body, String expectedToken, long expectedHandRevision,
            MachineItemSlot slot, long expectedSlotRevision, long expectedLedgerRevision,
            ItemLocationLedger ledger, BooleanSupplier machineValid) {
        return swapActiveHandOwnedForFixture(body, expectedToken, expectedHandRevision, slot, expectedSlotRevision,
                expectedLedgerRevision, ledger, machineValid, null);
    }

    /** Trusted-core fixture seam for exercising a rejected commit; never used by a client request route. */
    static Result swapActiveHandOwnedForFixture(LivingEntity body, String expectedToken, long expectedHandRevision,
            MachineItemSlot slot, long expectedSlotRevision, long expectedLedgerRevision,
            ItemLocationLedger ledger, BooleanSupplier machineValid, java.util.function.Predicate<LiveHands> commitOverride) {
        BooleanSupplier context = () -> validOwnedBody(body, machineValid);
        LiveHands current = body == null ? null : body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return swapCore(body, current == null ? java.util.List.of() : current.handIds(), expectedToken,
                expectedHandRevision, slot, expectedSlotRevision, expectedLedgerRevision, ledger, context,
                commitOverride == null ? next -> LiveHandsCommitGateway.commitTrusted(body, current, next) : commitOverride);
    }

    private static Result swapCore(LivingEntity body, java.util.Collection<String> supportedHands, String expectedToken,
            long expectedHandRevision, MachineItemSlot slot, long expectedSlotRevision, long expectedLedgerRevision,
            ItemLocationLedger ledger, BooleanSupplier context, java.util.function.Predicate<LiveHands> commit) {
        if (body == null || slot == null || ledger == null || expectedToken == null || !context.getAsBoolean()) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || before.revision() != expectedHandRevision || !before.compatible(body)
                || !supportedHands.contains(before.activeHand()) || before.revision() > Long.MAX_VALUE - 2) return Result.DENIED;
        String hand = before.activeHand();
        ItemToken incoming;
        try { incoming = new ItemToken(expectedToken); } catch (IllegalArgumentException failure) { return Result.DENIED; }
        var taken = before.takeWhole(expectedHandRevision, hand, incoming);
        if (!taken.succeeded()) return Result.DENIED;
        var held = before.stackCopy(hand).orElse(null);
        var slotBefore = slot.snapshot();
        if (held == null || !slot.canReplace(expectedSlotRevision, held) || slotBefore.token() == null
                || slotBefore.revision() >= Long.MAX_VALUE - 1 || ledger.snapshot().revision() != expectedLedgerRevision)
            return Result.DENIED;
        ItemToken outgoing = slotBefore.token();
        var placed = taken.state().putWhole(taken.state().revision(), hand, outgoing, slotBefore.stack());
        if (!placed.succeeded() || !context.getAsBoolean()
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before) return Result.DENIED;
        ItemLocation source = new ItemLocation.BodyHand(body.getUUID(), hand);
        if (slot.exchange(expectedSlotRevision, expectedLedgerRevision, incoming, source, held, source).isEmpty())
            return Result.DENIED;
        try {
            if (!commit.test(placed.state())) {
                if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != placed.state())
                    compensateSwap(slot, ledger, outgoing, incoming, source, slotBefore.stack());
                return Result.RECOVERY_REQUIRED;
            }
        } catch (RuntimeException failure) {
            if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != placed.state())
                compensateSwap(slot, ledger, outgoing, incoming, source, slotBefore.stack());
            return Result.RECOVERY_REQUIRED;
        }
        return context.getAsBoolean()
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == placed.state()
                ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
    }

    static Result insertOwnedForFixture(LivingEntity body, String hand, String expectedToken,
            long expectedHandRevision, MachineItemSlot slot, long expectedSlotRevision, long expectedLedgerRevision,
            ItemLocationLedger ledger, BooleanSupplier machineValid) {
        BooleanSupplier context = () -> validOwnedBody(body, machineValid);
        return insertCore(body, hand, expectedToken, expectedHandRevision, slot, expectedSlotRevision,
                expectedLedgerRevision, ledger, context, next -> LiveHandsCommitGateway.commitTrusted(body,
                        body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()), next));
    }

    private static Result insertCore(LivingEntity body, String hand, String expectedToken, long expectedHandRevision,
            MachineItemSlot slot, long expectedSlotRevision, long expectedLedgerRevision, ItemLocationLedger ledger,
            BooleanSupplier context, java.util.function.Predicate<LiveHands> commit) {
        if (body == null || expectedToken == null || slot == null || ledger == null || !context.getAsBoolean()) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || before.revision() != expectedHandRevision || !before.compatible(body)) return Result.DENIED;
        ItemToken token;
        try { token = new ItemToken(expectedToken); } catch (IllegalArgumentException e) { return Result.DENIED; }
        var taken = before.takeWhole(expectedHandRevision, hand, token);
        if (!taken.succeeded()) return Result.DENIED;
        ItemStack stack = taken.takenStack().orElseThrow();
        if (!slot.canAccept(expectedSlotRevision, stack)) return Result.DENIED;
        ItemLocation source = new ItemLocation.BodyHand(body.getUUID(), hand);
        long ledgerRevision = expectedLedgerRevision;
        if (slot.snapshot().revision() >= Long.MAX_VALUE - 1) return Result.DENIED;
        var ownership = ledger.snapshot().locations().get(token);
        if (ownership == null) {
            if (ledger.snapshot().revision() != expectedLedgerRevision || !ledger.register(token, source).accepted()) return Result.DENIED;
            ledgerRevision = ledger.snapshot().revision();
        } else if (!ownership.equals(source)) return Result.DENIED;
        if (!context.getAsBoolean() || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !slot.insert(expectedSlotRevision, ledgerRevision, token, source, stack)) return Result.DENIED;
        try {
            if (!commit.test(taken.state())) {
                if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == taken.state())
                    return Result.RECOVERY_REQUIRED;
                compensateSlot(slot, expectedSlotRevision + 1, ledger, token, source);
                return Result.RECOVERY_REQUIRED;
            }
        } catch (RuntimeException failure) {
            if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == taken.state())
                return Result.RECOVERY_REQUIRED;
            compensateSlot(slot, expectedSlotRevision + 1, ledger, token, source);
            return Result.RECOVERY_REQUIRED;
        }
        return context.getAsBoolean()
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == taken.state()
                ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
    }

    public static Result take(HandActorAuthority.Snapshot authority, String hand, long expectedHandRevision,
            MachineItemSlot slot, long expectedSlotRevision, long expectedLedgerRevision,
            ItemLocationLedger ledger, BooleanSupplier machineValid) {
        if (authority == null || slot == null || ledger == null || machineValid == null) return Result.DENIED;
        var actor = authority.actor(); LivingEntity body = authority.body();
        if (!authority.handIds().contains(hand)) return Result.DENIED;
        BooleanSupplier context = () -> valid(actor, authority, body, machineValid);
        return takeCore(body, hand, expectedHandRevision, slot, expectedSlotRevision, expectedLedgerRevision,
                ledger, context, next -> LiveHandsCommitGateway.commit(actor, authority, next));
    }

    /** Eject to the active free hand, then another declared free hand, then canonical world release. */
    public static Result ejectToHandsOrWorld(HandActorAuthority.Snapshot authority, MachineItemSlot slot,
            long expectedHandRevision, long expectedSlotRevision, long expectedLedgerRevision,
            ItemLocationLedger ledger, BooleanSupplier machineValid) {
        if (authority == null || slot == null || ledger == null || machineValid == null) return Result.DENIED;
        LivingEntity body = authority.body();
        BooleanSupplier context = () -> valid(authority.actor(), authority, body, machineValid);
        return ejectToHandsOrWorldCore(body, expectedHandRevision, slot, expectedSlotRevision, expectedLedgerRevision,
                ledger, context, authority.handIds(), hand -> take(authority, hand, expectedHandRevision, slot,
                        expectedSlotRevision, expectedLedgerRevision, ledger, machineValid),
                () -> ejectToWorld(authority, slot, expectedSlotRevision, expectedLedgerRevision, ledger, machineValid));
    }

    /** Trusted native fixture seam; authorization is deliberately not provided by this package-private core. */
    static Result ejectToHandsOrWorldOwnedForFixture(LivingEntity body, MachineItemSlot slot,
            long expectedHandRevision, long expectedSlotRevision, long expectedLedgerRevision,
            ItemLocationLedger ledger, BooleanSupplier machineValid) {
        BooleanSupplier context = () -> validOwnedBody(body, machineValid);
        LiveHands hands = body == null ? null : body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return ejectToHandsOrWorldCore(body, expectedHandRevision, slot, expectedSlotRevision, expectedLedgerRevision,
                ledger, context, hands == null ? java.util.List.of() : hands.handIds(),
                hand -> takeOwnedForFixture(body, hand, expectedHandRevision, slot, expectedSlotRevision,
                        expectedLedgerRevision, ledger, machineValid),
                () -> ejectToWorldOwnedForFixture(body, slot, expectedSlotRevision, expectedLedgerRevision, ledger, machineValid));
    }

    private static Result ejectToHandsOrWorldCore(LivingEntity body, long expectedHandRevision,
            MachineItemSlot slot, long expectedSlotRevision, long expectedLedgerRevision, ItemLocationLedger ledger,
            BooleanSupplier context, java.util.Collection<String> supportedHands,
            java.util.function.Function<String, Result> takeHand, java.util.function.Supplier<Result> worldEject) {
        if (body == null || slot == null || ledger == null || !context.getAsBoolean()) return Result.DENIED;
        LiveHands hands = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || hands.revision() != expectedHandRevision || !hands.compatible(body)) return Result.DENIED;
        var slotBefore = slot.snapshot();
        if (slotBefore.revision() != expectedSlotRevision || slotBefore.token() == null || slotBefore.stack().isEmpty()) return Result.DENIED;
        var ordered = new java.util.ArrayList<String>();
        if (supportedHands.contains(hands.activeHand())) ordered.add(hands.activeHand());
        for (String hand : hands.handIds()) if (supportedHands.contains(hand) && !hand.equals(hands.activeHand())) ordered.add(hand);
        if (ordered.size() != hands.handIds().size()) return Result.DENIED;
        boolean allUnavailable = true;
        for (String hand : ordered) {
            if (hands.token(hand).isPresent() || hands.reservation(hand).isPresent()) continue;
            allUnavailable = false;
            Result result = takeHand.apply(hand);
            if (result != Result.DENIED) return result;
            // A failed transfer is not permission to try another destination unless nothing changed.
            if (!context.getAsBoolean()
                    || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != hands
                    || slot.snapshot().revision() != expectedSlotRevision) return result;
            return result;
        }
        if (allUnavailable && context.getAsBoolean()
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == hands
                && slot.snapshot().revision() == expectedSlotRevision)
            return worldEject.get();
        return Result.DENIED;
    }

    /** Trusted-core fixture seam only; never used by a client request route. */
    static Result takeOwnedForFixture(LivingEntity body, String hand, long expectedHandRevision,
            MachineItemSlot slot, long expectedSlotRevision, long expectedLedgerRevision,
            ItemLocationLedger ledger, BooleanSupplier machineValid) {
        BooleanSupplier context = () -> validOwnedBody(body, machineValid);
        return takeCore(body, hand, expectedHandRevision, slot, expectedSlotRevision, expectedLedgerRevision,
                ledger, context, next -> LiveHandsCommitGateway.commitTrusted(body,
                        body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()), next));
    }

    private static Result takeCore(LivingEntity body, String hand, long expectedHandRevision,
            MachineItemSlot slot, long expectedSlotRevision, long expectedLedgerRevision,
            ItemLocationLedger ledger, BooleanSupplier context, java.util.function.Predicate<LiveHands> commit) {
        if (body == null || slot == null || ledger == null || context == null || commit == null || !context.getAsBoolean()) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        var snapshot = slot.snapshot();
        if (before == null || before.revision() != expectedHandRevision || !before.compatible(body)
                || snapshot.revision() != expectedSlotRevision || snapshot.revision() >= Long.MAX_VALUE - 1
                || snapshot.token() == null || snapshot.stack().isEmpty()) return Result.DENIED;
        ItemToken token = snapshot.token(); ItemLocation source = new ItemLocation.MachineSlot(snapshot.owner(), snapshot.slotId());
        ItemLocation handLocation = new ItemLocation.BodyHand(body.getUUID(), hand);
        var ejected = slot.eject(expectedSlotRevision, expectedLedgerRevision, handLocation);
        if (ejected.isEmpty()) {
            return Result.DENIED;
        }
        var put = before.putWhole(expectedHandRevision, hand, token, ejected.orElseThrow().stack());
        if (!put.succeeded() || !context.getAsBoolean()) {
            restoreSlot(slot, ledger, ejected.orElseThrow(), handLocation);
            return Result.DENIED;
        }
        LiveHands candidate = MeleeSelectionCooldown.stageSelectedInsertion(body, before, put.state());
        try {
            if (!commit.test(candidate)) {
                if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == candidate)
                    return Result.RECOVERY_REQUIRED;
                restoreSlot(slot, ledger, ejected.orElseThrow(), handLocation);
                return Result.RECOVERY_REQUIRED;
            }
        } catch (RuntimeException failure) {
            if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == candidate)
                return Result.RECOVERY_REQUIRED;
            restoreSlot(slot, ledger, ejected.orElseThrow(), handLocation);
            return Result.RECOVERY_REQUIRED;
        }
        if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != candidate) return Result.RECOVERY_REQUIRED;
        return ledger.consume(ledger.snapshot().revision(), token, handLocation).accepted()
                ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
    }

    /** Slot-to-world release. The source remains slot-owned until a detached canonical placement stage exists. */
    public static Result ejectToWorld(HandActorAuthority.Snapshot authority, MachineItemSlot slot,
            long expectedSlotRevision, long expectedLedgerRevision, ItemLocationLedger ledger,
            BooleanSupplier machineValid) {
        if (authority == null || slot == null || ledger == null || machineValid == null) return Result.DENIED;
        var actor = authority.actor(); LivingEntity body = authority.body();
        return ejectWorldCore(body, slot, expectedSlotRevision, expectedLedgerRevision, ledger,
                () -> valid(actor, authority, body, machineValid));
    }

    static Result ejectToWorldOwnedForFixture(LivingEntity body, MachineItemSlot slot,
            long expectedSlotRevision, long expectedLedgerRevision, ItemLocationLedger ledger,
            BooleanSupplier machineValid) {
        return ejectWorldCore(body, slot, expectedSlotRevision, expectedLedgerRevision, ledger,
                () -> validOwnedBody(body, machineValid));
    }

    /** Internal lifecycle release for a server-owned non-hand entity. Not a client authorization route. */
    public static Result ejectToWorldOwned(Entity owner, MachineItemSlot slot, long expectedSlotRevision,
            long expectedLedgerRevision, ItemLocationLedger ledger, BooleanSupplier ownerValid) {
        if (owner == null || slot == null || ledger == null || ownerValid == null) return Result.DENIED;
        return ejectWorldCore(owner, slot, expectedSlotRevision, expectedLedgerRevision, ledger, () -> {
            if (!(owner.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                    || owner.isRemoved() || !owner.isAddedToLevel() || level.getEntity(owner.getUUID()) != owner
                    || !owner.getUUID().equals(slot.snapshot().owner()) || !ownerValid.getAsBoolean()) return false;
            return true;
        });
    }

    private static Result ejectWorldCore(Entity body, MachineItemSlot slot,
            long expectedSlotRevision, long expectedLedgerRevision, ItemLocationLedger ledger,
            BooleanSupplier context) {
        if (body == null || !(body.level() instanceof ServerLevel) || !context.getAsBoolean()) return Result.DENIED;
        ServerLevel level = (ServerLevel) body.level();
        var snapshot = slot.snapshot();
        if (snapshot.revision() != expectedSlotRevision || snapshot.revision() >= Long.MAX_VALUE - 1
                || snapshot.token() == null || snapshot.stack().isEmpty()) return Result.DENIED;
        ItemStack stack = snapshot.stack();
        LooseItemEntity staged = BodyHandItemTransfer.createStagedDrop(body, stack);
        if (staged == null || !ItemStack.isSameItemSameComponents(stack, staged.getItem())
                || stack.getCount() != staged.getItem().getCount() || !context.getAsBoolean()) return Result.DENIED;
        ItemLocation.WorldEntity destination = new ItemLocation.WorldEntity(
                com.juicyslew.moonstation14.ms14.disposal.item.DisposalWorldPayloadStage.worldIdentity(level), staged.getUUID());
        var payload = slot.eject(expectedSlotRevision, expectedLedgerRevision, destination);
        if (payload.isEmpty()) return Result.DENIED;
        boolean added;
        try { added = level.addFreshEntity(staged); } catch (RuntimeException failure) { added = false; }
        if (!added || level.getEntity(staged.getUUID()) != staged || staged.isRemoved() || !staged.isAddedToLevel()
                || !ItemStack.isSameItemSameComponents(stack, staged.getItem()) || stack.getCount() != staged.getItem().getCount()) {
            if (!staged.isAddedToLevel() && !staged.isRemoved() && level.getEntity(staged.getUUID()) == null) {
                boolean restored = slot.insert(slot.snapshot().revision(), ledger.snapshot().revision(), payload.orElseThrow().token(), destination,
                        payload.orElseThrow().stack());
                return restored ? Result.DENIED : Result.RECOVERY_REQUIRED;
            }
            return Result.RECOVERY_REQUIRED;
        }
        boolean claimRetired = ledger.consume(ledger.snapshot().revision(), payload.orElseThrow().token(), destination).accepted();
        boolean contextStillValid = context.getAsBoolean();
        return claimRetired && contextStillValid ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
    }

    private static boolean valid(net.minecraft.server.level.ServerPlayer actor, HandActorAuthority.Snapshot authority,
            LivingEntity body, BooleanSupplier machineValid) {
        return body != null && body.level() instanceof ServerLevel level && level.getServer().isSameThread()
                && !body.isRemoved() && body.isAddedToLevel() && level.getEntity(body.getUUID()) == body
                && CharacterControlSystem.canAct(body) && HandActorAuthority.revalidate(actor, authority)
                && machineValid.getAsBoolean();
    }
    private static boolean validOwnedBody(LivingEntity body, BooleanSupplier machineValid) {
        return body != null && body.level() instanceof ServerLevel level && level.getServer().isSameThread()
                && !body.isRemoved() && body.isAlive() && body.isAddedToLevel() && level.getEntity(body.getUUID()) == body
                && CharacterControlSystem.canAct(body) && machineValid != null && machineValid.getAsBoolean();
    }
    private static void compensateSlot(MachineItemSlot slot, long slotRevision, ItemLocationLedger ledger,
                                      ItemToken token, ItemLocation destination) {
        slot.eject(slotRevision, ledger.snapshot().revision(), destination);
    }
    private static void restoreSlot(MachineItemSlot slot, ItemLocationLedger ledger, MachineItemSlot.Payload payload,
                                    ItemLocation source) {
        slot.insert(slot.snapshot().revision(), ledger.snapshot().revision(), payload.token(), source, payload.stack());
    }
    private static void compensateSwap(MachineItemSlot slot, ItemLocationLedger ledger, ItemToken outgoing,
            ItemToken incoming, ItemLocation hand, ItemStack oldSlotStack) {
        slot.exchange(slot.snapshot().revision(), ledger.snapshot().revision(), outgoing, hand,
                oldSlotStack, hand);
    }
}

