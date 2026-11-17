package de.serverhub.velocity;
import de.serverhub.common.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PackUrlsTest {
    @Test void fixedReleaseUsesHttpsAndTheExactVersion(){
        assertEquals("https://github.com/etzewetze/serverhubgui/releases/download/v2.5.1/serverhub-pack.zip",PackUrls.RELEASE_URL);
    }
    @Test void selfHostUsesTheJoiningHostWithExplicitHttpPortAndHash(){
        assertEquals("http://play.example.org:8124/serverhub.zip?sha1="+"a".repeat(40),PackUrls.selfHosted("http","play.example.org",8124,"a".repeat(40)));
    }
    @Test void ipv6AndDefaultHttpsPortAreEncodedCorrectly(){
        assertEquals("https://[2001:db8::1]/serverhub.zip?sha1=abc",PackUrls.selfHosted("https","[2001:db8::1]",443,"abc"));
    }
    @Test void invalidExternalAddressesCannotDisableTheGate(){
        for(String raw:new String[]{"","file:///tmp/pack.zip","https://user:pass@example.org/a.zip","https://example.org/a.zip#fragment"})assertThrows(IllegalArgumentException.class,()->PackUrls.external(raw));
        assertThrows(IllegalArgumentException.class,()->PackUrls.selfHosted("http",null,8124,"abc"));
    }
}
