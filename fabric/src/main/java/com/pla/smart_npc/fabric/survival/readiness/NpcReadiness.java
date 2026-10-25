package com.pla.smart_npc.fabric.survival.readiness;

import com.pla.smart_npc.entity.PlayerNpcEntity;
import com.pla.smart_npc.fabric.PersistentData;
import com.pla.smart_npc.fabric.survival.Journeys;
import com.pla.smart_npc.fabric.survival.SurvivalTasks;
import com.pla.smart_npc.fabric.survival.memory.NpcMemories;
import com.pla.smart_npc.util.PlayerNpcHomeUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** On-demand advice from actual carried equipment, never remote storage or earned labels. */
public final class NpcReadiness {
    private NpcReadiness() { }
    public static ExpeditionReadiness.Assessment expedition(PlayerNpcEntity npc,boolean waterCrossing){
        var tools=new ArrayList<ExpeditionReadiness.CarriedTool>();int boats=0;
        for(var stack:npc.getInventory().getItems()){
            addTool(tools,stack);
            if(stack.getItem() instanceof net.minecraft.world.item.BoatItem)boats+=stack.getCount();
        }
        addTool(tools,npc.getMainHandItem());addTool(tools,npc.getOffhandItem());
        // Main/offhand may alias carried slots; readiness is boolean, never a stock total.
        boolean handBoat=npc.getMainHandItem().getItem() instanceof net.minecraft.world.item.BoatItem
                ||npc.getOffhandItem().getItem() instanceof net.minecraft.world.item.BoatItem;
        if(handBoat)boats=Math.max(1,boats);
        var home=PlayerNpcHomeUtil.getHome(npc).isPresent()?ExpeditionReadiness.Home.SAME_DIMENSION:
                PersistentData.get(npc).contains("PlayerNpcHomeDimension")
                        &&!PersistentData.get(npc).getStringOr("PlayerNpcHomeDimension","").equals(SurvivalTasks.dimension(npc))
                        ?ExpeditionReadiness.Home.OTHER_DIMENSION:ExpeditionReadiness.Home.NONE;
        var facts=new ExpeditionReadiness.Facts(tools,SurvivalTasks.cookedFoodCount(npc),home,boats,Journeys.ownsBoat(npc,npc.getVehicle()));
        return ExpeditionReadiness.assess(facts,ExpeditionReadiness.Plan.expedition(NpcMemories.departureFoodReserve(npc),waterCrossing));
    }
    private static void addTool(List<ExpeditionReadiness.CarriedTool> tools,ItemStack stack){
        if(stack.isEmpty())return;
        var id=BuiltInRegistries.ITEM.getKey(stack.getItem());
        if(!id.getNamespace().equals("minecraft"))return; // Unknown tools need an explicit capability adapter.
        var parts=id.getPath().split("_",2);if(parts.length!=2)return;
        try{
            var type=ExpeditionReadiness.Tool.valueOf(parts[1].toUpperCase(Locale.ROOT));
            var material=parts[0].equals("wooden")?"WOOD":parts[0].equals("golden")?"GOLD":parts[0].toUpperCase(Locale.ROOT);
            var tier=ExpeditionReadiness.Tier.valueOf(material);
            tools.add(new ExpeditionReadiness.CarriedTool(type,tier,stack.getMaxDamage()-stack.getDamageValue()));
        }catch(IllegalArgumentException ignored){ }
    }
}
