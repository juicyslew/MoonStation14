package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.HandCapability;
import com.juicyslew.moonstation14.ms14.hands.HandAttachment;
import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.character.components.EquipmentSlotsPrototypeComponent;
import com.juicyslew.moonstation14.ms14.storage.pouch.PouchContents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Inert body-owned stack snapshot. Never publish its tokens to HANDS: legacy HANDS is metadata,
 * not stack ownership. Package-private whole-stack transitions are pure state calculations, not
 * authorization; server-side transfer services verify ownership, prototype and legacy compatibility
 * before publishing a snapshot.
 */
public final class LiveHands {
    private static final Codec<String> HAND_ID = Codec.STRING.validate(value ->
            value.isBlank() || value.length() > HandState.MAX_ID_LENGTH
                    ? DataResult.error(() -> "invalid hand ID") : DataResult.success(value));
    private static final Codec<String> TOKEN = Codec.STRING.validate(value ->
            value.isBlank() || value.length() > ItemToken.MAX_LENGTH
                    ? DataResult.error(() -> "invalid item token") : DataResult.success(value));
    private static final Codec<RawOccupant> RAW_OCCUPANT = RecordCodecBuilder.create(instance -> instance.group(
            TOKEN.fieldOf("token").forGetter(RawOccupant::token),
            ItemStack.CODEC.fieldOf("stack").forGetter(RawOccupant::stack)
    ).apply(instance, RawOccupant::new));
    private static final Codec<Occupant> OCCUPANT = RAW_OCCUPANT.comapFlatMap(raw -> {
        try {
            return DataResult.success(new Occupant(raw.token(), raw.stack()));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }, occupant -> new RawOccupant(occupant.token(), occupant.stack()));
    private static final Codec<Slot> SLOT = RecordCodecBuilder.create(instance -> instance.group(
            HAND_ID.fieldOf("id").forGetter(Slot::id),
            OCCUPANT.optionalFieldOf("occupant").forGetter(Slot::occupant)
    ).apply(instance, Slot::new));
    private static final Codec<String> EQUIPMENT_ID = Codec.STRING.validate(value ->
            EquipmentSlotsPrototypeComponent.ALLOWED_SLOTS.contains(value)
                    ? DataResult.success(value) : DataResult.error(() -> "invalid equipment slot"));
    private static final Codec<Slot> EQUIPMENT_SLOT = RecordCodecBuilder.create(instance -> instance.group(
            EQUIPMENT_ID.fieldOf("id").forGetter(Slot::id),
            OCCUPANT.optionalFieldOf("occupant").forGetter(Slot::occupant)
    ).apply(instance, Slot::new));
    private static final Codec<Raw> RAW = RecordCodecBuilder.create(instance -> instance.group(
            SLOT.listOf().fieldOf("hands").forGetter(Raw::hands),
            HAND_ID.fieldOf("active").forGetter(Raw::active),
            Codec.LONG.fieldOf("revision").forGetter(Raw::revision),
            EQUIPMENT_SLOT.listOf().optionalFieldOf("equipment").forGetter(Raw::equipment)
    ).apply(instance, Raw::new));
    public static final Codec<LiveHands> CODEC = RAW.comapFlatMap(raw -> {
        try {
            return DataResult.success(new LiveHands(raw.hands(), raw.active(), raw.revision(), raw.equipment()));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }, value -> new Raw(value.hands, value.active, value.revision, value.equipment));

    private final List<Slot> hands;
    private final String active;
    private final long revision;
    // Absent means a pre-equipment save, not a saved empty equipment layout.
    private final Optional<List<Slot>> equipment;

    private LiveHands(List<Slot> hands, String active, long revision, Optional<List<Slot>> equipment) {
        this.hands = List.copyOf(hands);
        this.active = active;
        this.revision = revision;
        this.equipment = equipment.map(List::copyOf);
        if (hands.isEmpty() || hands.size() > HandState.MAX_HANDS || revision < 0)
            throw new IllegalArgumentException("invalid hand count or revision");
        Set<String> ids = new HashSet<>();
        Set<String> tokens = new HashSet<>();
        for (Slot slot : hands) {
            if (!ids.add(slot.id()) || slot.occupant().filter(value -> !tokens.add(value.token())).isPresent())
                throw new IllegalArgumentException("duplicate hand or token");
        }
        if (this.equipment.map(slots -> slots.size() > EquipmentSlotsPrototypeComponent.MAX_SLOTS).orElse(false))
            throw new IllegalArgumentException("too many equipment slots");
        Set<String> equipmentIds = new HashSet<>();
        for (Slot slot : this.equipment.orElse(List.of())) {
            if (!EquipmentSlotsPrototypeComponent.ALLOWED_SLOTS.contains(slot.id())
                    || !equipmentIds.add(slot.id())
                    || slot.occupant().filter(value -> !tokens.add(value.token())).isPresent())
                throw new IllegalArgumentException("duplicate or invalid equipment slot or token");
        }
        if (!ids.contains(active)) throw new IllegalArgumentException("unknown active hand");
    }

    /** Explicit initialization only; never uses a human default for an unbound body or pig. */
    public static Optional<LiveHands> initialize(Entity body) {
        if (body.hasData(ModDataAttachments.LIVE_HANDS.get())) return Optional.empty();
        Optional<List<String>> prototype = hostHandIds(body);
        if (prototype.isEmpty() || prototype.orElseThrow().isEmpty()) return Optional.empty();
        try {
            HandState state = HandState.create(prototype.orElseThrow());
            LiveHands candidate = new LiveHands(state.handIds().stream()
                    .map(id -> new Slot(id, Optional.empty())).toList(), state.activeHand(), 0,
                    hostEquipmentIds(body).map(ids -> ids.stream().map(id -> new Slot(id, Optional.empty())).toList()));
            if (!candidate.compatible(body)) return Optional.empty();
            body.setData(ModDataAttachments.LIVE_HANDS.get(), candidate);
            return Optional.of(candidate);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /** Read-only eligibility: absence is NOT proof of an empty hand. */
    public static boolean isEmptyHand(Entity body, String hand) {
        LiveHands state = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        return state != null && state.compatible(body)
                && state.hands.stream().anyMatch(slot -> slot.id().equals(hand) && slot.occupant().isEmpty());
    }

    /** Legacy HANDS records layout metadata, not live active selection or stack ownership. */
    public boolean compatible(Entity body) {
        if (hostHandIds(body).filter(ids -> ids.equals(handIds())).isEmpty()) return false;
        Optional<List<String>> declared = hostEquipmentIds(body);
        if (equipment.isPresent() && declared.filter(ids -> ids.equals(equipmentIds())).isEmpty()) return false;
        HandAttachment metadata = body.getExistingDataOrNull(ModDataAttachments.HANDS.get());
        return metadata == null || (metadata.state().hands().stream().map(slot -> slot.id()).toList().equals(handIds())
                && metadata.state().hands().stream().allMatch(slot -> slot.token().isEmpty()));
    }

    private static Optional<List<String>> hostHandIds(Entity body) {
        return HandCapability.resolve(body);
    }

    private static Optional<List<String>> hostEquipmentIds(Entity body) {
        return HandCapability.resolveCharacter(body)
                .flatMap(character -> character.component(EquipmentSlotsPrototypeComponent.class))
                .map(EquipmentSlotsPrototypeComponent::slots);
    }

    private List<String> equipmentIds() {
        return equipment.orElse(List.of()).stream().map(Slot::id).toList();
    }

    /** Absent result means no compatible equipment capability; persisted distinguishes legacy absence. */
    public static Optional<EquipmentSnapshot> equipmentSnapshot(Entity body) {
        LiveHands state = body.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
        if (state == null || !state.compatible(body)) return Optional.empty();
        return hostEquipmentIds(body).map(ids -> new EquipmentSnapshot(state.equipment.isPresent(),
                ids.stream().map(id -> new EquipmentSlot(id, state.equipment.orElse(List.of()).stream()
                        .filter(slot -> slot.id().equals(id)).findFirst()
                        .flatMap(Slot::occupant).map(Occupant::token),
                        state.equipment.orElse(List.of()).stream().filter(slot -> slot.id().equals(id))
                                .findFirst().flatMap(Slot::occupant).map(Occupant::stack))).toList()));
    }

    public record EquipmentSlot(String id, Optional<String> token, Optional<ItemStack> stack) {
        public EquipmentSlot {
            stack = stack.map(ItemStack::copy);
        }
        @Override public Optional<ItemStack> stack() { return stack.map(ItemStack::copy); }
    }
    public record EquipmentSnapshot(boolean persisted, List<EquipmentSlot> slots) {
        public EquipmentSnapshot { slots = List.copyOf(slots); }
    }

    public List<String> handIds() { return hands.stream().map(Slot::id).toList(); }
    public String activeHand() { return active; }
    public long revision() { return revision; }

    /** Returns a defensive snapshot, never a transferable ownership claim. */
    public Optional<ItemStack> stackCopy(String hand) {
        return hands.stream().filter(slot -> slot.id().equals(hand)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown hand"))
                .occupant().map(value -> value.stack().copy());
    }

    public Optional<String> token(String hand) {
        return hands.stream().filter(slot -> slot.id().equals(hand)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown hand"))
                .occupant().map(Occupant::token);
    }

    /** Pure active-hand change; occupants and tokens stay in their original slots. */
    public Optional<LiveHands> selectActive(long expectedRevision, String chosen) {
        if (revision != expectedRevision || revision == Long.MAX_VALUE || hands.size() != 2
                || Objects.equals(active, chosen) || !handIds().contains(chosen)) return Optional.empty();
        return Optional.of(new LiveHands(hands, chosen, revision + 1, equipment));
    }

    enum Rejection { STALE_REVISION, REVISION_OVERFLOW, UNKNOWN_HAND, HAND_OCCUPIED,
        HAND_EMPTY, TOKEN_MISMATCH, DUPLICATE_TOKEN, INVALID_STACK }

    /** A detached result; rejected operations retain the exact original snapshot. */
    record WholeResult(LiveHands state, Optional<ItemStack> takenStack, Optional<Rejection> rejection) {
        WholeResult {
            Objects.requireNonNull(state);
            Objects.requireNonNull(takenStack);
            Objects.requireNonNull(rejection);
            takenStack = takenStack.map(ItemStack::copy);
        }

        @Override public Optional<ItemStack> takenStack() {
            return takenStack.map(ItemStack::copy);
        }

        boolean succeeded() { return rejection.isEmpty(); }
    }

    private WholeResult reject(Rejection reason) {
        return new WholeResult(this, Optional.empty(), Optional.of(reason));
    }

    /** Pure whole-stack calculation. The supplied stack is not proof of source ownership. */
    WholeResult putWhole(long expectedRevision, String hand, ItemToken token, ItemStack stack) {
        if (expectedRevision != revision) return reject(Rejection.STALE_REVISION);
        if (revision == Long.MAX_VALUE) return reject(Rejection.REVISION_OVERFLOW);
        int index = handIds().indexOf(hand);
        if (index < 0) return reject(Rejection.UNKNOWN_HAND);
        if (hands.get(index).occupant().isPresent()) return reject(Rejection.HAND_OCCUPIED);
        if (token == null || stack == null || stack.isEmpty() || stack.getCount() <= 0
                || stack.getCount() > stack.getMaxStackSize()) return reject(Rejection.INVALID_STACK);
        if (allOccupants().stream().anyMatch(value -> value.token().equals(token.value())))
            return reject(Rejection.DUPLICATE_TOKEN);
        ArrayList<Slot> next = new ArrayList<>(hands);
        next.set(index, new Slot(hand, Optional.of(new Occupant(token.value(), stack))));
        return new WholeResult(new LiveHands(next, active, revision + 1, equipment), Optional.empty(), Optional.empty());
    }

    /** Pure whole-stack calculation; caller must publish atomically with the owning carrier. */
    WholeResult takeWhole(long expectedRevision, String hand, ItemToken expectedToken) {
        if (expectedRevision != revision) return reject(Rejection.STALE_REVISION);
        if (revision == Long.MAX_VALUE) return reject(Rejection.REVISION_OVERFLOW);
        int index = handIds().indexOf(hand);
        if (index < 0) return reject(Rejection.UNKNOWN_HAND);
        Optional<Occupant> occupant = hands.get(index).occupant();
        if (occupant.isEmpty()) return reject(Rejection.HAND_EMPTY);
        if (expectedToken == null || !occupant.orElseThrow().token().equals(expectedToken.value()))
            return reject(Rejection.TOKEN_MISMATCH);
        ArrayList<Slot> next = new ArrayList<>(hands);
        next.set(index, new Slot(hand, Optional.empty()));
        return new WholeResult(new LiveHands(next, active, revision + 1, equipment),
                Optional.of(occupant.orElseThrow().stack()), Optional.empty());
    }

    /** Pure, single-revision movement into the pouch component; no intermediate ownership state. */
    Optional<LiveHands> insertPouch(long expectedRevision, String pouchHand, String pouchToken,
                                    String sourceHand, String sourceToken) {
        int pouchIndex = handIds().indexOf(pouchHand);
        int sourceIndex = handIds().indexOf(sourceHand);
        if (revision != expectedRevision || revision == Long.MAX_VALUE || hands.size() != 2
                || pouchIndex < 0 || sourceIndex < 0 || pouchIndex == sourceIndex
                || pouchToken == null || sourceToken == null || pouchToken.equals(sourceToken)) return Optional.empty();
        Optional<Occupant> pouch = hands.get(pouchIndex).occupant();
        Optional<Occupant> source = hands.get(sourceIndex).occupant();
        if (pouch.isEmpty() || source.isEmpty() || !pouch.orElseThrow().token().equals(pouchToken)
                || !source.orElseThrow().token().equals(sourceToken)) return Optional.empty();
        Optional<ItemStack> filled = PouchContents.put(pouch.orElseThrow().stack(), sourceToken,
                source.orElseThrow().stack());
        if (filled.isEmpty()) return Optional.empty();
        ArrayList<Slot> next = new ArrayList<>(hands);
        next.set(pouchIndex, new Slot(pouchHand, Optional.of(new Occupant(pouchToken, filled.orElseThrow()))));
        next.set(sourceIndex, new Slot(sourceHand, Optional.empty()));
        return Optional.of(new LiveHands(next, active, revision + 1, equipment));
    }

    /** Pure, single-revision movement out of the pouch component into the opposite empty hand. */
    Optional<LiveHands> extractPouch(long expectedRevision, String pouchHand, String pouchToken,
                                     String destinationHand, String childToken) {
        int pouchIndex = handIds().indexOf(pouchHand);
        int destinationIndex = handIds().indexOf(destinationHand);
        if (revision != expectedRevision || revision == Long.MAX_VALUE || hands.size() != 2
                || pouchIndex < 0 || destinationIndex < 0 || pouchIndex == destinationIndex
                || pouchToken == null || childToken == null || pouchToken.equals(childToken)) return Optional.empty();
        Optional<Occupant> pouch = hands.get(pouchIndex).occupant();
        if (pouch.isEmpty() || !pouch.orElseThrow().token().equals(pouchToken)
                || hands.get(destinationIndex).occupant().isPresent()) return Optional.empty();
        Optional<PouchContents.Removal> removed = PouchContents.take(pouch.orElseThrow().stack());
        if (removed.isEmpty() || !removed.orElseThrow().child().token().equals(childToken)) return Optional.empty();
        ArrayList<Slot> next = new ArrayList<>(hands);
        next.set(pouchIndex, new Slot(pouchHand, Optional.of(new Occupant(pouchToken, removed.orElseThrow().pouch()))));
        next.set(destinationIndex, new Slot(destinationHand,
                Optional.of(new Occupant(childToken, removed.orElseThrow().child().stack()))));
        return Optional.of(new LiveHands(next, active, revision + 1, equipment));
    }

    /** Pure one-cell transfer into an already worn container; the equipment owner never changes. */
    Optional<LiveHands> insertEquippedStorage(long expectedRevision, List<String> declared, String slot,
                                               String containerToken, String hand, String sourceToken) {
        if (!validEquippedStorage(expectedRevision, declared, slot, containerToken)) return Optional.empty();
        int handIndex = handIds().indexOf(hand);
        if (handIndex < 0 || sourceToken == null || sourceToken.equals(containerToken)) return Optional.empty();
        Optional<Occupant> source = hands.get(handIndex).occupant();
        if (source.isEmpty() || !source.orElseThrow().token().equals(sourceToken)
                || allOccupants().stream().filter(value -> value.token().equals(sourceToken)).count() != 1)
            return Optional.empty();
        int equipmentIndex = declared.indexOf(slot);
        Occupant container = equipmentSlot(equipmentIndex).occupant().orElseThrow();
        Optional<ItemStack> filled = PouchContents.put(container.stack(), sourceToken, source.orElseThrow().stack());
        if (filled.isEmpty()) return Optional.empty();
        ArrayList<Slot> nextHands = new ArrayList<>(hands);
        nextHands.set(handIndex, new Slot(hand, Optional.empty()));
        ArrayList<Slot> nextEquipment = new ArrayList<>(equipment.orElseThrow());
        nextEquipment.set(equipmentIndex, new Slot(slot, Optional.of(new Occupant(containerToken, filled.orElseThrow()))));
        return Optional.of(new LiveHands(nextHands, active, revision + 1, Optional.of(nextEquipment)));
    }

    /** Pure one-cell transfer out of a worn container into the designated empty hand. */
    Optional<LiveHands> extractEquippedStorage(long expectedRevision, List<String> declared, String slot,
                                                String containerToken, String hand, String childToken) {
        if (!validEquippedStorage(expectedRevision, declared, slot, containerToken)) return Optional.empty();
        int handIndex = handIds().indexOf(hand);
        if (handIndex < 0 || hands.get(handIndex).occupant().isPresent() || childToken == null
                || childToken.equals(containerToken) || allOccupants().stream()
                .anyMatch(value -> value.token().equals(childToken))) return Optional.empty();
        int equipmentIndex = declared.indexOf(slot);
        Occupant container = equipmentSlot(equipmentIndex).occupant().orElseThrow();
        Optional<PouchContents.Removal> removal = PouchContents.take(container.stack());
        if (removal.isEmpty() || !removal.orElseThrow().child().token().equals(childToken)) return Optional.empty();
        ArrayList<Slot> nextHands = new ArrayList<>(hands);
        nextHands.set(handIndex, new Slot(hand,
                Optional.of(new Occupant(childToken, removal.orElseThrow().child().stack()))));
        ArrayList<Slot> nextEquipment = new ArrayList<>(equipment.orElseThrow());
        nextEquipment.set(equipmentIndex, new Slot(slot,
                Optional.of(new Occupant(containerToken, removal.orElseThrow().pouch()))));
        return Optional.of(new LiveHands(nextHands, active, revision + 1, Optional.of(nextEquipment)));
    }

    private boolean validEquippedStorage(long expectedRevision, List<String> declared, String slot,
                                         String containerToken) {
        if (!validEquipmentTransition(expectedRevision, declared) || equipment.isEmpty()
                || slot == null || containerToken == null || !(slot.equals("belt") || slot.equals("back")))
            return false;
        int index = declared.indexOf(slot);
        if (index < 0) return false;
        return equipmentSlot(index).occupant().filter(value -> value.token().equals(containerToken))
                .map(value -> {
                    ItemStack stack = value.stack();
                    return (slot.equals("belt") && stack.is(ModItems.BELT)
                            || slot.equals("back") && stack.is(ModItems.BAG))
                            && PouchContents.read(stack).isPresent();
                }).orElse(false);
    }

    /** Pure internal rearrangement, never a source-to-hand insertion or hand-to-world extraction. */
    public Optional<LiveHands> move(long expectedRevision, String source, String target) {
        if (revision != expectedRevision || revision == Long.MAX_VALUE || Objects.equals(source, target))
            return Optional.empty();
        int from = handIds().indexOf(source);
        int to = handIds().indexOf(target);
        if (from < 0 || to < 0 || hands.get(from).occupant().isEmpty() || hands.get(to).occupant().isPresent())
            return Optional.empty();
        ArrayList<Slot> next = new ArrayList<>(hands);
        next.set(to, new Slot(target, hands.get(from).occupant()));
        next.set(from, new Slot(source, Optional.empty()));
        return Optional.of(new LiveHands(next, active, revision + 1, equipment));
    }

    private List<Occupant> allOccupants() {
        return java.util.stream.Stream.concat(hands.stream(), equipment.orElse(List.of()).stream())
                .flatMap(slot -> slot.occupant().stream()).toList();
    }

    /** Runtime entry point: the body must own both the hand and the exact declared equipment layout. */
    Optional<LiveHands> equip(long expectedRevision, Entity body, String hand, String slot, String token) {
        return compatible(body) ? hostEquipmentIds(body).flatMap(ids -> equip(expectedRevision, ids, hand, slot, token))
                : Optional.empty();
    }

    Optional<LiveHands> unequip(long expectedRevision, Entity body, String slot, String hand, String token) {
        return compatible(body) ? hostEquipmentIds(body).flatMap(ids -> unequip(expectedRevision, ids, slot, hand, token))
                : Optional.empty();
    }

    /** Pure fixture seam; callers publishing a result must first verify the bound body. */
    Optional<LiveHands> equip(long expectedRevision, List<String> declared, String hand, String slot, String token) {
        if (!validEquipmentTransition(expectedRevision, declared)) return Optional.empty();
        int from = handIds().indexOf(hand), to = declared.indexOf(slot);
        if (from < 0 || to < 0 || materializedEquipment(declared).get(to).occupant().isPresent()) return Optional.empty();
        Optional<Occupant> occupant = hands.get(from).occupant();
        if (occupant.isEmpty() || token == null || !occupant.orElseThrow().token().equals(token)) return Optional.empty();
        ArrayList<Slot> nextHands = new ArrayList<>(hands);
        nextHands.set(from, new Slot(hand, Optional.empty()));
        ArrayList<Slot> nextEquipment = new ArrayList<>(materializedEquipment(declared));
        nextEquipment.set(to, new Slot(slot, occupant));
        return Optional.of(new LiveHands(nextHands, active, revision + 1, Optional.of(nextEquipment)));
    }

    Optional<LiveHands> unequip(long expectedRevision, List<String> declared, String slot, String hand, String token) {
        if (!validEquipmentTransition(expectedRevision, declared) || equipment.isEmpty()) return Optional.empty();
        int from = declared.indexOf(slot), to = handIds().indexOf(hand);
        if (from < 0 || to < 0 || hands.get(to).occupant().isPresent()) return Optional.empty();
        Optional<Occupant> occupant = equipmentSlot(from).occupant();
        if (occupant.isEmpty() || token == null || !occupant.orElseThrow().token().equals(token)) return Optional.empty();
        ArrayList<Slot> nextEquipment = new ArrayList<>(equipment.orElseThrow());
        nextEquipment.set(from, new Slot(slot, Optional.empty()));
        ArrayList<Slot> nextHands = new ArrayList<>(hands);
        nextHands.set(to, new Slot(hand, occupant));
        return Optional.of(new LiveHands(nextHands, active, revision + 1, Optional.of(nextEquipment)));
    }

    private Slot equipmentSlot(int index) { return equipment.orElseThrow().get(index); }

    private List<Slot> materializedEquipment(List<String> declared) {
        return equipment.orElseGet(() -> declared.stream().map(id -> new Slot(id, Optional.empty())).toList());
    }

    private boolean validEquipmentTransition(long expectedRevision, List<String> declared) {
        if (revision != expectedRevision || revision == Long.MAX_VALUE || declared == null || declared.isEmpty()
                || declared.size() > EquipmentSlotsPrototypeComponent.MAX_SLOTS
                || !EquipmentSlotsPrototypeComponent.ALLOWED_SLOTS.containsAll(declared)
                || new HashSet<>(declared).size() != declared.size()) return false;
        return equipment.isEmpty() || equipmentIds().equals(declared);
    }

    private record Raw(List<Slot> hands, String active, long revision, Optional<List<Slot>> equipment) { }
    private record RawOccupant(String token, ItemStack stack) { }

    private record Slot(String id, Optional<Occupant> occupant) {
        private Slot {
            if (id == null || id.isBlank() || id.length() > HandState.MAX_ID_LENGTH || occupant == null)
                throw new IllegalArgumentException("invalid slot");
        }
    }

    private record Occupant(String token, ItemStack stack) {
        private Occupant {
            if (token == null || token.isBlank() || token.length() > ItemToken.MAX_LENGTH
                    || stack == null || stack.isEmpty() || stack.getCount() <= 0
                    || stack.getCount() > stack.getMaxStackSize())
                throw new IllegalArgumentException("invalid occupied stack");
            stack = stack.copy();
        }

        @Override public ItemStack stack() { return stack.copy(); }
    }
}
