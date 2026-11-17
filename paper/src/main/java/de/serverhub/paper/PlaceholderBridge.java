package de.serverhub.paper;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Optional public PlaceholderAPI bridge; placeholders refer to the viewing player's backend. */
final class PlaceholderBridge {
    private PlaceholderBridge(){}
    static String apply(Player player,String input,boolean enabled){
        if(!enabled||input==null||!input.contains("%"))return input;
        var plugin=Bukkit.getPluginManager().getPlugin("PlaceholderAPI");if(plugin==null||!plugin.isEnabled())return input;
        try{var api=plugin.getClass().getClassLoader().loadClass("me.clip.placeholderapi.PlaceholderAPI");String output=(String)api.getMethod("setPlaceholders",OfflinePlayer.class,String.class).invoke(null,player,input);return output.length()>2048?output.substring(0,2048):output;}catch(ReflectiveOperationException|RuntimeException e){return input;}
    }
}
