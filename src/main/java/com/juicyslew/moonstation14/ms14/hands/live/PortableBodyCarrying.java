package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.ms14.character.components.PortableBodyComponent;
import com.juicyslew.moonstation14.ms14.containment.BodyContainmentService;
import com.juicyslew.moonstation14.ms14.containment.ContainedBodyOwner;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys;
import com.juicyslew.moonstation14.ms14.hands.PortableBodyReference;
import com.juicyslew.moonstation14.ms14.loose_objects.LooseObjectProfile;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Hand-only capability and containment reconciliation; never stores a second body-owner index. */
public final class PortableBodyCarrying {
    private PortableBodyCarrying() { }

    private record Entry(String hand, String handToken, PortableBodyReference reference,
                         ItemStack keyStack, ItemStack rootStack, String rootToken,
                         com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Cell anchor,
                         com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Rotation rotation,
                         boolean directKey) { }

    public static boolean hasKey(LiveHands state) {
        return hasKey(state, null);
    }

    public static boolean hasKey(LiveHands state, LivingEntity body) {
        if (state == null) return false;
        return state.handIds().stream().map(hand -> state.stackCopy(hand).orElse(null))
                .anyMatch(PortableBodyKeys::isKeyItem)
                || state.handIds().stream().map(hand -> state.stackCopy(hand).orElse(null))
                .filter(PortableBodyCarrying::isGridHost).anyMatch(PortableBodyCarrying::gridContainsKey)
                || body != null && LiveHands.equipmentSnapshot(body, state).stream().flatMap(s -> s.slots().stream())
                .map(slot -> slot.stack().orElse(null)).filter(PortableBodyCarrying::isGridHost)
                .anyMatch(PortableBodyCarrying::gridContainsKey);
    }

    /** Validates every adapter reference in a prospective hand value against the exact live body. */
    public static boolean validateProspective(LivingEntity carrier, LiveHands value) {
        return carrier != null && value != null && value.compatible(carrier) && entries(carrier, value) != null;
    }

    public static boolean eligibleLivingTarget(LivingEntity carrier, LivingEntity target) {
        if (carrier == null || target == null || carrier == target
                || !(carrier.level() instanceof ServerLevel level) || target.level() != level
                || !BodyContainmentService.isValidBody(target, level)) return false;
        var capability = resolvedPortableBodyCapability(target).orElse(null);
        if (capability == null || BodyContainmentService.forLevel(level).registeredOwnerOf(target).isPresent()) return false;
        try { LooseObjectProfile.resolve(capability.profileId()); return true; }
        catch (RuntimeException missingProfile) { return false; }
    }

    /** Shared loaded-entity, persisted-dead-body, and resolved-capability gate for target nominations. */
    public static java.util.Optional<PortableBodyComponent> resolvedPortableBodyCapability(LivingEntity target) {
        if (target == null || target.isRemoved() || !target.isAddedToLevel()
                || !(target.isAlive() || com.juicyslew.moonstation14.ms14.mobs.MobStateSystem
                .isRetainedNonAliveBody(target))) return java.util.Optional.empty();
        return com.juicyslew.moonstation14.ms14.hands.HandCapability.resolveCharacter(target)
                .flatMap(character -> character.component(PortableBodyComponent.class));
    }

    public static ItemStack createKey(LivingEntity carrier, LivingEntity target, UUID token) {
        if (token == null || !eligibleLivingTarget(carrier, target)
                || !(carrier.level() instanceof ServerLevel level)) return ItemStack.EMPTY;
        ItemStack key = new ItemStack(com.juicyslew.moonstation14.item.ModItems.PORTABLE_BODY_KEY.get());
        key.set(ModDataComponents.PORTABLE_BODY_REFERENCE.get(), new PortableBodyReference(
                level.dimension().location(), target.getUUID(), token));
        var appearance = com.juicyslew.moonstation14.ms14.hands.PortableBodyAppearanceResolver.capture(target);
        if (appearance == null) return ItemStack.EMPTY;
        key.set(ModDataComponents.PORTABLE_BODY_APPEARANCE.get(), appearance);
        key.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                com.juicyslew.moonstation14.ms14.character.CharacterPublicName.component(target));
        return key;
    }

    /** Reconciles containment after the normal hand gateway has published its final attachment. */
    public static boolean reconcile(LivingEntity carrier, LiveHands before, LiveHands after) {
        if (!(carrier != null && carrier.level() instanceof ServerLevel level) || after == null
                || carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != after
                || before != null && (after.revision() < before.revision() || !before.compatible(carrier))
                || !validateProspective(carrier, after)) return false;
        Map<UUID, Entry> oldEntries = before == null ? Map.of() : entries(carrier, before), newEntries = entries(carrier, after);
        if (oldEntries == null || newEntries == null) return false;
        BodyContainmentService containment = BodyContainmentService.forLevel(level);
        Map<UUID, ContainedBodyOwner> existing = new LinkedHashMap<>();
        for (UUID id : oldEntries.keySet()) {
            LivingEntity body = level.getEntity(id) instanceof LivingEntity living ? living : null;
            if (body == null) return false;
            ContainedBodyOwner owner = containment.registeredOwnerOf(body).orElse(null);
            if (!(owner instanceof PortableBodyCarrierOwner portable && portable.carrier() == carrier)
                    && !(owner instanceof PortableWorldStorageOwner world && world.reference().body().equals(id))) return false;
            existing.put(id, owner);
        }

        // Complete all additions/transfers before releasing any old body, so denial cannot strand one.
        Map<UUID, ContainedBodyOwner> changed = new LinkedHashMap<>();
        for (var pair : newEntries.entrySet()) {
            UUID id = pair.getKey(); Entry entry = pair.getValue();
            LivingEntity body = level.getEntity(id) instanceof LivingEntity living ? living : null;
            if (body == null) { rollbackChanges(containment, level, existing, changed); return false; }
            ContainedBodyOwner current = containment.registeredOwnerOf(body).orElse(null);
            PortableBodyCarrierOwner next = new PortableBodyCarrierOwner(carrier, entry.reference(), entry.handToken());
            if (existing.containsKey(id)) {
                ContainedBodyOwner prior = existing.get(id);
                if (current == prior && prior instanceof PortableBodyCarrierOwner carried
                        && carried.carrier() == carrier && carried.reference().equals(next.reference())
                        && carried.handToken().equals(next.handToken())) continue;
                if (current != prior || !containment.transfer(body, prior, next)) {
                    rollbackChanges(containment, level, existing, changed); return false;
                }
                changed.put(id, next);
            } else {
                if (current instanceof PortableWorldStorageOwner world && world.reference().equals(entry.reference())) {
                    existing.put(id, current);
                    if (!containment.transfer(body, current, next)) {
                        rollbackChanges(containment, level, existing, changed); return false;
                    }
                } else if (current != null || !containment.bind(body, next)) {
                    rollbackChanges(containment, level, existing, changed); return false;
                }
                changed.put(id, next);
            }
        }
        Map<UUID, ContainedBodyOwner> removed = new LinkedHashMap<>();
        for (var pair : existing.entrySet()) {
            if (newEntries.containsKey(pair.getKey())) continue;
            LivingEntity body = level.getEntity(pair.getKey()) instanceof LivingEntity living ? living : null;
            ContainedBodyOwner current = body == null ? null : containment.registeredOwnerOf(body).orElse(null);
            if (current instanceof PortableWorldStorageOwner world && world.reference().body().equals(pair.getKey())) {
                removed.put(pair.getKey(), current);
                continue;
            }
            if (body == null || !containment.unbind(body, pair.getValue())) {
                for (var restore : removed.entrySet()) {
                    LivingEntity restoreBody = level.getEntity(restore.getKey()) instanceof LivingEntity living ? living : null;
                    if (restoreBody != null) containment.bind(restoreBody, restore.getValue());
                }
                rollbackChanges(containment, level, existing, changed); return false;
            }
            removed.put(pair.getKey(), pair.getValue());
        }
        return true;
    }

    private static void rollbackChanges(BodyContainmentService containment, ServerLevel level,
                                        Map<UUID, ContainedBodyOwner> existing,
                                        Map<UUID, ContainedBodyOwner> changed) {
        for (var pair : changed.entrySet()) {
            LivingEntity body = level.getEntity(pair.getKey()) instanceof LivingEntity living ? living : null;
            if (body == null) continue;
            ContainedBodyOwner old = existing.get(pair.getKey());
            if (old == null) containment.unbind(body, pair.getValue());
            else containment.transfer(body, pair.getValue(), old);
        }
    }

    private static Map<UUID, Entry> entries(LivingEntity carrier, LiveHands value) {
        if (!(carrier.level() instanceof ServerLevel level)) return null;
        Map<UUID, Entry> found = new LinkedHashMap<>();
        for (String hand : value.handIds()) {
            ItemStack stack = value.stackCopy(hand).orElse(null);
            if (stack == null) continue;
            if (PortableBodyKeys.isKeyItem(stack)
                    && !addReference(carrier, level, found, stack, value.token(hand).orElse(null), stack,
                    value.token(hand).orElse(null), hand, null, null, true)) return null;
            if (!isGridHost(stack)) continue;
            if (GridContents.read(stack).isEmpty()) {
                if (hasRawPortableKey(stack)) return null;
                continue;
            }
            for (var child : directGridKeys(stack))
                if (!addReference(carrier, level, found, child.stack(), child.token(), stack,
                        value.token(hand).orElse(null), hand, new com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Cell(child.x(), child.y()),
                        child.rotation(), false)) return null;
        }
        var equipment = LiveHands.equipmentSnapshot(carrier, value).orElse(null);
        if (equipment != null) for (var slot : equipment.slots()) {
            ItemStack host = slot.stack().orElse(null);
            if (!isGridHost(host)) continue;
            if (GridContents.read(host).isEmpty()) {
                if (hasRawPortableKey(host)) return null;
                continue;
            }
            for (var child : directGridKeys(host))
                if (!addReference(carrier, level, found, child.stack(), child.token(), host,
                        slot.token().orElse(null), slot.id(), new com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Cell(child.x(), child.y()),
                        child.rotation(), false)) return null;
        }
        return found;
    }

    private static boolean addReference(LivingEntity carrier, ServerLevel level, Map<UUID, Entry> found,
                                        ItemStack stack, String locationToken, ItemStack rootStack,
                                        String rootToken, String location,
                                        com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Cell anchor,
                                        com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Rotation rotation,
                                        boolean directKey) {
        if (!PortableBodyKeys.isKey(stack) || locationToken == null || stack.getCount() != 1
                || stack.getMaxStackSize() != 1
                || !LooseObjectProfile.PORTABLE_BODY_KEY_ID.equals(stack.get(ModDataComponents.LOOSE_OBJECT_PROFILE.get()))) return false;
        PortableBodyReference ref = PortableBodyKeys.reference(stack);
        LivingEntity mouse = ref == null || !ref.dimension().equals(level.dimension().location())
                ? null : level.getEntity(ref.body()) instanceof LivingEntity living ? living : null;
        PortableBodyComponent capability = mouse == null ? null : com.juicyslew.moonstation14.ms14.hands.HandCapability
                .resolveCharacter(mouse).flatMap(c -> c.component(PortableBodyComponent.class)).orElse(null);
        if (mouse == null || mouse == carrier || capability == null || !BodyContainmentService.isValidBody(mouse, level)
                || rootStack == null || rootToken == null
                || found.putIfAbsent(ref.body(), new Entry(location, locationToken, ref, stack.copy(),
                rootStack.copy(), rootToken, anchor, rotation, directKey)) != null) return false;
        try { LooseObjectProfile.resolve(capability.profileId()); }
        catch (RuntimeException missingProfile) { return false; }
        ContainedBodyOwner owner = BodyContainmentService.forLevel(level).registeredOwnerOf(mouse).orElse(null);
        return owner == null || owner instanceof PortableBodyCarrierOwner portable && portable.carrier() == carrier
                || owner instanceof PortableWorldStorageOwner world && world.reference().equals(ref);
    }

    public static boolean containsPortableReferences(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (PortableBodyKeys.isKeyItem(stack)) return true;
        return !directGridKeys(stack).isEmpty() || hasRawPortableKey(stack);
    }

    /** Detect keys in supported raw host payloads even when geometry validation rejects the host. */
    private static boolean hasRawPortableKey(ItemStack host) {
        if (host == null || host.isEmpty() || !isGridHost(host)) return false;
        var grid = host.get(ModDataComponents.GRID_CONTENTS.get());
        if (grid != null && grid.entries().stream().anyMatch(entry -> PortableBodyKeys.isKeyItem(entry.stack())))
            return true;
        var pouch = host.get(ModDataComponents.POUCH_CONTENTS.get());
        return pouch != null && pouch.child().map(child -> PortableBodyKeys.isKeyItem(child.stack())).orElse(false);
    }

    /** Shared bounded one-level key enumeration for owner locators and consumer guards. */
    /** Read-only bounded direct-child key enumeration shared by owner reconciliation and root adapters. */
    public static java.util.List<GridContents.Entry> directGridKeys(ItemStack root) {
        if (root == null || root.isEmpty()) return java.util.List.of();
        var grid = GridContents.read(root).orElse(null);
        if (grid == null) return java.util.List.of();
        return grid.entries().stream().filter(entry -> PortableBodyKeys.isKeyItem(entry.stack())).toList();
    }

    static boolean exactWorldEntry(ItemStack root, String childToken, PortableBodyReference reference) {
        return childToken != null && reference != null && directGridKeys(root).stream()
                .anyMatch(entry -> entry.token().equals(childToken)
                        && reference.equals(PortableBodyKeys.reference(entry.stack())));
    }

    /** Validates every direct world-root key against the sole containment ledger, with no nested scans. */
    static boolean worldRootReferencesOwned(ServerLevel level, UUID rootId, ItemStack rootStack) {
        if (level == null || rootId == null || rootStack == null || rootStack.isEmpty()) return false;
        if (isGridHost(rootStack) && GridContents.read(rootStack).isEmpty() && hasRawPortableKey(rootStack))
            return false;
        var ids = new java.util.HashSet<UUID>();
        var tokens = new java.util.HashSet<String>();
        var containment = BodyContainmentService.forLevel(level);
        for (var entry : directGridKeys(rootStack)) {
            var reference = PortableBodyKeys.reference(entry.stack());
            if (reference == null || !ids.add(reference.body()) || !tokens.add(entry.token())
                    || !reference.dimension().equals(level.dimension().location())
                    || !(level.getEntity(reference.body()) instanceof LivingEntity body)) return false;
            var owner = containment.registeredOwnerOf(body).orElse(null);
            if (!(owner instanceof PortableWorldStorageOwner world) || !world.rootId().equals(rootId)
                    || !world.childToken().equals(entry.token()) || !world.reference().equals(reference)
                    || !world.isValid()) return false;
        }
        return true;
    }

    public static GridContents.Entry directPortableKey(ItemStack root) {
        if (PortableBodyKeys.isKeyItem(root)) return new GridContents.Entry("direct", root, 0, 0,
                com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Rotation.DEG_0);
        var grid = root == null || root.isEmpty() ? java.util.Optional.<GridContents>empty() : GridContents.read(root);
        if (grid.isEmpty()) return null;
        var matches = grid.orElseThrow().entries().stream().filter(entry -> PortableBodyKeys.isKeyItem(entry.stack())).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static boolean isGridHost(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (stack.is(ModItems.BAG) || stack.is(ModItems.BELT));
    }
    private static boolean gridContainsKey(ItemStack host) {
        return containsPortableReferences(host);
    }

    /** Whether the exact reference remains in this body's current hand/equipped direct-grid ledger. */
    static boolean hasCurrentReference(LivingEntity carrier, PortableBodyReference reference) {
        return currentLocationToken(carrier, reference) != null;
    }

    static String currentLocationToken(LivingEntity carrier, PortableBodyReference reference) {
        if (carrier == null || reference == null) return null;
        LiveHands current = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (current == null || !current.compatible(carrier)) return null;
        Map<UUID, Entry> currentEntries = entries(carrier, current);
        Entry entry = currentEntries == null ? null : currentEntries.get(reference.body());
        return entry != null && entry.reference().equals(reference) ? entry.handToken() : null;
    }

    public record ReferenceLocation(String location, String childToken, String rootToken,
                                    ItemStack keyStack, ItemStack rootStack, long rootRevision,
                                    com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Cell anchor,
                                    com.juicyslew.moonstation14.ms14.storage.grid.GridGeometry.Rotation rotation,
                                    boolean directKey) {
        public ReferenceLocation { keyStack = keyStack.copy(); rootStack = rootStack.copy(); }
        @Override public ItemStack keyStack() { return keyStack.copy(); }
        @Override public ItemStack rootStack() { return rootStack.copy(); }
    }

    public static ReferenceLocation locateReference(LivingEntity carrier, PortableBodyReference reference) {
        if (carrier == null || reference == null || !(carrier.level() instanceof ServerLevel level)) return null;
        LiveHands hands = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(carrier)) return null;
        Map<UUID, Entry> current = entries(carrier, hands);
        Entry entry = current == null ? null : current.get(reference.body());
        if (entry == null || !entry.reference().equals(reference)) return null;
        return new ReferenceLocation(entry.hand(), entry.handToken(), entry.rootToken(), entry.keyStack(),
                entry.rootStack(), hands.revision(), entry.anchor(), entry.rotation(), entry.directKey());
    }

    /** Bounded existing live-hands/equipment key enumeration for one joining root; no world scan. */
    public static java.util.List<PortableBodyReference> loadedReferences(LivingEntity carrier) {
        if (carrier == null || !(carrier.level() instanceof ServerLevel)) return null;
        LiveHands hands = carrier.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(carrier)) return null;
        Map<UUID, Entry> found = entries(carrier, hands);
        return found == null ? null : found.values().stream().map(Entry::reference).toList();
    }
}
