package com.juicyslew.moonstation14.ms14.chat.identity;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One overworld-owned ledger; never reads entity chunks or client-supplied identity. */
public final class ChatIdentitySavedData extends SavedData {
    public static final String DATA_NAME = "moonstation14_chat_identity_v1";
    public static final Factory<ChatIdentitySavedData> FACTORY = new Factory<>(ChatIdentitySavedData::new,
            ChatIdentitySavedData::load, null);
    // V2 adds a saved RGB to each NPC. A V1 file must fail closed, never silently recolor NPCs.
    private static final int VERSION = 2;
    private static final int MAX_ENTRIES = 1_000_000;
    private final ChatIdentityRegistry registry;

    public ChatIdentitySavedData() { registry = new ChatIdentityRegistry(); }
    private ChatIdentitySavedData(ChatIdentityRegistry registry) { this.registry = registry; }

    /** Non-creating lookup. Empty can also indicate a failed load; never treat empty as enrollment proof. */
    public static Optional<ChatIdentitySavedData> existing(ServerLevel overworld) {
        requireOverworld(overworld);
        return Optional.ofNullable(overworld.getDataStorage().get(FACTORY, DATA_NAME));
    }

    /** Use only after authoritative first-enrollment verification, on the server thread. */
    public static ChatIdentitySavedData forFirstEnrollment(ServerLevel overworld) {
        requireOverworld(overworld);
        ChatIdentitySavedData loaded = overworld.getDataStorage().get(FACTORY, DATA_NAME);
        if (loaded != null) return loaded;
        // DimensionDataStorage catches deserializer/IO exceptions and caches null; computeIfAbsent would
        // silently replace that corrupt file. ServerChunkCache stores overworld data at <world>/data.
        requireNoUnloadedSave(overworld.getServer().getWorldPath(new LevelResource("data"))
                .resolve(DATA_NAME + ".dat"));
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    static void requireNoUnloadedSave(Path file) {
        if (Files.exists(file))
            throw new IllegalStateException("Chat identity save exists but could not be loaded; refusing replacement");
    }

    private static void requireOverworld(ServerLevel level) {
        if (!Objects.requireNonNull(level).dimension().equals(Level.OVERWORLD))
            throw new IllegalArgumentException("Chat identities belong to the overworld save only");
    }

    public Optional<ChatIdentityRegistry.Entry> character(ChatIdentityRegistry.CharacterKey key) {
        return registry.character(key);
    }

    public ChatIdentityRegistry.Entry allocateCharacter(ChatIdentityRegistry.CharacterKey key) {
        boolean absent = registry.character(key).isEmpty();
        ChatIdentityRegistry.Entry entry = registry.allocateCharacter(key);
        if (absent) setDirty();
        return entry;
    }

    public Optional<ChatIdentityRegistry.NpcEntry> npc(UUID persistentEntityId) { return registry.npc(persistentEntityId); }

    public ChatIdentityRegistry.NpcEntry allocateNpc(UUID persistentEntityId, String type) {
        boolean absent = registry.npc(persistentEntityId).isEmpty();
        ChatIdentityRegistry.NpcEntry entry = registry.allocateNpc(persistentEntityId, type);
        if (absent) setDirty();
        return entry;
    }

    public Optional<Integer> colorForPresentedName(String alias) { return registry.colorForPresentedName(alias); }

    public static ChatIdentitySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        Objects.requireNonNull(tag);
        if (!tag.contains("Version", CompoundTag.TAG_INT) || tag.getInt("Version") != VERSION
                || !tag.contains("Characters", CompoundTag.TAG_LIST) || !tag.contains("Npcs", CompoundTag.TAG_LIST))
            throw new IllegalArgumentException("Missing or unsupported chat identity save");
        ListTag people = (ListTag) tag.get("Characters");
        ListTag mobs = (ListTag) tag.get("Npcs");
        if (people.size() > MAX_ENTRIES || mobs.size() > MAX_ENTRIES)
            throw new IllegalArgumentException("Oversized chat identity save");
        Map<ChatIdentityRegistry.CharacterKey, ChatIdentityRegistry.Entry> characters = new HashMap<>();
        Map<UUID, ChatIdentityRegistry.NpcEntry> npcs = new HashMap<>();
        for (int i = 0; i < people.size(); i++) {
            if (!(people.get(i) instanceof CompoundTag)) throw new IllegalArgumentException("Invalid character list element");
            CompoundTag item = people.getCompound(i);
            if (!item.hasUUID("Account") || !item.contains("Profile", CompoundTag.TAG_STRING)
                    || !item.contains("Name", CompoundTag.TAG_STRING) || !item.contains("Rgb", CompoundTag.TAG_INT))
                throw new IllegalArgumentException("Incomplete saved character identity");
            var key = new ChatIdentityRegistry.CharacterKey(item.getUUID("Account"), item.getString("Profile"));
            var value = new ChatIdentityRegistry.Entry(item.getString("Name"), item.getInt("Rgb"));
            if (characters.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Duplicate character key");
        }
        for (int i = 0; i < mobs.size(); i++) {
            if (!(mobs.get(i) instanceof CompoundTag)) throw new IllegalArgumentException("Invalid NPC list element");
            CompoundTag item = mobs.getCompound(i);
            if (!item.hasUUID("Entity") || !item.contains("Type", CompoundTag.TAG_STRING)
                    || !item.contains("Number", CompoundTag.TAG_INT) || !item.contains("Rgb", CompoundTag.TAG_INT))
                throw new IllegalArgumentException("Incomplete saved NPC identity");
            var value = new ChatIdentityRegistry.NpcEntry(item.getString("Type"), item.getInt("Number"), item.getInt("Rgb"));
            if (npcs.putIfAbsent(item.getUUID("Entity"), value) != null) throw new IllegalArgumentException("Duplicate NPC UUID");
        }
        return new ChatIdentitySavedData(ChatIdentityRegistry.restore(characters, npcs));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", VERSION);
        ListTag people = new ListTag();
        registry.charactersSnapshot().forEach((key, value) -> {
            CompoundTag item = new CompoundTag();
            item.putUUID("Account", key.accountId());
            item.putString("Profile", key.profileKey());
            item.putString("Name", value.name());
            item.putInt("Rgb", value.rgb());
            people.add(item);
        });
        tag.put("Characters", people);
        ListTag mobs = new ListTag();
        registry.npcsSnapshot().forEach((uuid, value) -> {
            CompoundTag item = new CompoundTag();
            item.putUUID("Entity", uuid);
            item.putString("Type", value.type());
            item.putInt("Number", value.number());
            item.putInt("Rgb", value.rgb());
            mobs.add(item);
        });
        tag.put("Npcs", mobs);
        return tag;
    }
}
