package com.juicyslew.moonstation14.ms14.hands.network;

import com.juicyslew.moonstation14.ms14.hands.live.LiveHandsCommitGateway;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.live.BodyItemTargetProbe;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.live.BodyEquipmentTransfer;
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
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/** Two-owner, source-first publication; compensation only touches the exact staged owner. */
final class BodyWorldCrossTransfer {
    private BodyWorldCrossTransfer() { }
    enum Outcome { SUCCESS, DENIED, RECOVERY_REQUIRED }

    static Outcome apply(ServerPlayer actor, HandActorAuthority.Snapshot authority, ItemEntity world,
            ItemStack reference, ItemStack copy, BodyWorldCrossAction action) {
        return LiveHandsCommitGateway.batch(() -> apply(actor, authority, world, reference, copy, action,
                (body, next) -> LiveHandsCommitGateway.commitOwned(body, next), ItemEntity::setItem));
    }

    static Outcome apply(ServerPlayer actor, HandActorAuthority.Snapshot authority, ItemEntity world,
            ItemStack reference, ItemStack copy, BodyWorldCrossAction action,
            BiConsumer<LivingEntity, LiveHands> publishBody, BiConsumer<ItemEntity, ItemStack> publishWorld) {
        return applyTrusted(authority.body(), world, reference, copy, action, publishBody, publishWorld,
                () -> HandActorAuthority.revalidate(actor, authority));
    }

    /** Internal test seam; production callers must use the authority-resolving overload above. */
    static Outcome applyTrusted(LivingEntity body, ItemEntity world,
            ItemStack reference, ItemStack copy, BodyWorldCrossAction action,
            BiConsumer<LivingEntity, LiveHands> publishBody, BiConsumer<ItemEntity, ItemStack> publishWorld,
            BooleanSupplier authorityValid) {
        if (!(body.level() instanceof ServerLevel level) || level.getServer() == null || !level.getServer().isSameThread()
                || !CharacterControlSystem.canAct(body)) return Outcome.DENIED;
        Vec3 position = world.position();
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || before.revision() != action.bodyRevision() || !before.compatible(body)
                || !BodyHandStateService.representable(before, body)
                || !ready(body, world, level, before, reference, copy, position, authorityValid)) return Outcome.DENIED;
        var equipment = LiveHands.equipmentSnapshot(body);
        if (equipment.isEmpty() || !equipment.orElseThrow().persisted()) return Outcome.DENIED;
        List<String> declared = equipment.orElseThrow().slots().stream().map(LiveHands.EquipmentSlot::id).toList();
        var host = action.direction() == BodyWorldCrossAction.Direction.WORLD_TO_EQUIPMENT ? null
                : new LiveHands.GridHost(action.worn(), action.bodyHostId(), action.bodyHostToken());
        var oldCell = new GridGeometry.Cell(action.oldX(), action.oldY());
        var newCell = new GridGeometry.Cell(action.newX(), action.newY());
        var oldRotation = rotation(action.oldRotation());
        var newRotation = rotation(action.newRotation());
        var grid = GridContents.read(copy);
        if (grid.isEmpty()) return Outcome.DENIED;
        var entries = new ArrayList<>(grid.orElseThrow().entries());
        ItemStack child;
        LiveHands next;
        LiveHands.ExternalEquipmentRemoval equipmentRemoval = null;
        if (action.direction() == BodyWorldCrossAction.Direction.WORLD_TO_BODY
                || action.direction() == BodyWorldCrossAction.Direction.WORLD_TO_EQUIPMENT) {
            var found = entries.stream().filter(e -> e.token().equals(action.childToken())
                    && e.x() == oldCell.x() && e.y() == oldCell.y() && e.rotation() == oldRotation).findFirst();
            if (found.isEmpty()) return Outcome.DENIED;
            child = found.orElseThrow().stack();
            next = action.direction() == BodyWorldCrossAction.Direction.WORLD_TO_EQUIPMENT
                    ? before.insertEquipment(before.revision(), declared, action.bodyHostId(), action.childToken(), child).orElse(null)
                    : before.addGridChild(before.revision(), declared, host, action.childToken(), child, newCell, newRotation).orElse(null);
            entries.remove(found.orElseThrow());
        } else if (action.direction() == BodyWorldCrossAction.Direction.EQUIPMENT_TO_WORLD) {
            if (!action.bodyHostToken().equals(action.childToken())
                    || entries.stream().anyMatch(e -> e.token().equals(action.childToken()))) return Outcome.DENIED;
            var template = BodyEquipmentTransfer.templateForTransfer(body);
            if (template == null) return Outcome.DENIED;
            equipmentRemoval = before.removeEquipmentToExternal(before.revision(), declared, template,
                    action.bodyHostId(), action.childToken()).orElse(null);
            if (equipmentRemoval == null) return Outcome.DENIED;
            child = equipmentRemoval.moved().stack();
            next = equipmentRemoval.state();
            try { entries.add(new GridContents.Entry(equipmentRemoval.moved().token(), child,
                    newCell.x(), newCell.y(), newRotation)); }
            catch (IllegalArgumentException invalid) { return Outcome.DENIED; }
        } else {
            if (entries.stream().anyMatch(e -> e.token().equals(action.childToken()))) return Outcome.DENIED;
            ItemStack bodyHost = action.worn()
                    ? equipment.orElseThrow().slots().stream().filter(s -> s.id().equals(host.id())
                        && s.token().filter(host.token()::equals).isPresent()).findFirst()
                        .flatMap(LiveHands.EquipmentSlot::stack).orElse(null)
                    : before.token(host.id()).filter(host.token()::equals).flatMap(ignored -> before.stackCopy(host.id())).orElse(null);
            var bodyGrid = GridContents.read(bodyHost);
            if (bodyGrid.isEmpty()) return Outcome.DENIED;
            var found = bodyGrid.orElseThrow().entries().stream().filter(e -> e.token().equals(action.childToken())
                    && e.x() == oldCell.x() && e.y() == oldCell.y() && e.rotation() == oldRotation).findFirst();
            if (found.isEmpty()) return Outcome.DENIED;
            child = found.orElseThrow().stack();
            next = before.removeGridChild(before.revision(), declared, host, action.childToken(), oldCell, oldRotation).orElse(null);
            try { entries.add(new GridContents.Entry(action.childToken(), child, newCell.x(), newCell.y(), newRotation)); }
            catch (IllegalArgumentException invalid) { return Outcome.DENIED; }
        }
        ItemStack nextWorld;
        try { nextWorld = GridContents.applyCopyWithGrid(copy, GridContents.of(entries)).orElse(null); }
        catch (IllegalArgumentException invalid) { return Outcome.DENIED; }
        if (next == null || nextWorld == null || !BodyHandStateService.representable(next, body)
                || !ready(body, world, level, before, reference, copy, position, authorityValid)) return Outcome.DENIED;

        BodyEquipmentTransfer.DropStage drops = null;
        if (equipmentRemoval != null) {
            drops = BodyEquipmentTransfer.stageDependentDrops(body, equipmentRemoval.drops());
            if (drops.result() == BodyEquipmentTransfer.Result.RECOVERY_REQUIRED) return Outcome.RECOVERY_REQUIRED;
            if (drops.result() == BodyEquipmentTransfer.Result.DENIED) return Outcome.DENIED;
        }

        if (action.direction() == BodyWorldCrossAction.Direction.BODY_TO_WORLD
                && com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys.isKeyItem(child)) {
            return bodyGridKeyToWorld(body, world, level, before, next, reference, copy, nextWorld, position,
                    child, action.childToken(), authorityValid, publishBody, publishWorld);
        }

        if (action.direction() == BodyWorldCrossAction.Direction.WORLD_TO_BODY
                || action.direction() == BodyWorldCrossAction.Direction.WORLD_TO_EQUIPMENT) {
            try { publishWorld.accept(world, nextWorld); }
            catch (RuntimeException failure) {
                if (unchangedWorld(world, level, reference, copy, position) && current(body) == before) return Outcome.DENIED;
                // A publisher may mutate the owner and then throw. Do not infer that the
                // write succeeded and publish the other owner; leave the staged state for
                // explicit recovery rather than risk duplicating or losing the child.
                return Outcome.RECOVERY_REQUIRED;
            }
            ItemStack staged = world.getItem();
            if (!worldStaged(world, level, staged, nextWorld, position) || current(body) != before)
                return Outcome.RECOVERY_REQUIRED;
            if (!safe(body, world, level, before, position, authorityValid))
                return restoreWorld(world, level, staged, nextWorld, copy, body, before, position, publishWorld);
            try { publishBody.accept(body, next); }
            catch (RuntimeException failure) {
                if (current(body) == before)
                    return restoreWorld(world, level, staged, nextWorld, copy, body, before, position, publishWorld);
                return Outcome.RECOVERY_REQUIRED;
            }
            return worldStaged(world, level, staged, nextWorld, position) && current(body) == next && next.compatible(body)
                    && authorityValid.getAsBoolean() ? Outcome.SUCCESS : Outcome.RECOVERY_REQUIRED;
        }
        try { publishBody.accept(body, next); }
        catch (RuntimeException failure) {
            if (current(body) == before && unchangedWorld(world, level, reference, copy, position)) return cleanup(drops, Outcome.DENIED);
            // As above, a throwing callback is not a successful publication, even when
            // it appears to have installed the staged body state.
            return Outcome.RECOVERY_REQUIRED;
        }
        if (current(body) != next || !unchangedWorld(world, level, reference, copy, position)) return Outcome.RECOVERY_REQUIRED;
        if (!safe(body, world, level, next, position, authorityValid))
            return cleanup(drops, restoreBody(body, world, level, before, next, host, declared, action, child, reference, copy, position, publishBody));
        try { publishWorld.accept(world, nextWorld); }
        catch (RuntimeException failure) {
            if (unchangedWorld(world, level, reference, copy, position))
                return cleanup(drops, restoreBody(body, world, level, before, next, host, declared, action, child, reference, copy, position, publishBody));
            return Outcome.RECOVERY_REQUIRED;
        }
        ItemStack published = world.getItem();
        Outcome result = worldStaged(world, level, published, nextWorld, position) && current(body) == next && next.compatible(body)
                && authorityValid.getAsBoolean() ? Outcome.SUCCESS : Outcome.RECOVERY_REQUIRED;
        return cleanup(drops, result);
    }

    private static Outcome bodyGridKeyToWorld(LivingEntity body, ItemEntity world, ServerLevel level,
            LiveHands before, LiveHands next, ItemStack reference, ItemStack copy, ItemStack nextWorld,
            Vec3 position, ItemStack key, String childToken, BooleanSupplier authorityValid,
            BiConsumer<LivingEntity, LiveHands> publishBody, BiConsumer<ItemEntity, ItemStack> publishWorld) {
        if (!com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys.isKey(key)
                || com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrying.containsPortableReferences(copy))
            return Outcome.DENIED;
        var portableRef = com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys.reference(key);
        LivingEntity portableBody = portableRef == null || !portableRef.dimension().equals(level.dimension().location())
                ? null : level.getEntity(portableRef.body()) instanceof LivingEntity living ? living : null;
        var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
        var prior = portableBody == null ? null : containment.registeredOwnerOf(portableBody).orElse(null);
        if (!(prior instanceof com.juicyslew.moonstation14.ms14.hands.live.PortableBodyCarrierOwner owner)
                || owner.carrier() != body || !owner.reference().equals(portableRef) || !owner.isValid()) return Outcome.DENIED;
        try { publishWorld.accept(world, nextWorld); }
        catch (RuntimeException failure) {
            return unchangedWorld(world, level, reference, copy, position) && current(body) == before
                    ? Outcome.DENIED : Outcome.RECOVERY_REQUIRED;
        }
        ItemStack stagedWorld = world.getItem();
        if (!worldStaged(world, level, stagedWorld, nextWorld, position) || current(body) != before)
            return Outcome.RECOVERY_REQUIRED;
        var worldOwner = new com.juicyslew.moonstation14.ms14.hands.live.PortableWorldStorageOwner(
                level, world.getUUID(), childToken, portableRef);
        if (!containment.transfer(portableBody, prior, worldOwner))
            return restoreWorld(world, level, stagedWorld, nextWorld, copy, body, before, position, publishWorld);
        if (!safe(body, world, level, before, position, authorityValid))
            return compensateBodyToWorld(body, world, level, before, next, portableBody, containment, prior,
                    worldOwner, stagedWorld, nextWorld, copy, position, publishBody, publishWorld);
        try { publishBody.accept(body, next); }
        catch (RuntimeException failure) {
            if (current(body) == before) return compensateBodyToWorld(body, world, level, before, next,
                    portableBody, containment, prior, worldOwner, stagedWorld, nextWorld, copy, position,
                    publishBody, publishWorld);
            return Outcome.RECOVERY_REQUIRED;
        }
        return worldStaged(world, level, stagedWorld, nextWorld, position) && current(body) == next
                && next.compatible(body) && containment.registeredOwnerOf(portableBody).orElse(null) == worldOwner
                && authorityValid.getAsBoolean() ? Outcome.SUCCESS : Outcome.RECOVERY_REQUIRED;
    }

    private static Outcome compensateBodyToWorld(LivingEntity body, ItemEntity world, ServerLevel level,
            LiveHands before, LiveHands stagedBody, LivingEntity portableBody,
            com.juicyslew.moonstation14.ms14.containment.BodyContainmentService containment,
            com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner previous,
            com.juicyslew.moonstation14.ms14.hands.live.PortableWorldStorageOwner stagedOwner,
            ItemStack stagedWorld, ItemStack expectedWorld, ItemStack originalWorld, Vec3 position,
            BiConsumer<LivingEntity, LiveHands> publishBody, BiConsumer<ItemEntity, ItemStack> publishWorld) {
        if (current(body) != before && current(body) != stagedBody
                || !worldStaged(world, level, stagedWorld, expectedWorld, position)) return Outcome.RECOVERY_REQUIRED;
        if (current(body) == stagedBody) {
            var restored = stagedBody.restoreContents(stagedBody.revision(), before).orElse(null);
            if (restored == null) return Outcome.RECOVERY_REQUIRED;
            try { publishBody.accept(body, restored); }
            catch (RuntimeException failure) { return Outcome.RECOVERY_REQUIRED; }
            if (current(body) != restored) return Outcome.RECOVERY_REQUIRED;
        }
        if (containment.registeredOwnerOf(portableBody).orElse(null) == stagedOwner
                && !containment.transfer(portableBody, stagedOwner, previous)) return Outcome.RECOVERY_REQUIRED;
        try { publishWorld.accept(world, originalWorld.copy()); }
        catch (RuntimeException failure) { return Outcome.RECOVERY_REQUIRED; }
        return current(body) != before && current(body).revision() > before.revision()
                && ItemStack.matches(originalWorld, world.getItem())
                && containment.registeredOwnerOf(portableBody).orElse(null) == previous
                ? Outcome.DENIED : Outcome.RECOVERY_REQUIRED;
    }

    private static Outcome restoreWorld(ItemEntity world, ServerLevel level, ItemStack staged, ItemStack expected,
            ItemStack original, LivingEntity body, LiveHands before, Vec3 position,
            BiConsumer<ItemEntity, ItemStack> publish) {
        if (!worldStaged(world, level, staged, expected, position) || current(body) != before) return Outcome.RECOVERY_REQUIRED;
        try { publish.accept(world, original.copy()); }
        catch (RuntimeException failure) { return Outcome.RECOVERY_REQUIRED; }
        return current(body) == before && ItemStack.matches(original, world.getItem())
                && world.level() == level && level.getEntity(world.getUUID()) == world ? Outcome.DENIED : Outcome.RECOVERY_REQUIRED;
    }

    private static Outcome restoreBody(LivingEntity body, ItemEntity world, ServerLevel level, LiveHands before,
            LiveHands staged, LiveHands.GridHost host, List<String> declared, BodyWorldCrossAction action, ItemStack child,
            ItemStack reference, ItemStack copy, Vec3 position, BiConsumer<LivingEntity, LiveHands> publish) {
        if (current(body) != staged || !staged.compatible(body) || !unchangedWorld(world, level, reference, copy, position))
            return Outcome.RECOVERY_REQUIRED;
        var restored = action.direction() == BodyWorldCrossAction.Direction.EQUIPMENT_TO_WORLD
                ? staged.restoreContents(staged.revision(), before)
                : staged.addGridChild(staged.revision(), declared, host, action.childToken(), child,
                    new GridGeometry.Cell(action.oldX(), action.oldY()), rotation(action.oldRotation()));
        if (restored.isEmpty() || restored.orElseThrow().revision() <= before.revision()) return Outcome.RECOVERY_REQUIRED;
        try { publish.accept(body, restored.orElseThrow()); }
        catch (RuntimeException failure) { return Outcome.RECOVERY_REQUIRED; }
        return current(body) == restored.orElseThrow() && restored.orElseThrow().compatible(body)
                && unchangedWorld(world, level, reference, copy, position) ? Outcome.DENIED : Outcome.RECOVERY_REQUIRED;
    }

    private static Outcome cleanup(BodyEquipmentTransfer.DropStage drops, Outcome outcome) {
        if (drops == null || outcome == Outcome.SUCCESS || outcome == Outcome.RECOVERY_REQUIRED) return outcome;
        return drops.cleanup() ? outcome : Outcome.RECOVERY_REQUIRED;
    }

    private static LiveHands current(LivingEntity body) { return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()); }
    private static boolean owners(LivingEntity body, ItemEntity world, ServerLevel level) {
        return body.level() == level && body.isAlive() && body.isAddedToLevel() && !body.isRemoved()
                && level.getEntity(body.getUUID()) == body && world.level() == level && world.isAddedToLevel()
                && !world.isRemoved() && level.getEntity(world.getUUID()) == world;
    }
    private static boolean unchangedWorld(ItemEntity world, ServerLevel level, ItemStack reference, ItemStack copy, Vec3 position) {
        return world.level() == level && !world.isRemoved() && world.isAddedToLevel()
                && level.getEntity(world.getUUID()) == world && position.equals(world.position())
                && world.getTarget() == null && !world.hasPickUpDelay() && world.getItem() == reference
                && ItemStack.matches(copy, reference);
    }
    private static boolean worldStaged(ItemEntity world, ServerLevel level, ItemStack published, ItemStack expected, Vec3 position) {
        return world.level() == level && !world.isRemoved() && world.isAddedToLevel()
                && level.getEntity(world.getUUID()) == world && position.equals(world.position())
                && world.getTarget() == null && !world.hasPickUpDelay() && world.getItem() == published
                && ItemStack.matches(expected, published);
    }
    private static boolean safe(LivingEntity body,
            ItemEntity world, ServerLevel level, LiveHands expected, Vec3 position, BooleanSupplier authorityValid) {
        try {
            return authorityValid.getAsBoolean() && CharacterControlSystem.canAct(body)
                    && owners(body, world, level) && current(body) == expected && expected.compatible(body)
                    && position.equals(world.position()) && world.getTarget() == null && !world.hasPickUpDelay()
                    && BodyItemTargetProbe.reachableStorageHost(level, body, world, BodyItemTargetProbe.MAX_REACH)
                    && authorityValid.getAsBoolean() && current(body) == expected;
        } catch (RuntimeException failure) { return false; }
    }

    private static boolean ready(LivingEntity body,
            ItemEntity world, ServerLevel level, LiveHands expected, ItemStack reference, ItemStack copy, Vec3 position,
            BooleanSupplier authorityValid) {
        return unchangedWorld(world, level, reference, copy, position) && safe(body, world, level, expected, position, authorityValid)
                && unchangedWorld(world, level, reference, copy, position);
    }
    private static GridGeometry.Rotation rotation(int degrees) {
        for (var value : GridGeometry.Rotation.values()) if (value.degrees() == degrees) return value;
        throw new IllegalArgumentException("Invalid rotation");
    }
}

