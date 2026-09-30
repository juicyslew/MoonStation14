package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.item.ModItems;
import com.juicyslew.moonstation14.ms14.storage.pouch.PouchContents;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PouchPersistenceGameTests {
    private PouchPersistenceGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void wearableOneCellStoragePersistsAndRejectsNesting(GameTestHelper helper) {
        RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
        Item[] hosts = { ModItems.POUCH.get(), ModItems.BAG.get(), ModItems.BELT.get() };
        for (Item type : hosts) {
            ItemStack host = new ItemStack(type);
            require(host.getMaxStackSize() == 1 && PouchContents.read(host).orElseThrow().child().isEmpty(),
                    "registered storage items must be nonstackable empty hosts");
            ItemStack originalChild = new ItemStack(Items.APPLE, 64);
            ItemStack filled = PouchContents.put(host, "wearable-token", originalChild).orElseThrow();
            originalChild.setCount(1);
            require(PouchContents.read(host).orElseThrow().child().isEmpty()
                    && PouchContents.read(filled).orElseThrow().child().orElseThrow().stack().getCount() == 64,
                    "put must attach a detached whole stack only to the result");
            require(PouchContents.put(filled, "second", new ItemStack(Items.STICK)).isEmpty(),
                    "one cell cannot be overwritten");
            ItemStack decoded = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, filled).getOrThrow()).getOrThrow();
            require(ItemStack.matches(decoded, filled), "host component must survive ItemStack codec");
            ItemEntity dropped = helper.spawn(EntityType.ITEM, new BlockPos(1, 1, 1));
            dropped.setItem(filled.copy());
            ItemEntity loaded = EntityType.ITEM.create(helper.getLevel());
            require(loaded != null, "item entity must exist");
            loaded.load(dropped.saveWithoutId(new CompoundTag()));
            ItemStack restored = loaded.getItem();
            require(restored.is(type) && restored.getCount() == 1
                    && PouchContents.read(restored).orElseThrow().child().orElseThrow().token().equals("wearable-token")
                    && PouchContents.read(restored).orElseThrow().child().orElseThrow().stack().getCount() == 64,
                    "dropped host must keep its child, token, and count through save/load");
            PouchContents.Removal removed = PouchContents.take(restored).orElseThrow();
            require(removed.pouch().is(type) && PouchContents.read(removed.pouch()).orElseThrow().child().isEmpty()
                    && removed.child().stack().getCount() == 64
                    && PouchContents.read(restored).orElseThrow().child().isPresent(),
                    "take must return detached host and whole child without mutating source");
            for (Item nested : hosts) {
                require(PouchContents.put(host, "nested", new ItemStack(nested)).isEmpty()
                        && rejectedChild(ops, new ItemStack(nested)), "all three storage items are forbidden children");
            }
            ItemStack stacked = new ItemStack(type);
            stacked.setCount(2);
            require(PouchContents.read(stacked).isEmpty() && PouchContents.put(stacked, "x", new ItemStack(Items.APPLE)).isEmpty(),
                    "count-two hosts fail closed even if constructed artificially");
            ItemStack foreignHost = host.copy();
            foreignHost.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
            require(PouchContents.read(foreignHost).isEmpty()
                    && PouchContents.put(foreignHost, "x", new ItemStack(Items.APPLE)).isEmpty(),
                    "stack-bearing native component invalidates every host");
            ItemStack foreignChild = new ItemStack(Items.STICK);
            foreignChild.set(ModDataComponents.POUCH_CONTENTS.get(), PouchContents.empty());
            require(PouchContents.put(host, "x", foreignChild).isEmpty() && rejectedChild(ops, foreignChild),
                    "foreign stack bearing our component cannot enter the cell");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pouchContentsTravelWithDroppedStack(GameTestHelper helper) {
        ItemStack pouch = new ItemStack(ModItems.POUCH.get());
        ItemStack apples = new ItemStack(Items.APPLE, 19);
        ItemStack filled = PouchContents.put(pouch, "apple-token", apples).orElseThrow();
        RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
        net.minecraft.nbt.Tag encoded = ItemStack.CODEC.encodeStart(ops, filled).getOrThrow();
        ItemStack decoded = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
        require(ItemStack.matches(decoded, filled), "registered pouch component must round-trip in ItemStack.CODEC");
        RegistryFriendlyByteBuf wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            PouchContents.STREAM_CODEC.encode(wire, PouchContents.read(filled).orElseThrow());
            PouchContents fromWire = PouchContents.STREAM_CODEC.decode(wire);
            require(fromWire.equals(PouchContents.read(filled).orElseThrow()), "registered network codec must round-trip");
        } finally {
            wire.release();
        }
        apples.setCount(1);
        require(!pouch.has(ModDataComponents.POUCH_CONTENTS.get()), "input pouch must remain empty");
        require(PouchContents.read(filled).orElseThrow().child().orElseThrow().stack().getCount() == 19,
                "cell must copy the original child count");

        ItemEntity dropped = helper.spawn(EntityType.ITEM, new BlockPos(1, 1, 1));
        dropped.setItem(filled.copy());
        CompoundTag saved = dropped.saveWithoutId(new CompoundTag());
        ItemEntity loaded = EntityType.ITEM.create(helper.getLevel());
        require(loaded != null, "item entity must exist");
        loaded.load(saved);
        ItemStack restored = loaded.getItem();
        require(restored.is(ModItems.POUCH) && restored.getCount() == 1, "pouch identity must persist");
        PouchContents.Child child = PouchContents.read(restored).orElseThrow().child().orElseThrow();
        require(child.token().equals("apple-token") && child.stack().is(Items.APPLE)
                && child.stack().getCount() == 19, "child identity, token and count must persist");
        child.stack().setCount(1);
        require(PouchContents.read(restored).orElseThrow().child().orElseThrow().stack().getCount() == 19,
                "read must be detached");
        PouchContents.Removal removed = PouchContents.take(restored).orElseThrow();
        require(removed.child().stack().getCount() == 19 && removed.pouch().is(ModItems.POUCH)
                && PouchContents.read(removed.pouch()).orElseThrow().child().isEmpty(), "take preserves both stacks");
        require(PouchContents.read(restored).orElseThrow().child().isPresent(), "take must not mutate source");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void invalidChildrenAndForeignHostsAreRejected(GameTestHelper helper) {
        ItemStack pouch = new ItemStack(ModItems.POUCH.get());
        require(PouchContents.read(pouch).orElseThrow().child().isEmpty(), "absent component means empty pouch");
        require(PouchContents.put(pouch, "nested", pouch).isEmpty(), "no pouch nesting");
        require(PouchContents.put(pouch, "", new ItemStack(Items.APPLE)).isEmpty(), "token must be valid");
        require(PouchContents.put(pouch, "too-many", new ItemStack(Items.APPLE, 65)).isEmpty(),
                "count must fit the stack and the cell limit");
        RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
        CompoundTag invalid = new CompoundTag();
        CompoundTag childTag = new CompoundTag();
        childTag.putString("token", "x".repeat(ItemToken.MAX_LENGTH + 1));
        childTag.put("stack", ItemStack.CODEC.encodeStart(ops, new ItemStack(Items.APPLE)).getOrThrow());
        invalid.put("child", childTag);
        require(PouchContents.CODEC.parse(ops, invalid).isError(), "codec must reject oversized token");
        childTag.putString("token", "valid");
        childTag.put("stack", ItemStack.CODEC.encodeStart(ops, pouch).getOrThrow());
        require(PouchContents.CODEC.parse(ops, invalid).isError(), "codec must reject nested pouch");
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        bundle.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        require(PouchContents.put(pouch, "bundle", bundle).isEmpty(), "even empty bundle contents must be rejected");
        ItemStack foreign = new ItemStack(Items.STICK);
        foreign.set(ModDataComponents.POUCH_CONTENTS.get(), PouchContents.of("fake", new ItemStack(Items.APPLE)));
        require(PouchContents.read(foreign).isEmpty() && PouchContents.put(foreign, "x", new ItemStack(Items.APPLE)).isEmpty(),
                "foreign component does not grant storage");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nestedRemainderAndNativeHostStorageAreRejected(GameTestHelper helper) {
        ItemStack pouch = new ItemStack(ModItems.POUCH.get());
        ItemStack filled = PouchContents.put(pouch, "inside", new ItemStack(Items.APPLE)).orElseThrow();
        // 1.21.1 stores the use remainder in FOOD.usingConvertsTo, not USE_REMAINDER.
        ItemStack remainder = new ItemStack(Items.STICK);
        remainder.set(DataComponents.FOOD, new FoodProperties(1, 0, false, 1.6F,
                Optional.of(filled), List.of()));
        require(PouchContents.put(pouch, "remainder", remainder).isEmpty(),
                "populated food use remainder must not smuggle a filled pouch");
        RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
        require(rejectedChild(ops, remainder), "codec must reject nested filled pouch in use remainder");
        ItemStack disguised = new ItemStack(Items.STICK);
        disguised.set(ModDataComponents.POUCH_CONTENTS.get(), PouchContents.of("inside", new ItemStack(Items.APPLE)));
        require(PouchContents.put(pouch, "disguised", disguised).isEmpty() && rejectedChild(ops, disguised),
                "foreign item bearing pouch contents must not enter the cell by API or codec");

        ItemStack nativeHost = filled.copy();
        nativeHost.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND))));
        require(PouchContents.put(pouch, "container", nativeHost).isEmpty() && rejectedChild(ops, nativeHost),
                "native container must not enter another pouch by API or codec");
        require(PouchContents.read(nativeHost).isEmpty() && PouchContents.take(nativeHost).isEmpty()
                && PouchContents.put(nativeHost, "other", new ItemStack(Items.STICK)).isEmpty(),
                "pouch with populated native container must fail closed even when pouch cell is occupied");
        ItemStack emptyHost = pouch.copy();
        emptyHost.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        require(PouchContents.read(emptyHost).isEmpty() && PouchContents.take(emptyHost).isEmpty()
                && PouchContents.put(emptyHost, "other", new ItemStack(Items.STICK)).isEmpty(),
                "even empty native storage on a pouch host must fail closed");
        ItemStack remainderHost = pouch.copy();
        remainderHost.set(DataComponents.FOOD, new FoodProperties(1, 0, false, 1.6F,
                Optional.of(filled), List.of()));
        require(PouchContents.read(remainderHost).isEmpty() && PouchContents.take(remainderHost).isEmpty()
                && PouchContents.put(remainderHost, "other", new ItemStack(Items.STICK)).isEmpty(),
                "use remainder on a pouch host must fail closed");

        require(PouchContents.put(pouch, "apple", new ItemStack(Items.APPLE)).isPresent(),
                "ordinary food without a remainder must remain allowed");
        require(PouchContents.put(pouch, "potion", new ItemStack(Items.POTION)).isPresent(),
                "ordinary potion must remain allowed");
        require(PouchContents.put(pouch, "stick", new ItemStack(Items.STICK)).isPresent(),
                "ordinary item must remain allowed");
        helper.succeed();
    }

    private static boolean rejectedChild(RegistryOps<net.minecraft.nbt.Tag> ops, ItemStack stack) {
        CompoundTag encoded = new CompoundTag();
        CompoundTag child = new CompoundTag();
        child.putString("token", "test");
        child.put("stack", ItemStack.CODEC.encodeStart(ops, stack).getOrThrow());
        encoded.put("child", child);
        return PouchContents.CODEC.parse(ops, encoded).isError();
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new GameTestAssertException(message);
    }
}
