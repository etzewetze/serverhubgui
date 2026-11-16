package de.serverhub.velocity;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;
class PackProbeTest {
    @Test void probeFollowsRedirectsAndDetectsHtmlAndMissingFiles()throws Exception{
        byte[] bytes="the exact zip bytes".getBytes();
        String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/zip",x->{try(x){x.sendResponseHeaders(200,bytes.length);x.getResponseBody().write(bytes);}});
        server.createContext("/redirect",x->{try(x){x.getResponseHeaders().set("Location","/zip");x.sendResponseHeaders(302,-1);}});
        server.createContext("/html",x->{try(x){byte[] html="<html>login</html>".getBytes();x.sendResponseHeaders(200,html.length);x.getResponseBody().write(html);}});
        server.start();String base="http://127.0.0.1:"+server.getAddress().getPort();
        try{
            assertTrue(PackProbe.check(base+"/redirect",sha).success());
            assertFalse(PackProbe.check(base+"/html",sha).success());
            assertTrue(PackProbe.check(base+"/missing",sha).detail().contains("404"));
        }finally{server.stop(0);}
    }
}
