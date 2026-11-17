package de.serverhub.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NewsTest {
    @TempDir Path directory;
    @Test void newsPersistsAtTheAuthorityAndCanBeRemoved()throws Exception {
        var news=new NewsStore(directory);news.start("Challenges","&aNeue Welt\\nJetzt entdecken!");
        assertTrue(news.active("challenges"));assertEquals("&aNeue Welt\nJetzt entdecken!",new NewsStore(directory).get("challenges"));
        news.stop("challenges");assertFalse(new NewsStore(directory).active("Challenges"));
        assertThrows(java.io.IOException.class,()->news.start("../outside","text"));
        assertThrows(java.io.IOException.class,()->news.start("lobby","x".repeat(4097)));
    }
    @Test void currentServerAndExplicitLobbyTargetHaveTheSameWireOperation()throws Exception {
        var current=NewsCommand.parse(new String[]{"news","start","Neue","Welt"},"survival");
        var targeted=NewsCommand.parse(new String[]{"news","start","--server","survival","Neue","Welt"},"lobby");
        assertEquals(current,targeted);assertEquals("survival",NewsCommand.parse(new String[]{"news","stop"},"survival").target());
        assertThrows(IllegalArgumentException.class,()->NewsCommand.parse(new String[]{"news","start","Text"},""));
        assertThrows(IllegalArgumentException.class,()->NewsCommand.parse(new String[]{"news","stop","extra"},"survival"));
        String longMessage="Neue Welt: "+"Ä🌍".repeat(1000);var operation=new NewsCommand(true,"survival",longMessage);
        var packet=Wire.decode(Wire.encode(Wire.Type.NEWS_CHANGE,UUID.randomUUID(),operation.wire()));
        assertEquals(operation,NewsCommand.decode(packet.values()));
        var content=Wire.decode(Wire.encode(Wire.Type.NEWS_CONTENT,packet.nonce(),NewsCommand.content("survival",longMessage)));
        assertEquals(longMessage,String.join("",content.values().subList(1,content.values().size())));
    }
    @Test void stylesFontsAndBackgroundGeometryBelongToThePlayersPack()throws Exception {
        int[] widths=new int[192];Arrays.fill(widths,7);
        var fonts=new FontBook(Map.of("my_font.12",widths,"another-font.16",widths));
        var ui=new UiCatalog(40,40,Map.of("custom.round",new UiCatalog.Skin(0xE000,120)));
        var style=new ServerStyle(242,"custom","#123456",true,12,80,"12 Spieler",false,"",true);
        var entry=new MenuSnapshot.Entry("survival","Survival","#FFFFFF",12,"",List.of(),MenuSnapshot.Status.SLEEPING,"my_font",style);
        var snapshot=new MenuSnapshot(UUID.randomUUID(),MenuOptions.defaults(),List.of(entry),fonts,ui);
        assertEquals(snapshot,MenuSnapshot.decode(List.of(snapshot.encode())));
        assertEquals(Set.of("my_font","another-font"),fonts.families());
        assertEquals("Survival 12/80 Schläft",MenuTemplate.render("{name} {players}/{max_players} {status}","Survival","survival",entry.status(),12,80));
        assertFalse(MenuOptions.defaults().flag("hover.enabled"));
    }
}
