package com.juicyslew.moonstation14.ms14.slip;

import com.juicyslew.moonstation14.component.codec.json.ReagentData;
import com.juicyslew.moonstation14.ms14.MS14Bridges;
import com.juicyslew.moonstation14.ms14.MS14Provider;
import com.juicyslew.moonstation14.ms14.character.CharacterIdentitySystem;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.reagent.ModReagents;
import com.juicyslew.moonstation14.ms14.reagent.ReagentAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.Map;

/** Read-only, bounded contact query for the source puddle friction effect. */
public final class SlidingFrictionSystem {
    private static final double NEUTRAL = 1d;

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
        // Contact is a read-only client projection or server-authoritative actor query.
        if ((level.isClientSide ? CharacterIdentitySystem.projectForActor(entity)
                : CharacterIdentitySystem.resolveForActor(entity)).isEmpty()) return ContactFactor.NONE;

        try {
            PrototypeCatalog<ReagentData> reagents = ModReagents.catalog(level);
            if (reagents == null || reagents.size() == 0) return ContactFactor.NONE;

            SharedPuddleContactQuery.Result query = SharedPuddleContactQuery.queryFeetBand(entity);
            if (!query.available()) return ContactFactor.NONE;
            double total = 0d;
            int sources = 0;
            for (SharedPuddleContactQuery.Contact contact : query.contacts()) {
                ReagentAttachment contents = MS14Provider.getDetached(contact.source(), MS14Bridges.REAGENT);
                Map<ResourceKey<ReagentData>, Long> snapshot = contents.snapshotUnits();
                if (snapshot.isEmpty()) continue;
                SlipperySolution.Outcome outcome = SlipperySolution.calculate(snapshot, reagents);
                if (outcome.inert()) continue;
                total += outcome.friction();
                sources++;
            }
            return sources == 0 ? ContactFactor.NONE : new ContactFactor(true, total / sources);
        } catch (RuntimeException exception) {
            // Unknown prototypes, unavailable side catalogs, and invalid snapshots fail neutral.
            return ContactFactor.NONE;
        }
    }

    private record ContactFactor(boolean present, double friction) {
        private static final ContactFactor NONE = new ContactFactor(false, NEUTRAL);
    }
}
