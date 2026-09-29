package com.juicyslew.moonstation14.ms14.ui.model.animation;

import java.util.List;
import java.util.Objects;

/** Ordered, immutable layers; visibility is explicit and never inferred from state names. */
public final class LayeredSpriteAnimation {
    public record Layer(SpriteSheet sheet, boolean visible) {
        public Layer { Objects.requireNonNull(sheet, "sheet"); }
    }

    private final List<Layer> layers;

    public LayeredSpriteAnimation(List<Layer> layers) {
        Objects.requireNonNull(layers, "layers");
        if (layers.isEmpty() || layers.size() > 16) throw new IllegalArgumentException("Layer count out of bounds");
        this.layers = List.copyOf(layers);
    }

    public int size() { return layers.size(); }
    public Layer layer(int index) { return layers.get(index); }
}
