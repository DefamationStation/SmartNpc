package com.pla.smart_npc.fabric.survival.memory;

import java.util.UUID;

/** Stable personal tendencies, separate from interests, skills and combat permission. */
public record NpcPersonality(int sociability, int caution, int generosity, int curiosity, int diligence) {
    public NpcPersonality {
        sociability=clamp(sociability);caution=clamp(caution);generosity=clamp(generosity);
        curiosity=clamp(curiosity);diligence=clamp(diligence);
    }
    public record Interests(boolean teamUp, boolean cautious, boolean coward, boolean exploring, boolean building) { }
    public record Overrides(int sociability, int caution, int generosity, int curiosity, int diligence) {
        public static final Overrides SEEDED=new Overrides(-1,-1,-1,-1,-1);
    }
    public static NpcPersonality seeded(UUID id) {
        long seed=mix(id.getMostSignificantBits()^Long.rotateLeft(id.getLeastSignificantBits(),23));
        return new NpcPersonality(trait(seed,1),trait(seed,2),trait(seed,3),trait(seed,4),trait(seed,5));
    }
    public NpcPersonality effective(Interests interests, Overrides overrides) {
        return new NpcPersonality(value(sociability+(interests.teamUp?15:0),overrides.sociability),
                value(caution+(interests.cautious?20:0)+(interests.coward?15:0),overrides.caution),
                value(generosity+(interests.teamUp?10:0),overrides.generosity),
                value(curiosity+(interests.exploring?15:0),overrides.curiosity),
                value(diligence+(interests.building?15:0),overrides.diligence));
    }
    /** Never weakens the existing four-item floor or changes inventory. */
    public int departureFoodReserve(int maximumExtra) {
        int extra=caution>=80?2:caution>=60?1:0;
        return 4+Math.min(Math.clamp(maximumExtra,0,4),extra);
    }
    private static int value(int seeded,int override){return override<0?seeded:override;}
    private static int clamp(int value){return Math.clamp(value,0,100);}
    private static int trait(long seed,int salt){return 25+(int)Long.remainderUnsigned(mix(seed+salt*0x9e3779b97f4a7c15L),51);}
    private static long mix(long value){value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;value=(value^(value>>>27))*0x94d049bb133111ebL;return value^(value>>>31);}
}
