package de.serverhub.velocity;

import de.serverhub.common.*;
import com.velocitypowered.api.proxy.server.*;
import de.serverhub.common.MenuSnapshot.Status;
import net.kyori.adventure.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Live backend reports take precedence over the weaker server-list ping signal. */
final class ServerStatusMonitor {
    private record Observation(Status state,long time) {}
    private record Report(BackendStatus value,long received,Set<UUID> retired){}
    private final ConcurrentMap<String,Observation> states=new ConcurrentHashMap<>();
    private final ConcurrentMap<String,Integer> capacities=new ConcurrentHashMap<>();
    private final ConcurrentMap<String,Report> reports=new ConcurrentHashMap<>();
    private final Set<String> pending=ConcurrentHashMap.newKeySet();
    private final LongSupplier clock;
    private volatile UUID revision;
    private volatile Runnable changed=()->{};
    ServerStatusMonitor(){this(System::currentTimeMillis);}
    ServerStatusMonitor(LongSupplier clock){this.clock=clock;}
    void onChange(Runnable callback){changed=callback;}
    void configure(CentralMenuConfig.State config){revision=config.revision();states.clear();reports.keySet().removeIf(name->config.servers().stream().noneMatch(s->s.entry().target().equalsIgnoreCase(name)));}
    void backend(String target,BackendStatus status){
        String name=target.toLowerCase(Locale.ROOT);boolean[] notify={false};
        reports.compute(name,(key,previous)->{
            if(previous!=null){
                if(previous.value.instance().equals(status.instance())&&status.sequence()<=previous.value.sequence()||previous.retired.contains(status.instance()))return previous;
            }
            var retired=new LinkedHashSet<UUID>();if(previous!=null){retired.addAll(previous.retired);if(!previous.value.instance().equals(status.instance()))retired.add(previous.value.instance());}
            while(retired.size()>16)retired.remove(retired.iterator().next());
            notify[0]=previous==null||previous.value.paused()!=status.paused();
            return new Report(status,clock.getAsLong(),Set.copyOf(retired));
        });
        if(notify[0])changed.run();
    }
    void refresh(Collection<RegisteredServer> servers,CentralMenuConfig.State config){refresh(servers,config,RegisteredServer::ping);}
    void refresh(Collection<RegisteredServer> servers,CentralMenuConfig.State config,Function<RegisteredServer,CompletableFuture<ServerPing>> pingRequest){
        UUID expected=config.revision();if(!Objects.equals(revision,expected))return;
        Set<String> active=new HashSet<>();
        for(var server:servers){
            String name=server.getServerInfo().getName().toLowerCase(Locale.ROOT);active.add(name);
            var settings=config.servers().stream().filter(e->e.entry().target().equalsIgnoreCase(name)).findFirst().orElse(null);
            if(settings==null||!settings.override().equals("auto")||!pending.add(name))continue;
            try{
                pingRequest.apply(server).orTimeout(config.options().number("status.timeout-ms"),TimeUnit.MILLISECONDS).whenComplete((ping,error)->{
                    pending.remove(name);
                    if(Objects.equals(revision,expected)){if(error==null&&ping!=null)ping.getPlayers().ifPresent(players->capacities.put(name,players.getMax()));observe(name,classify(ping,error,settings));}
                });
            }catch(RuntimeException e){pending.remove(name);if(Objects.equals(revision,expected))observe(name,classify(null,e,settings));}
        }
        states.keySet().retainAll(active);reports.keySet().retainAll(active);capacities.keySet().retainAll(active);
    }
    int maximum(String target){return capacities.getOrDefault(target.toLowerCase(Locale.ROOT),-1);}
    private void observe(String name,Status state){var previous=states.put(name,new Observation(state,clock.getAsLong()));if(previous==null||previous.state!=state)changed.run();}
    private Report freshReport(String target,CentralMenuConfig.State config){
        if(!config.options().flag("status.backend-reports"))return null;
        var report=reports.get(target.toLowerCase(Locale.ROOT));return report!=null&&clock.getAsLong()-report.received<=config.options().number("status.heartbeat-timeout-seconds")*1000L?report:null;
    }
    Status status(CentralMenuConfig.Server server,CentralMenuConfig.State config){
        if(!server.override().equals("auto"))return Status.valueOf(server.override().toUpperCase(Locale.ROOT));
        var report=freshReport(server.entry().target(),config);if(report!=null&&report.value.paused())return Status.SLEEPING;
        var value=states.get(server.entry().target().toLowerCase(Locale.ROOT));
        Status ping=value==null||clock.getAsLong()-value.time>Math.max(3000,config.options().number("status.ping-interval-seconds")*3000L)?Status.UNKNOWN:value.state;
        return report!=null&&!report.value.paused()&&ping==Status.SLEEPING?Status.ONLINE:ping;
    }
    String details(CentralMenuConfig.Server server,CentralMenuConfig.State config){
        String target=server.entry().target();var report=reports.get(target.toLowerCase(Locale.ROOT));var ping=states.get(target.toLowerCase(Locale.ROOT));
        String backend=report==null?"noch keine Backend-Meldung":(freshReport(target,config)==null?"Backend-Meldung abgelaufen":"Backend meldet "+(report.value.paused()?"SCHLÄFT":"WACH"))+" vor "+String.format(Locale.ROOT,"%.1f",Math.max(0,clock.getAsLong()-report.received)/1000.0)+" s";
        return target+": "+status(server,config)+" · "+backend+" · Ping "+(ping==null?"ausstehend":ping.state.name());
    }
    static Status classify(ServerPing ping,Throwable error,CentralMenuConfig.Server settings){
        if(!settings.override().equals("auto"))return Status.valueOf(settings.override().toUpperCase(Locale.ROOT));
        if(error!=null||ping==null)return Status.OFFLINE;
        if(SleepSignal.paused(ping.getVersion().getName()))return Status.SLEEPING;
        String text=(plain(ping.getDescriptionComponent())+" "+ping.getVersion().getName()).toLowerCase(Locale.ROOT);
        if(settings.sleepMarkers().stream().anyMatch(marker->text.contains(marker.toLowerCase(Locale.ROOT))))return Status.SLEEPING;
        return Status.ONLINE;
    }
    private static String plain(Component c){if(c==null)return "";StringBuilder s=new StringBuilder();if(c instanceof TextComponent t)s.append(t.content());for(Component child:c.children())s.append(plain(child));return s.toString();}
}
