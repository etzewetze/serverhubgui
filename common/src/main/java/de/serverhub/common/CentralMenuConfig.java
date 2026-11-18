package de.serverhub.common;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Active entries live in servers.yml; general options and inactive entries in config.yml. */
public final class CentralMenuConfig {
    public record Server(MenuSnapshot.Entry entry,boolean hidden,int order,String override,List<String> sleepMarkers,String ampInstance) {
        public Server(MenuSnapshot.Entry entry,boolean hidden,int order,String override,List<String> markers){this(entry,hidden,order,override,markers,"");}
    }
    public record State(UUID revision,MenuOptions options,List<Server> servers,List<FontSource> fontSources,Set<String> registeredTargets) {
        public State {registeredTargets=Set.copyOf(registeredTargets);}
        public State(UUID revision,MenuOptions options,List<Server> servers,List<FontSource> fonts){this(revision,options,servers,fonts,targets(servers.stream().map(s->s.entry().target()).toList()));}
        public State(UUID revision,MenuOptions options,List<Server> servers){this(revision,options,servers,List.of());}
    }
    private final Path file,config;
    private volatile State current;
    public CentralMenuConfig(Path directory){file=directory.resolve("servers.yml");config=directory.resolve("config.yml");}
    public State current(){return current;}
    public synchronized State load(Collection<String> registered)throws IOException {
        Files.createDirectories(file.getParent());byte[] before=read(file),configBefore=read(config);
        try{
            Map<String,Object> originalServers=parse(before==null?resource("servers.yml"):before,"servers.yml");
            Map<String,Object> originalConfig=parse(configBefore==null?resource("config.yml"):configBefore,"config.yml");
            var settings=copy(originalConfig);var old=copy(originalServers);
            int version=integer(old,"config-version",0);boolean legacy=old.keySet().stream().anyMatch(key->!key.equals("servers"));
            // Old combined files are authoritative during migration, including existing Paper defaults.
            for(var entry:old.entrySet())if(!Set.of("servers","config-version").contains(entry.getKey()))settings.put(entry.getKey(),entry.getValue());
            boolean upgradedDefaults=false;
            if(legacy&&version<3){
                if(Objects.equals(String.valueOf(at(settings,"status.ping-interval-seconds")),"10")){put(settings,"status.ping-interval-seconds",1);upgradedDefaults=true;}
                if(Objects.equals(String.valueOf(at(settings,"status.timeout-ms")),"2000")){put(settings,"status.timeout-ms",1000);upgradedDefaults=true;}
            }
            if(legacy&&version<4){
                if(Objects.equals(String.valueOf(at(settings,"menu.button-width")),"140")){put(settings,"menu.button-width",0);upgradedDefaults=true;}
                if(Objects.equals(String.valueOf(at(settings,"menu.title-padding")),"12")){put(settings,"menu.title-padding",48);upgradedDefaults=true;}
                if(Objects.equals(String.valueOf(at(settings,"menu.title-max-width")),"320")){put(settings,"menu.title-max-width",400);upgradedDefaults=true;}
            }
            settings.remove("servers");settings.put("config-version",5);
            for(var entry:MenuOptions.DEFAULTS.entrySet())if(!entry.getKey().startsWith("runtime.")&&at(settings,entry.getKey())==null)put(settings,entry.getKey(),entry.getValue());
            if(!settings.containsKey("fonts"))settings.put("fonts",new LinkedHashMap<>());
            var values=new LinkedHashMap<String,String>();for(String key:MenuOptions.DEFAULTS.keySet()){Object value=at(settings,key);if(value!=null)values.put(key,String.valueOf(value));}
            var options=new MenuOptions(values);var fonts=new ArrayList<FontSource>();
            for(var entry:mapping(settings.get("fonts"),"fonts").entrySet()){
                if(entry.getValue() instanceof String url)fonts.add(new FontSource(entry.getKey(),url,false));
                else {var node=mapping(entry.getValue(),"fonts."+entry.getKey());fonts.add(new FontSource(entry.getKey(),string(node,"url",""),bool(node,"refresh",false)));}
            }
            var nodes=mapping(old.getOrDefault("servers",Map.of()),"servers");var archive=mapping(settings.getOrDefault("inactive-servers",Map.of()),"inactive-servers");
            for(var entry:archive.entrySet())if(nodes.keySet().stream().noneMatch(key->key.equalsIgnoreCase(entry.getKey())))nodes.put(entry.getKey(),entry.getValue());
            Set<String> active=targets(registered);int nextSort=0;
            for(var value:nodes.values()){var node=mapping(value,"Servereintrag");nextSort=Math.max(nextSort,integer(node,"sort",integer(node,"order",0)));}
            for(String target:registered.stream().filter(Wire::validTarget).distinct().sorted(String.CASE_INSENSITIVE_ORDER).limit(Wire.MAX_SERVERS).toList()){
                if(nodes.keySet().stream().anyMatch(key->key.equalsIgnoreCase(target)))continue;
                String name=target.replace('_',' ').replace('-',' ').strip();if(name.isBlank())name=target;name=name.substring(0,1).toUpperCase(Locale.ROOT)+name.substring(1);
                var node=new LinkedHashMap<String,Object>();node.put("name",name);node.put("color",options.text("menu.text-color"));node.put("font-size",options.number("menu.font-size"));node.put("sort",nextSort==Integer.MAX_VALUE?nextSort:++nextSort);nodes.put(target,node);
            }
            var servers=new ArrayList<Server>();var names=new HashSet<String>();var normalized=new LinkedHashMap<String,Map<String,Object>>();
            for(var item:nodes.entrySet()){
                String target=item.getKey();if(!Wire.validTarget(target)||!names.add(target.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Ungültiges oder doppeltes Ziel: "+target);
                var node=mapping(item.getValue(),"servers."+target);int sort=integer(node,"sort",integer(node,"order",0));node.remove("order");node.put("sort",sort);
                var defaults=new LinkedHashMap<String,Object>();defaults.put("font-family","");defaults.put("width",0);defaults.put("background","");defaults.put("background-tint","#FFFFFF");defaults.put("rounded",false);defaults.put("prefix","");defaults.put("hover-enabled",true);defaults.put("hover","");defaults.put("amp-instance","");defaults.put("permission","");defaults.put("description",new ArrayList<String>());defaults.put("hidden",false);defaults.put("status-override","auto");defaults.put("sleep-motd-contains",new ArrayList<String>());
                defaults.forEach(node::putIfAbsent);
                var style=new ServerStyle(integer(node,"width",0),string(node,"background","").replaceFirst("(?i)\\.png$","").toLowerCase(Locale.ROOT),string(node,"background-tint","#FFFFFF"),bool(node,"rounded",false),-1,-1,string(node,"prefix",""),bool(node,"hover-enabled",true),string(node,"hover",""),false);
                String override=string(node,"status-override","auto").toLowerCase(Locale.ROOT);if(!Set.of("auto","online","offline","sleeping").contains(override))throw new IllegalArgumentException(target+": status-override muss auto, online, offline oder sleeping sein");
                var entry=new MenuSnapshot.Entry(target,string(node,"name",target),string(node,"color",options.text("menu.text-color")),integer(node,"font-size",options.number("menu.font-size")),string(node,"permission",""),strings(node.get("description")),MenuSnapshot.Status.UNKNOWN,string(node,"font-family",""),style);
                var markers=strings(node.get("sleep-motd-contains"));if(markers.size()>10||markers.stream().anyMatch(s->s.isBlank()||s.length()>128))throw new IllegalArgumentException("Ungültige Schlaf-Kennung bei "+target);
                servers.add(new Server(entry,bool(node,"hidden",false),sort,override,markers,string(node,"amp-instance","")));normalized.put(target,node);
            }
            servers.sort(Comparator.comparingInt(Server::order).thenComparing(s->s.entry().target(),String.CASE_INSENSITIVE_ORDER));
            var shown=new LinkedHashMap<String,Object>();var inactive=new LinkedHashMap<String,Object>();
            for(var server:servers)(active.contains(server.entry().target().toLowerCase(Locale.ROOT))&&!server.hidden()?shown:inactive).put(server.entry().target(),normalized.get(server.entry().target()));
            settings.put("inactive-servers",inactive);var serverDocument=new LinkedHashMap<String,Object>();serverDocument.put("servers",shown);
            var next=new State(UUID.randomUUID(),options,List.copyOf(servers),List.copyOf(fonts),active);
            new MenuSnapshot(next.revision(),options,servers.stream().filter(s->shown.containsKey(s.entry().target())).map(Server::entry).toList()).encode();
            // Validate both documents and concurrent edits before removing anything from the old file.
            boolean writeConfig=configBefore==null||!settings.equals(originalConfig),writeServers=before==null||!serverDocument.equals(originalServers);
            if(writeConfig||writeServers){
                byte[] configBytes=writeConfig?encode(settings,"# ServerHub: allgemeine Einstellungen. Pack-Download: config.properties; AMP: amp.yml.\n# Ausgeblendete oder entfernte Ziele bleiben unter inactive-servers erhalten.\n"):null;
                byte[] serverBytes=writeServers?encode(serverDocument,"# ServerHub: aktive Server. Reihenfolge: sort: 1, sort: 2, ...\n# Allgemeine Optionen und inaktive Ziele: config.yml. /serverhubproxy reload\n"):null;
                unchanged(file,before);unchanged(config,configBefore);
                if(legacy&&before!=null)backup("servers-v2.5.2-backup.yml",before);
                if(upgradedDefaults&&before!=null)backup(version>=3?"servers-v2.5-backup.yml":"servers-v2.4-backup.yml",before);
                if(configBefore!=null&&integer(originalConfig,"config-version",0)<5)backup("config-v2.5.2-backup.yml",configBefore);
                // Config first: a crash before servers.yml is replaced leaves the old data recoverable.
                if(writeConfig){unchanged(config,configBefore);PackWorkspace.atomic(config,configBytes);}
                if(writeServers){unchanged(file,before);PackWorkspace.atomic(file,serverBytes);}
            }
            current=next;return next;
        }catch(IllegalArgumentException|org.yaml.snakeyaml.error.YAMLException e){throw new IOException("ServerHub-Konfiguration: "+e.getMessage(),e);}
    }
    public synchronized boolean discover(Collection<String> registered)throws IOException {if(current!=null&&current.registeredTargets().equals(targets(registered)))return false;load(registered);return true;}
    public void adopt(State state){current=Objects.requireNonNull(state);}
    private static Set<String> targets(Collection<String> names){var result=new TreeSet<String>();names.stream().filter(Wire::validTarget).map(s->s.toLowerCase(Locale.ROOT)).sorted().limit(Wire.MAX_SERVERS).forEach(result::add);return Set.copyOf(result);}
    private static byte[] read(Path path)throws IOException {if(!Files.exists(path))return null;if(Files.size(path)>1024*1024)throw new IOException(path.getFileName()+" ist größer als 1 MiB");return Files.readAllBytes(path);}
    private static byte[] resource(String name)throws IOException {try(var input=CentralMenuConfig.class.getResourceAsStream("/"+name)){if(input==null)throw new IOException("Mitgelieferte "+name+" fehlt");return input.readAllBytes();}}
    private static Map<String,Object> parse(byte[] input,String name){var loader=new LoaderOptions();loader.setAllowDuplicateKeys(false);loader.setMaxAliasesForCollections(20);loader.setCodePointLimit(1024*1024);Object value=new Yaml(new SafeConstructor(loader)).load(new String(input,StandardCharsets.UTF_8));return value==null?new LinkedHashMap<>():mapping(value,name);}
    private static Map<String,Object> copy(Map<String,Object> root){var result=new LinkedHashMap<String,Object>();root.forEach((key,value)->result.put(key,value instanceof Map<?,?>?copy(mapping(value,key)):value));return result;}
    private void backup(String name,byte[] bytes)throws IOException {Path path=file.resolveSibling(name);for(int index=1;Files.exists(path);index++){if(Arrays.equals(Files.readAllBytes(path),bytes))return;path=file.resolveSibling(name.replace(".yml","-"+index+".yml"));}Files.write(path,bytes,StandardOpenOption.CREATE_NEW);}
    private static void unchanged(Path path,byte[] before)throws IOException {if(before==null?Files.exists(path):!Files.exists(path)||!Arrays.equals(before,Files.readAllBytes(path)))throw new IOException(path.getFileName()+" wurde gleichzeitig bearbeitet; bitte erneut laden");}
    private static byte[] encode(Map<String,Object> document,String header)throws IOException {var options=new DumperOptions();options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);options.setPrettyFlow(true);options.setIndent(2);options.setAllowUnicode(true);byte[] bytes=(header+new Yaml(options).dump(document)).getBytes(StandardCharsets.UTF_8);if(bytes.length>1024*1024)throw new IOException("Konfigurationsdatei würde größer als 1 MiB");return bytes;}
    private static Map<String,Object> mapping(Object value,String name){if(!(value instanceof Map<?,?> map))throw new IllegalArgumentException(name+" muss eine YAML-Map sein");var result=new LinkedHashMap<String,Object>();map.forEach((key,item)->{if(!(key instanceof String text))throw new IllegalArgumentException("Textschlüssel erwartet");result.put(text,item);});return result;}
    private static Object at(Map<String,Object> root,String path){Object value=root;for(String key:path.split("\\.")){if(!(value instanceof Map<?,?> map))return null;value=map.get(key);}return value;}
    @SuppressWarnings("unchecked") private static void put(Map<String,Object> root,String path,Object value){String[] parts=path.split("\\.");Map<String,Object> node=root;for(int i=0;i<parts.length-1;i++){Object next=node.get(parts[i]);if(next==null){next=new LinkedHashMap<String,Object>();node.put(parts[i],next);}if(!(next instanceof Map<?,?>))throw new IllegalArgumentException(parts[i]+" muss eine YAML-Map sein");node=(Map<String,Object>)next;}node.put(parts[parts.length-1],value);}
    private static String string(Map<String,Object> map,String key,String fallback){Object value=map.get(key);return value==null?fallback:String.valueOf(value);}
    private static int integer(Map<String,Object> map,String key,int fallback){Object value=map.get(key);return value==null?fallback:Integer.parseInt(String.valueOf(value));}
    private static boolean bool(Map<String,Object> map,String key,boolean fallback){Object value=map.get(key);if(value==null)return fallback;if(value instanceof Boolean b)return b;throw new IllegalArgumentException(key+" muss true/false sein");}
    private static List<String> strings(Object value){if(value==null)return List.of();if(!(value instanceof List<?> list))throw new IllegalArgumentException("Liste erwartet");return list.stream().map(String::valueOf).toList();}
}
