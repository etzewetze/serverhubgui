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
import com.velocitypowered.api.proxy.server.PingOptions;
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
    private final CentralMenuConfig menus;
    private final ServerStatusMonitor statusMonitor=new ServerStatusMonitor();
    private ScheduledTask discoveryTask;
    private BackendStatusReceiver statusReceiver;
    private boolean started;
    private final Object reloadLock=new Object();
    private final NavigatorBridge navigator;
    private volatile FontBook currentFonts=FontBook.empty();
    private final ExecutorService diagnostics=Executors.newVirtualThreadPerTaskExecutor();
    private record Link(String server,UUID nonce) {}
    private static final class Gate {
        final PackGate state;
        final Player owner;
        final String sha,url;
        final FontBook fonts;
        volatile ScheduledTask timeout;
        volatile MenuSnapshot lastMenu;
        long lastSwitch;
        Gate(Player owner,PackData pack,String url,FontBook fonts){this.fonts=fonts;this.owner=owner;this.sha=pack.sha1();this.url=url;state=new PackGate(pack.id());}
        void cancel(){if(timeout!=null)timeout.cancel();}
    }
    @Inject public ServerHubVelocity(ProxyServer proxy,Logger logger,@DataDirectory Path directory){
        this.proxy=proxy;this.logger=logger;this.directory=directory.toAbsolutePath().normalize();this.menus=new CentralMenuConfig(this.directory);this.navigator=new NavigatorBridge(proxy);
        statusMonitor.onChange(this::pushMenus);
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
            reload();started=true;scheduleDiscovery();
        }catch(Exception e){logger.error("ServerHub-Konfiguration fehlerhaft. Neue Spieler werden abgewiesen: {}",e.getMessage());}
    }
    private void copyDefault(String name) throws IOException {
        Path path=directory.resolve(name);if(Files.exists(path))return;
        try(var input=getClass().getResourceAsStream("/"+name)){
            if(input==null)throw new IOException("Mitgelieferte Datei fehlt: "+name);Files.copy(input,path);
        }
    }
    void reload() throws IOException {
        synchronized(reloadLock){
            var candidate=new CentralMenuConfig(directory).load(proxy.getAllServers().stream().map(s->s.getServerInfo().getName()).toList());
            try(var bundled=getClass().getResourceAsStream("/serverhub-pack.zip")){
                if(bundled==null)throw new IOException("Mitgeliefertes Paket fehlt");
                PackWorkspace.prepare(directory,bundled.readAllBytes(),candidate.options());
            }
            ProxySettings next=ProxySettings.read(directory);FontBook fonts=next.pack().fonts();
            new MenuSnapshot(candidate.revision(),candidate.options(),candidate.servers().stream().filter(s->!s.hidden()).map(CentralMenuConfig.Server::entry).toList(),fonts).encode();
            synchronized(this){
                if(statusReceiver!=null&&(!statusReceiver.bind.equals(candidate.options().text("status.report-bind"))||statusReceiver.configuredPort!=candidate.options().number("status.report-port")))throw new IOException("Status-Bind oder Port geändert: Proxy vollständig neu starten");
                if(host!=null&&settings!=null&&(settings.port()!=next.port()||!settings.bind().equals(next.bind())))throw new IOException("HTTP-Bind oder Port geändert: Proxy vollständig neu starten");
                if(host==null&&next.http())host=new PackHost(next.bind(),next.port(),next.pack());else if(host!=null)host.update(next.pack());
                menus.adopt(candidate);statusMonitor.configure(candidate);currentFonts=fonts;settings=next;retainHostedPacks();
                if(statusReceiver==null&&candidate.options().flag("status.backend-reports"))try{statusReceiver=new BackendStatusReceiver(directory,candidate.options().text("status.report-bind"),candidate.options().number("status.report-port"),target->proxy.getServer(target).isPresent(),statusMonitor::backend);}
                catch(IOException e){logger.warn("Backend-Statuskanal konnte nicht gestartet werden: {}. Status fällt auf Pings zurück; status.report-port in servers.yml prüfen.",e.getMessage());}
            }
            logger.info("ServerHub: Paketmodus {}, SHA-1 {}. pack/ ist die bearbeitbare Quelle; neue Paketversionen gelten für neue Netzwerkverbindungen.",next.mode(),next.pack().sha1());
            if(started){scheduleDiscovery();for(Player player:proxy.getAllPlayers())sendMenu(player);}
        }
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
        Gate next=new Gate(p,c.pack(),url,currentFonts);
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
                if(!gates.containsKey(p.getUniqueId()))offer(p); sendState(p);sendCatalog(p);sendMenu(p,true);return;
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
    private List<CentralMenuConfig.Server> visibleServers(Player p){
        var state=menus.current();if(state==null)return List.of();
        Set<String> registered=proxy.getAllServers().stream().map(s->s.getServerInfo().getName().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        return state.servers().stream().filter(s->registered.contains(s.entry().target().toLowerCase(Locale.ROOT)))
            .filter(s->!s.hidden())
            .filter(s->p.getPermissionValue("serverhub.connect."+s.entry().target())!=Tristate.FALSE)
            .filter(s->s.entry().permission().isBlank()||p.hasPermission(s.entry().permission())).limit(Wire.MAX_SERVERS).toList();
    }
    private void sendCatalog(Player p){send(p,Wire.Type.CATALOG,visibleServers(p).stream().map(s->s.entry().target()).toArray(String[]::new));}
    private void sendMenu(Player p){
        sendMenu(p,false);
    }
    private void sendMenu(Player p,boolean force){
        var state=menus.current();if(state==null)return;
        boolean integrate=state.options().flag("integration.servernavigator");
        var entries=visibleServers(p).stream().map(s->{var e=s.entry();var status=statusMonitor.status(s,state);if(integrate&&s.override().equals("auto"))status=navigator.status(e.target(),status);return new MenuSnapshot.Entry(e.target(),e.name(),e.color(),e.size(),e.permission(),e.description(),status,e.family());}).toList();
        var values=new LinkedHashMap<>(state.options().values());values.put("runtime.waiting",Boolean.toString(integrate&&navigator.waiting(p.getUniqueId())));
        Gate gate=gates.get(p.getUniqueId());FontBook fonts=gate==null?currentFonts:gate.fonts;
        try{var snapshot=new MenuSnapshot(state.revision(),new MenuOptions(values),entries,fonts);if(!force&&gate!=null&&snapshot.equals(gate.lastMenu))return;send(p,Wire.Type.MENU,snapshot.encode());if(gate!=null)gate.lastMenu=snapshot;}
        catch(IllegalArgumentException e){logger.warn("Zentrale Menükonfiguration kann nicht übertragen werden: {}",e.getMessage());}
    }
    private void pushMenus(){if(menus.current()!=null)for(Player player:proxy.getAllPlayers())sendMenu(player);}
    private void scheduleDiscovery(){
        if(discoveryTask!=null)discoveryTask.cancel();
        discoveryTask=proxy.getScheduler().buildTask(this,()->{
            try{
                var registered=proxy.getAllServers();
                refreshRegistered();
            }catch(IOException e){logger.warn("Neue Server konnten nicht in servers.yml ergänzt werden: {}",e.getMessage());}
            var menu=menus.current();
            if(menu.options().flag("status.enabled"))statusMonitor.refresh(proxy.getAllServers(),menu,server->{
                var receiver=statusReceiver;
                if(receiver==null||!menu.options().flag("status.backend-reports"))return server.ping();
                return server.ping(PingOptions.builder().virtualHost(receiver.link(server.getServerInfo().getName(),menu.options()).virtualHost()).timeout(Duration.ofMillis(menu.options().number("status.timeout-ms"))).build());
            });
            pushMenus();
        }).delay(Duration.ofSeconds(1)).repeat(Duration.ofSeconds(menus.current().options().number("status.ping-interval-seconds"))).schedule();
    }
    void refreshRegistered()throws IOException {
        if(menus.discover(proxy.getAllServers().stream().map(s->s.getServerInfo().getName()).toList()))statusMonitor.configure(menus.current());
    }
    private void send(Player p,Wire.Type type,String... values){
        Link link=links.get(p.getUniqueId());if(link==null)return;
        p.getCurrentServer().filter(c->c.getServerInfo().getName().equals(link.server()))
            .ifPresent(c->c.sendPluginMessage(CHANNEL,Wire.encode(type,link.nonce(),values)));
    }
    private void connect(Player p,String target){
        Gate gate=gates.get(p.getUniqueId());
        if(gate==null||!gate.state.ready()){send(p,Wire.Type.RESULT,"loading");return;}
        synchronized(gate){long now=System.currentTimeMillis();if(now-gate.lastSwitch<700)return;gate.lastSwitch=now;}
        if(!target.matches("[A-Za-z0-9_.-]{1,64}")){send(p,Wire.Type.RESULT,"denied");return;}
        if(p.getPermissionValue("serverhub.connect."+target)==Tristate.FALSE){send(p,Wire.Type.RESULT,"denied");return;}
        if(visibleServers(p).stream().noneMatch(s->s.entry().target().equalsIgnoreCase(target))){send(p,Wire.Type.RESULT,"denied");return;}
        var registered=proxy.getServer(target);
        if(registered.isEmpty()){send(p,Wire.Type.RESULT,"unavailable");return;}
        if(p.getCurrentServer().filter(c->c.getServer().equals(registered.get())).isPresent()){send(p,Wire.Type.RESULT,"already");return;}
        if(menus.current().options().flag("integration.servernavigator")){
            var result=navigator.navigate(p,target);
            if(result==NavigatorBridge.Result.HANDLED){sendMenu(p);return;}
            if(result==NavigatorBridge.Result.UNSUPPORTED||result==NavigatorBridge.Result.FAILED){send(p,Wire.Type.RESULT,"navigator");return;}
        }
        p.createConnectionRequest(registered.get()).connect().whenComplete((result,error)->{
            if(error!=null||result==null||!result.isSuccessful())send(p,Wire.Type.RESULT,"unavailable");
        });
    }
    @Subscribe public void shutdown(ProxyShutdownEvent event){
        for(Gate g:gates.values())g.cancel();gates.clear();links.clear();
        if(host!=null)host.close();
        if(statusReceiver!=null)statusReceiver.close();
        if(discoveryTask!=null)discoveryTask.cancel();
        diagnostics.shutdownNow();
    }
    private final class AdminCommand implements SimpleCommand {
        @Override public boolean hasPermission(Invocation i){return !(i.source() instanceof Player)||i.source().hasPermission("serverhub.proxy.reload");}
        @Override public void execute(Invocation i){
            if(!hasPermission(i))return;
            if(i.arguments().length!=1){i.source().sendMessage(Component.text("/serverhubproxy reload | status | packcheck"));return;}
            if(i.arguments()[0].equalsIgnoreCase("reload")) {
                i.source().sendMessage(Component.text("Prüfe Konfiguration und packe Ressourcen …",NamedTextColor.YELLOW));
                diagnostics.submit(()->{try{reload();i.source().sendMessage(Component.text("ServerHub-Proxy und Ressourcen neu geladen. Bei geändertem Paket neu verbinden.",NamedTextColor.GREEN));}
                catch(Exception e){logger.warn("ServerHub-Reload abgelehnt: {}",e.getMessage());i.source().sendMessage(Component.text("Reload abgelehnt: "+e.getMessage(),NamedTextColor.RED));}});
            }else if(i.arguments()[0].equalsIgnoreCase("status")){
                var c=settings;
                i.source().sendMessage(Component.text(c==null?"ServerHub-Konfiguration fehlerhaft.":"Paketpflicht aktiv; Modus "+c.mode()+"; SHA-1 "+c.pack().sha1()+"; Netzwerksitzungen "+gates.size()+"; zentrale Datei servers.yml; erfasste Ziele "+menus.current().servers().size()));
                var state=menus.current();if(state!=null){i.source().sendMessage(Component.text("Statusintervall: "+state.options().number("status.ping-interval-seconds")+" s; Backend-Statuskanal: "+(statusReceiver==null?"nicht aktiv":statusReceiver.port()+"/TCP")));for(var server:state.servers())i.source().sendMessage(Component.text(statusMonitor.details(server,state)));}
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
