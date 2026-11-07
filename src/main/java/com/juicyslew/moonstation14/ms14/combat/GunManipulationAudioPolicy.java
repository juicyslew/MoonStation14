package com.juicyslew.moonstation14.ms14.combat;

import java.util.Optional;
import java.util.List;

/** Pure selection of profile-owned manipulation cues after an operation commits. */
public final class GunManipulationAudioPolicy {
    public enum Operation { WIELD, BOLT_TOGGLE, BALLISTIC_CYCLE, MODE, MAGAZINE_RACK, EJECT_MAGAZINE, EJECT_CELL, EMPTY_CYLINDER, SPIN_CYLINDER }
    private GunManipulationAudioPolicy() { }

    public static List<PositionalSoundCue> select(GunProfile profile, Operation operation,
            boolean beforeBoltClosed, boolean wieldSucceeded) {
        if (profile == null || operation == null) return List.of();
        return switch (operation) {
            case WIELD -> wieldSucceeded ? list(profile.wieldable().flatMap(GunProfile.WieldableData::wieldSound)) : List.of();
            case BOLT_TOGGLE -> profile.magazineChamberProvider().map(p -> list(beforeBoltClosed ? p.boltOpenSound() : p.boltCloseSound())).orElse(List.of());
            case BALLISTIC_CYCLE -> list(profile.ballisticProvider().flatMap(GunProfile.BallisticProviderData::cycleSound));
            case MODE -> list(profile.gun().modeSound());
            case MAGAZINE_RACK -> profile.magazineChamberProvider().map(p -> !beforeBoltClosed
                    ? list(p.boltCloseSound()) : list(p.rackSound())).orElse(List.of());
            case EJECT_MAGAZINE -> list(profile.magazineChamberProvider().flatMap(GunProfile.MagazineChamberProviderData::magazineEjectSound));
            case EJECT_CELL -> list(profile.cellSlot().flatMap(GunProfile.CellSlotData::ejectSound));
            case EMPTY_CYLINDER -> list(profile.revolverAmmoProvider().flatMap(GunProfile.RevolverAmmoProviderData::ejectSound));
            case SPIN_CYLINDER -> list(profile.revolverAmmoProvider().flatMap(GunProfile.RevolverAmmoProviderData::spinSound));
        };
    }
    private static List<PositionalSoundCue> list(Optional<PositionalSoundCue> cue) { return cue.map(List::of).orElse(List.of()); }
}
