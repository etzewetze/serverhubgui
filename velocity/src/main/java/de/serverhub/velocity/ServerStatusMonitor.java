package de.serverhub.velocity;

import com.velocitypowered.api.proxy.server.*;
import de.serverhub.common.MenuSnapshot.Status;
import net.kyori.adventure.text.*;
import java.util.*;
import java.util.concurrent.*;

/** Pings prove reachability, not an empty server's sleep state. */
final class ServerStatusMonitor {
    private record Observation(Status state,long time) {}
    private final ConcurrentMap<String,Observation> states=new ConcurrentHashMap<>();
    private final Set<String> pending=ConcurrentHashMap.newKeySet();
    private volatile UUID revision;
    void configure(CentralMenuConfig.State config){revision=config.revision();states.clear();}
    void refresh(Collection<RegisteredServer> servers,CentralMenuConfig.State config){
        UUID expected=config.revision();
        if(!Objects.equals(revision,expected))return;
        Set<String> active=new HashSet<>();
        for(var server:servers){
            String name=server.getServerInfo().getName().toLowerCase(Locale.ROOT);active.add(name);
            var settings=config.servers().stream().filter(e->e.entry().target().equalsIgnoreCase(name)).findFirst().orElse(null);
            if(settings==null||!settings.override().equals("auto")||!pending.add(name))continue;
            try{
                server.ping().orTimeout(config.options().number("status.timeout-ms"),TimeUnit.MILLISECONDS).whenComplete((ping,error)->{
                    pending.remove(name);
                    if(Objects.equals(revision,expected))states.put(name,new Observation(classify(ping,error,settings),System.currentTimeMillis()));
                });
            }catch(RuntimeException e){pending.remove(name);if(Objects.equals(revision,expected))states.put(name,new Observation(classify(null,e,settings),System.currentTimeMillis()));}
        }
        states.keySet().retainAll(active);
    }
    Status status(CentralMenuConfig.Server server,CentralMenuConfig.State config){
        if(!server.override().equals("auto"))return Status.valueOf(server.override().toUpperCase(Locale.ROOT));
        var value=states.get(server.entry().target().toLowerCase(Locale.ROOT));
        return value==null||System.currentTimeMillis()-value.time>config.options().number("status.ping-interval-seconds")*3000L?Status.UNKNOWN:value.state;
    }
    static Status classify(ServerPing ping,Throwable error,CentralMenuConfig.Server settings){
        if(!settings.override().equals("auto"))return Status.valueOf(settings.override().toUpperCase(Locale.ROOT));
        if(error!=null||ping==null)return Status.OFFLINE;
        String text=(plain(ping.getDescriptionComponent())+" "+ping.getVersion().getName()).toLowerCase(Locale.ROOT);
        if(settings.sleepMarkers().stream().anyMatch(marker->text.contains(marker.toLowerCase(Locale.ROOT))))return Status.SLEEPING;
        return Status.ONLINE;
    }
    private static String plain(Component c){if(c==null)return "";StringBuilder s=new StringBuilder();if(c instanceof TextComponent t)s.append(t.content());for(Component child:c.children())s.append(plain(child));return s.toString();}
}
