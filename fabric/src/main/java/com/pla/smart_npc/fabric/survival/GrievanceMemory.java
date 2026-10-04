package com.pla.smart_npc.fabric.survival;

import net.minecraft.nbt.CompoundTag;
import java.util.*;

/** Bounded, expiring evidence for defensive combat; identity is the offender's UUID. */
public final class GrievanceMemory {
    public static final int LIMIT = 16;
    public static final long DEFENCE_TICKS = 20 * 60;
    public enum Cause { ASSAULT, THEFT, VANDALISM, ALLY_DEFENCE }
    public record Grievance(UUID offender, Cause cause, long expires) {}
    private final List<Grievance> entries = new ArrayList<>();

    public void record(UUID offender, Cause cause, long now) {
        entries.removeIf(g -> g.offender.equals(offender) || g.expires <= now);
        entries.add(new Grievance(offender, cause, now + DEFENCE_TICKS));
        while (entries.size() > LIMIT) entries.removeFirst();
    }
    public Optional<Cause> cause(UUID offender, long now) {
        return entries.stream().filter(g -> g.offender.equals(offender) && g.expires > now)
            .map(Grievance::cause).findFirst();
    }
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("version", 1); tag.putInt("count", entries.size());
        for (int i = 0; i < entries.size(); i++) {
            var e = entries.get(i); var value = new CompoundTag();
            value.putString("offender", e.offender.toString()); value.putString("cause", e.cause.name());
            value.putLong("expires", e.expires); tag.put("entry" + i, value);
        }
        return tag;
    }
    public static GrievanceMemory load(CompoundTag tag) {
        var memory = new GrievanceMemory();
        if (tag.getIntOr("version", 0) != 1) return memory;
        for (int i = 0; i < Math.clamp(tag.getIntOr("count", 0), 0, LIMIT); i++) {
            var e = tag.getCompoundOrEmpty("entry" + i);
            try {
                memory.entries.add(new Grievance(UUID.fromString(e.getStringOr("offender", "")),
                    Cause.valueOf(e.getStringOr("cause", "")), e.getLongOr("expires", 0)));
            } catch (IllegalArgumentException ignored) { /* Invalid saved evidence grants no permission. */ }
        }
        return memory;
    }
}
