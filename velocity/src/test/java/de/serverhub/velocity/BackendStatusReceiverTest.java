package de.serverhub.velocity;

import de.serverhub.common.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class BackendStatusReceiverTest {
    @TempDir Path directory;
    int post(HttpClient client,URI uri,String token,byte[] body)throws Exception {
        var builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3)).POST(HttpRequest.BodyPublishers.ofByteArray(body));if(token!=null)builder.header("Authorization","Bearer "+token);
        return client.send(builder.build(),HttpResponse.BodyHandlers.discarding()).statusCode();
    }
    BackendStatus next(BlockingQueue<BackendStatus> frames,boolean paused)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);BackendStatus result;
        while(System.nanoTime()<end){result=frames.poll(Math.max(1,end-System.nanoTime()),TimeUnit.NANOSECONDS);if(result!=null&&result.paused()==paused)return result;}return null;
    }
    @Test void authenticationIsPerRegisteredTargetAndPayloadsAreBounded()throws Exception {
        var received=new CopyOnWriteArrayList<BackendStatus>();
        try(var receiver=new BackendStatusReceiver(directory,"127.0.0.1",0,name->Set.of("lobby","survival").contains(name),(name,value)->received.add(value));var client=HttpClient.newHttpClient()){
            var link=receiver.link("lobby",MenuOptions.defaults());var frame=new BackendStatus(UUID.randomUUID(),1,true);
            assertEquals(401,post(client,link.endpoint("127.0.0.1"),null,frame.encode()));
            assertEquals(401,post(client,link.endpoint("127.0.0.1"),receiver.token("survival"),frame.encode()));
            assertEquals(400,post(client,link.endpoint("127.0.0.1"),link.token(),new byte[65]));
            assertEquals(404,post(client,URI.create("http://127.0.0.1:"+receiver.port()+"/serverhub-status?target=unknown"),link.token(),frame.encode()));
            assertTrue(received.isEmpty());assertEquals(204,post(client,link.endpoint("127.0.0.1"),link.token(),frame.encode()));assertEquals(List.of(frame),received);
        }
    }
    @Test void backendHeartbeatsReportRealTransitionsWithoutAnyPlayerTransport()throws Exception {
        var frames=new LinkedBlockingQueue<BackendStatus>();String bootstrap;
        try(var receiver=new BackendStatusReceiver(directory.resolve("proxy"),"127.0.0.1",0,name->name.equals("survival"),(name,value)->frames.add(value))){
            bootstrap=receiver.link("survival",MenuOptions.defaults()).virtualHost();
            Path backend=directory.resolve("backend");Files.createDirectories(backend);
            try(var reporter=new BackendReporter(backend,message->{})){
                reporter.sample(false);reporter.configure(BackendReportLink.fromVirtualHost(bootstrap).orElseThrow(),"127.0.0.1");
                var awake=next(frames,false);assertNotNull(awake);assertFalse(awake.paused());
                reporter.sample(true);BackendStatus asleep=next(frames,true);assertNotNull(asleep);assertTrue(asleep.paused());assertTrue(asleep.sequence()>awake.sequence());
                reporter.sample(false);BackendStatus resumed=next(frames,false);assertNotNull(resumed);assertFalse(resumed.paused());
                assertEquals(awake.instance(),resumed.instance());assertTrue(Files.isRegularFile(backend.resolve("proxy-status.properties")));
            }
            frames.clear();
            // A backend restart can report using its cached link before any status ping or player joins.
            try(var restarted=new BackendReporter(backend,message->{})){restarted.sample(true);BackendStatus frame=next(frames,true);assertNotNull(frame);assertTrue(frame.paused());}
        }
    }
    @Test void proxyRestartKeepsThePreviouslyIssuedBackendCredentialsValid()throws Exception {
        String token;
        try(var receiver=new BackendStatusReceiver(directory,"127.0.0.1",0,name->true,(name,value)->{})){token=receiver.token("lobby");}
        try(var receiver=new BackendStatusReceiver(directory,"127.0.0.1",0,name->true,(name,value)->{})){assertEquals(token,receiver.token("LOBBY"));assertNotEquals(token,receiver.token("survival"));}
    }
    @Test void explicitCallbackHostsNeedThePrivateProxyChannelAndCannotBeChangedByAPing()throws Exception {
        var frames=new LinkedBlockingQueue<BackendStatus>();
        try(var receiver=new BackendStatusReceiver(directory.resolve("proxy"),"127.0.0.1",0,name->true,(name,value)->frames.add(value));var reporter=new BackendReporter(directory.resolve("backend"),message->{})){
            var options=new MenuOptions(Map.of("status.report-host","127.0.0.1"));var link=receiver.link("lobby",options);reporter.sample(true);
            assertFalse(reporter.configure(link,"127.0.0.1"));
            reporter.configureTrusted(link,"127.0.0.1");assertFalse(reporter.configure(receiver.link("other",MenuOptions.defaults()),"127.0.0.1"));
            assertNotNull(next(frames,true));
        }
    }
}
