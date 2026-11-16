package de.serverhub.velocity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ProxyConfigFilesTest {
    @TempDir Path directory;
    byte[] defaults()throws Exception{try(var input=getClass().getResourceAsStream("/config.properties")){return Objects.requireNonNull(input).readAllBytes();}}
    @Test void migrationPreservesOldConfigAndMovesFromPrivatePortToHttps()throws Exception{
        String old="pack.url=http://wrong-host:8088/serverhub.zip\nhttp.enabled=true\npack.timeout-seconds=180\n";
        Files.writeString(directory.resolve("config.properties"),old);
        assertTrue(ProxyConfigFiles.migrate(directory,defaults()));
        assertEquals(old,Files.readString(directory.resolve("config-v2.0-backup.properties")));
        Properties p=new Properties();try(var r=Files.newBufferedReader(directory.resolve("config.properties"),StandardCharsets.UTF_8)){p.load(r);}
        assertEquals("github",p.getProperty("pack.mode"));assertEquals("180",p.getProperty("pack.timeout-seconds"));assertNull(p.getProperty("pack.url"));
        assertFalse(ProxyConfigFiles.migrate(directory,defaults()));
    }
    @Test void explicitCustomModeIsNeverOverwritten()throws Exception{
        String current="pack.mode=external\npack.external-url=https://example.org/custom.zip\n";
        Files.writeString(directory.resolve("config.properties"),current);
        assertFalse(ProxyConfigFiles.migrate(directory,defaults()));assertEquals(current,Files.readString(directory.resolve("config.properties")));
    }
    @Test void freshConfigurationUsesGithubWithoutStartingAnHttpServer()throws Exception{
        ProxyConfigFiles.migrate(directory,defaults());
        try(var input=getClass().getResourceAsStream("/serverhub-pack.zip")){Files.copy(Objects.requireNonNull(input),directory.resolve("serverhub-pack.zip"));}
        var settings=ProxySettings.read(directory);assertFalse(settings.http());assertEquals(PackUrls.RELEASE_URL,settings.url(null));
    }
    @Test void externalModeWithEmptyUrlIsRejected()throws Exception{
        Files.writeString(directory.resolve("config.properties"),"pack.mode=external\npack.external-url=\n");
        assertThrows(java.io.IOException.class,()->ProxySettings.read(directory));
    }
}
