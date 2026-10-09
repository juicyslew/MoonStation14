package com.juicyslew.moonstation14.ms14.examine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.mobs.MobStateAttachment;
import com.juicyslew.moonstation14.ms14.mobs.MobStateData;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import com.juicyslew.moonstation14.ms14.puddle.ModPuddles;
import com.juicyslew.moonstation14.ms14.puddle.PuddleData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

@PrefixGameTestTemplate(false)
@GameTestTags("examine_puddle")
public final class PuddleExamineGameTests {
    private PuddleExamineGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void resolvedPuddleMetadataAndStoredSolutionAreReadOnlyAndDetailGated(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 5));
        level.setBlock(pos, ModBlocks.PUDDLE.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(pos) instanceof PuddleBlockEntity puddle))
            throw new GameTestAssertException("placed puddle has its live reagent block entity");
        PuddleData inheritedChild = resolveSmallPuddle();
        puddle.bindResolvedPuddleData(inheritedChild);
        require(puddle.getResolvedPuddleData() == inheritedChild && puddle.getCapacity() == 500f,
                "live puddle consumes the instance-bound inherited child capacity");
        require("Puddle".equals(inheritedChild.metadata().name())
                        && "A puddle of liquid.".equals(inheritedChild.metadata().description())
                        && "puddle".equals(inheritedChild.examinableSolution().solutionName())
                        && !inheritedChild.examinableSolution().exactVolume(),
                "child resolves inherited metadata and examinable-solution defaults");
        var before = MS14Provider.getDetached(puddle, MS14Bridges.REAGENT);
        ReagentAttachment contents = MS14Provider.getDetached(puddle, MS14Bridges.REAGENT);
        contents.specificAdd(ModReagents.createKey("water"), 150f, puddle.getCapacity());
        contents.specificAdd(ModReagents.createKey("blood"), 100f, puddle.getCapacity());
        contents.specificAdd(ModReagents.createKey("foamingagent"), 50f, puddle.getCapacity());
        require(MS14Provider.updateIfChanged(puddle, MS14Bridges.REAGENT, before.toComponent(), contents),
                "test setup commits stored water through the existing authoritative bridge");

        Villager actor = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        require(CharacterIdentitySystem.enroll(actor, level, ModCharacters.HUMAN_ID), "fixture actor enrolls as human");
        if (actor.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get()) == null)
            actor.setData(ModDataAttachments.MOB_STATE.get(), new MobStateAttachment(MobStateData.DEFAULT));

        ExamineContext publicContext = new ExamineContext(actor, level, ExamineTarget.block(pos), null, pos,
                level.getBlockState(pos), puddle, false);
        ExamineCollector publicCollector = new ExamineCollector(false, level.getBlockState(pos).getBlock().getName());
        BuiltinExamineContributors.metadata(publicContext, publicCollector);
        BuiltinExamineContributors.puddleSolution(publicContext, publicCollector);
        require("Puddle".equals(publicCollector.name().getString()), "resolved puddle name is used");
        require(!publicCollector.name().getString().contains("block.moonstation14.puddle"),
                "examine collector uses resolved metadata rather than raw translation key");
        require("Puddle".equals(ModBlocks.PUDDLE.get().getName().getString()), "block preview has localized display name");
        require("A puddle of liquid.".equals(publicCollector.description().getString()), "resolved public description is used");
        require(publicCollector.sortedEntries().isEmpty(), "solution details are absent when details are disallowed");

        ExamineContext detailsContext = new ExamineContext(actor, level, ExamineTarget.block(pos), null, pos,
                level.getBlockState(pos), puddle, true);
        ExamineCollector detailsCollector = new ExamineCollector(true);
        BuiltinExamineContributors.puddleSolution(detailsContext, detailsCollector);
        String text = detailsCollector.sortedEntries().stream().map(entry -> entry.text().getString())
                .collect(java.util.stream.Collectors.joining(" "));
        boolean halfFull = detailsCollector.sortedEntries().stream()
                .filter(entry -> entry.group().equals("puddle-solution"))
                .map(entry -> entry.text().getContents())
                .filter(net.minecraft.network.chat.contents.TranslatableContents.class::isInstance)
                .map(net.minecraft.network.chat.contents.TranslatableContents.class::cast)
                .anyMatch(translatable -> componentTranslationKeys((net.minecraft.network.chat.Component)
                        translatable.getArgs()[0]).contains("examine.moonstation14.puddle.volume.half-full"));
        require(halfFull, "provider uses bound 500-capacity child against live stored 300-unit solution: " + text
                + ", capacity=" + puddle.getCapacity() + ", units="
                + MS14Provider.getDetached(puddle, MS14Bridges.REAGENT).totalUnits());
        boolean recognizableWater = detailsCollector.sortedEntries().stream()
                .filter(entry -> entry.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translatable
                        && translatable.getKey().equals("examine.moonstation14.puddle.recognized"))
                .anyMatch(entry -> componentTranslationKeys(entry.text()).contains("reagent-name-water"));
        require(recognizableWater, "recognizable stored water is named");
        var recognizedEntry = detailsCollector.sortedEntries().stream()
                .filter(entry -> entry.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translatable
                        && translatable.getKey().equals("examine.moonstation14.puddle.recognized"))
                .findFirst().orElseThrow(() -> new GameTestAssertException("recognized names are present"));
        var recognizedKeys = componentTranslationKeys(recognizedEntry.text());
        require(recognizedKeys.contains("reagent-name-water") && recognizedKeys.contains("reagent-name-blood")
                        && !recognizedKeys.contains("reagent-name-foamingagent"),
                "only recognizable water and blood names appear: " + recognizedKeys);
        var expectedColor = com.juicyslew.moonstation14.ms14.reagent.ReagentComponent.getBlendedColor(
                MS14Provider.getDetached(puddle, MS14Bridges.REAGENT).getMap(), level);
        var physicalEntry = detailsCollector.sortedEntries().stream()
                .filter(entry -> entry.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translatable
                        && translatable.getKey().equals("examine.moonstation14.puddle.physical.plural"))
                .findFirst().orElseThrow(() -> new GameTestAssertException("primary physical description is present"));
        var physicalArgs = ((net.minecraft.network.chat.contents.TranslatableContents) physicalEntry.text().getContents()).getArgs();
        var description = (net.minecraft.network.chat.Component) physicalArgs[1];
        require(description.getStyle().getColor() != null
                        && description.getStyle().getColor().getValue() == (expectedColor & 0x00FFFFFF),
                "only primary physical-description component uses existing mixture color blend: expected="
                        + Integer.toHexString(expectedColor) + ", actual=" + description.getStyle().getColor());
        require(MS14Provider.getDetached(puddle, MS14Bridges.REAGENT).totalUnits() == 30000,
                "examination did not mutate stored solution");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private static java.util.Set<String> componentTranslationKeys(net.minecraft.network.chat.Component component) {
        var keys = new java.util.HashSet<String>();
        collectTranslationKeys(component, keys);
        return keys;
    }
    private static void collectTranslationKeys(net.minecraft.network.chat.Component component, java.util.Set<String> keys) {
        if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translatable) {
            keys.add(translatable.getKey());
            for (Object argument : translatable.getArgs())
                if (argument instanceof net.minecraft.network.chat.Component nested) collectTranslationKeys(nested, keys);
        }
        component.getSiblings().forEach(sibling -> collectTranslationKeys(sibling, keys));
    }

    private static PuddleData resolveSmallPuddle() {
        String path = "/data/moonstation14/moonstation14/puddle/puddle.json";
        try (var stream = PuddleExamineGameTests.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("missing root puddle prototype resource");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject child = new JsonObject();
            child.addProperty("id", "small_puddle_fixture");
            child.addProperty("parent", "puddle");
            JsonObject solution = new JsonObject();
            solution.addProperty("maxVolume", 500f);
            child.add("solutionContainer", solution);
            var raw = new LinkedHashMap<ResourceLocation, JsonObject>();
            raw.put(ModPuddles.PUDDLE, root);
            raw.put(ResourceLocation.fromNamespaceAndPath("moonstation14", "small_puddle_fixture"), child);
            var manager = new PrototypeManager();
            manager.register(ModPuddles.TYPE);
            manager.reload(ModPuddles.TYPE, raw);
            return manager.snapshot(ModPuddles.TYPE)
                    .get(ResourceLocation.fromNamespaceAndPath("moonstation14", "small_puddle_fixture"));
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("could not load puddle prototype fixture", failure);
        }
    }
}
