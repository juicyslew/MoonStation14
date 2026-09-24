package com.juicyslew.moonstation14.ms14.prototype;

/** Thrown when raw prototype metadata or its dependency graph is invalid. */
public class PrototypeResolutionException extends IllegalArgumentException {
    public PrototypeResolutionException(String message) {
        super(message);
    }

    public PrototypeResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
