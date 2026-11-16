package de.serverhub.common;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class MenuSnapshotTest {
    @Test void aCompleteConfigurationSurvivesTheNonceBoundChannel()throws Exception{
        var options=new MenuOptions(Map.of("menu.layout","list","menu.font-size","10","shift-f-hint.enabled","false"));
        var entries=java.util.stream.IntStream.range(0,256).mapToObj(i->new MenuSnapshot.Entry("server-"+i,"Überleben "+i,"#12AB34",12,"serverhub.vip",List.of("Für alle Gäste"),MenuSnapshot.Status.ONLINE)).toList();
        var snapshot=new MenuSnapshot(UUID.randomUUID(),options,entries);UUID nonce=UUID.randomUUID();
        var packet=Wire.decode(Wire.encode(Wire.Type.MENU,nonce,snapshot.encode()));
        assertEquals(nonce,packet.nonce());assertEquals(snapshot,MenuSnapshot.decode(packet.values()));
        assertTrue(Wire.encode(Wire.Type.MENU,nonce,snapshot.encode()).length<Wire.MAX_BYTES);
    }
    @Test void invalidTransportAndOversizedExpansionAreRejected()throws Exception{
        assertThrows(IOException.class,()->MenuSnapshot.decode(List.of("not-base64")));
        var bytes=new ByteArrayOutputStream();try(var zip=new GZIPOutputStream(bytes)){zip.write(new byte[512*1024+1]);}
        String bomb=Base64.getEncoder().encodeToString(bytes.toByteArray());
        assertThrows(IOException.class,()->MenuSnapshot.decode(List.of(bomb)));
        assertThrows(IllegalArgumentException.class,()->new MenuSnapshot.Entry("../bad","Bad","#FFFFFF",12,"",List.of(),MenuSnapshot.Status.UNKNOWN));
        assertThrows(IllegalArgumentException.class,()->new MenuOptions(Map.of("menu.layout","invalid")));
        assertThrows(IllegalArgumentException.class,()->new MenuOptions(Map.of("status.timeout-ms","-1")));
    }
    @Test void scrollingHasNoPageInfoAndPagingDefaultsToMultiplePagesOnly(){
        var scroll=MenuOptions.defaults();assertEquals(1,scroll.columns());assertTrue(scroll.scrolling());assertFalse(scroll.pageInfo(12));
        var pages=new MenuOptions(Map.of("menu.navigation","pages"));
        assertFalse(pages.pageInfo(6));assertTrue(pages.pageInfo(7));assertFalse(pages.flag("menu.show-server-count"));
        assertFalse(new MenuOptions(Map.of("menu.navigation","pages","menu.show-page-info","false")).pageInfo(12));
        assertEquals(2,new MenuOptions(Map.of("menu.layout","grid")).columns());
    }
}
