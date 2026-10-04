package com.pla.smart_npc.fabric;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

/** TOML configuration using the original keys and validation rules. */
public final class ConfigSpec {
    private final Map<String,ConfigValue<?>> values;
    private CommentedFileConfig config;
    private ConfigSpec(Map<String,ConfigValue<?>> values){this.values=Map.copyOf(values);}
    public void load(Path path) {
        config=CommentedFileConfig.builder(path).sync().build();config.load();
        for(var entry:values.entrySet()) {
            var value=entry.getValue();Object loaded=config.get(entry.getKey());
            if(loaded==null || !value.validator.test(loaded)) config.set(entry.getKey(),value.fallback);
            if(value.comment!=null) config.setComment(entry.getKey(),value.comment);
            value.owner=this;
        }
        config.save();
    }
    public CommentedFileConfig file(){return config;}
    public static class ConfigValue<T> {
        private final String path;private final T fallback;private final Predicate<Object> validator;private ConfigSpec owner;private String comment;
        private ConfigValue(String path,T fallback,Predicate<Object> validator){this.path=path;this.fallback=fallback;this.validator=validator;}
        @SuppressWarnings("unchecked") public T get(){
            Object value=owner==null?fallback:owner.config.get(path);
            // TOML may parse an integral setting as Long, or a floating setting as Integer.
            if(fallback instanceof Integer && value instanceof Number n) return (T)Integer.valueOf(n.intValue());
            if(fallback instanceof Double && value instanceof Number n) return (T)Double.valueOf(n.doubleValue());
            return (T)value;
        }
        public void set(T value){if(!validator.test(value))throw new IllegalArgumentException("Invalid "+path);if(owner==null)throw new IllegalStateException("Configuration not loaded");owner.config.set(path,value);owner.config.save();}
    }
    public static final class BooleanValue extends ConfigValue<Boolean>{private BooleanValue(String p,boolean d){super(p,d,o->o instanceof Boolean);}}
    public static final class IntValue extends ConfigValue<Integer>{private IntValue(String p,int d,int min,int max){super(p,d,o->o instanceof Number n&&n.doubleValue()==n.intValue()&&n.intValue()>=min&&n.intValue()<=max);}}
    public static final class DoubleValue extends ConfigValue<Double>{private DoubleValue(String p,double d,double min,double max){super(p,d,o->o instanceof Number n&&Double.isFinite(n.doubleValue())&&n.doubleValue()>=min&&n.doubleValue()<=max);}}
    public static final class Builder {
        private final Map<String,ConfigValue<?>> values=new LinkedHashMap<>();private final Deque<String> sections=new ArrayDeque<>();
        private String pendingComment;
        private String path(String key){return sections.isEmpty()?key:String.join(".",sections)+"."+key;}
        private <T extends ConfigValue<?>> T add(String key,T value){((ConfigValue<?>)value).comment=pendingComment;pendingComment=null;values.put(path(key),value);return value;}
        public Builder push(String key){sections.addLast(key);return this;}
        public Builder pop(){sections.removeLast();return this;}
        public Builder comment(String... text){pendingComment=String.join("\n",text);return this;}
        public BooleanValue define(String key,boolean value){return add(key,new BooleanValue(path(key),value));}
        public <T> ConfigValue<T> define(String key,T value,Predicate<Object> validator){return add(key,new ConfigValue<>(path(key),value,validator));}
        public IntValue defineInRange(String key,int value,int min,int max){return add(key,new IntValue(path(key),value,min,max));}
        public DoubleValue defineInRange(String key,double value,double min,double max){return add(key,new DoubleValue(path(key),value,min,max));}
        public <T> ConfigValue<List<? extends T>> defineList(String key,List<? extends T> value,Predicate<Object> validator){return add(key,new ConfigValue<>(path(key),value,o->o instanceof List<?> l&&l.stream().allMatch(validator)));}
        public ConfigSpec build(){return new ConfigSpec(values);}
    }
}
