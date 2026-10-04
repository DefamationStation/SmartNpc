package com.pla.smart_npc.fabric;
import net.minecraft.world.item.Item;
public final class ItemEntry<T extends Item> extends RegistryEntry<Item,T> {
    public ItemEntry(T value) { super(value); }
}
