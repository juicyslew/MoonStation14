package com.juicyslew.moonstation14.ms14.hands;

import com.juicyslew.moonstation14.util.interfaces.IMS14Component;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable persisted body hand-state snapshot. Occupants are opaque identifiers, not items. */
public record HandComponent(List<Slot> hands, String activeHand)
        implements IMS14Component<HandComponent, HandAttachment> {
    public static final int MAX_HANDS = HandState.MAX_HANDS;
    public static final int MAX_ID_LENGTH = HandState.MAX_ID_LENGTH;
    public static final int MAX_TOKEN_LENGTH = ItemToken.MAX_LENGTH;

    private static final Codec<String> HAND_ID_CODEC = boundedString(MAX_ID_LENGTH, "hand ID");
    private static final Codec<String> TOKEN_CODEC = boundedString(MAX_TOKEN_LENGTH, "item token");
    private static final Codec<Slot> SLOT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            HAND_ID_CODEC.fieldOf("id").forGetter(Slot::id),
            TOKEN_CODEC.optionalFieldOf("token").forGetter(Slot::token)
    ).apply(instance, Slot::new));

    private static final Codec<Raw> RAW_CODEC = RecordCodecBuilder.create(
            (RecordCodecBuilder.Instance<Raw> instance) -> instance.group(
                    SLOT_CODEC.listOf().fieldOf("hands").forGetter(Raw::hands),
                    HAND_ID_CODEC.fieldOf("active").forGetter(Raw::activeHand)
            ).apply(instance, Raw::new));
    public static final Codec<HandComponent> CODEC = RAW_CODEC.comapFlatMap(raw ->
                    validate(raw.hands(), raw.activeHand()).map(ignored -> new HandComponent(raw.hands(), raw.activeHand())),
            value -> new Raw(value.hands(), value.activeHand()));

    public static final StreamCodec<RegistryFriendlyByteBuf, HandComponent> STREAM_CODEC = StreamCodec.of(
            HandComponent::encode, HandComponent::decode);

    public HandComponent {
        hands = List.copyOf(Objects.requireNonNull(hands, "hands"));
        Objects.requireNonNull(activeHand, "activeHand");
        validate(hands, activeHand).getOrThrow(IllegalArgumentException::new);
    }

    public static HandComponent from(HandState state) {
        Objects.requireNonNull(state, "state");
        return new HandComponent(state.handIds().stream()
                .map(id -> new Slot(id, state.occupant(id).map(ItemToken::value)))
                .toList(), state.activeHand());
    }

    public HandState toHandState() {
        HandState state = HandState.create(hands.stream().map(Slot::id).toList());
        for (Slot slot : hands) {
            if (slot.token().isPresent()) {
                state = state.place(slot.id(), new ItemToken(slot.token().orElseThrow())).state();
            }
        }
        return state.selectActive(activeHand).state();
    }

    private static DataResult<Boolean> validate(List<Slot> hands, String activeHand) {
        if (hands.isEmpty() || hands.size() > MAX_HANDS)
            return DataResult.error(() -> "hand count must be in [1, " + MAX_HANDS + "]");
        Set<String> ids = new HashSet<>();
        Set<String> tokens = new HashSet<>();
        for (Slot slot : hands) {
            if (!ids.add(slot.id())) return DataResult.error(() -> "duplicate hand ID: " + slot.id());
            if (slot.token().isPresent() && !tokens.add(slot.token().orElseThrow()))
                return DataResult.error(() -> "duplicate item token");
        }
        if (!ids.contains(activeHand)) return DataResult.error(() -> "active hand is not defined");
        return DataResult.success(true);
    }

    private static Codec<String> boundedString(int max, String label) {
        return Codec.STRING.comapFlatMap(value -> value.isBlank() || value.length() > max
                ? DataResult.error(() -> label + " must be nonblank and at most " + max + " characters")
                : DataResult.success(value), value -> value);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, HandComponent component) {
        buffer.writeVarInt(component.hands.size());
        for (Slot slot : component.hands) {
            buffer.writeUtf(slot.id(), MAX_ID_LENGTH);
            buffer.writeBoolean(slot.token.isPresent());
            slot.token.ifPresent(token -> buffer.writeUtf(token, MAX_TOKEN_LENGTH));
        }
        buffer.writeUtf(component.activeHand, MAX_ID_LENGTH);
    }

    private static HandComponent decode(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 1 || count > MAX_HANDS) throw new IllegalArgumentException("invalid hand count: " + count);
        List<Slot> slots = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String id = buffer.readUtf(MAX_ID_LENGTH);
            Optional<String> token = buffer.readBoolean()
                    ? Optional.of(buffer.readUtf(MAX_TOKEN_LENGTH)) : Optional.empty();
            slots.add(new Slot(id, token));
        }
        return new HandComponent(slots, buffer.readUtf(MAX_ID_LENGTH));
    }

    @Override public HandAttachment toAttachment() { return new HandAttachment(this); }
    @Override public Codec<HandComponent> getCodec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf, HandComponent> getStreamCodec() { return STREAM_CODEC; }

    public record Slot(String id, Optional<String> token) {
        public Slot {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(token, "token");
            if (id.isBlank() || id.length() > MAX_ID_LENGTH) throw new IllegalArgumentException("invalid hand ID");
            token.ifPresent(value -> {
                if (value.isBlank() || value.length() > MAX_TOKEN_LENGTH)
                    throw new IllegalArgumentException("invalid item token");
            });
        }
    }

    private record Raw(List<Slot> hands, String activeHand) { }
}
