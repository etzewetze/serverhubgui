package de.serverhub.velocity;
import de.serverhub.common.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

import de.serverhub.common.CentralMenuConfig;
class CentralMenuConfigTest {
    @Test void oldDefaultStatusIntervalsUpgradeWithABackupAndOtherEditsSurvive()throws Exception{
        String previous="config-version: 2\nstatus:\n  ping-interval-seconds: 10\n  timeout-ms: 2000\nmenu:\n  title: Unser Netzwerk\nservers: {}\n";Files.writeString(directory.resolve("servers.yml"),previous);
        var state=new CentralMenuConfig(directory).load(List.of("lobby"));assertEquals(1,state.options().number("status.ping-interval-seconds"));assertEquals(1000,state.options().number("status.timeout-ms"));assertEquals("Unser Netzwerk",state.options().text("menu.title"));
        assertEquals(previous,Files.readString(directory.resolve("servers-v2.4-backup.yml")));assertTrue(Files.readString(directory.resolve("servers.yml")).contains("report-port: 8125"));
        Files.writeString(directory.resolve("servers.yml"),"config-version: 2\nstatus:\n  ping-interval-seconds: 4\n  timeout-ms: 1500\nservers: {}\n");
        var custom=new CentralMenuConfig(directory).load(List.of());assertEquals(4,custom.options().number("status.ping-interval-seconds"));assertEquals(1500,custom.options().number("status.timeout-ms"));
    }
    @TempDir Path directory;
    @Test void discoversAllTargetsInOneFileAndPreservesEditsWhenAnotherIsAdded()throws Exception{
        var config=new CentralMenuConfig(directory);config.load(List.of("lobby","survival"));
        Path file=directory.resolve("servers.yml");String text=Files.readString(file);
        assertTrue(text.contains("lobby:"));assertTrue(text.contains("survival:"));
        text=text.replace("name: Lobby\n    color: '#FFF4D6'","name: Treffpunkt\n    color: '#12AB34'");Files.writeString(file,text);
        assertTrue(config.discover(List.of("lobby","survival","event")));
        var lobby=config.current().servers().stream().filter(s->s.entry().target().equals("lobby")).findFirst().orElseThrow();
        assertEquals("Treffpunkt",lobby.entry().name());assertEquals("#12AB34",lobby.entry().color());
        assertEquals(3,config.current().servers().size());
        assertFalse(config.discover(List.of("event")));assertTrue(Files.readString(file).contains("survival:"));
    }
    @Test void invalidReloadKeepsPreviousConfigurationAndTheEditedFile()throws Exception{
        var config=new CentralMenuConfig(directory);var previous=config.load(List.of("lobby"));
        Path file=directory.resolve("servers.yml");String invalid="menu:\n  font-size: 99\nservers: {}\n";Files.writeString(file,invalid);
        assertThrows(java.io.IOException.class,()->config.load(List.of("lobby")));
        assertSame(previous,config.current());assertEquals(invalid,Files.readString(file));
        Files.writeString(file,"servers:\n  lobby: {}\n  lobby: {}\n");
        assertThrows(java.io.IOException.class,()->config.load(List.of("lobby")));
    }
    @Test void dottedTargetsStayLiteralAndAllOptionsAreCentral()throws Exception{
        Files.writeString(directory.resolve("servers.yml"),"menu:\n  layout: grid\n  navigation: pages\n  show-page-info: false\nservers:\n  game.eu:\n    name: Europa\n    font-size: 10\n    color: '#123456'\n    status-override: sleeping\n");
        var state=new CentralMenuConfig(directory).load(List.of("game.eu"));
        assertEquals("game.eu",state.servers().getFirst().entry().target());
        assertEquals("sleeping",state.servers().getFirst().override());assertEquals(10,state.servers().getFirst().entry().size());
        assertEquals(2,state.options().columns());assertFalse(state.options().pageInfo(100));
    }
    @Test void theMaximumDiscoveredListCanBeSavedAndLoadedAgain()throws Exception{
        var names=java.util.stream.IntStream.range(0,256).mapToObj(i->"server-"+i).toList();
        var config=new CentralMenuConfig(directory);config.load(names);
        assertEquals(256,config.load(names).servers().size());
    }
}
