package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;

/** Resolved physical magazine/speedloader contents and compatibility. Cells use BatteryProfile. */
public record AmmoContainerProfile(int capacity, Set<ResourceLocation> acceptedTags, Set<ResourceLocation> magazineTags, ResourceLocation fillCartridgePrototype, PositionalSoundCue insertSound) {
    public AmmoContainerProfile(int capacity, Set<ResourceLocation> acceptedTags) { this(capacity, acceptedTags, Set.of(), null, GunSoundDefaults.cue("magin/bullet_insert")); }
    public AmmoContainerProfile(int capacity, Set<ResourceLocation> acceptedTags, ResourceLocation fill) { this(capacity, acceptedTags, Set.of(), fill, GunSoundDefaults.cue("magin/bullet_insert")); }
    public AmmoContainerProfile(int capacity, Set<ResourceLocation> acceptedTags, Set<ResourceLocation> magazineTags, ResourceLocation fill) { this(capacity, acceptedTags, magazineTags, fill, GunSoundDefaults.cue("magin/bullet_insert")); }
    public static final Codec<AmmoContainerProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("capacity").forGetter(AmmoContainerProfile::capacity),
            ResourceLocation.CODEC.listOf().fieldOf("accepted_tags").xmap(Set::copyOf, s -> s.stream().toList()).forGetter(AmmoContainerProfile::acceptedTags),
            ResourceLocation.CODEC.listOf().optionalFieldOf("magazine_tags", java.util.List.of()).xmap(Set::copyOf, s -> s.stream().toList()).forGetter(AmmoContainerProfile::magazineTags),
            ResourceLocation.CODEC.optionalFieldOf("fill_cartridge_prototype", null).forGetter(AmmoContainerProfile::fillCartridgePrototype),
            PositionalSoundCue.codec(0f, 0f).optionalFieldOf("insert_sound", GunSoundDefaults.cue("magin/bullet_insert")).forGetter(AmmoContainerProfile::insertSound)
    ).apply(i, AmmoContainerProfile::new));
    public AmmoContainerProfile { acceptedTags=Set.copyOf(acceptedTags); magazineTags=Set.copyOf(magazineTags); if(capacity<1) throw new IllegalArgumentException("capacity must be positive"); }
}
