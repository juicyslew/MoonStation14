package com.juicyslew.moonstation14.ms14.botany;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Native coverage of the generic species transition and harvest consumer (not an upstream wheat mutation edge). */
@GameTestTags({"botany-genetics", "botany-species-mutation"})
@PrefixGameTestTemplate(false)
public final class PlantSpeciesMutationGameTests {
    private static final ResourceLocation WHEAT = ModSeedProfiles.seedId("wheat");
    private static final ResourceLocation TOMATO = ModSeedProfiles.seedId("tomato");

    private PlantSpeciesMutationGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 100)
    public static void speciesMutationFeedsResolvedProduceFactory(GameTestHelper helper) {
        BotanyTrayGameTestEnvironment.prepare(helper, tray -> {
            try {
                var catalog = ModSeedProfiles.serverCatalog();
                var wheat = catalog.get(WHEAT);
                var tomato = catalog.get(TOMATO);
                require(wheat != null && tomato != null, "resolved wheat and tomato profiles available");
                require(!wheat.sourceName().equals(tomato.sourceName()), "resolved source names distinguish wheat donor from tomato target");
                // Deliberate synthetic test-only edge; the current catalog has no wheat -> tomato target
                // and this does not claim that edge exists in the pinned Space Station 14 data.
                var arrangedChemicals = new java.util.LinkedHashMap<>(wheat.chemicals());
                var flour = ResourceLocation.fromNamespaceAndPath("moonstation14", "flour");
                var flourGene = arrangedChemicals.get(flour);
                require(flourGene != null && PrototypeRuntime.serverReagents().get(flour) != null,
                        "registered wheat flour reagent available as acquired marker");
                // Test-only acquired gene: wheat's inherent flour is made non-inherent to model acquisition.
                arrangedChemicals.put(flour, new SeedProfile.Chemical(flourGene.min(), flourGene.max(),
                        flourGene.potencyDivisor(), false));
                var arranged = new SeedProfile(wheat.packet(), wheat.product(), wheat.visualKey(), wheat.biology(),
                        wheat.lifecycle(), arrangedChemicals, wheat.consumeGasses(), wheat.exudeGasses(), List.of(TOMATO), wheat.scream(), wheat.sourceName());
                tray.commitPlant(WHEAT, arranged, ((ServerLevel) helper.getLevel()).getGameTime());
                var plant = tray.plantState();
                plant.state(0.73f, 0.61f, 0.24f, 0.13f, true, false);
                plant.adjustMutationLevel(1f);
                var before = plant.genetics();

                var entry = new PlantMutationTable.Entry("synthetic_species_transition", 1f, true, true, true,
                        PlantMutationTable.EffectKind.SPECIES, Optional.empty(), Optional.empty(), "");
                var result = PlantMutationSystem.cycle(plant, new PlantMutationTable(List.of(entry)),
                        new Random(0x51ec1eL), () -> 0f, ModWeightedRandomFillSolutions.serverCatalog(),
                        PrototypeRuntime.serverReagents(), catalog);

                require(result.severity() == 1f && result.unsupported().isEmpty(), "deterministic explicit species cycle applied");
                require(plant.seed().equals(TOMATO) && plant.genetics().species().equals(TOMATO), "holder identity transitioned to tomato");
                var genetics = plant.genetics();
                require(genetics.profile().packet().equals(tomato.packet())
                                && genetics.profile().product().equals(tomato.product())
                                && genetics.profile().visualKey().equals(tomato.visualKey())
                                && genetics.profile().sourceName().equals(tomato.sourceName())
                                && !genetics.profile().sourceName().equals(wheat.sourceName()),
                        "target identity and visual/product metadata come from resolved tomato");
                var tomatoVitamin = tomato.chemicals().get(ResourceLocation.fromNamespaceAndPath("moonstation14", "vitamin"));
                require(tomatoVitamin != null && genetics.profile().chemicals().get(tomatoVitaminId()).equals(tomatoVitamin),
                        "resolved tomato inherent vitamin gene is introduced");
                require(genetics.profile().chemicals().get(flour).equals(arrangedChemicals.get(flour))
                                && !genetics.profile().chemicals().get(flour).inherent(),
                        "acquired wheat flour gene is retained exactly");
                require(genetics.profile().chemicals().get(ResourceLocation.fromNamespaceAndPath("moonstation14", "water"))
                                .equals(tomato.chemicals().get(ResourceLocation.fromNamespaceAndPath("moonstation14", "water"))),
                        "resolved tomato inherent water gene is introduced");
                require(genetics.profile().biology().equals(before.profile().biology())
                                && genetics.profile().lifecycle().lifespan() == before.profile().lifecycle().lifespan()
                                && genetics.profile().lifecycle().maturation() == before.profile().lifecycle().maturation()
                                && genetics.profile().lifecycle().production() == before.profile().lifecycle().production()
                                && genetics.profile().lifecycle().yield() == before.profile().lifecycle().yield()
                                && genetics.profile().lifecycle().potency() == before.profile().lifecycle().potency()
                                && genetics.profile().lifecycle().harvest() == before.profile().lifecycle().harvest()
                                && genetics.profile().lifecycle().viable() == before.profile().lifecycle().viable()
                                && genetics.profile().lifecycle().seedless() == before.profile().lifecycle().seedless(),
                        "wheat genetic stats are retained");
                require(plant.age() == .73f && plant.health() == .61f && plant.lastProduceAge() == .24f
                                && plant.skipAging() == .13f && plant.ready() && !plant.dead(),
                        "species transition preserves holder lifecycle state");
                require(plant.mutationLevel() == 0f, "successful mutation cycle consumes mutation level");

                float potency = 5f;
                ItemStack produce = ProduceFactory.createHarvest(genetics, potency);
                require(produce.getItem() == net.minecraft.core.registries.BuiltInRegistries.ITEM.get(tomato.product()),
                        "harvest factory creates the resolved tomato product");
                var produceGenetics = produce.get(ModDataComponents.GENETIC_VALUE.get());
                require(genetics.equals(produceGenetics) && produceGenetics.species().equals(TOMATO),
                        "produce carries transitioned genetic value");
                require(produceGenetics.profile().sourceName().equals(tomato.sourceName()),
                        "harvest carrier retains transitioned resolved source name");
                var contents = produce.get(ModDataComponents.REAGENT.get());
                var expectedChemistry = ProduceFactory.generatedChemistry(genetics.profile(), potency);
                require(contents != null && contents.centContents().equals(expectedChemistry),
                        "harvest chemistry uses retained current genetics profile, not tomato defaults");
                helper.succeed();
            } catch (Throwable failure) {
                helper.testInfo.fail(failure);
            }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static ResourceLocation tomatoVitaminId() {
        return ResourceLocation.fromNamespaceAndPath("moonstation14", "vitamin");
    }
}
