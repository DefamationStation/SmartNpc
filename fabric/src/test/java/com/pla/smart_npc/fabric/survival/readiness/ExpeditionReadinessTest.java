package com.pla.smart_npc.fabric.survival.readiness;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.pla.smart_npc.fabric.survival.readiness.ExpeditionReadiness.*;
class ExpeditionReadinessTest {
    private final Plan mining=new Plan(List.of(new Requirement(Tool.PICKAXE,Tier.IRON,16)),4,true,false);
    private Facts facts(Tier tier,int uses){return new Facts(List.of(new CarriedTool(Tool.PICKAXE,tier,uses)),4,Home.SAME_DIMENSION,0,false);}
    @Test void sufficientCarriedMaterialsAreReadyWithoutChangingInputs(){
        var f=facts(Tier.IRON,16);assertTrue(assess(f,mining).ready());assertEquals(16,f.carriedTools().getFirst().remainingUses());assertEquals(4,f.cookedFood());
    }
    @Test void snapshotCopperHasStoneHarvestCapability(){assertTrue(Tier.COPPER.supports(Tier.STONE));assertFalse(Tier.COPPER.supports(Tier.IRON));}
    @Test void goldSpeedDoesNotCountAsIronMiningCapability(){assertEquals(Code.TOOL_TIER,assess(facts(Tier.GOLD,100),mining).missing().getFirst().code());}
    @Test void strongerToolCanSatisfyTierButWornOneStillFails(){assertTrue(assess(facts(Tier.DIAMOND,30),mining).ready());assertEquals(Code.TOOL_DURABILITY,assess(facts(Tier.NETHERITE,2),mining).missing().getFirst().code());}
    @Test void preservesAllMissingReasonsAndCallerFoodReserve(){
        var result=assess(new Facts(List.of(),3,Home.OTHER_DIMENSION,0,false),Plan.expedition(6,true));
        assertEquals(List.of(Code.HOME_IN_OTHER_DIMENSION,Code.FOOD_RESERVE,Code.TOOL_MISSING,Code.TOOL_MISSING,Code.BOAT_MISSING),result.missing().stream().map(Missing::code).toList());
        assertEquals(6,result.missing().get(1).need());
    }
    @Test void ownedMountedBoatSatisfiesWaterTransportButLandTravelNeedsNoBoat(){
        var tools=List.of(new CarriedTool(Tool.PICKAXE,Tier.STONE,20),new CarriedTool(Tool.AXE,Tier.WOOD,20));
        assertTrue(assess(new Facts(tools,6,Home.SAME_DIMENSION,0,true),Plan.expedition(6,true)).ready());
        assertTrue(assess(new Facts(tools,6,Home.SAME_DIMENSION,0,false),Plan.expedition(6,false)).ready());
        assertTrue(assess(new Facts(tools,6,Home.SAME_DIMENSION,1,false),Plan.expedition(6,true)).ready());
    }
    @Test void noHomeCannotBeReplacedByEquipment(){assertEquals(Code.NO_OWNED_HOME,assess(new Facts(facts(Tier.IRON,20).carriedTools(),64,Home.NONE,10,true),mining).missing().getFirst().code());}
    @Test void lowTierDurabilityCannotMaskWornCorrectTierOrCombineBrokenTools(){
        var tools=List.of(new CarriedTool(Tool.PICKAXE,Tier.WOOD,100),new CarriedTool(Tool.PICKAXE,Tier.IRON,8),new CarriedTool(Tool.PICKAXE,Tier.IRON,8));
        var missing=assess(new Facts(tools,4,Home.SAME_DIMENSION,0,false),mining).missing().getFirst();assertEquals(Code.TOOL_DURABILITY,missing.code());assertEquals(8,missing.have());
    }
    @Test void inputsAndResultAreImmutableAndNegativeCountsGiveNoCredit(){
        var input=new ArrayList<CarriedTool>();var f=new Facts(input,-3,Home.NONE,-1,false);input.add(new CarriedTool(Tool.PICKAXE,Tier.IRON,100));
        assertTrue(f.carriedTools().isEmpty());assertEquals(0,f.cookedFood());assertEquals(0,f.carriedBoats());
        assertThrows(UnsupportedOperationException.class,()->assess(f,mining).missing().clear());
        assertThrows(IllegalArgumentException.class,()->new Requirement(Tool.AXE,Tier.WOOD,0));
    }
}

