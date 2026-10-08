package com.juicyslew.moonstation14.item;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.gametest.support.GameTestTags;
import com.juicyslew.moonstation14.ms14.medical.ModMedicalItems;
import com.juicyslew.moonstation14.ms14.medical.ModInjectorModes;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestTags({"creative-menu", "prototype", "medical-treatment"})
@PrefixGameTestTemplate(false)
public final class CreativeMenuGameTests {
    private CreativeMenuGameTests() { }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void itemTabBuildHonorsCreativeOutputContract(GameTestHelper helper) {
        ModMedicalItems.bootstrapClientBundled();
        ModInjectorModes.bootstrapClientBundled();
        var tab = ModCreativeModeTabs.MOONSTATION_ITEMS_TAB.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                FeatureFlags.DEFAULT_FLAGS, true, helper.getLevel().registryAccess()));
        var entries = tab.getDisplayItems();
        require(!entries.isEmpty(), "item tab contains entries");
        for (var stack : entries) require(!stack.isEmpty() && stack.getCount() == 1,
                "creative display entries must be nonempty singleton stacks: " + stack);
        for (var item : java.util.List.of(ModItems.OINTMENT, ModItems.BRUTEPACK, ModItems.GAUZE,
                ModItems.HANDHELD_HEALTH_ANALYZER, ModItems.SYRINGE)) {
            require(entries.stream().anyMatch(stack -> stack.is(item.get()) && stack.getCount() == 1),
                    "medical entry displayed once: " + item.get());
        }
        var syringe = entries.stream().filter(stack -> stack.is(ModItems.SYRINGE.get())).findFirst().orElseThrow();
        var syringeData = syringe.get(ModDataComponents.SYRINGE.get());
        require(syringeData != null && syringeData.mode().toString().equals("moonstation14:syringe_draw_mode")
                && syringeData.transferAmountUnits() == 5f, "display syringe retains resolved mode and dose");

        var gameplay = ModMedicalItems.createStack(PrototypeRuntime.clientMedicalItems(),
                PrototypeRuntime.clientInjectorModes(), net.minecraft.resources.ResourceLocation.parse("moonstation14:ointment"));
        require(gameplay.getCount() == 10 && gameplay.getMaxStackSize() == 10, "supply gameplay stack retains resolved quantity and max 10");
        for (var id : java.util.List.of("brutepack", "gauze")) {
            require(ModMedicalItems.createStack(PrototypeRuntime.clientMedicalItems(),
                    PrototypeRuntime.clientInjectorModes(), net.minecraft.resources.ResourceLocation.parse("moonstation14:" + id)).getCount() == 10,
                    id + " gameplay factory quantity and maximum remain resolved at 10");
        }
        var analyzer = ModMedicalItems.createStack(PrototypeRuntime.clientMedicalItems(), PrototypeRuntime.clientInjectorModes(),
                net.minecraft.resources.ResourceLocation.parse("moonstation14:handheld_health_analyzer"));
        require(analyzer.getCount() == 1 && analyzer.getMaxStackSize() == 1, "analyzer remains singleton");
        for (var id : java.util.List.of("ointment1", "brutepack1", "gauze1")) {
            var stack = ModMedicalItems.createStack(PrototypeRuntime.clientMedicalItems(), PrototypeRuntime.clientInjectorModes(),
                    net.minecraft.resources.ResourceLocation.parse("moonstation14:" + id));
            require(stack.getCount() == 1 && stack.getMaxStackSize() == 10, "single supply starts at 1 with maximum 10: " + id);
        }
        for (var id : java.util.List.of("ointment1", "brutepack1", "gauze1")) {
            require(ModMedicalItems.createStack(PrototypeRuntime.clientMedicalItems(),
                    PrototypeRuntime.clientInjectorModes(), net.minecraft.resources.ResourceLocation.parse("moonstation14:" + id)).getCount() == 1,
                    id + " gameplay single-variant quantity remains 1");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "moonstation14", timeoutTicks = 20)
    public static void nativeSupplyMergingHonorsTenItemCap(GameTestHelper helper) {
        var inventory = new net.minecraft.world.SimpleContainer(1);
        var first = new net.minecraft.world.item.ItemStack(ModItems.GAUZE.get(), 9);
        var incoming = new net.minecraft.world.item.ItemStack(ModItems.GAUZE.get(), 5);
        require(inventory.addItem(first).isEmpty(), "first gauze stack inserted");
        var remainder = inventory.addItem(incoming);
        require(inventory.getItem(0).getCount() == 10, "native inventory merge stops at gauze max 10");
        require(remainder.getCount() == 4, "native inventory preserves four-item remainder");
        require(new net.minecraft.world.item.ItemStack(ModItems.HANDHELD_HEALTH_ANALYZER.get(), 1).getMaxStackSize() == 1,
                "analyzer remains nonstackable");
        helper.succeed();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }
}
