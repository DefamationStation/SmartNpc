package com.pla.smart_npc.fabric.survival;

import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Bounded personal observations and one durable inventory-target task. No world references. */
public final class ResourceMemory {
    public static final int LIMIT = 64;
    public static final long MAX_AGE = 72_000;
    public record Observation(String dimension, long position, long seen) {}
    public final List<Observation> coal = new ArrayList<>();
    public String dimension = "", status = "idle";
    public long origin;
    public int target;
    public boolean active, returning;
    public boolean automatic;
    public String purpose = "no resource task";
    public long started, nextDecision;
    public int cursor;

    public void observe(String dimension, long position, long now) {
        coal.removeIf(o -> o.dimension.equals(dimension) && o.position == position);
        coal.add(new Observation(dimension, position, now));
        while (coal.size() > LIMIT) coal.removeFirst();
    }

    public void request(String dimension, long origin, int inventoryCount, int additional) {
        if (additional < 1 || additional > 64) throw new IllegalArgumentException("Count must be 1..64");
        this.dimension = dimension;
        this.origin = origin;
        this.target = Math.addExact(inventoryCount, additional);
        active = true;
        returning = false;
        status = "seeking observed coal";
        automatic = false;
        purpose = "requested coal";
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("version", 1);
        tag.putString("dimension", dimension);
        tag.putLong("origin", origin);
        tag.putInt("target", target);
        tag.putBoolean("active", active);
        tag.putBoolean("returning", returning);
        tag.putBoolean("automatic", automatic);
        tag.putString("purpose", purpose);
        tag.putLong("started", started);
        tag.putLong("nextDecision", nextDecision);
        tag.putString("status", status);
        tag.putInt("count", coal.size());
        for (int i = 0; i < coal.size(); i++) {
            var entry = new CompoundTag(); var o = coal.get(i);
            entry.putString("dimension", o.dimension);
            entry.putLong("position", o.position);
            entry.putLong("seen", o.seen);
            tag.put("coal" + i, entry);
        }
        return tag;
    }

    public static ResourceMemory load(CompoundTag tag) {
        var m = new ResourceMemory();
        if (tag.getIntOr("version", 0) != 1) return m;
        m.dimension = tag.getStringOr("dimension", "");
        m.origin = tag.getLongOr("origin", 0);
        m.target = Math.max(0, tag.getIntOr("target", 0));
        m.active = tag.getBooleanOr("active", false) && m.target > 0 && !m.dimension.isEmpty();
        m.returning = tag.getBooleanOr("returning", false);
        m.automatic = tag.getBooleanOr("automatic", false);
        m.purpose = tag.getStringOr("purpose", m.active ? "requested coal" : "no resource task");
        m.started = tag.getLongOr("started", 0);
        m.nextDecision = tag.getLongOr("nextDecision", 0);
        m.status = tag.getStringOr("status", "idle");
        for (int i = 0; i < Math.clamp(tag.getIntOr("count", 0), 0, LIMIT); i++) {
            var e = tag.getCompoundOrEmpty("coal" + i);
            var dimension = e.getStringOr("dimension", "");
            if (!dimension.isEmpty()) m.observe(dimension, e.getLongOr("position", 0), e.getLongOr("seen", 0));
        }
        return m;
    }
}
