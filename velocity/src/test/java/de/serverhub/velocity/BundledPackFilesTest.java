package de.serverhub.velocity;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BundledPackFilesTest {
    @TempDir Path directory;
    String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));}
    @Test void onlyAKnownStockZipIsBackedUpAndReplaced()throws Exception{
        Files.writeString(directory.resolve("config.properties"),"pack.mode=github\n");
        byte[] old="old stock pack".getBytes(),next="new stock pack".getBytes();
        Files.write(directory.resolve("serverhub-pack.zip"),old);
        assertTrue(BundledPackFiles.upgrade(directory,next,Set.of(hash(old))));
        assertArrayEquals(old,Files.readAllBytes(directory.resolve("serverhub-pack.zip.v2.1-backup")));
        assertArrayEquals(next,Files.readAllBytes(directory.resolve("serverhub-pack.zip")));
        assertFalse(BundledPackFiles.upgrade(directory,next,Set.of(hash(old))));
    }
    @Test void customPacksExternalUrlsAndPathsOutsideThePluginAreNeverOverwritten()throws Exception{
        byte[] custom="custom".getBytes(),next="new".getBytes();
        Files.writeString(directory.resolve("config.properties"),"pack.mode=github\n");
        Files.write(directory.resolve("serverhub-pack.zip"),custom);
        assertFalse(BundledPackFiles.upgrade(directory,next,Set.of("unknown")));
        assertArrayEquals(custom,Files.readAllBytes(directory.resolve("serverhub-pack.zip")));
        Files.writeString(directory.resolve("config.properties"),"pack.mode=external\n");
        assertFalse(BundledPackFiles.upgrade(directory,next,Set.of(hash(custom))));
        Files.writeString(directory.resolve("config.properties"),"pack.mode=github\npack.file=../outside.zip\n");
        assertThrows(java.io.IOException.class,()->BundledPackFiles.upgrade(directory,next,Set.of(hash(custom))));
    }
}
