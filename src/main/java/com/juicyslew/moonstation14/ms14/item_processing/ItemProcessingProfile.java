package com.juicyslew.moonstation14.ms14.item_processing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/** Narrow resolved composition for source LogComponent and the consumed SliceableFood behavior. */
public record ItemProcessingProfile(Log log, SliceableFood sliceableFood, Butcherable butcherable, SpawnItemsOnUse spawnItemsOnUse) {
    public ItemProcessingProfile(Log log, SliceableFood sliceableFood, Butcherable butcherable) { this(log, sliceableFood, butcherable, null); }
    public ItemProcessingProfile(Log log) { this(log, null, null); }
    public ItemProcessingProfile(Log log, SliceableFood sliceableFood) { this(log, sliceableFood, null); }
    public record ItemOutput(ResourceLocation output, int count) {
        public static final Codec<ItemOutput> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("output").forGetter(ItemOutput::output),
                Codec.intRange(1, 65535).optionalFieldOf("count", 1).forGetter(ItemOutput::count)
        ).apply(i, ItemOutput::new));
        public void validate() {
            validateStructure();
            var item = BuiltInRegistries.ITEM.get(output);
            if (item == null || item == net.minecraft.world.item.Items.AIR || !output.equals(BuiltInRegistries.ITEM.getKey(item)))
                throw new IllegalArgumentException("unknown item output: " + output);
        }
        public void validateStructure() {
            if (output == null || count <= 0 || count > 65535) throw new IllegalArgumentException("invalid item output/count");
        }
    }
    /** Supported source Butcherable composition. Knife is the only locally supported butcher type. */
    public record Butcherable(java.util.List<ItemOutput> outputs, float delaySeconds, Type type) {
        public enum Type { KNIFE }
        public static final Codec<Butcherable> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemOutput.CODEC.listOf().fieldOf("outputs").forGetter(Butcherable::outputs),
                Codec.floatRange(0f, Float.MAX_VALUE).optionalFieldOf("delay_seconds", 8f).forGetter(Butcherable::delaySeconds),
                Codec.STRING.xmap(s -> Type.valueOf(s.toUpperCase(java.util.Locale.ROOT)), t -> t.name().toLowerCase(java.util.Locale.ROOT))
                        .optionalFieldOf("type", Type.KNIFE).forGetter(Butcherable::type)
        ).apply(i, Butcherable::new));
        public void validate() {
            if (outputs == null || outputs.isEmpty() || outputs.stream().anyMatch(java.util.Objects::isNull)
                    || !Float.isFinite(delaySeconds) || delaySeconds <= 0 || type != Type.KNIFE)
                throw new IllegalArgumentException("invalid Butcherable composition");
            outputs.forEach(ItemOutput::validate);
        }
    }
    /** Bounded Minecraft adaptation of source SpawnItemsOnUse: single-use only. */
    public record SpawnItemsOnUse(java.util.List<ItemOutput> outputs, int uses, ResourceLocation sound) {
        public static final Codec<SpawnItemsOnUse> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemOutput.CODEC.listOf().fieldOf("outputs").forGetter(SpawnItemsOnUse::outputs),
                Codec.intRange(1, 65535).optionalFieldOf("uses", 1).forGetter(SpawnItemsOnUse::uses),
                ResourceLocation.CODEC.optionalFieldOf("sound").forGetter(p -> java.util.Optional.ofNullable(p.sound()))
        ).apply(i, (outputs, uses, sound) -> new SpawnItemsOnUse(outputs, uses, sound.orElse(null))));
        public void validate() {
            if (outputs == null || outputs.isEmpty() || outputs.stream().anyMatch(java.util.Objects::isNull) || uses != 1)
                throw new IllegalArgumentException("SpawnItemsOnUse supports nonempty outputs and exactly one use");
            long total = 0;
            for (var output : outputs) { output.validate(); total += output.count(); }
            if (total > 65535) throw new IllegalArgumentException("SpawnItemsOnUse output count exceeds 65535");
        }
    }
    public record SliceableFood(ResourceLocation output, int count, float delaySeconds) {
        public static final Codec<SliceableFood> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("output").forGetter(SliceableFood::output),
                Codec.intRange(1, 65535).optionalFieldOf("count", 5).forGetter(SliceableFood::count),
                Codec.floatRange(0f, Float.MAX_VALUE).optionalFieldOf("delay_seconds", 1f).forGetter(SliceableFood::delaySeconds)
        ).apply(i, SliceableFood::new));
        public void validate() {
            if (output == null || count <= 0 || count > 65535 || !Float.isFinite(delaySeconds) || delaySeconds < 0)
                throw new IllegalArgumentException("invalid SliceableFood values");
            var item = BuiltInRegistries.ITEM.get(output);
            if (item == null || item == net.minecraft.world.item.Items.AIR
                    || !output.equals(BuiltInRegistries.ITEM.getKey(item)))
                throw new IllegalArgumentException("unknown SliceableFood output item: " + output);
        }
    }
    private static final ResourceLocation DEFAULT_LOG_OUTPUT =
            ResourceLocation.fromNamespaceAndPath("moonstation14", "wood");
    public record Log(ResourceLocation output, int count) {
        public static final Codec<Log> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("output", DEFAULT_LOG_OUTPUT).forGetter(Log::output),
                Codec.INT.optionalFieldOf("count", 2).forGetter(Log::count)
        ).apply(i, Log::new));
        public void validate() {
            if (output == null || count <= 0) throw new IllegalArgumentException("invalid Log output/count");
            var item = BuiltInRegistries.ITEM.get(output);
            if (item == null || item == net.minecraft.world.item.Items.AIR
                    || !output.equals(BuiltInRegistries.ITEM.getKey(item)))
                throw new IllegalArgumentException("unknown Log output item: " + output);
        }
    }
    public static final Codec<ItemProcessingProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Log.CODEC.optionalFieldOf("log").forGetter(p -> java.util.Optional.ofNullable(p.log())),
            SliceableFood.CODEC.optionalFieldOf("sliceable_food").forGetter(p -> java.util.Optional.ofNullable(p.sliceableFood()))
            , Butcherable.CODEC.optionalFieldOf("butcherable").forGetter(p -> java.util.Optional.ofNullable(p.butcherable())),
            SpawnItemsOnUse.CODEC.optionalFieldOf("spawn_items_on_use").forGetter(p -> java.util.Optional.ofNullable(p.spawnItemsOnUse()))
    ).apply(i, (log, slice, butcher, spawn) -> new ItemProcessingProfile(log.orElse(null), slice.orElse(null), butcher.orElse(null), spawn.orElse(null))));
    public void validate() { if (log != null) log.validate(); if (sliceableFood != null) sliceableFood.validate(); if (butcherable != null) butcherable.validate(); if (spawnItemsOnUse != null) spawnItemsOnUse.validate(); }
}
