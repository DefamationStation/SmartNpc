package com.pla.smart_npc.init;

import com.pla.smart_npc.SmartNpc;
import com.pla.smart_npc.item.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import com.pla.smart_npc.fabric.Registration;
import com.pla.smart_npc.fabric.ItemEntry;

public class SmartNpcModItems {
    public static final Registration.Items REGISTRY = Registration.createItems(SmartNpc.MODID);

    public static final ItemEntry<InventoryViewerItem> INVENTORY_VIEWER = SmartNpcModItems.REGISTRY.registerItem(
            "player_npc_inspector", InventoryViewerItem::new, properties -> properties.stacksTo(1));
    public static final ItemEntry<SpawnEggItem> PLAYER_NPC_SPAWN_EGG = SmartNpcModItems.REGISTRY.registerItem(
            "player_npc_spawn_egg",
            properties -> new SpawnEggItem(properties.spawnEgg(SmartNpcModEntities.PLAYER_NPC.get()))
    );

}
