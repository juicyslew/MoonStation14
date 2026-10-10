package com.juicyslew.moonstation14.ms14.machine.itemslots;

import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import net.minecraft.world.item.ItemStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/** One removable, whole-stack machine slot. Policy is supplied by the owning machine. */
public final class MachineItemSlot {
    private record Encoded(UUID owner, String id, long revision, String token, ItemStack stack) { }
    private static final Codec<Encoded> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("owner").forGetter(Encoded::owner),
            Codec.STRING.fieldOf("slot").forGetter(Encoded::id), Codec.LONG.fieldOf("revision").forGetter(Encoded::revision),
            Codec.STRING.optionalFieldOf("token", "").forGetter(Encoded::token),
            ItemStack.CODEC.optionalFieldOf("stack").forGetter(value -> value.stack().isEmpty()
                    ? Optional.empty() : Optional.of(value.stack()))
    ).apply(i, (owner, id, revision, token, stack) -> new Encoded(owner, id, revision, token,
            stack.orElse(ItemStack.EMPTY))));
    private final UUID owner;
    private final String id;
    private final Predicate<ItemStack> accepts;
    private final ItemLocationLedger locations;
    private long revision;
    private ItemToken token;
    private ItemStack stack = ItemStack.EMPTY;
    private boolean retired;

    public MachineItemSlot(UUID owner, String id, Predicate<ItemStack> accepts, ItemLocationLedger locations) {
        this.owner = Objects.requireNonNull(owner);
        this.id = Objects.requireNonNull(id);
        if (id.isBlank() || id.length() > ItemLocation.MAX_ID_LENGTH) throw new IllegalArgumentException("invalid slot id");
        this.accepts = Objects.requireNonNull(accepts);
        this.locations = Objects.requireNonNull(locations);
    }

    public synchronized Snapshot snapshot() { return new Snapshot(owner, id, revision, token, stack); }
    public synchronized boolean canAccept(long expectedRevision, ItemStack value) {
        return !retired && expectedRevision == revision && revision < Long.MAX_VALUE && stack.isEmpty()
                && value != null && !value.isEmpty() && accepts.test(value);
    }
    public static Codec<EncodedData> codec() { return CODEC.xmap(EncodedData::new, value -> value.value); }
    public synchronized EncodedData encode() {
        return new EncodedData(new Encoded(owner, id, revision, token == null ? "" : token.value(), stack));
    }
    /** Restore a saved owner into the shared item-location ledger; rejects conflicting ownership/policy. */
    public synchronized boolean restore(EncodedData data) {
        Encoded value = data.value;
        if (retired || !owner.equals(value.owner) || !id.equals(value.id) || value.revision < 0) return false;
        if (revision != 0 || token != null || !stack.isEmpty()) return false;
        if (value.stack.isEmpty()) {
            if (!value.token.isEmpty()) return false;
            stack = ItemStack.EMPTY; token = null; revision = value.revision; return true;
        }
        if (value.token.isEmpty() || !accepts.test(value.stack)) return false;
        ItemToken restored = new ItemToken(value.token);
        ItemLocation location = locations.snapshot().locations().get(restored);
        ItemLocation slot = new ItemLocation.MachineSlot(owner, id);
        if (location != null || !locations.register(restored, slot).accepted()) return false;
        token = restored; stack = value.stack.copy(); revision = value.revision; return true;
    }

    /** Retire this runtime instance and release its active-location claim; serialized payload stays owned here. */
    public synchronized boolean suspend() {
        if (retired) return true;
        if (token != null && !locations.consume(locations.snapshot().revision(), token,
                new ItemLocation.MachineSlot(owner, id)).accepted()) return false;
        retired = true;
        return true;
    }

    /** Reactivate a suspended runtime owner only if its persisted identity is currently unclaimed. */
    public synchronized boolean resume() {
        if (!retired) return true;
        if (token != null && !locations.register(token, new ItemLocation.MachineSlot(owner, id)).accepted()) return false;
        retired = false;
        return true;
    }

    public synchronized boolean isRetired() { return retired; }
    public static final class EncodedData {
        private final Encoded value;
        private EncodedData(Encoded value) { this.value = value; }
    }

    /** Move exact identified item into an empty compatible slot, only if slot and ledger revisions match. */
    public synchronized boolean insert(long expectedSlotRevision, long expectedLedgerRevision,
            ItemToken item, ItemLocation source, ItemStack wholeStack) {
        Objects.requireNonNull(item); Objects.requireNonNull(source); Objects.requireNonNull(wholeStack);
        if (retired || expectedSlotRevision != revision || revision == Long.MAX_VALUE || !stack.isEmpty() || wholeStack.isEmpty() || !accepts.test(wholeStack)) return false;
        var moved = locations.compareAndMove(expectedLedgerRevision, item, source, new ItemLocation.MachineSlot(owner, id));
        if (!moved.accepted()) return false;
        token = item; stack = wholeStack.copy(); revision++;
        return true;
    }

    /** Ejection records the staged destination before releasing the stored payload. */
    public synchronized Optional<Payload> eject(long expectedSlotRevision, long expectedLedgerRevision,
            ItemLocation destination) {
        if (retired || expectedSlotRevision != revision || revision == Long.MAX_VALUE || stack.isEmpty() || token == null) return Optional.empty();
        var moved = locations.compareAndMove(expectedLedgerRevision, token,
                new ItemLocation.MachineSlot(owner, id), destination);
        if (!moved.accepted()) return Optional.empty();
        Payload result = new Payload(token, stack);
        token = null; stack = ItemStack.EMPTY; revision++;
        return Optional.of(result);
    }

    /** Conditional component-preserving mutation; token identity cannot be changed. */
    public synchronized boolean mutate(long expectedRevision, ItemToken expectedToken, Predicate<ItemStack> mutation) {
        if (retired || revision != expectedRevision || token == null || !token.equals(expectedToken)) return false;
        ItemStack candidate = stack.copy();
        if (!mutation.test(candidate) || candidate.isEmpty() || !candidate.is(stack.getItem())
                || candidate.getCount() != stack.getCount() || !accepts.test(candidate) || revision == Long.MAX_VALUE) return false;
        stack = candidate; revision++;
        return true;
    }

    public UUID ownerId() { return owner; }
    public String slotId() { return id; }

    public record Payload(ItemToken token, ItemStack stack) {
        public Payload { Objects.requireNonNull(token); stack = Objects.requireNonNull(stack).copy(); }
        @Override public ItemStack stack() { return stack.copy(); }
    }
    public record Snapshot(UUID owner, String slotId, long revision, ItemToken token, ItemStack stack) {
        public Snapshot { Objects.requireNonNull(owner); Objects.requireNonNull(slotId); stack = stack.copy(); }
        @Override public ItemStack stack() { return stack.copy(); }
    }
}
