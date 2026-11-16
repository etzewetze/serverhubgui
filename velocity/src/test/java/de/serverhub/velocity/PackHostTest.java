package de.serverhub.velocity;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class PackHostTest {
    @Test void pendingHashRemainsAvailableAcrossReloadUntilItsSessionEnds()throws Exception{
        String first="a".repeat(40),second="b".repeat(40);
        var old=new PackData(new byte[]{1,2},first,UUID.randomUUID());
        try(var host=new PackHost("127.0.0.1",0,old);var client=HttpClient.newHttpClient()){
            URI base=URI.create("http://127.0.0.1:"+host.port()+"/serverhub.zip?sha1="+first);
            host.update(new PackData(new byte[]{3,4},second,UUID.randomUUID()));host.retain(java.util.Set.of(first));
            assertArrayEquals(old.bytes(),client.send(HttpRequest.newBuilder(base).build(),HttpResponse.BodyHandlers.ofByteArray()).body());
            host.retain(java.util.Set.of());
            assertEquals(404,client.send(HttpRequest.newBuilder(base).build(),HttpResponse.BodyHandlers.discarding()).statusCode());
        }
    }
    @Test void servesOnlyExactZipEndpointWithGetAndHead()throws Exception{
        var pack=new PackData(new byte[]{1,2,3},"abc",UUID.randomUUID());
        try(var host=new PackHost("127.0.0.1",0,pack);var client=HttpClient.newHttpClient()){
            URI base=URI.create("http://127.0.0.1:"+host.port());
            var get=client.send(HttpRequest.newBuilder(base.resolve("/serverhub.zip")).build(),HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200,get.statusCode());assertArrayEquals(pack.bytes(),get.body());
            var head=client.send(HttpRequest.newBuilder(base.resolve("/serverhub.zip")).method("HEAD",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200,head.statusCode());assertEquals("3",head.headers().firstValue("Content-Length").orElseThrow());assertEquals(0,head.body().length);
            assertEquals(404,client.send(HttpRequest.newBuilder(base.resolve("/config.properties")).build(),HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(405,client.send(HttpRequest.newBuilder(base.resolve("/serverhub.zip")).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.discarding()).statusCode());
            host.update(new PackData(new byte[]{4,5},"def",UUID.randomUUID()));
            assertArrayEquals(new byte[]{4,5},client.send(HttpRequest.newBuilder(base.resolve("/serverhub.zip")).build(),HttpResponse.BodyHandlers.ofByteArray()).body());
        }
    }
}
