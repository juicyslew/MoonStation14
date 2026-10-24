package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.network.BodyWorldGridAction;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.combat.MeleeSelectionCooldown;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/** Internal spawned-body seam. A caller must bind authority independently; never expose it as a client API. */
public final class BodyWorldGridTransfer {
    private BodyWorldGridTransfer() { }
    enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }

    public enum Outcome { SUCCESS, DENIED, RECOVERY_REQUIRED }

    /** Server-only entry: the network service must first pin and validate its nonce session. */
    public static Outcome applyAuthorized(ServerPlayer actor, HandActorAuthority.Snapshot authority,
                                          ItemEntity host, ItemStack reference, ItemStack copy,
                                          BodyWorldGridAction action) {
        if (actor == null || authority == null || host == null || reference == null || copy == null || action == null
                || HandActorAuthority.resolve(actor).filter(current -> sameAuthority(current, authority)).isEmpty()
                || !HandActorAuthority.revalidate(actor, authority)
                || authority.epoch() != action.epoch() || authority.body().level() != host.level()
                || !host.getUUID().equals(action.target())
                || (action.hand() != null && !authority.handIds().contains(action.hand()))) return Outcome.DENIED;
        var oldCell = action.mode() == BodyWorldGridAction.Mode.STORE ? null : new GridGeometry.Cell(action.oldX(), action.oldY());
        var oldRotation = action.mode() == BodyWorldGridAction.Mode.STORE ? null : rotation(action.oldRotation());
        var newCell = action.mode() == BodyWorldGridAction.Mode.TAKE ? null : new GridGeometry.Cell(action.newX(), action.newY());
        var newRotation = action.mode() == BodyWorldGridAction.Mode.TAKE ? null : rotation(action.newRotation());
        BooleanSupplier valid = () -> HandActorAuthority.revalidate(actor, authority)
                && authority.body().level() == host.level() && host.getUUID().equals(action.target());
        Result result = switch (action.mode()) {
            case STORE -> storeOwned(authority.body(), host, action.hand(), action.token(), action.handRevision(),
                    reference, copy, newCell, newRotation, valid);
            case TAKE -> takeOwned(authority.body(), host, action.hand(), action.token(), action.handRevision(),
                    reference, copy, oldCell, oldRotation, valid);
            case MOVE -> moveOwned(authority.body(), host, action.token(), action.handRevision(), reference, copy,
                    oldCell, oldRotation, newCell, newRotation, valid);
        };
        return Outcome.valueOf(result.name());
    }

    private static GridGeometry.Rotation rotation(int degrees) {
        for (var value : GridGeometry.Rotation.values()) if (value.degrees() == degrees) return value;
        throw new IllegalArgumentException("Invalid rotation");
    }

    private static boolean sameAuthority(HandActorAuthority.Snapshot a, HandActorAuthority.Snapshot b) {
        return a.actor() == b.actor() && a.body() == b.body() && a.epoch() == b.epoch()
                && a.source() == b.source() && a.mindId().equals(b.mindId())
                && a.harnessId().equals(b.harnessId()) && a.handIds().equals(b.handIds());
    }

    static Result storeOwned(LivingEntity body, ItemEntity host, String hand, String token, long revision,
                             ItemStack expectedReference, ItemStack expectedCopy, GridGeometry.Cell anchor,
                             GridGeometry.Rotation rotation, BooleanSupplier authorityValid) {
        return LiveHandsCommitGateway.batch(() -> transfer(body, host, hand, token, revision, expectedReference, expectedCopy,
                anchor, rotation, null, null, Mode.STORE, authorityValid));
    }

    static Result takeOwned(LivingEntity body, ItemEntity host, String hand, String token, long revision,
                            ItemStack expectedReference, ItemStack expectedCopy, GridGeometry.Cell anchor,
                            GridGeometry.Rotation rotation, BooleanSupplier authorityValid) {
        return LiveHandsCommitGateway.batch(() -> transfer(body, host, hand, token, revision, expectedReference, expectedCopy,
                anchor, rotation, null, null, Mode.TAKE, authorityValid));
    }

    static Result moveOwned(LivingEntity body, ItemEntity host, String token, long revision,
                            ItemStack expectedReference, ItemStack expectedCopy, GridGeometry.Cell oldAnchor,
                            GridGeometry.Rotation oldRotation, GridGeometry.Cell newAnchor,
                            GridGeometry.Rotation newRotation, BooleanSupplier authorityValid) {
        return LiveHandsCommitGateway.batch(() -> transfer(body, host, null, token, revision, expectedReference, expectedCopy,
                oldAnchor, oldRotation, newAnchor, newRotation, Mode.MOVE, authorityValid));
    }

    private enum Mode { STORE, TAKE, MOVE }

    private static Result transfer(LivingEntity body, ItemEntity host, String hand, String token, long revision,
                                   ItemStack reference, ItemStack copy, GridGeometry.Cell anchor,
                                   GridGeometry.Rotation rotation, GridGeometry.Cell nextAnchor,
                                   GridGeometry.Rotation nextRotation, Mode mode, BooleanSupplier valid) {
        if (body == null || host == null || token == null || anchor == null || rotation == null
                || mode == Mode.MOVE && (nextAnchor == null || nextRotation == null)
                || mode != Mode.MOVE && hand == null || reference == null || copy == null || copy == reference || valid == null
                || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || !CharacterControlSystem.canAct(body)) return Result.DENIED;
        Vec3 position = host.position();
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || before.revision() != revision || revision == Long.MAX_VALUE || !before.compatible(body)
                || !sameOwners(body, host, level) || host.getTarget() != null || host.hasPickUpDelay()
                || host.getItem() != reference || !same(copy, reference) || GridContents.read(copy).isEmpty()
                || !BodyItemTargetProbe.reachableStorageHost(level, body, host, 5))
            return Result.DENIED;
        if (!sameOwners(body, host, level) || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !unchangedWorld(host, level, reference, copy, position)) return Result.DENIED;
        var contents = GridContents.read(copy).orElseThrow();
        List<GridContents.Entry> entries = new ArrayList<>(contents.entries());
        // Reject collisions with hand and equipment tokens, including unrelated existing children.
        for (String id : before.handIds()) {
            String owned = before.token(id).orElse(null);
            if (owned != null && entries.stream().anyMatch(e -> e.token().equals(owned))) return Result.DENIED;
        }
        var equipment = LiveHands.equipmentSnapshot(body);
        if (equipment.isPresent()) for (var slot : equipment.orElseThrow().slots()) {
            String owned = slot.token().orElse(null);
            if (owned != null && entries.stream().anyMatch(e -> e.token().equals(owned))) return Result.DENIED;
        }
        LiveHands stagedBody = null, finalBody = null;
        ItemStack stagedWorld = null, finalWorld;
        GridContents.Entry child = entries.stream().filter(e -> e.token().equals(token)).findFirst().orElse(null);
        try {
            if (mode == Mode.STORE) {
                if (child != null) return Result.DENIED;
                LiveHands.WholeResult cleared = before.takeWhole(revision, hand, new ItemToken(token));
                if (!cleared.succeeded()) return Result.DENIED;
                stagedBody = cleared.state();
                entries.add(new GridContents.Entry(token, cleared.takenStack().orElseThrow(), anchor.x(), anchor.y(), rotation));
                finalWorld = GridContents.applyCopyWithGrid(copy, GridContents.of(entries)).orElse(null);
            } else {
                if (child == null || child.x() != anchor.x() || child.y() != anchor.y()
                        || child.rotation() != rotation) return Result.DENIED;
                if (mode == Mode.MOVE) {
                    if (anchor.equals(nextAnchor) && rotation == nextRotation) return Result.DENIED;
                    int childIndex = entries.indexOf(child);
                    entries.set(childIndex, new GridContents.Entry(token, child.stack(), nextAnchor.x(), nextAnchor.y(), nextRotation));
                    finalWorld = GridContents.applyCopyWithGrid(copy, GridContents.of(entries)).orElse(null);
                } else {
                    LiveHands.WholeResult filled = before.putWhole(revision, hand, new ItemToken(token), child.stack());
                    if (!filled.succeeded()) return Result.DENIED;
                    finalBody = MeleeSelectionCooldown.stageSelectedInsertion(body, before, filled.state());
                    entries.remove(child);
                    stagedWorld = GridContents.applyCopyWithGrid(copy, GridContents.of(entries)).orElse(null);
                    finalWorld = stagedWorld;
                }
            }
        } catch (IllegalArgumentException failure) { return Result.DENIED; }
        if (finalWorld == null || mode == Mode.TAKE && !BodyHandStateService.representable(finalBody, body)
                || mode == Mode.STORE && !BodyHandStateService.representable(stagedBody, body)
                || !ready(body, host, level, before, reference, copy, position, valid)) return Result.DENIED;

        if (mode == Mode.STORE) {
            try { LiveHandsCommitGateway.commitOwned(body, stagedBody); }
            catch (RuntimeException failure) {
                return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before ? Result.DENIED : Result.RECOVERY_REQUIRED;
            }
            if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != stagedBody
                    || !unchangedWorld(host, level, reference, copy, position)) return Result.RECOVERY_REQUIRED;
            if (!BodyItemTargetProbe.reachableStorageHost(level, body, host, 5))
                return compensateBody(body, host, level, before, stagedBody, hand, token,
                        entries.stream().filter(e -> e.token().equals(token)).findFirst().orElseThrow().stack(),
                        reference, copy, position);
            if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != stagedBody
                    || !unchangedWorld(host, level, reference, copy, position)) return Result.RECOVERY_REQUIRED;
            try { host.setItem(finalWorld); }
            catch (RuntimeException failure) { return compensateBody(body, host, level, before, stagedBody, hand, token,
                    entries.stream().filter(e -> e.token().equals(token)).findFirst().orElseThrow().stack(), reference, copy, position); }
            if (!sameOwners(body, host, level) || !position.equals(host.position()) || host.getTarget() != null
                    || host.hasPickUpDelay() || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != stagedBody
                    || !same(finalWorld, host.getItem())) return Result.RECOVERY_REQUIRED;
        } else {
            try { host.setItem(finalWorld); }
            catch (RuntimeException failure) {
                return unchangedWorld(host, level, reference, copy, position)
                        && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before ? Result.DENIED : Result.RECOVERY_REQUIRED;
            }
            ItemStack published = host.getItem(); // setItem may replace/copy the passed reference.
            if (!sameOwners(body, host, level) || !position.equals(host.position()) || host.getTarget() != null
                    || host.hasPickUpDelay() || !same(finalWorld, published)
                    || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before) return Result.RECOVERY_REQUIRED;
            if (mode == Mode.TAKE) {
                if (!BodyItemTargetProbe.reachableStorageHost(level, body, host, 5))
                    return compensateWorld(body, host, level, before, published, finalWorld, copy, position);
                if (!sameOwners(body, host, level) || !position.equals(host.position()) || host.getTarget() != null
                        || host.hasPickUpDelay() || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                        || host.getItem() != published || !same(finalWorld, published)) return Result.RECOVERY_REQUIRED;
                try { LiveHandsCommitGateway.commitOwned(body, finalBody); }
                catch (RuntimeException failure) { return compensateWorld(body, host, level, before, published, finalWorld, copy, position); }
                if (!sameOwners(body, host, level) || !position.equals(host.position()) || host.getTarget() != null
                        || host.hasPickUpDelay() || host.getItem() != published || !same(finalWorld, published)
                        || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != finalBody)
                    return Result.RECOVERY_REQUIRED;
            } else if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                    || host.getTarget() != null || host.hasPickUpDelay()) return Result.RECOVERY_REQUIRED;
        }
        return Result.SUCCESS;
    }

    static Result compensateBody(LivingEntity body, ItemEntity host, ServerLevel level, LiveHands before,
                                 LiveHands staged, String hand, String token, ItemStack savedSourceStack,
                                 ItemStack reference, ItemStack copy, Vec3 position) {
        if (!sameOwners(body, host, level) || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != staged
                || !staged.compatible(body) || staged.token(hand).isPresent()
                || !unchangedWorld(host, level, reference, copy, position)) return Result.RECOVERY_REQUIRED;
        LiveHands.WholeResult restored = staged.putWhole(staged.revision(), hand, new ItemToken(token), savedSourceStack);
        if (!restored.succeeded() || restored.state().revision() <= before.revision()) return Result.RECOVERY_REQUIRED;
        try { LiveHandsCommitGateway.commitOwned(body, restored.state()); }
        catch (RuntimeException failure) { return Result.RECOVERY_REQUIRED; }
        LiveHands current = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return current == restored.state() && current.revision() > before.revision() && current.compatible(body)
                && current.token(hand).filter(token::equals).isPresent()
                && current.stackCopy(hand).filter(stack -> same(stack, savedSourceStack)).isPresent()
                && unchangedWorld(host, level, reference, copy, position) ? Result.DENIED : Result.RECOVERY_REQUIRED;
    }

    private static Result compensateWorld(LivingEntity body, ItemEntity host, ServerLevel level, LiveHands before,
                                          ItemStack published, ItemStack staged, ItemStack original, Vec3 position) {
        if (!sameOwners(body, host, level) || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !position.equals(host.position()) || host.getTarget() != null || host.hasPickUpDelay()
                || host.getItem() != published || !same(staged, published))
            return Result.RECOVERY_REQUIRED;
        try { host.setItem(original.copy()); }
        catch (RuntimeException failure) { return Result.RECOVERY_REQUIRED; }
        return sameOwners(body, host, level) && position.equals(host.position()) && same(original, host.getItem())
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                ? Result.DENIED : Result.RECOVERY_REQUIRED;
    }

    private static boolean ready(LivingEntity body, ItemEntity host, ServerLevel level, LiveHands before,
                                 ItemStack reference, ItemStack copy, Vec3 position, BooleanSupplier valid) {
        if (!valid.getAsBoolean() || !CharacterControlSystem.canAct(body)
                || !sameOwners(body, host, level) || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !before.compatible(body) || !unchangedWorld(host, level, reference, copy, position)
                || !BodyItemTargetProbe.reachableStorageHost(level, body, host, 5)) return false;
        // Collision callbacks may modify either owner; do not trust the probe's earlier result alone.
        return valid.getAsBoolean() && CharacterControlSystem.canAct(body) && sameOwners(body, host, level)
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before && before.compatible(body)
                && unchangedWorld(host, level, reference, copy, position);
    }

    private static boolean unchangedWorld(ItemEntity host, ServerLevel level, ItemStack reference,
                                          ItemStack copy, Vec3 position) {
        return host.level() == level && !host.isRemoved() && host.isAddedToLevel()
                && level.getEntity(host.getUUID()) == host && position.equals(host.position())
                && host.getTarget() == null && !host.hasPickUpDelay() && host.getItem() == reference
                && same(copy, reference);
    }

    private static boolean sameOwners(LivingEntity body, ItemEntity host, ServerLevel level) {
        return body.level() == level && !body.isRemoved() && body.isAlive() && body.isAddedToLevel()
                && level.getEntity(body.getUUID()) == body && host.level() == level && !host.isRemoved()
                && host.isAddedToLevel() && level.getEntity(host.getUUID()) == host;
    }

    private static boolean same(ItemStack a, ItemStack b) { return ItemStack.matches(a, b); }
}
