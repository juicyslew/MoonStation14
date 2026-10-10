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
