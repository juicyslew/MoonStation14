package com.juicyslew.moonstation14.ms14.disposal.client;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Mapping from the imported disposal RSI frames to the supported local tube prototype IDs. */
public final class DisposalTubePresentation {
    private DisposalTubePresentation() { }
    public static ResourceLocation texture(String prototype) {
        if (prototype == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(prototype.indexOf(':') < 0 ? "moonstation14:" + prototype : prototype);
        if (id == null || !id.getNamespace().equals("moonstation14")) return null;
        String path = id.getPath();
        if (path.startsWith("disposal_entity/")) path = path.substring("disposal_entity/".length());
        String name = switch (path) {
            case "disposal_pipe" -> "pipe-s";
            case "disposal_bend" -> "pipe-b";
            case "disposal_trunk" -> "pipe-d";
            case "disposal_junction" -> "pipe-j1";
            case "disposal_junction_flipped" -> "pipe-j1f";
            case "disposal_y_junction" -> "pipe-y";
            case "disposal_x_junction" -> "pipe-x";
            case "disposal_tagger" -> "pipe-tagger";
            case "disposal_router" -> "signal-router";
            case "disposal_router_flipped" -> "signal-router-flipped";
            default -> null;
        };
        return name == null ? null : ResourceLocation.fromNamespaceAndPath("moonstation14", "textures/block/disposal/" + name + "_frame");
    }
    /** RSI frame sequence is north, east, south, west; orientation denotes the routing forward side. */
    public static int frame(Direction direction) {
        return switch (direction) { case NORTH -> 0; case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> throw new IllegalArgumentException("horizontal direction required"); };
    }
    public static int frame(com.juicyslew.moonstation14.ms14.disposal.domain.DisposalOrientation orientation) {
        return orientation.forward().getAxis() == Direction.Axis.Y ? 0 : frame(orientation.forward());
    }
}

