package de.serverhub.common;

import de.serverhub.common.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** The sole network menu source. Discovery only adds missing entries; it never deletes edits. */
public final class CentralMenuConfig {
    public record Server(MenuSnapshot.Entry entry,boolean hidden,int order,String override,List<String> sleepMarkers) {}
    public record State(UUID revision,MenuOptions options,List<Server> servers) {}
    private final Path file;
    private volatile State current;
    public CentralMenuConfig(Path directory){file=directory.resolve("servers.yml");}
    public State current(){return current;}
    public synchronized State load(Collection<String> registered)throws IOException {
        byte[] before=Files.exists(file)?Files.readAllBytes(file):null;
        if(before!=null&&before.length>1024*1024)throw new IOException("servers.yml ist größer als 1 MiB");
        Map<String,Object> root;
        try{
            byte[] input=before;
            if(input==null)try(var source=getClass().getResourceAsStream("/servers.yml")){
                if(source==null)throw new IOException("Mitgelieferte servers.yml fehlt");input=source.readAllBytes();
            }
            var loader=new LoaderOptions();loader.setAllowDuplicateKeys(false);loader.setMaxAliasesForCollections(20);loader.setCodePointLimit(1024*1024);
            Object parsed=new Yaml(new SafeConstructor(loader)).load(new String(input,StandardCharsets.UTF_8));
            root=mapping(parsed,"servers.yml");
            Map<String,String> values=new LinkedHashMap<>();
            for(String key:MenuOptions.DEFAULTS.keySet()){Object value=at(root,key);if(value!=null)values.put(key,String.valueOf(value));}
            MenuOptions options=new MenuOptions(values);
            Map<String,Object> nodes=root.containsKey("servers")?mapping(root.get("servers"),"servers"):new LinkedHashMap<>();
            root.put("servers",nodes);
            boolean changed=before==null;
            for(var setting:MenuOptions.DEFAULTS.entrySet())if(!setting.getKey().equals("runtime.waiting")&&at(root,setting.getKey())==null){put(root,setting.getKey(),setting.getValue());changed=true;}
            root.put("config-version",2);
            for(String target:registered.stream().filter(Wire::validTarget).distinct().sorted(String.CASE_INSENSITIVE_ORDER).limit(Wire.MAX_SERVERS).toList()){
                if(nodes.keySet().stream().anyMatch(key->key.equalsIgnoreCase(target)))continue;
                var added=new LinkedHashMap<String,Object>();String name=target.replace('_',' ').replace('-',' ').strip();
                if(name.isBlank())name=target;name=name.substring(0,1).toUpperCase(Locale.ROOT)+name.substring(1);
                added.put("name",name);added.put("color",options.text("menu.text-color"));added.put("font-size",options.number("menu.font-size"));
                added.put("font-family","");added.put("permission","");added.put("description",new ArrayList<String>());added.put("hidden",false);added.put("order",0);
                added.put("status-override","auto");added.put("sleep-motd-contains",new ArrayList<String>());
                nodes.put(target,added);changed=true;
            }
            var servers=new ArrayList<Server>();var names=new HashSet<String>();
            for(var item:nodes.entrySet()){
                String target=item.getKey();if(!Wire.validTarget(target)||!names.add(target.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Ungültiges oder doppeltes Ziel: "+target);
                var node=mapping(item.getValue(),"servers."+target);
                if(!node.containsKey("font-family")){node.put("font-family","");nodes.put(target,node);changed=true;}
                String override=string(node,"status-override","auto").toLowerCase(Locale.ROOT);
                if(!Set.of("auto","online","offline","sleeping").contains(override))throw new IllegalArgumentException(target+": status-override muss auto, online, offline oder sleeping sein");
                var entry=new MenuSnapshot.Entry(target,string(node,"name",target),string(node,"color",options.text("menu.text-color")),
                    integer(node,"font-size",options.number("menu.font-size")),string(node,"permission",""),strings(node.get("description")),MenuSnapshot.Status.UNKNOWN,string(node,"font-family",""));
                List<String> markers=strings(node.get("sleep-motd-contains"));
                if(markers.size()>10||markers.stream().anyMatch(s->s.isBlank()||s.length()>128))throw new IllegalArgumentException("Ungültige Schlaf-Kennung bei "+target);
                servers.add(new Server(entry,bool(node,"hidden",false),integer(node,"order",0),override,markers));
            }
            servers.sort(Comparator.comparingInt(Server::order).thenComparing(s->s.entry.target(),String.CASE_INSENSITIVE_ORDER));
            State next=new State(UUID.randomUUID(),options,List.copyOf(servers));
            // Validate channel size before applying or writing a discovered configuration.
            new MenuSnapshot(next.revision,options,servers.stream().filter(s->!s.hidden).limit(Wire.MAX_SERVERS).map(Server::entry).toList()).encode();
            if(changed)save(root,before);
            current=next;return next;
        }catch(IllegalArgumentException|org.yaml.snakeyaml.error.YAMLException e){throw new IOException("servers.yml: "+e.getMessage(),e);}
    }
    public synchronized boolean discover(Collection<String> names)throws IOException {
        State state=current;
        if(state!=null&&names.stream().filter(Wire::validTarget).distinct().sorted(String.CASE_INSENSITIVE_ORDER).limit(Wire.MAX_SERVERS).allMatch(n->state.servers.stream().anyMatch(s->s.entry.target().equalsIgnoreCase(n))))return false;
        load(names);return true;
    }
    public void adopt(State state){current=Objects.requireNonNull(state);}
    private void save(Map<String,Object> root,byte[] before)throws IOException {
        var options=new DumperOptions();options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);options.setPrettyFlow(true);options.setIndent(2);options.setAllowUnicode(true);
        String header="# ServerHub: zentrale Netzwerk-Konfiguration. Neue Velocity-Ziele werden ergänzt.\n"
            +"# Menü/HUD unter menu, shift-f-hint und hotbar-icon; Farben als '#RRGGBB'.\n"
            +"# menu.layout: list|grid; menu.navigation: scroll|pages. Reload: /serverhubproxy reload\n"
            +"# Schriften: sans|serif|mono|custom. Eigene TTF/OTF unter font.url oder pack/assets/serverhub/fonts/custom.ttf.\n"
            +"# Gelb: Paper-Pause, ServerNavigator-Start oder eindeutige Schlaf-Kennung. Leere Server sind nicht automatisch schlafend.\n"
            +"# Entfernte Ziele bleiben als Einstellungen erhalten, werden aber nicht angezeigt.\n";
        Path temp=Files.createTempFile(file.getParent(),"servers-",".tmp");
        try{
            Files.writeString(temp,header+new Yaml(options).dump(root),StandardCharsets.UTF_8);
            if(before==null?Files.exists(file):!Files.exists(file)||!Arrays.equals(before,Files.readAllBytes(file)))throw new IOException("servers.yml wurde gleichzeitig bearbeitet; bitte erneut laden");
            try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    private static Map<String,Object> mapping(Object value,String name){
        if(!(value instanceof Map<?,?> map))throw new IllegalArgumentException(name+" muss eine YAML-Map sein");
        var result=new LinkedHashMap<String,Object>();map.forEach((k,v)->{if(!(k instanceof String key))throw new IllegalArgumentException("Textschlüssel erwartet");result.put(key,v);});return result;
    }
    private static Object at(Map<String,Object> root,String path){Object value=root;for(String key:path.split("\\.")){if(!(value instanceof Map<?,?> m))return null;value=m.get(key);}return value;}
    @SuppressWarnings("unchecked") private static void put(Map<String,Object> root,String path,Object value){String[] parts=path.split("\\.");Map<String,Object> node=root;for(int i=0;i<parts.length-1;i++){Object next=node.get(parts[i]);if(next==null){next=new LinkedHashMap<String,Object>();node.put(parts[i],next);}if(!(next instanceof Map<?,?>))throw new IllegalArgumentException(parts[i]+" muss eine YAML-Map sein");node=(Map<String,Object>)next;}node.put(parts[parts.length-1],value);}
    private static String string(Map<String,Object> m,String key,String fallback){Object v=m.get(key);return v==null?fallback:String.valueOf(v);}
    private static int integer(Map<String,Object> m,String key,int fallback){Object v=m.get(key);return v==null?fallback:Integer.parseInt(String.valueOf(v));}
    private static boolean bool(Map<String,Object> m,String key,boolean fallback){Object v=m.get(key);if(v==null)return fallback;if(v instanceof Boolean b)return b;throw new IllegalArgumentException(key+" muss true/false sein");}
    private static List<String> strings(Object value){if(value==null)return List.of();if(!(value instanceof List<?> l))throw new IllegalArgumentException("Liste erwartet");return l.stream().map(String::valueOf).toList();}
}
