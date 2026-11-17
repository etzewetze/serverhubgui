package de.serverhub.velocity;

import com.sun.net.httpserver.*;
import de.serverhub.common.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Accepts only bounded, per-target authenticated state reports from registered backends. */
final class BackendStatusReceiver implements AutoCloseable {
    private final HttpServer server;
    private final byte[] secret;
    private final Predicate<String> registered;
    private final BiConsumer<String,BackendStatus> reports;
    private final ExecutorService executor=Executors.newFixedThreadPool(2,Thread.ofPlatform().daemon().name("serverhub-status-http-",0).factory());
    final String bind;
    final int configuredPort;
    BackendStatusReceiver(Path directory,String bind,int port,Predicate<String> registered,BiConsumer<String,BackendStatus> reports)throws IOException {
        this.bind=bind;this.configuredPort=port;this.registered=registered;this.reports=reports;
        Path key=directory.resolve("status-secret.bin");Files.createDirectories(directory);
        if(!Files.exists(key)){byte[] next=new byte[32];new SecureRandom().nextBytes(next);Files.write(key,next,StandardOpenOption.CREATE_NEW);}
        if(Files.size(key)!=32)throw new IOException("status-secret.bin muss 32 Byte enthalten");secret=Files.readAllBytes(key);
        server=HttpServer.create(new InetSocketAddress(bind,port),16);server.createContext("/",this::receive);server.setExecutor(executor);server.start();
    }
    int port(){return server.getAddress().getPort();}
    String token(String target){
        try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(("serverhub-status-v1:"+target.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8)));}catch(GeneralSecurityException e){throw new IllegalStateException(e);}
    }
    BackendReportLink link(String target,MenuOptions options){return new BackendReportLink(target,options.text("status.report-host"),port(),options.text("status.report-scheme").equals("https"),token(target),options.number("status.heartbeat-interval-seconds")*1000);}
    private void receive(HttpExchange exchange)throws IOException {
        try(exchange){
            if(!exchange.getRequestURI().getRawPath().equals("/serverhub-status")){exchange.sendResponseHeaders(404,-1);return;}
            if(!exchange.getRequestMethod().equals("POST")){exchange.sendResponseHeaders(405,-1);return;}
            String query=exchange.getRequestURI().getRawQuery(),target=query!=null&&query.startsWith("target=")?query.substring(7):"";
            if(!Wire.validTarget(target)||!registered.test(target)){exchange.sendResponseHeaders(404,-1);return;}
            String authorization=exchange.getRequestHeaders().getFirst("Authorization");
            if(authorization==null||!MessageDigest.isEqual(("Bearer "+token(target)).getBytes(StandardCharsets.US_ASCII),authorization.getBytes(StandardCharsets.US_ASCII))){exchange.sendResponseHeaders(401,-1);return;}
            BackendStatus status;try{status=BackendStatus.decode(exchange.getRequestBody().readNBytes(65));}catch(IOException e){exchange.sendResponseHeaders(400,-1);return;}
            reports.accept(target,status);exchange.sendResponseHeaders(204,-1);
        }
    }
    @Override public void close(){server.stop(0);executor.shutdownNow();}
}
