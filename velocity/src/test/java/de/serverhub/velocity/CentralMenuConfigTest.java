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
        String previous="config-version: 2\nmenu:\n  title: Unser Netzwerk\nstatus:\n  ping-interval-seconds: 10\n  timeout-ms: 2000\nservers: {}\n";Files.writeString(directory.resolve("servers.yml"),previous);
        var state=new CentralMenuConfig(directory).load(List.of("lobby"));assertEquals(1,state.options().number("status.ping-interval-seconds"));assertEquals(1000,state.options().number("status.timeout-ms"));assertEquals("Unser Netzwerk",state.options().text("menu.title"));
        assertEquals(previous,Files.readString(directory.resolve("servers-v2.4-backup.yml")));assertTrue(Files.readString(directory.resolve("config.yml")).contains("report-port: 8125"));
        String saved=Files.readString(directory.resolve("config.yml"));assertTrue(saved.indexOf("\nstatus:\n")<saved.indexOf("\nmenu:\n"));
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
        assertTrue(config.discover(List.of("event")));assertFalse(Files.readString(file).contains("survival:"));assertTrue(Files.readString(directory.resolve("config.yml")).contains("survival:"));
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
    @Test void twoFiveDefaultsMigrateAndNamedSourcesAndServerOverridesSurviveDiscovery()throws Exception {
        String previous="config-version: 3\nmenu:\n  button-width: 140\n  title-padding: 12\n  title-max-width: 320\nfonts:\n  first_font:\n    url: 'https://example.org/first.ttf'\n    refresh: false\n  second-font: 'https://example.org/second.otf'\nservers:\n  challenges:\n    width: 250\n    background: my_image.png\n    rounded: true\n    amp-instance: unique-amp-id\n    hover-enabled: false\n    prefix: '{players} Gäste'\n";
        Files.writeString(directory.resolve("servers.yml"),previous);var state=new CentralMenuConfig(directory).load(List.of("challenges","lobby"));
        assertEquals(previous,Files.readString(directory.resolve("servers-v2.5-backup.yml")));assertEquals(0,state.options().number("menu.button-width"));assertEquals(48,state.options().number("menu.title-padding"));assertEquals(400,state.options().number("menu.title-max-width"));assertEquals(2,state.fontSources().size());
        var target=state.servers().stream().filter(s->s.entry().target().equals("challenges")).findFirst().orElseThrow();assertEquals("unique-amp-id",target.ampInstance());assertEquals(250,target.entry().style().width());assertEquals("my_image",target.entry().style().background());assertTrue(target.entry().style().rounded());assertFalse(target.entry().style().hoverEnabled());
        assertTrue(Files.readString(directory.resolve("config.yml")).contains("button-min-width: 180"));
    }
    @Test void activeServersSortNumericallyWhileHiddenAndRemovedEntriesAreArchivedAndRestored()throws Exception {
        String legacy="config-version: 4\nmenu:\n  title: Mein Netzwerk\nservers:\n  alpha:\n    sort: 10\n  beta:\n    sort: 2\n  hidden:\n    hidden: true\n    order: 1\n    color: '#123456'\n  removed:\n    order: 3\n    name: Wieder da\n";Files.writeString(directory.resolve("servers.yml"),legacy);var config=new CentralMenuConfig(directory);var first=config.load(List.of("alpha","beta","hidden"));
        String active=Files.readString(directory.resolve("servers.yml")),general=Files.readString(directory.resolve("config.yml"));assertFalse(active.contains("menu:"));assertFalse(active.contains("\n  hidden:\n"));assertFalse(active.contains("\n  removed:\n"));assertTrue(active.indexOf("beta:")<active.indexOf("alpha:"));assertTrue(general.contains("inactive-servers:"));assertTrue(general.contains("removed:"));assertEquals(legacy,Files.readString(directory.resolve("servers-v2.5.2-backup.yml")));assertEquals("Mein Netzwerk",first.options().text("menu.title"));
        assertTrue(config.discover(List.of("alpha","beta","hidden","removed")));active=Files.readString(directory.resolve("servers.yml"));assertTrue(active.contains("name: Wieder da"));assertFalse(active.contains("\n  hidden:\n"));assertEquals(List.of("hidden","beta","removed","alpha"),config.current().servers().stream().map(s->s.entry().target()).toList());
        Files.writeString(directory.resolve("config.yml"),Files.readString(directory.resolve("config.yml")).replace("hidden: true","hidden: false"));config.load(List.of("alpha","beta","hidden","removed"));assertTrue(Files.readString(directory.resolve("servers.yml")).contains("color: '#123456'"));assertFalse(Files.readString(directory.resolve("servers.yml")).contains("order:"));assertFalse(config.discover(List.of("removed","alpha","hidden","beta")));
    }
    @Test void sortOverridesLegacyOrderAndBadGeneralSettingsDoNotRewriteEitherFile()throws Exception {
        Files.writeString(directory.resolve("servers.yml"),"servers:\n  alpha:\n    sort: 2\n    order: 0\n  beta:\n    order: 1\n");var config=new CentralMenuConfig(directory);var first=config.load(List.of("alpha","beta"));assertEquals(List.of("beta","alpha"),first.servers().stream().map(s->s.entry().target()).toList());assertFalse(Files.readString(directory.resolve("servers.yml")).contains("order:"));String servers=Files.readString(directory.resolve("servers.yml")),broken="config-version: 5\nmenu:\n  font-size: 99\n";Files.writeString(directory.resolve("config.yml"),broken);assertThrows(java.io.IOException.class,()->config.load(List.of("alpha","beta")));assertSame(first,config.current());assertEquals(servers,Files.readString(directory.resolve("servers.yml")));assertEquals(broken,Files.readString(directory.resolve("config.yml")));
    }
    @Test void migratingAPartialLegacySectionPreservesOtherExistingGeneralValues()throws Exception {
        Files.writeString(directory.resolve("config.yml"),"menu:\n  title: Vorhandener Titel\n  font-size: 14\nnetwork:\n  mode: standalone\n");Files.writeString(directory.resolve("servers.yml"),"config-version: 4\nmenu:\n  layout: grid\nservers: {}\n");var state=new CentralMenuConfig(directory).load(List.of("lobby"));assertEquals("Vorhandener Titel",state.options().text("menu.title"));assertEquals(14,state.options().number("menu.font-size"));assertEquals("grid",state.options().text("menu.layout"));assertTrue(Files.readString(directory.resolve("config.yml")).contains("mode: standalone"));assertTrue(Files.exists(directory.resolve("config-v2.5.2-backup.yml")));
    }
}
