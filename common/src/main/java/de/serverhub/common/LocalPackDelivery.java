package de.serverhub.common;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Used only when Paper has no configured proxy. Network backends never start this service. */
public final class LocalPackDelivery implements AutoCloseable {
    public record State(CentralMenuConfig.State menu,PackData pack,String mode,String external,String host,String scheme,int publicPort,int timeout){}
    private final Path directory;private final byte[] bundled;private volatile State state;private PackHost server;private String bind;private int port;
    public LocalPackDelivery(Path directory,byte[] bundled)throws IOException {
        this.directory=directory.toAbsolutePath().normalize();this.bundled=bundled;Files.createDirectories(this.directory);
        Path config=this.directory.resolve("config.properties");if(!Files.exists(config))try(var in=getClass().getResourceAsStream("/config.properties")){if(in==null)throw new IOException("config.properties fehlt");Files.copy(in,config);}
        reload();
    }
    public synchronized State reload()throws IOException {
        var candidate=new CentralMenuConfig(directory).load(List.of("local"));PackData pack=PackWorkspace.prepare(directory,bundled,candidate.options(),candidate.fontSources());
        new MenuSnapshot(candidate.revision(),candidate.options(),candidate.servers().stream().filter(s->!s.hidden()).map(CentralMenuConfig.Server::entry).toList(),pack.fonts(),pack.ui()).encode();
        Properties p=new Properties();try(var reader=Files.newBufferedReader(directory.resolve("config.properties"),StandardCharsets.UTF_8)){p.load(reader);}
        String mode=p.getProperty("pack.mode","auto").trim(),external=p.getProperty("pack.external-url","");
        if(mode.equals("auto"))mode=PackWorkspace.bundled(pack,bundled)?"github":"self-host";
        if(!Set.of("github","self-host","external").contains(mode))throw new IOException("Unbekannter Paketmodus");
        if(mode.equals("github")&&!PackWorkspace.bundled(pack,bundled))throw new IOException("Eigene Texturen benötigen pack.mode=auto oder self-host");
        if(mode.equals("external"))external=PackUrls.external(external);
        int nextPort,publicPort,timeout;try{nextPort=Integer.parseInt(p.getProperty("self-host.port","8124"));publicPort=Integer.parseInt(p.getProperty("self-host.advertised-port",Integer.toString(nextPort)));timeout=Integer.parseInt(p.getProperty("pack.timeout-seconds","120"));}catch(NumberFormatException e){throw new IOException("Ungültige Ports/Fristen",e);}
        if(nextPort<1||nextPort>65535||publicPort<1||publicPort>65535||timeout<15||timeout>600)throw new IOException("Ungültige Ports/Fristen");
        String nextBind=p.getProperty("self-host.bind","0.0.0.0"),host=p.getProperty("self-host.public-host","auto"),scheme=p.getProperty("self-host.scheme","http");
        if(!Set.of("http","https").contains(scheme))throw new IOException("self-host.scheme muss http oder https sein");
        if(!host.equals("auto"))PackUrls.selfHosted(scheme,host,publicPort,pack.sha1());
        if(server!=null&&(!bind.equals(nextBind)||port!=nextPort))throw new IOException("Pack-Port/Bind geändert: Server neu starten");
        if(server==null&&mode.equals("self-host")){server=new PackHost(nextBind,nextPort,pack);bind=nextBind;port=nextPort;}else if(server!=null)server.update(pack);
        return state=new State(candidate,pack,mode,external,host,scheme,publicPort,timeout);
    }
    public State state(){return state;}
    public String url(State snapshot,String joiningHost){return switch(snapshot.mode){case "github"->PackUrls.RELEASE_URL;case "external"->snapshot.external;default->PackUrls.selfHosted(snapshot.scheme,snapshot.host.equals("auto")?joiningHost:snapshot.host,snapshot.publicPort,snapshot.pack.sha1());};}
    public synchronized void retain(Set<String> hashes){if(server!=null)server.retain(hashes);}
    @Override public synchronized void close(){if(server!=null)server.close();}
}
