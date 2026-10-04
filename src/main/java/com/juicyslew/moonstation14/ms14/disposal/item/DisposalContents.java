package com.juicyslew.moonstation14.ms14.disposal.item;

import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocation;
import com.juicyslew.moonstation14.ms14.hands.transfer.ItemLocationLedger;
import net.minecraft.world.item.ItemStack;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.construction.tool.ToolPrototypeData;
import com.juicyslew.moonstation14.ms14.construction.tool.WelderFuelService;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuse;
import com.juicyslew.moonstation14.ms14.explosion.grenade.core.GrenadeFuseData;
import net.minecraft.server.level.ServerLevel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Transient whole-stack payload owner for one disposal unit or transit holder. Not actor permission. */
public final class DisposalContents {
    private final UUID ownerId;
    private final ItemLocationLedger ledger;
    private final Map<String, Payload> payloads = new LinkedHashMap<>();
    private final java.util.Set<String> expiredEntries = new java.util.HashSet<>();

    public DisposalContents(UUID ownerId, ItemLocationLedger ledger) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    public static Optional<DisposalContents> decode(EncodedData data, ItemLocationLedger ledger) {
        Objects.requireNonNull(data); Objects.requireNonNull(ledger);
        DisposalContents contents = new DisposalContents(data.ownerId(), ledger);
        for (var entry : data.payloads().entrySet()) {
            var location = new ItemLocation.DisposalContainer(data.ownerId(), entry.getKey());
            ItemLocation existing = ledger.snapshot().locations().get(entry.getValue().token());
            if (existing == null) {
                if (!ledger.register(entry.getValue().token(), location).accepted()) return Optional.empty();
            } else if (!existing.equals(location)) return Optional.empty();
            contents.payloads.put(entry.getKey(), entry.getValue());
        }
        return Optional.of(contents);
    }

    public UUID ownerId() { return ownerId; }

    /** Admit a pre-consumed whole item using an exact expected source; token and stack metadata are retained. */
    public synchronized boolean accept(ItemToken token, ItemLocation expectedSource, String entryId,
                                       ItemStack stack, long expectedLedgerRevision) {
        Objects.requireNonNull(token); Objects.requireNonNull(expectedSource); Objects.requireNonNull(stack);
        if (stack.isEmpty() || entryId == null || entryId.isBlank() || payloads.containsKey(entryId)
                || expiredEntries.contains(entryId)) return false;
        ItemLocation.DisposalContainer target = new ItemLocation.DisposalContainer(ownerId, entryId);
        ItemLocationLedger.Result moved = ledger.compareAndMove(expectedLedgerRevision, token, expectedSource, target);
        if (!moved.accepted()) return false;
        payloads.put(entryId, new Payload(token, stack.copy()));
        return true;
    }

    /** Transfer the exact payload identity between owners without changing the token or stack. */
    public synchronized Optional<Payload> transferTo(String entryId, DisposalContents destination,
                                                      String destinationEntryId, long expectedRevision) {
        Objects.requireNonNull(destination);
        if (destination == this || destinationEntryId == null || destinationEntryId.isBlank()) return Optional.empty();
        Payload payload = payloads.get(entryId);
        if (payload == null || expiredEntries.contains(entryId) || destination.payloads.containsKey(destinationEntryId)) return Optional.empty();
        ItemLocation from = new ItemLocation.DisposalContainer(ownerId, entryId);
        ItemLocation to = new ItemLocation.DisposalContainer(destination.ownerId, destinationEntryId);
        ItemLocationLedger.Result moved = ledger.compareAndMove(expectedRevision, payload.token(), from, to);
        if (!moved.accepted()) return Optional.empty();
        payloads.remove(entryId);
        destination.payloads.put(destinationEntryId, payload);
        return Optional.of(new Payload(payload.token(), payload.stack().copy()));
    }

    /** Remove only after caller has successfully staged the world ejection. */
    public synchronized Optional<Payload> removeForEjection(String entryId, ItemLocation.WorldEntity stagedWorld,
                                                              long expectedRevision) {
        Payload payload = payloads.get(entryId);
        if (payload == null || expiredEntries.contains(entryId)) return Optional.empty();
        var result = ledger.compareAndMove(expectedRevision, payload.token(),
                new ItemLocation.DisposalContainer(ownerId, entryId), stagedWorld);
        if (!result.accepted()) return Optional.empty();
        payloads.remove(entryId);
        return Optional.of(new Payload(payload.token(), payload.stack().copy()));
    }

    public synchronized Map<String, Payload> snapshot() {
        Map<String, Payload> copy = new LinkedHashMap<>();
        payloads.forEach((id, payload) -> copy.put(id, new Payload(payload.token(), payload.stack().copy())));
        return Map.copyOf(copy);
    }

    /**
     * Tick owned timed stacks on the server game-time clock. Runtime owners must supply their live position/context
     * and route grenade blasts through their normal contained-explosion policy. Expired entries are removed from
     * this store and permanently consumed from the exact ledger location before callback invocation.
     */
    public synchronized TickResult tick(ServerLevel level, long globalTick,
                                        java.util.function.Function<ItemStack, ToolPrototypeData> welderTools,
                                        java.util.function.Consumer<ExpiredPayload> detonate) {
        Objects.requireNonNull(level); Objects.requireNonNull(welderTools); Objects.requireNonNull(detonate);
        return tickInternal(level, globalTick, welderTools, detonate);
    }

    synchronized TickResult tickAt(long globalTick,
                                   java.util.function.Function<ItemStack, ToolPrototypeData> welderTools,
                                   java.util.function.Consumer<ExpiredPayload> detonate) {
        return tickInternal(null, globalTick, welderTools, detonate);
    }

    private TickResult tickInternal(ServerLevel level, long globalTick,
                                    java.util.function.Function<ItemStack, ToolPrototypeData> welderTools,
                                    java.util.function.Consumer<ExpiredPayload> detonate) {
        int updated = 0, expired = 0;
        for (var iterator = payloads.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            Payload payload = entry.getValue();
            ItemStack stack = payload.stack();
            if (stack.is(ModItems.GRENADE) && stack.getCount() == 1) {
                GrenadeFuseData fuse = stack.get(ModDataComponents.GRENADE_FUSE.get());
                if (fuse != null && fuse.phase() == GrenadeFuse.Phase.ARMED) {
                    GrenadeFuse.Evaluation evaluation = fuse.toFuse().evaluate(globalTick);
                    if (evaluation.expired()) {
                        var consumed = ledger.consume(ledger.snapshot().revision(), payload.token(),
                                new ItemLocation.DisposalContainer(ownerId, entry.getKey()));
                        if (!consumed.accepted()) continue;
                        iterator.remove(); expiredEntries.add(entry.getKey());
                        expired++;
                        detonate.accept(new ExpiredPayload(entry.getKey(), payload.token(), stack.copy(), level));
                        continue;
                    }
                    GrenadeFuseData nextFuse = GrenadeFuseData.fromFuse(evaluation.state());
                    if (!nextFuse.equals(fuse)) {
                        stack.set(ModDataComponents.GRENADE_FUSE.get(), nextFuse);
                        payload = new Payload(payload.token(), stack);
                        entry.setValue(payload);
                        updated++;
                    }
                }
            }
            ToolPrototypeData tools = welderTools.apply(stack);
            if (tools != null) {
                ItemStack after = WelderFuelService.tickOwnerStack(stack, tools, globalTick);
                if (!ItemStack.isSameItemSameComponents(stack, after)) {
                    entry.setValue(new Payload(payload.token(), after));
                    updated++;
                }
            }
        }
        return new TickResult(updated, expired);
    }

    public record TickResult(int updated, int expired) { }
    public record ExpiredPayload(String entryId, ItemToken token, ItemStack stack, ServerLevel level) {
        public ExpiredPayload { Objects.requireNonNull(entryId); Objects.requireNonNull(token); stack = stack.copy(); }
        @Override public ItemStack stack() { return stack.copy(); }
    }

    private record EncodedEntry(String entryId, String token, ItemStack stack) { }
    private record Encoded(UUID ownerId, java.util.List<EncodedEntry> entries) { }
    private static final Codec<EncodedEntry> ENTRY_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("entry").forGetter(EncodedEntry::entryId),
            Codec.STRING.fieldOf("token").forGetter(EncodedEntry::token),
            ItemStack.CODEC.fieldOf("stack").forGetter(EncodedEntry::stack)
    ).apply(i, EncodedEntry::new));
    private static final Codec<Encoded> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("owner").forGetter(Encoded::ownerId),
            ENTRY_CODEC.listOf().fieldOf("payloads").forGetter(Encoded::entries)
    ).apply(i, Encoded::new));

    /** Normal registry-aware Minecraft codec; write/read via RegistryOps and ItemStack.CODEC conventions. */
    public static Codec<EncodedData> codec() { return CODEC.xmap(EncodedData::new, EncodedData::encoded); }

    public synchronized EncodedData encodedData() {
        return new EncodedData(new Encoded(ownerId, payloads.entrySet().stream()
                .map(e -> new EncodedEntry(e.getKey(), e.getValue().token().value(), e.getValue().stack())).toList()));
    }

    /** Immutable serialization value; load into a fresh store and reconcile its locations with the item ledger. */
    public static final class EncodedData {
        private final Encoded value;
        private EncodedData(Encoded value) { this.value = value; }
        private Encoded encoded() { return value; }
        public UUID ownerId() { return value.ownerId(); }
        public Map<String, Payload> payloads() {
            Map<String, Payload> result = new LinkedHashMap<>();
            for (EncodedEntry e : value.entries()) result.put(e.entryId(), new Payload(new ItemToken(e.token()), e.stack()));
            return Map.copyOf(result);
        }
    }

    public record Payload(ItemToken token, ItemStack stack) {
        public Payload { Objects.requireNonNull(token); Objects.requireNonNull(stack); stack = stack.copy(); }
        @Override public ItemStack stack() { return stack.copy(); }
    }
}
