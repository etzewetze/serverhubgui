package de.serverhub.common;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** The proxy owns all announcements; Paper only uses a local store in standalone mode. */
public final class NewsStore {
    public static final int MAX_MESSAGE=4096;
    private final Path file;
    private Map<String,String> notes=Map.of();
    public NewsStore(Path directory)throws IOException {file=directory.resolve("news.yml");reload();}
    public synchronized void reload()throws IOException {
        if(!Files.exists(file)){notes=Map.of();return;}if(Files.size(file)>2*1024*1024)throw new IOException("news.yml ist zu groß");
        try{var loader=new LoaderOptions();loader.setAllowDuplicateKeys(false);loader.setMaxAliasesForCollections(0);loader.setCodePointLimit(2*1024*1024);Object value=new Yaml(new SafeConstructor(loader)).load(Files.readString(file));var map=new TreeMap<String,String>();
            if(value!=null){if(!(value instanceof Map<?,?> root))throw new IOException("news.yml benötigt eine Map");for(var entry:root.entrySet()){String target=String.valueOf(entry.getKey()),message=String.valueOf(entry.getValue());validate(target,message);if(map.put(target.toLowerCase(Locale.ROOT),message)!=null)throw new IOException("Doppelte News-Ziele");}}
            if(map.size()>Wire.MAX_SERVERS)throw new IOException("Zu viele Neuigkeiten");notes=Map.copyOf(map);
        }catch(org.yaml.snakeyaml.error.YAMLException|IllegalArgumentException e){throw new IOException("news.yml: "+e.getMessage(),e);}
    }
    public synchronized String get(String target){return notes.getOrDefault(target.toLowerCase(Locale.ROOT),"");}
    public synchronized boolean active(String target){return !get(target).isBlank();}
    public synchronized void start(String target,String message)throws IOException {message=message.replace("\\n","\n").strip();validate(target,message);var next=new TreeMap<>(notes);next.put(target.toLowerCase(Locale.ROOT),message);if(next.size()>Wire.MAX_SERVERS)throw new IOException("Zu viele Neuigkeiten");save(next);}
    public synchronized void stop(String target)throws IOException {if(!Wire.validTarget(target))throw new IOException("Ungültiges News-Ziel");var next=new TreeMap<>(notes);next.remove(target.toLowerCase(Locale.ROOT));save(next);}
    private static void validate(String target,String message)throws IOException {if(!Wire.validTarget(target)||message==null||message.isBlank()||message.length()>MAX_MESSAGE)throw new IOException("News benötigen ein gültiges Ziel und 1–4096 Zeichen");}
    private void save(Map<String,String> next)throws IOException {Files.createDirectories(file.getParent());var options=new DumperOptions();options.setAllowUnicode(true);options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);byte[] bytes=("# ServerHub: aktive Neuigkeiten. Im Spiel /sh news start bzw. stop.\n"+new Yaml(options).dump(next)).getBytes(StandardCharsets.UTF_8);PackWorkspace.atomic(file,bytes);notes=Map.copyOf(next);}
}
