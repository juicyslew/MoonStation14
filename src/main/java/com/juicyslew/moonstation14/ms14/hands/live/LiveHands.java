package com.juicyslew.moonstation14.ms14.hands.live;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.hands.HandCapability;
import com.juicyslew.moonstation14.ms14.hands.HandAttachment;
import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
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
 * ownership transfers. A future server-authoritative service must verify the source, prototype
 * and legacy compatibility before publishing a transition or consuming a source stack.
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
    private static final Codec<Raw> RAW = RecordCodecBuilder.create(instance -> instance.group(
            SLOT.listOf().fieldOf("hands").forGetter(Raw::hands),
            HAND_ID.fieldOf("active").forGetter(Raw::active),
            Codec.LONG.fieldOf("revision").forGetter(Raw::revision)
    ).apply(instance, Raw::new));
    public static final Codec<LiveHands> CODEC = RAW.comapFlatMap(raw -> {
        try {
            return DataResult.success(new LiveHands(raw.hands(), raw.active(), raw.revision()));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }, value -> new Raw(value.hands, value.active, value.revision));

    private final List<Slot> hands;
    private final String active;
    private final long revision;

    private LiveHands(List<Slot> hands, String active, long revision) {
        this.hands = List.copyOf(hands);
        this.active = active;
        this.revision = revision;
        if (hands.isEmpty() || hands.size() > HandState.MAX_HANDS || revision < 0)
            throw new IllegalArgumentException("invalid hand count or revision");
        Set<String> ids = new HashSet<>();
        Set<String> tokens = new HashSet<>();
        for (Slot slot : hands) {
            if (!ids.add(slot.id()) || slot.occupant().filter(value -> !tokens.add(value.token())).isPresent())
                throw new IllegalArgumentException("duplicate hand or token");
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
                    .map(id -> new Slot(id, Optional.empty())).toList(), state.activeHand(), 0);
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

    /** Fail closed on prototype drift or any legacy metadata token (dual occupancy). */
    public boolean compatible(Entity body) {
        if (hostHandIds(body).filter(ids -> ids.equals(handIds())).isEmpty()) return false;
        HandAttachment metadata = body.getExistingDataOrNull(ModDataAttachments.HANDS.get());
        return metadata == null || (metadata.state().hands().stream().map(slot -> slot.id()).toList().equals(handIds())
                && metadata.state().activeHand().equals(active)
                && metadata.state().hands().stream().allMatch(slot -> slot.token().isEmpty()));
    }

    private static Optional<List<String>> hostHandIds(Entity body) {
        return HandCapability.resolve(body);
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
        if (hands.stream().anyMatch(slot -> slot.occupant().map(value -> value.token().equals(token.value()))
                .orElse(false))) return reject(Rejection.DUPLICATE_TOKEN);
        ArrayList<Slot> next = new ArrayList<>(hands);
        next.set(index, new Slot(hand, Optional.of(new Occupant(token.value(), stack))));
        return new WholeResult(new LiveHands(next, active, revision + 1), Optional.empty(), Optional.empty());
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
        return new WholeResult(new LiveHands(next, active, revision + 1),
                Optional.of(occupant.orElseThrow().stack()), Optional.empty());
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
        return Optional.of(new LiveHands(next, active, revision + 1));
    }

    private record Raw(List<Slot> hands, String active, long revision) { }
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
