package com.juicyslew.moonstation14.ms14.devicenet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/** Catalog definition for a reusable source/sink port declaration. */
public record DevicePortPrototype(String portId, String name, String description, Direction direction, List<String> defaultLinks) {
    public enum Direction { SOURCE, SINK }
    public static final Codec<DevicePortPrototype> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("portId").forGetter(DevicePortPrototype::portId),
            Codec.STRING.fieldOf("name").forGetter(DevicePortPrototype::name),
            Codec.STRING.fieldOf("description").forGetter(DevicePortPrototype::description),
            Codec.STRING.xmap(Direction::valueOf, Direction::name).fieldOf("direction").forGetter(DevicePortPrototype::direction),
            Codec.STRING.listOf().optionalFieldOf("defaultLinks", List.of()).forGetter(DevicePortPrototype::defaultLinks)
    ).apply(i, DevicePortPrototype::new));
    public DevicePortPrototype {
        if (portId == null || portId.isBlank() || name == null || name.isBlank() || description == null || direction == null || defaultLinks == null)
            throw new IllegalArgumentException("invalid port prototype");
        defaultLinks=List.copyOf(defaultLinks);
    }
    public DevicePort port() { return new DevicePort(portId, name, description, defaultLinks); }
}

