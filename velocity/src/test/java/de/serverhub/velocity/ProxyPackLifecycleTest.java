package de.serverhub.velocity;

import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import com.velocitypowered.api.event.player.*;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.scheduler.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.time.Duration;
import de.serverhub.common.Wire;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.server.*;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.net.InetSocketAddress;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProxyPackLifecycleTest {
    @TempDir Path directory;
    ProxyServer proxy; Player player; ServerHubVelocity plugin;
    Scheduler.TaskBuilder task;ScheduledTask timeout; UUID packId;
    ArgumentCaptor<Runnable> callback;
    void setup(String url)throws Exception{
        try(var bundled=getClass().getResourceAsStream("/serverhub-pack.zip")){Files.copy(Objects.requireNonNull(bundled),directory.resolve("serverhub-pack.zip"));}
        Files.writeString(directory.resolve("config.properties"),"pack.mode="+(url.isBlank()?"github":"external")+"\npack.external-url="+url+"\n");
        proxy=mock(ProxyServer.class);player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());when(player.isActive()).thenReturn(true);
        when(proxy.getAllPlayers()).thenReturn(List.of());
        when(proxy.getAllServers()).thenReturn(List.of());
        var scheduler=mock(Scheduler.class);when(proxy.getScheduler()).thenReturn(scheduler);
        task=mock(Scheduler.TaskBuilder.class,RETURNS_SELF);timeout=mock(ScheduledTask.class);when(task.schedule()).thenReturn(timeout);
        callback=ArgumentCaptor.forClass(Runnable.class);when(scheduler.buildTask(any(),callback.capture())).thenReturn(task);
        var builder=mock(ResourcePackInfo.Builder.class,RETURNS_SELF);when(builder.build()).thenReturn(mock(ResourcePackInfo.class));
        when(proxy.createResourcePackBuilder(anyString())).thenReturn(builder);
        plugin=new ServerHubVelocity(proxy,mock(Logger.class),directory);plugin.reload();
        packId=PackData.read(directory.resolve("serverhub-pack.zip")).id();
    }
    void connect(){var event=mock(ServerPostConnectEvent.class);when(event.getPlayer()).thenReturn(player);plugin.connected(event);}
    void status(UUID id,PlayerResourcePackStatusEvent.Status status){
        var event=mock(PlayerResourcePackStatusEvent.class);when(event.getPlayer()).thenReturn(player);
        when(event.getPackId()).thenReturn(id);when(event.getStatus()).thenReturn(status);plugin.packStatus(event);
    }
    @Test void repeatedConnectEventsSendOnlyOneRequiredOffer()throws Exception{
        setup("https://example.org/serverhub.zip");connect();connect();
        verify(player,times(1)).sendResourcePackOffer(any());verify(task,times(1)).schedule();
        verify(proxy.createResourcePackBuilder("https://example.org/serverhub.zip")).setShouldForce(true);
    }
    @Test void githubNeedsNoManualUrlOrHttpPort()throws Exception{
        setup("");connect();verify(player,never()).disconnect(any());verify(player).sendResourcePackOffer(any());
        verify(proxy).createResourcePackBuilder(PackUrls.RELEASE_URL);
    }
    @Test void timeoutDisconnectsAnUnloadedClient()throws Exception{
        setup("https://example.org/serverhub.zip");connect();callback.getValue().run();verify(player).disconnect(any());
    }
    @Test void exactPackSuccessCancelsTimeoutAndOtherPacksCannotUnlock()throws Exception{
        setup("https://example.org/serverhub.zip");connect();
        status(UUID.randomUUID(),PlayerResourcePackStatusEvent.Status.SUCCESSFUL);verify(timeout,never()).cancel();
        status(packId,PlayerResourcePackStatusEvent.Status.SUCCESSFUL);verify(timeout).cancel();
        callback.getValue().run();verify(player,never()).disconnect(any());
        status(packId,PlayerResourcePackStatusEvent.Status.DISCARDED);verify(player).disconnect(any());
    }
    @Test void packChangesAndReloadDoNotPromptExistingPlayersAgain()throws Exception{
        setup("https://example.org/serverhub.zip");connect();status(packId,PlayerResourcePackStatusEvent.Status.SUCCESSFUL);
        try(var zip=new ZipOutputStream(Files.newOutputStream(directory.resolve("serverhub-pack.zip")))){
            for(String name:List.of("pack.mcmeta","assets/serverhub/font/gui.json","assets/serverhub/items/menu_icon.json")){
                zip.putNextEntry(new ZipEntry(name));zip.write("{\"new\":true}".getBytes());zip.closeEntry();
            }
        }
        plugin.reload();connect();
        verify(player,times(1)).sendResourcePackOffer(any());verify(timeout).cancel();
        callback.getValue().run();verify(player,never()).disconnect(any());
    }
    @Test void onlyARealNetworkDisconnectStartsAnotherOffer()throws Exception{
        setup("https://example.org/serverhub.zip");connect();
        var quit=mock(DisconnectEvent.class);when(quit.getPlayer()).thenReturn(player);plugin.disconnected(quit);connect();
        verify(player,times(2)).sendResourcePackOffer(any());
    }
    @Test void delayedDisconnectFromAnotherConnectionCannotResetThisSession()throws Exception{
        setup("https://example.org/serverhub.zip");connect();
        UUID id=player.getUniqueId();Player stale=mock(Player.class);when(stale.getUniqueId()).thenReturn(id);
        var quit=mock(DisconnectEvent.class);when(quit.getPlayer()).thenReturn(stale);plugin.disconnected(quit);connect();
        verify(player,times(1)).sendResourcePackOffer(any());
    }
    @Test void simultaneousBackendEventsStillSendExactlyOneOffer()throws Exception{
        setup("https://example.org/serverhub.zip");
        java.util.stream.IntStream.range(0,12).parallel().forEach(i->connect());
        verify(player,times(1)).sendResourcePackOffer(any());
    }
    @Test void newBackendHandshakesReceiveLoadedStateWithoutAnotherDownload()throws Exception{
        setup("https://example.org/serverhub.zip");connect();status(packId,PlayerResourcePackStatusEvent.Status.SUCCESSFUL);
        for(String name:List.of("lobby","survival","creative")){
            var registered=mock(RegisteredServer.class);var connection=mock(ServerConnection.class);
            when(connection.getPlayer()).thenReturn(player);when(connection.getServer()).thenReturn(registered);
            when(connection.getServerInfo()).thenReturn(new ServerInfo(name,new InetSocketAddress("127.0.0.1",25565)));
            when(player.getCurrentServer()).thenReturn(Optional.of(connection));
            UUID nonce=UUID.randomUUID();var message=mock(PluginMessageEvent.class);
            when(message.getIdentifier()).thenReturn(MinecraftChannelIdentifier.from(Wire.CHANNEL));
            when(message.getSource()).thenReturn(connection);when(message.getData()).thenReturn(Wire.encode(Wire.Type.HELLO,nonce));
            plugin.pluginMessage(message);
            var bytes=ArgumentCaptor.forClass(byte[].class);verify(connection,times(2)).sendPluginMessage(any(),bytes.capture());
            var state=Wire.decode(bytes.getAllValues().getFirst());assertEquals(nonce,state.nonce());assertEquals("true",state.value(0));
        }
        verify(player,times(1)).sendResourcePackOffer(any());
    }
    RegisteredServer registered(String name){
        var server=mock(RegisteredServer.class);
        when(server.getServerInfo()).thenReturn(new ServerInfo(name,new InetSocketAddress("127.0.0.1",25565)));
        return server;
    }
    List<String> catalog(ServerConnection connection)throws Exception{
        UUID nonce=UUID.randomUUID();var message=mock(PluginMessageEvent.class);
        when(message.getIdentifier()).thenReturn(MinecraftChannelIdentifier.from(Wire.CHANNEL));
        when(message.getSource()).thenReturn(connection);when(message.getData()).thenReturn(Wire.encode(Wire.Type.HELLO,nonce));
        clearInvocations(connection);plugin.pluginMessage(message);
        var bytes=ArgumentCaptor.forClass(byte[].class);verify(connection,times(2)).sendPluginMessage(any(),bytes.capture());
        var packet=Wire.decode(bytes.getAllValues().getLast());
        assertEquals(nonce,packet.nonce());assertEquals(Wire.Type.CATALOG,packet.type());return packet.values();
    }
    ServerConnection backend(){
        var server=registered("lobby");var connection=mock(ServerConnection.class);
        when(connection.getPlayer()).thenReturn(player);when(connection.getServer()).thenReturn(server);
        var info=server.getServerInfo();when(connection.getServerInfo()).thenReturn(info);when(player.getCurrentServer()).thenReturn(Optional.of(connection));
        return connection;
    }
    @Test void discoversRegisteredServersSortsThemAndHidesProxyPermissionDenials()throws Exception{
        setup("");connect();var connection=backend();
        var servers=List.of(registered("survival"),registered("admin"),registered("lobby"));
        when(proxy.getAllServers()).thenReturn(servers);
        when(player.getPermissionValue("serverhub.connect.admin")).thenReturn(com.velocitypowered.api.permission.Tristate.FALSE);
        assertEquals(List.of("lobby","survival"),catalog(connection));
        verify(player,times(1)).sendResourcePackOffer(any());
    }
    @Test void detectsAddedAndRemovedServersWithoutAnotherPackOffer()throws Exception{
        setup("");connect();var connection=backend();
        var lobby=registered("lobby");var event=registered("event");
        when(proxy.getAllServers()).thenReturn(List.of(lobby));
        assertEquals(List.of("lobby"),catalog(connection));
        when(proxy.getAllServers()).thenReturn(List.of(event,lobby));
        assertEquals(List.of("event","lobby"),catalog(connection));
        when(proxy.getAllServers()).thenReturn(List.of(event));
        assertEquals(List.of("event"),catalog(connection));
        verify(player,times(1)).sendResourcePackOffer(any());
    }
}
