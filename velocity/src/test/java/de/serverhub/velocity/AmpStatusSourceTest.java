package de.serverhub.velocity;

import com.sun.net.httpserver.HttpServer;
import de.serverhub.common.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class AmpStatusSourceTest {
    @TempDir Path directory;
    HttpServer server;String url;final List<String> paths=new ArrayList<>();final AtomicReference<String> response=new AtomicReference<>();final AtomicInteger statusCode=new AtomicInteger(200);final AtomicLong now=new AtomicLong(100000);
    @BeforeEach void serve()throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);url="http://127.0.0.1:"+server.getAddress().getPort()+"/";
        server.createContext("/API/",exchange->{String path=exchange.getRequestURI().getPath();paths.add(path);String request=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);String body;
            if(path.endsWith("Login")){assertTrue(request.contains("serverhub"));body="{\"result\":{\"success\":true,\"sessionID\":\"fixture-session\"}}";}
            else {assertEquals("fixture-session",Json.string(Json.read(request).getAsJsonObject(),"SESSIONID",""));body=response.get();}
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(path.endsWith("Login")?200:statusCode.get(),bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
    }
    @AfterEach void stop(){server.stop(0);}
    AmpStatusSource.Config config(String extra)throws Exception {
        Files.writeString(directory.resolve("amp.yml"),"enabled: true\nurl: '"+url+"'\nusername: serverhub\npassword: test-only\npoll-interval-seconds: 1\nstale-after-seconds: 3\n"+extra);
        return AmpStatusSource.load(directory);
    }
    CentralMenuConfig.Server target(String target,String explicit){return new CentralMenuConfig.Server(new MenuSnapshot.Entry(target,target,"#FFFFFF",12,"",List.of(),MenuSnapshot.Status.UNKNOWN),false,0,"auto",List.of(),explicit);}
    @Test void sleepingAmpApplicationOverridesAnOtherwiseGreenMinecraftPingAndReusesLogin()throws Exception {
        response.set("{\"result\":[{\"AvailableInstances\":[{\"InstanceID\":\"uuid-survival\",\"InstanceName\":\"Minecraft01\",\"FriendlyName\":\"Survival\",\"AppState\":50}]}]}");
        try(var source=new AmpStatusSource(config(""),s->{},()->{},now::get)){
            source.poll();assertEquals(MenuSnapshot.Status.SLEEPING,source.status(target("survival","")).orElseThrow());
            assertEquals(MenuSnapshot.Status.SLEEPING,source.status(target("other","uuid-survival")).orElseThrow());
            response.set("[{\"InstanceID\":\"uuid-survival\",\"FriendlyName\":\"Survival\",\"AppState\":20}]");source.poll();assertEquals(MenuSnapshot.Status.ONLINE,source.status(target("survival","")).orElseThrow());
            response.set("[{\"InstanceID\":\"uuid-survival\",\"FriendlyName\":\"Survival\",\"AppState\":45}]");source.poll();assertEquals(MenuSnapshot.Status.SLEEPING,source.status(target("survival","")).orElseThrow());
            now.addAndGet(4000);assertEquals(MenuSnapshot.Status.UNKNOWN,source.status(target("survival","")).orElseThrow());
            assertEquals(1,paths.stream().filter(p->p.endsWith("Login")).count());assertTrue(paths.stream().allMatch(p->p.endsWith("Login")||p.endsWith("GetInstances")));
        }
    }
    @Test void ambiguousNamesAndUnrecognizedStatesDoNotFallBackToOnline()throws Exception {
        response.set("[{\"InstanceID\":\"one\",\"FriendlyName\":\"Survival\",\"AppState\":50},{\"InstanceID\":\"two\",\"FriendlyName\":\"Survival\",\"AppState\":20}]");
        try(var source=new AmpStatusSource(config(""),s->{},()->{},now::get)){source.poll();assertEquals(MenuSnapshot.Status.UNKNOWN,source.status(target("survival","")).orElseThrow());assertEquals(MenuSnapshot.Status.SLEEPING,source.status(target("survival","one")).orElseThrow());assertEquals(MenuSnapshot.Status.UNKNOWN,source.status(target("missing","")).orElseThrow());}
    }
    @Test void directInstanceModeAndCustomStatesAreSupportedWithoutWakingTheGame()throws Exception {
        response.set("{\"result\":{\"State\":\"DeepSleep\",\"Metrics\":{}}}");
        try(var source=new AmpStatusSource(config("mode: instance\ninstance-target: survival\nstates:\n  sleeping: [DeepSleep]\n"),s->{},()->{},now::get)){source.poll();assertEquals(MenuSnapshot.Status.SLEEPING,source.status(target("survival","")).orElseThrow());assertTrue(source.status(target("lobby","")).isEmpty());assertTrue(paths.getLast().endsWith("Core/GetStatus"));}
    }
    @Test void deniedRequestsBackOffAndOldStatusExpires()throws Exception {
        response.set("[{\"InstanceID\":\"survival\",\"AppState\":50}]");
        try(var source=new AmpStatusSource(config(""),s->{},()->{},now::get)){source.poll();statusCode.set(403);source.poll();now.addAndGet(4000);source.poll();assertEquals(1,paths.stream().filter(p->p.endsWith("Login")).count());assertEquals(MenuSnapshot.Status.UNKNOWN,source.status(target("survival","")).orElseThrow());assertFalse(source.diagnostic().contains("test-only"));}
    }
    @Test void disabledAmpDoesNotClaimTargets()throws Exception {
        Files.writeString(directory.resolve("amp.yml"),"enabled: false\n");try(var source=new AmpStatusSource(AmpStatusSource.load(directory),s->{},()->{})){source.poll();assertTrue(paths.isEmpty());assertTrue(source.status(target("survival","")).isEmpty());}
    }
    @Test void successfulLoginAndDiscoveryAreLoggedWithoutRepeatingEveryPollOrLeakingSecrets()throws Exception {
        var info=new ArrayList<String>();var warnings=new ArrayList<String>();response.set("[{\"InstanceID\":\"survival\",\"AppState\":50}]");
        try(var source=new AmpStatusSource(config(""),warnings::add,info::add,()->{},now::get)){
            source.poll();assertEquals(List.of("AMP: Anmeldung erfolgreich.","AMP: 1 Serverinstanzen mit Anwendungsstatus gefunden."),info);source.poll();assertEquals(2,info.size());assertTrue(warnings.isEmpty());
            response.set("[{\"InstanceID\":\"survival\",\"AppState\":20},{\"InstanceID\":\"creative\",\"AppState\":0}]");source.poll();assertEquals("AMP: 2 Serverinstanzen mit Anwendungsstatus gefunden.",info.getLast());
            assertFalse(String.join(" ",info).contains("test-only"));assertFalse(String.join(" ",info).contains("fixture-session"));
        }
    }
    @Test void anEmptyDiscoveryIsReportedAsZeroAndNotAsASuccessfulStatusList()throws Exception {
        var info=new ArrayList<String>();var warnings=new ArrayList<String>();response.set("[]");try(var source=new AmpStatusSource(config(""),warnings::add,info::add,()->{},now::get)){source.poll();assertEquals("AMP: Anmeldung erfolgreich.",info.getFirst());assertTrue(info.getLast().contains("0 Serverinstanzen"));assertFalse(warnings.isEmpty());assertEquals(MenuSnapshot.Status.UNKNOWN,source.status(target("survival","")).orElseThrow());}
    }
}
