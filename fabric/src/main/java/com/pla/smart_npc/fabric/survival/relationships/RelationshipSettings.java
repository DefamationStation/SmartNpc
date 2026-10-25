package com.pla.smart_npc.fabric.survival.relationships;
import com.pla.smart_npc.fabric.ConfigSpec;
public final class RelationshipSettings {
    public static final ConfigSpec SPEC;
    public static final ConfigSpec.BooleanValue ENABLED;
    static{var b=new ConfigSpec.Builder();ENABLED=b.comment("Record bounded UUID-attributed actual cooperation/offense history and offer advisory status only. Does not grant trust ranks, teams, aggression or property rights.").define("relationship_evidence_enabled",true);SPEC=b.build();}
    private RelationshipSettings(){}
}