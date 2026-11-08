package com.juicyslew.moonstation14.ms14.combat;

import org.junit.jupiter.api.Test;
import java.util.*;
import com.juicyslew.moonstation14.ms14.hands.network.BodyTargetActionSnapshot;
import static org.junit.jupiter.api.Assertions.*;

class GunWorldUsePolicyTest {
    private static net.minecraft.resources.ResourceLocation id(String path) { return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("moonstation14", path); }
    private static GunProfile profile(int capacity) {
        return new GunProfile(new GunProfile.GunData(1, 1, false, "SEMI_AUTO", Optional.empty(), Optional.empty()),
                Optional.of(new GunProfile.RevolverAmmoProviderData(capacity, id("cartridge_magnum"), Set.of(), Set.of())));
    }

    @Test void nativeAmmoContainerRefillIsLifoConservingAndRejectsWrongOrFull() {
        var ammoId=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.BOWL);
        var roundId=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.STICK);
        var tag=id("rifle_round");
        var rounds=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        rounds.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("marked spent round"));
        rounds.set(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get(),true);
        var box=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOWL);
        box.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("named box"));
        box.set(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get(),new AmmoContainerState(List.of()));
        var sourceBefore=rounds.copy(); var boxBefore=box.copy();
        var profile=new AmmoContainerProfile(2,Set.of(tag));
        var maps=Map.of(ammoId,profile);
        var cartridges=Map.of(roundId,new CartridgeProfile(id("bullet"),false,Set.of(tag)));
        var filled=GunWorldUsePolicy.prepareInsert(null,box,rounds,cartridges,maps,Map.of());
        assertTrue(filled.accepted()); assertEquals(profile.insertSound(),filled.cue());
        assertTrue(net.minecraft.world.item.ItemStack.matches(sourceBefore,rounds));
        assertTrue(net.minecraft.world.item.ItemStack.matches(boxBefore,box));
        var state=filled.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get());
        var inserted=state.rounds().get(0);
        assertTrue(Boolean.TRUE.equals(inserted.get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get())));
        assertEquals("marked spent round",inserted.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString());
        assertEquals("named box",filled.gun().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString());
        var second=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        var partial=GunWorldUsePolicy.prepareInsert(null,filled.gun(),second,cartridges,maps,Map.of());
        assertTrue(partial.accepted());
        assertEquals(2,partial.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get()).rounds().size());
        var full=GunWorldUsePolicy.prepareInsert(null,partial.gun(),second,cartridges,maps,Map.of());
        assertFalse(full.accepted()); assertNull(full.cue()); assertTrue(net.minecraft.world.item.ItemStack.matches(second,full.sourceRemainder()));
        var wrong=GunWorldUsePolicy.prepareInsert(null,box,rounds,Map.of(roundId,new CartridgeProfile(id("bullet"),false,Set.of(id("wrong")))),maps,Map.of());
        assertFalse(wrong.accepted()); assertNull(wrong.cue()); assertTrue(net.minecraft.world.item.ItemStack.matches(sourceBefore,wrong.sourceRemainder()));
    }

    @Test void magazineInsertUsesMagazineTagsAndConservesSourceOnReject() {
        var magazineTag=id("mag_tag"); var cartridgeTag=id("cartridge_tag");
        var provider=new GunProfile.MagazineChamberProviderData("mag","chamber",Set.of(magazineTag),Set.of(cartridgeTag),Optional.empty(),Optional.empty(),true,true,false);
        var profile=new GunProfile(new GunProfile.GunData(1,1,false,"SEMI_AUTO",Optional.empty(),Optional.empty()),Optional.empty(),false,false,Optional.of(provider),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),false,1,Optional.empty(),Optional.empty());
        var gun=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        gun.set(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get(),new MagazineChamberState(net.minecraft.world.item.ItemStack.EMPTY,net.minecraft.world.item.ItemStack.EMPTY,false));
        var held=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOWL); held.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("source"));
        var original=held.copy(); var gunBefore=gun.copy();
        var ammo=Map.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.BOWL),new AmmoContainerProfile(5,Set.of(cartridgeTag),Set.of(id("wrong_mag")),null));
        var wrong=GunWorldUsePolicy.prepareInsert(profile,gun,held,Map.of(),ammo,Map.of());
        assertFalse(wrong.accepted()); assertTrue(net.minecraft.world.item.ItemStack.matches(original,wrong.sourceRemainder()));
        assertTrue(net.minecraft.world.item.ItemStack.matches(gunBefore,gun));
        ammo=Map.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.BOWL),new AmmoContainerProfile(5,Set.of(),Set.of(magazineTag),null));
        var accepted=GunWorldUsePolicy.prepareInsert(profile,gun,held,Map.of(),ammo,Map.of());
        assertTrue(accepted.accepted()); assertTrue(accepted.sourceRemainder().isEmpty());
        assertEquals(provider.magazineInsertSound().orElseThrow(), accepted.cue());
        assertTrue(net.minecraft.world.item.ItemStack.matches(original,held));
        var occupied=GunWorldUsePolicy.prepareInsert(profile,accepted.gun(),held,Map.of(),ammo,Map.of());
        assertFalse(occupied.accepted()); assertTrue(net.minecraft.world.item.ItemStack.matches(original,occupied.sourceRemainder()));
    }

    @Test void rawMagazineIsMaterializedAtInsertionAndExistingEmptyStateIsNeverRefilled() {
        var magTag=id("mk58_mag"); var roundTag=id("pistol_round");
        var provider=new GunProfile.MagazineChamberProviderData("mag","chamber",Set.of(magTag),Set.of(roundTag),Optional.empty(),Optional.empty(),true,true,false);
        var profile=new GunProfile(new GunProfile.GunData(1,1,false,"SEMI_AUTO",Optional.empty(),Optional.empty()),Optional.empty(),false,false,Optional.of(provider),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),false,1,Optional.empty(),Optional.empty());
        var gun=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        gun.set(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get(),new MagazineChamberState(net.minecraft.world.item.ItemStack.EMPTY,net.minecraft.world.item.ItemStack.EMPTY,false));
        var raw=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOWL);
        raw.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("custom raw mag"));
        var magId=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(raw.getItem());
        var ammo=Map.of(magId,new AmmoContainerProfile(6,Set.of(),Set.of(magTag),net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.STICK)));
        var cartridges=Map.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.STICK),new CartridgeProfile(id("bullet"),false,Set.of(roundTag)));
        var inserted=GunWorldUsePolicy.prepareInsert(profile,gun,raw,cartridges,ammo,Map.of());
        assertTrue(inserted.accepted()); assertTrue(inserted.sourceRemainder().isEmpty());
        var loaded=inserted.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get()).magazine();
        assertEquals(6,loaded.get(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get()).rounds().size());
        assertEquals("custom raw mag",loaded.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString());

        var depleted=raw.copy(); depleted.set(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get(),new AmmoContainerState(List.of()));
        var emptyInsert=GunWorldUsePolicy.prepareInsert(profile,gun,depleted,cartridges,ammo,Map.of());
        assertTrue(emptyInsert.accepted());
        assertTrue(emptyInsert.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get()).magazine()
                .get(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get()).rounds().isEmpty());
        assertEquals("custom raw mag",emptyInsert.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get()).magazine()
                .get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString());
    }

    @Test void directChamberInsertReturnsChamberCueAndLockedChamberRejectsSilently() {
        var tag=id("round");var cue=new PositionalSoundCue(id("custom_chamber"),0,0);
        var provider=new GunProfile.MagazineChamberProviderData("mag","chamber",Set.of(id("mag")),Set.of(tag),Optional.empty(),Optional.empty(),true,true,false,
                Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.of(cue));
        var profile=new GunProfile(new GunProfile.GunData(1,1,false,"SEMI_AUTO",Optional.empty(),Optional.empty()),Optional.empty(),false,false,Optional.of(provider),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),false,1,Optional.empty(),Optional.empty());
        var gun=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);var round=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOWL);var original=round.copy();
        var map=Map.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(round.getItem()),new CartridgeProfile(id("bullet"),false,Set.of(tag)));
        var accepted=GunWorldUsePolicy.prepareInsert(profile,gun,round,map);
        assertTrue(accepted.accepted());assertEquals(cue,accepted.cue());assertTrue(net.minecraft.world.item.ItemStack.matches(original,round));
        var lockedGun=gun.copy();lockedGun.set(com.juicyslew.moonstation14.component.ModDataComponents.MAGAZINE_CHAMBER_STATE.get(),new MagazineChamberState(net.minecraft.world.item.ItemStack.EMPTY,net.minecraft.world.item.ItemStack.EMPTY,true));
        var denied=GunWorldUsePolicy.prepareInsert(profile,lockedGun,round,map);
        assertFalse(denied.accepted());assertNull(denied.cue());assertTrue(net.minecraft.world.item.ItemStack.matches(original,round));
    }

    @Test void cellInsertionPreservesActualDepletedCellAndRejectsOccupiedOrWrongTag() {
        var tag=id("cell_tag"); var wrongTag=id("wrong_cell");
        var base=new GunProfile(new GunProfile.GunData(1,1,false,"SEMI_AUTO",Optional.empty(),Optional.empty()),Optional.empty(),false,false,Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),false,1,Optional.of(new GunProfile.CellSlotData(id("slot"),Set.of(tag),Optional.empty())),Optional.empty());
        var gun=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        gun.set(com.juicyslew.moonstation14.component.ModDataComponents.POWER_CELL_SLOT_STATE.get(),new GunPowerCellSlotState(Optional.empty()));
        var cell=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOWL); cell.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("marked")); cell.set(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get(),new BatteryState(0));
        var original=cell.copy(); var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.BOWL);
        var wrong=GunWorldUsePolicy.prepareInsert(base,gun,cell,Map.of(),Map.of(),Map.of(item,new BatteryProfile(100,100,Set.of(wrongTag))));
        assertFalse(wrong.accepted()); assertTrue(net.minecraft.world.item.ItemStack.matches(original,wrong.sourceRemainder()));
        var battery=Map.of(item,new BatteryProfile(100,100,Set.of(tag)));
        var inserted=GunWorldUsePolicy.prepareInsert(base,gun,cell,Map.of(),Map.of(),battery);
        assertTrue(inserted.accepted()); assertTrue(inserted.sourceRemainder().isEmpty());
        assertEquals(base.cellSlot().orElseThrow().insertSound().orElseThrow(), inserted.cue());
        var nested=inserted.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.POWER_CELL_SLOT_STATE.get()).cell().orElseThrow();
        assertEquals(0,nested.get(com.juicyslew.moonstation14.component.ModDataComponents.BATTERY_STATE.get()).charge());
        assertEquals(original.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME),nested.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        var occupied=GunWorldUsePolicy.prepareInsert(base,inserted.gun(),cell,Map.of(),Map.of(),battery);
        assertFalse(occupied.accepted()); assertTrue(net.minecraft.world.item.ItemStack.matches(original,occupied.sourceRemainder()));
        assertTrue(net.minecraft.world.item.ItemStack.matches(original,cell));
    }

    @Test void ballisticInsertConservesLogicalAndPhysicalSupplyAndRejectsWrongOrFull() {
        var tag=id("shotgun_round"); var bullet=id("bullet"); var cartridgeId=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.BOWL);
        var data=new GunProfile.BallisticProviderData(3,tag,id("fill"),true);
        var profile=new GunProfile(new GunProfile.GunData(1,1,false,"SEMI_AUTO",Optional.empty(),Optional.empty()),Optional.empty(),false,false,Optional.empty(),Optional.of(data),Optional.empty(),Optional.empty(),Optional.empty(),false,1,Optional.empty(),Optional.empty());
        var gun=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK); gun.set(com.juicyslew.moonstation14.component.ModDataComponents.BALLISTIC_STATE.get(),new BallisticState(List.of(),2,id("fill")));
        var round=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOWL); round.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("round"));
        var source=round.copy(); var cats=Map.of(cartridgeId,new CartridgeProfile(bullet,false,Set.of(tag)));
        var inserted=GunWorldUsePolicy.prepareInsert(profile,gun,round,cats,Map.of(),Map.of());
        assertTrue(inserted.accepted()); assertTrue(inserted.sourceRemainder().isEmpty());
        assertEquals(data.insertSound().orElseThrow(), inserted.cue());
        var state=inserted.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.BALLISTIC_STATE.get());
        assertEquals(3,state.count()); assertEquals(2,state.unspawnedCount()); assertEquals(source.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME),state.rounds().get(0).get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        assertTrue(net.minecraft.world.item.ItemStack.matches(source,round));
        var wrong=GunWorldUsePolicy.prepareInsert(profile,gun,round,Map.of(cartridgeId,new CartridgeProfile(bullet,false,Set.of(id("wrong")))),Map.of(),Map.of());
        assertFalse(wrong.accepted()); assertTrue(net.minecraft.world.item.ItemStack.matches(source,wrong.sourceRemainder()));
        var fullGun=inserted.gun(); fullGun.set(com.juicyslew.moonstation14.component.ModDataComponents.BALLISTIC_STATE.get(),new BallisticState(state.rounds(),2,id("fill")));
        var full=GunWorldUsePolicy.prepareInsert(profile,fullGun,round,cats,Map.of(),Map.of());
        assertFalse(full.accepted()); assertTrue(net.minecraft.world.item.ItemStack.matches(source,full.sourceRemainder()));
    }

    @Test void speedloaderTransfersOnlyAvailableCompatibleRoundsAndPreservesRemainder() {
        var loaderTag=id("speedloader_magnum"); var roundTag=id("magnum"); var bullet=id("bullet");
        var loaderId=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.BOWL);
        var roundId=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(net.minecraft.world.item.Items.STICK);
        var provider=new GunProfile.RevolverAmmoProviderData(3,id("fill"),Set.of(roundTag),Set.of(loaderTag));
        var profile=new GunProfile(new GunProfile.GunData(1,1,false,"SEMI_AUTO",Optional.empty(),Optional.empty()),Optional.of(provider));
        var gun=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        var occupiedFirst=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        occupiedFirst.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("occupied-first"));
        var occupiedLast=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        occupiedLast.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("occupied-last"));
        gun.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(),new RevolverState(1,List.of(new RevolverState.Chamber(false,Optional.of(occupiedFirst)),RevolverState.Chamber.empty(),new RevolverState.Chamber(false,Optional.of(occupiedLast)))));
        var rounds=List.of(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK),new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK),new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
        rounds.get(0).set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("first"));
        rounds.get(1).set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("second"));
        rounds.get(2).set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("third"));
        var loader=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOWL); loader.set(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get(),new AmmoContainerState(rounds)); loader.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("loader"));
        var original=loader.copy(); var containers=Map.of(loaderId,new AmmoContainerProfile(6,Set.of(),Set.of(loaderTag),id("fill")));
        var cartridges=Map.of(roundId,new CartridgeProfile(bullet,false,Set.of(roundTag)));
        var partial=GunWorldUsePolicy.prepareInsert(profile,gun,loader,cartridges,containers,Map.of());
        assertTrue(partial.accepted()); assertEquals(3,partial.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get()).chambers().stream().filter(c->c.physical().isPresent()).count());
        assertEquals(provider.insertSound().orElseThrow(), partial.cue());
        assertEquals(2,partial.sourceRemainder().get(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get()).rounds().size());
        var resultChambers=partial.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get()).chambers();
        assertEquals("third",resultChambers.get(1).physical().orElseThrow().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString());
        assertTrue(net.minecraft.world.item.ItemStack.matches(occupiedFirst,resultChambers.get(0).physical().orElseThrow()));
        assertTrue(net.minecraft.world.item.ItemStack.matches(occupiedLast,resultChambers.get(2).physical().orElseThrow()));
        assertEquals(5, resultChambers.stream().mapToInt(c->c.physical().map(net.minecraft.world.item.ItemStack::getCount).orElse(0)).sum()
                + partial.sourceRemainder().get(com.juicyslew.moonstation14.component.ModDataComponents.AMMO_CONTAINER_STATE.get()).rounds().stream().mapToInt(net.minecraft.world.item.ItemStack::getCount).sum());
        assertTrue(net.minecraft.world.item.ItemStack.matches(original,loader));
        var fullState=new RevolverState(0,List.of(new RevolverState.Chamber(false,Optional.of(rounds.get(0))),new RevolverState.Chamber(false,Optional.of(rounds.get(1))),new RevolverState.Chamber(false,Optional.of(rounds.get(2)))));
        var fullGun=gun.copy(); fullGun.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(),fullState);
        var full=GunWorldUsePolicy.prepareInsert(profile,fullGun,loader,cartridges,containers,Map.of()); assertFalse(full.accepted()); assertTrue(net.minecraft.world.item.ItemStack.matches(original,full.sourceRemainder()));
        var wrong=GunWorldUsePolicy.prepareInsert(profile,gun,loader,Map.of(roundId,new CartridgeProfile(bullet,false,Set.of(id("wrong")))),containers,Map.of());
        assertFalse(wrong.accepted()); assertTrue(net.minecraft.world.item.ItemStack.matches(original,wrong.sourceRemainder()));
    }

    @Test void insertStagesDetachedRoundAndPreservesIndexAndGunComponents() {
        var cartridgeId = net.minecraft.resources.ResourceLocation.withDefaultNamespace("stick");
        var tag = id("cap");
        var gunProfile = new GunProfile(new GunProfile.GunData(1, 1, false, "SEMI_AUTO", Optional.empty(), Optional.empty()),
                Optional.of(new GunProfile.RevolverAmmoProviderData(2, id("cartridge_magnum"), Set.of(tag), Set.of())));
        var gun = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        gun.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("gun"));
        gun.set(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get(), new GunRuntimeState(8, 7, 1.5f, 12));
        gun.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(), new RevolverState(1,
                List.of(RevolverState.Chamber.empty(), RevolverState.Chamber.spentDeferred(id("cartridge_magnum")))));
        var spent = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        spent.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("spent round"));
        spent.set(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get(), true);
        var originalGun = gun.copy(); var originalRound = spent.copy();
        var catalog = Map.of(cartridgeId, new CartridgeProfile(id("bullet_magnum"), false, Set.of(tag)));
        var plan = GunWorldUsePolicy.prepareInsert(gunProfile, gun, spent, catalog);
        assertTrue(plan.accepted());
        var state = plan.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get());
        assertEquals(1, state.index()); // only chamber zero was truly empty; index does not move
        var inserted = state.chambers().get(0).physical().orElseThrow();
        assertTrue(Boolean.TRUE.equals(inserted.get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get())));
        assertEquals(originalRound.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME), inserted.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        assertEquals(originalGun.get(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get()), plan.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get()));
        assertEquals(originalGun.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME), plan.gun().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        assertTrue(net.minecraft.world.item.ItemStack.matches(originalGun, gun));
        assertTrue(net.minecraft.world.item.ItemStack.matches(originalRound, spent));
        plan.gun().setCount(9);
        assertEquals(1, plan.gun().getCount());
    }

    @Test void insertRejectsWrongTagAndFullChambersWithoutMutatingGun() {
        var cartridgeId = net.minecraft.resources.ResourceLocation.withDefaultNamespace("stick");
        var profile = new GunProfile(new GunProfile.GunData(1, 1, false, "SEMI_AUTO", Optional.empty(), Optional.empty()),
                Optional.of(new GunProfile.RevolverAmmoProviderData(1, id("cartridge_magnum"), Set.of(id("cap")), Set.of())));
        var gun = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        var original = gun.copy(); var round = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        var wrong = GunWorldUsePolicy.prepareInsert(profile, gun, round,
                Map.of(cartridgeId, new CartridgeProfile(id("bullet_magnum"), false, Set.of(id("magnum")))));
        assertFalse(wrong.accepted()); assertEquals(RevolverProviderKernel.InsertFailure.WRONG_TAG, wrong.failure());
        gun.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(),
                new RevolverState(0, List.of(new RevolverState.Chamber(true, Optional.empty()))));
        original = gun.copy();
        var full = GunWorldUsePolicy.prepareInsert(profile, gun, round,
                Map.of(cartridgeId, new CartridgeProfile(id("bullet_magnum"), false, Set.of(id("cap")))));
        assertFalse(full.accepted()); assertEquals(RevolverProviderKernel.InsertFailure.FULL, full.failure());
        assertTrue(net.minecraft.world.item.ItemStack.matches(original, gun));
    }

    @Test void spinStagesIndexOnlyAndPreservesRichStackAndRounds() {
        var source = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        source.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("marked"));
        source.set(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get(), new GunRuntimeState(8, 7, 1.5f, 12));
        source.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(), RevolverProviderKernel.initialize(profile(6)));
        var old = source.copy();
        var staged = GunWorldUsePolicy.prepareSpin(profile(6), source, 4);
        assertFalse(staged.isEmpty());
        assertEquals(4, staged.get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get()).index());
        assertEquals(old.get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get()).chambers(), staged.get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get()).chambers());
        assertEquals(old.get(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get()), staged.get(com.juicyslew.moonstation14.component.ModDataComponents.GUN_RUNTIME_STATE.get()));
        assertEquals(old.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME), staged.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        assertTrue(net.minecraft.world.item.ItemStack.matches(old, source));
    }

    @Test void emptyStateDoesNotRefillButAbsentStateInitializesAndBadIndicesReject() {
        var profile = profile(6);
        var source = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        source.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(), RevolverState.empty(6));
        assertEquals(RevolverState.empty(6).chambers(), GunWorldUsePolicy.prepareSpin(profile, source, 2).get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get()).chambers());
        var absent = GunWorldUsePolicy.prepareSpin(profile, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK), 2);
        assertEquals(6, absent.get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get()).chambers().size());
        assertTrue(GunWorldUsePolicy.prepareSpin(profile, source, 6).isEmpty());
        assertTrue(GunWorldUsePolicy.prepareSpin(profile, source, -1).isEmpty());
        assertTrue(GunWorldUsePolicy.prepareSpin(profile(5), source, 1).isEmpty());
    }

    @Test void emptyPlanPreservesIndexMaterializesLiveAndSpentAndRetainsPhysicalRounds() {
        var profile = profile(4);
        var rich = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        rich.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("round"));
        var state = new RevolverState(2, List.of(new RevolverState.Chamber(true, Optional.empty()),
                RevolverState.Chamber.spentDeferred(id("cartridge_magnum")),
                new RevolverState.Chamber(false, Optional.of(rich)), RevolverState.Chamber.empty()));
        var source = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        source.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(), state);
        source.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("gun"));
        var original = source.copy();
        var plan = GunWorldUsePolicy.prepareEmpty(profile, source);
        assertNotNull(plan);
        var emptied = plan.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get());
        assertEquals(2, emptied.index());
        assertTrue(emptied.chambers().stream().allMatch(c -> !c.virtualLive() && c.physical().isEmpty() && c.spentCasing().isEmpty()));
        assertEquals(3, plan.outputs().size());
        assertFalse(Boolean.TRUE.equals(plan.outputs().get(0).get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get())));
        assertTrue(Boolean.TRUE.equals(plan.outputs().get(1).get(com.juicyslew.moonstation14.component.ModDataComponents.CARTRIDGE_SPENT.get())));
        assertEquals(rich.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME), plan.outputs().get(2).get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        assertTrue(net.minecraft.world.item.ItemStack.matches(original, source));
        assertEquals(original.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME), plan.gun().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));

        var absent = GunWorldUsePolicy.prepareEmpty(profile(6), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
        assertNotNull(absent);
        assertEquals(6, absent.outputs().size());
        var explicitEmpty = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        explicitEmpty.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(), RevolverState.empty(6));
        assertTrue(GunWorldUsePolicy.prepareEmpty(profile(6), explicitEmpty).outputs().isEmpty());
    }

    @Test void sharedWorldControlRowsHaveDetachedPlannerForEveryMutatingRowAndHideWield() {
        var profile=profile(6);
        var source=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        source.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(),RevolverState.empty(6));
        var original=source.copy();
        var held=com.juicyslew.moonstation14.ms14.combat.GunControlAvailability.rows(profile,source,GunControlAvailability.SourceKind.HELD);
        var world=com.juicyslew.moonstation14.ms14.combat.GunControlAvailability.rows(profile,source,GunControlAvailability.SourceKind.WORLD);
        assertTrue(held.stream().anyMatch(r->r.action()==com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_TOGGLE_WIELD)==profile.wieldable().isPresent());
        assertFalse(world.stream().anyMatch(r->r.action()==com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_TOGGLE_WIELD));
        for(var row:world) {
            if(!row.enabled())continue;
            assertNotNull(GunWorldUsePolicy.prepareControl(row.action(),profile,source,2),row.action().name());
        }
        var empty=GunWorldUsePolicy.prepareControl(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_EMPTY_CYLINDER,profile,source,0);
        assertNull(empty); // availability disables empty on a genuinely empty cylinder
        assertTrue(net.minecraft.world.item.ItemStack.matches(original,source));
    }
    @Test void legacyWorldRevolverActionsNormalizeIntoSharedControlPlanner(){
        var source=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        source.set(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get(),RevolverState.empty(6));
        var before=source.copy();
        var spin=GunWorldUsePolicy.prepareControl(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_SPIN,profile(6),source,3);
        assertNotNull(spin);assertEquals(3,spin.gun().get(com.juicyslew.moonstation14.component.ModDataComponents.REVOLVER_STATE.get()).index());
        var action=BodyTargetActionSnapshot.ActionId.SPIN_REVOLVER;
        assertEquals(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_SPIN,action.requestAction());
        var initialGun=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK);
        assertNotNull(GunWorldUsePolicy.prepareControl(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_EMPTY,profile(6),initialGun,0));
        assertTrue(net.minecraft.world.item.ItemStack.matches(before,source));
        assertEquals(com.juicyslew.moonstation14.ms14.hands.network.BodyHandActionRequest.Action.GUN_EMPTY,
                BodyTargetActionSnapshot.ActionId.EMPTY_REVOLVER.requestAction());
    }
}
