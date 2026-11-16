package de.serverhub.paper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import org.bukkit.configuration.file.YamlConfiguration;
import static org.junit.jupiter.api.Assertions.*;

class PaperConfigFilesTest {
    @TempDir Path directory;
    @Test void upgradePreservesTargetsPermissionsAndAnExactBackup()throws Exception{
        var p=directory.resolve("config.yml");
        String old="menu:\n  font-size: 10\n  title-color: '#E8C78F'\nhotbar-icon:\n  enabled: true\nservers:\n  test:\n    target: testserver\n    permission: serverhub.test\n";
        Files.writeString(p,old);assertTrue(PaperConfigFiles.upgrade(p.toFile()));
        assertEquals(old,Files.readString(directory.resolve("config-v2.1-backup.yml")));
        var settings=Settings.read(p.toFile());
        assertTrue(settings.automatic());assertFalse(settings.icon());assertEquals(12,settings.fontSize());
        assertEquals("testserver",settings.entries().getFirst().target());
        assertEquals("serverhub.test",settings.entries().getFirst().permission());
        assertEquals("#3D2817",settings.yaml().getString("menu.title-color"));
        assertFalse(PaperConfigFiles.upgrade(p.toFile()));
        var config=YamlConfiguration.loadConfiguration(p.toFile());config.set("hotbar-icon.enabled",true);config.save(p.toFile());
        assertFalse(PaperConfigFiles.upgrade(p.toFile()));assertTrue(Settings.read(p.toFile()).icon());
    }
}
