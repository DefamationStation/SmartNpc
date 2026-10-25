package com.pla.smart_npc.fabric.survival.memory;

import com.pla.smart_npc.fabric.ConfigSpec;

/** Small server controls; disabling observation/influence never deletes saved evidence. */
public final class MemorySettings {
    public static final ConfigSpec SPEC;
    public static final ConfigSpec.BooleanValue MEMORY_ENABLED,PERSONALITY_ENABLED,OBSERVE_RESOURCES,CONTEXTUAL_DIALOGUE;
    public static final ConfigSpec.IntValue EXTRA_DEPARTURE_FOOD,SOCIABILITY,CAUTION,GENEROSITY,CURIOSITY,DILIGENCE;
    static {
        var b=new ConfigSpec.Builder();
        MEMORY_ENABLED=b.comment("Record bounded personal observations. Disabling preserves existing data.").define("memory_enabled",true);
        PERSONALITY_ENABLED=b.comment("Allow small personal decision biases; interests and safety rules retain precedence.").define("personality_enabled",true);
        OBSERVE_RESOURCES=b.comment("Scan at most four new nearby positions plus one existing fact per admitted second, with loaded-chunk and line-of-sight checks.").define("observe_visible_resources",true);
        CONTEXTUAL_DIALOGUE=b.comment("Allow local contextual replies when crouch-right-clicking an NPC with an empty main hand. Also respects turnOnNpcChat.").define("contextual_dialogue",true);
        EXTRA_DEPARTURE_FOOD=b.comment("Maximum personality-based extra food over the existing four-item departure floor.").defineInRange("maximum_extra_departure_food",2,0,4);
        b.push("trait_overrides");
        SOCIABILITY=b.comment("-1 keeps the stable UUID tendency and current-interest bias; otherwise use 0..100.").defineInRange("sociability",-1,-1,100);
        CAUTION=b.defineInRange("caution",-1,-1,100);GENEROSITY=b.defineInRange("generosity",-1,-1,100);
        CURIOSITY=b.defineInRange("curiosity",-1,-1,100);DILIGENCE=b.defineInRange("diligence",-1,-1,100);
        b.pop();SPEC=b.build();
    }
    private MemorySettings(){ }
    public static NpcPersonality.Overrides overrides(){return new NpcPersonality.Overrides(SOCIABILITY.get(),CAUTION.get(),GENEROSITY.get(),CURIOSITY.get(),DILIGENCE.get());}
}
