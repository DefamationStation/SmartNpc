package com.pla.smart_npc.event;

import com.pla.smart_npc.util.EquipmentDataLoader;
import com.pla.smart_npc.util.PlayerNpcChatTemplateLoader;
import com.pla.smart_npc.util.PlayerNpcBuildLayoutLoader;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.bus.api.SubscribeEvent;

public class NpcGearLoadEvent {
    @SubscribeEvent
    public void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new EquipmentDataLoader());
        event.addListener(new PlayerNpcBuildLayoutLoader());
        event.addListener(new PlayerNpcChatTemplateLoader());
    }
}
