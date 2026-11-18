package de.serverhub.velocity;
import de.serverhub.common.*;

import de.serverhub.common.PackUrls;
import de.serverhub.common.PackData;
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
    @AfterEach void shutdown(){if(plugin!=null)plugin.shutdown(mock(com.velocitypowered.api.event.proxy.ProxyShutdownEvent.class));}
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
        Files.writeString(directory.resolve("pack/meine-aenderung.txt"),"Bearbeitetes Paket");
        plugin.reload();connect();
        assertNotEquals(packId,PackData.read(directory.resolve("serverhub-pack.zip")).id());
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
            var bytes=ArgumentCaptor.forClass(byte[].class);verify(connection,times(3)).sendPluginMessage(any(),bytes.capture());
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
        plugin.refreshRegistered();
        UUID nonce=UUID.randomUUID();var message=mock(PluginMessageEvent.class);
        when(message.getIdentifier()).thenReturn(MinecraftChannelIdentifier.from(Wire.CHANNEL));
        when(message.getSource()).thenReturn(connection);when(message.getData()).thenReturn(Wire.encode(Wire.Type.HELLO,nonce));
        clearInvocations(connection);plugin.pluginMessage(message);
        var bytes=ArgumentCaptor.forClass(byte[].class);verify(connection,times(3)).sendPluginMessage(any(),bytes.capture());
        var packet=Wire.decode(bytes.getAllValues().get(1));
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
    @Test void aProxyReloadSendsCentralStyleAndHiddenTargetsToEveryBackendSession()throws Exception{
        setup("");connect();var connection=backend();
        var registered=List.of(registered("lobby"),registered("private"));when(proxy.getAllServers()).thenReturn(registered);
        Files.writeString(directory.resolve("servers.yml"),"menu:\n  layout: list\n  navigation: scroll\n  show-page-info: false\nservers:\n  lobby:\n    name: Treffpunkt\n    color: '#123456'\n    font-size: 10\n  private:\n    hidden: true\n");
        plugin.reload();assertEquals(List.of("lobby"),catalog(connection));
        var bytes=ArgumentCaptor.forClass(byte[].class);verify(connection,times(3)).sendPluginMessage(any(),bytes.capture());
        var packet=Wire.decode(bytes.getAllValues().getLast());assertEquals(Wire.Type.MENU,packet.type());
        var snapshot=de.serverhub.common.MenuSnapshot.decode(packet.values());
        assertEquals("Treffpunkt",snapshot.entries().getFirst().name());assertEquals("#123456",snapshot.entries().getFirst().color());
        assertEquals(10,snapshot.entries().getFirst().size());assertTrue(snapshot.options().scrolling());assertFalse(snapshot.options().pageInfo(20));
        verify(player,times(1)).sendResourcePackOffer(any());
    }
    @Test void navigatorOwnsManagedRequestsAndServerHubDoesNotBypassTheStartDelay()throws Exception{
        setup("https://example.org/serverhub.zip");connect();status(packId,PlayerResourcePackStatusEvent.Status.SUCCESSFUL);var connection=backend();var target=registered("survival");
        var lobby=connection.getServer();when(proxy.getAllServers()).thenReturn(List.of(lobby,target));when(proxy.getServer("survival")).thenReturn(Optional.of(target));
        var api=new NavigatorBridgeTest.Api();api.waiting=player.getUniqueId();
        var manager=mock(com.velocitypowered.api.plugin.PluginManager.class);var container=mock(com.velocitypowered.api.plugin.PluginContainer.class);
        when(proxy.getPluginManager()).thenReturn(manager);when(manager.getPlugin("servernavigator")).thenReturn(Optional.of(container));doReturn(Optional.of(api)).when(container).getInstance();
        catalog(connection);UUID nonce=UUID.randomUUID();
        var hello=mock(PluginMessageEvent.class);when(hello.getIdentifier()).thenReturn(MinecraftChannelIdentifier.from(Wire.CHANNEL));when(hello.getSource()).thenReturn(connection);when(hello.getData()).thenReturn(Wire.encode(Wire.Type.HELLO,nonce));plugin.pluginMessage(hello);
        var click=mock(PluginMessageEvent.class);when(click.getIdentifier()).thenReturn(MinecraftChannelIdentifier.from(Wire.CHANNEL));when(click.getSource()).thenReturn(connection);when(click.getData()).thenReturn(Wire.encode(Wire.Type.CONNECT,nonce,"survival"));plugin.pluginMessage(click);
        assertSame(player,api.requested);assertEquals("survival",api.target);verify(player,never()).createConnectionRequest(any());
        var bytes=ArgumentCaptor.forClass(byte[].class);verify(connection,atLeastOnce()).sendPluginMessage(any(),bytes.capture());
        var packet=Wire.decode(bytes.getAllValues().getLast());var snapshot=de.serverhub.common.MenuSnapshot.decode(packet.values());
        assertTrue(snapshot.options().flag("runtime.waiting"));assertEquals(de.serverhub.common.MenuSnapshot.Status.STARTING,snapshot.entries().getLast().status());
    }
    @Test void aSleepingBackendWithoutPlayersPushesYellowWithoutAnotherHandshakeOrPackOffer()throws Exception{
        setup("https://example.org/serverhub.zip");connect();status(packId,PlayerResourcePackStatusEvent.Status.SUCCESSFUL);var connection=backend();var lobby=connection.getServer();var target=registered("survival");
        when(proxy.getAllServers()).thenReturn(List.of(lobby,target));when(proxy.getServer("survival")).thenReturn(Optional.of(target));catalog(connection);
        var ping=ServerPing.builder().version(new ServerPing.Version(774,"Paper")).description(net.kyori.adventure.text.Component.text("Online")).build();
        when(lobby.ping(any(PingOptions.class))).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(ping));when(target.ping(any(PingOptions.class))).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(ping));
        when(proxy.getAllPlayers()).thenReturn(List.of(player));
        var updates=new java.util.concurrent.LinkedBlockingQueue<de.serverhub.common.MenuSnapshot>();
        doAnswer(call->{var packet=Wire.decode(call.getArgument(1,byte[].class));if(packet.type()==Wire.Type.MENU)updates.add(de.serverhub.common.MenuSnapshot.decode(packet.values()));return true;}).when(connection).sendPluginMessage(any(),any(byte[].class));
        plugin.refreshStatuses();var options=ArgumentCaptor.forClass(PingOptions.class);verify(target).ping(options.capture());
        var bootstrap=de.serverhub.common.BackendReportLink.fromVirtualHost(options.getValue().getVirtualHost()).orElseThrow();assertEquals("survival",bootstrap.target());
        try(var reporter=new de.serverhub.common.BackendReporter(directory.resolve("backend-with-no-players"),message->{})){
            reporter.sample(true);reporter.configure(bootstrap,"127.0.0.1");long end=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5);boolean yellow=false;
            while(System.nanoTime()<end&&!yellow){var snapshot=updates.poll(1,java.util.concurrent.TimeUnit.SECONDS);yellow=snapshot!=null&&snapshot.entries().stream().anyMatch(e->e.target().equals("survival")&&e.status()==de.serverhub.common.MenuSnapshot.Status.SLEEPING);}
            assertTrue(yellow,"Backend report must update the visible snapshot even though the Minecraft ping stays green");
        }
        verify(player,times(1)).sendResourcePackOffer(any());
    }
    void packet(ServerConnection connection,UUID nonce,Wire.Type type,String... values){
        var event=mock(PluginMessageEvent.class);when(event.getIdentifier()).thenReturn(MinecraftChannelIdentifier.from(Wire.CHANNEL));when(event.getSource()).thenReturn(connection);when(event.getData()).thenReturn(Wire.encode(type,nonce,values));plugin.pluginMessage(event);
    }
    @Test void backendNewsGrantWorksWithUndefinedProxyRightsButExplicitDenialAndWrongNonceStillFail()throws Exception{
        setup("");connect();status(packId,PlayerResourcePackStatusEvent.Status.SUCCESSFUL);var connection=backend();var lobby=connection.getServer();var target=registered("challenges");when(proxy.getAllServers()).thenReturn(List.of(lobby,target));when(proxy.getServer("challenges")).thenReturn(Optional.of(target));plugin.refreshRegistered();UUID nonce=UUID.randomUUID();packet(connection,nonce,Wire.Type.HELLO);
        when(player.getPermissionValue("serverhub.news")).thenReturn(com.velocitypowered.api.permission.Tristate.FALSE);
        packet(connection,nonce,Wire.Type.NEWS_CHANGE,"start","challenges","Neue Welt");assertFalse(new NewsStore(directory).active("challenges"));
        when(player.getPermissionValue("serverhub.news")).thenReturn(com.velocitypowered.api.permission.Tristate.UNDEFINED);packet(connection,UUID.randomUUID(),Wire.Type.NEWS_CHANGE,"start","challenges","Gefälscht");assertFalse(new NewsStore(directory).active("challenges"));
        when(proxy.getAllPlayers()).thenReturn(List.of(player));clearInvocations(connection);packet(connection,nonce,Wire.Type.NEWS_CHANGE,"start","challenges","Neue Welt");assertEquals("Neue Welt",new NewsStore(directory).get("challenges"));
        var messages=ArgumentCaptor.forClass(byte[].class);verify(connection,atLeastOnce()).sendPluginMessage(any(),messages.capture());var snapshot=MenuSnapshot.decode(Wire.decode(messages.getAllValues().getLast()).values());assertTrue(snapshot.entries().stream().filter(e->e.target().equals("challenges")).findFirst().orElseThrow().style().news());assertEquals("lobby",snapshot.options().text("runtime.server"));
        clearInvocations(connection);packet(connection,nonce,Wire.Type.NEWS_VIEW,"challenges");verify(connection).sendPluginMessage(any(),messages.capture());var content=Wire.decode(messages.getValue());assertEquals(Wire.Type.NEWS_CONTENT,content.type());assertEquals(List.of("challenges","Neue Welt"),content.values());
        when(player.getPermissionValue("serverhub.connect.challenges")).thenReturn(com.velocitypowered.api.permission.Tristate.FALSE);clearInvocations(connection);packet(connection,nonce,Wire.Type.NEWS_VIEW,"challenges");verify(connection).sendPluginMessage(any(),messages.capture());assertEquals(Wire.Type.RESULT,Wire.decode(messages.getValue()).type());
        when(player.getPermissionValue("serverhub.connect.challenges")).thenReturn(com.velocitypowered.api.permission.Tristate.UNDEFINED);packet(connection,nonce,Wire.Type.NEWS_CHANGE,"stop","challenges");assertFalse(new NewsStore(directory).active("challenges"));
    }
    @Test void newsCanBeSetForTheLobbyAndOtherTargetsWithoutAnyTeleportOrConnectPermission()throws Exception{
        setup("");connect();status(packId,PlayerResourcePackStatusEvent.Status.SUCCESSFUL);var connection=backend();var lobby=connection.getServer();var other=registered("challenges");when(proxy.getAllServers()).thenReturn(List.of(lobby,other));when(proxy.getServer("lobby")).thenReturn(Optional.of(lobby));when(proxy.getServer("challenges")).thenReturn(Optional.of(other));plugin.refreshRegistered();UUID nonce=UUID.randomUUID();packet(connection,nonce,Wire.Type.HELLO);
        when(player.getPermissionValue("serverhub.news")).thenReturn(com.velocitypowered.api.permission.Tristate.UNDEFINED);when(player.getPermissionValue("serverhub.connect.challenges")).thenReturn(com.velocitypowered.api.permission.Tristate.FALSE);
        for(String target:List.of("lobby","challenges")){
            clearInvocations(connection);var operation=NewsCommand.parse(new String[]{"news","start","--server",target,"Neue","Welt"},"lobby");packet(connection,nonce,Wire.Type.NEWS_CHANGE,operation.wire());assertEquals("Neue Welt",new NewsStore(directory).get(target));
            var bytes=ArgumentCaptor.forClass(byte[].class);verify(connection).sendPluginMessage(any(),bytes.capture());assertEquals(List.of("news-start"),Wire.decode(bytes.getValue()).values());
            packet(connection,nonce,Wire.Type.NEWS_CHANGE,NewsCommand.parse(new String[]{"news","stop","--server",target},"lobby").wire());assertFalse(new NewsStore(directory).active(target));
        }
        clearInvocations(connection);packet(connection,nonce,Wire.Type.NEWS_CHANGE,"start","missing","Text");var bytes=ArgumentCaptor.forClass(byte[].class);verify(connection).sendPluginMessage(any(),bytes.capture());assertEquals(List.of("news-unavailable"),Wire.decode(bytes.getValue()).values());
        verify(player,never()).createConnectionRequest(any());
    }
    NavigatorBridgeTest.Api navigator(){
        var api=new NavigatorBridgeTest.Api();var manager=mock(com.velocitypowered.api.plugin.PluginManager.class);var container=mock(com.velocitypowered.api.plugin.PluginContainer.class);when(proxy.getPluginManager()).thenReturn(manager);when(manager.getPlugin("servernavigator")).thenReturn(Optional.of(container));doReturn(Optional.of(api)).when(container).getInstance();return api;
    }
    @Test void aRedOfflinePingNeverStartsTheNavigatorCountdownOrADirectTransfer()throws Exception{
        setup("");connect();status(packId,PlayerResourcePackStatusEvent.Status.SUCCESSFUL);var connection=backend();var lobby=connection.getServer();var target=registered("survival");when(proxy.getAllServers()).thenReturn(List.of(lobby,target));when(proxy.getServer("survival")).thenReturn(Optional.of(target));var api=navigator();catalog(connection);
        var online=ServerPing.builder().version(new ServerPing.Version(774,"Paper")).description(net.kyori.adventure.text.Component.text("Online")).build();when(lobby.ping(any(PingOptions.class))).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(online));when(target.ping(any(PingOptions.class))).thenReturn(java.util.concurrent.CompletableFuture.failedFuture(new java.io.IOException("offline")));plugin.refreshStatuses();
        UUID nonce=UUID.randomUUID();clearInvocations(connection);packet(connection,nonce,Wire.Type.HELLO);var bytes=ArgumentCaptor.forClass(byte[].class);verify(connection,times(3)).sendPluginMessage(any(),bytes.capture());var menu=MenuSnapshot.decode(Wire.decode(bytes.getAllValues().getLast()).values());assertEquals(MenuSnapshot.Status.OFFLINE,menu.entries().stream().filter(e->e.target().equals("survival")).findFirst().orElseThrow().status());
        clearInvocations(connection);packet(connection,nonce,Wire.Type.CONNECT,"survival");verify(connection).sendPluginMessage(any(),bytes.capture());assertEquals(List.of("offline"),Wire.decode(bytes.getValue()).values());assertNull(api.requested);verify(player,never()).createConnectionRequest(any());
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={0,50})
    void ampControlsTheActualMenuAndNavigationEvenWhenPingAndPaperHeartbeatAreAwake(int appState)throws Exception{
        setup("https://example.org/serverhub.zip");connect();status(packId,PlayerResourcePackStatusEvent.Status.SUCCESSFUL);var connection=backend();var lobby=connection.getServer();var survival=registered("survival");when(proxy.getAllServers()).thenReturn(List.of(lobby,survival));when(proxy.getServer("survival")).thenReturn(Optional.of(survival));
        var http=com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);http.createContext("/API/",exchange->{String result=exchange.getRequestURI().getPath().endsWith("Login")?"{\"success\":true,\"sessionID\":\"fixture\"}":"[{\"InstanceID\":\"survival\",\"AppState\":"+appState+"}]";exchange.getRequestBody().readAllBytes();byte[] bytes=result.getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});http.start();
        try{
            Files.writeString(directory.resolve("amp.yml"),"enabled: true\nurl: 'http://127.0.0.1:"+http.getAddress().getPort()+"/'\nusername: fixture\n");plugin.reload();var ampField=ServerHubVelocity.class.getDeclaredField("amp");ampField.setAccessible(true);((AmpStatusSource)ampField.get(plugin)).poll();
            var monitorField=ServerHubVelocity.class.getDeclaredField("statusMonitor");monitorField.setAccessible(true);((ServerStatusMonitor)monitorField.get(plugin)).backend("survival",new BackendStatus(UUID.randomUUID(),1,false));
            var awake=ServerPing.builder().version(new ServerPing.Version(774,"Paper")).description(net.kyori.adventure.text.Component.text("Online")).build();when(lobby.ping(any(PingOptions.class))).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(awake));when(survival.ping(any(PingOptions.class))).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(awake));plugin.refreshStatuses();
            catalog(connection);var bytes=ArgumentCaptor.forClass(byte[].class);verify(connection,times(3)).sendPluginMessage(any(),bytes.capture());var menu=MenuSnapshot.decode(Wire.decode(bytes.getAllValues().getLast()).values());assertEquals(appState==0?MenuSnapshot.Status.OFFLINE:MenuSnapshot.Status.SLEEPING,menu.entries().stream().filter(e->e.target().equals("survival")).findFirst().orElseThrow().status());
            var api=navigator();UUID nonce=UUID.randomUUID();packet(connection,nonce,Wire.Type.HELLO);packet(connection,nonce,Wire.Type.CONNECT,"survival");if(appState==0){assertNull(api.requested);verify(connection,atLeastOnce()).sendPluginMessage(any(),bytes.capture());assertEquals(List.of("offline"),Wire.decode(bytes.getValue()).values());}else assertSame(player,api.requested);verify(player,never()).createConnectionRequest(any());
        }finally{http.stop(0);}
        verify(player,times(1)).sendResourcePackOffer(any());
    }
}
