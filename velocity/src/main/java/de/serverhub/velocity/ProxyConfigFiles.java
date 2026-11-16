package de.serverhub.velocity;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/** Preserve the old URL/port configuration in a backup before moving to HTTPS. */
final class ProxyConfigFiles {
    private ProxyConfigFiles(){}
    static boolean migrate(Path directory,byte[] defaults)throws IOException{
        Path config=directory.resolve("config.properties");
        if(!Files.exists(config)){Files.write(config,defaults,StandardOpenOption.CREATE_NEW);return false;}
        Properties old=new Properties();try(var r=Files.newBufferedReader(config,StandardCharsets.UTF_8)){old.load(r);}
        if(old.getProperty("pack.mode","").equals("github")&&!old.getProperty("configuration-version","").equals("3")){
            Path backup=directory.resolve("config-v2.3-backup.properties");for(int i=1;Files.exists(backup);i++)backup=directory.resolve("config-v2.3-backup-"+i+".properties");Files.copy(config,backup);
            old.setProperty("configuration-version","3");old.setProperty("pack.mode","auto");
            try(var writer=Files.newBufferedWriter(config,StandardCharsets.UTF_8)){old.store(writer,"ServerHub: Standardpaket oder eigene Packdateien automatisch ausliefern");}return true;
        }
        if(old.containsKey("configuration-version")||old.containsKey("pack.mode"))return false;
        Properties next=new Properties();try(var r=new InputStreamReader(new ByteArrayInputStream(defaults),StandardCharsets.UTF_8)){next.load(r);}
        for(String key:new String[]{"pack.file","pack.timeout-seconds","pack.prompt","pack.kick"})if(old.containsKey(key))next.setProperty(key,old.getProperty(key));
        Path backup=directory.resolve("config-v2.0-backup.properties");
        for(int i=1;Files.exists(backup);i++)backup=directory.resolve("config-v2.0-backup-"+i+".properties");
        Files.copy(config,backup);
        Path temporary=Files.createTempFile(directory,"config-", ".tmp");
        try{
            try(var w=Files.newBufferedWriter(temporary,StandardCharsets.UTF_8)){next.store(w,"ServerHub 2.1: alte HTTP-Konfiguration gesichert; Download jetzt per GitHub-HTTPS");}
            try{Files.move(temporary,config,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporary,config,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
        return true;
    }
}
