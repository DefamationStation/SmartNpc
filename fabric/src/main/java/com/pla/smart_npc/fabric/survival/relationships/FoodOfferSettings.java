package com.pla.smart_npc.fabric.survival.relationships;
import com.pla.smart_npc.fabric.ConfigSpec;
/** Reversible explicit food offers; existing help history is preserved when disabled. */
public final class FoodOfferSettings {
    public static final ConfigSpec SPEC;
    public static final ConfigSpec.BooleanValue ENABLED;
    public static final ConfigSpec.IntValue PAIR_COOLDOWN,NPC_COOLDOWN;
    static {
        var b=new ConfigSpec.Builder();
        ENABLED=b.comment("Allow crouch-right-click with one held cooked food to offer it to a safe nearby NPC below its departure reserve. No teams, trust rank or access rights are granted.").define("explicit_food_offers",true);
        PAIR_COOLDOWN=b.comment("Minimum ticks between accepted offers from the same player to one NPC.").defineInRange("player_npc_cooldown_ticks",200,20,1200);
        NPC_COOLDOWN=b.comment("Minimum ticks between accepted offers to one NPC from any players.").defineInRange("npc_cooldown_ticks",20,1,200);
        SPEC=b.build();
    }
    private FoodOfferSettings(){}
}