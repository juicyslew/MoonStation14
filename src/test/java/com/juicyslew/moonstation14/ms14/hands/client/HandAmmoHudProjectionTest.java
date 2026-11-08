package com.juicyslew.moonstation14.ms14.hands.client;

import com.juicyslew.moonstation14.ms14.combat.*;
import com.juicyslew.moonstation14.ms14.hands.network.BodyHandStateSnapshot;
import com.juicyslew.moonstation14.ms14.hands.network.ItemPresentation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HandAmmoHudProjectionTest {
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final UUID BODY = UUID.randomUUID();

    private static BodyHandStateSnapshot snapshot(String active, String... ids) {
        var hands = java.util.Arrays.stream(ids).map(id -> {
            ItemStack stack = new ItemStack(Items.PAPER);
            return new BodyHandStateSnapshot.Hand(id, "token-" + id, "minecraft:paper", 1,
                    false, null, null, 0, null, ItemPresentation.fromStack(stack), null);
        }).toList();
        return new BodyHandStateSnapshot(4, 1, BodyHandStateSnapshot.Reason.OK, ACCOUNT, BODY,
                7, 2, active == null ? ids[0] : active, hands, List.of(), List.of(), false, null, 10, null);
    }

    private static AmmoContainerProfile container() {
        return new AmmoContainerProfile(10, java.util.Set.of(), java.util.Set.of(),
                ResourceLocation.withDefaultNamespace("arrow"), null);
    }

    private static GunProfile revolver() {
        return GunProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("""
                        {"gun":{"fire_rate":1,"projectile_speed":1,"use_key":false,"firing_mode":"SEMI_AUTO"},
                         "revolver_ammo_provider":{"capacity":6,"fill_cartridge_prototype":"moonstation14:cartridge_magnum",
                         "accepted_cartridge_tags":[],"accepted_speedloader_tags":[]},"ammo_counter":true}
                        """)).getOrThrow();
    }

    private static Map<ResourceLocation, AmmoContainerProfile> catalog() {
        return Map.of(ResourceLocation.withDefaultNamespace("paper"), container());
    }

    @Test void cellChargeIsReadOnlyAndBoundToEachPresentedHand() {
        var batteryId=ResourceLocation.withDefaultNamespace("paper");
        var batteries=Map.of(batteryId,new BatteryProfile(360,360,java.util.Set.of(ModBatteryProfiles.POWER_CELL_TAG)));
        for(float amount:new float[]{0,180,360}) {
            var cell=new ItemStack(Items.PAPER);
            if(amount!=360) cell.set(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get(),new BatteryState(amount));
            var pct=BatteryChargeProjection.project(cell,batteries);
            assertEquals(Math.round(amount*100/360),pct.percent());
            var s=new BodyHandStateSnapshot(4,1,BodyHandStateSnapshot.Reason.OK,ACCOUNT,BODY,7,2,"left",List.of(
                    new BodyHandStateSnapshot.Hand("left","l","minecraft:paper",1,false,null,null,0,null,ItemPresentation.fromStack(cell),null),
                    new BodyHandStateSnapshot.Hand("right","r","minecraft:paper",1,false,null,null,0,null,ItemPresentation.fromStack(cell),null)),List.of(),List.of(),false,null,10,null);
            var entries=HandAmmoHudProjection.project(s,null,ItemStack.EMPTY,Map.of(),batteries);
            assertEquals(List.of(0,1),entries.stream().map(HandAmmoHudProjection.Entry::handIndex).toList());
            assertTrue(entries.stream().allMatch(e->e.counter().kind()==GunAmmoCounterProjection.Kind.CELL_CHARGE));
            assertEquals(amount==0?"0/360 J  0%":amount==180?"180/360 J  50%":"360/360 J  100%",pct.text());
            assertEquals(amount==360?null:amount,cell.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get())==null?null:cell.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get()).charge());
        }
    }

    @Test void selectedGunAndStandaloneCellBothProjectFromTheirOwnHandInEitherOrder() {
        var cellId=ResourceLocation.withDefaultNamespace("stick");
        var batteries=Map.of(cellId,new BatteryProfile(360,360,java.util.Set.of(ModBatteryProfiles.POWER_CELL_TAG)));
        for(var ids:List.of(List.of("left","right"),List.of("right","left"))) {
            var gun=new ItemStack(Items.PAPER);var cell=new ItemStack(Items.STICK);
            cell.set(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get(),new BatteryState(180));
            var hands=ids.stream().map(id->{var stack=id.equals("left")?gun:cell;return new BodyHandStateSnapshot.Hand(id,"token-"+id,"minecraft:"+(id.equals("left")?"paper":"stick"),1,false,null,null,0,null,ItemPresentation.fromStack(stack),null);}).toList();
            var s=new BodyHandStateSnapshot(4,1,BodyHandStateSnapshot.Reason.OK,ACCOUNT,BODY,7,2,"left",hands,List.of(),List.of(),false,null,10,null);
            var selection=new GunWeaponResolver.Selection(BODY,2,"left","token-left",revolver());
            var result=HandAmmoHudProjection.project(s,selection,gun,catalog(),batteries);
            assertEquals(2,result.size());assertEquals("left",result.stream().filter(e->e.profile()!=null).findFirst().orElseThrow().handId());
            var cellEntry=result.stream().filter(e->e.counter().kind()==GunAmmoCounterProjection.Kind.CELL_CHARGE).findFirst().orElseThrow();
            assertEquals("right",cellEntry.handId());assertTrue(cellEntry.counter().text().contains("180/360 J"));assertEquals(180,cell.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get()).charge());
        }
        var integralId=ResourceLocation.withDefaultNamespace("iron_hoe");
        var integral= new ItemStack(Items.IRON_HOE);
        integral.set(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get(),new BatteryState(180));
        assertNull(BatteryChargeProjection.project(integral,Map.of(integralId,new BatteryProfile(360,360,java.util.Set.of()))),
                "integral battery profiles without the resolved power_cell capability are not standalone cells");
    }

    @Test void standaloneContainerAppearsForEitherHandOrderWithoutRefilling() {
        for (var hands : List.of(new String[]{"left", "right"}, new String[]{"right", "left"})) {
            for (int rounds : new int[]{-1, 3, 0}) {
                var stack = new ItemStack(Items.PAPER);
                if (rounds >= 0) stack.set(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get(),
                        new AmmoContainerState(rounds == 0 ? List.of() : List.of(new ItemStack(Items.ARROW, rounds))));
                var s = new BodyHandStateSnapshot(4, 1, BodyHandStateSnapshot.Reason.OK, ACCOUNT, BODY, 7, 2,
                        "hand", List.of(new BodyHandStateSnapshot.Hand("hand", "token", "minecraft:paper", 1, false,
                                null, null, 0, null, ItemPresentation.fromStack(stack), null)), List.of(), List.of(), false, null, 10, null);
                var entry = HandAmmoHudProjection.project(s, null, ItemStack.EMPTY, catalog(), Map.of()).getFirst();
                assertEquals(rounds < 0 ? 10 : rounds, entry.counter().count());
                assertEquals(10, entry.counter().capacity());
                var sourceState = stack.get(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get());
                if (rounds < 0) assertNull(sourceState);
                else if (rounds == 0) assertTrue(sourceState.rounds().isEmpty());
                else assertEquals(3, sourceState.rounds().getFirst().getCount());
            }
            var result = HandAmmoHudProjection.project(snapshot(null, hands), null, ItemStack.EMPTY, catalog(), Map.of());
            assertEquals(List.of(0, 1), result.stream().map(HandAmmoHudProjection.Entry::handIndex).toList());
        }
    }

    @Test void revolverCounterHasChambersOnlyOnSelectedBoundHand() {
        var s = snapshot("left", "left", "right");
        var selection = new GunWeaponResolver.Selection(BODY, 2, "left", "token-left", revolver());
        var selectedGun = new ItemStack(Items.PAPER);
        var entries = HandAmmoHudProjection.project(s, selection, selectedGun, catalog(), Map.of());
        assertEquals(2, entries.size());
        assertSame(selectedGun, entries.get(0).stack());
        assertNotNull(entries.get(0).revolver());
        assertNull(entries.get(1).revolver());
        assertNull(entries.get(1).profile());
    }

    @Test void staleSelectionDoesNotReplacePresentedStackAndMissingPresentationIsSkipped() {
        var original = snapshot("left", "left");
        var mismatch = new GunWeaponResolver.Selection(UUID.randomUUID(), 2, "left", "token-left", revolver());
        var fallback = HandAmmoHudProjection.project(original, mismatch, new ItemStack(Items.STICK), catalog(), Map.of());
        assertEquals(1, fallback.size());
        var presented = original.hands().getFirst().presentation().stack();
        var projected = fallback.getFirst().stack();
        assertNotSame(presented, projected);
        assertEquals(presented.getItem(), projected.getItem());
        assertEquals(presented.getCount(), projected.getCount());
        var h = new BodyHandStateSnapshot.Hand("left", "token-left", "minecraft:paper", 1);
        var noPresentation = new BodyHandStateSnapshot(4, 1, BodyHandStateSnapshot.Reason.OK, ACCOUNT, BODY,
                7, 2, "left", List.of(h), List.of(), List.of(), false, null, 10, null);
        assertTrue(HandAmmoHudProjection.project(noPresentation, null, ItemStack.EMPTY, catalog(), Map.of()).isEmpty());
    }

    @Test void preferredStackMustMatchHandItemIdentityButContainerStillUsesItsOwnPresentation() {
        var source = snapshot("left", "left");
        var selection = new GunWeaponResolver.Selection(BODY, 2, "left", "token-left", revolver());
        var wrong = HandAmmoHudProjection.project(source, selection, new ItemStack(Items.STICK), catalog(), Map.of());
        assertEquals(1, wrong.size());
        assertNull(wrong.getFirst().profile());
        assertEquals(10, wrong.getFirst().counter().count());
        assertEquals(Items.PAPER, wrong.getFirst().stack().getItem());

        var mismatchPresentation = new BodyHandStateSnapshot.Hand("left", "token-left", "minecraft:stick", 1,
                false, null, null, 0, null, ItemPresentation.fromStack(new ItemStack(Items.PAPER)), null);
        var mismatched = new BodyHandStateSnapshot(4, 1, BodyHandStateSnapshot.Reason.OK, ACCOUNT, BODY,
                7, 2, "left", List.of(mismatchPresentation), List.of(), List.of(), false, null, 10, null);
        assertTrue(HandAmmoHudProjection.project(mismatched, selection, new ItemStack(Items.STICK), catalog(), Map.of()).isEmpty());
    }
}
