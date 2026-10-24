package com.juicyslew.moonstation14.ms14.animals;

import com.juicyslew.moonstation14.MoonStation14;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Stable host and authored presentation contract shared with native animal renderers. */
public final class AnimalCatalog {
    public record Entry(String id, String modelId, float width, float height) {
        public Entry {
            if (id == null || modelId == null || width <= 0 || height <= 0) throw new IllegalArgumentException("invalid animal catalog entry");
        }
        public ResourceLocation host() { return ResourceLocation.fromNamespaceAndPath(MoonStation14.MOD_ID, id); }
    }
    private static final List<Entry> ENTRIES = List.of(
            e("bat",.5f,.45f),e("bee",.2f,.2f),e("butterfly",.4f,.35f),new Entry("chicken","chicken_0",.7f,.8f),
            new Entry("duck","duck_0",.7f,.65f),e("cockroach",.4f,.25f),e("mothroach",.4f,.3f),e("cow",.8f,1.4f),
            e("crab",.7f,.4f),e("goat",.7f,1.1f),e("goose",.7f,.8f),e("gorilla",2.01f,2.27f),
            e("kangaroo",.7f,1.5f),e("monkey",.7f,.9f),e("kobold",.7f,1f),e("lizard",.4f,.4f),
            e("slug",.4f,.25f),e("frog",.4f,.45f),e("parrot",.5f,.7f),e("penguin",.5f,.9f),
            e("snake",.5f,.4f),e("possum",.7f,.65f),new Entry("possum_poppy","poppy",.7f,.65f),new Entry("old_possum","possum_old",.7f,.65f),e("raccoon",.7f,.75f),
            e("fox",.7f,.8f),e("hamster",.4f,.35f),new Entry("dionanymph","nymph",.7f,.65f),e("reindeer_buck",.96f,1.4f),
            e("reindeer_doe",.96f,1.3f),e("scurret",.7f,.45f),e("snail",.4f,.3f),e("snoth",.4f,.4f),
            new Entry("giant_spider","spider_tarantula",.7f,1.2f),e("pig",.7f,.9f),e("moproach",.4f,.3f));
    private AnimalCatalog() { }
    private static Entry e(String id,float width,float height) { return new Entry(id,id,width,height); }
    public static List<Entry> entries() { return ENTRIES; }
    public static ResourceLocation host(String id) { return ENTRIES.stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow().host(); }
}
