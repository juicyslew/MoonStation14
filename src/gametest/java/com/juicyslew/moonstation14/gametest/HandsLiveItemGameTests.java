package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.hands.HandAttachment;
import com.juicyslew.moonstation14.ms14.hands.HandComponent;
import com.juicyslew.moonstation14.ms14.hands.HandState;
import com.juicyslew.moonstation14.ms14.hands.ItemToken;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HandsLiveItemGameTests {
    private HandsLiveItemGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void liveStacksPersistComponentsAndReadsDoNotInitialize(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        Zombie unbound = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 1));
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(5, 1, 1));
        helper.runAfterDelay(1, () -> {
            require(!LiveHands.isEmptyHand(body, "left") && !body.hasData(ModDataAttachments.LIVE_HANDS.get()),
                    "absence cannot authorize empty-hand actions");
            require(LiveHands.initialize(pig).isEmpty() && LiveHands.initialize(unbound).isEmpty()
                            && !pig.hasData(ModDataAttachments.LIVE_HANDS.get())
                            && !unbound.hasData(ModDataAttachments.LIVE_HANDS.get()),
                    "pig and unbound actor cannot inherit human hands");
            require(LiveHands.initialize(body).isPresent() && LiveHands.isEmptyHand(body, "left"),
                    "bound human can explicitly initialize an empty layout");

            CharacterIdentityAttachment wrongHost = new CharacterIdentityAttachment();
            wrongHost.bind(ModCharacters.HUMAN_ID);
            pig.setData(ModDataAttachments.CHARACTER_IDENTITY.get(), wrongHost);
            require(LiveHands.initialize(pig).isEmpty() && !pig.hasData(ModDataAttachments.LIVE_HANDS.get()),
                    "pig bound to human must not initialize human live hands");

            ItemStack source = new ItemStack(Items.DIAMOND, 7);
            source.set(DataComponents.CUSTOM_NAME, Component.literal("live hand fixture"));
            CompoundTag encoded = fixture(helper, source, 0, "left", "right", "fixture-token", false);
            LiveHands live = decode(helper, encoded);
            body.setData(ModDataAttachments.LIVE_HANDS.get(), live);
            source.setCount(1);
            source.set(DataComponents.CUSTOM_NAME, Component.literal("source changed"));
            ItemStack exposed = live.stackCopy("left").orElseThrow();
            require(exposed.getCount() == 7 && exposed.getHoverName().getString().equals("live hand fixture"),
                    "codec ingress copies count and components");
            exposed.setCount(2);
            require(live.stackCopy("left").orElseThrow().getCount() == 7,
                    "egress never exposes owned stack");
            require(!LiveHands.isEmptyHand(body, "left") && LiveHands.isEmptyHand(body, "right"),
                    "emptiness must come from live occupancy");
            pig.setData(ModDataAttachments.LIVE_HANDS.get(), live);
            require(!LiveHands.isEmptyHand(pig, "right") && !live.compatible(pig),
                    "wrong-host human binding cannot authorize an empty hand");
            body.setData(ModDataAttachments.HANDS.get(), new HandAttachment(HandComponent.from(
                    HandState.create(java.util.List.of("left", "right"))
                            .place("right", new ItemToken("legacy-only")).state())));
            require(!LiveHands.isEmptyHand(body, "right"), "legacy occupancy mismatch must fail closed");

            CompoundTag saved = body.saveWithoutId(new CompoundTag());
            Villager restored = EntityType.VILLAGER.create(helper.getLevel());
            require(restored != null, "fixture entity is constructible");
            restored.load(saved);
            LiveHands loaded = restored.getExistingDataOrNull(ModDataAttachments.LIVE_HANDS.get());
            require(loaded != null && loaded.stackCopy("left").orElseThrow().getCount() == 7
                            && loaded.stackCopy("left").orElseThrow().getHoverName().getString().equals("live hand fixture")
                            && loaded.token("left").orElseThrow().equals("fixture-token"),
                    "entity attachment survives save/load with item components");
            require(!unbound.hasData(ModDataAttachments.LIVE_HANDS.get()), "other entity stays untouched");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void restoredEmptyHandsRevalidateHostAndMetadata(GameTestHelper helper) {
        Villager body = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 1, 1));
        helper.runAfterDelay(1, () -> {
            require(LiveHands.initialize(body).isPresent(), "human initialization must succeed");
            CompoundTag saved = body.saveWithoutId(new CompoundTag());
            Villager restored = EntityType.VILLAGER.create(helper.getLevel());
            require(restored != null, "restored human is constructible");
            restored.load(saved);
            require(restored.hasData(ModDataAttachments.LIVE_HANDS.get())
                            && LiveHands.isEmptyHand(restored, "left")
                            && LiveHands.isEmptyHand(restored, "right"),
                    "saved empty hands remain eligible on their human host");

            restored.setData(ModDataAttachments.HANDS.get(), new HandAttachment(HandComponent.from(
                    HandState.create(java.util.List.of("left", "right"), "right"))));
            require(LiveHands.isEmptyHand(restored, "left") && LiveHands.isEmptyHand(restored, "right"),
                    "empty legacy metadata retains its historical active hand without hiding live empty slots");

            Pig wrongHost = EntityType.PIG.create(helper.getLevel());
            require(wrongHost != null, "wrong host is constructible");
            wrongHost.load(saved);
            require(wrongHost.hasData(ModDataAttachments.LIVE_HANDS.get())
                            && !LiveHands.isEmptyHand(wrongHost, "left")
                            && !LiveHands.isEmptyHand(wrongHost, "right"),
                    "restored human identity and empty hands cannot authorize a pig host");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void invalidStatesAndRevisionsFailClosed(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.DIAMOND, 4);
        LiveHands live = decode(helper, fixture(helper, stack, 0, "left", "right", "unique", false));
        require(live.move(1, "left", "right").isEmpty() && live.move(0, "missing", "right").isEmpty()
                        && live.move(0, "left", "left").isEmpty(), "stale revision and wrong hands reject");
        LiveHands moved = live.move(0, "left", "right").orElseThrow();
        require(moved.revision() == 1 && moved.stackCopy("right").orElseThrow().getCount() == 4
                        && live.stackCopy("left").isPresent() && moved.move(0, "right", "left").isEmpty()
                        && moved.activeHand().equals("left"),
                "move preserves original and increments revision");
        require(decodeFails(helper, fixture(helper, stack, 0, "left", "left", "unique", false)),
                "duplicate hand rejects");
        require(decodeFails(helper, fixture(helper, stack, 0, "left", "right", "unique", true)),
                "duplicate token rejects");
        require(decodeFails(helper, fixture(helper, stack, -1, "left", "right", "unique", false)),
                "negative revision rejects");
        CompoundTag missingStack = fixture(helper, stack, 0, "left", "right", "unique", false);
        ((CompoundTag) ((ListTag) missingStack.get("hands")).get(0)).getCompound("occupant").remove("stack");
        require(decodeFails(helper, missingStack), "token without stack rejects");
        CompoundTag missingToken = fixture(helper, stack, 0, "left", "right", "unique", false);
        ((CompoundTag) ((ListTag) missingToken.get("hands")).get(0)).getCompound("occupant").remove("token");
        require(decodeFails(helper, missingToken), "stack without token rejects");
        CompoundTag emptyStack = fixture(helper, stack, 0, "left", "right", "unique", false);
        ((CompoundTag) ((ListTag) emptyStack.get("hands")).get(0)).getCompound("occupant")
                .put("stack", new CompoundTag());
        require(decodeFails(helper, emptyStack), "malformed/empty stack rejects");
        CompoundTag occupiedTarget = fixture(helper, stack, 0, "left", "right", "other", true);
        ((CompoundTag) ((ListTag) occupiedTarget.get("hands")).get(1)).getCompound("occupant")
                .putString("token", "second");
        require(decode(helper, occupiedTarget).move(0, "left", "right").isEmpty(),
                "occupied target rejects without overwriting");
        LiveHands max = decode(helper, fixture(helper, stack, Long.MAX_VALUE, "left", "right", "unique", false));
        require(max.move(Long.MAX_VALUE, "left", "right").isEmpty(), "revision overflow rejects");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void liveSnapshotNbtRoundTripPreservesStackComponentsAndActiveSelection(GameTestHelper helper) {
        ItemStack source = new ItemStack(Items.DIAMOND, 6);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("round-trip component"));
        LiveHands original = decode(helper, fixture(helper, source, 8, "left", "right", "round-trip", false));
        var ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
        LiveHands restored = LiveHands.CODEC.parse(ops, LiveHands.CODEC.encodeStart(ops, original).getOrThrow())
                .getOrThrow();
        source.setCount(1);
        require(restored.revision() == 8 && restored.activeHand().equals("left")
                        && restored.token("left").orElseThrow().equals("round-trip")
                        && restored.stackCopy("left").orElseThrow().getCount() == 6
                        && restored.stackCopy("left").orElseThrow().getHoverName().getString()
                                .equals("round-trip component")
                        && restored.stackCopy("right").isEmpty(),
                "NBT codec round-trip preserves occupancy, components, revision and active hand");
        helper.succeed();
    }

    private static CompoundTag fixture(GameTestHelper helper, ItemStack stack, long revision,
                                       String first, String second, String token, boolean duplicateToken) {
        var ops = RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess());
        CompoundTag occupied = new CompoundTag();
        occupied.putString("token", token);
        occupied.put("stack", ItemStack.CODEC.encodeStart(ops, stack).getOrThrow());
        CompoundTag left = new CompoundTag();
        left.putString("id", first);
        left.put("occupant", occupied);
        CompoundTag right = new CompoundTag();
        right.putString("id", second);
        if (duplicateToken) right.put("occupant", occupied.copy());
        ListTag hands = new ListTag();
        hands.add(left);
        hands.add(right);
        CompoundTag result = new CompoundTag();
        result.put("hands", hands);
        result.putString("active", first);
        result.putLong("revision", revision);
        return result;
    }

    private static LiveHands decode(GameTestHelper helper, CompoundTag tag) {
        return LiveHands.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess()), tag)
                .getOrThrow();
    }

    private static boolean decodeFails(GameTestHelper helper, CompoundTag tag) {
        return LiveHands.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, helper.getLevel().registryAccess()), tag)
                .result().isEmpty();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
