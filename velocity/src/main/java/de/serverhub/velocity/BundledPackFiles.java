package de.serverhub.velocity;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Upgrade only an exact old stock ZIP; custom resource packs are never replaced. */
final class BundledPackFiles {
    private static final Set<String> PREVIOUS = Set.of("12e55d121ceb687cae863306d377859d2849ef47");
    private BundledPackFiles() {}
    static boolean upgrade(Path directory,byte[] bundled) throws IOException { return upgrade(directory,bundled,PREVIOUS); }
    static boolean upgrade(Path directory,byte[] bundled,Set<String> previous) throws IOException {
        Properties config=new Properties();
        try(var r=Files.newBufferedReader(directory.resolve("config.properties"),StandardCharsets.UTF_8)){config.load(r);}
        if(!config.getProperty("pack.mode","github").trim().equalsIgnoreCase("github"))return false;
        Path target=directory.resolve(config.getProperty("pack.file","serverhub-pack.zip")).normalize();
        if(!target.startsWith(directory.normalize()))throw new IOException("pack.file muss im Pluginordner liegen");
        if(!Files.exists(target)||Files.size(target)>32*1024*1024)return false;
        byte[] old=Files.readAllBytes(target);if(Arrays.equals(old,bundled))return false;
        String hash;
        try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(old));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        if(!previous.contains(hash))return false;
        Path backup=target.resolveSibling(target.getFileName()+".v2.1-backup");
        for(int i=1;Files.exists(backup);i++)backup=target.resolveSibling(target.getFileName()+".v2.1-backup-"+i);
        Files.copy(target,backup);
        Path temp=Files.createTempFile(target.getParent(),"serverhub-pack-",".tmp");
        try{
            Files.write(temp,bundled);
            try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
        return true;
    }
}
