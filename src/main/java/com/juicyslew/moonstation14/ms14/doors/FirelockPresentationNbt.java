package com.juicyslew.moonstation14.ms14.doors;

import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/** Shared immutable firelock presentation encoding; deliberately does not load controller authority. */
public final class FirelockPresentationNbt {
    private FirelockPresentationNbt() { }
    public static void write(CompoundTag tag,String prefix,FirelockController.Presentation view) {
        if(view==null){tag.putBoolean(prefix+"Present",false);return;}
        FirelockState state=view.state();tag.putBoolean(prefix+"Present",true);
        tag.putString(prefix+"Phase",state.phase().name());tag.putBoolean(prefix+"Powered",state.powered());tag.putBoolean(prefix+"Locked",state.locked());
        tag.putBoolean(prefix+"Pressure",state.pressureWarning());tag.putBoolean(prefix+"Temperature",state.temperatureWarning());
        tag.putLong(prefix+"Transition",view.transitionAt());tag.putLong(prefix+"Started",view.motionStartedAt());
        tag.putLong(prefix+"PressureStarted",view.pressureWarningStartedAt());tag.putLong(prefix+"TemperatureStarted",view.temperatureWarningStartedAt());
        tag.putLong(prefix+"PryCooldown",state.pryCooldownUntil());tag.putLong(prefix+"Revision",state.revision());
        tag.putBoolean(prefix+"Latch",view.authoritativeLatch());tag.putString(prefix+"Facing",view.facing().getName());
    }
    public static Optional<FirelockController.Presentation> read(CompoundTag tag,String prefix) {
        if(!tag.getBoolean(prefix+"Present"))return Optional.empty();
        try {
            for(String suffix:new String[]{"Phase","Powered","Locked","Pressure","Temperature","Transition","Started","PressureStarted","TemperatureStarted","PryCooldown","Revision","Latch","Facing"})if(!tag.contains(prefix+suffix))return Optional.empty();
            var state=new FirelockState(AirlockState.DoorPhase.valueOf(tag.getString(prefix+"Phase")),tag.getBoolean(prefix+"Powered"),tag.getBoolean(prefix+"Locked"),tag.getBoolean(prefix+"Pressure"),tag.getBoolean(prefix+"Temperature"),tag.getLong(prefix+"Transition"),tag.getLong(prefix+"PryCooldown"),tag.getLong(prefix+"Revision"));
            Direction facing=Direction.byName(tag.getString(prefix+"Facing"));if(facing==null)return Optional.empty();
            return Optional.of(new FirelockController.Presentation(state,tag.getBoolean(prefix+"Latch"),tag.getLong(prefix+"Started"),tag.getLong(prefix+"Transition"),tag.getLong(prefix+"PressureStarted"),tag.getLong(prefix+"TemperatureStarted"),facing));
        } catch(RuntimeException malformed){return Optional.empty();}
    }
}
