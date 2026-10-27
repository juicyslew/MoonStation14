package com.juicyslew.moonstation14.ms14.effect;

import com.juicyslew.moonstation14.component.codec.json.EffectData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Objects;

/** Selection, audience, and presentation decisions for PopupMessage. */
public final class PopupMessageEffect {
    private static final PopupMessageDelivery SERVER_ACTIONBAR = new PopupMessageDelivery() {
        @Override
        public void direct(ServerPlayer player, Component message) {
            player.connection.send(new ClientboundSetActionBarTextPacket(message));
        }

        @Override
        public void broadcast(ServerLevel level, Entity target, Component message) {
            level.getChunkSource().broadcastAndSend(target,
                    new ClientboundSetActionBarTextPacket(message));
        }

        @Override
        public void broadcastCoordinates(ServerLevel level, Vec3 origin, Component message) {
            var viewers = level.getChunkSource().chunkMap.getPlayers(
                    new ChunkPos(BlockPos.containing(origin)), false);
            for (ServerPlayer player : viewers) {
                try {
                    direct(player, message);
                } catch (RuntimeException ignored) {
                    // Best-effort presentation; never undo a committed gameplay effect.
                }
            }
        }
    };

    private PopupMessageEffect() {
    }

    /** Best-effort coordinate popup via the established actionbar adapter, limited to chunk viewers. */
    public static void showAt(ServerLevel level, Vec3 origin, String translationKey,
                              EffectData.PopupVisualType visualType) {
        showAt(level, origin, translationKey, visualType, SERVER_ACTIONBAR);
    }

    /** Best-effort private actionbar feedback using the standard popup presentation style. */
    public static void showToPlayer(ServerPlayer player, String translationKey,
                                    EffectData.PopupVisualType visualType) {
        Objects.requireNonNull(player, "player");
        Component message = Component.translatable(Objects.requireNonNull(translationKey, "translationKey"))
                .withStyle(style(visualType));
        try { SERVER_ACTIONBAR.direct(player, message); }
        catch (RuntimeException ignored) { /* feedback must not disrupt the authoritative speech gate */ }
    }

    static void showAt(ServerLevel level, Vec3 origin, String translationKey,
                       EffectData.PopupVisualType visualType, PopupMessageDelivery delivery) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(origin, "origin");
        Component message = Component.translatable(Objects.requireNonNull(translationKey, "translationKey"))
                .withStyle(style(visualType));
        deliverCoordinates(level, origin, message, delivery);
    }

    static void deliverCoordinates(ServerLevel level, Vec3 origin, Component message,
                                   PopupMessageDelivery delivery) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(delivery, "delivery").broadcastCoordinates(level, origin, message);
    }

    static EffectResult apply(EffectData.PopupMessage effect, EffectContext context) {
        return apply(effect, context, SERVER_ACTIONBAR);
    }

    static EffectResult apply(EffectData.PopupMessage effect, EffectContext context,
                              PopupMessageDelivery delivery) {
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(delivery, "delivery");

        String key = selectMessage(effect.messages(), context.random());
        Component message = message(key, context.entity(), effect.visualType());

        DeliveryPlan plan = plan(effect, context.level(), context.entity());
        if (plan.route() == DeliveryRoute.DIRECT_TARGET) {
            if (context.entity() instanceof ServerPlayer player) {
                delivery.direct(player, message);
            }
        } else if (plan.originKind() == OriginKind.COORDINATES) {
            deliverCoordinates(context.level(), plan.origin(), message, delivery);
        } else {
            delivery.broadcast(context.level(), context.entity(), message);
        }
        return EffectResult.APPLIED;
    }

    static String selectMessage(List<String> messages, RandomSource random) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(random, "random");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        return messages.get(random.nextInt(messages.size()));
    }

    static Style style(EffectData.PopupVisualType visualType) {
        return switch (Objects.requireNonNull(visualType, "visualType")) {
            case Small -> Style.EMPTY.withColor(ChatFormatting.GRAY);
            case SmallCaution -> Style.EMPTY.withColor(ChatFormatting.YELLOW);
            case Medium -> Style.EMPTY.withColor(ChatFormatting.WHITE);
            case MediumCaution -> Style.EMPTY.withColor(ChatFormatting.GOLD);
            case Large -> Style.EMPTY.withColor(ChatFormatting.AQUA).withBold(true);
            case LargeCaution -> Style.EMPTY.withColor(ChatFormatting.RED).withBold(true);
        };
    }

    static Component message(String key, Entity entity, EffectData.PopupVisualType visualType) {
        return message(key, Objects.requireNonNull(entity, "entity").getDisplayName(), visualType);
    }

    static Component message(String key, Component entityDisplayName,
                             EffectData.PopupVisualType visualType) {
        return Component.translatable(Objects.requireNonNull(key, "key"),
                        Objects.requireNonNull(entityDisplayName, "entityDisplayName"))
                .withStyle(style(visualType));
    }

    static DeliveryPlan plan(EffectData.PopupMessage effect, ServerLevel level, Entity target) {
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(target, "target");
        Vec3 origin = target.position();
        OriginKind originKind = originKind(effect.method());

        return new DeliveryPlan(effect.subType(), effect.method(), originKind, origin,
                route(effect.subType()));
    }

    static OriginKind originKind(EffectData.PopupMethod method) {
        return Objects.requireNonNull(method, "method") == EffectData.PopupMethod.PopupCoordinates
                ? OriginKind.COORDINATES : OriginKind.ENTITY;
    }

    static DeliveryRoute route(EffectData.PopupRecipients recipients) {
        return Objects.requireNonNull(recipients, "recipients") == EffectData.PopupRecipients.Local
                ? DeliveryRoute.DIRECT_TARGET : DeliveryRoute.TRACKER_BROADCAST;
    }

    enum OriginKind {
        ENTITY,
        COORDINATES
    }

    enum DeliveryRoute {
        DIRECT_TARGET,
        TRACKER_BROADCAST
    }

    record DeliveryPlan(EffectData.PopupRecipients recipientsType, EffectData.PopupMethod method,
                        OriginKind originKind, Vec3 origin, DeliveryRoute route) {
        DeliveryPlan {
            Objects.requireNonNull(recipientsType, "recipientsType");
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(originKind, "originKind");
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(route, "route");
        }
    }

    interface PopupMessageDelivery {
        void direct(ServerPlayer player, Component message);

        default void broadcast(ServerLevel level, Entity target, Component message) {
            throw new UnsupportedOperationException("broadcast delivery is not configured");
        }

        void broadcastCoordinates(ServerLevel level, Vec3 origin, Component message);
    }
}

