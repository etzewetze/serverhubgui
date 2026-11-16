package de.serverhub.paper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class SettingsTest {
    @TempDir Path directory;
    @Test void serverNamesCanBeExtendedWithoutRegeneratingTextures()throws Exception{
        var p=directory.resolve("config.yml");
        Files.writeString(p,"servers:\n  new:\n    name: Neues Ziel\n    target: new-server\n    font-size: 12\n    color: '#12AB34'\n");
        var settings=Settings.read(p.toFile());assertEquals("new-server",settings.entries().getFirst().target());
        assertEquals(12,settings.entries().getFirst().size());
    }
    @Test void invalidColorFontAndSlotAreRejected()throws Exception{
        assertThrows(IllegalArgumentException.class,()->Settings.color("gold"));
        assertThrows(IllegalArgumentException.class,()->Settings.size(9));
        var p=directory.resolve("config.yml");Files.writeString(p,"hotbar-icon:\n  slot: 9\nservers: {}\n");
        assertThrows(IllegalArgumentException.class,()->Settings.read(p.toFile()));
    }
    @Test void labelsAreClippedByActualFontWidthAndKeepUmlauts()throws Exception{
        var text=new GuiText();
        assertTrue(text.width(text.fit("Ein sehr langer Servername",12,44),12)<=44);
        assertEquals("Zurück",text.fit("Zurück",8,44));
    }
    @Test void automaticServersNeedNoManualListAndKeepOverridesAndPermissions()throws Exception{
        var p=directory.resolve("config.yml");
        Files.writeString(p,"servers:\n  lobby:\n    name: Treffpunkt\n    target: lobby\n    permission: serverhub.lobby\n  secret:\n    hidden: true\n  removed:\n    name: Alter Server\n");
        var settings=Settings.read(p.toFile());
        var list=settings.resolve(java.util.List.of("lobby","survival","secret","survival"));
        assertEquals(2,list.size());assertEquals("Treffpunkt",list.getFirst().name());
        assertEquals("serverhub.lobby",list.getFirst().permission());
        assertEquals("Survival",list.getLast().name());assertEquals(12,list.getLast().size());
        assertTrue(settings.automatic());assertFalse(settings.icon());
        Files.writeString(p,"menu:\n  title: Serverauswahl\n");
        assertEquals("event",Settings.read(p.toFile()).resolve(java.util.List.of("event")).getFirst().target());
    }
    @Test void manualModeUsesConfiguredEntriesOnly()throws Exception{
        var p=directory.resolve("config.yml");
        Files.writeString(p,"server-discovery:\n  enabled: false\nservers:\n  configured:\n    target: lobby\n");
        assertEquals("lobby",Settings.read(p.toFile()).resolve(java.util.List.of("survival")).getFirst().target());
    }
}
