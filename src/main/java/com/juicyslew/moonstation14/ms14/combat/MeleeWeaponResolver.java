package com.juicyslew.moonstation14.ms14.combat;

import com.juicyslew.moonstation14.component.codec.json.CharacterData;
import com.juicyslew.moonstation14.ms14.character.components.MeleeWeaponComponent;
import com.juicyslew.moonstation14.ms14.hands.live.LiveHands;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeCatalog;
import com.juicyslew.moonstation14.ms14.prototype.PrototypeRuntime;
import com.juicyslew.moonstation14.ms14.player_body_control.server.ActiveCharacterPolicy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/** Read-only melee source selection over body-owned LiveHands and resolved prototype data. */
public final class MeleeWeaponResolver {
    public enum Kind { HELD, GLOVES, UNARMED }

    public record Selection(Kind kind, MeleeWeaponProfile profile, UUID body, long handRevision,
                            String activeHand, Optional<String> itemToken) {
        public Selection { itemToken = itemToken == null ? Optional.empty() : itemToken; }
        public MeleeWeaponData weapon() { return profile.weapon(); }
    }

    private MeleeWeaponResolver() { }

    /** Ownership coherence shared by hand and equipment stack admission. */
    public static boolean coherentOwnedStack(boolean stackPresent, Optional<String> token) {
        return stackPresent == (token != null && token.isPresent());
    }

    /** Shared precedence kernel. Occupancy is separate from profile presence: an ordinary held item blocks fallback. */
    public static Optional<Selection> choose(UUID body, long revision, String activeHand,
            boolean heldOccupied, Optional<String> heldToken, Optional<String> glovesToken, Optional<MeleeWeaponProfile> held,
            Optional<MeleeWeaponProfile> gloves, Optional<MeleeWeaponData> unarmed) {
        if (heldOccupied) {
            if (heldToken.isPresent() && held.isPresent() && !held.orElseThrow().weapon().mustBeEquippedToUse())
                return Optional.of(new Selection(Kind.HELD, held.orElseThrow(), body, revision, activeHand, heldToken));
            return Optional.empty();
        }
        if (heldToken.isPresent()) return Optional.empty();
        if (gloves.isPresent() && glovesToken.isPresent()) return Optional.of(new Selection(Kind.GLOVES, gloves.orElseThrow(), body, revision, activeHand, glovesToken));
        return unarmed.map(data -> new Selection(Kind.UNARMED, new MeleeWeaponProfile(data, java.util.Map.of(), Optional.empty()), body, revision, activeHand, Optional.empty()));
    }

    /** Resolves only this body's active hand and equipped gloves; does not initialize or enumerate inventories. */
    public static Optional<Selection> resolve(LivingEntity body) {
        if (body == null || !(body.level() instanceof ServerLevel)) return Optional.empty();
        CharacterData actor = ActiveCharacterPolicy.resolveActor(body).orElse(null);
        if (actor == null) return Optional.empty();
        LiveHands hands = body.getExistingDataOrNull(com.juicyslew.moonstation14.component.ModDataAttachments.LIVE_HANDS.get());
        if (hands == null || !hands.compatible(body)) return Optional.empty();
        String hand = hands.activeHand();
        Optional<String> token = hands.token(hand);
        ItemStack heldStack = hands.stackCopy(hand).orElse(ItemStack.EMPTY);
        if (!coherentOwnedStack(!heldStack.isEmpty(), token)) return Optional.empty();
        Optional<MeleeWeaponProfile> held = heldStack.isEmpty() ? Optional.empty() : profile(heldStack);

        var glovesSlot = LiveHands.equipmentSnapshot(body, hands).stream()
                .flatMap(snapshot -> snapshot.slots().stream())
                .filter(slot -> slot.id().equals(com.juicyslew.moonstation14.ms14.equipment.EquipmentSlots.GLOVES)).findFirst();
        Optional<String> glovesToken = glovesSlot.flatMap(slot -> slot.token());
        Optional<ItemStack> glovesStack = glovesSlot.flatMap(slot -> slot.stack());
        if (!coherentOwnedStack(glovesStack.filter(stack -> !stack.isEmpty()).isPresent(), glovesToken)) return Optional.empty();
        Optional<MeleeWeaponProfile> gloves = glovesStack.filter(stack -> !stack.isEmpty()).flatMap(MeleeWeaponResolver::profile);
        Optional<MeleeWeaponData> unarmed = actor.component(MeleeWeaponComponent.class).map(MeleeWeaponComponent::weapon);
        return choose(body.getUUID(), hands.revision(), hand, !heldStack.isEmpty(), token, glovesToken, held, gloves, unarmed);
    }

    /** Re-resolves from current body state; a selection is valid only while its exact binding remains current. */
    public static boolean revalidate(LivingEntity body, Selection selection) {
        if (body == null || selection == null || !body.getUUID().equals(selection.body())) return false;
        return resolve(body).filter(selection::equals).isPresent();
    }

    /** Exact immutable source-binding comparison usable by focused policy tests and future attack admission. */
    public static boolean sameBinding(Selection expected, Selection current) {
        return expected != null && expected.equals(current);
    }

    private static Optional<MeleeWeaponProfile> profile(ItemStack stack) {
        PrototypeCatalog<MeleeWeaponProfile> catalog = PrototypeRuntime.serverMeleeWeaponProfiles();
        MeleeWeaponProfile profile = catalog.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return Optional.ofNullable(profile);
    }
}
