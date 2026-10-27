package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;
import java.util.Set;

/** Resolved, supported subset of SS14 gun and revolver-ammunition-provider composition. */
public record GunProfile(GunData gun, Optional<RevolverAmmoProviderData> revolverAmmoProvider, boolean pacifismAllowed, boolean ammoCounter) {
    public GunProfile(GunData gun, Optional<RevolverAmmoProviderData> revolverAmmoProvider) { this(gun, revolverAmmoProvider, false, false); }
    public GunProfile(GunData gun, Optional<RevolverAmmoProviderData> revolverAmmoProvider, boolean pacifismAllowed) { this(gun, revolverAmmoProvider, pacifismAllowed, false); }
    public static final Codec<GunProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            GunData.CODEC.fieldOf("gun").forGetter(GunProfile::gun),
            RevolverAmmoProviderData.CODEC.optionalFieldOf("revolver_ammo_provider").forGetter(GunProfile::revolverAmmoProvider),
            Codec.BOOL.optionalFieldOf("pacifism_allowed", false).forGetter(GunProfile::pacifismAllowed),
            Codec.BOOL.optionalFieldOf("ammo_counter", false).forGetter(GunProfile::ammoCounter)
    ).apply(i, GunProfile::new));
    public GunProfile {
        if (gun == null) throw new IllegalArgumentException("gun data is required");
        revolverAmmoProvider = revolverAmmoProvider == null ? Optional.empty() : revolverAmmoProvider;
    }
    public record GunData(float fireRate, float projectileSpeed, boolean useKey, String firingMode,
                           Optional<PositionalSoundCue> gunshotSound, Optional<Float> useDelaySeconds, SpreadData spread,
                            float burstCooldownSeconds, Optional<PositionalSoundCue> emptySound, float cameraRecoilScalar) {
        public static final PositionalSoundCue SOURCE_EMPTY_SOUND = new PositionalSoundCue(
                ResourceLocation.fromNamespaceAndPath("moonstation14", "ss14/weapons/guns/empty"), 0f, 0f);
        public GunData(float fireRate, float projectileSpeed, boolean useKey, String firingMode,
                       Optional<PositionalSoundCue> gunshotSound, Optional<Float> useDelaySeconds) {
            this(fireRate, projectileSpeed, useKey, firingMode, gunshotSound, useDelaySeconds, SpreadData.SOURCE_DEFAULTS, .25f, Optional.of(SOURCE_EMPTY_SOUND), 1f);
        }
        public GunData(float fireRate, float projectileSpeed, boolean useKey, String firingMode,
                       Optional<PositionalSoundCue> gunshotSound, Optional<Float> useDelaySeconds, SpreadData spread) {
            this(fireRate, projectileSpeed, useKey, firingMode, gunshotSound, useDelaySeconds, spread, .25f, Optional.of(SOURCE_EMPTY_SOUND), 1f);
        }
        public GunData(float fireRate, float projectileSpeed, boolean useKey, String firingMode, Optional<PositionalSoundCue> gunshotSound, Optional<Float> useDelaySeconds, SpreadData spread, float burstCooldownSeconds, Optional<PositionalSoundCue> emptySound) { this(fireRate, projectileSpeed, useKey, firingMode, gunshotSound, useDelaySeconds, spread, burstCooldownSeconds, emptySound, 1f); }
        public static final Codec<GunData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("fire_rate").forGetter(GunData::fireRate),
                Codec.FLOAT.fieldOf("projectile_speed").forGetter(GunData::projectileSpeed),
                Codec.BOOL.fieldOf("use_key").forGetter(GunData::useKey),
                Codec.STRING.fieldOf("firing_mode").forGetter(GunData::firingMode),
                PositionalSoundCue.codec(0f, 0f).optionalFieldOf("gunshot_sound").forGetter(GunData::gunshotSound),
                Codec.FLOAT.optionalFieldOf("use_delay_seconds").forGetter(GunData::useDelaySeconds),
                SpreadData.CODEC.optionalFieldOf("spread", SpreadData.SOURCE_DEFAULTS).forGetter(GunData::spread),
                Codec.FLOAT.optionalFieldOf("burst_cooldown_seconds", .25f).forGetter(GunData::burstCooldownSeconds),
                PositionalSoundCue.codec(0f, 0f).optionalFieldOf("empty_sound").forGetter(GunData::emptySound),
                Codec.FLOAT.optionalFieldOf("camera_recoil_scalar", 1f).forGetter(GunData::cameraRecoilScalar)
        ).apply(i, GunData::new));
        public GunData {
            if (!Float.isFinite(fireRate) || fireRate <= 0 || !Float.isFinite(projectileSpeed) || projectileSpeed <= 0 || !Float.isFinite(cameraRecoilScalar) || cameraRecoilScalar < 0)
                throw new IllegalArgumentException("gun rate and projectile speed must be finite and positive");
            if (!"SEMI_AUTO".equals(firingMode)) throw new IllegalArgumentException("unsupported firing mode");
            gunshotSound = gunshotSound == null ? Optional.empty() : gunshotSound;
            useDelaySeconds = useDelaySeconds == null ? Optional.empty() : useDelaySeconds;
            spread = spread == null ? SpreadData.SOURCE_DEFAULTS : spread;
            emptySound = emptySound == null || emptySound.isEmpty() ? Optional.of(SOURCE_EMPTY_SOUND) : emptySound;
            if (!Float.isFinite(burstCooldownSeconds) || burstCooldownSeconds < 0)
                throw new IllegalArgumentException("burst cooldown must be finite and nonnegative");
            if (useDelaySeconds.isPresent() && (!Float.isFinite(useDelaySeconds.get()) || useDelaySeconds.get() < 0))
                throw new IllegalArgumentException("use delay must be finite and nonnegative");
        }
    }
    public record SpreadData(float minimumDegrees, float maximumDegrees, float angleIncreaseDegrees, float angleDecayDegreesPerSecond) {
        public static final SpreadData SOURCE_DEFAULTS = new SpreadData(1f, 2f, .5f, 4f);
        public static final Codec<SpreadData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("minimum_degrees", 1f).forGetter(SpreadData::minimumDegrees),
                Codec.FLOAT.optionalFieldOf("maximum_degrees", 2f).forGetter(SpreadData::maximumDegrees),
                Codec.FLOAT.optionalFieldOf("angle_increase_degrees", .5f).forGetter(SpreadData::angleIncreaseDegrees),
                Codec.FLOAT.optionalFieldOf("angle_decay_degrees_per_second", 4f).forGetter(SpreadData::angleDecayDegreesPerSecond)
        ).apply(i, SpreadData::new));
        public SpreadData {
            if (!Float.isFinite(minimumDegrees) || !Float.isFinite(maximumDegrees) || !Float.isFinite(angleIncreaseDegrees)
                    || !Float.isFinite(angleDecayDegreesPerSecond) || minimumDegrees < 0 || maximumDegrees < minimumDegrees
                    || angleIncreaseDegrees < 0 || angleDecayDegreesPerSecond < 0) throw new IllegalArgumentException("invalid gun spread");
        }
    }
    public record RevolverAmmoProviderData(int capacity, ResourceLocation fillCartridgePrototype,
                                             Set<ResourceLocation> acceptedCartridgeTags,
                                              Set<ResourceLocation> acceptedSpeedloaderTags,
                                              Optional<PositionalSoundCue> ejectSound,
                                              Optional<PositionalSoundCue> spinSound,
                                              Optional<PositionalSoundCue> insertSound) {
        public static final PositionalSoundCue SOURCE_EJECT_SOUND = new PositionalSoundCue(ResourceLocation.fromNamespaceAndPath("moonstation14", "ss14/weapons/guns/magout/revolver_magout"), 0f, 0f);
        public static final PositionalSoundCue SOURCE_SPIN_SOUND = new PositionalSoundCue(ResourceLocation.fromNamespaceAndPath("moonstation14", "ss14/weapons/guns/misc/revolver_spin"), 0f, 0f);
        public static final PositionalSoundCue SOURCE_INSERT_SOUND = new PositionalSoundCue(ResourceLocation.fromNamespaceAndPath("moonstation14", "ss14/weapons/guns/magin/revolver_magin"), 0f, 0f);
        public RevolverAmmoProviderData(int capacity, ResourceLocation fill, Set<ResourceLocation> cartridges, Set<ResourceLocation> loaders) {
            this(capacity, fill, cartridges, loaders, Optional.of(SOURCE_EJECT_SOUND), Optional.of(SOURCE_SPIN_SOUND), Optional.of(SOURCE_INSERT_SOUND));
        }
        public RevolverAmmoProviderData(int capacity, ResourceLocation fill, Set<ResourceLocation> cartridges, Set<ResourceLocation> loaders,
                                       Optional<PositionalSoundCue> eject, Optional<PositionalSoundCue> spin) {
            this(capacity, fill, cartridges, loaders, eject, spin, Optional.of(SOURCE_INSERT_SOUND));
        }
        public static final Codec<RevolverAmmoProviderData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("capacity").forGetter(RevolverAmmoProviderData::capacity),
                ResourceLocation.CODEC.fieldOf("fill_cartridge_prototype").forGetter(RevolverAmmoProviderData::fillCartridgePrototype),
                ResourceLocation.CODEC.listOf().fieldOf("accepted_cartridge_tags").forGetter((RevolverAmmoProviderData v) -> v.acceptedCartridgeTags.stream().toList()),
                ResourceLocation.CODEC.listOf().fieldOf("accepted_speedloader_tags").forGetter((RevolverAmmoProviderData v) -> v.acceptedSpeedloaderTags.stream().toList()),
                PositionalSoundCue.codec(0f, 0f).optionalFieldOf("eject_sound", SOURCE_EJECT_SOUND).forGetter(v -> v.ejectSound.orElse(SOURCE_EJECT_SOUND)),
                PositionalSoundCue.codec(0f, 0f).optionalFieldOf("spin_sound", SOURCE_SPIN_SOUND).forGetter(v -> v.spinSound.orElse(SOURCE_SPIN_SOUND)),
                PositionalSoundCue.codec(0f, 0f).optionalFieldOf("insert_sound", SOURCE_INSERT_SOUND).forGetter(v -> v.insertSound.orElse(SOURCE_INSERT_SOUND))
        ).apply(i, (capacity, fill, cartridges, loaders, eject, spin, insert) -> new RevolverAmmoProviderData(capacity, fill, Set.copyOf(cartridges), Set.copyOf(loaders), Optional.of(eject), Optional.of(spin), Optional.of(insert))));
        public RevolverAmmoProviderData {
            if (capacity < 1 || fillCartridgePrototype == null) throw new IllegalArgumentException("invalid revolver ammo provider");
            acceptedCartridgeTags = Set.copyOf(acceptedCartridgeTags); acceptedSpeedloaderTags = Set.copyOf(acceptedSpeedloaderTags);
            ejectSound = ejectSound == null ? Optional.empty() : ejectSound; spinSound = spinSound == null ? Optional.empty() : spinSound;
            insertSound = insertSound == null ? Optional.empty() : insertSound;
        }
    }
}
