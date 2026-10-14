package com.juicyslew.moonstation14.ms14.storage.grid;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.juicyslew.moonstation14.ms14.storage.pouch.PouchContents;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Persistent, detached item-owned placements for bags and belts. */
public final class GridContents {
    private static final Codec<String> TOKEN = Codec.STRING.validate(s -> s == null || s.isBlank() || s.length() > ItemToken.MAX_LENGTH
            ? DataResult.error(() -> "invalid grid token") : DataResult.success(s));
    private static final Codec<RawEntry> RAW = RecordCodecBuilder.create(i -> i.group(
            TOKEN.fieldOf("token").forGetter(RawEntry::token), ItemStack.CODEC.fieldOf("stack").forGetter(RawEntry::stack),
            Codec.INT.fieldOf("x").forGetter(RawEntry::x), Codec.INT.fieldOf("y").forGetter(RawEntry::y),
            Codec.INT.fieldOf("rotation").forGetter(RawEntry::rotation)
    ).apply(i, RawEntry::new));
    private static final Codec<List<RawEntry>> BOUNDED_RAW = RAW.listOf(0, GridGeometry.MAX_ENTRIES);
    private static final class TooManyEntries extends RuntimeException {
        private TooManyEntries() { super("grid entry limit exceeded", null, false, false); }
    }
    public static final Codec<GridContents> CODEC = new Codec<>() {
        @Override public <T> DataResult<Pair<GridContents, T>> decode(DynamicOps<T> ops, T input) {
            // getList visits the list without decoding its children. Abort on the 33rd value,
            // before even one ItemStack or entry is decoded (including on streaming ops).
            try {
                var list = ops.getList(input).result();
                if (list.isEmpty()) return DataResult.error(() -> "invalid grid entries list");
                int[] count = {0};
                list.orElseThrow().accept(value -> {
                    if (++count[0] > GridGeometry.MAX_ENTRIES) throw new TooManyEntries();
                });
                var decoded = BOUNDED_RAW.decode(ops, input).result();
                if (decoded.isEmpty()) return DataResult.error(() -> "invalid grid entries");
                var pair = decoded.orElseThrow();
                return DataResult.success(Pair.of(new GridContents(pair.getFirst()), pair.getSecond()));
            } catch (RuntimeException ex) {
                return DataResult.error(() -> "invalid or oversized grid entries");
            }
        }

        @Override public <T> DataResult<T> encode(GridContents input, DynamicOps<T> ops, T prefix) {
            try {
                List<RawEntry> entries = input.raw();
                if (entries.size() > GridGeometry.MAX_ENTRIES)
                    return DataResult.error(() -> "grid entry limit exceeded");
                return BOUNDED_RAW.encode(entries, ops, prefix);
            } catch (RuntimeException ex) {
                return DataResult.error(() -> "invalid grid entries");
            }
        }
    };
    public static final StreamCodec<RegistryFriendlyByteBuf, GridContents> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
    private final List<Entry> entries;

    private GridContents(List<RawEntry> raw) {
        if (raw.size() > GridGeometry.MAX_ENTRIES) throw new IllegalArgumentException("grid entry limit exceeded");
        Set<String> tokens = new HashSet<>(); List<Entry> copy = new ArrayList<>();
        for (RawEntry e : raw) {
            if (!tokens.add(e.token())) throw new IllegalArgumentException("duplicate grid token");
            Entry value = new Entry(e.token(), e.stack(), e.x(), e.y(), rotation(e.rotation()));
            copy.add(value);
        }
        // Decoding may run on clients without the authoritative footprint catalog. Keep
        // construction structural; live hosts are checked against exact server shapes in read().
        if (copy.stream().anyMatch(e -> e.x() < 0 || e.y() < 0
                || e.x() >= GridGeometry.MAX_WIDTH || e.y() >= GridGeometry.MAX_HEIGHT))
            throw new IllegalArgumentException("invalid grid anchor");
        entries = List.copyOf(copy);
    }
    private record RawEntry(String token, ItemStack stack, int x, int y, int rotation) { }
    public record Entry(String token, ItemStack stack, int x, int y, GridGeometry.Rotation rotation) {
        public Entry {
            if (token == null || token.isBlank() || token.length() > ItemToken.MAX_LENGTH || stack == null || stack.isEmpty()
                    || stack.getCount() < 1 || stack.getCount() > stack.getMaxStackSize()
                    || "moonstation14:pouch".equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                    || stack.is(ModItems.BAG) || stack.is(ModItems.BELT)
                    || foreign(stack) || stack.has(ModDataComponents.POUCH_CONTENTS.get()) || stack.has(ModDataComponents.GRID_CONTENTS.get()))
                throw new IllegalArgumentException("invalid grid child");
            stack = stack.copy(); Objects.requireNonNull(rotation);
        }
        @Override public ItemStack stack() { return stack.copy(); }
    }
    public static GridContents empty() { return new GridContents(List.<RawEntry>of()); }
    public static GridContents of(List<Entry> entries) { return new GridContents(entries.stream().map(e -> new RawEntry(e.token(), e.stack(), e.x(), e.y(), e.rotation().degrees())).toList()); }
    public List<Entry> entries() { return entries.stream().map(e -> new Entry(e.token(), e.stack(), e.x(), e.y(), e.rotation())).toList(); }
    private List<RawEntry> raw() { return entries.stream().map(e -> new RawEntry(e.token(), e.stack(), e.x(), e.y(), e.rotation().degrees())).toList(); }

    /** A read adapter only: legacy contents are represented virtually and the source is untouched. */
    public static Optional<GridContents> read(ItemStack host) {
        if (host == null || host.isEmpty() || host.getCount() != 1 || (!host.is(ModItems.BAG) && !host.is(ModItems.BELT)) || foreign(host)) return Optional.empty();
        boolean grid = host.has(ModDataComponents.GRID_CONTENTS.get()), legacy = host.has(ModDataComponents.POUCH_CONTENTS.get());
        if (grid && legacy) return Optional.empty();
        int w = host.is(ModItems.BAG) ? 7 : 8, h = host.is(ModItems.BAG) ? 4 : 2;
        GridContents value = grid ? host.get(ModDataComponents.GRID_CONTENTS.get()) : empty();
        if (value == null) return Optional.empty();
        if (!grid && legacy) {
            Optional<PouchContents> old = PouchContents.read(host);
            if (old.isEmpty()) return Optional.empty();
            Optional<PouchContents.Child> child = old.orElseThrow().child();
            if (child.isPresent()) {
                var c = child.orElseThrow();
                try { value = of(List.of(new Entry(c.token(), c.stack(), 0, 0, GridGeometry.Rotation.DEG_0))); }
                catch (IllegalArgumentException ex) { return Optional.empty(); }
            }
        }
        if (!valid(value, w, h)) return Optional.empty();
        return Optional.of(value);
    }
    /** Explicit copy-on-write migration; callers should invoke only as part of an authorized write. */
    public static Optional<ItemStack> applyCopyWithGrid(ItemStack host, GridContents value) {
        if (read(host).isEmpty() || host.has(ModDataComponents.GRID_CONTENTS.get()) && host.has(ModDataComponents.POUCH_CONTENTS.get())) return Optional.empty();
        int w = host.is(ModItems.BAG) ? 7 : 8, h = host.is(ModItems.BAG) ? 4 : 2;
        if (!valid(value, w, h)) return Optional.empty();
        ItemStack result = host.copy(); result.set(ModDataComponents.GRID_CONTENTS.get(), value); result.remove(ModDataComponents.POUCH_CONTENTS.get());
        return Optional.of(result);
    }
    private static boolean valid(GridContents contents, int w, int h) {
        return validEntries(contents.entries, w, h);
    }
    private static boolean validEntries(List<Entry> entries, int w, int h) {
        // Empty grids have no geometry to validate and remain usable during bootstrap.
        // Never interpret an unloaded authoritative catalog's fallback ONE as a real profile.
        if (!entries.isEmpty() && ModItemFootprints.serverCatalog().size() == 0) return false;
        var snapshot = GridGeometry.Snapshot.<String>empty(GridGeometry.StorageMask.rectangle(w, h));
        for (Entry e : entries) {
            var result = snapshot.place(e.token(), ItemFootprints.forStack(e.stack()), new GridGeometry.Cell(e.x(), e.y()), e.rotation());
            if (!result.accepted()) return false; snapshot = result.snapshot();
        }
        return true;
    }
    private static GridGeometry.Rotation rotation(int degrees) {
        for (var r : GridGeometry.Rotation.values()) if (r.degrees() == degrees) return r;
        throw new IllegalArgumentException("invalid grid rotation");
    }
    private static boolean foreign(ItemStack stack) {
        return stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.CONTAINER_LOOT) || stack.has(DataComponents.BUNDLE_CONTENTS)
                || stack.has(DataComponents.CHARGED_PROJECTILES) || stack.has(DataComponents.BLOCK_ENTITY_DATA) || stack.has(DataComponents.ENTITY_DATA)
                || stack.has(DataComponents.BUCKET_ENTITY_DATA) || stack.has(DataComponents.BEES)
                || stack.has(DataComponents.FOOD) && stack.get(DataComponents.FOOD).usingConvertsTo().isPresent();
    }
    @Override public boolean equals(Object o) {
        if (!(o instanceof GridContents g) || entries.size() != g.entries.size()) return false;
        for (int i = 0; i < entries.size(); i++) {
            Entry a = entries.get(i), b = g.entries.get(i);
            if (!a.token().equals(b.token()) || a.x() != b.x() || a.y() != b.y()
                    || a.rotation() != b.rotation() || !ItemStack.matches(a.stack(), b.stack())) return false;
        }
        return true;
    }
    // ItemStack.matches compares components while ItemStack.hashCode is identity based.
    // A constant hash preserves the equals/hashCode contract for detached stack copies.
    @Override public int hashCode() { return 0; }
}

