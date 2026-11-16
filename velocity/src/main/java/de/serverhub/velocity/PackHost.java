package de.serverhub.velocity;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.*;

/** Serves only the cached ZIP, never a filesystem directory. */
final class PackHost implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor=Executors.newFixedThreadPool(4,Thread.ofPlatform().daemon().name("serverhub-http-",0).factory());
    private final AtomicReference<PackData> pack;
    private final ConcurrentMap<String,PackData> versions=new ConcurrentHashMap<>();
    PackHost(String bind,int port,PackData initial) throws IOException {
        pack=new AtomicReference<>(initial);
        versions.put(initial.sha1(),initial);
        server=HttpServer.create(new InetSocketAddress(bind,port),16);
        server.createContext("/",this::serve);
        server.setExecutor(executor);server.start();
    }
    void update(PackData next){versions.put(next.sha1(),next);pack.set(next);}
    void retain(Set<String> active){versions.keySet().removeIf(sha->!active.contains(sha)&&!sha.equals(pack.get().sha1()));}
    int port(){return server.getAddress().getPort();}
    private void serve(HttpExchange x) throws IOException {
        try(x) {
            if(!x.getRequestURI().getRawPath().equals("/serverhub.zip")){x.sendResponseHeaders(404,-1);return;}
            if(!x.getRequestMethod().equals("GET")&&!x.getRequestMethod().equals("HEAD")){
                x.getResponseHeaders().set("Allow","GET, HEAD");x.sendResponseHeaders(405,-1);return;
            }
            PackData current=pack.get();
            String query=x.getRequestURI().getRawQuery();
            if(query!=null){
                if(!query.matches("sha1=[0-9a-f]{40}")){x.sendResponseHeaders(400,-1);return;}
                current=versions.get(query.substring(5));
                if(current==null){x.sendResponseHeaders(404,-1);return;}
            }
            x.getResponseHeaders().set("Content-Type","application/zip");
            x.getResponseHeaders().set("Content-Length",Integer.toString(current.bytes().length));
            x.getResponseHeaders().set("Cache-Control","no-cache");
            x.getResponseHeaders().set("ETag","\""+current.sha1()+"\"");
            x.getResponseHeaders().set("X-Content-Type-Options","nosniff");
            if(x.getRequestMethod().equals("HEAD")){x.sendResponseHeaders(200,-1);return;}
            x.sendResponseHeaders(200,current.bytes().length);x.getResponseBody().write(current.bytes());
        }
    }
    @Override public void close(){server.stop(0);executor.shutdownNow();}
}
