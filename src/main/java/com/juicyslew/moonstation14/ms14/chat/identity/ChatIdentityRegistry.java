package com.juicyslew.moonstation14.ms14.chat.identity;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Server-only, append-only speaker identity ledger. Call allocateCharacter ONLY on confirmed first enrollment. */
public final class ChatIdentityRegistry {
    private static final String[] FIRST = {"Ada", "Arden", "Astrid", "Basil", "Celia", "Dara", "Elias", "Eliza",
            "Felix", "Greta", "Hana", "Iris", "Jasper", "Jonah", "Kira", "Lena", "Mara", "Nadia",
            "Nico", "Orion", "Piper", "Quinn", "Rosa", "Rowan", "Sable", "Selene", "Talia", "Theo",
            "Uma", "Vera", "Willa", "Zara"};
    private static final String[] LAST = {"Alder", "Ashford", "Bennett", "Briar", "Caldwell", "Carver", "Dalton", "Dawes",
            "Ellis", "Farrow", "Fletcher", "Gale", "Harlow", "Hart", "Hollis", "Keene", "Larkin", "Linden",
            "Marlow", "Mercer", "North", "Oakley", "Parker", "Reed", "Rhodes", "Sutton", "Vale", "Voss",
            "Ward", "Wells", "Wren", "Yates"};
    private static final int[] PALETTE = {0xFFFFFF, 0xFFB8B8, 0xFFD58A, 0xFFF59B, 0xBDECA3, 0x8FE8DE,
            0xA9D4FF, 0xD1BBFF, 0xFFB5E0, 0xE6DE92};
    private static final int BACKGROUND = 0x202020;
    private static final int MAX_NPC_NUMBER = 1_000_000;
    private final Map<CharacterKey, Entry> characters = new HashMap<>();
    private final Map<UUID, NpcEntry> npcs = new HashMap<>();
    private final Map<String, Entry> names = new HashMap<>();
    private final Map<String, Integer> claimedColors = new HashMap<>();
    private final Map<String, Integer> nextNpc = new HashMap<>();

    public ChatIdentityRegistry() {
        for (int color : PALETTE) if (!validColor(color)) throw new IllegalStateException("Unsafe chat identity palette");
    }

    public record CharacterKey(UUID accountId, String profileKey) {
        public CharacterKey {
            Objects.requireNonNull(accountId, "accountId");
            if (profileKey == null || !profileKey.matches("[A-Za-z0-9_-]{1,64}"))
                throw new IllegalArgumentException("Invalid profile key");
        }
    }

    /** Saved actual name and RGB are immutable; alias is never stored in this record. */
    public record Entry(String name, int rgb) {
        public Entry {
            if (!validHumanName(name) || !validColor(rgb)) throw new IllegalArgumentException("Invalid identity entry");
        }
    }

    public record NpcEntry(String type, int number, int rgb) {
        public NpcEntry {
            if (!validNpcType(type) || number < 1 || number > MAX_NPC_NUMBER || !validColor(rgb))
                throw new IllegalArgumentException("Invalid NPC identity");
        }
        public String name() { return type + " (" + number + ")"; }
    }

    /** Idempotent only for a known key. Caller MUST prove first enrollment before allocating an unknown key. */
    public synchronized Entry allocateCharacter(CharacterKey key) {
        Objects.requireNonNull(key, "key");
        Entry existing = characters.get(key);
        if (existing != null) return existing;
        int size = FIRST.length * LAST.length;
        int start = Math.floorMod(key.hashCode(), size);
        for (int offset = 0; offset < size; offset++) {
            int index = (start + offset) % size;
            String name = FIRST[index / LAST.length] + " " + LAST[index % LAST.length];
            if (!claimedColors.containsKey(normalize(name))) {
                Entry entry = new Entry(name, PALETTE[Math.floorMod(key.hashCode(), PALETTE.length)]);
                characters.put(key, entry);
                names.put(normalize(name), entry);
                claimedColors.put(normalize(name), entry.rgb());
                return entry;
            }
        }
        throw new IllegalStateException("Character name space exhausted");
    }

    public synchronized Optional<Entry> character(CharacterKey key) {
        return Optional.ofNullable(characters.get(Objects.requireNonNull(key)));
    }

    /** Presented aliases resolve to the CLAIMED human name's saved color; unknown aliases have no trusted color. */
    public synchronized Optional<Entry> presentedAlias(String alias) {
        String normalized = normalize(alias);
        return Optional.ofNullable(names.get(normalized));
    }

    public synchronized NpcEntry allocateNpc(UUID persistentEntityId, String type) {
        Objects.requireNonNull(persistentEntityId, "persistentEntityId");
        if (!validNpcType(type)) throw new IllegalArgumentException("Invalid NPC type");
        NpcEntry existing = npcs.get(persistentEntityId);
        if (existing != null) {
            if (!existing.type().equals(type)) throw new IllegalStateException("NPC type changed for persistent UUID");
            return existing;
        }
        int number = nextNpc.getOrDefault(type, 1);
        if (number > MAX_NPC_NUMBER) throw new IllegalStateException("NPC number space exhausted");
        NpcEntry entry = new NpcEntry(type, number, PALETTE[Math.floorMod(persistentEntityId.hashCode(), PALETTE.length)]);
        String normalized = normalize(entry.name());
        if (claimedColors.containsKey(normalized))
            throw new IllegalStateException("NPC name collision");
        npcs.put(persistentEntityId, entry);
        claimedColors.put(normalized, entry.rgb());
        nextNpc.put(type, number + 1);
        return entry;
    }

    public synchronized Optional<NpcEntry> npc(UUID persistentEntityId) {
        return Optional.ofNullable(npcs.get(Objects.requireNonNull(persistentEntityId)));
    }

    /** Existing claimed names (human or numbered NPC) lend their immutable saved RGB to impersonators. */
    public synchronized Optional<Integer> colorForPresentedName(String presentedName) {
        return Optional.ofNullable(claimedColors.get(normalize(presentedName)));
    }

    static String normalize(String name) {
        if (name == null) return "";
        String stripped = name.replaceAll("(?i)§[0-9a-fk-or]", "");
        return Normalizer.normalize(stripped.strip(), Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static boolean validHumanName(String name) {
        if (name == null || !name.matches("[A-Za-z]{2,24} [A-Za-z]{2,24}")) return false;
        String value = normalize(name);
        return !Set.of("system", "server", "admin", "administrator", "console", "narrator", "unknown")
                .contains(value);
    }

    private static boolean validNpcType(String type) {
        return type != null && type.matches("[A-Z][A-Za-z]{1,31}")
                && !Set.of("System", "Server", "Admin", "Console", "Narrator", "Unknown")
                .contains(type);
    }

    public static boolean validColor(int rgb) {
        if ((rgb & 0xFF000000) != 0) return false;
        return (luminance(rgb) + 0.05) / (luminance(BACKGROUND) + 0.05) >= 4.5;
    }

    private static double luminance(int rgb) {
        double total = 0;
        int[] shifts = {16, 8, 0};
        double[] weights = {0.2126, 0.7152, 0.0722};
        for (int i = 0; i < 3; i++) {
            double c = ((rgb >> shifts[i]) & 255) / 255.0;
            total += weights[i] * (c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
        }
        return total;
    }

    synchronized Map<CharacterKey, Entry> charactersSnapshot() { return Map.copyOf(characters); }
    synchronized Map<UUID, NpcEntry> npcsSnapshot() { return Map.copyOf(npcs); }

    static ChatIdentityRegistry restore(Map<CharacterKey, Entry> characters, Map<UUID, NpcEntry> npcs) {
        ChatIdentityRegistry ledger = new ChatIdentityRegistry();
        Set<String> seen = new HashSet<>();
        for (var item : characters.entrySet()) {
            String name = normalize(item.getValue().name());
            if (!seen.add(name) || !validHumanName(item.getValue().name()))
                throw new IllegalArgumentException("Duplicate or invalid saved character name");
            ledger.characters.put(item.getKey(), item.getValue());
            ledger.names.put(name, item.getValue());
            ledger.claimedColors.put(name, item.getValue().rgb());
        }
        for (var item : npcs.entrySet()) {
            NpcEntry entry = item.getValue();
            if (!seen.add(normalize(entry.name()))) throw new IllegalArgumentException("Duplicate saved NPC name");
            int next = ledger.nextNpc.getOrDefault(entry.type(), 1);
            ledger.nextNpc.put(entry.type(), Math.max(next, entry.number() + 1));
            ledger.npcs.put(item.getKey(), entry);
            ledger.claimedColors.put(normalize(entry.name()), entry.rgb());
        }
        return ledger;
    }
}
