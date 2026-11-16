package de.serverhub.common;

import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Editable pack directory, bounded font imports and deterministic packaging. No subprocesses. */
public final class PackWorkspace {
    private static final int MAX_PACK=64*1024*1024,MAX_FONT=8*1024*1024;
    private static final String WIDTHS="serverhub-font-widths.properties";
    private PackWorkspace(){}
    public static PackData prepare(Path directory,byte[] bundled,MenuOptions settings)throws IOException {
        Files.createDirectories(directory);Path root=directory.resolve("pack");
        if(Files.isSymbolicLink(root))throw new IOException("pack/ darf kein symbolischer Link sein");
        Map<String,byte[]> stock=unpack(bundled);Files.createDirectories(root);
        // New assets are added on upgrade. Existing editable files are never overwritten.
        for(var entry:stock.entrySet()){
            Path file=safe(root,entry.getKey());if(!Files.exists(file)){Files.createDirectories(file.getParent());Files.write(file,entry.getValue(),StandardOpenOption.CREATE_NEW);}
        }
        String url=settings.text("font.url");
        Path custom=safe(root,"assets/serverhub/fonts/custom.ttf"),urlCache=directory.resolve("font-url.txt");
        if(!url.isBlank()&&(settings.flag("font.refresh")||!Files.isRegularFile(custom)||!Files.exists(urlCache)||!Files.readString(urlCache).equals(url))){
            byte[] font=download(url);validateFont(font);Files.createDirectories(custom.getParent());atomic(custom,font);atomic(urlCache,url.getBytes(StandardCharsets.UTF_8));
        }
        Properties cache=new Properties();Path cacheFile=directory.resolve("pack-font-cache.properties");
        if(Files.exists(cacheFile))try(var input=Files.newInputStream(cacheFile)){cache.load(input);}
        boolean cacheChanged=false;
        for(String family:FontBook.FAMILIES){
            Path source=safe(root,"assets/serverhub/fonts/"+family+".ttf");if(!Files.isRegularFile(source))continue;
            if(Files.size(source)>MAX_FONT)throw new IOException("Schriftdatei ist größer als 8 MiB");byte[] font=Files.readAllBytes(source);
            String digest=sha256(font),known=cache.getProperty(family);
            if(known==null){byte[] original=stock.get("assets/serverhub/fonts/"+family+".ttf");known=original==null?"":sha256(original);}
            if(!digest.equals(known)||Arrays.stream(FontBook.SIZES).anyMatch(size->!Files.isRegularFile(root.resolve("assets/serverhub/font/label_"+family+"_"+size+".json"))||!Files.isRegularFile(root.resolve("assets/serverhub/textures/gui/latin_"+family+"_"+size+".png"))))rasterize(root,family,font);
            cache.setProperty(family,digest);cacheChanged=true;
        }
        FontBook book=measure(root);byte[] metrics=properties(book.properties());
        if((settings.text("menu.font-family").equals("custom")||List.of("previous","next","close").stream().anyMatch(b->settings.text("buttons."+b+".font-family").equals("custom")))&&!book.family("custom",12).equals("custom"))throw new IOException("Für font-family: custom fehlt font.url oder pack/assets/serverhub/fonts/custom.ttf");
        // Keep the exact bundled bytes for an untouched directory, including GitHub hashes.
        Path widths=root.resolve(WIDTHS);Properties existing=new Properties();if(Files.exists(widths))try(var in=Files.newInputStream(widths)){existing.load(in);}if(!existing.equals(book.properties()))atomic(widths,metrics);
        Map<String,byte[]> files=readFiles(root);boolean unchanged=same(files,stock);
        byte[] zip=unchanged?bundled:zip(files);Properties delivery=new Properties();Path config=directory.resolve("config.properties");if(Files.exists(config))try(var reader=Files.newBufferedReader(config,StandardCharsets.UTF_8)){delivery.load(reader);}
        Path archive=safe(directory,delivery.getProperty("pack.file","serverhub-pack.zip"));if(archive.startsWith(root))throw new IOException("pack.file darf nicht im bearbeitbaren pack/-Ordner liegen");Files.createDirectories(archive.getParent());
        if(Files.exists(archive)&&!Arrays.equals(Files.readAllBytes(archive),zip)){
            byte[] old=Files.readAllBytes(archive);Path backup=directory.resolve("serverhub-pack-backup-"+sha256(old).substring(0,16)+".zip");
            if(!Files.exists(backup))Files.write(backup,old,StandardOpenOption.CREATE_NEW);
        }
        atomic(archive,zip);if(cacheChanged)atomic(cacheFile,properties(cache));
        return PackData.read(archive);
    }
    public static boolean bundled(PackData pack,byte[] stock){return Arrays.equals(pack.bytes(),stock);}
    private static Font validateFont(byte[] bytes)throws IOException {
        try{return Font.createFont(Font.TRUETYPE_FONT,new ByteArrayInputStream(bytes));}
        catch(java.awt.FontFormatException|RuntimeException e){throw new IOException("Ungültige TTF-/OTF-Schriftdatei",e);}
    }
    private static byte[] download(String raw)throws IOException {
        URI uri;
        try{uri=URI.create(raw);checkUrl(uri);}catch(IllegalArgumentException e){throw new IOException("font.url muss eine direkte HTTPS-TTF-/OTF-Adresse sein",e);}
        try(var client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build()){
            for(int redirects=0;redirects<5;redirects++){
                var response=client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
                try(var body=response.body()){
                    if(Set.of(301,302,303,307,308).contains(response.statusCode())){uri=uri.resolve(response.headers().firstValue("location").orElseThrow(()->new IOException("Schrift-Weiterleitung ohne Ziel")));checkUrl(uri);continue;}
                    if(response.statusCode()!=200)throw new IOException("Schriftdownload: HTTP "+response.statusCode());
                    byte[] bytes=body.readNBytes(MAX_FONT+1);if(bytes.length>MAX_FONT)throw new IOException("Schriftdownload ist größer als 8 MiB");return bytes;
                }
            }
            throw new IOException("Zu viele Schrift-Weiterleitungen");
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Schriftdownload unterbrochen",e);}catch(IllegalArgumentException e){throw new IOException("Ungültige Schrift-Weiterleitung",e);}
    }
    private static void checkUrl(URI uri){if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null)throw new IllegalArgumentException("HTTPS ohne Zugangsdaten erforderlich");}
    private static void rasterize(Path root,String family,byte[] bytes)throws IOException {
        Font source=validateFont(bytes);Path gui=root.resolve("assets/serverhub/textures/gui");Files.createDirectories(gui);
        List<String> rows=new ArrayList<>();for(int row=0;row<12;row++){var s=new StringBuilder();for(int col=0;col<16;col++){int i=row*16+col;s.append((char)(i<96?i+32:i+64));}rows.add(s.toString());}
        for(int size:FontBook.SIZES){
            var atlas=new BufferedImage(384,288,BufferedImage.TYPE_INT_ARGB);var g=atlas.createGraphics();
            g.setFont(source.deriveFont((float)size));g.setColor(java.awt.Color.WHITE);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            for(int i=0;i<192;i++){int code=i<96?i+32:i+64;if(code!=32&&code!=160)g.drawString(String.valueOf((char)code),(i%16)*24+1,(i/16)*24+18);}g.dispose();
            String texture="latin_"+family+"_"+size+".png";ImageIO.write(atlas,"png",gui.resolve(texture).toFile());
            for(boolean shadow:new boolean[]{false,true}){
                var providers=List.of(Map.of("type","space","advances",Map.of(" ",Math.max(3,size/2),"\u00a0",Math.max(3,size/2))),Map.of("type","bitmap","file","serverhub:gui/"+texture,"height",24,"ascent",shadow?16:17,"chars",rows));
                Path json=root.resolve("assets/serverhub/font/label_"+family+"_"+size+(shadow?"_shadow":"")+".json");Files.createDirectories(json.getParent());Files.writeString(json,json(Map.of("providers",providers)),StandardCharsets.UTF_8);
            }
        }
    }
    private static FontBook measure(Path root)throws IOException {
        var result=new TreeMap<String,int[]>();var loader=new LoaderOptions();loader.setAllowDuplicateKeys(false);loader.setMaxAliasesForCollections(0);loader.setCodePointLimit(65536);
        for(String family:FontBook.FAMILIES)for(int size:FontBook.SIZES){
            Path file=safe(root,"assets/serverhub/font/label_"+family+"_"+size+".json");if(!Files.exists(file))continue;
            if(Files.size(file)>65536)throw new IOException("Schriftdefinition zu groß");
            try{
                Map<?,?> data=new Yaml(new SafeConstructor(loader)).load(Files.readString(file));
                List<?> providers=(List<?>)data.get("providers");Map<?,?> bitmap=providers.stream().filter(p->p instanceof Map<?,?> m&&"bitmap".equals(m.get("type"))).map(p->(Map<?,?>)p).findFirst().orElseThrow();
                Map<?,?> spaces=providers.stream().filter(p->p instanceof Map<?,?> m&&"space".equals(m.get("type"))).map(p->(Map<?,?>)((Map<?,?>)p).get("advances")).findFirst().orElse(Map.of());
                String[] texture=bitmap.get("file").toString().split(":",2);if(texture.length!=2||!texture[0].matches("[a-z0-9_.-]+"))throw new IOException("Ungültige Fonttextur");
                BufferedImage image=readImage(safe(root,"assets/"+texture[0]+"/textures/"+texture[1]));List<?> chars=(List<?>)bitmap.get("chars");
                if(chars.size()!=12||chars.stream().anyMatch(row->row.toString().length()!=16)||image.getWidth()%16!=0||image.getHeight()%12!=0)throw new IOException("Schriftatlas benötigt 16×12 Latin-1-Zellen");
                int cw=image.getWidth()/16,ch=image.getHeight()/12,height=((Number)bitmap.get("height")).intValue(),ascent=((Number)bitmap.get("ascent")).intValue();
                if(height<1||height>64||ascent>height)throw new IOException("Ungültiger Schrift-Ascent");int[] widths=new int[192];
                for(int i=0;i<192;i++){
                    int code=i<96?i+32:i+64;if(chars.get(i/16).toString().charAt(i%16)!=code)throw new IOException("Zeichentabelle muss Latin-1 enthalten");
                    if(code==32||code==160){Object advance=spaces.get(String.valueOf((char)code));widths[i]=advance==null?1:((Number)advance).intValue();continue;}
                    int right=0;for(int x=0;x<cw;x++)for(int y=0;y<ch;y++)if((image.getRGB(i%16*cw+x,i/16*ch+y)>>>24)>0)right=Math.max(right,x+1);
                    widths[i]=Math.max(1,Math.round(right*(float)height/ch)+1);
                }
                result.put(family+"."+size,widths);
            }catch(RuntimeException e){throw new IOException("Ungültige Schriftdefinition: "+file.getFileName(),e);}
        }
        for(int size:FontBook.SIZES)if(!result.containsKey("sans."+size))throw new IOException("Standardschrift sans."+size+" fehlt");
        return new FontBook(result);
    }
    private static BufferedImage readImage(Path path)throws IOException {
        if(Files.isSymbolicLink(path)||Files.size(path)>16*1024*1024)throw new IOException("Ungültige oder zu große Textur");
        try(var input=ImageIO.createImageInputStream(path.toFile())){
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("PNG-Textur erwartet");var reader=readers.next();
            try{reader.setInput(input);if((long)reader.getWidth(0)*reader.getHeight(0)>16*1024*1024)throw new IOException("Textur hat zu viele Pixel");return reader.read(0);}finally{reader.dispose();}
        }
    }
    private static Map<String,byte[]> unpack(byte[] bytes)throws IOException {
        var result=new TreeMap<String,byte[]>();long total=0;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
            if(entry.isDirectory())continue;if(result.size()>=4096)throw new IOException("Zu viele Packdateien");String name=entry.getName();
            if(name.startsWith("/")||name.contains("\\")||Arrays.asList(name.split("/")).contains(".."))throw new IOException("Unsicherer Packpfad");
            byte[] file=zip.readNBytes(MAX_PACK+1);total+=file.length;if(total>MAX_PACK)throw new IOException("Paket entpackt zu groß");if(result.put(name,file)!=null)throw new IOException("Doppelter Packpfad");
        }}return result;
    }
    private static Map<String,byte[]> readFiles(Path root)throws IOException {
        var files=new TreeMap<String,byte[]>();long total=0;
        try(var walk=Files.walk(root)){for(Path path:walk.sorted().toList()){
            if(Files.isSymbolicLink(path))throw new IOException("Links im Packordner sind nicht erlaubt");if(!Files.isRegularFile(path))continue;
            if(files.size()>=4096||Files.size(path)>MAX_PACK)throw new IOException("Pack zu groß");byte[] bytes=Files.readAllBytes(path);total+=bytes.length;if(total>MAX_PACK)throw new IOException("Pack zu groß");files.put(root.relativize(path).toString().replace(File.separatorChar,'/'),bytes);
        }}return files;
    }
    private static boolean same(Map<String,byte[]> a,Map<String,byte[]> b){return a.keySet().equals(b.keySet())&&a.entrySet().stream().allMatch(e->Arrays.equals(e.getValue(),b.get(e.getKey())));}
    private static byte[] zip(Map<String,byte[]> files)throws IOException {
        var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)){zip.setLevel(9);for(var file:files.entrySet()){
            var entry=new ZipEntry(file.getKey());entry.setTimeLocal(LocalDateTime.of(2026,11,16,0,0));zip.putNextEntry(entry);zip.write(file.getValue());zip.closeEntry();
        }}return bytes.toByteArray();
    }
    private static Path safe(Path root,String name)throws IOException {Path file=root.resolve(name).normalize();if(!file.startsWith(root.normalize())||name.contains("\\"))throw new IOException("Pfad außerhalb von pack/");for(Path p=file;p!=null&&p.startsWith(root);p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("Symbolischer Packpfad");return file;}
    private static void atomic(Path path,byte[] bytes)throws IOException {if(Files.exists(path)&&Arrays.equals(Files.readAllBytes(path),bytes))return;Path temp=Files.createTempFile(path.getParent(),"pack-",".tmp");try{Files.write(temp,bytes);try{Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}}
    private static byte[] properties(Properties p)throws IOException {var out=new StringBuilder();for(String key:new TreeSet<>(p.stringPropertyNames()))out.append(key).append('=').append(p.getProperty(key)).append('\n');return out.toString().getBytes(StandardCharsets.ISO_8859_1);}
    private static String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static String json(Object value){if(value instanceof Map<?,?> map){var list=new ArrayList<String>();map.forEach((k,v)->list.add(json(k.toString())+":"+json(v)));return "{"+String.join(",",list)+"}";}if(value instanceof List<?> l)return "["+String.join(",",l.stream().map(PackWorkspace::json).toList())+"]";if(value instanceof Number||value instanceof Boolean)return value.toString();var out=new StringBuilder("\"");for(char c:value.toString().toCharArray()){if(c=='"'||c=='\\')out.append('\\').append(c);else if(c<32)out.append(String.format("\\u%04x",(int)c));else out.append(c);}return out.append('"').toString();}
}
