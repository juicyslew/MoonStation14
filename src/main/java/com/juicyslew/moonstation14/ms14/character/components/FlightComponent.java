package com.juicyslew.moonstation14.ms14.character.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Natural landable flight capability; NPC cruise and autonomous phase policy are Minecraft adaptations. */
public record FlightComponent(double npcCruiseHeight, double groundedSpeedMultiplier,
                              int npcAirborneTicks, int npcGroundedTicks,
                              double npcVerticalSpeedMultiplier, double airborneSpeedMultiplier,
                              int throwRecoveryTicks) implements CharacterComponent {
    public static final String TYPE = "Flight";
    public static final double DEFAULT_NPC_CRUISE_HEIGHT = 4.0;
    public static final double MAX_NPC_CRUISE_HEIGHT = 6.0;
    public static final double DEFAULT_GROUNDED_SPEED_MULTIPLIER = 0.25;
    public static final int DEFAULT_NPC_AIRBORNE_TICKS = 400;
    public static final int DEFAULT_NPC_GROUNDED_TICKS = 160;
    public static final int MIN_NPC_PHASE_TICKS = 20;
    public static final int MAX_NPC_PHASE_TICKS = 2400;
    public static final double DEFAULT_NPC_VERTICAL_SPEED_MULTIPLIER = 0.4;
    public static final double DEFAULT_AIRBORNE_SPEED_MULTIPLIER = 0.80;
    public static final int DEFAULT_THROW_RECOVERY_TICKS = 4;
    private static final Codec<Integer> EXACT_INT = Codec.DOUBLE.validate(value ->
            Double.isFinite(value) && value == Math.rint(value)
                    && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE
                    ? DataResult.success(value)
                    : DataResult.error(() -> "expected an exact integer"))
            .xmap(Double::intValue, Integer::doubleValue);
    public FlightComponent() { this(DEFAULT_NPC_CRUISE_HEIGHT, DEFAULT_GROUNDED_SPEED_MULTIPLIER); }
    public FlightComponent(double npcCruiseHeight) { this(npcCruiseHeight, DEFAULT_GROUNDED_SPEED_MULTIPLIER); }
    public FlightComponent(double npcCruiseHeight, double groundedSpeedMultiplier) {
        this(npcCruiseHeight, groundedSpeedMultiplier, DEFAULT_NPC_AIRBORNE_TICKS,
                DEFAULT_NPC_GROUNDED_TICKS, DEFAULT_NPC_VERTICAL_SPEED_MULTIPLIER,
                DEFAULT_AIRBORNE_SPEED_MULTIPLIER, DEFAULT_THROW_RECOVERY_TICKS);
    }
    public FlightComponent(double npcCruiseHeight, double groundedSpeedMultiplier, int airborneTicks,
                           int groundedTicks, double verticalSpeedMultiplier) {
        this(npcCruiseHeight, groundedSpeedMultiplier, airborneTicks, groundedTicks, verticalSpeedMultiplier,
                DEFAULT_AIRBORNE_SPEED_MULTIPLIER, DEFAULT_THROW_RECOVERY_TICKS);
    }
    public FlightComponent {
        if (!Double.isFinite(npcCruiseHeight) || npcCruiseHeight < .75 || npcCruiseHeight > MAX_NPC_CRUISE_HEIGHT)
            throw new IllegalArgumentException("npcCruiseHeight must be between 0.75 and " + MAX_NPC_CRUISE_HEIGHT);
        if (!Double.isFinite(groundedSpeedMultiplier) || groundedSpeedMultiplier <= 0 || groundedSpeedMultiplier > 1)
            throw new IllegalArgumentException("groundedSpeedMultiplier must be greater than 0 and at most 1");
        if (npcAirborneTicks < MIN_NPC_PHASE_TICKS || npcAirborneTicks > MAX_NPC_PHASE_TICKS)
            throw new IllegalArgumentException("npcAirborneTicks must be in [" + MIN_NPC_PHASE_TICKS + ", " + MAX_NPC_PHASE_TICKS + "]");
        if (npcGroundedTicks < MIN_NPC_PHASE_TICKS || npcGroundedTicks > MAX_NPC_PHASE_TICKS)
            throw new IllegalArgumentException("npcGroundedTicks must be in [" + MIN_NPC_PHASE_TICKS + ", " + MAX_NPC_PHASE_TICKS + "]");
        if (!Double.isFinite(npcVerticalSpeedMultiplier) || npcVerticalSpeedMultiplier <= 0 || npcVerticalSpeedMultiplier > 1)
            throw new IllegalArgumentException("npcVerticalSpeedMultiplier must be greater than 0 and at most 1");
        if (!Double.isFinite(airborneSpeedMultiplier) || airborneSpeedMultiplier <= 0 || airborneSpeedMultiplier > 4)
            throw new IllegalArgumentException("airborneSpeedMultiplier must be greater than 0 and at most 4");
        if (throwRecoveryTicks < 1 || throwRecoveryTicks > 40)
            throw new IllegalArgumentException("throwRecoveryTicks must be in [1, 40]");
    }
    public static final Codec<FlightComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(FlightComponent::type),
            Codec.DOUBLE.optionalFieldOf("npc_cruise_height", DEFAULT_NPC_CRUISE_HEIGHT)
                    .forGetter(FlightComponent::npcCruiseHeight),
             Codec.DOUBLE.optionalFieldOf("grounded_speed_multiplier", DEFAULT_GROUNDED_SPEED_MULTIPLIER).forGetter(FlightComponent::groundedSpeedMultiplier),
             Codec.INT.optionalFieldOf("npc_airborne_ticks", DEFAULT_NPC_AIRBORNE_TICKS).forGetter(FlightComponent::npcAirborneTicks),
             Codec.INT.optionalFieldOf("npc_grounded_ticks", DEFAULT_NPC_GROUNDED_TICKS).forGetter(FlightComponent::npcGroundedTicks),
             Codec.DOUBLE.optionalFieldOf("npc_vertical_speed_multiplier", DEFAULT_NPC_VERTICAL_SPEED_MULTIPLIER).forGetter(FlightComponent::npcVerticalSpeedMultiplier),
             Codec.DOUBLE.optionalFieldOf("airborne_speed_multiplier", DEFAULT_AIRBORNE_SPEED_MULTIPLIER).forGetter(FlightComponent::airborneSpeedMultiplier),
              EXACT_INT.optionalFieldOf("throw_recovery_ticks", DEFAULT_THROW_RECOVERY_TICKS).forGetter(FlightComponent::throwRecoveryTicks)
     ).apply(instance, (type, height, multiplier, airborne, grounded, vertical, airSpeed, recovery) -> {
        if (!TYPE.equals(type)) throw new IllegalArgumentException("unknown component type: " + type);
         return new FlightComponent(height, multiplier, airborne, grounded, vertical, airSpeed, recovery);
    }));
    @Override public String type() { return TYPE; }
}
