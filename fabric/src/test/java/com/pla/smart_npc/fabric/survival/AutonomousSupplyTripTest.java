package com.pla.smart_npc.fabric.survival;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AutonomousSupplyTripTest {
    @Test void neitherSpawnedDropsNorInventoryGrowthAloneProvesPersonalCollection(){
        assertFalse(AutonomousSupplyTrip.collected(2,2,true));
        assertFalse(AutonomousSupplyTrip.collected(2,3,false));
        assertFalse(AutonomousSupplyTrip.collected(2,1,true));
        assertTrue(AutonomousSupplyTrip.collected(2,3,true));
    }
    @Test void resourceEvidenceCannotUseUnrelatedClearingOrWrongMaterial(){
        assertTrue(AutonomousSupplyTrip.matchesResource(false,"minecraft:oak_log"));
        assertTrue(AutonomousSupplyTrip.matchesResource(true,"minecraft:deepslate"));
        assertFalse(AutonomousSupplyTrip.matchesResource(false,"minecraft:oak_planks"));
        assertFalse(AutonomousSupplyTrip.matchesResource(true,"minecraft:coal_ore"));
        assertFalse(AutonomousSupplyTrip.matchesResource(false,"minecraft:stone"));
    }
    @Test void savedIdentityDoesNotClaimAReplacementJourney(){
        var trip=new Journeys.State();trip.dimension="minecraft:overworld";trip.reason="visible wood for shelter supplies";
        trip.home=BlockPos.ZERO;trip.destination=new BlockPos(20,70,0);
        var identity=new AutonomousSupplyTrip.Identity(trip.dimension,trip.reason,trip.destination,trip.home);
        assertTrue(identity.matches(trip));
        trip.reason="requested travel";assertFalse(identity.matches(trip));
        trip.reason=identity.reason();trip.destination=trip.destination.east();assertFalse(identity.matches(trip));
        trip.destination=identity.destination();trip.home=trip.home.north();assertFalse(identity.matches(trip));
        trip.home=identity.home();trip.dimension="minecraft:the_nether";assertFalse(identity.matches(trip));
    }
}
