package com.juicyslew.moonstation14.ms14.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

/** Physical magazine and chamber contents attached to a gun. */
public record MagazineChamberState(ItemStack magazine, ItemStack chamber, boolean boltClosed) {
    public static final Codec<MagazineChamberState> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("magazine", ItemStack.EMPTY).forGetter(MagazineChamberState::magazine),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("chamber", ItemStack.EMPTY).forGetter(MagazineChamberState::chamber),
            Codec.BOOL.optionalFieldOf("bolt_closed", false).forGetter(MagazineChamberState::boltClosed)
    ).apply(i, MagazineChamberState::new));
    public MagazineChamberState { magazine = magazine.copy(); chamber = chamber.copy(); }
    @Override public ItemStack magazine() { return magazine.copy(); }
    @Override public ItemStack chamber() { return chamber.copy(); }
    public static MagazineChamberState empty(boolean boltClosed) { return new MagazineChamberState(ItemStack.EMPTY, ItemStack.EMPTY, boltClosed); }
}
