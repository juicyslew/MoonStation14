package com.juicyslew.moonstation14.ms14.loose_objects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import com.juicyslew.moonstation14.component.ModDataComponents;

/** Physical/presentation tuning for a loose object. Values are Minecraft blocks and ticks. */
public record LooseObjectProfile(float mass, float friction, float restitution, float throwSpeed,
                                 float throwSpin, String geometry, String renderKind,
                                 boolean breakOnImpact, float breakThreshold,
                                  float throwRange, float minimumThrowRange, int throwCooldown,
                                     float physicalWidth, float physicalHeight, VisualTuning visual) {
    public record VisualTuning(float spriteScaleX, float spriteScaleY, float spriteOffsetX,
                               float spriteOffsetY, float modelScale, float modelFloorOffset) {
        public static final Codec<VisualTuning> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("sprite_scale_x").forGetter(VisualTuning::spriteScaleX),
                Codec.FLOAT.fieldOf("sprite_scale_y").forGetter(VisualTuning::spriteScaleY),
                Codec.FLOAT.fieldOf("sprite_offset_x").forGetter(VisualTuning::spriteOffsetX),
                Codec.FLOAT.fieldOf("sprite_offset_y").forGetter(VisualTuning::spriteOffsetY),
                Codec.FLOAT.fieldOf("model_scale").forGetter(VisualTuning::modelScale),
                Codec.FLOAT.fieldOf("model_floor_offset").forGetter(VisualTuning::modelFloorOffset)
        ).apply(i, VisualTuning::new));
        public VisualTuning {
            if (!bounded(spriteScaleX, 0, 4) || !bounded(spriteScaleY, 0, 4)
                    || !bounded(spriteOffsetX, -2, 2) || !bounded(spriteOffsetY, -2, 2)
                    || !bounded(modelScale, 0, 4) || !Float.isFinite(modelFloorOffset)
                    || modelFloorOffset < 0 || modelFloorOffset > 2)
                throw new IllegalArgumentException("invalid visual tuning");
        }
    }
    private static boolean bounded(float value, float min, float max) {
        return Float.isFinite(value) && value > min && value <= max;
    }
    public float visualWidth() { return visual.spriteScaleX(); }
    public float visualHeight() { return visual.spriteScaleY(); }
    public float modelScale() { return visual.modelScale(); }
    public float modelFloorOffset() { return visual.modelFloorOffset(); }
    // Keep these independent of ModLooseObjectProfiles: its prototype type is
    // constructed with CODEC, so referring back during profile initialization
    // would expose CODEC before it has been assigned.
    public static final ResourceLocation GENERIC_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "generic");
    public static final ResourceLocation JUG_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "jug");
    public static final ResourceLocation GRENADE_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "grenade");
    public static final ResourceLocation BLOCK_MODEL_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "block_model");
    public static final ResourceLocation PORTABLE_BODY_KEY_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "portable_body_key");
    public static final ResourceLocation MOUSE_TINY_ID = ResourceLocation.fromNamespaceAndPath("moonstation14", "mouse_tiny");
    public static final Codec<LooseObjectProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("mass").forGetter(LooseObjectProfile::mass),
            Codec.FLOAT.fieldOf("friction").forGetter(LooseObjectProfile::friction),
            Codec.FLOAT.fieldOf("restitution").forGetter(LooseObjectProfile::restitution),
            Codec.FLOAT.fieldOf("throw_speed").forGetter(LooseObjectProfile::throwSpeed),
            Codec.FLOAT.fieldOf("throw_spin").forGetter(LooseObjectProfile::throwSpin),
            Codec.STRING.fieldOf("geometry").forGetter(LooseObjectProfile::geometry),
            Codec.STRING.fieldOf("render_kind").forGetter(LooseObjectProfile::renderKind),
            Codec.BOOL.fieldOf("break_on_impact").forGetter(LooseObjectProfile::breakOnImpact),
            Codec.FLOAT.fieldOf("break_threshold").forGetter(LooseObjectProfile::breakThreshold),
            Codec.FLOAT.fieldOf("throw_range").forGetter(LooseObjectProfile::throwRange),
            Codec.FLOAT.fieldOf("minimum_throw_range").forGetter(LooseObjectProfile::minimumThrowRange),
            Codec.INT.fieldOf("throw_cooldown").forGetter(LooseObjectProfile::throwCooldown),
            Codec.FLOAT.fieldOf("physical_width").forGetter(LooseObjectProfile::physicalWidth),
            Codec.FLOAT.fieldOf("physical_height").forGetter(LooseObjectProfile::physicalHeight),
            VisualTuning.CODEC.fieldOf("visual").forGetter(LooseObjectProfile::visual)
    ).apply(i, LooseObjectProfile::new));

    public LooseObjectProfile {
        if (!Float.isFinite(mass) || mass <= 0 || !Float.isFinite(friction) || friction < 0
                || !Float.isFinite(restitution) || restitution < 0 || !Float.isFinite(throwSpeed) || throwSpeed < 0
                || !Float.isFinite(throwSpin) || !Float.isFinite(breakThreshold) || breakThreshold < 0
                || !Float.isFinite(throwRange) || throwRange <= 0 || !Float.isFinite(minimumThrowRange)
                || minimumThrowRange < 0 || minimumThrowRange > throwRange || throwCooldown < 0
                || !Float.isFinite(physicalWidth) || physicalWidth <= 0 || !Float.isFinite(physicalHeight) || physicalHeight <= 0
                || visual == null)
            throw new IllegalArgumentException("invalid loose object profile values");
    }

    public static LooseObjectProfile resolve(ResourceLocation id) {
        return ModLooseObjectProfiles.require(PrototypeRuntime.serverLooseObjectProfiles(), id);
    }

    public static LooseObjectProfile resolveClient(ResourceLocation id) {
        return ModLooseObjectProfiles.require(PrototypeRuntime.clientLooseObjectProfiles(), id);
    }

    /** Resolves stack classification without changing its authoritative data components. */
    public static LooseObjectProfile resolveForStack(ItemStack stack, boolean client) {
        ResourceLocation id = stack.get(ModDataComponents.LOOSE_OBJECT_PROFILE.get());
        if (id == null && stack.getItem() instanceof BlockItem) id = BLOCK_MODEL_ID;
        if (id == null) id = GENERIC_ID;
        return client ? resolveClient(id) : resolve(id);
    }
}
