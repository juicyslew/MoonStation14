package com.juicyslew.moonstation14.ms14.combat.client;

import net.minecraft.resources.ResourceLocation;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Presentation cache; captures are valid only in their render frame. */
public final class ActualMuzzleFrameCache {
    private record Key(ResourceLocation dimension, UUID body, String hand, ResourceLocation item, long revision) { }
    private final Map<Key, ActualMuzzleFrame> frames = new HashMap<>();
    private long currentFrame = Long.MIN_VALUE;

    public void beginFrame(long frame) {
        frames.clear(); currentFrame = frame;
    }
    public void put(ResourceLocation dimension, UUID body, String hand, ResourceLocation item,
                    long revision, ActualMuzzleFrame value) {
        if (value.frame() == currentFrame) frames.put(new Key(dimension, body, hand, item, revision), value);
    }
    public Optional<ActualMuzzleFrame> get(ResourceLocation dimension, UUID body, String hand,
                                           ResourceLocation item, long revision, long frame) {
        if (frame != currentFrame) return Optional.empty();
        return Optional.ofNullable(frames.get(new Key(dimension, body, hand, item, revision)));
    }
    public void clear() { frames.clear(); currentFrame = Long.MIN_VALUE; }
}
