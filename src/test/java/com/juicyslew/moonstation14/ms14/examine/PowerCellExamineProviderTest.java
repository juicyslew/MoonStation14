package com.juicyslew.moonstation14.ms14.examine;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PowerCellExamineProviderTest {
    @Test void registeredItemContributorUsesSharedChargeProjectionWithoutMutation() {
        var manager=com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime.serverManager();
        var saved=manager.snapshot(com.juicyslew.moonstation14.ms14.combat.ModBatteryProfiles.TYPE);
        manager.reload(com.juicyslew.moonstation14.ms14.combat.ModBatteryProfiles.TYPE,
                com.juicyslew.moonstation14.ms14.prototype.BotanyCatalogCandidateTest.mainResourcesAll("battery_profile"));
        try {
        BuiltinExamineContributors.register();
        var cells=com.juicyslew.moonstation14.ms14.combat.ModBatteryProfiles.serverCatalog().asMap();
        for(float charge:new float[]{0,180,360}) {
            var stack=new ItemStack(ModItems.POWER_CELL_SMALL.get());
            if(charge!=360) stack.set(ModDataComponents.BATTERY_STATE.get(),new com.juicyslew.moonstation14.ms14.combat.BatteryState(charge));
            var before=stack.copy().getComponents();
            var context=new ExamineContext(null,null,null,null,null,null,null,true,stack,false);
            var collector=new ExamineCollector(true, Component.empty());
            BuiltinExamineContributors.powerCellCharge(context,collector);
            var row=collector.sortedEntries().stream().filter(e->e.group().equals("power-cell")).findFirst().orElseThrow();
            var contents=(TranslatableContents)row.text().getContents();
            assertEquals("examine.moonstation14.power_cell.charge",contents.getKey());
            assertEquals(Math.round(charge*100/360),contents.getArgs()[0]);
            assertEquals(before,stack.getComponents());
        }
        var unrelated=new ExamineCollector(true,Component.empty());
        BuiltinExamineContributors.powerCellCharge(new ExamineContext(null,null,null,null,null,null,null,true,new ItemStack(Items.PAPER),false),unrelated);
        assertTrue(unrelated.sortedEntries().isEmpty());
        } finally {
            var raw=new java.util.LinkedHashMap<net.minecraft.resources.ResourceLocation,com.google.gson.JsonObject>();
            saved.asMap().forEach((key,value)->raw.put(key,com.juicyslew.moonstation14.ms14.combat.BatteryProfile.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,value).getOrThrow().getAsJsonObject()));
            manager.reload(com.juicyslew.moonstation14.ms14.combat.ModBatteryProfiles.TYPE,raw);
        }
    }
}
