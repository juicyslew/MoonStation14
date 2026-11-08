package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.ModDataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CartridgeOutputPlanTest {
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("test",path);}
    @Test void configuredSpentAndLiveRemainDistinctRealItems(){
        var presentation=new SpentCartridgePresentation(id("spent"),new PositionalSoundCue(id("fall"),-1),.2f,.03f,.7f,1.4f);
        var profile=new CartridgeProfile(id("bullet"),false,Set.of(),presentation);
        var profileId=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.STONE);
        var live=new ItemStack(Items.STONE); var spent=new ItemStack(Items.STONE);spent.set(ModDataComponents.CARTRIDGE_SPENT.get(),true);
        var other=new ItemStack(Items.DIRT);other.set(ModDataComponents.CARTRIDGE_SPENT.get(),true);
        var plan=CartridgeOutputPlan.partition(List.of(live,spent,other),Map.of(profileId,profile));
        assertEquals(2,plan.liveOutputs().size());assertEquals(1,plan.casings().size());assertTrue(plan.casings().get(0).get(ModDataComponents.CARTRIDGE_SPENT.get()));
        assertEquals(3,live.getCount()+spent.getCount()+other.getCount());
    }
    @Test void absentConfigurationSafelyPreservesSpentAsRecoverableOutput(){
        var spent=new ItemStack(Items.STONE);spent.set(ModDataComponents.CARTRIDGE_SPENT.get(),true);
        var plan=CartridgeOutputPlan.partition(List.of(spent),Map.of());
        assertEquals(1,plan.liveOutputs().size());assertTrue(plan.casings().isEmpty());
    }
}
