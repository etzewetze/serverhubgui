package de.serverhub.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.*;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.*;
import com.velocitypowered.api.event.proxy.*;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.messages.*;
import com.velocitypowered.api.scheduler.ScheduledTask;
import de.serverhub.common.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import com.velocitypowered.api.permission.Tristate;
import org.slf4j.Logger;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public final class ServerHubVelocity {
    private static final MinecraftChannelIdentifier CHANNEL=MinecraftChannelIdentifier.from(Wire.CHANNEL);
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path directory;
    private final ConcurrentMap<UUID,Gate> gates=new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID,Link> links=new ConcurrentHashMap<>();
    private volatile ProxySettings settings;
    private PackHost host;
    private final ExecutorService diagnostics=Executors.newVirtualThreadPerTaskExecutor();
    private record Link(String server,UUID nonce) {}
    private static final class Gate {
        final PackGate state;
        final Player owner;
        final String sha,url;
        volatile ScheduledTask timeout;
        long lastSwitch;
        Gate(Player owner,PackData pack,String url){this.owner=owner;this.sha=pack.sha1();this.url=url;state=new PackGate(pack.id());}
        void cancel(){if(timeout!=null)timeout.cancel();}
    }
    @Inject public ServerHubVelocity(ProxyServer proxy,Logger logger,@DataDirectory Path directory){
        this.proxy=proxy;this.logger=logger;this.directory=directory.toAbsolutePath().normalize();
    }
    @Subscribe public void initialize(ProxyInitializeEvent event){
        proxy.getChannelRegistrar().register(CHANNEL);
        proxy.getCommandManager().register(proxy.getCommandManager().metaBuilder("serverhubproxy").plugin(this).build(),new AdminCommand());
        try {
            Files.createDirectories(directory);
            try(var defaults=getClass().getResourceAsStream("/config.properties")){
                if(defaults==null)throw new IOException("Standardkonfiguration fehlt");
                if(ProxyConfigFiles.migrate(directory,defaults.readAllBytes()))logger.info("Alte 2.0-Konfiguration gesichert; Paket wird jetzt über den GitHub-Release geladen.");
            }
            copyDefault("serverhub-pack.zip");
            reload();
        }catch(Exception e){logger.error("ServerHub-Konfiguration fehlerhaft. Neue Spieler werden abgewiesen: {}",e.getMessage());}
    }
    private void copyDefault(String name) throws IOException {
        Path path=directory.resolve(name);if(Files.exists(path))return;
        try(var input=getClass().getResourceAsStream("/"+name)){
            if(input==null)throw new IOException("Mitgelieferte Datei fehlt: "+name);Files.copy(input,path);
        }
    }
    synchronized void reload() throws IOException {
        ProxySettings next=ProxySettings.read(directory),old=settings;
        if(old!=null&&(old.http()!=next.http()||old.port()!=next.port()||!old.bind().equals(next.bind())))
            throw new IOException("HTTP-Bind, Port oder Aktivierung geändert: Proxy vollständig neu starten");
        if(host==null&&next.http())host=new PackHost(next.bind(),next.port(),next.pack());
        else if(host!=null)host.update(next.pack());
        settings=next;
        logger.info("ServerHub: Modus {}, SHA-1 {}, Paket-ID {}. Genau ein Angebot pro Netzwerkbeitritt; Reload fordert keine Pakete erneut an.",next.mode(),next.pack().sha1(),next.pack().id());
        retainHostedPacks();
    }
    private void retainHostedPacks(){if(host!=null)host.retain(gates.values().stream().map(g->g.sha).collect(java.util.stream.Collectors.toSet()));}
    private Component kickText(){var c=settings;return Component.text(c==null?"ServerHub ist noch nicht korrekt konfiguriert. Bitte informiere das Serverteam.":c.kick(),NamedTextColor.RED);}
    private synchronized void offer(Player p){
        ProxySettings c=settings;
        if(c==null) {
            p.disconnect(Component.text("Das erforderliche Menüpaket ist serverseitig noch nicht eingerichtet. Bitte informiere das Serverteam.",NamedTextColor.RED));return;
        }
        // Post-connect and the first Paper HELLO may run concurrently.
        Gate existing=gates.get(p.getUniqueId());
        if(existing!=null){sendState(p);return;}
        String url;
        try{url=c.url(p.getVirtualHost().map(address->address.getHostString()).orElse(null));}
        catch(IllegalArgumentException e){logger.error("Paketadresse für {} ungültig: {}",p.getUsername(),e.getMessage());p.disconnect(kickText());return;}
        Gate next=new Gate(p,c.pack(),url);
        gates.put(p.getUniqueId(),next);
        sendState(p);
        next.timeout=proxy.getScheduler().buildTask(this,()->{
            if(p.isActive()&&gates.get(p.getUniqueId())==next&&!next.state.ready())p.disconnect(kickText());
        }).delay(Duration.ofSeconds(c.timeout())).schedule();
        try {
            p.sendResourcePackOffer(proxy.createResourcePackBuilder(url).setId(c.pack().id())
                .setHash(HexFormat.of().parseHex(c.pack().sha1())).setShouldForce(true)
                .setPrompt(Component.text(c.prompt())).build());
        }catch(RuntimeException e){logger.warn("Paketangebot fehlgeschlagen: {}",e.getMessage());p.disconnect(kickText());}
    }
    @Subscribe public void connected(ServerPostConnectEvent event){
        Player p=event.getPlayer();
        if(!gates.containsKey(p.getUniqueId()))offer(p);else sendState(p);
    }
    @Subscribe(order=PostOrder.LAST) public void beforeConnect(ServerPreConnectEvent event){
        // The first backend is needed to display the resource-pack prompt in play state.
        if(event.getPlayer().getCurrentServer().isEmpty())return;
        Gate gate=gates.get(event.getPlayer().getUniqueId());
        if(gate==null||!gate.state.ready()) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
            event.getPlayer().sendMessage(Component.text("Bitte warte, bis das erforderliche Menüpaket geladen ist.",NamedTextColor.YELLOW));
        }
    }
    @Subscribe public void packStatus(PlayerResourcePackStatusEvent event){
        Gate gate=gates.get(event.getPlayer().getUniqueId());
        if(gate==null||!gate.state.accept(event.getPackId(),event.getStatus().name()))return;
        if(gate.state.ready()){gate.cancel();sendState(event.getPlayer());}
        else {
            gate.cancel();sendState(event.getPlayer());
            logger.warn("ServerHub-Paket für {}: {}. URL: {}. Diagnose: /serverhubproxy packcheck",event.getPlayer().getUsername(),event.getStatus(),gate.url);
            event.getPlayer().disconnect(kickText());
        }
    }
    @Subscribe public void disconnected(DisconnectEvent event){
        UUID id=event.getPlayer().getUniqueId();
        gates.computeIfPresent(id,(key,old)->{if(old.owner!=event.getPlayer())return old;old.cancel();links.remove(id);return null;});
        retainHostedPacks();
    }
    @Subscribe public void pluginMessage(PluginMessageEvent event){
        if(!CHANNEL.equals(event.getIdentifier()))return;
        // Must happen BEFORE checking the source: client packets never reach a backend.
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if(!(event.getSource() instanceof ServerConnection backend))return;
        Player p=backend.getPlayer();
        if(p.getCurrentServer().filter(current->current.getServer().equals(backend.getServer())).isEmpty())return;
        try {
            Wire.Packet packet=Wire.decode(event.getData());
            if(packet.type()==Wire.Type.HELLO){
                links.put(p.getUniqueId(),new Link(backend.getServerInfo().getName(),packet.nonce()));
                if(!gates.containsKey(p.getUniqueId()))offer(p); sendState(p);return;
            }
            Link link=links.get(p.getUniqueId());
            if(link==null||!link.nonce().equals(packet.nonce())||!link.server().equals(backend.getServerInfo().getName()))return;
            if(packet.type()==Wire.Type.CONNECT)connect(p,packet.value(0));
        }catch(IOException|IllegalArgumentException e){logger.debug("Ungültige ServerHub-Nachricht verworfen");}
    }
    private void sendState(Player p){
        Gate gate=gates.get(p.getUniqueId());
        send(p,Wire.Type.STATE,Boolean.toString(gate!=null&&gate.state.ready()));
    }
    private void send(Player p,Wire.Type type,String value){
        Link link=links.get(p.getUniqueId());if(link==null)return;
        p.getCurrentServer().filter(c->c.getServerInfo().getName().equals(link.server()))
            .ifPresent(c->c.sendPluginMessage(CHANNEL,Wire.encode(type,link.nonce(),value)));
    }
    private void connect(Player p,String target){
        Gate gate=gates.get(p.getUniqueId());
        if(gate==null||!gate.state.ready()){send(p,Wire.Type.RESULT,"loading");return;}
        synchronized(gate){long now=System.currentTimeMillis();if(now-gate.lastSwitch<700)return;gate.lastSwitch=now;}
        if(!target.matches("[A-Za-z0-9_.-]{1,64}")){send(p,Wire.Type.RESULT,"denied");return;}
        if(p.getPermissionValue("serverhub.connect."+target)==Tristate.FALSE){send(p,Wire.Type.RESULT,"denied");return;}
        var registered=proxy.getServer(target);
        if(registered.isEmpty()){send(p,Wire.Type.RESULT,"unavailable");return;}
        if(p.getCurrentServer().filter(c->c.getServer().equals(registered.get())).isPresent()){send(p,Wire.Type.RESULT,"already");return;}
        p.createConnectionRequest(registered.get()).connect().whenComplete((result,error)->{
            if(error!=null||result==null||!result.isSuccessful())send(p,Wire.Type.RESULT,"unavailable");
        });
    }
    @Subscribe public void shutdown(ProxyShutdownEvent event){
        for(Gate g:gates.values())g.cancel();gates.clear();links.clear();
        if(host!=null)host.close();
        diagnostics.shutdownNow();
    }
    private final class AdminCommand implements SimpleCommand {
        @Override public boolean hasPermission(Invocation i){return !(i.source() instanceof Player)||i.source().hasPermission("serverhub.proxy.reload");}
        @Override public void execute(Invocation i){
            if(!hasPermission(i))return;
            if(i.arguments().length!=1){i.source().sendMessage(Component.text("/serverhubproxy reload | status | packcheck"));return;}
            if(i.arguments()[0].equalsIgnoreCase("reload")) {
                try{reload();i.source().sendMessage(Component.text("ServerHub-Proxy neu geladen.",NamedTextColor.GREEN));}
                catch(Exception e){logger.warn("ServerHub-Reload abgelehnt: {}",e.getMessage());i.source().sendMessage(Component.text("Reload abgelehnt: "+e.getMessage(),NamedTextColor.RED));}
            }else if(i.arguments()[0].equalsIgnoreCase("status")){
                var c=settings;
                i.source().sendMessage(Component.text(c==null?"ServerHub-Konfiguration fehlerhaft.":"Paketpflicht aktiv; Modus "+c.mode()+"; SHA-1 "+c.pack().sha1()+"; Netzwerksitzungen "+gates.size()));
            }else if(i.arguments()[0].equalsIgnoreCase("packcheck")){
                var c=settings;if(c==null){i.source().sendMessage(Component.text("ServerHub-Konfiguration fehlerhaft.",NamedTextColor.RED));return;}
                String joiningHost=i.source() instanceof Player player?player.getVirtualHost().map(a->a.getHostString()).orElse(null):null;
                String url;
                try{url=c.url(joiningHost);}catch(IllegalArgumentException e){i.source().sendMessage(Component.text(e.getMessage()+"; bei auto den Befehl im Spiel verwenden oder self-host.public-host fest setzen.",NamedTextColor.RED));return;}
                i.source().sendMessage(Component.text("Prüfe Paket vom Proxy aus: "+url,NamedTextColor.YELLOW));
                diagnostics.submit(()->{
                    var result=PackProbe.check(url,c.pack().sha1());
                    i.source().sendMessage(Component.text(result.detail(),result.success()?NamedTextColor.GREEN:NamedTextColor.RED));
                    logger.info("ServerHub packcheck: {}",result.detail());
                });
            }
        }
        @Override public List<String> suggest(Invocation i){return List.of("reload","status","packcheck");}
    }
}
