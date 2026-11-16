package de.serverhub.paper;

import java.nio.file.*;
import org.bukkit.configuration.file.YamlConfiguration;

final class NetworkMode {
    private NetworkMode() {}
    static boolean proxied(Path directory,String mode)throws Exception {
        if(mode.equalsIgnoreCase("proxy"))return true;
        if(mode.equalsIgnoreCase("standalone"))return false;
        if(!mode.equalsIgnoreCase("auto"))throw new IllegalArgumentException("network.mode: auto, proxy oder standalone");
        return enabled(directory.resolve("config/paper-global.yml"),"proxies.velocity.enabled")
            ||enabled(directory.resolve("paper.yml"),"settings.velocity-support.enabled")
            ||enabled(directory.resolve("spigot.yml"),"settings.bungeecord");
    }
    private static boolean enabled(Path file,String key)throws Exception {
        if(!Files.isRegularFile(file))return false;
        var yaml=new YamlConfiguration();yaml.load(file.toFile());return yaml.getBoolean(key,false);
    }
}
