package com.pla.smart_npc.init;
import com.pla.smart_npc.fabric.RegistryEntry;
import com.pla.smart_npc.inventory.InventoryViewerMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.inventory.MenuType;
public final class SmartNpcModMenus {
    public static final RegistryEntry<MenuType<?>,ExtendedMenuType<InventoryViewerMenu,Integer>> INVENTORY_VIEWER = new RegistryEntry<>(
        Registry.register(BuiltInRegistries.MENU,Identifier.fromNamespaceAndPath("smart_npc","inventory_viewer"),new ExtendedMenuType<>(InventoryViewerMenu::new,ByteBufCodecs.INT)));
}
