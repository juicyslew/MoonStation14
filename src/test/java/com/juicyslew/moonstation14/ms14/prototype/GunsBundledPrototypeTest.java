package com.juicyslew.moonstation14.ms14.prototype;

import com.juicyslew.moonstation14.ms14.combat.GunProfile;
import com.juicyslew.moonstation14.ms14.combat.ModGunProfiles;
import com.juicyslew.moonstation14.ms14.combat.CartridgeProfile;
import com.juicyslew.moonstation14.ms14.combat.AmmoContainerProfile;
import com.juicyslew.moonstation14.ms14.combat.ModCartridgeProfiles;
import com.juicyslew.moonstation14.ms14.combat.ModAmmoContainerProfiles;
import com.juicyslew.moonstation14.ms14.combat.MeleeWeaponProfile;
import com.juicyslew.moonstation14.ms14.combat.ModMeleeWeaponProfiles;
import com.juicyslew.moonstation14.sounds.ModSounds;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class GunsBundledPrototypeTest {
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", path);
    }

    @Test
    void allBundledProjectileProfilesResolveWithCanonicalLivingDamageKeys() {
        var projectiles = resolve(com.juicyslew.moonstation14.ms14.combat.ModProjectileProfiles.TYPE,
                "projectile_profile", com.juicyslew.moonstation14.ms14.combat.ProjectileProfile.CODEC);
        assertFalse(projectiles.isEmpty());
        assertEquals(Map.of("piercing", 19f), projectiles.get(id("bullet_light_rifle")).damage());
        for (var entry : projectiles.entrySet()) {
            com.juicyslew.moonstation14.ms14.damage.DamageKeys.validateState(entry.getValue().damage());
        }
    }

    @Test
    void allProductionRegisteredPrototypeResourcesStageTogetherWithoutPublishing() {
        var manager = com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.createManager();
        var candidate = new LinkedHashMap<PrototypeType<?>, Map<ResourceLocation, com.google.gson.JsonObject>>();
        for (var type : manager.registeredTypes())
            candidate.put(type, BotanyCatalogCandidateTest.mainResourcesAllAt(
                    "data/" + type.typeId().getNamespace() + "/" + type.resourceDirectory()));
        for (var entry : candidate.entrySet())
            assertFalse(entry.getValue().isEmpty(), "registered production family has no bundled resources: " + entry.getKey().typeId());
        assertEquals(manager.registeredTypes().size(), candidate.size());
        assertDoesNotThrow(() -> manager.stage(candidate));
    }

    @Test
    void bundledChargerInheritanceResolvesSourceDefaultsAndChargeKernelCaps() {
        var chargers = resolve(com.juicyslew.moonstation14.ms14.combat.ModChargerProfiles.TYPE,
                "charger_profile", com.juicyslew.moonstation14.ms14.combat.ChargerProfile.CODEC);
        var profile = chargers.get(id("weapon_capacitor_recharger"));
        assertNotNull(profile);
        assertEquals(1f, profile.idlePower());
        assertEquals(20f, profile.chargeRate());
        assertTrue(profile.ejectOnInteract());
        assertEquals("charger_slot", profile.slotId());
        assertEquals(Set.of(com.juicyslew.moonstation14.ms14.combat.ChargerCapability.BATTERY_AMMO_PROVIDER,
                com.juicyslew.moonstation14.ms14.combat.ChargerCapability.POWER_CELL), profile.acceptedCapabilities());
        assertEquals("base_item_recharger", com.google.gson.JsonParser.parseString(
                readResource("/data/moonstation14/moonstation14/charger_profile/weapon_capacitor_recharger.json"))
                .getAsJsonObject().get("parent").getAsString());
        var batteries = resolve(com.juicyslew.moonstation14.ms14.combat.ModBatteryProfiles.TYPE,
                "battery_profile", com.juicyslew.moonstation14.ms14.combat.BatteryProfile.CODEC);
        var energy = batteries.get(id("weapon_energy_shotgun"));
        assertEquals(480f, energy.capacity());
        var full = com.juicyslew.moonstation14.ms14.combat.ChargerKernel.charge(
                new com.juicyslew.moonstation14.ms14.combat.BatteryState(0), energy, profile, 1, true);
        assertEquals(20f, full.charge());
        var capped = com.juicyslew.moonstation14.ms14.combat.ChargerKernel.charge(
                new com.juicyslew.moonstation14.ms14.combat.BatteryState(479), energy, profile, 1, true);
        assertEquals(480f, capped.charge());
        assertFalse(com.juicyslew.moonstation14.ms14.combat.ChargerKernel.charge(
                new com.juicyslew.moonstation14.ms14.combat.BatteryState(0), energy, profile, 1, false).accepted());
        assertFalse(com.juicyslew.moonstation14.ms14.combat.ChargerKernel.charge(
                new com.juicyslew.moonstation14.ms14.combat.BatteryState(0), energy, profile, 0, true).accepted());
    }

    @Test
    void chargerTargetsResolveRealBatteryProvidersAndMutateOwnedNestedCellOnce() {
        var guns = resolve(ModGunProfiles.TYPE, "gun_profile", GunProfile.CODEC);
        var batteries = resolve(com.juicyslew.moonstation14.ms14.combat.ModBatteryProfiles.TYPE,
                "battery_profile", com.juicyslew.moonstation14.ms14.combat.BatteryProfile.CODEC);
        var charger = resolve(com.juicyslew.moonstation14.ms14.combat.ModChargerProfiles.TYPE,
                "charger_profile", com.juicyslew.moonstation14.ms14.combat.ChargerProfile.CODEC)
                .get(id("weapon_capacitor_recharger"));
        var shotgun = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WEAPON_ENERGY_SHOTGUN.get());
        var fresh = com.juicyslew.moonstation14.ms14.combat.ChargerBatteryResolver.prepareAcceptedStack(shotgun, charger, guns, batteries).orElseThrow();
        assertEquals(480f, fresh.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get()).charge());
        var target = com.juicyslew.moonstation14.ms14.combat.ChargerBatteryResolver.resolve(fresh, guns, batteries).orElseThrow();
        fresh.set(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get(),
                new com.juicyslew.moonstation14.ms14.combat.BatteryState(0));
        target = com.juicyslew.moonstation14.ms14.combat.ChargerBatteryResolver.resolve(fresh, guns, batteries).orElseThrow();
        var charged = com.juicyslew.moonstation14.ms14.combat.ChargerKernel.charge(
                target.state(), target.profile(), charger, 1, true);
        var changed = com.juicyslew.moonstation14.ms14.combat.ChargerBatteryResolver.writeBack(fresh, target, charged.charge());
        assertEquals(20f, changed.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get()).charge());
        var ballistic = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WEAPON_PISTOL_MK58.get());
        assertTrue(com.juicyslew.moonstation14.ms14.combat.ChargerBatteryResolver.resolve(ballistic, guns, batteries).isEmpty());
        var empty = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WEAPON_LASER_SVALINN.get());
        assertTrue(com.juicyslew.moonstation14.ms14.combat.ChargerBatteryResolver.resolve(empty, guns, batteries).isEmpty());
        var cell = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.POWER_CELL_SMALL.get());
        cell.set(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get(),
                new com.juicyslew.moonstation14.ms14.combat.BatteryState(180));
        var svalinn = new net.minecraft.world.item.ItemStack(com.juicyslew.moonstation14.item.ModItems.WEAPON_LASER_SVALINN.get());
        svalinn.set(com.juicyslew.moonstation14.component.ModDataComponents.POWER_CELL_SLOT_STATE.get(),
                new com.juicyslew.moonstation14.ms14.combat.GunPowerCellSlotState(java.util.Optional.of(cell)));
        var slotReady = com.juicyslew.moonstation14.ms14.combat.ChargerBatteryResolver.prepareAcceptedStack(svalinn, charger, guns, batteries).orElseThrow();
        var nestedTarget = com.juicyslew.moonstation14.ms14.combat.ChargerBatteryResolver.resolve(slotReady, guns, batteries).orElseThrow();
        var nestedCharged = com.juicyslew.moonstation14.ms14.combat.ChargerKernel.charge(nestedTarget.state(), nestedTarget.profile(), charger, 1, true);
        var nestedChanged = com.juicyslew.moonstation14.ms14.combat.ChargerBatteryResolver.writeBack(slotReady, nestedTarget, nestedCharged.charge());
        var returnedCell = nestedChanged.get(com.juicyslew.moonstation14.component.ModDataComponents.POWER_CELL_SLOT_STATE.get()).cell().orElseThrow();
        assertEquals(cell.getItem(), returnedCell.getItem());
        assertEquals(200f, returnedCell.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get()).charge());
        assertTrue(com.juicyslew.moonstation14.ms14.combat.ChargerBatteryResolver.prepareAcceptedStack(empty, charger, guns, batteries).isEmpty());
    }

    private static String readResource(String path) {
        try (var stream = GunsBundledPrototypeTest.class.getResourceAsStream(path)) {
            if (stream == null) throw new AssertionError("missing " + path);
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) { throw new AssertionError(e); }
    }

    @Test
    void bundledGunAmmoLinksResolveAgainstRealCartridgeAndContainerFamilies() {
        var guns = resolve(ModGunProfiles.TYPE, "gun_profile", GunProfile.CODEC);
        var cartridges = resolve(ModCartridgeProfiles.TYPE, "cartridge_profile", CartridgeProfile.CODEC);
        var containers = resolve(ModAmmoContainerProfiles.TYPE, "ammo_container_profile", AmmoContainerProfile.CODEC);

        assertTrue(guns.get(id("weapon_rifle_lecter")).magazineChamberProvider().orElseThrow().cartridgeTags()
                .containsAll(cartridges.get(id("cartridge_rifle")).tags()));
        assertTrue(guns.get(id("weapon_sniper_mosin")).ballisticProvider().orElseThrow().cartridgeTag()
                .equals(id("cartridge_light_rifle")));
        assertTrue(guns.get(id("weapon_revolver_inspector")).revolverAmmoProvider().orElseThrow().acceptedCartridgeTags()
                .containsAll(cartridges.get(id("cartridge_magnum")).tags()));
        assertEquals(id("shell_shotgun"), guns.get(id("weapon_shotgun_kammerer")).ballisticProvider().orElseThrow().fillCartridgePrototype());
        for (String family : Set.of("magazine_rifle", "magazine_pistol", "speedloader_magnum")) {
            var container = containers.get(id(family));
            assertNotNull(container, family);
            assertTrue(cartridges.keySet().stream().anyMatch(cartridge -> {
                var profile = cartridges.get(cartridge);
                return container.acceptedTags().stream().anyMatch(profile.tags()::contains);
            }), family);
        }
        assertTrue(cartridges.containsKey(id("shell_shotgun")));
    }

    private static <T> Map<ResourceLocation, T> resolve(PrototypeType<T> type, String directory, com.mojang.serialization.Codec<T> codec) {
        var manager = new PrototypeManager();
        manager.register(type);
        var resolved = new AtomicReference<Map<ResourceLocation, T>>();
        manager.stage(Map.of(type, BotanyCatalogCandidateTest.mainResourcesAll(directory)), catalogs -> {
            var entries = new LinkedHashMap<ResourceLocation, T>();
            catalogs.get(type.typeId()).forEach((key, raw) -> entries.put(key,
                    codec.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            resolved.set(Map.copyOf(entries));
        });
        return resolved.get();
    }

    @Test
    void allBundledProductionGunsResolveAndDecodeTheirInheritedProfiles() {
        var manager = new PrototypeManager();
        manager.register(ModGunProfiles.TYPE);
        var resolved = new AtomicReference<Map<ResourceLocation, GunProfile>>();
        manager.stage(Map.of(ModGunProfiles.TYPE, BotanyCatalogCandidateTest.mainResourcesAll("gun_profile")), catalogs -> {
            var profiles = new LinkedHashMap<ResourceLocation, GunProfile>();
            catalogs.get(ModGunProfiles.TYPE.typeId()).forEach((key, raw) -> profiles.put(key,
                    GunProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, raw).getOrThrow()));
            resolved.set(Map.copyOf(profiles));
        });

        var guns = resolved.get();
        assertEquals(8, guns.size());
        for (var entry : guns.entrySet()) {
            assertEquals(40f, entry.getValue().gun().projectileSpeed(), entry.getKey().toString());
            assertEquals(2d, entry.getValue().gun().projectileSpeed() / 20d, 1e-9,
                    entry.getKey() + " stationary projectile blocks/tick");
        }
        var providerFamilies = Map.of(
                "weapon_pistol_mk58", "magazine", "weapon_rifle_lecter", "magazine",
                "weapon_revolver_inspector", "revolver", "weapon_shotgun_kammerer", "ballistic",
                "weapon_sniper_mosin", "ballistic", "weapon_laser_gun", "battery",
                "weapon_laser_svalinn", "battery", "weapon_energy_shotgun", "battery");
        for (var entry : guns.entrySet()) {
            var gun = entry.getValue();
            var family = providerFamilies.get(entry.getKey().getPath());
            int providerCount = (gun.revolverAmmoProvider().isPresent() ? 1 : 0)
                    + (gun.magazineChamberProvider().isPresent() ? 1 : 0)
                    + (gun.ballisticProvider().isPresent() ? 1 : 0)
                    + (gun.batteryAmmoProvider().isPresent() ? 1 : 0);
            assertEquals(1, providerCount, entry.getKey().toString());
            assertTrue(switch (family) {
                case "magazine" -> gun.magazineChamberProvider().isPresent();
                case "revolver" -> gun.revolverAmmoProvider().isPresent();
                case "ballistic" -> gun.ballisticProvider().isPresent();
                case "battery" -> gun.batteryAmmoProvider().isPresent();
                default -> false;
            }, entry.getKey().toString());
            if (gun.batteryFireModes().isPresent())
                assertTrue(gun.batteryAmmoProvider().isPresent(), entry.getKey().toString());
        }
        for (String abstractId : Set.of("base_gun_wieldable", "base_weapon_rifle", "base_weapon_revolver",
                "base_weapon_pistol", "base_weapon_shotgun", "base_weapon_sniper", "base_weapon_battery",
                "base_weapon_battery_small", "base_weapon_power_cell", "base_weapon_power_cell_small"))
            assertFalse(guns.containsKey(id(abstractId)), abstractId + " must not be catalogued");
        var mk58 = guns.get(id("weapon_pistol_mk58"));
        assertNotNull(mk58);
        assertEquals(5f, mk58.gun().fireRate());
        assertEquals("SEMI_AUTO", mk58.gun().firingMode());
        var lecter = guns.get(id("weapon_rifle_lecter"));
        assertNotNull(lecter);
        assertEquals(5f, lecter.gun().fireRate());
        assertEquals("FULL_AUTO", lecter.gun().firingMode());
        assertEquals(Set.of("FULL_AUTO", "SEMI_AUTO"), lecter.gun().availableModes());
        assertFalse(lecter.requiresWield());
        assertTrue(lecter.wieldBonus().isPresent());
        assertEquals(21f, lecter.gun().spread().minimumDegrees());
        assertEquals(32f, lecter.gun().spread().maximumDegrees());
        assertEquals(1f, com.juicyslew.moonstation14.ms14.combat.GunModePolicy.effectiveSpread(lecter, true).minimumDegrees());
        assertEquals(2f, com.juicyslew.moonstation14.ms14.combat.GunModePolicy.effectiveSpread(lecter, true).maximumDegrees());
        assertEquals(21f, com.juicyslew.moonstation14.ms14.combat.GunModePolicy.effectiveSpread(lecter, false).minimumDegrees());
        assertEquals(32f, com.juicyslew.moonstation14.ms14.combat.GunModePolicy.effectiveSpread(lecter, false).maximumDegrees());
        for (String wieldedGun : Set.of("weapon_rifle_lecter", "weapon_shotgun_kammerer", "weapon_sniper_mosin", "weapon_energy_shotgun")) {
            var profile = guns.get(id(wieldedGun));
            assertNotNull(profile, wieldedGun);
            assertEquals(21f, profile.gun().spread().minimumDegrees(), wieldedGun);
            assertEquals(32f, profile.gun().spread().maximumDegrees(), wieldedGun);
            assertEquals(-20f, profile.wieldBonus().orElseThrow().minimumDegrees(), wieldedGun);
            assertEquals(-30f, profile.wieldBonus().orElseThrow().maximumDegrees(), wieldedGun);
            assertEquals(1f, com.juicyslew.moonstation14.ms14.combat.GunModePolicy.effectiveSpread(profile, true).minimumDegrees(), wieldedGun);
            assertEquals(2f, com.juicyslew.moonstation14.ms14.combat.GunModePolicy.effectiveSpread(profile, true).maximumDegrees(), wieldedGun);
            assertEquals(21f, com.juicyslew.moonstation14.ms14.combat.GunModePolicy.effectiveSpread(profile, false).minimumDegrees(), wieldedGun);
            assertEquals(32f, com.juicyslew.moonstation14.ms14.combat.GunModePolicy.effectiveSpread(profile, false).maximumDegrees(), wieldedGun);
        }
        assertEquals(5f, lecter.gun().fireRate());
        assertEquals("FULL_AUTO", lecter.gun().firingMode());
        assertEquals(1f, guns.get(id("weapon_shotgun_kammerer")).gun().fireRate());
        assertEquals(.75f, guns.get(id("weapon_sniper_mosin")).gun().fireRate());
        assertEquals(2f, guns.get(id("weapon_energy_shotgun")).gun().fireRate());
        assertTrue(guns.get(id("weapon_energy_shotgun")).requiresWield());
        assertFalse(guns.get(id("weapon_pistol_mk58")).requiresWield());
        assertEquals(1f, guns.get(id("weapon_pistol_mk58")).gun().spread().minimumDegrees());
        assertEquals(2f, guns.get(id("weapon_pistol_mk58")).gun().spread().maximumDegrees());

        assertEquals(6, guns.get(id("weapon_revolver_inspector")).revolverAmmoProvider().orElseThrow().capacity());
        var kammerer = guns.get(id("weapon_shotgun_kammerer"));
        assertEquals(7, kammerer.ballisticProvider().orElseThrow().capacity());
        assertTrue(kammerer.requiresWield());
        assertEquals(.6f, kammerer.gunSpreadModifier());
        var mosin = guns.get(id("weapon_sniper_mosin"));
        assertEquals(10, mosin.ballisticProvider().orElseThrow().capacity());
        assertTrue(mosin.requiresWield());

        assertEquals(62.5f, guns.get(id("weapon_laser_gun")).batteryAmmoProvider().orElseThrow().cost());
        var energyShotgun = guns.get(id("weapon_energy_shotgun"));
        assertEquals(2, energyShotgun.batteryFireModes().orElseThrow().size());
        assertEquals(80f, energyShotgun.batteryFireModes().orElseThrow().get(0).cost());
        assertEquals(48f, energyShotgun.batteryFireModes().orElseThrow().get(1).cost());
        assertEquals(id("bullet_laser_spread_narrow"), energyShotgun.batteryAmmoProvider().orElseThrow().ammoPrototype());
        assertEquals(80f, energyShotgun.batteryAmmoProvider().orElseThrow().cost());
        var svalinn = guns.get(id("weapon_laser_svalinn"));
        assertEquals(id("power_cell_small"), svalinn.cellSlot().orElseThrow().startingCell().orElseThrow());
        assertEquals(50f, svalinn.batteryAmmoProvider().orElseThrow().cost());
        assertEquals(id("red_light_laser"), svalinn.batteryAmmoProvider().orElseThrow().ammoPrototype());
    }

    @Test
    void bundledGunshotCuesResolveToRegisteredEventsWithPackagedSamples() throws Exception {
        var guns = resolve(ModGunProfiles.TYPE, "gun_profile", GunProfile.CODEC);
        String[][] mappings = {
                {"weapon_pistol_mk58", "ss14/weapons/guns/gunshots/pistol", "ss14/weapons/guns/gunshots/pistol"},
                {"weapon_rifle_lecter", "ss14/weapons/guns/gunshots/rifle", "ss14/weapons/guns/gunshots/ltrifle"},
                {"weapon_revolver_inspector", "ss14/weapons/guns/gunshots/revolver", "ss14/weapons/guns/gunshots/revolver"},
                {"weapon_shotgun_kammerer", "ss14/weapons/guns/gunshots/shotgun", "ss14/weapons/guns/gunshots/shotgun"},
                {"weapon_sniper_mosin", "ss14/weapons/guns/gunshots/sniper", "ss14/weapons/guns/gunshots/sniper"},
                {"weapon_laser_gun", "ss14/weapons/guns/gunshots/laser", "ss14/weapons/guns/gunshots/laser"},
                {"weapon_laser_svalinn", "ss14/weapons/guns/gunshots/laser", "ss14/weapons/guns/gunshots/laser"},
                {"weapon_energy_shotgun", "ss14/weapons/guns/gunshots/laser_cannon", "ss14/weapons/guns/gunshots/laser_cannon"}
        };
        var soundsJson = com.google.gson.JsonParser.parseString(new String(
                getClass().getResourceAsStream("/assets/moonstation14/sounds.json").readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        var registered = java.util.List.of(ModSounds.GUNSHOT_PISTOL, ModSounds.GUNSHOT_RIFLE, ModSounds.GUNSHOT_REVOLVER,
                ModSounds.GUNSHOT_SHOTGUN, ModSounds.GUNSHOT_SNIPER, ModSounds.GUNSHOT_LASER, ModSounds.GUNSHOT_LASER,
                ModSounds.GUNSHOT_LASER_CANNON);
        assertEquals(id("ss14/weapons/guns/gunshots/taser2"), ModSounds.GUNSHOT_TASER2.get().getLocation());
        for (int i = 0; i < mappings.length; i++) {
            String[] mapping = mappings[i];
            var cue = guns.get(id(mapping[0])).gun().gunshotSound().orElseThrow(() -> new AssertionError(mapping[0]));
            assertEquals(id(mapping[1]), cue.event(), mapping[0]);
            assertEquals(0f, cue.volumeDb(), mapping[0]);
            assertEquals(0f, cue.pitchVariation(), mapping[0]);
            assertEquals(id(mapping[1]), registered.get(i).get().getLocation(), mapping[0]);
            var event = soundsJson.getAsJsonObject(mapping[1]);
            assertNotNull(event, mapping[1]);
            assertEquals("moonstation14:" + mapping[2], event.getAsJsonArray("sounds").get(0).getAsString());
            assertNotNull(getClass().getResource("/assets/moonstation14/sounds/" + mapping[2] + ".ogg"), mapping[2]);
        }
    }

    @Test
    void genericChamberMagazineDefaultRackCueHasRegisteredPackagedEvent() {
        var guns = resolve(ModGunProfiles.TYPE, "gun_profile", GunProfile.CODEC);
        var rack = guns.get(id("weapon_rifle_lecter")).magazineChamberProvider().orElseThrow().rackSound().orElseThrow();
        var expected = id("ss14/weapons/guns/cock/sf_rifle_cock");
        assertEquals(expected, rack.event());
        assertNotNull(getClass().getResource("/assets/moonstation14/sounds/ss14/weapons/guns/cock/sf_rifle_cock.ogg"));
    }

    @Test
    void mosinMeleeProfileResolvesSourceMeleeCompositionAndWieldGate() {
        var profiles = resolve(ModMeleeWeaponProfiles.TYPE, "melee_weapon_profile", MeleeWeaponProfile.CODEC);
        var mosin = profiles.get(id("weapon_sniper_mosin"));

        assertNotNull(mosin);
        assertEquals(Map.of("piercing", 12f), mosin.weapon().damage());
        assertEquals(0f, mosin.weapon().angleDegrees());
        assertEquals(-135f, mosin.weapon().wideAnimationRotation());
        assertEquals(id("weapon_arc_thrust"), mosin.weapon().arc());
        assertTrue(mosin.requiresWield());
        assertEquals("melee", mosin.chemicalInjector().orElseThrow().solutionName());
        assertEquals(100, mosin.chemicalInjector().orElseThrow().transferCents());
        assertTrue(mosin.chemicalInjector().orElseThrow().pierceArmor());
        var solutions = resolve(com.juicyslew.moonstation14.ms14.reagent.ModItemFoodSolutionProfiles.TYPE,
                "item_food_solution_profile", com.juicyslew.moonstation14.ms14.reagent.ItemFoodSolutionProfile.CODEC);
        var solution = solutions.get(id("weapon_sniper_mosin"));
        assertNotNull(solution);
        assertEquals(200, solution.capacityCents());
        assertTrue(solution.contentsCents().isEmpty());
        assertEquals("melee", solution.solutionName());
        assertTrue(solution.refillable());
        assertEquals(200, solution.maxTransferCents());
    }

    @Test void ordinaryBallisticAndBatteryUsePlansPreserveSourceSemantics(){
        var guns=resolve(ModGunProfiles.TYPE,"gun_profile",GunProfile.CODEC);
        var action=com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_CYCLE_ACTION;
        for(String name:new String[]{"weapon_shotgun_kammerer","weapon_sniper_mosin"}){
            var profile=guns.get(id(name));var provider=profile.ballisticProvider().orElseThrow();
            var source=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
            source.set(com.juicyslew.moonstation14.component.ModDataComponents.BALLISTIC_STATE.get(),
                    com.juicyslew.moonstation14.ms14.combat.BallisticState.full(provider.capacity(),provider.fillCartridgePrototype()));
            source.set(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get(),
                    new com.juicyslew.moonstation14.ms14.combat.GunRuntimeState(0,0,0,0));
            var before=source.copy();var prior=source.get(com.juicyslew.moonstation14.component.ModDataComponents.BALLISTIC_STATE.get());
            var plan=com.juicyslew.moonstation14.ms14.combat.GunWorldUsePolicy.prepareControl(action,profile,source,0,100);
            assertNotNull(plan,name);var after=plan.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.BALLISTIC_STATE.get());
            assertEquals(prior.count()-1,after.count(),name);assertEquals(1,plan.outputs().size(),name);
            assertEquals(provider.fillCartridgePrototype(),net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(plan.outputs().get(0).getItem()),name);
            assertEquals(Math.max(101,com.juicyslew.moonstation14.ms14.combat.GunFirePolicy.nextFireTick(100,profile.gun().fireRate()).orElseThrow()),
                    plan.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get()).nextFireTick(),name);
            assertTrue(net.minecraft.world.item.ItemStack.matches(before,source),name);
        }
        var energy=guns.get(id("weapon_energy_shotgun"));var fixed=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        assertEquals(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_CYCLE_ACTION,
                com.juicyslew.moonstation14.ms14.combat.GunControlAvailability.ordinaryUseRows(guns.get(id("weapon_shotgun_kammerer")),com.juicyslew.moonstation14.ms14.combat.GunControlAvailability.SourceKind.WORLD).get(0).action());
        assertEquals(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_CYCLE_BATTERY_MODE,
                com.juicyslew.moonstation14.ms14.combat.GunControlAvailability.ordinaryUseRows(energy,com.juicyslew.moonstation14.ms14.combat.GunControlAvailability.SourceKind.HELD).get(0).action());
        for(var sourceKind:com.juicyslew.moonstation14.ms14.combat.GunControlAvailability.SourceKind.values()){
            assertTrue(com.juicyslew.moonstation14.ms14.combat.GunControlAvailability.rows(guns.get(id("weapon_sniper_mosin")),fixed,sourceKind)
                    .stream().anyMatch(row->row.action()==action));
            assertTrue(com.juicyslew.moonstation14.ms14.combat.GunControlAvailability.rows(energy,fixed,sourceKind)
                    .stream().anyMatch(row->row.action()==com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_CYCLE_BATTERY_MODE));
        }
        fixed.set(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get(),com.juicyslew.moonstation14.ms14.combat.GunRuntimeState.INITIAL);
        var batteryBefore=fixed.copy();
        var mode=com.juicyslew.moonstation14.ms14.combat.GunWorldUsePolicy.prepareControl(
                com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_CYCLE_BATTERY_MODE,energy,fixed,0,100);
        assertNotNull(mode);assertEquals(1,mode.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get()).batteryModeIndex());
        assertTrue(mode.outputs().isEmpty());assertTrue(net.minecraft.world.item.ItemStack.matches(batteryBefore,fixed));
    }

}
