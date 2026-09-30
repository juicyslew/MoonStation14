package com.juicyslew.moonstation14.ms14.storage.pouch;

import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** One bounded whole-stack cell attached to a registered pouch, bag, or belt ItemStack. */
public final class PouchContents {
    private static final Codec<String> TOKEN_CODEC = Codec.STRING.validate(token ->
            token.isBlank() || token.length() > ItemToken.MAX_LENGTH
                    ? DataResult.error(() -> "invalid pouch child token") : DataResult.success(token));
    private static final Codec<RawChild> RAW_CHILD_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TOKEN_CODEC.fieldOf("token").forGetter(RawChild::token),
            ItemStack.CODEC.fieldOf("stack").forGetter(RawChild::stack)
    ).apply(instance, RawChild::new));
    private static final Codec<Child> CHILD_CODEC = RAW_CHILD_CODEC.comapFlatMap(child -> {
        try {
            return DataResult.success(new Child(child.token(), child.stack()));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }, child -> new RawChild(child.token(), child.stack()));

    private record RawChild(String token, ItemStack stack) { }

    public static final Codec<PouchContents> CODEC = CHILD_CODEC.optionalFieldOf("child").codec()
            .xmap(PouchContents::new, PouchContents::child);
    // Registry-aware stack codec. Child admission rejects nested stack-bearing components.
    public static final StreamCodec<RegistryFriendlyByteBuf, PouchContents> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    private final Child child;

    private PouchContents(Optional<Child> child) {
        this.child = child.map(value -> new Child(value.token(), value.stack())).orElse(null);
    }

    public static PouchContents empty() {
        return new PouchContents(Optional.empty());
    }

    public static PouchContents of(String token, ItemStack stack) {
        return new PouchContents(Optional.of(new Child(token, stack)));
    }

    public Optional<Child> child() {
        return child == null ? Optional.empty() : Optional.of(new Child(child.token(), child.stack()));
    }

    /** Absence is empty only on a registered count-one host; foreign stacks cannot become containers. */
    public static Optional<PouchContents> read(ItemStack pouch) {
        if (pouch == null || pouch.isEmpty() || !isStorageItem(pouch) || pouch.getCount() != 1
                || hasForeignStorage(pouch))
            return Optional.empty();
        return Optional.ofNullable(pouch.get(ModDataComponents.POUCH_CONTENTS.get()))
                .or(() -> Optional.of(empty()));
    }

    /** Returns a new host stack; never changes the source or the supplied child. */
    public static Optional<ItemStack> put(ItemStack pouch, String token, ItemStack stack) {
        Optional<PouchContents> current = read(pouch);
        if (current.isEmpty() || current.orElseThrow().child != null) return Optional.empty();
        try {
            PouchContents next = of(token, stack);
            ItemStack result = pouch.copy();
            result.set(ModDataComponents.POUCH_CONTENTS.get(), next);
            return Optional.of(result);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    /** Removes the cell from a copy; callers receive a detached child and a detached host. */
    public static Optional<Removal> take(ItemStack pouch) {
        Optional<PouchContents> current = read(pouch);
        if (current.isEmpty() || current.orElseThrow().child == null) return Optional.empty();
        Child value = current.orElseThrow().child;
        ItemStack result = pouch.copy();
        result.remove(ModDataComponents.POUCH_CONTENTS.get());
        return Optional.of(new Removal(result, new Child(value.token(), value.stack())));
    }

    private static boolean isStorageItem(ItemStack stack) {
        return stack.is(ModItems.POUCH) || stack.is(ModItems.BAG) || stack.is(ModItems.BELT);
    }

    /** Explicit stack-bearing components in 1.21.1; FOOD.usingConvertsTo is this version's use remainder. */
    private static boolean hasForeignStorage(ItemStack stack) {
        return stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.CONTAINER_LOOT)
                || stack.has(DataComponents.BUNDLE_CONTENTS)
                || stack.has(DataComponents.CHARGED_PROJECTILES)
                || stack.has(DataComponents.BLOCK_ENTITY_DATA) || stack.has(DataComponents.ENTITY_DATA)
                || stack.has(DataComponents.BUCKET_ENTITY_DATA) || stack.has(DataComponents.BEES)
                || (stack.has(DataComponents.FOOD) && stack.get(DataComponents.FOOD).usingConvertsTo().isPresent());
    }

    public record Child(String token, ItemStack stack) {
        public Child {
            if (token == null || token.isBlank() || token.length() > ItemToken.MAX_LENGTH || stack == null
                    || stack.isEmpty() || stack.getCount() < 1 || stack.getCount() > 64
                    || stack.getCount() > stack.getMaxStackSize() || isStorageItem(stack)
                    || hasForeignStorage(stack)
                    || stack.has(ModDataComponents.POUCH_CONTENTS.get()))
                throw new IllegalArgumentException("invalid pouch child");
            stack = stack.copy();
        }

        @Override public ItemStack stack() { return stack.copy(); }
    }

    public record Removal(ItemStack pouch, Child child) {
        public Removal { pouch = pouch.copy(); child = new Child(child.token(), child.stack()); }
        @Override public ItemStack pouch() { return pouch.copy(); }
        @Override public Child child() { return new Child(child.token(), child.stack()); }
    }

    @Override public boolean equals(Object other) {
        return other instanceof PouchContents contents &&
                (child == null ? contents.child == null : contents.child != null
                        && child.token.equals(contents.child.token)
                        && ItemStack.matches(child.stack, contents.child.stack));
    }

    @Override public int hashCode() {
        return child == null ? 0 : 31 * child.token.hashCode() + ItemStack.hashItemAndComponents(child.stack) * 31
                + child.stack.getCount();
    }
}
