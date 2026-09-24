package com.juicyslew.moonstation14.ms14.reagent;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.recipe.ModRecipes;
import com.juicyslew.moonstation14.recipe.ReactionRecipe;
import com.juicyslew.moonstation14.recipe.ReactionRecipeInput;
import com.juicyslew.moonstation14.util.interfaces.IMS14Attachment;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.math.BigInteger;
import java.util.*;

/** Runtime reagent storage. The sole authoritative representation is integer hundredths. */
public final class ReagentAttachment implements IMS14Attachment<ReagentAttachment, ReagentComponent> {
    private final Map<ResourceKey<ReagentData>, Long> cents;

    public ReagentAttachment(ReagentComponent data) {
        this.cents = new HashMap<>(Objects.requireNonNull(data, "data").centContents());
    }
    public ReagentAttachment(Map<ResourceKey<ReagentData>, Float> data) {
        cents = new HashMap<>();
        ReagentUnits.fromMap(Objects.requireNonNull(data, "data")).forEach((key, value) -> {
            if (value != 0) cents.put(key, value);
        });
    }
    public ReagentAttachment() { cents = new HashMap<>(); }

    /** Immutable legacy-float adapter snapshot. */
    public Map<ResourceKey<ReagentData>, Float> getMap() { return floatSnapshot(cents); }
    /** Immutable snapshot of the authoritative cent quantities. */
    public Map<ResourceKey<ReagentData>, Long> snapshotUnits() { return Collections.unmodifiableMap(new LinkedHashMap<>(cents)); }
    public long totalUnits() { return total(cents); }

    /** Removes an exact cent quantity from one reagent; returns the amount removed. */
    public long removeUnits(ResourceKey<ReagentData> key, long requestedCents) {
        Objects.requireNonNull(key, "key");
        ReagentUnits.validateCents(requestedCents, "requestedCents");
        long before = cents.getOrDefault(key, 0L);
        long removed = Math.min(before, requestedCents), left = before - removed;
        if (left == 0) cents.remove(key); else cents.put(key, left);
        return removed;
    }

    /** Admits an exact cent quantity subject to the total capacity; returns admitted cents. */
    public long admitUnits(ResourceKey<ReagentData> key, long requestedCents, long capacityCents) {
        Objects.requireNonNull(key, "key");
        ReagentUnits.validateCents(capacityCents, "capacityCents");
        ReagentUnits.validateCents(requestedCents, "requestedCents");
        long current = total(cents);
        if (current > capacityCents) throw new IllegalArgumentException("stored total exceeds capacity");
        long admitted = Math.min(requestedCents, capacityCents - current);
        long updated = checkedAdd(cents.getOrDefault(key, 0L), admitted);
        if (admitted != 0) cents.put(key, updated);
        return admitted;
    }
    public boolean isEmpty() { return cents.isEmpty(); }
    public ReagentComponent toComponent() { return ReagentComponent.fromCents(snapshotUnits()); }

    public float removeUpTo(ResourceKey<ReagentData> key, float amount) {
        long request = units(amount, "amount"), before = cents.getOrDefault(Objects.requireNonNull(key), 0L);
        long removed = Math.min(before, request), left = before - removed;
        if (left == 0) cents.remove(key); else cents.put(key, left);
        return ReagentUnits.toFloat(removed);
    }

    public void scale(float scale) {
        if (!Float.isFinite(scale) || scale < 0f) throw new IllegalArgumentException("scale must be finite and nonnegative");
        Map<ResourceKey<ReagentData>, Long> staged = new HashMap<>();
        cents.forEach((key, amount) -> { long value = ReagentUnits.scale(amount, scale); if (value != 0) staged.put(key, value); });
        replace(staged);
    }

    public void specificAdd(ResourceKey<ReagentData> key, float amount, float capacity) {
        Objects.requireNonNull(key, "key");
        if (!Float.isFinite(amount)) throw new IllegalArgumentException("amount must be finite");
        if (amount < 0) { specificRemove(key, -amount); return; }
        long cap = units(capacity, "capacity"), wanted = units(amount, "amount"), total = total(cents);
        if (total > cap) throw new IllegalArgumentException("stored total exceeds capacity");
        long added = Math.min(wanted, cap - total), result = checkedAdd(cents.getOrDefault(key, 0L), added);
        Map<ResourceKey<ReagentData>, Long> staged = new HashMap<>(cents);
        if (result != 0) staged.put(key, result);
        replace(staged);
    }

    public void mergeAdd(Map<ResourceKey<ReagentData>, Float> toAdd, float capacity) {
        addCapacitySafe(toAdd, capacity);
    }

    public void specificRemove(ResourceKey<ReagentData> key, float amount) {
        long request = units(amount, "amount"), before = cents.getOrDefault(Objects.requireNonNull(key), 0L), left = Math.max(0, before - request);
        if (left == 0) cents.remove(key); else cents.put(key, left);
    }

    public Map<ResourceKey<ReagentData>, Float> naiveRemove(float amount) {
        long request = units(amount, "amount"), total = total(cents);
        long actual = Math.min(request, total);
        Map<ResourceKey<ReagentData>, Long> removed = ReagentUnits.split(cents, actual);
        Map<ResourceKey<ReagentData>, Long> staged = new HashMap<>();
        for (var entry : cents.entrySet()) {
            long left = entry.getValue() - removed.getOrDefault(entry.getKey(), 0L);
            if (left != 0) staged.put(entry.getKey(), left);
        }
        replace(staged);
        Map<ResourceKey<ReagentData>, Long> positive = new HashMap<>();
        removed.forEach((key, value) -> { if (value != 0) positive.put(key, value); });
        return floatSnapshot(positive);
    }

    public void trimZeroes() { cents.entrySet().removeIf(entry -> entry.getValue() == 0); }
    public void clear() { cents.clear(); }

    /**
     * Conserved capacity-limited transfer, with every validation and staged calculation
     * completed before either attachment is mutated. Requested quantities and capacity
     * are cents; the returned value is the exact amount admitted.
     */
    public static long transferUnits(ReagentAttachment source, ReagentAttachment target,
                                     long requestedCents, long capacityCents) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        ReagentUnits.validateCents(capacityCents, "capacityCents");
        long sourceTotal = total(source.cents), targetTotal = total(target.cents);
        if (source == target) return 0;
        if (targetTotal > capacityCents) throw new IllegalArgumentException("stored total exceeds capacity");
        long admitted = Math.min(ReagentUnits.admit(requestedCents, capacityCents - targetTotal), sourceTotal);
        if (admitted == 0) return 0;

        Map<ResourceKey<ReagentData>, Long> shares = ReagentUnits.split(source.cents, admitted);
        Map<ResourceKey<ReagentData>, Long> stagedSource = new HashMap<>(source.cents);
        Map<ResourceKey<ReagentData>, Long> stagedTarget = new HashMap<>(target.cents);
        for (var entry : shares.entrySet()) {
            long amount = entry.getValue();
            if (amount == 0) continue;
            long remainder = stagedSource.get(entry.getKey()) - amount;
            if (remainder == 0) stagedSource.remove(entry.getKey()); else stagedSource.put(entry.getKey(), remainder);
            stagedTarget.put(entry.getKey(), checkedAdd(stagedTarget.getOrDefault(entry.getKey(), 0L), amount));
        }
        if (total(stagedSource) != sourceTotal - admitted || total(stagedTarget) != targetTotal + admitted)
            throw new IllegalStateException("conserved reagent transfer preflight failed");
        source.replace(stagedSource);
        target.replace(stagedTarget);
        return admitted;
    }

    public record CapacityAddResult<T>(Map<T, Float> requested, Map<T, Float> retained, Map<T, Float> excess) {
        public CapacityAddResult {
            requested = immutableFiniteMap(requested, "requested");
            retained = immutableFiniteMap(retained, "retained");
            excess = immutableFiniteMap(excess, "excess");
        }
    }

    /** Exact hundredths result for internal capacity-limited additions. */
    public record UnitsCapacityAddResult<T>(Map<T, Long> requested, Map<T, Long> retained, Map<T, Long> excess) {
        public UnitsCapacityAddResult {
            requested = immutableUnitsMap(requested, "requested");
            retained = immutableUnitsMap(retained, "retained");
            excess = immutableUnitsMap(excess, "excess");
        }
    }

    /** Capacity-safe cent-native product admission. Excess remains explicit at the routed destination. */
    public UnitsCapacityAddResult<ResourceKey<ReagentData>> addUnitsCapacitySafe(
            Map<ResourceKey<ReagentData>, Long> requested, long capacityCents) {
        Objects.requireNonNull(requested, "requested");
        ReagentUnits.validateCents(capacityCents, "capacityCents");
        long current = total(cents);
        if (current > capacityCents) throw new IllegalArgumentException("stored total exceeds capacity");
        Map<ResourceKey<ReagentData>, Long> wanted = new LinkedHashMap<>();
        requested.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(key -> key.location().toString())))
                .forEach(entry -> wanted.put(Objects.requireNonNull(entry.getKey(), "reagent key"),
                        ReagentUnits.validateCents(Objects.requireNonNull(entry.getValue(), "requested cents"), "requested cents")));
        long totalWanted = total(wanted), admitted = Math.min(totalWanted, capacityCents - current);
        Map<ResourceKey<ReagentData>, Long> shares = totalWanted == 0 ? Map.of() : ReagentUnits.split(wanted, admitted);
        Map<ResourceKey<ReagentData>, Long> staged = new HashMap<>(cents);
        Map<ResourceKey<ReagentData>, Long> retained = new LinkedHashMap<>(), excess = new LinkedHashMap<>();
        for (var entry : wanted.entrySet()) {
            long add = shares.getOrDefault(entry.getKey(), 0L);
            if (add != 0) staged.put(entry.getKey(), checkedAdd(staged.getOrDefault(entry.getKey(), 0L), add));
            retained.put(entry.getKey(), add);
            excess.put(entry.getKey(), entry.getValue() - add);
        }
        UnitsCapacityAddResult<ResourceKey<ReagentData>> result = new UnitsCapacityAddResult<>(wanted, retained, excess);
        if (total(staged) > capacityCents) throw new IllegalStateException("capacity admission exceeded capacity");
        replace(staged);
        return result;
    }

    public CapacityAddResult<ResourceKey<ReagentData>> addCapacitySafe(Map<ResourceKey<ReagentData>, Float> requested, float capacity) {
        Objects.requireNonNull(requested, "requested");
        long cap = units(capacity, "capacity"), current = total(cents);
        if (current > cap) throw new IllegalArgumentException("stored total exceeds capacity");
        Map<ResourceKey<ReagentData>, Long> wanted = ReagentUnits.fromMap(requested);
        long totalWanted = total(wanted), admitted = Math.min(totalWanted, cap - current);
        Map<ResourceKey<ReagentData>, Long> shares = totalWanted == 0 ? Map.of() : ReagentUnits.split(wanted, admitted);
        Map<ResourceKey<ReagentData>, Long> staged = new HashMap<>(cents);
        Map<ResourceKey<ReagentData>, Float> retained = new LinkedHashMap<>(), excess = new LinkedHashMap<>();
        for (var entry : wanted.entrySet()) {
            long add = shares.getOrDefault(entry.getKey(), 0L);
            if (add != 0) staged.put(entry.getKey(), checkedAdd(staged.getOrDefault(entry.getKey(), 0L), add));
            retained.put(entry.getKey(), ReagentUnits.toFloat(add));
            excess.put(entry.getKey(), ReagentUnits.toFloat(entry.getValue() - add));
        }
        CapacityAddResult<ResourceKey<ReagentData>> result = new CapacityAddResult<>(floatSnapshot(wanted), retained, excess);
        if (total(staged) > cap) throw new IllegalStateException("capacity admission exceeded capacity");
        replace(staged);
        return result;
    }

    public CapacityAddResult<ResourceKey<ReagentData>> capacitySafeAdd(Map<ResourceKey<ReagentData>, Float> requested, float capacity) { return addCapacitySafe(requested, capacity); }
    public CapacityAddResult<ResourceKey<ReagentData>> mergeAddCapacitySafe(Map<ResourceKey<ReagentData>, Float> requested, float capacity) { return addCapacitySafe(requested, capacity); }

    public void recursiveReaction(Level level, float capacity) {
        Objects.requireNonNull(level, "level"); units(capacity, "capacity");
        while (true) {
            Optional<RecipeHolder<ReactionRecipe>> found = level.getRecipeManager().getRecipeFor(
                    ModRecipes.REACTION_RECIPE_TYPE.get(), new ReactionRecipeInput(getMap()), level);
            if (found.isEmpty()) return;
            Map<ResourceKey<ReagentData>, Long> before = new HashMap<>(cents);
            resolveReaction(found.get().value(), capacity);
            if (before.equals(cents)) return;
        }
    }

    public void resolveReaction(ReactionRecipe recipe, float capacity) {
        Objects.requireNonNull(recipe, "reactionRecipe");
        long cap = units(capacity, "capacity");
        Map<ResourceKey<ReagentData>, Long> inputs = ReagentUnits.fromMap(recipe.inputs());
        Map<ResourceKey<ReagentData>, Long> outputs = ReagentUnits.fromMap(recipe.outputs());
        if (inputs.isEmpty() || inputs.values().stream().anyMatch(value -> value == 0)) return;
        // Keep the limiting reaction count as a ratio in cent-space. Each
        // product is floored independently, so fractional reaction progress
        // cannot create material and a recipe cannot loop without cent progress.
        var limiting = inputs.entrySet().stream().min((left, right) ->
                BigInteger.valueOf(cents.getOrDefault(left.getKey(), 0L)).multiply(BigInteger.valueOf(right.getValue()))
                        .compareTo(BigInteger.valueOf(cents.getOrDefault(right.getKey(), 0L)).multiply(BigInteger.valueOf(left.getValue())))).orElseThrow();
        long limitingAmount = cents.getOrDefault(limiting.getKey(), 0L);
        if (limitingAmount == 0) return;
        Map<ResourceKey<ReagentData>, BigInteger> stagedWide = new HashMap<>();
        cents.forEach((key, amount) -> stagedWide.put(key, BigInteger.valueOf(amount)));
        for (var input : inputs.entrySet()) {
            BigInteger used = multiplyFloorWide(limitingAmount, input.getValue(), limiting.getValue());
            BigInteger left = stagedWide.getOrDefault(input.getKey(), BigInteger.ZERO).subtract(used);
            if (left.signum() == 0) stagedWide.remove(input.getKey()); else stagedWide.put(input.getKey(), left);
        }
        for (var output : outputs.entrySet()) {
            BigInteger product = multiplyFloorWide(limitingAmount, output.getValue(), limiting.getValue());
            if (product.signum() != 0)
                stagedWide.merge(output.getKey(), product, BigInteger::add);
        }

        // Validate the complete hypothetical result before narrowing to supported
        // cents. Capacity and per-container maximum failures defer the whole recipe;
        // they must never trim existing material or spend its inputs.
        BigInteger wideTotal = BigInteger.ZERO;
        BigInteger max = BigInteger.valueOf(ReagentUnits.MAX_CENTS);
        for (BigInteger amount : stagedWide.values()) {
            if (amount.signum() < 0 || amount.compareTo(max) > 0) return;
            wideTotal = wideTotal.add(amount);
            if (wideTotal.compareTo(max) > 0 || wideTotal.compareTo(BigInteger.valueOf(cap)) > 0) return;
        }
        Map<ResourceKey<ReagentData>, Long> staged = new HashMap<>();
        stagedWide.forEach((key, amount) -> { if (amount.signum() != 0) staged.put(key, amount.longValueExact()); });
        replace(staged);
    }

    private static long units(float value, String name) {
        long result = ReagentUnits.fromFloat(value);
        return result;
    }
    private static long multiplyFloor(long a, long b, long denominator) {
        return BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).divide(BigInteger.valueOf(denominator)).longValueExact();
    }
    private static BigInteger multiplyFloorWide(long a, long b, long denominator) {
        return BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).divide(BigInteger.valueOf(denominator));
    }
    private static long checkedMultiply(long a, long b) { return multiplyFloor(a, b, 1); }
    private static long checkedAdd(long a, long b) {
        try { return Math.addExact(a, b); } catch (ArithmeticException ex) { throw new IllegalArgumentException("reagent quantity overflow", ex); }
    }
    private static long total(Map<?, Long> values) { return ReagentUnits.total(values.values()); }
    private void replace(Map<ResourceKey<ReagentData>, Long> replacement) { total(replacement); cents.clear(); cents.putAll(replacement); }
    private static Map<ResourceKey<ReagentData>, Float> floatSnapshot(Map<ResourceKey<ReagentData>, Long> values) {
        Map<ResourceKey<ReagentData>, Float> result = new LinkedHashMap<>();
        values.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(key -> key.location().toString())))
                .forEach(entry -> result.put(entry.getKey(), ReagentUnits.toFloat(entry.getValue())));
        return Collections.unmodifiableMap(result);
    }
    private static <T> Map<T, Float> immutableFiniteMap(Map<T, Float> values, String name) {
        Objects.requireNonNull(values, name);
        values.forEach((key, value) -> { if (key == null || value == null || !Float.isFinite(value) || value < 0) throw new IllegalArgumentException(name + " values must be finite and nonnegative"); });
        return Map.copyOf(values);
    }
    private static <T> Map<T, Long> immutableUnitsMap(Map<T, Long> values, String name) {
        Objects.requireNonNull(values, name);
        values.forEach((key, value) -> { if (key == null || value == null || value < 0 || value > ReagentUnits.MAX_CENTS) throw new IllegalArgumentException(name + " values must be valid cents"); });
        return Map.copyOf(values);
    }

    public static final Codec<ReagentAttachment> CODEC = ReagentComponent.CODEC.xmap(ReagentAttachment::new, ReagentAttachment::toComponent);
    public static final StreamCodec<RegistryFriendlyByteBuf, ReagentAttachment> STREAM_CODEC = ReagentComponent.STREAM_CODEC.map(ReagentAttachment::new, ReagentAttachment::toComponent);

    @Override public boolean equals(Object other) { return this == other || other instanceof ReagentAttachment that && cents.equals(that.cents); }
    @Override public int hashCode() { return cents.hashCode(); }
    @Override public Codec<ReagentAttachment> getCodec() { return CODEC; }
    @Override public StreamCodec<RegistryFriendlyByteBuf, ReagentAttachment> getStreamCodec() { return STREAM_CODEC; }
}
