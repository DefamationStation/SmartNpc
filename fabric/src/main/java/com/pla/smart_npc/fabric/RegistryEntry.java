package com.pla.smart_npc.fabric;

import java.util.function.Supplier;

/** A registered value, keeping the common code's lazy-reference API. */
public class RegistryEntry<R, T extends R> implements Supplier<T> {
    private final T value;
    public RegistryEntry(T value) { this.value = value; }
    @Override public T get() { return value; }
    public T value() { return value; }
}
