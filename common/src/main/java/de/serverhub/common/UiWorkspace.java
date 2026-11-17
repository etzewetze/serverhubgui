package de.serverhub.common;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Compiles drop-in PNGs into column glyphs so each image fits its native button exactly. */
public final class UiWorkspace {
    private UiWorkspace(){}
    public static UiCatalog prepare(Path directory,Path root,Map<String,byte[]> stock)throws IOException {
        Path folder=PackWorkspace.safe(root,"assets/serverhub/textures/backgrounds/source");Files.createDirectories(folder);
        Path imports=directory.resolve("backgrounds");Files.createDirectories(imports);if(Files.isSymbolicLink(imports))throw new IOException("Links im Hintergrundordner sind nicht erlaubt");
        try(var files=Files.list(imports)){var names=new HashSet<String>();for(Path file:files.sorted().toList())if(file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")){
            String name=file.getFileName().toString(),id=name.substring(0,name.length()-4).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]","_");if(!FontBook.validFamily(id)||!names.add(id)||Files.isSymbolicLink(file))throw new IOException("Ungültiger oder doppelter Hintergrund: "+name);
            PackWorkspace.readImage(file);PackWorkspace.atomic(folder.resolve(id+".png"),Files.readAllBytes(file));
        }}
        var sources=new TreeMap<String,Path>();try(var files=Files.list(folder)){for(Path file:files.sorted().toList())if(file.getFileName().toString().endsWith(".png")){String name=file.getFileName().toString(),id=name.substring(0,name.length()-4);if(!FontBook.validFamily(id)||Files.isSymbolicLink(file))throw new IOException("Ungültiger Hintergrund: "+name);sources.put(id,file);}}
        if(sources.size()>16)throw new IOException("Maximal 16 Button-Hintergründe einschließlich Vorlagen");
        var cache=new Properties();Path cacheFile=directory.resolve("background-cache.properties");if(Files.exists(cacheFile))try(var input=Files.newInputStream(cacheFile)){cache.load(input);}
        UiCatalog previous=UiCatalog.empty();Path catalog=root.resolve("serverhub-ui.properties");if(Files.exists(catalog))try(var input=Files.newInputStream(catalog)){previous=UiCatalog.properties(input);}
        int left=PackWorkspace.readImage(root.resolve("assets/serverhub/textures/gui/scroll_left.png")).getWidth(),right=PackWorkspace.readImage(root.resolve("assets/serverhub/textures/gui/scroll_right.png")).getWidth();
        var skins=new TreeMap<String,UiCatalog.Skin>();var providers=new ArrayList<Map<String,Object>>();var advances=new TreeMap<String,Integer>();advances.put("\uF8FF",-1);providers.add(Map.of("type","space","advances",advances));boolean changed=false;int index=0;
        Path output=root.resolve("assets/serverhub/textures/backgrounds/generated");Files.createDirectories(output);
        for(var entry:sources.entrySet()){
            String id=entry.getKey(),path="assets/serverhub/textures/backgrounds/source/"+id+".png";byte[] source=Files.readAllBytes(entry.getValue());String digest=PackWorkspace.sha256(source),known=cache.getProperty(id);
            if(known==null&&stock.containsKey(path))known=PackWorkspace.sha256(stock.get(path));
            BufferedImage input=PackWorkspace.readImage(entry.getValue());int width=Math.clamp(Math.round(input.getWidth()*20f/input.getHeight()),16,192),base=0xE000+index++*384;
            for(boolean round:new boolean[]{false,true}){
                String key=id+(round?".round":""),name=id+(round?"_round":"")+".png";Path target=output.resolve(name);int code=base+(round?192:0);
                if(!digest.equals(known)||!Files.exists(target)){
                    var image=new BufferedImage(width,20,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);if(round)g.setClip(new RoundRectangle2D.Float(0,0,width,20,8,8));g.drawImage(input,0,0,width,20,null);g.dispose();ImageIO.write(image,"png",target.toFile());changed=true;
                }
                var pixels=PackWorkspace.readImage(target);if(pixels.getWidth()!=width||pixels.getHeight()!=20)throw new IOException("Generierte Hintergrundgröße ungültig; Quelldatei unter backgrounds/ bearbeiten");for(int column=0;column<width;column++){boolean visible=false;for(int y=0;y<20;y++)if((pixels.getRGB(column,y)>>>24)!=0){visible=true;break;}if(!visible)advances.put(String.valueOf((char)(code+column)),2);}
                skins.put(key,new UiCatalog.Skin(code,width));StringBuilder chars=new StringBuilder();for(int i=0;i<width;i++)chars.append((char)(code+i));providers.add(Map.of("type","bitmap","file","serverhub:backgrounds/generated/"+name,"height",20,"ascent",12,"chars",List.of(chars.toString())));
            }cache.setProperty(id,digest);
        }
        var next=new UiCatalog(left,right,Files.exists(root.resolve("assets/serverhub/font/icons.json")),skins);
        Path definition=root.resolve("assets/serverhub/font/backgrounds.json");var definitionJson=Json.read(Json.write(Map.of("providers",providers)));boolean sameDefinition=Files.exists(definition)&&Json.read(Files.readString(definition)).equals(definitionJson);
        if(changed||!next.equals(previous)||!sameDefinition){PackWorkspace.atomic(root.resolve("assets/serverhub/font/backgrounds.json"),Json.write(Map.of("providers",providers)).getBytes(java.nio.charset.StandardCharsets.UTF_8));PackWorkspace.atomic(catalog,PackWorkspace.properties(next.properties()));}
        PackWorkspace.atomic(cacheFile,PackWorkspace.properties(cache));return next;
    }
}
