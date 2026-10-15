package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys;
import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Server-internal, detached-grid transfer between two ground bag/belt hosts. Not a client API. */
public final class BodyWorldWorldGridTransfer {
    private BodyWorldWorldGridTransfer() { }

    public enum Outcome { SUCCESS, DENIED, RECOVERY_REQUIRED }

    static PreparedPortableKeyRelease preparePortableKeyRelease(ItemEntity root, String token,
            com.juicyslew.moonstation14.ms14.hands.PortableBodyReference reference, ItemStack expectedKey) {
        if (root == null || token == null || reference == null || expectedKey == null
                || !(root.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || level.getEntity(root.getUUID()) != root
                || !PortableBodyCarrying.worldRootReferencesOwned(level, root.getUUID(), root.getItem())) return null;
        var contents = GridContents.read(root.getItem()).orElse(null);
        var child = contents == null ? null : contents.entries().stream().filter(entry -> entry.token().equals(token)
                && reference.equals(PortableBodyKeys.reference(entry.stack())) && samePortableKey(expectedKey, entry.stack()))
                .findFirst().orElse(null);
        ItemStack rootReference = root.getItem();
        ItemStack rootEvidence = rootReference.copy();
        ItemStack stagedRemoval = child == null ? null : BodyWorldGridTransfer.prepareGridRemoval(rootReference, child);
        if (child == null || stagedRemoval == null) return null;
        return new PreparedPortableKeyRelease(root, token, reference, child, rootReference, rootEvidence, stagedRemoval);
    }

    static final class PreparedPortableKeyRelease {
        private final ItemEntity root;
        private final String token;
        private final com.juicyslew.moonstation14.ms14.hands.PortableBodyReference reference;
        private final GridContents.Entry originalChild;
        private final ItemStack preparedRootReference;
        private final ItemStack preparedRootEvidence;
        private final ItemStack preparedRemoval;
        private String diagnostic = "not-run";
        private PreparedPortableKeyRelease(ItemEntity root, String token,
                com.juicyslew.moonstation14.ms14.hands.PortableBodyReference reference, GridContents.Entry originalChild,
                ItemStack preparedRootReference, ItemStack preparedRootEvidence, ItemStack preparedRemoval) {
            this.root = root; this.token = token; this.reference = reference; this.originalChild = originalChild;
            this.preparedRootReference = preparedRootReference; this.preparedRootEvidence = preparedRootEvidence.copy();
            this.preparedRemoval = preparedRemoval.copy();
        }
        boolean remove() {
            if (!rootLive()) { diagnostic = "root identity/entity not live"; return false; }
            ItemStack before = root.getItem();
            if (before != preparedRootReference || !ItemStack.matches(preparedRootEvidence, before)) {
                diagnostic = "root changed after prepare; sameReference=" + (before == preparedRootReference)
                        + ",evidence=" + ItemStack.matches(preparedRootEvidence, before);
                return false;
            }
            boolean published = BodyWorldGridTransfer.publishGridRoot(root, preparedRootReference,
                    preparedRootEvidence, preparedRemoval);
            if (!published) { diagnostic = "prepared root publication denied; live=" + rootLive()
                    + ",sameBefore=" + (root.getItem() == preparedRootReference)
                    + ",evidence=" + ItemStack.matches(preparedRootEvidence, root.getItem()); return false; }
            boolean absent = !PortableBodyCarrying.exactWorldEntry(root.getItem(), token, reference);
            diagnostic = "published=" + published + ",entryAbsent=" + absent + ",entries="
                    + GridContents.read(root.getItem()).map(grid -> grid.entries().stream().map(e -> e.token()).toList()).orElse(java.util.List.of());
            return absent;
        }
        String diagnostic() { return diagnostic; }
        boolean restore() {
            if (!rootLive() || PortableBodyCarrying.exactWorldEntry(root.getItem(), token, reference)) return false;
            ItemStack before = root.getItem();
            var contents = GridContents.read(before).orElse(null);
            if (contents == null || contents.entries().stream().anyMatch(entry -> entry.token().equals(token))) return false;
            var entries = new ArrayList<>(contents.entries()); entries.add(originalChild);
            ItemStack staged = GridContents.applyCopyWithGrid(before, GridContents.of(entries)).orElse(null);
            return staged != null && BodyWorldGridTransfer.publishGridRoot(root, before, before.copy(), staged)
                    && PortableBodyCarrying.exactWorldEntry(root.getItem(), token, reference);
        }
        private boolean rootLive() {
            return root != null && root.level() instanceof ServerLevel level && level.getServer() != null
                    && level.getServer().isSameThread() && level.getEntity(root.getUUID()) == root && !root.isRemoved();
        }
    }

    private static boolean samePortableKey(ItemStack left, ItemStack right) {
        return left != null && right != null && !left.isEmpty() && !right.isEmpty()
                && left.getCount() == 1 && right.getCount() == 1
                && left.is(ModItems.PORTABLE_BODY_KEY) && right.is(ModItems.PORTABLE_BODY_KEY)
                && java.util.Objects.equals(PortableBodyKeys.reference(left), PortableBodyKeys.reference(right));
    }

    /**
     * Moves one child directly between distinct ground hosts. The callback revalidates actor authority
     * and reach; host ownership is checked against the expected staged stacks here.
     */
    public static Outcome transfer(LivingEntity body, ItemEntity source, ItemEntity destination, String token,
                                   int oldX, int oldY, GridGeometry.Rotation oldRotation,
                                   int newX, int newY, GridGeometry.Rotation newRotation,
                                   BooleanSupplier revalidate) {
        if (body == null || source == null || destination == null || token == null || token.isBlank() || oldRotation == null
                || newRotation == null || revalidate == null || source == destination
                || source.getUUID().equals(destination.getUUID()) || source.level() != destination.level()
                || !(source.level() instanceof ServerLevel level) || body.level() != level
                || level.getServer() == null || !level.getServer().isSameThread()) return Outcome.DENIED;
        ItemStack sourceRef = source.getItem(), destinationRef = destination.getItem();
        ItemStack sourceOriginal = sourceRef.copy(), destinationOriginal = destinationRef.copy();
        if (!live(level, body, source) || !live(level, body, destination)
                || source.hasPickUpDelay() || destination.hasPickUpDelay()
                || source.getTarget() != null || destination.getTarget() != null
                || !validate(revalidate) || !unchanged(source, sourceRef, sourceOriginal)
                || !unchanged(destination, destinationRef, destinationOriginal)) return Outcome.DENIED;
        var sourceContents = GridContents.read(sourceRef).orElse(null);
        var destinationContents = GridContents.read(destinationRef).orElse(null);
        if (sourceContents == null || destinationContents == null) return Outcome.DENIED;
        if (!PortableBodyCarrying.worldRootReferencesOwned(level, source.getUUID(), sourceRef)
                || !PortableBodyCarrying.worldRootReferencesOwned(level, destination.getUUID(), destinationRef))
            return Outcome.DENIED;
        var entries = new ArrayList<>(sourceContents.entries());
        var child = entries.stream().filter(e -> e.token().equals(token)).findFirst().orElse(null);
        if (child == null || child.x() != oldX || child.y() != oldY || child.rotation() != oldRotation)
            return Outcome.DENIED;
        var portableRef = PortableBodyKeys.isKeyItem(child.stack()) ? PortableBodyKeys.reference(child.stack()) : null;
        PortableWorldStorageOwner previousOwner = null;
        LivingEntity portableBody = null;
        if (portableRef != null) {
            if (targetEntriesContainsBody(destinationContents.entries(), portableRef.body())) return Outcome.DENIED;
            portableBody = level.getEntity(portableRef.body()) instanceof LivingEntity living ? living : null;
            var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
            var current = portableBody == null ? null : containment.registeredOwnerOf(portableBody).orElse(null);
            if (!(current instanceof PortableWorldStorageOwner world && world.rootId().equals(source.getUUID())
                    && world.childToken().equals(token) && world.reference().equals(portableRef) && world.isValid()))
                return Outcome.DENIED;
            previousOwner = world;
        }
        var targetEntries = new ArrayList<>(destinationContents.entries());
        var ids = new HashSet<String>();
        for (var entry : entries) if (!ids.add(entry.token())) return Outcome.DENIED;
        for (var entry : targetEntries) if (!ids.add(entry.token())) return Outcome.DENIED;
        entries.remove(child);
        try {
            targetEntries.add(new GridContents.Entry(token, child.stack(), newX, newY, newRotation));
        } catch (IllegalArgumentException invalid) { return Outcome.DENIED; }
        ItemStack stagedSource, stagedDestination;
        try {
            stagedSource = GridContents.applyCopyWithGrid(sourceRef, GridContents.of(entries)).orElse(null);
            stagedDestination = GridContents.applyCopyWithGrid(destinationRef, GridContents.of(targetEntries)).orElse(null);
        } catch (IllegalArgumentException invalid) { return Outcome.DENIED; }
        ItemStack stagedSourceEvidence = stagedSource == null ? null : stagedSource.copy();
        ItemStack stagedDestinationEvidence = stagedDestination == null ? null : stagedDestination.copy();
        if (stagedSource == null || stagedDestination == null || !ready(level, body, source, destination,
                sourceRef, destinationRef, sourceOriginal, destinationOriginal, revalidate)) return Outcome.DENIED;
        try {
            source.setItem(stagedSource);
        } catch (RuntimeException failure) {
            return source.getItem() == sourceRef && destination.getItem() == destinationRef
                    ? Outcome.DENIED : Outcome.RECOVERY_REQUIRED;
        }
        ItemStack publishedSource = source.getItem();
        ItemStack publishedSourceEvidence = publishedSource.copy();
        if (!same(stagedSourceEvidence, publishedSource)) return Outcome.RECOVERY_REQUIRED;
        if (!ready(level, body, source, destination, publishedSource, destinationRef,
                publishedSourceEvidence, destinationOriginal, revalidate)) {
            if (!unchanged(source, publishedSource, publishedSourceEvidence) || !unchanged(destination, destinationRef, destinationOriginal))
                return Outcome.RECOVERY_REQUIRED;
            try { source.setItem(sourceRef.copy()); }
            catch (RuntimeException failure) { return Outcome.RECOVERY_REQUIRED; }
            return same(sourceOriginal, source.getItem()) && destination.getItem() == destinationRef
                    ? Outcome.DENIED : Outcome.RECOVERY_REQUIRED;
        }
        try {
            destination.setItem(stagedDestination);
        } catch (RuntimeException failure) {
            if (destination.getItem() != destinationRef || source.getItem() != publishedSource
                    || !unchanged(source, publishedSource, publishedSourceEvidence)
                    || !unchanged(destination, destinationRef, destinationOriginal) || !live(level, body, source)
                    || !live(level, body, destination)) return Outcome.RECOVERY_REQUIRED;
            try { source.setItem(sourceRef.copy()); }
            catch (RuntimeException compensationFailure) { return Outcome.RECOVERY_REQUIRED; }
            return same(sourceOriginal, source.getItem()) && destination.getItem() == destinationRef
                    ? Outcome.DENIED : Outcome.RECOVERY_REQUIRED;
        }
        ItemStack finalSource = source.getItem(), finalDestination = destination.getItem();
        PortableWorldStorageOwner nextOwner = null;
        var containment = com.juicyslew.moonstation14.ms14.containment.BodyContainmentService.forLevel(level);
        if (previousOwner != null) {
            nextOwner = new PortableWorldStorageOwner(level, destination.getUUID(), token, portableRef);
            if (!containment.transfer(portableBody, previousOwner, nextOwner))
                return compensatePortableMove(level, source, destination, sourceOriginal, destinationOriginal,
                        finalSource, finalDestination, portableBody, containment, previousOwner, null);
        }
        if (same(stagedDestinationEvidence, finalDestination) && same(stagedSourceEvidence, finalSource)
                && ready(level, body, source, destination, finalSource, finalDestination,
                stagedSourceEvidence, stagedDestinationEvidence, revalidate)) return Outcome.SUCCESS;
        if (previousOwner != null) return compensatePortableMove(level, source, destination, sourceOriginal,
                destinationOriginal, finalSource, finalDestination, portableBody, containment, previousOwner, nextOwner);
        return Outcome.RECOVERY_REQUIRED;
    }

    private static boolean targetEntriesContainsBody(List<GridContents.Entry> entries, java.util.UUID body) {
        return entries.stream().filter(entry -> PortableBodyKeys.isKeyItem(entry.stack()))
                .map(entry -> PortableBodyKeys.reference(entry.stack()))
                .anyMatch(reference -> reference != null && reference.body().equals(body));
    }

    private static Outcome compensatePortableMove(ServerLevel level, ItemEntity source, ItemEntity destination,
            ItemStack sourceOriginal, ItemStack destinationOriginal, ItemStack sourcePublished, ItemStack destinationPublished,
            LivingEntity body, com.juicyslew.moonstation14.ms14.containment.BodyContainmentService containment,
            PortableWorldStorageOwner previous, PortableWorldStorageOwner next) {
        if (!live(level, body, source) || !live(level, body, destination)
                || source.getItem() != sourcePublished || destination.getItem() != destinationPublished) return Outcome.RECOVERY_REQUIRED;
        try { source.setItem(sourceOriginal.copy()); }
        catch (RuntimeException failure) { return Outcome.RECOVERY_REQUIRED; }
        if (next != null && containment.registeredOwnerOf(body).orElse(null) == next
                && !containment.transfer(body, next, previous)) return Outcome.RECOVERY_REQUIRED;
        try { destination.setItem(destinationOriginal.copy()); }
        catch (RuntimeException failure) { return Outcome.RECOVERY_REQUIRED; }
        return same(sourceOriginal, source.getItem()) && same(destinationOriginal, destination.getItem())
                && containment.registeredOwnerOf(body).orElse(null) == previous ? Outcome.DENIED : Outcome.RECOVERY_REQUIRED;
    }

    private static boolean ready(ServerLevel level, LivingEntity body, ItemEntity source, ItemEntity destination,
                                 ItemStack sourceStack, ItemStack destinationStack,
                                 ItemStack sourceEvidence, ItemStack destinationEvidence, BooleanSupplier validate) {
        boolean before = live(level, body, source) && live(level, body, destination)
                && source.getTarget() == null && destination.getTarget() == null
                && !source.hasPickUpDelay() && !destination.hasPickUpDelay()
                && source.getItem() == sourceStack && destination.getItem() == destinationStack
                && same(sourceEvidence, sourceStack) && same(destinationEvidence, destinationStack);
        if (!before) return false;
        boolean accepted = validate(validate);
        return accepted && live(level, body, source) && live(level, body, destination)
                && source.getItem() == sourceStack && destination.getItem() == destinationStack
                && same(sourceEvidence, sourceStack) && same(destinationEvidence, destinationStack);
    }

    private static boolean validate(BooleanSupplier validate) {
        try { return validate.getAsBoolean(); }
        catch (RuntimeException failure) { return false; }
    }

    private static boolean unchanged(ItemEntity host, ItemStack ref, ItemStack evidence) {
        return host.getItem() == ref && same(evidence, ref);
    }

    private static boolean live(ServerLevel level, LivingEntity body, ItemEntity host) {
        return body.level() == level && !body.isRemoved() && body.isAlive() && body.isAddedToLevel()
                && level.getEntity(body.getUUID()) == body && host.level() == level && !host.isRemoved()
                && host.isAddedToLevel() && level.getEntity(host.getUUID()) == host;
    }

    private static boolean same(ItemStack a, ItemStack b) { return ItemStack.matches(a, b); }
}
