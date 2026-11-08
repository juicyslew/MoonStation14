package com.juicyslew.moonstation14.ms14.combat;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GunManipulationAudioPolicyTest {
    private static PositionalSoundCue cue(String name) {
        return new PositionalSoundCue(ResourceLocation.fromNamespaceAndPath("test", name), 0, 0);
    }
    private static GunProfile profile(boolean closed, PositionalSoundCue rack, PositionalSoundCue open, PositionalSoundCue close) {
        var mag = new GunProfile.MagazineChamberProviderData("mag", "chamber", Set.of(ResourceLocation.withDefaultNamespace("mag")),
                Set.of(ResourceLocation.withDefaultNamespace("round")), Optional.empty(), Optional.empty(), true, true, closed,
                Optional.of(rack), Optional.of(open), Optional.of(close), Optional.empty(), Optional.empty());
        var gun = new GunProfile.GunData(1, 1, false, "SEMI_AUTO", Optional.empty(), Optional.empty());
        return new GunProfile(gun, Optional.empty(), false, false, Optional.of(mag), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), false, 1, Optional.empty(), Optional.empty());
    }
    private static final PositionalSoundCue RACK = cue("rack"), OPEN = cue("open"), CLOSE = cue("close");

    @Test void openingBoltRacksWithCloseCue() {
        assertEquals(List.of(CLOSE), GunManipulationAudioPolicy.select(profile(false, RACK, OPEN, CLOSE),
                GunManipulationAudioPolicy.Operation.MAGAZINE_RACK, false, false));
    }
    @Test void alreadyClosedBoltUsesRackCue() {
        assertEquals(List.of(RACK), GunManipulationAudioPolicy.select(profile(true, RACK, OPEN, CLOSE),
                GunManipulationAudioPolicy.Operation.MAGAZINE_RACK, true, false));
    }
    @Test void emptyManualRackFromOpenBoltSelectsCloseOnly() {
        assertEquals(List.of(CLOSE), GunManipulationAudioPolicy.select(profile(false, RACK, OPEN, CLOSE),
                GunManipulationAudioPolicy.Operation.MAGAZINE_RACK, false, false));
    }
    @Test void closedEmptyRackSelectsRackCueOnly() {
        assertEquals(List.of(RACK), GunManipulationAudioPolicy.select(profile(true, RACK, OPEN, CLOSE),
                GunManipulationAudioPolicy.Operation.MAGAZINE_RACK, true, false));
    }
    @Test void boltToggleSelectsDirectionalBoltCueRatherThanRackCue() {
        var profile = profile(true, RACK, OPEN, CLOSE);
        assertEquals(List.of(OPEN), GunManipulationAudioPolicy.select(profile, GunManipulationAudioPolicy.Operation.BOLT_TOGGLE, true, false));
        assertEquals(List.of(CLOSE), GunManipulationAudioPolicy.select(profile, GunManipulationAudioPolicy.Operation.BOLT_TOGGLE, false, false));
        assertEquals(List.of(CLOSE), GunManipulationAudioPolicy.select(profile, GunManipulationAudioPolicy.Operation.MAGAZINE_RACK, false, false));
        assertEquals(List.of(RACK), GunManipulationAudioPolicy.select(profile, GunManipulationAudioPolicy.Operation.MAGAZINE_RACK, true, false));
    }
    @Test void modeWieldAndEjectionSelectProfileOwnedCues() {
        var mode = cue("mode");
        var wield = cue("wield");
        var magEject = cue("mag-eject");
        var cellEject = cue("cell-eject");
        var base = profile(false, RACK, OPEN, CLOSE);
        var wieldable = new GunProfile.WieldableData(true, Optional.of(wield));
        var cell = new GunProfile.CellSlotData(ResourceLocation.withDefaultNamespace("cell"), Set.of(), Optional.empty(), Optional.empty(), Optional.of(cellEject));
        var configured = new GunProfile(new GunProfile.GunData(1, 1, false, "SEMI_AUTO", Optional.empty(), Optional.empty(),
                GunProfile.SpreadData.SOURCE_DEFAULTS, .25f, Optional.empty(), 1, Set.of("SEMI_AUTO"), Optional.of(mode)),
                Optional.empty(), false, false, base.magazineChamberProvider().map(p -> new GunProfile.MagazineChamberProviderData(
                p.magazineSlot(), p.chamberSlot(), p.magazineTags(), p.cartridgeTags(), p.startingMagazine(), p.startingChamber(), p.autoCycle(), p.canRack(), p.boltClosed(),
                p.rackSound(), p.boltOpenSound(), p.boltCloseSound(), p.magazineInsertSound(), Optional.of(magEject))), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of(wieldable), false, 1, Optional.of(cell), Optional.empty());
        assertTrue(GunManipulationAudioPolicy.select(configured, GunManipulationAudioPolicy.Operation.WIELD, false, false).isEmpty());
        assertEquals(List.of(wield), GunManipulationAudioPolicy.select(configured, GunManipulationAudioPolicy.Operation.WIELD, false, true));
        assertEquals(List.of(mode), GunManipulationAudioPolicy.select(configured, GunManipulationAudioPolicy.Operation.MODE, false, false));
        assertEquals(List.of(magEject), GunManipulationAudioPolicy.select(configured, GunManipulationAudioPolicy.Operation.EJECT_MAGAZINE, false, false));
        assertEquals(List.of(cellEject), GunManipulationAudioPolicy.select(configured, GunManipulationAudioPolicy.Operation.EJECT_CELL, false, false));
    }
}

