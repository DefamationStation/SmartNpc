package com.pla.smart_npc.init;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
public final class SmartNpcModCreativeTabs {
    public static void register(){Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,Identifier.fromNamespaceAndPath("smart_npc","player_npc_tab"),FabricCreativeModeTab.builder()
        .icon(()->new ItemStack(SmartNpcModItems.INVENTORY_VIEWER.get()))
        .title(Component.translatable("creativetab.player_npc_tab"))
        .displayItems((p,out)->{out.accept(SmartNpcModItems.INVENTORY_VIEWER.get());out.accept(SmartNpcModItems.PLAYER_NPC_SPAWN_EGG.get());}).build());}
}
