package de.serverhub.velocity;

import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.ZipFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

record PackData(byte[] bytes, String sha1, UUID id) {
    static PackData read(Path path) throws IOException {
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
