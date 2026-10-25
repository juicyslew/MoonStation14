package com.juicyslew.moonstation14.ms14.actions.prototype;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;

/** Resolved action profile; parent inheritance is performed by PrototypeResolver. */
public record ActionPrototype(String displayName, ResourceLocation icon, Optional<ResourceLocation> iconOn,
        ActionHandler handler, Kind kind, boolean checkCanInteract, boolean checkConsciousness,
        int useDelayTicks, int priority) {
    public enum Kind { INSTANT, TOGGLE }

    private static final Codec<Integer> EXACT_INT = Codec.DOUBLE.comapFlatMap(value ->
            Double.isFinite(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                    && value == Math.rint(value)
                    ? com.mojang.serialization.DataResult.success(value.intValue())
                    : com.mojang.serialization.DataResult.error(() -> "expected exact integer"), Integer::doubleValue);

    public static final Codec<ActionPrototype> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("display_name").forGetter(ActionPrototype::displayName),
            ResourceLocation.CODEC.fieldOf("icon").forGetter(ActionPrototype::icon),
            ResourceLocation.CODEC.optionalFieldOf("icon_on").forGetter(ActionPrototype::iconOn),
            Codec.STRING.comapFlatMap(name -> { try { return com.mojang.serialization.DataResult.success(ActionHandler.valueOf(name)); }
                catch (IllegalArgumentException e) { return com.mojang.serialization.DataResult.error(() -> "invalid action handler: " + name); } }, Enum::name)
                    .fieldOf("handler").forGetter(ActionPrototype::handler),
            Codec.STRING.comapFlatMap(name -> { try { return com.mojang.serialization.DataResult.success(Kind.valueOf(name)); }
                catch (IllegalArgumentException e) { return com.mojang.serialization.DataResult.error(() -> "invalid action kind: " + name); } }, Enum::name)
                    .optionalFieldOf("kind", Kind.INSTANT).forGetter(ActionPrototype::kind),
            Codec.BOOL.optionalFieldOf("check_can_interact", true).forGetter(ActionPrototype::checkCanInteract),
            Codec.BOOL.optionalFieldOf("check_consciousness", true).forGetter(ActionPrototype::checkConsciousness),
            EXACT_INT.optionalFieldOf("use_delay_ticks", 0).forGetter(ActionPrototype::useDelayTicks),
            EXACT_INT.optionalFieldOf("priority", 0).forGetter(ActionPrototype::priority)
    ).apply(i, ActionPrototype::new));

    public ActionPrototype {
        if (displayName == null || displayName.isBlank() || icon == null || iconOn == null || handler == null || kind == null)
            throw new IllegalArgumentException("action fields must be present");
        if (useDelayTicks < 0) throw new IllegalArgumentException("use_delay_ticks must be nonnegative");
    }
}
