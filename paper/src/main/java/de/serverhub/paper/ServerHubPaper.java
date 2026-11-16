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
        boolean acknowledged, ready, fullWarning;
        long lastOpen, lastSwitch;
    }
    private static final class Menu implements InventoryHolder {
        final int page;
        final List<Settings.Entry> entries;
        Inventory inventory;
        Menu(int page, List<Settings.Entry> entries) { this.page=page; this.entries=entries; }
        public Inventory getInventory() { return inventory; }
    }
    @Override public void onEnable() {
        saveDefaultConfig();
        try { settings = Settings.read(new File(getDataFolder(), "config.yml")); gui = new GuiText(); }
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
        for (Player p : Bukkit.getOnlinePlayers()) { if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) p.closeInventory(); removeIcons(p); }
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
                    if (!s.ready && p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) p.closeInventory();
                    applyIcon(p);
                }
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
                p.sendActionBar(Component.text("\uE100 ").font(net.kyori.adventure.key.Key.key("serverhub:hint"))
                    .append(text(settings.yaml().getString("shift-f-hint.text", "&7Shift + F"))
                        .font(net.kyori.adventure.key.Key.key("minecraft:default"))));
            }
        }
    }
    private boolean ready(Player p) {
        Session s = sessions.get(p.getUniqueId()); return s != null && s.ready;
    }
    private void open(Player p,int requested) {
        if (!p.hasPermission("serverhub.use")) { p.sendMessage(message("no-permission")); return; }
        if (!ready(p)) { p.sendMessage(message("loading")); return; }
        var entries = settings.entries().stream().filter(e -> e.permission().isBlank() || p.hasPermission(e.permission())).toList();
        int page = MenuLayout.clampPage(requested,entries.size());
        var visible = entries.subList(Math.min(page*6,entries.size()),Math.min(page*6+6,entries.size()));
        Component title = gui.background(visible.size());
        title = title.append(gui.label(settings.yaml().getString("menu.title","SERVERAUSWAHL"),10,"header",88,152,
            Settings.color(settings.yaml().getString("menu.title-color","#E8C78F"))));
        for (int i=0;i<visible.size();i++) {
            var e=visible.get(i); title=title.append(gui.label(e.name(),e.size(),"r"+(i/2),44+(i%2)*88,60,e.color()));
        }
        title=title.append(gui.label("Seite "+(page+1)+" / "+MenuLayout.pages(entries.size()),8,"status",132,64,TextColor.color(0x5A4D3A)));
        String[] nav={settings.yaml().getString("menu.previous","Zurück"),settings.yaml().getString("menu.close","Schließen"),settings.yaml().getString("menu.next","Weiter")};
        for (int i=0;i<3;i++) title=title.append(gui.label(nav[i],8,"nav",34+i*54,40,
            TextColor.color(i==0&&page==0 || i==2&&page+1>=MenuLayout.pages(entries.size()) ? 0xC5B6A0 : 0xFFF4D6)));
        var holder = new Menu(page,entries);
        holder.inventory=Bukkit.createInventory(holder,54,title);
        for (int slot=0;slot<54;slot++) {
            int local=MenuLayout.entryAt(slot);
            String name=" "; List<Component> lore=List.of();
            if (local>=0 && local<visible.size()) {
                var e=visible.get(local); name=e.name();
                var lines=new ArrayList<Component>();
                for(String line:e.description()) lines.add(text("&7"+line));
                lines.add(text("&8Ziel: "+e.target())); lines.add(text("&eKlicken zum Verbinden")); lore=lines;
            } else if(slot>=45) name=nav[(slot-45)/3];
            ItemStack item=new ItemStack(Material.PAPER); ItemMeta meta=item.getItemMeta();
            meta.setItemModel(new NamespacedKey("serverhub","invisible"));
            meta.displayName(text(name)); meta.lore(lore); if(name.equals(" "))meta.setHideTooltip(true);
            item.setItemMeta(meta); holder.inventory.setItem(slot,item);
        }
        p.openInventory(holder.inventory);
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
        if(e.getView().getTopInventory().getHolder() instanceof Menu||isIcon(e.getOldCursor())){e.setCancelled(true);return;}
        for(int raw:e.getRawSlots())if(isIcon(e.getView().getItem(raw))){e.setCancelled(true);return;}
    }
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;
        if(e.getView().getTopInventory().getHolder() instanceof Menu menu) {
            e.setCancelled(true);
            if(e.getClick()!=ClickType.LEFT&&e.getClick()!=ClickType.RIGHT)return;
            int raw=e.getRawSlot(); if(raw<0||raw>=54)return;
            if(!ready(p)){p.closeInventory();p.sendMessage(message("loading"));return;}
            if(raw>=45) {
                int action=(raw-45)/3;
                Bukkit.getScheduler().runTask(this,()->{if(action==1)p.closeInventory();else open(p,menu.page+(action==0?-1:1));}); return;
            }
            int local=MenuLayout.entryAt(raw), index=menu.page*6+local;
            if(local<0||index>=menu.entries.size())return;
            Settings.Entry entry=menu.entries.get(index);
            if(!p.hasPermission("serverhub.use")||!entry.permission().isBlank()&&!p.hasPermission(entry.permission())){p.sendMessage(message("no-permission"));return;}
            Session s=sessions.get(p.getUniqueId());long now=System.currentTimeMillis();
            if(s==null||now-s.lastSwitch<700)return; s.lastSwitch=now;
            Bukkit.getScheduler().runTask(this,()->{
                p.closeInventory();
                if(p.isOnline()&&ready(p))p.sendPluginMessage(this,Wire.CHANNEL,Wire.encode(Wire.Type.CONNECT,s.nonce,entry.target()));
            }); return;
        }
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
                    if(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu)open(p,menu.page);
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
