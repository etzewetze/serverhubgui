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
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().character('&').hexColors().build();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private Settings settings;
    private GuiText gui;
    private NamespacedKey iconKey;
    private long ticks;
    private static final class Session {
        final UUID nonce = UUID.randomUUID();
        final long joined = System.currentTimeMillis();
        boolean acknowledged, ready, fullWarning, discovered;
        List<String> catalog = List.of();
        UUID menuToken;
        long lastOpen, lastSwitch;
    }
    @Override public void onEnable() {
        saveDefaultConfig();
        try {
            File config = new File(getDataFolder(), "config.yml");
            if (PaperConfigFiles.upgrade(config)) getLogger().info("Menü auf native Dialoge, automatische Serverliste und HUD-Hinweis umgestellt; alte Konfiguration gesichert.");
            settings = Settings.read(config); gui = new GuiText();
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
        sessions.clear();
    }
    private Component text(String text) { return LEGACY.deserialize(text).decoration(TextDecoration.ITALIC,false); }
    private Component message(String key) { return text(settings.yaml().getString("messages."+key, "&cServerHub: "+key)); }
    private void startSession(Player p) {
        sessions.put(p.getUniqueId(),new Session()); applyIcon(p);
        Bukkit.getScheduler().runTaskLater(this, () -> hello(p),10);
    }
    private void hello(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s != null && p.isOnline()) p.sendPluginMessage(this,Wire.CHANNEL,Wire.encode(Wire.Type.HELLO,s.nonce));
    }
    private void incoming(Player p, byte[] data) {
        Session s = sessions.get(p.getUniqueId()); if (s == null || !p.isOnline()) return;
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
            } else if (packet.type() == Wire.Type.CATALOG) {
                s.catalog = packet.values(); s.discovered = true;
            } else if (packet.type() == Wire.Type.RESULT) {
                String key = switch(packet.value(0)) { case "already" -> "already-connected"; case "denied" -> "denied"; case "loading" -> "loading"; default -> "unavailable"; };
                p.sendMessage(message(key));
            }
        } catch (Exception ignored) { /* Untrusted or out-of-date message. */ }
    }
    private void tick() {
        ticks++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            Session s = sessions.get(p.getUniqueId()); if (s == null) continue;
            if (!s.acknowledged && System.currentTimeMillis()-s.joined > settings.handshakeTimeout()*1000L) { p.kick(message("proxy-missing")); continue; }
            if (ticks % 5 == 0 || !s.acknowledged) hello(p);
            if (s.ready && p.hasPermission("serverhub.use") && settings.hint() && ticks % settings.hintInterval() == 0
                && p.getOpenInventory().getType() == InventoryType.CRAFTING) {
                p.sendActionBar(gui.hint(settings.yaml().getInt("shift-f-hint.x-offset",106),
                    settings.yaml().getInt("shift-f-hint.y-offset",0)));
            }
        }
    }
    private boolean ready(Player p) {
        Session s = sessions.get(p.getUniqueId()); return s != null && s.ready;
    }
    private void open(Player p,int requested) {
        if (!p.hasPermission("serverhub.use")) { p.sendMessage(message("no-permission")); return; }
        if (!ready(p)) { p.sendMessage(message("loading")); return; }
        Session s=sessions.get(p.getUniqueId());
        if(settings.automatic()&&!s.discovered){hello(p);p.sendMessage(message("discovering"));return;}
        var entries=settings.resolve(s.catalog).stream().filter(e->e.permission().isBlank()||p.hasPermission(e.permission())).toList();
        int page=MenuLayout.clampPage(requested,entries.size());
        UUID token=UUID.randomUUID();s.menuToken=token;
        // Never place this dialog over a container: closing returns directly to the game.
        p.closeInventory();
        DialogMenu.show(p,gui,settings,entries,page,action->Bukkit.getScheduler().runTask(this,()->{
            if(!p.isOnline()||p.isDead()||sessions.get(p.getUniqueId())!=s||!token.equals(s.menuToken))return;
            if(!ready(p)||!p.hasPermission("serverhub.use")){s.menuToken=null;p.closeDialog();return;}
            s.menuToken=null;action.run();
        }),entry->choose(p,s,entry),next->open(p,next));
    }
    private void choose(Player p,Session s,Settings.Entry entry){
        if(!entry.permission().isBlank()&&!p.hasPermission(entry.permission())){p.sendMessage(message("no-permission"));return;}
        if(settings.automatic()&&s.catalog.stream().noneMatch(name->name.equalsIgnoreCase(entry.target()))) {
            p.sendMessage(message("unavailable"));return;
        }
        long now=System.currentTimeMillis();if(now-s.lastSwitch<700)return;s.lastSwitch=now;
        p.closeDialog();
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
        if(!settings.icon() || !p.hasPermission("serverhub.use") || p.isDead())return;
        PlayerInventory inv=p.getInventory(); int slot=settings.slot();
        if(!HotbarSlot.makeSpace(inv,slot)) {
                Session s=sessions.get(p.getUniqueId());
                if(s!=null&&!s.fullWarning){p.sendMessage(message("full-inventory"));s.fullWarning=true;} return;
        }
        ItemStack item=new ItemStack(Material.COMPASS); ItemMeta meta=item.getItemMeta();
        meta.getPersistentDataContainer().set(iconKey,PersistentDataType.BYTE,(byte)1);
        if(ready(p))meta.setItemModel(new NamespacedKey("serverhub","menu_icon"));
        meta.displayName(text(ready(p)?"&#E8C78FServermenü &7(Rechtsklick)":"&eMenüpaket wird geladen …"));
        meta.lore(List.of(text("&7Shift + F öffnet ebenfalls das Menü.")));
        item.setItemMeta(meta); inv.setItem(slot,item);
    }
    @EventHandler public void join(PlayerJoinEvent e){startSession(e.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent e){removeIcons(e.getPlayer());sessions.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void respawn(PlayerRespawnEvent e){Bukkit.getScheduler().runTaskLater(this,()->applyIcon(e.getPlayer()),1);}
    @EventHandler public void death(PlayerDeathEvent e){e.getDrops().removeIf(this::isIcon);}
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
            try {
                Settings next=Settings.read(new File(getDataFolder(),"config.yml")); settings=next;
                for(Player p:Bukkit.getOnlinePlayers()) {
                    Session s=sessions.get(p.getUniqueId());if(s!=null)s.menuToken=null;
                    hello(p);
                    applyIcon(p);
                }
                sender.sendMessage(message("reloaded"));
            }catch(Exception e){getLogger().warning("Reload abgelehnt: "+e.getMessage());sender.sendMessage(message("reload-failed"));}
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
