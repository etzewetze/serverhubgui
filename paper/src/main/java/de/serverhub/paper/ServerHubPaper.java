package de.serverhub.paper;

import de.serverhub.common.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.*;

public final class ServerHubPaper extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void sleepPing(com.destroystokyo.paper.event.server.PaperServerListPingEvent event){
        event.setVersion(SleepSignal.version(event.getVersion(),getServer().isPaused()));
        var reporter=backendReporter;
        if(proxyExpected&&reporter!=null){var virtual=event.getClient().getVirtualHost();if(virtual!=null)BackendReportLink.fromVirtualHost(virtual.getHostString()).ifPresent(link->reporter.configure(link,event.getAddress().getHostAddress()));}
    }
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private Settings settings;
    private GuiText gui;
    private NamespacedKey iconKey;
    private long ticks;
    private boolean proxyExpected;
    private LocalPackDelivery localDelivery;
    private BackendReporter backendReporter;
    private static final class Session {
        final UUID nonce = UUID.randomUUID();
        final long joined = System.currentTimeMillis();
        boolean acknowledged, ready, fullWarning, discovered;
        List<String> catalog = List.of();
        UUID menuToken,revision;
        Settings central;
        PackGate localGate;
        PackData localPack;
        FontBook localFonts;
        LocalPackDelivery.State localState;
        long lastOpen, lastSwitch;
        int menuPage;
    }
    @Override public void onEnable() {
        saveDefaultConfig();
        try {
            File config = new File(getDataFolder(), "config.yml");
            var bootstrap=new org.bukkit.configuration.file.YamlConfiguration();
            try{bootstrap.load(config);}catch(Exception e){getLogger().warning("Alte Backend-Konfiguration wird ignoriert; automatische Netzwerkerkennung bleibt aktiv.");}
            proxyExpected=NetworkMode.proxied(java.nio.file.Path.of("."),bootstrap.getString("network.mode","auto"));
            settings=Settings.fromNetwork(new MenuSnapshot(UUID.randomUUID(),MenuOptions.defaults(),List.of()));
            gui=new GuiText();
            if(proxyExpected){backendReporter=new BackendReporter(getDataFolder().toPath(),getLogger()::warning);backendReporter.sample(getServer().isPaused());}
            if(!proxyExpected)try(var input=getResource("serverhub-pack.zip")){if(input==null)throw new java.io.IOException("Paket fehlt");localDelivery=new LocalPackDelivery(getDataFolder().toPath(),input.readAllBytes());}
            getLogger().info(proxyExpected?"Netzwerkmodus: Menüeinstellungen werden automatisch vom Proxy übernommen.":"Eigenständiger Server: lokale servers.yml und pack/ aktiv; kein Proxy-Timeout.");
        }
        catch (Exception e) { getLogger().severe("ServerHub kann nicht starten: " + e.getMessage()); getServer().getPluginManager().disablePlugin(this); return; }
        iconKey = new NamespacedKey(this, "hotbar-menu");
        Objects.requireNonNull(getCommand("extramenu")).setExecutor(this);
        Objects.requireNonNull(getCommand("extramenu")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this,this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, Wire.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, Wire.CHANNEL, (channel,player,data) -> {
            byte[] copy = data.clone();
            getServer().getScheduler().runTask(this, () -> incoming(player,copy));
        });
        Bukkit.getOnlinePlayers().forEach(this::startSession);
        getServer().getScheduler().runTaskTimer(this,this::tick,20,20);
    }
    @Override public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) { Session s=sessions.get(p.getUniqueId()); if(s!=null&&s.menuToken!=null)p.closeDialog(); removeIcons(p); }
        sessions.clear();if(localDelivery!=null)localDelivery.close();if(backendReporter!=null)backendReporter.close();
    }
    private Component text(String text) { return LEGACY.deserialize(text).decoration(TextDecoration.ITALIC,false); }
    private Component message(String key) { return text(settings.yaml().getString("messages."+key, "&cServerHub: "+key)); }
    private Settings effective(Player p){Session s=sessions.get(p.getUniqueId());return s!=null&&s.central!=null?s.central:settings;}
    private Component message(Player p,String key){String fallback=MenuOptions.defaults().text("messages."+key);return text(effective(p).yaml().getString("messages."+key,fallback==null?"&cServerHub: "+key:fallback));}
    private void startSession(Player p) {
        Session session=new Session();sessions.put(p.getUniqueId(),session);
        if(!proxyExpected){session.acknowledged=true;session.localState=localDelivery.state();session.localPack=session.localState.pack();try{session.localFonts=session.localPack.fonts();session.central=localSettings(session);}catch(java.io.IOException e){p.kick(text("&cLokale Paketdaten ungültig."));return;}session.localGate=new PackGate(session.localPack.id());
            Bukkit.getScheduler().runTaskLater(this,()->offerLocal(p,session),10);}
        applyIcon(p);
        Bukkit.getScheduler().runTaskLater(this, () -> hello(p),10);
    }
    private Settings localSettings(Session session){
        var menu=localDelivery.state().menu();
        var entries=menu.servers().stream().filter(server->!server.hidden()&&server.entry().target().equals("local")).map(server->{var e=server.entry();return new MenuSnapshot.Entry(e.target(),e.name(),e.color(),e.size(),e.permission(),e.description(),MenuSnapshot.Status.ONLINE,e.family());}).toList();
        return Settings.fromNetwork(new MenuSnapshot(menu.revision(),menu.options(),entries,session.localFonts));
    }
    private void offerLocal(Player player,Session session){
        if(!player.isOnline()||sessions.get(player.getUniqueId())!=session)return;
        var state=session.localState;
        try{var address=player.getVirtualHost();String host=address==null?null:address.getHostString();
            String url=localDelivery.url(state,host);
            player.addResourcePack(session.localPack.id(),url,HexFormat.of().parseHex(session.localPack.sha1()),"Dieses Menü benötigt das ServerHub-Paket.",true);
            Bukkit.getScheduler().runTaskLater(this,()->{if(player.isOnline()&&sessions.get(player.getUniqueId())==session&&!session.ready)player.kick(message(player,"loading"));},state.timeout()*20L);
        }catch(IllegalArgumentException e){getLogger().warning("Lokale Paketadresse ungültig: "+e.getMessage());player.kick(message(player,"loading"));}
    }
    @EventHandler public void localPackStatus(PlayerResourcePackStatusEvent event){
        if(proxyExpected)return;Player player=event.getPlayer();Session session=sessions.get(player.getUniqueId());
        if(session==null||session.localGate==null||!session.localGate.accept(event.getID(),event.getStatus().name()))return;
        session.ready=session.localGate.ready();if(!session.ready)player.kick(message(player,"loading"));else applyIcon(player);
    }
    private void retainLocal(){if(localDelivery!=null)localDelivery.retain(sessions.values().stream().filter(s->s.localPack!=null).map(s->s.localPack.sha1()).collect(java.util.stream.Collectors.toSet()));}
    private void hello(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (proxyExpected && s != null && p.isOnline()) p.sendPluginMessage(this,Wire.CHANNEL,Wire.encode(Wire.Type.HELLO,s.nonce));
    }
    private void incoming(Player p, byte[] data) {
        Session s = sessions.get(p.getUniqueId()); if (!proxyExpected || s == null || !p.isOnline()) return;
        try {
            Wire.Packet packet = Wire.decode(data);
            if (!s.nonce.equals(packet.nonce())) return;
            if (packet.type() == Wire.Type.STATE) {
                boolean wasReady = s.ready;
                s.acknowledged = true; s.ready = Boolean.parseBoolean(packet.value(0));
                if (s.ready != wasReady) {
                    if (!s.ready && s.menuToken!=null) { s.menuToken=null; p.closeDialog(); }
                    applyIcon(p);
                }
            } else if(packet.type()==Wire.Type.MENU){
                MenuSnapshot snapshot=MenuSnapshot.decode(packet.values());
                Settings next=Settings.fromNetwork(snapshot);
                boolean redraw=s.menuToken!=null&&next.menuOptions().flag("status.refresh-open-menu")&&s.central!=null&&(!s.central.entries().equals(next.entries())||!s.central.menuOptions().equals(next.menuOptions()));
                if(!snapshot.revision().equals(s.revision))s.menuToken=null;
                s.revision=snapshot.revision();s.central=next;s.catalog=snapshot.entries().stream().map(MenuSnapshot.Entry::target).toList();s.discovered=true;
                applyIcon(p);
                if(redraw)open(p,s.menuPage);
            } else if (packet.type() == Wire.Type.CATALOG) {
                s.catalog = packet.values(); s.discovered = true;
            } else if (packet.type() == Wire.Type.RESULT) {
                String key = switch(packet.value(0)) { case "already" -> "already-connected"; case "denied" -> "denied"; case "loading" -> "loading";case "navigator" -> "navigator-unavailable"; default -> "unavailable"; };
                p.sendMessage(message(p,key));
            }
        } catch (Exception ignored) { /* Untrusted or out-of-date message. */ }
    }
    private void tick() {
        ticks++;
        if(backendReporter!=null)backendReporter.sample(getServer().isPaused());
        for (Player p : Bukkit.getOnlinePlayers()) {
            Session s = sessions.get(p.getUniqueId()); if (s == null) continue;
            Settings config=effective(p);
            if (proxyExpected && (!s.acknowledged||s.central==null) && System.currentTimeMillis()-s.joined > config.handshakeTimeout()*1000L) { p.kick(message(p,"proxy-missing")); continue; }
            if (proxyExpected && (ticks % 5 == 0 || !s.acknowledged)) hello(p);
            if (ready(p) && p.hasPermission("serverhub.use") && config.hint() && !config.menuOptions().flag("runtime.waiting") && ticks % config.hintInterval() == 0
                && p.getOpenInventory().getType() == InventoryType.CRAFTING) {
                p.sendActionBar(gui.hint(config.yaml().getInt("shift-f-hint.x-offset",106),
                    config.yaml().getInt("shift-f-hint.y-offset",0)));
            }
        }
    }
    private boolean ready(Player p) {
        Session s = sessions.get(p.getUniqueId()); return s != null && s.ready && s.central!=null;
    }
    private void open(Player p,int requested) {
        if(p.isDead())return;
        if(!p.hasPermission("serverhub.use")){p.sendMessage(message(p,"no-permission"));return;}
        Session s=sessions.get(p.getUniqueId());
        if(s==null||s.central==null){hello(p);p.sendMessage(message(p,"discovering"));return;}
        if(!ready(p)){p.sendMessage(message(p,"loading"));return;}
        Settings config=effective(p);MenuOptions options=config.menuOptions();
        var entries=config.entries().stream().filter(e->e.permission().isBlank()||p.hasPermission(e.permission())).toList();
        int page=options.scrolling()?0:MenuLayout.clampPage(requested,entries.size(),options.number("menu.page-size"));
        UUID token=UUID.randomUUID();s.menuToken=token;s.menuPage=page;
        // Never place this dialog over a container: closing returns directly to the game.
        p.closeInventory();
        DialogMenu.show(p,gui.forSettings(config),config,entries,page,action->Bukkit.getScheduler().runTask(this,()->{
            if(!p.isOnline()||p.isDead()||sessions.get(p.getUniqueId())!=s||!token.equals(s.menuToken))return;
            if(!ready(p)||!p.hasPermission("serverhub.use")){s.menuToken=null;p.closeDialog();return;}
            s.menuToken=null;action.run();
        }),entry->choose(p,s,entry),next->open(p,next));
    }
    private void choose(Player p,Session s,Settings.Entry entry){
        if(!entry.permission().isBlank()&&!p.hasPermission(entry.permission())){p.sendMessage(message(p,"no-permission"));return;}
        if(s.central==null||s.central.entries().stream().noneMatch(e->e.target().equalsIgnoreCase(entry.target()))) {
            p.sendMessage(message(p,"unavailable"));return;
        }
        long now=System.currentTimeMillis();if(now-s.lastSwitch<700)return;s.lastSwitch=now;
        p.closeDialog();
        if(!proxyExpected){p.sendMessage(message(p,"already-connected"));return;}
        p.sendPluginMessage(this,Wire.CHANNEL,Wire.encode(Wire.Type.CONNECT,s.nonce,entry.target()));
    }
    private boolean isIcon(ItemStack item) {
        return iconKey != null && item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(iconKey,PersistentDataType.BYTE);
    }
    private void removeIcons(Player p) {
        PlayerInventory inv=p.getInventory();
        for(int i=0;i<inv.getSize();i++) if(isIcon(inv.getItem(i)))inv.setItem(i,null);
    }
    private void applyIcon(Player p) {
        removeIcons(p);
        Settings config=effective(p);
        if(!ready(p) || !config.icon() || !p.hasPermission("serverhub.use") || p.isDead())return;
        PlayerInventory inv=p.getInventory(); int slot=config.slot();
        if(!HotbarSlot.makeSpace(inv,slot)) {
                Session s=sessions.get(p.getUniqueId());
                if(s!=null&&!s.fullWarning){p.sendMessage(message(p,"full-inventory"));s.fullWarning=true;} return;
        }
        ItemStack item=new ItemStack(Material.COMPASS); ItemMeta meta=item.getItemMeta();
        meta.getPersistentDataContainer().set(iconKey,PersistentDataType.BYTE,(byte)1);
        if(ready(p))meta.setItemModel(new NamespacedKey("serverhub","menu_icon"));
        meta.displayName(text(ready(p)?"&#E8C78FServermenü &7(Rechtsklick)":"&eMenüpaket wird geladen …"));
        meta.lore(List.of(text("&7Shift + F öffnet ebenfalls das Menü.")));
        item.setItemMeta(meta); inv.setItem(slot,item);
    }
    @EventHandler public void join(PlayerJoinEvent e){startSession(e.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent e){removeIcons(e.getPlayer());sessions.remove(e.getPlayer().getUniqueId());retainLocal();}
    @EventHandler public void respawn(PlayerRespawnEvent e){Bukkit.getScheduler().runTaskLater(this,()->applyIcon(e.getPlayer()),1);}
    @EventHandler public void death(PlayerDeathEvent e){e.getDrops().removeIf(this::isIcon);Session session=sessions.get(e.getEntity().getUniqueId());if(session!=null)session.menuToken=null;}
    @EventHandler(ignoreCancelled=true) public void swap(PlayerSwapHandItemsEvent e){
        Player p=e.getPlayer();
        if(p.isSneaking()) {
            e.setCancelled(true); Session s=sessions.get(p.getUniqueId()); long now=System.currentTimeMillis();
            if(s!=null&&now-s.lastOpen>200){s.lastOpen=now;Bukkit.getScheduler().runTask(this,()->open(p,0));}
        } else if(isIcon(e.getMainHandItem())||isIcon(e.getOffHandItem())) e.setCancelled(true);
    }
    @EventHandler public void rightClick(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||!isIcon(e.getItem())||(e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK))return;
        e.setCancelled(true); open(e.getPlayer(),0);
    }
    @EventHandler(ignoreCancelled=true) public void drop(PlayerDropItemEvent e){if(isIcon(e.getItemDrop().getItemStack()))e.setCancelled(true);}
    @EventHandler public void drag(InventoryDragEvent e){
        if(isIcon(e.getOldCursor())){e.setCancelled(true);return;}
        for(int raw:e.getRawSlots())if(isIcon(e.getView().getItem(raw))){e.setCancelled(true);return;}
    }
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        if(isIcon(e.getCurrentItem())||isIcon(e.getCursor())
            ||e.getHotbarButton()>=0&&isIcon(p.getInventory().getItem(e.getHotbarButton()))
            ||e.getClick()==ClickType.SWAP_OFFHAND&&isIcon(p.getInventory().getItemInOffHand()))e.setCancelled(true);
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(args.length==1&&args[0].equalsIgnoreCase("reload")) {
            if(!sender.hasPermission("serverhub.reload")){sender.sendMessage(message("no-permission"));return true;}
            if(proxyExpected)sender.sendMessage(text("&eNetzwerkmodus: servers.yml und pack/ am Proxy bearbeiten und dort /serverhubproxy reload ausführen."));
            else Bukkit.getScheduler().runTaskAsynchronously(this,()->{try{localDelivery.reload();Bukkit.getScheduler().runTask(this,()->{for(Player p:Bukkit.getOnlinePlayers()){Session session=sessions.get(p.getUniqueId());if(session!=null){session.central=localSettings(session);session.menuToken=null;applyIcon(p);}}retainLocal();sender.sendMessage(text("&aLokale Einstellungen und pack/ neu geladen. Nach Paketänderungen neu verbinden."));});}catch(Exception e){sender.sendMessage(text("&cReload abgelehnt: "+e.getMessage()));}});
            return true;
        }
        if(args.length!=0)return false;
        if(sender instanceof Player p)open(p,0);else sender.sendMessage("Im Spiel /extramenu oder in der Konsole /extramenu reload verwenden.");
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        return args.length==1&&sender.hasPermission("serverhub.reload")&&"reload".startsWith(args[0].toLowerCase(Locale.ROOT))?List.of("reload"):List.of();
    }
}
