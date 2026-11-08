package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.item.custom.SyringeItem;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
@GameTestTags({"syringe", "examination"})
public final class SyringeExamineGameTests {
    private SyringeExamineGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void registeredConsumerExaminesOnlyWorldSyringeContents(GameTestHelper helper) {
        var level = helper.getLevel();
        BuiltinExamineContributors.register();
        SyringeItem syringe = (SyringeItem) ModItems.SYRINGE.get();
        ItemStack stack = new ItemStack(syringe);
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2));
        require(helper.getBounds().contains(pos.getCenter()), "syringe remains in fixture bounds");
        ItemEntity item = new ItemEntity(level, pos.getX() + .5, pos.getY(), pos.getZ() + .5, stack);
        try {
            level.addFreshEntity(item);
            ExamineCollector empty = examine(item, level, true);
            require(empty.sortedEntries().size() == 1, "empty syringe emits volume only");

            ReagentAttachment contents = new ReagentAttachment();
            contents.specificAdd(ModReagents.createKey("water"), 5f, syringe.getCapacity(stack, level));
            contents.specificAdd(ModReagents.createKey("bicaridine"), 2f, syringe.getCapacity(stack, level));
            stack.set(ModDataComponents.REAGENT.get(), contents.toComponent());
            var before = stack.get(ModDataComponents.REAGENT.get());
            ExamineCollector populated = examine(item, level, true);
            var rows = populated.sortedEntries();
            var volume = rows.stream().filter(e -> key(e.text(), "examine.solution.volume.exact")).findFirst().orElseThrow();
            Object[] args = ((net.minecraft.network.chat.contents.TranslatableContents) volume.text().getContents()).getArgs();
            require("7".equals(args[0]) && "15".equals(args[1]), "resolved syringe capacity and populated volume");
            var recognized = rows.stream().filter(e -> key(e.text(), "examine.solution.recognized")).findFirst().orElseThrow();
            var names = translationKeys(recognized.text());
            require(names.contains("reagent-name-water") && !names.contains("reagent-name-bicaridine"),
                    "only recognized reagent details are disclosed: " + names);
            require(before.equals(stack.get(ModDataComponents.REAGENT.get())), "examine does not mutate stored solution");

            ExamineCollector denied = examine(item, level, false);
            require(denied.sortedEntries().isEmpty(), "detail denial hides syringe volume and contents");

            ReagentAttachment replacement = new ReagentAttachment();
            replacement.specificAdd(ModReagents.createKey("water"), 3f, syringe.getCapacity(stack, level));
            ItemStack changed = stack.copy();
            changed.set(ModDataComponents.REAGENT.get(), replacement.toComponent());
            item.setItem(changed);
            var changedRows = examine(item, level, true).sortedEntries();
            var changedVolume = changedRows.stream().filter(e -> key(e.text(), "examine.solution.volume.exact")).findFirst().orElseThrow();
            Object[] changedArgs = ((net.minecraft.network.chat.contents.TranslatableContents) changedVolume.text().getContents()).getArgs();
            require("3".equals(changedArgs[0]), "consumer reads current canonical stack after replacement");

            // The ordinary empty-syringe case retains the default empty component; test true absence separately.
            ItemStack absent = new ItemStack(syringe);
            absent.remove(ModDataComponents.REAGENT.get());
            ItemEntity absentItem = new ItemEntity(level, pos.getX() + .5, pos.getY(), pos.getZ() + .5, absent);
            level.addFreshEntity(absentItem);
            try {
                ItemStack actualAbsent = absentItem.getItem();
                require(!actualAbsent.has(ModDataComponents.REAGENT.get()), "absence fixture starts without reagent component");
                ItemStack beforeAbsentExamine = actualAbsent.copy();
                var beforeAbsentComponents = actualAbsent.getComponents();
                int beforeAbsentCount = actualAbsent.getCount();
                examine(absentItem, level, true);
                ItemStack afterAbsent = absentItem.getItem();
                require(!afterAbsent.has(ModDataComponents.REAGENT.get()), "read does not initialize absent reagent trait");
                require(beforeAbsentComponents.equals(afterAbsent.getComponents())
                                && beforeAbsentCount == afterAbsent.getCount()
                                && ItemStack.matches(beforeAbsentExamine, afterAbsent),
                        "read preserves absent world stack components and count");
            } finally { absentItem.discard(); }
        } finally { item.discard(); }
        helper.succeed();
    }

    private static ExamineCollector examine(ItemEntity item, net.minecraft.server.level.ServerLevel level, boolean details) {
        var context = new ExamineContext(null, level, ExamineTarget.entity(item), item, null, null, null, details);
        var collector = new ExamineCollector(details, item.getName());
        for (var contributor : ExamineContributorRegistry.contributors()) contributor.contribute(context, collector);
        return collector;
    }
    private static boolean key(Component component, String key) {
        return component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t && t.getKey().equals(key);
    }
    private static java.util.Set<String> translationKeys(Component component) {
        var keys = new java.util.HashSet<String>();
        if (component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) {
            keys.add(t.getKey());
            for (Object arg : t.getArgs()) if (arg instanceof Component nested) keys.addAll(translationKeys(nested));
        }
        component.getSiblings().forEach(s -> keys.addAll(translationKeys(s)));
        return keys;
    }
    private static void require(boolean value, String message) { if (!value) throw new GameTestAssertException(message); }
}
