package de.serverhub.velocity;

import com.velocitypowered.api.proxy.*;
import de.serverhub.common.MenuSnapshot.Status;
import java.lang.reflect.*;
import java.util.*;

/** Optional public plugin API; no access to private fields or proxy internals. */
final class NavigatorBridge {
    enum Result { ABSENT, UNMANAGED, HANDLED, UNSUPPORTED, FAILED }
    private final ProxyServer proxy;
    NavigatorBridge(ProxyServer proxy){this.proxy=proxy;}
    private Object instance(){return proxy.getPluginManager()==null?null:proxy.getPluginManager().getPlugin("servernavigator").flatMap(p->p.getInstance()).orElse(null);}
    private static boolean supported(Object api)throws ReflectiveOperationException {return ((Number)api.getClass().getMethod("serverHubApiVersion").invoke(api)).intValue()==1;}
    Result navigate(Player player,String target){
        Object api=instance();if(api==null)return Result.ABSENT;
        try{if(!supported(api))return Result.UNSUPPORTED;return (Boolean)api.getClass().getMethod("requestServer",Player.class,String.class).invoke(api,player,target)?Result.HANDLED:Result.UNMANAGED;}
        catch(NoSuchMethodException e){return Result.UNSUPPORTED;}catch(ReflectiveOperationException|RuntimeException e){return Result.FAILED;}
    }
    boolean waiting(UUID player){
        Object api=instance();if(api==null)return false;
        try{return supported(api)&&(Boolean)api.getClass().getMethod("isWaiting",UUID.class).invoke(api,player);}catch(ReflectiveOperationException|RuntimeException e){return false;}
    }
    Status status(String target,Status fallback){
        Object api=instance();if(api==null)return fallback;
        try{if(!supported(api))return fallback;String phase=(String)api.getClass().getMethod("navigationPhase",String.class).invoke(api,target);
            return Set.of("CHECKING","STARTING","LONGER","CONNECTING").contains(phase)?Status.STARTING:fallback;
        }catch(ReflectiveOperationException|RuntimeException e){return fallback;}
    }
}
