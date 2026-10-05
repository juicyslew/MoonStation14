package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.function.BooleanSupplier;

/** Server-internal, detached-grid transfer between two ground bag/belt hosts. Not a client API. */
public final class BodyWorldWorldGridTransfer {
    private BodyWorldWorldGridTransfer() { }

    public enum Outcome { SUCCESS, DENIED, RECOVERY_REQUIRED }

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
        var entries = new ArrayList<>(sourceContents.entries());
        var child = entries.stream().filter(e -> e.token().equals(token)).findFirst().orElse(null);
        if (child == null || child.x() != oldX || child.y() != oldY || child.rotation() != oldRotation)
            return Outcome.DENIED;
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
        return same(stagedDestinationEvidence, finalDestination) && same(stagedSourceEvidence, finalSource)
                && ready(level, body, source, destination, finalSource, finalDestination,
                stagedSourceEvidence, stagedDestinationEvidence, revalidate)
                ? Outcome.SUCCESS : Outcome.RECOVERY_REQUIRED;
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
