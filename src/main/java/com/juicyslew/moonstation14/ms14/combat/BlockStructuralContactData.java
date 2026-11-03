package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.juicyslew.moonstation14.ms14.damage.DamageModifierData;

/** Explicit block-keyed melee contact-only damage modifier. This never grants block damage. */
public record BlockStructuralContactData(String block, DamageModifierData structuralContact) {
    public static final Codec<BlockStructuralContactData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("block").forGetter(BlockStructuralContactData::block),
            DamageModifierData.CODEC.fieldOf("structuralContact").forGetter(BlockStructuralContactData::structuralContact)
    ).apply(i, BlockStructuralContactData::new));
    public BlockStructuralContactData {
        if (block == null || block.isBlank() || structuralContact == null)
            throw new IllegalArgumentException("invalid block structural contact mapping");
    }
}
