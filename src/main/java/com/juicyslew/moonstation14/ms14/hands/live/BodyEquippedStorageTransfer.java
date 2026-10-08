package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.ms14.storage.grid.ItemFootprints;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.BiConsumer;

/** Transfers between a worn body-owned bag/belt and a body-owned hand. */
public final class BodyEquippedStorageTransfer {
    private BodyEquippedStorageTransfer() { }

    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }

    /** Server chooses the first fitting placement; the caller supplies no geometry. */
    public static Result smartStore(ServerPlayer actor, String slot, String hostToken,
                                    String hand, String sourceToken, long expectedRevision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return smartOwned(authority.orElseThrow().body(), slot, hostToken, hand, sourceToken,
                expectedRevision, true, () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    /** Only the current last child can be retrieved. */
    public static Result smartTake(ServerPlayer actor, String slot, String hostToken,
                                   String hand, String expectedLastChildToken, long expectedRevision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return smartOwned(authority.orElseThrow().body(), slot, hostToken, hand, expectedLastChildToken,
                expectedRevision, false, () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    // Fixture seam uses a real spawned body; public calls alone resolve and revalidate the carrier.
    static Result smartOwned(LivingEntity body, String slot, String hostToken, String hand,
                             String token, long revision, boolean storing, BooleanSupplier authorityValid) {
        if (body == null || slot == null || hostToken == null || hand == null || token == null
                || authorityValid == null || !(body.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()
                || !live(body, level) || !CharacterControlSystem.canAct(body)) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || before.revision() != revision || revision == Long.MAX_VALUE
                || !before.compatible(body) || !before.handIds().contains(hand)) return Result.DENIED;
        Optional<LiveHands.EquipmentSnapshot> equipment = LiveHands.equipmentSnapshot(body);
        if (equipment.isEmpty() || !equipment.orElseThrow().persisted()) return Result.DENIED;
        List<String> declared = equipment.orElseThrow().slots().stream().map(LiveHands.EquipmentSlot::id).toList();
        Optional<LiveHands.EquipmentSlot> target = equipment.orElseThrow().slots().stream()
                .filter(candidate -> candidate.id().equals(slot) && candidate.token().filter(hostToken::equals).isPresent())
                .findFirst();
        if (target.isEmpty()) return Result.DENIED;
        var host = target.orElseThrow().stack();
        if (host.isEmpty() || !(slot.equals("back") && host.orElseThrow().is(ModItems.BAG)
                || slot.equals("belt") && host.orElseThrow().is(ModItems.BELT))) return Result.DENIED;
        Optional<GridContents> contents = GridContents.read(host.orElseThrow());
        if (contents.isEmpty()) return Result.DENIED;
        GridGeometry.Cell anchor;
        GridGeometry.Rotation rotation = null;
        if (storing) {
            if (!before.token(hand).filter(token::equals).isPresent()) return Result.DENIED;
            var source = before.stackCopy(hand).orElseThrow();
            // Entry validation in the pure transition rejects nested/foreign/invalid children.
            var snapshot = gridSnapshot(host.orElseThrow(), contents.orElseThrow());
            if (snapshot.isEmpty()) return Result.DENIED;
            GridGeometry.Mutation<String> fit = snapshot.orElseThrow().firstFit(token, ItemFootprints.forStack(source));
            if (!fit.accepted()) return Result.DENIED;
            anchor = fit.placement().anchor();
            rotation = fit.placement().rotation();
        } else {
            if (before.token(hand).isPresent() || contents.orElseThrow().entries().isEmpty()) return Result.DENIED;
            GridContents.Entry last = contents.orElseThrow().entries().getLast();
            if (!last.token().equals(token)) return Result.DENIED;
            anchor = new GridGeometry.Cell(last.x(), last.y());
        }
        Optional<LiveHands> next = storing
                ? before.insertEquippedGrid(revision, declared, slot, hostToken, hand, token, anchor, rotation)
                : before.extractEquippedGrid(revision, declared, slot, hostToken, hand, token, anchor);
        // The snapshot service reads equipment from the currently published body, so checking a
        // staged take against it would count the same child in both old equipment and new hand.
        // Validate the published source; the pure transition validates the staged grid/hand.
        if (next.isEmpty() || !BodyHandStateService.representable(before, body)
                || !authorityValid.getAsBoolean() || !live(body, level) || !CharacterControlSystem.canAct(body)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before || !before.compatible(body)
                || !LiveHands.equipmentSnapshot(body).map(current -> current.persisted()
                        && current.slots().stream().map(LiveHands.EquipmentSlot::id).toList().equals(declared)
                        && current.slots().stream().anyMatch(candidate -> candidate.id().equals(slot)
                        && candidate.token().filter(hostToken::equals).isPresent()
                        && candidate.stack().filter(stack -> ItemStack.matches(stack, host.orElseThrow()))
                        .isPresent())).orElse(false))
            return Result.DENIED;
        try { LiveHandsCommitGateway.commitOwned(body, next.orElseThrow()); }
        catch (RuntimeException failure) {
            return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        return live(body, level) && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == next.orElseThrow()
                && next.orElseThrow().compatible(body) ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
    }

    private static Optional<GridGeometry.Snapshot<String>> gridSnapshot(ItemStack host,
                                                                         GridContents contents) {
        var mask = GridGeometry.StorageMask.rectangle(host.is(ModItems.BAG) ? 7 : 8, host.is(ModItems.BAG) ? 4 : 2);
        var snapshot = GridGeometry.Snapshot.<String>empty(mask);
        for (GridContents.Entry entry : contents.entries()) {
            var placed = snapshot.place(entry.token(), ItemFootprints.forStack(entry.stack()),
                    new GridGeometry.Cell(entry.x(), entry.y()), entry.rotation());
            if (!placed.accepted()) return Optional.empty();
            snapshot = placed.snapshot();
        }
        return Optional.of(snapshot);
    }

    public static Result store(ServerPlayer actor, String slot, String expectedContainerToken,
                               String hand, String sourceToken, long expectedRevision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return transferOwned(authority.orElseThrow().body(), slot, expectedContainerToken, hand,
                sourceToken, expectedRevision, true,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    public static Result take(ServerPlayer actor, String slot, String expectedContainerToken,
                              String hand, String childToken, long expectedRevision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return transferOwned(authority.orElseThrow().body(), slot, expectedContainerToken, hand,
                childToken, expectedRevision, false,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    public static Result storeGrid(ServerPlayer actor, String slot, String expectedContainerToken,
                                   String hand, String sourceToken, long expectedRevision,
                                   GridGeometry.Cell anchor, GridGeometry.Rotation rotation) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return transferGridOwned(authority.orElseThrow().body(), slot, expectedContainerToken, hand,
                sourceToken, expectedRevision, anchor, rotation, true,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    public static Result takeGrid(ServerPlayer actor, String slot, String expectedContainerToken,
                                  String hand, String childToken, GridGeometry.Cell anchor, long expectedRevision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return transferGridOwned(authority.orElseThrow().body(), slot, expectedContainerToken, hand,
                childToken, expectedRevision, anchor, null, false,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    public static Result moveGrid(ServerPlayer actor, String slot, String expectedContainerToken, String childToken,
                                  long expectedRevision, GridGeometry.Cell expectedOldAnchor,
                                  GridGeometry.Rotation expectedOldRotation, GridGeometry.Cell newAnchor,
                                  GridGeometry.Rotation newRotation) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return moveGridOwned(authority.orElseThrow().body(), slot, expectedContainerToken, childToken,
                expectedRevision, expectedOldAnchor, expectedOldRotation, newAnchor, newRotation,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    public static Result moveGridBetweenHosts(ServerPlayer actor, String sourceSlot, String sourceHostToken,
            String destinationSlot, String destinationHostToken, String childToken, long revision,
            GridGeometry.Cell oldAnchor, GridGeometry.Rotation oldRotation,
            GridGeometry.Cell newAnchor, GridGeometry.Rotation newRotation) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return moveGridBetweenHostsOwned(authority.orElseThrow().body(), sourceSlot, sourceHostToken,
                destinationSlot, destinationHostToken, childToken, revision, oldAnchor, oldRotation,
                newAnchor, newRotation, () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    static Result moveGridBetweenHostsOwned(LivingEntity body, String sourceSlot, String sourceHostToken,
            String destinationSlot, String destinationHostToken, String childToken, long revision,
            GridGeometry.Cell oldAnchor, GridGeometry.Rotation oldRotation,
            GridGeometry.Cell newAnchor, GridGeometry.Rotation newRotation, BooleanSupplier authorityValid) {
        return moveGridBetweenHostsOwned(body, sourceSlot, sourceHostToken, destinationSlot, destinationHostToken,
                childToken, revision, oldAnchor, oldRotation, newAnchor, newRotation, authorityValid,
                (carrier, next) -> LiveHandsCommitGateway.commitOwned(carrier, next));
    }

    // Fixture seam for a publication that throws before or after the single attachment write.
    static Result moveGridBetweenHostsOwned(LivingEntity body, String sourceSlot, String sourceHostToken,
            String destinationSlot, String destinationHostToken, String childToken, long revision,
            GridGeometry.Cell oldAnchor, GridGeometry.Rotation oldRotation,
            GridGeometry.Cell newAnchor, GridGeometry.Rotation newRotation, BooleanSupplier authorityValid,
            BiConsumer<LivingEntity, LiveHands> publish) {
        if (body == null || sourceSlot == null || destinationSlot == null || sourceHostToken == null
                || destinationHostToken == null || childToken == null || authorityValid == null || publish == null
                || !(body.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread()
                || !live(body, level) || !CharacterControlSystem.canAct(body)) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || before.revision() != revision || !before.compatible(body)) return Result.DENIED;
        var equipment = LiveHands.equipmentSnapshot(body);
        if (equipment.isEmpty() || !equipment.orElseThrow().persisted()) return Result.DENIED;
        List<String> declared = equipment.orElseThrow().slots().stream().map(LiveHands.EquipmentSlot::id).toList();
        var source = equipment.orElseThrow().slots().stream().filter(s -> s.id().equals(sourceSlot)
                && s.token().filter(sourceHostToken::equals).isPresent()).findFirst();
        var destination = equipment.orElseThrow().slots().stream().filter(s -> s.id().equals(destinationSlot)
                && s.token().filter(destinationHostToken::equals).isPresent()).findFirst();
        if (source.isEmpty() || destination.isEmpty() || source.orElseThrow().stack().isEmpty()
                || destination.orElseThrow().stack().isEmpty()) return Result.DENIED;
        ItemStack sourceStack = source.orElseThrow().stack().orElseThrow();
        ItemStack destinationStack = destination.orElseThrow().stack().orElseThrow();
        var next = before.moveEquippedGridBetweenHosts(revision, declared, sourceSlot, sourceHostToken,
                destinationSlot, destinationHostToken, childToken, oldAnchor, oldRotation, newAnchor, newRotation);
        if (next.isEmpty() || !BodyHandStateService.representable(before, body)) return Result.DENIED;
        boolean authorized;
        try { authorized = authorityValid.getAsBoolean(); }
        catch (RuntimeException failure) { return Result.DENIED; }
        if (!authorized || !live(body, level) || !CharacterControlSystem.canAct(body)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before || !before.compatible(body)
                || !LiveHands.equipmentSnapshot(body).map(current -> current.persisted()
                    && current.slots().stream().map(LiveHands.EquipmentSlot::id).toList().equals(declared)
                    && current.slots().stream().anyMatch(s -> s.id().equals(sourceSlot)
                        && s.token().filter(sourceHostToken::equals).isPresent()
                        && s.stack().filter(stack -> ItemStack.matches(stack, sourceStack)).isPresent())
                    && current.slots().stream().anyMatch(s -> s.id().equals(destinationSlot)
                        && s.token().filter(destinationHostToken::equals).isPresent()
                        && s.stack().filter(stack -> ItemStack.matches(stack, destinationStack)).isPresent())).orElse(false))
            return Result.DENIED;
        try { publish.accept(body, next.orElseThrow()); }
        catch (RuntimeException failure) {
            return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        return live(body, level) && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == next.orElseThrow()
                && next.orElseThrow().compatible(body) ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
    }

    static Result moveGridOwned(LivingEntity body, String slot, String containerToken, String childToken,
                                long revision, GridGeometry.Cell oldAnchor, GridGeometry.Rotation oldRotation,
                                GridGeometry.Cell newAnchor, GridGeometry.Rotation newRotation,
                                BooleanSupplier authorityValid) {
        if (body == null || slot == null || containerToken == null || childToken == null || authorityValid == null
                || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || !live(body, level) || !CharacterControlSystem.canAct(body)) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || before.revision() != revision || !before.compatible(body)) return Result.DENIED;
        Optional<LiveHands.EquipmentSnapshot> equipment = LiveHands.equipmentSnapshot(body);
        if (equipment.isEmpty()) return Result.DENIED;
        List<String> declared = equipment.orElseThrow().slots().stream().map(LiveHands.EquipmentSlot::id).toList();
        if (equipment.orElseThrow().slots().stream().noneMatch(candidate -> candidate.id().equals(slot)
                && candidate.token().filter(containerToken::equals).isPresent()
                && candidate.stack().filter(stack -> stack.has(com.juicyslew.moonstation14.component.ModDataComponents.GRID_CONTENTS.get())
                && GridContents.read(stack).isPresent()).isPresent())) return Result.DENIED;
        Optional<LiveHands> next = before.moveEquippedGrid(revision, declared, slot, containerToken, childToken,
                oldAnchor, oldRotation, newAnchor, newRotation);
        if (next.isEmpty() || !authorityValid.getAsBoolean() || !live(body, level) || !CharacterControlSystem.canAct(body)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before || !before.compatible(body)
                || !LiveHands.equipmentSnapshot(body).map(snapshot -> snapshot.slots().stream()
                .map(LiveHands.EquipmentSlot::id).toList().equals(declared)
                && snapshot.slots().stream().anyMatch(candidate -> candidate.id().equals(slot)
                && candidate.token().filter(containerToken::equals).isPresent()
                && candidate.stack().filter(stack -> stack.has(com.juicyslew.moonstation14.component.ModDataComponents.GRID_CONTENTS.get())
                && GridContents.read(stack).isPresent()).isPresent())).orElse(false)) return Result.DENIED;
        try { LiveHandsCommitGateway.commitOwned(body, next.orElseThrow()); }
        catch (RuntimeException failure) {
            return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        return live(body, level) && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == next.orElseThrow()
                && next.orElseThrow().compatible(body) ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
    }

    static Result transferGridOwned(LivingEntity body, String slot, String containerToken, String hand,
                                            String token, long revision, GridGeometry.Cell anchor,
                                            GridGeometry.Rotation rotation, boolean storing, BooleanSupplier authorityValid) {
        if (body == null || slot == null || containerToken == null || hand == null || token == null
                || authorityValid == null || anchor == null || storing && rotation == null
                || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || !live(body, level) || !CharacterControlSystem.canAct(body)) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || before.revision() != revision || !before.compatible(body)) return Result.DENIED;
        Optional<LiveHands.EquipmentSnapshot> equipment = LiveHands.equipmentSnapshot(body);
        if (equipment.isEmpty()) return Result.DENIED;
        List<String> declared = equipment.orElseThrow().slots().stream().map(LiveHands.EquipmentSlot::id).toList();
        if (equipment.orElseThrow().slots().stream().noneMatch(candidate -> candidate.id().equals(slot)
                && candidate.token().filter(containerToken::equals).isPresent()
                && candidate.stack().filter(stack -> GridContents.read(stack).isPresent()).isPresent())) return Result.DENIED;
        Optional<LiveHands> next = storing
                ? before.insertEquippedGrid(revision, declared, slot, containerToken, hand, token, anchor, rotation)
                : before.extractEquippedGrid(revision, declared, slot, containerToken, hand, token, anchor);
        if (next.isEmpty() || !authorityValid.getAsBoolean() || !live(body, level) || !CharacterControlSystem.canAct(body)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before || !before.compatible(body)
                || !LiveHands.equipmentSnapshot(body).map(snapshot -> snapshot.slots().stream()
                    .map(LiveHands.EquipmentSlot::id).toList().equals(declared)
                    && snapshot.slots().stream().anyMatch(candidate -> candidate.id().equals(slot)
                    && candidate.token().filter(containerToken::equals).isPresent()
                    && candidate.stack().filter(stack -> GridContents.read(stack).isPresent()).isPresent())).orElse(false))
            return Result.DENIED;
        try { LiveHandsCommitGateway.commitOwned(body, next.orElseThrow()); }
        catch (RuntimeException failure) {
            return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        return live(body, level) && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == next.orElseThrow()
                && next.orElseThrow().compatible(body) ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
    }

    // Fixture seam: only a real spawned body; tests substitute the controller validity, not ownership.
    static Result transferOwned(LivingEntity body, String slot, String containerToken, String hand,
                                String token, long revision, boolean storing, BooleanSupplier authorityValid) {
        if (body == null || slot == null || containerToken == null || hand == null || token == null
                || authorityValid == null || !(body.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread() || !live(body, level)
                || !CharacterControlSystem.canAct(body)) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || before.revision() != revision || !before.compatible(body)) return Result.DENIED;
        Optional<LiveHands.EquipmentSnapshot> equipment = LiveHands.equipmentSnapshot(body);
        if (equipment.isEmpty()) return Result.DENIED;
        List<String> declared = equipment.orElseThrow().slots().stream().map(LiveHands.EquipmentSlot::id).toList();
        if (equipment.orElseThrow().slots().stream().noneMatch(candidate -> candidate.id().equals(slot)
                && candidate.token().filter(containerToken::equals).isPresent())) return Result.DENIED;
        Optional<LiveHands> next = storing
                ? before.insertEquippedStorage(revision, declared, slot, containerToken, hand, token)
                : before.extractEquippedStorage(revision, declared, slot, containerToken, hand, token);
        if (next.isEmpty() || !authorityValid.getAsBoolean() || !live(body, level)
                || !CharacterControlSystem.canAct(body)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !before.compatible(body)
                || !LiveHands.equipmentSnapshot(body).map(snapshot -> snapshot.slots().stream()
                        .map(LiveHands.EquipmentSlot::id).toList().equals(declared)).orElse(false))
            return Result.DENIED;
        try {
            LiveHandsCommitGateway.commitOwned(body, next.orElseThrow());
        } catch (RuntimeException failure) {
            return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                    ? Result.DENIED : Result.RECOVERY_REQUIRED;
        }
        return live(body, level) && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == next.orElseThrow()
                && next.orElseThrow().compatible(body) ? Result.SUCCESS : Result.RECOVERY_REQUIRED;
    }

    private static boolean live(LivingEntity body, ServerLevel level) {
        return body.level() == level && !body.isRemoved() && body.isAlive() && body.isAddedToLevel()
                && level.getEntity(body.getUUID()) == body;
    }
}
