package com.juicyslew.moonstation14.gametest;

import com.juicyslew.moonstation14.MoonStation14;
import com.juicyslew.moonstation14.ms14.hands.quarantine.CreativeInventorySnapshot;
import com.juicyslew.moonstation14.ms14.hands.quarantine.VanillaPlayerDataInventoryView;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder(MoonStation14.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VanillaPlayerDataRealShapeGameTests {
    private VanillaPlayerDataRealShapeGameTests() { }

    @GameTest(template = "empty")
    public static void serverPlayerSaveMatchesAllSupportedSlotsWithoutMutatingPlayerdata(GameTestHelper helper) {
        UUID account = UUID.randomUUID();
        // Unregistered and never connected: this object supplies vanilla's save format only.
        ServerPlayer fixture = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(account, "real-shape-fixture"), ClientInformation.createDefault());
        List<ItemStack> main = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = new ItemStack(Items.STONE, i % 64 + 1);
            fixture.getInventory().items.set(i, stack.copy());
            main.add(stack);
        }
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("saved custom name"));
        fixture.getInventory().items.set(35, named.copy());
        main.set(35, named);
        List<ItemStack> armor = List.of(new ItemStack(Items.DIAMOND_BOOTS),
                new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_CHESTPLATE),
                new ItemStack(Items.DIAMOND_HELMET));
        for (int i = 0; i < 4; i++) fixture.getInventory().armor.set(i, armor.get(i).copy());
        ItemStack shield = new ItemStack(Items.SHIELD);
        fixture.getInventory().offhand.set(0, shield.copy());
        fixture.getInventory().selected = 8;

        CompoundTag saved = fixture.saveWithoutId(new CompoundTag());
        require(saved.hasUUID("UUID") && account.equals(saved.getUUID("UUID")), "vanilla save UUID");
        require(saved.contains("Inventory", Tag.TAG_LIST), "vanilla save Inventory list");
        require(saved.contains("SelectedItemSlot", Tag.TAG_INT)
                && saved.getInt("SelectedItemSlot") == 8, "vanilla save selection");
        require(saved.contains("EnderItems", Tag.TAG_LIST), "vanilla save EnderItems list");
        require(saved.getList("EnderItems", Tag.TAG_COMPOUND).isEmpty(), "fixture EnderItems empty");
        ListTag inventory = saved.getList("Inventory", Tag.TAG_COMPOUND);
        require(inventory.size() == 41, "vanilla saved all 41 occupied slots");
        boolean[] seen = new boolean[41];
        for (Tag entryTag : inventory) {
            require(entryTag instanceof CompoundTag, "inventory entry compound");
            CompoundTag entry = (CompoundTag) entryTag;
            require(entry.contains("Slot", Tag.TAG_BYTE), "vanilla byte Slot");
            int raw = entry.getByte("Slot") & 255;
            int slot = raw <= 35 ? raw : raw >= 100 && raw <= 103 ? raw - 100 + 36
                    : raw == 150 ? 40 : -1;
            require(slot >= 0 && !seen[slot], "vanilla supported, unique slot " + raw);
            seen[slot] = true;
            if (slot >= 35) {
                require(!entry.contains("count") || entry.contains("count", Tag.TAG_INT)
                        && entry.getInt("count") == 1, "vanilla codec count-one slot " + raw);
            }
            if (slot == 35) require(entry.contains("components", Tag.TAG_COMPOUND)
                    && entry.getCompound("components").contains("minecraft:custom_name"),
                    "vanilla custom name component");
        }
        for (int i = 0; i < seen.length; i++) require(seen[i], "missing vanilla slot " + i);

        CompoundTag before = saved.copy();
        var view = VanillaPlayerDataInventoryView.inspect(saved, helper.getLevel().registryAccess(), account);
        require(saved.equals(before), "inspection must not mutate playerdata");
        require(view.availability() == VanillaPlayerDataInventoryView.Availability.AVAILABLE,
                "vanilla save should be available");
        require(view.cursorComparison() == VanillaPlayerDataInventoryView.CursorComparison.UNKNOWN,
                "cursor is not saved in playerdata");
        var matching = capture(main, armor, shield, new ItemStack(Items.EMERALD), 8);
        require(view.compareSupported(matching) == VanillaPlayerDataInventoryView.SupportedComparison.MATCH,
                "vanilla save matches supported slots despite nonempty cursor");
        require(view.compareSupported(capture(main, armor, shield, ItemStack.EMPTY, 8))
                == VanillaPlayerDataInventoryView.SupportedComparison.MATCH, "cursor never compared");
        require(view.compareSupported(capture(main, armor, shield, ItemStack.EMPTY, 7))
                == VanillaPlayerDataInventoryView.SupportedComparison.MISMATCH, "selected slot compared");
        for (int i = 0; i < 41; i++) {
            List<ItemStack> changedMain = new ArrayList<>(main);
            List<ItemStack> changedArmor = new ArrayList<>(armor);
            ItemStack changedOffhand = shield;
            if (i < 36) changedMain.set(i, ItemStack.EMPTY);
            else if (i < 40) changedArmor.set(i - 36, ItemStack.EMPTY);
            else changedOffhand = ItemStack.EMPTY;
            require(view.compareSupported(capture(changedMain, changedArmor, changedOffhand,
                    ItemStack.EMPTY, 8)) == VanillaPlayerDataInventoryView.SupportedComparison.MISMATCH,
                    "supported slot " + i + " compared");
        }
        ItemStack renamed = named.copy();
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("different name"));
        List<ItemStack> changedName = new ArrayList<>(main);
        changedName.set(35, renamed);
        require(view.compareSupported(capture(changedName, armor, shield, ItemStack.EMPTY, 8))
                == VanillaPlayerDataInventoryView.SupportedComparison.MISMATCH, "custom name compared");
        require(saved.equals(before), "comparisons must not mutate playerdata");
        helper.succeed();
    }

    private static CreativeInventorySnapshot capture(List<ItemStack> main, List<ItemStack> armor,
                                                     ItemStack offhand, ItemStack cursor, int selected) {
        return CreativeInventorySnapshot.capture(main, armor, List.of(offhand), cursor, selected, List.of());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
