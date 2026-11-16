package de.serverhub.common;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
class WireTest {
    @Test void roundTripBindsMessagesToTheBackendNonce()throws Exception{
        UUID nonce=UUID.randomUUID();
        var packet=Wire.decode(Wire.encode(Wire.Type.CONNECT,nonce,"survival"));
        assertEquals(nonce,packet.nonce());assertEquals("survival",packet.value(0));
        assertEquals(Wire.Type.CONNECT,packet.type());
        assertEquals(0,Wire.decode(Wire.encode(Wire.Type.HELLO,nonce)).values().size());
    }
    @Test void malformedMessagesAreRejected(){
        UUID nonce=UUID.randomUUID();
        byte[] valid=Wire.encode(Wire.Type.STATE,nonce,"true");
        assertThrows(IOException.class,()->Wire.decode(Arrays.copyOf(valid,valid.length-1)));
        assertThrows(IOException.class,()->Wire.decode(Arrays.copyOf(valid,valid.length+1)));
        valid[0]=0;assertThrows(IOException.class,()->Wire.decode(valid));
        assertThrows(IOException.class,()->Wire.decode(new byte[2049]));
        assertThrows(IOException.class,()->Wire.decode(Wire.encode(Wire.Type.STATE,nonce,"yes")));
    }
    @Test void catalogRoundTripsAllRegisteredNamesWithoutLosingTheNonce()throws Exception{
        UUID nonce=UUID.randomUUID();
        String[] names=java.util.stream.IntStream.range(0,Wire.MAX_SERVERS).mapToObj(i->"server-"+i).toArray(String[]::new);
        var packet=Wire.decode(Wire.encode(Wire.Type.CATALOG,nonce,names));
        assertEquals(nonce,packet.nonce());assertEquals(List.of(names),packet.values());
        assertTrue(Wire.encode(Wire.Type.CATALOG,nonce,names).length<Wire.MAX_BYTES);
        assertTrue(Wire.decode(Wire.encode(Wire.Type.CATALOG,nonce)).values().isEmpty());
    }
    @Test void catalogRejectsInvalidNamesExcessiveCountsAndTruncation(){
        UUID nonce=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->Wire.encode(Wire.Type.CATALOG,nonce,"lobby\nadmin"));
        assertThrows(IllegalArgumentException.class,()->Wire.encode(Wire.Type.CATALOG,nonce,"a".repeat(65)));
        assertThrows(IllegalArgumentException.class,()->Wire.encode(Wire.Type.CATALOG,nonce,new String[257]));
        byte[] valid=Wire.encode(Wire.Type.CATALOG,nonce,"lobby");
        assertThrows(IOException.class,()->Wire.decode(Arrays.copyOf(valid,valid.length-1)));
        valid[21]=1;valid[22]=1; // Malicious count 257 before reading any strings.
        assertThrows(IOException.class,()->Wire.decode(valid));
        assertThrows(IOException.class,()->Wire.decode(new byte[Wire.MAX_BYTES+1]));
    }
}
