package com.juicyslew.moonstation14.ms14.atmos.monitor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;

/** Resolved SS14 AtmosAlarmable source tags and receiver synchronization filter (not device-link ports). */
public record AtmosAlarmableData(Set<String> sourceTags, Set<String> syncWith, boolean ignoreAlarms) {
    public static final Codec<AtmosAlarmableData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("sourceTags", java.util.List.of()).forGetter(d -> d.sourceTags().stream().sorted().toList()),
            Codec.STRING.listOf().optionalFieldOf("syncWith", java.util.List.of()).forGetter(d -> d.syncWith().stream().sorted().toList()),
            Codec.BOOL.optionalFieldOf("ignoreAlarms", false).forGetter(AtmosAlarmableData::ignoreAlarms)
    ).apply(i, (sources, receivers, ignore) -> new AtmosAlarmableData(Set.copyOf(sources), Set.copyOf(receivers), ignore)));
    public AtmosAlarmableData {
        sourceTags = Set.copyOf(sourceTags);
        syncWith = Set.copyOf(syncWith);
        if (sourceTags.stream().anyMatch(String::isBlank) || syncWith.stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("atmos alarm tags cannot be blank");
    }
}
