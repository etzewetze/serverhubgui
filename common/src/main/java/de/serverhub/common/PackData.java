package de.serverhub.common;

import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.ZipFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public record PackData(byte[] bytes, String sha1, UUID id) {
    public UiCatalog ui()throws IOException {
        try(var zip=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))){for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry())if(e.getName().equals("serverhub-ui.properties"))return UiCatalog.properties(zip);}return UiCatalog.empty();
    }
    public FontBook fonts()throws IOException {
        try(var zip=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))){
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())if(entry.getName().equals("serverhub-font-widths.properties"))return FontBook.properties(zip);
        }
        return FontBook.empty();
    }
    public static PackData read(Path path) throws IOException {
        long size=Files.size(path);
        if(size<1||size>32*1024*1024)throw new IOException("Paket muss zwischen 1 Byte und 32 MiB groß sein");
        try(var zip=new ZipFile(path.toFile())) {
            for(String entry:List.of("pack.mcmeta","assets/serverhub/font/gui.json","assets/serverhub/items/menu_icon.json"))
                if(zip.getEntry(entry)==null)throw new IOException("Ungültiges Menüpaket: "+entry+" fehlt");
        }
        byte[] bytes=Files.readAllBytes(path);
        try {
            String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
            return new PackData(bytes,sha,UUID.nameUUIDFromBytes(("serverhub:"+sha).getBytes(StandardCharsets.UTF_8)));
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
