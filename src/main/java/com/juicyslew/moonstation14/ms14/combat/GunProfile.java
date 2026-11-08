package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;
import java.util.Set;

/** Resolved, supported subset of SS14 gun and revolver-ammunition-provider composition. */
public record GunProfile(GunData gun, Optional<RevolverAmmoProviderData> revolverAmmoProvider, boolean pacifismAllowed, boolean ammoCounter,
                         Optional<MagazineChamberProviderData> magazineChamberProvider,
                         Optional<BallisticProviderData> ballisticProvider,
                         Optional<BatteryAmmoProviderData> batteryAmmoProvider,
                         Optional<WieldBonusData> wieldBonus, Optional<WieldableData> wieldable, boolean requiresWield, float gunSpreadModifier,
                         Optional<CellSlotData> cellSlot, Optional<java.util.List<BatteryFireModeData>> batteryFireModes) {
    public GunProfile(GunData gun, Optional<RevolverAmmoProviderData> revolverAmmoProvider) { this(gun, revolverAmmoProvider, false, false); }
    public GunProfile(GunData gun, Optional<RevolverAmmoProviderData> revolverAmmoProvider, boolean pacifismAllowed) { this(gun, revolverAmmoProvider, pacifismAllowed, false); }
    public GunProfile(GunData gun, Optional<RevolverAmmoProviderData> revolverAmmoProvider, boolean pacifismAllowed, boolean ammoCounter) {
        this(gun, revolverAmmoProvider, pacifismAllowed, ammoCounter, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), false, 1f, Optional.empty(), Optional.empty());
    }
    public static final Codec<GunProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            GunData.CODEC.fieldOf("gun").forGetter(GunProfile::gun),
            RevolverAmmoProviderData.CODEC.optionalFieldOf("revolver_ammo_provider").forGetter(GunProfile::revolverAmmoProvider),
            Codec.BOOL.optionalFieldOf("pacifism_allowed", false).forGetter(GunProfile::pacifismAllowed),
            Codec.BOOL.optionalFieldOf("ammo_counter", false).forGetter(GunProfile::ammoCounter),
            MagazineChamberProviderData.CODEC.optionalFieldOf("magazine_chamber_provider").forGetter(GunProfile::magazineChamberProvider),
            BallisticProviderData.CODEC.optionalFieldOf("ballistic_provider").forGetter(GunProfile::ballisticProvider),
            BatteryAmmoProviderData.CODEC.optionalFieldOf("battery_ammo_provider").forGetter(GunProfile::batteryAmmoProvider),
            WieldBonusData.CODEC.optionalFieldOf("wield_bonus").forGetter(GunProfile::wieldBonus),
            WieldableData.CODEC.optionalFieldOf("wieldable").forGetter(GunProfile::wieldable),
            Codec.BOOL.optionalFieldOf("requires_wield", false).forGetter(GunProfile::requiresWield),
            Codec.FLOAT.optionalFieldOf("gun_spread_modifier", 1f).forGetter(GunProfile::gunSpreadModifier),
            CellSlotData.CODEC.optionalFieldOf("cell_slot").forGetter(GunProfile::cellSlot),
            BatteryFireModeData.CODEC.listOf().optionalFieldOf("battery_fire_modes").forGetter(GunProfile::batteryFireModes)
    ).apply(i, GunProfile::new));
    public GunProfile {
        if (gun == null) throw new IllegalArgumentException("gun data is required");
        revolverAmmoProvider = revolverAmmoProvider == null ? Optional.empty() : revolverAmmoProvider;
        magazineChamberProvider = magazineChamberProvider == null ? Optional.empty() : magazineChamberProvider;
        ballisticProvider = ballisticProvider == null ? Optional.empty() : ballisticProvider;
        batteryAmmoProvider = batteryAmmoProvider == null ? Optional.empty() : batteryAmmoProvider;
        wieldBonus = wieldBonus == null ? Optional.empty() : wieldBonus;
        wieldable = wieldable == null ? Optional.empty() : wieldable;
        cellSlot=cellSlot==null?Optional.empty():cellSlot;
        batteryFireModes=batteryFireModes==null?Optional.empty():batteryFireModes.map(java.util.List::copyOf);
        if(batteryFireModes.isPresent()&&batteryFireModes.get().isEmpty())throw new IllegalArgumentException("battery fire modes cannot be empty");
        if (!Float.isFinite(gunSpreadModifier) || gunSpreadModifier < 0) throw new IllegalArgumentException("invalid spread modifier");
        int providers = (revolverAmmoProvider.isPresent()?1:0)+(magazineChamberProvider.isPresent()?1:0)+(ballisticProvider.isPresent()?1:0)+(batteryAmmoProvider.isPresent()?1:0);
        if (providers > 1) throw new IllegalArgumentException("gun must have exactly one ammo provider family");
    }
    public record CellSlotData(ResourceLocation slotId,Set<ResourceLocation> acceptedTags,Optional<ResourceLocation> startingCell, Optional<PositionalSoundCue> insertSound, Optional<PositionalSoundCue> ejectSound){
        private static final PositionalSoundCue INSERT = GunSoundDefaults.cue("magin/revolver_magin");
        private static final PositionalSoundCue EJECT = GunSoundDefaults.cue("magout/revolver_magout");
        public CellSlotData(ResourceLocation slotId,Set<ResourceLocation> acceptedTags,Optional<ResourceLocation> startingCell){this(slotId,acceptedTags,startingCell,Optional.of(INSERT),Optional.of(EJECT));}
        public static final Codec<CellSlotData> CODEC=RecordCodecBuilder.create(i->i.group(ResourceLocation.CODEC.fieldOf("slot_id").forGetter(CellSlotData::slotId),ResourceLocation.CODEC.listOf().fieldOf("accepted_tags").xmap(Set::copyOf,s->java.util.List.copyOf(s)).forGetter(CellSlotData::acceptedTags),ResourceLocation.CODEC.optionalFieldOf("starting_cell").forGetter(CellSlotData::startingCell), PositionalSoundCue.codec(0f,0f).optionalFieldOf("insert_sound",INSERT).forGetter(d->d.insertSound.orElse(INSERT)), PositionalSoundCue.codec(0f,0f).optionalFieldOf("eject_sound",EJECT).forGetter(d->d.ejectSound.orElse(EJECT))).apply(i,(slot,tags,cell,in,out)->new CellSlotData(slot,tags,cell,Optional.of(in),Optional.of(out))));
        public CellSlotData{acceptedTags=Set.copyOf(acceptedTags);startingCell=startingCell==null?Optional.empty():startingCell;}
    }
    public record BatteryFireModeData(ResourceLocation ammoPrototype,float cost,boolean pacifismAllowed){
        public static final Codec<BatteryFireModeData> CODEC=RecordCodecBuilder.create(i->i.group(ResourceLocation.CODEC.fieldOf("ammo_prototype").forGetter(BatteryFireModeData::ammoPrototype),Codec.FLOAT.optionalFieldOf("cost",100f).forGetter(BatteryFireModeData::cost),Codec.BOOL.optionalFieldOf("pacifism_allowed",false).forGetter(BatteryFireModeData::pacifismAllowed)).apply(i,BatteryFireModeData::new));
        public BatteryFireModeData{if(ammoPrototype==null||!Float.isFinite(cost)||cost<=0)throw new IllegalArgumentException("invalid battery fire mode");}
    }
    public record WieldBonusData(float minimumDegrees,float maximumDegrees,float angleIncreaseDegrees,float angleDecayDegrees) {
        public static final Codec<WieldBonusData> CODEC=RecordCodecBuilder.create(i->i.group(Codec.FLOAT.optionalFieldOf("minimum_degrees",-20f).forGetter(WieldBonusData::minimumDegrees),Codec.FLOAT.optionalFieldOf("maximum_degrees",-30f).forGetter(WieldBonusData::maximumDegrees),Codec.FLOAT.optionalFieldOf("angle_increase_degrees",0f).forGetter(WieldBonusData::angleIncreaseDegrees),Codec.FLOAT.optionalFieldOf("angle_decay_degrees",0f).forGetter(WieldBonusData::angleDecayDegrees)).apply(i,WieldBonusData::new));
        public WieldBonusData { if(!Float.isFinite(minimumDegrees)||!Float.isFinite(maximumDegrees)||!Float.isFinite(angleIncreaseDegrees)||!Float.isFinite(angleDecayDegrees)) throw new IllegalArgumentException("invalid wield bonus"); }
    }
    public record WieldableData(boolean unwieldOnUse, Optional<PositionalSoundCue> wieldSound) { private static final PositionalSoundCue SOURCE_WIELD_SOUND=new PositionalSoundCue(ResourceLocation.fromNamespaceAndPath("moonstation14","ss14/effects/thudswoosh"),0f,0f); public WieldableData(boolean unwieldOnUse){this(unwieldOnUse,Optional.of(SOURCE_WIELD_SOUND));} public static final Codec<WieldableData> CODEC=RecordCodecBuilder.create(i->i.group(Codec.BOOL.optionalFieldOf("unwield_on_use",true).forGetter(WieldableData::unwieldOnUse),PositionalSoundCue.codec(0f,0f).optionalFieldOf("wield_sound",SOURCE_WIELD_SOUND).forGetter(d->d.wieldSound.orElse(SOURCE_WIELD_SOUND))).apply(i,(unwield,sound)->new WieldableData(unwield,Optional.of(sound)))); }
    public record MagazineChamberProviderData(String magazineSlot, String chamberSlot, Set<ResourceLocation> magazineTags, Set<ResourceLocation> cartridgeTags,
                                               Optional<ResourceLocation> startingMagazine, Optional<ResourceLocation> startingChamber,
                                                 boolean autoCycle, boolean canRack, boolean boltClosed, Optional<PositionalSoundCue> rackSound, Optional<PositionalSoundCue> boltOpenSound, Optional<PositionalSoundCue> boltCloseSound, Optional<PositionalSoundCue> magazineInsertSound, Optional<PositionalSoundCue> magazineEjectSound, Optional<PositionalSoundCue> chamberInsertSound) {
         private static final PositionalSoundCue RACK=GunSoundDefaults.cue("cock/ltrifle_cock"), BOLT_OPEN=GunSoundDefaults.cue("bolt/rifle_bolt_open"), BOLT_CLOSE=GunSoundDefaults.cue("bolt/rifle_bolt_closed"), MAG_INSERT=GunSoundDefaults.cue("magin/revolver_magin"), MAG_EJECT=GunSoundDefaults.cue("magout/revolver_magout"), CHAMBER_INSERT=GunSoundDefaults.cue("magin/revolver_magin");
         public MagazineChamberProviderData(String m,String c,Set<ResourceLocation> mt,Set<ResourceLocation> ct,Optional<ResourceLocation> sm,Optional<ResourceLocation> sc,boolean ac,boolean cr,boolean bc){this(m,c,mt,ct,sm,sc,ac,cr,bc,Optional.of(RACK),Optional.of(BOLT_OPEN),Optional.of(BOLT_CLOSE),Optional.of(MAG_INSERT),Optional.of(MAG_EJECT),Optional.of(GunSoundDefaults.cue("magin/revolver_magin")));}
         public MagazineChamberProviderData(String m,String c,Set<ResourceLocation> mt,Set<ResourceLocation> ct,Optional<ResourceLocation> sm,Optional<ResourceLocation> sc,boolean ac,boolean cr,boolean bc,Optional<PositionalSoundCue> rack,Optional<PositionalSoundCue> open,Optional<PositionalSoundCue> close,Optional<PositionalSoundCue> magInsert,Optional<PositionalSoundCue> magEject){this(m,c,mt,ct,sm,sc,ac,cr,bc,rack,open,close,magInsert,magEject,Optional.of(GunSoundDefaults.cue("magin/revolver_magin")));}
        public static final Codec<MagazineChamberProviderData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("magazine_slot", "gun_magazine").forGetter(MagazineChamberProviderData::magazineSlot),
                Codec.STRING.optionalFieldOf("chamber_slot", "gun_chamber").forGetter(MagazineChamberProviderData::chamberSlot),
                ResourceLocation.CODEC.listOf().fieldOf("magazine_tags").xmap(Set::copyOf,s->java.util.List.copyOf(s)).forGetter(MagazineChamberProviderData::magazineTags),
                ResourceLocation.CODEC.listOf().fieldOf("cartridge_tags").xmap(Set::copyOf,s->java.util.List.copyOf(s)).forGetter(MagazineChamberProviderData::cartridgeTags),
                ResourceLocation.CODEC.optionalFieldOf("starting_magazine").forGetter(MagazineChamberProviderData::startingMagazine),
                ResourceLocation.CODEC.optionalFieldOf("starting_chamber").forGetter(MagazineChamberProviderData::startingChamber),
                Codec.BOOL.optionalFieldOf("auto_cycle", true).forGetter(MagazineChamberProviderData::autoCycle),
                Codec.BOOL.optionalFieldOf("can_rack", true).forGetter(MagazineChamberProviderData::canRack),
                 Codec.BOOL.optionalFieldOf("bolt_closed", false).forGetter(MagazineChamberProviderData::boltClosed), PositionalSoundCue.codec(0f,0f).optionalFieldOf("rack_sound",RACK).forGetter(d->d.rackSound.orElse(RACK)), PositionalSoundCue.codec(0f,0f).optionalFieldOf("bolt_open_sound",BOLT_OPEN).forGetter(d->d.boltOpenSound.orElse(BOLT_OPEN)), PositionalSoundCue.codec(0f,0f).optionalFieldOf("bolt_close_sound",BOLT_CLOSE).forGetter(d->d.boltCloseSound.orElse(BOLT_CLOSE)), PositionalSoundCue.codec(0f,0f).optionalFieldOf("magazine_insert_sound",MAG_INSERT).forGetter(d->d.magazineInsertSound.orElse(MAG_INSERT)), PositionalSoundCue.codec(0f,0f).optionalFieldOf("magazine_eject_sound",MAG_EJECT).forGetter(d->d.magazineEjectSound.orElse(MAG_EJECT)), PositionalSoundCue.codec(0f,0f).optionalFieldOf("chamber_insert_sound",CHAMBER_INSERT).forGetter(d->d.chamberInsertSound.orElse(CHAMBER_INSERT))
         ).apply(i, (m,c,mt,ct,sm,sc,ac,cr,bc,r,o,cl,mi,me,ci)->new MagazineChamberProviderData(m,c,mt,ct,sm,sc,ac,cr,bc,Optional.of(r),Optional.of(o),Optional.of(cl),Optional.of(mi),Optional.of(me),Optional.of(ci))));
         public MagazineChamberProviderData { magazineTags=Set.copyOf(magazineTags); cartridgeTags=Set.copyOf(cartridgeTags); startingMagazine=startingMagazine==null?Optional.empty():startingMagazine; startingChamber=startingChamber==null?Optional.empty():startingChamber; rackSound=rackSound==null?Optional.empty():rackSound; boltOpenSound=boltOpenSound==null?Optional.empty():boltOpenSound; boltCloseSound=boltCloseSound==null?Optional.empty():boltCloseSound; magazineInsertSound=magazineInsertSound==null?Optional.empty():magazineInsertSound; magazineEjectSound=magazineEjectSound==null?Optional.empty():magazineEjectSound; chamberInsertSound=chamberInsertSound==null?Optional.empty():chamberInsertSound; if(magazineSlot==null||magazineSlot.isBlank()||chamberSlot==null||chamberSlot.isBlank()||magazineTags.isEmpty()||cartridgeTags.isEmpty()) throw new IllegalArgumentException("invalid provider slots/whitelists"); }
    }
    public record BallisticProviderData(int capacity, ResourceLocation cartridgeTag, ResourceLocation fillCartridgePrototype, boolean cycleable, Optional<PositionalSoundCue> insertSound, Optional<PositionalSoundCue> cycleSound) {
        private static final PositionalSoundCue INSERT=GunSoundDefaults.cue("magin/bullet_insert"), CYCLE=GunSoundDefaults.cue("cock/smg_cock");
        public BallisticProviderData(int capacity,ResourceLocation tag,ResourceLocation fill,boolean cycleable){this(capacity,tag,fill,cycleable,Optional.of(INSERT),Optional.of(CYCLE));}
        public static final Codec<BallisticProviderData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("capacity").forGetter(BallisticProviderData::capacity),
                ResourceLocation.CODEC.fieldOf("cartridge_tag").forGetter(BallisticProviderData::cartridgeTag),
                ResourceLocation.CODEC.fieldOf("fill_cartridge_prototype").forGetter(BallisticProviderData::fillCartridgePrototype),
                Codec.BOOL.optionalFieldOf("cycleable", true).forGetter(BallisticProviderData::cycleable), PositionalSoundCue.codec(0f,0f).optionalFieldOf("insert_sound",INSERT).forGetter(d->d.insertSound.orElse(INSERT)), PositionalSoundCue.codec(0f,0f).optionalFieldOf("cycle_sound",CYCLE).forGetter(d->d.cycleSound.orElse(CYCLE))
        ).apply(i, (capacity,tag,fill,cycleable,insert,cycle)->new BallisticProviderData(capacity,tag,fill,cycleable,Optional.of(insert),Optional.of(cycle))));
        public BallisticProviderData { if(capacity<1 || cartridgeTag==null || fillCartridgePrototype==null) throw new IllegalArgumentException("invalid ballistic provider"); }
    }
    public record BatteryAmmoProviderData(ResourceLocation ammoPrototype, float cost) {
        public static final Codec<BatteryAmmoProviderData> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("ammo_prototype").forGetter(BatteryAmmoProviderData::ammoPrototype),
                Codec.FLOAT.fieldOf("cost").forGetter(BatteryAmmoProviderData::cost)
        ).apply(i, BatteryAmmoProviderData::new));
        public BatteryAmmoProviderData { if(ammoPrototype==null || !Float.isFinite(cost) || cost<=0) throw new IllegalArgumentException("invalid battery ammo provider"); }
    }
    public record GunData(float fireRate, float projectileSpeed, boolean useKey, String firingMode,
                            Optional<PositionalSoundCue> gunshotSound, Optional<Float> useDelaySeconds, SpreadData spread,
                             float burstCooldownSeconds, Optional<PositionalSoundCue> emptySound, float cameraRecoilScalar, Set<String> availableModes, Optional<PositionalSoundCue> modeSound) {
        private static final PositionalSoundCue MODE_SOUND=GunSoundDefaults.cue("misc/selector");
        public GunData(float rate,float speed,boolean key,String mode,Optional<PositionalSoundCue> shot,Optional<Float> delay,SpreadData spread,float cooldown,Optional<PositionalSoundCue> empty,float recoil,Set<String> modes){this(rate,speed,key,mode,shot,delay,spread,cooldown,empty,recoil,modes,Optional.of(MODE_SOUND));}
        public static final PositionalSoundCue SOURCE_EMPTY_SOUND = new PositionalSoundCue(
                ResourceLocation.fromNamespaceAndPath("moonstation14", "ss14/weapons/guns/empty"), 0f, 0f);
        public GunData(float fireRate, float projectileSpeed, boolean useKey, String firingMode,
                       Optional<PositionalSoundCue> gunshotSound, Optional<Float> useDelaySeconds) {
            this(fireRate, projectileSpeed, useKey, firingMode, gunshotSound, useDelaySeconds, SpreadData.SOURCE_DEFAULTS, .25f, Optional.of(SOURCE_EMPTY_SOUND), 1f, Set.of("SEMI_AUTO"));
        }
        public GunData(float fireRate, float projectileSpeed, boolean useKey, String firingMode,
                       Optional<PositionalSoundCue> gunshotSound, Optional<Float> useDelaySeconds, SpreadData spread) {
            this(fireRate, projectileSpeed, useKey, firingMode, gunshotSound, useDelaySeconds, spread, .25f, Optional.of(SOURCE_EMPTY_SOUND), 1f, Set.of("SEMI_AUTO"));
        }
        public GunData(float fireRate, float projectileSpeed, boolean useKey, String firingMode, Optional<PositionalSoundCue> gunshotSound, Optional<Float> useDelaySeconds, SpreadData spread, float burstCooldownSeconds, Optional<PositionalSoundCue> emptySound) { this(fireRate, projectileSpeed, useKey, firingMode, gunshotSound, useDelaySeconds, spread, burstCooldownSeconds, emptySound, 1f, Set.of("SEMI_AUTO")); }
        public GunData(float fireRate, float projectileSpeed, boolean useKey, String firingMode, Optional<PositionalSoundCue> sound, Optional<Float> delay, SpreadData spread, float cooldown, Optional<PositionalSoundCue> empty, float recoil) { this(fireRate,projectileSpeed,useKey,firingMode,sound,delay,spread,cooldown,empty,recoil,Set.of("SEMI_AUTO")); }
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
                Codec.FLOAT.optionalFieldOf("camera_recoil_scalar", 1f).forGetter(GunData::cameraRecoilScalar),
            Codec.STRING.listOf().optionalFieldOf("available_modes", java.util.List.of("SEMI_AUTO")).xmap(Set::copyOf,s->java.util.List.copyOf(s)).forGetter(GunData::availableModes), PositionalSoundCue.codec(0f,0f).optionalFieldOf("mode_sound",MODE_SOUND).forGetter(d->d.modeSound.orElse(MODE_SOUND))
        ).apply(i, (rate,speed,key,mode,shot,delay,spread,cooldown,empty,recoil,modes,modeSound)->new GunData(rate,speed,key,mode,shot,delay,spread,cooldown,empty,recoil,modes,Optional.of(modeSound))));
        public GunData {
            if (!Float.isFinite(fireRate) || fireRate <= 0 || !Float.isFinite(projectileSpeed) || projectileSpeed <= 0 || !Float.isFinite(cameraRecoilScalar) || cameraRecoilScalar < 0)
                throw new IllegalArgumentException("gun rate and projectile speed must be finite and positive");
            availableModes=Set.copyOf(availableModes);
            if (availableModes.isEmpty() || !availableModes.contains(firingMode) || !Set.of("SEMI_AUTO","FULL_AUTO").containsAll(availableModes)) throw new IllegalArgumentException("invalid firing modes or selected mode");
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
