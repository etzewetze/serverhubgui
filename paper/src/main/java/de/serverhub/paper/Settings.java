package de.serverhub.paper;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import net.kyori.adventure.text.format.TextColor;
import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

record Settings(YamlConfiguration yaml, List<Entry> entries, int fontSize, boolean icon, int slot,
                boolean hint, int hintInterval, int handshakeTimeout) {
    record Entry(String key, String name, String target, String permission, TextColor color, int size, List<String> description) {}
    private static final Pattern TARGET = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    static Settings read(File file) throws Exception {
        var y = new YamlConfiguration(); y.load(file);
        int size = size(y.getInt("menu.font-size", 8));
        int slot = y.getInt("hotbar-icon.slot", 8);
        if (slot < 0 || slot > 8) throw new IllegalArgumentException("hotbar-icon.slot muss 0–8 sein");
        color(y.getString("menu.title-color", "#E8C78F"));
        String fallback = y.getString("menu.text-color", "#FFF4D6"); color(fallback);
        var entries = new ArrayList<Entry>();
        ConfigurationSection section = y.getConfigurationSection("servers");
        if (section == null) throw new IllegalArgumentException("servers fehlt");
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) throw new IllegalArgumentException("Ungültiger Servereintrag: " + key);
            String name = s.getString("name", key), target = s.getString("target", key);
            if (name.isBlank() || name.length() > 100 || !TARGET.matcher(target).matches())
                throw new IllegalArgumentException("Ungültiger Name/target bei " + key);
            entries.add(new Entry(key, name, target, s.getString("permission", ""),
                color(s.getString("color", fallback)), size(s.getInt("font-size", size)), List.copyOf(s.getStringList("description"))));
        }
        return new Settings(y, List.copyOf(entries), size, y.getBoolean("hotbar-icon.enabled", true), slot,
            y.getBoolean("shift-f-hint.enabled", true), Math.max(2, y.getInt("shift-f-hint.interval-seconds", 5)),
            Math.max(10, y.getInt("proxy.handshake-timeout-seconds", 30)));
    }
    static int size(int n) {
        if (n != 8 && n != 10 && n != 12) throw new IllegalArgumentException("font-size muss 8, 10 oder 12 sein");
        return n;
    }
    static TextColor color(String s) {
        if (s == null || !s.matches("#[0-9A-Fa-f]{6}")) throw new IllegalArgumentException("Ungültige Hexfarbe: " + s);
        return Objects.requireNonNull(TextColor.fromHexString(s));
    }
}
