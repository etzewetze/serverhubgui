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
    @Test void theRollEndPiecesMeetTheMiddleAtTheSameVisibleTopEdge()throws Exception {
        PackWorkspace.prepare(directory,stock(),MenuOptions.defaults());Path root=directory.resolve("pack/assets/serverhub");var definition=Json.read(Files.readString(root.resolve("font/gui.json"))).getAsJsonObject();var edges=new HashMap<String,int[]>();
        for(var value:definition.getAsJsonArray("providers")){
            var provider=value.getAsJsonObject();String file=Json.string(provider,"file","");if(!file.matches("serverhub:gui/scroll_(left|mid|right)\\.png"))continue;String part=file.substring(file.lastIndexOf('_')+1,file.length()-4);var image=javax.imageio.ImageIO.read(root.resolve("textures/"+file.substring("serverhub:".length())).toFile());int x=part.equals("left")?image.getWidth()-1:0,min=100,max=-100;int ascent=provider.get("ascent").getAsInt();
            for(int y=0;y<image.getHeight();y++)if((image.getRGB(x,y)>>>24)>=128){min=Math.min(min,y-ascent);max=Math.max(max,y-ascent);}edges.put(part,new int[]{min,max});
        }
        assertEquals(3,edges.size());for(String side:List.of("left","right")){assertEquals(edges.get("mid")[0],edges.get(side)[0],"The roll must have no step at its upper seam");assertTrue(Math.abs(edges.get("mid")[1]-edges.get(side)[1])<=1);}
    }
    @Test void anUntouchedTwoFourFolderUpgradesToTheExactNewGithubPack()throws Exception {
        byte[] stock=stock();PackWorkspace.prepare(directory,stock,MenuOptions.defaults());Path metadata=directory.resolve("pack/pack.mcmeta");String factory=Files.readString(metadata);Files.writeString(metadata,factory.replace("ServerHub 2.5.2","ServerHub 2.4"));
        Files.delete(directory.resolve("pack-bundled-files.properties"));
        var upgraded=PackWorkspace.prepare(directory,stock,MenuOptions.defaults());assertArrayEquals(stock,upgraded.bytes());assertEquals(factory,Files.readString(metadata));
    }
    @Test void factoryUpgradesKeepCustomTexturesAndChooseTheEditableDeliveryMode()throws Exception {
        byte[] stock=stock();PackWorkspace.prepare(directory,stock,MenuOptions.defaults());Path metadata=directory.resolve("pack/pack.mcmeta");Files.writeString(metadata,Files.readString(metadata).replace("ServerHub 2.5.2","ServerHub 2.4"));Files.delete(directory.resolve("pack-bundled-files.properties"));
        Path button=directory.resolve("pack/assets/serverhub/textures/gui/button_mid.png");var texture=javax.imageio.ImageIO.read(button.toFile());texture.setRGB(0,5,0xFF123456);javax.imageio.ImageIO.write(texture,"png",button.toFile());byte[] custom=Files.readAllBytes(button);
        Files.writeString(directory.resolve("config.properties"),"pack.mode=auto\n");var upgraded=PackWorkspace.prepare(directory,stock,MenuOptions.defaults());assertArrayEquals(custom,Files.readAllBytes(button));assertFalse(PackWorkspace.bundled(upgraded,stock));assertTrue(ProxySettings.read(directory).http());
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
    @Test void multipleNamedFontsProduceValidJsonAndAnActuallyDownloadablePack()throws Exception {
        byte[] stock=stock();PackWorkspace.prepare(directory,stock,MenuOptions.defaults());
        Path fonts=directory.resolve("fonts");Files.copy(directory.resolve("pack/assets/serverhub/fonts/mono.ttf"),fonts.resolve("my_font.ttf"));Files.copy(directory.resolve("pack/assets/serverhub/fonts/serif.ttf"),fonts.resolve("second-font.otf"));
        var pack=PackWorkspace.prepare(directory,stock,new MenuOptions(Map.of("menu.font-family","my_font","buttons.close.font-family","second-font")));
        assertTrue(pack.fonts().families().containsAll(List.of("my_font","second-font")));assertFalse(Files.exists(directory.resolve("font-problems.txt")));
        try(var zip=new ZipFile(directory.resolve("serverhub-pack.zip").toFile())){for(String family:List.of("my_font","second-font"))for(int size:FontBook.SIZES){var entry=zip.getEntry("assets/serverhub/font/label_"+family+"_"+size+".json");var json=Json.read(new String(zip.getInputStream(entry).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));assertTrue(json.getAsJsonObject().getAsJsonArray("providers").size()>0);}}
        try(var host=new PackHost("127.0.0.1",0,pack);var client=java.net.http.HttpClient.newHttpClient()){
            String url="http://127.0.0.1:"+host.port()+"/serverhub.zip?sha1="+pack.sha1();
            var result=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray());assertEquals(200,result.statusCode());assertArrayEquals(pack.bytes(),result.body());
        }
    }
    @Test void oneBrokenFontDoesNotPoisonThePackOrAnAlreadyWorkingFamily()throws Exception {
        byte[] stock=stock();PackWorkspace.prepare(directory,stock,MenuOptions.defaults());Files.writeString(directory.resolve("pack/assets/serverhub/fonts/custom.ttf"),"not a font");
        var fallback=PackWorkspace.prepare(directory,stock,new MenuOptions(Map.of("menu.font-family","custom")));assertArrayEquals(stock,fallback.bytes());assertEquals("sans",fallback.fonts().family("custom",12));assertTrue(Files.readString(directory.resolve("font-problems.txt")).contains("custom"));
        Files.copy(directory.resolve("pack/assets/serverhub/fonts/mono.ttf"),directory.resolve("fonts/custom.ttf"));var working=PackWorkspace.prepare(directory,stock,new MenuOptions(Map.of("menu.font-family","custom")));assertEquals("custom",working.fonts().family("custom",12));
        Files.writeString(directory.resolve("fonts/custom.ttf"),"bad replacement");assertArrayEquals(working.bytes(),PackWorkspace.prepare(directory,stock,new MenuOptions(Map.of("menu.font-family","custom"))).bytes());
    }
    @Test void aServerBackgroundCompilesToTheSameAlignedBoxWithOptionalRoundedAlpha()throws Exception {
        byte[] stock=stock();PackWorkspace.prepare(directory,stock,MenuOptions.defaults());var image=new java.awt.image.BufferedImage(120,20,java.awt.image.BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();g.setColor(java.awt.Color.BLUE);g.fillRect(0,0,120,20);g.dispose();javax.imageio.ImageIO.write(image,"png",directory.resolve("backgrounds/challenges.png").toFile());
        var pack=PackWorkspace.prepare(directory,stock,MenuOptions.defaults());var skin=pack.ui().skin("challenges",true);assertNotNull(skin);assertEquals(120,skin.columns());assertEquals(40,pack.ui().rollLeft());
        var rounded=javax.imageio.ImageIO.read(directory.resolve("pack/assets/serverhub/textures/backgrounds/generated/challenges_round.png").toFile());assertEquals(0,rounded.getRGB(0,0)>>>24);assertEquals(255,rounded.getRGB(60,10)>>>24);
        var definition=Json.read(Files.readString(directory.resolve("pack/assets/serverhub/font/backgrounds.json"))).getAsJsonObject();assertTrue(definition.getAsJsonArray("providers").size()>1);assertArrayEquals(pack.bytes(),PackWorkspace.prepare(directory,stock,MenuOptions.defaults()).bytes());
    }
    @Test void unsafePathsAreRejectedAndMissingFontsFallBack()throws Exception {
        byte[] stock=stock();assertArrayEquals(stock,PackWorkspace.prepare(directory,stock,new MenuOptions(Map.of("menu.font-family","custom"))).bytes());
        assertTrue(Files.readString(directory.resolve("font-problems.txt")).contains("custom"));
        assertArrayEquals(stock,PackWorkspace.prepare(directory,stock,new MenuOptions(Map.of("font.url","http://example.org/font.ttf"))).bytes());
        assertTrue(Files.readString(directory.resolve("font-problems.txt")).contains("HTTPS"));
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
    @Test void automaticDeliverySwitchesOnlyEditedPacksToTheBuiltInHost()throws Exception {
        byte[] stock=stock();Files.writeString(directory.resolve("config.properties"),"pack.mode=auto\n");PackWorkspace.prepare(directory,stock,MenuOptions.defaults());
        var defaults=ProxySettings.read(directory);assertFalse(defaults.http());assertEquals(PackUrls.RELEASE_URL,defaults.url(null));
        Files.writeString(directory.resolve("pack/modified.txt"),"Custom");PackWorkspace.prepare(directory,stock,MenuOptions.defaults());
        var custom=ProxySettings.read(directory);assertTrue(custom.http());assertTrue(custom.url("play.example.org").contains(custom.pack().sha1()));
        Files.writeString(directory.resolve("config.properties"),"pack.mode=github\n");assertThrows(java.io.IOException.class,()->ProxySettings.read(directory));
    }
}
