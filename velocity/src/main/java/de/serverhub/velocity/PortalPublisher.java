package de.serverhub.velocity;

import com.google.gson.*;
import de.serverhub.common.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;

/** Optional portal transport. Each pack hash has a separate file and immutable share link. */
final class PortalPublisher {
    private static final int CHUNK=2*1024*1024,MAX_RESPONSE=1024*1024;
    @FunctionalInterface interface Transport {JsonObject post(String base,String token,String action,byte[] data,String type)throws IOException,InterruptedException;}
    private PortalPublisher(){}
    static String publish(Path directory,Properties config,PackData pack,String filename)throws IOException {
        try(var client=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build()){
            return publish(directory,config,pack,filename,System::getenv,(base,token,action,data,type)->post(client,base,token,action,data,type));
        }
    }
    static String publish(Path directory,Properties config,PackData pack,String filename,Function<String,String> environment,Transport transport)throws IOException {
        URI portal;String base=config.getProperty("pack.portal-url","").trim().replaceAll("/+$","");
        try{portal=URI.create(base).normalize();}catch(IllegalArgumentException e){throw new IOException("pack.portal-url ist ungültig",e);}
        if(!"https".equalsIgnoreCase(portal.getScheme())||portal.getHost()==null||portal.getUserInfo()!=null||portal.getFragment()!=null||portal.getRawQuery()!=null)throw new IOException("pack.portal-url muss eine HTTPS-Adresse ohne Zugangsdaten sein");
        base=portal.toString().replaceAll("/+$","");int owner,folder;
        try{owner=Integer.parseInt(config.getProperty("pack.portal-owner-id",""));folder=Integer.parseInt(config.getProperty("pack.portal-folder-id","0"));}catch(NumberFormatException e){throw new IOException("Portal-Nutzer-ID oder Ordner-ID ungültig",e);}
        if(owner<1||folder<0)throw new IOException("Portal-Nutzer-ID oder Ordner-ID ungültig");
        String variable=config.getProperty("pack.portal-token-env","PORTAL_TOKEN").trim();if(!variable.matches("[A-Za-z_][A-Za-z0-9_]*"))throw new IOException("pack.portal-token-env ungültig");
        String token=environment.apply(variable);if(token==null||!token.matches("[a-f0-9]{64}"))throw new IOException("Portal-Zugangsschlüssel fehlt in der Umgebungsvariable "+variable);
        if(filename.isBlank()||filename.length()>180||filename.contains("/")||filename.contains("\\")||filename.chars().anyMatch(Character::isISOControl))throw new IOException("Pack-Dateiname ungültig");
        Path stateFile=directory.resolve("portal-upload.properties");var state=new Properties();if(Files.isSymbolicLink(stateFile))throw new IOException("Portal-Status darf kein symbolischer Link sein");
        if(Files.exists(stateFile)){if(Files.size(stateFile)>MAX_RESPONSE)throw new IOException("Portal-Status ist zu groß");try(var input=Files.newInputStream(stateFile)){state.load(input);}}
        String key="version."+digest(base+"\n"+owner+"\n"+folder+"\n"+filename)+"."+pack.sha1();String saved=state.getProperty(key,"");if(samePortalDownload(portal,saved))return PackUrls.external(saved);
        String stem=filename.toLowerCase(Locale.ROOT).endsWith(".zip")?filename.substring(0,filename.length()-4):filename;
        String remoteName=stem+"-"+pack.sha1()+".zip";
        try{
            var start=new JsonObject();start.addProperty("name",remoteName);start.addProperty("size",pack.bytes().length);start.addProperty("owner",owner);start.addProperty("folder",folder);start.addProperty("sha1",pack.sha1());
            String id=transport.post(base,token,"start",start.toString().getBytes(StandardCharsets.UTF_8),"application/json").get("id").getAsString();if(!id.matches("[a-f0-9]{32}"))throw new IOException("Portal lieferte keine gültige Upload-ID");
            byte[] bytes=pack.bytes();for(int offset=0;offset<bytes.length;offset+=CHUNK)transport.post(base,token,"chunk&id="+id+"&offset="+offset,Arrays.copyOfRange(bytes,offset,Math.min(bytes.length,offset+CHUNK)),"application/octet-stream");
            var finish=new JsonObject();finish.addProperty("id",id);var completed=transport.post(base,token,"complete",finish.toString().getBytes(StandardCharsets.UTF_8),"application/json");
            if(!pack.sha1().equals(completed.get("sha1").getAsString()))throw new IOException("Portal-SHA-1 weicht vom lokalen Paket ab");long fileId=completed.get("file_id").getAsLong();if(fileId<1)throw new IOException("Portal-Datei-ID ungültig");
            var share=new JsonObject();share.addProperty("file_id",fileId);share.addProperty("mode","version");String url=transport.post(base,token,"share",share.toString().getBytes(StandardCharsets.UTF_8),"application/json").get("url").getAsString();
            if(!samePortalDownload(portal,url))throw new IOException("Portal lieferte keine gültige Downloadadresse");url=PackUrls.external(url);state.setProperty(key,url);
            // Keep previous hashes too: a later reload must not invalidate a pending player's link.
            Path temporary=Files.createTempFile(directory,"portal-upload-",".tmp");try{
                try{Files.setPosixFilePermissions(temporary,Set.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));}catch(UnsupportedOperationException ignored){}
                var output=new ByteArrayOutputStream();state.store(output,"ServerHub portal versions; no API credentials");if(output.size()>MAX_RESPONSE)throw new IOException("Portal-Status ist zu groß");Files.write(temporary,output.toByteArray());
                try{Files.move(temporary,stateFile,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,stateFile,StandardCopyOption.REPLACE_EXISTING);}
            }finally{Files.deleteIfExists(temporary);}return url;
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Portal-Upload unterbrochen",e);}catch(RuntimeException e){throw new IOException("Portal-Antwort ungültig",e);}
    }
    static JsonObject post(HttpClient client,String base,String token,String action,byte[] data,String type)throws IOException,InterruptedException {
        var request=HttpRequest.newBuilder(URI.create(base+"/api.php?action="+action)).timeout(Duration.ofSeconds(120)).header("X-Portal-Token",token).header("Content-Type",type).POST(HttpRequest.BodyPublishers.ofByteArray(data)).build();
        var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());try(var body=response.body()){
            byte[] bytes=body.readNBytes(MAX_RESPONSE+1);if(bytes.length>MAX_RESPONSE)throw new IOException("Portal-Antwort ist zu groß");
            if(response.statusCode()<200||response.statusCode()>=300)throw new IOException("Portal-Upload HTTP "+response.statusCode());
            try{return Json.read(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();}catch(RuntimeException e){throw new IOException("Portal-Antwort ist kein JSON-Objekt",e);}
        }
    }
    private static boolean samePortalDownload(URI portal,String raw){
        try{URI url=URI.create(raw);String path=portal.getPath().replaceAll("/+$","")+"/download.php";
            return "https".equalsIgnoreCase(url.getScheme())&&portal.getHost().equalsIgnoreCase(url.getHost())&&port(portal)==port(url)&&path.equals(url.getPath())&&url.getRawQuery()!=null&&url.getRawQuery().matches("t=[a-f0-9]{64}")&&url.getUserInfo()==null&&url.getFragment()==null;
        }catch(IllegalArgumentException e){return false;}
    }
    private static int port(URI uri){return uri.getPort()<0?443:uri.getPort();}
    private static String digest(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
