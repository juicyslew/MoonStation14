package com.juicyslew.moonstation14.ms14.chemistry;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.reagent.ReagentComponent;
import com.juicyslew.moonstation14.ms14.reagent.ReagentUnits;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** ChemMaster's inert, unlimited-within-ReagentUnits buffer plus presentation order metadata. */
public final class ChemMasterBuffer {
    /** The sole supported solution name of the canonical ChemMaster buffer. */
    public static final String SOLUTION_NAME = "buffer";
    public enum Amount { ONE(100), FIVE(500), TEN(1000), FIFTEEN(1500), TWENTY(2000), TWENTY_FIVE(2500), THIRTY(3000), FIFTY(5000), HUNDRED(10000), ALL(-1);
        private final long cents;
        Amount(long cents) { this.cents = cents; }
        public long cents() { return cents; }
    }
    public enum Sort { OLDEST, ALPHABETICAL, QUANTITY, RECENT }
    public record Entry(ResourceKey<ReagentData> reagent, long cents) {}
    public record Snapshot(ReagentComponent contents, List<ResourceKey<ReagentData>> order) {
        public Snapshot {
            Objects.requireNonNull(contents); Objects.requireNonNull(order);
            if (order.stream().anyMatch(Objects::isNull) || new HashSet<>(order).size() != order.size())
                throw new IllegalArgumentException("buffer order must contain unique non-null reagent keys");
            order = List.copyOf(order);
        }
    }
    private static final Codec<ResourceKey<ReagentData>> ORDER_KEY_CODEC = ResourceLocation.CODEC.xmap(
            id -> ResourceKey.create(ModReagents.REAGENT_REGISTRY_KEY, id), ResourceKey::location);
    public static final Codec<Snapshot> SNAPSHOT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ReagentComponent.CODEC.fieldOf("contents").forGetter(Snapshot::contents),
            ORDER_KEY_CODEC.listOf().fieldOf("order").forGetter(Snapshot::order)
    ).apply(instance, Snapshot::new));

    private final ReagentAttachment quantities;
    private final ArrayList<ResourceKey<ReagentData>> order;

    public ChemMasterBuffer() { this(new Snapshot(new ReagentComponent(), List.of())); }
    public ChemMasterBuffer(Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        quantities = snapshot.contents().toAttachment();
        order = new ArrayList<>();
        Set<ResourceKey<ReagentData>> present = quantities.snapshotUnits().keySet();
        for (var key : snapshot.order()) if (present.contains(key) && !order.contains(key)) order.add(key);
        present.stream().filter(key -> !order.contains(key)).sorted(Comparator.comparing(key -> key.location().toString())).forEach(order::add);
    }
    public Snapshot snapshot() { return new Snapshot(quantities.toComponent(), order); }
    public Map<ResourceKey<ReagentData>, Long> quantities() { return quantities.snapshotUnits(); }
    public List<ResourceKey<ReagentData>> order() { return List.copyOf(order); }

    /** Extract selected material from a staged beaker; call this before committing either staged object. */
    public long extractFrom(ReagentAttachment stagedBeaker, ResourceKey<ReagentData> reagent, Amount amount) {
        long requested = requested(stagedBeaker, reagent, amount);
        long moved = ReagentAttachment.transferSelectedUnits(stagedBeaker, quantities, reagent, requested, ReagentUnits.MAX_CENTS);
        reconcileOrder();
        return moved;
    }

    /** Return selected material into a staged beaker, clamped to its resolved capacity. */
    public long returnTo(ReagentAttachment stagedBeaker, ResourceKey<ReagentData> reagent, Amount amount, long capacityCents) {
        long requested = requested(quantities, reagent, amount);
        long moved = ReagentAttachment.transferSelectedUnits(quantities, stagedBeaker, reagent, requested, capacityCents);
        reconcileOrder();
        return moved;
    }

    /** Stage an entire mixture without reactions; failure/overflow leaves this buffer unchanged. */
    public boolean addWholeMixture(ReagentAttachment mixture) {
        Objects.requireNonNull(mixture);
        try {
            long incoming = mixture.totalUnits();
            long current = quantities.totalUnits();
            if (current > ReagentUnits.MAX_CENTS || incoming > ReagentUnits.MAX_CENTS - current) return false;
            ReagentAttachment staged = new ReagentAttachment(quantities.toComponent());
            long moved = ReagentAttachment.transferUnits(mixture, staged, incoming, ReagentUnits.MAX_CENTS);
            if (moved != incoming) return false;
            Map<ResourceKey<ReagentData>, Long> combined = staged.snapshotUnits();
            quantities.clear();
            combined.forEach((key, amount) -> quantities.admitUnits(key, amount, ReagentUnits.MAX_CENTS));
            reconcileOrder();
            return true;
        } catch (RuntimeException invalidOrOverflow) { return false; }
    }

    /** Intentional sink operation on this buffer. */
    public long discard(ResourceKey<ReagentData> reagent, Amount amount) {
        long removed = quantities.removeUnits(reagent, requested(quantities, reagent, amount));
        reconcileOrder();
        return removed;
    }

    private static long requested(ReagentAttachment source, ResourceKey<ReagentData> reagent, Amount amount) {
        Objects.requireNonNull(source); Objects.requireNonNull(reagent); Objects.requireNonNull(amount);
        return amount == Amount.ALL ? source.snapshotUnits().getOrDefault(reagent, 0L) : amount.cents();
    }

    /** Moves exactly one species, clamped by both available quantity and destination room. */
    public static long transfer(ReagentAttachment source, ReagentAttachment target,
                                ResourceKey<ReagentData> reagent, Amount amount, long targetCapacityCents) {
        Objects.requireNonNull(amount);
        long requested = amount == Amount.ALL ? source.snapshotUnits().getOrDefault(reagent, 0L) : amount.cents();
        return ReagentAttachment.transferSelectedUnits(source, target, reagent, requested, targetCapacityCents);
    }

    /** Intentional sink operation, isolated to the selected reagent. */
    public static long discard(ReagentAttachment source, ResourceKey<ReagentData> reagent, Amount amount) {
        Objects.requireNonNull(amount);
        long requested = amount == Amount.ALL ? source.snapshotUnits().getOrDefault(reagent, 0L) : amount.cents();
        return source.removeUnits(reagent, requested);
    }

    public List<Entry> sorted(Sort sort, Comparator<ResourceKey<ReagentData>> localizedAlphabetical) {
        Objects.requireNonNull(sort);
        Map<ResourceKey<ReagentData>, Long> values = quantities.snapshotUnits();
        ArrayList<ResourceKey<ReagentData>> keys = new ArrayList<>(order);
        keys.removeIf(key -> !values.containsKey(key));
        Comparator<ResourceKey<ReagentData>> comparator = switch (sort) {
            case OLDEST -> Comparator.<ResourceKey<ReagentData>>comparingInt(order::indexOf);
            case RECENT -> Comparator.<ResourceKey<ReagentData>>comparingInt(order::indexOf).reversed();
            case ALPHABETICAL -> Objects.requireNonNull(localizedAlphabetical);
            case QUANTITY -> Comparator.<ResourceKey<ReagentData>>comparingLong(values::get).reversed().thenComparingInt(order::indexOf);
        };
        keys.sort(comparator);
        return keys.stream().map(key -> new Entry(key, values.get(key))).toList();
    }

    /** Maintain order after edits to the authoritative quantity attachment. */
    public void reconcileOrder() {
        Map<ResourceKey<ReagentData>, Long> values = quantities.snapshotUnits();
        order.removeIf(key -> !values.containsKey(key));
        values.keySet().stream().filter(key -> !order.contains(key)).sorted(Comparator.comparing(key -> key.location().toString())).forEach(order::add);
    }
}

