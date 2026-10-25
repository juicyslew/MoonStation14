package com.juicyslew.moonstation14.ms14.character;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.ms14.animals.AnimalCatalog;
import com.juicyslew.moonstation14.ms14.character.components.AnimalAppearanceComponent;
import com.juicyslew.moonstation14.ms14.character.components.MobThresholdsComponent;
import com.juicyslew.moonstation14.ms14.character.components.MovementSpeedModifierComponent;
import com.juicyslew.moonstation14.ms14.character.components.NpcRetaliationComponent;
import com.juicyslew.moonstation14.ms14.character.components.FlightComponent;
import com.juicyslew.moonstation14.ms14.character.components.HungerPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.ThirstPrototypeComponent;
import com.juicyslew.moonstation14.ms14.character.components.RespiratorComponent;
import com.juicyslew.moonstation14.ms14.character.components.MeleeAttackComponent;
import com.juicyslew.moonstation14.ms14.character.components.SpeechComponent;
import com.juicyslew.moonstation14.ms14.character.components.SpeciesComponent;
import com.juicyslew.moonstation14.ms14.character.components.BloodstreamComponent;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AnimalPrototypeTest {
    @Test void everyCatalogHostResolvesAppearanceIdentityThresholdsAndMovement() throws Exception {
        Path root = Path.of(System.getProperty("moonstation14.projectDir"), "src/main/resources/data/moonstation14/moonstation14/character");
        Map<ResourceLocation, JsonObject> data = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = root.relativize(file).toString().replace('\\','/');
                data.put(ResourceLocation.fromNamespaceAndPath("moonstation14", name.substring(0,name.length()-5)), JsonParser.parseString(Files.readString(file)).getAsJsonObject());
            }
        }
        // Exercise the exact cross-catalog validator used while staging production reloads,
        // against the complete shipped character and reagent resource sets (including parents).
        Path reagentRoot = root.getParent().resolve("reagent");
        Map<ResourceLocation, JsonObject> reagents = new LinkedHashMap<>();
        try (var paths = Files.walk(reagentRoot)) {
            for (Path file : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = reagentRoot.relativize(file).toString().replace('\\','/');
                reagents.put(ResourceLocation.fromNamespaceAndPath("moonstation14", name.substring(0,name.length()-5)),
                        JsonParser.parseString(Files.readString(file)).getAsJsonObject());
            }
        }
        ModCharacters.validateReagentReferences(Map.of(
                ModCharacters.CHARACTER_TYPE.typeId(), data,
                com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_TYPE.typeId(), reagents));
        assertTrue(reagents.containsKey(ResourceLocation.parse("moonstation14:insectblood")));
        assertTrue(reagents.containsKey(ResourceLocation.parse("moonstation14:copperblood")));
        PrototypeManager manager = new PrototypeManager(); manager.register(ModCharacters.CHARACTER_TYPE); manager.reload(ModCharacters.CHARACTER_TYPE,data);
        var resolved = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        assertEquals(4.0, resolved.get(ResourceLocation.parse("moonstation14:bat"))
                .component(FlightComponent.class).orElseThrow().npcCruiseHeight());
        var batFlight = resolved.get(ResourceLocation.parse("moonstation14:bat")).component(FlightComponent.class).orElseThrow();
        assertEquals(0.80, batFlight.airborneSpeedMultiplier(), "resolved prototype default");
        assertEquals(4, batFlight.throwRecoveryTicks(), "resolved prototype default");
        for (String flyer : java.util.List.of("bat", "bee", "butterfly", "parrot"))
            assertEquals(.25, resolved.get(ResourceLocation.parse("moonstation14:" + flyer))
                    .component(FlightComponent.class).orElseThrow().groundedSpeedMultiplier(), flyer);
        var gorillaMovement = resolved.get(ResourceLocation.parse("moonstation14:gorilla"))
                .component(MovementSpeedModifierComponent.class).orElseThrow();
        assertEquals(2.0, gorillaMovement.walkSpeed(), "prototype value before Minecraft scale");
        assertEquals(4.0, gorillaMovement.sprintSpeed(), "prototype value before Minecraft scale");
        var gorillaRuntimeMovement = com.juicyslew.moonstation14.ms14.movement.CharacterMovementPolicy
                .fromCharacterData(resolved.get(ResourceLocation.parse("moonstation14:gorilla")));
        assertEquals(2.5, gorillaRuntimeMovement.walkSpeedPerSecond(), "runtime speed after Minecraft scale");
        assertEquals(5.0, gorillaRuntimeMovement.sprintSpeedPerSecond(), "runtime speed after Minecraft scale");
        assertTrue(gorillaMovement.walkSpeed() < gorillaMovement.sprintSpeed());
        ResourceLocation flightChildId = ResourceLocation.parse("moonstation14:flight_height_child_fixture");
        data.put(flightChildId, JsonParser.parseString("""
                {"parent":"moonstation14:bat","host_entity_types":[],"components":[{"type":"Flight","npc_cruise_height":2.25,"airborne_speed_multiplier":1.5,"throw_recovery_ticks":8}]}
                """).getAsJsonObject());
        ModCharacters.validateReagentReferences(Map.of(
                ModCharacters.CHARACTER_TYPE.typeId(), data,
                com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_TYPE.typeId(), reagents));
        manager.reload(ModCharacters.CHARACTER_TYPE, data);
        var inheritedFlight = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        assertTrue(inheritedFlight.get(flightChildId).hostEntityTypes().isEmpty(), "template child adds no duplicate concrete host binding");
        assertEquals(2.25, inheritedFlight.get(flightChildId).component(FlightComponent.class)
                .orElseThrow().npcCruiseHeight(), "child override is resolved for the runtime NPC consumer");
        assertEquals(.25, inheritedFlight.get(flightChildId).component(FlightComponent.class)
                .orElseThrow().groundedSpeedMultiplier(), "unspecified child field inherits the flyer adaptation");
        assertEquals(1.5, inheritedFlight.get(flightChildId).component(FlightComponent.class).orElseThrow().airborneSpeedMultiplier());
        assertEquals(8, inheritedFlight.get(flightChildId).component(FlightComponent.class).orElseThrow().throwRecoveryTicks());
        com.juicyslew.moonstation14.ms14.character.components.MovementSpeedModifierComponent authoredBatMovement =
                resolved.get(ResourceLocation.parse("moonstation14:bat")).component(MovementSpeedModifierComponent.class).orElseThrow();
        assertEquals(3.0, authoredBatMovement.walkSpeed(), "flight fields do not alter authored walk speed");
        assertEquals(6.0, authoredBatMovement.sprintSpeed(), "flight fields do not alter authored sprint speed");
        assertEquals(400, inheritedFlight.get(flightChildId).component(FlightComponent.class).orElseThrow().npcAirborneTicks());
        assertEquals(160, inheritedFlight.get(flightChildId).component(FlightComponent.class).orElseThrow().npcGroundedTicks());
        assertEquals(.4, inheritedFlight.get(flightChildId).component(FlightComponent.class).orElseThrow().npcVerticalSpeedMultiplier());
        assertEquals(4.0, inheritedFlight.get(ResourceLocation.parse("moonstation14:bat"))
                .component(FlightComponent.class).orElseThrow().npcCruiseHeight(), "child override leaves parent default intact");
        ResourceLocation flightBoundaryId = ResourceLocation.parse("moonstation14:flight_height_boundary_fixture");
        data.put(flightBoundaryId, JsonParser.parseString("""
                {"parent":"moonstation14:bat","host_entity_types":[],"components":[{"type":"Flight","npc_cruise_height":6.0}]}
                """).getAsJsonObject());
        ModCharacters.validateReagentReferences(Map.of(
                ModCharacters.CHARACTER_TYPE.typeId(), data,
                com.juicyslew.moonstation14.ms14.reagent.ModReagents.REAGENT_TYPE.typeId(), reagents));
        manager.reload(ModCharacters.CHARACTER_TYPE, data);
        var boundaryFlight = manager.snapshot(ModCharacters.CHARACTER_TYPE);
        assertEquals(FlightComponent.MAX_NPC_CRUISE_HEIGHT, boundaryFlight.get(flightBoundaryId)
                .component(FlightComponent.class).orElseThrow().npcCruiseHeight(), "schema and prototype resolution admit the maximum");
        assertEquals(.25, boundaryFlight.get(flightBoundaryId).component(FlightComponent.class)
                .orElseThrow().groundedSpeedMultiplier(), "maximum-height child inherits grounded speed");
        assertEquals(36, AnimalCatalog.entries().size());
        Path englishPath = Path.of(System.getProperty("moonstation14.projectDir"), "src/main/resources/assets/moonstation14/lang/en_us.json");
        JsonObject english = JsonParser.parseString(Files.readString(englishPath)).getAsJsonObject();
        for (var host : AnimalCatalog.entries()) {
            String key = "entity.moonstation14." + host.id();
            assertTrue(english.has(key), "missing host translation " + key);
            assertFalse(english.get(key).getAsString().isBlank(), "blank host translation " + key);
        }
        assertEquals("Poppy", english.get("entity.moonstation14.possum_poppy").getAsString());
        assertEquals(40, resolved.asMap().entrySet().stream().filter(e -> e.getValue().component(AnimalAppearanceComponent.class).isPresent())
                .mapToInt(e -> e.getValue().component(AnimalAppearanceComponent.class).orElseThrow().modelIds().size()).sum());
        assertFalse(AnimalCatalog.entries().stream().anyMatch(e -> e.id().equals("rat")));
        for (var host : AnimalCatalog.entries()) {
            var profile = resolved.asMap().entrySet().stream().filter(e -> e.getValue().hostEntityTypes().contains(host.host())).map(Map.Entry::getValue).findFirst().orElseThrow(() -> new AssertionError("unbound host " + host.host()));
            assertEquals(host.modelId(), profile.component(AnimalAppearanceComponent.class).orElseThrow().modelIds().getFirst());
            assertTrue(profile.component(SpeciesComponent.class).isPresent());
            var bloodstream = profile.component(BloodstreamComponent.class).orElseThrow().policy();
            Set<String> knownReagents = reagents.keySet().stream().map(ResourceLocation::toString)
                    .collect(java.util.stream.Collectors.toSet());
            assertTrue(knownReagents.containsAll(bloodstream.referenceSolution().keySet()), host.id() + " reference solution");
            assertTrue(knownReagents.containsAll(bloodstream.metabolismExclusions().stream()
                    .map(ResourceLocation::toString).toList()), host.id() + " metabolism exclusions");
            assertTrue(bloodstream.metabolismExclusions().stream().map(ResourceLocation::toString)
                    .collect(java.util.stream.Collectors.toSet()).containsAll(bloodstream.referenceSolution().keySet()), host.id());
            var thresholds=profile.component(MobThresholdsComponent.class).orElseThrow();
            double critical = switch (host.id()) { case "bat" -> 30; case "gorilla" -> 200; case "kobold","scurret" -> 60; case "dionanymph" -> 30; case "hamster","mothroach","snoth" -> 40; case "moproach" -> 45; case "snail" -> 10; case "giant_spider","bee","butterfly","cockroach" -> -1; default -> 100; };
            double dead = switch (host.id()) { case "bee", "butterfly" -> 10; case "cockroach" -> 1; case "bat" -> 60; case "gorilla" -> 250; case "kobold","scurret" -> 125; case "giant_spider" -> 90; case "hamster","mothroach","snoth" -> 60; case "moproach" -> 65; case "dionanymph" -> 60; case "snail" -> 20; default -> 200; };
            assertEquals(dead, thresholds.thresholds().entrySet().stream().filter(e->e.getValue().name().equals("DEAD")).findFirst().orElseThrow().getKey());
            if (critical < 0) assertFalse(thresholds.thresholds().values().stream().anyMatch(s -> s.name().equals("CRITICAL")));
            else assertEquals(critical, thresholds.thresholds().entrySet().stream().filter(e->e.getValue().name().equals("CRITICAL")).findFirst().orElseThrow().getKey());
            var speed = profile.component(MovementSpeedModifierComponent.class).orElseThrow();
            assertEquals(switch(host.id()) { case "bat","bee","butterfly","frog" -> 3.0; case "cockroach","mothroach","moproach","hamster","dionanymph","parrot","snoth" -> 2.5; case "kangaroo","kobold" -> 3.5; case "gorilla","slug","snail","lizard" -> 2.0; case "scurret" -> 5.0; default -> 4.0; }, speed.walkSpeed(), host.id());
            assertEquals(switch(host.id()) { case "bee" -> 7.0; case "bat","butterfly","frog","parrot" -> 6.0; case "cockroach","moproach" -> 5.0; case "hamster","mothroach" -> 4.0; case "kangaroo" -> 4.5; case "gorilla" -> 4.0; case "kobold" -> 5.0; case "lizard","slug","snail" -> 3.0; case "dionanymph" -> 4.5; case "scurret" -> 7.0; default -> 4.0; }, speed.sprintSpeed(), host.id());
            boolean handed = host.id().equals("monkey") || host.id().equals("kobold") || host.id().equals("scurret");
            assertEquals(handed, profile.component(com.juicyslew.moonstation14.ms14.character.components.HandsPrototypeComponent.class).isPresent());
            boolean retaliator = java.util.Set.of("goat","gorilla","kangaroo","monkey","kobold","scurret","fox","reindeer_buck","reindeer_doe").contains(host.id());
            assertEquals(retaliator, profile.component(NpcRetaliationComponent.class).isPresent(), host.id());
            assertTrue(profile.component(MeleeAttackComponent.class).isPresent(), host.id());
            boolean flying = java.util.Set.of("bat","bee","butterfly","parrot").contains(host.id());
            assertEquals(flying, profile.component(FlightComponent.class).isPresent(), host.id());
            boolean role = java.util.Set.of("mothroach","moproach","hamster","snail","kangaroo","monkey","kobold").contains(host.id());
            assertEquals(role, profile.component(com.juicyslew.moonstation14.ms14.character.components.GhostRoleComponent.class).isPresent(), host.id());
            boolean portable = java.util.Set.of("bee","butterfly","cockroach","hamster","mothroach","moproach").contains(host.id());
            assertEquals(portable, profile.component(com.juicyslew.moonstation14.ms14.character.components.PortableBodyComponent.class).isPresent(), host.id());
            boolean escapable = host.id().equals("hamster") || host.id().equals("mothroach") || host.id().equals("moproach");
            assertEquals(escapable, profile.component(com.juicyslew.moonstation14.ms14.character.components.CanEscapeInventoryComponent.class).isPresent(), host.id());
            var hunger = profile.component(HungerPrototypeComponent.class).orElseThrow();
            assertEquals(host.id().equals("snail") ? 35.0 : 100.0, hunger.overfed(), host.id());
            assertTrue(profile.component(ThirstPrototypeComponent.class).isPresent(), host.id());
            assertEquals(!host.id().equals("snail"), profile.component(RespiratorComponent.class).isPresent(), host.id());
        }
        var chicken = resolved.get(ResourceLocation.parse("moonstation14:chicken"));
        assertEquals(java.util.List.of("chicken_0","chicken_1","chicken_2"), chicken.component(AnimalAppearanceComponent.class).orElseThrow().modelIds());
        var duck = resolved.get(ResourceLocation.parse("moonstation14:duck"));
        assertEquals(java.util.List.of("duck_0","duck_1","duck_2"), duck.component(AnimalAppearanceComponent.class).orElseThrow().modelIds());
        assertTrue(resolved.get(ResourceLocation.parse("moonstation14:cow")).component(com.juicyslew.moonstation14.ms14.character.components.UdderComponent.class).isPresent());
        assertEquals(50f, resolved.get(ResourceLocation.parse("moonstation14:cow"))
                .component(HungerPrototypeComponent.class).orElseThrow().okay());
        assertEquals(150f, resolved.get(ResourceLocation.parse("moonstation14:human"))
                .component(HungerPrototypeComponent.class).orElseThrow().okay());
        assertTrue(resolved.get(ResourceLocation.parse("moonstation14:goat")).component(com.juicyslew.moonstation14.ms14.character.components.UdderComponent.class).isPresent());
        var moproach = resolved.get(ResourceLocation.parse("moonstation14:moproach"));
        assertEquals(java.util.List.of(ResourceLocation.parse("moonstation14:moproach")), moproach.hostEntityTypes());
        assertTrue(moproach.component(com.juicyslew.moonstation14.ms14.character.components.PortableBodyComponent.class).isPresent());
        assertTrue(moproach.component(com.juicyslew.moonstation14.ms14.character.components.CanEscapeInventoryComponent.class).isPresent());
        assertEquals(ResourceLocation.parse("moonstation14:hamster"), moproach.component(com.juicyslew.moonstation14.ms14.character.components.EquipmentSlotsPrototypeComponent.class).orElseThrow().template().orElseThrow());
        assertEquals("moproach", moproach.component(AnimalAppearanceComponent.class).orElseThrow().modelIds().getFirst());
        var poppy = resolved.get(ResourceLocation.parse("moonstation14:possum_poppy"));
        assertEquals("Poppy", poppy.metadata().name());
        assertEquals("Possum", poppy.component(SpeciesComponent.class).orElseThrow().label());
        assertEquals(java.util.List.of(ResourceLocation.parse("moonstation14:possum_poppy")), poppy.hostEntityTypes());
        assertEquals("poppy", poppy.component(AnimalAppearanceComponent.class).orElseThrow().modelIds().getFirst());
        var parrot = resolved.get(ResourceLocation.parse("moonstation14:parrot"));
        assertTrue(parrot.component(com.juicyslew.moonstation14.ms14.character.components.ParrotListenerComponent.class).isPresent());
        assertTrue(parrot.component(com.juicyslew.moonstation14.ms14.character.components.ParrotMemoryComponent.class).isPresent());
        var kobold = resolved.get(ResourceLocation.parse("moonstation14:kobold"));
        var koboldTemp = kobold.component(com.juicyslew.moonstation14.ms14.character.components.TemperatureDamageComponent.class).orElseThrow();
        assertEquals(360.0, koboldTemp.heatDamageThresholdKelvin());
        assertEquals(285.0, koboldTemp.coldDamageThresholdKelvin());
        var scurretTemp = resolved.get(ResourceLocation.parse("moonstation14:scurret"))
                .component(com.juicyslew.moonstation14.ms14.character.components.TemperatureDamageComponent.class).orElseThrow();
        assertEquals(360.0, scurretTemp.heatDamageThresholdKelvin());
        assertEquals(285.0, scurretTemp.coldDamageThresholdKelvin());
        var penguin = resolved.get(ResourceLocation.parse("moonstation14:penguin"));
        assertEquals(46.0, penguin.component(com.juicyslew.moonstation14.ms14.character.components.TemperatureComponent.class)
                .orElseThrow().specificHeatJoulesPerKgKelvin());
        var penguinDamage = penguin.component(com.juicyslew.moonstation14.ms14.character.components.TemperatureDamageComponent.class).orElseThrow();
        assertEquals(335.0, penguinDamage.heatDamageThresholdKelvin());
        assertEquals(230.0, penguinDamage.coldDamageThresholdKelvin());
        assertEquals(.2, penguinDamage.heatDamagePerSecond());
        assertEquals(.05, penguinDamage.coldDamagePerSecond());
        var nymphDamage = resolved.get(ResourceLocation.parse("moonstation14:dionanymph"))
                .component(com.juicyslew.moonstation14.ms14.character.components.TemperatureDamageComponent.class).orElseThrow();
        assertEquals(325.0, nymphDamage.heatDamageThresholdKelvin());
        assertEquals(260.0, nymphDamage.coldDamageThresholdKelvin());
        var snail = resolved.get(ResourceLocation.parse("moonstation14:snail"));
        var snailDamage = snail.component(com.juicyslew.moonstation14.ms14.character.components.TemperatureDamageComponent.class).orElseThrow();
        assertEquals(500.0, snailDamage.heatDamageThresholdKelvin());
        assertEquals(.1, snailDamage.coldDamagePerSecond());
        assertEquals(0.0, snailDamage.coldDamageThresholdKelvin(), "MobSnail source sets coldDamageThreshold to absolute zero");
        assertFalse(com.juicyslew.moonstation14.ms14.atmos.exposure.BodyTemperatureSystem
                .thresholdDamage(260.0, snailDamage.toProfile(), 1.0).containsKey("cold"),
                "the resolved snail policy reaches the existing runtime damage projection without frost damage");
        var snailRegulator = snail.component(com.juicyslew.moonstation14.ms14.character.components.ThermalRegulatorComponent.class).orElseThrow();
        assertEquals(500.0, snailRegulator.implicitHeatRegulationJoulesPerSecond());
        assertEquals(2000.0, snailRegulator.sweatHeatRegulationJoulesPerSecond());
        assertEquals(2000.0, snailRegulator.shiveringHeatRegulationJoulesPerSecond());
        assertEquals(.2f, AnimalCatalog.entries().stream().filter(e -> e.id().equals("bee")).findFirst().orElseThrow().width());
        assertEquals(.7f, AnimalCatalog.entries().stream().filter(e -> e.id().equals("giant_spider")).findFirst().orElseThrow().width());
    }
}
