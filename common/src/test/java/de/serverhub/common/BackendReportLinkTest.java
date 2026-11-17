package de.serverhub.common;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BackendReportLinkTest {
    @Test void aPingCanBootstrapTheCorrectCallbackWithoutPlayers(){
        var link=new BackendReportLink("survival.eu","auto",8125,false,"12".repeat(32),1000);
        var decoded=BackendReportLink.fromVirtualHost(link.virtualHost()).orElseThrow();assertEquals(link,decoded);
        assertTrue(link.virtualHost().length()<=253);
        assertEquals("http://192.0.2.10:8125/serverhub-status?target=survival.eu",decoded.endpoint("192.0.2.10").toString());
        assertEquals("http://[2001:db8::1]:8125/serverhub-status?target=survival.eu",decoded.endpoint("2001:db8::1").toString());
    }
    @Test void malformedOrOrdinaryHostnamesNeverChangeTheReportPath(){
        for(String host:new String[]{"play.example.org","sh3-invalid.invalid","sh3-====.invalid","x".repeat(254)})assertTrue(BackendReportLink.fromVirtualHost(host).isEmpty());
        assertThrows(IllegalArgumentException.class,()->new BackendReportLink("../bad","auto",8125,false,"00".repeat(32),1000));
        assertThrows(IllegalArgumentException.class,()->new BackendReportLink("lobby","bad/host",8125,false,"00".repeat(32),1000));
        var longLink=new BackendReportLink("a".repeat(64),"h".repeat(60)+".org",65535,true,"ab".repeat(32),60000);
        assertEquals(longLink,BackendReportLink.fromVirtualHost(longLink.virtualHost()).orElseThrow());
    }
    @Test void reportFramesRejectTruncationAndTrailingData()throws Exception {
        var report=new BackendStatus(UUID.randomUUID(),42,true);assertEquals(report,BackendStatus.decode(report.encode()));
        assertThrows(java.io.IOException.class,()->BackendStatus.decode(new byte[28]));
        assertThrows(java.io.IOException.class,()->BackendStatus.decode(Arrays.copyOf(report.encode(),30)));
        assertThrows(IllegalArgumentException.class,()->new BackendStatus(UUID.randomUUID(),-1,false));
    }
}
