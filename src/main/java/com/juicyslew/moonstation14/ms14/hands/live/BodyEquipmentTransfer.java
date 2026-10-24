package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.character.CharacterControlSystem;
import com.juicyslew.moonstation14.ms14.hands.HandActorAuthority;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateService;
import com.juicyslew.moonstation14.ms14.storage.grid.GridContents;
import com.juicyslew.moonstation14.ms14.storage.pouch.PouchContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy;
import com.juicyslew.moonstation14.ms14.equipment.ModInventoryTemplates;
import com.juicyslew.moonstation14.ms14.equipment.InventoryTemplateData;
import com.juicyslew.moonstation14.ms14.equipment.EquipmentItemData;
import com.juicyslew.moonstation14.ms14.character.components.EquipmentSlotsPrototypeComponent;
import com.juicyslew.moonstation14.ms14.hands.HandCapability;

import java.util.Optional;
import java.util.function.BooleanSupplier;

/** One body-owned attachment publication; equipment is never mirrored into vanilla slots. */
public final class BodyEquipmentTransfer {
    private BodyEquipmentTransfer() { }

    public enum Result { SUCCESS, DENIED, RECOVERY_REQUIRED }

    public static Result equip(ServerPlayer actor, String hand, String slot, String expectedToken,
                               long expectedRevision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return transferOwned(authority.orElseThrow().body(), hand, slot, expectedToken, expectedRevision,
                true, () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    public static Result unequip(ServerPlayer actor, String slot, String hand, String expectedToken,
                                 long expectedRevision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return transferOwned(authority.orElseThrow().body(), hand, slot, expectedToken, expectedRevision,
                false, () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    // Trusted fixture seam: tests supply validity for a real spawned body, never a FakePlayer authority.
    static Result transferOwned(LivingEntity body, String hand, String slot, String expectedToken,
                                long revision, boolean equipping, BooleanSupplier authorityValid) {
        return LiveHandsCommitGateway.batch(() -> transferOwnedInBatch(body, hand, slot,
                expectedToken, revision, equipping, authorityValid));
    }

    public static Result moveEquipment(ServerPlayer actor, String sourceSlot, String destinationSlot,
                                       String sourceToken, long revision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return moveEquipmentOwned(authority.orElseThrow().body(), sourceSlot, destinationSlot, sourceToken,
                revision, () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    static Result moveEquipmentOwned(LivingEntity body, String sourceSlot, String destinationSlot,
                                     String sourceToken, long revision, BooleanSupplier authorityValid) {
        return LiveHandsCommitGateway.batch(() -> {
            if (body == null || sourceSlot == null || destinationSlot == null || sourceToken == null
                    || authorityValid == null || sourceSlot.equals(destinationSlot)
                    || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                    || !level.getServer().isSameThread() || !live(body, level) || !authorityValid.getAsBoolean()
                    || !CharacterControlSystem.canAct(body)) return Result.DENIED;
            LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if (before == null || !before.compatible(body) || before.revision() != revision) return Result.DENIED;
            var snapshot = LiveHands.equipmentSnapshot(body).orElse(null);
            InventoryTemplateData template = templateForTransfer(body);
            if (snapshot == null || template == null) return Result.DENIED;
            var source = snapshot.slots().stream().filter(slot -> slot.id().equals(sourceSlot)).findFirst().orElse(null);
            var destination = snapshot.slots().stream().filter(slot -> slot.id().equals(destinationSlot)).findFirst().orElse(null);
            if (source == null || destination == null || !source.token().filter(sourceToken::equals).isPresent()
                    || destination.stack().isPresent() || destination.token().isPresent()) return Result.DENIED;
            ItemStack moving = source.stack().orElse(null);
            if (moving == null || !fitsForTransfer(level, template, snapshot, destinationSlot, moving,
                    snapshot.slots().stream().filter(slot -> slot.stack().isPresent()).map(LiveHands.EquipmentSlot::id)
                            .collect(java.util.stream.Collectors.toSet()))) return Result.DENIED;
            var removal = before.moveEquipment(revision, snapshot.slots().stream().map(LiveHands.EquipmentSlot::id).toList(),
                    template, sourceSlot, sourceToken, destinationSlot).orElse(null);
            if (removal == null || !authorityValid.getAsBoolean() || !live(body, level)
                    || !CharacterControlSystem.canAct(body)
                    || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                    || !before.compatible(body)) return Result.DENIED;
            return commitCandidate(body, before, removal.state(), removal.drops(), authorityValid);
        });
    }

    public static Result quickEquip(ServerPlayer actor, String hand, String expectedToken, long revision) {
        var authority = HandActorAuthority.resolve(actor);
        if (authority.isEmpty()) return Result.DENIED;
        return quickEquipOwned(authority.orElseThrow().body(), hand, expectedToken, revision,
                () -> HandActorAuthority.revalidate(actor, authority.orElseThrow()));
    }

    // Trusted real-body fixture seam; never grants actor authority.
    static Result quickEquipOwned(LivingEntity body, String hand, String expectedToken, long revision,
                                  BooleanSupplier authorityValid) {
        return LiveHandsCommitGateway.batch(() -> {
            if (!authorityValid.getAsBoolean() || !CharacterControlSystem.canAct(body)
                    || !(body.level() instanceof ServerLevel level)) return Result.DENIED;
            LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            if (before == null || !before.compatible(body) || before.revision() != revision
                    || !before.token(hand).filter(expectedToken::equals).isPresent()) return Result.DENIED;
            ItemStack incoming = before.stackCopy(hand).orElse(null);
            InventoryTemplateData template = templateForTransfer(body);
            var snapshot = LiveHands.equipmentSnapshot(body).orElse(null);
            if (incoming == null || !EquipmentFitPolicy.quickEquip(level, incoming) || template == null || snapshot == null)
                return Result.DENIED;
            var catalog = com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.catalog(level);
            var occupiedAtStart = snapshot.slots().stream().filter(value -> value.stack().isPresent())
                    .map(LiveHands.EquipmentSlot::id).collect(java.util.stream.Collectors.toSet());
            for (String slot : EquipmentFitPolicy.quickEquipSlots(level, template, incoming, occupiedAtStart, null)) {
                var current = snapshot.slots().stream().filter(value -> value.id().equals(slot)).findFirst().orElse(null);
                if (current == null) continue;
                var occupied = snapshot.slots().stream().filter(value -> value.stack().isPresent())
                        .map(LiveHands.EquipmentSlot::id).collect(java.util.stream.Collectors.toSet());
                if (current.stack().isPresent()) {
                    var oldData = catalog.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(current.stack().orElseThrow().getItem()));
                    if (oldData != null && oldData.clothing().isPresent()
                            && !oldData.clothing().orElseThrow().quickEquip()) continue;
                    occupied.remove(slot);
                }
                if (!fitsForTransfer(level, template, snapshot, slot, incoming, occupied)) continue;
                var removal = current.stack().isPresent() ? before.quickReplaceEquipment(revision,
                        snapshot.slots().stream().map(LiveHands.EquipmentSlot::id).toList(), template, hand, slot, expectedToken).orElse(null) : null;
                LiveHands candidate = current.stack().isPresent() ? removal == null ? null : removal.state()
                        : before.equip(revision, body, hand, slot, expectedToken).orElse(null);
                return commitCandidate(body, before, candidate,
                        removal == null ? java.util.List.of() : removal.drops(), authorityValid);
            }
            return Result.DENIED;
        });
    }

    private static Result transferOwnedInBatch(LivingEntity body, String hand, String slot, String expectedToken,
                                 long revision, boolean equipping, BooleanSupplier authorityValid) {
        if (body == null || hand == null || slot == null || expectedToken == null || authorityValid == null
                || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || !live(body, level)
                || !CharacterControlSystem.canAct(body)) return Result.DENIED;
        LiveHands before = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (before == null || !before.compatible(body) || before.revision() != revision)
            return Result.DENIED;
        // The bound prototype, not a saved layout or client-provided name, declares this slot.
        Optional<LiveHands.EquipmentSnapshot> snapshot = LiveHands.equipmentSnapshot(body);
        if (snapshot.isEmpty()) return Result.DENIED;
        Optional<LiveHands.EquipmentSlot> target = snapshot.orElseThrow().slots().stream()
                .filter(candidate -> candidate.id().equals(slot)).findFirst();
        if (target.isEmpty()) return Result.DENIED;
        ItemStack stack;
        try {
            if (equipping) {
                if (target.orElseThrow().token().isPresent() || target.orElseThrow().stack().isPresent()
                        || !before.token(hand).filter(expectedToken::equals).isPresent()) return Result.DENIED;
                stack = before.stackCopy(hand).orElseThrow();
            } else {
                if (!target.orElseThrow().token().filter(expectedToken::equals).isPresent()) return Result.DENIED;
                stack = target.orElseThrow().stack().orElseThrow();
            }
        } catch (IllegalArgumentException | java.util.NoSuchElementException invalid) {
            return Result.DENIED;
        }
        InventoryTemplateData template = templateForTransfer(body);
        var occupied = snapshot.orElseThrow().slots().stream().filter(candidate -> candidate.stack().isPresent())
                .map(LiveHands.EquipmentSlot::id).collect(java.util.stream.Collectors.toSet());
        if (template == null || equipping && !fitsForTransfer(level, template, snapshot.orElseThrow(), slot, stack, occupied)) return Result.DENIED;
        LiveHands candidate;
        java.util.List<LiveHands.EquipmentRemovalItem> drops;
        if (equipping) {
            candidate = before.equip(revision, body, hand, slot, expectedToken).orElse(null);
            drops = java.util.List.of();
        } else {
            var removal = before.unequipWithDependents(revision,
                    snapshot.orElseThrow().slots().stream().map(LiveHands.EquipmentSlot::id).toList(),
                    template, slot, hand, expectedToken).orElse(null);
            candidate = removal == null ? null : removal.state();
            drops = removal == null ? java.util.List.of() : removal.drops();
        }
        if (candidate == null || !authorityValid.getAsBoolean() || !live(body, level) || !CharacterControlSystem.canAct(body)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !before.compatible(body) || equipping && !fitsForTransfer(level, template, snapshot.orElseThrow(), slot, stack, occupied))
            return Result.DENIED;
        return commitCandidate(body, before, candidate, drops, authorityValid);
    }

    static Result commitCandidate(LivingEntity body, LiveHands before, LiveHands candidate,
                                  java.util.List<LiveHands.EquipmentRemovalItem> drops,
                                  BooleanSupplier authorityValid) {
        if (body == null || before == null || candidate == null || drops == null || authorityValid == null
                || !(body.level() instanceof ServerLevel level) || level.getServer() == null
                || !level.getServer().isSameThread() || !live(body, level) || !CharacterControlSystem.canAct(body)
                || !authorityValid.getAsBoolean() || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !before.compatible(body) || !BodyHandStateService.representable(candidate, body)) return Result.DENIED;
        DropStage stage = stageDependentDrops(body, drops);
        if (stage.result() != Result.SUCCESS) return stage.result();
        if (!authorityValid.getAsBoolean() || !live(body, level) || !CharacterControlSystem.canAct(body)
                || body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) != before
                || !before.compatible(body) || !BodyHandStateService.representable(candidate, body))
            return body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before
                    ? stage.cleanup() ? Result.DENIED : Result.RECOVERY_REQUIRED : Result.RECOVERY_REQUIRED;
        boolean committed;
        try { committed = LiveHandsCommitGateway.commitTrusted(body, before, candidate); }
        catch (RuntimeException failure) { committed = false; }
        if (committed && live(body, level)
                && body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == candidate
                && candidate.compatible(body)) return Result.SUCCESS;
        if (body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get()) == before)
            return stage.cleanup() ? Result.DENIED : Result.RECOVERY_REQUIRED;
        // The attachment no longer proves that the old stacks remain owned; leave the drops untouched.
        return Result.RECOVERY_REQUIRED;
    }

    /** Stages root-owned equipment drops without publishing an equipment attachment change. */
    public static DropStage stageDependentDrops(LivingEntity body,
                                                java.util.List<LiveHands.EquipmentRemovalItem> drops) {
        if (body == null || drops == null || !(body.level() instanceof ServerLevel level)
                || level.getServer() == null || !level.getServer().isSameThread() || !live(body, level))
            return new DropStage(Result.DENIED, null, java.util.List.of());
        var spawned = new java.util.ArrayList<SpawnedDrop>();
        for (var drop : drops) {
            if (drop == null || drop.stack() == null || drop.stack().isEmpty()) {
                boolean cleaned = cleanupSpawned(level, spawned);
                return new DropStage(cleaned ? Result.DENIED : Result.RECOVERY_REQUIRED, level, java.util.List.of());
            }
            var entity = BodyHandItemTransfer.spawnTrustedGrant(body, drop.stack());
            if (entity == null) {
                boolean cleaned = cleanupSpawned(level, spawned);
                return new DropStage(cleaned ? Result.DENIED : Result.RECOVERY_REQUIRED, level, java.util.List.of());
            }
            spawned.add(new SpawnedDrop(entity, drop.stack()));
        }
        return new DropStage(Result.SUCCESS, level, java.util.List.copyOf(spawned));
    }

    /** A successful stage remains caller-owned until either cleanup() or successful publication. */
    public static final class DropStage {
        private final Result result;
        private final ServerLevel level;
        private final java.util.List<SpawnedDrop> spawned;
        private boolean cleaned;

        private DropStage(Result result, ServerLevel level, java.util.List<SpawnedDrop> spawned) {
            this.result = result;
            this.level = level;
            this.spawned = spawned;
        }

        public Result result() { return result; }

        /** Removes staged entities only when identity and expected stack still match. */
        public boolean cleanup() {
            if (result != Result.SUCCESS) return result == Result.DENIED;
            if (cleaned) return true;
            cleaned = cleanupSpawned(level, spawned);
            return cleaned;
        }
    }

    private static boolean cleanupSpawned(ServerLevel level, java.util.List<SpawnedDrop> spawned) {
        boolean conserved = true;
        for (var spawnedDrop : spawned) {
            var entity = spawnedDrop.entity();
            if (entity.level() == level && level.getEntity(entity.getUUID()) == entity && entity.isAddedToLevel()
                    && !entity.isRemoved() && ItemStack.matches(spawnedDrop.stack(), entity.getItem())) {
                try { entity.discard(); }
                catch (RuntimeException failure) { conserved = false; }
            }
            if (level.getEntity(entity.getUUID()) == entity && !entity.isRemoved()) conserved = false;
        }
        return conserved;
    }

    private record SpawnedDrop(com.juicyslew.moonstation14.entities.LooseItemEntity entity, ItemStack stack) {
        private SpawnedDrop { stack = stack.copy(); }
        @Override public ItemStack stack() { return stack.copy(); }
    }

    public static InventoryTemplateData templateForTransfer(LivingEntity body) {
        var character = HandCapability.resolveCharacter(body).orElse(null);
        if (character == null) return null;
        var component = character.component(EquipmentSlotsPrototypeComponent.class).orElse(null);
        if (component == null || component.template().isEmpty()) return null;
        return ModInventoryTemplates.catalog(body.level()).get(component.template().orElseThrow());
    }

    public static boolean fitsForTransfer(ServerLevel level, InventoryTemplateData template,
                               LiveHands.EquipmentSnapshot snapshot, String slot, ItemStack candidate,
                               java.util.Set<String> occupied) {
        if (("belt".equals(slot) || "back".equals(slot))
                && GridContents.read(candidate).isEmpty() && PouchContents.read(candidate).isEmpty()) return false;
        var catalog = com.juicyslew.moonstation14.ms14.equipment.ModEquipmentItems.catalog(level);
        var candidateData = catalog.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(candidate.getItem()));
        if (candidateData != null && candidateData.clothing().isPresent() && candidate.getCount() != 1) return false;
        boolean outerAllows = snapshot.slots().stream().filter(value -> value.id().equals("outerClothing"))
                .flatMap(value -> value.stack().stream()).map(value -> catalog.get(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(value.getItem())))
                .anyMatch(value -> value != null && value.allowSuitStorage().isPresent());
        if ("suitstorage".equals(slot) && template.slot(slot).map(value -> !value.whitelistComponents().isEmpty()).orElse(false))
            return com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.fits(level, template, slot, candidate, occupied);
        if ("suitstorage".equals(slot)) return com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.fits(
                template, slot, candidateData, occupied, outerAllows);
        return com.juicyslew.moonstation14.ms14.equipment.EquipmentFitPolicy.fits(level, template, slot, candidate, occupied);
    }

    private static boolean live(LivingEntity body, ServerLevel level) {
        return body.level() == level && !body.isRemoved() && body.isAlive() && body.isAddedToLevel()
                && level.getEntity(body.getUUID()) == body;
    }
}
