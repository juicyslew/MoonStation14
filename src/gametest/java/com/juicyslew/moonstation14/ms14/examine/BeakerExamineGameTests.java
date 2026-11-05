package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.item.custom.BeakerItem;
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
@GameTestTags({"beaker", "examination"})
public final class BeakerExamineGameTests {
    private BeakerExamineGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    @GameTestTags("item_examine")
    public static void registeredConsumerExaminesResolvedBeakerContents(GameTestHelper helper) {
        var level = helper.getLevel();
        BuiltinExamineContributors.register();
        verify(helper, level, ModItems.BEAKER.get(), 12.34f, 50f);
        verify(helper, level, ModItems.BEAKER_LARGE.get(), 12.34f, 100f);
        helper.succeed();
    }

    private static void verify(GameTestHelper helper, net.minecraft.server.level.ServerLevel level,
                               BeakerItem beaker, float amount, float expectedCapacity) {
        ItemStack stack = new ItemStack(beaker);
        ReagentAttachment contents = new ReagentAttachment();
        contents.specificAdd(ModReagents.createKey("water"), amount, expectedCapacity);
        contents.specificAdd(ModReagents.createKey("bicaridine"), 3f, expectedCapacity);
        stack.set(ModDataComponents.REAGENT.get(), contents.toComponent());
        var before = stack.get(ModDataComponents.REAGENT.get());
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
        require(helper.getBounds().contains(pos.getCenter()), "beaker item position remains inside fixture bounds");
        ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, stack);
        try {
            level.addFreshEntity(item);

            ExamineContext publicContext = new ExamineContext(null, level, ExamineTarget.entity(item), item,
                    null, null, null, false);
            ExamineCollector publicCollector = new ExamineCollector(false, item.getName());
            for (var contributor : ExamineContributorRegistry.contributors()) contributor.contribute(publicContext, publicCollector);
            require(publicCollector.sortedEntries().isEmpty(), "details disallowed hides beaker solution");
            require(!publicCollector.name().getString().isEmpty(), "public item name remains available");

            ExamineCollector details = new ExamineCollector(true);
            ExamineContext detailContext = new ExamineContext(null, level, ExamineTarget.entity(item), item,
                    null, null, null, true);
            for (var contributor : ExamineContributorRegistry.contributors()) contributor.contribute(detailContext, details);
            var rows = details.sortedEntries();
            var heldTarget = ExamineTarget.ownedItem(com.juicyslew.moonstation14.ms14.hands.live.LiveOwnedItemAccess.Location.HELD,
                    "left", 1, "fixture-held-token");
            var heldContext = new ExamineContext(null, level, heldTarget, null, null, null, null,
                    true, stack, true);
            var heldRows = new ExamineCollector(true);
            BuiltinExamineContributors.beakerSolution(heldContext, heldRows);
            require(heldRows.sortedEntries().stream().map(e -> e.text().getString()).toList()
                            .equals(rows.stream().map(e -> e.text().getString()).toList()),
                    "held stack and floor entity use the same detached stack-owned solution projection");
            var storedTarget = ExamineTarget.storedItem(com.juicyslew.moonstation14.ms14.hands.live.LiveOwnedItemAccess.Location.HELD,
                    "back", 2, "fixture-bag-token", "fixture-child-token");
            var storedRows = new ExamineCollector(true);
            BuiltinExamineContributors.beakerSolution(new ExamineContext(null, level, storedTarget, null,
                    null, null, null, true, stack, true), storedRows);
            require(storedRows.sortedEntries().stream().map(e -> e.text().getString()).toList()
                            .equals(rows.stream().map(e -> e.text().getString()).toList()),
                    "stored stack shares floor/current component view");
            var policy = com.juicyslew.moonstation14.ms14.chemistry.beaker.ModBeakers.resolved(level, beaker.prototypeId()).examinableSolution();
            require(!policy.heldOnly() && BuiltinExamineContributors.beakerExaminable(policy, detailContext)
                            && BuiltinExamineContributors.beakerExaminable(policy, heldContext)
                            && BuiltinExamineContributors.beakerExaminable(policy, storedRowsContext(level, storedTarget, stack)),
                    "resolved current beaker composition allows enabled disclosure in all ordinary contexts");
            var heldOnly = new com.juicyslew.moonstation14.ms14.chemistry.beaker.BeakerData.ExaminableSolution(
                    policy.enabled(), policy.solutionName(), policy.exactVolume(), true, policy.examinableWhileClosed());
            require(BuiltinExamineContributors.beakerExaminable(heldOnly, heldContext), "heldOnly policy accepts only direct held item context");
            require(!BuiltinExamineContributors.beakerExaminable(heldOnly, detailContext), "heldOnly policy rejects floor items");
            require(!BuiltinExamineContributors.beakerExaminable(heldOnly, storedRowsContext(level, storedTarget, stack)),
                    "heldOnly policy rejects stored contents despite their common item projection");
            var volume = rows.stream().filter(e -> e.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals("examine.solution.volume.exact")).findFirst()
                    .orElseThrow(() -> new GameTestAssertException("registered beaker consumer should emit exact volume"));
            var args = ((net.minecraft.network.chat.contents.TranslatableContents) volume.text().getContents()).getArgs();
            require(com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatUnits(amount + 3f).equals(args[0])
                            && com.juicyslew.moonstation14.ms14.reagent.ReagentUnits.formatUnits(expectedCapacity).equals(args[1]),
                    "resolved capacity/exact volume: " + java.util.Arrays.toString(args));
            var recognized = rows.stream().filter(e -> e.text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals("examine.solution.recognized")).findFirst()
                    .orElseThrow(() -> new GameTestAssertException("recognized reagent names should be present"));
            var keys = translationKeys(recognized.text());
            require(keys.contains("reagent-name-water") && !keys.contains("reagent-name-bicaridine"),
                    "only recognized names are disclosed: " + keys);
            require(before.equals(stack.get(ModDataComponents.REAGENT.get())), "beaker examine preserves stored component data");
            ReagentAttachment detached = MS14Provider.getDetached(beaker.toHandle(stack), MS14Bridges.REAGENT);
            require(detached != null && detached != contents, "reagent access returns a detached snapshot");
        } finally {
            item.discard();
        }
    }

    private static ExamineContext storedRowsContext(net.minecraft.server.level.ServerLevel level,
            ExamineTarget target, ItemStack stack) {
        return new ExamineContext(null, level, target, null, null, null, null, true, stack, true);
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
