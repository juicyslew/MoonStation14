package com.juicyslew.moonstation14.ms14.tesla;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.juicyslew.moonstation14.ms14.character.components.CharacterComponentRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TeslaProfilesTest {
    @Test void blockEligibilityUsesExplicitCounterpartMapping() {
        for (String positive : java.util.List.of("tesla_coil", "tesla_grounding_rod", "emitter", "airlock", "chem_master", "apc", "hv_mv_substation"))
            assertNotNull(ModTeslaProfiles.profileForBlock(ModTeslaProfiles.id(positive)), positive);
        for (String negative : java.util.List.of("tesla_generator", "singularity_generator", "containment_field_generator", "stone", "air", "loose_item"))
            assertNull(ModTeslaProfiles.profileForBlock(net.minecraft.resources.ResourceLocation.withDefaultNamespace(negative)), negative);
    }
    @Test void machineryIntegrityTransitionAppliesWholeAuthoredDamageAndIgnoresZero() {
        assertEquals(0, MachineryIntegrity.nextDamage(0, 0));
        assertEquals(0, MachineryIntegrity.nextDamage(0, -1));
        assertEquals(4, MachineryIntegrity.nextDamage(3, 1));
        assertEquals(5, MachineryIntegrity.nextDamage(3, 1.1));
    }
    @Test void registeredTeslaTypesDiscoverAndResolvePackResources(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        var manager = new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();
        manager.register(ModTeslaProfiles.BALL_TYPE);
        manager.register(ModTeslaProfiles.MACHINE_TYPE);
        manager.register(com.juicyslew.moonstation14.ms14.singularity.SingularityGeneratorProfile.TYPE);
        var pack = temp.resolve("tesla-pack");
        java.nio.file.Files.createDirectories(pack);
        for (String type : java.util.List.of("tesla_ball", "tesla_machine", "singularity_generator")) {
            var directory = "data/moonstation14/moonstation14/" + type;
            var filenames=switch(type) {
                case "tesla_ball" -> java.util.List.of("base.json", "tesla_energy_ball.json", "tesla_mini_energy_ball.json");
                case "tesla_machine" -> java.util.List.of("generic_machine.json", "tesla_coil.json", "tesla_grounding_rod.json", "tesla_generator.json", "emitter.json", "airlock.json", "chem_master.json", "apc.json", "hv_mv_substation.json");
                default -> java.util.List.of("base.json", "default.json", "tesla.json");
            };
            for (String filename : filenames) {
                var target = pack.resolve(directory).resolve(filename);
                java.nio.file.Files.createDirectories(target.getParent());
                try (var input = TeslaProfilesTest.class.getClassLoader().getResourceAsStream(directory + "/" + filename)) {
                    assertNotNull(input, directory + "/" + filename);
                    java.nio.file.Files.copy(input, target);
                }
            }
        }
        try (var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(
                net.minecraft.server.packs.PackType.SERVER_DATA, java.util.List.of(new net.minecraft.server.packs.PathPackResources(
                new net.minecraft.server.packs.PackLocationInfo("tesla", net.minecraft.network.chat.Component.literal("test"),
                        net.minecraft.server.packs.repository.PackSource.DEFAULT, java.util.Optional.empty()), pack)))) {
            var listener = new com.juicyslew.moonstation14.ms14.prototype.PrototypeReloadListener(manager);
            var prepare = com.juicyslew.moonstation14.ms14.prototype.PrototypeReloadListener.class.getDeclaredMethod(
                    "prepare", net.minecraft.server.packs.resources.ResourceManager.class, net.minecraft.util.profiling.ProfilerFiller.class);
            prepare.setAccessible(true);
            Object prepared = prepare.invoke(listener, resources, net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
            var apply = com.juicyslew.moonstation14.ms14.prototype.PrototypeReloadListener.class.getDeclaredMethod(
                    "apply", java.util.Map.class, net.minecraft.server.packs.resources.ResourceManager.class,
                    net.minecraft.util.profiling.ProfilerFiller.class);
            apply.setAccessible(true);
            apply.invoke(listener, prepared, resources, net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
            assertTrue(manager.commitStagedReload());
        }
        var balls = manager.snapshot(ModTeslaProfiles.BALL_TYPE);
        assertNotNull(balls.get(ModTeslaProfiles.MAIN_ID));
         assertEquals(7, balls.get(ModTeslaProfiles.MAIN_ID).shooter().range());
        var mini = balls.get(ModTeslaProfiles.MINI_ID);
        assertNotNull(mini);
        assertEquals(120, mini.timedDespawnSeconds());
        assertNull(mini.energy());
        var machines = manager.snapshot(ModTeslaProfiles.MACHINE_TYPE);
        assertEquals(java.util.Set.of("generic_machine", "tesla_coil", "tesla_grounding_rod", "tesla_generator", "emitter", "airlock", "chem_master", "apc", "hv_mv_substation"),
                machines.asMap().keySet().stream().map(net.minecraft.resources.ResourceLocation::getPath).collect(java.util.stream.Collectors.toSet()));
        var coil = machines.get(ModTeslaProfiles.id("tesla_coil"));
        assertEquals("coil", coil.kind());
        assertEquals(5_000_000, coil.capacityJoules());
        assertEquals(4, coil.flashSeconds());
        var rod = machines.get(ModTeslaProfiles.id("tesla_grounding_rod"));
        assertEquals("grounding_rod", rod.kind());
        assertEquals(4, rod.flashSeconds());
        assertEquals("generic_machine", machines.get(ModTeslaProfiles.id("generic_machine")).kind());
        var generic = machines.get(ModTeslaProfiles.id("generic_machine"));
        assertEquals(1, generic.target().priority()); assertEquals(1, generic.target().probability());
        assertEquals(1, generic.target().resistance()); assertEquals(1, generic.target().structuralDamage());
        assertTrue(generic.target().explodes());
        for (String counterpart : java.util.List.of("emitter", "airlock", "chem_master", "apc", "hv_mv_substation")) {
            var inherited = machines.get(ModTeslaProfiles.id(counterpart));
            assertEquals(counterpart, inherited.kind()); assertEquals(generic.target(), inherited.target());
            var mapped = machines.get(ModTeslaProfiles.profileForBlock(ModTeslaProfiles.id(counterpart)));
            assertSame(inherited, mapped, "live resolver mapping selects the child's typed profile: " + counterpart);
        }
        assertNull(machines.get(ModTeslaProfiles.id("tesla_generator")).target());
        assertEquals(225, machines.get(ModTeslaProfiles.COIL_ID).destructionThreshold());
        assertEquals(2, machines.get(ModTeslaProfiles.COIL_ID).salvageMin()); assertEquals(4, machines.get(ModTeslaProfiles.COIL_ID).salvageMax());
        assertEquals(new TeslaMachineProfile.Repair(1, 5, "Welding"), machines.get(ModTeslaProfiles.COIL_ID).repair());
        assertEquals(300, machines.get(ModTeslaProfiles.ROD_ID).destructionThreshold());
        assertEquals(6, machines.get(ModTeslaProfiles.COIL_ID).examineDamageMessages().size());
        assertEquals(machines.get(ModTeslaProfiles.COIL_ID).examineDamageMessages(), machines.get(ModTeslaProfiles.ROD_ID).examineDamageMessages());
        assertTrue(machines.get(ModTeslaProfiles.id("generic_machine")).examineDamageMessages().isEmpty());
        assertEquals(new TeslaMachineProfile.Repair(1, 5, "Welding"), machines.get(ModTeslaProfiles.ROD_ID).repair());
        assertEquals(200, machines.get(ModTeslaProfiles.id("emitter")).destructionThreshold());
        assertEquals(5, machines.get(ModTeslaProfiles.id("emitter")).salvageMin());
        assertEquals(50, machines.get(ModTeslaProfiles.id("chem_master")).destructionThreshold());
        assertEquals(500, machines.get(ModTeslaProfiles.id("airlock")).destructionThreshold());
        assertEquals(50, machines.get(ModTeslaProfiles.id("apc")).destructionThreshold());
        assertEquals(100, machines.get(ModTeslaProfiles.id("hv_mv_substation")).destructionThreshold());
        assertEquals(200, machines.get(ModTeslaProfiles.profileForBlock(ModTeslaProfiles.id("emitter"))).destructionThreshold());
        assertEquals(50, machines.get(ModTeslaProfiles.profileForBlock(ModTeslaProfiles.id("chem_master"))).destructionThreshold());
        assertEquals(com.juicyslew.moonstation14.ms14.singularity.ModSingularityGenerators.TESLA_ID,
                com.juicyslew.moonstation14.block.ModBlocks.TESLA_GENERATOR.get().profileId(),
                "registered Tesla generator block binds the singularity-generator catalog's actual tesla resource ID");
        var startupProfiles=manager.snapshot(com.juicyslew.moonstation14.ms14.singularity.SingularityGeneratorProfile.TYPE);
        var startup=startupProfiles.get(com.juicyslew.moonstation14.ms14.singularity.ModSingularityGenerators.TESLA_ID);
        assertNotNull(startup,"Tesla generator startup profile is discovered by the actual resource-pack reload listener");
        assertEquals(16,startup.startupThreshold());
        assertEquals(ModTeslaProfiles.MAIN_ID,startup.spawnProfile());
        assertEquals(ModTeslaProfiles.id("tesla_energy_ball"),startup.spawnEntity());
        assertEquals(16,startupProfiles.get(com.juicyslew.moonstation14.ms14.singularity.ModSingularityGenerators.DEFAULT_ID).startupThreshold(),
                "canonical default startup profile stays unchanged");
    }
    @Test void characterLightningComponentUsesUpstreamMobDefaultsAndRoundTrips() {
        var component = CharacterComponentRegistry.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"lightningTarget\"}")).getOrThrow();
        assertEquals("lightningTarget", component.type());
        var target = (com.juicyslew.moonstation14.ms14.character.components.LightningTargetComponent) component;
        assertEquals(0, target.priority()); assertEquals(1, target.probability());
        assertEquals(1, target.resistance()); assertEquals(25, target.intensity());
        assertEquals(2, target.dropoff()); assertEquals(5, target.maximumPerTile());
        assertEquals(1, target.structuralDamage()); assertTrue(target.explodes());
    }

    @Test void ballCodecRetainsGenuineOptionalAbsenceAndSemantics() {
        var mini = TeslaBallProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"shooter":{"range":3,"depth":2,"maxBolts":1,"minimumIntervalSeconds":4,"maximumIntervalSeconds":10},
                 "timedDespawnSeconds":120,"contact":{"shockDamage":7.5,"siemens":1,"stunSeconds":5}}
                """)).getOrThrow();
         assertNull(mini.energy()); assertNull(mini.jump()); assertNull(mini.horizonRadius()); assertNull(mini.orbit());
         assertNull(mini.blockConsumerRadius()); assertNull(mini.contactRadius()); assertEquals(.65, mini.resolvedBlockConsumerRadius());
         var contactOnly = TeslaBallProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                 {"shooter":{"range":3,"depth":2,"maxBolts":1,"minimumIntervalSeconds":4,"maximumIntervalSeconds":10},
                  "contactRadius":0.42}
                 """)).getOrThrow();
         assertNull(contactOnly.blockConsumerRadius()); assertEquals(.42, contactOnly.resolvedBlockConsumerRadius());
         var bothRadii = TeslaBallProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                 {"shooter":{"range":3,"depth":2,"maxBolts":1,"minimumIntervalSeconds":4,"maximumIntervalSeconds":10},
                  "contactRadius":0.65,"blockConsumerRadius":0.85}
                 """)).getOrThrow();
         assertEquals(.85, bothRadii.resolvedBlockConsumerRadius());
         assertEquals(120, mini.timedDespawnSeconds()); assertEquals(7.5, mini.contact().shockDamage());assertEquals(0,mini.contact().beamHalfWidth());
        var main = TeslaBallProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"shooter":{"range":7,"depth":3,"maxBolts":4,"minimumIntervalSeconds":3,"maximumIntervalSeconds":5},
                 "energy":{"initial":0,"gainPerConsumedEntity":2,"growthThreshold":100,"collapseThreshold":-100}}
                """)).getOrThrow();
        assertEquals(2, main.energy().gainPerConsumedEntity()); assertNull(main.contact());
        main.validate(); mini.validate();
    }

    @Test void machineProfilesDistinguishCoilAndRodPowerAndTargeting() {
        var coil = TeslaMachineProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"kind":"coil","capacityJoules":5000000,"chargePerStrikeJoules":5000000,"supplyWatts":350000,
                "rampWattsPerSecond":350000,"target":{"type":"lightningTarget","priority":4,"probability":0.5,"resistance":10,"structuralDamage":1,"explodes":false}}
                """)).getOrThrow();
        var rod = TeslaMachineProfile.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {"kind":"grounding_rod","target":{"type":"lightningTarget","priority":3,"probability":1,"resistance":10,"structuralDamage":0,"explodes":false}}
                """)).getOrThrow();
        assertEquals(5_000_000, coil.capacityJoules()); assertEquals(350_000, coil.supplyWatts());
        assertEquals(0, rod.supplyWatts()); assertEquals(3, rod.target().priority());
        assertEquals(4, coil.flashSeconds()); assertEquals(4, rod.flashSeconds());
        coil.validate(); rod.validate();
    }

    @Test void resolvedBallParentChainAndCharacterComponentInheritance() {
        var balls = new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();
        balls.register(ModTeslaProfiles.BALL_TYPE);
        var rawBalls = new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation, com.google.gson.JsonObject>();
        load(rawBalls, "tesla_ball/base.json", "base");
        load(rawBalls, "tesla_ball/tesla_energy_ball.json", "tesla_energy_ball");
        load(rawBalls, "tesla_ball/tesla_mini_energy_ball.json", "tesla_mini_energy_ball");
        balls.reload(ModTeslaProfiles.BALL_TYPE, rawBalls);
        var resolved = balls.snapshot(ModTeslaProfiles.BALL_TYPE);
        var mini = resolved.get(ModTeslaProfiles.MINI_ID);
         assertEquals(3, mini.shooter().range()); assertEquals(10, mini.shooter().maximumIntervalSeconds());
        assertEquals(120, mini.timedDespawnSeconds()); assertNull(mini.energy()); assertNull(mini.jump()); assertEquals(7.5, mini.contact().shockDamage());
         assertEquals(.65,mini.colliderRadius()); assertEquals(.90,mini.visualRadius()); assertEquals(5,mini.chasingWalk().minimumSpeed()); assertEquals(7,mini.chasingWalk().maximumSpeed());
         assertNotNull(mini.orbit()); assertEquals(.20,mini.lightning().beamHalfWidth());
        assertEquals(1,mini.chasingWalk().targetlessVerticalHalfLifeSeconds());
        assertEquals(TeslaBallProfile.Collision.NONE, mini.collision());
        var main = resolved.get(ModTeslaProfiles.MAIN_ID);
         assertEquals(7, main.shooter().range()); assertEquals(2, main.energy().gainPerConsumedEntity()); assertNull(main.contact());
          assertEquals(.75,main.colliderRadius());assertEquals(2,main.visualRadius());assertEquals(.65,main.contactRadius());assertEquals(.85,main.blockConsumerRadius());
        assertEquals(TeslaBallProfile.Collision.WORLD_AND_FIELDS, main.collision());
        assertEquals(1,main.chasingWalk().targetlessVerticalHalfLifeSeconds());

        var chars = new com.juicyslew.moonstation14.ms14.prototype.PrototypeManager();
        chars.register(com.juicyslew.moonstation14.ms14.character.ModCharacters.CHARACTER_TYPE);
        var rawChars = new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation, com.google.gson.JsonObject>();
        load(rawChars, "character/supported_simple_mob.json", "supported_simple_mob");
        load(rawChars, "character/supported_animal_core.json", "supported_animal_core");
        load(rawChars, "character/supported_animal.json", "supported_animal");
        load(rawChars, "character/supported_npc.json", "supported_npc");
        load(rawChars, "character/pig.json", "pig"); load(rawChars, "character/mouse.json", "mouse");
        load(rawChars, "character/human.json", "human"); load(rawChars, "character/observer_base.json", "observer_base");
        load(rawChars, "character/observer.json", "observer");
        chars.reload(com.juicyslew.moonstation14.ms14.character.ModCharacters.CHARACTER_TYPE, rawChars);
        var catalog = chars.snapshot(com.juicyslew.moonstation14.ms14.character.ModCharacters.CHARACTER_TYPE);
        assertNotNull(catalog.get(com.juicyslew.moonstation14.ms14.character.ModCharacters.HUMAN_ID).component(com.juicyslew.moonstation14.ms14.character.components.LightningTargetComponent.class).orElse(null));
        for (String id : java.util.List.of("pig", "mouse"))
            assertNotNull(catalog.get(ModTeslaProfiles.id(id)).component(com.juicyslew.moonstation14.ms14.character.components.LightningTargetComponent.class).orElse(null), id);
        assertTrue(catalog.get(ModTeslaProfiles.id("observer")).component(com.juicyslew.moonstation14.ms14.character.components.LightningTargetComponent.class).isEmpty());
    }

    private static void load(java.util.Map<net.minecraft.resources.ResourceLocation, com.google.gson.JsonObject> raw, String path, String id) {
        try (var stream = TeslaProfilesTest.class.getResourceAsStream("/data/moonstation14/moonstation14/" + path)) {
            assertNotNull(stream, path);
            raw.put(ModTeslaProfiles.id(id), JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
    }
}
