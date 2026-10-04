package com.pla.smart_npc.fabric;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Fabric registrations are performed during initialization, before registries freeze. */
public class Registration<R> {
    private final Registry<R> registry;
    protected final String namespace;
    protected Registration(Registry<R> registry, String namespace) { this.registry=registry; this.namespace=namespace; }
    public static <R> Registration<R> create(Registry<R> registry,String namespace) { return new Registration<>(registry,namespace); }
    public static Items createItems(String namespace) { return new Items(namespace); }
    public <T extends R> RegistryEntry<R,T> register(String name, Function<Identifier,T> factory) {
        var id=Identifier.fromNamespaceAndPath(namespace,name);
        T value=factory.apply(id);
        Registry.register(registry,id,value);
        return new RegistryEntry<>(value);
    }
    public <T extends R> RegistryEntry<R,T> register(String name,Supplier<T> factory) { return register(name,id->factory.get()); }
    public static final class Items extends Registration<Item> {
        private Items(String namespace) { super(BuiltInRegistries.ITEM,namespace); }
        public <T extends Item> ItemEntry<T> registerItem(String name,Function<Item.Properties,T> factory) { return registerItem(name,factory,UnaryOperator.identity()); }
        public <T extends Item> ItemEntry<T> registerItem(String name,Function<Item.Properties,T> factory,UnaryOperator<Item.Properties> properties) {
            var id=Identifier.fromNamespaceAndPath(namespace,name);
            var value=factory.apply(properties.apply(new Item.Properties().setId(ResourceKey.create(Registries.ITEM,id))));
            Registry.register(BuiltInRegistries.ITEM,id,value);
            return new ItemEntry<>(value);
        }
    }
}
