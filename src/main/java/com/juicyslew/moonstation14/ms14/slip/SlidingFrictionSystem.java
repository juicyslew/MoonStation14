package com.juicyslew.moonstation14.ms14.slip;

import com.juicyslew.moonstation14.block.ModBlocks;
import com.juicyslew.moonstation14.block.block_entity.PuddleBlockEntity;
import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentityAttachment;
import com.juicyslew.moonstation14.ms14.character.ModCharacters;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Map;

/** Read-only, bounded contact query for the source puddle friction effect. */
public final class SlidingFrictionSystem {
    private static final double NEUTRAL = 1d;
    private static final double MAX_SCAN_WIDTH = 16d;

    private SlidingFrictionSystem() { }

    public static double frictionFactor(LivingEntity entity) {
        if (entity == null || !SlipSystem.isSliding(entity)) return NEUTRAL;
        ContactFactor contact = scanContacts(entity);
        return contact.present ? contact.friction : NEUTRAL;
    }

    /** Read-only query for currently qualifying slippery feet contact. */
    public static boolean hasQualifyingContact(LivingEntity entity) {
        return entity != null && scanContacts(entity).present;
    }

    private static ContactFactor scanContacts(LivingEntity entity) {
        Level level = entity.level();
        CharacterIdentityAttachment identity = entity.getExistingDataOrNull(ModDataAttachments.CHARACTER_IDENTITY.get());
        if (identity == null || !identity.isBound()) return ContactFactor.NONE;

        try {
            PrototypeCatalog<CharacterData> characters = ModCharacters.catalog(level);
            PrototypeCatalog<ReagentData> reagents = ModReagents.catalog(level);
            if (characters == null || characters.size() == 0 || reagents == null || reagents.size() == 0
                    || characters.get(identity.characterId()) == null) return ContactFactor.NONE;

            AABB bounds = entity.getBoundingBox();
            // Query only a thin feet band; a capped horizontal extent keeps the scan bounded.
            AABB feet = new AABB(bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.minY + Math.min(.25d, bounds.getYsize()), bounds.maxZ);
            if (!Double.isFinite(feet.getXsize()) || !Double.isFinite(feet.getZsize())
                    || feet.getXsize() > MAX_SCAN_WIDTH || feet.getZsize() > MAX_SCAN_WIDTH) return ContactFactor.NONE;
            int minX = floor(feet.minX), maxX = floor(Math.nextDown(feet.maxX));
            int minY = floor(feet.minY), maxY = floor(Math.nextDown(feet.maxY));
            int minZ = floor(feet.minZ), maxZ = floor(Math.nextDown(feet.maxZ));
            double total = 0d;
            int sources = 0;
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        cursor.set(x, y, z);
                        if (!level.hasChunkAt(cursor) || !level.getBlockState(cursor).is(ModBlocks.PUDDLE.get())
                                || !(level.getBlockEntity(cursor) instanceof PuddleBlockEntity puddle)) continue;
                        AABB shape = level.getBlockState(cursor).getShape(level, cursor, CollisionContext.empty())
                                .bounds().move(cursor);
                        if (!feet.intersects(shape)) continue;
                        ReagentAttachment contents = MS14Provider.getDetached(puddle, MS14Bridges.REAGENT);
                        Map<ResourceKey<ReagentData>, Long> snapshot = contents.snapshotUnits();
                        if (snapshot.isEmpty()) continue;
                        SlipperySolution.Outcome outcome = SlipperySolution.calculate(snapshot, reagents);
                        if (outcome.inert()) continue;
                        total += outcome.friction();
                        sources++;
                    }
                }
            }
            return sources == 0 ? ContactFactor.NONE : new ContactFactor(true, total / sources);
        } catch (RuntimeException exception) {
            // Unknown prototypes, unavailable side catalogs, and invalid snapshots fail neutral.
            return ContactFactor.NONE;
        }
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private record ContactFactor(boolean present, double friction) {
        private static final ContactFactor NONE = new ContactFactor(false, NEUTRAL);
    }
}
