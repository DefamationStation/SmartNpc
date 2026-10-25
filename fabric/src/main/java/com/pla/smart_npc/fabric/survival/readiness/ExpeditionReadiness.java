package com.pla.smart_npc.fabric.survival.readiness;

import java.util.*;

/** Read-only material advice from observed inventory/ownership facts. This never
 * grants equipment, promises a route, or gates existing native behaviour. */
public final class ExpeditionReadiness {
    private ExpeditionReadiness() { }
    public enum Tool { PICKAXE, AXE, SHOVEL }
    /** Gold has wood's mining capability, irrespective of mining speed. */
    public enum Tier {
        WOOD(0), GOLD(0), STONE(1), COPPER(1), IRON(2), DIAMOND(3), NETHERITE(4);
        private final int capability;
        Tier(int capability){this.capability=capability;}
        public boolean supports(Tier required){return capability>=required.capability;}
    }
    public enum Home { NONE, OTHER_DIMENSION, SAME_DIMENSION }
    public enum Code { NO_OWNED_HOME, HOME_IN_OTHER_DIMENSION, FOOD_RESERVE, TOOL_MISSING, TOOL_TIER, TOOL_DURABILITY, BOAT_MISSING }
    public record CarriedTool(Tool tool,Tier tier,int remainingUses){
        public CarriedTool {Objects.requireNonNull(tool);Objects.requireNonNull(tier);remainingUses=Math.max(0,remainingUses);}
    }
    /** Only carried, usable tools belong here; chest contents, dropped items,
     * recipes known and historical earned stages are not carried equipment. */
    public record Facts(List<CarriedTool> carriedTools,int cookedFood,Home ownedHome,int carriedBoats,boolean aboardOwnedBoat){
        public Facts {carriedTools=List.copyOf(carriedTools);Objects.requireNonNull(ownedHome);cookedFood=Math.max(0,cookedFood);carriedBoats=Math.max(0,carriedBoats);}
    }
    public record Requirement(Tool tool,Tier tier,int remainingUses){
        public Requirement {Objects.requireNonNull(tool);Objects.requireNonNull(tier);if(remainingUses<1)throw new IllegalArgumentException("Usable tool reserve required");}
    }
    public record Plan(List<Requirement> tools,int cookedFoodReserve,boolean requireOwnedHome,boolean requireBoat){
        public Plan {tools=List.copyOf(tools);if(cookedFoodReserve<0)throw new IllegalArgumentException("Negative food reserve");}
        public static Plan expedition(int foodReserve,boolean waterCrossing){
            return new Plan(List.of(new Requirement(Tool.PICKAXE,Tier.STONE,16),new Requirement(Tool.AXE,Tier.WOOD,16)),foodReserve,true,waterCrossing);
        }
    }
    public record Missing(Code code,Tool tool,Tier requiredTier,int have,int need){ }
    public record Assessment(List<Missing> missing){
        public Assessment {missing=List.copyOf(missing);}
        public boolean ready(){return missing.isEmpty();}
    }
    public static Assessment assess(Facts facts,Plan plan){
        Objects.requireNonNull(facts);Objects.requireNonNull(plan);var missing=new ArrayList<Missing>();
        if(plan.requireOwnedHome()){
            if(facts.ownedHome()==Home.NONE)missing.add(new Missing(Code.NO_OWNED_HOME,null,null,0,1));
            else if(facts.ownedHome()==Home.OTHER_DIMENSION)missing.add(new Missing(Code.HOME_IN_OTHER_DIMENSION,null,null,0,1));
        }
        if(facts.cookedFood()<plan.cookedFoodReserve())missing.add(new Missing(Code.FOOD_RESERVE,null,null,facts.cookedFood(),plan.cookedFoodReserve()));
        for(var requirement:plan.tools()){
            var matching=facts.carriedTools().stream().filter(t->t.tool()==requirement.tool()).toList();
            var capable=matching.stream().filter(t->t.tier().supports(requirement.tier())).toList();
            if(matching.isEmpty())missing.add(new Missing(Code.TOOL_MISSING,requirement.tool(),requirement.tier(),0,1));
            else if(capable.isEmpty())missing.add(new Missing(Code.TOOL_TIER,requirement.tool(),requirement.tier(),0,1));
            else {
                // Multiple almost-broken tools cannot masquerade as one sound tool.
                int best=capable.stream().mapToInt(CarriedTool::remainingUses).max().orElse(0);
                if(best<requirement.remainingUses())missing.add(new Missing(Code.TOOL_DURABILITY,requirement.tool(),requirement.tier(),best,requirement.remainingUses()));
            }
        }
        if(plan.requireBoat()&&facts.carriedBoats()==0&&!facts.aboardOwnedBoat())missing.add(new Missing(Code.BOAT_MISSING,null,null,0,1));
        return new Assessment(missing);
    }
}

