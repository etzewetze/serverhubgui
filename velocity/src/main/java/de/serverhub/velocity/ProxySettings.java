package de.serverhub.velocity;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

record ProxySettings(Mode mode,String externalUrl,PackData pack,int timeout,String prompt,String kick,
                     String bind,int port,String publicHost,String scheme,int advertisedPort) {
    enum Mode { GITHUB, SELF_HOST, EXTERNAL }
    boolean http(){return mode==Mode.SELF_HOST;}
    String url(String joiningHost){
        return switch(mode){
            case GITHUB -> PackUrls.RELEASE_URL;
            case EXTERNAL -> externalUrl;
            case SELF_HOST -> PackUrls.selfHosted(scheme,publicHost.equalsIgnoreCase("auto")?joiningHost:publicHost,advertisedPort,pack.sha1());
        };
    }
    static ProxySettings read(Path directory)throws IOException{
        Properties p=new Properties();
        try(var r=Files.newBufferedReader(directory.resolve("config.properties"),StandardCharsets.UTF_8)){p.load(r);}
        Mode mode;
        try{mode=Mode.valueOf(p.getProperty("pack.mode","github").trim().replace('-','_').toUpperCase(Locale.ROOT));}
        catch(IllegalArgumentException e){throw new IOException("pack.mode muss github, self-host oder external sein",e);}
        String external="";
        try{if(mode==Mode.EXTERNAL)external=PackUrls.external(p.getProperty("pack.external-url",""));}
        catch(IllegalArgumentException e){throw new IOException(e.getMessage(),e);}
        Path packPath=directory.resolve(p.getProperty("pack.file","serverhub-pack.zip")).normalize();
        if(!packPath.startsWith(directory.normalize()))throw new IOException("pack.file muss innerhalb des Pluginordners liegen");
        int timeout,port,advertised;
        try{timeout=Integer.parseInt(p.getProperty("pack.timeout-seconds","120"));port=Integer.parseInt(p.getProperty("self-host.port","8124"));advertised=Integer.parseInt(p.getProperty("self-host.advertised-port",Integer.toString(port)));}
        catch(NumberFormatException e){throw new IOException("Timeout und Ports müssen Zahlen sein",e);}
        if(timeout<15||timeout>600||port<1||port>65535||advertised<1||advertised>65535)throw new IOException("Timeout 15–600 Sekunden; Ports 1–65535");
        String scheme=p.getProperty("self-host.scheme","http").toLowerCase(Locale.ROOT);
        if(!scheme.equals("http")&&!scheme.equals("https"))throw new IOException("self-host.scheme muss http oder https sein");
        String host=p.getProperty("self-host.public-host","auto").trim();
        if(mode==Mode.SELF_HOST&&!host.equalsIgnoreCase("auto")){
            try{PackUrls.selfHosted(scheme,host,advertised,"0".repeat(40));}catch(IllegalArgumentException e){throw new IOException(e.getMessage(),e);}
        }
        PackData pack=PackData.read(packPath);
        if(mode==Mode.GITHUB){
            try(var bundled=ProxySettings.class.getResourceAsStream("/serverhub-pack.zip")){
                if(bundled==null)throw new IOException("Mitgeliefertes Paket fehlt");
                if(!java.util.Arrays.equals(pack.bytes(),bundled.readAllBytes()))throw new IOException("Im GitHub-Modus muss pack.file die mitgelieferte ZIP enthalten. Für eigene Texturen self-host oder external verwenden.");
            }
        }
        return new ProxySettings(mode,external,pack,timeout,
            p.getProperty("pack.prompt","Dieses Netzwerk benötigt das Menüpaket."),
            p.getProperty("pack.kick","Das ServerHub-Menüpaket ist erforderlich. Bitte informiere das Serverteam."),
            p.getProperty("self-host.bind","0.0.0.0"),port,host,scheme,advertised);
    }
}
