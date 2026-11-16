package de.serverhub.velocity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CentralMenuConfigTest {
    @TempDir Path directory;
    @Test void discoversAllTargetsInOneFileAndPreservesEditsWhenAnotherIsAdded()throws Exception{
        var config=new CentralMenuConfig(directory);config.load(List.of("lobby","survival"));
        Path file=directory.resolve("servers.yml");String text=Files.readString(file);
        assertTrue(text.contains("lobby:"));assertTrue(text.contains("survival:"));
        text=text.replace("name: Lobby","name: Treffpunkt").replaceFirst("(?m)^    color: '#FFF4D6'","    color: '#12AB34'");Files.writeString(file,text);
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
