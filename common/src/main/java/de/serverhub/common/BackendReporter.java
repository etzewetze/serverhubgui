package de.serverhub.common;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Sends wall-clock heartbeats independently of players and Minecraft plugin-message transport. */
public final class BackendReporter implements AutoCloseable {
    private record Route(BackendReportLink link,String peer,boolean trusted){}
    private record Sample(boolean paused,long time){}
    private final UUID instance=UUID.randomUUID();
    private final Path cache;
    private final Consumer<String> log;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("serverhub-status-reporter").factory());
    private volatile Route route;
    private volatile Sample sample;
    private Route saved;
    private Sample sent;
    private long sequence,lastSend,lastWarning;
    public BackendReporter(Path directory,Consumer<String> log)throws IOException {
        this.cache=directory.resolve("proxy-status.properties");this.log=log;
        if(Files.isRegularFile(cache)&&Files.size(cache)<2048){
            try{var p=new Properties();try(var input=Files.newInputStream(cache)){p.load(input);}var link=BackendReportLink.fromVirtualHost(p.getProperty("link"));if(link.isPresent()){String peer=p.getProperty("peer");boolean trusted=Boolean.parseBoolean(p.getProperty("trusted","false"));if(link.get().host().equals("auto")||trusted){link.get().endpoint(peer);route=saved=new Route(link.get(),peer,trusted);}}}catch(Exception e){log.accept("Gespeicherter Statuskanal wird nach dem nächsten Proxy-Ping erneuert.");}
        }
        executor.scheduleWithFixedDelay(this::send,100,200,TimeUnit.MILLISECONDS);
    }
    /** Called on Paper's main-thread heartbeat, which also runs during native empty-server pause. */
    public void sample(boolean paused){sample=new Sample(paused,System.currentTimeMillis());}
    public boolean configure(BackendReportLink link,String peer){
        // Auto addresses always refer to the actual peer of the status connection, never an arbitrary packet address.
        if(!link.host().equals("auto")||route!=null&&route.trusted)return false;
        return configure(link,peer,false);
    }
    /** An explicit host may only arrive through the proxy's nonce-bound private channel. */
    public void configureTrusted(BackendReportLink link,String peer){configure(link,peer,true);}
    private boolean configure(BackendReportLink link,String peer,boolean trusted){
        if(peer==null||peer.isBlank()||peer.length()>64)return false;
        try{link.endpoint(peer);route=new Route(link,peer,trusted);return true;}catch(IllegalArgumentException ignored){return false;}
    }
    private void send(){
        Route destination=route;Sample value=sample;long now=System.currentTimeMillis();
        if(destination==null||value==null||now-value.time>Math.max(5000,destination.link.intervalMillis()*3L))return;
        if(now-lastSend<destination.link.intervalMillis()&&saved!=null&&destination.equals(saved)&&sent!=null&&sent.paused==value.paused)return;
        lastSend=now;sent=value;
        try{
            if(!destination.equals(saved)){Files.createDirectories(cache.getParent());var p=new Properties();p.setProperty("link",destination.link.virtualHost());p.setProperty("peer",destination.peer);p.setProperty("trusted",Boolean.toString(destination.trusted));
                Path temporary=Files.createTempFile(cache.getParent(),"status-",".tmp");try{try(var out=Files.newOutputStream(temporary)){p.store(out,"Automatisch vom Proxy erkannt; keine manuellen Backend-Einstellungen nötig");}try{Files.move(temporary,cache,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,cache,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temporary);}saved=destination;
            }
            var report=new BackendStatus(instance,++sequence,value.paused);
            var request=HttpRequest.newBuilder(destination.link.endpoint(destination.peer)).timeout(Duration.ofSeconds(2)).header("Authorization","Bearer "+destination.link.token()).header("Content-Type","application/octet-stream").POST(HttpRequest.BodyPublishers.ofByteArray(report.encode())).build();
            var response=client.send(request,HttpResponse.BodyHandlers.discarding());
            if(response.statusCode()!=204&&response.statusCode()!=409)warn(now,"Statusmeldung vom Proxy abgelehnt (HTTP "+response.statusCode()+"). Proxy-Statuskanal prüfen.");
        }catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception e){warn(now,"Proxy-Statuskanal nicht erreichbar. Zwischen Backend und Proxy muss der in servers.yml eingestellte status.report-port erreichbar sein.");}
    }
    private void warn(long now,String message){if(now-lastWarning>60000){lastWarning=now;log.accept(message);}}
    @Override public void close(){executor.shutdownNow();client.shutdownNow();}
}
