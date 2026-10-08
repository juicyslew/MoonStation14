package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.custom.JugItem;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;


@PrefixGameTestTemplate(false)
@GameTestTags("examine_jug")
public final class JugExamineGameTests {
    private JugExamineGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void droppedJugExaminesResolvedMetadataAndReadOnlyContents(GameTestHelper helper) {
        var level = helper.getLevel();
        JugItem jug = (JugItem) ModItems.JUG.get();
        ItemStack stack = new ItemStack(jug);
        ReagentAttachment contents = new ReagentAttachment();
        contents.specificAdd(ModReagents.createKey("spacelube"), 30f, jug.getCapacity());
        contents.specificAdd(ModReagents.createKey("water"), 20f, jug.getCapacity());
        contents.specificAdd(ModReagents.createKey("bicaridine"), 10f, jug.getCapacity());
        stack.set(ModDataComponents.REAGENT.get(), contents.toComponent());
        var storedBefore = stack.get(ModDataComponents.REAGENT.get());
        ItemEntity droppedJug = new ItemEntity(level, 2, 1, 2, stack);
        level.addFreshEntity(droppedJug);

        var publicContext = new ExamineContext(null, level, ExamineTarget.entity(droppedJug), droppedJug,
                null, null, null, false);
        var publicCollector = new ExamineCollector(false, droppedJug.getName());
        BuiltinExamineContributors.metadata(publicContext, publicCollector);
        BuiltinExamineContributors.jugSolution(publicContext, publicCollector);
        require("jug".equals(publicCollector.name().getString()), "jug profile supplies public name");
        require(publicCollector.description().getString().contains("Chugging is extremely ill-advised"),
                "jug profile supplies source description");
        require(publicCollector.sortedEntries().isEmpty(), "contents are detail-gated");

        var details = new ExamineCollector(true);
        BuiltinExamineContributors.jugSolution(new ExamineContext(null, level, ExamineTarget.entity(droppedJug),
                droppedJug, null, null, null, true), details);
        var rows = details.sortedEntries();
        require(rows.size() == 3, "exact volume, physical description and recognizable names only: " + rows.size());
        var volume = rows.stream().filter(e -> e.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().equals("examine.solution.volume.exact")).findFirst().orElseThrow();
        var args = ((net.minecraft.network.chat.contents.TranslatableContents) volume.text().getContents()).getArgs();
        require("60.00".equals(args[0]) && "200.00".equals(args[1]), "resolved jug exact volume/capacity used");
        var recognized = rows.stream().filter(e -> e.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().equals("examine.solution.recognized")).findFirst().orElseThrow();
        var keys = translationKeys(recognized.text());
        require(keys.contains("reagent-name-water") && keys.contains("reagent-name-space-lube")
                        && !keys.contains("reagent-name-bicaridine"),
                "only recognized reagent names are revealed: " + keys);
        require(storedBefore.equals(stack.get(ModDataComponents.REAGENT.get())), "examination leaves authoritative stack data unchanged");

        ItemStack emptyStack = new ItemStack(jug);
        ItemEntity emptyJug = new ItemEntity(level, 3, 1, 2, emptyStack);
        level.addFreshEntity(emptyJug);
        var emptyCollector = new ExamineCollector(true);
        BuiltinExamineContributors.jugSolution(new ExamineContext(null, level, ExamineTarget.entity(emptyJug),
                emptyJug, null, null, null, true), emptyCollector);
        require(emptyCollector.sortedEntries().size() == 1, "empty jug reports volume only");
        var emptyVolume = emptyCollector.sortedEntries().getFirst().text();
        require(translationKeys(emptyVolume).contains("examine.solution.volume.empty"),
                "empty exact jug uses source empty fill level rather than 0/capacity");
        helper.succeed();
    }

    private static java.util.Set<String> translationKeys(net.minecraft.network.chat.Component component) {
        var keys = new java.util.HashSet<String>();
        if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) {
            keys.add(t.getKey());
            for (Object arg : t.getArgs()) if (arg instanceof net.minecraft.network.chat.Component nested) keys.addAll(translationKeys(nested));
        }
        component.getSiblings().forEach(s -> keys.addAll(translationKeys(s)));
        return keys;
    }
    private static void require(boolean value, String message) { if (!value) throw new GameTestAssertException(message); }
}
