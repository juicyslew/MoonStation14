package com.juicyslew.moonstation14.ms14.chemistry.output;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/** Supported projection of chemistry-container solution/storage components. */
public record ChemContainerData(Optional<Solution> solution, Optional<Storage> storage) {
    public record Solution(String name, int capacity, boolean canReact) {
        public static final Codec<Solution> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("name", "food").forGetter(Solution::name),
                Codec.intRange(1, 10000).optionalFieldOf("capacity", 20).forGetter(Solution::capacity),
                Codec.BOOL.optionalFieldOf("can_react", false).forGetter(Solution::canReact)
        ).apply(i, Solution::new));
        public Solution { if (name == null || name.isBlank() || capacity < 1) throw new IllegalArgumentException("invalid solution profile"); }
    }
    public record Storage(Optional<Integer> width, Optional<Integer> height, Optional<String> whitelistItem) {
        public static final Codec<Storage> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 8).optionalFieldOf("width").forGetter(Storage::width),
                Codec.intRange(1, 8).optionalFieldOf("height").forGetter(Storage::height),
                Codec.STRING.optionalFieldOf("whitelist_item").forGetter(Storage::whitelistItem)
        ).apply(i, Storage::new));
        public Storage { if (width == null || height == null || whitelistItem == null) throw new IllegalArgumentException("invalid storage profile"); }
    }
    public static final Codec<ChemContainerData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Solution.CODEC.optionalFieldOf("solution").forGetter(ChemContainerData::solution),
            Storage.CODEC.optionalFieldOf("storage").forGetter(ChemContainerData::storage)
    ).apply(i, ChemContainerData::new));
    public ChemContainerData { if (solution == null || storage == null) throw new IllegalArgumentException("missing container profile"); }
}
