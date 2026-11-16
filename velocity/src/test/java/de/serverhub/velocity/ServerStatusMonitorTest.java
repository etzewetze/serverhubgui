package de.serverhub.velocity;
import com.velocitypowered.api.proxy.server.*;
import de.serverhub.common.*;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerStatusMonitorTest {
    CentralMenuConfig.Server settings(String override,List<String> markers){return new CentralMenuConfig.Server(new MenuSnapshot.Entry("lobby","Lobby","#FFFFFF",12,"",List.of(),MenuSnapshot.Status.UNKNOWN),false,0,override,markers);}
    ServerPing ping(String description){return ServerPing.builder().version(new ServerPing.Version(774,"Paper")).description(Component.text(description)).onlinePlayers(0).maximumPlayers(100).build();}
    @Test void emptyOnlineServersStayGreenAndOnlyExplicitSignalsTurnYellow(){
        var auto=settings("auto",List.of("[sleeping]"));
        assertEquals(MenuSnapshot.Status.ONLINE,ServerStatusMonitor.classify(ping("Keine Spieler online"),null,auto));
        assertEquals(MenuSnapshot.Status.SLEEPING,ServerStatusMonitor.classify(ping("[SLEEPING] Beitreten zum Wecken"),null,auto));
        assertEquals(MenuSnapshot.Status.OFFLINE,ServerStatusMonitor.classify(null,new IOExceptionLike(),auto));
        assertEquals(MenuSnapshot.Status.SLEEPING,ServerStatusMonitor.classify(null,new IOExceptionLike(),settings("sleeping",List.of())));
    }
    @Test void pendingPingsDoNotDuplicateAndOldResultsCannotOverwriteAReload(){
        var entry=settings("auto",List.of());var config=new CentralMenuConfig.State(UUID.randomUUID(),MenuOptions.defaults(),List.of(entry));
        var monitor=new ServerStatusMonitor();monitor.configure(config);
        RegisteredServer server=mock(RegisteredServer.class);when(server.getServerInfo()).thenReturn(new ServerInfo("lobby",new java.net.InetSocketAddress("127.0.0.1",25565)));
        var future=new CompletableFuture<ServerPing>();when(server.ping()).thenReturn(future);
        monitor.refresh(List.of(server),config);monitor.refresh(List.of(server),config);verify(server,times(1)).ping();
        var next=new CentralMenuConfig.State(UUID.randomUUID(),MenuOptions.defaults(),List.of(entry));monitor.configure(next);future.complete(ping("Online"));
        assertEquals(MenuSnapshot.Status.UNKNOWN,monitor.status(entry,next));
    }
    static class IOExceptionLike extends RuntimeException {}
}
