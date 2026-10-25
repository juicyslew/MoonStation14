package com.juicyslew.moonstation14.ms14.hands;

import com.juicyslew.moonstation14.ms14.mob.mouse.PortableMouseIconTextures;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/** Registered source-icon adapters for non-mouse portable bodies. Unknown visual keys intentionally have no fallback. */
public final class PortableBodyIconTextures {
    private record Source(String folder, int aliveW, int aliveH, int criticalW, int criticalH, int deadW, int deadH) { }
    private static final Map<String, Source> SOURCES = Map.of(
            "hamster", new Source("hamster", 32, 32, 32, 32, 32, 32),
            "bee", new Source("bee", 96, 96, 32, 32, 32, 32),
            "butterfly", new Source("butterfly", 128, 128, 32, 32, 32, 32),
            "cockroach", new Source("cockroach", 32, 32, 32, 32, 32, 32),
            "mothroach", new Source("mothroach", 32, 32, 64, 64, 64, 64),
            "moproach", new Source("moproach", 32, 32, 64, 64, 64, 64));
    private PortableBodyIconTextures() { }
    public static boolean isRegistered(String key) { return key != null && SOURCES.containsKey(key); }
    public static PortableMouseIconTextures.Frame frame(PortableBodyAppearance view) {
        Source source = SOURCES.get(view.visualKey());
        if (source == null) return null;
        String state = switch (view.state()) { case ALIVE -> "alive"; case CRITICAL -> "critical"; case DEAD -> "dead"; };
        int width = switch (view.state()) { case ALIVE -> source.aliveW(); case CRITICAL -> source.criticalW(); case DEAD -> source.deadW(); };
        int height = switch (view.state()) { case ALIVE -> source.aliveH(); case CRITICAL -> source.criticalH(); case DEAD -> source.deadH(); };
        var id = ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/entity/portable/" + source.folder() + "/" + state + ".png");
        return new PortableMouseIconTextures.Frame(id, 0, 0, 32, 32, width, height, 16, 16);
    }
}
