package com.juicyslew.moonstation14.ms14.hands;

import com.juicyslew.moonstation14.component.ModDataAttachments;
import com.juicyslew.moonstation14.component.ModDataComponents;
import com.juicyslew.moonstation14.entities.MouseMob;
import com.juicyslew.moonstation14.ms14.character.components.MobStateComponent;
import com.juicyslew.moonstation14.ms14.character.components.PortableBodyComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Captures only bounded source identity and visual state; never snapshots mind or physiology. */
public final class PortableBodyAppearanceResolver {
    private PortableBodyAppearanceResolver() { }

    public static PortableBodyAppearance capture(LivingEntity body) {
        if (body == null || !(body.level() instanceof ServerLevel level) || body.isRemoved()
                || !body.isAddedToLevel() || level.getEntity(body.getUUID()) != body) return null;
        var capability = com.juicyslew.moonstation14.ms14.hands.HandCapability.resolveCharacter(body)
                .flatMap(value -> value.component(PortableBodyComponent.class)).orElse(null);
        if (capability == null) return null;
        var coat = body instanceof MouseMob mouse ? switch (mouse.appearance()) {
            case WHITE -> PortableBodyAppearance.Coat.WHITE;
            case BROWN -> PortableBodyAppearance.Coat.BROWN;
            case GRAY -> PortableBodyAppearance.Coat.GRAY;
        } : null;
        if (coat == null) return null; // no asset adapter exists for other profile models yet
        var state = PortableBodyAppearance.State.ALIVE;
        var mob = body.getExistingDataOrNull(ModDataAttachments.MOB_STATE.get());
        if (mob != null) state = switch (mob.data().state()) {
            case CRITICAL -> PortableBodyAppearance.State.CRITICAL;
            case DEAD -> PortableBodyAppearance.State.DEAD;
            default -> PortableBodyAppearance.State.ALIVE;
        };
        String name = com.juicyslew.moonstation14.ms14.character.CharacterPublicName.component(body).getString();
        if (name.length() > 128) name = name.substring(0, 128);
        try { return new PortableBodyAppearance(capability.profileId(), name, coat, state); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    public static ItemStack refresh(ItemStack source, LivingEntity body) {
        if (source == null || source.isEmpty() || !com.juicyslew.moonstation14.ms14.hands.PortableBodyKeys.isKeyItem(source))
            return source == null ? ItemStack.EMPTY : source.copy();
        var appearance = capture(body);
        ItemStack result = source.copy();
        if (appearance != null) {
            result.set(ModDataComponents.PORTABLE_BODY_APPEARANCE.get(), appearance);
            result.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    com.juicyslew.moonstation14.ms14.character.CharacterPublicName.component(body));
        }
        return result;
    }

    public static PortableBodyAppearance fromStack(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : stack.get(ModDataComponents.PORTABLE_BODY_APPEARANCE.get());
    }
}
