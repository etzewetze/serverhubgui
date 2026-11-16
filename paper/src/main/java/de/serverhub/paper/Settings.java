package de.serverhub.paper;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import net.kyori.adventure.text.format.TextColor;
import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

record Settings(YamlConfiguration yaml, List<Entry> entries, int fontSize, boolean icon, int slot,
                boolean hint, int hintInterval, int handshakeTimeout, boolean automatic) {
    record Entry(String key, String name, String target, String permission, TextColor color, int size, List<String> description,
                 de.serverhub.common.MenuSnapshot.Status status) {
        Entry(String key,String name,String target,String permission,TextColor color,int size,List<String> description){
            this(key,name,target,permission,color,size,description,de.serverhub.common.MenuSnapshot.Status.UNKNOWN);
        }
    }
    private static final Pattern TARGET = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    static Settings read(File file) throws Exception {
        var y = new YamlConfiguration(); y.load(file);
        int size = size(y.getInt("menu.font-size", 12));
        int slot = y.getInt("hotbar-icon.slot", 8);
        if (slot < 0 || slot > 8) throw new IllegalArgumentException("hotbar-icon.slot muss 0–8 sein");
        color(y.getString("menu.title-color", "#3D2817"));
        String fallback = y.getString("menu.text-color", "#FFF4D6"); color(fallback);
        var entries = new ArrayList<Entry>();
        ConfigurationSection section = y.getConfigurationSection("servers");
        for (String key : section == null ? Set.<String>of() : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) throw new IllegalArgumentException("Ungültiger Servereintrag: " + key);
            String name = s.getString("name", key), target = s.getString("target", key);
            if (name.isBlank() || name.length() > 100 || !TARGET.matcher(target).matches())
                throw new IllegalArgumentException("Ungültiger Name/target bei " + key);
            entries.add(new Entry(key, name, target, s.getString("permission", ""),
                color(s.getString("color", fallback)), size(s.getInt("font-size", size)), List.copyOf(s.getStringList("description"))));
        }
        return new Settings(y, List.copyOf(entries), size, y.getBoolean("hotbar-icon.enabled", false), slot,
            y.getBoolean("shift-f-hint.enabled", true), Math.clamp(y.getInt("shift-f-hint.interval-seconds", 1),1,2),
            Math.max(10, y.getInt("proxy.handshake-timeout-seconds", 30)), y.getBoolean("server-discovery.enabled", true));
    }
    List<Entry> resolve(List<String> discovered) {
        if (!automatic) return entries;
        var overrides = new HashMap<String,Entry>();
        for (Entry entry : entries) overrides.put(entry.target().toLowerCase(Locale.ROOT),entry);
        var result = new ArrayList<Entry>();
        var seen = new HashSet<String>();
        for (String target : discovered) {
            if (!de.serverhub.common.Wire.validTarget(target) || !seen.add(target.toLowerCase(Locale.ROOT))) continue;
            Entry custom = overrides.get(target.toLowerCase(Locale.ROOT));
            if (custom != null) {
                if (!yaml.getBoolean("servers."+custom.key()+".hidden",false)) result.add(custom);
            } else {
                String display = target.replace('_',' ').replace('-',' ');
                display = display.substring(0,1).toUpperCase(Locale.ROOT)+display.substring(1);
                result.add(new Entry(target,display,target,"",color(yaml.getString("menu.text-color","#FFF4D6")),fontSize,List.of()));
            }
        }
        return List.copyOf(result);
    }
    static int size(int n) {
        if (n != 8 && n != 10 && n != 12) throw new IllegalArgumentException("font-size muss 8, 10 oder 12 sein");
        return n;
    }
    static Settings fromNetwork(de.serverhub.common.MenuSnapshot snapshot){
        var y=new YamlConfiguration();var o=snapshot.options();
        de.serverhub.common.MenuOptions.DEFAULTS.keySet().forEach(key->y.set(key,o.typed(key)));
        var entries=snapshot.entries().stream().map(e->new Entry(e.target(),e.name(),e.target(),e.permission(),color(e.color()),e.size(),e.description(),e.status())).toList();
        return new Settings(y,entries,o.number("menu.font-size"),o.flag("hotbar-icon.enabled"),o.number("hotbar-icon.slot"),
            o.flag("shift-f-hint.enabled"),o.number("shift-f-hint.interval-seconds"),o.number("proxy.handshake-timeout-seconds"),false);
    }
    de.serverhub.common.MenuOptions menuOptions(){
        var values=new LinkedHashMap<String,String>();
        de.serverhub.common.MenuOptions.DEFAULTS.keySet().forEach(key->{Object value=yaml.get(key);if(value!=null)values.put(key,String.valueOf(value));});
        return new de.serverhub.common.MenuOptions(values);
    }
    static TextColor color(String s) {
        if (s == null || !s.matches("#[0-9A-Fa-f]{6}")) throw new IllegalArgumentException("Ungültige Hexfarbe: " + s);
        return Objects.requireNonNull(TextColor.fromHexString(s));
    }
}
