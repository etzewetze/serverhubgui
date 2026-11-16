package de.serverhub.velocity;

import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.plugin.*;
import de.serverhub.common.MenuSnapshot.Status;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NavigatorBridgeTest {
    public static final class Api {
        Player requested;String target,phase="STARTING";UUID waiting;boolean managed=true,fail;
        public int serverHubApiVersion(){return 1;}
        public boolean requestServer(Player player,String name){if(fail)throw new IllegalStateException("Noch nicht bereit");requested=player;target=name;return managed;}
        public String navigationPhase(String name){return phase;}
        public boolean isWaiting(UUID id){return id.equals(waiting);}
    }
    NavigatorBridge bridge(Object api){
        ProxyServer proxy=mock(ProxyServer.class);PluginManager manager=mock(PluginManager.class);PluginContainer plugin=mock(PluginContainer.class);
        when(proxy.getPluginManager()).thenReturn(manager);when(manager.getPlugin("servernavigator")).thenReturn(Optional.of(plugin));doReturn(Optional.of(api)).when(plugin).getInstance();
        return new NavigatorBridge(proxy);
    }
    @Test void managedTargetsUseNavigatorsQueueAndItsOwnPlayerWaitingState(){
        var api=new Api();Player player=mock(Player.class);UUID id=UUID.randomUUID();api.waiting=id;var bridge=bridge(api);
        assertEquals(NavigatorBridge.Result.HANDLED,bridge.navigate(player,"survival"));assertSame(player,api.requested);assertEquals("survival",api.target);
        assertTrue(bridge.waiting(id));assertFalse(bridge.waiting(UUID.randomUUID()));
        assertEquals(Status.STARTING,bridge.status("survival",Status.ONLINE));api.phase="AVAILABLE";assertEquals(Status.SLEEPING,bridge.status("survival",Status.SLEEPING));
    }
    @Test void absenceUnmanagedTargetsAndFailuresRemainDistinct(){
        var api=new Api();var bridge=bridge(api);api.managed=false;assertEquals(NavigatorBridge.Result.UNMANAGED,bridge.navigate(mock(Player.class),"other"));
        api.fail=true;assertEquals(NavigatorBridge.Result.FAILED,bridge.navigate(mock(Player.class),"survival"));
        assertEquals(NavigatorBridge.Result.UNSUPPORTED,bridge(new Object()).navigate(mock(Player.class),"survival"));
        assertEquals(NavigatorBridge.Result.ABSENT,new NavigatorBridge(mock(ProxyServer.class)).navigate(mock(Player.class),"survival"));
    }
}
