package de.serverhub.velocity;

import de.serverhub.common.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class PackWorkspaceTest {
    @TempDir Path directory;
    byte[] stock()throws Exception {try(var input=getClass().getResourceAsStream("/serverhub-pack.zip")){return Objects.requireNonNull(input).readAllBytes();}}
    @Test void anUntouchedEditableDirectoryKeepsThePublishedZipByteForByte()throws Exception {
        byte[] stock=stock();var first=PackWorkspace.prepare(directory,stock,MenuOptions.defaults());
        assertArrayEquals(stock,first.bytes());assertFalse(first.fonts().isEmpty());
        assertArrayEquals(stock,PackWorkspace.prepare(directory,stock,MenuOptions.defaults()).bytes());
        assertTrue(Files.isRegularFile(directory.resolve("pack/assets/serverhub/textures/gui/button_mid.png")));
        assertFalse(Files.exists(directory.resolve("pack-font-cache.properties.tmp")));
    }
    @Test void editsArePackagedDeterministicallyAndThePreviousZipIsBackedUp()throws Exception {
        byte[] stock=stock();var before=PackWorkspace.prepare(directory,stock,MenuOptions.defaults());
        Files.writeString(directory.resolve("pack/MEINE-AENDERUNG.txt"),"Eigene Texturen");
        var after=PackWorkspace.prepare(directory,stock,MenuOptions.defaults());assertNotEquals(before.sha1(),after.sha1());
        assertArrayEquals(after.bytes(),PackWorkspace.prepare(directory,stock,MenuOptions.defaults()).bytes());
        try(var paths=Files.list(directory)){Path backup=paths.filter(p->p.getFileName().toString().startsWith("serverhub-pack-backup-")).findFirst().orElseThrow();assertArrayEquals(stock,Files.readAllBytes(backup));}
        try(var zip=new ZipFile(directory.resolve("serverhub-pack.zip").toFile())){assertNotNull(zip.getEntry("MEINE-AENDERUNG.txt"));}
    }
    @Test void aLocalCustomTtfBuildsEverySizeAndKeepsManualAtlasEditsOnReload()throws Exception {
        byte[] stock=stock();PackWorkspace.prepare(directory,stock,MenuOptions.defaults());
        Path custom=directory.resolve("pack/assets/serverhub/fonts/custom.ttf");Files.copy(directory.resolve("pack/assets/serverhub/fonts/mono.ttf"),custom);
        var options=new MenuOptions(Map.of("menu.font-family","custom"));var first=PackWorkspace.prepare(directory,stock,options);
        for(int size:FontBook.SIZES)assertEquals("custom",first.fonts().family("custom",size));
        Path atlas=directory.resolve("pack/assets/serverhub/textures/gui/latin_custom_12.png");var image=javax.imageio.ImageIO.read(atlas.toFile());
        int index='W'-32,x=(index%16)*24,y=(index/16)*24;
        image.setRGB(x+22,y+12,0xFFFFFFFF);javax.imageio.ImageIO.write(image,"png",atlas.toFile());
        byte[] edited=Files.readAllBytes(atlas);var second=PackWorkspace.prepare(directory,stock,options);
        assertArrayEquals(edited,Files.readAllBytes(atlas));assertEquals(24,second.fonts().advance("custom",12,'W'));
        assertNotEquals(first.sha1(),second.sha1());assertArrayEquals(second.bytes(),PackWorkspace.prepare(directory,stock,options).bytes());
    }
    @Test void unsafePathsAndMissingCustomFontsAreRejected()throws Exception {
        byte[] stock=stock();assertThrows(java.io.IOException.class,()->PackWorkspace.prepare(directory,stock,new MenuOptions(Map.of("menu.font-family","custom"))));
        assertThrows(java.io.IOException.class,()->PackWorkspace.prepare(directory,stock,new MenuOptions(Map.of("font.url","http://example.org/font.ttf"))));
        Files.createSymbolicLink(directory.resolve("pack/outside"),directory.getParent());
        assertThrows(java.io.IOException.class,()->PackWorkspace.prepare(directory,stock,MenuOptions.defaults()));
    }
    @Test void localDeliveryUsesTheSameWorkspaceWithoutDependingOnAProxy()throws Exception {
        byte[] stock=stock();Files.writeString(directory.resolve("config.properties"),"pack.mode=external\npack.external-url=https://example.org/pack.zip\n");
        try(var local=new LocalPackDelivery(directory,stock)){
            var original=local.state();assertEquals("local",original.menu().servers().getFirst().entry().target());assertArrayEquals(stock,original.pack().bytes());
            Files.writeString(directory.resolve("pack/local.txt"),"Lokale Änderung");var next=local.reload();
            assertNotEquals(original.pack().sha1(),next.pack().sha1());assertEquals("https://example.org/pack.zip",local.url(original,null));
        }
    }
}
