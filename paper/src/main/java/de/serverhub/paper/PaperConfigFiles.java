package de.serverhub.paper;

import java.io.File;
import java.nio.file.*;
import org.bukkit.configuration.file.YamlConfiguration;

final class PaperConfigFiles {
    private PaperConfigFiles() {}
    static boolean upgrade(File file) throws Exception {
        var config = new YamlConfiguration(); config.load(file);
        if (config.getInt("interface-version",0) >= 2) return false;
        Path backup = file.toPath().resolveSibling("config-v2.1-backup.yml");
        for (int i=1;Files.exists(backup);i++) backup=file.toPath().resolveSibling("config-v2.1-backup-"+i+".yml");
        Files.copy(file.toPath(),backup);
        config.set("interface-version",2);
        config.set("server-discovery.enabled",true);
        config.set("menu.font-size",12);
        if ("#E8C78F".equalsIgnoreCase(config.getString("menu.title-color"))) config.set("menu.title-color","#3D2817");
        config.set("hotbar-icon.enabled",false);
        config.set("shift-f-hint.enabled",true);
        config.set("shift-f-hint.interval-seconds",1);
        config.set("shift-f-hint.x-offset",106);
        config.set("shift-f-hint.y-offset",0);
        if(!config.contains("messages.discovering"))config.set("messages.discovering","&eDie Serverliste wird gerade vom Proxy geladen. Bitte gleich erneut öffnen.");
        config.save(file);
        return true;
    }
}
