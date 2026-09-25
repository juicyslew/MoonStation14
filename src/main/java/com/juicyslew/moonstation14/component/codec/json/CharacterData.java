package com.juicyslew.moonstation14.component.codec.json;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable, data-only character policy. The catalog key supplies identity. */
public record CharacterData(SlipTargetData slipData) {
    private static final Codec<CharacterData> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(SlipTargetData.CODEC.fieldOf("slip_data").forGetter(CharacterData::slipData))
                    .apply(instance, CharacterData::new));

    private static final Decoder<CharacterData> STRICT_DECODER = new Decoder<>() {
        @Override
        public <T> DataResult<Pair<CharacterData, T>> decode(DynamicOps<T> ops, T input) {
            try {
                JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                if (!json.isJsonObject()) {
                    return DataResult.error(() -> "character must be a JSON object");
                }
                CharacterSchemaAudit.audit(json.getAsJsonObject());
                return STRUCTURAL_CODEC.decode(ops, input);
            } catch (RuntimeException exception) {
                String message = exception.getMessage() == null
                        ? exception.getClass().getSimpleName() : exception.getMessage();
                return DataResult.error(() -> message);
            }
        }
    };

    public static final Codec<CharacterData> CODEC = Codec.of(STRUCTURAL_CODEC, STRICT_DECODER);

    public CharacterData {
        Objects.requireNonNull(slipData, "slipData");
    }

    /** Typed target capability bundle; separate from source-side reagent SlipData. */
    public record SlipTargetData(boolean canReceiveStun, boolean noSlip, boolean standingEligible,
                                 boolean proneEligible, List<ReactiveGroup> reactiveGroups,
                                 List<ReactiveMethod> reactiveMethods) {
        private static final Codec<SlipTargetData> STRUCTURAL_CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.BOOL.fieldOf("can_receive_stun").forGetter(SlipTargetData::canReceiveStun),
                        Codec.BOOL.fieldOf("no_slip").forGetter(SlipTargetData::noSlip),
                        Codec.BOOL.fieldOf("standing_eligible").forGetter(SlipTargetData::standingEligible),
                        Codec.BOOL.fieldOf("prone_eligible").forGetter(SlipTargetData::proneEligible),
                        Codec.list(ReactiveGroup.CODEC).fieldOf("reactive_groups").forGetter(SlipTargetData::reactiveGroups),
                        Codec.list(ReactiveMethod.CODEC).fieldOf("reactive_methods").forGetter(SlipTargetData::reactiveMethods)
                ).apply(instance, SlipTargetData::new));

        private static final Decoder<SlipTargetData> STRICT_DECODER = new Decoder<>() {
            @Override
            public <T> DataResult<Pair<SlipTargetData, T>> decode(DynamicOps<T> ops, T input) {
                try {
                    JsonElement json = ops.convertTo(JsonOps.INSTANCE, input);
                    if (!json.isJsonObject()) {
                        return DataResult.error(() -> "slip_data must be a JSON object");
                    }
                    CharacterSchemaAudit.auditSlipData(json.getAsJsonObject());
                    return STRUCTURAL_CODEC.decode(ops, input);
                } catch (RuntimeException exception) {
                    String message = exception.getMessage() == null
                            ? exception.getClass().getSimpleName() : exception.getMessage();
                    return DataResult.error(() -> message);
                }
            }
        };

        public static final Codec<SlipTargetData> CODEC = Codec.of(STRUCTURAL_CODEC, STRICT_DECODER);

        public SlipTargetData {
            Objects.requireNonNull(reactiveGroups, "reactiveGroups");
            Objects.requireNonNull(reactiveMethods, "reactiveMethods");
            reactiveGroups = distinctCopy(reactiveGroups, "reactiveGroups");
            reactiveMethods = distinctCopy(reactiveMethods, "reactiveMethods");
            if (reactiveGroups.isEmpty() != reactiveMethods.isEmpty()) {
                throw new IllegalArgumentException("reactive groups and methods must either both be empty or both be populated");
            }
        }

        private static <T> List<T> distinctCopy(List<T> values, String field) {
            List<T> copy = List.copyOf(values);
            if (new HashSet<>(copy).size() != copy.size()) {
                throw new IllegalArgumentException(field + " must not contain duplicates");
            }
            return copy;
        }
    }

    /** These are the inherited MobHuman target groups relevant to required Touch reactions. */
    public enum ReactiveGroup {
        FLAMMABLE("flammable"), EXTINGUISH("extinguish"), ACIDIC("acidic");

        public static final Codec<ReactiveGroup> CODEC = Codec.STRING.comapFlatMap(value -> {
            for (ReactiveGroup group : values()) {
                if (group.serialized.equals(value)) return DataResult.success(group);
            }
            return DataResult.error(() -> "unknown canonical reactive group '" + value + "'");
        }, ReactiveGroup::serialized);

        private final String serialized;
        ReactiveGroup(String serialized) { this.serialized = serialized; }
        public String serialized() { return serialized; }
    }

    /** Local supported reactive method; canonical spelling follows upstream `touch`. */
    public enum ReactiveMethod {
        TOUCH;

        public static final Codec<ReactiveMethod> CODEC = Codec.STRING.comapFlatMap(value ->
                "touch".equals(value) ? DataResult.success(TOUCH)
                        : DataResult.error(() -> "unknown canonical reactive method '" + value + "'"),
                ignored -> "touch");
    }
}
