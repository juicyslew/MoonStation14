package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.component.codec.json.EffectData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PopupMessageEffectTest {
    @Test
    void selectsExactlyOneUniformBoundedEntryFromInjectedRandom() {
        List<String> messages = List.of("first", "second", "third");
        RandomSource expectedRandom = RandomSource.create(12345L);
        int expectedIndex = expectedRandom.nextInt(messages.size());

        assertEquals(messages.get(expectedIndex),
                PopupMessageEffect.selectMessage(messages, RandomSource.create(12345L)));
        assertThrows(IllegalArgumentException.class,
                () -> PopupMessageEffect.selectMessage(List.of(), RandomSource.create()));
    }

    @Test
    void visualStylesMatchMinecraftTextStyling() {
        assertStyle(EffectData.PopupVisualType.Small, ChatFormatting.GRAY, false);
        assertStyle(EffectData.PopupVisualType.SmallCaution, ChatFormatting.YELLOW, false);
        assertStyle(EffectData.PopupVisualType.Medium, ChatFormatting.WHITE, false);
        assertStyle(EffectData.PopupVisualType.MediumCaution, ChatFormatting.GOLD, false);
        assertStyle(EffectData.PopupVisualType.Large, ChatFormatting.AQUA, true);
        assertStyle(EffectData.PopupVisualType.LargeCaution, ChatFormatting.RED, true);
    }

    @Test
    void routesUseDirectTargetOrTrackerBroadcastWithoutSphericalApproximation() {
        assertEquals(PopupMessageEffect.DeliveryRoute.DIRECT_TARGET,
                PopupMessageEffect.route(EffectData.PopupRecipients.Local));
        assertEquals(PopupMessageEffect.DeliveryRoute.TRACKER_BROADCAST,
                PopupMessageEffect.route(EffectData.PopupRecipients.Pvs));

        assertEquals(PopupMessageEffect.OriginKind.ENTITY,
                PopupMessageEffect.originKind(EffectData.PopupMethod.PopupEntity));
        assertEquals(PopupMessageEffect.OriginKind.COORDINATES,
                PopupMessageEffect.originKind(EffectData.PopupMethod.PopupCoordinates));
        assertEquals(PopupMessageEffect.DeliveryRoute.DIRECT_TARGET,
                PopupMessageEffect.route(EffectData.PopupRecipients.Local));
        assertEquals(PopupMessageEffect.DeliveryRoute.TRACKER_BROADCAST,
                PopupMessageEffect.route(EffectData.PopupRecipients.Pvs));
    }

    @Test
    void messagePreservesSelectedKeyDisplayNameArgumentAndStyle() {
        Component displayName = Component.literal("Affected target");
        Component message = PopupMessageEffect.message("popup-key", displayName,
                EffectData.PopupVisualType.LargeCaution);
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, message.getContents());

        assertEquals("popup-key", contents.getKey());
        assertEquals(displayName, contents.getArgs()[0]);
        assertStyle(EffectData.PopupVisualType.LargeCaution, ChatFormatting.RED, true, message.getStyle());
    }

    @Test
    void restorationCoordinateNoticeUsesFixedSourceKeyAndSmallGrayPresentationWithoutSelection() {
        Component message = PopupMessageEffect.message("botany-plant-seedsrestored",
                Component.empty(), EffectData.PopupVisualType.Small);
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, message.getContents());
        assertEquals("botany-plant-seedsrestored", contents.getKey());
        assertEquals(1, contents.getArgs().length);
        assertStyle(EffectData.PopupVisualType.Small, ChatFormatting.GRAY, false, message.getStyle());
    }

    @Test
    void coordinateEntryUsesOnlyCoordinateBroadcastWithExactLevelOriginKeyAndStyle() throws Exception {
        ServerLevel level = (ServerLevel) unsafe().allocateInstance(ServerLevel.class);
        Vec3 origin = new Vec3(4.25, 70, -9.5);
        var capture = new CapturingDelivery();

        PopupMessageEffect.showAt(level, origin, "botany-plant-seedsrestored",
                EffectData.PopupVisualType.Small, capture);

        assertEquals(1, capture.coordinates);
        assertEquals(0, capture.direct);
        assertEquals(0, capture.entityBroadcast);
        assertEquals(level, capture.level);
        assertEquals(origin, capture.origin);
        var contents = assertInstanceOf(TranslatableContents.class, capture.message.getContents());
        assertEquals("botany-plant-seedsrestored", contents.getKey());
        assertEquals(0, contents.getArgs().length);
        assertStyle(EffectData.PopupVisualType.Small, ChatFormatting.GRAY, false, capture.message.getStyle());
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }

    private static final class CapturingDelivery implements PopupMessageEffect.PopupMessageDelivery {
        private int direct;
        private int entityBroadcast;
        private int coordinates;
        private ServerLevel level;
        private Vec3 origin;
        private Component message;

        @Override public void direct(ServerPlayer player, Component message) { direct++; }
        @Override public void broadcast(ServerLevel level, Entity target, Component message) { entityBroadcast++; }
        @Override public void broadcastCoordinates(ServerLevel level, Vec3 origin, Component message) {
            coordinates++;
            this.level = level;
            this.origin = origin;
            this.message = message;
        }
    }

    private static void assertStyle(EffectData.PopupVisualType visual,
                                    ChatFormatting color, boolean bold) {
        assertStyle(visual, color, bold, PopupMessageEffect.style(visual));
    }

    private static void assertStyle(EffectData.PopupVisualType visual,
                                    ChatFormatting color, boolean bold, Style style) {
        assertEquals(color.getColor(), style.getColor().getValue());
        assertEquals(bold, style.isBold());
    }
}
