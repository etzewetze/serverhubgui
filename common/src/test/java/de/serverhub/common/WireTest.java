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
}
