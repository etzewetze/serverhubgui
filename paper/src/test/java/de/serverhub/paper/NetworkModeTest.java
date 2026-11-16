package de.serverhub.paper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import de.serverhub.common.*;
import static org.junit.jupiter.api.Assertions.*;

class NetworkModeTest {
    @TempDir Path directory;
    @Test void detectsExistingForwardingWithoutABackendPluginSetting()throws Exception{
        assertFalse(NetworkMode.proxied(directory,"auto"));
        Files.createDirectories(directory.resolve("config"));
        Files.writeString(directory.resolve("config/paper-global.yml"),"proxies:\n  velocity:\n    enabled: true\n");
        assertTrue(NetworkMode.proxied(directory,"auto"));assertFalse(NetworkMode.proxied(directory,"standalone"));
        assertTrue(NetworkMode.proxied(directory,"proxy"));assertThrows(IllegalArgumentException.class,()->NetworkMode.proxied(directory,"bad"));
    }
    @Test void proxySnapshotReplacesLocalColorsLayoutFontAndHudOptions(){
        var o=new MenuOptions(Map.of("menu.layout","list","menu.navigation","scroll","shift-f-hint.enabled","false","hotbar-icon.slot","3"));
        var e=new MenuSnapshot.Entry("survival","Überleben","#12AB34",10,"serverhub.survival",List.of("Beschreibung"),MenuSnapshot.Status.SLEEPING);
        var settings=Settings.fromNetwork(new MenuSnapshot(UUID.randomUUID(),o,List.of(e)));
        assertEquals(0x12AB34,settings.entries().getFirst().color().value());assertEquals(10,settings.entries().getFirst().size());
        assertEquals(MenuSnapshot.Status.SLEEPING,settings.entries().getFirst().status());assertFalse(settings.hint());assertEquals(3,settings.slot());
        assertEquals(1,settings.menuOptions().columns());assertTrue(settings.menuOptions().scrolling());
    }
}
