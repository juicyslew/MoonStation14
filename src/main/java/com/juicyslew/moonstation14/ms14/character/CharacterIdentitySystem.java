package com.juicyslew.moonstation14.ms14.character;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Server-authoritative character enrollment and fail-closed prototype resolution. */
public final class CharacterIdentitySystem {
    private static final Logger LOGGER = LoggerFactory.getLogger(CharacterIdentitySystem.class);
    private static final int WARNING_CACHE_LIMIT = 2048;
    private static final Map<PrototypeCatalog<CharacterData>, Set<ResourceLocation>> WARNED_MISSING =
            new WeakHashMap<>();
    private static int warningCacheSize;

    private CharacterIdentitySystem() { }

    /** Called only by the centralized spawn/load enrollment adapter. */
    public static void enrollSupportedActor(LivingEntity actor, ServerLevel level) {
        ResourceLocation hostType = BuiltInRegistries.ENTITY_TYPE.getKey(actor.getType());
        ModCharacters.characterForHost(level, hostType)
                .ifPresent(characterId -> enroll(actor, level, characterId));
    }

    /** Existing keys, including unknown/dangling keys, are never replaced. */
    public static boolean enroll(Entity entity, ServerLevel level, ResourceLocation requestedId) {
        if (entity.level().isClientSide || level.isClientSide) return false;
        if (entity.hasData(ModDataAttachments.CHARACTER_IDENTITY.get())) {
            CharacterIdentityAttachment existing = entity.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
            if (existing == null || !existing.isBound()) {
                LOGGER.warn("Character identity for {} is present but unbound; refusing enrollment overwrite",
                        entity.getStringUUID());
                return false;
            }
            ResourceLocation present = existing.characterId();
            if (!present.equals(requestedId)) {
                LOGGER.warn("Character identity for {} remains {} (refusing enrollment as {})",
                        entity.getStringUUID(), present, requestedId);
                return false;
            }
            return resolve(level, present).isPresent();
        }

        if (resolve(level, requestedId).isEmpty()) return false;
        CharacterIdentityAttachment binding = new CharacterIdentityAttachment();
        binding.bind(requestedId);
        MS14Provider.update(entity, MS14Bridges.CHARACTER_IDENTITY, binding);
        return true;
    }

    /** Reads do not materialize attachments. Every lookup uses the current server catalog. */
    public static Optional<CharacterData> resolve(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return Optional.empty();
        CharacterIdentityAttachment identity = entity.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        if (identity == null || !identity.isBound()) return Optional.empty();
        return resolve(level, identity.characterId());
    }

    /** Resolve only a currently bound prototype that still owns this actor's host type. */
    public static Optional<CharacterData> resolveForHost(LivingEntity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) return Optional.empty();
        CharacterIdentityAttachment identity = entity.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        if (identity == null || !identity.isBound()) return Optional.empty();
        ResourceLocation host = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        PrototypeCatalog<CharacterData> catalog = ModCharacters.catalog(level);
        return resolveForHost(catalog, identity.characterId(), host);
    }

    /** Pure snapshot seam: stale, absent, or swapped host ownership fails closed. */
    public static Optional<CharacterData> resolveForHost(PrototypeCatalog<CharacterData> catalog,
                                                         ResourceLocation boundId, ResourceLocation hostType) {
        if (catalog == null || boundId == null || hostType == null) return Optional.empty();
        CharacterData data = catalog.get(boundId);
        if (data == null || !data.hostEntityTypes().contains(hostType)) return Optional.empty();
        return ModCharacters.characterForHost(catalog, hostType).filter(boundId::equals).map(ignored -> data);
    }

    public static Optional<CharacterData> resolve(ServerLevel level, ResourceLocation id) {
        PrototypeCatalog<CharacterData> catalog = ModCharacters.catalog(level);
        CharacterData data = catalog.get(id);
        if (data == null) {
            if (shouldWarnUnresolved(catalog, id)) {
                LOGGER.warn("Unresolved character identity '{}' in current catalog; actor remains inert", id);
            }
            return Optional.empty();
        }
        return Optional.of(data);
    }

    static boolean shouldWarnUnresolved(PrototypeCatalog<CharacterData> catalog, ResourceLocation id) {
        synchronized (WARNED_MISSING) {
            Set<ResourceLocation> warned = WARNED_MISSING.get(catalog);
            if (warned != null && warned.contains(id)) return false;

            while (warningCacheSize >= WARNING_CACHE_LIMIT) {
                Iterator<Map.Entry<PrototypeCatalog<CharacterData>, Set<ResourceLocation>>> catalogs =
                        WARNED_MISSING.entrySet().iterator();
                if (!catalogs.hasNext()) {
                    warningCacheSize = 0;
                    break;
                }
                Map.Entry<PrototypeCatalog<CharacterData>, Set<ResourceLocation>> oldest = catalogs.next();
                Iterator<ResourceLocation> ids = oldest.getValue().iterator();
                if (ids.hasNext()) {
                    ids.next();
                    ids.remove();
                    warningCacheSize--;
                }
                if (oldest.getValue().isEmpty()) catalogs.remove();
            }

            WARNED_MISSING.computeIfAbsent(catalog, ignored -> new HashSet<>()).add(id);
            warningCacheSize++;
            return true;
        }
    }
}
