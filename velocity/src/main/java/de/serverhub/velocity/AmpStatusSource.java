package de.serverhub.velocity;

import com.google.gson.*;
import de.serverhub.common.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Read-only CubeCoders AMP status source. Never starts, stops or wakes an application. */
final class AmpStatusSource implements AutoCloseable {
    record Config(boolean enabled,URI url,String username,String password,String token,int interval,int stale,boolean autoMatch,String mode,String instanceTarget,Map<String,MenuSnapshot.Status> states){}
    private record Instance(String id,String name,String friendly,String state){}
    private record Snapshot(List<Instance> instances,long time){}
    private final Config config;private final Consumer<String> log;private final Runnable changed;private final LongSupplier clock;
    private final HttpClient client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(3)).build();
    private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();private final AtomicBoolean pending=new AtomicBoolean();
    private volatile Snapshot snapshot=new Snapshot(List.of(),0);private volatile String error="Noch nicht abgefragt";private volatile boolean closed;
    private String session="";private long nextPoll,nextLogin,lastWarning;
    AmpStatusSource(Config config,Consumer<String> log,Runnable changed){this(config,log,changed,System::currentTimeMillis);}
    AmpStatusSource(Config config,Consumer<String> log,Runnable changed,LongSupplier clock){this.config=config;this.log=log;this.changed=changed;this.clock=clock;}
    Config config(){return config;}
    static Config load(Path directory)throws IOException {
        Path file=directory.resolve("amp.yml");if(!Files.exists(file))try(var input=AmpStatusSource.class.getResourceAsStream("/amp.yml")){if(input==null)throw new IOException("AMP-Vorlage fehlt");Files.copy(input,file);}
        if(Files.size(file)>128*1024)throw new IOException("amp.yml ist zu groß");
        try{var opts=new LoaderOptions();opts.setAllowDuplicateKeys(false);opts.setMaxAliasesForCollections(0);var root=new Yaml(new SafeConstructor(opts)).<Map<String,Object>>load(Files.readString(file));if(root==null)root=Map.of();
            boolean enabled=Boolean.parseBoolean(String.valueOf(root.getOrDefault("enabled",false)));String raw=String.valueOf(root.getOrDefault("url",""));URI url=null;
            if(enabled){url=URI.create(raw.endsWith("/")?raw:raw+"/");if(!Set.of("http","https").contains(url.getScheme())||url.getHost()==null||url.getUserInfo()!=null||url.getQuery()!=null||url.getFragment()!=null)throw new IOException("AMP benötigt eine HTTP(S)-Paneladresse ohne Zugangsdaten im Link");}
            int interval=Integer.parseInt(String.valueOf(root.getOrDefault("poll-interval-seconds",3))),stale=Integer.parseInt(String.valueOf(root.getOrDefault("stale-after-seconds",12)));
            if(interval<1||interval>300||stale<interval*2||stale>900)throw new IOException("AMP-Intervall 1–300 s, Ablauf mindestens zwei Intervalle und maximal 900 s");
            String mode=String.valueOf(root.getOrDefault("mode","controller")),target=String.valueOf(root.getOrDefault("instance-target",""));if(!Set.of("controller","instance").contains(mode)||mode.equals("instance")&&!Wire.validTarget(target))throw new IOException("AMP mode: controller oder instance mit instance-target");
            var states=defaults();Object custom=root.get("states");if(custom instanceof Map<?,?> groups)for(var entry:groups.entrySet()){
                var state=switch(entry.getKey().toString()){case "sleeping"->MenuSnapshot.Status.SLEEPING;case "ready"->MenuSnapshot.Status.ONLINE;case "starting"->MenuSnapshot.Status.STARTING;case "offline"->MenuSnapshot.Status.OFFLINE;default->throw new IOException("Unbekannte AMP-Statusgruppe");};
                if(!(entry.getValue() instanceof List<?> values))throw new IOException("AMP-Statusgruppen benötigen Listen");for(Object value:values)states.put(normalize(value.toString()),state);
            }
            return new Config(enabled,url,String.valueOf(root.getOrDefault("username","")),secret(root,"password"),secret(root,"token"),interval,stale,Boolean.parseBoolean(String.valueOf(root.getOrDefault("auto-match",true))),mode,target,Map.copyOf(states));
        }catch(org.yaml.snakeyaml.error.YAMLException|IllegalArgumentException|ClassCastException e){throw new IOException("amp.yml: "+e.getMessage(),e);}
    }
    private static String secret(Map<String,Object> root,String key){String env=String.valueOf(root.getOrDefault(key+"-env",""));if(!env.isBlank())return System.getenv().getOrDefault(env,"");return String.valueOf(root.getOrDefault(key,""));}
    private static Map<String,MenuSnapshot.Status> defaults(){var result=new HashMap<String,MenuSnapshot.Status>();for(String key:List.of("45","50","sleeping","preparingforsleep"))result.put(key,MenuSnapshot.Status.SLEEPING);for(String key:List.of("20","ready","running"))result.put(key,MenuSnapshot.Status.ONLINE);for(String key:List.of("5","7","10","30","60","prestart","configuring","starting","restarting","waiting"))result.put(key,MenuSnapshot.Status.STARTING);for(String key:List.of("0","40","100","200","250","stopped","stopping","failed","suspended","maintenance"))result.put(key,MenuSnapshot.Status.OFFLINE);return result;}
    private static String normalize(String text){return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
    void refresh(){long now=clock.getAsLong();if(closed||!config.enabled||now<nextPoll||!pending.compareAndSet(false,true))return;nextPoll=now+config.interval*1000L;executor.submit(()->{try{poll();}finally{pending.set(false);}});}
    void poll(){
        if(closed||!config.enabled)return;long now=clock.getAsLong();
        try{
            if(session.isEmpty()){
                if(now<nextLogin)return;
                nextLogin=now+60000;var login=post("Core/Login",Map.of("username",config.username,"password",config.password,"token",config.token,"rememberMe",false),false).getAsJsonObject();
                if(!Json.get(login,"success").isJsonPrimitive()||!Json.get(login,"success").getAsBoolean()||(session=Json.string(login,"sessionID","")).isBlank()){nextLogin=now+60000;throw new IOException("AMP-Anmeldung fehlgeschlagen; Benutzer, Service-Token/Passwort und API-Rechte prüfen");}
                nextLogin=0;
            }
            JsonElement data=post(config.mode.equals("controller")?"ADSModule/GetInstances":"Core/GetStatus",Map.of("SESSIONID",session),true);var instances=new LinkedHashMap<String,Instance>();
            if(config.mode.equals("instance")){var value=data.getAsJsonObject();String state=Json.string(value,"State","");if(!state.isBlank())instances.put(config.instanceTarget,new Instance(config.instanceTarget,config.instanceTarget,config.instanceTarget,state));}
            else collect(data,instances,0);
            if(instances.isEmpty())throw new IOException("AMP liefert keine Anwendungszustände; ADSModule.GetInstances-Rechte und amp.yml mode prüfen");
            var next=new Snapshot(List.copyOf(instances.values()),clock.getAsLong());if(closed)return;boolean different=!next.instances.equals(snapshot.instances);snapshot=next;error="";if(different)changed.run();
        }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();if(closed)return;error=e instanceof IOException?e.getMessage():"AMP-Antwort entspricht nicht dem erwarteten API-Format";if(now-lastWarning>60000){lastWarning=now;log.accept(error);}changed.run();}
    }
    private JsonElement post(String method,Map<String,?> data,boolean authenticated)throws IOException,InterruptedException {
        String base=config.url.toString();if(!base.toLowerCase(Locale.ROOT).endsWith("/api/"))base+="API/";var builder=HttpRequest.newBuilder(URI.create(base+method)).timeout(Duration.ofSeconds(5)).header("Accept","application/vnd.cubecoders-ampapi").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(Json.write(data)));
        if(authenticated)builder.header("Authorization","Bearer "+session);
        var response=client.send(builder.build(),HttpResponse.BodyHandlers.ofInputStream());try(var body=response.body()){
            byte[] bytes=body.readNBytes(4*1024*1024+1);if(bytes.length>4*1024*1024)throw new IOException("AMP-Antwort ist zu groß");
            if(response.statusCode()==401||response.statusCode()==403){session="";nextLogin=clock.getAsLong()+(response.statusCode()==401?1000:30000);throw new IOException("AMP-API-Zugriff abgelehnt; Session.Exists und Status-/Instanzrechte prüfen");}
            if(response.statusCode()!=200)throw new IOException("AMP-API HTTP "+response.statusCode());var result=Json.unwrap(Json.read(new String(bytes,StandardCharsets.UTF_8)));
            if(result.isJsonObject()){String title=Json.string(result.getAsJsonObject(),"Title","");if(title.toLowerCase(Locale.ROOT).contains("unauthorized")||Json.get(result.getAsJsonObject(),"StackTrace").isJsonPrimitive()){session="";nextLogin=clock.getAsLong()+30000;throw new IOException("AMP-API verweigert diese Abfrage; Instanz- und Session-Rechte prüfen");}}
            return result;
        }
    }
    private static void collect(JsonElement data,Map<String,Instance> result,int depth)throws IOException {
        if(depth>12||result.size()>1024)throw new IOException("AMP-Instanzliste ist zu komplex");
        if(data.isJsonArray()){for(var item:data.getAsJsonArray())collect(item,result,depth+1);return;}if(!data.isJsonObject())return;var object=data.getAsJsonObject();String id=Json.string(object,"InstanceID",""),name=Json.string(object,"InstanceName","");String state=Json.string(object,"AppState",Json.string(object,"State",""));
        if((!id.isBlank()||!name.isBlank())&&!state.isBlank()){if(id.isBlank())id=name;result.put(id,new Instance(id,name,Json.string(object,"FriendlyName",name),state));}
        for(var value:object.entrySet())if(value.getValue().isJsonObject()||value.getValue().isJsonArray())collect(value.getValue(),result,depth+1);
    }
    Optional<MenuSnapshot.Status> status(CentralMenuConfig.Server server){
        if(!config.enabled)return Optional.empty();String explicit=server.ampInstance();if(explicit.isBlank()&&!config.autoMatch)return Optional.empty();if(config.mode.equals("instance")&&!server.entry().target().equalsIgnoreCase(config.instanceTarget))return Optional.empty();
        Snapshot current=snapshot;if(clock.getAsLong()-current.time>config.stale*1000L)return Optional.of(MenuSnapshot.Status.UNKNOWN);
        String target=normalize(explicit.isBlank()?server.entry().target():explicit);var matches=current.instances.stream().filter(i->normalize(i.id).equals(target)||normalize(i.name).equals(target)||normalize(i.friendly).equals(target)).toList();
        if(matches.size()!=1)return Optional.of(MenuSnapshot.Status.UNKNOWN);return Optional.of(config.states.getOrDefault(normalize(matches.getFirst().state),MenuSnapshot.Status.UNKNOWN));
    }
    String diagnostic(){return !config.enabled?"AMP-API: deaktiviert (amp.yml)":"AMP-API: "+(error.isBlank()?snapshot.instances.size()+" Instanzen, letzte Abfrage vor "+Math.max(0,(clock.getAsLong()-snapshot.time)/1000)+" s":error);}
    @Override public void close(){closed=true;executor.shutdownNow();client.shutdownNow();}
}
