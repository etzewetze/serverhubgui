package de.serverhub.velocity;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import de.serverhub.common.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class PortalPublisherTest {
    @TempDir Path directory;
    static final String TOKEN="a".repeat(64),BASE="https://portal.example/packs";
    Properties config(){var p=new Properties();p.setProperty("pack.portal-url",BASE);p.setProperty("pack.portal-owner-id","2");p.setProperty("pack.portal-folder-id","0");return p;}
    PackData pack(byte[] bytes)throws Exception{return new PackData(bytes,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes)),UUID.randomUUID());}
    static final class Portal implements PortalPublisher.Transport {
        final List<String> actions=new ArrayList<>(),names=new ArrayList<>();final List<Integer> offsets=new ArrayList<>();ByteArrayOutputStream uploaded=new ByteArrayOutputStream();String sha,share;boolean badSha;int files;
        @Override public JsonObject post(String base,String token,String action,byte[] data,String type)throws IOException{
            assertEquals(BASE,base);assertEquals(TOKEN,token);actions.add(action);var result=new JsonObject();
            if(action.equals("start")){var request=Json.read(new String(data,StandardCharsets.UTF_8)).getAsJsonObject();names.add(request.get("name").getAsString());sha=request.get("sha1").getAsString();uploaded=new ByteArrayOutputStream();offsets.clear();result.addProperty("id","c".repeat(32));}
            else if(action.startsWith("chunk&")){int offset=Integer.parseInt(action.substring(action.indexOf("offset=")+7));assertEquals(uploaded.size(),offset);assertEquals("application/octet-stream",type);offsets.add(offset);uploaded.writeBytes(data);}
            else if(action.equals("complete")){result.addProperty("sha1",badSha?"0".repeat(40):sha);result.addProperty("file_id",++files);}
            else if(action.equals("share")){assertEquals("version",Json.read(new String(data,StandardCharsets.UTF_8)).getAsJsonObject().get("mode").getAsString());result.addProperty("url",share==null?BASE+"/download.php?t="+String.format("%064x",files):share);}
            else fail("Unexpected portal action");return result;
        }
    }
    @Test void exactPackBytesAreUploadedInChunksThenCachedWithoutApiCredentials()throws Exception {
        byte[] bytes=new byte[4*1024*1024+23];new Random(42).nextBytes(bytes);var pack=pack(bytes);var portal=new Portal();String url=PortalPublisher.publish(directory,config(),pack,"serverhub-pack.zip",name->TOKEN,portal);
        assertArrayEquals(bytes,portal.uploaded.toByteArray());assertEquals(List.of(0,2*1024*1024,4*1024*1024),portal.offsets);assertTrue(portal.names.getFirst().contains(pack.sha1()));assertTrue(url.startsWith(BASE+"/download.php?t="));int calls=portal.actions.size();assertEquals(url,PortalPublisher.publish(directory,config(),pack,"serverhub-pack.zip",name->TOKEN,portal));assertEquals(calls,portal.actions.size());assertFalse(Files.readString(directory.resolve("portal-upload.properties")).contains(TOKEN));
    }
    @Test void oldVersionsStayAddressableAndChangingFoldersDoesNotReuseAnotherFoldersShare()throws Exception {
        var portal=new Portal();PackData first=pack(new byte[]{1}),second=pack(new byte[]{2}),third=pack(new byte[]{3});String initial=PortalPublisher.publish(directory,config(),first,"pack.zip",name->TOKEN,portal);PortalPublisher.publish(directory,config(),second,"pack.zip",name->TOKEN,portal);PortalPublisher.publish(directory,config(),third,"pack.zip",name->TOKEN,portal);int calls=portal.actions.size();assertEquals(initial,PortalPublisher.publish(directory,config(),first,"pack.zip",name->TOKEN,portal));assertEquals(calls,portal.actions.size());assertEquals(3,new HashSet<>(portal.names).size());
        var other=config();other.setProperty("pack.portal-folder-id","9");PortalPublisher.publish(directory,other,first,"pack.zip",name->TOKEN,portal);assertTrue(portal.actions.size()>calls);
    }
    @Test void badChecksumOrForeignShareNeverReplacesTheWorkingCache()throws Exception {
        var portal=new Portal();PortalPublisher.publish(directory,config(),pack(new byte[]{1}),"pack.zip",name->TOKEN,portal);byte[] before=Files.readAllBytes(directory.resolve("portal-upload.properties"));portal.badSha=true;assertThrows(IOException.class,()->PortalPublisher.publish(directory,config(),pack(new byte[]{2}),"pack.zip",name->TOKEN,portal));assertArrayEquals(before,Files.readAllBytes(directory.resolve("portal-upload.properties")));portal.badSha=false;portal.share="https://other.example/download.php?t="+"b".repeat(64);assertThrows(IOException.class,()->PortalPublisher.publish(directory,config(),pack(new byte[]{3}),"pack.zip",name->TOKEN,portal));assertArrayEquals(before,Files.readAllBytes(directory.resolve("portal-upload.properties")));
    }
    @Test void invalidConfigurationAndMissingEnvironmentTokensMakeNoRequests()throws Exception {
        var portal=new Portal();var p=config();p.setProperty("pack.portal-url","http://portal.example");assertThrows(IOException.class,()->PortalPublisher.publish(directory,p,pack(new byte[]{1}),"pack.zip",name->TOKEN,portal));assertThrows(IOException.class,()->PortalPublisher.publish(directory,config(),pack(new byte[]{1}),"pack.zip",name->null,portal));assertThrows(IOException.class,()->PortalPublisher.publish(directory,config(),pack(new byte[]{1}),"../pack.zip",name->TOKEN,portal));assertTrue(portal.actions.isEmpty());
    }
    @Test void httpsDefaultPortAndExplicit443ReferToTheSamePortal()throws Exception {
        var portal=new Portal();portal.share="https://portal.example:443/packs/download.php?t="+"b".repeat(64);assertEquals(portal.share,PortalPublisher.publish(directory,config(),pack(new byte[]{1}),"pack.zip",name->TOKEN,portal));
    }
    @Test void httpTransportIsBoundedDoesNotRedirectAndDoesNotExposeErrorBodies()throws Exception {
        var status=new AtomicInteger(200);var body=new AtomicReference<>("{\"id\":\"ok\"}".getBytes(StandardCharsets.UTF_8));var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api.php",exchange->{assertEquals(TOKEN,exchange.getRequestHeaders().getFirst("X-Portal-Token"));assertEquals("action=start",exchange.getRequestURI().getQuery());assertArrayEquals(new byte[]{1,2},exchange.getRequestBody().readAllBytes());if(status.get()==302)exchange.getResponseHeaders().add("Location","https://other.example/");exchange.sendResponseHeaders(status.get(),body.get().length);exchange.getResponseBody().write(body.get());exchange.close();});server.start();
        try(var client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()){
            String base="http://127.0.0.1:"+server.getAddress().getPort();assertEquals("ok",PortalPublisher.post(client,base,TOKEN,"start",new byte[]{1,2},"application/json").get("id").getAsString());status.set(302);body.set(TOKEN.getBytes(StandardCharsets.UTF_8));var redirect=assertThrows(IOException.class,()->PortalPublisher.post(client,base,TOKEN,"start",new byte[]{1,2},"application/json"));assertTrue(redirect.getMessage().contains("302"));assertFalse(redirect.getMessage().contains(TOKEN));status.set(200);body.set(new byte[1024*1024+1]);assertThrows(IOException.class,()->PortalPublisher.post(client,base,TOKEN,"start",new byte[]{1,2},"application/json"));
        }finally{server.stop(0);}
    }
}
